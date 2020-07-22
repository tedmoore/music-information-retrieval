/*
Ted Moore
ted@tedmooremusic.com
www.tedmooremusic.com
May 29, 2019

SuperCollider class for running non-real time Music Information Retreival analysis on files or folders and organizing them into a "corpus"

Inspired by Nic Collins' SCMIR: https://composerprogrammer.com/code.html

requires MIRAnalysis class as well as a collection of the python interfacing classes

*/

MIRCorpusItem {
	var <>buf, <startTime,<vector,<>pcaVector, <>normalizedVector,<>normalizedDisplaysVector,<dur,<>rect,<sourceFileID,<frameNum,<fftFrame, <>tSNELoc;

	*new {
		arg vector,startTime,dur,buf,sourceFileID,frameNum,fftFrame;
		^super.new.init(vector,startTime,dur,buf,sourceFileID,frameNum,fftFrame);
	}

	init {
		arg vector_, startTime_, dur_, buf_, sourceFileID_,frameNum_, fftFrame_;
		vector = vector_;
		startTime = startTime_;
		dur = dur_;
		buf = buf_;
		sourceFileID = sourceFileID_;
		frameNum = frameNum_;
		fftFrame = fftFrame_;
	}

	getParam {
		arg param, normalized = true;
		var fo = MIRAnalysis.featureOrder;
		if(fo.includes(param),{
			if(normalized,{
				^normalizedVector[fo.indexOf(param)];
			},{
				^vector[fo.indexOf(param)];
			});
		},{
			"MIRCorpusItem::getParam | param % not found".format(param).warn;
		});
	}

	getSubVector {
		arg paramArray, normalized = true;
		var returnVec = List.new;
		paramArray.do({
			arg param;
			returnVec.add(this.getParam(param,normalized));
		});
		^returnVec;
	}

	save {
		var saveArr = [
			normalizedVector,
			normalizedDisplaysVector,
			buf.path,
			startTime,
			pcaVector,
			dur,
			rect,
			sourceFileID,
			frameNum,
			vector
			//fftFrame
		];
		"save array: %".format(saveArr).postln;
		^saveArr;
	}

	/*	load {
	arg saveArr;
	normalizedVector = saveArr[0];
	normalizedDisplaysVector = saveArr[1];
	buf = saveArr[2];
	startTime = saveArr[3];
	pcaVector = saveArr[4];
	dur = saveArr[5];
	rect = saveArr[6];
	sourceFileID = saveArr[7];
	frameNum = saveArr[8];
	}*/

	post {
		"buf:              %".format(buf.path).postln;
		"buf dur:          %".format(buf.numFrames / buf.sampleRate).postln;
		"start time:       %".format(startTime).postln;
		"vector:           %".format(vector).postln;
		"pca vector:       %".format(pcaVector).postln;
		"norm. vec.:       %".format(normalizedVector).postln;
		"norm, disp. vec.: %".format(normalizedDisplaysVector).postln;
		"dur:              %".format(dur).postln;
		"frame num:        %".format(frameNum).postln;
		"fft frame:        %".format(fftFrame).postln;
		"".postln;
	}
}

