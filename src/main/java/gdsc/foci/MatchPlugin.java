package gdsc.foci;

import java.awt.Color;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import gdsc.UsageTracker;
import gdsc.core.data.utils.Rounder;
import gdsc.core.data.utils.RounderFactory;

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

import gdsc.core.ij.Utils;
import gdsc.core.match.Coordinate;
import gdsc.core.match.MatchCalculator;
import gdsc.core.match.MatchResult;
import gdsc.core.match.PointPair;
import gdsc.core.utils.TurboList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.procedure.TIntObjectProcedure;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.io.OpenDialog;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.plugin.ZProjector;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

/**
 * Compares the ROI points on two images and computes the match statistics.
 * 
 * Can output the matches for each quartile when the points are ranked using their height. Only supports 2D images (no
 * Z-stacks) but does allow selection of channel/frame that is used for the heights.
 */
public class MatchPlugin implements PlugIn
{
	/**
	 * Visually impaired safe colour for matches (greenish)
	 */
	public static Color MATCH = new Color(0, 158, 115);
	/**
	 * Visually impaired safe colour for no match 1 (yellowish)
	 */
	public static Color UNMATCH1 = new Color(240, 228, 66);
	/**
	 * Visually impaired safe colour for no match 2 (blueish)
	 */
	public static Color UNMATCH2 = new Color(86, 180, 233);

	private static String TITLE = "Match Calculator";
	private static String[] dTypes = new String[] { "Relative", "Absolute" };

	private static String title1 = "";
	private static String title2 = "";
	private static int dType = 0;
	private static double dThreshold = 0.05;

	// For memory mode
	private static String name1 = "";
	private static String name2 = "";
	private static String[] unitTypes = new String[] { "Pixel", "Calibrated" };
	private static int unitType = 0;
	private static double memoryThreshold = 5;

	private static boolean overlay = true;
	private static boolean quartiles = false;
	private static boolean scatter = true;
	private static boolean unmatchedDistribution = true;
	private static boolean matchTable = false;
	private static boolean saveMatches = false;
	private static String filename = "";
	private static int findFociImageIndex = 0;
	//@formatter:off
	private static String[] findFociResult = new String[] { 
			"Intensity", 
			"Intensity above saddle",
			"Intensity above background", 
			"Count", 
			"Count above saddle", 
			"Max value", 
			"Highest saddle value", };
	//@formatter:on
	private static int findFociResultChoiceIndex = 0;

	private static boolean writeHeader = true;
	private static boolean writeUnmatchedHeader = true;
	private static boolean writeMatchedHeader = true;

	private static TextWindow resultsWindow = null;
	private static TextWindow unmatchedWindow = null;
	private static TextWindow matchedWindow = null;
	private boolean memoryMode = false;
	private boolean fileMode = false;
	private boolean imageMode = false;
	private static int channel1 = 1;
	private static int frame1 = 1;
	private static int channel2 = 1;
	private static int frame2 = 1;
	private String t1 = "";
	private String t2 = "";
	private Coordinate[] actualPoints = null;
	private Coordinate[] predictedPoints = null;

	// For memory mode
	private String unit;

	private double scaleX = 1, scaleY = 1, scaleZ = 1;

