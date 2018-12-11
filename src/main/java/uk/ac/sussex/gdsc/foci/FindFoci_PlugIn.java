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
import uk.ac.sussex.gdsc.core.ij.ImageJLogHandler;
import uk.ac.sussex.gdsc.core.ij.ImageJTrackProgress;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.ij.gui.ExtendedGenericDialog;
import uk.ac.sussex.gdsc.core.logging.LoggerUtils;
import uk.ac.sussex.gdsc.core.logging.Ticker;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.core.utils.TextUtils;
import uk.ac.sussex.gdsc.core.utils.TurboList;
import uk.ac.sussex.gdsc.core.utils.concurrent.ConcurrencyUtils;
import uk.ac.sussex.gdsc.foci.FindFociBaseProcessor.ObjectAnalysisResult;
import uk.ac.sussex.gdsc.foci.model.FindFociModel;
import uk.ac.sussex.gdsc.ij.gui.LabelledPointRoi;
import uk.ac.sussex.gdsc.utils.GaussianFit_PlugIn;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Macro;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.ImageRoi;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.io.Opener;
import ij.io.RoiEncoder;
import ij.plugin.FolderOpener;
import ij.plugin.PlugIn;
import ij.plugin.frame.Recorder;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

import java.awt.Color;
import java.awt.TextField;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.MemoryHandler;

/**
 * Find the peak intensity regions of an image.
 *
 * <p>All local maxima above threshold are identified. For all other pixels the direction to the
 * highest neighbour pixel is stored (steepest gradient). In order of highest local maxima, regions
 * are only grown down the steepest gradient to a lower pixel. Provides many configuration options
 * for regions growing thresholds.
 *
 * <p>This plugin was based on {@link ij.plugin.filter.MaximumFinder}. Options have been changed to
 * only support greyscale 2D images and 3D stacks and to perform region growing using configurable
 * thresholds. Support for Watershed, Previewing, and Euclidian Distance Map (EDM) have been
 * removed.
 *
 * <p>Stopping criteria for region growing routines are partly based on the options in PRIISM
 * (http://www.msg.ucsf.edu/IVE/index.html).
 */
public class FindFoci_PlugIn implements PlugIn, FindFociProcessor {

  /** Get the title of the ImageJ plugin. */
  public static final String TITLE = "FindFoci";

  private static TextWindow resultsWindow = null;

  // Used to buffer the results to the TextWindow
  private final StringBuilder resultsBuffer = new StringBuilder();
  private int resultsCount = 0;
  private int flushCount = 0;

  private static List<FindFociResult> lastResultsArray = null;

  /**
   * Set to true if the Gaussian fit option is enabled. This requires the GDSC SMLM library to be
   * available.
   */
  static int isGaussianFitEnabled = 0;

  /** The new line string from System.getProperty("line.separator"). */
  private static final String NEW_LINE = System.getProperty("line.separator");

  private static final String BATCH_INPUT_DIRECTORY = "findfoci.batchInputDirectory";
  private static final String BATCH_MASK_DIRECTORY = "findfoci.batchMaskDirectory";
  private static final String BATCH_PARAMETER_FILE = "findfoci.batchParameterFile";
  private static final String BATCH_OUTPUT_DIRECTORY = "findfoci.batchOutputDirectory";
  private static final String BATCH_MULTI_THREAD = "findfoci.batchMultiThread";
  private static final String SEARCH_CAPACITY = "findfoci.searchCapacity";
  private static final String EMPTY_FIELD = "findfoci.emptyField";

  private static String batchInputDirectory = Prefs.get(BATCH_INPUT_DIRECTORY, "");
  private static String batchMaskDirectory = Prefs.get(BATCH_MASK_DIRECTORY, "");
  private static String batchParameterFile = Prefs.get(BATCH_PARAMETER_FILE, "");
  private static String batchOutputDirectory = Prefs.get(BATCH_OUTPUT_DIRECTORY, "");
  private static boolean batchMultiThread = Prefs.get(BATCH_MULTI_THREAD, true);

  /**
   * The search capacity. This is the maximum number of potential maxima for the algorithm. The
   * default value for legacy reasons is {@value Short#MAX_VALUE}.
   */
  static int searchCapacity = (int) Prefs.get(SEARCH_CAPACITY, Short.MAX_VALUE);
  private static String emptyField = Prefs.get(EMPTY_FIELD, "");
  private TextField textParamFile;

  //@formatter:off

  /**
   * List of background threshold methods for the dialog.
   */
  private static final String[] backgroundMethods = {
      "Absolute",
      "Mean",
      "Std.Dev above mean",
      "Auto threshold",
      "Min Mask/ROI", "None" };

  /**
   * List of search methods for the dialog.
   */
  private static final String[] searchMethods = {
      "Above background",
      "Fraction of peak - background",
      "Half peak value" };

  /**
   * List of peak height methods for the dialog.
   */
  private static final String[] peakMethods = {
      "Absolute height",
      "Relative height",
      "Relative above background" };

  /**
   * The list of peak height methods for the dialog.
   */
  private static final String[] maskOptions = {
      "(None)",
      "Peaks",
      "Threshold",
      "Peaks above saddle",
      "Threshold above saddle",
      "Fraction of intensity",
      "Fraction height above background" };

  /**
   * The list of recognised methods for sorting the results.
   */
  private static final String[] sortIndexMethods = {
      "Size",
      "Total intensity",
      "Max value",
      "Average intensity",
      "Total intensity minus background",
      "Average intensity minus background",
      "X",
      "Y",
      "Z",
      "Saddle height",
      "Size above saddle",
      "Intensity above saddle",
      "Absolute height",
      "Relative height >Bg",
      "Peak ID",
      "XYZ",
      "Total intensity minus min",
      "Average intensity minus min" };

  private static String[] centreMethods = {
      "Max value (search image)",
      "Max value (original image)",
      "Centre of mass (search image)",
      "Centre of mass (original image)",
      "Gaussian (search image)",
      "Gaussian (original image)" };
  //@formatter:on

  static {
    isGaussianFitEnabled = (GaussianFit_PlugIn.isFittingEnabled()) ? 1 : -1;
    if (isGaussianFitEnabled < 1) {
      centreMethods = Arrays.copyOf(centreMethods, centreMethods.length - 2);

      // Debug the reason why fitting is disabled
      if (IJ.shiftKeyDown()) {
        IJ.log(
            "Gaussian fitting is not enabled:" + NEW_LINE + GaussianFit_PlugIn.getErrorMessage());
      }
    }
  }

  /**
   * Define the peak centre using the highest pixel value of the search image (default). In the case
   * of multiple highest value pixels, the closest pixel to the geometric mean of their coordinates
   * is used.
   */
  public static final int CENTRE_MAX_VALUE_SEARCH = 0;
  /**
   * Re-map peak centre using the highest pixel value of the original image.
   */
  public static final int CENTRE_MAX_VALUE_ORIGINAL = 1;
  /**
   * Re-map peak centre using the peak centre of mass (COM) around the search image. The COM is
   * computed within a given volume of the highest pixel value. Only pixels above the saddle height
   * are used to compute the fit. The volume is specified using 2xN+1 where N is the centre
   * parameter.
   */
  public static final int CENTRE_OF_MASS_SEARCH = 2;
  /**
   * Re-map peak centre using the peak centre of mass (COM) around the original image.
   */
  public static final int CENTRE_OF_MASS_ORIGINAL = 3;
  /**
   * Re-map peak centre using a Gaussian fit on the search image. Only pixels above the saddle
   * height are used to compute the fit. The fit is performed in 2D using a projection along the
   * z-axis. If the centre parameter is 1 a maximum intensity projection is used; else an average
   * intensity project is used. The z-coordinate is computed using the centre of mass along the
   * projection axis located at the xy centre.
   */
  public static final int CENTRE_GAUSSIAN_SEARCH = 4;
  /**
   * Re-map peak centre using a Gaussian fit on the original image.
   */
  public static final int CENTRE_GAUSSIAN_ORIGINAL = 5;

  /**
   * The list of recognised methods for auto-thresholding.
   */
  private static final String[] autoThresholdMethods;

  static {
    final ArrayList<String> m = new ArrayList<>();
    for (final String name : AutoThreshold.getMethods(true)) {
      if (!name.contains("Mean")) {
        m.add(name);
      }
    }
    // Add multi-level Otsu thresholding
    m.add(AutoThreshold.Method.OTSU.toString() + "_3_Level");
    m.add(AutoThreshold.Method.OTSU.toString() + "_4_Level");
    Collections.sort(m);
    autoThresholdMethods = m.toArray(new String[m.size()]);
  }

  /**
   * The list of recognised modes for collecting statistics.
   */
  private static final String[] statisticsModes = {"Both", "Inside", "Outside"};

  private static String myMaskImage;
  private static int myBackgroundMethod;
  private static double myBackgroundParameter;
  private static String myThresholdMethod;
  private static String myStatisticsMode;
  private static int mySearchMethod;
  private static double mySearchParameter;
  private static int myMinSize;
  private static boolean myMinimumAboveSaddle;
  private static boolean myConnectedAboveSaddle;
  private static int myPeakMethod;
  private static double myPeakParameter;
  private static int mySortMethod;
  private static int myMaxPeaks;
  private static int myShowMask;
  private static boolean myOverlayMask;
  private static boolean myShowTable;
  private static boolean myClearTable;
  private static boolean myMarkMaxima;
  private static boolean myMarkRoiMaxima;
  private static boolean myMarkUsingOverlay;
  private static boolean myHideLabels;
  private static boolean myShowMaskMaximaAsDots;
  private static boolean myShowLogMessages;
  private static boolean myRemoveEdgeMaxima;
  private static String myResultsDirectory;
  private static boolean myObjectAnalysis;
  private static boolean myShowObjectMask;
  private static boolean mySaveToMemory;
  private static double myGaussianBlur;
  private static int myCentreMethod;
  private static double myCentreParameter;
  private static double myFractionParameter;

  static {
    // Use the default from the FindFociModel for consistency
    final FindFociModel model = new FindFociModel();
    myMaskImage = model.getMaskImage();
    myBackgroundMethod = model.getBackgroundMethod();
    myBackgroundParameter = model.getBackgroundParameter();
    myThresholdMethod = model.getThresholdMethod();
    myStatisticsMode = model.getStatisticsMode();
    mySearchMethod = model.getSearchMethod();
    mySearchParameter = model.getSearchParameter();
    myMinSize = model.getMinSize();
    myMinimumAboveSaddle = model.isMinimumAboveSaddle();
    myConnectedAboveSaddle = model.isConnectedAboveSaddle();
    myPeakMethod = model.getPeakMethod();
    myPeakParameter = model.getPeakParameter();
    mySortMethod = model.getSortMethod();
    myMaxPeaks = model.getMaxPeaks();
    myShowMask = model.getShowMask();
    myOverlayMask = model.isOverlayMask();
    myShowTable = model.isShowTable();
    myClearTable = model.isClearTable();
    myMarkMaxima = model.isMarkMaxima();
    myMarkRoiMaxima = model.isMarkRoiMaxima();
    myHideLabels = model.isHideLabels();
    myShowMaskMaximaAsDots = model.isShowMaskMaximaAsDots();
    myShowLogMessages = model.isShowLogMessages();
    myRemoveEdgeMaxima = model.isRemoveEdgeMaxima();
    myResultsDirectory = model.getResultsDirectory();
    myObjectAnalysis = model.isObjectAnalysis();
    myShowObjectMask = model.isShowObjectMask();
    mySaveToMemory = model.isSaveToMemory();
    myGaussianBlur = model.getGaussianBlur();
    myCentreMethod = model.getCentreMethod();
    myCentreParameter = model.getCentreParameter();
    myFractionParameter = model.getFractionParameter();
  }

  // Allow the results to be stored in memory for other plugins to access
  private static LinkedHashMap<String, FindFociMemoryResults> memory = new LinkedHashMap<>();

  // The following are class variables for having shorter argument lists
  private String resultsDirectory = null;

  // Used to record all the results into a single file during batch analysis
  private OutputStreamWriter allOut = null;
  private String emptyEntry = null;
  private FindFociBaseProcessor ffpStaged;
  private boolean optimisedProcessor = true;
  private AtomicInteger batchImages;
  private AtomicInteger batchOk;
  private AtomicInteger batchError;

  private class BatchParameters {
    String parameterOptions;
    HashMap<String, String> map;

    int backgroundMethod;
    double backgroundParameter;
    String autoThresholdMethod;
    int searchMethod;
    double searchParameter;
    int maxPeaks;
    int minSize;
    int peakMethod;
    double peakParameter;
    int sortIndex;
    double blur;
    int centreMethod;
    double centreParameter;
    double fractionParameter;
    boolean objectAnalysis;
    boolean showObjectMask;
    boolean saveToMemory;

    int outputType;
    int options;

    static final int C = 0;
    static final int Z = 1;
    static final int T = 2;
    int[] image = new int[3];
    int[] mask = new int[3];
    boolean originalTitle;

