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

package uk.ac.sussex.gdsc.utils;

import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.utils.rng.RandomUtils;
import uk.ac.sussex.gdsc.core.utils.rng.UniformRandomProviders;

import ij.IJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import org.apache.commons.rng.UniformRandomProvider;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Computes the odds of winning the lottery using random sampling.
 */
public class Lottery_PlugIn implements PlugIn {
  private static final String TITLE = "Lottery";
  /** The current settings for the plugin instance. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    int numbers = 59;
    int pick = 6;
    int match = 3;
    long simulations;

    /**
     * Default constructor.
     */
    Settings() {
      // Do nothing
    }

    /**
     * Copy constructor.
     *
     * @param source the source
     */
    private Settings(Settings source) {
      numbers = source.numbers;
      pick = source.pick;
      match = source.match;
      simulations = source.simulations;
    }

    /**
     * Copy the settings.
     *
     * @return the settings
     */
    Settings copy() {
      return new Settings(this);
    }

    /**
     * Load a copy of the settings.
     *
     * @return the settings
     */
    static Settings load() {
      return lastSettings.get().copy();
    }

    /**
     * Save the settings.
     */
    void save() {
      lastSettings.set(this);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    settings = Settings.load();

    final GenericDialog gd = new GenericDialog(TITLE);
    gd.addMessage("Calculate the lottory odds");
    gd.addSlider("Numbers", 1, settings.numbers, settings.numbers);
    gd.addSlider("Pick", 1, settings.pick, settings.pick);
    gd.addSlider("Match", 1, settings.pick, settings.match);
    gd.addNumericField("Simulations", settings.simulations, 0);
    gd.showDialog();
    if (gd.wasCanceled()) {
      return;
    }

    settings.numbers = Math.abs((int) gd.getNextNumber());
    settings.pick = Math.abs((int) gd.getNextNumber());
    settings.match = Math.abs((int) gd.getNextNumber());
    settings.simulations = Math.abs((long) gd.getNextNumber());

    settings.save();

    if (settings.numbers < 1) {
      IJ.error("Numbers must be >= 1");
      return;
    }
    if (settings.pick > settings.numbers) {
      IJ.error("Pick must be <= Numbers");
      return;
    }
    if (settings.match > settings.pick) {
      IJ.error("Match must be <= Pick");
      return;
    }

    runSimulation();
  }

  private void runSimulation() {
    final UniformRandomProvider rng = UniformRandomProviders.create();
    String msg = "Calculating ...";
    if (settings.simulations == 0) {
      msg += " Escape to exit";
    }
    IJ.log(msg);
    long count = 0;
    long ok = 0;
    int samples = 0;

    final int[] data = new int[settings.numbers];
    for (int i = 0; i < data.length; i++) {
      data[i] = i;
    }

    // local copy for inside the loop
    final int pick = settings.pick;
    final int match = settings.match;
    final long simulations = settings.simulations;

    for (;;) {
      count++;

      RandomUtils.shuffle(data, rng);

      // Count the matches.
      // Assumes the user has selected the first 'pick' numbers from the series (e.g. 0-5 for 6)
      // and counts how many of their numbers are in the random set (e.g. how many in the random
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
          ImageJUtils.log("0 / %d = 0", count);
        } else {
          final double f = (double) ok / count;
          ImageJUtils.log("%d / %d = %f (1 in %f)", ok, count, f, 1.0 / f);
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
