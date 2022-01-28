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

package uk.ac.sussex.gdsc.ij.foci;

/**
 * Contains the foci saddle result of the FindFoci algorithm.
 */
public class FindFociSaddle {
  /** The saddle peak id. */
  int id;

  /** The saddle value. */
  float value;

  /** Used for sorting. */
  int order;

  /**
   * Instantiates a new find foci saddle.
   *
   * @param id the id
   * @param value the value
   */
  public FindFociSaddle(int id, float value) {
    this.id = id;
    this.value = value;
  }

  /**
   * Copy constructor.
   *
   * @param source the source
   */
  public FindFociSaddle(FindFociSaddle source) {
    this.id = source.id;
    this.value = source.value;
  }

  /**
   * Returns a copy of this saddle.
   *
   * @return the find foci saddle
   */
  public FindFociSaddle copy() {
    return new FindFociSaddle(this);
  }

  /**
   * Compare the two results by value (descending) then id (ascending).
   *
   * @param r1 the first result
   * @param r2 the second result
   * @return the result [-1, 0, 1]
   */
  public static int compare(FindFociSaddle r1, FindFociSaddle r2) {
    if (r1.value > r2.value) {
      return -1;
    }
    if (r1.value < r2.value) {
      return 1;
    }
    // For compatibility with the legacy code the saddles must be sorted by Id if they are the same
    // value
    return Integer.compare(r1.id, r2.id);
  }

  /**
   * Gets the id.
   *
   * @return the id
   */
  public int getId() {
    return id;
  }

  /**
   * Gets the value.
   *
   * @return the value
   */
  public float getValue() {
    return value;
  }
}
