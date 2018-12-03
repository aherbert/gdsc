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
 * Contains the foci saddle result of the FindFoci algorithm.
 */
public class FindFociSaddle implements Cloneable, Comparable<FindFociSaddle> {
  /** The saddle peak id. */
  public int id;

  /** The saddle value. */
  public float value;

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
   * Returns a copy of this saddle.
   *
   * @return the find foci saddle
   */
  @Override
  public FindFociSaddle clone() {
    try {
      return (FindFociSaddle) super.clone();
    } catch (final CloneNotSupportedException e) {
      return null;
    }
  }

  /** {@inheritDoc} */
  @Override
  public int compareTo(FindFociSaddle that) {
    if (this.value > that.value) {
      return -1;
    }
    if (this.value < that.value) {
      return 1;
    }
    // For compatibility with the legacy code the saddles must be sorted by Id if they are the same
    // value
    // return 0;
    return this.id - that.id;
  }
}
