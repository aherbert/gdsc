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

import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.process.ImageProcessor;
import java.util.Arrays;
import java.util.BitSet;

/**
 * Outline objects defined by contiguous pixels of the same value.
 */
public class ObjectOutliner {
  /**
   * The x-direction offsets for tracing outlines, arranged as 8-connected winding round clockwise
   * where left is -1 and right is +1 in x.
   */
  private static final int[] DIR_X_OFFSET = {-1, -1, 0, 1, 1, 1, 0, -1};
  /**
   * The y-direction offsets for tracing outlines, arranged as 8-connected winding round clockwise
   * where up is -1 and down is +1 in y.
   */
  private static final int[] DIR_Y_OFFSET = {0, -1, -1, -1, 0, 1, 1, 1};

  /** The mask to make an odd number even. Masks out the lowest bit. */
  static final int EVEN_MASK = ~0x1;
  /** The left direction in the 8-connected directions. */
  static final int DIR_LEFT = 0;
  /** The up direction in the 8-connected directions. */
  static final int DIR_UP = 2;
  /** The right and up direction in the 8-connected directions. */
  static final int DIR_RIGHT_UP = 3;
  /** The right direction in the 8-connected directions. */
  static final int DIR_RIGHT = 4;
  /** The down direction in the 8-connected directions. */
  static final int DIR_DOWN = 6;

  private final ImageProcessor ip;
  private boolean eightConnected;

  private final int maxx;
  private final int xlimit;
  private final int ylimit;

  /** The 8-connected offset table. */
  private final int[] offset;

  /** The outline working space. */
  private final Outline outline;

  /**
   * Helper class to store the outline of a region.
   *
   * <p>This stores the outline as a set of coordinates that trace a line with the object pixels on
   * the right-hand side when orientated by the direction.
   */
  private static class Outline {
    /** The current x position. */
    int x;
    /** The current y position. */
    int y;
    /** The x-points. */
    int[] xp;
    /** The y-points. */
    int[] yp;
    /** The count of points. */
    int count;
    /** The current direction. This is always a 4-connected direction. */
    int dir;

    /**
     * Create a new instance.
     */
    Outline() {
      xp = new int[16];
      yp = new int[xp.length];
    }

    /**
     * Initialise.
     *
     * @param x the x origin
     * @param y the y origin
     * @param direction of the first edge
     */
    void initialise(int x, int y, int direction) {
      count = 0;
      this.x = x;
      this.y = y;
      addPoint();
      move(direction & EVEN_MASK);
    }

    /**
     * Adds the current point to the set of points. This is only done when the outline direction
     * changes.
     */
    private void addPoint() {
      if (count == this.xp.length) {
        final int newLength = xp.length * 2;
        xp = Arrays.copyOf(xp, newLength);
        yp = Arrays.copyOf(yp, newLength);
      }
      this.xp[count] = x;
      this.yp[count] = y;
      count++;
    }

    /**
     * Do a 4-connected move of the current location.
     *
     * @param direction the direction (must be 4-connected)
     */
    private void move(int direction) {
      this.dir = direction;
      // Equivalent of:
      // x += ObjectAnalyzer.DIR_X_OFFSET8[dir]
      // y += ObjectAnalyzer.DIR_Y_OFFSET8[dir]
      // Note that for Y up is negative to match the ImageJ visual orientation.
      switch (direction) {
        case DIR_LEFT:
          x--;
          break;
        case DIR_UP:
          y--;
          break;
        case DIR_RIGHT:
          x++;
          break;
        case DIR_DOWN:
        default:
          y++;
          break;
      }
    }

    /**
     * Adds the trace direction to the outline. This can be an 8-connected move direction.
     *
     * <p>This method compares the new direction with the current outline direction and then updates
     * the position by tracing the outline up to the next pixel.
     *
     * @param direction the direction
     */
    void add(int direction) {
      // (x,y) is at the corner of the object pixel most recently outlined.
      // Direction is the current orientation of the last edge.
      // @formatter:off
      // +---+---+---+
      // | 5 |   | 7 |
      // +---+--x,y--+
      // | 4 | X | 0 |
      // +---+---+---+
      // | 3 | 2 | 1 |
      // +---+---+---+
      // @formatter:on

      // Compute the change in direction.
      // This should be a value in the range -1 to 4.
      // The change -2 (or +6) is not possible as the trace is following the outline with
      // the object on the right. This is the left side and should be outside the object.
      final int change = (direction + 8 - dir) % 8;

      // Note: A new point is only added to the outline when a turn is made.
      // Otherwise the current location is moved.
      switch (change) {
        case 0:
          straight();
          break;
        case 1:
          right();
          left();
          break;
        case 2:
          right();
          straight();
          break;
        case 3:
          right();
          right();
          left();
          break;
        case 4:
          right();
          right();
          straight();
          break;
        case 5:
          right();
          right();
          right();
          left();
          break;
        // Miss out case 6 as this is an error
        case 7:
          left();
          break;
        default:
          throw new IllegalStateException("Unsupported move direction: " + change);
      }
    }

