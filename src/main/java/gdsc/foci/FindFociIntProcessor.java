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
public class FindFociIntProcessor extends FindFociBaseProcessor
{
	private int[] image;

	protected Object extractImage(ImagePlus imp)
	{
		if (imp.getBitDepth() != 8 && imp.getBitDepth() != 16)
			throw new RuntimeException("Bit-depth not supported: " + imp.getBitDepth());

		final ImageStack stack = imp.getStack();
		final int[] image = new int[maxx_maxy_maxz];
		final int c = imp.getChannel();
		final int f = imp.getFrame();
		for (int slice = 1, i = 0; slice <= maxz; slice++)
		{
			final int stackIndex = imp.getStackIndex(c, slice, f);
			final ImageProcessor ip = stack.getProcessor(stackIndex);
			for (int index = 0; index < ip.getPixelCount(); index++)
			{
				image[i++] = ip.get(index);
			}
		}
		return image;
	}

	protected byte[] createTypesArray(Object pixels)
	{
		return new byte[maxx_maxy_maxz];
	}

	protected float getImageMin(Object pixels, byte[] types)
	{
		final int[] image = (int[]) pixels;
		int min = Integer.MAX_VALUE;
		for (int i = image.length; i-- > 0;)
		{
			if ((types[i] & EXCLUDED) == 0)
			{
				if (min > image[i])
					min = image[i];
			}
		}
		return min;
	}

	protected Histogram buildHistogram(int bitDepth, Object pixels, byte[] types, int statsMode)
	{
		final int[] image = (int[]) pixels;

		final int size = (int) Math.pow(2, bitDepth);

		final int[] data = new int[size];

		if (statsMode == OPTION_STATS_INSIDE)
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
		final int[] image = ((int[]) pixels);

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
		final int[] image = (int[]) pixels;
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

	protected float getSearchThreshold(int backgroundMethod, double backgroundParameter, FindFociStatistics stats)
	{
		switch (backgroundMethod)
		{
			case BACKGROUND_ABSOLUTE:
				// Ensure all points above the threshold parameter are found
				return round((backgroundParameter >= 0) ? backgroundParameter : 0);

			case BACKGROUND_AUTO_THRESHOLD:
				return round(stats.background);

			case BACKGROUND_MEAN:
				return round(stats.backgroundRegionAverage);

			case BACKGROUND_STD_DEV_ABOVE_MEAN:
				return round(stats.backgroundRegionAverage +
						((backgroundParameter >= 0) ? backgroundParameter * stats.backgroundRegionStdDev : 0));

			case BACKGROUND_MIN_ROI:
				return round(stats.regionMinimum);

			case BACKGROUND_NONE:
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

	protected int getBackgroundBin(Histogram histogram, float background)
	{
		return round(background);
	}

	protected int getBin(Histogram histogram, int i)
	{
		return image[i];
	}

	protected float getTolerance(int searchMethod, double searchParameter, FindFociStatistics stats, float v0)
	{
		switch (searchMethod)
		{
			case SEARCH_ABOVE_BACKGROUND:
				return round(stats.background);

			case SEARCH_FRACTION_OF_PEAK_MINUS_BACKGROUND:
				if (searchParameter < 0)
					searchParameter = 0;
				return round(stats.background + searchParameter * (v0 - stats.background));

			case SEARCH_HALF_PEAK_VALUE:
				return round(stats.background + 0.5 * (v0 - stats.background));
		}
		return 0;
	}

	protected double getPeakHeight(int peakMethod, double peakParameter, FindFociStatistics stats, float v0)
	{
		int height = 1;
		if (peakParameter < 0)
			peakParameter = 0;
		switch (peakMethod)
		{
			case PEAK_ABSOLUTE:
				height = round(peakParameter);
				break;
			case PEAK_RELATIVE:
				height = round(v0 * peakParameter);
				break;
			case PEAK_RELATIVE_ABOVE_BACKGROUND:
				height = round((v0 - stats.background) * peakParameter);
				break;
		}
		if (height < 1)
			height = 1; // It should be a peak
		return height;
	}

	@Override
	public boolean isFloatProcessor()
	{
		return false;
	}
}
