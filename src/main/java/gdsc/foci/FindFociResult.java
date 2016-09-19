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
	public double averageIntensityAboveImageMinimum;
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
	 * The minimum x range covered by the peak. This is used when merging peaks above the minimum saddle value. 
	 */
	int minx;
	/**
	 * The minimum y range covered by the peak. This is used when merging peaks above the minimum saddle value. 
	 */
	int miny;
	/**
	 * The minimum z range covered by the peak. This is used when merging peaks above the minimum saddle value. 
	 */
	int minz;
	/**
	 * The maximum x range covered by the peak. This is used when merging peaks above the minimum saddle value. 
	 */
	int maxx;
	/**
	 * The maximum y range covered by the peak. This is used when merging peaks above the minimum saddle value. 
	 */
	int maxy;
	/**
	 * The maximum z range covered by the peak. This is used when merging peaks above the minimum saddle value. 
	 */
	int maxz;

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

	void updateBounds(FindFociResult result)
	{
		minx = Math.min(minx, result.minx);
		miny = Math.min(miny, result.miny);
		minz = Math.min(minz, result.minz);
		maxx = Math.max(maxx, result.maxx);
		maxy = Math.max(maxy, result.maxy);
		maxz = Math.max(maxz, result.maxz);
	}
}