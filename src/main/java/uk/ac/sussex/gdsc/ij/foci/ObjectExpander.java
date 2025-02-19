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

import ij.process.ImageProcessor;
import org.apache.commons.lang3.ArrayUtils;
import uk.ac.sussex.gdsc.core.data.VisibleForTesting;

/**
 * Expand objects defined by non-zero pixels.
 *
 * <p>Note that this is not the same as a greyscale dilation which will typically use the maximum
 * value from a structuring element on each pixel. This routine only affects zero-valued pixels. The
 * default operation counts the number of contacts with non-zero neighbour pixels in a 3x3 region
 * and assigns the pixel to the value from the most contacts. The effect is expansion of object
 * (non-zero) pixels into zero valued space.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Dilation_(morphology)#Grayscale_dilation">Greyscale
 *      dilation</a>
 */
public class ObjectExpander {
  /** The image. */
  private final ImageProcessor ip;
  /** A buffer for the output image. */
  private final ImageProcessor buffer;
  /** The selecter. */
  private final ValueSelecter selecter = new FrequencySelecter();

  /**
   * Define the method for selecting the value for the central pixel of a 3x3 region.
   */
  @VisibleForTesting
  interface ValueSelecter {
    /**
     * Select the value from the 3x3 region to assign to the centre (position 5).
     *
     * @param p1 value at position 1
     * @param p2 value at position 2
     * @param p3 value at position 3
     * @param p4 value at position 4
     * @param p5 value at position 5
     * @param p6 value at position 6
     * @param p7 value at position 7
     * @param p8 value at position 8
     * @param p9 value at position 9
     * @return the value
     */
    int select(int p1, int p2, int p3, int p4, int p5, int p6, int p7, int p8, int p9);
  }

  /**
   * Implement the default selection strategy. Uses the highest number of contacts with the same
   * object, and in the event of a tie, the highest object value.
   */
  @VisibleForTesting
  static final class FrequencySelecter implements ValueSelecter {
    /** Working buffer for the non-zero data. */
    private final int[] values = new int[8];

    @Override
    public int select(int p1, int p2, int p3, int p4, int p5, int p6, int p7, int p8, int p9) {
      // Note: Ignore p5 and zeros.
      // The fast path for touching no objects or a single object is handled first.

      // Find min.
      final int min = minNonZero(p1, p2, p3, p4, p6, p7, p8, p9);
      if (min == 0) {
        // No non-zero neighbours
        return 0;
      }
      int max = maxNonZero(p1, p2, p3, p4, p6, p7, p8, p9);

      // If the same non-zero value then assign that value.
      if (min == max) {
        return max;
      }

      // If not then count the frequency of each.
      // If same max count then use the max value.
      values[0] = p1;
      values[1] = p2;
      values[2] = p3;
      values[3] = p4;
      values[4] = p6;
      values[5] = p7;
      values[6] = p8;
      values[7] = p9;
      sort8(values);

      // Skip zeros
      final int i1 = ArrayUtils.indexOf(values, 0);
      int size = values.length;
      if (i1 != -1) {
        final int i2 = ArrayUtils.lastIndexOf(values, 0) + 1;
        final int length = i2 - i1;
        System.arraycopy(values, i2, values, i1, size - i2);
        size -= length;
      }

      // Initialise
      int count = 1;
      int maxCount = 1;
      max = values[0];
      int current = values[0];

      // Scan
      for (int i = 1; i < size; i++) {
        int next = values[i];
        if (current == next) {
          count++;
        } else {
          if (maxCount <= count) {
            maxCount = count;
            max = current;
          }
          count = 1;
          current = next;
        }
      }

      return maxCount <= count ? current : max;
    }


