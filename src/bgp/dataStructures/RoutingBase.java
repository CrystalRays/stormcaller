package bgp.dataStructures;

import java.util.*;

/**
 * Class that used as a router's routing base. One could call this a routing
 * table, but it's really not, more accurately it is a collection of routes,
 * only the local-RIB is a routing table in the traditional sense.
 * 
 */
public class RoutingBase {

	/**
	 * boolean flag controlling if this RIB allows for multiple routes to the
	 * same CIDR, if true then every BGP peer can have a route to a CIDR
	 */
	private boolean multipleRoutes;

	/**
	 * boolean flag controlling if the RIB will consider routes with different
	 * Intransitive attributes different, this will control when changes are
	 * Propagated to other RIBs or sent to other routers in Updates
	 */
	private boolean sensitiveToIntransitives;

	/**
	 * Stores routes with a key string that varies depending on if
	 * multipleRoutes are supported If multiple routes are supported the key is:
	 * the route's NLRI catted with the BGP peer ID If multiple routes are not
	 * supported the key is: the route's NLRI
	 */
	private HashMap<String, Route> routeTable;

	private HashMap<CIDR, HashSet<Integer>> nlriIndex;
	private HashMap<Integer, HashSet<CIDR>> asIndex;
	
	/**
	 * Constructor that builds an empty RoutingBase.
	 * 
	 * @param multipleRoutes
	 *            - flags if we will accept a route to each CIDR from each BGP
	 *            or just one route per CIDR
	 * @param sensitiveToIntransitives
	 *            - flags if we should report route change based on
	 *            in-transitive attributes (adj-in RIB and loc RIB)
	 */
	public RoutingBase(boolean multipleRoutes, boolean sensitiveToIntransitives) {
		this.multipleRoutes = multipleRoutes;
		this.sensitiveToIntransitives = sensitiveToIntransitives;
		this.routeTable = new HashMap<String, Route>();
		this.nlriIndex = new HashMap<CIDR, HashSet<Integer>>();
		this.asIndex = new HashMap<Integer, HashSet<CIDR>>();
	}
	
	public RoutingBase(String serialString){
		StringTokenizer bigTokens = new StringTokenizer(serialString, "%");
		String poll, subPoll, subsubPoll;
		CIDR netKey;
		int asKey;

		this.routeTable = new HashMap<String, Route>();
		this.nlriIndex = new HashMap<CIDR, HashSet<Integer>>();
		this.asIndex = new HashMap<Integer, HashSet<CIDR>>();
		
		poll = bigTokens.nextToken();
		this.multipleRoutes = (Integer.parseInt(poll) == 1);
		poll = bigTokens.nextToken();
		this.sensitiveToIntransitives = (Integer.parseInt(poll) == 1);
		
		poll = bigTokens.nextToken();
		StringTokenizer table = new StringTokenizer(poll, "@");
		while(table.hasMoreTokens()){
			subPoll = table.nextToken();
			if(subPoll.length() > 0){
				StringTokenizer entry = new StringTokenizer(subPoll, "#");
				this.routeTable.put(entry.nextToken(), new Route(entry.nextToken()));
			}
		}
		
		poll = bigTokens.nextToken();
		table = new StringTokenizer(poll, "@");
		while(table.hasMoreTokens()){
			subPoll = table.nextToken();
			if(subPoll.length() > 0){
				StringTokenizer entry = new StringTokenizer(subPoll, "#");
				netKey = new CIDR(entry.nextToken());
				this.nlriIndex.put(netKey, new HashSet<Integer>());
				if(entry.hasMoreTokens()){
					subsubPoll = entry.nextToken();
					entry = new StringTokenizer(subsubPoll, "$");
					while(entry.hasMoreTokens()){
						String gogo = entry.nextToken();
						if(gogo.length() > 0){
							this.nlriIndex.get(netKey).add(Integer.parseInt(gogo));
						}
					}
				}
			}
		}
		
		poll = bigTokens.nextToken();
		table = new StringTokenizer(poll, "@");
		while(table.hasMoreTokens()){
			subPoll = table.nextToken();
			if(subPoll.length() > 0){
				StringTokenizer entry = new StringTokenizer(subPoll, "#");
				asKey = Integer.parseInt(entry.nextToken());
				this.asIndex.put(asKey, new HashSet<CIDR>());
				if(entry.hasMoreTokens()){
					subsubPoll = entry.nextToken();
					entry = new StringTokenizer(subsubPoll, "$");
					while(entry.hasMoreTokens()){
						String gogo = entry.nextToken();
						if(gogo.length() > 0){
							this.asIndex.get(asKey).add(new CIDR(gogo));
						}
					}
				}
			}
		}
	}
	
