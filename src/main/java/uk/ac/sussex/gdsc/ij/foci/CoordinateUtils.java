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

import ij.measure.Calibration;
import java.util.function.ToDoubleBiFunction;
import uk.ac.sussex.gdsc.core.match.Coordinate;

/**
 * Utilities for coordinates.
 */
final class CoordinateUtils {
  /** No public constructor. */
  private CoordinateUtils() {}

  /**
   * Gets a squared Euclidean distance function in 2D/3D. If calibrated then the distance in Z is
   * scaled to equivalent pixels units for the XY dimensions.
   *
   * @param cal the calibration
   * @param is3d true if the target results are 3D
   * @return the distance function
   */
  static ToDoubleBiFunction<Coordinate, Coordinate> getSquaredDistanceFunction(Calibration cal,
      boolean is3d) {
    if (is3d) {
      if (cal.pixelWidth == cal.pixelHeight) {
        // No XY scaling
        if (cal.pixelDepth == cal.pixelWidth) {
          // No scaling
          return (c1, c2) -> {
            final double dx = c1.getX() - c2.getX();
            final double dy = c1.getY() - c2.getY();
            final double dz = c1.getZ() - c2.getZ();
            return dx * dx + dy * dy + dz * dz;
          };
        }
        // Z scaling
        final double sz = cal.pixelDepth / cal.pixelWidth;
        return (c1, c2) -> {
          final double dx = c1.getX() - c2.getX();
          final double dy = c1.getY() - c2.getY();
          final double dz = (c1.getZ() - c2.getZ()) * sz;
          return dx * dx + dy * dy + dz * dz;
        };
      }
      // YZ scaling
      final double sy = cal.pixelHeight / cal.pixelWidth;
      final double sz = cal.pixelDepth / cal.pixelWidth;
      return (c1, c2) -> {
        final double dx = c1.getX() - c2.getX();
        final double dy = (c1.getY() - c2.getY()) * sy;
        final double dz = (c1.getZ() - c2.getZ()) * sz;
        return dx * dx + dy * dy + dz * dz;
      };
    }
    if (cal.pixelWidth == cal.pixelHeight) {
      // No scaling
      return (c1, c2) -> {
        final double dx = c1.getX() - c2.getX();
        final double dy = c1.getY() - c2.getY();
        return dx * dx + dy * dy;
      };
    }
    // Y scaling
    final double sy = cal.pixelHeight / cal.pixelWidth;
    return (c1, c2) -> {
      final double dx = c1.getX() - c2.getX();
      final double dy = (c1.getY() - c2.getY()) * sy;
      return dx * dx + dy * dy;
    };
  }

  /**
   * Checks if there is more than one z-value in the two sets of coordinates.
   *
   * <p>Note: This will return true if each set of points is only from a single plane but the two
   * planes are different, e.g. z=0 and z=1.
   *
   * @param points1 the first set of points
   * @param points2 the second set of points
   * @return true, if is 3d
   */
  static boolean is3d(Coordinate[] points1, Coordinate[] points2) {
    final Coordinate[] longest = points1.length == 0 ? points2 : points1;
    if (longest.length == 0) {
      return false;
    }
    final float z = longest[0].getZ();
    for (final Coordinate p : points1) {
      if (p.getZ() != z) {
        return true;
      }
    }
    for (final Coordinate p : points2) {
      if (p.getZ() != z) {
        return true;
      }
    }
    return false;
  }
}
