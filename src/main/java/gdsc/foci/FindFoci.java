package gdsc.foci;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.TextField;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.RoundRectangle2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Vector;

import gdsc.UsageTracker;
import gdsc.core.ij.Utils;
import gdsc.core.threshold.AutoThreshold;

/*----------------------------------------------------------------------------- 
 * GDSC Plugins for ImageJ
 * 
 * Copyright (C) 2011 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

import gdsc.foci.model.FindFociModel;
import gdsc.threshold.Multi_OtsuThreshold;
import gdsc.utils.GaussianFit;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Macro;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.ImageRoi;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.PointRoi2;
import ij.gui.Roi;
import ij.io.Opener;
import ij.io.RoiEncoder;
import ij.plugin.FolderOpener;
import ij.plugin.PlugIn;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.frame.Recorder;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

/**
 * Find the peak intensity regions of an image.
 * 
 * <P>
 * All local maxima above threshold are identified. For all other pixels the direction to the highest neighbour pixel is
 * stored (steepest gradient). In order of highest local maxima, regions are only grown down the steepest gradient to a
 * lower pixel. Provides many configuration options for regions growing thresholds.
 * 
 * <P>
 * This plugin was based on {@link ij.plugin.filter.MaximumFinder}. Options have been changed to only support greyscale
 * 2D images and 3D stacks and to perform region growing using configurable thresholds. Support for Watershed,
 * Previewing, and Euclidian Distance Map (EDM) have been removed.
 * 
 * <P>
 * Stopping criteria for region growing routines are partly based on the options in PRIISM
 * (http://www.msg.ucsf.edu/IVE/index.html).
 */
public class FindFoci implements PlugIn, MouseListener
{
	private class BatchParameters
	{
		String parameterOptions;
		HashMap<String, String> map;

		int backgroundMethod;
		double backgroundParameter;
		String autoThresholdMethod;
		int searchMethod;
		double searchParameter;
		int maxPeaks;
		int minSize;
		int peakMethod;
		double peakParameter;
		int sortIndex;
		double blur;
		int centreMethod;
		double centreParameter;
		double fractionParameter;
		boolean objectAnalysis;
		boolean showObjectMask;
		boolean saveToMemory;

		int outputType;
		int options;

		static final int C = 0;
		static final int Z = 1;
		static final int T = 2;
		int[] image = new int[3];
		int[] mask = new int[3];
		boolean originalTitle;

		public BatchParameters(String filename) throws Exception
		{
			readParameters(filename);

			// Read all the parameters
			backgroundMethod = findIndex("Background_method", backgroundMethods);
			backgroundParameter = findDouble("Background_parameter");
			autoThresholdMethod = findString("Auto_threshold");
			String statisticsMode = findString("Statistics_mode");
			searchMethod = findIndex("Search_method", searchMethods);
			searchParameter = findDouble("Search_parameter");
			minSize = findInteger("Minimum_size");
			boolean minimumAboveSaddle = findBoolean("Minimum_above_saddle");
			peakMethod = findIndex("Minimum_peak_height", peakMethods);
			peakParameter = findDouble("Peak_parameter");
			sortIndex = findIndex("Sort_method", sortIndexMethods);
			maxPeaks = findInteger("Maximum_peaks");
			int showMask = findIndex("Show_mask", maskOptions);
			fractionParameter = findDouble("Fraction_parameter");
			boolean markMaxima = findBoolean("Mark_maxima");
			boolean markROIMaxima = findBoolean("Mark_peak_maxima");
			boolean markUsingOverlay = findBoolean("Mark_using_overlay");
			boolean hideLabels = findBoolean("Hide_labels");
			boolean showMaskMaximaAsDots = findBoolean("Show_peak_maxima_as_dots");
			boolean showLogMessages = findBoolean("Show_log_messages");
			boolean removeEdgeMaxima = findBoolean("Remove_edge_maxima");
			blur = findDouble("Gaussian_blur");
			centreMethod = findIndex("Centre_method", centreMethods);
			centreParameter = findDouble("Centre_parameter");
			objectAnalysis = findBoolean("Object_analysis");
			showObjectMask = findBoolean("Show_object_mask");
			saveToMemory = findBoolean("Save_to_memory");

			image[C] = findInteger("Image_C", 1);
			image[Z] = findInteger("Image_Z", 0);
			image[T] = findInteger("Image_T", 1);
			mask[C] = findInteger("Mask_C", 1);
			mask[Z] = findInteger("Mask_Z", 0);
			mask[T] = findInteger("Mask_T", 1);
			originalTitle = findBoolean("Original_Title");

			outputType = getOutputMaskFlags(showMask);

			if (markMaxima)
				outputType += OUTPUT_ROI_SELECTION;
			if (markROIMaxima)
				outputType += OUTPUT_MASK_ROI_SELECTION;
			if (markUsingOverlay)
				outputType += OUTPUT_ROI_USING_OVERLAY;
			if (hideLabels)
				outputType += OUTPUT_HIDE_LABELS;
			if (!showMaskMaximaAsDots)
				outputType += OUTPUT_MASK_NO_PEAK_DOTS;
			if (showLogMessages)
				outputType += OUTPUT_LOG_MESSAGES;

			options = 0;
			if (minimumAboveSaddle)
				options |= OPTION_MINIMUM_ABOVE_SADDLE;
			if (statisticsMode.equalsIgnoreCase("inside"))
				options |= OPTION_STATS_INSIDE;
			else if (statisticsMode.equalsIgnoreCase("outside"))
				options |= OPTION_STATS_OUTSIDE;
			if (removeEdgeMaxima)
				options |= OPTION_REMOVE_EDGE_MAXIMA;
			if (objectAnalysis)
			{
				options |= OPTION_OBJECT_ANALYSIS;
				if (showObjectMask)
					options |= OPTION_SHOW_OBJECT_MASK;
			}
			if (saveToMemory)
				options |= OPTION_SAVE_TO_MEMORY;
		}

		private void readParameters(String filename) throws IOException
		{
			ArrayList<String> parameters = new ArrayList<String>();
			BufferedReader input = null;
			try
			{
				FileInputStream fis = new FileInputStream(filename);
				input = new BufferedReader(new InputStreamReader(fis));

				String line;
				while ((line = input.readLine()) != null)
				{
					// Only use lines that have key-value pairs
					if (line.contains("="))
						parameters.add(line);
				}
			}
			finally
			{
				try
				{
					if (input != null)
						input.close();
				}
				catch (IOException e)
				{
					// Ignore
				}
			}
			if (parameters.isEmpty())
				throw new RuntimeException("No key=value parameters in the file");
			// Check if the parameters are macro options
			if (parameters.size() == 1)
			{
				parameterOptions = parameters.get(0) + " ";
			}
			else
			{
				// Store key-value pairs. Lower-case the key
				map = new HashMap<String, String>(parameters.size());
				for (String line : parameters)
				{
					int index = line.indexOf('=');
					String key = line.substring(0, index).trim().toLowerCase();
					String value = line.substring(index + 1).trim();
					map.put(key, value);
				}
			}
		}

		private String findString(String key)
		{
			String value;
			key = key.toLowerCase();
			if (parameterOptions != null)
				value = Macro.getValue(parameterOptions, key, "");
			else
			{
				value = map.get(key);
			}
			if (value == null || value.length() == 0)
				throw new RuntimeException("Missing parameter: " + key);
			return value;
		}

		private String findString(String key, String defaultValue)
		{
			String value;
			key = key.toLowerCase();
			if (parameterOptions != null)
				value = Macro.getValue(parameterOptions, key, "");
			else
			{
				value = map.get(key);
			}
			if (value == null || value.length() == 0)
				return defaultValue;
			return value;
		}

		private double findDouble(String key)
		{
			return Double.parseDouble(findString(key));
		}

		@SuppressWarnings("unused")
		private double findDouble(String key, double defaultValue)
		{
			String value = findString(key, null);
			if (value == null)
				return defaultValue;
			// Still error if the key value is not a double
			return Double.parseDouble(value);
		}

		private int findInteger(String key)
		{
			return Integer.parseInt(findString(key));
		}

		private int findInteger(String key, int defaultValue)
		{
			String value = findString(key, null);
			if (value == null)
				return defaultValue;
			// Still error if the key value is not an int
			return Integer.parseInt(value);
		}

		private boolean findBoolean(String key)
		{
			if (parameterOptions != null)
			{
				return isMatch(parameterOptions, key.toLowerCase() + " ");
			}
			else
			{
				try
				{
					return Boolean.parseBoolean(findString(key));
				}
				catch (RuntimeException e)
				{
					return false;
				}
			}
		}

		/**
		 * Returns true if s2 is in s1 and not in a bracketed literal (e.g., "[literal]")
		 * <p>
		 * Copied from ij.gui.GenericDialog since the recorder options do not show key=value pairs for booleans
		 * 
		 * @param s1
		 * @param s2
		 * @return
		 */
		boolean isMatch(String s1, String s2)
		{
			if (s1.startsWith(s2))
				return true;
			s2 = " " + s2;
			int len1 = s1.length();
			int len2 = s2.length();
			boolean match, inLiteral = false;
			char c;
			for (int i = 0; i < len1 - len2 + 1; i++)
			{
				c = s1.charAt(i);
				if (inLiteral && c == ']')
					inLiteral = false;
				else if (c == '[')
					inLiteral = true;
				if (c != s2.charAt(0) || inLiteral || (i > 1 && s1.charAt(i - 1) == '='))
					continue;
				match = true;
				for (int j = 0; j < len2; j++)
				{
					if (s2.charAt(j) != s1.charAt(i + j))
					{
						match = false;
						break;
					}
				}
				if (match)
					return true;
			}
			return false;
		}

		private int findIndex(String key, String[] options)
		{
			String value = findString(key);
			for (int i = 0; i < options.length; i++)
				if (options[i].equalsIgnoreCase(value))
					return i;
			throw new RuntimeException("Missing index for option: " + key + "=" + value);
		}
	}

	/**
	 * Used for sorting the results by z-slice
	 *
	 */
	private class XYZ implements Comparable<XYZ>
	{
		private int id, x, y, z;

		public XYZ(int id, int x, int y, int z)
		{
			this.id = id;
			this.x = x;
			this.y = y;
			this.z = z;
		}

		public int compareTo(XYZ that)
		{
			return this.z - that.z;
		}
	}

	public static final String TITLE = "FindFoci";

	private static TextWindow resultsWindow = null;

	// Used to buffer the results to the TextWindow
	private StringBuilder resultsBuffer = new StringBuilder();
	private int resultsCount = 0;
	private int flushCount = 0;

	private static ArrayList<int[]> lastResultsArray = null;
	private static int isGaussianFitEnabled = 0;
	private static String newLine = System.getProperty("line.separator");

	private static String BATCH_INPUT_DIRECTORY = "findfoci.batchInputDirectory";
	private static String BATCH_MASK_DIRECTORY = "findfoci.batchMaskDirectory";
	private static String BATCH_PARAMETER_FILE = "findfoci.batchParameterFile";
	private static String BATCH_OUTPUT_DIRECTORY = "findfoci.batchOutputDirectory";
	private static String SEARCH_CAPACITY = "findfoci.searchCapacity";
	private static String EMPTY_FIELD = "findfoci.emptyField";
	private static String batchInputDirectory = Prefs.get(BATCH_INPUT_DIRECTORY, "");
	private static String batchMaskDirectory = Prefs.get(BATCH_MASK_DIRECTORY, "");
	private static String batchParameterFile = Prefs.get(BATCH_PARAMETER_FILE, "");
	private static String batchOutputDirectory = Prefs.get(BATCH_OUTPUT_DIRECTORY, "");
	private static int searchCapacity = (int) Prefs.get(SEARCH_CAPACITY, Short.MAX_VALUE);
	private static String emptyField = Prefs.get(EMPTY_FIELD, "");
	/**
	 * The largest number that can be displayed in a 16-bit image.
	 * <p>
	 * Note searching for maxima uses 32-bit integers but ImageJ can only display 16-bit images.
	 */
	private static final int MAXIMA_CAPCITY = 65535;
	private TextField textParamFile;

	/**
	 * List of background threshold methods for the dialog
	 */
	public final static String[] backgroundMethods = { "Absolute", "Mean", "Std.Dev above mean", "Auto threshold",
			"Min Mask/ROI", "None" };

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
	 * List of search methods for the dialog
	 */
	public final static String[] searchMethods = { "Above background", "Fraction of peak - background",
			"Half peak value" };

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
	 * List of peak height methods for the dialog
	 */
	public final static String[] peakMethods = { "Absolute height", "Relative height", "Relative above background" };

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
	 * List of peak height methods for the dialog
	 */
	public final static String[] maskOptions = { "(None)", "Peaks", "Threshold", "Peaks above saddle",
			"Threshold above saddle", "Fraction of intensity", "Fraction height above background" };

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
	 * The index of the peak X coordinate within the result int[] array of the results ArrayList<int[]> object
	 */
	public final static int RESULT_X = 0;
	/**
	 * The index of the peak Y coordinate within the result int[] array of the results ArrayList<int[]> object
	 */
	public final static int RESULT_Y = 1;
	/**
	 * The index of the peak Z coordinate within the result int[] array of the results ArrayList<int[]> object
	 */
	public final static int RESULT_Z = 2;
	/**
	 * The index of the internal ID used during the FindFoci routine within the result int[] array of the results
	 * ArrayList<int[]> object. This can be ignored.
	 */
	public final static int RESULT_PEAK_ID = 3;
	/**
	 * The index of the number of pixels in the peak within the result int[] array of the results ArrayList<int[]>
	 * object
	 */
	public final static int RESULT_COUNT = 4;
	/**
	 * The index of the sum of the peak intensity within the result int[] array of the results ArrayList<int[]> object
	 */
	public final static int RESULT_INTENSITY = 5;
	/**
	 * The index of the peak maximum value within the result int[] array of the results ArrayList<int[]> object
	 */
	public final static int RESULT_MAX_VALUE = 6;
	/**
	 * The index of the peak highest saddle point within the result int[] array of the results ArrayList<int[]> object
	 */
	public final static int RESULT_HIGHEST_SADDLE_VALUE = 7;
	/**
	 * The index of the peak highest saddle point within the result int[] array of the results ArrayList<int[]> object
	 */
	public final static int RESULT_SADDLE_NEIGHBOUR_ID = 8;
	/**
	 * The index of the average of the peak intensity within the result int[] array of the results ArrayList<int[]>
	 * object
	 */
	public final static int RESULT_AVERAGE_INTENSITY = 9;
	/**
	 * The index of the sum of the peak intensity above the background within the result int[] array of the results
	 * ArrayList<int[]> object
	 */
	public final static int RESULT_INTENSITY_MINUS_BACKGROUND = 10;
	/**
	 * The index of the sum of the peak intensity within the result int[] array of the results ArrayList<int[]> object
	 */
	public final static int RESULT_AVERAGE_INTENSITY_MINUS_BACKGROUND = 11;
	/**
	 * The index of the number of pixels in the peak above the highest saddle within the result int[] array of the
	 * results ArrayList<int[]> object
	 */
	public final static int RESULT_COUNT_ABOVE_SADDLE = 12;
	/**
	 * The index of the sum of the peak intensity above the highest saddle within the result int[] array of the results
	 * ArrayList<int[]> object
	 */
	public final static int RESULT_INTENSITY_ABOVE_SADDLE = 13;
	/**
	 * The index of the custom sort value within the result int[] array of the results ArrayList<int[]> object. This is
	 * used
	 * internally to sort the results using values not stored in the result array.
	 */
	private final static int RESULT_CUSTOM_SORT_VALUE = 14;
	/**
	 * The index of the state (i.e. pixel value) from the mask image within the result int[] array of the results
	 * ArrayList<int[]> object
	 */
	private final static int RESULT_STATE = 15;
	/**
	 * The index of the allocated object from the mask image within the result int[] array of the results
	 * ArrayList<int[]> object
	 */
	private final static int RESULT_OBJECT = 16;

	// The length of the result array
	private final static int RESULT_LENGTH = 17;

	// The indices used within the saddle array
	private final static int SADDLE_PEAK_ID = 0;
	private final static int SADDLE_VALUE = 1;

	/**
	 * The list of recognised methods for sorting the results
	 */
	public final static String[] sortIndexMethods = { "Size", "Total intensity", "Max value", "Average intensity",
			"Total intensity minus background", "Average intensity minus background", "X", "Y", "Z", "Saddle height",
			"Size above saddle", "Intensity above saddle", "Absolute height", "Relative height >Bg", "Peak ID", "XYZ" };

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
	private final static int SORT_PEAK_ID = 14;
	/**
	 * Sort the peaks using the XYZ coordinates (in order)
	 */
	public final static int SORT_XYZ = 15;

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

	private static String[] centreMethods = { "Max value (search image)", "Max value (original image)",
			"Centre of mass (search image)", "Centre of mass (original image)", "Gaussian (search image)",
			"Gaussian (original image)" };

	static
	{
		GaussianFit gf = new GaussianFit();
		isGaussianFitEnabled = (gf.isFittingEnabled()) ? 1 : -1;
		if (!gf.isFittingEnabled())
		{
			centreMethods = Arrays.copyOf(centreMethods, centreMethods.length - 2);

			// Debug the reason why fitting is disabled
			if (IJ.shiftKeyDown())
				IJ.log("Gaussian fitting is not enabled:" + newLine + gf.getErrorMessage());
		}
	}

	/**
	 * List of methods for defining the centre of each peak
	 */
	public static String[] getCentreMethods()
	{
		return centreMethods;
	};

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
	 * The list of recognised methods for auto-thresholding
	 */
	public final static String[] autoThresholdMethods;
	static
	{
		ArrayList<String> m = new ArrayList<String>();
		for (String name : AutoThreshold.getMethods(true))
			m.add(name);
		// Add multi-level Otsu thresholding
		m.add(AutoThreshold.Method.OTSU.name + "_3_Level");
		m.add(AutoThreshold.Method.OTSU.name + "_4_Level");
		Collections.sort(m);
		autoThresholdMethods = m.toArray(new String[m.size()]);
	}
	public final static String[] statisticsModes = { "Both", "Inside", "Outside" };

	private static String myMaskImage;
	private static int myBackgroundMethod;
	private static double myBackgroundParameter;
	private static String myThresholdMethod;
	private static String myStatisticsMode;
	private static int mySearchMethod;
	private static double mySearchParameter;
	private static int myMinSize;
	private static boolean myMinimumAboveSaddle;
	private static int myPeakMethod;
	private static double myPeakParameter;
	private static int mySortMethod;
	private static int myMaxPeaks;
	private static int myShowMask;
	private static boolean myOverlayMask;
	private static boolean myShowTable;
	private static boolean myClearTable;
	private static boolean myMarkMaxima;
	private static boolean myMarkROIMaxima;
	private static boolean myMarkUsingOverlay;
	private static boolean myHideLabels;
	private static boolean myShowMaskMaximaAsDots;
	private static boolean myShowLogMessages;
	private static boolean myRemoveEdgeMaxima;
	private static String myResultsDirectory;
	private static boolean myObjectAnalysis;
	private static boolean myShowObjectMask;
	private static boolean mySaveToMemory;
	private static double myGaussianBlur;
	private static int myCentreMethod;
	private static double myCentreParameter;
	private static double myFractionParameter;

	static
	{
		// Use the default from the FindFociModel for consistency
		FindFociModel model = new FindFociModel();
		myMaskImage = model.getMaskImage();
		myBackgroundMethod = model.getBackgroundMethod();
		myBackgroundParameter = model.getBackgroundParameter();
		myThresholdMethod = model.getThresholdMethod();
		myStatisticsMode = model.getStatisticsMode();
		mySearchMethod = model.getSearchMethod();
		mySearchParameter = model.getSearchParameter();
		myMinSize = model.getMinSize();
		myMinimumAboveSaddle = model.isMinimumAboveSaddle();
		myPeakMethod = model.getPeakMethod();
		myPeakParameter = model.getPeakParameter();
		mySortMethod = model.getSortMethod();
		myMaxPeaks = model.getMaxPeaks();
		myShowMask = model.getShowMask();
		myOverlayMask = model.isOverlayMask();
		myShowTable = model.isShowTable();
		myClearTable = model.isClearTable();
		myMarkMaxima = model.isMarkMaxima();
		myMarkROIMaxima = model.isMarkROIMaxima();
		myHideLabels = model.isHideLabels();
		myShowMaskMaximaAsDots = model.isShowMaskMaximaAsDots();
		myShowLogMessages = model.isShowLogMessages();
		myRemoveEdgeMaxima = model.isRemoveEdgeMaxima();
		myResultsDirectory = model.getResultsDirectory();
		myObjectAnalysis = model.isObjectAnalysis();
		myShowObjectMask = model.isShowObjectMask();
		mySaveToMemory = model.isSaveToMemory();
		myGaussianBlur = model.getGaussianBlur();
		myCentreMethod = model.getCentreMethod();
		myCentreParameter = model.getCentreParameter();
		myFractionParameter = model.getFractionParameter();
	}

	// the following are class variables for having shorter argument lists
	private int maxx, maxy, maxz; // image dimensions
	private int xlimit, ylimit, zlimit;
	private int maxx_maxy, maxx_maxy_maxz;
	private int[] offset;
	private int dStart;
	private boolean[] flatEdge;
	private String resultsDirectory = null;
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
	private final static byte EXCLUDED = (byte) 1; // marks points outside the ROI
	private final static byte MAXIMUM = (byte) 2; // marks local maxima (irrespective of noise tolerance)
	private final static byte LISTED = (byte) 4; // marks points currently in the list
	private final static byte MAX_AREA = (byte) 8; // marks areas near a maximum, within the tolerance
	private final static byte SADDLE = (byte) 16; // marks a potential saddle between maxima
	private final static byte SADDLE_POINT = (byte) 32; // marks a saddle between maxima
	private final static byte SADDLE_WITHIN = (byte) 64; // marks a point within a maxima next to a saddle
	private final static byte PLATEAU = (byte) 128; // marks a point as a plateau region

	private final static byte BELOW_SADDLE = (byte) 128; // marks a point as falling below the highest saddle point

	private final static byte IGNORE = EXCLUDED | LISTED; // marks point to be ignored in stage 1

	// Allow the results to be stored in memory for other plugins to access
	private static HashMap<String, ArrayList<int[]>> memory = new HashMap<String, ArrayList<int[]>>();
	private static ArrayList<String> memoryNames = new ArrayList<String>();

	// Used to record all the results into a single file during batch analysis
	private OutputStreamWriter allOut = null;
	private int batchId = 0;
	private String batchPrefix = null, emptyEntry = null;

	/** Ask for parameters and then execute. */
	public void run(String arg)
	{
		UsageTracker.recordPlugin(this.getClass(), arg);

		ImagePlus imp = WindowManager.getCurrentImage();

		if ("batch".equals(arg))
		{
			runBatchMode();
			return;
		}

		if ("settings".equals(arg))
		{
			showSettingsDialog();
			return;
		}

		if (null == imp)
		{
			IJ.error(TITLE, "There must be at least one image open");
			return;
		}

		if (imp.getBitDepth() != 8 && imp.getBitDepth() != 16)
		{
			IJ.error(TITLE, "Only 8-bit and 16-bit images are supported");
			return;
		}

		// Build a list of the open images
		ArrayList<String> newImageList = buildMaskList(imp);

		GenericDialog gd = new GenericDialog(TITLE);

		gd.addChoice("Mask", newImageList.toArray(new String[0]), myMaskImage);
		gd.addMessage("Background options ...");
		gd.addChoice("Background_method", backgroundMethods, backgroundMethods[myBackgroundMethod]);
		gd.addNumericField("Background_parameter", myBackgroundParameter, 0);
		gd.addChoice("Auto_threshold", autoThresholdMethods, myThresholdMethod);
		gd.addChoice("Statistics_mode", statisticsModes, myStatisticsMode);
		gd.addMessage("Search options ...");
		gd.addChoice("Search_method", searchMethods, searchMethods[mySearchMethod]);
		gd.addNumericField("Search_parameter", mySearchParameter, 2);
		gd.addMessage("Merge options ...");
		gd.addNumericField("Minimum_size", myMinSize, 0);
		gd.addCheckbox("Minimum_above_saddle", myMinimumAboveSaddle);
		gd.addChoice("Minimum_peak_height", peakMethods, peakMethods[myPeakMethod]);
		gd.addNumericField("Peak_parameter", myPeakParameter, 2);
		gd.addMessage("Results options ...");
		Component resultsLabel = gd.getMessage(); // Note the component that will start column 2
		gd.addChoice("Sort_method", sortIndexMethods, sortIndexMethods[mySortMethod]);
		gd.addNumericField("Maximum_peaks", myMaxPeaks, 0);
		gd.addChoice("Show_mask", maskOptions, maskOptions[myShowMask]);
		gd.addCheckbox("Overlay_mask", myOverlayMask);
		gd.addSlider("Fraction_parameter", 0.05, 1, myFractionParameter);
		gd.addCheckbox("Show_table", myShowTable);
		gd.addCheckbox("Clear_table", myClearTable);
		gd.addCheckbox("Mark_maxima", myMarkMaxima);
		gd.addCheckbox("Mark_peak_maxima", myMarkROIMaxima);
		gd.addCheckbox("Mark_using_overlay", myMarkUsingOverlay);
		gd.addCheckbox("Hide_labels", myHideLabels);
		gd.addCheckbox("Show_peak_maxima_as_dots", myShowMaskMaximaAsDots);
		gd.addCheckbox("Show_log_messages", myShowLogMessages);
		gd.addCheckbox("Remove_edge_maxima", myRemoveEdgeMaxima);
		gd.addStringField("Results_directory", myResultsDirectory, 30);
		gd.addCheckbox("Object_analysis", myObjectAnalysis);
		gd.addCheckbox("Show_object_mask", myShowObjectMask);
		gd.addCheckbox("Save_to_memory", mySaveToMemory);
		gd.addMessage("Advanced options ...");
		gd.addNumericField("Gaussian_blur", myGaussianBlur, 1);
		gd.addChoice("Centre_method", centreMethods, centreMethods[myCentreMethod]);
		gd.addNumericField("Centre_parameter", myCentreParameter, 0);
		gd.addHelp(gdsc.help.URL.FIND_FOCI);

		// Re-arrange the standard layout which has a GridBagLayout with 2 columns (label,field)
		// to 4 columns: (label,field) x 2

		if (gd.getLayout() != null)
		{
			GridBagLayout grid = (GridBagLayout) gd.getLayout();

			int xOffset = 0, yOffset = 0;
			int lastY = -1, rowCount = 0;
			for (Component comp : gd.getComponents())
			{
				// Check if this should be the second major column
				if (comp == resultsLabel)
				{
					xOffset += 2;
					yOffset -= rowCount;
				}
				// Reposition the field
				GridBagConstraints c = grid.getConstraints(comp);
				if (lastY != c.gridy)
					rowCount++;
				lastY = c.gridy;
				c.gridx = c.gridx + xOffset;
				c.gridy = c.gridy + yOffset;
				c.insets.left = c.insets.left + 10 * xOffset;
				c.insets.top = 0;
				c.insets.bottom = 0;
				grid.setConstraints(comp, c);
			}

			if (IJ.isLinux())
				gd.setBackground(new Color(238, 238, 238));
		}

		gd.showDialog();
		if (gd.wasCanceled())
			return;

		myMaskImage = gd.getNextChoice();
		myBackgroundMethod = gd.getNextChoiceIndex();
		myBackgroundParameter = gd.getNextNumber();
		myThresholdMethod = gd.getNextChoice();
		myStatisticsMode = gd.getNextChoice();
		mySearchMethod = gd.getNextChoiceIndex();
		mySearchParameter = gd.getNextNumber();
		myMinSize = (int) gd.getNextNumber();
		myMinimumAboveSaddle = gd.getNextBoolean();
		myPeakMethod = gd.getNextChoiceIndex();
		myPeakParameter = gd.getNextNumber();
		mySortMethod = gd.getNextChoiceIndex();
		myMaxPeaks = (int) gd.getNextNumber();
		myShowMask = gd.getNextChoiceIndex();
		myOverlayMask = gd.getNextBoolean();
		myFractionParameter = gd.getNextNumber();
		myShowTable = gd.getNextBoolean();
		myClearTable = gd.getNextBoolean();
		myMarkMaxima = gd.getNextBoolean();
		myMarkROIMaxima = gd.getNextBoolean();
		myMarkUsingOverlay = gd.getNextBoolean();
		myHideLabels = gd.getNextBoolean();
		myShowMaskMaximaAsDots = gd.getNextBoolean();
		myShowLogMessages = gd.getNextBoolean();
		myRemoveEdgeMaxima = gd.getNextBoolean();
		myResultsDirectory = gd.getNextString();
		myObjectAnalysis = gd.getNextBoolean();
		myShowObjectMask = gd.getNextBoolean();
		mySaveToMemory = gd.getNextBoolean();
		myGaussianBlur = gd.getNextNumber();
		myCentreMethod = gd.getNextChoiceIndex();
		myCentreParameter = gd.getNextNumber();

		int outputType = getOutputMaskFlags(myShowMask);

		if (myOverlayMask)
			outputType += OUTPUT_OVERLAY_MASK;
		if (myShowTable)
			outputType += OUTPUT_RESULTS_TABLE;
		if (myClearTable)
			outputType += OUTPUT_CLEAR_RESULTS_TABLE;
		if (myMarkMaxima)
			outputType += OUTPUT_ROI_SELECTION;
		if (myMarkROIMaxima)
			outputType += OUTPUT_MASK_ROI_SELECTION;
		if (myMarkUsingOverlay)
			outputType += OUTPUT_ROI_USING_OVERLAY;
		if (myHideLabels)
			outputType += OUTPUT_HIDE_LABELS;
		if (!myShowMaskMaximaAsDots)
			outputType += OUTPUT_MASK_NO_PEAK_DOTS;
		if (myShowLogMessages)
			outputType += OUTPUT_LOG_MESSAGES;

		int options = 0;
		if (myMinimumAboveSaddle)
			options |= OPTION_MINIMUM_ABOVE_SADDLE;
		if (myStatisticsMode.equalsIgnoreCase("inside"))
			options |= OPTION_STATS_INSIDE;
		else if (myStatisticsMode.equalsIgnoreCase("outside"))
			options |= OPTION_STATS_OUTSIDE;
		if (myRemoveEdgeMaxima)
			options |= OPTION_REMOVE_EDGE_MAXIMA;

		if (outputType == 0)
		{
			IJ.error(TITLE, "No results options chosen");
			return;
		}

		ImagePlus mask = WindowManager.getImage(myMaskImage);

		setResultsDirectory(myResultsDirectory);

		// Only perform object analysis if necessary
		if (myObjectAnalysis && (myShowTable || this.resultsDirectory != null))
		{
			options |= OPTION_OBJECT_ANALYSIS;
			if (myShowObjectMask)
				options |= OPTION_SHOW_OBJECT_MASK;
		}

		if (mySaveToMemory)
			options |= OPTION_SAVE_TO_MEMORY;

		exec(imp, mask, myBackgroundMethod, myBackgroundParameter, myThresholdMethod, mySearchMethod, mySearchParameter,
				myMaxPeaks, myMinSize, myPeakMethod, myPeakParameter, outputType, mySortMethod, options, myGaussianBlur,
				myCentreMethod, myCentreParameter, myFractionParameter);
	}

