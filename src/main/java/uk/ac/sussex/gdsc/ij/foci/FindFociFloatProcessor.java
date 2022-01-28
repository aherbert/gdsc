/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2022 Alex Herbert
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

package uk.ac.sussex.gdsc.ij.foci;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import java.util.Arrays;
import uk.ac.sussex.gdsc.core.threshold.FloatHistogram;
import uk.ac.sussex.gdsc.core.threshold.Histogram;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.BackgroundMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.PeakMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.SearchMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.StatisticsMethod;

/**
 * Find the peak intensity regions of an image.
 *
 * <p>All local maxima above threshold are identified. For all other pixels the direction to the
 * highest neighbour pixel is stored (steepest gradient). In order of highest local maxima, regions
 * are only grown down the steepest gradient to a lower pixel. Provides many configuration options
 * for regions growing thresholds.
 *
 * <p>This plugin was based on {@link ij.plugin.filter.MaximumFinder}. Options have been changed to
 * only support greyscale 2D images and 3D stacks and to perform region growing using configurable
 * thresholds. Support for Watershed, Previewing, and Euclidian Distance Map (EDM) have been
 * removed.
 *
 * <p>Stopping criteria for region growing routines are partly based on the options in PRIISM
 * (http://www.msg.ucsf.edu/IVE/index.html).
 *
 * <p>Supports 8-, 16- or 32-bit images. Processing is performed using a float image values.
 */
public class FindFociFloatProcessor extends FindFociBaseProcessor {

  /** The image. */
  protected float[] image;
  /** Cache the bin for each index. */
  protected int[] bin;

  /**
   * Instantiates a new find foci float processor.
   */
  public FindFociFloatProcessor() {
    super();
  }

  /**
   * Instantiates a new find foci float processor.
   *
   * @param searchCapacity The search capacity. This is the maximum number of potential maxima to
   *        support (i.e. the upper limit on the ID for a candidate maxima).
   */
  public FindFociFloatProcessor(int searchCapacity) {
    super(searchCapacity);
  }

  /**
   * Custom class for sorting indexes using a float value.
   */
  private static final class SortData {
    /** The value. */
    final float value;
    /** The index. */
    final int index;

    /**
     * Instantiates a new sort data.
     *
     * @param value the value
     * @param index the index
     */
    SortData(float value, int index) {
      this.value = value;
      this.index = index;
    }

    /**
     * Compare the two sort data objects.
     *
     * <p>This should not be called with NaN or infinite values.
     *
     * @param o1 object 1
     * @param o2 object 2
     * @return the comparison result (-1,0,1)
     */
    static int compare(SortData o1, SortData o2) {
      // Smallest first
      if (o1.value < o2.value) {
        return -1;
      }
      if (o1.value > o2.value) {
        return 1;
      }
      return 0;
    }
  }

  /**
   * Extract the image into a linear array stacked in zyx order.
   */
  @Override
  protected Object extractImage(ImagePlus imp) {
    final ImageStack stack = imp.getStack();
    final float[] localImage = new float[maxxByMaxyByMaxz];
    final int ch = imp.getChannel();
    final int fr = imp.getFrame();
    for (int slice = 1, i = 0; slice <= maxz; slice++) {
      final int stackIndex = imp.getStackIndex(ch, slice, fr);
      final ImageProcessor ip = stack.getProcessor(stackIndex);
      for (int index = 0; index < ip.getPixelCount(); index++) {
        localImage[i++] = ip.getf(index);
      }
    }
    return localImage;
  }

  @Override
  protected byte[] createTypesArray(Object pixels) {
    final float[] localImage = (float[]) pixels;
    final byte[] types = new byte[maxxByMaxyByMaxz];
    // Blank bad pixels
    for (int i = 0; i < types.length; i++) {
      if (!Float.isFinite(localImage[i])) {
        types[i] = EXCLUDED;
      }
    }
    return types;
  }

