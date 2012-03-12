package sim.logging;

import java.io.*;
import java.util.regex.*;
import java.util.*;

import sim.network.dataObjects.AS;

public class LogParser {

	private String attackFile;

	private List<Integer> asList;

	private HashMap<Integer, AS> asMap;

	private static final int TIMESLICE = 5000;

	private static final double MEDLOAD = 42;

	// (time) (type) (dest) (src)
	private static final Pattern MAPattern = Pattern.compile("(\\d++)ma(\\d)t(\\d++)f(\\d++)");

	public LogParser(String attackFile, List<Integer> asList, HashMap<Integer, AS> asMap) {
		this.attackFile = attackFile;
		this.asList = asList;
		this.asMap = asMap;

		if (this.asList == null) {
			this.asList = new LinkedList<Integer>();
			this.asList.addAll(this.asMap.keySet());
		}
	}

	public void messageSweep() {
		int currentTime = 0;
		HashMap<Integer, Integer> arriveMap = new HashMap<Integer, Integer>();

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
				Matcher search = LogParser.MAPattern.matcher(pollString);
				if (search.find()) {
					if (Integer.parseInt(search.group(1)) > currentTime + LogParser.TIMESLICE) {
						this.dumpAndClean(arriveMap, arriveCSV, currentTime);
						currentTime += LogParser.TIMESLICE;
					}

					int key = Integer.parseInt(search.group(3));
					if (arriveMap.containsKey(key)) {
						arriveMap.put(key, arriveMap.get(key) + 1);
					} else {
						arriveMap.put(key, 1);
					}
				}
			}

			arriveCSV.close();
			logBuff.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void buildCDFs(int avoidTime) {
		int currentTime;
		HashMap<Integer, Integer> sumMap = new HashMap<Integer, Integer>();
		HashMap<Integer, PriorityQueue<Integer>> medMap = new HashMap<Integer, PriorityQueue<Integer>>();
		HashMap<Integer, Double> avgMap = new HashMap<Integer, Double>();
		HashMap<Integer, Double> medians = new HashMap<Integer, Double>();

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
				} else {
					medians.put(as, 0.0);
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
				outWriter.write("" + ((minValue + LogParser.MEDLOAD) / LogParser.MEDLOAD * 100) + ","
						+ ((double) noted.size() / (double) tierCount.get(AS.T1)) + "\n");
			}
			outWriter.close();
			noted.clear();

			outWriter = new BufferedWriter(new FileWriter(SimLogger.DIR + this.attackFile + "-2CDFmed.csv"));
			for (int counter = 0; counter < tierCount.get(AS.T2); counter++) {
				double minValue = Double.MAX_VALUE;
				int minAS = -1;
				for (int as : this.asList) {
					if (!noted.contains(as) && this.asMap.get(as).getTier() == AS.T2) {
						if (medians.get(as) < minValue) {
							minValue = medians.get(as);
							minAS = as;
						}
					}
				}
				noted.add(minAS);
				outWriter.write("" + ((minValue + LogParser.MEDLOAD) / LogParser.MEDLOAD * 100) + ","
						+ ((double) noted.size() / (double) tierCount.get(AS.T2)) + "\n");
			}
			outWriter.close();
			noted.clear();

			outWriter = new BufferedWriter(new FileWriter(SimLogger.DIR + this.attackFile + "-3CDFmed.csv"));
			for (int counter = 0; counter < tierCount.get(AS.T3); counter++) {
				double minValue = Double.MAX_VALUE;
				int minAS = -1;
				for (int as : this.asList) {
					if (!noted.contains(as) && this.asMap.get(as).getTier() == AS.T3) {
						if (medians.get(as) < minValue) {
							minValue = medians.get(as);
							minAS = as;
						}
					}
				}
				noted.add(minAS);
				outWriter.write("" + ((minValue + LogParser.MEDLOAD) / LogParser.MEDLOAD * 100) + ","
						+ ((double) noted.size() / (double) tierCount.get(AS.T3)) + "\n");
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
