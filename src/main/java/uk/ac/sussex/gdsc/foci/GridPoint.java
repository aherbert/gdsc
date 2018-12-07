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
 * Stores a 2D/3D point with a value and an assigned flag.
 */
public class GridPoint extends ValuedPoint {
  private boolean assigned = false;

  /**
   * Instantiates a new grid point.
   *
   * @param x the x
   * @param y the y
   * @param z the z
   * @param value the value
   */
  public GridPoint(int x, int y, int z, float value) {
    super(x, y, z, value);
  }

  /**
   * Instantiates a new grid point.
   *
   * @param point the point
   * @param value the value
   */
  public GridPoint(AssignedPoint point, float value) {
    super(point, value);
  }

  /**
   * Sets the assigned.
   *
   * @param assigned the new assigned
   */
  public void setAssigned(boolean assigned) {
    this.assigned = assigned;
  }

  /**
   * Checks if is assigned.
   *
   * @return true, if is assigned
   */
  public boolean isAssigned() {
    return assigned;
  }
}
