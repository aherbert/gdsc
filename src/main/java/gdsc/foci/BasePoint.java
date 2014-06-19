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
 * <p>Stores a 2D/3D point.
 * 
 * <p>Overrides equals and hashCode methods using x,y,z, coordinates for equivalence. Derived classes can optionally override this.
 * 
 * @see {@link java.lang.Object#equals(java.lang.Object) } 
 * @see {@link java.lang.Object#hashCode() }
 */
public class BasePoint implements Coordinate
{
	protected int x = 0;
	protected int y = 0;
	protected int z = 0;

	public BasePoint(int x, int y, int z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public BasePoint(int x, int y)
	{
		this.x = x;
		this.y = y;
	}

	/**
	 * Calculate the squared distance to the given coordinates
	 */
	public double distance2(int x, int y, int z)
	{
		return (this.x - x) * (this.x - x) + (this.y - y) * (this.y - y) + (this.z - z) * (this.z - z);
	}

	/**
	 * Calculate the squared distance to the given coordinates
	 */
	public double distance2(int x, int y)
	{
		return (this.x - x) * (this.x - x) + (this.y - y) * (this.y - y);
	}

	/**
	 * Calculate the distance to the given coordinates
	 */
	public double distance(int x, int y, int z)
	{
		return Math.sqrt(distance2(x, y, z));
	}

	/**
	 * Calculate the distance to the given coordinates
	 */
	public double distance(int x, int y)
	{
		return Math.sqrt(distance2(x, y));
	}

	/* (non-Javadoc)
	 * @see gdsc.foci.Coordinate#getX()
	 */
	public int getX()
	{
		return x;
	}

	/* (non-Javadoc)
	 * @see gdsc.foci.Coordinate#getY()
	 */
	public int getY()
	{
		return y;
	}

	/* (non-Javadoc)
	 * @see gdsc.foci.Coordinate#getZ()
	 */
	public int getZ()
	{
		return z;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object aThat)
	{
		if (this == aThat)
			return true;
		if (!(aThat instanceof BasePoint))
			return false;

		//cast to native object is now safe
		BasePoint that = (BasePoint) aThat;

		return x == that.x && y == that.y && z == that.z;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode()
	{
		return (41 * (41 * (41 + x) + y) + z);
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