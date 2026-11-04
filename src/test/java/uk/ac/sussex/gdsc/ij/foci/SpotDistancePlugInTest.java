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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.ac.sussex.gdsc.ij.foci.SpotDistance_PlugIn.DistanceResult;
import uk.ac.sussex.gdsc.ij.foci.SpotDistance_PlugIn.Tracker;

@SuppressWarnings({"javadoc"})
class SpotDistancePlugInTest {
  @Test
  void testTrackerAssignIds() {
    final Tracker tracker = new Tracker();
    final double distance = 100;

    // First frame: IDs are assigned in order
    final List<DistanceResult> frame1 = create(new int[][] {{10, 10}, {30, 30}, {50, 10}});
    tracker.assignIds(frame1, distance);
    assertIds(frame1, new int[][] {{10, 10, 1}, {30, 30, 2}, {50, 10, 3}});

    // Second frame: spots move slightly and are listed in a different order.
    // Spot 2 is lost.
    final List<DistanceResult> frame2 = create(new int[][] {{51, 11}, {11, 9}});
    tracker.assignIds(frame2, distance);
    assertIds(frame2, new int[][] {{11, 9, 1}, {51, 11, 3}});

    // Third frame: a new spot does not reuse the ID of the lost spot
    final List<DistanceResult> frame3 = create(new int[][] {{12, 9}, {52, 12}, {90, 90}});
    tracker.assignIds(frame3, distance);
    assertIds(frame3, new int[][] {{12, 9, 1}, {52, 12, 3}, {90, 90, 4}});
  }

  @Test
  void testTrackerAssignIdsWithEmptyFrame() {
    final Tracker tracker = new Tracker();
    final List<DistanceResult> frame1 = create(new int[][] {{10, 10}});
    tracker.assignIds(frame1, 10);
    final List<DistanceResult> frame2 = create(new int[0][]);
    tracker.assignIds(frame2, 10);
    final List<DistanceResult> frame3 = create(new int[][] {{10, 10}});
    tracker.assignIds(frame3, 10);
    // Nothing to track from the empty frame
    assertIds(frame3, new int[][] {{10, 10, 2}});
  }

  private static List<DistanceResult> create(int[][] xy) {
    final List<DistanceResult> list = new ArrayList<>();
    for (final int[] p : xy) {
      list.add(new DistanceResult(p[0], p[1], 0, 1));
    }
    return list;
  }

  private static void assertIds(List<DistanceResult> results, int[][] expected) {
    final int[][] actual = results.stream().map(r -> new int[] {r.getXint(), r.getYint(), r.id})
        .toArray(int[][]::new);
    Assertions.assertArrayEquals(expected, actual, () -> Arrays.deepToString(actual));
  }
}