    public BatchParameters(String filename) throws IOException, NumberFormatException {
      readParameters(filename);

      // Read all the parameters
      backgroundMethod = findIndex("Background_method", backgroundMethods);
      backgroundParameter = findDouble("Background_parameter");
      autoThresholdMethod = findString("Auto_threshold");
      final String statisticsMode = findString("Statistics_mode");
      searchMethod = findIndex("Search_method", searchMethods);
      searchParameter = findDouble("Search_parameter");
      minSize = findInteger("Minimum_size");
      final boolean minimumAboveSaddle = findBoolean("Minimum_above_saddle");
      final boolean connectedAboveSaddle = findBoolean("Connected_above_saddle");
      peakMethod = findIndex("Minimum_peak_height", peakMethods);
      peakParameter = findDouble("Peak_parameter");
      sortIndex = findIndex("Sort_method", sortIndexMethods);
      maxPeaks = findInteger("Maximum_peaks");
      final int showMask = findIndex("Show_mask", maskOptions);
      fractionParameter = findDouble("Fraction_parameter");
      final boolean markMaxima = findBoolean("Mark_maxima");
      final boolean markRoiMaxima = findBoolean("Mark_peak_maxima");
      final boolean markUsingOverlay = findBoolean("Mark_using_overlay");
      final boolean hideLabels = findBoolean("Hide_labels");
      final boolean showMaskMaximaAsDots = findBoolean("Show_peak_maxima_as_dots");
      final boolean showLogMessages = findBoolean("Show_log_messages");
      final boolean removeEdgeMaxima = findBoolean("Remove_edge_maxima");
      blur = findDouble("Gaussian_blur");
      centreMethod = findIndex("Centre_method", centreMethods);
      centreParameter = findDouble("Centre_parameter");
      objectAnalysis = findBoolean("Object_analysis");
      showObjectMask = findBoolean("Show_object_mask");
      saveToMemory = findBoolean("Save_to_memory");

      image[C] = findInteger("Image_C", 1);
      image[Z] = findInteger("Image_Z", 0);
      image[T] = findInteger("Image_T", 1);
      mask[C] = findInteger("Mask_C", 1);
      mask[Z] = findInteger("Mask_Z", 0);
      mask[T] = findInteger("Mask_T", 1);
      originalTitle = findBoolean("Original_Title");

      outputType = getOutputMaskFlags(showMask);

      if (markMaxima) {
        outputType += OUTPUT_ROI_SELECTION;
      }
      if (markRoiMaxima) {
        outputType += OUTPUT_MASK_ROI_SELECTION;
      }
      if (markUsingOverlay) {
        outputType += OUTPUT_ROI_USING_OVERLAY;
      }
      if (hideLabels) {
        outputType += OUTPUT_HIDE_LABELS;
      }
      if (!showMaskMaximaAsDots) {
        outputType += OUTPUT_MASK_NO_PEAK_DOTS;
      }
      if (showLogMessages) {
        outputType += OUTPUT_LOG_MESSAGES;
      }

      options = 0;
      if (minimumAboveSaddle) {
        options |= OPTION_MINIMUM_ABOVE_SADDLE;
      }
      if (connectedAboveSaddle) {
        options |= OPTION_CONTIGUOUS_ABOVE_SADDLE;
      }
      if (statisticsMode.equalsIgnoreCase("inside")) {
        options |= OPTION_STATS_INSIDE;
      } else if (statisticsMode.equalsIgnoreCase("outside")) {
        options |= OPTION_STATS_OUTSIDE;
      }
      if (removeEdgeMaxima) {
        options |= OPTION_REMOVE_EDGE_MAXIMA;
      }
      if (objectAnalysis) {
        options |= OPTION_OBJECT_ANALYSIS;
        if (showObjectMask) {
          options |= OPTION_SHOW_OBJECT_MASK;
        }
      }
      if (saveToMemory) {
        options |= OPTION_SAVE_TO_MEMORY;
      }
    }

    private void readParameters(String filename) throws IOException {
      final ArrayList<String> parameters = new ArrayList<>();
      try (final BufferedReader input =
          new BufferedReader(new InputStreamReader(new FileInputStream(filename)))) {
        String line;
        while ((line = input.readLine()) != null) {
          // Only use lines that have key-value pairs
          if (line.contains("=")) {
            parameters.add(line);
          }
        }
      }
      if (parameters.isEmpty()) {
        throw new IllegalArgumentException("No key=value parameters in the file");
      }
      // Check if the parameters are macro options
      if (parameters.size() == 1) {
        parameterOptions = parameters.get(0) + " ";
      } else {
        // Store key-value pairs. Lower-case the key
        map = new HashMap<>(parameters.size());
        for (final String line : parameters) {
          final int index = line.indexOf('=');
          final String key = line.substring(0, index).trim().toLowerCase();
          final String value = line.substring(index + 1).trim();
          map.put(key, value);
        }
      }
    }

    private String findString(String key) {
      String value;
      key = key.toLowerCase();
      if (parameterOptions != null) {
        value = Macro.getValue(parameterOptions, key, "");
      } else {
        value = map.get(key);
      }
      if (value == null || value.length() == 0) {
        throw new IllegalArgumentException("Missing parameter: " + key);
      }
      return value;
    }

    private String findString(String key, String defaultValue) {
      String value;
      key = key.toLowerCase();
      if (parameterOptions != null) {
        value = Macro.getValue(parameterOptions, key, "");
      } else {
        value = map.get(key);
      }
      if (value == null || value.length() == 0) {
        return defaultValue;
      }
      return value;
    }

    private double findDouble(String key) {
      return Double.parseDouble(findString(key));
    }

    private int findInteger(String key) {
      return Integer.parseInt(findString(key));
    }

    private int findInteger(String key, int defaultValue) {
      final String value = findString(key, null);
      if (value == null) {
        return defaultValue;
      }
      // Still error if the key value is not an int
      return Integer.parseInt(value);
    }

    private boolean findBoolean(String key) {
      if (parameterOptions != null) {
        return isMatch(parameterOptions, key.toLowerCase() + " ");
      }
      try {
        return Boolean.parseBoolean(findString(key));
      } catch (final IllegalArgumentException ex) {
        return false;
      }
    }

    /**
     * Returns true if s2 is in s1 and not in a bracketed literal, for example "[literal]".
     *
     * <p>Copied from ij.gui.GenericDialog since the recorder options do not show key=value pairs
     * for booleans.
     *
     * @param s1 the s1
     * @param s2 the s2
     * @return true, if is match
     */
    boolean isMatch(String s1, String s2) {
      if (s1.startsWith(s2)) {
        return true;
      }
      s2 = " " + s2;
      final int len1 = s1.length();
      final int len2 = s2.length();
      boolean match;
      boolean inLiteral = false;
      char ch;
      for (int i = 0; i < len1 - len2 + 1; i++) {
        ch = s1.charAt(i);
        if (inLiteral && ch == ']') {
          inLiteral = false;
        } else if (ch == '[') {
          inLiteral = true;
        }
        if (ch != s2.charAt(0) || inLiteral || (i > 1 && s1.charAt(i - 1) == '=')) {
          continue;
        }
        match = true;
        for (int j = 0; j < len2; j++) {
          if (s2.charAt(j) != s1.charAt(i + j)) {
            match = false;
            break;
          }
        }
        if (match) {
          return true;
        }
      }
      return false;
    }

    private int findIndex(String key, String[] options) {
      String value = findString(key);
      // Options in square brackets [] were changed to brackets ()
      if (value.length() > 0 && value.charAt(0) == '[' && value.charAt(value.length() - 1) == ']') {
        value = '(' + value.substring(1, value.length() - 1) + ')';
      }
      for (int i = 0; i < options.length; i++) {
        if (options[i].equalsIgnoreCase(value)) {
          return i;
        }
      }
      throw new IllegalArgumentException("Missing index for option: " + key + "=" + value);
    }
  }

  /**
   * Used for sorting the results by z-slice.
   */
  private static class XyzData {
    final int id;
    final int x;
    final int y;
    final int z;

    XyzData(int id, int x, int y, int z) {
      this.id = id;
      this.x = x;
      this.y = y;
      this.z = z;
    }
  }

  /**
   * Gets list of recognised background methods.
   *
   * @return the background methods
   */
  public static String[] getBackgroundMethods() {
    return backgroundMethods.clone();
  }

  /**
   * Gets list of recognised search methods.
   *
   * @return the search methods
   */
  public static String[] getSearchMethods() {
    return searchMethods.clone();
  }

  /**
   * Gets list of recognised peak height methods.
   *
   * @return the peak methods
   */
  public static String[] getPeakMethods() {
    return peakMethods.clone();
  }

  /**
   * Gets list of recognised mask options.
   *
   * @return the mask options
   */
  public static String[] getMaskOptions() {
    return maskOptions.clone();
  }

  /**
   * Gets list of recognised methods for sorting the results.
   *
   * @return the sort index methods
   */
  public static String[] getSortIndexMethods() {
    return sortIndexMethods.clone();
  }

  /**
   * List of methods for defining the centre of each peak.
   *
   * @return the centre methods
   */
  public static String[] getCentreMethods() {
    return centreMethods.clone();
  }

  /**
   * Gets list of recognised methods for auto-thresholding.
   *
   * @return the auto threshold methods
   */
  public static String[] getAutoThresholdMethods() {
    return autoThresholdMethods.clone();
  }

  /**
   * Gets list of recognised modes for collecting statistics.
   *
   * @return the statistics modes
   */
  public static String[] getStatisticsModes() {
    return statisticsModes.clone();
  }

  /**
   * Gets the background method.
   *
   * @param index the index
   * @return the background method
   */
  public static String getBackgroundMethod(int index) {
    return backgroundMethods[index];
  }

  /**
   * Gets the search method.
   *
   * @param index the index
   * @return the search method
   */
  public static String getSearchMethod(int index) {
    return searchMethods[index];
  }

  /**
   * Gets the peak method.
   *
   * @param index the index
   * @return the peak method
   */
  public static String getPeakMethod(int index) {
    return peakMethods[index];
  }

  /**
   * Gets the mask option.
   *
   * @param index the index
   * @return the mask option
   */
  public static String getMaskOption(int index) {
    return maskOptions[index];
  }

  /**
   * Gets the sort index method.
   *
   * @param index the index
   * @return the sort index method
   */
  public static String getSortIndexMethod(int index) {
    return sortIndexMethods[index];
  }

  /**
   * Gets the centre method.
   *
   * @param index the index
   * @return the centre method
   */
  public static String getCentreMethod(int index) {
    return centreMethods[index];
  }

  /**
   * Gets the auto threshold method.
   *
   * @param index the index
   * @return the auto threshold method
   */
  public static String getAutoThresholdMethod(int index) {
    return autoThresholdMethods[index];
  }

  /**
   * Gets the statistics mode.
   *
   * @param index the index
   * @return the statistics mode
   */
  public static String getStatisticsMode(int index) {
    return statisticsModes[index];
  }

  /** Ask for parameters and then execute. */
  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    final ImagePlus imp = WindowManager.getCurrentImage();

    if ("batch".equals(arg)) {
      runBatchMode();
      return;
    }

    if ("settings".equals(arg)) {
      showSettingsDialog();
      return;
    }

    if (null == imp) {
      IJ.error(TITLE, "There must be at least one image open");
      return;
    }

    if (!isSupported(imp.getBitDepth())) {
      IJ.error(TITLE, "Only " + getSupported() + " images are supported");
      return;
    }

    // Build a list of the open images
    final ArrayList<String> newImageList = buildMaskList(imp);

