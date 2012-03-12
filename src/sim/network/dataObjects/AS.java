package sim.network.dataObjects;

import java.security.InvalidParameterException;
import java.util.*;

import bgp.dataStructures.CIDR;

/**
 * Class used to store a variety of information about an AS. This class is
 * little more then a data container, but a damn handy data container.
 * 
 */
public class AS {

	/**
	 * Various "grades" of AS, T1 is larger then T2 is larger then T3
	 */
	public static final int T1 = 1;

	public static final int T2 = 2;

	public static final int T3 = 3;

	/**
	 * Enum that represents differing AS relations
	 */
	public static final int GRADE_CUST = 0;

	public static final int GRADE_PEER = 1;

	public static final int GRADE_PROV = 2;

	/**
	 * The ASN for this AS
	 */
	private Integer asNumber;

	/**
	 * The tier of the AS
	 */
	private int tier;

	/**
	 * Maps of all neighboring ASes split up by our relation to them and the
	 * links to them. i.e. Our providers will be in the provider map, etc
	 */
	private HashMap<AS, Link> customers;

	private HashMap<AS, Link> peers;

	private HashMap<AS, Link> providers;

	/**
	 * Set of local networks.
	 */
	private Set<CIDR> localNetworks;

	/**
	 * Create an AS of the given tier with the given ASN. It will have no local
	 * networks of neighbors until we give it those later. Those sets/maps are
	 * safe to access, they'll just be empty.
	 * 
	 * @param asNumber -
	 *            the ASN of the AS
	 * @param tier -
	 *            the tier of this AS (should be one of the values from above)
	 */
	public AS(int asNumber, int tier) {
		this.asNumber = asNumber;
		this.tier = tier;

		// create these data structs so they are ready to recieve
		this.customers = new HashMap<AS, Link>();
		this.peers = new HashMap<AS, Link>();
		this.providers = new HashMap<AS, Link>();
		this.localNetworks = new HashSet<CIDR>();
	}

	/**
	 * hashCode overridden to simply return the hash code of the AS number. This
	 * actually just returns the AS number, but needed for Hash sets & tables.
	 */
	public int hashCode() {
		return this.asNumber.hashCode();
	}

	/**
	 * overridden to return <AS Number> : (tier: <tier>)
	 */
	public String toString() {
		return "" + this.asNumber + " (tier = " + this.tier + ")";
	}

	/**
	 * Dumps the neighbors of the AS into a human readable display.
	 * 
	 * @return - a multiline formated string listing all neighbors and local
	 *         networks
	 */
	public String dumpNeighbors() {
		String retString = "Customers: \n";
		for (AS tAS : this.customers.keySet()) {
			retString += "  " + this.customers.get(tAS) + "\n";
		}
		retString += "Peers: \n";
		for (AS tAS : this.peers.keySet()) {
			retString += "  " + this.peers.get(tAS) + "\n";
		}
		retString += "Providers: \n";
		for (AS tAS : this.providers.keySet()) {
			retString += "  " + this.providers.get(tAS) + "\n";
		}
		retString += "Local Networks:\n";
		for (CIDR tNetwork : this.localNetworks) {
			retString += " " + tNetwork + "\n";
		}
		return retString;
	}

	/**
	 * Creates a list containing all neighboring ASes regardless of relation.
	 * 
	 * @return a list containing every AS we're connected to
	 */
	public List<AS> getAllNeighbors() {
		List<AS> retList = new LinkedList<AS>();

		retList.addAll(this.customers.keySet());
		retList.addAll(this.peers.keySet());
		retList.addAll(this.providers.keySet());

		return retList;
	}
	
	public Set<AS> getCustomerCone(){
		HashSet<AS> retSet = new HashSet<AS>();
		HashSet<AS> toCheckSet = new HashSet<AS>();
		toCheckSet.add(this);
		
		while(toCheckSet.size() > 0){
			HashSet<AS> remSet = new HashSet<AS>();
			HashSet<AS> addSet = new HashSet<AS>();
			
			for(AS tAS: toCheckSet){
				Set<AS> tCusts = tAS.getCustomers();
				for(AS tCustomer: tCusts){
					if(!retSet.contains(tCustomer) && !tCustomer.equals(this)){
						addSet.add(tCustomer);
					}
				}
				retSet.addAll(tCusts);
				remSet.add(tAS);
			}
			
			toCheckSet.addAll(addSet);
			toCheckSet.removeAll(remSet);
		}
		
		return retSet;
	}

	/**
	 * Getter for AS number.
	 * 
	 * @return the AS number
	 */
	public Integer getASNumber() {
		return this.asNumber;
	}

	/**
	 * Getter for AS tier;
	 * 
	 * @return the tier of the AS
	 */
	public int getTier() {
		return this.tier;
	}

	/**
	 * Adds the given neighbor to the given grade of connection. This function
	 * simply hands the AS and link off to the correct addXXXXX function.
	 * 
	 * @param incNeighbor
	 *            the potential new neighbor
	 * @param neighborLink
	 *            the link to the neighbor
	 * @param neighborGrade
	 *            the relationship between ASes (customer/peer/provider)
	 * @return false if the corrisponding addXXXX function has an error, true
	 *         otherwise
	 */
	public boolean addNeighbor(AS incNeighbor, Link neighborLink, int neighborGrade) {
		if (incNeighbor == null) {
			throw new NullPointerException("Null AS neighbor!");
		}
		if (neighborLink == null) {
			throw new NullPointerException("Null Link from neighbor!");
		}

		// hand off to correct function
		switch (neighborGrade) {
		case AS.GRADE_CUST:
			return this.addCustomer(incNeighbor, neighborLink);
		case AS.GRADE_PEER:
			return this.addPeer(incNeighbor, neighborLink);
		case AS.GRADE_PROV:
			return this.addProvider(incNeighbor, neighborLink);
		default:
			throw new InvalidParameterException("bad neighbor grade to add! " + neighborGrade);
		}
	}

