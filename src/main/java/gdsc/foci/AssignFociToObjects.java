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

import gdsc.help.URL;
import gdsc.utils.ImageJHelper;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.plugin.filter.PlugInFilter;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.text.TextWindow;

import java.awt.Color;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 * Finds spots in an image. Locates the closest neighbour spot within a radius
 * and produces a line profile through the spots. If no neighbour is present
 * produces a line profile through the principle axis.
 */
public class AssignFociToObjects implements PlugInFilter
{
	public static final String FRAME_TITLE = "Assign Foci";

	private static final int flags = DOES_16 + DOES_8G + NO_CHANGES + NO_UNDO;
	private static double radius = 30;
	private static boolean showObjects = false;
	private static boolean showFoci = false;

	private static TextWindow resultsWindow = null;
	private static TextWindow summaryWindow = null;

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
	public int setup(String arg, ImagePlus imp)
	{
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

		createDistanceGrid(radius);

		createResultsTables();

		ObjectAnalyzer oa = new ObjectAnalyzer(ip);
		int[] objectMask = oa.getObjectMask();

		final int maxx = ip.getWidth();
		final int maxy = ip.getHeight();

		final double maxD2 = radius * radius;

		// Assign each foci to the nearest object
		int[] count = new int[oa.getMaxObject() + 1];
		boolean[] found = new boolean[results.size()];
		for (int i = 0; i < results.size(); i++)
		{
			final int[] result = results.get(i);
			final int x = result[FindFoci.RESULT_X];
			final int y = result[FindFoci.RESULT_Y];
			// Check within the image
			if (x < 0 || x >= maxx || y < 0 || y >= maxy)
				continue;

			int index = y * maxx + x;
			if (objectMask[index] != 0)
			{
				count[objectMask[index]]++;
				found[i] = true;
				continue;
			}

			// Search for the closest object(s)
			int[] closestCount = new int[count.length];

			// Scan wider and wider from 0,0 until we find an object.
			for (Search[] next : search)
			{
				if (next[0].d2 > maxD2)
					break;
				boolean ok = false;
				for (Search s : next)
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
					found[i] = true;
					break;
				}
			}
		}

		showMask(oa, found);

		double[][] centres = oa.getObjectCentres();

