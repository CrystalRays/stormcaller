package sim.util;

import java.util.*;
import java.io.*;

public class Stats {

	public static void dumpLongCDF(String fileName, List<Long> vals) throws IOException{
		List<Double> parseList = new ArrayList<Double>();
		for(Long tLong: vals){
			parseList.add(tLong.doubleValue());
		}
		
		Stats.dumpDoubleCDF(fileName, parseList);
	}
	
	public static void dumpIntCDF(String fileName, List<Integer> vals) throws IOException{
		List<Double> parseList = new ArrayList<Double>();
		for(Integer tInt: vals){
			parseList.add(tInt.doubleValue());
		}
		
		Stats.dumpDoubleCDF(fileName, parseList);
	}
	
	public static void dumpDoubleCDF(String fileName, List<Double> vals) throws IOException{
		Collections.sort(vals);
		
		BufferedWriter outBuff = new BufferedWriter(new FileWriter(fileName));
		for(int counter = 0; counter < vals.size(); counter++){
			double frac = (double)(counter + 1.0) / (double)vals.size();
			outBuff.write("" + frac + "," + vals.get(counter) + "\n");
		}
		outBuff.close();
	}
	
}
