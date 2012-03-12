package sim.network.assembly;

import java.util.*;

import bgp.dataStructures.CIDR;
import sim.network.dataObjects.*;

/**
 * Class used to create AS objects for simulation run. This class is basically
 * in charge of creating a topology that is as close to that of the current
 * Internet as we can get. Ignore the long name, named that way at the start of
 * the project when there were going to be different Builders based on topology
 * ideas.
 * 
 * To use this class, just create an instance of it, then call the
 * connectGraph(int) function, it will spit out your map of ASes indexed by ASN.
 * 
 */
public class SimpleTieredBuilder {

	/**
	 * The number of ASes we want to generate, set by constructor param.
	 */
	private int size;

	/**
	 * The number of those ASes that are T1. We generate this inside the class.
	 */
	private int t1Count;

	/**
	 * The number of those ASes that are T2. We generate this inside the class.
	 */
	private int t2Count;

	/**
	 * The number of those ASes that are T3. We generate this inside the class.
	 */
	private int t3Count;

	/**
	 * The set of local networks we've handed out. The function that hands out
	 * networks is called once for each AS, so we need this to be a class level
	 * var so we don't get multi-homed IP blocks...
	 */
	private HashSet<CIDR> distributedNetworks;

	/**
	 * Random number gen used to get some randomness, should use the seed set
	 * down below so we can get multiple runs with the same network.
	 */
	private Random rng;

	/*
	 * Default distributions between T1/T2/T3
	 */
	public static final double DEFAULT_T1 = 0.1;
	public static final double DEFAULT_T2 = 0.3;
	public static final double DEFAULT_T3 = 0.6;

	/*
	 * Tier 1 network peer numbers, if not listed assume 0.
	 */
	public static final int T1_MAX_CUST = 9;
	public static final int T1_MIN_CUST = 7;
	public static final int T1_MAX_PEER = 4;
	public static final int T1_MIN_PEER = 3;

	/*
	 * Tier 2 network peer numbers, if not listed assume 0.
	 */
	public static final int T2_MAX_CUST = 5;
	public static final int T2_MIN_CUST = 3;
	public static final int T2_MAX_PROV = 3;
	public static final int T2_MIN_PROV = 2;

	/*
	 * Tier 3 network peer numbers, if not listed assume 0.
	 */
	public static final int T3_MAX_PROV = 3;
	public static final int T3_MIN_PROV = 2;

	/**
	 * Leftover of when I might have had different auction methods, here now
	 * because there isn't nothing to gain from pulling it out (code is a bit
	 * uglier, but no chance in functionality) besides I might actually do new
	 * methods of auctioning slots in the future.
	 */
	public static final int AUCTION_BLIND_AUTOACCEPT = 1;

	/**
	 * Random seed used to ensure predicatable randomness
	 */
	private static final int SEED = 10191983;

	/**
	 * Creates a network with the given size using default T1, T2, T3 distros.
	 * 
	 * @param size
	 *            - the number of ASes in the network
	 */
	public SimpleTieredBuilder(int size) {
		this.size = size;
		if (!this.buildCounts(SimpleTieredBuilder.DEFAULT_T1, SimpleTieredBuilder.DEFAULT_T2,
				SimpleTieredBuilder.DEFAULT_T3)) {
			System.err.println("Error creating build counts with default set.");
			System.exit(-1);
		}

		this.rng = new Random(SimpleTieredBuilder.SEED);
		this.distributedNetworks = new HashSet<CIDR>();
	}

	/**
	 * Creates a network with the given size and distribution between T1, T2,
	 * and T3 ASes.
	 * 
	 * @param size
	 * @param t1Percent
	 * @param t2Percent
	 * @param t3Percent
	 */
	public SimpleTieredBuilder(int size, double t1Percent, double t2Percent, double t3Percent) {
		this.size = size;
		if (!this.buildCounts(t1Percent, t2Percent, t3Percent)) {
			System.err.println("Error creating build counts with given percentages.");
			System.exit(-1);
		}

		this.rng = new Random(SimpleTieredBuilder.SEED);
		this.distributedNetworks = new HashSet<CIDR>();
	}

