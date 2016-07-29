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
 * Contains the foci result of the FindFoci algorithm.
 */
public class FindFociSaddle implements Cloneable
{
	/** The saddle peak id. */
	public int id;

	/** The saddle value. */
	public float value;

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
}