	/*
	 * (non-Javadoc)
	 * 
	 * // Build the values to plot
	 * float[] xMatch = new float[matches.size()];
	 * float[] yMatch = new float[matches.size()];
	 * float[] xNoMatch = new float[falsePositives.size() + falseNegatives.size()];
	 * float[] yNoMatch = new float[falsePositives.size() + falseNegatives.size()];
	 * 
	 * int n = 0;
	 * for (PointPair pair : matches)
	 * {
	 * TimeValuedPoint p1 = (TimeValuedPoint) pair.getPoint1();
	 * TimeValuedPoint p2 = (TimeValuedPoint) pair.getPoint2();
	 * xMatch[n] = p1.getValue(); // Actual
	 * yMatch[n] = p2.getValue(); // Predicted
	 * n++;
	 * }
	 * n = 0;
	 * // Actual
	 * for (Coordinate point : falseNegatives)
	 * {
	 * TimeValuedPoint p = (TimeValuedPoint) point;
	 * xNoMatch[n++] = p.getValue();
	 * }
	 * // Predicted
	 * for (Coordinate point : falsePositives)
	 * {
	 * TimeValuedPoint p = (TimeValuedPoint) point;
	 * yNoMatch[n++] = p.getValue();
	 * }
	 * 
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	@SuppressWarnings("unchecked")
	public void run(String arg)
	{
		UsageTracker.recordPlugin(this.getClass(), arg);

		memoryMode = "memory".equals(arg);
		fileMode = !memoryMode && "file".equals(arg);
		imageMode = !memoryMode && !fileMode;

		if (!showDialog())
		{
			return;
		}

		actualPoints = null;
		predictedPoints = null;
		double d = 0;
		boolean doQuartiles = false;
		boolean doScatter = false;
		boolean doUnmatched = false;
		ImagePlus imp1 = null;
		ImagePlus imp2 = null;

		t1 = title1;
		t2 = title2;

		if (memoryMode)
		{
			t1 = name1;
			t2 = name2;

			FindFociMemoryResults m1, m2;

			try
			{
				m1 = getFindFociMemoryResults(t1);
				actualPoints = getFindFociPoints(m1, t1);
				m2 = getFindFociMemoryResults(t2);
				predictedPoints = getFindFociPoints(m2, t2);
			}
			catch (IllegalStateException e)
			{
				IJ.error("Failed to load the points: " + e.getMessage());
				return;
			}
			d = memoryThreshold;

			// Support image analysis if the images are open
			imp1 = WindowManager.getImage(m1.imageId);
			imp2 = WindowManager.getImage(m2.imageId);

			boolean canExtractHeights = canExtractHeights(imp1, imp2);
			doQuartiles = quartiles && canExtractHeights;
			doScatter = scatter && canExtractHeights;
			doUnmatched = unmatchedDistribution && canExtractHeights;

			if (doQuartiles || doScatter || doUnmatched || matchTable)
			{
				// Extract the heights for each point
				actualPoints = extractHeights(imp1, actualPoints, channel1, frame1);
				predictedPoints = extractHeights(imp2, predictedPoints, channel2, frame2);
			}
		}
		else if (fileMode)
		{
			try
			{
				actualPoints = PointManager.loadPoints(t1);
				predictedPoints = PointManager.loadPoints(t2);
			}
			catch (IOException e)
			{
				IJ.error("Failed to load the points: " + e.getMessage());
				return;
			}
			d = dThreshold;
		}
		else
		{
			imp1 = WindowManager.getImage(t1);
			if (imp1 == null)
			{
				IJ.error("Failed to load image1: " + t1);
				return;
			}
			imp2 = WindowManager.getImage(t2);
			if (imp2 == null)
			{
				IJ.error("Failed to load image2: " + t2);
				return;
			}

			if (dType == 1)
			{
				d = dThreshold;
			}
			else
			{
				int length1 = Math.min(imp1.getWidth(), imp1.getHeight());
				int length2 = Math.min(imp2.getWidth(), imp2.getHeight());
				d = Math.ceil(dThreshold * Math.max(length1, length2));
			}

			actualPoints = PointManager.extractRoiPoints(imp1.getRoi());
			predictedPoints = PointManager.extractRoiPoints(imp2.getRoi());

			boolean canExtractHeights = canExtractHeights(imp1, imp2);
			doQuartiles = quartiles && canExtractHeights;
			doScatter = scatter && canExtractHeights;
			doUnmatched = unmatchedDistribution && canExtractHeights;

			if (doQuartiles || doScatter || doUnmatched || matchTable)
			{
				// Extract the heights for each point
				actualPoints = extractHeights(imp1, actualPoints, channel1, frame1);
				predictedPoints = extractHeights(imp2, predictedPoints, channel2, frame2);
			}
		}

		if (unit == null)
			unit = "px";

		Object[] points = compareROI(actualPoints, predictedPoints, d, doQuartiles);

		List<Coordinate> TP = (List<Coordinate>) points[0];
		List<Coordinate> FP = (List<Coordinate>) points[1];
		List<Coordinate> FN = (List<Coordinate>) points[2];
		List<PointPair> matches = (List<PointPair>) points[3];
		MatchResult result = (MatchResult) points[4];

		if (overlay && (imageMode || memoryMode))
		{
			// Imp2 is the predicted, show the overlay on this
			imp2.setOverlay(null);
			imp2.saveRoi();
			imp2.killRoi();
			// Use colour blind friendly colours
			addOverlay(imp2, TP, MATCH, scaleX, scaleY, scaleZ);
			addOverlay(imp2, FN, UNMATCH1, scaleX, scaleY, scaleZ);
			addOverlay(imp2, FP, UNMATCH2, scaleX, scaleY, scaleZ);
			imp2.updateAndDraw();
		}

		// Output a scatter plot of actual vs predicted
		if (doScatter)
		{
			scatterPlot(imp1, imp2, matches, FP, FN);
		}

		// Show analysis of the height distribution of the unmatched points 
		if (doUnmatched)
		{
			unmatchedAnalysis(t1, t2, matches, FP, FN);
		}

		if (saveMatches)
		{
			saveMatches(d, matches, FP, FN, result);
		}

		if (matchTable)
		{
			addIntensityFromFindFoci(matches, FP, FN);
			showMatches(matches, FP, FN);
		}
	}

	private FindFociMemoryResults getFindFociMemoryResults(String resultsName) throws IllegalStateException
	{
		FindFociMemoryResults memoryResults = FindFoci.getResults(resultsName);
		if (memoryResults == null)
		{
			throw new IllegalStateException("No foci with the name " + resultsName);
		}
		ArrayList<FindFociResult> results = memoryResults.results;
		if (results.size() == 0)
		{
			throw new IllegalStateException("Zero foci in the results with the name " + resultsName);
		}
		return memoryResults;
	}

	private Coordinate[] getFindFociPoints(FindFociMemoryResults memoryResults, String resultsName)
			throws IllegalStateException
	{
		ArrayList<FindFociResult> results = memoryResults.results;

		// If using calibration then we must convert the coordinates
		if (unitType == 1)
		{
			// Get the calibration
			Calibration calibration = memoryResults.calibration;
			if (calibration == null)
				throw new IllegalStateException("No calibration for the results with the name " + resultsName);

			String unit = calibration.getUnit();
			if (this.unit != null && !this.unit.equals(unit))
				throw new IllegalStateException("Calibration units for the results are different");
			this.unit = unit;

			// The units must be the same
			if (!unit.equals(calibration.getYUnit()) || !unit.equals(calibration.getZUnit()))
				throw new IllegalStateException(
						"Calibration units are different in different dimensions for the results with the name " +
								resultsName);

			scaleX = calibration.pixelWidth;
			scaleY = calibration.pixelHeight;
			scaleZ = calibration.pixelDepth;

			gdsc.core.match.BasePoint[] points = new gdsc.core.match.BasePoint[results.size()];
			int i = 0;
			for (FindFociResult result : results)
			{
				points[i++] = new gdsc.core.match.BasePoint(convert(result.x, scaleX), convert(result.y, scaleY),
						convert(result.z, scaleZ));
			}
			return points;
		}
		else
		{
			// Native pixel units
			AssignedPoint[] points = new AssignedPoint[results.size()];
			int i = 0;
			for (FindFociResult result : results)
			{
				points[i++] = new AssignedPoint(result.x, result.y, result.z);
			}
			return points;
		}
	}

	private static float convert(int x, double cx)
	{
		return (float) (x * cx);
	}

	/**
	 * @return True if heights can be extracted from the image
	 */
	private boolean canExtractHeights(ImagePlus imp1, ImagePlus imp2)
	{
		int[] dim1 = imp1.getDimensions();
		int[] dim2 = imp2.getDimensions();

		// Uncomment to prevent Z-stacks. Using the z-projection for heights so this should not matter
		//if (dim1[3] != 1 && dim2[3] != 1)
		//	return false;

		if ((dim1[2] != 1 || dim1[4] != 1) || (dim2[2] != 1 || dim2[4] != 1))
		{
			// Select channel/frame
			GenericDialog gd = new GenericDialog("Select Channel/Frame");
			gd.addMessage("Stacks detected.\nPlease select the channel/frame.");
			String[] channels1 = getChannels(imp1);
			String[] frames1 = getFrames(imp1);
			String[] channels2 = getChannels(imp2);
			String[] frames2 = getFrames(imp2);

			if (channels1.length > 1)
				gd.addChoice("Image1_Channel", channels1, channels1[channels1.length >= channel1 ? channel1 - 1 : 0]);
			if (frames1.length > 1)
				gd.addChoice("Image1_Frame", frames1, frames1[frames1.length >= frame1 ? frame1 - 1 : 0]);
			if (channels2.length > 1)
				gd.addChoice("Image2_Channel", channels2, channels2[channels2.length >= channel2 ? channel2 - 1 : 0]);
			if (frames2.length > 1)
				gd.addChoice("Image2_Frame", frames2, frames2[frames2.length >= frame2 ? frame2 - 1 : 0]);

			gd.showDialog();

			if (gd.wasCanceled())
				return false;

			// Extract the channel/frame
			channel1 = (channels1.length > 1) ? gd.getNextChoiceIndex() + 1 : 1;
			frame1 = (frames1.length > 1) ? gd.getNextChoiceIndex() + 1 : 1;
			channel2 = (channels2.length > 1) ? gd.getNextChoiceIndex() + 1 : 1;
			frame2 = (frames2.length > 1) ? gd.getNextChoiceIndex() + 1 : 1;

			t1 += " c" + channel1 + " t" + frame1;
			t2 += " c" + channel2 + " t" + frame2;
		}

		return true;
	}

