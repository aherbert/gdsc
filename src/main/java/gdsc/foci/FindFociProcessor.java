package gdsc.foci;

import ij.ImagePlus;

/*----------------------------------------------------------------------------- 
 * GDSC Plugins for ImageJ
 * 
 * Copyright (C) 2016 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

/**
 * Interface describing the methods for the FindFoci algorithm to find the peak intensity regions of an image.
 * <p>
 * Note this interface serves as the public documentation of the FindFoci algorithm.
 */
public interface FindFociProcessor
{
	/**
	 * The background intensity is set using the input value.
	 */
	public final static int BACKGROUND_ABSOLUTE = 0;
	/**
	 * The background intensity is set using the mean.
	 */
	public final static int BACKGROUND_MEAN = 1;
	/**
	 * The background intensity is set as the threshold value field times the standard deviation plus the mean.
	 */
	public final static int BACKGROUND_STD_DEV_ABOVE_MEAN = 2;
	/**
	 * The background intensity is set using the input auto-threshold method.
	 */
	public final static int BACKGROUND_AUTO_THRESHOLD = 3;
	/**
	 * The background intensity is set as the minimum image intensity within the ROI or mask.
	 */
	public final static int BACKGROUND_MIN_ROI = 4;
	/**
	 * The background intensity is set as 0. Equivalent to using {@link #BACKGROUND_ABSOLUTE} with a value of zero.
	 */
	public final static int BACKGROUND_NONE = 5;

	/**
	 * A region is grown until the intensity drops below the background.
	 */
	public final static int SEARCH_ABOVE_BACKGROUND = 0;
	/**
	 * A region is grown until the intensity drops to: background + (parameter value) * (peak intensity - background).
	 */
	public final static int SEARCH_FRACTION_OF_PEAK_MINUS_BACKGROUND = 1;
	/**
	 * A region is grown until the intensity drops to halfway between the value at the peak (the seed for the region)
	 * and the background level. This is equivalent to using the "fraction of peak - background" option with the
	 * threshold value set to 0.5.
	 */
	public final static int SEARCH_HALF_PEAK_VALUE = 2;

	/**
	 * The peak must be an absolute height above the highest saddle point.
	 */
	public final static int PEAK_ABSOLUTE = 0;
	/**
	 * The peak must be a relative height above the highest saddle point. The height is calculated as peak intensity *
	 * threshold value. The threshold value should be between 0 and 1.
	 */
	public final static int PEAK_RELATIVE = 1;
	/**
	 * The peak must be a relative height above the highest saddle point. The height is calculated as (peak intensity -
	 * background) * threshold value. The threshold value should be between 0 and 1.
	 */
	public final static int PEAK_RELATIVE_ABOVE_BACKGROUND = 2;

	/**
	 * Output the peak statistics to a results window
	 */
	public final static int OUTPUT_RESULTS_TABLE = 1;
	/**
	 * Create an output mask with values corresponding to the peak Ids
	 */
	public final static int OUTPUT_MASK_PEAKS = 2;
	/**
	 * Mark the peak locations on the input ImagePlus using point ROIs
	 */
	public final static int OUTPUT_ROI_SELECTION = 4;
	/**
	 * Output processing messages to the log window
	 */
	public final static int OUTPUT_LOG_MESSAGES = 8;
	/**
	 * Mark the peak locations on the mask ImagePlus using point ROIs
	 */
	public final static int OUTPUT_MASK_ROI_SELECTION = 16;
	/**
	 * Create an output mask with each peak region thresholded using the auto-threshold method
	 */
	public final static int OUTPUT_MASK_THRESHOLD = 32;
	/**
	 * Create an output mask showing only pixels above the peak's highest saddle value
	 */
	public final static int OUTPUT_MASK_ABOVE_SADDLE = 64;
	/**
	 * Do not mark the peaks location on the output mask using a single pixel dot.
	 * The pixel dot will use the brightest available value, which is the number of maxima + 1.
	 */
	public final static int OUTPUT_MASK_NO_PEAK_DOTS = 128;
	/**
	 * Create an output mask showing only the pixels contributing to a cumulative fraction of the peak's total intensity
	 */
	public final static int OUTPUT_MASK_FRACTION_OF_INTENSITY = 256;
	/**
	 * Create an output mask showing only pixels above a fraction of the peak's highest value
	 */
	public final static int OUTPUT_MASK_FRACTION_OF_HEIGHT = 512;
	/**
	 * Output the peak statistics to a results window
	 */
	public final static int OUTPUT_CLEAR_RESULTS_TABLE = 1024;
	/**
	 * When marking the peak locations on the input ImagePlus using point ROIs hide the number labels
	 */
	public final static int OUTPUT_HIDE_LABELS = 2048;
	/**
	 * Overlay the mask on the image
	 */
	public final static int OUTPUT_OVERLAY_MASK = 4096;
	/**
	 * Overlay the ROI points on the image (preserving any current ROI)
	 */
	public final static int OUTPUT_ROI_USING_OVERLAY = 8192;
	/**
	 * Create an output mask
	 */
	public final static int CREATE_OUTPUT_MASK = OUTPUT_MASK_PEAKS | OUTPUT_MASK_THRESHOLD | OUTPUT_OVERLAY_MASK;
	/**
	 * Show an output mask
	 */
	public final static int OUTPUT_MASK = OUTPUT_MASK_PEAKS | OUTPUT_MASK_THRESHOLD;