	/**
	 * Takes the network properties and creates a connected graph of it.
	 * 
	 * @param auctionMode
	 *            the method in which ASes are paired up to be neighbors
	 * @return a HashMap with AS numbers as keys and ASes as data objects, or
	 *         null if an error occurs
	 */
	public HashMap<Integer, AS> connectGraph(int auctionMode) {
		AS tempAS;

		AS[] t1ASes = new AS[this.t1Count];
		AS[] t2ASes = new AS[this.t2Count];
		AS[] t3ASes = new AS[this.t3Count];
		int[][] t1Slots;
		int[][] t2Slots;
		int[][] t3Slots;

		/*
		 * create the ASes, they will have no connections, dump them into the
		 * map and helper array default convention in numbering: T1 AS numbers
		 * will be single or double digit T2 AS numbers will be three digits,
		 * starting with 200 T3 AS numbers will be four digits, starting with
		 * 3000 this code also assigns the ASes an internal network as it
		 * creates the AS
		 */
		for (int count = 0; count < this.t1Count; count++) {
			tempAS = new AS(count + 1, AS.T1);
			this.attatchRandomLocalNetwork(tempAS);
			t1ASes[count] = tempAS;
		}
		for (int count = 0; count < this.t2Count; count++) {
			tempAS = new AS(count + 200, AS.T2);
			this.attatchRandomLocalNetwork(tempAS);
			t2ASes[count] = tempAS;
		}
		for (int count = 0; count < this.t3Count; count++) {
			tempAS = new AS(count + 3000, AS.T3);
			this.attatchRandomLocalNetwork(tempAS);
			t3ASes[count] = tempAS;
		}

		// generate slots for all tiers
		t1Slots = this.buildPossibleSlots(t1ASes, AS.T1);
		t2Slots = this.buildPossibleSlots(t2ASes, AS.T2);
		t3Slots = this.buildPossibleSlots(t3ASes, AS.T3);

		if (auctionMode == SimpleTieredBuilder.AUCTION_BLIND_AUTOACCEPT) {
			this.simpleConnectionPairings(t1ASes, t2ASes, t1Slots, t2Slots, AS.GRADE_CUST, AS.GRADE_PROV);
			this.simpleConnectionPairings(t1ASes, t1ASes, t1Slots, t1Slots, AS.GRADE_PEER, AS.GRADE_PEER);
			this.simpleConnectionPairings(t2ASes, t3ASes, t2Slots, t3Slots, AS.GRADE_CUST, AS.GRADE_PROV);
			this.simpleConnectionPairings(t2ASes, t2ASes, t2Slots, t2Slots, AS.GRADE_PEER, AS.GRADE_PEER);
			this.simpleConnectionPairings(t1ASes, t3ASes, t1Slots, t3Slots, AS.GRADE_CUST, AS.GRADE_PROV);

			HashMap<Integer, AS> retMap = new HashMap<Integer, AS>();
			for (int counter = 0; counter < t1ASes.length; counter++) {
				retMap.put(t1ASes[counter].getASNumber(), t1ASes[counter]);
			}
			for (int counter = 0; counter < t2ASes.length; counter++) {
				retMap.put(t2ASes[counter].getASNumber(), t2ASes[counter]);
			}
			for (int counter = 0; counter < t3ASes.length; counter++) {
				retMap.put(t3ASes[counter].getASNumber(), t3ASes[counter]);
			}
			return retMap;
		}

		// we didn't match an auction mode, yell and return null
		System.err.println("Didn't find a correct auction mode in connector: " + auctionMode);
		return null;
	}

