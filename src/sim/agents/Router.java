package sim.agents;

import java.util.*;
import java.io.*;

import bgp.messages.BGPMessage;
import bgp.messages.Constants;
import bgp.messages.Update;
import bgp.engine.BGPDaemon;
import bgp.engine.BGPLocalLoader;
import bgp.dataStructures.*;
import sim.logging.LoggingMessages;
import sim.logging.SimLogger;
import sim.network.dataObjects.AS;
import sim.network.dataObjects.TCPPacket;

import sim.engine.SimDriver;
import sim.event.*;

/**
 * This class represents a border router. Currently each AS only has one, this
 * will change in the future and this class will have to change in some
 * fundamental manner when that happens. This class does store the BGP daemon,
 * and provides a nice interface for other classes to get access to it. This
 * class does NOT directly handle traffic, that is the job of the Traffic
 * Accountant.
 * 
 */
public class Router implements SimAgent {

	/**
	 * The BGP daemon that handles routing and forwarding decisions.
	 */
	private BGPDaemon bgpDaemon;

	/**
	 * The internal route reflector for this router. Not sure if this will be
	 * perm, I really don't like it, might be good to kill this at some point.
	 */
	private BGPLocalLoader routeReflector;

	/**
	 * Map of other routers we're connected to. This is indexed by ASN since
	 * fornow it's one router per ASN
	 */
	private HashMap<Integer, Router> connectionMap;

	/**
	 * Map holding our TCP queues
	 */
	private HashMap<Integer, List<TCPPacket>> connectionStack;

	/**
	 * The ASN of the AS we live in
	 */
	private int asn;

	private int cpuWindow;

	private double bgpProcessTime;

	private double packetProcessTime;

	private HashMap<CIDR, Integer> netToASMappings = null;

	private HashMap<Integer, Integer> asWeights = null;

	private boolean cpuScheduled;

	/**
	 * The traffic accoutnant used for this simulation.
	 */
	private TrafficAccountant trafficAcct;

	private SimDriver theDriver;

	/**
	 * The logger used for this simulation.
	 */
	private SimLogger logger;

	private static final String CONF_IMPORT = "import";
	private static final String CONF_EXPORT = "export";
	private static final String CONF_RFD = "rfd";
	private static final String CONF_LOCAL = "localNets";
	private static final String CONF_HALT = "halt";
	private static final String CONF_KEEPALIVE = "keep";
	private static final String CONF_MRAI = "mrai";

	/**
	 * Creates a new router with settings given in the given config file.
	 * 
	 * @param configFile
	 *            - the config file as specified in the IOS spec in the same
	 *            folder
	 * @throws IOException
	 *             - if there is any exception reading the IOS file
	 */
	@SuppressWarnings("unchecked")
	public Router(String configFile, SimLogger logger, double netProcessTime, double bgpProcTime,
			HashMap<CIDR, Integer> netMap, HashMap<Integer, Integer> asWeights) throws IOException {

		HashMap<String, Object> configMap = this.parseRouterConfig(configFile);

		/*
		 * Yay, time to create the BGP Daemon NOT using a serial string, oh, by
		 * the way, we use a ghetto hack to get the data out of the config file,
		 * so we're type casting like it's Java 1.4.2
		 */
		this.routeReflector = new BGPLocalLoader((List<String>) configMap.get(Router.CONF_LOCAL));
		this.bgpDaemon = new BGPDaemon(this.routeReflector, this.asn, this, (List<String>) configMap
				.get(Router.CONF_IMPORT), (List<String>) configMap.get(Router.CONF_EXPORT), (List<String>) configMap
				.get(Router.CONF_RFD), (Integer) configMap.get(Router.CONF_KEEPALIVE), (Integer) configMap
				.get(Router.CONF_HALT), (Integer) configMap.get(Router.CONF_MRAI), logger);

		/*
		 * set this to null for now, we'll need it, but we need to be created
		 * for it to be created
		 */
		this.finalSetup(netProcessTime, bgpProcTime, logger, netMap, asWeights);
	}