	/**
	 * The index of the minimum in the results statistics array
	 */
	public final static int STATS_MIN = 0;
	/**
	 * The index of the maximum in the results statistics array
	 */
	public final static int STATS_MAX = 1;
	/**
	 * The index of the mean in the results statistics array
	 */
	public final static int STATS_AV = 2;
	/**
	 * The index of the standard deviation in the results statistics array
	 */
	public final static int STATS_SD = 3;
	/**
	 * The index of the total image intensity in the results statistics array
	 */
	public final static int STATS_SUM = 4;
	/**
	 * The index of the image background level in the results statistics array (see {@link #BACKGROUND_AUTO_THRESHOLD})
	 */
	public final static int STATS_BACKGROUND = 5;
	/**
	 * The index of the total image intensity above the background in the results statistics array
	 */
	public final static int STATS_SUM_ABOVE_BACKGROUND = 6;
	/**
	 * The index of the minimum of the background region in the results statistics array
	 * 
	 * @see {@link #OPTION_STATS_INSIDE } and {@link #OPTION_STATS_OUTSIDE }
	 */
	public final static int STATS_MIN_BACKGROUND = 7;
	/**
	 * The index of the maximum of the background region in the results statistics array
	 * 
	 * @see {@link #OPTION_STATS_INSIDE } and {@link #OPTION_STATS_OUTSIDE }
	 */
	public final static int STATS_MAX_BACKGROUND = 8;
	/**
	 * The index of the mean of the background region in the results statistics array
	 * 
	 * @see {@link #OPTION_STATS_INSIDE } and {@link #OPTION_STATS_OUTSIDE }
	 */
	public final static int STATS_AV_BACKGROUND = 9;
	/**
	 * The index of the standard deviation of the background region in the results statistics array.
	 * 
	 * @see {@link #OPTION_STATS_INSIDE } and {@link #OPTION_STATS_OUTSIDE }
	 */
	public final static int STATS_SD_BACKGROUND = 10;
	/**
	 * The index of the total image intensity above the background in the results statistics array
	 */
	public final static int STATS_SUM_ABOVE_MIN_BACKGROUND = 11;

