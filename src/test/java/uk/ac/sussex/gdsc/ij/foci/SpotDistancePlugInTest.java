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

import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ShortProcessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.ac.sussex.gdsc.ij.foci.SpotDistance_PlugIn.DistanceResult;
import uk.ac.sussex.gdsc.ij.foci.SpotDistance_PlugIn.Settings;
import uk.ac.sussex.gdsc.ij.foci.SpotDistance_PlugIn.Tracker;

@SuppressWarnings({"javadoc"})
class SpotDistancePlugInTest {
  @Test
  void testTrackerAssignIds() {
    final Tracker tracker = new Tracker();
    final double distance = 100;

    // First frame: IDs are assigned in order
    final List<DistanceResult> frame1 = create(new int[][] {{10, 10}, {30, 30}, {50, 10}});
    tracker.assignIds(frame1, distance);
    assertIds(frame1, new int[][] {{10, 10, 1}, {30, 30, 2}, {50, 10, 3}});

    // Second frame: spots move slightly and are listed in a different order.
    // Spot 2 is lost.
    final List<DistanceResult> frame2 = create(new int[][] {{51, 11}, {11, 9}});
    tracker.assignIds(frame2, distance);
    assertIds(frame2, new int[][] {{11, 9, 1}, {51, 11, 3}});

    // Third frame: a new spot does not reuse the ID of the lost spot
    final List<DistanceResult> frame3 = create(new int[][] {{12, 9}, {52, 12}, {90, 90}});
    tracker.assignIds(frame3, distance);
    assertIds(frame3, new int[][] {{12, 9, 1}, {52, 12, 3}, {90, 90, 4}});
  }

  @Test
  void testTrackerAssignIdsWithEmptyFrame() {
    final Tracker tracker = new Tracker();
    final List<DistanceResult> frame1 = create(new int[][] {{10, 10}});
    tracker.assignIds(frame1, 10);
    final List<DistanceResult> frame2 = create(new int[0][]);
    tracker.assignIds(frame2, 10);
    final List<DistanceResult> frame3 = create(new int[][] {{10, 10}});
    tracker.assignIds(frame3, 10);
    // Nothing to track from the empty frame
    assertIds(frame3, new int[][] {{10, 10, 2}});
  }

  @Test
  void testValidateSettings() {
    final Settings settings = new Settings();
    Assertions.assertNull(SpotDistance_PlugIn.validateSettings(settings));
    settings.featureSize = 0;
    Assertions.assertNull(SpotDistance_PlugIn.validateSettings(settings));
    settings.smoothingSize = 0;
    Assertions.assertNull(SpotDistance_PlugIn.validateSettings(settings));

    assertInvalid(s -> s.smoothingSize = -1);
    assertInvalid(s -> s.featureSize = -1);
    assertInvalid(s -> s.featureSize = s.smoothingSize);
    assertInvalid(s -> s.featureSize = s.smoothingSize * 0.5);
    assertInvalid(s -> s.maxPeaks = 0);
    assertInvalid(s -> s.regionCounter = 0);
    assertInvalid(s -> s.regionCounter = 65536);
    final Settings s2 = new Settings();
    s2.regionCounter = 65535;
    Assertions.assertNull(SpotDistance_PlugIn.validateSettings(s2));
  }

  private static void assertInvalid(java.util.function.Consumer<Settings> action) {
    final Settings settings = new Settings();
    action.accept(settings);
    Assertions.assertNotNull(SpotDistance_PlugIn.validateSettings(settings));
  }

  @Test
  void testIsValidDualChannel() {
    Assertions.assertTrue(SpotDistance_PlugIn.isValidDualChannel(1, 2, 2));
    Assertions.assertTrue(SpotDistance_PlugIn.isValidDualChannel(3, 1, 3));
    Assertions.assertFalse(SpotDistance_PlugIn.isValidDualChannel(1, 1, 2));
    Assertions.assertFalse(SpotDistance_PlugIn.isValidDualChannel(1, 2, 1));
    Assertions.assertFalse(SpotDistance_PlugIn.isValidDualChannel(0, 2, 2));
  }

  @Test
  void testIsValidMask() {
    final ImagePlus imp = new ImagePlus("image", new ShortProcessor(10, 8));
    Assertions.assertTrue(
        SpotDistance_PlugIn.isValidMask(imp, new ImagePlus("m", new ByteProcessor(10, 8))));
    Assertions.assertTrue(
        SpotDistance_PlugIn.isValidMask(imp, new ImagePlus("m", new ShortProcessor(10, 8))));
    Assertions.assertFalse(
        SpotDistance_PlugIn.isValidMask(imp, new ImagePlus("m", new FloatProcessor(10, 8))));
    Assertions.assertFalse(
        SpotDistance_PlugIn.isValidMask(imp, new ImagePlus("m", new ColorProcessor(10, 8))));
    Assertions.assertFalse(
        SpotDistance_PlugIn.isValidMask(imp, new ImagePlus("m", new ByteProcessor(11, 8))));
    Assertions.assertFalse(
        SpotDistance_PlugIn.isValidMask(imp, new ImagePlus("m", new ByteProcessor(10, 9))));
  }

