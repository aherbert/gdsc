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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"javadoc"})
class MaskObjectDimensionsPlugInTest {
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
