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

import gdsc.utils.ImageJHelper;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

import java.awt.Color;
import java.awt.Point;
import java.awt.TextField;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Compares the coordinates in two files and computes the match statistics.
 * <p>
 * Can read QuickPALM xls files; STORMJ xls files; and a generic CSV file.
 * <p>
 * The generic CSV file has records of the following:<br/>
 * ID,T,X,Y,Z,Value<br/>
 * Z and Value can be missing. The generic file can also be tab delimited.
 */
public class FileMatchCalculator implements PlugIn, MouseListener
{
	public class IdTimeValuedPoint extends TimeValuedPoint
	{
		public int id;

		public IdTimeValuedPoint(int id, TimeValuedPoint p)
		{
			super(p.x, p.y, p.z, p.time, p.value);
			this.id = id;
		}
	}

	private static String TITLE = "Match Calculator";

	private static String title1 = "";
	private static String title2 = "";
	private static double dThreshold = 1;
	private static String mask = "";
	private static double beta = 4;
	private static boolean showPairs = false;
	private static boolean savePairs = false;
	private static String filename = "";

	private static boolean writeHeader = true;
	private static TextWindow resultsWindow = null;
	private static TextWindow pairsWindow = null;

	private TextField text1;
	private TextField text2;

	// flag indicating the pairs have values that should be output
	private boolean valued = false;

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	public void run(String arg)
	{
		ImageJTracker.recordPlugin(TITLE, arg);
		
		if (!showDialog())
		{
			return;
		}

		TimeValuedPoint[] actualPoints = null;
		TimeValuedPoint[] predictedPoints = null;
		double d = 0;

		try
		{
			actualPoints = TimeValuePointManager.loadPoints(title1);
			predictedPoints = TimeValuePointManager.loadPoints(title2);
		}
		catch (IOException e)
		{
			IJ.error("Failed to load the points: " + e.getMessage());
			return;
		}
		d = dThreshold;

		ImagePlus maskImp = WindowManager.getImage(mask);
		if (maskImp != null)
		{
			actualPoints = filter(actualPoints, maskImp.getProcessor());
			predictedPoints = filter(predictedPoints, maskImp.getProcessor());
		}

		compareCoordinates(actualPoints, predictedPoints, d);
	}

	private TimeValuedPoint[] filter(TimeValuedPoint[] points, ImageProcessor processor)
	{
		int ok = 0;
		for (int i = 0; i < points.length; i++)
		{
			if (processor.get(points[i].getX(), points[i].getY()) > 0)
			{
				points[ok++] = points[i];
			}
		}
		return Arrays.copyOf(points, ok);
	}

	/**
	 * Adds an ROI point overlay to the image using the specified colour
	 * 
	 * @param imp
	 * @param list
	 * @param color
	 */
	public static void addOverlay(ImagePlus imp, List<? extends Coordinate> list, Color color)
	{
		if (list.isEmpty())
			return;

		Color strokeColor = color;
		Color fillColor = color;

		Overlay o = imp.getOverlay();
		PointRoi roi = (PointRoi) PointManager.createROI(list);
		roi.setStrokeColor(strokeColor);
		roi.setFillColor(fillColor);
		roi.setHideLabels(true);

		if (o == null)
		{
			imp.setOverlay(roi, strokeColor, 2, fillColor);
		}
		else
		{
			o.add(roi);
			imp.setOverlay(o);
		}
	}

