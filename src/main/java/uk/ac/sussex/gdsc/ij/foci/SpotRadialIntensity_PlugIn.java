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

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Plot;
import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.process.ShortProcessor;
import ij.text.TextWindow;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import uk.ac.sussex.gdsc.core.annotation.Nullable;
import uk.ac.sussex.gdsc.core.ij.BufferedTextWindow;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.ij.gui.ExtendedGenericDialog;
import uk.ac.sussex.gdsc.core.ij.process.LutHelper;
import uk.ac.sussex.gdsc.core.ij.process.LutHelper.LutColour;
import uk.ac.sussex.gdsc.core.utils.LocalList;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.core.utils.SimpleArrayUtils;
import uk.ac.sussex.gdsc.core.utils.Statistics;
import uk.ac.sussex.gdsc.ij.UsageTracker;
import uk.ac.sussex.gdsc.ij.foci.ObjectAnalyzer.ObjectCentre;
import uk.ac.sussex.gdsc.ij.help.HelpUrls;

/**
 * Output the radial intensity around spots within a mask region. Spots are defined using FindFoci
 * results loaded from memory.
 *
 * <p>A mask must be provided as an image.
 */
public class SpotRadialIntensity_PlugIn implements PlugIn {
  private static final String TITLE = "Spot Radial Intensity";
  /** The results windows for each table title. The title contains the distance and interval. */
  private static final Map<String, AtomicReference<TextWindow>> resultsWindows =
      new ConcurrentHashMap<>();
  private static final AtomicReference<TextWindow> objectsWindow = new AtomicReference<>();
  private static final int MODE_NONE = 0;
  private static final int MODE_MASK = 1;
  private static final int MODE_EXCLUSIVE_MASK = 2;

  private ImagePlus imp;
  private String prefix;

  /** The current settings for the plugin instance. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    boolean useRoi;
    String resultsName = "";
    String maskImage = "";
    int distance = 10;
    double interval = 1;
    int segmentMode;
    double segmentWidth = 3;
    boolean showFoci;
    boolean showObjects;
    boolean showTable = true;
    boolean showPlot = true;
    boolean showRadii;
    boolean plotDistanceAverages = true;

    /**
     * Default constructor.
     */
    Settings() {
      // Do nothing
    }

    /**
     * Copy constructor.
     *
     * @param source the source
     */
    private Settings(Settings source) {
      useRoi = source.useRoi;
      resultsName = source.resultsName;
      maskImage = source.maskImage;
      distance = source.distance;
      interval = source.interval;
      segmentMode = source.segmentMode;
      segmentWidth = source.segmentWidth;
      showFoci = source.showFoci;
      showObjects = source.showObjects;
      showTable = source.showTable;
      showPlot = source.showPlot;
      showRadii = source.showRadii;
      plotDistanceAverages = source.plotDistanceAverages;
    }

    /**
     * Copy the settings.
     *
     * @return the settings
     */
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
   * A spot within a mask object.
   */
  static class Foci {
    final int id;
    final int object;
    final int x;
    final int y;

    Foci(int id, int object, int x, int y) {
      this.id = id;
      this.object = object;
      this.x = x;
      this.y = y;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    imp = WindowManager.getCurrentImage();
    if (imp == null) {
      IJ.noImage();
      return;
    }
    if (imp.getBitDepth() == 24) {
      IJ.error(TITLE, "RGB images are not supported");
      return;
    }

    Roi imageRoi = imp.getRoi();
    if (imageRoi != null && imageRoi.getType() != Roi.POINT) {
      imageRoi = null;
    }

    // List the foci results
    final String[] names = FindFoci_PlugIn.getResultsNames();
    if (imageRoi == null && names.length == 0) {
      IJ.error(TITLE, "Spots must be point ROI or stored in memory using the "
          + FindFoci_PlugIn.TITLE + " plugin");
      return;
    }

    // Build a list of the open images for use as a mask
    final String[] maskImageList = getImageList();
    if (maskImageList.length == 0) {
      IJ.error(TITLE, "No mask images");
      return;
    }

    settings = Settings.load();

    final ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);

    gd.addMessage("Analyses spots within a mask region\n"
        + "and computes radial intensity within the mask object region.");

