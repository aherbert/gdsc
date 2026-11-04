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
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.ac.sussex.gdsc.core.math.hull.Hull2d;
import uk.ac.sussex.gdsc.core.math.hull.Hull3d;
import uk.ac.sussex.gdsc.ij.foci.ObjectFociDepth_PlugIn.Distance;
import uk.ac.sussex.gdsc.ij.foci.ObjectFociDepth_PlugIn.Distance2D;
import uk.ac.sussex.gdsc.ij.foci.ObjectFociDepth_PlugIn.Distance3D;
import uk.ac.sussex.gdsc.ij.foci.ObjectFociDepth_PlugIn.Settings;

@SuppressWarnings({"javadoc"})
class ObjectFociDepthPlugInTest {
  @Test
  void testSettingsCopySourceImage() {
    final Settings settings = new Settings();
    settings.sourceImage = "image";
    Assertions.assertEquals("image", settings.copy().sourceImage);
  }

  @Test
  void testCheckZ() {
    // ROI points on a single slice image have z = 0
    final List<AssignedPoint> points = Collections.singletonList(new AssignedPoint(1, 2, 0, 1));
    Assertions.assertTrue(ObjectFociDepth_PlugIn.checkZ(1, true, points));
    Assertions.assertTrue(ObjectFociDepth_PlugIn.checkZ(3, false, points));
    Assertions.assertFalse(ObjectFociDepth_PlugIn.checkZ(3, true, points));
    Assertions.assertTrue(ObjectFociDepth_PlugIn.checkZ(3, true,
        Collections.singletonList(new AssignedPoint(1, 2, 3, 1))));
  }

  @Test
  void testCreateHulls2d() {
    final ByteProcessor ip = new ByteProcessor(10, 8);
    ip.setValue(1);
    ip.setRoi(2, 1, 5, 4);
    ip.fill();
    final ObjectAnalyzer oa = new ObjectAnalyzer(ip);
    final Hull2d[] hulls = ObjectFociDepth_PlugIn.createHulls(oa, i -> new double[] {i % 10, i / 10});
    Assertions.assertEquals(2, hulls.length);
    Assertions.assertNull(hulls[0]);
    // Hull of the pixel coordinates: 4 x 3
    Assertions.assertEquals(12, hulls[1].getArea(), 1e-10);
  }

  @Test
  void testCreateHulls3d() {
    final int maxx = 6;
    final int maxy = 5;
    final int maxz = 4;
    final int[] image = new int[maxx * maxy * maxz];
    for (int z = 1; z < 3; z++) {
      for (int y = 1; y < 4; y++) {
        for (int x = 1; x < 5; x++) {
          image[z * maxx * maxy + y * maxx + x] = 1;
        }
      }
    }
    final ObjectAnalyzer3D oa = new ObjectAnalyzer3D(image, maxx, maxy, maxz);
    final Hull3d[] hulls = ObjectFociDepth_PlugIn.createHulls(oa, i -> new double[] {i % maxx,
        (i % (maxx * maxy)) / maxx, i / (maxx * maxy)});
    Assertions.assertEquals(2, hulls.length);
    Assertions.assertNull(hulls[0]);
    // Hull of the pixel coordinates: 3 x 2 x 1
    Assertions.assertEquals(6, hulls[1].getVolume(), 1e-10);
  }

  @Test
  void testSearch2dHullWithSmallObject() {
    final ByteProcessor ip = new ByteProcessor(10, 8);
    ip.set(3, 3, 1);
    ip.set(4, 3, 1);
    final ObjectAnalyzer oa = new ObjectAnalyzer(ip);
    final IntFunction<double[]> toCoords = i -> new double[] {i % 10, i / 10};
    final Hull2d[] hulls = ObjectFociDepth_PlugIn.createHulls(oa, toCoords);
    Assertions.assertNull(hulls[1]);
    final Distance d = ObjectFociDepth_PlugIn.search2d(new AssignedPoint(3, 3, 1),
        oa.getObjectMask(), 10, 8, toCoords, 1, hulls, new double[hulls.length][][]);
    Assertions.assertEquals(0, d.d2);
  }