  @Test
  void testLabelRegions() {
    final ShortProcessor ip = createTwoRegions();
    Assertions.assertEquals(65536, new SpotDistance_PlugIn().labelRegions(ip, 65534));
    Assertions.assertEquals(65534, ip.get(1, 1));
    Assertions.assertEquals(65535, ip.get(6, 6));
    // The second region would be 65536
    Assertions.assertEquals(-1, new SpotDistance_PlugIn().labelRegions(createTwoRegions(), 65535));
  }

  private static ShortProcessor createTwoRegions() {
    final ShortProcessor ip = new ShortProcessor(10, 10);
    ip.set(1, 1, 1);
    ip.set(6, 6, 1);
    return ip;
  }

  @Test
  void testScaleImageWithNegativeValues() {
    final ImageStack stack = new ImageStack(3, 1);
    stack.addSlice(new FloatProcessor(3, 1, new float[] {-4, -3, -2}));
    final float scale = SpotDistance_PlugIn.scaleImage(stack, false);
    Assertions.assertEquals(65535f / 2, scale);
    final ShortProcessor ip = (ShortProcessor) stack.getProcessor(1);
    Assertions.assertEquals(0, ip.get(0));
    Assertions.assertEquals(32768, ip.get(1));
    Assertions.assertEquals(65535, ip.get(2));
  }

  private static List<DistanceResult> create(int[][] xy) {
    final List<DistanceResult> list = new ArrayList<>();
    for (final int[] p : xy) {
      list.add(new DistanceResult(p[0], p[1], 0, 1));
    }
    return list;
  }

  private static void assertIds(List<DistanceResult> results, int[][] expected) {
    final int[][] actual = results.stream().map(r -> new int[] {r.getXint(), r.getYint(), r.id})
        .toArray(int[][]::new);
    Assertions.assertArrayEquals(expected, actual, () -> Arrays.deepToString(actual));
  }

  @Test
  void testGetUnitConversion() {
    Assertions.assertEquals(1, SpotDistance_PlugIn.getUnitConversion("pixel", "pixel"));
    Assertions.assertEquals(1, SpotDistance_PlugIn.getUnitConversion("Micron", "micron"));
    Assertions.assertEquals(1, SpotDistance_PlugIn.getUnitConversion("micron", "\u00b5m"));
    Assertions.assertEquals(1, SpotDistance_PlugIn.getUnitConversion("um", "microns"));
    Assertions.assertEquals(1e-3, SpotDistance_PlugIn.getUnitConversion("nm", "\u00b5m"), 1e-15);
    Assertions.assertEquals(1e3, SpotDistance_PlugIn.getUnitConversion("mm", "micron"), 1e-9);
    Assertions.assertEquals(1e-6, SpotDistance_PlugIn.getUnitConversion("nm", "mm"), 1e-18);
    Assertions.assertEquals(Double.NaN, SpotDistance_PlugIn.getUnitConversion("pixel", "nm"));
    Assertions.assertEquals(Double.NaN, SpotDistance_PlugIn.getUnitConversion("nm", "sec"));
  }

  @Test
  void testGetCalibratedScale() {
    final Calibration cal = new Calibration();
    Assertions.assertArrayEquals(new double[] {1, 1, 1},
        SpotDistance_PlugIn.getCalibratedScale(cal));
    cal.pixelWidth = 0.1;
    cal.pixelHeight = 0.2;
    cal.pixelDepth = 300;
    cal.setXUnit("\u00b5m");
    cal.setYUnit("micron");
    cal.setZUnit("nm");
    final double[] scale = SpotDistance_PlugIn.getCalibratedScale(cal);
    Assertions.assertArrayEquals(new double[] {0.1, 0.2, 0.3}, scale, 1e-12);
    Assertions.assertArrayEquals(new double[] {1, 2, 3}, SpotDistance_PlugIn.getFilterScale(scale),
        1e-12);
    // Units that cannot be converted
    cal.setZUnit("slice");
    Assertions.assertNull(SpotDistance_PlugIn.getCalibratedScale(cal));
    Assertions.assertArrayEquals(new double[] {1, 1, 1}, SpotDistance_PlugIn.getFilterScale(null));
  }

  @Test
  void testDistance2() {
    final double[] diff = {3, 4, 2};
    Assertions.assertEquals(29, SpotDistance_PlugIn.distance2(diff, new double[] {1, 1, 1}));
    // Pixel width 0.1, height 0.1, depth 0.3 (relative z = 3)
    final double[] scale = SpotDistance_PlugIn
        .getFilterScale(new double[] {0.1, 0.1, 0.3});
    // The z difference of 2 slices is 6 pixels
    Assertions.assertEquals(9 + 16 + 36, SpotDistance_PlugIn.distance2(diff, scale), 1e-9);
    // The diff is not modified
    Assertions.assertArrayEquals(new double[] {3, 4, 2}, diff);
  }
}
