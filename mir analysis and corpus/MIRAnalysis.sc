/*
Ted Moore
ted@tedmooremusic.com
www.tedmooremusic.com
April 29, 2019

SuperCollider class for running non-real time Music Information Retreival analysis on audio files
Inspired by Nic Collins' SCMIR: https://composerprogrammer.com/code.html

*/

MIRAnalysisFile {
	classvar <featureOrder;
	var <frames,<fftSize,<fftDur,<path,<fft,<dispersionIndicies,<onsets, <duration, <>normalizedFrames = nil, <ranges = nil;

	*initClass {
		featureOrder = MIRAnalysis.featureOrder;
	}

	*new {
		arg frames,fftSize,fftDur,path,fft,dispersionIndicies,onsets,duration;
		^super.newCopyArgs(frames,fftSize,fftDur,path,fft,dispersionIndicies,onsets,duration);
	}

	saveAsCSV {
		arg path;
		var file;
		if(PathName(path).extension != "csv",{
			path = path ++ ".csv";
		});
		file = File(path,"w");

		file.write("nFrames,%\n".format(frames.size));
		file.write("fftSize,%\n".format(fftSize));
		file.write("fftDur,%\n".format(fftDur));
		file.write("filePath,%\n".format(path));
		//file.write("normalizationRanges,%\n".format(ranges));
		file.write("frameIndex,frameTime_sec");

		featureOrder.do({
			arg feature;
			file.write(",%".format(feature.asString));
		});

		file.write(",dispersionIndex,onsets");

		if(normalizedFrames.notNil,{
			//file.write(" ");
			featureOrder.do({
				arg feature;
				file.write(",%_norm".format(feature.asString));
			});
		});

		file.write("\n");

		frames.do({
			arg frame, frameNum;
			file.write("%,%".format(frameNum,frame[0]));

			frame[1].do({ // vector
				arg val;
				file.write(",%".format(val));
			});

			file.write(",%,%".format(
				dispersionIndicies[frameNum],
				onsets[frameNum]
			));

			if(normalizedFrames.notNil,{
				normalizedFrames[frameNum][1].do({
					arg val;
					file.write(",%".format(val));
				});
			});

			file.write("\n");
		});

		file.close;
	}

	plot {
		frames.collect({
			arg frame, i;
			frame[1].copy.addAll([dispersionIndicies[i],onsets[i]]); // just the data vector;
		}).flatten.plot("mir",Rect(0,0,400,800),numChannels:frames[0][1].size+2);
	}

	normalize {
		# normalizedFrames, ranges = MIRAnalysisFile.normalize(frames);
		^this;
	}

	*normalize {
		arg frames;
		var ranges, normalizedFrames;

		ranges = Array.fill(frames[0][1].size,{ControlSpec(inf,-inf)}); // [0][1] is [first example][data vector]

		frames.do({
			arg line;
			line[1].do({ // vector
				arg val, i;
				if(val > ranges[i].maxval,{
					ranges[i].maxval = val;
				});
				if(val < ranges[i].minval,{
					ranges[i].minval = val;
				});
			});
		});

		normalizedFrames = frames.collect({
			arg line;
			var newVector = line[1].collect({
				arg val, i;
				ranges[i].unmap(val);
			});
			[line[0],newVector];
		});

		^[normalizedFrames, ranges];
	}

	getDefinedVectorFrames {
		arg defVec, getNormalized = false;
		var sourceFrames, outFrames;
		if(getNormalized,{
			if(normalizedFrames.isNil,{
				this.normalize;
			});
			sourceFrames = normalizedFrames;
		},{
			sourceFrames = frames;
		});
		outFrames = sourceFrames.collect({
			arg frame;
			var outVec = defVec.collect({
				arg param;
				var index = featureOrder.indexOf(param);
				frame[1][index];
			});
			[frame[0],outVec];
		});
		^outFrames;
	}
}

