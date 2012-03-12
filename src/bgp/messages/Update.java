package bgp.messages;

import java.util.*;

import bgp.dataStructures.CIDR;
import bgp.dataStructures.Route;

/**
 * Class used to abstract a BGP update message passed between BGP speakers.  
 * This object will possibly have a collection of NLRIs to withdraw and
 * possibly a new route to advertise.  An update will for sure have at least one
 * of these, it is not required to have both, but it can.  Routes that are
 * being implicitly withdrawn are NOT listed in the withdraws.
 *
 */
public class Update extends BGPMessage{
	
	/**
	 * Currently the AS number of the router that is the src of the Update,
	 * the dst is responsible for converting this to that AS/router's BGP
	 * peer ID.  This will be converted to an internal ID at some point
	 * during update processing.  The srcASN method on the super class will
	 * still return the original ID of the peer.
	 */
	private int srcId;
	
	/**
	 * Set of NLRIs that the router who issued this update is withdrawing.
	 * If no routes are being withdrawn the set will exist but, be empty.
	 */
	private Set<CIDR> withdraws;
	
	/**
	 * A new route advertised by the router that is the src of the update.
	 * If no new route is advertised this will be NULL.
	 */
	private Route advertised;
	
	/**
	 * Constructs an empty Update message with the given srcId.
	 * The resulting object will be packed with withdraws and/or a new route.
	 * 
	 * @param srcId - currently the AS that is generating this update
	 * @param timeStamp - the time the update is created & sent
	 */
	public Update(int srcId, int timeStamp){
		super(Constants.BGP_UPDATE, timeStamp, srcId);
		
		this.srcId = srcId;
		this.withdraws = new HashSet<CIDR>();
		this.advertised = null;
	}
	
	/**
	 * Changes the srcId from the foreign host's (src) id (currently ASN) to the local (dst) srcId.
	 * Changes both the Update's srcId and the srcId in any newly advertised route if it exists. 
	 * 
	 * @param localSrcId - the BGP peer ID that we received this update from
	 */
	public void setSrcId(int localSrcId){
		this.srcId = localSrcId;
		
		if(this.advertised != null){
			this.advertised.setSrcId(localSrcId);
		}
	}
	
	/**
	 * Getter for srcId.
	 * 
	 * @return - the srcId for this update
	 */
	public int getSrcId(){
		return this.srcId;
	}
	
	/**
	 * Adds a CIDR to the withdrawn set.
	 * If the CIDR is already in the set it will not be added.
	 * 
	 * @param withdrawnRoute - the CIDR to withdraw
	 */
	public void addWithdraw(CIDR withdrawnRoute){
		this.withdraws.add(withdrawnRoute);
	}
	
	/**
	 * Getter for the set of CIDRs to withdraw.
	 * 
	 * @return - a reference to the set of withdrawn records for this update
	 */
	public Set<CIDR> getWithdraws(){
		return this.withdraws;
	}
	
	/**
	 * Sets the advertised route for this update to the given route.
	 * Each update can contain at most one update, if a route is already
	 * packed into the Update, then this route will overwrite that route.
	 * The srcId of the route is synched with the srcId of the Update object.
	 * The route is actually a copy so that changes in one router do not affect
	 * other routers.
	 * 
	 * @param advertised - the route we wish to copy and advertise
	 */
	public void setAdvertised(Route advertised){
		this.advertised = advertised.copy();
		this.advertised.setSrcId(this.srcId);
	}
	
	/**
	 * Getter that fetches a copy of the newly advertised route.
	 * 
	 * @return - the advertised route, or NULL if this update does not contain one
	 */
	public Route getAdvertised(){
		//TODO we had a second copy here, was that right?
		return this.advertised;
	}
	
	public void clearAdvertised(){
		this.advertised = null;
	}
}
