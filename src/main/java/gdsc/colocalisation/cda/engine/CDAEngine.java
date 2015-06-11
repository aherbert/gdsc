package gdsc.colocalisation.cda.engine;

/*----------------------------------------------------------------------------- 
 * GDSC Plugins for ImageJ
 * 
 * Copyright (C) 2011 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

import ij.ImageStack;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Performs the Confined Displacement Algorithm (CDA).
 * <p>
 * Multi-threaded for speed. Uses a BlockingQueue to hold the work which is then processed sequentially by worker
 * threads.
 */
public class CDAEngine
{
	private BlockingQueue<CDAJob> jobs = null;
	private List<CDAWorker> workers = new LinkedList<CDAWorker>();
	private List<Thread> threads = new LinkedList<Thread>();

	/**
	 * Constructor
	 * 
	 * @param threads
	 *            The number of threads to use (set to 1 if less than 1)
	 */
	public CDAEngine(ImageStack imageStack1, ImageStack roiStack1, ImageStack confinedStack, ImageStack imageStack2,
			ImageStack roiStack2, double denom1, double denom2, List<CalculationResult> results, int totalSteps,
			int threads)
	{
		if (threads < 1)
			threads = 1;

		createQueue(threads);
		results = Collections.synchronizedList(results);

		// Create the workers
		for (int i = 0; i < threads; i++)
		{
			CDAWorker worker = new CDAWorker(imageStack1, roiStack1, imageStack2, roiStack2, confinedStack, denom1,
					denom2, results, jobs, totalSteps);
			Thread t = new Thread(worker);

			workers.add(worker);
			this.threads.add(t);

			t.start();
		}

		for (CDAWorker worker : workers)
		{
			for (int i = 0; !worker.isInitialised() && i < 5; i++)
			{
				try
				{
					Thread.sleep(20);
				}
				catch (InterruptedException e)
				{
				}
			}
		}
	}

	private void createQueue(int threads)
	{
		this.jobs = new ArrayBlockingQueue<CDAJob>(threads * 2);
	}

	/**
	 * Adds the work to the current queue.
	 */
	public void run(int n, int x, int y)
	{
		if (threads.isEmpty())
			return;

		put(n, x, y);
	}

	private void put(int n, int x, int y)
	{
		try
		{
			jobs.put(new CDAJob(n, x, y));
		}
		catch (InterruptedException e)
		{
			// TODO - Handle thread errors
			throw new RuntimeException("Unexpected interruption", e);
		}
	}

	/**
	 * Signal that no more fitting work will be added to the queue
	 * 
	 * @param now
	 *            Stop the work immediately, otherwise finish all work in the queue
	 */
	public void end(boolean now)
	{
		if (threads.isEmpty())
			return;

		if (now)
		{
			// Request worker shutdown
			for (CDAWorker worker : workers)
				worker.finish();

			// Workers may be waiting for a job. 
			// Add null jobs if the queue is not at capacity so they can be collected by alive workers.
			// If there are already jobs then the worker will stop due to the finish() signal.
			for (int i = 0; i < threads.size(); i++)
			{
				jobs.offer(new CDAJob(-1, 0, 0)); // non-blocking add to queue
			}
		}
		else
		{
			// Finish all the worker threads by passing in a null job
			for (int i = 0; i < threads.size(); i++)
			{
				put(-1, 0, 0); // blocking add to queue
			}
		}

		// Collect all the threads
		for (int i = 0; i < threads.size(); i++)
		{
			try
			{
				threads.get(i).join();
			}
			catch (InterruptedException e)
			{
				// TODO - Handle thread errors
				e.printStackTrace();
			}
		}

		threads.clear();
	}
}