	/**
	 * Get the output flags required for the specified index in the mask options
	 * <p>
	 * See {@link #maskOptions}
	 * 
	 * @param showMask
	 * @return The output flags
	 */
	public static int getOutputMaskFlags(int showMask)
	{
		switch (showMask)
		{
			case 1:
				return FindFoci.OUTPUT_MASK_PEAKS;
			case 2:
				return FindFoci.OUTPUT_MASK_THRESHOLD;
			case 3:
				return FindFoci.OUTPUT_MASK_PEAKS | FindFoci.OUTPUT_MASK_ABOVE_SADDLE;
			case 4:
				return FindFoci.OUTPUT_MASK_THRESHOLD | FindFoci.OUTPUT_MASK_ABOVE_SADDLE;
			case 5:
				return FindFoci.OUTPUT_MASK_PEAKS | FindFoci.OUTPUT_MASK_FRACTION_OF_INTENSITY;
			case 6:
				return FindFoci.OUTPUT_MASK_PEAKS | FindFoci.OUTPUT_MASK_FRACTION_OF_HEIGHT;
			default:
				return 0;
		}
	}

	/**
	 * Build a list of all the images with the correct dimensions to be used as a mask for the specified image.
	 * 
	 * @param imp
	 * @return
	 */
	public static ArrayList<String> buildMaskList(ImagePlus imp)
	{
		ArrayList<String> newImageList = new ArrayList<String>();
		newImageList.add("(None)");

		if (imp != null)
		{
			for (int id : gdsc.core.ij.Utils.getIDList())
			{
				ImagePlus maskImp = WindowManager.getImage(id);
				// Mask image must:
				// - Not be the same image
				// - Not be derived from the same image, i.e. a FindFoci result (check using image name)
				// - Match dimensions of the input image
				if (maskImp != null && maskImp.getID() != imp.getID() && !maskImp.getTitle().endsWith(TITLE) &&
						//!maskImp.getTitle().startsWith(imp.getTitle()) && 
						maskImp.getWidth() == imp.getWidth() && maskImp.getHeight() == imp.getHeight() &&
						(maskImp.getNSlices() == imp.getNSlices() || maskImp.getNSlices() == 1))
				{
					newImageList.add(maskImp.getTitle());
				}
			}
		}

		return newImageList;
	}

	/**
	 * Perform peak finding. Parameters as described in
	 * {@link #findMaxima(ImagePlus, int, double, String, int, double, int, int, int, double, int, int) }
	 * 
	 * @param imp
	 * @param mask
	 * @param backgroundMethod
	 * @param backgroundParameter
	 * @param autoThresholdMethod
	 * @param searchMethod
	 * @param searchParameter
	 * @param maxPeaks
	 * @param minSize
	 * @param peakMethod
	 * @param peakParameter
	 * @param outputType
	 *            See {@link #OUTPUT_LOG_MESSAGES}; {@link #OUTPUT_MASK_PEAKS}; {@link #OUTPUT_MASK_THRESHOLD};
	 *            {@link #OUTPUT_ROI_SELECTION}; {@link #OUTPUT_RESULTS_TABLE}. In the case of OUTPUT_MASK the ImagePlus
	 *            returned by
	 *            {@link #findMaxima(ImagePlus, int, double, String, int, double, int, int, int, double, int, int) }
	 *            will
	 *            be displayed.
	 * @param sortIndex
	 * @param options
	 * @param blur
	 * @param centreMethod
	 * @param centreParameter
	 * @param fractionParameter
	 */
	public void exec(ImagePlus imp, ImagePlus mask, int backgroundMethod, double backgroundParameter,
			String autoThresholdMethod, int searchMethod, double searchParameter, int maxPeaks, int minSize,
			int peakMethod, double peakParameter, int outputType, int sortIndex, int options, double blur,
			int centreMethod, double centreParameter, double fractionParameter)
	{
		if (imp.getBitDepth() != 8 && imp.getBitDepth() != 16)
		{
			IJ.error(TITLE, "Only 8-bit and 16-bit images are supported");
			return;
		}
		if ((centreMethod == CENTRE_GAUSSIAN_ORIGINAL || centreMethod == CENTRE_GAUSSIAN_SEARCH) &&
				isGaussianFitEnabled < 1)
		{
			IJ.error(TITLE, "Gaussian fit is not currently enabled");
			return;
		}

		// Ensure the ROI is reset if it is a point selection
		if ((outputType & OUTPUT_ROI_SELECTION) != 0)
		{
			Roi roi = imp.getRoi();
			imp.saveRoi(); // save previous selection so user can restore it
			if (roi != null)
			{
				if (roi.isArea())
				{
					if ((outputType & OUTPUT_ROI_USING_OVERLAY) == 0)
					{
						// YesNoCancelDialog causes asynchronous thread exception within Eclipse.
						GenericDialog d = new GenericDialog(TITLE);
						d.addMessage(
								"Warning: Marking the maxima will destroy the ROI area.\nUse the Mark_using_overlay option to preserve the ROI.\n \nClick OK to continue (destroys the area ROI)");
						d.showDialog();
						if (!d.wasOKed())
							return;
					}
				}
				else
				{
					// Remove any non-area ROI to reset the bounding rectangle
					imp.killRoi();
					roi = null;
				}
			}

			// The image may have a point ROI overlay added by the showResults(...) method called by the
			// preview functionality of the FindFoci GUI
			killOverlayPointRoi(imp);
		}

		Object[] results = findMaxima(imp, mask, backgroundMethod, backgroundParameter, autoThresholdMethod,
				searchMethod, searchParameter, maxPeaks, minSize, peakMethod, peakParameter, outputType, sortIndex,
				options, blur, centreMethod, centreParameter, fractionParameter);

		if (results == null)
		{
			IJ.showStatus("Cancelled.");
			return;
		}

		// Get the results
		ImagePlus maximaImp = (ImagePlus) results[0];
		@SuppressWarnings("unchecked")
		ArrayList<int[]> resultsArray = (ArrayList<int[]>) results[1];
		double[] stats = (double[]) results[2];

		// If we are outputting a results table or saving to file we can do the object analysis
		if ((options & OPTION_OBJECT_ANALYSIS) != 0 &&
				(((outputType & OUTPUT_RESULTS_TABLE) != 0) || resultsDirectory != null))
		{
			ImagePlus objectImp = doObjectAnalysis(mask, maximaImp, resultsArray,
					(options & OPTION_SHOW_OBJECT_MASK) != 0, null);
			if (objectImp != null)
				objectImp.show();
		}

		if ((options & OPTION_SAVE_TO_MEMORY) != 0)
		{
			saveToMemory(resultsArray, imp);
		}

		// Add peaks to a results window
		if ((outputType & OUTPUT_CLEAR_RESULTS_TABLE) != 0)
			clearResultsWindow();
		if (resultsArray != null && (outputType & OUTPUT_RESULTS_TABLE) != 0)
		{
			//if (isLogging(outputType))
			//	IJ.log("Background (noise) level = " + IJ.d2s(stats[STATS_BACKGROUND], 2));

			// There is some strange problem when using ImageJ's default results table when it asks the user
			// to save a previous list and then aborts the plugin if the user says Yes, No or Cancel leaving
			// the image locked.
			// So use a custom results table instead (or just Analyzer.setUnsavedMeasurements(false)).

			// Use a custom result table to avoid IJ bug
			createResultsWindow();
			for (int i = 0; i < resultsArray.size(); i++)
			{
				int[] result = resultsArray.get(i);
				addToResultTable(i + 1, resultsArray.size() - i, result, stats[STATS_SUM], stats[STATS_BACKGROUND],
						stats[STATS_SUM_ABOVE_BACKGROUND]);
			}
			flushResults();
			//if (!resultsArray.isEmpty())
			//	resultsWindow.append("");
		}

		// Record all the results to file
		if (resultsArray != null && resultsDirectory != null)
		{
			saveResults(generateId(imp), imp, mask, backgroundMethod, backgroundParameter, autoThresholdMethod,
					searchMethod, searchParameter, maxPeaks, minSize, peakMethod, peakParameter, outputType, sortIndex,
					options, blur, centreMethod, centreParameter, fractionParameter, resultsArray, stats,
					resultsDirectory);
		}

		// Update the mask image
		ImagePlus maxImp = null;
		Overlay overlay = null;
		if (maximaImp != null)
		{
			if ((outputType & OUTPUT_MASK) != 0)
			{
				maxImp = showImage(maximaImp, imp.getTitle() + " " + TITLE);

				// Adjust the contrast to show all the maxima
				if (resultsArray != null)
				{
					int maxValue = ((outputType & OUTPUT_MASK_THRESHOLD) != 0) ? 4 : resultsArray.size() + 1;
					maxImp.setDisplayRange(0, maxValue);
				}

				maxImp.updateAndDraw();
			}
			if ((outputType & OUTPUT_OVERLAY_MASK) != 0)
			{
				overlay = createOverlay(imp, maximaImp);
			}
		}

		// Remove ROI if not an output option
		if ((outputType & OUTPUT_ROI_SELECTION) == 0)
		{
			killPointRoi(imp);
			killOverlayPointRoi(imp);
		}
		if ((outputType & OUTPUT_MASK_ROI_SELECTION) == 0)
		{
			killPointRoi(maxImp);
			killOverlayPointRoi(maxImp);
		}

		// Add ROI crosses to original image
		if (resultsArray != null && (outputType & (OUTPUT_ROI_SELECTION | OUTPUT_MASK_ROI_SELECTION)) != 0)
		{
			if (!resultsArray.isEmpty())
			{
				if ((outputType & OUTPUT_ROI_USING_OVERLAY) != 0)
				{
					// Create an roi for each z slice
					PointRoi[] rois = createStackRoi(resultsArray, outputType);

					if ((outputType & OUTPUT_ROI_SELECTION) != 0)
					{
						killPointRoi(imp);
						overlay = addRoiToOverlay(imp, rois, overlay);
					}

					if (maxImp != null && (outputType & OUTPUT_MASK_ROI_SELECTION) != 0)
					{
						killPointRoi(maxImp);
						addRoiToOverlay(maxImp, rois);
					}
				}
				else
				{
					PointRoi roi = createRoi(resultsArray, outputType);

					if ((outputType & OUTPUT_ROI_SELECTION) != 0)
					{
						killOverlayPointRoi(imp);
						imp.setRoi(roi);
					}

					if (maxImp != null && (outputType & OUTPUT_MASK_ROI_SELECTION) != 0)
					{
						killOverlayPointRoi(maxImp);
						maxImp.setRoi(roi);
					}
				}
			}
			else
			{
				if ((outputType & OUTPUT_ROI_SELECTION) != 0)
				{
					killPointRoi(imp);
					killOverlayPointRoi(imp);
				}
				if ((outputType & OUTPUT_MASK_ROI_SELECTION) != 0)
				{
					killPointRoi(maxImp);
					killOverlayPointRoi(maxImp);
				}
			}
		}

		//if (overlay != null)
		imp.setOverlay(overlay);
	}

	/**
	 * Add the point rois to the overlay configuring the hyperstack position if necessary.
	 * 
	 * @param imp
	 * @param rois
	 * @param overlay
	 */
	private void addRoiToOverlay(ImagePlus imp, Roi[] rois)
	{
		imp.setOverlay(addRoiToOverlay(imp, rois, imp.getOverlay()));
	}

	/**
	 * Add the point rois to the overlay configuring the hyperstack position if necessary.
	 * 
	 * @param imp
	 * @param rois
	 * @param overlay
	 */
	private Overlay addRoiToOverlay(ImagePlus imp, Roi[] rois, Overlay overlay)
	{
		overlay = removeOverlayPointRoi(overlay);
		final int channel = imp.getChannel();
		final int frame = imp.getFrame();

		if (rois.length > 0)
		{
			if (overlay == null)
				overlay = new Overlay();
			for (Roi roi : rois)
			{
				if (imp.isDisplayedHyperStack())
				{
					roi = (Roi) roi.clone();
					roi.setPosition(channel, roi.getPosition(), frame);
				}
				overlay.addElement(roi);
			}
		}
		return overlay;
	}

	private PointRoi[] createStackRoi(ArrayList<int[]> resultsArray, int outputType)
	{
		final int nMaxima = resultsArray.size();
		final XYZ[] xyz = new XYZ[nMaxima];
		for (int i = 0; i < nMaxima; i++)
		{
			final int[] xy = resultsArray.get(i);
			xyz[i] = new XYZ(i + 1, xy[RESULT_X], xy[RESULT_Y], xy[RESULT_Z]);
		}

		Arrays.sort(xyz);

		final boolean hideLabels = nMaxima < 2 || (outputType & OUTPUT_HIDE_LABELS) != 0;

		PointRoi[] rois = new PointRoi[nMaxima];
		int count = 0;
		int[] ids = new int[nMaxima];
		int[] xpoints = new int[nMaxima];
		int[] ypoints = new int[nMaxima];
		int npoints = 0;
		int z = xyz[0].z;
		for (int i = 0; i < nMaxima; i++)
		{
			if (xyz[i].z != z)
			{
				rois[count++] = createPointRoi(ids, xpoints, ypoints, z + 1, npoints, hideLabels);
				npoints = 0;
			}
			ids[npoints] = xyz[i].id;
			xpoints[npoints] = xyz[i].x;
			ypoints[npoints] = xyz[i].y;
			z = xyz[i].z;
			npoints++;
		}
		rois[count++] = createPointRoi(ids, xpoints, ypoints, z + 1, npoints, hideLabels);
		return Arrays.copyOf(rois, count);
	}

	private PointRoi createPointRoi(int[] ids, int[] xpoints, int[] ypoints, int slice, int npoints, boolean hideLabels)
	{
		// Use a custom PointRoi so we can draw the labels
		PointRoi2 roi = new PointRoi2(xpoints, ypoints, npoints);
		if (hideLabels)
		{
			roi.setHideLabels(hideLabels);
		}
		else
		{
			roi.setLabels(ids);
		}
		configureOverlayRoi(roi);

		// This is only applicable to single z stack images. 
		// We should call setPosition(int,int,int) for hyperstacks
		roi.setPosition(slice);

		return roi;
	}

	private PointRoi createRoi(ArrayList<int[]> resultsArray, int outputType)
	{
		final int nMaxima = resultsArray.size();
		final int[] xpoints = new int[nMaxima];
		final int[] ypoints = new int[nMaxima];
		for (int i = 0; i < nMaxima; i++)
		{
			final int[] xy = resultsArray.get(i);
			xpoints[i] = xy[RESULT_X];
			ypoints[i] = xy[RESULT_Y];
		}
		PointRoi roi = new PointRoi(xpoints, ypoints, nMaxima);
		if ((outputType & OUTPUT_HIDE_LABELS) != 0)
			roi.setHideLabels(true);
		return roi;
	}

	private Overlay createOverlay(ImagePlus imp, ImagePlus maximaImp)
	{
		Overlay o = new Overlay();
		final int channel = imp.getChannel();
		final int frame = imp.getFrame();
		ImageStack stack = maximaImp.getImageStack();
		final int currentSlice = imp.getCurrentSlice();

		// Q. What happens if the image is the same colour?
		// Currently we just leave it to the user to switch the LUT for the image.
		final int value = Color.YELLOW.getRGB();
		//imp.getLuts();

		final boolean multiSliceStack = maximaImp.getStackSize() > 1;
		for (int slice = 1; slice <= maximaImp.getStackSize(); slice++)
		{
			// Use a RGB image to allow coloured output
			ImageProcessor ip = stack.getProcessor(slice);
			ColorProcessor cp = new ColorProcessor(ip.getWidth(), ip.getHeight());
			for (int i = 0; i < ip.getPixelCount(); i++)
			{
				if (ip.get(i) > 0)
					cp.set(i, value);
			}
			ImageRoi roi = new ImageRoi(0, 0, cp);
			roi.setZeroTransparent(true);
			roi.setOpacity(0.5);
			if (imp.isDisplayedHyperStack())
			{
				roi.setPosition(channel, slice, frame);
			}
			else
			{
				// If the mask has more than one slice then we can use the stack slice.
				// Otherwise we processed a single slice of the image and should use the current index.
				roi.setPosition((multiSliceStack) ? slice : currentSlice);
			}
			o.add(roi);
		}
		return o;
	}

	private ImagePlus showImage(ImagePlus imp, String title)
	{
		return showImage(imp, title, true);
	}

	private ImagePlus showImage(ImagePlus imp, String title, boolean show)
	{
		ImagePlus maxImp = WindowManager.getImage(title);
		ImageStack stack = imp.getImageStack();
		if (maxImp != null)
		{
			maxImp.setStack(stack);
		}
		else
		{
			maxImp = new ImagePlus(title, stack);
		}
		maxImp.setCalibration(imp.getCalibration());
		if (show)
			maxImp.show();
		return maxImp;
	}

	private String saveResults(String expId, ImagePlus imp, ImagePlus mask, int backgroundMethod,
			double backgroundParameter, String autoThresholdMethod, int searchMethod, double searchParameter,
			int maxPeaks, int minSize, int peakMethod, double peakParameter, int outputType, int sortIndex, int options,
			double blur, int centreMethod, double centreParameter, double fractionParameter,
			ArrayList<int[]> resultsArray, double[] stats, String resultsDirectory)
	{
		return saveResults(expId, imp, null, mask, null, backgroundMethod, backgroundParameter, autoThresholdMethod,
				searchMethod, searchParameter, maxPeaks, minSize, peakMethod, peakParameter, outputType, sortIndex,
				options, blur, centreMethod, centreParameter, fractionParameter, resultsArray, stats, resultsDirectory,
				null);
	}

	private void openBatchResultsFile()
	{
		try
		{
			batchId = 0;
			FileOutputStream fos = new FileOutputStream(resultsDirectory + File.separatorChar + "all.xls");
			allOut = new OutputStreamWriter(fos, "UTF-8");
			allOut.write("Image ID\tImage\t" + createResultsHeader(newLine));
		}
		catch (Exception e)
		{
			logError(e.getMessage());
			closeBatchResultsFile();
		}
	}

	private void closeBatchResultsFile()
	{
		if (allOut == null)
			return;
		try
		{
			allOut.close();
		}
		catch (Exception e)
		{
			logError(e.getMessage());
		}
		finally
		{
			allOut = null;
		}
	}

	private void initialiseBatchPrefix(String title)
	{
		batchPrefix = ++batchId + "\t" + title + "\t";
	}

	private void writeBatchResultsFile(String result)
	{
		if (allOut == null)
			return;
		try
		{
			allOut.write(batchPrefix);
			allOut.write(result);
		}
		catch (Exception e)
		{
			logError(e.getMessage());
			closeBatchResultsFile();
		}
	}

	private void writeEmptyObjectsToBatchResultsFile(ObjectAnalysisResult objectAnalysisResult)
	{
		for (int id = 1; id <= objectAnalysisResult.numberOfObjects; id++)
			if (objectAnalysisResult.fociCount[id] == 0)
				writeBatchResultsFile(buildEmptyObjectResultEntry(id, objectAnalysisResult.objectState[id]));
	}

	private void writeEmptyBatchResultsFile()
	{
		writeBatchResultsFile(buildEmptyResultEntry());
	}

	private String saveResults(String expId, ImagePlus imp, int[] imageDimension, ImagePlus mask, int[] maskDimension,
			int backgroundMethod, double backgroundParameter, String autoThresholdMethod, int searchMethod,
			double searchParameter, int maxPeaks, int minSize, int peakMethod, double peakParameter, int outputType,
			int sortIndex, int options, double blur, int centreMethod, double centreParameter, double fractionParameter,
			ArrayList<int[]> resultsArray, double[] stats, String resultsDirectory,
			ObjectAnalysisResult objectAnalysisResult)
	{
		try
		{
			// Save results to file
			FileOutputStream fos = new FileOutputStream(resultsDirectory + File.separatorChar + expId + ".xls");
			OutputStreamWriter out = new OutputStreamWriter(fos, "UTF-8");
			if (imageDimension == null)
				imageDimension = new int[] { imp.getC(), 0, imp.getT() };
			out.write(createResultsHeader(imp, imageDimension, stats, newLine));
			int[] xpoints = new int[resultsArray.size()];
			int[] ypoints = new int[resultsArray.size()];
			final StringBuilder sb = new StringBuilder();
			for (int i = 0; i < resultsArray.size(); i++)
			{
				int[] result = resultsArray.get(i);
				xpoints[i] = result[RESULT_X];
				ypoints[i] = result[RESULT_Y];

				buildResultEntry(sb, i + 1, resultsArray.size() - i, result, stats[STATS_SUM], stats[STATS_BACKGROUND],
						stats[STATS_SUM_ABOVE_BACKGROUND], newLine);
				final String resultEntry = sb.toString();
				out.write(resultEntry);
				writeBatchResultsFile(resultEntry);
				sb.setLength(0);
			}
			// Check if we have a batch file
			if (allOut != null)
			{
				if (objectAnalysisResult != null)
					// Record an empty record for empty objects
					writeEmptyObjectsToBatchResultsFile(objectAnalysisResult);
				else if (resultsArray.isEmpty())
					// Record an empty record for batch processing
					writeEmptyBatchResultsFile();
			}
			out.close();

			// Save roi to file
			RoiEncoder roiEncoder = new RoiEncoder(resultsDirectory + File.separatorChar + expId + ".roi");
			roiEncoder.write(new PointRoi(xpoints, ypoints, resultsArray.size()));

			// Save parameters to file
			if (mask != null && maskDimension == null)
				maskDimension = new int[] { mask.getC(), 0, mask.getT() };
			saveParameters(resultsDirectory + File.separatorChar + expId + ".params", imp, imageDimension, mask,
					maskDimension, backgroundMethod, backgroundParameter, autoThresholdMethod, searchMethod,
					searchParameter, maxPeaks, minSize, peakMethod, peakParameter, outputType, sortIndex, options, blur,
					centreMethod, centreParameter, fractionParameter, resultsDirectory);
			return expId;
		}
		catch (Exception e)
		{
			logError(e.getMessage());
		}
		return "";
	}

	/**
	 * Save the FindFoci parameters to file
	 * 
	 * @return True if the parameters were saved
	 */
	static boolean saveParameters(String filename, ImagePlus imp, int[] imageDimension, ImagePlus mask,
			int[] maskDimension, int backgroundMethod, double backgroundParameter, String autoThresholdMethod,
			int searchMethod, double searchParameter, int maxPeaks, int minSize, int peakMethod, double peakParameter,
			int outputType, int sortIndex, int options, double blur, int centreMethod, double centreParameter,
			double fractionParameter, String resultsDirectory)
	{
		try
		{
			// Save parameters to file
			FileOutputStream fos = new FileOutputStream(filename);
			OutputStreamWriter out = new OutputStreamWriter(fos, "UTF-8");
			if (imp != null)
			{
				writeParam(out, "Image", imp.getTitle());
				if (imp.getOriginalFileInfo() != null)
					writeParam(out, "File", imp.getOriginalFileInfo().directory + imp.getOriginalFileInfo().fileName);
				writeParam(out, "Image_C", Integer.toString(imageDimension[BatchParameters.C]));
				writeParam(out, "Image_Z", Integer.toString(imageDimension[BatchParameters.Z]));
				writeParam(out, "Image_T", Integer.toString(imageDimension[BatchParameters.T]));
			}
			if (mask != null)
			{
				writeParam(out, "Mask", mask.getTitle());
				if (mask.getOriginalFileInfo() != null)
					writeParam(out, "Mask File",
							mask.getOriginalFileInfo().directory + mask.getOriginalFileInfo().fileName);
				writeParam(out, "Mask_C", Integer.toString(maskDimension[BatchParameters.C]));
				writeParam(out, "Mask_Z", Integer.toString(maskDimension[BatchParameters.Z]));
				writeParam(out, "Mask_T", Integer.toString(maskDimension[BatchParameters.T]));
			}
			writeParam(out, "Background_method", backgroundMethods[backgroundMethod]);
			writeParam(out, "Background_parameter", Double.toString(backgroundParameter));
			if (autoThresholdMethod == null || autoThresholdMethod.length() == 0)
				autoThresholdMethod = "(None)";
			writeParam(out, "Auto_threshold", autoThresholdMethod);
			writeParam(out, "Statistics_mode", getStatisticsMode(options));
			writeParam(out, "Search_method", searchMethods[searchMethod]);
			writeParam(out, "Search_parameter", Double.toString(searchParameter));
			writeParam(out, "Minimum_size", Integer.toString(minSize));
			writeParam(out, "Minimum_above_saddle", ((options & OPTION_MINIMUM_ABOVE_SADDLE) != 0) ? "true" : "false");
			writeParam(out, "Minimum_peak_height", peakMethods[peakMethod]);
			writeParam(out, "Peak_parameter", Double.toString(peakParameter));
			writeParam(out, "Sort_method", sortIndexMethods[sortIndex]);
			writeParam(out, "Maximum_peaks", Integer.toString(maxPeaks));
			writeParam(out, "Show_mask", maskOptions[getMaskOption(outputType)]);
			writeParam(out, "Overlay_mask", ((outputType & OUTPUT_OVERLAY_MASK) != 0) ? "true" : "false");
			writeParam(out, "Fraction_parameter", "" + fractionParameter);
			writeParam(out, "Show_table", ((outputType & OUTPUT_RESULTS_TABLE) != 0) ? "true" : "false");
			writeParam(out, "Clear_table", ((outputType & OUTPUT_CLEAR_RESULTS_TABLE) != 0) ? "true" : "false");
			writeParam(out, "Mark_maxima", ((outputType & OUTPUT_ROI_SELECTION) != 0) ? "true" : "false");
			writeParam(out, "Mark_peak_maxima", ((outputType & OUTPUT_MASK_ROI_SELECTION) != 0) ? "true" : "false");
			writeParam(out, "Mark_using_overlay", ((outputType & OUTPUT_ROI_USING_OVERLAY) != 0) ? "true" : "false");
			writeParam(out, "Hide_labels", ((outputType & OUTPUT_HIDE_LABELS) != 0) ? "true" : "false");
			writeParam(out, "Show_peak_maxima_as_dots",
					((outputType & OUTPUT_MASK_NO_PEAK_DOTS) == 0) ? "true" : "false");
			writeParam(out, "Show_log_messages", ((outputType & OUTPUT_LOG_MESSAGES) != 0) ? "true" : "false");
			writeParam(out, "Remove_edge_maxima", ((options & OPTION_REMOVE_EDGE_MAXIMA) != 0) ? "true" : "false");
			writeParam(out, "Results_directory", resultsDirectory);
			writeParam(out, "Object_analysis", ((options & OPTION_OBJECT_ANALYSIS) != 0) ? "true" : "false");
			writeParam(out, "Show_object_mask", ((options & OPTION_SHOW_OBJECT_MASK) != 0) ? "true" : "false");
			writeParam(out, "Save_to_memory ", ((options & OPTION_SAVE_TO_MEMORY) != 0) ? "true" : "false");
			writeParam(out, "Gaussian_blur", "" + blur);
			writeParam(out, "Centre_method", centreMethods[centreMethod]);
			writeParam(out, "Centre_parameter", "" + centreParameter);
			out.close();
			return true;
		}
		catch (Exception e)
		{
			logError(e.getMessage());
		}
		return false;
	}

