/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2020 Alex Herbert
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

package uk.ac.sussex.gdsc.foci;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.process.ByteProcessor;
import ij.process.FloatPolygon;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.rng.UniformRandomProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.ac.sussex.gdsc.core.match.BasePoint;
import uk.ac.sussex.gdsc.test.junit5.RandomSeed;
import uk.ac.sussex.gdsc.test.junit5.SeededTest;
import uk.ac.sussex.gdsc.test.rng.RngUtils;

@SuppressWarnings({"javadoc"})
class AssignedPointUtilsTest {

  @SeededTest
  void canSaveAndLoadXyz(RandomSeed seed) throws IOException {
    final Path filename = Files.createTempFile("AssignedPointUtilsTest", ".dat");
    AssignedPoint[] in = AssignedPointUtils.loadPoints(filename.toString());
    Assertions.assertArrayEquals(new AssignedPoint[0], in);

    // Handle null
    AssignedPointUtils.savePoints(null, filename.toString());

    final UniformRandomProvider rng = RngUtils.create(seed.getSeed());
    final int width = 256;
    final int height = 128;
    final int depth = 5;
    final AssignedPoint[] out = new AssignedPoint[] {
        // Input IDs are a natural order from 1
        new AssignedPoint(rng.nextInt(width), rng.nextInt(height), rng.nextInt(depth) + 1, 1),
        new AssignedPoint(rng.nextInt(width), rng.nextInt(height), rng.nextInt(depth) + 1, 2),
        new AssignedPoint(rng.nextInt(width), rng.nextInt(height), rng.nextInt(depth) + 1, 3),
        new AssignedPoint(rng.nextInt(width), rng.nextInt(height), rng.nextInt(depth) + 1, 4),
        new AssignedPoint(rng.nextInt(width), rng.nextInt(height), rng.nextInt(depth) + 1, 5)};

    AssignedPointUtils.savePoints(out, filename.toString());
    in = AssignedPointUtils.loadPoints(filename.toString());
    Assertions.assertArrayEquals(out, in);

    // Exercise the error logger
    try (final BufferedWriter tmp = Files.newBufferedWriter(filename)) {
      tmp.write("X,Y,Z");
      tmp.newLine();
      // Not enough tokens
      tmp.write("1,2");
      tmp.newLine();
      // Invalid numbers
      tmp.write("1,2,a");
      tmp.newLine();
    }

    // Should not throw. It will log an exception
    in = AssignedPointUtils.loadPoints(filename.toString());
    Assertions.assertArrayEquals(new AssignedPoint[0], in);

    Files.delete(filename);
  }

  @SeededTest
  void canExtractRoiPoints(RandomSeed seed) {
    Assertions.assertArrayEquals(new AssignedPoint[0],
        AssignedPointUtils.extractRoiPoints((Roi) null));
    Assertions.assertArrayEquals(new AssignedPoint[0],
        AssignedPointUtils.extractRoiPoints(new Roi(0, 0, 10, 15)));
    Assertions.assertArrayEquals(new AssignedPoint[0], AssignedPointUtils
        .extractRoiPoints(new PolygonRoi(new int[10], new int[10], 10, Roi.POLYGON)));

    final UniformRandomProvider rng = RngUtils.create(seed.getSeed());
    final int[] x = new int[10];
    final int[] y = new int[x.length];
    for (int i = 0; i < x.length; i++) {
      x[i] = rng.nextInt(256);
      y[i] = rng.nextInt(256);
    }
    final Roi roi = new PolygonRoi(x, y, x.length, Roi.POINT);
    AssignedPoint[] points = AssignedPointUtils.extractRoiPoints(roi);
    Assertions.assertEquals(x.length, points.length);
    for (int i = 0; i < x.length; i++) {
      Assertions.assertEquals(x[i], points[i].x);
      Assertions.assertEquals(y[i], points[i].y);
      Assertions.assertEquals(0, points[i].z);
    }
    final int c = 3;
    final int z = 4;
    final int t = 2;
    roi.setPosition(c, z, t);
    points = AssignedPointUtils.extractRoiPoints(roi);
    Assertions.assertEquals(x.length, points.length);
    for (int i = 0; i < x.length; i++) {
      Assertions.assertEquals(x[i], points[i].x);
      Assertions.assertEquals(y[i], points[i].y);
      Assertions.assertEquals(z, points[i].z);
      Assertions.assertEquals(c, points[i].getChannel());
      Assertions.assertEquals(t, points[i].getFrame());
    }
    final int pos = 43;
    roi.setPosition(pos);
    points = AssignedPointUtils.extractRoiPoints(roi);
    Assertions.assertEquals(x.length, points.length);
    for (int i = 0; i < x.length; i++) {
      Assertions.assertEquals(x[i], points[i].x);
      Assertions.assertEquals(y[i], points[i].y);
      Assertions.assertEquals(pos, points[i].z);
      Assertions.assertEquals(0, points[i].getChannel());
      Assertions.assertEquals(0, points[i].getFrame());
    }
  }