MIRCorpus {
	classvar bufsDict, currentID;

	var axisFeatureIndex,
	axisOptions,
	axisPums,
	bufs,
	buildKDTree = false,
	circleRadius = 6,
	concatRemoveFrameAfterUse,
	container,
	//controlBus,
	<corpus,
	corpusFolder,
	//displayTSNE = false,
	explainedVarianceRatios,
	featureCoefficients,
	//featureCoefficientsMath,
	featureInfo,
	filePaths,
	history,
	//kdtree,
	<kdtreeOSC,
	lastPlayed,
	lastInput,
	<lengthMul,
	lfoTask,
	liveInBus,
	liveTarget,
	liveAction,
	//liveConcatTask,
	liveMIRAnalysis,
	//liveInputTask,
	liveTrigRate,
	<lenMul,
	nChans,
	maxHistory,
	norms,
	numFeatures,
	manual3DPos,
	pca,
	//<pcaKDTree,
	<pcaOSC,
	paramsToUseInAnalysis,
	plotView,
	plotWin,
	privBus,
	rangers,
	rangersTask,
	saverContainer,
	showPlotWin,
	showControlWin,
	uniqueID,
	lastPlayedView,
	mirLive,
	mirLiveSynth,
	server,
	soundInSynth,
	//timer,
	win,
	<normRanges,
	<pcaRanges,
	currentSourceFileID = 0;

	*initClass {
		StartUp.defer{
			//^Synth(\myMir_playGrain_++frame.buf.numChannels.asSymbol,[\buf,frame.buf,\startTime,frame.startTime,\dur,frame.dur * lengthMul * durMul,\vol,ampMul.ampdb,\pan,rrand(-1.0,1.0)]);
			[1,2].do({
				arg numChannels;
				SynthDef(\myMir_playGrain_++numChannels.asSymbol,{
					arg outBus = 0, buf, startTime, dur, vol, pan;
					var sig, env;
					env = EnvGen.kr(Env([0,1,1,0],[0.01,dur-0.02,0.01]),doneAction:2);
					numChannels.switch(
						1,{
							sig = Pan2.ar(PlayBuf.ar(1,buf,BufRateScale.ir(buf),0,startTime*SampleRate.ir,0,0),pan);
						},
						2,{
							sig = PlayBuf.ar(2,buf,BufRateScale.ir(buf),0,startTime*SampleRate.ir,0);
						}
					);
					sig = sig * env * vol.dbamp;
					Out.ar(outBus,sig);
				}).writeDefFile;
			});
		}
	}

	*new {
		arg server,
		corpusFolder,
		recursive = false,
		action,
		liveInBus_,
		liveTarget_,
		nChans_ = 1, showPlotWin = true, showControlWin = true,historyToAvoid = 10,lenMul = 1, liveAction, liveTrigRate, fullAnalysis = true;
		^super.new.init(server,corpusFolder,recursive,action,liveInBus_,liveTarget_,nChans_,showPlotWin,showControlWin,historyToAvoid,lenMul,liveAction,liveTrigRate, fullAnalysis);
	}

	*nextID {
		if(currentID.isNil,{
			currentID = 1;
			^currentID;
		},{
			currentID = currentID + 1;
			^currentID;
		});
	}

	init {
		arg server_, corpusFolder_, recursive = false,action,liveInBus_,liveTarget_,nChans_ = 1,showPlotWin_, showControlWin_, historyToAvoid = 10, lenMul_ = 1, liveTrigRate_ = 30, liveAction_, fullAnalysis_ = true;
		server = server_;
		liveInBus = liveInBus_;
		liveTarget = liveTarget_;
		liveAction = liveAction_;
		nChans = nChans_;
		showPlotWin = showPlotWin_;
		showControlWin = showControlWin_;
		lenMul = lenMul_;
		liveTrigRate = liveTrigRate_;

		lengthMul = lenMul;

		manual3DPos = 0.5.dup(3);
		//concatRemoveFrameAfterUse = false;

		saverContainer = HasAssignableGUIs.new.init;

		uniqueID = MIRCorpus.nextID;

		privBus = Bus.audio(server);

		history = [];
		maxHistory = historyToAvoid;

		if(corpusFolder_.notNil,{
			// we're not loading from a corpus, we're analyzing it now...
			corpusFolder = corpusFolder_;
			corpus = [];
			this.analyze(corpusFolder, recursive, action, fullAnalysis_);
		},{
			// this init was called from "initFromLoad", so the corpus is already loaded (or is being loaded...)
			if(showControlWin,{this.createControlWindow});
			if(showPlotWin,{this.createPlotWindow});
			if(liveInBus.notNil,{this.startLiveListener});
			action.value(this);
		});
	}

	initFromLoad {
		arg server_, corpus_, explainedVarianceRatios_, axisOptions_, action_, liveInBus_, liveTarget_, nChans_ = 1, showPlotWin_ = true, showControlWin_ = true, historyToAvoid_ = 10, lenMul_ = 1, normRanges_, liveAction_;

		corpus = corpus_;
		axisOptions = axisOptions_;
		normRanges = normRanges_;
		this.refreshAxisOptions;

		this.buildKDTree(corpus);

		explainedVarianceRatios = explainedVarianceRatios_;
		this.init(server_,nil,nil,action_,liveInBus_,liveTarget_,nChans_,showPlotWin_,showControlWin_,historyToAvoid_,lenMul_,liveAction_);
	}

	buildKDTree {
		arg corp;
		var input_path;
		var file;
		var osc_port = 8009;
		var data;

		if(buildKDTree,{
			data = corpus.collect({
				arg corpusItem;
				corpusItem.normalizedVector;
			});


			input_path = "/Users/ted/Documents/_CREATING/_PROJECT FILES/Machine Learning/Analysis and Dimensionality Reduction/kNN/input_files/knn_input_file_%.csv".format(Date.myFormat);
			file = File(input_path,"w");
			data.do({
				arg row, i;
				if(i != 0,{file.write("\n");});
				row.do({
					arg val, j;
					if(j != 0,{file.write(",")});
					file.write(val.asCompileString);
				});
			});
			file.close;
			kdtreeOSC = NetAddr("127.0.0.1",osc_port);
			RunInPython("/Users/ted/Documents/_CREATING/_PROJECT FILES/Machine Learning/Analysis and Dimensionality Reduction/kNN/knn_for_mir_corpus.py",["-i",input_path,"-o",osc_port]);
		});
	}

	// ====================== GUIS ==============================================

	refreshAxisOptions {
		axisPums.do(_.items_(axisOptions));
	}

	createPlotWindow {
		plotWin = Window("plot features",Rect(0,0,1200,900))
		.acceptsMouseOver_(true);
		plotWin.view.onResize_({
			plotView.bounds_(Rect(0,20,plotWin.view.bounds.width,plotWin.view.bounds.height-20));
			plotView.refresh;
		});

		container = CompositeView(plotWin,Rect(0,0,plotWin.view.bounds.width,20))
		.background_(Color.white);
		container.decorator_(FlowLayout(container.bounds,0@0,0@0));

		axisFeatureIndex = Dictionary.new;

		axisPums = ["X Axis","Y Axis","Color"].collect({
			arg name, i;
			var pum, startFeatureIndex;

			startFeatureIndex = ([0,1,2] + MIRAnalysis.nFeatures)[i];

			axisFeatureIndex.put(name,startFeatureIndex);

			StaticText(container,Rect(0,0,50,20)).string_(" " + name);
			pum = PopUpMenu(container,Rect(0,0,160,20))
			.items_(axisOptions)
			.action_({
				arg pum;
				//displayTSNE = false;
				axisFeatureIndex.put(name,pum.value);
				//plotView.refresh;
				this.slewDisplay(0.5);
			})
			.value_(startFeatureIndex);

			pum;
		});

		EZNumber(container,Rect(0,0,160,20),"Length Mul",ControlSpec(0.5,2,\exp),{
			arg sl;
			lengthMul = sl.value;
		},lenMul,true,90);

		Button(container,Rect(0,0,50,20))
		.states_([["PCA"]])
		.action_({
			arg b;
			axisFeatureIndex.put("X Axis",23);
			axisPums[0].value_(23);
			axisFeatureIndex.put("Y Axis",24);
			axisPums[1].value_(24);
			axisFeatureIndex.put("Color",25);
			axisPums[2].value_(25);
			this.slewDisplay(3);
			//plotView.refresh;
		});

		Button(container,Rect(0,0,50,20))
		.states_([["tSNE"]])
		.action_({
			arg b;
			axisPums[0].value_(26);
			axisFeatureIndex.put("X Axis",26);
			axisPums[1].value_(27);
			axisFeatureIndex.put("Y Axis",27);
			//plotView.refresh;
			this.slewDisplay(3);
		});

		Button(container,Rect(0,0,50,20))
		.states_([["AE"]])
		.action_({
			arg b;
			axisPums[0].value_(28);
			axisFeatureIndex.put("X Axis",28);
			axisPums[1].value_(29);
			axisFeatureIndex.put("Y Axis",29);
			this.slewDisplay(3);
			//plotView.refresh;
		});

		Button(container,Rect(0,0,50,20))
		.states_([["Auction"]])
		.action_({
			arg b;
			if(axisOptions.collect(_.asSymbol).includes('Auction 1').not,{
				this.runAuction;
			},{
				this.setDisplayToAuction;
			});
		});

		lastPlayed = nil;
		lastInput = nil;

		plotView = UserView(plotWin,Rect(0,20,plotWin.view.bounds.width,plotWin.view.bounds.height-20))
		//.background_(Color.green)
		.drawFunc_({
			var colorindex = axisFeatureIndex.at("Color");
			corpus.do({
				arg mci;
				var dispx = mci.rect.left.linlin(0,1,0,plotView.bounds.width-circleRadius);
				var dispy = mci.rect.top.linlin(0,1,plotView.bounds.height-circleRadius,0);
				var dispRect = Rect(dispx,dispy,circleRadius,circleRadius);
				//frame.rect = Rect(px,py,circleRadius,circleRadius);
				Pen.addOval(dispRect);
				Pen.color_(Color.hsv(mci.normalizedDisplaysVector[colorindex],1,1));
				Pen.draw;
			});
		})/*;

		lastPlayedView = UserView(plotWin,Rect(0,20,plotWin.view.bounds.width,plotWin.view.bounds.height-20))
		.drawFunc_({
		if(lastInput.notNil,{
		var px, py;
		var yindex,xindex;
		xindex = axisFeatureIndex.at("X Axis");
		yindex = axisFeatureIndex.at("Y Axis");
		//"last input: %".format(lastInput).postln;
		px = lastInput.normalizedDisplaysVector[xindex].linlin(0,1,0,plotView.bounds.width);
		py = lastInput.normalizedDisplaysVector[yindex].linlin(0,1,plotView.bounds.height,0);
		px = px.clip(0,plotView.bounds.width);
		py = py.clip(0,plotView.bounds.height);

		Pen.addOval(Rect(px,py,circleRadius*2,circleRadius*2));
		Pen.color_(Color.black);
		Pen.draw;
		});
		})
		*/.mouseOverAction_({
			arg view, px, py, modifiers;
			[px,py].postln;
			corpus.do({
				arg frame;
				/*				"frame: %".format(frame).postln;
				"rect:  %".format(frame.rect).postln;
				"".postln;*/
				if(frame.rect.notNil,{
					var unnormRect = Rect(
						frame.rect.left.linlin(0.0,1.0,0.0,plotView.bounds.width),
						frame.rect.top.linlin(0.0,1.0,plotView.bounds.height,0.0),
						frame.rect.width,
						frame.rect.height
					);

					if(unnormRect.contains(Point(px,py)).and(lastPlayed != frame),{
						"found: %".format(PathName(frame.buf.path).fileNameWithoutExtension).postln;
						lastPlayed = frame;
						this.playGrain(frame,1);
					});
				});
			});
		})
		.mouseMoveAction_({
			arg view, x, y, modifiers;
			var postVector = false;
			//[view, x, y, modifiers].postln;
			if(modifiers == 524288,{
				postVector = true;
			});
			this.playScreenXYPos(x,y,postVector);
		})
		.mouseDownAction_({
			arg view, x, y, modifiers, buttonNumber, clickCount;
			//[view, x, y, modifiers, buttonNumber, clickCount].postln;
			if((buttonNumber == 0) && (modifiers == 131072),{
				this.findAndDeleteByXY(x,y);
			});
		});

		corpus.do({
			arg mci;
			var xindex = axisFeatureIndex.at("X Axis");
			var yindex = axisFeatureIndex.at("Y Axis");
			var startx = mci.normalizedDisplaysVector[xindex];
			var starty = mci.normalizedDisplaysVector[yindex];
			mci.rect_(Rect(startx,starty,circleRadius,circleRadius));
		});

		this.slewDisplay;
		//plotView.refresh;
		plotWin.front;
	}

	slewDisplay {
		arg time = 0.1;
		time = max(time,0.1);
		Task({
			var startLocs, endLocs;
			//var time = 3;
			var updateTime = 30.reciprocal;
			var n = time / updateTime;
			var endXindex, endYindex;

			endXindex = axisFeatureIndex.at("X Axis");
			endYindex = axisFeatureIndex.at("Y Axis");

			//"endYindex: %".format(endYindex).postln;

			endLocs = corpus.collect({
				arg mci;
				var endx, endy, pt;
				endx = mci.normalizedDisplaysVector[endXindex];//.linlin(0,1,0,plotView.bounds.width-circleRadius);
				//"endYindex: %".format(endYindex).postln;
				//"mci normalized displays vector: %".format(mci.normalizedDisplaysVector).postln;
				endy = mci.normalizedDisplaysVector[endYindex];//.linlin(0,1,plotView.bounds.height-circleRadius,0);
				pt = Point(endx,endy);
				//pt.postln;
				//"".postln;
				pt;
			});
			//==================================
			startLocs = corpus.collect({
				arg mci;
				Point(mci.rect.left,mci.rect.top);
			});

			"n: %".format(n).postln;

			n.do({
				arg i;
				var lerp = i.linlin(0,n-1,-pi,0).cos.linlin(-1,1,0,1);
				corpus.do({
					arg mci,i;
					var ix = lerp.linlin(0,1,startLocs[i].x,endLocs[i].x);
					var iy = lerp.linlin(0,1,startLocs[i].y,endLocs[i].y);
					mci.rect_(Rect(ix,iy,circleRadius,circleRadius));
				});

				plotView.refresh;
				updateTime.wait;
			});

		},AppClock).play;
	}

	runAuction {
		var available_spots, cost_matrix, assignments, rows, cols, n, rowsPad, colsPad;
		var currentColorParam = axisFeatureIndex.at("Color");
		n = corpus.size;

		cols = n.sqrt.asInteger;
		rows = n / cols;

		if(((rows - rows.asInteger) == 0).not,{
			//"adding".postln;
			rows = rows + 1;
		});
		rows = rows.asInteger;

		/*		rowsPad =
		colsPad = */

		available_spots = Array.fill(rows,{
			arg y;
			y = y.linlin(0,rows-1,0.0,1.0);
			Array.fill(cols,{
				arg x;
				x = x.linlin(0,cols-1,0.0,1.0);
				[x,y];
			});
		}).flatten;

		//"available_spots: %".format(available_spots).postln;
		//"solution: %".format(t.getSolution).postln;

		cost_matrix = corpus.collect({
			arg mci;
			var tsneLoc = mci.tSNELoc; /// based on TSNE--------------

			available_spots.collect({
				arg as;
				(as - tsneLoc).pow(2).sum.sqrt;
			});
		});

		//"cost matrix: %".format(cost_matrix).postln;
		//cost_matrix.dopostln;

		assignments = Auction().auction(1.0 / cost_matrix);
		"assignments: %".format(assignments).postln;
		"assignments size: %".format(assignments.size).postln;
		"assignments shape: %".format(assignments.shape).postln;
		"n items in corpus: %".format(corpus.size).postln;


		2.do({
			arg i;
			axisOptions = axisOptions.add("Auction %".format(i+1));
		});

		corpus.do({
			arg mci, i;
			var newVec = mci.normalizedDisplaysVector.addAll(available_spots[assignments[i]]);
			mci.normalizedDisplaysVector_(newVec);
		});

		this.refreshAxisOptions;
		axisPums[2].value_(currentColorParam);
		this.setDisplayToAuction;
	}

	setDisplayToAuction {
		axisPums[0].value_(30);
		axisFeatureIndex.put("X Axis",30);
		axisPums[1].value_(31);
		axisFeatureIndex.put("Y Axis",31);
		//plotView.refresh;
		this.slewDisplay(3);
	}

	processTSNE {
		arg userAction;
		var inputFilePath, outputFilePath, inputFile, pythonPath, args;
		var timeStamp = Date.myFormat;
		inputFilePath = Platform.userAppSupportDir+/+"tmp/tsne_input_%.csv".format(timeStamp);
		outputFilePath = Platform.userAppSupportDir+/+"tmp/tsne_input_%_output.csv".format(timeStamp);

		//inputFilePath = inputFilePath.absolutePath;
		inputFile = File(inputFilePath,"w");

		corpus.do({
			arg mci;
			mci.normalizedVector.do({
				arg val, i;
				if(i != 0,{
					inputFile.write(",");
				});
				inputFile.write(val.asString);
			});
			inputFile.write("\n");
		});

		inputFile.close;

		pythonPath = "/Users/ted/Library/Application Support/SuperCollider/Extensions/tedsExtensions/machineLearning/TSNE/tsne_for_mirc.py";
		//pythonPath = "/Users/ted/Library/Application Support/SuperCollider/Extensions/tedsExtensions/machineLearning/tsne/tsne_for_mirc_running.py";

		args = ["-i",inputFilePath];
		RunInPython(pythonPath,args,{
			arg outFilePath;
			var outFile = CSVFileReader.readInterpret(outFilePath,true,true);
			//var tsneKDTreeData;
			//outFile.postln;
			//outFile.shape.postln;

			2.do({
				arg i;
				axisOptions = axisOptions.add("TSNE %".format(i+1));
			});

			corpus.do({
				arg mci, i;
				var newVec = mci.normalizedDisplaysVector.addAll(outFile[i]);
				mci.normalizedDisplaysVector_(newVec);
				mci.tSNELoc_(outFile[i]);
			});

			"norm disp vec size after tsne: %".format(corpus[0].normalizedDisplaysVector.size).postln;

			this.processAE(userAction);
		},outputFilePath);
	}

	processAE {
		arg userAction;
		var data = corpus.collect({
			arg mci;
			mci.normalizedVector;
		});

		AEPython(data,{
			arg outData;

			2.do({
				arg i;
				axisOptions = axisOptions.add("AE Dim %".format(i+1));
			});

			corpus.do({
				arg mci, i;
				var newVec = mci.normalizedDisplaysVector.addAll(outData[i]);
				mci.normalizedDisplaysVector_(newVec);
			});

			"norm disp vec size after ae: %".format(corpus[0].normalizedDisplaysVector.size).postln;

			this.analysisFinished(userAction);
		});
	}

	analysisFinished {
		arg userAction;
		userAction.value(this);

		defer{
			if(showControlWin,{this.createControlWindow});
			if(showPlotWin,{this.createPlotWindow});
			if(liveInBus.notNil,{this.startLiveListener});
		};
	}
	createControlWindow {
		win = Window("MIR Control",Rect(0,0,800,600));
		win.view.decorator_(FlowLayout(win.bounds));
		//timer = BeatSched.new;

		Button(win,Rect(0,0,120,20))
		.states_([["Live Input is Off",Color.black,Color.yellow],["Live Input is On",Color.black,Color.green]])
		.action_({
			arg but;
			if(but.value == 0,{
				//liveConcatTask.pause;
				//liveMIRAnalysis.running_(false);
			},{
				//liveMIRAnalysis.running_(true);
				//liveConcatTask.play;
			});
		});

		Button(win,Rect(0,0,120,20))
		.states_([["LFO Playing is Off"],["LFO Playing is On"]])
		.action_({
			arg but;
			if(but.value == 0,{
				lfoTask.pause;
			},{
				lfoTask.play;
			});
		});

		Button(win,Rect(0,0,120,20))
		.states_([["Save"]])
		.action_({
			arg but;
			Dialog.savePanel({
				arg path;
				this.save(path);
			});
		});

		Button(win,Rect(0,0,120,20))
		.states_([["Concat a Src File"]])
		.action_({
			arg but;
			Dialog.openPanel({
				arg path;
				ConcatSynthNRT.renderFromCorpus(server,this,path,concatRemoveFrameAfterUse);
			});
		});

		win.view.decorator.nextLine;

		["X","Y","Z"].do({
			arg name, i;
			EZSlider(win,Rect(0,0,300,20),"% Dim".format(name),nil.asSpec,{
				arg sl;
				manual3DPos[i] = sl.value;
			},0.5,false)
			.addHandleRequestNew(
				"/mir/%/%Dim".format(uniqueID,name),
				nil.asSpec,
				nil,
				saverContainer,
				win
			);

			win.view.decorator.nextLine;
		});

		lfoTask = Task({
			inf.do({
				var featureIndicies, dur;
				featureIndicies = [
					axisFeatureIndex.at("X Axis"),
					axisFeatureIndex.at("Y Axis"),
					axisFeatureIndex.at("Color")
				];
				dur = this.playNearestNDPos(manual3DPos,featureIndicies);
				//"dur: %".format(dur).postln;
				(dur * rrand(1.5,2)).wait;
			});
		},SystemClock);
		/*
		liveConcatTask = Task({
		inf.do({
		var waitTime = 0.1;
		var data = liveMIRAnalysis.getCurrentData;
		var kdtreeReturn = kdtree.nearest(data.normalized.vector);
		var nearest = kdtreeReturn[0].label;
		/*					"nearest:         %".format(nearest).postln;
		"nearest loc:     %".format(kdtreeReturn[0].location).postln;
		"history:         %".format(history).postln;
		"".postln;*/
		//durMul = max(me.trigRate.reciprocal / nearest.dur,1);
		this.playGrainSetDur(nearest,waitTime*3);
		lastInput = nearest;
		//defer{lastPlayedView.refresh};
		rrand(waitTime * 0.5,waitTime * 2).wait;
		});
		});*/

		win.front;
	}

	*checkForExistingBuf {
		arg server, path;
		var buf;
		if(bufsDict.isNil,{
			bufsDict = Dictionary.new;
		});

		if(bufsDict.at(path).notNil,{
			//"buf found: %".format(path).postln;
			buf = bufsDict.at(path);
		},{
			//"buf not found: %".format(path).postln;
			buf = Buffer.read(server,path);
			bufsDict.put(path,buf);
		});

		^buf;
	}

	playScreenXYPos {
		arg x, y, postVector = false;
/*		var recordClosest = inf;
		var winner = nil;
		var mouse = Point(x,y);*/

		x = x.linlin(0,plotView.bounds.width,0,1.0);
		y = y.linlin(0,plotView.bounds.height,1,0.0);

		this.playNearestCurrentXY(x,y,postVector);
	}

	playNearestCurrentXY {
		arg xx, yy, postVector = false;
		var xIndex = axisFeatureIndex.at("X Axis");
		var yIndex = axisFeatureIndex.at("Y Axis");
		this.playNearestNDPos([xx,yy],[xIndex,yIndex],postVector);
	}

	playNearestTSNE {
		arg tsne1, tsne2;
		// use the tsne kdtree

		//var postVector = false;
		//this.playNearestNDPos([tsne1,tsne2],[26,27],postVector);
	}

	playNearestCurrentXYZ {
		arg xx, yy, zz, postVector = false;
		var xIndex = axisFeatureIndex.at("X Axis");
		var yIndex = axisFeatureIndex.at("Y Axis");
		var zIndex = axisFeatureIndex.at("Color");
		this.playNearestNDPos([xx,yy,zz],[xIndex,yIndex,zIndex],postVector);
	}

	playNearestNDPos {
		arg arrayND,featureIndiciesToUse,postVector = false;
		var dur, closest, recordClosest = inf;

		// just double check
		if(arrayND.size != featureIndiciesToUse.size,{
			Error("array of values and array of feature indicies to use are not the same length:\n%\n%\n".format(arrayND.size,featureIndiciesToUse.size)).throw;
		});

		// go over them all...
		corpus.do({
			arg mirCorpusItem;
			var normVec = mirCorpusItem.normalizedDisplaysVector;
			var dist = this.euclidianDist(arrayND,normVec.at(featureIndiciesToUse));
			if((dist < recordClosest).and(history.includes(mirCorpusItem).not),{
				recordClosest = dist;
				closest = mirCorpusItem;
			});
		});

		if(postVector,{
			"%,".format(closest.normalizedVector.asCompileString).postln;
		});

		this.playGrain(closest,2);
		^(closest.dur * 2);
	}

	startLiveListener {
		if(liveInBus.notNil,{
			liveMIRAnalysis = MIRAnalysis.live(liveInBus,liveTarget,\addAfter,normRanges,nChans,autoRange:false,liveTrigRate:liveTrigRate,action:{
				arg mirAnalysisData, me;

				liveAction.value(mirAnalysisData,this);
			});
		});
	}

	// =================== ANALYSIS =========================================
	analyze {
		arg folder, recursive = false, action, fullAnalysis = true;
		//var bufsDict = Dictionary.new;

		if(PathName(folder).isFolder,{
			// a folder was passed
			MIRAnalysis.analyzeFolderNRT(folder,{
				arg mirAnalysisFile;
				this.addMIRAnalysisFileToCorpus(mirAnalysisFile);
			},recursive,0,{
				this.processCorpus(action,fullAnalysis);
			});
		},{
			// a file was passed
			if(PathName(folder).isFile,{
				MIRAnalysis.analyzeFileNRT(folder,{
					arg mirAnalysisFile;

					this.addMIRAnalysisFileToCorpus(mirAnalysisFile);

					this.processCorpus(action,fullAnalysis);
				});
			},{
				Error("Path sent to MIRCorpus was neither a folder or a file: %".format(folder)).throw;
			});
		});

		/*		featureCoefficients = [1,1,1,1,1];
		this.calcFCM();*/
	}

	nextSourceFileID {
		var next = currentSourceFileID;
		currentSourceFileID = currentSourceFileID + 1;
		^next;
	}

	addMIRAnalysisFileToCorpus {
		arg miraf;
		var buf;
		var thisID = this.nextSourceFileID;
		buf = MIRCorpus.checkForExistingBuf(server,miraf.path);

		FFTNRT.fft(miraf.path,fftSize:2048,overlap:1,action:{
			arg fftFrames;
			//"just finished fft frames for %".format(PathName(miraf.path).fileNameWithoutExtension).postln;
			//fftFrames.postln;

			fftFrames.pop;

			miraf.frames.do({
				arg frame, index;
				//"frame: %".format(frame).postln;
				corpus = corpus.add(
					MIRCorpusItem(
						frame[1],// vector: vector of data is in the first index
						frame[0],// startTime: start time is the zeroth index
						miraf.fftDur, // dur
						buf, // buf
						thisID, // sourceFileID
						index, // sourceFileIndex
						fftFrames[index]
					)
				);
			});
		});
	}

	processCorpus {
		arg action, fullAnalysis = true;
		//Task({
		var pcaMatrix = [];
		//"all done with the corpus analysis!!!!".postln;

		// add pca (does normalization for you)
		corpus.do({
			arg corpusItem;
			pcaMatrix = pcaMatrix.add(corpusItem.vector);
		});

		"mci vector size 01: %".format(corpus[0].vector.size).postln;

		pcaOSC = NetAddr("127.0.0.1",8005);

		pca = PCA(3);//
		pca.fit(pcaMatrix,action:{
			arg pcaResults;
			//var pcaKDTreeData = List.new;

			// normalize...
			normRanges = MIRAnalysis.nFeatures.collect({ControlSpec(inf,-inf)});
			pcaRanges = pca.nComponents.collect({ControlSpec(inf,-inf)});
			corpus.do({
				arg corpusItem, i;

				corpusItem.pcaVector_(pcaResults.pcaData[i]);
				//pcaKDTreeData.add(pcaResults.pcaData[i].copy.asList.add(corpusItem));

				// vector ranges
				corpusItem.vector.do({
					arg val, i;
					if(val < normRanges[i].minval,{
						normRanges[i].minval = val;
					});

					if(val > normRanges[i].maxval,{
						normRanges[i].maxval = val;
					});
				});

				// pca ranges
				corpusItem.pcaVector.do({
					arg val, i;
					if(val < pcaRanges[i].minval,{
						pcaRanges[i].minval = val;
					});

					if(val > pcaRanges[i].maxval,{
						pcaRanges[i].maxval = val;
					});
				});
			});

			corpus.do({
				arg corpusItem, idx;
				var normVector = corpusItem.vector.copy;

				// vector normalization
				normVector = normVector.collect({
					arg val, i;
					normRanges[i].unmap(val);
				});

				corpusItem.normalizedVector_(normVector.copy);

				// pca vector normalization
				corpusItem.pcaVector.do({
					arg val, i;

					// the normed pca values don't actually end up in the normalized vector -
					// this is only concatenating it so that then it can become the
					// normalizedDisplaysVector...
					normVector = normVector.add(pcaRanges[i].unmap(val));
				});

				corpusItem.normalizedDisplaysVector_(normVector);
			});

			"norm vec size after pca: %".format(corpus[0].normalizedVector.size).postln;

			"norm disp vec size after pca: %".format(corpus[0].normalizedDisplaysVector.size).postln;


			this.buildKDTree(corpus);

			// do other stuff
			explainedVarianceRatios = pcaResults.explainedVarianceRatios;
			//"got to 1".postln;

			axisOptions = MIRAnalysis.featureOrder;
			if(explainedVarianceRatios.notNil,{
				explainedVarianceRatios.do({
					arg ratio, i;
					axisOptions = axisOptions.add("PCA %: %\\%".format(
						i+1,
						(ratio*100).round(0.1)
					));
				});
			});
			if(fullAnalysis,{
				this.processTSNE(action);
			},{
				this.analysisFinished(action)
			});
		});
	}

	// ======================= FUNCTIONAL METHODS =================================

	euclidianDist {
		arg vector1,vector2,weights;
		var sqrs;
		/*		vector1.postln;
		vector2.postnl;
		weights.postln;
		"".postln;*/
		sqrs = (vector1 - vector2).pow(2);
		if(weights.notNil,{
			sqrs = sqrs * weights;
		});
		^sqrs.sum.sqrt;
	}

	cosDist {
		arg vector1, vector2;
		var mag1, mag2, dotProd, return;
		dotProd = (vector1 * vector2).sum;
		mag1 = vector1.pow(2).sum.sqrt;
		mag2 = vector2.pow(2).sum.sqrt;
		return = dotProd / (mag1*mag2);
		^return;
	}

	findAndDeleteByXY {
		arg x, y;
		var closestDist = inf, closestIndex = -1, mouse = Point(x,y);
		corpus.do({
			arg frame, i;
			var dist = frame.rect.center.dist(mouse);
			if(dist < closestDist,{
				closestDist = dist;
				closestIndex = i;
			});
		});

		//"closestDist: %\nclosestIndex: %\n".format(closestDist,closestIndex).postln;
		corpus.removeAt(closestIndex);
		plotView.refresh;
	}

	addToHistory {
		arg frame;
		history = history.add(frame);
		if(history.size > maxHistory,{
			history.removeAt(0);
		});
	}

	playGrain {
		arg frame, durMul = 1, ampMul = 1, ampTarget = nil;
		this.addToHistory(frame);
		"playing grain: %".format(frame).postln;

		if(ampTarget.notNil,{
			var ampScale = ampTarget / frame.getParam(\amplitude);
			ampMul = ampMul * ampScale;
		});

		^Synth(\myMir_playGrain_++frame.buf.numChannels.asSymbol,[\buf,frame.buf,\startTime,frame.startTime,\dur,frame.dur * lengthMul * durMul,\vol,ampMul.ampdb,\pan,rrand(-1.0,1.0)]);
	}

	playGrainByIndex {
		arg index, mul = 1;
		this.playGrain(corpus[index]);
	}

	playGrainByIndexAvoidHistory {
		arg indices, durMul = 1, ampMul = 1, ampTarget = nil;
		var played = false;
		var indexI = 0;

		indices.postln;
		while({
			played.not && ((indexI < history.size) || (indexI == 0));
		},{
			var frame = corpus[indices[indexI]];
			indexI.postln;
			frame.postln;
			if(history.includes(frame).not,{
				played = true;
				this.playGrain(frame,durMul,ampMul,ampTarget);
			});
			indexI = indexI + 1;
		});
	}

	playGrainSetDur {
		arg frame, dur;
		this.addToHistory(frame);
		//"playing grain: %".format(frame).postln;
		^Synth(\myMir_playGrain_++frame.buf.numChannels.asSymbol,[\buf,frame.buf,\startTime,frame.startTime,\dur,dur,\vol,0,\pan,rrand(-1.0,1.0)]);
	}

	save {
		arg path;
		var saveCorpus = corpus.collect({
			arg mirCorpusItem;
			mirCorpusItem.save;
		});
		var saveDict = Dictionary.newFrom([
			\corpus,saveCorpus,
			\explainedVarianceRatios,explainedVarianceRatios,
			\axisOptions,axisOptions,
			\normRanges,normRanges
		]);

		saveDict.writeArchive(path);
	}

	*load {
		arg server, path, action, liveInBus, liveTarget, nChans = 1, showPlotWin = true, showControlWin = true, historyToAvoid = 10, lenMul = 1;
		var loadDict = Object.readArchive(path);
		var loadCorpus = loadDict.at(\corpus);
		var normRanges = loadDict.at(\normRanges);
		loadCorpus = loadCorpus.collect({
			arg saveArr;
			var normalizedVector = saveArr[0];
			var normalizedDisplaysVector = saveArr[1];
			var buf_path = saveArr[2];
			var startTime = saveArr[3];
			var pcaVector = saveArr[4];
			var dur = saveArr[5];
			var rect = saveArr[6];
			var sourceFileID = saveArr[7];
			var frameNum = saveArr[8];
			var vector = saveArr[9];
			var buf = MIRCorpus.checkForExistingBuf(server,buf_path);
			var mci = MIRCorpusItem(vector,startTime,dur,buf,sourceFileID,frameNum,nil);
			mci.normalizedVector_(normalizedVector);
			mci.normalizedDisplaysVector_(normalizedDisplaysVector);
			mci.rect_(rect);
			//"loaded corpus item from: %".format(PathName(buf.path).fileName).postln;
			mci;
		});
		//arg server_, corpus_, explainedVarianceRatios_, axisOptions_, action_, liveInBus_, liveTarget_, nChans_ = 1, showPlotWin_ = true, showControlWin_ = true, historyToAvoid_ = 10, lenMul_ = 1;
		^super.new.initFromLoad(server,loadCorpus,loadDict.at(\explainedVarianceRatios),loadDict.at(\axisOptions),action,liveInBus,liveTarget,nChans,showPlotWin,showControlWin,historyToAvoid,lenMul,normRanges);
	}
}