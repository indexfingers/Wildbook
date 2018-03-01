package org.ecocean.identity.BenWhitesharkPkg;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.List;


import com.google.gson.Gson;




public class MatchContourToPoints2 {
	
	private FinDetectObjects.Regions regions;
	private double[] selectedRegx, selectedRegy;
	private double[] sec1x, sec2x, sec1y, sec2y, bigx, bigy;
	private String pathToJson, s3key, s3bucket;
	

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Constructors

	public MatchContourToPoints2() {
	}

	public MatchContourToPoints2(String pathOrUuid, String type) {
		switch (type) {
		case "path":
			this.importJson(pathOrUuid);
			break;
		case "uuid":
			this.doSomethingWithUuid(pathOrUuid);
			break;
		}
	}

	public MatchContourToPoints2(String bucket, String key, String type) {
		this.s3key = key;
		this.s3bucket = bucket;
		// TODO Get json from specified S3 bucket and key
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// relevant getters and setters
	
	public void setDetectionIsBad(int val){
		regions.detectionIsBad=val;
	}
	
	public int getDetectionIsBad(){
		return regions.detectionIsBad;
	}

	public void setTipPt(double x, double y) {
		regions.tipx = x;
		regions.tipy = y;
	}

	public void setLePt(double x, double y) {
		regions.lex = x;
		regions.ley = y;
	}

	public void setTePt(double x, double y) {
		regions.tex = x;
		regions.tey = y;
	}

	public double getTipPt(String dim) {
		if (dim.equals("x")) {
			return regions.tipx;
		} else {
			return regions.tipy;
		}
	}

	public double getTePt(String dim) {
		if (dim.equals("x")) {
			return regions.tex;
		} else {
			return regions.tey;
		}
	}

	public double getLePt(String dim) {
		if (dim.equals("x")) {
			return regions.lex;
		} else {
			return regions.ley;
		}
	}

	public int getSz(String dim) {
		if (dim.equals("x")) {
			return regions.szx;
		} else {
			return regions.szy;
		}
	}

	public double[] getFinDetectionContour(String dim) {
		if (dim.equals("x")) {
			return regions.detectionx;
		} else {
			return regions.detectiony;
		}
	}

	public double[] getSelectedRegionContour(String dim) {
		if (dim.equals("x")) {
			return selectedRegx;
		} else {
			return selectedRegy;
		}
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Import / export json

	public String importJson(String pathToJson) {
		this.pathToJson = pathToJson;
		final String EoL = System.getProperty("line.separator");
		final String content;
		String success;
		try {
			List<String> lines = Files.readAllLines(Paths.get(pathToJson), Charset.defaultCharset());
			StringBuilder sb = new StringBuilder();
			for (String line : lines) {
				sb.append(line).append(EoL);
			}
			content = sb.toString();
			Gson gson = new Gson();
			regions = gson.fromJson(content, FinDetectObjects.Regions.class);
				
			dealWithNullPts();

			this.selectedRegx = regions.regionsx.get(regions.selectedRegion);
			this.selectedRegy = regions.regionsy.get(regions.selectedRegion);
			success = "success";

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			success = "fail";
		}
		return success;

	}
	
	
	public String convertRegionsToJsonString(){
		Gson gson = new Gson();
		String jsonInString = gson.toJson(regions);
		System.out.println(jsonInString);
		return jsonInString;
		
	}

	public void exportJson() {
		//take the time of export as the dateModified time: want latest possible time...
		Date dateNow = new Date();
		this.regions.date=dateNow.getTime();

		
		String jsonInString = convertRegionsToJsonString();
		try(  PrintWriter out = new PrintWriter( pathToJson )  ){
		    out.println( jsonInString );
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// TODO

	}

	private void dealWithNullPts() {
		if ((regions.tipx + regions.tipy + regions.lex + regions.ley) == 0) {
			regions.tipx = -1;regions.tipy = -1;
			regions.tex = -1;regions.tey = -1;
			regions.lex = -1;regions.ley = -1;
		}
	}

	private void doSomethingWithUuid(String uuid) {
		// TODO Convert image uuid to S3 key and get json from there
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Matching

	public String matchPointsToContours() {
		String rtn;
		double[] dists;
		double maxDist = 0;
		double[] maxDists = new double[regions.regionsx.size()];
		int[] closestTip, closestLe, closestTe;
		closestTip = new int[regions.regionsx.size()];
		closestLe = new int[regions.regionsx.size()];
		closestTe = new int[regions.regionsx.size()];
		MinIdx minIdxTip, minIdxLe, minIdxTe, minIdxReg;
		
		
		// if matching is called, we assume that manual editing of detection has taken place....
		this.regions.ismanual=1;
		
		//Step 1: C* = min_c max_p dist(Reg_c,keypoint_p)
		//Find region that minimises the maximum distance you'd have to travel to from any keypoint to that region
		try {
			for (int j = 0; j < regions.regionsx.size(); j++) {

				dists = distsPtToContour(regions.tipx, regions.tipy, regions.regionsx.get(j), regions.regionsy.get(j));
				minIdxTip = getMinIdx(dists);
				closestTip[j] = minIdxTip.idx;
				if (minIdxTip.min > maxDist)
					maxDist = minIdxTip.min;

				dists = distsPtToContour(regions.lex, regions.ley, regions.regionsx.get(j), regions.regionsy.get(j));
				minIdxLe = getMinIdx(dists);
				//System.out.println(minIdxLe.idx + "  " + minIdxLe.min);
				closestLe[j] = minIdxLe.idx;
				if (minIdxLe.min > maxDist)
					maxDist = minIdxLe.min;

				dists = distsPtToContour(regions.tex, regions.tey, regions.regionsx.get(j), regions.regionsy.get(j));
				minIdxTe = getMinIdx(dists);
				//System.out.println(minIdxTe.idx + "  " + minIdxTe.min);
				closestTe[j] = minIdxTe.idx;
				if (minIdxTe.min > maxDist)
					maxDist = minIdxTe.min;
				maxDists[j] = maxDist;
			}

			minIdxReg = getMinIdx(maxDists);
			regions.selectedRegion=minIdxReg.idx;


			
			
			//Step2: split region into two open contour parts. Find part that minimises min distance to tip.
			
			// now need to subsection the region and get best matching part
			int idxOfRegContVertexNearestLePt = closestLe[minIdxReg.idx];
			int idxOfRegContVertexNearestTePt = closestTe[minIdxReg.idx];

			// System.out.println("LeIdx: " + idxOfRegContVertexNearestLePt +
			// "TeIdx: " + idxOfRegContVertexNearestTePt);

			int smallIdx = Math.min(idxOfRegContVertexNearestLePt, idxOfRegContVertexNearestTePt);
			int bigIdx = Math.max(idxOfRegContVertexNearestLePt, idxOfRegContVertexNearestTePt);

			// System.out.println("smmIdx: " + smallIdx + "bigIdx: " + bigIdx);

			bigx = concatDbl(regions.regionsx.get(minIdxReg.idx), regions.regionsx.get(minIdxReg.idx));
			bigy = concatDbl(regions.regionsy.get(minIdxReg.idx), regions.regionsy.get(minIdxReg.idx));

			sec1x = Arrays.copyOfRange(bigx, smallIdx, bigIdx);
			sec1y = Arrays.copyOfRange(bigy, smallIdx, bigIdx);

			sec2x = Arrays.copyOfRange(bigx, bigIdx, regions.regionsx.get(minIdxReg.idx).length + bigIdx - sec1x.length);
			sec2y = Arrays.copyOfRange(bigy, bigIdx, regions.regionsx.get(minIdxReg.idx).length + bigIdx - sec1x.length);

			// System.out.println("len big: " + bigx.length + "len sec 1: " +
			// sec1x.length + "len sec 2: " + sec2x.length);

			MinIdx minIdx1, minIdx2;

			dists = distsPtToContour(regions.tipx, regions.tipy, sec1x, sec1y);
			minIdx1 = getMinIdx(dists);
			dists = distsPtToContour(regions.tipx, regions.tipy, sec2x, sec2y);
			minIdx2 = getMinIdx(dists);

			if (minIdx1.min < minIdx2.min) {
				regions.detectionx = sec1x;
				regions.detectiony = sec1y;
			} else {
				regions.detectionx = sec2x;
				regions.detectiony = sec2y;
			}

			this.selectedRegx = regions.regionsx.get(minIdxReg.idx);
			this.selectedRegy = regions.regionsy.get(minIdxReg.idx);

			rtn = "success";
		} catch (Exception e) {
			rtn = "fail";

		}
		return rtn;
	}

	private double[] concatDbl(double[] a, double[] b) {
		int aLen = a.length;
		int bLen = b.length;
		double[] c = new double[aLen + bLen];
		System.arraycopy(a, 0, c, 0, aLen);
		System.arraycopy(b, 0, c, aLen, bLen);
		return c;
	}

	private MinIdx getMinIdx(double[] vals) {
		MinIdx rtn = new MinIdx();
		rtn.min = 999999999;
		rtn.idx = -1;

		for (int j = 0; j < vals.length; j++) {
			if (vals[j] <= rtn.min) {
				rtn.min = vals[j];
				rtn.idx = j;
			}
		}
		return rtn;
	}

	private double[] distsPtToContour(double ptx, double pty, double[] regionsx, double[] regionsy) {
		double[] dists = new double[regionsx.length];
		double dx, dy;
		for (int j = 0; j < regionsx.length; j++) {
			dx = regionsx[j] - ptx;
			dy = regionsy[j] - pty;
			dists[j] = Math.sqrt((dx * dx) + (dy * dy));
		}

		return dists;
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// For testing only

	public void setRegion(double[] x, double[] y) {
		this.selectedRegx = x;
		this.selectedRegy = y;
	}

	public void setDetection(double[] x, double[] y) {
		regions.detectionx = x;
		regions.detectiony = y;
	}

	public void setRegionWithIndex(int idx) {
		this.selectedRegx = regions.regionsx.get(idx);
		this.selectedRegy = regions.regionsy.get(idx);
	}

	public void addRegion(double[] x, double[] y) {
		System.out.println("received contour of length " + x.length);
		regions.regionsx.add(x);
		regions.regionsy.add(y);
	}

	public double[] getContour(String str) {
		switch (str) {
		case "bigx":
			return bigx;
		case "bigy":
			return bigy;
		case "sec1x":
			return sec1x;
		case "sec1y":
			return sec1y;
		case "sec2x":
			return sec2x;
		case "sec2y":
			return sec2y;
		}
		double[] blank = { -1 };
		return blank;
	}
}

class MinIdx {
	double min;
	int idx;
}

class FinDetectObjects {
	static class Regions {
		int szx, szy;
		long date;
		double tipx, tipy, lex, ley, tex, tey;
		int ismanual, selectedRegion;
		double[] detectionx, detectiony;
		List<double[]> regionsx, regionsy;
		int detectionIsBad;

	}
}