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

import uk.ac.sussex.gdsc.colocalisation.cda.TwinStackShifter;
import uk.ac.sussex.gdsc.core.logging.Ticker;
import uk.ac.sussex.gdsc.core.utils.Correlator;

import ij.ImageStack;

import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * Performs the CDA algorithm.
 */
public class CdaWorker implements Runnable {
  private final ImageStack imageStack2;
  private final ImageStack roiStack2;
  private final double denom1;
  private final double denom2;
  private final List<CalculationResult> results;
  private final TwinStackShifter twinImageShifter;
  private final BlockingQueue<CdaJob> jobs;
  private final Ticker ticker;
  private final Correlator correlator = new Correlator();
  private final int[] ii1;
  private final int[] ii2;

  private volatile boolean finished;

  /**
   * Instantiates a new CDA worker.
   *
   * @param imageStack1 the image stack 1
   * @param roiStack1 the roi stack 1
   * @param imageStack2 the image stack 2
   * @param roiStack2 the roi stack 2
   * @param confinedStack the confined stack
   * @param denom1 the denominator 1 (sum of image stack 1)
   * @param denom2 the denominator 2 (sum of image stack 2)
   * @param results the results
   * @param jobs the jobs
   * @param ticker the ticker
   */
  public CdaWorker(ImageStack imageStack1, ImageStack roiStack1, ImageStack imageStack2,
      ImageStack roiStack2, ImageStack confinedStack, double denom1, double denom2,
      List<CalculationResult> results, BlockingQueue<CdaJob> jobs, Ticker ticker) {
    this.imageStack2 = imageStack2;
    this.roiStack2 = roiStack2;
    this.denom1 = denom1;
    this.denom2 = denom2;
    this.results = results;
    this.jobs = jobs;
    this.ticker = ticker;
    ii1 = new int[imageStack1.getWidth() * imageStack1.getHeight()];
    ii2 = new int[ii1.length];
    twinImageShifter = new TwinStackShifter(imageStack1, roiStack1, confinedStack);
  }

  /**
   * Perform the CDA shift and calculate the results.
   *
   * @param jobNumber the job number
   * @param x the x shift
   * @param y the y shift
   */
  public void runJob(int jobNumber, int x, int y) {
    final double distance = Math.sqrt((double) x * x + y * y);

    twinImageShifter.run(x, y);

    final IntersectResult intersectResult = calculateResults(twinImageShifter.getResultStack(),
        twinImageShifter.getResultStack2(), imageStack2, roiStack2);

    final double m1 = intersectResult.sum1 / denom1;
    final double m2 = intersectResult.sum2 / denom2;

    results.add(new CalculationResult(distance, m1, m2, intersectResult.correlation));

    ticker.tick();
  }

  private IntersectResult calculateResults(ImageStack stack1, ImageStack roi1, ImageStack stack2,
      ImageStack roi2) {
    correlator.clear();

    for (int slice = stack1.getSize(); slice > 0; slice--) {
      final short[] i1 = (short[]) stack1.getPixels(slice);
      final short[] i2 = (short[]) stack2.getPixels(slice);

      final byte[] m1 = (byte[]) roi1.getPixels(slice);
      final byte[] m2 = (byte[]) roi2.getPixels(slice);

      int length = 0;
      for (int i = i1.length; i-- > 0;) {
        if ((m1[i] != 0) && (m2[i] != 0)) {
          // ImageJ stores unsigned values
          ii1[length] = i1[i] & 0xffff;
          ii2[length] = i2[i] & 0xffff;
          length++;
        }
      }
      correlator.add(ii1, ii2, length);
    }

    return new IntersectResult(correlator.getSumX(), correlator.getSumY(),
        correlator.getCorrelation());
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    try {
      while (!finished) {
        final CdaJob job = jobs.take();
        if (job == null || job.getJobNumber() < 0 || finished) {
          break;
        }
        runJob(job.getJobNumber(), job.getX(), job.getY());
      }
    } catch (final InterruptedException ex) {
      // Restore interrupted state
      Thread.currentThread().interrupt();
    } finally {
      finished = true;
    }
  }

  /**
   * Signal that the worker should end.
   */
  public void finish() {
    finished = true;
  }

  /**
   * Checks if is finished.
   *
   * @return True if the worker has finished.
   */
  public boolean isFinished() {
    return finished;
  }
}
