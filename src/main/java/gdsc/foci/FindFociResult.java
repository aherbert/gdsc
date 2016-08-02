package gdsc.foci;

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
 * Contains the foci result of the FindFoci algorithm.
 */
public class FindFociResult implements Cloneable
{
	/**
	 * The X coordinate
	 */
	public int x;
	/**
	 * The Y coordinate
	 */
	public int y;
	/**
	 * The Z coordinate (this is zero-indexed, not one-indexed as per ImageJ stack slices)
	 */
	public int z;
	/**
	 * The internal ID used during the FindFoci routine. This can be ignored.
	 */
	public int id;
	/**
	 * The number of pixels in the peak
	 */
	public int count;
	/**
	 * The sum of the peak intensity
	 */
	public double totalIntensity;
	/**
	 * The peak maximum value
	 */
	public float maxValue;
	/**
	 * The peak highest saddle point
	 */
	public float highestSaddleValue;
	/**
	 * The peak ID of the touching peak with the highest saddle point
	 */
	public int saddleNeighbourId;
	/**
	 * The average of the peak intensity
	 */
	public double averageIntensity;
	/**
	 * The sum of the peak intensity above the background
	 */
	public double totalIntensityAboveBackground;
	/**
	 * The average of the peak intensity above the background
	 */
	public double averageIntensityAboveBackground;
	/**
	 * The number of pixels in the peak above the highest saddle
	 */
	public int countAboveSaddle;
	/**
	 * The sum of the peak intensity above the highest saddle
	 */
	public double intensityAboveSaddle;
	/**
	 * The sum of the peak intensity above the minimum value of the analysed image
	 */
	public double totalIntensityAboveImageMinimum;
	/**
	 * The average of the peak intensity above the minimum value of the analysed image
	 */
	public double averageIntesnityAboveImageMinimum;
	/**
	 * The custom sort value. This is used internally to sort the results using values not stored in the result array.
	 */
	double sortValue;
	/**
	 * The state (i.e. pixel value) from the mask image
	 */
	public int state;
	/**
	 * The allocated object from the mask image
	 */
	public int object;

	/**
	 * Instantiates a new find foci result.
	 */
	public FindFociResult()
	{
	}

	/**
	 * Returns a copy of this result.
	 *
	 * @return the find foci result
	 * @see java.lang.Object#clone()
	 */
	@Override
	public FindFociResult clone()
	{
		try
		{
			return (FindFociResult) super.clone();
		}
		catch (CloneNotSupportedException e)
		{
			return null;
		}
	}

	/**
	 * Gets the coordinates.
	 *
	 * @return the coordinates [XYZ]
	 */
	public int[] getCoordinates()
	{
		return new int[] { x, y, z };
	}
}