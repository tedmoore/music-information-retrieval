SynthMIRNRT {
	//classvar uniqueID = 1000;
	var params;
	var time_counter;
	var pre_wait, post_wait;
	var input_msgs;
	var input_pts;
	var synthDef_to_analyze;
	var osc_actions;
	var save_path;
	var n_features = 53; // in first synth!
	var array_to_csv;
	var analysisfilename;
	var n_frames;
	var analysisfilename_melbands;
	var analysisfilename_chroma;
	var numChans;
	var labels_array;
	var n_active_params;

	*new {
		arg params_, save_path_, synthDef_to_analyze,pre_wait_ = 0.1,post_wait_ = 0.1,  audio_path = nil, action = nil, verbose = false, numChans_ = 1, csv_data_points = nil;
		^super.new.init(params_, save_path_, synthDef_to_analyze,pre_wait_,post_wait_, audio_path, action, verbose, numChans_, csv_data_points);
	}

	makeSynthDefs {
		arg numChans;
		SynthDef(\analysis_log_nrt,{
			arg audioInBus, analysis_buf, t_logger = 0;
			var ogsig = In.ar(audioInBus,numChans);
			var sig = Mix(ogsig) * numChans.reciprocal;
			var fft = FFT(LocalBuf(1024),sig);
			var mfcc = FluidMFCC.kr(sig,40);
			var spec = FluidSpectralShape.kr(sig);
			var pitch = FluidPitch.kr(sig);
			var loudness = FluidLoudness.kr(sig);
			var vector = mfcc ++ spec ++ pitch ++ /*chroma ++*/ loudness ++ [
				A2K.kr(ZeroCrossing.ar(sig)),
				SensoryDissonance.kr(fft)
			];
			Logger.kr(vector,t_logger,analysis_buf);
			Out.ar(0,ogsig);
		}).writeDefFile;

		SynthDef(\analysis_log_nrt_melbands,{
			arg audioInBus, analysis_buf, t_logger = 0;
			var sig = Mix(In.ar(audioInBus,numChans)) * numChans.reciprocal;
			var melBands = FluidMelBands.kr(sig,40,maxNumBands:40);
			Logger.kr(melBands,t_logger,analysis_buf);
			//Out.ar(0,sig);
		}).writeDefFile;

		SynthDef(\analysis_log_nrt_chroma,{
			arg audioInBus, analysis_buf, t_logger = 0;
			var sig = Mix(In.ar(audioInBus,numChans)) * numChans.reciprocal;
			var chroma = Chromagram.kr(FFT(LocalBuf(1024),sig),1024);
			Logger.kr(chroma,t_logger,analysis_buf);
			//Out.ar(0,sig);
		}).writeDefFile;
	}

	create_inputs_from_csv {
		arg path, verbose;
		var csv = CSVFileReader.readInterpret(path,true,true); // these should be normalized because it will use the scalars you passed to scale them!!!!!!!!

		if(n_active_params != csv[0].size,{
			"Number of active params is not equal to the number of dimensions in the csv file.".error;
		});

		n_frames = csv.size;

		input_msgs = List.new;
		input_pts = List.new;

		/*
		csv.postln;
		csv.shape.postln;*/

		csv.do({
			arg input_pt;
			var sub_array = List.new;
			var input_pt_sub_array = List.new;
			var input_idx = 0;

			sub_array.addAll([\n_set,1001]);

			params.do({
				arg param, i;
				var name = param[0];
				var val;

				if(param[1].isKindOf(ControlSpec),{
					var normed_val = input_pt[input_idx];
					val = param[1].map(normed_val);
					input_idx = input_idx + 1;
				},{
					val = param[1];
				});

				sub_array.addAll([name,val]);
				input_pt_sub_array.add(val);
			});

			input_pts.add(input_pt_sub_array);

			input_msgs.add([time_counter,sub_array.asArray]);
			time_counter = time_counter + pre_wait;
			input_msgs.add([time_counter,[\n_set,1000,\t_logger,1]]);
			input_msgs.add([time_counter,[\n_set,1002,\t_logger,1]]);
			input_msgs.add([time_counter,[\n_set,1003,\t_logger,1]]);
			time_counter = time_counter + post_wait;

			if(verbose,{sub_array.postln});
		});

		^input_pts.size;
	}

	/*	*nextUniqueID {
	uniqueID = uniqueID + 1;
	^uniqueID;
	}*/

	init {
		arg params_, save_path_, synthDef_to_analyze,pre_wait_ = 0.1,post_wait_ = 0.1, audio_path_ = nil, action = nil, verbose = false, numChans_ = 1, csv_data_points = nil;
		var log = ArrayToCSV.open(save_path_+/+"log.csv");
		var synthDef_to_analyze_name;
		//n_features = 40;//88;//92;//104;

		params = params_;
		pre_wait = pre_wait_;
		post_wait = post_wait_;
		save_path = save_path_;
		numChans = numChans_;

		this.makeSynthDefs(numChans);

		if(synthDef_to_analyze.isSymbolWS,{
			synthDef_to_analyze_name = synthDef_to_analyze;
		},{
			synthDef_to_analyze.writeDefFile;
			synthDef_to_analyze_name = synthDef_to_analyze.name;
		});

		time_counter = 0.0;
		analysisfilename = "/tmp/%_nrt_analysis_buf_%.wav".format(Date.localtime.stamp,UniqueID.next);
		analysisfilename_melbands = "/tmp/%_nrt_analysis_buf_melbands_%.wav".format(Date.localtime.stamp,UniqueID.next);
		analysisfilename_chroma = "/tmp/%_nrt_analysis_buf_chroma_%.wav".format(Date.localtime.stamp,UniqueID.next);

		log.writeLine(["SynthDef name: %".format(synthDef_to_analyze_name.asString)]);
		log.writeLine(["pre_wait",pre_wait]);
		log.writeLine(["post_wait",post_wait]);
		log.writeLine(["save_path",save_path]);
		log.writeLine(["audio_path",audio_path_]);

		n_active_params = params.select({arg param; param[1].isKindOf(ControlSpec)}).size;

		if(csv_data_points.isNil,{
			log.writeLine(["name","min","max","warp","step","n_steps_for_analysis"]);
			n_frames = 1;
			params.do({
				arg param;
				var is_active = param[1].isKindOf(ControlSpec);
				if(is_active,{
					n_frames = n_frames * param[2];
					log.writeLine([param[0],param[1].minval,param[1].maxval,param[1].warp.class.asString,param[1].step,param[2]]);
				},{
					log.writeLine([param[0],param[1]]);
				});
			});
			this.create_input_msgs(verbose);
		},{
			log.writeLine(["params taken from csv file:",csv_data_points]);
			log.writeLine(["name","min","max","warp","step"]);
			params.do({
				arg param;
				var is_active = param[1].isKindOf(ControlSpec);
				if(is_active,{
					log.writeLine([param[0],param[1].minval,param[1].maxval,param[1].warp.class.asString,param[1].step]);
				},{
					log.writeLine([param[0],param[1]]);
				});
			});
			this.create_inputs_from_csv(csv_data_points,verbose);
		});

		labels_array = List.new;
		labels_array.addAll(params.collect({
			arg param_array;
			// "param array: %".format(param_array).postln;
			param_array[0].asString;
		}));

		labels_array.addAll(40.collect({
			arg i_;
			"mfcc%".format(i_.asString.padLeft(2,"0"));
		}));

		labels_array.addAll([
			"spec_centroid",
			"spec_spread",
			"spec_skewness",
			"spec_kurtosis",
			"spec_rolloff",
			"spec_flatness",
			"spec_crest",
			"pitch",
			"pitch_confidence",
			"loudness",
			"loudness_truepeak",
			"zero_crossing",
			"sensory_dissonance"
		]);

		labels_array.addAll(40.collect({
			arg i_;
			"melband%".format(i_.asString.padLeft(2,"0"));
		}));

		labels_array.addAll(12.collect({
			arg i_;
			"chromagram%".format(i_.asString.padLeft(2,"0"));
		}));

		log.writeLine(["labels of columns:"]);
		labels_array.do({
			arg label, i;
			log.writeLine([i,label]);
		});

		log.close;

		osc_actions = [
			[0.0,[\b_alloc,0,n_frames.asInteger,n_features.asInteger]],
			[0.0,[\b_alloc,1,n_frames.asInteger,40]],// mel bands
			[0.0,[\b_alloc,2,n_frames.asInteger,12]],// chroma
			[0.0,[\s_new, \analysis_log_nrt, 1000, 0, 0, // name, id, addAction, addTarget
				\audioInBus,11, // start args
				\analysis_buf,0
			]],
			[0.0,[\s_new, \analysis_log_nrt_melbands, 1002, 0, 0, // name, id, addAction, addTarget
				\audioInBus,11, // start args
				\analysis_buf,1
			]],
			[0.0,[\s_new, \analysis_log_nrt_chroma, 1003, 0, 0, // name, id, addAction, addTarget
				\audioInBus,11, // start args
				\analysis_buf,2
			]],
			[0.0,[\s_new,synthDef_to_analyze_name,1001,0,0,
				\outBus,11
			]],
		];

		osc_actions = osc_actions ++ input_msgs; // insert them all

		//time_counter = time_counter + 1; // i dont think i need this, i'm trying to remove extraneous time so that i can analyze the file...
		osc_actions = osc_actions ++ [
			[time_counter,[\b_write,0,analysisfilename, "WAV", "float"]],
			[time_counter,[\b_write,1,analysisfilename_melbands, "WAV", "float"]],
			[time_counter,[\b_write,2,analysisfilename_chroma, "WAV", "float"]],
			[time_counter,[\c_set, 0, 0]]
		];

		//osc_actions.dopostln;
		//input_msgs.postln;
		//input_pts.postln;
		this.runAnalysis(audio_path_,action,verbose);
	}

	create_input_msgs {
		arg verbose;
		input_msgs = List.new;
		input_pts = List.new;
		this.create_input_msgs_r(params,0,nil, verbose);
	}

	create_input_msgs_r {
		arg params_, layer = 0, current_frame = nil, verbose;

		/*		"create input msg r:".postln;
		layer.postln;
		current_frame.postln;
		"".postln;*/

		if(layer < params_.size,{
			params_[layer][2].do({
				arg i;
				var i_n = params_[layer][1].map(i.linlin(0,params_[layer][2]-1,0,1));

				if(current_frame.isNil,{
					current_frame = Array.newClear(params_.size);
				});
				current_frame[layer] = i_n;
				this.create_input_msgs_r(params_,layer + 1, current_frame.copy, verbose);
			});
		},{
			var sub_array = List.new;
			sub_array.addAll([\n_set,1001]);

			params_.do({
				arg param, j;
				sub_array.addAll([param[0],current_frame[j]]);
			});

			sub_array = sub_array.asArray;

			input_msgs.add([time_counter,sub_array]);
			time_counter = time_counter + pre_wait;
			input_msgs.add([time_counter,[\n_set,1000,\t_logger,1]]);
			input_msgs.add([time_counter,[\n_set,1002,\t_logger,1]]);
			input_msgs.add([time_counter,[\n_set,1003,\t_logger,1]]);
			time_counter = time_counter + post_wait;

			input_pts.add(current_frame);

			if(verbose,{"sub array: %".format(sub_array).postln});
		});
	}

	runAnalysis {
		arg audio_path, action, verbose;
		var out_file_path = "/dev/null";

		if(audio_path.notNil,{
			out_file_path = audio_path;
		});

		//osc_actions.dopostln;

		// "out file path: %".format(out_file_path).postln;

		// "params before nrt: %".format(params).postln;
		Score.recordNRT(
			osc_actions,
			outputFilePath:out_file_path,
			//headerFormat:"wav",
			options:ServerOptions.new.numOutputBusChannels_(numChans),
			//duration:time_counter + 2,
			action:{
				//analysisfilename.postln;
				SoundFile.use(analysisfilename,{
					arg sf;
					var array;

					array_to_csv = ArrayToCSV.open(save_path+/+"analysis.csv");

					array = FloatArray.newClear(sf.numFrames * sf.numChannels);

					sf.readData(array);
					array = array.clump(n_features);

					// "first sf done".postln;

					SoundFile.use(analysisfilename_melbands,{
						arg sf_mb;
						var array_mb = FloatArray.newClear(sf_mb.numFrames * sf_mb.numChannels);

						sf_mb.readData(array_mb);
						array_mb = array_mb.clump(40); // n mel bands;

						// "second sf done".postln;

						SoundFile.use(analysisfilename_chroma,{
							arg sf_ch;

							var array_ch = FloatArray.newClear(sf_ch.numFrames * sf_ch.numChannels);

							sf_ch.readData(array_ch);
							array_ch = array_ch.clump(12); // chroma

							// input points
							// "params: %".format(params).postln;

							array_to_csv.writeLine(labels_array);

							/*							"array: %".format(array).postln;
							"input points: %".format(input_pts).postln;
							//"frame: %".format(frame).postln;
							"array_mb: %".format(array_mb).postln;
							"array_ch: %".format(array_ch).postln;*/
							array.do({
								arg frame, index;
								var line = input_pts[index] ++ frame ++ array_mb[index] ++ array_ch[index];

								/*								index.postln;
								line.postln;
								line.size.postln;
								"".postln;*/

								array_to_csv.writeLine(line);
							});

							array_to_csv.close;

							// INDICES (you have to add the number of input params to get the right csv index offset):
							// mfccs 00-39
							// spec  40-46
							// pitch 47-48
							// loudness 49-50
							// zeroc 51
							// sensdis 52
							// mels 53-92
							// chroma 93-104

							action.value;
						});
					});
				});
		});
	}
}