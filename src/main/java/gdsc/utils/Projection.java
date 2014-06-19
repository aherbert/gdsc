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

import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

/**
 * Provides methods to filter the image in the z-axis for better 3-D projections. 
 * Performs a local maxima filter in the z-axis resulting in a new image of the same
 * dimensions with a black background for non-maximal pixels.
 */
public class Projection implements PlugInFilter
{
	private ImagePlus imp;
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	public int setup(String arg, ImagePlus imp)
	{
		if (imp == null || imp.getNSlices() == 1)
		{
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
	public void run(ImageProcessor inputProcessor)
	{
		ImageStack stack = imp.getStack();
		
		ImageProcessor ip1 = null;
		ImageProcessor ip2 = null;
		ImageProcessor ip3 = stack.getProcessor(1);
		
		for (int n=2; n<=imp.getNSlices(); n++)
		{
			// Roll over processors
			ip1 = ip2;
			ip2 = ip3;
			ip3 = stack.getProcessor(n);
			
			process(n, ip1, ip2, ip3);
		}
		
		process(imp.getNSlices()+1, ip2, ip3, null);
	}

	private void process(int n, ImageProcessor ip1, ImageProcessor ip2, ImageProcessor ip3)
	{
		if (ip2 == null)
			return;
		
		if (ip1 != null)
		{
			// Check for maxima going backward
			System.out.printf("%d < %d : ", n-2, n-1);
			for (int i=ip2.getPixelCount(); i-->0; )
			{
				if (ip1.get(i) > ip2.get(i))
					ip2.set(i, 0);
			}
		}
		
		if (ip3 != null)
		{
			// Check for maxima going forward
			System.out.printf("%d > %d", n-1, n);
			for (int i=ip2.getPixelCount(); i-->0; )
			{
				if (ip3.get(i) > ip2.get(i))
					ip2.set(i, 0);
			}
		}		
		
		System.out.printf("\n");
	}
}
