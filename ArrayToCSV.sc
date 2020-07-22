/*
Ted Moore
ted@tedmooremusic.com
www.tedmooremusic.com
October 18, 2019

convert any 2D data into a csv file

also can be "opened" and then write lines in one at a time using "writeLine".

*/

ArrayToCSV {
	var file, n_lines;

	*open {
		arg path;
		^super.new.open(path);
	}

	open {
		arg path;
		file = File(path,"w");
		n_lines = 0;
	}

	writeLine {
		arg arr;
		if(n_lines != 0,{file.write("\n");});
		arr.do({
			arg val, j;
			if(j != 0,{file.write(",");});

			//if(val.isKindOf(Symbol).or(val.isKindOf(String)),{val = val.asString},{val = val.asCompileString});

			file.write(val.asString);
		});

		n_lines = n_lines + 1;
	}

	close {
		file.close;
	}

	*new {
		arg data, path;
		var file = File(path,"w");

		data.do({
			arg entry, i;
			if(i != 0,{file.write("\n");});
			entry.do({
				arg val, j;
				if(j != 0,{file.write(",");});

				//if(val.isKindOf(Symbol).or(val.isKindOf(String)),{val = val.asString},{val = val.asCompileString});

				file.write(val.asString);
			});
		});

		file.close;
		^nil;
	}
}