	@SuppressWarnings("unchecked")
	public Router(String configFile, SimLogger logger, String serialString, double netProcessTime, double bgpProcTime,
			HashMap<CIDR, Integer> netMap, HashMap<Integer, Integer> asWeights) throws IOException {

		/*
		 * Parse the config file
		 */
		HashMap<String, Object> configMap = this.parseRouterConfig(configFile);

		/*
		 * Yay, time to create the BGP Daemon USING A SERIAL STRING, oh, by the
		 * way, we use a ghetto hack to get the data out of the config file, so
		 * we're type casting like it's Java 1.4.2
		 */
		this.bgpDaemon = new BGPDaemon(this.asn, this, (List<String>) configMap.get(Router.CONF_IMPORT),
				(List<String>) configMap.get(Router.CONF_EXPORT), (List<String>) configMap.get(Router.CONF_RFD),
				(Integer) configMap.get(Router.CONF_KEEPALIVE), (Integer) configMap.get(Router.CONF_HALT),
				(Integer) configMap.get(Router.CONF_MRAI), logger, serialString);

		/*
		 * set this to null for now, we'll need it, but we need to be created
		 * for it to be created
		 */
		this.finalSetup(netProcessTime, bgpProcTime, logger, netMap, asWeights);
	}

	//TODO at some point this should be replaced with a config file parser
	private HashMap<String, Object> parseRouterConfig(String configFile) throws IOException {
		BufferedReader configBuff = new BufferedReader(new FileReader(configFile));
		StringTokenizer lineTokens;
		String line, first, second;
		int keepAlive, haltTimer, mrai;
		boolean importFlag, exportFlag, rfdFlag;
		List<String> importStrings, exportStrings, rfdStrings;
		List<String> locNets = new LinkedList<String>();

		// we need to make sure asn, keepAlive, and ping are set
		this.asn = -1;
		keepAlive = -1;
		haltTimer = -1;
		mrai = -1;

		/*
		 * we need to pass config strings bound for the import and export spec
		 * drivers to them using these guys
		 */
		importStrings = new LinkedList<String>();
		exportStrings = new LinkedList<String>();
		rfdStrings = new LinkedList<String>();
		importFlag = false;
		exportFlag = false;
		rfdFlag = false;

		// you know the drill, step through the config file, do what it says
		while (configBuff.ready()) {

			// read the line, get rid of white space, ignore blank lines
			line = configBuff.readLine().trim();
			if (line.length() == 0) {
				continue;
			}

			/*
			 * split the string based on the seperator (space) grab the main and
			 * sub arg
			 */
			lineTokens = new StringTokenizer(line, " ");
			first = lineTokens.nextToken().toLowerCase();
			second = lineTokens.nextToken().toLowerCase();

			if (first.equals("router")) {
				if (second.equals("asn")) {
					this.asn = Integer.parseInt(lineTokens.nextToken());
				} else if (second.equals("keepalive")) {
					keepAlive = Integer.parseInt(lineTokens.nextToken());
				} else if (second.equals("haltvalue")) {
					haltTimer = Integer.parseInt(lineTokens.nextToken());
				} else if (second.equals("mrai")) {
					mrai = Integer.parseInt(lineTokens.nextToken());
				} else {
					System.err.println("bad config line: " + line);
					System.exit(-1);
				}
			} else if (first.equals("network")) {
				if (second.equalsIgnoreCase("addr")) {
					locNets.add(lineTokens.nextToken());
				} else {
					System.err.println("bad config line: " + line);
					System.exit(-1);
				}
			} else if (first.equals("import")) {
				if (second.equals("start")) {
					importFlag = true;
				} else if (second.equals("stop")) {
					importFlag = false;
				} else {
					System.err.println("bad config line: " + line);
					System.exit(-1);
				}
			} else if (first.equals("export")) {
				if (second.equals("start")) {
					exportFlag = true;
				} else if (second.equals("stop")) {
					exportFlag = false;
				} else {
					System.err.println("bad config line: " + line);
					System.exit(-1);
				}
			} else if (first.equals("rfd")) {
				if (second.equals("start")) {
					rfdFlag = true;
				} else if (second.equals("stop")) {
					rfdFlag = false;
				} else {
					System.err.println("bad config line: " + line);
					System.exit(-1);
				}
			} else if (importFlag) {
				importStrings.add(line);
				continue;
			} else if (exportFlag) {
				exportStrings.add(line);
				continue;
			} else if (rfdFlag) {
				rfdStrings.add(line);
				continue;
			} else {
				System.err.println("bad config line: " + line);
				System.exit(-1);
			}
		}

		if (keepAlive == -1 || haltTimer == -1 || mrai == -1 || this.asn == -1) {
			System.err
					.println("one router value not set! " + keepAlive + "/" + haltTimer + "/" + mrai + "/" + this.asn);
			System.exit(-1);
		}

		/*
		 * Build the ret map -- oh dear god jesus this is messy, as we're adding
		 * lists of strings and strings, ghetto hack ENGAGE!
		 */
		HashMap<String, Object> retMap = new HashMap<String, Object>();
		retMap.put(Router.CONF_IMPORT, importStrings);
		retMap.put(Router.CONF_EXPORT, exportStrings);
		retMap.put(Router.CONF_RFD, rfdStrings);
		retMap.put(Router.CONF_LOCAL, locNets);
		retMap.put(Router.CONF_HALT, new Integer(haltTimer));
		retMap.put(Router.CONF_KEEPALIVE, new Integer(keepAlive));
		retMap.put(Router.CONF_MRAI, new Integer(mrai));
		return retMap;
	}

