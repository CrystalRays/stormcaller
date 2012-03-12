package bgp.engine;

import java.util.*;

import bgp.dataStructures.CIDR;
import bgp.dataStructures.Route;

/**
 * Class that manages export specifications for a BGP Daemon.
 * 
 */
public class BGPExportSpec {

	/**
	 * The ASN of the network I live in
	 */
	private int myASN;

	/**
	 * Set of networks we ALWAYS export out, these should be networks of our
	 * customers
	 */
	private Set<CIDR> whitleListNetworks;

	/**
	 * Set of ASes whose routes we always export.
	 */
	private Set<Integer> whiteListHosts;

	/**
	 * Set of ASes we don't want to export routes to, these are providers we
	 * don't want to provide transit for.
	 */
	private Set<Integer> blackListHosts;

	/**
	 * Object in charge of applying export policies to outgoing routes.
	 * 
	 * @param myASN -
	 *            the ASN of the network I live in
	 * @param configStrings -
	 *            list of config string lines from our router's IOS file
	 */
	public BGPExportSpec(int myASN, List<String> configStrings) {
		this.myASN = myASN;

		this.whitleListNetworks = new HashSet<CIDR>();
		this.whiteListHosts = new HashSet<Integer>();
		this.blackListHosts = new HashSet<Integer>();

		this.parseConfigFiles(configStrings);
	}

	/**
	 * Parses config strings, and sets up data strcutures so export programming
	 * is obeyed.
	 * 
	 * @param configStrings -
	 *            set of export programming strings from our router's IOS file
	 */
	private void parseConfigFiles(List<String> configStrings) {
		StringTokenizer cmdToken;
		String first, second;

		for (String tString : configStrings) {
			cmdToken = new StringTokenizer(tString, " ");
			first = cmdToken.nextToken().toLowerCase();

			if (first.equals("whitelist")) {
				second = cmdToken.nextToken().toLowerCase();
				if (second.equals("network")) {
					this.whitleListNetworks.add(new CIDR(cmdToken.nextToken()));
				} else if (second.equals("as")) {
					this.whiteListHosts.add(Integer.parseInt(cmdToken.nextToken()));
				} else {
					System.err.println("bad config line in export spec: " + tString);
				}
			} else if (first.equals("blacklist")) {
				this.blackListHosts.add(Integer.parseInt(cmdToken.nextToken()));
			} else {
				System.err.println("bad config line in export spec: " + tString);
			}
		}
	}

	/**
	 * Runs export specifications on a single route for a collection of
	 * networks. This function actually applies export speicifcation, so it
	 * should be called by any function looking to do exporting. The routes
	 * returned in the Map are actually copies of the initial route, this is
	 * important since we will change attributes.
	 * 
	 * @param inRoute -
	 *            the route to export
	 * @param asnSet -
	 *            a list of ASes we might want to export to
	 * @return - a map containing routes to export indexed by the ASN we'll send
	 *         them to
	 */
	public HashMap<Integer, Route> runExportSpec(Route inRoute, Set<Integer> asnSet) {
		HashMap<Integer, Route> retMap = new HashMap<Integer, Route>();
		int mirrorStop = inRoute.getNextHop();

		// this strips out srcId, and local pref, since those are
		// non-transitive
		Route outRoute = new Route(inRoute.getNlri(), inRoute.getOrigin(), inRoute.getAsPath(), 1);
		outRoute.extendPath(this.myASN);
		outRoute.setSrcId(this.myASN);
		
		for (int tASN : asnSet) {
			// check if the host to export to is blacklisted and the network or
			// host isn't whitelisted
			if (this.blackListHosts.contains(tASN)) {
				if (!(this.whitleListNetworks.contains(inRoute.getNlri()) || this.whiteListHosts.contains(inRoute
						.getNextHop()))) {
					continue;
				}
			}
			
			if(mirrorStop == tASN){
				continue;
			}

			retMap.put(tASN, outRoute);
		}

		return retMap;
	}

	/**
	 * Helper method that is used to apply export specs for a list of routes for
	 * a lone host.
	 * 
	 * @param routes -
	 *            the list of routes we might export
	 * @param asn -
	 *            the AS we might be exporting to
	 * @return - a list of copies of route objects that we can export out
	 */
	public List<Route> runExportSpecLoneAS(List<Route> routes, int asn) {
		HashMap<Integer, Route> dummyMap;
		List<Route> returnList = new LinkedList<Route>();
		Set<Integer> dummyASNSet = new HashSet<Integer>();
		dummyASNSet.add(asn);

		for (Route tRoute : routes) {
			dummyMap = this.runExportSpec(tRoute, dummyASNSet);
			if (dummyMap.containsKey(asn)) {
				returnList.add(dummyMap.get(asn));
			}
		}

		return returnList;
	}
}