    if (imageRoi != null && names.length != 0) {
      gd.addCheckbox("Use_roi", settings.useRoi);
    }
    if (names.length != 0) {
      gd.addChoice("Results_name", names, settings.resultsName);
    }
    gd.addChoice("Mask", maskImageList, settings.maskImage);
    gd.addNumericField("Distance", settings.distance, 0, 6, "pixels");
    gd.addNumericField("Interval", settings.interval, 2, 6, "pixels");
    gd.addChoice("Segment", new String[] {"None", "Mask", "Exclusive Mask"}, settings.segmentMode);
    gd.addNumericField("Segment_width", settings.segmentWidth, 2, 6, "pixels");
    gd.addCheckbox("Show_foci", settings.showFoci);
    gd.addCheckbox("Show_objects", settings.showObjects);
    gd.addCheckbox("Show_table", settings.showTable);
    gd.addCheckbox("Show_plot", settings.showPlot);
    gd.addCheckbox("Show_radii", settings.showRadii);
    gd.addCheckbox("Plot_average_distances", settings.plotDistanceAverages);
    gd.addHelp(HelpUrls.getUrl("spot-radial-intensity"));

    gd.showDialog();
    if (!gd.wasOKed()) {
      return;
    }

    if (imageRoi != null && names.length != 0) {
      settings.useRoi = gd.getNextBoolean();
    }
    if (names.length != 0) {
      settings.resultsName = gd.getNextChoice();
    }
    settings.maskImage = gd.getNextChoice();
    settings.distance = (int) gd.getNextNumber();
    settings.interval = gd.getNextNumber();
    settings.segmentMode = gd.getNextChoiceIndex();
    settings.segmentWidth = gd.getNextNumber();
    settings.showFoci = gd.getNextBoolean();
    settings.showObjects = gd.getNextBoolean();
    settings.showTable = gd.getNextBoolean();
    settings.showPlot = gd.getNextBoolean();
    settings.showRadii = gd.getNextBoolean();
    settings.plotDistanceAverages = gd.getNextBoolean();

    settings.save();

    // Validate options
    if (!isValidDistance(settings.distance, settings.interval)) {
      IJ.error(TITLE, "No valid distances using the given interval");
      return;
    }
    if (settings.segmentMode != MODE_NONE && !(settings.segmentWidth >= 1)) {
      IJ.error(TITLE, "Invalid segment width: " + settings.segmentWidth);
      return;
    }
    if (!(settings.showTable || settings.showPlot)) {
      IJ.error(TITLE, "No output option");
      return;
    }

    // Get the objects
    final ObjectAnalyzer objects = createObjectAnalyzer(settings.maskImage);
    if (objects == null) {
      return;
    }

    // Get the foci
    Foci[] foci;
    if (imageRoi != null && (settings.useRoi || names.length == 0)) {
      foci = getFoci(imageRoi, objects);
    } else {
      foci = getFoci(settings.resultsName, objects);
    }
    if (foci == null) {
      return;
    }

    final PointRoi roi = (settings.showFoci || settings.showObjects) ? createPointRoi(foci) : null;

    if (settings.showFoci) {
      imp.setRoi(roi);
    }
    if (settings.showObjects) {
      ImageJUtils.display(TITLE + " Objects", objects.toProcessor()).setRoi(roi);
    }

    analyse(foci, objects, roi);
  }

  /**
   * Checks the distance and interval define at least 2 radial bins.
   *
   * @param distance the distance
   * @param interval the interval
   * @return true if valid
   */
  static boolean isValidDistance(int distance, double interval) {
    return distance > 0 && interval > 0 && Double.isFinite(interval)
        && (int) (distance / interval) > 1;
  }

  /**
   * Build a list of all the valid mask image names.
   *
   * @return The list of images
   */
  public String[] getImageList() {
    final ArrayList<String> newImageList = new ArrayList<>();
    final int width = imp.getWidth();
    final int height = imp.getHeight();
    for (final int id : ImageJUtils.getIdList()) {
      final ImagePlus image = WindowManager.getImage(id);
      if ((image == null)
          // Same xy dimensions
          || (image.getWidth() != width || image.getHeight() != height)
          // 8/16-bit
          || (image.getBitDepth() == 24 || image.getBitDepth() == 32)) {
        continue;
      }
      newImageList.add(image.getTitle());
    }
    return newImageList.toArray(new String[0]);
  }

