package gdsc.foci;

import java.util.Arrays;
import java.util.Comparator;

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
 * Supports 8-, 16- or 32-bit images. Processing is performed using a float image values.
 */
public class FindFociFloatProcessor extends FindFociProcessor
{
	private float[] image;
	// Cache the bin for each index
	private int[] bin;

	public FindFociFloatProcessor(ImagePlus imp)
	{
		super(imp);
		if (imp.getBitDepth() != 8 && imp.getBitDepth() != 16 && imp.getBitDepth() != 32)
			throw new RuntimeException("Bit-depth not supported: " + imp.getBitDepth());
	}

	/**
	 * Extract the image into a linear array stacked in zyx order
	 */
	protected Object extractImage(ImagePlus imp)
	{
		ImageStack stack = imp.getStack();
		float[] image = new float[maxx_maxy_maxz];
		int c = imp.getChannel();
		int f = imp.getFrame();
		for (int slice = 1, i = 0; slice <= maxz; slice++)
		{
			int stackIndex = imp.getStackIndex(c, slice, f);
			ImageProcessor ip = stack.getProcessor(stackIndex);
			for (int index = 0; index < ip.getPixelCount(); index++)
			{
				image[i++] = ip.getf(index);
			}
		}
		return image;
	}

	@Override
	protected Histogram buildHistogram(int bitDepth, Object pixels, byte[] types, int statsMode)
	{
		float[] image = ((float[]) pixels).clone();
		// Store the bin for each index we include
		bin = new int[image.length];
		int[] indices = new int[image.length];
		int c = 0;
		if (statsMode == FindFoci.OPTION_STATS_INSIDE)
		{
			for (int i = 0; i < image.length; i++)
			{
				if ((types[i] & EXCLUDED) == 0)
				{
					image[c] = image[i];
					indices[c] = i;
					c++;
				}
			}
		}
		else
		{
			for (int i = 0; i < image.length; i++)
			{
				if ((types[i] & EXCLUDED) != 0)
				{
					image[c] = image[i];
					indices[c] = i;
					c++;
				}
			}
		}
		image = Arrays.copyOf(image, c);
		indices = Arrays.copyOf(indices, c);
		return buildHistogram(image, indices);
	}

	/**
	 * Build a histogram using all pixels.
	 *
	 * @param data
	 *            The image data (must be sorted)
	 * @param doSort
	 *            True if the data should be sorted
	 * @param indices
	 * @return The image histogram
	 */
	private FloatHistogram buildHistogram(float[] data, int[] indices)
	{
		// Convert data for sorting
		float[][] sortData = new float[indices.length][2];
		for (int i = indices.length; i-- > 0;)
		{
			sortData[i][0] = data[i];
			sortData[i][1] = indices[i];
		}

		Arrays.sort(sortData, new Comparator<float[]>()
		{
			public int compare(float[] o1, float[] o2)
			{
				// Smallest first
				if (o1[0] > o2[0])
					return 1;
				if (o1[0] < o2[0])
					return -1;
				return 0;
			}
		});

		// Copy back
		for (int i = indices.length; i-- > 0;)
		{
			indices[i] = (int) sortData[i][1];
			data[i] = sortData[i][0];
		}

		float lastValue = data[0];
		int count = 0;

		int size = 0;
		float[] value = new float[data.length];
		int[] h = new int[data.length];

		for (int i = 0; i < data.length; i++)
		{
			if (lastValue != data[i])
			{
				value[size] = lastValue;
				h[size] = count;
				while (count > 0)
				{
					// store the bin for the input indices
					bin[indices[i - count]] = size;
					count--;
				}
				size++;
			}
			lastValue = data[i];
			count++;
		}
		// Final count
		value[size] = lastValue;
		h[size] = count;
		while (count > 0)
		{
			// store the bin for the input indices
			bin[indices[data.length - count]] = size;
			count--;
		}
		size++;

		h = Arrays.copyOf(h, size);
		value = Arrays.copyOf(value, size);

		// TODO - remove this
		// Check
		int total = 0;
		for (int i : h)
			total += i;
		if (total != data.length)
			throw new RuntimeException("Failed to compute float histogram");

		return new FloatHistogram(value, h);
	}

	/**
	 * Build a histogram using all pixels.
	 *
	 * @param data
	 *            The image data (must be sorted)
	 * @param doSort
	 *            True if the data should be sorted
	 * @param indices
	 * @return The image histogram
	 */
	private FloatHistogram buildHistogram(float[] data, boolean doSort)
	{
		if (doSort)
			Arrays.sort(data);

		float lastValue = data[0];
		int count = 0;

		int size = 0;
		float[] value = new float[data.length];
		int[] h = new int[data.length];

		for (int i = 0; i < data.length; i++)
		{
			if (lastValue != data[i])
			{
				value[size] = lastValue;
				h[size++] = count;
				count = 0;
			}
			lastValue = data[i];
			count++;
		}
		// Final count
		value[size] = lastValue;
		h[size++] = count;

		h = Arrays.copyOf(h, size);
		value = Arrays.copyOf(value, size);

		// Check
		int total = 0;
		for (int i : h)
			total += i;
		if (total != data.length)
			throw new RuntimeException("Failed to compute float histogram");

		return new FloatHistogram(value, h);
	}

