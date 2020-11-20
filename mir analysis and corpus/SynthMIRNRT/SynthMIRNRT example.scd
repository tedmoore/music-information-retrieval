(
~dir = "/Users/ted/Desktop/SCD/flucoma/nn fm testing/";
File.mkdir(~dir);
~stamp = Date.localtime.stamp;

SynthMIRNRT(

	// 1st argument is an array arrays. each contains
	//           (1) the name of the param (as will be passed to the synth) and
	//           (2) a control spec for how this para should be scaled (from 0-1 to what the synth expects)

	[
		[
			\cfreq,
			ControlSpec(20,20000,\exp)
		],[
			\mfreq,
			ControlSpec(20,20000,\exp)
		],[
			\index,
			ControlSpec(0,20,\lin)
		]
	],

	// 2nd argument is the output location for the csv file

	"%/%_nn_fm_poisson=37542.csv".format(~dir,~stamp),

	// 3rd argument is the synth that you want to exctract descriptors from - NB: needs to have an "outBus" argument !!!!

	SynthDef(\fm_test_nrt,{
		arg cfreq = 20, mfreq = 20, index = 0, outBus = 0;

		// synth stuff
		var sig = SinOsc.ar(cfreq + SinOsc.ar(mfreq,0,index * mfreq));
		//[cfreq,mfreq,index].poll;

		Out.ar(outBus,sig);
	}),

	// 4th argument is either:
	//        - an integer of how many steps you divide each input dimension by in normalized space (e.g., 5 would sample that dimension
	//          at 0, 0.25, 0.5, 0.75, and 1 and then scale that up by the Control spec) (also 5 with three dimensions would be pow(5,3) = 125 data points
	//        - or a path to a csv file with the (normalized) data points you want to use to do the sampling.

	"/Volumes/Ted's 10TB My Book (June 2020)/PROJECT FILES/machine learning/Sampling/Poisson Sampling/poisson_sampling_n_dims/generated_samples/poisson_sample_set_ndims=3_npoints=37542_r=0.03_k=20_2020-07-16_20-39-56.csv",

	0.5, // 5th argument is pre-wait: duration (in NRT) between setting the input parameters and recording the sample of audio descriptors
	0.1, // 6th argument is post-wait: duration (in NRT) between setting the recording the sample of audio descriptors and setting the next input parameters
	nil, // 7th argument is where to put the "audio file" of data, leaving it nil will use temp dir
	{"============== DONE =============".postln;}, // 8th arg: done action
	false // 9th arg: verbosity
);
)