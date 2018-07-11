/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2018 Alex Herbert
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package gdsc.foci;

/**
 * Stores a 2D/3D point with an assigned Id.
 */
public class AssignedPoint extends BasePoint implements Comparable<AssignedPoint>
{
	/** The id. */
	protected int id = 0;

	/** The assigned id. */
	protected int assignedId = 0;

	/**
	 * Instantiates a new assigned point.
	 *
	 * @param x
	 *            the x
	 * @param y
	 *            the y
	 * @param z
	 *            the z
	 * @param id
	 *            the id
	 */
	public AssignedPoint(int x, int y, int z, int id)
	{
		super(x, y, z);
		this.id = id;
	}

	/**
	 * Instantiates a new assigned point.
	 *
	 * @param x
	 *            the x
	 * @param y
	 *            the y
	 * @param id
	 *            the id
	 */
	public AssignedPoint(int x, int y, int id)
	{
		super(x, y);
		this.id = id;
	}

	/**
	 * Sets the point Id.
	 *
	 * @param id
	 *            the new id
	 */
	public void setId(int id)
	{
		this.id = id;
	}

	/**
	 * Gets the id.
	 *
	 * @return the id
	 */
	public int getId()
	{
		return id;
	}

	/**
	 * Sets the assigned id.
	 *
	 * @param assignedId
	 *            the assignedId to set
	 */
	public void setAssignedId(int assignedId)
	{
		this.assignedId = assignedId;
	}

	/**
	 * Gets the assigned id.
	 *
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
	@Override
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
