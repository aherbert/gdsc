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

import java.awt.Frame;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import gdsc.UsageTracker;
import gdsc.core.utils.Sort;
import gdsc.help.URL;
import gdsc.threshold.ThreadAnalyser;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

/**
 * Finds spots in an image. Locates the closest neighbour spot within a radius
 * and produces a line profile through the spots. If no neighbour is present
 * produces a line profile through the principle axis.
 */
public class SpotSeparation implements PlugInFilter
{
	public static final String TITLE = "Spot Separation";

	private final static String[] methods = { "MaxEntropy", "Yen" };

	private static String method = "";
	private static double radius = 10;
	private static boolean showSpotImage = true;
	private static boolean showLineProfiles = true;

	private int flags = DOES_16 + DOES_8G;

	private LinkedList<String> plotProfiles = new LinkedList<String>();
	private static TextWindow resultsWindow = null;
	private ImagePlus imp;
	private String resultEntry = null;
	private Calibration cal;

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	public int setup(String arg, ImagePlus imp)
	{
		UsageTracker.recordPlugin(this.getClass(), arg);

		if (imp == null)
			return DONE;
		this.imp = imp;
		return flags;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	public void run(ImageProcessor ip)
	{
		if (!showDialog())
			return;

		// TODO - Add some options to be able to preview the spots using a ExtendedPluginFilter

		FindFociResults results = runFindFoci(ip);

		// Respect the image ROI
		results = cropToRoi(results, ip);

		if (results == null)
			return;

		ImagePlus maximaImp = results.mask;
		ImageProcessor spotIp = maximaImp.getProcessor();
		ArrayList<FindFociResult> resultsArray = results.results;

		showSpotImage(maximaImp, resultsArray);

		AssignedPoint[] points = extractSpotCoordinates(resultsArray);

		float[][] d = computeDistanceMatrix(points);

		int[] assigned = assignClosestPairs(d);

		createResultsWindow();

		// Draw line profiles
		for (int i = 0; i < assigned.length; i++)
		{

			float[] xValues, yValues;
			String profileTitle;

			if (assigned[i] < 0)
			{
				// For single spots assign principle axis and produce a line
				// profile

				float xpos = points[i].x;
				float ypos = points[i].y;
				int peakId = points[i].id;
				profileTitle = TITLE + " Spot " + peakId + " Profile";

				float[] com = new float[2];
				//double angle = calculateOrientation(spotIp, xpos, ypos, peakId, com);
				double angle = calculateOrientation(ip, spotIp, xpos, ypos, peakId, com);

				// Angle is in Radians anti-clockwise from the X-axis 

				float step = 0.5f;
				float dx = (float) (Math.cos(angle) * step);
				float dy = (float) (Math.sin(angle) * step);

				System.out.printf("%s : Angle = %f, dx=%f, dy=%f. d(x,y)=%.2f,%.2f\n", profileTitle,
						angle * 180.0 / Math.PI, dx, dy, points[i].x - com[0], points[i].y - com[1]);

				// Draw line profile through the centre calculated from the moments. This will fail if
				// the object has a hollow centre (e.g. a horseshoe) so check if it is in the mask.

				if (!isInPeak(spotIp, com[0], com[0], peakId))
				{
					com[0] = points[i].x;
					com[1] = points[i].y;
				}

				// Extend the line to the edge
				LinkedList<float[]> before = new LinkedList<float[]>();
				xpos = com[0];
				ypos = com[1];
				for (int s = 1;; s++)
				{
					xpos -= dx;
					ypos -= dy;
					if (isInPeak(spotIp, xpos, ypos, peakId))
					{
						before.add(new float[] { -step * s, (float) ip.getInterpolatedValue(xpos, ypos) });
					}
					else
						break;
				}

				// Extend the line to the edge in the other direction
				LinkedList<float[]> after = new LinkedList<float[]>();
				xpos = com[0];
				ypos = com[1];
				for (int s = 1;; s++)
				{
					xpos += dx;
					ypos += dy;
					if (isInPeak(spotIp, xpos, ypos, peakId))
					{
						before.add(new float[] { step * s, (float) ip.getInterpolatedValue(xpos, ypos) });
					}
					else
						break;
				}

				@SuppressWarnings("unchecked")
				float[][] profileValues = convertToFloat(before, after);
				xValues = profileValues[0];
				yValues = profileValues[1];

				sortValues(xValues, yValues);
				addSingleResult(peakId, xValues, yValues);
			}
			else
			{
				// Skip the second of a pair already processed
				if (assigned[i] < i)
					continue;

				// For pairs draw a line between the spot centres and extend to
				// the edge of the spots

				int j = assigned[i];
				profileTitle = TITLE + " Spot " + points[i].id + " to " + points[j].id + " Profile";

				float distance = (float) Math.sqrt(d[i][j]);

				int samples = (int) Math.ceil(distance);
				float dx = (float) (points[j].x - points[i].x) / samples;
				float dy = (float) (points[j].y - points[i].y) / samples;
				float step = (float) Math.sqrt(dx * dx + dy * dy);

				// The line between the two maxima
				ArrayList<float[]> values = new ArrayList<float[]>(samples + 1);
				values.add(new float[] { 0, ip.getf(points[i].x, points[i].y) });
				float xpos = points[i].x;
				float ypos = points[i].y;
				for (int s = 1; s < samples; s++)
				{
					xpos += dx;
					ypos += dy;
					values.add(new float[] { step * s, (float) ip.getInterpolatedValue(xpos, ypos) });
				}
				values.add(new float[] { distance, ip.getf(points[j].x, points[j].y) });

				// Extend the line to the edge of the first spot
				LinkedList<float[]> before = new LinkedList<float[]>();
				int peakId = points[i].id;
				xpos = points[i].x;
				ypos = points[i].y;
				for (int s = 1;; s++)
				{
					xpos -= dx;
					ypos -= dy;
					if (isInPeak(spotIp, xpos, ypos, peakId))
					{
						before.add(new float[] { -step * s, (float) ip.getInterpolatedValue(xpos, ypos) });
					}
					else
						break;
				}

				// Extend the line to the edge of the second spot
				LinkedList<float[]> after = new LinkedList<float[]>();
				peakId = points[j].id;
				xpos = points[j].x;
				ypos = points[j].y;
				for (int s = 1;; s++)
				{
					xpos += dx;
					ypos += dy;
					if (isInPeak(spotIp, xpos, ypos, peakId))
					{
						before.add(new float[] { distance + step * s, (float) ip.getInterpolatedValue(xpos, ypos) });
					}
					else
						break;
				}

				@SuppressWarnings("unchecked")
				float[][] profileValues = convertToFloat(before, values, after);
				xValues = profileValues[0];
				yValues = profileValues[1];

				float offset = sortValues(xValues, yValues);
				addPairResult(peakId, xValues, yValues, distance, offset);
			}

			showLineProfile(xValues, yValues, profileTitle);
		}

		// Summarise results

		closeOtherLineProfiles();
	}

	private boolean isInPeak(ImageProcessor spotIp, float xpos, float ypos, int peakId)
	{
		//return (int) spotIp.getInterpolatedValue(xpos, ypos) == peakId;
		return spotIp.get(Math.round(xpos), Math.round(ypos)) == peakId;
	}

	private float sortValues(float[] xValues, float[] yValues)
	{
		if (xValues.length == 0)
			return 0;
		Sort.sortArrays(yValues, xValues, true);
		// Reset to start at zero
		float offset = -xValues[0];
		for (int ii = 0; ii < xValues.length; ii++)
		{
			xValues[ii] += offset;
		}
		return offset;
	}

	public boolean showDialog()
	{
		GenericDialog gd = new GenericDialog(TITLE);
		gd.addHelp(URL.UTILITY);

		gd.addMessage("Analyse line profiles between spots within a separation distance");

		gd.addChoice("Threshold_method", methods, method);
		gd.addSlider("Radius", 5, 50, radius);
		gd.addCheckbox("Show_spot_image", showSpotImage);
		gd.addCheckbox("Show_line_profiles", showLineProfiles);

		gd.showDialog();
		if (gd.wasCanceled())
			return false;

		method = gd.getNextChoice();
		radius = gd.getNextNumber();
		showSpotImage = gd.getNextBoolean();
		showLineProfiles = gd.getNextBoolean();

		return true;
	}

	private FindFociResults cropToRoi(FindFociResults results, ImageProcessor ip)
	{
		Roi roi = imp.getRoi();
		if (roi == null || results == null)
			return results;

		ImagePlus maximaImp = results.mask;
		ImageProcessor spotIp = maximaImp.getProcessor();
		ArrayList<FindFociResult> resultsArray = results.results;

		// Find all maxima that are within the ROI bounds
		final byte[] mask = (roi.getMask() == null) ? null : (byte[]) roi.getMask().getPixels();
		final Rectangle bounds = roi.getBounds();
		final int maxx = bounds.x + bounds.width;
		final int maxy = bounds.y + bounds.height;
		int[] newId = new int[resultsArray.size() + 1];
		int newCount = 0;

		for (int i = 0; i < resultsArray.size(); i++)
		{
			final FindFociResult result = resultsArray.get(i);
			final int x = result.x;
			final int y = result.y;

			if (x >= bounds.x && x < maxx && y >= bounds.y && y < maxy)
			{
				if (mask != null)
				{
					final int index = (y - bounds.y) * bounds.width + x - bounds.x;
					if (mask[index] == 0)
						continue;
				}
				newId[i] = ++newCount;
			}
		}

		if (newCount == 0)
			return null;

		// Renumber the remaining valid spots
		ArrayList<FindFociResult> newResultsArray = new ArrayList<FindFociResult>(newCount);
		int[] maskIdMap = new int[resultsArray.size() + 1];
		for (int i = 0; i < resultsArray.size(); i++)
		{
			final FindFociResult result = resultsArray.get(i);
			if (newId[i] > 0)
			{
				result.id = newId[i];
				newResultsArray.add(result);
				// Reverse peak IDs so the highest value is the first in the
				// results list
				int oldMaskId = resultsArray.size() - i;
				maskIdMap[oldMaskId] = newCount - newId[i] + 1;
			}
		}

		// Update the image
		for (int i = 0; i < spotIp.getPixelCount(); i++)
		{
			final int oldPeakId = spotIp.get(i);
			if (oldPeakId > 0)
			{
				spotIp.set(i, maskIdMap[oldPeakId]);
			}
		}
		maximaImp.setProcessor(spotIp);

		return new FindFociResults(maximaImp, newResultsArray, null);
	}

	private FindFociResults runFindFoci(ImageProcessor ip)
	{
		// Run FindFoci to get the spots
		// Get each spot as a different number with the centre using the search
		// centre

		// Allow image thresholding
		int backgroundMethod = FindFoci.BACKGROUND_AUTO_THRESHOLD;
		double backgroundParameter = 0;
		if (ip.getMinThreshold() != ImageProcessor.NO_THRESHOLD)
		{
			backgroundMethod = FindFoci.BACKGROUND_ABSOLUTE;
			backgroundParameter = ip.getMinThreshold();
		}

		// TODO - Find the best method for this

		FindFoci ff = new FindFoci();
		String autoThresholdMethod = method;
		int searchMethod = FindFoci.SEARCH_ABOVE_BACKGROUND;
		double searchParameter = 0;
		int maxPeaks = 100;
		int minSize = 3;
		int peakMethod = FindFoci.PEAK_ABSOLUTE;
		double peakParameter = 5;
		int outputType = FindFoci.OUTPUT_MASK_PEAKS | FindFoci.OUTPUT_MASK_NO_PEAK_DOTS;
		int sortIndex = FindFoci.SORT_MAX_VALUE;
		int options = FindFoci.OPTION_STATS_INSIDE;
		double blur = 1.5;
		int centreMethod = FindFoci.CENTRE_MAX_VALUE_SEARCH;
		double centreParameter = 0;
		double fractionParameter = 0;

		FindFociResults results = ff.findMaxima(new ImagePlus("tmp", ip), null, backgroundMethod, backgroundParameter,
				autoThresholdMethod, searchMethod, searchParameter, maxPeaks, minSize, peakMethod, peakParameter,
				outputType, sortIndex, options, blur, centreMethod, centreParameter, fractionParameter);
		return results;
	}

	private void showSpotImage(ImagePlus maximaImp, ArrayList<FindFociResult> resultsArray)
	{
		if (showSpotImage)
		{
			ImageProcessor spotIp = maximaImp.getProcessor();
			spotIp.setMinAndMax(0, resultsArray.size());
			String title = TITLE + " Spots";
			ImagePlus spotImp = WindowManager.getImage(title);
			if (spotImp == null)
			{
				maximaImp.setTitle(title);
				maximaImp.show();
			}
			else
			{
				spotImp.setProcessor(spotIp);
				spotImp.updateAndDraw();
			}
		}
	}

	/**
	 * Extract the centre of each maxima from the results
	 * 
	 * @param resultsArray
	 * @return
	 */
	private AssignedPoint[] extractSpotCoordinates(ArrayList<FindFociResult> resultsArray)
	{
		AssignedPoint[] points = new AssignedPoint[resultsArray.size()];
		int i = 0;
		int maxId = resultsArray.size() + 1;
		for (FindFociResult result : resultsArray)
		{
			points[i] = new AssignedPoint(result.x, result.y, maxId - result.id);
			i++;
		}
		return points;
	}

	/**
	 * Compute the all-vs-all squared distance matrix
	 * 
	 * @param points
	 * @return
	 */
	private float[][] computeDistanceMatrix(AssignedPoint[] points)
	{
		float[][] d = new float[points.length][points.length];
		for (int i = 0; i < d.length; i++)
		{
			for (int j = i + 1; j < d.length; j++)
			{
				d[i][j] = (float) points[i].distance2(points[j].x, points[j].y);
				d[j][i] = d[i][j];
			}
		}
		return d;
	}

	/**
	 * Finds the pairs ij which are closer than the radius. Any given point can
	 * only be assigned to one pair and so the closest remaining unassigned
	 * points are processed until no more pairs are left.
	 * 
	 * @param d
	 *            Squared distance matrix
	 * @return Array of indices that each point is assigned to. Unassigned
	 *         points will have an index of -1.
	 */
	private int[] assignClosestPairs(float[][] d)
	{
		float d2 = (float) (radius * radius);
		int[] assigned = new int[d.length];
		Arrays.fill(assigned, -1);
		while (true)
		{
			float minD = d2;
			int ii = 0, jj = 0;
			for (int i = 0; i < d.length; i++)
			{
				if (assigned[i] >= 0)
					continue;
				for (int j = i + 1; j < d.length; j++)
				{
					if (assigned[j] >= 0)
						continue;
					if (d[i][j] < minD)
					{
						minD = d[i][j];
						ii = i;
						jj = j;
					}
				}
			}
			if (ii != jj)
			{
				assigned[ii] = jj;
				assigned[jj] = ii;
			}
			else
				break;
		}
		return assigned;
	}

	/**
	 * Create the result window (if it is not available)
	 */
	private void createResultsWindow()
	{
		if (resultsWindow == null || !resultsWindow.isShowing())
		{
			resultsWindow = new TextWindow(TITLE + " Positions", createResultsHeader(), "", 700, 300);
		}

		// Create the image result prefix
		StringBuilder sb = new StringBuilder();
		sb.append(imp.getTitle());
		if (imp.getStackSize() > 1)
		{
			sb.append(" [Z").append(imp.getSlice()).append("C").append(imp.getChannel()).append("T")
					.append(imp.getFrame()).append("]");
		}
		sb.append('\t');
		cal = imp.getCalibration();
		sb.append(cal.getXUnit());
		if (!(cal.getYUnit().equalsIgnoreCase(cal.getXUnit()) && cal.getZUnit().equalsIgnoreCase(cal.getXUnit())))
		{
			sb.append(" ").append(cal.getYUnit()).append(" ").append(cal.getZUnit()).append(" ");
		}
		sb.append('\t');
		resultEntry = sb.toString();
	}

	private String createResultsHeader()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Image\t");
		sb.append("Units\t");
		sb.append("Peak Id\t");
		sb.append("Type\t");
		sb.append("Width1\t");
		sb.append("Width2\t");
		sb.append("Distance");
		return sb.toString();
	}

