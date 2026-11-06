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

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
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

  @Test
  void testIsNoThreshold() {
    final ByteProcessor bp1 = new ByteProcessor(3, 2);
    bp1.set(0, 255);
    final ByteProcessor bp2 = new ByteProcessor(3, 2);
    bp2.set(1, 255);
    Assertions.assertFalse(MaskParticleAnalyzer.isNoThreshold(new ImagePlus("binary", bp1)));
    final ImageStack stack = new ImageStack(3, 2);
    stack.addSlice(bp1);
    stack.addSlice(bp2);
    Assertions.assertFalse(MaskParticleAnalyzer.isNoThreshold(new ImagePlus("binary", stack)));
    // A label in any slice
    bp2.set(2, 3);
    Assertions.assertTrue(MaskParticleAnalyzer.isNoThreshold(new ImagePlus("labels", stack)));
    final ImagePlus imp = new ImagePlus("labels", stack);
    imp.getProcessor().setThreshold(1, 255, ImageProcessor.NO_LUT_UPDATE);
    Assertions.assertFalse(MaskParticleAnalyzer.isNoThreshold(imp));
    Assertions.assertTrue(
        MaskParticleAnalyzer.isNoThreshold(new ImagePlus("float", new FloatProcessor(3, 2))));
  }

  @Test
  void testSetNonZeroThresholdUsesAllSlices() {
    final ImageStack stack = new ImageStack(3, 2);
    stack.addSlice(new ShortProcessor(3, 2));
    stack.addSlice(new ShortProcessor(3, 2));
    stack.addSlice(new ShortProcessor(3, 2));
    final ImagePlus imp = new ImagePlus("labels", stack);
    final MaskParticleAnalyzer pa = createAnalyzer();
    Assertions.assertFalse(pa.setNonZeroThreshold(imp));
    stack.getProcessor(1).set(0, 4);
    stack.getProcessor(3).set(1, 9);
    stack.getProcessor(3).set(2, 2);
    imp.setSlice(1);
    Assertions.assertTrue(pa.setNonZeroThreshold(imp));
    Assertions.assertEquals(2, imp.getProcessor().getMinThreshold());
    Assertions.assertEquals(9, imp.getProcessor().getMaxThreshold());
  }

  @Test
  void testAnalyzeLabels() {
    // Touching labels; label 1 has two separate particles
    final ShortProcessor sp = new ShortProcessor(5, 3);
    sp.setIntArray(new int[][] {{1, 1, 0}, {1, 2, 0}, {0, 2, 0}, {0, 2, 0}, {1, 0, 0}});
    assertParticles(new ImagePlus("labels", sp), new double[] {1, 1, 2}, new double[] {3, 1, 3});
  }

  @Test
  void testAnalyzeNegativeValues() {
    // The automatic threshold [-3, 7] includes the zero background
    final FloatProcessor fp = new FloatProcessor(6, 3);
    fp.setFloatArray(new float[][] {{-3, -3, 0}, {-3, -3, 0}, {2, 2, 0}, {2, 0, 0}, {0, 0, 0},
        {0, 0, 7}});
    assertParticles(new ImagePlus("negative", fp), new double[] {-3, 2, 7}, new double[] {4, 3, 1});
  }

  private static MaskParticleAnalyzer createAnalyzer() {
    return new MaskParticleAnalyzer(0, Measurements.AREA, new ResultsTable());
  }

  private static void assertParticles(ImagePlus imp, double[] values, double[] pixels) {
    final ResultsTable rt = new ResultsTable();
    final MaskParticleAnalyzer pa = new MaskParticleAnalyzer(0, Measurements.AREA, rt);
    Assertions.assertTrue(MaskParticleAnalyzer.isNoThreshold(imp));
    Assertions.assertTrue(pa.setNonZeroThreshold(imp));
    Assertions.assertTrue(pa.analyze(imp));
    Assertions.assertArrayEquals(values, rt.getColumn("Particle Value"));
    Assertions.assertArrayEquals(pixels, rt.getColumn("Pixels"));
  }
}
