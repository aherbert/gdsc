package gdsc.foci;

import java.util.ArrayList;

import ij.ImagePlus;

// TODO: Auto-generated Javadoc
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
public class FindFociResult implements Cloneable
{
	
	/** The mask. */
	public final ImagePlus mask;
	
	/** The results. */
	public final ArrayList<double[]> results;
	
	/** The stats. */
	public final double[] stats;

	/**
	 * Instantiates a new find foci result.
	 *
	 * @param mask the mask
	 * @param results the results
	 * @param stats the stats
	 */
	public FindFociResult(ImagePlus mask, ArrayList<double[]> results, double[] stats)
	{
		this.mask = mask;
		this.results = results;
		this.stats = stats;
	}
}