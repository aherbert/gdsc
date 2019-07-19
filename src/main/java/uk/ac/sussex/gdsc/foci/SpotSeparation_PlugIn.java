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
import uk.ac.sussex.gdsc.core.data.VisibleForTesting;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.utils.SortUtils;
import uk.ac.sussex.gdsc.core.utils.TextUtils;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.BackgroundMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.CentreMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.MaskMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.PeakMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.SearchMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.SortMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.StatisticsMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.ThresholdMethod;
import uk.ac.sussex.gdsc.help.UrlUtils;
import uk.ac.sussex.gdsc.threshold.ThreadAnalyser_PlugIn;

import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

import java.awt.Frame;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Finds spots in an image. Locates the closest neighbour spot within a radius and produces a line
 * profile through the spots. If no neighbour is present produces a line profile through the
 * principle axis.
 */
public class SpotSeparation_PlugIn implements PlugInFilter {
  /** The plugin title. */
  private static final String TITLE = "Spot Separation";

  private static final String[] methods =
      {ThresholdMethod.MAX_ENTROPY.getDescription(), ThresholdMethod.YEN.getDescription()};

  private static final int FLAGS = DOES_16 + DOES_8G;
  private static AtomicReference<TextWindow> resultsWindowRef = new AtomicReference<>();

  private final LinkedList<String> plotProfiles = new LinkedList<>();

  private ImagePlus imp;
  private String resultEntry;
  private Calibration cal;

