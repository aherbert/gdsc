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

import gdsc.colocalisation.cda.engine.CalculationResult;
import gdsc.colocalisation.cda.TwinStackShifter;
import ij.IJ;
import ij.ImageStack;

import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * Performs the CDA algorithm
 */
public class CDAWorker implements Runnable
{
	ImageStack imageStack2;
	ImageStack roiStack2;
	double denom1;
	double denom2;
	List<CalculationResult> results;
	TwinStackShifter twinImageShifter;
	BlockingQueue<CDAJob> jobs;
	int totalSteps;

	private volatile boolean finished = false;

	public CDAWorker(ImageStack imageStack2, ImageStack roiStack2, double denom1, double denom2,
			List<CalculationResult> results, TwinStackShifter twinImageShifter, BlockingQueue<CDAJob> jobs,
			int totalSteps)
	{
		this.imageStack2 = imageStack2;
		this.roiStack2 = roiStack2;
		this.denom1 = denom1;
		this.denom2 = denom2;
		this.results = results;
		this.twinImageShifter = twinImageShifter;
		this.jobs = jobs;
		this.totalSteps = totalSteps;
	}

	/**
	 * Perform the CDA shift and calculate the results
	 */
	public void run(int n, int x, int y)
	{
		double distance = Math.sqrt(x * x + y * y);

		if (n % 2 == 0)
			IJ.showProgress(n, totalSteps);

		twinImageShifter.run(x, y);

		IntersectResult intersectResult = calculateResults(twinImageShifter.getResultStack(),
				twinImageShifter.getResultStack2(), imageStack2, roiStack2);

		double m1 = intersectResult.sum1 / denom1;
		double m2 = intersectResult.sum2 / denom2;

		results.add(new CalculationResult(distance, m1, m2, intersectResult.r));
	}

	private IntersectResult calculateResults(ImageStack stack1, ImageStack roi1, ImageStack stack2, ImageStack roi2)
	{
		int sumX = 0;
		long sumXY = 0;
		long sumXX = 0;
		long sumYY = 0;
		int sumY = 0;
		int n = 0;
		short ch1;
		short ch2;

		for (int slice = stack1.getSize(); slice > 0; slice--)
		{
			short[] i1 = (short[]) stack1.getPixels(slice);
			short[] i2 = (short[]) stack2.getPixels(slice);

			byte[] m1 = (byte[]) roi1.getPixels(slice);
			byte[] m2 = (byte[]) roi2.getPixels(slice);

			for (int i = i1.length; i-- > 0;)
			{
				if ((m1[i] != 0) && (m2[i] != 0))
				{
					ch1 = i1[i];
					ch2 = i2[i];

					sumX += ch1;
					sumXY += (ch1 * ch2);
					sumXX += (ch1 * ch1);
					sumYY += (ch2 * ch2);
					sumY += ch2;

					n++;
				}
			}
		}

		double r = Double.NaN;

		if (n > 0)
		{
			double pearsons1 = sumXY - (1.0 * sumX * sumY / n);
			double pearsons2 = sumXX - (1.0 * sumX * sumX / n);
			double pearsons3 = sumYY - (1.0 * sumY * sumY / n);

			r = pearsons1 / (Math.sqrt(pearsons2 * pearsons3));
		}

		return new IntersectResult(sumX, sumY, r); 
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	public void run()
	{
		try
		{
			while (!finished)
			{
				CDAJob job = jobs.take();
				if (job == null || job.n < 0 || finished)
					break;
				run(job.n, job.x, job.y);
			}
		}
		catch (InterruptedException e)
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
}
