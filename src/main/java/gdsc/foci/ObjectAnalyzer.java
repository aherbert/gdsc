package gdsc.foci;

/*----------------------------------------------------------------------------- 
 * GDSC Plugins for ImageJ
 * 
 * Copyright (C) 2011 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

import ij.process.ImageProcessor;

import java.util.Arrays;

/**
 * Find objects defined by contiguous pixels of the same value
 */
public class ObjectAnalyzer
{
	private ImageProcessor ip;
	private boolean eightConnected;
	private int[] objectMask;
	private int maxObject;

	public ObjectAnalyzer(ImageProcessor ip)
	{
		this(ip, false);
	}

	public ObjectAnalyzer(ImageProcessor ip, boolean eightConnected)
	{
		this.ip = ip;
		this.eightConnected = eightConnected;

		analyseObjects();
	}

	/**
	 * @return A pixel array containing the object number for each pixel in the input image
	 */
	public int[] getObjectMask()
	{
		return objectMask;
	}

	/**
	 * @return The maximum object number
	 */
	public int getMaxObject()
	{
		return maxObject;
	}

	private void analyseObjects()
	{
		final int[] maskImage = new int[ip.getPixelCount()];
		for (int i = 0; i < maskImage.length; i++)
			maskImage[i] = ip.get(i);

		// Perform a search for objects. 
		// Expand any non-zero pixel value into all 8-connected pixels of the same value.
		objectMask = new int[maskImage.length];
		maxObject = 0;

		int[] pList = new int[100];
		initialise(ip);

		for (int i = 0; i < maskImage.length; i++)
		{
			// Look for non-zero values that are not already in an object
			if (maskImage[i] != 0 && objectMask[i] == 0)
			{
				maxObject++;
				pList = expandObjectXY(maskImage, objectMask, i, maxObject, pList);
			}
		}
	}

	/**
	 * Searches from the specified point to find all coordinates of the same value and assigns them to given maximum ID.
	 */
	private int[] expandObjectXY(final int[] image, final int[] objectMask, final int index0, final int id, int[] pList)
	{
		objectMask[index0] = id; // mark first point
		int listI = 0; // index of current search element in the list
		int listLen = 1; // number of elements in the list
		final int neighbours = (eightConnected) ? 8 : 4;

		// we create a list of connected points and start the list at the current point
		pList[listI] = index0;

		final int v0 = image[index0];

		do
		{
			final int index1 = pList[listI];
			final int x1 = index1 % maxx;
			final int y1 = index1 / maxx;

			boolean isInnerXY = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);

			for (int d = neighbours; d-- > 0;)
			{
				if (isInnerXY || isWithinXY(x1, y1, d))
				{
					int index2 = index1 + offset[d];
					if (objectMask[index2] != 0)
					{
						// This has been done already, ignore this point
						continue;
					}

					int v2 = image[index2];

					if (v2 == v0)
					{
						// Add this to the search
						pList[listLen++] = index2;
						objectMask[index2] = id;
						if (pList.length == listLen)
							pList = Arrays.copyOf(pList, (int) (listLen * 1.5));
					}
				}
			}

			listI++;

		} while (listI < listLen);

		return pList;
	}

	private int maxx, maxy;
	private int xlimit, ylimit;
	private int[] offset;
	private final int[] DIR_X_OFFSET = new int[] { 0, 1, 0, -1, 1, 1, -1, -1 };
	private final int[] DIR_Y_OFFSET = new int[] { -1, 0, 1, 0, -1, 1, 1, -1 };

	/**
	 * Creates the direction offset tables.
	 */
	private void initialise(ImageProcessor ip)
	{
		maxx = ip.getWidth();
		maxy = ip.getHeight();

		xlimit = maxx - 1;
		ylimit = maxy - 1;

		// Create the offset table (for single array 3D neighbour comparisons)
		offset = new int[DIR_X_OFFSET.length];
		for (int d = offset.length; d-- > 0;)
		{
			offset[d] = maxx * DIR_Y_OFFSET[d] + DIR_X_OFFSET[d];
		}
	}

	/**
	 * returns whether the neighbour in a given direction is within the image. NOTE: it is assumed that the pixel x,y
	 * itself is within the image! Uses class variables xlimit, ylimit: (dimensions of the image)-1
	 * 
	 * @param x
	 *            x-coordinate of the pixel that has a neighbour in the given direction
	 * @param y
	 *            y-coordinate of the pixel that has a neighbour in the given direction
	 * @param direction
	 *            the direction from the pixel towards the neighbour
	 * @return true if the neighbour is within the image (provided that x, y is within)
	 */
	private boolean isWithinXY(int x, int y, int direction)
	{
		switch (direction)
		{
		// 4-connected directions
			case 0:
				return (y > 0);
			case 1:
				return (x < xlimit);
			case 2:
				return (y < ylimit);
			case 3:
				return (x > 0);
				// Then remaining 8-connected directions
			case 4:
				return (y > 0 && x < xlimit);
			case 5:
				return (y < ylimit && x < xlimit);
			case 6:
				return (y < ylimit && x > 0);
			case 7:
				return (y > 0 && x > 0);
			default:
				return false;
		}
	}

	/**
	 * @return The image width
	 */
	public int getWidth()
	{
		return ip.getWidth();
	}

	/**
	 * @return The image height
	 */
	public int getHeight()
	{
		return ip.getHeight();
	}

	/**
	 * Get the centre-of-mass and pixel count of each object. Data is stored indexed by the object value so processing
	 * of results should start from 1.
	 * 
	 * @return The centre-of-mass of each object (plus the pixel count) [object][cx,cy,n]
	 */
	public double[][] getObjectCentres()
	{
		int[] count = new int[maxObject + 1];
		double[] sumx = new double[count.length];
		double[] sumy = new double[count.length];
		final int maxy = getHeight();
		final int maxx = getWidth();
		for (int y = 0, i = 0; y < maxy; y++)
			for (int x = 0; x < maxx; x++, i++)
			{
				final int value = objectMask[i];
				if (value != 0)
				{
					sumx[value] += x;
					sumy[value] += y;
					count[value]++;
				}
			}
		double[][] data = new double[count.length][3];
		for (int i = 1; i < count.length; i++)
		{
			data[i][0] = sumx[i] / count[i];
			data[i][1] = sumy[i] / count[i];
			data[i][2] = count[i];
		}
		return data;
	}
}
