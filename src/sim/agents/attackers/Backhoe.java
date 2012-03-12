package sim.agents.attackers;

import java.io.*;
import java.util.*;

import bgp.dataStructures.CIDR;
import bgp.dataStructures.Route;

import sim.agents.*;
import sim.event.*;
import sim.logging.ASIPParse;
import sim.network.dataObjects.*;

public class Backhoe extends VelvetHammer {

	private List<Link> currentAttack;
	private int restartDelay;
	private int cycleCount;
	private int failTime;

	private boolean rogueAS;

	private static final String RESTART = "restart";
	private static final String CYCLES = "cycles";
	private static final String FAILTIME = "failtime";
	private static final String MODE = "mode";

	private static final String MODE_ROGUE = "rogue";
	private static final String MODE_VELVET = "velvet";

	private static final String SKIPCOUNT = "skip";
	private static final String USE_BETWEEN = "between";

	public Backhoe(HashMap<Integer, AS> asMap, HashMap<Integer, Router> routerMap, TrafficAccountant tMgmt,
			String configFile) throws IOException {
		super(asMap, routerMap, tMgmt, configFile);

		HashSet<String> myParams = new HashSet<String>();
		myParams.add(Backhoe.RESTART);
		myParams.add(Backhoe.CYCLES);
		myParams.add(Backhoe.FAILTIME);
		myParams.add(Backhoe.MODE);

		try {
			super.configs.validateAddtionalRequiredParams(myParams);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
		this.restartDelay = super.configs.getIntegerValue(Backhoe.RESTART);
		this.cycleCount = super.configs.getIntegerValue(Backhoe.CYCLES);
		this.failTime = super.configs.getIntegerValue(Backhoe.FAILTIME);

		String modeStr = super.configs.getValue(Backhoe.MODE);
		this.rogueAS = false;
		if (modeStr.equals(Backhoe.MODE_ROGUE)) {
			this.rogueAS = true;
		} else if (modeStr.equals(Backhoe.MODE_VELVET)) {
			//sets nothing
		} else {
			System.err.println("invalid mode! " + modeStr);
			System.exit(-1);
		}
	}

	public void giveEvent(SimEvent inEvent) {

		
		if (inEvent.getType() != SimEvent.TIMEREXPIRE) {
			System.err.println("non timer expire event to Backhoe!");
			System.exit(-1);
		}

		if (!this.ranNetworkProbe) {
			this.bgpFlux();
			System.exit(0);
			this.probeNetwork();
			if (this.timeSlicing) {
				System.err.println("backhoe shouldn't try time slicing!");
				System.exit(-1);
				this.computeSlicingTasking();
			} else if (this.rogueAS) {
				int tarAS = this.selectRogueAS();
				this.currentAttack = this.getFullTargetSet(tarAS);
			} else {
				HashSet<Link> targetLinks = new HashSet<Link>();
				this.computeAllOrNothingTasking(targetLinks);
				this.currentAttack = new LinkedList<Link>();

				/*
				 * Parse the targeted links into the correct form
				 */
				for (Link tLink : targetLinks) {
					this.currentAttack.add(LinkUpDown.buildCorrectLink(tLink.getASes()[0].getASNumber(), tLink
							.getASes()[1].getASNumber()));
				}
			}
			
			/*
			 * Since we know the schedule "exactly", just put in all events
			 * at once
			 */
			int currentTime = super.configs.getIntegerValue(VelvetHammer.STARTTIME);
			for (int counter = 0; counter < this.cycleCount; counter++) {
				super.theDriver.postEvent(new LinkUpDown(currentTime, super.trafficMgmt, null, this.currentAttack));
				super.theDriver.postEvent(new LinkUpDown(currentTime + this.failTime, super.trafficMgmt,
						this.currentAttack, null));
				currentTime += this.failTime + this.restartDelay;
			}
			
		} else if (this.timeSlicing) {
			System.err.println("velvet hammer & backhoe are not very adaptive yet...");
			//this.launchNextPulse();
		} else {
			System.err.println("too many events to backhoe");
			System.exit(-2);
		}

	}

	public void bgpFlux() {
		HashMap<Integer, HashMap<Integer, Long>> fluxMap = new HashMap<Integer, HashMap<Integer, Long>>();
		ASIPParse asWeighter = null;
		try{
			asWeighter = new ASIPParse(ASIPParse.RIB_FILE);
		}
		catch(IOException e){
			e.printStackTrace();
		}

		for (int tASN : this.routerMap.keySet()) {
			fluxMap.put(tASN, new HashMap<Integer, Long>());
		}

		for (int tASN : this.routerMap.keySet()) {
			Router tRouter = this.routerMap.get(tASN);

			for (int tDest : this.asMap.keySet()) {
				if (tDest == tASN) {
					continue;
				}
				CIDR net = this.asMap.get(tDest).getAnyNetwork();
				Route tRoute = tRouter.getRoute(net);
				if(tRoute == null){
					continue;
				}
				
				Integer mass = asWeighter.getASWeighting().get(tDest);
				if(mass == null){
					mass = 1;
				}
				int path[] = tRoute.getAsPath();
				for (int counter = 0; counter < (path.length - 2); counter++) {
					int lhs = path[counter];
					int rhs = path[counter + 1];
					if (this.asMap.get(rhs).getProviders().contains(this.asMap.get(lhs))
							|| this.asMap.get(rhs).getPeers().contains(this.asMap.get(lhs))) {
						Long current = fluxMap.get(rhs).get(tASN);
						if (current == null) {
							current = new Long(0);
						}
						current += mass;
						fluxMap.get(rhs).put(tASN, current);
					}
				}
			}
		}

		try {
			BufferedWriter out = new BufferedWriter(new FileWriter("flux.csv"));
			for(int tASN: fluxMap.keySet()){
				List<Long> tList = new ArrayList<Long>();
				for(int tOther: fluxMap.get(tASN).keySet()){
					tList.add(fluxMap.get(tASN).get(tOther));
				}
				if(tList.size() == 0){
					continue;
				}
				
				Collections.sort(tList);
				
				long sum = 0;
				for(long tVal: tList){
					sum += tVal;
				}
				int above = 0;
				for(int counter = 0; counter < tList.size(); counter++){
					if(tList.get(counter) >= 120000){
						above = tList.size() - counter;
						break;
					}
				}
				
				out.write("" + tASN + "," + sum + "," + tList.size() + "," + tList.get(tList.size()/2) + "," + above + "\n");
			}
			
			out.close();
			
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private int selectRogueAS() {
		int skipCount = super.configs.getIntegerValue(Backhoe.SKIPCOUNT);
		boolean useBetweenness = super.configs.getBooleanValue(Backhoe.USE_BETWEEN);

		int winningAS = -1;
		int winningDegree = -1;
		int winningBet = -1;
		HashSet<Integer> seenASes = new HashSet<Integer>();

		for (int counter = 0; counter < skipCount + 1; counter++) {
			int currAS = -1;
			int maxDeg = -1;
			int maxBet = -1;

			for (int tAS : super.asMap.keySet()) {
				/*
				 * skip it if we've already placed it
				 */
				if (seenASes.contains(tAS)) {
					continue;
				}

				boolean best = false;
				int myDeg = super.asMap.get(tAS).getDegree();
				int myBet = 0;
				for(AS tNei: super.asMap.get(tAS).getAllNeighbors()){
					Double tVal = super.loadMap.get(super.asMap.get(tAS).getLinkToNeighbor(tNei));
					if(tVal != null){
						myBet += tVal.intValue(); 
					}
				}

				if (useBetweenness) {
					best = myBet > maxBet;
				} else {
					best = myDeg > maxDeg;
				}

				if (best) {
					currAS = tAS;
					maxDeg = myDeg;
					maxBet = myBet;
				}
			}

			winningAS = currAS;
			winningDegree = maxDeg;
			winningBet = maxBet;
			seenASes.add(winningAS);
		}

		System.out.println("winning asn: " + winningAS + " deg = " + winningDegree + " bet = " + winningBet);
		return winningAS;
	}

	//XXX what if we do all at once vs stagering?
	private List<Link> getFullTargetSet(int as) {
		List<Link> retList = new LinkedList<Link>();

		for (AS tAS : super.asMap.get(as).getAllNeighbors()) {
			retList.add(LinkUpDown.buildCorrectLink(as, tAS.getASNumber()));
		}

		return retList;
	}
}
