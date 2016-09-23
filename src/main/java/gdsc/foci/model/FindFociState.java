package gdsc.foci.model;

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
 * Defines the different processing states for the FindFoci algorithm
 */
public enum FindFociState
{
	INITIAL,
	FIND_MAXIMA,
	SEARCH,
	MERGE_HEIGHT,
	MERGE_SIZE,
	MERGE_SADDLE,
	CALCULATE_RESULTS,
	CALCULATE_OUTPUT_MASK,
	SHOW_RESULTS,
	COMPLETE
}
