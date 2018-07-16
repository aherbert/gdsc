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
package gdsc.colocalisation.cda.engine;

import java.util.List;
import java.util.concurrent.BlockingQueue;

import gdsc.colocalisation.cda.TwinStackShifter;
import gdsc.core.utils.Correlator;
import ij.IJ;
import ij.ImageStack;

/**
 * Performs the CDA algorithm
 */
public class CDAWorker implements Runnable
{
	private ImageStack imageStack1;
	private ImageStack roiStack1;
	private ImageStack imageStack2;
	private ImageStack roiStack2;
	private ImageStack confinedStack;
	private double denom1;
	private double denom2;
	private List<CalculationResult> results;
	private TwinStackShifter twinImageShifter;
	private BlockingQueue<CDAJob> jobs;
	private int totalSteps;
	private Correlator c = new Correlator();
	private final int[] ii1, ii2;

	private volatile boolean finished = false;

	/**
	 * Instantiates a new CDA worker.
	 *
	 * @param imageStack1
	 *            the image stack 1
	 * @param roiStack1
	 *            the roi stack 1
	 * @param imageStack2
	 *            the image stack 2
	 * @param roiStack2
	 *            the roi stack 2
	 * @param confinedStack
	 *            the confined stack
	 * @param denom1
	 *            the denominator 1 (sum of image stack 1)
	 * @param denom2
	 *            the denominator 2 (sum of image stack 2)
	 * @param results
	 *            the results
	 * @param jobs
	 *            the jobs
	 * @param totalSteps
	 *            the total steps
	 */
	public CDAWorker(ImageStack imageStack1, ImageStack roiStack1, ImageStack imageStack2, ImageStack roiStack2,
			ImageStack confinedStack, double denom1, double denom2, List<CalculationResult> results,
			BlockingQueue<CDAJob> jobs, int totalSteps)
	{
		this.imageStack1 = imageStack1;
		this.roiStack1 = roiStack1;
		this.imageStack2 = imageStack2;
		this.roiStack2 = roiStack2;
		this.confinedStack = confinedStack;
		this.denom1 = denom1;
		this.denom2 = denom2;
		this.results = results;
		this.jobs = jobs;
		this.totalSteps = totalSteps;
		ii1 = new int[imageStack1.getWidth() * imageStack1.getHeight()];
		ii2 = new int[ii1.length];
	}

	/**
	 * Perform the CDA shift and calculate the results
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
		final double distance = Math.sqrt(x * x + y * y);

		if (n % 2 == 0)
			IJ.showProgress(n, totalSteps);

		twinImageShifter.run(x, y);

		final IntersectResult intersectResult = calculateResults(twinImageShifter.getResultStack(),
				twinImageShifter.getResultStack2(), imageStack2, roiStack2);

		final double m1 = intersectResult.sum1 / denom1;
		final double m2 = intersectResult.sum2 / denom2;

		//System.out.printf("d=%f, x=%d, y=%d, n=%d, r=%f, sx=%d, sy=%d\n", distance, x, y, c.getN(), intersectResult.r,
		//		c.getSumX(), c.getSumY());

		results.add(new CalculationResult(distance, m1, m2, intersectResult.r));
	}

	private IntersectResult calculateResults(ImageStack stack1, ImageStack roi1, ImageStack stack2, ImageStack roi2)
	{
		c.clear();

		for (int slice = stack1.getSize(); slice > 0; slice--)
		{
			final short[] i1 = (short[]) stack1.getPixels(slice);
			final short[] i2 = (short[]) stack2.getPixels(slice);

			final byte[] m1 = (byte[]) roi1.getPixels(slice);
			final byte[] m2 = (byte[]) roi2.getPixels(slice);

			int n = 0;
			for (int i = i1.length; i-- > 0;)
				if ((m1[i] != 0) && (m2[i] != 0))
				{
					// ImageJ stores unsigned values
					ii1[n] = i1[i] & 0xffff;
					ii2[n] = i2[i] & 0xffff;
					n++;
				}
			c.add(ii1, ii2, n);
		}

		return new IntersectResult(c.getSumX(), c.getSumY(), c.getCorrelation());
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run()
	{
		twinImageShifter = new TwinStackShifter(imageStack1, roiStack1, confinedStack);

		try
		{
			while (!finished)
			{
				final CDAJob job = jobs.take();
				if (job == null || job.n < 0 || finished)
					break;
				run(job.n, job.x, job.y);
			}
		}
		catch (final InterruptedException e)
		{
			System.out.println(e.toString());
			throw new RuntimeException(e);
		}
		finally
		{
			finished = true;
			//notifyAll();
		}
	}

	/**
	 * Signal that the worker should end
	 */
	public void finish()
	{
		finished = true;
	}

	/**
	 * @return True if the worker has finished
	 */
	public boolean isFinished()
	{
		return finished;
	}

	/**
	 * @return True if the worker is ready to run jobs
	 */
	public boolean isInitialised()
	{
		return twinImageShifter != null;
	}
}
