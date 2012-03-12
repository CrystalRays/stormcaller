package bgp.engine;

import java.util.*;

import bgp.messages.*;
import bgp.dataStructures.*;

/**
 * Using this guy as a temp bootstrapper to load routes local to the BGP peer.
 */
public class BGPLocalLoader {

	private List<CIDR> localNetworks;

	/**
	 * Creates a local loader with our internal networks.
	 * 
	 * @param localNetworks
	 *            - a list of CIDR strings that are internal to us
	 */
	public BGPLocalLoader(List<String> localNetworks) {
		this.localNetworks = new ArrayList<CIDR>(localNetworks.size());

		for (String tempString : localNetworks) {
			this.localNetworks.add(new CIDR(tempString));
		}
	}

	/**
	 * Builds a list of "updates" that the router will process.
	 * 
	 * @return - a list of updates for our internal networks
	 */
	public List<Update> createIGPUpdateList() {
		Route tempRoute;
		Update tempUpdate;
		List<Update> retList = new ArrayList<Update>(this.localNetworks.size());

		for (CIDR tempNetwork : this.localNetworks) {
			tempRoute = new Route(tempNetwork, Constants.IGP);
			tempUpdate = new Update(0, 0);
			tempUpdate.setAdvertised(tempRoute);
			retList.add(tempUpdate);
		}

		return retList;
	}
}
