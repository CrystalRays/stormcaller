package sim.engine;

import java.util.concurrent.*;

import sim.event.SimEvent;

public class SimWorker implements Runnable {

	private Semaphore taskCount;
	private Semaphore doneReporter;
	private SimWorkerPool parent;

	public SimWorker(Semaphore workCount, Semaphore completeSemaphore, SimWorkerPool owningPool) {
		this.taskCount = workCount;
		this.doneReporter = completeSemaphore;
		this.parent = owningPool;
	}

	public void run() {
		SimEvent currentJob = null;

		try {
			while (true) {
				this.taskCount.acquire();
				currentJob = this.parent.getTask();
				currentJob.dispatch();
				currentJob = null;
				this.doneReporter.release();
			}
		} catch (InterruptedException e) {
			System.out.println("thread exiting...");
			return;
		}
	}
}