	private void finalSetup(double netProcTime, double bgpProcTime, SimLogger logger, HashMap<CIDR, Integer> netMap,
			HashMap<Integer, Integer> weightMap) {
		this.connectionStack = new HashMap<Integer, List<TCPPacket>>();
		this.connectionMap = new HashMap<Integer, Router>();
		this.logger = logger;
		this.trafficAcct = null;
		this.theDriver = null;
		this.packetProcessTime = netProcTime;
		this.bgpProcessTime = bgpProcTime;
		this.cpuWindow = 0;
		this.cpuScheduled = false;
		this.netToASMappings = netMap;
		this.asWeights = weightMap;
	}

	public String serialString() {
		return this.bgpDaemon.serialString();
	}

	/**
	 * Sets the traffic accountant, this can only be done once. We have to do
	 * this outside of the constructor since the traffic accountant is dependent
	 * on us and we on him, and by luck of the draw routers get made first.
	 * 
	 * @param lhs
	 *            - the traffic accountant we will use
	 */
	public void setTrafficAcct(TrafficAccountant lhs) {
		if (this.trafficAcct == null) {
			this.trafficAcct = lhs;
		}
	}

	public void setSimDriver(SimDriver theDriver) {
		if (this.theDriver == null) {
			this.theDriver = theDriver;
		}
	}

	public void giveEvent(SimEvent theEvent) {
		/*
		 * no matter the event, update the time
		 */
		this.bgpDaemon.updateWallTime(theEvent.getTime());

		if (theEvent.getType() == SimEvent.TIMEREXPIRE) {
			/*
			 * run the timer check
			 */
			if (this.bgpDaemon.runTimerCheck()) {
				// FIXME update cpu stats here if we're doing something complicated
			}

			/*
			 * Insert the next timer check, apply some sanity checks, wait no
			 * longer then 5 secs and no more then min cpu time
			 */
			int nextUpdate = this.bgpDaemon.getNextTimerExp();
			nextUpdate = Math.min(nextUpdate, theEvent.getTime() + 5000);
			/*
			 * FIXME this got hard coded over to one...not sure if that is
			 * right...in fact it isn't...this should instead of being 1, but
			 * the current CPU window. Now there might be some issues with this.
			 * Right now we're letting nodes always run their timers. Good news
			 * of this: routers never fail to send keep alives accidently (prob
			 * not an issue given that routers do KAs every 1 sec to simulate
			 * background BGP traffic) bad news is that routers get to handle
			 * any expiration without concern to it's CPU state, which of course
			 * means that it might push updates in a little too fast (only dir
			 * targeted by DoS routers will do this, so again, fairly small)
			 */
			nextUpdate = Math.max(nextUpdate, theEvent.getTime() + 1);
			this.theDriver.postEvent(new SimEvent(SimEvent.TIMEREXPIRE, nextUpdate, this));
		} else if (theEvent.getType() == SimEvent.ROUTERCPUFREE) {

			/*
			 * TODO at some point it might be wise to think about the way we
			 * schedule router CPUs, there are a few too many hacks then I am
			 * comfortable with in here...
			 */

			/*
			 * we are ok to run something on the cpu, give it a spin
			 */
			if (theEvent.getTime() >= this.cpuWindow) {

				/*
				 * Handle one message, if we touch any networks then we'll need
				 * to note that to deal w/ the CPU
				 */
				Set<CIDR> touchedNets = this.bgpDaemon.handleOneMessage();

				/*
				 * If we touched networks, then compute how many "real" IP
				 * blocks we were talking about
				 */
				if (touchedNets.size() > 0) {
					int netCount = 0;
					for (CIDR tempNet : touchedNets) {
						Integer tASN = this.netToASMappings.get(tempNet);
						if (tASN == null) {
							System.err.println("got a network not bound to an AS, this should NEVER happen.");
							System.exit(-2);
						}
						Integer netWeight = this.asWeights.get(tASN);
						if (netWeight == null) {
							netCount += 1;
						} else {
							netCount += netWeight;
						}
					}

					/*
					 * Figure out the cpu time spent processing, this is done by
					 * computing the amount of CPU time spent processing each IP
					 * block. Here is where this gets a little murky....we don't
					 * want do drop under our time granularity (1 ms).
					 * Additionally, router performance is vastly dependent on
					 * the number of update messages per packet. So gogo slight
					 * hack, we'll set a min time (the packet processing time),
					 * which will be the floor, even if you could get done in
					 * microseconds, you still take miliseconds.
					 */
					double timeSpent = netCount * this.bgpProcessTime;
					//FIXME remove me
					timeSpent = 0;
					if (timeSpent < this.packetProcessTime) {
						timeSpent = this.packetProcessTime;
					}
					this.cpuWindow = this.theDriver.getCurrentTime() + (int) Math.round(timeSpent);
				}
				else{
					this.cpuWindow = this.theDriver.getCurrentTime() + 1;
				}
			}
			/*
			 *  TODO can the event ever be before, right now it looks like we're
			 *  just throwing the event away
			 */

			/*
			 * if we still have pending messages, we need to inject a
			 * notification into the queue
			 */
			if (this.bgpDaemon.getMessageQueueSize() > 0) {
				this.theDriver.postEvent(new SimEvent(SimEvent.ROUTERCPUFREE, this.cpuWindow, this));
				this.cpuScheduled = true;
			} else {
				this.cpuScheduled = false;
			}
		} else if (theEvent.getType() == SimEvent.TCPSEND) {
			TCPEvent tcpEvent = (TCPEvent) theEvent;
			this.runTCP(tcpEvent);
		} else {
			throw new IllegalArgumentException("incorrent event given to router: " + theEvent.getType());
		}
	}