	public String serialString(){
		StringBuilder retString = new StringBuilder();
		if(this.multipleRoutes){
			retString.append("1%");
		}
		else{
			retString.append("0%");
		}
		
		if(this.sensitiveToIntransitives){
			retString.append("1%");
		}
		else{
			retString.append("0%");
		}
		
		for(String key: this.routeTable.keySet()){
			retString.append(key + "#" + this.routeTable.get(key).serialString() + "@"); 
		}
		retString.append("%");
		
		for(CIDR key: this.nlriIndex.keySet()){
			retString.append(key + "#");
			for(int val: this.nlriIndex.get(key)){
				retString.append(val + "$");
			}
			retString.append("@");
		}
		
		retString.append("%");
		
		for(int key: this.asIndex.keySet()){
			retString.append(key + "#");
			for(CIDR val: this.asIndex.get(key)){
				retString.append(val.toString() + "$");
			}
			retString.append("@");
		}
		
		return retString.toString();
	}

	/**
	 * Creates a list containing a copy of every route stored in this RIB. Order
	 * is not specified.
	 * 
	 * @return - a list with a copy of all routes in this RoutingBase
	 */
	public List<Route> fetchWholeTable() {
		List<Route> fullList = new LinkedList<Route>();

		for (String tKey : this.routeTable.keySet()) {
			fullList.add(this.routeTable.get(tKey).copy());
		}

		return fullList;
	}

	/**
	 * Places the given route into the routing base, replacing any previous
	 * route. This will report updates differently depending on the
	 * sensitiveToIntransitive flag. See this flag for more details. This will
	 * replace any previous route stored for the given network.
	 * 
	 * @param inRoute
	 *            - the new route to be installed
	 * @return - true if no route previously existed or if a route is changed at
	 *         a sensitivity lvl set by RIB vars, false otherwise
	 */
	public boolean installRoute(Route inRoute) {
		String keyString;
		boolean replaceFlag;

		keyString = this.generateKeyString(inRoute.getNlri(), inRoute.getSrcId());
		replaceFlag = this.routeTable.containsKey(keyString)
				&& !(this.routeTable.get(keyString).equalsTransitiveAttr(inRoute)) && !this.sensitiveToIntransitives
				|| this.routeTable.containsKey(keyString) && !(this.routeTable.get(keyString).equals(inRoute))
				&& this.sensitiveToIntransitives || !this.routeTable.containsKey(keyString);
		this.routeTable.put(keyString, inRoute);
		if (!this.nlriIndex.containsKey(inRoute.getNlri())) {
			this.nlriIndex.put(inRoute.getNlri(), new HashSet<Integer>());
		}
		if (!this.asIndex.containsKey(inRoute.getSrcId())) {
			this.asIndex.put(inRoute.getSrcId(), new HashSet<CIDR>());
		}
		this.nlriIndex.get(inRoute.getNlri()).add(inRoute.getSrcId());
		this.asIndex.get(inRoute.getSrcId()).add(inRoute.getNlri());
		return replaceFlag;
	}

