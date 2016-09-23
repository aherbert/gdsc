package gdsc.foci;

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
 * Contains the results of the FindFoci algorithm during the merge stage.
 */
public class FindFociMergeTempResults
{

	/** The results array. */
	public FindFociResult[] resultsArray;

	/** The saddle points. */
	public FindFociSaddleList[] saddlePoints;

	/** The peak id map. */
	public int[] peakIdMap;

	/** The result list. */
	public FindFociResult[] resultList;

	/**
	 * Instantiates a new find foci merge results.
	 *
	 * @param resultsArray
	 *            the results array
	 * @param saddlePoints
	 *            the saddle points
	 * @param peakIdMap
	 *            the peak id map
	 * @param resultList
	 *            the result list
	 */
	public FindFociMergeTempResults(FindFociResult[] resultsArray, FindFociSaddleList[] saddlePoints, int[] peakIdMap,
			FindFociResult[] resultList)
	{
		this.resultsArray = resultsArray;
		this.saddlePoints = saddlePoints;
		this.peakIdMap = peakIdMap;
		this.resultList = resultList;
	}
}