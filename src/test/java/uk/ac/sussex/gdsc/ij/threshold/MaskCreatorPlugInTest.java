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

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import java.util.BitSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"javadoc"})
class MaskCreatorPlugInTest {
  @Test
  void testAssignObjectsAboveLimit() {
    // A grid of single pixel objects: 300 x 300 = 90000 objects
    final ByteProcessor bp = new ByteProcessor(600, 600);
    for (int y = 0; y < 600; y += 2) {
      for (int x = 0; x < 600; x += 2) {
        bp.set(x, y, 255);
      }
    }
    final MaskCreator_PlugIn mc =
        new MaskCreator_PlugIn(new ImagePlus("objects", bp), MaskCreator_PlugIn.OPTION_MASK);
    mc.setAssignObjects(true);
    final ImageProcessor mask = mc.createMask().getProcessor();

    // Each remaining object has a unique value up to the 16-bit limit
    final BitSet values = new BitSet();
    int count = 0;
    for (int i = 0; i < mask.getPixelCount(); i++) {
      final int value = mask.get(i);
      if (value != 0) {
        count++;
        Assertions.assertFalse(values.get(value), () -> "Duplicate object value: " + value);
        values.set(value);
      }
    }
    Assertions.assertEquals(65535, count);
  }
}