    /**
     * Mark the end of the current edge and then turn the direction <strong>left</strong> (90
     * degrees) and start a new edge.
     */
    private void left() {
      addPoint();
      move((dir + 6) % 8);
    }

    /**
     * Mark the end of the current edge and then turn the direction <strong>right</strong> (90
     * degrees) and start a new edge.
     */
    private void right() {
      addPoint();
      move((dir + 2) % 8);
    }

    /**
     * Move in the current direction.
     */
    private void straight() {
      move(dir);
    }

    /**
     * Gets the ROI.
     *
     * @return the ROI
     */
    Roi getRoi() {
      // The final move back to the origin X may put the position as a, c, or d:
      // Complete the outline back to a.
      // @formatter:off
      // +---+---+---+
      // |   |   | C |
      // +---a---b---+
      // |   | X | D |
      // +---d---c---+
      // | A | A | D |
      // +---+---+---+
      // @formatter:on
      if (x == xp[0] + 1) {
        // Currently at corner c.
        // (Assumes y == yp[0] + 1)
        addPoint();
        move(DIR_LEFT);
      }
      if (y == yp[0] + 1) {
        // Currently at corner d.
        // (Assumes x == xp[0])
        addPoint();
      }

      // Optimise rectangles
      if (count == 4) {
        final int width = xp[1] - xp[0];
        final int height = yp[3] - yp[0];
        return new Roi(xp[0], yp[0], width, height);
      }

      final int[] xpoints = Arrays.copyOf(xp, count);
      final int[] ypoints = Arrays.copyOf(yp, count);
      final int npoints = count;
      return new PolygonRoi(xpoints, ypoints, npoints, Roi.FREEROI);
    }
  }

  /**
   * Create a new instance using 4-connected pixels.
   *
   * @param ip the image
   */
  public ObjectOutliner(ImageProcessor ip) {
    this(ip, false);
  }

  /**
   * Create a new instance.
   *
   * @param ip the image
   * @param eightConnected the eight connected flag
   */
  public ObjectOutliner(ImageProcessor ip, boolean eightConnected) {
    this.ip = ip;
    this.eightConnected = eightConnected;

    maxx = ip.getWidth();
    final int maxy = ip.getHeight();

    xlimit = maxx - 1;
    ylimit = maxy - 1;

    // Create the offset table (for single array 2D neighbour comparisons)
    offset = new int[DIR_X_OFFSET.length];
    for (int d = offset.length; d-- > 0;) {
      offset[d] = maxx * DIR_Y_OFFSET[d] + DIR_X_OFFSET[d];
    }

    outline = new Outline();
  }

  /**
   * Returns whether the neighbour in a given direction is within the image. This assumes the
   * direction is from the 8-connected offset table.
   *
   * <p>NOTE: it is assumed that the pixel x,y itself is within the image! Uses class variables
   * xlimit, ylimit: (dimensions of the image)-1
   *
   * @param x x-coordinate of the pixel that has a neighbour in the given direction
   * @param y y-coordinate of the pixel that has a neighbour in the given direction
   * @param direction the direction from the pixel towards the neighbour
   * @return true if the neighbour is within the image (provided that x, y is within)
   */
  private boolean isWithinXy(int x, int y, int direction) {
    switch (direction) {
      case 0:
        return (x > 0);
      case 1:
        return (y > 0 && x > 0);
      case 2:
        return (y > 0);
      case 3:
        return (y > 0 && x < xlimit);
      case 4:
        return (x < xlimit);
      case 5:
        return (y < ylimit && x < xlimit);
      case 6:
        return (y < ylimit);
      case 7:
      default:
        return (y < ylimit && x > 0);
    }
  }

  /**
   * Checks if the position is inside the image border.
   *
   * @param x the x position
   * @param y the y position
   * @return true if inside
   */
  private boolean isInner(final int x, final int y) {
    return (y != 0 && y != ylimit) && (x != 0 && x != xlimit);
  }

