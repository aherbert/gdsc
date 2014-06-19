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

import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

/**
 * Manages I/O of the Point class
 */
public class PointManager
{
	public static final String newline = System.getProperty("line.separator");

	/**
	 * Save the predicted points to the given file
	 * 
	 * @param points
	 * @param filename
	 * @throws IOException
	 */
	public static void savePoints(AssignedPoint[] points, String filename) throws IOException
	{
		if (points == null)
			return;

		OutputStreamWriter out = null;
		try
		{
			File file = new File(filename);
			if (!file.exists())
			{
				if (file.getParent() != null)
					new File(file.getParent()).mkdirs();
			}

			// Save results to file
			FileOutputStream fos = new FileOutputStream(filename);
			out = new OutputStreamWriter(fos);

			StringBuilder sb = new StringBuilder();

			out.write("X,Y,Z" + newline);

			// Output all results in ascending rank order
			for (AssignedPoint point : points)
			{
				sb.append(point.getX()).append(',');
				sb.append(point.getY()).append(',');
				sb.append(point.getZ()).append(newline);
				out.write(sb.toString());
				sb.setLength(0);
			}
		}
		finally
		{
			try
			{
				if (out != null)
					out.close();
			}
			catch (IOException e)
			{
			}
		}
	}

	/**
	 * Loads the points from the file
	 * 
	 * @param filename
	 * @return
	 * @throws IOException
	 */
	public static AssignedPoint[] loadPoints(String filename) throws IOException
	{
		LinkedList<AssignedPoint> points = new LinkedList<AssignedPoint>();
		BufferedReader input = null;
		try
		{
			// Load results from file
			input = new BufferedReader(new FileReader(filename));

			String line = input.readLine();

			if (line != null)
			{
				int lineCount = 1;
				while ((line = input.readLine()) != null)
				{
					lineCount++;
					String[] tokens = line.split(",");
					if (tokens.length == 3)
					{
						try
						{
							int x = Integer.parseInt(tokens[0]);
							int y = Integer.parseInt(tokens[1]);
							int z = Integer.parseInt(tokens[2]);
							points.add(new AssignedPoint(x, y, z, lineCount - 1));
						}
						catch (NumberFormatException e)
						{
							System.err.println("Invalid numbers on line: " + lineCount);
						}
					}
				}
			}

			return points.toArray(new AssignedPoint[0]);
		}
		finally
		{
			try
			{
				if (input != null)
					input.close();
			}
			catch (IOException e)
			{
			}
		}
	}

	/**
	 * Extracts the points from the given Point ROI
	 * 
	 * @param roi
	 * @return The list of points (can be zero length)
	 */
	public static AssignedPoint[] extractRoiPoints(Roi roi)
	{
		AssignedPoint[] roiPoints = null;

		if (roi != null && roi.getType() == Roi.POINT)
		{
			Polygon p = ((PolygonRoi) roi).getNonSplineCoordinates();
			int n = p.npoints;
			Rectangle bounds = roi.getBounds();

			roiPoints = new AssignedPoint[n];
			for (int i = 0; i < n; i++)
			{
				roiPoints[i] = new AssignedPoint(bounds.x + p.xpoints[i], bounds.y + p.ypoints[i], i);
			}
		}
		else
		{
			roiPoints = new AssignedPoint[0];
		}

		return roiPoints;
	}

	/**
	 * Creates an ImageJ PointRoi from the list of points
	 * 
	 * @param array
	 *            List of points
	 * @return The PointRoi
	 */
	public static Roi createROI(List<? extends Coordinate> array)
	{
		int nMaxima = array.size();
		int[] xpoints = new int[nMaxima];
		int[] ypoints = new int[nMaxima];
		int i = 0;
		for (Coordinate point : array)
		{
			xpoints[i] = point.getX();
			ypoints[i] = point.getY();
			i++;
		}
		return new PointRoi(xpoints, ypoints, nMaxima);
	}

	/**
	 * Creates an ImageJ PointRoi from the list of points
	 * 
	 * @param array
	 *            List of points
	 * @return The PointRoi
	 */
	public static Roi createROI(AssignedPoint[] array)
	{
		int nMaxima = array.length;
		int[] xpoints = new int[nMaxima];
		int[] ypoints = new int[nMaxima];
		for (int i = 0; i < nMaxima; i++)
		{
			xpoints[i] = array[i].getX();
			ypoints[i] = array[i].getY();
		}
		return new PointRoi(xpoints, ypoints, nMaxima);
	}

	/**
	 * Eliminates duplicate coordinates. Destructively alters the IDs in the input array since the objects are recycled
	 * 
	 * @param points
	 * @return new list of points with Ids from zero
	 */
	public static AssignedPoint[] eliminateDuplicates(AssignedPoint[] points)
	{
		HashSet<AssignedPoint> newPoints = new HashSet<AssignedPoint>();
		int id = 0;
		for (AssignedPoint p : points)
		{
			if (newPoints.add(p))
				p.setId(id++);
		}
		return newPoints.toArray(new AssignedPoint[0]);
	}
}