  @SeededTest
  void canExtractRoiPointsFromImage(RandomSeed seed) {
    Assertions.assertArrayEquals(new AssignedPoint[0],
        AssignedPointUtils.extractRoiPoints((ImagePlus) null));
    ImagePlus imp = new ImagePlus(null, new ByteProcessor(10, 15));
    Assertions.assertArrayEquals(new AssignedPoint[0], AssignedPointUtils.extractRoiPoints(imp));
    imp.setRoi(3, 4, 5, 6);
    Assertions.assertArrayEquals(new AssignedPoint[0], AssignedPointUtils.extractRoiPoints(imp));
    imp.setRoi(new PolygonRoi(new int[] {1, 2, 1}, new int[] {1, 1, 2}, 3, Roi.POLYGON));
    Assertions.assertArrayEquals(new AssignedPoint[0], AssignedPointUtils.extractRoiPoints(imp));

    final UniformRandomProvider rng = RngUtils.create(seed.getSeed());
    final int[] x = new int[10];
    final int[] y = new int[x.length];
    for (int i = 0; i < x.length; i++) {
      x[i] = rng.nextInt(256);
      y[i] = rng.nextInt(256);
    }
    final Roi roi = new PolygonRoi(x, y, x.length, Roi.POINT);
    imp.setRoi(roi);
    AssignedPoint[] points = AssignedPointUtils.extractRoiPoints(imp);
    Assertions.assertEquals(x.length, points.length);
    for (int i = 0; i < x.length; i++) {
      Assertions.assertEquals(x[i], points[i].x);
      Assertions.assertEquals(y[i], points[i].y);
      Assertions.assertEquals(0, points[i].z);
      Assertions.assertEquals(0, points[i].getChannel());
      Assertions.assertEquals(0, points[i].getFrame());
    }

    imp = IJ.createImage(null, "8-bit", 10, 15, 4, 3, 2);
    final int c = 3;
    final int z = 2;
    final int t = 1;
    roi.setPosition(c, z, t);
    imp.setRoi(roi);
    points = AssignedPointUtils.extractRoiPoints(imp);
    Assertions.assertEquals(x.length, points.length);
    for (int i = 0; i < x.length; i++) {
      Assertions.assertEquals(x[i], points[i].x);
      Assertions.assertEquals(y[i], points[i].y);
      Assertions.assertEquals(z, points[i].z);
      Assertions.assertEquals(c, points[i].getChannel());
      Assertions.assertEquals(t, points[i].getFrame());
    }

    // Set into the ROI in bulk
    int[] cpositions = new int[imp.getStackSize()];
    int[] zpositions = new int[imp.getStackSize()];
    int[] tpositions = new int[imp.getStackSize()];
    int[] counters = new int[zpositions.length];
    for (int i = 0; i < zpositions.length; i++) {
      final int channel = 1 + rng.nextInt(imp.getNChannels());
      final int slice = 1 + rng.nextInt(imp.getNSlices());
      final int frame = 1 + rng.nextInt(imp.getNFrames());
      cpositions[i] = channel;
      zpositions[i] = slice;
      tpositions[i] = frame;
      counters[i] = imp.getStackIndex(channel, slice, frame) << 8;
    }
    PointRoi roi2 = new PointRoi(x, y, x.length);
    roi2.setCounters(counters);
    imp.setRoi(roi2);
    points = AssignedPointUtils.extractRoiPoints(imp);
    Assertions.assertEquals(x.length, points.length);
    for (int i = 0; i < x.length; i++) {
      Assertions.assertEquals(x[i], points[i].x);
      Assertions.assertEquals(y[i], points[i].y);
      Assertions.assertEquals(zpositions[i], points[i].z);
      Assertions.assertEquals(cpositions[i], points[i].getChannel());
      Assertions.assertEquals(tpositions[i], points[i].getFrame());
    }

    imp = IJ.createImage(null, "8-bit", 10, 15, 21);
    roi2 = new PointRoi(x, y, x.length);
    final int pos = 13;
    roi2.setPosition(pos);
    imp.setRoi(roi2);
    points = AssignedPointUtils.extractRoiPoints(imp);
    Assertions.assertEquals(x.length, points.length);
    for (int i = 0; i < x.length; i++) {
      Assertions.assertEquals(x[i], points[i].x);
      Assertions.assertEquals(y[i], points[i].y);
      Assertions.assertEquals(pos, points[i].z);
      Assertions.assertEquals(0, points[i].getChannel());
      Assertions.assertEquals(0, points[i].getFrame());
    }

    // Set into the ROI in bulk
    zpositions = new int[imp.getStackSize()];
    for (int i = 0; i < zpositions.length; i++) {
      zpositions[i] = 1 + rng.nextInt(zpositions.length);
    }
    counters = new int[zpositions.length];
    for (int i = 0; i < zpositions.length; i++) {
      counters[i] = zpositions[i] << 8;
    }
    roi2.setCounters(counters);
    imp.setRoi(roi2);
    points = AssignedPointUtils.extractRoiPoints(imp);
    Assertions.assertEquals(x.length, points.length);
    for (int i = 0; i < x.length; i++) {
      Assertions.assertEquals(x[i], points[i].x);
      Assertions.assertEquals(y[i], points[i].y);
      Assertions.assertEquals(zpositions[i], points[i].z);
      // PointRoi positions are converted to 1-indexed channel and frame for a z-stack
      Assertions.assertEquals(1, points[i].getChannel());
      Assertions.assertEquals(1, points[i].getFrame());
    }
  }

