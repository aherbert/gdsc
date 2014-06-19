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
 * Stores a 2D/3D point with a value and an assigned flag.
 */
public class GridException extends Exception
{
	private static final long serialVersionUID = 5920992981718121344L;

	public GridException(String message)
	{
		super(message);
	}

	public GridException(String message, Throwable cause)
	{
		super(message, cause);
	}
}