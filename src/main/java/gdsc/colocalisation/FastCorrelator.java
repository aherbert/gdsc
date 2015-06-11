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

import java.math.BigInteger;

/**
 * Class to calculate the correlation between two datasets using rolling sums
 */
public class FastCorrelator
{
	private long sumX = 0;
	private long sumXY = 0;
	private long sumXX = 0;
	private long sumYY = 0;
	private long sumY = 0;
	private int n = 0;

	/**
	 * Add a pair of data points
	 * 
	 * @param v1
	 * @param v2
	 */
	public void add(final int v1, final int v2)
	{
		addData(v1, v2);
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
		sumXY += (v1 * v2);
		sumXX += (v1 * v1);
		sumYY += (v2 * v2);
		sumY += v2;
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
		return calculateCorrelation(sumX, sumXY, sumXX, sumYY, sumY, n);
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
	 * @return The sum of the X data squared
	 */
	public long getSumXX()
	{
		return sumXX;
	}

	/**
	 * @return The sum of the Y data squared
	 */
	public long getSumYY()
	{
		return sumYY;
	}

	/**
	 * @return The sum of each X data point multiplied by the paired Y data point
	 */
	public long getSumXY()
	{
		return sumXY;
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
		return doCorrelation(x, y, n);
	}

	/**
	 * Calculate the correlation using a fast sum
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
	 * Calculate the correlation using a fast sum
	 * 
	 * @param x
	 *            The X data
	 * @param y
	 *            the Y data
	 * @return The correlation
	 */
	private static double doCorrelation(int[] x, int[] y, int n)
	{
		if (n <= 0)
			return Double.NaN;

		long sumX = 0;
		long sumXY = 0;
		long sumXX = 0;
		long sumYY = 0;
		long sumY = 0;

		for (int i = n; i-- > 0;)
		{
			sumX += x[i];
			sumXY += (x[i] * y[i]);
			sumXX += (x[i] * x[i]);
			sumYY += (y[i] * y[i]);
			sumY += y[i];
		}

		return calculateCorrelation(sumX, sumXY, sumXX, sumYY, sumY, n);
	}

	/**
	 * Calculate the correlation using BigInteger to avoid precision error
	 * 
	 * @param sumX
	 *            The sum of the X values
	 * @param sumXY
	 *            The sum of the X*Y values
	 * @param sumXX
	 *            The squared sum of the X values
	 * @param sumYY
	 *            The squared sum of the Y values
	 * @param sumY
	 *            The sum of the Y values
	 * @param n
	 *            The number of values
	 * @return The correlation
	 */
	public static double calculateCorrelation(long sumX, long sumXY, long sumXX, long sumYY, long sumY, long n)
	{
		BigInteger nSumXY = BigInteger.valueOf(sumXY).multiply(BigInteger.valueOf(n));
		BigInteger nSumXX = BigInteger.valueOf(sumXX).multiply(BigInteger.valueOf(n));
		BigInteger nSumYY = BigInteger.valueOf(sumYY).multiply(BigInteger.valueOf(n));

		nSumXY = nSumXY.subtract(BigInteger.valueOf(sumX).multiply(BigInteger.valueOf(sumY)));
		nSumXX = nSumXX.subtract(BigInteger.valueOf(sumX).multiply(BigInteger.valueOf(sumX)));
		nSumYY = nSumYY.subtract(BigInteger.valueOf(sumY).multiply(BigInteger.valueOf(sumY)));

		BigInteger product = nSumXX.multiply(nSumYY);

		return nSumXY.doubleValue() / Math.sqrt(product.doubleValue());
	}

	/**
	 * Clear all stored values
	 */
	public void clear()
	{
		sumX = sumXY = sumXX = sumYY = sumY = 0;
		n = 0;
	}
}
