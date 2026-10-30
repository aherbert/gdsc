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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"javadoc"})
class IntensityAnalysisPlugInTest {
  private static float[] getExposures(String... labels) {
    final int prefix = IntensityAnalysis_PlugIn.getCommonPrefixLength(labels);
    final float[] exposures = new float[labels.length];
    for (int i = 0; i < labels.length; i++) {
      exposures[i] = IntensityAnalysis_PlugIn.getExposure(labels[i], prefix);
    }
    return exposures;
  }

  @Test
  void testGetExposures() {
    Assertions.assertArrayEquals(new float[] {10, 20, 40},
        getExposures("image_10ms", "image_20ms", "image_40ms"));
    // Exposures with a common leading digit
    Assertions.assertArrayEquals(new float[] {100, 150},
        getExposures("img_100ms", "img_150ms"));
    Assertions.assertArrayEquals(new float[] {1.5f, 1.25f},
        getExposures("img_1.5ms", "img_1.25ms"));
    // A number in the prefix
    Assertions.assertArrayEquals(new float[] {100, 150},
        getExposures("img2_100ms", "img2_150ms"));
    // Labels of different lengths
    Assertions.assertArrayEquals(new float[] {10, 1}, getExposures("exp10", "exp1"));
    Assertions.assertArrayEquals(new float[] {1, 10}, getExposures("exp1", "exp10"));
    // No prefix
    Assertions.assertArrayEquals(new float[] {5, 10}, getExposures("5", "10"));
  }

  @Test
  void testGetCommonPrefixLength() {
    Assertions.assertEquals(4, IntensityAnalysis_PlugIn
        .getCommonPrefixLength(new String[] {"img_100ms", "img_150ms"}));
    Assertions.assertEquals(5, IntensityAnalysis_PlugIn
        .getCommonPrefixLength(new String[] {"img2_100ms", "img2_150ms"}));
  }

  @Test
  void testGetExposureThrows() {
    Assertions.assertThrows(NumberFormatException.class,
        () -> IntensityAnalysis_PlugIn.getExposure("no number", 0));
  }
}
