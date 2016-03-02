package gdsc.utils;

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
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.io.FileSaver;
import ij.measure.Measurements;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import gdsc.UsageTracker;

/**
 * Scales all planes in an image to the given maximim
 */
public class Image_Scaler implements PlugInFilter
{
	private static final String TITLE = "Image Scaler";
	private ImagePlus imp;
	private static double maxValue = 255;
	private static String listFile = "";

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	public int setup(String arg, ImagePlus imp)
	{
		UsageTracker.recordPlugin(this.getClass(), arg);
		
		if (!showDialog())
		{
			return DONE;
		}
		this.imp = imp;
		return DOES_ALL | NO_IMAGE_REQUIRED;
	}

	private boolean showDialog()
	{
		GenericDialog gd = new GenericDialog(TITLE);

		gd.addMessage("Rescales the maxima of the image(s) to the given value.\nProcesses the image stack or a set of input images.");
		gd.addNumericField("Max", maxValue, 2);
		gd.addMessage("List file containing full image path, one image per line.");
		gd.addStringField("List file", listFile);
		gd.showDialog();
		gd.addHelp(gdsc.help.URL.UTILITY);

		if (gd.wasCanceled())
			return false;

		maxValue = gd.getNextNumber();
		listFile = gd.getNextString();

		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	public void run(ImageProcessor inputProcessor)
	{
		boolean listExists = (listFile != null && !listFile.equals(""));
		if (imp == null && !listExists)
			return;

		if (listExists)
		{
			run(listFile, maxValue);
		}
		else
		{
			run(new ImagePlus[] { imp }, maxValue);
		}
	}

	/**
	 * Scales all the images by the same factor so that one image has the specified maximum.
	 * 
	 * @param listFile File listing all the images to scale
	 * @param maxValue
	 */
	public void run(String listFile, double maxValue)
	{
		boolean listExists = (listFile != null && !listFile.equals(""));
		if (!listExists)
			return;

		if (!new File(listFile).exists())
			return;

		double[] limits = new double[] { Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }; 
		
		// Read all images sequentially and find the limits
		try
		{
			BufferedReader input = new BufferedReader(new FileReader(listFile));
			String line = null; //not declared within while loop
			while ((line = input.readLine()) != null)
			{
				if (!new File(line).exists())
					continue;
				
				ImagePlus imp = new ImagePlus(line);
				if (imp != null)
				{
					updateMinAndMax(imp, limits);
				}
				imp.flush();
				imp = null; // Free memory
			}
			input.close();
		}
		catch (Exception e)
		{
			IJ.error("Failed to read images in input list file: " + listFile);
			return;
		}
		
		if (limits[1] <= limits[0])
			return;

		double scaleFactor = maxValue / limits[1];
		
		// Rewrite images
		try
		{
			BufferedReader input = new BufferedReader(new FileReader(listFile));
			String line = null; //not declared within while loop
			while ((line = input.readLine()) != null)
			{
				if (!new File(line).exists())
					continue;
				
				ImagePlus imp = new ImagePlus(line);
				if (imp != null)
				{
					multiply(imp, scaleFactor);
					new FileSaver(imp).save();
				}
				imp.flush();
				imp = null; // Free memory
			}
			input.close();
		}
		catch (Exception e)
		{
			IJ.error("Failed to re-write images in input list file: " + listFile);
		}
	}
	
	/**
	 * Scales all the images by the same factor so that one image has the specified maximum.
	 * 
	 * @param images
	 * @param maxValue
	 */
	public void run(ImagePlus[] images, double maxValue)
	{
		double[] limits = new double[] { Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }; 

		for (ImagePlus imp : images)
		{
			updateMinAndMax(imp, limits);
		}

		if (limits[1] <= limits[0])
			return;

		double scaleFactor = maxValue / limits[1];

		for (ImagePlus imp : images)
		{
			multiply(imp, scaleFactor);
		}
	}

	private void updateMinAndMax(ImagePlus imp, double[] limits)
	{
		ImageStack stack = imp.getImageStack();
		for (int slice = 1; slice <= stack.getSize(); slice++)
		{
			ImageStatistics stats = ImageStatistics.getStatistics(stack.getProcessor(slice), Measurements.MIN_MAX,
					null);
			if (limits[0] > stats.min)
				limits[0] = stats.min;
			if (limits[1] < stats.max)
				limits[1] = stats.max;
		}		
	}

	private void multiply(ImagePlus imp, double scaleFactor)
	{
		ImageStack stack = imp.getImageStack();
		for (int slice = 1; slice <= stack.getSize(); slice++)
		{
			stack.getProcessor(slice).multiply(scaleFactor);
		}
		if (imp.isVisible())
		{
			imp.resetDisplayRange();
			imp.updateAndDraw();
		}
	}
}
