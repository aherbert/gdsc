/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2018 Alex Herbert
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package gdsc.foci.model;

/**
 * Defines the different processing states for the FindFoci algorithm.
 */
public enum FindFociState
{
	/** The initial state. */
	INITIAL,
	/** The find maxima state. */
	FIND_MAXIMA,
	/** The search state. */
	SEARCH,
	/** The merge height state. */
	MERGE_HEIGHT,
	/** The merge size state. */
	MERGE_SIZE,
	/** The merge saddle state. */
	MERGE_SADDLE,
	/** The calculate results state. */
	CALCULATE_RESULTS,
	/** The calculate output mask state. */
	CALCULATE_OUTPUT_MASK,
	/** The show results state. */
	SHOW_RESULTS,
	/** The complete state. */
	COMPLETE
}