  /** The current settings for the plugin instance. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    String method = "";
    double radius = 10;
    boolean showSpotImage = true;
    boolean showLineProfiles = true;

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
      method = source.method;
      radius = source.radius;
      showSpotImage = source.showSpotImage;
      showLineProfiles = source.showLineProfiles;
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

    // XXX - Add some options to be able to preview the spots using a ExtendedPluginFilter

    FindFociResults results = runFindFoci(ip);

    // Respect the image ROI
    results = cropToRoi(results);

    if (results == null) {
      return;
    }

    final ImagePlus maximaImp = results.mask;
    final ImageProcessor spotIp = maximaImp.getProcessor();
    final List<FindFociResult> resultsArray = results.results;

    showSpotImage(maximaImp, resultsArray);

    final AssignedPoint[] points = extractSpotCoordinates(resultsArray);

    final float[][] d = computeDistanceMatrix(points);

    final int[] assigned = assignClosestPairs(d);

    final TextWindow resultsWindow = createResultsWindow();

    // Draw line profiles
    for (int i = 0; i < assigned.length; i++) {

      float[] xvalues;
      float[] yvalues;
      String profileTitle;

      if (assigned[i] < 0) {
        // For single spots assign principle axis and produce a line
        // profile

        float xpos = points[i].x;
        float ypos = points[i].y;
        final int peakId = points[i].id;
        profileTitle = TITLE + " Spot " + peakId + " Profile";

        final float[] com = new float[2];
        final double angle = calculateOrientation(ip, spotIp, xpos, ypos, peakId, com);

        // Angle is in Radians anti-clockwise from the X-axis

        final float step = 0.5f;
        final float dx = (float) (Math.cos(angle) * step);
        final float dy = (float) (Math.sin(angle) * step);

        // System.out.printf("%s : Angle = %f, dx=%f, dy=%f. d(x,y)=%.2f,%.2f\n", profileTitle,
        // Math.toDegrees(angle), dx, dy, points[i].x - com[0], points[i].y - com[1])

        // Draw line profile through the centre calculated from the moments. This will fail if
        // the object has a hollow centre (e.g. a horseshoe) so check if it is in the mask.

        if (!isInPeak(spotIp, com[0], com[0], peakId)) {
          com[0] = points[i].x;
          com[1] = points[i].y;
        }

        // Extend the line to the edge
        final LinkedList<float[]> before = new LinkedList<>();
        xpos = com[0];
        ypos = com[1];
        for (int s = 1;; s++) {
          xpos -= dx;
          ypos -= dy;
          if (isInPeak(spotIp, xpos, ypos, peakId)) {
            before.add(new float[] {-step * s, (float) ip.getInterpolatedValue(xpos, ypos)});
          } else {
            break;
          }
        }

        // Extend the line to the edge in the other direction
        final LinkedList<float[]> after = new LinkedList<>();
        xpos = com[0];
        ypos = com[1];
        for (int s = 1;; s++) {
          xpos += dx;
          ypos += dy;
          if (isInPeak(spotIp, xpos, ypos, peakId)) {
            before.add(new float[] {step * s, (float) ip.getInterpolatedValue(xpos, ypos)});
          } else {
            break;
          }
        }

        final float[][] profileValues = convertToFloat(before, after);
        xvalues = profileValues[0];
        yvalues = profileValues[1];

        sortValues(xvalues, yvalues);
        addSingleResult(resultsWindow, peakId, xvalues, yvalues);
      } else {
        // Skip the second of a pair already processed
        if (assigned[i] < i) {
          continue;
        }

        // For pairs draw a line between the spot centres and extend to
        // the edge of the spots

        final int j = assigned[i];
        profileTitle = TITLE + " Spot " + points[i].id + " to " + points[j].id + " Profile";

        final float distance = (float) Math.sqrt(d[i][j]);

        final int samples = (int) Math.ceil(distance);
        final float dx = (float) (points[j].x - points[i].x) / samples;
        final float dy = (float) (points[j].y - points[i].y) / samples;
        final float step = (float) Math.sqrt(dx * dx + dy * dy);

        // The line between the two maxima
        final ArrayList<float[]> values = new ArrayList<>(samples + 1);
        values.add(new float[] {0, ip.getf(points[i].x, points[i].y)});
        float xpos = points[i].x;
        float ypos = points[i].y;
        for (int s = 1; s < samples; s++) {
          xpos += dx;
          ypos += dy;
          values.add(new float[] {step * s, (float) ip.getInterpolatedValue(xpos, ypos)});
        }
        values.add(new float[] {distance, ip.getf(points[j].x, points[j].y)});

        // Extend the line to the edge of the first spot
        final LinkedList<float[]> before = new LinkedList<>();
        int peakId = points[i].id;
        xpos = points[i].x;
        ypos = points[i].y;
        for (int s = 1;; s++) {
          xpos -= dx;
          ypos -= dy;
          if (isInPeak(spotIp, xpos, ypos, peakId)) {
            before.add(new float[] {-step * s, (float) ip.getInterpolatedValue(xpos, ypos)});
          } else {
            break;
          }
        }

        // Extend the line to the edge of the second spot
        final LinkedList<float[]> after = new LinkedList<>();
        peakId = points[j].id;
        xpos = points[j].x;
        ypos = points[j].y;
        for (int s = 1;; s++) {
          xpos += dx;
          ypos += dy;
          if (isInPeak(spotIp, xpos, ypos, peakId)) {
            before.add(
                new float[] {distance + step * s, (float) ip.getInterpolatedValue(xpos, ypos)});
          } else {
            break;
          }
        }

        final float[][] profileValues = convertToFloat(before, values, after);
        xvalues = profileValues[0];
        yvalues = profileValues[1];

        final float offset = sortValues(xvalues, yvalues);
        addPairResult(resultsWindow, peakId, xvalues, yvalues, distance, offset);
      }

      showLineProfile(xvalues, yvalues, profileTitle);
    }

    closeOtherLineProfiles();
  }

  private static boolean isInPeak(ImageProcessor spotIp, float xpos, float ypos, int peakId) {
    return spotIp.get(Math.round(xpos), Math.round(ypos)) == peakId;
  }

  private static float sortValues(float[] xValues, float[] yValues) {
    if (xValues.length == 0) {
      return 0;
    }
    SortUtils.sortData(yValues, xValues, true, false);
    // Reset to start at zero
    final float offset = -xValues[0];
    for (int ii = 0; ii < xValues.length; ii++) {
      xValues[ii] += offset;
    }
    return offset;
  }

  private boolean showDialog() {
    settings = Settings.load();
    final GenericDialog gd = new GenericDialog(TITLE);
    gd.addHelp(UrlUtils.UTILITY);

    gd.addMessage("Analyse line profiles between spots within a separation distance");

    gd.addChoice("Threshold_method", methods, settings.method);
    gd.addSlider("Radius", 5, 50, settings.radius);
    gd.addCheckbox("Show_spot_image", settings.showSpotImage);
    gd.addCheckbox("Show_line_profiles", settings.showLineProfiles);

    gd.showDialog();
    if (gd.wasCanceled()) {
      return false;
    }

    settings.method = gd.getNextChoice();
    settings.radius = gd.getNextNumber();
    settings.showSpotImage = gd.getNextBoolean();
    settings.showLineProfiles = gd.getNextBoolean();
    settings.save();

    return true;
  }

  private FindFociResults cropToRoi(FindFociResults results) {
    final Roi roi = imp.getRoi();
    if (roi == null || results == null) {
      return results;
    }

    final ImagePlus maximaImp = results.mask;
    final ImageProcessor spotIp = maximaImp.getProcessor();
    final List<FindFociResult> resultsArray = results.results;

    // Find all maxima that are within the ROI bounds
    final byte[] mask = (roi.getMask() == null) ? null : (byte[]) roi.getMask().getPixels();
    final Rectangle bounds = roi.getBounds();
    final int maxx = bounds.x + bounds.width;
    final int maxy = bounds.y + bounds.height;
    final int[] newId = new int[resultsArray.size() + 1];
    int newCount = 0;

    for (int i = 0; i < resultsArray.size(); i++) {
      final FindFociResult result = resultsArray.get(i);
      final int x = result.x;
      final int y = result.y;

      if (x >= bounds.x && x < maxx && y >= bounds.y && y < maxy) {
        if (mask != null) {
          final int index = (y - bounds.y) * bounds.width + x - bounds.x;
          if (mask[index] == 0) {
            continue;
          }
        }
        newId[i] = ++newCount;
      }
    }

    if (newCount == 0) {
      return null;
    }

    // Renumber the remaining valid spots
    final List<FindFociResult> newResultsArray = new ArrayList<>(newCount);
    final int[] maskIdMap = new int[resultsArray.size() + 1];
    for (int i = 0; i < resultsArray.size(); i++) {
      final FindFociResult result = resultsArray.get(i);
      if (newId[i] > 0) {
        result.id = newId[i];
        newResultsArray.add(result);
        // Reverse peak IDs so the highest value is the first in the
        // results list
        final int oldMaskId = resultsArray.size() - i;
        maskIdMap[oldMaskId] = newCount - newId[i] + 1;
      }
    }

    // Update the image
    for (int i = 0; i < spotIp.getPixelCount(); i++) {
      final int oldPeakId = spotIp.get(i);
      if (oldPeakId > 0) {
        spotIp.set(i, maskIdMap[oldPeakId]);
      }
    }
    maximaImp.setProcessor(spotIp);

    return new FindFociResults(maximaImp, newResultsArray, null);
  }

  private FindFociResults runFindFoci(ImageProcessor ip) {
    // Run FindFoci to get the spots
    // Get each spot as a different number with the centre using the search
    // centre

    // Allow image thresholding
    final FindFociProcessorOptions processorOptions = new FindFociProcessorOptions();
    processorOptions.setBackgroundMethod(BackgroundMethod.AUTO_THRESHOLD);
    processorOptions.setBackgroundParameter(0);
    if (ip.getMinThreshold() != ImageProcessor.NO_THRESHOLD) {
      processorOptions.setBackgroundMethod(BackgroundMethod.ABSOLUTE);
      processorOptions.setBackgroundParameter(ip.getMinThreshold());
    }

    // TODO - Find the best method for this
    processorOptions.setThresholdMethod(ThresholdMethod.fromDescription(settings.method));
    processorOptions.setSearchMethod(SearchMethod.ABOVE_BACKGROUND);
    processorOptions.setMaxPeaks(100);
    processorOptions.setMinSize(3);
    processorOptions.setPeakMethod(PeakMethod.ABSOLUTE);
    processorOptions.setPeakParameter(5);
    processorOptions.getOptions().clear();
    processorOptions.setStatisticsMethod(StatisticsMethod.INSIDE);
    processorOptions.setMaskMethod(MaskMethod.PEAKS);
    processorOptions.setSortMethod(SortMethod.MAX_VALUE);
    processorOptions.setGaussianBlur(1.5);
    processorOptions.setCentreMethod(CentreMethod.MAX_VALUE_SEARCH);

    final ImagePlus tmpImp = new ImagePlus("tmp", ip);
    final FindFoci_PlugIn ff = new FindFoci_PlugIn();
    return ff.createFindFociProcessor(tmpImp).findMaxima(tmpImp, null, processorOptions);
  }

  private void showSpotImage(ImagePlus maximaImp, List<FindFociResult> resultsArray) {
    if (settings.showSpotImage) {
      final ImageProcessor spotIp = maximaImp.getProcessor();
      spotIp.setMinAndMax(0, resultsArray.size());
      final String title = TITLE + " Spots";
      final ImagePlus spotImp = WindowManager.getImage(title);
      if (spotImp == null) {
        maximaImp.setTitle(title);
        maximaImp.show();
      } else {
        spotImp.setProcessor(spotIp);
        spotImp.updateAndDraw();
      }
    }
  }

  /**
   * Extract the centre of each maxima from the results.
   *
   * @param resultsArray the results array
   * @return the assigned points
   */
  private static AssignedPoint[] extractSpotCoordinates(List<FindFociResult> resultsArray) {
    final AssignedPoint[] points = new AssignedPoint[resultsArray.size()];
    int index = 0;
    final int maxId = resultsArray.size() + 1;
    for (final FindFociResult result : resultsArray) {
      points[index] = new AssignedPoint(result.x, result.y, maxId - result.id);
      index++;
    }
    return points;
  }

