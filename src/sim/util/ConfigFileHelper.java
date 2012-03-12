package sim.util;

import java.io.*;
import java.util.*;

/**
 * Class that standardizes the parsing of config files. There is support for
 * asserting required params, optional params can also be easily enumerated.
 * This class throws a lot of defensive exceptions, be warned.
 * 
 */
public class ConfigFileHelper {

	private Set<String> requiredParams;
	private HashMap<String, String> vals;

	private static final String sep = "=";
	private static final String COMMENT = "#";
	private static final String TRUE = "true";
	private static final String FALSE = "false";

	/**
	 * Builds a config file helper with the given opening set of required
	 * params. Note that parseFile MUST BE CALLED BEFORE THIS CAN BE USED.
	 * 
	 * @param requiredParams -
	 *            params that must be present in the config file.
	 */
	public ConfigFileHelper(Set<String> requiredParams) {
		this.requiredParams = new HashSet<String>();
		this.internalAddMoreReqParams(requiredParams);
		this.vals = new HashMap<String, String>();
	}

	/**
	 * Public function for adding more required param assertions. This is done
	 * if some params become required after the file is parsed for example.
	 * 
	 * @param moreReqParams -
	 *            the set of param key strings that need to be there
	 * @throws Exception -
	 *             if one or more required params are missing
	 */
	public void validateAddtionalRequiredParams(Set<String> moreReqParams) throws Exception {
		this.internalAddMoreReqParams(moreReqParams);
		this.checkRequiredParams();
	}

	/**
	 * Internal function for adding additional required parms. This function
	 * cleans them, ensuring they are trimmed and in lower case, it also makes
	 * sure that no one from outside holds a ref to the required param set.
	 * 
	 * @param reqParams -
	 *            set of required param strings
	 */
	private void internalAddMoreReqParams(Set<String> reqParams) {
		for (String param : reqParams) {
			this.requiredParams.add(param.trim().toLowerCase());
		}
	}

	/**
	 * Internal function that ensures all required params exist. Doesn't mess
	 * around with returning null, just throws an exception if one ore more does
	 * not exist.
	 * 
	 * @throws Exception -
	 *             if a required param is missing
	 */
	private void checkRequiredParams() throws Exception {
		for (String tParam : this.requiredParams) {
			if (!this.vals.containsKey(tParam)) {
				throw new Exception("missing required param: " + tParam);
			}
		}
	}

	/**
	 * Parses the given config file. This function will clean up values and will
	 * assert that all currently required params are present.
	 * 
	 * @param configFile -
	 *            the path to the config file
	 * @throws IOException -
	 *             if there is an IOException while reading the file
	 * @throws Exception -
	 *             if there is a required param that is not present
	 */
	public void parseFile(String configFile) throws IOException, Exception {
		BufferedReader confBuff = new BufferedReader(new FileReader(configFile));
		String poll;
		String lhs, rhs;

		/*
		 * Step through the config file trimming and de-capping
		 */
		while (confBuff.ready()) {
			poll = confBuff.readLine().trim();
			
			/*
			 * Parse out comments
			 */
			int commentPos = poll.indexOf(ConfigFileHelper.COMMENT);
			if(commentPos != -1){
				if(commentPos == 0){
					continue;
				}
				else{
					poll = poll.substring(0, commentPos).trim();
				}
			}
			
			if (poll.length() > 0) {				
				lhs = poll.substring(0, poll.indexOf(ConfigFileHelper.sep)).trim().toLowerCase();
				rhs = poll.substring(poll.indexOf(ConfigFileHelper.sep) + 1).trim().toLowerCase();

				if (vals.containsKey(lhs)) {
					throw new Exception("duplicate config entries for " + lhs + " in " + configFile);
				}

				vals.put(lhs, rhs);
			}
		}
		confBuff.close();

		/*
		 * Check that we've got our required params
		 */
		this.checkRequiredParams();
	}

	/**
	 * Fetches the string value stored for a given key, if the key is not
	 * present it will return null.
	 * 
	 * @param param -
	 *            the param key whose value you want
	 * @return - the trimmed lower case value mapped to the given key, or null
	 *         if the key didn't exist
	 */
	public String getValue(String param) {
		return this.vals.get(param.trim().toLowerCase());
	}

	/**
	 * Wrapper function for fetching a boolean value. This will automatically
	 * parse the mapped string, if the key does not exist FALSE will be assumed.
	 * Throws a runtime if the value stored is not true or false.
	 * 
	 * @param param -
	 *            a param key value that stores a boolean
	 * @return - true if the value is the string "true", "false" if it is the
	 *         string "false" or the value does not exist
	 */
	public boolean getBooleanValue(String param) {
		if (!this.vals.containsKey(param)) {
			return false;
		}

		if (this.vals.get(param).equals(ConfigFileHelper.TRUE)) {
			return true;
		} else if (this.vals.get(param).equals(ConfigFileHelper.FALSE)) {
			return false;
		}

		throw new RuntimeException("asked for binary config value on non true/false entry: " + param + " value: "
				+ this.vals.get(param));
	}
	
	/**
	 * Wrapper function for fetching an integer value. This will automatically
	 * parse the mapped string, if the key does not exist -1 will be assumed.
	 * 
	 * @param param -
	 *            a param key value that stores an int
	 * @return - the value of the param parsed into an integer, or -1 if the param does not exist
	 */
	public int getIntegerValue(String param){
		if (!this.vals.containsKey(param)) {
			return -1;
		}

		return Integer.parseInt(this.vals.get(param));
	}
	
	/**
	 * Wrapper function for fetching a double value. This will automatically
	 * parse the mapped string, if the key does not exist -1.0 will be assumed.
	 * 
	 * @param param -
	 *            a param key value that stores a double
	 * @return - the value of the param parsed into a double, or -1.0 if the param does not exist
	 */
	public double getDoubleValue(String param){
		if (!this.vals.containsKey(param)) {
			return -1.0;
		}

		return Double.parseDouble(this.vals.get(param));
	}

	/**
	 * Fetches the set of all optional params, in other words, all key values
	 * that have not been as of yet specified as required.
	 * 
	 * @return - the set of all non-required key strings
	 */
	public Set<String> getOptionalParams() {
		Set<String> retSet = new HashSet<String>();

		for (String tParam : this.vals.keySet()) {
			if (!this.requiredParams.contains(tParam)) {
				retSet.add(tParam);
			}
		}

		return retSet;
	}
}
