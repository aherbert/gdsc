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
	 */
	@Override
	public FindFociStatistics clone()
	{
		try
		{
			return (FindFociStatistics) super.clone();
		}
		catch (final CloneNotSupportedException e)
		{
			return null;
		}
	}
}
