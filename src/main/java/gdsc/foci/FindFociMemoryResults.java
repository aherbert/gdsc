package gdsc.foci;

import java.util.ArrayList;
import java.util.Arrays;

import ij.ImagePlus;
import ij.measure.Calibration;

/*----------------------------------------------------------------------------- 
 * GDSC Plugins for ImageJ
 * 
 * Copyright (C) 2017 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

/**
 * Contains the results of the FindFoci algorithm saved to memory.
 */
public class FindFociMemoryResults implements Cloneable
{
	/** The image Id. */
	public final int imageId;
	
	/** The image calibration. */
	public final Calibration calibration;

	/** The results. */
	public final ArrayList<FindFociResult> results;

	/**
	 * Instantiates a new find foci result.
	 *
	 * @param id
	 *            the id
	 * @param results
	 *            the results
	 * @param stats
	 *            the stats
	 */
	public FindFociMemoryResults(ImagePlus imp, ArrayList<FindFociResult> results)
	{
		this.imageId = imp.getID();
		this.calibration = imp.getCalibration();
		this.results = results;
	}

	/**
	 * Instantiates a new find foci results.
	 *
	 * @param id
	 *            the id
	 * @param results
	 *            the results
	 * @param stats
	 *            the stats
	 */
	public FindFociMemoryResults(ImagePlus imp, FindFociResult[] results)
	{
		this(imp, (results == null) ? new ArrayList<FindFociResult>(0)
				: new ArrayList<FindFociResult>(Arrays.asList(results)));
	}

	/**
	 * Returns a shallow copy of this set of results.
	 *
	 * @return the find foci results
	 * @see java.lang.Object#clone()
	 */
	@Override
	public FindFociMemoryResults clone()
	{
		try
		{
			FindFociMemoryResults copy = (FindFociMemoryResults) super.clone();
			return copy;
		}
		catch (CloneNotSupportedException e)
		{
			return null;
		}
	}
}