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
 * Stores a 2D/3D point with an assigned Id
 */
public class AssignedPoint extends BasePoint implements Comparable<AssignedPoint>
{
	protected int id = 0;
	protected int assignedId = 0;

	public AssignedPoint(int x, int y, int z, int id)
	{
		super(x, y, z);
		this.id = id;
	}

	public AssignedPoint(int x, int y, int id)
	{
		super(x, y);
		this.id = id;
	}

	/**
	 * Sets the point Id
	 * 
	 * @param id
	 */
	public void setId(int id)
	{
		this.id = id;
	}

	/**
	 * @return the id
	 */
	public int getId()
	{
		return id;
	}

	/**
	 * @param assignedId
	 *            the assignedId to set
	 */
	public void setAssignedId(int assignedId)
	{
		this.assignedId = assignedId;
	}

	/**
	 * @return the assignedId
	 */
	public int getAssignedId()
	{
		return assignedId;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(AssignedPoint that)
	{
		int d = this.x - that.x;
		if (d != 0)
			return d;
		d = this.y - that.y;
		if (d != 0)
			return d;
		return this.z - that.z;
	}
}