	public static String getStatisticsMode(int options)
	{
		if ((options &
				(FindFoci.OPTION_STATS_INSIDE | FindFoci.OPTION_STATS_OUTSIDE)) == (FindFoci.OPTION_STATS_INSIDE |
						FindFoci.OPTION_STATS_OUTSIDE))
			return "Both";
		if ((options & FindFoci.OPTION_STATS_INSIDE) != 0)
			return "Inside";
		if ((options & FindFoci.OPTION_STATS_OUTSIDE) != 0)
			return "Outside";
		return "Both";
	}

	/**
	 * Show the result of the FindFoci algorithm.
	 * <p>
	 * The method must be called with the output from
	 * {@link #findMaxima(ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int, double, int, double)}
	 */
	public void showResults(ImagePlus imp, ImagePlus mask, int backgroundMethod, double backgroundParameter,
			String autoThresholdMethod, double searchParameter, int maxPeaks, int minSize, int peakMethod,
			double peakParameter, int outputType, int sortIndex, int options, Object[] results)
	{
		// Get the results
		ImagePlus maximaImp = (ImagePlus) results[0];
		@SuppressWarnings("unchecked")
		ArrayList<int[]> resultsArray = (ArrayList<int[]>) results[1];
		double[] stats = (double[]) results[2];

		// If we are outputting a results table or saving to file we can do the object analysis
		if ((options & OPTION_OBJECT_ANALYSIS) != 0 && ((outputType & OUTPUT_RESULTS_TABLE) != 0))
		{
			ImagePlus objectImp = doObjectAnalysis(mask, maximaImp, resultsArray,
					(options & OPTION_SHOW_OBJECT_MASK) != 0, null);
			if (objectImp != null)
				objectImp.show();
		}

		if ((options & OPTION_SAVE_TO_MEMORY) != 0)
		{
			saveToMemory(resultsArray, imp);
		}

		// Add peaks to a results window
		if ((outputType & OUTPUT_CLEAR_RESULTS_TABLE) != 0)
			clearResultsWindow();
		if (resultsArray != null && (outputType & OUTPUT_RESULTS_TABLE) != 0)
		{
			// There is some strange problem when using ImageJ's default results table when it asks the user
			// to save a previous list and then aborts the plugin if the user says Yes, No or Cancel leaving
			// the image locked.
			// So use a custom results table instead (or just Analyzer.setUnsavedMeasurements(false)).

			// Use a custom result table to avoid IJ bug
			createResultsWindow();
			for (int i = 0; i < resultsArray.size(); i++)
			{
				int[] result = resultsArray.get(i);
				addToResultTable(i + 1, resultsArray.size() - i, result, stats[STATS_SUM], stats[STATS_BACKGROUND],
						stats[STATS_SUM_ABOVE_BACKGROUND]);
			}
			flushResults();
			//if (!resultsArray.isEmpty())
			//	resultsWindow.append("");
		}

		// Update the mask image
		ImagePlus maxImp = null;
		Overlay overlay = null;
		if (maximaImp != null)
		{
			if ((outputType & OUTPUT_MASK) != 0)
			{
				maxImp = showImage(maximaImp, imp.getTitle() + " " + TITLE);

				// Adjust the contrast to show all the maxima
				if (resultsArray != null)
				{
					int maxValue = ((outputType & OUTPUT_MASK_THRESHOLD) != 0) ? 4 : resultsArray.size() + 1;
					maxImp.setDisplayRange(0, maxValue);
				}

				maxImp.updateAndDraw();
			}
			if ((outputType & OUTPUT_OVERLAY_MASK) != 0)
			{
				overlay = createOverlay(imp, maximaImp);
			}
		}

		// Remove ROI if not an output option
		if ((outputType & OUTPUT_ROI_SELECTION) == 0)
		{
			killPointRoi(imp);
			killOverlayPointRoi(imp);
		}
		if ((outputType & OUTPUT_MASK_ROI_SELECTION) == 0)
		{
			killPointRoi(maxImp);
			killOverlayPointRoi(maxImp);
		}

		// Add ROI crosses to original image
		if (resultsArray != null && (outputType & (OUTPUT_ROI_SELECTION | OUTPUT_MASK_ROI_SELECTION)) != 0)
		{
			if (!resultsArray.isEmpty())
			{
				if ((outputType & OUTPUT_ROI_USING_OVERLAY) != 0)
				{
					// Create an roi for each z slice
					PointRoi[] rois = createStackRoi(resultsArray, outputType);

					if ((outputType & OUTPUT_ROI_SELECTION) != 0)
					{
						killPointRoi(imp);
						overlay = addRoiToOverlay(imp, rois, overlay);
					}

					if (maxImp != null && (outputType & OUTPUT_MASK_ROI_SELECTION) != 0)
					{
						killPointRoi(maxImp);
						addRoiToOverlay(maxImp, rois);
					}
				}
				else
				{
					PointRoi roi = createRoi(resultsArray, outputType);

					if ((outputType & OUTPUT_ROI_SELECTION) != 0)
					{
						killOverlayPointRoi(imp);

						// Use an overlay so that any area ROI is preserved when previewing results
						if (imp.getRoi() != null && !(imp.getRoi() instanceof PointRoi))
						{
							Roi roi2 = (Roi) roi.clone();
							configureOverlayRoi(roi2);
							// Add to the current overlay
							if (overlay != null)
								overlay.add(roi2);
							else
								overlay = new Overlay(roi2);
						}
						else
						{
							imp.setRoi(roi);
						}
					}

					if (maxImp != null && (outputType & OUTPUT_MASK_ROI_SELECTION) != 0)
					{
						killOverlayPointRoi(maxImp);
						maxImp.setRoi(roi);
					}
				}
			}
			else
			{
				if ((outputType & OUTPUT_ROI_SELECTION) != 0)
				{
					killPointRoi(imp);
					killOverlayPointRoi(imp);
				}
				if ((outputType & OUTPUT_MASK_ROI_SELECTION) != 0)
				{
					killPointRoi(maxImp);
					killOverlayPointRoi(maxImp);
				}
			}
		}

		// Set the overlay (if null this will remove the old one)
		//if (overlay != null)
		imp.setOverlay(overlay);
	}

	private void killPointRoi(ImagePlus imp)
	{
		if (imp != null)
		{
			if (imp.getRoi() != null && imp.getRoi().getType() == Roi.POINT)
			{
				imp.killRoi();
			}
		}
	}

	private void configureOverlayRoi(Roi roi)
	{
		roi.setStrokeWidth(1);
		roi.setStrokeColor(Color.CYAN);
		roi.setFillColor(Color.YELLOW);
	}

	private void killOverlayPointRoi(ImagePlus imp)
	{
		if (imp != null && imp.getOverlay() != null)
		{
			imp.setOverlay(removeOverlayPointRoi(imp.getOverlay()));
		}
	}

	/**
	 * Removes any PointRoi from the overlay
	 * 
	 * @param overlay
	 * @return The reduced overlay (or null)
	 */
	private Overlay removeOverlayPointRoi(Overlay overlay)
	{
		if (overlay != null)
		{
			Roi[] rois = overlay.toArray();
			int count = 0;
			for (int i = 0; i < rois.length; i++)
			{
				if (rois[i] instanceof PointRoi)
				{
					if (rois[i].getStrokeColor() == Color.CYAN && rois[i].getFillColor() == Color.YELLOW)
					{
						continue;
					}
				}
				rois[count++] = rois[i];
			}
			if (count == 0)
				return null;
			if (count != rois.length)
			{
				overlay.clear();
				for (int i = 0; i < count; i++)
					overlay.add(rois[i]);
			}
		}
		return overlay;
	}

	private static int getMaskOption(int outputType)
	{
		if (isSet(outputType, FindFoci.OUTPUT_MASK_THRESHOLD | FindFoci.OUTPUT_MASK_FRACTION_OF_HEIGHT))
			return 6;
		if (isSet(outputType, FindFoci.OUTPUT_MASK_THRESHOLD | FindFoci.OUTPUT_MASK_FRACTION_OF_INTENSITY))
			return 5;
		if (isSet(outputType, FindFoci.OUTPUT_MASK_THRESHOLD | FindFoci.OUTPUT_MASK_ABOVE_SADDLE))
			return 4;
		if (isSet(outputType, FindFoci.OUTPUT_MASK_PEAKS | FindFoci.OUTPUT_MASK_ABOVE_SADDLE))
			return 3;
		if (isSet(outputType, FindFoci.OUTPUT_MASK_THRESHOLD))
			return 2;
		if (isSet(outputType, FindFoci.OUTPUT_MASK_PEAKS))
			return 1;

		return 0;
	}

	private static boolean isSet(int flag, int mask)
	{
		return (flag & mask) == mask;
	}

