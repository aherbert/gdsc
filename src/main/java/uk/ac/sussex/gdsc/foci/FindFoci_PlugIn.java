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
import uk.ac.sussex.gdsc.core.annotation.Nullable;
import uk.ac.sussex.gdsc.core.ij.BufferedTextWindow;
import uk.ac.sussex.gdsc.core.ij.ImageJLogHandler;
import uk.ac.sussex.gdsc.core.ij.ImageJPluginLoggerHelper;
import uk.ac.sussex.gdsc.core.ij.ImageJTrackProgress;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.ij.gui.ExtendedGenericDialog;
import uk.ac.sussex.gdsc.core.logging.LoggerUtils;
import uk.ac.sussex.gdsc.core.logging.Ticker;
import uk.ac.sussex.gdsc.core.utils.CollectionUtils;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.core.utils.MemoryUtils;
import uk.ac.sussex.gdsc.core.utils.TextUtils;
import uk.ac.sussex.gdsc.core.utils.TurboList;
import uk.ac.sussex.gdsc.core.utils.ValidationUtils;
import uk.ac.sussex.gdsc.core.utils.concurrent.ConcurrencyUtils;
import uk.ac.sussex.gdsc.foci.FindFociBaseProcessor.ObjectAnalysisResult;
import uk.ac.sussex.gdsc.foci.FindFociOptions.OutputOption;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.AlgorithmOption;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.BackgroundMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.CentreMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.MaskMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.PeakMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.SearchMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.SortMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.StatisticsMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.ThresholdMethod;
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
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
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
public class FindFoci_PlugIn implements PlugIn {
  /** Get the title of the ImageJ plugin. */
  public static final String TITLE = "FindFoci";

  /** The supported ImageJ images as a text output. */
  public static final String SUPPORTED_BIT_DEPTH = "8-bit, 16-bit and 32-bit";

  /** The option name for the mask. */
  public static final String OPTION_MASK = "Mask";
  /** The option name for the background method. */
  public static final String OPTION_BACKGROUND_METHOD = "Background_method";
  /** The option name for the background parameter. */
  public static final String OPTION_BACKGROUND_PARAMETER = "Background_parameter";
  /** The option name for the auto threshold method. */
  public static final String OPTION_AUTO_THRESHOLD = "Auto_threshold";
  /** The option name for the statistics mode. */
  public static final String OPTION_STASTISTICS_MODE = "Statistics_mode";
  /** The option name for the search method. */
  public static final String OPTION_SEARCH_METHOD = "Search_method";
  /** The option name for the search parameter. */
  public static final String OPTION_SEARCH_PARAMETER = "Search_parameter";
  /** The option name for the minimum size. */
  public static final String OPTION_MINIMUM_SIZE = "Minimum_size";
  /** The option name for the minimum above saddle. */
  public static final String OPTION_MINIMUM_SIZE_ABOVE_SADDLE = "Minimum_above_saddle";
  /** The option name for the connected above saddle. */
  public static final String OPTION_CONNECTED_ABOVE_SADDLE = "Connected_above_saddle";
  /** The option name for the minimum peak height. */
  public static final String OPTION_MINIMUM_PEAK_HEIGHT = "Minimum_peak_height";
  /** The option name for the peak parameter. */
  public static final String OPTION_PEAK_PARAMETER = "Peak_parameter";
  /** The option name for the sort method. */
  public static final String OPTION_SORT_METHOD = "Sort_method";
  /** The option name for the maximum peaks. */
  public static final String OPTION_MAXIMUM_PEAKS = "Maximum_peaks";
  /** The option name for the show mask. */
  public static final String OPTION_SHOW_MASK = "Show_mask";
  /** The option name for the overlay mask. */
  public static final String OPTION_OVERLAY_MASK = "Overlay_mask";
  /** The option name for the fraction parameter. */
  public static final String OPTION_FRACTION_PARAMETER = "Fraction_parameter";
  /** The option name for the show table. */
  public static final String OPTION_SHOW_TABLE = "Show_table";
  /** The option name for the clear table. */
  public static final String OPTION_CLEAR_TABLE = "Clear_table";
  /** The option name for the mark maxima. */
  public static final String OPTION_MARK_MAXIMA = "Mark_maxima";
  /** The option name for the mark peak maxima. */
  public static final String OPTION_MARK_PEAK_MAXIMA = "Mark_peak_maxima";
  /** The option name for the mark using overlay. */
  public static final String OPTION_MARK_USING_OVERLAY = "Mark_using_overlay";
  /** The option name for the hide labels. */
  public static final String OPTION_HIDE_LABELS = "Hide_labels";
  /** The option name for the show peak maxima as dots. */
  public static final String OPTION_SHOW_PEAK_MAXIMA_AS_DOTS = "Show_peak_maxima_as_dots";
  /** The option name for the show log messages. */
  public static final String OPTION_SHOW_LOG_MESSAGES = "Show_log_messages";
  /** The option name for the remove edge maxima. */
  public static final String OPTION_REMOVE_EDGE_MAXIMA = "Remove_edge_maxima";
  /** The option name for the results directory. */
  public static final String OPTION_RESULTS_DIRECTORY = "Results_directory";
  /** The option name for the Gaussian blur. */
  public static final String OPTION_GAUSSIAN_BLUR = "Gaussian_blur";
  /** The option name for the centre metho. */
  public static final String OPTION_CENTRE_METHOD = "Centre_method";
  /** The option name for the centre parameter. */
  public static final String OPTION_CENTRE_PARAMETER = "Centre_parameter";
  /** The option name for the object analysis. */
  public static final String OPTION_OBJECT_ANALYSIS = "Object_analysis";
  /** The option name for the show object mask. */
  public static final String OPTION_SHOW_OBJECT_MASK = "Show_object_mask";
  /** The option name for the save to memory. */
  public static final String OPTION_SAVE_TO_MEMORY = "Save_to_memory";

  /** A single reference to the last results window. */
  private static final AtomicReference<TextWindow> resultsWindow = new AtomicReference<>();

  /** A reference to the results of the last computation. */
  private static final AtomicReference<List<FindFociResult>> lastResultsArray =
      new AtomicReference<>();

  /**
   * Set to true if the Gaussian fit option is enabled. This requires the GDSC SMLM library to be
   * available.
   */
  static final int IS_GAUSSIAN_FIT_ENABLED;

  /** The new line string from System.getProperty("line.separator"). */
  private static final String NEW_LINE = System.lineSeparator();

  /**
   * The available centre methods. Some methods require additional libraries so this is created at
   * run-time.
   */
  private static final String[] centreMethods;

  static {
    IS_GAUSSIAN_FIT_ENABLED = (GaussianFit_PlugIn.isFittingEnabled()) ? 1 : -1;
    if (IS_GAUSSIAN_FIT_ENABLED < 1) {
      // This relies on the 2 Gaussian methods being at the end of the enum
      final String[] descriptions = CentreMethod.getDescriptions();
      centreMethods = Arrays.copyOf(descriptions, descriptions.length - 2);
      // Debug the reason why fitting is disabled
      if (IJ.shiftKeyDown()) {
        IJ.log(
            "Gaussian fitting is not enabled:" + NEW_LINE + GaussianFit_PlugIn.getErrorMessage());
      }
    } else {
      centreMethods = CentreMethod.getDescriptions();
    }
  }

  /**
   * Allow the results to be stored in memory for other plugins to access.
   *
   * <p>Access and modification should be synchronized.
   */
  private static LinkedHashMap<String, FindFociMemoryResults> memory = new LinkedHashMap<>();

  /** Flag set to true if using an optimised FindFociProcessor. */
  private boolean optimisedProcessor = true;

  /**
   * Lazy load the logger for the plugin.
   */
  private static class LoggerLoader {
    /** A default logger configured for the plugin. */
    static final Logger logger;

    static {
      // Redirect console logging to the ImageJ log
      logger = ImageJPluginLoggerHelper.getLogger(FindFoci_PlugIn.class);
    }
  }

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    final FindFociOptions options;
    final FindFociProcessorOptions processorOptions;
    String maskImage;
    boolean showLogMessages;

    Settings() {
      // Set defaults
      options = new FindFociOptions();
      processorOptions = new FindFociProcessorOptions();
      maskImage = "";
      showLogMessages = true;
    }

