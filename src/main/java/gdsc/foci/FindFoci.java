package gdsc.foci;

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

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.TextField;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
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
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import gdsc.UsageTracker;
import gdsc.core.ij.Utils;
import gdsc.core.logging.MemoryLogger;
import gdsc.core.threshold.AutoThreshold;
import gdsc.core.utils.Maths;
import gdsc.core.utils.UnicodeReader;
import gdsc.foci.FindFociBaseProcessor.ObjectAnalysisResult;
import gdsc.foci.model.FindFociModel;
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
public class FindFoci implements PlugIn, MouseListener, FindFociProcessor
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
			// Options in square brackets [] were changed to brackets ()
			if (value.length() > 0)
			{
				if (value.charAt(0) == '[' && value.charAt(value.length() - 1) == ']')
					value = '(' + value.substring(1, value.length() - 1) + ')';
			}
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

	private static ArrayList<FindFociResult> lastResultsArray = null;
	static int isGaussianFitEnabled = 0;
	static String newLine = System.getProperty("line.separator");

	private static String BATCH_INPUT_DIRECTORY = "findfoci.batchInputDirectory";
	private static String BATCH_MASK_DIRECTORY = "findfoci.batchMaskDirectory";
	private static String BATCH_PARAMETER_FILE = "findfoci.batchParameterFile";
	private static String BATCH_OUTPUT_DIRECTORY = "findfoci.batchOutputDirectory";
	private static String BATCH_MULTI_THREAD = "findfoci.batchMultiThread";
	private static String SEARCH_CAPACITY = "findfoci.searchCapacity";
	private static String EMPTY_FIELD = "findfoci.emptyField";
	private static String batchInputDirectory = Prefs.get(BATCH_INPUT_DIRECTORY, "");
	private static String batchMaskDirectory = Prefs.get(BATCH_MASK_DIRECTORY, "");
	private static String batchParameterFile = Prefs.get(BATCH_PARAMETER_FILE, "");
	private static String batchOutputDirectory = Prefs.get(BATCH_OUTPUT_DIRECTORY, "");
	private static boolean batchMultiThread = Prefs.get(BATCH_MULTI_THREAD, true);
	static int searchCapacity = (int) Prefs.get(SEARCH_CAPACITY, Short.MAX_VALUE);
	private static String emptyField = Prefs.get(EMPTY_FIELD, "");
	private TextField textParamFile;

	//@formatter:off
	
	/**
	 * List of background threshold methods for the dialog
	 */
	public final static String[] backgroundMethods = { 
			"Absolute", 
			"Mean", 
			"Std.Dev above mean", 
			"Auto threshold",
			"Min Mask/ROI", "None" };

	/**
	 * List of search methods for the dialog
	 */
	public final static String[] searchMethods = { 
			"Above background", 
			"Fraction of peak - background",
			"Half peak value" };

	/**
	 * List of peak height methods for the dialog
	 */
	public final static String[] peakMethods = { 
			"Absolute height", 
			"Relative height", 
			"Relative above background" };

	/**
	 * List of peak height methods for the dialog
	 */
	public final static String[] maskOptions = { 
			"(None)", 
			"Peaks", 
			"Threshold", 
			"Peaks above saddle",
			"Threshold above saddle", 
			"Fraction of intensity", 
			"Fraction height above background" };

	/**
	 * The list of recognised methods for sorting the results
	 */
	public final static String[] sortIndexMethods = { 
			"Size", 
			"Total intensity", 
			"Max value", 
			"Average intensity",
			"Total intensity minus background", 
			"Average intensity minus background", 
			"X", 
			"Y", 
			"Z", 
			"Saddle height",
			"Size above saddle", 
			"Intensity above saddle", 
			"Absolute height", 
			"Relative height >Bg", 
			"Peak ID", 
			"XYZ",
			"Total intensity minus min", 
			"Average intensity minus min" };

	private static String[] centreMethods = { 
			"Max value (search image)", 
			"Max value (original image)",
			"Centre of mass (search image)", 
			"Centre of mass (original image)", 
			"Gaussian (search image)",
			"Gaussian (original image)" };
	//@formatter:on

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
			if (!name.contains("Mean")) // We do these from the computed statistics
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
	private String resultsDirectory = null;

	// Allow the results to be stored in memory for other plugins to access
	private static HashMap<String, ArrayList<FindFociResult>> memory = new HashMap<String, ArrayList<FindFociResult>>();
	private static ArrayList<String> memoryNames = new ArrayList<String>();

	// Used to record all the results into a single file during batch analysis
	private OutputStreamWriter allOut = null;
	private String emptyEntry = null;
	private FindFociBaseProcessor ffpStaged;
	private boolean optimisedProcessor = true;
	private AtomicInteger batchImages;
	private AtomicInteger batchOK;
	private AtomicInteger batchError;

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

		if (!isSupported(imp.getBitDepth()))
		{
			IJ.error(TITLE, "Only " + FindFoci.getSupported() + " images are supported");
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
		if (!isSupported(imp.getBitDepth()))
		{
			IJ.error(TITLE, "Only " + FindFoci.getSupported() + " images are supported");
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

		FindFociBaseProcessor ffp = createFFProcessor(imp);
		FindFociResults ffResult = findMaxima(ffp, imp, mask, backgroundMethod, backgroundParameter,
				autoThresholdMethod, searchMethod, searchParameter, maxPeaks, minSize, peakMethod, peakParameter,
				outputType, sortIndex, options, blur, centreMethod, centreParameter, fractionParameter);

		if (ffResult == null)
		{
			IJ.showStatus("Cancelled.");
			return;
		}

		// Get the results
		ImagePlus maximaImp = ffResult.mask;
		ArrayList<FindFociResult> resultsArray = ffResult.results;
		FindFociStatistics stats = ffResult.stats;

		// If we are outputting a results table or saving to file we can do the object analysis
		if ((options & OPTION_OBJECT_ANALYSIS) != 0 &&
				(((outputType & OUTPUT_RESULTS_TABLE) != 0) || resultsDirectory != null))
		{
			ImagePlus objectImp = ffp.doObjectAnalysis(mask, maximaImp, resultsArray,
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
			//	IJ.log("Background (noise) level = " + IJ.d2s(stats.STATS_BACKGROUND, 2));

			// There is some strange problem when using ImageJ's default results table when it asks the user
			// to save a previous list and then aborts the plugin if the user says Yes, No or Cancel leaving
			// the image locked.
			// So use a custom results table instead (or just Analyzer.setUnsavedMeasurements(false)).

			// Use a custom result table to avoid IJ bug
			createResultsWindow();
			for (int i = 0; i < resultsArray.size(); i++)
			{
				FindFociResult result = resultsArray.get(i);
				addToResultTable(ffp, i + 1, resultsArray.size() - i, result, stats);
			}
			flushResults();
			//if (!resultsArray.isEmpty())
			//	resultsWindow.append("");
		}

		// Record all the results to file
		if (resultsArray != null && resultsDirectory != null)
		{
			saveResults(ffp, generateId(imp), imp, mask, backgroundMethod, backgroundParameter, autoThresholdMethod,
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
	private static void addRoiToOverlay(ImagePlus imp, Roi[] rois)
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
	private static Overlay addRoiToOverlay(ImagePlus imp, Roi[] rois, Overlay overlay)
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

	private PointRoi[] createStackRoi(ArrayList<FindFociResult> resultsArray, int outputType)
	{
		final int nMaxima = resultsArray.size();
		final XYZ[] xyz = new XYZ[nMaxima];
		for (int i = 0; i < nMaxima; i++)
		{
			final FindFociResult xy = resultsArray.get(i);
			xyz[i] = new XYZ(i + 1, xy.x, xy.y, xy.z);
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

	private static PointRoi createPointRoi(int[] ids, int[] xpoints, int[] ypoints, int slice, int npoints,
			boolean hideLabels)
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

	private static PointRoi createRoi(ArrayList<FindFociResult> resultsArray, int outputType)
	{
		final int nMaxima = resultsArray.size();
		final int[] xpoints = new int[nMaxima];
		final int[] ypoints = new int[nMaxima];
		for (int i = 0; i < nMaxima; i++)
		{
			final FindFociResult xy = resultsArray.get(i);
			xpoints[i] = xy.x;
			ypoints[i] = xy.y;
		}
		PointRoi roi = new PointRoi(xpoints, ypoints, nMaxima);
		if ((outputType & OUTPUT_HIDE_LABELS) != 0)
			roi.setShowLabels(false);
		return roi;
	}

	private static Overlay createOverlay(ImagePlus imp, ImagePlus maximaImp)
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

	static ImagePlus showImage(ImagePlus imp, String title)
	{
		return showImage(imp, title, true);
	}

	static ImagePlus showImage(ImagePlus imp, String title, boolean show)
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

	private String saveResults(FindFociBaseProcessor ffp, String expId, ImagePlus imp, ImagePlus mask,
			int backgroundMethod, double backgroundParameter, String autoThresholdMethod, int searchMethod,
			double searchParameter, int maxPeaks, int minSize, int peakMethod, double peakParameter, int outputType,
			int sortIndex, int options, double blur, int centreMethod, double centreParameter, double fractionParameter,
			ArrayList<FindFociResult> resultsArray, FindFociStatistics stats, String resultsDirectory)
	{
		return saveResults(ffp, expId, imp, null, mask, null, backgroundMethod, backgroundParameter,
				autoThresholdMethod, searchMethod, searchParameter, maxPeaks, minSize, peakMethod, peakParameter,
				outputType, sortIndex, options, blur, centreMethod, centreParameter, fractionParameter, resultsArray,
				stats, resultsDirectory, null, null);
	}

	private String openBatchResultsFile()
	{
		String filename = resultsDirectory + File.separatorChar + "all.xls";
		try
		{
			FileOutputStream fos = new FileOutputStream(filename);
			allOut = new OutputStreamWriter(fos, "UTF-8");
			allOut.write("Image ID\tImage\t" + createResultsHeader(newLine));
			return filename;
		}
		catch (Exception e)
		{
			logError(e.getMessage());
			closeBatchResultsFile();
			return null;
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

	private class BatchResult implements Comparable<BatchResult>
	{
		final String entry;
		final int id;
		final int batchId;

		BatchResult(String entry, int id)
		{
			this.entry = entry;
			this.id = id;
			// The first characters before the tab are the batch Id
			int index = entry.indexOf('\t');
			batchId = Integer.parseInt(entry.substring(0, index));
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		public int compareTo(BatchResult that)
		{
			int result = this.batchId - that.batchId;
			return (result == 0) ? this.id - that.id : result;
		}
	}

	private void sortBatchResultsFile(String filename)
	{
		BufferedReader input = null;
		ArrayList<BatchResult> results = null;
		String header = null;
		try
		{
			FileInputStream fis = new FileInputStream(filename);
			input = new BufferedReader(new UnicodeReader(fis, null));
			header = input.readLine();
			String line;
			int id = 0;
			results = new ArrayList<FindFoci.BatchResult>();
			while ((line = input.readLine()) != null)
			{
				results.add(new BatchResult(line, ++id));
			}
		}
		catch (OutOfMemoryError e)
		{
			// In case the file is too big to read in for a sort
			results.clear();
			results = null;
			// Try and free the memory
			final Runtime runtime = Runtime.getRuntime();
			runtime.runFinalization();
			runtime.gc();
			logError(e.getMessage());
			return;
		}
		catch (Exception e)
		{
			logError(e.getMessage());
			return;
		}
		finally
		{
			try
			{
				if (input != null)
					input.close();
			}
			catch (Exception e)
			{
				logError(e.getMessage());
			}
		}

		Collections.sort(results);

		try
		{
			FileOutputStream fos = new FileOutputStream(filename);
			allOut = new OutputStreamWriter(fos, "UTF-8");
			// Add new lines because Buffered reader strips them
			allOut.write(header);
			allOut.write(newLine);
			for (BatchResult r : results)
			{
				allOut.write(r.entry);
				allOut.write(newLine);
			}
		}
		catch (Exception e)
		{
			logError(e.getMessage());
		}
		finally
		{
			closeBatchResultsFile();
		}
	}

	private String initialiseBatchPrefix(int batchId, String title)
	{
		return batchId + "\t" + title + "\t";
	}

	private synchronized void writeBatchResultsFile(String batchPrefix, List<String> results)
	{
		if (allOut == null)
			return;
		try
		{
			for (String result : results)
			{
				allOut.write(batchPrefix);
				allOut.write(result);
			}
		}
		catch (Exception e)
		{
			logError(e.getMessage());
			closeBatchResultsFile();
		}
	}

	private void writeEmptyObjectsToBatchResultsFile(ArrayList<String> batchResults,
			ObjectAnalysisResult objectAnalysisResult)
	{
		for (int id = 1; id <= objectAnalysisResult.numberOfObjects; id++)
			if (objectAnalysisResult.fociCount[id] == 0)
				batchResults.add(buildEmptyObjectResultEntry(id, objectAnalysisResult.objectState[id]));
	}

	private void writeEmptyBatchResultsFile(ArrayList<String> batchResults)
	{
		batchResults.add(buildEmptyResultEntry());
	}

	private String saveResults(FindFociBaseProcessor ffp, String expId, ImagePlus imp, int[] imageDimension,
			ImagePlus mask, int[] maskDimension, int backgroundMethod, double backgroundParameter,
			String autoThresholdMethod, int searchMethod, double searchParameter, int maxPeaks, int minSize,
			int peakMethod, double peakParameter, int outputType, int sortIndex, int options, double blur,
			int centreMethod, double centreParameter, double fractionParameter, ArrayList<FindFociResult> resultsArray,
			FindFociStatistics stats, String resultsDirectory, ObjectAnalysisResult objectAnalysisResult,
			String batchPrefix)
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
			final ArrayList<String> batchResults = (allOut == null) ? null : new ArrayList<String>(resultsArray.size());
			for (int i = 0; i < resultsArray.size(); i++)
			{
				FindFociResult result = resultsArray.get(i);
				xpoints[i] = result.x;
				ypoints[i] = result.y;

				buildResultEntry(ffp, sb, i + 1, resultsArray.size() - i, result, stats, newLine);
				final String resultEntry = sb.toString();
				out.write(resultEntry);
				if (batchResults != null)
					batchResults.add(resultEntry);
				sb.setLength(0);
			}
			// Check if we have a batch file
			if (batchResults != null)
			{
				if (objectAnalysisResult != null)
					// Record an empty record for empty objects
					writeEmptyObjectsToBatchResultsFile(batchResults, objectAnalysisResult);
				else if (resultsArray.isEmpty())
					// Record an empty record for batch processing
					writeEmptyBatchResultsFile(batchResults);

				writeBatchResultsFile(batchPrefix, batchResults);
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

	private static void configureOverlayRoi(Roi roi)
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
	private static Overlay removeOverlayPointRoi(Overlay overlay)
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.FindFociProcessor#findMaxima(ij.ImagePlus, ij.ImagePlus, int, double, java.lang.String, int,
	 * double, int, int, int, double, int, int, int, double, int, double, double)
	 */
	public FindFociResults findMaxima(ImagePlus imp, ImagePlus mask, int backgroundMethod, double backgroundParameter,
			String autoThresholdMethod, int searchMethod, double searchParameter, int maxPeaks, int minSize,
			int peakMethod, double peakParameter, int outputType, int sortIndex, int options, double blur,
			int centreMethod, double centreParameter, double fractionParameter)
	{
		lastResultsArray = null;

		if (imp == null)
			return null;
		if (!isSupported(imp.getBitDepth()))
		{
			IJ.error(TITLE, "Only " + FindFoci.getSupported() + " images are supported");
			return null;
		}

		// Support int[] or float[] images using a dedicated processor
		FindFociResults result = findMaxima(createFFProcessor(imp), imp, mask, backgroundMethod, backgroundParameter,
				autoThresholdMethod, searchMethod, searchParameter, maxPeaks, minSize, peakMethod, peakParameter,
				outputType, sortIndex, options, blur, centreMethod, centreParameter, fractionParameter);
		if (result != null)
			lastResultsArray = result.results;
		return result;
	}

	private FindFociResults findMaxima(FindFociBaseProcessor ffp, ImagePlus imp, ImagePlus mask, int backgroundMethod,
			double backgroundParameter, String autoThresholdMethod, int searchMethod, double searchParameter,
			int maxPeaks, int minSize, int peakMethod, double peakParameter, int outputType, int sortIndex, int options,
			double blur, int centreMethod, double centreParameter, double fractionParameter)
	{
		FindFociResults result = ffp.findMaxima(imp, mask, backgroundMethod, backgroundParameter, autoThresholdMethod,
				searchMethod, searchParameter, maxPeaks, minSize, peakMethod, peakParameter, outputType, sortIndex,
				options, blur, centreMethod, centreParameter, fractionParameter);
		return result;
	}

	private FindFociBaseProcessor createFFProcessor(ImagePlus imp)
	{
		final FindFociBaseProcessor ffp;
		if (isOptimisedProcessor())
		{
			ffp = (imp.getBitDepth() == 32) ? new FindFociOptimisedFloatProcessor()
					: new FindFociOptimisedIntProcessor();
		}
		else
		{
			ffp = (imp.getBitDepth() == 32) ? new FindFociFloatProcessor() : new FindFociIntProcessor();
		}
		return ffp;
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
		return FindFociBaseProcessor.applyBlur(imp, blur);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.FindFociProcessor#blur(ij.ImagePlus, double)
	 */
	public ImagePlus blur(ImagePlus imp, double blur)
	{
		// Use static method as the FindFociProcessor may be null
		return FindFociBaseProcessor.applyBlur(imp, blur);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.FindFociProcessor#findMaximaInit(ij.ImagePlus, ij.ImagePlus, ij.ImagePlus, int, java.lang.String,
	 * int)
	 */
	public FindFociInitResults findMaximaInit(ImagePlus originalImp, ImagePlus imp, ImagePlus mask,
			int backgroundMethod, String autoThresholdMethod, int options)
	{
		lastResultsArray = null;

		if (imp == null)
			return null;
		if (!isSupported(imp.getBitDepth()))
		{
			IJ.error(TITLE, "Only " + FindFoci.getSupported() + " images are supported");
			return null;
		}

		// Support int[] or float[] images using a dedicated processor
		ffpStaged = createFFProcessor(originalImp);

		return ffpStaged.findMaximaInit(originalImp, imp, mask, backgroundMethod, autoThresholdMethod, options);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.FindFociProcessor#clone(gdsc.foci.FindFociInitResults, gdsc.foci.FindFociInitResults)
	 */
	public FindFociInitResults clone(FindFociInitResults initResults, FindFociInitResults clonedInitResults)
	{
		return ffpStaged.clone(initResults, clonedInitResults);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.FindFociProcessor#findMaximaSearch(gdsc.foci.FindFociInitResults, int, double, int, double)
	 */
	public FindFociSearchResults findMaximaSearch(FindFociInitResults initResults, int backgroundMethod,
			double backgroundParameter, int searchMethod, double searchParameter)
	{
		lastResultsArray = null;
		return ffpStaged.findMaximaSearch(initResults, backgroundMethod, backgroundParameter, searchMethod,
				searchParameter);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.FindFociProcessor#findMaximaMergePeak(gdsc.foci.FindFociInitResults,
	 * gdsc.foci.FindFociSearchResults, int, double)
	 */
	public FindFociMergeTempResults findMaximaMergePeak(FindFociInitResults initResults,
			FindFociSearchResults searchResults, int peakMethod, double peakParameter)
	{
		lastResultsArray = null;
		return ffpStaged.findMaximaMergePeak(initResults, searchResults, peakMethod, peakParameter);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.FindFociProcessor#findMaximaMergeSize(gdsc.foci.FindFociInitResults,
	 * gdsc.foci.FindFociMergeTempResults, int)
	 */
	public FindFociMergeTempResults findMaximaMergeSize(FindFociInitResults initResults,
			FindFociMergeTempResults mergeResults, int minSize)
	{
		lastResultsArray = null;
		return ffpStaged.findMaximaMergeSize(initResults, mergeResults, minSize);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.FindFociProcessor#findMaximaMergeFinal(gdsc.foci.FindFociInitResults,
	 * gdsc.foci.FindFociMergeTempResults, int, int, double)
	 */
	public FindFociMergeResults findMaximaMergeFinal(FindFociInitResults initResults,
			FindFociMergeTempResults mergeResults, int minSize, int options, double blur)
	{
		lastResultsArray = null;
		return ffpStaged.findMaximaMergeFinal(initResults, mergeResults, minSize, options, blur);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.FindFociProcessor#findMaximaResults(gdsc.foci.FindFociInitResults, gdsc.foci.FindFociMergeResults,
	 * int, int, int, double)
	 */
	public FindFociResults findMaximaResults(FindFociInitResults initResults, FindFociMergeResults mergeResults,
			int maxPeaks, int sortIndex, int centreMethod, double centreParameter)
	{
		lastResultsArray = null;
		FindFociResults result = ffpStaged.findMaximaResults(initResults, mergeResults, maxPeaks, sortIndex,
				centreMethod, centreParameter);
		if (result != null)
			lastResultsArray = result.results;
		return result;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.FindFociProcessor#findMaximaPrelimResults(gdsc.foci.FindFociInitResults,
	 * gdsc.foci.FindFociMergeResults, int, int, int, double)
	 */
	public FindFociPrelimResults findMaximaPrelimResults(FindFociInitResults initResults,
			FindFociMergeResults mergeResults, int maxPeaks, int sortIndex, int centreMethod, double centreParameter)
	{
		lastResultsArray = null;
		FindFociPrelimResults result = ffpStaged.findMaximaPrelimResults(initResults, mergeResults, maxPeaks, sortIndex,
				centreMethod, centreParameter);
		if (result != null)
			lastResultsArray = (result.results == null) ? null
					: new ArrayList<FindFociResult>(Arrays.asList(result.results));
		return result;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.FindFociProcessor#findMaximaMaskResults(gdsc.foci.FindFociInitResults,
	 * gdsc.foci.FindFociMergeResults, gdsc.foci.FindFociResults, int, java.lang.String, java.lang.String, double)
	 */
	public FindFociResults findMaximaMaskResults(FindFociInitResults initResults, FindFociMergeResults mergeResults,
			FindFociPrelimResults prelimResults, int outputType, String autoThresholdMethod, String imageTitle,
			double fractionParameter)
	{
		lastResultsArray = null;
		FindFociResults result = ffpStaged.findMaximaMaskResults(initResults, mergeResults, prelimResults, outputType,
				autoThresholdMethod, imageTitle, fractionParameter);
		if (result != null)
			lastResultsArray = result.results;
		return result;
	}

	/**
	 * Show the result of the FindFoci algorithm. It is assumed the results were generated using the FindFoci staged
	 * methods.
	 * <p>
	 * The method must be called with the output from
	 * {@link #findMaxima(ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int, double, int, double)}
	 */
	public void showResults(ImagePlus imp, ImagePlus mask, int backgroundMethod, double backgroundParameter,
			String autoThresholdMethod, double searchParameter, int maxPeaks, int minSize, int peakMethod,
			double peakParameter, int outputType, int sortIndex, int options, FindFociResults results)
	{
		// Get the results
		ImagePlus maximaImp = results.mask;
		ArrayList<FindFociResult> resultsArray = results.results;
		FindFociStatistics stats = results.stats;

		// If we are outputting a results table or saving to file we can do the object analysis
		if ((options & OPTION_OBJECT_ANALYSIS) != 0 && ((outputType & OUTPUT_RESULTS_TABLE) != 0))
		{
			// Assume ffpStaged is not null as the user has already run through the FindFoci algorithm to get the results.

			ImagePlus objectImp = ffpStaged.doObjectAnalysis(mask, maximaImp, resultsArray,
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
				FindFociResult result = resultsArray.get(i);
				addToResultTable(ffpStaged, i + 1, resultsArray.size() - i, result, stats);
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

	private String createResultsHeader(ImagePlus imp, int[] dimension, FindFociStatistics stats, String newLine)
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
			sb.append("# Background\t").append(stats.background).append(newLine);
			sb.append("# Min\t").append(stats.regionMinimum).append(newLine);
			sb.append("# Max\t").append(stats.regionMaximum).append(newLine);
			sb.append("# Average\t").append(stats.regionAverage).append(newLine);
			sb.append("# Std.Dev\t").append(stats.regionStdDev).append(newLine);
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
		sb.append("Total (>Min)\t");
		sb.append("Av (>Min)\t");
		sb.append("% Signal\t");
		sb.append("% Signal (>Bg)\t");
		sb.append("% Signal (>Min)\t");
		sb.append("Signal / Noise\t");
		sb.append("Object\t");
		sb.append("State");
		sb.append(newLine);
		return sb.toString();
	}

	/**
	 * Add a result to the result table.
	 *
	 * @param ffp
	 *            the processor used to create the results
	 * @param i
	 *            Peak number
	 * @param id
	 *            the id
	 * @param result
	 *            The peak result
	 * @param stats
	 *            the stats
	 */
	private void addToResultTable(FindFociBaseProcessor ffp, int i, int id, FindFociResult result,
			FindFociStatistics stats)
	{
		// Buffer the output so that the table is displayed faster
		buildResultEntry(ffp, resultsBuffer, i, id, result, stats, "\n");
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

	private void buildResultEntry(FindFociBaseProcessor ffp, StringBuilder sb, int i, int id, FindFociResult result,
			FindFociStatistics stats, String newLine)
	{
		final double sum = stats.regionTotal;
		final double noise = stats.background;

		final double absoluteHeight = ffp.getAbsoluteHeight(result, noise);
		final double relativeHeight = ffp.getRelativeHeight(result, noise, absoluteHeight);

		final boolean floatImage = ffp.isFloatProcessor();

		sb.append(i).append("\t");
		sb.append(id).append("\t");
		// XY are pixel coordinates
		sb.append(result.x).append("\t");
		sb.append(result.y).append("\t");
		// Z should correspond to slice 
		sb.append(result.z + 1).append("\t");
		sb.append(result.count).append("\t");

		addValue(sb, result.maxValue, floatImage);
		addValue(sb, result.totalIntensity, floatImage);
		addValue(sb, result.highestSaddleValue, floatImage);

		sb.append(result.saddleNeighbourId).append("\t");
		addValue(sb, absoluteHeight, floatImage);
		addValue(sb, relativeHeight);
		sb.append(result.countAboveSaddle).append("\t");
		addValue(sb, result.intensityAboveSaddle, floatImage);
		addValue(sb, result.totalIntensity / result.count);
		addValue(sb, result.totalIntensityAboveBackground, floatImage);
		addValue(sb, result.totalIntensityAboveBackground / result.count);
		addValue(sb, result.totalIntensityAboveImageMinimum, floatImage);
		addValue(sb, result.totalIntensityAboveImageMinimum / result.count);
		addValue(sb, 100 * (result.totalIntensity / sum));
		addValue(sb, 100 * (result.totalIntensityAboveBackground / stats.totalAboveBackground));
		addValue(sb, 100 * (result.totalIntensityAboveImageMinimum / stats.totalAboveImageMinimum));
		addValue(sb, (result.maxValue / noise));
		sb.append(result.object).append("\t");
		sb.append(result.state);
		sb.append(newLine);

		resultsCount++;
	}

	private void addValue(StringBuilder sb, double value, boolean floatImage)
	{
		if (floatImage)
		{
			addValue(sb, value);
		}
		else
		{
			sb.append((int) value).append("\t");
		}
	}

	private void addValue(StringBuilder sb, double value)
	{
		sb.append(getFormat(value)).append("\t");
	}

	static String getFormat(double value, boolean floatImage)
	{
		if (floatImage)
		{
			return getFormat(value);
		}
		else
		{
			return Integer.toString((int) value);
		}
	}

	static String getFormat(double value)
	{
		if (value > 100)
			return IJ.d2s(value, 2);
		else
			return Utils.rounded(value, 4);
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

	private synchronized String buildEmptyResultEntry()
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
		IJ.log("ERROR - " + FindFoci.TITLE + ": " + message);
	}

	/**
	 * @return The results from the last call of FindFoci
	 */
	public static ArrayList<FindFociResult> getResults()
	{
		return lastResultsArray;
	}

	// Used for multi-threaded batch mode processing
	/** The total progress. */
	int progress, stepProgress, totalProgress;

	/**
	 * Show progress.
	 */
	private synchronized void showProgress()
	{
		if (++progress % stepProgress == 0)
		{
			if (Utils.showStatus("Frame: " + progress + " / " + totalProgress))
				IJ.showProgress(progress, totalProgress);
		}
	}

	private class Job
	{
		final String filename;
		final int batchId;

		Job(String filename, int batchId)
		{
			this.filename = filename;
			this.batchId = batchId;
		}

		Job()
		{
			this.filename = null;
			this.batchId = 0;
		}
	}

	/**
	 * Used to allow multi-threading of the fitting method
	 */
	private class Worker implements Runnable
	{
		volatile boolean finished = false;
		final BlockingQueue<Job> jobs;
		final FindFoci ff;
		final BatchParameters parameters;

		public Worker(BlockingQueue<Job> jobs, FindFoci ff, BatchParameters parameters)
		{
			this.jobs = jobs;
			this.ff = ff;
			this.parameters = parameters;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Runnable#run()
		 */
		public void run()
		{
			try
			{
				while (!finished)
				{
					final Job job = jobs.take();
					if (job == null || finished)
						break;
					if (job.batchId == 0)
						break;
					run(job.filename, job.batchId);
				}
			}
			catch (InterruptedException e)
			{
				System.out.println(e.toString());
				throw new RuntimeException(e);
			}
			finally
			{
				finished = true;
			}
		}

		private void run(String filename, int batchId)
		{
			if (Utils.isInterrupted())
			{
				finished = true;
				return;
			}
			MemoryLogger logger = new MemoryLogger();
			runBatch(ff, batchId, filename, parameters, logger);
			recordLogMessages(logger);
		}
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
		if ((parameters.centreMethod == CENTRE_GAUSSIAN_ORIGINAL ||
				parameters.centreMethod == CENTRE_GAUSSIAN_SEARCH) && isGaussianFitEnabled < 1)
		{
			IJ.error(TITLE, "Gaussian fit is not currently enabled");
			return;
		}

		setResultsDirectory(batchOutputDirectory);
		String batchFilename = openBatchResultsFile();

		if ((parameters.outputType & OUTPUT_LOG_MESSAGES) != 0)
		{
			IJ.log("---");
			IJ.log(TITLE + " Batch");
		}

		// Multi-threaded use can result in ImageJ objects not existing.
		// Initialise all IJ static methods we will use.
		// TODO - Check if this is complete 
		IJ.d2s(0);

		long runTime = System.nanoTime();
		totalProgress = imageList.length;
		stepProgress = Utils.getProgressInterval(totalProgress);
		progress = 0;
		batchImages = new AtomicInteger();
		batchOK = new AtomicInteger();
		batchError = new AtomicInteger();

		// Allow multi-threaded execution
		final int nThreads = Maths.min(Prefs.getThreads(), totalProgress);
		boolean sortResults = false;
		if (batchMultiThread && nThreads > 1)
		{
			BlockingQueue<Job> jobs = new ArrayBlockingQueue<Job>(nThreads * 2);
			List<Worker> workers = new LinkedList<Worker>();
			List<Thread> threads = new LinkedList<Thread>();
			for (int i = 0; i < nThreads; i++)
			{
				Worker worker = new Worker(jobs, this, parameters);
				Thread t = new Thread(worker);
				workers.add(worker);
				threads.add(t);
				t.start();
			}

			int batchId = 0;
			for (String image : imageList)
			{
				putJob(jobs, image, ++batchId);
				if (Utils.isInterrupted())
					break;
			}
			sortResults = batchId > 1;
			// Finish all the worker threads by passing in a null job
			for (int i = 0; i < threads.size(); i++)
			{
				putEmptyJob(jobs);
			}

			// Wait for all to finish
			for (int i = 0; i < threads.size(); i++)
			{
				try
				{
					threads.get(i).join();
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}
			threads.clear();
		}
		else
		{
			int batchId = 0;
			for (String image : imageList)
			{
				runBatch(this, ++batchId, image, parameters, null);
				if (Utils.isInterrupted())
					break;
			}
		}

		closeBatchResultsFile();

		if (sortResults)
			sortBatchResultsFile(batchFilename);

		runTime = System.nanoTime() - runTime;
		IJ.showProgress(1);
		IJ.showStatus("");

		if ((parameters.outputType & OUTPUT_LOG_MESSAGES) != 0)
			IJ.log("---");

		IJ.log(String.format("%s Batch time = %s. %s. Processed %d / %s. %s.", TITLE,
				Utils.timeToString(runTime / 1000000.0), Utils.pleural(totalProgress, "file"), batchOK.get(),
				Utils.pleural(batchImages.get(), "image"), Utils.pleural(batchError.get(), "file error")));

		if (Utils.isInterrupted())
		{
			IJ.showStatus("Cancelled");
			IJ.log(TITLE + " Batch Cancelled");
		}
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
		gd.addCheckbox("Multi-thread", batchMultiThread);
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
		batchMultiThread = gd.getNextBoolean();
		Prefs.set(BATCH_INPUT_DIRECTORY, batchInputDirectory);
		Prefs.set(BATCH_MASK_DIRECTORY, batchMaskDirectory);
		Prefs.set(BATCH_PARAMETER_FILE, batchParameterFile);
		Prefs.set(BATCH_OUTPUT_DIRECTORY, batchOutputDirectory);
		Prefs.set(BATCH_MULTI_THREAD, batchMultiThread);
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

	private void putJob(BlockingQueue<Job> jobs, String filename, int batchId)
	{
		try
		{
			jobs.put(new Job(filename, batchId));
		}
		catch (InterruptedException e)
		{
			throw new RuntimeException("Unexpected interruption", e);
		}
	}

	private void putEmptyJob(BlockingQueue<Job> jobs)
	{
		try
		{
			jobs.put(new Job());
		}
		catch (InterruptedException e)
		{
			throw new RuntimeException("Unexpected interruption", e);
		}
	}

	private static boolean runBatch(FindFoci ff, int batchId, String image, BatchParameters parameters,
			MemoryLogger logger)
	{
		ff.showProgress();
		IJ.showStatus(image);
		final String[] mask = getMaskImage(batchInputDirectory, batchMaskDirectory, image);

		// Open the image (and mask)
		ImagePlus imp = openImage(batchInputDirectory, image);
		if (imp == null)
		{
			error(ff, logger, parameters, "File is not a valid image: " + image);
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
		return execBatch(ff, batchId, imp, maskImp, parameters, imageDimension, maskDimension, logger);
	}

	/**
	 * Simple error log to the ImageJ window instead of using IJ.error() and IJ.redirectErrorMessages since the redirect
	 * flag is reset each time.
	 * 
	 * @param ff
	 * @param parameters
	 * 
	 * @param msg
	 */
	public static void error(FindFoci ff, MemoryLogger logger, BatchParameters parameters, String msg)
	{
		ff.batchError.incrementAndGet();
		if (logger != null)
		{
			if ((parameters.outputType & OUTPUT_LOG_MESSAGES) != 0)
				logger.error("---");
			logger.error(TITLE + " Batch ERROR: " + msg);
		}
		else
		{
			if ((parameters.outputType & OUTPUT_LOG_MESSAGES) != 0)
				IJ.log("---");
			IJ.log(TITLE + " Batch ERROR: " + msg);
		}
		Macro.abort();
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
		// TODO - Add support for loading custom channel, slice and frame using a filename suffix, e.g. [cCzZtT]
		// If the suffix exists, remove it, load the image then extract the specified slices.
		return opener.openImage(directory, filename);
	}

	private static ImagePlus setupImage(ImagePlus imp, int[] dimension)
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
	 * Truncated version of the exec() method that saves all results to the batch output directory.
	 *
	 * @param ff
	 *            the FindFoci instance
	 * @param batchId
	 *            the batch id
	 * @param imp
	 *            the imp
	 * @param mask
	 *            the mask
	 * @param p
	 *            the p
	 * @param imageDimension
	 *            the image dimension
	 * @param maskDimension
	 *            the mask dimension
	 * @param logger
	 *            the logger
	 * @return true, if successful
	 */
	private static boolean execBatch(FindFoci ff, int batchId, ImagePlus imp, ImagePlus mask, BatchParameters p,
			int[] imageDimension, int[] maskDimension, MemoryLogger logger)
	{
		if (!isSupported(imp.getBitDepth()))
		{
			error(ff, logger, p, "Only " + FindFoci.getSupported() + " images are supported");
			return false;
		}

		final int options = p.options;
		final int outputType = p.outputType;

		FindFociBaseProcessor ffp = ff.createFFProcessor(imp);
		ffp.setShowStatus(false);
		ffp.setLogger(logger);
		ff.batchImages.incrementAndGet();
		FindFociResults ffResult = ff.findMaxima(ffp, imp, mask, p.backgroundMethod, p.backgroundParameter,
				p.autoThresholdMethod, p.searchMethod, p.searchParameter, p.maxPeaks, p.minSize, p.peakMethod,
				p.peakParameter, outputType, p.sortIndex, options, p.blur, p.centreMethod, p.centreParameter,
				p.fractionParameter);

		if (ffResult == null)
			return false;

		ff.batchOK.incrementAndGet();

		// Get the results
		ImagePlus maximaImp = ffResult.mask;
		ArrayList<FindFociResult> resultsArray = ffResult.results;
		FindFociStatistics stats = ffResult.stats;

		final String expId;
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
			objectAnalysisResult = ffp.new ObjectAnalysisResult();
			ImagePlus objectImp = ffp.doObjectAnalysis(mask, maximaImp, resultsArray,
					(options & OPTION_SHOW_OBJECT_MASK) != 0, objectAnalysisResult);
			if (objectImp != null)
				IJ.saveAsTiff(objectImp, batchOutputDirectory + File.separator + expId + ".objects.tiff");
		}

		if ((options & OPTION_SAVE_TO_MEMORY) != 0)
		{
			ff.saveToMemory(resultsArray, imp, imageDimension[BatchParameters.C], imageDimension[BatchParameters.Z],
					imageDimension[BatchParameters.T]);
		}

		// Record all the results to file
		final String batchPrefix = ff.initialiseBatchPrefix(batchId, expId);
		ff.saveResults(ffp, expId, imp, imageDimension, mask, maskDimension, p.backgroundMethod, p.backgroundParameter,
				p.autoThresholdMethod, p.searchMethod, p.searchParameter, p.maxPeaks, p.minSize, p.peakMethod,
				p.peakParameter, outputType, p.sortIndex, options, p.blur, p.centreMethod, p.centreParameter,
				p.fractionParameter, resultsArray, stats, batchOutputDirectory, objectAnalysisResult, batchPrefix);

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
					PointRoi[] rois = ff.createStackRoi(resultsArray, outputType);

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

	/**
	 * Record the messages from the logger
	 *
	 * @param logger
	 *            the logger
	 */
	private synchronized static void recordLogMessages(MemoryLogger logger)
	{
		for (String message : logger.getMessages())
			IJ.log(message);
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
	 * Save the results array to memory using the image name and current channel and frame
	 * 
	 * @param resultsArray
	 * @param imp
	 */
	private void saveToMemory(ArrayList<FindFociResult> resultsArray, ImagePlus imp)
	{
		saveToMemory(resultsArray, imp, imp.getChannel(), 0, imp.getFrame());
	}

	/**
	 * Save the results array to memory using the image name and current channel and frame
	 * 
	 * @param resultsArray
	 * @param imp
	 */
	private void saveToMemory(ArrayList<FindFociResult> resultsArray, ImagePlus imp, int c, int z, int t)
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
	public static ArrayList<FindFociResult> getResults(String name)
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

	/**
	 * Checks if is supported.
	 *
	 * @param bitDepth
	 *            the bit depth
	 * @return true, if is supported
	 */
	public static boolean isSupported(int bitDepth)
	{
		return bitDepth == 8 || bitDepth == 16 || bitDepth == 32;
	}

	/**
	 * Get the supported images as a text output
	 *
	 * @return A text output of the supported images
	 */
	public static String getSupported()
	{
		return "8-bit, 16-bit and 32-bit";
	}

	/**
	 * @return True if using an optimised FindFociProcessor (default is generic)
	 */
	public boolean isOptimisedProcessor()
	{
		return optimisedProcessor;
	}

	/**
	 * @param optimisedProcessor
	 *            True if using an optimised FindFociProcessor (default is generic)
	 */
	public void setOptimisedProcessor(boolean optimisedProcessor)
	{
		this.optimisedProcessor = optimisedProcessor;
	}
}
