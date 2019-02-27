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
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.match.Coordinate;
import uk.ac.sussex.gdsc.core.match.MatchCalculator;
import uk.ac.sussex.gdsc.core.match.MatchResult;
import uk.ac.sussex.gdsc.core.match.PointPair;
import uk.ac.sussex.gdsc.core.utils.FileUtils;
import uk.ac.sussex.gdsc.core.utils.MathUtils;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

import java.awt.Color;
import java.awt.Point;
import java.awt.TextField;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Compares the coordinates in two files and computes the match statistics.
 *
 * <p>Can read QuickPALM xls files; STORMJ xls files; and a generic CSV file.
 *
 * <p>The generic CSV file has records of the following:<br> ID,T,X,Y,Z,Value<br> Z and Value can be
 * missing. The generic file can also be tab delimited.
 */
public class FileMatchCalculator_PlugIn implements PlugIn {

  private static final String TITLE = "Match Calculator";

  private static String title1 = "";
  private static String title2 = "";
  private static double dThreshold = 1;
  private static String mask = "";
  private static double beta = 4;
  private static boolean showPairs;
  private static boolean savePairs;
  private static boolean savePairsSingleFile;
  private static String filename = "";
  private static String filenameSingle = "";
  private static String image1 = "";
  private static String image2 = "";
  private static boolean showComposite;
  private static boolean ignoreFrames;
  private static boolean useSlicePosition;

  private String myMask;
  private String myImage1;
  private String myImage2;

  private static boolean writeHeader = true;
  private static TextWindow resultsWindow;
  private static TextWindow pairsWindow;

  private TextField text1;
  private TextField text2;

  // flag indicating the pairs have values that should be output
  private boolean valued;

  private static class IdTimeValuedPoint extends TimeValuedPoint {
    final int id;

    IdTimeValuedPoint(int id, TimeValuedPoint point) {
      super(point.getX(), point.getY(), point.getZ(), point.time, point.value);
      this.id = id;
    }

    @Override
    public boolean equals(Object object) {
      // Ignore the Id in the equals comparison
      return super.equals(object);
    }

    @Override
    public int hashCode() {
      // Ignore the Id in the hash code
      return super.hashCode();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    if (!showDialog()) {
      return;
    }

    TimeValuedPoint[] actualPoints = null;
    TimeValuedPoint[] predictedPoints = null;

    try {
      actualPoints = TimeValuePointManager.loadPoints(title1);
      predictedPoints = TimeValuePointManager.loadPoints(title2);
    } catch (final IOException ex) {
      IJ.error("Failed to load the points: " + ex.getMessage());
      return;
    }

    // Optionally filter points using a mask
    if (myMask != null) {
      final ImagePlus maskImp = WindowManager.getImage(myMask);
      if (maskImp != null) {
        actualPoints = filter(actualPoints, maskImp.getProcessor());
        predictedPoints = filter(predictedPoints, maskImp.getProcessor());
      }
    }

    compareCoordinates(actualPoints, predictedPoints, dThreshold);
  }

  private static TimeValuedPoint[] filter(TimeValuedPoint[] points, ImageProcessor processor) {
    int ok = 0;
    for (int i = 0; i < points.length; i++) {
      if (processor.get(points[i].getXint(), points[i].getYint()) > 0) {
        points[ok++] = points[i];
      }
    }
    return Arrays.copyOf(points, ok);
  }

  /**
   * Adds an ROI point overlay to the image using the specified colour.
   *
   * @param imp the image
   * @param list the list
   * @param color the color
   */
  public static void addOverlay(ImagePlus imp, List<? extends Coordinate> list, Color color) {
    if (list.isEmpty()) {
      return;
    }

    final Color strokeColor = color;
    final Color fillColor = color;

    final Overlay o = imp.getOverlay();
    final PointRoi roi = (PointRoi) AssignedPointUtils.createRoi(list);
    roi.setStrokeColor(strokeColor);
    roi.setFillColor(fillColor);
    roi.setShowLabels(false);

    if (o == null) {
      imp.setOverlay(roi, strokeColor, 2, fillColor);
    } else {
      o.add(roi);
      imp.setOverlay(o);
    }
  }