    Settings(Settings source) {
      options = source.options.copy();
      processorOptions = source.processorOptions.copy();
      maskImage = source.maskImage;
      showLogMessages = source.showLogMessages;
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
   * Contains the global settings of the FindFoci algorithm.
   *
   * <p>There is a single instance controlling atomic access to the settings.
   */
  private static class GlobalSettings {
    private static final String KEY_SEARCH_CAPACITY = "findfoci.searchCapacity";
    private static final String KEY_EMPTY_FIELD = "findfoci.emptyField";

    private static final GlobalSettings INSTANCE = new GlobalSettings();

    /**
     * The search capacity. This is the maximum number of potential maxima for the algorithm. The
     * default value for legacy reasons is {@value Short#MAX_VALUE}.
     */
    AtomicInteger searchCapacity =
        new AtomicInteger((int) Prefs.get(KEY_SEARCH_CAPACITY, Short.MAX_VALUE));

    /** The empty field to use in results files. */
    AtomicReference<String> emptyField = new AtomicReference<>(Prefs.get(KEY_EMPTY_FIELD, ""));

    /**
     * Default constructor.
     */
    private GlobalSettings() {
      // Do nothing
    }

    /**
     * Save the settings.
     */
    void save() {
      Prefs.set(KEY_SEARCH_CAPACITY, searchCapacity.get());
      Prefs.set(KEY_EMPTY_FIELD, emptyField.get());
    }
  }

  /**
   * Contains the settings that are the re-usable state of the plugin in match mode.
   */
  private static class BatchSettings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<BatchSettings> lastSettings =
        new AtomicReference<>(new BatchSettings());

    private static final String KEY_BATCH_INPUT_DIRECTORY = "findfoci.batchInputDirectory";
    private static final String KEY_BATCH_MASK_DIRECTORY = "findfoci.batchMaskDirectory";
    private static final String KEY_BATCH_PARAMETER_FILE = "findfoci.batchParameterFile";
    private static final String KEY_BATCH_OUTPUT_DIRECTORY = "findfoci.batchOutputDirectory";
    private static final String KEY_BATCH_MULTI_THREAD = "findfoci.batchMultiThread";
    private static final String KEY_BATCH_SHOW_LOG_MESSAGES = "findfoci.batchShowLogMessages";

    String inputDirectory;
    String maskDirectory;
    String parameterFile;
    String outputDirectory;
    boolean multiThread;
    boolean showLogMessages;

    /**
     * Default constructor.
     */
    BatchSettings() {
      // Set defaults
      inputDirectory = Prefs.get(KEY_BATCH_INPUT_DIRECTORY, "");
      maskDirectory = Prefs.get(KEY_BATCH_MASK_DIRECTORY, "");
      parameterFile = Prefs.get(KEY_BATCH_PARAMETER_FILE, "");
      outputDirectory = Prefs.get(KEY_BATCH_OUTPUT_DIRECTORY, "");
      multiThread = Prefs.get(KEY_BATCH_MULTI_THREAD, true);
      showLogMessages = Prefs.get(KEY_BATCH_SHOW_LOG_MESSAGES, true);
    }

    /**
     * Copy constructor.
     *
     * @param source the source
     */
    private BatchSettings(BatchSettings source) {
      inputDirectory = source.inputDirectory;
      maskDirectory = source.maskDirectory;
      parameterFile = source.parameterFile;
      outputDirectory = source.outputDirectory;
      multiThread = source.multiThread;
      showLogMessages = source.showLogMessages;
    }

    /**
     * Copy the settings.
     *
     * @return the settings
     */
    BatchSettings copy() {
      return new BatchSettings(this);
    }

    /**
     * Load a copy of the settings.
     *
     * @return the settings
     */
    static BatchSettings load() {
      return lastSettings.get().copy();
    }

    /**
     * Save the settings.
     */
    void save() {
      lastSettings.set(this);
      // Store in preferences for next time
      Prefs.set(KEY_BATCH_INPUT_DIRECTORY, inputDirectory);
      Prefs.set(KEY_BATCH_MASK_DIRECTORY, maskDirectory);
      Prefs.set(KEY_BATCH_PARAMETER_FILE, parameterFile);
      Prefs.set(KEY_BATCH_OUTPUT_DIRECTORY, outputDirectory);
      Prefs.set(KEY_BATCH_MULTI_THREAD, multiThread);
      Prefs.set(KEY_BATCH_SHOW_LOG_MESSAGES, showLogMessages);
    }
  }

  private static class BatchConfiguration {
    // Incremented by child batch processes
    AtomicInteger batchImages = new AtomicInteger();
    AtomicInteger batchOk = new AtomicInteger();
    AtomicInteger batchError = new AtomicInteger();

    /** Used to record all the results into a single file during batch analysis. */
    private BufferedWriter allOut;
    /**
     * The empty entry builder. Used to record no results in the batch results file.
     */
    private final EmptyResultBuilder emptyResultBuilder =
        new EmptyResultBuilder(GlobalSettings.INSTANCE.emptyField.get());

    private final String batchOutputDirectory;

    BatchConfiguration(String batchOutputDirectory) {
      this.batchOutputDirectory = batchOutputDirectory;
    }

    /**
     * Log the message to the logger. Increment the error counter.
     *
     * @param logger the logger
     * @param parameters the parameters
     * @param msg the msg
     */
    void error(Logger logger, BatchParameters parameters, String msg) {
      batchError.incrementAndGet();
      if (logger != null) {
        logger.severe(() -> TITLE + " Batch: " + msg);
      }
      Macro.abort();
    }

    synchronized boolean openBatchResultsFile() {
      final Path path = Paths.get(batchOutputDirectory, "all.xls");
      try {
        allOut = Files.newBufferedWriter(path);
        allOut.write("Image ID\tImage\t");
        allOut.write(createResultsHeader(NEW_LINE));
        return true;
      } catch (final Exception ex) {
        logError(ex.getMessage());
        closeBatchResultsFile();
        return false;
      }
    }

    synchronized void closeBatchResultsFile() {
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

    void sortBatchResultsFile() {
      final Path path = Paths.get(batchOutputDirectory, "all.xls");
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
        // Try and free the memory
        MemoryUtils.runGarbageCollectorOnce();
        logError(ex.getMessage());
        return;
      } catch (final Exception ex) {
        logError(ex.getMessage());
        return;
      }

      Collections.sort(results, BatchResult::compare);

      try (final BufferedWriter out = Files.newBufferedWriter(path)) {
        // Note: Add new lines because BufferedReader strips them
        if (header != null) {
          out.write(header);
          out.newLine();
        }
        for (final BatchResult r : results) {
          out.write(r.entry);
          out.newLine();
        }
      } catch (final Exception ex) {
        logError(ex.getMessage());
      }
    }

    static String initialiseBatchPrefix(int batchId, String title) {
      return batchId + "\t" + title + "\t";
    }

    /**
     * Add empty records for empty objects to the batch results.
     *
     * @param batchResults the batch results
     * @param objectAnalysisResult the object analysis result
     */
    void addEmptyObjectsToBatchResults(ArrayList<String> batchResults,
        ObjectAnalysisResult objectAnalysisResult) {
      for (int id = 1; id <= objectAnalysisResult.numberOfObjects; id++) {
        if (objectAnalysisResult.fociCount[id] == 0) {
          batchResults.add(emptyResultBuilder.getEmptyObjectResultEntry(id,
              objectAnalysisResult.objectState[id]));
        }
      }
    }

    /**
     * Adds an empty record for batch processing.
     *
     * @param batchResults the batch results
     */
    void addEmptyBatchResults(ArrayList<String> batchResults) {
      batchResults.add(emptyResultBuilder.getEmptyResultEntry());
    }

    /**
     * Write the batch results to file.
     *
     * @param batchPrefix the batch prefix (used a the line prefix for each record in the file)
     * @param batchResults the batch results
     */
    synchronized void writeBatchResultsFile(String batchPrefix, List<String> batchResults) {
      if (allOut == null) {
        return;
      }
      try {
        for (final String result : batchResults) {
          allOut.write(batchPrefix);
          allOut.write(result);
        }
      } catch (final Exception ex) {
        logError(ex.getMessage());
        closeBatchResultsFile();
      }
    }

  }

  private static class BatchParameters {
    static final int C = 0;
    static final int Z = 1;
    static final int T = 2;

    String parameterOptions;
    Map<String, String> map;

    FindFociProcessorOptions processorOptions = new FindFociProcessorOptions();
    FindFociOptions options = new FindFociOptions();

    int[] image = new int[3];
    int[] mask = new int[3];
    boolean originalTitle;

