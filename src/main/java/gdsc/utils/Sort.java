package gdsc.utils;

import java.util.Arrays;
import java.util.Comparator;

/*----------------------------------------------------------------------------- 
 * GDSC Plugins for ImageJ
 * 
 * Copyright (C) 2013 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

/**
 * Provides sorting functionality
 */
public class Sort
{
	/**
	 * Sorts the indices in descending order of their values
	 * 
	 * @param indices
	 * @param values
	 * @return The indices
	 */
	public static int[] sort(int[] indices, final float[] values)
	{
		return sort(indices, values, false);
	}

	/**
	 * Sorts the indices in order of their values
	 * 
	 * @param indices
	 * @param values
	 * @param ascending
	 * @return The indices
	 */
	public static int[] sort(int[] indices, final float[] values, boolean ascending)
	{
		// Convert data for sorting
		float[][] data = new float[indices.length][2];
		for (int i = indices.length; i-- > 0;)
		{
			data[i][0] = values[indices[i]];
			data[i][1] = indices[i];
		}

		Comparator<float[]> comp;
		if (ascending)
		{
			comp = new Comparator<float[]>()
			{
				public int compare(float[] o1, float[] o2)
				{
					// smallest first
					if (o1[0] < o2[0])
						return -1;
					if (o1[0] > o2[0])
						return 1;
					return 0;
				}
			};
		}
		else
		{
			comp = new Comparator<float[]>()
			{
				public int compare(float[] o1, float[] o2)
				{
					// Largest first
					if (o1[0] > o2[0])
						return -1;
					if (o1[0] < o2[0])
						return 1;
					return 0;
				}
			};
		}

		Arrays.sort(data, comp);

		// Copy back
		for (int i = indices.length; i-- > 0;)
		{
			indices[i] = (int) data[i][1];
		}

		return indices;
	}

	/**
	 * Sorts the indices in descending order of their values. Does not evaluate
	 * equivalence (returns only 1 or -1 to the sort routine)
	 * 
	 * @param indices
	 * @param values
	 * @return The indices
	 */
	public static int[] sortIgnoreEqual(int[] indices, final float[] values)
	{
		return sortIgnoreEqual(indices, values, false);
	}

	/**
	 * Sorts the indices in order of their values. Does not evaluate equivalence
	 * (returns only 1 or -1 to the sort routine)
	 * 
	 * @param indices
	 * @param values
	 * @param ascending
	 * @return The indices
	 */
	public static int[] sortIgnoreEqual(int[] indices, final float[] values, boolean ascending)
	{
		// Convert data for sorting
		float[][] data = new float[indices.length][2];
		for (int i = indices.length; i-- > 0;)
		{
			data[i][0] = values[indices[i]];
			data[i][1] = indices[i];
		}

		Comparator<float[]> comp;
		if (ascending)
		{
			comp = new Comparator<float[]>()
			{
				public int compare(float[] o1, float[] o2)
				{
					// smallest first
					if (o1[0] < o2[0])
						return -1;
					return 1;
				}
			};
		}
		else
		{
			comp = new Comparator<float[]>()
			{
				public int compare(float[] o1, float[] o2)
				{
					// Largest first
					if (o1[0] > o2[0])
						return -1;
					return 1;
				}
			};
		}

		Arrays.sort(data, comp);

		// Copy back
		for (int i = indices.length; i-- > 0;)
		{
			indices[i] = (int) data[i][1];
		}

		return indices;
	}

	/**
	 * Sorts array 1 using the values in array 2.
	 * 
	 * @param values1
	 * @param values2
	 * @param ascending
	 * @return The two arrays, sorted using array 2
	 */
	public static void sortArrays(float[] values1, final float[] values2, boolean ascending)
	{
		// Extract indices
		int[] indices = new int[values1.length];
		for (int i = values1.length; i-- > 0;)
		{
			indices[i] = i;
		}

		sort(indices, values2, ascending);

		// Copy back
		float[] v1 = Arrays.copyOf(values1, values1.length);
		float[] v2 = Arrays.copyOf(values2, values2.length);

		for (int i = values1.length; i-- > 0;)
		{
			values1[i] = v1[indices[i]];
			values2[i] = v2[indices[i]];
		}
	}

	/**
	 * Sorts array 1 using the values in array 2. Does not evaluate equivalence
	 * (returns only 1 or -1 to the sort routine)
	 * 
	 * @param values1
	 * @param values2
	 * @param ascending
	 * @return The two arrays, sorted using array 2
	 */
	public static void sortArraysIgnoreEqual(float[] values1, final float[] values2, boolean ascending)
	{
		// Extract indices
		int[] indices = new int[values1.length];
		for (int i = values1.length; i-- > 0;)
		{
			indices[i] = i;
		}

		sortIgnoreEqual(indices, values2, ascending);

		// Copy back
		float[] v1 = Arrays.copyOf(values1, values1.length);
		float[] v2 = Arrays.copyOf(values2, values2.length);

		for (int i = values1.length; i-- > 0;)
		{
			values1[i] = v1[indices[i]];
			values2[i] = v2[indices[i]];
		}
	}
}
