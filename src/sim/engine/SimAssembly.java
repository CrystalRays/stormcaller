package sim.engine;

import java.io.*;
import java.util.*;

import bgp.dataStructures.CIDR;

import sim.agents.*;
import sim.agents.attackers.*;
import sim.logging.ASIPParse;
import sim.logging.SimLogger;
import sim.network.dataObjects.*;
import sim.network.assembly.*;

/**
 * This class is used to wrap all object creation for the simulation in one
 * place. This class should create any agents that are needed for the
 * simulation, doing all of their correct setup.
 * 
 */
public class SimAssembly {

	/**
	 * Storage dict for AS objects, indexed by ASN
	 */
	private HashMap<Integer, AS> asMap;

	private HashMap<CIDR, Integer> cidrToASMapping;

	/**
	 * Storage dict for Router objects, indexed by ASN
	 */
	private HashMap<Integer, Router> routerMap;

	/**
	 * Central object that handles simulating traffic flows.
	 */
	private TrafficAccountant trafficAccountant;

	private BotMaster botMaster;

	private ASIPParse asWeighter = null;

	/**
	 * The logging object created for this simulator
	 */
	private SimLogger logger;

	private String serialStub = null;

	private boolean usedSerialString = false;

	/**
	 * Constructor that is actually a meta-constructor. The args provide this
	 * assembly object all of the information it needs to build all objects
	 * needed for the simulation. This is exactly what is done, after the
	 * constructor returns the get methods found at the bottom of this code
	 * should be called to acquire the correct objects.
	 * 
	 * @param networkSize
	 *            - the number of ASes in the simulation
	 * @param freshConfigFiles
	 *            - flag to tell us if we're creating fresh config files or
	 *            using those found in the IOS folder (DANGEROUS)
	 * @param bigLogFile
	 *            - set to true if you want to log everything and get a truely
	 *            massive log file, false otherwise (small log file that doesn't
	 *            actually lose that much)
	 * @param legitTraffic
	 *            - the volume of legit traffic we want sent, in Kbps
	 * @param botnetSize
	 *            - the number of bots in the botnet
	 * @param botResources
	 *            - the speed of the connection the bots have, in Kbps
	 */
	public SimAssembly(String logName, boolean debugMode, boolean freshConfigFiles, boolean bigLogFile,
			int attackPacketSize, String botStyle, String botConfig, String largeRouterConfig,
			String smallRouterConfig, int largeCutoff, String serialFile, int netProcTime, double bgpProcTime,
			HashMap<String, String> networkLinkSize, String asDataFile) {

		/*
		 * build logger, exit if it doesn't get created since doing a sim w/o
		 * one is stupid
		 */
		try {
			this.logger = new SimLogger(logName, bigLogFile);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-2);
		}

