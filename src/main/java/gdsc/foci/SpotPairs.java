package gdsc.foci;

import gdsc.ImageJTracker;

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
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

import java.awt.AWTEvent;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Analyses marked ROI points in an image. Find the closest pairs within a set distance of each other.
 */
public class SpotPairs implements ExtendedPlugInFilter, DialogListener
{
	/**
	 * Used to store information about a cluster in the clustering analysis
	 */
	public class Cluster
	{
		public double x, y, sumx, sumy;
		public int n;

		// Used to construct a single linked list of clusters
		public Cluster next = null;

		// Used to store potential clustering links
		public Cluster closest = null;
		public double d2;

		// Used to construct a single linked list of cluster points
		public ClusterPoint head = null;

		public Cluster(ClusterPoint point)
		{
			point.next = null;
			head = point;
			this.x = sumx = point.x;
			this.y = sumy = point.y;
			n = 1;
		}

		public double distance2(Cluster other)
		{
			final double dx = x - other.x;
			final double dy = y - other.y;
			return dx * dx + dy * dy;
		}

		public void add(Cluster other)
		{
			// Do not check if the other cluster is null or has no points

			// Add to this list
			// Find the tail of the shortest list
			ClusterPoint big, small;
			if (n < other.n)
			{
				small = head;
				big = other.head;
			}
			else
			{
				small = other.head;
				big = head;
			}

			ClusterPoint tail = small;
			while (tail.next != null)
				tail = tail.next;

			// Join the small list to the long list 
			tail.next = big;
			head = small;

			// Find the new centroid
			sumx += other.sumx;
			sumy += other.sumy;
			n += other.n;
			x = sumx / n;
			y = sumy / n;

			// Free the other cluster
			other.clear();
		}

		private void clear()
		{
			head = null;
			closest = null;
			n = 0;
			x = y = sumx = sumy = d2 = 0;
		}

		/**
		 * Link the two clusters as potential merge candidates only if the squared distance is smaller than the other
		 * clusters current closest
		 * 
		 * @param other
		 * @param d2
		 */
		public void link(Cluster other, double d2)
		{
			// Check if the other cluster has a closer candidate
			if (other.closest != null && other.d2 < d2)
				return;

			other.closest = this;
			other.d2 = d2;

			this.closest = other;
			this.d2 = d2;
		}

		/**
		 * @return True if the closest cluster links back to this cluster
		 */
		public boolean validLink()
		{
			// Check if the other cluster has an updated link to another candidate
			if (closest != null)
			{
				// Valid if the other cluster links back to this cluster
				return closest.closest == this;
			}
			return false;
		}

		/**
		 * Sorts the points in ID order. This only works for the first two points in the list. 
		 */
		public void sort()
		{
			if (n < 2)
				return;
			ClusterPoint p1 = head;
			ClusterPoint p2 = p1.next;
			if (p2.id < p1.id)
			{
				head = p2;
				p1.next = p2.next;
				p2.next = p1;
			}			
		}
	}

	/**
	 * Used to store information about a point in the clustering analysis
	 */
	public class ClusterPoint
	{
		public double x, y;
		public int id;

		// Used to construct a single linked list of points
		public ClusterPoint next = null;

		public ClusterPoint(int id, double x, double y)
		{
			this.id = id;
			this.x = x;
			this.y = y;
		}

		public double distance(ClusterPoint other)
		{
			final double dx = x - other.x;
			final double dy = y - other.y;
			return Math.sqrt(dx * dx + dy * dy);
		}
	}

	public static final String TITLE = "Spot Pairs";
	private static TextWindow resultsWindow = null;

	private static double radius = 10;
	private static boolean addOverlay = true;
	private static boolean killRoi = false;

	// Cache the ROI when we remove it so it can be reused
	private static ImagePlus lastImp = null;
	private static Roi lastRoi = null;

	private Calibration cal;
	private AssignedPoint[] points;
	private ArrayList<Cluster> candidates;
	private boolean addedOverlay = false;

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	public int setup(String arg, ImagePlus imp)
	{
		ImageJTracker.recordPlugin(this.getClass(), arg);
		
		if (imp == null)
			return DONE;
		Roi roi = imp.getRoi();

		// If there is no ROI it may be because we removed it last time
		if (roi == null && lastImp == imp)
		{
			// Re-use the saved ROI from last time  
			roi = lastRoi;
			imp.setRoi(roi);
		}

		points = PointManager.extractRoiPoints(roi);
		if (points.length < 2)
		{
			IJ.error(TITLE, "Please mark at least two ROI points on the image");
			return DONE;
		}

		lastRoi = roi;
		lastImp = imp;

		cal = imp.getCalibration();

		if (arg.equals("final"))
		{
			for (Cluster c : candidates)
				c.sort();
			
			Collections.sort(candidates, new Comparator<Cluster>()
			{
				public int compare(Cluster o1, Cluster o2)
				{
					// Put the pairs first
					if (o1.n > o2.n)
						return -1;
					if (o1.n < o2.n)
						return 1;
					
					// Sort by the first point ID
					if (o1.head.id < o2.head.id)
						return -1;
					if (o1.head.id > o2.head.id)
						return 1;
					return 0;
				}
			});

			// Show the results in a table
			createResultsWindow();

			// Report the results
			resultsWindow.append(imp.getTitle());
			for (Cluster c : candidates)
			{
				addResult(c);
			}

			// The final processing mode of ImageJ restores the image ROI so kill it again
			if (killRoi)
				lastImp.killRoi();
		}

		return DOES_ALL | FINAL_PROCESSING;
	}

