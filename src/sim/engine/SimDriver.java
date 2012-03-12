package sim.engine;

import java.util.*;
import java.io.*;

import sim.agents.*;
import sim.event.*;
import sim.logging.*;
import sim.network.assembly.RealTopology;
import sim.network.dataObjects.AS;
import sim.util.*;

//Exit error codes
//  -1 = config error
//  -2 = ??
//  -3 = threading error
public class SimDriver implements Runnable, SimAgent {

	private int currentTime;

	private int maxTime;

	private SimWorkerPool workerPool;

	private PriorityQueue<SimEvent> eventQueue;

	private HashMap<Integer, Router> routerMap;

	private HashMap<Integer, AS> asMap;

	private TrafficAccountant trafficMgmt;

	private BotMaster botMaster;

	private SimLogger logger;

	private HashSet<Integer> started;

	private int routerFlight;

	private static final boolean DEBUG = false;
	
	private static final String CONS_TIME = "time";
	private static final String CONS_LEFT = "left";

	public static void main(String argv[]) {
		/*
		 * List of required params in config file
		 */
		String botType = "bot type";
		String serialFile = "serial file";
		String simTime = "sim time";
		String logTime = "log time";
		String netProcessTime = "net process time";
		String bgpProcessTime = "bgp process time";
		String workerCount = "worker count";
		String asFile = "as file";
		String botFile = "bot conf file";
		String largeRouterFile = "large router conf file";
		String smallRouterFile = "small router conf file";
		String largeCut = "large cutoff";
		String logFile = "log file";

		/*
		 * Setup required config set
		 */
		Set<String> configReqParams = new HashSet<String>();
		configReqParams.add(botType);
		configReqParams.add(serialFile);
		configReqParams.add(simTime);
		configReqParams.add(logTime);
		configReqParams.add(netProcessTime);
		configReqParams.add(bgpProcessTime);
		configReqParams.add(workerCount);
		configReqParams.add(botFile);
		configReqParams.add(largeRouterFile);
		configReqParams.add(smallRouterFile);
		configReqParams.add(largeCut);
		configReqParams.add(logFile);
		if (!SimDriver.DEBUG) {
			configReqParams.add(asFile);
		}
		ConfigFileHelper configFile = new ConfigFileHelper(configReqParams);

		/*
		 * Load the config file given by the run time arg
		 */
		if (argv.length != 1) {
			System.err.println("Invalid usage!\nSimDriver <sim config file path>");
			System.exit(-1);
		}
		try {
			configFile.parseFile(argv[0]);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}

		/*
		 * build a hash map out of the network size params
		 */
		SimAssembly theFactory = null;
		HashMap<String, String> networkLinkSizeMap = new HashMap<String, String>();
		networkLinkSizeMap.put(RealTopology.T3LINKPARAM, configFile.getValue(RealTopology.T3LINKPARAM));
		networkLinkSizeMap.put(RealTopology.T2T2LINKPARAM, configFile.getValue(RealTopology.T2T2LINKPARAM));
		networkLinkSizeMap.put(RealTopology.T2T1LINKPARAM, configFile.getValue(RealTopology.T2T1LINKPARAM));
		networkLinkSizeMap.put(RealTopology.T1T1LINKPARAM, configFile.getValue(RealTopology.T1T1LINKPARAM));

		/*
		 * Build the sim data structs and then garbage collect
		 */
		theFactory = new SimAssembly(configFile.getValue(logFile), SimDriver.DEBUG, true, SimDriver.DEBUG, 128,
				configFile.getValue(botType), configFile.getValue(botFile), configFile.getValue(largeRouterFile),
				configFile.getValue(smallRouterFile), configFile.getIntegerValue(largeCut), configFile
						.getValue(serialFile), configFile.getIntegerValue(netProcessTime), configFile
						.getDoubleValue(bgpProcessTime), networkLinkSizeMap, configFile.getValue(asFile));

		SimDriver theDriver = new SimDriver(theFactory, Integer.parseInt(configFile.getValue(simTime)), Integer
				.parseInt(configFile.getValue(workerCount)));
		theFactory.purgeSerialState();
		System.gc();

		/*
		 * start up the system.in console thread
		 */
		Thread consThread = new Thread(theDriver);
		consThread.setDaemon(true);
		consThread.start();

		System.out.println("done w/ pre-sim, starting sim");
		long runTime = System.currentTimeMillis();
		theDriver.runSim();
		runTime = System.currentTimeMillis() - runTime;
		System.out.println("sim done at: " + runTime);
		theDriver.logger.logMessage("sim done at: " + runTime, false);
		theDriver.cleanUp(SimDriver.DEBUG);

		if (!theFactory.getUsedSerialString()) {
			theDriver.doSerialDump(configFile.getValue(logFile));
		}

		System.out.println("starting log parse");

		/*
		 * Do the actual parse of the log file, use the weighted parser, as
		 * we're interested in more then just networks w/ a single IP block
		 */
		WeightedLogParser logParse = new WeightedLogParser(configFile.getValue(logFile), theFactory.getASWeights());
		logParse.messageSweep();
		logParse.buildCDFs(configFile.getIntegerValue(logTime));

		/*
		 * Generate the makespan data
		 */
		try {
			TTPParser obj = new TTPParser(configFile.getValue(logFile), configFile.getDoubleValue(bgpProcessTime),
					theFactory.getASMap());
		} catch (IOException e) {
			System.err.println("oh dear lord there was an issue doing the makespan parse");
			e.printStackTrace();
		}

		System.out.println("ALL DONE");
	}

