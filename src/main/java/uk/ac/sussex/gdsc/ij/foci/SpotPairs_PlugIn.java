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
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import ij.text.TextWindow;
import java.awt.AWTEvent;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.concurrent.atomic.AtomicReference;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.trees.DoubleDistanceFunctions;
import uk.ac.sussex.gdsc.core.trees.IntDoubleKdTree;
import uk.ac.sussex.gdsc.core.trees.KdTrees;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.ij.UsageTracker;
import uk.ac.sussex.gdsc.ij.help.HelpUrls;

/**
 * Analyses marked ROI points in an image. Find the closest pairs within a set distance of each
 * other.
 *
 * <p>Analysis uses 2D XY coordinates.
 */
public class SpotPairs_PlugIn implements ExtendedPlugInFilter, DialogListener {

  private static final String TITLE = "Spot Pairs";
  private static AtomicReference<TextWindow> resultsWindowRef = new AtomicReference<>();

  private ImagePlus imp;

  private Calibration cal;
  private AssignedPoint[] points;
  private ArrayList<Cluster> candidates;
  private boolean addedOverlay;

  /** The current settings for the plugin instance. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    double radius = 10;
    boolean addOverlay = true;
    boolean killRoi;
    ImagePlus lastImp;
    /** Cache the ROI when we remove it so it can be reused. */
    Roi lastRoi;

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
      radius = source.radius;
      addOverlay = source.addOverlay;
      killRoi = source.killRoi;
      lastImp = source.lastImp;
      lastRoi = source.lastRoi;
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
   * Used to store information about a cluster in the clustering analysis.
   */
  static class Cluster {
    double x;
    double y;
    double sumx;
    double sumy;
    int size;

    // Used to construct a single linked list of cluster points
    ClusterPoint head;

    Cluster(ClusterPoint point) {
      point.next = null;
      head = point;
      this.x = sumx = point.x;
      this.y = sumy = point.y;
      size = 1;
    }

    void add(Cluster other) {
      // Do not check if the other cluster is null or has no points

      // Add to this list
      // Find the tail of the shortest list
      ClusterPoint big;
      ClusterPoint small;
      if (size < other.size) {
        small = head;
        big = other.head;
      } else {
        small = other.head;
        big = head;
      }

      ClusterPoint tail = small;
      while (tail.next != null) {
        tail = tail.next;
      }

      // Join the small list to the long list
      tail.next = big;
      head = small;

      // Find the new centroid
      sumx += other.sumx;
      sumy += other.sumy;
      size += other.size;
      x = sumx / size;
      y = sumy / size;

      // Free the other cluster
      other.clear();
    }

    private void clear() {
      head = null;
      size = 0;
      x = y = sumx = sumy = 0;
    }

    /**
     * Sorts the points in ID order. This only works for the first two points in the list.
     */
    void sort() {
      if (size < 2) {
        return;
      }
      final ClusterPoint p1 = head;
      final ClusterPoint p2 = p1.next;
      if (p2.id < p1.id) {
        head = p2;
        p1.next = p2.next;
        p2.next = p1;
      }
    }
  }

  /**
   * Used to store information about a point in the clustering analysis.
   */
  static class ClusterPoint {
    double x;
    double y;
    int id;

    // Used to construct a single linked list of points
    ClusterPoint next;

    ClusterPoint(int id, double x, double y) {
      this.id = id;
      this.x = x;
      this.y = y;
    }

    double distance(ClusterPoint other) {
      final double dx = x - other.x;
      final double dy = y - other.y;
      return Math.sqrt(dx * dx + dy * dy);
    }
  }

