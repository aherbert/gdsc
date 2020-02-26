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

package uk.ac.sussex.gdsc.ij.plugin;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.measure.Measurements;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;
import java.util.Arrays;

/**
 * Extend the ZProjector to support mode intensity projection.
 *
 * <p>Note: This class extends a copy of the default ImageJ ZProjector so that certain methods and
 * properties can be changed to protected from the private/default scope. Extending a copy allows
 * easier update when the super class changes.
 */
public class ExtendedZProjector extends ZProjectorCopy {
  /** Use Mode projection. */
  public static final int MODE_METHOD = 6;
  /** Use Mode projection (ignoring zero from the image). */
  public static final int MODE_IGNORE_ZERO_METHOD = 7;

  /** The available projection methods. */
  private static final String[] METHODS = {"Average Intensity", "Max Intensity", "Min Intensity",
      "Sum Slices", "Standard Deviation", "Median", "Mode", "Mode (ignore zero)"};

  /**
   * Gets the methods.
   *
   * @return the methods
   */
  public static String[] getMethods() {
    return METHODS.clone();
  }

  @Override
  public void run(String arg) {
    super.run(arg);
    if (projImage != null) {
      // Set the display range
      ImageProcessor ip = projImage.getProcessor();
      if (ip instanceof ByteProcessor) {
        // We do not want the standard 0-255 range for ByteProcessor but the min/max range
        for (int c = 1; c <= projImage.getNChannels(); c++) {
          final int index = projImage.getStackIndex(c, 1, 1);
          projImage.setSliceWithoutUpdate(index);
          ip = projImage.getProcessor();
          final ImageStatistics stats =
              ImageStatistics.getStatistics(ip, Measurements.MIN_MAX, null);
          ip.setMinAndMax(stats.min, stats.max);
        }
        projImage.setSliceWithoutUpdate(projImage.getStackIndex(1, 1, 1));
      } else {
        projImage.resetDisplayRange();
      }
      projImage.updateAndDraw();
    }
  }

