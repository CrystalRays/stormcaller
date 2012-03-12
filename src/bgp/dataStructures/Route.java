package bgp.dataStructures;

import java.util.*;
import bgp.messages.Constants;

/**
 * Class that represents information about a route to a destination. This class
 * contains all attributes of said route. Not all attributes are set at
 * construction time, these attributes must be set later with setters. These
 * attributes will start with default values as defined by the attribute.
 * 
 * All attributes in the Route class are mutable, as routers will often change
 * attributes (possibly arbitrarily) during import and export specifications.
 * 
 */
public class Route {

	/**
	 * The destination network this route is for.
	 */
	private CIDR nlri;

	/**
	 * Attribute that is set by the router to state if the route was learned
	 * from an internal, external, or unknown source.
	 */
	private int origin;

	/**
	 * Comma delimited string of AS numbers the route passes through. This is
	 * NOT a list of routers/IP addresses/etc
	 */
	private int[] asPath;

	/**
	 * LOCAL_PREF attribute of the route. This attribute is non-transitive, and
	 * is not set at construction time. Default value is 0.
	 */
	private int localPref;

	/**
	 * The srcId of the router that sent us this route. This isn't really used
	 * right now since we could just use AS number to do this as that uniquely
	 * IDs routers, but AS numbers have structure in the simulator, so this
	 * needs to be a random ID still. This is not set at construction and
	 * currently is set when packaged into an Update object to the AS number of
	 * the sender, the receiver should then translate it into a random unique
	 * router ID. Default value is -1.
	 */
	private int srcId;

	/**
	 * Construct a Route object with the given attributes. Some attributes will
	 * not be set at construction, these should be set at the correct time with
	 * setters.
	 * 
	 * @param nlri -
	 *            the network this route reaches
	 * @param origin -
	 *            where this route was learned from
	 * @param asPath -
	 *            the path of ASes in this route
	 * 
	 */
	public Route(CIDR nlri, int origin, int cpyPath[]) {
		/*
		 * Transitive attrs
		 */
		this.nlri = nlri;
		this.origin = origin;

		/*
		 * Non-transitive attrs
		 */
		this.localPref = 0;
		this.srcId = -1;

		/*
		 * Build the path safely
		 */
		this.asPath = new int[cpyPath.length];
		for (int counter = 0; counter < cpyPath.length; counter++) {
			this.asPath[counter] = cpyPath[counter];
		}

	}

	public Route(CIDR nlri, int origin) {
		this.nlri = nlri;
		this.origin = origin;
		this.localPref = 0;
		this.srcId = -1;
		this.asPath = new int[1];
		this.asPath[0] = 0;
	}

	public Route(CIDR nlri, int origin, int path[], int reservePathSpace) {
		this.nlri = nlri;
		this.origin = origin;
		this.localPref = 0;
		this.srcId = -1;
		this.asPath = new int[path.length + reservePathSpace];
		for(int counter = 0; counter < path.length; counter++){
			this.asPath[counter + reservePathSpace] = path[counter];
		}
	}

	public Route(String serialString) {
		StringTokenizer serialTokens = new StringTokenizer(serialString, "$");
		
		this.nlri = new CIDR(serialTokens.nextToken());
		this.origin = Integer.parseInt(serialTokens.nextToken());

		StringTokenizer pathTokens = new StringTokenizer(serialTokens.nextToken(), ",");
		this.asPath = new int[pathTokens.countTokens()];
		for(int counter = 0; counter < this.asPath.length; counter++){
			this.asPath[counter] = Integer.parseInt(pathTokens.nextToken());
		}
		if (this.asPath.equals("null")) {
			this.asPath = null;
		}

		this.localPref = Integer.parseInt(serialTokens.nextToken());
		this.srcId = Integer.parseInt(serialTokens.nextToken());
	}

