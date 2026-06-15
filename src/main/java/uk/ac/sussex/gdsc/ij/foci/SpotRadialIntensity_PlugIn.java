/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2025 Alex Herbert
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
import ij.gui.GenericDialog;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import uk.ac.sussex.gdsc.core.annotation.Nullable;
import uk.ac.sussex.gdsc.core.ij.BufferedTextWindow;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.ij.process.LutHelper;
import uk.ac.sussex.gdsc.core.ij.process.LutHelper.LutColour;
import uk.ac.sussex.gdsc.core.utils.LocalList;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.core.utils.SimpleArrayUtils;
import uk.ac.sussex.gdsc.core.utils.Statistics;
import uk.ac.sussex.gdsc.ij.UsageTracker;
import uk.ac.sussex.gdsc.ij.foci.ObjectAnalyzer.ObjectCentre;

/**
 * Output the radial intensity around spots within a mask region. Spots are defined using FindFoci
 * results loaded from memory.
 *
 * <p>A mask must be provided as an image.
 */
public class SpotRadialIntensity_PlugIn implements PlugIn {
  private static final String TITLE = "Spot Radial Intensity";
  private static final AtomicReference<TextWindow> resultsWindow = new AtomicReference<>();
  private static final AtomicReference<TextWindow> objectsWindow = new AtomicReference<>();

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
    boolean segment;
    double segmentWidth = 3;
    boolean showFoci;
    boolean showObjects;
    boolean showTable = true;
    boolean showPlot = true;
    boolean showRadii;

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
      segment = source.segment;
      segmentWidth = source.segmentWidth;
      showFoci = source.showFoci;
      showObjects = source.showObjects;
      showTable = source.showTable;
      showPlot = source.showPlot;
      showRadii = source.showRadii;
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

  private static class Foci {
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

    final GenericDialog gd = new GenericDialog(TITLE);

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
    gd.addCheckbox("Segment", settings.segment);
    gd.addNumericField("Segment_width", settings.segmentWidth, 2, 6, "pixels");
    gd.addCheckbox("Show_foci", settings.showFoci);
    gd.addCheckbox("Show_objects", settings.showObjects);
    gd.addCheckbox("Show_table", settings.showTable);
    gd.addCheckbox("Show_plot", settings.showPlot);
    gd.addCheckbox("Show_radii", settings.showRadii);
    gd.addHelp(uk.ac.sussex.gdsc.ij.help.Urls.FIND_FOCI);

    gd.showDialog();
    if (!gd.wasOKed()) {
      return;
    }

    // Check if these change
    final int distance = settings.distance;
    final double interval = settings.interval;

    if (imageRoi != null && names.length != 0) {
      settings.useRoi = gd.getNextBoolean();
    }
    if (names.length != 0) {
      settings.resultsName = gd.getNextChoice();
    }
    settings.maskImage = gd.getNextChoice();
    settings.distance = (int) gd.getNextNumber();
    settings.interval = gd.getNextNumber();
    settings.segment = gd.getNextBoolean();
    settings.segmentWidth = gd.getNextNumber();
    settings.showFoci = gd.getNextBoolean();
    settings.showObjects = gd.getNextBoolean();
    settings.showTable = gd.getNextBoolean();
    settings.showPlot = gd.getNextBoolean();
    settings.showRadii = gd.getNextBoolean();

    settings.save();

    // Validate options
    if (!Double.isFinite(distance) || !Double.isFinite(interval) || distance <= 0 || interval <= 0
        || (int) (distance / interval) <= 1) {
      IJ.error(TITLE, "No valid distances using the given interval");
      return;
    }
    if (settings.segment && settings.segmentWidth < 1) {
      IJ.error(TITLE, "Invalid segment width: " + settings.segmentWidth);
      return;
    }
    if (!(settings.showTable || settings.showPlot)) {
      IJ.error(TITLE, "No output option");
      return;
    }

