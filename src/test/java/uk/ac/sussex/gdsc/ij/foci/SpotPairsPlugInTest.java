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
}