	public boolean installRoute(Route inRoute, int asn) {
		String keyString = this.generateKeyString(inRoute.getNlri(), asn);
		boolean replaceFlag;
		
		replaceFlag = this.routeTable.containsKey(keyString)
				&& !(this.routeTable.get(keyString).equalsTransitiveAttr(inRoute)) && !this.sensitiveToIntransitives
				|| this.routeTable.containsKey(keyString) && !(this.routeTable.get(keyString).equals(inRoute))
				&& this.sensitiveToIntransitives || !this.routeTable.containsKey(keyString);
		this.routeTable.put(keyString, inRoute);
		if (!this.nlriIndex.containsKey(inRoute.getNlri())) {
			this.nlriIndex.put(inRoute.getNlri(), new HashSet<Integer>());
		}
		if (!this.asIndex.containsKey(inRoute.getSrcId())) {
			this.asIndex.put(asn, new HashSet<CIDR>());
		}
		this.nlriIndex.get(inRoute.getNlri()).add(inRoute.getSrcId());
		this.asIndex.get(asn).add(inRoute.getNlri());
		return replaceFlag;
	}

	/**
	 * Withdraws the route to a given network. This works if the routing base
	 * supports multiple routes, if multiple routes are not supported this call
	 * is equivilent to a call to withdrawRoute(CIDR).
	 * 
	 * @param network
	 *            - the network to withdraw
	 * @param srcId
	 *            - the BGP peer that reported this route to us
	 * @return - true if the network existed in the routing base prior to
	 *         withdrawl, false otherwise
	 */
	public boolean withdrawRoute(CIDR network, int srcId) {
		String keyString;

		keyString = this.generateKeyString(network, srcId);
		if (!this.nlriIndex.containsKey(network)) {
			this.nlriIndex.put(network, new HashSet<Integer>());
		}
		if (!this.asIndex.containsKey(srcId)) {
			this.asIndex.put(srcId, new HashSet<CIDR>());
		}
		this.nlriIndex.get(network).remove(srcId);
		this.asIndex.get(srcId).remove(network);
		return this.withdrawRouteInternal(keyString);
	}

	/**
	 * Withdraws the route to a given network. This only works if the routing
	 * base does not support multiple routes, otherwise the withdrawRoute(CIDR,
	 * int) function must be called.
	 * 
	 * @param network
	 *            - the network to withdraw
	 * @return - true if the network existed in the routing base prior to
	 *         withdrawl, false otherwise
	 */
	public boolean withdrawRoute(CIDR network) {
		String keyString;

		keyString = this.generateKeyString(network);
		return this.withdrawRouteInternal(keyString);
	}

	/**
	 * Internal function to withdraw a route that matches the given indexing
	 * string.
	 * 
	 * @param keyString
	 *            - string generated from the call to generateKeyString with the
	 *            correct args
	 * @return - true if the network existed in the routing base prior to
	 *         withdrawl, false otherwise
	 */
	private boolean withdrawRouteInternal(String keyString) {
		boolean inTable;

		inTable = this.routeTable.containsKey(keyString);
		if (inTable) {
			this.routeTable.remove(keyString);
		}
		return inTable;
	}

	/**
	 * Fetches a list of all routes that EXACTLY match this nlri. This function
	 * does not return routes that are subsets of the given route, nor does it
	 * return any routes whose nlri contains the given nlri.
	 * 
	 * @param nlri
	 *            - the network we want routes for
	 * @return - a list of routes that reach the given nlri, if no routes match
	 *         an empty list is returned
	 */
	public List<Route> fetchRoutesForNLRI(CIDR nlri) {
		List<Route> returnList;
		String key;

		// create an empty list, if nothing else return it
		returnList = new LinkedList<Route>();

		// step through looking for all matching indexes that are the same NLRI
		if (this.multipleRoutes) {
			HashSet<Integer> indexInt = this.nlriIndex.get(nlri);
			
			if(indexInt == null){
				return null;
			}
			
			for (Integer tAS : indexInt) {
				key = this.generateKeyString(nlri, tAS);
				returnList.add(this.routeTable.get(key));
			}
		} else {
			// if we don't support multiple routes then there is only one,
			// assuming there is one
			key = this.generateKeyString(nlri);
			if (this.routeTable.containsKey(key)) {
				returnList.add(this.routeTable.get(key));
			}
		}

		return returnList;
	}

