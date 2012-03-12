package sim.event;

import sim.agents.SimAgent;

/**
 * Class that represents events in our event driven simulator. This class will
 * store information about what an event is, when it should happen, and who
 * should deal with it.
 * 
 */
public class SimEvent implements Comparable<SimEvent> {

	/*
	 * These event types have the concept of priority attatched, if two events
	 * have the same time, then the event with the smaller numbered type will
	 * occure first. Currently the sim's multi-threading will enforce this
	 * stricly, i.e. all TCPSEND events at time X will happen before ALL
	 * ROUTERCPUFREE events at time X.
	 */

	/**
	 * Signals that the traffic manager must re-run traffic flows
	 */
	public static final int ROUTECHANGE = 2;

	/**
	 * Signals that a link has been killed or brought back via simulator rules
	 * (aka act of god)
	 */
	public static final int LINKUPDOWN = 3;

	/**
	 * Signals that the attack traffic is changing direction
	 */
	public static final int ATTACKFLOW = 4;

	/**
	 * Signals a node to try and send data over the network, i.e. a TCP timer
	 * has expired
	 */
	public static final int TCPSEND = 6;

	/**
	 * Signals a router to check it's timers, as at least one of them might have
	 * expired.
	 */
	public static final int TIMEREXPIRE = 8;

	/**
	 * Signals a router that it's CPU is allowed to compute something and that
	 * is should have something to compute.
	 */
	public static final int ROUTERCPUFREE = 10;

	/**
	 * The type this event is, should be one of the above constants.
	 */
	private int type;

	/**
	 * The time this event will be handed back to it's parent via a callback.
	 */
	private int time;

	/**
	 * The agent that this event is "addressed" to.
	 */
	private SimAgent parent;

	/**
	 * Builds a SimEvent.
	 * 
	 * @param type
	 *            - the event type from the type constants
	 * @param time
	 *            - the time the event will be handed back to it's parent
	 * @param parent
	 *            - the object the event is for, note this can be different from
	 *            the creating object
	 */
	public SimEvent(int type, int time, SimAgent parent) {
		this.type = type;
		this.time = time;
		this.parent = parent;
	}

	/**
	 * Compares two events. First and foremost if one event fires earlier then
	 * the other then it proceeds it. Otherwise we compare the event types. The
	 * type "enum" has the concept of priority built into it, please see those
	 * contansts for more information.
	 */
	public int compareTo(SimEvent arg0) {
		/*
		 * First check if the two events happen at different times, if so then
		 * clearly the earlier one happens first
		 */
		int timeDif = this.time - arg0.time;
		if (timeDif != 0) {
			return timeDif;
		}

		/*
		 * If the two events happen at the same time prioritize by type
		 */
		return this.type - arg0.type;
	}

	/**
	 * Returns what type this event is.
	 * 
	 * @return - the type "enum" of this event
	 */
	public int getType() {
		return this.type;
	}

	/**
	 * Gets the time this event fires.
	 * 
	 * @return - the sim time this event fires and will be handed back to it's
	 *         parent
	 */
	public int getTime() {
		return this.time;
	}

	/**
	 * Returns the event to it's parent via the callback function. The parent
	 * must have the callback function as it has to implement the SimAgent
	 * interface. This callback function should be thread safe, as some/most of
	 * the simulator is/will be multi-threaded.
	 */
	public void dispatch() {
		this.parent.giveEvent(this);
	}
}
