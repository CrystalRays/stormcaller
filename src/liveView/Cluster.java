package liveView;

import java.io.*;
import java.util.*;

import sim.network.dataObjects.AS;

public class Cluster {

	private HashMap<Integer, HashSet<Integer>> edgeList;
	private HashMap<Integer, Integer> rankMap;

	private HashMap<Integer, HashMap<Integer, Integer>> t2Assoc;
	private HashMap<Integer, HashMap<Integer, Integer>> t1Assoc;

	private Ring outterCluster;
	private Ring middleCluster;
	private Ring innerCluster;

	private static final int T1BREAK = 3;

	public Cluster(String topoFile, String rankFile) throws IOException {
		this.edgeList = new HashMap<Integer, HashSet<Integer>>();
		this.rankMap = new HashMap<Integer, Integer>();
		this.t2Assoc = new HashMap<Integer, HashMap<Integer, Integer>>();
		this.t1Assoc = new HashMap<Integer, HashMap<Integer, Integer>>();

		/*
		 * setup some parsing vars
		 */

		String poll = null;
		StringTokenizer pollTokens = null;

		/*
		 * Read in the rank file
		 */
		BufferedReader rankReader = new BufferedReader(new FileReader(rankFile));
		while (rankReader.ready()) {
			poll = rankReader.readLine();
			pollTokens = new StringTokenizer(poll, " ");

			this.rankMap.put(Integer.parseInt(pollTokens.nextToken()), Integer.parseInt(pollTokens.nextToken()));
		}
		rankReader.close();

		/*
		 * Load the edge list into memory
		 */
		BufferedReader topoReader = new BufferedReader(new FileReader(topoFile));
		while (topoReader.ready()) {
			poll = topoReader.readLine();
			pollTokens = new StringTokenizer(poll, " ");

			int lhsAS = Integer.parseInt(pollTokens.nextToken());
			int rhsAS = Integer.parseInt(pollTokens.nextToken());

			if (!this.edgeList.containsKey(lhsAS)) {
				this.edgeList.put(lhsAS, new HashSet<Integer>());
			}
			if (!this.edgeList.containsKey(rhsAS)) {
				this.edgeList.put(rhsAS, new HashSet<Integer>());
			}

			this.edgeList.get(lhsAS).add(rhsAS);
			this.edgeList.get(rhsAS).add(lhsAS);
		}
		topoReader.close();

		/*
		 * Build asssociation matrix for each as
		 */
		for (int tAS : this.edgeList.keySet()) {
			this.buildAssoc(tAS);
		}

		this.buildRings();
	}

	private void buildAssoc(int targetAS) {
		int rank = this.rankMap.get(targetAS);
		HashMap<Integer, HashMap<Integer, Integer>> masterTable;
		HashSet<Integer> myEdges = this.edgeList.get(targetAS);

		if (rank == AS.T3) {
			return;
		} else if (rank == AS.T2) {
			masterTable = this.t2Assoc;
		} else {
			masterTable = this.t1Assoc;
		}

		HashMap<Integer, Integer> newAssoc = new HashMap<Integer, Integer>();

		for (int tAS : this.edgeList.keySet()) {
			if (tAS == targetAS) {
				continue;
			}
			if (this.rankMap.get(tAS) != rank) {
				continue;
			}
			HashSet<Integer> tempSet = new HashSet<Integer>();
			tempSet.addAll(myEdges);
			tempSet.retainAll(this.edgeList.get(tAS));

			newAssoc.put(tAS, tempSet.size());
		}

		masterTable.put(targetAS, newAssoc);
	}

	private void buildRings() {
		this.outterCluster = new Ring();
		this.middleCluster = new Ring();
		this.innerCluster = new Ring();

		this.buildMiddleRing();
		this.buildOutterRing();
		this.buildInnerRing();

		//this.altBuildInnerRing();
		//this.altBuildMiddleRing();
		//this.altBuildOutterRing();
	}

	private void buildMiddleRing() {
		HashSet<Integer> waitingT2 = new HashSet<Integer>();

		/*
		 * Add all t2 ases to the set of unplaced T2 ases
		 */
		for (int tAS : this.rankMap.keySet()) {
			if (this.rankMap.get(tAS) == AS.T2) {
				waitingT2.add(tAS);
			}
		}

		/*
		 * While we still have unplaced T2's, place them...
		 */
		while (waitingT2.size() > 0) {
			int nextAS = -1;
			int maxSim = -1;
			int partnerAS = -1;

			for (int tAS : this.t2Assoc.keySet()) {
				/*
				 * if it's already placed, move on
				 */
				if (!waitingT2.contains(tAS)) {
					continue;
				}

				int focusPartner = -1;
				int focusMax = -1;
				HashMap<Integer, Integer> tAssoc = this.t2Assoc.get(tAS);
				for (int tFocus : tAssoc.keySet()) {
					if (tAssoc.get(tFocus) > focusMax) {
						focusMax = tAssoc.get(tFocus);
						focusPartner = tFocus;
					}
				}

				if (focusMax > maxSim) {
					nextAS = tAS;
					partnerAS = focusPartner;
					maxSim = focusMax;
				}
			}

			/*
			 * Assuming we found the highest association pair, add them
			 */
			if (nextAS == -1) {
				throw new RuntimeException("not empty, but couldn't select....FUCKKKKK");
			} else {
				/*
				 * They might both be unplaced, in that case, start a new
				 * cluster with one of them
				 */
				if (waitingT2.contains(partnerAS)) {
					this.middleCluster.addToNewCluster(partnerAS);
					waitingT2.remove(partnerAS);
				}

				/*
				 * Add the node to the linked cluster
				 */
				this.middleCluster.addNodeToExisting(nextAS, partnerAS);
				waitingT2.remove(nextAS);
			}
		}
	}

