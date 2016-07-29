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
	public int RESULT_X;
	/**
	 * The Y coordinate
	 */
	public int RESULT_Y;
	/**
	 * The Z coordinate
	 */
	public int RESULT_Z;
	/**
	 * The internal ID used during the FindFoci routine. This can be ignored.
	 */
	public int RESULT_PEAK_ID;
	/**
	 * The number of pixels in the peak
	 */
	public int RESULT_COUNT;
	/**
	 * The sum of the peak intensity
	 */
	public double RESULT_INTENSITY;
	/**
	 * The peak maximum value
	 */
	public float RESULT_MAX_VALUE;
	/**
	 * The peak highest saddle point
	 */
	public float RESULT_HIGHEST_SADDLE_VALUE;
	/**
	 * The peak ID of the touching peak with the highest saddle point
	 */
	public int RESULT_SADDLE_NEIGHBOUR_ID;
	/**
	 * The average of the peak intensity
	 */
	public double RESULT_AVERAGE_INTENSITY;
	/**
	 * The sum of the peak intensity above the background
	 */
	public double RESULT_INTENSITY_MINUS_BACKGROUND;
	/**
	 * The average of the peak intensity above the background
	 */
	public double RESULT_AVERAGE_INTENSITY_MINUS_BACKGROUND;
	/**
	 * The number of pixels in the peak above the highest saddle
	 */
	public int RESULT_COUNT_ABOVE_SADDLE;
	/**
	 * The sum of the peak intensity above the highest saddle
	 */
	public double RESULT_INTENSITY_ABOVE_SADDLE;
	/**
	 * The sum of the peak intensity above the minimum value of the analysed image
	 */
	public double RESULT_INTENSITY_MINUS_MIN;
	/**
	 * The average of the peak intensity above the minimum value of the analysed image
	 */
	public double RESULT_AVERAGE_INTENSITY_MINUS_MIN;
	/**
	 * The custom sort value. This is used internally to sort the results using values not stored in the result array.
	 */
	double RESULT_CUSTOM_SORT_VALUE;
	/**
	 * The state (i.e. pixel value) from the mask image
	 */
	public int RESULT_STATE;
	/**
	 * The allocated object from the mask image
	 */
	public int RESULT_OBJECT;

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
		return new int[] { RESULT_X, RESULT_Y, RESULT_Z };
	}
}