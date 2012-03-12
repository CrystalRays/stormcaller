package bgp.messages;

/**
 * BGP error message, carries a string right now, since we just log the reason
 * and take the same action no matter what the error actually is.
 * 
 */
public class Error extends BGPMessage {

	/**
	 * The error reason that we will log
	 */
	private String errorReason;

	/**
	 * Creates an error message with the given error reason.
	 * 
	 * @param srcASN -
	 *            the source AS the error is coming from
	 * @param time -
	 *            the current wall time
	 * @param errorReason -
	 *            the error message, not acted on programatically
	 */
	public Error(int srcASN, int time, String errorReason) {
		super(Constants.BGP_ERROR, time, srcASN);
		this.errorReason = errorReason;
	}

	/**
	 * Gets the error message.
	 * 
	 * @return - the error message for the error this packet is reporting
	 */
	public String getReason() {
		return this.errorReason;
	}

}
