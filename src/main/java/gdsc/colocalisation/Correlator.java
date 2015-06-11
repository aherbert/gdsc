package gdsc.colocalisation;

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

import java.util.Arrays;

/**
 * Class to calculate the correlation between two datasets, storing the data for the correlation calculation.
 */
public class Correlator
{
	private int[] x, y;
	private int n = 0;

	private long sumX = 0;
	private long sumY = 0;

	/**
	 * Constructor
	 * 
	 * @param capacity
	 *            The initial capacity
	 */
	public Correlator(int capacity)
	{
		if (capacity < 0)
			capacity = 0;
		x = new int[capacity];
		y = new int[capacity];
	}

	/**
	 * Constructor
	 */
	public Correlator()
	{
		this(100);
	}

	/**
	 * Add a pair of data points
	 * 
	 * @param v1
	 * @param v2
	 */
	public void add(final int v1, final int v2)
	{
		checkCapacity(1);
		addData(v1, v2);
	}

	/**
	 * Ensure that the specified number of elements can be added to the arrays.
	 * 
	 * @param length
	 */
	private void checkCapacity(int length)
	{
		final int minCapacity = n + length;
		if (minCapacity > x.length)
		{
			int newCapacity = (x.length * 3) / 2 + 1;
			if (newCapacity < minCapacity)
				newCapacity = minCapacity;
			int[] newValues = new int[newCapacity];
			System.arraycopy(x, 0, newValues, 0, n);
			x = newValues;
			newValues = new int[newCapacity];
			System.arraycopy(y, 0, newValues, 0, n);
			y = newValues;
		}
	}

	/**
	 * Add a pair of data points to the sums
	 * 
	 * @param v1
	 * @param v2
	 */
	private void addData(final int v1, final int v2)
	{
		sumX += v1;
		sumY += v2;
		x[n] = v1;
		y[n] = v2;
		n++;
	}

	/**
	 * Add a set of paired data points
	 * 
	 * @param v1
	 * @param v2
	 */
	public void add(final int[] v1, final int[] v2)
	{
		if (v1 == null || v2 == null)
			return;
		final int length = Math.min(v1.length, v2.length);
		checkCapacity(length);
		for (int i = 0; i < length; i++)
		{
			addData(v1[i], v2[i]);
		}
	}

	/**
	 * Add a set of paired data points
	 * 
	 * @param v1
	 * @param v2
	 * @param length
	 *            the length of the data set
	 */
	public void add(final int[] v1, final int[] v2, int length)
	{
		if (v1 == null || v2 == null)
			return;
		length = Math.min(Math.min(v1.length, v2.length), length);
		checkCapacity(length);
		for (int i = 0; i < length; i++)
		{
			addData(v1[i], v2[i]);
		}
	}

	/**
	 * @return The correlation
	 */
	public double getCorrelation()
	{
		if (n == 0)
			return Double.NaN;
		
		final double ux = sumX / (double) n;
		final double uy = sumY / (double) n;
		return doCorrelation(x, y, n, ux, uy);
		
		//return correlation(x, y, n);
	}

	/**
	 * @return The correlation calculated using a fast sum
	 */
	public double getFastCorrelation()
	{
		if (n == 0)
			return Double.NaN;
		
		long sumXY = 0;
		long sumXX = 0;
		long sumYY = 0;
		
		for (int i = n; i-- > 0;)
		{
			sumXY += (x[i] * y[i]);
			sumXX += (x[i] * x[i]);
			sumYY += (y[i] * y[i]);
		}

		return FastCorrelator.calculateCorrelation(sumX, sumXY, sumXX, sumYY, sumY, n);
	}

	/**
	 * @return The X-data
	 */
	public int[] getX()
	{
		return Arrays.copyOf(x, n);
	}

	/**
	 * @return The Y-data
	 */
	public int[] getY()
	{
		return Arrays.copyOf(y, n);
	}

	/**
	 * @return The sum of the X data
	 */
	public long getSumX()
	{
		return sumX;
	}

	/**
	 * @return The sum of the Y data
	 */
	public long getSumY()
	{
		return sumY;
	}

	/**
	 * @return The number of data points
	 */
	public int getN()
	{
		return n;
	}

	/**
	 * Calculate the correlation
	 * 
	 * @param x
	 *            The X data
	 * @param y
	 *            the Y data
	 * @return The correlation
	 */
	public static double correlation(int[] x, int[] y)
	{
		if (x == null || y == null)
			return Double.NaN;
		final int n = Math.min(x.length, y.length);
		return correlation(x, y, n);
	}

	/**
	 * Calculate the correlation
	 * 
	 * @param x
	 *            The X data
	 * @param y
	 *            the Y data
	 * @param n
	 *            The number of data points
	 * @return The correlation
	 */
	public static double correlation(int[] x, int[] y, int n)
	{
		if (x == null || y == null)
			return Double.NaN;
		n = Math.min(Math.min(x.length, y.length), n);
		return doCorrelation(x, y, n);
	}

	/**
	 * Calculate the correlation
	 * 
	 * @param x
	 *            The X data
	 * @param y
	 *            the Y data
	 * @param n
	 *            The number of data points
	 * @return The correlation
	 */
	private static double doCorrelation(final int[] x, final int[] y, final int n)
	{
		if (n <= 0)
			return Double.NaN;

		long sx = 0;
		long sy = 0;

		// Get means
		for (int i = n; i-- > 0;)
		{
			sx += x[i];
			sy += y[i];
		}

		final double ux = sx / (double) n;
		final double uy = sy / (double) n;
		
		return doCorrelation(x, y, n, ux, uy);	}

	/**
	 * Calculate the correlation
	 * 
	 * @param x
	 *            The X data
	 * @param y
	 *            the Y data
	 * @param n
	 *            The number of data points
	 * @param ux
	 *            The mean of the X data
	 * @param uy
	 *            The mean of the Y data
	 * @return The correlation
	 */
	private static double doCorrelation(final int[] x, final int[] y, final int n, final double ux, final double uy)
	{
		// TODO - Add a check to ensure that the sum will not lose precision as the total aggregates.
		// This could be done by keeping a BigDecimal to store the overall sums. When the rolling total
		// reaches the specified precision limit for a double then it should be added to the 
		// BigDecimal and reset. The precision limit could be set using the value of the mean, 
		// e.g. 1e10 times bigger than the mean. 

		// Calculate variances
		double p1 = 0;
		double p2 = 0;
		double p3 = 0;
		for (int i = n; i-- > 0;)
		{
			final double d1 = x[i] - ux;
			final double d2 = y[i] - uy;
			p1 += d1 * d1;
			p2 += d2 * d2;
			p3 += d1 * d2;
		}

		return p3 / Math.sqrt(p1 * p2);
	}

	/**
	 * Clear all stored values
	 */
	public void clear()
	{
		sumX = sumY = 0;
		n = 0;
	}
}
