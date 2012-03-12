package bgp.messages;

import java.security.InvalidParameterException;

/**
 * Stores a collection of BGP constants.
 *
 */
public class Constants {

	//bgp message types
	public static final int BGP_UPDATE = 0;
	public static final int BGP_KEEPALIVE = 1;
	public static final int BGP_CONNECT = 2;
	public static final int BGP_ERROR = 3;
	
	/**
	 * Parses BGP message type into human readable String.
	 * An exception will be thrown if the message type isn't a valid
	 * message type;
	 * 
	 * @param messageType - the message type value
	 * @return - a human readable string for that message value
	 */
	public static String bgpMessageTypeToString(int messageType){
		if(messageType == BGP_UPDATE){
			return "BGP Update";
		}
		else if(messageType == BGP_KEEPALIVE){
			return "BGP Keep Alive";
		}
		else if(messageType == BGP_CONNECT){
			return "BGP Connect";
		}
		else if(messageType == BGP_ERROR){
			return "BGP Error";
		}
		
		throw new InvalidParameterException("Bad message type: " + messageType);
	}
	
	//origin attributes
	public static final int IGP = 0;
	public static final int EGP = 1;
	public static final int INCOMPLETE = 2;
	
	/**
	 * Parses an origin into a human readable String.
	 * 
	 * @param origin - int value of origin attribute
	 * @return - String representing the origin attribute
	 */
	public static String originToString(int origin){
		if(origin == Constants.EGP){
			return "External";
		}
		else if(origin == Constants.IGP){
			return "Internal";
		}
		else if(origin == Constants.INCOMPLETE){
			return "Incomplete";
		}
		
		throw new InvalidParameterException("Bad origin value: " + origin);
	}
}
