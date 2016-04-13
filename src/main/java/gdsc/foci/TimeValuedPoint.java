package gdsc.foci;

import gdsc.core.match.BasePoint;

/*----------------------------------------------------------------------------- 
 * GDSC Plugins for ImageJ
 * 
 * Copyright (C) 2011 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

/**
 * Stores a 2D/3D point (real coordinates) with time and value.
 */
public class TimeValuedPoint extends BasePoint
{
	protected int time;
	protected float value;

	public TimeValuedPoint(float x, float y, float z, int time, float value)
	{
		super(x, y, z);
		this.time = time;
		this.value = value;
	}

	public TimeValuedPoint(float x, float y, float z, float value)
	{
		super(x, y, z);
		this.value = value;
	}

	
	/**
	 * @return the time
	 */
	public int getTime()
	{
		return time;
	}

	/**
	 * @return the value
	 */
	public float getValue()
	{
		return value;
	}
}