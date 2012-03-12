package bgp.dataStructures;

import java.util.*;

public class DampeningTable {

	private int cut = -1;
	private int reuse = -1;
	private int decayOk = -1;
	private int decayNg = -1;
	private int ceil = -1;
	private int tmaxOk = -1;
	private int tmaxNg = -1;

	private HashMap<String, Integer> fomTable;
	private HashMap<String, Integer> lastUpdateTable;
	private HashMap<String, Integer> lastFlipTable;
	private HashMap<String, Boolean> okTable;
	private HashMap<String, Boolean> supressTable;

	private List<String> reuseList;

	public static final String CUT = "cut";
	public static final String REUSE = "reuse";
	public static final String DECAYOK = "decayok";
	public static final String DECAYNG = "decayng";
	public static final String CEIL = "ceil";
	public static final String TMAXOK = "tmaxok";
	public static final String TMAXNG = "tmaxng";

	public static final int FOM = 1000;

	public DampeningTable(List<String> configParams) {
		this.parseConfig(configParams);
		this.fomTable = new HashMap<String, Integer>();
		this.lastUpdateTable = new HashMap<String, Integer>();
		this.lastFlipTable = new HashMap<String, Integer>();
		this.okTable = new HashMap<String, Boolean>();
		this.supressTable = new HashMap<String, Boolean>();
		this.reuseList = new LinkedList<String>();
	}

	private void parseConfig(List<String> configParams) {
		StringTokenizer confTokens;
		String cmd;

		for (String line : configParams) {
			confTokens = new StringTokenizer(line, " ");
			cmd = confTokens.nextToken().toLowerCase();
			if (cmd.equals(DampeningTable.CUT)) {
				this.cut = Integer.parseInt(confTokens.nextToken());
			} else if (cmd.equals(DampeningTable.REUSE)) {
				this.reuse = Integer.parseInt(confTokens.nextToken());
			} else if (cmd.equals(DampeningTable.DECAYOK)) {
				this.decayOk = Integer.parseInt(confTokens.nextToken());
			} else if (cmd.equals(DampeningTable.DECAYNG)) {
				this.decayNg = Integer.parseInt(confTokens.nextToken());
			} else if (cmd.equals(DampeningTable.CEIL)) {
				this.ceil = Integer.parseInt(confTokens.nextToken());
			} else if (cmd.equals(DampeningTable.TMAXOK)) {
				this.tmaxOk = Integer.parseInt(confTokens.nextToken());
			} else if (cmd.equals(DampeningTable.TMAXNG)) {
				this.tmaxNg = Integer.parseInt(confTokens.nextToken());
			} else {
				System.err.println("bad config line for dampening: " + line);
				System.exit(-1);
			}
		}

		if (this.cut == -1 || this.reuse == -1 || this.decayOk == -1 || this.decayNg == -1 || this.ceil == -1
				|| this.tmaxOk == -1 || this.tmaxNg == -1) {
			System.err.println("dampening not fully configured...");
			System.exit(-1);
		}
	}

	public boolean routeAdvertised(int asn, CIDR network, int time) {
		String key = DampeningTable.genKeyString(asn, network);

		/*
		 * no previous flapping, we can just accept the route
		 */
		if (!this.fomTable.containsKey(key)) {
			return true;
		}

		/*
		 * Update fom table, update time table, update like ok table
		 */
		this.fomTable.put(key, this.runDecay(this.fomTable.get(key), this.lastUpdateTable.get(key), time, false,
				this.lastFlipTable.get(key)));
		this.lastFlipTable.put(key, time);
		this.lastUpdateTable.put(key, time);
		this.okTable.put(key, true);

		/*
		 * getting suppression setting for dampening decision
		 */
		boolean currentSuppressed = this.supressTable.containsKey(key);
		if (currentSuppressed) {
			currentSuppressed = this.supressTable.get(key);
		}

		/*
		 * make decision to dampen
		 */
		if (!currentSuppressed) {
			if (this.fomTable.get(key) < this.cut) {
				this.supressTable.put(key, false);
				return true;
			} else {
				this.supressTable.put(key, true);
				this.reuseList.add(key);
				return false;
			}
		} else {
			if (this.fomTable.get(key) < this.reuse) {
				this.supressTable.put(key, false);
				return true;
			} else {
				this.supressTable.put(key, true);
				this.reuseList.remove(key);
				this.reuseList.add(key);
				return false;
			}
		}
	}

	public void routeWithdrawn(int asn, CIDR network, int time) {
		String key = DampeningTable.genKeyString(asn, network);

		/*
		 * figure out feature of merit value
		 */
		int addFom = DampeningTable.FOM;
		if (this.fomTable.containsKey(key) && this.fomTable.get(key) > 0) {
			addFom += this.runDecay(this.fomTable.get(key), this.lastUpdateTable.get(key), time, true,
					this.lastFlipTable.get(key));
			this.reuseList.remove(key);
		}

		/*
		 * update tables
		 */
		this.fomTable.put(key, addFom);
		this.lastUpdateTable.put(key, time);
		this.lastFlipTable.put(key, time);
		this.okTable.put(key, false);
	}

	public List<String> runTimerCheck(int currentTime) {
		List<String> retList = new LinkedList<String>();
		String tKey;
		
		while(this.reuseList.size() > 0){
			tKey = this.reuseList.remove(0);
			int newFoM = this.runDecay(this.fomTable.get(tKey), this.lastUpdateTable.get(tKey), currentTime,
					this.okTable.get(tKey), this.lastFlipTable.get(tKey));
			
			if(newFoM > 0){
				this.lastUpdateTable.put(tKey, currentTime);
				this.fomTable.put(tKey, newFoM);
			}
			else{
				this.remove(tKey);
			}
			
			if(newFoM < this.reuse){
				retList.add(tKey);
			}
			else{
				this.reuseList.add(0, tKey);
				break;
			}
		}

		return retList;
	}

	public static String genKeyString(int asn, CIDR network) {
		return asn + ":" + network.toString();
	}

	private void remove(String tKey){
		this.lastFlipTable.remove(tKey);
		this.lastUpdateTable.remove(tKey);
		this.fomTable.remove(tKey);
		this.okTable.remove(tKey);
		this.supressTable.remove(tKey);
	}
	
	private int runDecay(int value, int oldTime, int newTime, boolean online, int lastFlip) {
		if ((online && lastFlip >= this.tmaxOk) || (lastFlip >= this.tmaxNg)) {
			return 0;
		}

		int delta = newTime - oldTime;
		int hl;
		if (online) {
			hl = this.decayOk;
		} else {
			hl = this.decayNg;
		}

		return (int) Math.round(Math.pow(2.0, (double) delta * -1.0 / hl) * value);
	}
}
