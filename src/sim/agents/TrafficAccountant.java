package sim.agents;

import java.util.*;

import bgp.dataStructures.Route;

import sim.engine.SimDriver;
import sim.event.*;
import sim.network.dataObjects.*;

/**
 * Manages all traffic comings and goings.
 * 
 */
public class TrafficAccountant implements SimAgent {

	/**
	 * List of current attack flows that are active.
	 */
	private List<TrafficFlow> flows;

	/**
	 * Stores the amount of data attempting to go from one AS to a second AS
	 */
	private HashMap<Integer, HashMap<Integer, Integer>> outboundContention;

	/**
	 * Stores the amount of data attempting to come in from one AS to a second
	 * AS
	 */
	private HashMap<Integer, HashMap<Integer, Integer>> inboundContention;

	/**
	 * Map of AS objects used in this simulation, indexed by ASN. This is
	 * currently only used since each AS object stores references to the links
	 * it uses.
	 */
	private HashMap<Integer, AS> asMap;

	/**
	 * Map of Router objects used in this simulation, indexed by ASN.
	 */
	private HashMap<Integer, Router> routerMap;

	/**
	 * Map that stores message chances since we only want to compute them once
	 */
	private HashMap<Integer, HashMap<Integer, Double>> messageChance;

	/**
	 * TODO doc
	 */
	private HashSet<Link> killedLinks;

	/**
	 * Random number gen used for coin flip
	 */
	private Random rng;

	/**
	 * Sim framework we get and give events from and to.
	 */
	private SimDriver theDriver;

	/**
	 * Boolean flag for it we're currently scheduled to re-run flows because of
	 * route changes
	 */
	private boolean routesRefreshing;

	/**
	 * Number of bits in attack packets. (128 bits (16 Bytes) should be
	 * standard)
	 */
	private int attackSize;

	/**
	 * Number of bits in "line speed" lookup. (64 Bytes)
	 */
	private static final int LOOKUPSIZE = 512;

	/**
	 * Creates an accountant that governs the traffic between the given set of
	 * ASes, connected with the given set of routers.
	 * 
	 * @param asMap
	 *            - map of networks in this simulator
	 * @param routerMap
	 *            - map of routers in this simulator
	 * @param attackSize
	 *            - the size in bits of attack packets
	 */
	public TrafficAccountant(HashMap<Integer, AS> asMap, HashMap<Integer, Router> routerMap, int attackSize) {
		/*
		 * Create new empty tables
		 */
		this.flows = new LinkedList<TrafficFlow>();
		this.outboundContention = new HashMap<Integer, HashMap<Integer, Integer>>();
		this.inboundContention = new HashMap<Integer, HashMap<Integer, Integer>>();
		this.messageChance = new HashMap<Integer, HashMap<Integer, Double>>();

		/*
		 * Refs to sim tables
		 */
		this.routerMap = routerMap;
		this.asMap = asMap;

		/*
		 * Objects needed for traffic dynamics
		 */
		this.attackSize = attackSize;
		this.rng = new Random();

		/*
		 * Event objects
		 */
		this.theDriver = null;
		this.routesRefreshing = false;

		/*
		 * Setup any internal maps since I hear null pointers are fail
		 */
		for (AS tAS : this.asMap.values()) {
			this.messageChance.put(tAS.getASNumber(), new HashMap<Integer, Double>());
			this.outboundContention.put(tAS.getASNumber(), new HashMap<Integer, Integer>());
			this.inboundContention.put(tAS.getASNumber(), new HashMap<Integer, Integer>());
		}

		this.killedLinks = new HashSet<Link>();
	}

	public void setSimDriver(SimDriver inDriver) {
		if (this.theDriver == null) {
			this.theDriver = inDriver;
		}
	}

	/**
	 * Predicate that does a weighted coin flip to determine if router messages
	 * get through.
	 * 
	 * @param srcASN
	 *            - the source router
	 * @param destASN
	 *            - the destination router
	 * @return - true if the message gets through, false if it does not
	 */
	public boolean routerMessageWorks(int srcASN, int destASN) {
		double roll;
		double chance = -1.0;

		/*
		 * we need to get the map for the dest, since that is the place where
		 * we're worried about dropping
		 */
		if (this.messageChance.get(srcASN).containsKey(destASN)) {
			chance = this.messageChance.get(srcASN).get(destASN);
		}

		/*
		 * Check if the message is sure to get through, this will occure if the
		 * chance is 1.0 or if the chance was never initialized (i.e. there is
		 * no message chance put in the table for it)
		 */
		if (chance == 1.0 || chance < 0.0) {
			return true;
		}
		if (chance == 0.0) {
			return false;
		}

		/*
		 * weighted coin flip
		 */
		roll = this.rng.nextDouble();
		if (roll < chance) {
			return true;
		}

		return false;
	}