    /**
     * Sorts the array of length 8.
     *
     * @param data Data array.
     */
    static void sort8(int[] data) {
      // Uses an optimal sorting network from Knuth's Art of Computer Programming.
      // 19 comparisons.
      // Order pairs:
      // [(0,2),(1,3),(4,6),(5,7)]
      // [(0,4),(1,5),(2,6),(3,7)]
      // [(0,1),(2,3),(4,5),(6,7)]
      // [(2,4),(3,5)]
      // [(1,4),(3,6)]
      // [(1,2),(3,4),(5,6)]
      if (data[7] < data[5]) {
        final int u = data[7];
        data[7] = data[5];
        data[5] = u;
      }
      if (data[6] < data[4]) {
        final int v = data[6];
        data[6] = data[4];
        data[4] = v;
      }
      if (data[3] < data[1]) {
        final int w = data[3];
        data[3] = data[1];
        data[1] = w;
      }
      if (data[2] < data[0]) {
        final int x = data[2];
        data[2] = data[0];
        data[0] = x;
      }

      if (data[7] < data[3]) {
        final int u = data[7];
        data[7] = data[3];
        data[3] = u;
      }
      if (data[6] < data[2]) {
        final int v = data[6];
        data[6] = data[2];
        data[2] = v;
      }
      if (data[5] < data[1]) {
        final int w = data[5];
        data[5] = data[1];
        data[1] = w;
      }
      if (data[4] < data[0]) {
        final int x = data[4];
        data[4] = data[0];
        data[0] = x;
      }

      if (data[7] < data[6]) {
        final int u = data[7];
        data[7] = data[6];
        data[6] = u;
      }
      if (data[5] < data[4]) {
        final int v = data[5];
        data[5] = data[4];
        data[4] = v;
      }
      if (data[3] < data[2]) {
        final int w = data[3];
        data[3] = data[2];
        data[2] = w;
      }
      if (data[1] < data[0]) {
        final int x = data[1];
        data[1] = data[0];
        data[0] = x;
      }

      if (data[5] < data[3]) {
        final int u = data[5];
        data[5] = data[3];
        data[3] = u;
      }
      if (data[4] < data[2]) {
        final int v = data[4];
        data[4] = data[2];
        data[2] = v;
      }

      if (data[6] < data[3]) {
        final int u = data[6];
        data[6] = data[3];
        data[3] = u;
      }
      if (data[4] < data[1]) {
        final int v = data[4];
        data[4] = data[1];
        data[1] = v;
      }

      if (data[6] < data[5]) {
        final int u = data[6];
        data[6] = data[5];
        data[5] = u;
      }
      if (data[4] < data[3]) {
        final int v = data[4];
        data[4] = data[3];
        data[3] = v;
      }
      if (data[2] < data[1]) {
        final int w = data[2];
        data[2] = data[1];
        data[1] = w;
      }
    }
  }

  /**
   * Create a new instance.
   *
   * @param ip the image (modified in place)
   */
  public ObjectExpander(ImageProcessor ip) {
    this.ip = ip;
    this.buffer = ip.duplicate();
  }

  /**
   * Perform several iterations of an object expansion. Any zero pixel touching a non-zero pixel
   * will be set to a non-zero value.
   *
   * @param iterations the iterations
   * @see #expand()
   */
  public void expand(int iterations) {
    for (int i = 0; i < iterations; i++) {
      expand();
    }
  }

