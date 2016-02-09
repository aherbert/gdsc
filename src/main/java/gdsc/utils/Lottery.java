package gdsc.utils;

import ij.IJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.Well44497b;

/**
 * Computes the odds of winning the lottery using random sampling 
 */
public class Lottery implements PlugIn
{
	static int numbers = 59;
	static int pick = 6;
	static int match = 3;
	static long simulations = 0;

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	public void run(String arg)
	{
		GenericDialog gd = new GenericDialog("Lottery");
		gd.addMessage("Calculate the lottory odds");
		gd.addSlider("Numbers", 1, numbers, numbers);
		gd.addSlider("Pick", 1, pick, pick);
		gd.addSlider("Match", 1, pick, match);
		gd.addNumericField("Simulations", simulations, 0);
		gd.showDialog();
		if (gd.wasCanceled())
			return;

		numbers = Math.abs((int) gd.getNextNumber());
		pick = Math.abs((int) gd.getNextNumber());
		match = Math.abs((int) gd.getNextNumber());
		simulations = Math.abs((long) gd.getNextNumber());

		if (numbers < 1)
		{
			IJ.error("Numbers must be >= 1");
			return;
		}
		if (pick > numbers)
		{
			IJ.error("Pick must be <= Numbers");
			return;
		}
		if (match > pick)
		{
			IJ.error("Match must be <= Pick");
			return;
		}

		RandomGenerator rand = new Well44497b(System.currentTimeMillis());
		String msg = "Calculating ...";
		if (simulations == 0)
			msg += " Escape to exit";
		IJ.log(msg);
		long count = 0;
		long ok = 0;
		int c = 0;

		final int[] data = new int[numbers];
		for (int i = 0; i < data.length; i++)
			data[i] = i;

		while (true)
		{
			count++;

			// Shuffle
			for (int i = data.length; i-- > 1;)
			{
				final int j = rand.nextInt(i + 1);
				final int tmp = data[i];
				data[i] = data[j];
				data[j] = tmp;
			}

			// Count the matches
			int m = 0;
			for (int i = 0; i < pick; i++)
				if (data[i] < pick)
					m++;
			if (m == match)
				ok++;

			if (++c == 100000)
			{
				double f = (double) ok / count;
				IJ.log(String.format("%d / %d = %f (1 in %f)", ok, count, f, 1.0 / f));
				c = 0;
				if (ImageJHelper.isInterrupted())
					return;
			}

			if (count == simulations)
				return;
		}
	}
}