  /** {@inheritDoc} */
  @Override
  public int setup(String arg, ImagePlus imp) {
    if ("final".equals(arg)) {
      finalProcessing(imp);
      return DONE;
    }

    UsageTracker.recordPlugin(this.getClass(), arg);

    if (imp == null) {
      return DONE;
    }
    this.imp = imp;
    Roi roi = imp.getRoi();

    // If there is no ROI it may be because we removed it last time
    settings = Settings.load();
    if (roi == null && settings.lastImp == imp) {
      // Re-use the saved ROI from last time
      roi = settings.lastRoi;
      imp.setRoi(roi);
    }

    points = AssignedPointUtils.extractRoiPoints(imp);
    if (points.length < 2) {
      IJ.error(TITLE, "Please mark at least two ROI points on the image");
      return DONE;
    }

    settings.lastImp = imp;
    settings.lastRoi = roi;

    cal = imp.getCalibration();

    return DOES_ALL | FINAL_PROCESSING;
  }

  private void finalProcessing(ImagePlus imp) {
    // All state is already created.
    for (final Cluster c : candidates) {
      c.sort();
    }

    Collections.sort(candidates, (o1, o2) -> {
      // Put the pairs first
      if (o1.size > o2.size) {
        return -1;
      }
      if (o1.size < o2.size) {
        return 1;
      }

      // Sort by the first point ID
      return Integer.compare(o1.head.id, o2.head.id);
    });

    // Show the results in a table
    final TextWindow resultsWindow = createResultsWindow(cal);

    // Report the results
    resultsWindow.append(imp.getTitle());
    final StringBuilder sb = new StringBuilder();
    try (Formatter formatter = new Formatter(sb)) {
      for (final Cluster c : candidates) {
        sb.setLength(0);
        addResult(formatter, resultsWindow, c);
      }
    }

    // The final processing mode of ImageJ restores the image ROI so kill it again
    if (settings.killRoi) {
      imp.killRoi();
    }
  }

  private static TextWindow createResultsWindow(Calibration cal) {
    final String header = createResultsHeader(cal);
    TextWindow tw = ImageJUtils.refresh(resultsWindowRef,
        () -> new TextWindow(TITLE, header, "", 600, 500));
    if (!header.equals(tw.getTextPanel().getColumnHeadings())) {
      // The calibrated units have changed. Create a new table.
      tw = new TextWindow(TITLE, header, "", 600, 500);
      resultsWindowRef.set(tw);
    }
    return tw;
  }

  private static String createResultsHeader(Calibration cal) {
    final StringBuilder sb = new StringBuilder();
    sb.append("Image\t");
    sb.append("Id1\t");
    sb.append("X1\t");
    sb.append("Y1\t");
    sb.append("Id2\t");
    sb.append("X2\t");
    sb.append("Y2\t");
    sb.append("Distance\t");
    sb.append("Distance (").append(cal.getXUnit()).append(")");
    return sb.toString();
  }

  private void addResult(Formatter formatter, TextWindow resultsWindow, Cluster cluster) {
    final ClusterPoint p1 = cluster.head;
    if (cluster.size == 1) {
      formatter.format("\t%d\t%.0f\t%.0f", p1.id, p1.x, p1.y);
    } else {
      final ClusterPoint p2 = p1.next;
      final double d = p1.distance(p2);
      formatter.format("\t%d\t%.0f\t%.0f\t%d\t%.0f\t%.0f\t%s\t%s", p1.id, p1.x, p1.y, p2.id, p2.x,
          p2.y, MathUtils.rounded(d), MathUtils.rounded(d * cal.pixelWidth));
    }
    resultsWindow.append(formatter.toString());
  }

