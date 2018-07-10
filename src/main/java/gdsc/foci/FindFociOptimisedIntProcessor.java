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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import gdsc.core.ij.Utils;
import gdsc.core.threshold.Histogram;
import ij.IJ;

/**
 * Find the peak intensity regions of an image.
 * <P>
 * Extends the FindFociIntProcessor to override the FindFociBaseProcessor methods with integer specific processing.
 */
public class FindFociOptimisedIntProcessor extends FindFociIntProcessor
{
	/*
	 * (non-Javadoc)
	 *
	 * @see gdsc.foci.FindFociBaseProcessor#getSortedMaxPoints(java.lang.Object, int[], byte[], float, float)
	 */
	@Override
	protected Coordinate[] getSortedMaxPoints(Object pixels, int[] maxima, byte[] types, float fGlobalMin,
			float fThreshold)
	{
		final ArrayList<Coordinate> maxPoints = new ArrayList<>(500);
		int[] pList = null; // working list for expanding local plateaus

		// Int processing
		final int globalMin = (int) fGlobalMin;
		final int threshold = (int) fThreshold;

		int id = 0;
		final int[] xyz = new int[3];

		//int pCount = 0;
		setPixels(pixels);

		if (is2D())
			for (int i = maxx_maxy_maxz; i-- > 0;)
			{
				if ((types[i] & (EXCLUDED | MAX_AREA | PLATEAU | NOT_MAXIMUM)) != 0)
					continue;
				final int v = image[i];
				if (v < threshold)
					continue;
				if (v == globalMin)
					continue;

				getXY(i, xyz);

				final int x = xyz[0];
				final int y = xyz[1];

				/*
				 * check whether we have a local maximum.
				 */
				final boolean isInnerXY = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);
				boolean isMax = true, equalNeighbour = false;

				// It is more likely that the z stack will be out-of-bounds.
				// Adopt the xy limit lookup and process z lookup separately

				for (int d = 8; d-- > 0;)
					if (isInnerXY || isWithinXY(x, y, d))
					{
						final int vNeighbor = image[i + offset[d]];
						if (vNeighbor > v)
						{
							isMax = false;
							break;
						}
						else if (vNeighbor == v)
							// Neighbour is equal, this is a potential plateau maximum
							equalNeighbour = true;
						else
							// This is lower so cannot be a maxima
							types[i + offset[d]] |= NOT_MAXIMUM;
					}

				if (isMax)
				{
					id++;
					if (id >= FindFoci.searchCapacity)
					{
						IJ.log("The number of potential maxima exceeds the search capacity: " +
								FindFoci.searchCapacity +
								". Try using a denoising/smoothing filter or increase the capacity.");
						return null;
					}

					if (equalNeighbour)
					{
						// Initialise the working list
						if (pList == null)
						 // Create an array to hold the rest of the points (worst case scenario for the maxima expansion)
							pList = new int[i + 1];

						// Search the local area marking all equal neighbour points as maximum
						if (!expandMaximum(maxima, types, globalMin, threshold, i, v, id, maxPoints, pList))
							// Not a true maximum, ignore this
							id--;
					}
					else
					{
						types[i] |= MAXIMUM | MAX_AREA;
						maxima[i] = id;
						maxPoints.add(new Coordinate(x, y, 0, id, v));
					}
				}
			}
		else
			for (int i = maxx_maxy_maxz; i-- > 0;)
			{
				if ((types[i] & (EXCLUDED | MAX_AREA | PLATEAU | NOT_MAXIMUM)) != 0)
					continue;
				final int v = image[i];
				if (v < threshold)
					continue;
				if (v == globalMin)
					continue;

				getXYZ(i, xyz);

				final int x = xyz[0];
				final int y = xyz[1];
				final int z = xyz[2];

				/*
				 * check whether we have a local maximum.
				 */
				final boolean isInnerXY = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);
				final boolean isInnerXYZ = (zlimit == 0) ? isInnerXY : isInnerXY && (z != 0 && z != zlimit);
				boolean isMax = true, equalNeighbour = false;

				// It is more likely that the z stack will be out-of-bounds.
				// Adopt the xy limit lookup and process z lookup separately

				for (int d = 26; d-- > 0;)
					if (isInnerXYZ || (isInnerXY && isWithinZ(z, d)) || isWithinXYZ(x, y, z, d))
					{
						final int vNeighbor = image[i + offset[d]];
						if (vNeighbor > v)
						{
							isMax = false;
							break;
						}
						else if (vNeighbor == v)
							// Neighbour is equal, this is a potential plateau maximum
							equalNeighbour = true;
						else
							// This is lower so cannot be a maxima
							types[i + offset[d]] |= NOT_MAXIMUM;
					}

