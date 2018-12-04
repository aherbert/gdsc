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

package uk.ac.sussex.gdsc.foci;

/**
 * Contains the results of the FindFoci algorithm after the merge stage.
 */
public class FindFociMergeResults {
  /** The results array. */
  public FindFociResult[] resultsArray;

  /** The original number of peaks. */
  public int originalNumberOfPeaks;

  /**
   * Instantiates a new find foci merge results.
   *
   * @param resultsArray the results array
   * @param originalNumberOfPeaks the original number of peaks
   */
  public FindFociMergeResults(FindFociResult[] resultsArray, int originalNumberOfPeaks) {
    this.resultsArray = resultsArray;
    this.originalNumberOfPeaks = originalNumberOfPeaks;
  }
}
