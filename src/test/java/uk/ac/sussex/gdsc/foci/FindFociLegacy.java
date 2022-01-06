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

package uk.ac.sussex.gdsc.foci;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.plugin.filter.GaussianBlur;
import ij.process.ImageProcessor;
import java.awt.Rectangle;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold;
import uk.ac.sussex.gdsc.threshold.MultiOtsuThreshold_PlugIn;
import uk.ac.sussex.gdsc.utils.GaussianFit_PlugIn;

/**
 * Find the peak intensity regions of an image.
 *
 * <p>This is an old version of the FindFoci algorithm before it was converted to allow 32-bit
 * images. It is used for unit testing to ensure the new version functions correctly.
 */
@SuppressWarnings({"javadoc"})
public class FindFociLegacy {
  private static final String TITLE = "FindFoci Legacy";

  private static int isGaussianFitEnabled = 0;
  private static String newLine = System.lineSeparator();

  /**
   * The largest number that can be displayed in a 16-bit image.
   *
   * <p>Note searching for maxima uses 32-bit integers but ImageJ can only display 16-bit images.
   */
  private static final int MAXIMA_CAPCITY = 65535;

  /**
   * List of background threshold methods for the dialog.
   */
  public static final String[] backgroundMethods =
      {"Absolute", "Mean", "Std.Dev above mean", "Auto threshold", "Min Mask/ROI", "None"};

  /**
   * The background intensity is set using the input value.
   */
  public static final int BACKGROUND_ABSOLUTE = 0;
  /**
   * The background intensity is set using the mean.
   */
  public static final int BACKGROUND_MEAN = 1;
  /**
   * The background intensity is set as the threshold value field times the standard deviation plus
   * the mean.
   */
  public static final int BACKGROUND_STD_DEV_ABOVE_MEAN = 2;
  /**
   * The background intensity is set using the input auto-threshold method.
   */
  public static final int BACKGROUND_AUTO_THRESHOLD = 3;
  /**
   * The background intensity is set as the minimum image intensity within the ROI or mask.
   */
  public static final int BACKGROUND_MIN_ROI = 4;
  /**
   * The background intensity is set as 0. Equivalent to using {@link #BACKGROUND_ABSOLUTE} with a
   * value of zero.
   */
  public static final int BACKGROUND_NONE = 5;

  /**
   * List of search methods for the dialog.
   */
  public static final String[] searchMethods =
      {"Above background", "Fraction of peak - background", "Half peak value"};

  /**
   * A region is grown until the intensity drops below the background.
   */
  public static final int SEARCH_ABOVE_BACKGROUND = 0;
  /**
   * A region is grown until the intensity drops to: background + (parameter value) * (peak
   * intensity - background).
   */
  public static final int SEARCH_FRACTION_OF_PEAK_MINUS_BACKGROUND = 1;
  /**
   * A region is grown until the intensity drops to halfway between the value at the peak (the seed
   * for the region) and the background level. This is equivalent to using the "fraction of peak -
   * background" option with the threshold value set to 0.5.
   */
  public static final int SEARCH_HALF_PEAK_VALUE = 2;

  /**
   * List of peak height methods for the dialog.
   */
  public static final String[] peakMethods =
      {"Absolute height", "Relative height", "Relative above background"};

  /**
   * The peak must be an absolute height above the highest saddle point.
   */
  public static final int PEAK_ABSOLUTE = 0;
  /**
   * The peak must be a relative height above the highest saddle point. The height is calculated as
   * peak intensity * threshold value. The threshold value should be between 0 and 1.
   */
  public static final int PEAK_RELATIVE = 1;
  /**
   * The peak must be a relative height above the highest saddle point. The height is calculated as
   * (peak intensity - background) * threshold value. The threshold value should be between 0 and 1.
   */
  public static final int PEAK_RELATIVE_ABOVE_BACKGROUND = 2;

  /**
   * List of peak height methods for the dialog.
   */
  public static final String[] maskOptions = {"(None)", "Peaks", "Threshold", "Peaks above saddle",
      "Threshold above saddle", "Fraction of intensity", "Fraction height above background"};

  /**
   * Output the peak statistics to a results window.
   */
  public static final int OUTPUT_RESULTS_TABLE = 1;
  /**
   * Create an output mask with values corresponding to the peak Ids.
   */
  public static final int OUTPUT_MASK_PEAKS = 2;
  /**
   * Mark the peak locations on the input ImagePlus using point ROIs.
   */
  public static final int OUTPUT_ROI_SELECTION = 4;
  /**
   * Output processing messages to the log window.
   */
  public static final int OUTPUT_LOG_MESSAGES = 8;
  /**
   * Mark the peak locations on the mask ImagePlus using point ROIs.
   */
  public static final int OUTPUT_MASK_ROI_SELECTION = 16;
  /**
   * Create an output mask with each peak region thresholded using the auto-threshold method.
   */
  public static final int OUTPUT_MASK_THRESHOLD = 32;
  /**
   * Create an output mask showing only pixels above the peak's highest saddle value.
   */
  public static final int OUTPUT_MASK_ABOVE_SADDLE = 64;
  /**
   * Do not mark the peaks location on the output mask using a single pixel dot. The pixel dot will
   * use the brightest available value, which is the number of maxima + 1.
   */
  public static final int OUTPUT_MASK_NO_PEAK_DOTS = 128;
  /**
   * Create an output mask showing only the pixels contributing to a cumulative fraction of the
   * peak's total intensity.
   */
  public static final int OUTPUT_MASK_FRACTION_OF_INTENSITY = 256;
  /**
   * Create an output mask showing only pixels above a fraction of the peak's highest value.
   */
  public static final int OUTPUT_MASK_FRACTION_OF_HEIGHT = 512;
  /**
   * Output the peak statistics to a results window.
   */
  public static final int OUTPUT_CLEAR_RESULTS_TABLE = 1024;
  /**
   * When marking the peak locations on the input ImagePlus using point ROIs hide the number labels.
   */
  public static final int OUTPUT_HIDE_LABELS = 2048;
  /**
   * Overlay the mask on the image.
   */
  public static final int OUTPUT_OVERLAY_MASK = 4096;
  /**
   * Overlay the ROI points on the image (preserving any current ROI).
   */
  public static final int OUTPUT_ROI_USING_OVERLAY = 8192;
  /**
   * Create an output mask.
   */
  public static final int CREATE_OUTPUT_MASK =
      OUTPUT_MASK_PEAKS | OUTPUT_MASK_THRESHOLD | OUTPUT_OVERLAY_MASK;
  /**
   * Show an output mask.
   */
  public static final int OUTPUT_MASK = OUTPUT_MASK_PEAKS | OUTPUT_MASK_THRESHOLD;

  /**
   * The index of the minimum in the results statistics array.
   */
  public static final int STATS_MIN = 0;
  /**
   * The index of the maximum in the results statistics array.
   */
  public static final int STATS_MAX = 1;
  /**
   * The index of the mean in the results statistics array.
   */
  public static final int STATS_AV = 2;
  /**
   * The index of the standard deviation in the results statistics array.
   */
  public static final int STATS_SD = 3;
  /**
   * The index of the total image intensity in the results statistics array.
   */
  public static final int STATS_SUM = 4;
  /**
   * The index of the image background level in the results statistics array (see
   * {@link #BACKGROUND_AUTO_THRESHOLD}).
   */
  public static final int STATS_BACKGROUND = 5;
  /**
   * The index of the total image intensity above the background in the results statistics array.
   */
  public static final int STATS_SUM_ABOVE_BACKGROUND = 6;
  /**
   * The index of the minimum of the background region in the results statistics array.
   */
  public static final int STATS_MIN_BACKGROUND = 7;
  /**
   * The index of the maximum of the background region in the results statistics array.
   */
  public static final int STATS_MAX_BACKGROUND = 8;
  /**
   * The index of the mean of the background region in the results statistics array.
   */
  public static final int STATS_AV_BACKGROUND = 9;
  /**
   * The index of the standard deviation of the background region in the results statistics array.
   */
  public static final int STATS_SD_BACKGROUND = 10;

  /**
   * The index of the peak X coordinate within the result int[] array of the results object.
   */
  public static final int RESULT_X = 0;
  /**
   * The index of the peak Y coordinate within the result int[] array of the results object.
   */
  public static final int RESULT_Y = 1;
  /**
   * The index of the peak Z coordinate within the result int[] array of the results object.
   */
  public static final int RESULT_Z = 2;
  /**
   * The index of the internal ID used during the FindFoci routine within the result int[] array of
   * the results object. This can be ignored.
   */
  public static final int RESULT_PEAK_ID = 3;
  /**
   * The index of the number of pixels in the peak within the result int[] array of the results
   * object.
   */
  public static final int RESULT_COUNT = 4;
  /**
   * The index of the sum of the peak intensity within the result int[] array of the results object.
   */
  public static final int RESULT_INTENSITY = 5;
  /**
   * The index of the peak maximum value within the result int[] array of the results object.
   */
  public static final int RESULT_MAX_VALUE = 6;
  /**
   * The index of the peak highest saddle point within the result int[] array of the results object.
   */
  public static final int RESULT_HIGHEST_SADDLE_VALUE = 7;
  /**
   * The index of the peak highest saddle point within the result int[] array of the results object.
   */
  public static final int RESULT_SADDLE_NEIGHBOUR_ID = 8;
  /**
   * The index of the average of the peak intensity within the result int[] array of the results
   * object.
   */
  public static final int RESULT_AVERAGE_INTENSITY = 9;
  /**
   * The index of the sum of the peak intensity above the background within the result int[] array
   * of the results object.
   */
  public static final int RESULT_INTENSITY_MINUS_BACKGROUND = 10;
  /**
   * The index of the sum of the peak intensity within the result int[] array of the results object.
   */
  public static final int RESULT_AVERAGE_INTENSITY_MINUS_BACKGROUND = 11;
  /**
   * The index of the number of pixels in the peak above the highest saddle within the result int[]
   * array of the results object.
   */
  public static final int RESULT_COUNT_ABOVE_SADDLE = 12;
  /**
   * The index of the sum of the peak intensity above the highest saddle within the result int[]
   * array of the results object.
   */
  public static final int RESULT_INTENSITY_ABOVE_SADDLE = 13;
  /**
   * The index of the custom sort value within the result int[] array of the results object. This is
   * used internally to sort the results using values not stored in the result array.
   */
  private static final int RESULT_CUSTOM_SORT_VALUE = 14;
  // The length of the result array
  private static final int RESULT_LENGTH = 17;

  // The indices used within the saddle array
  private static final int SADDLE_PEAK_ID = 0;
  private static final int SADDLE_VALUE = 1;

  /**
   * The list of recognised methods for sorting the results.
   */
  public static final String[] sortIndexMethods = {"Size", "Total intensity", "Max value",
      "Average intensity", "Total intensity minus background", "Average intensity minus background",
      "X", "Y", "Z", "Saddle height", "Size above saddle", "Intensity above saddle",
      "Absolute height", "Relative height >Bg", "Peak ID", "XYZ"};

  /**
   * Sort the peaks using the pixel count.
   */
  public static final int SORT_COUNT = 0;
  /**
   * Sort the peaks using the sum of pixel intensity.
   */
  public static final int SORT_INTENSITY = 1;
  /**
   * Sort the peaks using the maximum pixel value.
   */
  public static final int SORT_MAX_VALUE = 2;
  /**
   * Sort the peaks using the average pixel value.
   */
  public static final int SORT_AVERAGE_INTENSITY = 3;
  /**
   * Sort the peaks using the sum of pixel intensity (minus the background).
   */
  public static final int SORT_INTENSITY_MINUS_BACKGROUND = 4;
  /**
   * Sort the peaks using the average pixel value (minus the background).
   */
  public static final int SORT_AVERAGE_INTENSITY_MINUS_BACKGROUND = 5;
  /**
   * Sort the peaks using the X coordinate.
   */
  public static final int SORT_X = 6;
  /**
   * Sort the peaks using the Y coordinate.
   */
  public static final int SORT_Y = 7;
  /**
   * Sort the peaks using the Z coordinate.
   */
  public static final int SORT_Z = 8;
  /**
   * Sort the peaks using the saddle height.
   */
  public static final int SORT_SADDLE_HEIGHT = 9;
  /**
   * Sort the peaks using the pixel count above the saddle height.
   */
  public static final int SORT_COUNT_ABOVE_SADDLE = 10;
  /**
   * Sort the peaks using the sum of pixel intensity above the saddle height.
   */
  public static final int SORT_INTENSITY_ABOVE_SADDLE = 11;
  /**
   * Sort the peaks using the absolute height above the highest saddle.
   */
  public static final int SORT_ABSOLUTE_HEIGHT = 12;
  /**
   * Sort the peaks using the relative height above the background.
   */
  public static final int SORT_RELATIVE_HEIGHT_ABOVE_BACKGROUND = 13;
  /**
   * Sort the peaks using the peak Id.
   */
  private static final int SORT_PEAK_ID = 14;
  /**
   * Sort the peaks using the XYZ coordinates (in order).
   */
  public static final int SORT_XYZ = 15;

  /**
   * Apply the minimum size criteria to the peak size above the highest saddle point.
   */
  public static final int OPTION_MINIMUM_ABOVE_SADDLE = 1;
  /**
   * Calculate the statistics using the pixels outside the ROI/Mask (default is all pixels).
   */
  public static final int OPTION_STATS_OUTSIDE = 2;
  /**
   * Calculate the statistics using the pixels inside the ROI/Mask (default is all pixels).
   */
  public static final int OPTION_STATS_INSIDE = 4;
  /**
   * Remove any maxima that touch the edge of the image.
   */
  public static final int OPTION_REMOVE_EDGE_MAXIMA = 8;
  /**
   * Identify all connected non-zero mask pixels with the same value as objects and label the maxima
   * as belonging to each object.
   */
  public static final int OPTION_OBJECT_ANALYSIS = 16;
  /**
   * Show the object mask calculated during the object analysis.
   */
  public static final int OPTION_SHOW_OBJECT_MASK = 32;
  /**
   * Save the results to memory (allows other plugins to obtain the results).
   */
  public static final int OPTION_SAVE_TO_MEMORY = 64;

  private static String[] centreMethods = {"Max value (search image)", "Max value (original image)",
      "Centre of mass (search image)", "Centre of mass (original image)", "Gaussian (search image)",
      "Gaussian (original image)"};

  static {
    isGaussianFitEnabled = (GaussianFit_PlugIn.isFittingEnabled()) ? 1 : -1;
    if (isGaussianFitEnabled < 1) {
      centreMethods = Arrays.copyOf(centreMethods, centreMethods.length - 2);

      // Debug the reason why fitting is disabled
      if (IJ.shiftKeyDown()) {
        IJ.log("Gaussian fitting is not enabled:" + newLine + GaussianFit_PlugIn.getErrorMessage());
      }
    }
  }

  /**
   * List of methods for defining the centre of each peak.
   */
  public static String[] getCentreMethods() {
    return centreMethods;
  }

  /**
   * Define the peak centre using the highest pixel value of the search image (default). In the case
   * of multiple highest value pixels, the closest pixel to the geometric mean of their coordinates
   * is used.
   */
  public static final int CENTRE_MAX_VALUE_SEARCH = 0;
  /**
   * Re-map peak centre using the highest pixel value of the original image.
   */
  public static final int CENTRE_MAX_VALUE_ORIGINAL = 1;
  /**
   * Re-map peak centre using the peak centre of mass (COM) around the search image. The COM is
   * computed within a given volume of the highest pixel value. Only pixels above the saddle height
   * are used to compute the fit. The volume is specified using 2xN+1 where N is the centre
   * parameter.
   */
  public static final int CENTRE_OF_MASS_SEARCH = 2;
  /**
   * Re-map peak centre using the peak centre of mass (COM) around the original image.
   */
  public static final int CENTRE_OF_MASS_ORIGINAL = 3;
  /**
   * Re-map peak centre using a Gaussian fit on the search image. Only pixels above the saddle
   * height are used to compute the fit. The fit is performed in 2D using a projection along the
   * z-axis. If the centre parameter is 1 a maximum intensity projection is used; else an average
   * intensity project is used. The z-coordinate is computed using the centre of mass along the
   * projection axis located at the xy centre.
   */
  public static final int CENTRE_GAUSSIAN_SEARCH = 4;
  /**
   * Re-map peak centre using a Gaussian fit on the original image.
   */
  public static final int CENTRE_GAUSSIAN_ORIGINAL = 5;

  /**
   * The list of recognised methods for auto-thresholding.
   */
  public static final String[] autoThresholdMethods = {"Default", "Huang", "Intermodes", "IsoData",
      "Li", "MaxEntropy", "Mean", "MinError(I)", "Minimum", "Moments", "Otsu", "Otsu_3_Level",
      "Otsu_4_Level", "Percentile", "RenyiEntropy", "Shanbhag", "Triangle", "Yen"};
  public static final String[] statisticsModes = {"Both", "Inside", "Outside"};

