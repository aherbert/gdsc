package gdsc.foci;

import java.util.ArrayList;

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
					resultsArray.get(i - 1).RESULT_MAX_VALUE);
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
			result.RESULT_COUNT = count[result.RESULT_PEAK_ID];
			result.RESULT_INTENSITY = intensity[result.RESULT_PEAK_ID];
			result.RESULT_AVERAGE_INTENSITY = result.RESULT_INTENSITY / result.RESULT_COUNT;
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
			final int id = result.RESULT_PEAK_ID;
			if (intensity[id] != 0)
			{
				result.RESULT_INTENSITY = intensity[id];
				result.RESULT_MAX_VALUE = max[id];
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
			saddleHeight[result.RESULT_PEAK_ID] = result.RESULT_HIGHEST_SADDLE_VALUE;
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
			result.RESULT_COUNT_ABOVE_SADDLE = peakSize[result.RESULT_PEAK_ID];
			result.RESULT_INTENSITY_ABOVE_SADDLE = peakIntensity[result.RESULT_PEAK_ID];
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.FindFociBaseProcessor#reanalysePeak(int[], int[], int, gdsc.foci.FindFociSaddle,
	 * gdsc.foci.FindFociResult, boolean)
	 */
	protected void reanalysePeak(int[] maxima, int[] peakIdMap, int peakId, FindFociSaddle saddle,
			FindFociResult result, boolean updatePeakAboveSaddle)
	{
		if (updatePeakAboveSaddle)
		{
			int peakSize = 0;
			double peakIntensity = 0;
			final float saddleHeight = saddle.value;
			for (int i = maxima.length; i-- > 0;)
			{
				if (maxima[i] > 0)
				{
					maxima[i] = peakIdMap[maxima[i]];
					if (maxima[i] == peakId)
					{
						final float v = image[i];
						if (v > saddleHeight)
						{
							peakIntensity += v;
							peakSize++;
						}
					}
				}
			}

			result.RESULT_COUNT_ABOVE_SADDLE = peakSize;
			result.RESULT_INTENSITY_ABOVE_SADDLE = peakIntensity;
		}

		result.RESULT_SADDLE_NEIGHBOUR_ID = peakIdMap[saddle.id];
		result.RESULT_HIGHEST_SADDLE_VALUE = saddle.value;
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
