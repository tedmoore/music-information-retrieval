KNNInterpolater {
	var tree, data;

	*new {
		arg data, k = 3;
		^super.new.init(data,k);//[[[lookup_data],[interp_data]],...]
	}

	init {
		arg data_, k = 3;
		data = data_;
		tree = KNearestNeighbour(k,data);
	}

	k_ {
		arg k;
		tree.k_(k);
	}

	interp {
		arg point;
		var closestK = tree.findClosestK(point).flop;
		var dists = closestK[0];
		var is = closestK[1];
		var weights = 1.0 / (dists + 0.001);
		var interps = data.flop[1].at(is);
		var result = (interps * weights).sum / weights.sum;
		^result;
	}
}