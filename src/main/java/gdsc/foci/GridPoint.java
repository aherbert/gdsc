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
 * Stores a 2D/3D point with a value and an assigned flag.
 */
public class GridPoint extends ValuedPoint
{
	private boolean assigned = false;

	public GridPoint(int x, int y, int z, float value)
	{
		super(x, y, z, value);
	}

	public GridPoint(AssignedPoint point, float value)
	{
		super(point, value);
	}

	/**
	 * @param assigned the assigned to set
	 */
	public void setAssigned(boolean assigned)
	{
		this.assigned = assigned;
	}

	/**
	 * @return the assigned
	 */
	public boolean isAssigned()
	{
		return assigned;
	}
}