	/**
	 * Helper function that matches open connection slots between two groups of
	 * ASes. This function doesn't care about what level it is actually matching
	 * up at (you provide that in the params). This proceeds to look at all
	 * nodes that want connections (lhs) and tries to find matching connections
	 * in the other pool (rhs).
	 * 
	 * @param lhsASes
	 *            - one set of ASes looking for connections
	 * @param rhsASes
	 *            - the other set of ASes looking for connections, this can be
	 *            the same as LHS
	 * @param lhsSlots
	 *            - the open connection counts array for lhs
	 * @param rhsSlots
	 *            - the open connection counts array for rhs
	 * @param lhsLevel
	 *            - the "level" of connection the lhs ASes are trying to fill,
	 *            used to index into lhsSlots
	 * @param rhsLevel
	 *            - again the "level" of connection the rhs ASes are trying to
	 *            fill
	 */
	private void simpleConnectionPairings(AS[] lhsASes, AS[] rhsASes, int[][] lhsSlots, int[][] rhsSlots, int lhsLevel,
			int rhsLevel) {
		int rhsOffset, rhsPosition;
		Link tLink;
		
		int prev;
		int after;
		int delta = -1;
		
		while(delta < 0){
			prev = 0;
			after = 0;
			for(int slotCounter = 0; slotCounter < lhsASes.length; slotCounter++){
				prev += lhsSlots[lhsLevel][slotCounter];
			}

			for (int lhsStep = 0; lhsStep < lhsASes.length; lhsStep++) {

				/*
				 * generate a random starting place inside the rhs AS array and step
				 * through all of it looking for matches
				 */
				rhsOffset = this.rng.nextInt(rhsASes.length);
				for (int rhsStep = 0; rhsStep < rhsASes.length && lhsSlots[lhsLevel][lhsStep] > 0; rhsStep++) {
					rhsPosition = (rhsStep + rhsOffset) % rhsASes.length;

					/*
					 * ensure we don't create a circular link to ourself (when
					 * hunting for peers we search over the same tier of AS
					 */
					if (rhsASes[rhsPosition].getASNumber().equals(lhsASes[lhsStep].getASNumber())) {
						continue;
					}

					/*
					 * the AS on the rhs has a slot in the given level open and
					 * isn't currently a neighbor of the lhs AS, mate the two
					 */
					if (rhsSlots[rhsLevel][rhsPosition] > 0 && !lhsASes[lhsStep].isNeighbor(rhsASes[rhsPosition])) {

						// build their link and error check
						tLink = this.createNaiveTieredLink(lhsASes[lhsStep], rhsASes[rhsPosition]);
						if (tLink == null) {
							System.err.println("Failure to create link in simpleConnectionPairing!");
							System.exit(-1);
						}

						// add the connections between ASes, update avail slots
						lhsASes[lhsStep].addNeighbor(rhsASes[rhsPosition], tLink, lhsLevel);
						rhsASes[rhsPosition].addNeighbor(lhsASes[lhsStep], tLink, rhsLevel);
						lhsSlots[lhsLevel][lhsStep]--;
						rhsSlots[rhsLevel][rhsPosition]--;
						break;
					}
				}
			}
			
			for(int slotCounter = 0; slotCounter < lhsASes.length; slotCounter++){
				after += lhsSlots[lhsLevel][slotCounter];
			}
			delta = after - prev;
		}
	}

	/**
	 * Distributes the ASes between class buckets based on the given
	 * percentages. This is deterministic, it will faithfully dump nodes based
	 * only on the percentages, using the T3 (small) ASes as fudge room to
	 * ensure that the sum of ASes in each class bucket is the network size.
	 * 
	 * @param t1Percent
	 *            percentage of ASes that are T1 (large)
	 * @param t2Percent
	 *            percentage of ASes that are T2 (medium)
	 * @param t3Percent
	 *            percentage of ASes that are T3 (small)
	 * @return false if any error happens (percentages don't add to 100, node
	 *         counts don't match, empty buckets), true otherwise
	 */
	private boolean buildCounts(double t1Percent, double t2Percent, double t3Percent) {
		// check to ensure we're not above 100%
		if (t1Percent + t2Percent + t3Percent > 1) {
			return false;
		}

		// allocate to AS classes
		this.t1Count = (int) Math.round(this.size * t1Percent);
		this.t2Count = (int) Math.round(this.size * t2Percent);
		this.t3Count = (int) Math.round(this.size * t3Percent);

		// do to rounding errors we might be off by a node + or -, so fudge in
		// the T3 ASes
		if (this.t1Count + this.t2Count + this.t3Count > this.size) {
			this.t3Count--;
		} else if (this.t1Count + this.t2Count + this.t3Count < this.size) {
			this.t3Count++;
		}

		// if after fudging we're still not balanced yell about it and fail
		if (this.t1Count + this.t2Count + this.t3Count != this.size) {
			System.err.println("Counts not correct after adujust!");
			System.err.println("" + this.t1Count + " " + this.t2Count + " " + this.t3Count + " " + this.size + " "
					+ t1Percent + "/" + t2Percent + "/" + t3Percent);
			return false;
		}

		// all AS classes must be non-empty
		if (this.t1Count == 0 || this.t2Count == 0 || this.t3Count == 0) {
			System.err.println("one AS class was zero: " + this.t1Count + " " + this.t2Count + " " + this.t3Count);
			return false;
		}

		return true;
	}

