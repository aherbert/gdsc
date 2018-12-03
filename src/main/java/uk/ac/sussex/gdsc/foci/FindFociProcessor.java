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
package uk.ac.sussex.gdsc.foci;

import ij.ImagePlus;

/**
 * Interface describing the methods for the FindFoci algorithm to find the peak intensity regions of
 * an image. <p> Note this interface serves as the public documentation of the FindFoci algorithm.
 */
public interface FindFociProcessor {
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
   * Create an output mask showing only pixels above the peak's highest saddle value
   */
  public static final int OUTPUT_MASK_ABOVE_SADDLE = 64;
  /**
   * Do not mark the peaks location on the output mask using a single pixel dot. The pixel dot will
   * use the brightest available value, which is the number of maxima + 1.
   */
  public static final int OUTPUT_MASK_NO_PEAK_DOTS = 128;
  /**
   * Create an output mask showing only the pixels contributing to a cumulative fraction of the
   * peak's total intensity
   */
  public static final int OUTPUT_MASK_FRACTION_OF_INTENSITY = 256;
  /**
   * Create an output mask showing only pixels above a fraction of the peak's highest value
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
  public static final int SORT_PEAK_ID = 14;
  /**
   * Sort the peaks using the XYZ coordinates (in order).
   */
  public static final int SORT_XYZ = 15;
  /**
   * Sort the peaks using the sum of pixel intensity (minus the minimum in the image).
   */
  public static final int SORT_INTENSITY_MINUS_MIN = 16;
  /**
   * Sort the peaks using the average pixel value (minus the minimum in the image).
   */
  public static final int SORT_AVERAGE_INTENSITY_MINUS_MIN = 17;

