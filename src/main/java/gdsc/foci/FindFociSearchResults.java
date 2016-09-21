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
 * Contains the results of the FindFoci algorithm after the search stage.
 */
public class FindFociSearchResults
{
	public FindFociResult[] resultsArray;
	public FindFociSaddleList[] saddlePoints;

	/**
	 * Instantiates a new find foci merge results.
	 *
	 * @param resultsArray
	 *            the results array
	 * @param saddlePoints
	 *            the saddle points
	 */
	public FindFociSearchResults(FindFociResult[] resultsArray,
			FindFociSaddleList[] saddlePoints)
	{
		this.resultsArray = resultsArray;
		this.saddlePoints = saddlePoints;
	}
}