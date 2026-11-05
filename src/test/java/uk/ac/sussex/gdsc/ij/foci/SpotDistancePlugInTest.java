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
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.ac.sussex.gdsc.ij.foci.SpotDistance_PlugIn.DistanceResult;
import uk.ac.sussex.gdsc.ij.foci.SpotDistance_PlugIn.RegionMask;
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

  @Test
  void testGetMaskStack() {
    // Mask with 2 channels, 3 slices and 2 frames. Value = 100 * c + 10 * z + t
    final ImageStack stack = new ImageStack(4, 3);
    for (int t = 1; t <= 2; t++) {
      for (int z = 1; z <= 3; z++) {
        for (int c = 1; c <= 2; c++) {
          final ByteProcessor bp = new ByteProcessor(4, 3);
          bp.set(100 * c + 10 * z + t);
          stack.addSlice(bp);
        }
      }
    }
    final ImagePlus mask = new ImagePlus("mask", stack);
    mask.setDimensions(2, 3, 2);
    for (int c = 1; c <= 2; c++) {
      for (int t = 1; t <= 2; t++) {
        final ImageStack s2 = SpotDistance_PlugIn.getMaskStack(mask, c, t);
        Assertions.assertEquals(3, s2.getSize());
        for (int z = 1; z <= 3; z++) {
          Assertions.assertEquals(100 * c + 10 * z + t, s2.getProcessor(z).get(0));
        }
      }
    }
    // A single channel and frame mask is used for all channels and frames
    final ImagePlus mask2 = new ImagePlus("mask", new ByteProcessor(4, 3));
    mask2.getProcessor().set(7);
    Assertions.assertEquals(7,
        SpotDistance_PlugIn.getMaskStack(mask2, 2, 3).getProcessor(1).get(0));
  }

  @Test
  void testRegionMask() {
    final ShortProcessor sp = new ShortProcessor(10, 8);
    sp.set(1, 2, 3);
    sp.set(2, 3, 3);
    sp.set(6, 5, 5);
    final ImageStack stack = new ImageStack(10, 8);
    stack.addSlice(sp);
    final RegionMask mask = new RegionMask(stack);
    Assertions.assertArrayEquals(new int[] {3, 5}, mask.regions);
    Assertions.assertEquals(new Rectangle(1, 2, 6, 4), mask.blurBounds);
    final Rectangle bounds = new Rectangle();
    final ImagePlus region = mask.getRegion(3, bounds);
    Assertions.assertEquals(new Rectangle(1, 2, 2, 2), bounds);
    Assertions.assertEquals(2, region.getWidth());
    Assertions.assertEquals(1, region.getProcessor().get(0, 0));
    Assertions.assertEquals(0, region.getProcessor().get(1, 0));
    Assertions.assertEquals(1, region.getProcessor().get(1, 1));
    Assertions.assertNull(mask.getRegion(4, bounds));

    // Single region
    final ShortProcessor sp2 = new ShortProcessor(10, 8);
    sp2.set(6, 5, 5);
    final ImageStack stack2 = new ImageStack(10, 8);
    stack2.addSlice(sp2);
    final RegionMask mask2 = new RegionMask(stack2);
    Assertions.assertArrayEquals(new int[] {5}, mask2.regions);
    Assertions.assertEquals(new Rectangle(6, 5, 1, 1), mask2.blurBounds);
    final Rectangle bounds2 = new Rectangle();
    final ImagePlus region2 = mask2.getRegion(5, bounds2);
    Assertions.assertEquals(new Rectangle(6, 5, 1, 1), bounds2);
    Assertions.assertSame(region2, mask2.getRegion(5, new Rectangle()));
    Assertions.assertNull(mask2.getRegion(3, bounds2));

    // No regions
    final ImageStack stack3 = new ImageStack(10, 8);
    stack3.addSlice(new ShortProcessor(10, 8));
    final RegionMask mask3 = new RegionMask(stack3);
    Assertions.assertArrayEquals(new int[0], mask3.regions);
    Assertions.assertNull(mask3.blurBounds);
  }

  @Test
  void testUnion() {
    Assertions.assertArrayEquals(new int[] {1, 2, 3, 5},
        SpotDistance_PlugIn.union(new int[] {1, 3, 5}, new int[] {2, 3}));
    Assertions.assertArrayEquals(new int[] {2, 3},
        SpotDistance_PlugIn.union(new int[0], new int[] {2, 3}));
  }

  @Test
  void testCrop() {
    final ImageStack stack = new ImageStack(10, 8);
    for (int z = 1; z <= 2; z++) {
      final ShortProcessor sp = new ShortProcessor(10, 8);
      for (int i = 0; i < sp.getPixelCount(); i++) {
        sp.set(i, i + 100 * z);
      }
      stack.addSlice(sp);
    }
    final ImagePlus imp = new ImagePlus(null, stack);
    final ImagePlus cropped = SpotDistance_PlugIn.crop(imp, new Rectangle(2, 3, 4, 5));
    Assertions.assertEquals(4, cropped.getWidth());
    Assertions.assertEquals(5, cropped.getHeight());
    Assertions.assertEquals(2, cropped.getStackSize());
    for (int z = 1; z <= 2; z++) {
      Assertions.assertEquals(3 * 10 + 2 + 100 * z,
          cropped.getImageStack().getProcessor(z).get(0, 0));
    }
    // The source is not changed
    Assertions.assertNull(imp.getRoi());
    Assertions.assertEquals(10, imp.getWidth());
  }
}
