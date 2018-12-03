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
package uk.ac.sussex.gdsc.threshold;

import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.gui.YesNoCancelDialog;
import ij.plugin.PlugIn;
import ij.plugin.filter.EDM;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.text.TextWindow;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;

/**
 * Analyses an image using a given mask. <p> Skeletonises the mask image and extracts a set of lines
 * connecting node points on the skeleton. Output statistics about each of the lines using the
 * original image and the Euclidian Distance Map (EDM) of the mask.
 */
public class ThreadAnalyser implements PlugIn {
  private static String TITLE = "Thread Analyser";
  private static TextWindow resultsWindow = null;
  private static boolean writeHeader = true;

  private static String[] ignoreSuffix =
      new String[] {"EDM", "SkeletonNodeMap", "SkeletonMap", "Threads", "Objects"};

  private static String image = "";
  private static int imageChannel = 0;
  private static String maskImage = "";
  private static int maskChannel = 0;
  private static String objectImage = "";
  private static int objectChannel = 0;
  private static String method = AutoThreshold.Method.OTSU.toString();
  private static String resultDirectory = "";
  private static int minLength = 0;
  private static boolean showSkeleton = false;
  private static boolean showSkeletonMap = true;
  private static boolean showSkeletonImage = false;
  private static boolean showSkeletonEDM = false;
  private static boolean showObjectImage = false;
  private static boolean labelThreads = false;

  /** {@inheritDoc} */
  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    if (!showDialog()) {
      return;
    }

    final ImageProcessor ip = getImage(image, imageChannel);
    final ByteProcessor maskIp = getMask(getImage(maskImage, maskChannel));
    final ByteProcessor objectIp = getMask(getImage(objectImage, objectChannel));

    if (ip.getWidth() != maskIp.getWidth() || ip.getHeight() != maskIp.getHeight()) {
      IJ.error(TITLE, "Image and mask must have the same X,Y dimensions");
      return;
    }
    if (objectIp != null) {
      if (ip.getWidth() != objectIp.getWidth() || ip.getHeight() != objectIp.getHeight()) {
        IJ.error(TITLE, "Image and object image must have the same X,Y dimensions");
        return;
      }
    }

    // Create EDM
    final FloatProcessor floatEdm = createEDM(maskIp);

    // Create Skeleton
    final SkeletonAnalyser sa = new SkeletonAnalyser();

    sa.skeletonise(maskIp, true);

    final byte[] map = sa.findNodes(maskIp);

    // Need to create this before the map pixels are set to processed
    final ColorProcessor cp = sa.createMapImage(map, maskIp.getWidth(), maskIp.getHeight());

    // Get the lines
    final LinkedList<ChainCode> chainCodes = new LinkedList<>();
    sa.extractLines(map, chainCodes);

    lengthFilter(chainCodes);

    // Report statistics
    final ImageProcessor skeletonMap = createSkeletonMap(maskIp, chainCodes);
    reportResults(ip, floatEdm, chainCodes, skeletonMap, objectIp);

    if (minLength > 0) {
      // Skeleton Map is filtered for length. Use this to remove pixels from the other images
      for (int i = map.length; i-- > 0;) {
        if (skeletonMap.get(i) == 0) {
          cp.set(i, 0);
          map[i] = 0;
        }
      }
    }