		// Show the results
		DescriptiveStatistics stats = new DescriptiveStatistics();
		StringBuilder sb = new StringBuilder();
		for (int i = 1; i < count.length; i++)
		{
			sb.append(imp.getTitle());
			sb.append('\t').append(i);
			sb.append('\t').append(ImageJHelper.rounded(centres[i][0]));
			sb.append('\t').append(ImageJHelper.rounded(centres[i][1]));
			sb.append('\t').append((int) (centres[i][2]));
			sb.append('\t').append(count[i]);
			sb.append('\n');
			stats.addValue(count[i]);
		}
		resultsWindow.append(sb.toString());
		// Show the summary
		sb.setLength(0);
		sb.append(imp.getTitle());
		sb.append('\t').append(oa.getMaxObject());
		sb.append('\t').append(results.size());
		sb.append('\t').append((int) stats.getSum());
		sb.append('\t').append(results.size() - (int) stats.getSum());
		sb.append('\t').append(stats.getMin());
		sb.append('\t').append(stats.getMax());
		sb.append('\t').append(ImageJHelper.rounded(stats.getMean()));
		sb.append('\t').append(ImageJHelper.rounded(stats.getPercentile(50)));
		sb.append('\t').append(ImageJHelper.rounded(stats.getStandardDeviation()));
		summaryWindow.append(sb.toString());
	}

	public boolean showDialog()
	{
		results = FindFoci.getResults();
		if (results == null)
		{
			IJ.error(FRAME_TITLE, "No " + FindFoci.FRAME_TITLE + " results in memory");
			return false;
		}

		GenericDialog gd = new GenericDialog(FRAME_TITLE);
		gd.addHelp(URL.FIND_FOCI);

		gd.addMessage("Assign Find Foci " + ImageJHelper.pleural(results.size(), "result") +
				" to the nearest object\n(Objects will be found in the current image)");

		gd.addSlider("Radius", 5, 50, radius);
		gd.addCheckbox("Show_objects", showObjects);
		gd.addCheckbox("Show_foci", showFoci);

		gd.showDialog();
		if (gd.wasCanceled())
			return false;

		radius = Math.abs(gd.getNextNumber());
		showObjects = gd.getNextBoolean();
		showFoci = gd.getNextBoolean();

		return true;
	}

	private static void createDistanceGrid(double radius)
	{
		// Auto-generated method stub
		int n = (int) Math.ceil(radius);
		int newSize = 2 * n + 1;
		if (size >= newSize)
			return;
		size = newSize;
		search = new ArrayList<Search[]>();
		AssignFociToObjects instance = new AssignFociToObjects();
		Search[] s = new Search[size * size - 1];

		double[] tmp = new double[newSize];
		for (int y = -n, i = 0; y <= n; y++, i++)
			tmp[i] = y * y;

		for (int y = -n, i = 0, index = 0; y <= n; y++, i++)
		{
			final double y2 = tmp[i];
			for (int x = -n, ii = 0; x <= n; x++, ii++)
			{
				if (x == 0 && y == 0)
					continue;
				s[index++] = instance.new Search(x, y, y2 + tmp[ii]);
			}
		}

		Arrays.sort(s);

		// Store each increasing search distance as a set
		double last = -1;
		int begin = 0, end = -1;
		for (int i = 0; i < s.length; i++)
		{
			if (last != s[i].d2)
			{
				if (end != -1)
				{
					int length = end - begin + 1;
					Search[] next = new Search[length];
					System.arraycopy(s, begin, next, 0, length);
					search.add(next);
				}
				begin = i;
			}
			end = i;
			last = s[i].d2;
		}

		int length = end - begin + 1;
		Search[] next = new Search[length];
		System.arraycopy(s, begin, next, 0, length);
		search.add(next);
	}

	private void createResultsTables()
	{
		resultsWindow = createWindow(resultsWindow, "Results", "Image\tObject\tcx\tcy\tSize\tCount");
		summaryWindow = createWindow(summaryWindow, "Summary", "Image\tnObjects\tnFoci\tIn\tOut\tMin\tMax\tAv\tMed\tSD");
		Point p1 = resultsWindow.getLocation();
		Point p2 = summaryWindow.getLocation();
		if (p1.x == p2.x && p1.y == p2.y)
		{
			p2.y += resultsWindow.getHeight();
			summaryWindow.setLocation(p2);
		}
	}

	private TextWindow createWindow(TextWindow window, String title, String header)
	{
		if (window == null || !window.isVisible())
			window = new TextWindow(FRAME_TITLE + " " + title, header, "", 800, 400);
		return window;
	}

	private void showMask(ObjectAnalyzer oa, boolean[] found)
	{
		if (!showObjects)
			return;

		int[] objectMask = oa.getObjectMask();
		ImageProcessor ip;
		if (oa.getMaxObject() <= 255)
			ip = new ByteProcessor(oa.getWidth(), oa.getHeight());
		else
			ip = new ShortProcessor(oa.getWidth(), oa.getHeight());
		for (int i = 0; i < objectMask.length; i++)
			ip.set(i, objectMask[i]);

		ip.setMinAndMax(0, oa.getMaxObject());
		ImagePlus imp = ImageJHelper.display(FRAME_TITLE + " Objects", ip);
		if (showFoci && found.length > 0)
		{
			int[] xin = new int[found.length];
			int[] yin = new int[found.length];
			int[] xout = new int[found.length];
			int[] yout = new int[found.length];
			int in = 0, out = 0;
			for (int i = 0; i < found.length; i++)
			{
				final int[] xy = results.get(i);
				if (found[i])
				{
					xin[in] = xy[FindFoci.RESULT_X];
					yin[in++] = xy[FindFoci.RESULT_Y];
				}
				else
				{
					xout[out] = xy[FindFoci.RESULT_X];
					yout[out++] = xy[FindFoci.RESULT_Y];
				}
			}

			Overlay o = new Overlay();
			PointRoi roi = new PointRoi(xin, yin, in);
			roi.setHideLabels(true);
			roi.setFillColor(Color.GREEN);
			roi.setStrokeColor(Color.GREEN);
			o.add(roi);
			roi = new PointRoi(xout, yout, out);
			roi.setHideLabels(true);
			roi.setFillColor(Color.RED);
			roi.setStrokeColor(Color.RED);
			o.add(roi);
			imp.setOverlay(o);
		}
	}
}
