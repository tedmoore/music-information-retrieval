AEPython {
	classvar pythonPath = "/Users/ted/Documents/_CREATING/_PROJECT FILES/Machine Learning/Analysis and Dimensionality Reduction/Autoencoders/auto_encoder_for_mirc/autoencoder_for_mirc.py";

	*new {
		arg inputData,action, z = 2, epochs = 100;
		^super.new.init(inputData,action,z,epochs);
	}

	init {
		arg inputData,action,z = 2, epochs = 100;
		this.fit_transform(inputData,action,z,epochs);
	}

	fit_transform {
		arg inputData,action,z = 2,epochs = 100;
		var timeStamp = Date.myFormat;
		var inputFilePath = Platform.userAppSupportDir+/+"tmp/ae_input_%.csv".format(timeStamp);
		var outputFilePath = Platform.userAppSupportDir+/+"tmp/ae_input_%_output.csv".format(timeStamp);
		var inputFile = File(inputFilePath,"w");
		var args;

		inputData.do({
			arg line;
			line.do({
				arg val, i;
				if(i != 0,{
					inputFile.write(",");
				});
				inputFile.write(val.asString);
			});
			inputFile.write("\n");
		});

		inputFile.close;

		args = ["-i",inputFilePath,"-z",z,"-e",epochs];

		RunInPython(pythonPath,args,{
			arg outputFilePath;
			var coordinates = CSVFileReader.readInterpret(outputFilePath,true);
			action.value(coordinates);
		},outputFilePath);
	}
}