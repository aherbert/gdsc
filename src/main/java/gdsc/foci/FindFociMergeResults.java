package gdsc.foci;

import java.util.ArrayList;

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
 * Contains the results of the FindFoci algorithm after the initialisation stage.
 */
public class FindFociMergeResults
{
	public ArrayList<FindFociResult> resultsArray;
	public int originalNumberOfPeaks;

	/**
	 * Instantiates a new find foci merge results.
	 *
	 * @param resultsArray
	 *            the results array
	 * @param originalNumberOfPeaks
	 *            the original number of peaks
	 */
	public FindFociMergeResults(ArrayList<FindFociResult> resultsArray, int originalNumberOfPeaks)
	{
		this.resultsArray = resultsArray;
		this.originalNumberOfPeaks = originalNumberOfPeaks;
	}
}