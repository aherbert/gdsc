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
import java.util.stream.IntStream;
import org.apache.commons.rng.UniformRandomProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.ac.sussex.gdsc.core.match.Coordinate;
import uk.ac.sussex.gdsc.test.junit5.SeededTest;
import uk.ac.sussex.gdsc.test.rng.RngFactory;
import uk.ac.sussex.gdsc.test.utils.RandomSeed;

@SuppressWarnings({"javadoc"})
class CoordinateUtilsTest {
  @SeededTest
  void canGetSquaredDistanceFunction(RandomSeed seed) {
    final UniformRandomProvider rng = RngFactory.create(seed.get());
    final Coordinate[] coords = IntStream.range(0, 5)
        .mapToObj(i -> new uk.ac.sussex.gdsc.core.match.BasePoint(rng.nextInt(32), rng.nextInt(32),
            rng.nextInt(32)))
        .toArray(Coordinate[]::new);

    // 2D image, no calibration
    assertGetSquaredDistanceFunction(coords, 1, 1, 0);
    // 2D image, different XY distances
    assertGetSquaredDistanceFunction(coords, 2, 3, 0);
    assertGetSquaredDistanceFunction(coords, 3, 2, 0);
    // 3D image, no calibration
    assertGetSquaredDistanceFunction(coords, 1, 1, 1);
    // 3D image, difference Z distances
    assertGetSquaredDistanceFunction(coords, 2, 2, 4);
    // 3D image, difference XYZ distances
    assertGetSquaredDistanceFunction(coords, 2, 3, 4);
    assertGetSquaredDistanceFunction(coords, 3, 1, 4);
  }

  private static void assertGetSquaredDistanceFunction(Coordinate[] coords, double width,
      double height, double depth) {
    final double sy = height / width;
    final double sz = depth / width;
    final ToDoubleBiFunction<Coordinate, Coordinate> df1 = (c1, c2) -> {
      final double dx = c1.getX() - c2.getX();
      final double dy = (c1.getY() - c2.getY()) * sy;
      final double dz = (c1.getZ() - c2.getZ()) * sz;
      return dx * dx + dy * dy + dz * dz;
    };
    final Calibration cal = new Calibration();
    cal.pixelWidth = width;
    cal.pixelHeight = height;
    cal.pixelDepth = depth;
    final boolean is3d = depth != 0;
    final ToDoubleBiFunction<Coordinate, Coordinate> df2 =
        CoordinateUtils.getSquaredDistanceFunction(cal, is3d);
    for (int i = 0; i < coords.length; i++) {
      final Coordinate c1 = coords[i];
      for (int j = i; j < coords.length; j++) {
        final Coordinate c2 = coords[j];
        Assertions.assertEquals(df1.applyAsDouble(c1, c2), df2.applyAsDouble(c1, c2));
      }
    }
  }

  @Test
  void testIs3d() {
    final Coordinate[] empty = new Coordinate[0];
    final Coordinate[] z0 = new Coordinate[] {new BasePoint(0, 0, 0)};
    final Coordinate[] z1 = new Coordinate[] {new BasePoint(0, 0, 1)};
    final Coordinate[] z11 = new Coordinate[] {new BasePoint(0, 0, 1), new BasePoint(1, 1, 1)};
    final Coordinate[] z12 = new Coordinate[] {new BasePoint(0, 0, 1), new BasePoint(0, 0, 2)};
    Assertions.assertFalse(CoordinateUtils.is3d(empty, empty));
    Assertions.assertFalse(CoordinateUtils.is3d(empty, z0));
    Assertions.assertFalse(CoordinateUtils.is3d(empty, z1));
    Assertions.assertFalse(CoordinateUtils.is3d(empty, z11));
    Assertions.assertTrue(CoordinateUtils.is3d(empty, z12));
    Assertions.assertFalse(CoordinateUtils.is3d(z0, empty));
    Assertions.assertFalse(CoordinateUtils.is3d(z1, empty));
    Assertions.assertFalse(CoordinateUtils.is3d(z11, empty));
    Assertions.assertTrue(CoordinateUtils.is3d(z12, empty));
    Assertions.assertFalse(CoordinateUtils.is3d(z1, z1));
    Assertions.assertFalse(CoordinateUtils.is3d(z1, z11));
    Assertions.assertTrue(CoordinateUtils.is3d(z0, z1));
    Assertions.assertTrue(CoordinateUtils.is3d(z0, z11));
    Assertions.assertTrue(CoordinateUtils.is3d(z1, z0));
    Assertions.assertTrue(CoordinateUtils.is3d(z11, z0));
    Assertions.assertTrue(CoordinateUtils.is3d(z12, z12));
  }
}
