package sim.logging;

import java.io.*;
import java.util.*;
import sim.network.dataObjects.*;
import sim.network.assembly.RealTopology;

public class LogReParser {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws IOException {
		LogReParser tempParse = new LogReParser(
				"logs/250klargerlog", 175000);
		//tempParse.buildRunningAvg(209);

		//tempParse.buildAverages(); 
		//tempParse.buildPercentiles(0.75);
		//tempParse.buildPercentiles(0.90);

		//tempParse.buildSingleCDF(4766);
		//tempParse.buildSingleCDF(209);
		//tempParse.buildSingleCDF(7018);
		//tempParse.buildSingleCDF(1239);
		//tempParse.buildSingleCDF(3356);
	}

	private String fileName;
	private int avoidTime;
	private HashMap<Integer, List<Integer>> loadedData;
	private HashMap<Integer, List<Integer>> unsortedData;
	private HashMap<Integer, AS> asMap;

	private static final String FILE_EXT = "MA.csv";

	public LogReParser(String incFile, int avoidTime) throws IOException {
		this.fileName = incFile;
		this.avoidTime = avoidTime;
		this.loadedData = new HashMap<Integer, List<Integer>>();
		this.unsortedData = new HashMap<Integer, List<Integer>>();
		this.parseBaseFile();

		//RealTopology realTopo = new RealTopology(RealTopology.DEFAULT_AS_FILE, false, "OC3", "OC48", "OC192", "OC768");
		RealTopology realTopo = new RealTopology("conf/as_rel.txt", false, "OC3", "OC48", "OC192", "OC768");
		this.asMap = realTopo.getASMap();
	}

	private void parseBaseFile() throws IOException {
		BufferedReader baseBuff = new BufferedReader(new FileReader(this.fileName + LogReParser.FILE_EXT));

		String asLine = baseBuff.readLine();
		StringTokenizer lineTokens = new StringTokenizer(asLine, ",");
		String tempString = lineTokens.nextToken();
		HashMap<Integer, Integer> asPos = new HashMap<Integer, Integer>();

		if (!tempString.equals("time")) {
			System.err.println("first line off");
			System.exit(-1);
		}

		/*
		 * import the asns
		 */
		int counter = 1;
		while (lineTokens.hasMoreTokens()) {
			tempString = lineTokens.nextToken();
			this.loadedData.put(Integer.parseInt(tempString), new LinkedList<Integer>());
			this.unsortedData.put(Integer.parseInt(tempString), new LinkedList<Integer>());
			asPos.put(counter, Integer.parseInt(tempString));
			counter++;
		}

		while (baseBuff.ready()) {
			String updateLine = baseBuff.readLine();

			if (updateLine.trim().length() == 0) {
				continue;
			}

			lineTokens = new StringTokenizer(updateLine, ",");
			tempString = lineTokens.nextToken();

			/*
			 * skip the time in the sim when the attack isn't running
			 */
			int tempTime = Integer.parseInt(tempString);
			if (tempTime < this.avoidTime) {
				continue;
			}

			counter = 1;
			while (lineTokens.hasMoreTokens()) {
				String nextToken = lineTokens.nextToken();
				this.loadedData.get(asPos.get(counter)).add(Integer.parseInt(nextToken));
				this.unsortedData.get(asPos.get(counter)).add(Integer.parseInt(nextToken));
				counter++;
			}
		}

		baseBuff.close();

		for (int tAS : this.loadedData.keySet()) {
			Collections.sort(this.loadedData.get(tAS));
		}
	}

	public void buildAverages() throws IOException {

		List<Double> avgList = new LinkedList<Double>();
		for (int tASN : this.asMap.keySet()) {
			if (this.asMap.get(tASN).getTier() == AS.T1) {
				int sum = 0;
				List<Integer> updateList = this.loadedData.get(tASN);
				for (int tempValue : updateList) {
					sum += tempValue;
				}
				avgList.add(((((double) sum / 17.0) + 864.0) / 864.0) * 100.0);
			}
		}
		this.writeCDF(this.fileName + "-T1avg.csv", avgList);

		avgList.clear();
		for (int tASN : this.asMap.keySet()) {
			if (this.asMap.get(tASN).getTier() == AS.T2) {
				int sum = 0;
				List<Integer> updateList = this.loadedData.get(tASN);
				for (int tempValue : updateList) {
					sum += tempValue;
				}
				avgList.add(((((double) sum / 17.0) + 864.0) / 864.0) * 100.0);
			}
		}
		this.writeCDF(this.fileName + "-T2avg.csv", avgList);

		avgList.clear();
		for (int tASN : this.asMap.keySet()) {
			if (this.asMap.get(tASN).getTier() == AS.T3) {
				int sum = 0;
				List<Integer> updateList = this.loadedData.get(tASN);
				for (int tempValue : updateList) {
					sum += tempValue;
				}
				avgList.add(((((double) sum / 17.0) + 864.0) / 864.0) * 100.0);
			}
		}
		this.writeCDF(this.fileName + "-T3avg.csv", avgList);
	}