	private void buildOutterRing() {
		HashSet<Integer> toBePlaced = new HashSet<Integer>();
		for (int tAS : this.rankMap.keySet()) {
			if (this.rankMap.get(tAS) == AS.T3) {
				toBePlaced.add(tAS);
			}
		}

		HashSet<Integer> fillIn = new HashSet<Integer>();
		HashSet<Integer> secondPass = new HashSet<Integer>();

		/*
		 * This pass places all heavily T2 associated T3 nodes
		 */
		for (int tAS : toBePlaced) {
			int t2Count = 0;
			int t3Count = 0;

			for (int tLink : this.edgeList.get(tAS)) {
				if (this.rankMap.get(tLink) == AS.T2) {
					t2Count++;
				}
				if (this.rankMap.get(tLink) == AS.T3) {
					t3Count++;
				}
			}

			if (t2Count == 0 && t3Count == 0) {
				fillIn.add(tAS);
				continue;
			}

			if (t3Count == 0 || t2Count > Cluster.T1BREAK) {
				int winningCluster = this.middleCluster.getLargestClusterIntersect(this.edgeList.get(tAS));
				this.outterCluster.directlyLinkNodeToCluster(tAS, winningCluster);
			} else {
				secondPass.add(tAS);
			}
		}

		/*
		 * Now to place all heavily T3 associated nodes
		 */
		for (int tAS : secondPass) {
			int winningCluster = this.outterCluster.getLargestClusterIntersect(this.edgeList.get(tAS));

			if (winningCluster != -1) {
				this.outterCluster.directlyLinkNodeToCluster(tAS, winningCluster);
			} else {
				this.outterCluster.directlyLinkNodeToCluster(tAS, this.outterCluster.getSmallestCluster());
			}
		}

		/*
		 * Try to fill in the empty clusters
		 */
		for (int tAS : fillIn) {
			this.outterCluster.directlyLinkNodeToCluster(tAS, this.outterCluster.getSmallestCluster());
		}
	}

	private void buildInnerRing() {
		HashSet<Integer> toBePlaced = new HashSet<Integer>();
		for (int tAS : this.rankMap.keySet()) {
			if (this.rankMap.get(tAS) == AS.T1) {
				toBePlaced.add(tAS);
			}
		}

		HashSet<Integer> filler = new HashSet<Integer>();

		for (int tAS : toBePlaced) {
			int winningClusterPair = -1;
			int maxSize = 0;

			for (int counter = 0; counter < this.middleCluster.getClusterCount(); counter++) {
				HashSet<Integer> mid = this.middleCluster.getNodesInCluster(counter);
				HashSet<Integer> outter = this.outterCluster.getNodesInCluster(counter);

				mid.retainAll(this.edgeList.get(tAS));
				outter.retainAll(this.edgeList.get(tAS));

				int sum = mid.size() + outter.size();
				if (sum > maxSize) {
					maxSize = sum;
					winningClusterPair = counter;
				}
			}

			if (winningClusterPair != -1) {
				this.innerCluster.directlyLinkNodeToCluster(tAS, winningClusterPair);
			} else {
				filler.add(tAS);
			}
		}

		for (int tAS : filler) {
			this.innerCluster.directlyLinkNodeToCluster(tAS, this.innerCluster.getSmallestCluster());
		}
	}