	/**
	 * Adds the customer to the customer list with the given link if the
	 * customer is not already a neighbor.
	 * 
	 * @param incCustomer
	 *            the potential new customer
	 * @param customerLink
	 *            the link to the customer
	 * @return false if the customer is already a neighbor, true otherwise
	 */
	public boolean addCustomer(AS incCustomer, Link customerLink) {
		if (this.isNeighbor(incCustomer)) {
			return false;
		}

		this.customers.put(incCustomer, customerLink);
		return true;
	}

	/**
	 * Adds the peer to the peer list with the given link if the peer is not
	 * already a neighbor.
	 * 
	 * @param incPeer
	 *            the potenial new peer
	 * @param peerLink
	 *            the link to the peer
	 * @return false if the peer is already a neighbor, true otherwise
	 */
	public boolean addPeer(AS incPeer, Link peerLink) {
		if (this.isNeighbor(incPeer)) {
			return false;
		}

		this.peers.put(incPeer, peerLink);
		return true;
	}

	/**
	 * Adds the provider to the provider list with the given link if the
	 * provider is not already a neighbor.
	 * 
	 * @param incProvider
	 *            the potential new provider
	 * @param providerLink
	 *            the link to the provider
	 * @return false if the provider is already a neighbor, true otherwise
	 */
	public boolean addProvider(AS incProvider, Link providerLink) {
		if (this.isNeighbor(incProvider)) {
			return false;
		}

		this.providers.put(incProvider, providerLink);
		return true;
	}

	/**
	 * Fetches the link to the given neighbor.
	 * 
	 * @param neighbor
	 *            the neighboring AS we want a link to
	 * @return the link to the AS if it is
	 */
	public Link getLinkToNeighbor(AS neighbor) {
		if (!this.isNeighbor(neighbor)) {
			throw new NullPointerException("Can't get a link to a null neighbor: " + this.toString());
		}

		if (this.customers.containsKey(neighbor)) {
			return this.customers.get(neighbor);
		} else if (this.peers.containsKey(neighbor)) {
			return this.peers.get(neighbor);
		} else if (this.providers.containsKey(neighbor)) {
			return this.providers.get(neighbor);
		}

		// should never get here since isNeighbor looks to see if the AS lives
		// in one of those
		// key sets, yell a lot
		throw new NullPointerException("Can't get a link to a neighbor that does not exist: " + this.toString() + " - "
				+ neighbor);
	}

	/**
	 * Gets the link to the as with the given ASN. This works no matter what
	 * type of relationship this AS has with the given AS. If this AS has no
	 * relationship with the given AS an excpetion is thrown.
	 * 
	 * @param asn -
	 *            the ASN of the AS we want the link to
	 * @return - the Link to the AS with the given ASN
	 */
	public Link getLinkToNeighbor(int asn) {
		for (AS tAS : this.providers.keySet()) {
			if (tAS.getASNumber() == asn) {
				return this.providers.get(tAS);
			}
		}
		for (AS tAS : this.customers.keySet()) {
			if (tAS.getASNumber() == asn) {
				return this.customers.get(tAS);
			}
		}
		for (AS tAS : this.peers.keySet()) {
			if (tAS.getASNumber() == asn) {
				return this.peers.get(tAS);
			}
		}

		throw new NullPointerException("Can't get a link to an asn that does not exist: " + this.toString() + " - "
				+ asn);
	}

	/**
	 * Returns the total degree of the AS, this degree is the sum of the
	 * customer, peer, and provider set.
	 * 
	 * @return the degree of the AS node
	 */
	public int getDegree() {
		return this.customers.keySet().size() + this.peers.keySet().size() + this.providers.keySet().size();
	}

	/**
	 * Returns if this AS and the given AS are adjacent in the AS graph. This
	 * function does not take the class of the relationship into account.
	 * 
	 * @param rhs
	 *            the potential neighbor AS
	 * @return true if the AS is adjacent, false otherwise
	 */
	public boolean isNeighbor(AS rhs) {
		return this.customers.containsKey(rhs) || this.peers.containsKey(rhs) || this.providers.containsKey(rhs);
	}

	/**
	 * Adds a local network to this AS.
	 * 
	 * @param inNet -
	 *            CIDR that represents the network
	 */
	public void addLocalNetwork(CIDR inNet) {
		this.localNetworks.add(inNet);
	}

	/**
	 * Gets local networks connected to this AS.
	 * 
	 * @return a set of CIDRs for local networks
	 */
	public Set<CIDR> getLocalNetworks() {
		return this.localNetworks;
	}
	
	public CIDR getAnyNetwork(){
		CIDR retNet = null;
		for(CIDR tNet: this.localNetworks){
			retNet = tNet;
		}
		return retNet;
	}

	/**
	 * Gets the set of peers for this AS.
	 * 
	 * @return - a set holding all peers of this AS
	 */
	public Set<AS> getPeers() {
		return this.peers.keySet();
	}

	/**
	 * Gets the set of providers for this AS.
	 * 
	 * @return - a set holding all providers of this AS
	 */
	public Set<AS> getProviders() {
		return this.providers.keySet();
	}

	/**
	 * Gets the set of customers for this AS.
	 * 
	 * @return - a set holding all customers of this AS
	 */
	public Set<AS> getCustomers() {
		return this.customers.keySet();
	}

	/**
	 * Just tests if our asn is the same, very lame, but works.
	 */
	public boolean equals(Object rhs) {
		AS asRHS = (AS) rhs;
		return this.asNumber == asRHS.asNumber;
	}
}
