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
 * Contains the foci saddle result of the FindFoci algorithm.
 */
public class FindFociSaddle implements Cloneable, Comparable<FindFociSaddle>
{
	/** The saddle peak id. */
	public int id;

	/** The saddle value. */
	public float value;
	
	/** Used for sorting */
	int order; 
	
	/**
	 * Instantiates a new find foci saddle.
	 */
	public FindFociSaddle(int id, float value)
	{
		this.id = id;
		this.value = value;
	}

	/**
	 * Returns a copy of this saddle.
	 *
	 * @return the find foci saddle
	 * @see java.lang.Object#clone()
	 */
	@Override
	public FindFociSaddle clone()
	{
		try
		{
			return (FindFociSaddle) super.clone();
		}
		catch (CloneNotSupportedException e)
		{
			return null;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(FindFociSaddle that)
	{
		if (this.value > that.value)
			return -1;
		if (this.value < that.value)
			return 1;
		// For compatibility with the legacy code the saddles must be sorted by Id if they are the same value
		//return 0; 
		return this.id - that.id;
	}
}