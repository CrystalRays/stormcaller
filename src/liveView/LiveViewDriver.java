package liveView;

import java.util.*;
import java.io.IOException;
import javax.swing.*;

import sim.network.dataObjects.AS;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.Graphics;
import java.awt.Color;
import java.awt.TextField;
import java.awt.Graphics2D;

public class LiveViewDriver extends JComponent {

	private static final int widthHalf = 600;
	private static final int heightHalf = 380;
	private static final int adjust = 50;
	
	private static final int t1XRad = 300;
	private static final int t1YRad = 100;
	private static final int t2XRad = 100 + t1XRad;
	private static final int t2YRad = 100 + t1YRad;
	private static final int t3XRad = 125 + t2XRad;
	private static final int t3YRad = 125 + t2YRad;

	private static final int circleSize = 8;
	private static final int circleRad = LiveViewDriver.circleSize / 2;
	
	private static final double colorScale = 30.0;

	private Toolkit tools;
	private Cluster theTopo;
	private MakespanPlayer thePlayer;

	private HashMap<Integer, Dimension> circlePos;
	private HashMap<Integer, Color> circleColor;

	public static void main(String argv[]) throws InterruptedException, IOException {
		LiveViewDriver me = new LiveViewDriver(new Cluster("logs/link.txt", "logs/rank.txt"),
				new MakespanPlayer("logs/testlogMA.csv"));
		JFrame frame = new JFrame("LiveView");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().add(me);
		frame.setSize(me.getSize());
		Dimension screenD = me.getTools().getScreenSize();
		frame.setLocation((screenD.width / 2) - LiveViewDriver.widthHalf, (screenD.height / 2)
				- LiveViewDriver.heightHalf);
		frame.setVisible(true);

		me.gogo();
	}

	public LiveViewDriver(Cluster topo, MakespanPlayer data) {
		this.tools = this.getToolkit();
		this.theTopo = topo;
		this.thePlayer = data;
		this.setup();
	}

	private void gogo() throws InterruptedException {
		Thread.sleep(2000);

		while (this.thePlayer.notDone()) {
			for (int tAS : this.circleColor.keySet()) {
				double delay = Math.floor(this.thePlayer.getDelay(tAS) / 60000.0);
				delay = delay / LiveViewDriver.colorScale;
				delay = Math.min(1.0, delay);
				
				double red = delay;
				double blue = 1.0 - delay;				
				this.circleColor.put(tAS, new Color((float)red, (float)0.0, (float)blue));
			}
			this.thePlayer.nextRound();
			
			this.repaint();
			Thread.sleep(100);
		}
	}

	public Toolkit getTools() {
		return this.tools;
	}

	public void paint(Graphics g) {
		/*
		 * Hack to set the background color
		 */
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, LiveViewDriver.widthHalf * 2, LiveViewDriver.heightHalf * 2);

		/*
		 * Controls the number of lines we're drawing
		 */
		int lineCount = 5;
		int t1LineCount = 50;
		int hackCounter = 0;

		/*
		 * Draw the time bar
		 */
		g.setColor(Color.BLACK);
		g.drawRect(10, 10, 200, 20);
		g.fillRect(10, 10, (int)Math.round(200 * this.thePlayer.getPercentDone()), 20);
		
		/*
		 * Draw the scale
		 */
		for(int counter = 0; counter < 100; counter++){
			double red = (double)counter * 0.01;
			double blue = 1.0 - (double)counter * 0.01;
			g.setColor(new Color((float)red, (float)0.0, (float)blue));
			g.fillRect(10 + (2 * counter), 40, 2, 20);
		}
		
		/*
		 * Label the scale
		 */
		g.setColor(Color.BLACK);
		Graphics2D g2 = (Graphics2D)g;
		g2.drawString("0", 5, 75);
		g2.drawString(Integer.toString((int)(LiveViewDriver.colorScale/2)), 100, 75);
		g2.drawString(Integer.toString((int)LiveViewDriver.colorScale) + "+", 200, 75);
		