  /**
   * Compute the all-vs-all squared distance matrix.
   *
   * @param points the points
   * @return the float[][] distance matrix
   */
  private static float[][] computeDistanceMatrix(AssignedPoint[] points) {
    final float[][] d = new float[points.length][points.length];
    for (int i = 0; i < d.length; i++) {
      for (int j = i + 1; j < d.length; j++) {
        d[i][j] = (float) points[i].distanceSquared(points[j].x, points[j].y);
        d[j][i] = d[i][j];
      }
    }
    return d;
  }

  /**
   * Finds the pairs ij which are closer than the radius. Any given point can only be assigned to
   * one pair and so the closest remaining unassigned points are processed until no more pairs are
   * left.
   *
   * @param matrix Squared distance matrix
   * @return Array of indices that each point is assigned to. Unassigned points will have an index
   *         of -1.
   */
  private int[] assignClosestPairs(float[][] matrix) {
    final float d2 = (float) (settings.radius * settings.radius);
    final int[] assigned = new int[matrix.length];
    Arrays.fill(assigned, -1);
    for (;;) {
      float minD = d2;
      int ii = 0;
      int jj = 0;
      for (int i = 0; i < matrix.length; i++) {
        if (assigned[i] >= 0) {
          continue;
        }
        for (int j = i + 1; j < matrix.length; j++) {
          if (assigned[j] >= 0) {
            continue;
          }
          if (matrix[i][j] < minD) {
            minD = matrix[i][j];
            ii = i;
            jj = j;
          }
        }
      }
      if (ii != jj) {
        assigned[ii] = jj;
        assigned[jj] = ii;
      } else {
        break;
      }
    }
    return assigned;
  }

