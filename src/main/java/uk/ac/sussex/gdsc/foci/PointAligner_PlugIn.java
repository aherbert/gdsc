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
import uk.ac.sussex.gdsc.core.match.MatchResult;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.core.utils.SimpleArrayUtils;
import uk.ac.sussex.gdsc.core.utils.TextUtils;

import gnu.trove.list.array.TFloatArrayList;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.plugin.ZProjector;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.text.TextWindow;

import java.awt.Color;
import java.io.File;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * Analyses the image using the FindFoci algorithm to identify and assign pixels to maxima. Realigns
 * the marked PointROI with the appropriate peak. Any points that cannot be aligned are identified
 * as problem points.
 */
public class PointAligner_PlugIn implements PlugIn {
  private static final String TITLE = "Point Aligner";
  private static TextWindow resultsWindow;

  private static String resultTitle = "-";
  private static String maskImage = "";
  private static String[] limitMethods =
      new String[] {"None", "Q1 - f * IQR", "Mean - f * SD", "nth Percentile", "% Missed < f"};
  private static int limitMethod = 4;
  private static double factor = 15;
  private static boolean logAlignments = true;
  private static boolean showMoved;
  private static boolean updateRoi;
  private static boolean showOverlay = true;
  private static boolean updateOverlay = true;
  private static boolean showUnaligned;
  private static int unalignedBorder = 10;
  private static String resultsDirectory = "";

  private static boolean writeHeader = true;

  private ImagePlus imp;
  private boolean saveResults;

  /** {@inheritDoc} */
  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    imp = WindowManager.getCurrentImage();

    if (null == imp) {
      IJ.error(TITLE, "There must be at least one image open");
      return;
    }

    if (!FindFoci_PlugIn.isSupported(imp.getBitDepth())) {
      IJ.error(TITLE, "Only " + FindFoci_PlugIn.getSupported() + " images are supported");
      return;
    }

    if (imp.getNChannels() != 1 || imp.getNFrames() != 1) {
      IJ.error(TITLE, "Only single channel, single frame images are supported");
      return;
    }

    final Roi roi = imp.getRoi();
    if (roi == null || roi.getType() != Roi.POINT) {
      IJ.error(TITLE, "The image does not contain Point ROI");
      return;
    }

    if (!showDialog(this)) {
      return;
    }

    final AssignedPoint[] points = FindFociOptimiser_PlugIn.extractRoiPoints(roi, imp, null);

    final FindFoci_PlugIn ff = new FindFoci_PlugIn();

    final ImagePlus mask = null;
    final int backgroundMethod = FindFociProcessor.BACKGROUND_ABSOLUTE;
    final double backgroundParameter = getBackgroundLevel(points);
    final String autoThresholdMethod = "";
    final int searchMethod = FindFociProcessor.SEARCH_ABOVE_BACKGROUND;
    final double searchParameter = 0;
    final int maxPeaks = 33000;
    final int minSize = 1;
    final int peakMethod = FindFociProcessor.PEAK_RELATIVE;
    final double peakParameter = 0;
    final int outputType =
        FindFociProcessor.OUTPUT_MASK_PEAKS | FindFociProcessor.OUTPUT_MASK_NO_PEAK_DOTS;
    final int sortIndex = FindFociProcessor.SORT_MAX_VALUE;
    final int options = 0;
    final double blur = 0;
    final int centreMethod = FindFoci_PlugIn.CENTRE_MAX_VALUE_ORIGINAL;
    final double centreParameter = 0;

    final FindFociResults results = ff.findMaxima(imp, mask, backgroundMethod, backgroundParameter,
        autoThresholdMethod, searchMethod, searchParameter, maxPeaks, minSize, peakMethod,
        peakParameter, outputType, sortIndex, options, blur, centreMethod, centreParameter, 1);

    if (results == null) {
      IJ.error(TITLE, "FindFoci failed");
      return;
    }

