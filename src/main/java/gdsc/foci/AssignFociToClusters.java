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
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.text.TextWindow;

import java.awt.AWTEvent;
import java.awt.Point;
import java.awt.image.ColorModel;
import java.util.ArrayList;
import java.util.Collections;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

/**
 * Finds objects in an image using contiguous pixels of the same value. Locates the closest object to each Find Foci
 * result held in memory and summarises the counts.
 */
public class AssignFociToClusters implements ExtendedPlugInFilter, DialogListener
{
	public static final String FRAME_TITLE = "Assign Foci To Clusters";

	private static int imageFlags = FINAL_PROCESSING + DOES_8G + DOES_16 + SNAPSHOT;
	private static int noImageFlags = NO_IMAGE_REQUIRED;

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
	private static int algorithm = 1;
	private static boolean showMask = true;
	private boolean myShowMask = false;

	private static TextWindow resultsWindow = null;
	private static TextWindow summaryWindow = null;
	private static int resultId = 1;

	private ImagePlus imp;
	private ArrayList<int[]> results;
	private ArrayList<Cluster> clusters;
	private ColorModel cm;

	public int setup(String arg, ImagePlus imp)
	{
		// TODO - When the preview is cancelled we need to reset the color model on the original image
		
		if (arg.equals("final"))
		{
			if (clusters == null)
				doClustering();
			if (this.imp != null)
			{
				// Reset the preview 
				ImageProcessor ip = this.imp.getProcessor();
				ip.setColorModel(cm);
				ip.reset();
			}
			displayResults();
			return DONE;
		}

		results = FindFoci.getResults();
		if (results == null || results.isEmpty())
		{
			IJ.error(FRAME_TITLE, "Require " + FindFoci.FRAME_TITLE + " results in memory");
			return DONE;
		}

		this.imp = validateInputImage(imp);

		return (this.imp == null) ? noImageFlags : imageFlags;
	}

	/**
	 * Check if the input image has the same number of non-zero values as the FindFoci results. This means it is a
	 * FindFoci mask for the results.
	 * 
	 * @param imp
	 * @return The image if valid
	 */
	private ImagePlus validateInputImage(ImagePlus imp)
	{
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

		// Store this so it can be reset
		cm = imp.getProcessor().getColorModel();

		return imp;
	}

	public void run(ImageProcessor ip)
	{
		doClustering();

		if (imp == null)
		{
			// This occurs when we set the NO_IMAGE_REQUIRED 
			displayResults();			
			return;
		}
		
		// This occurs when we are supporting a preview

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

		// Update the preview processor with the clusters
		for (int i = 0; i < ip.getPixelCount(); i++)
		{
			if (ip.get(i) != 0)
				ip.set(i, map[ip.get(i)]);
		}
		ip.setColorModel(getColorModel());
		ip.setMinAndMax(0, clusters.size());
	}

	/**
	 * Build a custom LUT that helps show the classes
	 * 
	 * @return
	 */
	private LUT getColorModel()
	{
		// TODO - create a colour LUT so that all colours from 1-255 are distinct
		
		byte[] reds = new byte[256];
		byte[] greens = new byte[256];
		byte[] blues = new byte[256];
		int nColors = fire(reds, greens, blues);
		if (nColors < 256)
			interpolate(reds, greens, blues, nColors);
		return new LUT(reds, greens, blues);
	}

	/**
	 * Adapted from ij.plugin.LutLoader to remove the dark colours
	 * 
	 * @param reds
	 * @param greens
	 * @param blues
	 * @return
	 */
	private int fire(byte[] reds, byte[] greens, byte[] blues)
	{
		int[] r = { //0, 0, 1, 25, 49, 
		73, 98, 122, 146, 162, 173, 184, 195, 207, 217, 229, 240, 252, 255, 255, 255, 255, 255, 255, 255, 255, 255,
				255, 255, 255, 255, 255 };
		int[] g = { //0, 0, 0, 0, 0, 
		0, 0, 0, 0, 0, 0, 0, 0, 14, 35, 57, 79, 101, 117, 133, 147, 161, 175, 190, 205, 219, 234, 248, 255, 255, 255,
				255 };
		int[] b = { //0, 61, 96, 130, 165, 
		192, 220, 227, 210, 181, 151, 122, 93, 64, 35, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 35, 98, 160, 223, 255 };
		for (int i = 0; i < r.length; i++)
		{
			reds[i] = (byte) r[i];
			greens[i] = (byte) g[i];
			blues[i] = (byte) b[i];
		}
		return r.length;
	}