  private boolean showDialog() {
    final ArrayList<String> newImageList = buildMaskList();
    final boolean haveImages = !newImageList.isEmpty();

    final GenericDialog gd = new GenericDialog(TITLE);

    gd.addMessage("Compare the points in two files\nand compute the match statistics\n"
        + "(Double click input fields to use a file chooser)");
    gd.addStringField("Input_1", title1, 30);
    gd.addStringField("Input_2", title2, 30);
    if (haveImages) {
      final ArrayList<String> maskImageList = new ArrayList<>(newImageList.size() + 1);
      maskImageList.add("[None]");
      maskImageList.addAll(newImageList);
      gd.addChoice("mask", maskImageList.toArray(new String[0]), mask);
    }
    gd.addNumericField("Distance", dThreshold, 2);
    gd.addNumericField("Beta", beta, 2);
    gd.addCheckbox("Show_pairs", showPairs);
    gd.addCheckbox("Save_pairs", savePairs);
    gd.addMessage("Use this option to save the pairs to a single file,\nappending if it exists");
    gd.addCheckbox("Save_pairs_single_file", savePairsSingleFile);
    if (!newImageList.isEmpty()) {
      gd.addCheckbox("Show_composite_image", showComposite);
      gd.addCheckbox("Ignore_file_frames", ignoreFrames);
      final String[] items = newImageList.toArray(new String[newImageList.size()]);
      gd.addChoice("Image_1", items, image1);
      gd.addChoice("Image_2", items, image2);
      gd.addCheckbox("Use_slice_position", useSlicePosition);
    }

    // Dialog to allow double click to select files using a file chooser
    if (!java.awt.GraphicsEnvironment.isHeadless()) {
      text1 = (TextField) gd.getStringFields().get(0);
      text2 = (TextField) gd.getStringFields().get(1);
      final MouseAdapter ma = new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent event) {
          if (event.getClickCount() > 1) {
            // Double-click
            TextField text;
            String title;
            if (event.getSource() == text1) {
              text = text1;
              title = "Coordinate_file_1";
            } else {
              text = text2;
              title = "Coordinate_file_2";
            }
            final String[] path = decodePath(text.getText());
            final OpenDialog chooser = new OpenDialog(title, path[0], path[1]);
            if (chooser.getFileName() != null) {
              text.setText(chooser.getDirectory() + chooser.getFileName());
            }
          }
        }
      };
      text1.addMouseListener(ma);
      text2.addMouseListener(ma);
    }

    gd.showDialog();

    if (gd.wasCanceled()) {
      return false;
    }

    title1 = gd.getNextString();
    title2 = gd.getNextString();
    if (haveImages) {
      myMask = mask = gd.getNextChoice();
    }
    dThreshold = gd.getNextNumber();
    beta = gd.getNextNumber();
    showPairs = gd.getNextBoolean();
    savePairs = gd.getNextBoolean();
    savePairsSingleFile = gd.getNextBoolean();
    if (haveImages) {
      showComposite = gd.getNextBoolean();
      ignoreFrames = gd.getNextBoolean();
      myImage1 = image1 = gd.getNextChoice();
      myImage2 = image2 = gd.getNextChoice();
      useSlicePosition = gd.getNextBoolean();
    }