	/**
	 * Generates the number of customers, peers, and clients that the ASes in a
	 * given tier are looking for.
	 * 
	 * @param asArray
	 *            the array of AS objects created in connectGraph
	 * @param tier
	 *            what tier the ASes belong to (affects distribution of
	 *            customer/peer/provider
	 * @return int array holding counts in the form int[AS.GRADE_CUST][AS]
	 *         (customers) int[AS.GRADE_PEER][AS] (peer) int[AS.GRADE_PROV][AS]
	 *         (providers)
	 */
	private int[][] buildPossibleSlots(AS[] asArray, int tier) {
		int[][] retArray = new int[3][asArray.length];

		int custFloor = 0, custCeil = 0;
		int peerFloor = 0, peerCeil = 0;
		int provFloor = 0, provCeil = 0;

		/*
		 * Setup the correct ceil and floor values for number of peers.
		 */
		if (tier == AS.T1) {
			custFloor = SimpleTieredBuilder.T1_MIN_CUST;
			custCeil = SimpleTieredBuilder.T1_MAX_CUST;
			peerFloor = SimpleTieredBuilder.T1_MIN_PEER;
			peerCeil = SimpleTieredBuilder.T1_MAX_PEER;
			provFloor = 0;
			provCeil = 0;
		} else if (tier == AS.T2) {
			custFloor = SimpleTieredBuilder.T2_MIN_CUST;
			custCeil = SimpleTieredBuilder.T2_MAX_CUST;
			peerFloor = 0;
			peerCeil = 0;
			provFloor = SimpleTieredBuilder.T2_MIN_PROV;
			provCeil = SimpleTieredBuilder.T2_MAX_PROV;
		} else if (tier == AS.T3) {
			custFloor = 0;
			custCeil = 0;
			peerFloor = 0;
			peerCeil = 0;
			provFloor = SimpleTieredBuilder.T3_MIN_PROV;
			provCeil = SimpleTieredBuilder.T3_MAX_PROV;
		} else {
			System.err.println("Unknown tier given to buildPossibleSlots!");
			System.exit(-1);
		}

		// step over the array of ASes, handing out numbers of customer, peer,
		// and client slots
		for (int counter = 0; counter < asArray.length; counter++) {
			if (custCeil - custFloor > 0) {
				retArray[AS.GRADE_CUST][counter] = this.rng.nextInt(custCeil - custFloor) + custFloor;
			} else {
				retArray[AS.GRADE_CUST][counter] = custFloor;
			}

			if (peerCeil - peerFloor > 0) {
				retArray[AS.GRADE_PEER][counter] = this.rng.nextInt(peerCeil - peerFloor) + peerFloor;
			} else {
				retArray[AS.GRADE_PEER][counter] = peerFloor;
			}

			if (provCeil - provFloor > 0) {
				retArray[AS.GRADE_PROV][counter] = this.rng.nextInt(provCeil - provFloor) + provFloor;
			} else {
				retArray[AS.GRADE_PROV][counter] = provFloor;
			}
		}

		return retArray;
	}

	/**
	 * Creates a tiered link between two ASes. T1-T1 Links are OC192 T1-T2 and
	 * T2-T2 Links are OC48 T3-Anything is OC12
	 * 
	 * @param lhs
	 *            the left hand AS we're linking
	 * @param rhs
	 *            the right hand AS we're linking
	 * @return a link connecting the two ASes with the given capacity
	 */
	private Link createNaiveTieredLink(AS lhs, AS rhs) {
		if (lhs.getTier() == AS.T1 || rhs.getTier() == AS.T1) {
			if (lhs.getTier() == rhs.getTier()) {
				return new Link(lhs, rhs, Link.OC192);
			} else if (lhs.getTier() == AS.T2 || rhs.getTier() == AS.T2) {
				return new Link(lhs, rhs, Link.OC48);
			} else {
				return new Link(lhs, rhs, Link.OC12);
			}
		} else {
			if (lhs.getTier() == rhs.getTier()) {
				return new Link(lhs, rhs, Link.OC48);
			} else {
				return new Link(lhs, rhs, Link.OC12);
			}
		}
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
