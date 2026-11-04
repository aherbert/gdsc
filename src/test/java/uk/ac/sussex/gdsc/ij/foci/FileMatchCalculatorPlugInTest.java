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

import ij.process.ByteProcessor;
import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"javadoc"})
class FileMatchCalculatorPlugInTest {
  @Test
  void testFilterRemovesPointsOutsideTheMask() {
    final ByteProcessor mask = new ByteProcessor(5, 4);
    mask.set(0, 1, 1);
    mask.set(2, 2, 1);
    final TimeValuedPoint inside = new TimeValuedPoint(2.5f, 2.5f, 0, 1, 0);
    final TimeValuedPoint[] points = {
        // Past the right edge: the next row is (0, 1) which is in the mask
        new TimeValuedPoint(5, 0, 0, 1, 0),
        // Far outside the image
        new TimeValuedPoint(50, 50, 0, 1, 0),
        // Negative
        new TimeValuedPoint(-0.5f, 1, 0, 1, 0), inside,
        // Outside the mask
        new TimeValuedPoint(1, 1, 0, 1, 0),};
    final TimeValuedPoint[] result = FileMatchCalculator_PlugIn.filter(points, mask);
    Assertions.assertArrayEquals(new TimeValuedPoint[] {inside}, result);
  }

  @Test
  void testGetTimepointsIsSorted() {
    final TimeValuedPoint[] p1 = new TimeValuedPoint[50];
    final TimeValuedPoint[] p2 = new TimeValuedPoint[50];
    for (int i = 0; i < p1.length; i++) {
      p1[i] = new TimeValuedPoint(0, 0, 0, 1000 - 17 * i, 0);
      p2[i] = new TimeValuedPoint(0, 0, 0, 3 + 31 * i, 0);
    }
    final int[] times = FileMatchCalculator_PlugIn.getTimepoints(p1, p2);
    final int[] expected = times.clone();
    Arrays.sort(expected);
    Assertions.assertArrayEquals(expected, times);
  }
}
