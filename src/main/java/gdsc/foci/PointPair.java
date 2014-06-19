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
 * Class to store a pair of coordinates.
 */
public class PointPair
{
	private Coordinate point1;
	private Coordinate point2;

	/**
	 * @param point1
	 * @param point2
	 */
	public PointPair(Coordinate point1, Coordinate point2)
	{
		this.point1 = point1;
		this.point2 = point2;
	}

	/**
	 * @return the point1
	 */
	public Coordinate getPoint1()
	{
		return point1;
	}

	/**
	 * @return the point2
	 */
	public Coordinate getPoint2()
	{
		return point2;
	}
	
	/**
	 * @return the distance (or -1 if either point is null)
	 */
	public double getXYZDistance()
	{
		if (point1 == null || point2 == null)
			return -1;
		
		return point1.distance(point2.getPositionX(), point2.getPositionY(), point2.getPositionZ());
	}
	
	/**
	 * @return the squared distance (or -1 if either point is null)
	 */
	public double getXYZDistance2()
	{
		if (point1 == null || point2 == null)
			return -1;
		
		return point1.distance2(point2.getPositionX(), point2.getPositionY(), point2.getPositionZ());
	}
	
	/**
	 * @return the XY distance (or -1 if either point is null)
	 */
	public double getXYDistance()
	{
		if (point1 == null || point2 == null)
			return -1;
		
		return point1.distance(point2.getPositionX(), point2.getPositionY());
	}
	
	/**
	 * @return the squared XY distance (or -1 if either point is null)
	 */
	public double getXYDistance2()
	{
		if (point1 == null || point2 == null)
			return -1;
		
		return point1.distance2(point2.getPositionX(), point2.getPositionY());
	}
}
