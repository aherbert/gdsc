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

class IntHistogram extends Histogram
{
	final int offset;

	protected IntHistogram(int[] h, int minBin, int maxBin, int offset)
	{
		super(h, minBin, maxBin);
		this.offset = offset;
	}
	
	public IntHistogram(int[] h, int offset)
	{
		super(h);
		this.offset = offset;
	}

	public float getValue(int i)
	{
		return offset + i;
	}

	@Override
	public IntHistogram clone()
	{
		return new IntHistogram(this.h.clone(), minBin, maxBin, offset);
	}
}