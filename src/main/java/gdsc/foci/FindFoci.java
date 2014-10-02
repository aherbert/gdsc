package gdsc.foci;

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

import gdsc.utils.GaussianFit;
import gdsc.threshold.Auto_Threshold;
import gdsc.utils.ImageJHelper;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.io.RoiEncoder;
import ij.plugin.PlugIn;
import ij.plugin.filter.GaussianBlur;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.text.TextWindow;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;

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
public class FindFoci implements PlugIn
{
	public static String FRAME_TITLE = "FindFoci";
	private static TextWindow resultsWindow = null;
	private static ArrayList<int[]> lastResultsArray = null;
	private static int isGaussianFitEnabled = 0;
	private static String newLine = System.getProperty("line.separator");

	/**
	 * List of background threshold methods for the dialog
	 */
	public final static String[] backgroundMethods = { "Absolute", "Mean", "Std.Dev above mean", "Auto threshold",
			"None" };

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
	 * The background intensity is set as 0. Equivalent to using {@link #BACKGROUND_ABSOLUTE} with a value of zero.
	 */
	public final static int BACKGROUND_NONE = 4;

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
	public final static String[] maskOptions = { "[None]", "Peaks", "Threshold", "Peaks above saddle",
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
	 * Create an output mask
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
	 * The index of the sum of the peak intensity within the result int[] array of the results ArrayList<int[]> object
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

	// The length of the result array
	private final static int RESULT_LENGTH = 15;

	// The indices used within the saddle array
	private final static int SADDLE_PEAK_ID = 0;
	private final static int SADDLE_VALUE = 1;

	/**
	 * The list of recognised methods for sorting the results
	 */
	public final static String[] sortIndexMethods = { "Size", "Total intensity", "Max value", "Average intensity",
			"Total intensity minus background", "Average intensity minus background", "X", "Y", "Z", "Saddle height",
			"Size above saddle", "Intensity above saddle", "Absolute height", "Relative height >Bg" };

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

	private static String[] centreMethods = { "Max value (search image)", "Max value (original image)",
			"Centre of mass (search image)", "Centre of mass (original image)", "Gaussian (search image)",
			"Gaussian (original image)" };

	static
	{
		GaussianFit gf = new GaussianFit();
		isGaussianFitEnabled = (gf.isFittingEnabled()) ? 1 : -1;
		if (!gf.isFittingEnabled())
		{
			centreMethods = Arrays.copyOf(centreMethods, centreMethods.length-2);
			
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
	public final static String[] autoThresholdMethods = { "Default", "Huang", "Intermodes", "IsoData", "Li",
			"MaxEntropy", "Mean", "MinError(I)", "Minimum", "Moments", "Otsu", "Percentile", "RenyiEntropy",
			"Shanbhag", "Triangle", "Yen" };
	public final static String[] statisticsModes = { "Both", "Inside", "Outside" };

	private static String myMaskImage = "";
	private static int myBackgroundMethod = FindFoci.BACKGROUND_STD_DEV_ABOVE_MEAN;
	private static double myBackgroundParameter = 3;
	private static String myThresholdMethod = "Otsu";
	private static String myStatisticsMode = "Both";
	private static int mySearchMethod = FindFoci.SEARCH_ABOVE_BACKGROUND;
	private static double mySearchParameter = 0.3;
	private static int myMinSize = 5;
	private static boolean myMinimumAboveSaddle = true;
	private static int myPeakMethod = FindFoci.PEAK_RELATIVE_ABOVE_BACKGROUND;
	private static double myPeakParameter = 0.5;
	private static int mySortMethod = FindFoci.SORT_INTENSITY;
	private static int myMaxPeaks = 50;
	private static int myShowMask = 3;
	private static boolean myShowTable = true;
	private static boolean myMarkMaxima = true;
	private static boolean myMarkROIMaxima = false;
	private static boolean myShowMaskMaximaAsDots = true;
	private static boolean myShowLogMessages = true;
	private static boolean myRemoveEdgeMaxima = true;
	private static String myResultsDirectory = null;
	private static double myGaussianBlur = 0;
	private static int myCentreMethod = FindFoci.CENTRE_MAX_VALUE_SEARCH;
	private static double myCentreParameter = 2;
	private static double myFractionParameter = 0.5;

	// the following are class variables for having shorter argument lists
	private int maxx, maxy, maxz; // image dimensions
	private int xlimit, ylimit, zlimit;
	private int maxx_maxy, maxx_maxy_maxz;
	private int[] offset;
	private int dStart;
	private boolean[] flatEdge;
	private String resultsDirectory = null;

	// The following arrays are built for a 3D search through the following z-order: (0,-1,1)
	// Each 2D plane is built for a search round a pixel in an anti-clockwise direction. 
	// Note the x==y==z==0 element is not present. Thus there are blocks of 8,9,9 for each plane.
	// This preserves the isWithin() functionality of ij.plugin.filter.MaximumFinder.

	private final int[] DIR_X_OFFSET = new int[] { 0, 1, 1, 1, 0, -1, -1, -1, 0, 1, 1, 1, 0, -1, -1, -1, 0, 0, 1, 1, 1,
			0, -1, -1, -1, 0 };
	private final int[] DIR_Y_OFFSET = new int[] { -1, -1, 0, 1, 1, 1, 0, -1, -1, -1, 0, 1, 1, 1, 0, -1, 0, -1, -1, 0,
			1, 1, 1, 0, -1, 0 };
	private final int[] DIR_Z_OFFSET = new int[] { 0, 0, 0, 0, 0, 0, 0, 0, -1, -1, -1, -1, -1, -1, -1, -1, -1, 1, 1, 1,
			1, 1, 1, 1, 1, 1 };

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

	/** Ask for parameters and then execute. */
	public void run(String arg)
	{
		ImagePlus imp = WindowManager.getCurrentImage();

		if (null == imp)
		{
			IJ.showMessage("There must be at least one image open");
			return;
		}

		if (imp.getBitDepth() != 8 && imp.getBitDepth() != 16)
		{
			IJ.showMessage("Error", "Only 8-bit and 16-bit images are supported");
			return;
		}

		//if (imp.getNChannels() != 1 || imp.getNFrames() != 1)
		//{
		//	IJ.showMessage("Error", "Only single channel, single frame images are supported");
		//	return;
		//}

		// Build a list of the open images
		ArrayList<String> newImageList = buildMaskList(imp);

		GenericDialog gd = new GenericDialog(FRAME_TITLE);

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
		gd.addSlider("Fraction_parameter", 0.05, 1, myFractionParameter);
		gd.addCheckbox("Show_table", myShowTable);
		gd.addCheckbox("Mark_maxima", myMarkMaxima);
		gd.addCheckbox("Mark_peak_maxima", myMarkROIMaxima);
		gd.addCheckbox("Show_peak_maxima_as_dots", myShowMaskMaximaAsDots);
		gd.addCheckbox("Show_log_messages", myShowLogMessages);
		gd.addCheckbox("Remove_edge_maxima", myRemoveEdgeMaxima);
		gd.addStringField("Results_directory", myResultsDirectory);
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
		myFractionParameter = gd.getNextNumber();
		myShowTable = gd.getNextBoolean();
		myMarkMaxima = gd.getNextBoolean();
		myMarkROIMaxima = gd.getNextBoolean();
		myShowMaskMaximaAsDots = gd.getNextBoolean();
		myShowLogMessages = gd.getNextBoolean();
		myRemoveEdgeMaxima = gd.getNextBoolean();
		myResultsDirectory = gd.getNextString();
		myGaussianBlur = gd.getNextNumber();
		myCentreMethod = gd.getNextChoiceIndex();
		myCentreParameter = gd.getNextNumber();

		int outputType = 0;
		if (myShowMask == 1)
			outputType += FindFoci.OUTPUT_MASK_PEAKS;
		if (myShowMask == 2)
			outputType += FindFoci.OUTPUT_MASK_THRESHOLD;
		if (myShowMask == 3)
			outputType += FindFoci.OUTPUT_MASK_PEAKS | FindFoci.OUTPUT_MASK_ABOVE_SADDLE;
		if (myShowMask == 4)
			outputType += FindFoci.OUTPUT_MASK_THRESHOLD | FindFoci.OUTPUT_MASK_ABOVE_SADDLE;
		if (myShowMask == 5)
			outputType += FindFoci.OUTPUT_MASK_PEAKS | FindFoci.OUTPUT_MASK_FRACTION_OF_INTENSITY;
		if (myShowMask == 6)
			outputType += FindFoci.OUTPUT_MASK_PEAKS | FindFoci.OUTPUT_MASK_FRACTION_OF_HEIGHT;

		if (myShowTable)
			outputType += OUTPUT_RESULTS_TABLE;
		if (myMarkMaxima)
			outputType += OUTPUT_ROI_SELECTION;
		if (myMarkROIMaxima)
			outputType += OUTPUT_MASK_ROI_SELECTION;
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
			IJ.showMessage("Error", "No results options chosen");
			return;
		}

		ImagePlus mask = WindowManager.getImage(myMaskImage);

		setResultsDirectory(myResultsDirectory);
		exec(imp, mask, myBackgroundMethod, myBackgroundParameter, myThresholdMethod, mySearchMethod,
				mySearchParameter, myMaxPeaks, myMinSize, myPeakMethod, myPeakParameter, outputType, mySortMethod,
				options, myGaussianBlur, myCentreMethod, myCentreParameter, myFractionParameter);
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
		newImageList.add("[None]");

		if (imp != null)
		{
			for (int id : gdsc.utils.ImageJHelper.getIDList())
			{
				ImagePlus maskImp = WindowManager.getImage(id);
				// Mask image must:
				// - Not be the same image
				// - Not be derived from the same image, i.e. a FindFoci result (check using image name)
				// - Match dimensions of the input image
				if (maskImp != null && maskImp.getID() != imp.getID() && !maskImp.getTitle().endsWith(FRAME_TITLE) &&
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
	 *            {@link #findMaxima(ImagePlus, int, double, String, int, double, int, int, int, double, int, int) } will
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
			IJ.showMessage("Error", "Only 8-bit and 16-bit images are supported");
			return;
		}
		if ((centreMethod == CENTRE_GAUSSIAN_ORIGINAL || centreMethod == CENTRE_GAUSSIAN_SEARCH) &&
				isGaussianFitEnabled < 1)
		{
			IJ.showMessage("Error", "Gaussian fit is not currently enabled");
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
					// YesNoCancelDialog causes asynchronous thread exception within Eclipse.
					GenericDialog d = new GenericDialog(FRAME_TITLE);
					d.addMessage("Warning: Marking the maxima will destroy the ROI area");
					d.showDialog();
					if (!d.wasOKed())
						return;
				}
				else
				{
					imp.killRoi();
					roi = null;
				}
			}
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

		// Add peaks to a results window
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
			resultsWindow.append("");
		}

		// Record all the results to file
		if (resultsArray != null && resultsDirectory != null)
		{
			try
			{
				String expId = generateId(imp);

				// Save results to file
				FileOutputStream fos = new FileOutputStream(resultsDirectory + File.separatorChar + expId + ".xls");
				OutputStreamWriter out = new OutputStreamWriter(fos, "UTF-8");
				out.write(createResultsHeader(imp, stats));
				int[] xpoints = new int[resultsArray.size()];
				int[] ypoints = new int[resultsArray.size()];
				for (int i = 0; i < resultsArray.size(); i++)
				{
					int[] result = resultsArray.get(i);
					xpoints[i] = result[RESULT_X];
					ypoints[i] = result[RESULT_Y];
					out.write(buildResultEntry(i + 1, resultsArray.size() - i, result, stats[STATS_SUM],
							stats[STATS_BACKGROUND], stats[STATS_SUM_ABOVE_BACKGROUND]));
				}
				out.close();

				// Save roi to file
				RoiEncoder roiEncoder = new RoiEncoder(resultsDirectory + File.separatorChar + expId + ".roi");
				roiEncoder.write(new PointRoi(xpoints, ypoints, resultsArray.size()));

				// Save parameters to file
				fos = new FileOutputStream(resultsDirectory + File.separatorChar + expId + ".params");
				out = new OutputStreamWriter(fos, "UTF-8");
				writeParam(out, "Image", imp.getTitle());
				if (imp.getOriginalFileInfo() != null)
					writeParam(out, "File", imp.getOriginalFileInfo().directory + imp.getOriginalFileInfo().fileName);
				if (mask != null)
				{
					writeParam(out, "Mask", mask.getTitle());
					if (mask.getOriginalFileInfo() != null)
						writeParam(out, "Mask File", mask.getOriginalFileInfo().directory +
								mask.getOriginalFileInfo().fileName);
				}
				writeParam(out, "Background_method", backgroundMethods[backgroundMethod]);
				writeParam(out, "Background_parameter", Double.toString(backgroundParameter));
				writeParam(out, "Auto_threshold", autoThresholdMethod);
				writeParam(out, "Search_method", searchMethods[searchMethod]);
				writeParam(out, "Statistics_mode", getStatisticsMode(options));
				writeParam(out, "Search_parameter", Double.toString(searchParameter));
				writeParam(out, "Minimum_size", Integer.toString(minSize));
				writeParam(out, "Minimum_above_saddle", ((options & OPTION_MINIMUM_ABOVE_SADDLE) != 0) ? "true"
						: "false");
				writeParam(out, "Minimum_peak_height", peakMethods[peakMethod]);
				writeParam(out, "Peak_parameter", Double.toString(peakParameter));
				writeParam(out, "Sort_method", sortIndexMethods[sortIndex]);
				writeParam(out, "Maximum_peaks", Integer.toString(maxPeaks));
				writeParam(out, "Show_mask", maskOptions[getMaskOption(outputType)]);
				writeParam(out, "Show_table", ((outputType & OUTPUT_RESULTS_TABLE) != 0) ? "true" : "false");
				writeParam(out, "Mark_maxima", ((outputType & OUTPUT_ROI_SELECTION) != 0) ? "true" : "false");
				writeParam(out, "Mark_peak_maxima", ((outputType & OUTPUT_MASK_ROI_SELECTION) != 0) ? "true" : "false");
				writeParam(out, "Show_peak_maxima_as_dots", ((outputType & OUTPUT_MASK_NO_PEAK_DOTS) == 0) ? "true"
						: "false");
				writeParam(out, "Show_log_messages", ((outputType & OUTPUT_LOG_MESSAGES) != 0) ? "true" : "false");
				writeParam(out, "Results_directory", resultsDirectory);
				writeParam(out, "Gaussian_blur", "" + blur);
				writeParam(out, "Centre_method", centreMethods[centreMethod]);
				writeParam(out, "Centre_parameter", "" + centreParameter);
				out.close();
			}
			catch (Exception e)
			{
				logError(e.getMessage());
			}
		}

		// Update the mask image
		ImagePlus maxImp = null;
		if (maximaImp != null && (outputType & OUTPUT_MASK) != 0)
		{
			ImageStack stack = maximaImp.getStack();

			String outname = imp.getTitle() + " " + FRAME_TITLE;

			maxImp = WindowManager.getImage(outname);
			if (maxImp != null)
			{
				maxImp.setStack(stack);
			}
			else
			{
				maxImp = new ImagePlus(outname, stack);
			}

			maxImp.show();

			// Adjust the contrast to show all the maxima
			if (resultsArray != null)
			{
				int maxValue = ((outputType & OUTPUT_MASK_THRESHOLD) != 0) ? 4 : resultsArray.size() + 1;
				maxImp.setDisplayRange(0, maxValue);
			}

			maxImp.updateAndDraw();
		}

		// Add ROI crosses to original image
		if (resultsArray != null && (outputType & (OUTPUT_ROI_SELECTION | OUTPUT_MASK_ROI_SELECTION)) != 0)
		{
			int nMaxima = resultsArray.size();

			if (nMaxima > 0)
			{
				int[] xpoints = new int[nMaxima];
				int[] ypoints = new int[nMaxima];
				for (int i = 0; i < nMaxima; i++)
				{
					int[] xy = resultsArray.get(i);
					xpoints[i] = xy[RESULT_X];
					ypoints[i] = xy[RESULT_Y];
				}

				if ((outputType & OUTPUT_ROI_SELECTION) != 0)
					imp.setRoi(new PointRoi(xpoints, ypoints, nMaxima));

				if (maxImp != null && (outputType & OUTPUT_MASK_ROI_SELECTION) != 0)
					maxImp.setRoi(new PointRoi(xpoints, ypoints, nMaxima));
			}
		}

		// Remove points from mask if necessary
		if (maxImp != null && (outputType & OUTPUT_MASK_ROI_SELECTION) == 0)
		{
			if (maxImp.getRoi() != null && maxImp.getRoi().getType() == Roi.POINT)
			{
				maxImp.killRoi();
			}
		}
	}

	public static String getStatisticsMode(int options)
	{
		if ((options & (FindFoci.OPTION_STATS_INSIDE | FindFoci.OPTION_STATS_OUTSIDE)) == (FindFoci.OPTION_STATS_INSIDE | FindFoci.OPTION_STATS_OUTSIDE))
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
	public void showResults(ImagePlus imp, int backgroundMethod, double backgroundParameter,
			String autoThresholdMethod, double searchParameter, int maxPeaks, int minSize, int peakMethod,
			double peakParameter, int outputType, int sortIndex, int options, Object[] results)
	{
		// Get the results
		ImagePlus maximaImp = (ImagePlus) results[0];
		@SuppressWarnings("unchecked")
		ArrayList<int[]> resultsArray = (ArrayList<int[]>) results[1];
		double[] stats = (double[]) results[2];

		// Add peaks to a results window
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
			if (!resultsArray.isEmpty())
				resultsWindow.append("");
		}

		// Update the mask image
		ImagePlus maxImp = null;
		if (maximaImp != null && (outputType & OUTPUT_MASK) != 0)
		{
			ImageStack stack = maximaImp.getStack();

			String outname = imp.getTitle() + " " + FRAME_TITLE;

			maxImp = WindowManager.getImage(outname);
			if (maxImp != null)
			{
				maxImp.setStack(stack);
			}
			else
			{
				maxImp = new ImagePlus(outname, stack);
			}

			maxImp.show();

			// Adjust the contrast to show all the maxima
			if (resultsArray != null)
			{
				int maxValue = ((outputType & OUTPUT_MASK_THRESHOLD) != 0) ? 4 : resultsArray.size() + 1;
				maxImp.setDisplayRange(0, maxValue);
			}

			maxImp.updateAndDraw();
		}

		// Remove ROI if not an output option
		if ((outputType & OUTPUT_ROI_SELECTION) == 0)
			killPointRoi(imp);
		if ((outputType & OUTPUT_MASK_ROI_SELECTION) == 0)
			killPointRoi(maxImp);

		// Add ROI crosses to original image
		if (resultsArray != null && (outputType & (OUTPUT_ROI_SELECTION | OUTPUT_MASK_ROI_SELECTION)) != 0)
		{
			int nMaxima = resultsArray.size();

			if (nMaxima > 0)
			{
				int[] xpoints = new int[nMaxima];
				int[] ypoints = new int[nMaxima];
				for (int i = 0; i < nMaxima; i++)
				{
					int[] xy = resultsArray.get(i);
					xpoints[i] = xy[RESULT_X];
					ypoints[i] = xy[RESULT_Y];
				}

				if ((outputType & OUTPUT_ROI_SELECTION) != 0)
					imp.setRoi(new PointRoi(xpoints, ypoints, nMaxima));

				if (maxImp != null && (outputType & OUTPUT_MASK_ROI_SELECTION) != 0)
					maxImp.setRoi(new PointRoi(xpoints, ypoints, nMaxima));
			}
			else
			{
				if ((outputType & OUTPUT_ROI_SELECTION) != 0)
					killPointRoi(imp);
				if ((outputType & OUTPUT_MASK_ROI_SELECTION) != 0)
					killPointRoi(maxImp);
			}
		}
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

	private int getMaskOption(int outputType)
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

	private boolean isSet(int flag, int mask)
	{
		return (flag & mask) == mask;
	}

	private void writeParam(OutputStreamWriter out, String key, String value) throws IOException
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
			IJ.showMessage("Error", "Only 8-bit and 16-bit images are supported");
			return null;
		}
		if ((centreMethod == CENTRE_GAUSSIAN_ORIGINAL || centreMethod == CENTRE_GAUSSIAN_SEARCH) &&
				isGaussianFitEnabled < 1)
		{
			IJ.showMessage("Error", "Gaussian fit is not currently enabled");
			return null;
		}

		boolean isLogging = isLogging(outputType);

		if (isLogging)
			IJ.log("---" + newLine + FRAME_TITLE + " : " + imp.getTitle());

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
		short[] maxima = new short[maxx_maxy_maxz]; // Contains the maxima Id assigned for each point

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

		if (ImageJHelper.isInterrupted())
			return null;

		// Calculate the auto-threshold if necessary
		if (backgroundMethod == BACKGROUND_AUTO_THRESHOLD)
		{
			stats[STATS_BACKGROUND] = Auto_Threshold.getThreshold(autoThresholdMethod, statsHistogram);
		}
		statsHistogram = null;

		IJ.showStatus("Getting sorted maxima...");
		stats[STATS_BACKGROUND] = getSearchThreshold(backgroundMethod, backgroundParameter, stats);
		if (isLogging)
			IJ.log("Background level = " + IJ.d2s(stats[STATS_BACKGROUND], 2));
		Coordinate[] maxPoints = getSortedMaxPoints(image, maxima, types, round(stats[STATS_MIN]),
				round(stats[STATS_BACKGROUND]));

		if (ImageJHelper.isInterrupted() || maxPoints == null)
			return null;

		if (isLogging)
			IJ.log("Number of potential maxima = " + maxPoints.length);
		IJ.showStatus("Analyzing maxima...");

		ArrayList<int[]> resultsArray = new ArrayList<int[]>(maxPoints.length);

		assignMaxima(maxima, maxPoints, resultsArray);

		// Free memory
		maxPoints = null;

		assignPointsToMaxima(image, histogram, types, stats, maxima);

		if (ImageJHelper.isInterrupted())
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

		if (ImageJHelper.isInterrupted())
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

		if (ImageJHelper.isInterrupted())
			return null;

		if ((options & OPTION_REMOVE_EDGE_MAXIMA) != 0)
			removeEdgeMaxima(resultsArray, image, maxima, stats, isLogging);

		int totalPeaks = resultsArray.size();

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
		if ((outputType & OUTPUT_MASK) != 0)
		{
			IJ.showStatus("Generating mask image...");

			outImp = generateOutputMask(outputType, autoThresholdMethod, imp.getTitle(), fractionParameter, image,
					types, maxima, stats, resultsArray, nMaxima);

			if (isLogging)
				timingSplit("Calulated output mask");
		}

		renumberPeaks(resultsArray, originalNumberOfPeaks);

		IJ.showTime(imp, start, "Done ", maxz);

		lastResultsArray = resultsArray;

		return new Object[] { outImp, resultsArray, stats };
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
	 *         int[] - image histogram; (5) double[] - image statistics; (6) int[] - the original image.
	 */
	public Object[] findMaximaInit(ImagePlus originalImp, ImagePlus imp, ImagePlus mask, int backgroundMethod,
			String autoThresholdMethod, int options)
	{
		lastResultsArray = null;

		if (originalImp.getBitDepth() != 8 && originalImp.getBitDepth() != 16)
		{
			IJ.showMessage("Error", "Only 8-bit and 16-bit images are supported");
			return null;
		}

		// Call first to set up the processing for isWithin;
		initialise(imp);

		int[] originalImage = extractImage(originalImp);
		int[] image = extractImage(imp);
		byte[] types = new byte[maxx_maxy_maxz]; // Will be a notepad for pixel types
		short[] maxima = new short[maxx_maxy_maxz]; // Contains the maxima Id assigned for each point

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
			stats[STATS_BACKGROUND] = Auto_Threshold.getThreshold(autoThresholdMethod, statsHistogram);
		}

		return new Object[] { image, types, maxima, histogram, stats, originalImage };
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
		short[] maxima = (short[]) initArray[2];
		int[] histogram = (int[]) initArray[3];
		double[] stats = (double[]) initArray[4];
		int[] originalImage = (int[]) initArray[5];

		byte[] types2 = null;
		short[] maxima2 = null;
		int[] histogram2 = null;
		double[] stats2 = null;

		if (clonedInitArray == null)
		{
			clonedInitArray = new Object[6];
			types2 = new byte[types.length];
			maxima2 = new short[maxima.length];
			histogram2 = new int[histogram.length];
			stats2 = new double[stats.length];
		}
		else
		{
			types2 = (byte[]) clonedInitArray[1];
			maxima2 = (short[]) clonedInitArray[2];
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
		short[] maxima = (short[]) initArray[2];
		int[] histogram = (int[]) initArray[3];
		double[] stats = (double[]) initArray[4];
		int[] originalImage = (int[]) initArray[5];

		byte[] types2 = null;
		short[] maxima2 = null;
		int[] histogram2 = null;
		double[] stats2 = null;

		if (clonedInitArray == null)
		{
			clonedInitArray = new Object[6];
			types2 = new byte[types.length];
			maxima2 = new short[maxima.length];
			histogram2 = new int[histogram.length];
			stats2 = new double[stats.length];
		}
		else
		{
			types2 = (byte[]) clonedInitArray[1];
			maxima2 = (short[]) clonedInitArray[2];
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
		short[] maxima = (short[]) initArray[2]; // Contains the maxima Id assigned for each point
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
		short[] maxima = (short[]) initArray[2]; // Contains the maxima Id assigned for each point
		int[] histogram = (int[]) initArray[3];
		double[] stats = (double[]) initArray[4];

		stats[STATS_BACKGROUND] = getSearchThreshold(backgroundMethod, backgroundParameter, stats);
		stats[STATS_SUM_ABOVE_BACKGROUND] = getIntensityAboveBackground(image, types, round(stats[STATS_BACKGROUND]));
		Coordinate[] maxPoints = getSortedMaxPoints(image, maxima, types, round(stats[STATS_MIN]),
				round(stats[STATS_BACKGROUND]));

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
		short[] maxima = (short[]) initArray[2]; // Contains the maxima Id assigned for each point
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
			IJ.log(FRAME_TITLE + " Error: Gaussian fit is not currently enabled");
			return null;
		}

		int[] searchImage = (int[]) initArray[0];
		byte[] types = (byte[]) initArray[1];
		short[] maxima = (short[]) initArray[2];
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
			IJ.log(FRAME_TITLE + " Error: Gaussian fit is not currently enabled");
			return null;
		}

		int[] searchImage = (int[]) initArray[0];
		byte[] types = (byte[]) initArray[1];
		short[] maxima = (short[]) initArray[2];
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
		short[] maxima = (short[]) initArray[2];
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
		if ((outputType & OUTPUT_MASK) != 0)
		{
			outImp = generateOutputMask(outputType, autoThresholdMethod, imageTitle, fractionParameter, image, types,
					maxima, stats, resultsArray, nMaxima);
		}

		renumberPeaks(resultsArray, originalNumberOfPeaks);

		lastResultsArray = resultsArray;

		return new Object[] { outImp, resultsArray, stats };
	}

	private ImagePlus generateOutputMask(int outputType, String autoThresholdMethod, String imageTitle,
			double fractionParameter, int[] image, byte[] types, short[] maxima, double[] stats,
			ArrayList<int[]> resultsArray, int nMaxima)
	{
		// TODO - Add an option for a coloured map of peaks using 4 colours. No touching peaks should be the same colour.
		// - Assign all neighbours for each cell
		// - Start @ cell with most neighbours -> label with a colour
		// - Find unlabelled cell next to labelled cell -> label with an unused colour not used by its neighbours
		// - Repeat
		// - Finish all cells with no neighbours using random colour asignment

		// Rebuild the mask: all maxima have value 1, the remaining peak area are numbered sequentially starting
		// with value 2.
		// First create byte values to use in the mask for each maxima
		short[] maximaValues = new short[nMaxima];
		short[] maximaPeakIds = new short[nMaxima];
		int[] displayValues = new int[nMaxima];

		if ((outputType & (OUTPUT_MASK_ABOVE_SADDLE | OUTPUT_MASK_FRACTION_OF_INTENSITY | OUTPUT_MASK_FRACTION_OF_HEIGHT)) != 0)
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
			maximaValues[i] = (short) (nMaxima - i);
			int[] result = resultsArray.get(i);
			maximaPeakIds[i] = (short) result[RESULT_PEAK_ID];
			if ((outputType & OUTPUT_MASK_ABOVE_SADDLE) != 0)
			{
				displayValues[i] = result[RESULT_HIGHEST_SADDLE_VALUE];
			}
			else if ((outputType & OUTPUT_MASK_FRACTION_OF_HEIGHT) != 0)
			{
				displayValues[i] = (int) Math.round(fractionParameter *
						(result[RESULT_MAX_VALUE] - stats[STATS_BACKGROUND]) + stats[STATS_BACKGROUND]);
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

		ImageStack stack = new ImageStack(maxx, maxy);
		short maximaValue = (short) (nMaxima + 1);

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
			maximaValue = 4;
		}

		// Blank any pixels below the saddle height
		if ((outputType & (OUTPUT_MASK_ABOVE_SADDLE | OUTPUT_MASK_FRACTION_OF_INTENSITY | OUTPUT_MASK_FRACTION_OF_HEIGHT)) != 0)
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
			for (int i = 0; i < nMaxima; i++)
			{
				int[] result = resultsArray.get(i);
				maxima[getIndex(result[RESULT_X], result[RESULT_Y], result[RESULT_Z])] = maximaValue;
			}
		}

		if ((outputType & OUTPUT_MASK_THRESHOLD) != 0)
		{
			nMaxima = 4;
		}

		// Output the mask
		// The index is '(maxx_maxy) * z + maxx * y + x' so we can simply iterate over the array if we use z, y, x order
		for (int z = 0, index = 0; z < maxz; z++)
		{
			ImageProcessor ip = (nMaxima > 253) ? new ShortProcessor(maxx, maxy) : new ByteProcessor(maxx, maxy);
			for (int y = 0; y < maxy; y++)
			{
				for (int x = 0; x < maxx; x++)
				{
					ip.set(x, y, maxima[index++]);
				}
			}
			stack.addSlice(null, ip);
		}

		return new ImagePlus(imageTitle + " " + FRAME_TITLE, stack);
	}

	private void calculateFractionOfIntensityDisplayValues(double fractionParameter, int[] image, short[] maxima,
			double[] stats, short[] maximaPeakIds, int[] displayValues)
	{
		// For each maxima
		for (int i = 0; i < maximaPeakIds.length; i++)
		{
			short peakValue = maximaPeakIds[i];

			// Histogram all the pixels above background
			int[] hist = buildHistogram(image, maxima, peakValue, round(stats[STATS_MAX]));

			int background = (int) Math.floor(stats[STATS_BACKGROUND]);

			// Sum above background
			long sum = 0;
			for (int value = 0; value < hist.length; value++)
				sum += hist[value] * (value - background);

			// Determine the cut-off using fraction of cumulative intensity
			long total = (long) (sum * fractionParameter);

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
		short[] peakIdMap = new short[originalNumberOfPeaks + 1];
		short i = 1;
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
	private void findBorders(short[] maxima, byte[] types)
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

		int[] xyz = new int[3];

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
				int x = xyz[0];
				int y = xyz[1];
				boolean isInnerXY = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);

				// Process the neighbours
				for (int d = 8; d-- > 0;)
				{
					if (isInnerXY || isWithinXY(x, y, d))
					{
						int index2 = index + offset[d];

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
		int[] xyz = new int[3];

		for (int i = maxx_maxy, index = maxx_maxy * z; i-- > 0; index++)
		{
			if ((types[index] & SADDLE_POINT) != 0)
			{
				getXY(index, xyz);
				int x = xyz[0];
				int y = xyz[1];

				boolean isInner = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);

				boolean[] edgesSet = new boolean[8];
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
				int nRadii = nRadii(types, index); // number of lines radiating
				if (nRadii == 0) // single point or foreground patch?
				{
					types[index] &= ~SADDLE_POINT;
					types[index] |= SADDLE_WITHIN;
				}
				else if (nRadii == 1)
					removeLineFrom(types, index);
			} // if v<255 && v>0
		}
	} // void cleanupExtraLines

	/** delete a line starting at x, y up to the next (4-connected) vertex */
	void removeLineFrom(byte[] types, int index)
	{
		types[index] &= ~SADDLE_POINT;
		types[index] |= SADDLE_WITHIN;
		int[] xyz = new int[3];
		boolean continues;
		do
		{
			getXY(index, xyz);
			int x = xyz[0];
			int y = xyz[1];

			continues = false;
			boolean isInner = (y != 0 && y != maxy - 1) && (x != 0 && x != maxx - 1); // not necessary, but faster
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
	} // void removeLineFrom

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
		int[] xyz = new int[3];
		getXY(index, xyz);
		int x = xyz[0];
		int y = xyz[1];

		boolean isInner = (y != 0 && y != maxy - 1) && (x != 0 && x != maxx - 1); // not necessary, but faster than
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
	} // int nRadii

	/**
	 * For each peak in the maxima image, perform thresholding using the specified method.
	 * 
	 * @param image
	 * @param maxima
	 * @param s
	 * @param autoThresholdMethod
	 */
	private void thresholdMask(int[] image, short[] maxima, short peakValue, String autoThresholdMethod, double[] stats)
	{
		int[] histogram = buildHistogram(image, maxima, peakValue, round(stats[STATS_MAX]));
		int threshold = Auto_Threshold.getThreshold(autoThresholdMethod, histogram);

		for (int i = maxima.length; i-- > 0;)
		{
			if (maxima[i] == peakValue)
			{
				// Use negative to allow use of image in place
				maxima[i] = (short) ((image[i] > threshold) ? -3 : -2);
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
	private int[] buildHistogram(int[] image, short[] maxima, short peakValue, int maxValue)
	{
		int[] histogram = new int[maxValue + 1];

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
	private void invertMask(short[] maxima)
	{
		for (int i = maxima.length; i-- > 0;)
		{
			if (maxima[i] < 0)
			{
				maxima[i] = (short) -maxima[i];
			}
		}
	}

	/**
	 * Adds the borders to the peaks
	 * 
	 * @param maxima
	 * @param types
	 */
	private void addBorders(short[] maxima, byte[] types)
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
			resultsWindow = new TextWindow(FRAME_TITLE + " Results", createResultsHeader(), "", 900, 300);
		}
	}

	private String createResultsHeader()
	{
		return createResultsHeader(null, null);
	}

	private String createResultsHeader(ImagePlus imp, double[] stats)
	{
		StringBuilder sb = new StringBuilder();
		if (imp != null)
		{
			sb.append("# Image\t").append(imp.getTitle()).append(newLine);
			if (imp.getOriginalFileInfo() != null)
			{
				sb.append("# File\t").append(imp.getOriginalFileInfo().directory)
						.append(imp.getOriginalFileInfo().fileName).append(newLine);
			}
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
		sb.append("Signal / Noise");
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
	private void addToResultTable(int i, int id, int[] result, double sum, double noise, double intensityAboveBackground)
	{
		resultsWindow.append(buildResultEntry(i, id, result, sum, noise, intensityAboveBackground));
	}

	private String buildResultEntry(int i, int id, int[] result, double sum, double noise,
			double intensityAboveBackground)
	{
		int absoluteHeight = getAbsoluteHeight(result, noise);
		double relativeHeight = getRelativeHeight(result, noise, absoluteHeight);

		StringBuilder sb = new StringBuilder();
		sb.append(i).append("\t");
		sb.append(id).append("\t");
		sb.append(result[RESULT_X]).append("\t");
		sb.append(result[RESULT_Y]).append("\t");
		sb.append(result[RESULT_Z]).append("\t");
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
		sb.append(IJ.d2s((result[RESULT_MAX_VALUE] / noise), 2)).append(newLine);
		return sb.toString();
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
		Roi roi = imp.getRoi();

		if (roi != null && roi.isArea())
		{
			Rectangle roiBounds = roi.getBounds();

			// Check if this ROI covers the entire image
			if (roi.getType() == Roi.RECTANGLE && roiBounds.width == maxx && roiBounds.height == maxy)
				return 0;

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
			int xOffset = roiBounds.x;
			int yOffset = roiBounds.y;
			int rwidth = roiBounds.width;
			int rheight = roiBounds.height;

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
						for (int index = getIndex(x + xOffset, y + yOffset, 0); index < maxx_maxy_maxz; index += maxx_maxy)
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
			ImageProcessor ipMask = mask.getProcessor();

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
			ImageStack stack = mask.getStack();
			int c = mask.getChannel();
			int f = mask.getFrame();
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
		int bitDepth = imp.getBitDepth();
		if (bitDepth != 8 && bitDepth != 16)
			return imp.getProcessor().getHistogram();

		int size = (int) Math.pow(2, bitDepth);

		int[] data = new int[size];

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
		int bitDepth = imp.getBitDepth();
		if (bitDepth != 8 && bitDepth != 16)
			return imp.getProcessor().getHistogram();

		int size = (int) Math.pow(2, bitDepth);

		int[] data = new int[size];

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
				return round(backgroundParameter);

			case BACKGROUND_AUTO_THRESHOLD:
				return round(stats[STATS_BACKGROUND]);

			case BACKGROUND_MEAN:
				return round(stats[STATS_AV_BACKGROUND]);

			case BACKGROUND_STD_DEV_ABOVE_MEAN:
				return round(stats[STATS_AV_BACKGROUND] + backgroundParameter * stats[STATS_SD_BACKGROUND]);

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
	 * Find all local maxima (irrespective whether they finally qualify as maxima or not)
	 * 
	 * @param image
	 *            The image to be analyzed
	 * @param roi
	 *            The image ROI bounds
	 * @param types
	 *            A byte image, same size as ip, where the maximum points are marked as MAXIMUM
	 * @param direction
	 *            The direction array. Will be marked with the greatest uphill direction of each point.
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
	private Coordinate[] getSortedMaxPoints(int[] image, short[] maxima, byte[] types, int globalMin, int threshold)
	{
		ArrayList<Coordinate> maxPoints = new ArrayList<Coordinate>(500);
		int[] pList = null; // working list for expanding local plateaus

		short id = 0;
		int[] xyz = new int[3];
		int x, y, z;

		//int pCount = 0;

		for (int i = image.length; i-- > 0;)
		{
			if ((types[i] & (EXCLUDED | MAX_AREA | PLATEAU)) != 0)
				continue;
			int v = image[i];
			if (v < threshold)
				continue;
			if (v == globalMin)
				continue;

			getXYZ(i, xyz);

			x = xyz[0];
			y = xyz[1];
			z = xyz[2];

			/*
			 * check whether we have a local maximum.
			 */
			boolean isInnerXY = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);
			boolean isInnerXYZ = (zlimit == 0) ? isInnerXY : isInnerXY && (z != 0 && z != zlimit);
			boolean isMax = true, equalNeighbour = false;

			// It is more likely that the z stack will be out-of-bounds.
			// Adopt the xy limit lookup and process z lookup separately

			for (int d = dStart; d-- > 0;)
			{
				if (isInnerXYZ || (isInnerXY && isWithinZ(z, d)) || isWithinXYZ(x, y, z, d))
				{
					int vNeighbor = image[i + offset[d]];
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
				if (id >= Short.MAX_VALUE)
				{
					IJ.log("The number of potential maxima exceeds the search capacity: " + Short.MAX_VALUE +
							". Try using a denoising/smoothing filter.");
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

		if (ImageJHelper.isInterrupted())
			return null;

		Collections.sort(maxPoints);

		// Build a map between the original id and the new id following the sort
		short[] idMap = new short[maxPoints.size() + 1];

		// Label the points
		for (int i = 0; i < maxPoints.size(); i++)
		{
			short newId = (short) (i + 1);
			short oldId = maxPoints.get(i).id;
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
	private boolean expandMaximum(int[] image, short[] maxima, byte[] types, int globalMin, int threshold, int index0,
			int v0, short id, ArrayList<Coordinate> maxPoints, int[] pList)
	{
		types[index0] |= LISTED | PLATEAU; // mark first point as listed
		int listI = 0; // index of current search element in the list
		int listLen = 1; // number of elements in the list

		// we create a list of connected points and start the list at the current maximum
		pList[listI] = index0;

		// Calculate the center of plateau
		boolean isPlateau = true;
		int[] xyz = new int[3];

		do
		{
			int index1 = pList[listI];
			getXYZ(index1, xyz);
			int x1 = xyz[0];
			int y1 = xyz[1];
			int z1 = xyz[2];

			// It is more likely that the z stack will be out-of-bounds.
			// Adopt the xy limit lookup and process z lookup separately

			boolean isInnerXY = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);
			boolean isInnerXYZ = (zlimit == 0) ? isInnerXY : isInnerXY && (z1 != 0 && z1 != zlimit);

			for (int d = dStart; d-- > 0;)
			{
				if (isInnerXYZ || (isInnerXY && isWithinZ(z1, d)) || isWithinXYZ(x1, y1, z1, d))
				{
					int index2 = index1 + offset[d];
					if ((types[index2] & IGNORE) != 0)
					{
						// This has been done already, ignore this point
						continue;
					}

					int v2 = image[index2];

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

		// Calculate the maxima origin
		for (int i = listLen; i-- > 0;)
		{
			int index = pList[i];
			types[index] &= ~LISTED; // reset attributes no longer needed

			if (isPlateau)
			{
				getXYZ(index, xyz);

				int x = xyz[0];
				int y = xyz[1];
				int z = xyz[2];

				double d = (xEqual - x) * (xEqual - x) + (yEqual - y) * (yEqual - y) + (zEqual - z) * (zEqual - z);

				if (d < dMax)
				{
					d = dMax;
					iMax = i;
				}

				types[index] |= MAX_AREA;
				maxima[index] = id;
			}
		}

		// Assign the maximum
		if (isPlateau)
		{
			int index = pList[iMax];
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
	private void assignMaxima(short[] maxima, Coordinate[] maxPoints, ArrayList<int[]> resultsArray)
	{
		int[] xyz = new int[3];

		for (Coordinate maximum : maxPoints)
		{
			getXYZ(maximum.index, xyz);

			int x = xyz[0];
			int y = xyz[1];
			int z = xyz[2];

			maxima[maximum.index] = maximum.id;

			int[] result = new int[RESULT_LENGTH];
			result[RESULT_X] = x;
			result[RESULT_Y] = y;
			result[RESULT_Z] = z;
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
	private void assignPointsToMaxima(int[] image, int[] histogram, byte[] types, double[] stats, short[] maxima)
	{
		int background = round(stats[STATS_BACKGROUND]);
		int maxValue = round(stats[STATS_MAX]);

		// Create an array with the coordinates of all points between the threshold value and the max-1 value
		int arraySize = 0;
		for (int v = background; v < maxValue; v++)
			arraySize += histogram[v];

		if (arraySize == 0)
			return;

		int[] coordinates = new int[arraySize]; // from pixel coordinates, low bits x, high bits y
		int highestValue = 0;
		int offset = 0;
		int[] levelStart = new int[maxValue + 1];
		for (int v = background; v < maxValue; v++)
		{
			levelStart[v] = offset;
			offset += histogram[v];
			if (histogram[v] > 0)
				highestValue = v;
		}
		int[] levelOffset = new int[highestValue + 1];
		for (int i = image.length; i-- > 0;)
		{
			if ((types[i] & EXCLUDED) != 0)
				continue;

			int v = image[i];
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
				int n = processLevel(image, types, maxima, levelStart[level], remaining, coordinates, background);
				remaining -= n; // number of points processed

				// If nothing was done then stop
				if (n == 0)
					break;
			}

			if ((processedLevel % 64 == 0) && ImageJHelper.isInterrupted())
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
	private int processLevel(int[] image, byte[] types, short[] maxima, int levelStart, int levelNPoints,
			int[] coordinates, int background)
	{
		//int[] pList = new int[0]; // working list for expanding local plateaus
		int nChanged = 0;
		int nUnchanged = 0;
		int[] xyz = new int[3];

		for (int i = 0, p = levelStart; i < levelNPoints; i++, p++)
		{
			int index = coordinates[p];

			if ((types[index] & (EXCLUDED | MAX_AREA)) != 0)
			{
				// This point can be ignored
				nChanged++;
				continue;
			}

			getXYZ(index, xyz);

			// Extract the point coordinate
			int x = xyz[0];
			int y = xyz[1];
			int z = xyz[2];

			int v = image[index];

			// It is more likely that the z stack will be out-of-bounds.
			// Adopt the xy limit lookup and process z lookup separately
			boolean isInnerXY = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);
			boolean isInnerXYZ = (zlimit == 0) ? isInnerXY : isInnerXY && (z != 0 && z != zlimit);

			// Check for the highest neighbour

			// TODO - Try out using a Sobel operator to assign the gradient direction. Follow the steepest gradient.

			int dMax = -1;
			int vMax = v;
			for (int d = dStart; d-- > 0;)
			{
				if (isInnerXYZ || (isInnerXY && isWithinZ(z, d)) || isWithinXYZ(x, y, z, d))
				{
					int index2 = index + offset[d];
					int vNeighbor = image[index2];
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
	private void expandPlateau(int[] image, short[] maxima, byte[] types, int index0, int v0, short id, int[] pList)
	{
		types[index0] |= LISTED; // mark first point as listed
		int listI = 0; // index of current search element in the list
		int listLen = 1; // number of elements in the list

		// we create a list of connected points and start the list at the current maximum
		pList[listI] = index0;

		int[] xyz = new int[3];

		do
		{
			int index1 = pList[listI];
			getXYZ(index1, xyz);
			int x1 = xyz[0];
			int y1 = xyz[1];
			int z1 = xyz[2];

			// It is more likely that the z stack will be out-of-bounds.
			// Adopt the xy limit lookup and process z lookup separately

			boolean isInnerXY = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);
			boolean isInnerXYZ = (zlimit == 0) ? isInnerXY : isInnerXY && (z1 != 0 && z1 != zlimit);

			for (int d = dStart; d-- > 0;)
			{
				if (isInnerXYZ || (isInnerXY && isWithinZ(z1, d)) || isWithinXYZ(x1, y1, z1, d))
				{
					int index2 = index1 + offset[d];
					if ((types[index2] & IGNORE) != 0)
					{
						// This has been done already, ignore this point
						continue;
					}

					int v2 = image[index2];

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
			int index = pList[i];
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
			ArrayList<int[]> resultsArray, short[] maxima)
	{
		// Build an array containing the threshold for each peak.
		// Note that maxima are numbered from 1
		int nMaxima = resultsArray.size();
		int[] peakThreshold = new int[nMaxima + 1];
		for (int i = 1; i < peakThreshold.length; i++)
		{
			int v0 = resultsArray.get(i - 1)[RESULT_MAX_VALUE];
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
	private void calculateInitialResults(int[] image, short[] maxima, ArrayList<int[]> resultsArray)
	{
		int nMaxima = resultsArray.size();

		// Maxima are numbered from 1
		int[] count = new int[nMaxima + 1];
		int[] intensity = new int[nMaxima + 1];

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
	private void calculateNativeResults(int[] image, short[] maxima, ArrayList<int[]> resultsArray,
			int originalNumberOfPeaks)
	{
		// Maxima are numbered from 1
		int[] intensity = new int[originalNumberOfPeaks + 1];
		int[] max = new int[originalNumberOfPeaks + 1];

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
	private void locateMaxima(int[] image, int[] searchImage, short[] maxima, byte[] types,
			ArrayList<int[]> resultsArray, int originalNumberOfPeaks, int centreMethod, double centreParameter)
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
			short maximaId = (short) result[RESULT_PEAK_ID];
			int index = getIndex(result[RESULT_X], result[RESULT_Y], result[RESULT_Z]);
			int listLen = findMaximaCoords(image, maxima, types, index, maximaId, result[RESULT_HIGHEST_SADDLE_VALUE],
					pList);
			//IJ.log("maxima size > saddle = " + listLen);

			// Find the boundaries of the coordinates
			int[] min_xyz = new int[] { maxx, maxy, maxz };
			int[] max_xyz = new int[] { 0, 0, 0 };
			int[] xyz = new int[3];
			for (int listI = listLen; listI-- > 0;)
			{
				int index1 = pList[listI];
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
			int[] dimensions = new int[3];
			for (int i = 3; i-- > 0;)
				dimensions[i] = max_xyz[i] - min_xyz[i] + 1;

			int[] subImage = extractSubImage(image, maxima, min_xyz, dimensions, maximaId,
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
					int[] shift = new int[3];
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
	private int findMaximaCoords(int[] image, short[] maxima, byte[] types, int index0, short maximaId,
			int saddleValue, int[] pList)
	{
		types[index0] |= LISTED; // mark first point as listed
		int listI = 0; // index of current search element in the list
		int listLen = 1; // number of elements in the list

		// we create a list of connected points and start the list at the current maximum
		pList[listI] = index0;

		int[] xyz = new int[3];

		do
		{
			int index1 = pList[listI];
			getXYZ(index1, xyz);
			int x1 = xyz[0];
			int y1 = xyz[1];
			int z1 = xyz[2];

			boolean isInnerXY = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);
			boolean isInnerXYZ = (zlimit == 0) ? isInnerXY : isInnerXY && (z1 != 0 && z1 != zlimit);

			for (int d = dStart; d-- > 0;)
			{
				if (isInnerXYZ || (isInnerXY && isWithinZ(z1, d)) || isWithinXYZ(x1, y1, z1, d))
				{
					int index2 = index1 + offset[d];
					if ((types[index2] & IGNORE) != 0 || maxima[index2] != maximaId)
					{
						// This has been done already, ignore this point
						continue;
					}

					int v2 = image[index2];

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
			int index = pList[i];
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
	private int[] extractSubImage(int[] image, short[] maxima, int[] min_xyz, int[] dimensions, short maximaId,
			int minValue)
	{
		int[] subImage = new int[dimensions[0] * dimensions[1] * dimensions[2]];

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
		int blockSize = dimensions[0] * dimensions[1];

		if (count == 1)
		{
			// There is only one maximum pixel
			int[] xyz = new int[3];
			xyz[2] = index / (blockSize);
			int mod = index % (blockSize);
			xyz[1] = mod / dimensions[0];
			xyz[0] = mod % dimensions[0];
			return xyz;
		}

		// Find geometric mean
		double[] centre = new double[3];
		for (int i = image.length; i-- > 0;)
		{
			if (maxValue == image[i])
			{
				int[] xyz = new int[3];
				xyz[2] = i / (blockSize);
				int mod = i % (blockSize);
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
		int[] centre = findCentreMaxValue(subImage, dimensions);
		double[] com = new double[] { centre[0], centre[1], centre[2] };

		// Iterate until convergence
		double distance;
		int iter = 0;
		do
		{
			double[] newCom = findCentreOfMass(subImage, dimensions, range, com);
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
		int[] centre = convertCentre(com);

		int[] min = new int[3];
		int[] max = new int[3];
		for (int i = 3; i-- > 0;)
		{
			min[i] = centre[i] - range;
			max[i] = centre[i] + range;
			if (min[i] < 0)
				min[i] = 0;
			if (max[i] >= dimensions[i] - 1)
				max[i] = dimensions[i] - 1;
		}

		int blockSize = dimensions[0] * dimensions[1];

		double[] newCom = new double[3];
		long sum = 0;
		for (int z = min[2]; z <= max[2]; z++)
		{
			for (int y = min[1]; y <= max[1]; y++)
			{
				int index = blockSize * z + dimensions[0] * y + min[0];
				for (int x = min[0]; x <= max[0]; x++, index++)
				{
					int value = subImage[index];
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

		int blockSize = dimensions[0] * dimensions[1];
		float[] projection = new float[blockSize];

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

		GaussianFit gf = new GaussianFit();
		double[] fitParams = gf.fit(projection, dimensions[0], dimensions[1]);

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
				int index = blockSize * z;
				int value = subImage[index];
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
	private void findSaddlePoints(int[] image, byte[] types, ArrayList<int[]> resultsArray, short[] maxima,
			ArrayList<LinkedList<int[]>> saddlePoints)
	{
		// Initialise the saddle points
		int nMaxima = resultsArray.size();
		for (int i = 0; i < nMaxima + 1; i++)
			saddlePoints.add(new LinkedList<int[]>());

		int maxPeakSize = getMaxPeakSize(resultsArray);
		int[][] pList = new int[maxPeakSize][2]; // here we enter points starting from a maximum (index,value)
		int[] xyz = new int[3];

		/* Process all the maxima */
		for (int[] result : resultsArray)
		{
			int x0 = result[RESULT_X];
			int y0 = result[RESULT_Y];
			int z0 = result[RESULT_Z];
			short id = (short) result[RESULT_PEAK_ID];
			int index0 = getIndex(x0, y0, z0);

			int v0 = result[RESULT_MAX_VALUE];

			// List of saddle highest values with every other peak
			int[] highestSaddleValue = new int[nMaxima + 1];

			types[index0] |= LISTED; // mark first point as listed
			int listI = 0; // index of current search element in the list
			int listLen = 1; // number of elements in the list

			// we create a list of connected points and start the list at the current maximum
			pList[0][0] = index0;
			pList[0][1] = v0;

			do
			{
				int index1 = pList[listI][0];
				int v1 = pList[listI][1];

				getXYZ(index1, xyz);
				int x1 = xyz[0];
				int y1 = xyz[1];
				int z1 = xyz[2];

				// It is more likely that the z stack will be out-of-bounds.
				// Adopt the xy limit lookup and process z lookup separately

				boolean isInnerXY = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);
				boolean isInnerXYZ = (zlimit == 0) ? isInnerXY : isInnerXY && (z1 != 0 && z1 != zlimit);

				// Check for the highest neighbour
				for (int d = dStart; d-- > 0;)
				{
					if (isInnerXYZ || (isInnerXY && isWithinZ(z1, d)) || isWithinXYZ(x1, y1, z1, d))
					{
						// Get the coords
						int index2 = index1 + offset[d];

						if ((types[index2] & IGNORE) != 0)
						{
							// This has been done already, ignore this point
							continue;
						}

						short id2 = maxima[index2];

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
							int v2 = image[index2];

							// Take the lower of the two points as the saddle
							int minV;
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
				int index = pList[i][0];

				types[index] &= ~LISTED; // reset attributes no longer needed
			}

			// Find the highest saddle
			short highestNeighbourPeakId = 0;
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
						highestNeighbourPeakId = (short) id2;
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
	private void analysePeaks(ArrayList<int[]> resultsArray, int[] image, short[] maxima, double[] stats)
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
	private void mergeSubPeaks(ArrayList<int[]> resultsArray, int[] image, short[] maxima, int minSize, int peakMethod,
			double peakParameter, double[] stats, ArrayList<LinkedList<int[]>> saddlePoints, boolean isLogging,
			boolean restrictAboveSaddle)
	{
		// Create an array containing the mapping between the original peak Id and the current Id that the peak has been
		// mapped to.
		short[] peakIdMap = new short[resultsArray.size() + 1];
		for (short i = 0; i < peakIdMap.length; i++)
			peakIdMap[i] = i;

		// Process all the peaks for the minimum height. Process in order of saddle height
		sortDescResults(resultsArray, SORT_SADDLE_HEIGHT, stats);

		for (int[] result : resultsArray)
		{
			short peakId = (short) result[RESULT_PEAK_ID];
			LinkedList<int[]> saddles = saddlePoints.get(peakId);

			// Check if this peak has been reassigned or has no neighbours
			if (peakId != peakIdMap[peakId])
				continue;

			int[] highestSaddle = findHighestNeighbourSaddle(peakIdMap, saddles, peakId);

			int peakBase = (highestSaddle == null) ? round(stats[STATS_BACKGROUND]) : highestSaddle[1];

			int threshold = getPeakHeight(peakMethod, peakParameter, stats, result[RESULT_MAX_VALUE]);

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
					short neighbourPeakId = peakIdMap[highestSaddle[SADDLE_PEAK_ID]];
					int[] neighbourResult = findResult(resultsArray, neighbourPeakId);

					mergePeak(image, maxima, peakIdMap, peakId, result, neighbourPeakId, neighbourResult, saddles,
							saddlePoints.get(neighbourPeakId), highestSaddle, false);
				}
			}
		}

		if (isLogging)
			IJ.log("Height filter : Number of peaks = " + countPeaks(peakIdMap));
		if (ImageJHelper.isInterrupted())
			return;

		// Process all the peaks for the minimum size. Process in order of smallest first
		sortAscResults(resultsArray, SORT_COUNT, stats);

		for (int[] result : resultsArray)
		{
			short peakId = (short) result[RESULT_PEAK_ID];

			// Check if this peak has been reassigned
			if (peakId != peakIdMap[peakId])
				continue;

			if (result[RESULT_COUNT] < minSize)
			{
				// This peak is not large enough, merge into the neighbour peak

				LinkedList<int[]> saddles = saddlePoints.get(peakId);
				int[] highestSaddle = findHighestNeighbourSaddle(peakIdMap, saddles, peakId);

				if (highestSaddle == null)
				{
					removePeak(image, maxima, peakIdMap, result, peakId);
				}
				else
				{
					// Find the neighbour peak (use the map because the neighbour may have been merged)
					short neighbourPeakId = peakIdMap[highestSaddle[SADDLE_PEAK_ID]];
					int[] neighbourResult = findResult(resultsArray, neighbourPeakId);

					mergePeak(image, maxima, peakIdMap, peakId, result, neighbourPeakId, neighbourResult, saddles,
							saddlePoints.get(neighbourPeakId), highestSaddle, false);
				}
			}
		}

		if (isLogging)
			IJ.log("Size filter : Number of peaks = " + countPeaks(peakIdMap));
		if (ImageJHelper.isInterrupted())
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
				short peakId = (short) result[RESULT_PEAK_ID];

				// Check if this peak has been reassigned
				if (peakId != peakIdMap[peakId])
					continue;

				if (result[RESULT_COUNT_ABOVE_SADDLE] < minSize)
				{
					// This peak is not large enough, merge into the neighbour peak

					LinkedList<int[]> saddles = saddlePoints.get(peakId);
					int[] highestSaddle = findHighestNeighbourSaddle(peakIdMap, saddles, peakId);

					if (highestSaddle == null)
					{
						// No neighbour so just remove
						mergePeak(image, maxima, peakIdMap, peakId, result, (short) 0, null, null, null, null, true);
					}
					else
					{
						// Find the neighbour peak (use the map because the neighbour may have been merged)
						short neighbourPeakId = peakIdMap[highestSaddle[SADDLE_PEAK_ID]];
						int[] neighbourResult = findResult(resultsArray, neighbourPeakId);

						// Note: Ensure the peak counts above the saddle are updated.
						mergePeak(image, maxima, peakIdMap, peakId, result, neighbourPeakId, neighbourResult, saddles,
								saddlePoints.get(neighbourPeakId), highestSaddle, true);

						// Check for interruption after each merge
						if (ImageJHelper.isInterrupted())
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

	private void removePeak(int[] image, short[] maxima, short[] peakIdMap, int[] result, short peakId)
	{
		// No neighbour so just remove
		mergePeak(image, maxima, peakIdMap, peakId, result, (short) 0, null, null, null, null, false);
	}

	private int countPeaks(short[] peakIdMap)
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

	private void updateSaddleDetails(ArrayList<int[]> resultsArray, short[] peakIdMap)
	{
		for (int[] result : resultsArray)
		{
			short neighbourPeakId = peakIdMap[result[RESULT_SADDLE_NEIGHBOUR_ID]];

			// Ensure the peak is not marked as a saddle with itself
			if (neighbourPeakId == result[RESULT_PEAK_ID])
				neighbourPeakId = 0;

			result[RESULT_SADDLE_NEIGHBOUR_ID] = neighbourPeakId;
			if (result[RESULT_SADDLE_NEIGHBOUR_ID] == 0)
			{
				result[RESULT_COUNT_ABOVE_SADDLE] = result[RESULT_COUNT];
				result[RESULT_HIGHEST_SADDLE_VALUE] = 0;
			}
		}
	}

	/**
	 * Find the highest saddle that has not been assigned to the specified peak
	 * 
	 * @param peakIdMap
	 * @param peakId
	 * @param saddles
	 * @return
	 */
	private int[] findHighestNeighbourSaddle(short[] peakIdMap, LinkedList<int[]> saddles, short peakId)
	{
		int[] maxSaddle = null;
		int max = 0;
		for (int[] saddle : saddles)
		{
			// Find foci that have not been reassigned to this peak (or nothing)
			short neighbourPeakId = peakIdMap[saddle[SADDLE_PEAK_ID]];
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
	private int[] findHighestSaddle(short[] peakIdMap, LinkedList<int[]> saddles, int peakId)
	{
		int[] maxSaddle = null;
		int max = 0;
		for (int[] saddle : saddles)
		{
			// Use the map to ensure the original saddle id corresponds to the current peaks
			short neighbourPeakId = peakIdMap[saddle[SADDLE_PEAK_ID]];
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
	private void mergePeak(int[] image, short[] maxima, short[] peakIdMap, short peakId, int[] result,
			short neighbourPeakId, int[] neighbourResult, LinkedList<int[]> peakSaddles,
			LinkedList<int[]> neighbourSaddles, int[] highestSaddle, boolean updatePeakAboveSaddle)
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
				int saddlePeakId = peakIdMap[peakSaddle[SADDLE_PEAK_ID]];
				int[] neighbourSaddle = findHighestSaddle(peakIdMap, neighbourSaddles, saddlePeakId);
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
			int[] newHighestSaddle = findHighestNeighbourSaddle(peakIdMap, neighbourSaddles, neighbourPeakId);
			if (newHighestSaddle != null)
			{
				reanalysePeak(image, maxima, peakIdMap, neighbourPeakId, newHighestSaddle, neighbourResult,
						updatePeakAboveSaddle);
			}
			else
			{
				neighbourResult[RESULT_SADDLE_NEIGHBOUR_ID] = 0;
				neighbourResult[RESULT_HIGHEST_SADDLE_VALUE] = 0;
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
	private void reanalysePeak(int[] image, short[] maxima, short[] peakIdMap, short peakId, int[] saddle,
			int[] result, boolean updatePeakAboveSaddle)
	{
		if (updatePeakAboveSaddle)
		{
			int peakSize = 0;
			int peakIntensity = 0;
			int saddleHeight = saddle[1];
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
	private void reassignMaxima(short[] maxima, short[] peakIdMap)
	{
		for (int i = maxima.length; i-- > 0;)
		{
			if (maxima[i] > 0)
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
	private void removeEdgeMaxima(ArrayList<int[]> resultsArray, int[] image, short[] maxima, double[] stats,
			boolean isLogging)
	{
		// Build a look-up table for all the peak IDs
		short maxId = 0;
		for (int[] result : resultsArray)
			if (maxId < (short) result[RESULT_PEAK_ID])
				maxId = (short) result[RESULT_PEAK_ID];

		short[] peakIdMap = new short[maxId + 1];
		for (short i = 0; i < peakIdMap.length; i++)
			peakIdMap[i] = i;

		// Set the look-up to zero if the peak contains edge pixels
		for (int z = maxz; z-- > 0;)
		{
			// Look at top and bottom column
			for (int y = maxy, i = getIndex(0, 0, z), ii = getIndex(maxx - 1, 0, z); y-- > 0; i += maxx, ii += maxx)
			{
				peakIdMap[maxima[i]] = 0;
				peakIdMap[maxima[ii]] = 0;
			}
			// Look at top and bottom row
			for (int x = maxx, i = getIndex(0, 0, z), ii = getIndex(0, maxy - 1, z); x-- > 0; i++, ii++)
			{
				peakIdMap[maxima[i]] = 0;
				peakIdMap[maxima[ii]] = 0;
			}
		}

		// Mark maxima to be removed
		for (int[] result : resultsArray)
		{
			short peakId = (short) result[RESULT_PEAK_ID];
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
	private void sortDescResults(ArrayList<int[]> resultsArray, int sortIndex, double[] stats)
	{
		Collections.sort(resultsArray, new ResultDescComparator(getResultIndex(sortIndex, resultsArray, stats)));
	}

	/**
	 * Sort the results using the specified index in ascending order
	 * 
	 * @param resultsArray
	 * @param sortIndex
	 */
	private void sortAscResults(ArrayList<int[]> resultsArray, int sortIndex, double[] stats)
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
		public short id;
		public int value;

		public Coordinate(int index, short id, int value)
		{
			this.index = index;
			this.id = id;
			this.value = value;
		}

		public Coordinate(int x, int y, int z, short id, int value)
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

	private void logError(String message)
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
}
