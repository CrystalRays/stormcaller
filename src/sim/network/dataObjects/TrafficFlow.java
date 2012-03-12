package sim.network.dataObjects;

import bgp.dataStructures.CIDR;

public class TrafficFlow {
	
	private int srcAS;
	private int dstAS;
	private CIDR dstNetwork;
	private int size;
	
	
	public TrafficFlow(int srcAS, int dstAS, CIDR dstNetwork, int size) {
		this.srcAS = srcAS;
		this.dstAS = dstAS;
		this.dstNetwork = dstNetwork;
		this.size = size;
	}
	
	public int getSrcAS() {
		return srcAS;
	}
	public int getDstAS() {
		return dstAS;
	}
	public CIDR getDstNetwork(){
		return this.dstNetwork;
	}
	public int getSize() {
		return size;
	}
	
	public String toString(){
		return this.srcAS + ":" + this.dstAS + "(" + this.dstNetwork + ") " + this.size;
	}
	
	public int hashCode(){
		return this.toString().hashCode();
	}
	
	public boolean equals(Object rhs){
		TrafficFlow rhsFlow = (TrafficFlow)rhs;
		return this.dstAS == rhsFlow.dstAS && this.srcAS == rhsFlow.srcAS && this.size == rhsFlow.size;
	}
}
