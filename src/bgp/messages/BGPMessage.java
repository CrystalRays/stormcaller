package bgp.messages;

/**
 * Abstract class that wraps all BGP Messages.
 * 
 */
public abstract class BGPMessage implements Comparable<BGPMessage>{

	/**
	 * field that IDs what the message type is
	 */
	private int messageType;

	/**
	 * Field that contains the simulator time stamp (when said message was
	 * created)
	 */
	private int timeStamp;

	/**
	 * Field that contains the src "address". This will not be parsed over into
	 * an internal ID EVER, this will always always always be the src ASN.
	 */
	private int srcASN;

	/**
	 * Sets the fields common to all BGP Messages
	 * 
	 * @param messageType
	 *            - the BGP message type (defined in Constants)
	 */
	public BGPMessage(int messageType, int timeStamp, int srcASN){
		this.messageType = messageType;
		this.timeStamp = timeStamp;
		this.srcASN = srcASN;
	}

	/**
	 * Getter for BGP Message type
	 * 
	 * @return - the type of BGP message this is
	 */
	public int getMessageType() {
		return this.messageType;
	}

	/**
	 * Getter for time stamp
	 * 
	 * @return - the time the message was created & sent
	 */
	public int getTimeStamp() {
		return this.timeStamp;
	}

	/**
	 * Getter for the source ASN, in other words the source "address"
	 * 
	 * @return - the src ASN
	 */
	public int getSrcASN() {
		return this.srcASN;
	}
	
	public int compareTo(BGPMessage rhs){
		if(this.getMessageType() == Constants.BGP_ERROR){
			if(rhs.getMessageType() == Constants.BGP_ERROR){
				return this.getTimeStamp() - rhs.getTimeStamp();
			}
			else{
				return -1;
			}
		}
		else if(this.getMessageType() == Constants.BGP_KEEPALIVE){
			if(rhs.getMessageType() == Constants.BGP_ERROR){
				return 1;
			}
			else if(rhs.getMessageType() == Constants.BGP_KEEPALIVE){
				return this.getTimeStamp() - rhs.getTimeStamp();
			}
			else{
				return -1;
			}
		}
		else if(this.getMessageType() == Constants.BGP_CONNECT){
			if(rhs.getMessageType() == Constants.BGP_UPDATE){
				return -1;
			}
			else if(rhs.getMessageType() == Constants.BGP_CONNECT){
				return this.getTimeStamp() - rhs.getTimeStamp();
			}
			else{
				return 1;
			}
		}
		else{
			if(rhs.getMessageType() == Constants.BGP_UPDATE){
				return this.getTimeStamp() - rhs.getTimeStamp();
			}
			else{
				return 1;
			}
		}
	}
}