    if (distance != settings.distance || interval != settings.interval) {
      resultsWindow.set(null); // Create a new window
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
    final int[] mask = objects.getObjectMask();
    final int maxx = imp.getWidth();
    final LocalList<Foci> foci = new LocalList<>(xpoints.length);
    for (int i = 0, id = 1; i < xpoints.length; i++) {
      final int x = xpoints[i];
      final int y = ypoints[i];
      final int object = mask[y * maxx + x];
      if (object != 0) {
        foci.add(new Foci(id++, object, x, y));
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
    final int[] mask = objects.getObjectMask();
    final int maxx = imp.getWidth();
    final LocalList<Foci> foci = new LocalList<>(results.size());
    for (int i = 0, id = 1; i < results.size(); i++) {
      final FindFociResult result = results.get(i);
      final int object = mask[result.y * maxx + result.x];
      if (object != 0) {
        foci.add(new Foci(id++, object, result.x, result.y));
      }
    }
    if (foci.isEmpty()) {
      IJ.error(TITLE, "Zero foci in the results within mask objects");
      return null;
    }
    return foci.toArray(new Foci[0]);
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
    final TextWindow tw = (settings.showTable) ? createResultsWindow() : null;
    final StringBuilder sb = new StringBuilder();

    final int[] mask = objects.getObjectMask();
    final float[] background = getBackground(objects);

    // Show a table of objects: ID, Cx, Cy, Size, Intensity, Mean, Background
    if (settings.showTable) {
      summariseObjects(objects, background);
    }

    // Optionally segment the mask objects using segments of the specified width
    if (settings.segment) {
      segmentMask(mask, objects, foci);
    }

    // For radial intensity
    final float[] pixels = (float[]) imp.getProcessor().toFloat(0, null).getPixels();
    final int distance = settings.distance;
    final int limit = distance * distance;
    final int maxBin = (int) (distance / settings.interval);
    final int[] count = new int[maxBin];
    final double[] sum = new double[maxBin];
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

      // For all pixels
      for (int y = miny; y <= maxy; y++) {
        final int dy2 = MathUtils.pow2(f.y - y);
        for (int x = minx, i = y * w + minx, j = 0; x <= maxx; x++, i++, j++) {
          // If correct object
          if (mask[i] == f.object) {
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
    // Here we already have the distances for each foci. We could single pass through the
    // asiigned array. But we still process each foci so we can output the plot and results
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
            stats[bin].add(pixels[i] - b);
            ip.set(i, bin + 1);
          }
        }
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
            yAxis[i] = 0;
            sb.append("\t0");
          } else {
            final double v = sum[i] / count[i] - b;
            yAxis[i] = v;
            sb.append('\t').append(MathUtils.rounded(v));
          }
        }
        for (int i = 0; i < maxBin; i++) {
          sb.append('\t').append(count[i]);
        }

        tw.append(sb.toString());
      }

      // Add to plot
      if (plot != null) {
        int xlimit = 0;
        for (int i = 0; i < maxBin; i++) {
          if (count[i] != 0) {
            xlimit = i + 1;
          }
        }

        plot.setColor(LutHelper.getColour(lut, ii + 1, 0, foci.length));
        if (xlimit < xAxis.length) {
          plot.addPoints(Arrays.copyOf(xAxis, xlimit), Arrays.copyOf(yAxis, xlimit), Plot.LINE);
        } else {
          plot.addPoints(xAxis, yAxis, Plot.LINE);
        }
      }
    }

    if (plot != null) {
      plot.setColor(Color.blue);
      final double[] yError = new double[xAxis.length];
      for (int i = 0; i < maxBin; i++) {
        yAxis[i] = stats[i].getMean();
        yError[i] = stats[i].getConfidenceInterval(0.95);
      }
      plot.addPoints(xAxis, yAxis, yError, Plot.LINE);

      plot.setColor(Color.BLACK);
      ImageJUtils.display(TITLE, plot);
      plot.setLimitsToFit(true); // Seems to only work after drawing
    }