  /**
   * Creates the object analyzer from the mask image.
   *
   * @param maskImage the mask image
   * @return the object analyzer
   */
  private static ObjectAnalyzer createObjectAnalyzer(String maskImage) {
    final ImagePlus maskImp = WindowManager.getImage(maskImage);
    if (maskImp == null) {
      IJ.error(TITLE, "No mask");
      return null;
    }
    return new ObjectAnalyzer(maskImp.getProcessor());
  }


  /**
   * Gets the FindFoci results that are within objects.
   *
   * @param roi the roi
   * @param objects the objects
   * @return the foci
   */
  @Nullable
  private Foci[] getFoci(Roi roi, ObjectAnalyzer objects) {
    roi = imp.getRoi();
    final int[] xpoints = roi.getPolygon().xpoints;
    final int[] ypoints = roi.getPolygon().ypoints;
    final LocalList<Foci> foci = new LocalList<>(xpoints.length);
    for (int i = 0, id = 1; i < xpoints.length; i++) {
      final Foci f = createFoci(objects, id, xpoints[i], ypoints[i]);
      if (f != null) {
        foci.add(f);
        id++;
      }
    }
    if (foci.isEmpty()) {
      IJ.error(TITLE, "Zero foci in the results within mask objects");
      return null;
    }
    return foci.toArray(new Foci[foci.size()]);
  }

  /**
   * Gets the FindFoci results that are within objects.
   *
   * @param resultsName the results name
   * @param objects the objects
   * @return the foci
   */
  @Nullable
  private Foci[] getFoci(String resultsName, ObjectAnalyzer objects) {
    final FindFociMemoryResults memoryResults = FindFoci_PlugIn.getResults(resultsName);
    if (memoryResults == null) {
      IJ.error(TITLE, "No foci with the name " + resultsName);
      return null;
    }
    final List<FindFociResult> results = memoryResults.getResults();
    if (results.isEmpty()) {
      IJ.error(TITLE, "Zero foci in the results with the name " + resultsName);
      return null;
    }
    final LocalList<Foci> foci = new LocalList<>(results.size());
    for (int i = 0, id = 1; i < results.size(); i++) {
      final FindFociResult result = results.get(i);
      final Foci f = createFoci(objects, id, result.x, result.y);
      if (f != null) {
        foci.add(f);
        id++;
      }
    }
    if (foci.isEmpty()) {
      IJ.error(TITLE, "Zero foci in the results within mask objects");
      return null;
    }
    return foci.toArray(new Foci[0]);
  }

  /**
   * Creates the foci if the position is within a mask object.
   *
   * @param objects the objects
   * @param id the id
   * @param x the x
   * @param y the y
   * @return the foci (or null)
   */
  @Nullable
  static Foci createFoci(ObjectAnalyzer objects, int id, int x, int y) {
    if (x < 0 || x >= objects.getWidth() || y < 0 || y >= objects.getHeight()) {
      return null;
    }
    final int object = objects.getObjectMask()[y * objects.getWidth() + x];
    return object == 0 ? null : new Foci(id, object, x, y);
  }

  /**
   * Creates the point roi for the foci.
   *
   * @param foci the foci
   * @return the point roi
   */
  private static PointRoi createPointRoi(Foci[] foci) {
    final float[] x = new float[foci.length];
    final float[] y = new float[foci.length];
    for (int ii = 0; ii < foci.length; ii++) {
      final Foci f = foci[ii];
      x[ii] = f.x;
      y[ii] = f.y;
    }
    final PointRoi roi = new PointRoi(x, y);
    roi.setShowLabels(true);
    return roi;
  }

