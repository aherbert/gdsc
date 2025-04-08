/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2025 Alex Herbert
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
 * Stores a 2D/3D point with an assigned Id, channel and frame. The point supports XYZCT positioning
 * with additional identifiers for use in algorithms.
 */
public class AssignedPoint extends BasePoint {
  /** The id. */
  protected int id;

  /** The assigned id. */
  protected int assignedId;

  /** The channel. */
  protected int channel;

  /** The frame. */
  protected int frame;

  /**
   * Instantiates a new assigned point using XYZCT position.
   *
   * @param x the x
   * @param y the y
   * @param z the z
   * @param channel the channel (c)
   * @param frame the time frame (t)
   * @param id the id
   */
  public AssignedPoint(int x, int y, int z, int channel, int frame, int id) {
    super(x, y, z);
    this.channel = channel;
    this.frame = frame;
    this.id = id;
  }

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

  /**
   * Sets the point Channel.
   *
   * @param channel the new channel
   */
  public void setChannel(int channel) {
    this.channel = channel;
  }

  /**
   * Gets the channel.
   *
   * @return the channel
   */
  public int getChannel() {
    return channel;
  }

  /**
   * Sets the point Frame.
   *
   * @param frame the new frame
   */
  public void setFrame(int frame) {
    this.frame = frame;
  }

  /**
   * Gets the frame.
   *
   * @return the frame
   */
  public int getFrame() {
    return frame;
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
        && assignedId == that.assignedId && channel == that.channel && frame == that.frame;
  }

  @Override
  public int hashCode() {
    return (41 * (41 * (41 * (41 * (41 * (41 * (41 + x) + y) + z) + id) + assignedId) + channel)
        + frame);
  }
}
