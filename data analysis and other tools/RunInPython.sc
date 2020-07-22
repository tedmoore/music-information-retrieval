/*
Ted Moore
ted@tedmooremusic.com
www.tedmooremusic.com
May 25, 2019

SuperCollider convenience class for passing data to a Python script and awaiting it's return

*/

RunInPython {

	*new {
		arg pythonPath,args,action,outputFilePath,verbose=false, python3 = true;
		^super.new.init(pythonPath,args,action,outputFilePath,verbose,python3);
	}

	init {
		arg pythonPath,args,action,outputFilePath,verbose=false,python3=true;
		var version;

		if(python3,{version=3},{version=""});

		Routine({
			var cmd, startTime;

			//"routine running".postln;

			// this is the actual unix command that will run, however if you want to run it in
			// windows, this probably has to change... check the String help file for how to run
			// things in the windows terminal...
			pythonPath = pythonPath.escapeChar($ ).escapeChar($');

			cmd = "python% %".format(version,pythonPath);

			if(args.notNil,{
				args.do({
					arg ar;
					cmd = cmd + ar.asString.escapeChar($ ).escapeChar($');
				});
			});

			startTime = Process.elapsedTime;

			if(verbose,{cmd.postln;});
			// run the command in the terminal
			cmd.runInTerminal;

			//"out file path: %".format(outputFilePath).postln;
			if(outputFilePath.notNil,{
				while({
					// just keep on checking to see if the output file exists yet...
					File.exists(outputFilePath).not;
				},{
					// wait 1 second in between checking
					1.wait;
					if(verbose,{"looking for: %".format(outputFilePath).postln});
					"Python running for %".format(
						(Process.elapsedTime - startTime).asTimeString
					).postln;
				});
				// once the file exists, break out of the while loop and execute the action,
				// passing the output file path as the argument... so you'll have to load and
				// unpack it yourself, probably using something like the CSVFileReader class
				action.value(outputFilePath);
			},{
				action.value(nil);
			});
		}).play;
	}
}