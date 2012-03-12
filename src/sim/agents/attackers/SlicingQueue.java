package sim.agents.attackers;

public class SlicingQueue {

	private Slice mySlices;

	public static void main(String args[]) {
		SlicingQueue testQueue = new SlicingQueue(1, 1000);
		for(int counter = 2; counter < 30; counter++){
			testQueue.placeNewSliceGroup(counter);
		}
		testQueue.printQueue();
	}

	public SlicingQueue(int startingID, int startTime) {
		this.mySlices = null;
		this.insertSliceGroup(startingID, startTime);
	}

	public void placeNewSliceGroup(int id) {
		int time = this.findStartTime();

		if (time == -1) {
			System.err.println("bad insert");
			return;
		}

		this.insertSliceGroup(id, time);
	}

	public Slice getNextSlice(){
		if(this.mySlices == null){
			return null;
		}
		
		Slice temp = this.mySlices;
		this.mySlices = this.mySlices.getNext();
		return temp;
	}
	
	public int peekAtNextTime(){
		return this.mySlices.getStart();
	}
	
	public boolean hasMore(){
		return this.mySlices != null;
	}
	
	public void printQueue() {
		Slice tempSlice = this.mySlices;
		while (tempSlice != null) {
			System.out.println(tempSlice.toString());
			tempSlice = tempSlice.getNext();
		}
	}

	private int findStartTime() {
		Slice currentSlice = this.mySlices.getNext();
		Slice prevSlice = this.mySlices;

		while (currentSlice != null) {
			while (currentSlice.getStart() - prevSlice.getEnd() < 5002) {
				prevSlice = currentSlice;
				currentSlice = prevSlice.getNext();

				if (currentSlice == null) {
					return prevSlice.getEnd() + 1000;
				}
			}

			for (int counter = 1; counter <= (int)(Math.floor((currentSlice.getStart() - prevSlice.getEnd()) / 1000) - 4); counter++) {
				Slice tempCurrentSlice = currentSlice;
				Slice tempPrevSlice = prevSlice;
				int baseTime = tempPrevSlice.getEnd() + counter * 1000;
				int searchTime = baseTime + 3000;
				int walkCounter = 2;
				boolean pass = true;

				while (pass && walkCounter < 8) {
					searchTime = searchTime + (int) Math.pow(2, walkCounter) * 1000;
					walkCounter++;

					while (tempCurrentSlice.getEnd() < searchTime) {
						tempPrevSlice = tempCurrentSlice;
						tempCurrentSlice = tempPrevSlice.getNext();

						if (tempCurrentSlice == null) {
							return baseTime;
						}
					}

					if (searchTime + 1000 > tempCurrentSlice.getStart()) {
						pass = false;
					}
				}

				if (pass) {
					return baseTime;
				}
			}
			
			prevSlice = currentSlice;
			currentSlice = prevSlice.getNext();
		}

		/*
		 * Should never get here...
		 */
		return -1;
	}

	private void insertSliceGroup(int id, int startTime) {
		Slice prevCurrentSlice = null;
		Slice currentSlice = this.mySlices;
		Slice insertSlice = new Slice(startTime - 1, startTime + 4001, id, null, null);
		int waitCounter = 2;
		int baseTime = 0;

		if (currentSlice == null) {
			currentSlice = insertSlice;
			this.mySlices = currentSlice;
			baseTime = insertSlice.getEnd() + (int) Math.pow(2, waitCounter) * 1000 - 1001;
			waitCounter++;
			insertSlice = new Slice(baseTime - 1, baseTime + 1001, id, null, null);
		}

		while (waitCounter < 8) {
			if (currentSlice != null && currentSlice.getStart() < insertSlice.getStart()) {
				prevCurrentSlice = currentSlice;
				currentSlice = currentSlice.getNext();
				continue;
			}

			prevCurrentSlice.insertAfterMe(insertSlice);
			currentSlice = insertSlice;
			baseTime = insertSlice.getEnd() + (int) Math.pow(2, waitCounter) * 1000 - 1001;
			waitCounter++;
			insertSlice = new Slice(baseTime - 1, baseTime + 1001, id, null, null);
		}
	}

}
