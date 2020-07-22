/*
Ted Moore
ted@tedmooremusic.com
www.tedmooremusic.com
April 29, 2019

SuperCollider class for running non-real time concatenation of audio grains into new files

intended to interface with MIRAnalysis and MIRCorpus classes

*/

ConcatSynthNRT {
	var normRanges;

	*halfSineWindow {
		arg len;
		^Array.fill(len,{
			arg i;
			i.linlin(0,len-1,-0.5 * pi,0.5 * pi).cos;
		});
	}

	*fullSineWindow {
		arg len;
		^Array.fill(len,{
			arg i;
			i.linlin(0,len-1,-pi,pi).cos;
		});
	}

	*hannWindow {
		arg len;
		^Array.fill(len,{
			arg i;
			0.5 * (1-((2pi*i) / (len-1)).cos);
		});
	}

	*rampWindow {
		arg len, rampSamples;
		^Array.fill(len,{
			arg i;
			var val;//, rampSamples;
			//rampSamples = rampTime * server.sampleRate;
			case
			{i < rampSamples}{
				val = i.linlin(0,rampSamples,0,1);
			}
			{i >= (len - rampSamples)}{
				val = i.linlin(len-rampSamples,len-1,1,0);
			}
			{
				val = 1;
			};
			val;
		});
	}

	normalize_fit_one {
		arg vector;

		if(normRanges.isNil,{
			normRanges = MIRAnalysis.nFeatures.collect({
				ControlSpec(inf,-inf);
			});
		});

		vector.do({
			arg val, i;
			if(val > normRanges[i].maxval,{normRanges[i].maxval = val});
			if(val < normRanges[i].minval,{normRanges[i].minval = val});
		});
	}

	normalize_fit_batch {
		arg vectors;
		vectors.do({
			arg vector;
			this.normalize_fit_one(vector);
		});
	}

	normalize_transform_one {
		arg vector;
		^vector.collect({
			arg val, i;
			normRanges[i].unmap(val);
		});
	}

	normalize_transform_batch {
		arg vectors;
		^vectors.collect({
			arg vector;
			this.normalize_transform_one(vector);
		});
	}

	*renderFromCorpusAndFilePath {
		arg server, mirAnalysisCorpus, inputFilePath, removeFrameAfterUse, windowType = "halfSine",historyToAvoid = 0,outPath, overlap = 1.2;
		//"class method played".postln;
		^super.new.renderFromCorpusAndFilePath(server, mirAnalysisCorpus, inputFilePath, removeFrameAfterUse, windowType,historyToAvoid,outPath,overlap);
	}

	renderFromCorpusAndFilePath {
		arg server, mirAnalysisCorpus, inputFilePath, removeFrameAfterUse,windowType = "halfSine",historyToAvoid = 0, outPath, overlap;
		var pathName = PathName(inputFilePath);

		outPath = outPath ? "%%_concat_%.wav".format(pathName.pathOnly,pathName.fileNameWithoutExtension,Date.myFormat);

		MIRAnalysis.analyzeFileNRT(inputFilePath,{
			arg mirAnalysisFile;
			var inputFileFrames;

			inputFileFrames = mirAnalysisFile.frames.collect({
				arg frame;
				frame[1];
			});

			this.renderFromCorpusAndRawFrames(server,mirAnalysisCorpus,inputFileFrames,removeFrameAfterUse, windowType, historyToAvoid, outPath, mirAnalysisFile.fftDur);
		});
	}

	*renderFromCorpusAndRawFrames {
		arg server, mirAnalysisCorpus, inputFileFrames, removeFrameAfterUse = false, windowType = "halfSine", historyToAvoid = 0, outPath, frameDur,overlap = 1.2;
		^super.new.renderFromCorpusAndRawFrames(server, mirAnalysisCorpus, inputFileFrames, removeFrameAfterUse, windowType, historyToAvoid, outPath, frameDur,overlap);
	}

	renderFromCorpusAndRawFrames {
		arg server, mirAnalysisCorpus, inputFileFrames, removeFrameAfterUse = false, windowType = "halfSine", historyToAvoid = 0, outPath, frameDur,overlap = 1.2;
		var kdt, corpusForKDT;

		if(outPath.isNil,{
			outPath = Platform.recordingsDir+/+"concat_synth_nrt_%.wav".format(Date.myFormat);
		});

		frameDur = frameDur ? MIRAnalysis.fftDur;

		// normalize fit on input
		inputFileFrames.do({
			arg frame;
			this.normalize_fit_one(frame);
		});

		// normalize fit on corpus
		mirAnalysisCorpus.corpus.do({
			arg corpusItem;
			this.normalize_fit_one(corpusItem.vector);
		});

		// normalize transform on input
		inputFileFrames = inputFileFrames.collect({
			arg frame;
			this.normalize_transform_one(frame);
		});

		// normalize transform on corpus (and format it properly for k dimensional tree)
		corpusForKDT = mirAnalysisCorpus.corpus.collect({
			arg corpusItem;
			this.normalize_transform_one(corpusItem.vector) ++ [corpusItem];
		});

		kdt = KDTree(corpusForKDT,lastIsLabel:true);

		this.runRenderLoop(server,kdt,inputFileFrames,removeFrameAfterUse, windowType, historyToAvoid, outPath, frameDur,overlap);
	}

	getNextSlice {
		arg inputFileFrame, mode, removeFrameAfterUse, kdt, historyToAvoid, history;
		var buf, startTime, dur;
		mode.switch(
			"kdt",{
				var kdtReturn, newSlice;
				kdtReturn = kdt.nearest(inputFileFrame);
				newSlice = kdtReturn[0];

				if(removeFrameAfterUse,{
					kdt.delete(newSlice.location);
					//kdt.label = corpus;
				});

				if(historyToAvoid > 0,{
					history = history.add(newSlice);
					kdt.delete(newSlice.location);

					if(history.size > historyToAvoid,{
						var putBackIn = history.removeAt(0);
						kdt.undelete(putBackIn.location);
					});
				});

				buf = newSlice.label.buf;
				dur = newSlice.label.dur;
				startTime = newSlice.label.startTime;
			},
			"fromArrayOfCorpusItems",{
				buf = inputFileFrame.buf;
				startTime = inputFileFrame.startTime;
				dur = inputFileFrame.dur;
			}
		);
		^[startTime,dur,buf,history];
	}

	runRenderLoop {
		arg server,kdt,inputFileFrames,removeFrameAfterUse=false,windowType="halfSine",historyToAvoid=0,outPath,frameDur,overlap=1.2,mode="kdt",jitter=0,ampMul=1;

		Routine({
			var destinationArray, history = [];

			// we only make stereo files now...
			destinationArray = Array.fill(((frameDur * inputFileFrames.size) + 1) * server.sampleRate * 2,{0});

			inputFileFrames.do({
				arg frame, inputFileFrameNum;
				var src_frame_start, envBuf, srcBuf, startTime, dur, dst_frame_start, num_frames_get_from_Buf, condition = Condition.new, bufChannels;

				# startTime, dur, srcBuf, history = this.getNextSlice(frame,mode,removeFrameAfterUse,kdt,historyToAvoid,history);

				bufChannels = srcBuf.numChannels;

				src_frame_start = (startTime * server.sampleRate).asInteger;
				dst_frame_start = (max(0,(inputFileFrameNum * frameDur) + jitter.rand2) * server.sampleRate).asInteger;


				//[srcBuf.numFrames, src_frame_start].postln;
				// ========== get window ==============================
				num_frames_get_from_Buf = min(
					(dur * server.sampleRate * overlap).asInteger,
					srcBuf.numFrames - src_frame_start
				);
				//"num_frames_get_from_Buf: %".format(num_frames_get_from_Buf).postln;
				//"env buf len: %".format(~envBufLen).postln;
				windowType.switch(
					"halfSine",{
						envBuf = ConcatSynthNRT.halfSineWindow(
							num_frames_get_from_Buf
						);
					},
					"fullSine",{
						envBuf = ConcatSynthNRT.fullSineWindow(
							num_frames_get_from_Buf
						);
					},
					"hann",{
						envBuf = ConcatSynthNRT.hannWindow(
							num_frames_get_from_Buf
						);
					},
					"ramp",{
						envBuf = ConcatSynthNRT.rampWindow(
							num_frames_get_from_Buf,
							server.sampleRate * 0.03;
						);
					}
				);

				srcBuf.getn(src_frame_start * bufChannels, num_frames_get_from_Buf * bufChannels,{
					arg array;
					//array.size.postln;

					if(bufChannels == 2,{
						array = array.clump(2);
					});
					//var adderArray;
					array = array * envBuf;

					if(bufChannels == 2,{
						array = array.flatten;
					},{
						// presuming buf chans = 1
						array = array.dup.flop.flatten;
					});

					array = array * ampMul;
					//array.postln;
					//if((inputFileFrameNum % 200) == 0,{defer{array.plot}});
					array.size.do({
						arg samp_index_in_src_array;
						var samp_in_dst_array = (dst_frame_start * 2) + samp_index_in_src_array;
/*						"dst array size:            %".format(destinationArray.size).postln;
						"src buf size:              %".format(srcBuf.numFrames).postln;
						"inputFileFrameNum:         %".format(inputFileFrameNum).postln;
						"samp_in_dst_array:         %".format(samp_in_dst_array).postln;
						"samp_index_in_src_array:   %".format(samp_index_in_src_array).postln;
						"".postln;*/
						destinationArray[samp_in_dst_array] = destinationArray[samp_in_dst_array] + array[samp_index_in_src_array];
					});
					condition.unhang;
					//condition.signal;
				});
				condition.hang;

				"% of % = % percent complete".format(
					(inputFileFrameNum+1).asStringff(5),
					inputFileFrames.size.asStringff(5),
					(((inputFileFrameNum+1)/inputFileFrames.size)*100).round(0.01).asString.padRight(5,"0")
				).postln;
			});

			Buffer.loadCollection(server,destinationArray,2,{
				arg newBuf;
				newBuf.write(outPath);
			});
		}).play;
	}

	*renderFromArrayOfCorpusItems {
		arg server, orderedItems, windowType, outPath, frameDur, overlap = 1.2, jitter = 0, ampMul = 1;
		this.new.runRenderLoop(server, nil, orderedItems, false, windowType, 0, outPath, frameDur, overlap, "fromArrayOfCorpusItems", jitter,ampMul);
	}
}