	public SimDriver(SimAssembly simFactory, int maxTime, int workerCount) {
		this.workerPool = new SimWorkerPool(workerCount);
		this.eventQueue = new PriorityQueue<SimEvent>();
		this.currentTime = 0;
		this.maxTime = maxTime;
		this.routerFlight = 0;

		this.routerMap = simFactory.getRouterMap();
		this.asMap = simFactory.getASMap();
		this.trafficMgmt = simFactory.getTrafficAccountant();
		this.botMaster = simFactory.getBotMaster();
		this.logger = simFactory.getLogStream();

		this.doPreLogging();
		this.registerDriver();
		this.setupRouterConnections(simFactory.getSerialStart());
	}

	public String serialString() {
		StringBuilder retString = new StringBuilder();

		for (int tAS : this.asMap.keySet()) {
			for (AS nAS : this.asMap.get(tAS).getAllNeighbors()) {
				retString.append(tAS + "#" + nAS.getASNumber() + "@");
			}
		}

		return retString.substring(0, retString.length() - 1);
	}

	public void doSerialDump(String logBase) {
		try {
			BufferedWriter outBuff = new BufferedWriter(new FileWriter(SimLogger.DIR + logBase + ".serial"));
			outBuff.write(this.serialString());

			for (int tAS : this.routerMap.keySet()) {
				outBuff.write("\n");
				outBuff.write("" + tAS);
				outBuff.write("&");
				outBuff.write(this.routerMap.get(tAS).serialString());
			}

			outBuff.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public int getCurrentTime() {
		return this.currentTime;
	}

	public synchronized void postEvent(SimEvent inEvent) {
		this.eventQueue.add(inEvent);
	}

	/**
	 * Dumps information to the console about the current simulation run.
	 */
	public void run() {
		try {
			BufferedReader sysBuff = new BufferedReader(new InputStreamReader(System.in));
			while (true) {
				String consInput = sysBuff.readLine();
				consInput = consInput.trim().toLowerCase();
				
				if (consInput.equals(SimDriver.CONS_TIME)) {
					System.out.println("current time is: " + this.currentTime + " ("
							+ ((double) this.currentTime / (double) this.maxTime) + ")");
				} else if(consInput.equals(SimDriver.CONS_LEFT)){
					
					/*
					 * take a time and sim step reading
					 */
					System.out.println("starting measurement, this will take 1 minute");
					int currStep = this.currentTime;
					long currentTime = System.currentTimeMillis();
					
					/*
					 * wait one minute
					 */
					try {
						Thread.sleep(60000);
					} catch (InterruptedException e) {
						System.out.println("error while sleeping to figure out time left...that's odd...");
						continue;
					}
					
					/*
					 * take another set of readings
					 */
					currStep = this.currentTime - currStep;
					currentTime = System.currentTimeMillis() - currentTime;
					
					/*
					 * do some math, spit out an time left answer
					 */
					double simRate = (double)currStep / (double)currentTime;
					int toGo = this.maxTime - this.currentTime;
					double timeLeftHours = ((double)toGo / simRate) / 3600000.0;
					System.out.println("sim rate is: " + simRate + " (sim ms/ wall ms)");
					System.out.println("estimated time to completion: " + timeLeftHours + "(hrs)");
				}
				else{
					System.out.println("valid options are:");
					System.out.println("   " + SimDriver.CONS_TIME);
					System.out.println("   " + SimDriver.CONS_LEFT);
				}
				
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void runSim() {
		SimEvent currentEvent;
		int watchTime = this.maxTime / 10;
		int watchCounter = 0;
		boolean workersRunning = false;

		while (this.currentTime < this.maxTime) {
			synchronized (this) {
				currentEvent = this.eventQueue.peek();
			}

			if (currentEvent == null) {
				System.err.println("out of events at time: " + this.currentTime);
				break;
			}

			if (this.currentTime > currentEvent.getTime()) {
				System.err.println("wtf: " + this.currentTime + " " + currentEvent.getTime() + " type "
						+ currentEvent.getType());
				System.exit(-2);
			}

			if (!workersRunning) {
				synchronized (this) {
					currentEvent = this.eventQueue.poll();
				}

				this.currentTime = currentEvent.getTime();
				if (this.currentTime > watchTime) {
					watchCounter++;
					System.out.println("" + (watchCounter * 10) + "% done");
					watchTime += this.maxTime / 10;
				}

				if (currentEvent.getType() == SimEvent.ROUTERCPUFREE) {
					this.workerPool.addTask(currentEvent);
					workersRunning = true;
				} else {
					currentEvent.dispatch();
				}
			} else {
				/*
				 * TODO in the future we might have to block differently, in
				 * other words not just on time, but event type
				 */
				if (currentEvent.getTime() > this.currentTime) {
					this.workerPool.blockOnEpoch();
					workersRunning = false;
					continue;
				} else {
					synchronized (this) {
						currentEvent = this.eventQueue.poll();
					}
					this.workerPool.addTask(currentEvent);
				}
			}
		}

		this.workerPool.closePool();
	}

	private void registerDriver() {
		for (Router tRouter : this.routerMap.values()) {
			tRouter.setSimDriver(this);
		}
		this.trafficMgmt.setSimDriver(this);

		if (this.botMaster != null) {
			this.botMaster.setSimDriver(this);
		}
	}

	private void setupRouterConnections(String serialStart) {
		for (int tASN : this.asMap.keySet()) {
			this.postEvent(new SimEvent(SimEvent.TIMEREXPIRE, 1000, this.routerMap.get(tASN)));
		}

		if (serialStart == null) {
			this.started = new HashSet<Integer>();
			this.postEvent(new SimEvent(SimEvent.TIMEREXPIRE, 0, this));
		} else {
			StringTokenizer topTokens = new StringTokenizer(serialStart, "@");
			while (topTokens.hasMoreTokens()) {
				String poll = topTokens.nextToken();
				StringTokenizer midTokens = new StringTokenizer(poll, "#");
				int lhs = Integer.parseInt(midTokens.nextToken());
				int rhs = Integer.parseInt(midTokens.nextToken());

				this.routerMap.get(lhs).connectWithoutConnecting(rhs, this.routerMap.get(rhs));
			}
		}
	}

	private void doPreLogging() {
		this.logger.logMessage("Starting sim with max length of: " + this.maxTime, false);
		// log the opening AS relationships
		this.logger.logMessage("Opening AS relationships", false);
		for (int tASN : this.asMap.keySet()) {
			this.logger.logMessage(this.asMap.get(tASN).toString(), false);
			this.logger.logMessage(this.asMap.get(tASN).dumpNeighbors(), false);
		}
	}

	private void cleanUp(boolean bgpDump) {
		this.logger.logMessage("Done with Sim", false);

		// dump starting state for all routers to log

		if (bgpDump) {
			for (Integer tASN : this.routerMap.keySet()) {
				this.routerMap.get(tASN).doBGPDaemonDump(false);
			}
		}
		this.logger.doneLogging();
	}

	public void giveEvent(SimEvent theEvent) {
		if (theEvent.getType() == SimEvent.TIMEREXPIRE) {
			List<AS> connectionList;

			int maxDeg = 0;
			int currAS = -1;

			/*
			 * Look at all routers, find the one with the largest degree that
			 * has not been started yet
			 */
			for (int tASN : this.asMap.keySet()) {
				if (!this.started.contains(tASN) && this.asMap.get(tASN).getDegree() > maxDeg) {
					maxDeg = this.asMap.get(tASN).getDegree();
					currAS = tASN;
				}
			}

			if (currAS == -1) {
				System.out.println("done booting routers");
				return;
			}

			/*
			 * Add it to the set of started routers, keep track of how many
			 * routers are running
			 */
			this.started.add(currAS);
			this.routerFlight++;

			/*
			 * connect all neighboring routers together
			 */
			connectionList = this.asMap.get(currAS).getAllNeighbors();
			for (AS tempAS : connectionList) {
				this.routerMap.get(currAS).connectToRouter(tempAS.getASNumber(),
						this.routerMap.get(tempAS.getASNumber()));
			}

			/*
			 * If we're in our opening large routers, give us a little bit more
			 * of a time gap (1 minute) between routers starting up, otherwise,
			 * 2 seconds...
			 */
			if (this.routerFlight < 20) {
				this.postEvent(new SimEvent(SimEvent.TIMEREXPIRE, this.currentTime + 60000, this));
			} else {
				this.postEvent(new SimEvent(SimEvent.TIMEREXPIRE, this.currentTime + 2000, this));
			}
		} else {
			throw new IllegalArgumentException("bad event in sim driver: " + theEvent.getType());
		}
	}

	public void setSimDriver(SimDriver theDriver) {
		// does nothing since we are our own driver of course
	}

	public void notifyBotSessionFailHack(int lhs, int rhs, int time) {
		this.botMaster.notifySessionFail(this.asMap.get(lhs).getLinkToNeighbor(rhs), time);
	}
}
