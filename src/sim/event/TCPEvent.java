package sim.event;

import java.util.List;
import sim.agents.SimAgent;
import sim.network.dataObjects.TCPPacket;

public class TCPEvent extends SimEvent {
	
	private TCPPacket packet;
	private List<TCPPacket> tcpQueue;
	
	public TCPEvent(int time, SimAgent parent, TCPPacket packet, List<TCPPacket> tcpQueue){
		super(SimEvent.TCPSEND, time, parent);
		this.packet = packet;
		this.tcpQueue = tcpQueue;
	}

	public TCPPacket getPacket(){
		return this.packet;
	}
	
	public List<TCPPacket> getTcpQueue(){
		return this.tcpQueue;
	}
}
