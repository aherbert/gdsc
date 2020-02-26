/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2020 Alex Herbert
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

package uk.ac.sussex.gdsc.trackmate.detector;

import fiji.plugin.trackmate.Spot;
import ij.gui.Roi;

/**
 * Specialisation of the Spot class to store the Nucleus ROI.
 */
public class NucleusSpot extends Spot {
  /** The roi. */
  private final Roi roi;

  /**
   * Create a new instance.
   *
   * @param x the x
   * @param y the y
   * @param z the z
   * @param radius the radius
   * @param quality the quality
   * @param roi the roi
   */
  public NucleusSpot(double x, double y, double z, double radius, double quality, Roi roi) {
    super(x, y, z, radius, quality);
    this.roi = roi;
  }

  /**
   * Gets the roi.
   *
   * @return the roi
   */
  public Roi getRoi() {
    return roi;
  }

  @Override
  public boolean equals(Object other) {
    // Default to super implementation
    return super.equals(other);
  }

  @Override
  public int hashCode() {
    // Default to super implementation
    return super.hashCode();
  }
}
