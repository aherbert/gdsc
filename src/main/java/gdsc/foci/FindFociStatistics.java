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
 * Contains the statistics results of the FindFoci algorithm.
 */
public class FindFociStatistics implements Cloneable
{
	/**
	 * The minimum in the analysed region
	 */
	public float regionMinimum;
	/**
	 * The maximum in the analysed region
	 */
	public float regionMaximum;
	/**
	 * The mean in the analysed region
	 */
	public double regionAverage;
	/**
	 * The standard deviation in the analysed region
	 */
	public double regionStdDev;
	/**
	 * The total image intensity in the analysed region
	 */
	public double regionTotal;
	/**
	 * The image background level
	 */
	public float background;
	/**
	 * The total image intensity above the background
	 */
	public double totalAboveBackground;
	/**
	 * The minimum of the background region
	 */
	public float backgroundRegionMinimum;
	/**
	 * The maximum of the background region
	 */
	public float backgroundRegionMaximum;
	/**
	 * The mean of the background region
	 */
	public double backgroundRegionAverage;
	/**
	 * The standard deviation of the background region
	 */
	public double backgroundRegionStdDev;
	/**
	 * The minimum image value
	 */
	public float imageMinimum;
	/**
	 * The total image intensity above the minimum image value
	 */
	public double totalAboveImageMinimum;

	/**
	 * Instantiates a new find foci statistics.
	 */
	public FindFociStatistics()
	{
	}

	/**
	 * Returns a copy of this statistics
	 *
	 * @return the find foci result
	 * @see java.lang.Object#clone()
	 */
	@Override
	public FindFociStatistics clone()
	{
		try
		{
			return (FindFociStatistics) super.clone();
		}
		catch (CloneNotSupportedException e)
		{
			return null;
		}
	}
}