  // the following are class variables for having shorter argument lists
  private int maxx, maxy, maxz; // image dimensions
  private int xlimit, ylimit, zlimit;
  private int maxx_maxy, maxx_maxy_maxz;
  private int[] offset;
  private int dStart;
  private boolean[] flatEdge;
  private Rectangle bounds = null;

  // The following arrays are built for a 3D search through the following z-order: (0,-1,1)
  // Each 2D plane is built for a search round a pixel in an anti-clockwise direction.
  // Note the x==y==z==0 element is not present. Thus there are blocks of 8,9,9 for each plane.
  // This preserves the isWithin() functionality of ij.plugin.filter.MaximumFinder.

  //@formatter:off
  private final int[] DIR_X_OFFSET = new int[] { 0, 1, 1, 1, 0,-1,-1,-1, 0, 1, 1, 1, 0,-1,-1,-1, 0, 0, 1, 1, 1, 0,-1,-1,-1, 0 };
  private final int[] DIR_Y_OFFSET = new int[] {-1,-1, 0, 1, 1, 1, 0,-1,-1,-1, 0, 1, 1, 1, 0,-1, 0,-1,-1, 0, 1, 1, 1, 0,-1, 0 };
  private final int[] DIR_Z_OFFSET = new int[] { 0, 0, 0, 0, 0, 0, 0, 0,-1,-1,-1,-1,-1,-1,-1,-1,-1, 1, 1, 1, 1, 1, 1, 1, 1, 1 };
  //@formatter:on

  /* the following constants are used to set bits corresponding to pixel types */
  private static final byte EXCLUDED = (byte) 1; // marks points outside the ROI
  private static final byte MAXIMUM = (byte) 2; // marks local maxima (irrespective of noise
                                                // tolerance)
  private static final byte LISTED = (byte) 4; // marks points currently in the list
  private static final byte MAX_AREA = (byte) 8; // marks areas near a maximum, within the tolerance
  private static final byte SADDLE = (byte) 16; // marks a potential saddle between maxima
  private static final byte SADDLE_POINT = (byte) 32; // marks a saddle between maxima
  private static final byte SADDLE_WITHIN = (byte) 64; // marks a point within a maxima next to a
                                                       // saddle
  private static final byte PLATEAU = (byte) 128; // marks a point as a plateau region

  private static final byte BELOW_SADDLE = (byte) 128; // marks a point as falling below the highest
                                                       // saddle point

  private static final byte IGNORE = EXCLUDED | LISTED; // marks point to be ignored in stage 1

  private static String getStatisticsMode(int options) {
    if ((options & (OPTION_STATS_INSIDE | OPTION_STATS_OUTSIDE)) == (OPTION_STATS_INSIDE
        | OPTION_STATS_OUTSIDE)) {
      return "Both";
    }
    if ((options & OPTION_STATS_INSIDE) != 0) {
      return "Inside";
    }
    if ((options & OPTION_STATS_OUTSIDE) != 0) {
      return "Outside";
    }
    return "Both";
  }

  /**
   * Here the processing is done: Find the maxima of an image.
   *
   * <p>Local maxima are processed in order, highest first. Regions are grown from local maxima
   * until a saddle point is found or the stopping criteria are met (based on pixel intensity). If a
   * peak does not meet the peak criteria (min size) it is absorbed into the highest peak that
   * touches it (if a neighbour peak exists). Only a single iteration is performed and consequently
   * peak absorption could produce sub-optimal results due to greedy peak growth.
   *
   * <p>Peak expansion stopping criteria are defined using the method parameter. See
   * {@link #SEARCH_ABOVE_BACKGROUND}; {@link #SEARCH_FRACTION_OF_PEAK_MINUS_BACKGROUND};
   * {@link #SEARCH_HALF_PEAK_VALUE}.
   *
   * @param imp The input image
   * @param mask A mask image used to define the region to search for peaks
   * @param backgroundMethod Method for calculating the background level (use the constants with
   *        prefix BACKGROUND_)
   * @param backgroundParameter parameter for calculating the background level
   * @param autoThresholdMethod The thresholding method (use a string from
   *        {@link #autoThresholdMethods } )
   * @param searchMethod Method for calculating the region growing stopping criteria (use the
   *        constants with prefix SEARCH_)
   * @param searchParameter parameter for calculating the stopping criteria
   * @param maxPeaks The maximum number of peaks to report
   * @param minSize The minimum size for a peak
   * @param peakMethod Method for calculating the minimum peak height above the highest saddle (use
   *        the constants with prefix PEAK_)
   * @param peakParameter parameter for calculating the minimum peak height
   * @param outputType Use {@link #OUTPUT_MASK_PEAKS} to get an ImagePlus in the result Object
   *        array. Use {@link #OUTPUT_LOG_MESSAGES} to get runtime information.
   * @param sortIndex The index of the result statistic to use for the peak sorting
   * @param options An options flag (use the constants with prefix OPTION_)
   * @param blur Apply a Gaussian blur of the specified radius before processing (helps smooth noisy
   *        images for better peak identification)
   * @param centreMethod Define the method used to calculate the peak centre (use the constants with
   *        prefix CENTRE_)
   * @param centreParameter Parameter for calculating the peak centre
   * @param fractionParameter Used to specify the fraction of the peak to show in the mask
   * @return Object array containing: (1) a new ImagePlus (with a stack) where the maxima are set to
   *         nMaxima+1 and peak areas numbered starting from nMaxima (Background 0). Pixels outside
   *         of the roi of the input ip are not set. Alternatively the peak areas can be thresholded
   *         using the auto-threshold method and coloured 1(saddle), 2(background), 3(threshold),
   *         4(peak); (2) a result ArrayList<int[]> with details of the maxima. The details can be
   *         extracted for each result using the constants defined with the prefix RESULT_; (3) the
   *         image statistics double[] array. The details can be extracted using the constants
   *         defined with the STATS_ prefix. Returns null if cancelled by escape.
   */
  public Object[] findMaxima(ImagePlus imp, ImagePlus mask, int backgroundMethod,
      double backgroundParameter, String autoThresholdMethod, int searchMethod,
      double searchParameter, int maxPeaks, int minSize, int peakMethod, double peakParameter,
      int outputType, int sortIndex, int options, double blur, int centreMethod,
      double centreParameter, double fractionParameter) {
    if (imp.getBitDepth() != 8 && imp.getBitDepth() != 16) {
      IJ.error(TITLE, "Only 8-bit and 16-bit images are supported");
      return null;
    }
    if ((centreMethod == CENTRE_GAUSSIAN_ORIGINAL || centreMethod == CENTRE_GAUSSIAN_SEARCH)
        && isGaussianFitEnabled < 1) {
      IJ.error(TITLE, "Gaussian fit is not currently enabled");
      return null;
    }

    final boolean isLogging = isLogging(outputType);

    if (isLogging) {
      IJ.log("---" + newLine + TITLE + " : " + imp.getTitle());
    }

    // Call first to set up the processing for isWithin;
    initialise(imp);
    IJ.resetEscape();
    final long start = System.currentTimeMillis();
    timingStart();
    final boolean restrictAboveSaddle =
        (options & OPTION_MINIMUM_ABOVE_SADDLE) == OPTION_MINIMUM_ABOVE_SADDLE;

    IJ.showStatus("Initialising memory...");

    final ImagePlus originalImp = imp;
    final int[] originalImage = extractImage(imp);
    int[] image;

    if (blur > 0) {
      // Apply a Gaussian pre-processing step
      imp = applyBlur(imp, blur);
      image = extractImage(imp);
    } else {
      // The images are the same so just copy the reference
      image = originalImage;
    }

    final byte[] types = new byte[maxx_maxy_maxz]; // Will be a notepad for pixel types
    final int[] maxima = new int[maxx_maxy_maxz]; // Contains the maxima Id assigned for each point

    IJ.showStatus("Initialising ROI...");

    // Mark any point outside the ROI as processed
    int exclusion = excludeOutsideROI(originalImp, types, isLogging);
    exclusion += excludeOutsideMask(mask, types, isLogging);

    // The histogram is used to process the levels in the assignpointsToMaxima() routine.
    // So only use those that have not been excluded.
    IJ.showStatus("Building histogram...");

    final int[] histogram = buildHistogram(imp, image, types, OPTION_STATS_INSIDE);
    final double[] stats = getStatistics(histogram);

    int[] statsHistogram = histogram;

    // Set to both by default
    if ((options & (OPTION_STATS_INSIDE | OPTION_STATS_OUTSIDE)) == 0) {
      options |= OPTION_STATS_INSIDE | OPTION_STATS_OUTSIDE;
    }

    // Allow the threshold to be set using pixels outside the mask/ROI, or both inside and outside.
    if (exclusion > 0 && (options & OPTION_STATS_OUTSIDE) != 0) {
      if ((options & OPTION_STATS_INSIDE) != 0) {
        // Both inside and outside
        statsHistogram = buildHistogram(imp, image);
      } else {
        statsHistogram = buildHistogram(imp, image, types, OPTION_STATS_OUTSIDE);
      }
      final double[] newStats = getStatistics(statsHistogram);
      for (int i = 0; i < 4; i++) {
        stats[i + STATS_MIN_BACKGROUND] = newStats[i + STATS_MIN];
      }
    }

    if (isLogging) {
      recordStatistics(stats, exclusion, options);
    }

    if (ImageJUtils.isInterrupted()) {
      return null;
    }

    // Calculate the auto-threshold if necessary
    if (backgroundMethod == BACKGROUND_AUTO_THRESHOLD) {
      stats[STATS_BACKGROUND] = getThreshold(autoThresholdMethod, statsHistogram);
    }
    statsHistogram = null;

    IJ.showStatus("Getting sorted maxima...");
    stats[STATS_BACKGROUND] = getSearchThreshold(backgroundMethod, backgroundParameter, stats);
    if (isLogging) {
      IJ.log("Background level = " + IJ.d2s(stats[STATS_BACKGROUND], 2));
    }
    Coordinate[] maxpoints = getSortedMaxpoints(image, maxima, types, round(stats[STATS_MIN]),
        round(stats[STATS_BACKGROUND]));

    if (ImageJUtils.isInterrupted() || maxpoints == null) {
      return null;
    }

    if (isLogging) {
      IJ.log("Number of potential maxima = " + maxpoints.length);
    }
    IJ.showStatus("Analyzing maxima...");

    final ArrayList<int[]> resultsArray = new ArrayList<>(maxpoints.length);

    assignMaxima(maxima, maxpoints, resultsArray);

    // Free memory
    maxpoints = null;

    assignpointsToMaxima(image, histogram, types, stats, maxima);

    if (ImageJUtils.isInterrupted()) {
      return null;
    }

    if (isLogging) {
      timingSplit("Assigned maxima");
    }

    // Remove points below the peak growth criteria
    pruneMaxima(image, types, searchMethod, searchParameter, stats, resultsArray, maxima);

    // Calculate the initial results (peak size and intensity)
    calculateInitialResults(image, maxima, resultsArray);

    IJ.showStatus("Finding saddle points...");

    // Calculate the highest saddle point for each peak
    final ArrayList<LinkedList<int[]>> saddlePoints = new ArrayList<>(resultsArray.size() + 1);
    findSaddlePoints(image, types, resultsArray, maxima, saddlePoints);

    if (ImageJUtils.isInterrupted()) {
      return null;
    }

    // Find the peak sizes above their saddle points.
    analysePeaks(resultsArray, image, maxima);

    if (isLogging) {
      timingSplit("Mapped saddle points");
    }

    IJ.showStatus("Merging peaks...");

    // Combine maxima below the minimum peak criteria to adjacent peaks (or eliminate if no
    // neighbours)
    final int originalNumberOfPeaks = resultsArray.size();
    mergeSubPeaks(resultsArray, image, maxima, minSize, peakMethod, peakParameter, stats,
        saddlePoints, isLogging, restrictAboveSaddle);

    if (isLogging) {
      timingSplit("Merged peaks");
    }

    if (ImageJUtils.isInterrupted()) {
      return null;
    }

    if ((options & OPTION_REMOVE_EDGE_MAXIMA) != 0) {
      removeEdgeMaxima(resultsArray, image, maxima, stats, isLogging);
    }

    final int totalPeaks = resultsArray.size();

    if (blur > 0) {
      // Recalculate the totals but do not update the saddle values
      // (since basing these on the blur image should give smoother saddle results).
      calculateNativeResults(originalImage, maxima, resultsArray, originalNumberOfPeaks);
    } else // If no blur was applied, then the centre using the original image will be the same as
    // using the search
    if (centreMethod == CENTRE_MAX_VALUE_ORIGINAL) {
      centreMethod = CENTRE_MAX_VALUE_SEARCH;
    }

    stats[STATS_SUM_ABOVE_BACKGROUND] =
        getIntensityAboveBackground(originalImage, types, round(stats[STATS_BACKGROUND]));

    // Calculate the peaks centre and maximum value.
    locateMaxima(originalImage, image, maxima, types, resultsArray, originalNumberOfPeaks,
        centreMethod, centreParameter);

    // Calculate the average intensity and values minus background
    calculateFinalResults(resultsArray, round(stats[STATS_BACKGROUND]));

    // Reorder the results
    sortDescResults(resultsArray, sortIndex, stats);

    // Only return the best results
    while (resultsArray.size() > maxPeaks) {
      resultsArray.remove(resultsArray.size() - 1);
    }

    final int nMaxima = resultsArray.size();
    if (isLogging) {
      String message = "Final number of peaks = " + nMaxima;
      if (nMaxima < totalPeaks) {
        message += " / " + totalPeaks;
      }
      IJ.log(message);
    }

    if (isLogging) {
      timingSplit("Calulated results");
    }

    // Build the output mask
    ImagePlus outImp = null;
    if ((outputType & CREATE_OUTPUT_MASK) != 0) {
      IJ.showStatus("Generating mask image...");

      outImp = generateOutputMask(outputType, autoThresholdMethod, imp, fractionParameter, image,
          types, maxima, stats, resultsArray, nMaxima);

      if (outImp == null) {
        IJ.error(TITLE, "Too many maxima to display in a 16-bit image: " + nMaxima);
      }

      if (isLogging) {
        timingSplit("Calulated output mask");
      }
    }

    renumberPeaks(resultsArray, originalNumberOfPeaks);

    IJ.showTime(imp, start, "Done ", maxz);

    return new Object[] {outImp, resultsArray, stats};
  }

  private static int getThreshold(String autoThresholdMethod, int[] statsHistogram) {
    if (autoThresholdMethod.endsWith("evel")) {
      final MultiOtsuThreshold_PlugIn multi = new MultiOtsuThreshold_PlugIn();
      // Configure the algorithm
      statsHistogram[0] = 0; // Ignore zero
      final int level = autoThresholdMethod.contains("_3_") ? 3 : 4;
      final int[] threshold = multi.calculateThresholds(statsHistogram, level);
      return threshold[1];
    }
    return AutoThreshold.getThreshold(autoThresholdMethod, statsHistogram);
  }

  private long timestamp;

  private void timingStart() {
    timestamp = System.nanoTime();
  }

  private void timingSplit(String string) {
    final long newTimestamp = System.nanoTime();
    IJ.log(string + " = " + ((newTimestamp - timestamp) / 1000000.0) + " msec");
    timestamp = newTimestamp;
  }

  /**
   * Extract the image into a linear array stacked in zyx order.
   */
  private int[] extractImage(ImagePlus imp) {
    final ImageStack stack = imp.getStack();
    final int[] image = new int[maxx_maxy_maxz];
    final int c = imp.getChannel();
    final int f = imp.getFrame();
    for (int slice = 1, i = 0; slice <= maxz; slice++) {
      final int stackIndex = imp.getStackIndex(c, slice, f);
      final ImageProcessor ip = stack.getProcessor(stackIndex);
      for (int index = 0; index < ip.getPixelCount(); index++) {
        image[i++] = ip.get(index);
      }
    }
    return image;
  }

  /**
   * Apply a Gaussian blur to the image and returns a new image. Returns the original image if blur
   * <= 0.
   *
   * <p>Only blurs the current channel and frame for use in the FindFoci algorithm.
   *
   * @param imp the image
   * @param blur The blur standard deviation
   * @return the blurred image
   */
  public static ImagePlus applyBlur(ImagePlus imp, double blur) {
    if (blur > 0) {
      // Note: imp.duplicate() crops the image if there is an ROI selection
      // so duplicate each ImageProcessor instead.
      final GaussianBlur gb = new GaussianBlur();
      final ImageStack stack = imp.getImageStack();
      final ImageStack newStack =
          new ImageStack(stack.getWidth(), stack.getHeight(), stack.getSize());
      final int channel = imp.getChannel();
      final int frame = imp.getFrame();
      final int[] dim = imp.getDimensions();
      // Copy the entire stack
      for (int slice = 1; slice <= stack.getSize(); slice++) {
        newStack.setPixels(stack.getProcessor(slice).getPixels(), slice);
      }
      // Now blur the current channel and frame
      for (int slice = 1; slice <= dim[3]; slice++) {
        final int stackIndex = imp.getStackIndex(channel, slice, frame);
        final ImageProcessor ip = stack.getProcessor(stackIndex).duplicate();
        gb.blurGaussian(ip, blur, blur, 0.0002);
        newStack.setPixels(ip.getPixels(), stackIndex);
      }
      imp = new ImagePlus(null, newStack);
      imp.setDimensions(dim[2], dim[3], dim[4]);
      imp.setC(channel);
      imp.setT(frame);
    }
    return imp;
  }