    BatchParameters(String filename) throws IOException {
      readParameters(filename);

      // Read all the parameters
      processorOptions.setBackgroundMethod(
          findEnum(OPTION_BACKGROUND_METHOD, BackgroundMethod::fromDescription));
      processorOptions.setBackgroundParameter(findDouble(OPTION_BACKGROUND_PARAMETER));
      processorOptions
          .setThresholdMethod(findEnum(OPTION_AUTO_THRESHOLD, ThresholdMethod::fromDescription));
      processorOptions.setStatisticsMethod(
          findEnum(OPTION_STASTISTICS_MODE, StatisticsMethod::fromDescription));
      processorOptions
          .setSearchMethod(findEnum(OPTION_SEARCH_METHOD, SearchMethod::fromDescription));
      processorOptions.setSearchParameter(findDouble(OPTION_SEARCH_PARAMETER));
      processorOptions.setMinSize(findInteger(OPTION_MINIMUM_SIZE));
      processorOptions.setOption(AlgorithmOption.MINIMUM_ABOVE_SADDLE,
          findBoolean(OPTION_MINIMUM_SIZE_ABOVE_SADDLE));
      processorOptions.setOption(AlgorithmOption.CONTIGUOUS_ABOVE_SADDLE,
          findBoolean(OPTION_CONNECTED_ABOVE_SADDLE));
      processorOptions
          .setPeakMethod(findEnum(OPTION_MINIMUM_PEAK_HEIGHT, PeakMethod::fromDescription));
      processorOptions.setPeakParameter(findDouble(OPTION_PEAK_PARAMETER));
      processorOptions.setSortMethod(findEnum(OPTION_SORT_METHOD, SortMethod::fromDescription));
      processorOptions.setMaxPeaks(findInteger(OPTION_MAXIMUM_PEAKS));
      processorOptions.setMaskMethod(findEnum(OPTION_SHOW_MASK, MaskMethod::fromDescription));
      processorOptions.setFractionParameter(findDouble(OPTION_FRACTION_PARAMETER));
      options.setOption(OutputOption.ROI_SELECTION, findBoolean(OPTION_MARK_MAXIMA));
      options.setOption(OutputOption.MASK_ROI_SELECTION, findBoolean(OPTION_MARK_PEAK_MAXIMA));
      options.setOption(OutputOption.ROI_USING_OVERLAY, findBoolean(OPTION_MARK_USING_OVERLAY));
      options.setOption(OutputOption.HIDE_LABELS, findBoolean(OPTION_HIDE_LABELS));
      processorOptions.setOption(AlgorithmOption.OUTPUT_MASK_PEAK_DOTS,
          findBoolean(OPTION_SHOW_PEAK_MAXIMA_AS_DOTS));
      processorOptions.setOption(AlgorithmOption.REMOVE_EDGE_MAXIMA,
          findBoolean(OPTION_REMOVE_EDGE_MAXIMA));
      processorOptions.setGaussianBlur(findDouble(OPTION_GAUSSIAN_BLUR));
      processorOptions
          .setCentreMethod(findEnum(OPTION_CENTRE_METHOD, CentreMethod::fromDescription));
      processorOptions.setCentreParameter(findDouble(OPTION_CENTRE_PARAMETER));
      options.setOption(OutputOption.OBJECT_ANALYSIS, findBoolean(OPTION_OBJECT_ANALYSIS));
      options.setOption(OutputOption.SHOW_OBJECT_MASK, findBoolean(OPTION_SHOW_OBJECT_MASK));
      options.setOption(OutputOption.SAVE_TO_MEMORY, findBoolean(OPTION_SAVE_TO_MEMORY));

      image[C] = findInteger("Image_C", 1);
      image[Z] = findInteger("Image_Z", 0);
      image[T] = findInteger("Image_T", 1);
      mask[C] = findInteger("Mask_C", 1);
      mask[Z] = findInteger("Mask_Z", 0);
      mask[T] = findInteger("Mask_T", 1);
      originalTitle = findBoolean("Original_Title");
    }

    private void readParameters(String filename) throws IOException {
      final ArrayList<String> parameters = new ArrayList<>();
      try (final BufferedReader input = Files.newBufferedReader(Paths.get(filename))) {
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
          final String key = line.substring(0, index).trim().toLowerCase(Locale.US);
          final String value = line.substring(index + 1).trim();
          map.put(key, value);
        }
      }
    }

    private String findString(String key) {
      String value;
      key = key.toLowerCase(Locale.US);
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
      key = key.toLowerCase(Locale.US);
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
        return isMatch(parameterOptions, key.toLowerCase(Locale.US) + " ");
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

    private <T> T findEnum(String key, Function<String, T> convertor) {
      final String value = findString(key);
      final T result = convertor.apply(value);
      if (result == null) {
        throw new IllegalArgumentException("Missing index for option: " + key + "=" + value);
      }
      return result;
    }
  }

  private static class BatchResult {
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

    static int compare(BatchResult r1, BatchResult r2) {
      final int result = Integer.compare(r1.batchId, r2.batchId);
      return (result == 0) ? 0 : Integer.compare(r1.id, r2.id);
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
   * Encapsulate the functionality to create empty records for the results file.
   */
  private static class EmptyResultBuilder {
    /**
     * The empty result entry. Used to record no results in the batch results file.
     */
    private String emptyResultEntry;
    /**
     * The empty object result entry prefix. Used to record no results in the batch results file but
     * with an object Id and state.
     */
    private String emptyObjectResultEntryPrefix;

    /** The empty field. */
    private final String emptyField;

    EmptyResultBuilder(String emptyField) {
      this.emptyField = emptyField;
    }

    String getEmptyResultEntry() {
      // Lazy initialise the entry
      String entry = emptyResultEntry;
      if (entry == null) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < HeaderTabCount.COUNT; i++) {
          sb.append(emptyField).append('\t');
        }
        sb.append(emptyField).append(NEW_LINE);
        emptyResultEntry = entry = sb.toString();
      }
      return entry;
    }

    String getEmptyObjectResultEntry(int objectId, int objectState) {
      // Lazy initialise the prefix
      String prefix = emptyObjectResultEntryPrefix;
      if (prefix == null) {
        final StringBuilder sb = new StringBuilder();
        // We start at 1 since we want to add the objectId and another tab
        for (int i = 1; i < HeaderTabCount.COUNT; i++) {
          sb.append(emptyField).append('\t');
        }
        emptyObjectResultEntryPrefix = prefix = sb.toString();
      }

      final StringBuilder sb = new StringBuilder(prefix);
      sb.append(objectId).append('\t');
      sb.append(objectState).append(NEW_LINE);
      return sb.toString();
    }
  }

  /**
   * List of methods for defining the centre of each peak.
   *
   * @return the centre methods
   */
  public static String[] getCentreMethods() {
    return centreMethods.clone();
  }

  /** Ask for parameters and then execute. */
  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    if ("batch".equals(arg)) {
      runBatchMode();
      return;
    }

    if ("settings".equals(arg)) {
      showSettingsDialog();
      return;
    }

    final ImagePlus imp = WindowManager.getCurrentImage();

    if (null == imp) {
      IJ.error(TITLE, "There must be at least one image open");
      return;
    }

    if (!isSupported(imp.getBitDepth())) {
      IJ.error(TITLE, "Only " + SUPPORTED_BIT_DEPTH + " images are supported");
      return;
    }

    // Build a list of the open images
    final List<String> newImageList = buildMaskList(imp);