  /**
   * Apply the minimum size criteria to the peak size above the highest saddle point.
   */
  public static final int OPTION_MINIMUM_ABOVE_SADDLE = 1;
  /**
   * Calculate the statistics using the pixels outside the ROI/Mask (default is all pixels)
   */
  public static final int OPTION_STATS_OUTSIDE = 2;
  /**
   * Calculate the statistics using the pixels inside the ROI/Mask (default is all pixels)
   */
  public static final int OPTION_STATS_INSIDE = 4;
  /**
   * Remove any maxima that touch the edge of the image.
   */
  public static final int OPTION_REMOVE_EDGE_MAXIMA = 8;
  /**
   * Identify all connected non-zero mask pixels with the same value as objects and label the maxima
   * as belonging to each object
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
  /**
   * The peak above the highest saddle point must be contiguous. The legacy algorithm used
   * non-contiguous pixels above the saddle.
   */
  public static final int OPTION_CONTIGUOUS_ABOVE_SADDLE = 128;

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
   * Here the processing is done: Find the maxima of an image.
   *
   * <P> Local maxima are processed in order, highest first. Regions are grown from local maxima
   * until a saddle point is found or the stopping criteria are met (based on pixel intensity). If a
   * peak does not meet the peak criteria (min size) it is absorbed into the highest peak that
   * touches it (if a neighbour peak exists). Only a single iteration is performed and consequently
   * peak absorption could produce sub-optimal results due to greedy peak growth.
   *
   * <P> Peak expansion stopping criteria are defined using the method parameter. See
   * {@link #SEARCH_ABOVE_BACKGROUND}; {@link #SEARCH_FRACTION_OF_PEAK_MINUS_BACKGROUND};
   * {@link #SEARCH_HALF_PEAK_VALUE}.
   *
   * @param imp the image
   * @param mask A mask image used to define the region to search for peaks
   * @param backgroundMethod Method for calculating the background level (use the constants with
   *        prefix {@code BACKGROUND_}, e.g. {@link #BACKGROUND_AUTO_THRESHOLD}).
   * @param backgroundParameter parameter for calculating the background level
   * @param autoThresholdMethod The thresholding method (use a string from
   *        {@link uk.ac.sussex.gdsc.core.threshold.AutoThreshold#getMethods() } )
   * @param searchMethod Method for calculating the region growing stopping criteria (use the
   *        constants with prefix {@code SEARCH_}, e.g. {@link #SEARCH_ABOVE_BACKGROUND}).
   * @param searchParameter parameter for calculating the stopping criteria
   * @param maxPeaks The maximum number of peaks to report
   * @param minSize The minimum size for a peak
   * @param peakMethod Method for calculating the minimum peak height above the highest saddle (use
   *        the constants with prefix {@code PEAK_}, e.g. {@link #PEAK_RELATIVE}).
   * @param peakParameter parameter for calculating the minimum peak height
   * @param outputType Use {@link #OUTPUT_MASK_PEAKS} to get an ImagePlus in the result Object
   *        array. Use {@link #OUTPUT_LOG_MESSAGES} to get runtime information.
   * @param sortIndex The index of the result statistic to use for the peak sorting
   * @param options An options flag (use the constants with prefix {@code OPTION_}, e.g.
   *        {@link #OPTION_MINIMUM_ABOVE_SADDLE}).
   * @param blur Apply a Gaussian blur of the specified radius before processing (helps smooth noisy
   *        images for better peak identification)
   * @param centreMethod Define the method used to calculate the peak centre (use the constants with
   *        prefix {@code CENTRE_}, e.g. {@link #CENTRE_MAX_VALUE_SEARCH}
   * @param centreParameter Parameter for calculating the peak centre
   * @param fractionParameter Used to specify the fraction of the peak to show in the mask
   * @return Result containing: <ol> <li>a new ImagePlus (with a stack) where the maxima are set to
   *         {@code nMaxima+1} and peak areas numbered starting from {@code nMaxima} (Background 0).
   *         Pixels outside of the ROI of the input image are not set. Alternatively the peak areas
   *         can be thresholded using the auto-threshold method and coloured 1(saddle),
   *         2(background), 3(threshold), 4(peak);</li> <li>a result {@code ArrayList<double[]>}
   *         with details of the maxima. The details can be extracted for each result using the
   *         constants defined with the prefix FindFoci.RESULT_;</li> <li>the image statistics:
   *         {@link FindFociStatistics}.</li> </ol> Returns null if cancelled by escape.
   */
  public FindFociResults findMaxima(ImagePlus imp, ImagePlus mask, int backgroundMethod,
      double backgroundParameter, String autoThresholdMethod, int searchMethod,
      double searchParameter, int maxPeaks, int minSize, int peakMethod, double peakParameter,
      int outputType, int sortIndex, int options, double blur, int centreMethod,
      double centreParameter, double fractionParameter);

  /**
   * Apply a Gaussian blur to the image and returns a new image. Returns the original image if
   * {@code blur <= 0}. <p> Only blurs the current channel and frame for use in the FindFoci
   * algorithm.
   *
   * @param imp the image
   * @param blur The blur standard deviation
   * @return the blurred image
   */
  public ImagePlus blur(ImagePlus imp, double blur);

  /**
   * This method is a stripped-down version of the
   * {@link #findMaxima(ImagePlus, ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int, double, int, double, double) }
   * routine. It does not support logging, interruption or mask generation. The method initialises
   * the system up to the point of background generation. The result object can be cloned and passed
   * multiple times to later methods for further processing. <p> This method is intended for
   * benchmarking.
   *
   * @param originalImp the original image
   * @param imp the image after the blur has been applied ( see {@link #blur(ImagePlus, double)} ).
   *        This allows the blur to be pre-computed.
   * @param mask A mask image used to define the region to search for peaks
   * @param backgroundMethod Method for calculating the background level (use the constants with
   *        prefix {@code BACKGROUND_}, e.g. {@link #BACKGROUND_AUTO_THRESHOLD}).
   * @param autoThresholdMethod The thresholding method (use a string from
   *        {@link uk.ac.sussex.gdsc.core.threshold.AutoThreshold#getMethods() } )
   * @param options An options flag (use the constants with prefix {@code OPTION_}, e.g.
   *        {@link #OPTION_MINIMUM_ABOVE_SADDLE}).
   * @return the initialisation results
   */
  public FindFociInitResults findMaximaInit(ImagePlus originalImp, ImagePlus imp, ImagePlus mask,
      int backgroundMethod, String autoThresholdMethod, int options);

  /**
   * Clones the init array for use in findMaxima staged methods. Only the elements that are
   * destructively modified by the findMaxima staged methods are duplicated. The rest are shallow
   * copied.
   *
   * @param initResults The original init results object
   * @param clonedInitResults A previously cloned init results object (avoid reallocating memory).
   *        Can be null.
   * @return the find foci init results
   */
  public FindFociInitResults clone(FindFociInitResults initResults,
      FindFociInitResults clonedInitResults);

  /**
   * This method is a stripped-down version of the
   * {@link #findMaxima(ImagePlus, ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int, double, int, double, double) }
   * routine. It does not support logging, interruption or mask generation. Only the result array is
   * generated. <p> This method is intended for benchmarking.
   *
   * @param initResults The output from
   *        {@link #findMaximaInit(ImagePlus, ImagePlus, ImagePlus, int, String, int)}. Contents are
   *        destructively modified so should be cloned before input.
   * @param backgroundMethod Method for calculating the background level (use the constants with
   *        prefix {@code BACKGROUND_}, e.g. {@link #BACKGROUND_AUTO_THRESHOLD}).
   * @param backgroundParameter parameter for calculating the background level
   * @param searchMethod Method for calculating the region growing stopping criteria (use the
   *        constants with prefix {@code SEARCH_}, e.g. {@link #SEARCH_ABOVE_BACKGROUND}).
   * @param searchParameter parameter for calculating the stopping criteria
   * @return the find foci search results
   */
  public FindFociSearchResults findMaximaSearch(FindFociInitResults initResults,
      int backgroundMethod, double backgroundParameter, int searchMethod, double searchParameter);

  /**
   * This method is a stripped-down version of the
   * {@link #findMaxima(ImagePlus, ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int, double, int, double, double) }
   * routine. It does not support logging, interruption or mask generation. Only the result array is
   * generated. <p> This method is intended for benchmarking.
   *
   * @param initResults The output from
   *        {@link #findMaximaInit(ImagePlus, ImagePlus, ImagePlus, int, String, int)}.
   * @param searchResults The output from
   *        {@link #findMaximaSearch(FindFociInitResults, int, double, int, double) }. Contents are
   *        unchanged.
   * @param peakMethod Method for calculating the minimum peak height above the highest saddle (use
   *        the constants with prefix {@code PEAK_}, e.g. {@link #PEAK_RELATIVE}).
   * @param peakParameter parameter for calculating the minimum peak height
   * @return the find foci merge results
   */
  public FindFociMergeTempResults findMaximaMergePeak(FindFociInitResults initResults,
      FindFociSearchResults searchResults, int peakMethod, double peakParameter);

  /**
   * This method is a stripped-down version of the
   * {@link #findMaxima(ImagePlus, ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int, double, int, double, double) }
   * routine. It does not support logging, interruption or mask generation. Only the result array is
   * generated. <p> This method is intended for benchmarking.
   *
   * @param initResults The output from
   *        {@link #findMaximaInit(ImagePlus, ImagePlus, ImagePlus, int, String, int)}.
   * @param mergeResults The output from
   *        {@link #findMaximaMergePeak(FindFociInitResults, FindFociSearchResults, int, double)}.
   * @param minSize The minimum size for a peak
   * @return the find foci merge results
   */
  public FindFociMergeTempResults findMaximaMergeSize(FindFociInitResults initResults,
      FindFociMergeTempResults mergeResults, int minSize);

  /**
   * This method is a stripped-down version of the
   * {@link #findMaxima(ImagePlus, ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int, double, int, double, double) }
   * routine. It does not support logging, interruption or mask generation. Only the result array is
   * generated. <p> This method is intended for benchmarking.
   *
   * @param initResults The output from
   *        {@link #findMaximaInit(ImagePlus, ImagePlus, ImagePlus, int, String, int)}. Contents are
   *        destructively modified so should be cloned before input.
   * @param mergeResults The output from
   *        {@link #findMaximaMergeSize(FindFociInitResults, FindFociMergeTempResults, int)}.
   * @param minSize The minimum size for a peak
   * @param options An options flag (use the constants with prefix {@code OPTION_}, e.g.
   *        {@link #OPTION_MINIMUM_ABOVE_SADDLE}).
   * @param blur Apply a Gaussian blur of the specified radius before processing (helps smooth noisy
   *        images for better peak identification)
   * @return the find foci merge results
   */
  public FindFociMergeResults findMaximaMergeFinal(FindFociInitResults initResults,
      FindFociMergeTempResults mergeResults, int minSize, int options, double blur);

  /**
   * This method is a stripped-down version of the
   * {@link #findMaxima(ImagePlus, ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int, double, int, double, double) }
   * routine. It does not support logging, interruption or mask generation. Only the result array is
   * generated. <p> This method is intended for benchmarking.
   *
   * @param initResults The output from
   *        {@link #findMaximaInit(ImagePlus, ImagePlus, ImagePlus, int, String, int)}. Contents are
   *        destructively modified so should be cloned before input.
   * @param mergeResults The output from
   *        {@link #findMaximaMergeFinal(FindFociInitResults, FindFociMergeTempResults, int, int, double)}
   *        Contents are unchanged.
   * @param maxPeaks The maximum number of peaks to report
   * @param sortIndex The index of the result statistic to use for the peak sorting
   * @param centreMethod Define the method used to calculate the peak centre (use the constants with
   *        prefix {@code CENTRE_}, e.g. {@link #CENTRE_MAX_VALUE_SEARCH}
   * @param centreParameter Parameter for calculating the peak centre
   * @return the find foci results
   */
  public FindFociResults findMaximaResults(FindFociInitResults initResults,
      FindFociMergeResults mergeResults, int maxPeaks, int sortIndex, int centreMethod,
      double centreParameter);

  /**
   * This method is a stripped-down version of the
   * {@link #findMaxima(ImagePlus, ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int, double, int, double, double) }
   * routine. It does not support logging, interruption or mask generation. Only the result array is
   * generated. <p> This method is intended for staged processing.
   *
   * @param initResults The output from
   *        {@link #findMaximaInit(ImagePlus, ImagePlus, ImagePlus, int, String, int)}. Contents are
   *        destructively modified so should be cloned before input.
   * @param mergeResults The output from
   *        {@link #findMaximaMergeFinal(FindFociInitResults, FindFociMergeTempResults, int, int, double)}
   *        Contents are unchanged.
   * @param maxPeaks The maximum number of peaks to report
   * @param sortIndex The index of the result statistic to use for the peak sorting
   * @param centreMethod Define the method used to calculate the peak centre (use the constants with
   *        prefix {@code CENTRE_}, e.g. {@link #CENTRE_MAX_VALUE_SEARCH}
   * @param centreParameter Parameter for calculating the peak centre
   * @return the find foci results
   */
  public FindFociPrelimResults findMaximaPrelimResults(FindFociInitResults initResults,
      FindFociMergeResults mergeResults, int maxPeaks, int sortIndex, int centreMethod,
      double centreParameter);

  /**
   * This method is a stripped-down version of the
   * {@link #findMaxima(ImagePlus, ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int, double, int, double, double) }
   * routine. It does not support logging, interruption or mask generation. Only the result array is
   * generated. <p> This method is intended for staged processing.
   *
   * @param initResults The output from
   *        {@link #findMaximaInit(ImagePlus, ImagePlus, ImagePlus, int, String, int)}. Contents are
   *        destructively modified so should be cloned before input.
   * @param mergeResults The output from
   *        {@link #findMaximaMergeFinal(FindFociInitResults, FindFociMergeTempResults, int, int, double)}
   *        Contents are unchanged.
   * @param prelimResults The output from
   *        {@link #findMaximaPrelimResults(FindFociInitResults, FindFociMergeResults, int, int, int, double)}
   *        Contents are unchanged.
   * @param outputType Use {@link #OUTPUT_MASK_PEAKS} to get an ImagePlus in the results. Use
   *        {@link #OUTPUT_LOG_MESSAGES} to get runtime information.
   * @param autoThresholdMethod The thresholding method (use a string from
   *        {@link uk.ac.sussex.gdsc.core.threshold.AutoThreshold#getMethods() } )
   * @param imageTitle the image title
   * @param fractionParameter Used to specify the fraction of the peak to show in the mask
   * @return the find foci results
   */
  public FindFociResults findMaximaMaskResults(FindFociInitResults initResults,
      FindFociMergeResults mergeResults, FindFociPrelimResults prelimResults, int outputType,
      String autoThresholdMethod, String imageTitle, double fractionParameter);
}
