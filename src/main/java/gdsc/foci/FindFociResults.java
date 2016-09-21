package gdsc.foci;

import java.util.ArrayList;
import java.util.Arrays;

import ij.ImagePlus;

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
		this.results = (results == null) ? new ArrayList<FindFociResult>(0)
				: new ArrayList<FindFociResult>(Arrays.asList(results));
		this.stats = stats;
	}

	/**
	 * Returns a shallow copy of this set of results.
	 *
	 * @return the find foci results
	 * @see java.lang.Object#clone()
	 */
	@Override
	public FindFociResults clone()
	{
		try
		{
			FindFociResults copy = (FindFociResults) super.clone();
			return copy;
		}
		catch (CloneNotSupportedException e)
		{
			return null;
		}
	}
}