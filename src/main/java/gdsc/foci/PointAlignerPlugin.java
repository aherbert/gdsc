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

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.plugin.ZProjector;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.text.TextWindow;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import gdsc.UsageTracker;
import gdsc.core.ij.Utils;
import gdsc.core.match.MatchResult;

/**
 * Analyses the image using the FindFoci algorithm to identify and assign pixels to maxima.
 * Realigns the marked PointROI with the appropriate peak. Any points that cannot be aligned
 * are identified as problem points.
 */
public class PointAlignerPlugin implements PlugIn
{
	private static String TITLE = "Point Aligner";
	private static TextWindow resultsWindow = null;

	private static String resultTitle = "-";
	private static String maskImage = "";
	private static String[] limitMethods = new String[] { "None", "Q1 - f * IQR", "Mean - f * SD", "nth Percentile",
			"% Missed < f" };
	private static int limitMethod = 4;
	private static double factor = 15;
	private static boolean logAlignments = true;
	private static boolean showMoved = false;
	private static boolean updateRoi = false;
	private static boolean showOverlay = true;
	private static boolean updateOverlay = true;
	private static boolean showUnaligned = false;
	private static int unalignedBorder = 10;
	private static String resultsDirectory = "";

	private static boolean writeHeader = true;

	private ImagePlus imp;
	private boolean saveResults;

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	public void run(String arg)
	{
		UsageTracker.recordPlugin(this.getClass(), arg);

		imp = WindowManager.getCurrentImage();

		if (null == imp)
		{
			IJ.showMessage("There must be at least one image open");
			return;
		}

		if (!FindFoci.isSupported(imp.getBitDepth()))
		{
			IJ.showMessage("Error", "Only " + FindFoci.getSupported() + " images are supported");
			return;
		}

		if (imp.getNChannels() != 1 || imp.getNFrames() != 1)
		{
			IJ.showMessage("Error", "Only single channel, single frame images are supported");
			return;
		}

		Roi roi = imp.getRoi();
		if (roi == null || roi.getType() != Roi.POINT)
		{
			IJ.showMessage("Error", "The image does not contain Point ROI");
			return;
		}

		if (!showDialog())
		{
			return;
		}

		AssignedPoint[] points = FindFociOptimiser.extractRoiPoints(roi, imp, null);

		FindFoci ff = new FindFoci();

		//ImagePlus mask = WindowManager.getImage(maskImage);
		ImagePlus mask = null;
		int backgroundMethod = FindFoci.BACKGROUND_ABSOLUTE;
		double backgroundParameter = getBackgroundLevel(points);
		String autoThresholdMethod = "";
		int searchMethod = FindFoci.SEARCH_ABOVE_BACKGROUND;
		double searchParameter = 0;
		int maxPeaks = 33000;
		int minSize = 1;
		int peakMethod = FindFoci.PEAK_RELATIVE;
		double peakParameter = 0;
		int outputType = FindFoci.OUTPUT_MASK_PEAKS | FindFoci.OUTPUT_MASK_NO_PEAK_DOTS;
		int sortIndex = FindFoci.SORT_MAX_VALUE;
		int options = 0;
		double blur = 0;
		int centreMethod = FindFoci.CENTRE_MAX_VALUE_ORIGINAL;
		double centreParameter = 0;

		FindFociResults results = ff.findMaxima(imp, mask, backgroundMethod, backgroundParameter, autoThresholdMethod,
				searchMethod, searchParameter, maxPeaks, minSize, peakMethod, peakParameter, outputType, sortIndex,
				options, blur, centreMethod, centreParameter, 1);

		if (results == null)
		{
			IJ.showMessage("Error", "FindFoci failed");
			return;
		}

		alignPoints(points, results);
	}

