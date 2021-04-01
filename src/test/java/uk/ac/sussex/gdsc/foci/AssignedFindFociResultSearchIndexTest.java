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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.function.IntSupplier;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.sampling.CombinationSampler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.ac.sussex.gdsc.core.trees.DoubleDistanceFunction;
import uk.ac.sussex.gdsc.foci.AssignedFindFociResultSearchIndex.SearchMode;
import uk.ac.sussex.gdsc.test.junit5.RandomSeed;
import uk.ac.sussex.gdsc.test.junit5.SeededTest;
import uk.ac.sussex.gdsc.test.rng.RngUtils;

@SuppressWarnings({"javadoc"})
class AssignedFindFociResultSearchIndexTest {
  @Test
  void testSearchMode() {
    Assertions.assertNull(SearchMode.forOrdinal(-1));
    for (final SearchMode sm : SearchMode.values()) {
      Assertions.assertSame(sm, SearchMode.forOrdinal(sm.ordinal()));
      Assertions.assertNotEquals(sm.name(), sm.toString());
    }
  }

  @Test
  void testConstructor() {
    final ArrayList<FindFociResult> results = new ArrayList<>();
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new AssignedFindFociResultSearchIndex(results, 0, 1, 1));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new AssignedFindFociResultSearchIndex(results, 1, 0, 1));

    // Can create empty
    Assertions.assertEquals(0, new AssignedFindFociResultSearchIndex(results, 1, 1, 1).size());

    // 2d results are OK
    results.add(createResult(10, 9, 8, 7));
    final AssignedFindFociResultSearchIndex index =
        new AssignedFindFociResultSearchIndex(results, 1, 1, 0);
    Assertions.assertEquals(1, index.size());

    // Still 2d
    results.add(createResult(11, 12, 8, 7));
    Assertions.assertEquals(2, new AssignedFindFociResultSearchIndex(results, 1, 1, 0).size());
    // Make 3d
    results.add(createResult(10, 9, 5, 7));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new AssignedFindFociResultSearchIndex(results, 1, 1, 0));
  }

  @Test
  void testSearchModeProperty() {
    final ArrayList<FindFociResult> results = new ArrayList<>();
    final AssignedFindFociResultSearchIndex index =
        new AssignedFindFociResultSearchIndex(results, 1, 1, 1);
    for (final SearchMode sm : SearchMode.values()) {
      Assertions.assertSame(index, index.setSearchMode(sm));
      Assertions.assertSame(sm, index.getSearchMode());
    }
  }

  @Test
  void testSearchDistanceProperty() {
    final ArrayList<FindFociResult> results = new ArrayList<>();
    final AssignedFindFociResultSearchIndex index =
        new AssignedFindFociResultSearchIndex(results, 1, 1, 1);
    Assertions.assertThrows(IllegalArgumentException.class, () -> index.setSearchDistance(-1));
    for (final int pixels : new int[] {0, 1, 5, 15}) {
      Assertions.assertSame(index, index.setSearchDistance(pixels));
      Assertions.assertEquals(pixels, index.getSearchDistance());
    }
  }

  @SeededTest
  void testDistanceFunction(RandomSeed seed) {
    testDistanceFunction(seed, 32, 16, 0, 1, 1, 0);
    testDistanceFunction(seed, 32, 16, 0, 1.5, 2.5, 0);
    testDistanceFunction(seed, 32, 16, 8, 1, 1, 1);
    testDistanceFunction(seed, 32, 16, 8, 1, 1, 3);
    testDistanceFunction(seed, 32, 16, 8, 2, 1, 3);
  }

  private static void testDistanceFunction(RandomSeed seed, int width, int height, int depth,
      double sx, double sy, double sz) {
    final ArrayList<FindFociResult> results = new ArrayList<>();
    final UniformRandomProvider rng = RngUtils.create(seed.getSeed());
    final IntSupplier z = depth == 0 ? () -> 0 : () -> rng.nextInt(depth);
    final int range = 10;
    for (int i = 0; i < 10; i++) {
      results.add(
          createResult(rng.nextInt(width), rng.nextInt(height), z.getAsInt(), rng.nextInt(range)));
    }
    final AssignedFindFociResultSearchIndex index =
        new AssignedFindFociResultSearchIndex(results, sx, sy, sz);
    final DoubleDistanceFunction df = index.getDistanceFunction();
    for (int i = 0; i < 10; i++) {
      final FindFociResult r1 = results.get(i);
      final double[] p1 = {r1.getX(), r1.getY(), r1.getZ()};
      for (int j = i + 1; j < 10; j++) {
        final FindFociResult r2 = results.get(i);
        final double[] p2 = {r2.getX(), r2.getY(), r2.getZ()};
        Assertions.assertEquals(distance2(r1, r2, sx, sy, sz), df.distance(p1, p2));
      }
    }
  }

  @SeededTest
  void canForEach(RandomSeed seed) {
    final UniformRandomProvider rng = RngUtils.create(seed.getSeed());
    final int w = 32;
    final int h = 32;
    final int d = 32;
    final int range = 10;
    final ArrayList<FindFociResult> results = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      results.add(createResult(rng.nextInt(w), rng.nextInt(h), rng.nextInt(d), rng.nextInt(range)));
    }
    final AssignedFindFociResultSearchIndex index =
        new AssignedFindFociResultSearchIndex(results, 1, 1, 1);
    final HashSet<FindFociResult> set = new HashSet<>(results);
    index.forEach(r -> Assertions.assertTrue(set.remove(r.getResult())));
    Assertions.assertEquals(0, set.size());
  }

  @SeededTest
  void canSetAssigned(RandomSeed seed) {
    final UniformRandomProvider rng = RngUtils.create(seed.getSeed());
    final ArrayList<FindFociResult> results = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      results.add(createResult(i, i, i, i));
    }
    final AssignedFindFociResultSearchIndex index =
        new AssignedFindFociResultSearchIndex(results, 1, 1, 1);
    index.forEach(r -> r.setAssigned(rng.nextBoolean()));
    index.setAssigned(true);
    index.forEach(r -> Assertions.assertTrue(r.isAssigned()));
    index.setAssigned(false);
    index.forEach(r -> Assertions.assertFalse(r.isAssigned()));
  }

  @SeededTest
  void canFindPoints3d(RandomSeed seed) {
    canFindPoints(seed, 32, 32, 32, 1, 1, 1);
  }

  @SeededTest
  void canFindPoints3dWithScaledDimensions(RandomSeed seed) {
    canFindPoints(seed, 32, 32, 32, 1.5, 2.0, 2.5);
  }

  @SeededTest
  void canFindPoints2d(RandomSeed seed) {
    canFindPoints(seed, 32, 32, 1, 1, 1, 1);
  }

  @SeededTest
  void canFindPoints2dWithScaledDimensions(RandomSeed seed) {
    canFindPoints(seed, 32, 32, 1, 1.5, 2.0, 1);
  }

  @SeededTest
  void canFindPoints2dWithUniformScaledDimensions(RandomSeed seed) {
    canFindPoints(seed, 32, 32, 1, 1.5, 1.5, 1);
  }

  private static void canFindPoints(RandomSeed seed, int width, int height, int depth, double sx,
      double sy, double sz) {
    final UniformRandomProvider rng = RngUtils.create(seed.getSeed());
    final int range = 10;
    final ArrayList<FindFociResult> results = new ArrayList<>();
    // Results should be unique for xyz so use a random set of the possible XYZ indices
    final int xy = width * height;
    final int xyz = xy * depth;
    for (int index : new CombinationSampler(rng, xyz, 50).sample()) {
      final int z = index / xy;
      final int mod = index % xy;
      final int y = mod / width;
      final int x = mod % width;
      results.add(createResult(x, y, z, 1 + rng.nextInt(range)));
    }
    final AssignedFindFociResultSearchIndex index =
        new AssignedFindFociResultSearchIndex(results, sx, sy, sz);
    Assertions.assertEquals(results.size(), index.size());

    final int pixels = 10;
    final double threshold = pixels * pixels;
    index.setSearchDistance(pixels);

    for (final FindFociResult r : results) {
      final AssignedFindFociResult r1 = index.findExact(r.x, r.y, r.z, false);
      assertXyzEquals(r, r1.getResult());
      // Should not find an assigned result
      Assertions.assertNull(index.findExact(r.x, r.y, r.z, true));

      // Assign this result and may find another
      r1.setAssigned(true);
      final AssignedFindFociResult r2 = index.findExact(r.x, r.y, r.z, false);
      if (r2 != null) {
        assertXyzEquals(r, r2.getResult());
      }

      // Search for the closest at the default search distance.
      double min = threshold;
      FindFociResult closest = null;
      for (final FindFociResult rr : results) {
        if (r != rr) {
          final double d2 = distance2(r, rr, sx, sy, sz);
          if (d2 <= min) {
            min = d2;
            closest = rr;
          }
        }
      }
      final AssignedFindFociResult r3 = index.findClosest(r.x, r.y, r.z, false);
      if (closest != null) {
        // It may not match the coordinates but the distance should match
        final double d1 = distance2(r, closest, sx, sy, sz);
        final double d2 = distance2(r, r3.getResult(), sx, sy, sz);
        Assertions.assertEquals(d1, d2, 1e-8);
      } else {
        Assertions.assertNull(r3);
      }
      index.setSearchMode(SearchMode.CLOSEST);
      Assertions.assertSame(r3, index.find(r.x, r.y, r.z, false));

      // Search for the highest at the default search distance.
      FindFociResult highest = null;
      double max = -1;
      for (final FindFociResult rr : results) {
        if (r != rr) {
          final double d2 = distance2(r, rr, sx, sy, sz);
          if (d2 <= threshold && max < rr.maxValue) {
            max = rr.maxValue;
            highest = rr;
          }
        }
      }
      final AssignedFindFociResult r4 = index.findHighest(r.x, r.y, r.z, false);
      if (highest != null) {
        // It may not match the coordinates but the max value should match
        Assertions.assertEquals(max, r4.getResult().maxValue);
      } else {
        Assertions.assertNull(r4);
      }
      index.setSearchMode(SearchMode.HIGHEST);
      Assertions.assertSame(r4, index.find(r.x, r.y, r.z, false));

      // Reset
      r1.setAssigned(false);
    }
  }

  /**
   * Creates the result.
   *
   * @param x the x
   * @param y the y
   * @param z the z
   * @param maxValue the max value
   * @return the find foci result
   */
  private static FindFociResult createResult(int x, int y, int z, int maxValue) {
    final FindFociResult r = new FindFociResult();
    r.x = x;
    r.y = y;
    r.z = z;
    r.maxValue = maxValue;
    return r;
  }

  /**
   * Assert the xyz coordinates are equal.
   *
   * @param r1 the first result
   * @param r2 the second result
   */
  private static void assertXyzEquals(FindFociResult r1, FindFociResult r2) {
    Assertions.assertEquals(r1.x, r2.x);
    Assertions.assertEquals(r1.y, r2.y);
    Assertions.assertEquals(r1.z, r2.z);
  }

  /**
   * Compute the squared Euclidean distance. The Y and Z dimensions are scaled relative to the X
   * dimension.
   *
   * @param r1 the first result
   * @param r2 the second result
   * @param sx the scale x
   * @param sy the scale y
   * @param sz the scale z
   * @return the squared Euclidean distance
   */
  private static double distance2(FindFociResult r1, FindFociResult r2, double sx, double sy,
      double sz) {
    final double dx = (r1.x - r2.x);
    final double dy = (r1.y - r2.y) * sy / sx;
    final double dz = (r1.z - r2.z) * sz / sx;
    return (dx * dx + dy * dy + dz * dz);
  }
}
