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

import ij.ImagePlus;
import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.IntUnaryOperator;
import java.util.logging.Logger;
import uk.ac.sussex.gdsc.core.match.Coordinate;
import uk.ac.sussex.gdsc.core.utils.FileUtils;

/**
 * Manages I/O of the {@link AssignedPoint} class.
 */
public final class AssignedPointUtils {
  private static final String NEW_LINE = System.lineSeparator();

  /** No public constructor. */
  private AssignedPointUtils() {}

  /**
   * Save the predicted points to the given file.
   *
   * @param points the points
   * @param filename the filename
   * @throws IOException Signals that an I/O exception has occurred.
   */
  public static void savePoints(AssignedPoint[] points, String filename) throws IOException {
    if (points == null) {
      return;
    }

    final Path path = Paths.get(filename);
    FileUtils.createParent(path);

    try (final BufferedWriter out = Files.newBufferedWriter(path)) {
      // Save results to file
      final StringBuilder sb = new StringBuilder();

      out.write("X,Y,Z");
      out.newLine();

      // Output all results in ascending rank order
      for (final AssignedPoint point : points) {
        sb.append(point.getX()).append(',');
        sb.append(point.getY()).append(',');
        sb.append(point.getZ()).append(NEW_LINE);
        out.write(sb.toString());
        sb.setLength(0);
      }
    }
  }

  /**
   * Loads the points from the file.
   *
   * @param filename the filename
   * @return the assigned points
   * @throws IOException Signals that an I/O exception has occurred.
   */
  public static AssignedPoint[] loadPoints(String filename) throws IOException {
    final LinkedList<AssignedPoint> points = new LinkedList<>();
    // Load results from file
    try (BufferedReader input = Files.newBufferedReader(Paths.get(filename))) {
      String line = input.readLine();

      if (line != null) {
        int lineCount = 1;
        while ((line = input.readLine()) != null) {
          lineCount++;
          final String[] tokens = line.split(",");
          if (tokens.length == 3) {
            try {
              final int x = Integer.parseInt(tokens[0]);
              final int y = Integer.parseInt(tokens[1]);
              final int z = Integer.parseInt(tokens[2]);
              points.add(new AssignedPoint(x, y, z, lineCount - 1));
            } catch (final NumberFormatException ex) {
              final int lineNumber = lineCount;
              Logger.getLogger(AssignedPointUtils.class.getName())
                  .warning(() -> "Invalid numbers on line: " + lineNumber);
            }
          }
        }
      }

      return points.toArray(new AssignedPoint[0]);
    }
  }

  /**
   * Extracts the points from the given Point ROI. Uses the ROI Z-position (if it has a hyperstack
   * position) or the the stack position. If neither are set the z position will be zero.
   *
   * @param roi the roi
   * @return The list of points (can be zero length)
   * @see Roi#hasHyperStackPosition()
   * @see Roi#getZPosition()
   * @see Roi#getPosition()
   */
  public static AssignedPoint[] extractRoiPoints(Roi roi) {
    if (roi instanceof PolygonRoi && roi.getType() == Roi.POINT) {
      // The ROI has either a hyperstack position or a stack position, but not both.
      // Both will be zero if the ROI has no 3D information.
      final int zpos = roi.hasHyperStackPosition() ? roi.getZPosition() : roi.getPosition();
      return extractRoiPoints((PolygonRoi) roi, i -> zpos);
    }
    return new AssignedPoint[0];
  }

  /**
   * Extracts the points from the ROI of the given image. Z coordinates will be extracted if the
   * image stack size if above 1. Uses the point position from a PointRoi if available for a
   * per-point z coordinate. Otherwise defaults to the ROI Z-position (if it has a hyperstack
   * position) or the the stack position. If neither are set the z position will be zero.
   *
   * @param imp the image
   * @return The list of points (can be zero length)
   * @see Roi#hasHyperStackPosition()
   * @see Roi#getZPosition()
   * @see Roi#getPosition()
   * @see PointRoi#getPointPosition(int)
   * @since 1.4
   */
  public static AssignedPoint[] extractRoiPoints(ImagePlus imp) {
    if (imp != null) {
      final Roi roi = imp.getRoi();
      if (roi instanceof PolygonRoi && roi.getType() == Roi.POINT) {
        IntUnaryOperator z;
        if (imp.getStackSize() > 1) {
          // Find z position (see method above)
          final int zpos = roi.hasHyperStackPosition() ? roi.getZPosition() : roi.getPosition();

          // PointRoi have a point position for the stack index.
          // This can be converted to CZT if a hyperstack.
          if (roi instanceof PointRoi) {
            final PointRoi pointRoi = (PointRoi) roi;
            z = i -> {
              final int pos = pointRoi.getPointPosition(i);
              if (pos != 0) {
                return imp.convertIndexToPosition(pos)[1];
              }
              return zpos;
            };
          } else {
            z = i -> zpos;
          }
        } else {
          z = i -> 0;
        }
        return extractRoiPoints((PolygonRoi) roi, z);
      }
    }
    return new AssignedPoint[0];
  }