  private ImagePlus generateOutputMask(int outputType, String autoThresholdMethod, ImagePlus imp,
      double fractionParameter, int[] image, byte[] types, int[] maxima, double[] stats,
      ArrayList<int[]> resultsArray, int nMaxima) {
    final String imageTitle = imp.getTitle();
    // Rebuild the mask: all maxima have value 1, the remaining peak area are numbered sequentially
    // starting
    // with value 2.
    // First create byte values to use in the mask for each maxima
    final int[] maximaValues = new int[nMaxima];
    final int[] maximaPeakIds = new int[nMaxima];
    final int[] displayValues = new int[nMaxima];

    if ((outputType & (OUTPUT_MASK_ABOVE_SADDLE | OUTPUT_MASK_FRACTION_OF_INTENSITY
        | OUTPUT_MASK_FRACTION_OF_HEIGHT)) != 0) {
      if ((outputType & OUTPUT_MASK_FRACTION_OF_HEIGHT) != 0) {
        fractionParameter = Math.max(Math.min(1 - fractionParameter, 1), 0);
      }

      // Reset unneeded flags in the types array since new flags are required to mark pixels below
      // the cut-off height.
      final byte resetFlag = (byte) (SADDLE_POINT | MAX_AREA);
      for (int i = types.length; i-- > 0;) {
        types[i] &= resetFlag;
      }
    } else {
      // Ensure no pixels are below the saddle height
      Arrays.fill(displayValues, -1);
    }

    for (int i = 0; i < nMaxima; i++) {
      maximaValues[i] = nMaxima - i;
      final int[] result = resultsArray.get(i);
      maximaPeakIds[i] = result[RESULT_PEAK_ID];
      if ((outputType & OUTPUT_MASK_ABOVE_SADDLE) != 0) {
        displayValues[i] = result[RESULT_HIGHEST_SADDLE_VALUE];
      } else if ((outputType & OUTPUT_MASK_FRACTION_OF_HEIGHT) != 0) {
        displayValues[i] = (int) Math
            .round(fractionParameter * (result[RESULT_MAX_VALUE] - stats[STATS_BACKGROUND])
                + stats[STATS_BACKGROUND]);
      }
    }

    if ((outputType & OUTPUT_MASK_FRACTION_OF_INTENSITY) != 0) {
      calculateFractionOfIntensityDisplayValues(fractionParameter, image, maxima, stats,
          maximaPeakIds, displayValues);
    }

    // Now assign the output mask
    for (int index = maxima.length; index-- > 0;) {
      if ((types[index] & MAX_AREA) != 0) {
        // Find the maxima in the list of maxima Ids.
        int i = 0;
        while (i < nMaxima && maximaPeakIds[i] != maxima[index]) {
          i++;
        }
        if (i < nMaxima) {
          if ((image[index] <= displayValues[i])) {
            types[index] |= BELOW_SADDLE;
          }
          maxima[index] = maximaValues[i];
          continue;
        }
      }

      // Fall through condition, reset the value
      maxima[index] = 0;
      types[index] = 0;
    }

    int maxValue = nMaxima;

    if ((outputType & OUTPUT_MASK_THRESHOLD) != 0) {
      // Perform thresholding on the peak regions
      findBorders(maxima, types);
      for (int i = 0; i < nMaxima; i++) {
        thresholdMask(image, maxima, maximaValues[i], autoThresholdMethod, stats);
      }
      invertMask(maxima);
      addBorders(maxima, types);

      // Adjust the values used to create the output mask
      maxValue = 3;
    }

    // Blank any pixels below the saddle height
    if ((outputType & (OUTPUT_MASK_ABOVE_SADDLE | OUTPUT_MASK_FRACTION_OF_INTENSITY
        | OUTPUT_MASK_FRACTION_OF_HEIGHT)) != 0) {
      for (int i = maxima.length; i-- > 0;) {
        if ((types[i] & BELOW_SADDLE) != 0) {
          maxima[i] = 0;
        }
      }
    }

    // Set maxima to a high value
    if ((outputType & OUTPUT_MASK_NO_PEAK_DOTS) == 0) {
      maxValue++;
      for (int i = 0; i < nMaxima; i++) {
        final int[] result = resultsArray.get(i);
        maxima[getIndex(result[RESULT_X], result[RESULT_Y], result[RESULT_Z])] = maxValue;
      }
    }

    // Check the maxima can be displayed
    if (maxValue > MAXIMA_CAPCITY) {
      IJ.log("The number of maxima exceeds the 16-bit capacity used for diplay: " + MAXIMA_CAPCITY);
      return null;
    }

    // Output the mask
    // The index is '(maxx_maxy) * z + maxx * y + x' so we can simply iterate over the array if we
    // use z, y, x order
    final ImageStack stack = new ImageStack(maxx, maxy, maxz);
    if (maxValue > 255) {
      for (int z = 0, index = 0; z < maxz; z++) {
        final short[] pixels = new short[maxx_maxy];
        for (int i = 0; i < maxx_maxy; i++, index++) {
          pixels[i] = (short) maxima[index];
        }
        stack.setPixels(pixels, z + 1);
      }
    } else {
      for (int z = 0, index = 0; z < maxz; z++) {
        final byte[] pixels = new byte[maxx_maxy];
        for (int i = 0; i < maxx_maxy; i++, index++) {
          pixels[i] = (byte) maxima[index];
        }
        stack.setPixels(pixels, z + 1);
      }
    }

    final ImagePlus result = new ImagePlus(imageTitle + " " + TITLE, stack);
    result.setCalibration(imp.getCalibration());
    return result;
  }

  private static void calculateFractionOfIntensityDisplayValues(double fractionParameter,
      int[] image, int[] maxima, double[] stats, int[] maximaPeakIds, int[] displayValues) {
    // For each maxima
    for (int i = 0; i < maximaPeakIds.length; i++) {
      // Histogram all the pixels above background
      final int[] hist = buildHistogram(image, maxima, maximaPeakIds[i], round(stats[STATS_MAX]));

      final int background = (int) Math.floor(stats[STATS_BACKGROUND]);

      // Sum above background
      long sum = 0;
      for (int value = 0; value < hist.length; value++) {
        sum += hist[value] * (value - background);
      }

      // Determine the cut-off using fraction of cumulative intensity
      final long total = (long) (sum * fractionParameter);

      // Find the point in the histogram that exceeds the fraction
      sum = 0;
      int value = hist.length;
      while (value-- > 0) {
        sum += hist[value] * (value - background);
        if (sum > total) {
          break;
        }
      }
      displayValues[i] = value;
    }
  }

  /**
   * Update the peak Ids to use the sorted order.
   */
  private static void renumberPeaks(ArrayList<int[]> resultsArray, int originalNumberOfPeaks) {
    // Build a map between the original peak number and the new sorted order
    final int[] peakIdMap = new int[originalNumberOfPeaks + 1];
    int i = 1;
    for (final int[] result : resultsArray) {
      peakIdMap[result[RESULT_PEAK_ID]] = i++;
    }

    // Update the Ids
    for (final int[] result : resultsArray) {
      result[RESULT_PEAK_ID] = peakIdMap[result[RESULT_PEAK_ID]];
      result[RESULT_SADDLE_NEIGHBOUR_ID] = peakIdMap[result[RESULT_SADDLE_NEIGHBOUR_ID]];
    }
  }

  /**
   * Finds the borders of peak regions.
   *
   * @param maxima the maxima
   * @param types the types
   */
  private void findBorders(int[] maxima, byte[] types) {
    // Note: This is not perfect. There is a problem with regions marked as saddles
    // between 3 or more peaks. This can results in large blocks of saddle regions that
    // are eroded from the outside in. In this case they should be eroded from the inside out.
    // .......Peaks..Correct..Wrong
    // .......1.22....+.+......+.+
    // ........12......+........+
    // ........12......+........+
    // .......1332....+.+.......+
    // .......3332....+..+......+
    // .......3332....+..+......+
    // (Dots inserted to prevent auto-formatting removing spaces)
    // It also only works on the XY plane. However it is fine for an approximation of the peak
    // boundaries.

    final int[] xyz = new int[3];

    for (int index = maxima.length; index-- > 0;) {
      if (maxima[index] == 0) {
        types[index] = 0;
        continue;
      }

      // If a saddle, search around to check if it still a saddle
      if ((types[index] & SADDLE) != 0) {
        // reset unneeded flags
        types[index] &= BELOW_SADDLE;

        getXy(index, xyz);
        final int x = xyz[0];
        final int y = xyz[1];
        final boolean isInnerXy = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);

        // Process the neighbours
        for (int d = 8; d-- > 0;) {
          if (isInnerXy || isWithinXy(x, y, d)) {
            final int index2 = index + offset[d];

            // Check if neighbour is a different peak
            if (maxima[index] != maxima[index2] && maxima[index2] > 0
                && (types[index2] & SADDLE_POINT) != SADDLE_POINT) {
              types[index] |= SADDLE_POINT;
            }
          }
        }
      }

      // If it is not a saddle point then mark it as within the saddle
      if ((types[index] & SADDLE_POINT) == 0) {
        types[index] |= SADDLE_WITHIN;
      }
    }

