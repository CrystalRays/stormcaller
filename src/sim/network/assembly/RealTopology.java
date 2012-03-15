package sim.network.assembly;

import java.io.*;
import java.util.*;

import bgp.dataStructures.CIDR;

import sim.network.dataObjects.*;

public class RealTopology {

	private HashSet<CIDR> distributedNetworks;

	private HashMap<Integer, AS> asMap;

	private HashMap<Integer, HashSet<Integer>> peerMap;
	
	/**
	 * Map containing a set of CUSTOMERS of the KEY VALUE as
	 */
	private HashMap<Integer, HashSet<Integer>> customerMap;
	private HashMap<Integer, HashSet<Integer>> providerMap;

	private Random rng;

	private boolean printInfo;

	private int t3LinkSize = -1;
	private int t2t2LinkSize = -1;
	private int t2t1LinkSize = -1;
	private int t1t1LinkSize = -1;

	private static final double T2CUT = .3;

	private static final double T1CUT = .1;

	public static final String T3LINKPARAM = "t3 size";
	public static final String T2T2LINKPARAM = "t2t2 size";
	public static final String T2T1LINKPARAM = "t2t1 size";
	public static final String T1T1LINKPARAM = "t1t1 size";

	public static final String DEFAULT_AS_FILE = "stormcaller/conf/as_rel.txt";

	public static void main(String argv[]) throws IOException{
		RealTopology test = new RealTopology("/scratch/minerva/schuch/stormcaller/conf/as_rel.txt", true, "OC3", "OC48", "OC192", "OC768");

		test.validateConnected();
		test.dumpASIPToFile("/scratch/waterhouse/schuch/stormcaller/asToIP.txt");
	}

	public RealTopology(String asFile, boolean printInfo, String t3Size, String t2t2Size, String t2t1Size,
			String t1t1Size) {
		this.distributedNetworks = new HashSet<CIDR>();
		this.rng = new Random(10191983);

		/*
		 * Setup what link sizes are supose to be
		 */
		try {
			this.t3LinkSize = this.stringToLinkCap(t3Size);
			this.t2t2LinkSize = this.stringToLinkCap(t2t2Size);
			this.t2t1LinkSize = this.stringToLinkCap(t2t1Size);
			this.t1t1LinkSize = this.stringToLinkCap(t1t1Size);
		} catch (NullPointerException e) {
			throw new NullPointerException("one or more link capacities are not set in sim.conf");
		}

		this.printInfo = printInfo;
		try {
			this.loadASFile(asFile);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}

		/*
		 * current pruning policy
		 */
		this.purgeNoCustomer();
		this.purgeNoCustomer();

		/*
		 * Build the actual AS objects
		 */
		this.asMap = new HashMap<Integer, AS>();
		this.buildASes();
	}

	private int stringToLinkCap(String sizeStr) {
		if (sizeStr.equalsIgnoreCase("OC3")) {
			return Link.OC3;
		} else if (sizeStr.equalsIgnoreCase("OC12")) {
			return Link.OC12;
		} else if (sizeStr.equalsIgnoreCase("OC48")) {
			return Link.OC48;
		} else if (sizeStr.equalsIgnoreCase("OC192")) {
			return Link.OC192;
		} else if (sizeStr.equalsIgnoreCase("OC768")) {
			return Link.OC768;
		}

		return -1;
	}

