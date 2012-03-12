package sim.logging;

/**
 * Really just a resource file for string so we can build parsers easily.
 * 
 */
public abstract class LoggingMessages {

	/*
	 * Shared messages
	 */
	//public static final String TO = " to ";
	public static final String TO = "t";
	//public static final String SIZE = " size: ";
	public static final String SIZE = "s";
	//public static final String AT = " at ";
	public static final String AT = "a";
	//public static final String FROM = " from ";
	public static final String FROM = "f";
	//public static final String ON = " on ";
	public static final String ON = "o";

	/*
	 * TrafficAccountant strings
	 */
	//public static final String TRAFFIC_LOSS = "Loss: ";
	public static final String TRAFFIC_LOSS = "L";
	public static final String TRAFFIC_ARRIVED = "Arrive: ";
	//public static final String LEGIT = " Legit ";
	public static final String LEGIT = "l";
	public static final String ATTACK = " Attack ";
	//public static final String BANDWIDTH = " bandwidth";
	public static final String BANDWIDTH = "b";
	//public static final String LOOKUP = " lookup";
	public static final String LOOKUP = "u";
	//public static final String NO_TRANSIT = " not allowing traffic";
	public static final String NO_TRANSIT = "n";

	/*
	 * Router messages
	 */
	//MESSSAGE (msg type) + TO + (dest ASN) + FROM + (src ASN)
	public static final String MESSAGE_GEN_PATTERN = LoggingMessages.MESSAGE_GEN + "(\\d++)" + LoggingMessages.TO
			+ "(\\d++)" + LoggingMessages.FROM + "(\\d++)";
	public static final String MESSAGE_ARRIVE_PATTERN = LoggingMessages.MESSAGE_ARRIVE + "(\\d++)" + LoggingMessages.TO
			+ "(\\d++)" + LoggingMessages.FROM + "(\\d++)" + LoggingMessages.ON + "([0-9\\./]++)";

	/*
	 * Router strings
	 */
	//public static final String MESSAGE = "BGP Message type ";
	public static final String MESSAGE_GEN = "mg";
	public static final String MESSAGE_ARRIVE = "ma";

	/*
	 * BGPDaemon messages
	 */
	// ERROR_MSG + ROUTER_TIMEOUT/UNKNOWN_PEER + (srcASN) + AT + (recieving ASN)
	// DISTANCE_DUMP_START + (asn)
	public static final String DD_START = LoggingMessages.DISTANCE_DUMP_START + "(\\d++)";
	public static final String DD_VALUE = "(\\d++)" + LoggingMessages.TO + "([0-9\\./]++)";
	public static final String TIMEOUT_PATTERN = LoggingMessages.ROUTER_TIMEOUT + "(\\d++)" + LoggingMessages.AT + "(\\d++)";
	
	/*
	 * BGPDaemon strings
	 */
	public static final String ERROR_MSG = "Error msg: ";
	public static final String ROUTER_TIMEOUT = "Router timeout ";
	public static final String RECONNECT = "Router reconnect started ";
	public static final String ROUTER_ALREADY_CONN = "Router already connected ";
	public static final String UNKNOWN_PEER = "Unknown Peer ";
	public static final String DISTANCE_DUMP_START = "Start distance dump ";
	public static final String DISTANCE_DUMP_STOP = "Stop distance dump";
}
