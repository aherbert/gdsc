package ij.plugin.filter;

import gdsc.utils.ImageJHelper;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;

import java.util.Arrays;

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

/**
 * Extend the ImageJ Particle Analyser to allow the particles to be obtained from an input mask with objects
 * assigned using contiguous pixels with a unique value. If blank pixels exist between two objects with the same pixel
 * value then they will be treated as separate objects.
 * <p>
 * Adds an option to select the redirection image for particle analysis. This can be none.
 * <p>
 * If the input image is a mask then the functionality is the same as the original ParticleAnalyzer class.
 */
public class MaskParticleAnalyzer extends ParticleAnalyzer
{
	private static String redirectTitle = "";

	private boolean useGetPixelValue;
	private float[] image;
	private double dmin, dmax;

	public int setup(String arg, ImagePlus imp)
	{
		int flags = 0;
		if (imp != null)
		{
			if ("final".equals(arg))
			{
				imp.getProcessor().resetThreshold();
				imp.setDisplayRange(dmin, dmax);
				imp.updateAndDraw();
				return DONE;
			}
			dmin = imp.getDisplayRangeMin();
			dmax = imp.getDisplayRangeMax();

			boolean noThreshold = isNoThreshold(imp);

			// The plugin will be run on a thresholded/mask image to define particles.
			// Choose the redirect image to sample the pixels from.
			int[] idList = ImageJHelper.getIDList();
			String[] list = new String[idList.length + 1];
			list[0] = "[None]";
			int count = 0;
			for (int id : idList)
			{
				ImagePlus imp2 = WindowManager.getImage(id);
				if (imp2 == null || imp2.getWidth() != imp.getWidth() || imp2.getHeight() != imp.getHeight())
					continue;
				list[count++] = imp2.getTitle();
			}
			list = Arrays.copyOf(list, count);
			GenericDialog gd = new GenericDialog("Mask Particle Analyzer...");
			gd.addChoice("Redirect_image", list, redirectTitle);
			if (noThreshold)
				gd.addMessage("Warning: The image is not thresholded / 8-bit binary mask.\nContinuing will use the min/max values in the image");
			gd.showDialog();
			if (gd.wasCanceled())
				return DONE;
			int index = gd.getNextChoiceIndex();
			redirectTitle = list[index];
			ImagePlus redirectImp = (index > 0) ? WindowManager.getImage(redirectTitle) : null;
			Analyzer.setRedirectImage(redirectImp);

			useGetPixelValue = imp.getProcessor() instanceof ColorProcessor;
			image = new float[imp.getWidth() * imp.getHeight()];

			if (noThreshold)
			{
				flags = FINAL_PROCESSING;
				cache(imp.getProcessor());
				float min = Float.POSITIVE_INFINITY;
				float max = Float.NEGATIVE_INFINITY;
				for (int i = 1; i < image.length; i++)
				{
					if (image[i] != 0)
					{
						if (min > image[i])
							min = image[i];
						else if (max < image[i])
							max = image[i];
					}
				}
				if (min == Float.POSITIVE_INFINITY)
				{
					IJ.error("The image has no values");
					return DONE;
				}
				imp.getProcessor().setThreshold(min, max, ImageProcessor.NO_LUT_UPDATE);
			}
		}

		return super.setup(arg, imp) + flags;
	}

	public boolean isNoThreshold(ImagePlus imp)
	{
		boolean noThreshold = false;
		ImageProcessor ip = imp.getProcessor();
		double t1 = ip.getMinThreshold();
		int imageType;
		if (ip instanceof ShortProcessor)
			imageType = SHORT;
		else if (ip instanceof FloatProcessor)
			imageType = FLOAT;
		else
			imageType = BYTE;
		if (t1 == ImageProcessor.NO_THRESHOLD)
		{
			ImageStatistics stats = imp.getStatistics();
			if (imageType != BYTE || (stats.histogram[0] + stats.histogram[255] != stats.pixelCount))
			{
				noThreshold = true;
			}
		}
		return noThreshold;
	}

	private void cache(ImageProcessor ip)
	{
		// Cache a floating-point copy of the image
		final int w = ip.getWidth();
		final int h = ip.getHeight();
		for (int y = 0, i = 0; y < h; y++)
			for (int x = 0; x < w; x++, i++)
			{
				image[i] = (useGetPixelValue) ? ip.getPixelValue(x, y) : ip.getf(i);
			}
	}

	@Override
	public void run(ImageProcessor ip)
	{
		cache(ip);
		super.run(ip);
	}

	@Override
	void analyzeParticle(int x, int y, ImagePlus imp, ImageProcessor ip)
	{
		// x,y - the position the particle was first found
		// imp - the particle image
		// ip - the current processor from the particle image 

		// We need to perform the same work as the super-class but instead of outlining using the 
		// configured thresholds in the particle image we just use the position's current value.
		// Do this by zeroing all pixels that are not the same value and then calling the super-class method.
		ImageProcessor ip2 = ip.duplicate();
		final float value = (useGetPixelValue) ? ip.getPixelValue(x, y) : ip.getf(x, y);
		for (int i = 0; i < image.length; i++)
			if (image[i] != value)
				ip2.set(i, 0);

		ImageProcessor ip3 = ip2.duplicate();
		super.analyzeParticle(x, y, imp, ip2);

		// At the end of processing the analyser fills the image processor to prevent 
		// re-processing this object's pixels. 
		// We must copy back the filled pixel values.
		final int newValue = ip2.get(x, y);
		for (int i = 0; i < image.length; i++)
			if (ip2.get(i) != ip3.get(i))
				ip.set(i, newValue);
	}
}
