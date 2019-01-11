/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2019 Alex Herbert
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
 * Stores a 2D/3D point with an assigned Id.
 */
public class AssignedPoint extends BasePoint {
  /** The id. */
  protected int id;

  /** The assigned id. */
  protected int assignedId;

  /**
   * Instantiates a new assigned point.
   *
   * @param x the x
   * @param y the y
   * @param z the z
   * @param id the id
   */
  public AssignedPoint(int x, int y, int z, int id) {
    super(x, y, z);
    this.id = id;
  }

  /**
   * Instantiates a new assigned point.
   *
   * @param x the x
   * @param y the y
   * @param id the id
   */
  public AssignedPoint(int x, int y, int id) {
    super(x, y);
    this.id = id;
  }

  /**
   * Sets the point Id.
   *
   * @param id the new id
   */
  public void setId(int id) {
    this.id = id;
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
   * Sets the assigned id.
   *
   * @param assignedId the assignedId to set
   */
  public void setAssignedId(int assignedId) {
    this.assignedId = assignedId;
  }

  /**
   * Gets the assigned id.
   *
   * @return the assignedId
   */
  public int getAssignedId() {
    return assignedId;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    // Must be the same class, allowing subtypes their own implementation
    if (object == null || getClass() != object.getClass()) {
      return false;
    }

    // cast to native object is now safe
    final AssignedPoint that = (AssignedPoint) object;

    return x == that.x && y == that.y && z == that.z && id == that.id
        && assignedId == that.assignedId;
  }

  @Override
  public int hashCode() {
    // Note: floatToRawIntBits does not unify all possible NaN values
    // However since the equals() will fail for NaN values we are not
    // breaking the java contract.
    return (41 * (41 * (41 * (41 * (41 + Float.floatToRawIntBits(x)) + Float.floatToRawIntBits(y))
        + Float.floatToRawIntBits(z)) + id) + assignedId);
  }
}
