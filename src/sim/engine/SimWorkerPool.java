package sim.engine;

import java.util.*;
import java.util.concurrent.*;

import sim.event.SimEvent;

/**
 * This class is used to run tasks in the simulator in parallel. It servers as a
 * central point to control worker threads and for a driver thread to hand tasks
 * to. See the documentation on the constructor for more information on how this
 * guy works.
 * 
 */
public class SimWorkerPool {

	/**
	 * Semaphore used to signal worker threads that work is available.
	 */
	private Semaphore taskCount;

	/**
	 * Semaphore used during the blockOnEpoch() function to form an execution
	 * wall.
	 */
	private Semaphore outstandingTasks;

	/**
	 * Stores the number of tasks handed to us for this current epoch.
	 */
	private int pushedTasks;

	/**
	 * Fast add/remove thread safe queue that stores the tasks we can do this
	 * epoch. Note that this type of queue has some gotchas, like calling
	 * .size() is not a constant time operation. Be careful when doing stuff
	 * with this guy.
	 */
	private ConcurrentLinkedQueue<SimEvent> tasks;

	/**
	 * The list of worker threds. Currently only really used to shut the threads
	 * down when execution is over.
	 */
	private LinkedList<Thread> theWorkers;

	/**
	 * Creates a worker pool for a simulation with the given number of worker
	 * threads. This pool can be used to run work in a parallel manner. The
	 * general work flow is to build a collection of tasks that can happen
	 * together, this is called an epoch. Tasks that are outside this epoch
	 * generally need tasks inside the epoch to be finished before they can be
	 * ran. There is an execution walling function built in that a main thread
	 * can use to block until an epoch completes. It should be noted that
	 * usually there is NOT linera scaling with worker threads/processor cores.
	 * In general though it is advisable to give a pool a number of threads
	 * equal to the number of cores - 3.
	 * 
	 * @param workerCount
	 *            - the number of worker threads that will be executing tasks in
	 *            parallel
	 */
	public SimWorkerPool(int workerCount) {
		this.taskCount = new Semaphore(0);
		this.outstandingTasks = new Semaphore(0);
		this.pushedTasks = 0;
		this.tasks = new ConcurrentLinkedQueue<SimEvent>();
		this.theWorkers = new LinkedList<Thread>();

		/*
		 * Create worker threads and start them. Currently workers are started
		 * as real threads, not daemon threads, this might cause issues if there
		 * is a threading error (i.e. the run might hang) but this isn't a big
		 * deal as this would only happen if there was an error anyway.
		 */
		for (int counter = 0; counter < workerCount; counter++) {
			SimWorker temp = new SimWorker(this.taskCount, this.outstandingTasks, this);
			Thread tempThread = new Thread(temp);
			this.theWorkers.add(tempThread);
			tempThread.start();
		}
	}

	/**
	 * Interrupts all workers, which will break them out of their work loops and
	 * trigger them to shut down. This should be called when the simulation is
	 * done running.
	 */
	public void closePool() {
		for (Thread tThread : this.theWorkers) {
			tThread.interrupt();
		}
	}

	/**
	 * Gives the pool a task that can be ran in a multi-threaded manner during
	 * this epoch.
	 * 
	 * @param readyEvent
	 *            - the event that can be handled by a worker thread.
	 */
	public void addTask(SimEvent readyEvent) {
		/*
		 * Increment the number of tasks we've received this epoch by one, add
		 * to the work queue, and release a semaphore so a worker thread knows a
		 * task is avail.
		 */
		this.pushedTasks++;
		this.tasks.add(readyEvent);
		this.taskCount.release();
	}

	/**
	 * Creates an execution wall, forcing all work in the current epoch to get
	 * done before the simulation is allowed to move on. Call this function when
	 * you want ensure that all work you have given to the simulator is done
	 * before moving on. It is important to note that the epoch is considered
	 * closed after this function is called, and calls to addTask will place the
	 * tasks in a new epoch.
	 */
	public void blockOnEpoch() {
		try {
			/*
			 * Workers report tasks complete to a semaphore, therefore when we
			 * can acquire a number of tickets equal to the number of tasks this
			 * epoch, we know that the epoch has been completed.
			 */
			for (int counter = 0; counter < this.pushedTasks; counter++) {
				this.outstandingTasks.acquire();
			}
		} catch (InterruptedException e) {
			/*
			 * If we are interrupted (should never happen) we should yell a lot
			 * and exit with the threading error exit code (-3).
			 */
			e.printStackTrace();
			System.exit(-3);
		}

		/*
		 * Reset the epoch
		 */
		this.pushedTasks = 0;
	}

	/**
	 * Interface called by worker threads to get a task out of the work queue.
	 * This uses a fast thread safe queue so locking isn't needed.
	 * 
	 * @return - an event that a worker thread can run
	 */
	public SimEvent getTask() {
		return this.tasks.poll();
	}
}
