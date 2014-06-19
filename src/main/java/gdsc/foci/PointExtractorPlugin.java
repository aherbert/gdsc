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
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Extracts the ROI points from an image to file.
 */
public class PointExtractorPlugin implements PlugInFilter
{
	private static String TITLE = "Point Extracter";

	private static String mask = "";
	private static String filename = "";
	private static boolean xyz = true;

	private ImagePlus imp;

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	public int setup(String arg, ImagePlus imp)
	{
		if (imp == null)
		{
			IJ.noImage();
			return DONE;
		}

		this.imp = imp;

		return DOES_ALL;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	public void run(ImageProcessor ip)
	{
		if (!showDialog())
		{
			return;
		}
		Roi roi = imp.getRoi();

		ImagePlus maskImp = WindowManager.getImage(mask);

		AssignedPoint[] points = FindFociOptimiser.extractRoiPoints(roi, imp, maskImp);

		try
		{
			if (xyz)
			{
				PointManager.savePoints(points, filename);
			}
			else
			{
				// Extract the values
				final int t = imp.getCurrentSlice();
				final int z = 0;
				TimeValuedPoint[] p = new TimeValuedPoint[points.length];
				for (int i=0; i<points.length; i++)
				{
					final int x = points[i].getX();
					final int y = points[i].getY();
					p[i] = new TimeValuedPoint(x, y, z, t, ip.getf(x, y));
				}

				TimeValuePointManager.savePoints(p, filename);
			}
		}
		catch (IOException e)
		{
			IJ.error("Failed to save the ROI points:\n" + e.getMessage());
		}
	}

	private boolean showDialog()
	{
		ArrayList<String> newImageList = FindFoci.buildMaskList(imp);

		GenericDialog gd = new GenericDialog(TITLE);

		gd.addMessage("Extracts the ROI points from an image to file");
		gd.addChoice("mask", newImageList.toArray(new String[0]), mask);
		gd.addStringField("filename", filename, 30);
		gd.addCheckbox("xyz_only", xyz);

		Roi roi = imp.getRoi();
		if (roi == null || roi.getType() != Roi.POINT)
		{
			gd.addMessage("Warning: The image does not contain Point ROI.\nAn empty result file will be produced");
		}

		gd.showDialog();

		if (gd.wasCanceled())
			return false;

		mask = gd.getNextChoice();
		filename = gd.getNextString();
		xyz = gd.getNextBoolean();

		return true;
	}
}