	private void buildASes() {
		HashSet<Integer> all = this.buildUniqueSet();
		int t1Count = (int) Math.floor(RealTopology.T1CUT * all.size());
		int t2Count = (int) Math.floor(RealTopology.T2CUT * all.size());

		HashSet<Integer> t1 = new HashSet<Integer>();
		HashSet<Integer> t2 = new HashSet<Integer>();
		HashSet<Integer> t3 = new HashSet<Integer>();

		for (int counter = 0; counter < t1Count; counter++) {
			int maxDeg = 0;
			int maxAS = -1;
			for (int tAS : all) {
				if (this.getASDegree(tAS) > maxDeg) {
					maxDeg = this.getASDegree(tAS);
					maxAS = tAS;
				}
			}

			t1.add(maxAS);
			all.remove(maxAS);
		}
		for (int counter = 0; counter < t2Count; counter++) {
			int maxDeg = 0;
			int maxAS = -1;
			for (int tAS : all) {
				if (this.getASDegree(tAS) > maxDeg) {
					maxDeg = this.getASDegree(tAS);
					maxAS = tAS;
				}
			}

			t2.add(maxAS);
			all.remove(maxAS);
		}
		t3.addAll(all);

		/*
		 * Create the actual AS objects
		 */
		for (int tAS : t1) {
			this.asMap.put(tAS, new AS(tAS, AS.T1));
		}
		for (int tAS : t2) {
			this.asMap.put(tAS, new AS(tAS, AS.T2));
		}
		for (int tAS : t3) {
			this.asMap.put(tAS, new AS(tAS, AS.T3));
		}

		/*
		 * Give each a network
		 */
		for (int tAS : this.asMap.keySet()) {
			this.attatchRandomLocalNetwork(this.asMap.get(tAS));
		}

		/*
		 * Link each network
		 */
		for (int tAS : this.asMap.keySet()) {
			AS tASObject = this.asMap.get(tAS);
			if (this.peerMap.containsKey(tAS)) {
				for (int tPeer : this.peerMap.get(tAS)) {
					if (!this.asMap.get(tAS).isNeighbor(this.asMap.get(tPeer))) {
						Link tLink;
						if (tASObject.getTier() == AS.T3 || this.asMap.get(tPeer).getTier() == AS.T3) {
							tLink = new Link(tASObject, this.asMap.get(tPeer), this.t3LinkSize);
						} else if (tASObject.getTier() == AS.T2 || this.asMap.get(tPeer).getTier() == AS.T2) {
							/*
							 * We have a link that is either t2 - t2 or t2 - t1,
							 * as it for sure does not have a t3 as and it for
							 * sure does have a t2 as, figure out which
							 */
							if (tASObject.getTier() == AS.T1 || this.asMap.get(tPeer).getTier() == AS.T1) {
								tLink = new Link(tASObject, this.asMap.get(tPeer), this.t2t1LinkSize);
							} else {
								tLink = new Link(tASObject, this.asMap.get(tPeer), this.t2t2LinkSize);
							}
						} else {
							/*
							 * the link can only be a t1 - t1 link at this point
							 */
							tLink = new Link(tASObject, this.asMap.get(tPeer), this.t1t1LinkSize);
						}
						tASObject.addPeer(this.asMap.get(tPeer), tLink);
						this.asMap.get(tPeer).addPeer(tASObject, tLink);
					}
				}
			}
			if (this.customerMap.containsKey(tAS)) {
				for (int tCust : this.customerMap.get(tAS)) {
					if (!this.asMap.get(tAS).isNeighbor(this.asMap.get(tCust))) {
						Link tLink;
						if (tASObject.getTier() == AS.T3 || this.asMap.get(tCust).getTier() == AS.T3) {
							tLink = new Link(tASObject, this.asMap.get(tCust), this.t3LinkSize);
						} else if (tASObject.getTier() == AS.T2 || this.asMap.get(tCust).getTier() == AS.T2) {
							/*
							 * We have a link that is either t2 - t2 or t2 - t1,
							 * as it for sure does not have a t3 as and it for
							 * sure does have a t2 as, figure out which
							 */
							if (tASObject.getTier() == AS.T1 || this.asMap.get(tCust).getTier() == AS.T1) {
								tLink = new Link(tASObject, this.asMap.get(tCust), this.t2t1LinkSize);
							} else {
								tLink = new Link(tASObject, this.asMap.get(tCust), this.t2t2LinkSize);
							}
						} else {
							/*
							 * the link can only be a t1 - t1 link at this point
							 */
							tLink = new Link(tASObject, this.asMap.get(tCust), this.t1t1LinkSize);
						}
						tASObject.addCustomer(this.asMap.get(tCust), tLink);
						this.asMap.get(tCust).addProvider(tASObject, tLink);
					}
				}
			}
			if (this.providerMap.containsKey(tAS)) {
				for (int tProv : this.providerMap.get(tAS)) {
					if (!this.asMap.get(tAS).isNeighbor(this.asMap.get(tProv))) {
						Link tLink;
						if (tASObject.getTier() == AS.T3 || this.asMap.get(tProv).getTier() == AS.T3) {
							tLink = new Link(tASObject, this.asMap.get(tProv), this.t3LinkSize);
						} else if (tASObject.getTier() == AS.T2 || this.asMap.get(tProv).getTier() == AS.T2) {
							/*
							 * We have a link that is either t2 - t2 or t2 - t1,
							 * as it for sure does not have a t3 as and it for
							 * sure does have a t2 as, figure out which
							 */
							if (tASObject.getTier() == AS.T1 || this.asMap.get(tProv).getTier() == AS.T1) {
								tLink = new Link(tASObject, this.asMap.get(tProv), this.t2t1LinkSize);
							} else {
								tLink = new Link(tASObject, this.asMap.get(tProv), this.t2t2LinkSize);
							}
						} else {
							/*
							 * the link can only be a t1 - t1 link at this point
							 */
							tLink = new Link(tASObject, this.asMap.get(tProv), this.t1t1LinkSize);
						}
						tASObject.addProvider(this.asMap.get(tProv), tLink);
						this.asMap.get(tProv).addCustomer(tASObject, tLink);
					}
				}
			}
		}
	}