	private void addSingleResult(int id, float[] xValues, float[] yValues)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(resultEntry);
		sb.append(id).append("\tSingle\t");

		int[] maxima = new int[2];
		float[] d = ThreadAnalyser.countMaxima(xValues, yValues, maxima);
		if (d.length > 1)
		{
			// Find the plot range
			int[] maxIndices = new int[d.length];
			float max = yValues[0], min = yValues[0];
			for (int i = 1; i < yValues.length; i++)
			{
				if (max < yValues[i])
				{
					maxIndices[0] = i;
					max = yValues[i];
				}
				if (min > yValues[i])
					min = yValues[i];
			}
			// Set the minimum height for a maxima using a a fraction of the plot range
			float height = (max - min) * 0.1f;

			int maxCount = 1;
			int start = findIndex(0, d[0], xValues);
			for (int i = 1; i < d.length; i++)
			{
				int end = findIndex(start, d[i], xValues);

				// Set threshold using the minimum height
				float threshold = Math.min(yValues[start], yValues[end]) - height;
				for (int j = start + 1; j < end; j++)
				{
					if (yValues[j] < threshold)
					{
						// Add to the maxima
						if (!findIndex(maxIndices, maxCount, start))
							maxIndices[maxCount++] = start;
						if (!findIndex(maxIndices, maxCount, end))
							maxIndices[maxCount++] = end;
						break;
					}
				}

				start = end;
			}

			if (maxCount > 1)
			{
				// There are definitely multiple peaks in this single spot

				// Find the widths of each peak
				Arrays.sort(maxIndices);
				float[] minD = new float[maxIndices.length];
				float lastMinD = 0;
				start = maxIndices[0];
				for (int i = 1; i < maxCount; i++)
				{
					int end = maxIndices[i];
					min = Float.POSITIVE_INFINITY;
					float middle = xValues[end] - xValues[start];
					for (int j = start + 1; j < end; j++)
					{
						if (min > yValues[j])
						{
							min = yValues[j];
							middle = xValues[j];
						}
					}
					minD[i - 1] = middle + lastMinD;
					lastMinD = minD[i - 1];

					start = end;
				}
				minD[maxCount - 1] = xValues[xValues.length - 1];

				System.out.printf("Multiple peak in a single spot: Peak Id %d = %d peaks\n", id, maxCount);

				// TODO - Perform a better table output if there are more than 2 peaks.
				sb.append(String.format("%.2f\t", minD[0] * cal.pixelWidth));
				sb.append(String.format("%.2f\t", (minD[1] - minD[0]) * cal.pixelWidth));
				sb.append(String.format("%.2f", (xValues[maxIndices[1]] - xValues[maxIndices[0]]) * cal.pixelWidth));
				resultsWindow.append(sb.toString());
				return;
			}
		}

