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

package uk.ac.sussex.gdsc.foci;

import uk.ac.sussex.gdsc.core.threshold.Histogram;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.BackgroundMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.PeakMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.SearchMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.StatisticsMethod;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

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
 * <p>Supports 8- or 16-bit images
 */
public class FindFociIntProcessor extends FindFociBaseProcessor {
  /** The image. */
  protected int[] image;

  /**
   * Instantiates a new find foci int processor.
   */
  public FindFociIntProcessor() {
    super();
  }

  /**
   * Instantiates a new find foci int processor.
   *
   * @param searchCapacity The search capacity. This is the maximum number of potential maxima to
   *        support (i.e. the upper limit on the ID for a candidate maxima).
   */
  public FindFociIntProcessor(int searchCapacity) {
    super(searchCapacity);
  }

  @Override
  protected Object extractImage(ImagePlus imp) {
    if (imp.getBitDepth() != 8 && imp.getBitDepth() != 16) {
      throw new IllegalArgumentException("Bit-depth not supported: " + imp.getBitDepth());
    }

    final ImageStack stack = imp.getStack();
    final int[] localImage = new int[maxxByMaxyByMaxz];
    final int c = imp.getChannel();
    final int f = imp.getFrame();
    for (int slice = 1, i = 0; slice <= maxz; slice++) {
      final int stackIndex = imp.getStackIndex(c, slice, f);
      final ImageProcessor ip = stack.getProcessor(stackIndex);
      for (int index = 0; index < ip.getPixelCount(); index++) {
        localImage[i++] = ip.get(index);
      }
    }
    return localImage;
  }

  @Override
  protected byte[] createTypesArray(Object pixels) {
    return new byte[maxxByMaxyByMaxz];
  }

  @Override
  protected float getImageMin(Object pixels, byte[] types) {
    final int[] localImage = (int[]) pixels;
    int min = Integer.MAX_VALUE;
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
    final int[] localImage = (int[]) pixels;

    // Fast power of 2 computation
    final int size = 1 << bitDepth;

    final int[] data = new int[size];

    // Assume the method is either either inside or outside
    final int flag = statisticsMethod == StatisticsMethod.INSIDE ? 0 : EXCLUDED;
    for (int i = 0; i < localImage.length; i++) {
      if ((types[i] & EXCLUDED) == flag) {
        data[localImage[i]]++;
      }
    }

    return new Histogram(data);
  }

  @Override
  protected Histogram buildHistogram(int bitDepth, Object pixels) {
    final int[] localImage = ((int[]) pixels);

    final int size = (int) Math.pow(2, bitDepth);

    final int[] data = new int[size];

    for (int i = localImage.length; i-- > 0;) {
      data[localImage[i]]++;
    }

    return new Histogram(data);
  }

  @Override
  protected Histogram buildHistogram(Object pixels, int[] maxima, int peakValue, float maxValue) {
    final int[] localImage = (int[]) pixels;
    final int[] histogram = new int[(int) maxValue + 1];

    for (int i = localImage.length; i-- > 0;) {
      if (maxima[i] == peakValue) {
        histogram[localImage[i]]++;
      }
    }
    return new Histogram(histogram);
  }

  @Override
  protected float getSearchThreshold(BackgroundMethod backgroundMethod, double backgroundParameter,
      FindFociStatistics stats) {
    switch (backgroundMethod) {
      case ABSOLUTE:
        // Ensure all points above the threshold parameter are found
        return round((backgroundParameter >= 0) ? backgroundParameter : 0);

      case AUTO_THRESHOLD:
        return round(stats.background);

      case MEAN:
        return round(stats.backgroundRegionAverage);

      case STD_DEV_ABOVE_MEAN:
        return round(stats.backgroundRegionAverage
            + ((backgroundParameter >= 0) ? backgroundParameter * stats.backgroundRegionStdDev
                : 0));

      case MIN_MASK_OR_ROI:
        return round(stats.regionMinimum);

      case NONE:
      default:
        // Ensure all the maxima are found
        return 0;
    }
  }

  @Override
  protected void setPixels(Object pixels) {
    this.image = (int[]) pixels;
  }

  @Override
  protected float getf(int index) {
    return image[index];
  }

  @Override
  protected int getBackgroundBin(Histogram histogram, float background) {
    return round(background);
  }

  @Override
  protected int getBin(Histogram histogram, int index) {
    return image[index];
  }

  @Override
  protected float getTolerance(SearchMethod searchMethod, double searchParameter,
      FindFociStatistics stats, float v0) {
    switch (searchMethod) {
      case ABOVE_BACKGROUND:
        return round(stats.background);

      case FRACTION_OF_PEAK_MINUS_BACKGROUND:
        return round(stats.background + Math.max(0, searchParameter) * (v0 - stats.background));

      case HALF_PEAK_VALUE:
        return round(stats.background + 0.5 * (v0 - stats.background));

      default:
        return 0;
    }
  }

  @Override
  protected double getPeakHeight(PeakMethod peakMethod, double peakParameter,
      FindFociStatistics stats, float v0) {
    int height = 1;
    final double localPeakParameter = Math.max(0, peakParameter);
    switch (peakMethod) {
      case ABSOLUTE:
        height = round(localPeakParameter);
        break;
      case RELATIVE:
        height = round(v0 * localPeakParameter);
        break;
      case RELATIVE_ABOVE_BACKGROUND:
        height = round((v0 - stats.background) * localPeakParameter);
        break;
      default:
        return height;
    }
    // It should be a peak so ensure it is above zero
    return Math.max(1, height);
  }

  @Override
  public boolean isFloatProcessor() {
    return false;
  }
}