	private boolean showDialog()
	{
		ArrayList<String> newImageList = buildMaskList();

		GenericDialog gd = new GenericDialog(TITLE);

		gd.addMessage("Compare the points in two files\nand compute the match statistics\n(Double click input fields to use a file chooser)");
		gd.addStringField("Input_1", title1, 30);
		gd.addStringField("Input_2", title2, 30);
		if (!newImageList.isEmpty())
			gd.addChoice("mask", newImageList.toArray(new String[0]), mask);
		gd.addNumericField("Distance", dThreshold, 2);
		gd.addNumericField("Beta", beta, 2);
		gd.addCheckbox("Show_pairs", showPairs);
		gd.addCheckbox("Save_pairs", savePairs);

		// Dialog to allow double click to select files using a file chooser
		if (!java.awt.GraphicsEnvironment.isHeadless())
		{
			text1 = (TextField) gd.getStringFields().get(0);
			text2 = (TextField) gd.getStringFields().get(1);
			text1.addMouseListener(this);
			text2.addMouseListener(this);
		}

		gd.showDialog();

		if (gd.wasCanceled())
			return false;

		title1 = gd.getNextString();
		title2 = gd.getNextString();
		mask = (newImageList.isEmpty()) ? "" : gd.getNextChoice();
		dThreshold = gd.getNextNumber();
		beta = gd.getNextNumber();
		showPairs = gd.getNextBoolean();
		savePairs = gd.getNextBoolean();

		return true;
	}

	public static ArrayList<String> buildMaskList()
	{
		ArrayList<String> newImageList = new ArrayList<String>();
		newImageList.add("[None]");

		for (int id : gdsc.utils.ImageJHelper.getIDList())
		{
			ImagePlus maskImp = WindowManager.getImage(id);
			newImageList.add(maskImp.getTitle());
		}

		return newImageList;
	}

	private MatchResult compareCoordinates(TimeValuedPoint[] actualPoints, TimeValuedPoint[] predictedPoints,
			double dThreshold)
	{
		int tp = 0, fp = 0, fn = 0;
		double rmsd = 0;

		final boolean is3D = is3D(actualPoints) && is3D(predictedPoints);

		List<PointPair> pairs = (showPairs) ? new LinkedList<PointPair>() : null;

		// Process each timepoint
		for (Integer t : getTimepoints(actualPoints, predictedPoints))
		{
			Coordinate[] actual = getCoordinates(actualPoints, t);
			Coordinate[] predicted = getCoordinates(predictedPoints, t);

			List<Coordinate> TP = null;
			List<Coordinate> FP = null;
			List<Coordinate> FN = null;
			List<PointPair> matches = null;
			if (showPairs)
			{
				TP = new LinkedList<Coordinate>();
				FP = new LinkedList<Coordinate>();
				FN = new LinkedList<Coordinate>();
				matches = new LinkedList<PointPair>();
			}

			MatchResult result = (is3D) ? MatchCalculator.analyseResults3D(actual, predicted, dThreshold, TP, FP, FN,
					matches) : MatchCalculator.analyseResults2D(actual, predicted, dThreshold, TP, FP, FN, matches);

			// Aggregate
			tp += result.getTruePositives();
			fp += result.getFalsePositives();
			fn += result.getFalseNegatives();
			rmsd += (result.getRMSD() * result.getRMSD()) * result.getTruePositives();

			if (showPairs)
			{
				pairs.addAll(matches);
				for (Coordinate c : FN)
					pairs.add(new PointPair(c, null));
				for (Coordinate c : FP)
					pairs.add(new PointPair(null, c));
			}
		}

		if (showPairs)
		{
			// Check if these are valued points
			valued = isValued(actualPoints) && isValued(predictedPoints);
		}

		if (!java.awt.GraphicsEnvironment.isHeadless())
		{
			if (resultsWindow == null || !resultsWindow.isShowing())
			{
				resultsWindow = new TextWindow(TITLE + " Results", createResultsHeader(), "", 900, 300);
			}
			if (showPairs)
			{
				String header = createPairsHeader(title1, title2);
				if (pairsWindow == null || !pairsWindow.isShowing())
				{
					pairsWindow = new TextWindow(TITLE + " Pairs", header, "", 900, 300);
					Point p = resultsWindow.getLocation();
					p.y += resultsWindow.getHeight();
					pairsWindow.setLocation(p);
				}
				for (PointPair pair : pairs)
					addPairResult(pair, is3D);
			}
		}
		else
		{
			if (writeHeader)
			{
				writeHeader = false;
				IJ.log(createResultsHeader());
			}
		}
		if (tp > 0)
			rmsd = Math.sqrt(rmsd / tp);
		MatchResult result = new MatchResult(tp, fp, fn, rmsd);
		addResult(title1, title2, dThreshold, result);

		if (showPairs && savePairs)
		{
			savePairs(pairs, is3D);
		}

		return result;
	}

