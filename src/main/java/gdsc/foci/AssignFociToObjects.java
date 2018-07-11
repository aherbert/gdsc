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
package gdsc.foci;

import java.awt.Color;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import gdsc.UsageTracker;
import gdsc.core.ij.Utils;
import gdsc.core.utils.TextUtils;
import gdsc.help.URL;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.PointRoi;
import ij.plugin.filter.PlugInFilter;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.text.TextWindow;

/**
 * Finds objects in an image using contiguous pixels of the same value. Locates the closest object to each Find Foci
 * result held in memory and summarises the counts.
 */
public class AssignFociToObjects implements PlugInFilter
{
	/** The title of the plugin. */
	private static final String TITLE = "Assign Foci";

	private static final int flags = DOES_16 + DOES_8G + NO_CHANGES + NO_UNDO;
	private static String input = "";
	private static double radius = 30;
	private static int minSize = 0;
	private static int maxSize = 0;
	private static boolean eightConnected = false;
	private static boolean removeSmallObjects = true;
	private static boolean showObjects = false;
	private static boolean showFoci = false;
	private static boolean showDistances = false;
	private static boolean showHistogram = false;

	private static TextWindow resultsWindow = null;
	private static TextWindow summaryWindow = null;
	private static TextWindow distancesWindow = null;

	private ImagePlus imp;
	private ArrayList<int[]> results;

	private static int size = 0;
	private static ArrayList<Search[]> search = null;

	private class Search implements Comparable<Search>
	{
		int x, y;
		double d2;

		public Search(int x, int y, double d2)
		{
			this.x = x;
			this.y = y;
			this.d2 = d2;
		}

