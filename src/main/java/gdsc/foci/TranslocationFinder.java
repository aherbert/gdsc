package gdsc.foci;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import gdsc.UsageTracker;

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
import gdsc.core.match.MatchCalculator;
import gdsc.core.match.PointPair;
import gdsc.core.utils.Maths;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.TextRoi;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;
import ij.text.TextWindow;

/**
 * Find translocations using markers for colocalisation.
 * 
 * <P>
 * Run a pairwise analysis of 3 channels. Find triplets where the two markers from channel 2 & 3 matching a foci in
 * channel 1 are also a matching pair. Draw a bounding box round the triplet and output the distances between the
 * centres. Output a guess for a translocation where channel 13 distance << 12|23, no transolcation where 12 << 13|23.
 */
public class TranslocationFinder implements PlugIn
{
	public static String TITLE = "Translocation Finder";
	private static TextWindow resultsWindow = null;
	private static final int UNKNOWN = 0;
	private static final int NO_TRANSLOCATION = 1;
	private static final int TRANSLOCATION = 2;
	private static final String[] CLASSIFICATION = { "Unknown", "No translocation", "Translocation" };

	private static String resultsName1 = "";
	private static String resultsName2 = "";
	private static String resultsName3 = "";
	private static String image = "";
	private static double distance = 50;
	private static double factor = 2;
	private static boolean showMatches = false;

	public void run(String arg)
	{
		UsageTracker.recordPlugin(this.getClass(), arg);

		// List the foci results
		String[] names = FindFoci.getResultsNames();
		if (names == null || names.length < 3)
		{
			IJ.error(TITLE, "3 sets of Foci must be stored in memory using the " + FindFoci.TITLE + " plugin");
			return;
		}

		// Build a list of the open images to add an overlay
		String[] imageList = Utils.getImageList(Utils.GREY_8_16 | Utils.NO_IMAGE, null);

		GenericDialog gd = new GenericDialog(TITLE);

		gd.addMessage("Analyses spots within a mask/ROI region\nand computes density and closest distances.");

		gd.addChoice("Results_name_1", names, resultsName1);
		gd.addChoice("Results_name_2", names, resultsName2);
		gd.addChoice("Results_name_3", names, resultsName3);
		gd.addChoice("Overlay_on_image", imageList, image);
		gd.addNumericField("Distance", distance, 2, 6, "pixels");
		gd.addNumericField("Factor", factor, 2);
		gd.addCheckbox("Show_matches", showMatches);
		gd.addHelp(gdsc.help.URL.FIND_FOCI);

		gd.showDialog();
		if (!gd.wasOKed())
			return;

		resultsName1 = gd.getNextChoice();
		resultsName2 = gd.getNextChoice();
		resultsName3 = gd.getNextChoice();
		image = gd.getNextChoice();
		distance = gd.getNextNumber();
		factor = gd.getNextNumber();
		showMatches = gd.getNextBoolean();

		// Get the foci
		AssignedPoint[] foci1 = getFoci(resultsName1);
		AssignedPoint[] foci2 = getFoci(resultsName2);
		AssignedPoint[] foci3 = getFoci(resultsName3);

		if (foci1 == null || foci2 == null || foci3 == null)
			return;

		analyse(foci1, foci2, foci3);
	}

	private AssignedPoint[] getFoci(String resultsName)
	{
		ArrayList<int[]> results = FindFoci.getResults(resultsName);
		if (results == null || results.size() == 0)
		{
			IJ.showMessage("Error", "No foci with the name " + resultsName);
			return null;
		}
		AssignedPoint[] foci = new AssignedPoint[results.size()];
		int i = 0;
		for (int[] result : results)
		{
			foci[i] = new AssignedPoint(result[FindFoci.RESULT_X], result[FindFoci.RESULT_Y], result[FindFoci.RESULT_Z],
					i);
			i++;
		}
		return foci;
	}