	private boolean showDialog()
	{
		ArrayList<String> maskList = FindFoci.buildMaskList(imp);

		GenericDialog gd = new GenericDialog(TITLE);

		gd.addMessage("Realigns the marked PointROI with the appropriate peak");
		gd.addStringField("Title", resultTitle);
		gd.addChoice("Mask", maskList.toArray(new String[0]), maskImage);
		gd.addChoice("Limit_method", limitMethods, limitMethods[limitMethod]);
		gd.addNumericField("Factor", factor, 2);
		gd.addCheckbox("Log_alignments", logAlignments);
		gd.addCheckbox("Update_ROI", updateRoi);
		gd.addCheckbox("Show_moved", showMoved);
		gd.addCheckbox("Show_overlay", showOverlay);
		gd.addCheckbox("Update_overlay", updateOverlay);
		gd.addCheckbox("Show_unaligned", showUnaligned);
		gd.addNumericField("Unaligned_border", unalignedBorder, 0);
		gd.addStringField("Results_directory", resultsDirectory, 30);
		gd.addHelp(gdsc.help.URL.FIND_FOCI);

		gd.showDialog();

		if (gd.wasCanceled())
			return false;

		resultTitle = gd.getNextString();
		maskImage = gd.getNextChoice();
		limitMethod = gd.getNextChoiceIndex();
		factor = gd.getNextNumber();
		logAlignments = gd.getNextBoolean();
		updateRoi = gd.getNextBoolean();
		showMoved = gd.getNextBoolean();
		showOverlay = gd.getNextBoolean();
		updateOverlay = gd.getNextBoolean();
		showUnaligned = gd.getNextBoolean();
		unalignedBorder = (int) gd.getNextNumber();
		resultsDirectory = gd.getNextString();

		if (!resultsDirectory.equals(""))
		{
			File dir = new File(resultsDirectory);
			if (!dir.isDirectory())
			{
				try
				{
					dir.mkdirs();
				}
				catch (SecurityException e)
				{
					IJ.log("Failed to create directory: " + resultsDirectory + ". " + e.getMessage());
				}
			}
			saveResults = new File(resultsDirectory).isDirectory();
		}
		else
			saveResults = false;

		return true;
	}

	private float getBackgroundLevel(AssignedPoint[] points)
	{
		// Get a maximum intensity project of the image
		ZProjector projector = new ZProjector(imp);
		projector.setMethod(ZProjector.MAX_METHOD);
		projector.doProjection();

		ImageProcessor ip = projector.getProjection().getProcessor();

		// Set background using the lowest currently picked point
		float background = Float.POSITIVE_INFINITY;
		for (AssignedPoint point : points)
		{
			final float v = ip.getf(point.getXint(), point.getYint());
			if (background > v)
				background = v;
		}

		// Subtract the image Std.Dev. to get a buffer for error.
		ImageStatistics stats = ip.getStatistics();
		background -= stats.stdDev;

		return background;
	}