  @Override
  protected GenericDialog buildControlDialog(int start, int stop) {
    final GenericDialog gd = new GenericDialog("ZProjection", IJ.getInstance());
    gd.addNumericField("Start slice:", startSlice, 0/* digits */);
    gd.addNumericField("Stop slice:", stopSlice, 0/* digits */);
    gd.addChoice("Projection type", METHODS, METHODS[method]);
    if (isHyperstack && imp.getNFrames() > 1 && imp.getNSlices() > 1) {
      gd.addCheckbox("All time frames", allTimeFrames);
    }
    gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.UTILITY);
    return gd;
  }

  @Override
  protected String makeTitle() {
    final String prefix = getPrefix(method);
    return WindowManager.makeUniqueName(prefix + imp.getTitle());
  }

  private static String getPrefix(int method) {
    switch (method) {
      case AVG_METHOD:
        return "AVG_";
      case SUM_METHOD:
        return "SUM_";
      case MAX_METHOD:
        return "MAX_";
      case MIN_METHOD:
        return "MIN_";
      case SD_METHOD:
        return "STD_";
      case MEDIAN_METHOD:
        return "MED_";
      case MODE_METHOD:
      case MODE_IGNORE_ZERO_METHOD:
        return "MOD_";
      default:
        throw new IllegalStateException("Unknown method: " + method);
    }
  }

  @Override
  public void doProjection() {
    if (imp == null) {
      return;
    }
    sliceCount = 0;
    for (int slice = startSlice; slice <= stopSlice; slice += increment) {
      sliceCount++;
    }
    if (method >= MODE_METHOD) {
      projImage = doModeProjection(method == MODE_IGNORE_ZERO_METHOD);
      return;
    }
    if (method == MEDIAN_METHOD) {
      projImage = doMedianProjection();
      return;
    }

    super.doProjection();
  }

  private interface Projector {
    float value(float[] values);
  }

  private ImagePlus doProjection(String name, Projector projector) {
    IJ.showStatus("Calculating " + name + "...");
    final ImageStack stack = imp.getStack();
    // Check not an RGB stack
    int ptype;
    if (stack.getProcessor(1) instanceof ByteProcessor) {
      ptype = BYTE_TYPE;
    } else if (stack.getProcessor(1) instanceof ShortProcessor) {
      ptype = SHORT_TYPE;
    } else if (stack.getProcessor(1) instanceof FloatProcessor) {
      ptype = FLOAT_TYPE;
    } else {
      IJ.error("Z Project", "Non-RGB stack required");
      return null;
    }
    final ImageProcessor[] slices = new ImageProcessor[sliceCount];
    int index = 0;
    for (int slice = startSlice; slice <= stopSlice; slice += increment) {
      slices[index++] = stack.getProcessor(slice);
    }
    ImageProcessor ip2 = slices[0].duplicate();
    ip2 = ip2.convertToFloat();
    final float[] values = new float[sliceCount];
    final int width = ip2.getWidth();
    final int height = ip2.getHeight();
    final int inc = Math.max(height / 30, 1);
    for (int y = 0, k = 0; y < height; y++) {
      if (y % inc == 0) {
        IJ.showProgress(y, height - 1);
      }
      for (int x = 0; x < width; x++, k++) {
        for (int i = 0; i < sliceCount; i++) {
          values[i] = slices[i].getf(k);
        }
        ip2.setf(k, projector.value(values));
      }
    }
    final ImagePlus projImage = makeOutputImage(imp, (FloatProcessor) ip2, ptype);
    IJ.showProgress(1, 1);
    return projImage;
  }

  /** {@inheritDoc} */
  @Override
  protected ImagePlus doMedianProjection() {
    // Override to change the method for accessing pixel values to getf()
    return doProjection("median", this::median);
  }

  /**
   * Do mode projection.
   *
   * @param ignoreZero the ignore zero flag
   * @return the image plus
   */
  protected ImagePlus doModeProjection(final boolean ignoreZero) {
    return doProjection("mode", values -> getMode(values, ignoreZero));
  }

  /**
   * Return the mode of the array. Return the mode with the highest value in the event of a tie.
   *
   * <p>Sorts the input array in natural order.
   *
   * <p>NaN values are ignored. The mode may be NaN only if the array is zero length or contains
   * only NaN.
   *
   * @param array the array
   * @param ignoreBelowZero Ignore all values less than or equal to zero. If no values are above
   *        zero the return is zero (not NaN).
   * @return The mode
   */
  public static float mode(float[] array, boolean ignoreBelowZero) {
    if (array == null || array.length == 0) {
      return Float.NaN;
    }
    return getMode(array, ignoreBelowZero);
  }

  /**
   * Return the mode of the array. Return the mode with the highest value in the event of a tie.
   *
   * <p>Sorts the input array in natural order.
   *
   * <p>NaN values are ignored. The mode may be NaN only if the array is zero length or contains
   * only NaN.
   *
   * @param array the array
   * @param ignoreBelowZero Ignore all values less than or equal to zero. If no values are above
   *        zero the return is zero (not NaN).
   * @return The mode
   */
  private static float getMode(float[] array, boolean ignoreBelowZero) {
    // Assume array is not null or empty

    Arrays.sort(array);

    // Ignore NaN values.
    // NaN will be placed at the end by the sort.
    int index = array.length - 1;
    while (index >= 0 && Float.isNaN(array[index])) {
      index--;
    }

    if (index < 0) {
      return Float.NaN;
    }

    // At least 1 non NaN value.
    final int length = index + 1;

    int modeCount = 0;
    float mode = 0;

    index = 0;
    if (ignoreBelowZero) {
      while (index < length && array[index] <= 0) {
        index++;
      }
      if (length == index) {
        return 0;
      }
    }

    int currentCount = 1;
    float currentValue = array[index];
    while (++index < length) {
      if (array[index] != currentValue) {
        if (modeCount <= currentCount) {
          modeCount = currentCount;
          mode = currentValue;
        }
        currentCount = 1;
      } else {
        currentCount++;
      }
      currentValue = array[index];
    }
    // Do the final check
    if (modeCount <= currentCount) {
      mode = currentValue;
    }

    return mode;
  }
}
