AEPython_variable_size {
	classvar current_osc_port = 11000;
	var pythonPath = "/Users/ted/Documents/_CREATING/_PROJECT FILES/Machine Learning/Analysis and Dimensionality Reduction/Autoencoders/generic_ae/generic_autoencoder_01.py";
	var >encodedAction, >decodedAction, osc_port, toAE, input_dim, latent_dim, id;

	*new {
		arg trainingData, latent_dim_size = 2, encodedAction, decodedAction,readyAction;
		^super.new.init(trainingData, latent_dim_size,encodedAction, decodedAction,readyAction);
	}

	init {
		arg trainingData, latent_dim_ = 2,encodedAction_, decodedAction_,readyAction;
		var masterFilePath = "/Users/ted/Documents/_CREATING/_PROJECT FILES/Machine Learning/Analysis and Dimensionality Reduction/Autoencoders/generic_ae/input_data/generic_ae_input_%".format(Date.myFormat);
		var inputFilePath = masterFilePath ++ ".csv";
		var outputFilePath = masterFilePath ++ "_output.csv";
		var args;

		input_dim = trainingData[0].size;
		latent_dim = latent_dim_.asInteger;

		osc_port = current_osc_port;
		current_osc_port = current_osc_port + 1;

		toAE = NetAddr("localhost",osc_port);
		id = UniqueID.next;

		encodedAction = encodedAction_;
		decodedAction = decodedAction_;

		ArrayToCSV(trainingData,inputFilePath);

		args = [
			"-i",inputFilePath,
			//"-epochs",1000,
			"-o",osc_port,
			"-e",input_dim,latent_dim,
			"-d",input_dim
		];

		RunInPython(pythonPath,args,{
			var ae_output = CSVFileReader.readInterpret(outputFilePath,true,true,$,);

			OSCFunc({
				arg msg;
				if(msg[1] == id,{
					decodedAction.value(msg[2..]);
				});
			},"/decoded");

			OSCFunc({
				arg msg;
				//msg.postln;
				if(msg[1] == id,{
					encodedAction.value(msg[2..]);
				});
			},"/encoded");

			readyAction.value(ae_output);

		},outputFilePath);
	}

	encode {
		arg vector;
		var msg;
		msg = ["/encode",id] ++ vector;
		toAE.sendMsg(*msg);
	}

	decode {
		arg vector;
		var msg;
		msg = ["/decode",id] ++ vector;
		toAE.sendMsg(*msg);
	}
}