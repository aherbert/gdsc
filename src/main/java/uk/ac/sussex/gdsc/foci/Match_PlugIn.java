/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2018 Alex Herbert
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
import uk.ac.sussex.gdsc.core.data.utils.Rounder;
import uk.ac.sussex.gdsc.core.data.utils.RounderUtils;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.match.Coordinate;
import uk.ac.sussex.gdsc.core.match.MatchCalculator;
import uk.ac.sussex.gdsc.core.match.MatchResult;
import uk.ac.sussex.gdsc.core.match.PointPair;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.core.utils.TurboList;

import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.io.OpenDialog;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.plugin.ZProjector;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

import java.awt.Color;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

/**
 * Compares the ROI points on two images and computes the match statistics.
 *
 * <p>Can output the matches for each quartile when the points are ranked using their height. Only
 * supports 2D images (no Z-stacks) but does allow selection of channel/frame that is used for the
 * heights.
 */
public class Match_PlugIn implements PlugIn {
  /**
   * Visually impaired safe colour for matches (greenish).
   */
  public static final Color MATCH = new Color(0, 158, 115);
  /**
   * Visually impaired safe colour for no match 1 (yellowish).
   */
  public static final Color UNMATCH1 = new Color(240, 228, 66);
  /**
   * Visually impaired safe colour for no match 2 (blueish).
   */
  public static final Color UNMATCH2 = new Color(86, 180, 233);

  /** The plugin title. */
  private static final String TITLE = "Match Calculator";
  private static final String[] DISTANCE_TYPES = new String[] {"Relative", "Absolute"};

  private static final String INPUT1 = "Input_1";
  private static final String INPUT2 = "Input_2";

  private static String title1 = "";
  private static String title2 = "";
  private static int dType = 0;
  private static double distanceThreshold = 0.05;

  // For memory mode
  private static String name1 = "";
  private static String name2 = "";
  private static String[] unitTypes = new String[] {"Pixel", "Calibrated"};
  private static int unitType = 0;
  private static double memoryThreshold = 5;

  private static String[] overlayTypes = new String[] {"None", "Colour", "Colour safe"};
  private static int overlay = 1;
  private static boolean quartiles = false;
  private static boolean scatter = true;
  private static boolean unmatchedDistribution = true;
  private static boolean matchTable = false;
  private static boolean saveMatches = false;
  private static String filename = "";
  private static int findFociImageIndex = 0;
  //@formatter:off
  private static String[] findFociResult = new String[] {
      "Intensity",
      "Intensity above saddle",
      "Intensity above background",
      "Count",
      "Count above saddle",
      "Max value",
      "Highest saddle value", };
  //@formatter:on
  private static int findFociResultChoiceIndex = 0;

  private static boolean writeHeader = true;
  private static boolean writeUnmatchedHeader = true;
  private static boolean writeMatchedHeader = true;

  private static TextWindow resultsWindow = null;
  private static TextWindow unmatchedWindow = null;
  private static TextWindow matchedWindow = null;
  private boolean memoryMode = false;
  private boolean fileMode = false;
  private boolean imageMode = false;
  private static int channel1 = 1;
  private static int frame1 = 1;
  private static int channel2 = 1;
  private static int frame2 = 1;
  private String t1 = "";
  private String t2 = "";
  private Coordinate[] actualPoints = null;
  private Coordinate[] predictedPoints = null;

  // For memory mode
  private String unit;

  private double scaleX = 1;
  private double scaleY = 1;
  private double scaleZ = 1;

  /** {@inheritDoc} */
  @Override
  @SuppressWarnings("unchecked")
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    memoryMode = "memory".equals(arg);
    fileMode = !memoryMode && "file".equals(arg);
    imageMode = !memoryMode && !fileMode;

    if (!showDialog()) {
      return;
    }

    actualPoints = null;
    predictedPoints = null;
    double localDistanceThreshold = 0;
    boolean doQuartiles = false;
    boolean doScatter = false;
    boolean doUnmatched = false;
    ImagePlus imp1 = null;
    ImagePlus imp2 = null;

    t1 = title1;
    t2 = title2;

    if (memoryMode) {
      t1 = name1;
      t2 = name2;

      FindFociMemoryResults m1;
      FindFociMemoryResults m2;

      try {
        m1 = getFindFociMemoryResults(t1);
        actualPoints = getFindFociPoints(m1, t1);
        m2 = getFindFociMemoryResults(t2);
        predictedPoints = getFindFociPoints(m2, t2);
      } catch (final IllegalStateException ex) {
        IJ.error("Failed to load the points: " + ex.getMessage());
        return;
      }
      localDistanceThreshold = memoryThreshold;

      // Support image analysis if the images are open
      imp1 = WindowManager.getImage(m1.imageId);
      imp2 = WindowManager.getImage(m2.imageId);

      final boolean canExtractHeights = canExtractHeights(imp1, imp2);
      doQuartiles = quartiles && canExtractHeights;
      doScatter = scatter && canExtractHeights;
      doUnmatched = unmatchedDistribution && canExtractHeights;

      if (doQuartiles || doScatter || doUnmatched || matchTable) {
        // Extract the heights for each point
        actualPoints = extractPoints(imp1, actualPoints, channel1, frame1);
        predictedPoints = extractPoints(imp2, predictedPoints, channel2, frame2);
      }
    } else if (fileMode) {
      try {
        actualPoints = AssignedPointUtils.loadPoints(t1);
        predictedPoints = AssignedPointUtils.loadPoints(t2);
      } catch (final IOException ex) {
        IJ.error("Failed to load the points: " + ex.getMessage());
        return;
      }
      localDistanceThreshold = distanceThreshold;
    } else {
      imp1 = WindowManager.getImage(t1);
      if (imp1 == null) {
        IJ.error("Failed to load image1: " + t1);
        return;
      }
      imp2 = WindowManager.getImage(t2);
      if (imp2 == null) {
        IJ.error("Failed to load image2: " + t2);
        return;
      }

      if (dType == 1) {
        localDistanceThreshold = distanceThreshold;
      } else {
        final int length1 = Math.min(imp1.getWidth(), imp1.getHeight());
        final int length2 = Math.min(imp2.getWidth(), imp2.getHeight());
        localDistanceThreshold = Math.ceil(distanceThreshold * Math.max(length1, length2));
      }

      actualPoints = AssignedPointUtils.extractRoiPoints(imp1.getRoi());
      predictedPoints = AssignedPointUtils.extractRoiPoints(imp2.getRoi());

      final boolean canExtractHeights = canExtractHeights(imp1, imp2);
      doQuartiles = quartiles && canExtractHeights;
      doScatter = scatter && canExtractHeights;
      doUnmatched = unmatchedDistribution && canExtractHeights;

      if (doQuartiles || doScatter || doUnmatched || matchTable) {
        // Extract the heights for each point
        actualPoints = extractPoints(imp1, actualPoints, channel1, frame1);
        predictedPoints = extractPoints(imp2, predictedPoints, channel2, frame2);
      }
    }