	private void altBuildInnerRing() {
		HashSet<Integer> toBePlaced = new HashSet<Integer>();

		/*
		 * Add all t2 ases to the set of unplaced T2 ases
		 */
		for (int tAS : this.rankMap.keySet()) {
			if (this.rankMap.get(tAS) == AS.T1) {
				toBePlaced.add(tAS);
			}
		}

		int kMax = 30;

		/*
		 * While we still have unplaced T1's, place them...
		 */
		while (toBePlaced.size() > 0) {
			int nextAS = -1;
			int maxSim = -1;
			int partnerAS = -1;

			for (int tAS : this.t1Assoc.keySet()) {
				/*
				 * if it's already placed, move on
				 */
				if (!toBePlaced.contains(tAS)) {
					continue;
				}

				int focusPartner = -1;
				int focusMax = -1;
				HashMap<Integer, Integer> tAssoc = this.t1Assoc.get(tAS);
				for (int tFocus : tAssoc.keySet()) {
					if (!toBePlaced.contains(tFocus)) {
						int currCluster = this.innerCluster.locateNode(tFocus);
						if (this.innerCluster.getCluster(currCluster).size() > kMax) {
							continue;
						}
					}

					if (tAssoc.get(tFocus) > focusMax) {
						focusMax = tAssoc.get(tFocus);
						focusPartner = tFocus;
					}
				}

				if (focusMax > maxSim) {
					nextAS = tAS;
					partnerAS = focusPartner;
					maxSim = focusMax;
				}
			}

			/*
			 * Assuming we found the highest association pair, add them
			 */
			if (nextAS == -1) {
				throw new RuntimeException("not empty, but couldn't select....FUCKKKKK");
			} else {
				/*
				 * They might both be unplaced, in that case, start a new
				 * cluster with one of them
				 */
				if (toBePlaced.contains(partnerAS)) {
					this.innerCluster.addToNewCluster(partnerAS);
					toBePlaced.remove(partnerAS);
				}

				/*
				 * Add the node to the linked cluster
				 */
				this.innerCluster.addNodeToExisting(nextAS, partnerAS);
				toBePlaced.remove(nextAS);
			}
		}
	}

	private void altBuildMiddleRing() {
		HashSet<Integer> toBePlaced = new HashSet<Integer>();
		for (int tAS : this.rankMap.keySet()) {
			if (this.rankMap.get(tAS) == AS.T2) {
				toBePlaced.add(tAS);
			}
		}

		HashSet<Integer> fillIn = new HashSet<Integer>();
		HashSet<Integer> secondPass = new HashSet<Integer>();

		/*
		 * This pass places all heavily T2 associated T3 nodes
		 */
		for (int tAS : toBePlaced) {
			int t1Count = 0;
			int t2Count = 0;

			for (int tLink : this.edgeList.get(tAS)) {
				if (this.rankMap.get(tLink) == AS.T1) {
					t1Count++;
				}
				if (this.rankMap.get(tLink) == AS.T2) {
					t2Count++;
				}
			}

			if (t1Count == 0 && t2Count == 0) {
				fillIn.add(tAS);
				continue;
			}

			if (t2Count == 0 || t1Count > Cluster.T1BREAK) {
				int winningCluster = this.innerCluster.getLargestClusterIntersect(this.edgeList.get(tAS));
				this.middleCluster.directlyLinkNodeToCluster(tAS, winningCluster);
			} else {
				secondPass.add(tAS);
			}
		}

		/*
		 * Now to place all heavily T3 associated nodes
		 */
		for (int tAS : secondPass) {
			int winningCluster = this.middleCluster.getLargestClusterIntersect(this.edgeList.get(tAS));

			if (winningCluster != -1) {
				this.middleCluster.directlyLinkNodeToCluster(tAS, winningCluster);
			} else {
				this.middleCluster.directlyLinkNodeToCluster(tAS, this.middleCluster.getSmallestCluster());
			}
		}

		/*
		 * Try to fill in the empty clusters
		 */
		for (int tAS : fillIn) {
			this.middleCluster.directlyLinkNodeToCluster(tAS, this.middleCluster.getSmallestCluster());
		}
	}

	private void altBuildOutterRing() {
		HashSet<Integer> toBePlaced = new HashSet<Integer>();
		for (int tAS : this.rankMap.keySet()) {
			if (this.rankMap.get(tAS) == AS.T3) {
				toBePlaced.add(tAS);
			}
		}

		HashSet<Integer> filler = new HashSet<Integer>();

		for (int tAS : toBePlaced) {
			int winningClusterPair = -1;
			int maxSize = 0;

			for (int counter = 0; counter < this.innerCluster.getClusterCount(); counter++) {
				HashSet<Integer> innerSet = this.innerCluster.getNodesInCluster(counter);
				HashSet<Integer> midSet = null;
				if (this.middleCluster.getClusterCount() > counter) {
					midSet = this.middleCluster.getNodesInCluster(counter);
				} else {
					midSet = new HashSet<Integer>();
				}

				innerSet.retainAll(this.edgeList.get(tAS));
				midSet.retainAll(this.edgeList.get(tAS));

				int sum = innerSet.size() + midSet.size();
				if (sum > maxSize) {
					maxSize = sum;
					winningClusterPair = counter;
				}
			}

			if (winningClusterPair != -1) {
				this.outterCluster.directlyLinkNodeToCluster(tAS, winningClusterPair);
			} else {
				filler.add(tAS);
			}
		}

		for (int tAS : filler) {
			this.outterCluster.directlyLinkNodeToCluster(tAS, this.outterCluster.getSmallestCluster());
		}
	}

	public Ring getOutterCluster() {
		return outterCluster;
	}

	public Ring getMiddleCluster() {
		return middleCluster;
	}

	public Ring getInnerCluster() {
		return innerCluster;
	}

	public HashMap<Integer, HashSet<Integer>> getEdgeList() {
		return this.edgeList;
	}

	public int getRank(int as) {
		return this.rankMap.get(as);
	}
}