	/**
	 * The index of the peak X coordinate within the result int[] array of the results ArrayList<double[]> object
	 */
	public final static int RESULT_X = 0;
	/**
	 * The index of the peak Y coordinate within the result int[] array of the results ArrayList<double[]> object
	 */
	public final static int RESULT_Y = 1;
	/**
	 * The index of the peak Z coordinate within the result int[] array of the results ArrayList<double[]> object
	 */
	public final static int RESULT_Z = 2;
	/**
	 * The index of the internal ID used during the FindFoci routine within the result int[] array of the results
	 * ArrayList<double[]> object. This can be ignored.
	 */
	public final static int RESULT_PEAK_ID = 3;
	/**
	 * The index of the number of pixels in the peak within the result int[] array of the results ArrayList<double[]>
	 * object
	 */
	public final static int RESULT_COUNT = 4;
	/**
	 * The index of the sum of the peak intensity within the result int[] array of the results ArrayList
	 * <double[]> object
	 */
	public final static int RESULT_INTENSITY = 5;
	/**
	 * The index of the peak maximum value within the result int[] array of the results ArrayList<double[]> object
	 */
	public final static int RESULT_MAX_VALUE = 6;
	/**
	 * The index of the peak highest saddle point within the result int[] array of the results ArrayList
	 * <double[]> object
	 */
	public final static int RESULT_HIGHEST_SADDLE_VALUE = 7;
	/**
	 * The index of the peak highest saddle point within the result int[] array of the results ArrayList
	 * <double[]> object
	 */
	public final static int RESULT_SADDLE_NEIGHBOUR_ID = 8;
	/**
	 * The index of the average of the peak intensity within the result int[] array of the results ArrayList<double[]>
	 * object
	 */
	public final static int RESULT_AVERAGE_INTENSITY = 9;
	/**
	 * The index of the sum of the peak intensity above the background within the result int[] array of the results
	 * ArrayList<double[]> object
	 */
	public final static int RESULT_INTENSITY_MINUS_BACKGROUND = 10;
	/**
	 * The index of the average of the peak intensity above the background within the result int[] array of the results
	 * ArrayList<double[]> object
	 */
	public final static int RESULT_AVERAGE_INTENSITY_MINUS_BACKGROUND = 11;
	/**
	 * The index of the number of pixels in the peak above the highest saddle within the result int[] array of the
	 * results ArrayList<double[]> object
	 */
	public final static int RESULT_COUNT_ABOVE_SADDLE = 12;
	/**
	 * The index of the sum of the peak intensity above the highest saddle within the result int[] array of the results
	 * ArrayList<double[]> object
	 */
	public final static int RESULT_INTENSITY_ABOVE_SADDLE = 13;
	/**
	 * The index of the sum of the peak intensity above the minimum value of the analysed image within the result int[] array of the results
	 * ArrayList<double[]> object
	 */
	public final static int RESULT_INTENSITY_MINUS_MIN = 14;
	/**
	 * The index of the average of the peak intensity above the minimum value of the analysed image within the result int[] array of the results
	 * ArrayList<double[]> object
	 */
	public final static int RESULT_AVERAGE_INTENSITY_MINUS_MIN = 15;
	/**
	 * The index of the custom sort value within the result int[] array of the results ArrayList<int[]> object. This is
	 * used
	 * internally to sort the results using values not stored in the result array.
	 */
	final static int RESULT_CUSTOM_SORT_VALUE = 16;
	/**
	 * The index of the state (i.e. pixel value) from the mask image within the result int[] array of the results
	 * ArrayList<double[]> object
	 */
	final static int RESULT_STATE = 17;
	/**
	 * The index of the allocated object from the mask image within the result int[] array of the results
	 * ArrayList<double[]> object
	 */
	final static int RESULT_OBJECT = 18;

	// The length of the result array
	final static int RESULT_LENGTH = 19;

	/**
	 * Sort the peaks using the pixel count
	 */
	public final static int SORT_COUNT = 0;
	/**
	 * Sort the peaks using the sum of pixel intensity
	 */
	public final static int SORT_INTENSITY = 1;
	/**
	 * Sort the peaks using the maximum pixel value
	 */
	public final static int SORT_MAX_VALUE = 2;
	/**
	 * Sort the peaks using the average pixel value
	 */
	public final static int SORT_AVERAGE_INTENSITY = 3;
	/**
	 * Sort the peaks using the sum of pixel intensity (minus the background)
	 */
	public final static int SORT_INTENSITY_MINUS_BACKGROUND = 4;
	/**
	 * Sort the peaks using the average pixel value (minus the background)
	 */
	public final static int SORT_AVERAGE_INTENSITY_MINUS_BACKGROUND = 5;
	/**
	 * Sort the peaks using the X coordinate
	 */
	public final static int SORT_X = 6;
	/**
	 * Sort the peaks using the Y coordinate
	 */
	public final static int SORT_Y = 7;
	/**
	 * Sort the peaks using the Z coordinate
	 */
	public final static int SORT_Z = 8;
	/**
	 * Sort the peaks using the saddle height
	 */
	public final static int SORT_SADDLE_HEIGHT = 9;
	/**
	 * Sort the peaks using the pixel count above the saddle height
	 */
	public final static int SORT_COUNT_ABOVE_SADDLE = 10;
	/**
	 * Sort the peaks using the sum of pixel intensity above the saddle height
	 */
	public final static int SORT_INTENSITY_ABOVE_SADDLE = 11;
	/**
	 * Sort the peaks using the absolute height above the highest saddle
	 */
	public final static int SORT_ABSOLUTE_HEIGHT = 12;
	/**
	 * Sort the peaks using the relative height above the background
	 */
	public final static int SORT_RELATIVE_HEIGHT_ABOVE_BACKGROUND = 13;
	/**
	 * Sort the peaks using the peak Id
	 */
	public final static int SORT_PEAK_ID = 14;
	/**
	 * Sort the peaks using the XYZ coordinates (in order)
	 */
	public final static int SORT_XYZ = 15;
	/**
	 * Sort the peaks using the sum of pixel intensity (minus the minimum in the analysed region)
	 */
	public final static int SORT_INTENSITY_MINUS_MIN = 16;
	/**
	 * Sort the peaks using the average pixel value (minus the minimum in the analysed region)
	 */
	public final static int SORT_AVERAGE_INTENSITY_MINUS_MIN = 17;

