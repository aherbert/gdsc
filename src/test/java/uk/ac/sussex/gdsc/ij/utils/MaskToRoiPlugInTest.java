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

package uk.ac.sussex.gdsc.ij.utils;

import ij.gui.Roi;
import ij.process.ByteProcessor;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"javadoc"})
class MaskToRoiPlugInTest {
  private static List<Roi> trace(ByteProcessor bp, boolean eightConnected) {
    final List<Roi> rois = new ArrayList<>();
    MaskToRoi_PlugIn.traceObjects(bp, eightConnected, rois::add);
    return rois;
  }

  private static void fill(ByteProcessor bp, int value, int x, int y, int w, int h) {
    bp.setValue(value);
    bp.setRoi(x, y, w, h);
    bp.fill();
    bp.resetRoi();
  }

  @Test
  void testBinaryMask() {
    // Objects with the same value
    final ByteProcessor bp = new ByteProcessor(40, 20);
    fill(bp, 255, 2, 2, 5, 5);
    fill(bp, 255, 20, 2, 5, 6);
    final List<Roi> rois = trace(bp, true);
    Assertions.assertEquals(2, rois.size());
    Assertions.assertEquals(new Rectangle(2, 2, 5, 5), rois.get(0).getBounds());
    Assertions.assertEquals(new Rectangle(20, 2, 5, 6), rois.get(1).getBounds());
  }

  @Test
  void testLabelledMask() {
    // Touching objects with different values
    final ByteProcessor bp = new ByteProcessor(40, 20);
    fill(bp, 1, 2, 2, 5, 5);
    fill(bp, 2, 7, 2, 5, 5);
    final List<Roi> rois = trace(bp, true);
    Assertions.assertEquals(2, rois.size());
    Assertions.assertEquals(new Rectangle(2, 2, 5, 5), rois.get(0).getBounds());
    Assertions.assertEquals(new Rectangle(7, 2, 5, 5), rois.get(1).getBounds());
  }

  @Test
  void testConnectivity() {
    // Two pixels touching diagonally
    final ByteProcessor bp = new ByteProcessor(10, 10);
    bp.set(3, 3, 255);
    bp.set(4, 4, 255);
    Assertions.assertEquals(1, trace(bp, true).size());
    Assertions.assertEquals(2, trace(bp, false).size());
  }

  @Test
  void testObjectWithHole() {
    // A ring with a separate object of the same value in the hole
    final ByteProcessor bp = new ByteProcessor(20, 20);
    fill(bp, 255, 2, 2, 11, 11);
    fill(bp, 0, 4, 4, 7, 7);
    fill(bp, 255, 6, 6, 3, 3);
    final List<Roi> rois = trace(bp, true);
    Assertions.assertEquals(2, rois.size());
    Assertions.assertEquals(new Rectangle(2, 2, 11, 11), rois.get(0).getBounds());
    Assertions.assertEquals(new Rectangle(6, 6, 3, 3), rois.get(1).getBounds());
  }
}
