package liveView;

import java.util.*;
import java.io.*;

public class MakespanPlayer {

	private HashMap<Integer, ArrayList<Double>> makespanLists;
	private int playCount;
	private int maxCount;
	
	private static final double TTP = .4;
	
	public MakespanPlayer(String maFile) throws IOException{
		this.makespanLists = new HashMap<Integer, ArrayList<Double>>();
		this.playCount = 0;
		this.maxCount = 0;
		
		this.parseMA(maFile);
	}
	
	private void parseMA(String maFile) throws IOException{
		BufferedReader inBuff = new BufferedReader(new FileReader(maFile));
		HashMap<Integer, Integer> posMap = new HashMap<Integer, Integer>();
		HashMap<Integer, Double> openTimeMap = new HashMap<Integer, Double>();
		
		String firstLine = inBuff.readLine();
		StringTokenizer tokens = new StringTokenizer(firstLine, ",");
		String theToken = tokens.nextToken();
		
		int counter = 0;
		while(tokens.hasMoreTokens()){
			theToken = tokens.nextToken();
			int tASN = Integer.parseInt(theToken);
			posMap.put(counter, tASN);
			counter++;
			this.makespanLists.put(tASN, new ArrayList<Double>());
			openTimeMap.put(tASN, 0.0);
		}
		
		while(inBuff.ready()){
			firstLine = inBuff.readLine();
			tokens = new StringTokenizer(firstLine, ",");			
			theToken = tokens.nextToken();
			int time = Integer.parseInt(theToken);
			counter = 0;
			
			while(tokens.hasMoreTokens()){
				
				int currentAS = posMap.get(counter);
				int load = Integer.parseInt(tokens.nextToken());
				
				double value = Math.max(0.0, openTimeMap.get(currentAS) - time) + ((load + 1) / 2) * MakespanPlayer.TTP;
				double processTime = load * MakespanPlayer.TTP;

				if (openTimeMap.get(currentAS) <= time) {
					openTimeMap.put(currentAS, time + processTime);
				} else {
					openTimeMap.put(currentAS, openTimeMap.get(currentAS) + processTime);
				}
				this.makespanLists.get(currentAS).add(value);
				
				counter++;
			}
			this.maxCount++;
		}
		
		inBuff.close();
	}
	
	public void nextRound(){
		this.playCount++;
	}
	
	public boolean notDone(){
		return this.playCount < this.maxCount;
	}
	
	public double getPercentDone(){
		return (double)this.playCount / (double) this.maxCount;
	}
	
	public double getDelay(int asn){
		return this.makespanLists.get(asn).get(this.playCount);
	}
}
