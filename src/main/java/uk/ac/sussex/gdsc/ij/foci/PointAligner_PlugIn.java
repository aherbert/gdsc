/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2022 Alex Herbert
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

import gnu.trove.list.array.TFloatArrayList;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.plugin.ZProjector;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.text.TextWindow;
import java.awt.Color;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.ij.gui.ExtendedGenericDialog;
import uk.ac.sussex.gdsc.core.match.MatchResult;
import uk.ac.sussex.gdsc.core.utils.LocalList;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.core.utils.SimpleArrayUtils;
import uk.ac.sussex.gdsc.core.utils.TextUtils;
import uk.ac.sussex.gdsc.ij.UsageTracker;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.BackgroundMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.CentreMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.MaskMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.PeakMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.SearchMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.SortMethod;

/**
 * Analyses the image using the FindFoci algorithm to identify and assign pixels to maxima. Realigns
 * the marked PointROI with the appropriate peak. Any points that cannot be aligned are identified
 * as problem points.
 */
public class PointAligner_PlugIn implements PlugIn {
  private static final String TITLE = "Point Aligner";
  private static AtomicReference<TextWindow> resultsWindowRef = new AtomicReference<>();

  private static AtomicBoolean writeHeader = new AtomicBoolean(true);

  private ImagePlus imp;
  private int currentC;
  private int currentT;
  private boolean saveResults;

  /** The plugin settings. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    static final String[] limitMethods =
        new String[] {"None", "Q1 - f * IQR", "Mean - f * SD", "nth Percentile", "% Missed < f"};

    String resultTitle = "-";
    String maskImage = "";
    int limitMethod = 4;
    double factor = 15;
    boolean logAlignments = true;
    boolean showMoved;
    boolean updateRoi;
    boolean showOverlay = true;
    boolean updateOverlay = true;
    boolean showUnaligned;
    int unalignedBorder = 10;
    String resultsDirectory = "";

    Settings() {
      resultTitle = "-";
      maskImage = "";
      limitMethod = 4;
      factor = 15;
      logAlignments = true;
      showOverlay = true;
      updateOverlay = true;
      unalignedBorder = 10;
      resultsDirectory = "";
    }

    Settings(Settings source) {
      resultTitle = source.resultTitle;
      maskImage = source.maskImage;
      limitMethod = source.limitMethod;
      factor = source.factor;
      logAlignments = source.logAlignments;
      showMoved = source.showMoved;
      updateRoi = source.updateRoi;
      showOverlay = source.showOverlay;
      updateOverlay = source.updateOverlay;
      showUnaligned = source.showUnaligned;
      unalignedBorder = source.unalignedBorder;
      resultsDirectory = source.resultsDirectory;
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
   * The distance function.
   */
  private interface DistanceFunction {

    /**
     * Compute the distance.
     *
     * @param x the x
     * @param y the y
     * @param z the z
     * @param result the result
     * @return the distance
     */
    double distance(int x, int y, int z, FindFociResult result);
  }

  /**
   * Formmater for coordinates.
   */
  private enum CoordinateFormatter {
    TWOD {
      @Override
      String format(int x, int y, int z) {
        return String.format("%d,%d", x, y);
      }
    },
    THREED {
      @Override
      String format(int x, int y, int z) {
        return String.format("%d,%d,%d", x, y, z);
      }
    };

