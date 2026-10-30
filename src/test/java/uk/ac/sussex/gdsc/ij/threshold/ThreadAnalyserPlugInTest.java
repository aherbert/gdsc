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

import ij.gui.Roi;
import java.awt.Polygon;
import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
}
