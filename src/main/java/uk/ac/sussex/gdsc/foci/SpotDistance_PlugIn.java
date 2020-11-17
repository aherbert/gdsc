/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2020 Alex Herbert
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
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.MacroInstaller;
import ij.plugin.PlugIn;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.text.TextPanel;
import ij.text.TextWindow;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.match.MatchCalculator;
import uk.ac.sussex.gdsc.core.match.PointPair;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.core.utils.concurrent.ConcurrencyUtils;
import uk.ac.sussex.gdsc.foci.FindFociOptions.OutputOption;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.AlgorithmOption;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.BackgroundMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.CentreMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.MaskMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.PeakMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.SearchMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.SortMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.ThresholdMethod;
import uk.ac.sussex.gdsc.ij.plugin.filter.DifferenceOfGaussians;

/**
 * Output the distances between spots within a mask region.
 *
 * <p>For each mask region a Difference of Gaussians is performed to enhance spot features. The
 * spots are then identified and the distances between spots are calculated.
 *
 * <p>A mask can be defined using a freehand ROI or provided as an image. All pixels with the same
 * non-zero value are used to define a mask region. The image can thus be used to define multiple
 * regions using pixel values 1,2,3, etc.
 */
public class SpotDistance_PlugIn implements PlugIn {
  /** The plugin title. */
  private static final String TITLE = "Spot Distance";

  private static final int[] DIR_X_OFFSET = {0, 1, 1, 1, 0, -1, -1, -1};
  private static final int[] DIR_Y_OFFSET = {-1, -1, 0, 1, 1, 1, 0, -1};
  private static final String[] OVERLAY = {"None", "Slice position", "Entire stack"};

  private static final AtomicReference<TextWindow> resultsWindowRef = new AtomicReference<>();
  private static final AtomicReference<TextWindow> summaryWindowRef = new AtomicReference<>();
  private static final AtomicReference<TextWindow> distancesWindowRef = new AtomicReference<>();

  private TextWindow resultsWindow;
  private TextWindow summaryWindow;
  private TextWindow distancesWindow;

  // Only updated when holding the lock
  private static final Object lock = new Object();
  private static int lastResultLineCount;
  private static int lastSummaryLineCount;
  private static int lastDistancesLineCount;

  // Atomically update the undo flag
  private static AtomicBoolean allowUndo = new AtomicBoolean();

  // Debugging images only modified with a synchronized block
  private static ImagePlus debugSpotImp;
  private static ImagePlus debugSpotImp2;
  private static ImagePlus debugMaskImp;

  private ImagePlus imp;
  private ImagePlus maskImp;
  private ImagePlus projectionImp;
  private String resultEntry;
  private Calibration cal;
  private double maxDistance2;

  // Used to cache the mask region
  private int[] regions;
  private ImagePlus regionImp;
  private Rectangle bounds = new Rectangle();
  private Rectangle cachedBlurBounds;

  // Store the last frame results to allow primitive tracking
  private ArrayList<DistanceResult> prevResultsArray;
  private ArrayList<DistanceResult> prevResultsArray2;

  // Used for a particle search
  private int maxx;
  private int xlimit;
  private int ylimit;
  private int[] offset;

  /** The current settings for the plugin instance. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    String maskImage = "";
    double smoothingSize = 1.5;
    double featureSize = 4.5;

    boolean autoThreshold = true;
    double stdDevAboveBackground = 3;
    int minPeakSize = 15;
    double minPeakHeight = 5;
    int maxPeaks = 50;

    double circularityLimit = 0.7;
    int showOverlay = 2;
    double maxDistance;
    boolean processFrames;
    boolean showProjection;
    boolean showDistances;
    boolean pixelDistances = true;
    boolean calibratedDistances = true;
    boolean trackObjects;
    int regionCounter = 1;
    boolean debug;

    boolean dualChannel;
    int c1 = 1;
    int c2 = 2;

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
      maskImage = source.maskImage;
      smoothingSize = source.smoothingSize;
      featureSize = source.featureSize;

      autoThreshold = source.autoThreshold;
      stdDevAboveBackground = source.stdDevAboveBackground;
      minPeakSize = source.minPeakSize;
      minPeakHeight = source.minPeakHeight;
      maxPeaks = source.maxPeaks;

      circularityLimit = source.circularityLimit;
      showOverlay = source.showOverlay;
      maxDistance = source.maxDistance;
      processFrames = source.processFrames;
      showProjection = source.showProjection;
      showDistances = source.showDistances;
      pixelDistances = source.pixelDistances;
      calibratedDistances = source.calibratedDistances;
      trackObjects = source.trackObjects;
      regionCounter = source.regionCounter;
      debug = source.debug;

      dualChannel = source.dualChannel;
      c1 = source.c1;
      c2 = source.c2;
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
   * Used to store results and cache XYZ output formatting.
   */
  private class DistanceResult extends BasePoint {
    int id;
    double circularity;
    String pixelXyz;
    String calXyz;

    DistanceResult(int x, int y, int z, double circularity) {
      super(x, y, z);
      this.circularity = circularity;
    }

    String getPixelXyz() {
      if (pixelXyz == null) {
        pixelXyz = String.format("%d\t%d\t%d", x, y, z);
      }
      return pixelXyz;
    }

    String getCalXyz() {
      if (calXyz == null) {
        calXyz = String.format("%s\t%s\t%s", MathUtils.rounded(x * cal.pixelWidth),
            MathUtils.rounded(y * cal.pixelHeight), MathUtils.rounded(z * cal.pixelDepth));
      }
      return calXyz;
    }

    @Override
    public boolean equals(Object object) {
      // Ignore the additional fields in the equals comparison
      return super.equals(object);
    }

    @Override
    public int hashCode() {
      // Ignore the additional fields in the hash code
      return super.hashCode();
    }
  }

  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    boolean extraOptions = IJ.shiftKeyDown();

    if ("undo".equals(arg)) {
      undoLastResult();
      return;
    }

    if ("extra".equals(arg)) {
      extraOptions = true;
    }

    imp = WindowManager.getCurrentImage();

    if (null == imp) {
      IJ.noImage();
      return;
    }

    if (!FindFoci_PlugIn.isSupported(imp.getBitDepth())) {
      IJ.error(TITLE, "Only " + FindFoci_PlugIn.SUPPORTED_BIT_DEPTH + " images are supported");
      return;
    }

    // Build a list of the open images
    final String[] maskImageList = buildMaskList(imp);

    final Roi roi = imp.getRoi();
    if (maskImageList.length == 1 && (roi == null || !roi.isArea())) {
      IJ.error(TITLE, "No mask images and no area ROI");
      return;
    }

    settings = Settings.load();

    boolean selectDualChannel = false;

