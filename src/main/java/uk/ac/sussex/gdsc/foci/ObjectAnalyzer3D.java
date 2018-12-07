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

import java.util.Arrays;

/**
 * Find objects defined by contiguous pixels of the same value.
 */
public class ObjectAnalyzer3D {
  //@formatter:off
                                              //4N                //8N
  private static final int[] DIR_X_OFFSET2 = {  0, 1, 0,-1,        1, 1,-1,-1 };
  private static final int[] DIR_Y_OFFSET2 = { -1, 0, 1, 0,       -1, 1, 1,-1 };
                                              //4N                //8N
  private static final int[] DIR_X_OFFSET3 = { 0, 1, 0,-1, 0, 0,   1, 1,-1,-1, 0, 1, 1, 1, 0,-1,-1,-1, 0, 1, 1, 1, 0,-1,-1,-1 };
  private static final int[] DIR_Y_OFFSET3 = {-1, 0, 1, 0, 0, 0,  -1, 1, 1,-1,-1,-1, 0, 1, 1, 1, 0,-1,-1,-1, 0, 1, 1, 1, 0,-1 };
  private static final int[] DIR_Z_OFFSET3 = { 0, 0, 0, 0,-1, 1,   0, 0, 0, 0,-1,-1,-1,-1,-1,-1,-1,-1, 1, 1, 1, 1, 1, 1, 1, 1 };
  //@formatter:on

  private final int[] maskImage;
  private final int maxx;
  private final int maxy;
  private final int maxz;
  private final int maxxByMaxy;
  private final int xlimit;
  private final int ylimit;
  private final int zlimit;

  private boolean eightConnected;
  private int[] objectMask;
  private int maxObject;
  private int minObjectSize = 0;

  private int[] offset;

  /**
   * Instantiates a new object analyzer 3D.
   *
   * @param image the image
   * @param maxx the maxx
   * @param maxy the maxy
   * @param maxz the maxz
   */
  public ObjectAnalyzer3D(int[] image, int maxx, int maxy, int maxz) {
    this(image, maxx, maxy, maxz, false);
  }

  /**
   * Instantiates a new object analyzer 3D.
   *
   * @param image the image
   * @param maxx the maxx
   * @param maxy the maxy
   * @param maxz the maxz
   * @param eightConnected the eight connected flag
   */
  public ObjectAnalyzer3D(int[] image, int maxx, int maxy, int maxz, boolean eightConnected) {
    this.maskImage = image;
    this.maxx = maxx;
    this.maxy = maxy;
    this.maxz = maxz;
    maxxByMaxy = maxx * maxy;
    xlimit = maxx - 1;
    ylimit = maxy - 1;
    zlimit = maxz - 1;
    this.eightConnected = eightConnected;
  }

  /**
   * Gets the object mask.
   *
   * @return A pixel array containing the object number for each pixel in the input image.
   */
  public int[] getObjectMask() {
    analyseObjects();
    return objectMask;
  }

  /**
   * Gets the max object number.
   *
   * @return The maximum object number.
   */
  public int getMaxObject() {
    analyseObjects();
    return maxObject;
  }

  private void analyseObjects() {
    if (objectMask != null) {
      return;
    }

    // Perform a search for objects.
    // Expand any non-zero pixel value into all 8-connected pixels of the same value.
    objectMask = new int[maskImage.length];
    maxObject = 0;

    final int[][] ppList = new int[1][];
    ppList[0] = new int[100];
    initialise();
    final boolean is2D = maxz == 1;

    int[] sizes = new int[100];

    for (int i = 0; i < maskImage.length; i++) {
      // Look for non-zero values that are not already in an object
      if (maskImage[i] != 0 && objectMask[i] == 0) {
        maxObject++;
        int size;
        if (is2D) {
          size = expandObjectXy(maskImage, objectMask, i, maxObject, ppList);
        } else {
          size = expandObjectXyz(maskImage, objectMask, i, maxObject, ppList);
        }
        if (sizes.length == maxObject) {
          sizes = Arrays.copyOf(sizes, (int) (maxObject * 1.5));
        }
        sizes[maxObject] = size;
      }
    }

    // Remove objects that are too small
    if (minObjectSize > 0) {
      final int[] map = new int[maxObject + 1];
      maxObject = 0;
      for (int i = 1; i < map.length; i++) {
        if (sizes[i] >= minObjectSize) {
          map[i] = ++maxObject;
        }
      }

      for (int i = 0; i < objectMask.length; i++) {
        if (objectMask[i] != 0) {
          objectMask[i] = map[objectMask[i]];
        }
      }
    }
  }

