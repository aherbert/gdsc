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
	@Override
	protected void pruneMaxima(Object pixels, byte[] types, int searchMethod, double searchParameter,
			FindFociStatistics stats, FindFociResult[] resultsArray, int[] maxima)
	{
		setPixels(pixels);

		// Build an array containing the threshold for each peak.
		// Note that maxima are numbered from 1
		final int nMaxima = resultsArray.length;
		final float[] peakThreshold = new float[nMaxima + 1];
		for (int i = 1; i < peakThreshold.length; i++)
		{
			peakThreshold[i] = getTolerance(searchMethod, searchParameter, stats, resultsArray[i - 1].maxValue);
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
	@Override
	protected void calculateInitialResults(Object pixels, int[] maxima, FindFociResult[] resultsArray)
	{
		setPixels(pixels);
		final int nMaxima = resultsArray.length;

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

		for (int i = 0; i < resultsArray.length; i++)
		{
			final FindFociResult result = resultsArray[i];
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
	@Override
	protected void calculateNativeResults(Object pixels, int[] maxima, FindFociResult[] resultsArray,
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

		for (int i = 0; i < resultsArray.length; i++)
		{
			final FindFociResult result = resultsArray[i];
			final int id = result.id;
			if (intensity[id] != 0)
			{
				result.totalIntensity = intensity[id];
				result.maxValue = max[id];
			}
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
	@Override
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
	@Override
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