  /**
   * Checks if objects should use 8-connected pixels. The default is 4-connected.
   *
   * @return True if objects should use 8-connected pixels.
   */
  public boolean isEightConnected() {
    return eightConnected;
  }

  /**
   * Sets if objects should use 8-connected pixels. The default is 4-connected.
   *
   * @param eightConnected True if objects should use 8-connected pixels.
   */
  public void setEightConnected(boolean eightConnected) {
    this.eightConnected = eightConnected;
  }

  /**
   * Create an ROI outline of all pixels with the same value that are connected to the specified
   * position.
   *
   * @param x the x position
   * @param y the y position
   * @return the roi
   */
  public Roi outline(int x, int y) {
    return outline(y * ip.getWidth() + x);
  }

  /**
   * Create an ROI outline of all pixels with the same value that are connected to the specified
   * position.
   *
   * @param index the pixel index
   * @return the roi
   */
  public Roi outline(int index) {
    // Move to the upper-left of the pixel region
    final int index0 = findUpperLeft(index);

    // Outline
    return traceOutline(index0);
  }


  /**
   * Find the upper-left of the pixel region.
   *
   * @param index the index
   * @return the upper-left index
   */
  private int findUpperLeft(int index) {
    final int object = ip.get(index);

    int newIndex = index;
    int xp = index % maxx;
    int yp = index / maxx;

    final int increment = (eightConnected) ? 1 : 2;
    boolean repeat = true;
    while (repeat) {
      repeat = false;
      for (int d = 0; d <= 2; d += increment) {
        if (isWithinXy(xp, yp, d) && ip.get(newIndex + offset[d]) == object) {
          // Move
          repeat = true;
          newIndex += offset[d];
          xp += DIR_X_OFFSET[d];
          yp += DIR_Y_OFFSET[d];
        }
      }
    }

    return newIndex;
  }

  /**
   * Outline the object starting from the given index to create an ROI.
   *
   * <p>Note: The index must be for a pixel from the top-left of the object to outline so that the
   * origin at A has no matches in the left, left-up, or up directions:
   *
   * <pre>
   * +---+---+---+
   * |   |   | a |
   * +---+---+---+
   * |   | A | a |
   * +---+---+---+
   * | a | a | a |
   * +---+---+---+
   * </pre>
   *
   * @param index0 the start index
   * @return the roi
   */
  Roi traceOutline(int index0) {
    final int ox = index0 % maxx;
    final int oy = index0 / maxx;

    // This is called when the pixel to the left is not the same value
    // and there are no pixels above this of the same value.
    // The start position is the upper-left corner of the pixel, e.g. A:
    // E.g.
    // @formatter:off
    // +---+---+---+
    // |   |   | a |
    // +---+---+---+
    // |   | A | a |
    // +---+---+---+
    // | a | a | a |
    // +---+---+---+
    // @formatter:on

    // Trace the outline using an 8-connected search.
    // This uses a different offset map to the one in this class as the 8 directions are
    // consecutive around the compass.
    // The outline is stored as a set of edges that are orientated on the pixel edges.

    // Need to allow the search to revisit the start and then extend the other way if
    // for example the shape is:
    // ..Aaaa
    // .a
    // a

    // So keep a track of the 8 directions processed from the start point.
    final BitSet processedDirections = new BitSet(8);

    // Note that direction 0 (-1, 0) will be the first in the search for a start direction.
    // This will not match as it has been processed in the sweep over the pixels from
    // low x to high x. Similar arguments for all of the top row so the search can start at 3:
    // @formatter:off
    // +---+---+---+
    // | 1 | 2 |   |
    // +---+---+---+
    // | 0 | X |   |
    // +---+---+---+
    // |   |   |   |
    // +---+---+---+
    // @formatter:on
    processedDirections.set(DIR_LEFT, DIR_RIGHT_UP);

    int dir = findStartDirection(index0, processedDirections);
    if (dir < 0) {
      // An object of size 1
      return new Roi(ox, oy, 1, 1);
    }

    // Start the outline with the first edge the top of the start pixel:
    // @formatter:off
    //     +---+
    //       X
    // @formatter:on
    outline.initialise(ox, oy, DIR_RIGHT);

    while (dir >= 0) {
      dir = followOutline(index0, outline, dir);
      updateProcessedDirections(processedDirections, dir);
      dir = findStartDirection(index0, processedDirections);
    }

    return outline.getRoi();
  }