  @Override
  protected float getImageMin(Object pixels, byte[] types) {
    final float[] localImage = (float[]) pixels;
    float min = Float.POSITIVE_INFINITY;
    for (int i = localImage.length; i-- > 0;) {
      if ((types[i] & EXCLUDED) == 0 && min > localImage[i]) {
        min = localImage[i];
      }
    }
    return min;
  }

  @Override
  protected Histogram buildHistogram(int bitDepth, Object pixels, byte[] types,
      StatisticsMethod statisticsMethod) {
    float[] localImage = ((float[]) pixels).clone();
    // Store the bin for each index we include
    bin = new int[localImage.length];
    int[] indices = new int[localImage.length];
    int count = 0;
    // Assume the method is either either inside or outside
    final int flag = statisticsMethod == StatisticsMethod.INSIDE ? 0 : EXCLUDED;
    for (int i = 0; i < localImage.length; i++) {
      if ((types[i] & EXCLUDED) == flag) {
        localImage[count] = localImage[i];
        indices[count] = i;
        count++;
      }
    }
    if (count < localImage.length) {
      localImage = Arrays.copyOf(localImage, count);
      indices = Arrays.copyOf(indices, count);
    }
    return buildHistogram(localImage, indices);
  }

  /**
   * Build a histogram using all pixels up to the specified length.
   *
   * <p>The input arrays are modified.
   *
   * @param data The image data
   * @param indices the indices
   * @param dataLength the length of the data
   * @return The image histogram
   */
  private FloatHistogram buildHistogram(float[] data, int[] indices) {
    sortData(data, indices);

    float lastValue = data[0];
    int count = 0;

    int size = 0;
    // This can re-use the same array
    float[] value = data;
    int[] histogram = indices;

    for (int i = 0; i < data.length; i++) {
      final float currentValue = data[i];
      if (currentValue != lastValue) {
        for (int j = count; j > 0; j--) {
          // store the bin for the input indices
          bin[indices[i - j]] = size;
        }
        // Since the arrays are reused update after
        value[size] = lastValue;
        histogram[size] = count;
        count = 0;
        size++;
      }
      lastValue = currentValue;
      count++;
    }
    // Final count
    for (int j = count; j > 0; j--) {
      // store the bin for the input indices
      bin[indices[data.length - j]] = size;
    }
    value[size] = lastValue;
    histogram[size] = count;
    size++;

    // Truncate
    if (size < value.length) {
      histogram = Arrays.copyOf(histogram, size);
      value = Arrays.copyOf(value, size);
    }

    return new FloatHistogram(value, histogram);
  }

  @Override
  protected Histogram buildHistogram(int bitDepth, Object pixels) {
    return FloatHistogram.buildHistogram(((float[]) pixels).clone(), true, true);
  }

  @Override
  protected Histogram buildHistogram(Object pixels, int[] maxima, int peakValue, float maxValue) {
    final float[] localImage = (float[]) pixels;
    int size = 0;
    float[] data = new float[100];
    for (int i = localImage.length; i-- > 0;) {
      if (maxima[i] == peakValue) {
        data[size++] = localImage[i];
        if (size == data.length) {
          data = Arrays.copyOf(data, (int) (size * 1.5));
        }
      }
    }
    return FloatHistogram.buildHistogram(Arrays.copyOf(data, size), true, true);
  }

  /**
   * Sort the data and indices.
   *
   * @param data the data
   * @param indices the indices
   * @param dataLength the data length
   */
  private static void sortData(float[] data, int[] indices) {
    // Convert data for sorting.
    // Preserve integers exactly.
    final SortData[] sortData = new SortData[data.length];
    for (int i = data.length; i-- > 0;) {
      sortData[i] = new SortData(data[i], indices[i]);
    }

    Arrays.sort(sortData, SortData::compare);

    // Copy back
    for (int i = data.length; i-- > 0;) {
      data[i] = sortData[i].value;
      indices[i] = sortData[i].index;
    }
  }

