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

package uk.ac.sussex.gdsc.ij.ext.plugin.filter;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"javadoc"})
class MaskParticleAnalyzerTest {
  @Test
  void testFindNonZeroRange() {
    // Single value
    Assertions.assertArrayEquals(new float[] {1, 1},
        MaskParticleAnalyzer.findNonZeroRange(new float[] {0, 1, 1, 0}));
    // Descending values
    Assertions.assertArrayEquals(new float[] {3, 5},
        MaskParticleAnalyzer.findNonZeroRange(new float[] {0, 5, 3, 0}));
    // The first value is used
    Assertions.assertArrayEquals(new float[] {2, 9},
        MaskParticleAnalyzer.findNonZeroRange(new float[] {9, 2, 4}));
    Assertions.assertArrayEquals(new float[] {1, 4},
        MaskParticleAnalyzer.findNonZeroRange(new float[] {1, 2, 4}));
    // Negative values and NaN
    Assertions.assertArrayEquals(new float[] {-3, 2},
        MaskParticleAnalyzer.findNonZeroRange(new float[] {Float.NaN, 2, 0, -3}));
    // Infinite values
    Assertions.assertArrayEquals(new float[] {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY},
        MaskParticleAnalyzer.findNonZeroRange(new float[] {0, Float.POSITIVE_INFINITY}));
    Assertions.assertArrayEquals(new float[] {Float.NEGATIVE_INFINITY, 1},
        MaskParticleAnalyzer.findNonZeroRange(new float[] {Float.NEGATIVE_INFINITY, 1}));
    // No values
    Assertions.assertNull(
        MaskParticleAnalyzer.findNonZeroRange(new float[] {0, -0.0f, 0, Float.NaN}));
    Assertions.assertNull(MaskParticleAnalyzer.findNonZeroRange(new float[0]));
  }
}