  /**
   * For each foci compute the radial mean of all pixels within the same mask object.
   *
   * @param foci the foci
   * @param objects the objects
   * @param roi the roi (used to show ROI on a radii image)
   */
  private void analyse(Foci[] foci, ObjectAnalyzer objects, PointRoi roi) {
    final TextWindow tw = settings.showTable ? createResultsWindow() : null;
    final StringBuilder sb = new StringBuilder();

    final ObjectCentre[] centres =
        settings.showTable || settings.segmentMode != 0 ? objects.getObjectCentres() : null;

    final int[] mask = objects.getObjectMask();
    final float[] background = getBackground(objects);

    // Show a table of objects: ID, Cx, Cy, Size, Intensity, Mean, Background
    if (settings.showTable) {
      summariseObjects(objects, centres, background);
    }

    // Optionally segment the mask objects using segments of the specified width
    if (settings.segmentMode == MODE_MASK) {
      segmentMask(mask, objects, centres, foci, settings.segmentWidth);
    }

    // For radial intensity
    final float[] pixels = (float[]) imp.getProcessor().toFloat(0, null).getPixels();
    final int distance = settings.distance;
    final int limit = distance * distance;
    final int maxBin = (int) (distance / settings.interval);
    final int[] count = new int[maxBin];
    final double[] sum = new double[maxBin];
    final double[] sumD = new double[maxBin];
    // The lower limit of the squared distance for each bin
    final double[] distances = new double[maxBin];
    for (int i = 0; i < distances.length; i++) {
      distances[i] = MathUtils.pow2(i * settings.interval);
    }

    // Table of dx^2
    final int[] dx2 = new int[2 * distance + 1];

    final int w = imp.getWidth();
    final int upperx = imp.getWidth() - 1;
    final int uppery = imp.getHeight() - 1;

    // Note:
    // Foci may be within the max radius * 2, i.e. the circles overlap.
    // Each pixel that will be counted must first be assigned to its
    // closest foci to avoid double counting. In the event of a tie
    // we have options: ignore; assign to both; assign to first.
    // Here we assign to the first. If foci are sorted by intensity it
    // will create more data for the brightest foci.
    final int[] assigned = new int[mask.length];
    final int[] assignedDistance = new int[mask.length];
    Arrays.fill(assignedDistance, Integer.MAX_VALUE);

    for (int ii = 0; ii < foci.length; ii++) {
      final Foci f = foci[ii];

      // Find limits && clip
      final int minx = Math.max(0, f.x - distance);
      final int maxx = Math.min(upperx, f.x + distance);
      final int miny = Math.max(0, f.y - distance);
      final int maxy = Math.min(uppery, f.y + distance);

      // Table of dx^2
      for (int x = minx, j = 0; x <= maxx; x++, j++) {
        dx2[j] = MathUtils.pow2(f.x - x);
      }

      // Assign foci centre. This overcomes the issue when the segment
      // triangle base does not capture the foci due to floating-point error.
      assignedDistance[f.y * w + f.x] = 0;
      assigned[f.y * w + f.x] = f.id;

      Roi segment = null;
      if (settings.segmentMode == MODE_EXCLUSIVE_MASK) {
        // Only use pixels within the segment
        segment = createSegmentRoi(f, centres[f.object], settings.segmentWidth);
      }

      // For all pixels
      for (int y = miny; y <= maxy; y++) {
        final int dy2 = MathUtils.pow2(f.y - y);
        for (int x = minx, i = y * w + minx, j = 0; x <= maxx; x++, i++, j++) {
          // If correct object (and segment)
          if (mask[i] == f.object && (segment == null || segment.contains(x, y))) {
            // Get distance squared
            final int d2 = dy2 + dx2[j];
            if (d2 < limit && d2 < assignedDistance[i]) {
              assignedDistance[i] = d2;
              assigned[i] = f.id;
            }
          }
        }
      }
    }

    // Plot of each radial intensity
    final Plot plot = (settings.showPlot) ? new Plot(TITLE, "Distance", "Average") : null;
    final double[] xAxis = SimpleArrayUtils.newArray(maxBin, 0, settings.interval);
    final double[] yAxis = new double[xAxis.length];
    final LUT lut = LutHelper.createLut(LutHelper.LutColour.FIRE_GLOW);

    // Image of the radial bins
    ImageProcessor ip;
    if (maxBin < 256) {
      ip = new ByteProcessor(objects.getWidth(), objects.getHeight());
    } else if (maxBin < 1 << 16) {
      ip = new ShortProcessor(objects.getWidth(), objects.getHeight());
    } else {
      ip = new FloatProcessor(objects.getWidth(), objects.getHeight());
    }

    final Statistics[] stats =
        IntStream.range(0, maxBin).mapToObj(i -> new Statistics()).toArray(Statistics[]::new);
    final double[] allD = new double[maxBin];
    // Here we already have the distances for each foci. We could single pass through the
    // assigned array. But we still process each foci so we can output the plot and results
    // table per foci.
    for (int ii = 0; ii < foci.length; ii++) {
      final Foci f = foci[ii];

      // Find limits && clip
      final int minx = Math.max(0, f.x - distance);
      final int maxx = Math.min(upperx, f.x + distance);
      final int miny = Math.max(0, f.y - distance);
      final int maxy = Math.min(uppery, f.y + distance);

      // Reset radial stats
      for (int i = 0, len = count.length; i < len; i++) {
        count[i] = 0;
        sum[i] = 0;
        sumD[i] = 0;
      }

      final float b = background[f.object];

      // For all pixels
      for (int y = miny; y <= maxy; y++) {
        for (int x = minx, i = y * w + minx; x <= maxx; x++, i++) {
          // If correct object
          if (assigned[i] == f.id) {
            // Get distance squared
            final int d2 = assignedDistance[i];
            // Put in radial stats
            // int bin = (int) (Math.sqrt(d2) / interval)
            // Q. Faster than sqrt?
            int bin = Arrays.binarySearch(distances, d2);
            if (bin < 0) {
              // The bin is the (insertion point)-1 => -(bin+1) - 1
              bin = -(bin + 1) - 1;
            }
            count[bin]++;
            sum[bin] += pixels[i];
            sumD[bin] += Math.sqrt(d2);
            stats[bin].add(pixels[i] - b);
            ip.set(i, bin + 1);
          }
        }
      }
      for (int i = 0, len = allD.length; i < len; i++) {
        allD[i] += sumD[i];
      }

      for (int i = 0; i < maxBin; i++) {
        yAxis[i] = count[i] == 0 ? 0 : sum[i] / count[i] - b;
      }

      if (tw != null) {
        // Table of results
        sb.setLength(0);
        sb.append(prefix);
        sb.append('\t').append(f.id);
        sb.append('\t').append(f.object);
        sb.append('\t').append(MathUtils.rounded(b));
        sb.append('\t').append(f.x);
        sb.append('\t').append(f.y);
        for (int i = 0; i < maxBin; i++) {
          if (count[i] == 0) {
            sb.append("\t0");
          } else {
            sb.append('\t').append(MathUtils.rounded(yAxis[i]));
          }
        }
        for (int i = 0; i < maxBin; i++) {
          sb.append('\t').append(count[i]);
        }
        for (int i = 0; i < maxBin; i++) {
          if (count[i] != 0) {
            sb.append('\t').append(MathUtils.rounded(sumD[i] / count[i]));
          } else {
            sb.append("\t-");
          }
        }

        tw.append(sb.toString());
      }

      // Add to plot
      // Use observed average bin distance
      double[] x = xAxis.clone();
      if (plot != null) {
        int xlimit = 0;
        for (int i = 0; i < maxBin; i++) {
          if (count[i] != 0) {
            xlimit = i + 1;
            x[i] = sumD[i] / count[i];
          }
        }

        x = settings.plotDistanceAverages ? x : xAxis;
        plot.setColor(LutHelper.getColour(lut, ii + 1, 0, foci.length));
        if (xlimit < xAxis.length) {
          plot.addPoints(Arrays.copyOf(x, xlimit), Arrays.copyOf(yAxis, xlimit), Plot.LINE);
        } else {
          plot.addPoints(x, yAxis, Plot.LINE);
        }
      }
    }

    if (plot != null) {
      plot.setColor(Color.blue);
      double[] x = xAxis.clone();
      final double[] yError = new double[xAxis.length];
      for (int i = 0; i < maxBin; i++) {
        if (stats[i].getN() != 0) {
          x[i] = allD[i] / stats[i].getN();
          if (stats[i].getN() > 1) {
            yError[i] = stats[i].getConfidenceInterval(0.95);
          }
        }
        yAxis[i] = getMean(stats[i]);
      }
      x = settings.plotDistanceAverages ? x : xAxis;
      plot.addPoints(x, yAxis, yError, Plot.LINE);

      plot.setColor(Color.BLACK);
      ImageJUtils.display(TITLE, plot);
      plot.setLimitsToFit(true); // Seems to only work after drawing
    }

    if (tw != null) {
      sb.setLength(0);
      sb.append(prefix);
      sb.append("\t0\t0\t0\t0\t0");
      for (int i = 0; i < maxBin; i++) {
        sb.append('\t').append(MathUtils.rounded(getMean(stats[i])));
      }
      for (int i = 0; i < maxBin; i++) {
        sb.append('\t').append(stats[i].getN());
      }
      for (int i = 0; i < maxBin; i++) {
        if (stats[i].getN() != 0) {
          sb.append('\t').append(MathUtils.rounded(allD[i] / stats[i].getN()));
        } else {
          sb.append("\t-");
        }
      }

      tw.append(sb.toString());
    }

    if (settings.showRadii) {
      final ImagePlus imp = ImageJUtils.display(TITLE + " radii", ip);
      imp.setLut(LutHelper.createLut(LutColour.FIRE_LIGHT, true));
      imp.setDisplayRange(0, maxBin);
      imp.updateAndDraw();
      if (settings.showFoci) {
        imp.setRoi(roi);
      }
    }
  }