	private String[] getChannels(ImagePlus imp)
	{
		int c = imp.getNChannels();
		String[] result = new String[c];
		for (int i = 0; i < c; i++)
			result[i] = Integer.toString(i + 1);
		return result;
	}

	private String[] getFrames(ImagePlus imp)
	{
		int c = imp.getNFrames();
		String[] result = new String[c];
		for (int i = 0; i < c; i++)
			result[i] = Integer.toString(i + 1);
		return result;
	}

	private TimeValuedPoint[] extractHeights(ImagePlus imp, Coordinate[] actualPoints, int channel, int frame)
	{
		// Use maximum intensity projection
		ImageProcessor ip = Utils.extractTile(imp, frame, channel, ZProjector.MAX_METHOD);
		//new ImagePlus("height", ip).show(); 

		// Store ID as the time
		TimeValuedPoint[] newPoints = new TimeValuedPoint[actualPoints.length];
		for (int i = 0; i < newPoints.length; i++)
		{
			int x = (int) Math.round(actualPoints[i].getX() / scaleX);
			int y = (int) Math.round(actualPoints[i].getY() / scaleY);
			int value = ip.get(x, y);
			newPoints[i] = new TimeValuedPoint(actualPoints[i].getX(), actualPoints[i].getY(), actualPoints[i].getZ(),
					i + 1, value);
		}

		return newPoints;
	}

	/**
	 * Adds an ROI point overlay to the image using the specified colour.
	 *
	 * @param imp
	 *            the imp
	 * @param list
	 *            the list
	 * @param color
	 *            the color
	 */
	public static void addOverlay(ImagePlus imp, List<? extends Coordinate> list, Color color)
	{
		addOverlay(imp, list, color, 1, 1, 1);
	}

	/**
	 * Adds an ROI point overlay to the image using the specified colour.
	 *
	 * @param imp
	 *            the imp
	 * @param list
	 *            the list
	 * @param color
	 *            the color
	 * @param scaleX
	 *            the scale X
	 * @param scaleY
	 *            the scale Y
	 * @param scaleZ
	 *            the scale Z
	 */
	public static void addOverlay(ImagePlus imp, List<? extends Coordinate> list, Color color, double scaleX,
			double scaleY, double scaleZ)
	{
		if (list.isEmpty())
			return;

		Color strokeColor = color;
		Color fillColor = color;

		Overlay o = imp.getOverlay();
		if (o == null)
			o = new Overlay();

		for (PointRoi roi : createROI(imp, list, scaleX, scaleY, scaleZ))
		{
			roi.setStrokeColor(strokeColor);
			roi.setFillColor(fillColor);
			roi.setShowLabels(false);
			o.add(roi);
		}
		imp.setOverlay(o);
	}

	/**
	 * Creates an ImageJ PointRoi from the list of points, one per slice.
	 *
	 * @param array
	 *            List of points
	 * @param scaleX
	 *            the scale X
	 * @param scaleY
	 *            the scale Y
	 * @param scaleZ
	 *            the scale Z
	 * @return The PointRoi
	 */
	public static PointRoi[] createROI(final ImagePlus imp, List<? extends Coordinate> array, double scaleX,
			double scaleY, double scaleZ)
	{
		// We have to create an overlay per z-slice using the calibration scale
		TIntObjectHashMap<TIntArrayList> xpoints = new TIntObjectHashMap<TIntArrayList>();
		TIntArrayList nextlist = new TIntArrayList();

		for (Coordinate point : array)
		{
			int x = (int) Math.round(point.getX() / scaleX);
			int y = (int) Math.round(point.getY() / scaleY);
			int z = (int) Math.round(point.getZ() / scaleZ);

			TIntArrayList list = xpoints.putIfAbsent(z, nextlist);
			if (list == null)
			{
				list = nextlist;
				nextlist = new TIntArrayList();
			}

			list.add(x);
			list.add(y);
		}

		final int channel = 0;
		final int frame = 0;
		final boolean isHyperStack = imp.isDisplayedHyperStack();

		final TurboList<PointRoi> rois = new TurboList<PointRoi>(xpoints.size());
		xpoints.forEachEntry(new TIntObjectProcedure<TIntArrayList>()
		{
			public boolean execute(int z, TIntArrayList b)
			{
				int[] data = b.toArray();
				float[] x = new float[data.length / 2];
				float[] y = new float[x.length];
				for (int i = 0, j = 0; j < x.length; j++)
				{
					x[j] = data[i++];
					y[j] = data[i++];
				}
				PointRoi roi = new PointRoi(x, y);
				if (isHyperStack)
				{
					roi.setPosition(channel, z, frame);
				}
				else
				{
					roi.setPosition(imp.getStackIndex(channel, z, frame));
				}
				rois.add(roi);
				return true;
			}
		});

		return rois.toArray(new PointRoi[rois.size()]);
	}

