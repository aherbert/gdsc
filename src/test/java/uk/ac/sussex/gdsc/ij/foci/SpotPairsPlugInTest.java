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

import ij.ImagePlus;
import ij.gui.PointRoi;
import ij.process.ByteProcessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.ac.sussex.gdsc.ij.foci.SpotPairs_PlugIn.Cluster;
import uk.ac.sussex.gdsc.ij.foci.SpotPairs_PlugIn.ClusterPoint;

@SuppressWarnings({"javadoc"})
class SpotPairsPlugInTest {
  @Test
  void testFindPairsUsesRoiPointIds() {
    final ImagePlus imp = new ImagePlus("test", new ByteProcessor(50, 50));
    // Point 1 and 3 are a pair; point 2 is single
    imp.setRoi(new PointRoi(new float[] {10, 40, 13}, new float[] {10, 40, 10}));
    final AssignedPoint[] points = AssignedPointUtils.extractRoiPoints(imp);
    final ArrayList<Cluster> clusters = SpotPairs_PlugIn.findPairs(points, 5);
    final int[][] ids = clusters.stream().map(c -> {
      final int[] a = new int[c.size];
      int i = 0;
      for (ClusterPoint p = c.head; p != null; p = p.next) {
        a[i++] = p.id;
      }
      Arrays.sort(a);
      return a;
    }).sorted((a, b) -> Integer.compare(a[0], b[0])).toArray(int[][]::new);
    Assertions.assertArrayEquals(new int[][] {{1, 3}, {2}}, ids);
  }

  /**
   * Find the pairs and return the point IDs of each cluster. IDs are sorted within the cluster and
   * the clusters are sorted by the first ID.
   */
  private static int[][] findPairIds(AssignedPoint[] points, double radius) {
    return SpotPairs_PlugIn.findPairs(points, radius).stream().map(c -> {
      final int[] a = new int[c.size];
      int i = 0;
      for (ClusterPoint p = c.head; p != null; p = p.next) {
        a[i++] = p.id;
      }
      Arrays.sort(a);
      return a;
    }).sorted((a, b) -> Integer.compare(a[0], b[0])).toArray(int[][]::new);
  }

  /** Call the action with every permutation of the points. */
  private static void forEachPermutation(AssignedPoint[] points, int k,
      java.util.function.Consumer<AssignedPoint[]> action) {
    if (k == points.length) {
      action.accept(points.clone());
      return;
    }
    for (int i = k; i < points.length; i++) {
      swap(points, k, i);
      forEachPermutation(points, k + 1, action);
      swap(points, k, i);
    }
  }

  private static void swap(AssignedPoint[] a, int i, int j) {
    final AssignedPoint t = a[i];
    a[i] = a[j];
    a[j] = t;
  }

  private static void assertPairs(int[][] expected, AssignedPoint[] points, double radius) {
    final List<String> errors = new java.util.ArrayList<>();
    forEachPermutation(points, 0, p -> {
      final int[][] ids = findPairIds(p, radius);
      if (!Arrays.deepEquals(expected, ids)) {
        errors.add(Arrays.deepToString(ids));
      }
    });
    Assertions.assertEquals(List.of(), errors);
  }

  @Test
  void testFindPairsUsesMutualNearestNeighbours() {
    // X and Y are a pair. C is closest to X; Z is closest to C. C and Z are not paired.
    // Point IDs: X=1, Y=2, C=3, Z=4
    assertPairs(new int[][] {{1, 2}, {3}, {4}}, new AssignedPoint[] {
        new AssignedPoint(10, 10, 1), new AssignedPoint(8, 10, 2), new AssignedPoint(14, 10, 3),
        new AssignedPoint(14, 15, 4)}, 10);
    // Mirror in x
    assertPairs(new int[][] {{1, 2}, {3}, {4}}, new AssignedPoint[] {
        new AssignedPoint(20, 20, 1), new AssignedPoint(22, 20, 2), new AssignedPoint(16, 20, 3),
        new AssignedPoint(16, 15, 4)}, 10);
  }

  @Test
  void testFindPairsTieUsesLowestId() {
    // Point 1 is the same distance from 2 and 3
    assertPairs(new int[][] {{1, 2}, {3}}, new AssignedPoint[] {new AssignedPoint(10, 10, 1),
        new AssignedPoint(13, 10, 2), new AssignedPoint(7, 10, 3)}, 10);
  }

  @Test
  void testFindPairsRadius() {
    final AssignedPoint[] points = {new AssignedPoint(10, 10, 1), new AssignedPoint(13, 14, 2)};
    // Distance is 5
    Assertions.assertArrayEquals(new int[][] {{1}, {2}}, findPairIds(points, 5));
    Assertions.assertArrayEquals(new int[][] {{1, 2}}, findPairIds(points, 5.01));
    // Same position
    final AssignedPoint[] points2 = {new AssignedPoint(10, 10, 1), new AssignedPoint(10, 10, 2)};
    Assertions.assertArrayEquals(new int[][] {{1, 2}}, findPairIds(points2, 0.5));
  }
}
