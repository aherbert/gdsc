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

import ij.ImagePlus;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import java.awt.Rectangle;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.sussex.gdsc.core.ij.AlignImagesFft;
import uk.ac.sussex.gdsc.core.ij.AlignImagesFft.SubPixelMethod;
import uk.ac.sussex.gdsc.core.utils.ImageWindow.WindowMethod;

@SuppressWarnings({"javadoc"})
class AlignImagesFftPlugInTest {
  @Test
  void testCreateBoundsIncludesMaxShift() {
    Assertions.assertEquals(new Rectangle(-2, -1, 5, 3),
        AlignImagesFft_PlugIn.createBounds(-2, 2, -1, 1));
    // A single translation
    Assertions.assertEquals(new Rectangle(3, -4, 1, 1),
        AlignImagesFft_PlugIn.createBounds(3, 3, -4, -4));
    Assertions.assertEquals(AlignImagesFft_PlugIn.createBounds(-2, 2, -1, 1),
        AlignStacks_PlugIn.createBounds(-2, 2, -1, 1));
  }

  @ParameterizedTest
  @ValueSource(ints = {-3, 3})
  void testAlignAtTranslationLimit(int shift) {
    // Smooth random image
    final SplittableRandom rng = new SplittableRandom(12345);
    final FloatProcessor ref = new FloatProcessor(64, 64);
    for (int i = 0; i < ref.getPixelCount(); i++) {
      ref.setf(i, (float) rng.nextDouble());
    }
    ref.blurGaussian(2);
    final ImageProcessor target = ref.duplicate();
    target.translate(shift, 0);

    // The translation to align the target is the negative of the shift and is at the
    // limit of the range
    final Rectangle bounds = AlignImagesFft_PlugIn.createBounds(-3, 3, -3, 3);
    final AlignImagesFft align = new AlignImagesFft();
    align.align(new ImagePlus("ref", ref), new ImagePlus("target", target), WindowMethod.NONE,
        bounds, SubPixelMethod.NONE, ImageProcessor.NONE, true, null, null, null, false);
    Assertions.assertEquals(-shift, align.getLastXOffset());
    Assertions.assertEquals(0, align.getLastYOffset());
  }
}