  @Override
  protected float getSearchThreshold(BackgroundMethod backgroundMethod, double backgroundParameter,
      FindFociStatistics stats) {
    switch (backgroundMethod) {
      case ABSOLUTE:
        // Ensure all points above the threshold parameter are found
        return (float) backgroundParameter;

      case AUTO_THRESHOLD:
        return (stats.background);

      case MEAN:
        return (float) (stats.backgroundRegionAverage);

      case STD_DEV_ABOVE_MEAN:
        return (float) (stats.backgroundRegionAverage
            + ((backgroundParameter > 0) ? backgroundParameter * stats.backgroundRegionStdDev : 0));

      case MIN_MASK_OR_ROI:
        return (stats.regionMinimum);

      case NONE:
      default:
        // Ensure all the maxima are found. Use Min and not zero to support float images with
        // negative values
        return stats.regionMinimum;
    }
  }

  @Override
  protected void setPixels(Object pixels) {
    this.image = (float[]) pixels;
  }

  @Override
  protected float getf(int index) {
    return image[index];
  }

  @Override
  protected int getBackgroundBin(Histogram histogram, float background) {
    for (int i = histogram.minBin; i < histogram.maxBin; i++) {
      if (histogram.getValue(i) >= background) {
        return i;
      }
    }
    return histogram.maxBin;
  }

  @Override
  protected int getBin(Histogram histogram, int index) {
    // We store the bin for each input index when building the histogram.
    // Equivalent to findBin(histogram, i)
    return this.bin[index];
  }

  /**
   * Find the histogram bin for the index.
   *
   * @param histogram the histogram
   * @param index the index
   * @return the bin
   */
  protected int findBin(Histogram histogram, int index) {
    /* perform binary search - relies on having sorted data */
    final float[] values = ((FloatHistogram) histogram).value;
    final float value = image[index];
    int upper = values.length - 1;
    int lower = 0;

    while (upper - lower > 1) {
      // Unsigned right shift to ensure a positive result
      final int mid = MathUtils.averageIndex(lower, upper);

      if (value >= values[mid]) {
        lower = mid;
      } else {
        upper = mid;
      }
    }

    /* sanity check the result */
    if (value < values[lower] || value >= values[lower + 1]) {
      // The search attempts to find the index for lower which is equal or above the value.
      // Process the exceptional case where we are at the top end of the range
      if (value == values[lower + 1]) {
        return lower + 1;
      }

      return -1;
    }

    return lower;
  }

  @Override
  protected float getTolerance(SearchMethod searchMethod, double searchParameter,
      FindFociStatistics stats, float v0) {
    switch (searchMethod) {
      case ABOVE_BACKGROUND:
        return (stats.background);

      case FRACTION_OF_PEAK_MINUS_BACKGROUND:
        if (searchParameter < 0) {
          searchParameter = 0;
        }
        return (float) (stats.background + searchParameter * (v0 - stats.background));

      case HALF_PEAK_VALUE:
        return (float) (stats.background + 0.5 * (v0 - stats.background));

      default:
        return stats.regionMinimum;
    }
  }

  @Override
  protected double getPeakHeight(PeakMethod peakMethod, double peakParameter,
      FindFociStatistics stats, float v0) {
    double height = 0;
    switch (peakMethod) {
      case ABSOLUTE:
        height = (peakParameter);
        break;
      case RELATIVE:
        height = (v0 * peakParameter);
        break;
      case RELATIVE_ABOVE_BACKGROUND:
        height = ((v0 - stats.background) * peakParameter);
        break;
      default:
        return height;
    }
    if (height <= 0) {
      // This is an edge case that will only happen if peakParameter is zero or below.
      // Just make it small enough that there must be a peak above the saddle point.
      height = ((v0 - stats.background) * 1e-6);
    }
    return height;
  }

  @Override
  public boolean isFloatProcessor() {
    return true;
  }
}