				if (isMax)
				{
					id++;
					if (id >= FindFoci.searchCapacity)
					{
						IJ.log("The number of potential maxima exceeds the search capacity: " +
								FindFoci.searchCapacity +
								". Try using a denoising/smoothing filter or increase the capacity.");
						return null;
					}

					if (equalNeighbour)
					{
						// Initialise the working list
						if (pList == null)
						 // Create an array to hold the rest of the points (worst case scenario for the maxima expansion)
							pList = new int[i + 1];

						// Search the local area marking all equal neighbour points as maximum
						if (!expandMaximum(maxima, types, globalMin, threshold, i, v, id, maxPoints, pList))
							// Not a true maximum, ignore this
							id--;
					}
					else
					{
						types[i] |= MAXIMUM | MAX_AREA;
						maxima[i] = id;
						maxPoints.add(new Coordinate(x, y, z, id, v));
					}
				}
			}

		//if (pCount > 0)
		//	System.out.printf("Plateau count = %d\n", pCount);

		if (Utils.isInterrupted())
			return null;

		for (int i = maxx_maxy_maxz; i-- > 0;)
			types[i] &= ~NOT_MAXIMUM; // reset attributes no longer needed

		Collections.sort(maxPoints);

		// Build a map between the original id and the new id following the sort
		final int[] idMap = new int[maxPoints.size() + 1];

		// Label the points
		for (int i = 0; i < maxPoints.size(); i++)
		{
			final int newId = (i + 1);
			final int oldId = maxPoints.get(i).id;
			idMap[oldId] = newId;
			maxPoints.get(i).id = newId;
		}

		reassignMaxima(maxima, idMap);

		return maxPoints.toArray(new Coordinate[maxPoints.size()]);
	}

	/**
	 * Searches from the specified point to find all coordinates of the same value and determines the centre of the
	 * plateau maximum.
	 *
	 * @param maxima
	 *            the maxima
	 * @param types
	 *            the types
	 * @param globalMin
	 *            the global min
	 * @param threshold
	 *            the threshold
	 * @param index0
	 *            the index0
	 * @param v0
	 *            the v0
	 * @param id
	 *            the id
	 * @param maxPoints
	 *            the max points
	 * @param pList
	 *            the list
	 * @return True if this is a true plateau, false if the plateau reaches a higher point
	 */
	protected boolean expandMaximum(int[] maxima, byte[] types, int globalMin, int threshold, int index0, int v0,
			int id, ArrayList<Coordinate> maxPoints, int[] pList)
	{
		types[index0] |= LISTED | PLATEAU; // mark first point as listed
		int listI = 0; // index of current search element in the list
		int listLen = 1; // number of elements in the list

		// we create a list of connected points and start the list at the current maximum
		pList[listI] = index0;

		// Calculate the center of plateau
		boolean isPlateau = true;
		final int[] xyz = new int[3];

		if (is2D())
			do
			{
				final int index1 = pList[listI];
				getXY(index1, xyz);
				final int x1 = xyz[0];
				final int y1 = xyz[1];

				// It is more likely that the z stack will be out-of-bounds.
				// Adopt the xy limit lookup and process z lookup separately

				final boolean isInnerXY = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);

				for (int d = 8; d-- > 0;)
					if (isInnerXY || isWithinXY(x1, y1, d))
					{
						final int index2 = index1 + offset[d];
						if ((types[index2] & IGNORE) != 0)
							// This has been done already, ignore this point
							continue;

						final int v2 = image[index2];

						if (v2 > v0)
							isPlateau = false;
							//break; // Cannot break as we want to label the entire plateau.
						else if (v2 == v0)
						{
							// Add this to the search
							pList[listLen++] = index2;
							types[index2] |= LISTED | PLATEAU;
						}
						else
							types[index2] |= NOT_MAXIMUM;
					}

				listI++;

			} while (listI < listLen && isPlateau);
		else
			do
			{
				final int index1 = pList[listI];
				getXYZ(index1, xyz);
				final int x1 = xyz[0];
				final int y1 = xyz[1];
				final int z1 = xyz[2];

				// It is more likely that the z stack will be out-of-bounds.
				// Adopt the xy limit lookup and process z lookup separately

				final boolean isInnerXY = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);
				final boolean isInnerXYZ = (zlimit == 0) ? isInnerXY : isInnerXY && (z1 != 0 && z1 != zlimit);

				for (int d = 26; d-- > 0;)
					if (isInnerXYZ || (isInnerXY && isWithinZ(z1, d)) || isWithinXYZ(x1, y1, z1, d))
					{
						final int index2 = index1 + offset[d];
						if ((types[index2] & IGNORE) != 0)
							// This has been done already, ignore this point
							continue;

						final int v2 = image[index2];

						if (v2 > v0)
							isPlateau = false;
							//break; // Cannot break as we want to label the entire plateau.
						else if (v2 == v0)
						{
							// Add this to the search
							pList[listLen++] = index2;
							types[index2] |= LISTED | PLATEAU;
						}
						else
							types[index2] |= NOT_MAXIMUM;
					}

				listI++;

			} while (listI < listLen && isPlateau);

		// IJ.log("Potential plateau "+ x0 + ","+y0+","+z0+" : "+listLen);

		// Find the centre
		double xEqual = 0;
		double yEqual = 0;
		double zEqual = 0;
		int nEqual = 0;
		if (isPlateau)
			for (int i = listLen; i-- > 0;)
			{
				getXYZ(pList[i], xyz);
				xEqual += xyz[0];
				yEqual += xyz[1];
				zEqual += xyz[2];
				nEqual++;
			}
		xEqual /= nEqual;
		yEqual /= nEqual;
		zEqual /= nEqual;

		double dMax = Double.MAX_VALUE;
		int iMax = 0;

		// Calculate the maxima origin as the closest pixel to the centre-of-mass
		for (int i = listLen; i-- > 0;)
		{
			final int index = pList[i];
			types[index] &= ~LISTED; // reset attributes no longer needed

			if (isPlateau)
			{
				getXYZ(index, xyz);

				final int x = xyz[0];
				final int y = xyz[1];
				final int z = xyz[2];

				final double d = (xEqual - x) * (xEqual - x) + (yEqual - y) * (yEqual - y) +
						(zEqual - z) * (zEqual - z);

				if (d < dMax)
				{
					dMax = d;
					iMax = i;
				}

				types[index] |= MAX_AREA;
				maxima[index] = id;
			}
		}

		// Assign the maximum
		if (isPlateau)
		{
			final int index = pList[iMax];
			types[index] |= MAXIMUM;
			maxPoints.add(new Coordinate(index, id, v0));
		}

		return isPlateau;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see gdsc.foci.FindFociBaseProcessor#assignPointsToMaxima(java.lang.Object, gdsc.foci.Histogram, byte[],
	 * gdsc.foci.FindFociStatistics, int[])
	 */
	@Override
	protected void assignPointsToMaxima(Object pixels, Histogram hist, byte[] types, FindFociStatistics stats,
			int[] maxima)
	{
		setPixels(pixels);
		// This is modified so clone it
		final int[] histogram = hist.h.clone();

		final int minBin = getBackgroundBin(hist, stats.background);
		final int maxBin = hist.maxBin;

		// Create an array with the coordinates of all points between the threshold value and the max-1 value
		// (since maximum values should already have been processed)
		int arraySize = 0;
		for (int bin = minBin; bin < maxBin; bin++)
			arraySize += histogram[bin];

		if (arraySize == 0)
			return;

		final int[] coordinates = new int[arraySize]; // from pixel coordinates, low bits x, high bits y
		int highestBin = 0;
		int offset = 0;
		final int[] levelStart = new int[maxBin + 1];
		for (int bin = minBin; bin < maxBin; bin++)
		{
			levelStart[bin] = offset;
			offset += histogram[bin];
			if (histogram[bin] != 0)
				highestBin = bin;
		}
		final int[] levelOffset = new int[highestBin + 1];
		for (int i = types.length; i-- > 0;)
		{
			if ((types[i] & EXCLUDED) != 0)
				continue;

			final int v = image[i];
			if (v >= minBin && v < maxBin)
			{
				offset = levelStart[v] + levelOffset[v];
				coordinates[offset] = i;
				levelOffset[v]++;
			}
		}

		// Process down through the levels
		int processedLevel = 0; // Counter incremented when work is done
		//int levels = 0;
		for (int level = highestBin; level >= minBin; level--)
		{
			int remaining = histogram[level];

			if (remaining == 0)
			 continue;

			// Use the idle counter to ensure that we exit the loop if no pixels have been processed for two cycles
			while (remaining > 0)
			{
				processedLevel++;
				final int n = processLevel(types, maxima, levelStart[level], remaining, coordinates, minBin);
				remaining -= n; // number of points processed

				// If nothing was done then stop
				if (n == 0)
					break;
			}

			if ((processedLevel % 64 == 0) && Utils.isInterrupted())
				return;

			if (remaining > 0 && level > minBin)
			{
				// any pixels that we have not reached?
				// It could happen if there is a large area of flat pixels => no local maxima.
				// Add to the next level.
				//IJ.log("Unprocessed " + remaining + " @level = " + level);

				int nextLevel = level; // find the next level to process
				do
					nextLevel--;
				while (nextLevel > 1 && histogram[nextLevel] == 0);

				// Add all unprocessed pixels of this level to the tasklist of the next level.
				// This could make it slow for some images, however.
				if (nextLevel > 0)
				{
					int newNextLevelEnd = levelStart[nextLevel] + histogram[nextLevel];
					for (int i = 0, p = levelStart[level]; i < remaining; i++, p++)
					{
						final int index = coordinates[p];
						coordinates[newNextLevelEnd++] = index;
					}
					// tasklist for the next level to process becomes longer by this:
					histogram[nextLevel] = newNextLevelEnd - levelStart[nextLevel];
				}
			}
		}

		//int nP = 0;
		//for (byte b : types)
		//	if ((b & PLATEAU) == PLATEAU)
		//		nP++;
		//System.out.printf("Processed %d levels [%d steps], %d plateau points\n", levels, processedLevel, nP);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see gdsc.foci.FindFociBaseProcessor#processLevel(byte[], int[], int, int, int[], int)
	 */
	@Override
	protected int processLevel(byte[] types, int[] maxima, int levelStart, int levelNPoints, int[] coordinates,
			int background)
	{
		//int[] pList = new int[0]; // working list for expanding local plateaus
		int nChanged = 0;
		int nUnchanged = 0;
		final int[] xyz = new int[3];

		if (is2D())
			for (int i = 0, p = levelStart; i < levelNPoints; i++, p++)
			{
				final int index = coordinates[p];

				if ((types[index] & (EXCLUDED | MAX_AREA)) != 0)
				{
					// This point can be ignored
					nChanged++;
					continue;
				}

				getXY(index, xyz);

				// Extract the point coordinate
				final int x = xyz[0];
				final int y = xyz[1];

				final int v = image[index];

				// It is more likely that the z stack will be out-of-bounds.
				// Adopt the xy limit lookup and process z lookup separately
				final boolean isInnerXY = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);

				// Check for the highest neighbour

				int dMax = -1;
				int vMax = v;
				for (int d = 8; d-- > 0;)
					if (isInnerXY || isWithinXY(x, y, d))
					{
						final int index2 = index + offset[d];
						final int vNeighbor = image[index2];
						if (vMax < vNeighbor) // Higher neighbour
						{
							vMax = vNeighbor;
							dMax = d;
						}
						else if (vMax == vNeighbor)
							// Check if the neighbour is higher than this point (i.e. an equal higher neighbour has been found)
							if (v != vNeighbor)
							{
								// Favour flat edges over diagonals in the case of equal neighbours
								if (flatEdge[d])
									dMax = d;
							}
							// The neighbour is the same height, check if it is a maxima
							else if ((types[index2] & MAX_AREA) != 0)
								if (dMax < 0)
									dMax = d;
								else if (flatEdge[d])
									dMax = d;
					}

				if (dMax < 0)
				{
					// This could happen if all neighbours are the same height and none are maxima.
					// Since plateau maxima should be handled in the initial maximum finding stage, any equal neighbours
					// should be processed eventually.
					coordinates[levelStart + (nUnchanged++)] = index;
					continue;
				}

				final int index2 = index + offset[dMax];

				types[index] |= MAX_AREA;
				maxima[index] = maxima[index2];
				nChanged++;
			} // for pixel i
		else
			for (int i = 0, p = levelStart; i < levelNPoints; i++, p++)
			{
				final int index = coordinates[p];

				if ((types[index] & (EXCLUDED | MAX_AREA)) != 0)
				{
					// This point can be ignored
					nChanged++;
					continue;
				}

				getXYZ(index, xyz);

				// Extract the point coordinate
				final int x = xyz[0];
				final int y = xyz[1];
				final int z = xyz[2];

				final int v = image[index];

				// It is more likely that the z stack will be out-of-bounds.
				// Adopt the xy limit lookup and process z lookup separately
				final boolean isInnerXY = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);
				final boolean isInnerXYZ = (zlimit == 0) ? isInnerXY : isInnerXY && (z != 0 && z != zlimit);

				// Check for the highest neighbour

				int dMax = -1;
				int vMax = v;
				for (int d = 26; d-- > 0;)
					if (isInnerXYZ || (isInnerXY && isWithinZ(z, d)) || isWithinXYZ(x, y, z, d))
					{
						final int index2 = index + offset[d];
						final int vNeighbor = image[index2];
						if (vMax < vNeighbor) // Higher neighbour
						{
							vMax = vNeighbor;
							dMax = d;
						}
						else if (vMax == vNeighbor)
							// Check if the neighbour is higher than this point (i.e. an equal higher neighbour has been found)
							if (v != vNeighbor)
							{
								// Favour flat edges over diagonals in the case of equal neighbours
								if (flatEdge[d])
									dMax = d;
							}
							// The neighbour is the same height, check if it is a maxima
							else if ((types[index2] & MAX_AREA) != 0)
								if (dMax < 0)
									dMax = d;
								else if (flatEdge[d])
									dMax = d;
					}

				if (dMax < 0)
				{
					// This could happen if all neighbours are the same height and none are maxima.
					// Since plateau maxima should be handled in the initial maximum finding stage, any equal neighbours
					// should be processed eventually.
					coordinates[levelStart + (nUnchanged++)] = index;
					continue;
				}

				final int index2 = index + offset[dMax];

				types[index] |= MAX_AREA;
				maxima[index] = maxima[index2];
				nChanged++;
			} // for pixel i

		return nChanged;
	}

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
		final int[] peakThreshold = new int[nMaxima + 1];
		for (int i = 1; i < peakThreshold.length; i++)
			peakThreshold[i] = (int) getTolerance(searchMethod, searchParameter, stats, resultsArray[i - 1].maxValue);

		for (int i = maxima.length; i-- > 0;)
		{
			final int id = maxima[i];
			if (id != 0)
				if (image[i] < peakThreshold[id])
				{
					// Unset this pixel as part of the peak
					maxima[i] = 0;
					types[i] &= ~MAX_AREA;
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
		final long[] intensity = new long[nMaxima + 1];

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
		final long[] intensity = new long[originalNumberOfPeaks + 1];
		final int[] max = new int[originalNumberOfPeaks + 1];

		for (int i = maxima.length; i-- > 0;)
		{
			final int id = maxima[i];
			if (id != 0)
			{
				final int v = image[i];
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

	private int[] highestSaddleValues = null;

	/**
	 * Set up processing for {@link #findHighestSaddleValues(FindFociResult, int[], byte[], ArrayList)}
	 *
	 * @param nMaxima
	 *            the number of maxima
	 */
	@Override
	protected void setupFindHighestSaddleValues(int nMaxima)
	{
		nMaxima++;
		if (highestSaddleValues == null || highestSaddleValues.length < nMaxima)
			highestSaddleValues = new int[nMaxima];
	}

	@Override
	protected void finaliseFindHighestSaddleValues()
	{
	}

	/**
	 * Find highest saddle values for each maxima touching the given result.
	 *
	 * @param result
	 *            the result
	 * @param maxima
	 *            the maxima
	 * @param types
	 *            the types
	 * @param highestSaddleValues
	 *            the highest saddle values
	 */
	@Override
	protected void findHighestSaddleValues(FindFociResult result, int[] maxima, byte[] types,
			FindFociSaddleList[] saddlePoints)
	{
		Arrays.fill(highestSaddleValues, 0);
		final int id = result.id;

		final boolean alwaysInnerY = (result.miny != 0 && result.maxy != maxy);
		final boolean alwaysInnerX = (result.minx != 0 && result.maxx != maxx);

		if (is2D())
			for (int y = result.miny; y < result.maxy; y++)
			{
				final boolean isInnerY = alwaysInnerY || (y != 0 && y != ylimit);
				for (int x = result.minx, index1 = getIndex(result.minx, y); x < result.maxx; x++, index1++)
				{
					if ((types[index1] & SADDLE_SEARCH) == 0)
						continue;
					if (maxima[index1] == id)
					{
						final int v1 = image[index1];

						final boolean isInnerXY = isInnerY && (alwaysInnerX || (x != 0 && x != xlimit));

						for (int d = 8; d-- > 0;)
							if (isInnerXY || isWithinXY(x, y, d))
							{
								// Get the coords
								final int index2 = index1 + offset[d];
								final int id2 = maxima[index2];

								if (id2 == id || id2 == 0)
									// Same maxima, or no maxima, do nothing
									continue;

								// This is another peak, see if it a saddle highpoint
								final int v2 = image[index2];

								// Take the lower of the two points as the saddle
								final int minV;
								if (v1 < v2)
								{
									types[index1] |= SADDLE;
									minV = v1;
								}
								else
								{
									types[index2] |= SADDLE;
									minV = v2;
								}

								if (highestSaddleValues[id2] < minV)
									highestSaddleValues[id2] = minV;
							}
					}
				}
			}
		else
			for (int z = result.minz; z < result.maxz; z++)
			{
				final boolean isInnerZ = (zlimit == 0) ? true : (z != 0 && z != zlimit);
				for (int y = result.miny; y < result.maxy; y++)
				{
					final boolean isInnerY = alwaysInnerY || (y != 0 && y != ylimit);
					for (int x = result.minx, index1 = getIndex(result.minx, y, z); x < result.maxx; x++, index1++)
					{
						if ((types[index1] & SADDLE_SEARCH) == 0)
							continue;
						if (maxima[index1] == id)
						{
							final int v1 = image[index1];

							final boolean isInnerXY = isInnerY && (alwaysInnerX || (x != 0 && x != xlimit));
							final boolean isInnerXYZ = isInnerXY && isInnerZ;

							for (int d = 26; d-- > 0;)
								if (isInnerXYZ || (isInnerXY && isWithinZ(z, d)) || isWithinXYZ(x, y, z, d))
								{
									// Get the coords
									final int index2 = index1 + offset[d];
									final int id2 = maxima[index2];

									if (id2 == id || id2 == 0)
										// Same maxima, or no maxima, do nothing
										continue;

									// This is another peak, see if it a saddle highpoint
									final int v2 = image[index2];

									// Take the lower of the two points as the saddle
									final int minV;
									if (v1 < v2)
									{
										types[index1] |= SADDLE;
										minV = v1;
									}
									else
									{
										types[index2] |= SADDLE;
										minV = v2;
									}

									if (highestSaddleValues[id2] < minV)
										highestSaddleValues[id2] = minV;
								}
						}
					}
				}
			}

		// Find the highest saddle
		int highestNeighbourPeakId = 0;
		float highestNeighbourValue = 0;
		int count = 0;
		for (int id2 = 1; id2 < highestSaddleValues.length; id2++)
			if (highestSaddleValues[id2] != 0)
			{
				count++;
				// log("Peak saddle " + id + " -> " + id2 + " @ " + highestSaddleValue[id2]);
				if (highestNeighbourValue < highestSaddleValues[id2])
				{
					highestNeighbourValue = highestSaddleValues[id2];
					highestNeighbourPeakId = id2;
				}
			}
		if (count != 0)
		{
			final FindFociSaddle[] saddles = new FindFociSaddle[count];
			for (int id2 = 1, c = 0; id2 < highestSaddleValues.length; id2++)
				if (highestSaddleValues[id2] != 0)
					saddles[c++] = new FindFociSaddle(id2, highestSaddleValues[id2]);
			Arrays.sort(saddles);
			saddlePoints[id] = new FindFociSaddleList(saddles);
		}
		else
			saddlePoints[id] = new FindFociSaddleList();

		// Set the saddle point
		if (highestNeighbourPeakId != 0)
		{
			result.saddleNeighbourId = highestNeighbourPeakId;
			result.highestSaddleValue = highestNeighbourValue;
		}
	}

	/**
	 * Find the size and intensity of peaks above their saddle heights.
	 *
	 * @param resultsArray
	 *            the results array
	 * @param maxima
	 *            the maxima
	 */
	@Override
	protected void analyseNonContiguousPeaks(FindFociResult[] resultsArray, int[] maxima)
	{
		// Create an array of the size/intensity of each peak above the highest saddle
		final long[] peakIntensity = new long[resultsArray.length + 1];
		final int[] peakSize = new int[resultsArray.length + 1];

		// Store all the saddle heights
		final int[] saddleHeight = new int[resultsArray.length + 1];
		for (int i = 0; i < resultsArray.length; i++)
		{
			final FindFociResult result = resultsArray[i];
			saddleHeight[result.id] = (int) result.highestSaddleValue;
		}

		for (int i = maxima.length; i-- > 0;)
		{
			final int id = maxima[i];
			if (id != 0)
			{
				final int v = image[i];
				if (v > saddleHeight[id])
				{
					peakIntensity[id] += v;
					peakSize[id]++;
				}
			}
		}

		for (int i = 0; i < resultsArray.length; i++)
		{
			final FindFociResult result = resultsArray[i];
			result.countAboveSaddle = peakSize[result.id];
			result.intensityAboveSaddle = peakIntensity[result.id];
		}
	}

	/**
	 * Searches from the specified maximum to find all contiguous points above the saddle
	 *
	 * @param maxima
	 *            the maxima
	 * @param types
	 *            the types
	 * @param result
	 *            the result
	 * @param pList
	 *            the list
	 * @return True if this is a true plateau, false if the plateau reaches a higher point
	 */
	@Override
	protected int[] analyseContiguousPeak(int[] maxima, byte[] types, FindFociResult result, int[] pList)
	{
		final int index0 = getIndex(result.x, result.y, result.z);
		final int peakId = result.id;
		final int v0 = (int) result.highestSaddleValue;

		if (pList.length < result.count)
			pList = new int[result.count];

		types[index0] |= LISTED; // mark first point as listed
		int listI = 0; // index of current search element in the list
		int listLen = 1; // number of elements in the list

		// we create a list of connected points and start the list at the current maximum
		pList[listI] = index0;

		final int[] xyz = new int[3];

		long sum = 0;

		if (is2D())
			do
			{
				final int index1 = pList[listI];
				getXY(index1, xyz);
				final int x1 = xyz[0];
				final int y1 = xyz[1];

				// It is more likely that the z stack will be out-of-bounds.
				// Adopt the xy limit lookup and process z lookup separately

				final boolean isInnerXY = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);

				for (int d = 8; d-- > 0;)
					if (isInnerXY || isWithinXY(x1, y1, d))
					{
						final int index2 = index1 + offset[d];
						if ((types[index2] & LISTED) != 0 || maxima[index2] != peakId)
							// Different peak or already done
							continue;

						final int v1 = image[index2];
						if (v1 > v0)
						{
							pList[listLen++] = index2;
							types[index2] |= LISTED;
							sum += v1;
						}
					}

				listI++;

			} while (listI < listLen);
		else
			do
			{
				final int index1 = pList[listI];
				getXYZ(index1, xyz);
				final int x1 = xyz[0];
				final int y1 = xyz[1];
				final int z1 = xyz[2];

				// It is more likely that the z stack will be out-of-bounds.
				// Adopt the xy limit lookup and process z lookup separately

				final boolean isInnerXY = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);
				final boolean isInnerXYZ = (zlimit == 0) ? isInnerXY : isInnerXY && (z1 != 0 && z1 != zlimit);

				for (int d = 26; d-- > 0;)
					if (isInnerXYZ || (isInnerXY && isWithinZ(z1, d)) || isWithinXYZ(x1, y1, z1, d))
					{
						final int index2 = index1 + offset[d];
						if ((types[index2] & LISTED) != 0 || maxima[index2] != peakId)
							// Different peak or already done
							continue;

						final int v1 = image[index2];
						if (v1 > v0)
						{
							pList[listLen++] = index2;
							types[index2] |= LISTED;
							sum += v1;
						}
					}

				listI++;

			} while (listI < listLen);

		result.countAboveSaddle = listI;
		result.intensityAboveSaddle = sum;

		return pList;
	}

	/**
	 * Searches from the specified maximum to find all contiguous points above the saddle.
	 *
	 * @param maxima
	 *            the maxima
	 * @param types
	 *            the types
	 * @param result
	 *            the result
	 * @param pList
	 *            the list
	 * @param peakIdMap
	 *            the peak id map
	 * @param peakId
	 *            the peak id
	 * @return True if this is a true plateau, false if the plateau reaches a higher point
	 */
	@Override
	protected int[] analyseContiguousPeak(int[] maxima, byte[] types, FindFociResult result, int[] pList,
			final int[] peakIdMap, final int peakId)
	{
		final int index0 = getIndex(result.x, result.y, result.z);
		final int v0 = (int) result.highestSaddleValue;

		if (pList.length < result.count)
			pList = new int[result.count];

		types[index0] |= LISTED; // mark first point as listed
		int listI = 0; // index of current search element in the list
		int listLen = 1; // number of elements in the list

		// we create a list of connected points and start the list at the current maximum
		pList[listI] = index0;

		final int[] xyz = new int[3];

		long sum = 0;

		if (is2D())
			do
			{
				final int index1 = pList[listI];
				getXY(index1, xyz);
				final int x1 = xyz[0];
				final int y1 = xyz[1];

				// It is more likely that the z stack will be out-of-bounds.
				// Adopt the xy limit lookup and process z lookup separately

				final boolean isInnerXY = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);

				for (int d = 8; d-- > 0;)
					if (isInnerXY || isWithinXY(x1, y1, d))
					{
						final int index2 = index1 + offset[d];
						if ((types[index2] & LISTED) != 0)
							// Already done
							continue;
						if (peakIdMap[maxima[index2]] != peakId)
							// Different peak
							continue;

						final int v1 = image[index2];
						if (v1 > v0)
						{
							pList[listLen++] = index2;
							types[index2] |= LISTED;
							sum += v1;
						}
					}

				listI++;

			} while (listI < listLen);
		else
			do
			{
				final int index1 = pList[listI];
				getXYZ(index1, xyz);
				final int x1 = xyz[0];
				final int y1 = xyz[1];
				final int z1 = xyz[2];

				// It is more likely that the z stack will be out-of-bounds.
				// Adopt the xy limit lookup and process z lookup separately

				final boolean isInnerXY = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);
				final boolean isInnerXYZ = (zlimit == 0) ? isInnerXY : isInnerXY && (z1 != 0 && z1 != zlimit);

				for (int d = 26; d-- > 0;)
					if (isInnerXYZ || (isInnerXY && isWithinZ(z1, d)) || isWithinXYZ(x1, y1, z1, d))
					{
						final int index2 = index1 + offset[d];
						if ((types[index2] & LISTED) != 0)
							// Already done
							continue;
						if (peakIdMap[maxima[index2]] != peakId)
							// Different peak
							continue;

						final int v1 = image[index2];
						if (v1 > v0)
						{
							pList[listLen++] = index2;
							types[index2] |= LISTED;
							sum += v1;
						}
					}

				listI++;

			} while (listI < listLen);

		for (int i = listLen; i-- > 0;)
			types[pList[i]] &= ~LISTED; // reset attributes no longer needed

		result.countAboveSaddle = listI;
		result.intensityAboveSaddle = sum;

		return pList;
	}

	/**
	 * Find the size and intensity of peaks above their saddle heights.
	 */
	protected void analysePeaksWithBounds(FindFociResult[] resultsArray, Object pixels, int[] maxima,
			FindFociStatistics stats)
	{
		setPixels(pixels);

		// Create an array of the size/intensity of each peak above the highest saddle
		final long[] peakIntensity = new long[resultsArray.length + 1];
		final int[] peakSize = new int[resultsArray.length + 1];

		// Store all the saddle heights
		final int[] saddleHeight = new int[resultsArray.length + 1];
		for (int i = 0; i < resultsArray.length; i++)
		{
			final FindFociResult result = resultsArray[i];
			saddleHeight[result.id] = (int) result.highestSaddleValue;
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
						final int v = image[i];
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

		for (int i = 0; i < resultsArray.length; i++)
		{
			final FindFociResult result = resultsArray[i];
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
	@Override
	protected void computeIntensityAboveSaddle(final int[] maxima, final int[] peakIdMap, final int peakId,
			final FindFociResult result, final float saddleHeight)
	{
		int peakSize = 0;
		long peakIntensity = 0;

		// Search using the bounds
		for (int z = result.minz; z < result.maxz; z++)
			for (int y = result.miny; y < result.maxy; y++)
				for (int x = result.minx, i = getIndex(result.minx, y, z); x < result.maxx; x++, i++)
				{
					final int id = maxima[i];
					if (id != 0 && peakIdMap[id] == peakId)
					{
						final int v = image[i];
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
	protected double getIntensityAboveFloor(Object pixels, byte[] types, final float fFloor)
	{
		setPixels(pixels);

		final int floor = (int) fFloor;

		long sum = 0;
		for (int i = types.length; i-- > 0;)
			if ((types[i] & EXCLUDED) == 0)
			{
				final int v = image[i];
				if (v > floor)
					sum += (v - floor);
			}
		return sum;
	}
}
