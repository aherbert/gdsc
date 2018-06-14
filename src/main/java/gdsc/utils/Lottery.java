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
package gdsc.utils;

import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.Well44497b;

import gdsc.UsageTracker;
import gdsc.core.ij.Utils;
import ij.IJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

/**
 * Computes the odds of winning the lottery using random sampling
 */
public class Lottery implements PlugIn
{
	final static String TITLE = "Lottery";
	static int numbers = 59;
	static int pick = 6;
	static int match = 3;
	static long simulations = 0;

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	@Override
	public void run(String arg)
	{
		UsageTracker.recordPlugin(this.getClass(), arg);

		GenericDialog gd = new GenericDialog(TITLE);
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
				if (Utils.isInterrupted())
					return;
			}

			if (count == simulations)
				return;
		}
	}
}
