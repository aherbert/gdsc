package gdsc.foci;

import java.util.ArrayList;
import java.util.Arrays;

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
 * <P>
 * Extends the FindFociFloatProcessor to override the FindFociBaseProcessor methods with float specific processing.
 */
public class FindFociOptimisedFloatProcessor extends FindFociFloatProcessor
{
	// There may be nothing to optimise. 
	// We would just be replacing the call to getf(i) with image[i]. All the 
	// processing in FindFociBaseProcessor is float specific anyway.
	// So optimisation should be done by the JVM.

	// For now I have just included those simple methods that use getf() for all pixels in the image.
	// This means changes to the main algorithm methods will be inherited. 

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.FindFociBaseProcessor#pruneMaxima(java.lang.Object, byte[], int, double,
	 * gdsc.foci.FindFociStatistics, java.util.ArrayList, int[])
	 */
	protected void pruneMaxima(Object pixels, byte[] types, int searchMethod, double searchParameter,
			FindFociStatistics stats, ArrayList<FindFociResult> resultsArray, int[] maxima)
	{
		setPixels(pixels);

		// Build an array containing the threshold for each peak.
		// Note that maxima are numbered from 1
		final int nMaxima = resultsArray.size();
		final float[] peakThreshold = new float[nMaxima + 1];
		for (int i = 1; i < peakThreshold.length; i++)
		{
			peakThreshold[i] = getTolerance(searchMethod, searchParameter, stats,
					resultsArray.get(i - 1).maxValue);
		}

		for (int i = maxima.length; i-- > 0;)
		{
			final int id = maxima[i];
			if (id != 0)
			{
				if (image[i] < peakThreshold[id])
				{
					// Unset this pixel as part of the peak
					maxima[i] = 0;
					types[i] &= ~MAX_AREA;
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.FindFociBaseProcessor#calculateInitialResults(java.lang.Object, int[], java.util.ArrayList)
	 */
	protected void calculateInitialResults(Object pixels, int[] maxima, ArrayList<FindFociResult> resultsArray)
	{
		setPixels(pixels);
		final int nMaxima = resultsArray.size();

		// Maxima are numbered from 1
		final int[] count = new int[nMaxima + 1];
		final double[] intensity = new double[nMaxima + 1];

		for (int i = maxima.length; i-- > 0;)
		{
			final int id = maxima[i];
			if (id != 0)
			{
				count[id]++;
				intensity[id] += image[i];
			}
		}

		for (FindFociResult result : resultsArray)
		{
			result.count = count[result.id];
			result.totalIntensity = intensity[result.id];
			result.averageIntensity = result.totalIntensity / result.count;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.FindFociBaseProcessor#calculateNativeResults(java.lang.Object, int[], java.util.ArrayList, int)
	 */
	protected void calculateNativeResults(Object pixels, int[] maxima, ArrayList<FindFociResult> resultsArray,
			int originalNumberOfPeaks)
	{
		setPixels(pixels);

		// Maxima are numbered from 1
		final double[] intensity = new double[originalNumberOfPeaks + 1];
		final float[] max = new float[originalNumberOfPeaks + 1];

		for (int i = maxima.length; i-- > 0;)
		{
			final int id = maxima[i];
			if (id != 0)
			{
				final float v = image[i];
				intensity[id] += v;
				if (max[id] < v)
					max[id] = v;
			}
		}

		for (FindFociResult result : resultsArray)
		{
			final int id = result.id;
			if (intensity[id] != 0)
			{
				result.totalIntensity = intensity[id];
				result.maxValue = max[id];
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.FindFociBaseProcessor#analysePeaks(java.util.ArrayList, java.lang.Object, int[],
	 * gdsc.foci.FindFociStatistics)
	 */
	protected void analysePeaks(ArrayList<FindFociResult> resultsArray, Object pixels, int[] maxima,
			FindFociStatistics stats)
	{
		setPixels(pixels);

		// Create an array of the size/intensity of each peak above the highest saddle 
		final double[] peakIntensity = new double[resultsArray.size() + 1];
		final int[] peakSize = new int[resultsArray.size() + 1];

		// Store all the saddle heights
		final float[] saddleHeight = new float[resultsArray.size() + 1];
		for (FindFociResult result : resultsArray)
		{
			saddleHeight[result.id] = result.highestSaddleValue;
		}

		for (int i = maxima.length; i-- > 0;)
		{
			if (maxima[i] > 0)
			{
				final float v = image[i];
				if (v > saddleHeight[maxima[i]])
				{
					peakIntensity[maxima[i]] += v;
					peakSize[maxima[i]]++;
				}
			}
		}

		for (FindFociResult result : resultsArray)
		{
			result.countAboveSaddle = peakSize[result.id];
			result.intensityAboveSaddle = peakIntensity[result.id];
		}
	}


	/**
	 * Find the size and intensity of peaks above their saddle heights.
	 */
	protected void analysePeaksWithBounds(ArrayList<FindFociResult> resultsArray, Object pixels, int[] maxima,
			FindFociStatistics stats)
	{
		setPixels(pixels);

		// Create an array of the size/intensity of each peak above the highest saddle 
		final double[] peakIntensity = new double[resultsArray.size() + 1];
		final int[] peakSize = new int[resultsArray.size() + 1];

		// Store all the saddle heights
		final float[] saddleHeight = new float[resultsArray.size() + 1];
		for (FindFociResult result : resultsArray)
		{
			saddleHeight[result.id] = result.highestSaddleValue;
			//System.out.printf("ID=%d saddle=%f (%f)\n", result.RESULT_PEAK_ID, result.RESULT_HIGHEST_SADDLE_VALUE, result.RESULT_COUNT_ABOVE_SADDLE);
		}

		// Store the xyz limits for each peak.
		// This speeds up re-computation of the height above the min saddle.
		final int[] minx = new int[peakIntensity.length];
		final int[] miny = new int[peakIntensity.length];
		final int[] minz = new int[peakIntensity.length];
		Arrays.fill(minx, this.maxx);
		Arrays.fill(miny, this.maxy);
		Arrays.fill(minz, this.maxz);
		final int[] maxx = new int[peakIntensity.length];
		final int[] maxy = new int[peakIntensity.length];
		final int[] maxz = new int[peakIntensity.length];

		for (int z = 0, i = 0; z < this.maxz; z++)
			for (int y = 0; y < this.maxy; y++)
				for (int x = 0; x < this.maxx; x++, i++)
				{
					final int id = maxima[i];
					if (id != 0)
					{
						final float v = image[i];
						if (v > saddleHeight[id])
						{
							peakIntensity[id] += v;
							peakSize[id]++;
						}

						// Get bounds
						minx[id] = Math.min(minx[id], x);
						miny[id] = Math.min(miny[id], y);
						minz[id] = Math.min(minz[id], z);
						maxx[id] = Math.max(maxx[id], x);
						maxy[id] = Math.max(maxy[id], y);
						maxz[id] = Math.max(maxz[id], z);
					}
				}

		for (FindFociResult result : resultsArray)
		{
			result.countAboveSaddle = peakSize[result.id];
			result.intensityAboveSaddle = peakIntensity[result.id];
			result.minx = minx[result.id];
			result.miny = miny[result.id];
			result.minz = minz[result.id];
			// Allow iterating i=min; i<max; i++
			result.maxx = maxx[result.id] + 1;
			result.maxy = maxy[result.id] + 1;
			result.maxz = maxz[result.id] + 1;
		}
	}

	/**
	 * Compute the intensity of the peak above the saddle height.
	 *
	 * @param maxima
	 *            the maxima
	 * @param peakIdMap
	 *            the peak id map
	 * @param peakId
	 *            the peak id
	 * @param result
	 *            the result
	 * @param saddleHeight
	 *            the saddle height
	 */
	protected void computeIntensityAboveSaddle(final int[] maxima, final int[] peakIdMap, final int peakId,
			final FindFociResult result, final float saddleHeight)
	{
		int peakSize = 0;
		double peakIntensity = 0;

		// Search using the bounds
		for (int z = result.minz; z < result.maxz; z++)
			for (int y = result.miny; y < result.maxy; y++)
				for (int x = result.minx, i = getIndex(result.minx, y, z); x < result.maxx; x++, i++)
				{
					final int id = maxima[i];
					if (id != 0 && peakIdMap[id] == peakId)
					{
						final float v = image[i];
						if (v > saddleHeight)
						{
							peakIntensity += v;
							peakSize++;
						}
					}
				}

		result.countAboveSaddle = peakSize;
		result.intensityAboveSaddle = peakIntensity;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.FindFociBaseProcessor#getIntensityAboveFloor(java.lang.Object, byte[], float)
	 */
	protected double getIntensityAboveFloor(Object pixels, byte[] types, final float floor)
	{
		setPixels(pixels);

		double sum = 0;
		for (int i = types.length; i-- > 0;)
		{
			if ((types[i] & EXCLUDED) == 0)
			{
				final float v = image[i];
				if (v > floor)
					sum += (v - floor);
			}
		}
		return sum;
	}
}