  @Test
  void testSearch2dHullOffsetIsInPixels() {
    final int maxx = 12;
    final ByteProcessor ip = new ByteProcessor(maxx, 10);
    ip.setValue(1);
    // Pixels x [2, 8]; y [2, 6]
    ip.setRoi(2, 2, 7, 5);
    ip.fill();
    final ObjectAnalyzer oa = new ObjectAnalyzer(ip);
    // Pixel height is 2 x pixel width
    final double sy = 2;
    final IntFunction<double[]> toCoords = i -> new double[] {i % maxx, (i / maxx) * sy};
    final Hull2d[] hulls = ObjectFociDepth_PlugIn.createHulls(oa, toCoords);
    final Distance d = ObjectFociDepth_PlugIn.search2d(new AssignedPoint(5, 3, 1),
        oa.getObjectMask(), maxx, 10, toCoords, sy, hulls, new double[hulls.length][][]);
    // Closest edge is y=2: 1 pixel; 2 pixel widths
    Assertions.assertEquals(0, d.dx, 1e-10);
    Assertions.assertEquals(-1, d.dy, 1e-10);
    Assertions.assertEquals(4, d.d2, 1e-10);
  }

  @Test
  void testSearch3dHullOffsetIsInPixels() {
    final int maxx = 10;
    final int maxy = 9;
    final int maxz = 8;
    final int[] image = new int[maxx * maxy * maxz];
    // Pixels x [1, 8]; y [1, 7]; z [1, 6]
    for (int z = 1; z <= 6; z++) {
      for (int y = 1; y <= 7; y++) {
        for (int x = 1; x <= 8; x++) {
          image[z * maxx * maxy + y * maxx + x] = 1;
        }
      }
    }
    final ObjectAnalyzer3D oa = new ObjectAnalyzer3D(image, maxx, maxy, maxz);
    final double sy = 1;
    final double sz = 2;
    final IntFunction<double[]> toCoords = i -> new double[] {i % maxx,
        (i % (maxx * maxy)) / maxx * sy, i / (maxx * maxy) * sz};
    final Hull3d[] hulls = ObjectFociDepth_PlugIn.createHulls(oa, toCoords);
    // Point at z index 2 (slice 3): z distance to the edge is 1 pixel; 2 pixel widths.
    // x and y distance is 3.
    final Distance d = ObjectFociDepth_PlugIn.search3d(new AssignedPoint(4, 4, 3, 1),
        oa.getObjectMask(), maxx, maxy, maxz, toCoords, sy, sz, hulls,
        new double[hulls.length][][]);
    Assertions.assertEquals(0, d.dx, 1e-10);
    Assertions.assertEquals(0, d.dy, 1e-10);
    Assertions.assertEquals(-1, d.dz, 1e-10);
    Assertions.assertEquals(4, d.d2, 1e-10);
  }

  @Test
  void testGetPerimetersUsesPixelHeight() {
    final ByteProcessor ip = new ByteProcessor(10, 8);
    ip.setValue(1);
    ip.setRoi(2, 2, 4, 3);
    ip.fill();
    final ImagePlus imp = new ImagePlus("test", ip);
    final Calibration cal = new Calibration();
    cal.pixelWidth = 1.5;
    cal.pixelHeight = 3;
    imp.setCalibration(cal);
    final double[] perimeters =
        ObjectFociDepth_PlugIn.getPerimeters(new ObjectAnalyzer(ip), imp);
    Assertions.assertEquals(2 * (4 * 1.5 + 3 * 3), perimeters[1], 1e-10);
  }

  @Test
  void testSearch2dFindsClosestEdgePixel() {
    final SplittableRandom rng = new SplittableRandom(12345);
    final int maxx = 23;
    final int maxy = 19;
    final double[] scales = {1, 2.25};
    for (int trial = 0; trial < 50; trial++) {
      final ByteProcessor ip = new ByteProcessor(maxx, maxy);
      for (int i = 0; i < ip.getPixelCount(); i++) {
        ip.set(i, rng.nextDouble() < 0.85 ? 1 : 0);
      }
      final int[] mask = new ObjectAnalyzer(ip).getObjectMask();
      for (final double sy : scales) {
        final Distance2D df = (dx, dy) -> (double) dx * dx + dy * dy * sy;
        for (int y = 0; y < maxy; y++) {
          for (int x = 0; x < maxx; x++) {
            final Distance d =
                ObjectFociDepth_PlugIn.search2d(new AssignedPoint(x, y, 1), mask, maxx, maxy, df);
            final int id = mask[y * maxx + x];
            if (id == 0) {
              Assertions.assertNull(d);
              continue;
            }
            final double expected = closestEdge(mask, id, x, y, maxx, maxy, df);
            final int xx = x;
            final int yy = y;
            Assertions.assertEquals(expected, d.d2, () -> String.format("%d,%d", xx, yy));
            // The offset is to an edge pixel at the distance
            Assertions.assertEquals(expected, df.distance((int) d.dx, (int) d.dy));
            Assertions.assertEquals(id, mask[(y + (int) d.dy) * maxx + x + (int) d.dx]);
          }
        }
      }
    }
  }

