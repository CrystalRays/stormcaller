package sim.agents;

import sim.engine.SimDriver;
import sim.event.SimEvent;

/**
 * Interface all active agents in the simulator must implement in order to use
 * the event framework.
 * 
 */
public interface SimAgent {

	/**
	 * Registers the sim driver so the agent can enqueue events.
	 * 
	 * @param theDriver -
	 *            the driver running the simulation
	 */
	public void setSimDriver(SimDriver theDriver);

	/**
	 * Callback function that the sim driver will use to give events.
	 * 
	 * @param theEvent -
	 *            the event that was dequeued and is now valid to be acted on
	 */
	public void giveEvent(SimEvent theEvent);
}
