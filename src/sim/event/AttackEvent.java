package sim.event;

import java.util.*;
import sim.agents.SimAgent;
import sim.network.dataObjects.TrafficFlow;

public class AttackEvent extends SimEvent {
	
	private List<TrafficFlow> newAttackFlows;
	private List<TrafficFlow> oldAttackFlows;

	public AttackEvent(int time, SimAgent parent, List<TrafficFlow> newAttackFlows, List<TrafficFlow> oldAttackFlows) {
		super(SimEvent.ATTACKFLOW, time, parent);
		this.newAttackFlows = newAttackFlows;
		this.oldAttackFlows = oldAttackFlows;
	}
	
	public List<TrafficFlow> getNewAttackFlows(){
		return this.newAttackFlows;
	}
	public List<TrafficFlow> getExpiredAttackFlows(){
		return this.oldAttackFlows;
	}

}
