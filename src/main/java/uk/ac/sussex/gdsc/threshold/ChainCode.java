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

package uk.ac.sussex.gdsc.threshold;

import gnu.trove.list.array.TIntArrayList;
import uk.ac.sussex.gdsc.core.utils.ValidationUtils;

/**
 * Stores a chain code. Stores the origin x,y and a set of directions in a chain of 8 neighbour
 * connected 2D pixels.
 *
 * <p>The directions can be used to find the x,y coordinates for each point using the directions for
 * successive points from the origin.
 */
public class ChainCode {
  private static final double ROOT2 = Math.sqrt(2);

  /** The number of directions for an 8 neighbour connected pixel. */
  public static final int DIRECTION_SIZE = 8;
  /**
   * Describes the x-direction for the chain code.
   */
  private static final int[] DIR_X_OFFSET = {0, 1, 1, 1, 0, -1, -1, -1};
  /**
   * Describes the y-direction for the chain code.
   */
  private static final int[] DIR_Y_OFFSET = {-1, -1, 0, 1, 1, 1, 0, -1};

  /**
   * Describes the length for the chain code.
   */
  private static final float[] DIR_LENGTH =
      {1, (float) ROOT2, 1, (float) ROOT2, 1, (float) ROOT2, 1, (float) ROOT2};

  private int x;
  private int y;

  private final TIntArrayList run = new TIntArrayList();
  private float length;
  private String toString;

  /**
   * Create a chain code starting at the specified coordinates.
   *
   * @param x the x
   * @param y the y
   */
  public ChainCode(int x, int y) {
    this.x = x;
    this.y = y;
    clearCachedState();
  }

  /**
   * Remove the cached state as the chain code has been modified.
   */
  private void clearCachedState() {
    length = -1;
    toString = null;
  }

  /**
   * Extend the chain code in the given direction.
   *
   * @param direction the direction
   * @throws IllegalArgumentException If the direction is not in the range [0-7]
   */
  public void add(int direction) {
    ValidationUtils.checkArgument(direction >= 0 && direction < DIRECTION_SIZE,
        "Invalid direction: %d", direction);
    run.add(direction);
    clearCachedState();
  }

  /**
   * Get the x origin.
   *
   * @return the x origin
   */
  public int getX() {
    return x;
  }

  /**
   * Get the y origin.
   *
   * @return the y origin
   */
  public int getY() {
    return y;
  }

  /**
   * Get the chain code length from the origin to the end of the chain.
   *
   * @return the length
   */
  public float getLength() {
    float len = length;
    if (len < 0) {
      if (run.isEmpty()) {
        len = length = 0;
      } else {
        final int[] count = new int[2];
        run.forEach(code -> {
          // Note:
          // even number chain codes are 4n connected and have length 1
          // odd number chain codes are diagonal and have length sqrt(2)
          count[code % 2]++;
          return true;
        });
        len = length = (float) (count[0] + count[1] * ROOT2);
      }
    }
    return len;
  }

  /**
   * Get the number of points in the chain code (includes the x,y origin).
   *
   * @return the size
   */
  public int getSize() {
    return run.size() + 1;
  }

  /**
   * Get the chain code run.
   *
   * @return the run
   */
  public int[] getRun() {
    return run.toArray();
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    if (toString == null) {
      final StringBuilder sb = new StringBuilder();
      sb.append(x).append(",").append(y);
      run.forEach(code -> {
        sb.append(":").append(DIR_X_OFFSET[code]).append(",").append(DIR_Y_OFFSET[code]);
        return true;
      });
      toString = sb.toString();
    }
    return toString;
  }

  /**
   * Compares the two specified {@code ChainCode} values.
   *
   * <p>The comparison uses ascending order of the properties: {@link #getLength()};
   * {@link #getX()}; and {@link #getY()}.
   *
   * @param o1 the first {@code ChainCode} to compare
   * @param o2 the second {@code ChainCode} to compare
   * @return the value {@code 0} if equal; a value less than {@code 0} if {@code o1} is less than
   *         {@code o2}; and a value greater than {@code 0} if {@code o1} is greater than
   *         {@code o2}.
   */
  public static int compare(ChainCode o1, ChainCode o2) {
    if (o1.getLength() < o2.getLength()) {
      return -1;
    }
    if (o1.getLength() > o2.getLength()) {
      return 1;
    }
    if (o1.getX() < o2.getX()) {
      return -1;
    }
    if (o1.getX() > o2.getX()) {
      return 1;
    }
    return Integer.compare(o1.getY(), o2.getY());
  }

  /**
   * Gets the end position.
   *
   * @return the end [endX, endY]
   */
  public int[] getEnd() {
    int endx = this.x;
    int endy = this.y;

    for (int i = 0; i < run.size(); i++) {
      final int code = run.getQuick(i);
      endx += DIR_X_OFFSET[code];
      endy += DIR_Y_OFFSET[code];
    }

    return new int[] {endx, endy};
  }

  /**
   * Create a new chain code in the opposite direction.
   *
   * @return the chain code
   */
  public ChainCode reverse() {
    int endx = this.x;
    int endy = this.y;

    // Do in descending order
    final ChainCode reverse = new ChainCode(endx, endy);
    for (int i = run.size() - 1; i >= 0; i--) {
      final int code = run.getQuick(i);
      endx += DIR_X_OFFSET[code];
      endy += DIR_Y_OFFSET[code];
      // Invert the chain code
      reverse.add((code + 4) % DIRECTION_SIZE);
    }

    // Update the origin
    reverse.x = endx;
    reverse.y = endy;

    return reverse;
  }

  /**
   * Gets the x direction for the chain direction index.
   *
   * <p>The returned value is a direction for an 8 neighbour connected pixel and will have a value
   * of -1, 0, or 1.
   *
   * @param index the index (range 0-7)
   * @return the x direction
   */
  public static final int getXDirection(int index) {
    return DIR_X_OFFSET[index];
  }

  /**
   * Gets the y direction for the chain direction index.
   *
   * <p>The returned value is a direction for an 8 neighbour connected pixel and will have a value
   * of -1, 0, or 1.
   *
   * @param index the index (range 0-7)
   * @return the y direction
   */
  public static final int getYDirection(int index) {
    return DIR_Y_OFFSET[index];
  }

  /**
   * Gets the length for the combined (x,y) direction for the chain direction index.
   *
   * <p>The returned value is the length in the given (x,y) direction for an 8 neighbour connected
   * pixel and will have a value of 1 or sqrt(2).
   *
   * @param index the index (range 0-7)
   * @return the length
   */
  public static final float getDirectionLength(int index) {
    return DIR_LENGTH[index];
  }
}
