package gdsc.colocalisation.cda.engine;

import gdsc.colocalisation.cda.engine.CalculationResult;
import gdsc.core.utils.Correlator;
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
	ImageStack imageStack1;
	ImageStack roiStack1;
	ImageStack imageStack2;
	ImageStack roiStack2;
	ImageStack confinedStack;
	double denom1;
	double denom2;
	List<CalculationResult> results;
	TwinStackShifter twinImageShifter;
	BlockingQueue<CDAJob> jobs;
	int totalSteps;
	Correlator c = new Correlator();

	private volatile boolean finished = false;

	public CDAWorker(ImageStack imageStack1, ImageStack roiStack1, ImageStack imageStack2, ImageStack roiStack2, ImageStack confinedStack, double denom1, double denom2,
			List<CalculationResult> results, BlockingQueue<CDAJob> jobs,
			int totalSteps)
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
	}

	/**
	 * Perform the CDA shift and calculate the results
	 */
	public void run(int n, int x, int y)
	{
		final double distance = Math.sqrt(x * x + y * y);

		if (n % 2 == 0)
			IJ.showProgress(n, totalSteps);

		twinImageShifter.run(x, y);

		IntersectResult intersectResult = calculateResults(twinImageShifter.getResultStack(),
				twinImageShifter.getResultStack2(), imageStack2, roiStack2);

		final double m1 = (double) intersectResult.sum1 / denom1;
		final double m2 = (double) intersectResult.sum2 / denom2;

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

			int[] ii1 = new int[i1.length];
			int[] ii2 = new int[i1.length];
			int n = 0;
			for (int i = i1.length; i-- > 0;)
			{
				if ((m1[i] != 0) && (m2[i] != 0))
				{
					// ImageJ stores unsigned values
					ii1[n] = i1[i] & 0xffff;
					ii2[n] = i2[i] & 0xffff;
					n++;
				}
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
	public void run()
	{
		twinImageShifter = new TwinStackShifter(imageStack1, roiStack1, confinedStack);
		
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
	
	/**
	 * @return True if the worker is ready to run jobs
	 */
	public boolean isInitialised()
	{
		return twinImageShifter != null;
	}
}
