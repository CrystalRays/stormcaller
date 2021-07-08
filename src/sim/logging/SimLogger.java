package sim.logging;

import java.io.*;

/**
 * Really basic logger, in general just a BufferedWriter that eats IOExceptions
 * when trying to write because I would get sick of writing try/catch blocks
 * everywhere I wanted to log.
 * 
 */
public class SimLogger {

	/**
	 * Flag to control how much we log.
	 */
	private boolean logVerbose;

	/**
	 * The BufferedWriter we're using
	 */
	private BufferedWriter outStream;

	/*
	 * The directory and extension we want to use
	 */
	public static final String DIR = "logs/";
	public static final String EXT = ".log";

	/**
	 * Creates a logger that will write to the log directory with a filename of
	 * the current system time.
	 * 
	 * @param logVerbose
	 *            - if true we log EVERYTHING, this gets huge, like ~8 gigs for
	 *            a 1hr sim with 5 ASes only...
	 * @throws IOException
	 *             - if there is an issue creating/opening the file
	 */
	public SimLogger(String fileName, boolean logVerbose) throws IOException {
		this.logVerbose = logVerbose;
		this.outStream = new BufferedWriter(new FileWriter(SimLogger.DIR + fileName + SimLogger.EXT));
	}

	/**
	 * Logs the message by appending a newline to it and sending it to the
	 * BufferedWriter. This will eat any exceptions and dump stack traces, but
	 * won't stop the sim from running, you need to look at the console to see
	 * errors.
	 * 
	 * @param message
	 *            - the message we want to log
	 * @param verbose
	 *            - flag to set if this message is considered verbose
	 */
	public void logMessage(String message, boolean verbose) {
		/*
		 * Don't log verbose things if we're not suppose to...
		 */
		if (verbose && !this.logVerbose) {
			return;
		}

		try {
			this.outStream.write(message + "\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Informs the logger that we're done logging, which will cause the logger
	 * to close the file, again eating any exception
	 */
	public void doneLogging() {
		try {
			this.outStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
