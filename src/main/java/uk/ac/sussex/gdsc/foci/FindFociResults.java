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
package uk.ac.sussex.gdsc.foci;

import java.util.ArrayList;
import java.util.Arrays;

import ij.ImagePlus;

/**
 * Contains the results of the FindFoci algorithm.
 */
public class FindFociResults implements Cloneable
{
	/** The mask. */
	public final ImagePlus mask;

	/** The results. */
	public final ArrayList<FindFociResult> results;

	/** The statistics. */
	public final FindFociStatistics stats;

	/**
	 * Instantiates a new find foci result.
	 *
	 * @param mask
	 *            the mask
	 * @param results
	 *            the results
	 * @param stats
	 *            the stats
	 */
	public FindFociResults(ImagePlus mask, ArrayList<FindFociResult> results, FindFociStatistics stats)
	{
		this.mask = mask;
		this.results = results;
		this.stats = stats;
	}

	/**
	 * Instantiates a new find foci results.
	 *
	 * @param mask
	 *            the mask
	 * @param results
	 *            the results
	 * @param stats
	 *            the stats
	 */
	public FindFociResults(ImagePlus mask, FindFociResult[] results, FindFociStatistics stats)
	{
		this.mask = mask;
		this.results = (results == null) ? new ArrayList<>(0) : new ArrayList<>(Arrays.asList(results));
		this.stats = stats;
	}

	/**
	 * Returns a shallow copy of this set of results.
	 *
	 * @return the find foci results
	 */
	@Override
	public FindFociResults clone()
	{
		try
		{
			final FindFociResults copy = (FindFociResults) super.clone();
			return copy;
		}
		catch (final CloneNotSupportedException e)
		{
			return null;
		}
	}
}
