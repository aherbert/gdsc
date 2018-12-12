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

package uk.ac.sussex.gdsc.utils;

import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;

import ij.IJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.simple.RandomSource;

/**
 * Computes the odds of winning the lottery using random sampling.
 */
public class Lottery_PlugIn implements PlugIn {
  private static final String TITLE = "Lottery";
  private static int numbers = 59;
  private static int pick = 6;
  private static int match = 3;
  private static long simulations;

  /** {@inheritDoc} */
  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    final GenericDialog gd = new GenericDialog(TITLE);
    gd.addMessage("Calculate the lottory odds");
    gd.addSlider("Numbers", 1, numbers, numbers);
    gd.addSlider("Pick", 1, pick, pick);
    gd.addSlider("Match", 1, pick, match);
    gd.addNumericField("Simulations", simulations, 0);
    gd.showDialog();
    if (gd.wasCanceled()) {
      return;
    }

    numbers = Math.abs((int) gd.getNextNumber());
    pick = Math.abs((int) gd.getNextNumber());
    match = Math.abs((int) gd.getNextNumber());
    simulations = Math.abs((long) gd.getNextNumber());

    if (numbers < 1) {
      IJ.error("Numbers must be >= 1");
      return;
    }
    if (pick > numbers) {
      IJ.error("Pick must be <= Numbers");
      return;
    }
    if (match > pick) {
      IJ.error("Match must be <= Pick");
      return;
    }

    final UniformRandomProvider rand = RandomSource.create(RandomSource.SPLIT_MIX_64);
    String msg = "Calculating ...";
    if (simulations == 0) {
      msg += " Escape to exit";
    }
    IJ.log(msg);
    long count = 0;
    long ok = 0;
    int samples = 0;

    final int[] data = new int[numbers];
    for (int i = 0; i < data.length; i++) {
      data[i] = i;
    }

    while (true) {
      count++;

      // Shuffle
      for (int i = data.length; i-- > 1;) {
        final int j = rand.nextInt(i + 1);
        final int tmp = data[i];
        data[i] = data[j];
        data[j] = tmp;
      }

      // Count the matches.
      // Assumes the user has selected the first 'pick' numbers from the series (e.g. 0-5 for 6)
      // and counts how may of their numbers are in the random set (e.g. how many in the random
      // set are <6).
      int found = 0;
      for (int i = 0; i < pick; i++) {
        if (data[i] < pick) {
          found++;
        }
      }
      // Check if this is the amount of numbers to match
      if (found == match) {
        ok++;
      }

      if (++samples == 100000) {
        if (ok == 0) {
          IJ.log(String.format("0 / %d = 0", count));
        } else {
          final double f = (double) ok / count;
          IJ.log(String.format("%d / %d = %f (1 in %f)", ok, count, f, 1.0 / f));
        }
        samples = 0;
        if (ImageJUtils.isInterrupted()) {
          return;
        }
      }

      if (count == simulations) {
        return;
      }
    }
  }
}
