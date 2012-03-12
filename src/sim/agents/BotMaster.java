package sim.agents;

import java.util.*;
import java.io.*;

import sim.network.dataObjects.*;
import sim.event.*;
import sim.engine.SimDriver;
import sim.util.*;

public abstract class BotMaster implements SimAgent {

	protected HashMap<Integer, Integer> capMap;

	protected TrafficAccountant trafficMgmt;

	protected SimDriver theDriver;

	protected ConfigFileHelper configs;

	public static final String BOTCOUNT = "bot count";
	public static final String BOTSPEED = "bot speed";
	public static final String STARTTIME = "start time";
	public static final String SETUPTIME = "setup time";

	public BotMaster(HashMap<Integer, AS> asMap, TrafficAccountant trafficMgmt, String configFile) throws IOException {
		this.parseConfigFile(configFile);
		this.capMap = this.buildCaps(asMap, Integer.parseInt(this.configs.getValue(BotMaster.BOTCOUNT)));
		this.trafficMgmt = trafficMgmt;
		this.theDriver = null;
	}

	public void setSimDriver(SimDriver theDriver) {
		if (this.theDriver == null) {
			this.theDriver = theDriver;
		}
		this.runInitialSetup();
	}

	public abstract void giveEvent(SimEvent theEvent);

	public abstract void runInitialSetup();

	//TODO use real distribution again
	private HashMap<Integer, Integer> buildCaps(HashMap<Integer, AS> asMap, int botnetSize) {
		int botsPerAS = botnetSize / asMap.keySet().size();
		int botSpeed = Integer.parseInt(this.configs.getValue(BotMaster.BOTSPEED));
		HashMap<Integer, Integer> retMap = new HashMap<Integer, Integer>();

		for (int tASN : asMap.keySet()) {
			retMap.put(tASN, botsPerAS * botSpeed);
		}

		return retMap;
	}

	private void parseConfigFile(String configFile) {
		Set<String> topLevelReqParams = new HashSet<String>();
		topLevelReqParams.add(BotMaster.BOTCOUNT);
		topLevelReqParams.add(BotMaster.BOTSPEED);
		topLevelReqParams.add(BotMaster.SETUPTIME);
		topLevelReqParams.add(BotMaster.STARTTIME);
		this.configs = new ConfigFileHelper(topLevelReqParams);

		try {
			this.configs.parseFile(configFile);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}
	
	public void notifySessionFail(Link failedLink, int time){
		return;
	}
}