  /**
   * Find the next start direction from the index that has not been processed.
   *
   * @param index the index
   * @param ip the ip
   * @param processedDirections the processed directions
   * @return the start direction
   */
  private int findStartDirection(int index, BitSet processedDirections) {
    final int object = ip.get(index);
    final int x = index % maxx;
    final int y = index / maxx;
    final boolean isInner = isInner(x, y);
    for (int d = 0; d < 8; d++) {
      // Skip already processed directions
      if (processedDirections.get(d)) {
        continue;
      }
      processedDirections.set(d);
      if (isAllowed(isInner, object, index, x, y, d)) {
        return d;
      }
    }
    // Not found
    return -1;
  }

  private boolean isAllowed(boolean isInner, int object, int index, int x, int y, int direction) {
    return
        // Pixel must be within image
        ((isInner || isWithinXy(x, y, direction))
        // Pixel must have same object value
        && object == ip.get(index + offset[direction])
        // Support for 4 or 8-connected objects.
        // 4-connected is supported by checking an 8-connected move is directly
        // followed by an allowed 4-connected direction.
        && (eightConnected
          // Even directions are 4-connected move
          || (direction & 1) == 0
          // An eight-connected move. Check the next move.
          // It can be assumed to be within the bounds.
          || object == ip.get(index + offset[(direction + 1) % 8])));
  }

  /**
   * Follow the outline of the object from the start index, tracing the outside of the object until
   * the start index is reached.
   *
   * @param index0 the start index
   * @param ip the ip
   * @param outline the outline
   * @param dir the initial direction
   * @return the finish direction
   */
  private int followOutline(int index0, Outline outline, int dir) {
    final int object = ip.get(index0);
    int index = index0;
    int direction = dir;
    for (;;) {
      outline.add(direction);
      index += offset[direction];
      // End if back to the start point
      if (index == index0) {
        break;
      }
      direction = findNext(object, index, direction);
    }
    return direction;
  }

  /**
   * Find the next direction given the current.
   *
   * @param object the object
   * @param index the index
   * @param direction the current direction
   * @return the next direction
   */
  private int findNext(int object, int index, int direction) {
    final int x = index % maxx;
    final int y = index / maxx;
    final boolean isInner = isInner(x, y);

    // The current direction was found using a clock-wise sweep over the directions.
    // So we know that from the current index X (direction + 4) % 8 is valid (this is the reverse
    // step) and (direction + 5) % 8 is empty for 8-connected and (direction + 6) % 8 is empty for
    // 4 connected:
    //
    // @formatter:off
    // +---+---+---+    +---+---+---+
    // | 7 | 8 | 0 |    | 7 |dir| 1 |
    // +---+---+---+    +---+---+---+
    // |   | X | 1 |    |   | X | 2 |
    // +---+---+---+    +---+---+---+
    // | X | 3 | 2 |    | 5 | X | 3 |
    // +---+---+---+    +---+---+---+
    // @formatter:on
    //
    // Truncate the diagonal search to 4-connected and then start at
    // (direction + 7). This is equivalent to (direction - 1).
    final int searchDirection = ((direction & EVEN_MASK) + 7) % 8;
    for (int i = 0; i < 7; i++) {
      final int d = (searchDirection + i) % 8;
      if (isAllowed(isInner, object, index, x, y, d)) {
        return d;
      }
    }

    // Unreachable!
    // It will always be possible to move back the direction we came
    throw new IllegalStateException();
  }

  /**
   * Update the processed directions using the finishing direction used to move back to the start.
   *
   * @param processedDirections the processed directions
   * @param dir the finishing direction
   */
  private static void updateProcessedDirections(BitSet processedDirections, int dir) {
    // Starting from the opposite direction, sweep anti-clockwise and mark all directions
    // as processed back to the initial direction:
    // @formatter:off
    // Finish           Opposite         Processed
    // +---+---+---+    +---+---+---+    +---+---+---+
    // |   |   |   |    |   |   |   |    | x | x | x |
    // +---+---+---+    +---+---+---+    +---+---+---+
    // |   |dir|   |    |   | X |   |    | x | X | x |
    // +---+---+---+    +---+---+---+    +---+---+---+
    // |   |   | X |    |   |   | d |    |   |   | x |
    // +---+---+---+    +---+---+---+    +---+---+---+
    // @formatter:on
    for (int d = (dir + 4) % 8; d >= 0; d--) {
      processedDirections.set(d, true);
    }
  }
}
