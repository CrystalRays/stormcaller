package bgp.engine;

import java.util.*;

import bgp.dataStructures.*;
import bgp.messages.*;
import bgp.messages.Error;
import sim.agents.Router;
import sim.logging.*;

public class BGPDaemon {

	/**
	 * The adj-in RIB, which holds all routes that are advertised to us POST
	 * import specifications. This RIB is sensitive to intransitives and
	 * supports multiple routes to the same network (so long as they come from
	 * unique hosts).
	 */
	private RoutingBase adjInRIB;

	/**
	 * The local RIB, this holds all routes that are considered "best" for every
	 * CIDR we have a route to. This is the RIB used to make routing decisions
	 * for the router. This RIB is sensitive to instransitvies and does NOT
	 * support multiple routes.
	 */
	private RoutingBase localRIB;

	/**
	 * The adj-out RIB, which holds all routes that we advertise to our peers
	 * POST export specifications. This RIB is in-sensitive to intransitive
	 * attributes and does support multiple routes.
	 */
	private RoutingBase adjOutRIB;

	/**
	 * The import specification driver for the Daemon.
	 */
	private BGPImportSpec importDriver;

	/**
	 * The export specification driver for the Daemon.
	 */
	private BGPExportSpec exportDriver;

	/**
	 * Mapping of ASN to local BGP peer ID for connected peers
	 */
	private HashMap<Integer, Integer> asToPeerMap;

	/**
	 * Stores what wall time each AS's current connection was created. This is a
	 * poor man's client TCP socket since we don't want messages from an old
	 * connection coming out and screwing us.
	 */
	private HashMap<Integer, Integer> asConTimeMap;

	/**
	 * Set of ASNs who have completed half of the two way handshake
	 */
	private HashSet<Integer> pendingPeers;

	/**
	 * Route ranker object, used to select routes for installation into the
	 * local-RIB
	 */
	private BGPRanker routeRanker;

	/**
	 * RNG generator for assigning BGP speaker peers
	 */
	private Random rand;

	/**
	 * The parent router this daemon is running in, the router we get updates
	 * from and push updates to.
	 */
	private Router router;

	/**
	 * Queue holding BGP messages we need to process.
	 */
	private PriorityQueue<BGPMessage> messageQueue;

	/**
	 * ASN this daemon is running in. In the future we might want to change this
	 * to a router ID if we have multiple eBGP speakers per AS.
	 */
	private int myASN;

	/**
	 * The current simulator time.
	 */
	private int wallTime;

	/**
	 * The amount of time (in ms) we wait before we dump a peer for inactivity.
	 */
	private int haltTimer;

	/**
	 * The amount of time (in ms) we will go before we send a keepalive. This is
	 * reset everytime we send some type of traffic.
	 */
	private int keepaliveTimer;

	private int dampenCheckTimer;

	/**
	 * The amount of time we must wait between successive advertisements for a
	 * prefix
	 */
	private int mrai;

	/**
	 * Map that stores when we've last heard from a peer. This is used by the
	 * halt timer.
	 */
	private HashMap<Integer, Integer> lastSeenMap;

	/**
	 * Map that stores the last time we've sent traffic to a host. This is used
	 * by the keep alive timer.
	 */
	private HashMap<Integer, Integer> keepAliveMap;

	/**
	 * Stores the last time we issued an update for a given CIDR
	 */
	private HashMap<String, Integer> advertisementAllowedMap;

	/**
	 * Small set used to ensure that we don't mark a route as dirty twice, this
	 * is used for MRAI code
	 */
	private HashSet<String> dirtyRoutes;

	/**
	 * List used to store routes that need to be advertised in the future
	 * because they changed inside a MRAI window, the list will be ordered with
	 * the head being the first to expire, this stops a user from having to step
	 * through the whole list
	 */
	private Queue<String> mraiPendingRoutes;

	private boolean rfdFlag;

	private DampeningTable rfdTable;

	private HashMap<String, Update> dampenedUpdates;

	/**
	 * Map used to keep track of when we should reconnect to a peer.
	 */
	private HashMap<Integer, Integer> reconnectMap;

	/**
	 * The logger for this sim run.
	 */
	private SimLogger logger;

	/**
	 * The length of time we wait before attempting to reconnect to a peer
	 * automatically
	 */
	private static final int RECONNECT = 60000;

	private static final int RFDCHECK = 5000;

	/**
	 * Offset for random numbers used for peer IDs.
	 */
	private static final int RANDOFFSET = 0;

