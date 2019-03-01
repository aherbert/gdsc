/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2019 Alex Herbert
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

import ij.ImageStack;

import org.apache.commons.lang3.concurrent.ConcurrentRuntimeException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Performs the Confined Displacement Algorithm (CDA).
 *
 * <p>Multi-threaded for speed. Uses a BlockingQueue to hold the work which is then processed
 * sequentially by worker threads.
 */
public class CdaEngine {
  private static final String INTERRUPTED_MSG = "Unexpected interruption";

  private final BlockingQueue<CdaJob> jobs;
  private final List<CdaWorker> workers;
  private List<Thread> threads;

  /**
   * Instantiates a new CDA engine. This creates workers but these must be started using
   * {@link #start()}.
   *
   * @param imageStack1 the image stack 1
   * @param roiStack1 the roi stack 1
   * @param confinedStack the confined stack
   * @param imageStack2 the image stack 2
   * @param roiStack2 the roi stack 2
   * @param denom1 the denominator 1 (sum of image stack 1)
   * @param denom2 the denominator 2 (sum of image stack 2)
   * @param results the results
   * @param totalSteps the total steps
   * @param threads The number of threads to use
   */
  public CdaEngine(ImageStack imageStack1, ImageStack roiStack1, ImageStack confinedStack,
      ImageStack imageStack2, ImageStack roiStack2, double denom1, double denom2,
      List<CalculationResult> results, int totalSteps, int threads) {
    if (threads < 1) {
      threads = 1;
    }

    this.jobs = new ArrayBlockingQueue<>(threads * 2);

    results = Collections.synchronizedList(results);

    workers = new ArrayList<>(threads);

    // Create the workers
    for (int i = 0; i < threads; i++) {
      final CdaWorker worker = new CdaWorker(imageStack1, roiStack1, imageStack2, roiStack2,
          confinedStack, denom1, denom2, results, jobs, totalSteps);
      workers.add(worker);
    }
  }

  /**
   * Start the engine. This must be called before {@link #run(int, int, int)}.
   *
   * <p>An engine that has been stopped using {@link #end(boolean)} cannot be restarted.
   */
  public void start() {
    if (threads != null) {
      return;
    }
    threads = new ArrayList<>(workers.size());
    for (final CdaWorker worker : workers) {
      final Thread t = new Thread(worker);
      this.threads.add(t);
      t.start();
    }
  }

  /**
   * Adds the work to the current queue.
   *
   * @param jobNumber the job number
   * @param x the x shift
   * @param y the y shift
   */
  public void run(int jobNumber, int x, int y) {
    if (threads.isEmpty()) {
      return;
    }

    put(jobNumber, x, y);
  }

  private void put(int jobNumber, int x, int y) {
    try {
      jobs.put(new CdaJob(jobNumber, x, y));
    } catch (final InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ConcurrentRuntimeException(INTERRUPTED_MSG, ex);
    }
  }

  /**
   * Signal that no more fitting work will be added to the queue.
   *
   * @param now Stop the work immediately, otherwise finish all work in the queue
   */
  public void end(boolean now) {
    if (threads.isEmpty()) {
      return;
    }

    if (now) {
      // Request worker shutdown
      for (final CdaWorker worker : workers) {
        worker.finish();
      }

      // Workers may be waiting for a job.
      // Add null jobs if the queue is not at capacity so they can be collected by alive workers.
      // If there are already jobs then the worker will stop due to the finish() signal.
      final CdaJob signal = new CdaJob(-1, 0, 0);
      for (int i = 0; i < threads.size(); i++) {
        // Non-blocking add to queue.
        // If the queue is full then no need to persist.
        if (!jobs.offer(signal)) {
          break;
        }
      }
    } else {
      // Finish all the worker threads by passing in a null job
      for (int i = 0; i < threads.size(); i++) {
        put(-1, 0, 0); // blocking add to queue
      }
    }

    // Collect all the threads
    for (int i = 0; i < threads.size(); i++) {
      try {
        threads.get(i).join();
      } catch (final InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new ConcurrentRuntimeException(INTERRUPTED_MSG, ex);
      }
    }

    threads.clear();
  }
}