  @Test
  void testSearch3dFindsClosestEdgePixel() {
    final SplittableRandom rng = new SplittableRandom(678);
    final int maxx = 11;
    final int maxy = 10;
    final int maxz = 7;
    final double[] scales = {1, 2.25};
    for (int trial = 0; trial < 20; trial++) {
      final int[] image = new int[maxx * maxy * maxz];
      for (int i = 0; i < image.length; i++) {
        image[i] = rng.nextDouble() < 0.9 ? 1 : 0;
      }
      final int[] mask = new ObjectAnalyzer3D(image, maxx, maxy, maxz).getObjectMask();
      for (final double sz : scales) {
        final Distance3D df = (dx, dy, dz) -> (double) dx * dx + dy * dy + dz * dz * sz;
        for (int z = 0; z < maxz; z++) {
          for (int y = 0; y < maxy; y++) {
            for (int x = 0; x < maxx; x++) {
              final Distance d = ObjectFociDepth_PlugIn
                  .search3d(new AssignedPoint(x, y, z + 1, 1), mask, maxx, maxy, maxz, df);
              final int id = mask[z * maxx * maxy + y * maxx + x];
              if (id == 0) {
                Assertions.assertNull(d);
                continue;
              }
              final double expected = closestEdge(mask, id, x, y, z, maxx, maxy, maxz, df);
              final int xx = x;
              final int yy = y;
              final int zz = z;
              Assertions.assertEquals(expected, d.d2,
                  () -> String.format("%d,%d,%d", xx, yy, zz));
              Assertions.assertEquals(expected,
                  df.distance((int) d.dx, (int) d.dy, (int) d.dz));
            }
          }
        }
      }
    }
  }

  /**
   * Find the squared distance to the closest edge pixel of the object using a brute force search.
   * An edge pixel is an object pixel with an 8-connected neighbour outside the object or the image.
   */
  private static double closestEdge(int[] mask, int id, int x, int y, int maxx, int maxy,
      Distance2D df) {
    double min = Double.POSITIVE_INFINITY;
    for (int j = 0; j < maxy; j++) {
      for (int i = 0; i < maxx; i++) {
        if (mask[j * maxx + i] == id && isEdge(mask, id, i, j, 0, maxx, maxy, 1, false)) {
          min = Math.min(min, df.distance(i - x, j - y));
        }
      }
    }
    return min;
  }

  private static double closestEdge(int[] mask, int id, int x, int y, int z, int maxx, int maxy,
      int maxz, Distance3D df) {
    double min = Double.POSITIVE_INFINITY;
    for (int k = 0; k < maxz; k++) {
      for (int j = 0; j < maxy; j++) {
        for (int i = 0; i < maxx; i++) {
          if (mask[k * maxx * maxy + j * maxx + i] == id
              && isEdge(mask, id, i, j, k, maxx, maxy, maxz, true)) {
            min = Math.min(min, df.distance(i - x, j - y, k - z));
          }
        }
      }
    }
    return min;
  }

  private static boolean isEdge(int[] mask, int id, int x, int y, int z, int maxx, int maxy,
      int maxz, boolean is3d) {
    final int range = is3d ? 1 : 0;
    for (int k = z - range; k <= z + range; k++) {
      for (int j = y - 1; j <= y + 1; j++) {
        for (int i = x - 1; i <= x + 1; i++) {
          if (i < 0 || j < 0 || k < 0 || i >= maxx || j >= maxy || k >= maxz
              || mask[k * maxx * maxy + j * maxx + i] != id) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
