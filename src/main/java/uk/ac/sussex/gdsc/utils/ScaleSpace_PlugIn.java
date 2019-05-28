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
import uk.ac.sussex.gdsc.core.utils.MathUtils;

import gnu.trove.list.array.TDoubleArrayList;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Create a scale-space representation of an image using a 2D Gaussian filter at different scales.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Scale_space">Scale space</a>
 */
public class ScaleSpace_PlugIn implements PlugInFilter {
  private static final String TITLE = "Scale Space";

  /** The flags specifying the capabilities and needs. */
  private static final int FLAGS = DOES_ALL | NO_CHANGES;

  /** The flags specifying the capabilities and needs for Difference of Gaussians. */
  private static final int DOG_FLAGS = DOES_8G | DOES_16 | DOES_32 | NO_CHANGES;

  private ImagePlus imp;

  /** The current settings for the plugin instance. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    double minScale;
    double maxScale;
    boolean outputDifferenceImage;

    /**
     * Default constructor.
     */
    Settings() {
      minScale = 1;
    }

    /**
     * Copy constructor.
     *
     * @param source the source
     */
    private Settings(Settings source) {
      minScale = source.minScale;
      maxScale = source.maxScale;
      outputDifferenceImage = source.outputDifferenceImage;
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

  @Override
  public int setup(String arg, ImagePlus imp) {
    UsageTracker.recordPlugin(this.getClass(), arg);
    this.imp = imp;
    return showDialog();
  }

  private int showDialog() {
    settings = Settings.load();

    final GenericDialog gd = new GenericDialog(TITLE);
    gd.addMessage("Computes a scale-space representation of an image.\n"
        + "All scales up to the minimum image dimension in X or Y are computed.");
    gd.addNumericField("Min_scale", settings.minScale, 2);
    gd.addNumericField("Max_scale", settings.maxScale, 2);
    gd.addCheckbox("Difference_of_Gaussians", settings.outputDifferenceImage);
    gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.UTILITY);
    gd.showDialog();
    settings.save();
    if (gd.wasCanceled()) {
      return DONE;
    }
    settings.minScale = gd.getNextNumber();
    settings.maxScale = gd.getNextNumber();
    settings.outputDifferenceImage = gd.getNextBoolean();

    if (gd.invalidNumber()) {
      IJ.error(TITLE, "Bad input number");
      return DONE;
    }

    return (settings.outputDifferenceImage) ? DOG_FLAGS : FLAGS;
  }

  @Override
  public void run(ImageProcessor ip) {
    final int width = ip.getWidth();
    final int height = ip.getHeight();
    ImageStack stack = new ImageStack(width, height);
    stack.addSlice("t=0", ip);

    // Limits on the scale:
    // A Gaussian blur below 0.5 in a single dimension, or
    // a Gaussian blur with a width above the dimension size is meaningless.
    final double limitT = MathUtils.pow2(Math.min(width, height));
    double minT = MathUtils.clip(0.25, limitT, settings.minScale);
    // If max is 0 use the computed max for auto-range
    double maxT =
        MathUtils.clip(minT, limitT, (settings.maxScale > 0 ? settings.maxScale : limitT));

    // This could be multi-threaded using Callable<?>.
    // The returned results would have to be sorted by t.

    // Blur up to the minimum image dimension (above which the Gaussian blur is meaningless)
    final GaussianBlur gb = new GaussianBlur();
    gb.showProgress(false);
    final TDoubleArrayList scales = new TDoubleArrayList();
    for (double scaleT = minT; scaleT <= maxT; scaleT *= 2) {
      scales.add(scaleT);
      // Gaussian blur. Since it is 2D use sqrt(t) for each dimension
      final double sigma = Math.sqrt(scaleT);
      ImageProcessor scaledIp = ip.duplicate();
      if (settings.outputDifferenceImage) {
        // Float-image required for correct difference image
        scaledIp = scaledIp.toFloat(0, null);
      }
      gb.blurGaussian(scaledIp, sigma);
      stack.addSlice("t=" + MathUtils.rounded(scaleT), scaledIp);
    }

    String suffix;
    if (settings.outputDifferenceImage && stack.getSize() > 2) {
      suffix = "Difference of Gaussians";
      final ImageStack diffStack = new ImageStack(width, height);
      // Ignore first blur minus no blur
      float[] pixels2 = (float[]) stack.getPixels(2);
      for (int slice = 3; slice <= stack.size(); slice++) {
        final float[] pixels1 = pixels2;
        pixels2 = (float[]) stack.getPixels(slice);
        // Scale normalised using the smaller scale
        for (int i = 0; i < pixels1.length; i++) {
          final float v = pixels1[i] - pixels2[i];
          pixels1[i] = v;
        }
        diffStack.addSlice(stack.getSliceLabel(slice) + "-" + stack.getSliceLabel(slice - 1),
            pixels1);
      }
      stack = diffStack;
    } else {
      suffix = TITLE;
    }

    ImageJUtils.display(imp.getTitle() + " " + suffix, stack);
  }
}
