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
  private ImageProcessor resultIp1;
  private ImageProcessor resultIp2;
  private int shiftX;
  private int shiftY;
  private int width;
  private int height;
  private int[][] horizontalRoi;
  private int[][] verticalRoi;

  // Used as working space
  private int[] t1;
  private int[] t2;
  private int[] t3;
  private int[] t4;

  /**
   * Instantiates a new twin image shifter.
   *
   * @param image1 the first input image
   * @param image2 the second input image
   * @param mask the mask image
   * @throws IllegalArgumentException If the dimensions do not match
   */
  public TwinImageShifter(ImageProcessor image1, ImageProcessor image2, ImageProcessor mask) {
    this.image1 = image1;
    this.image2 = image2;
    this.mask = mask;
    setup();
  }

  private void setup() {
    this.width = this.image1.getWidth();
    this.height = this.image1.getHeight();

    if (image2.getWidth() != width || image2.getHeight() != height) {
      throw new IllegalArgumentException(
          "The first and second channel image dimensions do not match");
    }
    if (mask != null && (mask.getWidth() != width || mask.getHeight() != height)) {
      throw new IllegalArgumentException(
          "The channel image and confined image dimensions do not match");
    }

    buildHorizontalRoiArrays();
    buildVerticalRoiArrays();

    createTempArrays();
  }

  private void buildHorizontalRoiArrays() {
    this.horizontalRoi = new int[height][];

    final int[] sites = new int[width];

    if (mask != null) {
      for (int y = 0; y < height; ++y) {
        int size = 0;
        for (int x = 0, index = y * mask.getWidth(); x < width; x++, index++) {
          if (mask.get(index) != 0) {
            sites[size++] = x;
          }
        }

        // This is the array for height position 'y'
        horizontalRoi[y] = Arrays.copyOf(sites, size);
      }
    } else {
      for (int x = 0; x < width; x++) {
        sites[x] = x;
      }

      for (int y = 0; y < height; ++y) {
        // This is the array for height position 'y'
        horizontalRoi[y] = sites;
      }
    }
  }

  private void buildVerticalRoiArrays() {
    this.verticalRoi = new int[width][];

    final int[] sites = new int[height];

    if (mask != null) {
      for (int x = 0; x < width; ++x) {
        int size = 0;
        for (int y = 0; y < height; ++y) {
          if (mask.get(x, y) != 0) {
            sites[size++] = y;
          }
        }

        // This is the array for width position 'x'
        verticalRoi[x] = Arrays.copyOf(sites, size);
      }
    } else {
      for (int y = 0; y < height; ++y) {
        sites[y] = y;
      }

      for (int x = 0; x < width; ++x) {
        // This is the array for width position 'x'
        verticalRoi[x] = sites;
      }
    }
  }

  private void createTempArrays() {
    int max = 0;
    for (final int[] list : horizontalRoi) {
      if (max < list.length) {
        max = list.length;
      }
    }
    for (final int[] list : verticalRoi) {
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
    this.resultIp1 = image1.duplicate();
    this.resultIp2 = image2.duplicate();

    // shift and wrap the pixel values in the X-direction
    // (stores the result in the resultImage)
    shiftX();

    // Shift and wrap the pixel values in the Y-direction.
    // (stores the result in the resultImage)
    shiftY();
  }

  private void shiftX() {
    if (shiftX == 0) {
      return;
    }

    for (int y = 0; y < height; ++y) {
      // Find all the locations in this strip that need to be shifted
      final int[] sites = horizontalRoi[y];

      if (sites.length == 0) {
        continue;
      }

      // Extract the values
      final int index = y * resultIp1.getWidth();
      for (int i = 0; i < sites.length; i++) {
        t1[i] = resultIp1.get(index + sites[i]);
        t2[i] = resultIp2.get(index + sites[i]);
      }

      // Perform a shift
      rotateArrays(t1, t2, t3, t4, shiftX, sites.length);

      // Write back the values
      for (int i = 0; i < sites.length; i++) {
        resultIp1.set(index + sites[i], t3[i]);
        resultIp2.set(index + sites[i], t4[i]);
      }
    }
  }

  private void shiftY() {
    if (shiftY == 0) {
      return;
    }

    for (int x = 0; x < width; ++x) {
      // Find all the locations in this strip that need to be shifted
      final int[] sites = verticalRoi[x];

      if (sites.length == 0) {
        continue;
      }

      // Extract the values
      for (int i = 0; i < sites.length; i++) {
        final int index = sites[i] * resultIp1.getWidth() + x;
        t1[i] = resultIp1.get(index);
        t2[i] = resultIp2.get(index);
      }

      // Perform a shift
      rotateArrays(t1, t2, t3, t4, shiftY, sites.length);

      // Write back the values
      for (int i = 0; i < sites.length; i++) {
        final int index = sites[i] * resultIp1.getWidth() + x;
        resultIp1.set(index, t3[i]);
        resultIp2.set(index, t4[i]);
      }
    }
  }

  /**
   * Sets the shift X.
   *
   * @param x the new shift X
   */
  public void setShiftX(int x) {
    this.shiftX = x;
  }

  /**
   * Sets the shift Y.
   *
   * @param y the new shift Y
   */
  public void setShiftY(int y) {
    this.shiftY = y;
  }

  /**
   * Sets the shift.
   *
   * @param x the x
   * @param y the y
   */
  public void setShift(int x, int y) {
    this.shiftX = x;
    this.shiftY = y;
  }

  /**
   * Gets the result image.
   *
   * @return the result image
   */
  public ImageProcessor getResultImage() {
    return this.resultIp1;
  }

  /**
   * Gets the result image 2.
   *
   * @return the result image 2
   */
  public ImageProcessor getResultImage2() {
    return this.resultIp2;
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