	private void alignPoints(AssignedPoint[] points, FindFociResults results)
	{
		if (showOverlay || logAlignments)
			IJ.log(String.format("%s : %s", TITLE, imp.getTitle()));

		// Get the results
		ImagePlus maximaImp = results.mask;
		ArrayList<FindFociResult> resultsArray = results.results;

		// We would like the order of the results to correspond to the maxima image pixel values. 
		Collections.reverse(resultsArray);

		// Use a stack for 3D support
		ImageStack impStack = imp.getStack();
		ImageStack maximaStack = maximaImp.getStack();
		boolean is3d = maximaStack.getSize() > 1;

		// Assign points to maxima
		int[] assigned = new int[resultsArray.size()];
		Arrays.fill(assigned, -1);
		for (AssignedPoint point : points)
			point.setAssignedId(-1);
		float[] pointHeight = new float[points.length];
		float minHeight = Float.POSITIVE_INFINITY;

		sortPoints(points, impStack);

		// TODO - Why is there no maximum move distance?

		for (AssignedPoint point : points)
		{
			int pointId = point.getId();
			point.setAssignedId(-1);
			int x = point.getXint();
			int y = point.getYint();
			int z = point.getZint(); // TODO - Deal with 3D images

			pointHeight[pointId] = impStack.getProcessor(z + 1).getf(x, y);
			if (minHeight > pointHeight[pointId])
			{
				minHeight = pointHeight[pointId];
			}

			ImageProcessor ip = maximaStack.getProcessor(z + 1);

			int maximaId = ip.get(x, y) - 1;
			if (maximaId >= 0)
			{
				if (assigned[maximaId] >= 0)
				{
					// Already assigned - The previous point is higher so it wins.
					// See if any unassigned maxima are closer. This could be an ROI marking error.
					FindFociResult result = resultsArray.get(maximaId);
					double d = distance2(x, y, z, result.x, result.y, result.z);
					float maxHeight = Float.NEGATIVE_INFINITY;

					for (int id = 0; id < assigned.length; id++)
					{
						// Only check the maxima that have not been assigned
						if (assigned[id] < 0)
						{
							result = resultsArray.get(id);

							double newD = distance2(x, y, z, result.x, result.y, result.z);
							if (newD < d)
							{
								// Pick the closest
								//maximaId = id;
								//d = newD;

								// Pick the highest
								final float v = result.maxValue;
								if (maxHeight < v)
								{
									maximaId = id;
									maxHeight = v;
								}
							}
						}
					}
				}

				if (assigned[maximaId] < 0)
				{
					// Assign this ROI point to the maxima
					assigned[maximaId] = pointId;
				}

				point.setAssignedId(maximaId);
			}
		}

		// Analyse assigned points for possible errors
		final float thresholdHeight = getThresholdHeight(points, assigned, resultsArray);

		// Output results
		LinkedList<AssignedPoint> ok = new LinkedList<AssignedPoint>();
		LinkedList<AssignedPoint> moved = new LinkedList<AssignedPoint>();
		LinkedList<AssignedPoint> conflict = new LinkedList<AssignedPoint>();
		LinkedList<AssignedPoint> noAlign = new LinkedList<AssignedPoint>();

		double averageMovedDistance = 0;
		float minAssignedHeight = Float.POSITIVE_INFINITY;

		// List of ROI after moving to the assigned peak
		ArrayList<AssignedPoint> newRoiPoints = new ArrayList<AssignedPoint>(points.length);

		for (AssignedPoint point : points)
		{
			int pointId = point.getId();
			AssignedPoint newPoint = point;
			int x = point.getXint();
			int y = point.getYint();
			int z = point.getZint();

			int maximaId = point.getAssignedId();

			if (maximaId >= 0)
			{
				FindFociResult result = resultsArray.get(maximaId);
				int newX = result.x;
				int newY = result.y;
				int newZ = result.z;

				double d = 0;
				if (newX != x || newY != y || newZ != z)
				{
					d = point.distance(newX, newY, newZ);
				}

				if (result.maxValue < thresholdHeight)
				{
					if (logAlignments)
						log("Point [%d] %s @ %s ~> %s @ %s (%s) below height threshold (< %s)", pointId + 1,
								Utils.rounded(pointHeight[pointId]), getCoords(is3d, x, y, z),
								Utils.rounded(result.maxValue), getCoords(is3d, newX, newY, newZ), IJ.d2s(d, 2),
								Utils.rounded(thresholdHeight));
					noAlign.add(point);
					extractPoint(impStack, "below_threshold", pointId + 1, x, y, z, newX, newY, newZ);
					newPoint = null; // remove unaligned points from the updated ROI
				}
				else
				{
					final float v = (float) result.maxValue;
					if (minAssignedHeight > v)
					{
						minAssignedHeight = v;
					}

					if (assigned[maximaId] == pointId)
					{
						// This is the highest point assigned to the maxima.
						// Check if it is being moved.
						if (logAlignments)
							log("Point [%d] %s @ %s => %s @ %s (%s)", pointId + 1, Utils.rounded(pointHeight[pointId]),
									getCoords(is3d, x, y, z), Utils.rounded(result.maxValue),
									getCoords(is3d, newX, newY, newZ), IJ.d2s(d, 2));
						newPoint = new AssignedPoint(newX, newY, newZ, point.getId());
						if (showMoved && d > 0)
							moved.add((updateOverlay) ? newPoint : point);
						else
							ok.add((updateOverlay) ? newPoint : point);
						averageMovedDistance += d;
					}
					else
					{
						// This point is lower than another assigned to the maxima
						if (logAlignments)
							log("Point [%d] %s @ %s conflicts for assigned point [%d]", pointId + 1,
									Utils.rounded(pointHeight[pointId]), getCoords(is3d, x, y, z),
									assigned[maximaId] + 1);
						conflict.add(point);

						// Output an image showing the pixels
						extractPoint(impStack, "conflict", pointId + 1, x, y, z, newX, newY, newZ);
					}
				}
			}
			else
			{
				if (logAlignments)
					log("Point [%d] %s @ %s cannot be aligned", pointId + 1, Utils.rounded(pointHeight[pointId]),
							getCoords(is3d, x, y, z));
				noAlign.add(point);
				extractPoint(impStack, "noalign", pointId + 1, x, y, z, x, y, z);
				newPoint = null; // remove unaligned points from the updated ROI
			}

			if (newPoint != null)
				newRoiPoints.add(newPoint);
		}

		if (logAlignments)
		{
			log("Minimum picked value = " + Utils.rounded(minHeight));
			log("Threshold = " + Utils.rounded(thresholdHeight));
			log("Minimum assigned peak height = " + Utils.rounded(minAssignedHeight));
		}

		if (averageMovedDistance > 0)
			averageMovedDistance /= (moved.isEmpty()) ? ok.size() : moved.size();

		LinkedList<AssignedPoint> missed = findMissedPoints(resultsArray, assigned, minAssignedHeight);

		updateRoi(newRoiPoints);

		showOverlay(ok, moved, conflict, noAlign, missed);

		createResultsWindow();
		addResult(minHeight, thresholdHeight, minAssignedHeight, ok, moved, averageMovedDistance, conflict, noAlign,
				missed);
	}

