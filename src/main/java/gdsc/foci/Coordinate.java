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
 * Stores a 2D/3D point
 */
public interface Coordinate
{
	/**
	 * @return The X-coordinate (rounded if necessary)
	 */
	public int getX();
	/**
	 * @return The Y-coordinate (rounded if necessary)
	 */
	public int getY();
	/**
	 * @return The Z-coordinate (rounded if necessary)
	 */
	public int getZ();
	
	/**
	 * @return The X-coordinate
	 */
	public float getPositionX();
	/**
	 * @return The Y-coordinate
	 */
	public float getPositionY();
	/**
	 * @return The Z-coordinate
	 */
	public float getPositionZ();

	/**
	 * Calculate the XYZ distance to the given coordinates
	 */
	public double distance(float x, float y, float z);

	/**
	 * Calculate the XY distance to the given coordinates
	 */
	public double distance(float x, float y);
	
	/**
	 * Calculate the XYZ squared distance to the given coordinates
	 */
	public double distance2(float x, float y, float z);

	/**
	 * Calculate the XY squared distance to the given coordinates
	 */
	public double distance2(float x, float y);
}