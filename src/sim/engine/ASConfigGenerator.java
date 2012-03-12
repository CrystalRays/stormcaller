package sim.engine;

import java.io.*;
import java.util.*;

import bgp.dataStructures.CIDR;
import sim.network.dataObjects.*;
import sim.util.*;

public class ASConfigGenerator {

	/*
	 * Required params in the config files
	 */
	private static final String KEEPALIVE = "keep alive timer";
	private static final String HALT = "halt timer";
	private static final String MRAI = "mrai";
	private static final String PROVIDERAGNOSTIC = "provider agnostic";

	/*
	 * vars dealing with separating routers into two config groups
	 */
	private ConfigFileHelper largeConf;
	private ConfigFileHelper smallConf;
	private int largeCutoff = -1;
	private int largeCount = 0;
	private int totalCount = 0;

	/*
	 * Fixed randomness, as we need it to be the same relative to the randomness
	 * used to generate the serial file if we're using one
	 */
	private static Random rng = new Random(15151);

	/*
	 * Path and extension strings
	 */
	public static final String IOSDIR = "stormcaller/ios/";
	public static final String IOSEXT = ".ios";

	/**
	 * Builds the ASConfigGenerator object, after this calls should be made to
	 * createConfigFile for each AS in order to build the as config files
	 * themselves.
	 * 
	 * @param largeRouterFile
	 *            - the config file used for routers at or above the
	 *            largeCuttoff
	 * @param smallRouterFile
	 *            - the config file used for routers under the largeCuttoff
	 * @param largeCuttoff
	 *            - routers with AS degree under this size will use the small
	 *            router file, otherwise they use the large config file
	 */
	public ASConfigGenerator(String largeRouterFile, String smallRouterFile, int largeCuttoff) {

		/*
		 * Setup the required params
		 */
		Set<String> configReqParams = new HashSet<String>();
		configReqParams.add(ASConfigGenerator.HALT);
		configReqParams.add(ASConfigGenerator.KEEPALIVE);
		configReqParams.add(ASConfigGenerator.MRAI);
		configReqParams.add(ASConfigGenerator.PROVIDERAGNOSTIC);

		/*
		 * There are currently two config files, one for "large" routers, one
		 * for "small", this can be the same config
		 */
		this.largeConf = new ConfigFileHelper(configReqParams);
		this.smallConf = new ConfigFileHelper(configReqParams);
		this.largeCutoff = largeCuttoff;

		/*
		 * parse the config files through the config file parsers, yell and die
		 * if something goes wrong
		 */
		try {
			this.largeConf.parseFile(largeRouterFile);
			this.smallConf.parseFile(smallRouterFile);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	/**
	 * Actually generates and builds the AS config file for a given AS. This
	 * will result in a file being written to the path that is returned by a
	 * call to getConfigFileName for the given AS.
	 * 
	 * @param incAS
	 *            - the ASN of the AS we want a config file for
	 * @return - the path to the config file, as generated by the call to
	 *         getConfigFileName
	 * @throws IOException
	 *             - if something goes wrong while writing the config file
	 *             (should be fatal)
	 */
	public String createConfigFile(AS incAS) throws IOException {
		Set<CIDR> localNets = incAS.getLocalNetworks();
		Set<AS> providers = incAS.getProviders();
		Set<AS> customers = incAS.getCustomers();
		Set<AS> peers = incAS.getPeers();

		/*
		 * Figure out the file name, open up the file we're writing to
		 */
		String fileName = this.getConfigFileName(incAS);
		PrintStream outStream = new PrintStream(new FileOutputStream(fileName));

		/*
		 * Figure out if we're using the large config or small config, do some
		 * bookkeeping so we can log about it later, also, cache the
		 * providerAgnostic predicate for the given config so we don't have to
		 * fetch it over and over
		 */
		ConfigFileHelper configMap = null;
		this.totalCount++;
		if (providers.size() + customers.size() + peers.size() >= this.largeCutoff) {
			configMap = this.largeConf;
			this.largeCount++;
		} else {
			configMap = this.smallConf;
		}
		boolean providerAgnostic = configMap.getBooleanValue(ASConfigGenerator.PROVIDERAGNOSTIC);

		/*
		 * Write opening info about asn, and timer values
		 */
		outStream.println("router asn " + incAS.getASNumber());
		outStream.println("router keepalive " + configMap.getValue(ASConfigGenerator.KEEPALIVE));
		outStream.println("router haltvalue " + configMap.getValue(ASConfigGenerator.HALT));
		outStream.println("router mrai " + configMap.getValue(ASConfigGenerator.MRAI));

		/*
		 * Write info about the network we own
		 */
		for (CIDR tempNetwork : localNets) {
			outStream.println("network addr " + tempNetwork);
		}

		outStream.println("export start");
		//whitelist all networks we're responsible for
		for (CIDR tempNetwork : localNets) {
			outStream.println("whitelist network " + tempNetwork.toString());
		}
		for (AS tAS : customers) {
			outStream.println("whitelist as " + tAS.getASNumber());
		}
		//blacklist all providers, we don't carry links for them
		for (AS tAS : providers) {
			outStream.println("blacklist " + tAS.getASNumber());
		}

		for (AS tAS : peers) {
			outStream.println("blacklist " + tAS.getASNumber());
		}
		outStream.println("export stop");

		/*
		 * Set our local prefs for routes with the usual customer > peer >
		 * provider, we have a small difference if we're agnostic to which
		 * provider is sending traffic
		 */
		outStream.println("import start");
		for (AS tAS : customers) {
			outStream.println("aspref " + tAS.getASNumber() + " 150");
		}
		for (AS tAS : peers) {
			outStream.println("aspref " + tAS.getASNumber() + " 100");
		}
		if (providerAgnostic) {
			for (AS tAS : providers) {
				outStream.println("aspref " + tAS.getASNumber() + " 50");
			}
		} else {
			for (AS tAS : providers) {
				outStream.println("aspref " + tAS.getASNumber() + " " + (50 + ASConfigGenerator.rng.nextInt(20)));
			}
		}
		outStream.println("import stop");

		outStream.close();
		return fileName;
	}

	/**
	 * Returns the path to a config file for the given AS.
	 * 
	 * @param incAS
	 *            - the ASN of the AS we want a path for
	 * @return - the path to the AS such that it should be correct from the
	 *         perspective of SimDriver, if there is an issue, be sure to run
	 *         the install script.
	 */
	public String getConfigFileName(AS incAS) {
		return ASConfigGenerator.IOSDIR + incAS.getASNumber() + ASConfigGenerator.IOSEXT;
	}

	/**
	 * Prints out the number and percentage of total ASes that are at or above
	 * the large cutoff. This function only works correctly after all calls to
	 * createConfigFile are done.
	 */
	public void printLargeCount() {
		System.out.println("large count is: " + this.largeCount + " for cut of: " + this.largeCutoff + "("
				+ ((double) this.largeCount / (double) this.totalCount) + ")");
	}
}