		double width = xValues[xValues.length - 1];
		sb.append(String.format("%.2f", width * cal.pixelWidth));
		resultsWindow.append(sb.toString());
	}

	private boolean findIndex(int[] maxIndices, int maxCount, int index)
	{
		for (int i = maxCount; i-- > 0;)
			if (maxIndices[i] == index)
				return true;
		return false;
	}

	private int findIndex(int i, float f, float[] xValues)
	{
		for (; i < xValues.length; i++)
			if (f == xValues[i])
				return i;
		throw new RuntimeException("Unable to find peak index for the specified maxima");
	}

	private void addPairResult(int id, float[] xValues, float[] yValues, float distance, float peak1Start)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(resultEntry);
		sb.append(id).append("\tPair\t");
		// Find the midpoint between the two peaks
		int i = 0;
		while (i < xValues.length && xValues[i] < peak1Start)
			i++;
		float min = Float.POSITIVE_INFINITY;
		float middle = distance * 0.5f;
		while (i < xValues.length && xValues[i] <= peak1Start + distance)
		{
			if (min > yValues[i])
			{
				min = yValues[i];
				middle = xValues[i];
			}
			i++;
		}

		double width1 = middle;
		double width2 = xValues[xValues.length - 1] - width1;
		sb.append(String.format("%.2f\t%.2f\t%.2f", width1 * cal.pixelWidth, width2 * cal.pixelWidth,
				distance * cal.pixelWidth));
		resultsWindow.append(sb.toString());
	}

	/**
	 * Estimate the angle using the central moments.
	 * <p>
	 * See Burger & Burge, Digital Image Processing, An Algorithmic Introduction using Java (1st Edition), pp231.
	 * 
	 * @param spotIp
	 * @param xpos
	 * @param ypos
	 * @param peakId
	 * @param com
	 *            The calculated centre-of-mass
	 * @return The orientation in range -pi/2 to pi/2 from the x-axis, incrementing clockwise if the Y-axis points
	 *         downwards
	 */
	@SuppressWarnings("unused")
	private double calculateOrientation(ImageProcessor spotIp, float xpos, float ypos, int peakId, float[] com)
	{
		// Find the limits of the spot and calculate the centre of mass
		int maxu = (int) xpos;
		int minu = maxu;
		int maxv = (int) ypos;
		int minv = maxv;

		double u00 = 0;
		double xCtr = 0, yCtr = 0;
		for (int ii = 0; ii < spotIp.getPixelCount(); ii++)
		{
			if (spotIp.get(ii) == peakId)
			{
				int u = ii % spotIp.getWidth();
				int v = ii / spotIp.getWidth();
				if (maxu < u)
					maxu = u;
				if (minu > u)
					minu = u;
				if (maxv < v)
					maxv = v;
				if (minv > v)
					minv = v;
				u00++;
				xCtr += u;
				yCtr += v;
			}
		}
		xCtr /= u00;
		yCtr /= u00;

		if (com != null)
		{
			com[0] = (float) xCtr;
			com[1] = (float) yCtr;
		}

		// Calculate moments
		double u11 = 0;
		double u20 = 0;
		double u02 = 0;
		for (int v = minv; v <= maxv; v++)
		{
			for (int u = minu, ii = v * spotIp.getWidth() + minu; u <= maxu; u++, ii++)
			{
				if (spotIp.get(ii) == peakId)
				{
					double dx = u - xCtr;
					double dy = v - yCtr;
					u11 += dx * dy;
					u20 += dx * dx;
					u02 += dy * dy;
				}
			}
		}

		// Calculate the ellipsoid
		double A = 2 * u11;
		double B = u20 - u02;
		double angle = 0.5 * Math.atan2(A, B);
		return angle;
	}

	/**
	 * Estimate the angle using the central moments. Moments are weighted using the original image values
	 * <p>
	 * See Burger & Burge, Digital Image Processing, An Algorithmic Introduction using Java (1st Edition), pp231.
	 * 
	 * @param ip
	 * @param spotIp
	 * @param xpos
	 * @param ypos
	 * @param peakId
	 * @param com
	 *            The calculated centre-of-mass
	 * @return The orientation in range -pi/2 to pi/2 from the x-axis, incrementing clockwise if the Y-axis points
	 *         downwards
	 */
	private double calculateOrientation(ImageProcessor ip, ImageProcessor spotIp, float xpos, float ypos, int peakId,
			float[] com)
	{
		// Find the limits of the spot and calculate the centre of mass
		int maxu = (int) xpos;
		int minu = maxu;
		int maxv = (int) ypos;
		int minv = maxv;

		double u00 = 0;
		double xCtr = 0, yCtr = 0;
		for (int ii = 0; ii < spotIp.getPixelCount(); ii++)
		{
			if (spotIp.get(ii) == peakId)
			{
				final float value = ip.getf(ii);
				int u = ii % spotIp.getWidth();
				int v = ii / spotIp.getWidth();
				if (maxu < u)
					maxu = u;
				if (minu > u)
					minu = u;
				if (maxv < v)
					maxv = v;
				if (minv > v)
					minv = v;
				u00 += value;
				xCtr += u * value;
				yCtr += v * value;
			}
		}
		xCtr /= u00;
		yCtr /= u00;

		if (com != null)
		{
			com[0] = (float) xCtr;
			com[1] = (float) yCtr;
		}

		// Calculate moments
		double u11 = 0;
		double u20 = 0;
		double u02 = 0;
		for (int v = minv; v <= maxv; v++)
		{
			for (int u = minu, ii = v * spotIp.getWidth() + minu; u <= maxu; u++, ii++)
			{
				if (spotIp.get(ii) == peakId)
				{
					final float value = ip.getf(ii);
					double dx = u - xCtr;
					double dy = v - yCtr;
					u11 += value * dx * dy;
					u20 += value * dx * dx;
					u02 += value * dy * dy;
				}
			}
		}

		// Calculate the ellipsoid
		double A = 2 * u11;
		double B = u20 - u02;
		double angle = 0.5 * Math.atan2(A, B);
		return angle;
	}

	@SafeVarargs
	private final float[][] convertToFloat(List<float[]>... lists)
	{
		int size = 0;
		for (List<float[]> list : lists)
		{
			size += list.size();
		}
		float[][] results = new float[2][size];
		int i = 0;
		for (List<float[]> list : lists)
		{
			for (float[] f : list)
			{
				results[0][i] = f[0];
				results[1][i] = f[1];
				i++;
			}
		}
		return results;
	}

	private void closeOtherLineProfiles()
	{
		if (!showLineProfiles)
			return;
		Frame[] frames = WindowManager.getNonImageWindows();
		if (frames == null)
			return;
		for (Frame f : frames)
		{
			if (plotProfiles.contains(f.getTitle()))
				continue;
			if (f.getTitle().startsWith(TITLE) && f.getTitle().endsWith("Profile"))
			{
				if (f instanceof PlotWindow)
				{
					PlotWindow p = ((PlotWindow) f);
					p.close();
				}
			}
		}
	}

	/**
	 * Show a plot of the line profile
	 * 
	 * @param xValues
	 * @param yValues
	 * @param profileTitle
	 */
	private void showLineProfile(float[] xValues, float[] yValues, String profileTitle)
	{
		if (showLineProfiles)
		{
			Frame f = WindowManager.getFrame(profileTitle);
			Plot plot = new Plot(profileTitle, "Distance", "Value", xValues, yValues);
			if (f instanceof PlotWindow)
			{
				PlotWindow p = ((PlotWindow) f);
				p.drawPlot(plot);
			}
			else
			{
				plot.show();
			}

			plotProfiles.add(profileTitle);
		}
	}
}
