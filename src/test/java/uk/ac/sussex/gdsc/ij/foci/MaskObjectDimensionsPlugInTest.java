/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2026 Alex Herbert
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

import java.util.SplittableRandom;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.ac.sussex.gdsc.ij.foci.MaskObjectDimensions_PlugIn.MaskObject;

@SuppressWarnings({"javadoc"})
class MaskObjectDimensionsPlugInTest {
  @Test
  void testCreateObjectsWithNoObjects() {
    final int[] image = new int[24];
    final int[] histogram = new int[256];
    histogram[0] = image.length;
    Assertions.assertEquals(0,
        MaskObjectDimensions_PlugIn.createObjects(image, histogram, 4, 3, 2).length);
  }

  @Test
  void testCreateObjectsWithSparseValues() {
    final int maxx = 13;
    final int maxy = 11;
    final int maxz = 3;
    final int[] image = new int[maxx * maxy * maxz];
    final int[] values = {0, 1, 7, 300, 60000, 65535};
    final SplittableRandom rng = new SplittableRandom(12345);
    final int[] histogram = new int[65536];
    for (int i = 0; i < image.length; i++) {
      // Value 300 is not used
      final int v = values[rng.nextInt(values.length)];
      image[i] = v == 300 ? 0 : v;
      histogram[image[i]]++;
    }
    final MaskObject[] objects =
        MaskObjectDimensions_PlugIn.createObjects(image, histogram, maxx, maxy, maxz);
    final int[] expectedValues = {1, 7, 60000, 65535};
    Assertions.assertEquals(expectedValues.length, objects.length);
    for (int k = 0; k < expectedValues.length; k++) {
      final int object = expectedValues[k];
      // Brute force Centre-of-Mass
      double cx = 0;
      double cy = 0;
      double cz = 0;
      int count = 0;
      for (int z = 0, i = 0; z < maxz; z++) {
        for (int y = 0; y < maxy; y++) {
          for (int x = 0; x < maxx; x++, i++) {
            if (image[i] == object) {
              cx += x;
              cy += y;
              cz += z;
              count++;
            }
          }
        }
      }
      final MaskObject o = objects[k];
      Assertions.assertEquals(object, o.getValue());
      Assertions.assertEquals(count, o.size);
      Assertions.assertEquals(cx / count + 0.5, o.cx);
      Assertions.assertEquals(cy / count + 0.5, o.cy);
      Assertions.assertEquals(cz / count + 0.5, o.cz);
    }
  }

  @Test
  void testEigenSort3x3() {
    final double[] weights = {0, 1, 2};
    final double[][] vectors = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
    MaskObjectDimensions_PlugIn.eigenSort3x3(weights, vectors);
    Assertions.assertArrayEquals(new double[] {2, 1, 0}, weights);
    Assertions.assertArrayEquals(new double[][] {{0, 0, 1}, {0, 1, 0}, {1, 0, 0}}, vectors);
  }

  @Test
  void testEigenSort3x3WithTiedWeights() {
    // Eigen decomposition of the inertia tensor of a horizontal line
    final double[] weights = {2, 2, 0};
    final double[][] vectors = {{0, 1, 0}, {0, 0, 1}, {1, 0, 0}};
    MaskObjectDimensions_PlugIn.eigenSort3x3(weights, vectors);
    Assertions.assertArrayEquals(new double[] {2, 2, 0}, weights);
    // The vector for each weight is unchanged
    for (int i = 0; i < 3; i++) {
      final double[] v = vectors[i];
      if (weights[i] == 0) {
        Assertions.assertArrayEquals(new double[] {1, 0, 0}, v);
      } else {
        Assertions.assertEquals(0, v[0]);
        Assertions.assertEquals(1, Math.abs(v[1]) + Math.abs(v[2]));
      }
    }
  }

  @Test
  void testFindAxisEndsOnDiagonal() {
    // Line of pixels (x, 9 - x)
    final int w = 10;
    final int h = 10;
    final int[] image = new int[w * h];
    for (int x = 0; x < w; x++) {
      image[(9 - x) * w + x] = 1;
    }
    final double[] com = {5.5, 4.5, 0.5};
    final double[] axis = {Math.sqrt(0.5), -Math.sqrt(0.5), 0};
    final double[] end1 = com.clone();
    final double[] end2 = com.clone();
    MaskObjectDimensions_PlugIn.findAxisEnds(image, w, h, 1, com, axis, new int[] {0, 0, 0},
        new int[] {w, h, 1}, end1, end2);
    Assertions.assertArrayEquals(new double[] {9.5, 0.5, 0.5}, end1, 1e-10);
    Assertions.assertArrayEquals(new double[] {0.5, 9.5, 0.5}, end2, 1e-10);
  }
}