	/**
	 * Copied from ij.plugin.LutLoader.
	 * 
	 * @param reds
	 * @param greens
	 * @param blues
	 * @param nColors
	 */
	private void interpolate(byte[] reds, byte[] greens, byte[] blues, int nColors)
	{
		byte[] r = new byte[nColors];
		byte[] g = new byte[nColors];
		byte[] b = new byte[nColors];
		System.arraycopy(reds, 0, r, 0, nColors);
		System.arraycopy(greens, 0, g, 0, nColors);
		System.arraycopy(blues, 0, b, 0, nColors);
		double scale = nColors / 256.0;
		int i1, i2;
		double fraction;
		// Ignore i=0 to ensure a black zero 
		reds[0] = greens[0] = blues[0] = 0;
		for (int i = 1; i < 256; i++)
		{
			i1 = (int) (i * scale);
			i2 = i1 + 1;
			if (i2 == nColors)
				i2 = nColors - 1;
			fraction = i * scale - i1;
			//IJ.write(i+" "+i1+" "+i2+" "+fraction);
			reds[i] = (byte) ((1.0 - fraction) * (r[i1] & 255) + fraction * (r[i2] & 255));
			greens[i] = (byte) ((1.0 - fraction) * (g[i1] & 255) + fraction * (g[i2] & 255));
			blues[i] = (byte) ((1.0 - fraction) * (b[i1] & 255) + fraction * (b[i2] & 255));
		}
	}

	public void setNPasses(int nPasses)
	{
		// Nothing to do
	}

	public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
	{
		GenericDialog gd = new GenericDialog(FRAME_TITLE);
		gd.addHelp(URL.FIND_FOCI);
		gd.addMessage(ImageJHelper.pleural(results.size(), "result"));

		gd.addSlider("Radius", 5, 500, radius);
		gd.addChoice("Algorithm", names, names[algorithm]);
		if (this.imp != null)
		{
			// Allow preview
			gd.addCheckbox("Show_mask", showMask);
			gd.addPreviewCheckbox(pfr);
			gd.addDialogListener(this);
		}
		gd.showDialog();

		if (gd.wasCanceled() || !dialogItemChanged(gd, null))
		{
			return DONE;
		}

		return (this.imp == null) ? noImageFlags : imageFlags;
	}

	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e)
	{
		radius = Math.abs(gd.getNextNumber());
		algorithm = gd.getNextChoiceIndex();
		if (this.imp != null)
			myShowMask = showMask = gd.getNextBoolean();
		return true;
	}

	private double doClustering()
	{
		long start = System.currentTimeMillis();
		IJ.showStatus("Clustering ...");
		ClusteringEngine e = new ClusteringEngine(Prefs.getThreads(), algorithms[algorithm], new IJTrackProgress());
		ArrayList<ClusterPoint> points = getPoints();
		clusters = e.findClusters(points, radius);
		Collections.sort(clusters);
		Collections.reverse(clusters);
		double seconds = (System.currentTimeMillis() - start) / 1000.0;
		IJ.showStatus(ImageJHelper.pleural(clusters.size(), "cluster") + " in " + ImageJHelper.rounded(seconds) +
				" seconds");
		return seconds;
	}

	private void displayResults()
	{
		if (clusters == null)
			return;

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

			// Auto-width adjustment is only performed when number of rows is less than 10
			// so do this before it won't work
			if (i == 9 && resultsWindow.getTextPanel().getLineCount() < 10)
			{
				resultsWindow.append(sb.toString());
				sb.setLength(0);
			}
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

			// Set a color table if this is a new image
			ImagePlus clusterImp = WindowManager.getImage(FRAME_TITLE);
			if (clusterImp == null)
				newStack.setColorModel(getColorModel());

			ImageJHelper.display(FRAME_TITLE, newStack);
		}

		IJ.showStatus("");
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
