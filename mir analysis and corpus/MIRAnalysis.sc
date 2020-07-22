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
			\amplitude,
			\fftCrest,
			\fftSlope,
			\fftSpread,
			\loudness,
			\sensoryDissonance,
			\specCentroid,
			\specFlatness,
			\specPcile,
			\zeroCrossing,
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
			\mfcc13
		];
	}

	*makeSynthDefs {
		arg maxChans;
		[\Nrt,\Live].do({
			arg mode;
			(1..maxChans).do({
				arg nChans;
				SynthDef(\mir++mode.asSymbol++nChans.asSymbol,{
					arg soundBuf, dataBuf, fftDur, trigRate = 30, inBus, replyID,onsetThresh = 0.9,onsetRelaxTime = 0.1, autoRange = 0, lag = 0;
					var sig,fft,trig,specCent,features, normedFeatures, norms, onsetTrigs;

					if(mode == \Nrt,{
						sig = Mix(PlayBuf.ar(nChans,soundBuf,BufRateScale.ir(soundBuf),doneAction:2));
						trig = DelayN.kr(Impulse.kr(fftDur.reciprocal),fftDur,fftDur);
					},{
						sig = Mix(In.ar(inBus,nChans));
						trig = DelayN.kr(Impulse.kr(trigRate),trigRate.reciprocal,trigRate.reciprocal)
					});

					//sig.postln;

					fft = FFT(LocalBuf(fftSize),sig);
					specCent = SpecCentroid.kr(fft);

					features = [
						Amplitude.kr(sig),
						FFTCrest.kr(fft),
						FFTSlope.kr(fft),
						FFTSpread.kr(fft,specCent),
						Loudness.kr(fft),
						SensoryDissonance.kr(fft),
						specCent,
						SpecFlatness.kr(fft),
						SpecPcile.kr(fft,0.9),
						A2K.kr(ZeroCrossing.ar(sig))
					] ++ MFCC.kr(fft); // 23 features

					features = Sanitize.kr(features);

					onsetTrigs = Onsets.kr(fft,onsetThresh,relaxtime:onsetRelaxTime);

					if(mode == \Nrt,{
						features = features.lag(lag);
						onsetTrigs = Trig1.kr(onsetTrigs,fftDur);
						Logger.kr(features ++ [onsetTrigs],trig,dataBuf);
					},{
						norms = \norms.kr(0.dup(this.nFeatures * 2));

						normedFeatures = features.collect({
							arg feature, index;
							feature.linlin(norms[index * 2], norms[(index * 2) + 1],0,1);
						});

						normedFeatures = Select.kr(autoRange,[
							normedFeatures,
							normedFeatures.collect({
								arg feature;
								AutoRange.kr(feature);
							})
						]);

						features = features ++ normedFeatures;

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
		arg liveInBus_, target_,addAction_,normalizedRanges_,nChans_ = 1, onsetThresh, onsetRelaxTime, autoRange_, lag_, action_, liveTrigRate = 30;
		liveInBus = liveInBus_;
		nChans = nChans_;
		replyID = UniqueID.next;
		action = action_;
		normalizedRanges = normalizedRanges_;
		playing = true;
		vectorHistory = [];
		trigRate = liveTrigRate;

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

				malf.dispersionIndex_(MIRAnalysis.getDispersionIndex(vectorHistory));

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

	*getDispersionIndex {
		arg malfs; // expects array of frames;
		var means, variances, dispersion;
		var frames = malfs.collect({
			arg malf;
			malf.vector;
		});
		//"disp frames: %".format(frames).postln;
		means = frames.mean;
		//"means:      %".format(means).postln;
		variances = frames.flop.collect({
			arg paramVector, index;
			(paramVector - means[index]).pow(2).sum / frames.size;
		});

		//"variances:   %".format(variances).postln;
		dispersion = variances / means;

		dispersion = dispersion.collect({
			arg dis;
			if(dis.isNaN,{dis = 0});
			dis;
		});

		//"dispersion 1: %".format(dispersion).postln;

		dispersion = dispersion.mean / 400000; // this is a rough normalization figure, found by testing a few options

		//"dispersion 2: %".format(dispersion).postln;

		if(dispersion.isNaN,{dispersion = 0});
		//"disp inside class method: %".format(dispersion).postln;
		^dispersion;
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

		/*pn = PathName(folderPath);

		if(recursive,{
		pn.folders.do({
		arg pnsubfolder;
		this.analyzeFolderNRT(
		pnsubfolder.fullPath,
		action,
		true,
		featureSmoothingLagOnServer,
		finishedAction,
		trackingDict
		);
		});
		});*/

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
		arg filePath, action,featureSmoothingLagOnServer = 0, finishedAction, trackingDict, onsetThresh = 0.9, onsetRelaxTime = 0.1;
		var analysisfilename, ext;

		ext = PathName(filePath).extension;
		if((ext == "wav") || (ext == "aiff") || (ext == "aif"),{
			//PathName(filePath).fileNameWithoutExtension.postln;
			if(trackingDict.notNil,{
				trackingDict.put(filePath,false);
			});

			analysisfilename = "/tmp/%_nrt_analysis_buf_%.wav".format(Date.myFormat,UniqueID.next);
			SoundFile.use(filePath,{
				arg sf;
				var fileDur,nChans,oscActions,fftDur;
				fileDur = sf.duration;
				nChans = sf.numChannels;
				fftDur = fftSize / sf.sampleRate;

				oscActions = [
					[0.0,[\b_alloc,0,fileDur / fftDur,this.nFeatures + 1]], // bufnum, frames, chans (+ 1 is for the onsets channel, which gets stripped off afterwards)
					[0.0,[\b_allocRead,1,filePath]],
					// wait a little time
					[0,[\s_new, \mirNrt++nChans.asSymbol, 1000, 0, 0, // name, id, addAction, addTarget
						\soundBuf,1, // start args
						\dataBuf,0,
						\fftDur,fftDur,
						\lag,featureSmoothingLagOnServer,
						\onsetThresh,onsetThresh,
						\onsetRelaxTime,onsetRelaxTime
					]],
					[fileDur,[\b_write,0,analysisfilename, "WAV", "float"]],
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
							array = array.clump(this.nFeatures + 1); // + 1 for onsets channel
							//"array size: %".format(array.size).postln;

							// strip off onsets
							onsetVector = array.flop.last;
							//"onset vector: %".format(onsetVector).postln;

							array = array.collect({
								arg frame, index;
								var time;//, nDispersionFrames;//, disp;
								time = index * fftDur;
								/*								time.postln;
								frame.postln;
								"".postln;*/
								//"frame: %".format(frame).postln;
								//nDispersionFrames = min(maxHistory-1,index);

								//disp = MIRAnalysis.getDispersionIndex(array[(index-nDispersionFrames)..index]);
								//"dispersion: %".format(disp).postln;
								//dispersionData = dispersionData.add(disp);

								[time,frame[0..(frame.size-2)]]; // -2 to strip off the last value (which was onset info)
							});

							/*							data.frames = array;
							data.fftSize = fftSize;
							data.fftDur = fftDur;
							data.path = filePath;
							data.featureOrder = this.featureOrder;
							data.dispersionIndexArray = dispersionData;
							data.onsetVector = onsetVector;*/
							action.value(MIRAnalysisFile(
								array,
								fftSize,
								fftDur,
								filePath,
								nil,
								nil,//dispersionData,
								onsetVector,
								fileDur
							));


							/*							if(trackingDict.notNil,{
							trackingDict.put(filePath,true);
							/*if(trackingDict.includes(false),{
							// not finished yet:
							var done = trackingDict.values.count({arg val; val});
							var total = trackingDict.values.size;
							var pct = ((done / total) * 100).round(0.1).asString.padLeft(5);
							total = total.asString.padLeft(6);
							done = done.asStringff.padLeft(6);
							"% of % --- %\\% complete".format(done,total,pct).postln;
							});*/
							});*/

							//trackingDict.postln;
							//"trackingDict.includes(false).not".postln;
							//trackingDict.includes(false).not.postln;
							if(trackingDict.notNil,{
								trackingDict.put(filePath,true);
								if(trackingDict.includes(false).not,{
									// finished with all them!
									finishedAction.value;
								});
							});

							/*							array.dopostln;
							"".postln;
							array[0].postln;
							array.size.postln;*/
						});
						/*buf = Buffer.read(server,analysisfilename);

						buf.loadToFloatArray(action:{
						arg floatArray;
						//floatArray.size.postln;
						floatArray = floatArray.clump(nFeatures);// clump to get each frame's vector
						action.value(floatArray);
						//~corpus = ~corpus.addAll(floatArray);
						});*/
				}); // synthesize
			});
		});
	}

	/*	*fftDur {
	fftSize / 44100; // really hope you don't have to call this...! keep track a yo shit.
	}*/
}