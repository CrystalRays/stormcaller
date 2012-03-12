package bgp.messages;

import java.security.InvalidParameterException;

/**
 * Message type used to start up a BGP connection between peers.
 */
public class Connect extends BGPMessage {

	/**
	 * Stores which part of the three way handshake we're on.
	 */
	private int pass;

	/**
	 * Creates a connect message of the given phase.
	 * 
	 * @param srcASN
	 *            - the asn sending the connect message
	 * @param time
	 *            - the current wall time
	 * @param pass
	 *            - which phase (SYN, SYN/ACK, or ACK) we are at
	 */
	public Connect(int srcASN, int time, int pass) {
		super(Constants.BGP_CONNECT, time, srcASN);
		
		/*
		 * Make sure the pass is correct
		 */
		if(pass < 1 || pass > 3){
			throw new InvalidParameterException("Incorrect connect pass: " + pass);
		}
		
		this.pass = pass;
	}

	/**
	 * Tests if this is a SYN packet.
	 * 
	 * @return true if this is a SYN packet
	 */
	public boolean isSyn() {
		return this.pass == 1;
	}

	/**
	 * Tests if this is a SYN/ACK packet.
	 * 
	 * @return true if this is a SYN/ACK packet
	 */
	public boolean isSynAck() {
		return this.pass == 2;
	}

	/**
	 * Tests if this is aN ACK packet.
	 * 
	 * @return true if this is aN ACK packet
	 */
	public boolean isAck() {
		return this.pass == 3;
	}

}
