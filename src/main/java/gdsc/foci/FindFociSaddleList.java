package gdsc.foci;

import java.util.Arrays;
import java.util.Comparator;

/*----------------------------------------------------------------------------- 
 * GDSC Plugins for ImageJ
 * 
 * Copyright (C) 2016 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

/**
 * Contains the foci saddle results of the FindFoci algorithm.
 */
public class FindFociSaddleList implements Cloneable
{
	private static class IdSaddleComparator implements Comparator<FindFociSaddle>
	{
		public int compare(FindFociSaddle o1, FindFociSaddle o2)
		{
			final int result = o1.id - o2.id;
			if (result != 0)
				return result;
			return o1.order - o2.order;
		}
	}

	private static class OrderSaddleComparator implements Comparator<FindFociSaddle>
	{
		public int compare(FindFociSaddle o1, FindFociSaddle o2)
		{
			return o1.order - o2.order;
		}
	}

	private static IdSaddleComparator idSaddleComparator;
	private static OrderSaddleComparator orderSaddleComparator;
	static
	{
		idSaddleComparator = new IdSaddleComparator();
		orderSaddleComparator = new OrderSaddleComparator();
	}

	int size;
	FindFociSaddle[] list;

	/**
	 * Instantiates a new find foci saddle.
	 */
	public FindFociSaddleList()
	{
		this.list = new FindFociSaddle[0];
		this.size = 0;
	}

	/**
	 * Instantiates a new find foci saddle.
	 */
	public FindFociSaddleList(FindFociSaddle[] list)
	{
		this.list = list;
		this.size = list.length;
	}

	/**
	 * get the size of the list
	 * 
	 * @return The size
	 */
	public int getSize()
	{
		return size;
	}

	/**
	 * Get the saddle for the index
	 * 
	 * @param i
	 *            The index
	 * @return The saddle
	 */
	public FindFociSaddle get(final int i)
	{
		return list[i];
	}

	/**
	 * Add a new saddle. Does not check there is capacity to do this. Call {@link #ensureExtraCapacity(int)} first.
	 * 
	 * @param saddle
	 *            The saddle
	 */
	public void add(FindFociSaddle saddle)
	{
		list[size++] = saddle;
	}

	/**
	 * Clear the list but do not reduce the capacity
	 */
	public void clear()
	{
		clear(0);
	}

	/**
	 * Clear the list from the given position but do not reduce the capacity
	 * 
	 * @param position
	 *            The position
	 */
	public void clear(int position)
	{
		if (position == size)
			return;
		final int oldSize = size;
		size = position;
		while (position < oldSize)
			list[position++] = null;
	}

	/**
	 * Clear the list and set capacity to zero. This should be called to allow the garbage collector to work on freeing
	 * memory.
	 */
	public void erase()
	{
		clear();
		list = null;
	}

	/**
	 * Ensure that n extra elements can be added to the list.
	 * <p>
	 * Note this is different from the ensureCapacity() method of ArrayList which checks total capacity.
	 * 
	 * @param n
	 *            The number of extra elements (this should not be negative)
	 */
	public void ensureExtraCapacity(int n)
	{
		if (list.length - size >= n)
			return;
		// Increase size
		list = Arrays.copyOf(list, size + n);

		// On the assumption that any list which needs extra capacity will be from a large peak
		// This does not appear to affect speed so it is left commented out.
		//list = Arrays.copyOf(list, Math.max(size + n, (int) (list.length * 3)));
	}

	/**
	 * Get the total capacity of the list
	 * 
	 * @return The total capacity
	 */
	public int getCapacity()
	{
		return list.length;
	}

	/**
	 * Returns a hard copy of this saddle.
	 *
	 * @return the find foci saddle
	 * @see java.lang.Object#clone()
	 */
	@Override
	public FindFociSaddleList clone()
	{
		// Make same length so that add operations behave exactly the same
		if (size == 0)
			return new FindFociSaddleList();
		final FindFociSaddle[] list = new FindFociSaddle[this.list.length];
		for (int i = 0; i < size; i++)
			list[i] = this.list[i].clone();
		final FindFociSaddleList newList = new FindFociSaddleList(list);
		newList.size = this.size;
		return newList;
	}

	/**
	 * Sort the list
	 */
	public void sort()
	{
		if (size < 0)
			return;
		Arrays.sort(list, 0, size);
	}

	/**
	 * Sort the list using the comparator
	 * 
	 * @param saddleComparator
	 */
	public void sort(Comparator<FindFociSaddle> saddleComparator)
	{
		if (size < 0)
			return;
		Arrays.sort(list, 0, size, saddleComparator);
	}

	/**
	 * Remove duplicate Ids from the list and maintain the current order, or else do a default sort
	 * 
	 * @param maintain
	 *            Maintain the current order, otherwise do default sort
	 */
	public void removeDuplicates(boolean maintain)
	{
		if (size < 2)
			return;
		for (int i = 0; i < size; i++)
			list[i].order = i;
		sort(idSaddleComparator);
		int lastId = 0;
		int newSize = 0;
		for (int i = 0; i < size; i++)
		{
			if (lastId != list[i].id)
			{
				list[newSize++] = list[i];
				lastId = list[i].id;
			}
		}
		clear(newSize);
		if (maintain)
			sort(orderSaddleComparator);
		else
			sort();
	}

	/**
	 * Sort the list by Id but otherwise maintain the current order
	 */
	public void sortById()
	{
		if (size < 2)
			return;
		for (int i = 0; i < size; i++)
			list[i].order = i;
		sort(idSaddleComparator);
	}
}