	/**
	 * Creates an instance of a BGP daemon ro run in the given router.
	 * 
	 * @param localRouteGen
	 *            - the iBGP route reflector used by this router
	 * @param myASN
	 *            - the ASN that router resides in
	 * @param router
	 *            - the router running this BGP daemon
	 * @param importStrings
	 *            - list of config strings for import specs
	 * @param exportStrings
	 *            - list of config strings for export specs
	 * @param keepAlive
	 *            - the value of the keep alive timer, in ms
	 * @param haltTimer
	 *            - the value of the halt timer, in ms
	 * @param logger
	 *            - the logger we're using
	 */
	public BGPDaemon(BGPLocalLoader localRouteGen, int myASN, Router router, List<String> importStrings,
			List<String> exportStrings, List<String> rfdStrings, int keepAlive, int haltTimer, int mrai,
			SimLogger logger) {
		List<Update> igpUpdates;

		// setup the vast bulk of this daemon
		this
				.initStructures(myASN, keepAlive, haltTimer, mrai, importStrings, exportStrings, rfdStrings, router,
						logger);

		// setup our RIBs correctly
		this.adjInRIB = new RoutingBase(true, true);
		this.localRIB = new RoutingBase(false, true);
		this.adjOutRIB = new RoutingBase(true, false);

		// fetch & process starting updates from route reflector
		igpUpdates = localRouteGen.createIGPUpdateList();
		for (Update tUpdate : igpUpdates) {
			this.processUpdate(tUpdate);
		}
	}

	public BGPDaemon(int myASN, Router router, List<String> importStrings, List<String> exportStrings,
			List<String> rfdStrings, int keepAlive, int haltTimer, int mrai, SimLogger logger, String serialString) {

		// setup the vast bulk of this daemon
		this
				.initStructures(myASN, keepAlive, haltTimer, mrai, importStrings, exportStrings, rfdStrings, router,
						logger);

		/*
		 * Let the serial file parsing begin!
		 */
		StringTokenizer topTokens = new StringTokenizer(serialString, "^");
		String poll = topTokens.nextToken();

		StringTokenizer midTokens = new StringTokenizer(poll, "@");
		while (midTokens.hasMoreTokens()) {
			String subPoll = midTokens.nextToken();
			if (subPoll.length() > 0) {
				StringTokenizer bottomTokens = new StringTokenizer(subPoll, "#");
				int asExt = Integer.parseInt(bottomTokens.nextToken());
				this.asToPeerMap.put(asExt, Integer.parseInt(bottomTokens.nextToken()));
				this.asConTimeMap.put(asExt, 0);
				this.lastSeenMap.put(asExt, 0);
				this.keepAliveMap.put(asExt, 0);
			}
		}

		poll = topTokens.nextToken();
		this.adjInRIB = new RoutingBase(poll);
		poll = topTokens.nextToken();
		this.localRIB = new RoutingBase(poll);
		poll = topTokens.nextToken();
		this.adjOutRIB = new RoutingBase(poll);
	}

	/**
	 * Initiates a large amount of the data structures the daemon will use.
	 * 
	 * @param myASN
	 *            - my AS number
	 * @param keepAlive
	 *            - value of the keep alive timer
	 * @param haltTimer
	 *            - value of the halt timer
	 * @param mrai
	 *            - value of the min route advertisement interval
	 * @param importStrings
	 *            - the list of import policy config strings
	 * @param exportStrings
	 *            - the list of export policy config strings
	 */
	private void initStructures(int myASN, int keepAlive, int haltTimer, int mrai, List<String> importStrings,
			List<String> exportStrings, List<String> rfdStrings, Router theRouter, SimLogger theLogger) {
		// setup logger, rng, remember our home router & ASN
		this.logger = theLogger;
		this.rand = new Random(myASN + BGPDaemon.RANDOFFSET);
		this.router = theRouter;
		this.myASN = myASN;

		// setup our connection managers
		this.messageQueue = new PriorityQueue<BGPMessage>();
		this.asToPeerMap = new HashMap<Integer, Integer>();
		this.asConTimeMap = new HashMap<Integer, Integer>();
		this.reconnectMap = new HashMap<Integer, Integer>();
		this.pendingPeers = new HashSet<Integer>();

		// setup timing vars
		this.lastSeenMap = new HashMap<Integer, Integer>();
		this.keepAliveMap = new HashMap<Integer, Integer>();
		this.advertisementAllowedMap = new HashMap<String, Integer>();
		this.dirtyRoutes = new HashSet<String>();
		this.mraiPendingRoutes = new LinkedList<String>();
		this.wallTime = 0;
		this.keepaliveTimer = keepAlive;
		this.haltTimer = haltTimer;
		this.mrai = mrai;

		// setup our import/export specs & ranker
		this.importDriver = new BGPImportSpec(this.myASN, importStrings);
		this.exportDriver = new BGPExportSpec(this.myASN, exportStrings);
		this.buildRFDData(rfdStrings);
		this.routeRanker = new BGPRanker();
	}

	private void buildRFDData(List<String> rfdStrings) {
		if (rfdStrings.size() == 0) {
			this.rfdFlag = false;
			this.rfdTable = null;
			this.dampenedUpdates = null;
		} else {
			this.rfdFlag = true;
			this.rfdTable = new DampeningTable(rfdStrings);
			this.dampenedUpdates = new HashMap<String, Update>();
			this.dampenCheckTimer = 0;
		}
	}

