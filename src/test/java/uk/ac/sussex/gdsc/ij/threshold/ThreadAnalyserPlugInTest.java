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

package uk.ac.sussex.gdsc.ij.threshold;

import ij.Prefs;
import ij.gui.Roi;
import ij.process.ByteProcessor;
import ij.process.ShortProcessor;
import java.awt.Polygon;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@SuppressWarnings({"javadoc"})
class ThreadAnalyserPlugInTest {
  @Test
  void testCreateLabelsAtThreadMiddle() {
    // Thread starting at (10,10) moving East for 4 steps: (10,10) to (14,10)
    final ChainCode east = new ChainCode(10, 10);
    for (int i = 0; i < 4; i++) {
      east.add(2);
    }
    // Thread starting at (20,5) moving South for 6 steps: (20,5) to (20,11)
    final ChainCode south = new ChainCode(20, 5);
    for (int i = 0; i < 6; i++) {
      south.add(4);
    }
    final Roi roi = ThreadAnalyser_PlugIn.createLabels(Arrays.asList(east, south));
    final Polygon p = roi.getPolygon();
    Assertions.assertEquals(2, p.npoints);
    Assertions.assertEquals(12, p.xpoints[0]);
    Assertions.assertEquals(10, p.ypoints[0]);
    Assertions.assertEquals(20, p.xpoints[1]);
    Assertions.assertEquals(8, p.ypoints[1]);
  }

  @ParameterizedTest
  @CsvSource({"1, true", "1, false", "255, true", "255, false"})
  void testGetMaskFromExistingMask(int value, boolean blackBackground) {
    // A horizontal bar 3 pixels thick with a single non-zero value
    final ShortProcessor ip = new ShortProcessor(40, 20);
    for (int y = 9; y <= 11; y++) {
      for (int x = 5; x < 35; x++) {
        ip.set(x, y, value);
      }
    }
    final boolean previous = Prefs.blackBackground;
    try {
      Prefs.blackBackground = blackBackground;
      final ByteProcessor mask = ThreadAnalyser_PlugIn.getMask(ip);
      Assertions.assertTrue(mask.isBinary(), "Mask should be binary");
      // The bar is skeletonised to a single line
      final SkeletonAnalyser_PlugIn sa = new SkeletonAnalyser_PlugIn();
      Assertions.assertTrue(sa.skeletonise(mask, true));
      final byte[] map = sa.findNodes(mask);
      Assertions.assertNotNull(map);
      final List<float[]> lines = sa.extractLines(map);
      Assertions.assertEquals(1, lines.size(), "Number of lines");
      // The line is within the bar (not around the bar)
      final float[] line = lines.get(0);
      Assertions.assertTrue(line[1] >= 9 && line[1] <= 11, () -> "Start y " + line[1]);
      Assertions.assertTrue(line[3] >= 9 && line[3] <= 11, () -> "End y " + line[3]);
      Assertions.assertTrue(line[4] < 32, () -> "Length " + line[4]);
    } finally {
      Prefs.blackBackground = previous;
    }
  }
}