    for (int z = maxz; z-- > 0;) {
      cleanupExtraLines(types, z);
      cleanupExtraCornerPixels(types, z);
    }
  }

  /**
   * For each saddle pixel, check the 2 adjacent non-diagonal neighbour pixels in clockwise fashion.
   * If they are both saddle pixels then this pixel can be removed (since they form a diagonal
   * line).
   */
  private int cleanupExtraCornerPixels(byte[] types, int z) {
    int removed = 0;
    final int[] xyz = new int[3];

    for (int i = maxx_maxy, index = maxx_maxy * z; i-- > 0; index++) {
      if ((types[index] & SADDLE_POINT) != 0) {
        getXy(index, xyz);
        final int x = xyz[0];
        final int y = xyz[1];

        final boolean isInner = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);

        final boolean[] edgesSet = new boolean[8];
        for (int d = 8; d-- > 0;) {
          // analyze 4 flat-edge neighbours
          if (isInner || isWithinXy(x, y, d)) {
            edgesSet[d] = ((types[index + offset[d]] & SADDLE_POINT) != 0);
          }
        }

        for (int d = 0; d < 8; d += 2) {
          if ((edgesSet[d] && edgesSet[(d + 2) % 8]) && !edgesSet[(d + 5) % 8]) {
            removed++;
            types[index] &= ~SADDLE_POINT;
            types[index] |= SADDLE_WITHIN;
          }
        }
      }
    }

    return removed;
  }

  /**
   * Delete saddle lines that do not divide two peak areas. Adapted from
   * {@link ij.plugin.filter.MaximumFinder}
   */
  void cleanupExtraLines(byte[] types, int z) {
    for (int i = maxx_maxy, index = maxx_maxy * z; i-- > 0; index++) {
      if ((types[index] & SADDLE_POINT) != 0) {
        final int nRadii = nRadii(types, index); // number of lines radiating
        if (nRadii == 0) // single point or foreground patch?
        {
          types[index] &= ~SADDLE_POINT;
          types[index] |= SADDLE_WITHIN;
        } else if (nRadii == 1) {
          removeLineFrom(types, index);
        }
      }
    }
  }

  /** delete a line starting at x, y up to the next (4-connected) vertex */
  void removeLineFrom(byte[] types, int index) {
    types[index] &= ~SADDLE_POINT;
    types[index] |= SADDLE_WITHIN;
    final int[] xyz = new int[3];
    boolean continues;
    do {
      getXy(index, xyz);
      final int x = xyz[0];
      final int y = xyz[1];

      continues = false;
      final boolean isInner = (y != 0 && y != maxy - 1) && (x != 0 && x != maxx - 1); // not
                                                                                      // necessary,
                                                                                      // but faster
      // than isWithin
      for (int d = 0; d < 8; d += 2) {
        if (isInner || isWithinXy(x, y, d)) {
          final int index2 = index + offset[d];
          final byte v = types[index2];
          if ((v & SADDLE_WITHIN) != SADDLE_WITHIN && (v & SADDLE_POINT) == SADDLE_POINT) {
            final int nRadii = nRadii(types, index2);
            if (nRadii <= 1) { // found a point or line end
              index = index2;
              types[index] &= ~SADDLE_POINT;
              types[index] |= SADDLE_WITHIN; // delete the point
              continues = nRadii == 1; // continue along that line
              break;
            }
          }
        }
      }
    } while (continues);
  }

  /**
   * Analyze the neighbors of a pixel (x, y) in a byte image; pixels <255 ("non-white") are
   * considered foreground. Edge pixels are considered foreground.
   *
   * @param types The byte image
   * @param index coordinate of the point
   * @return Number of 4-connected lines emanating from this point. Zero if the point is embedded in
   *         either foreground or background
   */
  int nRadii(byte[] types, int index) {
    int countTransitions = 0;
    boolean prevPixelSet = true;
    boolean firstPixelSet = true; // initialize to make the compiler happy
    final int[] xyz = new int[3];
    getXy(index, xyz);
    final int x = xyz[0];
    final int y = xyz[1];

    final boolean isInner = (y != 0 && y != maxy - 1) && (x != 0 && x != maxx - 1); // not
                                                                                    // necessary,
                                                                                    // but faster
                                                                                    // than
    // isWithin
    for (int d = 0; d < 8; d++) { // walk around the point and note every no-line->line transition
      boolean pixelSet = prevPixelSet;
      if (isInner || isWithinXy(x, y, d)) {
        final boolean isSet = ((types[index + offset[d]] & SADDLE_WITHIN) == SADDLE_WITHIN);
        if ((d & 1) == 0) {
          pixelSet = isSet; // non-diagonal directions: always regarded
        } else if (!isSet) {
          pixelSet = false; // but are insufficient for a 4-connected line
        }
      } else {
        pixelSet = true;
      }
      if (pixelSet && !prevPixelSet) {
        countTransitions++;
      }
      prevPixelSet = pixelSet;
      if (d == 0) {
        firstPixelSet = pixelSet;
      }
    }
    if (firstPixelSet && !prevPixelSet) {
      countTransitions++;
    }
    return countTransitions;
  }

  /**
   * For each peak in the maxima image, perform thresholding using the specified method.
   *
   * @param image the image
   * @param maxima the maxima
   * @param peakValue the peak value
   * @param autoThresholdMethod the auto threshold method
   * @param stats the stats
   */
  private static void thresholdMask(int[] image, int[] maxima, int peakValue,
      String autoThresholdMethod, double[] stats) {
    final int[] histogram = buildHistogram(image, maxima, peakValue, round(stats[STATS_MAX]));
    final int threshold = getThreshold(autoThresholdMethod, histogram);

    for (int i = maxima.length; i-- > 0;) {
      if (maxima[i] == peakValue) {
        // Use negative to allow use of image in place
        maxima[i] = ((image[i] > threshold) ? -3 : -2);
      }
    }
  }

  /**
   * Build a histogram using only the specified peak area.
   *
   * @param image the image
   * @param maxima the maxima
   * @param peakValue the peak value
   * @param maxValue the max value
   * @return the histogram
   */
  private static int[] buildHistogram(int[] image, int[] maxima, int peakValue, int maxValue) {
    final int[] histogram = new int[maxValue + 1];

    for (int i = image.length; i-- > 0;) {
      if (maxima[i] == peakValue) {
        histogram[image[i]]++;
      }
    }
    return histogram;
  }

  /**
   * Changes all negative value to positive.
   *
   * @param maxima the maxima
   */
  private static void invertMask(int[] maxima) {
    for (int i = maxima.length; i-- > 0;) {
      if (maxima[i] < 0) {
        maxima[i] = -maxima[i];
      }
    }
  }

  /**
   * Adds the borders to the peaks.
   *
   * @param maxima the maxima
   * @param types the types
   */
  private static void addBorders(int[] maxima, byte[] types) {
    for (int i = maxima.length; i-- > 0;) {
      if ((types[i] & SADDLE_POINT) != 0) {
        maxima[i] = 1;
      }
    }
  }

  /**
   * Records the image statistics to the log window.
   *
   * @param stats The statistics
   * @param exclusion non-zero if pixels have been excluded
   * @param options The options (used to get the statistics mode)
   */
  private static void recordStatistics(double[] stats, int exclusion, int options) {
    final StringBuilder sb = new StringBuilder();
    if (exclusion > 0) {
      sb.append("Image stats (inside mask/ROI) : Min = ");
    } else {
      sb.append("Image stats : Min = ");
    }
    sb.append(IJ.d2s(stats[STATS_MIN], 2));
    sb.append(", Max = ").append(IJ.d2s(stats[STATS_MAX], 2));
    sb.append(", Mean = ").append(IJ.d2s(stats[STATS_AV], 2));
    sb.append(", StdDev = ").append(IJ.d2s(stats[STATS_SD], 2));
    IJ.log(sb.toString());

    sb.setLength(0);
    if (exclusion > 0) {
      sb.append("Background stats (mode=").append(getStatisticsMode(options)).append(") : Min = ");
    } else {
      sb.append("Background stats : Min = ");
    }
    sb.append(IJ.d2s(stats[STATS_MIN_BACKGROUND], 2));
    sb.append(", Max = ").append(IJ.d2s(stats[STATS_MAX_BACKGROUND], 2));
    sb.append(", Mean = ").append(IJ.d2s(stats[STATS_AV_BACKGROUND], 2));
    sb.append(", StdDev = ").append(IJ.d2s(stats[STATS_SD_BACKGROUND], 2));
    IJ.log(sb.toString());
  }

  private static int getAbsoluteHeight(int[] result, double noise) {
    int absoluteHeight = 0;
    if (result[RESULT_HIGHEST_SADDLE_VALUE] > 0) {
      absoluteHeight = result[RESULT_MAX_VALUE] - result[RESULT_HIGHEST_SADDLE_VALUE];
    } else {
      absoluteHeight = round(result[RESULT_MAX_VALUE] - noise);
    }
    return absoluteHeight;
  }

  private static double getRelativeHeight(int[] result, double noise, int absoluteHeight) {
    return absoluteHeight / (result[RESULT_MAX_VALUE] - noise);
  }

  /**
   * Set all pixels outside the ROI to PROCESSED.
   *
   * @param imp The input image
   * @param types The types array used within the peak finding routine (same size as imp)
   * @param isLogging True if logging
   * @return 1 if masking was performed, else 0
   */
  private int excludeOutsideROI(ImagePlus imp, byte[] types, boolean isLogging) {
    final Roi roi = imp.getRoi();

    if (roi != null && roi.isArea()) {
      final Rectangle roiBounds = roi.getBounds();

      // Check if this ROI covers the entire image
      if (roi.getType() == Roi.RECTANGLE && roiBounds.width == maxx && roiBounds.height == maxy) {
        return 0;
      }

      // Store the bounds of the ROI for the edge object analysis
      bounds = roiBounds;

      ImageProcessor ipMask = null;
      RoundRectangle2D rr = null;

      // Use the ROI mask if present
      if (roi.getMask() != null) {
        ipMask = roi.getMask();
        if (isLogging) {
          IJ.log("ROI = Mask");
        }
      }
      // Use a mask for an irregular ROI
      else if (roi.getType() == Roi.FREEROI) {
        ipMask = imp.getMask();
        if (isLogging) {
          IJ.log("ROI = Freehand ROI");
        }
      }
      // Use a round rectangle if necessary
      else if (roi.getRoundRectArcSize() != 0) {
        rr = new RoundRectangle2D.Float(roiBounds.x, roiBounds.y, roiBounds.width, roiBounds.height,
            roi.getRoundRectArcSize(), roi.getRoundRectArcSize());
        if (isLogging) {
          IJ.log("ROI = Round ROI");
        }
      }

      // Set everything as processed
      for (int i = types.length; i-- > 0;) {
        types[i] = EXCLUDED;
      }

      // Now unset the ROI region

      // Create a mask from the ROI rectangle
      final int xOffset = roiBounds.x;
      final int yOffset = roiBounds.y;
      final int rwidth = roiBounds.width;
      final int rheight = roiBounds.height;

      for (int y = 0; y < rheight; y++) {
        for (int x = 0; x < rwidth; x++) {
          boolean mask = true;
          if (ipMask != null) {
            mask = (ipMask.get(x, y) > 0);
          } else if (rr != null) {
            mask = rr.contains(x + xOffset, y + yOffset);
          }

          if (mask) {
            // Set each z-slice as excluded
            for (int index = getIndex(x + xOffset, y + yOffset, 0); index < maxx_maxy_maxz;
                index += maxx_maxy) {
              types[index] &= ~EXCLUDED;
            }
          }
        }
      }

      return 1;
    }
    return 0;
  }

  /**
   * Set all pixels outside the Mask to PROCESSED.
   *
   * @param mask the mask
   * @param types The types array used within the peak finding routine
   * @param isLogging the is logging
   * @return 1 if masking was performed, else 0
   */
  private int excludeOutsideMask(ImagePlus mask, byte[] types, boolean isLogging) {
    if (mask == null) {
      return 0;
    }

    // Check sizes in X & Y
    if (mask.getWidth() != maxx || mask.getHeight() != maxy
        || (mask.getNSlices() != maxz && mask.getNSlices() != 1)) {
      if (isLogging) {
        IJ.log("Mask dimensions do not match the image");
      }
      return 0;
    }

    if (isLogging) {
      IJ.log("Mask image = " + mask.getTitle());
    }

    if (mask.getNSlices() == 1) {
      // If a single plane then duplicate through the image
      final ImageProcessor ipMask = mask.getProcessor();

      for (int i = maxx_maxy; i-- > 0;) {
        if (ipMask.get(i) == 0) {
          for (int index = i; index < maxx_maxy_maxz; index += maxx_maxy) {
            types[index] |= EXCLUDED;
          }
        }
      }
    } else {
      // If the same stack size then process through the image
      final ImageStack stack = mask.getStack();
      final int c = mask.getChannel();
      final int f = mask.getFrame();
      for (int slice = 1; slice <= mask.getNSlices(); slice++) {
        final int stackIndex = mask.getStackIndex(c, slice, f);
        final ImageProcessor ipMask = stack.getProcessor(stackIndex);

        int index = maxx_maxy * slice;
        for (int i = maxx_maxy; i-- > 0;) {
          index--;
          if (ipMask.get(i) == 0) {
            types[index] |= EXCLUDED;
          }
        }
      }
    }

    return 1;
  }

  /**
   * Build a histogram using all pixels not marked as EXCLUDED.
   *
   * @param imp the image
   * @param image the image
   * @param types A byte image, same size as ip, where the points can be marked as EXCLUDED
   * @param statsMode OPTION_STATS_INSIDE or OPTION_STATS_OUTSIDE
   * @return The image histogram
   */
  private static int[] buildHistogram(ImagePlus imp, int[] image, byte[] types, int statsMode) {
    // Just in case the image is not 8 or 16-bit
    final int bitDepth = imp.getBitDepth();
    if (bitDepth != 8 && bitDepth != 16) {
      return imp.getProcessor().getHistogram();
    }

    final int size = (int) Math.pow(2, bitDepth);

    final int[] data = new int[size];

    if (statsMode == OPTION_STATS_INSIDE) {
      for (int i = image.length; i-- > 0;) {
        if ((types[i] & EXCLUDED) == 0) {
          data[image[i]]++;
        }
      }
    } else {
      for (int i = image.length; i-- > 0;) {
        if ((types[i] & EXCLUDED) != 0) {
          data[image[i]]++;
        }
      }
    }

    return data;
  }

  /**
   * Build a histogram using all pixels.
   *
   * @param imp the image
   * @param image The image
   * @return The image histogram
   */
  private static int[] buildHistogram(ImagePlus imp, int[] image) {
    // Just in case the image is not 8 or 16-bit
    final int bitDepth = imp.getBitDepth();
    if (bitDepth != 8 && bitDepth != 16) {
      return imp.getProcessor().getHistogram();
    }

    final int size = (int) Math.pow(2, bitDepth);

    final int[] data = new int[size];

    for (int i = image.length; i-- > 0;) {
      data[image[i]]++;
    }

    return data;
  }

  private static double getIntensityAboveBackground(int[] image, byte[] types, int background) {
    long sum = 0;
    for (int i = image.length; i-- > 0;) {
      if ((types[i] & EXCLUDED) == 0 && image[i] > background) {
        sum += (image[i] - background);
      }
    }

    return sum;
  }

  /**
   * Return the image statistics.
   *
   * @param hist The image histogram
   * @return Array containing: min, max, av, stdDev
   */
  private static double[] getStatistics(int[] hist) {
    // Get the limits
    int min = 0;
    int max = hist.length - 1;
    while ((hist[min] == 0) && (min < max)) {
      min++;
    }

    // Check for an empty histogram
    if (min == max && hist[max] == 0) {
      return new double[11];
    }

    while ((hist[max] == 0) && (max > min)) {
      max--;
    }

    // Get the average
    int count;
    double value;
    double sum = 0.0;
    double sum2 = 0.0;
    long n = 0;
    for (int i = min; i <= max; i++) {
      if (hist[i] > 0) {
        count = hist[i];
        n += count;
        value = i;
        sum += value * count;
        sum2 += (value * value) * count;
      }
    }
    final double av = sum / n;

    // Get the Std.Dev
    double stdDev;
    if (n > 0) {
      final double d = n;
      stdDev = (d * sum2 - sum * sum) / d;
      if (stdDev > 0.0) {
        stdDev = Math.sqrt(stdDev / (d - 1.0));
      } else {
        stdDev = 0.0;
      }
    } else {
      stdDev = 0.0;
    }

    return new double[] {min, max, av, stdDev, sum, 0, 0, min, max, av, stdDev};
  }

  /**
   * Get the threshold for searching for maxima.
   *
   * @param backgroundMethod The background thresholding method
   * @param backgroundParameter The method thresholding parameter
   * @param stats The image statistics
   * @return The threshold
   */
  private static int getSearchThreshold(int backgroundMethod, double backgroundParameter,
      double[] stats) {
    switch (backgroundMethod) {
      case BACKGROUND_ABSOLUTE:
        // Ensure all points above the threshold parameter are found
        return round((backgroundParameter >= 0) ? backgroundParameter : 0);

      case BACKGROUND_AUTO_THRESHOLD:
        return round(stats[STATS_BACKGROUND]);

      case BACKGROUND_MEAN:
        return round(stats[STATS_AV_BACKGROUND]);

      case BACKGROUND_STD_DEV_ABOVE_MEAN:
        return round(stats[STATS_AV_BACKGROUND]
            + ((backgroundParameter >= 0) ? backgroundParameter * stats[STATS_SD_BACKGROUND] : 0));

      case BACKGROUND_MIN_ROI:
        return round(stats[STATS_MIN]);

      case BACKGROUND_NONE:
      default:
        // Ensure all the maxima are found
        return 0;
    }
  }

  private static int round(double d) {
    return (int) Math.round(d);
  }

  /**
   * Get the threshold that limits the maxima region growing.
   *
   * @param searchMethod The thresholding method
   * @param searchParameter The method thresholding parameter
   * @param stats The image statistics
   * @param v0 The current maxima value
   * @return The threshold
   */
  private static int getTolerance(int searchMethod, double searchParameter, double[] stats,
      int v0) {
    switch (searchMethod) {
      case SEARCH_ABOVE_BACKGROUND:
        return round(stats[STATS_BACKGROUND]);

      case SEARCH_FRACTION_OF_PEAK_MINUS_BACKGROUND:
        if (searchParameter < 0) {
          searchParameter = 0;
        }
        return round(stats[STATS_BACKGROUND] + searchParameter * (v0 - stats[STATS_BACKGROUND]));

      case SEARCH_HALF_PEAK_VALUE:
        return round(stats[STATS_BACKGROUND] + 0.5 * (v0 - stats[STATS_BACKGROUND]));
    }
    return 0;
  }

  /**
   * Get the minimum height for this peak above the highest saddle point.
   *
   * @param peakMethod The method
   * @param peakParameter The method parameter
   * @param stats The image statistics
   * @param v0 The current maxima value
   * @return The minimum height
   */
  private static int getPeakHeight(int peakMethod, double peakParameter, double[] stats, int v0) {
    int height = 1;
    if (peakParameter < 0) {
      peakParameter = 0;
    }
    switch (peakMethod) {
      case PEAK_ABSOLUTE:
        height = round(peakParameter);
        break;
      case PEAK_RELATIVE:
        height = round(v0 * peakParameter);
        break;
      case PEAK_RELATIVE_ABOVE_BACKGROUND:
        height = round((v0 - stats[STATS_BACKGROUND]) * peakParameter);
        break;
    }
    if (height < 1) {
      height = 1; // It should be a peak
    }
    return height;
  }

  /**
   * Find all local maxima (irrespective whether they finally qualify as maxima or not).
   *
   * @param image The image to be analyzed
   * @param maxima the maxima
   * @param types A byte image, same size as ip, where the maximum points are marked as MAXIMUM
   * @param globalMin The image global minimum
   * @param threshold The threshold below which no pixels are processed.
   * @return Maxima sorted by value.
   */
  private Coordinate[] getSortedMaxpoints(int[] image, int[] maxima, byte[] types, int globalMin,
      int threshold) {
    final ArrayList<Coordinate> maxpoints = new ArrayList<>(500);
    int[] pointList = null; // working list for expanding local plateaus

    int id = 0;
    final int[] xyz = new int[3];

    // int pCount = 0;

    for (int i = image.length; i-- > 0;) {
      if ((types[i] & (EXCLUDED | MAX_AREA | PLATEAU)) != 0) {
        continue;
      }
      final int v = image[i];
      if (v < threshold) {
        continue;
      }
      if (v == globalMin) {
        continue;
      }

      getXyz(i, xyz);

      final int x = xyz[0];
      final int y = xyz[1];
      final int z = xyz[2];

      /*
       * check whether we have a local maximum.
       */
      final boolean isInnerXy = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);
      final boolean isInnerXyz = (zlimit == 0) ? isInnerXy : isInnerXy && (z != 0 && z != zlimit);
      boolean isMax = true, equalNeighbour = false;

      // It is more likely that the z stack will be out-of-bounds.
      // Adopt the xy limit lookup and process z lookup separately

      for (int d = dStart; d-- > 0;) {
        if (isInnerXyz || (isInnerXy && isWithinZ(z, d)) || isWithinXyz(x, y, z, d)) {
          final int vNeighbor = image[i + offset[d]];
          if (vNeighbor > v) {
            isMax = false;
            break;
          } else if (vNeighbor == v) {
            // Neighbour is equal, this is a potential plateau maximum
            equalNeighbour = true;
          }
        }
      }

      if (isMax) {
        id++;
        final int searchCapacity = 65536;
        if (id >= searchCapacity) {
          IJ.log("The number of potential maxima exceeds the search capacity: " + searchCapacity
              + ". Try using a denoising/smoothing filter or increase the capacity.");
          return null;
        }

        if (equalNeighbour) {
          // Initialise the working list
          if (pointList == null) {
            // Create an array to hold the rest of the points (worst case scenario for the maxima
            // expansion)
            pointList = new int[i + 1];
          }

          // Search the local area marking all equal neighbour points as maximum
          if (!expandMaximum(image, maxima, types, globalMin, threshold, i, v, id, maxpoints,
              pointList)) {
            // Not a true maximum, ignore this
            id--;
          }
        } else {
          types[i] |= MAXIMUM | MAX_AREA;
          maxima[i] = id;
          maxpoints.add(new Coordinate(x, y, z, id, v));
        }
      }
    }

    // if (pCount > 0)
    // logger.fine(FunctionUtils.getSupplier("Plateau count = %d\n", pCount));

    if (ImageJUtils.isInterrupted()) {
      return null;
    }

    Collections.sort(maxpoints);

    // Build a map between the original id and the new id following the sort
    final int[] idMap = new int[maxpoints.size() + 1];

    // Label the points
    for (int i = 0; i < maxpoints.size(); i++) {
      final int newId = (i + 1);
      final int oldId = maxpoints.get(i).id;
      idMap[oldId] = newId;
      maxpoints.get(i).id = newId;
    }

    reassignMaxima(maxima, idMap);

    final Coordinate[] results = maxpoints.toArray(new Coordinate[0]);
    return results;
  }

  /**
   * Searches from the specified point to find all coordinates of the same value and determines the
   * centre of the plateau maximum.
   *
   * @param image the image
   * @param maxima the maxima
   * @param types the types
   * @param globalMin the global min
   * @param threshold the threshold
   * @param index0 the index 0
   * @param v0 the v 0
   * @param id the id
   * @param maxpoints the max points
   * @param pointList the list
   * @return True if this is a true plateau, false if the plateau reaches a higher point
   */
  private boolean expandMaximum(int[] image, int[] maxima, byte[] types, int globalMin,
      int threshold, int index0, int v0, int id, ArrayList<Coordinate> maxpoints, int[] pointList) {
    types[index0] |= LISTED | PLATEAU; // mark first point as listed
    int listI = 0; // index of current search element in the list
    int listLen = 1; // number of elements in the list

    // we create a list of connected points and start the list at the current maximum
    pointList[listI] = index0;

    // Calculate the center of plateau
    boolean isPlateau = true;
    final int[] xyz = new int[3];

    do {
      final int index1 = pointList[listI];
      getXyz(index1, xyz);
      final int x1 = xyz[0];
      final int y1 = xyz[1];
      final int z1 = xyz[2];

      // It is more likely that the z stack will be out-of-bounds.
      // Adopt the xy limit lookup and process z lookup separately

      final boolean isInnerXy = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);
      final boolean isInnerXyz = (zlimit == 0) ? isInnerXy : isInnerXy && (z1 != 0 && z1 != zlimit);

      for (int d = dStart; d-- > 0;) {
        if (isInnerXyz || (isInnerXy && isWithinZ(z1, d)) || isWithinXyz(x1, y1, z1, d)) {
          final int index2 = index1 + offset[d];
          if ((types[index2] & IGNORE) != 0) {
            // This has been done already, ignore this point
            continue;
          }

          final int v2 = image[index2];

          if (v2 > v0) {
            isPlateau = false;
          } else if (v2 == v0) {
            // Add this to the search
            pointList[listLen++] = index2;
            types[index2] |= LISTED | PLATEAU;
          }
        }
      }

      listI++;

    } while (listI < listLen && isPlateau);

    // IJ.log("Potential plateau "+ x0 + ","+y0+","+z0+" : "+listLen);

    // Find the centre
    double xEqual = 0;
    double yEqual = 0;
    double zEqual = 0;
    int nEqual = 0;
    if (isPlateau) {
      for (int i = listLen; i-- > 0;) {
        getXyz(pointList[i], xyz);
        xEqual += xyz[0];
        yEqual += xyz[1];
        zEqual += xyz[2];
        nEqual++;
      }
    }
    xEqual /= nEqual;
    yEqual /= nEqual;
    zEqual /= nEqual;

    double dMax = Double.MAX_VALUE;
    int iMax = 0;

    // Calculate the maxima origin as the closest pixel to the centre-of-mass
    for (int i = listLen; i-- > 0;) {
      final int index = pointList[i];
      types[index] &= ~LISTED; // reset attributes no longer needed

      if (isPlateau) {
        getXyz(index, xyz);

        final int x = xyz[0];
        final int y = xyz[1];
        final int z = xyz[2];

        final double d =
            (xEqual - x) * (xEqual - x) + (yEqual - y) * (yEqual - y) + (zEqual - z) * (zEqual - z);

        if (d < dMax) {
          dMax = d;
          iMax = i;
        }

        types[index] |= MAX_AREA;
        maxima[index] = id;
      }
    }

    // Assign the maximum
    if (isPlateau) {
      final int index = pointList[iMax];
      types[index] |= MAXIMUM;
      maxpoints.add(new Coordinate(index, id, v0));
    }

    return isPlateau;
  }

  /**
   * Initialises the maxima image using the maxima Id for each point.
   *
   * @param maxima the maxima
   * @param maxpoints the max points
   * @param resultsArray the results array
   */
  private void assignMaxima(int[] maxima, Coordinate[] maxpoints, ArrayList<int[]> resultsArray) {
    final int[] xyz = new int[3];

    for (final Coordinate maximum : maxpoints) {
      getXyz(maximum.index, xyz);

      maxima[maximum.index] = maximum.id;

      final int[] result = new int[RESULT_LENGTH];
      result[RESULT_X] = xyz[0];
      result[RESULT_Y] = xyz[1];
      result[RESULT_Z] = xyz[2];
      result[RESULT_PEAK_ID] = maximum.id;
      result[RESULT_MAX_VALUE] = maximum.value;
      result[RESULT_INTENSITY] = maximum.value;
      result[RESULT_COUNT] = 1;

      resultsArray.add(result);
    }
  }

  /**
   * Assigns points to their maxima using the steepest uphill gradient. Processes points in order of
   * height, progressively building peaks in a top-down fashion.
   */
  private void assignpointsToMaxima(int[] image, int[] histogram, byte[] types, double[] stats,
      int[] maxima) {
    final int background = round(stats[STATS_BACKGROUND]);
    final int maxValue = round(stats[STATS_MAX]);

    // Create an array with the coordinates of all points between the threshold value and the max-1
    // value
    int arraySize = 0;
    for (int v = background; v < maxValue; v++) {
      arraySize += histogram[v];
    }

    if (arraySize == 0) {
      return;
    }

    final int[] coordinates = new int[arraySize]; // from pixel coordinates, low bits x, high bits y
    int highestValue = 0;
    int offset = 0;
    final int[] levelStart = new int[maxValue + 1];
    for (int v = background; v < maxValue; v++) {
      levelStart[v] = offset;
      offset += histogram[v];
      if (histogram[v] > 0) {
        highestValue = v;
      }
    }
    final int[] levelOffset = new int[highestValue + 1];
    for (int i = image.length; i-- > 0;) {
      if ((types[i] & EXCLUDED) != 0) {
        continue;
      }

      final int v = image[i];
      if (v >= background && v < maxValue) {
        offset = levelStart[v] + levelOffset[v];
        coordinates[offset] = i;
        levelOffset[v]++;
      }
    }

    // Process down through the levels
    int processedLevel = 0; // Counter incremented when work is done
    // int levels = 0;
    for (int level = highestValue; level >= background; level--) {
      int remaining = histogram[level];

      if (remaining == 0) {
        continue;
      }

      // Use the idle counter to ensure that we exit the loop if no pixels have been processed for
      // two cycles
      while (remaining > 0) {
        processedLevel++;
        final int n = processLevel(image, types, maxima, levelStart[level], remaining, coordinates,
            background);
        remaining -= n; // number of points processed

        // If nothing was done then stop
        if (n == 0) {
          break;
        }
      }

      if ((processedLevel % 64 == 0) && ImageJUtils.isInterrupted()) {
        return;
      }

      if (remaining > 0 && level > background) {
        // any pixels that we have not reached?
        // It could happen if there is a large area of flat pixels => no local maxima.
        // Add to the next level.
        // IJ.log("Unprocessed " + remaining + " @level = " + level);

        int nextLevel = level; // find the next level to process
        do {
          nextLevel--;
        } while (nextLevel > 1 && histogram[nextLevel] == 0);

        // Add all unprocessed pixels of this level to the tasklist of the next level.
        // This could make it slow for some images, however.
        if (nextLevel > 0) {
          int newNextLevelEnd = levelStart[nextLevel] + histogram[nextLevel];
          for (int i = 0, p = levelStart[level]; i < remaining; i++, p++) {
            final int index = coordinates[p];
            coordinates[newNextLevelEnd++] = index;
          }
          // tasklist for the next level to process becomes longer by this:
          histogram[nextLevel] = newNextLevelEnd - levelStart[nextLevel];
        }
      }
    }

    // int nP = 0;
    // for (byte b : types)
    // if ((b & PLATEAU) == PLATEAU)
    // nP++;

    // IJ.log(String.format("Processed %d levels [%d steps], %d plateau points", levels,
    // processedLevel, nP));
  }

  /**
   * Processes points in order of height, progressively building peaks in a top-down fashion.
   *
   * @param image the input image
   * @param types The image pixel types
   * @param maxima The image maxima
   * @param levelStart offsets of the level in pixelPointers[]
   * @param levelNPoints number of points in the current level
   * @param coordinates list of xyz coordinates (should be offset by levelStart)
   * @param background The background intensity
   * @return number of pixels that have been changed
   */
  private int processLevel(int[] image, byte[] types, int[] maxima, int levelStart,
      int levelNPoints, int[] coordinates, int background) {
    // int[] pointList = new int[0]; // working list for expanding local plateaus
    int nChanged = 0;
    int nUnchanged = 0;
    final int[] xyz = new int[3];

    for (int i = 0, p = levelStart; i < levelNPoints; i++, p++) {
      final int index = coordinates[p];

      if ((types[index] & (EXCLUDED | MAX_AREA)) != 0) {
        // This point can be ignored
        nChanged++;
        continue;
      }

      getXyz(index, xyz);

      // Extract the point coordinate
      final int x = xyz[0];
      final int y = xyz[1];
      final int z = xyz[2];

      final int v = image[index];

      // It is more likely that the z stack will be out-of-bounds.
      // Adopt the xy limit lookup and process z lookup separately
      final boolean isInnerXy = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);
      final boolean isInnerXyz = (zlimit == 0) ? isInnerXy : isInnerXy && (z != 0 && z != zlimit);

      // Check for the highest neighbour

      int dMax = -1;
      int vMax = v;
      for (int d = dStart; d-- > 0;) {
        if (isInnerXyz || (isInnerXy && isWithinZ(z, d)) || isWithinXyz(x, y, z, d)) {
          final int index2 = index + offset[d];
          final int vNeighbor = image[index2];
          if (vMax < vNeighbor) // Higher neighbour
          {
            vMax = vNeighbor;
            dMax = d;
          } else if (vMax == vNeighbor) {
            // Check if the neighbour is higher than this point (i.e. an equal higher neighbour has
            // been found)
            if (v != vNeighbor) {
              // Favour flat edges over diagonals in the case of equal neighbours
              if (flatEdge[d]) {
                dMax = d;
              }
            }
            // The neighbour is the same height, check if it is a maxima
            else if ((types[index2] & MAX_AREA) != 0) {
              if (dMax < 0) {
                dMax = d;
              } else if (flatEdge[d]) {
                dMax = d;
              }
            }
          }
        }
      }

      if (dMax < 0) {
        // This could happen if all neighbours are the same height and none are maxima.
        // Since plateau maxima should be handled in the initial maximum finding stage, any equal
        // neighbours
        // should be processed eventually.
        coordinates[levelStart + (nUnchanged++)] = index;
        continue;
      }

      final int index2 = index + offset[dMax];
      types[index] |= MAX_AREA;
      maxima[index] = maxima[index2];
      nChanged++;
    } // for pixel i

    // if (nUnchanged > 0)
    // logger.fine(FunctionUtils.getSupplier("nUnchanged = %d\n", nUnchanged));

    return nChanged;
  }// processLevel

  /**
   * Loop over all points that have been assigned to a peak area and clear any pixels below the peak
   * growth threshold.
   *
   * @param image the image
   * @param types the types
   * @param searchMethod the search method
   * @param searchParameter the search parameter
   * @param stats the stats
   * @param resultsArray the results array
   * @param maxima the maxima
   */
  private static void pruneMaxima(int[] image, byte[] types, int searchMethod,
      double searchParameter, double[] stats, ArrayList<int[]> resultsArray, int[] maxima) {
    // Build an array containing the threshold for each peak.
    // Note that maxima are numbered from 1
    final int nMaxima = resultsArray.size();
    final int[] peakThreshold = new int[nMaxima + 1];
    for (int i = 1; i < peakThreshold.length; i++) {
      final int v0 = resultsArray.get(i - 1)[RESULT_MAX_VALUE];
      peakThreshold[i] = getTolerance(searchMethod, searchParameter, stats, v0);
    }

    for (int i = image.length; i-- > 0;) {
      if (maxima[i] > 0) {
        if (image[i] < peakThreshold[maxima[i]]) {
          // Unset this pixel as part of the peak
          maxima[i] = 0;
          types[i] &= ~MAX_AREA;
        }
      }
    }
  }

  /**
   * Loop over the image and sum the intensity and size of each peak area, storing this into the
   * results array.
   *
   * @param image the image
   * @param maxima the maxima
   * @param resultsArray the results array
   */
  private static void calculateInitialResults(int[] image, int[] maxima,
      ArrayList<int[]> resultsArray) {
    final int nMaxima = resultsArray.size();

    // Maxima are numbered from 1
    final int[] count = new int[nMaxima + 1];
    final int[] intensity = new int[nMaxima + 1];

    for (int i = maxima.length; i-- > 0;) {
      if (maxima[i] > 0) {
        count[maxima[i]]++;
        intensity[maxima[i]] += image[i];
      }
    }

    for (final int[] result : resultsArray) {
      result[RESULT_COUNT] = count[result[RESULT_PEAK_ID]];
      result[RESULT_INTENSITY] = intensity[result[RESULT_PEAK_ID]];
      result[RESULT_AVERAGE_INTENSITY] = result[RESULT_INTENSITY] / result[RESULT_COUNT];
    }
  }

  /**
   * Loop over the image and sum the intensity of each peak area using the original image, storing
   * this into the results array.
   */
  private static void calculateNativeResults(int[] image, int[] maxima,
      ArrayList<int[]> resultsArray, int originalNumberOfPeaks) {
    // Maxima are numbered from 1
    final int[] intensity = new int[originalNumberOfPeaks + 1];
    final int[] max = new int[originalNumberOfPeaks + 1];

    for (int i = maxima.length; i-- > 0;) {
      if (maxima[i] > 0) {
        intensity[maxima[i]] += image[i];
        if (max[maxima[i]] < image[i]) {
          max[maxima[i]] = image[i];
        }
      }
    }

    for (final int[] result : resultsArray) {
      if (intensity[result[RESULT_PEAK_ID]] > 0) {
        result[RESULT_INTENSITY] = intensity[result[RESULT_PEAK_ID]];
        result[RESULT_MAX_VALUE] = max[result[RESULT_PEAK_ID]];
      }
    }
  }

  /**
   * Calculate the peaks centre and maximum value. This could be done in many ways: - Max value -
   * Centre-of-mass (within a bounding box of max value defined by the centreParameter) - Gaussian
   * fit (Using a 2D projection defined by the centreParameter: (1) Maximum value; (other) Average
   * value)
   *
   * @param image the image
   * @param searchImage the search image
   * @param maxima the maxima
   * @param types the types
   * @param resultsArray the results array
   * @param originalNumberOfPeaks the original number of peaks
   * @param centreMethod the centre method
   * @param centreParameter the centre parameter
   */
  private void locateMaxima(int[] image, int[] searchImage, int[] maxima, byte[] types,
      ArrayList<int[]> resultsArray, int originalNumberOfPeaks, int centreMethod,
      double centreParameter) {
    if (centreMethod == CENTRE_MAX_VALUE_SEARCH) {
      return; // This is the current value so just return
    }

    // Swap to the search image for processing if necessary
    switch (centreMethod) {
      case CENTRE_GAUSSIAN_SEARCH:
      case CENTRE_OF_MASS_SEARCH:
      case CENTRE_MAX_VALUE_SEARCH:
        image = searchImage;
    }

    // Working list of peak coordinates
    int[] pointList = new int[0];

    // For each peak, compute the centre
    for (final int[] result : resultsArray) {
      // Ensure list is large enough
      if (pointList.length < result[RESULT_COUNT]) {
        pointList = new int[result[RESULT_COUNT]];
      }

      // Find the peak coords above the saddle
      final int maximaId = result[RESULT_PEAK_ID];
      final int index = getIndex(result[RESULT_X], result[RESULT_Y], result[RESULT_Z]);
      final int listLen = findMaximaCoords(image, maxima, types, index, maximaId,
          result[RESULT_HIGHEST_SADDLE_VALUE], pointList);
      // IJ.log("maxima size > saddle = " + listLen);

      // Find the boundaries of the coordinates
      final int[] min_xyz = new int[] {maxx, maxy, maxz};
      final int[] max_xyz = new int[] {0, 0, 0};
      final int[] xyz = new int[3];
      for (int listI = listLen; listI-- > 0;) {
        final int index1 = pointList[listI];
        getXyz(index1, xyz);
        for (int i = 3; i-- > 0;) {
          if (min_xyz[i] > xyz[i]) {
            min_xyz[i] = xyz[i];
          }
          if (max_xyz[i] < xyz[i]) {
            max_xyz[i] = xyz[i];
          }
        }
      }
      // IJ.log("Boundaries " + maximaId + " : " + min_xyz[0] + "," + min_xyz[1] + "," + min_xyz[2]
      // + " => " +
      // max_xyz[0] + "," + max_xyz[1] + "," + max_xyz[2]);

      // Extract sub image
      final int[] dimensions = new int[3];
      for (int i = 3; i-- > 0;) {
        dimensions[i] = max_xyz[i] - min_xyz[i] + 1;
      }

      final int[] subImage = extractSubImage(image, maxima, min_xyz, dimensions, maximaId,
          result[RESULT_HIGHEST_SADDLE_VALUE]);

      int[] centre = null;
      switch (centreMethod) {
        case CENTRE_GAUSSIAN_SEARCH:
        case CENTRE_GAUSSIAN_ORIGINAL:
          centre = findCentreGaussianFit(subImage, dimensions, round(centreParameter));
          // if (centre == null)
          // {
          // if (IJ.debugMode)
          // {
          // IJ.log("No Gaussian fit");
          // }
          // }
          break;

        case CENTRE_OF_MASS_SEARCH:
        case CENTRE_OF_MASS_ORIGINAL:
          centre = findCentreOfMass(subImage, dimensions, round(centreParameter));
          break;

        case CENTRE_MAX_VALUE_ORIGINAL:
        default:
          centre = findCentreMaxValue(subImage, dimensions);
      }

      if (centre != null) {
        if (IJ.debugMode) {
          final int[] shift = new int[3];
          double d = 0;
          for (int i = 3; i-- > 0;) {
            shift[i] = result[i] - (centre[i] + min_xyz[i]);
            d += shift[i] * shift[i];
          }
          IJ.log("Moved centre: " + shift[0] + " , " + shift[1] + " , " + shift[2] + " = "
              + IJ.d2s(Math.sqrt(d), 2));
        }

        // RESULT_[XYZ] are 0, 1, 2
        for (int i = 3; i-- > 0;) {
          result[i] = centre[i] + min_xyz[i];
        }
      }
    }
  }

  /**
   * Search for all connected points in the maxima above the saddle value.
   *
   * @return The number of points
   */
  private int findMaximaCoords(int[] image, int[] maxima, byte[] types, int index0, int maximaId,
      int saddleValue, int[] pointList) {
    types[index0] |= LISTED; // mark first point as listed
    int listI = 0; // index of current search element in the list
    int listLen = 1; // number of elements in the list

    // we create a list of connected points and start the list at the current maximum
    pointList[listI] = index0;

    final int[] xyz = new int[3];

    do {
      final int index1 = pointList[listI];
      getXyz(index1, xyz);
      final int x1 = xyz[0];
      final int y1 = xyz[1];
      final int z1 = xyz[2];

      final boolean isInnerXy = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);
      final boolean isInnerXyz = (zlimit == 0) ? isInnerXy : isInnerXy && (z1 != 0 && z1 != zlimit);

      for (int d = dStart; d-- > 0;) {
        if (isInnerXyz || (isInnerXy && isWithinZ(z1, d)) || isWithinXyz(x1, y1, z1, d)) {
          final int index2 = index1 + offset[d];
          if ((types[index2] & IGNORE) != 0 || maxima[index2] != maximaId) {
            // This has been done already, ignore this point
            continue;
          }

          final int v2 = image[index2];

          if (v2 >= saddleValue) {
            // Add this to the search
            pointList[listLen++] = index2;
            types[index2] |= LISTED;
          }
        }
      }

      listI++;

    } while (listI < listLen);

    for (int i = listLen; i-- > 0;) {
      final int index = pointList[i];
      types[index] &= ~LISTED; // reset attributes no longer needed
    }

    return listLen;
  }

  /**
   * Extract a sub-image from the given input image using the specified boundaries. The minValue is
   * subtracted from all pixels. All pixels below the minValue are ignored (set to zero).
   *
   * @param image the image
   * @param maxima the maxima
   * @param min_xyz the min xyz
   * @param dimensions the dimensions
   * @param maximaId the maxima id
   * @param minValue the min value
   * @return the int[]
   */
  private int[] extractSubImage(int[] image, int[] maxima, int[] min_xyz, int[] dimensions,
      int maximaId, int minValue) {
    final int[] subImage = new int[dimensions[0] * dimensions[1] * dimensions[2]];

    int offset = 0;
    for (int z = 0; z < dimensions[2]; z++) {
      for (int y = 0; y < dimensions[1]; y++) {
        int index = getIndex(min_xyz[0], y + min_xyz[1], z + min_xyz[2]);
        for (int x = 0; x < dimensions[0]; x++, index++, offset++) {
          if (maxima[index] == maximaId && image[index] > minValue) {
            subImage[offset] = image[index] - minValue;
          }
        }
      }
    }

    // DEBUGGING
    // ImageProcessor ip = new ShortProcessor(dimensions[0], dimensions[1]);
    // for (int i = subImage.length; i-- > 0;)
    // ip.set(i, subImage[i]);
    // new ImagePlus(null, ip).show();

    return subImage;
  }

  /**
   * Finds the centre of the image using the maximum pixel value. If many pixels have the same value
   * the closest pixel to the geometric mean of the coordinates is returned.
   *
   * @param image the image
   * @param dimensions the dimensions
   * @return the centre
   */
  private static int[] findCentreMaxValue(int[] image, int[] dimensions) {
    // Find the maximum value in the image
    int maxValue = 0;
    int count = 0;
    int index = 0;
    for (int i = image.length; i-- > 0;) {
      if (maxValue < image[i]) {
        maxValue = image[i];
        index = i;
        count = 1;
      } else if (maxValue == image[i]) {
        count++;
      }
    }

    // Used to map index back to XYZ
    final int blockSize = dimensions[0] * dimensions[1];

    if (count == 1) {
      // There is only one maximum pixel
      final int[] xyz = new int[3];
      xyz[2] = index / (blockSize);
      final int mod = index % (blockSize);
      xyz[1] = mod / dimensions[0];
      xyz[0] = mod % dimensions[0];
      return xyz;
    }

    // Find geometric mean
    final double[] centre = new double[3];
    for (int i = image.length; i-- > 0;) {
      if (maxValue == image[i]) {
        final int[] xyz = new int[3];
        xyz[2] = i / (blockSize);
        final int mod = i % (blockSize);
        xyz[1] = mod / dimensions[0];
        xyz[0] = mod % dimensions[0];
        for (int j = 3; j-- > 0;) {
          centre[j] += xyz[j];
        }
      }
    }
    for (int j = 3; j-- > 0;) {
      centre[j] /= count;
    }

    // Find nearest point
    double dMin = Double.MAX_VALUE;
    int[] closest = new int[] {round(centre[0]), round(centre[1]), round(centre[2])};
    for (int i = image.length; i-- > 0;) {
      if (maxValue == image[i]) {
        final int[] xyz = new int[3];
        xyz[2] = i / (blockSize);
        final int mod = i % (blockSize);
        xyz[1] = mod / dimensions[0];
        xyz[0] = mod % dimensions[0];
        final double d = Math.pow(xyz[0] - centre[0], 2) + Math.pow(xyz[1] - centre[1], 2)
            + Math.pow(xyz[2] - centre[2], 2);
        if (dMin > d) {
          dMin = d;
          closest = xyz;
        }
      }
    }

    return closest;
  }

  /**
   * Finds the centre of the image using the centre of mass within the given range of the maximum
   * pixel value.
   */
  private static int[] findCentreOfMass(int[] subImage, int[] dimensions, int range) {
    final int[] centre = findCentreMaxValue(subImage, dimensions);
    double[] com = new double[] {centre[0], centre[1], centre[2]};

    // Iterate until convergence
    double distance;
    int iter = 0;
    do {
      final double[] newCom = findCentreOfMass(subImage, dimensions, range, com);
      distance = Math.pow(newCom[0] - com[0], 2) + Math.pow(newCom[1] - com[1], 2)
          + Math.pow(newCom[2] - com[2], 2);
      com = newCom;
      iter++;
    } while (distance > 1 && iter < 10);

    return convertCentre(com);
  }

  /**
   * Finds the centre of the image using the centre of mass within the given range of the specified
   * centre-of-mass.
   *
   * @param subImage the sub image
   * @param dimensions the dimensions
   * @param range the range
   * @param com the com
   * @return the centre
   */
  private static double[] findCentreOfMass(int[] subImage, int[] dimensions, int range,
      double[] com) {
    final int[] centre = convertCentre(com);

    final int[] min = new int[3];
    final int[] max = new int[3];
    if (range < 1) {
      range = 1;
    }
    for (int i = 3; i-- > 0;) {
      min[i] = centre[i] - range;
      max[i] = centre[i] + range;
      if (min[i] < 0) {
        min[i] = 0;
      }
      if (max[i] >= dimensions[i] - 1) {
        max[i] = dimensions[i] - 1;
      }
    }

    final int blockSize = dimensions[0] * dimensions[1];

    final double[] newCom = new double[3];
    long sum = 0;
    for (int z = min[2]; z <= max[2]; z++) {
      for (int y = min[1]; y <= max[1]; y++) {
        int index = blockSize * z + dimensions[0] * y + min[0];
        for (int x = min[0]; x <= max[0]; x++, index++) {
          final int value = subImage[index];
          if (value > 0) {
            sum += value;
            newCom[0] += x * value;
            newCom[1] += y * value;
            newCom[2] += z * value;
          }
        }
      }
    }

    for (int i = 3; i-- > 0;) {
      newCom[i] /= sum;
    }

    return newCom;
  }

  /**
   * Finds the centre of the image using a 2D Gaussian fit to projection along the Z-axis.
   *
   * @param subImage the sub image
   * @param dimensions the dimensions
   * @param projectionMethod (0) Average value; (1) Maximum value
   * @return the int[]
   */
  private static int[] findCentreGaussianFit(int[] subImage, int[] dimensions,
      int projectionMethod) {
    if (isGaussianFitEnabled < 1) {
      return null;
    }

    final int blockSize = dimensions[0] * dimensions[1];
    final float[] projection = new float[blockSize];

    if (projectionMethod == 1) {
      // Maximum value
      for (int z = dimensions[2]; z-- > 0;) {
        int index = blockSize * z;
        for (int i = 0; i < blockSize; i++, index++) {
          if (projection[i] < subImage[index]) {
            projection[i] = subImage[index];
          }
        }
      }
    } else {
      // Average value
      for (int z = dimensions[2]; z-- > 0;) {
        int index = blockSize * z;
        for (int i = 0; i < blockSize; i++, index++) {
          projection[i] += subImage[index];
        }
      }
      for (int i = blockSize; i-- > 0;) {
        projection[i] /= dimensions[2];
      }
    }

    final GaussianFit_PlugIn gf = new GaussianFit_PlugIn();
    final double[] fitParams = gf.fit(projection, dimensions[0], dimensions[1]);

    int[] centre = null;
    if (fitParams != null) {
      // Find the centre of mass along the z-axis
      centre = convertCentre(new double[] {fitParams[2], fitParams[3]});

      // Use the centre of mass along the projection axis
      double com = 0;
      long sum = 0;
      for (int z = dimensions[2]; z-- > 0;) {
        final int index = blockSize * z;
        final int value = subImage[index];
        if (value > 0) {
          com += z * value;
          sum += value;
        }
      }
      centre[2] = round(com / sum);
      // Avoid clipping
      if (centre[2] >= dimensions[2]) {
        centre[2] = dimensions[2] - 1;
      }
    }

    return centre;
  }

  /**
   * Convert the centre from double to int. Handles input arrays of length 2 or 3.
   */
  private static int[] convertCentre(double[] centre) {
    final int[] newCentre = new int[3];
    for (int i = centre.length; i-- > 0;) {
      newCentre[i] = round(centre[i]);
    }
    return newCentre;
  }

  /**
   * Loop over the results array and calculate the average intensity and the intensity above the
   * background.
   *
   * @param resultsArray the results array
   * @param background the background
   */
  private static void calculateFinalResults(ArrayList<int[]> resultsArray, int background) {
    for (final int[] result : resultsArray) {
      result[RESULT_INTENSITY_MINUS_BACKGROUND] =
          result[RESULT_INTENSITY] - background * result[RESULT_COUNT];
      result[RESULT_AVERAGE_INTENSITY] = result[RESULT_INTENSITY] / result[RESULT_COUNT];
      result[RESULT_AVERAGE_INTENSITY_MINUS_BACKGROUND] =
          result[RESULT_INTENSITY_MINUS_BACKGROUND] / result[RESULT_COUNT];
    }
  }

  /**
   * Finds the highest saddle point for each peak.
   *
   * @param image the image
   * @param types the types
   * @param resultsArray the results array
   * @param maxima the maxima
   * @param saddlePoints Contains an entry for each peak indexed from 1. The entry is a linked list
   *        of saddle points. Each saddle point is an array containing the neighbouring peak ID and
   *        the saddle value.
   */
  private void findSaddlePoints(int[] image, byte[] types, ArrayList<int[]> resultsArray,
      int[] maxima, ArrayList<LinkedList<int[]>> saddlePoints) {
    // Initialise the saddle points
    final int nMaxima = resultsArray.size();
    for (int i = 0; i < nMaxima + 1; i++) {
      saddlePoints.add(new LinkedList<int[]>());
    }

    final int maxPeakSize = getMaxPeakSize(resultsArray);
    final int[][] pointList = new int[maxPeakSize][2]; // here we enter points starting from a
                                                       // maximum
    // (index,value)
    final int[] xyz = new int[3];

    /* Process all the maxima */
    for (final int[] result : resultsArray) {
      final int x0 = result[RESULT_X];
      final int y0 = result[RESULT_Y];
      final int z0 = result[RESULT_Z];
      final int id = result[RESULT_PEAK_ID];
      final int index0 = getIndex(x0, y0, z0);

      final int v0 = result[RESULT_MAX_VALUE];

      // List of saddle highest values with every other peak
      final int[] highestSaddleValue = new int[nMaxima + 1];

      types[index0] |= LISTED; // mark first point as listed
      int listI = 0; // index of current search element in the list
      int listLen = 1; // number of elements in the list

      // we create a list of connected points and start the list at the current maximum
      pointList[0][0] = index0;
      pointList[0][1] = v0;

      do {
        final int index1 = pointList[listI][0];
        final int v1 = pointList[listI][1];

        getXyz(index1, xyz);
        final int x1 = xyz[0];
        final int y1 = xyz[1];
        final int z1 = xyz[2];

        // It is more likely that the z stack will be out-of-bounds.
        // Adopt the xy limit lookup and process z lookup separately

        final boolean isInnerXy = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);
        final boolean isInnerXyz =
            (zlimit == 0) ? isInnerXy : isInnerXy && (z1 != 0 && z1 != zlimit);

        // Check for the highest neighbour
        for (int d = dStart; d-- > 0;) {
          if (isInnerXyz || (isInnerXy && isWithinZ(z1, d)) || isWithinXyz(x1, y1, z1, d)) {
            // Get the coords
            final int index2 = index1 + offset[d];

            if ((types[index2] & IGNORE) != 0) {
              // This has been done already, ignore this point
              continue;
            }

            final int id2 = maxima[index2];

            if (id2 == id) {
              // Add this to the search
              pointList[listLen][0] = index2;
              pointList[listLen][1] = image[index2];
              listLen++;
              types[index2] |= LISTED;
            } else if (id2 != 0) {
              // This is another peak, see if it a saddle highpoint
              final int v2 = image[index2];

              // Take the lower of the two points as the saddle
              final int minV;
              if (v1 < v2) {
                types[index1] |= SADDLE;
                minV = v1;
              } else {
                types[index2] |= SADDLE;
                minV = v2;
              }

              if (highestSaddleValue[id2] < minV) {
                highestSaddleValue[id2] = minV;
              }
            }
          }
        }

        listI++;

      } while (listI < listLen);

      for (int i = listLen; i-- > 0;) {
        final int index = pointList[i][0];
        types[index] &= ~LISTED; // reset attributes no longer needed
      }

      // Find the highest saddle
      int highestNeighbourPeakId = 0;
      int highestNeighbourValue = 0;
      final LinkedList<int[]> saddles = saddlePoints.get(id);
      for (int id2 = 1; id2 <= nMaxima; id2++) {
        if (highestSaddleValue[id2] > 0) {
          saddles.add(new int[] {id2, highestSaddleValue[id2]});
          // IJ.log("Peak saddle " + id + " -> " + id2 + " @ " + highestSaddleValue[id2]);
          if (highestNeighbourValue < highestSaddleValue[id2]) {
            highestNeighbourValue = highestSaddleValue[id2];
            highestNeighbourPeakId = id2;
          }
        }
      }

      // Set the saddle point
      if (highestNeighbourPeakId > 0) {
        result[RESULT_SADDLE_NEIGHBOUR_ID] = highestNeighbourPeakId;
        result[RESULT_HIGHEST_SADDLE_VALUE] = highestNeighbourValue;
      }
    } // for all maxima
  }

  private static int getMaxPeakSize(ArrayList<int[]> resultsArray) {
    int maxPeakSize = 0;
    for (final int[] result : resultsArray) {
      if (maxPeakSize < result[RESULT_COUNT]) {
        maxPeakSize = result[RESULT_COUNT];
      }
    }
    return maxPeakSize;
  }

  /**
   * Find the size and intensity of peaks above their saddle heights.
   */
  private static void analysePeaks(ArrayList<int[]> resultsArray, int[] image, int[] maxima) {
    // Create an array of the size/intensity of each peak above the highest saddle
    final int[] peakIntensity = new int[resultsArray.size() + 1];
    final int[] peakSize = new int[resultsArray.size() + 1];

    // Store all the saddle heights
    final int[] saddleHeight = new int[resultsArray.size() + 1];
    for (final int[] result : resultsArray) {
      saddleHeight[result[RESULT_PEAK_ID]] = result[RESULT_HIGHEST_SADDLE_VALUE];
    }

    for (int i = maxima.length; i-- > 0;) {
      if (maxima[i] > 0) {
        if (image[i] > saddleHeight[maxima[i]]) {
          peakIntensity[maxima[i]] += image[i];
          peakSize[maxima[i]]++;
        }
      }
    }

    for (final int[] result : resultsArray) {
      result[RESULT_COUNT_ABOVE_SADDLE] = peakSize[result[RESULT_PEAK_ID]];
      result[RESULT_INTENSITY_ABOVE_SADDLE] = peakIntensity[result[RESULT_PEAK_ID]];
    }
  }

  /**
   * Merge sub-peaks into their highest neighbour peak using the highest saddle point.
   */
  private void mergeSubPeaks(ArrayList<int[]> resultsArray, int[] image, int[] maxima, int minSize,
      int peakMethod, double peakParameter, double[] stats,
      ArrayList<LinkedList<int[]>> saddlePoints, boolean isLogging, boolean restrictAboveSaddle) {
    // Create an array containing the mapping between the original peak Id and the current Id that
    // the peak has been
    // mapped to.
    final int[] peakIdMap = new int[resultsArray.size() + 1];
    for (int i = 0; i < peakIdMap.length; i++) {
      peakIdMap[i] = i;
    }

    if (peakParameter > 0) {
      // Process all the peaks for the minimum height. Process in order of saddle height
      sortDescResults(resultsArray, SORT_SADDLE_HEIGHT, stats);

      for (final int[] result : resultsArray) {
        final int peakId = result[RESULT_PEAK_ID];
        final LinkedList<int[]> saddles = saddlePoints.get(peakId);

        // Check if this peak has been reassigned or has no neighbours
        if (peakId != peakIdMap[peakId]) {
          continue;
        }

        final int[] highestSaddle = findHighestNeighbourSaddle(peakIdMap, saddles, peakId);

        final int peakBase =
            (highestSaddle == null) ? round(stats[STATS_BACKGROUND]) : highestSaddle[1];

        final int threshold =
            getPeakHeight(peakMethod, peakParameter, stats, result[RESULT_MAX_VALUE]);

        if (result[RESULT_MAX_VALUE] - peakBase < threshold) {
          // This peak is not high enough, merge into the neighbour peak
          if (highestSaddle == null) {
            removePeak(image, maxima, peakIdMap, result, peakId);
          } else {
            // Find the neighbour peak (use the map because the neighbour may have been merged)
            final int neighbourPeakId = peakIdMap[highestSaddle[SADDLE_PEAK_ID]];
            final int[] neighbourResult = findResult(resultsArray, neighbourPeakId);

            mergePeak(image, maxima, peakIdMap, peakId, result, neighbourPeakId, neighbourResult,
                saddles, saddlePoints.get(neighbourPeakId), highestSaddle, false);
          }
        }
      }
    }

    if (isLogging) {
      IJ.log("Height filter : Number of peaks = " + countPeaks(peakIdMap));
    }
    if (ImageJUtils.isInterrupted()) {
      return;
    }

    if (minSize > 1) {
      // Process all the peaks for the minimum size. Process in order of smallest first
      sortAscResults(resultsArray, SORT_COUNT, stats);

      for (final int[] result : resultsArray) {
        final int peakId = result[RESULT_PEAK_ID];

        // Check if this peak has been reassigned
        if (peakId != peakIdMap[peakId]) {
          continue;
        }

        if (result[RESULT_COUNT] < minSize) {
          // This peak is not large enough, merge into the neighbour peak

          final LinkedList<int[]> saddles = saddlePoints.get(peakId);
          final int[] highestSaddle = findHighestNeighbourSaddle(peakIdMap, saddles, peakId);

          if (highestSaddle == null) {
            removePeak(image, maxima, peakIdMap, result, peakId);
          } else {
            // Find the neighbour peak (use the map because the neighbour may have been merged)
            final int neighbourPeakId = peakIdMap[highestSaddle[SADDLE_PEAK_ID]];
            final int[] neighbourResult = findResult(resultsArray, neighbourPeakId);

            mergePeak(image, maxima, peakIdMap, peakId, result, neighbourPeakId, neighbourResult,
                saddles, saddlePoints.get(neighbourPeakId), highestSaddle, false);
          }
        }
      }
    }

    if (isLogging) {
      IJ.log("Size filter : Number of peaks = " + countPeaks(peakIdMap));
    }
    if (ImageJUtils.isInterrupted()) {
      return;
    }

    // This can be intensive due to the requirement to recount the peak size above the saddle, so it
    // is optional
    if (minSize > 1 && restrictAboveSaddle) {
      updateSaddleDetails(resultsArray, peakIdMap);
      reassignMaxima(maxima, peakIdMap);
      analysePeaks(resultsArray, image, maxima);

      // Process all the peaks for the minimum size above the saddle points. Process in order of
      // smallest first
      sortAscResults(resultsArray, SORT_COUNT_ABOVE_SADDLE, stats);

      for (final int[] result : resultsArray) {
        final int peakId = result[RESULT_PEAK_ID];

        // Check if this peak has been reassigned
        if (peakId != peakIdMap[peakId]) {
          continue;
        }

        if (result[RESULT_COUNT_ABOVE_SADDLE] < minSize) {
          // This peak is not large enough, merge into the neighbour peak

          final LinkedList<int[]> saddles = saddlePoints.get(peakId);
          final int[] highestSaddle = findHighestNeighbourSaddle(peakIdMap, saddles, peakId);

          if (highestSaddle == null) {
            // No neighbour so just remove
            mergePeak(image, maxima, peakIdMap, peakId, result, 0, null, null, null, null, true);
          } else {
            // Find the neighbour peak (use the map because the neighbour may have been merged)
            final int neighbourPeakId = peakIdMap[highestSaddle[SADDLE_PEAK_ID]];
            final int[] neighbourResult = findResult(resultsArray, neighbourPeakId);

            // Note: Ensure the peak counts above the saddle are updated.
            mergePeak(image, maxima, peakIdMap, peakId, result, neighbourPeakId, neighbourResult,
                saddles, saddlePoints.get(neighbourPeakId), highestSaddle, true);

            // Check for interruption after each merge
            if (ImageJUtils.isInterrupted()) {
              return;
            }
          }
        }
      }

      if (isLogging) {
        IJ.log("Size above saddle filter : Number of peaks = " + countPeaks(peakIdMap));
      }
    }

    // Remove merged peaks from the results
    sortDescResults(resultsArray, SORT_INTENSITY, stats);
    while (resultsArray.size() > 0
        && resultsArray.get(resultsArray.size() - 1)[RESULT_INTENSITY] == 0) {
      resultsArray.remove(resultsArray.size() - 1);
    }

    reassignMaxima(maxima, peakIdMap);

    updateSaddleDetails(resultsArray, peakIdMap);
  }

  private static void removePeak(int[] image, int[] maxima, int[] peakIdMap, int[] result,
      int peakId) {
    // No neighbour so just remove
    mergePeak(image, maxima, peakIdMap, peakId, result, 0, null, null, null, null, false);
  }

  private static int countPeaks(int[] peakIdMap) {
    int count = 0;
    for (int i = 1; i < peakIdMap.length; i++) {
      if (peakIdMap[i] == i) {
        count++;
      }
    }
    return count;
  }

  private static void updateSaddleDetails(ArrayList<int[]> resultsArray, int[] peakIdMap) {
    for (final int[] result : resultsArray) {
      int neighbourPeakId = peakIdMap[result[RESULT_SADDLE_NEIGHBOUR_ID]];

      // Ensure the peak is not marked as a saddle with itself
      if (neighbourPeakId == result[RESULT_PEAK_ID]) {
        neighbourPeakId = 0;
      }

      if (neighbourPeakId == 0) {
        clearSaddle(result);
      } else {
        result[RESULT_SADDLE_NEIGHBOUR_ID] = neighbourPeakId;
      }
    }
  }

  private static void clearSaddle(int[] result) {
    result[RESULT_COUNT_ABOVE_SADDLE] = result[RESULT_COUNT];
    result[RESULT_INTENSITY_ABOVE_SADDLE] = result[RESULT_INTENSITY];
    result[RESULT_SADDLE_NEIGHBOUR_ID] = 0;
    result[RESULT_HIGHEST_SADDLE_VALUE] = 0;
  }

  /**
   * Find the highest saddle that has not been assigned to the specified peak.
   *
   * @param peakIdMap the peak id map
   * @param saddles the saddles
   * @param peakId the peak id
   * @return the result
   */
  private static int[] findHighestNeighbourSaddle(int[] peakIdMap, LinkedList<int[]> saddles,
      int peakId) {
    int[] maxSaddle = null;
    int max = 0;
    for (final int[] saddle : saddles) {
      // Find foci that have not been reassigned to this peak (or nothing)
      final int neighbourPeakId = peakIdMap[saddle[SADDLE_PEAK_ID]];
      if (neighbourPeakId != peakId && neighbourPeakId != 0) {
        if (max < saddle[SADDLE_VALUE]) {
          max = saddle[SADDLE_VALUE];
          maxSaddle = saddle;
        }
      }
    }
    return maxSaddle;
  }

  /**
   * Find the highest saddle that has been assigned to the specified peak.
   *
   * @param peakIdMap the peak id map
   * @param saddles the saddles
   * @param peakId the peak id
   * @return the result
   */
  private static int[] findHighestSaddle(int[] peakIdMap, LinkedList<int[]> saddles, int peakId) {
    int[] maxSaddle = null;
    int max = 0;
    for (final int[] saddle : saddles) {
      // Use the map to ensure the original saddle id corresponds to the current peaks
      final int neighbourPeakId = peakIdMap[saddle[SADDLE_PEAK_ID]];
      if (neighbourPeakId == peakId) {
        if (max < saddle[SADDLE_VALUE]) {
          max = saddle[SADDLE_VALUE];
          maxSaddle = saddle;
        }
      }
    }
    return maxSaddle;
  }

  /**
   * Find the result for the peak in the results array.
   *
   * @param resultsArray the results array
   * @param id the id
   * @return the result
   */
  private static int[] findResult(ArrayList<int[]> resultsArray, int id) {
    for (final int[] result : resultsArray) {
      if (result[RESULT_PEAK_ID] == id) {
        return result;
      }
    }
    return null;
  }

  private static class SaddleComparator implements Comparator<int[]> {
    static final SaddleComparator INSTANCE = new SaddleComparator();

    @Override
    public int compare(int[] o1, int[] o2) {
      if (o1[SADDLE_PEAK_ID] < o2[SADDLE_PEAK_ID]) {
        return -1;
      }
      if (o1[SADDLE_PEAK_ID] > o2[SADDLE_PEAK_ID]) {
        return 1;
      }
      return Integer.compare(o2[SADDLE_VALUE], o1[SADDLE_VALUE]);
    }
  }

  private static class DefaultSaddleComparator implements Comparator<int[]> {
    static final DefaultSaddleComparator INSTANCE = new DefaultSaddleComparator();

    @Override
    public int compare(int[] o1, int[] o2) {
      if (o1[SADDLE_VALUE] > o2[SADDLE_VALUE]) {
        return -1;
      }
      if (o1[SADDLE_VALUE] < o2[SADDLE_VALUE]) {
        return 1;
      }
      return Integer.compare(o1[SADDLE_PEAK_ID], o2[SADDLE_PEAK_ID]);
    }
  }

  /**
   * Assigns the peak to the neighbour. Flags the peak as merged by setting the intensity to zero.
   * If the highest saddle is lowered then recomputes the size/intensity above the saddle.
   *
   * @param image the image
   * @param maxima the maxima
   * @param peakIdMap the peak id map
   * @param peakId the peak id
   * @param result the result
   * @param neighbourPeakId the neighbour peak id
   * @param neighbourResult the neighbour result
   * @param peakSaddles the peak saddles
   * @param neighbourSaddles the neighbour saddles
   * @param highestSaddle the highest saddle
   * @param updatePeakAboveSaddle the update peak above saddle
   */
  private static void mergePeak(int[] image, int[] maxima, int[] peakIdMap, int peakId,
      int[] result, int neighbourPeakId, int[] neighbourResult, LinkedList<int[]> peakSaddles,
      LinkedList<int[]> neighbourSaddles, int[] highestSaddle, boolean updatePeakAboveSaddle) {
    if (neighbourResult != null) {
      // IJ.log("Merging " + peakId + " (" + result[RESULT_COUNT] + ") -> " + neighbourPeakId + " ("
      // +
      // neighbourResult[RESULT_COUNT] + ")");

      // Assign this peak's statistics to the neighbour
      neighbourResult[RESULT_INTENSITY] += result[RESULT_INTENSITY];
      neighbourResult[RESULT_COUNT] += result[RESULT_COUNT];

      neighbourResult[RESULT_AVERAGE_INTENSITY] =
          neighbourResult[RESULT_INTENSITY] / neighbourResult[RESULT_COUNT];

      // Check if the neighbour is higher and reassign the maximum point
      if (neighbourResult[RESULT_MAX_VALUE] < result[RESULT_MAX_VALUE]) {
        neighbourResult[RESULT_MAX_VALUE] = result[RESULT_MAX_VALUE];
        neighbourResult[RESULT_X] = result[RESULT_X];
        neighbourResult[RESULT_Y] = result[RESULT_Y];
        neighbourResult[RESULT_Z] = result[RESULT_Z];
      }

      // 19.09.2016: Added to match the new implementation in the FindFociBaseProcessor

      // Consolidate the saddles of the neighbour. This should speed up processing.
      // 1. Remove all saddle with the peak that is being merged.
      int size = 0;
      int[][] newNeighbourSaddles = new int[neighbourSaddles.size()][];
      for (final int[] saddle : neighbourSaddles) {
        if (peakIdMap[saddle[SADDLE_PEAK_ID]] == peakId || peakIdMap[saddle[SADDLE_PEAK_ID]] == 0) {
          // Ignore saddle with peak that is being merged or has been removed
          continue;
        }
        // Consolidate the id
        saddle[SADDLE_PEAK_ID] = peakIdMap[saddle[SADDLE_PEAK_ID]];
        newNeighbourSaddles[size++] = saddle;
      }
      newNeighbourSaddles = Arrays.copyOf(newNeighbourSaddles, size);
      Arrays.sort(newNeighbourSaddles, SaddleComparator.INSTANCE);

      // 2. Remove all but the highest saddle with other peaks.
      int lastId = 0;
      size = 0;
      for (int i = 0; i < newNeighbourSaddles.length; i++) {
        if (lastId != newNeighbourSaddles[i][SADDLE_PEAK_ID]) {
          newNeighbourSaddles[size++] = newNeighbourSaddles[i];
        }
        lastId = newNeighbourSaddles[i][SADDLE_PEAK_ID];
      }
      newNeighbourSaddles = Arrays.copyOf(newNeighbourSaddles, size);
      Arrays.sort(newNeighbourSaddles, DefaultSaddleComparator.INSTANCE);

      neighbourSaddles.clear();
      neighbourSaddles.addAll(Arrays.asList(newNeighbourSaddles));

      // Consolidate the peak saddles too...
      int size2 = 0;
      int[][] newSaddles = new int[peakSaddles.size()][];
      for (final int[] saddle : peakSaddles) {
        if (peakIdMap[saddle[SADDLE_PEAK_ID]] == neighbourPeakId
            || peakIdMap[saddle[SADDLE_PEAK_ID]] == 0) {
          // Ignore saddle with peak that is being merged or has been removed
          continue;
        }
        // Consolidate the id
        saddle[SADDLE_PEAK_ID] = peakIdMap[saddle[SADDLE_PEAK_ID]];
        newSaddles[size2++] = saddle;
      }
      newSaddles = Arrays.copyOf(newSaddles, size2);
      Arrays.sort(newSaddles, SaddleComparator.INSTANCE);

      // Merge the saddles
      lastId = 0;
      boolean doSort = false;
      for (int i = 0; i < newSaddles.length; i++) {
        if (lastId == newSaddles[i][SADDLE_PEAK_ID]) {
          continue;
        }
        lastId = newSaddles[i][SADDLE_PEAK_ID];

        final int[] peakSaddle = newSaddles[i];
        final int saddlePeakId = peakIdMap[peakSaddle[SADDLE_PEAK_ID]];
        final int[] neighbourSaddle = findHighestSaddle(peakIdMap, neighbourSaddles, saddlePeakId);
        if (neighbourSaddle == null) {
          // The neighbour peak does not touch this peak, add to the list
          neighbourSaddles.add(peakSaddle);
          doSort = true;
        } else // Check if the saddle is higher
        if (neighbourSaddle[SADDLE_VALUE] < peakSaddle[SADDLE_VALUE]) {
          neighbourSaddle[SADDLE_VALUE] = peakSaddle[SADDLE_VALUE];
          doSort = true;
        }
      }

      if (doSort) {
        Collections.sort(neighbourSaddles, DefaultSaddleComparator.INSTANCE);
      }

      // Free memory
      peakSaddles.clear();
    }
    // else
    // {
    // IJ.log("Merging " + peakId + " (" + result[RESULT_COUNT] + ") -> " + neighbourPeakId);
    // }

    // Map anything previously mapped to this peak to the new neighbour
    for (int i = peakIdMap.length; i-- > 0;) {
      if (peakIdMap[i] == peakId) {
        peakIdMap[i] = neighbourPeakId;
      }
    }

    // Flag this result as merged using the intensity flag. This will be used later to eliminate
    // peaks
    result[RESULT_INTENSITY] = 0;

    // Update the count and intensity above the highest neighbour saddle
    if (neighbourResult != null) {
      final int[] newHighestSaddle =
          findHighestNeighbourSaddle(peakIdMap, neighbourSaddles, neighbourPeakId);
      if (newHighestSaddle != null) {
        reanalysePeak(image, maxima, peakIdMap, neighbourPeakId, newHighestSaddle, neighbourResult,
            updatePeakAboveSaddle);
      } else {
        clearSaddle(neighbourResult);
      }
    }
  }

  /**
   * Reassign the maxima using the peak Id map and recounts all pixels above the saddle height.
   *
   * @param image the image
   * @param maxima the maxima
   * @param peakIdMap the peak id map
   * @param peakId the peak id
   * @param saddle the saddle
   * @param result the result
   * @param updatePeakAboveSaddle the update peak above saddle
   */
  private static void reanalysePeak(int[] image, int[] maxima, int[] peakIdMap, int peakId,
      int[] saddle, int[] result, boolean updatePeakAboveSaddle) {
    if (updatePeakAboveSaddle) {
      int peakSize = 0;
      int peakIntensity = 0;
      final int saddleHeight = saddle[1];
      for (int i = maxima.length; i-- > 0;) {
        if (maxima[i] > 0) {
          maxima[i] = peakIdMap[maxima[i]];
          if (maxima[i] == peakId) {
            if (image[i] > saddleHeight) {
              peakIntensity += image[i];
              peakSize++;
            }
          }
        }
      }

      result[RESULT_COUNT_ABOVE_SADDLE] = peakSize;
      result[RESULT_INTENSITY_ABOVE_SADDLE] = peakIntensity;
    }

    result[RESULT_SADDLE_NEIGHBOUR_ID] = peakIdMap[saddle[SADDLE_PEAK_ID]];
    result[RESULT_HIGHEST_SADDLE_VALUE] = saddle[SADDLE_VALUE];
  }

  /**
   * Reassign the maxima using the peak Id map.
   *
   * @param maxima the maxima
   * @param peakIdMap the peak id map
   */
  private static void reassignMaxima(int[] maxima, int[] peakIdMap) {
    for (int i = maxima.length; i-- > 0;) {
      if (maxima[i] != 0) {
        maxima[i] = peakIdMap[maxima[i]];
      }
    }
  }

  /**
   * Removes any maxima that have pixels that touch the edge.
   *
   * @param resultsArray the results array
   * @param image the image
   * @param maxima the maxima
   * @param stats the stats
   * @param isLogging the is logging
   */
  private void removeEdgeMaxima(ArrayList<int[]> resultsArray, int[] image, int[] maxima,
      double[] stats, boolean isLogging) {
    // Build a look-up table for all the peak IDs
    int maxId = 0;
    for (final int[] result : resultsArray) {
      if (maxId < result[RESULT_PEAK_ID]) {
        maxId = result[RESULT_PEAK_ID];
      }
    }

    final int[] peakIdMap = new int[maxId + 1];
    for (int i = 0; i < peakIdMap.length; i++) {
      peakIdMap[i] = i;
    }

    // Support the ROI bounds used to create the analysis region
    final int lowerx, upperx, lowery, uppery;
    if (bounds != null) {
      lowerx = bounds.x;
      lowery = bounds.y;
      upperx = bounds.x + bounds.width;
      uppery = bounds.y + bounds.height;
    } else {
      lowerx = 0;
      upperx = maxx;
      lowery = 0;
      uppery = maxy;
    }

    // Set the look-up to zero if the peak contains edge pixels
    for (int z = maxz; z-- > 0;) {
      // Look at top and bottom column
      for (int y = uppery, i = getIndex(lowerx, lowery, z), ii = getIndex(upperx - 1, lowery, z);
          y-- > lowery; i += maxx, ii += maxx) {
        peakIdMap[maxima[i]] = 0;
        peakIdMap[maxima[ii]] = 0;
      }
      // Look at top and bottom row
      for (int x = upperx, i = getIndex(lowerx, lowery, z), ii = getIndex(lowerx, uppery - 1, z);
          x-- > lowerx; i++, ii++) {
        peakIdMap[maxima[i]] = 0;
        peakIdMap[maxima[ii]] = 0;
      }
    }

    // Mark maxima to be removed
    for (final int[] result : resultsArray) {
      final int peakId = result[RESULT_PEAK_ID];
      if (peakIdMap[peakId] == 0) {
        result[RESULT_INTENSITY] = 0;
      }
    }

    // Remove maxima
    sortDescResults(resultsArray, SORT_INTENSITY, stats);
    while (resultsArray.size() > 0
        && resultsArray.get(resultsArray.size() - 1)[RESULT_INTENSITY] == 0) {
      resultsArray.remove(resultsArray.size() - 1);
    }

    reassignMaxima(maxima, peakIdMap);

    updateSaddleDetails(resultsArray, peakIdMap);
  }

  /**
   * Sort the results using the specified index in descending order.
   *
   * @param resultsArray the results array
   * @param sortIndex the sort index
   * @param stats the stats
   */
  void sortDescResults(ArrayList<int[]> resultsArray, int sortIndex, double[] stats) {
    Collections.sort(resultsArray,
        new ResultDescComparator(getResultIndex(sortIndex, resultsArray, stats)));
  }

  /**
   * Sort the results using the specified index in ascending order.
   *
   * @param resultsArray the results array
   * @param sortIndex the sort index
   * @param stats the stats
   */
  void sortAscResults(ArrayList<int[]> resultsArray, int sortIndex, double[] stats) {
    Collections.sort(resultsArray,
        new ResultAscComparator(getResultIndex(sortIndex, resultsArray, stats)));
  }

  private int getResultIndex(int sortIndex, ArrayList<int[]> resultsArray, double[] stats) {
    switch (sortIndex) {
      case SORT_INTENSITY:
        return RESULT_INTENSITY;
      case SORT_INTENSITY_MINUS_BACKGROUND:
        return RESULT_INTENSITY_MINUS_BACKGROUND;
      case SORT_COUNT:
        return RESULT_COUNT;
      case SORT_MAX_VALUE:
        return RESULT_MAX_VALUE;
      case SORT_AVERAGE_INTENSITY:
        return RESULT_AVERAGE_INTENSITY;
      case SORT_AVERAGE_INTENSITY_MINUS_BACKGROUND:
        return RESULT_AVERAGE_INTENSITY_MINUS_BACKGROUND;
      case SORT_X:
        return RESULT_X;
      case SORT_Y:
        return RESULT_Y;
      case SORT_Z:
        return RESULT_Z;
      case SORT_SADDLE_HEIGHT:
        return RESULT_HIGHEST_SADDLE_VALUE;
      case SORT_COUNT_ABOVE_SADDLE:
        return RESULT_COUNT_ABOVE_SADDLE;
      case SORT_INTENSITY_ABOVE_SADDLE:
        return RESULT_INTENSITY_ABOVE_SADDLE;
      case SORT_ABSOLUTE_HEIGHT:
        customSortAbsoluteHeight(resultsArray, round(stats[STATS_BACKGROUND]));
        return RESULT_CUSTOM_SORT_VALUE;
      case SORT_RELATIVE_HEIGHT_ABOVE_BACKGROUND:
        customSortRelativeHeightAboveBackground(resultsArray, round(stats[STATS_BACKGROUND]));
        return RESULT_CUSTOM_SORT_VALUE;
      case SORT_PEAK_ID:
        return RESULT_PEAK_ID;
      case SORT_XYZ:
        customSortXYZ(resultsArray);
        return RESULT_CUSTOM_SORT_VALUE;
    }
    return RESULT_INTENSITY;
  }

  private static void customSortAbsoluteHeight(ArrayList<int[]> resultsArray, int background) {
    for (final int[] result : resultsArray) {
      result[RESULT_CUSTOM_SORT_VALUE] = getAbsoluteHeight(result, background);
    }
  }

  private static void customSortRelativeHeightAboveBackground(ArrayList<int[]> resultsArray,
      int background) {
    for (final int[] result : resultsArray) {
      final int absoluteHeight = getAbsoluteHeight(result, background);
      // Increase the relative height to avoid rounding when casting to int (Note: relative height
      // is in range [0 - 1])
      // result[RESULT_CUSTOM_SORT_VALUE] = round(100000.0 * getRelativeHeight(result, background,
      // absoluteHeight));
      result[RESULT_CUSTOM_SORT_VALUE] =
          round(getRelativeHeight(result, background, absoluteHeight << 8));
    }
  }

  private void customSortXYZ(ArrayList<int[]> resultsArray) {
    final int a = maxy * maxz;
    final int b = maxz;
    for (final int[] result : resultsArray) {
      final int x = result[RESULT_X];
      final int y = result[RESULT_Y];
      final int z = result[RESULT_Z];
      result[RESULT_CUSTOM_SORT_VALUE] = x * a + y * b + z;
    }
  }

  /**
   * Initialises the global width, height and depth variables. Creates the direction offset tables.
   */
  private void initialise(ImagePlus imp) {
    maxx = imp.getWidth();
    maxy = imp.getHeight();
    maxz = imp.getNSlices();

    // Used to look-up x,y,z from a single index
    maxx_maxy = maxx * maxy;
    maxx_maxy_maxz = maxx * maxy * maxz;

    xlimit = maxx - 1;
    ylimit = maxy - 1;
    zlimit = maxz - 1;
    dStart = (maxz == 1) ? 8 : 26;

    // Create the offset table (for single array 3D neighbour comparisons)
    offset = new int[DIR_X_OFFSET.length];
    flatEdge = new boolean[DIR_X_OFFSET.length];
    for (int d = offset.length; d-- > 0;) {
      offset[d] = getIndex(DIR_X_OFFSET[d], DIR_Y_OFFSET[d], DIR_Z_OFFSET[d]);
      flatEdge[d] =
          (Math.abs(DIR_X_OFFSET[d]) + Math.abs(DIR_Y_OFFSET[d]) + Math.abs(DIR_Z_OFFSET[d]) == 1);
    }
  }

  /**
   * Return the single index associated with the x,y,z coordinates.
   *
   * @param x the x
   * @param y the y
   * @param z the z
   * @return The index
   */
  private int getIndex(int x, int y, int z) {
    return (maxx_maxy) * z + maxx * y + x;
  }

  /**
   * Convert the single index into x,y,z coords, Input array must be length >= 3.
   *
   * @param index the index
   * @param xyz the xyz
   * @return The xyz array
   */
  private int[] getXyz(int index, int[] xyz) {
    xyz[2] = index / (maxx_maxy);
    final int mod = index % (maxx_maxy);
    xyz[1] = mod / maxx;
    xyz[0] = mod % maxx;
    return xyz;
  }

  /**
   * Convert the single index into x,y,z coords, Input array must be length >= 3.
   *
   * @param index the index
   * @param xyz the xyz
   * @return The xyz array
   */
  private int[] getXy(int index, int[] xyz) {
    final int mod = index % (maxx_maxy);
    xyz[1] = mod / maxx;
    xyz[0] = mod % maxx;
    return xyz;
  }

  /**
   * returns whether the neighbour in a given direction is within the image. NOTE: it is assumed
   * that the pixel x,y itself is within the image! Uses class variables xlimit, ylimit: (dimensions
   * of the image)-1
   *
   * @param x x-coordinate of the pixel that has a neighbour in the given direction
   * @param y y-coordinate of the pixel that has a neighbour in the given direction
   * @param direction the direction from the pixel towards the neighbour
   * @return true if the neighbour is within the image (provided that x, y is within)
   */
  private boolean isWithinXy(int x, int y, int direction) {
    switch (direction) {
      case 0:
        return (y > 0);
      case 1:
        return (y > 0 && x < xlimit);
      case 2:
        return (x < xlimit);
      case 3:
        return (y < ylimit && x < xlimit);
      case 4:
        return (y < ylimit);
      case 5:
        return (y < ylimit && x > 0);
      case 6:
        return (x > 0);
      case 7:
        return (y > 0 && x > 0);
      case 8:
        return true;
    }
    return false;
  }

  /**
   * returns whether the neighbour in a given direction is within the image. NOTE: it is assumed
   * that the pixel x,y,z itself is within the image! Uses class variables xlimit, ylimit, zlimit:
   * (dimensions of the image)-1
   *
   * @param x x-coordinate of the pixel that has a neighbour in the given direction
   * @param y y-coordinate of the pixel that has a neighbour in the given direction
   * @param z z-coordinate of the pixel that has a neighbour in the given direction
   * @param direction the direction from the pixel towards the neighbour
   * @return true if the neighbour is within the image (provided that x, y, z is within)
   */
  private boolean isWithinXyz(int x, int y, int z, int direction) {
    switch (direction) {
      case 0:
        return (y > 0);
      case 1:
        return (y > 0 && x < xlimit);
      case 2:
        return (x < xlimit);
      case 3:
        return (y < ylimit && x < xlimit);
      case 4:
        return (y < ylimit);
      case 5:
        return (y < ylimit && x > 0);
      case 6:
        return (x > 0);
      case 7:
        return (y > 0 && x > 0);
      case 8:
        return (z > 0 && y > 0);
      case 9:
        return (z > 0 && y > 0 && x < xlimit);
      case 10:
        return (z > 0 && x < xlimit);
      case 11:
        return (z > 0 && y < ylimit && x < xlimit);
      case 12:
        return (z > 0 && y < ylimit);
      case 13:
        return (z > 0 && y < ylimit && x > 0);
      case 14:
        return (z > 0 && x > 0);
      case 15:
        return (z > 0 && y > 0 && x > 0);
      case 16:
        return (z > 0);
      case 17:
        return (z < zlimit && y > 0);
      case 18:
        return (z < zlimit && y > 0 && x < xlimit);
      case 19:
        return (z < zlimit && x < xlimit);
      case 20:
        return (z < zlimit && y < ylimit && x < xlimit);
      case 21:
        return (z < zlimit && y < ylimit);
      case 22:
        return (z < zlimit && y < ylimit && x > 0);
      case 23:
        return (z < zlimit && x > 0);
      case 24:
        return (z < zlimit && y > 0 && x > 0);
      case 25:
        return (z < zlimit);
    }
    return false;
  }

  /**
   * returns whether the neighbour in a given direction is within the image. NOTE: it is assumed
   * that the pixel z itself is within the image! Uses class variables zlimit: (dimensions of the
   * image)-1
   *
   * @param z z-coordinate of the pixel that has a neighbour in the given direction
   * @param direction the direction from the pixel towards the neighbour
   * @return true if the neighbour is within the image (provided that z is within)
   */
  private boolean isWithinZ(int z, int direction) {
    // z = 0
    if (direction < 8) {
      return true;
    }
    // z = -1
    if (direction < 17) {
      return (z > 0);
    }
    // z = 1
    return z < zlimit;
  }

  /**
   * Check if the logging flag is enabled.
   *
   * @param outputType The output options flag
   * @return True if logging
   */
  private static boolean isLogging(int outputType) {
    return (outputType & OUTPUT_LOG_MESSAGES) != 0;
  }

  /**
   * Stores the details of a pixel position.
   */
  private class Coordinate implements Comparable<Coordinate> {
    public int index;
    public int id;
    public int value;

    public Coordinate(int index, int id, int value) {
      this.index = index;
      this.id = id;
      this.value = value;
    }

    public Coordinate(int x, int y, int z, int id, int value) {
      this.index = getIndex(x, y, z);
      this.id = id;
      this.value = value;
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(Coordinate o) {
      // Require the sort to rank the highest peak as first.
      // Since sort works in ascending order return a negative for a higher value.
      if (value > o.value) {
        return -1;
      }
      if (value < o.value) {
        return 1;
      }
      return 0;
    }
  }

  private class ResultComparator implements Comparator<int[]> {
    /** {@inheritDoc} */
    @Override
    public int compare(int[] o1, int[] o2) {
      if (o1[RESULT_MAX_VALUE] > o2[RESULT_MAX_VALUE]) {
        return -1;
      }
      if (o1[RESULT_MAX_VALUE] < o2[RESULT_MAX_VALUE]) {
        return 1;
      }
      if (o1[RESULT_COUNT] > o2[RESULT_COUNT]) {
        return -1;
      }
      if (o1[RESULT_COUNT] < o2[RESULT_COUNT]) {
        return 1;
      }
      if (o1[RESULT_X] > o2[RESULT_X]) {
        return 1;
      }
      if (o1[RESULT_X] < o2[RESULT_X]) {
        return -1;
      }
      if (o1[RESULT_Y] > o2[RESULT_Y]) {
        return 1;
      }
      if (o1[RESULT_Y] < o2[RESULT_Y]) {
        return -1;
      }
      if (o1[RESULT_Z] > o2[RESULT_Z]) {
        return 1;
      }
      if (o1[RESULT_Z] < o2[RESULT_Z]) {
        return -1;
      }
      // This should not happen as two maxima will be in the same position
      throw new RuntimeException("Unable to sort the results");
    }
  }

  /**
   * Provides the ability to sort the results arrays in descending order.
   */
  private class ResultDescComparator extends ResultComparator {
    private int sortIndex = 0;

    public ResultDescComparator(int sortIndex) {
      this.sortIndex = sortIndex;
    }

    /** {@inheritDoc} */
    @Override
    public int compare(int[] o1, int[] o2) {
      // Require the highest is first
      if (o1[sortIndex] > o2[sortIndex]) {
        return -1;
      }
      if (o1[sortIndex] < o2[sortIndex]) {
        return 1;
      }
      return super.compare(o1, o2);
    }
  }

  /**
   * Provides the ability to sort the results arrays in ascending order.
   */
  private class ResultAscComparator extends ResultComparator {
    private int sortIndex = 0;

    public ResultAscComparator(int sortIndex) {
      this.sortIndex = sortIndex;
    }

    /** {@inheritDoc} */
    @Override
    public int compare(int[] o1, int[] o2) {
      // Require the lowest is first
      if (o1[sortIndex] > o2[sortIndex]) {
        return 1;
      }
      if (o1[sortIndex] < o2[sortIndex]) {
        return -1;
      }
      // Reverse order for ascending sort
      return super.compare(o2, o1);
    }
  }
}
