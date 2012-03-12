package bgp.engine;

import java.util.*;

import bgp.messages.Constants;
import bgp.dataStructures.Route;

/**
 * Class used to handle import specifications.
 * 
 */
public class BGPImportSpec {

	/**
	 * The ASN the hosting BGP Daemon lives in.
	 */
	private int myASNumber;

	/**
	 * Map used to store local pref bindings based on the route's next hop ASN.
	 */
	private HashMap<Integer, Integer> locPrefMap;

	/**
	 * Object that handles the application of import specifications to incoming
	 * routes.
	 * 
	 * @param myASNumber -
	 *            the ASN of the network we live in
	 * @param configStrings -
	 *            the set of config strings from our router's IOS file that
	 *            configure us
	 */
	public BGPImportSpec(int myASNumber, List<String> configStrings) {
		this.myASNumber = myASNumber;
		this.locPrefMap = new HashMap<Integer, Integer>();

		this.parseConfig(configStrings);
	}

	/**
	 * Converts the config strings into import policies.
	 * 
	 * @param configStrings -
	 *            config strings from the IOS file that are for the import spec
	 */
	private void parseConfig(List<String> configStrings) {
		StringTokenizer cmdTokens;
		String first;

		for (String tString : configStrings) {
			cmdTokens = new StringTokenizer(tString, " ");
			first = cmdTokens.nextToken().toLowerCase();

			if (first.equals("aspref")) {
				this.locPrefMap.put(Integer.parseInt(cmdTokens.nextToken()), Integer.parseInt(cmdTokens.nextToken()));
			} else {
				System.err.println("bad import config line: " + tString);
			}
		}
	}

	/**
	 * Applies import specifications to an incoming Route. This function does a
	 * collection of standardized things, for example checking for loops,
	 * updating origin attributes, etc. Then open ended programming can be
	 * applied from import spec config strings, for example, adding local pref
	 * attributes.
	 * 
	 * @param inRoute -
	 *            the route we wish to run through import specs, this can be
	 *            null in some cases, in which case we should simply return null
	 * @return - the route after applying import specs, this can result in no
	 *         route being returned, in which case we return NULL
	 */
	public Route runImportSpec(Route inRoute) {
		if (inRoute == null) {
			return null;
		}

		// throw out loops
		if (this.isLoop(inRoute)) {
			return null;
		}

		// check if we're learning about this route from internal or external,
		// set origin accordingly
		if (inRoute.getNextHop() > 0 && inRoute.getNextHop() != this.myASNumber) {
			inRoute.setOrigin(Constants.EGP);
		}

		// update the local pref if we have a directive to assign local pref
		if (this.locPrefMap.containsKey(inRoute.getNextHop())) {
			inRoute.setLocalPref(this.locPrefMap.get(inRoute.getNextHop()));
		}

		return inRoute;
	}

	/**
	 * Predicate that checks if the given route is a loop. This is done via
	 * scanning the route for any instances of our own ASN.
	 * 
	 * @param inRoute -
	 *            the route we want to check for loops
	 * @return - true if the route loops back on ourself, false otherwise
	 */
	private boolean isLoop(Route inRoute) {
		int path[] = inRoute.getAsPath();

		for (int counter = 0; counter < path.length; counter++) {
			if (path[counter] == this.myASNumber) {
				return true;
			}
		}

		return false;
	}
}