  /**
   * Create the result window (if it is not available).
   *
   * @return the text window
   */
  private TextWindow createResultsWindow() {
    final TextWindow resultsWindow = ImageJUtils.refresh(resultsWindowRef,
        () -> new TextWindow(TITLE + " Positions", createResultsHeader(), "", 700, 300));

    // Create the image result prefix
    final StringBuilder sb = new StringBuilder();
    sb.append(imp.getTitle());
    if (imp.getStackSize() > 1) {
      sb.append(" [Z").append(imp.getSlice()).append("C").append(imp.getChannel()).append("T")
          .append(imp.getFrame()).append("]");
    }
    sb.append('\t');
    cal = imp.getCalibration();
    sb.append(cal.getXUnit());
    if (!(cal.getYUnit().equalsIgnoreCase(cal.getXUnit())
        && cal.getZUnit().equalsIgnoreCase(cal.getXUnit()))) {
      sb.append(" ").append(cal.getYUnit()).append(" ").append(cal.getZUnit()).append(" ");
    }
    sb.append('\t');
    resultEntry = sb.toString();
    return resultsWindow;
  }

  private static String createResultsHeader() {
    final StringBuilder sb = new StringBuilder();
    sb.append("Image\t");
    sb.append("Units\t");
    sb.append("Peak Id\t");
    sb.append("Type\t");
    sb.append("Width1\t");
    sb.append("Width2\t");
    sb.append("Distance");
    return sb.toString();
  }