MIRAnalysisLiveFrame {
	var <vector, <normalizedVector, <featureOrder, <dispersionIndex;

	*new {
		arg vector;
		^super.new.init(vector);
	}

	init {
		arg vector_;
		vector = vector_;
	}

	getParam {
		arg param, normalized;
		/*		param.postln;
		param.class.postln;
		featureOrder.postln;
		featureOrder.includes(param).postln;
		"".postln;*/
		if(featureOrder.includes(param),{
			if(normalized,{
				^normalizedVector[featureOrder.indexOf(param)];
			},{
				^vector[featureOrder.indexOf(param)];
			});
		},{
			"MIRAnalysisLiveFrame::getParam | param % not found".format(param).warn;
			^nil;
		});
	}

	featureOrder_ {
		arg fo;
		featureOrder = fo;
	}

	normalizedVector_ {
		arg v;
		normalizedVector = v;
	}

	dispersionIndex_ {
		arg di;
		dispersionIndex = di;
	}

	getSubVector {
		arg paramArray, normalized;
		var returnVec = List.new;
		paramArray.do({
			arg param;
			returnVec.add(this.getParam(param,normalized));
		});
		^returnVec
	}
}

MIRAnalysis {
	classvar <fftSize = 2048, <>maxNChans = 8, <>maxHistory = 6;
	classvar buf1size = 14;
	classvar buf2size = 52;
	classvar buf3size = 40;
	var <liveInBus, nChans = 1, synth, <>maxHistory = 6, replyID,action, normalizedRanges, normalizedRangesForSynth, playing, vectorHistory, currentData, <trigRate, maxHistory_seconds = 1;

	*initClass {
		StartUp.defer {
			this.makeSynthDefs(maxNChans);
		}
	}

	*nFeatures {
		^this.featureOrder.size;
	}

	*featureOrder {
		^[
			\specCent,
			\specSpread,
			\specSkewness,
			\specKurtosis,
			\specRolloff,
			\specFlatness,
			\specCrest,
			\pitch,
			\pitchConfidence,
			\loudness,
			\truePeak,
			\senseDis,
			\zeroCrossing,
			\mfcc00,
			\mfcc01,
			\mfcc02,
			\mfcc03,
			\mfcc04,
			\mfcc05,
			\mfcc06,
			\mfcc07,
			\mfcc08,
			\mfcc09,
			\mfcc10,
			\mfcc11,
			\mfcc12,
			\mfcc13,
			\mfcc14,
			\mfcc15,
			\mfcc16,
			\mfcc17,
			\mfcc18,
			\mfcc19,
			\mfcc20,
			\mfcc21,
			\mfcc22,
			\mfcc23,
			\mfcc24,
			\mfcc25,
			\mfcc26,
			\mfcc27,
			\mfcc28,
			\mfcc29,
			\mfcc30,
			\mfcc31,
			\mfcc32,
			\mfcc33,
			\mfcc34,
			\mfcc35,
			\mfcc36,
			\mfcc37,
			\mfcc38,
			\mfcc39,
			\chromagram00,
			\chromagram01,
			\chromagram02,
			\chromagram03,
			\chromagram04,
			\chromagram05,
			\chromagram06,
			\chromagram07,
			\chromagram08,
			\chromagram09,
			\chromagram10,
			\chromagram11,
			\melband00,
			\melband01,
			\melband02,
			\melband03,
			\melband04,
			\melband05,
			\melband06,
			\melband07,
			\melband08,
			\melband09,
			\melband10,
			\melband11,
			\melband12,
			\melband13,
			\melband14,
			\melband15,
			\melband16,
			\melband17,
			\melband18,
			\melband19,
			\melband20,
			\melband21,
			\melband22,
			\melband23,
			\melband24,
			\melband25,
			\melband26,
			\melband27,
			\melband28,
			\melband29,
			\melband30,
			\melband31,
			\melband32,
			\melband33,
			\melband34,
			\melband35,
			\melband36,
			\melband37,
			\melband38,
			\melband39
		];
	}

	*makeSynthDefs {
		arg maxChans;
		[\Nrt,\Live].do({
			arg mode;
			(1..maxChans).do({
				arg nChans;
				SynthDef(\mir++mode.asSymbol++nChans.asSymbol,{
					arg soundBuf, dataBuf, dataBuf2, dataBuf3, trigRate = 30, inBus, replyID,onsetThresh = 0.9,onsetRelaxTime = 0.1, autoRange = 0, lag = 0;
					var sig,fft,trig,specCent,features, normedFeatures, norms, onsetTrigs, mfcc_chroma, melbands;

					if(mode == \Nrt,{
						sig = Mix(PlayBuf.ar(nChans,soundBuf,BufRateScale.ir(soundBuf),doneAction:2));
						trig = DelayN.kr(Impulse.kr(trigRate),trigRate.reciprocal,trigRate.reciprocal);
					},{
						sig = Mix(In.ar(inBus,nChans));
						trig = DelayN.kr(Impulse.kr(trigRate),trigRate.reciprocal,trigRate.reciprocal)
					});

					//sig.postln;

					fft = FFT(LocalBuf(fftSize),sig);

					features = FluidSpectralShape.kr(sig) ++
					FluidPitch.kr(sig) ++
					FluidLoudness.kr(sig) ++
					[
						SensoryDissonance.kr(fft),
						A2K.kr(ZeroCrossing.ar(sig))
					]; // 13

					mfcc_chroma = FluidMFCC.kr(sig,40) ++
					Chromagram.kr(fft,fftSize);

					melbands = FluidMelBands.kr(sig,40,maxNumBands:40);

					features = Sanitize.kr(features);

					//features = Median.kr(15,features); // about 25 ms

					onsetTrigs = Onsets.kr(fft,onsetThresh,relaxtime:onsetRelaxTime);

					if(mode == \Nrt,{
						features = features.lag(lag);
						onsetTrigs = Trig1.kr(onsetTrigs,trigRate.reciprocal);
						Logger.kr(features ++ [onsetTrigs],trig,dataBuf);
						Logger.kr(mfcc_chroma,trig,dataBuf2);
						Logger.kr(melbands,trig,dataBuf3);
					},{
						features = features ++ mfcc_chroma ++ melbands;

						features = features.lag(lag);

						SendReply.kr(
							trig,
							"/mirLive",
							features,
							replyID
						);
						SendReply.kr(onsetTrigs,"/mirLiveOnset",replyID:replyID);
					});
				}).writeDefFile;
			});
		});
	}

	*live {
		arg audioInBus,target,addAction,normalizedRanges,nChans = 1,onsetThresh = 1.0, onsetRelaxTime = 0.2,autoRange,lag,action,liveTrigRate = 30;
		^super.new.init(audioInBus,target,addAction,normalizedRanges,nChans,onsetThresh,onsetRelaxTime,autoRange,lag,action,liveTrigRate);
	}

	autoRange_ {
		arg boolean;
		if(boolean,{
			synth.set(\autoRange,1);
		},{
			synth.set(\autoRange,0);
		});
	}

	lag_ {
		arg la;
		synth.set(\lag,la);
	}

	running_ {
		arg bool;
		playing = bool;
		if(playing,{
			synth.run(true);
		},{
			synth.run(false);
		});
	}

	inBus_ {
		arg b;
		synth !? (_.set(\inBus,b));
	}

	init {
		arg liveInBus_, target_,addAction_,normalizedRanges_,nChans_ = 1, onsetThresh, onsetRelaxTime, autoRange_, lag_, action_, trigRate_ = 30;
		liveInBus = liveInBus_;
		nChans = nChans_;
		replyID = UniqueID.next;
		action = action_;
		normalizedRanges = normalizedRanges_;
		playing = true;
		vectorHistory = [];
		trigRate = trigRate_;

		maxHistory = trigRate * maxHistory_seconds;

		if(normalizedRanges.notNil,{
			normalizedRangesForSynth = MIRAnalysis.normalizeRangesForSynth(normalizedRanges);
		});
		//"replyID: %".format(replyID).postln;

		synth = Synth(\mirLive++nChans_.asSymbol,[
			\inBus,liveInBus_,
			\replyID,replyID,
			\onsetThresh,onsetThresh,
			\onsetRelaxTime,onsetRelaxTime,
			\norms,normalizedRangesForSynth,
			\autoRange,autoRange_,
			\trigRate,trigRate,
			\lag,lag_
		],target_,addAction_);

		if(playing.not,{synth.run(false)});

		OSCFunc({
			arg msg;
			//msg.postln;
			if(msg[2] == replyID,{
				//var data = ();
				var malf;

				// MIRAnalysis.featureOrder.do({
				// 	arg feature, index;
				// 	data[feature] = msg[index+3];
				// });

				malf = MIRAnalysisLiveFrame(msg[3..(MIRAnalysis.nFeatures+2)]);

				malf.featureOrder_(MIRAnalysis.featureOrder);

				//data.vector = msg[3..(MIRAnalysis.nFeatures+2)];

				if(normalizedRanges.notNil,{
					//var normedString = "", rawString = "", nameString = "";
					/*data.normalized = ();
					data.normalized.vector = */
					malf.normalizedVector_(msg[(MIRAnalysis.nFeatures+3)..]); //NeuralNetwork.unmap(data.vector,normalizedRanges);
					/*					MIRAnalysis.featureOrder.do({
					arg feature, index;
					data.normalized[feature] = data.normalized.vector[index];
					});*/

					/*					MIRAnalysis.featureOrder.do({
					arg param;
					nameString = nameString ++ "% ".format(param.asStringff(8));
					rawString = rawString ++ "% ".format(data[param].asStringff(8));
					normedString = normedString ++ "% ".format(data.normalized[param].asStringff(8));
					});
					"%\n%\n%\n".format(nameString,rawString,normedString).postln;*/
				});

				vectorHistory = vectorHistory.addFirst(malf);
				if(vectorHistory.size > maxHistory,{
					vectorHistory.removeAt(maxHistory)
				});

				//malf.dispersionIndex_(MIRAnalysis.getDispersionIndex(vectorHistory));

				currentData = malf;
				action.value(malf,this);
			});
		},"/mirLive");

		OSCFunc({
			arg msg;
			//msg.postln;
			if(msg[2] == replyID,{
				action.value("onset");
			});
		},"/mirLiveOnset");

		^this;
	}

	getCurrentData {
		^currentData;
	}

	getRecentData_seconds {
		arg dur;
		if(dur > maxHistory_seconds,{
			Error("MIRAnalysis::getRecentData_seconds | dur % is too big. Max hisory in seconds is %".format(dur, maxHistory_seconds)).throw;
		},{
			var n_frames, hist;
			n_frames = (trigRate * dur) - 1;
			//n_frames.postln;
			hist = vectorHistory[0..n_frames.asInteger];
			//hist.postln;
			^hist;
		});
	}

	*normalizeRangesForSynth {
		arg normalizedRanges;
		^normalizedRanges.collect({
			arg cs;
			[cs.minval,cs.maxval];
		}).flatten;
	}

	*fftSize_ {
		arg size;
		fftSize = size;
		this.makeSynthDefs(maxNChans);
	}

	*addFiles_r {
		arg filesList, folderPathName;
		filesList = filesList.addAll(folderPathName.files);
		folderPathName.folders.do({
			arg folder;
			filesList = this.addFiles_r(filesList,folder);
		});
		^filesList;
	}

	*analyzeFolderNRT {
		arg folderPath, action, recursive = false, featureSmoothingLagOnServer = 0, finishedAction, trackingDict;
		var pn;
		var files = List.new;

		pn = PathName(folderPath);

		if(recursive,{
			files = this.addFiles_r(files,pn);
		},{
			files = pn.files;
		});

		files = files.select({
			arg fi;
			(fi.extension == "wav") ||
			(fi.extension == "aif") ||
			(fi.extension == "aiff")
		});

		if(trackingDict.isNil,{
			trackingDict = Dictionary.new;
		});

		files.do({
			arg pnfile;
			this.analyzeFileNRT(
				pnfile.fullPath,
				action,
				featureSmoothingLagOnServer,
				finishedAction,
				trackingDict
			);
		});
	}

	*analyzeFileNRT {
		arg filePath, action,featureSmoothingLagOnServer = 0, finishedAction, trackingDict, onsetThresh = 0.9, onsetRelaxTime = 0.1, trigRate = 30;
		var analysisfilename,analysisfilename2,analysisfilename3, ext;

		ext = PathName(filePath).extension;
		if((ext == "wav") || (ext == "aiff") || (ext == "aif"),{
			//PathName(filePath).fileNameWithoutExtension.postln;
			if(trackingDict.notNil,{
				trackingDict.put(filePath,false);
			});

			analysisfilename = "/tmp/%_nrt_analysis_buf_%.wav".format(Date.localtime.stamp,UniqueID.next);
			analysisfilename2 = "/tmp/%_nrt_analysis_buf_%.wav".format(Date.localtime.stamp,UniqueID.next);
			analysisfilename3 = "/tmp/%_nrt_analysis_buf_%.wav".format(Date.localtime.stamp,UniqueID.next);
			SoundFile.use(filePath,{
				arg sf;
				var fileDur,nChans,oscActions;
				fileDur = sf.duration;
				nChans = sf.numChannels;
				//fftDur = fftSize / sf.sampleRate;

				oscActions = [
					[0.0,[\b_alloc,0,fileDur / trigRate.reciprocal,buf1size]], // bufnum, frames, chans (+1 is for the onsets channel, which gets stripped off afterwards)
					[0.0,[\b_alloc,2,fileDur / trigRate.reciprocal,buf2size]],
					[0.0,[\b_alloc,3,fileDur / trigRate.reciprocal,buf3size]],
					[0.0,[\b_allocRead,1,filePath]],
					// wait a little time
					[0,[\s_new, \mirNrt++nChans.asSymbol, 1000, 0, 0, // name, id, addAction, addTarget
						\soundBuf,1, // start args
						\dataBuf,0,
						\dataBuf2,2,
						\dataBuf3,3,
						//\fftDur,fftDur,
						\lag,featureSmoothingLagOnServer,
						\onsetThresh,onsetThresh,
						\onsetRelaxTime,onsetRelaxTime,
						\trigRate,trigRate
					]],
					[fileDur,[\b_write,0,analysisfilename, "WAV", "float"]],
					[fileDur,[\b_write,2,analysisfilename2, "WAV", "float"]],
					[fileDur,[\b_write,3,analysisfilename3, "WAV", "float"]],
					[fileDur,[\c_set, 0, 0]]
				];

				Score.recordNRT(
					oscActions,
					outputFilePath:"/dev/null",
					options:ServerOptions.new.numOutputBusChannels_(1),
					action:{
						var buf;
						SoundFile.use(analysisfilename,{
							arg sf;
							var array, /*data = (),*/ dispersionData = [], onsetVector;
							array = FloatArray.newClear(sf.numFrames * sf.numChannels);
							sf.readData(array);
							array = array.clump(buf1size); // + 1 for onsets channel
							//"array size: %".format(array.size).postln;

							// strip off onsets
							onsetVector = array.flop.last;
							//"onset vector: %".format(onsetVector).postln;

							SoundFile.use(analysisfilename2,{
								arg sf_2;
								var array2 = FloatArray.newClear(sf_2.numFrames * sf_2.numChannels);
								sf_2.readData(array2);

								array2 = array2.clump(buf2size);

								//"array 2 shape: %".format(array2.shape).postln;

								SoundFile.use(analysisfilename3,{
									arg sf_3;
									var array3 = FloatArray.newClear(sf_3.numFrames * sf_3.numChannels);
									sf_3.readData(array3);

									array3 = array3.clump(buf3size);

									array = array.collect({
										arg frame, index;
										var time;//, nDispersionFrames;//, disp;
										var vec = frame[0..(frame.size-2)] ++ array2[index] ++ array3[index];// -2 to strip off the last value (which was onset info)
										time = index * trigRate.reciprocal;

										/*									time.postln;
										frame.postln;
										array2[index].postln;
										vec.postln;
										vec.size.postln;
										"".postln;*/
										[time,vec];
									});

									action.value(MIRAnalysisFile(
										array,
										fftSize,
										trigRate.reciprocal,
										filePath,
										nil,
										nil,//dispersionData,
										onsetVector,
										fileDur
									));

									if(trackingDict.notNil,{
										trackingDict.put(filePath,true);
										if(trackingDict.includes(false).not,{
											// finished with all them!
											finishedAction.value;
										});
									});
								});
							});
						});
				});
			});
		});
	}
}