	/**
	 * For all foci in set 1, compare to set 2 and output a histogram of the average density around each foci (pair
	 * correlation) and the minimum distance to another foci.
	 *
	 * @param foci1
	 *            the foci1
	 * @param foci2
	 *            the foci2
	 * @param foci3
	 *            the foci3
	 */
	private void analyse(AssignedPoint[] foci1, AssignedPoint[] foci2, AssignedPoint[] foci3)
	{
		// Compute pairwise matches
		final boolean is3D = is3D(foci1) || is3D(foci2) || is3D(foci3);
		List<PointPair> matches12 = new ArrayList<PointPair>(Math.min(foci1.length, foci2.length));
		List<PointPair> matches13 = new ArrayList<PointPair>(Math.min(foci1.length, foci3.length));
		List<PointPair> matches23 = new ArrayList<PointPair>(Math.min(foci2.length, foci3.length));
		if (is3D)
		{
			MatchCalculator.analyseResults3D(foci1, foci2, distance, null, null, null, matches12);
			MatchCalculator.analyseResults3D(foci1, foci3, distance, null, null, null, matches13);
			MatchCalculator.analyseResults3D(foci2, foci3, distance, null, null, null, matches23);
		}
		else
		{
			MatchCalculator.analyseResults2D(foci1, foci2, distance, null, null, null, matches12);
			MatchCalculator.analyseResults2D(foci1, foci3, distance, null, null, null, matches13);
			MatchCalculator.analyseResults2D(foci2, foci3, distance, null, null, null, matches23);
		}

		// Use for debugging
		ImagePlus imp = WindowManager.getImage(image);

		if (imp != null && showMatches)
		{
			// DEBUG : Show the matches
			ImageStack stack = new ImageStack(imp.getWidth(), imp.getHeight());
			stack.addSlice("12", new ByteProcessor(imp.getWidth(), imp.getHeight()));
			stack.addSlice("13", new ByteProcessor(imp.getWidth(), imp.getHeight()));
			stack.addSlice("23", new ByteProcessor(imp.getWidth(), imp.getHeight()));
			Overlay ov = new Overlay();
			add(ov, matches12, 1);
			add(ov, matches13, 2);
			add(ov, matches23, 3);
			Utils.display(TITLE, stack).setOverlay(ov);
		}

		// Find triplets with mutual closest neighbours
		ArrayList<int[]> triplets = new ArrayList<int[]>();
		for (PointPair pair12 : matches12)
		{
			final int id1 = ((AssignedPoint) pair12.getPoint1()).id;
			final int id2 = ((AssignedPoint) pair12.getPoint2()).id;

			// Find match in channel 3
			int id3 = -1;
			for (PointPair pair13 : matches13)
			{
				if (id1 == ((AssignedPoint) pair13.getPoint1()).id)
				{
					id3 = ((AssignedPoint) pair13.getPoint2()).id;
					break;
				}
			}

			if (id3 != -1)
			{
				// Find if the same pair match in channel 23
				for (PointPair pair23 : matches23)
				{
					if (id2 == ((AssignedPoint) pair23.getPoint1()).id)
					{
						if (id3 == ((AssignedPoint) pair23.getPoint2()).id)
						{
							// Add an extra int to store the classification
							triplets.add(new int[] { id1, id2, id3, UNKNOWN });
						}
						break;
					}
				}
			}
		}

		// Table of results
		createResultsWindow();

		int count = 0;
		for (int[] triplet : triplets)
		{
			count++;
			StringBuilder sb = new StringBuilder();
			sb.append(name);
			AssignedPoint p1 = foci1[triplet[0]];
			AssignedPoint p2 = foci2[triplet[1]];
			AssignedPoint p3 = foci3[triplet[2]];
			addTriplet(sb, p1);
			addTriplet(sb, p2);
			addTriplet(sb, p3);
			final double d12 = p1.distanceXYZ(p2);
			final double d13 = p1.distanceXYZ(p3);
			final double d23 = p2.distanceXYZ(p3);
			sb.append("\t").append(Utils.rounded(d12));
			sb.append("\t").append(Utils.rounded(d13));
			sb.append("\t").append(Utils.rounded(d23));

			// Compute classification
			if (muchSmaller(d12, d13, d23))
				triplet[3] = NO_TRANSLOCATION;
			else if (muchSmaller(d13, d12, d23))
				triplet[3] = TRANSLOCATION;
			sb.append('\t').append(CLASSIFICATION[triplet[3]]);
			sb.append('\t').append(count).append(CLASSIFICATION[triplet[3]].charAt(0));
			resultsWindow.append(sb.toString());
		}

		// Overlay triplets on image
		if (imp != null)
		{
			Overlay o = new Overlay();
			count = 0;
			for (int[] triplet : triplets)
			{
				count++;
				float[] x = new float[3];
				float[] y = new float[3];
				AssignedPoint p1 = foci1[triplet[0]];
				AssignedPoint p2 = foci2[triplet[1]];
				AssignedPoint p3 = foci3[triplet[2]];
				x[0] = p1.x;
				x[1] = p2.x;
				x[2] = p3.x;
				y[0] = p1.y;
				y[1] = p2.y;
				y[2] = p3.y;
				PolygonRoi roi = new PolygonRoi(x, y, 3, Roi.POLYGON);
				Color color;
				switch (triplet[3])
				{
					case TRANSLOCATION:
						color = Color.CYAN;
						break;
					case NO_TRANSLOCATION:
						color = Color.MAGENTA;
						break;
					case UNKNOWN:
					default:
						color = Color.YELLOW;
				}
				roi.setName("test");
				//roi.setStrokeWidth(2);
				roi.setStrokeColor(color);
				o.add(roi);

				TextRoi text = new TextRoi(Maths.max(x) + 1, Maths.min(y),
						Integer.toString(count) + CLASSIFICATION[triplet[3]].charAt(0));
				text.setStrokeColor(color);
				o.add(text);
			}
			imp.setOverlay(o);
		}
	}