		@Override
		public int compareTo(Search that)
		{
			if (this.d2 < that.d2)
				return -1;
			if (this.d2 > that.d2)
				return 1;
			return 0;
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	@Override
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
	@Override
	public void run(ImageProcessor ip)
	{
		if (!showDialog())
			return;

		createDistanceGrid(radius);

		createResultsTables();

		final ObjectAnalyzer oa = new ObjectAnalyzer(ip);
		if (removeSmallObjects)
			oa.setMinObjectSize(minSize);
		oa.setEightConnected(eightConnected);
		final int[] objectMask = oa.getObjectMask();

		final int maxx = ip.getWidth();
		final int maxy = ip.getHeight();

		final double maxD2 = radius * radius;

		// Assign each foci to the nearest object
		final int[] count = new int[oa.getMaxObject() + 1];
		final int[] found = new int[results.size()];
		final double[] d2 = new double[found.length];
		Arrays.fill(d2, -1);
		for (int i = 0; i < results.size(); i++)
		{
			final int[] result = results.get(i);
			final int x = result[0];
			final int y = result[1];
			// Check within the image
			if (x < 0 || x >= maxx || y < 0 || y >= maxy)
				continue;

			int index = y * maxx + x;
			if (objectMask[index] != 0)
			{
				count[objectMask[index]]++;
				found[i] = objectMask[index];
				d2[i] = 0;
				continue;
			}

			// Search for the closest object(s)
			final int[] closestCount = new int[count.length];

			// Scan wider and wider from 0,0 until we find an object.
			for (final Search[] next : search)
			{
				if (next[0].d2 > maxD2)
					break;
				boolean ok = false;
				for (final Search s : next)
				{
					final int xx = x + s.x;
					final int yy = y + s.y;
					if (xx < 0 || xx >= maxx || yy < 0 || yy >= maxy)
						continue;
					index = yy * maxx + xx;
					if (objectMask[index] != 0)
					{
						ok = true;
						closestCount[objectMask[index]]++;
					}
				}
				if (ok)
				{
					// Get the object with the highest count
					int maxCount = 0;
					for (int j = 1; j < closestCount.length; j++)
						if (closestCount[maxCount] < closestCount[j])
							maxCount = j;
					// Assign
					count[maxCount]++;
					found[i] = maxCount;
					d2[i] = next[0].d2;
					break;
				}
			}
		}

		final double[][] centres = oa.getObjectCentres();

		// We must ignore those that are too small/big
		final int[] idMap = new int[count.length];
		for (int i = 1; i < count.length; i++)
		{
			idMap[i] = i;
			if (centres[i][2] < minSize || (maxSize != 0 && centres[i][2] > maxSize))
				idMap[i] = -i;
		}

		// TODO - Remove objects from the output image ?
		showMask(oa, found, idMap);

		// Show the results
		final DescriptiveStatistics stats = new DescriptiveStatistics();
		final DescriptiveStatistics stats2 = new DescriptiveStatistics();
		final StringBuilder sb = new StringBuilder();
		for (int i = 1, j = 0; i < count.length; i++)
		{
			sb.append(imp.getTitle());
			sb.append('\t').append(i);
			sb.append('\t').append(Utils.rounded(centres[i][0]));
			sb.append('\t').append(Utils.rounded(centres[i][1]));
			sb.append('\t').append((int) (centres[i][2]));
			if (idMap[i] > 0)
			{
				// Include this object
				sb.append("\tTrue");
				stats.addValue(count[i]);
			}
			else
			{
				// Exclude this object
				sb.append("\tFalse");
				stats2.addValue(count[i]);
			}
			sb.append('\t').append(count[i]);
			sb.append('\n');

			// Flush before 10 lines to ensure auto-layout of columns
			if (i >= 9 && j++ == 0)
			{
				resultsWindow.append(sb.toString());
				sb.setLength(0);
			}
		}
		resultsWindow.append(sb.toString());

		// Histogram the count
		if (showHistogram)
		{
			final int max = (int) stats.getMax();
			final double[] xValues = new double[max + 1];
			final double[] yValues = new double[xValues.length];
			for (int i = 1; i < count.length; i++)
				if (idMap[i] > 0)
					yValues[count[i]]++;
			double yMax = 0;
			for (int i = 0; i <= max; i++)
			{
				xValues[i] = i;
				if (yMax < yValues[i])
					yMax = yValues[i];
			}
			final String title = TITLE + " Histogram";
			final Plot plot = new Plot(title, "Count", "Frequency", xValues, yValues);
			plot.setLimits(0, xValues[xValues.length - 1], 0, yMax);
			plot.addLabel(0, 0,
					String.format("N = %d, Mean = %s", (int) stats.getSum(), Utils.rounded(stats.getMean())));
			plot.draw();
			plot.setColor(Color.RED);
			plot.drawLine(stats.getMean(), 0, stats.getMean(), yMax);
			Utils.display(title, plot);
		}

		// Show the summary
		sb.setLength(0);
		sb.append(imp.getTitle());
		sb.append('\t').append(oa.getMaxObject());
		sb.append('\t').append(stats.getN());
		sb.append('\t').append(results.size());
		sb.append('\t').append((int) stats.getSum());
		sb.append('\t').append((int) stats2.getSum());
		sb.append('\t').append(results.size() - (int) (stats.getSum() + stats2.getSum()));
		sb.append('\t').append(stats.getMin());
		sb.append('\t').append(stats.getMax());
		sb.append('\t').append(Utils.rounded(stats.getMean()));
		sb.append('\t').append(Utils.rounded(stats.getPercentile(50)));
		sb.append('\t').append(Utils.rounded(stats.getStandardDeviation()));
		summaryWindow.append(sb.toString());

		if (!showDistances)
			return;

		sb.setLength(0);
		for (int i = 0, j = 0; i < results.size(); i++)
		{
			final int[] result = results.get(i);
			final int x = result[0];
			final int y = result[1];

			// Check within the image
			if (x < 0 || x >= maxx || y < 0 || y >= maxy)
				continue;

			sb.append(imp.getTitle());
			sb.append('\t').append(i + 1);
			sb.append('\t').append(x);
			sb.append('\t').append(y);
			if (found[i] > 0)
			{
				sb.append('\t').append(found[i]);
				if (idMap[found[i]] > 0)
					sb.append("\tTrue\t");
				else
					sb.append("\tFalse\t");
				sb.append(Utils.rounded(Math.sqrt(d2[i])));
				sb.append('\n');
			}
			else
				sb.append("\t\t\t\n");

			// Flush before 10 lines to ensure auto-layout of columns
			if (i >= 9 && j++ == 0)
			{
				distancesWindow.append(sb.toString());
				sb.setLength(0);
			}
		}
		distancesWindow.append(sb.toString());
	}

	private boolean showDialog()
	{
		final ArrayList<int[]> findFociResults = getFindFociResults();
		final ArrayList<int[]> roiResults = getRoiResults();
		if (findFociResults == null && roiResults == null)
		{
			IJ.error(TITLE, "No " + FindFoci.TITLE + " results in memory or point ROI on the image");
			return false;
		}

		final GenericDialog gd = new GenericDialog(TITLE);
		gd.addHelp(URL.FIND_FOCI);

		String[] options = new String[2];
		int count = 0;

		String msg = "Assign foci to the nearest object\n(Objects will be found in the current image)\nAvailable foci:";
		if (findFociResults != null)
		{
			msg += "\nFind Foci = " + TextUtils.pleural(findFociResults.size(), "result");
			options[count++] = "Find Foci";
		}
		if (roiResults != null)
		{
			msg += "\nROI = " + TextUtils.pleural(roiResults.size(), "point");
			options[count++] = "ROI";
		}
		options = Arrays.copyOf(options, count);

		gd.addMessage(msg);

		gd.addChoice("Foci", options, input);
		gd.addSlider("Radius", 5, 50, radius);
		gd.addNumericField("Min_size", minSize, 0);
		gd.addNumericField("Max_size", maxSize, 0);
		gd.addCheckbox("Eight_connected", eightConnected);
		gd.addCheckbox("Remove_small_objects", removeSmallObjects);
		gd.addCheckbox("Show_objects", showObjects);
		gd.addCheckbox("Show_foci", showFoci);
		gd.addCheckbox("Show_distances", showDistances);
		gd.addCheckbox("Show_histogram", showHistogram);

		gd.showDialog();
		if (gd.wasCanceled())
			return false;

		input = gd.getNextChoice();
		radius = Math.abs(gd.getNextNumber());
		minSize = Math.abs((int) gd.getNextNumber());
		maxSize = Math.abs((int) gd.getNextNumber());
		eightConnected = gd.getNextBoolean();
		removeSmallObjects = gd.getNextBoolean();
		showObjects = gd.getNextBoolean();
		showFoci = gd.getNextBoolean();
		showDistances = gd.getNextBoolean();
		showHistogram = gd.getNextBoolean();

		// Load objects
		results = (input.equalsIgnoreCase("ROI")) ? roiResults : findFociResults;
		if (results == null)
		{
			IJ.error(TITLE, "No foci could be loaded");
			return false;
		}

		return true;
	}

	private static ArrayList<int[]> getFindFociResults()
	{
		if (FindFoci.getResults() == null)
			return null;
		final ArrayList<int[]> results = new ArrayList<>(FindFoci.getResults().size());
		for (final FindFociResult result : FindFoci.getResults())
			results.add(new int[] { result.x, result.y });
		return results;
	}

	private ArrayList<int[]> getRoiResults()
	{
		final AssignedPoint[] points = PointManager.extractRoiPoints(imp.getRoi());
		if (points.length == 0)
			return null;
		final ArrayList<int[]> results = new ArrayList<>(points.length);
		for (final AssignedPoint point : points)
			results.add(new int[] { point.x, point.y });
		return results;
	}

	private static void createDistanceGrid(double radius)
	{
		final int n = (int) Math.ceil(radius);
		final int newSize = 2 * n + 1;
		if (size >= newSize)
		{
			//return;
		}
		size = newSize;
		search = new ArrayList<>();
		final AssignFociToObjects instance = new AssignFociToObjects();
		final Search[] s = new Search[size * size];

		final double[] tmp = new double[newSize];
		for (int y = -n, i = 0; y <= n; y++, i++)
			tmp[i] = y * y;

		for (int y = -n, i = 0, index = 0; y <= n; y++, i++)
		{
			final double y2 = tmp[i];
			for (int x = -n, ii = 0; x <= n; x++, ii++)
				s[index++] = instance.new Search(x, y, y2 + tmp[ii]);
		}

		Arrays.sort(s);

		// Store each increasing search distance as a set
		// Ignore first record as it is d2==0
		double last = -1;
		int begin = 0, end = -1;
		for (int i = 1; i < s.length; i++)
		{
			if (last != s[i].d2)
			{
				if (end != -1)
				{
					final int length = end - begin + 1;
					final Search[] next = new Search[length];
					System.arraycopy(s, begin, next, 0, length);
					search.add(next);
				}
				begin = i;
			}
			end = i;
			last = s[i].d2;
		}

		if (end != -1)
		{
			final int length = end - begin + 1;
			final Search[] next = new Search[length];
			System.arraycopy(s, begin, next, 0, length);
			search.add(next);
		}
	}

	private static void createResultsTables()
	{
		resultsWindow = createWindow(resultsWindow, "Results", "Image\tObject\tcx\tcy\tSize\tValid\tCount");
		summaryWindow = createWindow(summaryWindow, "Summary",
				"Image\tnObjects\tValid\tnFoci\tIn\tIgnored\tOut\tMin\tMax\tAv\tMed\tSD");
		final Point p1 = resultsWindow.getLocation();
		final Point p2 = summaryWindow.getLocation();
		if (p1.x == p2.x && p1.y == p2.y)
		{
			p2.y += resultsWindow.getHeight();
			summaryWindow.setLocation(p2);
		}
		if (showDistances)
		{
			distancesWindow = createWindow(distancesWindow, "Distances", "Image\tFoci\tx\ty\tObject\tValid\tDistance");
			final Point p3 = distancesWindow.getLocation();
			if (p1.x == p3.x && p1.y == p3.y)
			{
				p3.x += 50;
				p3.y += 50;
				distancesWindow.setLocation(p3);
			}
		}
	}

	private static TextWindow createWindow(TextWindow window, String title, String header)
	{
		if (window == null || !window.isVisible())
			window = new TextWindow(TITLE + " " + title, header, "", 800, 400);
		return window;
	}

	private void showMask(ObjectAnalyzer oa, int[] found, int[] idMap)
	{
		if (!showObjects)
			return;

		final int[] objectMask = oa.getObjectMask();
		ImageProcessor ip;
		if (oa.getMaxObject() <= 255)
			ip = new ByteProcessor(oa.getWidth(), oa.getHeight());
		else
			ip = new ShortProcessor(oa.getWidth(), oa.getHeight());
		for (int i = 0; i < objectMask.length; i++)
			ip.set(i, objectMask[i]);

		ip.setMinAndMax(0, oa.getMaxObject());
		final ImagePlus imp = Utils.display(TITLE + " Objects", ip);
		if (showFoci && found.length > 0)
		{
			final int[] xin = new int[found.length];
			final int[] yin = new int[found.length];
			final int[] xremove = new int[found.length];
			final int[] yremove = new int[found.length];
			final int[] xout = new int[found.length];
			final int[] yout = new int[found.length];
			int in = 0, remove = 0, out = 0;
			for (int i = 0; i < found.length; i++)
			{
				final int[] xy = results.get(i);
				final int id = idMap[found[i]];
				if (id > 0)
				{
					xin[in] = xy[0];
					yin[in++] = xy[1];
				}
				else if (id < 0)
				{
					xremove[remove] = xy[0];
					yremove[remove++] = xy[1];
				}
				else
				{
					xout[out] = xy[0];
					yout[out++] = xy[1];
				}
			}

			final Overlay o = new Overlay();
			addRoi(xin, yin, in, o, Color.GREEN);
			addRoi(xremove, yremove, remove, o, Color.YELLOW);
			addRoi(xout, yout, out, o, Color.RED);
			imp.setOverlay(o);
		}
	}

	private static void addRoi(int[] x, int[] y, int n, Overlay o, Color color)
	{
		final PointRoi roi = new PointRoi(x, y, n);
		roi.setShowLabels(false);
		roi.setFillColor(color);
		roi.setStrokeColor(color);
		o.add(roi);
	}
}