  /**
   * Searches from the specified point to find all coordinates of the same value and assigns them to
   * given maximum ID.
   */
  private int expandObjectXy(final int[] image, final int[] objectMask, final int index0,
      final int id, int[][] ppList) {
    objectMask[index0] = id; // mark first point
    int listI = 0; // index of current search element in the list
    int listLen = 1; // number of elements in the list
    final int neighbours = (eightConnected) ? 8 : 4;

    // we create a list of connected points and start the list at the current point
    int[] pointList = ppList[0];
    pointList[listI] = index0;

    final int v0 = image[index0];

    do {
      final int index1 = pointList[listI];
      final int x1 = index1 % maxx;
      final int y1 = index1 / maxx;

      final boolean isInnerXy = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);

      for (int d = neighbours; d-- > 0;) {
        if (isInnerXy || isWithinXy(x1, y1, d)) {
          final int index2 = index1 + offset[d];
          if (objectMask[index2] != 0) {
            // This has been done already, ignore this point
            continue;
          }

          final int v2 = image[index2];

          if (v2 == v0) {
            // Add this to the search
            pointList[listLen++] = index2;
            objectMask[index2] = id;
            if (pointList.length == listLen) {
              pointList = Arrays.copyOf(pointList, (int) (listLen * 1.5));
            }
          }
        }
      }

      listI++;

    }
    while (listI < listLen);

    ppList[0] = pointList;