	public String serialString() {
		StringBuilder retString = new StringBuilder();

		for (int asExt : this.asToPeerMap.keySet()) {
			retString.append(asExt);
			retString.append("#");
			retString.append(this.asToPeerMap.get(asExt));
			retString.append("@");
		}
		retString.append("^");
		retString.append(this.adjInRIB.serialString());
		retString.append("^");
		retString.append(this.localRIB.serialString());
		retString.append("^");
		retString.append(this.adjOutRIB.serialString());

		return retString.toString();
	}

	/**
	 * Fetches a status report for this BGPDaemon. Ostensibly this dumps the
	 * status for the router.
	 * 
	 * @return - a very long multi line string that gives the full status of the
	 *         BGP daemon
	 */
	public String getStatus() {
		String retString;
		retString = "*******************\n";
		retString += "Report for AS: " + this.myASN + "\n";

		// dump RIBs
		retString += "adj-In RIB:\n";
		retString += this.adjInRIB.dumpTable() + "\n";
		retString += "local RIB:\n";
		retString += this.localRIB.dumpTable() + "\n";
		retString += "adj-Out RIB:\n";
		retString += this.adjOutRIB.dumpTable() + "\n";

		// dump connected/pending peers
		retString += "connected ASNs:\n";
		for (int tASN : this.asToPeerMap.keySet()) {
			retString += "\t" + tASN + " - " + this.asToPeerMap.get(tASN) + "\n";
		}
		retString += "pending peers:\n\t";
		for (int tASN : this.pendingPeers) {
			retString += tASN + ",";
		}
		retString = retString.substring(0, retString.length() - 1) + "\n";

		retString += "pending BGP message count: " + this.messageQueue.size() + "\n";

		retString += "*******************\n";
		return retString;
	}

	/**
	 * Function called to handle an inbound Update to the BGP daemon. This
	 * function is kinda huge, more then a little complicated, and the heart and
	 * soul of BGP. Stated simply: DON'T TOUCH THIS UNLESS YOU KNOW WHAT THE
	 * FUCK YOU'RE DOING.....
	 * 
	 * @param inUpdate
	 *            - the update to process
	 */
	private void processUpdate(Update inUpdate) {
		Route advertisedRoute, newBestRoute;
		Update revokeUpdate;
		HashMap<Integer, Route> exportAdditions;
		boolean sendUpdate;
		Set<CIDR> networksToRecalc, networksToWithdraw;
		Set<Route> routesToExport;
		List<Route> routesToNetwork;

		// create a few empty sets of CIDRs, we'll add CIDRs we need to
		// re-evaulate to the set as they pop up
		networksToRecalc = new HashSet<CIDR>();
		networksToWithdraw = new HashSet<CIDR>();
		routesToExport = new HashSet<Route>();

		// convert from asn to bgp peer number - skip this for internal RR
		// (srcId will be 0, works since ASN 0 does not exist)
		if (inUpdate.getSrcId() != 0) {
			if (this.asToPeerMap.containsKey(inUpdate.getSrcId())) {
				inUpdate.setSrcId(this.asToPeerMap.get(inUpdate.getSrcId()));
			} else {
				/*
				 * we just recieved an Update from a BGP speaker we're not
				 * connected to, simply drop it
				 */
				System.err.println("updated from UNKNOWN PEER me: " + this.myASN + " : " + inUpdate.getSrcId());
				return;
			}
		}

		// withdraw all networks the update tells us to withdraw
		for (CIDR tWithdrawnNetwork : inUpdate.getWithdraws()) {
			this.adjInRIB.withdrawRoute(tWithdrawnNetwork, inUpdate.getSrcId());
			networksToRecalc.add(tWithdrawnNetwork);
		}

		/*
		 * grab the advertised route, if it's not null then even if our import
		 * spec tells us to ignore the network we should remove the previous one
		 * this host gave to us (if it exists), this prevents stale routes from
		 * getting stuck in our adjIn-RIB
		 */
		advertisedRoute = inUpdate.getAdvertised();
		if (advertisedRoute != null) {
			if (this.adjInRIB.withdrawRoute(advertisedRoute.getNlri(), advertisedRoute.getSrcId())) {
				networksToRecalc.add(advertisedRoute.getNlri());
			}
			advertisedRoute = this.importDriver.runImportSpec(advertisedRoute);
		}

		// if we have a new advertised route install it in adj-in rib
		if (advertisedRoute != null) {
			this.adjInRIB.installRoute(advertisedRoute);
			networksToRecalc.add(advertisedRoute.getNlri());
		}

		// step through each of the NLRIs that was touched via the update
		// recalc the new best network (or discover that we don't have one)
		// if we have none we need to do a withdrawl ourself
		// if we have a new network we might need to send out an update
		for (CIDR tNLRI : networksToRecalc) {
			// get all routes in the adj-in RIB for the given network, try and
			// find
			// the best route we have, this will go into the local RIB
			routesToNetwork = this.adjInRIB.fetchRoutesForNLRI(tNLRI);
			newBestRoute = this.routeRanker.getBestRoute(routesToNetwork);

			// we lost our route to that network if newBestRoute is null, we
			// have to tell our peers
			if (newBestRoute == null) {
				this.localRIB.withdrawRoute(tNLRI);
				networksToWithdraw.add(tNLRI);
				this.router.notifyRouteChange();
			} else if (this.localRIB.installRoute(newBestRoute)) {
				// if we have a new network installed in our local rib we should
				// again tell folks
				routesToExport.add(newBestRoute);
				this.router.notifyRouteChange();
			}
		}

		/*
		 * walk through each of our BGP peers, we may or may not have networks
		 * to explicitly withdraw (depends on if we ever advertised the network
		 * in the first place), for each BGP peer, check if we withdraw any
		 * networks advertised to them, if so, send an update to them
		 */
		for (int tASN : this.asToPeerMap.keySet()) {
			/*
			 * create a new update and add any networks we no longer have
			 * connection to do a sanity check that there are withdraws to issue
			 */
			revokeUpdate = new Update(this.myASN, this.wallTime);
			sendUpdate = false;

			for (CIDR tNetwork : networksToWithdraw) {
				if (this.adjOutRIB.withdrawRoute(tNetwork, tASN)) {
					/*
					 * Apply mrai check to the withdrawl
					 */
					if (this.runMRAICheck(tNetwork, tASN)) {
						revokeUpdate.addWithdraw(tNetwork);
						sendUpdate = true;
					}
				}
			}
			if (sendUpdate) {
				this.sendUpdate(revokeUpdate, tASN);
			}
		}

		/*
		 * run each new route in our local RIB through export specs and if we
		 * have a new route to install in our adj-out RIB do so if it is truly
		 * new (only looking at transitive attributes here) then build an update
		 * and send it to connected peers
		 */
		for (Route tRoute : routesToExport) {

			exportAdditions = this.exportDriver.runExportSpec(tRoute, this.asToPeerMap.keySet());

			for (int tASN : exportAdditions.keySet()) {
				if (this.adjOutRIB.installRoute(exportAdditions.get(tASN), tASN)) {
					if (this.runMRAICheck(tRoute.getNlri(), tASN)) {
						this.sendAdvertisement(exportAdditions.get(tASN), tASN);
					}
				}
			}
		}
	}