    if (tw != null) {
      sb.setLength(0);
      sb.append(prefix);
      sb.append("\t0\t0\t0\t0\t0");
      for (int i = 0; i < maxBin; i++) {
        sb.append('\t').append(MathUtils.rounded(stats[i].getMean()));
      }
      for (int i = 0; i < maxBin; i++) {
        sb.append('\t').append(MathUtils.rounded(stats[i].getN()));
      }

      tw.append(sb.toString());
    }

    if (settings.showRadii) {
      final ImagePlus imp = ImageJUtils.display(TITLE + " radii", ip);
      if (settings.showFoci) {
        imp.setRoi(roi);
        imp.setLut(LutHelper.createLut(LutColour.FIRE_LIGHT, true));
        imp.setDisplayRange(0, maxBin);
        imp.updateAndDraw();
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
   * @param foci the foci
   */
  private void segmentMask(int[] mask, ObjectAnalyzer objects, Foci[] foci) {
    // Note: Object centres use the top-left corner of the pixel as (0,0).
    // Add 0.5 to position the segments around the pixel centre.
    final ObjectCentre[] centres = objects.getObjectCentres();
    final byte[] within = new byte[mask.length];
    final ByteProcessor ip = new ByteProcessor(objects.getWidth(), objects.getHeight(), within);
    ip.setColor(255);
    for (int ii = 0; ii < foci.length; ii++) {
      final Foci f = foci[ii];
      final ObjectCentre c = centres[f.object];
      // Construct triangle segment.
      double dx = c.getCentreX() - f.x;
      double dy = c.getCentreY() - f.y;
      // Normalise
      final double norm = Math.hypot(dx, dy);
      if (norm == 0) {
        ImageJUtils.log("Foci (%d,%d) located in object %d centre", f.x, f.y, f.object);
        continue;
      }
      // Create base using half the segment width in each direction
      dx = 0.5 * settings.segmentWidth * dx / norm;
      dy = 0.5 * settings.segmentWidth * dy / norm;
      // Orthogonal vectpr is:
      // clockwise = dy, -dx
      // anticlockwise = -dy, dx
      final double px = f.x + 0.5;
      final double py = f.y + 0.5;
      final float[] x = {(float) (c.getCentreX() + 0.5), (float) (px + dy), (float) (px - dy)};
      final float[] y = {(float) (c.getCentreY() + 0.5), (float) (py - dx), (float) (py + dx)};
      final PolygonRoi roi = new PolygonRoi(x, y, Roi.POLYGON);
      ip.fill(roi);
    }
    for (int i = 0; i < mask.length; i++) {
      mask[i] &= within[i];
    }
  }

  private TextWindow createResultsWindow() {
    final TextWindow textWindow = ImageJUtils.refresh(resultsWindow,
        () -> new TextWindow(TITLE + " Summary", createResultsHeader(), "", 700, 300));
    prefix = String.format("%s (C=%d,Z=%d,T=%d)", imp.getTitle(), imp.getChannel(), imp.getSlice(),
        imp.getFrame());
    return textWindow;
  }

  private String createResultsHeader() {
    final StringBuilder sb = new StringBuilder();
    sb.append("Image");
    sb.append("\tID");
    sb.append("\tObject");
    sb.append("\tBackground");
    sb.append("\tx");
    sb.append("\ty");
    final int maxBin = (int) (settings.distance / settings.interval);
    for (int i = 0; i < maxBin; i++) {
      final double low = settings.interval * i;
      final double high = settings.interval * (i + 1);
      sb.append("\tAv ").append(MathUtils.rounded(low));
      sb.append("-").append(MathUtils.rounded(high));
    }
    for (int i = 0; i < maxBin; i++) {
      final double low = settings.interval * i;
      final double high = settings.interval * (i + 1);
      sb.append("\tN ").append(MathUtils.rounded(low));
      sb.append("-").append(MathUtils.rounded(high));
    }
    return sb.toString();
  }

  private void summariseObjects(ObjectAnalyzer objects, float[] background) {
    final TextWindow tw = createObjectWindow();
    final StringBuilder sb = new StringBuilder(256);
    final ObjectCentre[] centres = objects.getObjectCentres();
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