  private void addSingleResult(TextWindow resultsWindow, int id, float[] xValues, float[] yValues) {
    final StringBuilder sb = new StringBuilder();
    sb.append(resultEntry);
    sb.append(id).append("\tSingle\t");

    final int[] maxima = new int[2];
    final float[] d = ThreadAnalyser_PlugIn.countMaxima(xValues, yValues, maxima);
    if (d.length > 1) {
      // Find the plot range
      final int[] maxIndices = new int[d.length];
      float max = yValues[0];
      float min = yValues[0];
      for (int i = 1; i < yValues.length; i++) {
        if (max < yValues[i]) {
          maxIndices[0] = i;
          max = yValues[i];
        }
        if (min > yValues[i]) {
          min = yValues[i];
        }
      }
      // Set the minimum height for a maxima using a a fraction of the plot range
      final float height = (max - min) * 0.1f;

      int maxCount = 1;
      int start = findIndex(0, d[0], xValues);
      for (int i = 1; i < d.length; i++) {
        final int end = findIndex(start, d[i], xValues);

        // Set threshold using the minimum height
        final float threshold = Math.min(yValues[start], yValues[end]) - height;
        for (int j = start + 1; j < end; j++) {
          if (yValues[j] < threshold) {
            // Add to the maxima
            if (!contains(maxIndices, maxCount, start)) {
              maxIndices[maxCount++] = start;
            }
            if (!contains(maxIndices, maxCount, end)) {
              maxIndices[maxCount++] = end;
            }
            break;
          }
        }

        start = end;
      }

      if (maxCount > 1) {
        // There are definitely multiple peaks in this single spot

        // Find the widths of each peak
        Arrays.sort(maxIndices);
        final float[] minD = new float[maxIndices.length];
        float lastMinD = 0;
        start = maxIndices[0];
        for (int i = 1; i < maxCount; i++) {
          final int end = maxIndices[i];
          min = Float.POSITIVE_INFINITY;
          float middle = xValues[end] - xValues[start];
          for (int j = start + 1; j < end; j++) {
            if (min > yValues[j]) {
              min = yValues[j];
              middle = xValues[j];
            }
          }
          minD[i - 1] = middle + lastMinD;
          lastMinD = minD[i - 1];

          start = end;
        }
        minD[maxCount - 1] = xValues[xValues.length - 1];

        ImageJUtils.log("Multiple peak in a single spot: Peak Id %d = %d peaks", id, maxCount);

        // TODO - Perform a better table output if there are more than 2 peaks.
        try (Formatter formatter = new Formatter(sb)) {
          formatter.format("%.2f\t", minD[0] * cal.pixelWidth);
          formatter.format("%.2f\t", (minD[1] - minD[0]) * cal.pixelWidth);
          formatter.format("%.2f",
              (xValues[maxIndices[1]] - xValues[maxIndices[0]]) * cal.pixelWidth);
        }
        resultsWindow.append(sb.toString());
        return;
      }
    }

    final double width = xValues[xValues.length - 1];
    TextUtils.formatTo(sb, "%.2f", width * cal.pixelWidth);
    resultsWindow.append(sb.toString());
  }