	/**
	 * Predicate to apply the MRAI policy checks. If we can't advertise it, the
	 * route is marked as dirty if it is not already, so we can advertise the
	 * route later.
	 * 
	 * @param nlri
	 *            - the prefix we're attempting to advertise/withdraw
	 * @param dstASN
	 *            - the as we're attempting to send the update to
	 * @return - true if we're allowed to send the update, false if we're not
	 *         allowed to
	 */
	private boolean runMRAICheck(CIDR nlri, int dstASN) {
		/*
		 * Short circuit test, if our mrai is zero we can always send, skip
		 * everything else
		 */
		if (this.mrai == 0) {
			return true;
		}

		/*
		 * Apply min advertisement intervals policy
		 */
		String mraiKey = dstASN + ":" + nlri.toString();
		if (this.advertisementAllowedMap.containsKey(mraiKey)) {
			if (this.advertisementAllowedMap.get(mraiKey) > this.wallTime) {
				/*
				 * Getting here means that we're unable to send an update this
				 * soon for this prefix, so we should mark the route as dirty
				 */
				if (!this.dirtyRoutes.contains(mraiKey)) {
					this.dirtyRoutes.add(mraiKey);
					this.mraiPendingRoutes.add(mraiKey);
				}
				return false;
			}
		}
		/*
		 * We've gotten this far, which means we're good to send the update,
		 */
		this.advertisementAllowedMap.put(mraiKey, this.wallTime + mrai);
		return true;
	}

	/**
	 * Sends an update advertising the given route to the given AS. Builds
	 * update and invokes sendUpdate(Update, ASN)
	 * 
	 * @param outRoute
	 *            - the route to advertise to the user
	 * @param dstASN
	 *            - the AS we want to advertise the route to
	 */
	private void sendAdvertisement(Route outRoute, int dstASN) {
		Update outUpdate = new Update(this.myASN, this.wallTime);
		outUpdate.setAdvertised(outRoute);
		this.sendUpdate(outUpdate, dstASN);
	}

	private void sendWithdrawl(CIDR nlri, int dstASN) {
		Update outUpdate = new Update(this.myASN, this.wallTime);
		outUpdate.addWithdraw(nlri);
		this.sendUpdate(outUpdate, dstASN);
	}

	/**
	 * Sends an update to the given AS.
	 * 
	 * @param outUpdate
	 *            - the update to send to the given AS
	 * @param dstASN
	 *            - the AS to send the update to
	 */
	private void sendUpdate(Update outUpdate, int dstASN) {
		this.router.sendMessage(dstASN, outUpdate);

		/*
		 * since we sent an update we don't need to send a keepalive, update the
		 * last time we talked to a node
		 */
		this.keepAliveMap.put(dstASN, this.wallTime);
	}