	public void giveEvent(SimEvent inEvent) {
		/*
		 * The attack flow event indicates that traffic flows have changed, the
		 * event should be converted into an AttackEvent and the relevent
		 * details extracted
		 */
		if (inEvent.getType() == SimEvent.ATTACKFLOW) {
			AttackEvent inAttack = (AttackEvent) inEvent;
			/*
			 * Remove all expired flows
			 */
			if (inAttack.getExpiredAttackFlows() != null) {
				this.flows.removeAll(inAttack.getExpiredAttackFlows());
			}
			/*
			 * Add all new flows
			 */
			if (inAttack.getNewAttackFlows() != null) {
				for (TrafficFlow tFlow : inAttack.getNewAttackFlows()) {
					this.flows.add(tFlow);
				}
			}

			/*
			 * If we had to clear out flows run the whole thing again, otherwise
			 * just run the new flows
			 */
			if (inAttack.getExpiredAttackFlows() != null) {
				this.resetCircuits();
				for (TrafficFlow tFlow : this.flows) {
					this.runFlow(tFlow);
				}
			} else {
				for (TrafficFlow tFlow : inAttack.getNewAttackFlows()) {
					this.runFlow(tFlow);
				}
			}
		}
		/*
		 * ROUTECHANGE means that the underlying network topology has changed,
		 * so we get to re-run all flows...yay...
		 */
		else if (inEvent.getType() == SimEvent.ROUTECHANGE) {
			this.resetCircuits();
			for (TrafficFlow tFlow : this.flows) {
				this.runFlow(tFlow);
			}
			this.routesRefreshing = false;
		} else if (inEvent.getType() == SimEvent.LINKUPDOWN) {
			LinkUpDown upsAndDowns = (LinkUpDown) inEvent;

			if (upsAndDowns.getUpLinks() != null) {
				this.killedLinks.removeAll(upsAndDowns.getUpLinks());
			}
			if (upsAndDowns.getDownLinks() != null) {
				this.killedLinks.addAll(upsAndDowns.getDownLinks());
			}

			this.resetCircuits();
			for (TrafficFlow tFlow : this.flows) {
				this.runFlow(tFlow);
			}
			this.routesRefreshing = false;
		} else {
			System.err.println("bad event type to TA: " + inEvent.getType());
		}
	}

	/**
	 * Callback that routers use in order to inform the accountant of a topology
	 * change
	 * 
	 */
	public void informRouteChange() {
		/*
		 * Check if we've already scheduled a refresh, if we have not then do
		 * so, schedule it for now + epsilon (1 ms in this case), that way we
		 * only do one refresh per set of changes
		 */
		if (!this.routesRefreshing) {
			this.routesRefreshing = true;
			this.theDriver.postEvent(new SimEvent(SimEvent.ROUTECHANGE, this.theDriver.getCurrentTime() + 1, this));
		}
	}

	/**
	 * Clears all link usages and message chances
	 * 
	 */
	private void resetCircuits() {
		for (int tASN : this.messageChance.keySet()) {
			this.messageChance.get(tASN).clear();
			this.outboundContention.get(tASN).clear();
			this.inboundContention.get(tASN).clear();
		}

		/*
		 * remove the chance for all links that have been killed
		 */
		for (Link tLink : this.killedLinks) {
			AS pairOfAS[] = tLink.getASes();
			this.messageChance.get(pairOfAS[0].getASNumber()).put(pairOfAS[1].getASNumber(), 0.0);
			this.messageChance.get(pairOfAS[1].getASNumber()).put(pairOfAS[0].getASNumber(), 0.0);
		}
	}

