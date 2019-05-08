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

import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.annotation.Nullable;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.core.utils.TextUtils;
import uk.ac.sussex.gdsc.foci.ObjectAnalyzer.ObjectCentre;
import uk.ac.sussex.gdsc.help.UrlUtils;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.PointRoi;
import ij.plugin.filter.PlugInFilter;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.text.TextWindow;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.awt.Color;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Finds objects in an image using contiguous pixels of the same value. Locates the closest object
 * to each Find Foci result held in memory and summarises the counts.
 */
public class AssignFociToObjects_PlugIn implements PlugInFilter {
  /** The title of the plugin. */
  private static final String TITLE = "Assign Foci";

  private static final int FLAGS = DOES_16 + DOES_8G + NO_CHANGES + NO_UNDO;

  private static AtomicReference<TextWindow> resultsWindowRef = new AtomicReference<>();
  private static AtomicReference<TextWindow> summaryWindowRef = new AtomicReference<>();
  private static AtomicReference<TextWindow> distancesWindowRef = new AtomicReference<>();

  private TextWindow resultsWindow;
  private TextWindow summaryWindow;
  private TextWindow distancesWindow;

  private ImagePlus imp;
  private ArrayList<int[]> results;

  // The distance grid is only modified in a synchronized block
  private static Object lock = new Object();
  private static int size;
  private static ArrayList<Search[]> distanceGrid;

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
    double radius;
    int minSize;
    int maxSize;
    boolean eightConnected;
    boolean removeSmallObjects;
    boolean showObjects;
    boolean showFoci;
    boolean showDistances;
    boolean showHistogram;

    Settings() {
      input = "";
      radius = 30;
      removeSmallObjects = true;
    }