	/**
	 * Apply the minimum size criteria to the peak size above the highest saddle point
	 */
	public final static int OPTION_MINIMUM_ABOVE_SADDLE = 1;
	/**
	 * Calculate the statistics using the pixels outside the ROI/Mask (default is all pixels)
	 */
	public final static int OPTION_STATS_OUTSIDE = 2;
	/**
	 * Calculate the statistics using the pixels inside the ROI/Mask (default is all pixels)
	 */
	public final static int OPTION_STATS_INSIDE = 4;
	/**
	 * Remove any maxima that touch the edge of the image
	 */
	public final static int OPTION_REMOVE_EDGE_MAXIMA = 8;
	/**
	 * Identify all connected non-zero mask pixels with the same value as objects and label the maxima
	 * as belonging to each object
	 */
	public final static int OPTION_OBJECT_ANALYSIS = 16;
	/**
	 * Show the object mask calculated during the object analysis
	 */
	public final static int OPTION_SHOW_OBJECT_MASK = 32;
	/**
	 * Save the results to memory (allows other plugins to obtain the results)
	 */
	public final static int OPTION_SAVE_TO_MEMORY = 64;

	/**
	 * Define the peak centre using the highest pixel value of the search image (default). In the case of multiple
	 * highest value pixels, the closest pixel to the geometric mean of their coordinates is used.
	 */
	public final static int CENTRE_MAX_VALUE_SEARCH = 0;
	/**
	 * Re-map peak centre using the highest pixel value of the original image.
	 */
	public final static int CENTRE_MAX_VALUE_ORIGINAL = 1;
	/**
	 * Re-map peak centre using the peak centre of mass (COM) around the search image. The COM is computed within a
	 * given volume of the highest pixel value. Only pixels above the saddle height are used to compute the fit.
	 * The volume is specified using 2xN+1 where N is the centre parameter.
	 */
	public final static int CENTRE_OF_MASS_SEARCH = 2;
	/**
	 * Re-map peak centre using the peak centre of mass (COM) around the original image.
	 */
	public final static int CENTRE_OF_MASS_ORIGINAL = 3;
	/**
	 * Re-map peak centre using a Gaussian fit on the search image. Only pixels above the saddle height are used to
	 * compute the fit. The fit is performed in 2D using a projection along the z-axis. If the centre parameter is 1 a
	 * maximum intensity projection is used; else an average intensity project is used. The z-coordinate is computed
	 * using the centre of mass along the projection axis located at the xy centre.
	 */
	public final static int CENTRE_GAUSSIAN_SEARCH = 4;
	/**
	 * Re-map peak centre using a Gaussian fit on the original image.
	 */
	public final static int CENTRE_GAUSSIAN_ORIGINAL = 5;
	