	protected Histogram buildHistogram(int bitDepth, Object pixels)
	{
		return buildHistogram(((float[]) pixels).clone(), true);
	}

	protected Histogram buildHistogram(Object pixels, int[] maxima, int peakValue, float maxValue)
	{
		float[] image = (float[]) pixels;
		int size = 0;
		float[] data = new float[100];
		for (int i = image.length; i-- > 0;)
		{
			if (maxima[i] == peakValue)
			{
				data[size++] = image[i];
				if (size == data.length)
					data = Arrays.copyOf(data, (int) (size * 1.5));
			}
		}
		return buildHistogram(Arrays.copyOf(data, size), true);
	}

	@Override
	protected float getSearchThreshold(int backgroundMethod, double backgroundParameter, double[] stats)
	{
		switch (backgroundMethod)
		{
			case FindFoci.BACKGROUND_ABSOLUTE:
				// Ensure all points above the threshold parameter are found
				//return (float) ((backgroundParameter > 0) ? backgroundParameter : 0);
				// Allow negatives
				return (float) backgroundParameter;

			case FindFoci.BACKGROUND_AUTO_THRESHOLD:
				return (float) (stats[FindFoci.STATS_BACKGROUND]);

			case FindFoci.BACKGROUND_MEAN:
				return (float) (stats[FindFoci.STATS_AV_BACKGROUND]);

			case FindFoci.BACKGROUND_STD_DEV_ABOVE_MEAN:
				return (float) (stats[FindFoci.STATS_AV_BACKGROUND] +
						((backgroundParameter > 0) ? backgroundParameter * stats[FindFoci.STATS_SD_BACKGROUND] : 0));

			case FindFoci.BACKGROUND_MIN_ROI:
				return (float) (stats[FindFoci.STATS_MIN]);

			case FindFoci.BACKGROUND_NONE:
			default:
				// Ensure all the maxima are found. Use Min and not zero to support float images with negative values
				return (float) stats[FindFoci.STATS_MIN];
		}
	}

	protected void setPixels(Object pixels)
	{
		this.image = (float[]) pixels;
	}

	protected float getf(int i)
	{
		return image[i];
	}

	protected int getBackgroundBin(Histogram histogram, double background)
	{
		for (int i = histogram.minBin; i < histogram.maxBin; i++)
		{
			if (histogram.getValue(i) >= background)
				return i;
		}
		return histogram.maxBin;
	}

	protected int getBin(Histogram histogram, int i)
	{
		// We store the bin for each input index when building the histogram
		int bin = this.bin[i];

		//// Check 
		//int bin2 = findBin(histogram, i);
		//if (bin != bin2)
		//{
		//	throw new RuntimeException("Failed to compute float value histogram bin: " + bin + " != " + bin2);
		//}

		return bin;
	}

	protected int findBin(Histogram histogram, int i)
	{
		/* perform binary search - relies on having sorted data */
		final float[] values = ((FloatHistogram) histogram).value;
		final float value = image[i];
		int upper = values.length - 1;
		int lower = 0;

		while (upper - lower > 1)
		{
			//final int mid = (upper + lower) / 2;
			final int mid = upper + lower >>> 1;

			if (value >= values[mid])
			{
				lower = mid;
			}
			else
			{
				upper = mid;
			}
		}

		/* sanity check the result */
		if (value < values[lower] || value >= values[lower + 1])
		{
			// The search attempts to find the index for lower which is equal or above the value.
			// Process the exceptional case where we are at the top end of the range
			if (value == values[lower + 1])
				return lower + 1;

			return -1;
		}

		return lower;
	}

	protected float getTolerance(int searchMethod, double searchParameter, double[] stats, double v0)
	{
		switch (searchMethod)
		{
			case FindFoci.SEARCH_ABOVE_BACKGROUND:
				return (float) (stats[FindFoci.STATS_BACKGROUND]);

			case FindFoci.SEARCH_FRACTION_OF_PEAK_MINUS_BACKGROUND:
				if (searchParameter < 0)
					searchParameter = 0;
				return (float) (stats[FindFoci.STATS_BACKGROUND] +
						searchParameter * (v0 - stats[FindFoci.STATS_BACKGROUND]));

			case FindFoci.SEARCH_HALF_PEAK_VALUE:
				return (float) (stats[FindFoci.STATS_BACKGROUND] + 0.5 * (v0 - stats[FindFoci.STATS_BACKGROUND]));
		}
		return (float) (stats[FindFoci.STATS_MIN]);
	}

	protected double getPeakHeight(int peakMethod, double peakParameter, double[] stats, double v0)
	{
		double height = 0;
		switch (peakMethod)
		{
			case FindFoci.PEAK_ABSOLUTE:
				height = (peakParameter);
				break;
			case FindFoci.PEAK_RELATIVE:
				height = (v0 * peakParameter);
				break;
			case FindFoci.PEAK_RELATIVE_ABOVE_BACKGROUND:
				height = ((v0 - stats[FindFoci.STATS_BACKGROUND]) * peakParameter);
				break;
		}
		if (height <= 0)
		{
			// This is an edge case that will only happen if peakParameter is zero or below.
			// Just make it small enough that there must be a peak above the saddle point.
			height = ((v0 - stats[FindFoci.STATS_BACKGROUND]) * 1e-6);
		}
		return height;
	}
}