	private static void writeParam(OutputStreamWriter out, String key, String value) throws IOException
	{
		out.write(key);
		out.write(" = ");
		out.write(value);
		out.write(newLine);
	}

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
	 *            The input image
	 * @param mask
	 *            A mask image used to define the region to search for peaks
	 * @param backgroundMethod
	 *            Method for calculating the background level (use the constants with prefix BACKGROUND_)
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
	 *            Use {@link #OUTPUT_MASK_PEAKS} to get an ImagePlus in the result Object array. Use
	 *            {@link #OUTPUT_LOG_MESSAGES} to get runtime information.
	 * @param sortIndex
	 *            The index of the result statistic to use for the peak sorting
	 * @param options
	 *            An options flag (use the constants with prefix OPTION_)
	 * @param blur
	 *            Apply a Gaussian blur of the specified radius before processing (helps smooth noisy images for better
	 *            peak identification)
	 * @param centreMethod
	 *            Define the method used to calculate the peak centre (use the constants with prefix CENTRE_)
	 * @param centreParameter
	 *            Parameter for calculating the peak centre
	 * @param fractionParameter
	 *            Used to specify the fraction of the peak to show in the mask
	 * @return Object array containing: (1) a new ImagePlus (with a stack) where the maxima are set to nMaxima+1 and
	 *         peak areas numbered starting from nMaxima (Background 0). Pixels outside of the roi of the input ip are
	 *         not set. Alternatively the peak areas can be thresholded using the auto-threshold method and coloured
	 *         1(saddle), 2(background), 3(threshold), 4(peak); (2) a result ArrayList<int[]> with details of the
	 *         maxima. The details can be extracted for each result using the constants defined with the prefix RESULT_;
	 *         (3) the image statistics double[] array. The details can be extracted using the constants defined with
	 *         the STATS_ prefix. Returns null if cancelled by escape.
	 */
	public Object[] findMaxima(ImagePlus imp, ImagePlus mask, int backgroundMethod, double backgroundParameter,
			String autoThresholdMethod, int searchMethod, double searchParameter, int maxPeaks, int minSize,
			int peakMethod, double peakParameter, int outputType, int sortIndex, int options, double blur,
			int centreMethod, double centreParameter, double fractionParameter)
	{
		lastResultsArray = null;

		if (imp.getBitDepth() != 8 && imp.getBitDepth() != 16)
		{
			IJ.error(TITLE, "Only 8-bit and 16-bit images are supported");
			return null;
		}
		if ((centreMethod == CENTRE_GAUSSIAN_ORIGINAL || centreMethod == CENTRE_GAUSSIAN_SEARCH) &&
				isGaussianFitEnabled < 1)
		{
			IJ.error(TITLE, "Gaussian fit is not currently enabled");
			return null;
		}

		boolean isLogging = isLogging(outputType);

		if (isLogging)
			IJ.log("---" + newLine + TITLE + " : " + imp.getTitle());

		// Call first to set up the processing for isWithin;
		initialise(imp);
		IJ.resetEscape();
		long start = System.currentTimeMillis();
		timingStart();
		boolean restrictAboveSaddle = (options & OPTION_MINIMUM_ABOVE_SADDLE) == OPTION_MINIMUM_ABOVE_SADDLE;

		IJ.showStatus("Initialising memory...");

		ImagePlus originalImp = imp;
		int[] originalImage = extractImage(imp);
		int[] image;

		if (blur > 0)
		{
			// Apply a Gaussian pre-processing step
			imp = applyBlur(imp, blur);
			image = extractImage(imp);
		}
		else
		{
			// The images are the same so just copy the reference
			image = originalImage;
		}

		byte[] types = new byte[maxx_maxy_maxz]; // Will be a notepad for pixel types
		int[] maxima = new int[maxx_maxy_maxz]; // Contains the maxima Id assigned for each point

		IJ.showStatus("Initialising ROI...");

		// Mark any point outside the ROI as processed
		int exclusion = excludeOutsideROI(originalImp, types, isLogging);
		exclusion += excludeOutsideMask(mask, types, isLogging);

		// The histogram is used to process the levels in the assignPointsToMaxima() routine. 
		// So only use those that have not been excluded.
		IJ.showStatus("Building histogram...");

		int[] histogram = buildHistogram(imp, image, types, OPTION_STATS_INSIDE);
		double[] stats = getStatistics(histogram);

		int[] statsHistogram = histogram;

		// Set to both by default
		if ((options & (OPTION_STATS_INSIDE | OPTION_STATS_OUTSIDE)) == 0)
			options |= OPTION_STATS_INSIDE | OPTION_STATS_OUTSIDE;

		// Allow the threshold to be set using pixels outside the mask/ROI, or both inside and outside.
		if (exclusion > 0 && (options & OPTION_STATS_OUTSIDE) != 0)
		{
			if ((options & OPTION_STATS_INSIDE) != 0)
			{
				// Both inside and outside
				statsHistogram = buildHistogram(imp, image);
			}
			else
			{
				statsHistogram = buildHistogram(imp, image, types, OPTION_STATS_OUTSIDE);
			}
			double[] newStats = getStatistics(statsHistogram);
			for (int i = 0; i < 4; i++)
				stats[i + STATS_MIN_BACKGROUND] = newStats[i + STATS_MIN];
		}

		if (isLogging)
			recordStatistics(stats, exclusion, options);

		if (Utils.isInterrupted())
			return null;

		// Calculate the auto-threshold if necessary
		if (backgroundMethod == BACKGROUND_AUTO_THRESHOLD)
		{
			stats[STATS_BACKGROUND] = getThreshold(autoThresholdMethod, statsHistogram);
		}
		statsHistogram = null;

		IJ.showStatus("Getting sorted maxima...");
		stats[STATS_BACKGROUND] = getSearchThreshold(backgroundMethod, backgroundParameter, stats);
		if (isLogging)
			IJ.log("Background level = " + IJ.d2s(stats[STATS_BACKGROUND], 2));
		Coordinate[] maxPoints = getSortedMaxPoints(image, maxima, types, round(stats[STATS_MIN]),
				round(stats[STATS_BACKGROUND]));

		if (Utils.isInterrupted() || maxPoints == null)
			return null;

		if (isLogging)
			IJ.log("Number of potential maxima = " + maxPoints.length);
		IJ.showStatus("Analyzing maxima...");

		ArrayList<int[]> resultsArray = new ArrayList<int[]>(maxPoints.length);

		assignMaxima(maxima, maxPoints, resultsArray);

		// Free memory
		maxPoints = null;

		assignPointsToMaxima(image, histogram, types, stats, maxima);

		if (Utils.isInterrupted())
			return null;

		if (isLogging)
			timingSplit("Assigned maxima");

		// Remove points below the peak growth criteria
		pruneMaxima(image, types, searchMethod, searchParameter, stats, resultsArray, maxima);

		// Calculate the initial results (peak size and intensity)
		calculateInitialResults(image, maxima, resultsArray);

		IJ.showStatus("Finding saddle points...");

		// Calculate the highest saddle point for each peak
		ArrayList<LinkedList<int[]>> saddlePoints = new ArrayList<LinkedList<int[]>>(resultsArray.size() + 1);
		findSaddlePoints(image, types, resultsArray, maxima, saddlePoints);

		if (Utils.isInterrupted())
			return null;

		// Find the peak sizes above their saddle points.
		analysePeaks(resultsArray, image, maxima, stats);

		if (isLogging)
			timingSplit("Mapped saddle points");

		IJ.showStatus("Merging peaks...");

		// Combine maxima below the minimum peak criteria to adjacent peaks (or eliminate if no neighbours)
		int originalNumberOfPeaks = resultsArray.size();
		mergeSubPeaks(resultsArray, image, maxima, minSize, peakMethod, peakParameter, stats, saddlePoints, isLogging,
				restrictAboveSaddle);

		if (isLogging)
			timingSplit("Merged peaks");

		if (Utils.isInterrupted())
			return null;

		if ((options & OPTION_REMOVE_EDGE_MAXIMA) != 0)
			removeEdgeMaxima(resultsArray, image, maxima, stats, isLogging);

		final int totalPeaks = resultsArray.size();

		if (blur > 0)
		{
			// Recalculate the totals but do not update the saddle values 
			// (since basing these on the blur image should give smoother saddle results).
			calculateNativeResults(originalImage, maxima, resultsArray, originalNumberOfPeaks);
		}
		else
		{
			// If no blur was applied, then the centre using the original image will be the same as using the search
			if (centreMethod == CENTRE_MAX_VALUE_ORIGINAL)
				centreMethod = CENTRE_MAX_VALUE_SEARCH;
		}

		stats[STATS_SUM_ABOVE_BACKGROUND] = getIntensityAboveBackground(originalImage, types,
				round(stats[STATS_BACKGROUND]));

		// Calculate the peaks centre and maximum value.
		locateMaxima(originalImage, image, maxima, types, resultsArray, originalNumberOfPeaks, centreMethod,
				centreParameter);

		// Calculate the average intensity and values minus background
		calculateFinalResults(resultsArray, round(stats[STATS_BACKGROUND]));

		// Reorder the results
		sortDescResults(resultsArray, sortIndex, stats);

		// Only return the best results
		while (resultsArray.size() > maxPeaks)
		{
			resultsArray.remove(resultsArray.size() - 1);
		}

		int nMaxima = resultsArray.size();
		if (isLogging)
		{
			String message = "Final number of peaks = " + nMaxima;
			if (nMaxima < totalPeaks)
				message += " / " + totalPeaks;
			IJ.log(message);
		}

		if (isLogging)
			timingSplit("Calulated results");

		// Build the output mask
		ImagePlus outImp = null;
		if ((outputType & CREATE_OUTPUT_MASK) != 0)
		{
			IJ.showStatus("Generating mask image...");

			outImp = generateOutputMask(outputType, autoThresholdMethod, imp, fractionParameter, image, types, maxima,
					stats, resultsArray, nMaxima);

			if (outImp == null)
				IJ.error(TITLE, "Too many maxima to display in a 16-bit image: " + nMaxima);

			if (isLogging)
				timingSplit("Calulated output mask");
		}

		renumberPeaks(resultsArray, originalNumberOfPeaks);

		IJ.showTime(imp, start, "Done ", maxz);

		lastResultsArray = resultsArray;

		return new Object[] { outImp, resultsArray, stats };
	}

	private int getThreshold(String autoThresholdMethod, int[] statsHistogram)
	{
		if (autoThresholdMethod.endsWith("evel"))
		{
			Multi_OtsuThreshold multi = new Multi_OtsuThreshold();
			multi.ignoreZero = false;
			int level = autoThresholdMethod.contains("_3_") ? 3 : 4;
			int[] threshold = multi.calculateThresholds(statsHistogram, level);
			return threshold[1];
		}
		return AutoThreshold.getThreshold(autoThresholdMethod, statsHistogram);
	}

	private long timestamp;

	private void timingStart()
	{
		timestamp = System.nanoTime();
	}

	private void timingSplit(String string)
	{
		long newTimestamp = System.nanoTime();
		IJ.log(string + " = " + ((newTimestamp - timestamp) / 1000000.0) + " msec");
		timestamp = newTimestamp;
	}

	/**
	 * Extract the image into a linear array stacked in zyx order
	 */
	private int[] extractImage(ImagePlus imp)
	{
		ImageStack stack = imp.getStack();
		int[] image = new int[maxx_maxy_maxz];
		int c = imp.getChannel();
		int f = imp.getFrame();
		for (int slice = 1, i = 0; slice <= maxz; slice++)
		{
			int stackIndex = imp.getStackIndex(c, slice, f);
			ImageProcessor ip = stack.getProcessor(stackIndex);
			for (int index = 0; index < ip.getPixelCount(); index++)
			{
				image[i++] = ip.get(index);
			}
		}
		return image;
	}

	/**
	 * Apply a Gaussian blur to the image and returns a new image.
	 * Returns the original image if blur <= 0.
	 * <p>
	 * Only blurs the current channel and frame for use in the FindFoci algorithm.
	 * 
	 * @param imp
	 * @param blur
	 *            The blur standard deviation
	 * @return the blurred image
	 */
	public static ImagePlus applyBlur(ImagePlus imp, double blur)
	{
		if (blur > 0)
		{
			// Note: imp.duplicate() crops the image if there is an ROI selection
			// so duplicate each ImageProcessor instead.
			GaussianBlur gb = new GaussianBlur();
			ImageStack stack = imp.getImageStack();
			ImageStack newStack = new ImageStack(stack.getWidth(), stack.getHeight(), stack.getSize());
			int channel = imp.getChannel();
			int frame = imp.getFrame();
			int[] dim = imp.getDimensions();
			// Copy the entire stack
			for (int slice = 1; slice <= stack.getSize(); slice++)
				newStack.setPixels(stack.getProcessor(slice).getPixels(), slice);
			// Now blur the current channel and frame
			for (int slice = 1; slice <= dim[3]; slice++)
			{
				int stackIndex = imp.getStackIndex(channel, slice, frame);
				ImageProcessor ip = stack.getProcessor(stackIndex).duplicate();
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
	 * @return Object array containing: (1) int[] - image array; (2) byte[] - types array; (3) short[] - maxima array;
	 *         (4)
	 *         int[] - image histogram; (5) double[] - image statistics; (6) int[] - the original image; (7) ImagePlus -
	 *         the original image
	 */
	public Object[] findMaximaInit(ImagePlus originalImp, ImagePlus imp, ImagePlus mask, int backgroundMethod,
			String autoThresholdMethod, int options)
	{
		lastResultsArray = null;

		if (originalImp.getBitDepth() != 8 && originalImp.getBitDepth() != 16)
		{
			IJ.error(TITLE, "Only 8-bit and 16-bit images are supported");
			return null;
		}

		// Call first to set up the processing for isWithin;
		initialise(imp);

		int[] originalImage = extractImage(originalImp);
		int[] image = extractImage(imp);
		byte[] types = new byte[maxx_maxy_maxz]; // Will be a notepad for pixel types
		int[] maxima = new int[maxx_maxy_maxz]; // Contains the maxima Id assigned for each point

		// Mark any point outside the ROI as processed
		int exclusion = excludeOutsideROI(originalImp, types, false);
		exclusion += excludeOutsideMask(mask, types, false);

		int[] histogram = buildHistogram(imp, image, types, OPTION_STATS_INSIDE);
		double[] stats = getStatistics(histogram);

		int[] statsHistogram = histogram;

		// Set to both by default
		if ((options & (OPTION_STATS_INSIDE | OPTION_STATS_OUTSIDE)) == 0)
			options |= OPTION_STATS_INSIDE | OPTION_STATS_OUTSIDE;

		// Allow the threshold to be set using pixels outside the mask/ROI, or both inside and outside.
		if (exclusion > 0 && (options & OPTION_STATS_OUTSIDE) != 0)
		{
			if ((options & OPTION_STATS_INSIDE) != 0)
			{
				// Both inside and outside
				statsHistogram = buildHistogram(imp, image);
			}
			else
			{
				statsHistogram = buildHistogram(imp, image, types, OPTION_STATS_OUTSIDE);
			}
			double[] newStats = getStatistics(statsHistogram);
			for (int i = 0; i < 4; i++)
				stats[i + STATS_MIN_BACKGROUND] = newStats[i + STATS_MIN];
		}

		// Calculate the auto-threshold if necessary
		if (backgroundMethod == BACKGROUND_AUTO_THRESHOLD)
		{
			stats[STATS_BACKGROUND] = getThreshold(autoThresholdMethod, statsHistogram);
		}

		return new Object[] { image, types, maxima, histogram, stats, originalImage, originalImp };
	}

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
	public static Object[] cloneInitArray(Object[] initArray, Object[] clonedInitArray)
	{
		int[] image = (int[]) initArray[0];
		byte[] types = (byte[]) initArray[1];
		int[] maxima = (int[]) initArray[2];
		int[] histogram = (int[]) initArray[3];
		double[] stats = (double[]) initArray[4];
		int[] originalImage = (int[]) initArray[5];
		ImagePlus imp = (ImagePlus) initArray[6];

		byte[] types2 = null;
		int[] maxima2 = null;
		int[] histogram2 = null;
		double[] stats2 = null;

		if (clonedInitArray == null)
		{
			clonedInitArray = new Object[7];
			types2 = new byte[types.length];
			maxima2 = new int[maxima.length];
			histogram2 = new int[histogram.length];
			stats2 = new double[stats.length];
		}
		else
		{
			types2 = (byte[]) clonedInitArray[1];
			maxima2 = (int[]) clonedInitArray[2];
			histogram2 = (int[]) clonedInitArray[3];
			stats2 = (double[]) clonedInitArray[4];

			// Maxima should be all zeros
			final short zero = 0;
			Arrays.fill(maxima2, zero);
		}

		// Copy the arrays that are destructively modified 
		System.arraycopy(types, 0, types2, 0, types.length);
		System.arraycopy(histogram, 0, histogram2, 0, histogram.length);
		System.arraycopy(stats, 0, stats2, 0, stats.length);

		// Image is unchanged so this is not copied
		clonedInitArray[0] = image;
		clonedInitArray[1] = types2;
		clonedInitArray[2] = maxima2;
		clonedInitArray[3] = histogram2;
		clonedInitArray[4] = stats2;
		clonedInitArray[5] = originalImage;
		clonedInitArray[6] = imp;

		return clonedInitArray;
	}

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
	public static Object[] cloneResultsArray(Object[] initArray, Object[] clonedInitArray)
	{
		int[] image = (int[]) initArray[0];
		byte[] types = (byte[]) initArray[1];
		int[] maxima = (int[]) initArray[2];
		int[] histogram = (int[]) initArray[3];
		double[] stats = (double[]) initArray[4];
		int[] originalImage = (int[]) initArray[5];
		ImagePlus imp = (ImagePlus) initArray[6];

		byte[] types2 = null;
		int[] maxima2 = null;
		int[] histogram2 = null;
		double[] stats2 = null;

		if (clonedInitArray == null)
		{
			clonedInitArray = new Object[7];
			types2 = new byte[types.length];
			maxima2 = new int[maxima.length];
			histogram2 = new int[histogram.length];
			stats2 = new double[stats.length];
		}
		else
		{
			types2 = (byte[]) clonedInitArray[1];
			maxima2 = (int[]) clonedInitArray[2];
			histogram2 = (int[]) clonedInitArray[3];
			stats2 = (double[]) clonedInitArray[4];
		}

		// Copy the arrays that are destructively modified 
		System.arraycopy(types, 0, types2, 0, types.length);
		System.arraycopy(maxima, 0, maxima2, 0, maxima.length);
		System.arraycopy(histogram, 0, histogram2, 0, histogram.length);
		System.arraycopy(stats, 0, stats2, 0, stats.length);

		// Image is unchanged so this is not copied
		clonedInitArray[0] = image;
		clonedInitArray[1] = types2;
		clonedInitArray[2] = maxima2;
		clonedInitArray[3] = histogram2;
		clonedInitArray[4] = stats2;
		clonedInitArray[5] = originalImage;
		clonedInitArray[6] = imp;

		return clonedInitArray;
	}

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
	 * @return Object array containing: (1) null (this option is not supported); (2) a result ArrayList<int[]> with
	 *         details of the
	 *         maxima. The details can be extracted for each result using the constants defined with the prefix RESULT_;
	 *         (3) the image statistics double[] array. The details can be extracted using the constants defined with
	 *         the STATS_ prefix.
	 */
	public Object[] findMaximaRun(Object[] initArray, int backgroundMethod, double backgroundParameter,
			int searchMethod, double searchParameter, int minSize, int peakMethod, double peakParameter, int sortIndex,
			int options, double blur)
	{
		boolean restrictAboveSaddle = (options & OPTION_MINIMUM_ABOVE_SADDLE) == OPTION_MINIMUM_ABOVE_SADDLE;

		int[] image = (int[]) initArray[0];
		byte[] types = (byte[]) initArray[1]; // Will be a notepad for pixel types
		int[] maxima = (int[]) initArray[2]; // Contains the maxima Id assigned for each point
		int[] histogram = (int[]) initArray[3];
		double[] stats = (double[]) initArray[4];
		int[] originalImage = (int[]) initArray[5];

		stats[STATS_BACKGROUND] = getSearchThreshold(backgroundMethod, backgroundParameter, stats);
		stats[STATS_SUM_ABOVE_BACKGROUND] = getIntensityAboveBackground(image, types, round(stats[STATS_BACKGROUND]));
		Coordinate[] maxPoints = getSortedMaxPoints(image, maxima, types, round(stats[STATS_MIN]),
				round(stats[STATS_BACKGROUND]));

		if (maxPoints == null)
			return null;

		ArrayList<int[]> resultsArray = new ArrayList<int[]>(maxPoints.length);

		assignMaxima(maxima, maxPoints, resultsArray);

		// Free memory
		maxPoints = null;

		assignPointsToMaxima(image, histogram, types, stats, maxima);

		// Remove points below the peak growth criteria
		pruneMaxima(image, types, searchMethod, searchParameter, stats, resultsArray, maxima);

		// Calculate the initial results (peak size and intensity)
		calculateInitialResults(image, maxima, resultsArray);

		// Calculate the highest saddle point for each peak
		ArrayList<LinkedList<int[]>> saddlePoints = new ArrayList<LinkedList<int[]>>(resultsArray.size() + 1);
		findSaddlePoints(image, types, resultsArray, maxima, saddlePoints);

		// Find the peak sizes above their saddle points.
		analysePeaks(resultsArray, image, maxima, stats);

		// TODO - Add another staging method here.

		// Combine maxima below the minimum peak criteria to adjacent peaks (or eliminate if no neighbours)
		int originalNumberOfPeaks = resultsArray.size();
		mergeSubPeaks(resultsArray, image, maxima, minSize, peakMethod, peakParameter, stats, saddlePoints, false,
				restrictAboveSaddle);

		if ((options & OPTION_REMOVE_EDGE_MAXIMA) != 0)
			removeEdgeMaxima(resultsArray, image, maxima, stats, false);

		if (blur > 0)
		{
			// Recalculate the totals using the original image 
			calculateNativeResults(originalImage, maxima, resultsArray, originalNumberOfPeaks);
		}

		lastResultsArray = resultsArray;

		return new Object[] { resultsArray, new Integer(originalNumberOfPeaks) };
	}

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
	 * @return Object array containing: (1) null (this option is not supported); (2) a result ArrayList<int[]> with
	 *         details of the
	 *         maxima. The details can be extracted for each result using the constants defined with the prefix RESULT_;
	 *         (3) the image statistics double[] array. The details can be extracted using the constants defined with
	 *         the STATS_ prefix.
	 */
	public Object[] findMaximaSearch(Object[] initArray, int backgroundMethod, double backgroundParameter,
			int searchMethod, double searchParameter)
	{
		int[] image = (int[]) initArray[0];
		byte[] types = (byte[]) initArray[1]; // Will be a notepad for pixel types
		int[] maxima = (int[]) initArray[2]; // Contains the maxima Id assigned for each point
		int[] histogram = (int[]) initArray[3];
		double[] stats = (double[]) initArray[4];

		stats[STATS_BACKGROUND] = getSearchThreshold(backgroundMethod, backgroundParameter, stats);
		stats[STATS_SUM_ABOVE_BACKGROUND] = getIntensityAboveBackground(image, types, round(stats[STATS_BACKGROUND]));
		Coordinate[] maxPoints = getSortedMaxPoints(image, maxima, types, round(stats[STATS_MIN]),
				round(stats[STATS_BACKGROUND]));
		if (maxPoints == null)
			return null;

		ArrayList<int[]> resultsArray = new ArrayList<int[]>(maxPoints.length);

		assignMaxima(maxima, maxPoints, resultsArray);

		// Free memory
		maxPoints = null;

		assignPointsToMaxima(image, histogram, types, stats, maxima);

		// Remove points below the peak growth criteria
		pruneMaxima(image, types, searchMethod, searchParameter, stats, resultsArray, maxima);

		// Calculate the initial results (peak size and intensity)
		calculateInitialResults(image, maxima, resultsArray);

		// Calculate the highest saddle point for each peak
		ArrayList<LinkedList<int[]>> saddlePoints = new ArrayList<LinkedList<int[]>>(resultsArray.size() + 1);
		findSaddlePoints(image, types, resultsArray, maxima, saddlePoints);

		// Find the peak sizes above their saddle points.
		analysePeaks(resultsArray, image, maxima, stats);

		lastResultsArray = resultsArray;

		return new Object[] { resultsArray, saddlePoints };
	}

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
	 * @return Object array containing: (1) null (this option is not supported); (2) a result ArrayList<int[]> with
	 *         details of the
	 *         maxima. The details can be extracted for each result using the constants defined with the prefix RESULT_;
	 *         (3) the image statistics double[] array. The details can be extracted using the constants defined with
	 *         the STATS_ prefix.
	 */
	public Object[] findMaximaMerge(Object[] initArray, Object[] searchArray, int minSize, int peakMethod,
			double peakParameter, int options, double blur)
	{
		boolean restrictAboveSaddle = (options & OPTION_MINIMUM_ABOVE_SADDLE) == OPTION_MINIMUM_ABOVE_SADDLE;

		int[] image = (int[]) initArray[0];
		int[] maxima = (int[]) initArray[2]; // Contains the maxima Id assigned for each point
		double[] stats = (double[]) initArray[4];
		int[] originalImage = (int[]) initArray[5];

		@SuppressWarnings("unchecked")
		ArrayList<int[]> originalResultsArray = (ArrayList<int[]>) searchArray[0];
		@SuppressWarnings("unchecked")
		ArrayList<LinkedList<int[]>> originalSaddlePoints = (ArrayList<LinkedList<int[]>>) searchArray[1];

		// Clone the results
		ArrayList<int[]> resultsArray = new ArrayList<int[]>(originalResultsArray.size());
		for (int[] result : originalResultsArray)
			resultsArray.add(copy(result));

		// Clone the saddle points
		ArrayList<LinkedList<int[]>> saddlePoints = new ArrayList<LinkedList<int[]>>(originalSaddlePoints.size() + 1);
		for (LinkedList<int[]> saddlePoint : originalSaddlePoints)
		{
			LinkedList<int[]> newSaddlePoint = new LinkedList<int[]>();
			for (int[] result : saddlePoint)
			{
				newSaddlePoint.add(copy(result));
			}
			saddlePoints.add(newSaddlePoint);
		}

		// Combine maxima below the minimum peak criteria to adjacent peaks (or eliminate if no neighbours)
		int originalNumberOfPeaks = resultsArray.size();
		mergeSubPeaks(resultsArray, image, maxima, minSize, peakMethod, peakParameter, stats, saddlePoints, false,
				restrictAboveSaddle);

		if ((options & OPTION_REMOVE_EDGE_MAXIMA) != 0)
			removeEdgeMaxima(resultsArray, image, maxima, stats, false);

		if (blur > 0)
		{
			// Recalculate the totals using the original image 
			calculateNativeResults(originalImage, maxima, resultsArray, originalNumberOfPeaks);
		}

		lastResultsArray = resultsArray;

		return new Object[] { resultsArray, new Integer(originalNumberOfPeaks) };
	}

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
	 * @return Object array containing: (1) null (this option is not supported); (2) a result ArrayList<int[]> with
	 *         details of the
	 *         maxima. The details can be extracted for each result using the constants defined with the prefix RESULT_;
	 *         (3) the image statistics double[] array. The details can be extracted using the constants defined with
	 *         the STATS_ prefix.
	 */
	public Object[] findMaximaResults(Object[] initArray, Object[] runArray, int maxPeaks, int sortIndex,
			int centreMethod, double centreParameter)
	{
		if ((centreMethod == CENTRE_GAUSSIAN_ORIGINAL || centreMethod == CENTRE_GAUSSIAN_SEARCH) &&
				isGaussianFitEnabled < 1)
		{
			IJ.log(TITLE + " Error: Gaussian fit is not currently enabled");
			return null;
		}

		int[] searchImage = (int[]) initArray[0];
		byte[] types = (byte[]) initArray[1];
		int[] maxima = (int[]) initArray[2];
		double[] stats = (double[]) initArray[4];
		int[] originalImage = (int[]) initArray[5];

		@SuppressWarnings("unchecked")
		ArrayList<int[]> originalResultsArray = (ArrayList<int[]>) runArray[0];
		int originalNumberOfPeaks = (Integer) runArray[1];

		// Clone the results
		ArrayList<int[]> resultsArray = new ArrayList<int[]>(originalResultsArray.size());
		for (int[] result : originalResultsArray)
			resultsArray.add(copy(result));

		// Calculate the peaks centre and maximum value.
		locateMaxima(originalImage, searchImage, maxima, types, resultsArray, originalNumberOfPeaks, centreMethod,
				centreParameter);

		// Calculate the average intensity and values minus background
		calculateFinalResults(resultsArray, round(stats[STATS_BACKGROUND]));

		// Reorder the results
		sortDescResults(resultsArray, sortIndex, stats);

		// Only return the best results
		while (resultsArray.size() > maxPeaks)
		{
			resultsArray.remove(resultsArray.size() - 1);
		}

		renumberPeaks(resultsArray, originalNumberOfPeaks);

		lastResultsArray = resultsArray;

		return new Object[] { null, resultsArray, stats };
	}

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
	 * @return Object array containing: (1) null (this option is not supported); (2) a result ArrayList<int[]> with
	 *         details of the
	 *         maxima. The details can be extracted for each result using the constants defined with the prefix RESULT_;
	 *         (3) the image statistics double[] array. The details can be extracted using the constants defined with
	 *         the STATS_ prefix.
	 */
	public Object[] findMaximaPrelimResults(Object[] initArray, Object[] mergeArray, int maxPeaks, int sortIndex,
			int centreMethod, double centreParameter)
	{
		if ((centreMethod == CENTRE_GAUSSIAN_ORIGINAL || centreMethod == CENTRE_GAUSSIAN_SEARCH) &&
				isGaussianFitEnabled < 1)
		{
			IJ.log(TITLE + " Error: Gaussian fit is not currently enabled");
			return null;
		}

		int[] searchImage = (int[]) initArray[0];
		byte[] types = (byte[]) initArray[1];
		int[] maxima = (int[]) initArray[2];
		double[] stats = (double[]) initArray[4];
		int[] originalImage = (int[]) initArray[5];

		@SuppressWarnings("unchecked")
		ArrayList<int[]> originalResultsArray = (ArrayList<int[]>) mergeArray[0];
		int originalNumberOfPeaks = (Integer) mergeArray[1];

		// Clone the results
		ArrayList<int[]> resultsArray = new ArrayList<int[]>(originalResultsArray.size());
		for (int[] result : originalResultsArray)
			resultsArray.add(copy(result));

		// Calculate the peaks centre and maximum value.
		locateMaxima(originalImage, searchImage, maxima, types, resultsArray, originalNumberOfPeaks, centreMethod,
				centreParameter);

		// Calculate the average intensity and values minus background
		calculateFinalResults(resultsArray, round(stats[STATS_BACKGROUND]));

		// Reorder the results
		sortDescResults(resultsArray, sortIndex, stats);

		// Only return the best results
		while (resultsArray.size() > maxPeaks)
		{
			resultsArray.remove(resultsArray.size() - 1);
		}

		lastResultsArray = resultsArray;

		return new Object[] { null, resultsArray, stats };
	}

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
	 * @param prelimResultsArray
	 *            The output from {@link #findMaximaPrelimResults(Object[], Object[], int, int, int, double)}.
	 *            Contents are unchanged.
	 * @param imageTitle
	 * @param autoThresholdMethod
	 * @param fractionParameter
	 *            The height of the peak to show in the mask
	 * @return Object array containing: (1) a new ImagePlus (with a stack) where the maxima are set to nMaxima+1 and
	 *         peak areas numbered starting from nMaxima (Background 0). Pixels outside of the roi of the input ip are
	 *         not set. Alternatively the peak areas can be thresholded using the auto-threshold method and coloured
	 *         1(saddle), 2(background), 3(threshold), 4(peak); (2) a result ArrayList<int[]> with details of the
	 *         maxima. The details can be extracted for each result using the constants defined with the prefix RESULT_;
	 *         (3) the image statistics double[] array. The details can be extracted using the constants defined with
	 *         the STATS_ prefix. Returns null if cancelled by escape.
	 */
	public Object[] findMaximaMaskResults(Object[] initArray, Object[] mergeArray, Object[] prelimResultsArray,
			int outputType, String autoThresholdMethod, String imageTitle, double fractionParameter)
	{
		int[] image = (int[]) initArray[0];
		byte[] types = (byte[]) initArray[1];
		int[] maxima = (int[]) initArray[2];
		double[] stats = (double[]) initArray[4];

		@SuppressWarnings("unchecked")
		ArrayList<int[]> originalResultsArray = (ArrayList<int[]>) prelimResultsArray[1];
		int originalNumberOfPeaks = (Integer) mergeArray[1];

		// Clone the results
		ArrayList<int[]> resultsArray = new ArrayList<int[]>(originalResultsArray.size());
		for (int[] result : originalResultsArray)
			resultsArray.add(copy(result));

		int nMaxima = resultsArray.size();

		// Build the output mask
		ImagePlus outImp = null;
		if ((outputType & CREATE_OUTPUT_MASK) != 0)
		{
			ImagePlus imp = (ImagePlus) initArray[6];
			outImp = generateOutputMask(outputType, autoThresholdMethod, imp, fractionParameter, image, types, maxima,
					stats, resultsArray, nMaxima);
		}

		renumberPeaks(resultsArray, originalNumberOfPeaks);

		lastResultsArray = resultsArray;

		return new Object[] { outImp, resultsArray, stats };
	}

	private ImagePlus generateOutputMask(int outputType, String autoThresholdMethod, ImagePlus imp,
			double fractionParameter, int[] image, byte[] types, int[] maxima, double[] stats,
			ArrayList<int[]> resultsArray, int nMaxima)
	{
		// TODO - Add an option for a coloured map of peaks using 4 colours. No touching peaks should be the same colour.
		// - Assign all neighbours for each cell
		// - Start @ cell with most neighbours -> label with a colour
		// - Find unlabelled cell next to labelled cell -> label with an unused colour not used by its neighbours
		// - Repeat
		// - Finish all cells with no neighbours using random colour asignment

		String imageTitle = imp.getTitle();
		// Rebuild the mask: all maxima have value 1, the remaining peak area are numbered sequentially starting
		// with value 2.
		// First create byte values to use in the mask for each maxima
		int[] maximaValues = new int[nMaxima];
		int[] maximaPeakIds = new int[nMaxima];
		int[] displayValues = new int[nMaxima];

		if ((outputType &
				(OUTPUT_MASK_ABOVE_SADDLE | OUTPUT_MASK_FRACTION_OF_INTENSITY | OUTPUT_MASK_FRACTION_OF_HEIGHT)) != 0)
		{
			if ((outputType & OUTPUT_MASK_FRACTION_OF_HEIGHT) != 0)
				fractionParameter = Math.max(Math.min(1 - fractionParameter, 1), 0);

			// Reset unneeded flags in the types array since new flags are required to mark pixels below the cut-off height.
			byte resetFlag = (byte) (SADDLE_POINT | MAX_AREA);
			for (int i = types.length; i-- > 0;)
			{
				types[i] &= resetFlag;
			}
		}
		else
		{
			// Ensure no pixels are below the saddle height
			Arrays.fill(displayValues, -1);
		}

		for (int i = 0; i < nMaxima; i++)
		{
			maximaValues[i] = nMaxima - i;
			int[] result = resultsArray.get(i);
			maximaPeakIds[i] = result[RESULT_PEAK_ID];
			if ((outputType & OUTPUT_MASK_ABOVE_SADDLE) != 0)
			{
				displayValues[i] = result[RESULT_HIGHEST_SADDLE_VALUE];
			}
			else if ((outputType & OUTPUT_MASK_FRACTION_OF_HEIGHT) != 0)
			{
				displayValues[i] = (int) Math
						.round(fractionParameter * (result[RESULT_MAX_VALUE] - stats[STATS_BACKGROUND]) +
								stats[STATS_BACKGROUND]);
			}
		}

		if ((outputType & OUTPUT_MASK_FRACTION_OF_INTENSITY) != 0)
		{
			calculateFractionOfIntensityDisplayValues(fractionParameter, image, maxima, stats, maximaPeakIds,
					displayValues);
		}

		// Now assign the output mask
		for (int index = maxima.length; index-- > 0;)
		{
			if ((types[index] & MAX_AREA) != 0)
			{
				// Find the maxima in the list of maxima Ids.
				int i = 0;
				while (i < nMaxima && maximaPeakIds[i] != maxima[index])
					i++;
				if (i < nMaxima)
				{
					if ((image[index] <= displayValues[i]))
						types[index] |= BELOW_SADDLE;
					maxima[index] = maximaValues[i];
					continue;
				}
			}

			// Fall through condition, reset the value
			maxima[index] = 0;
			types[index] = 0;
		}

		int maxValue = nMaxima;

		if ((outputType & OUTPUT_MASK_THRESHOLD) != 0)
		{
			// Perform thresholding on the peak regions
			findBorders(maxima, types);
			for (int i = 0; i < nMaxima; i++)
			{
				thresholdMask(image, maxima, maximaValues[i], autoThresholdMethod, stats);
			}
			invertMask(maxima);
			addBorders(maxima, types);

			// Adjust the values used to create the output mask
			maxValue = 3;
		}

		// Blank any pixels below the saddle height
		if ((outputType &
				(OUTPUT_MASK_ABOVE_SADDLE | OUTPUT_MASK_FRACTION_OF_INTENSITY | OUTPUT_MASK_FRACTION_OF_HEIGHT)) != 0)
		{
			for (int i = maxima.length; i-- > 0;)
			{
				if ((types[i] & BELOW_SADDLE) != 0)
					maxima[i] = 0;
			}
		}

		// Set maxima to a high value
		if ((outputType & OUTPUT_MASK_NO_PEAK_DOTS) == 0)
		{
			maxValue++;
			for (int i = 0; i < nMaxima; i++)
			{
				final int[] result = resultsArray.get(i);
				maxima[getIndex(result[RESULT_X], result[RESULT_Y], result[RESULT_Z])] = maxValue;
			}
		}

		// Check the maxima can be displayed
		if (maxValue > MAXIMA_CAPCITY)
		{
			IJ.log("The number of maxima exceeds the 16-bit capacity used for diplay: " + MAXIMA_CAPCITY);
			return null;
		}

		// Output the mask
		// The index is '(maxx_maxy) * z + maxx * y + x' so we can simply iterate over the array if we use z, y, x order
		ImageStack stack = new ImageStack(maxx, maxy, maxz);
		if (maxValue > 255)
		{
			for (int z = 0, index = 0; z < maxz; z++)
			{
				final short[] pixels = new short[maxx_maxy];
				for (int i = 0; i < maxx_maxy; i++, index++)
					pixels[i] = (short) maxima[index];
				stack.setPixels(pixels, z + 1);
			}
		}
		else
		{
			for (int z = 0, index = 0; z < maxz; z++)
			{
				final byte[] pixels = new byte[maxx_maxy];
				for (int i = 0; i < maxx_maxy; i++, index++)
					pixels[i] = (byte) maxima[index];
				stack.setPixels(pixels, z + 1);
			}
		}

		ImagePlus result = new ImagePlus(imageTitle + " " + TITLE, stack);
		result.setCalibration(imp.getCalibration());
		return result;
	}

	private void calculateFractionOfIntensityDisplayValues(double fractionParameter, int[] image, int[] maxima,
			double[] stats, int[] maximaPeakIds, int[] displayValues)
	{
		// For each maxima
		for (int i = 0; i < maximaPeakIds.length; i++)
		{
			// Histogram all the pixels above background
			final int[] hist = buildHistogram(image, maxima, maximaPeakIds[i], round(stats[STATS_MAX]));

			final int background = (int) Math.floor(stats[STATS_BACKGROUND]);

			// Sum above background
			long sum = 0;
			for (int value = 0; value < hist.length; value++)
				sum += hist[value] * (value - background);

			// Determine the cut-off using fraction of cumulative intensity
			final long total = (long) (sum * fractionParameter);

			// Find the point in the histogram that exceeds the fraction
			sum = 0;
			int value = hist.length;
			while (value-- > 0)
			{
				sum += hist[value] * (value - background);
				if (sum > total)
					break;
			}
			displayValues[i] = value;
		}
	}

	private int[] copy(int[] array)
	{
		int[] newArray = new int[array.length];
		for (int i = newArray.length; i-- > 0;)
			newArray[i] = array[i];
		return newArray;
	}

	/**
	 * Update the peak Ids to use the sorted order
	 */
	private void renumberPeaks(ArrayList<int[]> resultsArray, int originalNumberOfPeaks)
	{
		// Build a map between the original peak number and the new sorted order
		final int[] peakIdMap = new int[originalNumberOfPeaks + 1];
		int i = 1;
		for (int[] result : resultsArray)
		{
			peakIdMap[result[RESULT_PEAK_ID]] = i++;
		}

		// Update the Ids
		for (int[] result : resultsArray)
		{
			result[RESULT_PEAK_ID] = peakIdMap[result[RESULT_PEAK_ID]];
			result[RESULT_SADDLE_NEIGHBOUR_ID] = peakIdMap[result[RESULT_SADDLE_NEIGHBOUR_ID]];
		}
	}

	/**
	 * Finds the borders of peak regions
	 * 
	 * @param maxima
	 * @param types
	 */
	private void findBorders(int[] maxima, byte[] types)
	{
		// TODO - This is not perfect. There is a problem with regions marked as saddles
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
		// It also only works on the XY plane. However it is fine for an approximation of the peak boundaries.

		final int[] xyz = new int[3];

		for (int index = maxima.length; index-- > 0;)
		{
			if (maxima[index] == 0)
			{
				types[index] = 0;
				continue;
			}

			// If a saddle, search around to check if it still a saddle
			if ((types[index] & SADDLE) != 0)
			{
				// reset unneeded flags
				types[index] &= BELOW_SADDLE;

				getXY(index, xyz);
				final int x = xyz[0];
				final int y = xyz[1];
				final boolean isInnerXY = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);

				// Process the neighbours
				for (int d = 8; d-- > 0;)
				{
					if (isInnerXY || isWithinXY(x, y, d))
					{
						final int index2 = index + offset[d];

						// Check if neighbour is a different peak
						if (maxima[index] != maxima[index2] && maxima[index2] > 0 &&
								(types[index2] & SADDLE_POINT) != SADDLE_POINT)
						{
							types[index] |= SADDLE_POINT;
						}
					}
				}
			}

			// If it is not a saddle point then mark it as within the saddle
			if ((types[index] & SADDLE_POINT) == 0)
			{
				types[index] |= SADDLE_WITHIN;
			}
		}

		for (int z = maxz; z-- > 0;)
		{
			cleanupExtraLines(types, z);
			cleanupExtraCornerPixels(types, z);
		}
	}

	/**
	 * For each saddle pixel, check the 2 adjacent non-diagonal neighbour pixels in clockwise fashion. If they are both
	 * saddle pixels then this pixel can be removed (since they form a diagonal line).
	 */
	private int cleanupExtraCornerPixels(byte[] types, int z)
	{
		int removed = 0;
		final int[] xyz = new int[3];

		for (int i = maxx_maxy, index = maxx_maxy * z; i-- > 0; index++)
		{
			if ((types[index] & SADDLE_POINT) != 0)
			{
				getXY(index, xyz);
				final int x = xyz[0];
				final int y = xyz[1];

				final boolean isInner = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);

				final boolean[] edgesSet = new boolean[8];
				for (int d = 8; d-- > 0;)
				{
					// analyze 4 flat-edge neighbours
					if (isInner || isWithinXY(x, y, d))
					{
						edgesSet[d] = ((types[index + offset[d]] & SADDLE_POINT) != 0);
					}
				}

				for (int d = 0; d < 8; d += 2)
				{
					if ((edgesSet[d] && edgesSet[(d + 2) % 8]) && !edgesSet[(d + 5) % 8])
					{
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
	 * Delete saddle lines that do not divide two peak areas. Adapted from {@link ij.plugin.filter.MaximumFinder}
	 */
	void cleanupExtraLines(byte[] types, int z)
	{
		for (int i = maxx_maxy, index = maxx_maxy * z; i-- > 0; index++)
		{
			if ((types[index] & SADDLE_POINT) != 0)
			{
				final int nRadii = nRadii(types, index); // number of lines radiating
				if (nRadii == 0) // single point or foreground patch?
				{
					types[index] &= ~SADDLE_POINT;
					types[index] |= SADDLE_WITHIN;
				}
				else if (nRadii == 1)
					removeLineFrom(types, index);
			}
		}
	}

	/** delete a line starting at x, y up to the next (4-connected) vertex */
	void removeLineFrom(byte[] types, int index)
	{
		types[index] &= ~SADDLE_POINT;
		types[index] |= SADDLE_WITHIN;
		final int[] xyz = new int[3];
		boolean continues;
		do
		{
			getXY(index, xyz);
			final int x = xyz[0];
			final int y = xyz[1];

			continues = false;
			final boolean isInner = (y != 0 && y != maxy - 1) && (x != 0 && x != maxx - 1); // not necessary, but faster
			// than isWithin
			for (int d = 0; d < 8; d += 2)
			{ // analyze 4-connected neighbors
				if (isInner || isWithinXY(x, y, d))
				{
					int index2 = index + offset[d];
					byte v = types[index2];
					if ((v & SADDLE_WITHIN) != SADDLE_WITHIN && (v & SADDLE_POINT) == SADDLE_POINT)
					{
						int nRadii = nRadii(types, index2);
						if (nRadii <= 1)
						{ // found a point or line end
							index = index2;
							types[index] &= ~SADDLE_POINT;
							types[index] |= SADDLE_WITHIN; // delete the point
							continues = nRadii == 1; // continue along that line
							break;
						}
					}
				}
			} // for directions d
		} while (continues);
	}

	/**
	 * Analyze the neighbors of a pixel (x, y) in a byte image; pixels <255 ("non-white") are considered foreground.
	 * Edge pixels are considered foreground.
	 * 
	 * @param types
	 *            The byte image
	 * @param index
	 *            coordinate of the point
	 * @return Number of 4-connected lines emanating from this point. Zero if the point is embedded in either foreground
	 *         or background
	 */
	int nRadii(byte[] types, int index)
	{
		int countTransitions = 0;
		boolean prevPixelSet = true;
		boolean firstPixelSet = true; // initialize to make the compiler happy
		final int[] xyz = new int[3];
		getXY(index, xyz);
		final int x = xyz[0];
		final int y = xyz[1];

		final boolean isInner = (y != 0 && y != maxy - 1) && (x != 0 && x != maxx - 1); // not necessary, but faster than
		// isWithin
		for (int d = 0; d < 8; d++)
		{ // walk around the point and note every no-line->line transition
			boolean pixelSet = prevPixelSet;
			if (isInner || isWithinXY(x, y, d))
			{
				boolean isSet = ((types[index + offset[d]] & SADDLE_WITHIN) == SADDLE_WITHIN);
				if ((d & 1) == 0)
					pixelSet = isSet; // non-diagonal directions: always regarded
				else if (!isSet) // diagonal directions may separate two lines,
					pixelSet = false; // but are insufficient for a 4-connected line
			}
			else
			{
				pixelSet = true;
			}
			if (pixelSet && !prevPixelSet)
				countTransitions++;
			prevPixelSet = pixelSet;
			if (d == 0)
				firstPixelSet = pixelSet;
		}
		if (firstPixelSet && !prevPixelSet)
			countTransitions++;
		return countTransitions;
	}

	/**
	 * For each peak in the maxima image, perform thresholding using the specified method.
	 * 
	 * @param image
	 * @param maxima
	 * @param s
	 * @param autoThresholdMethod
	 */
	private void thresholdMask(int[] image, int[] maxima, int peakValue, String autoThresholdMethod, double[] stats)
	{
		final int[] histogram = buildHistogram(image, maxima, peakValue, round(stats[STATS_MAX]));
		final int threshold = getThreshold(autoThresholdMethod, histogram);

		for (int i = maxima.length; i-- > 0;)
		{
			if (maxima[i] == peakValue)
			{
				// Use negative to allow use of image in place
				maxima[i] = ((image[i] > threshold) ? -3 : -2);
			}
		}
	}

	/**
	 * Build a histogram using only the specified peak area.
	 * 
	 * @param image
	 * @param maxima
	 * @param peakValue
	 * @param maxValue
	 * @return
	 */
	private int[] buildHistogram(int[] image, int[] maxima, int peakValue, int maxValue)
	{
		final int[] histogram = new int[maxValue + 1];

		for (int i = image.length; i-- > 0;)
		{
			if (maxima[i] == peakValue)
			{
				histogram[image[i]]++;
			}
		}
		return histogram;
	}

	/**
	 * Changes all negative value to positive
	 * 
	 * @param maxima
	 */
	private void invertMask(int[] maxima)
	{
		for (int i = maxima.length; i-- > 0;)
		{
			if (maxima[i] < 0)
			{
				maxima[i] = -maxima[i];
			}
		}
	}

	/**
	 * Adds the borders to the peaks
	 * 
	 * @param maxima
	 * @param types
	 */
	private void addBorders(int[] maxima, byte[] types)
	{
		for (int i = maxima.length; i-- > 0;)
		{
			if ((types[i] & SADDLE_POINT) != 0)
			{
				maxima[i] = 1;
			}
		}
	}

	/**
	 * Records the image statistics to the log window
	 * 
	 * @param stats
	 *            The statistics
	 * @param exclusion
	 *            non-zero if pixels have been excluded
	 * @param options
	 *            The options (used to get the statistics mode)
	 */
	private void recordStatistics(double[] stats, int exclusion, int options)
	{
		StringBuilder sb = new StringBuilder();
		if (exclusion > 0)
			sb.append("Image stats (inside mask/ROI) : Min = ");
		else
			sb.append("Image stats : Min = ");
		sb.append(IJ.d2s(stats[STATS_MIN], 2));
		sb.append(", Max = ").append(IJ.d2s(stats[STATS_MAX], 2));
		sb.append(", Mean = ").append(IJ.d2s(stats[STATS_AV], 2));
		sb.append(", StdDev = ").append(IJ.d2s(stats[STATS_SD], 2));
		IJ.log(sb.toString());

		sb.setLength(0);
		if (exclusion > 0)
			sb.append("Background stats (mode=").append(getStatisticsMode(options)).append(") : Min = ");
		else
			sb.append("Background stats : Min = ");
		sb.append(IJ.d2s(stats[STATS_MIN_BACKGROUND], 2));
		sb.append(", Max = ").append(IJ.d2s(stats[STATS_MAX_BACKGROUND], 2));
		sb.append(", Mean = ").append(IJ.d2s(stats[STATS_AV_BACKGROUND], 2));
		sb.append(", StdDev = ").append(IJ.d2s(stats[STATS_SD_BACKGROUND], 2));
		IJ.log(sb.toString());
	}

	/**
	 * Create the result window (if it is not available)
	 */
	private void createResultsWindow()
	{
		if (resultsWindow == null || !resultsWindow.isShowing())
		{
			resultsWindow = new TextWindow(TITLE + " Results", createResultsHeader(""), "", 900, 300);
			resetResultsCount();
		}
	}

	private void resetResultsCount()
	{
		resultsCount = 0;
		// Set this at a level where the ij.text.TextWindow will auto-layout the columns
		flushCount = 8;
	}

	private void clearResultsWindow()
	{
		if (resultsWindow != null && resultsWindow.isShowing())
		{
			resultsWindow.getTextPanel().clear();
			resetResultsCount();
		}
	}

	private String createResultsHeader(String newLine)
	{
		return createResultsHeader(null, null, null, newLine);
	}

	private String createResultsHeader(ImagePlus imp, int[] dimension, double[] stats, String newLine)
	{
		final StringBuilder sb = new StringBuilder();
		if (imp != null)
		{
			sb.append("# Image\t").append(imp.getTitle()).append(newLine);
			if (imp.getOriginalFileInfo() != null)
			{
				sb.append("# File\t").append(imp.getOriginalFileInfo().directory)
						.append(imp.getOriginalFileInfo().fileName).append(newLine);
			}
			if (dimension == null)
				dimension = new int[] { imp.getC(), 0, imp.getT() };
			sb.append("# C\t").append(dimension[0]).append(newLine);
			if (dimension[1] != 0)
				sb.append("# Z\t").append(dimension[1]).append(newLine);
			sb.append("# T\t").append(dimension[2]).append(newLine);
		}
		if (stats != null)
		{
			sb.append("# Background\t").append(stats[STATS_BACKGROUND]).append(newLine);
			sb.append("# Min\t").append(stats[STATS_MIN]).append(newLine);
			sb.append("# Max\t").append(stats[STATS_MAX]).append(newLine);
			sb.append("# Average\t").append(stats[STATS_AV]).append(newLine);
			sb.append("# Std.Dev\t").append(stats[STATS_SD]).append(newLine);
		}
		sb.append("Peak #\t");
		sb.append("Mask Value\t");
		sb.append("X\t");
		sb.append("Y\t");
		sb.append("Z\t");
		sb.append("Size\t");
		sb.append("Max\t");
		sb.append("Total\t");
		sb.append("Saddle value\t");
		sb.append("Saddle Id\t");
		sb.append("Abs.Height\t");
		sb.append("Rel.Height (>Bg)\t");
		sb.append("Size > saddle\t");
		sb.append("Total > saddle\t");
		sb.append("Av\t");
		sb.append("Total (>Bg)\t");
		sb.append("Av (>Bg)\t");
		sb.append("% Signal\t");
		sb.append("% Signal (>Bg)\t");
		sb.append("Signal / Noise\t");
		sb.append("Object\t");
		sb.append("State");
		sb.append(newLine);
		return sb.toString();
	}

	/**
	 * Add a result to the result table
	 * 
	 * @param i
	 *            Peak number
	 * @param result
	 *            The peak result
	 * @param sum
	 *            The total image intensity
	 * @param noise
	 *            The image background noise level
	 * @param intensityAboveBackground
	 *            The image intensity above the background
	 */
	private void addToResultTable(int i, int id, int[] result, double sum, double noise,
			double intensityAboveBackground)
	{
		// Buffer the output so that the table is displayed faster
		buildResultEntry(resultsBuffer, i, id, result, sum, noise, intensityAboveBackground, "\n");
		if (resultsCount > flushCount)
		{
			flushResults();
		}
	}

	private void flushResults()
	{
		resultsWindow.append(resultsBuffer.toString());
		resultsBuffer.setLength(0);
		// One we have allowed auto-layout of the columns do the rest at the same time
		flushCount = Integer.MAX_VALUE;
	}

	private void buildResultEntry(StringBuilder sb, int i, int id, int[] result, double sum, double noise,
			double intensityAboveBackground, String newLine)
	{
		final int absoluteHeight = getAbsoluteHeight(result, noise);
		final double relativeHeight = getRelativeHeight(result, noise, absoluteHeight);

		sb.append(i).append("\t");
		sb.append(id).append("\t");
		// XY are pixel coordinates
		sb.append(result[RESULT_X]).append("\t");
		sb.append(result[RESULT_Y]).append("\t");
		// Z should correspond to slice 
		sb.append(result[RESULT_Z] + 1).append("\t");
		sb.append(result[RESULT_COUNT]).append("\t");
		sb.append(result[RESULT_MAX_VALUE]).append("\t");
		sb.append(result[RESULT_INTENSITY]).append("\t");
		sb.append(result[RESULT_HIGHEST_SADDLE_VALUE]).append("\t");
		sb.append(result[RESULT_SADDLE_NEIGHBOUR_ID]).append("\t");
		sb.append(absoluteHeight).append("\t");
		sb.append(IJ.d2s(relativeHeight, 3)).append("\t");
		sb.append(result[RESULT_COUNT_ABOVE_SADDLE]).append("\t");
		sb.append(result[RESULT_INTENSITY_ABOVE_SADDLE]).append("\t");
		sb.append(IJ.d2s(1.0 * result[RESULT_INTENSITY] / result[RESULT_COUNT], 2)).append("\t");
		sb.append(result[RESULT_INTENSITY_MINUS_BACKGROUND]).append("\t");
		sb.append(IJ.d2s(1.0 * result[RESULT_INTENSITY_MINUS_BACKGROUND] / result[RESULT_COUNT], 2)).append("\t");
		sb.append(IJ.d2s(100 * (result[RESULT_INTENSITY] / sum), 2)).append("\t");
		sb.append(IJ.d2s(100 * (result[RESULT_INTENSITY_MINUS_BACKGROUND] / intensityAboveBackground), 2)).append("\t");
		sb.append(IJ.d2s((result[RESULT_MAX_VALUE] / noise), 2)).append("\t");
		sb.append(result[RESULT_OBJECT]).append("\t");
		sb.append(result[RESULT_STATE]);
		sb.append(newLine);

		resultsCount++;
	}

	private String buildEmptyObjectResultEntry(int objectId, int objectState)
	{
		final StringBuilder sb = new StringBuilder();
		// Note: The entry prefix has a tab at the end, so write field then tab
		for (int i = 0; i < 20; i++)
			sb.append(emptyField).append('\t');
		sb.append(objectId).append('\t').append(objectState);
		sb.append(newLine);
		return sb.toString();
	}

	private String buildEmptyResultEntry()
	{
		if (emptyEntry == null)
		{
			final StringBuilder sb = new StringBuilder();
			// Note: The entry prefix has a tab at the end, so write field then tab
			for (int i = 0; i < 21; i++)
				sb.append(emptyField).append('\t');
			sb.append(emptyField).append(newLine);
			emptyEntry = sb.toString();
		}
		return emptyEntry;
	}

	private String generateId(ImagePlus imp)
	{
		DateFormat df = new SimpleDateFormat("-yyyyMMdd_HHmmss");
		return "FindFoci-" + imp.getShortTitle() + df.format(new Date());
	}

	private int getAbsoluteHeight(int[] result, double noise)
	{
		int absoluteHeight = 0;
		if (result[RESULT_HIGHEST_SADDLE_VALUE] > 0)
		{
			absoluteHeight = result[RESULT_MAX_VALUE] - result[RESULT_HIGHEST_SADDLE_VALUE];
		}
		else
		{
			absoluteHeight = round(result[RESULT_MAX_VALUE] - noise);
		}
		return absoluteHeight;
	}

	private double getRelativeHeight(int[] result, double noise, int absoluteHeight)
	{
		return absoluteHeight / (result[RESULT_MAX_VALUE] - noise);
	}

	/**
	 * Set all pixels outside the ROI to PROCESSED
	 * 
	 * @param imp
	 *            The input image
	 * @param types
	 *            The types array used within the peak finding routine (same size as imp)
	 * @param isLogging
	 * @return 1 if masking was performed, else 0
	 */
	private int excludeOutsideROI(ImagePlus imp, byte[] types, boolean isLogging)
	{
		final Roi roi = imp.getRoi();

		if (roi != null && roi.isArea())
		{
			final Rectangle roiBounds = roi.getBounds();

			// Check if this ROI covers the entire image
			if (roi.getType() == Roi.RECTANGLE && roiBounds.width == maxx && roiBounds.height == maxy)
				return 0;

			// Store the bounds of the ROI for the edge object analysis
			bounds = roiBounds;

			ImageProcessor ipMask = null;
			RoundRectangle2D rr = null;

			// Use the ROI mask if present
			if (roi.getMask() != null)
			{
				ipMask = roi.getMask();
				if (isLogging)
					IJ.log("ROI = Mask");
			}
			// Use a mask for an irregular ROI
			else if (roi.getType() == Roi.FREEROI)
			{
				ipMask = imp.getMask();
				if (isLogging)
					IJ.log("ROI = Freehand ROI");
			}
			// Use a round rectangle if necessary
			else if (roi.getRoundRectArcSize() != 0)
			{
				rr = new RoundRectangle2D.Float(roiBounds.x, roiBounds.y, roiBounds.width, roiBounds.height,
						roi.getRoundRectArcSize(), roi.getRoundRectArcSize());
				if (isLogging)
					IJ.log("ROI = Round ROI");
			}

			// Set everything as processed
			for (int i = types.length; i-- > 0;)
				types[i] = EXCLUDED;

			// Now unset the ROI region

			// Create a mask from the ROI rectangle
			final int xOffset = roiBounds.x;
			final int yOffset = roiBounds.y;
			final int rwidth = roiBounds.width;
			final int rheight = roiBounds.height;

			for (int y = 0; y < rheight; y++)
			{
				for (int x = 0; x < rwidth; x++)
				{
					boolean mask = true;
					if (ipMask != null)
						mask = (ipMask.get(x, y) > 0);
					else if (rr != null)
						mask = rr.contains(x + xOffset, y + yOffset);

					if (mask)
					{
						// Set each z-slice as excluded
						for (int index = getIndex(x + xOffset, y + yOffset,
								0); index < maxx_maxy_maxz; index += maxx_maxy)
						{
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
	 * Set all pixels outside the Mask to PROCESSED
	 * 
	 * @param imp
	 *            The mask image
	 * @param types
	 *            The types array used within the peak finding routine
	 * @return 1 if masking was performed, else 0
	 */
	private int excludeOutsideMask(ImagePlus mask, byte[] types, boolean isLogging)
	{
		if (mask == null)
			return 0;

		// Check sizes in X & Y
		if (mask.getWidth() != maxx || mask.getHeight() != maxy ||
				(mask.getNSlices() != maxz && mask.getNSlices() != 1))
		{
			if (isLogging)
			{
				IJ.log("Mask dimensions do not match the image");
			}
			return 0;
		}

		if (isLogging)
		{
			IJ.log("Mask image = " + mask.getTitle());
		}

		if (mask.getNSlices() == 1)
		{
			// If a single plane then duplicate through the image
			final ImageProcessor ipMask = mask.getProcessor();

			for (int i = maxx_maxy; i-- > 0;)
			{
				if (ipMask.get(i) == 0)
				{
					for (int index = i; index < maxx_maxy_maxz; index += maxx_maxy)
					{
						types[index] |= EXCLUDED;
					}
				}
			}
		}
		else
		{
			// If the same stack size then process through the image
			final ImageStack stack = mask.getStack();
			final int c = mask.getChannel();
			final int f = mask.getFrame();
			for (int slice = 1; slice <= mask.getNSlices(); slice++)
			{
				int stackIndex = mask.getStackIndex(c, slice, f);
				ImageProcessor ipMask = stack.getProcessor(stackIndex);

				int index = maxx_maxy * slice;
				for (int i = maxx_maxy; i-- > 0;)
				{
					index--;
					if (ipMask.get(i) == 0)
					{
						types[index] |= EXCLUDED;
					}
				}
			}
		}

		return 1;
	}

	/**
	 * Extract the mask image
	 * 
	 * @param imp
	 *            The mask image
	 * @return The mask image array
	 */
	private int[] extractMask(ImagePlus mask)
	{
		if (mask == null)
			return null;

		// Check sizes in X & Y
		if (mask.getWidth() != maxx || mask.getHeight() != maxy ||
				(mask.getNSlices() != maxz && mask.getNSlices() != 1))
		{
			return null;
		}

		final int[] image;
		if (mask.getNSlices() == 1)
		{
			// Extract a single plane
			ImageProcessor ipMask = mask.getProcessor();

			image = new int[maxx_maxy];
			for (int i = maxx_maxy; i-- > 0;)
			{
				image[i] = ipMask.get(i);
			}
		}
		else
		{
			// If the same stack size then process through the image
			final ImageStack stack = mask.getStack();
			final int c = mask.getChannel();
			final int f = mask.getFrame();
			image = new int[maxx_maxy_maxz];
			for (int slice = 1; slice <= mask.getNSlices(); slice++)
			{
				final int stackIndex = mask.getStackIndex(c, slice, f);
				ImageProcessor ipMask = stack.getProcessor(stackIndex);

				int index = maxx_maxy * slice;
				for (int i = maxx_maxy; i-- > 0;)
				{
					index--;
					image[index] = ipMask.get(i);
				}
			}
		}

		return image;
	}

	/**
	 * Build a histogram using all pixels not marked as EXCLUDED
	 * 
	 * @param ip
	 *            The image
	 * @param types
	 *            A byte image, same size as ip, where the points can be marked as EXCLUDED
	 * @param statsMode
	 *            OPTION_STATS_INSIDE or OPTION_STATS_OUTSIDE
	 * @return The image histogram
	 */
	private int[] buildHistogram(ImagePlus imp, int[] image, byte[] types, int statsMode)
	{
		// Just in case the image is not 8 or 16-bit
		final int bitDepth = imp.getBitDepth();
		if (bitDepth != 8 && bitDepth != 16)
			return imp.getProcessor().getHistogram();

		final int size = (int) Math.pow(2, bitDepth);

		final int[] data = new int[size];

		if (statsMode == OPTION_STATS_INSIDE)
		{
			for (int i = image.length; i-- > 0;)
			{
				if ((types[i] & EXCLUDED) == 0)
				{
					data[image[i]]++;
				}
			}
		}
		else
		{
			for (int i = image.length; i-- > 0;)
			{
				if ((types[i] & EXCLUDED) != 0)
				{
					data[image[i]]++;
				}
			}
		}

		return data;
	}

	/**
	 * Build a histogram using all pixels
	 * 
	 * @param image
	 *            The image
	 * @return The image histogram
	 */
	private int[] buildHistogram(ImagePlus imp, int[] image)
	{
		// Just in case the image is not 8 or 16-bit
		final int bitDepth = imp.getBitDepth();
		if (bitDepth != 8 && bitDepth != 16)
			return imp.getProcessor().getHistogram();

		final int size = (int) Math.pow(2, bitDepth);

		final int[] data = new int[size];

		for (int i = image.length; i-- > 0;)
		{
			data[image[i]]++;
		}

		return data;
	}

	private double getIntensityAboveBackground(int[] image, byte[] types, int background)
	{
		long sum = 0;
		for (int i = image.length; i-- > 0;)
		{
			if ((types[i] & EXCLUDED) == 0 && image[i] > background)
			{
				sum += (image[i] - background);
			}
		}

		return (double) sum;
	}

	/**
	 * Return the image statistics
	 * 
	 * @param hist
	 *            The image histogram
	 * @return Array containing: min, max, av, stdDev
	 */
	private double[] getStatistics(int[] hist)
	{
		// Get the limits
		int min = 0;
		int max = hist.length - 1;
		while ((hist[min] == 0) && (min < max))
			min++;

		// Check for an empty histogram
		if (min == max && hist[max] == 0)
		{
			return new double[11];
		}

		while ((hist[max] == 0) && (max > min))
			max--;

		// Get the average
		int count;
		double value;
		double sum = 0.0;
		double sum2 = 0.0;
		long n = 0;
		for (int i = min; i <= max; i++)
		{
			if (hist[i] > 0)
			{
				count = hist[i];
				n += count;
				value = i;
				sum += value * count;
				sum2 += (value * value) * count;
			}
		}
		double av = sum / n;

		// Get the Std.Dev
		double stdDev;
		if (n > 0)
		{
			double d = n;
			stdDev = (d * sum2 - sum * sum) / d;
			if (stdDev > 0.0)
				stdDev = Math.sqrt(stdDev / (d - 1.0));
			else
				stdDev = 0.0;
		}
		else
			stdDev = 0.0;

		return new double[] { min, max, av, stdDev, sum, 0, 0, min, max, av, stdDev };
	}

	/**
	 * Get the threshold for searching for maxima
	 * 
	 * @param backgroundMethod
	 *            The background thresholding method
	 * @param backgroundParameter
	 *            The method thresholding parameter
	 * @param stats
	 *            The image statistics
	 * @return The threshold
	 */
	private int getSearchThreshold(int backgroundMethod, double backgroundParameter, double[] stats)
	{
		switch (backgroundMethod)
		{
			case BACKGROUND_ABSOLUTE:
				// Ensure all points above the threshold parameter are found
				return round((backgroundParameter >= 0) ? backgroundParameter : 0);

			case BACKGROUND_AUTO_THRESHOLD:
				return round(stats[STATS_BACKGROUND]);

			case BACKGROUND_MEAN:
				return round(stats[STATS_AV_BACKGROUND]);

			case BACKGROUND_STD_DEV_ABOVE_MEAN:
				return round(stats[STATS_AV_BACKGROUND] +
						((backgroundParameter >= 0) ? backgroundParameter * stats[STATS_SD_BACKGROUND] : 0));

			case BACKGROUND_MIN_ROI:
				return round(stats[STATS_MIN]);

			case BACKGROUND_NONE:
			default:
				// Ensure all the maxima are found
				return 0;
		}
	}

	private int round(double d)
	{
		return (int) Math.round(d);
	}

	/**
	 * Get the threshold that limits the maxima region growing
	 * 
	 * @param searchMethod
	 *            The thresholding method
	 * @param searchParameter
	 *            The method thresholding parameter
	 * @param stats
	 *            The image statistics
	 * @param v0
	 *            The current maxima value
	 * @return The threshold
	 */
	private int getTolerance(int searchMethod, double searchParameter, double[] stats, int v0)
	{
		switch (searchMethod)
		{
			case SEARCH_ABOVE_BACKGROUND:
				return round(stats[STATS_BACKGROUND]);

			case SEARCH_FRACTION_OF_PEAK_MINUS_BACKGROUND:
				if (searchParameter < 0)
					searchParameter = 0;
				return round(stats[STATS_BACKGROUND] + searchParameter * (v0 - stats[STATS_BACKGROUND]));

			case SEARCH_HALF_PEAK_VALUE:
				return round(stats[STATS_BACKGROUND] + 0.5 * (v0 - stats[STATS_BACKGROUND]));
		}
		return 0;
	}

	/**
	 * Get the minimum height for this peak above the highest saddle point
	 * 
	 * @param peakMethod
	 *            The method
	 * @param peakParameter
	 *            The method parameter
	 * @param stats
	 *            The image statistics
	 * @param v0
	 *            The current maxima value
	 * @return The minimum height
	 */
	private int getPeakHeight(int peakMethod, double peakParameter, double[] stats, int v0)
	{
		int height = 1;
		if (peakParameter < 0)
			peakParameter = 0;
		switch (peakMethod)
		{
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
		if (height < 1)
			height = 1; // It should be a peak
		return height;
	}

	/**
	 * Find all local maxima (irrespective whether they finally qualify as maxima or not).
	 *
	 * @param image
	 *            The image to be analyzed
	 * @param maxima
	 *            the maxima
	 * @param types
	 *            A byte image, same size as ip, where the maximum points are marked as MAXIMUM
	 * @param globalMin
	 *            The image global minimum
	 * @param threshold
	 *            The threshold below which no pixels are processed.
	 * @return Maxima sorted by value.
	 */
	/**
	 * @param image
	 * @param types
	 * @param threshold
	 * @return
	 */
	private Coordinate[] getSortedMaxPoints(int[] image, int[] maxima, byte[] types, int globalMin, int threshold)
	{
		ArrayList<Coordinate> maxPoints = new ArrayList<Coordinate>(500);
		int[] pList = null; // working list for expanding local plateaus

		int id = 0;
		final int[] xyz = new int[3];

		//int pCount = 0;

		for (int i = image.length; i-- > 0;)
		{
			if ((types[i] & (EXCLUDED | MAX_AREA | PLATEAU)) != 0)
				continue;
			final int v = image[i];
			if (v < threshold)
				continue;
			if (v == globalMin)
				continue;

			getXYZ(i, xyz);

			final int x = xyz[0];
			final int y = xyz[1];
			final int z = xyz[2];

			/*
			 * check whether we have a local maximum.
			 */
			final boolean isInnerXY = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);
			final boolean isInnerXYZ = (zlimit == 0) ? isInnerXY : isInnerXY && (z != 0 && z != zlimit);
			boolean isMax = true, equalNeighbour = false;

			// It is more likely that the z stack will be out-of-bounds.
			// Adopt the xy limit lookup and process z lookup separately

			for (int d = dStart; d-- > 0;)
			{
				if (isInnerXYZ || (isInnerXY && isWithinZ(z, d)) || isWithinXYZ(x, y, z, d))
				{
					final int vNeighbor = image[i + offset[d]];
					if (vNeighbor > v)
					{
						isMax = false;
						break;
					}
					else if (vNeighbor == v)
					{
						// Neighbour is equal, this is a potential plateau maximum
						equalNeighbour = true;
					}
				}
			}

			if (isMax)
			{
				id++;
				if (id >= searchCapacity)
				{
					IJ.log("The number of potential maxima exceeds the search capacity: " + searchCapacity +
							". Try using a denoising/smoothing filter or increase the capacity.");
					return null;
				}

				if (equalNeighbour)
				{
					// Initialise the working list
					if (pList == null)
					{
						// Create an array to hold the rest of the points (worst case scenario for the maxima expansion)
						pList = new int[i + 1];
					}
					//pCount++;

					// Search the local area marking all equal neighbour points as maximum
					if (!expandMaximum(image, maxima, types, globalMin, threshold, i, v, id, maxPoints, pList))
					{
						// Not a true maximum, ignore this
						id--;
					}
				}
				else
				{
					types[i] |= MAXIMUM | MAX_AREA;
					maxima[i] = id;
					maxPoints.add(new Coordinate(x, y, z, id, v));
				}
			}
		}

		//if (pCount > 0)
		//	System.out.printf("Plateau count = %d\n", pCount);

		if (Utils.isInterrupted())
			return null;

		Collections.sort(maxPoints);

		// Build a map between the original id and the new id following the sort
		final int[] idMap = new int[maxPoints.size() + 1];

		// Label the points
		for (int i = 0; i < maxPoints.size(); i++)
		{
			final int newId = (i + 1);
			final int oldId = maxPoints.get(i).id;
			idMap[oldId] = newId;
			maxPoints.get(i).id = newId;
		}

		reassignMaxima(maxima, idMap);

		return maxPoints.toArray(new Coordinate[0]);
	} // getSortedMaxPoints

	/**
	 * Searches from the specified point to find all coordinates of the same value and determines the centre of the
	 * plateau maximum.
	 * 
	 * @param image
	 * @param maxima
	 * @param types
	 * @param globalMin
	 * @param threshold
	 * @param index0
	 * @param x0
	 * @param y0
	 * @param z0
	 * @param v0
	 * @param id
	 * @param maxPoints
	 * @param pList
	 * @return True if this is a true plateau, false if the plateau reaches a higher point
	 */
	private boolean expandMaximum(int[] image, int[] maxima, byte[] types, int globalMin, int threshold, int index0,
			int v0, int id, ArrayList<Coordinate> maxPoints, int[] pList)
	{
		types[index0] |= LISTED | PLATEAU; // mark first point as listed
		int listI = 0; // index of current search element in the list
		int listLen = 1; // number of elements in the list

		// we create a list of connected points and start the list at the current maximum
		pList[listI] = index0;

		// Calculate the center of plateau
		boolean isPlateau = true;
		final int[] xyz = new int[3];

		do
		{
			final int index1 = pList[listI];
			getXYZ(index1, xyz);
			final int x1 = xyz[0];
			final int y1 = xyz[1];
			final int z1 = xyz[2];

			// It is more likely that the z stack will be out-of-bounds.
			// Adopt the xy limit lookup and process z lookup separately

			final boolean isInnerXY = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);
			final boolean isInnerXYZ = (zlimit == 0) ? isInnerXY : isInnerXY && (z1 != 0 && z1 != zlimit);

			for (int d = dStart; d-- > 0;)
			{
				if (isInnerXYZ || (isInnerXY && isWithinZ(z1, d)) || isWithinXYZ(x1, y1, z1, d))
				{
					final int index2 = index1 + offset[d];
					if ((types[index2] & IGNORE) != 0)
					{
						// This has been done already, ignore this point
						continue;
					}

					final int v2 = image[index2];

					if (v2 > v0)
					{
						isPlateau = false;
						//break; // Cannot break as we want to label the entire plateau.
					}
					else if (v2 == v0)
					{
						// Add this to the search
						pList[listLen++] = index2;
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
		if (isPlateau)
		{
			for (int i = listLen; i-- > 0;)
			{
				getXYZ(pList[i], xyz);
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
		for (int i = listLen; i-- > 0;)
		{
			final int index = pList[i];
			types[index] &= ~LISTED; // reset attributes no longer needed

			if (isPlateau)
			{
				getXYZ(index, xyz);

				final int x = xyz[0];
				final int y = xyz[1];
				final int z = xyz[2];

				final double d = (xEqual - x) * (xEqual - x) + (yEqual - y) * (yEqual - y) +
						(zEqual - z) * (zEqual - z);

				if (d < dMax)
				{
					dMax = d;
					iMax = i;
				}

				types[index] |= MAX_AREA;
				maxima[index] = id;
			}
		}

		// Assign the maximum
		if (isPlateau)
		{
			final int index = pList[iMax];
			types[index] |= MAXIMUM;
			maxPoints.add(new Coordinate(index, id, v0));
		}

		return isPlateau;
	}

	/**
	 * Initialises the maxima image using the maxima Id for each point
	 * 
	 * @param maxima
	 * @param maxPoints
	 */
	private void assignMaxima(int[] maxima, Coordinate[] maxPoints, ArrayList<int[]> resultsArray)
	{
		final int[] xyz = new int[3];

		for (Coordinate maximum : maxPoints)
		{
			getXYZ(maximum.index, xyz);

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
	 * Assigns points to their maxima using the steepest uphill gradient. Processes points in order of height,
	 * progressively building peaks in a top-down fashion.
	 */
	private void assignPointsToMaxima(int[] image, int[] histogram, byte[] types, double[] stats, int[] maxima)
	{
		final int background = round(stats[STATS_BACKGROUND]);
		final int maxValue = round(stats[STATS_MAX]);

		// Create an array with the coordinates of all points between the threshold value and the max-1 value
		int arraySize = 0;
		for (int v = background; v < maxValue; v++)
			arraySize += histogram[v];

		if (arraySize == 0)
			return;

		final int[] coordinates = new int[arraySize]; // from pixel coordinates, low bits x, high bits y
		int highestValue = 0;
		int offset = 0;
		final int[] levelStart = new int[maxValue + 1];
		for (int v = background; v < maxValue; v++)
		{
			levelStart[v] = offset;
			offset += histogram[v];
			if (histogram[v] > 0)
				highestValue = v;
		}
		final int[] levelOffset = new int[highestValue + 1];
		for (int i = image.length; i-- > 0;)
		{
			if ((types[i] & EXCLUDED) != 0)
				continue;

			final int v = image[i];
			if (v >= background && v < maxValue)
			{
				offset = levelStart[v] + levelOffset[v];
				coordinates[offset] = i;
				levelOffset[v]++;
			}
		}

		// Process down through the levels
		int processedLevel = 0; // Counter incremented when work is done
		//int levels = 0;
		for (int level = highestValue; level >= background; level--)
		{
			int remaining = histogram[level];

			if (remaining == 0)
			{
				continue;
			}
			//levels++;

			// Use the idle counter to ensure that we exit the loop if no pixels have been processed for two cycles
			while (remaining > 0)
			{
				processedLevel++;
				final int n = processLevel(image, types, maxima, levelStart[level], remaining, coordinates, background);
				remaining -= n; // number of points processed

				// If nothing was done then stop
				if (n == 0)
					break;
			}

			if ((processedLevel % 64 == 0) && Utils.isInterrupted())
				return;

			if (remaining > 0 && level > background)
			{
				// any pixels that we have not reached?
				// It could happen if there is a large area of flat pixels => no local maxima.
				// Add to the next level.
				//IJ.log("Unprocessed " + remaining + " @level = " + level);

				int nextLevel = level; // find the next level to process
				do
					nextLevel--;
				while (nextLevel > 1 && histogram[nextLevel] == 0);

				// Add all unprocessed pixels of this level to the tasklist of the next level.
				// This could make it slow for some images, however.
				if (nextLevel > 0)
				{
					int newNextLevelEnd = levelStart[nextLevel] + histogram[nextLevel];
					for (int i = 0, p = levelStart[level]; i < remaining; i++, p++)
					{
						int index = coordinates[p];
						coordinates[newNextLevelEnd++] = index;
					}
					// tasklist for the next level to process becomes longer by this:
					histogram[nextLevel] = newNextLevelEnd - levelStart[nextLevel];
				}
			}
		}

		//int nP = 0;
		//for (byte b : types)
		//	if ((b & PLATEAU) == PLATEAU)
		//		nP++;

		//IJ.log(String.format("Processed %d levels [%d steps], %d plateau points", levels, processedLevel, nP));
	}

	/**
	 * Processes points in order of height, progressively building peaks in a top-down fashion.
	 * 
	 * @param image
	 *            the input image
	 * @param types
	 *            The image pixel types
	 * @param maxima
	 *            The image maxima
	 * @param levelStart
	 *            offsets of the level in pixelPointers[]
	 * @param levelNPoints
	 *            number of points in the current level
	 * @param coordinates
	 *            list of xyz coordinates (should be offset by levelStart)
	 * @param background
	 *            The background intensity
	 * @return number of pixels that have been changed
	 */
	private int processLevel(int[] image, byte[] types, int[] maxima, int levelStart, int levelNPoints,
			int[] coordinates, int background)
	{
		//int[] pList = new int[0]; // working list for expanding local plateaus
		int nChanged = 0;
		int nUnchanged = 0;
		final int[] xyz = new int[3];

		for (int i = 0, p = levelStart; i < levelNPoints; i++, p++)
		{
			final int index = coordinates[p];

			if ((types[index] & (EXCLUDED | MAX_AREA)) != 0)
			{
				// This point can be ignored
				nChanged++;
				continue;
			}

			getXYZ(index, xyz);

			// Extract the point coordinate
			final int x = xyz[0];
			final int y = xyz[1];
			final int z = xyz[2];

			final int v = image[index];

			// It is more likely that the z stack will be out-of-bounds.
			// Adopt the xy limit lookup and process z lookup separately
			final boolean isInnerXY = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);
			final boolean isInnerXYZ = (zlimit == 0) ? isInnerXY : isInnerXY && (z != 0 && z != zlimit);

			// Check for the highest neighbour

			// TODO - Try out using a Sobel operator to assign the gradient direction. Follow the steepest gradient.

			int dMax = -1;
			int vMax = v;
			for (int d = dStart; d-- > 0;)
			{
				if (isInnerXYZ || (isInnerXY && isWithinZ(z, d)) || isWithinXYZ(x, y, z, d))
				{
					final int index2 = index + offset[d];
					final int vNeighbor = image[index2];
					if (vMax < vNeighbor) // Higher neighbour
					{
						vMax = vNeighbor;
						dMax = d;
					}
					else if (vMax == vNeighbor) // Equal neighbour
					{
						// Check if the neighbour is higher than this point (i.e. an equal higher neighbour has been found)
						if (v != vNeighbor)
						{
							// Favour flat edges over diagonals in the case of equal neighbours
							if (flatEdge[d])
							{
								dMax = d;
							}
						}
						// The neighbour is the same height, check if it is a maxima
						else if ((types[index2] & MAX_AREA) != 0)
						{
							if (dMax < 0) // Unassigned
							{
								dMax = d;
							}
							// Favour flat edges over diagonals in the case of equal neighbours
							else if (flatEdge[d])
							{
								dMax = d;
							}
						}
					}
				}
			}

			if (dMax < 0)
			{
				// This could happen if all neighbours are the same height and none are maxima.
				// Since plateau maxima should be handled in the initial maximum finding stage, any equal neighbours
				// should be processed eventually.
				coordinates[levelStart + (nUnchanged++)] = index;
				continue;
			}

			int index2 = index + offset[dMax];

			// TODO. 
			// The code below can be uncommented to flood fill a plateau with the first maxima that touches it.
			// However this can lead to striping artifacts where diagonals are at the same level but 
			// adjacent cells are not, e.g:
			// 1122
			// 1212
			// 2122
			// 1222
			// Consequently the code has been commented out and the default behaviour fills plateaus from the
			// edges inwards with a bias in the direction of the sweep across the pixels.
			// A better method may be to assign pixels to the nearest maxima using a distance measure 
			// (Euclidian, City-Block, etc). This would involve:
			// - Mark all plateau edges that touch a maxima 
			// - for each maxima edge:
			// -- Measure distance for each plateau point to the nearest touching edge
			// - Compare distance maps for each maxima and assign points to nearest maxima

			// Flood fill
			//          // A higher point has been found. Check if this position is a plateau
			//if ((types[index] & PLATEAU) == PLATEAU)
			//{
			//	IJ.log(String.format("Plateau merge to higher level: %d @ [%d,%d] : %d", image[index], x, y,
			//			image[index2]));
			//
			//	// Initialise the list to allow all points on this level to be processed. 
			//	if (pList.length < levelNPoints)
			//	{
			//		pList = new int[levelNPoints];
			//	}
			//
			//	expandPlateau(image, maxima, types, index, v, maxima[index2], pList);
			//}
			//else
			{
				types[index] |= MAX_AREA;
				maxima[index] = maxima[index2];
				nChanged++;
			}
		} // for pixel i

		//if (nUnchanged > 0)
		//	System.out.printf("nUnchanged = %d\n", nUnchanged);

		return nChanged;
	}// processLevel

	/**
	 * Searches from the specified point to find all coordinates of the same value and assigns them to given maximum.
	 */
	@SuppressWarnings("unused")
	private void expandPlateau(int[] image, int[] maxima, byte[] types, int index0, int v0, int id, int[] pList)
	{
		types[index0] |= LISTED; // mark first point as listed
		int listI = 0; // index of current search element in the list
		int listLen = 1; // number of elements in the list

		// we create a list of connected points and start the list at the current maximum
		pList[listI] = index0;

		final int[] xyz = new int[3];

		do
		{
			final int index1 = pList[listI];
			getXYZ(index1, xyz);
			final int x1 = xyz[0];
			final int y1 = xyz[1];
			final int z1 = xyz[2];

			// It is more likely that the z stack will be out-of-bounds.
			// Adopt the xy limit lookup and process z lookup separately

			final boolean isInnerXY = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);
			final boolean isInnerXYZ = (zlimit == 0) ? isInnerXY : isInnerXY && (z1 != 0 && z1 != zlimit);

			for (int d = dStart; d-- > 0;)
			{
				if (isInnerXYZ || (isInnerXY && isWithinZ(z1, d)) || isWithinXYZ(x1, y1, z1, d))
				{
					final int index2 = index1 + offset[d];
					if ((types[index2] & IGNORE) != 0)
					{
						// This has been done already, ignore this point
						continue;
					}

					final int v2 = image[index2];

					if (v2 == v0)
					{
						// Add this to the search
						pList[listLen++] = index2;
						types[index2] |= LISTED;
					}
				}
			}

			listI++;

		} while (listI < listLen);

		//IJ.log("Plateau size = "+listLen);

		for (int i = listLen; i-- > 0;)
		{
			final int index = pList[i];
			types[index] &= ~LISTED; // reset attributes no longer needed

			// Assign to the given maximum
			types[index] |= MAX_AREA;
			maxima[index] = id;
		}
	}

	/**
	 * Loop over all points that have been assigned to a peak area and clear any pixels below the peak growth threshold
	 * 
	 * @param image
	 * @param roiBounds
	 * @param types
	 * @param maxPoints
	 * @param searchMethod
	 * @param searchParameter
	 * @param stats
	 * @param resultsArray
	 * @param maxima
	 */
	private void pruneMaxima(int[] image, byte[] types, int searchMethod, double searchParameter, double[] stats,
			ArrayList<int[]> resultsArray, int[] maxima)
	{
		// Build an array containing the threshold for each peak.
		// Note that maxima are numbered from 1
		final int nMaxima = resultsArray.size();
		final int[] peakThreshold = new int[nMaxima + 1];
		for (int i = 1; i < peakThreshold.length; i++)
		{
			final int v0 = resultsArray.get(i - 1)[RESULT_MAX_VALUE];
			peakThreshold[i] = getTolerance(searchMethod, searchParameter, stats, v0);
		}

		for (int i = image.length; i-- > 0;)
		{
			if (maxima[i] > 0)
			{
				if (image[i] < peakThreshold[maxima[i]])
				{
					// Unset this pixel as part of the peak
					maxima[i] = 0;
					types[i] &= ~MAX_AREA;
				}
			}
		}
	}

	/**
	 * Loop over the image and sum the intensity and size of each peak area, storing this into the results array
	 * 
	 * @param image
	 * @param roi
	 * @param maxima
	 * @param resultsArray
	 */
	private void calculateInitialResults(int[] image, int[] maxima, ArrayList<int[]> resultsArray)
	{
		final int nMaxima = resultsArray.size();

		// Maxima are numbered from 1
		final int[] count = new int[nMaxima + 1];
		final int[] intensity = new int[nMaxima + 1];

		for (int i = maxima.length; i-- > 0;)
		{
			if (maxima[i] > 0)
			{
				count[maxima[i]]++;
				intensity[maxima[i]] += image[i];
			}
		}

		for (int[] result : resultsArray)
		{
			result[RESULT_COUNT] = count[result[RESULT_PEAK_ID]];
			result[RESULT_INTENSITY] = intensity[result[RESULT_PEAK_ID]];
			result[RESULT_AVERAGE_INTENSITY] = result[RESULT_INTENSITY] / result[RESULT_COUNT];
		}
	}

	/**
	 * Loop over the image and sum the intensity of each peak area using the original image, storing this into the
	 * results array.
	 */
	private void calculateNativeResults(int[] image, int[] maxima, ArrayList<int[]> resultsArray,
			int originalNumberOfPeaks)
	{
		// Maxima are numbered from 1
		final int[] intensity = new int[originalNumberOfPeaks + 1];
		final int[] max = new int[originalNumberOfPeaks + 1];

		for (int i = maxima.length; i-- > 0;)
		{
			if (maxima[i] > 0)
			{
				intensity[maxima[i]] += image[i];
				if (max[maxima[i]] < image[i])
					max[maxima[i]] = image[i];
			}
		}

		for (int[] result : resultsArray)
		{
			if (intensity[result[RESULT_PEAK_ID]] > 0)
			{
				result[RESULT_INTENSITY] = intensity[result[RESULT_PEAK_ID]];
				result[RESULT_MAX_VALUE] = max[result[RESULT_PEAK_ID]];
			}
		}
	}

	/**
	 * Calculate the peaks centre and maximum value. This could be done in many ways:
	 * - Max value
	 * - Centre-of-mass (within a bounding box of max value defined by the centreParameter)
	 * - Gaussian fit (Using a 2D projection defined by the centreParameter: (1) Maximum value; (other) Average value)
	 * 
	 * @param image
	 * @param maxima
	 * @param types
	 * @param resultsArray
	 * @param originalNumberOfPeaks
	 * @param centreMethod
	 * @param centreParameter
	 */
	private void locateMaxima(int[] image, int[] searchImage, int[] maxima, byte[] types, ArrayList<int[]> resultsArray,
			int originalNumberOfPeaks, int centreMethod, double centreParameter)
	{
		if (centreMethod == CENTRE_MAX_VALUE_SEARCH)
		{
			return; // This is the current value so just return
		}

		// Swap to the search image for processing if necessary
		switch (centreMethod)
		{
			case CENTRE_GAUSSIAN_SEARCH:
			case CENTRE_OF_MASS_SEARCH:
			case CENTRE_MAX_VALUE_SEARCH:
				image = searchImage;
		}

		// Working list of peak coordinates 
		int[] pList = new int[0];

		// For each peak, compute the centre
		for (int[] result : resultsArray)
		{
			// Ensure list is large enough
			if (pList.length < result[RESULT_COUNT])
				pList = new int[result[RESULT_COUNT]];

			// Find the peak coords above the saddle
			final int maximaId = result[RESULT_PEAK_ID];
			final int index = getIndex(result[RESULT_X], result[RESULT_Y], result[RESULT_Z]);
			final int listLen = findMaximaCoords(image, maxima, types, index, maximaId,
					result[RESULT_HIGHEST_SADDLE_VALUE], pList);
			//IJ.log("maxima size > saddle = " + listLen);

			// Find the boundaries of the coordinates
			final int[] min_xyz = new int[] { maxx, maxy, maxz };
			final int[] max_xyz = new int[] { 0, 0, 0 };
			final int[] xyz = new int[3];
			for (int listI = listLen; listI-- > 0;)
			{
				final int index1 = pList[listI];
				getXYZ(index1, xyz);
				for (int i = 3; i-- > 0;)
				{
					if (min_xyz[i] > xyz[i])
						min_xyz[i] = xyz[i];
					if (max_xyz[i] < xyz[i])
						max_xyz[i] = xyz[i];
				}
			}
			//IJ.log("Boundaries " + maximaId + " : " + min_xyz[0] + "," + min_xyz[1] + "," + min_xyz[2] + " => " +
			//		max_xyz[0] + "," + max_xyz[1] + "," + max_xyz[2]);

			// Extract sub image
			final int[] dimensions = new int[3];
			for (int i = 3; i-- > 0;)
				dimensions[i] = max_xyz[i] - min_xyz[i] + 1;

			final int[] subImage = extractSubImage(image, maxima, min_xyz, dimensions, maximaId,
					result[RESULT_HIGHEST_SADDLE_VALUE]);

			int[] centre = null;
			switch (centreMethod)
			{
				case CENTRE_GAUSSIAN_SEARCH:
				case CENTRE_GAUSSIAN_ORIGINAL:
					centre = findCentreGaussianFit(subImage, dimensions, round(centreParameter));
					//					if (centre == null)
					//					{
					//						if (IJ.debugMode)
					//						{
					//							IJ.log("No Gaussian fit");
					//						}
					//					}
					break;

				case CENTRE_OF_MASS_SEARCH:
				case CENTRE_OF_MASS_ORIGINAL:
					centre = findCentreOfMass(subImage, dimensions, round(centreParameter));
					break;

				case CENTRE_MAX_VALUE_ORIGINAL:
				default:
					centre = findCentreMaxValue(subImage, dimensions);
			}

			if (centre != null)
			{
				if (IJ.debugMode)
				{
					final int[] shift = new int[3];
					double d = 0;
					for (int i = 3; i-- > 0;)
					{
						shift[i] = result[i] - (centre[i] + min_xyz[i]);
						d += shift[i] * shift[i];
					}
					IJ.log("Moved centre: " + shift[0] + " , " + shift[1] + " , " + shift[2] + " = " +
							IJ.d2s(Math.sqrt(d), 2));
				}

				// RESULT_[XYZ] are 0, 1, 2
				for (int i = 3; i-- > 0;)
					result[i] = centre[i] + min_xyz[i];
			}
		}
	}

	/**
	 * Search for all connected points in the maxima above the saddle value.
	 * 
	 * @return The number of points
	 */
	private int findMaximaCoords(int[] image, int[] maxima, byte[] types, int index0, int maximaId, int saddleValue,
			int[] pList)
	{
		types[index0] |= LISTED; // mark first point as listed
		int listI = 0; // index of current search element in the list
		int listLen = 1; // number of elements in the list

		// we create a list of connected points and start the list at the current maximum
		pList[listI] = index0;

		final int[] xyz = new int[3];

		do
		{
			final int index1 = pList[listI];
			getXYZ(index1, xyz);
			final int x1 = xyz[0];
			final int y1 = xyz[1];
			final int z1 = xyz[2];

			final boolean isInnerXY = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);
			final boolean isInnerXYZ = (zlimit == 0) ? isInnerXY : isInnerXY && (z1 != 0 && z1 != zlimit);

			for (int d = dStart; d-- > 0;)
			{
				if (isInnerXYZ || (isInnerXY && isWithinZ(z1, d)) || isWithinXYZ(x1, y1, z1, d))
				{
					final int index2 = index1 + offset[d];
					if ((types[index2] & IGNORE) != 0 || maxima[index2] != maximaId)
					{
						// This has been done already, ignore this point
						continue;
					}

					final int v2 = image[index2];

					if (v2 >= saddleValue)
					{
						// Add this to the search
						pList[listLen++] = index2;
						types[index2] |= LISTED;
					}
				}
			}

			listI++;

		} while (listI < listLen);

		for (int i = listLen; i-- > 0;)
		{
			final int index = pList[i];
			types[index] &= ~LISTED; // reset attributes no longer needed
		}

		return listLen;
	}

	/**
	 * Extract a sub-image from the given input image using the specified boundaries. The minValue is subtracted from
	 * all pixels. All pixels below the minValue are ignored (set to zero).
	 * 
	 * @param result
	 * @param maxima
	 */
	private int[] extractSubImage(int[] image, int[] maxima, int[] min_xyz, int[] dimensions, int maximaId,
			int minValue)
	{
		final int[] subImage = new int[dimensions[0] * dimensions[1] * dimensions[2]];

		int offset = 0;
		for (int z = 0; z < dimensions[2]; z++)
		{
			for (int y = 0; y < dimensions[1]; y++)
			{
				int index = getIndex(min_xyz[0], y + min_xyz[1], z + min_xyz[2]);
				for (int x = 0; x < dimensions[0]; x++, index++, offset++)
				{
					if (maxima[index] == maximaId && image[index] > minValue)
						subImage[offset] = image[index] - minValue;
				}
			}
		}

		// DEBUGGING
		//		ImageProcessor ip = new ShortProcessor(dimensions[0], dimensions[1]);
		//		for (int i = subImage.length; i-- > 0;)
		//			ip.set(i, subImage[i]);
		//		new ImagePlus(null, ip).show();

		return subImage;
	}

	/**
	 * Finds the centre of the image using the maximum pixel value.
	 * If many pixels have the same value the closest pixel to the geometric mean of the coordinates is returned.
	 */
	private int[] findCentreMaxValue(int[] image, int[] dimensions)
	{
		// Find the maximum value in the image
		int maxValue = 0;
		int count = 0;
		int index = 0;
		for (int i = image.length; i-- > 0;)
		{
			if (maxValue < image[i])
			{
				maxValue = image[i];
				index = i;
				count = 1;
			}
			else if (maxValue == image[i])
			{
				count++;
			}
		}

		// Used to map index back to XYZ
		final int blockSize = dimensions[0] * dimensions[1];

		if (count == 1)
		{
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
		for (int i = image.length; i-- > 0;)
		{
			if (maxValue == image[i])
			{
				final int[] xyz = new int[3];
				xyz[2] = i / (blockSize);
				final int mod = i % (blockSize);
				xyz[1] = mod / dimensions[0];
				xyz[0] = mod % dimensions[0];
				for (int j = 3; j-- > 0;)
					centre[j] += xyz[j];
			}
		}
		for (int j = 3; j-- > 0;)
			centre[j] /= count;

		// Find nearest point
		double dMin = Double.MAX_VALUE;
		int[] closest = new int[] { round(centre[0]), round(centre[1]), round(centre[2]) };
		for (int i = image.length; i-- > 0;)
		{
			if (maxValue == image[i])
			{
				int[] xyz = new int[3];
				xyz[2] = i / (blockSize);
				int mod = i % (blockSize);
				xyz[1] = mod / dimensions[0];
				xyz[0] = mod % dimensions[0];
				double d = Math.pow(xyz[0] - centre[0], 2) + Math.pow(xyz[1] - centre[1], 2) +
						Math.pow(xyz[2] - centre[2], 2);
				if (dMin > d)
				{
					dMin = d;
					closest = xyz;
				}
			}
		}

		return closest;
	}

	/**
	 * Finds the centre of the image using the centre of mass within the given range of the maximum pixel value.
	 */
	private int[] findCentreOfMass(int[] subImage, int[] dimensions, int range)
	{
		final int[] centre = findCentreMaxValue(subImage, dimensions);
		double[] com = new double[] { centre[0], centre[1], centre[2] };

		// Iterate until convergence
		double distance;
		int iter = 0;
		do
		{
			final double[] newCom = findCentreOfMass(subImage, dimensions, range, com);
			distance = Math.pow(newCom[0] - com[0], 2) + Math.pow(newCom[1] - com[1], 2) +
					Math.pow(newCom[2] - com[2], 2);
			com = newCom;
			iter++;
		} while (distance > 1 && iter < 10);

		return convertCentre(com);
	}

	/**
	 * Finds the centre of the image using the centre of mass within the given range of the specified centre-of-mass.
	 */
	private double[] findCentreOfMass(int[] subImage, int[] dimensions, int range, double[] com)
	{
		final int[] centre = convertCentre(com);

		final int[] min = new int[3];
		final int[] max = new int[3];
		if (range < 1)
			range = 1;
		for (int i = 3; i-- > 0;)
		{
			min[i] = centre[i] - range;
			max[i] = centre[i] + range;
			if (min[i] < 0)
				min[i] = 0;
			if (max[i] >= dimensions[i] - 1)
				max[i] = dimensions[i] - 1;
		}

		final int blockSize = dimensions[0] * dimensions[1];

		final double[] newCom = new double[3];
		long sum = 0;
		for (int z = min[2]; z <= max[2]; z++)
		{
			for (int y = min[1]; y <= max[1]; y++)
			{
				int index = blockSize * z + dimensions[0] * y + min[0];
				for (int x = min[0]; x <= max[0]; x++, index++)
				{
					final int value = subImage[index];
					if (value > 0)
					{
						sum += value;
						newCom[0] += x * value;
						newCom[1] += y * value;
						newCom[2] += z * value;
					}
				}
			}
		}

		for (int i = 3; i-- > 0;)
		{
			newCom[i] /= sum;
		}

		return newCom;
	}

	/**
	 * Finds the centre of the image using a 2D Gaussian fit to projection along the Z-axis.
	 * 
	 * @param subImage
	 * @param dimensions
	 * @param projectionMethod
	 *            (0) Average value; (1) Maximum value
	 * @return
	 */
	private int[] findCentreGaussianFit(int[] subImage, int[] dimensions, int projectionMethod)
	{
		if (isGaussianFitEnabled < 1)
			return null;

		final int blockSize = dimensions[0] * dimensions[1];
		final float[] projection = new float[blockSize];

		if (projectionMethod == 1)
		{
			// Maximum value
			for (int z = dimensions[2]; z-- > 0;)
			{
				int index = blockSize * z;
				for (int i = 0; i < blockSize; i++, index++)
				{
					if (projection[i] < subImage[index])
						projection[i] = subImage[index];
				}
			}
		}
		else
		{
			// Average value
			for (int z = dimensions[2]; z-- > 0;)
			{
				int index = blockSize * z;
				for (int i = 0; i < blockSize; i++, index++)
					projection[i] += subImage[index];
			}
			for (int i = blockSize; i-- > 0;)
				projection[i] /= dimensions[2];
		}

		final GaussianFit gf = new GaussianFit();
		final double[] fitParams = gf.fit(projection, dimensions[0], dimensions[1]);

		int[] centre = null;
		if (fitParams != null)
		{
			// Find the centre of mass along the z-axis
			centre = convertCentre(new double[] { fitParams[2], fitParams[3] });

			// Use the centre of mass along the projection axis
			double com = 0;
			long sum = 0;
			for (int z = dimensions[2]; z-- > 0;)
			{
				final int index = blockSize * z;
				final int value = subImage[index];
				if (value > 0)
				{
					com += z * value;
					sum += value;
				}
			}
			centre[2] = round(com / sum);
			// Avoid clipping
			if (centre[2] >= dimensions[2])
				centre[2] = dimensions[2] - 1;
		}

		return centre;
	}

	/**
	 * Convert the centre from double to int. Handles input arrays of length 2 or 3.
	 */
	private int[] convertCentre(double[] centre)
	{
		int[] newCentre = new int[3];
		for (int i = centre.length; i-- > 0;)
		{
			newCentre[i] = round(centre[i]);
		}
		return newCentre;
	}

	/**
	 * Loop over the results array and calculate the average intensity and the intensity above the background
	 * 
	 * @param resultsArray
	 * @param background
	 */
	private void calculateFinalResults(ArrayList<int[]> resultsArray, int background)
	{
		for (int[] result : resultsArray)
		{
			result[RESULT_INTENSITY_MINUS_BACKGROUND] = result[RESULT_INTENSITY] - background * result[RESULT_COUNT];
			result[RESULT_AVERAGE_INTENSITY] = result[RESULT_INTENSITY] / result[RESULT_COUNT];
			result[RESULT_AVERAGE_INTENSITY_MINUS_BACKGROUND] = result[RESULT_INTENSITY_MINUS_BACKGROUND] /
					result[RESULT_COUNT];
		}
	}

	/**
	 * Finds the highest saddle point for each peak
	 * 
	 * @param image
	 * @param types
	 * @param resultsArray
	 * @param maxima
	 * @param saddlePoints
	 *            Contains an entry for each peak indexed from 1. The entry is a linked list of saddle points. Each
	 *            saddle point is an array containing the neighbouring peak ID and the saddle value.
	 */
	private void findSaddlePoints(int[] image, byte[] types, ArrayList<int[]> resultsArray, int[] maxima,
			ArrayList<LinkedList<int[]>> saddlePoints)
	{
		// Initialise the saddle points
		final int nMaxima = resultsArray.size();
		for (int i = 0; i < nMaxima + 1; i++)
			saddlePoints.add(new LinkedList<int[]>());

		final int maxPeakSize = getMaxPeakSize(resultsArray);
		final int[][] pList = new int[maxPeakSize][2]; // here we enter points starting from a maximum (index,value)
		final int[] xyz = new int[3];

		/* Process all the maxima */
		for (int[] result : resultsArray)
		{
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
			pList[0][0] = index0;
			pList[0][1] = v0;

			do
			{
				final int index1 = pList[listI][0];
				final int v1 = pList[listI][1];

				getXYZ(index1, xyz);
				final int x1 = xyz[0];
				final int y1 = xyz[1];
				final int z1 = xyz[2];

				// It is more likely that the z stack will be out-of-bounds.
				// Adopt the xy limit lookup and process z lookup separately

				final boolean isInnerXY = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);
				final boolean isInnerXYZ = (zlimit == 0) ? isInnerXY : isInnerXY && (z1 != 0 && z1 != zlimit);

				// Check for the highest neighbour
				for (int d = dStart; d-- > 0;)
				{
					if (isInnerXYZ || (isInnerXY && isWithinZ(z1, d)) || isWithinXYZ(x1, y1, z1, d))
					{
						// Get the coords
						final int index2 = index1 + offset[d];

						if ((types[index2] & IGNORE) != 0)
						{
							// This has been done already, ignore this point
							continue;
						}

						final int id2 = maxima[index2];

						if (id2 == id)
						{
							// Add this to the search
							pList[listLen][0] = index2;
							pList[listLen][1] = image[index2];
							listLen++;
							types[index2] |= LISTED;
						}
						else if (id2 != 0)
						{
							// This is another peak, see if it a saddle highpoint
							final int v2 = image[index2];

							// Take the lower of the two points as the saddle
							final int minV;
							if (v1 < v2)
							{
								types[index1] |= SADDLE;
								minV = v1;
							}
							else
							{
								types[index2] |= SADDLE;
								minV = v2;
							}

							if (highestSaddleValue[id2] < minV)
							{
								highestSaddleValue[id2] = minV;
							}
						}
					}
				}

				listI++;

			} while (listI < listLen);

			for (int i = listLen; i-- > 0;)
			{
				final int index = pList[i][0];
				types[index] &= ~LISTED; // reset attributes no longer needed
			}

			// Find the highest saddle
			int highestNeighbourPeakId = 0;
			int highestNeighbourValue = 0;
			LinkedList<int[]> saddles = saddlePoints.get(id);
			for (int id2 = 1; id2 <= nMaxima; id2++)
			{
				if (highestSaddleValue[id2] > 0)
				{
					saddles.add(new int[] { id2, highestSaddleValue[id2] });
					// IJ.log("Peak saddle " + id + " -> " + id2 + " @ " + highestSaddleValue[id2]);
					if (highestNeighbourValue < highestSaddleValue[id2])
					{
						highestNeighbourValue = highestSaddleValue[id2];
						highestNeighbourPeakId = id2;
					}
				}
			}

			// Set the saddle point
			if (highestNeighbourPeakId > 0)
			{
				result[RESULT_SADDLE_NEIGHBOUR_ID] = highestNeighbourPeakId;
				result[RESULT_HIGHEST_SADDLE_VALUE] = highestNeighbourValue;
			}
		} // for all maxima
	}

	private int getMaxPeakSize(ArrayList<int[]> resultsArray)
	{
		int maxPeakSize = 0;
		for (int[] result : resultsArray)
		{
			if (maxPeakSize < result[RESULT_COUNT])
				maxPeakSize = result[RESULT_COUNT];
		}
		return maxPeakSize;
	}

	/**
	 * Find the size and intensity of peaks above their saddle heights.
	 */
	private void analysePeaks(ArrayList<int[]> resultsArray, int[] image, int[] maxima, double[] stats)
	{
		// Create an array of the size/intensity of each peak above the highest saddle 
		int[] peakIntensity = new int[resultsArray.size() + 1];
		int[] peakSize = new int[resultsArray.size() + 1];

		// Store all the saddle heights
		int[] saddleHeight = new int[resultsArray.size() + 1];
		for (int[] result : resultsArray)
		{
			saddleHeight[result[RESULT_PEAK_ID]] = result[RESULT_HIGHEST_SADDLE_VALUE];
		}

		for (int i = maxima.length; i-- > 0;)
		{
			if (maxima[i] > 0)
			{
				if (image[i] > saddleHeight[maxima[i]])
				{
					peakIntensity[maxima[i]] += image[i];
					peakSize[maxima[i]]++;
				}
			}
		}

		for (int[] result : resultsArray)
		{
			result[RESULT_COUNT_ABOVE_SADDLE] = peakSize[result[RESULT_PEAK_ID]];
			result[RESULT_INTENSITY_ABOVE_SADDLE] = peakIntensity[result[RESULT_PEAK_ID]];
		}
	}

	/**
	 * Merge sub-peaks into their highest neighbour peak using the highest saddle point
	 */
	private void mergeSubPeaks(ArrayList<int[]> resultsArray, int[] image, int[] maxima, int minSize, int peakMethod,
			double peakParameter, double[] stats, ArrayList<LinkedList<int[]>> saddlePoints, boolean isLogging,
			boolean restrictAboveSaddle)
	{
		// Create an array containing the mapping between the original peak Id and the current Id that the peak has been
		// mapped to.
		final int[] peakIdMap = new int[resultsArray.size() + 1];
		for (int i = 0; i < peakIdMap.length; i++)
			peakIdMap[i] = i;

		// Process all the peaks for the minimum height. Process in order of saddle height
		sortDescResults(resultsArray, SORT_SADDLE_HEIGHT, stats);

		for (int[] result : resultsArray)
		{
			final int peakId = result[RESULT_PEAK_ID];
			final LinkedList<int[]> saddles = saddlePoints.get(peakId);

			// Check if this peak has been reassigned or has no neighbours
			if (peakId != peakIdMap[peakId])
				continue;

			final int[] highestSaddle = findHighestNeighbourSaddle(peakIdMap, saddles, peakId);

			final int peakBase = (highestSaddle == null) ? round(stats[STATS_BACKGROUND]) : highestSaddle[1];

			final int threshold = getPeakHeight(peakMethod, peakParameter, stats, result[RESULT_MAX_VALUE]);

			if (result[RESULT_MAX_VALUE] - peakBase < threshold)
			{
				// This peak is not high enough, merge into the neighbour peak
				if (highestSaddle == null)
				{
					removePeak(image, maxima, peakIdMap, result, peakId);
				}
				else
				{
					// Find the neighbour peak (use the map because the neighbour may have been merged)
					final int neighbourPeakId = peakIdMap[highestSaddle[SADDLE_PEAK_ID]];
					final int[] neighbourResult = findResult(resultsArray, neighbourPeakId);

					mergePeak(image, maxima, peakIdMap, peakId, result, neighbourPeakId, neighbourResult, saddles,
							saddlePoints.get(neighbourPeakId), highestSaddle, false);
				}
			}
		}

		if (isLogging)
			IJ.log("Height filter : Number of peaks = " + countPeaks(peakIdMap));
		if (Utils.isInterrupted())
			return;

		// Process all the peaks for the minimum size. Process in order of smallest first
		sortAscResults(resultsArray, SORT_COUNT, stats);

		for (int[] result : resultsArray)
		{
			final int peakId = result[RESULT_PEAK_ID];

			// Check if this peak has been reassigned
			if (peakId != peakIdMap[peakId])
				continue;

			if (result[RESULT_COUNT] < minSize)
			{
				// This peak is not large enough, merge into the neighbour peak

				final LinkedList<int[]> saddles = saddlePoints.get(peakId);
				final int[] highestSaddle = findHighestNeighbourSaddle(peakIdMap, saddles, peakId);

				if (highestSaddle == null)
				{
					removePeak(image, maxima, peakIdMap, result, peakId);
				}
				else
				{
					// Find the neighbour peak (use the map because the neighbour may have been merged)
					final int neighbourPeakId = peakIdMap[highestSaddle[SADDLE_PEAK_ID]];
					final int[] neighbourResult = findResult(resultsArray, neighbourPeakId);

					mergePeak(image, maxima, peakIdMap, peakId, result, neighbourPeakId, neighbourResult, saddles,
							saddlePoints.get(neighbourPeakId), highestSaddle, false);
				}
			}
		}

		if (isLogging)
			IJ.log("Size filter : Number of peaks = " + countPeaks(peakIdMap));
		if (Utils.isInterrupted())
			return;

		// This can be intensive due to the requirement to recount the peak size above the saddle, so it is optional
		if (restrictAboveSaddle)
		{
			updateSaddleDetails(resultsArray, peakIdMap);
			reassignMaxima(maxima, peakIdMap);
			analysePeaks(resultsArray, image, maxima, stats);

			// Process all the peaks for the minimum size above the saddle points. Process in order of smallest first
			sortAscResults(resultsArray, SORT_COUNT_ABOVE_SADDLE, stats);

			for (int[] result : resultsArray)
			{
				final int peakId = result[RESULT_PEAK_ID];

				// Check if this peak has been reassigned
				if (peakId != peakIdMap[peakId])
					continue;

				if (result[RESULT_COUNT_ABOVE_SADDLE] < minSize)
				{
					// This peak is not large enough, merge into the neighbour peak

					final LinkedList<int[]> saddles = saddlePoints.get(peakId);
					final int[] highestSaddle = findHighestNeighbourSaddle(peakIdMap, saddles, peakId);

					if (highestSaddle == null)
					{
						// TODO - This should not occur... ? What is the count above the saddle?

						// No neighbour so just remove
						mergePeak(image, maxima, peakIdMap, peakId, result, 0, null, null, null, null, true);
					}
					else
					{
						// Find the neighbour peak (use the map because the neighbour may have been merged)
						final int neighbourPeakId = peakIdMap[highestSaddle[SADDLE_PEAK_ID]];
						final int[] neighbourResult = findResult(resultsArray, neighbourPeakId);

						// Note: Ensure the peak counts above the saddle are updated.
						mergePeak(image, maxima, peakIdMap, peakId, result, neighbourPeakId, neighbourResult, saddles,
								saddlePoints.get(neighbourPeakId), highestSaddle, true);

						// Check for interruption after each merge
						if (Utils.isInterrupted())
							return;
					}
				}
			}

			if (isLogging)
				IJ.log("Size above saddle filter : Number of peaks = " + countPeaks(peakIdMap));

			// TODO - Add an intensity above saddle filter.
			// All code is in place so this should be a copy of the code above.
			// However what should the intensity above the saddle be relative to? 
			// - It could be an absolute value. This is image specific.
			// - It could be relative to the total peak intensity.
		}

		// Remove merged peaks from the results
		sortDescResults(resultsArray, SORT_INTENSITY, stats);
		while (resultsArray.size() > 0 && resultsArray.get(resultsArray.size() - 1)[RESULT_INTENSITY] == 0)
			resultsArray.remove(resultsArray.size() - 1);

		reassignMaxima(maxima, peakIdMap);

		updateSaddleDetails(resultsArray, peakIdMap);
	}

	private void removePeak(int[] image, int[] maxima, int[] peakIdMap, int[] result, int peakId)
	{
		// No neighbour so just remove
		mergePeak(image, maxima, peakIdMap, peakId, result, 0, null, null, null, null, false);
	}

	private int countPeaks(int[] peakIdMap)
	{
		int count = 0;
		for (int i = 1; i < peakIdMap.length; i++)
		{
			if (peakIdMap[i] == i)
			{
				count++;
			}
		}
		return count;
	}

	private void updateSaddleDetails(ArrayList<int[]> resultsArray, int[] peakIdMap)
	{
		for (int[] result : resultsArray)
		{
			int neighbourPeakId = peakIdMap[result[RESULT_SADDLE_NEIGHBOUR_ID]];

			// Ensure the peak is not marked as a saddle with itself
			if (neighbourPeakId == result[RESULT_PEAK_ID])
				neighbourPeakId = 0;

			if (neighbourPeakId == 0)
				clearSaddle(result);
			else
				result[RESULT_SADDLE_NEIGHBOUR_ID] = neighbourPeakId;
		}
	}

	private void clearSaddle(int[] result)
	{
		result[RESULT_COUNT_ABOVE_SADDLE] = result[RESULT_COUNT];
		result[RESULT_INTENSITY_ABOVE_SADDLE] = result[RESULT_INTENSITY];
		result[RESULT_SADDLE_NEIGHBOUR_ID] = 0;
		result[RESULT_HIGHEST_SADDLE_VALUE] = 0;
	}

	/**
	 * Find the highest saddle that has not been assigned to the specified peak
	 * 
	 * @param peakIdMap
	 * @param peakId
	 * @param saddles
	 * @return
	 */
	private int[] findHighestNeighbourSaddle(int[] peakIdMap, LinkedList<int[]> saddles, int peakId)
	{
		int[] maxSaddle = null;
		int max = 0;
		for (int[] saddle : saddles)
		{
			// Find foci that have not been reassigned to this peak (or nothing)
			final int neighbourPeakId = peakIdMap[saddle[SADDLE_PEAK_ID]];
			if (neighbourPeakId != peakId && neighbourPeakId != 0)
			{
				if (max < saddle[SADDLE_VALUE])
				{
					max = saddle[SADDLE_VALUE];
					maxSaddle = saddle;
				}
			}
		}
		return maxSaddle;
	}

	/**
	 * Find the highest saddle that has been assigned to the specified peak
	 * 
	 * @param peakIdMap
	 * @param peakId
	 * @param saddles
	 * @return
	 */
	private int[] findHighestSaddle(int[] peakIdMap, LinkedList<int[]> saddles, int peakId)
	{
		int[] maxSaddle = null;
		int max = 0;
		for (int[] saddle : saddles)
		{
			// Use the map to ensure the original saddle id corresponds to the current peaks
			final int neighbourPeakId = peakIdMap[saddle[SADDLE_PEAK_ID]];
			if (neighbourPeakId == peakId)
			{
				if (max < saddle[SADDLE_VALUE])
				{
					max = saddle[SADDLE_VALUE];
					maxSaddle = saddle;
				}
			}
		}
		return maxSaddle;
	}

	/**
	 * Find the result for the peak in the results array
	 * 
	 * @param resultsArray
	 * @param id
	 * @return
	 */
	private int[] findResult(ArrayList<int[]> resultsArray, int id)
	{
		for (int[] result : resultsArray)
		{
			if (result[RESULT_PEAK_ID] == id)
				return result;
		}
		return null;
	}

	/**
	 * Assigns the peak to the neighbour. Flags the peak as merged by setting the intensity to zero.
	 * If the highest saddle is lowered then recomputes the size/intensity above the saddle.
	 * 
	 * @param maxima
	 * @param peakIdMap
	 * @param peakId
	 * @param result
	 * @param neighbourPeakId
	 * @param neighbourResult
	 * @param linkedList
	 * @param peakSaddles
	 * @param highestSaddle
	 */
	private void mergePeak(int[] image, int[] maxima, int[] peakIdMap, int peakId, int[] result, int neighbourPeakId,
			int[] neighbourResult, LinkedList<int[]> peakSaddles, LinkedList<int[]> neighbourSaddles,
			int[] highestSaddle, boolean updatePeakAboveSaddle)
	{
		if (neighbourResult != null)
		{
			//			IJ.log("Merging " + peakId + " (" + result[RESULT_COUNT] + ") -> " + neighbourPeakId + " (" +
			//					neighbourResult[RESULT_COUNT] + ")");

			// Assign this peak's statistics to the neighbour
			neighbourResult[RESULT_INTENSITY] += result[RESULT_INTENSITY];
			neighbourResult[RESULT_COUNT] += result[RESULT_COUNT];

			neighbourResult[RESULT_AVERAGE_INTENSITY] = neighbourResult[RESULT_INTENSITY] /
					neighbourResult[RESULT_COUNT];

			// Check if the neighbour is higher and reassign the maximum point
			if (neighbourResult[RESULT_MAX_VALUE] < result[RESULT_MAX_VALUE])
			{
				neighbourResult[RESULT_MAX_VALUE] = result[RESULT_MAX_VALUE];
				neighbourResult[RESULT_X] = result[RESULT_X];
				neighbourResult[RESULT_Y] = result[RESULT_Y];
				neighbourResult[RESULT_Z] = result[RESULT_Z];
			}

			// Merge the saddles
			for (int[] peakSaddle : peakSaddles)
			{
				final int saddlePeakId = peakIdMap[peakSaddle[SADDLE_PEAK_ID]];
				final int[] neighbourSaddle = findHighestSaddle(peakIdMap, neighbourSaddles, saddlePeakId);
				if (neighbourSaddle == null)
				{
					// The neighbour peak does not touch this peak, add to the list
					neighbourSaddles.add(peakSaddle);
				}
				else
				{
					// Check if the saddle is higher
					if (neighbourSaddle[SADDLE_VALUE] < peakSaddle[SADDLE_VALUE])
					{
						neighbourSaddle[SADDLE_VALUE] = peakSaddle[SADDLE_VALUE];
					}
				}
			}

			// Free memory
			peakSaddles.clear();
		}
		// else
		// {
		// IJ.log("Merging " + peakId + " (" + result[RESULT_COUNT] + ") -> " + neighbourPeakId);
		// }

		// Map anything previously mapped to this peak to the new neighbour
		for (int i = peakIdMap.length; i-- > 0;)
		{
			if (peakIdMap[i] == peakId)
				peakIdMap[i] = neighbourPeakId;
		}

		// Flag this result as merged using the intensity flag. This will be used later to eliminate peaks
		result[RESULT_INTENSITY] = 0;

		// Update the count and intensity above the highest neighbour saddle
		if (neighbourResult != null)
		{
			final int[] newHighestSaddle = findHighestNeighbourSaddle(peakIdMap, neighbourSaddles, neighbourPeakId);
			if (newHighestSaddle != null)
			{
				reanalysePeak(image, maxima, peakIdMap, neighbourPeakId, newHighestSaddle, neighbourResult,
						updatePeakAboveSaddle);
			}
			else
			{
				clearSaddle(neighbourResult);
			}
		}
	}

	/**
	 * Reassign the maxima using the peak Id map and recounts all pixels above the saddle height.
	 * 
	 * @param maxima
	 * @param peakIdMap
	 * @param updatePeakAboveSaddle
	 */
	private void reanalysePeak(int[] image, int[] maxima, int[] peakIdMap, int peakId, int[] saddle, int[] result,
			boolean updatePeakAboveSaddle)
	{
		if (updatePeakAboveSaddle)
		{
			int peakSize = 0;
			int peakIntensity = 0;
			final int saddleHeight = saddle[1];
			for (int i = maxima.length; i-- > 0;)
			{
				if (maxima[i] > 0)
				{
					maxima[i] = peakIdMap[maxima[i]];
					if (maxima[i] == peakId)
					{
						if (image[i] > saddleHeight)
						{
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
	 * Reassign the maxima using the peak Id map
	 * 
	 * @param maxima
	 * @param peakIdMap
	 */
	private void reassignMaxima(int[] maxima, int[] peakIdMap)
	{
		for (int i = maxima.length; i-- > 0;)
		{
			if (maxima[i] != 0)
			{
				maxima[i] = peakIdMap[maxima[i]];
			}
		}
	}

	/**
	 * Removes any maxima that have pixels that touch the edge
	 * 
	 * @param resultsArray
	 * @param image
	 * @param maxima
	 * @param stats
	 * @param isLogging
	 */
	private void removeEdgeMaxima(ArrayList<int[]> resultsArray, int[] image, int[] maxima, double[] stats,
			boolean isLogging)
	{
		// Build a look-up table for all the peak IDs
		int maxId = 0;
		for (int[] result : resultsArray)
			if (maxId < result[RESULT_PEAK_ID])
				maxId = result[RESULT_PEAK_ID];

		final int[] peakIdMap = new int[maxId + 1];
		for (int i = 0; i < peakIdMap.length; i++)
			peakIdMap[i] = i;

		// Support the ROI bounds used to create the analysis region
		final int lowerx, upperx, lowery, uppery;
		if (bounds != null)
		{
			lowerx = bounds.x;
			lowery = bounds.y;
			upperx = bounds.x + bounds.width;
			uppery = bounds.y + bounds.height;
		}
		else
		{
			lowerx = 0;
			upperx = maxx;
			lowery = 0;
			uppery = maxy;
		}

		// Set the look-up to zero if the peak contains edge pixels
		for (int z = maxz; z-- > 0;)
		{
			// Look at top and bottom column
			for (int y = uppery, i = getIndex(lowerx, lowery, z), ii = getIndex(upperx - 1, lowery,
					z); y-- > lowery; i += maxx, ii += maxx)
			{
				peakIdMap[maxima[i]] = 0;
				peakIdMap[maxima[ii]] = 0;
			}
			// Look at top and bottom row
			for (int x = upperx, i = getIndex(lowerx, lowery, z), ii = getIndex(lowerx, uppery - 1,
					z); x-- > lowerx; i++, ii++)
			{
				peakIdMap[maxima[i]] = 0;
				peakIdMap[maxima[ii]] = 0;
			}
		}

		// Mark maxima to be removed
		for (int[] result : resultsArray)
		{
			final int peakId = result[RESULT_PEAK_ID];
			if (peakIdMap[peakId] == 0)
				result[RESULT_INTENSITY] = 0;
		}

		// Remove maxima
		sortDescResults(resultsArray, SORT_INTENSITY, stats);
		while (resultsArray.size() > 0 && resultsArray.get(resultsArray.size() - 1)[RESULT_INTENSITY] == 0)
			resultsArray.remove(resultsArray.size() - 1);

		reassignMaxima(maxima, peakIdMap);

		updateSaddleDetails(resultsArray, peakIdMap);
	}

	/**
	 * Sort the results using the specified index in descending order
	 * 
	 * @param resultsArray
	 * @param sortIndex
	 */
	void sortDescResults(ArrayList<int[]> resultsArray, int sortIndex, double[] stats)
	{
		Collections.sort(resultsArray, new ResultDescComparator(getResultIndex(sortIndex, resultsArray, stats)));
	}

	/**
	 * Sort the results using the specified index in ascending order
	 * 
	 * @param resultsArray
	 * @param sortIndex
	 */
	void sortAscResults(ArrayList<int[]> resultsArray, int sortIndex, double[] stats)
	{
		Collections.sort(resultsArray, new ResultAscComparator(getResultIndex(sortIndex, resultsArray, stats)));
	}

	private int getResultIndex(int sortIndex, ArrayList<int[]> resultsArray, double[] stats)
	{
		switch (sortIndex)
		{
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

	private void customSortAbsoluteHeight(ArrayList<int[]> resultsArray, int background)
	{
		for (int[] result : resultsArray)
		{
			result[RESULT_CUSTOM_SORT_VALUE] = getAbsoluteHeight(result, background);
		}
	}

	private void customSortRelativeHeightAboveBackground(ArrayList<int[]> resultsArray, int background)
	{
		for (int[] result : resultsArray)
		{
			int absoluteHeight = getAbsoluteHeight(result, background);
			// Increase the relative height to avoid rounding when casting to int (Note: relative height is in range [0 - 1])  
			//result[RESULT_CUSTOM_SORT_VALUE] = round(100000.0 * getRelativeHeight(result, background, absoluteHeight)); 
			result[RESULT_CUSTOM_SORT_VALUE] = round(getRelativeHeight(result, background, absoluteHeight << 8));
		}
	}

	private void customSortXYZ(ArrayList<int[]> resultsArray)
	{
		final int a = maxy * maxz;
		final int b = maxz;
		for (int[] result : resultsArray)
		{
			int x = result[RESULT_X];
			int y = result[RESULT_Y];
			int z = result[RESULT_Z];
			result[RESULT_CUSTOM_SORT_VALUE] = x * a + y * b + z;
		}
	}

	/**
	 * Initialises the global width, height and depth variables. Creates the direction offset tables.
	 */
	private void initialise(ImagePlus imp)
	{
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
		for (int d = offset.length; d-- > 0;)
		{
			offset[d] = getIndex(DIR_X_OFFSET[d], DIR_Y_OFFSET[d], DIR_Z_OFFSET[d]);
			flatEdge[d] = (Math.abs(DIR_X_OFFSET[d]) + Math.abs(DIR_Y_OFFSET[d]) + Math.abs(DIR_Z_OFFSET[d]) == 1);
		}
	}

	/**
	 * Return the single index associated with the x,y,z coordinates
	 * 
	 * @param x
	 * @param y
	 * @param z
	 * @return The index
	 */
	private int getIndex(int x, int y, int z)
	{
		return (maxx_maxy) * z + maxx * y + x;
	}

	/**
	 * Convert the single index into x,y,z coords, Input array must be length >= 3.
	 * 
	 * @param index
	 * @param xyz
	 * @return The xyz array
	 */
	private int[] getXYZ(int index, int[] xyz)
	{
		xyz[2] = index / (maxx_maxy);
		int mod = index % (maxx_maxy);
		xyz[1] = mod / maxx;
		xyz[0] = mod % maxx;
		return xyz;
	}

	/**
	 * Convert the single index into x,y,z coords, Input array must be length >= 3.
	 * 
	 * @param index
	 * @param xyz
	 * @return The xyz array
	 */
	private int[] getXY(int index, int[] xyz)
	{
		int mod = index % (maxx_maxy);
		xyz[1] = mod / maxx;
		xyz[0] = mod % maxx;
		return xyz;
	}

	/**
	 * Debugging method
	 * 
	 * @param index
	 *            the single x,y,z index
	 * @return The x coordinate
	 */
	@SuppressWarnings("unused")
	private int getX(int index)
	{
		int mod = index % (maxx_maxy);
		return mod % maxx;
	}

	/**
	 * Debugging method
	 * 
	 * @param index
	 *            the single x,y,z index
	 * @return The x coordinate
	 */
	@SuppressWarnings("unused")
	private int getY(int index)
	{
		int mod = index % (maxx_maxy);
		return mod / maxx;
	}

	/**
	 * Debugging method
	 * 
	 * @param index
	 *            the single x,y,z index
	 * @return The x coordinate
	 */
	@SuppressWarnings("unused")
	private int getZ(int index)
	{
		return index / (maxx_maxy);
	}

	/**
	 * returns whether the neighbour in a given direction is within the image. NOTE: it is assumed that the pixel x,y
	 * itself is within the image! Uses class variables xlimit, ylimit: (dimensions of the image)-1
	 * 
	 * @param x
	 *            x-coordinate of the pixel that has a neighbour in the given direction
	 * @param y
	 *            y-coordinate of the pixel that has a neighbour in the given direction
	 * @param direction
	 *            the direction from the pixel towards the neighbour
	 * @return true if the neighbour is within the image (provided that x, y is within)
	 */
	private boolean isWithinXY(int x, int y, int direction)
	{
		switch (direction)
		{
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
	 * returns whether the neighbour in a given direction is within the image. NOTE: it is assumed that the pixel x,y,z
	 * itself is within the image! Uses class variables xlimit, ylimit, zlimit: (dimensions of the image)-1
	 * 
	 * @param x
	 *            x-coordinate of the pixel that has a neighbour in the given direction
	 * @param y
	 *            y-coordinate of the pixel that has a neighbour in the given direction
	 * @param z
	 *            z-coordinate of the pixel that has a neighbour in the given direction
	 * @param direction
	 *            the direction from the pixel towards the neighbour
	 * @return true if the neighbour is within the image (provided that x, y, z is within)
	 */
	private boolean isWithinXYZ(int x, int y, int z, int direction)
	{
		switch (direction)
		{
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
	 * returns whether the neighbour in a given direction is within the image. NOTE: it is assumed that the pixel z
	 * itself is within the image! Uses class variables zlimit: (dimensions of the image)-1
	 * 
	 * @param z
	 *            z-coordinate of the pixel that has a neighbour in the given direction
	 * @param direction
	 *            the direction from the pixel towards the neighbour
	 * @return true if the neighbour is within the image (provided that z is within)
	 */
	private boolean isWithinZ(int z, int direction)
	{
		// z = 0
		if (direction < 8)
			return true;
		// z = -1
		if (direction < 17)
			return (z > 0);
		// z = 1
		return z < zlimit;
	}

	/**
	 * Check if the logging flag is enabled
	 * 
	 * @param outputType
	 *            The output options flag
	 * @return True if logging
	 */
	private boolean isLogging(int outputType)
	{
		return (outputType & OUTPUT_LOG_MESSAGES) != 0;
	}

	/**
	 * Stores the details of a pixel position.
	 */
	private class Coordinate implements Comparable<Coordinate>
	{
		public int index;
		public int id;
		public int value;

		public Coordinate(int index, int id, int value)
		{
			this.index = index;
			this.id = id;
			this.value = value;
		}

		public Coordinate(int x, int y, int z, int id, int value)
		{
			this.index = getIndex(x, y, z);
			this.id = id;
			this.value = value;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		public int compareTo(Coordinate o)
		{
			// Require the sort to rank the highest peak as first.
			// Since sort works in ascending order return a negative for a higher value.
			if (value > o.value)
				return -1;
			if (value < o.value)
				return 1;
			return 0;
		}
	}

	/**
	 * Provides the ability to sort the results arrays in descending order
	 */
	private class ResultDescComparator implements Comparator<int[]>
	{
		private int sortIndex = 0;

		public ResultDescComparator(int sortIndex)
		{
			this.sortIndex = sortIndex;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
		 */
		public int compare(int[] o1, int[] o2)
		{
			// Require the highest is first
			if (o1[sortIndex] > o2[sortIndex])
				return -1;
			if (o1[sortIndex] < o2[sortIndex])
				return 1;
			return 0;
		}
	}

	/**
	 * Provides the ability to sort the results arrays in ascending order
	 */
	private class ResultAscComparator implements Comparator<int[]>
	{
		private int sortIndex = 0;

		public ResultAscComparator(int sortIndex)
		{
			this.sortIndex = sortIndex;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
		 */
		public int compare(int[] o1, int[] o2)
		{
			// Require the lowest is first
			if (o1[sortIndex] > o2[sortIndex])
				return 1;
			if (o1[sortIndex] < o2[sortIndex])
				return -1;
			return 0;
		}
	}

	/**
	 * Sets the directory to record results.
	 * Text results are saved in simple text files and Point ROIs in ImageJ ROI files.
	 * 
	 * @param directory
	 */
	public void setResultsDirectory(String directory)
	{
		this.resultsDirectory = null;

		if (directory == null || directory.length() == 0)
			return;

		if (!new File(directory).exists())
		{
			logError("The results directory does not exist. Results will not be saved.");
		}
		else
		{
			this.resultsDirectory = directory;
		}
	}

	private static void logError(String message)
	{
		IJ.log("ERROR - FindFoci: " + message);
	}

	/**
	 * @return The results from the last call of FindFoci
	 */
	public static ArrayList<int[]> getResults()
	{
		return lastResultsArray;
	}

	/**
	 * Runs a batch of FindFoci analysis. Asks for an input directory, parameter file and results directory.
	 */
	private void runBatchMode()
	{
		if (!showBatchDialog())
			return;
		String[] imageList = getBatchImages(batchInputDirectory);
		if (imageList == null || imageList.length == 0)
		{
			IJ.error(TITLE, "No input images in folder: " + batchInputDirectory);
			return;
		}
		BatchParameters parameters;
		try
		{
			parameters = new BatchParameters(batchParameterFile);
		}
		catch (Exception e)
		{
			IJ.error(TITLE, "Unable to read parameters file: " + e.getMessage());
			return;
		}
		setResultsDirectory(batchOutputDirectory);
		openBatchResultsFile();
		for (String image : imageList)
		{
			runBatch(image, parameters);
			if (Utils.isInterrupted())
				break;
		}
		closeBatchResultsFile();
	}

	private boolean showBatchDialog()
	{
		GenericDialog gd = new GenericDialog(TITLE);
		gd.addMessage("Run " + TITLE +
				" on a set of images.\n \nAll images in a directory will be processed.\n \nOptional mask images in the input directory should be named:\n[image_name].mask.[ext]\nor placed in the mask directory with the same name as the parent image.");
		gd.addStringField("Input_directory", batchInputDirectory);
		gd.addStringField("Mask_directory", batchMaskDirectory);
		gd.addStringField("Parameter_file", batchParameterFile);
		gd.addStringField("Output_directory", batchOutputDirectory);
		gd.addMessage("[Note: Double-click a text field to open a selection dialog]");
		@SuppressWarnings("unchecked")
		Vector<TextField> texts = (Vector<TextField>) gd.getStringFields();
		for (TextField tf : texts)
		{
			tf.addMouseListener(this);
			tf.setColumns(50);
		}
		textParamFile = texts.get(2);

		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		batchInputDirectory = gd.getNextString();
		if (!new File(batchInputDirectory).isDirectory())
		{
			IJ.error(TITLE, "Input directory is not a valid directory: " + batchInputDirectory);
			return false;
		}
		batchMaskDirectory = gd.getNextString();
		if ((batchMaskDirectory != null && batchMaskDirectory.length() > 0) &&
				!new File(batchMaskDirectory).isDirectory())
		{
			IJ.error(TITLE, "Mask directory is not a valid directory: " + batchMaskDirectory);
			return false;
		}
		batchParameterFile = gd.getNextString();
		if (!new File(batchParameterFile).isFile())
		{
			IJ.error(TITLE, "Parameter file is not a valid file: " + batchParameterFile);
			return false;
		}
		batchOutputDirectory = gd.getNextString();
		if (!new File(batchOutputDirectory).isDirectory())
		{
			IJ.error(TITLE, "Output directory is not a valid directory: " + batchOutputDirectory);
			return false;
		}
		Prefs.set(BATCH_INPUT_DIRECTORY, batchInputDirectory);
		Prefs.set(BATCH_MASK_DIRECTORY, batchMaskDirectory);
		Prefs.set(BATCH_PARAMETER_FILE, batchParameterFile);
		Prefs.set(BATCH_OUTPUT_DIRECTORY, batchOutputDirectory);
		return true;
	}

	public static String[] getBatchImages(String directory)
	{
		if (directory == null)
			return null;

		// Get a list of files
		File[] fileList = (new File(directory)).listFiles();
		if (fileList == null)
			return null;

		// Exclude directories
		String[] list = new String[fileList.length];
		int c = 0;
		for (int i = 0; i < list.length; i++)
			if (fileList[i].isFile())
				list[c++] = fileList[i].getName();
		list = Arrays.copyOf(list, c);

		// Now exclude non-image files as per the ImageJ FolderOpener
		FolderOpener fo = new FolderOpener();
		list = fo.trimFileList(list);
		if (list == null)
			return null;

		list = fo.sortFileList(list);

		// Now exclude mask images
		c = 0;
		for (String name : list)
		{
			if (name.contains("mask."))
				continue;
			list[c++] = name;
		}
		return Arrays.copyOf(list, c);
	}

	private boolean runBatch(String image, BatchParameters parameters)
	{
		IJ.redirectErrorMessages();

		String[] mask = getMaskImage(batchInputDirectory, batchMaskDirectory, image);

		// Open the image (and mask)
		ImagePlus imp = openImage(batchInputDirectory, image);
		if (imp == null)
		{
			IJ.error(TITLE, "File is not a valid image: " + image);
			return false;
		}
		ImagePlus maskImp = openImage(mask[0], mask[1]);

		// Check if there are CZT options for the image/mask
		int[] imageDimension = parameters.image.clone();
		int[] maskDimension = parameters.mask.clone();
		imp = setupImage(imp, imageDimension);
		if (maskImp != null)
			maskImp = setupImage(maskImp, maskDimension);

		// Run the algorithm
		return execBatch(imp, maskImp, parameters, imageDimension, maskDimension);
	}

	/**
	 * Look for the mask image for the given file. The mask will be named using the original filename in directory2 or
	 * [image_name].mask.[ext] in either directory.
	 * 
	 * @param directory
	 * @param directory2
	 * @param filename
	 * @return array of [directory, filename]
	 */
	public static String[] getMaskImage(String directory, String directory2, String filename)
	{
		int index = filename.lastIndexOf('.');
		if (index > 0)
		{
			// Check for the mask using the original filename in the second directory
			if (new File(directory2, filename).exists())
				return new String[] { directory2, filename };

			// Look for [image_name].mask.[ext] in either directory
			String prefix = filename.substring(0, index);
			String ext = filename.substring(index);
			String maskFilename = prefix + ".mask" + ext;
			if (new File(directory, maskFilename).exists())
				return new String[] { directory, maskFilename };
			if (new File(directory2, maskFilename).exists())
				return new String[] { directory2, maskFilename };
		}
		return new String[2];
	}

	public static ImagePlus openImage(String directory, String filename)
	{
		if (filename == null)
			return null;
		Opener opener = new Opener();
		opener.setSilentMode(true);
		// TODO - Add suport for loading custom channel, slice and frame using a filename suffix, e.g. [cCzZtT]
		// If the suffix exists, remove it, load the image then extract the specified slices.
		return opener.openImage(directory, filename);
	}

	private ImagePlus setupImage(ImagePlus imp, int[] dimension)
	{
		// For channel and frame we can just update the position
		if (dimension[BatchParameters.C] > 1 && dimension[BatchParameters.C] <= imp.getNChannels())
			imp.setC(dimension[BatchParameters.C]);
		dimension[BatchParameters.C] = imp.getC();
		if (dimension[BatchParameters.T] > 1 && dimension[BatchParameters.T] <= imp.getNFrames())
			imp.setT(dimension[BatchParameters.T]);
		dimension[BatchParameters.T] = imp.getT();
		// For z we extract the slice since the algorithm processes the stack from the current channel & frame
		int z = 0;
		if (imp.getNSlices() != 1 && dimension[BatchParameters.Z] > 0 &&
				dimension[BatchParameters.Z] <= imp.getNSlices())
		{
			imp.setZ(dimension[BatchParameters.Z]);
			ImageProcessor ip = imp.getProcessor();
			String title = imp.getTitle();
			imp = new ImagePlus(title, ip);
			z = dimension[BatchParameters.Z];
		}
		dimension[BatchParameters.Z] = z;
		return imp;
	}

	/**
	 * Truncated version of the exec() method that saves all results to the batch output directory
	 * 
	 * @param imp
	 * @param mask
	 * @param p
	 * @param imageDimension
	 * @param maskDimension
	 * @return
	 */
	private boolean execBatch(ImagePlus imp, ImagePlus mask, BatchParameters p, int[] imageDimension,
			int[] maskDimension)
	{
		if (imp.getBitDepth() != 8 && imp.getBitDepth() != 16)
		{
			IJ.error(TITLE, "Only 8-bit and 16-bit images are supported");
			return false;
		}
		if ((p.centreMethod == CENTRE_GAUSSIAN_ORIGINAL || p.centreMethod == CENTRE_GAUSSIAN_SEARCH) &&
				isGaussianFitEnabled < 1)
		{
			IJ.error(TITLE, "Gaussian fit is not currently enabled");
			return false;
		}

		final int options = p.options;
		final int outputType = p.outputType;

		Object[] results = findMaxima(imp, mask, p.backgroundMethod, p.backgroundParameter, p.autoThresholdMethod,
				p.searchMethod, p.searchParameter, p.maxPeaks, p.minSize, p.peakMethod, p.peakParameter, outputType,
				p.sortIndex, options, p.blur, p.centreMethod, p.centreParameter, p.fractionParameter);

		if (results == null)
		{
			IJ.showStatus("Cancelled.");
			return false;
		}

		// Get the results
		ImagePlus maximaImp = (ImagePlus) results[0];
		@SuppressWarnings("unchecked")
		ArrayList<int[]> resultsArray = (ArrayList<int[]>) results[1];
		double[] stats = (double[]) results[2];

		String expId;
		if (p.originalTitle)
		{
			expId = imp.getShortTitle();
		}
		else
		{
			expId = imp.getShortTitle() + String.format("_c%dz%dt%d", imageDimension[BatchParameters.C],
					imageDimension[BatchParameters.Z], imageDimension[BatchParameters.T]);
		}

		ObjectAnalysisResult objectAnalysisResult = null;
		if ((options & OPTION_OBJECT_ANALYSIS) != 0)
		{
			objectAnalysisResult = new ObjectAnalysisResult();
			ImagePlus objectImp = doObjectAnalysis(mask, maximaImp, resultsArray,
					(options & OPTION_SHOW_OBJECT_MASK) != 0, objectAnalysisResult);
			if (objectImp != null)
				IJ.saveAsTiff(objectImp, batchOutputDirectory + File.separator + expId + ".objects.tiff");
		}

		if ((options & OPTION_SAVE_TO_MEMORY) != 0)
		{
			saveToMemory(resultsArray, imp, imageDimension[BatchParameters.C], imageDimension[BatchParameters.Z],
					imageDimension[BatchParameters.T]);
		}

		// Record all the results to file
		initialiseBatchPrefix(expId);
		saveResults(expId, imp, imageDimension, mask, maskDimension, p.backgroundMethod, p.backgroundParameter,
				p.autoThresholdMethod, p.searchMethod, p.searchParameter, p.maxPeaks, p.minSize, p.peakMethod,
				p.peakParameter, outputType, p.sortIndex, options, p.blur, p.centreMethod, p.centreParameter,
				p.fractionParameter, resultsArray, stats, batchOutputDirectory, objectAnalysisResult);

		boolean saveImp = false;

		// Update the mask image
		ImagePlus maxImp = null;
		Overlay overlay = null;
		if (maximaImp != null)
		{
			if ((outputType & OUTPUT_MASK) != 0)
			{
				ImageStack stack = maximaImp.getStack();

				String outname = imp.getTitle() + " " + TITLE;
				maxImp = new ImagePlus(outname, stack);
				// Adjust the contrast to show all the maxima
				int maxValue = ((outputType & OUTPUT_MASK_THRESHOLD) != 0) ? 4 : resultsArray.size() + 1;
				maxImp.setDisplayRange(0, maxValue);
			}
			if ((outputType & OUTPUT_OVERLAY_MASK) != 0)
				overlay = createOverlay(imp, maximaImp);
		}

		// Add ROI crosses to original image
		if ((outputType & (OUTPUT_ROI_SELECTION | OUTPUT_MASK_ROI_SELECTION)) != 0)
		{
			if (!resultsArray.isEmpty())
			{
				if ((outputType & OUTPUT_ROI_USING_OVERLAY) != 0)
				{
					// Create an roi for each z slice
					PointRoi[] rois = createStackRoi(resultsArray, outputType);

					if ((outputType & OUTPUT_ROI_SELECTION) != 0)
						overlay = addRoiToOverlay(imp, rois, overlay);

					if (maxImp != null && (outputType & OUTPUT_MASK_ROI_SELECTION) != 0)
						addRoiToOverlay(maxImp, rois);
				}
				else
				{
					PointRoi roi = createRoi(resultsArray, outputType);

					if ((outputType & OUTPUT_ROI_SELECTION) != 0)
					{
						imp.setRoi(roi);
						saveImp = true;
					}

					if (maxImp != null && (outputType & OUTPUT_MASK_ROI_SELECTION) != 0)
						maxImp.setRoi(roi);
				}
			}
		}

		if (overlay != null)
		{
			imp.setOverlay(overlay);
			saveImp = true;
		}

		if (saveImp)
		{
			IJ.saveAsTiff(imp, batchOutputDirectory + File.separator + expId + ".tiff");
		}
		if (maxImp != null)
		{
			IJ.saveAsTiff(maxImp, batchOutputDirectory + File.separator + expId + ".mask.tiff");
		}

		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.awt.event.MouseListener#mouseClicked(java.awt.event.MouseEvent)
	 */
	public void mouseClicked(MouseEvent e)
	{
		if (e.getClickCount() > 1 && e.getSource() instanceof TextField) // Double-click
		{
			TextField tf = (TextField) e.getSource();
			String path = tf.getText();
			boolean recording = Recorder.record;
			Recorder.record = false;
			if (tf == textParamFile)
			{
				path = Utils.getFilename("Choose_a_parameter_file", path);
			}
			else
			{
				path = Utils.getDirectory("Choose_a_directory", path);
			}
			Recorder.record = recording;
			if (path != null)
				tf.setText(path);
		}
	}

	public void mousePressed(MouseEvent paramMouseEvent)
	{
	}

	public void mouseReleased(MouseEvent paramMouseEvent)
	{
	}

	public void mouseEntered(MouseEvent paramMouseEvent)
	{
	}

	public void mouseExited(MouseEvent paramMouseEvent)
	{
	}

	/**
	 * Used to store the numer of objects and their original mask value (state)
	 */
	private class ObjectAnalysisResult
	{
		int numberOfObjects;
		int[] objectState;
		int[] fociCount;
	}

	/**
	 * Identify all non-zero pixels in the mask image as potential objects. Mark connected pixels with the same value as
	 * a single object. For each maxima identify the object and original mask value for the maxima location.
	 * 
	 * @param mask
	 *            The mask containing objects
	 * @param maximaImp
	 * @param resultsArray
	 * @param createObjectMask
	 * @return The mask image if created
	 */
	/**
	 * @param mask
	 * @param maximaImp
	 * @param resultsArray
	 * @param createObjectMask
	 * @param oar
	 * @return
	 */
	private ImagePlus doObjectAnalysis(ImagePlus mask, ImagePlus maximaImp, ArrayList<int[]> resultsArray,
			boolean createObjectMask, ObjectAnalysisResult objectAnalysisResult)
	{
		if (resultsArray == null || resultsArray.isEmpty())
		{
			// Allow the analysis to continue if we are creating the object mask or storing the analysis results
			if (!createObjectMask && objectAnalysisResult == null)
				return null;
		}

		final int[] maskImage = extractMask(mask);
		if (maskImage == null)
			return null;

		// Track all the objects. Allow more than the 16-bit capacity for counting objects.
		final int[] objects = new int[maskImage.length];
		int id = 0;
		int[] objectState = new int[10];
		// Label for 2D/3D processing
		final boolean is2D = (maskImage.length == maxx_maxy);

		int[] pList = new int[100];

		for (int i = 0; i < maskImage.length; i++)
		{
			if (maskImage[i] != 0 && objects[i] == 0)
			{
				id++;

				// Store the original mask value of new object
				if (objectState.length <= id)
					objectState = Arrays.copyOf(objectState, (int) (objectState.length * 1.5));
				objectState[id] = maskImage[i];

				if (is2D)
					pList = expandObjectXY(maskImage, objects, i, id, pList);
				else
					pList = expandObjectXYZ(maskImage, objects, i, id, pList);
			}
		}

		// For each maximum, mark the object and original mask value (state).
		// Count the number of foci in each object.
		int[] fociCount = new int[id + 1];
		if (resultsArray != null)
		{
			for (int[] result : resultsArray)
			{
				final int x = result[RESULT_X];
				final int y = result[RESULT_Y];
				final int z = (is2D) ? 0 : result[RESULT_Z];
				final int index = getIndex(x, y, z);
				final int objectId = objects[index];
				result[RESULT_OBJECT] = objectId;
				result[RESULT_STATE] = objectState[objectId];
				fociCount[objectId]++;
			}
		}

		// Store the number of objects and their orignal mask value
		if (objectAnalysisResult != null)
		{
			objectAnalysisResult.numberOfObjects = id;
			objectAnalysisResult.objectState = objectState;
			objectAnalysisResult.fociCount = fociCount;
		}

		// Show the object mask
		ImagePlus maskImp = null;
		if (createObjectMask)
		{
			// Check we do not exceed capcity
			if (id > MAXIMA_CAPCITY)
			{
				IJ.log("The number of objects exceeds the 16-bit capacity used for diplay: " + MAXIMA_CAPCITY);
				return null;
			}

			final int n = (is2D) ? 1 : maxz;
			ImageStack stack = new ImageStack(maxx, maxy, n);
			for (int z = 0, index = 0; z < n; z++)
			{
				final short[] pixels = new short[maxx_maxy];
				for (int i = 0; i < pixels.length; i++, index++)
					pixels[i] = (short) objects[index];
				stack.setPixels(pixels, z + 1);
			}
			// Create a new ImagePlus so that the stack and calibration can be set
			ImagePlus imp = new ImagePlus("", stack);
			imp.setCalibration(maximaImp.getCalibration());
			maskImp = showImage(imp, mask.getTitle() + " Objects", false);
		}

		return maskImp;
	}

	/**
	 * Searches from the specified point to find all coordinates of the same value and assigns them to given maximum ID.
	 */
	private int[] expandObjectXYZ(final int[] image, final int[] maxima, final int index0, final int id, int[] pList)
	{
		maxima[index0] = id; // mark first point
		int listI = 0; // index of current search element in the list
		int listLen = 1; // number of elements in the list

		// we create a list of connected points and start the list at the current point
		pList[listI] = index0;

		final int[] xyz = new int[3];
		final int v0 = image[index0];

		do
		{
			final int index1 = pList[listI];
			getXYZ(index1, xyz);
			final int x1 = xyz[0];
			final int y1 = xyz[1];
			final int z1 = xyz[2];

			// It is more likely that the z stack will be out-of-bounds.
			// Adopt the xy limit lookup and process z lookup separately

			final boolean isInnerXY = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);
			final boolean isInnerXYZ = (zlimit == 0) ? isInnerXY : isInnerXY && (z1 != 0 && z1 != zlimit);

			for (int d = 26; d-- > 0;)
			{
				if (isInnerXYZ || (isInnerXY && isWithinZ(z1, d)) || isWithinXYZ(x1, y1, z1, d))
				{
					final int index2 = index1 + offset[d];
					if (maxima[index2] != 0)
					{
						// This has been done already, ignore this point
						continue;
					}

					final int v2 = image[index2];

					if (v2 == v0)
					{
						// Add this to the search
						pList[listLen++] = index2;
						maxima[index2] = id;
						if (pList.length == listLen)
							pList = Arrays.copyOf(pList, (int) (listLen * 1.5));
					}
				}
			}

			listI++;

		} while (listI < listLen);

		return pList;
	}

	/**
	 * Searches from the specified point to find all coordinates of the same value and assigns them to given maximum ID.
	 */
	private int[] expandObjectXY(final int[] image, final int[] maxima, final int index0, final int id, int[] pList)
	{
		maxima[index0] = id; // mark first point
		int listI = 0; // index of current search element in the list
		int listLen = 1; // number of elements in the list

		// we create a list of connected points and start the list at the current point
		pList[listI] = index0;

		final int[] xyz = new int[2];
		final int v0 = image[index0];

		do
		{
			final int index1 = pList[listI];
			getXY(index1, xyz);
			final int x1 = xyz[0];
			final int y1 = xyz[1];

			final boolean isInnerXY = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);

			for (int d = 8; d-- > 0;)
			{
				if (isInnerXY || isWithinXY(x1, y1, d))
				{
					final int index2 = index1 + offset[d];
					if (maxima[index2] != 0)
					{
						// This has been done already, ignore this point
						continue;
					}

					final int v2 = image[index2];

					if (v2 == v0)
					{
						// Add this to the search
						pList[listLen++] = index2;
						maxima[index2] = id;
						if (pList.length == listLen)
							pList = Arrays.copyOf(pList, (int) (listLen * 1.5));
					}
				}
			}

			listI++;

		} while (listI < listLen);

		return pList;
	}

	/**
	 * Save the results array to memory using the image name and current channel and frame
	 * 
	 * @param resultsArray
	 * @param imp
	 */
	private void saveToMemory(ArrayList<int[]> resultsArray, ImagePlus imp)
	{
		saveToMemory(resultsArray, imp, imp.getChannel(), 0, imp.getFrame());
	}

	/**
	 * Save the results array to memory using the image name and current channel and frame
	 * 
	 * @param resultsArray
	 * @param imp
	 */
	private void saveToMemory(ArrayList<int[]> resultsArray, ImagePlus imp, int c, int z, int t)
	{
		if (resultsArray == null)
			return;
		String name;
		// If we use a specific slice then add this to the name 
		if (z != 0)
			name = String.format("%s (c%d,z%d,t%d)", imp.getTitle(), c, z, t);
		else
			name = String.format("%s (c%d,t%d)", imp.getTitle(), c, t);
		// Check if there was nothing stored at the key position and store the name.
		// This allows memory results to be listed in order.
		if (memory.put(name, resultsArray) == null)
			memoryNames.add(name);
	}

	/**
	 * Get a list of the names of the results that are stored in memory
	 * 
	 * @return a list of results names
	 */
	public static String[] getResultsNames()
	{
		return memoryNames.toArray(new String[memoryNames.size()]);
	}

	/**
	 * Get set of results corresponding to the name
	 * 
	 * @param name
	 *            The name of the results.
	 * @return The results (or null if none exist)
	 */
	public static ArrayList<int[]> getResults(String name)
	{
		return memory.get(name);
	}

	private boolean showSettingsDialog()
	{
		GenericDialog gd = new GenericDialog(TITLE + " Settings");
		//@formatter:off
		gd.addMessage("Set the maximum number of potential maxima for the " + TITLE + " algorithm.\n" +
				"Increasing this number can allow processing large images (which may be slow).\n" +
				"The number of potential maxima can be reduced by smoothing the image (e.g\n" +
				"using a Gaussian blur).\n" + 
				"Note: The default is the legacy value for 16-bit signed integers.\n" +
				"The maximum value supported is " + Integer.MAX_VALUE);
		gd.addNumericField("Capacity", searchCapacity, 0);
		gd.addMessage("Set the record to use when recording no results during batch processing.\n" +
				"(A record is always written to the results file for each batch image,\n" +
				"even when no foci are found; this value will be used for all the fields\n" +
				"in the empty record.)");
		gd.addStringField("Empty_Field", emptyField);

		gd.addMessage("Settings are saved when you exit ImageJ.");
		//@formatter:on

		gd.showDialog();
		if (gd.wasCanceled())
			return false;

		final double d = Math.abs(gd.getNextNumber());
		if (d > Integer.MAX_VALUE)
			return false;
		searchCapacity = Math.max(1, (int) d);

		emptyField = gd.getNextString();
		if (emptyField == null)
			emptyField = "";

		// Reset this so it will be initialised again
		emptyEntry = null;

		Prefs.set(SEARCH_CAPACITY, searchCapacity);
		Prefs.set(EMPTY_FIELD, emptyField);

		return true;
	}
}
