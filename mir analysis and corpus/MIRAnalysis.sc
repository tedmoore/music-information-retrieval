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
	var <frames,<fftSize,<grain_dur,<path,<fft,<dispersionIndicies,<onsets, <duration, <nChannels;

	*initClass {
		featureOrder = MIRAnalysis.featureOrder;
	}

	*new {
		/*
		array,
		fftSize,
		grain_dur,
		filePath,
		onsetVector,
		fileDur
		*/
		arg frames,fftSize,grain_dur,path,onsets,duration,nChans;
		^super.new.init(frames,fftSize,grain_dur,path,onsets,duration,nChans);
	}

	init {
		arg frames_, fftSize_, grain_dur_, path_, onsets_, duration_,nChans_;
		frames = frames_;
		fftSize = fftSize_;
		grain_dur = grain_dur_;
		path = path_;
		onsets = onsets_;
		duration = duration_;
		nChannels = nChans_;
	}

	getDefinedVectorFrames {
		arg defVec;
		var sourceFrames, outFrames;
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
			\specCent,// 0
			\specSpread,// 1
			\specSkewness,// 2
			\specKurtosis,// 3
			\specRolloff,// 4
			\specFlatness,// 5
			\specCrest,// 6
			\pitch,// 7
			\pitchConfidence,// 8
			\loudness,// 9
			\truePeak,// 10
			\amplitude,// 11
			\senseDis,// 12
			\zeroCrossing,// 13
			\mfcc00,// 14
			\mfcc01,// 14
			\mfcc02,// 14
			\mfcc03,// 14
			\mfcc04,// 14
			\mfcc05,// 14
			\mfcc06,// 14
			\mfcc07,// 14
			\mfcc08,// 14
			\mfcc09,// 14
			\mfcc10,// 14
			\mfcc11,// 14
			\mfcc12,// 14
			\mfcc13,// 14
			\mfcc14,// 14
			\mfcc15,// 14
			\mfcc16,// 14
			\mfcc17,// 14
			\mfcc18,// 14
			\mfcc19,// 14
			\mfcc20,// 14
			\mfcc21,// 14
			\mfcc22,// 14
			\mfcc23,// 14
			\mfcc24,// 14
			\mfcc25,// 14
			\mfcc26,// 14
			\mfcc27,// 14
			\mfcc28,// 14
			\mfcc29,// 14
			\mfcc30,// 14
			\mfcc31,
			\mfcc32,
			\mfcc33,
			\mfcc34,
			\mfcc35,
			\mfcc36,
			\mfcc37,
			\mfcc38,
			\mfcc39,// 53
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
						Amplitude.kr(sig),
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

	*analyzeFilesArrayNRT {
		arg files, action, grain_dur, featureSmoothingLagOnServer = 0, finishedAction;
		var trackingDict = Dictionary.new;

		files.do({
			arg path;
			this.analyzeFileNRT(
				path,
				action,
				featureSmoothingLagOnServer,
				finishedAction,
				trackingDict,
				grain_dur:grain_dur
			);
		});
	}

	*analyzeFileNRT {
		arg filePath, action,featureSmoothingLagOnServer = 0, finishedAction, trackingDict, onsetThresh = 0.9, onsetRelaxTime = 0.1, grain_dur = 0.05;
		var analysisfilename,analysisfilename2,analysisfilename3, ext;
		var trigRate = grain_dur.reciprocal;

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
					[0.0,[\b_allocRead,0,filePath]],
					[0.0,[\b_alloc,1,fileDur / grain_dur,buf1size]], // bufnum, frames, chans (+1 is for the onsets channel, which gets stripped off afterwards)
					[0.0,[\b_alloc,2,fileDur / grain_dur,buf2size]],
					[0.0,[\b_alloc,3,fileDur / grain_dur,buf3size]],
					[0,[\s_new, \mirNrt++nChans.asSymbol, 1000, 0, 0, // name, id, addAction, addTarget
						\soundBuf,0, // start args
						\dataBuf,1,
						\dataBuf2,2,
						\dataBuf3,3,
						\lag,featureSmoothingLagOnServer,
						\onsetThresh,onsetThresh,
						\onsetRelaxTime,onsetRelaxTime,
						\trigRate,trigRate
					]],
					[fileDur,[\b_write,1,analysisfilename, "WAV", "float"]],
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
						//[analysisfilename,analysisfilename2,analysisfilename3].postln;
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

									//"array 3 shape: %".format(array3).shape;

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
										grain_dur,
										filePath,
										onsetVector,
										fileDur,
										nChans
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