		/*
		 * draw the lines
		 */
		g.setColor(Color.GRAY);
		for (int tAS : this.circlePos.keySet()) {
			Dimension tPos = this.circlePos.get(tAS);
			int myX = LiveViewDriver.widthHalf + tPos.width - LiveViewDriver.adjust;
			int myY = LiveViewDriver.heightHalf + tPos.height - LiveViewDriver.adjust;

			HashSet<Integer> edgeSet = this.theTopo.getEdgeList().get(tAS);
			for (int tEdge : edgeSet) {
				int myLC;
				if (this.theTopo.getRank(tAS) == AS.T1 || this.theTopo.getRank(tEdge) == AS.T1) {
					myLC = t1LineCount;
				} else {
					myLC = lineCount;
				}

				Dimension edgeDim = this.circlePos.get(tEdge);
				if (hackCounter % myLC == 0) {
					g.drawLine(myX + LiveViewDriver.circleRad, myY + LiveViewDriver.circleRad, LiveViewDriver.widthHalf
							+ edgeDim.width + LiveViewDriver.circleRad - LiveViewDriver.adjust, LiveViewDriver.heightHalf + edgeDim.height
							+ LiveViewDriver.circleRad - LiveViewDriver.adjust);
				}
				hackCounter++;
			}
		}

		for (int tAS : this.circlePos.keySet()) {
			Dimension tPos = this.circlePos.get(tAS);
			g.setColor(this.circleColor.get(tAS));
			int myX = LiveViewDriver.widthHalf + tPos.width - LiveViewDriver.adjust;
			int myY = LiveViewDriver.heightHalf + tPos.height - LiveViewDriver.adjust;
			g.fillOval(myX, myY, LiveViewDriver.circleSize, LiveViewDriver.circleSize);
		}
	}

	private void setup() {
		this.setSize(2 * LiveViewDriver.widthHalf, 2 * LiveViewDriver.heightHalf);
		this.circlePos = new HashMap<Integer, Dimension>();

		this.equalSpacePerCluster(this.theTopo.getInnerCluster(), LiveViewDriver.t1XRad, LiveViewDriver.t1YRad);
		this.equalSpacePerCluster(this.theTopo.getMiddleCluster(), LiveViewDriver.t2XRad, LiveViewDriver.t2YRad);
		this.equalSpacePerCluster(this.theTopo.getOutterCluster(), LiveViewDriver.t3XRad, LiveViewDriver.t3YRad);

		//this.equalSpacePerNode(this.theTopo.getInnerCluster(), LiveViewDriver.t1Rad);
		//this.equalSpacePerNode(this.theTopo.getMiddleCluster(), LiveViewDriver.t2Rad);
		//this.equalSpacePerNode(this.theTopo.getOutterCluster(), LiveViewDriver.t3Rad);

		this.circleColor = new HashMap<Integer, Color>();
		Color startColor = new Color((float)0.0, (float)0.0, (float)1.0);
		for (int tAS : this.circlePos.keySet()) {
			this.circleColor.put(tAS, startColor);
		}
	}

	private void equalSpacePerCluster(Ring currentRing, int xRadius, int yRadius) {
		double radPerCluster = 2.0 * Math.PI / (double) currentRing.getClusterCount();

		for (int counter = 0; counter < currentRing.getClusterCount(); counter++) {
			List<Integer> cluster = currentRing.getCluster(counter);
			double degreesPerNode = radPerCluster / (double) cluster.size();

			for (int innerCounter = 0; innerCounter < cluster.size(); innerCounter++) {
				double current = radPerCluster * counter + degreesPerNode * innerCounter;
				Dimension tDim = new Dimension((int) Math.round(Math.sin(current) * xRadius), (int) Math.round(Math
						.cos(current)
						* yRadius));
				this.circlePos.put(cluster.get(innerCounter), tDim);
			}
		}
	}

	private void equalSpacePerNode(Ring currentRing, int radius) {
		double radPerNode = 2.0 * Math.PI / (double) currentRing.getNodeCount();

		int nodeCounter = 0;
		for (int counter = 0; counter < currentRing.getClusterCount(); counter++) {
			List<Integer> cluster = currentRing.getCluster(counter);

			for (int innerCounter = 0; innerCounter < cluster.size(); innerCounter++) {
				double current = radPerNode * nodeCounter;
				Dimension tDim = new Dimension((int) Math.round(Math.sin(current) * radius), (int) Math.round(Math
						.cos(current)
						* radius));
				this.circlePos.put(cluster.get(innerCounter), tDim);
				nodeCounter++;
			}
		}
	}
}