	private double distance2(int x, int y, int z, int x2, int y2, int z2)
	{
		final int dx = x - x2;
		final int dy = y - y2;
		final int dz = z - z2;
		return dx * dx + dy * dy + dz * dz;
	}

	public void sortPoints(AssignedPoint[] points, ImageStack impStack)
	{
		if (points == null || impStack == null)
			return;

		int[] pointHeight = new int[points.length];

		// Do this in descending height order
		for (AssignedPoint point : points)
		{
			int x = point.getXint();
			int y = point.getYint();
			int z = point.getZint();
			pointHeight[point.getId()] = impStack.getProcessor(z + 1).get(x, y);
		}
		Arrays.sort(points, new PointHeightComparator(pointHeight));
		for (int pointId = 0; pointId < points.length; pointId++)
		{
			points[pointId].setId(pointId);
		}
	}

	private LinkedList<AssignedPoint> findMissedPoints(ArrayList<FindFociResult> resultsArray, int[] assigned,
			float minAssignedHeight)
	{
		// List maxima above the minimum height that have not been picked
		ImageStack maskStack = getMaskStack();
		LinkedList<AssignedPoint> missed = new LinkedList<AssignedPoint>();
		for (int maximaId = 0; maximaId < resultsArray.size(); maximaId++)
		{
			FindFociResult result = resultsArray.get(maximaId);
			final float v = (float) result.maxValue;
			if (assigned[maximaId] < 0 && v > minAssignedHeight)
			{
				int x = result.x;
				int y = result.y;
				int z = result.z;

				// Check if the point is within the mask
				if (maskStack != null)
				{
					if (maskStack.getProcessor(z + 1).get(x, y) == 0)
						continue;
				}

				missed.add(new AssignedPoint(x, y, z, maximaId));
			}
		}
		return missed;
	}

	private LinkedList<Float> findMissedHeights(ArrayList<FindFociResult> resultsArray, int[] assigned, double t)
	{
		// List maxima above the minimum height that have not been picked
		ImageStack maskStack = getMaskStack();
		LinkedList<Float> missed = new LinkedList<Float>();
		for (int maximaId = 0; maximaId < resultsArray.size(); maximaId++)
		{
			FindFociResult result = resultsArray.get(maximaId);
			if (assigned[maximaId] < 0 && result.maxValue > t)
			{
				int x = result.x;
				int y = result.y;
				int z = result.z;

				// Check if the point is within the mask
				if (maskStack != null)
				{
					if (maskStack.getProcessor(z + 1).get(x, y) == 0)
						continue;
				}

				missed.add(result.maxValue);
			}
		}
		Collections.sort(missed);
		return missed;
	}