	private void loadASFile(String dataFile) throws IOException {
		BufferedReader relBuff = new BufferedReader(new FileReader(dataFile));

		this.customerMap = new HashMap<Integer, HashSet<Integer>>();
		this.peerMap = new HashMap<Integer, HashSet<Integer>>();
		this.providerMap = new HashMap<Integer, HashSet<Integer>>();

		while (relBuff.ready()) {
			/*
			 * Read the line, ignore comments or blank lines
			 */
			String poll = relBuff.readLine().trim();
			if (poll.length() == 0 || poll.substring(0, 1).equals("#")) {
				continue;
			}

			/*
			 * Pull out the tokens
			 */
			StringTokenizer tokens = new StringTokenizer(poll, "|");
			int first = Integer.parseInt(tokens.nextToken().trim());
			int second = Integer.parseInt(tokens.nextToken().trim());
			int rel = Integer.parseInt(tokens.nextToken().trim());

			/*
			 * Dump into the correct relationship array
			 */
			if (rel == 0) {
				if (!this.peerMap.containsKey(first)) {
					this.peerMap.put(first, new HashSet<Integer>());
				}
				if(!this.peerMap.containsKey(second)){
					this.peerMap.put(second, new HashSet<Integer>());
				}
				this.peerMap.get(first).add(second);
				this.peerMap.get(second).add(first);
			} else if (rel == 1) {
				if (!this.providerMap.containsKey(first)) {
					this.providerMap.put(first, new HashSet<Integer>());
				}
				if(!this.customerMap.containsKey(second)){
					this.customerMap.put(second, new HashSet<Integer>());
				}
				this.providerMap.get(first).add(second);
				this.customerMap.get(second).add(first);
			} else if (rel == -1) {
				if (!this.customerMap.containsKey(first)) {
					this.customerMap.put(first, new HashSet<Integer>());
				}
				if(!this.providerMap.containsKey(second)){
					this.providerMap.put(second, new HashSet<Integer>());
				}
				this.customerMap.get(first).add(second);
				this.providerMap.get(second).add(first);
			}
		}
		relBuff.close();

		if (this.printInfo) {
			System.out.println("opening size is: " + this.countUniqueASes());
		}

	}

	public void validateConnected() {
		HashSet<Integer> all = this.buildUniqueSet();
		HashSet<Integer> traversed = new HashSet<Integer>();
		HashSet<Integer> neighbors;
		HashSet<Integer> newNeighbors = new HashSet<Integer>();

		int start = 0;
		for (int tAS : all) {
			start = tAS;
			break;
		}

		traversed.add(start);
		neighbors = this.getNeighbors(start);
		neighbors.removeAll(traversed);

		while (neighbors.size() > 0) {
			newNeighbors = new HashSet<Integer>();
			traversed.addAll(neighbors);

			for (int tAS : neighbors) {
				newNeighbors.addAll(this.getNeighbors(tAS));
			}
			neighbors = newNeighbors;
			neighbors.removeAll(traversed);
		}

		if (traversed.size() == all.size()) {
			System.out.println("connected");
		} else {
			System.out.println("not connected " + traversed.size() + "/" + all.size());
		}
	}
	
	public void dumpASIPToFile(String fileName) throws IOException{
		BufferedWriter outBuff = new BufferedWriter(new FileWriter(fileName));
		for(int tASN: this.asMap.keySet()){
			outBuff.write("" + tASN + " " + this.asMap.get(tASN).getAnyNetwork().toString() + "\n");
		}
		outBuff.close();
	}

	private HashSet<Integer> getNeighbors(int as) {
		HashSet<Integer> retSet = new HashSet<Integer>();

		if (this.peerMap.containsKey(as)) {
			retSet.addAll(this.peerMap.get(as));
		}
		if (this.customerMap.containsKey(as)) {
			retSet.addAll(this.customerMap.get(as));
		}
		if (this.providerMap.containsKey(as)) {
			retSet.addAll(this.providerMap.get(as));
		}

		return retSet;
	}