	/**
	 * Initiates our half of the connection. This will allocate locations in
	 * tables and send connect messages, any other steps are taken by the
	 * recieveNewPeer() function
	 * 
	 * @param foriegnASN
	 *            - the ASN of the peer that we want to connect to
	 */
	public void connectBGPPeer(int foriegnASN) {
		Connect connectMsg;

		this.router.clearTCPStack(foriegnASN);

		if (!this.asToPeerMap.containsKey(foriegnASN)) {
			/*
			 * Send the actual connect message to the peer and hand off to
			 * handshake manager
			 */
			connectMsg = new Connect(this.myASN, this.wallTime, 1);
			this.router.sendMessage(foriegnASN, connectMsg);
		} else {
			try {
				throw new Exception("fairly sure this should never happen, yelling lowdly in connectBGPPeer");
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}
		this.handShakeHelper(foriegnASN);
	}

	/**
	 * Figures out which part of the two way handshake we're at.
	 * 
	 * @param foriegnASN
	 *            - the ASN of the peer that is connecting to us
	 */
	private void handShakeHelper(int foriegnASN) {
		int newPeerId = 0;

		/*
		 * we've been waiting for connection (2nd part of handshake) remove from
		 * pending, dump export table, and our connection is now valid, so note
		 * that
		 */
		if (this.pendingPeers.contains(foriegnASN)) {
			/*
			 * Figure out an internal id for this peer
			 */
			while (newPeerId == 0) {
				newPeerId = this.rand.nextInt();
				for (Integer tASN : this.asToPeerMap.keySet()) {
					if (this.asToPeerMap.get(tASN) == newPeerId) {
						newPeerId = 0;
						break;
					}
				}
			}
			this.asToPeerMap.put(foriegnASN, newPeerId);

			/*
			 * Setup timer maps, we don't need to update the keep alive timer
			 * since we'll be sending messages right away which will do that for
			 * us
			 */
			this.lastSeenMap.put(foriegnASN, this.wallTime);

			/*
			 * Send export table dump and remove from pending
			 */
			this.runExportDump(foriegnASN);
			this.pendingPeers.remove(foriegnASN);
		} else {
			/*
			 * we've not been waiting for this connection (1st part of
			 * handshake), we need to wait till the other half is ready to
			 * connect
			 */
			this.pendingPeers.add(foriegnASN);
			this.asConTimeMap.put(foriegnASN, this.wallTime);
		}
	}

	/**
	 * Runs all routes in the local RIB through the export specs, installs any
	 * exportable routes and does so. This is called during the transition to
	 * STATE-ESTABLISHED.
	 * 
	 * @param asn
	 *            - the ASN who just connected to us
	 */
	private void runExportDump(int asn) {
		List<Route> localList, exportList;

		localList = this.localRIB.fetchWholeTable();
		exportList = this.exportDriver.runExportSpecLoneAS(localList, asn);

		for (Route tRoute : exportList) {
			this.adjOutRIB.installRoute(tRoute, asn);
			this.sendAdvertisement(tRoute, asn);
		}
	}

	/**
	 * Removes a BGP peer from our world view. This includes both removing all
	 * entries in our routing tables from them and removing all timers. This
	 * should count as our Update for the turn.
	 * 
	 * @param asn
	 *            - the AS we want to remove
	 * @param addJitter
	 *            - allows us to add some jitter in the reconnection so we don't
	 *            get reconnection collisions, this would normally be done by
	 *            latency, but we add it synthetically, if true adds 200 ms to
	 *            the reconnect timer
	 */
	//FIXME need to remove data from dampening table?  or at least re-use list
	private List<CIDR> runPeerRemoval(int asn, boolean addJitter) {
		Update fakeUpdate;
		List<Route> himToUsList = null;

		/*
		 * first build a "fake" update so we can just run it through the update
		 * processor
		 */
		fakeUpdate = new Update(asn, this.wallTime);
		himToUsList = this.adjInRIB.fetchRoutesForAS(this.asToPeerMap.get(asn));

		if (himToUsList != null) {
			for (Route tRoute : himToUsList) {
				fakeUpdate.addWithdraw(tRoute.getNlri());
			}
			this.processUpdate(fakeUpdate);
		} else {
			System.err.println("removing network without networks advertised to us");
			System.err.println("me: " + this.myASN + " him " + asn + " time " + this.wallTime + " conn started "
					+ this.asConTimeMap.get(asn));
		}

		/*
		 * next off manually clean up our outbound rib, yes we'll have sent a
		 * message to a non-connected peer, but the other peer will receive the
		 * notification of error first, so these will just be ignored
		 */
		List<Route> usToHimList = this.adjOutRIB.fetchRoutesForAS(asn);

		if (usToHimList != null) {
			for (Route tRoute : usToHimList) {
				this.adjOutRIB.withdrawRoute(tRoute.getNlri(), asn);
			}
		} else {
			System.err.println("removing network without networks advertised to him");
			System.err.println("me: " + this.myASN + " him " + asn + " time " + this.wallTime + " conn started "
					+ this.asConTimeMap.get(asn));
		}

		// remove all refs in timer maps and peer map
		this.asToPeerMap.remove(asn);
		this.asConTimeMap.remove(asn);
		this.keepAliveMap.remove(asn);
		this.lastSeenMap.remove(asn);
		this.pendingPeers.remove(asn);

		// start up a reconnect timer for the defined interval
		if (addJitter) {
			this.reconnectMap.put(asn, this.wallTime + BGPDaemon.RECONNECT + 1000);
		} else {
			this.reconnectMap.put(asn, this.wallTime + BGPDaemon.RECONNECT);
		}

		/*
		 * Build the list of routes we had to touch w/ our CPU, report it up to
		 * the router so it can reschedule the CPU correctly
		 */
		List<CIDR> touchedNetList = new LinkedList<CIDR>();
		if (himToUsList != null) {
			for (Route tRoute : himToUsList) {
				touchedNetList.add(tRoute.getNlri());
			}
		}
		return touchedNetList;
	}

	/**
	 * Adds a message to the incoming message queue.
	 * 
	 * @param inMessage
	 *            - message to add to the message queue
	 */
	public void addMessageToQueue(BGPMessage inMessage) {
		if (this.lastSeenMap.containsKey(inMessage.getSrcASN())) {
			this.lastSeenMap.put(inMessage.getSrcASN(), this.wallTime);
		}

		if (inMessage.getMessageType() != Constants.BGP_KEEPALIVE) {
			this.messageQueue.offer(inMessage);
		}
	}

	/**
	 * Tell the daemon it is allowed to handle one update message and as many
	 * keep alives as it sees in the mean time. This method is called by the
	 * simulator to allow the daemon its "turn".
	 * 
	 * @return return a set of all networks that were touched in the process of
	 *         handling a message. By touched I mean that the route processor
	 *         had to actually do a route calculation on that network. This is
	 *         used by the Router class, which will use it for correct CPU time
	 *         bookkeeping.
	 * 
	 */
	public Set<CIDR> handleOneMessage() {
		Set<CIDR> netsTouched = new HashSet<CIDR>();
		boolean ranUpdate = false;
		BGPMessage pollMessage;

		while (!this.messageQueue.isEmpty() && !ranUpdate) {
			pollMessage = this.messageQueue.poll();

			/*
			 * There are a collection of sanity checks and actions we do for any
			 * message on an established connection we skip these checks/actions
			 * on BGP connect messages since they are acting like ACKs
			 */
			if (pollMessage.getMessageType() != Constants.BGP_CONNECT) {
				/*
				 * check if we're actually connected to the peer that just sent
				 * us a message, if not ignore anything they say till reconnect
				 */
				if (pollMessage.getSrcASN() != 0 && !this.asToPeerMap.containsKey(pollMessage.getSrcASN())) {
					continue;
				}

				/*
				 * Check the time stamp on the message if it isn't a connect
				 * message, if it's before we've established a connection, then
				 * that's a bad thing (it's a message from a past session, we
				 * need to ignore it.
				 */
				if (pollMessage.getTimeStamp() < this.asConTimeMap.get(pollMessage.getSrcASN())) {
					continue;
				}
			}

			/*
			 * After sanity checks and generic actions, do message type specific
			 * actions
			 */
			if (pollMessage.getMessageType() == Constants.BGP_UPDATE) {
				Update incUpdate = (Update) pollMessage;
				if (this.rfdFlag) {
					Update delayUpdate = new Update(incUpdate.getSrcId(), incUpdate.getTimeStamp());
					for (CIDR tNetwork : incUpdate.getWithdraws()) {
						this.rfdTable.routeWithdrawn(incUpdate.getSrcId(), tNetwork, this.wallTime);
					}
					if (incUpdate.getAdvertised() != null) {
						if (!this.rfdTable.routeAdvertised(incUpdate.getSrcId(), incUpdate.getAdvertised().getNlri(),
								this.wallTime)) {
							delayUpdate.setAdvertised(incUpdate.getAdvertised());
							this.dampenedUpdates.put(DampeningTable.genKeyString(incUpdate.getSrcId(), incUpdate
									.getAdvertised().getNlri()), delayUpdate);
							incUpdate.clearAdvertised();
						}
					}
				}
				this.processUpdate(incUpdate);

				/*
				 * Add into nets touched all of the withdrawn and advertised
				 * cidrs
				 */
				for (CIDR tNet : incUpdate.getWithdraws()) {
					netsTouched.add(tNet);
				}
				if (incUpdate.getAdvertised() != null) {
					netsTouched.add(incUpdate.getAdvertised().getNlri());
				}

				ranUpdate = true;
			} else if (pollMessage.getMessageType() == Constants.BGP_CONNECT) {
				Connect outMessage;
				Connect connMessage = (Connect) pollMessage;
				if (connMessage.isSyn()) {
					/*
					 * If we're currently connected we should remove the peer
					 * and fall back to a safe, known state
					 */
					if (this.asToPeerMap.containsKey(connMessage.getSrcASN())) {
						Error errorMessage;

						this.logger.logMessage(LoggingMessages.ROUTER_ALREADY_CONN + connMessage.getSrcASN()
								+ LoggingMessages.AT + this.myASN, false);
						this.runPeerRemoval(connMessage.getSrcASN(), false);

						// send notification
						errorMessage = new Error(this.myASN, this.wallTime, LoggingMessages.ROUTER_ALREADY_CONN);
						this.router.sendMessage(connMessage.getSrcASN(), errorMessage);
						continue;
					}

					/*
					 * Remove from reconnect map if it's in there
					 */
					if (this.reconnectMap.containsKey(connMessage.getSrcASN())) {
						this.reconnectMap.remove(connMessage.getSrcASN());
						this.router.clearTCPStack(connMessage.getSrcASN());
					}

					outMessage = new Connect(this.myASN, this.wallTime, 2);
					this.router.sendMessage(connMessage.getSrcASN(), outMessage);
					this.handShakeHelper(connMessage.getSrcASN());
				} else if (connMessage.isSynAck()) {
					outMessage = new Connect(this.myASN, this.wallTime, 3);
					this.router.sendMessage(connMessage.getSrcASN(), outMessage);
					this.handShakeHelper(connMessage.getSrcASN());
				} else if (connMessage.isAck()) {
					this.handShakeHelper(connMessage.getSrcASN());
				} else {
					System.err.println("this should never happen in handle message - conn message");
					System.exit(-3);
				}
				ranUpdate = false;
			} else if (pollMessage.getMessageType() == Constants.BGP_ERROR) {
				/*
				 * We have an error, log it, and remove them from our world
				 * view, then try to reconnect
				 */
				this.logger.logMessage(LoggingMessages.ERROR_MSG + ((Error) pollMessage).getReason()
						+ pollMessage.getSrcASN() + LoggingMessages.AT + this.myASN, false);
				List<CIDR> removedNets = this.runPeerRemoval(pollMessage.getSrcASN(), true);

				/*
				 * Store the nets touched for correct CPU book-keeping
				 */
				for (CIDR tNet : removedNets) {
					netsTouched.add(tNet);
				}

				ranUpdate = true;
			} else {
				System.err.println("ZOMG UNKNOWN MESSAGE TYPE: " + pollMessage.getMessageType());
				System.exit(-3);
			}
		}

		return netsTouched;
	}

	public int getMessageQueueSize() {
		return this.messageQueue.size();
	}

	/**
	 * Runs a check on each set of timers. First we look to see if we need to
	 * send any keep alive messages to our peers. Then we check if there are any
	 * peers that we need to advertise routes that we couldn't send earlyer
	 * because of MRAI. After that we look for any peer that has not talked to
	 * us within a halt timer. If we find one we remove them and stop, since we
	 * can only process one update a turn. We'll find any others in subsequent
	 * turns. This does allow a peer to "sneak in" after it's halt timer has
	 * expired, but that is ok. Lastly we check if we need to reconnect to any
	 * peers, and if so, start the connection process.
	 * 
	 * @return - true if we had to drop a peer (our update for the turn) false
	 *         otherwise
	 */
	//FIXME need to check dampening table too
	public boolean runTimerCheck() {
		Error errorMessage;
		HashSet<Integer> removeSet = new HashSet<Integer>();
		boolean didUpdate = false;

		/*
		 * Vars used for mrai interactions
		 */
		String mraiKeyString;
		int mraiAS;
		CIDR mraiCIDR;
		Route mraiRoute;

		/*
		 * send any keepalives we need to send
		 */
		for (int tASN : this.keepAliveMap.keySet()) {
			if ((this.wallTime - this.keepAliveMap.get(tASN)) >= this.keepaliveTimer) {
				this.router.sendMessage(tASN, new KeepAlive(this.myASN, this.wallTime));
				this.keepAliveMap.put(tASN, this.wallTime);
			}
		}

		/*
		 * Any routes we've wanted to advertise, but couldn't because of MRAI
		 * should be advertised now
		 */
		while (this.mraiPendingRoutes.size() > 0) {
			mraiKeyString = this.mraiPendingRoutes.peek();
			if (this.advertisementAllowedMap.get(mraiKeyString) <= this.wallTime) {
				this.mraiPendingRoutes.poll();
				this.dirtyRoutes.remove(mraiKeyString);
				mraiAS = Integer.parseInt(mraiKeyString.substring(0, mraiKeyString.indexOf(":")));
				mraiCIDR = new CIDR(mraiKeyString.substring(mraiKeyString.indexOf(":") + 1));

				/*
				 * If we're not currently connected then don't send the update
				 */
				if (!this.asConTimeMap.containsKey(mraiAS)) {
					continue;
				}

				mraiRoute = this.adjOutRIB.fetchRoute(mraiCIDR, mraiAS);
				this.advertisementAllowedMap.put(mraiKeyString, this.wallTime + this.mrai);
				/*
				 * If the route is null withdraw it, otherwise advertise it
				 */
				if (mraiRoute == null) {
					this.sendWithdrawl(mraiCIDR, mraiAS);
				} else {
					this.sendAdvertisement(mraiRoute, mraiAS);
				}
			} else {
				break;
			}
		}

		/*
		 * check for peers who have timed out, we only get to clear one since
		 * that's all we can process, we'll clear the others in following turns
		 */
		for (int tASN : this.lastSeenMap.keySet()) {
			if ((this.wallTime - this.lastSeenMap.get(tASN)) >= this.haltTimer) {
				// send notification
				this.router.clearTCPStack(tASN);
				errorMessage = new Error(this.myASN, this.wallTime, LoggingMessages.ROUTER_TIMEOUT);
				this.router.notifySessionFail(tASN, this.wallTime);
				this.router.sendMessage(tASN, errorMessage);

				// log & remove
				this.logger.logMessage(LoggingMessages.ROUTER_TIMEOUT + tASN + LoggingMessages.AT + this.myASN, false);
				this.runPeerRemoval(tASN, false);

				// we now wait for the peer that timed out to re-connect to us
				didUpdate = false;
				break;
			}
		}

		/*
		 * Lastly check if we need to try and reconnect to anyone if so do so
		 * and note who to remove from the reconnect set
		 */
		for (int tASN : this.reconnectMap.keySet()) {
			if (this.reconnectMap.get(tASN) == this.wallTime) {
				this.logger.logMessage(LoggingMessages.RECONNECT + tASN + LoggingMessages.AT + this.myASN, false);
				this.connectBGPPeer(tASN);
				removeSet.add(tASN);
			}
		}
		/*
		 * Remove anyone we've reconnected to from the reconnect map
		 */
		for (int tASN : removeSet) {
			this.reconnectMap.remove(tASN);
		}

		return didUpdate;
	}

	/**
	 * Gets when the next timer expires. This can be one of four timers right
	 * now, keep alive, halt, auto-reconnection, or mrai advertisement.
	 * 
	 * @return - the simulator time of the next expiring event
	 */
	public int getNextTimerExp() {
		int mostRecent = Integer.MAX_VALUE;

		for (int tTime : this.keepAliveMap.values()) {
			if ((tTime + this.keepaliveTimer) < mostRecent) {
				mostRecent = tTime + this.keepaliveTimer;
			}
		}
		for (int tTime : this.lastSeenMap.values()) {
			if ((tTime + this.haltTimer) < mostRecent) {
				mostRecent = tTime + this.haltTimer;
			}
		}
		for (int tTime : this.reconnectMap.values()) {
			if (tTime < mostRecent) {
				mostRecent = tTime;
			}
		}
		if (this.mraiPendingRoutes.size() > 0) {
			if (this.advertisementAllowedMap.get(this.mraiPendingRoutes.peek()) < mostRecent) {
				mostRecent = this.advertisementAllowedMap.get(this.mraiPendingRoutes.peek());
			}
		}
		if (this.rfdFlag) {
			if (this.dampenCheckTimer + BGPDaemon.RFDCHECK < mostRecent) {
				mostRecent = this.dampenCheckTimer + BGPDaemon.RFDCHECK;
			}
		}

		return mostRecent;
	}

	/**
	 * Predicate testing if we will carry traffic for a given as to a given
	 * network This is testing if we will allow the traffic based on policy
	 * alone. If we have exported the route to the host, (i.e. it shows up in
	 * our adjOut RIB) then we will allow the traffic.
	 * 
	 * @param destNetwork
	 *            - the end destination of the traffic
	 * @param srcAsn
	 *            - the AS that is attempting to use us as transit (NOT THE AS
	 *            THAT FIRST SENT THE TRAFFIC)
	 * @return - true if we will allow the traffic, false otherwise
	 */
	public boolean acceptTraffic(CIDR destNetwork, int srcAsn) {
		return this.adjOutRIB.fetchRoute(destNetwork, srcAsn) != null;
	}

	/**
	 * Fetches the route we currently use for ourself to reach the given
	 * network. Simple lookup to the local RIB.
	 * 
	 * @param destNetwork
	 *            - the end destination of the traffic
	 * @return - the route we will use if we have one, NULL otherwise
	 */
	public Route fetchRoute(CIDR destNetwork) {
		return this.localRIB.fetchRoute(destNetwork);
	}

	/**
	 * Updates the walltime to the given value.
	 * 
	 * @param currentWallTime
	 *            - the new wall time
	 */
	public void updateWallTime(int currentWallTime) {
		this.wallTime = currentWallTime;
	}

	/**
	 * Function that dumps the distance to all networks to the log. This has
	 * been kept as each network instead of agregating because we might want to
	 * extract other stats, such as std dev, etc. This does log in size
	 * complexity of N^2, so be warned, kinda large.
	 */
	public void logRouteDistances() {
		List<Route> fullList;

		this.logger.logMessage(LoggingMessages.DISTANCE_DUMP_START + this.myASN, false);

		fullList = this.localRIB.fetchWholeTable();
		for (Route tRoute : fullList) {
			this.logger.logMessage(tRoute.getAsPath().length + LoggingMessages.TO + tRoute.getNlri().toString(), false);
		}

		this.logger.logMessage(LoggingMessages.DISTANCE_DUMP_STOP, false);
	}
}
