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
package uk.ac.sussex.gdsc.colocalisation.cda;

import ij.process.ImageProcessor;

import java.util.Arrays;

/**
 * A class to shift two images simultaneously within a mask.
 */
public class TwinImageShifter {
  private final ImageProcessor image1;
  private final ImageProcessor image2;
  private final ImageProcessor mask;
  private ImageProcessor resultIP;
  private ImageProcessor result2;
  private int xShift = 0;
  private int yShift = 0;
  private int w;
  private int h;
  private int[][] horizontalROI;
  private int[][] verticalROI;

  // Used as working space
  private int[] t1 = null, t2 = null, t3 = null, t4 = null;

  /**
   * Instantiates a new twin image shifter.
   *
   * @param image1 the first input image
   * @param image2 the second input image
   * @param mask the mask image
   */
  public TwinImageShifter(ImageProcessor image1, ImageProcessor image2, ImageProcessor mask) {
    this.image1 = image1;
    this.image2 = image2;
    this.mask = mask;
    setup();
  }

  private void setup() {
    this.w = this.image1.getWidth();
    this.h = this.image1.getHeight();

    if (image2.getWidth() != w || image2.getHeight() != h) {
      throw new RuntimeException("The first and second channel image dimensions do not match");
    }
    if (mask != null) {
      if (mask.getWidth() != w || mask.getHeight() != h) {
        throw new RuntimeException("The channel image and confined image dimensions do not match");
      }
    }

    buildHorizontalROIArrays();
    buildVerticalROIArrays();

    createTempArrays();
  }

  private void buildHorizontalROIArrays() {
    this.horizontalROI = new int[h][];

    final int[] sites = new int[w];

    if (mask != null) {
      for (int y = 0; y < h; ++y) {
        int size = 0;
        for (int x = 0, index = y * mask.getWidth(); x < w; x++, index++) {
          if (mask.get(index) != 0) {
            sites[size++] = x;
          }
        }

        // This is the array for height position 'y'
        horizontalROI[y] = Arrays.copyOf(sites, size);
      }
    } else {
      for (int x = 0; x < w; x++) {
        sites[x] = x;
      }

      for (int y = 0; y < h; ++y) {
        // This is the array for height position 'y'
        horizontalROI[y] = sites;
      }
    }
  }

  private void buildVerticalROIArrays() {
    this.verticalROI = new int[w][];

    final int[] sites = new int[h];

    if (mask != null) {
      for (int x = 0; x < w; ++x) {
        int size = 0;
        for (int y = 0; y < h; ++y) {
          if (mask.get(x, y) != 0) {
            sites[size++] = y;
          }
        }

        // This is the array for width position 'x'
        verticalROI[x] = Arrays.copyOf(sites, size);
      }
    } else {
      for (int y = 0; y < h; ++y) {
        sites[y] = y;
      }

      for (int x = 0; x < w; ++x) {
        // This is the array for width position 'x'
        verticalROI[x] = sites;
      }
    }
  }

  private void createTempArrays() {
    int max = 0;
    for (final int[] list : horizontalROI) {
      if (max < list.length) {
        max = list.length;
      }
    }
    for (final int[] list : verticalROI) {
      if (max < list.length) {
        max = list.length;
      }
    }
    t1 = new int[max];
    t2 = new int[max];
    t3 = new int[max];
    t4 = new int[max];
  }

  /**
   * Run.
   */
  public void run() {
    // Duplicate the image to ensure the same return type.
    // This will ensure get and put pixel sets the correct value.
    this.resultIP = image1.duplicate();
    this.result2 = image2.duplicate();

    // shift and wrap the pixel values in the X-direction
    // (stores the result in the resultImage)
    shiftX();

    // Shift and wrap the pixel values in the Y-direction.
    // (stores the result in the resultImage)
    shiftY();
  }

  private void shiftX() {
    if (xShift == 0) {
      return;
    }

    for (int y = 0; y < h; ++y) {
      // Find all the locations in this strip that need to be shifted
      final int[] sites = horizontalROI[y];

      if (sites.length == 0) {
        continue;
      }

      // Extract the values
      final int index = y * resultIP.getWidth();
      for (int i = 0; i < sites.length; i++) {
        t1[i] = resultIP.get(index + sites[i]);
        t2[i] = result2.get(index + sites[i]);
      }

      // Perform a shift
      rotateArrays(t1, t2, t3, t4, xShift, sites.length);

      // Write back the values
      for (int i = 0; i < sites.length; i++) {
        resultIP.set(index + sites[i], t3[i]);
        result2.set(index + sites[i], t4[i]);
      }
    }
  }

  private void shiftY() {
    if (yShift == 0) {
      return;
    }

    for (int x = 0; x < w; ++x) {
      // Find all the locations in this strip that need to be shifted
      final int[] sites = verticalROI[x];

      if (sites.length == 0) {
        continue;
      }

      // Extract the values
      for (int i = 0; i < sites.length; i++) {
        final int index = sites[i] * resultIP.getWidth() + x;
        t1[i] = resultIP.get(index);
        t2[i] = result2.get(index);
      }

      // Perform a shift
      rotateArrays(t1, t2, t3, t4, yShift, sites.length);

      // Write back the values
      for (int i = 0; i < sites.length; i++) {
        final int index = sites[i] * resultIP.getWidth() + x;
        resultIP.set(index, t3[i]);
        result2.set(index, t4[i]);
      }
    }
  }

  /**
   * Sets the shift X.
   *
   * @param x the new shift X
   */
  public void setShiftX(int x) {
    this.xShift = x;
  }

  /**
   * Sets the shift Y.
   *
   * @param y the new shift Y
   */
  public void setShiftY(int y) {
    this.yShift = y;
  }

  /**
   * Sets the shift.
   *
   * @param x the x
   * @param y the y
   */
  public void setShift(int x, int y) {
    this.xShift = x;
    this.yShift = y;
  }

  /**
   * Gets the result image.
   *
   * @return the result image
   */
  public ImageProcessor getResultImage() {
    return this.resultIP;
  }

  /**
   * Gets the result image 2.
   *
   * @return the result image 2
   */
  public ImageProcessor getResultImage2() {
    return this.result2;
  }

  private static void rotateArrays(int[] array1, int[] array2, int[] array3, int[] array4,
      int shift, int size) {
    while (shift < 0) {
      shift += size;
    }

    for (int i = 0; i < size; ++i) {
      array3[(i + shift) % size] = array1[i];
      array4[(i + shift) % size] = array2[i];
    }
  }
}
