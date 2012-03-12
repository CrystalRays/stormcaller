package sim.agents.attackers;

import java.util.*;
import java.io.*;

import sim.agents.BotMaster;
import sim.agents.TrafficAccountant;
import sim.event.*;
import sim.network.dataObjects.*;

public class Slammer extends BotMaster {

	private HashMap<Integer, AS> asMap;

	public Slammer(HashMap<Integer, AS> asMap, TrafficAccountant trafficMgmt, String configFile) throws IOException {
		super(asMap, trafficMgmt, configFile);
		this.asMap = asMap;
	}

	public void giveEvent(SimEvent theEvent) {
		throw new IllegalArgumentException("Slammer doesn't answer to events");
	}

	public void runInitialSetup() {
		int subDivide = -1;
		List<TrafficFlow> attackFlow = new LinkedList<TrafficFlow>();

		for (int tTasked : this.asMap.keySet()) {
			if (subDivide == -1) {
				subDivide = this.capMap.get(tTasked) / (this.asMap.keySet().size() - 1);
			}
			for (int tTargeted : this.asMap.keySet()) {
				if (tTargeted == tTasked) {
					continue;
				}
				attackFlow
						.add(new TrafficFlow(tTasked, tTargeted, this.asMap.get(tTargeted).getAnyNetwork(), subDivide));
			}
		}

		this.theDriver.postEvent(new AttackEvent(Integer.parseInt(this.configs.getValue(BotMaster.STARTTIME)),
				this.trafficMgmt, attackFlow, null));
	}

}