  @SeededTest
  void canCreateRoiFromCoordinates(RandomSeed seed) {
    final UniformRandomProvider rng = RngUtils.create(seed.getSeed());

    final int width = 256;
    final int height = 128;
    final int depth = 5;
    final List<BasePoint> list = Arrays.asList(
        new BasePoint(rng.nextFloat() * width, rng.nextFloat() * height, rng.nextInt(depth) + 1),
        new BasePoint(rng.nextFloat() * width, rng.nextFloat() * height, rng.nextInt(depth) + 1),
        new BasePoint(rng.nextFloat() * width, rng.nextFloat() * height, rng.nextInt(depth) + 1),
        new BasePoint(rng.nextFloat() * width, rng.nextFloat() * height, rng.nextInt(depth) + 1),
        new BasePoint(rng.nextFloat() * width, rng.nextFloat() * height, rng.nextInt(depth) + 1));
    ImagePlus imp = IJ.createImage(null, width, height, 1, 8);
    PointRoi roi = (PointRoi) AssignedPointUtils.createRoi(imp, list);
    FloatPolygon poly = roi.getNonSplineFloatPolygon();
    Assertions.assertEquals(list.size(), poly.npoints);
    for (int i = 0; i < poly.npoints; i++) {
      final BasePoint p = list.get(i);
      Assertions.assertEquals(p.getX(), poly.xpoints[i]);
      Assertions.assertEquals(p.getY(), poly.ypoints[i]);
      Assertions.assertEquals(0, roi.getPointPosition(i));
    }

    // With stack
    imp = IJ.createImage(null, width, height, depth, 8);
    imp.setDimensions(1, depth, 1);
    roi = (PointRoi) AssignedPointUtils.createRoi(imp, list);
    poly = roi.getNonSplineFloatPolygon();
    Assertions.assertEquals(list.size(), poly.npoints);
    for (int i = 0; i < poly.npoints; i++) {
      final BasePoint p = list.get(i);
      Assertions.assertEquals(p.getX(), poly.xpoints[i]);
      Assertions.assertEquals(p.getY(), poly.ypoints[i]);
      Assertions.assertEquals(p.getZint(), roi.getPointPosition(i));
    }

    // With hyperstack
    final int channels = 4;
    final int frames = 3;
    final int channel = 1 + rng.nextInt(channels);
    final int frame = 1 + rng.nextInt(frames);
    imp = IJ.createImage(null, "8-bit", width, height, channels, depth, frames);
    imp.setDimensions(channels, depth, frames);
    imp.setPosition(channel, 1, frame);
    roi = (PointRoi) AssignedPointUtils.createRoi(imp, list);
    poly = roi.getNonSplineFloatPolygon();
    Assertions.assertEquals(list.size(), poly.npoints);
    for (int i = 0; i < poly.npoints; i++) {
      final BasePoint p = list.get(i);
      Assertions.assertEquals(p.getX(), poly.xpoints[i]);
      Assertions.assertEquals(p.getY(), poly.ypoints[i]);
      Assertions.assertEquals(imp.getStackIndex(channel, p.getZint(), frame),
          roi.getPointPosition(i));
    }
  }