	/**
	 * Here the processing is done: Find the maxima of an image.
	 * 
	 * <P>
	 * Local maxima are processed in order, highest first. Regions are grown from local maxima until a saddle point is
	 * found or the stopping criteria are met (based on pixel intensity). If a peak does not meet the peak criteria (min
	 * size) it is absorbed into the highest peak that touches it (if a neighbour peak exists). Only a single iteration
	 * is performed and consequently peak absorption could produce sub-optimal results due to greedy peak growth.
	 * 
	 * <P>
	 * Peak expansion stopping criteria are defined using the method parameter. See {@link #SEARCH_ABOVE_BACKGROUND};
	 * {@link #SEARCH_FRACTION_OF_PEAK_MINUS_BACKGROUND}; {@link #SEARCH_HALF_PEAK_VALUE};
	 * {@link #SEARCH_STD_DEV_FROM_BACKGROUND}
	 *
	 * @param imp
	 *            the image
	 * @param mask
	 *            A mask image used to define the region to search for peaks
	 * @param backgroundMethod
	 *            Method for calculating the background level (use the constants with prefix FindFoci.BACKGROUND_)
	 * @param backgroundParameter
	 *            parameter for calculating the background level
	 * @param autoThresholdMethod
	 *            The thresholding method (use a string from {@link #autoThresholdMethods } )
	 * @param searchMethod
	 *            Method for calculating the region growing stopping criteria (use the constants with prefix SEARCH_)
	 * @param searchParameter
	 *            parameter for calculating the stopping criteria
	 * @param maxPeaks
	 *            The maximum number of peaks to report
	 * @param minSize
	 *            The minimum size for a peak
	 * @param peakMethod
	 *            Method for calculating the minimum peak height above the highest saddle (use the constants with prefix
	 *            PEAK_)
	 * @param peakParameter
	 *            parameter for calculating the minimum peak height
	 * @param outputType
	 *            Use {@link #FindFoci.OUTPUT_MASK_PEAKS} to get an ImagePlus in the result Object array. Use
	 *            {@link #FindFoci.OUTPUT_LOG_MESSAGES} to get runtime information.
	 * @param sortIndex
	 *            The index of the result statistic to use for the peak sorting
	 * @param options
	 *            An options flag (use the constants with prefix FindFoci.OPTION_)
	 * @param blur
	 *            Apply a Gaussian blur of the specified radius before processing (helps smooth noisy images for better
	 *            peak identification)
	 * @param centreMethod
	 *            Define the method used to calculate the peak centre (use the constants with prefix
	 *            FindFoci.FindFoci.CENTRE_)
	 * @param centreParameter
	 *            Parameter for calculating the peak centre
	 * @param fractionParameter
	 *            Used to specify the fraction of the peak to show in the mask
	 * @return Result containing: (1) a new ImagePlus (with a stack) where the maxima are set to nMaxima+1 and
	 *         peak areas numbered starting from nMaxima (Background 0). Pixels outside of the roi of the input ip are
	 *         not set. Alternatively the peak areas can be thresholded using the auto-threshold method and coloured
	 *         1(saddle), 2(background), 3(threshold), 4(peak); (2) a result ArrayList<double[]> with details of the
	 *         maxima. The details can be extracted for each result using the constants defined with the prefix
	 *         FindFoci.RESULT_;
	 *         (3) the image statistics double[] array. The details can be extracted using the constants defined with
	 *         the FindFoci.STATS_ prefix. Returns null if cancelled by escape.
	 */
	FindFociResult findMaxima(ImagePlus imp, ImagePlus mask, int backgroundMethod, double backgroundParameter,
			String autoThresholdMethod, int searchMethod, double searchParameter, int maxPeaks, int minSize,
			int peakMethod, double peakParameter, int outputType, int sortIndex, int options, double blur,
			int centreMethod, double centreParameter, double fractionParameter);

	/**
	 * This method is a stripped-down version of the
	 * {@link #findMaxima(ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int, double, int, double)}
	 * routine.
	 * It does not support logging, interruption or mask generation. The method initialises the system up to the point
	 * of background generation. The result object can be cloned and passed multiple times to the
	 * {@link #findMaximaRun(Object[], int, double, int, double, int, int, int, double, int, int, double, int, double)}
	 * method.
	 * 
	 * <p>
	 * This method is intended for benchmarking.
	 * 
	 * @return Object array containing: (1) Object - image pixels; (2) byte[] - types array; (3) short[] - maxima array;
	 *         (4)
	 *         Histogram - image histogram; (5) double[] - image statistics; (6) Object - the original image pixels; (7)
	 *         ImagePlus -
	 *         the original image
	 */
	Object[] findMaximaInit(ImagePlus originalImp, ImagePlus imp, ImagePlus mask, int backgroundMethod,
			String autoThresholdMethod, int options);

