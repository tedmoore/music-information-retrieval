SynthMIRNRT {
	//classvar uniqueID = 1000;
	var params;
	var time_counter;
	var pre_wait, post_wait;
	var input_msgs;
	var input_pts;
	var n_steps;
	var synthDef_to_analyze;
	var osc_actions;
	var save_path;
	var n_features = 53;
	var array_to_csv;
	var analysisfilename;
	var n_frames;
	var analysisfilename_melbands;
	var analysisfilename_chroma;

	*new {
		arg params_, save_path_, synthDef_to_analyze, n_steps_or_file = 10,pre_wait_ = 0.1,post_wait_ = 0.1,  audio_path = nil, action = nil, verbose = false;
		^super.new.init(params_, save_path_, synthDef_to_analyze, n_steps_or_file,pre_wait_,post_wait_, audio_path, action, verbose);
	}

	*initClass {
		StartUp.defer{
			SynthDef(\analysis_log_nrt,{
				arg audioInBus, analysis_buf, t_logger = 0;

				var sig = In.ar(audioInBus);

				// analysis
				var fft = FFT(LocalBuf(2048),sig);
				var mfcc = FluidMFCC.kr(sig,40);
				var spec = FluidSpectralShape.kr(sig);
				var pitch = FluidPitch.kr(sig);
				var loudness = FluidLoudness.kr(sig);
				//var chroma = Chromagram.kr(fft);
				var vector = mfcc ++ spec ++ pitch ++ /*chroma ++*/ loudness ++ [
					A2K.kr(ZeroCrossing.ar(sig)),
					SensoryDissonance.kr(fft)
				];

				// INDICES (you have to add the number of input params to get the right csv index offset):
				// mfccs 00-39
				// spec  40-46
				// pitch 47-48
				// loudness 49-50
				// zeroc 51
				// sensdis 52
				// mels 53-92
				// chroma 93-104

				//vector = vector.flatten;
				//sig.poll(label:"sig");

				//vector = Median.kr(31,vector);
				//vector.poll(1,"vector");
				//Trig1.kr(t_logger,0.4).poll(10,"t logger");
				Logger.kr(vector,t_logger,analysis_buf);
				Out.ar(0,sig);
			}).writeDefFile;

			SynthDef(\analysis_log_nrt_melbands,{
				arg audioInBus, analysis_buf, t_logger = 0;

				var sig = In.ar(audioInBus);

				// analysis
				var melBands = FluidMelBands.kr(sig,40,maxNumBands:40);

				//melBands = Median.kr(31,melBands);
				//vector.poll(1,"vector");
				//Trig1.kr(t_logger,0.4).poll(10,"t logger");
				Logger.kr(melBands,t_logger,analysis_buf);
				Out.ar(0,sig);
			}).writeDefFile;

			SynthDef(\analysis_log_nrt_chroma,{
				arg audioInBus, analysis_buf, t_logger = 0;

				var sig = In.ar(audioInBus);

				// analysis
				var chroma = Chromagram.kr(FFT(LocalBuf(2048),sig),2048);

				//chroma = Median.kr(31,chroma);
				//vector.poll(1,"vector");
				//Trig1.kr(t_logger,0.4).poll(10,"t logger");
				Logger.kr(chroma,t_logger,analysis_buf);
				Out.ar(0,sig);
			}).writeDefFile;
		}
	}

	create_inputs_from_csv {
		arg path, verbose;
		var csv = CSVFileReader.readInterpret(path,true,true); // these should be normalized because it will use the scalars you passed to scale them!!!!!!!!

		input_msgs = List.new;
		input_pts = List.new;
		/*
		csv.postln;
		csv.shape.postln;*/

		csv.do({
			arg input_pt;
			var sub_array = List.new;
			var input_pt_sub_array = List.new;

			sub_array.addAll([\n_set,1001]);

			input_pt.do({
				arg normed_val, i;
				var val = params[i][1].map(normed_val);
				sub_array.addAll([params[i][0]/*name*/,val]);
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
		arg params_, save_path_, synthDef_to_analyze, n_steps_or_file_ = 10,pre_wait_ = 0.1,post_wait_ = 0.1, audio_path_ = nil, action = nil, verbose = false;

		//n_features = 40;//88;//92;//104;

		params = params_;
		n_steps = n_steps_or_file_;
		pre_wait = pre_wait_;
		post_wait = post_wait_;
		save_path = save_path_;

		synthDef_to_analyze.writeDefFile;

		time_counter = 3;
		analysisfilename = "/tmp/%_nrt_analysis_buf_%.wav".format(Date.localtime.stamp,UniqueID.next);
		analysisfilename_melbands = "/tmp/%_nrt_analysis_buf_melbands_%.wav".format(Date.localtime.stamp,UniqueID.next);
		analysisfilename_chroma = "/tmp/%_nrt_analysis_buf_chroma_%.wav".format(Date.localtime.stamp,UniqueID.next);

		if(n_steps.isString,{
			n_frames = this.create_inputs_from_csv(n_steps,verbose);
		},{
			n_frames = n_steps.pow(params.size);
			this.create_input_msgs(verbose);
		});

		//"n frames: %".format(n_frames).postln;

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
			[0.0,[\s_new,synthDef_to_analyze.name.asSymbol,1001,0,0,
				\outBus,11
			]],
		];

		//"class: %".format(osc_actions.class).postln;
		//time_counter = 3;

		osc_actions = osc_actions ++ input_msgs;

		time_counter = time_counter + 1;
		osc_actions = osc_actions ++ [
			[time_counter,[\b_write,0,analysisfilename, "WAV", "float"]],
			[time_counter,[\b_write,1,analysisfilename_melbands, "WAV", "float"]],
			[time_counter,[\b_write,2,analysisfilename_chroma, "WAV", "float"]],
			[time_counter + 1,[\c_set, 0, 0]]
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
		this.create_input_msgs_r(params,0,nil,n_steps, verbose);
	}

	create_input_msgs_r {
		arg params_, layer = 0, current_frame = nil, n_steps_ = 3, verbose;

		if(layer < params_.size,{
			n_steps_.do({
				arg i;
				var i_n = params_[layer][1].map(i.linlin(0,n_steps_-1,0,1));

				if(current_frame.isNil,{
					current_frame = Array.newClear(params_.size);
				});
				current_frame[layer] = i_n;
				this.create_input_msgs_r(params_,layer + 1, current_frame.copy, n_steps_, verbose);
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

			if(verbose,{sub_array.postln});
		});
	}

	runAnalysis {
		arg audio_path, action, verbose;
		var out_file_path = "/dev/null";

		if(audio_path.notNil,{
			out_file_path = audio_path;
		});

		//osc_actions.dopostln;

		"out file path: %".format(out_file_path).postln;
		Score.recordNRT(
			osc_actions,
			outputFilePath:out_file_path,
			//headerFormat:"wav",
			options:ServerOptions.new.numOutputBusChannels_(1),
			//duration:time_counter + 2,
			action:{
				//analysisfilename.postln;
				SoundFile.use(analysisfilename,{
					arg sf;
					var array;

					array_to_csv = ArrayToCSV.open(save_path);

					/*					sf.postln;
					sf.numFrames.postln;
					sf.numChannels.postln;
					Buffer.readChannel(Server.default,sf.path,channels:[0,1,2],action:{
					arg buf;
					defer{buf.plot};
					});*/

					//"n frames: %".format(n_frames).postln;

					//osc_actions.dopostln;

					array = FloatArray.newClear(sf.numFrames * sf.numChannels);

					//"array1: %, %".format(array.size,array).postln;

					sf.readData(array);

					//"array2: %, %".format(array.size,array).postln;

					//"n features: %".format(n_features).postln;
					array = array.clump(n_features);

					//"array3: %, %".format(array.size,array).postln;

					SoundFile.use(analysisfilename_melbands,{
						arg sf_mb;
						var array_mb = FloatArray.newClear(sf_mb.numFrames * sf_mb.numChannels);

						sf_mb.readData(array_mb);
						array_mb = array_mb.clump(40); // n mel bands;

						SoundFile.use(analysisfilename_chroma,{
							arg sf_ch;

							var array_ch = FloatArray.newClear(sf_ch.numFrames * sf_ch.numChannels);
							var labels_array = List.new;

							sf_ch.readData(array_ch);
							array_ch = array_ch.clump(12); // n mel bands;

							// input points
							labels_array.addAll(params.collect({
								arg param_array;
								param_array[0].asString;
							}));

							/*var vector = mfcc ++ spec ++ pitch ++ /*chroma ++*/ loudness ++ [
							A2K.kr(ZeroCrossing.ar(sig)),
							SensoryDissonance.kr(fft)
							];*/

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

							array_to_csv.writeLine(labels_array);

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

							//analysisfilename.postln;

							//osc_actions.dopostln;
							action.value;
						});
					});
				});
		});
	}
}