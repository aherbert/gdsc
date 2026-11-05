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
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.text.TextPanel;
import ij.text.TextWindow;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.match.MatchCalculator;
import uk.ac.sussex.gdsc.core.match.PointPair;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.core.utils.concurrent.ConcurrencyUtils;
import uk.ac.sussex.gdsc.ij.UsageTracker;
import uk.ac.sussex.gdsc.ij.ext.plugin.filter.DifferenceOfGaussians;
import uk.ac.sussex.gdsc.ij.foci.FindFociOptions.OutputOption;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.AlgorithmOption;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.BackgroundMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.CentreMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.MaskMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.PeakMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.SearchMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.SortMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.ThresholdMethod;
import uk.ac.sussex.gdsc.ij.help.HelpUrls;

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
  /** The maximum region number for a region defined using an area ROI. */
  private static final int MAX_REGION = 65535;

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
  private static ImagePlus debugMaskImp2;

  /**
   * The regions of a mask.
   */
  static class RegionMask {
    /** The mask. */
    final ImageStack stack;
    /** The region values in ascending order. */
    final int[] regions;
    /** The bounds of all the regions (null if there are no regions). */
    final Rectangle blurBounds;
    /** The region image if the mask has a single region. */
    private ImagePlus singleRegionImp;

    /**
     * Create an instance.
     *
     * @param stack the mask
     */
    RegionMask(ImageStack stack) {
      this.stack = stack;
      regions = findRegions(stack);
      if (regions.length == 1) {
        // Cache the cropped mask if it has only one region
        final Rectangle bounds = new Rectangle();
        singleRegionImp = crop(extractRegion(stack, regions[0], bounds), bounds);
        blurBounds = bounds;
      } else if (regions.length > 1) {
        blurBounds = findBounds(stack);
      } else {
        blurBounds = null;
      }
    }

    /**
     * Gets the region image cropped to the bounds of the region.
     *
     * @param region the region
     * @param bounds the bounds of the region (output)
     * @return the region image (null if the region is not in the mask)
     */
    ImagePlus getRegion(int region, Rectangle bounds) {
      if (Arrays.binarySearch(regions, region) < 0) {
        return null;
      }
      if (singleRegionImp != null) {
        bounds.setBounds(blurBounds);
        return singleRegionImp;
      }
      return crop(extractRegion(stack, region, bounds), bounds);
    }
  }

  private ImagePlus imp;
  private ImagePlus maskImp;
  private ImagePlus projectionImp;
  private String resultEntry;
  private Calibration cal;
  /** The size of a pixel in x, y and z in the x calibration unit (null if not available). */
  private double[] calScale;
  /** The size of a pixel in x, y and z relative to the pixel width. */
  private double[] filterScale;
  private double maxDistance2;

  // The mask regions for each channel. The second channel can use the same mask.
  private RegionMask regionMask;
  private RegionMask regionMask2;

  // Store the last frame results for each region to allow primitive tracking
  private Int2ObjectOpenHashMap<Tracker> trackers;
  private Int2ObjectOpenHashMap<Tracker> trackers2;

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
  static class Settings {
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
    int maxPeaks = 33000;

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

    /**
     * Save the region counter to the last settings. This allows the counter to be updated when
     * the settings are not saved (e.g. when repeating the analysis).
     *
     * @param regionCounter the region counter
     */
    static void saveRegionCounter(int regionCounter) {
      lastSettings.get().regionCounter = regionCounter;
    }
  }

  /**
   * Used to store results and cache XYZ output formatting.
   */
  static class DistanceResult extends BasePoint {
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

    String getCalXyz(Calibration cal) {
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

  /**
   * Track the spots in a region between frames using closest distance matching.
   */
  static class Tracker {
    /** The results from the previous frame. */
    private List<DistanceResult> previous = Collections.emptyList();
    /** The maximum ID assigned. */
    private int maxId;

    /**
     * Assign an ID to each result. Results are matched to the closest result from the previous
     * frame within the distance and use the same ID. Results that are not matched are assigned a
     * new ID. The results are sorted by ID.
     *
     * @param results the results
     * @param distance the maximum distance for a match
     */
    void assignIds(List<DistanceResult> results, double distance) {
      if (!previous.isEmpty() && !results.isEmpty()) {
        final List<PointPair> matches = new ArrayList<>();
        MatchCalculator.analyseResults3D(previous.toArray(new DistanceResult[0]),
            results.toArray(new DistanceResult[0]), distance, null, null, null, matches);
        for (final PointPair match : matches) {
          ((DistanceResult) match.getPoint2()).id = ((DistanceResult) match.getPoint1()).id;
        }
      }
      // Assign those that could not be tracked
      for (final DistanceResult r : results) {
        if (r.id == 0) {
          r.id = ++maxId;
        }
      }
      results.sort((o1, o2) -> Integer.compare(o1.id, o2.id));
      previous = results;
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
        gd.addNumericField("Max_peaks", settings.maxPeaks, 0);
      }
      gd.addNumericField("Circularity_limit", settings.circularityLimit, 2);
      gd.addNumericField("Max_distance", settings.maxDistance, 2, 6, "pixels");
      if (imp.getNFrames() != 1) {
        gd.addCheckbox("Process_frames", settings.processFrames);
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
      gd.addHelp(HelpUrls.getUrl("spot-distance"));

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
        settings.minPeakHeight = gd.getNextNumber();
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

      final String error = validateSettings(settings);
      if (error != null) {
        IJ.error(TITLE, error);
        return;
      }
    }

    calScale = getCalibratedScale(imp.getCalibration());
    if (calScale == null && settings.calibratedDistances) {
      final Calibration c = imp.getCalibration();
      IJ.error(TITLE, "Calibrated distances require x, y and z units that can be converted to\n"
          + "the same unit: " + c.getXUnit() + ", " + c.getYUnit() + ", " + c.getZUnit());
      return;
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

      if (settings.c1 == settings.c2) {
        IJ.error(TITLE, "Dual channel analysis requires two different channels");
        return;
      }
      doDualChannel = true;
    } else {
      // The dual channel option is only shown for a multi-channel image
      doDualChannel = settings.dualChannel && imp.getNChannels() > 1;
      if (doDualChannel && !isValidDualChannel(settings.c1, settings.c2, imp.getNChannels())) {
        IJ.error(TITLE, "Invalid channels for dual channel analysis: " + settings.c1 + " & "
            + settings.c2);
        return;
      }
    }

    initialise();

    final int channel = (doDualChannel) ? settings.c1 : imp.getChannel();
    final ImageStack imageStack = imp.getImageStack();

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

      // Cache the mask regions. Only recreate the mask if using multiple frames.
      if (regionMask == null || maskImp.getNFrames() > 1) {
        regionMask = new RegionMask(getMaskStack(maskImp, channel, frame));
        // The second channel uses the matching mask channel if available
        regionMask2 = (doDualChannel && maskImp.getNChannels() > 1)
            ? new RegionMask(getMaskStack(maskImp, settings.c2, frame))
            : regionMask;
      }

      if (ImageJUtils.isInterrupted()) {
        return;
      }

      exec(frame, channel, s1, s1b);
      IJ.showProgress(i + 1, frames.length);

      if (ImageJUtils.isInterrupted()) {
        return;
      }
    }
    resultsWindow.append("");

    if (settings.showProjection) {
      projectionImp.setSlice(1);
    }
  }

  /**
   * Validate the settings.
   *
   * @param settings the settings
   * @return the error message (or null)
   */
  static String validateSettings(Settings settings) {
    if (settings.smoothingSize < 0) {
      return "Smoothing must be positive";
    }
    if (settings.featureSize < 0) {
      return "Feature size must be positive";
    }
    if (settings.featureSize > 0 && settings.featureSize <= settings.smoothingSize) {
      return "Feature size must be larger than the smoothing";
    }
    if (settings.maxPeaks < 1) {
      return "Max peaks must be strictly positive";
    }
    if (settings.regionCounter < 1 || settings.regionCounter > MAX_REGION) {
      return "Region counter must be in the range 1-" + MAX_REGION;
    }
    return null;
  }

  /**
   * Checks if the channels are valid for dual channel analysis.
   *
   * @param c1 the first channel
   * @param c2 the second channel
   * @param channels the number of channels
   * @return true if valid
   */
  static boolean isValidDualChannel(int c1, int c2, int channels) {
    return c1 != c2 && c1 >= 1 && c2 >= 1 && c1 <= channels && c2 <= channels;
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

  /**
   * Removes the lines from the window after the specified line. Use a negative line to ignore the
   * window.
   *
   * @param windowRef the window reference
   * @param line the line
   */
  private static void removeAfterLine(AtomicReference<TextWindow> windowRef, int line) {
    if (line >= 0) {
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

  /**
   * Gets the size of a pixel in x, y and z in the x calibration unit. The y and z pixel sizes are
   * converted to the x unit if the units are different.
   *
   * @param cal the calibration
   * @return the scale (null if the units cannot be converted)
   */
  static double[] getCalibratedScale(Calibration cal) {
    final String unit = cal.getXUnit();
    final double fy = getUnitConversion(cal.getYUnit(), unit);
    final double fz = getUnitConversion(cal.getZUnit(), unit);
    if (Double.isNaN(fy) || Double.isNaN(fz)) {
      return null;
    }
    return new double[] {cal.pixelWidth, cal.pixelHeight * fy, cal.pixelDepth * fz};
  }

  /**
   * Gets the size of a pixel in x, y and z relative to the pixel width. If the calibrated scale
   * is not available then all the sizes are 1 (pixel and z-slice index).
   *
   * @param calScale the calibrated scale (can be null)
   * @return the scale
   */
  static double[] getFilterScale(double[] calScale) {
    if (calScale == null) {
      return new double[] {1, 1, 1};
    }
    return new double[] {1, calScale[1] / calScale[0], calScale[2] / calScale[0]};
  }

  /**
   * Gets the conversion factor to convert a length in the unit to the target unit.
   *
   * @param unit the unit
   * @param target the target unit
   * @return the conversion factor (NaN if the units cannot be converted)
   */
  static double getUnitConversion(String unit, String target) {
    if (unit.equalsIgnoreCase(target)) {
      return 1;
    }
    return getUnitLength(unit) / getUnitLength(target);
  }

  /**
   * Gets the length of the unit in metres.
   *
   * @param unit the unit
   * @return the length (NaN if unknown)
   */
  private static double getUnitLength(String unit) {
    switch (unit.trim().toLowerCase(Locale.ROOT)) {
      case "pm":
        return 1e-12;
      case "\u00e5":
      case "angstrom":
      case "angstroms":
        return 1e-10;
      case "nm":
      case "nanometer":
      case "nanometers":
      case "nanometre":
      case "nanometres":
        return 1e-9;
      case "um":
      case "\u00b5m":
      case "\u03bcm":
      case "micron":
      case "microns":
      case "micrometer":
      case "micrometers":
      case "micrometre":
      case "micrometres":
        return 1e-6;
      case "mm":
      case "millimeter":
      case "millimeters":
      case "millimetre":
      case "millimetres":
        return 1e-3;
      case "cm":
      case "centimeter":
      case "centimeters":
      case "centimetre":
      case "centimetres":
        return 1e-2;
      case "m":
      case "meter":
      case "meters":
      case "metre":
      case "metres":
        return 1;
      case "km":
        return 1e3;
      case "in":
      case "inch":
      case "inches":
        return 0.0254;
      default:
        return Double.NaN;
    }
  }

  /**
   * Compute the squared distance using the scale for each dimension.
   *
   * @param diff the difference in x, y and z
   * @param scale the scale for x, y and z
   * @return the squared distance
   */
  static double distance2(double[] diff, double[] scale) {
    final double dx = diff[0] * scale[0];
    final double dy = diff[1] * scale[1];
    final double dz = diff[2] * scale[2];
    return dx * dx + dy * dy + dz * dz;
  }

  private void initialise() {
    regionMask = null;
    regionMask2 = null;
    trackers = new Int2ObjectOpenHashMap<>();
    trackers2 = new Int2ObjectOpenHashMap<>();
    allowUndo.set(false);
    maxDistance2 = settings.maxDistance * settings.maxDistance;
    filterScale = getFilterScale(calScale);
    if (calScale == null && maxDistance2 != 0) {
      ImageJUtils.log("%s : Pixel spacing units cannot be converted; the max distance uses the "
          + "pixel and z-slice index", TITLE);
    }
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
    // Hyperstack order is channel then frame
    for (final int f : frames) {
      stack.addSlice("Frame " + f, ip.createProcessor(ip.getWidth(), ip.getHeight()));
      if (dualChannel) {
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
      if (maskImp != null && maskImp.getID() != imp.getID() && isValidMask(imp, maskImp)
          && (maskImp.getNSlices() == imp.getNSlices() || maskImp.getNSlices() == 1)
          && (maskImp.getNChannels() == imp.getNChannels() || maskImp.getNChannels() == 1)
          && (maskImp.getNFrames() == imp.getNFrames() || maskImp.getNFrames() == 1)) {
        newImageList.add(maskImp.getTitle());
      }
    }

    return newImageList.toArray(new String[0]);
  }

  /**
   * Checks if the mask is valid for the image. The mask must be an 8-bit or 16-bit image with the
   * same XY dimensions as the image.
   *
   * @param imp the image
   * @param mask the mask
   * @return true if valid
   */
  static boolean isValidMask(ImagePlus imp, ImagePlus mask) {
    return (mask.getBitDepth() == 8 || mask.getBitDepth() == 16)
        && mask.getWidth() == imp.getWidth() && mask.getHeight() == imp.getHeight();
  }

  private ImagePlus checkMask(ImagePlus imp, String maskImage) {
    ImagePlus mask = WindowManager.getImage(maskImage);

    // The mask is selected from a list of valid images. Check again in case the
    // image has changed or the analysis is repeated on a different image.
    if (mask != null && (mask.getID() == imp.getID() || !isValidMask(imp, mask))) {
      IJ.error(TITLE, "Mask image must be an 8-bit or 16-bit image with the same width and\n"
          + "height as the input image: " + maskImage);
      return null;
    }

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
      final int counter = labelRegions(ip, settings.regionCounter);
      if (counter < 0) {
        IJ.error(TITLE, "Region counter exceeds the maximum of " + MAX_REGION);
        return null;
      }
      settings.regionCounter = counter;
      Settings.saveRegionCounter(counter);

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
        (distancesWindow != null) ? distancesWindow.getTextPanel().getLineCount() : -1);

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
        // Window must be showing and have the same header
        window -> ImageJUtils.isShowing(window) && sameHeader(window, header),
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
    if (settings.trackObjects) {
      sb.append("ID\t");
    }
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

  private void exec(int frame, int channel, ImageStack s1, ImageStack s1b) {
    // Process all the regions in either mask
    final int[] regions =
        (s1b == null) ? regionMask.regions : union(regionMask.regions, regionMask2.regions);
    if (regions.length == 0) {
      return; // Q. Should anything be reported?
    }

    if (settings.showProjection) {
      // Do Z-projection into the current image processor of the stack
      runZProjection(frame, 1, s1);
      if (s1b != null) {
        runZProjection(frame, 2, s1b);
      }
    }

    // Filter to enhance the spot features. Filtering is limited to the bounds of the mask regions.
    // The image is not used if the mask has no regions.
    if (regionMask.regions.length != 0) {
      s1 = filterImage(s1, regionMask.blurBounds);
    }
    if (s1b != null && regionMask2.regions.length != 0) {
      s1b = filterImage(s1b, regionMask2.blurBounds);
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
    processorOptions.setMaxPeaks(settings.maxPeaks);
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
      final ArrayList<DistanceResult> resultsArray = findSpots(regionMask, region, spotImp, ff,
          processorOptions, getTracker(trackers, region), frame, channel, overlay, false);
      if (resultsArray == null) {
        return;
      }
      for (final DistanceResult result : resultsArray) {
        addResult(frame, channel, region, result);
      }

      if (s1b == null) {
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
            if (maxDistance2 != 0 && distance2(diff, filterScale) > maxDistance2) {
              continue;
            }

            count++;

            double distance = 0;
            double calibratedDistance = 0;
            if (settings.pixelDistances) {
              distance = Math.sqrt(diff[0] * diff[0] + diff[1] * diff[1] + diff[2] * diff[2]);
              if (distance < minD) {
                minD = distance;
              }
              if (distance > maxD) {
                maxD = distance;
              }
              sumD += distance;
            }

            if (settings.calibratedDistances) {
              // Distance in the x calibration unit
              calibratedDistance = Math.sqrt(distance2(diff, calScale));
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

        if (count == 0) {
          // No pairs within the maximum distance
          addSummaryResult(frame, ch, region, resultsArray.size());
        } else {
          addSummaryResult(frame, ch, region, resultsArray.size(), minD, maxD, sumD / count, minD2,
              maxD2, sumD2 / count);
        }
      } else {
        // Dual channel analysis.
        // Analyse the second channel
        final ArrayList<DistanceResult> resultsArray2 = findSpots(regionMask2, region, spotImp2,
            ff, processorOptions, getTracker(trackers2, region), frame, settings.c2, overlay2,
            true);
        if (resultsArray2 == null) {
          return;
        }
        for (final DistanceResult result : resultsArray2) {
          addResult(frame, settings.c2, region, result);
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
            if (maxDistance2 != 0 && distance2(diff, filterScale) > maxDistance2) {
              continue;
            }

            count++;

            double distance = 0;
            double calibratedDistance = 0;
            if (settings.pixelDistances) {
              distance = Math.sqrt(diff[0] * diff[0] + diff[1] * diff[1] + diff[2] * diff[2]);
              if (distance < minD) {
                minD = distance;
              }
              if (distance > maxD) {
                maxD = distance;
              }
              sumD += distance;
            }

            if (settings.calibratedDistances) {
              // Distance in the x calibration unit
              calibratedDistance = Math.sqrt(distance2(diff, calScale));
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

        if (count == 0) {
          // No pairs within the maximum distance
          addSummaryResult(frame, ch, region, resultsArray.size() + resultsArray2.size());
        } else {
          addSummaryResult(frame, ch, region, resultsArray.size() + resultsArray2.size(), minD,
              maxD, sumD / count, minD2, maxD2, sumD2 / count);
        }
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
  }

  /**
   * Gets the mask stack for the channel and frame. The mask channel and frame are only used if the
   * mask has more than one channel or frame.
   *
   * @param maskImp the mask image
   * @param channel the channel
   * @param frame the frame
   * @return the mask stack
   */
  static ImageStack getMaskStack(ImagePlus maskImp, int channel, int frame) {
    final ImageStack maskStack = maskImp.getImageStack();
    final ImageStack s2 =
        new ImageStack(maskImp.getWidth(), maskImp.getHeight(), maskImp.getNSlices());
    final int maskChannel = (maskImp.getNChannels() > 1) ? channel : 1;
    final int maskFrame = (maskImp.getNFrames() > 1) ? frame : 1;
    for (int slice = 1; slice <= maskImp.getNSlices(); slice++) {
      final int maskStackIndex = maskImp.getStackIndex(maskChannel, slice, maskFrame);
      s2.setPixels(maskStack.getPixels(maskStackIndex), slice);
    }
    return s2;
  }

  /**
   * Get the union of the two sorted arrays.
   *
   * @param a the first array
   * @param b the second array
   * @return the sorted union
   */
  static int[] union(int[] a, int[] b) {
    return IntStream.concat(Arrays.stream(a), Arrays.stream(b)).distinct().sorted().toArray();
  }

  /**
   * Filter the image using a Difference of Gaussians if two radii are provided, otherwise a
   * Gaussian blur if the smoothing is provided.
   *
   * @param s1 the image
   * @param blurBounds the bounds to filter
   * @return the filtered image
   */
  private ImageStack filterImage(ImageStack s1, Rectangle blurBounds) {
    if (settings.featureSize > 0) {
      return runDifferenceOfGaussians(s1, blurBounds);
    }
    if (settings.smoothingSize > 0) {
      return runGaussianBlur(s1, blurBounds);
    }
    return s1;
  }

  /**
   * Find the spots in the region of the mask.
   *
   * @param mask the mask
   * @param region the region
   * @param spotImp the spot image
   * @param ff the FindFoci plugin
   * @param processorOptions the processor options
   * @param tracker the tracker (can be null)
   * @param frame the frame
   * @param channel the channel
   * @param overlay the overlay (can be null)
   * @param secondChannel true if this is the second channel (used for debug images)
   * @return the results (null if interrupted)
   */
  private ArrayList<DistanceResult> findSpots(RegionMask mask, int region, ImagePlus spotImp,
      FindFoci_PlugIn ff, FindFociProcessorOptions processorOptions, Tracker tracker, int frame,
      int channel, Overlay overlay, boolean secondChannel) {
    final Rectangle bounds = new Rectangle();
    final ImagePlus regionImp = mask.getRegion(region, bounds);
    if (regionImp == null) {
      // The region is not in the mask for this channel
      return new ArrayList<>(0);
    }
    final ImagePlus croppedImp = crop(spotImp, bounds);
    final ImageStack stack = croppedImp.getImageStack();
    final float scale = scaleImage(stack, settings.debug);
    croppedImp.setStack(stack); // Updates the image bit depth
    processorOptions.setPeakParameter(settings.minPeakHeight * scale);

    if (settings.debug) {
      showSpotImage(croppedImp, regionImp, secondChannel, cal);
    }

    final FindFociBaseProcessor ffp = ff.createFindFociProcessor(croppedImp);
    final FindFociResults ffResult = ffp.findMaxima(croppedImp, regionImp, processorOptions);

    if (ImageJUtils.isInterrupted()) {
      return null;
    }

    return analyseResults(tracker, croppedImp, ffp, ffResult, frame, channel, overlay, bounds);
  }

  private void runZProjection(int frame, int channel, ImageStack s1) {
    final int index = projectionImp.getStackIndex(channel, 1, frame);
    projectionImp.setSliceWithoutUpdate(index);
    final ImageProcessor max = projectionImp.getProcessor();
    max.insert(s1.getProcessor(1), 0, 0);
    for (int n = 2; n <= s1.getSize(); n++) {
      final ImageProcessor ip = s1.getProcessor(n);
      for (int i = 0; i < ip.getPixelCount(); i++) {
        if (max.getf(i) < ip.getf(i)) {
          max.setf(i, ip.getf(i));
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
      if (projectionImp.getNChannels() > 1) {
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
   * @param debug set to true to log the scaling
   * @return The scale
   */
  static float scaleImage(ImageStack s1, boolean debug) {
    if (!(s1.getPixels(1) instanceof float[])) {
      return 1;
    }

    // Find the range
    float min = Float.POSITIVE_INFINITY;
    float max = Float.NEGATIVE_INFINITY;
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
    final float scale = (range > 0) ? 65535 / range : 1;
    if (debug) {
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

  private static synchronized void showSpotImage(ImagePlus croppedImp, ImagePlus regionImp,
      boolean secondChannel, Calibration cal) {
    if (secondChannel) {
      debugSpotImp2 = createImage(croppedImp, debugSpotImp2, TITLE + " Pixels B", 0, cal);
      debugMaskImp2 = createImage(regionImp, debugMaskImp2, TITLE + " Region B", 1, cal);
    } else {
      debugSpotImp = createImage(croppedImp, debugSpotImp, TITLE + " Pixels", 0, cal);
      debugMaskImp = createImage(regionImp, debugMaskImp, TITLE + " Region", 1, cal);
    }
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

  private static int[] findRegions(ImageStack s2) {
    // Build a histogram. Any non-zero value defines a region.
    final int[] histogram = s2.getProcessor(1).getHistogram();
    for (int slice = 2; slice <= s2.getSize(); slice++) {
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
   * Gets the tracker for the region if tracking objects.
   *
   * @param trackers the trackers
   * @param region the region
   * @return the tracker (or null)
   */
  private Tracker getTracker(Int2ObjectOpenHashMap<Tracker> trackers, int region) {
    return settings.trackObjects ? trackers.computeIfAbsent(region, k -> new Tracker()) : null;
  }

  /**
   * Crop all the slices of the image to the bounds.
   *
   * <p>Note: ImagePlus.duplicate() ignores the ROI so cannot be used to crop.
   *
   * @param imp the image
   * @param bounds the bounds
   * @return the cropped image
   */
  static ImagePlus crop(ImagePlus imp, Rectangle bounds) {
    final ImageStack stack = imp.getImageStack();
    return new ImagePlus(null, stack.crop(bounds.x, bounds.y, 0, bounds.width, bounds.height,
        stack.getSize()));
  }

  /**
   * Check the peak circularity. Add an overlay of the spots if requested.
   *
   * @param tracker the tracker (can be null)
   * @param croppedImp the cropped imp
   * @param ffp the FindFociProcessor that generated the results
   * @param ffResult the ff result
   * @param frame the frame
   * @param channel the channel
   * @param overlay the overlay
   * @param bounds the bounds of the cropped image
   * @return the results
   */
  private ArrayList<DistanceResult> analyseResults(Tracker tracker,
      ImagePlus croppedImp, FindFociBaseProcessor ffp, FindFociResults ffResult, int frame,
      int channel, Overlay overlay, Rectangle bounds) {
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
      final Roi roi = extractSelection(maskIp, maskId, channel, z + 1, frame, bounds);
      // The peak may have no pixels on the slice of the centre
      final double perimeter = (roi == null) ? 0 : roi.getLength();
      final double area = getArea(maskIp, maskId);

      final double circularity =
          perimeter == 0.0 ? 0.0 : 4.0 * Math.PI * (area / (perimeter * perimeter));

      if (circularity < settings.circularityLimit) {
        IJ.log(String.format("Excluded non-circular peak @ x%d,y%d,z%d : %g (4pi * %g / %g^2)", x,
            y, z, circularity, area, perimeter));
        continue;
      }

      if (overlay != null && roi != null) {
        overlay.add(roi);
      }

      newResultsArray.add(new DistanceResult(x, y, z, circularity));
    }

    if (tracker != null) {
      // Assign an ID to each result using closest distance tracking to the previous frame.
      final double d = Math.sqrt((double) croppedImp.getWidth() * croppedImp.getWidth()
          + croppedImp.getHeight() * croppedImp.getHeight()
          + croppedImp.getNSlices() * croppedImp.getNSlices());
      tracker.assignIds(newResultsArray, d);
    }

    return newResultsArray;
  }

  private Roi extractSelection(ImageProcessor maskIp, int maskId, int channel, int slice,
      int frame, Rectangle bounds) {
    maskIp.setThreshold(maskId, maskId, ImageProcessor.NO_LUT_UPDATE);
    final ThresholdToSelection ts = new ThresholdToSelection();
    final Roi roi = ts.convert(maskIp);
    if (roi == null) {
      return null;
    }
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
    if (settings.trackObjects) {
      sb.append(result.id).append('\t');
    }
    if (settings.pixelDistances) {
      sb.append(result.getPixelXyz()).append('\t');
    }
    if (settings.calibratedDistances) {
      sb.append(result.getCalXyz(cal)).append('\t');
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
      sb.append('\t').append(r1.getCalXyz(cal));
      sb.append('\t').append(r2.getCalXyz(cal));
      sb.append('\t').append(MathUtils.rounded(calibratedDistance));
    }
    distancesWindow.append(sb.toString());
    allowUndo.set(true);
  }

  /**
   * Convert the binary mask to labelled regions, each with a unique number starting from the
   * region counter.
   *
   * @param ip the mask
   * @param regionCounter the region counter
   * @return the next region number; or -1 if the region number exceeds the maximum
   */
  int labelRegions(ShortProcessor ip, int regionCounter) {
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
    int counter = regionCounter;
    for (int i = 0; i < binaryMask.length; i++) {
      if (binaryMask[i]) {
        if (counter > MAX_REGION) {
          counter = -1;
          break;
        }
        expandParticle(binaryMask, mask, pointList, i, (short) counter);
        counter++;
      }
    }

    // Free memory
    offset = null;
    return counter;
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