    return listLen;
  }

  /**
   * Searches from the specified point to find all coordinates of the same value and assigns them to
   * given maximum ID.
   */
  private int expandObjectXyz(final int[] image, final int[] objectMask, final int index0,
      final int id, int[][] ppList) {
    objectMask[index0] = id; // mark first point
    int listI = 0; // index of current search element in the list
    int listLen = 1; // number of elements in the list
    final int neighbours = (eightConnected) ? 26 : 6;

    // we create a list of connected points and start the list at the current point
    int[] pointList = ppList[0];
    pointList[listI] = index0;

    final int v0 = image[index0];

    do {
      final int index1 = pointList[listI];

      final int z1 = index1 / (maxxByMaxy);
      final int mod = index1 % (maxxByMaxy);
      final int y1 = mod / maxx;
      final int x1 = mod % maxx;

      // It is more likely that the z stack will be out-of-bounds.
      // Adopt the xy limit lookup and process z lookup separately

      final boolean isInnerXy = (y1 != 0 && y1 != ylimit) && (x1 != 0 && x1 != xlimit);
      final boolean isInnerXyz = (zlimit == 0) ? isInnerXy : isInnerXy && (z1 != 0 && z1 != zlimit);

      for (int d = neighbours; d-- > 0;) {
        if (isInnerXyz || (isInnerXy && isWithinZ(z1, d)) || isWithinXyz(x1, y1, z1, d)) {
          final int index2 = index1 + offset[d];
          try {
            if (objectMask[index2] != 0) {
              // This has been done already, ignore this point
              continue;
            }
          } catch (final ArrayIndexOutOfBoundsException ex) {
            continue;
          }

          final int v2 = image[index2];

          if (v2 == v0) {
            // Add this to the search
            pointList[listLen++] = index2;
            objectMask[index2] = id;
            if (pointList.length == listLen) {
              pointList = Arrays.copyOf(pointList, (int) (listLen * 1.5));
            }
          }
        }
      }

      listI++;

    }
    while (listI < listLen);

    ppList[0] = pointList;

    return listLen;
  }

  /**
   * Creates the direction offset tables.
   */
  private void initialise() {
    if (maxz == 1) {
      // Create the 2D offset table
      offset = new int[DIR_X_OFFSET2.length];
      for (int d = offset.length; d-- > 0;) {
        offset[d] = maxx * DIR_Y_OFFSET2[d] + DIR_X_OFFSET2[d];
      }
    } else {
      // Create the 3D offset table
      offset = new int[DIR_X_OFFSET3.length];
      for (int d = offset.length; d-- > 0;) {
        offset[d] = maxxByMaxy * DIR_Z_OFFSET3[d] + maxx * DIR_Y_OFFSET3[d] + DIR_X_OFFSET3[d];
      }
    }
  }

  /**
   * returns whether the neighbour in a given direction is within the image. NOTE: it is assumed
   * that the pixel x,y itself is within the image! Uses class variables xlimit, ylimit: (dimensions
   * of the image)-1
   *
   * @param x x-coordinate of the pixel that has a neighbour in the given direction
   * @param y y-coordinate of the pixel that has a neighbour in the given direction
   * @param direction the direction from the pixel towards the neighbour
   * @return true if the neighbour is within the image (provided that x, y is within)
   */
  private boolean isWithinXy(int x, int y, int direction) {
    switch (direction) {
      // 4-connected directions
      case 0:
        return (y > 0);
      case 1:
        return (x < xlimit);
      case 2:
        return (y < ylimit);
      case 3:
        return (x > 0);
      // Then remaining 8-connected directions
      case 4:
        return (y > 0 && x < xlimit);
      case 5:
        return (y < ylimit && x < xlimit);
      case 6:
        return (y < ylimit && x > 0);
      case 7:
        return (y > 0 && x > 0);
      default:
        return false;
    }
  }

  /**
   * returns whether the neighbour in a given direction is within the image. NOTE: it is assumed
   * that the pixel x,y,z itself is within the image! Uses class variables xlimit, ylimit, zlimit:
   * (dimensions of the image)-1
   *
   * @param x x-coordinate of the pixel that has a neighbour in the given direction
   * @param y y-coordinate of the pixel that has a neighbour in the given direction
   * @param z z-coordinate of the pixel that has a neighbour in the given direction
   * @param direction the direction from the pixel towards the neighbour
   * @return true if the neighbour is within the image (provided that x, y, z is within)
   */
  private boolean isWithinXyz(int x, int y, int z, int direction) {
    switch (direction) {
      // 4-connected directions
      case 0:
        return (y > 0);
      case 1:
        return (x < xlimit);
      case 2:
        return (y < ylimit);
      case 3:
        return (x > 0);
      case 4:
        return (z > 0);
      case 5:
        return (z < zlimit);

      // Then remaining 8-connected directions
      case 6:
        return (y > 0 && x < xlimit);
      case 7:
        return (y < ylimit && x < xlimit);
      case 8:
        return (y < ylimit && x > 0);
      case 9:
        return (y > 0 && x > 0);
      case 10:
        return (z > 0 && y > 0);
      case 11:
        return (z > 0 && y > 0 && x < xlimit);
      case 12:
        return (z > 0 && x < xlimit);
      case 13:
        return (z > 0 && y < ylimit && x < xlimit);
      case 14:
        return (z > 0 && y < ylimit);
      case 15:
        return (z > 0 && y < ylimit && x > 0);
      case 16:
        return (z > 0 && x > 0);
      case 17:
        return (z > 0 && y > 0 && x > 0);
      case 18:
        return (z < zlimit && y > 0);
      case 19:
        return (z < zlimit && y > 0 && x < xlimit);
      case 20:
        return (z < zlimit && x < xlimit);
      case 21:
        return (z < zlimit && y < ylimit && x < xlimit);
      case 22:
        return (z < zlimit && y < ylimit);
      case 23:
        return (z < zlimit && y < ylimit && x > 0);
      case 24:
        return (z < zlimit && x > 0);
      case 25:
        return (z < zlimit && y > 0 && x > 0);
      default:
        return false;
    }
  }

  /**
   * returns whether the neighbour in a given direction is within the image. NOTE: it is assumed
   * that the pixel z itself is within the image! Uses class variables zlimit: (dimensions of the
   * image)-1
   *
   * @param z z-coordinate of the pixel that has a neighbour in the given direction
   * @param direction the direction from the pixel towards the neighbour
   * @return true if the neighbour is within the image (provided that z is within)
   */
  private boolean isWithinZ(int z, int direction) {
    // z = 0
    if (direction < 4) {
      return true;
    }
    // z = -1
    if (direction == 4) {
      return z > 0;
    }
    // z = 1
    if (direction == 5) {
      return z < zlimit;
    }
    // z = 0
    if (direction < 10) {
      return true;
    }
    // z = -1
    if (direction < 18) {
      return z > 0;
    }
    // z = 1
    return z < zlimit;
  }

  /**
   * Gets the max X.
   *
   * @return The image width (maxx).
   */
  public int getMaxX() {
    return maxx;
  }

  /**
   * Gets the max Y.
   *
   * @return The image height (maxy).
   */
  public int getMaxY() {
    return maxy;
  }

  /**
   * Gets the max Z.
   *
   * @return The image depth (maxz).
   */
  public int getMaxZ() {
    return maxy;
  }

  /**
   * Get the centre-of-mass and pixel count of each object. Data is stored indexed by the object
   * value so processing of results should start from 1.
   *
   * @return The centre-of-mass of each object (plus the pixel count) [object][cx,cy,cz,n]
   */
  public double[][] getObjectCentres() {
    final int[] count = new int[maxObject + 1];
    final double[] sumx = new double[count.length];
    final double[] sumy = new double[count.length];
    final double[] sumz = new double[count.length];
    for (int z = 0, i = 0; z < maxz; z++) {
      for (int y = 0; y < maxy; y++) {
        for (int x = 0; x < maxx; x++, i++) {
          final int value = objectMask[i];
          if (value != 0) {
            sumx[value] += x;
            sumy[value] += y;
            sumz[value] += z;
            count[value]++;
          }
        }
      }
    }
    final double[][] data = new double[count.length][3];
    for (int i = 1; i < count.length; i++) {
      data[i][0] = sumx[i] / count[i];
      data[i][1] = sumy[i] / count[i];
      data[i][2] = sumz[i] / count[i];
      data[i][3] = count[i];
    }
    return data;
  }

  /**
   * Gets the minimum object size. Objects below this are removed.
   *
   * @return The minimum object size. Objects below this are removed.
   */
  public int getMinObjectSize() {
    return minObjectSize;
  }

  /**
   * Sets the minimum object size. Objects below this are removed.
   *
   * @param minObjectSize The minimum object size. Objects below this are removed.
   */
  public void setMinObjectSize(int minObjectSize) {
    if (minObjectSize != this.minObjectSize) {
      this.objectMask = null;
    }
    this.minObjectSize = minObjectSize;
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
    if (eightConnected != this.eightConnected) {
      this.objectMask = null;
    }
    this.eightConnected = eightConnected;
  }
}