	/**
	 * Actually runs a single traffic flow through the network. To some degree
	 * this is un-realistic since flow that are run earlier get priority, but
	 * short of using kinetic data structures I fail to see an easy way around
	 * this.
	 * 
	 * @param currentFlow
	 *            - the attack flow we are running
	 */
	private void runFlow(TrafficFlow currentFlow) {
		int currentAS = currentFlow.getSrcAS();
		int currentAmount = currentFlow.getSize();
		int currentPacketCount = currentFlow.getSize() / this.attackSize;
		int avilResource;
		int nextHop;
		Route tempRoute;
		Link nextLink;

		/*
		 * Walk through each step in the path, consuming availible bandwidth at
		 * each step
		 */
		while (currentAS != currentFlow.getDstAS() && currentAmount > 0) {
			/*
			 * Get the route for the destination, if we don't have one then the
			 * network is currently un-reachable by us therefore the traffic is
			 * simply dropped here, otherwise get the next hop
			 */
			tempRoute = this.routerMap.get(currentAS).getRoute(currentFlow.getDstNetwork());
			if (tempRoute == null) {
				break;
			}
			nextHop = tempRoute.getNextHop();
			nextLink = this.asMap.get(currentAS).getLinkToNeighbor(nextHop);

			/*
			 * Compute the amount of contention on the link, using this compute
			 * the amount of traffic that flows through the link
			 */
			/*
			 * XXX so right now this function isn't quite as realistic as we
			 * would like. Basically is a flow is placed toward the front of the
			 * list it gets some amount of priority. This seems a little wrong,
			 * but it is the far and away simplest way to write this function.
			 * Nick had mentioned something about kinetic data structures that
			 * might be a bit more realistic about this, but I have not looked
			 * at it yet. The other solution is to run some kind of breadth
			 * first flow algorithm, but even that isn't really right, might be
			 * something to look at when we have a lot more free time...
			 */
			if (this.outboundContention.get(currentAS).containsKey(nextHop)) {
				avilResource = Math
						.max(0, nextLink.getCapacity() - this.outboundContention.get(currentAS).get(nextHop));
				this.outboundContention.get(currentAS).put(nextHop,
						this.outboundContention.get(currentAS).get(nextHop) + currentAmount);
			} else {
				avilResource = nextLink.getCapacity();
				this.outboundContention.get(currentAS).put(nextHop, currentAmount);
			}
			currentAmount = Math.min(currentAmount, avilResource);

			/*
			 * Update the packet count and do the same game with packets and
			 * forwarding cap
			 */
			currentPacketCount = currentAmount / this.attackSize;
			if (this.inboundContention.get(currentAS).containsKey(nextHop)) {
				avilResource = Math.max(0,
						((nextLink.getCapacity() / TrafficAccountant.LOOKUPSIZE) - this.inboundContention
								.get(currentAS).get(nextHop)));
				this.inboundContention.get(currentAS).put(nextHop,
						this.inboundContention.get(currentAS).get(nextHop) + currentPacketCount);
			} else {
				avilResource = nextLink.getCapacity() / TrafficAccountant.LOOKUPSIZE;
				this.inboundContention.get(currentAS).put(nextHop, currentPacketCount);
			}
			currentPacketCount = Math.min(currentPacketCount, avilResource);

			/*
			 * Update the message chance chart
			 */
			this.updateMessageChance(currentAS, nextHop, nextLink);

			/*
			 * If the next hop isn't allowing transit then we can simply drop
			 * the data, resources are still consumed as policies are applied
			 * after the bandwidth and lookup rate is expended
			 */
			if (!this.routerMap.get(nextHop).acceptTraffic(currentFlow.getDstNetwork(), currentAS)) {
				break;
			}

			/*
			 * convert packets back into bits and update the next hop
			 */
			currentAmount = currentPacketCount * this.attackSize;
			currentAS = nextHop;
		}
	}

	/**
	 * Computes the chance a BGP message will make it to its destination given
	 * the current load on outgoing and incoming resources.
	 * 
	 * @param srcAS
	 *            - the point a message is originating from
	 * @param dstAS
	 *            - the point a message is address for
	 * @param usedLink
	 *            - the link the message is carried over (for capacities)
	 */
	private void updateMessageChance(int srcAS, int dstAS, Link usedLink) {

		/*
		 * If the link is already down, ignore the rest of this
		 */
		if (this.messageChance.get(srcAS).get(dstAS) == 0.0) {
			return;
		}

		/*
		 * Compute the fraction of traffic that is going to actually get through
		 * given how much traffic is trying to get through and avil resouces
		 */
		double outboundOver = Math.min(1.0, (double) usedLink.getCapacity()
				/ (double) this.outboundContention.get(srcAS).get(dstAS));
		double inboundOver = Math.min(1.0, (double) (usedLink.getCapacity() / TrafficAccountant.LOOKUPSIZE)
				/ (double) this.inboundContention.get(srcAS).get(dstAS));

		/*
		 * Compute the chance of getting through at both points
		 */
		this.messageChance.get(srcAS).put(dstAS, outboundOver * inboundOver);
	}
}