    /**
     * Format.
     *
     * @param x the x
     * @param y the y
     * @param z the z
     * @return the string
     */
    abstract String format(int x, int y, int z);
  }

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
      IJ.error(TITLE, "Only " + FindFoci_PlugIn.SUPPORTED_BIT_DEPTH + " images are supported");
      return;
    }

    // if (imp.getNChannels() != 1 || imp.getNFrames() != 1) {
    // IJ.error(TITLE, "Only single channel, single frame images are supported");
    // return;
    // }

    final Roi roi = imp.getRoi();
    if (roi == null || roi.getType() != Roi.POINT) {
      IJ.error(TITLE, "The image does not contain Point ROI");
      return;
    }

    if (!showDialog(this)) {
      return;
    }

    final AssignedPoint[] points = AssignedPointUtils.extractRoiPoints(imp);
    if (!checkZCoordinate(imp, points)) {
      return;
    }

    final FindFoci_PlugIn ff = new FindFoci_PlugIn();
    final ImagePlus mask = WindowManager.getImage(settings.maskImage);
    final FindFociProcessorOptions processorOptions = new FindFociProcessorOptions();
    processorOptions.setBackgroundMethod(BackgroundMethod.ABSOLUTE);
    processorOptions.setBackgroundParameter(getBackgroundLevel(points));
    processorOptions.setSearchMethod(SearchMethod.ABOVE_BACKGROUND);
    processorOptions.setMaxPeaks(33000);
    processorOptions.setMinSize(1);
    processorOptions.setPeakMethod(PeakMethod.RELATIVE);
    processorOptions.setPeakParameter(0);
    processorOptions.getOptions().clear();
    processorOptions.setMaskMethod(MaskMethod.PEAKS);
    processorOptions.setSortMethod(SortMethod.MAX_VALUE);
    processorOptions.setGaussianBlur(0);
    processorOptions.setCentreMethod(CentreMethod.MAX_VALUE_ORIGINAL);

    final FindFociResults results =
        ff.createFindFociProcessor(imp).findMaxima(imp, mask, processorOptions);

    if (results == null) {
      IJ.error(TITLE, "FindFoci failed");
      return;
    }

    // Use IJ.log here as this is used in show overlay to output the key even
    // if log alignments is false
    if (settings.showOverlay || settings.logAlignments) {
      IJ.log(String.format("%s : %s", TITLE, imp.getTitle()));
      log("%s Background : %s", FindFoci_PlugIn.TITLE,
          MathUtils.rounded(processorOptions.getBackgroundParameter()));
    }

    alignPoints(points, results);
  }

  /**
   * Check that a 3D image has non-zero z coordinates for the points.
   *
   * <p>Note: For a 2D image the z-coordinate is ignored during analysis and this method returns
   * true.
   *
   * @param imp the image
   * @param points the points
   * @return true, if successful
   */
  private static boolean checkZCoordinate(ImagePlus imp, AssignedPoint[] points) {
    if (imp.getNSlices() != 1) {
      for (final AssignedPoint point : points) {
        if (point.getZ() == 0) {
          IJ.error(TITLE, "3D image does not have non-zero z-coordinate for all points");
          return false;
        }
      }
    }
    return true;
  }

  private boolean showDialog(PointAligner_PlugIn plugin) {
    final List<String> maskList = FindFoci_PlugIn.buildMaskList(plugin.imp);

    final ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);

    settings = Settings.load();
    gd.addMessage("Realigns the marked PointROI with the appropriate peak");
    gd.addStringField("Title", settings.resultTitle);
    gd.addChoice("Mask", maskList.toArray(new String[0]), settings.maskImage);
    gd.addChoice("Limit_method", Settings.limitMethods, settings.limitMethod);
    gd.addNumericField("Factor", settings.factor, 2);
    gd.addCheckbox("Log_alignments", settings.logAlignments);
    gd.addCheckbox("Update_ROI", settings.updateRoi);
    gd.addCheckbox("Show_moved", settings.showMoved);
    gd.addCheckbox("Show_overlay", settings.showOverlay);
    gd.addCheckbox("Update_overlay", settings.updateOverlay);
    gd.addCheckbox("Show_unaligned", settings.showUnaligned);
    gd.addNumericField("Unaligned_border", settings.unalignedBorder, 0);
    gd.addStringField("Results_directory", settings.resultsDirectory, 30);
    gd.addHelp(uk.ac.sussex.gdsc.ij.help.Urls.FIND_FOCI);

    gd.showDialog();

    if (gd.wasCanceled()) {
      return false;
    }

    settings.resultTitle = gd.getNextString();
    settings.maskImage = gd.getNextChoice();
    settings.limitMethod = gd.getNextChoiceIndex();
    settings.factor = gd.getNextNumber();
    settings.logAlignments = gd.getNextBoolean();
    settings.updateRoi = gd.getNextBoolean();
    settings.showMoved = gd.getNextBoolean();
    settings.showOverlay = gd.getNextBoolean();
    settings.updateOverlay = gd.getNextBoolean();
    settings.showUnaligned = gd.getNextBoolean();
    settings.unalignedBorder = (int) gd.getNextNumber();
    settings.resultsDirectory = gd.getNextString();
    settings.save();

    if (TextUtils.isNotEmpty(settings.resultsDirectory)) {
      try {
        Files.createDirectories(Paths.get(settings.resultsDirectory));
        plugin.saveResults = true;
      } catch (final Exception ex) {
        IJ.log("Failed to create directory: " + settings.resultsDirectory + ". " + ex.getMessage());
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
    // Get the results
    final ImagePlus maximaImp = results.mask;
    currentC = imp.getChannel();
    currentT = imp.getFrame();
    final List<FindFociResult> resultsArray = results.results;

    // We would like the order of the results to correspond to the maxima image pixel values.
    Collections.reverse(resultsArray);

    // Use a stack for 3D support
    final ImageStack impStack = imp.getStack();
    final ImageStack maximaStack = maximaImp.getStack();
    final boolean is3d = maximaStack.getSize() > 1;

    // Allow use of z for stack slice lookup
    if (is3d) {
      FindFociResults.incrementZ(resultsArray);
    }

    // Assign points to maxima
    final int[] assigned = SimpleArrayUtils.newIntArray(resultsArray.size(), -1);

    final float[] pointHeight = sortDescending(points, impStack);

    // Q - Why is there no maximum move distance?

    // Handle 3D images with different z unit calibration
    // Scale the YZ dimensions relative to X
    final Calibration cal = imp.getCalibration();
    DistanceFunction df;
    CoordinateFormatter cf;
    if (is3d) {
      cf = CoordinateFormatter.THREED;
      final double sy = cal.pixelHeight / cal.pixelWidth;
      final double sz = cal.pixelDepth / cal.pixelWidth;
      df = (x, y, z, r) -> {
        final double dx = x - r.x;
        final double dy = (y - r.y) * sy;
        final double dz = (z - r.z) * sz;
        return dx * dx + dy * dy + dz * dz;
      };
    } else {
      // 2D
      cf = CoordinateFormatter.TWOD;
      if (cal.pixelHeight == cal.pixelWidth) {
        // Common case when pixels are same width and height.
        df = (x, y, z, r) -> {
          final double dx = x - r.x;
          final double dy = y - r.y;
          return dx * dx + dy * dy;
        };
      } else {
        final double sy = cal.pixelHeight / cal.pixelWidth;
        df = (x, y, z, r) -> {
          final double dx = x - r.x;
          final double dy = (y - r.y) * sy;
          return dx * dx + dy * dy;
        };
      }
    }

    for (final AssignedPoint point : points) {
      final int pointId = point.getId();
      final int x = point.getXint();
      final int y = point.getYint();
      // 3D results used 1-based z coordinates, 2D results will have z==0.
      final int z = is3d ? point.getZint() : 1;

      final ImageProcessor ip = maximaStack.getProcessor(z);

      int maximaId = ip.get(x, y) - 1;
      if (maximaId >= 0) {
        if (assigned[maximaId] >= 0) {
          // Already assigned - The previous point is higher so it wins.
          // See if any unassigned maxima are closer. This could be an ROI marking error.
          FindFociResult result = resultsArray.get(maximaId);
          final double dist = df.distance(x, y, z, result);
          maximaId = -1;
          float maxHeight = Float.NEGATIVE_INFINITY;

          for (int id = 0; id < assigned.length; id++) {
            // Only check the maxima that have not been assigned
            if (assigned[id] < 0) {
              result = resultsArray.get(id);

              final double newDist = df.distance(x, y, z, result);
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

          // Assign this ROI point to the alternative maxima if found
          if (maximaId != -1) {
            assigned[maximaId] = pointId;
            point.setAssignedId(maximaId);
          }
        } else {
          // Assign this ROI point to the maxima
          assigned[maximaId] = pointId;
          point.setAssignedId(maximaId);
        }
      }
    }

    // Analyse assigned points for possible errors
    final float thresholdHeight = getThresholdHeight(points, assigned, resultsArray);

    // Output results
    final List<AssignedPoint> ok = new LocalList<>(points.length);
    final List<AssignedPoint> moved = new LocalList<>(points.length);
    final List<AssignedPoint> conflict = new LocalList<>(points.length);
    final List<AssignedPoint> noAlign = new LocalList<>(points.length);

    double averageMovedDistance = 0;
    float minAssignedHeight = Float.POSITIVE_INFINITY;

    // List of ROI after moving to the assigned peak
    final List<AssignedPoint> newRoiPoints = new LocalList<>(points.length);

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

        final double distance = Math.sqrt(df.distance(x, y, z, result));

        if (result.maxValue < thresholdHeight) {
          if (settings.logAlignments) {
            log("Point [%d] %s @ %s ~> %s @ %s (%s) below height threshold (< %s)", pointId + 1,
                MathUtils.rounded(pointHeight[pointId]), cf.format(x, y, z),
                MathUtils.rounded(result.maxValue), cf.format(newX, newY, newZ),
                MathUtils.rounded(distance), MathUtils.rounded(thresholdHeight));
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
            if (settings.logAlignments) {
              log("Point [%d] %s @ %s => %s @ %s (%s)", pointId + 1,
                  MathUtils.rounded(pointHeight[pointId]), cf.format(x, y, z),
                  MathUtils.rounded(result.maxValue), cf.format(newX, newY, newZ),
                  MathUtils.rounded(distance));
            }
            newPoint = new AssignedPoint(newX, newY, newZ, point.getId());
            if (settings.showMoved && distance != 0) {
              moved.add((settings.updateOverlay) ? newPoint : point);
            } else {
              ok.add((settings.updateOverlay) ? newPoint : point);
            }
            averageMovedDistance += distance;
          } else {
            // This point is lower than another assigned to the maxima
            if (settings.logAlignments) {
              log("Point [%d] %s @ %s conflicts for assigned point [%d]", pointId + 1,
                  MathUtils.rounded(pointHeight[pointId]), cf.format(x, y, z),
                  assigned[maximaId] + 1);
            }
            conflict.add(point);

            // Output an image showing the pixels
            extractPoint(impStack, "conflict", pointId + 1, x, y, z, newX, newY, newZ);
          }
        }
      } else {
        if (settings.logAlignments) {
          log("Point [%d] %s @ %s cannot be aligned", pointId + 1,
              MathUtils.rounded(pointHeight[pointId]), cf.format(x, y, z));
        }
        noAlign.add(point);
        extractPoint(impStack, "noalign", pointId + 1, x, y, z, x, y, z);
        newPoint = null; // remove unaligned points from the updated ROI
      }

      if (newPoint != null) {
        newRoiPoints.add(newPoint);
      }
    }

    final float minHeight = MathUtils.min(pointHeight);
    if (settings.logAlignments) {
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

    addResult(createResultsWindow(), minHeight, thresholdHeight, minAssignedHeight, ok, moved,
        averageMovedDistance, conflict, noAlign, missed);
  }

  /**
   * Gets the point heights. Sorts the array by the height descending and updates the id to the
   * array position. The assigned id is set to -1.
   *
   * @param points the points
   * @param impStack the image stack
   * @return the point heights
   */
  private float[] sortDescending(AssignedPoint[] points, final ImageStack impStack) {
    final float[] pointHeight = new float[points.length];
    int id = 0;
    for (final AssignedPoint point : points) {
      point.setId(id);
      point.setAssignedId(-1);
      final int x = point.getXint();
      final int y = point.getYint();
      final int z = point.getZint();

      final int slice = imp.getStackIndex(currentC, z, currentT);
      pointHeight[id++] = impStack.getProcessor(slice).getf(x, y);
    }

    // Sort descending
    Arrays.sort(points,
        (o1, o2) -> Float.compare(pointHeight[o2.getId()], pointHeight[o1.getId()]));

    // Return sorted heights
    final float[] heights = new float[points.length];
    for (int i = 0; i < points.length; i++) {
      heights[i] = pointHeight[points[i].getId()];
      points[i].setId(i);
    }

    return heights;
  }

  private LinkedList<AssignedPoint> findMissedPoints(List<FindFociResult> resultsArray,
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
        // 3D results used 1-based z coordinates, 2D results will have z==0.
        if (maskStack != null && maskStack.getProcessor(Math.max(1, z)).get(x, y) == 0) {
          continue;
        }

        missed.add(new AssignedPoint(x, y, z, maximaId));
      }
    }
    return missed;
  }

  private TFloatArrayList findMissedHeights(List<FindFociResult> resultsArray, int[] assigned,
      double heightThreshold) {
    // List maxima above the minimum height that have not been picked
    final ImageStack maskStack = getMaskStack();
    final TFloatArrayList missed = new TFloatArrayList();
    for (int maximaId = 0; maximaId < resultsArray.size(); maximaId++) {
      final FindFociResult result = resultsArray.get(maximaId);
      if (assigned[maximaId] < 0 && result.maxValue > heightThreshold) {
        final int x = result.x;
        final int y = result.y;
        final int z = result.z;

        // Check if the point is within the mask.
        // 3D results used 1-based z coordinates, 2D results will have z==0.
        if (maskStack != null && maskStack.getProcessor(Math.max(1, z)).get(x, y) == 0) {
          continue;
        }

        missed.add(result.maxValue);
      }
    }
    missed.sort();
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
  private float getThresholdHeight(AssignedPoint[] points, int[] assigned,
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

    switch (settings.limitMethod) {
      case 1:
        // Factor of inter-quartile range
        final float q1 = getQuartileBoundary(heights, 0.25);
        final float q2 = getQuartileBoundary(heights, 0.5);

        heightThreshold = q1 - settings.factor * (q2 - q1);
        if (settings.logAlignments) {
          log("Limiting peaks %s: %s - %s * %s = %s", Settings.limitMethods[settings.limitMethod],
              MathUtils.rounded(q1), MathUtils.rounded(settings.factor), MathUtils.rounded(q2 - q1),
              MathUtils.rounded(heightThreshold));
        }

        break;

      case 2:
        // n * Std.Dev. below the mean
        final double[] stats = getStatistics(heights);
        heightThreshold = stats[0] - settings.factor * stats[1];

        if (settings.logAlignments) {
          log("Limiting peaks %s: %s - %s * %s = %s", Settings.limitMethods[settings.limitMethod],
              MathUtils.rounded(stats[0]), MathUtils.rounded(settings.factor),
              MathUtils.rounded(stats[1]), MathUtils.rounded(heightThreshold));
        }

        break;

      case 3:
        // nth Percentile
        heightThreshold = getQuartileBoundary(heights, 0.01 * settings.factor);
        if (settings.logAlignments) {
          log("Limiting peaks %s: %sth = %s", Settings.limitMethods[settings.limitMethod],
              MathUtils.rounded(settings.factor), MathUtils.rounded(heightThreshold));
        }

        break;

      case 4:
        // Number of missed points is a factor below picked points,
        // i.e. the number of potential maxima is a fraction of the number of assigned maxima.
        final TFloatArrayList missedHeights =
            findMissedHeights(resultsArray, assigned, heightThreshold);
        final double fraction = settings.factor / 100;
        final int size = heights.length;
        for (int pointId = 0; pointId < size; pointId++) {
          heightThreshold = heights[pointId];
          // Count points
          final int missedCount = countPoints(missedHeights, heightThreshold);
          final int assignedCount = size - pointId;
          final double totalCount = (double) missedCount + assignedCount;

          if ((missedCount / totalCount) < fraction) {
            break;
          }
        }

        if (heightThreshold == heights[size - 1]) {
          log("Warning: Maximum height threshold reached when attempting to limit the "
              + "number of missed peaks");
        }

        if (settings.logAlignments) {
          log("Limiting peaks %s: %s %% = %s", Settings.limitMethods[settings.limitMethod],
              MathUtils.rounded(settings.factor), MathUtils.rounded(heightThreshold));
        }
        break;

      case 0:
      default:
        // None
        break;
    }

    // Round for integer data
    if (SimpleArrayUtils.isInteger(heights)) {
      heightThreshold = Math.round(heightThreshold);
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
  private static int countPoints(TFloatArrayList missedHeights, double height) {
    // Note: Avoid a binary search as we expect the height (initialised to the lowest
    // assigned maxima) to be below the missed heights (all unassigned maxima above
    // the lowest assigned maxima).

    final int[] count = {missedHeights.size()};
    missedHeights.forEach(h -> {
      if (h < height) {
        count[0]--;
        return true;
      }
      return false;
    });
    return count[0];
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

  private ImageStack getMaskStack() {
    final ImagePlus mask = WindowManager.getImage(settings.maskImage);
    if (mask != null) {
      return mask.getStack();
    }
    return null;
  }

  private void log(String format, Object... args) {
    if (settings.logAlignments) {
      IJ.log(String.format(format, args));
    }
  }

  private void extractPoint(ImageStack impStack, String type, int id, int x, int y, int z, int newX,
      int newY, int newZ) {
    if (settings.unalignedBorder <= 0) {
      return;
    }

    if (settings.showUnaligned || saveResults) {
      final int xx = impStack.getWidth() - 1;
      final int yy = impStack.getHeight() - 1;

      final int minX = Math.min(xx, Math.max(0, Math.min(x, newX) - settings.unalignedBorder));
      final int minY = Math.min(yy, Math.max(0, Math.min(y, newY) - settings.unalignedBorder));
      final int maxX = Math.min(xx, Math.max(0, Math.max(x, newX) + settings.unalignedBorder));
      final int maxY = Math.min(yy, Math.max(0, Math.max(y, newY) + settings.unalignedBorder));
      final int w = maxX - minX + 1;
      final int h = maxY - minY + 1;
      final int nz = imp.getNSlices();
      final ImageStack newStack = new ImageStack(w, h);
      for (int slice = 1; slice <= nz; slice++) {
        final int index = imp.getStackIndex(currentC, slice, currentT);
        final ImageProcessor ip = impStack.getProcessor(index);
        ip.setRoi(minX, minY, w, h);
        newStack.addSlice(null, ip.crop());
      }
      final String title = imp.getShortTitle() + "_" + type + "_" + id;
      ImagePlus pointImp = WindowManager.getImage(title);
      if (pointImp == null) {
        pointImp = new ImagePlus(title, newStack);
      } else {
        pointImp.setStack(newStack);
      }

      pointImp.setOverlay(null);
      // If the first point XYZ is different then assume this is a correct point to display
      // (e.g. a match), the second is always displayed (e.g. an unmatched or a conflict point).
      if (newX != x || newY != y || newZ != z) {
        final List<BasePoint> ok =
            Collections.singletonList(new BasePoint(newX - minX, newY - minY, newZ));
        Match_PlugIn.addOverlay(pointImp, ok, Match_PlugIn.MATCH);
      }
      final List<BasePoint> conflict =
          Collections.singletonList(new BasePoint(x - minX, y - minY, z));
      Match_PlugIn.addOverlay(pointImp, conflict, Match_PlugIn.UNMATCH1);
      pointImp.updateAndDraw();

      if (saveResults) {
        IJ.save(pointImp, settings.resultsDirectory + File.separatorChar + title + ".tif");
      }

      if (settings.showUnaligned) {
        pointImp.show();
      }
    }
  }

  private void updateRoi(List<? extends BasePoint> newRoiPoints) {
    if (settings.updateRoi) {
      imp.setRoi(AssignedPointUtils.createRoi(imp, currentC, currentT, newRoiPoints));
    }
  }

  private void showOverlay(List<AssignedPoint> ok, List<AssignedPoint> moved,
      List<AssignedPoint> conflict, List<AssignedPoint> noAlign, List<AssignedPoint> missed) {
    // Add overlap
    if (settings.showOverlay) {
      IJ.log("Overlay key:");
      IJ.log("  OK = Green");
      if (settings.showMoved) {
        IJ.log("  Moved = Yellow");
      }
      IJ.log("  Conflict = Red");
      IJ.log("  NoAlign = Blue");
      IJ.log("  Missed = Magenta");

      imp.setOverlay(null);
      // Q. Why remove the ROI? The user can do this easily (Ctrl+Shift+E).
      // imp.killRoi();
      Match_PlugIn.addOverlay(imp, ok, Match_PlugIn.MATCH);
      Match_PlugIn.addOverlay(imp, moved, Match_PlugIn.UNMATCH1);
      Match_PlugIn.addOverlay(imp, conflict, Color.red);
      Match_PlugIn.addOverlay(imp, noAlign, Match_PlugIn.UNMATCH2);
      Match_PlugIn.addOverlay(imp, missed, Color.magenta);
      imp.updateAndDraw();
    }
  }

  private static Consumer<String> createResultsWindow() {
    if (java.awt.GraphicsEnvironment.isHeadless()) {
      if (writeHeader.compareAndSet(true, false)) {
        IJ.log(createResultsHeader());
      }
      return IJ::log;
    }
    return ImageJUtils.refresh(resultsWindowRef,
        () -> new TextWindow(TITLE + " Results", createResultsHeader(), "", 900, 300))::append;
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

  private void addResult(Consumer<String> output, float minHeight, float thresholdHeight,
      float minAssignedHeight, List<AssignedPoint> ok, List<AssignedPoint> moved,
      double averageMovedDistance, List<AssignedPoint> conflict, List<AssignedPoint> noAlign,
      List<AssignedPoint> missed) {
    final StringBuilder sb = new StringBuilder();
    sb.append(settings.resultTitle).append('\t');
    sb.append(imp.getTitle()).append('\t');
    sb.append(Settings.limitMethods[settings.limitMethod]).append('\t');
    sb.append(settings.factor).append('\t');
    sb.append(MathUtils.rounded(minHeight)).append('\t');
    sb.append(MathUtils.rounded(thresholdHeight)).append('\t');
    sb.append(MathUtils.rounded(minAssignedHeight)).append('\t');
    sb.append(ok.size()).append('\t');
    sb.append(moved.size()).append('\t');
    sb.append(MathUtils.rounded(averageMovedDistance)).append('\t');
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
    sb.append(MathUtils.rounded(match.getPrecision())).append('\t');
    sb.append(MathUtils.rounded(match.getRecall())).append('\t');
    sb.append(MathUtils.rounded(match.getJaccard())).append('\t');
    sb.append(MathUtils.rounded(match.getFScore(1)));

    output.accept(sb.toString());
  }
}
