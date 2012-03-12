package sim.logging;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.*;

import sim.network.assembly.RealTopology;
import sim.network.dataObjects.*;

public class LinkKillProfile {

	private HashSet<String> targets = null;
	private HashMap<Integer, AS> asMap = null;
	
	public LinkKillProfile(String logFile, String targetFile) throws IOException{
		RealTopology tRT = new RealTopology("/scratch/minerva/schuch/stormcaller/conf/as_rel.txt", true, "OC3", "OC48", "OC768", "OC768");
		this.asMap = tRT.getASMap();
		
		BufferedReader targetBuff = new BufferedReader(new FileReader(targetFile));
		Pattern targetPatter = Pattern.compile("(\\d+) <--> (\\d+)");
		this.targets = new HashSet<String>();
		while(targetBuff.ready()){
			Matcher matt = targetPatter.matcher(targetBuff.readLine());
			if(matt.find()){
				int lhs = Integer.parseInt(matt.group(1));
				int rhs = Integer.parseInt(matt.group(2));
				targets.add("" + lhs + "-" + rhs);
				targets.add("" + rhs + "-" + lhs);
			}
			else{
				System.err.println("wtf...");
			}
		}
		targetBuff.close();
		
		HashSet<String> trippedT3Links = new HashSet<String>();
		HashSet<String> trippedNonT3Links = new HashSet<String>();
		HashSet<String> trippedLastMile = new HashSet<String>();
		HashSet<String> trippedNonLastMile = new HashSet<String>();
		HashSet<String> trippedTarget = new HashSet<String>();
		
		Pattern timeoutPattern = Pattern.compile(LoggingMessages.TIMEOUT_PATTERN);
		BufferedReader logBuff = new BufferedReader(new FileReader(logFile));
		while(logBuff.ready()){
			String poll = logBuff.readLine();
			Matcher tMatch = timeoutPattern.matcher(poll);
			
			if(tMatch.find()){
				int lhs = Integer.parseInt(tMatch.group(1));
				int rhs = Integer.parseInt(tMatch.group(2));
				
				String a = "" + lhs + "-" + rhs;
				String b = "" + rhs + "-" + lhs;
				
				if(this.targets.contains(a)){
					trippedTarget.add(a);
					trippedTarget.add(b);
				}
				else{
					if(this.asMap.get(lhs).getTier() == AS.T3 || this.asMap.get(rhs).getTier() == AS.T3){
						trippedT3Links.add(a);
						trippedT3Links.add(b);
					}
					else{
						trippedNonT3Links.add(a);
						trippedNonT3Links.add(b);
					}
					
					if(this.asMap.get(lhs).getCustomers().size() == 0 || this.asMap.get(rhs).getCustomers().size() == 0){
						trippedLastMile.add(a);
						trippedLastMile.add(b);
					}
					else{
						trippedNonLastMile.add(a);
						trippedNonLastMile.add(b);
					}
				}
			}
		}
		
		System.out.println("targeted: " + (double)trippedTarget.size()/(double)this.targets.size());
		int t3Count = 0;
		int lastMileCount = 0;
		int fullCount = 0;
		for(int tASN: this.asMap.keySet()){
			fullCount += this.asMap.get(tASN).getAllNeighbors().size();
			
			if(this.asMap.get(tASN).getTier() == AS.T3){
				t3Count += this.asMap.get(tASN).getAllNeighbors().size();
			}
			else{
				for(AS tAS: this.asMap.get(tASN).getAllNeighbors()){
					if(tAS.getTier() == AS.T3){
						t3Count++;
					}
				}
			}
			
			if(this.asMap.get(tASN).getCustomers().size() == 0){
				lastMileCount += this.asMap.get(tASN).getAllNeighbors().size();
			}
			else{
				for(AS tAS: this.asMap.get(tASN).getAllNeighbors()){
					if(tAS.getCustomers().size() == 0){
						lastMileCount++;
					}
				}
			}
		}
		
		System.out.println("t3: " + (double)trippedT3Links.size()/(double)t3Count);
		System.out.println(t3Count);
		System.out.println("non t3: " + (double)trippedNonT3Links.size()/(double)(fullCount - t3Count - this.targets.size()));
		System.out.println("last mile: " + (double)trippedLastMile.size()/(double)lastMileCount);
		System.out.println(lastMileCount);
		System.out.println("non last mile: " + (double)trippedNonLastMile.size()/(double)(fullCount - lastMileCount - this.targets.size()));
		System.out.println(fullCount);
	}
	
	public static void main(String[] args) throws IOException{
		LinkKillProfile obj = new LinkKillProfile("/scratch/minerva/schuch/stormcaller/logs/250kfastrouter30secoc768.log", "/scratch/waterhouse/schuch/stormcaller/targets.txt");

	}

}
