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
 * Stores a 2D/3D point with a value.
 */
public class ValuedPoint extends BasePoint
{
	private float value = 0;

	public ValuedPoint(int x, int y, int z, float value)
	{
		super(x, y, z);
		this.value = value;
	}

	public ValuedPoint(AssignedPoint point, float value)
	{
		super(point.getXint(), point.getYint(), point.getZint());
		this.value = value;
	}

	/**
	 * @return the value
	 */
	public float getValue()
	{
		return value;
	}
}