	private boolean showDialog()
	{
		String[] items = null;
		String t1 = title1;
		String t2 = title2;
		double d = dThreshold;

		if (memoryMode)
		{
			items = FindFoci.getResultsNames();
			if (items.length < 2)
			{
				IJ.showMessage(TITLE, "Require 2 results in memory");
				return false;
			}

			List<String> imageList = Arrays.asList(items);
			int index = 0;
			t1 = (imageList.contains(name1) ? name1 : imageList.get(index++));
			t2 = (imageList.contains(name2) ? name2 : imageList.get(index));
			d = memoryThreshold;
		}
		if (imageMode)
		{
			List<String> imageList = new LinkedList<String>();
			for (int id : gdsc.core.ij.Utils.getIDList())
			{
				ImagePlus imp = WindowManager.getImage(id);
				if (imp != null)
				{
					Roi roi = imp.getRoi();
					// Allow no ROI => No points
					if ((roi == null || roi.getType() == Roi.POINT) && !imp.getTitle().startsWith(TITLE))
					{
						imageList.add(imp.getTitle());
					}
				}
			}

			if (imageList.size() < 2)
			{
				IJ.showMessage(TITLE, "Require 2 images open with point ROI");
				return false;
			}

			items = imageList.toArray(new String[0]);
			int index = 0;
			t1 = (imageList.contains(title1) ? title1 : imageList.get(index++));
			t2 = (imageList.contains(title2) ? title2 : imageList.get(index));
		}

		GenericDialog gd = new GenericDialog(TITLE);

		if (memoryMode)
		{
			gd.addMessage("Compare the points between 2 FindFoci results\nand compute the match statistics");
			gd.addChoice("Input_1", items, t1);
			gd.addChoice("Input_2", items, t2);
			gd.addMessage("Distance between matching points in:");
			gd.addChoice("Unit", unitTypes, unitTypes[unitType]);
		}
		else if (fileMode)
		{
			gd.addMessage("Compare the points in two files\nand compute the match statistics");
			gd.addStringField("Input_1", t1, 30);
			gd.addStringField("Input_2", t2, 30);
			gd.addMessage("Distance between matching points in pixels");
		}
		else
		{
			gd.addMessage("Compare the ROI points between 2 images\nand compute the match statistics");
			gd.addChoice("Input_1", items, t1);
			gd.addChoice("Input_2", items, t2);
			gd.addMessage("Distance between matching points in pixels, or fraction of\n" + "image edge length");
			gd.addChoice("Distance_type", dTypes, dTypes[dType]);
		}
		gd.addNumericField("Distance", d, 2);
		if (imageMode || memoryMode)
		{
			gd.addCheckbox("Overlay", overlay);
			gd.addCheckbox("Quartiles", quartiles);
			gd.addCheckbox("Scatter_plot", scatter);
			gd.addCheckbox("Unmatched_distribution", unmatchedDistribution);
			gd.addCheckbox("Match_table", matchTable);
		}
		gd.addCheckbox("Save_matches", saveMatches);
		ArrayList<FindFociResult> resultsArray = null;
		if (!memoryMode)
		{
			resultsArray = FindFoci.getResults();
			if (resultsArray != null)
			{
				String[] imageItems = new String[] { "[None]", "Image1", "Image2" };
				gd.addChoice("FindFoci_image", imageItems, imageItems[findFociImageIndex]);
				gd.addChoice("FindFoci_result", findFociResult, findFociResult[findFociResultChoiceIndex]);
			}
		}

		gd.addHelp(gdsc.help.URL.FIND_FOCI);
		gd.showDialog();

		if (gd.wasCanceled())
			return false;

		if (memoryMode)
		{
			name1 = gd.getNextChoice();
			name2 = gd.getNextChoice();
			unitType = gd.getNextChoiceIndex();
		}
		else if (fileMode)
		{
			title1 = gd.getNextString();
			title2 = gd.getNextString();
		}
		else
		{
			title1 = gd.getNextChoice();
			title2 = gd.getNextChoice();
			dType = gd.getNextChoiceIndex();
		}
		d = gd.getNextNumber();
		if (memoryMode)
		{
			memoryThreshold = d;
		}
		else
		{
			dThreshold = d;
		}
		if (imageMode || memoryMode)
		{
			overlay = gd.getNextBoolean();
			quartiles = gd.getNextBoolean();
			scatter = gd.getNextBoolean();
			unmatchedDistribution = gd.getNextBoolean();
			matchTable = gd.getNextBoolean();
		}
		saveMatches = gd.getNextBoolean();
		findFociImageIndex = 0;
		if (resultsArray != null)
		{
			findFociImageIndex = gd.getNextChoiceIndex();
			findFociResultChoiceIndex = gd.getNextChoiceIndex();
		}

		return true;
	}

	private Object[] compareROI(Coordinate[] actualPoints, Coordinate[] predictedPoints, double dThreshold,
			boolean doQuartiles)
	{
		List<Coordinate> TP = new LinkedList<Coordinate>();
		List<Coordinate> FP = new LinkedList<Coordinate>();
		List<Coordinate> FN = new LinkedList<Coordinate>();
		List<PointPair> matches = new LinkedList<PointPair>();
		MatchResult result = (memoryMode)
				? MatchCalculator.analyseResults3D(actualPoints, predictedPoints, dThreshold, TP, FP, FN, matches)
				: MatchCalculator.analyseResults2D(actualPoints, predictedPoints, dThreshold, TP, FP, FN, matches);
		MatchResult[] qResults = null;

		if (doQuartiles)
			qResults = compareQuartiles(actualPoints, predictedPoints, dThreshold);

		String header = null;

		if (!java.awt.GraphicsEnvironment.isHeadless())
		{
			if (doQuartiles)
			{
				header = createResultsHeader(qResults);
				Utils.refreshHeadings(resultsWindow, header, true);
			}

			if (resultsWindow == null || !resultsWindow.isShowing())
			{
				if (header == null)
					header = createResultsHeader(qResults);
				resultsWindow = new TextWindow(TITLE + " Results", header, "", 900, 300);
			}
		}
		else
		{
			if (writeHeader)
			{
				header = createResultsHeader(qResults);
				writeHeader = false;
				IJ.log(header);
			}
		}
		addResult(t1, t2, dThreshold, result, qResults);

		return new Object[] { TP, FP, FN, matches, result };
	}

