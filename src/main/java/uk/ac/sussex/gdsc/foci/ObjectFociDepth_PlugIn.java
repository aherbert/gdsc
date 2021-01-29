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
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.measure.Calibration;
import ij.plugin.filter.PlugInFilter;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.text.TextWindow;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.annotation.Nullable;
import uk.ac.sussex.gdsc.core.ij.BufferedTextWindow;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.match.Coordinate;
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

    Settings() {
      input = "";
      is3d = true;
    }

    Settings(Settings source) {
      input = source.input;
      is3d = source.is3d;
      eightConnected = source.eightConnected;
      showObjects = source.showObjects;
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
    final int dx;
    final int dy;
    final int dz;
    /** The squared distance. */
    final double d2;

    Distance(Coordinate point, int id, int dx, int dy, int dz, double d2) {
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

      showMask(oa);

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

      results.stream().map(r -> search3d(r, objectMask, maxx, maxy, maxz, df))
          .filter(d -> d != null).sequential().forEach(distances::add);
    } else {
      // 2D analysis with the current image processor
      final ObjectAnalyzer oa = new ObjectAnalyzer(ip, settings.eightConnected);
      final int[] objectMask = oa.getObjectMask();

      showMask(oa);

      Distance2D df;
      if (cal.pixelWidth == cal.pixelHeight) {
        // No scaling
        df = (dx, dy) -> (double) dx * dx + (double) dy * dy;
      } else {
        // Y scaling
        final double sy = MathUtils.pow2(cal.pixelHeight / cal.pixelWidth);
        df = (dx, dy) -> (double) dx * dx + (double) dy * dy * sy;
      }

      results.stream().map(r -> search2d(r, objectMask, maxx, maxy, df)).filter(d -> d != null)
          .sequential().forEach(distances::add);
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
        sb.append('\t').append(d.dx);
        sb.append('\t').append(d.dy);
        if (is3d) {
          sb.append('\t').append(d.dz);
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

  private void showMask(ObjectAnalyzer oa) {
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

    final ImagePlus imp = ImageJUtils.display(TITLE + " Objects", ip, ImageJUtils.NO_TO_FRONT);
    imp.setDisplayRange(0, oa.getMaxObject());
    imp.setCalibration(this.imp.getCalibration());
  }

  private void showMask(ObjectAnalyzer3D oa) {
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

    final ImagePlus imp = ImageJUtils.display(TITLE + " Objects", stack, ImageJUtils.NO_TO_FRONT);
    imp.setDisplayRange(0, oa.getMaxObject());
    imp.setCalibration(this.imp.getCalibration());
  }
}
