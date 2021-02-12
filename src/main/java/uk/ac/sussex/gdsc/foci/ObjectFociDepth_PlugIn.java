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

import gnu.trove.list.array.TIntArrayList;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.filter.PlugInFilter;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.text.TextWindow;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.annotation.Nullable;
import uk.ac.sussex.gdsc.core.ij.BufferedTextWindow;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.match.Coordinate;
import uk.ac.sussex.gdsc.core.math.hull.ConvexHull2d;
import uk.ac.sussex.gdsc.core.math.hull.ConvexHull3d;
import uk.ac.sussex.gdsc.core.math.hull.Hull2d;
import uk.ac.sussex.gdsc.core.math.hull.Hull3d;
import uk.ac.sussex.gdsc.core.utils.LocalList;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.core.utils.SimpleArrayUtils;
import uk.ac.sussex.gdsc.core.utils.TextUtils;
import uk.ac.sussex.gdsc.help.UrlUtils;

/**
 * Finds objects in an image using contiguous pixels of the same value. Computes the distance of
 * each foci inside an object to the edge/surface (the foci depth).
 *
 * <p>Analysis uses 2D or 3D coordinates.
 */
public class ObjectFociDepth_PlugIn implements PlugInFilter {
  /** The title of the plugin. */
  private static final String TITLE = "Object Foci Depth";

  private static final int FLAGS = DOES_16 + DOES_8G + NO_CHANGES + NO_UNDO;

  private static AtomicReference<TextWindow> distancesWindowRef = new AtomicReference<>();

  private ImagePlus imp;
  private List<Coordinate> results;

  /** The plugin settings. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    String input;
    boolean is3d;
    boolean eightConnected;
    boolean showObjects;
    boolean useHull;

    Settings() {
      input = "";
      is3d = true;
    }

    Settings(Settings source) {
      input = source.input;
      is3d = source.is3d;
      eightConnected = source.eightConnected;
      showObjects = source.showObjects;
      useHull = source.useHull;
    }

    Settings copy() {
      return new Settings(this);
    }

    /**
     * Load a copy of the settings.
     *
     * @return the settings
     */
    static Settings load() {
      return lastSettings.get().copy();
    }