	/**
	 * Clones the init array for use in
	 * {@link #findMaximaRun(Object[], int, double, int, double, int, int, int, double, int, int) }.
	 * Only the elements that are destructively modified by findMaximaRun are duplicated. The rest are shallow copied.
	 * 
	 * @param initArray
	 *            The original init array
	 * @param clonedInitArray
	 *            A previously cloned init array (avoid reallocating memory). Can be null.
	 * @return The cloned array
	 */
	Object[] cloneInitArray(Object[] initArray, Object[] clonedInitArray);

	/**
	 * Clones the init array for use in findMaxima staged methods.
	 * Only the elements that are destructively modified by findMaximaRun are duplicated. The rest are shallow copied.
	 * 
	 * @param initArray
	 *            The original init array
	 * @param clonedInitArray
	 *            A previously cloned init array (avoid reallocating memory). Can be null.
	 * @return The cloned array
	 */
	Object[] cloneResultsArray(Object[] initArray, Object[] clonedInitArray);

	/**
	 * This method is a stripped-down version of the
	 * {@link #findMaxima(ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int, double)}
	 * routine.
	 * It does not support logging, interruption or mask generation. Only the result array is generated.
	 * 
	 * <p>
	 * The method must be called with the output from
	 * {@link #findMaximaInit(ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int)}
	 * 
	 * <p>
	 * This method is intended for benchmarking.
	 * 
	 * @param initArray
	 *            The output from
	 *            {@link #findMaximaInit(ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int)}
	 * @return Object array containing: (1) a result ArrayList<double[]> with details of the
	 *         maxima. The details can be extracted for each result using the constants defined with the prefix
	 *         FindFoci.RESULT_;
	 *         (2) Integer with the original number of peaks before merging.
	 */
	Object[] findMaximaRun(Object[] initArray, int backgroundMethod, double backgroundParameter, int searchMethod,
			double searchParameter, int minSize, int peakMethod, double peakParameter, int sortIndex, int options,
			double blur);

	/**
	 * This method is a stripped-down version of the
	 * {@link #findMaxima(ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int, double)}
	 * routine.
	 * It does not support logging, interruption or mask generation. Only the result array is generated.
	 * 
	 * <p>
	 * The method must be called with the output from
	 * {@link #findMaximaInit(ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int)}
	 * 
	 * <p>
	 * This method is intended for benchmarking.
	 * 
	 * @param initArray
	 *            The output from
	 *            {@link #findMaximaInit(ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int)}
	 *            Contents are destructively modified so should be cloned before input.
	 * @return Object array containing: (1) a result ArrayList<double[]> with
	 *         details of the
	 *         maxima. The details can be extracted for each result using the constants defined with the prefix
	 *         FindFoci.RESULT_;
	 *         (2) ArrayList<LinkedList<double[]>> the saddle points
	 */
	Object[] findMaximaSearch(Object[] initArray, int backgroundMethod, double backgroundParameter, int searchMethod,
			double searchParameter);

	/**
	 * This method is a stripped-down version of the
	 * {@link #findMaxima(ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int, double)}
	 * routine.
	 * It does not support logging, interruption or mask generation. Only the result array is generated.
	 * 
	 * <p>
	 * The method must be called with the output from
	 * {@link #findMaximaInit(ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int)}
	 * 
	 * <p>
	 * This method is intended for benchmarking.
	 * 
	 * @param initArray
	 *            The output from
	 *            {@link #findMaximaInit(ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int)}
	 *            .
	 *            Contents are destructively modified so should be cloned before input.
	 * @param searchArray
	 *            The output from {@link #findMaximaSearch(Object[], int, double, int, double)}.
	 *            Contents are unchanged.
	 * @return Object array containing: (1) a result ArrayList<double[]> with
	 *         details of the
	 *         maxima. The details can be extracted for each result using the constants defined with the prefix
	 *         FindFoci.RESULT_;
	 *         (2) Integer - the original number of peaks before merging.
	 */
	Object[] findMaximaMerge(Object[] initArray, Object[] searchArray, int minSize, int peakMethod,
			double peakParameter, int options, double blur);