	private boolean is3D(AssignedPoint[] foci)
	{
		final int z = foci[0].z;
		for (int i = 1; i < foci.length; i++)
			if (foci[i].z != z)
				return true;
		return false;
	}

	private void add(Overlay ov, List<PointPair> matches, int n)
	{
		for (PointPair pair : matches)
		{
			AssignedPoint p1 = (AssignedPoint) pair.getPoint1();
			AssignedPoint p2 = (AssignedPoint) pair.getPoint2();
			Line line = new Line(p1.x, p1.y, p2.x, p2.y);
			line.setPosition(n);
			ov.add(line);
		}
	}

	private String name = "";

	private void createResultsWindow()
	{
		// Get the name.
		// We are expecting FindFoci to be run on 3 channels of the same image:
		// 1=ImageTitle (c1,t1)
		// 2=ImageTitle (c2,t1)
		// 3=ImageTitle (c3,t1)
		// Look for this and then output:
		// ImageTitle (c1,t1); (c2,t1); (c3,t1) 

		final int len = Maths.min(resultsName1.length(), resultsName2.length(), resultsName3.length());
		int i = 0;
		while (i < len)
		{
			// Find common prefix
			if (resultsName1.charAt(i) != resultsName2.charAt(i) || resultsName1.charAt(i) != resultsName3.charAt(i) ||
					resultsName1.charAt(i) == '(') // First character of FindFoci results suffix 
			{
				break;
			}
			i++;
		}
		// Common prefix plus the FindFoci suffix
		name = resultsName1 + "; " + resultsName2.substring(i).trim() + "; " + resultsName3.substring(i).trim();

		if (resultsWindow == null || !resultsWindow.isShowing())
		{
			resultsWindow = new TextWindow(TITLE + " Results", createResultsHeader(), "", 700, 300);
		}
	}

	private String createResultsHeader()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Name");
		sb.append("\t1x\t1y\t1z");
		sb.append("\t2x\t2y\t2z");
		sb.append("\t3x\t3y\t3z");
		sb.append("\tD12");
		sb.append("\tD13");
		sb.append("\tD23");
		sb.append("\tClass");
		sb.append("\tLabel");
		return sb.toString();
	}

	private void addTriplet(StringBuilder sb, AssignedPoint p)
	{
		sb.append("\t").append(p.x);
		sb.append("\t").append(p.y);
		sb.append("\t").append(p.z);
	}

	/**
	 * Check if distance 12 is much smaller than distance 13 and 23. It must be a given factor smaller than the other
	 * two distances.
	 *
	 * @param d12
	 *            the d12
	 * @param d13
	 *            the d13
	 * @param d23
	 *            the d23
	 * @return true, if successful
	 */
	private boolean muchSmaller(double d12, double d13, double d23)
	{
		return d13 / d12 > factor && d23 / d12 > factor;
	}
}
