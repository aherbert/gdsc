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

import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.process.ByteProcessor;
import ij.process.FloatPolygon;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@SuppressWarnings({"javadoc"})
class MaskAnalyzeChannelsPlugInTest {
  @ParameterizedTest
  @CsvSource({
      // Single square
      "2, 2, 5, -1, -1, 0",
      // Square at the image edge
      "5, 5, 5, -1, -1, 0",
      // Two separate squares
      "0, 0, 3, 6, 6, 3",
      // Single pixels
      "4, 4, 1, 7, 2, 1"})
  void testFindIndices(int x1, int y1, int size1, int x2, int y2, int size2) {
    final int w = 10;
    final int h = 10;
    final ByteProcessor ip = new ByteProcessor(w, h);
    fill(ip, x1, y1, size1, 1);
    int expected = size1 * size1;
    if (x2 >= 0) {
      fill(ip, x2, y2, size2, 2);
      expected += size2 * size2;
    }
    final ObjectAnalyzer oa = new ObjectAnalyzer(ip, true);
    final FloatPolygon hull = MaskAnalyzeChannels_PlugIn.createConvexHull(oa);
    final Roi roi = new PolygonRoi(hull, Roi.POLYGON);
    final IntArrayList objects = new IntArrayList();
    final IntArrayList nonObjects = new IntArrayList();
    MaskAnalyzeChannels_PlugIn.findIndices(oa, roi, objects, nonObjects);

    // All the object pixels are found
    Assertions.assertEquals(expected, objects.size(), "objects");
    objects.forEach(i -> Assertions.assertNotEquals(0, ip.get(i)));
    // The non-object pixels have a centre inside the hull
    int count = 0;
    for (int y = 0, i = 0; y < h; y++) {
      for (int x = 0; x < w; x++, i++) {
        if (ip.get(i) == 0 && hull.contains(x + 0.5f, y + 0.5f)) {
          count++;
          Assertions.assertTrue(nonObjects.contains(i), "non-object");
        }
      }
    }
    Assertions.assertEquals(count, nonObjects.size(), "non-objects");
  }

  private static void fill(ByteProcessor ip, int x, int y, int size, int value) {
    ip.setValue(value);
    ip.setRoi(x, y, size, size);
    ip.fill();
    ip.resetRoi();
  }
}
