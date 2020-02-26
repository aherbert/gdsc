/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2019 Alex Herbert
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
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.gui.PointRoi;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.text.TextWindow;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.annotation.Nullable;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.ij.process.LutHelper;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.core.utils.SimpleArrayUtils;
import uk.ac.sussex.gdsc.core.utils.TurboList;

/**
 * Output the radial intensity around spots within a mask region. Spots are defined using FindFoci
 * results loaded from memory.
 *
 * <p>A mask must be provided as an image.
 */
public class SpotRadialIntensity_PlugIn implements PlugIn {
  private static final String TITLE = "Spot Radial Intensity";
  private static final AtomicReference<TextWindow> resultsWindow = new AtomicReference<>();

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

    String resultsName = "";
    String maskImage = "";
    int distance = 10;
    double interval = 1;
    boolean showFoci;
    boolean showObjects;
    boolean showTable = true;
    boolean showPlot = true;

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
      resultsName = source.resultsName;
      maskImage = source.maskImage;
      distance = source.distance;
      interval = source.interval;
      showFoci = source.showFoci;
      showObjects = source.showObjects;
      showTable = source.showTable;
      showPlot = source.showPlot;
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

    // List the foci results
    final String[] names = FindFoci_PlugIn.getResultsNames();
    if (names == null || names.length == 0) {
      IJ.error(TITLE,
          "Spots must be stored in memory using the " + FindFoci_PlugIn.TITLE + " plugin");
      return;
    }

    imp = WindowManager.getCurrentImage();
    if (imp == null) {
      IJ.noImage();
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

    gd.addChoice("Resultsettings.name", names, settings.resultsName);
    gd.addChoice("Mask", maskImageList, settings.maskImage);
    gd.addNumericField("Distance", settings.distance, 0, 6, "pixels");
    gd.addNumericField("Interval", settings.interval, 2, 6, "pixels");
    gd.addCheckbox("Show_foci", settings.showFoci);
    gd.addCheckbox("Show_objects", settings.showObjects);
    gd.addCheckbox("Show_table", settings.showTable);
    gd.addCheckbox("Show_plot", settings.showPlot);
    gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.FIND_FOCI);

    gd.showDialog();
    if (!gd.wasOKed()) {
      return;
    }

    // Check if these change
    final int distance = settings.distance;
    final double interval = settings.interval;

    settings.resultsName = gd.getNextChoice();
    settings.maskImage = gd.getNextChoice();
    settings.distance = (int) gd.getNextNumber();
    settings.interval = gd.getNextNumber();
    settings.showFoci = gd.getNextBoolean();
    settings.showObjects = gd.getNextBoolean();
    settings.showTable = gd.getNextBoolean();
    settings.showPlot = gd.getNextBoolean();

    settings.save();

    // Validate options
    if (!Double.isFinite(distance) || !Double.isFinite(interval) || distance <= 0 || interval <= 0
        || (int) (distance / interval) <= 1) {
      IJ.error(TITLE, "No valid distances using the given interval");
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
    final Foci[] foci = getFoci(settings.resultsName, objects);
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

    analyse(foci, objects);
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
    final TurboList<Foci> foci = new TurboList<>(results.size());
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
    return foci.toArray(new Foci[foci.size()]);
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
   */
  private void analyse(Foci[] foci, ObjectAnalyzer objects) {
    final TextWindow tw = (settings.showTable) ? createResultsWindow() : null;
    final StringBuilder sb = new StringBuilder();

    final int[] mask = objects.getObjectMask();
    final float[] background = getBackground(objects);

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

    // Plot of each radial intensity
    final Plot plot = (settings.showPlot) ? new Plot(TITLE, "Distance", "Average") : null;
    final double[] xAxis = SimpleArrayUtils.newArray(maxBin, 0, settings.interval);
    final double[] yAxis = new double[xAxis.length];
    final LUT lut = LutHelper.createLut(LutHelper.LutColour.FIRE_GLOW);

    final int w = imp.getWidth();
    final int upperx = imp.getWidth() - 1;
    final int uppery = imp.getHeight() - 1;
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

      // Reset radial stats
      for (int i = 0, len = count.length; i < len; i++) {
        count[i] = 0;
        sum[i] = 0;
      }

      // For all pixels
      for (int y = miny; y <= maxy; y++) {
        final int dy2 = MathUtils.pow2(f.y - y);
        for (int x = minx, i = y * w + minx, j = 0; x <= maxx; x++, i++, j++) {
          // If correct object
          if (mask[i] == f.object) {
            // Get distance squared
            final int d2 = dy2 + dx2[j];
            if (d2 < limit) {
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
            }
          }
        }
      }

      if (tw != null) {
        // Table of results
        sb.setLength(0);
        sb.append(prefix);
        sb.append('\t').append(f.id);
        sb.append('\t').append(f.object);
        final float b = background[f.object];
        sb.append('\t').append(MathUtils.rounded(b));
        sb.append('\t').append(f.x);
        sb.append('\t').append(f.y);
        for (int i = 0; i < maxBin; i++) {
          final double v = sum[i] / count[i] - b * count[i];
          yAxis[i] = v;
          sb.append('\t').append(MathUtils.rounded(v));
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
      plot.setColor(Color.BLACK);
      ImageJUtils.display(TITLE, plot);
      plot.setLimitsToFit(true); // Seems to only work after drawing
    }
  }

  /**
   * Gets the background using a 3x3 block mean.
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
}
