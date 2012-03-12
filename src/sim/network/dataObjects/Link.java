package sim.network.dataObjects;

public class Link {

	/**
	 * The two ASes this link connects, left and right side has zero meaning.
	 */
	private AS lhs;

	private AS rhs;

	/**
	 * capacity is measured in Kbit/sec
	 */
	private int capacity;

	/**
	 * Capcities of common Optical Carrier grades.
	 */
	public static final int OC768 = 38486016;
	public static final int OC192 = 9621504;
	public static final int OC48 = 2405376;
	public static final int OC12 = 601344;
	public static final int OC3 = 148608;

	/**
	 * Creates a link between two nodes with the given capacity.
	 * 
	 * @param lhs
	 *            - "left" side AS (left has no meaning)
	 * @param rhs
	 *            - "right" side AS (right has no meaning either)
	 * @param capacity
	 *            - capacity of the link in kbps
	 */
	public Link(AS lhs, AS rhs, int capacity) {
		this.lhs = lhs;
		this.rhs = rhs;
		this.capacity = capacity;
	}

	/**
	 * overriden to return <lhs AS number> <--> <rhs AS number> (Cap = <X>)
	 */
	public String toString() {
		String lineStr;

		if (this.capacity == Link.OC768) {
			lineStr = "OC768";
		} else if (this.capacity == Link.OC192) {
			lineStr = "OC192";
		} else if (this.capacity == Link.OC48) {
			lineStr = "OC48";
		} else if (this.capacity == Link.OC12) {
			lineStr = "OC12";
		} else if (this.capacity == Link.OC3) {
			lineStr = "OC3";
		} else {
			lineStr = "Unknown line: " + this.capacity;
		}

		return this.lhs.getASNumber() + " <--> " + this.rhs.getASNumber() + " (" + lineStr + ")";
	}

	/**
	 * REturns an array of ASes (size 2 always) that contains the two ASes
	 * connected. lhs AS is always item 0, rhs AS is always item 1.
	 * 
	 * @return an array containing the two ASes connected by this link
	 */
	public AS[] getASes() {
		AS[] retArray = new AS[2];
		retArray[0] = this.lhs;
		retArray[1] = this.rhs;
		return retArray;
	}

	/**
	 * Getter for the link capacity.
	 * 
	 * @return the capacity of the link
	 */
	public int getCapacity() {
		return this.capacity;
	}

	/**
	 * The to string captures all and only invarient vars
	 */
	public int hashCode() {
		return this.toString().hashCode();
	}

	/**
	 * Checks if the two ends are the same, and if the link is the same cap.
	 */
	public boolean equals(Object rhs) {
		Link rhsLink = (Link) rhs;

		return this.capacity == rhsLink.capacity && this.lhs.equals(rhsLink.lhs) && this.rhs.equals(rhsLink.rhs);
	}
}
