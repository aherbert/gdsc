package gdsc.foci;

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
public class TimeValuedPoint implements Coordinate
{
	protected float x = 0;
	protected float y = 0;
	protected float z = 0;
	protected int time = 0;
	protected float value = 0;

	public TimeValuedPoint(float x, float y, float z, int time, float value)
	{
		this.x = x;
		this.y = y;
		this.z = z;
		this.time = time;
		this.value = value;
	}

	public TimeValuedPoint(float x, float y, float z, float value)
	{
		this.x = x;
		this.y = y;
		this.z = z;
		this.value = value;
	}

	/* (non-Javadoc)
	 * @see gdsc.foci.Coordinate#getX()
	 */
	public int getX()
	{
		return (int)x;
	}

	/* (non-Javadoc)
	 * @see gdsc.foci.Coordinate#getY()
	 */
	public int getY()
	{
		return (int)y;
	}

	/* (non-Javadoc)
	 * @see gdsc.foci.Coordinate#getZ()
	 */
	public int getZ()
	{
		return (int)z;
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
	
	/* (non-Javadoc)
	 * @see gdsc.foci.Coordinate#getPositionX()
	 */
	public float getPositionX()
	{
		return x;
	}

	/* (non-Javadoc)
	 * @see gdsc.foci.Coordinate#getPositionY()
	 */
	public float getPositionY()
	{
		return y;
	}

	/* (non-Javadoc)
	 * @see gdsc.foci.Coordinate#getPositionZ()
	 */
	public float getPositionZ()
	{
		return z;
	}

	/* (non-Javadoc)
	 * @see gdsc.foci.Coordinate#distance(float, float, float)
	 */
	public double distance(float x, float y, float z)
	{
		return Math.sqrt(distance2(x, y, z));
	}

	/* (non-Javadoc)
	 * @see gdsc.foci.Coordinate#distance(float, float)
	 */
	public double distance(float x, float y)
	{
		return Math.sqrt(distance2(x, y));
	}
	
	/* (non-Javadoc)
	 * @see gdsc.foci.Coordinate#distance2(float, float, float)
	 */
	public double distance2(float x, float y, float z)
	{
		return (this.x - x) * (this.x - x) + (this.y - y) * (this.y - y) + (this.z - z) * (this.z - z);
	}

	/* (non-Javadoc)
	 * @see gdsc.foci.Coordinate#distance2(float, float)
	 */
	public double distance2(float x, float y)
	{
		return (this.x - x) * (this.x - x) + (this.y - y) * (this.y - y);
	}
}