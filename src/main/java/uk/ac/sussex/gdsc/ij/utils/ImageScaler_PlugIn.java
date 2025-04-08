/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2025 Alex Herbert
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

package uk.ac.sussex.gdsc.ij.utils;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.io.FileSaver;
import ij.measure.Measurements;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import uk.ac.sussex.gdsc.ij.UsageTracker;

/**
 * Scales all planes in an image to the given maximum.
 */
public class ImageScaler_PlugIn implements PlugInFilter {
  private static final String TITLE = "Image Scaler";
  private ImagePlus imp;
  private static double maxValue = 255;
  private static String listFile = "";

  /** {@inheritDoc} */
  @Override
  public int setup(String arg, ImagePlus imp) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    if (!showDialog()) {
      return DONE;
    }
    this.imp = imp;
    return DOES_ALL | NO_IMAGE_REQUIRED;
  }

  private static boolean showDialog() {
    final GenericDialog gd = new GenericDialog(TITLE);

    gd.addMessage("Rescales the maxima of the image(s) to the given value.\n"
        + "Processes the image stack or a set of input images.");
    gd.addNumericField("Max", maxValue, 2);
    gd.addMessage("List file containing full image path, one image per line.");
    gd.addStringField("List file", listFile);
    gd.showDialog();
    gd.addHelp(uk.ac.sussex.gdsc.ij.help.Urls.UTILITY);

    if (gd.wasCanceled()) {
      return false;
    }

    maxValue = gd.getNextNumber();
    listFile = gd.getNextString();

    return true;
  }

  /** {@inheritDoc} */
  @Override
  public void run(ImageProcessor inputProcessor) {
    final boolean listExists = (listFile != null && !listFile.equals(""));
    if (imp == null && !listExists) {
      return;
    }

    if (listExists) {
      run(listFile, maxValue);
    } else {
      run(new ImagePlus[] {imp}, maxValue);
    }
  }

  /**
   * Scales all the images by the same factor so that one image has the specified maximum.
   *
   * @param listFile File listing all the images to scale
   * @param maxValue the max value
   */
  public void run(String listFile, double maxValue) {
    final boolean listExists = (listFile != null && !listFile.equals(""));
    if (!listExists) {
      return;
    }

    if (!new File(listFile).exists()) {
      return;
    }

    final double[] limits = new double[] {Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};

    // Read all images sequentially and find the limits
    try (final BufferedReader input = Files.newBufferedReader(Paths.get(listFile))) {
      String line = null; // not declared within while loop
      while ((line = input.readLine()) != null) {
        if (!new File(line).exists()) {
          continue;
        }
        ImagePlus tmpImp = new ImagePlus(line);
        updateMinAndMax(tmpImp, limits);
        tmpImp.flush();
        tmpImp = null; // Free memory
      }
    } catch (final IOException ex) {
      IJ.error("Failed to read images in input list file: " + listFile);
      return;
    }

    if (limits[1] <= limits[0]) {
      return;
    }

    final double scaleFactor = maxValue / limits[1];

    // Rewrite images
    try (final BufferedReader input = Files.newBufferedReader(Paths.get(listFile))) {
      String line = null; // not declared within while loop
      while ((line = input.readLine()) != null) {
        if (!new File(line).exists()) {
          continue;
        }

        ImagePlus tmpImp = new ImagePlus(line);
        multiply(tmpImp, scaleFactor);
        new FileSaver(tmpImp).save();
        tmpImp.flush();
        tmpImp = null; // Free memory
      }
    } catch (final IOException ex) {
      IJ.error("Failed to re-write images in input list file: " + listFile);
    }
  }

  /**
   * Scales all the images by the same factor so that one image has the specified maximum.
   *
   * @param images the images
   * @param maxValue the max value
   */
  public void run(ImagePlus[] images, double maxValue) {
    final double[] limits = new double[] {Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};

    for (final ImagePlus im : images) {
      updateMinAndMax(im, limits);
    }

    if (limits[1] <= limits[0]) {
      return;
    }

    final double scaleFactor = maxValue / limits[1];

    for (final ImagePlus im : images) {
      multiply(im, scaleFactor);
    }
  }

  private static void updateMinAndMax(ImagePlus imp, double[] limits) {
    final ImageStack stack = imp.getImageStack();
    for (int slice = 1; slice <= stack.getSize(); slice++) {
      final ImageStatistics stats =
          ImageStatistics.getStatistics(stack.getProcessor(slice), Measurements.MIN_MAX, null);
      if (limits[0] > stats.min) {
        limits[0] = stats.min;
      }
      if (limits[1] < stats.max) {
        limits[1] = stats.max;
      }
    }
  }

  private static void multiply(ImagePlus imp, double scaleFactor) {
    final ImageStack stack = imp.getImageStack();
    for (int slice = 1; slice <= stack.getSize(); slice++) {
      stack.getProcessor(slice).multiply(scaleFactor);
    }
    if (imp.isVisible()) {
      imp.resetDisplayRange();
      imp.updateAndDraw();
    }
  }
}
