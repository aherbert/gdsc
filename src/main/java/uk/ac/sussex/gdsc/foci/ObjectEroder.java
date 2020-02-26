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

package uk.ac.sussex.gdsc.foci;

import ij.process.ImageProcessor;
import uk.ac.sussex.gdsc.core.data.VisibleForTesting;

/**
 * Erode objects defined by non-zero pixels.
 */
public class ObjectEroder {
  /** The image. */
  private final ImageProcessor ip;
  /** A buffer for the output image. */
  private final ImageProcessor buffer;
  /** Flag indicating pixels are extended outside the image. */
  private final boolean extendOutside;

  /**
   * Create a new instance.
   *
   * @param ip the image (modified in place)
   * @param extendOutside Flag indicating edge pixels are extended outside the image
   */
  public ObjectEroder(ImageProcessor ip, boolean extendOutside) {
    this.ip = ip;
    this.buffer = ip.duplicate();
    this.extendOutside = extendOutside;
  }

  /**
   * Perform several iterations of an object erosion. Any non-zero pixel touching a pixel of a
   * different value will be set to zero.
   *
   * @param iterations the iterations
   */
  public void erode(int iterations) {
    for (int i = 0; i < iterations; i++) {
      erode();
    }
  }

  /**
   * Perform an object erosion. Any non-zero pixel touching a pixel of a different value will be set
   * to zero.
   */
  public void erode() {
    final int width = ip.getWidth();
    final int height = ip.getHeight();

    // Handle edge case of a single dimension array
    // XXX this may be redundant
    if (width <= 1 || height <= 1) {
      erode1d();
      return;
    }

    // Process internal pixels with a 3x3 neighbourhood
    // @formatter:off
    // +---+---+---+
    // | 1 | 2 | 3 |
    // +---+---+---+
    // | 4 | 5 | 6 |
    // +---+---+---+
    // | 7 | 8 | 9 |
    // +---+---+---+
    // @formatter:on
    final int lastX = width - 1;
    final int lastY = height - 1;
    for (int y = 1; y < lastY; y++) {
      int index = 1 + y * width;
      int p2 = ip.get(index - width - 1);
      int p3 = ip.get(index - width);
      int p5 = ip.get(index - 1);
      int p6 = ip.get(index);
      int p8 = ip.get(index + width - 1);
      int p9 = ip.get(index + width);

      for (int x = 1; x < lastX; x++, index++) {
        // Shift neighbourhood
        final int p1 = p2;
        p2 = p3;
        p3 = ip.get(index - width + 1);
        final int p4 = p5;
        p5 = p6;
        p6 = ip.get(index + 1);
        final int p7 = p8;
        p8 = p9;
        p9 = ip.get(index + width + 1);

        if (p5 == 0) {
          continue;
        }

        if (isDifferent(p1, p2, p3, p4, p5, p6, p7, p8, p9)) {
          buffer.set(index, 0);
        }
      }
    }

    // Filter the edges
    if (extendOutside) {
      erode(width, height, height, 0, 0, 0, 1);
      erode(width, height, width, 0, 0, 1, 0);
      erode(width, height, height, lastX, 0, 0, 1);
      erode(width, height, width, 0, lastY, 1, 0);
    } else {
      erase(width, 0, 1);
      erase(height, 0, width);
      erase(width, lastY * width, 1);
      erase(height, lastX, width);
    }

    updatePixels();
  }

  /**
   * Erode the image from the given (x,y) position using the given increments for the given length.
   *
   * @param width the width
   * @param height the height
   * @param length the length
   * @param x the x position
   * @param y the y position
   * @param xinc the x increment
   * @param yinc the y increment
   */
  private void erode(int width, int height, int length, int x, int y, int xinc, int yinc) {
    for (int i = 0; i < length; i++) {
      final int p5 = getEdgePixel(width, height, x, y);
      if (p5 != 0) {
        final int p1 = getEdgePixel(width, height, x - 1, y - 1);
        final int p2 = getEdgePixel(width, height, x, y - 1);
        final int p3 = getEdgePixel(width, height, x + 1, y - 1);
        final int p4 = getEdgePixel(width, height, x - 1, y);
        final int p6 = getEdgePixel(width, height, x + 1, y);
        final int p7 = getEdgePixel(width, height, x - 1, y + 1);
        final int p8 = getEdgePixel(width, height, x, y + 1);
        final int p9 = getEdgePixel(width, height, x + 1, y + 1);

        if (isDifferent(p1, p2, p3, p4, p5, p6, p7, p8, p9)) {
          buffer.set(x + y * width, 0);
        }
      }
      x += xinc;
      y += yinc;
    }
  }

  /**
   * Gets the edge pixel with bounds checking.
   *
   * @param width the width
   * @param height the height
   * @param x the x position
   * @param y the y position
   * @return the edge pixel
   */
  private int getEdgePixel(int width, int height, int x, int y) {
    if (x <= 0) {
      x = 0;
    } else if (x >= width) {
      x = width - 1;
    }
    if (y <= 0) {
      y = 0;
    } else if (y >= height) {
      y = height - 1;
    }
    return ip.get(x + y * width);
  }

  /**
   * Checks if any value is different from the value at position 5.
   *
   * @param p1 value 1
   * @param p2 value 2
   * @param p3 value 3
   * @param p4 value 4
   * @param p5 value 5
   * @param p6 value 6
   * @param p7 value 7
   * @param p8 value 8
   * @param p9 value 9
   * @return true, if is different
   */
  @VisibleForTesting
  static boolean isDifferent(int p1, int p2, int p3, int p4, int p5, int p6, int p7, int p8,
      int p9) {
    return p1 != p5 || p2 != p5 || p3 != p5 || p4 != p5 || p6 != p5 || p7 != p5 || p8 != p5
        || p9 != p5;
  }

  /**
   * Erase from the start index using the given increment for the given length.
   *
   * @param index the index
   * @param increment the increment
   * @param length the length
   */
  private void erase(int length, int index, int increment) {
    for (int i = 0; i < length; i++) {
      buffer.set(index + i * increment, 0);
    }
  }

  /**
   * Perform a 1-dimension object erosion. Any non-zero pixel touching a pixel of a different value
   * will be set to zero.
   *
   * <p>Pixels outside the image are ignored.
   */
  private void erode1d() {
    // Handle edge case of a no pixel array
    if (ip.getPixelCount() == 0) {
      return;
    }

    if (extendOutside) {
      // Rolling window of 3
      // @formatter:off
      // +---+---+---+
      // | 1 | 2 | 3 |
      // +---+---+---+
      // @formatter:on
      int p3 = ip.get(0);
      int p2 = p3;
      // Process all but the last index
      final int lastIndex = Math.max(ip.getWidth(), ip.getHeight()) - 1;
      for (int i = 0; i < lastIndex; i++) {
        // Shift neighbourhood
        final int p1 = p2;
        p2 = p3;
        p3 = ip.get(i + 1);
        if (p2 == 0) {
          continue;
        }
        if (p1 != p2 || p3 != p2) {
          buffer.set(i, 0);
        }
      }

      // Filter end using a window of 2
      if (p3 != p2) {
        buffer.set(lastIndex, 0);
      }
    } else {
      // With nothing outside the entire pixel array is eroded.
      erase(ip.getPixelCount(), 0, 1);
    }

    updatePixels();
  }

  /**
   * Update the pixels from the buffer to the image.
   */
  private void updatePixels() {
    // This copies rather than directly swapping the pixel arrays as other objects may hold
    // a reference to the processor array.
    System.arraycopy(buffer.getPixels(), 0, ip.getPixels(), 0, ip.getPixelCount());
  }
}