    Roi roi = null;
    if (labelThreads) {
      // Build a multi-point ROI using the centre of each thread
      roi = createLabels(chainCodes);
    }
    if (showSkeletonEDM) {
      showImage(floatEdm, maskImage + " EDM", roi);
    }
    if (showSkeleton) {
      showImage(cp, maskImage + " SkeletonNodeMap", roi);
    }
    if (showSkeletonMap) {
      skeletonMap.setMinAndMax(0, chainCodes.size());
      showImage(skeletonMap, maskImage + " SkeletonMap", roi);
    }
    if (showSkeletonImage) {
      // Put skeleton back on original image
      final ImageProcessor threadImage = createThreadImage(ip, map);
      showImage(threadImage, image + " Threads", roi);
    }
    if (showObjectImage) {
      showImage(objectIp, objectImage + " Objects", null);
    }
  }

  private static ByteProcessor getMask(ImageProcessor ip) {
    if (ip == null) {
      return null;
    }

    // Check if already a mask. Find first non-zero value and check all other pixels are the same
    // value
    int value = 0;
    int i = 0;
    for (; i < ip.getPixelCount(); i++) {
      if (ip.get(i) != 0) {
        value = ip.get(i);
        break;
      }
    }
    boolean isMask = true;
    for (; i < ip.getPixelCount(); i++) {
      if (ip.get(i) != 0 && value != ip.get(i)) {
        isMask = false;
        break;
      }
    }

    ip = ip.duplicate();
    if (!isMask) {
      final int[] data = ip.getHistogram();
      final int level = AutoThreshold.getThreshold(method, data);
      ip.threshold(level);
      if (!Prefs.blackBackground) {
        ip.invert();
      }
    }

    return (ByteProcessor) ip.convertToByte(false);
  }

  private static ImageProcessor getImage(String title, int channel) {
    final ImagePlus imp = WindowManager.getImage(title);
    if (imp == null) {
      return null;
    }
    if (imp.getNChannels() == 1) {
      return imp.getProcessor();
    }

    // Channels should be one-based index
    final int index = imp.getStackIndex(channel + 1, 1, 1);
    final ImageStack stack = imp.getImageStack();
    return stack.getProcessor(index);
  }

  /**
   * Show an ImageJ Dialog and get the parameters.
   *
   * @return False if the user cancelled
   */
  private static boolean showDialog() {
    GenericDialog gd = new GenericDialog(TITLE);

    // Add a second mask image to threshold (if necessary) for objects to find on the threads

    final String[] imageList = ImageJUtils.getImageList(ImageJUtils.GREY_8_16, ignoreSuffix);
    final String[] objectList =
        ImageJUtils.getImageList(ImageJUtils.GREY_8_16 | ImageJUtils.NO_IMAGE, ignoreSuffix);

    if (imageList.length == 0) {
      IJ.error(TITLE, "Require an 8 or 16 bit image");
      return false;
    }

    gd.addChoice("Image", imageList, image);
    gd.addChoice("Mask", imageList, maskImage);
    gd.addChoice("Objects", objectList, objectImage);
    gd.addChoice("Threshold_method", AutoThreshold.getMethods(true), method);
    gd.addStringField("Result_directory", resultDirectory, 30);
    gd.addNumericField("Min_length", minLength, 0);
    gd.addCheckbox("Show_skeleton", showSkeleton);
    gd.addCheckbox("Show_skeleton_map", showSkeletonMap);
    gd.addCheckbox("Show_skeleton_image", showSkeletonImage);
    gd.addCheckbox("Show_skeleton_EDM", showSkeletonEDM);
    gd.addCheckbox("Show_object_image", showObjectImage);
    gd.addCheckbox("Label_threads", labelThreads);
    gd.addHelp(uk.ac.sussex.gdsc.help.URL.UTILITY);

    gd.showDialog();

    if (gd.wasCanceled()) {
      return false;
    }

    image = gd.getNextChoice();
    maskImage = gd.getNextChoice();
    objectImage = gd.getNextChoice();
    resultDirectory = gd.getNextString();
    minLength = (int) gd.getNextNumber();
    showSkeleton = gd.getNextBoolean();
    showSkeletonMap = gd.getNextBoolean();
    showSkeletonImage = gd.getNextBoolean();
    showSkeletonEDM = gd.getNextBoolean();
    showObjectImage = gd.getNextBoolean();
    labelThreads = gd.getNextBoolean();

    // Check if the images have multiple channels. If so ask the user which channel to use.
    final int imageChannels = getChannels(image);
    final int maskChannels = getChannels(maskImage);
    final int objectChannels = getChannels(objectImage);

    if (imageChannels + maskChannels + objectChannels > 3) {
      gd = new GenericDialog(TITLE + " Channels Selection");
      gd.addMessage("Multi-channel images. Please select the channels");
      addChoice(gd, "Image_channel", imageChannels, imageChannel);
      addChoice(gd, "Mask_channel", maskChannels, maskChannel);
      addChoice(gd, "Object_channel", objectChannels, objectChannel);

      gd.showDialog();

      if (gd.wasCanceled()) {
        return false;
      }

      if (imageChannels > 1) {
        imageChannel = gd.getNextChoiceIndex();
      }
      if (maskChannels > 1) {
        maskChannel = gd.getNextChoiceIndex();
      }
      if (objectChannels > 1) {
        objectChannel = gd.getNextChoiceIndex();
      }
    }

    return true;
  }

  private static int getChannels(String title) {
    final ImagePlus imp = WindowManager.getImage(title);
    if (imp != null) {
      return imp.getNChannels();
    }
    return 1;
  }

  private static void addChoice(GenericDialog gd, String label, int nChannels, int index) {
    if (nChannels == 1) {
      return;
    }
    final String[] items = new String[nChannels];
    for (int c = 0; c < nChannels; c++) {
      items[c] = Integer.toString(c + 1);
    }
    if (index >= nChannels) {
      index = 0;
    }
    gd.addChoice(label, items, items[index]);
  }

  /**
   * Create a Euclidian Distance Map (EDM) of the mask.
   *
   * @param bp the mask
   * @return the EDM float processor
   */
  private static FloatProcessor createEDM(ByteProcessor bp) {
    final EDM edm = new EDM();
    byte backgroundValue = (byte) (Prefs.blackBackground ? 0 : 255);
    if (bp.isInvertedLut()) {
      backgroundValue = (byte) (255 - backgroundValue);
    }
    final FloatProcessor floatEdm = edm.makeFloatEDM(bp, backgroundValue, false);
    return floatEdm;
  }

  /**
   * Remove all chain codes below a certain length.
   *
   * @param chainCodes the chain codes
   */
  private static void lengthFilter(LinkedList<ChainCode> chainCodes) {
    Collections.sort(chainCodes);

    while (!chainCodes.isEmpty()) {
      if (chainCodes.getFirst().getDistance() < minLength) {
        chainCodes.removeFirst();
      } else {
        break;
      }
    }
  }

  /**
   * If showSkeletonMap is true return an image processor that can store the total count of chain
   * codes.
   *
   * @param bp the mask (used for dimensions)
   * @param chainCodes the chain codes
   * @return the image processor
   */
  private static ImageProcessor createSkeletonMap(ByteProcessor bp,
      LinkedList<ChainCode> chainCodes) {
    // Need a map if displaying it or using to filter for length
    if (showSkeletonMap || minLength > 0) {
      final int size = chainCodes.size();
      final ImageProcessor ip = (size > 255) ? new ShortProcessor(bp.getWidth(), bp.getHeight())
          : new ByteProcessor(bp.getWidth(), bp.getHeight());
      return ip;
    }
    return null;
  }

  /**
   * Sets all points in the original image outside the skeleton to zero.
   *
   * @param ip the image
   * @param map the map
   * @return the image processor
   */
  private static ImageProcessor createThreadImage(ImageProcessor ip, byte[] map) {
    ip = ip.duplicate();
    for (int i = map.length; i-- > 0;) {
      if (map[i] == 0) {
        ip.set(i, 0);
      }
    }
    return ip;
  }

  private static Roi createLabels(LinkedList<ChainCode> chainCodes) {
    int nPoints = 0;
    final int[] xPoints = new int[chainCodes.size()];
    final int[] yPoints = new int[xPoints.length];
    for (final ChainCode code : chainCodes) {
      int x = code.getX();
      int y = code.getY();
      final int[] run = code.getRun();
      for (int i = 0; i < run.length / 2; i++) {
        x += ChainCode.DIR_X_OFFSET[run[i]];
        y += ChainCode.DIR_Y_OFFSET[run[i]];
      }
      xPoints[nPoints] = x;
      yPoints[nPoints] = y;
      nPoints++;
    }
    return new PointRoi(xPoints, yPoints, nPoints);
  }

  private static void showImage(ImageProcessor ip, String title, Roi roi) {
    ImagePlus imp = WindowManager.getImage(title);
    if (imp == null) {
      imp = new ImagePlus(title, ip);
    } else {
      imp.setProcessor(ip);
      imp.updateAndDraw();
    }
    imp.setRoi(roi);
    imp.show();
  }

  /**
   * Return the statistics.
   *
   * @param data The input data
   * @return Array containing: min, max, av, stdDev
   */
  private static double[] getStatistics(float[] data) {
    // Get the limits
    float min = Float.POSITIVE_INFINITY;
    float max = Float.NEGATIVE_INFINITY;

    // Get the average
    double sum = 0.0;
    double sum2 = 0.0;
    final long n = data.length;
    for (final float value : data) {
      if (min > value) {
        min = value;
      }
      if (max < value) {
        max = value;
      }

      sum += value;
      sum2 += (value * value);
    }

    double av;
    double stdDev;
    if (n > 0) {
      av = sum / n;
      final double d = n;
      stdDev = (d * sum2 - sum * sum) / d;
      if (stdDev > 0.0) {
        stdDev = Math.sqrt(stdDev / (d - 1.0));
      } else {
        stdDev = 0.0;
      }
    } else {
      av = 0;
      stdDev = 0.0;
    }

    return new double[] {min, max, av, stdDev};
  }

  /**
   * Report the results to a table and saving to file.
   *
   * @param ip The original image
   * @param floatEdm The EDM
   * @param chainCodes The chain codes
   * @param skeletonMap If not null, fill each x,y coord with the chain code ID
   * @param objectIp Mask containing objects that overlap the skeleton threads
   */
  @SuppressWarnings("resource")
  private static void reportResults(ImageProcessor ip, FloatProcessor floatEdm,
      LinkedList<ChainCode> chainCodes, ImageProcessor skeletonMap, ByteProcessor objectIp) {
    if (chainCodes.size() > 1000) {
      final YesNoCancelDialog d = new YesNoCancelDialog(IJ.getInstance(), TITLE,
          "Do you want to show all " + chainCodes.size() + " results?");
      d.setVisible(true);
      if (!d.yesPressed()) {
        return;
      }
    }

    final FloatProcessor floatImage = ip.toFloat(1, null);
    final FloatProcessor floatObjectImage = (objectIp == null) ? null : objectIp.toFloat(1, null);
    float[] objectMaximaDistances = null;

    Collections.sort(chainCodes);

    OutputStreamWriter out = createResultsFile();

    createResultsWindow();
    int id = 1;
    final int[] maxima = new int[2];
    final int[] objectMaxima = new int[2];
    for (final ChainCode code : chainCodes) {
      // Extract line coordinates
      final int[] x = new int[code.getSize()];
      final int[] y = new int[code.getSize()];
      final float[] d = new float[code.getSize()];
      getPoints(code, x, y, d);

      final float[] line =
          new float[] {x[0], y[0], x[x.length - 1], y[x.length - 1], code.getDistance()};

      out = saveResult(out, id, line);
      out = saveResult(out, "Distance", d);

      if (skeletonMap != null) {
        for (int i = x.length; i-- > 0;) {
          skeletonMap.set(x[i], y[i], id);
        }
      }

      // calculate average/sd height for each line from original image
      final float[] data = new float[x.length];
      final double[] imageStats = extractStatistics(x, y, floatImage, data);
      out = saveResult(out, "Image Intensity", data);

      // Count maxima along the line
      // TODO - Add smoothing to the data?
      // Note that the spacing between points is not equal.
      // Use a weighted sum for each point using the distance to neighbour
      // points within a distance window.
      final float[] imageMaximaDistances = countMaxima(d, data, maxima);

      // calculate average/sd height for each line from EDM
      final double[] edmStats = extractStatistics(x, y, floatEdm, data);
      out = saveResult(out, "EDM Intensity", data);

      if (floatObjectImage != null) {
        // If using a 2nd mask image then count the number of foreground objects
        // on the thread.
        extractStatistics(x, y, floatObjectImage, data);
        convertObjects(data);
        out = saveResult(out, "Objects", data);
        objectMaximaDistances = countMaxima(d, data, objectMaxima);
      }

      out = saveResult(out, "Image Maxima", imageMaximaDistances);
      if (floatObjectImage != null) {
        out = saveResult(out, "Object Maxima", objectMaximaDistances);
      }

      addResult(id, line, maxima, objectMaxima, imageStats, edmStats);

      id++;
    }

    if (out != null) {
      try {
        out.close();
      } catch (final IOException ex) {
        // Ignore
      }
    }
  }

  private static OutputStreamWriter createResultsFile() {
    if (resultDirectory == null || resultDirectory.equals("")) {
      return null;
    }

    OutputStreamWriter out = null;
    final String filename = resultDirectory + System.getProperty("file.separator") + image + "_"
        + maskImage + ".threads.csv";
    try {
      final File file = new File(filename);
      if (!file.exists()) {
        if (file.getParent() != null) {
          new File(file.getParent()).mkdirs();
        }
      }

      // Save results to file
      final FileOutputStream fos = new FileOutputStream(filename);
      out = new OutputStreamWriter(fos, "UTF-8");

      out.write("# Intensity profiles of threads:\n");
      out.write("#ID,startX,startY,endX,endY,Distance\n");
      out.write("#Distance, ...\n");
      out.write("#Image Intensity, ...\n");
      out.write("#EDM Intensity, ...\n");

      return out;
    } catch (final Exception ex) {
      IJ.log("Failed to create results file '" + filename + "': " + ex.getMessage());
      if (out != null) {
        try {
          out.close();
        } catch (final IOException ioe) {
          // Ignore
        }
      }
    }
    return null;
  }

  private static OutputStreamWriter saveResult(OutputStreamWriter out, int id, float[] line) {
    if (out == null) {
      return null;
    }

    try {
      final StringBuilder sb = new StringBuilder();
      sb.append(id).append(",");
      for (int i = 0; i < 4; i++) {
        sb.append((int) line[i]).append(",");
      }
      sb.append(IJ.d2s(line[4], 2)).append("\n");

      out.write(sb.toString());
    } catch (final IOException ex) {
      IJ.log("Failed to write to the output file : " + ex.getMessage());
      try {
        out.close();
      } catch (final IOException ex2) {
        // Ignore
      } finally {
        out = null;
      }
    }
    return out;
  }

  private static OutputStreamWriter saveResult(OutputStreamWriter out, String string, float[] d) {
    if (out == null) {
      return null;
    }

    try {
      out.write(string);
      for (int i = 0; i < d.length; i++) {
        out.write(",");
        out.write(IJ.d2s(d[i], 2));
      }
      out.write("\n");
    } catch (final IOException ex) {
      IJ.log("Failed to write to the output file : " + ex.getMessage());
      try {
        out.close();
      } catch (final IOException ex2) {
        // Ignore
      } finally {
        out = null;
      }
    }
    return out;
  }

  private static void getPoints(ChainCode code, int[] x, int[] y, float[] d) {
    int i = 0;
    x[i] = code.getX();
    y[i] = code.getY();
    d[i] = 0;
    for (final int direction : code.getRun()) {
      x[i + 1] = x[i] + ChainCode.DIR_X_OFFSET[direction];
      y[i + 1] = y[i] + ChainCode.DIR_Y_OFFSET[direction];
      d[i + 1] = d[i] + ChainCode.DIR_LENGTH[direction];
      i++;
    }
  }

  /**
   * Process the 1-D line and count the number of maxima. Total count will be set into the first
   * index, internal maxima are set in the second index.
   *
   * @param x the x
   * @param y the y
   * @param maxima the maxima
   * @return The distance from the start for each maxima
   */
  public static float[] countMaxima(float[] x, float[] y, int[] maxima) {
    int total = 0;
    int internal = 0;

    if (x.length == 1) {
      maxima[0] = (y[0] != 0) ? 1 : 0;
      maxima[1] = 0;
      return new float[] {0};
    }
    if (x.length == 2) {
      maxima[1] = 0;
      if (y[0] != 0 && y[1] != 0) {
        maxima[0] = 1;
        return new float[] {distance(x[0], x[1])};
      }
      if (y[0] != 0) {
        maxima[0] = 1;
        return new float[] {0};
      }
      if (y[1] != 0) {
        maxima[0] = 1;
        return new float[] {x[1]};
      }
      maxima[0] = 1;
      return new float[0];
    }

    final float[] max = new float[x.length];

    // Move along the line moving up to a maxima or down to a minima.
    boolean upDirection = (y[0] <= y[1]);
    int starti = 0; // last position known to be higher than the previous
    if (!upDirection) {
      // Maxima at first point
      max[total++] = x[starti];
    }

    for (int i = 0; i < x.length; i++) {
      final int j = i + 1;
      if (upDirection) {
        // Search for next maxima
        boolean isMaxima = false;
        if (j < x.length) {
          if (y[i] > y[j]) {
            isMaxima = true;
          } else if (y[i] < y[j]) {
            starti = j;
          }
        } else {
          isMaxima = true;
        }

        if (isMaxima) {
          // Count the maxima (if non-zero)
          if (y[i] > 0) {
            // Record the centre of the maxima between starti and i
            final double centre = (i + starti) / 2.0;
            max[total++] = distance(x[(int) Math.floor(centre)], x[(int) Math.ceil(centre)]);
            if (starti != 0 && j < x.length) {
              internal++;
            }
          }
          upDirection = false;
        }
      } else {
        // Search for next minima
        boolean isMinima = false;
        if (j < x.length) {
          if (y[i] < y[j]) {
            isMinima = true;
            starti = j;
          }
        } else {
          isMinima = true;
        }

        if (isMinima) {
          upDirection = true;
        }
      }
    }

    maxima[0] = total;
    maxima[1] = internal;

    return Arrays.copyOf(max, total);
  }

  private static float distance(float f, float g) {
    return (f + g) * 0.5f;
  }

  private static double[] extractStatistics(int[] x, int[] y, FloatProcessor floatImage,
      float[] data) {
    if (data == null) {
      data = new float[x.length];
    }
    for (int i = 0; i < x.length; i++) {
      data[i] = floatImage.getf(x[i], y[i]);
    }

    return getStatistics(data);
  }

  private static void convertObjects(float[] data) {
    final float foreground = (Prefs.blackBackground) ? 255 : 0;
    for (int i = 0; i < data.length; i++) {
      data[i] = (data[i] == foreground) ? 1 : 0;
    }
  }

  private static void createResultsWindow() {
    if (java.awt.GraphicsEnvironment.isHeadless()) {
      if (writeHeader) {
        writeHeader = false;
        IJ.log(createResultsHeader());
      }
    } else if (resultsWindow == null || !resultsWindow.isShowing()) {
      resultsWindow = new TextWindow(TITLE + " Results", createResultsHeader(), "", 400, 500);
    }
  }

  private static String createResultsHeader() {
    final StringBuilder sb = new StringBuilder();
    sb.append("ID\t");
    sb.append("StartX\t");
    sb.append("StartY\t");
    sb.append("EndX\t");
    sb.append("EndY\t");
    sb.append("Length\t");
    sb.append("Maxima\t");
    sb.append("InnerMaxima\t");
    sb.append("Objects\t");
    sb.append("InnerObjects\t");
    sb.append("Img Min\t");
    sb.append("Img Max\t");
    sb.append("Img Av\t");
    sb.append("Img SD\t");
    sb.append("EDM Min\t");
    sb.append("EDM Max\t");
    sb.append("EDM Av\t");
    sb.append("EDM SD\t");
    return sb.toString();
  }

  private static void addResult(int id, float[] line, int[] maxima, int[] objectMaxima,
      double[] imageStats, double[] edmStats) {
    final StringBuilder sb = new StringBuilder();
    sb.append(id).append('\t');
    for (int i = 0; i < 4; i++) {
      sb.append((int) line[i]).append('\t');
    }
    sb.append(IJ.d2s(line[4], 2)).append('\t');
    for (int i = 0; i < 2; i++) {
      sb.append(maxima[i]).append('\t');
    }
    for (int i = 0; i < 2; i++) {
      sb.append(objectMaxima[i]).append('\t');
    }
    for (int i = 0; i < 4; i++) {
      sb.append(IJ.d2s(imageStats[i], 2)).append('\t');
    }
    for (int i = 0; i < 4; i++) {
      sb.append(IJ.d2s(edmStats[i], 2)).append('\t');
    }

    if (java.awt.GraphicsEnvironment.isHeadless()) {
      IJ.log(sb.toString());
    } else {
      resultsWindow.append(sb.toString());
    }
  }
}
