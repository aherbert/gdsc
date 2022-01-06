/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2022 Alex Herbert
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

import ij.ImagePlus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Contains the results of the FindFoci algorithm.
 */
public class FindFociResults {
  /** The mask. */
  final ImagePlus mask;

  /** The results. */
  final List<FindFociResult> results;

  /** The statistics. */
  final FindFociStatistics stats;

  /**
   * Instantiates a new find foci result.
   *
   * @param mask the mask
   * @param results the results
   * @param stats the statistics
   */
  public FindFociResults(ImagePlus mask, List<FindFociResult> results, FindFociStatistics stats) {
    this.mask = mask;
    this.results = results;
    this.stats = stats;
  }

  /**
   * Instantiates a new find foci results.
   *
   * @param mask the mask
   * @param results the results
   * @param stats the statistics
   */
  public FindFociResults(ImagePlus mask, FindFociResult[] results, FindFociStatistics stats) {
    this.mask = mask;
    this.results = (results == null) ? new ArrayList<>(0) : Arrays.asList(results);
    this.stats = stats;
  }

  /**
   * Copy constructor.
   *
   * @param source the source
   */
  protected FindFociResults(FindFociResults source) {
    mask = source.mask;
    results = source.results;
    stats = source.stats;
  }

  /**
   * Returns a shallow copy of this set of results.
   *
   * @return the copy
   */
  public FindFociResults copy() {
    return new FindFociResults(this);
  }

  /**
   * Gets the mask.
   *
   * @return the mask
   */
  public ImagePlus getMask() {
    return mask;
  }

  /**
   * Gets the results.
   *
   * @return the results
   */
  public List<FindFociResult> getResults() {
    return results;
  }

  /**
   * Gets the statistics.
   *
   * @return the statistics
   */
  public FindFociStatistics getStats() {
    return stats;
  }

  /**
   * Increment the z position of the results.
   *
   * <p>By default the FindFociResult z position uses 0-based indexing. Incrementing to 1-based
   * indexing matches the indexing used by ImageJ ImageStacks.
   *
   * @param results the results
   */
  public static void incrementZ(List<FindFociResult> results) {
    results.forEach(r -> r.z++);
  }
}
