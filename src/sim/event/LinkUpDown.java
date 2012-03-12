package sim.event;

import java.util.*;

import sim.agents.SimAgent;
import sim.network.dataObjects.*;

public class LinkUpDown extends SimEvent{
	
	private List<Link> upLinks;
	private List<Link> downLinks;
	
	public LinkUpDown(int simTime, SimAgent parent, List<Link> up, List<Link> down){
		super(SimEvent.LINKUPDOWN, simTime, parent);
		this.upLinks = up;
		this.downLinks = down;
	}

	public List<Link> getUpLinks(){
		return this.upLinks;
	}
	
	public List<Link> getDownLinks(){
		return this.downLinks;
	}
	
	public static Link buildCorrectLink(int asn1, int asn2){
		int lhsAS, rhsAS;
		
		/*
		 * Place the smaller as first, so that hash values and equals have meaning
		 */
		if(asn1 < asn2){
			lhsAS = asn1;
			rhsAS = asn2;
		}
		else{
			lhsAS = asn2;
			rhsAS = asn1;
		}
		
		return new Link(new AS(lhsAS, 0), new AS(rhsAS, 0), 0);
	}
}
