package bgp.messages;

/**
 * Very very simple class that wraps a BGP message turning it into a keep-alive.
 * There isn't really anything else this class needs to do, so it's just an
 * empty message really.
 * 
 */
public class KeepAlive extends BGPMessage {

	/**
	 * Lawl, easy constructor is easy....
	 * 
	 * @param srcAS -
	 *            the AS this keepalive comes from
	 * @param time -
	 *            the current wall time
	 */
	public KeepAlive(int srcAS, int time) {
		super(Constants.BGP_KEEPALIVE, time, srcAS);
	}

}