  /**
   * Gets the background using a 3x3 block mean filter. The lowest value in each filtered object is
   * the background.
   *
   * @param objects the objects
   * @return the background
   */
  private float[] getBackground(ObjectAnalyzer objects) {
    final ImageProcessor ip = imp.getProcessor().duplicate();
    ip.convolve3x3(SimpleArrayUtils.newIntArray(9, 1));
    final int[] mask = objects.getObjectMask();
    final int maxObject = objects.getMaxObject();
    final float[] background = new float[maxObject + 1];
    Arrays.fill(background, Float.POSITIVE_INFINITY);
    for (int i = mask.length; i-- > 0;) {
      final int id = mask[i];
      if (id != 0) {
        final float v = ip.getf(i);
        if (background[id] > v) {
          background[id] = v;
        }
      }
    }
    return background;
  }

  /**
   * Segment the mask. For each foci, create a segment (triangle) with the foci located in the
   * centre of the base of the triangle and the centre of the object as one triangle vertex. The
   * base has the specified length. The segment will create a region of pixels from the foci towards
   * the centre of the object.
   *
   * @param mask the mask
   * @param objects the objects
   * @param centres the centres
   * @param foci the foci
   * @param segmentWidth the segment width
   */
  static void segmentMask(int[] mask, ObjectAnalyzer objects, ObjectCentre[] centres,
      Foci[] foci, double segmentWidth) {
    // Note: Object centres use the top-left corner of the pixel as (0,0).
    // Add 0.5 to position the segments around the pixel centre.
    final byte[] within = new byte[mask.length];
    final ByteProcessor ip = new ByteProcessor(objects.getWidth(), objects.getHeight(), within);
    ip.setColor(255);
    for (int ii = 0; ii < foci.length; ii++) {
      final Foci f = foci[ii];
      final ObjectCentre c = centres[f.object];
      final Roi roi = createSegmentRoi(f, c, segmentWidth);
      if (roi == null) {
        // No segment: do not restrict the object
        for (int i = 0; i < mask.length; i++) {
          if (mask[i] == f.object) {
            within[i] = (byte) 255;
          }
        }
      } else {
        ip.fill(roi);
      }
    }
    for (int i = 0; i < mask.length; i++) {
      mask[i] &= within[i];
    }
  }