    /**
     * Save the settings.
     */
    void save() {
      lastSettings.set(this);
    }
  }

  /**
   * Store the distance to the edge of the object.
   */
  private static class Distance {
    final Coordinate point;
    final int id;
    final double dx;
    final double dy;
    final double dz;
    /** The squared distance. */
    final double d2;

    Distance(Coordinate point, int id, double dx, double dy, double dz, double d2) {
      this.point = point;
      this.id = id;
      this.dx = dx;
      this.dy = dy;
      this.dz = dz;
      this.d2 = d2;
    }
  }

  /**
   * Define a 2D distance function.
   */
  private interface Distance2D {
    /**
     * Get the distance.
     *
     * @param dx the difference in x
     * @param dy the difference in y
     * @return the distance
     */
    double distance(int dx, int dy);
  }

  /**
   * Define a 3D distance function.
   */
  private interface Distance3D {
    /**
     * Get the distance.
     *
     * @param dx the difference in x
     * @param dy the difference in y
     * @param dz the difference in z
     * @return the distance
     */
    double distance(int dx, int dy, int dz);
  }

  /** {@inheritDoc} */
  @Override
  public int setup(String arg, ImagePlus imp) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    if (imp == null) {
      return DONE;
    }
    this.imp = imp;
    return FLAGS;
  }

  /** {@inheritDoc} */
  @Override
  public void run(ImageProcessor ip) {
    if (!showDialog()) {
      return;
    }

    // Analyse in 2D/3D
    final boolean is3d = imp.getNSlices() > 1 && settings.is3d;

    final int maxx = ip.getWidth();
    final int maxy = ip.getHeight();
    final Calibration cal = imp.getCalibration();

    final List<Distance> distances = new LocalList<>(results.size());

    if (is3d) {
      // 3D analysis with the current z-stack
      final int ch = imp.getChannel();
      final int fr = imp.getFrame();
      final int maxz = imp.getNSlices();
      final int maxx_maxy = maxx * maxy;
      final ImageStack stack = imp.getImageStack();

      final int[] image = new int[maxx_maxy * maxz];
      for (int sl = 1, index = 0; sl <= maxz; sl++) {
        final ImageProcessor ip2 = stack.getProcessor(imp.getStackIndex(ch, sl, fr));
        for (int i = 0; i < maxx_maxy; i++) {
          image[index++] = ip2.get(i);
        }
      }

      final ObjectAnalyzer3D oa =
          new ObjectAnalyzer3D(image, maxx, maxy, maxz, settings.eightConnected);
      final int[] objectMask = oa.getObjectMask();

      // if hull
      // Create convex hull.
      // Optionally show polygons of the hull z-slices on the mask
      // Measure distance of point to all planes of the hull and pick smallest.

      // Same for 2D

      Distance3D df;
      if (cal.pixelWidth == cal.pixelHeight) {
        // No XY scaling
        if (cal.pixelDepth == cal.pixelWidth) {
          // No scaling
          df = (dx, dy, dz) -> (double) dx * dx + (double) dy * dy + (double) dz * dz;
        } else {
          // Z scaling
          final double sz = MathUtils.pow2(cal.pixelDepth / cal.pixelWidth);
          df = (dx, dy, dz) -> (double) dx * dx + (double) dy * dy + (double) dz * dz * sz;
        }
      } else {
        // YZ scaling
        final double sy = MathUtils.pow2(cal.pixelHeight / cal.pixelWidth);
        final double sz = MathUtils.pow2(cal.pixelDepth / cal.pixelWidth);
        df = (dx, dy, dz) -> (double) dx * dx + (double) dy * dy * sy + (double) dz * dz * sz;
      }

      if (settings.useHull) {
        // Function to convert the index to XYZ coordinates
        final double sy = cal.pixelHeight / cal.pixelWidth;
        final double sz = cal.pixelDepth / cal.pixelWidth;
        final IntFunction<double[]> toCoords = i -> {
          final int z = i / maxx_maxy;
          final int mod = i % maxx_maxy;
          final int y = mod / maxx;
          final int x = mod % maxx;
          return new double[] {x, y * sy, z * sz};
        };
        final Hull3d[] hulls = createHulls(oa, toCoords);
        final double[][][] planes = new double[hulls.length][][];
        showMask(oa, hulls, sy, sz);
        results.stream()
            .map(r -> search3d(r, objectMask, maxx, maxy, maxz, toCoords, hulls, planes))
            .filter(d -> d != null).sequential().forEach(distances::add);
      } else {
        showMask(oa, null, 0, 0);
        results.stream().map(r -> search3d(r, objectMask, maxx, maxy, maxz, df))
            .filter(d -> d != null).sequential().forEach(distances::add);
      }
    } else {
      // 2D analysis with the current image processor
      final ObjectAnalyzer oa = new ObjectAnalyzer(ip, settings.eightConnected);
      final int[] objectMask = oa.getObjectMask();

      Distance2D df;
      if (cal.pixelWidth == cal.pixelHeight) {
        // No scaling
        df = (dx, dy) -> (double) dx * dx + (double) dy * dy;
      } else {
        // Y scaling
        final double sy = MathUtils.pow2(cal.pixelHeight / cal.pixelWidth);
        df = (dx, dy) -> (double) dx * dx + (double) dy * dy * sy;
      }

      if (settings.useHull) {
        // Function to convert the index to XYZ coordinates
        final double sy = cal.pixelHeight / cal.pixelWidth;
        final IntFunction<double[]> toCoords = i -> {
          final int y = i / maxx;
          final int x = i % maxx;
          return new double[] {x, y * sy};
        };
        final Hull2d[] hulls = createHulls(oa, toCoords);
        showMask(oa, hulls, sy);
        final double[][][] planes = new double[hulls.length][][];
        results.stream().map(r -> search2d(r, objectMask, maxx, maxy, toCoords, hulls, planes))
            .filter(d -> d != null).sequential().forEach(distances::add);
      } else {
        showMask(oa, null, 0);
        results.stream().map(r -> search2d(r, objectMask, maxx, maxy, df)).filter(d -> d != null)
            .sequential().forEach(distances::add);
      }
    }

    try (BufferedTextWindow tw = new BufferedTextWindow(createWindow(distancesWindowRef,
        "Distances", "Image\tObject\tx\ty\tz\tdx\tdy\tdz\tDistance (px)\tDistance\tUnits"))) {
      final StringBuilder sb = new StringBuilder();
      final String title = imp.getTitle();
      for (final Distance d : distances) {
        sb.setLength(0);
        final Coordinate point = d.point;
        sb.append(title);
        sb.append('\t').append(d.id);
        sb.append('\t').append(point.getXint());
        sb.append('\t').append(point.getYint());
        if (is3d) {
          sb.append('\t').append(point.getZint());
        } else {
          sb.append("\t0");
        }
        sb.append('\t').append(MathUtils.round(d.dx));
        sb.append('\t').append(MathUtils.round(d.dy));
        if (is3d) {
          sb.append('\t').append(MathUtils.round(d.dz));
        } else {
          sb.append("\t0");
        }
        final double dist = Math.sqrt(d.d2);
        sb.append('\t').append(MathUtils.round(dist));
        sb.append('\t').append(MathUtils.round(dist * cal.pixelWidth));
        sb.append('\t').append(cal.getUnit());
        tw.append(sb.toString());
      }
    }
  }

  /**
   * Creates the 3D hull for each mask object.
   *
   * @param oa the object analyser
   * @param toCoords the function to convert mask index to coordinates
   * @return the hulls
   */
  private static Hull3d[] createHulls(ObjectAnalyzer3D oa, IntFunction<double[]> toCoords) {
    final int[] mask = oa.getObjectMask();
    final int maxx = oa.getMaxX();
    final int maxy = oa.getMaxY();
    final int maxx_maxy = maxx * maxy;

    // Create indices for the outside of each hull.
    final TIntArrayList[] indices = IntStream.rangeClosed(0, oa.getMaxObject())
        .mapToObj(i -> new TIntArrayList()).toArray(TIntArrayList[]::new);
    indices[0] = null;
    for (int i = 0; i < oa.getMaxZ(); i++) {
      scanImageOutline(mask, i * maxx_maxy, maxx, maxy, indices);
    }

    // For each hull indices convert to coordinates and create a hull.
    return Arrays.stream(indices).map(l -> {
      if (l == null || l.size() < 4) {
        return null;
      }
      final ConvexHull3d.Builder builder = ConvexHull3d.newBuilder();
      l.forEach(index -> {
        builder.add(toCoords.apply(index));
        return true;
      });
      return builder.build();
    }).toArray(Hull3d[]::new);
  }


  /**
   * Creates the 3D hull for each mask object.
   *
   * @param oa the object analyser
   * @param toCoords the function to convert mask index to coordinates
   * @return the hulls
   */
  private static Hull2d[] createHulls(ObjectAnalyzer oa, IntFunction<double[]> toCoords) {
    final int[] mask = oa.getObjectMask();
    final int maxx = oa.getWidth();
    final int maxy = oa.getHeight();

    // Create indices for the outside of each hull.
    final TIntArrayList[] indices = IntStream.rangeClosed(0, oa.getMaxObject())
        .mapToObj(i -> new TIntArrayList()).toArray(TIntArrayList[]::new);
    indices[0] = null;
    scanImageOutline(mask, 0, maxx, maxy, indices);

    // For each hull indices convert to coordinates and create a hull.
    return Arrays.stream(indices).map(l -> {
      if (l == null || l.size() < 3) {
        return null;
      }
      final ConvexHull2d.Builder builder = ConvexHull2d.newBuilder();
      l.forEach(index -> {
        builder.add(toCoords.apply(index));
        return true;
      });
      return builder.build();
    }).toArray(Hull2d[]::new);
  }

  /**
   * Scan the image outline in horizontal strips for any change in mask objects and store the
   * indices in the appropriate list.
   *
   * @param mask the mask
   * @param start the start of the scan
   * @param maxx the maxx of the mask
   * @param maxy the maxy of the mask
   * @param indices the list of indices for each object
   */
  private static void scanImageOutline(int[] mask, int start, int maxx, int maxy,
      TIntArrayList[] indices) {
    int index = start;
    for (int y = 0; y < maxy; y++) {
      int current = 0;
      for (int x = 0; x < maxx; x++, index++) {
        final int next = mask[index];
        if (next != current) {
          addToList(indices[current], current, index - 1);
          addToList(indices[next], next, index);
          current = next;
        }
      }
      // End of the line
      addToList(indices[current], current, index - 1);
    }
  }

  private static void addToList(TIntArrayList indices, int object, int index) {
    if (object != 0 && (indices.isEmpty() || indices.getQuick(indices.size() - 1) != index)) {
      indices.add(index);
    }
  }

  /**
   * Search from the result to the edge of the containing object in 2D.
   *
   * @param result the result
   * @param objectMask the object mask
   * @param maxx the max x dimension of the mask
   * @param maxy the max y dimension of the mask
   * @param df the distance function
   * @return the distance (or null)
   */
  private static Distance search2d(Coordinate point, int[] objectMask, int maxx, int maxy,
      Distance2D df) {
    final int x = point.getXint();
    final int y = point.getYint();
    // Check within the image
    if (x < 0 || x >= maxx || y < 0 || y >= maxy) {
      return null;
    }

    int index = y * maxx + x;
    final int objectId = objectMask[index];
    if (objectId == 0) {
      // Not within an object
      return null;
    }

    // Search for the edge
    // Find the initial bounding box in XY.
    final int sx1 = scan(objectMask, index, -1, x);
    final int sx2 = scan(objectMask, index, 1, maxx - x - 1);
    final int sy1 = scan(objectMask, index, -maxx, y);
    final int sy2 = scan(objectMask, index, maxx, maxy - y - 1);
    // Find the upper limit of the distance.
    // @formatter:off
    final double[] distances = {
        df.distance(sx1, 0),
        df.distance(sx2, 0),
        df.distance(0, sy1),
        df.distance(0, sy2),
    };
    // @formatter:on
    // Find the closest pixel not from the object inside the bounding box.
    double dmin = MathUtils.min(distances);
    int imin = -1;
    // Only need to search the minimum bounding box.
    // Treat dimensions separately as they may be scaled (i.e. do not take minimum of sx and sy).
    final int sx = Math.min(sx1, sx2);
    final int sy = Math.min(sy1, sy2);
    for (int dy = -sy; dy <= sy; dy++) {
      index = (y + dy) * maxx + x;
      for (int dx = -sx; dx <= sx; dx++) {
        if (objectId != objectMask[index + dx]) {
          final double d = df.distance(dx, dy);
          if (d < dmin) {
            dmin = d;
            imin = index + dx;
          }
        }
      }
    }

    // If not found then the closest point is from the initial the bounding box
    if (imin == -1) {
      // @formatter:off
      switch (SimpleArrayUtils.findMinIndex(distances)) {
        case 0:  return new Distance(point, objectId, -sx1,  0, 0, dmin);
        case 1:  return new Distance(point, objectId,  sx2,  0, 0, dmin);
        case 2:  return new Distance(point, objectId,  0, -sy1, 0, dmin);
        default: return new Distance(point, objectId,  0,  sy2, 0, dmin);
      }
      // @formatter:on
    }

    // Find the closest pixel from the object touching that non-object pixel.
    int iy = imin / maxx;
    int ix = imin % maxx;
    // Compute offsets to points inside the image
    final int ox1 = Math.max(0, ix - 1) - x;
    final int ox2 = Math.min(maxx - 1, ix + 1) - x;
    final int oy1 = Math.max(0, iy - 1) - y;
    final int oy2 = Math.min(maxy - 1, iy + 1) - y;
    for (int dy = oy1; dy <= oy2; dy++) {
      for (int dx = ox1; dx <= ox2; dx++) {
        final double d = df.distance(dx, dy);
        if (d < dmin) {
          // Closer so check if the correct object
          index = (y + dy) * maxx + x + dx;
          if (objectId == objectMask[index]) {
            dmin = d;
            ix = dx;
            iy = dy;
          }
        }
      }
    }

    return new Distance(point, objectId, ix, iy, 0, dmin);
  }

  /**
   * Search from the result to the edge of the containing object in 2D.
   *
   * @param point the point
   * @param objectMask the object mask
   * @param maxx the max x dimension of the mask
   * @param maxy the max y dimension of the mask
   * @param toCoords the function to convert mask index to coordinates
   * @param hulls the hulls
   * @param hullPlanes the hull planes
   * @return the distance (or null)
   */
  private static Distance search2d(Coordinate point, int[] objectMask, int maxx, int maxy,
      IntFunction<double[]> toCoords, Hull2d[] hulls, double[][][] hullPlanes) {
    final int x = point.getXint();
    final int y = point.getYint();
    // Check within the image
    if (x < 0 || x >= maxx || y < 0 || y >= maxy) {
      return null;
    }

    final int index = y * maxx + x;
    final int objectId = objectMask[index];
    if (objectId == 0) {
      // Not within an object
      return null;
    }

    // Get the planes for the hull lines
    final double[][] planes = getPlanes(hulls[objectId], hullPlanes[objectId]);

    // Measure distance to each plane.
    final double[] coords = toCoords.apply(index);
    final double x0 = coords[0];
    final double y0 = coords[1];
    double dmin = Double.POSITIVE_INFINITY;
    double[] minPlane = null;
    for (final double[] plane : planes) {
      // Distance to the plane. We use the absolute as we do not know if the hull is using
      // clockwise or counter clockwise winding and the distance is signed to be inside / outside.
      final double d = Math.abs(x0 * plane[0] + y0 * plane[1] + plane[2]);
      if (dmin > d) {
        dmin = d;
        minPlane = plane;
      }
    }

    if (minPlane == null) {
      // Invalid lines for the entire hull.
      // Assume a single point for the hull and a distance of zero.
      return new Distance(point, objectId, 0, 0, 0, 0);
    }

    // Compute the XY offset
    final double a = minPlane[0];
    final double b = minPlane[1];
    final double c = minPlane[2];
    // Signed distance
    final double sd = x0 * a + y0 * b + c;

    // The offset is the distance multiplied by the plane normal.
    // The distance is squared.
    return new Distance(point, objectId, -sd * a, -sd * b, 0, sd * sd);
  }

  /**
   * Search from the result to the edge of the containing object in 3D.
   *
   * @param point the point
   * @param objectMask the object mask
   * @param maxx the max x dimension of the mask
   * @param maxy the max y dimension of the mask
   * @param maxz the max z dimension of the mask
   * @param df the distance function
   * @return the distance (or null)
   */
  private static Distance search3d(Coordinate point, int[] objectMask, int maxx, int maxy, int maxz,
      Distance3D df) {
    final int x = point.getXint();
    final int y = point.getYint();
    // Check within the image
    if (x < 0 || x >= maxx || y < 0 || y >= maxy) {
      return null;
    }
    // For 3D processing z has already been validated to be in the range [1,nSlices]
    final int z = point.getZint() - 1;

    final int maxx_maxy = maxx * maxy;
    int index = z * maxx_maxy + y * maxx + x;
    final int objectId = objectMask[index];
    if (objectId == 0) {
      // Not within an object
      return null;
    }

    // Search for the edge
    // Find the initial bounding box in XY.
    final int sx1 = scan(objectMask, index, -1, x);
    final int sx2 = scan(objectMask, index, 1, maxx - x - 1);
    final int sy1 = scan(objectMask, index, -maxx, y);
    final int sy2 = scan(objectMask, index, maxx, maxy - y - 1);
    final int sz1 = scan(objectMask, index, -maxx_maxy, z);
    final int sz2 = scan(objectMask, index, maxx_maxy, maxz - z - 1);
    // Find the upper limit of the distance.
    // @formatter:off
    final double[] distances = {
        df.distance(sx1, 0, 0),
        df.distance(sx2, 0, 0),
        df.distance(0, sy1, 0),
        df.distance(0, sy2, 0),
        df.distance(0, 0, sz1),
        df.distance(0, 0, sz2),
    };
    // @formatter:on
    // Find the closest pixel not from the object inside the bounding box.
    double dmin = MathUtils.min(distances);
    int imin = -1;
    // Only need to search the minimum bounding box.
    // Treat dimensions separately as they may be scaled (i.e. do not take minimum of sx and sy).
    final int sx = Math.min(sx1, sx2);
    final int sy = Math.min(sy1, sy2);
    final int sz = Math.min(sz1, sz2);
    for (int dz = -sz; dz <= sz; dz++) {
      for (int dy = -sy; dy <= sy; dy++) {
        index = (z + dz) * maxx_maxy + (y + dy) * maxx + x;
        for (int dx = -sx; dx <= sx; dx++) {
          if (objectId != objectMask[index + dx]) {
            final double d = df.distance(dx, dy, dz);
            if (d < dmin) {
              dmin = d;
              imin = index + dx;
            }
          }
        }
      }
    }

    // If not found then the closest point is from the initial the bounding box
    if (imin == -1) {
      // @formatter:off
      switch (SimpleArrayUtils.findMinIndex(distances)) {
        case 0:  return new Distance(point, objectId, -sx1,  0, 0, dmin);
        case 1:  return new Distance(point, objectId,  sx2,  0, 0, dmin);
        case 2:  return new Distance(point, objectId,  0, -sy1, 0, dmin);
        case 3:  return new Distance(point, objectId,  0,  sy2, 0, dmin);
        case 4:  return new Distance(point, objectId,  0, 0, -sz1, dmin);
        default: return new Distance(point, objectId,  0, 0,  sz2, dmin);
      }
      // @formatter:on
    }

    // Find the closest pixel from the object touching that non-object pixel.
    int iz = imin / maxx_maxy;
    imin %= maxx_maxy;
    int iy = imin / maxx;
    int ix = imin % maxx;
    // Compute offsets to points inside the image
    final int ox1 = Math.max(0, ix - 1) - x;
    final int ox2 = Math.min(maxx - 1, ix + 1) - x;
    final int oy1 = Math.max(0, iy - 1) - y;
    final int oy2 = Math.min(maxy - 1, iy + 1) - y;
    final int oz1 = Math.max(0, iz - 1) - z;
    final int oz2 = Math.min(maxz - 1, iz + 1) - z;
    for (int dz = oz1; dz <= oz2; dz++) {
      for (int dy = oy1; dy <= oy2; dy++) {
        for (int dx = ox1; dx <= ox2; dx++) {
          final double d = df.distance(dx, dy, dz);
          if (d < dmin) {
            // Closer so check if the correct object
            index = (z + dz) * maxx_maxy + (y + dy) * maxx + x + dx;
            if (objectId == objectMask[index]) {
              dmin = d;
              ix = dx;
              iy = dy;
              iz = dz;
            }
          }
        }
      }
    }

    return new Distance(point, objectId, ix, iy, iz, dmin);
  }

  /**
   * Search from the result to the edge of the containing object in 3D.
   *
   * @param point the point
   * @param objectMask the object mask
   * @param maxx the max x dimension of the mask
   * @param maxy the max y dimension of the mask
   * @param maxz the max z dimension of the mask
   * @param toCoords the function to convert mask index to coordinates
   * @param hulls the hulls
   * @param hullPlanes the hull planes
   * @return the distance (or null)
   */
  private static Distance search3d(Coordinate point, int[] objectMask, int maxx, int maxy, int maxz,
      IntFunction<double[]> toCoords, Hull3d[] hulls, double[][][] hullPlanes) {
    final int x = point.getXint();
    final int y = point.getYint();
    // Check within the image
    if (x < 0 || x >= maxx || y < 0 || y >= maxy) {
      return null;
    }
    // For 3D processing z has already been validated to be in the range [1,nSlices]
    final int z = point.getZint() - 1;

    final int maxx_maxy = maxx * maxy;
    final int index = z * maxx_maxy + y * maxx + x;
    final int objectId = objectMask[index];
    if (objectId == 0) {
      // Not within an object
      return null;
    }

    if (hulls[objectId] == null) {
      // Invalid hull.
      // Assume the object is too small to create a hull and return zero distance.
      return new Distance(point, objectId, 0, 0, 0, 0);
    }

    // Get the planes for the hull faces
    final double[][] planes = getPlanes(hulls[objectId], hullPlanes[objectId]);

    // Measure distance to each plane.
    final double[] coords = toCoords.apply(index);
    final double x0 = coords[0];
    final double y0 = coords[1];
    final double z0 = coords[2];
    double dmin = Double.POSITIVE_INFINITY;
    double[] minPlane = null;
    for (final double[] plane : planes) {
      // Distance to the plane.
      // The distance will be negative if below the plane (inside the hull).
      // Use the absolute to avoid rounding errors close to the hull surface.
      final double d = Math.abs(x0 * plane[0] + y0 * plane[1] + z0 * plane[2] + plane[3]);
      if (dmin > d) {
        dmin = d;
        minPlane = plane;
      }
    }

    if (minPlane == null) {
      // Invalid planes for the entire hull. This should not be possible.
      throw new IllegalStateException("No valid planes for the hull for object " + objectId);
    }

    // Compute the XY offset
    final double a = minPlane[0];
    final double b = minPlane[1];
    final double c = minPlane[2];
    final double d = minPlane[3];
    // Signed distance
    final double sd = x0 * a + y0 * b + z0 * c + d;

    // The offset is the distance multiplied by the plane normal.
    // The distance is squared.
    return new Distance(point, objectId, -sd * a, -sd * b, -sd * c, sd * sd);
  }

  /**
   * Gets the planes for the hull lines.
   *
   * @param hull the hull
   * @param planes the planes (can be null)
   * @return the planes
   */
  private static double[][] getPlanes(Hull2d hull, double[][] planes) {
    if (planes == null) {
      final double[][] vertices = hull.getVertices();
      planes = new double[vertices.length][];
      for (int i = vertices.length, i1 = 0; i-- > 0; i1 = i) {
        final double[] v1 = vertices[i];
        final double[] v2 = vertices[i1];
        // Line in the plane ax + bx + c = 0
        // where the plane normal is (a,b).
        // Find the plane normal.
        double a = v2[1] - v1[1];
        double b = v1[0] - v2[0];
        final double length = Math.hypot(a, b);
        if (length == 0) {
          // Note a line so store an invalid plane.
          // Any distance computed to this plane will be NaN.
          planes[i] = new double[] {Double.NaN, Double.NaN, Double.NaN};
          continue;
        }
        a /= length;
        b /= length;
        // Find point on the line (use the average)
        final double x = (v1[0] + v2[0]) / 2;
        final double y = (v1[1] + v2[1]) / 2;
        // Solve ax + bx + c = 0 => c = -ax - by
        final double c = -a * x - b * y;
        planes[i] = new double[] {a, b, c};
      }
    }
    return planes;
  }

  /**
   * Gets the planes for the hull faces.
   *
   * @param hull the hull
   * @param planes the planes (can be null)
   * @return the planes
   */
  private static double[][] getPlanes(Hull3d hull, double[][] planes) {
    if (planes == null) {
      final int faces = hull.getNumberOfFaces();
      planes = new double[faces][];
      for (int i = 0; i < faces; i++) {
        planes[i] = hull.getPlane(i);
      }
    }
    return planes;
  }

  /**
   * Scan the object mask from the given index in the given direction up to the specified number of
   * maximum steps while remaining in the same object.
   *
   * @param objectMask the object mask
   * @param index the index
   * @param delta the delta
   * @param maxSteps the max steps
   * @return the number of steps while remaining in the same object
   */
  private static int scan(int[] objectMask, int index, int delta, int maxSteps) {
    final int objectId = objectMask[index];
    for (int i = 1; i <= maxSteps; i++) {
      if (objectId != objectMask[index + i * delta]) {
        return i - 1;
      }
    }
    return maxSteps;
  }

  private boolean showDialog() {
    final List<Coordinate> findFociResults = getFindFociResults();
    final List<Coordinate> roiResults = getRoiResults();
    if (findFociResults == null && roiResults == null) {
      IJ.error(TITLE,
          "No " + FindFoci_PlugIn.TITLE + " results in memory or point ROI on the image");
      return false;
    }

    final GenericDialog gd = new GenericDialog(TITLE);
    gd.addHelp(UrlUtils.FIND_FOCI);

    String[] options = new String[2];
    int count = 0;

    final StringBuilder msg =
        new StringBuilder("Measure the foci distance to the nearest object edge\n"
            + "(Objects will be found in the current image).\nThe distance is to the "
            + "nearest pixel in the same object that is touching the edge.\nAvailable foci:");
    if (findFociResults != null) {
      msg.append("\nFind Foci = ").append(TextUtils.pleural(findFociResults.size(), "result"));
      options[count++] = "Find Foci";
    }
    if (roiResults != null) {
      msg.append("\nROI = ").append(TextUtils.pleural(roiResults.size(), "point"));
      options[count++] = "ROI";
    }
    options = Arrays.copyOf(options, count);

    gd.addMessage(msg.toString());

    final boolean is3d = imp.getNSlices() > 1;

    settings = Settings.load();
    gd.addChoice("Foci", options, settings.input);
    if (is3d) {
      gd.addMessage(TextUtils.wrap(
          "Z-stack input image allows 3D processing. This may produce shorter distances than 2D "
              + "processing as neighbouring slices may have closer edges.",
          80));
      gd.addCheckbox("3D", settings.is3d);
    }
    gd.addCheckbox("Use_convex_hull", settings.useHull);
    gd.addCheckbox("Eight_connected", settings.eightConnected);
    gd.addCheckbox("Show_objects", settings.showObjects);

    gd.showDialog();
    if (gd.wasCanceled()) {
      return false;
    }

    settings.input = gd.getNextChoice();
    if (is3d) {
      settings.is3d = gd.getNextBoolean();
    }
    settings.useHull = gd.getNextBoolean();
    settings.eightConnected = gd.getNextBoolean();
    settings.showObjects = gd.getNextBoolean();
    settings.save();

    // Load objects
    results = (settings.input.equalsIgnoreCase("ROI")) ? roiResults : findFociResults;
    if (results == null) {
      IJ.error(TITLE, "No foci could be loaded");
      return false;
    }

    if (settings.is3d && !checkZ(imp.getNSlices(), results)) {
      IJ.error(TITLE, "3D processing requested but foci contain invalid z coordinates that\n"
          + "do not fit in the z-stack");
      return false;
    }

    return true;
  }

  @Nullable
  private static List<Coordinate> getFindFociResults() {
    final List<FindFociResult> lastResults = FindFoci_PlugIn.getLastResults();
    if (lastResults == null) {
      return null;
    }
    final LocalList<Coordinate> list = new LocalList<>(lastResults.size());
    for (final FindFociResult result : lastResults) {
      // z is zero-indexed. Adjust to one-indexed
      list.add(new BasePoint(result.x, result.y, result.z + 1));
    }
    return list;
  }

  @Nullable
  private List<Coordinate> getRoiResults() {
    final AssignedPoint[] points = AssignedPointUtils.extractRoiPoints(imp);
    if (points.length == 0) {
      return null;
    }
    final LocalList<Coordinate> list = new LocalList<>(points.length);
    for (final AssignedPoint point : points) {
      // z is one-indexed
      list.add(new BasePoint(point.x, point.y, point.z));
    }
    return list;
  }

  /**
   * Check the Z coordinate is within the number of slices.
   *
   * @param slices the number of slices
   * @param results the results
   * @return true, if successful
   */
  private static boolean checkZ(int slices, List<Coordinate> results) {
    for (final Coordinate r : results) {
      if (r.getZint() > slices || r.getZint() < 1) {
        return false;
      }
    }
    return true;
  }

  private static TextWindow createWindow(AtomicReference<TextWindow> windowRef, String title,
      String header) {
    TextWindow window = windowRef.get();
    if (window == null || !window.isVisible()) {
      window = new TextWindow(TITLE + " " + title, header, "", 800, 400);
      windowRef.set(window);
    }
    return window;
  }

  private void showMask(ObjectAnalyzer oa, Hull2d[] hulls, double sy) {
    if (!settings.showObjects) {
      return;
    }

    final int[] objectMask = oa.getObjectMask();
    ImageProcessor ip;
    if (oa.getMaxObject() <= 255) {
      ip = new ByteProcessor(oa.getWidth(), oa.getHeight());
    } else {
      ip = new ShortProcessor(oa.getWidth(), oa.getHeight());
    }
    for (int i = 0; i < objectMask.length; i++) {
      ip.set(i, objectMask[i]);
    }

    final Overlay overlay = new Overlay();
    if (hulls != null) {
      Arrays.stream(hulls).filter(hull -> hull != null)
          .forEach(hull -> overlay.add(toRoi(hull.getVertices(), sy, 0)));
    }

    final ImagePlus imp = ImageJUtils.display(TITLE + " Objects", ip, ImageJUtils.NO_TO_FRONT);
    imp.setDisplayRange(0, oa.getMaxObject());
    imp.setCalibration(this.imp.getCalibration());
    imp.setOverlay(overlay);
  }

  private void showMask(ObjectAnalyzer3D oa, Hull3d[] hulls, double sy, double sz) {
    if (!settings.showObjects) {
      return;
    }

    final int[] objectMask = oa.getObjectMask();
    ImageProcessor ip;
    if (oa.getMaxObject() <= 255) {
      ip = new ByteProcessor(oa.getMaxX(), oa.getMaxY());
    } else {
      ip = new ShortProcessor(oa.getMaxX(), oa.getMaxY());
    }

    final ImageStack stack = new ImageStack(oa.getMaxX(), oa.getMaxY());
    final int maxx_maxy = ip.getPixelCount();
    for (int sl = 1, index = 0; sl <= oa.getMaxZ(); sl++) {
      for (int i = 0; i < maxx_maxy; i++) {
        ip.set(i, objectMask[index++]);
      }
      stack.addSlice(null, ip.getPixelsCopy());
    }

    final Overlay overlay = new Overlay();
    if (hulls != null) {
      final double[] plane = {0, 0, 1, 0};
      final int maxz = oa.getMaxZ();
      Arrays.stream(hulls).filter(hull -> hull != null).forEach(hull -> {
        // Slice into planes
        for (int z = 0; z < maxz; z++) {
          plane[3] = -z * sz;
          for (final List<double[]> polygon : hull.getPolygons(plane)) {
            overlay.add(toRoi(polygon.toArray(new double[0][]), sy, z + 1));
          }
        }
      });
    }

    final ImagePlus imp = ImageJUtils.display(TITLE + " Objects", stack, ImageJUtils.NO_TO_FRONT);
    imp.setDisplayRange(0, oa.getMaxObject());
    imp.setCalibration(this.imp.getCalibration());
    imp.setOverlay(overlay);
  }

  /**
   * Convert the hull vertices to an roi. The scale for the y coordinate is used to map the vertices
   * from x-dimension pixel units back to y-dimension pixel units.
   *
   * <pre>
   * y_px = y / sy
   * </pre>
   *
   * @param vertices the vertices
   * @param sy the scale for the y dimension
   * @param position the slice position
   * @return the roi
   */
  private static Roi toRoi(double[][] vertices, double sy, int position) {
    final float[] x = new float[vertices.length];
    final float[] y = new float[vertices.length];
    for (int i = 0; i < x.length; i++) {
      x[i] = (float) vertices[i][0];
      y[i] = (float) (vertices[i][1] / sy);
    }
    // Draw the coordinate centre at the pixel centre as if this was a line or point ROI
    @SuppressWarnings("serial")
    final PolygonRoi roi = new PolygonRoi(x, y, Roi.POLYGON) {
      @Override
      protected boolean useLineSubpixelConvention() {
        return true;
      }
    };
    roi.setPosition(position);
    return roi;
  }
}
