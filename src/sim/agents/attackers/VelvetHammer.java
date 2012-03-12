package sim.agents.attackers;

import java.util.*;
import java.io.*;

import bgp.dataStructures.*;
import sim.agents.*;
import sim.event.AttackEvent;
import sim.event.SimEvent;
import sim.logging.SimLogger;
import sim.network.dataObjects.*;

public class VelvetHammer extends BotMaster {

	protected HashMap<Integer, AS> asMap;
	protected HashMap<Integer, Router> routerMap;
	protected HashMap<Link, Double> loadMap;
	protected boolean ranNetworkProbe;

	/*
	 * Vars used for time slicing
	 */
	private HashMap<Integer, List<TrafficFlow>> asGroupMap = null;
	private HashMap<Link, List<TrafficFlow>> mainGroupMap = null;
	private HashMap<Link, List<TrafficFlow>> alternateGroupMap = null;
	private HashMap<Link, Link> attackBuddies = null;
	private SlicingQueue schedule = null;
	private int sliceCount = -1;

	private boolean weightLinksBySize;
	private boolean weightLinksByTime;
	private boolean smartWeighting;
	private boolean heavyLogging;
	protected boolean timeSlicing;
	private int depthOfSearch;
	private double overflowSize;

	public static final String HEAVY_LOGGING = "heavy logging";
	public static final String WEIGHT_SIZE = "weight by size";
	public static final String SMART_WEIGHTING = "smart weighting";
	public static final String WEIGHT_TIME = "weight by time";
	public static final String TIME_SLICING = "time slicing";
	public static final String DEPTH_OF_SEARCH = "depth of search";
	public static final String OVERFLOW = "overflow";
	public static final String SLICE_COUNT = "slice count";

	public VelvetHammer(HashMap<Integer, AS> incAsMap, HashMap<Integer, Router> incRouterMap,
			TrafficAccountant trafficMgmt, String configFile) throws IOException {
		super(incAsMap, trafficMgmt, configFile);
		this.validateReqParams();

		this.asMap = incAsMap;
		this.routerMap = incRouterMap;
		this.mainGroupMap = new HashMap<Link, List<TrafficFlow>>();
		this.asGroupMap = new HashMap<Integer, List<TrafficFlow>>();
		this.alternateGroupMap = new HashMap<Link, List<TrafficFlow>>();
		this.attackBuddies = new HashMap<Link, Link>();
		this.loadMap = null;
		this.ranNetworkProbe = false;

		this.heavyLogging = this.configs.getBooleanValue(VelvetHammer.HEAVY_LOGGING);
		this.weightLinksBySize = this.configs.getBooleanValue(VelvetHammer.WEIGHT_SIZE);
		this.weightLinksByTime = this.configs.getBooleanValue(VelvetHammer.WEIGHT_TIME);
		this.smartWeighting = this.configs.getBooleanValue(VelvetHammer.SMART_WEIGHTING);
		this.timeSlicing = this.configs.getBooleanValue(VelvetHammer.TIME_SLICING);
		this.depthOfSearch = Integer.parseInt(this.configs.getValue(VelvetHammer.DEPTH_OF_SEARCH));
		this.overflowSize = Double.parseDouble(this.configs.getValue(VelvetHammer.OVERFLOW));

		if (this.timeSlicing) {
			this.sliceCount = Integer.parseInt(this.configs.getValue(VelvetHammer.SLICE_COUNT));
		}
	}

