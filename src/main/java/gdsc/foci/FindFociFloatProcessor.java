/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 * 
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2018 Alex Herbert
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package gdsc.foci;

import java.util.Arrays;
import java.util.Comparator;

import gdsc.core.threshold.FloatHistogram;
import gdsc.core.threshold.Histogram;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;


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
public class FindFociFloatProcessor extends FindFociBaseProcessor
{
	protected float[] image;
	// Cache the bin for each index
	protected int[] bin;

	/**
	 * Extract the image into a linear array stacked in zyx order
	 */
	protected Object extractImage(ImagePlus imp)
	{
		final ImageStack stack = imp.getStack();
		final float[] image = new float[maxx_maxy_maxz];
		final int c = imp.getChannel();
		final int f = imp.getFrame();
		for (int slice = 1, i = 0; slice <= maxz; slice++)
		{
			final int stackIndex = imp.getStackIndex(c, slice, f);
			final ImageProcessor ip = stack.getProcessor(stackIndex);
			for (int index = 0; index < ip.getPixelCount(); index++)
			{
				image[i++] = ip.getf(index);
			}
		}
		return image;
	}

	protected byte[] createTypesArray(Object pixels)
	{
		final float[] image = (float[]) pixels;
		final byte[] types = new byte[maxx_maxy_maxz];
		// Blank bad pixels
		// TODO - See how the algorithm will cope with a Gaussian blur on NaN or infinite pixels.
		// Maybe we must replace invalid pixels with something else, e.g. mean of the remaining valid pixels
		// or mean of the surrounding pixels (if they are valid)
		for (int i = 0; i < types.length; i++)
			if (Float.isNaN(image[i]) || Float.isInfinite(image[i]))
				types[i] = EXCLUDED;
		return types;
	}

	protected float getImageMin(Object pixels, byte[] types)
	{
		final float[] image = (float[]) pixels;
		float min = Float.POSITIVE_INFINITY;
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

	@Override
	protected Histogram buildHistogram(int bitDepth, Object pixels, byte[] types, int statsMode)
	{
		float[] image = ((float[]) pixels).clone();
		// Store the bin for each index we include
		bin = new int[image.length];
		int[] indices = new int[image.length];
		int c = 0;
		if (statsMode == OPTION_STATS_INSIDE)
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

		//// Check
		//int total = 0;
		//for (int i : h)
		//	total += i;
		//if (total != data.length)
		//	throw new RuntimeException("Failed to compute float histogram");

		return new FloatHistogram(value, h);
	}

	protected Histogram buildHistogram(int bitDepth, Object pixels)
	{
		return FloatHistogram.buildHistogram(((float[]) pixels).clone(), true);
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
		return FloatHistogram.buildHistogram(Arrays.copyOf(data, size), true);
	}

	@Override
	protected float getSearchThreshold(int backgroundMethod, double backgroundParameter, FindFociStatistics stats)
	{
		switch (backgroundMethod)
		{
			case BACKGROUND_ABSOLUTE:
				// Ensure all points above the threshold parameter are found
				//return (float) ((backgroundParameter > 0) ? backgroundParameter : 0);
				// Allow negatives
				return (float) backgroundParameter;

			case BACKGROUND_AUTO_THRESHOLD:
				return (float) (stats.background);

			case BACKGROUND_MEAN:
				return (float) (stats.backgroundRegionAverage);

			case BACKGROUND_STD_DEV_ABOVE_MEAN:
				return (float) (stats.backgroundRegionAverage +
						((backgroundParameter > 0) ? backgroundParameter * stats.backgroundRegionStdDev : 0));

			case BACKGROUND_MIN_ROI:
				return (float) (stats.regionMinimum);

			case BACKGROUND_NONE:
			default:
				// Ensure all the maxima are found. Use Min and not zero to support float images with negative values
				return (float) stats.regionMinimum;
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

	protected int getBackgroundBin(Histogram histogram, float background)
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

	protected float getTolerance(int searchMethod, double searchParameter, FindFociStatistics stats, float v0)
	{
		switch (searchMethod)
		{
			case SEARCH_ABOVE_BACKGROUND:
				return (float) (stats.background);

			case SEARCH_FRACTION_OF_PEAK_MINUS_BACKGROUND:
				if (searchParameter < 0)
					searchParameter = 0;
				return (float) (stats.background + searchParameter * (v0 - stats.background));

			case SEARCH_HALF_PEAK_VALUE:
				return (float) (stats.background + 0.5 * (v0 - stats.background));
		}
		return (float) (stats.regionMinimum);
	}

	protected double getPeakHeight(int peakMethod, double peakParameter, FindFociStatistics stats, float v0)
	{
		double height = 0;
		switch (peakMethod)
		{
			case PEAK_ABSOLUTE:
				height = (peakParameter);
				break;
			case PEAK_RELATIVE:
				height = (v0 * peakParameter);
				break;
			case PEAK_RELATIVE_ABOVE_BACKGROUND:
				height = ((v0 - stats.background) * peakParameter);
				break;
		}
		if (height <= 0)
		{
			// This is an edge case that will only happen if peakParameter is zero or below.
			// Just make it small enough that there must be a peak above the saddle point.
			height = ((v0 - stats.background) * 1e-6);
		}
		return height;
	}

	@Override
	public boolean isFloatProcessor()
	{
		return true;
	}
}
