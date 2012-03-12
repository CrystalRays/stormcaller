package bgp.engine;

import java.util.*;
import bgp.dataStructures.Route;

/**
 * Class used to wrap around the route ranking decision.
 * There is really no reason for this to be a class, could just be
 * a static method, but one never knows (maybe logging?).
 *
 */
public class BGPRanker {

	public BGPRanker(){
		//does nothing
	}

	/**
	 * BGP Ranking algorithm that will return the best route from a collection.
	 * We make a few "sane and logical assumptions".
	 * First:  All of the routes in the list are for the same NLRI
	 * Second: All networks are NOT multi homed
	 * Third: The MED attribute is never used
	 * This MUST be able to handle empty lists (should return NULL in that case).
	 * 
	 * returns null if no routes are passed (denoting we no longer have a route to CIDR
	 * 
	 * @param routeList - the list of routes to a given network
	 * @return - the best route as ranked by BGP, or null if there is no route to the "given (not actually given)" network 
	 */
	public Route getBestRoute(List<Route> routeList){
		Route best = null;
		int bestValue = -1;
		boolean bestTie = false;
		List<Route> tieList = new LinkedList<Route>();
		
		if(routeList.size() == 0){
			return null;
		}
		
		//LOCAL_PREF round
		for(Route tempRoute: routeList){
			if(tempRoute.getLocalPref() > bestValue){
				bestValue = tempRoute.getLocalPref();
				bestTie = false;
				tieList.clear();
				tieList.add(tempRoute);
				best = tempRoute;
			}
			else if(tempRoute.getLocalPref() == bestValue){
				bestTie = true;
				tieList.add(tempRoute);
			}
		}
		
		if(!bestTie){
			return best;
		}
		
		//reset after tie, just look at 1st placers
		best = null;
		bestValue = Integer.MAX_VALUE;
		bestTie = false;
		routeList.clear();
		routeList.addAll(tieList);
		tieList.clear();
		
		//MED round would go here
		
		//AS length round
		for(Route tempRoute: routeList){
			/*
			 * important note, the AS path can be empty, but we would only hit
			 * this point with one of those if a local network was multihomed,
			 * but we currently don't do that, if we change that this is going
			 * to BREAK MASSIVELY and we'll need to handle that
			 */			
			if(tempRoute.getAsPath().length < bestValue){
				best = tempRoute;
				bestValue = tempRoute.getAsPath().length;
				bestTie = false;
				tieList.clear();
				tieList.add(tempRoute);
			}
			else if(tempRoute.getAsPath().length == bestValue){
				bestTie = true;
				tieList.add(tempRoute);
			}
		}
		
		if(!bestTie){
			return best;
		}
		
		//reset after tie again
		best = null;
		bestValue = Integer.MAX_VALUE;
		bestTie = false;
		routeList.clear();
		routeList.addAll(tieList);
		tieList.clear();

		/*
		 * hot potato round - implemented out of principle right now since it's
		 * one router per as, but that might change....
		 */
		for(Route tempRoute: routeList){
			if(tempRoute.getOrigin() < bestValue){
				best = tempRoute;
				bestValue = tempRoute.getOrigin();
				bestTie = false;
				tieList.clear();
				tieList.add(tempRoute);
			}
			else if(tempRoute.getOrigin() == bestValue){
				bestTie = true;
				tieList.add(tempRoute);
			}
		}
		
		if(!bestTie){			
			return best;
		}
		
		//reset after tie
		best = null;
		bestValue = Integer.MIN_VALUE;
		bestTie = false;
		routeList.clear();
		routeList.addAll(tieList);
		tieList.clear();
		
		//throw up hands and break tie via largest bgp session ID
		for(Route tempRoute: routeList){
			if(tempRoute.getSrcId() > bestValue){
				best = tempRoute;
				bestValue = tempRoute.getSrcId();
			}
		}
		
		return best;
	}
}