	/**
	 * Validates that all velvet hammer params exist
	 */
	private void validateReqParams() {
		Set<String> reqParams = new HashSet<String>();

		reqParams.add(VelvetHammer.HEAVY_LOGGING);
		reqParams.add(VelvetHammer.WEIGHT_SIZE);
		reqParams.add(VelvetHammer.WEIGHT_TIME);
		reqParams.add(VelvetHammer.SMART_WEIGHTING);
		reqParams.add(VelvetHammer.TIME_SLICING);
		reqParams.add(VelvetHammer.DEPTH_OF_SEARCH);
		reqParams.add(VelvetHammer.OVERFLOW);

		try {
			this.configs.validateAddtionalRequiredParams(reqParams);

			if (this.timeSlicing) {
				reqParams.clear();
				reqParams.add(VelvetHammer.SLICE_COUNT);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	/**
	 * Posts the initial probe event in the queue
	 */
	public void runInitialSetup() {
		this.theDriver.postEvent(new SimEvent(SimEvent.TIMEREXPIRE, Integer.parseInt(this.configs
				.getValue(BotMaster.SETUPTIME)), this));
	}

	/**
	 * Answer events
	 */
	public void giveEvent(SimEvent theEvent) {
		if (theEvent.getType() != SimEvent.TIMEREXPIRE) {
			System.err.println("non timer expire event to Velvet Hammer!");
			System.exit(-1);
		}

		if (!this.ranNetworkProbe) {
			this.probeNetwork();
			if (this.timeSlicing) {
				this.computeSlicingTasking();
				this.launchNextPulse();
			} else {
				List<TrafficFlow> attackFlows;
				attackFlows = this.computeAllOrNothingTasking(null);
				this.theDriver.postEvent(new AttackEvent(Integer.parseInt(this.configs.getValue(STARTTIME)),
						this.trafficMgmt, attackFlows, null));
			}
		} else if (this.timeSlicing) {
			System.err.println("velvet hammer isn't very adaptive yet...");
			//this.launchNextPulse();
		} else {
			System.err.println("too many events to Velvet Hammer that isn't time slicing");
		}
	}

	private void launchNextPulse() {
		Slice tempSlice = null;
		AttackEvent startEvent = null;
		AttackEvent stopEvent = null;
		while (this.schedule.hasMore()) {
			tempSlice = this.schedule.getNextSlice();
			startEvent = new AttackEvent(tempSlice.getStart(), this.trafficMgmt, this.asGroupMap.get(tempSlice
					.getSliceGroupID()), null);
			stopEvent = new AttackEvent(tempSlice.getEnd(), this.trafficMgmt, null, this.asGroupMap.get(tempSlice
					.getSliceGroupID()));
			this.theDriver.postEvent(startEvent);
			this.theDriver.postEvent(stopEvent);
		}
	}

	/**
	 * Takes into account various switches and computes the botnet's view of the
	 * network's link loads. This takes decisions about weighting into account.
	 */
	protected void probeNetwork() {
		HashMap<Link, Double> linkLoads = this.getLinkUsageCount();

		if (this.weightLinksBySize) {
			if (this.heavyLogging) {
				System.out.println("doing link weighting");
			}

			if (this.smartWeighting) {
				linkLoads = this.weightLinkUsagesIgnoreTrivial(linkLoads);
			} else {
				linkLoads = this.weightLinkUsageBySize(linkLoads);
			}
		}

		this.loadMap = linkLoads;

		if (this.heavyLogging) {
			try {
				BufferedWriter tempWriter = new BufferedWriter(new FileWriter("stormcaller/logs/loaddump.txt"));
				HashSet<Link> tempSet = new HashSet<Link>();
				while (tempSet.size() < this.loadMap.keySet().size()) {
					Link tempLink = this.getBiggestUntargetedLink(tempSet);
					tempWriter.write(tempLink.toString() + " : " + this.loadMap.get(tempLink) + "\n");
				}
				tempWriter.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Generates a mapping of links, and the number of times the link is
	 * traversed in all current routes in EITHER direction. This should be used
	 * to select the routes that will give you the most bang for your buck.
	 * 
	 * @return
	 */
	private HashMap<Link, Double> getLinkUsageCount() {
		HashMap<Link, Double> retMap = new HashMap<Link, Double>();
		int noPathCount = 0;

		/*
		 * Step through each router scanning it's paths to all networks
		 */
		for (int tProbePoint : this.routerMap.keySet()) {
			Router tRouter = this.routerMap.get(tProbePoint);
			for (int tProbeDest : this.asMap.keySet()) {
				/*
				 * Don't probe to ourself
				 */
				if (tProbeDest == tProbePoint) {
					continue;
				}

				/*
				 * Get the route, move on if we don't have one
				 */
				Route tRoute = tRouter.getRoute(this.asMap.get(tProbeDest).getAnyNetwork());
				if (tRoute == null) {
					noPathCount++;
					continue;
				}

				/*
				 * Walk through every step in the path (with the exception of
				 * the "0" hop at the end, add a count to each link we see on
				 * the walk
				 */
				int asPath[] = tRoute.getAsPath();
				int currentAS = tProbePoint;
				for (int loopCounter = 0; loopCounter < asPath.length - 1; loopCounter++) {
					Link tLink = this.asMap.get(currentAS).getLinkToNeighbor(asPath[loopCounter]);
					if (!retMap.containsKey(tLink)) {
						retMap.put(tLink, 0.0);
					}
					retMap.put(tLink, retMap.get(tLink) + 1.0);
					currentAS = asPath[loopCounter];
				}
			}
		}

		if (this.heavyLogging) {
			System.out.println("no path count is: " + noPathCount);
		}

		return retMap;
	}

	/**
	 * Weights links by the size. This does not ignore OC12 links, so it will
	 * tend to be strongly biased toward them...
	 * 
	 * @param linkUsages -
	 *            Map index by link that maps to link load
	 * @return - map index by link that maps to link load / bandwidth capacity
	 *         of link
	 */
	private HashMap<Link, Double> weightLinkUsageBySize(HashMap<Link, Double> linkUsages) {
		HashMap<Link, Double> retMap = new HashMap<Link, Double>();

		for (Link tLink : linkUsages.keySet()) {
			retMap.put(tLink, linkUsages.get(tLink) / tLink.getCapacity());
		}

		return retMap;
	}

	/**
	 * Weights links by size, but throws out OC12 links as they are usually
	 * really really tiny.
	 * 
	 * @param linkUsages
	 *            map indexed by link that maps to link load
	 * @return - map index by link that maps to link load /bandwidth of the
	 *         link, but only contains links larger then OC12
	 */
	private HashMap<Link, Double> weightLinkUsagesIgnoreTrivial(HashMap<Link, Double> linkUsages) {
		HashMap<Link, Double> retMap = new HashMap<Link, Double>();

		for (Link tLink : linkUsages.keySet()) {
			if (tLink.getCapacity() == Link.OC12) {
				continue;
			}

			retMap.put(tLink, linkUsages.get(tLink) / tLink.getCapacity());
		}

		return retMap;
	}

	//FIXME this needs to play around w/ the taskedLinks set since it includes links that were considered but not attacked...
	protected List<TrafficFlow> computeAllOrNothingTasking(Set<Link> taskedLinks) {
		int searchDepth = 0;
		List<TrafficFlow> attackFlows = new LinkedList<TrafficFlow>();
		if (taskedLinks == null) {
			taskedLinks = new HashSet<Link>();
		}
		HashMap<Integer, Integer> availibleBandwidth = new HashMap<Integer, Integer>();
		for (int tASN : this.capMap.keySet()) {
			availibleBandwidth.put(tASN, this.capMap.get(tASN));
		}

		HashMap<Link, Set<Integer>> attackGroupMap = new HashMap<Link, Set<Integer>>();
		HashSet<Link> lookedAtLinks = new HashSet<Link>();
		lookedAtLinks.addAll(taskedLinks);
		
		/*
		 * Write what links we are attacking to a file
		 */
		BufferedWriter fBuff = null;
		try{
			fBuff = new BufferedWriter(new FileWriter(SimLogger.DIR + "attack.txt"));
		}
		catch(IOException e){
			e.printStackTrace();
		}
		

		/*
		 * Loop looking for targets until we have less bandwidth then could be
		 * used to fill an OC48
		 * 
		 * TODO this should be configurable...
		 */
		while (searchDepth < this.depthOfSearch && this.computeBotMapBandwidth(availibleBandwidth) > Link.OC48) {
			Link nextMaxLink = this.getBiggestUntargetedLink(lookedAtLinks);
			searchDepth++;

			/*
			 * Figure out best direction to approach from, then do some sanity
			 * checks about size of flow, etc
			 */
			TaskingTuple bestDirOrders = this.getOptimalFirstHop(availibleBandwidth, nextMaxLink, taskedLinks);
			if (bestDirOrders.getMaxAttackVolume() < nextMaxLink.getCapacity() * 0.75) {
				/*
				 * We're too weak to actually attack this link, move on with
				 * life, no point in wasting bandwidth
				 */
				continue;
			}

			/*
			 * Compute amount of traffic to send, step through each bot,
			 * assigning their share of attack size
			 */
			double attackScaling = Math.min(this.overflowSize,
					((double) bestDirOrders.getMaxAttackVolume() / (double) nextMaxLink.getCapacity()));
			long attackAmount = Math.round(nextMaxLink.getCapacity() * attackScaling);
			taskedLinks.add(nextMaxLink);

			//XXX uncomment this for fractional tasking
			//double taskingScaling = (double) attackAmount / (double) bestDirOrders.getMaxAttackVolume();

			if (this.heavyLogging) {
				System.out.println("tasking to: " + nextMaxLink.toString() + " coverage: " + attackScaling + " damage " + this.loadMap.get(nextMaxLink));
				try {
					fBuff.write("tasking to: " + nextMaxLink.toString() + " coverage: " + attackScaling + " damage " + this.loadMap.get(nextMaxLink));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			int targetAS = bestDirOrders.getAttackDestination();
			CIDR targetNetwork = this.asMap.get(targetAS).getAnyNetwork();

			/*
			 * Does all or nothing assign, this keeps down flow overhead
			 */
			List<TrafficFlow> tempFlowList = new LinkedList<TrafficFlow>();
			Set<Integer> tempAttackerSet = new HashSet<Integer>();
			long assignedAmount = 0;
			for (int tASN : bestDirOrders.getMaxDirASes()) {
				long maxAssign = attackAmount - assignedAmount;
				int tempAssign = (int) Math.min(maxAssign, availibleBandwidth.get(tASN));
				TrafficFlow tempFlow = new TrafficFlow(tASN, targetAS, targetNetwork, tempAssign);
				attackFlows.add(tempFlow);
				availibleBandwidth.put(tASN, availibleBandwidth.get(tASN) - tempAssign);

				if (this.weightLinksByTime) {
					tempFlowList.add(tempFlow);
					tempAttackerSet.add(tASN);
				}

				/*
				 * Check if we've assigned enough
				 */
				assignedAmount += tempAssign;
				if (assignedAmount >= attackAmount) {
					break;
				}
			}

			if (this.weightLinksByTime) {
				this.mainGroupMap.put(nextMaxLink, tempFlowList);
				attackGroupMap.put(nextMaxLink, tempAttackerSet);
			}

			//XXX uncomment this for fractional tasking, might be nicer, but takes a lot longer to sim
			//			for (int tASN : bestDirOrders.getMaxDirASes()) {
			//				int asContribution = (int) Math.floor(availibleBandwidth.get(tASN) * taskingScaling);
			//				attackFlows.add(new TrafficFlow(tASN, targetAS, targetNetwork, asContribution));
			//				availibleBandwidth.put(tASN, availibleBandwidth.get(tASN) - asContribution);
			//			}
		}
		try {
			fBuff.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		/*
		 * If we're logging spit out how deep our search was, how much bandwidth
		 * is left, etc
		 */
		if (this.heavyLogging) {
			System.out.println("done tasking for all or nothing");
			System.out
					.println("tasked set size: " + taskedLinks.size() + " (search depth: " + this.depthOfSearch + ")");
			System.out.println("unused bandwidth: " + this.computeBotMapBandwidth(availibleBandwidth) + " (out of: "
					+ this.computeBotMapBandwidth(this.capMap) + ")");
		}

		//TODO I want these bots adaptive before we sleep tonight...
		if (this.weightLinksByTime) {
			for (Link tTarget : attackGroupMap.keySet()) {
				lookedAtLinks.clear();
				lookedAtLinks.addAll(taskedLinks);
				HashMap<Integer, Integer> groupBandwidth = new HashMap<Integer, Integer>();
				for (int tAttacker : attackGroupMap.get(tTarget)) {
					groupBandwidth.put(tAttacker, this.capMap.get(tAttacker));
				}

				int searchCounter = 0;
				while (searchCounter < this.depthOfSearch) {
					searchCounter++;

					Link possTarget = this.getBiggestUntargetedLink(lookedAtLinks);
					TaskingTuple possTasking = this.getOptimalFirstHop(groupBandwidth, possTarget, taskedLinks);
					if (possTasking.getMaxAttackVolume() < possTarget.getCapacity() * 0.75) {
						/*
						 * We're too weak to actually attack this link, move on
						 * with life, no point in wasting bandwidth
						 */
						continue;
					}

					double attackScaling = Math.min(this.overflowSize,
							((double) possTasking.getMaxAttackVolume() / (double) possTarget.getCapacity()));
					long attackAmount = Math.round(possTarget.getCapacity() * attackScaling);
					taskedLinks.add(possTarget);

					int targetAS = possTasking.getAttackDestination();
					CIDR targetNetwork = this.asMap.get(targetAS).getAnyNetwork();

					if (this.heavyLogging) {
						System.out.println("alt to: " + possTarget.toString() + " coverage: " + attackScaling
								+ " search took " + searchCounter + " link dmg " + this.loadMap.get(possTarget));
					}

					List<TrafficFlow> tempFlowList = new LinkedList<TrafficFlow>();
					long assignedAmount = 0;
					for (int tASN : possTasking.getMaxDirASes()) {
						long maxAssign = attackAmount - assignedAmount;
						int tempAssign = (int) Math.min(maxAssign, groupBandwidth.get(tASN));
						TrafficFlow tempFlow = new TrafficFlow(tASN, targetAS, targetNetwork, tempAssign);
						tempFlowList.add(tempFlow);

						/*
						 * Check if we've assigned enough
						 */
						assignedAmount += tempAssign;
						if (assignedAmount >= attackAmount) {
							break;
						}
					}
					this.alternateGroupMap.put(possTarget, tempFlowList);
					this.attackBuddies.put(tTarget, possTarget);
					this.attackBuddies.put(possTarget, tTarget);

					break;
				}
			}
		}

		return attackFlows;
	}

	protected void computeSlicingTasking() {

		/*
		 * Computes the slice targets.
		 */
		HashSet<Link> usedSet = new HashSet<Link>();
		for (int counter = 0; counter < this.sliceCount; counter++) {
			List<TrafficFlow> tempFlows = this.computeAllOrNothingTasking(usedSet);
			this.asGroupMap.put(counter, tempFlows);
		}

		/*
		 * Schedule the slices. Right now this is a little ad-hoc w/ timing.
		 */
		this.schedule = new SlicingQueue(0, Integer.parseInt(this.configs.getValue(BotMaster.STARTTIME)));
		for (int counter = 0; counter < this.sliceCount * 6; counter++) {
			int id = counter % this.sliceCount;
			this.schedule.placeNewSliceGroup(id);
		}

		/*
		 * TODO So this is a huge todo, we need to figure out how many sets of
		 * targets we have...this is of course dependent on how many slices we
		 * can fit into a given time frame, and how big our time frame is. The
		 * current number is going to be a mighty 12 sets, here is the logic for
		 * it. It takes 180 secs to kill a router then it takes 60 secs for it
		 * to bounce back up. We want to give the router some amount of
		 * breathing room to generate updates for us, and to fill it's table, so
		 * that way we can then proceed to rip it out from under it. Right now
		 * we're going to give it 60 seconds. This is enough time to deal with
		 * 20k messages, should be enough time. So looking at a dump of
		 * scheduling slices, we quickly see that the next slice to start after
		 * 300 seconds is slice 13, so 12 slices it is...this is going to need a
		 * LOT of thinking
		 */

		/*
		 * TODO (el huge number two...) so we are going to pre-compute who can
		 * reach what where, but this is NO promise of future paths. In the
		 * future we should really look at computing at each pulse who is
		 * sending where. If we see a lot of good results at the start of an
		 * attack and then everything
		 */
	}

	/**
	 * Computes the bandwidth in a given <int, int> map. Currently used to
	 * report either the total amount of bandwidth in the system or the total
	 * avail bandwidth.
	 * 
	 * @param availBandwidth -
	 *            the map of bandwidths indexed by ASN
	 * @return - the sum of all bandwidths in the given map
	 */
	private long computeBotMapBandwidth(HashMap<Integer, Integer> availBandwidth) {
		long retValue = 0;

		for (Integer tASN : availBandwidth.keySet()) {
			retValue += availBandwidth.get(tASN);
		}

		return retValue;
	}

	/**
	 * TODO doc DOES update usedLinks
	 * 
	 * @param usedLinks
	 * @return
	 */
	private Link getBiggestUntargetedLink(Set<Link> usedLinks) {
		Link maxLink = null;
		double maxWeight = 0.0;

		for (Link tLink : this.loadMap.keySet()) {
			if (usedLinks.contains(tLink)) {
				continue;
			}

			if (this.loadMap.get(tLink) > maxWeight) {
				maxLink = tLink;
				maxWeight = this.loadMap.get(tLink);
			}
		}
		usedLinks.add(maxLink);

		return maxLink;
	}

	/**
	 * Magical wish granting function that determines which direction has the
	 * most bandwidth to approach from
	 * 
	 * @param availBandwidth
	 * @param targetedLink
	 * @return
	 */
	private TaskingTuple getOptimalFirstHop(HashMap<Integer, Integer> availBandwidth, Link targetedLink,
			Set<Link> forbidenLinks) {
		long lhsSum = 0;
		long rhsSum = 0;
		Set<Integer> lhsSet = new HashSet<Integer>();
		Set<Integer> rhsSet = new HashSet<Integer>();

		int lhs = targetedLink.getASes()[0].getASNumber();
		int rhs = targetedLink.getASes()[1].getASNumber();
		CIDR lhsNet = this.asMap.get(lhs).getAnyNetwork();
		CIDR rhsNet = this.asMap.get(rhs).getAnyNetwork();

		/*
		 * Step through everyone, adding the bandwidth to each tasking set the
		 * node belongs to
		 */
		for (int tASN : availBandwidth.keySet()) {
			if (availBandwidth.get(tASN) <= 0) {
				continue;
			}

			/*
			 * Get the actual routes
			 */
			Router tRouter = this.routerMap.get(tASN);
			Route toLhsRoute = tRouter.getRoute(lhsNet);
			Route toRhsRoute = tRouter.getRoute(rhsNet);

			/*
			 * Check if we have a route, and then see if we go from rhs on our
			 * way to lhs, since they are directly connected it would HAVE to be
			 * the hop before dest
			 */
			if (toLhsRoute != null) {
				int[] asHops = toLhsRoute.getAsPath();

				/*
				 * it's us, move on
				 */
				if (asHops.length == 1) {
					//do nothing
				}
				/*
				 * check if we're the rhs, if so then count it
				 */
				else if (asHops.length == 2) {
					if (tASN == rhs) {
						if (!this.crossesBadLink(tASN, lhs, forbidenLinks)) {
							rhsSet.add(tASN);
							rhsSum += availBandwidth.get(tASN);
						}
					}
				} else if (asHops[asHops.length - 3] == rhs) {
					if (!this.crossesBadLink(tASN, lhs, forbidenLinks)) {
						rhsSet.add(tASN);
						rhsSum += availBandwidth.get(tASN);
					}
				}
			}
			//TODO copy and paste code blocks....
			if (toRhsRoute != null) {
				int[] asHops = toRhsRoute.getAsPath();

				/*
				 * it's us, move on
				 */
				if (asHops.length == 1) {
					//do nothing
				}
				/*
				 * check if we're the rhs, if so then count it
				 */
				else if (asHops.length == 2) {
					if (tASN == lhs) {
						if (!this.crossesBadLink(tASN, rhs, forbidenLinks)) {
							lhsSet.add(tASN);
							lhsSum += availBandwidth.get(tASN);
						}
					}
				} else if (asHops[asHops.length - 3] == lhs) {
					if (!this.crossesBadLink(tASN, rhs, forbidenLinks)) {
						lhsSet.add(tASN);
						lhsSum += availBandwidth.get(tASN);
					}
				}
			}
		}

		/*
		 * TODO ok this, is VERY FUBAR right now with lhs and rhs being thrown
		 * around way way too much, the direction currently returned is the
		 * destination AS for traffic to be used when building attack flows,
		 * this should be cleaned up at some point plox
		 */
		TaskingTuple optimal;
		if (lhsSum > rhsSum) {
			optimal = new TaskingTuple(rhs, lhsSum, lhsSet);
		} else {
			optimal = new TaskingTuple(lhs, rhsSum, rhsSet);
		}

		return optimal;
	}

	private boolean crossesBadLink(int src, int dst, Set<Link> badLinks) {
		int currentAS = src;

		Router tempRouter = this.routerMap.get(currentAS);
		Route tempRoute = null;
		Link tempLink = null;
		while (currentAS != dst) {
			tempRoute = tempRouter.getRoute(this.asMap.get(dst).getAnyNetwork());
			if (tempRoute == null) {
				return false;
			}

			tempLink = this.asMap.get(currentAS).getLinkToNeighbor(tempRoute.getNextHop());
			if (badLinks.contains(tempLink)) {
				return true;
			}

			currentAS = tempRoute.getNextHop();
			tempRouter = this.routerMap.get(currentAS);
		}

		return false;
	}

	public void notifySessionFail(Link failedLink, int time) {
		if (this.weightLinksByTime) {
			if (this.attackBuddies.containsKey(failedLink)) {
				List<TrafficFlow> removeSet = null;
				List<TrafficFlow> addSet = null;

				if (this.mainGroupMap.containsKey(failedLink)) {
					removeSet = this.mainGroupMap.get(failedLink);
					addSet = this.alternateGroupMap.get(this.attackBuddies.get(failedLink));
					System.out.println("switching to alt");
				} else {
					removeSet = this.alternateGroupMap.get(failedLink);
					addSet = this.mainGroupMap.get(this.attackBuddies.get(failedLink));
					System.out.println("switching to main");
				}

				this.theDriver.postEvent(new AttackEvent(time, this.trafficMgmt, addSet, removeSet));
			}
		}
	}
}
