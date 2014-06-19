package gdsc.colocalisation.cda.engine;

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
 * Specifies a translation shift for the CDA algorithm 
 */
public class CDAJob
{
	public int n, x, y;
	
	public CDAJob(int n, int x, int y)
	{
		this.n = n;
		this.x = x;
		this.y = y;
	}
}