  /** {@inheritDoc} */
  @Override
  public void run(ImageProcessor ip) {
    candidates = findPairs(points, settings.radius);

    if (settings.addOverlay) {
      final Overlay overlay = new Overlay();
      // Add the original points
      final Roi pointRoi = AssignedPointUtils.createRoi(points);
      pointRoi.setStrokeColor(Color.orange);
      pointRoi.setFillColor(Color.white);
      overlay.add(pointRoi);
      for (final Cluster c : candidates) {
        if (c.size == 2) {
          // Draw a line between pairs
          final ClusterPoint p1 = c.head;
          final ClusterPoint p2 = p1.next;
          final Line line = new Line(p1.x, p1.y, p2.x, p2.y);
          line.setStrokeColor(Color.magenta);
          line.setStrokeWidth(2);
          overlay.add(line);
        }
      }
      imp.setOverlay(overlay);
      imp.updateAndDraw();
      addedOverlay = true;

      // Remove the point ROI to allow the overlay to be seen
      if (settings.killRoi) {
        imp.killRoi(); // Saves the ROI so it can be restored
      }
    } else {
      imp.setOverlay(null);
    }
  }

  /**
   * Find the pairs of points within the radius distance.
   *
   * <p>Two points are paired if each point is the closest point to the other and the distance is
   * below the radius. Ties in the distance to the closest point are resolved using the lowest point
   * ID. The result is independent of the order of the points.
   *
   * @param points the points
   * @param radius the radius
   * @return the clusters (pairs and single points)
   */
  static ArrayList<Cluster> findPairs(AssignedPoint[] points, double radius) {
    final int n = points.length;
    final IntDoubleKdTree tree = KdTrees.newIntDoubleKdTree(2);
    for (int i = 0; i < n; i++) {
      tree.add(new double[] {points[i].x, points[i].y}, i);
    }

    // Find the closest point within the radius for each point
    final double r2 = radius * radius;
    final int[] closest = new int[n];
    final double[] min = new double[1];
    for (int i = 0; i < n; i++) {
      final int index = i;
      closest[i] = -1;
      min[0] = r2;
      tree.findNeighbours(new double[] {points[i].x, points[i].y}, r2,
          DoubleDistanceFunctions.SQUARED_EUCLIDEAN_2D, (j, d2) -> {
            if (j == index) {
              return;
            }
            if (d2 < min[0]
                || (d2 == min[0] && closest[index] >= 0 && points[j].id < points[closest[index]].id)) {
              min[0] = d2;
              closest[index] = j;
            }
          });
    }

    // Pair mutual closest points
    final ArrayList<Cluster> clusters = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      final int j = closest[i];
      if (j < 0 || closest[j] != i) {
        clusters.add(createCluster(points[i]));
      } else if (i < j) {
        final Cluster c = createCluster(points[i]);
        c.add(createCluster(points[j]));
        clusters.add(c);
      }
    }
    return clusters;
  }

  private static Cluster createCluster(AssignedPoint p) {
    return new Cluster(new ClusterPoint(p.id, p.x, p.y));
  }

  /** {@inheritDoc} */
  @Override
  public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
    final GenericDialog gd = new GenericDialog(TITLE);
    gd.addHelp(HelpUrls.getUrl("spot-pairs"));

    gd.addMessage("Find the closest pairs of marked ROI points");

    gd.addSlider("Radius", 5, 50, settings.radius);
    gd.addCheckbox("Add_overlay", settings.addOverlay);
    gd.addCheckbox("Kill_ROI", settings.killRoi);

    gd.addPreviewCheckbox(pfr);
    gd.addDialogListener(this);
    gd.showDialog();
    final boolean cancelled = gd.wasCanceled() || !dialogItemChanged(gd, null);

    settings.save();

    if (cancelled) {
      // Remove any overlay we added
      if (addedOverlay) {
        imp.setOverlay(null);
      }
      return DONE;
    }

    return DOES_ALL | FINAL_PROCESSING;
  }

  /** {@inheritDoc} */
  @Override
  public boolean dialogItemChanged(GenericDialog gd, AWTEvent event) {
    settings.radius = gd.getNextNumber();
    settings.addOverlay = gd.getNextBoolean();
    settings.killRoi = gd.getNextBoolean();
    return !gd.invalidNumber();
  }

  /** {@inheritDoc} */
  @Override
  public void setNPasses(int passes) {
    // Do nothing
  }
}