	public void notifyRouteChange() {
		if (this.trafficAcct != null) {
			this.trafficAcct.informRouteChange();
		}
	}

	/**
	 * Interface used by other routers to give us a message. The incoming
	 * message gets placed in the queue until the queue gets flushed.
	 * 
	 * @param inMsg
	 *            - the bgp message to add to the queue
	 */
	public void postMessage(BGPMessage inMsg) {
		this.bgpDaemon.addMessageToQueue(inMsg);
		String ipStr = "0.0.0.0/0";
		if (inMsg.getMessageType() == Constants.BGP_UPDATE) {
			Update tUpdate = (Update) inMsg;
			if (tUpdate.getAdvertised() != null) {
				ipStr = tUpdate.getAdvertised().getNlri().toString();
			}
		}
		this.logger.logMessage(this.theDriver.getCurrentTime() + LoggingMessages.MESSAGE_ARRIVE
				+ inMsg.getMessageType() + LoggingMessages.TO + this.asn + LoggingMessages.FROM + inMsg.getSrcASN()
				+ LoggingMessages.ON + ipStr, inMsg.getMessageType() == Constants.BGP_KEEPALIVE);

		if (!this.cpuScheduled && inMsg.getMessageType() != Constants.BGP_KEEPALIVE) {
			this.theDriver.postEvent(new SimEvent(SimEvent.ROUTERCPUFREE, Math.max(this.theDriver.getCurrentTime(),
					this.cpuWindow), this));
			this.cpuScheduled = true;
		}
	}

	/**
	 * Attempts to send messages that are in the "TCP" stack to their
	 * destinations. This does a lot of the things TCP does, it ensures in order
	 * delivery and handles retransmission.
	 * 
	 * @param currentTime
	 *            - the current simulation wall time
	 */
	private void runTCP(TCPEvent tcpEvent) {
		List<TCPPacket> tStack = tcpEvent.getTcpQueue();
		TCPPacket tPacket = tcpEvent.getPacket();
		if (tPacket.sendResult(this.trafficAcct.routerMessageWorks(this.asn, tPacket.getDst()))) {
			while (tStack.size() > 0) {
				tPacket = tStack.remove(0);

				if (tPacket.getArrived()) {
					this.connectionMap.get(tPacket.getDst()).postMessage(tPacket.getMessage());
				} else {
					/*
					 * Since we have to deliver messages in order, we put the
					 * message back and stop delivering
					 */
					tStack.add(0, tPacket);
					break;
				}
			}
		} else {
			this.theDriver.postEvent(new TCPEvent(tPacket.getTransTime(), this, tPacket, tStack));
		}
	}