	/**
	 * Dumps a formated logging string of all route attributes. This string is
	 * more then somewhat wordy.
	 */
	public String toString() {
		String returnString = this.nlri.toString() + "\n";

		returnString += "\tOrigin is: " + Constants.originToString(this.origin) + "\n";
		returnString += "\tPath is: ";
		for(int counter = 0; counter < this.asPath.length; counter++){
			returnString += "" + this.asPath[counter];
			if(counter < this.asPath.length -1){
				returnString += ",";
			}
		}
		returnString += "\n";
		returnString += "\tLocal pref is: " + this.localPref + "\n";
		returnString += "\tSource ID: " + this.srcId;

		return returnString;
	}

	/**
	 * Checks if two routes have the same transitive attributes. This is used to
	 * determine if a route has changed and updates have to be issued to other
	 * tables.
	 * 
	 * @param rhs -
	 *            the other route we're comparing
	 * @return - true if the two routes share nlri, origin, asPath, and next
	 *         hop, false otherwise
	 */
	public boolean equalsTransitiveAttr(Route rhs) {
		if ((!this.nlri.equals(rhs.nlri)) || this.origin != rhs.origin || this.asPath.length != rhs.asPath.length) {
			return false;
		}

		boolean pathEqual = true;
		for (int counter = 0; counter < this.asPath.length; counter++) {
			if (this.asPath[counter] != rhs.asPath[counter]) {
				pathEqual = false;
				break;
			}
		}

		return pathEqual;
	}

	/**
	 * Checks all attributes (transitive and intransitive) and determines if two
	 * routes are equal. Uses a call to equalsTransitiveAttr plus checks on the
	 * intransitive attributes.
	 * 
	 * @param rhs -
	 *            the other route we're comparing
	 * @return - true if the two routes are transitvely equal and they share
	 *         local preference and source, false otherwise
	 */
	public boolean equals(Route rhs) {
		return this.equalsTransitiveAttr(rhs) && this.localPref == rhs.localPref && this.srcId == rhs.srcId;
	}

	public boolean equals(Object rhs) {
		Route rhsRoute = (Route) rhs;
		return this.equals(rhsRoute);
	}

	/**
	 * Creates a deep copy of this Route object. This copies all attributes,
	 * including those not set at construction time.
	 * 
	 * @return a new route object with the same attributes as the current
	 */
	public Route copy() {
		Route toReturn = new Route(this.nlri, this.origin, this.asPath);
		toReturn.setLocalPref(this.localPref);
		toReturn.setSrcId(this.srcId);

		return toReturn;
	}

	/**
	 * Computes the hash of the long dump string, which should be unique per
	 * route.
	 */
	public int hashCode() {
		return this.asPath.hashCode() + this.nlri.hashCode() + this.srcId;
	}

	// getters and setters from here on...
	// both a getter and a setter exist for every attribute
	public void setLocalPref(int localPref) {
		this.localPref = localPref;
	}

	public String serialString() {
		StringBuilder retString = new StringBuilder(this.nlri.toString());
		retString.append("$");
		retString.append(this.origin);
		retString.append("$");
		
		for(int counter = 0; counter < this.asPath.length; counter++){
			retString.append("" + this.asPath[counter]);
			if(counter < this.asPath.length - 1){
				retString.append(",");
			}
		}
		
		retString.append("$");
		retString.append(this.localPref);
		retString.append("$");
		retString.append(this.srcId);
		return retString.toString();
	}

	public int getLocalPref() {
		return localPref;
	}

	public void setSrcId(int srcId) {
		this.srcId = srcId;
	}

	public int getSrcId() {
		return srcId;
	}

	public void extendPath(int addedHop) {
		this.asPath[0] = addedHop;
	}

	public int[] getAsPath() {
		return asPath;
	}

	public int getNextHop() {
		return this.asPath[0];
	}

	public CIDR getNlri() {
		return nlri;
	}

	public void setOrigin(int origin) {
		this.origin = origin;
	}

	public int getOrigin() {
		return origin;
	}
}
