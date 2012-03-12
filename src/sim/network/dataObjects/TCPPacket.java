package sim.network.dataObjects;

import bgp.messages.BGPMessage;

public class TCPPacket {

	private BGPMessage message;
	private int retransInterval;
	private int retransTime;
	private boolean arrived;
	
	private int dstASN;
	
	private static final int TCPSTARTRETRANS = 1000;
	
	public TCPPacket(BGPMessage message, int firstSendTime, int dstASN){
		this.retransInterval = TCPPacket.TCPSTARTRETRANS;
		this.arrived = false;
		this.message = message;
		this.retransTime = firstSendTime;
		this.dstASN = dstASN;
	}
	
	public int getTransTime(){
		return this.retransTime;
	}

	public boolean sendResult(boolean success){
		if(success){
			this.arrived = true;
		}
		else{
			this.retransTime += this.retransInterval;
			this.retransInterval = this.retransInterval * 2;
		}
		
		return success;
	}
	
	public boolean getArrived(){
		return this.arrived;
	}
	
	public BGPMessage getMessage(){
		return this.message;
	}
	
	public int getDst(){
		return this.dstASN;
	}
}