		/*
		 * Currently: everyone depends on AS Map Traffic Senders depend on
		 * Traffic Accountant Traffic Accountant depends on Router Map
		 */
		this.buildNetworks(debugMode, networkLinkSize, asDataFile);
		this.buildRouters(freshConfigFiles, largeRouterConfig, smallRouterConfig, largeCutoff, serialFile, netProcTime,
				bgpProcTime);
		this.buildTrafficAccountant(attackPacketSize);
		this.buildBotMaster(botStyle, botConfig);
		this.doBackLinking();

	}

	/**
	 * Builds the AS map, this function should be the first buildXXXXX to be
	 * called since most of the other construction needs a ref to the AS map
	 * 
	 * @param networkSize
	 *            - the number of ASes in the network
	 */
	private void buildNetworks(boolean debugMode, HashMap<String, String> networkLinkSize, String asDataFile) {
		// currently set to something different from default distribution since
		// we're dealing with tiny networks (~5 nodes)

		if (debugMode) {
			SimpleTieredBuilder builder = new SimpleTieredBuilder(5);
			this.asMap = builder.connectGraph(SimpleTieredBuilder.AUCTION_BLIND_AUTOACCEPT);
		} else {
			RealTopology builder = new RealTopology(asDataFile, false, networkLinkSize.get(RealTopology.T3LINKPARAM),
					networkLinkSize.get(RealTopology.T2T2LINKPARAM), networkLinkSize.get(RealTopology.T2T1LINKPARAM),
					networkLinkSize.get(RealTopology.T1T1LINKPARAM));
			this.asMap = builder.getASMap();

			/*
			 * build the network to AS mapping
			 */
			this.cidrToASMapping = new HashMap<CIDR, Integer>();
			for (Integer tASN : this.asMap.keySet()) {
				for (CIDR tempNet : this.asMap.get(tASN).getLocalNetworks()) {
					this.cidrToASMapping.put(tempNet, tASN);
				}
			}

			System.out.println("starting build of AS weighter");
			try {
				this.asWeighter = new ASIPParse(ASIPParse.RIB_FILE);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(-1);
			}
			System.out.println("done with build of AS weighter");
		}
	}

	/**
	 * Builds the router map.
	 * 
	 * @param freshConfigFiles
	 *            - flag that determines if we build fresh IOS files, or if we
	 *            simply use the current one, if this is set to false, BE SURE
	 *            THERE ARE IOS FILES IN THE CORRECT DIR
	 */
	private void buildRouters(boolean freshConfigFiles, String largeRouterConfig, String smallRouterConfig,
			int largeCutoff, String serialFile, int netProcTime, double bgpProcTime) {
		String configFile;
		ASConfigGenerator configGen = new ASConfigGenerator(largeRouterConfig, smallRouterConfig, largeCutoff);

		this.routerMap = new HashMap<Integer, Router>();

		if (serialFile.equalsIgnoreCase("null")) {
			serialFile = null;
		}

		if (serialFile == null) {

			/*
			 * steps through each AS, creating a router for each, this of course
			 * requires a config file
			 * 
			 * if freshConfigFiles is true then we'll generate fresh config
			 * files
			 * 
			 * if it is false, then we'll just figure out the file name, this
			 * allows you to tinker with the IOS files, BE SURE THAT THEY EXIST
			 * AND THAT THERE IS STILL ONE FOR EACH ROUTER
			 */
			for (int tASN : this.asMap.keySet()) {
				try {
					if (freshConfigFiles) {
						configFile = configGen.createConfigFile(this.asMap.get(tASN));
					} else {
						configFile = configGen.getConfigFileName(this.asMap.get(tASN));
					}
					this.routerMap.put(tASN, new Router(configFile, this.logger, netProcTime, bgpProcTime,
							this.cidrToASMapping, this.asWeighter.getASWeighting()));
				} catch (IOException e) {
					/*
					 * A router missing it's config file is fatal, yell and
					 * die...
					 */
					e.printStackTrace();
					System.exit(-1);
				}
			}
		} else {
			this.usedSerialString = true;
			try {
				BufferedReader serialBuff = new BufferedReader(new FileReader(serialFile));
				this.serialStub = serialBuff.readLine();

				while (serialBuff.ready()) {
					String poll = serialBuff.readLine();
					StringTokenizer topToken = new StringTokenizer(poll, "&");
					int tASN = Integer.parseInt(topToken.nextToken());

					if (freshConfigFiles) {
						configFile = configGen.createConfigFile(this.asMap.get(tASN));
					} else {
						configFile = configGen.getConfigFileName(this.asMap.get(tASN));
					}
					this.routerMap.put(tASN, new Router(configFile, this.logger, topToken.nextToken(), netProcTime,
							bgpProcTime, this.cidrToASMapping, this.asWeighter.getASWeighting()));
				}

				serialBuff.close();
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}

		configGen.printLargeCount();
	}

	/**
	 * Builds the traffic accountant. Needs to be called after the as map and
	 * router map are created.
	 * 
	 */
	private void buildTrafficAccountant(int attackPacketSize) {
		this.trafficAccountant = new TrafficAccountant(this.asMap, this.routerMap, attackPacketSize);
	}

	private void buildBotMaster(String botStyle, String botConfig) {
		try {
			if (botStyle.equals("velvet hammer")) {
				this.botMaster = new VelvetHammer(this.asMap, this.routerMap, this.trafficAccountant, botConfig);
			} else if (botStyle.equals("null")) {
				this.botMaster = null;
			} else if (botStyle.equals("backhoe")) {
				this.botMaster = new Backhoe(this.asMap, this.routerMap, this.trafficAccountant, botConfig);
			} else {
				throw new IllegalArgumentException("bad bot style: " + botStyle);
			}
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-2);
		}
	}

	/**
	 * Links any mutual dependencies. For example Routers and the TrafficAcct
	 * are mutually dependent on each other, we create the routers first, so we
	 * need to tell them about the TrafficAcct (sigh, god hates tightly linked
	 * code, but sadly this is the way it has to be be at times)
	 */
	private void doBackLinking() {
		// give all the routers a ref to the traffic accountant
		for (Integer tAS : this.routerMap.keySet()) {
			this.routerMap.get(tAS).setTrafficAcct(this.trafficAccountant);
		}
	}

	public String getSerialStart() {
		return this.serialStub;
	}

	public void purgeSerialState() {
		this.serialStub = null;
	}

	public boolean getUsedSerialString() {
		return this.usedSerialString;
	}

	// boring getters from here down...
	public HashMap<Integer, AS> getASMap() {
		return this.asMap;
	}

	public HashMap<Integer, Router> getRouterMap() {
		return this.routerMap;
	}

	public HashMap<CIDR, Integer> getNetworkMapping() {
		return this.cidrToASMapping;
	}

	public TrafficAccountant getTrafficAccountant() {
		return this.trafficAccountant;
	}

	public BotMaster getBotMaster() {
		return this.botMaster;
	}

	public SimLogger getLogStream() {
		return this.logger;
	}

	public ASIPParse getASWeights() {
		return this.asWeighter;
	}
}
