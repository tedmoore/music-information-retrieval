(
~stamp = Date.localtime.stamp;
~dir = "/Users/ted/Desktop/SCD/timbral_space_mapping/%".format(~stamp);
File.mkdir(~dir);

SynthMIRNRT([
		[
			\cfreq,
			ControlSpec(20,20000,\exp),
			2
		],[
			\mfreq,
			ControlSpec(20,20000,\exp),
			3
		],[
			\index,
			ControlSpec(0,20,\lin),
			4
		]
	],
	~dir+/+"%_analysis.csv".format(~stamp),
	SynthDef(\fm_test_nrt,{
		arg cfreq = 20, mfreq = 20, index = 0, outBus = 0;

		// synth stuff
		var sig = SinOsc.ar(cfreq + SinOsc.ar(mfreq,0,index * mfreq));
		//[cfreq,mfreq,index].poll;

		Out.ar(outBus,sig);
	}),
	0.5, // 5th argument is pre-wait: duration (in NRT) between setting the input parameters and recording the sample of audio descriptors
	0.1, // 6th argument is post-wait: duration (in NRT) between setting the recording the sample of audio descriptors and setting the next input parameters
	nil, // 7th argument is where to put the "audio file" of data, leaving it nil will use temp dir
	{"============== DONE =============".postln;}, // 8th arg: done action
	true // 9th arg: verbosity
);
)