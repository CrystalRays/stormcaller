package bgp.dataStructures;

import java.security.InvalidParameterException;
import java.util.*;

/**
 * Class used to represent networks, used for addressing data and routes.
 * 
 */
public class CIDR {

	private int netBits;
	private byte[] address;

	/**
	 * Creates a CIDR from a given input string. Address bits are taken from the
	 * IP style part of the string. Input in the address bits that extends
	 * beyond the network bits will be dropped.
	 * 
	 * @param cidrString
	 *            - string if form ZZZ.ZZZ.ZZZ.ZZZ/YY
	 */
	public CIDR(String cidrString) {
		StringTokenizer addressNetToken, addressOctetsToken;
		String addressString, netString;
		int tempOctet, netBitsLeft, maskSize;
		byte mask;

		//make sure we have a "/" to show netbit section
		addressNetToken = new StringTokenizer(cidrString, "/");
		if (addressNetToken.countTokens() != 2) {
			throw new InvalidParameterException("No \"/\" found.");
		}

		//seperate the ZZZ.ZZZ.ZZZ.ZZZ/YY into component parts
		addressString = addressNetToken.nextToken();
		netString = addressNetToken.nextToken();

		//parse the size of the network part, if too large or small, yell a lot
		this.netBits = Integer.parseInt(netString);
		if (this.netBits < 1 || this.netBits > 24) {
			throw new InvalidParameterException("Net bits outside of range: " + netBits);
		}

		//count the number of octets, make sure we have 4
		addressOctetsToken = new StringTokenizer(addressString, ".");
		if (addressOctetsToken.countTokens() != 4) {
			throw new InvalidParameterException("Incorrect number of octets");
		}

		//build the address array
		this.address = new byte[4];
		netBitsLeft = this.netBits;
		for (int counter = 0; counter < 4; counter++) {
			tempOctet = Integer.parseInt(addressOctetsToken.nextToken());
			if (tempOctet < 0 || tempOctet > 255) {
				throw new InvalidParameterException("Octet outside of range: " + tempOctet);
			}

			//strip out address bits not inside the network size
			maskSize = Math.min(netBitsLeft, 8);
			netBitsLeft -= maskSize;
			if (maskSize == 0) {
				this.address[counter] = (byte) 0;
			} else {
				mask = (byte) 128;
				mask = (byte) (mask >> (maskSize - 1));
				this.address[counter] = (byte) (((byte) tempOctet) & mask);
			}
		}
	}

	/**
	 * Dumps a string in the same form the constructor expects.
	 */
	public String toString() {
		String retString = "";
		int tempInt;

		//construct the address bits
		for (int counter = 0; counter < 4; counter++) {
			tempInt = this.address[counter];
			//damn you signed ints...
			if (tempInt < 0) {
				tempInt += 256;
			}
			retString += tempInt + ".";
		}
		//trim the last "." and add the "/"
		retString = retString.substring(0, retString.length() - 1) + "/";
		retString += netBits;
		return retString;
	}

	/**
	 * Transparent call through to equals(CIDR). Throws an exception if the
	 * object isn't a CIDR.
	 */
	public boolean equals(Object rhs) {
		CIDR rhsObj = (CIDR) rhs;
		return this.equals(rhsObj);
	}

	/**
	 * Tests if two CIDRs are equal.
	 * 
	 * @param rhs
	 *            - the other CIDR
	 * @return - true if the two CIDRs have the same number of network bits and
	 *         the addresses match
	 */
	public boolean equals(CIDR rhs) {
		boolean returnFlag = true;

		for (int counter = 0; counter < this.address.length && returnFlag; counter++) {
			returnFlag = (this.address[counter] == rhs.address[counter]);
		}

		return returnFlag && (this.netBits == rhs.netBits);
	}

	/**
	 * Predicate that tests if the given CIDR is contained in this CIDR.
	 * 
	 * @param subCIDR
	 *            - the potential subnet
	 * @return - true if the subCIDR is not a less specific network and all
	 *         address bits match (only network bits are stored/checked)
	 */
	public boolean contains(CIDR subCIDR) {
		int netBitsLeft, occtetCounter, tempBits;
		byte ander;

		//don't even check if the subCIDR has fewer network bits
		if (subCIDR.netBits < this.netBits) {
			return false;
		}

		netBitsLeft = this.netBits;
		occtetCounter = 0;
		while (netBitsLeft > 0) {
			ander = (byte) 128;

			//look at either the next 8 bits or however many bits are left....
			tempBits = Math.min(netBitsLeft, 8);
			netBitsLeft -= tempBits;

			//get our bitmask
			ander = (byte) (ander >> (tempBits - 1));

			if ((this.address[occtetCounter] & ander) != (subCIDR.address[occtetCounter] & ander)) {
				return false;
			}

			occtetCounter++;
		}

		return true;
	}

	/**
	 * Hash value is simply the hash of the string that represents this CIDR
	 */
	public int hashCode() {
		return this.toString().hashCode();
	}
}