    return true;
  }

  private static ArrayList<String> buildMaskList() {
    final ArrayList<String> newImageList = new ArrayList<>();

    for (final int id : uk.ac.sussex.gdsc.core.ij.ImageJUtils.getIdList()) {
      final ImagePlus imp = WindowManager.getImage(id);
      // Ignore RGB images
      if (imp.getBitDepth() == 24) {
        continue;
      }
      newImageList.add(imp.getTitle());
    }

    return newImageList;
  }

  private void compareCoordinates(TimeValuedPoint[] actualPoints, TimeValuedPoint[] predictedPoints,
      double distanceThreshold) {
    int tp = 0;
    int fp = 0;
    int fn = 0;
    double rmsd = 0;

    final boolean is3D = is3D(actualPoints) && is3D(predictedPoints);
    final boolean computePairs = showPairs || savePairs || savePairsSingleFile
        || (showComposite && myImage1 != null && myImage2 != null);

    final List<PointPair> pairs = (computePairs) ? new LinkedList<>() : null;

    // Process each timepoint
    for (final Integer t : getTimepoints(actualPoints, predictedPoints)) {
      final Coordinate[] actual = getCoordinates(actualPoints, t);
      final Coordinate[] predicted = getCoordinates(predictedPoints, t);

      List<Coordinate> truePositives = null;
      List<Coordinate> falsePositives = null;
      List<Coordinate> falseNegatives = null;
      List<PointPair> matches = null;
      if (computePairs) {
        truePositives = new LinkedList<>();
        falsePositives = new LinkedList<>();
        falseNegatives = new LinkedList<>();
        matches = new LinkedList<>();
      }

      final MatchResult result = (is3D)
          ? MatchCalculator.analyseResults3D(actual, predicted, distanceThreshold, truePositives,
              falsePositives, falseNegatives, matches)
          : MatchCalculator.analyseResults2D(actual, predicted, distanceThreshold, truePositives,
              falsePositives, falseNegatives, matches);

      // Aggregate
      tp += result.getTruePositives();
      fp += result.getFalsePositives();
      fn += result.getFalseNegatives();
      rmsd += (result.getRmsd() * result.getRmsd()) * result.getTruePositives();

      if (computePairs) {
        pairs.addAll(matches);
        for (final Coordinate c : falseNegatives) {
          pairs.add(new PointPair(c, null));
        }
        for (final Coordinate c : falsePositives) {
          pairs.add(new PointPair(null, c));
        }
      }
    }

    if (showPairs || savePairs || savePairsSingleFile) {
      // Check if these are valued points
      valued = isValued(actualPoints) && isValued(predictedPoints);
    }

    if (!java.awt.GraphicsEnvironment.isHeadless()) {
      if (resultsWindow == null || !resultsWindow.isShowing()) {
        resultsWindow = new TextWindow(TITLE + " Results", createResultsHeader(), "", 900, 300);
      }
      if (showPairs) {
        final String header = createPairsHeader(title1, title2);
        if (pairsWindow == null || !pairsWindow.isShowing()) {
          pairsWindow = new TextWindow(TITLE + " Pairs", header, "", 900, 300);
          final Point p = resultsWindow.getLocation();
          p.y += resultsWindow.getHeight();
          pairsWindow.setLocation(p);
        }
        for (final PointPair pair : pairs) {
          addPairResult(pair, is3D);
        }
      }
    } else if (writeHeader) {
      writeHeader = false;
      IJ.log(createResultsHeader());
    }
    if (tp > 0) {
      rmsd = Math.sqrt(rmsd / tp);
    }
    final MatchResult result = new MatchResult(tp, fp, fn, rmsd);
    addResult(title1, title2, distanceThreshold, result);

    if (savePairs || savePairsSingleFile) {
      savePairs(pairs, is3D);
    }

    if (java.awt.GraphicsEnvironment.isHeadless()) {
      return;
    }

    // If input images and a mask have been selected then we can produce an output
    // that draws the points on a composite image.
    produceComposite(pairs);
  }

  /**
   * Checks if there is more than one z-value in the coordinates.
   *
   * @param points the points
   * @return true, if is 3d
   */
  private static boolean is3D(TimeValuedPoint[] points) {
    if (points.length == 0) {
      return false;
    }
    final float z = points[0].getZ();
    for (final TimeValuedPoint p : points) {
      if (p.getZ() != z) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if there is a non-zero value within the points.
   *
   * @param points the points
   * @return true, if is valued
   */
  private static boolean isValued(TimeValuedPoint[] points) {
    if (points.length == 0) {
      return false;
    }
    for (final TimeValuedPoint p : points) {
      if (p.getValue() != 0) {
        return true;
      }
    }
    return false;
  }

  private static int[] getTimepoints(TimeValuedPoint[] points, TimeValuedPoint[] points2) {
    final TIntHashSet set = new TIntHashSet();
    for (final TimeValuedPoint p : points) {
      set.add(p.getTime());
    }
    for (final TimeValuedPoint p : points2) {
      set.add(p.getTime());
    }
    final int[] data = set.toArray();
    if (showPairs) {
      // Sort so the table order is nice
      Arrays.sort(data);
    }
    return data;
  }

  private static Coordinate[] getCoordinates(TimeValuedPoint[] points, int time) {
    final LinkedList<Coordinate> coords = new LinkedList<>();
    int id = 1;
    for (final TimeValuedPoint p : points) {
      if (p.getTime() == time) {
        coords.add(new IdTimeValuedPoint(id++, p));
      }
    }
    return coords.toArray(new Coordinate[coords.size()]);
  }

  private static String createResultsHeader() {
    final StringBuilder sb = new StringBuilder();
    sb.append("Image 1\t");
    sb.append("Image 2\t");
    sb.append("Distance (px)\t");
    sb.append("N\t");
    sb.append("TP\t");
    sb.append("FP\t");
    sb.append("FN\t");
    sb.append("Jaccard\t");
    sb.append("RMSD\t");
    sb.append("Precision\t");
    sb.append("Recall\t");
    sb.append("F0.5\t");
    sb.append("F1\t");
    sb.append("F2\t");
    sb.append("F-beta");
    return sb.toString();
  }

  private static void addResult(String i1, String i2, double distanceThrehsold,
      MatchResult result) {
    final StringBuilder sb = new StringBuilder();
    sb.append(i1).append('\t');
    sb.append(i2).append('\t');
    sb.append(IJ.d2s(distanceThrehsold, 2)).append('\t');
    sb.append(result.getNumberPredicted()).append('\t');
    sb.append(result.getTruePositives()).append('\t');
    sb.append(result.getFalsePositives()).append('\t');
    sb.append(result.getFalseNegatives()).append('\t');
    sb.append(IJ.d2s(result.getJaccard(), 4)).append('\t');
    sb.append(IJ.d2s(result.getRmsd(), 4)).append('\t');
    sb.append(IJ.d2s(result.getPrecision(), 4)).append('\t');
    sb.append(IJ.d2s(result.getRecall(), 4)).append('\t');
    sb.append(IJ.d2s(result.getFScore(0.5), 4)).append('\t');
    sb.append(IJ.d2s(result.getFScore(1.0), 4)).append('\t');
    sb.append(IJ.d2s(result.getFScore(2.0), 4)).append('\t');
    sb.append(IJ.d2s(result.getFScore(beta), 4));

    if (java.awt.GraphicsEnvironment.isHeadless()) {
      IJ.log(sb.toString());
    } else {
      resultsWindow.append(sb.toString());
    }
  }

  private String pairsPrefix;

  private String createPairsHeader(String i1, String i2) {
    final StringBuilder sb = new StringBuilder();
    sb.append(i1).append('\t');
    sb.append(i2).append('\t');
    pairsPrefix = sb.toString();

    sb.setLength(0);
    sb.append("Image 1\t");
    sb.append("Image 2\t");
    sb.append("T\t");
    sb.append("ID1\t");
    sb.append("X1\t");
    sb.append("Y1\t");
    sb.append("Z1\t");
    if (valued) {
      sb.append("Value\t");
    }
    sb.append("ID2\t");
    sb.append("X2\t");
    sb.append("Y2\t");
    sb.append("Z2\t");
    if (valued) {
      sb.append("Value\t");
    }
    sb.append("Distance\t");
    sb.append("Outcome");
    return sb.toString();
  }

  private void addPairResult(PointPair pair, boolean is3D) {
    pairsWindow.append(createPairResult(pair, is3D));
  }

  private String createPairResult(PointPair pair, boolean is3D) {
    final StringBuilder sb = new StringBuilder(pairsPrefix);
    final IdTimeValuedPoint p1 = (IdTimeValuedPoint) pair.getPoint1();
    final IdTimeValuedPoint p2 = (IdTimeValuedPoint) pair.getPoint2();
    final int t = (p1 != null) ? p1.getTime() : p2.getTime();
    sb.append(t).append('\t');
    addPoint(sb, p1);
    addPoint(sb, p2);
    final double distance = (is3D) ? pair.getXyzDistance() : pair.getXyDistance();
    if (distance >= 0) {
      sb.append(distance).append('\t');
    } else {
      sb.append('\t');
    }
    // Added for colocalisation analysis:
    final char outcome = getOutcome(p1, p2);
    sb.append(outcome);
    return sb.toString();
  }

  private static char getOutcome(IdTimeValuedPoint p1, IdTimeValuedPoint p2) {
    // C = Colocalised (i.e. a match)
    // F = First dataset has foci
    // S = Second dataset has foci
    if (p1 != null) {
      return (p2 != null) ? 'C' : 'F';
    }
    // Assume the second point is not null
    return 'S';
  }

  private void addPoint(StringBuilder sb, IdTimeValuedPoint point) {
    if (point == null) {
      if (valued) {
        sb.append("\t\t\t\t\t");
      } else {
        sb.append("\t\t\t\t");
      }
    } else {
      sb.append(point.id).append('\t');
      sb.append(point.getXint()).append('\t');
      sb.append(point.getYint()).append('\t');
      sb.append(point.getZint()).append('\t');
      if (valued) {
        sb.append(point.getValue()).append('\t');
      }
    }
  }

  @SuppressWarnings("resource")
  private void savePairs(List<PointPair> pairs, boolean is3D) {
    boolean fileSelected = false;
    if (savePairs) {
      filename = getFilename("Pairs_filename", filename);
      fileSelected = filename != null;
    }
    boolean fileSingleSelected = false;
    if (savePairsSingleFile) {
      filenameSingle = getFilename("Pairs_filename_single", filenameSingle);
      fileSingleSelected = filenameSingle != null;
    }
    if (!(fileSelected || fileSingleSelected)) {
      return;
    }

    BufferedWriter out = null;
    BufferedWriter outSingle = null;
    try {
      // Always create the header as it sets up the pairs prefix for each pair result
      final String header = createPairsHeader(title1, title2);
      if (fileSelected) {
        out = Files.newBufferedWriter(Paths.get(filename));
        out.write(header);
        out.newLine();
      }
      if (fileSingleSelected) {
        final File file = new File(filenameSingle);
        if (file.length() != 0) {
          outSingle = Files.newBufferedWriter(file.toPath(), StandardOpenOption.APPEND);
        } else {
          outSingle = Files.newBufferedWriter(file.toPath());
          outSingle.write(header);
          outSingle.newLine();
        }
      }

      for (final PointPair pair : pairs) {
        final String result = createPairResult(pair, is3D);
        if (out != null) {
          out.write(result);
          out.newLine();
        }
        if (outSingle != null) {
          outSingle.write(result);
          outSingle.newLine();
        }
      }
    } catch (IOException ex) {
      IJ.log("Unable to save the matches to file: " + ex.getMessage());
    } finally {
      if (out != null) {
        try {
          out.close();
        } catch (final IOException ex) {
          // Ignore
        }
      }
      if (outSingle != null) {
        try {
          outSingle.close();
        } catch (final IOException ex) {
          // Ignore
        }
      }
    }
  }

  private static String getFilename(String title, String filename) {
    final String[] path = ImageJUtils.decodePath(filename);
    final OpenDialog chooser = new OpenDialog(title, path[0], path[1]);
    if (chooser.getFileName() == null) {
      return null;
    }
    return FileUtils.replaceExtension(chooser.getDirectory() + chooser.getFileName(), ".xls");
  }

  private static String[] decodePath(String path) {
    final String[] result = new String[2];
    int index = path.lastIndexOf('/');
    if (index == -1) {
      index = path.lastIndexOf('\\');
    }
    if (index > 0) {
      result[0] = path.substring(0, index + 1);
      result[1] = path.substring(index + 1);
    } else {
      result[0] = OpenDialog.getDefaultDirectory();
      result[1] = path;
    }
    return result;
  }

  private void produceComposite(List<PointPair> pairs) {
    if (!showComposite) {
      return;
    }
    if (myImage1 == null || myImage2 == null) {
      IJ.error(TITLE, "No images specified for composite");
      return;
    }
    final ImagePlus imp1 = WindowManager.getImage(myImage1);
    ImagePlus imp2 = WindowManager.getImage(myImage2);
    final ImagePlus impM = WindowManager.getImage(myMask);
    // Images must be the same XYZ dimensions
    final int w = imp1.getWidth();
    final int h = imp1.getHeight();
    final int nSlices = imp1.getNSlices();
    if (w != imp2.getWidth() || h != imp2.getHeight() || nSlices != imp2.getNSlices()) {
      IJ.error(TITLE, "Composite images must have the same XYZ dimensions");
      return;
    }
    if (impM != null && (w != impM.getWidth() || h != impM.getHeight())) {
      IJ.error(TITLE, "Composite image mask must have the same XY dimensions");
      return;
    }

    final boolean addMask = impM != null;

    final ImageStack stack = new ImageStack(w, h);
    final ImageStack s1 = imp1.getImageStack();
    final ImageStack s2 = imp2.getImageStack();
    final ImageStack sm = (addMask) ? impM.getImageStack() : null;

    // Get the bit-depth for the output stack
    int depth = imp1.getBitDepth();
    depth = Math.max(depth, imp2.getBitDepth());
    if (addMask) {
      depth = Math.max(depth, impM.getBitDepth());
    }

    final int channels = (addMask) ? 3 : 2;
    int frames = 0;

    // Produce a composite for each time point.
    // The input list will have pairs in order of time.
    // So move through the list noting each new time.
    final TIntArrayList upper = new TIntArrayList();

    int time = -1;
    final IdTimeValuedPoint[] p1 = new IdTimeValuedPoint[pairs.size()];
    final IdTimeValuedPoint[] p2 = new IdTimeValuedPoint[pairs.size()];
    for (int i = 0; i < pairs.size(); i++) {
      p1[i] = (IdTimeValuedPoint) pairs.get(i).getPoint1();
      p2[i] = (IdTimeValuedPoint) pairs.get(i).getPoint2();
      final int newTime = (p1[i] != null) ? p1[i].time : p2[i].time;
      if (time != newTime) {
        if (time != -1) {
          upper.add(i);
        }
        time = newTime;
      }
    }
    upper.add(pairs.size());

    // Check if the input image has only one time frame but the points have multiple time points
    boolean singleFrame = false;
    if (upper.size() > 1 && imp1.getNFrames() == 1 && imp2.getNFrames() == 1 && ignoreFrames) {
      singleFrame = true;
      upper.clear();
      upper.add(pairs.size());
    }

    // Create an overlay to show the pairs
    final Overlay overlay = new Overlay();
    final Color colorf = Match_PlugIn.UNMATCH1;
    final Color colors = Match_PlugIn.UNMATCH2;
    final Color colorc = Match_PlugIn.MATCH;

    int low = 0;
    for (final int high : upper.toArray()) {
      frames++;
      time = (p1[low] != null) ? p1[low].time : p2[low].time;
      if (singleFrame) {
        time = 1;
      }

      // Extract the images for the specified time
      for (int slice = 1; slice <= nSlices; slice++) {
        stack.addSlice("Image1, t=" + time,
            convert(s1.getProcessor(imp1.getStackIndex(imp1.getC(), slice, time)), depth));
        stack.addSlice("Image2, t=" + time,
            convert(s2.getProcessor(imp2.getStackIndex(imp2.getC(), slice, time)), depth));
        if (addMask) {
          stack.addSlice("Mask, t=" + time,
              convert(sm.getProcessor(impM.getStackIndex(impM.getC(), slice, time)), depth));
        }
      }

      // Count number of First, Second, Colocalised.
      int countF = 0;
      int countS = 0;
      int countC = 0;
      final float[] fx = new float[high - low];
      final float[] fy = new float[fx.length];
      final float[] sx = new float[fx.length];
      final float[] sy = new float[fx.length];
      final float[] cx = new float[fx.length];
      final float[] cy = new float[fx.length];
      final int[] fz = new int[fx.length];
      final int[] sz = new int[fx.length];
      final int[] cz = new int[fx.length];
      for (int j = low; j < high; j++) {
        if (p1[j] == null) {
          sx[countS] = p2[j].getX();
          sy[countS] = p2[j].getY();
          sz[countS] = p2[j].getZint();
          countS++;
        } else if (p2[j] == null) {
          fx[countF] = p1[j].getX();
          fy[countF] = p1[j].getY();
          fz[countF] = p1[j].getZint();
          countF++;
        } else {
          cx[countC] = (p1[j].getX() + p2[j].getX()) * 0.5f;
          cy[countC] = (p1[j].getY() + p2[j].getY()) * 0.5f;
          cz[countC] = MathUtils.averageIndex(p1[j].getZint(), p2[j].getZint());
          countC++;
        }
      }

      add(overlay, fx, fy, fz, countF, colorf, frames);
      add(overlay, sx, sy, sz, countS, colors, frames);
      add(overlay, cx, cy, cz, countC, colorc, frames);

      low = high;
    }

    final String title = "Match Composite";
    final ImagePlus imp = new ImagePlus(title, stack);
    imp.setDimensions(channels, nSlices, frames);
    imp.setOverlay(overlay);
    imp.setOpenAsHyperStack(true);

    imp2 = WindowManager.getImage(title);
    if (imp2 != null) {
      if (imp2.isDisplayedHyperStack()) {
        imp2.setImage(imp);
        imp2.setOverlay(overlay);
        imp2.getWindow().toFront();
        return;
      }
      // Figure out how to convert back to a hyperstack if it is not already.
      // Currently we just close the image and show a new one.
      imp2.close();
    }

    imp.show();
  }

  private static ImageProcessor convert(ImageProcessor processor, int depth) {
    if (depth == 8) {
      return processor.convertToByte(true);
    }
    if (depth == 16) {
      return processor.convertToShort(true);
    }
    return processor.convertToFloat();
  }

  private static void add(Overlay overlay, float[] x, float[] y, int[] z, int size, Color color,
      int frame) {
    if (size != 0) {
      // Option to position the ROI points on the z-slice.
      // This requires an ROI for each slice that contains a point.
      if (useSlicePosition) {
        // Sort points by slice position
        final float[][] data = new float[size][3];
        for (int i = size; i-- > 0;) {
          data[i][0] = z[i];
          data[i][1] = x[i];
          data[i][2] = y[i];
        }

        Arrays.sort(data, (o1, o2) -> {
          // smallest first
          if (o1[0] < o2[0]) {
            return -1;
          }
          if (o1[0] > o2[0]) {
            return 1;
          }
          return 0;
        });

        // Find blocks in the same slice
        int low = 0;
        final TIntArrayList upper = new TIntArrayList(size);
        for (int i = 0; i < size; i++) {
          if (data[low][0] != data[i][0]) {
            upper.add(i);
            low = i;
          }
        }
        upper.add(size);

        // Process each block
        low = 0;
        for (final int high : upper.toArray()) {
          final int npoints = high - low;
          for (int i = low, j = 0; i < high; i++, j++) {
            x[j] = data[i][1];
            y[j] = data[i][2];
          }
          add(overlay, new PointRoi(x, y, npoints), (int) data[low][0], frame, color);
          low = high;
        }
      } else {
        add(overlay, new PointRoi(x, y, size), 0, frame, color);
      }
    }
  }

  private static void add(Overlay overlay, PointRoi roi, int slice, int frame, Color color) {
    // Tie position to the frame but not the channel or slice
    roi.setPosition(0, slice, frame);
    roi.setStrokeColor(color);
    roi.setFillColor(color);
    roi.setShowLabels(false);
    overlay.add(roi);
  }
}
