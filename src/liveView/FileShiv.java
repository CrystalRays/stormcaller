package liveView;

import sim.network.assembly.*;
import sim.network.dataObjects.AS;

import java.io.*;
import java.util.*;

public class FileShiv {

	public static void main(String[] args) throws IOException{
		RealTopology builder = new RealTopology("conf/as_rel.txt", false, "OC3",
				"OC48", "OC192", "OC768");
		HashMap<Integer, AS> asMap = builder.getASMap();
		
		BufferedWriter rankWriter = new BufferedWriter(new FileWriter("src/liveView/rank.txt"));
		for(int tAS: asMap.keySet()){
			rankWriter.write("" + tAS + " " + asMap.get(tAS).getTier() + "\n");
		}
		rankWriter.close();
		
		BufferedWriter linkWriter = new BufferedWriter(new FileWriter("src/liveView/link.txt"));
		for(int tAS: asMap.keySet()){
			for(AS tNeighbor: asMap.get(tAS).getAllNeighbors()){
				linkWriter.write("" + tAS + " " + tNeighbor.getASNumber() + "\n");
			}
		}
		linkWriter.close();
	}
}