  /**
   * Perform an object expansion. Any zero pixel touching a non-zero pixel will be set to a non-zero
   * value.
   *
   * <p>The value chosen in the case of touching two different valued non-zero pixels requires a
   * decision strategy. The default strategy uses the highest number of contacts with the same
   * object, and in the event of a tie, the highest object value.
   */
  public void expand() {
    final int width = ip.getWidth();
    final int height = ip.getHeight();

    // Handle edge case of a single dimension array
    if (width <= 1 || height <= 1) {
      expand1d();
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
          buffer.set(index, selecter.select(p1, p2, p3, p4, p5, p6, p7, p8, p9));
        }
      }
    }

    // Filter the edges
    expand(width, height, height, 0, 0, 0, 1);
    expand(width, height, width, 0, 0, 1, 0);
    expand(width, height, height, lastX, 0, 0, 1);
    expand(width, height, width, 0, lastY, 1, 0);

    updatePixels();
  }

  /**
   * Expand the image from the given (x,y) position using the given increments for the given length.
   *
   * @param width the width
   * @param height the height
   * @param length the length
   * @param x the x position
   * @param y the y position
   * @param xinc the x increment
   * @param yinc the y increment
   */
  private void expand(int width, int height, int length, int x, int y, int xinc, int yinc) {
    for (int i = 0; i < length; i++) {
      final int p5 = getEdgePixel(width, height, x, y);
      if (p5 == 0) {
        final int p1 = getEdgePixel(width, height, x - 1, y - 1);
        final int p2 = getEdgePixel(width, height, x, y - 1);
        final int p3 = getEdgePixel(width, height, x + 1, y - 1);
        final int p4 = getEdgePixel(width, height, x - 1, y);
        final int p6 = getEdgePixel(width, height, x + 1, y);
        final int p7 = getEdgePixel(width, height, x - 1, y + 1);
        final int p8 = getEdgePixel(width, height, x, y + 1);
        final int p9 = getEdgePixel(width, height, x + 1, y + 1);

        buffer.set(x + y * width, selecter.select(p1, p2, p3, p4, p5, p6, p7, p8, p9));
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
   * Perform a 1-dimension object dilation. Any zero pixel touching a non-zero pixel will be set to
   * a non-zero value.
   *
   * <p>The value chosen in the case of touching two different valued non-zero pixels requires a
   * decision strategy. The default strategy uses the highest number of contacts with the same
   * object, and in the event of a tie, the highest object value.
   */
  private void expand1d() {
    // Handle edge case of a no pixel array
    if (ip.getPixelCount() == 0) {
      return;
    }

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
        // Use max to handle ties between two different objects
        buffer.set(i, maxNonZero(p1, p3));
      }
    }

    // Filter end using a window of 2
    if (p3 == 0) {
      buffer.set(lastIndex, p2);
    }

    updatePixels();
  }

  /**
   * Return the highest non-zero value, or zero.
   *
   * @param v1 value 1
   * @param v2 value 2
   * @return the value
   */
  private static int maxNonZero(int v1, int v2) {
    if (v1 == 0) {
      return v2;
    }
    return v2 == 0 ? v1 : Math.max(v1, v2);
  }

  /**
   * Return the highest non-zero value, or zero.
   *
   * @param p1 value 1
   * @param p2 value 2
   * @param p3 value 3
   * @param p4 value 4
   * @param p5 value 5
   * @param p6 value 6
   * @param p7 value 7
   * @param p8 value 8
   * @return the maximum
   */
  private static int maxNonZero(int p1, int p2, int p3, int p4, int p5, int p6, int p7, int p8) {
    final int max1234 = maxNonZero(maxNonZero(p1, p2), maxNonZero(p3, p4));
    final int max5678 = maxNonZero(maxNonZero(p5, p6), maxNonZero(p7, p8));
    return maxNonZero(max1234, max5678);
  }

  /**
   * Return the lowest non-zero value, or zero.
   *
   * @param v1 value 1
   * @param v2 value 2
   * @return the value
   */
  private static int minNonZero(int v1, int v2) {
    if (v1 == 0) {
      return v2;
    }
    return v2 == 0 ? v1 : Math.min(v1, v2);
  }

  /**
   * Find the minimum.
   *
   * @param p1 value 1
   * @param p2 value 2
   * @param p3 value 3
   * @param p4 value 4
   * @param p5 value 5
   * @param p6 value 6
   * @param p7 value 7
   * @param p8 value 8
   * @return the minimum
   */
  private static int minNonZero(int p1, int p2, int p3, int p4, int p5, int p6, int p7, int p8) {
    final int min1234 = minNonZero(minNonZero(p1, p2), minNonZero(p3, p4));
    final int min5678 = minNonZero(minNonZero(p5, p6), minNonZero(p7, p8));
    return minNonZero(min1234, min5678);
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
