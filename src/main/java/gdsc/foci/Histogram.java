package gdsc.foci;

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

class Histogram implements Cloneable
{
	final int[] h;
	final int minBin, maxBin;

	protected Histogram(int[] h, int minBin, int maxBin)
	{
		this.h = h;
		this.minBin = minBin;
		this.maxBin = maxBin;				
	}
	
	public Histogram(int[] h)
	{
		// Find min and max bins
		int min = 0;
		int max = h.length - 1;
		while ((h[min] == 0) && (min < max))
			min++;
		while ((h[max] == 0) && (max > min))
			max--;
		minBin = min;
		maxBin = max;
		this.h = h;
	}

	public Histogram compact(int size)
	{
		// Ignore 
		return this;
	}

	public float getValue(int i)
	{
		return i;
	}

	@Override
	public Histogram clone()
	{
		return new Histogram(this.h.clone(), minBin, maxBin);
	}
}