	private void trimASByDegree(int minDegree) {
		HashSet<Integer> all = this.buildUniqueSet();
		HashSet<Integer> remove = new HashSet<Integer>();

		for (int tAS : all) {
			if (this.getASDegree(tAS) < minDegree) {
				remove.add(tAS);
			}
		}

		for (int tAS : remove) {
			this.purgeAS(tAS);
		}

		if (this.printInfo) {
			System.out.println("after trimming to min degree: " + minDegree + " we have " + this.countUniqueASes());
		}
	}

	private void trimASByHighOrderDegree(int minDegree) {
		HashSet<Integer> all = this.buildUniqueSet();
		HashSet<Integer> remove = new HashSet<Integer>();

		for (int tAS : all) {
			if (this.getASHighOrderDegree(tAS) < minDegree) {
				remove.add(tAS);
			}
		}

		for (int tAS : remove) {
			this.purgeAS(tAS);
		}

		if (this.printInfo) {
			System.out.println("after trimming to min high order degree: " + minDegree + " we have "
					+ this.countUniqueASes());
		}
	}

	private void purgeAS(int asn) {
		if (this.peerMap.containsKey(asn)) {
			for (int tPeer : this.peerMap.get(asn)) {
				this.peerMap.get(tPeer).remove(asn);
				if (this.peerMap.get(tPeer).size() == 0) {
					this.peerMap.remove(tPeer);
				}
			}
			this.peerMap.remove(asn);
		}
		if (this.customerMap.containsKey(asn)) {
			for (int tCust : this.customerMap.get(asn)) {
				this.providerMap.get(tCust).remove(asn);
				if (this.providerMap.get(tCust).size() == 0) {
					this.providerMap.remove(tCust);
				}
			}
			this.customerMap.remove(asn);
		}
		if (this.providerMap.containsKey(asn)) {
			for (int tProv : this.providerMap.get(asn)) {
				this.customerMap.get(tProv).remove(asn);
				if (this.customerMap.get(tProv).size() == 0) {
					this.customerMap.remove(tProv);
				}
			}
			this.providerMap.remove(asn);
		}
	}

	private void purgeNoCustomer() {
		HashSet<Integer> all = this.buildUniqueSet();
		all.removeAll(this.customerMap.keySet());

		for (int tAS : all) {
			this.purgeAS(tAS);
		}

		if (this.printInfo) {
			System.out.println("after purging non-providers we have: " + this.countUniqueASes());
		}
	}

	private int countUniqueASes() {
		return this.buildUniqueSet().size();
	}

	private HashSet<Integer> buildUniqueSet() {
		HashSet<Integer> all = new HashSet<Integer>();
		all.addAll(this.peerMap.keySet());
		all.addAll(this.providerMap.keySet());
		all.addAll(this.customerMap.keySet());
		return all;
	}

	private int getASDegree(int as) {
		int count = 0;

		if (this.peerMap.containsKey(as)) {
			count += this.peerMap.get(as).size();
		}
		if (this.customerMap.containsKey(as)) {
			count += this.customerMap.get(as).size();
		}
		if (this.providerMap.containsKey(as)) {
			count += this.providerMap.get(as).size();
		}

		return count;
	}

	private int getASHighOrderDegree(int as) {
		int count = 0;

		if (this.peerMap.containsKey(as)) {
			count += this.peerMap.get(as).size();
		}
		if (this.providerMap.containsKey(as)) {
			count += this.providerMap.get(as).size();
		}

		return count;
	}

	public HashMap<Integer, AS> getASMap() {
		return this.asMap;
	}

	/**
	 * Give an AS a random internal network. The network is guaranteed to be
	 * unique across our test network.
	 * 
	 * @param incAS
	 *            - the AS that needs a local network
	 */
	private void attatchRandomLocalNetwork(AS incAS) {
		CIDR addCIDR = null;
		int first, second, third;

		while (addCIDR == null) {

			// generate a random CIDR for a /24
			first = this.rng.nextInt(253) + 1;
			second = this.rng.nextInt(256);
			third = this.rng.nextInt(256);
			addCIDR = new CIDR("" + first + "." + second + "." + third + ".0/24");

			/*
			 * make sure we have not already handed that network out, if so MAKE
			 * IT AGAIN, otherwise note that we've handed it out, and, well,
			 * hand it out
			 */if (this.distributedNetworks.contains(addCIDR)) {
				addCIDR = null;
			} else {
				this.distributedNetworks.add(addCIDR);
			}
		}

		incAS.addLocalNetwork(addCIDR);
	}

}