  @Nullable
  private static Roi createSegmentRoi(Foci f, ObjectCentre c, double segmentWidth) {
    // Construct triangle segment.
    double dx = c.getCentreX() - f.x;
    double dy = c.getCentreY() - f.y;
    // Normalise
    final double norm = Math.hypot(dx, dy);
    if (norm == 0) {
      ImageJUtils.log("Foci (%d,%d) located in object %d centre", f.x, f.y, f.object);
      return null;
    }
    // Create base using half the segment width in each direction
    dx = 0.5 * segmentWidth * dx / norm;
    dy = 0.5 * segmentWidth * dy / norm;
    // Orthogonal vector is:
    // clockwise = dy, -dx
    // anticlockwise = -dy, dx
    final double px = f.x + 0.5;
    final double py = f.y + 0.5;
    final float[] x = {(float) (c.getCentreX() + 0.5), (float) (px + dy), (float) (px - dy)};
    final float[] y = {(float) (c.getCentreY() + 0.5), (float) (py - dx), (float) (py + dx)};
    return new PolygonRoi(x, y, Roi.POLYGON);
  }

  private TextWindow createResultsWindow() {
    // Use a table for each distance and interval as these define the columns
    final String title = createResultsTitle(settings.distance, settings.interval);
    final String header = createResultsHeader();
    final TextWindow textWindow =
        ImageJUtils.refresh(resultsWindows.computeIfAbsent(title, k -> new AtomicReference<>()),
            () -> new TextWindow(title, header, "", 700, 300));
    // The title uses a rounded interval. Check the columns match for intervals that round to the
    // same value. This clears the table.
    if (!header.equals(textWindow.getTextPanel().getColumnHeadings())) {
      textWindow.getTextPanel().setColumnHeadings(header);
    }
    prefix = String.format("%s (C=%d,Z=%d,T=%d)", imp.getTitle(), imp.getChannel(), imp.getSlice(),
        imp.getFrame());
    return textWindow;
  }