	/**
	 * Compare the match results for the points within each height quartile
	 * 
	 * @param actualPoints
	 * @param predictedPoints
	 * @param dThreshold
	 * @return An array of 4 quartile results (or null if there are no points)
	 */
	private MatchResult[] compareQuartiles(Coordinate[] actualPoints, Coordinate[] predictedPoints, double dThreshold)
	{
		TimeValuedPoint[] actualValuedPoints = (TimeValuedPoint[]) actualPoints;
		TimeValuedPoint[] predictedValuedPoints = (TimeValuedPoint[]) predictedPoints;

		// Combine points and sort
		ArrayList<Float> heights = extractHeights(actualValuedPoints, predictedValuedPoints);

		if (heights.isEmpty())
			return null;

		float[] Q = getQuartiles(heights);

		// Process each quartile
		MatchResult[] qResults = new MatchResult[4];
		for (int q = 0; q < 4; q++)
		{
			TimeValuedPoint[] actual = extractPoints(actualValuedPoints, Q[q], Q[q + 1]);
			TimeValuedPoint[] predicted = extractPoints(predictedValuedPoints, Q[q], Q[q + 1]);
			qResults[q] = MatchCalculator.analyseResults2D(actual, predicted, dThreshold);
		}

		return qResults;
	}

	/**
	 * Extract all the heights from the two sets of valued points
	 */
	private ArrayList<Float> extractHeights(TimeValuedPoint[] actualPoints, TimeValuedPoint[] predictedPoints)
	{
		HashSet<TimeValuedPoint> nonDuplicates = new HashSet<TimeValuedPoint>();
		nonDuplicates.addAll(Arrays.asList(actualPoints));
		nonDuplicates.addAll(Arrays.asList(predictedPoints));

		ArrayList<Float> heights = new ArrayList<Float>(nonDuplicates.size());
		for (TimeValuedPoint p : nonDuplicates)
		{
			heights.add(p.getValue());
		}
		Collections.sort(heights);
		return heights;
	}

	/**
	 * Extract the points that are within the specified limits
	 */
	private TimeValuedPoint[] extractPoints(TimeValuedPoint[] points, float lower, float upper)
	{
		LinkedList<TimeValuedPoint> list = new LinkedList<TimeValuedPoint>();
		for (TimeValuedPoint p : points)
		{
			if (p.getValue() >= lower && p.getValue() < upper)
				list.add(p);
		}
		return list.toArray(new TimeValuedPoint[list.size()]);
	}

	/**
	 * Count the points that are within the specified limits
	 */
	private int countPoints(TimeValuedPoint[] points, float lower, float upper)
	{
		int n = 0;
		for (TimeValuedPoint p : points)
		{
			if (p.getValue() >= lower && p.getValue() < upper)
				n++;
		}
		return n;
	}

