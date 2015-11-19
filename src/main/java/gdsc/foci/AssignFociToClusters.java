package gdsc.foci;

/*----------------------------------------------------------------------------- 
 * GDSC Plugins for ImageJ
 * 
 * Copyright (C) 2015 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

import gdsc.clustering.Cluster;
import gdsc.clustering.ClusterPoint;
import gdsc.clustering.ClusteringAlgorithm;
import gdsc.clustering.ClusteringEngine;
import gdsc.clustering.IJTrackProgress;
import gdsc.help.URL;
import gdsc.utils.ImageJHelper;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 * Finds objects in an image using contiguous pixels of the same value. Locates the closest object to each Find Foci
 * result held in memory and summarises the counts.
 */
public class AssignFociToClusters implements PlugIn
{
	public static final String FRAME_TITLE = "Assign Foci To Clusters";

	private static double radius = 100;
	private static ClusteringAlgorithm[] algorithms = new ClusteringAlgorithm[] {
			ClusteringAlgorithm.PARTICLE_SINGLE_LINKAGE, ClusteringAlgorithm.CENTROID_LINKAGE,
			ClusteringAlgorithm.PARTICLE_CENTROID_LINKAGE, ClusteringAlgorithm.PAIRWISE,
			ClusteringAlgorithm.PAIRWISE_WITHOUT_NEIGHBOURS };
	private static String[] names;
	static
	{
		names = new String[algorithms.length];
		for (int i = 0; i < names.length; i++)
			names[i] = algorithms[i].toString();
	}
	private static int algorithm = 0;
	private static boolean showMask = true;
	private boolean myShowMask = false;

	private static TextWindow resultsWindow = null;
	private static TextWindow summaryWindow = null;
	private static int resultId = 1;

	private ImagePlus imp;
	private ArrayList<int[]> results;

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	public void run(String arg)
	{
		results = FindFoci.getResults();
		if (results == null)
		{
			IJ.error(FRAME_TITLE, "Require " + FindFoci.FRAME_TITLE + " results in memory");
			return;
		}
		this.imp = validateInputImage();

		if (!showDialog())
			return;

		doClustering();
	}

	/**
	 * Check if the input image has the same number of non-zero values as the FindFoci results. This means it is a
	 * FindFoci mask for the results.
	 * 
	 * @param imp
	 * @return The image if valid
	 */
	private ImagePlus validateInputImage()
	{
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp == null)
			return null;
		if (imp.getBitDepth() != 8 && imp.getBitDepth() != 16)
			return null;

		// Find all the mask objects using a stack histogram.
		ImageStack stack = imp.getImageStack();
		int[] h = stack.getProcessor(1).getHistogram();
		for (int s = 2; s <= stack.getSize(); s++)
		{
			int[] h2 = stack.getProcessor(1).getHistogram();
			for (int i = 0; i < h.length; i++)
				h[i] += h2[i];
		}

		// Correct mask objects should be numbered sequentially from 1.
		// Find first number that is zero.
		int size = 1;
		while (size < h.length)
		{
			if (h[size] == 0)
				break;
			size++;
		}
		size--; // Decrement to find the last non-zero number 

		// Check the FindFoci results have the same number of objects
		if (size != results.size())
			return null;

		// Check each result matches the image.
		// Image values correspond to the reverse order of the results.
		for (int i = 0, id = results.size(); i < results.size(); i++, id--)
		{
			final int[] result = results.get(i);
			final int x = result[FindFoci.RESULT_X];
			final int y = result[FindFoci.RESULT_Y];
			final int z = result[FindFoci.RESULT_Z];
			try
			{
				final int value = stack.getProcessor(z + 1).get(x, y);
				if (value != id)
				{
					System.out.printf("x%d,y%d,z%d %d != %d\n", x, y, z + 1, value, id);
					return null;
				}
			}
			catch (IllegalArgumentException e)
			{
				// The stack is not the correct size
				System.out.println(e.getMessage());
				return null;
			}
		}