  /**
   * Extracts the points from the given Point ROI.
   *
   * @param roi the roi
   * @param z the function to generate the z position
   * @return The list of points (can be zero length)
   */
  private static AssignedPoint[] extractRoiPoints(PolygonRoi roi, IntUnaryOperator z) {
    final Polygon p = roi.getNonSplineCoordinates();
    final int n = p.npoints;
    final Rectangle bounds = roi.getBounds();
    final AssignedPoint[] roiPoints = new AssignedPoint[n];
    for (int i = 0; i < n; i++) {
      roiPoints[i] =
          new AssignedPoint(bounds.x + p.xpoints[i], bounds.y + p.ypoints[i], z.applyAsInt(i), i);
    }
    return roiPoints;
  }

  /**
   * Creates an ImageJ PointRoi from the list of points. Uses the float XY coordinates.
   *
   * @param array List of points
   * @return The PointRoi
   */
  public static Roi createRoi(List<? extends Coordinate> array) {
    final int nMaxima = array.size();
    final float[] xpoints = new float[nMaxima];
    final float[] ypoints = new float[nMaxima];
    int index = 0;
    for (final Coordinate point : array) {
      xpoints[index] = point.getX();
      ypoints[index] = point.getY();
      index++;
    }
    return new PointRoi(xpoints, ypoints, nMaxima);
  }

  /**
   * Creates an ImageJ PointRoi from the list of points. Uses the float XY coordinates.
   *
   * @param array List of points
   * @return The PointRoi
   */
  public static Roi createRoi(AssignedPoint[] array) {
    final int nMaxima = array.length;
    final float[] xpoints = new float[nMaxima];
    final float[] ypoints = new float[nMaxima];
    for (int i = 0; i < nMaxima; i++) {
      xpoints[i] = array[i].getX();
      ypoints[i] = array[i].getY();
    }
    return new PointRoi(xpoints, ypoints, nMaxima);
  }

  /**
   * Creates an ImageJ PointRoi from the list of points. Uses the float XY coordinates.
   *
   * <p>If the image is a stack then the integer Z coordinates are converted to a stack position
   * using the provided image and the current channel and frame.
   *
   * @param imp the image
   * @param array List of points
   * @return The PointRoi
   * @see ImagePlus#getChannel()
   * @see ImagePlus#getFrame()
   * @see ImagePlus#getStackIndex(int, int, int)
   * @see PointRoi#getPointPosition(int)
   * @since 1.4
   */
  public static Roi createRoi(ImagePlus imp, List<? extends Coordinate> array) {
    final Roi roi = createRoi(array);
    if (imp.getStackSize() > 1) {
      final int channel = imp.getChannel();
      final int frame = imp.getFrame();
      addPositions((PointRoi) roi, i -> imp.getStackIndex(channel, array.get(i).getZint(), frame));
    }
    return roi;
  }

  /**
   * Creates an ImageJ PointRoi from the list of points. Uses the float XY coordinates.
   *
   * <p>If the image is a stack then the integer Z coordinates are converted to a stack position
   * using the provided image and the current channel and frame.
   *
   * @param imp the image
   * @param array List of points
   * @return The PointRoi
   * @see ImagePlus#getChannel()
   * @see ImagePlus#getFrame()
   * @see ImagePlus#getStackIndex(int, int, int)
   * @see PointRoi#getPointPosition(int)
   * @since 1.4
   */
  public static Roi createRoi(ImagePlus imp, AssignedPoint[] array) {
    final Roi roi = createRoi(array);
    if (imp.getStackSize() > 1) {
      final int channel = imp.getChannel();
      final int frame = imp.getFrame();
      addPositions((PointRoi) roi, i -> imp.getStackIndex(channel, array[i].getZint(), frame));
    }
    return roi;
  }

  /**
   * Adds the per-point positions to the PointRoi using the function to generate the position index.
   *
   * @param roi the roi
   * @param pos the position function
   */
  private static void addPositions(PointRoi roi, IntUnaryOperator pos) {
    final int n = roi.size();
    final int[] counters = new int[n];
    for (int i = 0; i < n; i++) {
      counters[i] = pos.applyAsInt(i) << 8;
    }
    roi.setCounters(counters);
  }

  /**
   * Eliminates duplicate coordinates. Destructively alters the IDs in the input array since the
   * objects are recycled.
   *
   * @param points the points
   * @return new list of points with Ids from zero
   */
  public static AssignedPoint[] eliminateDuplicates(AssignedPoint[] points) {
    // Compare using only XYZ (ignore the ID and assigned ID)
    final TreeSet<AssignedPoint> newPoints = new TreeSet<>((o1, o2) -> {
      int result = Integer.compare(o1.z, o2.z);
      if (result != 0) {
        return result;
      }
      result = Integer.compare(o1.x, o2.x);
      if (result != 0) {
        return result;
      }
      return Integer.compare(o1.y, o2.y);
    });
    newPoints.addAll(Arrays.asList(points));
    final int[] id = {0};
    newPoints.forEach(p -> p.setId(id[0]++));
    return newPoints.toArray(new AssignedPoint[0]);
  }
}