	private String createResultsHeader(MatchResult[] qResults)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Image 1\t");
		sb.append("Image 2\t");
		sb.append("Distance\t");
		sb.append("Unit\t");
		sb.append("N 1\t");
		sb.append("N 2\t");
		sb.append("Match\t");
		sb.append("Unmatch 1\t");
		sb.append("Unmatch 2\t");
		sb.append("Jaccard\t");
		sb.append("Recall 1\t");
		sb.append("Recall 2\t");
		sb.append("F1");
		if (qResults != null)
		{
			for (int q = 0; q < 4; q++)
				addQuartileHeader(sb, "Q" + (q + 1));
		}
		return sb.toString();
	}

	private void addQuartileHeader(StringBuilder sb, String quartile)
	{
		sb.append("\t \t");
		sb.append(quartile).append(" ").append("N1\t");
		sb.append(quartile).append(" ").append("N2\t");
		sb.append(quartile).append(" ").append("M\t");
		sb.append(quartile).append(" ").append("U1\t");
		sb.append(quartile).append(" ").append("U2\t");
		sb.append(quartile).append(" ").append("Jaccard\t");
		sb.append(quartile).append(" ").append("Recall1\t");
		sb.append(quartile).append(" ").append("Recall2\t");
		sb.append(quartile).append(" ").append("F1");
	}

	private void addResult(String i1, String i2, double dThrehsold, MatchResult result, MatchResult[] qResults)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(i1).append('\t');
		sb.append(i2).append('\t');
		sb.append(IJ.d2s(dThrehsold, 2)).append('\t');
		sb.append(unit).append('\t');
		sb.append(result.getNumberActual()).append('\t');
		sb.append(result.getNumberPredicted()).append('\t');
		sb.append(result.getTruePositives()).append('\t');
		sb.append(result.getFalseNegatives()).append('\t');
		sb.append(result.getFalsePositives()).append('\t');
		sb.append(IJ.d2s(result.getJaccard(), 4)).append('\t');
		sb.append(IJ.d2s(result.getRecall(), 4)).append('\t');
		sb.append(IJ.d2s(result.getPrecision(), 4)).append('\t');
		sb.append(IJ.d2s(result.getFScore(1.0), 4));

		if (qResults != null)
		{
			for (int q = 0; q < 4; q++)
				addQuartileResult(sb, qResults[q]);
		}

		if (java.awt.GraphicsEnvironment.isHeadless())
		{
			IJ.log(sb.toString());
		}
		else
		{
			resultsWindow.append(sb.toString());
		}
	}

	private void addQuartileResult(StringBuilder sb, MatchResult result)
	{
		sb.append("\t \t");
		sb.append(result.getNumberActual()).append('\t');
		sb.append(result.getNumberPredicted()).append('\t');
		sb.append(result.getTruePositives()).append('\t');
		sb.append(result.getFalseNegatives()).append('\t');
		sb.append(result.getFalsePositives()).append('\t');
		sb.append(IJ.d2s(result.getJaccard(), 4)).append('\t');
		sb.append(IJ.d2s(result.getRecall(), 4)).append('\t');
		sb.append(IJ.d2s(result.getPrecision(), 4)).append('\t');
		sb.append(IJ.d2s(result.getFScore(1.0), 4));
	}

	/**
	 * Build a scatter plot of the matches and the false positives/negatives using the image values for the X/Y axes
	 * 
	 * @param imp1
	 *            - Actual
	 * @param imp2
	 *            - Predicted
	 * @param matches
	 * @param falsePositives
	 * @param falseNegatives
	 */
	private void scatterPlot(ImagePlus imp1, ImagePlus imp2, List<PointPair> matches, List<Coordinate> falsePositives,
			List<Coordinate> falseNegatives)
	{
		if (matches.isEmpty() && falsePositives.isEmpty() && falseNegatives.isEmpty())
			return;

		// Build the values to plot
		float[] xMatch = new float[matches.size()];
		float[] yMatch = new float[matches.size()];
		float[] xNoMatch1 = new float[falseNegatives.size()];
		float[] yNoMatch1 = new float[falseNegatives.size()];
		float[] xNoMatch2 = new float[falsePositives.size()];
		float[] yNoMatch2 = new float[falsePositives.size()];

		int n = 0;
		float minimum = Float.POSITIVE_INFINITY, maximum = 0;
		for (PointPair pair : matches)
		{
			TimeValuedPoint p1 = (TimeValuedPoint) pair.getPoint1();
			TimeValuedPoint p2 = (TimeValuedPoint) pair.getPoint2();
			xMatch[n] = p1.getValue(); // Actual
			yMatch[n] = p2.getValue(); // Predicted
			final float max = Math.max(xMatch[n], yMatch[n]);
			final float min = Math.min(xMatch[n], yMatch[n]);
			if (maximum < max)
				maximum = max;
			if (minimum > min)
				minimum = min;
			n++;
		}
		n = 0;
		// Actual
		for (Coordinate point : falseNegatives)
		{
			TimeValuedPoint p = (TimeValuedPoint) point;
			xNoMatch1[n++] = p.getValue();
			if (maximum < p.getValue())
				maximum = p.getValue();
			minimum = 0;
		}
		// Predicted
		n = 0;
		for (Coordinate point : falsePositives)
		{
			TimeValuedPoint p = (TimeValuedPoint) point;
			yNoMatch2[n++] = p.getValue();
			if (maximum < p.getValue())
				maximum = p.getValue();
			minimum = 0;
		}

		// Create a new plot
		String title = TITLE + " : " + imp1.getTitle() + " vs " + imp2.getTitle();

		Plot plot = new Plot(title, imp1.getTitle(), imp2.getTitle(), (float[]) null, (float[]) null);
		// Ensure the plot is square
		float range = maximum - minimum;
		if (range == 0)
			range = 10;
		maximum += range * 0.05;
		minimum -= range * 0.05;
		plot.setLimits(minimum, maximum, minimum, maximum);
		plot.setFrameSize(300, 300);

		plot.setColor(MATCH);
		plot.addPoints(xMatch, yMatch, Plot.X);
		plot.setColor(UNMATCH1);
		plot.addPoints(xNoMatch1, yNoMatch1, Plot.CROSS);
		plot.setColor(UNMATCH2);
		plot.addPoints(xNoMatch2, yNoMatch2, Plot.CROSS);

		// Find old plot
		ImagePlus oldPlot = null;
		for (int id : Utils.getIDList())
		{
			ImagePlus imp = WindowManager.getImage(id);
			if (imp != null && imp.getTitle().equals(title))
			{
				oldPlot = imp;
				break;
			}
		}

		// Update plot or draw a new one
		if (oldPlot != null)
		{
			oldPlot.setProcessor(plot.getProcessor());
			oldPlot.updateAndDraw();
		}
		else
		{
			plot.show();
		}
	}

	/**
	 * Build a table showing the percentage of unmatched points that fall within each quartile of the matched points
	 * 
	 * @param title1
	 *            - Actual
	 * @param title2
	 *            - Predicted
	 * @param matches
	 * @param falsePositives
	 * @param falseNegatives
	 */
	private void unmatchedAnalysis(String title1, String title2, List<PointPair> matches,
			List<Coordinate> falsePositives, List<Coordinate> falseNegatives)
	{
		if (matches.isEmpty() && falsePositives.isEmpty() && falseNegatives.isEmpty())
			return;

		// Extract the heights of the matched points. Use the average height of each match.
		ArrayList<Float> heights = new ArrayList<Float>(matches.size());
		for (PointPair pair : matches)
		{
			TimeValuedPoint p1 = (TimeValuedPoint) pair.getPoint1();
			TimeValuedPoint p2 = (TimeValuedPoint) pair.getPoint2();
			heights.add((float) ((p1.getValue() + p2.getValue()) / 2.0));
		}
		Collections.sort(heights);

		// Get the quartile ranges
		float[] Q = getQuartiles(heights);

		// Extract the valued points
		TimeValuedPoint[] actualPoints = extractValuedPoints(falseNegatives);
		TimeValuedPoint[] predictedPoints = extractValuedPoints(falsePositives);

		// Count the number of unmatched points from each image in each quartile
		int[] actualCount = new int[6];
		int[] predictedCount = new int[6];

		actualCount[0] = countPoints(actualPoints, Float.NEGATIVE_INFINITY, Q[0]);
		predictedCount[0] = countPoints(predictedPoints, Float.NEGATIVE_INFINITY, Q[0]);
		for (int q = 0; q < 4; q++)
		{
			actualCount[q + 1] = countPoints(actualPoints, Q[q], Q[q + 1]);
			predictedCount[q + 1] = countPoints(predictedPoints, Q[q], Q[q + 1]);
		}
		actualCount[5] = countPoints(actualPoints, Q[4], Float.POSITIVE_INFINITY);
		predictedCount[5] = countPoints(predictedPoints, Q[4], Float.POSITIVE_INFINITY);

		// Show a result table
		String header = "Image 1\tN\t% <Q1\t% Q1\t% Q2\t% Q3\t% Q4\t% >Q4\tImage2\tN\t% <Q1\t% Q1\t% Q2\t% Q3\t% Q4\t% >Q4";
		if (!java.awt.GraphicsEnvironment.isHeadless())
		{
			if (unmatchedWindow == null || !unmatchedWindow.isShowing())
			{
				unmatchedWindow = new TextWindow(TITLE + " Unmatched", header, "", 900, 300);
			}
		}
		else
		{
			if (writeUnmatchedHeader)
			{
				writeUnmatchedHeader = false;
				IJ.log(header);
			}
		}
		addUnmatchedResult(title1, title2, actualCount, predictedCount);
	}

	private float[] getQuartiles(ArrayList<Float> heights)
	{
		if (heights.isEmpty())
			return new float[] { 0, 0, 0, 0, 0 };

		return new float[] { heights.get(0).floatValue(), PointAlignerPlugin.getQuartileBoundary(heights, 0.25),
				PointAlignerPlugin.getQuartileBoundary(heights, 0.5),
				PointAlignerPlugin.getQuartileBoundary(heights, 0.75),
				heights.get(heights.size() - 1).floatValue() + 1 };
	}

	private TimeValuedPoint[] extractValuedPoints(List<Coordinate> list)
	{
		TimeValuedPoint[] points = new TimeValuedPoint[list.size()];
		int i = 0;
		for (Coordinate p : list)
			points[i++] = (TimeValuedPoint) p;
		return points;
	}

	private void addUnmatchedResult(String title1, String title2, int[] actualCount, int[] predictedCount)
	{
		StringBuilder sb = new StringBuilder();
		addQuartiles(sb, title1, actualCount);
		sb.append('\t');
		addQuartiles(sb, title2, predictedCount);

		if (java.awt.GraphicsEnvironment.isHeadless())
		{
			IJ.log(sb.toString());
		}
		else
		{
			unmatchedWindow.append(sb.toString());
		}
	}

	private void addQuartiles(StringBuilder sb, String imageTitle, int[] counts)
	{
		// Count the total number of create a scale factor to calculate the percentage
		int total = 0;
		for (int c : counts)
			total += c;

		sb.append(imageTitle).append('\t').append(total);

		if (total > 0)
		{
			double factor = total / 100.0;
			for (int c : counts)
			{
				sb.append('\t');
				sb.append(IJ.d2s(c / factor, 1));
			}
		}
		else
		{
			for (int c = counts.length; c-- > 0;)
			{
				sb.append("\t-");
			}
		}
	}

	/**
	 * Saves the matches and the false positives/negatives to file
	 * 
	 * @param d
	 * @param matches
	 * @param falsePositives
	 * @param falseNegatives
	 * @param result
	 */
	private void saveMatches(double d, List<PointPair> matches, List<Coordinate> falsePositives,
			List<Coordinate> falseNegatives, MatchResult result)
	{
		if (matches.isEmpty() && falsePositives.isEmpty() && falseNegatives.isEmpty())
			return;

		String[] path = Utils.decodePath(filename);
		OpenDialog chooser = new OpenDialog("matches_file", path[0], path[1]);
		if (chooser.getFileName() == null)
			return;

		filename = chooser.getDirectory() + chooser.getFileName();

		OutputStreamWriter out = null;
		try
		{
			FileOutputStream fos = new FileOutputStream(filename);
			out = new OutputStreamWriter(fos, "UTF-8");

			StringBuilder sb = new StringBuilder();
			final String newLine = System.getProperty("line.separator");
			sb.append("# Image 1   = ").append(t1).append(newLine);
			sb.append("# Image 2   = ").append(t2).append(newLine);
			sb.append("# Distance  = ").append(Utils.rounded(d, 2)).append(newLine);
			sb.append("# Unit      = ").append(unit).append(newLine);
			sb.append("# N 1       = ").append(result.getNumberActual()).append(newLine);
			sb.append("# N 2       = ").append(result.getNumberPredicted()).append(newLine);
			sb.append("# Match     = ").append(result.getTruePositives()).append(newLine);
			sb.append("# Unmatch 1 = ").append(result.getFalseNegatives()).append(newLine);
			sb.append("# Unmatch 2 = ").append(result.getFalsePositives()).append(newLine);
			sb.append("# Jaccard   = ").append(Utils.rounded(result.getJaccard(), 4)).append(newLine);
			sb.append("# Recall 1  = ").append(Utils.rounded(result.getRecall(), 4)).append(newLine);
			sb.append("# Recall 2  = ").append(Utils.rounded(result.getPrecision(), 4)).append(newLine);
			sb.append("# F-score   = ").append(Utils.rounded(result.getFScore(1.0), 4)).append(newLine);
			sb.append("# X1\tY1\tV1\tX2\tY2\tV2").append(newLine);

			Rounder r = RounderFactory.create(4);

			out.write(sb.toString());

			for (PointPair pair : matches)
			{
				Coordinate c1 = pair.getPoint1();
				Coordinate c2 = pair.getPoint2();
				float v1 = 0, v2 = 0;
				if (pair.getPoint1() instanceof TimeValuedPoint)
				{
					TimeValuedPoint p1 = (TimeValuedPoint) c1;
					TimeValuedPoint p2 = (TimeValuedPoint) c2;
					v1 = p1.getValue(); // Actual
					v2 = p2.getValue(); // Predicted
				}
				out.write(String.format("%s\t%s\t%s\t%s\t%s\t%s%s", r.round(c1.getX()), r.round(c1.getY()), r.round(v1),
						r.round(c2.getX()), r.round(c2.getY()), r.round(v2), newLine));
			}
			// Actual
			for (Coordinate c : falseNegatives)
			{
				float v1 = 0;
				if (c instanceof TimeValuedPoint)
				{
					TimeValuedPoint p = (TimeValuedPoint) c;
					v1 = p.getValue();
				}
				out.write(String.format("%s\t%s\t%.0f\t0\t0\t0%s", r.round(c.getX()), r.round(c.getY()), r.round(v1),
						newLine));
			}
			// Predicted
			for (Coordinate c : falsePositives)
			{
				float v1 = 0;
				if (c instanceof TimeValuedPoint)
				{
					TimeValuedPoint p = (TimeValuedPoint) c;
					v1 = p.getValue();
				}
				out.write(String.format("0\t0\t0\t%s\t%s\t%.0f%s", r.round(c.getX()), r.round(c.getY()), r.round(v1),
						newLine));
			}
		}
		catch (Exception e)
		{
			IJ.log("Unable to save the matches to file: " + e.getMessage());
		}
		finally
		{
			if (out != null)
			{
				try
				{
					out.close();
				}
				catch (IOException e)
				{
					// Ignore
				}
			}
		}
	}

	/**
	 * Build a table showing the matched pairs and unmatched points
	 * 
	 * @param title1
	 *            - Actual
	 * @param title2
	 *            - Predicted
	 * @param matches
	 * @param falsePositives
	 * @param falseNegatives
	 */
	private void showMatches(List<PointPair> matches, List<Coordinate> falsePositives, List<Coordinate> falseNegatives)
	{
		if (matches.isEmpty() && falsePositives.isEmpty() && falseNegatives.isEmpty())
			return;

		// Show a result table
		String header = "Image 1\tId\tX\tY\tImage 2\tId\tX\tY\tDistance";
		if (findFociImageIndex > 0)
		{
			header += "\tImage " + findFociImageIndex + ": " + findFociResult[findFociResultChoiceIndex];
		}
		if (!java.awt.GraphicsEnvironment.isHeadless())
		{
			if (matchedWindow == null || !matchedWindow.isShowing())
			{
				matchedWindow = new TextWindow(TITLE + " Matched", header, "", 800, 300);
			}
			else
			{
				Utils.refreshHeadings(matchedWindow, header, true);
			}
		}
		else
		{
			if (writeMatchedHeader)
			{
				writeMatchedHeader = false;
				IJ.log(header);
			}
		}

		Collections.sort(matches, new Comparator<PointPair>()
		{
			public int compare(PointPair o1, PointPair o2)
			{
				TimeValuedPoint p1 = (TimeValuedPoint) o1.getPoint1();
				TimeValuedPoint p2 = (TimeValuedPoint) o2.getPoint1();
				return (p1.getTime() < p2.getTime()) ? -1 : 1;
			}
		});

		for (PointPair pair : matches)
		{
			int value = -1;
			if (findFociImageIndex > 0)
			{
				TimeValuedPoint point = (TimeValuedPoint) ((findFociImageIndex == 1) ? pair.getPoint1()
						: pair.getPoint2());
				value = (int) point.value;
			}
			addMatchedPair(pair.getPoint1(), pair.getPoint2(), pair.getXYZDistance(), value);
		}

		TimeValuedPoint[] actualPoints = extractValuedPoints(falseNegatives);
		TimeValuedPoint[] predictedPoints = extractValuedPoints(falsePositives);

		Arrays.sort(actualPoints, new Comparator<TimeValuedPoint>()
		{
			public int compare(TimeValuedPoint p1, TimeValuedPoint p2)
			{
				return (p1.getTime() < p2.getTime()) ? -1 : 1;
			}
		});
		Arrays.sort(predictedPoints, new Comparator<TimeValuedPoint>()
		{
			public int compare(TimeValuedPoint p1, TimeValuedPoint p2)
			{
				return (p1.getTime() < p2.getTime()) ? -1 : 1;
			}
		});

		for (TimeValuedPoint point : actualPoints)
		{
			int value = (findFociImageIndex == 1) ? (int) point.value : -1;
			addMatchedPair(point, null, -1, value);
		}

		for (TimeValuedPoint point : predictedPoints)
		{
			int value = (findFociImageIndex == 2) ? (int) point.value : -1;
			addMatchedPair(null, point, -1, value);
		}
	}

	private void addMatchedPair(Coordinate point1, Coordinate point2, double xyzDistance, int value)
	{
		StringBuilder sb = new StringBuilder();
		addPoint(sb, t1, point1);
		addPoint(sb, t2, point2);
		if ((xyzDistance > -1))
			sb.append(xyzDistance);
		else
			sb.append("-");
		if ((value > -1))
			sb.append('\t').append(value);
		if (!java.awt.GraphicsEnvironment.isHeadless())
		{
			matchedWindow.append(sb.toString());
		}
		else
		{
			IJ.log(sb.toString());
		}
	}

	private void addPoint(StringBuilder sb, String title, Coordinate point)
	{
		if (point != null)
		{
			TimeValuedPoint p = (TimeValuedPoint) point;
			sb.append(title).append('\t').append(p.getTime()).append('\t').append(p.getX()).append('\t')
					.append(p.getY()).append('\t');
		}
		else
		{
			sb.append("-\t-\t-\t-\t");
		}
	}

	private void addIntensityFromFindFoci(List<PointPair> matches, List<Coordinate> fP, List<Coordinate> fN)
	{
		if (findFociImageIndex == 0)
			return;
		ArrayList<FindFociResult> resultsArray = FindFoci.getResults();

		// Check the arrays are the correct size
		if (resultsArray.size() != ((findFociImageIndex == 1) ? actualPoints.length : predictedPoints.length))
		{
			findFociImageIndex = 0;
			return;
		}

		for (PointPair pair : matches)
		{
			TimeValuedPoint point = (TimeValuedPoint) ((findFociImageIndex == 1) ? pair.getPoint1() : pair.getPoint2());
			point.value = getValue(resultsArray.get(point.time - 1));
		}
		TimeValuedPoint[] points = extractValuedPoints((findFociImageIndex == 1) ? fN : fP);
		for (TimeValuedPoint point : points)
		{
			point.value = getValue(resultsArray.get(point.time - 1));
		}
	}

	private float getValue(FindFociResult result)
	{
		switch (findFociResultChoiceIndex)
		{
			//@formatter:off
			case 0: return  (float) result.totalIntensity;
			case 1: return  (float) result.intensityAboveSaddle;
			case 2: return  (float) result.totalIntensityAboveBackground;
			case 3: return  (float) result.count;
			case 4: return  (float) result.countAboveSaddle;
			case 5: return  (float) result.maxValue;
			case 6: return  (float) result.highestSaddleValue;
			default: return (float) result.totalIntensity;
			//@formatter:on
		}
	}
}
