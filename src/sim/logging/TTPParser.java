package sim.logging;

import java.util.*;
import java.io.*;

import sim.network.assembly.RealTopology;
import sim.network.dataObjects.AS;

public class TTPParser {

	private HashMap<Integer, Double> openTimeMap;
	private HashMap<Integer, Double> cAverageMap;
	private HashMap<Integer, Integer> posMap;

	public static void main(String[] args) throws IOException {
		RealTopology tempRTopoBuilder = new RealTopology("/scratch/minerva/schuch/stormcaller/conf/as_rel.txt", true,
				"OC3", "OC48", "OC192", "OC768");
		TTPParser obj = new TTPParser("/scratch/minerva/schuch/stormcaller/logs/maxint12largelog", 0.4,
				tempRTopoBuilder.getASMap());
	}

	public TTPParser(String messageFile, double baseTTP, HashMap<Integer, AS> asMap) throws IOException {

		this.openTimeMap = new HashMap<Integer, Double>();
		this.cAverageMap = new HashMap<Integer, Double>();
		this.posMap = new HashMap<Integer, Integer>();

		BufferedReader logBuff = new BufferedReader(new FileReader(SimLogger.DIR + messageFile + "MA.csv"));
		BufferedWriter ttpAverageBuff = new BufferedWriter(new FileWriter(SimLogger.DIR + messageFile + "-TTP-AVG.csv"));
		BufferedWriter ttpMedianBuff = new BufferedWriter(new FileWriter(SimLogger.DIR + messageFile + "-TTP-MED.csv"));

		String asString = logBuff.readLine();
		StringTokenizer asTokens = new StringTokenizer(asString, ",");
		asTokens.nextToken();
		int posCounter = 0;
		int t1Count = 0;
		while (asTokens.hasMoreTokens()) {
			int tASN = Integer.parseInt(asTokens.nextToken());

			posMap.put(posCounter, tASN);
			openTimeMap.put(tASN, 0.0);
			cAverageMap.put(tASN, 0.0);

			if (asMap.get(tASN).getTier() == AS.T1) {
				t1Count++;
			}

			posCounter++;
		}

		while (logBuff.ready()) {
			String poll = logBuff.readLine();
			StringTokenizer pollTokens = new StringTokenizer(poll, ",");

			int time = Integer.parseInt(pollTokens.nextToken());
			posCounter = 0;
			double systemSum = 0.0;
			List<Double> ttpPerASList = new LinkedList<Double>();
			while (pollTokens.hasMoreTokens()) {
				int currentAS = this.posMap.get(posCounter);
				int load = Integer.parseInt(pollTokens.nextToken());
				posCounter++;

				if (asMap.get(currentAS).getTier() != AS.T1) {
					continue;
				}

				double value = Math.max(0.0, this.openTimeMap.get(currentAS) - time) + ((load + 1) / 2) * baseTTP;
				double processTime = load * baseTTP;

				if (this.openTimeMap.get(currentAS) <= time) {
					this.openTimeMap.put(currentAS, time + processTime);
				} else {
					this.openTimeMap.put(currentAS, this.openTimeMap.get(currentAS) + processTime);
				}
				ttpPerASList.add(value);
				systemSum += value;
			}

			/*
			 * Compute the average across T1 ASes, dump to file
			 */
			systemSum = systemSum / t1Count;
			ttpAverageBuff.write("" + time + "," + systemSum + "\n");

			/*
			 * Compute the median across T1 ASes, dump to file
			 */
			Collections.sort(ttpPerASList);
			if (ttpPerASList.size() % 2 == 0) {
				ttpMedianBuff
						.write(""
								+ time
								+ ","
								+ ((ttpPerASList.get(ttpPerASList.size() / 2) + ttpPerASList
										.get(ttpPerASList.size() / 2 - 1)) / 2));
			} else {
				ttpMedianBuff.write("" + time + "," + ttpPerASList.get(ttpPerASList.size() / 2 - 1) + "\n");
			}
		}

		logBuff.close();
		ttpAverageBuff.close();
		ttpMedianBuff.close();
	}
}
