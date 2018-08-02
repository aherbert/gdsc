/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2018 Alex Herbert
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package uk.ac.sussex.gdsc.colocalisation.cda.engine;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import ij.ImageStack;

/**
 * Performs the Confined Displacement Algorithm (CDA).
 * <p>
 * Multi-threaded for speed. Uses a BlockingQueue to hold the work which is then processed sequentially by worker
 * threads.
 */
public class CDAEngine
{
    private BlockingQueue<CDAJob> jobs = null;
    private final List<CDAWorker> workers = new LinkedList<>();
    private final List<Thread> threads = new LinkedList<>();

    /**
     * Constructor.
     *
     * @param imageStack1
     *            the image stack 1
     * @param roiStack1
     *            the roi stack 1
     * @param confinedStack
     *            the confined stack
     * @param imageStack2
     *            the image stack 2
     * @param roiStack2
     *            the roi stack 2
     * @param denom1
     *            the denominator 1 (sum of image stack 1)
     * @param denom2
     *            the denominator 2 (sum of image stack 2)
     * @param results
     *            the results
     * @param totalSteps
     *            the total steps
     * @param threads
     *            The number of threads to use
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
            final CDAWorker worker = new CDAWorker(imageStack1, roiStack1, imageStack2, roiStack2, confinedStack,
                    denom1, denom2, results, jobs, totalSteps);
            final Thread t = new Thread(worker);

            workers.add(worker);
            this.threads.add(t);

            t.start();
        }
    }

    /**
     * This method checks if all the worker threads are ready to accept jobs, waiting a short period if necessary.
     * Note that jobs can still be queued if this method returns false.
     *
     * @return True if ready to accept jobs, false if the workers are still initialising.
     */
    public boolean isInitialised()
    {
        boolean ok = checkWorkers();
        if (ok)
            return true;

        ok = true;
        for (final CDAWorker worker : workers)
            if (!checkWorkerWithDelay(worker))
                ok = false;

        if (ok)
            return true;

        // Re-check as they may have now initialised
        return checkWorkers();
    }

    private static boolean checkWorkerWithDelay(CDAWorker worker)
    {
        for (int i = 0; !worker.isInitialised() && i < 5; i++)
            try
            {
                Thread.sleep(20);
            }
            catch (final InterruptedException e)
            {
                // Ignore
            }
        return worker.isInitialised();
    }

    private boolean checkWorkers()
    {
        for (final CDAWorker worker : workers)
            if (!worker.isInitialised())
                return false;
        return true;
    }

    private void createQueue(int threads)
    {
        this.jobs = new ArrayBlockingQueue<>(threads * 2);
    }

    /**
     * Adds the work to the current queue.
     *
     * @param n
     *            the job number
     * @param x
     *            the x shift
     * @param y
     *            the y shift
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
        catch (final InterruptedException e)
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
            for (final CDAWorker worker : workers)
                worker.finish();

            // Workers may be waiting for a job.
            // Add null jobs if the queue is not at capacity so they can be collected by alive workers.
            // If there are already jobs then the worker will stop due to the finish() signal.
            for (int i = 0; i < threads.size(); i++)
                jobs.offer(new CDAJob(-1, 0, 0)); // non-blocking add to queue
        }
        else
            // Finish all the worker threads by passing in a null job
            for (int i = 0; i < threads.size(); i++)
                put(-1, 0, 0); // blocking add to queue

        // Collect all the threads
        for (int i = 0; i < threads.size(); i++)
            try
            {
                threads.get(i).join();
            }
            catch (final InterruptedException e)
            {
                // TODO - Handle thread errors
                e.printStackTrace();
            }

        threads.clear();
    }
}