	public void buildPercentiles(double percent) throws IOException {

		List<Double> perList = new LinkedList<Double>();
		for (int tASN : this.asMap.keySet()) {
			if (this.asMap.get(tASN).getTier() == AS.T1) {
				List<Integer> valueList = this.loadedData.get(tASN);
				int pos = (int) Math.ceil((double) valueList.size() * percent);
				perList.add((double) valueList.get(pos));
			}
		}
		this.writeCDF(this.fileName + "-T1" + percent + ".csv", perList);

		perList.clear();
		for (int tASN : this.asMap.keySet()) {
			if (this.asMap.get(tASN).getTier() == AS.T2) {
				List<Integer> valueList = this.loadedData.get(tASN);
				int pos = (int) Math.ceil((double) valueList.size() * percent);
				perList.add((double) valueList.get(pos));
			}
		}
		this.writeCDF(this.fileName + "-T2" + percent + ".csv", perList);

		perList.clear();
		for (int tASN : this.asMap.keySet()) {
			if (this.asMap.get(tASN).getTier() == AS.T3) {
				List<Integer> valueList = this.loadedData.get(tASN);
				int pos = (int) Math.ceil((double) valueList.size() * percent);
				perList.add((double) valueList.get(pos));
			}
		}
		this.writeCDF(this.fileName + "-T3" + percent + ".csv", perList);
	}

	public void buildSingleCDF(int asn) throws IOException {
		if (!this.asMap.containsKey(asn)) {
			System.out.println("" + asn + " doesn't exist in data");
			return;
		} else {
			System.out.println("" + asn + " is a tier " + this.asMap.get(asn).getTier());
		}

		this.writeIntCDF("logs/" + asn + ".csv", this.loadedData.get(asn));
	}

	public void buildRunningAvg(int asn) throws IOException {
		if (!this.asMap.containsKey(asn)) {
			System.out.println("" + asn + " doesn't exist in data");
			return;
		} else {
			System.out.println("" + asn + " is a tier " + this.asMap.get(asn).getTier());
		}

		HashMap<Integer, Integer> baseMap = new HashMap<Integer, Integer>();
		BufferedReader baseBuff = new BufferedReader(new FileReader("/home/pendgaft/scratch/rv.csv"));
		int baseCounter = 0;
		while (baseBuff.ready()) {
			String tStr = baseBuff.readLine();
			StringTokenizer tTok = new StringTokenizer(tStr, ",");
			baseMap.put(baseCounter, Integer.parseInt(tTok.nextToken()));
			baseCounter++;
		}
		baseBuff.close();

		BufferedWriter outBuff = new BufferedWriter(new FileWriter("/home/pendgaft/scratch/running.csv"));

		List<Integer> tempList = this.unsortedData.get(asn);
		int avgCounter = 0;
		double targetValue = 0;
		double baseValue = 0;
		Random rng = new Random();

		for (int counter = 0; counter < 120; counter++) {
			int tempPoll = rng.nextInt(baseMap.size());
			int tempPoll2 = rng.nextInt(baseMap.size());

			targetValue = ((double) baseMap.get(tempPoll) + avgCounter * targetValue) / (double) (avgCounter + 1);
			baseValue = ((double) baseMap.get(tempPoll2) + avgCounter * baseValue) / (double) (avgCounter + 1);

			outBuff.write("" + (counter * 5) + "," + baseValue + "," + targetValue + "\n");

			avgCounter++;
		}

		for (int counter = 0; counter < 180; counter++) {
			int tempPoll2 = rng.nextInt(baseMap.size());
			baseValue = ((double) baseMap.get(tempPoll2) + avgCounter * baseValue) / (double) (avgCounter + 1);
			targetValue = ((double) tempList.get(counter + 36) + avgCounter * targetValue) / (double) (avgCounter + 1);

			outBuff.write("" + ((counter + 120) * 5) + "," + baseValue + "," + targetValue + "\n");

			avgCounter++;
		}

		outBuff.close();
	}

	private void writeCDF(String fileName, List<Double> values) throws IOException {
		Collections.sort(values);
		BufferedWriter outBuff = new BufferedWriter(new FileWriter(fileName));

		for (int counter = 0; counter < values.size(); counter++) {
			double percent = ((double) counter + 1.0) / (double) values.size();
			outBuff.write("" + values.get(counter) + "," + percent + "\n");
		}

		outBuff.close();
	}

	private void writeIntCDF(String fileName, List<Integer> values) throws IOException {
		Collections.sort(values);
		BufferedWriter outBuff = new BufferedWriter(new FileWriter(fileName));

		for (int counter = 0; counter < values.size(); counter++) {
			double percent = ((double) counter + 1.0) / (double) values.size();
			outBuff.write("" + values.get(counter) + "," + percent + "\n");
		}

		outBuff.close();
	}

	public void updateTimeCDF() {

		int asTier = AS.T1;
		List<Integer> updateValues;
		String fileEnd = "T1.csv";
		while (asTier != -1) {
			updateValues = new ArrayList<Integer>();
			for (int tASN : this.asMap.keySet()) {

				/*
				 * This as is of the correct tier
				 */
				if (this.asMap.get(tASN).getTier() == asTier) {

				}

			}

			/*
			 * Move the AS tier on to the next lowest tier, or -1 if we're done
			 */
			if (asTier == AS.T1) {
				asTier = AS.T2;
				fileEnd = "T2.csv";
			} else if (asTier == AS.T2) {
				asTier = AS.T3;
				fileEnd = "T3.csv";
			} else if (asTier == AS.T3) {
				asTier = -1;
				fileEnd = null;
			}
		}
	}
}