  /**
   * Gets the mean of the statistics. Returns zero if there are no values.
   *
   * @param stats the statistics
   * @return the mean
   */
  static double getMean(Statistics stats) {
    return stats.getN() == 0 ? 0 : stats.getMean();
  }

  /**
   * Creates the results title.
   *
   * @param distance the distance
   * @param interval the interval
   * @return the title
   */
  static String createResultsTitle(int distance, double interval) {
    return TITLE + " Summary (Distance=" + distance + ", Interval=" + MathUtils.rounded(interval)
        + ")";
  }

  private String createResultsHeader() {
    return createResultsHeader(settings.distance, settings.interval);
  }

  /**
   * Creates the results header. The final bin extends to the distance.
   *
   * @param distance the distance
   * @param interval the interval
   * @return the header
   */
  static String createResultsHeader(int distance, double interval) {
    final StringBuilder sb = new StringBuilder();
    sb.append("Image");
    sb.append("\tID");
    sb.append("\tObject");
    sb.append("\tBackground");
    sb.append("\tx");
    sb.append("\ty");
    final int maxBin = (int) (distance / interval);
    final String[] ranges = new String[maxBin];
    for (int i = 0; i < maxBin; i++) {
      final double low = interval * i;
      final double high = i == maxBin - 1 ? distance : interval * (i + 1);
      ranges[i] = MathUtils.rounded(low) + "-" + MathUtils.rounded(high);
    }
    for (final String type : new String[] {"Av ", "N ", "D "}) {
      for (final String range : ranges) {
        sb.append('\t').append(type).append(range);
      }
    }
    return sb.toString();
  }

  private void summariseObjects(ObjectAnalyzer objects, ObjectCentre[] centres,
      float[] background) {
    final TextWindow tw = createObjectWindow();
    final StringBuilder sb = new StringBuilder(256);
    final int[] mask = objects.getObjectMask();
    final double[] intensities = new double[centres.length];
    final ImageProcessor ip = imp.getProcessor().duplicate();
    for (int i = 0, len = ip.getWidth() * ip.getHeight(); i < len; i++) {
      intensities[mask[i]] += ip.getf(i);
    }
    try (BufferedTextWindow bw = new BufferedTextWindow(tw)) {
      for (int i = 1; i < centres.length; i++) {
        sb.setLength(0);
        sb.append(prefix).append('\t').append(i).append('\t')
            .append(String.format("%.2f", centres[i].getCentreX())).append('\t')
            .append(String.format("%.2f", centres[i].getCentreY())).append('\t')
            .append(centres[i].getSize()).append('\t').append(intensities[i]).append('\t')
            .append(String.format("%.2f", intensities[i] / centres[i].getSize())).append('\t')
            .append(background[i]);
        bw.append(sb.toString());
      }
    }
  }

  private static TextWindow createObjectWindow() {
    return ImageJUtils.refresh(objectsWindow, () -> new TextWindow(TITLE + " Objects",
        "Image\tObject\tx\ty\tn\tIntensity\tMean\tBackground", "", 700, 300));
  }
}