		return imp;
	}

	private boolean showDialog()
	{
		GenericDialog gd = new GenericDialog(FRAME_TITLE);
		gd.addHelp(URL.FIND_FOCI);
		gd.addMessage(ImageJHelper.pleural(results.size(), "result"));

		gd.addSlider("Radius", 5, 200, radius);
		gd.addChoice("Algorithm", names, names[algorithm]);
		if (imp != null)
			gd.addCheckbox("Show_mask", showMask);

		gd.showDialog();
		if (gd.wasCanceled())
			return false;

		radius = Math.abs(gd.getNextNumber());
		algorithm = gd.getNextChoiceIndex();
		if (imp != null)
			myShowMask = showMask = gd.getNextBoolean();

		return true;
	}

	private void doClustering()
	{
		long start = System.currentTimeMillis();
		IJ.showStatus("Clustering ...");
		ClusteringEngine e = new ClusteringEngine(Prefs.getThreads(), algorithms[algorithm], new IJTrackProgress());
		ArrayList<ClusterPoint> points = getPoints();
		ArrayList<Cluster> clusters = e.findClusters(points, radius);
		Collections.sort(clusters);
		IJ.showStatus("Displaying results ...");

		createResultsTables();

		// Show the results
		final String title = (imp != null) ? imp.getTitle() : "Result " + (resultId++);
		StringBuilder sb = new StringBuilder();
		DescriptiveStatistics stats = new DescriptiveStatistics();
		for (int i = 0; i < clusters.size(); i++)
		{
			final Cluster cluster = clusters.get(i);
			sb.append(title).append('\t');
			sb.append(i + 1).append('\t');
			sb.append(ImageJHelper.rounded(cluster.x)).append('\t');
			sb.append(ImageJHelper.rounded(cluster.y)).append('\t');
			sb.append(ImageJHelper.rounded(cluster.n)).append('\t');
			stats.addValue(cluster.n);
			sb.append('\n');
		}
		resultsWindow.append(sb.toString());

		sb.setLength(0);
		sb.append(title).append('\t');
		sb.append(ImageJHelper.rounded(radius)).append('\t');
		sb.append(results.size()).append('\t');
		sb.append(clusters.size()).append('\t');
		sb.append((int) stats.getMin()).append('\t');
		sb.append((int) stats.getMax()).append('\t');
		sb.append(ImageJHelper.rounded(stats.getMean())).append('\t');
		summaryWindow.append(sb.toString());

		if (myShowMask)
		{
			// Create a new mask image colouring the objects from each cluster.
			// Create a map to convert original foci pixels to clusters.
			int[] map = new int[results.size() + 1];
			for (int i = 0; i < clusters.size(); i++)
			{
				for (ClusterPoint p = clusters.get(i).head; p != null; p = p.next)
				{
					map[p.id] = i + 1;
				}
			}

			ImageStack stack = imp.getImageStack();
			ImageStack newStack = new ImageStack(stack.getWidth(), stack.getHeight(), stack.getSize());
			for (int s = 1; s <= stack.getSize(); s++)
			{
				ImageProcessor ip = stack.getProcessor(s);
				ImageProcessor ip2 = ip.createProcessor(ip.getWidth(), ip.getHeight());
				for (int i = 0; i < ip.getPixelCount(); i++)
				{
					if (ip.get(i) != 0)
						ip2.set(i, map[ip.get(i)]);
				}
				newStack.setProcessor(ip2, s);
			}
			ImageJHelper.display(FRAME_TITLE, newStack);
			if (ImageJHelper.isNewWindow())
				IJ.run("Fire");
		}
		double seconds = (System.currentTimeMillis() - start) / 1000.0;
		IJ.showStatus("Done in " + ImageJHelper.rounded(seconds) + " seconds");
	}

	private ArrayList<ClusterPoint> getPoints()
	{
		if (FindFoci.getResults() == null)
			return null;
		ArrayList<ClusterPoint> points = new ArrayList<ClusterPoint>(FindFoci.getResults().size());
		// Image values correspond to the reverse order of the results.
		for (int i = 0, id = results.size(); i < results.size(); i++, id--)
		{
			int[] result = results.get(i);
			points.add(ClusterPoint.newClusterPoint(id, result[FindFoci.RESULT_X], result[FindFoci.RESULT_Y]));
		}
		return points;
	}

	private void createResultsTables()
	{
		resultsWindow = createWindow(resultsWindow, "Results", "Title\tCluster\tcx\tcy\tSize");
		summaryWindow = createWindow(summaryWindow, "Summary", "Title\tRadius\tFoci\tClusters\tMin\tMax\tAv");
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
}