    if (unit == null) {
      unit = "px";
    }

    final Object[] points =
        compareRoi(actualPoints, predictedPoints, localDistanceThreshold, doQuartiles);

    final List<Coordinate> truePositives = (List<Coordinate>) points[0];
    final List<Coordinate> falsePositives = (List<Coordinate>) points[1];
    final List<Coordinate> falseNegatives = (List<Coordinate>) points[2];
    final List<PointPair> matches = (List<PointPair>) points[3];
    final MatchResult result = (MatchResult) points[4];

    if (overlay > 0 && (imageMode || memoryMode)) {
      // Imp2 is the predicted, show the overlay on this
      imp2.setOverlay(null);
      imp2.saveRoi();
      imp2.killRoi();
      Color match;
      Color unmatch1;
      Color unmatch2;
      if (overlay == 2) {
        // Use colour blind friendly colours
        match = MATCH;
        unmatch1 = UNMATCH1;
        unmatch2 = UNMATCH2;
      } else {
        match = Color.YELLOW;
        unmatch1 = Color.RED;
        unmatch2 = Color.GREEN;
      }
      addOverlay(imp2, truePositives, match, scaleX, scaleY, scaleZ);
      addOverlay(imp2, falseNegatives, unmatch1, scaleX, scaleY, scaleZ);
      addOverlay(imp2, falsePositives, unmatch2, scaleX, scaleY, scaleZ);
      imp2.updateAndDraw();
    }

    // Output a scatter plot of actual vs predicted
    if (doScatter) {
      scatterPlot(imp1, imp2, matches, falsePositives, falseNegatives);
    }

    // Show analysis of the height distribution of the unmatched points
    if (doUnmatched) {
      unmatchedAnalysis(t1, t2, matches, falsePositives, falseNegatives);
    }

    if (saveMatches) {
      saveMatches(localDistanceThreshold, matches, falsePositives, falseNegatives, result);
    }