	private void createResultsWindow()
	{
		if (resultsWindow == null || !resultsWindow.isShowing())
		{
			resultsWindow = new TextWindow(TITLE, createResultsHeader(), "", 600, 500);
		}
	}

	private String createResultsHeader()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Image\t");
		sb.append("Id1\t");
		sb.append("X1\t");
		sb.append("Y1\t");
		sb.append("Id2\t");
		sb.append("X2\t");
		sb.append("Y2\t");
		sb.append("Distance\t");
		sb.append("Distance (").append(cal.getXUnit()).append(")");
		return sb.toString();
	}

	private void addResult(Cluster c)
	{
		ClusterPoint p1 = c.head;
		if (c.n == 1)
		{
			resultsWindow.append(String.format("\t%d\t%.0f\t%.0f", p1.id, p1.x, p1.y));
		}
		else
		{
			ClusterPoint p2 = p1.next;
			final double d = p1.distance(p2);
			resultsWindow.append(String.format("\t%d\t%.0f\t%.0f\t%d\t%.0f\t%.0f\t%s\t%s\n", p1.id, p1.x, p1.y, p2.id,
					p2.x, p2.y, ImageJHelper.rounded(d), ImageJHelper.rounded(d * cal.pixelWidth)));
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	public void run(ImageProcessor ip)
	{
		candidates = findPairs();

		if (addOverlay)
		{
			Overlay overlay = new Overlay();
			// Add the original points
			Roi pointRoi = PointManager.createROI(points);
			pointRoi.setStrokeColor(Color.orange);
			pointRoi.setFillColor(Color.white);
			overlay.add(pointRoi);
			for (Cluster c : candidates)
			{
				if (c.n == 2)
				{
					// Draw a line between pairs
					ClusterPoint p1 = c.head;
					ClusterPoint p2 = p1.next;
					Line line = new Line(p1.x, p1.y, p2.x, p2.y);
					line.setStrokeColor(Color.magenta);
					line.setStrokeWidth(2);
					overlay.add(line);
				}
			}
			lastImp.setOverlay(overlay);
			lastImp.updateAndDraw();
			addedOverlay = true;

			// Remove the point ROI to allow the overlay to be seen 
			if (killRoi)
			{
				lastImp.killRoi(); // Saves the ROI so it can be restored
			}
		}
		else
		{
			lastImp.setOverlay(null);
		}
	}

	private ArrayList<Cluster> findPairs()
	{
		// NOTE:
		// This code has been adapted from the GDSC SMLM plugins from the PCPALMClusters class.
		// This was the fastest way of implementing this.

		ArrayList<Cluster> candidates = new ArrayList<Cluster>(points.length);
		for (AssignedPoint p : points)
		{
			final Cluster c = new Cluster(new ClusterPoint(p.id + 1, p.x, p.y));
			candidates.add(c);
		}

		// Find the bounds of the candidates
		double minx = candidates.get(0).x;
		double miny = candidates.get(0).y;
		double maxx = minx, maxy = miny;
		for (Cluster c : candidates)
		{
			if (minx > c.x)
				minx = c.x;
			else if (maxx < c.x)
				maxx = c.x;
			if (miny > c.y)
				miny = c.y;
			else if (maxy < c.y)
				maxy = c.y;
		}

		// Assign to a grid
		final int maxBins = 500;
		final double xBinWidth = Math.max(radius, (maxx - minx) / maxBins);
		final double yBinWidth = Math.max(radius, (maxy - miny) / maxBins);
		final int nXBins = 1 + (int) ((maxx - minx) / xBinWidth);
		final int nYBins = 1 + (int) ((maxy - miny) / yBinWidth);
		Cluster[][] grid = new Cluster[nXBins][nYBins];
		for (Cluster c : candidates)
		{
			final int xBin = (int) ((c.x - minx) / xBinWidth);
			final int yBin = (int) ((c.y - miny) / yBinWidth);
			// Build a single linked list
			c.next = grid[xBin][yBin];
			grid[xBin][yBin] = c;
		}

		final double r2 = radius * radius;

		// Sweep the all-vs-all clusters and make potential links between clusters.
		// If a link can be made to a closer cluster then break the link and rejoin.
		// Then join all the links into clusters.
		final int maximumPairingSteps = 1;
		int i = 0;
		while (findLinks(grid, nXBins, nYBins, r2))
		{
			joinLinks(grid, nXBins, nYBins, candidates);

			if (++i >= maximumPairingSteps)
				break;

			// Reassign the grid
			for (Cluster c : candidates)
			{
				final int xBin = (int) ((c.x - minx) / xBinWidth);
				final int yBin = (int) ((c.y - miny) / yBinWidth);
				// Build a single linked list
				c.next = grid[xBin][yBin];
				grid[xBin][yBin] = c;
			}
		}
		return candidates;
	}

	/**
	 * Search for potential links between clusters that are below the squared radius distance. Store if the clusters
	 * have any neighbours within 2*r^2.
	 * 
	 * @param grid
	 * @param nXBins
	 * @param nYBins
	 * @param r2
	 *            The squared radius distance
	 * @return True if any links were made
	 */
	private boolean findLinks(Cluster[][] grid, final int nXBins, final int nYBins, final double r2)
	{
		Cluster[] neighbours = new Cluster[5];
		boolean linked = false;
		for (int yBin = 0; yBin < nYBins; yBin++)
		{
			for (int xBin = 0; xBin < nXBins; xBin++)
			{
				for (Cluster c1 = grid[xBin][yBin]; c1 != null; c1 = c1.next)
				{
					// Build a list of which cells to compare up to a maximum of 5
					//      | 0,0  |  1,0
					// ------------+-----
					// -1,1 | 0,1  |  1,1

					int count = 0;
					neighbours[count++] = c1.next;

					if (yBin < nYBins - 1)
					{
						neighbours[count++] = grid[xBin][yBin + 1];
						if (xBin > 0)
							neighbours[count++] = grid[xBin - 1][yBin + 1];
					}
					if (xBin < nXBins - 1)
					{
						neighbours[count++] = grid[xBin + 1][yBin];
						if (yBin < nYBins - 1)
							neighbours[count++] = grid[xBin + 1][yBin + 1];
					}

					// Compare to neighbours and find the closest.
					// Use either the radius threshold or the current closest distance
					// which may have been set by an earlier comparison.
					double min = (c1.closest == null) ? r2 : c1.d2;
					Cluster other = null;
					while (count-- > 0)
					{
						for (Cluster c2 = neighbours[count]; c2 != null; c2 = c2.next)
						{
							final double d2 = c1.distance2(c2);
							if (d2 < min)
							{
								min = d2;
								other = c2;
							}
						}
					}

					if (other != null)
					{
						// Store the potential link between the two clusters
						c1.link(other, min);
						linked = true;
					}
				}
			}
		}
		return linked;
	}

	/**
	 * Join valid links between clusters. Resets the link candidates.
	 * 
	 * @param grid
	 * @param nXBins
	 * @param nYBins
	 * @param candidates
	 *            Re-populate will all the remaining clusters
	 */
	private void joinLinks(Cluster[][] grid, int nXBins, int nYBins, ArrayList<Cluster> candidates)
	{
		candidates.clear();

		for (int yBin = 0; yBin < nYBins; yBin++)
		{
			for (int xBin = 0; xBin < nXBins; xBin++)
			{
				for (Cluster c1 = grid[xBin][yBin]; c1 != null; c1 = c1.next)
				{
					if (c1.validLink())
					{
						c1.add(c1.closest);
					}
					// Reset the link candidates
					c1.closest = null;

					// Store all remaining clusters
					if (c1.n != 0)
					{
						candidates.add(c1);
					}
				}

				// Reset the grid
				grid[xBin][yBin] = null;
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.ExtendedPlugInFilter#showDialog(ij.ImagePlus, java.lang.String,
	 * ij.plugin.filter.PlugInFilterRunner)
	 */
	public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
	{
		GenericDialog gd = new GenericDialog(TITLE);
		gd.addHelp(URL.UTILITY);

		gd.addMessage("Find the closest pairs of marked ROI points");

		gd.addSlider("Radius", 5, 50, radius);
		gd.addCheckbox("Add_overlay", addOverlay);
		gd.addCheckbox("Kill_ROI", killRoi);

		gd.addPreviewCheckbox(pfr);
		gd.addDialogListener(this);
		gd.showDialog();
		if (gd.wasCanceled() || !dialogItemChanged(gd, null))
		{
			// Remove any overlay we added
			if (addedOverlay)
				imp.setOverlay(null);
			return DONE;
		}

		return DOES_ALL | FINAL_PROCESSING;
	}

	/**
	 * Listener to modifications of the input fields of the dialog
	 * 
	 * @param gd
	 * @param e
	 * @return
	 */
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e)
	{
		radius = gd.getNextNumber();
		addOverlay = gd.getNextBoolean();
		killRoi = gd.getNextBoolean();
		if (gd.invalidNumber())
			return false;
		return true;
	}

	/**
	 * @param nPasses
	 */
	public void setNPasses(int nPasses)
	{
		// Do nothing		
	}
}
