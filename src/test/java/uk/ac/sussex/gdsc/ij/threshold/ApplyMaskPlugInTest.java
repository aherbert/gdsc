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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"javadoc"})
class ApplyMaskPlugInTest {
  @Test
  void testApplyMaskUsesSettings() {
    // Source with values 0 to 99 and a minimum display value of 50
    final ByteProcessor source = new ByteProcessor(10, 10);
    for (int i = 0; i < 100; i++) {
      source.set(i, i);
    }
    final ImagePlus sourceImp = new ImagePlus("source", source);
    sourceImp.setDisplayRange(50, 99);

    final ByteProcessor target = new ByteProcessor(10, 10);
    target.setValue(200);
    target.fill();
    final ImagePlus targetImp = new ImagePlus("target", target);

    final ApplyMask_PlugIn plugin =
        new ApplyMask_PlugIn(targetImp, MaskCreator_PlugIn.OPTION_MIN_VALUE);
    plugin.setMaskImp(sourceImp);
    plugin.applyMask();

    // Pixels below the minimum display value are outside the mask
    for (int i = 0; i < 100; i++) {
      Assertions.assertEquals(i >= 50 ? 200 : 0, target.get(i), "Pixel " + i);
    }
  }
}