	/**
	 * Fetch all routes in a multi-supported routing table that are from/for a
	 * given ASN.
	 * 
	 * @param asn
	 *            - the ASN of the AS whose routes we're interested in
	 * @return - a list of all routes the are linked to the given ASN
	 */
	public List<Route> fetchRoutesForAS(int asn) {
		List<Route> returnList;

		//if we don't support multiple routes this makes no sense really, throw an exception
		if (!this.multipleRoutes) {
			throw new NullPointerException("Attempted to fetch all routes for an AS in a non-multi route table");
		}

		returnList = new LinkedList<Route>();
		HashSet<CIDR> indexNet = this.asIndex.get(asn);
		
		if(indexNet == null){
			return null;
		}
		
		String key;
		for (CIDR tNet : indexNet) {
			key = this.generateKeyString(tNet, asn);
			returnList.add(this.routeTable.get(key));
		}

		return returnList;
	}

	/**
	 * Fetches the route a given network, that we index by ASN. This is only
	 * usable if the RIB that this is being called on allows multiple routes.
	 * 
	 * @param network
	 *            - the network we want the route for
	 * @param asn
	 *            - the AS that it is indexed by in multiple route support
	 * @return - the route indexed by the given CIDR/AS pair if we have one,
	 *         NULL otherwise
	 */
	public Route fetchRoute(CIDR network, int asn) {
		String keyString;

		keyString = this.generateKeyString(network, asn);
		return this.routeTable.get(keyString);
	}

	/**
	 * Fetches the route to a given network. This is only usable if the RIB does
	 * NOT allow multiple route support.
	 * 
	 * @param network
	 *            - the network we want the route for
	 * @return - the route indexed by the given CIDR if we have one, NULL
	 *         otherwise
	 */
	public Route fetchRoute(CIDR network) {
		String keyString;

		keyString = this.generateKeyString(network);
		return this.routeTable.get(keyString);
	}

	/**
	 * Generates correct indexing string for a given network and route source.
	 * Code that is indexing into the routing base should NOT generate this
	 * string itself, instead it should call this fucntion or
	 * generateKeyString(CIDR) in order to build this string.
	 * 
	 * @param network
	 *            - the network we want to index to
	 * @param srcId
	 *            - the bgp peer ID of the route, if multiple routes are not
	 *            supported this param will be ignored
	 * @return - CIDR:srcId if multiple routes are supported, CIDR if they are
	 *         not
	 */
	private String generateKeyString(CIDR network, int srcId) {
		if (this.multipleRoutes) {
			return network.toString() + ":" + srcId;
		} else {
			return network.toString();
		}
	}

	/**
	 * Generates correct indexing string for a given network Code that is
	 * indexing into the routing base should NOT generate this string itself,
	 * instead it should call this function or generateKeyString(CIDR, int).
	 * This function only functions if multiple routes are not supported by this
	 * Routing Base, an exception will be thrown if this is called and multiple
	 * routes are supported.
	 * 
	 * @param network
	 *            - the network we want to index to
	 * @return - CIDR
	 */
	private String generateKeyString(CIDR network) {
		if (this.multipleRoutes) {
			throw new NullPointerException("Used non-multiple route key string generator in a multi route RIB.");
		}

		return network.toString();
	}

	/**
	 * Dumps a status update for this routing base. This status update simply
	 * dumps logging strings for all routes in the routing base.
	 * 
	 * @return - a LONG multiline string showing all routes in the RIB
	 */
	public String dumpTable() {
		String returnString = "";

		for (String tKey : this.routeTable.keySet()) {
			returnString += this.routeTable.get(tKey) + "\n";
		}

		return returnString;
	}
}