	/**
	 * Analyse the assigned heights and attempt to identify any errornous points
	 * 
	 * @return The height below which any point is considered an error
	 */
	private float getThresholdHeight(AssignedPoint[] points, int[] assigned, ArrayList<FindFociResult> resultsArray)
	{
		ArrayList<Float> heights = new ArrayList<Float>(points.length);
		for (int maximaId = 0; maximaId < assigned.length; maximaId++)
		{
			if (assigned[maximaId] >= 0)
			{
				FindFociResult result = resultsArray.get(maximaId);
				heights.add(result.maxValue);
			}
		}

		Collections.sort(heights);

		// Box plot type analysis:
		// The bottom and top of the box are always the 25th and 75th percentile (the lower and upper quartiles, respectively), 
		// and the band near the middle of the box is always the 50th percentile (the median). 
		// But the ends of the whiskers can represent several possible alternative values, among them:
		// - the minimum and maximum of all the data
		// - the lowest datum still within 1.5 IQR of the lower quartile, 
		//   and the highest datum still within 1.5 IQR of the upper quartile
		// - one standard deviation above and below the mean of the data
		// - 2nd/9th percentile etc.

		if (heights.isEmpty())
			return 0;

		double t = heights.get(0);

		switch (limitMethod)
		{
			case 1:
				// Factor of inter-quartile range
				float q1 = getQuartileBoundary(heights, 0.25);
				float q2 = getQuartileBoundary(heights, 0.5);

				t = q1 - factor * (q2 - q1);
				if (logAlignments)
					log("Limiting peaks %s: %s - %s * %s = %s", limitMethods[limitMethod], Utils.rounded(q1),
							Utils.rounded(factor), Utils.rounded(q2 - q1), Utils.rounded(t));

				break;

			case 2:
				// n * Std.Dev. below the mean
				double[] stats = getStatistics(heights);
				t = stats[0] - factor * stats[1];

				if (logAlignments)
					log("Limiting peaks %s: %s - %s * %s = %s", limitMethods[limitMethod], Utils.rounded(stats[0]),
							Utils.rounded(factor), Utils.rounded(stats[1]), Utils.rounded(t));

				break;

			case 3:
				// nth Percentile
				t = getQuartileBoundary(heights, 0.01 * factor);
				if (logAlignments)
					log("Limiting peaks %s: %sth = %s", limitMethods[limitMethod], Utils.rounded(factor),
							Utils.rounded(t));

				break;

			case 4:
				// Number of missed points is a factor below picked points,
				// i.e. the number of potential maxima is a fraction of the number of assigned maxima.
				List<Float> missedHeights = findMissedHeights(resultsArray, assigned, t);
				double fraction = factor / 100;
				for (int pointId = 0; pointId < heights.size(); pointId++)
				{
					t = heights.get(pointId);
					// Count points 
					int missedCount = countPoints(missedHeights, t);
					int assignedCount = heights.size() - pointId;
					int totalCount = missedCount + assignedCount;

					if ((missedCount / (double) totalCount) < fraction)
					{
						break;
					}
				}

				if (t == heights.get(heights.size() - 1))
				{
					log("Warning: Maximum height threshold reached when attempting to limit the number of missed peaks");
				}

				if (logAlignments)
					log("Limiting peaks %s: %s %% = %s", limitMethods[limitMethod], Utils.rounded(factor),
							Utils.rounded(t));
				break;

			default:
		}

		// Round for integer data
		if (integerData(heights))
			t = round(t);

		return (float) t;
	}

	private boolean integerData(ArrayList<Float> heights)
	{
		for (double d : heights)
			if ((int) d != d)
				return false;

		return true;
	}

	/**
	 * Count points that are above the given height
	 * 
	 * @param missedHeights
	 * @param height
	 * @return The count
	 */
	private int countPoints(List<Float> missedHeights, double height)
	{
		int count = missedHeights.size();
		for (double h : missedHeights)
		{
			if (h < height)
			{
				count--;
			}
			else
			{
				break;
			}
		}
		return count;
	}

	private int round(double d)
	{
		return (int) (d + 0.5);
	}

