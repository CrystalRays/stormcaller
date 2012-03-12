package sim.agents.attackers;

import java.util.*;

/**
 * Helper struct that wraps information about what direction is the best to
 * attack a link from. Shouldn't really be passed around too much, just used by
 * bot code to return values cleanly.
 * 
 */
public class TaskingTuple {

	private int attackDest;
	private long maxAttackVolume;
	private Set<Integer> maxDirASes;

	/**
	 * Builds an immutable tuple holding the destination of the attack, the
	 * amount of traffic that can go there, and a set of all ASes who can reach
	 * that destination in a meaningful way.
	 * 
	 * @param maxDest -
	 *            the AS the attack flow should be targeted for
	 * @param amount -
	 *            the total amount of traffic that can be tasked for that
	 *            destination
	 * @param asesGoingThisWay -
	 *            the ASes that contributed bandwidth to the amount value
	 */
	public TaskingTuple(int maxDest, long amount, Set<Integer> asesGoingThisWay) {
		this.attackDest = maxDest;
		this.maxAttackVolume = amount;
		this.maxDirASes = asesGoingThisWay;
	}

	/**
	 * Gets the destination AS for the attack flow. Note that this is the
	 * OPPOSITE AS from what was normally considered the "direction" of the
	 * attack (i.e. this is the second AS of the pair being attacked that is
	 * seen by traffic).
	 * 
	 * @return - the AS to send traffic to.
	 */
	public int getAttackDestination() {
		return this.attackDest;
	}

	/**
	 * Gets the total amount of traffic that can be tasked across this link.
	 * This value DOES NOT take into account flow congestion.
	 * 
	 * @return - the amount of attack traffic that can reach the link
	 */
	public long getMaxAttackVolume() {
		return maxAttackVolume;
	}

	/**
	 * All ASes that contributed to the maxAttackVolume figure.
	 * 
	 * @return - the set of all ASes with avail bandwidth that can cross the
	 *         link in the correct direction.
	 */
	public Set<Integer> getMaxDirASes() {
		return maxDirASes;
	}
}
