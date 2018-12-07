/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2018 Alex Herbert
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

import uk.ac.sussex.gdsc.core.match.Coordinate;

import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Manages I/O of the {@link AssignedPoint} class.
 */
public final class AssignedPointUtils {
  private static final String NEW_LINE = System.getProperty("line.separator");

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

    final File file = new File(filename);
    if (!file.exists() && file.getParent() != null) {
      new File(file.getParent()).mkdirs();
    }

    try (final OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(filename))) {
      // Save results to file
      final StringBuilder sb = new StringBuilder();

      out.write("X,Y,Z" + NEW_LINE);

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
    try (BufferedReader input = new BufferedReader(new FileReader(filename))) {
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
   * Extracts the points from the given Point ROI.
   *
   * @param roi the roi
   * @return The list of points (can be zero length)
   */
  public static AssignedPoint[] extractRoiPoints(Roi roi) {
    AssignedPoint[] roiPoints = null;

    if (roi != null && roi.getType() == Roi.POINT) {
      final Polygon p = ((PolygonRoi) roi).getNonSplineCoordinates();
      final int n = p.npoints;
      final Rectangle bounds = roi.getBounds();
      // The ROI has either a hyperstack position or a stack position, but not both.
      // Both will be zero if the ROI has no 3D information.
      int zpos = roi.getZPosition();
      if (zpos == 0) {
        zpos = roi.getPosition();
      }

      roiPoints = new AssignedPoint[n];
      for (int i = 0; i < n; i++) {
        roiPoints[i] = new AssignedPoint(bounds.x + p.xpoints[i], bounds.y + p.ypoints[i], zpos, i);
      }
    } else {
      roiPoints = new AssignedPoint[0];
    }

    return roiPoints;
  }

  /**
   * Creates an ImageJ PointRoi from the list of points.
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
   * Creates an ImageJ PointRoi from the list of points.
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
   * Eliminates duplicate coordinates. Destructively alters the IDs in the input array since the
   * objects are recycled
   *
   * @param points the points
   * @return new list of points with Ids from zero
   */
  public static AssignedPoint[] eliminateDuplicates(AssignedPoint[] points) {
    final HashSet<AssignedPoint> newPoints = new HashSet<>();
    int id = 0;
    for (final AssignedPoint p : points) {
      if (newPoints.add(p)) {
        p.setId(id++);
      }
    }
    return newPoints.toArray(new AssignedPoint[0]);
  }
}
