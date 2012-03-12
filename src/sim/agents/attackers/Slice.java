package sim.agents.attackers;

public class Slice implements Comparable<Slice> {

	private int start;
	private int end;
	private int sliceGroupID;
	private Slice prev;
	private Slice next;

	public Slice(int start, int end, int sliceGroupID, Slice prev, Slice next) {
		this.start = start;
		this.end = end;
		this.sliceGroupID = sliceGroupID;
		this.prev = prev;
		this.next = next;
	}

	public int getStart() {
		return this.start;
	}

	public int getEnd() {
		return this.end;
	}

	public int getSliceGroupID() {
		return this.sliceGroupID;
	}

	public Slice getPrev() {
		return this.prev;
	}

	public void setPrev(Slice newPrev) {
		this.prev = newPrev;
	}

	public Slice getNext() {
		return this.next;
	}

	public void setNext(Slice newNext) {
		this.next = newNext;
	}

	public void insertAfterMe(Slice insertSlice) {
		Slice oldNext = this.next;
		this.next = insertSlice;
		insertSlice.prev = this;
		insertSlice.next = oldNext;
		
		if (oldNext != null) {
			oldNext.prev = insertSlice;
		}
	}

	public void insertBeforeMe(Slice insertSlice) {
		Slice oldPrev = this.prev;
		this.prev = insertSlice;
		insertSlice.next = this;
		insertSlice.prev = oldPrev;

		if (oldPrev != null) {
			oldPrev.next = insertSlice;
		}
	}

	public boolean equals(Slice rhs) {
		return this.start == rhs.start && this.end == rhs.end && this.sliceGroupID == rhs.sliceGroupID;
	}

	public int compareTo(Slice rhs) {
		return this.start - rhs.start;
	}
	
	public String toString(){
		return "slice id: " + this.sliceGroupID + " start: " + this.start + " end: " + this.end;
	}

}