  @SeededTest
  void canCreateRoiFromAssignedPoint(RandomSeed seed) {
    final UniformRandomProvider rng = RngUtils.create(seed.getSeed());

    final int width = 256;
    final int height = 128;
    final int depth = 5;
    final int channels = 4;
    final int frames = 3;
    final AssignedPoint[] array = new AssignedPoint[] {
        new AssignedPoint(rng.nextInt(width), rng.nextInt(height), rng.nextInt(depth) + 1, 0),
        new AssignedPoint(rng.nextInt(width), rng.nextInt(height), rng.nextInt(depth) + 1, 1),
        new AssignedPoint(rng.nextInt(width), rng.nextInt(height), rng.nextInt(depth) + 1, 2),
        new AssignedPoint(rng.nextInt(width), rng.nextInt(height), rng.nextInt(depth) + 1, 3),
        new AssignedPoint(rng.nextInt(width), rng.nextInt(height), rng.nextInt(depth) + 1, 4)};
    final List<AssignedPoint> list = Arrays.asList(array);
    ImagePlus imp = IJ.createImage(null, width, height, 1, 8);
    PointRoi roi = (PointRoi) AssignedPointUtils.createRoi(imp, array);
    FloatPolygon poly = roi.getNonSplineFloatPolygon();
    Assertions.assertEquals(list.size(), poly.npoints);
    for (int i = 0; i < poly.npoints; i++) {
      final AssignedPoint p = list.get(i);
      Assertions.assertEquals(p.getX(), poly.xpoints[i]);
      Assertions.assertEquals(p.getY(), poly.ypoints[i]);
      Assertions.assertEquals(0, roi.getPointPosition(i));
    }

    // With stack
    imp = IJ.createImage(null, width, height, depth, 8);
    imp.setDimensions(1, depth, 1);
    roi = (PointRoi) AssignedPointUtils.createRoi(imp, array);
    poly = roi.getNonSplineFloatPolygon();
    Assertions.assertEquals(list.size(), poly.npoints);
    for (int i = 0; i < poly.npoints; i++) {
      final AssignedPoint p = list.get(i);
      Assertions.assertEquals(p.getX(), poly.xpoints[i]);
      Assertions.assertEquals(p.getY(), poly.ypoints[i]);
      Assertions.assertEquals(p.getZint(), roi.getPointPosition(i));
    }

    // With hyperstack
    final int channel = 1 + rng.nextInt(channels);
    final int frame = 1 + rng.nextInt(frames);
    imp = IJ.createImage(null, "8-bit", width, height, channels, depth, frames);
    imp.setDimensions(channels, depth, frames);
    // This position should be ignored
    imp.setPosition(channel, 1, frame);
    // Positions will be taken from the assigned point
    for (final AssignedPoint p : array) {
      p.setChannel(rng.nextInt(channels) + 1);
      p.setFrame(rng.nextInt(frames) + 1);
    }
    roi = (PointRoi) AssignedPointUtils.createRoi(imp, array);
    poly = roi.getNonSplineFloatPolygon();
    Assertions.assertEquals(list.size(), poly.npoints);
    for (int i = 0; i < poly.npoints; i++) {
      final AssignedPoint p = list.get(i);
      Assertions.assertEquals(p.getX(), poly.xpoints[i]);
      Assertions.assertEquals(p.getY(), poly.ypoints[i]);
      Assertions.assertEquals(imp.getStackIndex(p.getChannel(), p.getZint(), p.getFrame()),
          roi.getPointPosition(i));
    }
  }

  @Test
  void canEliminateDuplicates() {
    Assertions.assertArrayEquals(new AssignedPoint[0],
        AssignedPointUtils.eliminateDuplicates(new AssignedPoint[0]));

    final AssignedPoint[] actual = AssignedPointUtils.eliminateDuplicates(new AssignedPoint[] {
        new AssignedPoint(2, 2, 3, 1), new AssignedPoint(1, 2, 3, 2), new AssignedPoint(1, 2, 3, 3),
        new AssignedPoint(1, 1, 3, 4), new AssignedPoint(1, 1, 1, 5), new AssignedPoint(1, 1, 1, 6),
        new AssignedPoint(1, 2, 1, 7), new AssignedPoint(2, 1, 1, 8),});
    final AssignedPoint[] expected = new AssignedPoint[] {new AssignedPoint(1, 1, 1, 0),
        new AssignedPoint(1, 2, 1, 1), new AssignedPoint(2, 1, 1, 2), new AssignedPoint(1, 1, 3, 3),
        new AssignedPoint(1, 2, 3, 4), new AssignedPoint(2, 2, 3, 5),};
    Assertions.assertArrayEquals(expected, actual);
  }

  @Test
  void canIncrementZ() {
    final AssignedPoint[] points =
        new AssignedPoint[] {new AssignedPoint(0, 0, 0, 0), new AssignedPoint(0, 0, 4, 0)};
    AssignedPointUtils.incrementZ(points);
    Assertions.assertEquals(1, points[0].getZ());
    Assertions.assertEquals(5, points[1].getZ());
  }
}
