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
package uk.ac.sussex.gdsc.colocalisation.cda.engine;

/**
 * Used to store the results of comparing the intersection of two images.
 */
public class IntersectResult {
  /** The first sum. */
  public long sum1;

  /** The second sum. */
  public long sum2;

  /** The correlation. */
  public double r;

  /**
   * Instantiates a new intersect result.
   *
   * @param sum1 the first sum
   * @param sum2 the second sum
   * @param r The correlation
   */
  public IntersectResult(long sum1, long sum2, double r) {
    this.sum1 = sum1;
    this.sum2 = sum2;
    this.r = r;
  }
}
