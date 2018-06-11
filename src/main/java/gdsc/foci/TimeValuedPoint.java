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

import gdsc.core.match.BasePoint;


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