	/**
	 * Checks if there is more than one z-value in the coordinates
	 * 
	 * @param points
	 * @return
	 */
	private boolean is3D(TimeValuedPoint[] points)
	{
		if (points.length == 0)
			return false;
		float z = points[0].getPositionZ();
		for (TimeValuedPoint p : points)
			if (p.getZ() != z)
				return true;
		return false;
	}

	/**
	 * Checks if there is a non-zero value within the points
	 * 
	 * @param points
	 * @return
	 */
	private boolean isValued(TimeValuedPoint[] points)
	{
		if (points.length == 0)
			return false;
		for (TimeValuedPoint p : points)
			if (p.getValue() != 0)
				return true;
		return false;
	}

	private Collection<Integer> getTimepoints(TimeValuedPoint[] points, TimeValuedPoint[] points2)
	{
		Set<Integer> set;
		if (showPairs)
			set = new TreeSet<Integer>();
		else
			// The order is not critical so use a HashSet
			set = new HashSet<Integer>();
		for (TimeValuedPoint p : points)
			set.add(p.getTime());
		for (TimeValuedPoint p : points2)
			set.add(p.getTime());
		return set;
	}

	private Coordinate[] getCoordinates(TimeValuedPoint[] points, int t)
	{
		LinkedList<Coordinate> coords = new LinkedList<Coordinate>();
		int id = 1;
		for (TimeValuedPoint p : points)
			if (p.getTime() == t)
				coords.add(new IdTimeValuedPoint(id++, p));
		return coords.toArray(new Coordinate[coords.size()]);
	}