	/**
	 * Get the quartile boundary for the given fraction, e.g. fraction 0.25 is Q1-Q2 interquartile.
	 * 
	 * @param heights
	 * @param fraction
	 * @return The boundary
	 */
	public static float getQuartileBoundary(ArrayList<Float> heights, double fraction)
	{
		if (heights.isEmpty())
			return 0;
		if (heights.size() == 1)
			return heights.get(0).floatValue();

		int upper = (int) Math.ceil(heights.size() * fraction);
		int lower = (int) Math.floor(heights.size() * fraction);

		upper = Math.min(Math.max(upper, 0), heights.size() - 1);
		lower = Math.min(Math.max(lower, 0), heights.size() - 1);

		return (float) ((heights.get(upper) + heights.get(lower)) / 2.0);
	}

	private double[] getStatistics(ArrayList<Float> heights)
	{
		double sum = 0.0;
		double sum2 = 0.0;
		int n = heights.size();
		for (double h : heights)
		{
			sum += h;
			sum2 += (h * h);
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

		return new double[] { av, stdDev };
	}

	private ImageStack getMaskStack()
	{
		ImagePlus mask = WindowManager.getImage(maskImage);
		if (mask != null)
		{
			return mask.getStack();
		}
		return null;
	}

	private Object getCoords(boolean is3d, int x, int y, int z)
	{
		if (is3d)
			return String.format("%d,%d,%d", x, y, z);
		return String.format("%d,%d", x, y);
	}

	private void log(String format, Object... args)
	{
		if (logAlignments)
		{
			IJ.log(String.format(format, args));
		}
	}

	private void extractPoint(ImageStack impStack, String type, int Id, int x, int y, int z, int newX, int newY,
			int newZ)
	{
		if (unalignedBorder <= 0)
			return;
		if (showUnaligned || saveResults)
		{
			int xx = impStack.getWidth() - 1;
			int yy = impStack.getHeight() - 1;

			int minX = Math.min(xx, Math.max(0, Math.min(x, newX) - unalignedBorder));
			int minY = Math.min(yy, Math.max(0, Math.min(y, newY) - unalignedBorder));
			int maxX = Math.min(xx, Math.max(0, Math.max(x, newX) + unalignedBorder));
			int maxY = Math.min(yy, Math.max(0, Math.max(y, newY) + unalignedBorder));
			int w = maxX - minX + 1;
			int h = maxY - minY + 1;
			ImageStack newStack = new ImageStack(w, h);
			for (int slice = 1; slice <= impStack.getSize(); slice++)
			{
				ImageProcessor ip = impStack.getProcessor(slice).duplicate();
				ip.setRoi(minX, minY, w, h);
				ip = ip.crop();
				newStack.addSlice(null, ip);
			}
			String title = imp.getShortTitle() + "_" + type + "_" + Id;
			ImagePlus pointImp = WindowManager.getImage(title);
			if (pointImp == null)
				pointImp = new ImagePlus(title, newStack);
			else
				pointImp.setStack(newStack);

			pointImp.setOverlay(null);
			if (newX - x != 0 || newY - y != 0)
			{
				ArrayList<BasePoint> ok = new ArrayList<BasePoint>(1);
				ok.add(new BasePoint(newX - minX, newY - minY, newZ));
				MatchPlugin.addOverlay(pointImp, ok, MatchPlugin.MATCH);
			}
			ArrayList<BasePoint> conflict = new ArrayList<BasePoint>(1);
			conflict.add(new BasePoint(x - minX, y - minY, z));
			MatchPlugin.addOverlay(pointImp, conflict, MatchPlugin.UNMATCH1);
			pointImp.updateAndDraw();

			if (saveResults)
			{
				IJ.save(pointImp, resultsDirectory + File.separatorChar + title + ".tif");
			}

			if (showUnaligned)
			{
				pointImp.show();
			}
		}
	}

	private void updateRoi(ArrayList<? extends BasePoint> newRoiPoints)
	{
		if (updateRoi)
		{
			imp.setRoi(PointManager.createROI(newRoiPoints));
		}
	}

	private void showOverlay(LinkedList<AssignedPoint> ok, LinkedList<AssignedPoint> moved,
			LinkedList<AssignedPoint> conflict, LinkedList<AssignedPoint> noAlign, LinkedList<AssignedPoint> missed)
	{
		// Add overlap
		if (showOverlay)
		{
			IJ.log("Overlay key:");
			IJ.log("  OK = Green");
			if (showMoved)
				IJ.log("  Moved = Yellow");
			IJ.log("  Conflict = Red");
			IJ.log("  NoAlign = Blue");
			IJ.log("  Missed = Magenta");

			imp.setOverlay(null);
			imp.saveRoi();
			imp.killRoi();
			MatchPlugin.addOverlay(imp, ok, MatchPlugin.MATCH);
			MatchPlugin.addOverlay(imp, moved, MatchPlugin.UNMATCH1);
			MatchPlugin.addOverlay(imp, conflict, Color.red);
			MatchPlugin.addOverlay(imp, noAlign, MatchPlugin.UNMATCH2);
			MatchPlugin.addOverlay(imp, missed, Color.magenta);
			imp.updateAndDraw();
		}
	}

	private void createResultsWindow()
	{
		if (java.awt.GraphicsEnvironment.isHeadless())
		{
			if (writeHeader)
			{
				writeHeader = false;
				IJ.log(createResultsHeader());
			}
		}
		else
		{
			if (resultsWindow == null || !resultsWindow.isShowing())
			{
				resultsWindow = new TextWindow(TITLE + " Results", createResultsHeader(), "", 900, 300);
			}
		}
	}

	private String createResultsHeader()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Title\t");
		sb.append("Image\t");
		sb.append("Method\t");
		sb.append("Factor\t");
		sb.append("Min Height\t");
		sb.append("Threshold\t");
		sb.append("Min Assigned Height\t");
		sb.append("OK\t");
		sb.append("Moved\t");
		sb.append("Av.Move\t");
		sb.append("Conflict\t");
		sb.append("NoAlign\t");
		sb.append("Missed\t");
		sb.append("N\t");
		sb.append("TP\t");
		sb.append("FP\t");
		sb.append("FN\t");
		sb.append("Precision\t");
		sb.append("Recall\t");
		sb.append("Jaccard\t");
		sb.append("F1-score");
		return sb.toString();
	}

