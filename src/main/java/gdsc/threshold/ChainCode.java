package gdsc.threshold;

import java.util.LinkedList;

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
 * Stores a chain code. Stores the origin and a set of offsets in a run. The offset can be used to find the x,y
 * coordinates for each point using the DIR_X_OFFSET and DIR_Y_OFFSET directions for successive points from the origin.
 * <p>
 * The implementation is not very space efficient but is useful for debugging.
 */
public class ChainCode implements Comparable<ChainCode>
{
	private final static float ROOT2 = (float) Math.sqrt(2);

	/**
	 * Describes the x-direction for the chain code
	 */
	public final static int[] DIR_X_OFFSET = new int[] { 0, 1, 1, 1, 0, -1, -1, -1 };
	/**
	 * Describes the y-direction for the chain code
	 */
	public final static int[] DIR_Y_OFFSET = new int[] { -1, -1, 0, 1, 1, 1, 0, -1 };

	/**
	 * Describes the length for the chain code
	 */
	public final static float[] DIR_LENGTH = new float[] { 1, ROOT2, 1, ROOT2, 1, ROOT2, 1, ROOT2 };

	private int x = 0;
	private int y = 0;

	private LinkedList<Integer> run = new LinkedList<Integer>();
	private float distance;
	private String toString;

	/**
	 * Default constructor
	 */
	public ChainCode(int x, int y)
	{
		this.x = x;
		this.y = y;
		dirty();
	}

	private void dirty()
	{
		distance = -1;
		toString = null;
	}

	/**
	 * Extend the chain code in the given direction
	 * 
	 * @param direction
	 */
	public void add(int direction)
	{
		run.add(direction);
		dirty();
	}

	/**
	 * @return the x origin
	 */
	public int getX()
	{
		return x;
	}

	/**
	 * @return the y origin
	 */
	public int getY()
	{
		return y;
	}

	/**
	 * @return The chain code distance
	 */
	public float getDistance()
	{
		if (distance < 0)
		{
			distance = 0;
			for (int code : run)
			{
				distance += DIR_LENGTH[code];
			}
		}
		return distance;
	}

	/**
	 * @return The number of points in the chain code (includes the x,y origin)
	 */
	public int getSize()
	{
		return run.size() + 1;
	}

	/**
	 * @return The chain code run
	 */
	public int[] getRun()
	{
		int[] codes = new int[run.size()];
		int i = 0;
		for (int code : run)
		{
			codes[i++] = code;
		}
		return codes;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	public String toString()
	{
		if (toString == null)
		{
			StringBuilder sb = new StringBuilder();
			sb.append(x).append(",").append(y);
			for (int code : run)
			{
				sb.append(":").append(DIR_X_OFFSET[code]).append(",").append(DIR_Y_OFFSET[code]);
			}
			toString = sb.toString();
		}
		return toString;
	}

	public int compareTo(ChainCode o)
	{
		if (o != null)
		{
			if (getDistance() < o.getDistance())
				return -1;
			if (getDistance() > o.getDistance())
				return 1;
			if (x < o.getX())
				return -1;
			if (x > o.getX())
				return 1;
			if (y < o.getY())
				return -1;
			if (y > o.getY())
				return 1;
		}
		return 0;
	}

	/**
	 * Gets the end position.
	 *
	 * @return the end [endX, endY]
	 */
	public int[] getEnd()
	{
		int x = this.x;
		int y = this.y;

		for (int code : run)
		{
			x += DIR_X_OFFSET[code];
			y += DIR_Y_OFFSET[code];
		}

		return new int[] { x, y };
	}

	/**
	 * Create a new chain code in the opposite direction
	 *
	 * @return the chain code
	 */
	public ChainCode reverse()
	{
		int x = this.x;
		int y = this.y;

		int i = 0;
		int[] run2 = new int[run.size()];
		for (int code : run)
		{
			x += DIR_X_OFFSET[code];
			y += DIR_Y_OFFSET[code];
			run2[i++] = code;
		}

		ChainCode reverse = new ChainCode(x, y);
		while (i-- > 0)
			reverse.add((run2[i] + 4) % 8);

		return reverse;
	}
}
