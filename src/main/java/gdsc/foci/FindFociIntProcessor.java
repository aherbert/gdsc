package gdsc.foci;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

/*----------------------------------------------------------------------------- 
 * GDSC Plugins for ImageJ
 * 
 * Copyright (C) 2016 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

/**
 * Find the peak intensity regions of an image.
 * 
 * <P>
 * All local maxima above threshold are identified. For all other pixels the direction to the highest neighbour pixel is
 * stored (steepest gradient). In order of highest local maxima, regions are only grown down the steepest gradient to a
 * lower pixel. Provides many configuration options for regions growing thresholds.
 * 
 * <P>
 * This plugin was based on {@link ij.plugin.filter.MaximumFinder}. Options have been changed to only support greyscale
 * 2D images and 3D stacks and to perform region growing using configurable thresholds. Support for Watershed,
 * Previewing, and Euclidian Distance Map (EDM) have been removed.
 * 
 * <P>
 * Stopping criteria for region growing routines are partly based on the options in PRIISM
 * (http://www.msg.ucsf.edu/IVE/index.html).
 * 
 * <p>
 * Supports 8- or 16-bit images
 */
public class FindFociIntProcessor extends FindFociProcessor
{
	private int[] image;

	public FindFociIntProcessor(ImagePlus imp)
	{
		super(imp);
		if (imp.getBitDepth() != 8 && imp.getBitDepth() != 16)
			throw new RuntimeException("Bit-depth not supported: " + imp.getBitDepth());
	}

	protected Object extractImage(ImagePlus imp)
	{
		ImageStack stack = imp.getStack();
		int[] image = new int[maxx_maxy_maxz];
		int c = imp.getChannel();
		int f = imp.getFrame();
		for (int slice = 1, i = 0; slice <= maxz; slice++)
		{
			int stackIndex = imp.getStackIndex(c, slice, f);
			ImageProcessor ip = stack.getProcessor(stackIndex);
			for (int index = 0; index < ip.getPixelCount(); index++)
			{
				image[i++] = ip.get(index);
			}
		}
		return image;
	}

	protected Histogram buildHistogram(int bitDepth, Object pixels, byte[] types, int statsMode)
	{
		int[] image = (int[]) pixels;

		final int size = (int) Math.pow(2, bitDepth);

		final int[] data = new int[size];

		if (statsMode == FindFoci.OPTION_STATS_INSIDE)
		{
			for (int i = image.length; i-- > 0;)
			{
				if ((types[i] & EXCLUDED) == 0)
				{
					data[image[i]]++;
				}
			}
		}
		else
		{
			for (int i = image.length; i-- > 0;)
			{
				if ((types[i] & EXCLUDED) != 0)
				{
					data[image[i]]++;
				}
			}
		}

		return new Histogram(data);
	}

	protected Histogram buildHistogram(int bitDepth, Object pixels)
	{
		int[] image = ((int[]) pixels);

		final int size = (int) Math.pow(2, bitDepth);

		final int[] data = new int[size];

		for (int i = image.length; i-- > 0;)
		{
			data[image[i]]++;
		}

		return new Histogram(data);
	}

	protected Histogram buildHistogram(Object pixels, int[] maxima, int peakValue, float maxValue)
	{
		int[] image = (int[]) pixels;
		final int[] histogram = new int[(int) maxValue + 1];

		for (int i = image.length; i-- > 0;)
		{
			if (maxima[i] == peakValue)
			{
				histogram[image[i]]++;
			}
		}
		return new Histogram(histogram);
	}

	protected float getSearchThreshold(int backgroundMethod, double backgroundParameter, double[] stats)
	{
		switch (backgroundMethod)
		{
			case FindFoci.BACKGROUND_ABSOLUTE:
				// Ensure all points above the threshold parameter are found
				return round((backgroundParameter >= 0) ? backgroundParameter : 0);

			case FindFoci.BACKGROUND_AUTO_THRESHOLD:
				return round(stats[FindFoci.STATS_BACKGROUND]);

			case FindFoci.BACKGROUND_MEAN:
				return round(stats[FindFoci.STATS_AV_BACKGROUND]);

			case FindFoci.BACKGROUND_STD_DEV_ABOVE_MEAN:
				return round(stats[FindFoci.STATS_AV_BACKGROUND] +
						((backgroundParameter >= 0) ? backgroundParameter * stats[FindFoci.STATS_SD_BACKGROUND] : 0));

			case FindFoci.BACKGROUND_MIN_ROI:
				return round(stats[FindFoci.STATS_MIN]);

			case FindFoci.BACKGROUND_NONE:
			default:
				// Ensure all the maxima are found
				return 0;
		}
	}

	protected void setPixels(Object pixels)
	{
		this.image = (int[]) pixels;
	}

	protected float getf(int i)
	{
		return image[i];
	}

	protected int getBackgroundBin(Histogram histogram, double background)
	{
		return round(background);
	}
	
	protected int getBin(Histogram histogram, int i)
	{
		return image[i];
	}
	
	protected float getTolerance(int searchMethod, double searchParameter, double[] stats, double v0)
	{
		switch (searchMethod)
		{
			case FindFoci.SEARCH_ABOVE_BACKGROUND:
				return round(stats[FindFoci.STATS_BACKGROUND]);

			case FindFoci.SEARCH_FRACTION_OF_PEAK_MINUS_BACKGROUND:
				if (searchParameter < 0)
					searchParameter = 0;
				return round(stats[FindFoci.STATS_BACKGROUND] + searchParameter * (v0 - stats[FindFoci.STATS_BACKGROUND]));

			case FindFoci.SEARCH_HALF_PEAK_VALUE:
				return round(stats[FindFoci.STATS_BACKGROUND] + 0.5 * (v0 - stats[FindFoci.STATS_BACKGROUND]));
		}
		return 0;
	}
	
	protected double getPeakHeight(int peakMethod, double peakParameter, double[] stats, double v0)
	{
		int height = 1;
		if (peakParameter < 0)
			peakParameter = 0;
		switch (peakMethod)
		{
			case FindFoci.PEAK_ABSOLUTE:
				height = round(peakParameter);
				break;
			case FindFoci.PEAK_RELATIVE:
				height = round(v0 * peakParameter);
				break;
			case FindFoci.PEAK_RELATIVE_ABOVE_BACKGROUND:
				height = round((v0 - stats[FindFoci.STATS_BACKGROUND]) * peakParameter);
				break;
		}
		if (height < 1)
			height = 1; // It should be a peak
		return height;
	}
}
