package sim.logging;

import java.io.*;
import java.util.regex.*;
import java.util.*;

import bgp.dataStructures.CIDR;

import sim.network.dataObjects.AS;

public class WeightedLogParser {

	private String attackFile;

	private List<Integer> asList;

	private HashMap<Integer, AS> asMap;
	
	private ASIPParse weights;

	private static final int TIMESLICE = 5000;

	private static final double MEDLOAD = 42;

	// (time) (type) (dest) (src)
	private static final Pattern MAPattern = Pattern.compile("(\\d++)ma(\\d)t(\\d++)f(\\d++)o([0-9\\./]++)");
	
	public static void main(String args[]) throws IOException{
		ASIPParse obj = new ASIPParse("/export/scratch/schuch/lci/routeViews/readable.txt");
		WeightedLogParser me = new WeightedLogParser("64klargerlog", obj);
		me.messageSweep();
		me.buildCDFs(175000);
	}

	public WeightedLogParser(String attackFile, ASIPParse weights) {
		this.attackFile = attackFile;
		this.asMap = weights.getASMap();
		this.weights = weights;
		this.asList = new LinkedList<Integer>();
		this.asList.addAll(this.asMap.keySet());
	}

	public void messageSweep() {
		int currentTime = 0;
		HashMap<Integer, Integer> arriveMap = new HashMap<Integer, Integer>();
		int nonWeightedCount = 0;
		int unFoundCount = 0;
		int total = 0;
		
		HashMap<CIDR, Integer> netToASMap = new HashMap<CIDR, Integer>();
		for(int tASN: this.asMap.keySet()){
			netToASMap.put(this.asMap.get(tASN).getAnyNetwork(), tASN);
		}

		try {
			BufferedReader logBuff = new BufferedReader(new FileReader(SimLogger.DIR + this.attackFile + SimLogger.EXT));
			BufferedWriter arriveCSV = new BufferedWriter(new FileWriter(SimLogger.DIR + this.attackFile + "MA.csv"));

			String header = "time";
			for (int as : this.asList) {
				header += "," + as;
			}
			header += "\n";
			arriveCSV.write(header);

			while (logBuff.ready()) {
				String pollString = logBuff.readLine();
				Matcher search = WeightedLogParser.MAPattern.matcher(pollString);
				if (search.find()) {
					if (Integer.parseInt(search.group(1)) > currentTime + WeightedLogParser.TIMESLICE) {
						this.dumpAndClean(arriveMap, arriveCSV, currentTime);
						currentTime += WeightedLogParser.TIMESLICE;
					}

					int key = Integer.parseInt(search.group(3));
					
					int value;
					String cidrStr = search.group(5);
					if (cidrStr.equals("0.0.0.0/0")) {
						value = 1;
						nonWeightedCount++;
					} else {
						CIDR tCIDR = new CIDR(search.group(5));
						Integer objValue = this.weights.getASWeighting().get(netToASMap.get(tCIDR));
						if (objValue == null) {
							value = 1;
							unFoundCount++;
						} else {
							value = objValue.intValue();
						}
					}
					
					total++;
					if (arriveMap.containsKey(key)) {
						arriveMap.put(key, arriveMap.get(key) + value);
					} else {
						arriveMap.put(key, value);
					}
				}
			}

			arriveCSV.close();
			logBuff.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		System.out.println("NW count: " + (double)nonWeightedCount/(double)total);
		System.out.println("UF count: " + (double)unFoundCount/(double)total);
	}

	public void buildCDFs(int avoidTime) {
		int currentTime;
		HashMap<Integer, Integer> sumMap = new HashMap<Integer, Integer>();
		HashMap<Integer, PriorityQueue<Integer>> medMap = new HashMap<Integer, PriorityQueue<Integer>>();
		HashMap<Integer, Double> avgMap = new HashMap<Integer, Double>();
		HashMap<Integer, Double> medians = new HashMap<Integer, Double>();
		HashMap<Integer, Double> threeQuarters = new HashMap<Integer, Double>();
		HashMap<Integer, Double> nineTenths = new HashMap<Integer, Double>();

		HashMap<Integer, Integer> tierCount = new HashMap<Integer, Integer>();
		tierCount.put(AS.T1, 0);
		tierCount.put(AS.T2, 0);
		tierCount.put(AS.T3, 0);
		for (int as : this.asList) {
			tierCount.put(this.asMap.get(as).getTier(), tierCount.get(this.asMap.get(as).getTier()) + 1);
		}

		for (int as : this.asList) {
			sumMap.put(as, 0);
			medMap.put(as, new PriorityQueue<Integer>());
		}

		try {
			BufferedReader csvBuffer = new BufferedReader(new FileReader(SimLogger.DIR + this.attackFile + "MA.csv"));

			int turnCount = 0;
			csvBuffer.readLine();

			while (csvBuffer.ready()) {
				String pollString = csvBuffer.readLine();
				StringTokenizer values = new StringTokenizer(pollString, ",");
				currentTime = Integer.parseInt(values.nextToken());

				if (currentTime >= avoidTime) {
					turnCount++;
					for (int as : this.asList) {
						int tVal = Integer.parseInt(values.nextToken());
						sumMap.put(as, sumMap.get(as) + tVal);
						medMap.get(as).add(tVal);
					}
				}
			}

			int zeroMedianCount = 0;
			for (int as : this.asList) {
				avgMap.put(as, (double) sumMap.get(as) / (double) turnCount);
				int count = medMap.get(as).size();
				int half = (int) Math.floor(count / 2);
				int p75 = (int) Math.ceil(count * .75);
				int p90 = (int) Math.ceil(count * .90);
				int foo = 0;
				if (count > 0) {
					for (int step = 0; step < half; step++) {
						foo = medMap.get(as).poll();
					}
					if (count % 2 == 1) {
						medians.put(as, (double) medMap.get(as).poll());
					} else {
						foo += medMap.get(as).poll();
						medians.put(as, (double) foo / 2);
					}
					
					for(int step = 0; step < (p75 - half); step++){
						foo = medMap.get(as).poll();
					}
					threeQuarters.put(as, (double) medMap.get(as).poll());
					
					for(int step = 0; step < (p90 - p75); step++){
						foo = medMap.get(as).poll();
					}
					nineTenths.put(as, (double) medMap.get(as).poll());
				} else {
					medians.put(as, 0.0);
					threeQuarters.put(as, 0.0);
					nineTenths.put(as, 0.0);
					zeroMedianCount++;
				}
			}

			/*
			 * This should only happen if we've ignored all lines of the CSV,
			 * this should only happen if the ignore time is set AFTER all
			 * messages are done
			 */
			if (zeroMedianCount > 0) {
				System.out.println("zero message counts: " + zeroMedianCount);
				System.out.println("this means that the ignore time is not set right...");
			}

			BufferedWriter outWriter;
			HashSet<Integer> noted = new HashSet<Integer>();
			outWriter = new BufferedWriter(new FileWriter(SimLogger.DIR + this.attackFile + "-1CDFmed.csv"));
			for (int counter = 0; counter < tierCount.get(AS.T1); counter++) {
				double minValue = Double.MAX_VALUE;
				int minAS = -1;
				for (int as : this.asList) {
					if (!noted.contains(as) && this.asMap.get(as).getTier() == AS.T1) {
						if (medians.get(as) < minValue) {
							minValue = medians.get(as);
							minAS = as;
						}
					}
				}
				noted.add(minAS);
				outWriter.write("" + ((minValue + WeightedLogParser.MEDLOAD) / WeightedLogParser.MEDLOAD * 100) + ","
						+ ((double) noted.size() / (double) tierCount.get(AS.T1)) + "\n");
			}
			outWriter.close();
			noted.clear();

			outWriter = new BufferedWriter(new FileWriter(SimLogger.DIR + this.attackFile + "-75th.csv"));
			for (int counter = 0; counter < tierCount.get(AS.T1); counter++) {
				double minValue = Double.MAX_VALUE;
				int minAS = -1;
				for (int as : this.asList) {
					if (!noted.contains(as) && this.asMap.get(as).getTier() == AS.T1) {
						if (threeQuarters.get(as) < minValue) {
							minValue = threeQuarters.get(as);
							minAS = as;
						}
					}
				}
				noted.add(minAS);
				outWriter.write("" + minValue + ","
						+ ((double) noted.size() / (double) tierCount.get(AS.T1)) + "\n");
			}
			outWriter.close();
			noted.clear();

			outWriter = new BufferedWriter(new FileWriter(SimLogger.DIR + this.attackFile + "-90th.csv"));
			for (int counter = 0; counter < tierCount.get(AS.T1); counter++) {
				double minValue = Double.MAX_VALUE;
				int minAS = -1;
				for (int as : this.asList) {
					if (!noted.contains(as) && this.asMap.get(as).getTier() == AS.T1) {
						if (nineTenths.get(as) < minValue) {
							minValue = nineTenths.get(as);
							minAS = as;
						}
					}
				}
				noted.add(minAS);
				outWriter.write("" + minValue  + ","
						+ ((double) noted.size() / (double) tierCount.get(AS.T1)) + "\n");
			}
			outWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void dumpAndClean(HashMap<Integer, Integer> arrMap, BufferedWriter arrBuf, int time) throws IOException {
		this.dumpTable(arrMap, arrBuf, time);
		arrMap.clear();
	}

	private void dumpTable(HashMap<Integer, Integer> values, BufferedWriter buffer, int time) throws IOException {
		String writeString = "" + time;
		for (int asn : this.asList) {
			if (values.containsKey(asn)) {
				writeString += "," + values.get(asn);
			} else {
				writeString += ",0";
			}
		}
		writeString += "\n";

		buffer.write(writeString);
	}
}