    // Allow repeat with same parameters. "redo" will skip this dialog.
    if (!"redo".equals(arg)) {
      final GenericDialog gd = new GenericDialog(TITLE);

      gd.addMessage("Detects spots within a mask/ROI region\nand computes all-vs-all distances.\n"
          + "(Hold shift for extra options)");

      gd.addChoice("Mask", maskImageList, settings.maskImage);
      gd.addNumericField("Smoothing", settings.smoothingSize, 2, 6, "pixels");
      gd.addNumericField("Feature_Size", settings.featureSize, 2, 6, "pixels");
      if (extraOptions) {
        gd.addCheckbox("Auto_threshold", settings.autoThreshold);
        gd.addNumericField("Background (SD > Av)", settings.stdDevAboveBackground, 1);
        gd.addNumericField("Min_peak_size", settings.minPeakSize, 0);
        gd.addNumericField("Min_peak_height", settings.minPeakHeight, 2);
        gd.addNumericField("Max_peaks", settings.maxPeaks, 2);
      }
      gd.addNumericField("Ciruclarity_limit", settings.circularityLimit, 2);
      gd.addNumericField("Max_distance", settings.maxDistance, 2, 6, "pixels");
      if (imp.getNFrames() != 1) {
        gd.addCheckbox("Processettings.frames", settings.processFrames);
      }
      if (imp.getNChannels() != 1) {
        gd.addCheckbox("Dual_channel", settings.dualChannel);
      }
      gd.addCheckbox("Show_projection", settings.showProjection);
      gd.addCheckbox("Show_distances", settings.showDistances);
      gd.addChoice("Show_overlay", OVERLAY, OVERLAY[settings.showOverlay]);
      gd.addNumericField("Region_counter", settings.regionCounter, 0);
      if (extraOptions) {
        gd.addCheckbox("Show_spot_image (debugging)", settings.debug);
      }
      gd.addCheckbox("Pixel_distances", settings.pixelDistances);
      gd.addCheckbox("Calibrated_distances", settings.calibratedDistances);
      gd.addCheckbox("Track_objects", settings.trackObjects);
      gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.FIND_FOCI);

      gd.showDialog();
      if (!gd.wasOKed()) {
        return;
      }

      settings.maskImage = gd.getNextChoice();
      settings.smoothingSize = gd.getNextNumber();
      settings.featureSize = gd.getNextNumber();
      if (extraOptions) {
        settings.autoThreshold = gd.getNextBoolean();
        settings.stdDevAboveBackground = gd.getNextNumber();
        settings.minPeakSize = (int) gd.getNextNumber();
        settings.minPeakHeight = (int) gd.getNextNumber();
        settings.maxPeaks = (int) gd.getNextNumber();
      }
      settings.circularityLimit = gd.getNextNumber();
      settings.maxDistance = gd.getNextNumber();
      if (imp.getNFrames() != 1) {
        settings.processFrames = gd.getNextBoolean();
      }
      if (imp.getNChannels() != 1) {
        selectDualChannel = settings.dualChannel = gd.getNextBoolean();
      }
      settings.showProjection = gd.getNextBoolean();
      settings.showDistances = gd.getNextBoolean();
      settings.showOverlay = gd.getNextChoiceIndex();
      settings.regionCounter = (int) gd.getNextNumber();
      if (extraOptions) {
        settings.debug = gd.getNextBoolean();
      } else {
        settings.debug = false;
      }
      settings.pixelDistances = gd.getNextBoolean();
      settings.calibratedDistances = gd.getNextBoolean();
      settings.trackObjects = gd.getNextBoolean();
      if (!(settings.pixelDistances || settings.calibratedDistances)) {
        settings.calibratedDistances = true;
      }