	/**
	 * Adds a message to the "TCP" stack to be sent.
	 * 
	 * @param dstASN
	 *            - the destination router we want to send the message to
	 * @param msg
	 *            - the BGPMessage we want to send
	 */
	public void sendMessage(int dstASN, BGPMessage msg) {
		/*
		 * Create this packet, the line card should try to fire this off next
		 * sim tick, hence the plus one underneath
		 */
		TCPPacket tempPacket = new TCPPacket(msg, this.theDriver.getCurrentTime() + 1, dstASN);
		this.connectionStack.get(dstASN).add(tempPacket);
		this.theDriver.postEvent(new TCPEvent(tempPacket.getTransTime(), this, tempPacket, this.connectionStack
				.get(dstASN)));
		this.logger.logMessage(this.theDriver.getCurrentTime() + LoggingMessages.MESSAGE_GEN + msg.getMessageType()
				+ LoggingMessages.TO + dstASN + LoggingMessages.FROM + this.asn, true);
	}

	/**
	 * Starts up a connection between two routers. This should be called once
	 * per pair of routers only, to ensure it is not called twice the
	 * isInitiallyConnected function is used to test if the other router has
	 * already started a connection. If it has, then no need to start up the
	 * connection at the BGPDaemon level.
	 * 
	 * @param asn
	 *            - the AS the router is located in
	 * @param router
	 *            - the router we want to connect to
	 */
	public void connectToRouter(int asn, Router router) {
		/*
		 * If we're the first of the pair to connect, send connect messages
		 */
		if (!router.isInitiallyConnected(this.asn)) {
			this.connectionMap.put(asn, router);
			this.connectionStack.put(asn, new LinkedList<TCPPacket>());
			router.connectionMap.put(this.asn, this);
			router.connectionStack.put(this.asn, new LinkedList<TCPPacket>());
			this.bgpDaemon.updateWallTime(this.theDriver.getCurrentTime());
			this.bgpDaemon.connectBGPPeer(asn);
		}
	}

	public void connectWithoutConnecting(int asn, Router router) {
		this.connectionMap.put(asn, router);
		this.connectionStack.put(asn, new LinkedList<TCPPacket>());
	}

	/**
	 * Predicate that checks if we have started the connection to the peer at
	 * the VERY begining of the simulation.
	 * 
	 * @param asn
	 *            - the asn we want to know if we're connected to or not
	 * @return - true if have them in our connection map (have made the very
	 *         initial connection), false otherwise
	 */
	private boolean isInitiallyConnected(int asn) {
		return this.connectionMap.containsKey(asn);
	}

	/**
	 * Clears all messages in the "TCP" stack, this is basically just starting
	 * up a new connection.
	 * 
	 * @param asn
	 *            - the destination ASN who we are starting a new connection
	 *            with
	 */
	public void clearTCPStack(int asn) {
		this.connectionStack.get(asn).clear();
	}

	/**
	 * Predicate to test if the router should accept incoming traffic. This is
	 * done to enforce the fact that customers won't carry traffic for
	 * providers, etc. This check is done by the BGPDaemon, based on the source
	 * of the traffic (currently asn) and the destination network.
	 * 
	 * @param destNetwork
	 *            - the destination network the traffic is bound for
	 * @param srcAsn
	 *            - the direct AS that is attempting to move the traffic (NOT
	 *            THE AS THAT FIRST ADDED THE TRAFFFIC)
	 * @return - true if this router should accept the traffic based on policy,
	 *         false otherwise
	 */
	public boolean acceptTraffic(CIDR destNetwork, int srcAsn) {
		return this.bgpDaemon.acceptTraffic(destNetwork, srcAsn);
	}

	/**
	 * Interface allowing hosts connected to the router to fetch the route to a
	 * given network. The router will simply as the BGPDaemon for the route.
	 * This should only be called by hosts internal to the router, as the result
	 * is only applicable to them.
	 * 
	 * @param destNetwork
	 *            - the destination network we want to reach
	 * @return - the route we currently use to get to the network, NULL if we
	 *         have no route
	 */
	public Route getRoute(CIDR destNetwork) {
		return this.bgpDaemon.fetchRoute(destNetwork);
	}

	/**
	 * Prints the BGP Daemon's status, is massive and wordy.
	 * 
	 * @param verbose
	 *            - set to true if we consider this verbose (not always needed)
	 *            false otherwise
	 */
	public void doBGPDaemonDump(boolean verbose) {
		this.logger.logMessage(this.bgpDaemon.getStatus(), verbose);
	}

	/**
	 * Tells the BGPDaemon to dump the hop counts to all networks it knows about
	 * to logs.
	 */
	public void doRouteDistanceDump() {
		this.bgpDaemon.logRouteDistances();
	}

	public void notifySessionFail(int rhs, int time) {
		this.theDriver.notifyBotSessionFailHack(this.asn, rhs, time);
	}
}
