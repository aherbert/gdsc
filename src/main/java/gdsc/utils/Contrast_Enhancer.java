package gdsc.utils;

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

import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.ContrastEnhancer;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

/**
 * Runs the contrast enhancer on all the open images
 */
public class Contrast_Enhancer implements PlugInFilter
{
	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	public int setup(String arg, ImagePlus imp)
	{
		UsageTracker.recordPlugin(this.getClass(), arg);
		
		if (imp == null)
		{
			return DONE;
		}
		return DOES_ALL | NO_CHANGES;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	public void run(ImageProcessor inputProcessor)
	{
		ContrastEnhancer ce = new ContrastEnhancer();
		double saturated = 0.35;
		for (int id : gdsc.core.ij.Utils.getIDList())
		{
			ImagePlus imp = WindowManager.getImage(id);
			imp.resetDisplayRange();
			ce.stretchHistogram(imp, saturated);
			imp.updateAndDraw();
		}
	}
}