      settings.save();
    }

    maskImp = checkMask(imp, settings.maskImage);

    if (maskImp == null) {
      return;
    }

    boolean doDualChannel;
    if (selectDualChannel) {
      // Select dual channel is only true if the first settings dialog was shown
      // (not for "redo" analysis)

      final GenericDialog gd = new GenericDialog(TITLE);
      gd.addMessage("Select the channels for dual channel analysis");

      final String[] list = new String[imp.getNChannels()];
      for (int i = 0; i < list.length; i++) {
        list[i] = "Channel " + (i + 1);
      }
      final int myC1 = (settings.c1 > list.length) ? 0 : settings.c1 - 1;
      int myC2 = (settings.c2 > list.length) ? 0 : settings.c2 - 1;
      if (myC1 == myC2) {
        myC2 = (myC1 + 1) % list.length;
      }
      gd.addChoice("Channel_1", list, list[myC1]);
      gd.addChoice("Channel_2", list, list[myC2]);
      gd.showDialog();
      if (!gd.wasOKed()) {
        return;
      }

      settings.c1 = gd.getNextChoiceIndex() + 1;
      settings.c2 = gd.getNextChoiceIndex() + 1;

      doDualChannel = settings.c1 != settings.c2;
    } else {
      doDualChannel = settings.dualChannel;
    }

    initialise();

    final int channel = (doDualChannel) ? settings.c1 : imp.getChannel();
    final ImageStack imageStack = imp.getImageStack();
    final ImageStack maskStack = maskImp.getImageStack();

    // Cache the mask image. Only recreate the mask if using multiple frames
    ImageStack s2 = null;

    final int[] frames = buildFrameList();

    createProjection(frames, doDualChannel);

    for (int i = 0; i < frames.length; i++) {
      final int frame = frames[i];

      // Build an image and mask for the frame
      final ImageStack s1 = new ImageStack(imp.getWidth(), imp.getHeight(), imp.getNSlices());
      final ImageStack s1b =
          (doDualChannel) ? new ImageStack(imp.getWidth(), imp.getHeight(), imp.getNSlices())
              : null;

      for (int slice = 1; slice <= imp.getNSlices(); slice++) {
        int stackIndex = imp.getStackIndex(channel, slice, frame);
        ImageProcessor ip = imageStack.getProcessor(stackIndex);
        s1.setPixels(ip.getPixels(), slice);
        if (doDualChannel) {
          stackIndex = imp.getStackIndex(settings.c2, slice, frame);
          ip = imageStack.getProcessor(stackIndex);
          s1b.setPixels(ip.getPixels(), slice);
        }
      }

      if (s2 == null) {
        s2 = new ImageStack(imp.getWidth(), imp.getHeight(), maskImp.getNSlices());

        // Only create a mask with 1 slice if necessary
        for (int slice = 1; slice <= maskImp.getNSlices(); slice++) {
          final int maskChannel = (maskImp.getNChannels() > 1) ? channel : 1;
          final int maskFrame = (maskImp.getNFrames() > 1) ? frame : 1;
          final int maskSlice = (maskImp.getNSlices() > 1) ? slice : 1;

          final int maskStackIndex = maskImp.getStackIndex(maskChannel, maskSlice, maskFrame);
          final ImageProcessor maskIp = maskStack.getProcessor(maskStackIndex);

          s2.setPixels(maskIp.getPixels(), slice);
        }
      }

      if (ImageJUtils.isInterrupted()) {
        return;
      }

      exec(frame, channel, s1, s1b, s2);
      IJ.showProgress(i + 1, frames.length);

      if (ImageJUtils.isInterrupted()) {
        return;
      }

      // Recreate the mask if using multiple frames
      if (maskImp.getNFrames() > 1) {
        s2 = null;
      }
    }
    resultsWindow.append("");

    if (settings.showProjection) {
      projectionImp.setSlice(1);
    }
  }

  // Helper methods for macros

  /**
   * Run the SpotDistance plugin.
   */
  public static void run() {
    new SpotDistance_PlugIn().run(null);
  }

  /**
   * Run the SpotDistance plugin with the argument "redo".
   */
  public static void redo() {
    new SpotDistance_PlugIn().run("redo");
  }

  /**
   * Run the SpotDistance plugin with the argument "undo".
   */
  public static void undo() {
    new SpotDistance_PlugIn().run("undo");
  }

  /**
   * Run the SpotDistance plugin with the argument "extra".
   */
  public static void extra() {
    new SpotDistance_PlugIn().run("extra");
  }

  private static void undoLastResult() {
    if (allowUndo.compareAndSet(true, false)) {
      // Do this atomically
      int resultLineCount;
      int summaryLineCount;
      int distancesLineCount;
      synchronized (lock) {
        resultLineCount = lastResultLineCount;
        summaryLineCount = lastSummaryLineCount;
        distancesLineCount = lastDistancesLineCount;
      }
      removeAfterLine(resultsWindowRef, resultLineCount);
      removeAfterLine(summaryWindowRef, summaryLineCount);
      removeAfterLine(distancesWindowRef, distancesLineCount);
    }
  }

  private static void removeAfterLine(AtomicReference<TextWindow> windowRef, int line) {
    if (line > 0) {
      final TextWindow window = windowRef.get();
      if (window != null) {
        final TextPanel tp = window.getTextPanel();
        final int nLines = tp.getLineCount();
        if (line < nLines) {
          tp.setSelection(line, nLines);
          tp.clearSelection();
        }
      }
    }
  }

  private void initialise() {
    regions = null;
    regionImp = null;
    bounds = new Rectangle();
    allowUndo.set(false);
    maxDistance2 = settings.maxDistance * settings.maxDistance;
    if (settings.showOverlay > 0) {
      imp.setOverlay(null);
    }
    createResultsWindow();
  }

  private int[] buildFrameList() {
    int[] frames;
    if (settings.processFrames) {
      frames = new int[imp.getNFrames()];
      for (int i = 0; i < frames.length; i++) {
        frames[i] = i + 1;
      }
    } else {
      frames = new int[] {imp.getFrame()};
    }
    return frames;
  }

  private void createProjection(int[] frames, boolean dualChannel) {
    if (!settings.showProjection) {
      return;
    }

    final ImageStack stack = imp.createEmptyStack();
    final ImageProcessor ip = imp.getProcessor();
    for (final int f : frames) {
      stack.addSlice("Frame " + f, ip.createProcessor(ip.getWidth(), ip.getHeight()));
    }
    if (dualChannel) {
      for (final int f : frames) {
        stack.addSlice("Frame " + f, ip.createProcessor(ip.getWidth(), ip.getHeight()));
      }
    }

    final String title = imp.getTitle() + " Spots Projection";
    projectionImp = WindowManager.getImage(title);
    final int nChannels = (dualChannel) ? 2 : 1;
    if (projectionImp == null) {
      projectionImp = new ImagePlus(title, stack);
    } else {
      projectionImp.setStack(stack);
    }
    projectionImp.setSlice(1);
    projectionImp.setOverlay(null);
    projectionImp.setDimensions(nChannels, 1, frames.length);
    projectionImp.setOpenAsHyperStack(dualChannel);
    projectionImp.show();
  }

  private static String[] buildMaskList(ImagePlus imp) {
    final ArrayList<String> newImageList = new ArrayList<>();
    newImageList.add("[None]");

    for (final int id : uk.ac.sussex.gdsc.core.ij.ImageJUtils.getIdList()) {
      final ImagePlus maskImp = WindowManager.getImage(id);
      // Mask image must:
      // - Not be the same image
      // - Match XY dimensions of the input image
      // - Math Z dimensions of input or else be a single image
      if (maskImp != null && maskImp.getID() != imp.getID() && maskImp.getWidth() == imp.getWidth()
          && maskImp.getHeight() == imp.getHeight()
          && (maskImp.getNSlices() == imp.getNSlices() || maskImp.getNSlices() == 1)
          && (maskImp.getNChannels() == imp.getNChannels() || maskImp.getNChannels() == 1)
          && (maskImp.getNFrames() == imp.getNFrames() || maskImp.getNFrames() == 1)) {
        newImageList.add(maskImp.getTitle());
      }
    }

    return newImageList.toArray(new String[0]);
  }

  private ImagePlus checkMask(ImagePlus imp, String maskImage) {
    ImagePlus mask = WindowManager.getImage(maskImage);

    if (mask == null) {
      // Build a mask image using the input image ROI
      final Roi roi = imp.getRoi();
      if (roi == null || !roi.isArea()) {
        IJ.error(TITLE, "No region defined (use an area ROI or an input mask)");
        return null;
      }
      final ShortProcessor ip = new ShortProcessor(imp.getWidth(), imp.getHeight());
      ip.setValue(1);
      ip.setRoi(roi);
      ip.fill(roi);

      // Label each separate region with a different number
      labelRegions(ip);

      mask = new ImagePlus("Mask", ip);
    }

    if (imp.getNSlices() > 1 && mask.getNSlices() != 1 && mask.getNSlices() != imp.getNSlices()) {
      IJ.error(TITLE, "Mask region has incorrect slice dimension");
      return null;
    }
    if (imp.getNChannels() > 1 && mask.getNChannels() != 1
        && mask.getNChannels() != imp.getNChannels()) {
      IJ.error(TITLE, "Mask region has incorrect channel dimension");
      return null;
    }
    if (imp.getNFrames() > 1 && settings.processFrames && mask.getNFrames() != 1
        && mask.getNFrames() != imp.getNFrames()) {
      IJ.error(TITLE, "Mask region has incorrect frame dimension");
      return null;
    }

    return mask;
  }

  /**
   * Create the result window (if it is not available).
   */
  private void createResultsWindow() {
    // Create stacked output text windows
    resultsWindow = createTextWindow(createResultsHeader(), resultsWindowRef, " Positions", null);
    summaryWindow =
        createTextWindow(createSummaryHeader(), summaryWindowRef, " Summary", resultsWindow);
    if (settings.showDistances) {
      distancesWindow = createTextWindow(createDistancesHeader(), distancesWindowRef, " Distances",
          summaryWindow);
    }

    // Store the line number for each text window
    setLastLineCount(resultsWindow.getTextPanel().getLineCount(),
        summaryWindow.getTextPanel().getLineCount(),
        (distancesWindow != null) ? distancesWindow.getTextPanel().getLineCount() : 0);

    // Create the image result prefix
    final StringBuilder sb = new StringBuilder();
    sb.append(imp.getTitle()).append('\t');
    if (settings.calibratedDistances) {
      cal = imp.getCalibration();
      sb.append(cal.getXUnit());
      if (!(cal.getYUnit().equalsIgnoreCase(cal.getXUnit())
          && cal.getZUnit().equalsIgnoreCase(cal.getXUnit()))) {
        sb.append(" ").append(cal.getYUnit()).append(" ").append(cal.getZUnit());
      }
    } else {
      sb.append("pixels");
    }
    sb.append('\t');
    resultEntry = sb.toString();
  }

  private static TextWindow createTextWindow(String header,
      AtomicReference<TextWindow> windowReference, String titleSuffix, TextWindow parent) {
    return ConcurrencyUtils.refresh(windowReference,
        // Window must have the same header
        window -> sameHeader(window, header),
        // Create a new window
        () -> {
          final TextWindow window = new TextWindow(TITLE + titleSuffix, header, "", 700, 300);
          // Position relative to parent
          if (parent != null) {
            final Point p = parent.getLocation();
            p.y += parent.getHeight();
            window.setLocation(p);
          }
          return window;
        });
  }

  private static void setLastLineCount(int resultLineCount, int summaryLineCount,
      int distancesLineCount) {
    synchronized (lock) {
      lastResultLineCount = resultLineCount;
      lastSummaryLineCount = summaryLineCount;
      lastDistancesLineCount = distancesLineCount;
    }
  }

  private static boolean sameHeader(TextWindow results, String header) {
    return results.getTextPanel().getColumnHeadings().equals(header);
  }

  private String createResultsHeader() {
    final StringBuilder sb = new StringBuilder();
    sb.append("Image\t");
    sb.append("Units\t");
    sb.append("Frame\t");
    sb.append("Channel\t");
    sb.append("Region\t");
    if (settings.pixelDistances) {
      sb.append("x (px)\t");
      sb.append("y (px)\t");
      sb.append("z (px)\t");
    }
    if (settings.calibratedDistances) {
      sb.append("X\t");
      sb.append("Y\t");
      sb.append("Z\t");
    }
    sb.append("Circularity");
    return sb.toString();
  }

  private String createSummaryHeader() {
    final StringBuilder sb = new StringBuilder();
    sb.append("Image\t");
    sb.append("Units\t");
    sb.append("Frame\t");
    sb.append("Channel\t");
    sb.append("Region\t");
    sb.append("Spots");
    if (settings.pixelDistances) {
      sb.append('\t');
      sb.append("Min (px)\t");
      sb.append("Max (px)\t");
      sb.append("Av (px)");
    }
    if (settings.calibratedDistances) {
      sb.append('\t');
      sb.append("Min\t");
      sb.append("Max\t");
      sb.append("Av");
    }
    return sb.toString();
  }

  private String createDistancesHeader() {
    final StringBuilder sb = new StringBuilder();
    sb.append("Image\t");
    sb.append("Units\t");
    sb.append("Frame\t");
    sb.append("Channel\t");
    sb.append("Region");
    if (settings.pixelDistances) {
      sb.append('\t');
      sb.append("X1 (px)\tY1 (px)\tZ1 (px)\t");
      sb.append("X2 (px)\tY2 (px)\tZ2 (px)\t");
      sb.append("Distance (px)");
    }
    if (settings.calibratedDistances) {
      sb.append('\t');
      sb.append("X1\tY1\tZ1\t");
      sb.append("X2\tY2\tZ2\t");
      sb.append("Distance");
    }
    return sb.toString();
  }

  private void exec(int frame, int channel, ImageStack s1, ImageStack s1b, ImageStack s2) {
    // Check the mask we are using.
    // We can use a ROI bounds for the Gaussian blur to increase speed.
    Rectangle blurBounds = cachedBlurBounds;
    if (regions == null) {
      regions = findRegions(s2);
    }
    if (regions.length == 0) {
      return; // Q. Should anything be reported?
    }

    if (regions.length == 1) {
      if (regionImp == null) {
        regionImp = extractRegion(s2, regions[0], bounds);
        regionImp = crop(regionImp, bounds);
      }
      blurBounds = bounds;
    } else if (blurBounds == null) {
      // Create the bounds using the min and max of all the regions
      blurBounds = findBounds(s2);
    }

    cachedBlurBounds = blurBounds;

    if (settings.showProjection) {
      // Do Z-projection into the current image processor of the stack
      runZProjection(frame, 1, s1);
      if (s1b != null) {
        runZProjection(frame, 2, s1b);
      }
    }

    // Perform Difference of Gaussians to enhance the spot features if two radii are provided
    if (settings.featureSize > 0) {
      s1 = runDifferenceOfGaussians(s1, blurBounds);
      if (s1b != null) {
        s1b = runDifferenceOfGaussians(s1b, blurBounds);
      }
    } else if (settings.smoothingSize > 0) {
      // Just perform image smoothing
      s1 = runGaussianBlur(s1, blurBounds);
      if (s1b != null) {
        s1b = runGaussianBlur(s1b, blurBounds);
      }
    }

    final ImagePlus spotImp = new ImagePlus(null, s1);
    final ImagePlus spotImp2 = (s1b != null) ? new ImagePlus(null, s1b) : null;

    // Process each region with FindFoci

    // TODO - Optimise these settings
    // - Maybe need a global thresholding on the whole DoG image
    // - Try Float IP for the DoG image
    // - Bigger feature size for DoG?

    final FindFoci_PlugIn ff = new FindFoci_PlugIn();
    final FindFociProcessorOptions processorOptions = new FindFociProcessorOptions();
    processorOptions.setBackgroundMethod(settings.autoThreshold ? BackgroundMethod.AUTO_THRESHOLD
        : BackgroundMethod.STD_DEV_ABOVE_MEAN);
    processorOptions.setBackgroundParameter(settings.stdDevAboveBackground);
    processorOptions.setThresholdMethod(ThresholdMethod.OTSU);
    processorOptions.setSearchMethod(SearchMethod.ABOVE_BACKGROUND);
    processorOptions.setMaxPeaks(33000);
    processorOptions.setMinSize(settings.minPeakSize);
    processorOptions.setPeakMethod(PeakMethod.ABSOLUTE);
    processorOptions.setPeakParameter(0);
    processorOptions.setOption(AlgorithmOption.MINIMUM_ABOVE_SADDLE, true);
    processorOptions.setMaskMethod(MaskMethod.PEAKS_ABOVE_SADDLE);
    processorOptions.setSortMethod(SortMethod.MAX_VALUE);
    processorOptions.setGaussianBlur(0);
    processorOptions.setCentreMethod(CentreMethod.CENTRE_OF_MASS_ORIGINAL);
    processorOptions.setCentreParameter(2);

    final FindFociOptions options = new FindFociOptions();
    options.getOptions().clear();
    options.setOption(OutputOption.RESULTS_TABLE, true);

    Overlay overlay = null;
    Overlay overlay2 = null;
    if (settings.showOverlay > 0) {
      overlay = new Overlay();
      if (s1b != null) {
        overlay2 = new Overlay();
      }
    }

    for (final int region : regions) {
      // Cache the cropped mask if it has only one region
      if (regions.length != 1) {
        regionImp = null;
      }

      if (regionImp == null) {
        regionImp = extractRegion(s2, region, bounds);
        regionImp = crop(regionImp, bounds);
      }

      final ImagePlus croppedImp = crop(spotImp, bounds);
      ImagePlus croppedImp2 = null;
      final ImageStack stack = croppedImp.getImageStack();
      final float scale = scaleImage(stack);
      croppedImp.setStack(stack); // Updates the image bit depth
      processorOptions.setPeakParameter(Math.round(settings.minPeakHeight * scale));
      double peakParameter2 = 0;

      if (s1b != null) {
        croppedImp2 = crop(spotImp2, bounds);
        final ImageStack stack2 = croppedImp2.getImageStack();
        final float scale2 = scaleImage(stack2);
        croppedImp2.setStack(stack2); // Updates the image bit depth
        peakParameter2 = Math.round(settings.minPeakHeight * scale2);
      }

      if (settings.debug) {
        showSpotImage(croppedImp, croppedImp2, regionImp, cal);
      }

      FindFociBaseProcessor ffp = ff.createFindFociProcessor(croppedImp);
      final FindFociResults ffResult = ffp.findMaxima(croppedImp, regionImp, processorOptions);

      if (ImageJUtils.isInterrupted()) {
        return;
      }

      final ArrayList<DistanceResult> resultsArray =
          analyseResults(prevResultsArray, croppedImp, ffp, ffResult, frame, channel, overlay);
      for (final DistanceResult result : resultsArray) {
        addResult(frame, channel, region, result);
      }

      if (settings.trackObjects) {
        prevResultsArray = resultsArray;
      }

      if (s1b == null) {
        if (settings.trackObjects) {
          prevResultsArray2 = null;
        }

        // Single channel analysis
        final String ch = Integer.toString(channel);

        if (resultsArray.size() < 2) {
          // No comparisons possible
          addSummaryResult(frame, ch, region, resultsArray.size());
          continue;
        }

        double minD = Double.POSITIVE_INFINITY;
        double minD2 = Double.POSITIVE_INFINITY;
        double maxD = 0;
        double maxD2 = 0;
        double sumD = 0;
        double sumD2 = 0;
        int count = 0;
        for (int i = 0; i < resultsArray.size(); i++) {
          final DistanceResult r1 = resultsArray.get(i);
          for (int j = i + 1; j < resultsArray.size(); j++) {
            final DistanceResult r2 = resultsArray.get(j);
            final double[] diff = new double[] {(r1.x - r2.x), (r1.y - r2.y), (r1.z - r2.z)};

            // Ignore distances above the maximum
            double diff2 = -1;
            if (maxDistance2 != 0) {
              diff2 = diff[0] * diff[0] + diff[1] * diff[1] + diff[2] * diff[2];
              if (diff2 > maxDistance2) {
                continue;
              }
            }

            count++;

            double distance = 0;
            double calibratedDistance = 0;
            if (settings.pixelDistances) {
              if (diff2 == -1) {
                diff2 = diff[0] * diff[0] + diff[1] * diff[1] + diff[2] * diff[2];
              }
              distance = Math.sqrt(diff2);
              if (distance < minD) {
                minD = distance;
              }
              if (distance > maxD) {
                maxD = distance;
              }
              sumD += distance;
            }

            if (settings.calibratedDistances) {
              diff[0] *= cal.pixelWidth;
              diff[1] *= cal.pixelHeight;
              diff[2] *= cal.pixelDepth;
              // TODO - This will not be valid if the units are not the same for all dimensions
              calibratedDistance =
                  Math.sqrt(diff[0] * diff[0] + diff[1] * diff[1] + diff[2] * diff[2]);
              if (calibratedDistance < minD2) {
                minD2 = calibratedDistance;
              }
              if (calibratedDistance > maxD2) {
                maxD2 = calibratedDistance;
              }
              sumD2 += calibratedDistance;
            }

            if (settings.showDistances) {
              addDistanceResult(frame, ch, region, r1, r2, distance, calibratedDistance);
            }
          }
        }

        addSummaryResult(frame, ch, region, resultsArray.size(), minD, maxD, sumD / count, minD2,
            maxD2, sumD2 / count);
      } else {
        // Dual channel analysis.
        // Analyse the second channel
        processorOptions.setPeakParameter(peakParameter2);
        ffp = ff.createFindFociProcessor(croppedImp2);
        final FindFociResults results2 = ffp.findMaxima(croppedImp2, regionImp, processorOptions);

        if (ImageJUtils.isInterrupted()) {
          return;
        }

        final ArrayList<DistanceResult> resultsArray2 = analyseResults(prevResultsArray2,
            croppedImp2, ffp, results2, frame, settings.c2, overlay2);
        for (final DistanceResult result : resultsArray2) {
          addResult(frame, settings.c2, region, result);
        }

        if (settings.trackObjects) {
          prevResultsArray2 = resultsArray2;
        }

        final String ch = channel + " + " + settings.c2;

        if (resultsArray.isEmpty() || resultsArray2.isEmpty()) {
          // No comparisons possible
          addSummaryResult(frame, ch, region, resultsArray.size() + resultsArray2.size());
          continue;
        }

        double minD = Double.POSITIVE_INFINITY;
        double minD2 = Double.POSITIVE_INFINITY;
        double maxD = 0;
        double maxD2 = 0;
        double sumD = 0;
        double sumD2 = 0;
        int count = 0;
        for (final DistanceResult r1 : resultsArray) {
          for (final DistanceResult r2 : resultsArray2) {
            final double[] diff = new double[] {(r1.x - r2.x), (r1.y - r2.y), (r1.z - r2.z)};

            // Ignore distances above the maximum
            double diff2 = -1;
            if (maxDistance2 != 0) {
              diff2 = diff[0] * diff[0] + diff[1] * diff[1] + diff[2] * diff[2];
              if (diff2 > maxDistance2) {
                continue;
              }
            }

            count++;

            double distance = 0;
            double calibratedDistance = 0;
            if (settings.pixelDistances) {
              if (diff2 == -1) {
                diff2 = diff[0] * diff[0] + diff[1] * diff[1] + diff[2] * diff[2];
              }
              distance = Math.sqrt(diff2);
              if (distance < minD) {
                minD = distance;
              }
              if (distance > maxD) {
                maxD = distance;
              }
              sumD += distance;
            }

            if (settings.calibratedDistances) {
              diff[0] *= cal.pixelWidth;
              diff[1] *= cal.pixelHeight;
              diff[2] *= cal.pixelDepth;
              // TODO - This will not be valid if the units are not the same for all dimensions
              calibratedDistance =
                  Math.sqrt(diff[0] * diff[0] + diff[1] * diff[1] + diff[2] * diff[2]);
              if (calibratedDistance < minD2) {
                minD2 = calibratedDistance;
              }
              if (calibratedDistance > maxD2) {
                maxD2 = calibratedDistance;
              }
              sumD2 += calibratedDistance;
            }

            if (settings.showDistances) {
              addDistanceResult(frame, ch, region, r1, r2, distance, calibratedDistance);
            }
          }
        }

        addSummaryResult(frame, ch, region, resultsArray.size() + resultsArray2.size(), minD, maxD,
            MathUtils.div0(sumD, count), minD2, maxD2, MathUtils.div0(sumD2, count));
      }
    }

    // Append to image overlay
    if (settings.showOverlay > 0) {
      Overlay impOverlay = imp.getOverlay();
      if (impOverlay == null) {
        impOverlay = new Overlay();
      }
      addToOverlay(impOverlay, overlay, Color.magenta);
      if (overlay2 != null) {
        addToOverlay(impOverlay, overlay2, Color.yellow);
      }
      imp.setOverlay(impOverlay);
      imp.updateAndDraw();
    }

    if (settings.showProjection) {
      // Add all the ROIs of the overlay to the slice
      Overlay projOverlay = projectionImp.getOverlay();
      if (projOverlay == null) {
        projOverlay = new Overlay();
      }

      addToProjectionOverlay(projOverlay, overlay, frame, 1);
      if (overlay2 != null) {
        addToProjectionOverlay(projOverlay, overlay2, frame, 2);
      }

      final int index = projectionImp.getStackIndex(1, 1, frame);
      projectionImp.setSlice(index);
      projectionImp.setOverlay(projOverlay);
      projectionImp.updateAndDraw();
    }

    // Cache the mask only if it has one frame
    if (maskImp.getNFrames() > 1) {
      regions = null;
      regionImp = null;
      cachedBlurBounds = null;
    }
  }

  private void runZProjection(int frame, int channel, ImageStack s1) {
    final int index = projectionImp.getStackIndex(channel, 1, frame);
    projectionImp.setSliceWithoutUpdate(index);
    final ImageProcessor max = projectionImp.getProcessor();
    max.insert(s1.getProcessor(1), 0, 0);
    for (int n = 2; n <= s1.getSize(); n++) {
      final ImageProcessor ip = s1.getProcessor(n);
      for (int i = 0; i < ip.getPixelCount(); i++) {
        if (max.get(i) < ip.get(i)) {
          max.set(i, ip.get(i));
        }
      }
    }
  }

  private ImageStack runDifferenceOfGaussians(ImageStack s1, Rectangle blurBounds) {
    final ImageStack newS1 = new ImageStack(s1.getWidth(), s1.getHeight(), s1.getSize());
    for (int slice = 1; slice <= s1.getSize(); slice++) {
      final ImageProcessor ip = s1.getProcessor(slice).toFloat(0, null);
      if (blurBounds != null) {
        ip.setRoi(blurBounds);
      }
      DifferenceOfGaussians.run(ip, settings.featureSize, settings.smoothingSize);
      newS1.setPixels(ip.getPixels(), slice);
    }
    s1 = newS1;
    return s1;
  }

  private ImageStack runGaussianBlur(ImageStack s1, Rectangle blurBounds) {
    final DifferenceOfGaussians filter = new DifferenceOfGaussians();
    filter.setNoProgress(true);
    final ImageStack newS1 = new ImageStack(s1.getWidth(), s1.getHeight(), s1.getSize());
    for (int slice = 1; slice <= s1.getSize(); slice++) {
      final ImageProcessor ip = s1.getProcessor(slice).toFloat(0, null);
      if (blurBounds != null) {
        ip.setRoi(blurBounds);
        ip.snapshot(); // Needed to reset region outside ROI
      }
      filter.blurGaussian(ip, settings.smoothingSize);
      newS1.setPixels(ip.getPixels(), slice);
    }
    s1 = newS1;
    return s1;
  }

  private static void addToOverlay(Overlay mainOverlay, Overlay overlay, Color color) {
    overlay.setFillColor(color);
    overlay.setStrokeColor(color);
    for (final Roi roi : overlay.toArray()) {
      mainOverlay.add(roi);
    }
  }

  private void addToProjectionOverlay(Overlay mainOverlay, Overlay overlay, int frame,
      int channel) {
    if (projectionImp.getNFrames() == 1) {
      frame = 1;
    }
    overlay = overlay.duplicate();
    final Roi[] rois = overlay.toArray();
    for (final Roi roi : rois) {
      if (settings.dualChannel) {
        roi.setPosition(channel, 1, frame);
      } else {
        roi.setPosition(frame);
      }
      mainOverlay.add(roi);
    }
  }

  /**
   * Scale the image to maximise the information available (since the FindFoci routine uses integer
   * values).
   *
   * <p>Only scale the image if the input image stack is a FloatProcessor. The stack will be
   * converted to a ShortProcessor.
   *
   * @param s1 The image
   * @return The scale
   */
  private float scaleImage(ImageStack s1) {
    if (!(s1.getPixels(1) instanceof float[])) {
      return 1;
    }

    // Find the range
    float min = Float.MAX_VALUE;
    float max = 0;
    for (int n = 1; n <= s1.getSize(); n++) {
      final float[] pixels = (float[]) s1.getPixels(n);
      for (final float f : pixels) {
        if (min > f) {
          min = f;
        }
        if (max < f) {
          max = f;
        }
      }
    }

    // Convert pixels to short[]
    final float range = max - min;
    final float scale = (range > 0) ? 65334 / range : 1;
    if (settings.debug) {
      IJ.log(String.format("Scaling transformed image by %f (range: %f - %f)", scale, min, max));
    }
    for (int n = 1; n <= s1.getSize(); n++) {
      final float[] pixels = (float[]) s1.getPixels(n);
      final short[] newPixels = new short[pixels.length];
      for (int i = 0; i < pixels.length; i++) {
        newPixels[i] = (short) Math.round((pixels[i] - min) * scale);
      }
      s1.setPixels(newPixels, n);
    }
    return scale;
  }

  private static synchronized void showSpotImage(ImagePlus croppedImp, ImagePlus croppedImp2,
      ImagePlus regionImp, Calibration cal) {
    debugSpotImp = createImage(croppedImp, debugSpotImp, TITLE + " Pixels", 0, cal);
    if (croppedImp2 != null) {
      debugSpotImp2 = createImage(croppedImp2, debugSpotImp2, TITLE + " Pixels B", 0, cal);
    }
    debugMaskImp = createImage(regionImp, debugMaskImp, TITLE + " Region", 1, cal);
  }

  private static ImagePlus createImage(ImagePlus sourceImp, ImagePlus debugImp, String title,
      int region, Calibration cal) {
    if (debugImp == null || !debugImp.isVisible()) {
      debugImp = new ImagePlus();
      debugImp.setTitle(title);
    }
    updateImage(debugImp, sourceImp, region, cal);
    return debugImp;
  }

  private static void updateImage(ImagePlus debugImp, ImagePlus sourceImp, int region,
      Calibration cal) {
    debugImp.setStack(sourceImp.getImageStack(), 1, sourceImp.getStackSize(), 1);
    if (region > 0) {
      debugImp.setDisplayRange(region - 1.0, region);
    } else {
      debugImp.resetDisplayRange();
    }
    debugImp.updateAndDraw();
    debugImp.setCalibration(cal);
    debugImp.show();
  }

  private int[] findRegions(ImageStack s2) {
    // Build a histogram. Any non-zero value defines a region.
    final int[] histogram = s2.getProcessor(1).getHistogram();
    for (int slice = 2; slice <= maskImp.getNSlices(); slice++) {
      final int[] tmp = s2.getProcessor(slice).getHistogram();
      for (int i = 1; i < tmp.length; i++) {
        histogram[i] += tmp[i];
      }
    }

    int count = 0;
    for (int i = 1; i < histogram.length; i++) {
      if (histogram[i] != 0) {
        count++;
      }
    }

    final int[] newRegions = new int[count];
    count = 0;
    for (int i = 1; i < histogram.length; i++) {
      if (histogram[i] != 0) {
        newRegions[count++] = i;
      }
    }
    return newRegions;
  }

  private static ImagePlus extractRegion(ImageStack inputStack, int region, Rectangle bounds) {
    int minX = Integer.MAX_VALUE;
    int minY = Integer.MAX_VALUE;
    int maxX = 0;
    int maxY = 0;
    final ImageStack outputStack =
        new ImageStack(inputStack.getWidth(), inputStack.getHeight(), inputStack.getSize());
    for (int slice = 1; slice <= inputStack.getSize(); slice++) {
      final ImageProcessor ip = inputStack.getProcessor(slice);
      final byte[] mask = new byte[inputStack.getWidth() * inputStack.getHeight()];
      for (int i = 0; i < ip.getPixelCount(); i++) {
        if (ip.get(i) == region) {
          mask[i] = 1;
          // Calculate bounding rectangle
          final int x = i % inputStack.getWidth();
          final int y = i / inputStack.getWidth();
          if (minX > x) {
            minX = x;
          }
          if (minY > y) {
            minY = y;
          }
          if (maxX < x) {
            maxX = x;
          }
          if (maxY < y) {
            maxY = y;
          }
        }
      }
      outputStack.setPixels(mask, slice);
    }

    bounds.x = minX;
    bounds.y = minY;
    bounds.width = maxX - minX + 1;
    bounds.height = maxY - minY + 1;
    return new ImagePlus(null, outputStack);
  }

  private static Rectangle findBounds(ImageStack inputStack) {
    int minX = Integer.MAX_VALUE;
    int minY = Integer.MAX_VALUE;
    int maxX = 0;
    int maxY = 0;
    for (int slice = 1; slice <= inputStack.getSize(); slice++) {
      final ImageProcessor ip = inputStack.getProcessor(slice);
      for (int i = 0; i < ip.getPixelCount(); i++) {
        if (ip.get(i) != 0) {
          // Calculate bounding rectangle
          final int x = i % inputStack.getWidth();
          final int y = i / inputStack.getWidth();
          if (minX > x) {
            minX = x;
          }
          if (minY > y) {
            minY = y;
          }
          if (maxX < x) {
            maxX = x;
          }
          if (maxY < y) {
            maxY = y;
          }
        }
      }
    }

    final Rectangle bounds = new Rectangle();
    bounds.x = minX;
    bounds.y = minY;
    bounds.width = maxX - minX + 1;
    bounds.height = maxY - minY + 1;
    return bounds;
  }

  /**
   * Crop by duplication within the bounds.
   *
   * @param imp the image
   * @param bounds the bounds
   * @return the image plus
   */
  private static ImagePlus crop(ImagePlus imp, Rectangle bounds) {
    imp.setRoi(bounds);
    return imp.duplicate();
  }

  /**
   * Check the peak circularity. Add an overlay of the spots if requested.
   *
   * @param prev the prev
   * @param croppedImp the cropped imp
   * @param ffp the FindFociProcessor that generated the results
   * @param ffResult the ff result
   * @param frame the frame
   * @param channel the channel
   * @param overlay the overlay
   * @return the results
   */
  private ArrayList<DistanceResult> analyseResults(ArrayList<DistanceResult> prev,
      ImagePlus croppedImp, FindFociBaseProcessor ffp, FindFociResults ffResult, int frame,
      int channel, Overlay overlay) {
    if (ffResult == null) {
      return new ArrayList<>(0);
    }

    final ImagePlus peaksImp = ffResult.mask;
    final List<FindFociResult> resultsArray = ffResult.results;

    final ArrayList<DistanceResult> newResultsArray = new ArrayList<>(resultsArray.size());

    // Process in XYZ order
    ffp.sortAscResults(resultsArray, SortMethod.XYZ, null);

    final ImageStack maskStack = peaksImp.getImageStack();

    for (final FindFociResult result : resultsArray) {
      final int x = bounds.x + result.x;
      final int y = bounds.y + result.y;
      final int z = result.z;

      // Filter peaks on circularity (spots should be circles).
      // C = 4*pi*A / P^2
      // where A = Area, P = Perimeter
      // See: https://en.wikipedia.org/wiki/Polsby-Popper_Test
      // Q. Not sure if this will be valid for small spots.

      // Extract the peak.
      // This could extract by a % volume. At current just use the mask region.

      final ImageProcessor maskIp = maskStack.getProcessor(z + 1);

      final int peakId = result.id;
      final int maskId = resultsArray.size() - peakId + 1;
      final Roi roi = extractSelection(maskIp, maskId, channel, z + 1, frame);
      final double perimeter = roi.getLength();
      final double area = getArea(maskIp, maskId);

      final double circularity =
          perimeter == 0.0 ? 0.0 : 4.0 * Math.PI * (area / (perimeter * perimeter));

      if (circularity < settings.circularityLimit) {
        IJ.log(String.format("Excluded non-circular peak @ x%d,y%d,z%d : %g (4pi * %g / %g^2)", x,
            y, z, circularity, area, perimeter));
        continue;
      }

      if (overlay != null) {
        overlay.add(roi);
      }

      newResultsArray.add(new DistanceResult(x, y, z, circularity));
    }

    if (prev != null && !prev.isEmpty() && !newResultsArray.isEmpty()) {
      // If we have results from a previous frame then try and assign an ID to each
      // result using closest distance tracking.
      final double d = Math.sqrt((double) croppedImp.getWidth() * croppedImp.getWidth()
          + croppedImp.getHeight() * croppedImp.getHeight()
          + croppedImp.getNSlices() * croppedImp.getNSlices());
      final List<PointPair> matches = new ArrayList<>();
      MatchCalculator.analyseResults3D(prev.toArray(new DistanceResult[0]),
          newResultsArray.toArray(new DistanceResult[0]), d, null, null, null, matches);

      for (final PointPair match : matches) {
        ((DistanceResult) match.getPoint2()).id = ((DistanceResult) match.getPoint1()).id;
      }

      // Find max ID
      int id = 0;
      for (final DistanceResult r : newResultsArray) {
        if (id < r.id) {
          id = r.id;
        }
      }
      // Assign those that could not be tracked
      for (final DistanceResult r : newResultsArray) {
        if (r.id == 0) {
          r.id = ++id;
        }
      }
      // Sort
      Collections.sort(newResultsArray, (o1, o2) -> Integer.compare(o1.id, o2.id));
    }

    return newResultsArray;
  }

  private Roi extractSelection(ImageProcessor maskIp, int maskId, int channel, int slice,
      int frame) {
    maskIp.setThreshold(maskId, maskId, ImageProcessor.NO_LUT_UPDATE);
    final ThresholdToSelection ts = new ThresholdToSelection();
    final Roi roi = ts.convert(maskIp);
    if (imp.getStackSize() == 1) {
      roi.setPosition(0);
    } else if (imp.isDisplayedHyperStack()) {
      if (settings.showOverlay != 1) {
        slice = 0; // Display across entire slice stack
      }
      roi.setPosition(channel, slice, frame);
    } else {
      // We cannot support display across the entire stack if this is not a hyperstack
      final int index = imp.getStackIndex(channel, slice, frame);
      roi.setPosition(index);
    }
    final Rectangle roiBounds = roi.getBounds();
    roi.setLocation(bounds.x + roiBounds.x, bounds.y + roiBounds.y);
    return roi;
  }

  @SuppressWarnings("unused")
  private double getPerimeter(ImageProcessor maskIp, int maskId, Overlay overlay, int z) {
    maskIp.setThreshold(maskId, maskId, ImageProcessor.NO_LUT_UPDATE);
    final ThresholdToSelection ts = new ThresholdToSelection();
    final Roi roi = ts.convert(maskIp);
    if (overlay != null) {
      roi.setPosition(z);
      roi.setLocation(bounds.x, bounds.y);
      overlay.add(roi);
    }
    return roi.getLength();
  }

  private static double getArea(ImageProcessor maskIp, int maskId) {
    int area = 0;
    for (int i = 0; i < maskIp.getPixelCount(); i++) {
      if (maskIp.get(i) == maskId) {
        area++;
      }
    }
    return area;
  }

  private void addResult(int frame, int channel, int region, DistanceResult result) {
    final StringBuilder sb = new StringBuilder();
    sb.append(resultEntry);
    sb.append(frame).append('\t');
    sb.append(channel).append('\t');
    sb.append(region).append('\t');
    if (settings.pixelDistances) {
      sb.append(result.getPixelXyz()).append('\t');
    }
    if (settings.calibratedDistances) {
      sb.append(result.getCalXyz()).append('\t');
    }
    sb.append(MathUtils.rounded(result.circularity));
    resultsWindow.append(sb.toString());
    allowUndo.set(true);
  }

  private void addSummaryResult(int frame, String channel, int region, int size) {
    final StringBuilder sb = new StringBuilder();
    sb.append(resultEntry);
    sb.append(frame).append('\t');
    sb.append(channel).append('\t');
    sb.append(region).append('\t').append(size);
    if (settings.pixelDistances) {
      sb.append("\t0\t0\t0");
    }
    if (settings.calibratedDistances) {
      sb.append("\t0\t0\t0");
    }
    summaryWindow.append(sb.toString());
    allowUndo.set(true);
  }

  private void addSummaryResult(int frame, String channel, int region, int size, double minD,
      double maxD, double averageD, double minD2, double maxD2, double averageD2) {
    final StringBuilder sb = new StringBuilder();
    sb.append(resultEntry);
    sb.append(frame).append('\t');
    sb.append(channel).append('\t');
    sb.append(region).append('\t').append(size);
    if (settings.pixelDistances) {
      sb.append('\t').append(MathUtils.rounded(minD));
      sb.append('\t').append(MathUtils.rounded(maxD));
      sb.append('\t').append(MathUtils.rounded(averageD));
    }
    if (settings.calibratedDistances) {
      sb.append('\t').append(MathUtils.rounded(minD2));
      sb.append('\t').append(MathUtils.rounded(maxD2));
      sb.append('\t').append(MathUtils.rounded(averageD2));
    }
    summaryWindow.append(sb.toString());
    allowUndo.set(true);
  }

  private void addDistanceResult(int frame, String channel, int region, DistanceResult r1,
      DistanceResult r2, double distance, double calibratedDistance) {
    final StringBuilder sb = new StringBuilder();
    sb.append(resultEntry);
    sb.append(frame).append('\t');
    sb.append(channel).append('\t');
    sb.append(region);
    if (settings.pixelDistances) {
      sb.append('\t').append(r1.getPixelXyz());
      sb.append('\t').append(r2.getPixelXyz());
      sb.append('\t').append(MathUtils.rounded(distance));
    }
    if (settings.calibratedDistances) {
      sb.append('\t').append(r1.getCalXyz());
      sb.append('\t').append(r2.getCalXyz());
      sb.append('\t').append(MathUtils.rounded(calibratedDistance));
    }
    distancesWindow.append(sb.toString());
    allowUndo.set(true);
  }

  /**
   * Install the Spot Distance tool.
   */
  static void installTool() {
    final StringBuilder sb = new StringBuilder();
    sb.append("macro 'Spot Distance Action Tool - C00fo4233o6922oa644Cf00O00ff' {\n");
    sb.append("   call('").append(SpotDistance_PlugIn.class.getName()).append(".run');\n");
    sb.append("};\n");
    sb.append("macro 'Spot Distance Action Tool Options' {\n");
    sb.append("   call('").append(SpotDistance_PlugIn.class.getName()).append(".setOptions');\n");
    sb.append("};\n");
    new MacroInstaller().install(sb.toString());
  }

  /**
   * Convert the binary mask to labelled regions, each with a unique number.
   */
  private void labelRegions(ShortProcessor ip) {
    maxx = ip.getWidth();

    xlimit = maxx - 1;
    ylimit = ip.getHeight() - 1;

    // Create the offset table (for single array 2D neighbour comparisons)
    offset = new int[DIR_X_OFFSET.length];
    for (int d = offset.length; d-- > 0;) {
      offset[d] = maxx * DIR_Y_OFFSET[d] + DIR_X_OFFSET[d];
    }

    final short[] mask = (short[]) ip.getPixels();
    final int[] pointList = new int[mask.length];

    // Store all the non-zero positions
    final boolean[] binaryMask = new boolean[mask.length];
    for (int i = 0; i < binaryMask.length; i++) {
      binaryMask[i] = (mask[i] != 0);
    }

    // Find particles
    for (int i = 0; i < binaryMask.length; i++) {
      if (binaryMask[i]) {
        expandParticle(binaryMask, mask, pointList, i, (short) settings.regionCounter);
        settings.regionCounter++;
      }
    }

    // Free memory
    offset = null;
  }

  /**
   * Searches from the specified point to find all connected points and assigns them to given
   * particle.
   */
  private void expandParticle(boolean[] binaryMask, short[] mask, int[] pointList, int index0,
      final short particle) {
    binaryMask[index0] = false; // mark as processed
    int listI = 0; // index of current search element in the list
    int listLen = 1; // number of elements in the list

    // we create a list of connected points and start the list at the particle start position
    pointList[listI] = index0;

    do {
      final int index1 = pointList[listI];
      // Mark this position as part of the particle
      mask[index1] = particle;

      // Search the 8-connected neighbours
      final int x1 = index1 % maxx;
      final int y1 = index1 / maxx;

      final boolean isInnerXy = (x1 != 0 && x1 != xlimit) && (y1 != 0 && y1 != ylimit);

      if (isInnerXy) {
        for (int d = 8; d-- > 0;) {
          final int index2 = index1 + offset[d];
          if (binaryMask[index2]) {
            binaryMask[index2] = false; // mark as processed
            // Add this to the search
            pointList[listLen++] = index2;
          }
        }
      } else {
        for (int d = 8; d-- > 0;) {
          if (isInside(x1, y1, d)) {
            final int index2 = index1 + offset[d];
            if (binaryMask[index2]) {
              binaryMask[index2] = false; // mark as processed
              // Add this to the search
              pointList[listLen++] = index2;
            }
          }
        }
      }

      listI++;

    } while (listI < listLen);
  }

  /**
   * Returns whether the neighbour in a given direction is within the image. NOTE: it is assumed
   * that the pixel x,y itself is within the image! Uses class variables xlimit, ylimit: (dimensions
   * of the image)-1
   *
   * @param x x-coordinate of the pixel that has a neighbour in the given direction
   * @param y y-coordinate of the pixel that has a neighbour in the given direction
   * @param direction the direction from the pixel towards the neighbour
   * @return true if the neighbour is within the image (provided that x, y is within)
   */
  private boolean isInside(int x, int y, int direction) {
    switch (direction) {
      case 0:
        return (y > 0);
      case 1:
        return (y > 0 && x < xlimit);
      case 2:
        return (x < xlimit);
      case 3:
        return (y < ylimit && x < xlimit);
      case 4:
        return (y < ylimit);
      case 5:
        return (y < ylimit && x > 0);
      case 6:
        return (x > 0);
      case 7:
        return (y > 0 && x > 0);
      default:
        return false;
    }
  }
}