	private String createResultsHeader()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Image 1\t");
		sb.append("Image 2\t");
		sb.append("Distance (px)\t");
		sb.append("N\t");
		sb.append("TP\t");
		sb.append("FP\t");
		sb.append("FN\t");
		sb.append("Jaccard\t");
		sb.append("RMSD\t");
		sb.append("Precision\t");
		sb.append("Recall\t");
		sb.append("F0.5\t");
		sb.append("F1\t");
		sb.append("F2\t");
		sb.append("F-beta");
		return sb.toString();
	}
	
	private void addResult(String i1, String i2, double dThrehsold, MatchResult result)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(i1).append("\t");
		sb.append(i2).append("\t");
		sb.append(IJ.d2s(dThrehsold, 2)).append("\t");
		sb.append(result.getNumberPredicted()).append("\t");
		sb.append(result.getTruePositives()).append("\t");
		sb.append(result.getFalsePositives()).append("\t");
		sb.append(result.getFalseNegatives()).append("\t");
		sb.append(IJ.d2s(result.getJaccard(), 4)).append("\t");
		sb.append(IJ.d2s(result.getRMSD(), 4)).append("\t");
		sb.append(IJ.d2s(result.getPrecision(), 4)).append("\t");
		sb.append(IJ.d2s(result.getRecall(), 4)).append("\t");
		sb.append(IJ.d2s(result.getFScore(0.5), 4)).append("\t");
		sb.append(IJ.d2s(result.getFScore(1.0), 4)).append("\t");
		sb.append(IJ.d2s(result.getFScore(2.0), 4)).append("\t");
		sb.append(IJ.d2s(result.getFScore(beta), 4));

		if (java.awt.GraphicsEnvironment.isHeadless())
		{
			IJ.log(sb.toString());
		}
		else
		{
			resultsWindow.append(sb.toString());
		}
	}

	private String pairsPrefix;
	
	private String createPairsHeader(String i1, String i2)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(i1).append("\t");
		sb.append(i2).append("\t");
		pairsPrefix = sb.toString();
		
		sb.setLength(0);
		sb.append("Image 1\t");
		sb.append("Image 2\t");
		sb.append("T\t");
		sb.append("ID1\t");
		sb.append("X1\t");
		sb.append("Y1\t");
		sb.append("Z1\t");
		if (valued)
			sb.append("Value\t");
		sb.append("ID2\t");
		sb.append("X2\t");
		sb.append("Y2\t");
		sb.append("Z2\t");
		if (valued)
			sb.append("Value\t");
		sb.append("Distance");
		return sb.toString();
	}

	private void addPairResult(PointPair pair, boolean is3D)
	{
		pairsWindow.append(createPairResult(pair, is3D));
	}

	private String createPairResult(PointPair pair, boolean is3D)
	{
		StringBuilder sb = new StringBuilder(pairsPrefix);
		IdTimeValuedPoint p1 = (IdTimeValuedPoint) pair.getPoint1();
		IdTimeValuedPoint p2 = (IdTimeValuedPoint) pair.getPoint2();
		int t = (p1 != null) ? p1.getTime() : p2.getTime();
		sb.append(t).append("\t");
		addPoint(sb, p1);
		addPoint(sb, p2);
		double d = (is3D) ? pair.getXYZDistance() : pair.getXYDistance();
		if (d >= 0)
			sb.append(d);
		else
			sb.append("-");
		return sb.toString();
	}

	private void addPoint(StringBuilder sb, IdTimeValuedPoint p)
	{
		if (p == null)
		{
			if (valued)
				sb.append("-\t-\t-\t-\t-\t");
			else
				sb.append("-\t-\t-\t-\t");
		}
		else
		{
			sb.append(p.id).append("\t");
			sb.append(p.getPositionX()).append("\t");
			sb.append(p.getPositionY()).append("\t");
			sb.append(p.getPositionZ()).append("\t");
			if (valued)
				sb.append(p.getValue()).append("\t");
		}
	}

	private void savePairs(List<PointPair> pairs, boolean is3D)
	{
		String[] path = ImageJHelper.decodePath(filename);
		OpenDialog chooser = new OpenDialog("matches_file", path[0], path[1]);
		if (chooser.getFileName() == null)
			return;

		filename = chooser.getDirectory() + chooser.getFileName();

		OutputStreamWriter out = null;
		try
		{
			FileOutputStream fos = new FileOutputStream(filename);
			out = new OutputStreamWriter(fos, "UTF-8");

			final String newLine = System.getProperty("line.separator");

			out.write(createPairsHeader(title1, title2));
			out.write(newLine);

			for (PointPair pair : pairs)
			{
				out.write(createPairResult(pair, is3D));
				out.write(newLine);
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

	public void mouseClicked(MouseEvent e)
	{
		if (e.getClickCount() > 1) // Double-click
		{
			TextField text;
			String title;
			if (e.getSource() == text1)
			{
				text = text1;
				title = "Coordinate_file_1";
			}
			else
			{
				text = text2;
				title = "Coordinate_file_2";
			}
			String[] path = decodePath(text.getText());
			OpenDialog chooser = new OpenDialog(title, path[0], path[1]);
			if (chooser.getFileName() != null)
			{
				text.setText(chooser.getDirectory() + chooser.getFileName());
			}
		}
	}

	private String[] decodePath(String path)
	{
		String[] result = new String[2];
		int i = path.lastIndexOf('/');
		if (i == -1)
			i = path.lastIndexOf('\\');
		if (i > 0)
		{
			result[0] = path.substring(0, i + 1);
			result[1] = path.substring(i + 1);
		}
		else
		{
			result[0] = OpenDialog.getDefaultDirectory();
			result[1] = path;
		}
		return result;
	}

	public void mousePressed(MouseEvent e)
	{

	}

	public void mouseReleased(MouseEvent e)
	{

	}

	public void mouseEntered(MouseEvent e)
	{

	}

	public void mouseExited(MouseEvent e)
	{

	}
}
