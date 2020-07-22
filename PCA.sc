/*
Ted Moore
ted@tedmooremusic.com
www.tedmooremusic.com
May 25, 2019

script for running PCA analysis is SuperCollider by passing data off to Python for processing in sklearn and then passing it back.

requires python script as well as "RunInPython" SuperColldier class

*/

PCA {
	//classvar pythonPath = "/Users/Ted/Library/Application Support/SuperCollider/Extensions/mir/python/pca_get_proj_matrix.py";
	classvar pythonPath = "/Users/ted/Documents/_CREATING/_PROJECT FILES/Machine Learning/Analysis and Dimensionality Reduction/PCA/pca_get_proj_matrix_running.py";
	classvar currentOSCPort = 9000;

	var <>inputData, <pcaData, <explainedVarianceRatios, <nComponents, projectionMatrix, <normRanges, outputFilePath, privateOSCPort, oscAddr, transformID = 0;

	*new {
		arg nComponents;
		^super.new.init(nComponents);
	}

	init {
		arg nComponents_;
		nComponents = nComponents_;
		privateOSCPort = currentOSCPort;
		currentOSCPort = currentOSCPort + 1;
		oscAddr = NetAddr("127.0.0.1",privateOSCPort);
		^this;
	}

	normalize {
		arg d;
		normRanges = d[0].size.collect({
			ControlSpec(inf,-inf);
		});

		d.do({
			arg frame, i;
			frame.do({
				arg val, j;
				if(val < normRanges[j].minval,{normRanges[j].minval = val});
				if(val > normRanges[j].maxval,{normRanges[j].maxval = val});
			});
		});

		d = d.collect({
			arg frame;
			frame.collect({
				arg val, i;
				normRanges[i].unmap(val);
			});
		});

		^Matrix.with(d);//.center;
	}

	writeFile {
		var inputFilePath, inputFile;
		var timeStamp = Date.myFormat;
		inputFilePath = Platform.userAppSupportDir+/+"tmp/%_%.csv".format("pcaInputFile",timeStamp);
		outputFilePath = Platform.userAppSupportDir+/+"tmp/%_%_output.csv".format("pcaInputFile",timeStamp);
		//inputFilePath = inputFilePath.absolutePath;
		inputFile = File(inputFilePath,"w");

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
		^inputFilePath;
	}

	fit {
		arg inputData_, action;
		var inputFilePath;
		var args;

		inputData = inputData_;

		inputData = this.normalize(inputData);

		inputFilePath = this.writeFile;

		//"python path:     %".format(pythonPath).postln;
		//"input file path: %".format(inputFilePath).postln;
		args = ["-i",inputFilePath,"-c",nComponents,"-o",privateOSCPort];
		RunInPython(pythonPath,args,{
			arg outputFilePath;
			var outData = CSVFileReader.readInterpret(outputFilePath,true);

			explainedVarianceRatios = outData[0];
			nComponents = outData[1][0];
			projectionMatrix = Matrix.with(outData[2..(nComponents+1)]);
			pcaData = outData[(nComponents+2)..];

			action.value(this);
		},outputFilePath);
	}

	transform1 {
		arg inputVector, action;
		var msg, oscf, myID;

		myID = transformID;
		transformID = transformID + 1;

		msg = inputVector.addFirst(myID).addFirst("/pca_transform");

		oscf = OSCFunc({
			arg msg;
			if(msg[1] == myID,{
				action.value(msg[2..]);
				oscf.free;
			});
		},"/pca_transformed");

		oscAddr.sendMsg(*msg);
		/*^(projectionMatrix * Matrix.with(inputVector.clump(1))).flatten;*/
	}
}