  /**
   * Test if the the value is in the values, searching from 0 to max.
   *
   * @param values the values
   * @param max the max
   * @param value the value
   * @return true, if successful
   */
  private static boolean contains(int[] values, int max, int value) {
    for (int i = max; i-- > 0;) {
      if (values[i] == value) {
        return true;
      }
    }
    return false;
  }

  /**
   * Find the index of the value in the values, searching from the specified index.
   *
   * @param from the from index
   * @param value the value
   * @param values the values
   * @return the index
   */
  private static int findIndex(int from, float value, float[] values) {
    for (int i = from; i < values.length; i++) {
      if (value == values[i]) {
        return i;
      }
    }
    throw new IllegalStateException("Unable to find peak index for the specified maxima");
  }

  private void addPairResult(TextWindow resultsWindow, int id, float[] xValues, float[] yValues,
      float distance, float peak1Start) {
    final StringBuilder sb = new StringBuilder();
    sb.append(resultEntry);
    sb.append(id).append("\tPair\t");
    // Find the midpoint between the two peaks
    int index = 0;
    while (index < xValues.length && xValues[index] < peak1Start) {
      index++;
    }
    float min = Float.POSITIVE_INFINITY;
    float middle = distance * 0.5f;
    while (index < xValues.length && xValues[index] <= peak1Start + distance) {
      if (min > yValues[index]) {
        min = yValues[index];
        middle = xValues[index];
      }
      index++;
    }

    final double width1 = middle;
    final double width2 = xValues[xValues.length - 1] - width1;
    TextUtils.formatTo(sb, "%.2f\t%.2f\t%.2f", width1 * cal.pixelWidth, width2 * cal.pixelWidth,
        distance * cal.pixelWidth);
    resultsWindow.append(sb.toString());
  }

  /**
   * Estimate the angle of the spot using the central moments.
   *
   * <p>See Burger & Burge, Digital Image Processing, An Algorithmic Introduction using Java (1st
   * Edition), pp231.
   *
   * @param spotIp the spot image
   * @param xpos the xpos
   * @param ypos the ypos
   * @param peakId the peak id
   * @param com The calculated centre-of-mass
   * @return The orientation in range -pi/2 to pi/2 from the x-axis, incrementing clockwise if the
   *         Y-axis points downwards
   */
  @VisibleForTesting
  static double calculateOrientation(ImageProcessor spotIp, float xpos, float ypos, int peakId,
      float[] com) {
    // Find the limits of the spot and calculate the centre of mass
    int maxu = (int) xpos;
    int minu = maxu;
    int maxv = (int) ypos;
    int minv = maxv;

    double u00 = 0;
    double cx = 0;
    double cy = 0;
    for (int ii = 0; ii < spotIp.getPixelCount(); ii++) {
      if (spotIp.get(ii) == peakId) {
        final int u = ii % spotIp.getWidth();
        final int v = ii / spotIp.getWidth();
        if (maxu < u) {
          maxu = u;
        }
        if (minu > u) {
          minu = u;
        }
        if (maxv < v) {
          maxv = v;
        }
        if (minv > v) {
          minv = v;
        }
        u00++;
        cx += u;
        cy += v;
      }
    }
    cx /= u00;
    cy /= u00;

    if (com != null) {
      com[0] = (float) cx;
      com[1] = (float) cy;
    }

    // Calculate moments
    double u11 = 0;
    double u20 = 0;
    double u02 = 0;
    for (int v = minv; v <= maxv; v++) {
      for (int u = minu, ii = v * spotIp.getWidth() + minu; u <= maxu; u++, ii++) {
        if (spotIp.get(ii) == peakId) {
          final double dx = u - cx;
          final double dy = v - cy;
          u11 += dx * dy;
          u20 += dx * dx;
          u02 += dy * dy;
        }
      }
    }

    // Calculate the ellipsoid
    final double A = 2 * u11;
    final double B = u20 - u02;
    return 0.5 * Math.atan2(A, B);
  }