    Settings(Settings source) {
      input = source.input;
      radius = source.radius;
      minSize = source.minSize;
      maxSize = source.maxSize;
      eightConnected = source.eightConnected;
      removeSmallObjects = source.removeSmallObjects;
      showObjects = source.showObjects;
      showFoci = source.showFoci;
      showDistances = source.showDistances;
      showHistogram = source.showHistogram;
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

  private static class Search {
    int x;
    int y;
    double d2;

    public Search(int x, int y, double d2) {
      this.x = x;
      this.y = y;
      this.d2 = d2;
    }
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

    final ArrayList<Search[]> searchGrid = createDistanceGrid(settings.radius);

    createResultsTables();

    final ObjectAnalyzer oa = new ObjectAnalyzer(ip);
    if (settings.removeSmallObjects) {
      oa.setMinObjectSize(settings.minSize);
    }
    oa.setEightConnected(settings.eightConnected);
    final int[] objectMask = oa.getObjectMask();

    final int maxx = ip.getWidth();
    final int maxy = ip.getHeight();

    final double maxD2 = settings.radius * settings.radius;

    // Assign each foci to the nearest object
    final int[] count = new int[oa.getMaxObject() + 1];
    final int[] found = new int[results.size()];
    final double[] d2 = new double[found.length];
    Arrays.fill(d2, -1);
    for (int i = 0; i < results.size(); i++) {
      final int[] result = results.get(i);
      final int x = result[0];
      final int y = result[1];
      // Check within the image
      if (x < 0 || x >= maxx || y < 0 || y >= maxy) {
        continue;
      }

      int index = y * maxx + x;
      if (objectMask[index] != 0) {
        count[objectMask[index]]++;
        found[i] = objectMask[index];
        d2[i] = 0;
        continue;
      }

      // Search for the closest object(s)
      final int[] closestCount = new int[count.length];

      // Scan wider and wider from 0,0 until we find an object.
      for (final Search[] next : searchGrid) {
        if (next[0].d2 > maxD2) {
          break;
        }
        boolean ok = false;
        for (final Search s : next) {
          final int xx = x + s.x;
          final int yy = y + s.y;
          if (xx < 0 || xx >= maxx || yy < 0 || yy >= maxy) {
            continue;
          }
          index = yy * maxx + xx;
          if (objectMask[index] != 0) {
            ok = true;
            closestCount[objectMask[index]]++;
          }
        }
        if (ok) {
          // Get the object with the highest count
          int maxCount = 0;
          for (int j = 1; j < closestCount.length; j++) {
            if (closestCount[maxCount] < closestCount[j]) {
              maxCount = j;
            }
          }
          // Assign
          count[maxCount]++;
          found[i] = maxCount;
          d2[i] = next[0].d2;
          break;
        }
      }
    }

    final ObjectCentre[] centres = oa.getObjectCentres();

    // We must ignore those that are too small/big
    final int[] idMap = new int[count.length];
    for (int i = 1; i < count.length; i++) {
      idMap[i] = i;
      if (centres[i].getSize() < settings.minSize
          || (settings.maxSize != 0 && centres[i].getSize() > settings.maxSize)) {
        idMap[i] = -i;
      }
    }

    // TODO - Remove objects from the output image ?
    showMask(oa, found, idMap);

    // Show the results
    final DescriptiveStatistics stats = new DescriptiveStatistics();
    final DescriptiveStatistics stats2 = new DescriptiveStatistics();
    final StringBuilder sb = new StringBuilder();
    for (int i = 1, j = 0; i < count.length; i++) {
      sb.append(imp.getTitle());
      sb.append('\t').append(i);
      sb.append('\t').append(MathUtils.rounded(centres[i].getCentreX()));
      sb.append('\t').append(MathUtils.rounded(centres[i].getCentreY()));
      sb.append('\t').append(centres[i].getSize());
      if (idMap[i] > 0) {
        // Include this object
        sb.append("\tTrue");
        stats.addValue(count[i]);
      } else {
        // Exclude this object
        sb.append("\tFalse");
        stats2.addValue(count[i]);
      }
      sb.append('\t').append(count[i]);
      sb.append('\n');

      // Flush before 10 lines to ensure auto-layout of columns
      if (i >= 9 && j++ == 0) {
        resultsWindow.append(sb.toString());
        sb.setLength(0);
      }
    }
    resultsWindow.append(sb.toString());

    // Histogram the count
    if (settings.showHistogram) {
      final int max = (int) stats.getMax();
      final double[] xvalues = new double[max + 1];
      final double[] yvalues = new double[xvalues.length];
      for (int i = 1; i < count.length; i++) {
        if (idMap[i] > 0) {
          yvalues[count[i]]++;
        }
      }
      double ymax = 0;
      for (int i = 0; i <= max; i++) {
        xvalues[i] = i;
        if (ymax < yvalues[i]) {
          ymax = yvalues[i];
        }
      }
      final String title = TITLE + " Histogram";
      final Plot plot = new Plot(title, "Count", "Frequency");
      plot.addPoints(xvalues, yvalues, Plot.LINE);
      plot.setLimits(0, xvalues[xvalues.length - 1], 0, ymax);
      plot.addLabel(0, 0, String.format("N = %d, Mean = %s", (int) stats.getSum(),
          MathUtils.rounded(stats.getMean())));
      plot.draw();
      plot.setColor(Color.RED);
      plot.drawLine(stats.getMean(), 0, stats.getMean(), ymax);
      ImageJUtils.display(title, plot);
    }

    // Show the summary
    sb.setLength(0);
    sb.append(imp.getTitle());
    sb.append('\t').append(oa.getMaxObject());
    sb.append('\t').append(stats.getN());
    sb.append('\t').append(results.size());
    sb.append('\t').append((int) stats.getSum());
    sb.append('\t').append((int) stats2.getSum());
    sb.append('\t').append(results.size() - (int) (stats.getSum() + stats2.getSum()));
    sb.append('\t').append(stats.getMin());
    sb.append('\t').append(stats.getMax());
    sb.append('\t').append(MathUtils.rounded(stats.getMean()));
    sb.append('\t').append(MathUtils.rounded(stats.getPercentile(50)));
    sb.append('\t').append(MathUtils.rounded(stats.getStandardDeviation()));
    summaryWindow.append(sb.toString());

    if (!settings.showDistances) {
      return;
    }

    sb.setLength(0);
    for (int i = 0, j = 0; i < results.size(); i++) {
      final int[] result = results.get(i);
      final int x = result[0];
      final int y = result[1];

      // Check within the image
      if (x < 0 || x >= maxx || y < 0 || y >= maxy) {
        continue;
      }

      sb.append(imp.getTitle());
      sb.append('\t').append(i + 1);
      sb.append('\t').append(x);
      sb.append('\t').append(y);
      if (found[i] > 0) {
        sb.append('\t').append(found[i]);
        if (idMap[found[i]] > 0) {
          sb.append("\tTrue\t");
        } else {
          sb.append("\tFalse\t");
        }
        sb.append(MathUtils.rounded(Math.sqrt(d2[i])));
        sb.append('\n');
      } else {
        sb.append("\t\t\t\n");
      }

      // Flush before 10 lines to ensure auto-layout of columns
      if (i >= 9 && j++ == 0) {
        distancesWindow.append(sb.toString());
        sb.setLength(0);
      }
    }
    distancesWindow.append(sb.toString());
  }

  private boolean showDialog() {
    final ArrayList<int[]> findFociResults = getFindFociResults();
    final ArrayList<int[]> roiResults = getRoiResults();
    if (findFociResults == null && roiResults == null) {
      IJ.error(TITLE,
          "No " + FindFoci_PlugIn.TITLE + " results in memory or point ROI on the image");
      return false;
    }

    final GenericDialog gd = new GenericDialog(TITLE);
    gd.addHelp(UrlUtils.FIND_FOCI);

    String[] options = new String[2];
    int count = 0;

    final StringBuilder msg = new StringBuilder(
        "Assign foci to the nearest object\n(Objects will be found in the current image)\n"
            + "Available foci:");
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

    settings = Settings.load();
    gd.addChoice("Foci", options, settings.input);
    gd.addSlider("Radius", 5, 50, settings.radius);
    gd.addNumericField("Min_size", settings.minSize, 0);
    gd.addNumericField("Max_size", settings.maxSize, 0);
    gd.addCheckbox("Eight_connected", settings.eightConnected);
    gd.addCheckbox("Remove_small_objects", settings.removeSmallObjects);
    gd.addCheckbox("Show_objects", settings.showObjects);
    gd.addCheckbox("Show_foci", settings.showFoci);
    gd.addCheckbox("Show_distances", settings.showDistances);
    gd.addCheckbox("Show_histogram", settings.showHistogram);

    gd.showDialog();
    if (gd.wasCanceled()) {
      return false;
    }

    settings.input = gd.getNextChoice();
    settings.radius = Math.abs(gd.getNextNumber());
    settings.minSize = Math.abs((int) gd.getNextNumber());
    settings.maxSize = Math.abs((int) gd.getNextNumber());
    settings.eightConnected = gd.getNextBoolean();
    settings.removeSmallObjects = gd.getNextBoolean();
    settings.showObjects = gd.getNextBoolean();
    settings.showFoci = gd.getNextBoolean();
    settings.showDistances = gd.getNextBoolean();
    settings.showHistogram = gd.getNextBoolean();
    settings.save();

    // Load objects
    results = (settings.input.equalsIgnoreCase("ROI")) ? roiResults : findFociResults;
    if (results == null) {
      IJ.error(TITLE, "No foci could be loaded");
      return false;
    }

    return true;
  }

  @Nullable
  private static ArrayList<int[]> getFindFociResults() {
    final List<FindFociResult> lastResults = FindFoci_PlugIn.getLastResults();
    if (lastResults == null) {
      return null;
    }
    final ArrayList<int[]> list = new ArrayList<>(lastResults.size());
    for (final FindFociResult result : lastResults) {
      list.add(new int[] {result.x, result.y});
    }
    return list;
  }

  @Nullable
  private ArrayList<int[]> getRoiResults() {
    final AssignedPoint[] points = AssignedPointUtils.extractRoiPoints(imp.getRoi());
    if (points.length == 0) {
      return null;
    }
    final ArrayList<int[]> list = new ArrayList<>(points.length);
    for (final AssignedPoint point : points) {
      list.add(new int[] {point.x, point.y});
    }
    return list;
  }

  private static ArrayList<Search[]> createDistanceGrid(double radius) {
    final int n = (int) Math.ceil(radius);
    final int newSize = 2 * n + 1;

    // Ensure thread-safe state
    synchronized (lock) {
      if (size >= newSize) {
        return distanceGrid;
      }

      // Create a new grid
      size = newSize;
      distanceGrid = new ArrayList<>();
      final Search[] s = new Search[size * size];

      final double[] tmp = new double[newSize];
      for (int y = -n, i = 0; y <= n; y++, i++) {
        tmp[i] = (double) y * y;
      }

      for (int y = -n, i = 0, index = 0; y <= n; y++, i++) {
        final double y2 = tmp[i];
        for (int x = -n, ii = 0; x <= n; x++, ii++) {
          s[index++] = new Search(x, y, y2 + tmp[ii]);
        }
      }

      Arrays.sort(s, (s1, s2) -> Double.compare(s1.d2, s2.d2));

      // Store each increasing search distance as a set
      // Ignore first record as it is d2==0
      double last = -1;
      int begin = 0;
      int end = -1;
      for (int i = 1; i < s.length; i++) {
        if (last != s[i].d2) {
          if (end != -1) {
            final int length = end - begin + 1;
            final Search[] next = new Search[length];
            System.arraycopy(s, begin, next, 0, length);
            distanceGrid.add(next);
          }
          begin = i;
        }
        end = i;
        last = s[i].d2;
      }

      if (end != -1) {
        final int length = end - begin + 1;
        final Search[] next = new Search[length];
        System.arraycopy(s, begin, next, 0, length);
        distanceGrid.add(next);
      }
      return distanceGrid;
    }
  }

  private void createResultsTables() {
    resultsWindow =
        createWindow(resultsWindowRef, "Results", "Image\tObject\tcx\tcy\tSize\tValid\tCount");
    summaryWindow = createWindow(summaryWindowRef, "Summary",
        "Image\tnObjects\tValid\tnFoci\tIn\tIgnored\tOut\tMin\tMax\tAv\tMed\tSD");
    final Point p1 = resultsWindow.getLocation();
    final Point p2 = summaryWindow.getLocation();
    if (p1.x == p2.x && p1.y == p2.y) {
      p2.y += resultsWindow.getHeight();
      summaryWindow.setLocation(p2);
    }
    if (settings.showDistances) {
      distancesWindow = createWindow(distancesWindowRef, "Distances",
          "Image\tFoci\tx\ty\tObject\tValid\tDistance");
      final Point p3 = distancesWindow.getLocation();
      if (p1.x == p3.x && p1.y == p3.y) {
        p3.x += 50;
        p3.y += 50;
        distancesWindow.setLocation(p3);
      }
    }
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

  private void showMask(ObjectAnalyzer oa, int[] found, int[] idMap) {
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

    ip.setMinAndMax(0, oa.getMaxObject());
    final ImagePlus objectImp = ImageJUtils.display(TITLE + " Objects", ip);
    if (settings.showFoci && found.length > 0) {
      final int[] xin = new int[found.length];
      final int[] yin = new int[found.length];
      final int[] xremove = new int[found.length];
      final int[] yremove = new int[found.length];
      final int[] xout = new int[found.length];
      final int[] yout = new int[found.length];
      int in = 0;
      int remove = 0;
      int out = 0;
      for (int i = 0; i < found.length; i++) {
        final int[] xy = results.get(i);
        final int id = idMap[found[i]];
        if (id > 0) {
          xin[in] = xy[0];
          yin[in++] = xy[1];
        } else if (id < 0) {
          xremove[remove] = xy[0];
          yremove[remove++] = xy[1];
        } else {
          xout[out] = xy[0];
          yout[out++] = xy[1];
        }
      }

      final Overlay o = new Overlay();
      addRoi(xin, yin, in, o, Color.GREEN);
      addRoi(xremove, yremove, remove, o, Color.YELLOW);
      addRoi(xout, yout, out, o, Color.RED);
      objectImp.setOverlay(o);
    }
  }

  private static void addRoi(int[] x, int[] y, int points, Overlay ooverlay, Color color) {
    final PointRoi roi = new PointRoi(x, y, points);
    roi.setShowLabels(false);
    roi.setFillColor(color);
    roi.setStrokeColor(color);
    ooverlay.add(roi);
  }
}
