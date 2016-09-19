package gdsc.foci;

import java.util.ArrayList;
import java.util.Collections;

import gdsc.core.ij.Utils;
import gdsc.core.threshold.Histogram;
import ij.IJ;

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
 * Extends the FindFociIntProcessor to override the FindFociBaseProcessor methods with integer specific processing.
 */
public class FindFociOptimisedIntProcessor extends FindFociIntProcessor
{
	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.FindFociBaseProcessor#getSortedMaxPoints(java.lang.Object, int[], byte[], float, float)
	 */
	protected Coordinate[] getSortedMaxPoints(Object pixels, int[] maxima, byte[] types, float fGlobalMin,
			float fThreshold)
	{
		ArrayList<Coordinate> maxPoints = new ArrayList<Coordinate>(500);
		int[] pList = null; // working list for expanding local plateaus

		// Int processing
		final int globalMin = (int) fGlobalMin;
		final int threshold = (int) fThreshold;

		int id = 0;
		final int[] xyz = new int[3];

		//int pCount = 0;
		setPixels(pixels);
		for (int i = maxx_maxy_maxz; i-- > 0;)
		{
			if ((types[i] & (EXCLUDED | MAX_AREA | PLATEAU)) != 0)
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

			for (int d = dStart; d-- > 0;)
			{
				if (isInnerXYZ || (isInnerXY && isWithinZ(z, d)) || isWithinXYZ(x, y, z, d))
				{
					final int vNeighbor = image[i + offset[d]];
					if (vNeighbor > v)
					{
						isMax = false;
						break;
					}
					else if (vNeighbor == v)
					{
						// Neighbour is equal, this is a potential plateau maximum
						equalNeighbour = true;
					}
				}
			}

			if (isMax)
			{
				id++;
				if (id >= FindFoci.searchCapacity)
				{
					IJ.log("The number of potential maxima exceeds the search capacity: " + FindFoci.searchCapacity +
							". Try using a denoising/smoothing filter or increase the capacity.");
					return null;
				}

				if (equalNeighbour)
				{
					// Initialise the working list
					if (pList == null)
					{
						// Create an array to hold the rest of the points (worst case scenario for the maxima expansion)
						pList = new int[i + 1];
					}
					//pCount++;

					// Search the local area marking all equal neighbour points as maximum
					if (!expandMaximum(maxima, types, globalMin, threshold, i, v, id, maxPoints, pList))
					{
						// Not a true maximum, ignore this
						id--;
					}
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

			for (int d = dStart; d-- > 0;)
			{
				if (isInnerXYZ || (isInnerXY && isWithinZ(z1, d)) || isWithinXYZ(x1, y1, z1, d))
				{
					final int index2 = index1 + offset[d];
					if ((types[index2] & IGNORE) != 0)
					{
						// This has been done already, ignore this point
						continue;
					}

					final int v2 = image[index2];

					if (v2 > v0)
					{
						isPlateau = false;
						//break; // Cannot break as we want to label the entire plateau.
					}
					else if (v2 == v0)
					{
						// Add this to the search
						pList[listLen++] = index2;
						types[index2] |= LISTED | PLATEAU;
					}
				}
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
		{
			for (int i = listLen; i-- > 0;)
			{
				getXYZ(pList[i], xyz);
				xEqual += xyz[0];
				yEqual += xyz[1];
				zEqual += xyz[2];
				nEqual++;
			}
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
			{
				continue;
			}
			//levels++;

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
						int index = coordinates[p];
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
	protected int processLevel(byte[] types, int[] maxima, int levelStart, int levelNPoints, int[] coordinates,
			int background)
	{
		//int[] pList = new int[0]; // working list for expanding local plateaus
		int nChanged = 0;
		int nUnchanged = 0;
		final int[] xyz = new int[3];

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

			// TODO - Try out using a Sobel operator to assign the gradient direction. Follow the steepest gradient.

			int dMax = -1;
			int vMax = v;
			for (int d = dStart; d-- > 0;)
			{
				if (isInnerXYZ || (isInnerXY && isWithinZ(z, d)) || isWithinXYZ(x, y, z, d))
				{
					final int index2 = index + offset[d];
					final int vNeighbor = image[index2];
					if (vMax < vNeighbor) // Higher neighbour
					{
						vMax = vNeighbor;
						dMax = d;
					}
					else if (vMax == vNeighbor) // Equal neighbour
					{
						// Check if the neighbour is higher than this point (i.e. an equal higher neighbour has been found)
						if (v != vNeighbor)
						{
							// Favour flat edges over diagonals in the case of equal neighbours
							if (flatEdge[d])
							{
								dMax = d;
							}
						}
						// The neighbour is the same height, check if it is a maxima
						else if ((types[index2] & MAX_AREA) != 0)
						{
							if (dMax < 0) // Unassigned
							{
								dMax = d;
							}
							// Favour flat edges over diagonals in the case of equal neighbours
							else if (flatEdge[d])
							{
								dMax = d;
							}
						}
					}
				}
			}

			if (dMax < 0)
			{
				// This could happen if all neighbours are the same height and none are maxima.
				// Since plateau maxima should be handled in the initial maximum finding stage, any equal neighbours
				// should be processed eventually.
				coordinates[levelStart + (nUnchanged++)] = index;
				continue;
			}

			int index2 = index + offset[dMax];

			// TODO. 
			// The code below can be uncommented to flood fill a plateau with the first maxima that touches it.
			// However this can lead to striping artifacts where diagonals are at the same level but 
			// adjacent cells are not, e.g:
			// 1122
			// 1212
			// 2122
			// 1222
			// Consequently the code has been commented out and the default behaviour fills plateaus from the
			// edges inwards with a bias in the direction of the sweep across the pixels.
			// A better method may be to assign pixels to the nearest maxima using a distance measure 
			// (Euclidian, City-Block, etc). This would involve:
			// - Mark all plateau edges that touch a maxima 
			// - for each maxima edge:
			// -- Measure distance for each plateau point to the nearest touching edge
			// - Compare distance maps for each maxima and assign points to nearest maxima

			// Flood fill
			//          // A higher point has been found. Check if this position is a plateau
			//if ((types[index] & PLATEAU) == PLATEAU)
			//{
			//	IJ.log(String.format("Plateau merge to higher level: %d @ [%d,%d] : %d", image[index], x, y,
			//			image[index2]));
			//
			//	// Initialise the list to allow all points on this level to be processed. 
			//	if (pList.length < levelNPoints)
			//	{
			//		pList = new int[levelNPoints];
			//	}
			//
			//	expandPlateau(maxima, types, index, v, maxima[index2], pList);
			//}
			//else
			{
				types[index] |= MAX_AREA;
				maxima[index] = maxima[index2];
				nChanged++;
			}
		} // for pixel i

		//if (nUnchanged > 0)
		//	System.out.printf("nUnchanged = %d\n", nUnchanged);

		return nChanged;
	}

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
		final int[] peakThreshold = new int[nMaxima + 1];
		for (int i = 1; i < peakThreshold.length; i++)
		{
			peakThreshold[i] = (int) getTolerance(searchMethod, searchParameter, stats,
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

		for (FindFociResult result : resultsArray)
		{
			result.count = count[result.id];
			result.totalIntensity = (double) intensity[result.id];
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

		for (FindFociResult result : resultsArray)
		{
			final int id = result.id;
			if (intensity[id] != 0)
			{
				result.totalIntensity = (double) intensity[id];
				result.maxValue = max[id];
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.FindFociBaseProcessor#findSaddlePoints(java.lang.Object, byte[], java.util.ArrayList, int[],
	 * java.util.ArrayList)
	 */
	protected void findSaddlePoints(Object pixels, byte[] types, ArrayList<FindFociResult> resultsArray, int[] maxima,
			ArrayList<ArrayList<FindFociSaddle>> saddlePoints)
	{
		setPixels(pixels);

		// Initialise the saddle points
		final int nMaxima = resultsArray.size();
		for (int i = 0; i < nMaxima + 1; i++)
			saddlePoints.add(new ArrayList<FindFociSaddle>());

		final int maxPeakSize = getMaxPeakSize(resultsArray);
		final int[] pListI = new int[maxPeakSize]; // here we enter points starting from a maximum (index,value)
		final int[] pListV = new int[maxPeakSize];
		final int[] xyz = new int[3];

		/* Process all the maxima */
		for (FindFociResult result : resultsArray)
		{
			final int x0 = result.x;
			final int y0 = result.y;
			final int z0 = result.z;
			final int id = result.id;
			final int index0 = getIndex(x0, y0, z0);

			// List of saddle highest values with every other peak
			final int[] highestSaddleValue = new int[nMaxima + 1];
			//Arrays.fill(highestSaddleValue, NO_SADDLE_VALUE); // No saddle value will be zero

			types[index0] |= LISTED; // mark first point as listed
			int listI = 0; // index of current search element in the list
			int listLen = 1; // number of elements in the list

			// we create a list of connected points and start the list at the current maximum
			pListI[0] = index0;
			pListV[0] = image[index0];

			do
			{
				final int index1 = pListI[listI];
				final int v1 = pListV[listI];

				getXYZ(index1, xyz);
				final int x1 = xyz[0];
				final int y1 = xyz[1];
				final int z1 = xyz[2];

				// It is more likely that the z stack will be out-of-bounds.
				// Adopt the xy limit lookup and process z lookup separately

				final boolean isInnerXY = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);
				final boolean isInnerXYZ = (zlimit == 0) ? isInnerXY : isInnerXY && (z1 != 0 && z1 != zlimit);

				// Check for the highest neighbour
				for (int d = dStart; d-- > 0;)
				{
					if (isInnerXYZ || (isInnerXY && isWithinZ(z1, d)) || isWithinXYZ(x1, y1, z1, d))
					{
						// Get the coords
						final int index2 = index1 + offset[d];

						if ((types[index2] & IGNORE) != 0)
						{
							// This has been done already, ignore this point
							continue;
						}

						final int id2 = maxima[index2];

						if (id2 == id)
						{
							// Add this to the search
							pListI[listLen] = index2;
							pListV[listLen] = image[index2];
							listLen++;
							types[index2] |= LISTED;
						}
						else if (id2 != 0)
						{
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

							if (highestSaddleValue[id2] < minV)
							{
								highestSaddleValue[id2] = minV;
							}
						}
					}
				}

				listI++;

			} while (listI < listLen);

			for (int i = listLen; i-- > 0;)
			{
				final int index = pListI[i];
				types[index] &= ~LISTED; // reset attributes no longer needed
			}

			// Find the highest saddle
			int highestNeighbourPeakId = 0;
			int highestNeighbourValue = 0;
			int count = 0;
			for (int id2 = 1; id2 <= nMaxima; id2++)
			{
				if (highestSaddleValue[id2] != 0)
				{
					count++;
					// IJ.log("Peak saddle " + id + " -> " + id2 + " @ " + highestSaddleValue[id2]);
					if (highestNeighbourValue < highestSaddleValue[id2])
					{
						highestNeighbourValue = highestSaddleValue[id2];
						highestNeighbourPeakId = id2;
					}
				}
			}
			if (count != 0)
			{
				ArrayList<FindFociSaddle> saddles = saddlePoints.get(id);
				saddles.ensureCapacity(saddles.size() + count);
				for (int id2 = 1; id2 <= nMaxima; id2++)
				{
					if (highestSaddleValue[id2] != 0)
					{
						saddles.add(new FindFociSaddle(id2, highestSaddleValue[id2]));
					}
				}
			}

			// Set the saddle point
			if (highestNeighbourPeakId != 0)
			{
				result.saddleNeighbourId = highestNeighbourPeakId;
				result.highestSaddleValue = highestNeighbourValue;
			}
		} // for all maxima
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
		final long[] peakIntensity = new long[resultsArray.size() + 1];
		final int[] peakSize = new int[resultsArray.size() + 1];

		// Store all the saddle heights
		final int[] saddleHeight = new int[resultsArray.size() + 1];
		for (FindFociResult result : resultsArray)
		{
			saddleHeight[result.id] = (int) result.highestSaddleValue;
		}

		for (int i = maxima.length; i-- > 0;)
		{
			if (maxima[i] > 0)
			{
				final int v = image[i];
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
			result.intensityAboveSaddle = (double) peakIntensity[result.id];
		}
	}

	/**
	 * Reassign the maxima using the peak Id map and recounts all pixels above the saddle height.
	 * 
	 * @param maxima
	 * @param peakIdMap
	 * @param updatePeakAboveSaddle
	 */
	protected void reanalysePeak(int[] maxima, int[] peakIdMap, int peakId, FindFociSaddle saddle,
			FindFociResult result, boolean updatePeakAboveSaddle)
	{
		if (updatePeakAboveSaddle)
		{
			int peakSize = 0;
			long peakIntensity = 0;
			final float saddleHeight = saddle.value;
			for (int i = maxima.length; i-- > 0;)
			{
				if (maxima[i] > 0)
				{
					maxima[i] = peakIdMap[maxima[i]];
					if (maxima[i] == peakId)
					{
						final int v = image[i];
						if (v > saddleHeight)
						{
							peakIntensity += v;
							peakSize++;
						}
					}
				}
			}

			result.countAboveSaddle = peakSize;
			result.intensityAboveSaddle = (double) peakIntensity;
		}

		result.saddleNeighbourId = peakIdMap[saddle.id];
		result.highestSaddleValue = saddle.value;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.FindFociBaseProcessor#getIntensityAboveFloor(java.lang.Object, byte[], float)
	 */
	protected double getIntensityAboveFloor(Object pixels, byte[] types, final float fFloor)
	{
		setPixels(pixels);

		final int floor = (int) fFloor;

		long sum = 0;
		for (int i = types.length; i-- > 0;)
		{
			if ((types[i] & EXCLUDED) == 0)
			{
				final int v = image[i];
				if (v > floor)
					sum += (v - floor);
			}
		}
		return (double) sum;
	}
}