  /**
   * Estimate the angle of the spot using the central moments. Moments are weighted using the
   * original image values
   *
   * <p>See Burger & Burge, Digital Image Processing, An Algorithmic Introduction using Java (1st
   * Edition), pp231.
   *
   * @param ip the image
   * @param spotIp the spot image
   * @param xpos the xpos
   * @param ypos the ypos
   * @param peakId the peak id
   * @param com The calculated centre-of-mass
   * @return The orientation in range -pi/2 to pi/2 from the x-axis, incrementing clockwise if the
   *         Y-axis points downwards
   */
  @VisibleForTesting
  static double calculateOrientation(ImageProcessor ip, ImageProcessor spotIp, float xpos,
      float ypos, int peakId, float[] com) {
    // Find the limits of the spot and calculate the centre of mass
    int maxu = (int) xpos;
    int minu = maxu;
    int maxv = (int) ypos;
    int minv = maxv;

    double u00 = 0;
    double cx = 0;
    double cy = 0;
    for (int ii = 0; ii < spotIp.getPixelCount(); ii++) {
      if (spotIp.get(ii) == peakId) {
        final float value = ip.getf(ii);
        final int u = ii % spotIp.getWidth();
        final int v = ii / spotIp.getWidth();
        if (maxu < u) {
          maxu = u;
        }
        if (minu > u) {
          minu = u;
        }
        if (maxv < v) {
          maxv = v;
        }
        if (minv > v) {
          minv = v;
        }
        u00 += value;
        cx += u * value;
        cy += v * value;
      }
    }
    cx /= u00;
    cy /= u00;

    if (com != null) {
      com[0] = (float) cx;
      com[1] = (float) cy;
    }

    // Calculate moments
    double u11 = 0;
    double u20 = 0;
    double u02 = 0;
    for (int v = minv; v <= maxv; v++) {
      for (int u = minu, ii = v * spotIp.getWidth() + minu; u <= maxu; u++, ii++) {
        if (spotIp.get(ii) == peakId) {
          final float value = ip.getf(ii);
          final double dx = u - cx;
          final double dy = v - cy;
          u11 += value * dx * dy;
          u20 += value * dx * dx;
          u02 += value * dy * dy;
        }
      }
    }

    // Calculate the ellipsoid
    final double A = 2 * u11;
    final double B = u20 - u02;
    return 0.5 * Math.atan2(A, B);
  }

  @SafeVarargs
  private static final float[][] convertToFloat(List<float[]>... lists) {
    int size = 0;
    for (final List<float[]> list : lists) {
      size += list.size();
    }
    final float[][] results = new float[2][size];
    int index = 0;
    for (final List<float[]> list : lists) {
      for (final float[] f : list) {
        results[0][index] = f[0];
        results[1][index] = f[1];
        index++;
      }
    }
    return results;
  }

  private void closeOtherLineProfiles() {
    if (!settings.showLineProfiles) {
      return;
    }
    final Frame[] frames = WindowManager.getNonImageWindows();
    if (frames == null) {
      return;
    }
    for (final Frame f : frames) {
      if (plotProfiles.contains(f.getTitle())) {
        continue;
      }
      if (f.getTitle().startsWith(TITLE) && f.getTitle().endsWith("Profile")
          && f instanceof PlotWindow) {
        final PlotWindow p = ((PlotWindow) f);
        p.close();
      }
    }
  }

  /**
   * Show a plot of the line profile.
   *
   * @param xValues the x values
   * @param yValues the y values
   * @param profileTitle the profile title
   */
  private void showLineProfile(float[] xValues, float[] yValues, String profileTitle) {
    if (settings.showLineProfiles) {
      final Frame f = WindowManager.getFrame(profileTitle);
      final Plot plot = new Plot(profileTitle, "Distance", "Value");
      plot.addPoints(xValues, yValues, Plot.LINE);
      if (f instanceof PlotWindow) {
        final PlotWindow p = ((PlotWindow) f);
        p.drawPlot(plot);
      } else {
        plot.show();
      }

      plotProfiles.add(profileTitle);
    }
  }
}