	private void addResult(float minHeight, float thresholdHeight, float minAssignedHeight,
			LinkedList<AssignedPoint> ok, LinkedList<AssignedPoint> moved, double averageMovedDistance,
			LinkedList<AssignedPoint> conflict, LinkedList<AssignedPoint> noAlign, LinkedList<AssignedPoint> missed)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(resultTitle).append("\t");
		sb.append(imp.getTitle()).append("\t");
		sb.append(limitMethods[limitMethod]).append("\t");
		sb.append(factor).append("\t");
		sb.append(Utils.rounded(minHeight)).append("\t");
		sb.append(Utils.rounded(thresholdHeight)).append("\t");
		sb.append(Utils.rounded(minAssignedHeight)).append("\t");
		sb.append(ok.size()).append("\t");
		sb.append(moved.size()).append("\t");
		sb.append(IJ.d2s(averageMovedDistance, 2)).append("\t");
		sb.append(conflict.size()).append("\t");
		sb.append(noAlign.size()).append("\t");
		sb.append(missed.size()).append("\t");

		int tp = ok.size() + moved.size();
		int fp = conflict.size() + noAlign.size();
		int fn = missed.size();
		MatchResult match = new MatchResult(tp, fp, fn, 0);

		sb.append(match.getNumberPredicted()).append("\t");
		sb.append(tp).append("\t");
		sb.append(fp).append("\t");
		sb.append(fn).append("\t");
		sb.append(IJ.d2s(match.getPrecision(), 4)).append("\t");
		sb.append(IJ.d2s(match.getRecall(), 4)).append("\t");
		sb.append(IJ.d2s(match.getJaccard(), 4)).append("\t");
		sb.append(IJ.d2s(match.getFScore(1), 4));

		if (java.awt.GraphicsEnvironment.isHeadless())
		{
			IJ.log(sb.toString());
		}
		else
		{
			resultsWindow.append(sb.toString());
		}
	}

	public class PointHeightComparator implements Comparator<AssignedPoint>
	{
		private int[] pointHeight;

		public PointHeightComparator(int[] pointHeight)
		{
			this.pointHeight = pointHeight;
		}

		public int compare(AssignedPoint o1, AssignedPoint o2)
		{
			int diff = pointHeight[o1.getId()] - pointHeight[o2.getId()];
			if (diff > 0)
				return -1;
			if (diff < 0)
				return 1;
			return 0;
		}
	}
}