    if (matchTable) {
      addIntensityFromFindFoci(matches, falsePositives, falseNegatives);
      showMatches(matches, falsePositives, falseNegatives);
    }
  }

  private static FindFociMemoryResults getFindFociMemoryResults(String resultsName) {
    final FindFociMemoryResults memoryResults = FindFoci_PlugIn.getResults(resultsName);
    if (memoryResults == null) {
      throw new IllegalStateException("No foci with the name " + resultsName);
    }
    final List<FindFociResult> results = memoryResults.results;
    if (results.isEmpty()) {
      throw new IllegalStateException("Zero foci in the results with the name " + resultsName);
    }
    return memoryResults;
  }

  private Coordinate[] getFindFociPoints(FindFociMemoryResults memoryResults, String resultsName) {
    final List<FindFociResult> results = memoryResults.results;

    // If using calibration then we must convert the coordinates
    if (unitType == 1) {
      // Get the calibration
      final Calibration calibration = memoryResults.calibration;
      if (calibration == null) {
        throw new IllegalStateException(
            "No calibration for the results with the name " + resultsName);
      }

      final String unit = calibration.getUnit();
      if (this.unit != null && !this.unit.equals(unit)) {
        throw new IllegalStateException("Calibration units for the results are different");
      }
      this.unit = unit;

      // The units must be the same
      if (!unit.equals(calibration.getYUnit()) || !unit.equals(calibration.getZUnit())) {
        throw new IllegalStateException(
            "Calibration units are different in different dimensions for the results with the name "
                + resultsName);
      }

      scaleX = calibration.pixelWidth;
      scaleY = calibration.pixelHeight;
      scaleZ = calibration.pixelDepth;

      final uk.ac.sussex.gdsc.core.match.BasePoint[] points =
          new uk.ac.sussex.gdsc.core.match.BasePoint[results.size()];
      int index = 0;
      for (final FindFociResult result : results) {
        points[index++] = new uk.ac.sussex.gdsc.core.match.BasePoint(convert(result.x, scaleX),
            convert(result.y, scaleY), convert(result.z, scaleZ));
      }
      return points;
    }

    // Native pixel units
    final AssignedPoint[] points = new AssignedPoint[results.size()];
    int index = 0;
    for (final FindFociResult result : results) {
      points[index++] = new AssignedPoint(result.x, result.y, result.z, 0);
    }
    return points;
  }

  private static float convert(int x, double cx) {
    return (float) (x * cx);
  }

  /**
   * Check if heights can be extracted from the image.
   *
   * @param imp1 the imp 1
   * @param imp2 the imp 2
   * @return True if heights can be extracted from the image.
   */
  private boolean canExtractHeights(ImagePlus imp1, ImagePlus imp2) {
    final int[] dim1 = imp1.getDimensions();
    final int[] dim2 = imp2.getDimensions();

    // Note: Z-projection is used for heights so number of slices does not matter

    if ((dim1[2] != 1 || dim1[4] != 1) || (dim2[2] != 1 || dim2[4] != 1)) {
      // Select channel/frame
      final GenericDialog gd = new GenericDialog("Select Channel/Frame");
      gd.addMessage("Stacks detected.\nPlease select the channel/frame.");
      final String[] channels1 = getChannels(imp1);
      final String[] frames1 = getFrames(imp1);
      final String[] channels2 = getChannels(imp2);
      final String[] frames2 = getFrames(imp2);

      if (channels1.length > 1) {
        gd.addChoice("Image1_Channel", channels1,
            channels1[channels1.length >= channel1 ? channel1 - 1 : 0]);
      }
      if (frames1.length > 1) {
        gd.addChoice("Image1_Frame", frames1, frames1[frames1.length >= frame1 ? frame1 - 1 : 0]);
      }
      if (channels2.length > 1) {
        gd.addChoice("Image2_Channel", channels2,
            channels2[channels2.length >= channel2 ? channel2 - 1 : 0]);
      }
      if (frames2.length > 1) {
        gd.addChoice("Image2_Frame", frames2, frames2[frames2.length >= frame2 ? frame2 - 1 : 0]);
      }

      gd.showDialog();

      if (gd.wasCanceled()) {
        return false;
      }

      // Extract the channel/frame
      channel1 = (channels1.length > 1) ? gd.getNextChoiceIndex() + 1 : 1;
      frame1 = (frames1.length > 1) ? gd.getNextChoiceIndex() + 1 : 1;
      channel2 = (channels2.length > 1) ? gd.getNextChoiceIndex() + 1 : 1;
      frame2 = (frames2.length > 1) ? gd.getNextChoiceIndex() + 1 : 1;

      t1 += " c" + channel1 + " t" + frame1;
      t2 += " c" + channel2 + " t" + frame2;
    }

    return true;
  }

  private static String[] getChannels(ImagePlus imp) {
    final int c = imp.getNChannels();
    final String[] result = new String[c];
    for (int i = 0; i < c; i++) {
      result[i] = Integer.toString(i + 1);
    }
    return result;
  }

  private static String[] getFrames(ImagePlus imp) {
    final int c = imp.getNFrames();
    final String[] result = new String[c];
    for (int i = 0; i < c; i++) {
      result[i] = Integer.toString(i + 1);
    }
    return result;
  }

  private TimeValuedPoint[] extractPoints(ImagePlus imp, Coordinate[] actualPoints, int channel,
      int frame) {
    // Use maximum intensity projection
    final ImageProcessor ip = ImageJUtils.extractTile(imp, frame, channel, ZProjector.MAX_METHOD);

    // Store ID as the time
    final TimeValuedPoint[] newPoints = new TimeValuedPoint[actualPoints.length];
    for (int i = 0; i < newPoints.length; i++) {
      final int x = (int) Math.round(actualPoints[i].getX() / scaleX);
      final int y = (int) Math.round(actualPoints[i].getY() / scaleY);
      final int value = ip.get(x, y);
      newPoints[i] = new TimeValuedPoint(actualPoints[i].getX(), actualPoints[i].getY(),
          actualPoints[i].getZ(), i + 1, value);
    }

    return newPoints;
  }

  /**
   * Adds an ROI point overlay to the image using the specified colour.
   *
   * @param imp the image
   * @param list the list
   * @param color the color
   */
  public static void addOverlay(ImagePlus imp, List<? extends Coordinate> list, Color color) {
    addOverlay(imp, list, color, 1, 1, 1);
  }

  /**
   * Adds an ROI point overlay to the image using the specified colour.
   *
   * @param imp the image
   * @param list the list
   * @param color the color
   * @param scaleX the scale X
   * @param scaleY the scale Y
   * @param scaleZ the scale Z
   */
  public static void addOverlay(ImagePlus imp, List<? extends Coordinate> list, Color color,
      double scaleX, double scaleY, double scaleZ) {
    if (list.isEmpty()) {
      return;
    }

    final Color strokeColor = color;
    final Color fillColor = color;

    Overlay overlay = imp.getOverlay();
    if (overlay == null) {
      overlay = new Overlay();
    }

    for (final PointRoi roi : createRoi(imp, list, scaleX, scaleY, scaleZ)) {
      roi.setStrokeColor(strokeColor);
      roi.setFillColor(fillColor);
      roi.setShowLabels(false);
      overlay.add(roi);
    }
    imp.setOverlay(overlay);
  }

  /**
   * Creates an ImageJ PointRoi from the list of points, one per slice.
   *
   * @param imp the image
   * @param array List of points
   * @param scaleX the scale X
   * @param scaleY the scale Y
   * @param scaleZ the scale Z
   * @return The PointRoi
   */
  public static PointRoi[] createRoi(final ImagePlus imp, List<? extends Coordinate> array,
      double scaleX, double scaleY, double scaleZ) {
    // We have to create an overlay per z-slice using the calibration scale
    final TIntObjectHashMap<TIntArrayList> xpoints = new TIntObjectHashMap<>();
    TIntArrayList nextlist = new TIntArrayList();

    for (final Coordinate point : array) {
      final int x = (int) Math.round(point.getX() / scaleX);
      final int y = (int) Math.round(point.getY() / scaleY);
      final int z = (int) Math.round(point.getZ() / scaleZ);

      TIntArrayList list = xpoints.putIfAbsent(z, nextlist);
      if (list == null) {
        list = nextlist;
        nextlist = new TIntArrayList();
      }

      list.add(x);
      list.add(y);
    }

    final int channel = 0;
    final int frame = 0;
    final boolean isHyperStack = imp.isDisplayedHyperStack();

    final TurboList<PointRoi> rois = new TurboList<>(xpoints.size());
    xpoints.forEachEntry((z, b) -> {
      final int[] data = b.toArray();
      final float[] x = new float[data.length / 2];
      final float[] y = new float[x.length];
      for (int i = 0, j = 0; j < x.length; j++) {
        x[j] = data[i++];
        y[j] = data[i++];
      }
      final PointRoi roi = new PointRoi(x, y);
      if (isHyperStack) {
        roi.setPosition(channel, z, frame);
      } else {
        roi.setPosition(imp.getStackIndex(channel, z, frame));
      }
      rois.add(roi);
      return true;
    });

    return rois.toArray(new PointRoi[rois.size()]);
  }

  private boolean showDialog() {
    String[] items = null;
    String initialTitle1 = title1;
    String initialTitle2 = title2;
    double initialDistanceThreshold = distanceThreshold;

    if (memoryMode) {
      items = FindFoci_PlugIn.getResultsNames();
      if (items.length < 2) {
        IJ.showMessage(TITLE, "Require 2 results in memory");
        return false;
      }

      final List<String> imageList = Arrays.asList(items);
      int index = 0;
      initialTitle1 = (imageList.contains(name1) ? name1 : imageList.get(index++));
      initialTitle2 = (imageList.contains(name2) ? name2 : imageList.get(index));
      initialDistanceThreshold = memoryThreshold;
    }
    if (imageMode) {
      final List<String> imageList = new LinkedList<>();
      for (final int id : uk.ac.sussex.gdsc.core.ij.ImageJUtils.getIdList()) {
        final ImagePlus imp = WindowManager.getImage(id);
        if (imp != null) {
          final Roi roi = imp.getRoi();
          // Allow no ROI => No points
          if ((roi == null || roi.getType() == Roi.POINT) && !imp.getTitle().startsWith(TITLE)) {
            imageList.add(imp.getTitle());
          }
        }
      }

      if (imageList.size() < 2) {
        IJ.showMessage(TITLE, "Require 2 images open with point ROI");
        return false;
      }

      items = imageList.toArray(new String[imageList.size()]);
      int index = 0;
      initialTitle1 = (imageList.contains(title1) ? title1 : imageList.get(index++));
      initialTitle2 = (imageList.contains(title2) ? title2 : imageList.get(index));
    }

    final GenericDialog gd = new GenericDialog(TITLE);

    if (memoryMode) {
      gd.addMessage(
          "Compare the points between 2 FindFoci results\nand compute the match statistics");
      gd.addChoice(INPUT1, items, initialTitle1);
      gd.addChoice(INPUT2, items, initialTitle2);
      gd.addMessage("Distance between matching points in:");
      gd.addChoice("Unit", unitTypes, unitTypes[unitType]);
    } else if (fileMode) {
      gd.addMessage("Compare the points in two files\nand compute the match statistics");
      gd.addStringField(INPUT1, initialTitle1, 30);
      gd.addStringField(INPUT2, initialTitle2, 30);
      gd.addMessage("Distance between matching points in pixels");
    } else {
      gd.addMessage("Compare the ROI points between 2 images\nand compute the match statistics");
      gd.addChoice(INPUT1, items, initialTitle1);
      gd.addChoice(INPUT2, items, initialTitle2);
      gd.addMessage(
          "Distance between matching points in pixels, or fraction of\n" + "image edge length");
      gd.addChoice("Distance_type", DISTANCE_TYPES, DISTANCE_TYPES[dType]);
    }
    gd.addNumericField("Distance", initialDistanceThreshold, 2);
    if (imageMode || memoryMode) {
      gd.addChoice("Overlay", overlayTypes, overlayTypes[overlay]);
      gd.addCheckbox("Quartiles", quartiles);
      gd.addCheckbox("Scatter_plot", scatter);
      gd.addCheckbox("Unmatched_distribution", unmatchedDistribution);
      gd.addCheckbox("Match_table", matchTable);
    }
    gd.addCheckbox("Save_matches", saveMatches);
    List<FindFociResult> resultsArray = null;
    if (!memoryMode) {
      resultsArray = FindFoci_PlugIn.getLastResults();
      if (resultsArray != null) {
        final String[] imageItems = new String[] {"[None]", "Image1", "Image2"};
        gd.addChoice("FindFoci_image", imageItems, imageItems[findFociImageIndex]);
        gd.addChoice("FindFoci_result", findFociResult, findFociResult[findFociResultChoiceIndex]);
      }
    }

    gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.FIND_FOCI);
    gd.showDialog();

    if (gd.wasCanceled()) {
      return false;
    }

    if (memoryMode) {
      name1 = gd.getNextChoice();
      name2 = gd.getNextChoice();
      unitType = gd.getNextChoiceIndex();
    } else if (fileMode) {
      title1 = gd.getNextString();
      title2 = gd.getNextString();
    } else {
      title1 = gd.getNextChoice();
      title2 = gd.getNextChoice();
      dType = gd.getNextChoiceIndex();
    }
    initialDistanceThreshold = gd.getNextNumber();
    if (memoryMode) {
      memoryThreshold = initialDistanceThreshold;
    } else {
      distanceThreshold = initialDistanceThreshold;
    }
    if (imageMode || memoryMode) {
      overlay = gd.getNextChoiceIndex();
      quartiles = gd.getNextBoolean();
      scatter = gd.getNextBoolean();
      unmatchedDistribution = gd.getNextBoolean();
      matchTable = gd.getNextBoolean();
    }
    saveMatches = gd.getNextBoolean();
    findFociImageIndex = 0;
    if (resultsArray != null) {
      findFociImageIndex = gd.getNextChoiceIndex();
      findFociResultChoiceIndex = gd.getNextChoiceIndex();
    }

    return true;
  }

  private Object[] compareRoi(Coordinate[] actualPoints, Coordinate[] predictedPoints,
      double distanceThreshold, boolean doQuartiles) {
    final List<Coordinate> truePositives = new LinkedList<>();
    final List<Coordinate> falsePositives = new LinkedList<>();
    final List<Coordinate> falseNegatives = new LinkedList<>();
    final List<PointPair> matches = new LinkedList<>();
    final MatchResult result = (memoryMode)
        ? MatchCalculator.analyseResults3D(actualPoints, predictedPoints, distanceThreshold,
            truePositives, falsePositives, falseNegatives, matches)
        : MatchCalculator.analyseResults2D(actualPoints, predictedPoints, distanceThreshold,
            truePositives, falsePositives, falseNegatives, matches);
    final MatchResult[] quartileResults =
        (doQuartiles) ? compareQuartiles(actualPoints, predictedPoints, distanceThreshold) : null;

    String header = null;

    if (!java.awt.GraphicsEnvironment.isHeadless()) {
      if (doQuartiles) {
        header = createResultsHeader(quartileResults);
        ImageJUtils.refreshHeadings(resultsWindow, header, true);
      }

      if (resultsWindow == null || !resultsWindow.isShowing()) {
        if (header == null) {
          header = createResultsHeader(quartileResults);
        }
        resultsWindow = new TextWindow(TITLE + " Results", header, "", 900, 300);
      }
    } else if (writeHeader) {
      header = createResultsHeader(quartileResults);
      writeHeader = false;
      IJ.log(header);
    }
    addResult(t1, t2, distanceThreshold, result, quartileResults);

    return new Object[] {truePositives, falsePositives, falseNegatives, matches, result};
  }

  /**
   * Compare the match results for the points within each height quartile.
   *
   * @param actualPoints the actual points
   * @param predictedPoints the predicted points
   * @param distanceThreshold the d threshold
   * @return An array of 4 quartile results (or null if there are no points)
   */
  private static MatchResult[] compareQuartiles(Coordinate[] actualPoints,
      Coordinate[] predictedPoints, double distanceThreshold) {
    final TimeValuedPoint[] actualValuedPoints = (TimeValuedPoint[]) actualPoints;
    final TimeValuedPoint[] predictedValuedPoints = (TimeValuedPoint[]) predictedPoints;

    // Combine points and sort
    final float[] heights = extractHeights(actualValuedPoints, predictedValuedPoints);

    if (heights.length == 0) {
      return null;
    }

    final float[] quartiles = getQuartiles(heights);

    // Process each quartile
    final MatchResult[] quartileResults = new MatchResult[4];
    for (int q = 0; q < 4; q++) {
      final TimeValuedPoint[] actual =
          extractPointsWithinRange(actualValuedPoints, quartiles[q], quartiles[q + 1]);
      final TimeValuedPoint[] predicted =
          extractPointsWithinRange(predictedValuedPoints, quartiles[q], quartiles[q + 1]);
      quartileResults[q] = MatchCalculator.analyseResults2D(actual, predicted, distanceThreshold);
    }

    return quartileResults;
  }

  /**
   * Extract all the heights from the two sets of valued points.
   */
  private static float[] extractHeights(TimeValuedPoint[] actualPoints,
      TimeValuedPoint[] predictedPoints) {
    final HashSet<TimeValuedPoint> nonDuplicates = new HashSet<>();
    nonDuplicates.addAll(Arrays.asList(actualPoints));
    nonDuplicates.addAll(Arrays.asList(predictedPoints));

    final TFloatArrayList heights = new TFloatArrayList(nonDuplicates.size());
    for (final TimeValuedPoint p : nonDuplicates) {
      heights.add(p.getValue());
    }
    heights.sort();
    return heights.toArray();
  }

  /**
   * Extract the points that are within the specified height limits.
   */
  private static TimeValuedPoint[] extractPointsWithinRange(TimeValuedPoint[] points, float lower,
      float upper) {
    final LinkedList<TimeValuedPoint> list = new LinkedList<>();
    for (final TimeValuedPoint p : points) {
      if (p.getValue() >= lower && p.getValue() < upper) {
        list.add(p);
      }
    }
    return list.toArray(new TimeValuedPoint[list.size()]);
  }

  /**
   * Count the points that are within the specified limits.
   */
  private static int countPoints(TimeValuedPoint[] points, float lower, float upper) {
    int count = 0;
    for (final TimeValuedPoint p : points) {
      if (p.getValue() >= lower && p.getValue() < upper) {
        count++;
      }
    }
    return count;
  }

  private static String createResultsHeader(MatchResult[] quartileResults) {
    final StringBuilder sb = new StringBuilder();
    sb.append("Image 1\t");
    sb.append("Image 2\t");
    sb.append("Distance\t");
    sb.append("Unit\t");
    sb.append("N 1\t");
    sb.append("N 2\t");
    sb.append("Match\t");
    sb.append("Unmatch 1\t");
    sb.append("Unmatch 2\t");
    sb.append("Jaccard\t");
    sb.append("Recall 1\t");
    sb.append("Recall 2\t");
    sb.append("F1");
    if (quartileResults != null) {
      for (int q = 0; q < 4; q++) {
        addQuartileHeader(sb, "Q" + (q + 1));
      }
    }
    return sb.toString();
  }

  private static void addQuartileHeader(StringBuilder sb, String quartile) {
    sb.append("\t \t");
    sb.append(quartile).append(" ").append("N1\t");
    sb.append(quartile).append(" ").append("N2\t");
    sb.append(quartile).append(" ").append("M\t");
    sb.append(quartile).append(" ").append("U1\t");
    sb.append(quartile).append(" ").append("U2\t");
    sb.append(quartile).append(" ").append("Jaccard\t");
    sb.append(quartile).append(" ").append("Recall1\t");
    sb.append(quartile).append(" ").append("Recall2\t");
    sb.append(quartile).append(" ").append("F1");
  }

  private void addResult(String i1, String i2, double distanceThrehsold, MatchResult result,
      MatchResult[] quartileResults) {
    final StringBuilder sb = new StringBuilder();
    sb.append(i1).append('\t');
    sb.append(i2).append('\t');
    sb.append(IJ.d2s(distanceThrehsold, 2)).append('\t');
    sb.append(unit).append('\t');
    sb.append(result.getNumberActual()).append('\t');
    sb.append(result.getNumberPredicted()).append('\t');
    sb.append(result.getTruePositives()).append('\t');
    sb.append(result.getFalseNegatives()).append('\t');
    sb.append(result.getFalsePositives()).append('\t');
    sb.append(IJ.d2s(result.getJaccard(), 4)).append('\t');
    sb.append(IJ.d2s(result.getRecall(), 4)).append('\t');
    sb.append(IJ.d2s(result.getPrecision(), 4)).append('\t');
    sb.append(IJ.d2s(result.getFScore(1.0), 4));

    if (quartileResults != null) {
      for (int q = 0; q < 4; q++) {
        addQuartileResult(sb, quartileResults[q]);
      }
    }

    if (java.awt.GraphicsEnvironment.isHeadless()) {
      IJ.log(sb.toString());
    } else {
      resultsWindow.append(sb.toString());
    }
  }

  private static void addQuartileResult(StringBuilder sb, MatchResult result) {
    sb.append("\t \t");
    sb.append(result.getNumberActual()).append('\t');
    sb.append(result.getNumberPredicted()).append('\t');
    sb.append(result.getTruePositives()).append('\t');
    sb.append(result.getFalseNegatives()).append('\t');
    sb.append(result.getFalsePositives()).append('\t');
    sb.append(IJ.d2s(result.getJaccard(), 4)).append('\t');
    sb.append(IJ.d2s(result.getRecall(), 4)).append('\t');
    sb.append(IJ.d2s(result.getPrecision(), 4)).append('\t');
    sb.append(IJ.d2s(result.getFScore(1.0), 4));
  }

  /**
   * Build a scatter plot of the matches and the false positives/negatives using the image values
   * for the X/Y axes.
   *
   * @param imp1 - Actual
   * @param imp2 - Predicted
   * @param matches the matches
   * @param falsePositives the false positives
   * @param falseNegatives the false negatives
   */
  private static void scatterPlot(ImagePlus imp1, ImagePlus imp2, List<PointPair> matches,
      List<Coordinate> falsePositives, List<Coordinate> falseNegatives) {
    if (matches.isEmpty() && falsePositives.isEmpty() && falseNegatives.isEmpty()) {
      return;
    }

    // Build the values to plot
    final float[] xMatch = new float[matches.size()];
    final float[] yMatch = new float[matches.size()];
    final float[] xNoMatch1 = new float[falseNegatives.size()];
    final float[] yNoMatch1 = new float[falseNegatives.size()];
    final float[] xNoMatch2 = new float[falsePositives.size()];
    final float[] yNoMatch2 = new float[falsePositives.size()];

    int count = 0;
    float minimum = Float.POSITIVE_INFINITY;
    float maximum = 0;
    for (final PointPair pair : matches) {
      final TimeValuedPoint p1 = (TimeValuedPoint) pair.getPoint1();
      final TimeValuedPoint p2 = (TimeValuedPoint) pair.getPoint2();
      xMatch[count] = p1.getValue(); // Actual
      yMatch[count] = p2.getValue(); // Predicted
      final float max = Math.max(xMatch[count], yMatch[count]);
      final float min = Math.min(xMatch[count], yMatch[count]);
      if (maximum < max) {
        maximum = max;
      }
      if (minimum > min) {
        minimum = min;
      }
      count++;
    }
    count = 0;
    // Actual
    for (final Coordinate point : falseNegatives) {
      final TimeValuedPoint p = (TimeValuedPoint) point;
      xNoMatch1[count++] = p.getValue();
      if (maximum < p.getValue()) {
        maximum = p.getValue();
      }
      minimum = 0;
    }
    // Predicted
    count = 0;
    for (final Coordinate point : falsePositives) {
      final TimeValuedPoint p = (TimeValuedPoint) point;
      yNoMatch2[count++] = p.getValue();
      if (maximum < p.getValue()) {
        maximum = p.getValue();
      }
      minimum = 0;
    }

    // Create a new plot
    final String title = TITLE + " : " + imp1.getTitle() + " vs " + imp2.getTitle();

    final Plot plot =
        new Plot(title, imp1.getTitle(), imp2.getTitle(), (float[]) null, (float[]) null);
    // Ensure the plot is square
    float range = maximum - minimum;
    if (range == 0) {
      range = 10;
    }
    maximum += range * 0.05;
    minimum -= range * 0.05;
    plot.setLimits(minimum, maximum, minimum, maximum);
    plot.setFrameSize(300, 300);

    plot.setColor(MATCH);
    plot.addPoints(xMatch, yMatch, Plot.X);
    plot.setColor(UNMATCH1);
    plot.addPoints(xNoMatch1, yNoMatch1, Plot.CROSS);
    plot.setColor(UNMATCH2);
    plot.addPoints(xNoMatch2, yNoMatch2, Plot.CROSS);

    // Find old plot
    ImagePlus oldPlot = null;
    for (final int id : ImageJUtils.getIdList()) {
      final ImagePlus imp = WindowManager.getImage(id);
      if (imp != null && imp.getTitle().equals(title)) {
        oldPlot = imp;
        break;
      }
    }

    // Update plot or draw a new one
    if (oldPlot != null) {
      oldPlot.setProcessor(plot.getProcessor());
      oldPlot.updateAndDraw();
    } else {
      plot.show();
    }
  }

  /**
   * Build a table showing the percentage of unmatched points that fall within each quartile of the
   * matched points.
   *
   * @param title1 - Actual
   * @param title2 - Predicted
   * @param matches the matches
   * @param falsePositives the false positives
   * @param falseNegatives the false negatives
   */
  private static void unmatchedAnalysis(String title1, String title2, List<PointPair> matches,
      List<Coordinate> falsePositives, List<Coordinate> falseNegatives) {
    if (matches.isEmpty() && falsePositives.isEmpty() && falseNegatives.isEmpty()) {
      return;
    }

    // Extract the heights of the matched points. Use the average height of each match.
    final TFloatArrayList heights = new TFloatArrayList(matches.size());
    for (final PointPair pair : matches) {
      final TimeValuedPoint p1 = (TimeValuedPoint) pair.getPoint1();
      final TimeValuedPoint p2 = (TimeValuedPoint) pair.getPoint2();
      heights.add((p1.getValue() + p2.getValue()) / 2f);
    }
    heights.sort();

    // Get the quartile ranges
    final float[] quartiles = getQuartiles(heights.toArray());

    // Extract the valued points
    final TimeValuedPoint[] actualPoints = extractValuedPoints(falseNegatives);
    final TimeValuedPoint[] predictedPoints = extractValuedPoints(falsePositives);

    // Count the number of unmatched points from each image in each quartile
    final int[] actualCount = new int[6];
    final int[] predictedCount = new int[6];

    actualCount[0] = countPoints(actualPoints, Float.NEGATIVE_INFINITY, quartiles[0]);
    predictedCount[0] = countPoints(predictedPoints, Float.NEGATIVE_INFINITY, quartiles[0]);
    for (int q = 0; q < 4; q++) {
      actualCount[q + 1] = countPoints(actualPoints, quartiles[q], quartiles[q + 1]);
      predictedCount[q + 1] = countPoints(predictedPoints, quartiles[q], quartiles[q + 1]);
    }
    actualCount[5] = countPoints(actualPoints, quartiles[4], Float.POSITIVE_INFINITY);
    predictedCount[5] = countPoints(predictedPoints, quartiles[4], Float.POSITIVE_INFINITY);

    // Show a result table
    final String header = "Image 1\tN\t% <Q1\t% Q1\t% Q2\t% Q3\t% Q4\t% >Q4\tImage2\tN\t% <Q1\t"
        + "% Q1\t% Q2\t% Q3\t% Q4\t% >Q4";
    if (!java.awt.GraphicsEnvironment.isHeadless()) {
      if (unmatchedWindow == null || !unmatchedWindow.isShowing()) {
        unmatchedWindow = new TextWindow(TITLE + " Unmatched", header, "", 900, 300);
      }
    } else if (writeUnmatchedHeader) {
      writeUnmatchedHeader = false;
      IJ.log(header);
    }
    addUnmatchedResult(title1, title2, actualCount, predictedCount);
  }

  private static float[] getQuartiles(float[] heights) {
    return new float[] {heights[0], PointAligner_PlugIn.getQuartileBoundary(heights, 0.25),
        PointAligner_PlugIn.getQuartileBoundary(heights, 0.5),
        PointAligner_PlugIn.getQuartileBoundary(heights, 0.75), heights[heights.length - 1] + 1};
  }

  private static TimeValuedPoint[] extractValuedPoints(List<Coordinate> list) {
    final TimeValuedPoint[] points = new TimeValuedPoint[list.size()];
    int index = 0;
    for (final Coordinate p : list) {
      points[index++] = (TimeValuedPoint) p;
    }
    return points;
  }

  private static void addUnmatchedResult(String title1, String title2, int[] actualCount,
      int[] predictedCount) {
    final StringBuilder sb = new StringBuilder();
    addQuartiles(sb, title1, actualCount);
    sb.append('\t');
    addQuartiles(sb, title2, predictedCount);

    if (java.awt.GraphicsEnvironment.isHeadless()) {
      IJ.log(sb.toString());
    } else {
      unmatchedWindow.append(sb.toString());
    }
  }

  private static void addQuartiles(StringBuilder sb, String imageTitle, int[] counts) {
    // Count the total number of create a scale factor to calculate the percentage
    int total = 0;
    for (final int c : counts) {
      total += c;
    }

    sb.append(imageTitle).append('\t').append(total);

    if (total > 0) {
      final double factor = total / 100.0;
      for (final int c : counts) {
        sb.append('\t');
        sb.append(IJ.d2s(c / factor, 1));
      }
    } else {
      for (int c = counts.length; c-- > 0;) {
        sb.append("\t-");
      }
    }
  }

  /**
   * Saves the matches and the false positives/negatives to file.
   *
   * @param distance the distance
   * @param matches the matches
   * @param falsePositives the false positives
   * @param falseNegatives the false negatives
   * @param result the result
   */
  private void saveMatches(double distance, List<PointPair> matches,
      List<Coordinate> falsePositives, List<Coordinate> falseNegatives, MatchResult result) {
    if (matches.isEmpty() && falsePositives.isEmpty() && falseNegatives.isEmpty()) {
      return;
    }

    final String[] path = ImageJUtils.decodePath(filename);
    final OpenDialog chooser = new OpenDialog("matches_file", path[0], path[1]);
    if (chooser.getFileName() == null) {
      return;
    }

    filename = chooser.getDirectory() + chooser.getFileName();

    try (final OutputStreamWriter out =
        new OutputStreamWriter(new FileOutputStream(filename), "UTF-8")) {
      final StringBuilder sb = new StringBuilder();
      final String newLine = System.getProperty("line.separator");
      sb.append("# Image 1   = ").append(t1).append(newLine);
      sb.append("# Image 2   = ").append(t2).append(newLine);
      sb.append("# Distance  = ").append(MathUtils.rounded(distance, 2)).append(newLine);
      sb.append("# Unit      = ").append(unit).append(newLine);
      sb.append("# N 1       = ").append(result.getNumberActual()).append(newLine);
      sb.append("# N 2       = ").append(result.getNumberPredicted()).append(newLine);
      sb.append("# Match     = ").append(result.getTruePositives()).append(newLine);
      sb.append("# Unmatch 1 = ").append(result.getFalseNegatives()).append(newLine);
      sb.append("# Unmatch 2 = ").append(result.getFalsePositives()).append(newLine);
      sb.append("# Jaccard   = ").append(MathUtils.rounded(result.getJaccard(), 4)).append(newLine);
      sb.append("# Recall 1  = ").append(MathUtils.rounded(result.getRecall(), 4)).append(newLine);
      sb.append("# Recall 2  = ").append(MathUtils.rounded(result.getPrecision(), 4))
          .append(newLine);
      sb.append("# F-score   = ").append(MathUtils.rounded(result.getFScore(1.0), 4))
          .append(newLine);
      sb.append("# X1\tY1\tV1\tX2\tY2\tV2").append(newLine);

      final Rounder r = RounderUtils.create(4);

      out.write(sb.toString());

      for (final PointPair pair : matches) {
        final Coordinate c1 = pair.getPoint1();
        final Coordinate c2 = pair.getPoint2();
        float v1 = 0;
        float v2 = 0;
        if (pair.getPoint1() instanceof TimeValuedPoint) {
          final TimeValuedPoint p1 = (TimeValuedPoint) c1;
          final TimeValuedPoint p2 = (TimeValuedPoint) c2;
          v1 = p1.getValue(); // Actual
          v2 = p2.getValue(); // Predicted
        }
        out.write(String.format("%s\t%s\t%s\t%s\t%s\t%s%s", r.round(c1.getX()), r.round(c1.getY()),
            r.round(v1), r.round(c2.getX()), r.round(c2.getY()), r.round(v2), newLine));
      }
      // Actual
      for (final Coordinate c : falseNegatives) {
        float v1 = 0;
        if (c instanceof TimeValuedPoint) {
          final TimeValuedPoint p = (TimeValuedPoint) c;
          v1 = p.getValue();
        }
        out.write(String.format("%s\t%s\t%.0f\t0\t0\t0%s", r.round(c.getX()), r.round(c.getY()),
            r.round(v1), newLine));
      }
      // Predicted
      for (final Coordinate c : falsePositives) {
        float v1 = 0;
        if (c instanceof TimeValuedPoint) {
          final TimeValuedPoint p = (TimeValuedPoint) c;
          v1 = p.getValue();
        }
        out.write(String.format("0\t0\t0\t%s\t%s\t%.0f%s", r.round(c.getX()), r.round(c.getY()),
            r.round(v1), newLine));
      }
    } catch (final Exception ex) {
      IJ.log("Unable to save the matches to file: " + ex.getMessage());
    }
  }

  /**
   * Build a table showing the matched pairs and unmatched points.
   *
   * @param matches the matches
   * @param falsePositives the false positives
   * @param falseNegatives the false negatives
   */
  private void showMatches(List<PointPair> matches, List<Coordinate> falsePositives,
      List<Coordinate> falseNegatives) {
    if (matches.isEmpty() && falsePositives.isEmpty() && falseNegatives.isEmpty()) {
      return;
    }

    // Show a result table
    String header = "Image 1\tId\tX\tY\tSlice\tImage 2\tId\tX\tY\tSlice\tDistance";
    if (findFociImageIndex > 0) {
      header += "\tImage " + findFociImageIndex + ": " + findFociResult[findFociResultChoiceIndex];
    }
    if (!java.awt.GraphicsEnvironment.isHeadless()) {
      if (matchedWindow == null || !matchedWindow.isShowing()) {
        matchedWindow = new TextWindow(TITLE + " Matched", header, "", 800, 300);
      } else {
        ImageJUtils.refreshHeadings(matchedWindow, header, true);
      }
    } else if (writeMatchedHeader) {
      writeMatchedHeader = false;
      IJ.log(header);
    }

    Collections.sort(matches, new Comparator<PointPair>() {
      @Override
      public int compare(PointPair o1, PointPair o2) {
        final TimeValuedPoint p1 = (TimeValuedPoint) o1.getPoint1();
        final TimeValuedPoint p2 = (TimeValuedPoint) o2.getPoint1();
        return (p1.getTime() < p2.getTime()) ? -1 : 1;
      }
    });

    for (final PointPair pair : matches) {
      int value = -1;
      if (findFociImageIndex > 0) {
        final TimeValuedPoint point =
            (TimeValuedPoint) ((findFociImageIndex == 1) ? pair.getPoint1() : pair.getPoint2());
        value = (int) point.value;
      }
      addMatchedPair(pair.getPoint1(), pair.getPoint2(), pair.getXyzDistance(), value);
    }

    final TimeValuedPoint[] actualPoints = extractValuedPoints(falseNegatives);
    final TimeValuedPoint[] predictedPoints = extractValuedPoints(falsePositives);

    Arrays.sort(actualPoints, new Comparator<TimeValuedPoint>() {
      @Override
      public int compare(TimeValuedPoint p1, TimeValuedPoint p2) {
        return (p1.getTime() < p2.getTime()) ? -1 : 1;
      }
    });
    Arrays.sort(predictedPoints, new Comparator<TimeValuedPoint>() {
      @Override
      public int compare(TimeValuedPoint p1, TimeValuedPoint p2) {
        return (p1.getTime() < p2.getTime()) ? -1 : 1;
      }
    });

    for (final TimeValuedPoint point : actualPoints) {
      final int value = (findFociImageIndex == 1) ? (int) point.value : -1;
      addMatchedPair(point, null, -1, value);
    }

    for (final TimeValuedPoint point : predictedPoints) {
      final int value = (findFociImageIndex == 2) ? (int) point.value : -1;
      addMatchedPair(null, point, -1, value);
    }
  }

  private void addMatchedPair(Coordinate point1, Coordinate point2, double xyzDistance, int value) {
    final StringBuilder sb = new StringBuilder();
    addPoint(sb, t1, point1);
    addPoint(sb, t2, point2);
    if ((xyzDistance > -1)) {
      sb.append(MathUtils.rounded(xyzDistance));
    } else {
      sb.append("-");
    }
    if ((value > -1)) {
      sb.append('\t').append(value);
    }
    if (!java.awt.GraphicsEnvironment.isHeadless()) {
      matchedWindow.append(sb.toString());
    } else {
      IJ.log(sb.toString());
    }
  }

  private void addPoint(StringBuilder sb, String title, Coordinate point) {
    if (point != null) {
      final TimeValuedPoint p = (TimeValuedPoint) point;
      final Rounder r = RounderUtils.create(4);
      //@formatter:off
      sb.append(title)
          .append('\t').append(p.getTime())
          .append('\t').append(r.round(p.getX()))
          .append('\t').append(r.round(p.getY()))
        //.append('\t').append(r.round(p.getZ()))
        .append('\t').append((int) Math.round(p.getZ() / scaleZ))
        .append('\t');
      //@formatter:on
    } else {
      sb.append("-\t-\t-\t-\t-\t");
    }
  }

  private void addIntensityFromFindFoci(List<PointPair> matches, List<Coordinate> falsePositives,
      List<Coordinate> falseNegatives) {
    if (findFociImageIndex == 0) {
      return;
    }
    final List<FindFociResult> resultsArray = FindFoci_PlugIn.getLastResults();

    // Check the arrays are the correct size
    if (resultsArray
        .size() != ((findFociImageIndex == 1) ? actualPoints.length : predictedPoints.length)) {
      findFociImageIndex = 0;
      return;
    }

    for (final PointPair pair : matches) {
      final TimeValuedPoint point =
          (TimeValuedPoint) ((findFociImageIndex == 1) ? pair.getPoint1() : pair.getPoint2());
      point.value = getValue(resultsArray.get(point.time - 1));
    }
    final TimeValuedPoint[] points =
        extractValuedPoints((findFociImageIndex == 1) ? falseNegatives : falsePositives);
    for (final TimeValuedPoint point : points) {
      point.value = getValue(resultsArray.get(point.time - 1));
    }
  }

  private static float getValue(FindFociResult result) {
    switch (findFociResultChoiceIndex) {
      //@formatter:off
      case 0: return  (float) result.totalIntensity;
      case 1: return  (float) result.intensityAboveSaddle;
      case 2: return  (float) result.totalIntensityAboveBackground;
      case 3: return  result.count;
      case 4: return  result.countAboveSaddle;
      case 5: return  result.maxValue;
      case 6: return  result.highestSaddleValue;
      default: return (float) result.totalIntensity;
      //@formatter:on
    }
  }
}