    final ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);

    gd.addChoice("Mask", newImageList.toArray(new String[0]), myMaskImage);
    gd.addMessage("Background options ...");
    gd.addChoice("Background_method", backgroundMethods, backgroundMethods[myBackgroundMethod]);
    gd.addNumericField("Background_parameter", myBackgroundParameter, 0);
    gd.addChoice("Auto_threshold", autoThresholdMethods, myThresholdMethod);
    gd.addChoice("Statistics_mode", statisticsModes, myStatisticsMode);
    gd.addMessage("Search options ...");
    gd.addChoice("Search_method", searchMethods, searchMethods[mySearchMethod]);
    gd.addNumericField("Search_parameter", mySearchParameter, 2);
    gd.addMessage("Merge options ...");
    gd.addChoice("Minimum_peak_height", peakMethods, peakMethods[myPeakMethod]);
    gd.addNumericField("Peak_parameter", myPeakParameter, 2);
    gd.addNumericField("Minimum_size", myMinSize, 0);
    gd.addCheckbox("Minimum_above_saddle", myMinimumAboveSaddle);
    gd.addCheckbox("Connected_above_saddle", myConnectedAboveSaddle);
    gd.addMessage("Results options ...");
    gd.addChoice("Sort_method", sortIndexMethods, sortIndexMethods[mySortMethod]);
    gd.addNumericField("Maximum_peaks", myMaxPeaks, 0);
    gd.addChoice("Show_mask", maskOptions, maskOptions[myShowMask]);
    gd.addCheckbox("Overlay_mask", myOverlayMask);
    gd.addSlider("Fraction_parameter", 0.05, 1, myFractionParameter);
    gd.addCheckbox("Show_table", myShowTable);
    gd.addCheckbox("Clear_table", myClearTable);
    gd.addCheckbox("Mark_maxima", myMarkMaxima);
    gd.addCheckbox("Mark_peak_maxima", myMarkRoiMaxima);
    gd.addCheckbox("Mark_using_overlay", myMarkUsingOverlay);
    gd.addCheckbox("Hide_labels", myHideLabels);
    gd.addCheckbox("Show_peak_maxima_as_dots", myShowMaskMaximaAsDots);
    gd.addCheckbox("Show_log_messages", myShowLogMessages);
    gd.addCheckbox("Remove_edge_maxima", myRemoveEdgeMaxima);
    gd.addDirectoryField("Results_directory", myResultsDirectory, 30);
    gd.addCheckbox("Object_analysis", myObjectAnalysis);
    gd.addCheckbox("Show_object_mask", myShowObjectMask);
    gd.addCheckbox("Save_to_memory", mySaveToMemory);
    gd.addMessage("Advanced options ...");
    gd.addNumericField("Gaussian_blur", myGaussianBlur, 1);
    gd.addChoice("Centre_method", centreMethods, centreMethods[myCentreMethod]);
    gd.addNumericField("Centre_parameter", myCentreParameter, 0);
    gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.FIND_FOCI);

    gd.showDialog();
    if (gd.wasCanceled()) {
      return;
    }

    myMaskImage = gd.getNextChoice();
    myBackgroundMethod = gd.getNextChoiceIndex();
    myBackgroundParameter = gd.getNextNumber();
    myThresholdMethod = gd.getNextChoice();
    myStatisticsMode = gd.getNextChoice();
    mySearchMethod = gd.getNextChoiceIndex();
    mySearchParameter = gd.getNextNumber();
    myPeakMethod = gd.getNextChoiceIndex();
    myPeakParameter = gd.getNextNumber();
    myMinSize = (int) gd.getNextNumber();
    myMinimumAboveSaddle = gd.getNextBoolean();
    myConnectedAboveSaddle = gd.getNextBoolean();
    mySortMethod = gd.getNextChoiceIndex();
    myMaxPeaks = (int) gd.getNextNumber();
    myShowMask = gd.getNextChoiceIndex();
    myOverlayMask = gd.getNextBoolean();
    myFractionParameter = gd.getNextNumber();
    myShowTable = gd.getNextBoolean();
    myClearTable = gd.getNextBoolean();
    myMarkMaxima = gd.getNextBoolean();
    myMarkRoiMaxima = gd.getNextBoolean();
    myMarkUsingOverlay = gd.getNextBoolean();
    myHideLabels = gd.getNextBoolean();
    myShowMaskMaximaAsDots = gd.getNextBoolean();
    myShowLogMessages = gd.getNextBoolean();
    myRemoveEdgeMaxima = gd.getNextBoolean();
    myResultsDirectory = gd.getNextString();
    myObjectAnalysis = gd.getNextBoolean();
    myShowObjectMask = gd.getNextBoolean();
    mySaveToMemory = gd.getNextBoolean();
    myGaussianBlur = gd.getNextNumber();
    myCentreMethod = gd.getNextChoiceIndex();
    myCentreParameter = gd.getNextNumber();

    int outputType = getOutputMaskFlags(myShowMask);

    if (myOverlayMask) {
      outputType += OUTPUT_OVERLAY_MASK;
    }
    if (myShowTable) {
      outputType += OUTPUT_RESULTS_TABLE;
    }
    if (myClearTable) {
      outputType += OUTPUT_CLEAR_RESULTS_TABLE;
    }
    if (myMarkMaxima) {
      outputType += OUTPUT_ROI_SELECTION;
    }
    if (myMarkRoiMaxima) {
      outputType += OUTPUT_MASK_ROI_SELECTION;
    }
    if (myMarkUsingOverlay) {
      outputType += OUTPUT_ROI_USING_OVERLAY;
    }
    if (myHideLabels) {
      outputType += OUTPUT_HIDE_LABELS;
    }
    if (!myShowMaskMaximaAsDots) {
      outputType += OUTPUT_MASK_NO_PEAK_DOTS;
    }
    if (myShowLogMessages) {
      outputType += OUTPUT_LOG_MESSAGES;
    }

    int options = 0;
    if (myMinimumAboveSaddle) {
      options |= OPTION_MINIMUM_ABOVE_SADDLE;
    }
    if (myConnectedAboveSaddle) {
      options |= OPTION_CONTIGUOUS_ABOVE_SADDLE;
    }
    if (myStatisticsMode.equalsIgnoreCase("inside")) {
      options |= OPTION_STATS_INSIDE;
    } else if (myStatisticsMode.equalsIgnoreCase("outside")) {
      options |= OPTION_STATS_OUTSIDE;
    }
    if (myRemoveEdgeMaxima) {
      options |= OPTION_REMOVE_EDGE_MAXIMA;
    }

    if (outputType == 0) {
      IJ.error(TITLE, "No results options chosen");
      return;
    }

    final ImagePlus mask = WindowManager.getImage(myMaskImage);

    setResultsDirectory(myResultsDirectory);

    // Only perform object analysis if necessary
    if (myObjectAnalysis && (myShowTable || this.resultsDirectory != null)) {
      options |= OPTION_OBJECT_ANALYSIS;
      if (myShowObjectMask) {
        options |= OPTION_SHOW_OBJECT_MASK;
      }
    }

    if (mySaveToMemory) {
      options |= OPTION_SAVE_TO_MEMORY;
    }

    try {
      exec(imp, mask, myBackgroundMethod, myBackgroundParameter, myThresholdMethod, mySearchMethod,
          mySearchParameter, myMaxPeaks, myMinSize, myPeakMethod, myPeakParameter, outputType,
          mySortMethod, options, myGaussianBlur, myCentreMethod, myCentreParameter,
          myFractionParameter);
    } catch (final Throwable thrown) {
      // Because we have no underscore '_' in the class name ImageJ will not print
      // the error so handle it here
      IJ.handleException(thrown);
    }
  }

  /**
   * Get the output flags required for the specified index in the mask options.
   *
   * <p>See {@link #getMaskOptions()}.
   *
   * @param showMask the show mask index
   * @return The output flags
   */
  public static int getOutputMaskFlags(int showMask) {
    switch (showMask) {
      case 1:
        return FindFociProcessor.OUTPUT_MASK_PEAKS;
      case 2:
        return FindFociProcessor.OUTPUT_MASK_THRESHOLD;
      case 3:
        return FindFociProcessor.OUTPUT_MASK_PEAKS | FindFociProcessor.OUTPUT_MASK_ABOVE_SADDLE;
      case 4:
        return FindFociProcessor.OUTPUT_MASK_THRESHOLD | FindFociProcessor.OUTPUT_MASK_ABOVE_SADDLE;
      case 5:
        return FindFociProcessor.OUTPUT_MASK_PEAKS
            | FindFociProcessor.OUTPUT_MASK_FRACTION_OF_INTENSITY;
      case 6:
        return FindFociProcessor.OUTPUT_MASK_PEAKS
            | FindFociProcessor.OUTPUT_MASK_FRACTION_OF_HEIGHT;
      default:
        return 0;
    }
  }

  /**
   * Build a list of all the images with the correct dimensions to be used as a mask for the
   * specified image.
   *
   * @param imp the image
   * @return the array list
   */
  public static ArrayList<String> buildMaskList(ImagePlus imp) {
    final ArrayList<String> newImageList = new ArrayList<>();
    newImageList.add("(None)");

    if (imp != null) {
      for (final int id : ImageJUtils.getIdList()) {
        final ImagePlus maskImp = WindowManager.getImage(id);
        // Mask image must:
        // - Not be the same image
        // - Not be derived from the same image, i.e. a FindFoci result (check using image name)
        // - Match dimensions of the input image
        if (maskImp != null && maskImp.getID() != imp.getID() && !maskImp.getTitle().endsWith(TITLE)
            &&
            // !maskImp.getTitle().startsWith(imp.getTitle()) &&
            maskImp.getWidth() == imp.getWidth() && maskImp.getHeight() == imp.getHeight()
            && (maskImp.getNSlices() == imp.getNSlices() || maskImp.getNSlices() == 1)) {
          newImageList.add(maskImp.getTitle());
        }
      }
    }

    return newImageList;
  }

  /**
   * Perform peak finding.
   *
   * <p>Parameters as described in
   * {@link FindFociProcessor#findMaxima(ImagePlus, ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int, double, int, double, double) }.
   *
   * @param imp the image
   * @param mask A mask image used to define the region to search for peaks
   * @param backgroundMethod Method for calculating the background level (use the constants with
   *        prefix BACKGROUND_)
   * @param backgroundParameter parameter for calculating the background level
   * @param autoThresholdMethod The thresholding method (use a string from
   *        {@link uk.ac.sussex.gdsc.core.threshold.AutoThreshold#getMethods() } )
   * @param searchMethod Method for calculating the region growing stopping criteria (use the
   *        constants with prefix SEARCH_)
   * @param searchParameter parameter for calculating the stopping criteria
   * @param maxPeaks The maximum number of peaks to report
   * @param minSize The minimum size for a peak
   * @param peakMethod Method for calculating the minimum peak height above the highest saddle (use
   *        the constants with prefix PEAK_)
   * @param peakParameter parameter for calculating the minimum peak height
   * @param outputType Use {@link #OUTPUT_MASK_PEAKS} to get an ImagePlus in the result Object
   *        array. Use {@link #OUTPUT_LOG_MESSAGES} to get runtime information.
   * @param sortIndex The index of the result statistic to use for the peak sorting
   * @param options An options flag (use the constants with prefix OPTION_)
   * @param blur Apply a Gaussian blur of the specified radius before processing (helps smooth noisy
   *        images for better peak identification)
   * @param centreMethod Define the method used to calculate the peak centre (use the constants with
   *        prefix CENTRE_)
   * @param centreParameter Parameter for calculating the peak centre
   * @param fractionParameter Used to specify the fraction of the peak to show in the mask
   */
  public void exec(ImagePlus imp, ImagePlus mask, int backgroundMethod, double backgroundParameter,
      String autoThresholdMethod, int searchMethod, double searchParameter, int maxPeaks,
      int minSize, int peakMethod, double peakParameter, int outputType, int sortIndex, int options,
      double blur, int centreMethod, double centreParameter, double fractionParameter) {
    lastResultsArray = null;

    if (!isSupported(imp.getBitDepth())) {
      IJ.error(TITLE, "Only " + getSupported() + " images are supported");
      return;
    }
    if ((centreMethod == CENTRE_GAUSSIAN_ORIGINAL || centreMethod == CENTRE_GAUSSIAN_SEARCH)
        && isGaussianFitEnabled < 1) {
      IJ.error(TITLE, "Gaussian fit is not currently enabled");
      return;
    }

    // Ensure the ROI is reset if it is a point selection
    if ((outputType & OUTPUT_ROI_SELECTION) != 0) {
      Roi roi = imp.getRoi();
      imp.saveRoi(); // save previous selection so user can restore it
      if (roi != null) {
        if (roi.isArea()) {
          if ((outputType & OUTPUT_ROI_USING_OVERLAY) == 0) {
            // YesNoCancelDialog causes asynchronous thread exception within Eclipse.
            final GenericDialog d = new GenericDialog(TITLE);
            d.addMessage("Warning: Marking the maxima will destroy the ROI area.\n"
                + "Use the Mark_using_overlay option to preserve the ROI.\n \n"
                + "Click OK to continue (destroys the area ROI)");
            d.showDialog();
            if (!d.wasOKed()) {
              return;
            }
          }
        } else {
          // Remove any non-area ROI to reset the bounding rectangle
          imp.killRoi();
          roi = null;
        }
      }

      // The image may have a point ROI overlay added by the showResults(...) method called by the
      // preview functionality of the FindFoci GUI
      killOverlayPointRoi(imp);
    }

    final FindFociBaseProcessor ffp = createFindFociProcessor(imp);
    final FindFociResults ffResult =
        findMaxima(ffp, imp, mask, backgroundMethod, backgroundParameter, autoThresholdMethod,
            searchMethod, searchParameter, maxPeaks, minSize, peakMethod, peakParameter, outputType,
            sortIndex, options, blur, centreMethod, centreParameter, fractionParameter);

    if (ffResult == null) {
      IJ.showStatus("Cancelled.");
      return;
    }
    lastResultsArray = ffResult.results;

    // Get the results
    final ImagePlus maximaImp = ffResult.mask;
    final List<FindFociResult> resultsArray = ffResult.results;
    final FindFociStatistics stats = ffResult.stats;

    // If we are outputting a results table or saving to file we can do the object analysis
    if ((options & OPTION_OBJECT_ANALYSIS) != 0
        && (((outputType & OUTPUT_RESULTS_TABLE) != 0) || resultsDirectory != null)) {
      final ImagePlus objectImp = ffp.doObjectAnalysis(mask, maximaImp, resultsArray,
          (options & OPTION_SHOW_OBJECT_MASK) != 0, null);
      if (objectImp != null) {
        objectImp.show();
      }
    }

    if ((options & OPTION_SAVE_TO_MEMORY) != 0) {
      saveToMemory(resultsArray, imp);
    }

    // Add peaks to a results window
    if ((outputType & OUTPUT_CLEAR_RESULTS_TABLE) != 0) {
      clearResultsWindow();
    }
    if (resultsArray != null && (outputType & OUTPUT_RESULTS_TABLE) != 0) {
      // if (isLogging(outputType))
      // IJ.log("Background (noise) level = " + IJ.d2s(stats.STATS_BACKGROUND, 2));

      // There is some strange problem when using ImageJ's default results table when it asks the
      // user
      // to save a previous list and then aborts the plugin if the user says Yes, No or Cancel
      // leaving
      // the image locked.
      // So use a custom results table instead (or just Analyzer.setUnsavedMeasurements(false)).

      // Use a custom result table to avoid IJ bug
      createResultsWindow();
      for (int i = 0; i < resultsArray.size(); i++) {
        final FindFociResult result = resultsArray.get(i);
        addToResultTable(ffp, i + 1, resultsArray.size() - i, result, stats);
      }
      flushResults();
    }

    // Record all the results to file
    if (resultsArray != null && resultsDirectory != null) {
      saveResults(ffp, generateId(imp), imp, mask, backgroundMethod, backgroundParameter,
          autoThresholdMethod, searchMethod, searchParameter, maxPeaks, minSize, peakMethod,
          peakParameter, outputType, sortIndex, options, blur, centreMethod, centreParameter,
          fractionParameter, resultsArray, stats, resultsDirectory);
    }

    // Update the mask image
    ImagePlus maxImp = null;
    Overlay overlay = null;
    if (maximaImp != null) {
      if ((outputType & OUTPUT_MASK) != 0) {
        maxImp = showImage(maximaImp, imp.getTitle() + " " + TITLE);

        // Adjust the contrast to show all the maxima
        if (resultsArray != null) {
          final int maxValue =
              ((outputType & OUTPUT_MASK_THRESHOLD) != 0) ? 4 : resultsArray.size() + 1;
          maxImp.setDisplayRange(0, maxValue);
        }

        maxImp.updateAndDraw();
      }
      if ((outputType & OUTPUT_OVERLAY_MASK) != 0) {
        overlay = createOverlay(imp, maximaImp);
      }
    }

    // Remove ROI if not an output option
    if ((outputType & OUTPUT_ROI_SELECTION) == 0) {
      killPointRoi(imp);
      killOverlayPointRoi(imp);
    }
    if ((outputType & OUTPUT_MASK_ROI_SELECTION) == 0) {
      killPointRoi(maxImp);
      killOverlayPointRoi(maxImp);
    }

    // Add ROI crosses to original image
    if (resultsArray != null
        && (outputType & (OUTPUT_ROI_SELECTION | OUTPUT_MASK_ROI_SELECTION)) != 0) {
      if (!resultsArray.isEmpty()) {
        if ((outputType & OUTPUT_ROI_USING_OVERLAY) != 0) {
          // Create an roi for each z slice
          final PointRoi[] rois = createStackRoi(resultsArray, outputType);

          if ((outputType & OUTPUT_ROI_SELECTION) != 0) {
            killPointRoi(imp);
            overlay = addRoiToOverlay(imp, rois, overlay);
          }

          if (maxImp != null && (outputType & OUTPUT_MASK_ROI_SELECTION) != 0) {
            killPointRoi(maxImp);
            addRoiToOverlay(maxImp, rois);
          }
        } else {
          final PointRoi roi = createRoi(resultsArray, outputType);

          if ((outputType & OUTPUT_ROI_SELECTION) != 0) {
            killOverlayPointRoi(imp);
            imp.setRoi(roi);
          }

          if (maxImp != null && (outputType & OUTPUT_MASK_ROI_SELECTION) != 0) {
            killOverlayPointRoi(maxImp);
            maxImp.setRoi(roi);
          }
        }
      } else {
        if ((outputType & OUTPUT_ROI_SELECTION) != 0) {
          killPointRoi(imp);
          killOverlayPointRoi(imp);
        }
        if ((outputType & OUTPUT_MASK_ROI_SELECTION) != 0) {
          killPointRoi(maxImp);
          killOverlayPointRoi(maxImp);
        }
      }
    }

    imp.setOverlay(overlay);
  }

  /**
   * Add the point rois to the overlay configuring the hyperstack position if necessary.
   *
   * @param imp the imp
   * @param rois the rois
   */
  private static void addRoiToOverlay(ImagePlus imp, Roi[] rois) {
    imp.setOverlay(addRoiToOverlay(imp, rois, imp.getOverlay()));
  }

  /**
   * Add the point rois to the overlay configuring the hyperstack position if necessary.
   *
   * @param imp the imp
   * @param rois the rois
   * @param overlay the overlay
   * @return the overlay
   */
  private static Overlay addRoiToOverlay(ImagePlus imp, Roi[] rois, Overlay overlay) {
    overlay = removeOverlayPointRoi(overlay);
    final int channel = imp.getChannel();
    final int frame = imp.getFrame();

    if (rois.length > 0) {
      if (overlay == null) {
        overlay = new Overlay();
      }
      for (Roi roi : rois) {
        if (imp.isDisplayedHyperStack()) {
          roi = (Roi) roi.clone();
          roi.setPosition(channel, roi.getPosition(), frame);
        }
        overlay.addElement(roi);
      }
    }
    return overlay;
  }

  private static PointRoi[] createStackRoi(List<FindFociResult> resultsArray, int outputType) {
    final int nMaxima = resultsArray.size();
    final XyzData[] xyz = new XyzData[nMaxima];
    for (int i = 0; i < nMaxima; i++) {
      final FindFociResult xy = resultsArray.get(i);
      xyz[i] = new XyzData(i + 1, xy.x, xy.y, xy.z);
    }

    Arrays.sort(xyz, (o1, o2) -> Integer.compare(o1.z, o2.z));

    final boolean hideLabels = nMaxima < 2 || (outputType & OUTPUT_HIDE_LABELS) != 0;

    final PointRoi[] rois = new PointRoi[nMaxima];
    int count = 0;
    final int[] ids = new int[nMaxima];
    final int[] xpoints = new int[nMaxima];
    final int[] ypoints = new int[nMaxima];
    int npoints = 0;
    int zposition = xyz[0].z;
    for (int i = 0; i < nMaxima; i++) {
      if (xyz[i].z != zposition) {
        rois[count++] = createPointRoi(ids, xpoints, ypoints, zposition + 1, npoints, hideLabels);
        npoints = 0;
      }
      ids[npoints] = xyz[i].id;
      xpoints[npoints] = xyz[i].x;
      ypoints[npoints] = xyz[i].y;
      zposition = xyz[i].z;
      npoints++;
    }
    rois[count++] = createPointRoi(ids, xpoints, ypoints, zposition + 1, npoints, hideLabels);
    return Arrays.copyOf(rois, count);
  }

  private static PointRoi createPointRoi(int[] ids, int[] xpoints, int[] ypoints, int slice,
      int npoints, boolean hideLabels) {
    // Use a custom PointRoi so we can draw the labels
    final LabelledPointRoi roi = new LabelledPointRoi(xpoints, ypoints, npoints);
    if (hideLabels) {
      roi.setShowLabels(false);
    } else {
      roi.setLabels(ids);
    }
    configureOverlayRoi(roi);

    // This is only applicable to single z stack images.
    // We should call setPosition(int,int,int) for hyperstacks
    roi.setPosition(slice);

    return roi;
  }

  private static PointRoi createRoi(List<FindFociResult> resultsArray, int outputType) {
    final int nMaxima = resultsArray.size();
    final int[] xpoints = new int[nMaxima];
    final int[] ypoints = new int[nMaxima];
    for (int i = 0; i < nMaxima; i++) {
      final FindFociResult xy = resultsArray.get(i);
      xpoints[i] = xy.x;
      ypoints[i] = xy.y;
    }
    final PointRoi roi = new PointRoi(xpoints, ypoints, nMaxima);
    if ((outputType & OUTPUT_HIDE_LABELS) != 0) {
      roi.setShowLabels(false);
    }
    return roi;
  }

  private static Overlay createOverlay(ImagePlus imp, ImagePlus maximaImp) {
    final Overlay o = new Overlay();
    final int channel = imp.getChannel();
    final int frame = imp.getFrame();
    final ImageStack stack = maximaImp.getImageStack();
    final int currentSlice = imp.getCurrentSlice();

    // Q. What happens if the image is the same colour?
    // Currently we just leave it to the user to switch the LUT for the image.
    final int value = Color.YELLOW.getRGB();
    // imp.getLuts();

    final boolean multiSliceStack = maximaImp.getStackSize() > 1;
    for (int slice = 1; slice <= maximaImp.getStackSize(); slice++) {
      // Use a RGB image to allow coloured output
      final ImageProcessor ip = stack.getProcessor(slice);
      final ColorProcessor cp = new ColorProcessor(ip.getWidth(), ip.getHeight());
      for (int i = 0; i < ip.getPixelCount(); i++) {
        if (ip.get(i) > 0) {
          cp.set(i, value);
        }
      }
      final ImageRoi roi = new ImageRoi(0, 0, cp);
      roi.setZeroTransparent(true);
      roi.setOpacity(0.5);
      if (imp.isDisplayedHyperStack()) {
        roi.setPosition(channel, slice, frame);
      } else {
        // If the mask has more than one slice then we can use the stack slice.
        // Otherwise we processed a single slice of the image and should use the current index.
        roi.setPosition((multiSliceStack) ? slice : currentSlice);
      }
      o.add(roi);
    }
    return o;
  }

  private static ImagePlus showImage(ImagePlus imp, String title) {
    return showImage(imp, title, true);
  }

  /**
   * Create a new image or recycle an existing image window.
   *
   * @param imp the image
   * @param title the title
   * @param show Set to true to show the image
   * @return the image plus
   */
  static ImagePlus showImage(ImagePlus imp, String title, boolean show) {
    ImagePlus maxImp = WindowManager.getImage(title);
    final ImageStack stack = imp.getImageStack();
    if (maxImp != null) {
      maxImp.setStack(stack);
    } else {
      maxImp = new ImagePlus(title, stack);
    }
    maxImp.setCalibration(imp.getCalibration());
    if (show) {
      maxImp.show();
    }
    return maxImp;
  }

  private String openBatchResultsFile() {
    final String filename = resultsDirectory + File.separatorChar + "all.xls";
    try {
      final FileOutputStream fos = new FileOutputStream(filename);
      allOut = new OutputStreamWriter(fos, "UTF-8");
      allOut.write("Image ID\tImage\t" + createResultsHeader(NEW_LINE));
      return filename;
    } catch (final Exception ex) {
      logError(ex.getMessage());
      closeBatchResultsFile();
      return null;
    }
  }

  private void closeBatchResultsFile() {
    if (allOut == null) {
      return;
    }
    try {
      allOut.close();
    } catch (final Exception ex) {
      logError(ex.getMessage());
    } finally {
      allOut = null;
    }
  }

  private class BatchResult implements Comparable<BatchResult> {
    final String entry;
    final int id;
    final int batchId;

    BatchResult(String entry, int id) {
      this.entry = entry;
      this.id = id;
      // The first characters before the tab are the batch Id
      final int index = entry.indexOf('\t');
      batchId = Integer.parseInt(entry.substring(0, index));
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(BatchResult that) {
      final int result = this.batchId - that.batchId;
      return (result == 0) ? this.id - that.id : result;
    }
  }

  private void sortBatchResultsFile(String filename) {
    final Path path = new File(filename).toPath();
    ArrayList<BatchResult> results = new ArrayList<>();
    String header = null;
    try (final BufferedReader input = Files.newBufferedReader(path)) {
      header = input.readLine();
      String line;
      int id = 0;
      while ((line = input.readLine()) != null) {
        results.add(new BatchResult(line, ++id));
      }
    } catch (final OutOfMemoryError ex) {
      // In case the file is too big to read in for a sort
      results.clear();
      results = null;
      // Try and free the memory
      final Runtime runtime = Runtime.getRuntime();
      runtime.runFinalization();
      runtime.gc();
      logError(ex.getMessage());
      return;
    } catch (final Exception ex) {
      logError(ex.getMessage());
      return;
    }

    Collections.sort(results);

    try (final BufferedWriter out = Files.newBufferedWriter(new File(filename).toPath())) {
      // Add new lines because Buffered reader strips them
      out.write(header);
      out.write(NEW_LINE);
      for (final BatchResult r : results) {
        out.write(r.entry);
        out.write(NEW_LINE);
      }
    } catch (final Exception ex) {
      logError(ex.getMessage());
    }
  }

  private static String initialiseBatchPrefix(int batchId, String title) {
    return batchId + "\t" + title + "\t";
  }

  private synchronized void writeBatchResultsFile(String batchPrefix, List<String> results) {
    if (allOut == null) {
      return;
    }
    try {
      for (final String result : results) {
        allOut.write(batchPrefix);
        allOut.write(result);
      }
    } catch (final Exception ex) {
      logError(ex.getMessage());
      closeBatchResultsFile();
    }
  }

  private static void writeEmptyObjectsToBatchResultsFile(ArrayList<String> batchResults,
      ObjectAnalysisResult objectAnalysisResult) {
    for (int id = 1; id <= objectAnalysisResult.numberOfObjects; id++) {
      if (objectAnalysisResult.fociCount[id] == 0) {
        batchResults.add(buildEmptyObjectResultEntry(id, objectAnalysisResult.objectState[id]));
      }
    }
  }

  private void writeEmptyBatchResultsFile(ArrayList<String> batchResults) {
    batchResults.add(buildEmptyResultEntry());
  }

  private String saveResults(FindFociBaseProcessor ffp, String expId, ImagePlus imp, ImagePlus mask,
      int backgroundMethod, double backgroundParameter, String autoThresholdMethod,
      int searchMethod, double searchParameter, int maxPeaks, int minSize, int peakMethod,
      double peakParameter, int outputType, int sortIndex, int options, double blur,
      int centreMethod, double centreParameter, double fractionParameter,
      List<FindFociResult> resultsArray, FindFociStatistics stats, String resultsDirectory) {
    return saveResults(ffp, expId, imp, null, mask, null, backgroundMethod, backgroundParameter,
        autoThresholdMethod, searchMethod, searchParameter, maxPeaks, minSize, peakMethod,
        peakParameter, outputType, sortIndex, options, blur, centreMethod, centreParameter,
        fractionParameter, resultsArray, stats, resultsDirectory, null, null);
  }

  private String saveResults(FindFociBaseProcessor ffp, String expId, ImagePlus imp,
      int[] imageDimension, ImagePlus mask, int[] maskDimension, int backgroundMethod,
      double backgroundParameter, String autoThresholdMethod, int searchMethod,
      double searchParameter, int maxPeaks, int minSize, int peakMethod, double peakParameter,
      int outputType, int sortIndex, int options, double blur, int centreMethod,
      double centreParameter, double fractionParameter, List<FindFociResult> resultsArray,
      FindFociStatistics stats, String resultsDirectory, ObjectAnalysisResult objectAnalysisResult,
      String batchPrefix) {
    try (final BufferedWriter out =
        Files.newBufferedWriter(new File(resultsDirectory, expId + ".xls").toPath())) {
      // Save results to file
      if (imageDimension == null) {
        imageDimension = new int[] {imp.getC(), 0, imp.getT()};
      }
      out.write(createResultsHeader(imp, imageDimension, stats, NEW_LINE));
      final int[] xpoints = new int[resultsArray.size()];
      final int[] ypoints = new int[resultsArray.size()];
      final StringBuilder sb = new StringBuilder();
      final ArrayList<String> batchResults =
          (allOut == null) ? null : new ArrayList<>(resultsArray.size());
      for (int i = 0; i < resultsArray.size(); i++) {
        final FindFociResult result = resultsArray.get(i);
        xpoints[i] = result.x;
        ypoints[i] = result.y;

        buildResultEntry(ffp, sb, i + 1, resultsArray.size() - i, result, stats, NEW_LINE);
        final String resultEntry = sb.toString();
        out.write(resultEntry);
        if (batchResults != null) {
          batchResults.add(resultEntry);
        }
        sb.setLength(0);
      }
      // Check if we have a batch file
      if (batchResults != null) {
        if (objectAnalysisResult != null) {
          // Record an empty record for empty objects
          writeEmptyObjectsToBatchResultsFile(batchResults, objectAnalysisResult);
        } else if (resultsArray.isEmpty()) {
          // Record an empty record for batch processing
          writeEmptyBatchResultsFile(batchResults);
        }

        writeBatchResultsFile(batchPrefix, batchResults);
      }

      // Save roi to file
      final RoiEncoder roiEncoder =
          new RoiEncoder(resultsDirectory + File.separatorChar + expId + ".roi");
      roiEncoder.write(new PointRoi(xpoints, ypoints, resultsArray.size()));

      // Save parameters to file
      if (mask != null && maskDimension == null) {
        maskDimension = new int[] {mask.getC(), 0, mask.getT()};
      }
      saveParameters(resultsDirectory + File.separatorChar + expId + ".params", imp, imageDimension,
          mask, maskDimension, backgroundMethod, backgroundParameter, autoThresholdMethod,
          searchMethod, searchParameter, maxPeaks, minSize, peakMethod, peakParameter, outputType,
          sortIndex, options, blur, centreMethod, centreParameter, fractionParameter,
          resultsDirectory);
      return expId;
    } catch (final Exception ex) {
      logError(ex.getMessage());
    }
    return "";
  }

  /**
   * Save the FindFoci parameters to file.
   *
   * @param filename the filename
   * @param imp the imp
   * @param imageDimension the image dimension
   * @param mask the mask
   * @param maskDimension the mask dimension
   * @param backgroundMethod the background method
   * @param backgroundParameter the background parameter
   * @param autoThresholdMethod the auto threshold method
   * @param searchMethod the search method
   * @param searchParameter the search parameter
   * @param maxPeaks the max peaks
   * @param minSize the min size
   * @param peakMethod the peak method
   * @param peakParameter the peak parameter
   * @param outputType the output type
   * @param sortIndex the sort index
   * @param options the options
   * @param blur the blur
   * @param centreMethod the centre method
   * @param centreParameter the centre parameter
   * @param fractionParameter the fraction parameter
   * @param resultsDirectory the results directory
   * @return True if the parameters were saved
   */
  static boolean saveParameters(String filename, ImagePlus imp, int[] imageDimension,
      ImagePlus mask, int[] maskDimension, int backgroundMethod, double backgroundParameter,
      String autoThresholdMethod, int searchMethod, double searchParameter, int maxPeaks,
      int minSize, int peakMethod, double peakParameter, int outputType, int sortIndex, int options,
      double blur, int centreMethod, double centreParameter, double fractionParameter,
      String resultsDirectory) {
    try (final OutputStreamWriter out =
        new OutputStreamWriter(new FileOutputStream(filename), "UTF-8")) {
      // Save parameters to file
      if (imp != null) {
        writeParam(out, "Image", imp.getTitle());
        if (imp.getOriginalFileInfo() != null) {
          final String path = (imp.getOriginalFileInfo().directory != null)
              ? imp.getOriginalFileInfo().directory + imp.getOriginalFileInfo().fileName
              : imp.getOriginalFileInfo().fileName;
          writeParam(out, "File", path);
        }
        writeParam(out, "Image_C", Integer.toString(imageDimension[BatchParameters.C]));
        writeParam(out, "Image_Z", Integer.toString(imageDimension[BatchParameters.Z]));
        writeParam(out, "Image_T", Integer.toString(imageDimension[BatchParameters.T]));
      }
      if (mask != null) {
        writeParam(out, "Mask", mask.getTitle());
        if (mask.getOriginalFileInfo() != null) {
          final String path = (mask.getOriginalFileInfo().directory != null)
              ? mask.getOriginalFileInfo().directory + mask.getOriginalFileInfo().fileName
              : mask.getOriginalFileInfo().fileName;
          writeParam(out, "Mask File", path);
        }
        writeParam(out, "Mask_C", Integer.toString(maskDimension[BatchParameters.C]));
        writeParam(out, "Mask_Z", Integer.toString(maskDimension[BatchParameters.Z]));
        writeParam(out, "Mask_T", Integer.toString(maskDimension[BatchParameters.T]));
      }
      writeParam(out, "Background_method", backgroundMethods[backgroundMethod]);
      writeParam(out, "Background_parameter", Double.toString(backgroundParameter));
      if (autoThresholdMethod == null || autoThresholdMethod.length() == 0) {
        autoThresholdMethod = "(None)";
      }
      writeParam(out, "Auto_threshold", autoThresholdMethod);
      writeParam(out, "Statistics_mode", getStatisticsMode(options));
      writeParam(out, "Search_method", searchMethods[searchMethod]);
      writeParam(out, "Search_parameter", Double.toString(searchParameter));
      writeParam(out, "Minimum_size", Integer.toString(minSize));
      writeParam(out, "Minimum_above_saddle",
          ((options & OPTION_MINIMUM_ABOVE_SADDLE) != 0) ? "true" : "false");
      writeParam(out, "Connected_above_saddle",
          ((options & OPTION_CONTIGUOUS_ABOVE_SADDLE) != 0) ? "true" : "false");
      writeParam(out, "Minimum_peak_height", peakMethods[peakMethod]);
      writeParam(out, "Peak_parameter", Double.toString(peakParameter));
      writeParam(out, "Sort_method", sortIndexMethods[sortIndex]);
      writeParam(out, "Maximum_peaks", Integer.toString(maxPeaks));
      writeParam(out, "Show_mask", maskOptions[getMaskOptionFromFlags(outputType)]);
      writeParam(out, "Overlay_mask", ((outputType & OUTPUT_OVERLAY_MASK) != 0) ? "true" : "false");
      writeParam(out, "Fraction_parameter", "" + fractionParameter);
      writeParam(out, "Show_table", ((outputType & OUTPUT_RESULTS_TABLE) != 0) ? "true" : "false");
      writeParam(out, "Clear_table",
          ((outputType & OUTPUT_CLEAR_RESULTS_TABLE) != 0) ? "true" : "false");
      writeParam(out, "Mark_maxima", ((outputType & OUTPUT_ROI_SELECTION) != 0) ? "true" : "false");
      writeParam(out, "Mark_peak_maxima",
          ((outputType & OUTPUT_MASK_ROI_SELECTION) != 0) ? "true" : "false");
      writeParam(out, "Mark_using_overlay",
          ((outputType & OUTPUT_ROI_USING_OVERLAY) != 0) ? "true" : "false");
      writeParam(out, "Hide_labels", ((outputType & OUTPUT_HIDE_LABELS) != 0) ? "true" : "false");
      writeParam(out, "Show_peak_maxima_as_dots",
          ((outputType & OUTPUT_MASK_NO_PEAK_DOTS) == 0) ? "true" : "false");
      writeParam(out, "Show_log_messages",
          ((outputType & OUTPUT_LOG_MESSAGES) != 0) ? "true" : "false");
      writeParam(out, "Remove_edge_maxima",
          ((options & OPTION_REMOVE_EDGE_MAXIMA) != 0) ? "true" : "false");
      writeParam(out, "Results_directory", resultsDirectory);
      writeParam(out, "Object_analysis",
          ((options & OPTION_OBJECT_ANALYSIS) != 0) ? "true" : "false");
      writeParam(out, "Show_object_mask",
          ((options & OPTION_SHOW_OBJECT_MASK) != 0) ? "true" : "false");
      writeParam(out, "Save_to_memory ",
          ((options & OPTION_SAVE_TO_MEMORY) != 0) ? "true" : "false");
      writeParam(out, "Gaussian_blur", "" + blur);
      writeParam(out, "Centre_method", centreMethods[centreMethod]);
      writeParam(out, "Centre_parameter", "" + centreParameter);
      out.close();
      return true;
    } catch (final Exception ex) {
      logError(ex.getMessage());
    }
    return false;
  }

  /**
   * Gets the statistics mode.
   *
   * @param options the options
   * @return the statistics mode
   */
  public static String getStatisticsModeFromOptions(int options) {
    if ((options & (FindFociProcessor.OPTION_STATS_INSIDE
        | FindFociProcessor.OPTION_STATS_OUTSIDE)) == (FindFociProcessor.OPTION_STATS_INSIDE
            | FindFociProcessor.OPTION_STATS_OUTSIDE)) {
      return "Both";
    }
    if ((options & FindFociProcessor.OPTION_STATS_INSIDE) != 0) {
      return "Inside";
    }
    if ((options & FindFociProcessor.OPTION_STATS_OUTSIDE) != 0) {
      return "Outside";
    }
    return "Both";
  }

  private static void killPointRoi(ImagePlus imp) {
    if (imp != null && imp.getRoi() != null && imp.getRoi().getType() == Roi.POINT) {
      imp.killRoi();
    }
  }

  private static void configureOverlayRoi(Roi roi) {
    roi.setStrokeWidth(1);
    roi.setStrokeColor(Color.CYAN);
    roi.setFillColor(Color.YELLOW);
  }

  private static void killOverlayPointRoi(ImagePlus imp) {
    if (imp != null && imp.getOverlay() != null) {
      imp.setOverlay(removeOverlayPointRoi(imp.getOverlay()));
    }
  }

  /**
   * Removes any PointRoi from the overlay.
   *
   * @param overlay the overlay
   * @return The reduced overlay (or null)
   */
  private static Overlay removeOverlayPointRoi(Overlay overlay) {
    if (overlay != null) {
      final Roi[] rois = overlay.toArray();
      int count = 0;
      for (int i = 0; i < rois.length; i++) {
        if (rois[i] instanceof PointRoi) {
          if (rois[i].getStrokeColor() == Color.CYAN && rois[i].getFillColor() == Color.YELLOW) {
            continue;
          }
        }
        rois[count++] = rois[i];
      }
      if (count == 0) {
        return null;
      }
      if (count != rois.length) {
        overlay.clear();
        for (int i = 0; i < count; i++) {
          overlay.add(rois[i]);
        }
      }
    }
    return overlay;
  }

  private static int getMaskOptionFromFlags(int outputType) {
    if (isSet(outputType, FindFociProcessor.OUTPUT_MASK_THRESHOLD
        | FindFociProcessor.OUTPUT_MASK_FRACTION_OF_HEIGHT)) {
      return 6;
    }
    if (isSet(outputType, FindFociProcessor.OUTPUT_MASK_THRESHOLD
        | FindFociProcessor.OUTPUT_MASK_FRACTION_OF_INTENSITY)) {
      return 5;
    }
    if (isSet(outputType,
        FindFociProcessor.OUTPUT_MASK_THRESHOLD | FindFociProcessor.OUTPUT_MASK_ABOVE_SADDLE)) {
      return 4;
    }
    if (isSet(outputType,
        FindFociProcessor.OUTPUT_MASK_PEAKS | FindFociProcessor.OUTPUT_MASK_ABOVE_SADDLE)) {
      return 3;
    }
    if (isSet(outputType, FindFociProcessor.OUTPUT_MASK_THRESHOLD)) {
      return 2;
    }
    if (isSet(outputType, FindFociProcessor.OUTPUT_MASK_PEAKS)) {
      return 1;
    }

    return 0;
  }

  private static boolean isSet(int flag, int mask) {
    return (flag & mask) == mask;
  }

  private static void writeParam(OutputStreamWriter out, String key, String value)
      throws IOException {
    out.write(key);
    out.write(" = ");
    out.write(value);
    out.write(NEW_LINE);
  }

  /** {@inheritDoc} */
  @Override
  public FindFociResults findMaxima(ImagePlus imp, ImagePlus mask, int backgroundMethod,
      double backgroundParameter, String autoThresholdMethod, int searchMethod,
      double searchParameter, int maxPeaks, int minSize, int peakMethod, double peakParameter,
      int outputType, int sortIndex, int options, double blur, int centreMethod,
      double centreParameter, double fractionParameter) {
    lastResultsArray = null;

    if (imp == null) {
      return null;
    }
    if (!isSupported(imp.getBitDepth())) {
      IJ.error(TITLE, "Only " + getSupported() + " images are supported");
      return null;
    }

    // Support int[] or float[] images using a dedicated processor
    final FindFociResults result = findMaxima(createFindFociProcessor(imp), imp, mask,
        backgroundMethod, backgroundParameter, autoThresholdMethod, searchMethod, searchParameter,
        maxPeaks, minSize, peakMethod, peakParameter, outputType, sortIndex, options, blur,
        centreMethod, centreParameter, fractionParameter);
    if (result != null) {
      lastResultsArray = result.results;
    }
    return result;
  }

  private static FindFociResults findMaxima(FindFociBaseProcessor ffp, ImagePlus imp,
      ImagePlus mask, int backgroundMethod, double backgroundParameter, String autoThresholdMethod,
      int searchMethod, double searchParameter, int maxPeaks, int minSize, int peakMethod,
      double peakParameter, int outputType, int sortIndex, int options, double blur,
      int centreMethod, double centreParameter, double fractionParameter) {
    final FindFociResults result =
        ffp.findMaxima(imp, mask, backgroundMethod, backgroundParameter, autoThresholdMethod,
            searchMethod, searchParameter, maxPeaks, minSize, peakMethod, peakParameter, outputType,
            sortIndex, options, blur, centreMethod, centreParameter, fractionParameter);
    return result;
  }

  private FindFociBaseProcessor createFindFociProcessor(ImagePlus imp) {
    final FindFociBaseProcessor ffp;
    if (isOptimisedProcessor()) {
      ffp = (imp.getBitDepth() == 32) ? new FindFociOptimisedFloatProcessor()
          : new FindFociOptimisedIntProcessor();
    } else {
      ffp = (imp.getBitDepth() == 32) ? new FindFociFloatProcessor() : new FindFociIntProcessor();
    }
    return ffp;
  }

  /**
   * Apply a Gaussian blur to the image and returns a new image. Returns the original image if
   * {@code blur <= 0}.
   *
   * <p>Only blurs the current channel and frame for use in the FindFoci algorithm.
   *
   * @param imp the image
   * @param blur The blur standard deviation
   * @return the blurred image
   */
  public static ImagePlus applyBlur(ImagePlus imp, double blur) {
    return FindFociBaseProcessor.applyBlur(imp, blur);
  }

  /** {@inheritDoc} */
  @Override
  public ImagePlus blur(ImagePlus imp, double blur) {
    // Use static method as the FindFociProcessor may be null
    return FindFociBaseProcessor.applyBlur(imp, blur);
  }

  /** {@inheritDoc} */
  @Override
  public FindFociInitResults findMaximaInit(ImagePlus originalImp, ImagePlus imp, ImagePlus mask,
      int backgroundMethod, String autoThresholdMethod, int options) {
    lastResultsArray = null;

    if (imp == null) {
      return null;
    }
    if (!isSupported(imp.getBitDepth())) {
      IJ.error(TITLE, "Only " + getSupported() + " images are supported");
      return null;
    }

    // Support int[] or float[] images using a dedicated processor
    ffpStaged = createFindFociProcessor(originalImp);

    return ffpStaged.findMaximaInit(originalImp, imp, mask, backgroundMethod, autoThresholdMethod,
        options);
  }

  /** {@inheritDoc} */
  @Override
  public FindFociInitResults copyForStagedProcessing(FindFociInitResults initResults,
      FindFociInitResults clonedInitResults) {
    return ffpStaged.copyForStagedProcessing(initResults, clonedInitResults);
  }

  /** {@inheritDoc} */
  @Override
  public FindFociSearchResults findMaximaSearch(FindFociInitResults initResults,
      int backgroundMethod, double backgroundParameter, int searchMethod, double searchParameter) {
    lastResultsArray = null;
    return ffpStaged.findMaximaSearch(initResults, backgroundMethod, backgroundParameter,
        searchMethod, searchParameter);
  }

  /** {@inheritDoc} */
  @Override
  public FindFociMergeTempResults findMaximaMergePeak(FindFociInitResults initResults,
      FindFociSearchResults searchResults, int peakMethod, double peakParameter) {
    lastResultsArray = null;
    return ffpStaged.findMaximaMergePeak(initResults, searchResults, peakMethod, peakParameter);
  }

  /** {@inheritDoc} */
  @Override
  public FindFociMergeTempResults findMaximaMergeSize(FindFociInitResults initResults,
      FindFociMergeTempResults mergeResults, int minSize) {
    lastResultsArray = null;
    return ffpStaged.findMaximaMergeSize(initResults, mergeResults, minSize);
  }

  /** {@inheritDoc} */
  @Override
  public FindFociMergeResults findMaximaMergeFinal(FindFociInitResults initResults,
      FindFociMergeTempResults mergeResults, int minSize, int options, double blur) {
    lastResultsArray = null;
    return ffpStaged.findMaximaMergeFinal(initResults, mergeResults, minSize, options, blur);
  }

  /** {@inheritDoc} */
  @Override
  public FindFociResults findMaximaResults(FindFociInitResults initResults,
      FindFociMergeResults mergeResults, int maxPeaks, int sortIndex, int centreMethod,
      double centreParameter) {
    lastResultsArray = null;
    final FindFociResults result = ffpStaged.findMaximaResults(initResults, mergeResults, maxPeaks,
        sortIndex, centreMethod, centreParameter);
    if (result != null) {
      lastResultsArray = result.results;
    }
    return result;

  }

  /** {@inheritDoc} */
  @Override
  public FindFociPrelimResults findMaximaPrelimResults(FindFociInitResults initResults,
      FindFociMergeResults mergeResults, int maxPeaks, int sortIndex, int centreMethod,
      double centreParameter) {
    lastResultsArray = null;
    final FindFociPrelimResults result = ffpStaged.findMaximaPrelimResults(initResults,
        mergeResults, maxPeaks, sortIndex, centreMethod, centreParameter);
    if (result != null) {
      lastResultsArray =
          (result.results == null) ? null : new ArrayList<>(Arrays.asList(result.results));
    }
    return result;

  }

  /** {@inheritDoc} */
  @Override
  public FindFociResults findMaximaMaskResults(FindFociInitResults initResults,
      FindFociMergeResults mergeResults, FindFociPrelimResults prelimResults, int outputType,
      String autoThresholdMethod, String imageTitle, double fractionParameter) {
    lastResultsArray = null;
    final FindFociResults result = ffpStaged.findMaximaMaskResults(initResults, mergeResults,
        prelimResults, outputType, autoThresholdMethod, imageTitle, fractionParameter);
    if (result != null) {
      lastResultsArray = result.results;
    }
    return result;
  }

  /**
   * Show the result of the FindFoci algorithm. It is assumed the results were generated using the
   * FindFoci staged methods.
   *
   * <p>The method must be called with the output from
   * {@link #findMaxima(ImagePlus, ImagePlus, int, double, String, int, double, int, int, int, double, int, int, int, double, int, double, double)}
   *
   * @param imp the image
   * @param mask A mask image used to define the region to search for peaks
   * @param backgroundMethod Method for calculating the background level (use the constants with
   *        prefix BACKGROUND_)
   * @param backgroundParameter parameter for calculating the background level
   * @param autoThresholdMethod The thresholding method (use a string from
   *        {@link uk.ac.sussex.gdsc.core.threshold.AutoThreshold#getMethods() } )
   * @param searchParameter parameter for calculating the stopping criteria
   * @param maxPeaks The maximum number of peaks to report
   * @param minSize The minimum size for a peak
   * @param peakMethod Method for calculating the minimum peak height above the highest saddle (use
   *        the constants with prefix PEAK_)
   * @param peakParameter parameter for calculating the minimum peak height
   * @param outputType Use {@link #OUTPUT_MASK_PEAKS} to get an ImagePlus in the result Object
   *        array. Use {@link #OUTPUT_LOG_MESSAGES} to get runtime information.
   * @param sortIndex The index of the result statistic to use for the peak sorting
   * @param options An options flag (use the constants with prefix OPTION_)
   * @param results the results
   */
  public void showResults(ImagePlus imp, ImagePlus mask, int backgroundMethod,
      double backgroundParameter, String autoThresholdMethod, double searchParameter, int maxPeaks,
      int minSize, int peakMethod, double peakParameter, int outputType, int sortIndex, int options,
      FindFociResults results) {
    // Get the results
    final ImagePlus maximaImp = results.mask;
    final List<FindFociResult> resultsArray = results.results;
    final FindFociStatistics stats = results.stats;

    // If we are outputting a results table or saving to file we can do the object analysis
    if ((options & OPTION_OBJECT_ANALYSIS) != 0 && ((outputType & OUTPUT_RESULTS_TABLE) != 0)) {
      // Assume ffpStaged is not null as the user has already run through the FindFoci algorithm to
      // get the results.

      final ImagePlus objectImp = ffpStaged.doObjectAnalysis(mask, maximaImp, resultsArray,
          (options & OPTION_SHOW_OBJECT_MASK) != 0, null);
      if (objectImp != null) {
        objectImp.show();
      }
    }

    if ((options & OPTION_SAVE_TO_MEMORY) != 0) {
      saveToMemory(resultsArray, imp);
    }

    // Add peaks to a results window
    if ((outputType & OUTPUT_CLEAR_RESULTS_TABLE) != 0) {
      clearResultsWindow();
    }
    if (resultsArray != null && (outputType & OUTPUT_RESULTS_TABLE) != 0) {
      // There is some strange problem when using ImageJ's default results table when it asks the
      // user
      // to save a previous list and then aborts the plugin if the user says Yes, No or Cancel
      // leaving
      // the image locked.
      // So use a custom results table instead (or just Analyzer.setUnsavedMeasurements(false)).

      // Use a custom result table to avoid IJ bug
      createResultsWindow();
      for (int i = 0; i < resultsArray.size(); i++) {
        final FindFociResult result = resultsArray.get(i);
        addToResultTable(ffpStaged, i + 1, resultsArray.size() - i, result, stats);
      }
      flushResults();
      // if (!resultsArray.isEmpty())
      // resultsWindow.append("");
    }

    // Update the mask image
    ImagePlus maxImp = null;
    Overlay overlay = null;
    if (maximaImp != null) {
      if ((outputType & OUTPUT_MASK) != 0) {
        maxImp = showImage(maximaImp, imp.getTitle() + " " + TITLE);

        // Adjust the contrast to show all the maxima
        if (resultsArray != null) {
          final int maxValue =
              ((outputType & OUTPUT_MASK_THRESHOLD) != 0) ? 4 : resultsArray.size() + 1;
          maxImp.setDisplayRange(0, maxValue);
        }

        maxImp.updateAndDraw();
      }
      if ((outputType & OUTPUT_OVERLAY_MASK) != 0) {
        overlay = createOverlay(imp, maximaImp);
      }
    }

    // Remove ROI if not an output option
    if ((outputType & OUTPUT_ROI_SELECTION) == 0) {
      killPointRoi(imp);
      killOverlayPointRoi(imp);
    }
    if ((outputType & OUTPUT_MASK_ROI_SELECTION) == 0) {
      killPointRoi(maxImp);
      killOverlayPointRoi(maxImp);
    }

    // Add ROI crosses to original image
    if (resultsArray != null
        && (outputType & (OUTPUT_ROI_SELECTION | OUTPUT_MASK_ROI_SELECTION)) != 0) {
      if (!resultsArray.isEmpty()) {
        if ((outputType & OUTPUT_ROI_USING_OVERLAY) != 0) {
          // Create an roi for each z slice
          final PointRoi[] rois = createStackRoi(resultsArray, outputType);

          if ((outputType & OUTPUT_ROI_SELECTION) != 0) {
            killPointRoi(imp);
            overlay = addRoiToOverlay(imp, rois, overlay);
          }

          if (maxImp != null && (outputType & OUTPUT_MASK_ROI_SELECTION) != 0) {
            killPointRoi(maxImp);
            addRoiToOverlay(maxImp, rois);
          }
        } else {
          final PointRoi roi = createRoi(resultsArray, outputType);

          if ((outputType & OUTPUT_ROI_SELECTION) != 0) {
            killOverlayPointRoi(imp);

            // Use an overlay so that any area ROI is preserved when previewing results
            if (imp.getRoi() != null && !(imp.getRoi() instanceof PointRoi)) {
              final Roi roi2 = (Roi) roi.clone();
              configureOverlayRoi(roi2);
              // Add to the current overlay
              if (overlay != null) {
                overlay.add(roi2);
              } else {
                overlay = new Overlay(roi2);
              }
            } else {
              imp.setRoi(roi);
            }
          }

          if (maxImp != null && (outputType & OUTPUT_MASK_ROI_SELECTION) != 0) {
            killOverlayPointRoi(maxImp);
            maxImp.setRoi(roi);
          }
        }
      } else {
        if ((outputType & OUTPUT_ROI_SELECTION) != 0) {
          killPointRoi(imp);
          killOverlayPointRoi(imp);
        }
        if ((outputType & OUTPUT_MASK_ROI_SELECTION) != 0) {
          killPointRoi(maxImp);
          killOverlayPointRoi(maxImp);
        }
      }
    }

    // Set the overlay (if null this will remove the old one)
    // if (overlay != null)
    imp.setOverlay(overlay);
  }

  /**
   * Create the result window (if it is not available).
   */
  private void createResultsWindow() {
    if (resultsWindow == null || !resultsWindow.isShowing()) {
      resultsWindow = new TextWindow(TITLE + " Results", createResultsHeader(""), "", 900, 300);
      resetResultsCount();
    }
  }

  private void resetResultsCount() {
    resultsCount = 0;
    // Set this at a level where the ij.text.TextWindow will auto-layout the columns
    flushCount = 8;
  }

  private void clearResultsWindow() {
    if (resultsWindow != null && resultsWindow.isShowing()) {
      resultsWindow.getTextPanel().clear();
      resetResultsCount();
    }
  }

  private static String createResultsHeader(String newLine) {
    return createResultsHeader(null, null, null, newLine);
  }

  private static String createResultsHeader(ImagePlus imp, int[] dimension,
      FindFociStatistics stats, String newLine) {
    final StringBuilder sb = new StringBuilder();
    if (imp != null) {
      sb.append("# Image\t").append(imp.getTitle()).append(newLine);
      if (imp.getOriginalFileInfo() != null) {
        sb.append("# File\t").append(imp.getOriginalFileInfo().directory)
            .append(imp.getOriginalFileInfo().fileName).append(newLine);
      }
      if (dimension == null) {
        dimension = new int[] {imp.getC(), 0, imp.getT()};
      }
      sb.append("# C\t").append(dimension[0]).append(newLine);
      if (dimension[1] != 0) {
        sb.append("# Z\t").append(dimension[1]).append(newLine);
      }
      sb.append("# T\t").append(dimension[2]).append(newLine);
    }
    if (stats != null) {
      sb.append("# Background\t").append(stats.background).append(newLine);
      sb.append("# Min\t").append(stats.regionMinimum).append(newLine);
      sb.append("# Max\t").append(stats.regionMaximum).append(newLine);
      sb.append("# Average\t").append(stats.regionAverage).append(newLine);
      sb.append("# Std.Dev\t").append(stats.regionStdDev).append(newLine);
    }
    sb.append("Peak #\t");
    sb.append("Mask Value\t");
    sb.append("X\t");
    sb.append("Y\t");
    sb.append("Z\t");
    sb.append("Size\t");
    sb.append("Max\t");
    sb.append("Total\t");
    sb.append("Saddle value\t");
    sb.append("Saddle Id\t");
    sb.append("Abs.Height\t");
    sb.append("Rel.Height (>Bg)\t");
    sb.append("Size > saddle\t");
    sb.append("Total > saddle\t");
    sb.append("Av\t");
    sb.append("Total (>Bg)\t");
    sb.append("Av (>Bg)\t");
    sb.append("Total (>Min)\t");
    sb.append("Av (>Min)\t");
    sb.append("% Signal\t");
    sb.append("% Signal (>Bg)\t");
    sb.append("% Signal (>Min)\t");
    sb.append("Signal / Noise\t");
    sb.append("Object\t");
    sb.append("State");
    sb.append(newLine);
    return sb.toString();
  }

  /**
   * Perform lazy initialisation on the header tab count.
   */
  private static final class HeaderTabCount {
    /**
     * Hold the header tab count in the standard header.
     */
    private static final int COUNT;

    static {
      int count = 0;
      final String header = createResultsHeader(null, null, null, NEW_LINE);
      for (int i = 0; i < header.length(); i++) {
        if (header.charAt(i) == '\t') {
          count++;
        }
      }
      COUNT = count;
    }
  }

  /**
   * Add a result to the result table.
   *
   * @param ffp the processor used to create the results
   * @param peakNumber Peak number
   * @param id the id
   * @param result The peak result
   * @param stats the stats
   */
  private void addToResultTable(FindFociBaseProcessor ffp, int peakNumber, int id,
      FindFociResult result, FindFociStatistics stats) {
    // Buffer the output so that the table is displayed faster
    buildResultEntry(ffp, resultsBuffer, peakNumber, id, result, stats, "\n");
    if (resultsCount > flushCount) {
      flushResults();
    }
  }

  private void flushResults() {
    resultsWindow.append(resultsBuffer.toString());
    resultsBuffer.setLength(0);
    // One we have allowed auto-layout of the columns do the rest at the same time
    flushCount = Integer.MAX_VALUE;
  }

  private void buildResultEntry(FindFociBaseProcessor ffp, StringBuilder sb, int peakNumber, int id,
      FindFociResult result, FindFociStatistics stats, String newLine) {
    final double sum = stats.regionTotal;
    final double noise = stats.background;

    final double absoluteHeight = ffp.getAbsoluteHeight(result, noise);
    final double relativeHeight =
        FindFociBaseProcessor.getRelativeHeight(result, noise, absoluteHeight);

    final boolean floatImage = ffp.isFloatProcessor();

    sb.append(peakNumber).append('\t');
    sb.append(id).append('\t');
    // XY are pixel coordinates
    sb.append(result.x).append('\t');
    sb.append(result.y).append('\t');
    // Z should correspond to slice
    sb.append(result.z + 1).append('\t');
    sb.append(result.count).append('\t');

    addValue(sb, result.maxValue, floatImage);
    addValue(sb, result.totalIntensity, floatImage);
    addValue(sb, result.highestSaddleValue, floatImage);

    sb.append(result.saddleNeighbourId).append('\t');
    addValue(sb, absoluteHeight, floatImage);
    addValue(sb, relativeHeight);
    sb.append(result.countAboveSaddle).append('\t');
    addValue(sb, result.intensityAboveSaddle, floatImage);
    addValue(sb, result.totalIntensity / result.count);
    addValue(sb, result.totalIntensityAboveBackground, floatImage);
    addValue(sb, result.totalIntensityAboveBackground / result.count);
    addValue(sb, result.totalIntensityAboveImageMinimum, floatImage);
    addValue(sb, result.totalIntensityAboveImageMinimum / result.count);
    addValue(sb, 100 * (result.totalIntensity / sum));
    addValue(sb, 100 * (result.totalIntensityAboveBackground / stats.totalAboveBackground));
    addValue(sb, 100 * (result.totalIntensityAboveImageMinimum / stats.totalAboveImageMinimum));
    addValue(sb, (result.maxValue / noise));
    sb.append(result.object).append('\t');
    sb.append(result.state);
    sb.append(newLine);

    resultsCount++;
  }

  private static void addValue(StringBuilder sb, double value, boolean floatImage) {
    if (floatImage) {
      addValue(sb, value);
    } else {
      sb.append((int) value).append('\t');
    }
  }

  private static void addValue(StringBuilder sb, double value) {
    sb.append(getFormat(value)).append('\t');
  }

  /**
   * Gets the formatted text for the value.
   *
   * @param value the value
   * @param floatImage Set to true for floating-point value
   * @return the text
   */
  static String getFormat(double value, boolean floatImage) {
    if (floatImage) {
      return getFormat(value);
    }
    return Integer.toString((int) value);
  }

  /**
   * Gets the formatted text for the value.
   *
   * @param value the value
   * @return the text
   */
  static String getFormat(double value) {
    if (value > 100) {
      return IJ.d2s(value, 2);
    }
    return MathUtils.rounded(value, 4);
  }

  private static String buildEmptyObjectResultEntry(int objectId, int objectState) {
    final StringBuilder sb = new StringBuilder();
    // We subtract 1 since we want to add the objectId and another tab
    for (int i = 0; i < HeaderTabCount.COUNT; i++) {
      sb.append(emptyField).append('\t');
    }
    sb.append(objectId).append('\t');
    sb.append(objectState).append(NEW_LINE);
    return sb.toString();
  }

  /**
   * Builds the empty result entry. This can be used to build.
   *
   * @return the string
   */
  private synchronized String buildEmptyResultEntry() {
    if (emptyEntry == null) {
      final StringBuilder sb = new StringBuilder();
      for (int i = 0; i < HeaderTabCount.COUNT; i++) {
        sb.append(emptyField).append('\t');
      }
      sb.append(emptyField).append(NEW_LINE);
      emptyEntry = sb.toString();
    }
    return emptyEntry;
  }

  private static String generateId(ImagePlus imp) {
    final DateFormat df = new SimpleDateFormat("-yyyyMMdd_HHmmss");
    return "FindFoci-" + imp.getShortTitle() + df.format(new Date());
  }

  /**
   * Sets the directory to record results. Text results are saved in simple text files and Point
   * ROIs in ImageJ ROI files.
   *
   * @param directory the new results directory
   */
  public void setResultsDirectory(String directory) {
    this.resultsDirectory = null;

    if (directory == null || directory.length() == 0) {
      return;
    }

    if (!new File(directory).exists()) {
      logError("The results directory does not exist. Results will not be saved.");
    } else {
      this.resultsDirectory = directory;
    }
  }

  private static void logError(String message) {
    IJ.log("ERROR - " + TITLE + ": " + message);
  }

  /**
   * Gets the results from the last call of FindFoci.
   *
   * @return The results from the last call of FindFoci.
   */
  public static List<FindFociResult> getLastResults() {
    return lastResultsArray;
  }

  /**
   * Runs a batch of FindFoci analysis. Asks for an input directory, parameter file and results
   * directory.
   */
  private void runBatchMode() {
    if (!showBatchDialog()) {
      return;
    }
    final String[] imageList = getBatchImages(batchInputDirectory);
    if (imageList == null || imageList.length == 0) {
      IJ.error(TITLE, "No input images in folder: " + batchInputDirectory);
      return;
    }
    BatchParameters parameters;
    try {
      parameters = new BatchParameters(batchParameterFile);
    } catch (final Exception ex) {
      IJ.error(TITLE, "Unable to read parameters file: " + ex.getMessage());
      return;
    }
    if ((parameters.centreMethod == CENTRE_GAUSSIAN_ORIGINAL
        || parameters.centreMethod == CENTRE_GAUSSIAN_SEARCH) && isGaussianFitEnabled < 1) {
      IJ.error(TITLE, "Gaussian fit is not currently enabled");
      return;
    }

    setResultsDirectory(batchOutputDirectory);
    final String batchFilename = openBatchResultsFile();

    if ((parameters.outputType & OUTPUT_LOG_MESSAGES) != 0) {
      IJ.log("---");
      IJ.log(TITLE + " Batch");
    }

    // Multi-threaded use can result in ImageJ objects not existing.
    // Initialise all IJ static methods we will use.
    // TODO - Check if this is complete
    IJ.d2s(0);

    final long startTime = System.nanoTime();
    batchImages = new AtomicInteger();
    batchOk = new AtomicInteger();
    batchError = new AtomicInteger();

    // Allow multi-threaded execution
    final int totalProgress = imageList.length;
    final int threadCount = MathUtils.min(Prefs.getThreads(), totalProgress);
    boolean sortResults = false;
    if (batchMultiThread && threadCount > 1) {
      final Ticker ticker = Ticker.createStarted(new ImageJTrackProgress(), totalProgress, true);
      final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
      final TurboList<Future<?>> futures = new TurboList<>(totalProgress);

      final FindFoci_PlugIn ff = this;
      for (int i = 0; i < imageList.length; i++) {
        if (ImageJUtils.isInterrupted()) {
          break;
        }
        final int batchId = i + 1;
        final String image = imageList[i];
        futures.add(executor.submit(() -> {
          final MemoryHandler handler = new MemoryHandler(new ImageJLogHandler(), 50, Level.OFF);
          final Logger logger = LoggerUtils.getUnconfiguredLogger();
          logger.addHandler(handler);
          runBatch(ff, batchId, image, parameters, logger);
          handler.push();
          ticker.tick();
        }));
      }

      sortResults = futures.size() > 1;
      executor.shutdown();

      // No need to log errors. These will bubble up to ImageJ for logging.
      ConcurrencyUtils.waitForCompletionUnchecked(futures);
    } else {
      final Ticker ticker = Ticker.createStarted(new ImageJTrackProgress(), totalProgress, false);
      for (int i = 0; i < imageList.length; i++) {
        if (ImageJUtils.isInterrupted()) {
          break;
        }
        final int batchId = i + 1;
        final String image = imageList[i];
        runBatch(this, batchId, image, parameters, null);
        ticker.tick();
      }
    }

    closeBatchResultsFile();

    if (sortResults) {
      sortBatchResultsFile(batchFilename);
    }

    final long runTime = System.nanoTime() - startTime;
    IJ.showProgress(1);
    IJ.showStatus("");

    if ((parameters.outputType & OUTPUT_LOG_MESSAGES) != 0) {
      IJ.log("---");
    }

    IJ.log(String.format("%s Batch time = %s. %s. Processed %d / %s. %s.", TITLE,
        TextUtils.nanosToString(runTime), TextUtils.pleural(totalProgress, "file"), batchOk.get(),
        TextUtils.pleural(batchImages.get(), "image"),
        TextUtils.pleural(batchError.get(), "file error")));

    if (ImageJUtils.isInterrupted()) {
      IJ.showStatus("Cancelled");
      IJ.log(TITLE + " Batch Cancelled");
    }
  }

  private boolean showBatchDialog() {
    final GenericDialog gd = new GenericDialog(TITLE);
    gd.addMessage("Run " + TITLE
        + " on a set of images.\n \nAll images in a directory will be processed.\n \n"
        + "Optional mask images in the input directory should be named:\n"
        + "[image_name].mask.[ext]\nor placed in the mask directory with the same name "
        + "as the parent image.");
    gd.addStringField("Input_directory", batchInputDirectory);
    gd.addStringField("Mask_directory", batchMaskDirectory);
    gd.addStringField("Parameter_file", batchParameterFile);
    gd.addStringField("Output_directory", batchOutputDirectory);
    gd.addCheckbox("Multi-thread", batchMultiThread);
    gd.addMessage("[Note: Double-click a text field to open a selection dialog]");
    final Vector<TextField> texts = gd.getStringFields();
    final MouseAdapter ma = new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent event) {
        if (event.getClickCount() > 1 && event.getSource() instanceof TextField) {
          // Double-click
          final TextField tf = (TextField) event.getSource();
          String path = tf.getText();
          final boolean recording = Recorder.record;
          Recorder.record = false;
          if (tf == textParamFile) {
            path = ImageJUtils.getFilename("Choose_a_parameter_file", path);
          } else {
            path = ImageJUtils.getDirectory("Choose_a_directory", path);
          }
          Recorder.record = recording;
          if (path != null) {
            tf.setText(path);
          }
        }
      }
    };
    for (final TextField tf : texts) {
      tf.addMouseListener(ma);
      tf.setColumns(50);
    }
    textParamFile = texts.get(2);

    gd.showDialog();
    if (gd.wasCanceled()) {
      return false;
    }
    batchInputDirectory = gd.getNextString();
    if (!new File(batchInputDirectory).isDirectory()) {
      IJ.error(TITLE, "Input directory is not a valid directory: " + batchInputDirectory);
      return false;
    }
    batchMaskDirectory = gd.getNextString();
    if ((batchMaskDirectory != null && batchMaskDirectory.length() > 0)
        && !new File(batchMaskDirectory).isDirectory()) {
      IJ.error(TITLE, "Mask directory is not a valid directory: " + batchMaskDirectory);
      return false;
    }
    batchParameterFile = gd.getNextString();
    if (!new File(batchParameterFile).isFile()) {
      IJ.error(TITLE, "Parameter file is not a valid file: " + batchParameterFile);
      return false;
    }
    batchOutputDirectory = gd.getNextString();
    if (!new File(batchOutputDirectory).isDirectory()) {
      IJ.error(TITLE, "Output directory is not a valid directory: " + batchOutputDirectory);
      return false;
    }
    batchMultiThread = gd.getNextBoolean();
    Prefs.set(BATCH_INPUT_DIRECTORY, batchInputDirectory);
    Prefs.set(BATCH_MASK_DIRECTORY, batchMaskDirectory);
    Prefs.set(BATCH_PARAMETER_FILE, batchParameterFile);
    Prefs.set(BATCH_OUTPUT_DIRECTORY, batchOutputDirectory);
    Prefs.set(BATCH_MULTI_THREAD, batchMultiThread);
    return true;
  }

  /**
   * Gets the batch images.
   *
   * @param directory the directory
   * @return the batch images
   */
  public static String[] getBatchImages(String directory) {
    if (directory == null) {
      return null;
    }

    // Get a list of files
    final File[] fileList = (new File(directory)).listFiles();
    if (fileList == null) {
      return null;
    }

    // Exclude directories
    String[] list = new String[fileList.length];
    int count = 0;
    for (int i = 0; i < list.length; i++) {
      if (fileList[i].isFile()) {
        list[count++] = fileList[i].getName();
      }
    }
    list = Arrays.copyOf(list, count);

    // Now exclude non-image files as per the ImageJ FolderOpener
    final FolderOpener fo = new FolderOpener();
    list = fo.trimFileList(list);
    if (list == null) {
      return null;
    }

    list = fo.sortFileList(list);

    // Now exclude mask images
    count = 0;
    for (final String name : list) {
      if (name.contains("mask.")) {
        continue;
      }
      list[count++] = name;
    }
    return Arrays.copyOf(list, count);
  }

  private static boolean runBatch(FindFoci_PlugIn ff, int batchId, String image,
      BatchParameters parameters, Logger logger) {
    IJ.showStatus(image);
    final String[] mask = getMaskImage(batchInputDirectory, batchMaskDirectory, image);

    // Open the image (and mask)
    ImagePlus imp = openImage(batchInputDirectory, image);
    if (imp == null) {
      error(ff, logger, parameters, "File is not a valid image: " + image);
      return false;
    }
    ImagePlus maskImp = openImage(mask[0], mask[1]);

    // Check if there are CZT options for the image/mask
    final int[] imageDimension = parameters.image.clone();
    final int[] maskDimension = parameters.mask.clone();
    imp = setupImage(imp, imageDimension);
    if (maskImp != null) {
      maskImp = setupImage(maskImp, maskDimension);
    }

    // Run the algorithm
    return execBatch(ff, batchId, imp, maskImp, parameters, imageDimension, maskDimension, logger);
  }

  /**
   * Simple error log to the ImageJ window instead of using IJ.error() and IJ.redirectErrorMessages
   * since the redirect flag is reset each time.
   *
   * @param ff the FindFoci instance
   * @param logger the logger
   * @param parameters the parameters
   * @param msg the msg
   */
  public static void error(FindFoci_PlugIn ff, Logger logger, BatchParameters parameters,
      String msg) {
    ff.batchError.incrementAndGet();
    if (logger != null) {
      logger.severe(() -> TITLE + " Batch: " + msg);
    } else {
      if ((parameters.outputType & OUTPUT_LOG_MESSAGES) != 0) {
        IJ.log("---");
      }
      IJ.log(TITLE + " Batch ERROR: " + msg);
    }
    Macro.abort();
  }

  /**
   * Look for the mask image for the given file. The mask will be named using the original filename
   * in directory2 or [image_name].mask.[ext] in either directory.
   *
   * @param directory the first directory
   * @param directory2 the second directory
   * @param filename the filename
   * @return array of [directory, filename]
   */
  public static String[] getMaskImage(String directory, String directory2, String filename) {
    final int index = filename.lastIndexOf('.');
    if (index > 0) {
      // Check for the mask using the original filename in the second directory
      if (new File(directory2, filename).exists()) {
        return new String[] {directory2, filename};
      }

      // Look for [image_name].mask.[ext] in either directory
      final String prefix = filename.substring(0, index);
      final String ext = filename.substring(index);
      final String maskFilename = prefix + ".mask" + ext;
      if (new File(directory, maskFilename).exists()) {
        return new String[] {directory, maskFilename};
      }
      if (new File(directory2, maskFilename).exists()) {
        return new String[] {directory2, maskFilename};
      }
    }
    return new String[2];
  }

  /**
   * Open the image.
   *
   * @param directory the directory
   * @param filename the filename
   * @return the image plus
   */
  public static ImagePlus openImage(String directory, String filename) {
    if (filename == null) {
      return null;
    }
    final Opener opener = new Opener();
    opener.setSilentMode(true);
    // TODO - Add support for loading custom channel, slice and frame using a filename suffix, e.g.
    // [cCzZtT]
    // If the suffix exists, remove it, load the image then extract the specified slices.
    return opener.openImage(directory, filename);
  }

  private static ImagePlus setupImage(ImagePlus imp, int[] dimension) {
    // For channel and frame we can just update the position
    if (dimension[BatchParameters.C] > 1 && dimension[BatchParameters.C] <= imp.getNChannels()) {
      imp.setC(dimension[BatchParameters.C]);
    }
    dimension[BatchParameters.C] = imp.getC();
    if (dimension[BatchParameters.T] > 1 && dimension[BatchParameters.T] <= imp.getNFrames()) {
      imp.setT(dimension[BatchParameters.T]);
    }
    dimension[BatchParameters.T] = imp.getT();
    // For z we extract the slice since the algorithm processes the stack from the current channel &
    // frame
    int zposition = 0;
    if (imp.getNSlices() != 1 && dimension[BatchParameters.Z] > 0
        && dimension[BatchParameters.Z] <= imp.getNSlices()) {
      imp.setZ(dimension[BatchParameters.Z]);
      final ImageProcessor ip = imp.getProcessor();
      final String title = imp.getTitle();
      imp = new ImagePlus(title, ip);
      zposition = dimension[BatchParameters.Z];
    }
    dimension[BatchParameters.Z] = zposition;
    return imp;
  }

  /**
   * Truncated version of the exec() method that saves all results to the batch output directory.
   *
   * @param ff the FindFoci instance
   * @param batchId the batch id
   * @param imp the image
   * @param mask the mask
   * @param params the parameters
   * @param imageDimension the image dimension
   * @param maskDimension the mask dimension
   * @param logger the logger
   * @return true, if successful
   */
  private static boolean execBatch(FindFoci_PlugIn ff, int batchId, ImagePlus imp, ImagePlus mask,
      BatchParameters params, int[] imageDimension, int[] maskDimension, Logger logger) {
    if (!isSupported(imp.getBitDepth())) {
      error(ff, logger, params, "Only " + getSupported() + " images are supported");
      return false;
    }

    final int options = params.options;
    final int outputType = params.outputType;

    final FindFociBaseProcessor ffp = ff.createFindFociProcessor(imp);
    ffp.setShowStatus(false);
    ffp.setLogger(logger);
    ff.batchImages.incrementAndGet();
    final FindFociResults ffResult = findMaxima(ffp, imp, mask, params.backgroundMethod,
        params.backgroundParameter, params.autoThresholdMethod, params.searchMethod,
        params.searchParameter, params.maxPeaks, params.minSize, params.peakMethod,
        params.peakParameter, outputType, params.sortIndex, options, params.blur,
        params.centreMethod, params.centreParameter, params.fractionParameter);

    if (ffResult == null) {
      return false;
    }

    ff.batchOk.incrementAndGet();

    // Get the results
    final ImagePlus maximaImp = ffResult.mask;
    final List<FindFociResult> resultsArray = ffResult.results;
    final FindFociStatistics stats = ffResult.stats;

    final String expId;
    if (params.originalTitle) {
      expId = imp.getShortTitle();
    } else {
      expId = imp.getShortTitle() + String.format("_c%dz%dt%d", imageDimension[BatchParameters.C],
          imageDimension[BatchParameters.Z], imageDimension[BatchParameters.T]);
    }

    ObjectAnalysisResult objectAnalysisResult = null;
    if ((options & OPTION_OBJECT_ANALYSIS) != 0) {
      objectAnalysisResult = ffp.new ObjectAnalysisResult();
      final ImagePlus objectImp = ffp.doObjectAnalysis(mask, maximaImp, resultsArray,
          (options & OPTION_SHOW_OBJECT_MASK) != 0, objectAnalysisResult);
      if (objectImp != null) {
        IJ.saveAsTiff(objectImp, batchOutputDirectory + File.separator + expId + ".objects.tiff");
      }
    }

    if ((options & OPTION_SAVE_TO_MEMORY) != 0) {
      saveToMemory(resultsArray, imp, imageDimension[BatchParameters.C],
          imageDimension[BatchParameters.Z], imageDimension[BatchParameters.T]);
    }

    // Record all the results to file
    final String batchPrefix = initialiseBatchPrefix(batchId, expId);
    ff.saveResults(ffp, expId, imp, imageDimension, mask, maskDimension, params.backgroundMethod,
        params.backgroundParameter, params.autoThresholdMethod, params.searchMethod,
        params.searchParameter, params.maxPeaks, params.minSize, params.peakMethod,
        params.peakParameter, outputType, params.sortIndex, options, params.blur,
        params.centreMethod, params.centreParameter, params.fractionParameter, resultsArray, stats,
        batchOutputDirectory, objectAnalysisResult, batchPrefix);

    boolean saveImp = false;

    // Update the mask image
    ImagePlus maxImp = null;
    Overlay overlay = null;
    if (maximaImp != null) {
      if ((outputType & OUTPUT_MASK) != 0) {
        final ImageStack stack = maximaImp.getStack();

        final String outname = imp.getTitle() + " " + TITLE;
        maxImp = new ImagePlus(outname, stack);
        // Adjust the contrast to show all the maxima
        final int maxValue =
            ((outputType & OUTPUT_MASK_THRESHOLD) != 0) ? 4 : resultsArray.size() + 1;
        maxImp.setDisplayRange(0, maxValue);
      }
      if ((outputType & OUTPUT_OVERLAY_MASK) != 0) {
        overlay = createOverlay(imp, maximaImp);
      }
    }

    // Add ROI crosses to original image
    if ((outputType & (OUTPUT_ROI_SELECTION | OUTPUT_MASK_ROI_SELECTION)) != 0
        && !resultsArray.isEmpty()) {
      if ((outputType & OUTPUT_ROI_USING_OVERLAY) != 0) {
        // Create an roi for each z slice
        final PointRoi[] rois = createStackRoi(resultsArray, outputType);

        if ((outputType & OUTPUT_ROI_SELECTION) != 0) {
          overlay = addRoiToOverlay(imp, rois, overlay);
        }

        if (maxImp != null && (outputType & OUTPUT_MASK_ROI_SELECTION) != 0) {
          addRoiToOverlay(maxImp, rois);
        }
      } else {
        final PointRoi roi = createRoi(resultsArray, outputType);

        if ((outputType & OUTPUT_ROI_SELECTION) != 0) {
          imp.setRoi(roi);
          saveImp = true;
        }

        if (maxImp != null && (outputType & OUTPUT_MASK_ROI_SELECTION) != 0) {
          maxImp.setRoi(roi);
        }
      }
    }

    if (overlay != null) {
      imp.setOverlay(overlay);
      saveImp = true;
    }

    if (saveImp) {
      IJ.saveAsTiff(imp, batchOutputDirectory + File.separator + expId + ".tiff");
    }
    if (maxImp != null) {
      IJ.saveAsTiff(maxImp, batchOutputDirectory + File.separator + expId + ".mask.tiff");
    }

    return true;
  }

  /**
   * Save the results array to memory using the image name and current channel and frame.
   *
   * @param resultsArray the results array
   * @param imp the image
   */
  private static void saveToMemory(List<FindFociResult> resultsArray, ImagePlus imp) {
    saveToMemory(resultsArray, imp, imp.getChannel(), 0, imp.getFrame());
  }

  /**
   * Save the results array to memory using the image name and current channel and frame.
   *
   * @param resultsArray the results array
   * @param imp the image
   * @param channel the channel
   * @param zposition the z position
   * @param frame the frame
   */
  private static void saveToMemory(List<FindFociResult> resultsArray, ImagePlus imp, int channel,
      int zposition, int frame) {
    if (resultsArray == null) {
      return;
    }
    String name;
    // If we use a specific slice then add this to the name
    if (zposition != 0) {
      name = String.format("%s (c%d,z%d,t%d)", imp.getTitle(), channel, zposition, frame);
    } else {
      name = String.format("%s (c%d,t%d)", imp.getTitle(), channel, frame);
    }
    // Check if there was nothing stored at the key position and store the name.
    // This allows memory results to be listed in order.
    memory.put(name, new FindFociMemoryResults(imp, resultsArray));
  }

  /**
   * Get a list of the names of the results that are stored in memory.
   *
   * @return a list of results names
   */
  public static String[] getResultsNames() {
    final String[] names = new String[memory.size()];
    int index = 0;
    for (final String name : memory.keySet()) {
      names[index++] = name;
    }
    return names;
  }

  /**
   * Get set of results corresponding to the name.
   *
   * @param name The name of the results.
   * @return The results (or null if none exist)
   */
  public static FindFociMemoryResults getResults(String name) {
    return memory.get(name);
  }

  private boolean showSettingsDialog() {
    final GenericDialog gd = new GenericDialog(TITLE + " Settings");
    //@formatter:off
    gd.addMessage("Set the maximum number of potential maxima for the " + TITLE + " algorithm.\n" +
        "Increasing this number can allow processing large images (which may be slow).\n" +
        "The number of potential maxima can be reduced by smoothing the image (e.g\n" +
        "using a Gaussian blur).\n" +
        "Note: The default is the legacy value for 16-bit signed integers.\n" +
        "The maximum value supported is " + Integer.MAX_VALUE);
    gd.addNumericField("Capacity", searchCapacity, 0);
    gd.addMessage("Set the record to use when recording no results during batch processing.\n" +
        "(A record is always written to the results file for each batch image,\n" +
        "even when no foci are found; this value will be used for all the fields\n" +
        "in the empty record.)");
    gd.addStringField("Empty_Field", emptyField);

    gd.addMessage("Settings are saved when you exit ImageJ.");
    //@formatter:on

    gd.showDialog();
    if (gd.wasCanceled()) {
      return false;
    }

    final double d = Math.abs(gd.getNextNumber());
    if (d > Integer.MAX_VALUE) {
      return false;
    }
    searchCapacity = Math.max(1, (int) d);

    emptyField = gd.getNextString();
    if (emptyField == null) {
      emptyField = "";
    }

    // Reset this so it will be initialised again
    emptyEntry = null;

    Prefs.set(SEARCH_CAPACITY, searchCapacity);
    Prefs.set(EMPTY_FIELD, emptyField);

    return true;
  }

  /**
   * Checks the bit depth is supported.
   *
   * @param bitDepth the bit depth
   * @return true, if is supported
   */
  public static boolean isSupported(int bitDepth) {
    return bitDepth == 8 || bitDepth == 16 || bitDepth == 32;
  }

  /**
   * Get the supported images as a text output.
   *
   * @return A text output of the supported images
   */
  public static String getSupported() {
    return "8-bit, 16-bit and 32-bit";
  }

  /**
   * Checks if using an optimised FindFociProcessor.
   *
   * @return True if using an optimised FindFociProcessor (default is generic).
   */
  public boolean isOptimisedProcessor() {
    return optimisedProcessor;
  }

  /**
   * Set to true to use an optimised FindFociProcessor (default is generic).
   *
   * @param optimisedProcessor True if using an optimised FindFociProcessor (default is generic)
   */
  public void setOptimisedProcessor(boolean optimisedProcessor) {
    this.optimisedProcessor = optimisedProcessor;
  }
}