    final ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);

    final Settings settings = Settings.load();
    final FindFociProcessorOptions processorOptions = settings.processorOptions;
    final FindFociOptions options = settings.options;

    gd.addChoice(OPTION_MASK, newImageList.toArray(new String[newImageList.size()]),
        settings.maskImage);
    gd.addMessage("Background options ...");
    gd.addChoice(OPTION_BACKGROUND_METHOD, BackgroundMethod.getDescriptions(),
        processorOptions.getBackgroundMethod().ordinal());
    gd.addNumericField(OPTION_BACKGROUND_PARAMETER, processorOptions.getBackgroundParameter(), 0);
    gd.addChoice(OPTION_AUTO_THRESHOLD, ThresholdMethod.getDescriptions(),
        processorOptions.getThresholdMethod().ordinal());
    gd.addChoice(OPTION_STASTISTICS_MODE, StatisticsMethod.getDescriptions(),
        processorOptions.getStatisticsMethod().ordinal());
    gd.addMessage("Search options ...");
    gd.addChoice(OPTION_SEARCH_METHOD, SearchMethod.getDescriptions(),
        processorOptions.getSearchMethod().ordinal());
    gd.addNumericField(OPTION_SEARCH_PARAMETER, processorOptions.getSearchParameter(), 2);
    gd.addMessage("Merge options ...");
    gd.addChoice(OPTION_MINIMUM_PEAK_HEIGHT, PeakMethod.getDescriptions(),
        processorOptions.getPeakMethod().ordinal());
    gd.addNumericField(OPTION_PEAK_PARAMETER, processorOptions.getPeakParameter(), 2);
    gd.addNumericField(OPTION_MINIMUM_SIZE, processorOptions.getMinSize(), 0);
    gd.addCheckbox(OPTION_MINIMUM_SIZE_ABOVE_SADDLE,
        processorOptions.isOption(AlgorithmOption.MINIMUM_ABOVE_SADDLE));
    gd.addCheckbox(OPTION_CONNECTED_ABOVE_SADDLE,
        processorOptions.isOption(AlgorithmOption.CONTIGUOUS_ABOVE_SADDLE));
    gd.addMessage("Results options ...");
    gd.addChoice(OPTION_SORT_METHOD, SortMethod.getDescriptions(),
        processorOptions.getSortMethod().ordinal());
    gd.addNumericField(OPTION_MAXIMUM_PEAKS, processorOptions.getMaxPeaks(), 0);
    gd.addChoice(OPTION_SHOW_MASK, MaskMethod.getDescriptions(),
        processorOptions.getMaskMethod().ordinal());
    gd.addCheckbox(OPTION_OVERLAY_MASK, options.isOption(OutputOption.OVERLAY_MASK));
    gd.addSlider(OPTION_FRACTION_PARAMETER, 0.05, 1, processorOptions.getFractionParameter());
    gd.addCheckbox(OPTION_SHOW_TABLE, options.isOption(OutputOption.RESULTS_TABLE));
    gd.addCheckbox(OPTION_CLEAR_TABLE, options.isOption(OutputOption.CLEAR_RESULTS_TABLE));
    gd.addCheckbox(OPTION_MARK_MAXIMA, options.isOption(OutputOption.ROI_SELECTION));
    gd.addCheckbox(OPTION_MARK_PEAK_MAXIMA, options.isOption(OutputOption.MASK_ROI_SELECTION));
    gd.addCheckbox(OPTION_MARK_USING_OVERLAY, options.isOption(OutputOption.ROI_USING_OVERLAY));
    gd.addCheckbox(OPTION_HIDE_LABELS, options.isOption(OutputOption.HIDE_LABELS));
    gd.addCheckbox(OPTION_SHOW_PEAK_MAXIMA_AS_DOTS,
        processorOptions.isOption(AlgorithmOption.OUTPUT_MASK_PEAK_DOTS));
    gd.addCheckbox(OPTION_SHOW_LOG_MESSAGES, settings.showLogMessages);
    gd.addCheckbox(OPTION_REMOVE_EDGE_MAXIMA,
        processorOptions.isOption(AlgorithmOption.REMOVE_EDGE_MAXIMA));
    gd.addDirectoryField(OPTION_RESULTS_DIRECTORY, options.getResultsDirectory(), 30);
    gd.addCheckbox(OPTION_OBJECT_ANALYSIS, options.isOption(OutputOption.OBJECT_ANALYSIS));
    gd.addCheckbox(OPTION_SHOW_OBJECT_MASK, options.isOption(OutputOption.SHOW_OBJECT_MASK));
    gd.addCheckbox(OPTION_SAVE_TO_MEMORY, options.isOption(OutputOption.SAVE_TO_MEMORY));
    gd.addMessage("Advanced options ...");
    gd.addNumericField(OPTION_GAUSSIAN_BLUR, processorOptions.getGaussianBlur(), 1);
    gd.addChoice(OPTION_CENTRE_METHOD, centreMethods, processorOptions.getCentreMethod().ordinal());
    gd.addNumericField(OPTION_CENTRE_PARAMETER, processorOptions.getCentreParameter(), 0);
    gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.FIND_FOCI);

    gd.showDialog();
    if (gd.wasCanceled()) {
      return;
    }

    settings.maskImage = gd.getNextChoice();
    processorOptions.setBackgroundMethod(BackgroundMethod.fromOrdinal(gd.getNextChoiceIndex()));
    processorOptions.setBackgroundParameter(gd.getNextNumber());
    processorOptions.setThresholdMethod(ThresholdMethod.fromOrdinal(gd.getNextChoiceIndex()));
    processorOptions.setStatisticsMethod(StatisticsMethod.fromOrdinal(gd.getNextChoiceIndex()));
    processorOptions.setSearchMethod(SearchMethod.fromOrdinal(gd.getNextChoiceIndex()));
    processorOptions.setSearchParameter(gd.getNextNumber());
    processorOptions.setPeakMethod(PeakMethod.fromOrdinal(gd.getNextChoiceIndex()));
    processorOptions.setPeakParameter(gd.getNextNumber());
    processorOptions.setMinSize((int) gd.getNextNumber());
    processorOptions.setOption(AlgorithmOption.MINIMUM_ABOVE_SADDLE, gd.getNextBoolean());
    processorOptions.setOption(AlgorithmOption.CONTIGUOUS_ABOVE_SADDLE, gd.getNextBoolean());
    processorOptions.setSortMethod(SortMethod.fromOrdinal(gd.getNextChoiceIndex()));
    processorOptions.setMaxPeaks((int) gd.getNextNumber());
    processorOptions.setMaskMethod(MaskMethod.fromOrdinal(gd.getNextChoiceIndex()));
    options.setOption(OutputOption.OVERLAY_MASK, gd.getNextBoolean());
    processorOptions.setFractionParameter(gd.getNextNumber());
    options.setOption(OutputOption.RESULTS_TABLE, gd.getNextBoolean());
    options.setOption(OutputOption.CLEAR_RESULTS_TABLE, gd.getNextBoolean());
    options.setOption(OutputOption.ROI_SELECTION, gd.getNextBoolean());
    options.setOption(OutputOption.MASK_ROI_SELECTION, gd.getNextBoolean());
    options.setOption(OutputOption.ROI_USING_OVERLAY, gd.getNextBoolean());
    options.setOption(OutputOption.HIDE_LABELS, gd.getNextBoolean());
    processorOptions.setOption(AlgorithmOption.OUTPUT_MASK_PEAK_DOTS, gd.getNextBoolean());
    settings.showLogMessages = gd.getNextBoolean();
    processorOptions.setOption(AlgorithmOption.REMOVE_EDGE_MAXIMA, gd.getNextBoolean());
    options.setResultsDirectory(checkResultsDirectory(gd.getNextString()));
    options.setOption(OutputOption.OBJECT_ANALYSIS, gd.getNextBoolean());
    options.setOption(OutputOption.SHOW_OBJECT_MASK, gd.getNextBoolean());
    options.setOption(OutputOption.SAVE_TO_MEMORY, gd.getNextBoolean());
    processorOptions.setGaussianBlur(gd.getNextNumber());
    processorOptions.setCentreMethod(CentreMethod.fromOrdinal(gd.getNextChoiceIndex()));
    processorOptions.setCentreParameter(gd.getNextNumber());

    // Only perform object analysis if necessary
    if (options.isOption(OutputOption.OBJECT_ANALYSIS)
        // Must have a results table or results directory
        && !(options.isOption(OutputOption.RESULTS_TABLE)
            || options.getResultsDirectory() != null)) {
      options.setOption(OutputOption.OBJECT_ANALYSIS, false);
    }

    settings.save();

    if (options.getOptions().isEmpty()) {
      IJ.error(TITLE, "No results options chosen");
      return;
    }

    // Process plugin specific settings
    final ImagePlus mask = WindowManager.getImage(settings.maskImage);

    exec(imp, mask, processorOptions, options, settings.showLogMessages);
  }

  /**
   * Build a list of all the images with the correct dimensions to be used as a mask for the
   * specified image.
   *
   * @param imp the image
   * @return the array list
   */
  public static List<String> buildMaskList(ImagePlus imp) {
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
   * <p>Parameters are as described in
   * {@link FindFociProcessor#findMaxima(ImagePlus, ImagePlus, FindFociProcessorOptions)}.
   * Additional output processing is controlled using the {@link FindFociOptions} parameter.
   *
   * @param imp the image
   * @param mask A mask image used to define the region to search for peaks
   * @param processorOptions the processor options
   * @param options the options
   * @param showLogMessages Set to true to show log messages
   */
  public void exec(ImagePlus imp, ImagePlus mask, FindFociProcessorOptions processorOptions,
      FindFociOptions options, boolean showLogMessages) {
    final Logger logger = showLogMessages ? LoggerLoader.logger : null;
    exec(imp, mask, processorOptions, options, logger);
  }

  /**
   * Perform peak finding.
   *
   * <p>Parameters are as described in
   * {@link FindFociProcessor#findMaxima(ImagePlus, ImagePlus, FindFociProcessorOptions)}.
   * Additional output processing is controlled using the {@link FindFociOptions} parameter.
   *
   * @param imp the image
   * @param mask A mask image used to define the region to search for peaks
   * @param processorOptions the processor options
   * @param options the options
   * @param logger the logger
   */
  public void exec(ImagePlus imp, ImagePlus mask, FindFociProcessorOptions processorOptions,
      FindFociOptions options, Logger logger) {
    lastResultsArray.set(null);

    if (!isSupported(imp.getBitDepth())) {
      IJ.error(TITLE, "Only " + SUPPORTED_BIT_DEPTH + " images are supported");
      return;
    }
    if (EnumSet.of(CentreMethod.GAUSSIAN_ORIGINAL, CentreMethod.GAUSSIAN_SEARCH)
        .contains(processorOptions.getCentreMethod()) && IS_GAUSSIAN_FIT_ENABLED < 1) {
      IJ.error(TITLE, "Gaussian fit is not currently enabled");
      return;
    }

    // Ensure the ROI is reset if it is a point selection
    if (options.isOption(OutputOption.ROI_SELECTION)) {
      final Roi roi = imp.getRoi();
      imp.saveRoi(); // save previous selection so user can restore it
      if (roi != null) {
        if (roi.isArea()) {
          if (!options.isOption(OutputOption.ROI_USING_OVERLAY)) {
            // YesNoCancelDialog causes asynchronous thread exception within Eclipse.
            final GenericDialog d = new GenericDialog(TITLE);
            d.addMessage("Warning: Marking the maxima will destroy the ROI area.\nUse the "
                + OPTION_MARK_USING_OVERLAY + " option to preserve the ROI.\n \n"
                + "Click OK to continue (destroys the area ROI)");
            d.showDialog();
            if (!d.wasOKed()) {
              return;
            }
          }
        } else {
          // Remove any non-area ROI to reset the bounding rectangle
          imp.killRoi();
        }
      }

      // The image may have a point ROI overlay added by the showResults(...) method called by the
      // preview functionality of the FindFoci GUI
      killOverlayPointRoi(imp);
    }

    final FindFociBaseProcessor ffp = createFindFociProcessor(imp);
    ffp.setLogger(logger);
    final FindFociResults ffResult = ffp.findMaxima(imp, mask, processorOptions);

    if (ffResult == null) {
      IJ.showStatus("Cancelled.");
      return;
    }
    lastResultsArray.set(ffResult.results);

    // Note: Do not skip saving the results.
    // This option is for preview using the staged processor.
    showResults(imp, mask, processorOptions, options, ffp, ffResult, false);
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

  private static PointRoi[] createStackRoi(List<FindFociResult> resultsArray,
      boolean optionHideLabels) {
    final int nMaxima = resultsArray.size();
    final XyzData[] xyz = new XyzData[nMaxima];
    for (int i = 0; i < nMaxima; i++) {
      final FindFociResult xy = resultsArray.get(i);
      xyz[i] = new XyzData(i + 1, xy.x, xy.y, xy.z);
    }

    Arrays.sort(xyz, (o1, o2) -> Integer.compare(o1.z, o2.z));

    final boolean hideLabels = optionHideLabels || nMaxima < 2;

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
      roi.setShowLabels(true);
      roi.setLabels(ids);
    }
    configureOverlayRoi(roi);

    // This is only applicable to single z stack images.
    // We should call setPosition(int,int,int) for hyperstacks
    roi.setPosition(slice);

    return roi;
  }

  private static PointRoi createRoi(List<FindFociResult> resultsArray, boolean hideLabels) {
    final int nMaxima = resultsArray.size();
    final int[] xpoints = new int[nMaxima];
    final int[] ypoints = new int[nMaxima];
    for (int i = 0; i < nMaxima; i++) {
      final FindFociResult xy = resultsArray.get(i);
      xpoints[i] = xy.x;
      ypoints[i] = xy.y;
    }
    final PointRoi roi = new PointRoi(xpoints, ypoints, nMaxima);
    roi.setShowLabels(!hideLabels);
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

  private static String saveResults(FindFociBaseProcessor ffp, String expId, ImagePlus imp,
      ImagePlus mask, FindFociProcessorOptions processorOptions, FindFociOptions options,
      List<FindFociResult> resultsArray, FindFociStatistics stats) {
    return saveResults(ffp, expId, imp, null, mask, null, processorOptions, options, resultsArray,
        stats, null, 0, null);
  }

  private static String saveResults(FindFociBaseProcessor ffp, String expId, ImagePlus imp,
      int[] imageDimension, ImagePlus mask, int[] maskDimension,
      FindFociProcessorOptions processorOptions, FindFociOptions options,
      List<FindFociResult> resultsArray, FindFociStatistics stats,
      ObjectAnalysisResult objectAnalysisResult, int batchId, BatchConfiguration batchConfig) {
    final String resultsDirectory = options.getResultsDirectory();
    try (final BufferedWriter out =
        Files.newBufferedWriter(Paths.get(resultsDirectory, expId + ".xls"))) {
      // Save results to file
      if (imageDimension == null) {
        imageDimension = new int[] {imp.getC(), 0, imp.getT()};
      }
      out.write(createResultsHeader(imp, imageDimension, stats, NEW_LINE));
      final int[] xpoints = new int[resultsArray.size()];
      final int[] ypoints = new int[resultsArray.size()];
      final StringBuilder sb = new StringBuilder();
      final ArrayList<String> batchResults =
          (batchConfig != null) ? new ArrayList<>(resultsArray.size()) : null;

      for (int i = 0; i < resultsArray.size(); i++) {
        final FindFociResult result = resultsArray.get(i);
        xpoints[i] = result.x;
        ypoints[i] = result.y;

        final String resultEntry =
            buildResultEntry(ffp, sb, i + 1, resultsArray.size() - i, result, stats, NEW_LINE);
        out.write(resultEntry);
        if (batchResults != null) {
          batchResults.add(resultEntry);
        }
      }

      // Check if we have a batch file
      if (batchConfig != null) {
        if (objectAnalysisResult != null) {
          batchConfig.addEmptyObjectsToBatchResults(batchResults, objectAnalysisResult);
        } else if (resultsArray.isEmpty()) {
          batchConfig.addEmptyBatchResults(batchResults);
        }

        final String batchPrefix = BatchConfiguration.initialiseBatchPrefix(batchId, expId);
        batchConfig.writeBatchResultsFile(batchPrefix, batchResults);
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
          mask, maskDimension, processorOptions, options);

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
   * @param processorOptions the processor options
   * @param options the options
   * @return True if the parameters were saved
   */
  static boolean saveParameters(String filename, ImagePlus imp, int[] imageDimension,
      ImagePlus mask, int[] maskDimension, FindFociProcessorOptions processorOptions,
      FindFociOptions options) {
    try (final BufferedWriter out = Files.newBufferedWriter(Paths.get(filename))) {
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
        writeParam(out, OPTION_MASK, mask.getTitle());
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
      writeParam(out, OPTION_BACKGROUND_METHOD,
          processorOptions.getBackgroundMethod().getDescription());
      writeParam(out, OPTION_BACKGROUND_PARAMETER,
          Double.toString(processorOptions.getBackgroundParameter()));
      writeParam(out, OPTION_AUTO_THRESHOLD,
          processorOptions.getThresholdMethod().getDescription());
      writeParam(out, OPTION_STASTISTICS_MODE,
          processorOptions.getStatisticsMethod().getDescription());
      writeParam(out, OPTION_SEARCH_METHOD, processorOptions.getSearchMethod().getDescription());
      writeParam(out, OPTION_SEARCH_PARAMETER,
          Double.toString(processorOptions.getSearchParameter()));
      writeParam(out, OPTION_MINIMUM_SIZE, Integer.toString(processorOptions.getMinSize()));
      writeParam(out, OPTION_MINIMUM_SIZE_ABOVE_SADDLE,
          processorOptions.isOption(AlgorithmOption.MINIMUM_ABOVE_SADDLE));
      writeParam(out, OPTION_CONNECTED_ABOVE_SADDLE,
          processorOptions.isOption(AlgorithmOption.CONTIGUOUS_ABOVE_SADDLE));
      writeParam(out, OPTION_MINIMUM_PEAK_HEIGHT,
          processorOptions.getPeakMethod().getDescription());
      writeParam(out, OPTION_PEAK_PARAMETER, Double.toString(processorOptions.getPeakParameter()));
      writeParam(out, OPTION_SORT_METHOD, processorOptions.getSortMethod().getDescription());
      writeParam(out, OPTION_MAXIMUM_PEAKS, Integer.toString(processorOptions.getMaxPeaks()));
      writeParam(out, OPTION_SHOW_MASK, processorOptions.getMaskMethod().getDescription());
      writeParam(out, OPTION_OVERLAY_MASK, options.isOption(OutputOption.OVERLAY_MASK));
      writeParam(out, OPTION_FRACTION_PARAMETER,
          Double.toString(processorOptions.getFractionParameter()));
      writeParam(out, OPTION_SHOW_TABLE, options.isOption(OutputOption.RESULTS_TABLE));
      writeParam(out, OPTION_CLEAR_TABLE, options.isOption(OutputOption.CLEAR_RESULTS_TABLE));
      writeParam(out, OPTION_MARK_MAXIMA, options.isOption(OutputOption.ROI_SELECTION));
      writeParam(out, OPTION_MARK_PEAK_MAXIMA, options.isOption(OutputOption.MASK_ROI_SELECTION));
      writeParam(out, OPTION_MARK_USING_OVERLAY, options.isOption(OutputOption.ROI_USING_OVERLAY));
      writeParam(out, OPTION_HIDE_LABELS, options.isOption(OutputOption.HIDE_LABELS));
      writeParam(out, OPTION_SHOW_PEAK_MAXIMA_AS_DOTS,
          processorOptions.isOption(AlgorithmOption.OUTPUT_MASK_PEAK_DOTS));
      writeParam(out, OPTION_REMOVE_EDGE_MAXIMA,
          processorOptions.isOption(AlgorithmOption.REMOVE_EDGE_MAXIMA));
      writeParam(out, OPTION_RESULTS_DIRECTORY, options.getResultsDirectory());
      writeParam(out, OPTION_OBJECT_ANALYSIS, options.isOption(OutputOption.OBJECT_ANALYSIS));
      writeParam(out, OPTION_SHOW_OBJECT_MASK, options.isOption(OutputOption.SHOW_OBJECT_MASK));
      writeParam(out, OPTION_SAVE_TO_MEMORY, options.isOption(OutputOption.SAVE_TO_MEMORY));
      writeParam(out, OPTION_GAUSSIAN_BLUR, Double.toString(processorOptions.getGaussianBlur()));
      writeParam(out, OPTION_CENTRE_METHOD, processorOptions.getCentreMethod().getDescription());
      writeParam(out, OPTION_CENTRE_PARAMETER,
          Double.toString(processorOptions.getCentreParameter()));
      return true;
    } catch (final IOException ex) {
      logError(ex.getMessage());
    }
    return false;
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
        if (rois[i] instanceof PointRoi && rois[i].getStrokeColor() == Color.CYAN
            && rois[i].getFillColor() == Color.YELLOW) {
          continue;
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

  private static void writeParam(BufferedWriter out, String key, boolean value) throws IOException {
    writeParam(out, key, value ? "true" : "false");
  }

  private static void writeParam(BufferedWriter out, String key, String value) throws IOException {
    out.write(key);
    out.write(" = ");
    out.write(value);
    out.write(NEW_LINE);
  }

  /**
   * Creates the find foci processor for the image.
   *
   * @param imp the image
   * @return the find foci base processor
   * @throws IllegalArgumentException If the image bit-depth is not supported
   */
  public FindFociBaseProcessor createFindFociProcessor(ImagePlus imp) {
    ValidationUtils.checkArgument(isSupported(imp.getBitDepth()),
        "Only " + SUPPORTED_BIT_DEPTH + " images are supported");
    return createFindFociProcessor(imp, GlobalSettings.INSTANCE.searchCapacity.get());
  }

  private FindFociBaseProcessor createFindFociProcessor(ImagePlus imp, int searchCapacity) {
    if (imp.getBitDepth() == 32) {
      return new FindFociFloatProcessor(searchCapacity);
    }
    return (isOptimisedProcessor()) ? new FindFociOptimisedIntProcessor(searchCapacity)
        : new FindFociIntProcessor(searchCapacity);
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

  /**
   * Show the result of the FindFoci algorithm. It is assumed the results were generated using the
   * provided processor.
   *
   * <p>The method must be called with the output from
   * {@link FindFociProcessor#findMaxima(ImagePlus, ImagePlus, FindFociProcessorOptions)}.
   *
   * @param imp the image
   * @param mask A mask image used to define the region to search for peaks
   * @param processorOptions the processor options
   * @param options An options flag (use the constants with prefix OPTION_)
   * @param ffp the FindFoci processor
   * @param results the results
   * @param skipSave Set to true to skip saving to the results directory
   */
  public static void showResults(ImagePlus imp, ImagePlus mask,
      FindFociProcessorOptions processorOptions, FindFociOptions options, FindFociBaseProcessor ffp,
      FindFociResults results, boolean skipSave) {
    // Get the results
    final ImagePlus maximaImp = results.mask;
    final List<FindFociResult> resultsArray = results.results;
    final FindFociStatistics stats = results.stats;

    // If we are outputting a results table or saving to file we can do the object analysis
    if (options.isOption(OutputOption.OBJECT_ANALYSIS)
        && (options.isOption(OutputOption.RESULTS_TABLE)
            || options.getResultsDirectory() != null)) {
      final ImagePlus objectImp = ffp.doObjectAnalysis(mask, maximaImp, resultsArray,
          options.isOption(OutputOption.SHOW_OBJECT_MASK), null);
      if (objectImp != null) {
        objectImp.show();
      }
    }

    if (options.isOption(OutputOption.SAVE_TO_MEMORY)) {
      saveToMemory(resultsArray, imp);
    }

    // Add peaks to a results window
    if (options.isOption(OutputOption.CLEAR_RESULTS_TABLE)) {
      clearResultsWindow();
    }
    if (resultsArray != null && options.isOption(OutputOption.RESULTS_TABLE)) {
      final BufferedTextWindow window = createResultsWindow();
      final StringBuilder sb = new StringBuilder();
      for (int i = 0; i < resultsArray.size(); i++) {
        final FindFociResult result = resultsArray.get(i);
        window
            .append(buildResultEntry(ffp, sb, i + 1, resultsArray.size() - i, result, stats, "\n"));
      }
      window.flush();
    }

    // Record all the results to file
    if (!skipSave && resultsArray != null && options.getResultsDirectory() != null) {
      saveResults(ffp, generateId(imp), imp, mask, processorOptions, options, resultsArray, stats);
    }

    // Update the mask image
    ImagePlus maxImp = null;
    Overlay overlay = null;
    if (maximaImp != null) {
      if (processorOptions.getMaskMethod() != MaskMethod.NONE) {
        maxImp = showImage(maximaImp, imp.getTitle() + " " + TITLE);

        // Adjust the contrast to show all the maxima
        if (resultsArray != null) {
          final int maxValue = EnumSet.of(MaskMethod.THRESHOLD, MaskMethod.THRESHOLD_ABOVE_SADDLE)
              .contains(processorOptions.getMaskMethod()) ? 4 : resultsArray.size() + 1;
          maxImp.setDisplayRange(0, maxValue);
        }

        maxImp.updateAndDraw();
      }
      if (options.isOption(OutputOption.OVERLAY_MASK)) {
        overlay = createOverlay(imp, maximaImp);
      }
    }

    // Remove ROI if not an output option
    if (!options.isOption(OutputOption.ROI_SELECTION)) {
      killPointRoi(imp);
      killOverlayPointRoi(imp);
    }
    if (!options.isOption(OutputOption.MASK_ROI_SELECTION)) {
      killPointRoi(maxImp);
      killOverlayPointRoi(maxImp);
    }

    // Add ROI crosses to original image
    if (resultsArray != null && CollectionUtils.containsAny(options.getOptions(),
        OutputOption.ROI_SELECTION, OutputOption.MASK_ROI_SELECTION)) {
      if (!resultsArray.isEmpty()) {
        if (options.isOption(OutputOption.ROI_USING_OVERLAY)) {
          // Create an roi for each z slice
          final PointRoi[] rois =
              createStackRoi(resultsArray, options.isOption(OutputOption.HIDE_LABELS));

          if (options.isOption(OutputOption.ROI_SELECTION)) {
            killPointRoi(imp);
            overlay = addRoiToOverlay(imp, rois, overlay);
          }

          if (maxImp != null && options.isOption(OutputOption.MASK_ROI_SELECTION)) {
            killPointRoi(maxImp);
            addRoiToOverlay(maxImp, rois);
          }
        } else {
          final PointRoi roi = createRoi(resultsArray, options.isOption(OutputOption.HIDE_LABELS));

          if (options.isOption(OutputOption.ROI_SELECTION)) {
            killOverlayPointRoi(imp);
            imp.setRoi(roi);
          }

          if (maxImp != null && options.isOption(OutputOption.MASK_ROI_SELECTION)) {
            killOverlayPointRoi(maxImp);
            maxImp.setRoi(roi);
          }
        }
      } else {
        if (options.isOption(OutputOption.ROI_SELECTION)) {
          killPointRoi(imp);
          killOverlayPointRoi(imp);
        }
        if (options.isOption(OutputOption.MASK_ROI_SELECTION)) {
          killPointRoi(maxImp);
          killOverlayPointRoi(maxImp);
        }
      }
    }

    imp.setOverlay(overlay);
  }

  /**
   * Create the result window (if it is not available).
   *
   * @return the buffered text window
   */
  private static BufferedTextWindow createResultsWindow() {
    TextWindow window = resultsWindow.get();
    if (window == null || !window.isShowing()) {
      window = new TextWindow(TITLE + " Results", createResultsHeader(""), "", 900, 300);
      resultsWindow.set(window);
    }
    final BufferedTextWindow bufferedTextWindow = new BufferedTextWindow(window);
    // Only flush once
    bufferedTextWindow.setIncrement(0);
    return bufferedTextWindow;
  }

  /**
   * Clear the results window.
   */
  private static void clearResultsWindow() {
    final TextWindow window = resultsWindow.get();
    if (window != null && window.isShowing()) {
      window.getTextPanel().clear();
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

  private static String buildResultEntry(FindFociBaseProcessor ffp, StringBuilder sb,
      int peakNumber, int id, FindFociResult result, FindFociStatistics stats, String newLine) {
    final double sum = stats.regionTotal;
    final double noise = stats.background;

    final double absoluteHeight = ffp.getAbsoluteHeight(result, noise);
    final double relativeHeight =
        FindFociBaseProcessor.getRelativeHeight(result, noise, absoluteHeight);

    final boolean floatImage = ffp.isFloatProcessor();

    sb.setLength(0);
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
    return sb.toString();
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

  private static String generateId(ImagePlus imp) {
    final DateFormat df = new SimpleDateFormat("-yyyyMMdd_HHmmss");
    return "FindFoci-" + imp.getShortTitle() + df.format(new Date());
  }

  /**
   * Check the results directory exists, or return null.
   *
   * <p>Log an error if a non-empty directory path is not a directory.
   *
   * @param directory the directory
   * @return the directory (or null)
   */
  private static String checkResultsDirectory(String directory) {
    if (TextUtils.isNotEmpty(directory)) {
      if (new File(directory).isDirectory()) {
        logError("The results directory does not exist. Results will not be saved.");
      } else {
        return directory;
      }
    }
    return null;
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
    final List<FindFociResult> results = lastResultsArray.get();
    return (results != null) ? new ArrayList<>(results) : null;
  }

  /**
   * Runs a batch of FindFoci analysis. Asks for an input directory, parameter file and results
   * directory.
   */
  private void runBatchMode() {
    final BatchSettings batchSettings = BatchSettings.load();
    if (!showBatchDialog(batchSettings)) {
      return;
    }
    final String[] imageList = getBatchImages(batchSettings.inputDirectory);
    if (imageList == null || imageList.length == 0) {
      IJ.error(TITLE, "No input images in folder: " + batchSettings.inputDirectory);
      return;
    }
    BatchParameters parameters;
    try {
      parameters = new BatchParameters(batchSettings.parameterFile);
    } catch (final Exception ex) {
      IJ.error(TITLE, "Unable to read parameters file: " + ex.getMessage());
      return;
    }
    final FindFociProcessorOptions processorOptions = parameters.processorOptions;
    if (EnumSet.of(CentreMethod.GAUSSIAN_ORIGINAL, CentreMethod.GAUSSIAN_SEARCH)
        .contains(processorOptions.getCentreMethod()) && IS_GAUSSIAN_FIT_ENABLED < 1) {
      IJ.error(TITLE, "Gaussian fit is not currently enabled");
      return;
    }

    // Initialise batch configuration
    final BatchConfiguration config = new BatchConfiguration(batchSettings.outputDirectory);
    if (!config.openBatchResultsFile()) {
      return;
    }

    final Logger logger = batchSettings.showLogMessages ? LoggerLoader.logger : null;
    if (logger != null) {
      logger.info("---");
      logger.info(TITLE + " Batch");
    }

    final long startTime = System.nanoTime();

    // Allow multi-threaded execution
    final int totalProgress = imageList.length;
    final int threadCount = MathUtils.min(Prefs.getThreads(), totalProgress);
    boolean sortResults = false;
    // Fixed search capacity for the entire batch
    final int searchCapacity = GlobalSettings.INSTANCE.searchCapacity.get();
    final Function<ImagePlus, FindFociBaseProcessor> imageConverter =
        image -> createFindFociProcessor(image, searchCapacity);
    if (batchSettings.multiThread && threadCount > 1) {
      final Ticker ticker = Ticker.createStarted(new ImageJTrackProgress(), totalProgress, true);
      final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
      final TurboList<Future<?>> futures = new TurboList<>(totalProgress);

      for (int i = 0; i < imageList.length; i++) {
        if (ImageJUtils.isInterrupted()) {
          break;
        }
        final int batchId = i + 1;
        final String image = imageList[i];
        futures.add(executor.submit(() -> {
          // Save all messages from the batch run to memory then push to the output log
          final MemoryHandler handler = new MemoryHandler(new ImageJLogHandler(), 50, Level.OFF);
          final Logger localLogger = LoggerUtils.getUnconfiguredLogger();
          localLogger.addHandler(handler);
          runBatch(imageConverter, batchSettings, config, batchId, image, parameters, localLogger);
          handler.push();
          ticker.tick();
        }));
      }

      sortResults = futures.size() > 1;
      executor.shutdown();

      try {
        // No need to log errors. These will bubble up to ImageJ for logging.
        ConcurrencyUtils.waitForCompletionUnchecked(futures);
      } finally {
        config.closeBatchResultsFile();
      }
    } else {
      final Ticker ticker = Ticker.createStarted(new ImageJTrackProgress(), totalProgress, false);
      for (int i = 0; i < imageList.length; i++) {
        if (ImageJUtils.isInterrupted()) {
          break;
        }
        runBatch(imageConverter, batchSettings, config, i + 1, imageList[i], parameters, null);
        ticker.tick();
      }
      config.closeBatchResultsFile();
    }

    if (sortResults) {
      config.sortBatchResultsFile();
    }

    final long runTime = System.nanoTime() - startTime;
    IJ.showProgress(1);
    IJ.showStatus("");

    if (logger != null) {
      logger.info("---");
    }

    IJ.log(String.format("%s Batch time = %s. %s. Processed %d / %s. %s.", TITLE,
        TextUtils.nanosToString(runTime), TextUtils.pleural(totalProgress, "file"),
        config.batchOk.get(), TextUtils.pleural(config.batchImages.get(), "image"),
        TextUtils.pleural(config.batchError.get(), "file error")));

    if (ImageJUtils.isInterrupted()) {
      IJ.showStatus("Cancelled");
      IJ.log(TITLE + " Batch Cancelled");
    }
  }

  private static boolean showBatchDialog(BatchSettings batchSettings) {
    final ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);
    gd.addMessage("Run " + TITLE
        + " on a set of images.\n \nAll images in a directory will be processed.\n \n"
        + "Optional mask images in the input directory should be named:\n"
        + "[image_name].mask.[ext]\nor placed in the mask directory with the same name "
        + "as the parent image.");
    final int columns = 50;
    gd.addDirectoryField("Input_directory", batchSettings.inputDirectory, columns);
    gd.addDirectoryField("Mask_directory", batchSettings.maskDirectory, columns);
    gd.addFilenameField("Parameter_file", batchSettings.parameterFile, columns);
    gd.addDirectoryField("Output_directory", batchSettings.outputDirectory, columns);
    gd.addCheckbox("Multi-thread", batchSettings.multiThread);
    gd.addCheckbox(OPTION_SHOW_LOG_MESSAGES, batchSettings.showLogMessages);

    gd.showDialog();
    if (gd.wasCanceled()) {
      return false;
    }
    batchSettings.inputDirectory = gd.getNextString();
    batchSettings.maskDirectory = gd.getNextString();
    batchSettings.parameterFile = gd.getNextString();
    batchSettings.outputDirectory = gd.getNextString();
    batchSettings.multiThread = gd.getNextBoolean();
    batchSettings.showLogMessages = gd.getNextBoolean();
    batchSettings.save();

    // Validation
    if (new File(batchSettings.inputDirectory).isDirectory()) {
      IJ.error(TITLE, "Input directory is not a valid directory: " + batchSettings.inputDirectory);
      return false;
    }
    if (TextUtils.isNotEmpty(batchSettings.maskDirectory)
        && new File(batchSettings.maskDirectory).isDirectory()) {
      IJ.error(TITLE, "Mask directory is not a valid directory: " + batchSettings.maskDirectory);
      return false;
    }
    if (new File(batchSettings.parameterFile).isFile()) {
      IJ.error(TITLE, "Parameter file is not a valid file: " + batchSettings.parameterFile);
      return false;
    }
    if (new File(batchSettings.outputDirectory).isDirectory()) {
      IJ.error(TITLE,
          "Output directory is not a valid directory: " + batchSettings.outputDirectory);
      return false;
    }

    return true;
  }

  /**
   * Gets the batch images.
   *
   * @param directory the directory
   * @return the batch images
   */
  public static @Nullable String[] getBatchImages(String directory) {
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

  private static boolean runBatch(Function<ImagePlus, FindFociBaseProcessor> processorSupplier,
      BatchSettings batchSettings, BatchConfiguration config, int batchId, String image,
      BatchParameters parameters, Logger logger) {
    IJ.showStatus(image);
    final String[] mask =
        getMaskImage(batchSettings.inputDirectory, batchSettings.maskDirectory, image);

    // Open the image (and mask)
    ImagePlus imp = openImage(batchSettings.inputDirectory, image);
    if (imp == null) {
      config.error(logger, parameters, "File is not a valid image: " + image);
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
    return execBatch(processorSupplier, batchSettings, config, batchId, imp, maskImp, parameters,
        imageDimension, maskDimension, logger);
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
    // TODO - Add support for loading custom channel, slice and frame using a filename suffix,
    // e.g. [cCzZtT]
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
   * Truncated version of the
   * {@link #exec(ImagePlus, ImagePlus, FindFociProcessorOptions, FindFociOptions, Logger)} method
   * that saves all results to the batch output directory.
   *
   * @param processorSupplier the processor supplier
   * @param config the batch config
   * @param batchId the batch id
   * @param imp the image
   * @param mask the mask
   * @param params the parameters
   * @param imageDimension the image dimension
   * @param maskDimension the mask dimension
   * @param logger the logger
   * @return true, if successful
   */
  private static boolean execBatch(Function<ImagePlus, FindFociBaseProcessor> processorSupplier,
      BatchSettings batchSettings, BatchConfiguration config, int batchId, ImagePlus imp,
      ImagePlus mask, BatchParameters params, int[] imageDimension, int[] maskDimension,
      Logger logger) {
    if (!isSupported(imp.getBitDepth())) {
      config.error(logger, params, "Only " + SUPPORTED_BIT_DEPTH + " images are supported");
      return false;
    }

    final FindFociBaseProcessor ffp = processorSupplier.apply(imp);
    ffp.setShowStatus(false);
    ffp.setLogger(logger);
    config.batchImages.incrementAndGet();
    final FindFociProcessorOptions processorOptions = params.processorOptions;
    final FindFociResults ffResult = ffp.findMaxima(imp, mask, processorOptions);

    if (ffResult == null) {
      return false;
    }

    config.batchOk.incrementAndGet();

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

    final FindFociOptions options = params.options;
    ObjectAnalysisResult objectAnalysisResult = null;
    if (options.isOption(OutputOption.OBJECT_ANALYSIS)) {
      objectAnalysisResult = new ObjectAnalysisResult();
      final ImagePlus objectImp = ffp.doObjectAnalysis(mask, maximaImp, resultsArray,
          options.isOption(OutputOption.SHOW_OBJECT_MASK), objectAnalysisResult);
      if (objectImp != null) {
        IJ.saveAsTiff(objectImp,
            batchSettings.outputDirectory + File.separator + expId + ".objects.tiff");
      }
    }

    if (options.isOption(OutputOption.SAVE_TO_MEMORY)) {
      saveToMemory(resultsArray, imp, imageDimension[BatchParameters.C],
          imageDimension[BatchParameters.Z], imageDimension[BatchParameters.T]);
    }

    // Record all the results to file
    options.setResultsDirectory(batchSettings.outputDirectory);
    FindFoci_PlugIn.saveResults(ffp, expId, imp, imageDimension, mask, maskDimension,
        processorOptions, options, resultsArray, stats, objectAnalysisResult, batchId, config);

    boolean saveImp = false;

    // Update the mask image
    ImagePlus maxImp = null;
    Overlay overlay = null;
    if (maximaImp != null) {
      if (processorOptions.getMaskMethod() != MaskMethod.NONE) {
        final ImageStack stack = maximaImp.getStack();

        final String outname = imp.getTitle() + " " + TITLE;
        maxImp = new ImagePlus(outname, stack);
        // Adjust the contrast to show all the maxima
        final int maxValue = EnumSet.of(MaskMethod.THRESHOLD, MaskMethod.THRESHOLD_ABOVE_SADDLE)
            .contains(processorOptions.getMaskMethod()) ? 4 : resultsArray.size() + 1;
        maxImp.setDisplayRange(0, maxValue);
      }
      if (options.isOption(OutputOption.OVERLAY_MASK)) {
        overlay = createOverlay(imp, maximaImp);
      }
    }

    // Add ROI crosses to original image
    if (resultsArray != null && CollectionUtils.containsAny(options.getOptions(),
        OutputOption.ROI_SELECTION, OutputOption.MASK_ROI_SELECTION)) {
      if (options.isOption(OutputOption.ROI_USING_OVERLAY)) {
        // Create an roi for each z slice
        final PointRoi[] rois =
            createStackRoi(resultsArray, options.isOption(OutputOption.HIDE_LABELS));

        if (options.isOption(OutputOption.ROI_SELECTION)) {
          overlay = addRoiToOverlay(imp, rois, overlay);
        }

        if (maxImp != null && options.isOption(OutputOption.MASK_ROI_SELECTION)) {
          addRoiToOverlay(maxImp, rois);
        }
      } else {
        final PointRoi roi = createRoi(resultsArray, options.isOption(OutputOption.HIDE_LABELS));

        if (options.isOption(OutputOption.ROI_SELECTION)) {
          imp.setRoi(roi);
          saveImp = true;
        }

        if (maxImp != null && options.isOption(OutputOption.MASK_ROI_SELECTION)) {
          maxImp.setRoi(roi);
        }
      }
    }

    if (overlay != null) {
      imp.setOverlay(overlay);
      saveImp = true;
    }

    if (saveImp) {
      IJ.saveAsTiff(imp, batchSettings.outputDirectory + File.separator + expId + ".tiff");
    }
    if (maxImp != null) {
      IJ.saveAsTiff(maxImp, batchSettings.outputDirectory + File.separator + expId + ".mask.tiff");
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
    synchronized (memory) {
      memory.put(name, new FindFociMemoryResults(imp, resultsArray));
    }
  }

  /**
   * Get a list of the names of the results that are stored in memory.
   *
   * @return a list of results names
   */
  public static String[] getResultsNames() {
    synchronized (memory) {
      final String[] names = new String[memory.size()];
      int index = 0;
      for (final String name : memory.keySet()) {
        names[index++] = name;
      }
      return names;
    }
  }

  /**
   * Get set of results corresponding to the name.
   *
   * @param name The name of the results.
   * @return The results (or null if none exist)
   */
  public static FindFociMemoryResults getResults(String name) {
    synchronized (memory) {
      return memory.get(name);
    }
  }

  private static boolean showSettingsDialog() {
    final GenericDialog gd = new GenericDialog(TITLE + " Settings");
    int searchCapacity = GlobalSettings.INSTANCE.searchCapacity.get();
    String emptyField = GlobalSettings.INSTANCE.emptyField.get();
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

    GlobalSettings.INSTANCE.searchCapacity.set(searchCapacity);
    GlobalSettings.INSTANCE.emptyField.set(emptyField);
    GlobalSettings.INSTANCE.save();

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
   * Checks if using an optimised FindFociProcessor.
   *
   * @return True if using an optimised FindFociProcessor
   */
  public boolean isOptimisedProcessor() {
    return optimisedProcessor;
  }

  /**
   * Set to true to use an optimised FindFociProcessor (otherwise use a generic processor).
   *
   * @param optimisedProcessor True if using an optimised FindFociProcessor
   */
  public void setOptimisedProcessor(boolean optimisedProcessor) {
    this.optimisedProcessor = optimisedProcessor;
  }
}
