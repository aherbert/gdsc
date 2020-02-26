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
import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.help.UrlUtils;

/**
 * Analyses marked ROI points in an image. Find the closest pairs within a set distance of each
 * other.
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
  private static class Cluster {
    double x;
    double y;
    double sumx;
    double sumy;
    int size;

    // Used to construct a single linked list of clusters
    Cluster next;

    // Used to store potential clustering links
    Cluster closest;
    double distance;

    // Used to construct a single linked list of cluster points
    ClusterPoint head;

    Cluster(ClusterPoint point) {
      point.next = null;
      head = point;
      this.x = sumx = point.x;
      this.y = sumy = point.y;
      size = 1;
    }

    double distance2(Cluster other) {
      final double dx = x - other.x;
      final double dy = y - other.y;
      return dx * dx + dy * dy;
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
      closest = null;
      size = 0;
      x = y = sumx = sumy = distance = 0;
    }

    /**
     * Link the two clusters as potential merge candidates only if the squared distance is smaller
     * than the other clusters current closest.
     *
     * @param other the other
     * @param d2 the squared distance
     */
    void link(Cluster other, double d2) {
      // Check if the other cluster has a closer candidate
      if (other.closest != null && other.distance < d2) {
        return;
      }

      other.closest = this;
      other.distance = d2;

      this.closest = other;
      this.distance = d2;
    }

    /**
     * True if the closest cluster links back to this cluster.
     *
     * @return True if the closest cluster links back to this cluster.
     */
    boolean validLink() {
      // Check if the other cluster has an updated link to another candidate
      if (closest != null) {
        // Valid if the other cluster links back to this cluster
        return closest.closest == this;
      }
      return false;
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
  private static class ClusterPoint {
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

    points = AssignedPointUtils.extractRoiPoints(roi);
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
    return ImageJUtils.refresh(resultsWindowRef,
        () -> new TextWindow(TITLE, createResultsHeader(cal), "", 600, 500));
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
    candidates = findPairs();

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

  private ArrayList<Cluster> findPairs() {
    // NOTE:
    // This code has been adapted from the GDSC SMLM plugins from the PCPALMClusters class.
    // This was the fastest way of implementing this.

    final ArrayList<Cluster> newCandidates = new ArrayList<>(points.length);
    for (final AssignedPoint p : points) {
      final Cluster c = new Cluster(new ClusterPoint(p.id + 1, p.x, p.y));
      newCandidates.add(c);
    }

    // Find the bounds of the candidates
    double minx = newCandidates.get(0).x;
    double miny = newCandidates.get(0).y;
    double maxx = minx;
    double maxy = miny;
    for (final Cluster c : newCandidates) {
      if (minx > c.x) {
        minx = c.x;
      } else if (maxx < c.x) {
        maxx = c.x;
      }
      if (miny > c.y) {
        miny = c.y;
      } else if (maxy < c.y) {
        maxy = c.y;
      }
    }

    // Assign to a grid
    final int maxBins = 500;
    final double xBinWidth = Math.max(settings.radius, (maxx - minx) / maxBins);
    final double yBinWidth = Math.max(settings.radius, (maxy - miny) / maxBins);
    final int nxbins = 1 + (int) ((maxx - minx) / xBinWidth);
    final int nybins = 1 + (int) ((maxy - miny) / yBinWidth);
    final Cluster[][] grid = new Cluster[nxbins][nybins];
    for (final Cluster c : newCandidates) {
      final int xbin = (int) ((c.x - minx) / xBinWidth);
      final int ybin = (int) ((c.y - miny) / yBinWidth);
      // Build a single linked list
      c.next = grid[xbin][ybin];
      grid[xbin][ybin] = c;
    }

    final double r2 = settings.radius * settings.radius;

    // Sweep the all-vs-all clusters and make potential links between clusters.
    // If a link can be made to a closer cluster then break the link and rejoin.
    // Then join all the links into clusters.
    if (findLinks(grid, nxbins, nybins, r2)) {
      joinLinks(grid, nxbins, nybins, newCandidates);
    }
    return newCandidates;
  }

  /**
   * Search for potential links between clusters that are below the squared radius distance. Store
   * if the clusters have any neighbours within 2*r^2.
   *
   * @param grid the grid
   * @param nxbins the number of X bins
   * @param nybins the number of Y bins
   * @param r2 The squared radius distance
   * @return True if any links were made
   */
  private static boolean findLinks(Cluster[][] grid, final int nxbins, final int nybins,
      final double r2) {
    final Cluster[] neighbours = new Cluster[5];
    boolean linked = false;
    for (int ybin = 0; ybin < nybins; ybin++) {
      for (int xbin = 0; xbin < nxbins; xbin++) {
        for (Cluster c1 = grid[xbin][ybin]; c1 != null; c1 = c1.next) {
          // Build a list of which cells to compare up to a maximum of 5
          // @formatter:off
          //      | 0,0 | 1,0
          // ------------+-----
          // -1,1 | 0,1 | 1,1
          // @formatter:on

          int count = 0;
          neighbours[count++] = c1.next;

          if (ybin < nybins - 1) {
            neighbours[count++] = grid[xbin][ybin + 1];
            if (xbin > 0) {
              neighbours[count++] = grid[xbin - 1][ybin + 1];
            }
          }
          if (xbin < nxbins - 1) {
            neighbours[count++] = grid[xbin + 1][ybin];
            if (ybin < nybins - 1) {
              neighbours[count++] = grid[xbin + 1][ybin + 1];
            }
          }

          // Compare to neighbours and find the closest.
          // Use either the radius threshold or the current closest distance
          // which may have been set by an earlier comparison.
          double min = (c1.closest == null) ? r2 : c1.distance;
          Cluster other = null;
          while (count-- > 0) {
            for (Cluster c2 = neighbours[count]; c2 != null; c2 = c2.next) {
              final double d2 = c1.distance2(c2);
              if (d2 < min) {
                min = d2;
                other = c2;
              }
            }
          }

          if (other != null) {
            // Store the potential link between the two clusters
            c1.link(other, min);
            linked = true;
          }
        }
      }
    }
    return linked;
  }

  /**
   * Join valid links between clusters. Resets the link candidates.
   *
   * @param grid the grid
   * @param nxbins the number of X bins
   * @param nybins the number of Y bins
   * @param candidates Re-populate will all the remaining clusters
   */
  private static void joinLinks(Cluster[][] grid, int nxbins, int nybins,
      ArrayList<Cluster> candidates) {
    candidates.clear();

    for (int ybin = 0; ybin < nybins; ybin++) {
      for (int xbin = 0; xbin < nxbins; xbin++) {
        for (Cluster c1 = grid[xbin][ybin]; c1 != null; c1 = c1.next) {
          if (c1.validLink()) {
            c1.add(c1.closest);
          }
          // Reset the link candidates
          c1.closest = null;

          // Store all remaining clusters
          if (c1.size != 0) {
            candidates.add(c1);
          }
        }

        // Reset the grid
        grid[xbin][ybin] = null;
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
    final GenericDialog gd = new GenericDialog(TITLE);
    gd.addHelp(UrlUtils.UTILITY);

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
