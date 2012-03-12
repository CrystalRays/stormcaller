package liveView;

import java.security.InvalidParameterException;
import java.util.*;

public class Ring {

	private List<List<Integer>> clusters;
	private List<HashSet<Integer>> membership;
	private HashSet<Integer> allNodes;
	
	private int maxSize = 0;

	public Ring() {
		this.clusters = new LinkedList<List<Integer>>();
		this.membership = new ArrayList<HashSet<Integer>>();
		this.allNodes = new HashSet<Integer>();
	}

	public void addNodeToExisting(int added, int linkedNode) {
		if (this.allNodes.contains(added)) {
			throw new InvalidParameterException("asked to add a second time: " + added);
		}
		if (!this.allNodes.contains(linkedNode)) {
			throw new InvalidParameterException("asked to link to nonexistent node: " + added + " - " + linkedNode);
		}

		this.allNodes.add(added);

		for (int counter = 0; counter < this.membership.size(); counter++) {
			if (this.membership.get(counter).contains(linkedNode)) {
				int pos;
				boolean front;
				this.membership.get(counter).add(added);

				int linkPos = this.clusters.get(counter).indexOf(linkedNode);
				front = (linkPos / 2 < this.clusters.get(counter).size());

				if (front) {
					pos = 0;
				} else {
					pos = this.clusters.get(counter).size();
				}
				this.clusters.get(counter).add(pos, added);
				break;
			}
		}
	}

	public void addToNewCluster(int added) {
		if (this.allNodes.contains(added)) {
			throw new InvalidParameterException("asked to add a second time: " + added);
		}
		this.allNodes.add(added);

		this.clusters.add(new ArrayList<Integer>());
		this.membership.add(new HashSet<Integer>());

		this.membership.get(this.membership.size() - 1).add(added);
		this.clusters.get(this.clusters.size() - 1).add(added);
	}

	public void directlyLinkNodeToCluster(int node, int cluster) {
		int sizeDiff = cluster - this.clusters.size() + 1;
		for (int counter = 0; counter < sizeDiff; counter++) {
			this.clusters.add(new ArrayList<Integer>());
			this.membership.add(new HashSet<Integer>());
		}

		this.clusters.get(cluster).add(node);
		this.allNodes.add(node);
		this.membership.get(cluster).add(node);
	}

	public HashSet<Integer> getNodesInCluster(int cluster) {
		HashSet<Integer> retSet = new HashSet<Integer>();
		retSet.addAll(this.membership.get(cluster));
		return retSet;
	}
	
	public int locateNode(int node){
		for(int counter = 0; counter < this.clusters.size(); counter++){
			if(this.membership.get(counter).contains(node)){
				return counter;
			}
		}
		
		return -1;
	}

	public boolean existsInRing(int candidate) {
		return this.allNodes.contains(candidate);
	}

	public int getClusterCount() {
		return this.clusters.size();
	}
	
	public int getNodeCount(){
		int sum = 0;
		
		for(List<Integer> tCluster : this.clusters){
			sum += tCluster.size();
		}
		
		return sum;
	}
	
	public List<Integer> getCluster(int clusterPos){
		return this.clusters.get(clusterPos);
	}

	public int getSmallestCluster() {
		int min = Integer.MAX_VALUE;
		int winningSlot = -1;

		for (int counter = 0; counter < this.clusters.size(); counter++) {
			if (this.clusters.get(counter).size() < min) {
				min = this.clusters.get(counter).size();
				winningSlot = counter;
			}
		}

		return winningSlot;
	}

	public int getLargestClusterIntersect(HashSet<Integer> nodes) {
		int winningCluster = -1;
		int maxCluster = 0;

		for (int counter = 0; counter < this.getClusterCount(); counter++) {
			HashSet<Integer> intersect = this.getNodesInCluster(counter);
			intersect.retainAll(nodes);
			if (intersect.size() > maxCluster) {
				winningCluster = counter;
				maxCluster = intersect.size();
			}
		}
		
		return winningCluster;
	}
}