    alignPoints(points, results);
  }

  private static boolean showDialog(PointAligner_PlugIn plugin) {
    final List<String> maskList = FindFoci_PlugIn.buildMaskList(plugin.imp);

    final GenericDialog gd = new GenericDialog(TITLE);

    gd.addMessage("Realigns the marked PointROI with the appropriate peak");
    gd.addStringField("Title", resultTitle);
    gd.addChoice("Mask", maskList.toArray(new String[0]), maskImage);
    gd.addChoice("Limit_method", limitMethods, limitMethods[limitMethod]);
    gd.addNumericField("Factor", factor, 2);
    gd.addCheckbox("Log_alignments", logAlignments);
    gd.addCheckbox("Update_ROI", updateRoi);
    gd.addCheckbox("Show_moved", showMoved);
    gd.addCheckbox("Show_overlay", showOverlay);
    gd.addCheckbox("Update_overlay", updateOverlay);
    gd.addCheckbox("Show_unaligned", showUnaligned);
    gd.addNumericField("Unaligned_border", unalignedBorder, 0);
    gd.addStringField("Results_directory", resultsDirectory, 30);
    gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.FIND_FOCI);

    gd.showDialog();

    if (gd.wasCanceled()) {
      return false;
    }

    resultTitle = gd.getNextString();
    maskImage = gd.getNextChoice();
    limitMethod = gd.getNextChoiceIndex();
    factor = gd.getNextNumber();
    logAlignments = gd.getNextBoolean();
    updateRoi = gd.getNextBoolean();
    showMoved = gd.getNextBoolean();
    showOverlay = gd.getNextBoolean();
    updateOverlay = gd.getNextBoolean();
    showUnaligned = gd.getNextBoolean();
    unalignedBorder = (int) gd.getNextNumber();
    resultsDirectory = gd.getNextString();

    if (TextUtils.isNotEmpty(resultsDirectory)) {
      try {
        Files.createDirectories(Paths.get(resultsDirectory));
        plugin.saveResults = true;
      } catch (final Exception ex) {
        IJ.log("Failed to create directory: " + resultsDirectory + ". " + ex.getMessage());
      }
    } else {
      plugin.saveResults = false;
    }

    return true;
  }

  private float getBackgroundLevel(AssignedPoint[] points) {
    // Get a maximum intensity project of the image
    final ZProjector projector = new ZProjector(imp);
    projector.setMethod(ZProjector.MAX_METHOD);
    projector.doProjection();

    final ImageProcessor ip = projector.getProjection().getProcessor();

    // Set background using the lowest currently picked point
    float background = Float.POSITIVE_INFINITY;
    for (final AssignedPoint point : points) {
      final float v = ip.getf(point.getXint(), point.getYint());
      if (background > v) {
        background = v;
      }
    }

    // Subtract the image Std.Dev. to get a buffer for error.
    final ImageStatistics stats = ip.getStatistics();
    background -= stats.stdDev;

    return background;
  }

  private void alignPoints(AssignedPoint[] points, FindFociResults results) {
    if (showOverlay || logAlignments) {
      IJ.log(String.format("%s : %s", TITLE, imp.getTitle()));
    }

    // Get the results
    final ImagePlus maximaImp = results.mask;
    final List<FindFociResult> resultsArray = results.results;

    // We would like the order of the results to correspond to the maxima image pixel values.
    Collections.reverse(resultsArray);

    // Use a stack for 3D support
    final ImageStack impStack = imp.getStack();
    final ImageStack maximaStack = maximaImp.getStack();
    final boolean is3d = maximaStack.getSize() > 1;

    // Assign points to maxima
    final int[] assigned = new int[resultsArray.size()];
    Arrays.fill(assigned, -1);
    for (final AssignedPoint point : points) {
      point.setAssignedId(-1);
    }
    final float[] pointHeight = new float[points.length];
    float minHeight = Float.POSITIVE_INFINITY;

    sortPoints(points, impStack);

    // Q - Why is there no maximum move distance?

    for (final AssignedPoint point : points) {
      final int pointId = point.getId();
      point.setAssignedId(-1);
      final int x = point.getXint();
      final int y = point.getYint();
      final int z = point.getZint();

      pointHeight[pointId] = impStack.getProcessor(z + 1).getf(x, y);
      if (minHeight > pointHeight[pointId]) {
        minHeight = pointHeight[pointId];
      }

      final ImageProcessor ip = maximaStack.getProcessor(z + 1);

      // TODO - Deal with 3D images with different z unit calibration

      int maximaId = ip.get(x, y) - 1;
      if (maximaId >= 0) {
        if (assigned[maximaId] >= 0) {
          // Already assigned - The previous point is higher so it wins.
          // See if any unassigned maxima are closer. This could be an ROI marking error.
          FindFociResult result = resultsArray.get(maximaId);
          final double dist = distance2(x, y, z, result.x, result.y, result.z);
          float maxHeight = Float.NEGATIVE_INFINITY;

          for (int id = 0; id < assigned.length; id++) {
            // Only check the maxima that have not been assigned
            if (assigned[id] < 0) {
              result = resultsArray.get(id);

              final double newDist = distance2(x, y, z, result.x, result.y, result.z);
              if (newDist < dist) {
                // Pick the closest
                // maximaId = id
                // d = newD

                // Pick the highest
                final float v = result.maxValue;
                if (maxHeight < v) {
                  maximaId = id;
                  maxHeight = v;
                }
              }
            }
          }
        }

        if (assigned[maximaId] < 0) {
          // Assign this ROI point to the maxima
          assigned[maximaId] = pointId;
        }

        point.setAssignedId(maximaId);
      }
    }

    // Analyse assigned points for possible errors
    final float thresholdHeight = getThresholdHeight(points, assigned, resultsArray);

    // Output results
    final LinkedList<AssignedPoint> ok = new LinkedList<>();
    final LinkedList<AssignedPoint> moved = new LinkedList<>();
    final LinkedList<AssignedPoint> conflict = new LinkedList<>();
    final LinkedList<AssignedPoint> noAlign = new LinkedList<>();

    double averageMovedDistance = 0;
    float minAssignedHeight = Float.POSITIVE_INFINITY;

    // List of ROI after moving to the assigned peak
    final ArrayList<AssignedPoint> newRoiPoints = new ArrayList<>(points.length);

    for (final AssignedPoint point : points) {
      final int pointId = point.getId();
      AssignedPoint newPoint = point;
      final int x = point.getXint();
      final int y = point.getYint();
      final int z = point.getZint();

      final int maximaId = point.getAssignedId();

      if (maximaId >= 0) {
        final FindFociResult result = resultsArray.get(maximaId);
        final int newX = result.x;
        final int newY = result.y;
        final int newZ = result.z;

        double distance = 0;
        if (newX != x || newY != y || newZ != z) {
          distance = point.distance(newX, newY, newZ);
        }

        if (result.maxValue < thresholdHeight) {
          if (logAlignments) {
            log("Point [%d] %s @ %s ~> %s @ %s (%s) below height threshold (< %s)", pointId + 1,
                MathUtils.rounded(pointHeight[pointId]), getCoords(is3d, x, y, z),
                MathUtils.rounded(result.maxValue), getCoords(is3d, newX, newY, newZ),
                IJ.d2s(distance, 2), MathUtils.rounded(thresholdHeight));
          }
          noAlign.add(point);
          extractPoint(impStack, "below_threshold", pointId + 1, x, y, z, newX, newY, newZ);
          newPoint = null; // remove unaligned points from the updated ROI
        } else {
          final float v = result.maxValue;
          if (minAssignedHeight > v) {
            minAssignedHeight = v;
          }

          if (assigned[maximaId] == pointId) {
            // This is the highest point assigned to the maxima.
            // Check if it is being moved.
            if (logAlignments) {
              log("Point [%d] %s @ %s => %s @ %s (%s)", pointId + 1,
                  MathUtils.rounded(pointHeight[pointId]), getCoords(is3d, x, y, z),
                  MathUtils.rounded(result.maxValue), getCoords(is3d, newX, newY, newZ),
                  IJ.d2s(distance, 2));
            }
            newPoint = new AssignedPoint(newX, newY, newZ, point.getId());
            if (showMoved && distance > 0) {
              moved.add((updateOverlay) ? newPoint : point);
            } else {
              ok.add((updateOverlay) ? newPoint : point);
            }
            averageMovedDistance += distance;
          } else {
            // This point is lower than another assigned to the maxima
            if (logAlignments) {
              log("Point [%d] %s @ %s conflicts for assigned point [%d]", pointId + 1,
                  MathUtils.rounded(pointHeight[pointId]), getCoords(is3d, x, y, z),
                  assigned[maximaId] + 1);
            }
            conflict.add(point);

            // Output an image showing the pixels
            extractPoint(impStack, "conflict", pointId + 1, x, y, z, newX, newY, newZ);
          }
        }
      } else {
        if (logAlignments) {
          log("Point [%d] %s @ %s cannot be aligned", pointId + 1,
              MathUtils.rounded(pointHeight[pointId]), getCoords(is3d, x, y, z));
        }
        noAlign.add(point);
        extractPoint(impStack, "noalign", pointId + 1, x, y, z, x, y, z);
        newPoint = null; // remove unaligned points from the updated ROI
      }

      if (newPoint != null) {
        newRoiPoints.add(newPoint);
      }
    }

    if (logAlignments) {
      log("Minimum picked value = " + MathUtils.rounded(minHeight));
      log("Threshold = " + MathUtils.rounded(thresholdHeight));
      log("Minimum assigned peak height = " + MathUtils.rounded(minAssignedHeight));
    }

    if (averageMovedDistance > 0) {
      averageMovedDistance /= (moved.isEmpty()) ? ok.size() : moved.size();
    }

    final LinkedList<AssignedPoint> missed =
        findMissedPoints(resultsArray, assigned, minAssignedHeight);

    updateRoi(newRoiPoints);

    showOverlay(ok, moved, conflict, noAlign, missed);

    createResultsWindow();
    addResult(minHeight, thresholdHeight, minAssignedHeight, ok, moved, averageMovedDistance,
        conflict, noAlign, missed);
  }

  private static double distance2(int x, int y, int z, int x2, int y2, int z2) {
    final int dx = x - x2;
    final int dy = y - y2;
    final int dz = z - z2;
    return (double) (dx * dx) + (dy * dy) + (dz * dz);
  }

  /**
   * Sort the points using the value from the image stack for each xyz point position.
   *
   * @param points the points
   * @param impStack the image stack
   */
  public void sortPoints(AssignedPoint[] points, ImageStack impStack) {
    if (points == null || impStack == null) {
      return;
    }

    final int[] pointHeight = new int[points.length];

    // Do this in descending height order
    for (final AssignedPoint point : points) {
      final int x = point.getXint();
      final int y = point.getYint();
      final int z = point.getZint();
      pointHeight[point.getId()] = impStack.getProcessor(z + 1).get(x, y);
    }
    Arrays.sort(points, new PointHeightComparator(pointHeight));
    for (int pointId = 0; pointId < points.length; pointId++) {
      points[pointId].setId(pointId);
    }
  }

  private static LinkedList<AssignedPoint> findMissedPoints(List<FindFociResult> resultsArray,
      int[] assigned, float minAssignedHeight) {
    // List maxima above the minimum height that have not been picked
    final ImageStack maskStack = getMaskStack();
    final LinkedList<AssignedPoint> missed = new LinkedList<>();
    for (int maximaId = 0; maximaId < resultsArray.size(); maximaId++) {
      final FindFociResult result = resultsArray.get(maximaId);
      final float v = result.maxValue;
      if (assigned[maximaId] < 0 && v > minAssignedHeight) {
        final int x = result.x;
        final int y = result.y;
        final int z = result.z;

        // Check if the point is within the mask
        if (maskStack != null && maskStack.getProcessor(z + 1).get(x, y) == 0) {
          continue;
        }

        missed.add(new AssignedPoint(x, y, z, maximaId));
      }
    }
    return missed;
  }

  private static LinkedList<Float> findMissedHeights(List<FindFociResult> resultsArray,
      int[] assigned, double heightThreshold) {
    // List maxima above the minimum height that have not been picked
    final ImageStack maskStack = getMaskStack();
    final LinkedList<Float> missed = new LinkedList<>();
    for (int maximaId = 0; maximaId < resultsArray.size(); maximaId++) {
      final FindFociResult result = resultsArray.get(maximaId);
      if (assigned[maximaId] < 0 && result.maxValue > heightThreshold) {
        final int x = result.x;
        final int y = result.y;
        final int z = result.z;

        // Check if the point is within the mask
        if (maskStack != null && maskStack.getProcessor(z + 1).get(x, y) == 0) {
          continue;
        }

        missed.add(result.maxValue);
      }
    }
    Collections.sort(missed);
    return missed;
  }

  /**
   * Analyse the assigned heights and attempt to identify any errornous points.
   *
   * @param points the points
   * @param assigned the assigned
   * @param resultsArray the results array
   * @return The height below which any point is considered an error
   */
  private static float getThresholdHeight(AssignedPoint[] points, int[] assigned,
      List<FindFociResult> resultsArray) {
    final TFloatArrayList heightList = new TFloatArrayList(points.length);
    for (int maximaId = 0; maximaId < assigned.length; maximaId++) {
      if (assigned[maximaId] >= 0) {
        final FindFociResult result = resultsArray.get(maximaId);
        heightList.add(result.maxValue);
      }
    }

    if (heightList.isEmpty()) {
      return 0;
    }

    // Box plot type analysis:
    // The bottom and top of the box are always the 25th and 75th percentile (the lower and upper
    // quartiles, respectively),
    // and the band near the middle of the box is always the 50th percentile (the median).
    // But the ends of the whiskers can represent several possible alternative values, among them:
    // - the minimum and maximum of all the data
    // - the lowest datum still within 1.5 IQR of the lower quartile,
    // and the highest datum still within 1.5 IQR of the upper quartile
    // - one standard deviation above and below the mean of the data
    // - 2nd/9th percentile etc.

    heightList.sort();

    final float[] heights = heightList.toArray();
    double heightThreshold = heights[0];

    switch (limitMethod) {
      case 1:
        // Factor of inter-quartile range
        final float q1 = getQuartileBoundary(heights, 0.25);
        final float q2 = getQuartileBoundary(heights, 0.5);

        heightThreshold = q1 - factor * (q2 - q1);
        if (logAlignments) {
          log("Limiting peaks %s: %s - %s * %s = %s", limitMethods[limitMethod],
              MathUtils.rounded(q1), MathUtils.rounded(factor), MathUtils.rounded(q2 - q1),
              MathUtils.rounded(heightThreshold));
        }

        break;

      case 2:
        // n * Std.Dev. below the mean
        final double[] stats = getStatistics(heights);
        heightThreshold = stats[0] - factor * stats[1];

        if (logAlignments) {
          log("Limiting peaks %s: %s - %s * %s = %s", limitMethods[limitMethod],
              MathUtils.rounded(stats[0]), MathUtils.rounded(factor), MathUtils.rounded(stats[1]),
              MathUtils.rounded(heightThreshold));
        }

        break;

      case 3:
        // nth Percentile
        heightThreshold = getQuartileBoundary(heights, 0.01 * factor);
        if (logAlignments) {
          log("Limiting peaks %s: %sth = %s", limitMethods[limitMethod], MathUtils.rounded(factor),
              MathUtils.rounded(heightThreshold));
        }

        break;

      case 4:
        // Number of missed points is a factor below picked points,
        // i.e. the number of potential maxima is a fraction of the number of assigned maxima.
        final List<Float> missedHeights =
            findMissedHeights(resultsArray, assigned, heightThreshold);
        final double fraction = factor / 100;
        final int size = heights.length;
        for (int pointId = 0; pointId < size; pointId++) {
          heightThreshold = heights[pointId];
          // Count points
          final int missedCount = countPoints(missedHeights, heightThreshold);
          final int assignedCount = size - pointId;
          final int totalCount = missedCount + assignedCount;

          if ((missedCount / (double) totalCount) < fraction) {
            break;
          }
        }

        if (heightThreshold == heights[size - 1]) {
          log("Warning: Maximum height threshold reached when attempting to limit the "
              + "number of missed peaks");
        }

        if (logAlignments) {
          log("Limiting peaks %s: %s %% = %s", limitMethods[limitMethod], MathUtils.rounded(factor),
              MathUtils.rounded(heightThreshold));
        }
        break;

      case 0:
      default:
        // None
        break;
    }

    // Round for integer data
    if (SimpleArrayUtils.isInteger(heights)) {
      heightThreshold = round(heightThreshold);
    }

    return (float) heightThreshold;
  }

  /**
   * Count points that are above the given height.
   *
   * @param missedHeights the missed heights
   * @param height the height
   * @return The count
   */
  private static int countPoints(List<Float> missedHeights, double height) {
    int count = missedHeights.size();
    for (final double h : missedHeights) {
      if (h < height) {
        count--;
      } else {
        break;
      }
    }
    return count;
  }

  private static int round(double value) {
    return (int) (value + 0.5);
  }

  /**
   * Get the quartile boundary for the given fraction, e.g. fraction 0.25 is Q1-Q2 interquartile.
   *
   * @param heights the heights
   * @param fraction the fraction
   * @return The boundary
   */
  public static float getQuartileBoundary(float[] heights, double fraction) {
    if (heights.length == 0) {
      return 0;
    }
    if (heights.length == 1) {
      return heights[0];
    }

    final int size = heights.length;
    int upper = (int) Math.ceil(size * fraction);
    int lower = (int) Math.floor(size * fraction);

    upper = MathUtils.clip(0, size - 1, upper);
    lower = MathUtils.clip(0, size - 1, lower);

    return (heights[upper] + heights[lower]) / 2f;
  }

  private static double[] getStatistics(float[] heights) {
    double sum = 0.0;
    double sum2 = 0.0;
    final int n = heights.length;
    for (final float h : heights) {
      sum += h;
      sum2 += (h * h);
    }
    final double av = sum / n;

    // Get the Std.Dev
    double stdDev;
    if (n > 0) {
      final double d = n;
      stdDev = (d * sum2 - sum * sum) / d;
      if (stdDev > 0.0) {
        stdDev = Math.sqrt(stdDev / (d - 1.0));
      } else {
        stdDev = 0.0;
      }
    } else {
      stdDev = 0.0;
    }

    return new double[] {av, stdDev};
  }

  private static ImageStack getMaskStack() {
    final ImagePlus mask = WindowManager.getImage(maskImage);
    if (mask != null) {
      return mask.getStack();
    }
    return null;
  }

  private static Object getCoords(boolean is3d, int x, int y, int z) {
    if (is3d) {
      return String.format("%d,%d,%d", x, y, z);
    }
    return String.format("%d,%d", x, y);
  }

  private static void log(String format, Object... args) {
    if (logAlignments) {
      IJ.log(String.format(format, args));
    }
  }

  private void extractPoint(ImageStack impStack, String type, int id, int x, int y, int z, int newX,
      int newY, int newZ) {
    if (unalignedBorder <= 0) {
      return;
    }
    if (showUnaligned || saveResults) {
      final int xx = impStack.getWidth() - 1;
      final int yy = impStack.getHeight() - 1;

      final int minX = Math.min(xx, Math.max(0, Math.min(x, newX) - unalignedBorder));
      final int minY = Math.min(yy, Math.max(0, Math.min(y, newY) - unalignedBorder));
      final int maxX = Math.min(xx, Math.max(0, Math.max(x, newX) + unalignedBorder));
      final int maxY = Math.min(yy, Math.max(0, Math.max(y, newY) + unalignedBorder));
      final int w = maxX - minX + 1;
      final int h = maxY - minY + 1;
      final ImageStack newStack = new ImageStack(w, h);
      for (int slice = 1; slice <= impStack.getSize(); slice++) {
        ImageProcessor ip = impStack.getProcessor(slice).duplicate();
        ip.setRoi(minX, minY, w, h);
        ip = ip.crop();
        newStack.addSlice(null, ip);
      }
      final String title = imp.getShortTitle() + "_" + type + "_" + id;
      ImagePlus pointImp = WindowManager.getImage(title);
      if (pointImp == null) {
        pointImp = new ImagePlus(title, newStack);
      } else {
        pointImp.setStack(newStack);
      }

      pointImp.setOverlay(null);
      if (newX - x != 0 || newY - y != 0) {
        final ArrayList<BasePoint> ok = new ArrayList<>(1);
        ok.add(new BasePoint(newX - minX, newY - minY, newZ));
        Match_PlugIn.addOverlay(pointImp, ok, Match_PlugIn.MATCH);
      }
      final ArrayList<BasePoint> conflict = new ArrayList<>(1);
      conflict.add(new BasePoint(x - minX, y - minY, z));
      Match_PlugIn.addOverlay(pointImp, conflict, Match_PlugIn.UNMATCH1);
      pointImp.updateAndDraw();

      if (saveResults) {
        IJ.save(pointImp, resultsDirectory + File.separatorChar + title + ".tif");
      }

      if (showUnaligned) {
        pointImp.show();
      }
    }
  }

  private void updateRoi(ArrayList<? extends BasePoint> newRoiPoints) {
    if (updateRoi) {
      imp.setRoi(AssignedPointUtils.createRoi(newRoiPoints));
    }
  }

  private void showOverlay(LinkedList<AssignedPoint> ok, LinkedList<AssignedPoint> moved,
      LinkedList<AssignedPoint> conflict, LinkedList<AssignedPoint> noAlign,
      LinkedList<AssignedPoint> missed) {
    // Add overlap
    if (showOverlay) {
      IJ.log("Overlay key:");
      IJ.log("  OK = Green");
      if (showMoved) {
        IJ.log("  Moved = Yellow");
      }
      IJ.log("  Conflict = Red");
      IJ.log("  NoAlign = Blue");
      IJ.log("  Missed = Magenta");

      imp.setOverlay(null);
      imp.saveRoi();
      imp.killRoi();
      Match_PlugIn.addOverlay(imp, ok, Match_PlugIn.MATCH);
      Match_PlugIn.addOverlay(imp, moved, Match_PlugIn.UNMATCH1);
      Match_PlugIn.addOverlay(imp, conflict, Color.red);
      Match_PlugIn.addOverlay(imp, noAlign, Match_PlugIn.UNMATCH2);
      Match_PlugIn.addOverlay(imp, missed, Color.magenta);
      imp.updateAndDraw();
    }
  }

  private static void createResultsWindow() {
    if (java.awt.GraphicsEnvironment.isHeadless()) {
      if (writeHeader) {
        writeHeader = false;
        IJ.log(createResultsHeader());
      }
    } else if (resultsWindow == null || !resultsWindow.isShowing()) {
      resultsWindow = new TextWindow(TITLE + " Results", createResultsHeader(), "", 900, 300);
    }
  }

  private static String createResultsHeader() {
    final StringBuilder sb = new StringBuilder();
    sb.append("Title\t");
    sb.append("Image\t");
    sb.append("Method\t");
    sb.append("Factor\t");
    sb.append("Min Height\t");
    sb.append("Threshold\t");
    sb.append("Min Assigned Height\t");
    sb.append("OK\t");
    sb.append("Moved\t");
    sb.append("Av.Move\t");
    sb.append("Conflict\t");
    sb.append("NoAlign\t");
    sb.append("Missed\t");
    sb.append("N\t");
    sb.append("TP\t");
    sb.append("FP\t");
    sb.append("FN\t");
    sb.append("Precision\t");
    sb.append("Recall\t");
    sb.append("Jaccard\t");
    sb.append("F1-score");
    return sb.toString();
  }

  private void addResult(float minHeight, float thresholdHeight, float minAssignedHeight,
      LinkedList<AssignedPoint> ok, LinkedList<AssignedPoint> moved, double averageMovedDistance,
      LinkedList<AssignedPoint> conflict, LinkedList<AssignedPoint> noAlign,
      LinkedList<AssignedPoint> missed) {
    final StringBuilder sb = new StringBuilder();
    sb.append(resultTitle).append('\t');
    sb.append(imp.getTitle()).append('\t');
    sb.append(limitMethods[limitMethod]).append('\t');
    sb.append(factor).append('\t');
    sb.append(MathUtils.rounded(minHeight)).append('\t');
    sb.append(MathUtils.rounded(thresholdHeight)).append('\t');
    sb.append(MathUtils.rounded(minAssignedHeight)).append('\t');
    sb.append(ok.size()).append('\t');
    sb.append(moved.size()).append('\t');
    sb.append(IJ.d2s(averageMovedDistance, 2)).append('\t');
    sb.append(conflict.size()).append('\t');
    sb.append(noAlign.size()).append('\t');
    sb.append(missed.size()).append('\t');

    final int tp = ok.size() + moved.size();
    final int fp = conflict.size() + noAlign.size();
    final int fn = missed.size();
    final MatchResult match = new MatchResult(tp, fp, fn, 0);

    sb.append(match.getNumberPredicted()).append('\t');
    sb.append(tp).append('\t');
    sb.append(fp).append('\t');
    sb.append(fn).append('\t');
    sb.append(IJ.d2s(match.getPrecision(), 4)).append('\t');
    sb.append(IJ.d2s(match.getRecall(), 4)).append('\t');
    sb.append(IJ.d2s(match.getJaccard(), 4)).append('\t');
    sb.append(IJ.d2s(match.getFScore(1), 4));

    if (java.awt.GraphicsEnvironment.isHeadless()) {
      IJ.log(sb.toString());
    } else {
      resultsWindow.append(sb.toString());
    }
  }

  private static class PointHeightComparator implements Comparator<AssignedPoint>, Serializable {
    private static final long serialVersionUID = 1L;
    /** The point height. */
    private final int[] pointHeight;

    /**
     * Instantiates a new point height comparator.
     *
     * @param pointHeight the point height
     */
    PointHeightComparator(int[] pointHeight) {
      this.pointHeight = pointHeight;
    }

    @Override
    public int compare(AssignedPoint o1, AssignedPoint o2) {
      // Highest first
      return Integer.compare(pointHeight[o2.getId()], pointHeight[o1.getId()]);
    }
  }
}