	/**
	 * This method is a stripped-down version of the
	 * {@link #findMaxima(ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int, double)}
	 * routine.
	 * It does not support logging, interruption or mask generation. Only the result array is generated.
	 * 
	 * <p>
	 * The method must be called with the output from
	 * {@link #findMaximaInit(ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int)}
	 * 
	 * <p>
	 * This method is intended for benchmarking.
	 * 
	 * @param initArray
	 *            The output from
	 *            {@link #findMaximaInit(ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int)}
	 *            .
	 *            Contents are destructively modified so should be cloned before input.
	 * @param runArray
	 *            The output from
	 *            {@link #findMaximaRun(Object[], int, double, int, double, int, int, double, int, int, double)}.
	 *            Contents are unchanged.
	 * @return Result containing: (1) null (this option is not supported); (2) a result ArrayList<double[]> with
	 *         details of the
	 *         maxima. The details can be extracted for each result using the constants defined with the prefix
	 *         FindFoci.RESULT_;
	 *         (3) the image statistics double[] array. The details can be extracted using the constants defined with
	 *         the FindFoci.STATS_ prefix.
	 */
	FindFociResult findMaximaResults(Object[] initArray, Object[] runArray, int maxPeaks, int sortIndex,
			int centreMethod, double centreParameter);

	/**
	 * This method is a stripped-down version of the
	 * {@link #findMaxima(ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int, double)}
	 * routine.
	 * It does not support logging, interruption or mask generation. Only the result array is generated.
	 * 
	 * <p>
	 * The method must be called with the output from
	 * {@link #findMaximaInit(ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int)}
	 * 
	 * <p>
	 * This method is intended for staged processing.
	 * 
	 * @param initArray
	 *            The output from
	 *            {@link #findMaximaInit(ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int)}
	 *            .
	 *            Contents are destructively modified so should be cloned before input.
	 * @param mergeArray
	 *            The output from {@link #findMaximaMerge(Object[], Object[], int, int, double, int, double)}.
	 *            Contents are unchanged.
	 * @return Result containing: (1) null (this option is not supported); (2) a result ArrayList<double[]> with
	 *         details of the
	 *         maxima. The details can be extracted for each result using the constants defined with the prefix
	 *         FindFoci.RESULT_;
	 *         (3) the image statistics double[] array. The details can be extracted using the constants defined with
	 *         the FindFoci.STATS_ prefix.
	 */
	FindFociResult findMaximaPrelimResults(Object[] initArray, Object[] mergeArray, int maxPeaks, int sortIndex,
			int centreMethod, double centreParameter);

	/**
	 * This method is a stripped-down version of the
	 * {@link #findMaxima(ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int, double)}
	 * routine.
	 * It does not support logging or interruption.
	 * 
	 * <p>
	 * The method must be called with the output from
	 * {@link #findMaximaPrelimResults(Object[], Object[], int, int, int, double)}
	 * 
	 * <p>
	 * This method is intended for staged processing.
	 * 
	 * @param initArray
	 *            The output from
	 *            {@link #findMaximaInit(ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int)}
	 *            .
	 *            Contents are destructively modified so should be cloned before input.
	 * @param mergeArray
	 *            The output from {@link #findMaximaMerge(Object[], Object[], int, int, double, int, double)}.
	 *            Contents are unchanged.
	 * @param prelimResults
	 *            The output from {@link #findMaximaPrelimResults(Object[], Object[], int, int, int, double)}.
	 *            Contents are unchanged.
	 * @param imageTitle
	 * @param autoThresholdMethod
	 * @param fractionParameter
	 *            The height of the peak to show in the mask
	 * @return Result containing: (1) a new ImagePlus (with a stack) where the maxima are set to nMaxima+1 and
	 *         peak areas numbered starting from nMaxima (Background 0). Pixels outside of the roi of the input ip are
	 *         not set. Alternatively the peak areas can be thresholded using the auto-threshold method and coloured
	 *         1(saddle), 2(background), 3(threshold), 4(peak); (2) a result ArrayList<double[]> with details of the
	 *         maxima. The details can be extracted for each result using the constants defined with the prefix
	 *         FindFoci.RESULT_;
	 *         (3) the image statistics double[] array. The details can be extracted using the constants defined with
	 *         the FindFoci.STATS_ prefix. Returns null if cancelled by escape.
	 */
	FindFociResult findMaximaMaskResults(Object[] initArray, Object[] mergeArray, FindFociResult prelimResults,
			int outputType, String autoThresholdMethod, String imageTitle, double fractionParameter);
}
