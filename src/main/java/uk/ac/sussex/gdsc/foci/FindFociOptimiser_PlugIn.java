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

import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.set.hash.TDoubleHashSet;
import gnu.trove.set.hash.TIntHashSet;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.gui.YesNoCancelDialog;
import ij.io.FileInfo;
import ij.plugin.Duplicator;
import ij.plugin.PlugIn;
import ij.plugin.frame.Recorder;
import ij.process.ImageProcessor;
import ij.text.TextPanel;
import ij.text.TextWindow;
import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Color;
import java.awt.TextField;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.TextListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Formatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToDoubleBiFunction;
import java.util.regex.Pattern;
import javax.swing.WindowConstants;
import org.apache.commons.lang3.ArrayUtils;
import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.annotation.Nullable;
import uk.ac.sussex.gdsc.core.data.VisibleForTesting;
import uk.ac.sussex.gdsc.core.ij.BufferedTextWindow;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.ij.SimpleImageJTrackProgress;
import uk.ac.sussex.gdsc.core.ij.gui.ExtendedGenericDialog;
import uk.ac.sussex.gdsc.core.logging.Ticker;
import uk.ac.sussex.gdsc.core.match.Coordinate;
import uk.ac.sussex.gdsc.core.match.MatchCalculator;
import uk.ac.sussex.gdsc.core.match.MatchResult;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold;
import uk.ac.sussex.gdsc.core.utils.FileUtils;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.core.utils.SoftLock;
import uk.ac.sussex.gdsc.core.utils.StoredData;
import uk.ac.sussex.gdsc.core.utils.TextUtils;
import uk.ac.sussex.gdsc.core.utils.concurrent.ConcurrencyUtils;
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
import uk.ac.sussex.gdsc.foci.gui.OptimiserView;

/**
 * Runs the FindFoci plugin with various settings and compares the results to the reference image
 * point ROI.
 */
public class FindFociOptimiser_PlugIn implements PlugIn {
  private static final String TITLE = "FindFoci Optimiser";

  /** The precision to use when reporting results. */
  private static final int RESULT_PRECISION = 4;

  private static final String RESULTS_SUFFIX = ".results.xls";

  /** The list of recognised methods for sorting the results. */
  private static final String[] sortMethods =
      {"None", "Precision", "Recall", "F0.5", "F1", "F2", "F-beta", "Jaccard", "RMSD"};

  /** Do not sort the results. */
  private static final int SORT_NONE = 0;
  /** Sort the results using the Jaccard. */
  private static final int SORT_JACCARD = 7;
  /**
   * Sort the results using the RMSD. Note that the RMSD is only computed using the TP so is
   * therefore only of use when the TP values are the same (i.e. for a tie breaker)
   */
  private static final int SORT_RMSD = 8;

  private static final Pattern TAB_PATTERN = Pattern.compile("\t");
  private static final Pattern POINTS_PATTERN = Pattern.compile("[, \t]+");
  /** The maximum errors when reading a file. */
  private static final int MAX_ERROR = 5;

  private static AtomicReference<TextWindow> resultsWindow = new AtomicReference<>();

  private static final String[] SCORING_MODES =
      new String[] {"Raw score metric", "Relative (% drop from top)", "Z-score", "Rank"};
  private static final int SCORE_RAW = 0;
  private static final int SCORE_RELATIVE = 1;
  private static final int SCORE_Z = 2;
  private static final int SCORE_RANK = 3;

  /** The current settings for the plugin instance. */
  private Settings settings;

  /** ImageJ macro command for the plugin. */
  private String optimiserCommandOptions;

  /** The current batch settings for the plugin instance. */
  private BatchSettings batchSettings;

  /** An instance of the optimiser view. */
  private static final AtomicReference<OptimiserView> instance = new AtomicReference<>();

  private List<EnumSet<AlgorithmOption>> optionsArray;
  private ThresholdMethod[] thresholdMethodArray;
  private StatisticsMethod[] statisticsMethodArray;
  private BackgroundMethod[] backgroundMethodArray;
  private double backgroundParameterMin;
  private double backgroundParameterMax;
  private double backgroundParameterInterval;
  private double[] backgroundParameterMinArray;
  private SearchMethod[] searchMethodArray;
  private double searchParameterMin;
  private double searchParameterMax;
  private double searchParameterInterval;
  private double[] searchParameterMinArray;
  private int minSizeMin;
  private int minSizeMax;
  private int minSizeInterval;
  private double peakParameterMin;
  private double peakParameterMax;
  private double peakParameterInterval;
  private SortMethod[] sortMethodArray;
  private double[] blurArray;
  private CentreMethod[] centreMethodArray;
  private int centreParameterMin;
  private int centreParameterMax;
  private int centreParameterInterval;
  private int[] centreParameterMinArray;
  private int[] centreParameterMaxArray;
  private int[] centreParameterIntervalArray;

  // For the multi-image mode
  private boolean multiMode;

  @SuppressWarnings("rawtypes")
  private Vector checkbox;
  @SuppressWarnings("rawtypes")
  private Vector choice;

  // The number of combinations
  private int combinations;

  private final TObjectIntHashMap<String> idMap = new TObjectIntHashMap<>();
  private final TIntObjectHashMap<Parameters> optionsMap = new TIntObjectHashMap<>();

  private final SoftLock lock = new SoftLock();

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());
    /** The options used for search above the saddle. */
    static final String[] saddleOptions = {"Yes", "Yes - Connected", "No", "All"};
    /** The search methods used for matching. */
    static final String[] matchSearchMethods = {"Relative", "Absolute"};

    private static final String KEY_MASK_IMAGE = "findfoci.optimiser.maskImage";
    private static final String KEY_BACKGROUND_STD_DEV_ABOVE_MEAN =
        "findfoci.optimiser.backgroundStdDevAboveMean";
    private static final String KEY_BACKGROUND_AUTO = "findfoci.optimiser.backgroundAuto";
    private static final String KEY_BACKGROUND_ABSOLUTE = "findfoci.optimiser.backgroundAbsolute";
    private static final String KEY_BACKGROUND_PARAMETER = "findfoci.optimiser.backgroundParameter";
    private static final String KEY_THRESHOLD_METHOD = "findfoci.optimiser.thresholdMethod";
    private static final String KEY_STATICTICS_MODE = "findfoci.optimiser.statisticsMode";
    private static final String KEY_SEARCH_ABOVE_BACKGROUND =
        "findfoci.optimiser.searchAboveBackground";
    private static final String KEY_SEARCH_FRACTION_OF_BACKGROUND =
        "findfoci.optimiser.searchFractionOfPeak";
    private static final String KEY_SEARCH_PARAMETER = "findfoci.optimiser.searchParameter";
    private static final String KEY_MIN_SIZE_PARAMETER = "findfoci.optimiser.minSizeParameter";
    private static final String KEY_MINIMUM_ABOVE_SADDLE = "findfoci.optimiser.minimumAboveSaddle";
    private static final String KEY_PEAK_METHOD = "findfoci.optimiser.peakMethod";
    private static final String KEY_PEAK_PARAMETER = "findfoci.optimiser.peakParameter";
    private static final String KEY_SORT_METHOD = "findfoci.optimiser.sortMethod";
    private static final String KEY_MAX_PEAKS = "findfoci.optimiser.maxPeaks";
    private static final String KEY_GAUSSIAN_BLUR = "findfoci.optimiser.gaussianBlur";
    private static final String KEY_CENTRE_METHOD = "findfoci.optimiser.centreMethod";
    private static final String KEY_CENTRE_PARAMETER = "findfoci.optimiser.centreParameter";
    private static final String KEY_STEP_LIMIT = "findfoci.optimiser.stepLimit";
    private static final String KEY_MATCH_SEARCH_METHOD = "findfoci.optimiser.matchSearchMethod";
    private static final String KEY_MATCH_SEARCH_DISTANCE =
        "findfoci.optimiser.matchSearchDistance";
    private static final String KEY_RESULTS_SORT_METHOD = "findfoci.optimiser.resultsSortMethod";
    private static final String KEY_BETA = "findfoci.optimiser.beta";
    private static final String KEY_MAX_RESULTS = "findfoci.optimiser.maxResults";
    private static final String KEY_SHOW_SCORE_IMAGES = "findfoci.optimiser.showScoreImages";
    private static final String KEY_RESULT_FILE = "findfoci.optimiser.resultFile";

    String maskImage;
    boolean backgroundStdDevAboveMean;
    boolean backgroundAuto;
    boolean backgroundAbsolute;
    String backgroundParameter;
    String thresholdMethod;
    String statisticsMode;
    boolean searchAboveBackground;
    boolean searchFractionOfPeak;
    String searchParameter;
    String minSizeParameter;
    int minimumAboveSaddle;
    PeakMethod peakMethod;
    String peakParameter;
    String sortMethod;
    int maxPeaks;
    String gaussianBlur;
    String centreMethod;
    String centreParameter;
    int stepLimit;
    int matchSearchMethod;
    double matchSearchDistance;
    int resultsSortMethod;
    double beta;
    int maxResults;
    boolean showScoreImages;
    String resultFile;

    /**
     * Default constructor.
     */
    Settings() {
      maskImage = Prefs.get(KEY_MASK_IMAGE, "");
      backgroundStdDevAboveMean = Prefs.get(KEY_BACKGROUND_STD_DEV_ABOVE_MEAN, true);
      backgroundAuto = Prefs.get(KEY_BACKGROUND_AUTO, true);
      backgroundAbsolute = Prefs.get(KEY_BACKGROUND_ABSOLUTE, false);
      backgroundParameter = Prefs.get(KEY_BACKGROUND_PARAMETER, "2.5, 3.5, 0.5");
      thresholdMethod = Prefs.get(KEY_THRESHOLD_METHOD, ThresholdMethod.OTSU.getDescription());
      statisticsMode = Prefs.get(KEY_STATICTICS_MODE, "Both");
      searchAboveBackground = Prefs.get(KEY_SEARCH_ABOVE_BACKGROUND, true);
      searchFractionOfPeak = Prefs.get(KEY_SEARCH_FRACTION_OF_BACKGROUND, true);
      searchParameter = Prefs.get(KEY_SEARCH_PARAMETER, "0, 0.6, 0.2");
      minSizeParameter = Prefs.get(KEY_MIN_SIZE_PARAMETER, "1, 9, 2");
      minimumAboveSaddle = Prefs.getInt(KEY_MINIMUM_ABOVE_SADDLE, 0);
      peakMethod = PeakMethod.fromOrdinal(
          Prefs.getInt(KEY_PEAK_METHOD, PeakMethod.RELATIVE_ABOVE_BACKGROUND.ordinal()),
          PeakMethod.RELATIVE_ABOVE_BACKGROUND);
      peakParameter = Prefs.get(KEY_PEAK_PARAMETER, "0, 0.6, 0.2");
      sortMethod = Prefs.get(KEY_SORT_METHOD, SortMethod.INTENSITY.getDescription());
      maxPeaks = Prefs.getInt(KEY_MAX_PEAKS, 500);
      gaussianBlur = Prefs.get(KEY_GAUSSIAN_BLUR, "0, 0.5, 1");
      centreMethod = Prefs.get(KEY_CENTRE_METHOD, CentreMethod.MAX_VALUE_SEARCH.getDescription());
      centreParameter = Prefs.get(KEY_CENTRE_PARAMETER, "2");
      stepLimit = Prefs.getInt(KEY_STEP_LIMIT, 10000);
      matchSearchMethod = Prefs.getInt(KEY_MATCH_SEARCH_METHOD, 0);
      matchSearchDistance = Prefs.get(KEY_MATCH_SEARCH_DISTANCE, 0.05);
      resultsSortMethod = Prefs.getInt(KEY_RESULTS_SORT_METHOD, SORT_JACCARD);
      beta = Prefs.get(KEY_BETA, 4.0);
      maxResults = Prefs.getInt(KEY_MAX_RESULTS, 100);
      showScoreImages = Prefs.get(KEY_SHOW_SCORE_IMAGES, false);
      resultFile = Prefs.get(KEY_RESULT_FILE, "");
    }

    /**
     * Copy constructor.
     *
     * @param source the source
     */
    private Settings(Settings source) {
      maskImage = source.maskImage;
      backgroundStdDevAboveMean = source.backgroundStdDevAboveMean;
      backgroundAuto = source.backgroundAuto;
      backgroundAbsolute = source.backgroundAbsolute;
      backgroundParameter = source.backgroundParameter;
      thresholdMethod = source.thresholdMethod;
      statisticsMode = source.statisticsMode;
      searchAboveBackground = source.searchAboveBackground;
      searchFractionOfPeak = source.searchFractionOfPeak;
      searchParameter = source.searchParameter;
      minSizeParameter = source.minSizeParameter;
      minimumAboveSaddle = source.minimumAboveSaddle;
      peakMethod = source.peakMethod;
      peakParameter = source.peakParameter;
      sortMethod = source.sortMethod;
      maxPeaks = source.maxPeaks;
      gaussianBlur = source.gaussianBlur;
      centreMethod = source.centreMethod;
      centreParameter = source.centreParameter;
      stepLimit = source.stepLimit;
      matchSearchMethod = source.matchSearchMethod;
      matchSearchDistance = source.matchSearchDistance;
      resultsSortMethod = source.resultsSortMethod;
      beta = source.beta;
      maxResults = source.maxResults;
      showScoreImages = source.showScoreImages;
      resultFile = source.resultFile;
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
      Prefs.set(KEY_MASK_IMAGE, maskImage);
      Prefs.set(KEY_MASK_IMAGE, maskImage);
      Prefs.set(KEY_BACKGROUND_STD_DEV_ABOVE_MEAN, backgroundStdDevAboveMean);
      Prefs.set(KEY_BACKGROUND_AUTO, backgroundAuto);
      Prefs.set(KEY_BACKGROUND_ABSOLUTE, backgroundAbsolute);
      Prefs.set(KEY_BACKGROUND_PARAMETER, backgroundParameter);
      Prefs.set(KEY_THRESHOLD_METHOD, thresholdMethod);
      Prefs.set(KEY_STATICTICS_MODE, statisticsMode);
      Prefs.set(KEY_SEARCH_ABOVE_BACKGROUND, searchAboveBackground);
      Prefs.set(KEY_SEARCH_FRACTION_OF_BACKGROUND, searchFractionOfPeak);
      Prefs.set(KEY_SEARCH_PARAMETER, searchParameter);
      Prefs.set(KEY_MIN_SIZE_PARAMETER, minSizeParameter);
      Prefs.set(KEY_MINIMUM_ABOVE_SADDLE, minimumAboveSaddle);
      Prefs.set(KEY_PEAK_METHOD, peakMethod.ordinal());
      Prefs.set(KEY_PEAK_PARAMETER, peakParameter);
      Prefs.set(KEY_SORT_METHOD, sortMethod);
      Prefs.set(KEY_MAX_PEAKS, maxPeaks);
      Prefs.set(KEY_GAUSSIAN_BLUR, gaussianBlur);
      Prefs.set(KEY_CENTRE_METHOD, centreMethod);
      Prefs.set(KEY_CENTRE_PARAMETER, centreParameter);
      Prefs.set(KEY_STEP_LIMIT, stepLimit);
      Prefs.set(KEY_MATCH_SEARCH_METHOD, matchSearchMethod);
      Prefs.set(KEY_MATCH_SEARCH_DISTANCE, matchSearchDistance);
      Prefs.set(KEY_RESULTS_SORT_METHOD, resultsSortMethod);
      Prefs.set(KEY_BETA, beta);
      Prefs.set(KEY_MAX_RESULTS, maxResults);
      Prefs.set(KEY_SHOW_SCORE_IMAGES, showScoreImages);
      Prefs.set(KEY_RESULT_FILE, resultFile);
    }
  }

  /**
   * Contains the batch settings that are the re-usable state of the plugin.
   */
  private static class BatchSettings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<BatchSettings> lastSettings =
        new AtomicReference<>(new BatchSettings());

    private static final String KEY_INPUT_DIRECTORY = "findfoci.optimiser.inputDirectory";
    private static final String KEY_MASK_DIRECTORY = "findfoci.optimiser.maskDirectory";
    private static final String KEY_OUTPUT_DIRECTORY = "findfoci.optimiser.outputDirectory";
    private static final String KEY_SCORING_MODE = "findfoci.optimiser.scoringMode";
    private static final String KEY_REUSE_RESULTS = "findfoci.optimiser.reuseResults";
    String inputDirectory;
    String maskDirectory;
    String outputDirectory;
    int scoringMode;
    boolean reuseResults;

    /**
     * Default constructor.
     */
    BatchSettings() {
      inputDirectory = Prefs.get(KEY_INPUT_DIRECTORY, "");
      maskDirectory = Prefs.get(KEY_MASK_DIRECTORY, "");
      outputDirectory = Prefs.get(KEY_OUTPUT_DIRECTORY, "");
      scoringMode = Prefs.getInt(KEY_SCORING_MODE, SCORE_RAW);
      reuseResults = Prefs.getBoolean(KEY_REUSE_RESULTS, true);
    }

    /**
     * Copy constructor.
     *
     * @param source the source
     */
    private BatchSettings(BatchSettings source) {
      inputDirectory = source.inputDirectory;
      maskDirectory = source.maskDirectory;
      outputDirectory = source.outputDirectory;
      scoringMode = source.scoringMode;
      reuseResults = source.reuseResults;
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
      // Save to ImageJ preferences
      Prefs.set(KEY_INPUT_DIRECTORY, inputDirectory);
      Prefs.set(KEY_MASK_DIRECTORY, maskDirectory);
      Prefs.set(KEY_OUTPUT_DIRECTORY, outputDirectory);
      Prefs.set(KEY_SCORING_MODE, scoringMode);
      Prefs.set(KEY_REUSE_RESULTS, reuseResults);
    }
  }

  /**
   * Contains the settings to allow display of the latest results from the results table.
   */
  private static class LastImageSettings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<LastImageSettings> lastSettings =
        new AtomicReference<>(new LastImageSettings());

    final ImagePlus imp;
    final ImagePlus mask;
    final ArrayList<Result> results;
    final boolean showScoreImages;
    final int matchSearchMethod;
    final double matchSearchDistance;

    /**
     * Default constructor.
     *
     * @param imp the image
     * @param mask the mask
     * @param results the results
     * @param showScoreImages the show score images flag
     * @param matchSearchMethod the match search method
     * @param matchSearchDistance the match search distance
     */
    LastImageSettings(ImagePlus imp, ImagePlus mask, ArrayList<Result> results,
        boolean showScoreImages, int matchSearchMethod, double matchSearchDistance) {
      this.imp = imp;
      this.mask = mask;
      this.results = results;
      this.showScoreImages = showScoreImages;
      this.matchSearchMethod = matchSearchMethod;
      this.matchSearchDistance = matchSearchDistance;
    }

    /**
     * Empty constructor.
     */
    private LastImageSettings() {
      imp = null;
      mask = null;
      results = null;
      showScoreImages = false;
      matchSearchMethod = 0;
      matchSearchDistance = 0;
    }

    /**
     * Load the settings.
     *
     * @return the settings
     */
    static LastImageSettings load() {
      return lastSettings.get();
    }

    /**
     * Save the settings.
     */
    void save() {
      lastSettings.set(this);
    }
  }

  /**
   * Store the match result between the target foci and the actual foci found by the algorithm.
   */
  private static class Result {
    static final int PRECISION = 0;
    static final int RECALL = 1;
    static final int F05 = 2;
    static final int F1 = 3;
    static final int F2 = 4;
    static final int FB = 5;
    static final int JACCARD = 6;
    static final int RANK = 7;
    static final int SCORE = 8;
    static final int RMSD = 9;

    int id;
    Parameters options;
    int count;
    int tp;
    int fp;
    int fn;
    long time;
    double[] metrics = new double[10];

    Result(int id, Parameters options, int count, int tp, int fp, int fn, long time, double beta,
        double rmsd) {
      this.id = id;
      this.options = options;
      this.count = count;
      this.tp = tp;
      this.fp = fp;
      this.fn = fn;
      this.time = time;

      if (tp + fp > 0) {
        metrics[PRECISION] = (double) tp / (tp + fp);
      }
      if (tp + fn > 0) {
        metrics[RECALL] = (double) tp / (tp + fn);
      }
      if (tp + fp + fn > 0) {
        metrics[JACCARD] = (double) tp / (tp + fp + fn);
      }
      metrics[F05] = calculateFScore(metrics[PRECISION], metrics[RECALL], 0.5);
      metrics[F1] = calculateFScore(metrics[PRECISION], metrics[RECALL], 1.0);
      metrics[F2] = calculateFScore(metrics[PRECISION], metrics[RECALL], 2.0);
      metrics[FB] = calculateFScore(metrics[PRECISION], metrics[RECALL], beta);
      metrics[RMSD] = rmsd;
    }

    private static double calculateFScore(double precision, double recall, double beta) {
      final double b2 = beta * beta;
      final double f = ((1.0 + b2) * precision * recall) / (b2 * precision + recall);
      return (Double.isNaN(f) ? 0 : f);
    }

    /**
     * Add the values stored in the given result to the current values.
     *
     * @param result the result
     */
    void add(Result result) {
      // Create a new RMSD
      final double sd1 = metrics[RMSD] * metrics[RMSD] * tp;
      final double sd2 = result.metrics[RMSD] * result.metrics[RMSD] * result.tp;
      metrics[RMSD] = Math.sqrt((sd1 + sd2) / (tp + result.tp));

      // Combine all other metrics
      count += result.count;
      tp += result.tp;
      fp += result.fp;
      fn += result.fn;
      time += result.time;
      for (int i = 0; i < metrics.length - 1; i++) {
        metrics[i] += result.metrics[i];
      }
    }

    String getParameters() {
      if (options != null) {
        return options.toString();
      }
      return "";
    }
  }

  /**
   * Provides the ability to sort the results arrays in ascending order.
   */
  private static class ResultComparator implements Comparator<Result>, Serializable {
    private static final long serialVersionUID = 1L;

    private final int sortIndex;
    private final int tieIndex;
    private final int sortRank;
    private final int tieRank;

    ResultComparator(int sortIndex, boolean lowestFirst) {
      this.sortIndex = sortIndex;
      if (sortIndex == Result.RMSD) {
        tieIndex = Result.JACCARD;
      } else {
        tieIndex = Result.RMSD;
      }
      sortRank = (lowestFirst) ? 1 : -1;
      tieRank = getRankOfHighest(tieIndex);
    }

    ResultComparator(int sortIndex) {
      this.sortIndex = sortIndex;
      if (sortIndex == Result.RMSD) {
        tieIndex = Result.JACCARD;
      } else {
        tieIndex = Result.RMSD;
      }
      sortRank = getRankOfHighest(sortIndex);
      tieRank = getRankOfHighest(tieIndex);
    }

    private static int getRankOfHighest(int index) {
      switch (index) {
        case Result.RANK:
        case Result.RMSD:
          // Highest last
          return 1;
        default:
          // Highest first
          return -1;
      }
    }

    /** {@inheritDoc} */
    @Override
    public int compare(Result o1, Result o2) {
      // Require the highest is first
      if (o1.metrics[sortIndex] > o2.metrics[sortIndex]) {
        return sortRank;
      }
      if (o1.metrics[sortIndex] < o2.metrics[sortIndex]) {
        return -sortRank;
      }

      // Compare using the tie index
      if (o1.metrics[tieIndex] > o2.metrics[tieIndex]) {
        return tieRank;
      }
      if (o1.metrics[tieIndex] < o2.metrics[tieIndex]) {
        return -tieRank;
      }

      // Update this to not perform a comparison of the result parameter options
      if (o1.options != null && o2.options != null) {
        // Return method with most conservative settings
        final int[] result = new int[1];

        final FindFociProcessorOptions p1 = o1.options.processorOptions;
        final FindFociProcessorOptions p2 = o2.options.processorOptions;

        if (compare(p1.getGaussianBlur(), p2.getGaussianBlur(), result) != 0) {
          return result[0];
        }

        // Higher background methods are more general
        if (compare(p1.getBackgroundMethod(), p2.getBackgroundMethod(), result) != 0) {
          return -result[0];
        }
        if (compare(p1.getBackgroundParameter(), p2.getBackgroundParameter(), result) != 0) {
          return result[0];
        }

        // Smallest size is more general
        if (compare(p1.getMinSize(), p2.getMinSize(), result) != 0) {
          return result[0];
        }

        // Lower search methods are more general
        if (compare(p1.getSearchMethod(), p2.getSearchMethod(), result) != 0) {
          return result[0];
        }
        if (compare(p1.getSearchParameter(), p2.getSearchParameter(), result) != 0) {
          return result[0];
        }

        // Higher peak methods are more general
        if (compare(p1.getPeakMethod(), p2.getPeakMethod(), result) != 0) {
          return -result[0];
        }
        if (compare(p1.getPeakParameter(), p2.getPeakParameter(), result) != 0) {
          return result[0];
        }
      }

      // Return fastest method
      return Long.compare(o1.time, o2.time);
    }

    private static int compare(double value1, double value2, int[] result) {
      if (value1 < value2) {
        result[0] = -1;
      } else if (value1 > value2) {
        result[0] = 1;
      } else {
        result[0] = 0;
      }
      return result[0];
    }

    private static int compare(int value1, int value2, int[] result) {
      result[0] = Integer.compare(value1, value2);
      return result[0];
    }

    private static <T extends Enum<?>> int compare(T value1, T value2, int[] result) {
      result[0] = Integer.compare(value1.ordinal(), value2.ordinal());
      return result[0];
    }
  }

  /**
   * A class to allow conversion of optimised parameters to and from a {@link String}.
   */
  @VisibleForTesting
  static class Parameters {
    private static final String SPACER = " : ";

    /** The processor options. */
    final FindFociProcessorOptions processorOptions;
    /** The cached parameter string. */
    private String parameterString;

    /**
     * Instantiates a new parameters.
     *
     * @param processorOptions the processor options
     */
    Parameters(FindFociProcessorOptions processorOptions) {
      this.processorOptions = processorOptions.copy();
    }

    /**
     * Creates the string representation of the parameters (the value is computed once and cached).
     *
     * @return the string representation of the parameters.
     */
    @Override
    public String toString() {
      String result = parameterString;
      if (result == null) {
        parameterString = result = createParametersString();
      }
      return result;
    }

    /**
     * Convert the FindFoci parameters into a text representation.
     *
     * @return the string
     */
    String createParametersString() {
      // Output results
      final StringBuilder sb = new StringBuilder();
      // Field 1
      sb.append(processorOptions.getGaussianBlur()).append('\t');
      // Field 2
      sb.append(processorOptions.getBackgroundMethod().getDescription());
      if (backgroundMethodHasStatisticsMode(processorOptions.getBackgroundMethod())) {
        sb.append(" (").append(processorOptions.getStatisticsMethod().getDescription())
            .append(") ");
      }
      sb.append(SPACER);
      if (backgroundMethodHasParameter(processorOptions.getBackgroundMethod())) {
        sb.append(IJ.d2s(processorOptions.getBackgroundParameter(), 2));
      } else {
        sb.append(processorOptions.getThresholdMethod().getDescription());
      }
      sb.append('\t');
      // Field 3
      sb.append(processorOptions.getMaxPeaks()).append('\t');
      // Field 4
      sb.append(processorOptions.getMinSize());
      if (processorOptions.isOption(AlgorithmOption.MINIMUM_ABOVE_SADDLE)) {
        sb.append(" >saddle");
        if (processorOptions.isOption(AlgorithmOption.CONTIGUOUS_ABOVE_SADDLE)) {
          sb.append(" conn");
        }
      }
      sb.append('\t');
      // Field 5
      sb.append(processorOptions.getSearchMethod().getDescription());
      if (searchMethodHasParameter(processorOptions.getSearchMethod())) {
        sb.append(SPACER).append(IJ.d2s(processorOptions.getSearchParameter(), 2));
      }
      sb.append('\t');
      // Field 6
      sb.append(processorOptions.getPeakMethod().getDescription()).append(SPACER);
      sb.append(IJ.d2s(processorOptions.getPeakParameter(), 2)).append('\t');
      // Field 7
      sb.append(processorOptions.getSortMethod().getDescription()).append('\t');
      // Field 8
      sb.append(processorOptions.getCentreMethod().getDescription());
      if (centreMethodHasParameter(processorOptions.getCentreMethod())) {
        sb.append(SPACER).append(IJ.d2s(processorOptions.getCentreParameter(), 2));
      }
      sb.append('\t');
      return sb.toString();
    }

    /**
     * Convert the FindFoci text representation into Parameters.
     *
     * @param text the parameters text
     * @return the options
     * @throws IllegalArgumentException if the argument could not be parsed
     */
    static Parameters fromString(String text) {
      final String[] fields = TAB_PATTERN.split(text);
      try {
        final FindFociProcessorOptions processorOptions = new FindFociProcessorOptions(true);
        // Field 1
        processorOptions.setGaussianBlur(Double.parseDouble(fields[0]));
        // Field 2 - Divided by a spacer
        int index = fields[1].indexOf(SPACER);
        final String backgroundMethod = fields[1].substring(0, index);
        final String backgroundOption = fields[1].substring(index + SPACER.length());
        index = backgroundMethod.indexOf('(');
        if (index != -1) {
          final int first = index + 1;
          final int last = backgroundMethod.indexOf(')', first);
          processorOptions.setBackgroundMethod(
              BackgroundMethod.fromDescription(backgroundMethod.substring(0, index - 1)));
          processorOptions.setStatisticsMethod(
              StatisticsMethod.fromDescription(backgroundMethod.substring(first, last)));
        } else {
          processorOptions.setBackgroundMethod(BackgroundMethod.fromDescription(backgroundMethod));
        }
        if (backgroundMethodHasParameter(processorOptions.getBackgroundMethod())) {
          processorOptions.setBackgroundParameter(Double.parseDouble(backgroundOption));
        } else {
          processorOptions.setThresholdMethod(ThresholdMethod.fromDescription(backgroundOption));
        }
        // Field 3
        processorOptions.setMaxPeaks(Integer.parseInt(fields[2]));
        // Field 4
        index = fields[3].indexOf(' ');
        if (index > 0) {
          processorOptions.setOption(AlgorithmOption.MINIMUM_ABOVE_SADDLE, true);
          if (fields[3].contains("conn")) {
            processorOptions.setOption(AlgorithmOption.CONTIGUOUS_ABOVE_SADDLE, true);
          }
          fields[3] = fields[3].substring(0, index);
        }
        processorOptions.setMinSize(Integer.parseInt(fields[3]));
        // Field 5
        index = fields[4].indexOf(SPACER);
        if (index != -1) {
          processorOptions
              .setSearchParameter(Double.parseDouble(fields[4].substring(index + SPACER.length())));
          fields[4] = fields[4].substring(0, index);
        }
        processorOptions.setSearchMethod(SearchMethod.fromDescription(fields[4]));
        // Field 6
        index = fields[5].indexOf(SPACER);
        processorOptions.setPeakMethod(PeakMethod.fromDescription(fields[5].substring(0, index)));
        processorOptions
            .setPeakParameter(Double.parseDouble(fields[5].substring(index + SPACER.length())));
        // Field 7
        processorOptions.setSortMethod(SortMethod.fromDescription(fields[6]));
        // Field 8
        index = fields[7].indexOf(SPACER);
        if (index != -1) {
          processorOptions
              .setCentreParameter(Double.parseDouble(fields[7].substring(index + SPACER.length())));
          fields[7] = fields[7].substring(0, index);
        }
        processorOptions.setCentreMethod(CentreMethod.fromDescription(fields[7]));

        return new Parameters(processorOptions);
      } catch (final NullPointerException | NumberFormatException | IndexOutOfBoundsException ex) {
        // NPE will be thrown if the enum cannot parse the description because null
        // will be passed to the setter.
        throw new IllegalArgumentException(
            "Error converting parameters to FindFoci options: " + text, ex);
      }
    }

    private static boolean centreMethodHasParameter(CentreMethod centreMethod) {
      return (centreMethod == CentreMethod.CENTRE_OF_MASS_SEARCH
          || centreMethod == CentreMethod.GAUSSIAN_SEARCH);
    }
  }

  /**
   * The Class OptimisationWorker.
   */
  private class OptimisationWorker implements Runnable {

    /** The image. */
    String image;

    /** The ticker. */
    Ticker ticker;

    /** The result. */
    OptimiserResult result;

    /**
     * Instantiates a new optimisation worker.
     *
     * @param image the image
     * @param ticker the ticker
     */
    OptimisationWorker(String image, Ticker ticker) {
      this.image = image;
      this.ticker = ticker;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
      if (IJ.escapePressed()) {
        return;
      }

      final ImagePlus imp = FindFoci_PlugIn.openImage(batchSettings.inputDirectory, image);
      // The input filename may not be an image
      if (imp == null) {
        IJ.log("Skipping file (it may not be an image): " + batchSettings.inputDirectory + image);
        // We can skip forward in the progress
        ticker.tick(combinations);
        return;
      }
      final String[] maskPath = FindFoci_PlugIn.getMaskImage(batchSettings.inputDirectory,
          batchSettings.maskDirectory, image);
      final ImagePlus mask = FindFoci_PlugIn.openImage(maskPath[0], maskPath[1]);
      final String resultFile = batchSettings.outputDirectory + imp.getShortTitle();
      final String fullResultFile = resultFile + RESULTS_SUFFIX;
      boolean newResults = false;
      if (batchSettings.reuseResults && new File(fullResultFile).exists()) {
        final ArrayList<Result> results = loadResults(fullResultFile);
        if (results.size() == combinations) {
          IJ.log("Re-using results: " + fullResultFile);
          // We can skip forward in the progress
          ticker.tick(combinations);
          result = new OptimiserResult(results, 0, 0);
        }
      }
      if (result == null) {
        IJ.log("Creating results: " + fullResultFile);
        newResults = true;
        result = runOptimiser(imp, mask, ticker);
      }
      if (result != null) {
        if (newResults) {
          saveResults(imp, mask, result.results, null, resultFile);
        }

        checkOptimisationSpace(result, imp);

        // Reset to the order defined by the ID
        Collections.sort(result.results, (r1, r2) -> Integer.compare(r1.id, r2.id));
      }
    }

    /**
     * Load the results from the specified file. We assign an arbitrary ID to each result using the
     * unique combination of parameters.
     *
     * @param filename the filename
     * @return The results
     */
    private ArrayList<Result> loadResults(String filename) {

      final ArrayList<Result> results = new ArrayList<>();
      if (countLines(filename) != combinations) {
        return results;
      }

      try (BufferedReader input = Files.newBufferedReader(Paths.get(filename))) {
        String line;
        boolean isRmsd = false;
        while ((line = input.readLine()) != null) {
          if (line.length() == 0) {
            continue;
          }
          if (line.charAt(0) == '#') {
            // Look for the RMSD field which was added later. This supports older results files
            if (line.contains("RMSD")) {
              isRmsd = true;
            }
            continue;
          }

          // Code using split and parse
          // # Rank Blur Background method Max Min Search method Peak method Sort method Centre
          // method
          // N TP FP FN Jaccard Precision Recall F0.5 F1 F2 F-beta RMSD mSec
          final int endIndex = getIndex(line, 8) + 1; // include the final tab
          final String parameters = line.substring(line.indexOf('\t') + 1, endIndex);
          final String metrics = line.substring(endIndex);
          final String[] fields = TAB_PATTERN.split(metrics);

          // Items we require
          final int id = getId(parameters);

          final int n = Integer.parseInt(fields[0]);
          final int tp = Integer.parseInt(fields[1]);
          final int fp = Integer.parseInt(fields[2]);
          final int fn = Integer.parseInt(fields[3]);
          double rmsd = 0;
          if (isRmsd) {
            rmsd = Double.parseDouble(fields[fields.length - 2]);
          }
          final long time = Long.parseLong(fields[fields.length - 1]);

          final Result r = new Result(id, null, n, tp, fp, fn, time, settings.beta, rmsd);
          // Do not count on the Options being parsed from the parameters.
          r.options = optionsMap.get(id);
          results.add(r);
        }

        // If the results were loaded then we must sort them to get a rank
        sortResults(results, settings.resultsSortMethod);
      } catch (final ArrayIndexOutOfBoundsException | IOException | NumberFormatException ex) {
        // Ignore parsing errors
      }
      return results;
    }

    /**
     * Count the number of valid lines in the file.
     *
     * @param filename the filename
     * @return The number of lines
     */
    private int countLines(String filename) {
      try (BufferedReader input = Files.newBufferedReader(Paths.get(filename))) {
        int count = 0;
        String line;
        while ((line = input.readLine()) != null) {
          if (line.isEmpty() || line.charAt(0) == '#') {
            continue;
          }
          count++;
        }
        return count;
      } catch (final IOException ex) {
        return 0;
      }
    }

    /**
     * Get the index of the nth occurrence of the tab character.
     *
     * @param line the line
     * @param nth the n'th occurrence
     * @return the index
     */
    private int getIndex(String line, int nth) {
      final char[] value = line.toCharArray();
      for (int i = 0; i < value.length; i++) {
        if (value[i] == '\t' && nth-- <= 0) {
          return i;
        }
      }
      return -1;
    }

    /**
     * Get a unique ID for the parameters string.
     *
     * @param parameters the parameters
     * @return the ID
     */
    private int getId(String parameters) {
      int id = idMap.get(parameters);
      if (id == 0) {
        synchronized (idMap) {
          id = createId(parameters);
        }
      }
      return id;
    }

    /**
     * Create a unique ID for the parameters string.
     *
     * @param parameters the parameters
     * @return the ID
     */
    private int createId(String parameters) {
      // Check again in case another thread just created it
      int id = idMap.get(parameters);
      if (id == 0) {
        id = idMap.size() + 1;
        // Ensure we have options for every ID
        optionsMap.put(id, Parameters.fromString(parameters));
        idMap.put(parameters, id);
      }
      return id;
    }
  }

  private static class StopWatch {
    long base;
    long time;

    StopWatch() {
      this(0);
    }

    StopWatch create() {
      return new StopWatch(getTime());
    }

    private StopWatch(long base) {
      time = System.nanoTime();
      this.base = base;
    }

    long stop() {
      time = System.nanoTime() - time;
      return getTime();
    }

    long getTime() {
      return time + base;
    }
  }

  private static class OptimiserResult {
    final ArrayList<Result> results;
    final long time;
    final long analysisTime;
    long total;

    OptimiserResult(ArrayList<Result> results, long time, long analysisTime) {
      this.results = results;
      this.time = time;
      this.analysisTime = analysisTime;
      total = 0;
      if (results != null) {
        for (final Result result : results) {
          total += result.time;
        }
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    if ("frame".equals(arg)) {
      showOptimiserWindow();
    } else {
      final ImagePlus imp = ("multi".equals(arg)) ? null : WindowManager.getCurrentImage();
      run(imp);
    }
  }

  /**
   * Run the optimiser on the given image. If the image is null then process in multi-image mode.
   *
   * @param imp the image
   */
  public void run(ImagePlus imp) {
    if (!showDialog(imp)) {
      return;
    }

    IJ.log("---\n" + TITLE);
    IJ.log(combinations + " combinations");

    if (multiMode) {
      runMultiMode();
    } else {
      runSingleMode(imp);
    }
  }

  private void runMultiMode() {
    // Get the list of images
    final String[] imageList = FindFoci_PlugIn.getBatchImages(batchSettings.inputDirectory);
    if (imageList == null || imageList.length == 0) {
      IJ.error(TITLE, "No input images in folder: " + batchSettings.inputDirectory);
      return;
    }

    if (batchSettings.reuseResults && resultsExist1(imageList)) {
      IJ.log("Output directory contains existing results that will be re-used if they have the "
          + "correct number of combinations");
    }

    IJ.showStatus("Running optimiser ...");

    // For each image start an optimiser run:
    // - Run the optimiser
    // - save the results to the output directory
    int size = imageList.length;
    final ExecutorService threadPool = Executors.newFixedThreadPool(Prefs.getThreads());
    final List<Future<?>> futures = new ArrayList<>(size);
    final ArrayList<OptimisationWorker> workers = new ArrayList<>(size);

    // Allow progress to be tracked across all threads
    final Ticker ticker = Ticker.createStarted(SimpleImageJTrackProgress.getInstance(),
        (long) combinations * size, true);
    for (final String image : imageList) {
      final OptimisationWorker w = new OptimisationWorker(image, ticker);
      workers.add(w);
      futures.add(threadPool.submit(w));
    }

    // Collect all the results
    ConcurrencyUtils.waitForCompletionUnchecked(futures);
    ImageJUtils.finished();

    if (ImageJUtils.isInterrupted()) {
      return;
    }

    // Check all results are the same size
    final ArrayList<ArrayList<Result>> allResults = new ArrayList<>(size);
    for (final OptimisationWorker w : workers) {
      if (w.result == null) {
        continue;
      }
      final ArrayList<Result> results = w.result.results;
      if (!allResults.isEmpty() && results.size() != allResults.get(0).size()) {
        IJ.error(TITLE, "Some optimisation runs produced a different number of results");
        return;
      }
      allResults.add(results);
    }
    if (allResults.isEmpty()) {
      IJ.error(TITLE, "No optimisation runs produced results");
      return;
    }

    IJ.showStatus("Calculating scores ...");

    // Combine using the chosen ranking score.
    // Use the first set of results to accumulate scores.
    final ArrayList<Result> results = allResults.get(0);
    final int scoringMode = batchSettings.scoringMode;
    getScore(results, scoringMode);
    size = allResults.size();
    for (int i = 1; i < size; i++) {
      final ArrayList<Result> results2 = allResults.get(i);
      getScore(results2, scoringMode);
      for (int j = 0; j < results.size(); j++) {
        // Combine all the metrics
        final Result r1 = results.get(j);
        final Result r2 = results2.get(j);
        r1.add(r2);
      }
    }
    // Average the scores
    final double factor = 1.0 / size;
    for (int j = 0; j < results.size(); j++) {
      final double[] metrics = results.get(j).metrics;
      // Do not average the RMSD
      for (int i = 0; i < metrics.length - 1; i++) {
        metrics[i] *= factor;
      }
      // We must reset the score with the original RMSD
      if (settings.resultsSortMethod == SORT_RMSD) {
        metrics[Result.SCORE] = metrics[Result.RMSD];
      }
    }

    // Now sort the results using the combined scores. Check is the scored metric is lowest first
    final boolean lowestFirst = settings.resultsSortMethod == SORT_RMSD;
    sortResultsByScore(results, lowestFirst);

    // Output the combined results
    saveResults(null, null, results, null, batchSettings.outputDirectory + "all");

    // Show in a table
    showResults(null, null, results, settings.maxResults);

    IJ.showStatus("");
  }

  private void runSingleMode(ImagePlus imp) {
    final ImagePlus mask = WindowManager.getImage(settings.maskImage);

    final OptimiserResult result = runOptimiser(imp, mask,
        Ticker.createStarted(SimpleImageJTrackProgress.getInstance(), combinations, false));
    ImageJUtils.finished();

    if (ImageJUtils.isInterrupted()) {
      return;
    }

    if (result == null) {
      IJ.error(TITLE, "No results");
      return;
    }

    // For a single image we use the raw score (since no results are combined)
    final ArrayList<Result> results = result.results;
    getScore(results, SCORE_RAW);
    showResults(imp, mask, results, settings.maxResults);

    // Re-run Find_Peaks and output the best result
    if (!results.isEmpty()) {
      IJ.log("Top result = "
          + IJ.d2s(results.get(0).metrics[getSortIndex(settings.resultsSortMethod)], 4));

      final Parameters bestOptions = results.get(0).options;

      final AssignedPoint[] predictedPoints = showResult(imp, mask, bestOptions,
          settings.showScoreImages, settings.matchSearchMethod, settings.matchSearchDistance);

      saveResults(imp, mask, results, predictedPoints, settings.resultFile);

      checkOptimisationSpace(result, imp);
    }
  }

  /**
   * Check if the output directory has any results already.
   *
   * @param imageList the image list
   * @return true if results exist
   */
  private boolean resultsExist1(String[] imageList) {
    // List the results in the output directory
    final String[] results =
        new File(batchSettings.outputDirectory).list((dir, name) -> name.endsWith(RESULTS_SUFFIX));

    if (ArrayUtils.isEmpty(results)) {
      return false;
    }
    for (String name : imageList) {
      name = getShortTitle(name);
      for (final String result : results) {
        if (result.startsWith(name)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Copied from ImagePlus.getShortTitle()
   *
   * @param title the title
   * @return the title with no spaces or extension
   */
  private static String getShortTitle(String title) {
    int index = title.indexOf(' ');
    if (index > -1) {
      title = title.substring(0, index);
    }
    index = title.lastIndexOf('.');
    if (index > 0) {
      title = title.substring(0, index);
    }
    return title;
  }

  /**
   * Check if the optimal results was obtained at the edge of the optimisation search space.
   *
   * @param result the result
   * @param imp the imp
   */
  private void checkOptimisationSpace(OptimiserResult result, ImagePlus imp) {
    final Parameters bestOptions = result.results.get(0).options;
    if (bestOptions == null) {
      return;
    }

    if (result.time != 0) {
      IJ.log(String.format("%s Optimisation time = %s (%s / combination). Speed up = %.3fx",
          imp.getTitle(), TextUtils.nanosToString(result.time),
          TextUtils.nanosToString(result.time / combinations),
          result.total / (double) (result.time - result.analysisTime)));
    }

    // Check if a sub-optimal best result was obtained at the limit of the optimisation range
    if (result.results.get(0).metrics[Result.F1] < 1.0) {
      final StringBuilder sb = new StringBuilder();
      @SuppressWarnings("resource")
      final Formatter formatter = new Formatter(sb);

      final FindFociProcessorOptions processorOptions = bestOptions.processorOptions;
      if (backgroundMethodHasParameter(processorOptions.getBackgroundMethod())) {
        if (processorOptions.getBackgroundParameter() == backgroundParameterMin) {
          append(formatter, "- Background parameter @ lower limit (%g)\n",
              processorOptions.getBackgroundParameter());
        } else if (processorOptions.getBackgroundParameter()
            + backgroundParameterInterval > backgroundParameterMax) {
          append(formatter, "- Background parameter @ upper limit (%g)\n",
              processorOptions.getBackgroundParameter());
        }
      }
      if (searchMethodHasParameter(processorOptions.getSearchMethod())) {
        if (processorOptions.getSearchParameter() == searchParameterMin && searchParameterMin > 0) {
          append(formatter, "- Search parameter @ lower limit (%g)\n",
              processorOptions.getSearchParameter());
        } else if (processorOptions.getSearchParameter()
            + searchParameterInterval > searchParameterMax && searchParameterMax < 1) {
          append(formatter, "- Search parameter @ upper limit (%g)\n",
              processorOptions.getSearchParameter());
        }
      }
      if (processorOptions.getMinSize() == minSizeMin && minSizeMin > 1) {
        append(formatter, "- Min Size @ lower limit (%d)\n", processorOptions.getMinSize());
      } else if (processorOptions.getMinSize() + minSizeInterval > minSizeMax) {
        append(formatter, "- Min Size @ upper limit (%d)\n", processorOptions.getMinSize());
      }

      if (processorOptions.getPeakParameter() == peakParameterMin && peakParameterMin > 0) {
        append(formatter, "- Peak parameter @ lower limit (%g)\n",
            processorOptions.getPeakParameter());
      } else if (processorOptions.getPeakParameter() + peakParameterInterval > peakParameterMax
          && peakParameterMax < 1) {
        append(formatter, "- Peak parameter @ upper limit (%g)\n",
            processorOptions.getPeakParameter());
      }

      if (processorOptions.getGaussianBlur() == blurArray[0] && blurArray[0] > 0) {
        append(formatter, "- Gaussian blur @ lower limit (%g)\n",
            processorOptions.getGaussianBlur());
      } else if (processorOptions.getGaussianBlur() == blurArray[blurArray.length - 1]) {
        append(formatter, "- Gaussian blur @ upper limit (%g)\n",
            processorOptions.getGaussianBlur());
      }

      if (processorOptions.getMaxPeaks() == result.results.get(0).count) {
        append(formatter, "- Total peaks == Maximum Peaks (%d)\n", processorOptions.getMaxPeaks());
      }

      if (sb.length() > 0) {
        sb.insert(0,
            "Optimal result ("
                + IJ.d2s(result.results.get(0).metrics[getSortIndex(settings.resultsSortMethod)], 4)
                + ") for " + imp.getShortTitle() + " obtained at the following limits:\n");
        sb.append("You may want to increase the optimisation space.");

        showIncreaseSpaceMessage(sb.toString());
      }
    }
  }

  private static void append(Formatter formatter, String format, Object... args) {
    formatter.format(format, args);
  }

  private synchronized void showIncreaseSpaceMessage(String msg) {
    IJ.log("---");
    IJ.log(msg);

    // Do not show messages when running in batch
    if (!(java.awt.GraphicsEnvironment.isHeadless() || multiMode)) {
      IJ.showMessage(msg);
    }
  }

  /**
   * Gets the score for each item in the results set and sets it to the score field. The score is
   * determined using the configured resultsSortMethod and scoringMode. It is assumed that all the
   * scoring metrics start at zero and higher is better.
   *
   * @param results the results
   * @param scoringMode the scoring mode
   */
  private void getScore(ArrayList<Result> results, int scoringMode) {
    // Extract the score from the results
    final int scoreIndex = getScoreIndex(scoringMode);

    // Only Raw is valid for RMSD (i.e. not Z or relative)
    // as it is not linear or Normally distributed.
    if (scoreIndex == Result.RMSD && scoringMode != SCORE_RAW) {
      scoringMode = SCORE_RAW;
    }

    double[] score = new double[results.size()];
    for (int i = 0; i < score.length; i++) {
      score[i] = results.get(i).metrics[scoreIndex];
    }

    // Perform additional score adjustment
    if (scoringMode == SCORE_Z) {
      // Use the z-score
      final double[] stats = getStatistics(score);
      final double av = stats[0];
      final double sd = stats[1];
      if (sd > 0) {
        final double factor = 1.0 / sd;
        for (int i = 0; i < score.length; i++) {
          score[i] = (score[i] - av) * factor;
        }
      } else {
        score = new double[score.length]; // all have z=0
      }
    } else if (scoringMode == SCORE_RELATIVE) {
      // Use the relative (%) from the top score. Assumes the bottom score is zero.
      final double top = getTop(score);
      final double factor = 100 / top;
      for (int i = 0; i < score.length; i++) {
        score[i] = factor * (score[i] - top);
      }
    }

    // Set the score into the results
    for (int i = 0; i < score.length; i++) {
      results.get(i).metrics[Result.SCORE] = score[i];
    }
  }

  private int getScoreIndex(int scoringMode) {
    switch (scoringMode) {
      case SCORE_RAW:
      case SCORE_Z:
      case SCORE_RELATIVE:
        return getSortIndex(settings.resultsSortMethod);

      // If scoring using the rank then note that the rank was assigned
      // using the chosen metric in resultsSortMethod within sortResults(...)
      case SCORE_RANK:
      default:
        return Result.RANK;
    }
  }

  /**
   * Get the statistics.
   *
   * @param score the score
   * @return The average and standard deviation
   */
  private static double[] getStatistics(double[] score) {
    // Get the average
    double sum = 0.0;
    double sum2 = 0.0;
    final int n = score.length;
    for (final double value : score) {
      sum += value;
      sum2 += (value * value);
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

  /**
   * Get the highest score. Assumes the lowest is zero.
   *
   * @param score the score
   * @return The top score
   */
  private static double getTop(double[] score) {
    double top = score[0];
    for (int i = 1; i < score.length; i++) {
      if (top < score[i]) {
        top = score[i];
      }
    }
    return top;
  }

  /**
   * Enumerate the parameters for FindFoci on the provided image.
   *
   * <p>Returns null if the image is invalid, there are no ROI points inside the mask, the algorithm
   * was cancelled or cannot produce results.
   *
   * @param imp The image
   * @param mask The mask
   * @param ticker the ticker
   * @return The results
   */
  private @Nullable OptimiserResult runOptimiser(ImagePlus imp, ImagePlus mask, Ticker ticker) {
    if (invalidImage(imp)) {
      return null;
    }
    final AssignedPoint[] roiPoints = extractRoiPoints(imp, mask);

    if (roiPoints.length == 0) {
      IJ.showMessage("Error", "Image must have a point ROI or corresponding ROI file");
      return null;
    }

    final ArrayList<Result> results = new ArrayList<>(combinations);

    // Set the threshold for assigning points matches as a fraction of the image size
    final double distanceThreshold =
        getDistanceThreshold(imp, settings.matchSearchMethod, settings.matchSearchDistance);
    final ToDoubleBiFunction<Coordinate, Coordinate> distanceFunction =
        CoordinateUtils.getSquaredDistanceFunction(imp.getCalibration(), is3D(roiPoints));

    // The stopwatch for the total run-time
    final StopWatch sw = new StopWatch();
    // The total time for analysis
    long analysisTime = 0;

    final FindFociBaseProcessor ff = new FindFoci_PlugIn().createFindFociProcessor(imp);
    final FindFociProcessorOptions processorOptions = new FindFociProcessorOptions(true);
    // Only one supported peak method.
    // The parameter values for absolute height and relative height are on a different scale
    // and using both methods is not yet supported.
    processorOptions.setPeakMethod(settings.peakMethod);
    processorOptions.setMaxPeaks(settings.maxPeaks);

    int id = 0;
    for (int blurCount = 0; blurCount < blurArray.length; blurCount++) {
      final double blur = blurArray[blurCount];
      processorOptions.setGaussianBlur(blur);
      final StopWatch sw0 = new StopWatch();
      final ImagePlus imp2 = ff.blur(imp, blur);
      sw0.stop();

      // Iterate over the options
      int thresholdMethodIndex = 0;
      for (int b = 0; b < backgroundMethodArray.length; b++) {
        processorOptions.setBackgroundMethod(backgroundMethodArray[b]);
        if (backgroundMethodArray[b] == BackgroundMethod.AUTO_THRESHOLD) {
          processorOptions.setThresholdMethod(thresholdMethodArray[thresholdMethodIndex++]);
        }

        final StatisticsMethod[] statisticsMethods =
            backgroundMethodHasStatisticsMode(backgroundMethodArray[b]) ? statisticsMethodArray
                : new StatisticsMethod[] {StatisticsMethod.ALL};
        for (final StatisticsMethod statisticsMethod : statisticsMethods) {
          processorOptions.setStatisticsMethod(statisticsMethod);

          final StopWatch sw1 = sw0.create();
          final FindFociInitResults initResults =
              ff.findMaximaInit(imp, imp2, mask, processorOptions);
          sw1.stop();
          if (initResults == null) {
            return null;
          }
          FindFociInitResults searchInitArray = null;

          for (double backgroundParameter = backgroundParameterMinArray[b];
              backgroundParameter <= backgroundParameterMax;
              backgroundParameter += backgroundParameterInterval) {
            // Use zero when there is no parameter
            processorOptions.setBackgroundParameter(
                (backgroundMethodHasParameter(backgroundMethodArray[b])) ? backgroundParameter : 0);

            boolean logBackground = (blurCount == 0) && !multiMode; // Log on first blur iteration

            for (int s = 0; s < searchMethodArray.length; s++) {
              processorOptions.setSearchMethod(searchMethodArray[s]);

              for (double searchParameter = searchParameterMinArray[s];
                  searchParameter <= searchParameterMax;
                  searchParameter += searchParameterInterval) {
                // Use zero when there is no parameter
                processorOptions.setSearchParameter(
                    (searchMethodHasParameter(searchMethodArray[s])) ? searchParameter : 0);

                searchInitArray = ff.copyForStagedProcessing(initResults, searchInitArray);
                final StopWatch sw2 = sw1.create();
                final FindFociSearchResults searchArray =
                    ff.findMaximaSearch(searchInitArray, processorOptions);
                sw2.stop();
                if (searchArray == null) {
                  return null;
                }
                FindFociInitResults mergeInitArray = null;

                if (logBackground) {
                  // Log the background level on the first occurrence
                  final float backgroundLevel = searchInitArray.stats.background;
                  logBackground = false;
                  IJ.log(String.format("Background level - %s %s: %s = %g",
                      backgroundMethodArray[b].getDescription(),
                      backgroundMethodHasStatisticsMode(backgroundMethodArray[b])
                          ? "(" + statisticsMethod + ") "
                          : "",
                      ((backgroundMethodHasParameter(backgroundMethodArray[b]))
                          ? IJ.d2s(backgroundParameter, 2)
                          : processorOptions.getThresholdMethod().getDescription()),
                      backgroundLevel));
                }

                // Note: Currently only 1 PeakMethod is supported so there is no iteration over this
                for (double peakParameter = peakParameterMin; peakParameter <= peakParameterMax;
                    peakParameter += peakParameterInterval) {
                  processorOptions.setPeakParameter(peakParameter);

                  final StopWatch sw3 = sw2.create();
                  final FindFociMergeTempResults mergePeakResults =
                      ff.findMaximaMergePeak(searchInitArray, searchArray, processorOptions);
                  sw3.stop();

                  for (int minSize = minSizeMin; minSize <= minSizeMax;
                      minSize += minSizeInterval) {
                    processorOptions.setMinSize(minSize);

                    final StopWatch sw4 = sw3.create();
                    final FindFociMergeTempResults mergeSizeResults =
                        ff.findMaximaMergeSize(searchInitArray, mergePeakResults, processorOptions);
                    sw4.stop();

                    for (final EnumSet<AlgorithmOption> options : optionsArray) {
                      processorOptions.setOptions(options);

                      mergeInitArray = ff.copyForStagedProcessing(searchInitArray, mergeInitArray);
                      final StopWatch sw5 = sw4.create();
                      final FindFociMergeResults mergeArray = ff
                          .findMaximaMergeFinal(mergeInitArray, mergeSizeResults, processorOptions);
                      sw5.stop();
                      if (mergeArray == null) {
                        return null;
                      }

                      for (final SortMethod sortMethod : sortMethodArray) {
                        processorOptions.setSortMethod(sortMethod);

                        for (int c = 0; c < centreMethodArray.length; c++) {
                          processorOptions.setCentreMethod(centreMethodArray[c]);

                          for (double centreParameter = centreParameterMinArray[c];
                              centreParameter <= centreParameterMaxArray[c];
                              centreParameter += centreParameterIntervalArray[c]) {
                            processorOptions.setCentreParameter(centreParameter);

                            final StopWatch sw6 = sw5.create();
                            final FindFociResults peakResults =
                                ff.findMaximaResults(mergeInitArray, mergeArray, processorOptions);
                            final long time = sw6.stop();

                            ticker.tick();

                            if (peakResults != null) {
                              // Get the results
                              // The analysis time is not included in the speed-up factor
                              final long start = System.nanoTime();
                              final Parameters runOptions = new Parameters(processorOptions);
                              final Result result = analyseResults(id, roiPoints,
                                  peakResults.results, distanceThreshold, runOptions, time,
                                  settings.beta, distanceFunction);
                              results.add(result);
                              analysisTime += System.nanoTime() - start;
                            }

                            id++;
                            if (IJ.escapePressed()) {
                              return null;
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    sw.stop();

    // All possible results sort methods are highest first
    sortResults(results, settings.resultsSortMethod);

    return new OptimiserResult(results, sw.getTime(), analysisTime);
  }

  private void showResults(ImagePlus imp, ImagePlus mask, ArrayList<Result> results,
      int maxResults) {
    try (final BufferedTextWindow bw = createResultsWindow(imp, mask, results)) {
      // Limit the number of results
      int noOfResults = results.size();
      if (maxResults > 0 && noOfResults > maxResults) {
        noOfResults = maxResults;
      }

      final StringBuilder sb = new StringBuilder();
      for (int i = noOfResults; i-- > 0;) {
        final Result result = results.get(i);
        sb.setLength(0);
        sb.append(IJ.d2s(result.metrics[Result.RANK], 0)).append('\t');
        sb.append(result.getParameters());
        sb.append(result.count).append('\t');
        sb.append(result.tp).append('\t');
        sb.append(result.fp).append('\t');
        sb.append(result.fn).append('\t');
        sb.append(IJ.d2s(result.metrics[Result.JACCARD], RESULT_PRECISION)).append('\t');
        sb.append(IJ.d2s(result.metrics[Result.PRECISION], RESULT_PRECISION)).append('\t');
        sb.append(IJ.d2s(result.metrics[Result.RECALL], RESULT_PRECISION)).append('\t');
        sb.append(IJ.d2s(result.metrics[Result.F05], RESULT_PRECISION)).append('\t');
        sb.append(IJ.d2s(result.metrics[Result.F1], RESULT_PRECISION)).append('\t');
        sb.append(IJ.d2s(result.metrics[Result.F2], RESULT_PRECISION)).append('\t');
        sb.append(IJ.d2s(result.metrics[Result.FB], RESULT_PRECISION)).append('\t');
        sb.append(IJ.d2s(result.metrics[Result.SCORE], RESULT_PRECISION)).append('\t');
        sb.append(IJ.d2s(result.metrics[Result.RMSD], RESULT_PRECISION)).append('\t');
        sb.append(IJ.d2s(result.time / 1000000.0, RESULT_PRECISION));
        bw.append(sb.toString());
      }
      bw.append("\n"); // Empty line separator
    }
  }

  /**
   * Saves the optimiser results to the given file. Also saves the predicted points (from the best
   * scoring options) if provided.
   *
   * @param imp the image
   * @param mask the mask
   * @param results the results
   * @param predictedPoints can be null
   * @param resultFile the result file
   */
  private void saveResults(ImagePlus imp, ImagePlus mask, ArrayList<Result> results,
      AssignedPoint[] predictedPoints, String resultFile) {
    if (TextUtils.isNullOrEmpty(resultFile)) {
      return;
    }

    final Parameters bestOptions = results.get(0).options;

    final FindFociOptions options = new FindFociOptions();
    options.getOptions().clear();
    options.setOption(OutputOption.RESULTS_TABLE, true);
    options.setOption(OutputOption.ROI_SELECTION, true);
    options.setOption(OutputOption.MASK_ROI_SELECTION, true);

    // TODO - Add support for saving the channel, slice & frame
    FindFoci_PlugIn.saveParameters(resultFile + ".params", null, null, null, null,
        bestOptions.processorOptions, options);

    try (final BufferedWriter out = createResultsFile(bestOptions, imp, mask, resultFile)) {
      if (out == null) {
        return;
      }

      out.write("#");
      out.newLine();
      out.write("# Results");
      out.newLine();
      out.write(createResultsHeader(true, false)); // Include newline

      // Output all results in ascending rank order
      for (int i = 0; i < results.size(); i++) {
        final Result result = results.get(i);
        final StringBuilder sb = new StringBuilder();
        sb.append(IJ.d2s(result.metrics[Result.RANK], 0)).append('\t');
        sb.append(result.getParameters());
        sb.append(result.count).append('\t');
        sb.append(result.tp).append('\t');
        sb.append(result.fp).append('\t');
        sb.append(result.fn).append('\t');
        sb.append(IJ.d2s(result.metrics[Result.JACCARD], RESULT_PRECISION)).append('\t');
        sb.append(IJ.d2s(result.metrics[Result.PRECISION], RESULT_PRECISION)).append('\t');
        sb.append(IJ.d2s(result.metrics[Result.RECALL], RESULT_PRECISION)).append('\t');
        sb.append(IJ.d2s(result.metrics[Result.F05], RESULT_PRECISION)).append('\t');
        sb.append(IJ.d2s(result.metrics[Result.F1], RESULT_PRECISION)).append('\t');
        sb.append(IJ.d2s(result.metrics[Result.F2], RESULT_PRECISION)).append('\t');
        sb.append(IJ.d2s(result.metrics[Result.FB], RESULT_PRECISION)).append('\t');
        sb.append(IJ.d2s(result.metrics[Result.SCORE], RESULT_PRECISION)).append('\t');
        sb.append(IJ.d2s(result.metrics[Result.RMSD], RESULT_PRECISION)).append('\t');
        sb.append(result.time);
        out.write(sb.toString());
        out.newLine();
      }

      // Save the identified points
      if (predictedPoints != null) {
        AssignedPointUtils.savePoints(predictedPoints, resultFile + ".points.csv");
      }
    } catch (final IOException ex) {
      IJ.log(
          "Failed to write to the output file '" + resultFile + ".points.csv': " + ex.getMessage());
    }
  }

  private static synchronized void addFindFociCommand(BufferedWriter out, Parameters bestOptions,
      String maskTitle) throws IOException {
    if (bestOptions == null) {
      return;
    }

    // This is the only way to clear the recorder.
    // It will save the current optimiser command to the recorder and then clear it.
    Recorder.saveCommand();

    // Use the recorder to build the options for the FindFoci plugin
    final FindFociProcessorOptions processorOptions = bestOptions.processorOptions;
    Recorder.setCommand(FindFoci_PlugIn.TITLE);
    Recorder.recordOption(FindFoci_PlugIn.OPTION_MASK, maskTitle);
    Recorder.recordOption(FindFoci_PlugIn.OPTION_BACKGROUND_METHOD,
        processorOptions.getBackgroundMethod().getDescription());
    Recorder.recordOption(FindFoci_PlugIn.OPTION_BACKGROUND_PARAMETER,
        Double.toString(processorOptions.getBackgroundParameter()));
    Recorder.recordOption(FindFoci_PlugIn.OPTION_AUTO_THRESHOLD,
        processorOptions.getThresholdMethod().getDescription());
    if (backgroundMethodHasStatisticsMode(processorOptions.getBackgroundMethod())) {
      Recorder.recordOption(FindFoci_PlugIn.OPTION_STASTISTICS_MODE,
          processorOptions.getStatisticsMethod().getDescription());
    }
    Recorder.recordOption(FindFoci_PlugIn.OPTION_SEARCH_METHOD,
        processorOptions.getSearchMethod().getDescription());
    Recorder.recordOption(FindFoci_PlugIn.OPTION_SEARCH_PARAMETER,
        Double.toString(processorOptions.getSearchParameter()));
    Recorder.recordOption(FindFoci_PlugIn.OPTION_MINIMUM_SIZE,
        Integer.toString(processorOptions.getMinSize()));
    if (processorOptions.isOption(AlgorithmOption.MINIMUM_ABOVE_SADDLE)) {
      Recorder.recordOption(FindFoci_PlugIn.OPTION_MINIMUM_SIZE_ABOVE_SADDLE);
    }
    if (processorOptions.isOption(AlgorithmOption.CONTIGUOUS_ABOVE_SADDLE)) {
      Recorder.recordOption(FindFoci_PlugIn.OPTION_CONNECTED_ABOVE_SADDLE);
    }
    Recorder.recordOption(FindFoci_PlugIn.OPTION_MINIMUM_PEAK_HEIGHT,
        processorOptions.getPeakMethod().getDescription());
    Recorder.recordOption(FindFoci_PlugIn.OPTION_PEAK_PARAMETER,
        Double.toString(processorOptions.getPeakParameter()));
    Recorder.recordOption(FindFoci_PlugIn.OPTION_SORT_METHOD,
        processorOptions.getSortMethod().getDescription());
    Recorder.recordOption(FindFoci_PlugIn.OPTION_MAXIMUM_PEAKS,
        Integer.toString(processorOptions.getMaxPeaks()));
    Recorder.recordOption(FindFoci_PlugIn.OPTION_SHOW_MASK,
        MaskMethod.PEAKS_ABOVE_SADDLE.getDescription());
    Recorder.recordOption(FindFoci_PlugIn.OPTION_SHOW_TABLE);
    Recorder.recordOption(FindFoci_PlugIn.OPTION_MARK_MAXIMA);
    Recorder.recordOption(FindFoci_PlugIn.OPTION_MARK_PEAK_MAXIMA);
    Recorder.recordOption(FindFoci_PlugIn.OPTION_SHOW_LOG_MESSAGES);
    if (processorOptions.getGaussianBlur() > 0) {
      Recorder.recordOption(FindFoci_PlugIn.OPTION_GAUSSIAN_BLUR,
          Double.toString(processorOptions.getGaussianBlur()));
    }
    Recorder.recordOption(FindFoci_PlugIn.OPTION_CENTRE_METHOD,
        processorOptions.getCentreMethod().getDescription());
    if (processorOptions.getCentreMethod() == CentreMethod.CENTRE_OF_MASS_SEARCH) {
      Recorder.recordOption(FindFoci_PlugIn.OPTION_CENTRE_PARAMETER,
          Double.toString(processorOptions.getCentreParameter()));
    }

    out.write(String.format("# run(\"" + FindFoci_PlugIn.TITLE + "\", \"%s\")%n",
        Recorder.getCommandOptions()));

    // Ensure the new command we have just added does not get recorded
    Recorder.setCommand(null);
  }

  /**
   * Gets the distance threshold. This is suitable for a squared Euclidean distance function.
   *
   * @param imp the imp
   * @param matchSearchMethod the match search method
   * @param matchSearchDistance the match search distance
   * @return the distance threshold
   */
  private static double getDistanceThreshold(ImagePlus imp, int matchSearchMethod,
      double matchSearchDistance) {
    if (matchSearchMethod == 1) {
      return matchSearchDistance * matchSearchDistance;
    }
    final int length = Math.min(imp.getWidth(), imp.getHeight());
    return MathUtils.pow2(Math.ceil(matchSearchDistance * length));
  }

  @Nullable
  private static AssignedPoint[] showResult(ImagePlus imp, ImagePlus mask, Parameters parameters,
      boolean showScoreImages, int matchSearchMethod, double matchSearchDistance) {
    if (imp == null) {
      return null;
    }

    // Clone the input image to allow display of the peaks on the original
    final ImagePlus clone = cloneImage(imp, imp.getTitle() + " clone");
    clone.show();

    final FindFociProcessorOptions processorOptions = parameters.processorOptions.copy();
    processorOptions.setMaskMethod(MaskMethod.PEAKS_ABOVE_SADDLE);
    final FindFociOptions options = new FindFociOptions(true);
    options.setOption(OutputOption.ROI_SELECTION, true);
    options.setOption(OutputOption.MASK_ROI_SELECTION, true);
    final FindFoci_PlugIn ff = new FindFoci_PlugIn();
    ff.exec(clone, mask, processorOptions, options, true);

    // Add 3D support here by getting the results from the results table
    final List<FindFociResult> results = FindFoci_PlugIn.getLastResults();
    final AssignedPoint[] predictedPoints = extractedPredictedPoints(results);
    maskImage(clone, mask);

    if (showScoreImages) {
      final AssignedPoint[] actualPoints = extractRoiPoints(imp, mask);

      final List<Coordinate> truePositives = new LinkedList<>();
      final List<Coordinate> falsePositives = new LinkedList<>();
      final List<Coordinate> falseNegatives = new LinkedList<>();
      final double distanceThreshold =
          getDistanceThreshold(imp, matchSearchMethod, matchSearchDistance);
      final ToDoubleBiFunction<Coordinate, Coordinate> distanceFunction =
          CoordinateUtils.getSquaredDistanceFunction(imp.getCalibration(), is3D(actualPoints));
      MatchCalculator.analyseResultsCoordinates(actualPoints, predictedPoints, distanceThreshold,
          truePositives, falsePositives, falseNegatives, null, distanceFunction);

      // Show image with TP, FP and FN. Use an overlay to support 3D images
      final ImagePlus tpImp = cloneImage(imp, mask, imp.getTitle() + " TP");
      tpImp.setOverlay(createOverlay(truePositives, imp));
      tpImp.show();

      final ImagePlus fpImp = cloneImage(imp, mask, imp.getTitle() + " FP");
      fpImp.setOverlay(createOverlay(falsePositives, imp));
      fpImp.show();

      final ImagePlus fnImp = cloneImage(imp, mask, imp.getTitle() + " FN");
      fnImp.setOverlay(createOverlay(falseNegatives, imp));
      fnImp.show();
    } else {
      // Leaving old results would be confusing so close them
      closeImage(imp.getTitle() + " TP");
      closeImage(imp.getTitle() + " FP");
      closeImage(imp.getTitle() + " FN");
    }

    return predictedPoints;
  }

  private static void closeImage(String title) {
    final ImagePlus imp = WindowManager.getImage(title);
    if (imp != null) {
      imp.close();
    }
  }

  private static ImagePlus cloneImage(ImagePlus imp, String cloneTitle) {
    ImagePlus clone = WindowManager.getImage(cloneTitle);
    if (clone == null) {
      clone = duplicate(imp);
      clone.setTitle(cloneTitle);
    } else {
      clone.setStack(imp.getImageStack().duplicate());
      clone.setDimensions(imp.getNChannels(), imp.getNSlices(), imp.getNFrames());
    }
    clone.setOverlay(null);
    return clone;
  }

  private static ImagePlus cloneImage(ImagePlus imp, ImagePlus mask, String cloneTitle) {
    ImagePlus clone = WindowManager.getImage(cloneTitle);
    final Integer maskId = (mask != null) ? Integer.valueOf(mask.getID()) : 0;
    if (clone == null || !clone.getProperty("IMAGE").equals(imp.getID())
        || !clone.getProperty("MASK").equals(maskId)) {
      if (clone != null) {
        clone.close();
      }
      clone = duplicate(imp);
      clone.setTitle(cloneTitle);
      clone.setProperty("IMAGE", imp.getID());
      clone.setProperty("MASK", maskId);
      clone.setOverlay(null);

      // Exclude outside the mask
      maskImage(clone, mask);
    }
    return clone;
  }

  /**
   * Duplicate the image. This avoids a bug in {@link ImagePlus#duplicate()} where the ROI is killed
   * and the position information of a PointRoi is lost.
   *
   * @param imp the imp
   * @return the image plus
   */
  private static ImagePlus duplicate(ImagePlus imp) {
    // ImagePlus duplicate() does not clone the ROI and the delete removes the position
    // information for PointRois
    final Roi roi = (Roi) imp.getRoi().clone();
    imp.deleteRoi();
    final ImagePlus imp2 = (new Duplicator()).run(imp);
    imp.setRoi(roi);
    return imp2;
  }

  private static void maskImage(ImagePlus clone, ImagePlus mask) {
    if (validMask(clone, mask)) {
      final ImageStack cloneStack = clone.getImageStack();
      final ImageStack maskStack = mask.getImageStack();
      final int ch1 = clone.getChannel();
      final int fr1 = clone.getFrame();
      final int ch2 = mask.getChannel();
      final int fr2 = mask.getFrame();
      for (int slice = 1; slice <= cloneStack.getSize(); slice++) {
        final ImageProcessor ipClone =
            cloneStack.getProcessor(clone.getStackIndex(ch1, slice, fr1));
        final ImageProcessor ipMask = maskStack.getProcessor(mask.getStackIndex(ch2, slice, fr2));

        for (int i = ipClone.getPixelCount(); i-- > 0;) {
          if (ipMask.get(i) == 0) {
            ipClone.set(i, 0);
          }
        }
      }
      clone.updateAndDraw();
    }
  }

  private List<EnumSet<AlgorithmOption>> createOptionsArray() {
    if (settings.minimumAboveSaddle == 0) {
      return Arrays.asList(EnumSet.of(AlgorithmOption.MINIMUM_ABOVE_SADDLE));
    } else if (settings.minimumAboveSaddle == 1) {
      return Arrays.asList(EnumSet.of(AlgorithmOption.MINIMUM_ABOVE_SADDLE,
          AlgorithmOption.CONTIGUOUS_ABOVE_SADDLE));
    } else if (settings.minimumAboveSaddle == 2) {
      return Arrays.asList(EnumSet.noneOf(AlgorithmOption.class));
    }
    // All options
    return Arrays.asList(EnumSet.of(AlgorithmOption.MINIMUM_ABOVE_SADDLE),
        EnumSet.of(AlgorithmOption.MINIMUM_ABOVE_SADDLE, AlgorithmOption.CONTIGUOUS_ABOVE_SADDLE),
        EnumSet.noneOf(AlgorithmOption.class));
  }

  private static boolean invalidImage(ImagePlus imp) {
    if (null == imp) {
      IJ.noImage();
      return true;
    }
    if (!FindFoci_PlugIn.isSupported(imp.getBitDepth())) {
      IJ.showMessage("Error",
          "Only " + FindFoci_PlugIn.SUPPORTED_BIT_DEPTH + " images are supported");
      return true;
    }
    return false;
  }

  private boolean showDialog(ImagePlus imp) {
    // Ensure the Dialog options are recorded. These are used later to write to file.
    final boolean recorderOn = Recorder.record;
    if (!recorderOn) {
      Recorder.saveCommand(); // Clear the old command options
      resetRecorderState(true);
    }

    if (imp == null) {
      multiMode = true;
      if (!showMultiDialog()) {
        resetRecorderState(recorderOn); // Reset the recorder
        return false;
      }
    }

    // Get the optimisation search settings
    settings = Settings.load();
    final ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);

    final List<String> newImageList = (multiMode) ? null : FindFoci_PlugIn.buildMaskList(imp);

    gd.addMessage("Runs the FindFoci algorithm using different parameters.\n"
        + "Results are compared to reference ROI points.\n\n"
        + "Input range fields accept 3 values: min,max,interval\n"
        + "Gaussian blur accepts comma-delimited values for the blur.");

    createPresetSettings();
    gd.addChoice("Settings", presetSettingsNames, presetSettingsNames[0]);

    if (newImageList != null) {
      gd.addChoice("Mask", newImageList.toArray(new String[0]), settings.maskImage);
    }

    // Do not allow background above mean and background absolute to both be enabled.
    gd.addCheckbox("Background_SD_above_mean", settings.backgroundStdDevAboveMean);
    gd.addCheckbox("Background_Absolute",
        !settings.backgroundStdDevAboveMean && settings.backgroundAbsolute);

    gd.addStringField("Background_parameter", settings.backgroundParameter, 12);
    gd.addCheckbox("Background_Auto_Threshold", settings.backgroundAuto);
    gd.addStringField("Auto_threshold", settings.thresholdMethod, 25);
    gd.addStringField("Statistics_mode", settings.statisticsMode, 25);
    gd.addCheckbox("Search_above_background", settings.searchAboveBackground);
    gd.addCheckbox("Search_fraction_of_peak", settings.searchFractionOfPeak);
    gd.addStringField("Search_parameter", settings.searchParameter, 12);
    gd.addChoice("Minimum_peak_height", PeakMethod.getDescriptions(),
        settings.peakMethod.ordinal());
    gd.addStringField("Peak_parameter", settings.peakParameter, 12);
    gd.addStringField("Minimum_size", settings.minSizeParameter, 12);
    gd.addChoice("Minimum_above_saddle", Settings.saddleOptions,
        Settings.saddleOptions[settings.minimumAboveSaddle]);

    gd.addMessage(createSortOptions());

    gd.addStringField("Sort_method", settings.sortMethod);
    gd.addNumericField("Maximum_peaks", settings.maxPeaks, 0);
    gd.addStringField("Gaussian_blur", settings.gaussianBlur);
    gd.addMessage(createCentreOptions());
    gd.addStringField("Centre_method", settings.centreMethod);
    gd.addStringField("Centre_parameter", settings.centreParameter);

    gd.addMessage("Optimisation options:");
    gd.addChoice("Match_search_method", Settings.matchSearchMethods,
        Settings.matchSearchMethods[settings.matchSearchMethod]);
    gd.addNumericField("Match_search_distance", settings.matchSearchDistance, 2);
    gd.addChoice("Result_sort_method", sortMethods, sortMethods[settings.resultsSortMethod]);
    gd.addNumericField("F-beta", settings.beta, 2);
    gd.addNumericField("Maximum_results", settings.maxResults, 0);
    gd.addNumericField("Step_limit", settings.stepLimit, 0);
    if (!multiMode) {
      gd.addCheckbox("Show_score_images", settings.showScoreImages);
      gd.addFilenameField("Result_file", settings.resultFile, 35);

      // Add a message about double clicking the result table to show the result
      gd.addMessage("Note: Double-click an entry in the optimiser results table\n"
          + "to view the FindFoci output. This only works for the most recent\n"
          + "set of results in the table.");
    }

    gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.FIND_FOCI);

    if (!java.awt.GraphicsEnvironment.isHeadless()) {
      checkbox = gd.getCheckboxes();
      choice = gd.getChoices();

      saveCustomSettings(gd);

      // Listen for changes
      addListeners(gd);
    }

    gd.showDialog();
    if (gd.wasCanceled()) {
      resetRecorderState(recorderOn); // Reset the recorder
      return false;
    }

    readDialog(gd, multiMode, recorderOn);

    // Validate the chosen parameters
    if (settings.backgroundStdDevAboveMean && settings.backgroundAbsolute) {
      IJ.error("Cannot optimise background methods 'SD above mean' and 'Absolute' "
          + "using the same parameters");
      return false;
    }
    if (!(settings.backgroundStdDevAboveMean || settings.backgroundAbsolute
        || settings.backgroundAuto)) {
      IJ.error("Require at least one background method");
      return false;
    }
    if (!(settings.searchAboveBackground || settings.searchFractionOfPeak)) {
      IJ.error("Require at least one background search method");
      return false;
    }

    // Check which options to optimise
    optionsArray = createOptionsArray();

    parseThresholdMethods();
    if (settings.backgroundAuto && thresholdMethodArray.length == 0) {
      IJ.error("No recognised methods for auto-threshold");
      return false;
    }

    parseStatisticsModes();

    backgroundMethodArray = createBackgroundArray();
    parseBackgroundLimits();
    if (backgroundParameterMax < backgroundParameterMin) {
      IJ.error("Background parameter max must be greater than min");
      return false;
    }
    backgroundParameterMinArray = createBackgroundLimits();

    searchMethodArray = createSearchArray();
    parseSearchLimits();
    if (searchParameterMax < searchParameterMin) {
      IJ.error("Search parameter max must be greater than min");
      return false;
    }
    searchParameterMinArray = createSearchLimits();

    parseMinSizeLimits();
    if (minSizeMax < minSizeMin) {
      IJ.error("Size max must be greater than min");
      return false;
    }

    // Note: Currently only 1 PeakMethod is supported so there is no iteration over this
    parsePeakParameterLimits();
    if (peakParameterMax < peakParameterMin) {
      IJ.error("Peak parameter max must be greater than min");
      return false;
    }

    sortMethodArray = createSortArray();
    if (sortMethodArray.length == 0) {
      IJ.error("Require at least one sort method");
      return false;
    }

    blurArray = createBlurArray();

    centreMethodArray = createCentreArray();
    parseCentreLimits();
    if (centreParameterMax < centreParameterMin) {
      IJ.error("Centre parameter max must be greater than min");
      return false;
    }
    centreParameterMinArray = createCentreMinLimits();
    centreParameterMaxArray = createCentreMaxLimits();
    centreParameterIntervalArray = createCentreIntervals();

    if (!multiMode) {
      final ImagePlus mask = WindowManager.getImage(settings.maskImage);
      if (!validMask(imp, mask)) {
        statisticsMethodArray = new StatisticsMethod[] {StatisticsMethod.ALL};
      }
    }

    if (settings.matchSearchMethod == 1 && settings.matchSearchDistance < 1) {
      IJ.log("WARNING: Absolute peak match distance is less than 1 pixel: "
          + settings.matchSearchDistance);
    }

    // Count the number of options
    combinations = countSteps();
    if (combinations >= settings.stepLimit) {
      IJ.error("Maximum number of optimisation steps exceeded: " + combinations + " >> "
          + settings.stepLimit);
      return false;
    }

    final YesNoCancelDialog d = new YesNoCancelDialog(IJ.getInstance(), TITLE,
        combinations + " combinations. Do you wish to proceed?");
    return d.yesPressed();
  }

  private boolean showMultiDialog() {
    batchSettings = BatchSettings.load();
    final ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);
    gd.addMessage("Run " + TITLE
        + " on a set of images.\n \nAll images in a directory will be processed.\n \n"
        + "Optional mask images in the input directory should be named:\n"
        + "[image_name].mask.[ext]\n"
        + "or placed in the mask directory with the same name as the parent image.");
    gd.addDirectoryField("Input_directory", batchSettings.inputDirectory);
    gd.addDirectoryField("Mask_directory", batchSettings.maskDirectory);
    gd.addDirectoryField("Output_directory", batchSettings.outputDirectory);
    gd.addMessage("The score metric for each parameter combination is computed per image.\n"
        + "The scores are converted then averaged across all images.");
    gd.addChoice("Score_conversion", SCORING_MODES, SCORING_MODES[batchSettings.scoringMode]);
    gd.addCheckbox("Re-use_results", batchSettings.reuseResults);

    gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.FIND_FOCI);
    gd.showDialog();
    if (gd.wasCanceled()) {
      return false;
    }

    batchSettings.inputDirectory = gd.getNextString();
    batchSettings.maskDirectory = gd.getNextString();
    batchSettings.scoringMode = gd.getNextChoiceIndex();
    batchSettings.reuseResults = gd.getNextBoolean();
    batchSettings.outputDirectory = gd.getNextString();
    batchSettings.save();

    if (!new File(batchSettings.inputDirectory).isDirectory()) {
      IJ.error(TITLE, "Input directory is not a valid directory: " + batchSettings.inputDirectory);
      return false;
    }
    if ((batchSettings.maskDirectory != null && batchSettings.maskDirectory.length() > 0)
        && !new File(batchSettings.maskDirectory).isDirectory()) {
      IJ.error(TITLE, "Mask directory is not a valid directory: " + batchSettings.maskDirectory);
      return false;
    }
    if (!new File(batchSettings.outputDirectory).isDirectory()) {
      IJ.error(TITLE,
          "Output directory is not a valid directory: " + batchSettings.outputDirectory);
      return false;
    }

    batchSettings.inputDirectory = FileUtils.addFileSeparator(batchSettings.inputDirectory);
    batchSettings.maskDirectory = FileUtils.addFileSeparator(batchSettings.maskDirectory);
    batchSettings.outputDirectory = FileUtils.addFileSeparator(batchSettings.outputDirectory);

    return true;
  }

  /**
   * Read the dialog settings into the static fields, save the recorder command options and then
   * reset the recorder state.
   *
   * @param gd the dialog
   * @param multiMode the multi-mode flag
   * @param recorderOn Flag indicating the recorder state
   */
  private void readDialog(ExtendedGenericDialog gd, boolean multiMode, boolean recorderOn) {
    // Ignore the settings field
    gd.getNextChoiceIndex();

    if (!multiMode) {
      settings.maskImage = gd.getNextChoice();
    }
    settings.backgroundStdDevAboveMean = gd.getNextBoolean();
    settings.backgroundAbsolute = gd.getNextBoolean();
    settings.backgroundParameter = gd.getNextString();
    settings.backgroundAuto = gd.getNextBoolean();
    settings.thresholdMethod = gd.getNextString();
    settings.statisticsMode = gd.getNextString();
    settings.searchAboveBackground = gd.getNextBoolean();
    settings.searchFractionOfPeak = gd.getNextBoolean();
    settings.searchParameter = gd.getNextString();
    settings.peakMethod = PeakMethod.fromOrdinal(gd.getNextChoiceIndex());
    settings.peakParameter = gd.getNextString();
    settings.minSizeParameter = gd.getNextString();
    settings.minimumAboveSaddle = gd.getNextChoiceIndex();
    settings.sortMethod = gd.getNextString();
    settings.maxPeaks = (int) gd.getNextNumber();
    settings.gaussianBlur = gd.getNextString();
    settings.centreMethod = gd.getNextString();
    settings.centreParameter = gd.getNextString();

    settings.matchSearchMethod = gd.getNextChoiceIndex();
    settings.matchSearchDistance = gd.getNextNumber();
    settings.resultsSortMethod = gd.getNextChoiceIndex();

    settings.beta = gd.getNextNumber();
    settings.maxResults = (int) gd.getNextNumber();
    settings.stepLimit = (int) gd.getNextNumber();
    if (!multiMode) {
      settings.showScoreImages = gd.getNextBoolean();
      settings.resultFile = gd.getNextString();
    }
    settings.save();

    resetRecorderState(recorderOn); // Reset the recorder

    // This only works if we do not attach as a dialogListener to the GenericDialog
    optimiserCommandOptions = Recorder.getCommandOptions();
  }

  private static void resetRecorderState(boolean record) {
    Recorder.record = record;
  }

  private static String createSortOptions() {
    final StringBuilder sb = new StringBuilder();
    sb.append("Sort options (comma-delimited). Use if total peaks > max peaks:\n");
    addSortOption(sb, SortMethod.COUNT).append("; ");
    addSortOption(sb, SortMethod.INTENSITY).append("; ");
    addSortOption(sb, SortMethod.MAX_VALUE).append("; ");
    addSortOption(sb, SortMethod.AVERAGE_INTENSITY).append(";\n");
    addSortOption(sb, SortMethod.INTENSITY_MINUS_BACKGROUND).append("; ");
    addSortOption(sb, SortMethod.AVERAGE_INTENSITY_MINUS_BACKGROUND).append(";\n");
    addSortOption(sb, SortMethod.SADDLE_HEIGHT).append("; ");
    addSortOption(sb, SortMethod.COUNT_ABOVE_SADDLE).append("; ");
    addSortOption(sb, SortMethod.INTENSITY_ABOVE_SADDLE).append(";\n");
    addSortOption(sb, SortMethod.ABSOLUTE_HEIGHT);
    return sb.toString();
  }

  private static StringBuilder addSortOption(StringBuilder sb, SortMethod method) {
    return sb.append("[").append(method.ordinal()).append("] ").append(method.getDescription());
  }

  private static String createCentreOptions() {
    final StringBuilder sb = new StringBuilder();
    sb.append("Centre options (comma-delimited):\n");
    final CentreMethod[] methods = CentreMethod.values();
    // Ignore the final two on the assumption these are the Gaussian fit methods
    for (int method = 0; method < methods.length - 2; method++) {
      addCentreOption(sb, methods[method]).append("; ");
      if ((method + 1) % 2 == 0) {
        sb.append("\n");
      }
    }
    return sb.toString();
  }

  private static StringBuilder addCentreOption(StringBuilder sb, CentreMethod method) {
    return sb.append("[").append(method.ordinal()).append("] ").append(method.getDescription());
  }

  private void parseThresholdMethods() {
    final String[] values = settings.thresholdMethod.split("\\s*;\\s*|\\s*,\\s*|\\s*:\\s*");
    final LinkedList<ThresholdMethod> methods = new LinkedList<>();
    for (final String method : values) {
      final ThresholdMethod tm = ThresholdMethod.fromDescription(method);
      if (tm != null) {
        methods.add(tm);
      }
    }
    thresholdMethodArray = methods.toArray(new ThresholdMethod[0]);
  }

  private void parseStatisticsModes() {
    final String[] values = settings.statisticsMode.split("\\s*;\\s*|\\s*,\\s*|\\s*:\\s*");
    final LinkedList<StatisticsMethod> modes = new LinkedList<>();
    for (final String mode : values) {
      final StatisticsMethod sm = StatisticsMethod.fromDescription(mode);
      if (sm != null) {
        modes.add(sm);
      }
    }
    if (modes.isEmpty()) {
      modes.add(StatisticsMethod.ALL);
    }
    statisticsMethodArray = modes.toArray(new StatisticsMethod[0]);
  }

  private BackgroundMethod[] createBackgroundArray() {
    final BackgroundMethod[] array = new BackgroundMethod[countBackgroundFlags()];
    int index = 0;
    if (settings.backgroundAbsolute) {
      array[index] = BackgroundMethod.ABSOLUTE;
      index++;
    }
    if (settings.backgroundAuto) {
      for (int i = 0; i < thresholdMethodArray.length; i++) {
        array[index] = BackgroundMethod.AUTO_THRESHOLD;
        index++;
      }
    }
    if (settings.backgroundStdDevAboveMean) {
      array[index] = BackgroundMethod.STD_DEV_ABOVE_MEAN;
    }
    return array;
  }

  private int countBackgroundFlags() {
    int count = 0;
    if (settings.backgroundAbsolute) {
      count++;
    }
    if (settings.backgroundAuto) {
      count += thresholdMethodArray.length;
    }
    if (settings.backgroundStdDevAboveMean) {
      count++;
    }
    return count;
  }

  private void parseBackgroundLimits() {
    double[] values = splitValues(settings.backgroundParameter);
    values = checkValuesTriplet("Background parameter", values, 0, 1);
    backgroundParameterMin = values[0];
    backgroundParameterMax = values[1];
    backgroundParameterInterval = values[2];
  }

  private static double[] checkValuesTriplet(String name, double[] values, double defaultMin,
      double defaultInterval) {
    if (values.length == 0) {
      ImageJUtils.log("%s Warning : %s : No min:max:increment, setting to default minimum %s",
          TITLE, name, MathUtils.rounded(defaultMin));
      return new double[] {defaultMin, defaultMin, defaultInterval};
    }

    double min = values[0];
    if (min < defaultMin) {
      ImageJUtils.log("%s Warning : %s : Minimum below default (%f < %s), setting to default",
          TITLE, name, min, MathUtils.rounded(defaultMin));
      min = defaultMin;
    }

    double max = min;
    double interval = defaultInterval;
    if (values.length > 1) {
      max = values[1];
      if (max < min) {
        ImageJUtils.log("%s Warning : %s : Maximum below minimum (%f < %f), setting to minimum",
            TITLE, name, max, min);
        max = min;
      }

      if (values.length > 2) {
        interval = values[2];
        if (interval <= 0) {
          ImageJUtils.log(
              "%s Warning : %s : Interval is not strictly positive (%f), setting to default (%s)",
              TITLE, name, interval, MathUtils.rounded(defaultInterval));
          interval = defaultInterval;
        }
      }
    }

    return new double[] {min, max, interval};
  }

  private static double[] splitValues(String text) {
    final String[] tokens = text.split(";|,|:");
    final StoredData list = new StoredData(tokens.length);
    for (final String token : tokens) {
      try {
        list.add(Double.parseDouble(token));
      } catch (final NumberFormatException ex) {
        // Ignore - not a double
      }
    }

    return list.getValues();
  }

  private double[] createBackgroundLimits() {
    final double[] limits = new double[backgroundMethodArray.length];
    for (int i = limits.length; i-- > 0;) {
      limits[i] = getBackgroundLimit(backgroundMethodArray[i]);
    }
    return limits;
  }

  private double getBackgroundLimit(BackgroundMethod backgroundMethod) {
    return backgroundMethodHasParameter(backgroundMethod) ? backgroundParameterMin
        : backgroundParameterMax;
  }

  private static boolean backgroundMethodHasStatisticsMode(BackgroundMethod backgroundMethod) {
    return backgroundMethod != BackgroundMethod.NONE
        && backgroundMethod != BackgroundMethod.ABSOLUTE;
  }

  private static boolean backgroundMethodHasParameter(BackgroundMethod backgroundMethod) {
    return !EnumSet
        .of(BackgroundMethod.NONE, BackgroundMethod.MEAN, BackgroundMethod.AUTO_THRESHOLD)
        .contains(backgroundMethod);
  }

  private SearchMethod[] createSearchArray() {
    final SearchMethod[] array = new SearchMethod[countSearchFlags()];
    int count = 0;
    if (settings.searchAboveBackground) {
      array[count] = SearchMethod.ABOVE_BACKGROUND;
      count++;
    }
    if (settings.searchFractionOfPeak) {
      array[count] = SearchMethod.FRACTION_OF_PEAK_MINUS_BACKGROUND;
    }
    return array;
  }

  private int countSearchFlags() {
    int count = 0;
    if (settings.searchAboveBackground) {
      count++;
    }
    if (settings.searchFractionOfPeak) {
      count++;
    }
    return count;
  }

  private void parseSearchLimits() {
    double[] values = splitValues(settings.searchParameter);
    values = checkValuesTriplet("Search parameter", values, 0, 1);
    searchParameterMin = values[0];
    searchParameterMax = values[1];
    searchParameterInterval = values[2];
  }

  private double[] createSearchLimits() {
    final double[] limits = new double[searchMethodArray.length];
    for (int i = limits.length; i-- > 0;) {
      limits[i] = getSearchLimit(searchMethodArray[i]);
    }
    return limits;
  }

  private double getSearchLimit(SearchMethod searchMethod) {
    return searchMethodHasParameter(searchMethod) ? searchParameterMin : searchParameterMax;
  }

  private static boolean searchMethodHasParameter(SearchMethod searchMethod) {
    return searchMethod != SearchMethod.ABOVE_BACKGROUND;
  }

  private void parseMinSizeLimits() {
    double[] values = splitValues(settings.minSizeParameter);
    values = checkValuesTriplet("Min size parameter", values, 1, 1);
    minSizeMin = (int) values[0];
    minSizeMax = (int) values[1];
    minSizeInterval = (int) values[2];
  }

  private void parsePeakParameterLimits() {
    double[] values = splitValues(settings.peakParameter);
    values = checkValuesTriplet("Peak parameter", values, 0, 1);
    peakParameterMin = values[0];
    peakParameterMax = values[1];
    peakParameterInterval = values[2];
  }

  private SortMethod[] createSortArray() {
    final double[] values = splitValues(settings.sortMethod);
    final TIntHashSet set = new TIntHashSet(values.length);
    for (final double v : values) {
      final int method = (int) v;
      if (method >= 0 && method <= SortMethod.AVERAGE_INTENSITY_MINUS_MIN.ordinal()) {
        set.add(method);
      }
    }
    if (set.isEmpty()) {
      ImageJUtils.log("%s Warning : Sort method : No values, setting to default %s", TITLE,
          SortMethod.INTENSITY);
      return new SortMethod[] {SortMethod.INTENSITY}; // Default
    }
    final SortMethod[] array = new SortMethod[set.size()];
    final int[] index = new int[1];
    set.forEach(method -> {
      array[index[0]++] = SortMethod.fromOrdinal(method);
      return true;
    });
    Arrays.sort(array, (o1, o2) -> Integer.compare(o1.ordinal(), o2.ordinal()));
    return array;
  }

  private double[] createBlurArray() {
    final double[] values = splitValues(settings.gaussianBlur);
    final TDoubleHashSet set = new TDoubleHashSet(values.length);
    for (final double v : values) {
      if (v >= 0) {
        set.add(v);
      }
    }
    if (set.isEmpty()) {
      ImageJUtils.log("%s Warning : Gaussian blur : No values, setting to default 0", TITLE);
      return new double[] {0}; // Default
    }
    final double[] array = set.toArray();
    Arrays.sort(array);
    return array;
  }

  private CentreMethod[] createCentreArray() {
    final double[] values = splitValues(settings.centreMethod);
    final TIntHashSet set = new TIntHashSet(values.length);
    for (final double v : values) {
      final int method = (int) v;
      if (method >= 0 && method <= CentreMethod.GAUSSIAN_ORIGINAL.ordinal()) {
        set.add(method);
      }
    }
    if (set.isEmpty()) {
      ImageJUtils.log("%s Warning : Centre method : No values, setting to default %s", TITLE,
          CentreMethod.MAX_VALUE_SEARCH);
      return new CentreMethod[] {CentreMethod.MAX_VALUE_SEARCH}; // Default
    }
    final CentreMethod[] array = new CentreMethod[set.size()];
    final int[] index = new int[1];
    set.forEach(method -> {
      array[index[0]++] = CentreMethod.fromOrdinal(method);
      return true;
    });
    Arrays.sort(array, (o1, o2) -> Integer.compare(o1.ordinal(), o2.ordinal()));
    return array;
  }

  private void parseCentreLimits() {
    double[] values = splitValues(settings.centreParameter);
    values = checkValuesTriplet("Centre parameter", values, 0, 1);
    centreParameterMin = (int) values[0];
    centreParameterMax = (int) values[1];
    centreParameterInterval = (int) values[2];
  }

  private int[] createCentreMinLimits() {
    final int[] limits = new int[centreMethodArray.length];
    for (int i = limits.length; i-- > 0;) {
      limits[i] = getCentreMinLimit(centreMethodArray[i]);
    }
    return limits;
  }

  private int getCentreMinLimit(CentreMethod centreMethod) {
    // If a range has been specified then run the optimiser for average and maximum projection,
    // otherwise use average projection only.
    if (centreMethod == CentreMethod.GAUSSIAN_SEARCH) {
      return (centreParameterMin < centreParameterMax) ? 0 : 1;
    }
    if (centreMethod == CentreMethod.CENTRE_OF_MASS_SEARCH) {
      return centreParameterMin;
    }
    return 0; // Other methods have no parameters
  }

  private int[] createCentreMaxLimits() {
    final int[] limits = new int[centreMethodArray.length];
    for (int i = limits.length; i-- > 0;) {
      limits[i] = getCentreMaxLimit(centreMethodArray[i]);
    }
    return limits;
  }

  private int getCentreMaxLimit(CentreMethod centreMethod) {
    if (centreMethod == CentreMethod.GAUSSIAN_SEARCH) {
      return 1; // Average projection
    }
    if (centreMethod == CentreMethod.CENTRE_OF_MASS_SEARCH) {
      return centreParameterMax; // Limit can be any value above zero
    }
    return 0; // Other methods have no parameters
  }

  private int[] createCentreIntervals() {
    final int[] limits = new int[centreMethodArray.length];
    for (int i = limits.length; i-- > 0;) {
      limits[i] = getCentreInterval(centreMethodArray[i]);
    }
    return limits;
  }

  private int getCentreInterval(CentreMethod centreMethod) {
    if (centreMethod == CentreMethod.GAUSSIAN_SEARCH) {
      return 1;
    }
    return centreParameterInterval;
  }

  /**
   * Count the number of steps up to the maximum allowed steps.
   *
   * <p>This can be used to check the configuration is computationally feasible and that the user
   * has not configured something incorrectly.
   *
   * @return the count
   */
  private int countSteps() {
    final int maxSteps = settings.stepLimit;
    int steps = 0;
    for (int blurCount = 0; blurCount < blurArray.length; blurCount++) {
      for (int b = 0; b < backgroundMethodArray.length; b++) {
        final StatisticsMethod[] statisticsMethods =
            backgroundMethodHasStatisticsMode(backgroundMethodArray[b]) ? statisticsMethodArray
                : new StatisticsMethod[] {StatisticsMethod.ALL};
        for (int i = statisticsMethods.length; i-- != 0;) {
          for (double backgroundParameter = backgroundParameterMinArray[b];
              backgroundParameter <= backgroundParameterMax;
              backgroundParameter += backgroundParameterInterval) {
            for (int s = 0; s < searchMethodArray.length; s++) {
              for (double searchParameter = searchParameterMinArray[s];
                  searchParameter <= searchParameterMax;
                  searchParameter += searchParameterInterval) {
                for (double peakParameter = peakParameterMin; peakParameter <= peakParameterMax;
                    peakParameter += peakParameterInterval) {
                  for (int minSize = minSizeMin; minSize <= minSizeMax;
                      minSize += minSizeInterval) {
                    for (int j = optionsArray.size(); j-- != 0;) {
                      for (int k = sortMethodArray.length; k-- != 0;) {
                        for (int c = 0; c < centreMethodArray.length; c++) {
                          for (double centreParameter = centreParameterMinArray[c];
                              centreParameter <= centreParameterMaxArray[c];
                              centreParameter += centreParameterIntervalArray[c]) {
                            // Stop at the maximum allowed
                            if (++steps >= maxSteps) {
                              return steps;
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    return steps;
  }

  private BufferedTextWindow createResultsWindow(ImagePlus imp, ImagePlus mask,
      ArrayList<Result> results) {
    new LastImageSettings(imp, mask, results, settings.showScoreImages, settings.matchSearchMethod,
        settings.matchSearchDistance).save();
    final TextWindow textWindow = ImageJUtils.refresh(resultsWindow, () -> {
      final String heading = createResultsHeader(true, true);
      final TextWindow window = new TextWindow(TITLE + " Results", heading, "", 1000, 300);
      final TextPanel textPanel = window.getTextPanel();
      textPanel.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent event) {
          if (event.getClickCount() < 2) {
            return;
          }
          final LastImageSettings lastImageSettings = LastImageSettings.load();

          // Show the result that was double clicked in the result table
          if (lastImageSettings.imp != null && lastImageSettings.imp.isVisible()) {
            // An extra line is added at the end of the results so remove this
            final int rank = textPanel.getLineCount() - textPanel.getSelectionStart() - 1;

            // Show the result that was double clicked. Results are stored in reverse order.
            if (rank > 0 && rank <= lastImageSettings.results.size()) {
              showResult(lastImageSettings.imp, lastImageSettings.mask,
                  lastImageSettings.results.get(rank - 1).options,
                  lastImageSettings.showScoreImages, lastImageSettings.matchSearchMethod,
                  lastImageSettings.matchSearchDistance);
            }
          }
        }
      });
      return window;
    });
    final BufferedTextWindow bw = new BufferedTextWindow(textWindow);
    bw.setIncrement(0);
    return bw;
  }

  private BufferedWriter createResultsFile(Parameters bestOptions, ImagePlus imp, ImagePlus mask,
      String resultFile) {
    BufferedWriter out = null;
    try {
      final String filename = resultFile + RESULTS_SUFFIX;

      final Path path = Paths.get(filename);
      FileUtils.createParent(path);

      // Save results to file
      out = Files.newBufferedWriter(path);

      String maskTitle = "";
      if (imp != null) {
        out.write("# ImageJ Script to repeat the optimiser and then run the optimal parameters");
        out.newLine();
        out.write("#");
        out.newLine();
        if (mask != null) {
          out.write("# open(\"" + getFilename(mask) + "\");");
          out.newLine();
          maskTitle = mask.getTitle();
        }
        out.write("# open(\"" + getFilename(imp) + "\");");
        out.newLine();
      }
      // Write the ImageJ macro command
      out.write(String.format("# run(\"FindFoci Optimiser\", \"%s\")%n", optimiserCommandOptions));

      addFindFociCommand(out, bestOptions, maskTitle);

      return out;
    } catch (final Exception ex) {
      IJ.log("Failed to create results file '" + resultFile + ".results.xls': " + ex.getMessage());
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

  private static String getFilename(ImagePlus imp) {
    final FileInfo info = imp.getOriginalFileInfo();
    if (info != null) {
      return Paths.get(info.directory, info.fileName).toString();
    }
    return imp.getTitle();
  }

  private static String createResultsHeader(boolean withScore, boolean milliSeconds) {
    final StringBuilder sb = new StringBuilder();
    sb.append("Rank\t");
    sb.append("Blur\t");
    sb.append("Background method\t");
    sb.append("Max\t");
    sb.append("Min\t");
    sb.append("Search method\t");
    sb.append("Peak method\t");
    sb.append("Sort method\t");
    sb.append("Centre method\t");
    sb.append("N\t");
    sb.append("TP\t");
    sb.append("FP\t");
    sb.append("FN\t");
    sb.append("Jaccard\t");
    sb.append("Precision\t");
    sb.append("Recall\t");
    sb.append("F0.5\t");
    sb.append("F1\t");
    sb.append("F2\t");
    sb.append("F-beta\t");
    if (withScore) {
      sb.append("Score\t");
    }
    sb.append("RMSD\t");
    if (milliSeconds) {
      sb.append("mSec");
    } else {
      sb.append("nanoSec");
    }
    sb.append(System.lineSeparator());
    return sb.toString();
  }

  private static Result analyseResults(int id, AssignedPoint[] roiPoints,
      List<FindFociResult> resultsArray, double distanceThreshold, Parameters options, long time,
      double beta, ToDoubleBiFunction<Coordinate, Coordinate> edges) {
    // Extract results for analysis
    final Coordinate[] predictedPoints = extractedPredictedPoints(resultsArray);

    final MatchResult matchResult = MatchCalculator.analyseResultsCoordinates(roiPoints,
        predictedPoints, distanceThreshold, null, null, null, null, edges);

    return new Result(id, options, matchResult.getNumberPredicted(), matchResult.getTruePositives(),
        matchResult.getFalsePositives(), matchResult.getFalseNegatives(), time, beta,
        matchResult.getRmsd());
  }

  /**
   * Extracted the predicted points.
   *
   * <p>Note: Uses AssignedPoint for convenience so that the points can be saved to file using
   * AssignedPointUtils.
   *
   * @param resultsArray the results array
   * @return the points
   */
  private static AssignedPoint[] extractedPredictedPoints(List<FindFociResult> resultsArray) {
    final AssignedPoint[] predictedPoints = new AssignedPoint[resultsArray.size()];
    for (int i = 0; i < resultsArray.size(); i++) {
      final FindFociResult result = resultsArray.get(i);
      predictedPoints[i] = new AssignedPoint(result.x, result.y, result.z + 1, i);
    }
    return predictedPoints;
  }

  /**
   * Extract the points for the given image. If a file exists in the same directory as the image
   * with the suffix .csv, .xyz, or .txt then the program will attempt to load 3D coordinates from
   * file. Otherwise the points are taken from the the ROI.
   *
   * <p>The points are then filtered to include only those within the mask region (if the mask
   * dimensions match those of the image).
   *
   * @param imp the image
   * @param mask the mask
   * @return the assigned points
   */
  public static AssignedPoint[] extractRoiPoints(ImagePlus imp, ImagePlus mask) {
    AssignedPoint[] roiPoints = null;

    final boolean is3D = imp.getNSlices() > 1;

    roiPoints = loadPointsFromFile(imp);

    if (roiPoints == null) {
      // Extract points using the ROI associated with the image to get z information
      roiPoints = AssignedPointUtils.extractRoiPoints(imp);
    }

    if (!is3D) {
      // Discard any potential z-information since the image is not 3D
      for (final AssignedPoint point : roiPoints) {
        point.z = 0;
      }
    }

    // If the mask is not valid or we have no points then return
    if (!validMask(imp, mask) || roiPoints.length == 0) {
      return roiPoints;
    }

    return restrictToMask(mask, roiPoints);
  }

  /**
   * Restrict the given points to the mask.
   *
   * @param mask the mask
   * @param roiPoints the roi points
   * @return the assigned points
   */
  public static AssignedPoint[] restrictToMask(ImagePlus mask, AssignedPoint[] roiPoints) {
    if (mask == null) {
      return roiPoints;
    }

    // Check that the mask should be used in 3D
    final boolean is3D = is3D(roiPoints) && mask.getNSlices() > 1;

    // Look through the ROI points and exclude all outside the mask
    final ImageStack stack = mask.getStack();
    final int c = mask.getChannel();
    final int f = mask.getFrame();

    int id = 0;
    for (final AssignedPoint point : roiPoints) {
      if (is3D) {
        // Check within the 3D mask
        if (point.z <= mask.getNSlices()) {
          final int stackIndex = mask.getStackIndex(c, point.z, f);
          final ImageProcessor ipMask = stack.getProcessor(stackIndex);

          if (ipMask.get(point.getXint(), point.getYint()) > 0) {
            roiPoints[id++] = point;
          }
        }
      } else {
        // Check all slices of the mask, i.e. a 2D projection
        for (int slice = 1; slice <= mask.getNSlices(); slice++) {
          final int stackIndex = mask.getStackIndex(c, slice, f);
          final ImageProcessor ipMask = stack.getProcessor(stackIndex);

          if (ipMask.get(point.getXint(), point.getYint()) > 0) {
            roiPoints[id++] = point;
            break;
          }
        }
      }
    }

    return Arrays.copyOf(roiPoints, id);
  }

  private static boolean is3D(AssignedPoint[] roiPoints) {
    if (roiPoints.length == 0) {
      return false;
    }

    // All points must have a z-coordinate above zero
    for (final AssignedPoint point : roiPoints) {
      if (point.z < 1) {
        return false;
      }
    }

    return true;
  }

  @Nullable
  private static AssignedPoint[] loadPointsFromFile(ImagePlus imp) {
    final FileInfo fileInfo = imp.getOriginalFileInfo();
    if (fileInfo != null && fileInfo.directory != null) {
      final String title = FileUtils.removeExtension(imp.getTitle());
      for (final String suffix : new String[] {".csv", ".xyz", ".txt"}) {
        final AssignedPoint[] roiPoints = loadPointsFromFile(fileInfo.directory + title + suffix);
        if (roiPoints != null) {
          return roiPoints;
        }
      }
    }
    return null;
  }

  @Nullable
  private static AssignedPoint[] loadPointsFromFile(String filename) {
    final File file = new File(filename);
    if (!file.exists()) {
      return null;
    }

    try (BufferedReader input = Files.newBufferedReader(file.toPath())) {
      String line;
      int id = 0;
      int errors = 0;
      final ArrayList<AssignedPoint> points = new ArrayList<>();
      while ((line = input.readLine()) != null) {
        if (line.length() == 0 || line.charAt(0) == '#') {
          continue;
        }
        final String[] fields = POINTS_PATTERN.split(line);
        if (fields.length > 1) {
          if (addPoint(points, fields, id)) {
            id++;
          } else if (++errors == MAX_ERROR) {
            // Abort if too many errors
            return null;
          }
        }
      }
      return points.toArray(new AssignedPoint[0]);
    } catch (final IOException ex) {
      // ignore
    }
    return null;
  }

  private static boolean addPoint(ArrayList<AssignedPoint> points, String[] fields, int id) {
    try {
      final int x = (int) Double.parseDouble(fields[0]);
      final int y = (int) Double.parseDouble(fields[1]);
      int zposition = 0;
      if (fields.length > 2) {
        zposition = (int) Double.parseDouble(fields[2]);
      }
      points.add(new AssignedPoint(x, y, zposition, id));
      return true;
    } catch (final NumberFormatException ex) {
      return false;
    }
  }

  @SuppressWarnings("unused")
  private static Roi createRoi(List<Coordinate> points) {
    final int[] ox = new int[points.size()];
    final int[] oy = new int[points.size()];

    int index = 0;
    for (final Coordinate point : points) {
      ox[index] = point.getXint();
      oy[index] = point.getYint();
      index++;
    }
    return new PointRoi(ox, oy, ox.length);
  }

  private static Overlay createOverlay(List<Coordinate> points, ImagePlus imp) {
    final int channel = imp.getChannel();
    final int frame = imp.getFrame();
    final boolean isHyperStack = imp.isDisplayedHyperStack();

    final int[] ox = new int[points.size()];
    final int[] oy = new int[points.size()];
    final int[] oz = new int[points.size()];

    int index = 0;
    for (final Coordinate point : points) {
      ox[index] = point.getXint();
      oy[index] = point.getYint();
      oz[index] = point.getZint();
      index++;
    }

    final Overlay overlay = new Overlay();
    int remaining = ox.length;
    for (int ii = 0; ii < ox.length; ii++) {
      // Find the next unprocessed slice
      if (oz[ii] != -1) {
        final int slice = oz[ii];
        // Extract all the points from this slice
        final int[] x = new int[remaining];
        final int[] y = new int[remaining];
        int count = 0;
        for (int j = ii; j < ox.length; j++) {
          if (oz[j] == slice) {
            x[count] = ox[j];
            y[count] = oy[j];
            count++;
            oz[j] = -1; // Mark processed
          }
        }
        final PointRoi roi = new PointRoi(Arrays.copyOf(x, count), Arrays.copyOf(y, count), count);
        if (isHyperStack) {
          roi.setPosition(channel, slice, frame);
        } else {
          roi.setPosition(slice);
        }
        roi.setShowLabels(false);
        overlay.add(roi);
        remaining -= count;
      }
    }

    overlay.setStrokeColor(Color.cyan);

    return overlay;
  }

  private static boolean validMask(ImagePlus imp, ImagePlus mask) {
    return mask != null && mask.getWidth() == imp.getWidth() && mask.getHeight() == imp.getHeight()
        && (mask.getNSlices() == imp.getNSlices() || mask.getStackSize() == 1);
  }

  private static void sortResults(ArrayList<Result> results, int sortMethod) {
    if (sortMethod != SORT_NONE) {
      final int sortIndex = getSortIndex(sortMethod);
      final ResultComparator c = new ResultComparator(sortIndex);
      sortAndAssignRank(results, sortIndex, c);
    }
  }

  private static int getSortIndex(int sortMethod) {
    // Most of the sort methods correspond to the first items of the metrics array
    if (sortMethod <= SORT_JACCARD) {
      return sortMethod - 1;
    }

    // Process special cases
    if (sortMethod == SORT_RMSD) {
      return Result.RMSD;
    }

    // This is an error
    return -1;
  }

  private static void sortResultsByScore(List<Result> results, boolean lowestFirst) {
    final int sortIndex = Result.SCORE;
    final ResultComparator c = new ResultComparator(sortIndex, lowestFirst);
    sortAndAssignRank(results, sortIndex, c);
  }

  /**
   * Sort and assign rank.
   *
   * @param results the results
   * @param sortIndex the sort index
   * @param cmp the comparator
   */
  private static void sortAndAssignRank(List<Result> results, int sortIndex, ResultComparator cmp) {
    Collections.sort(results, cmp);

    // Cannot assign a rank if we have not sorted
    int rank = 1;
    int count = 0;
    double score = results.get(0).metrics[sortIndex];
    for (final Result r : results) {
      if (score != r.metrics[sortIndex]) {
        rank += count;
        count = 0;
        score = r.metrics[sortIndex];
      }
      r.metrics[Result.RANK] = rank;
      count++;
    }
  }

  private static void showOptimiserWindow() {
    final OptimiserView currentView = instance.get();

    if (currentView != null) {
      showView(currentView);
      return;
    }

    IJ.showStatus("Initialising FindFoci Optimiser ...");

    String errorMessage = null;
    Throwable exception = null;

    try {
      Class.forName("org.jdesktop.beansbinding.Property", false,
          FindFociOptimiser_PlugIn.class.getClassLoader());

      // it exists on the classpath
      final OptimiserView view = new OptimiserView();
      view.addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent event) {
          WindowManager.removeWindow(view);
        }
      });
      view.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
      instance.set(view);

      IJ.register(OptimiserView.class);

      showView(view);
      IJ.showStatus("FindFoci Optimiser ready");
    } catch (final ExceptionInInitializerError ex) {
      exception = ex;
      errorMessage = "Failed to initialize class: " + ex.getMessage();
    } catch (final LinkageError ex) {
      exception = ex;
      errorMessage = "Failed to link class: " + ex.getMessage();
    } catch (final ClassNotFoundException ex) {
      exception = ex;
      errorMessage = "Failed to find class: " + ex.getMessage()
          + "\nCheck you have beansbinding-1.2.1.jar on your classpath\n";
    } catch (final Throwable ex) {
      exception = ex;
      errorMessage = ex.getMessage();
    } finally {
      if (exception != null) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);
        pw.write(errorMessage);
        pw.append('\n');
        exception.printStackTrace(pw);
        IJ.log(sw.toString());
      }
    }
  }

  private static void showView(OptimiserView view) {
    WindowManager.addWindow(view);
    view.setVisible(true);
    view.toFront();
  }

  // ---------------------------------------------------------------------------
  // Start preset values.
  // The following code is for setting the dialog fields with preset values
  // ---------------------------------------------------------------------------
  private static class DialogSettings {
    String name;
    ArrayList<String> text = new ArrayList<>();
    ArrayList<Boolean> option = new ArrayList<>();

    public DialogSettings(String name) {
      this.name = name;
    }
  }

  private String[] presetSettingsNames;
  private ArrayList<DialogSettings> presetSettings;

  private long lastTime;
  private boolean custom = true;

  // Store the preset values for the Text fields, Choices, Numeric field.
  // Precede with a '-' character if the field is for single mode only.
  //@formatter:off
  private final String[][] textPreset = new String[][] { { "Testing", // preset.toString()
      // Text fields
      "3", // Background_parameter
      AutoThreshold.Method.OTSU.toString(), // Auto_threshold
      "Both", // Statistics_mode
      "0, 0.6, 0.2", // Search_parameter
      "0, 0.6, 0.2", // Peak_parameter
      "3, 9, 2", // Minimum_size
      "1", // Sort_method
      "1", // Gaussian_blur
      "0", // Centre_method
      "2", // Centre_parameter
      // Choices
      "-", // Mask
      "Yes", // Minimum_above_saddle
      "Relative above background", // Minimum_peak_height
      "Relative", // Match_search_method
      "Jaccard", // Result_sort_method
      // Numeric fields
      "500", // Maximum_peaks
      "0.05", // Match_search_distance
      "4.0", // F-beta
      "100", // Maximum_results
      "10000", // Step_limit
  }, { "Default", // preset.toString()
      // Text fields
      "2.5, 3.5, 0.5", // Background_parameter
      AutoThreshold.Method.OTSU.toString(), // Auto_threshold
      "Both", // Statistics_mode
      "0, 0.6, 0.2", // Search_parameter
      "0, 0.6, 0.2", // Peak_parameter
      "1, 9, 2", // Minimum_size
      "1", // Sort_method
      "0, 0.5, 1", // Gaussian_blur
      "0", // Centre_method
      "2", // Centre_parameter
      // Choices
      "-", // Mask
      "Yes", // Minimum_above_saddle
      "Relative above background", // Minimum_peak_height
      "Relative", // Match_search_method
      "Jaccard", // Result_sort_method
      // Numeric fields
      "500", // Maximum_peaks
      "0.05", // Match_search_distance
      "4.0", // F-beta
      "100", // Maximum_results
      "10000", // Step_limit
  }, { "Benchmark", // preset.toString()
      // Text fields
      "0, 4.7, 0.667", // Background_parameter
      AutoThreshold.Method.OTSU.toString() + ", "+AutoThreshold.Method.RENYI_ENTROPY.toString()+
      ", "+AutoThreshold.Method.TRIANGLE.toString(), // Auto_threshold
      "Both", // Statistics_mode
      "0, 0.8, 0.1", // Search_parameter
      "0, 0.8, 0.1", // Peak_parameter
      "1, 9, 2", // Minimum_size
      "1", // Sort_method
      "0, 0.5, 1, 2", // Gaussian_blur
      "0", // Centre_method
      "2", // Centre_parameter
      // Choices
      "-", // Mask
      "Yes", // Minimum_above_saddle
      "Relative above background", // Minimum_peak_height
      "Relative", // Match_search_method
      "Jaccard", // Result_sort_method
      // Numeric fields
      "500", // Maximum_peaks
      "0.05", // Match_search_distance
      "4.0", // F-beta
      "100", // Maximum_results
      "30000", // Step_limit
  } };
  // Store the preset values for the Checkboxes.
  // Use int so that the flags can be checked if they are for single mode only.
  private static final int FLAG_FALSE = 0;
  private static final int FLAG_TRUE = 1;
  private static final int FLAG_SINGLE = 2;
  private static final int[][] optionPreset = new int[][] { {
      FLAG_FALSE, // Background_SD_above_mean
      FLAG_FALSE, // Background_Absolute
      FLAG_TRUE, // Background_Auto_Threshold
      FLAG_TRUE, // Search_above_background
      FLAG_FALSE, // Search_fraction_of_peak
      FLAG_FALSE + FLAG_SINGLE // Show_score_images
  }, { FLAG_TRUE, // Background_SD_above_mean
      FLAG_FALSE, // Background_Absolute
      FLAG_TRUE, // Background_Auto_Threshold
      FLAG_TRUE, // Search_above_background
      FLAG_TRUE, // Search_fraction_of_peak
      FLAG_FALSE + FLAG_SINGLE // Show_score_images
  }, { FLAG_TRUE, // Background_SD_above_mean
      FLAG_FALSE, // Background_Absolute
      FLAG_TRUE, // Background_Auto_Threshold
      FLAG_TRUE, // Search_above_background
      FLAG_TRUE, // Search_fraction_of_peak
      FLAG_FALSE + FLAG_SINGLE // Show_score_images
  } };
  //@formatter:on

  private void createPresetSettings() {
    presetSettings = new ArrayList<>();

    presetSettings.add(new DialogSettings("Custom"));
    for (int i = 0; i < textPreset.length; i++) {
      // First field is the name
      final DialogSettings s = new DialogSettings(textPreset[i][0]);
      // We only need the rest of the settings if there is a dialog
      if (!java.awt.GraphicsEnvironment.isHeadless()) {
        for (int j = 1; j < textPreset[i].length; j++) {
          if (textPreset[i][j].startsWith("-")) {
            if (multiMode) {
              continue;
            }
            textPreset[i][j] = textPreset[i][j].substring(1);
          }
          s.text.add(textPreset[i][j]);
        }
        for (int j = 0; j < optionPreset[i].length; j++) {
          if (multiMode && (optionPreset[i][j] & FLAG_SINGLE) != 0) {
            continue;
          }
          s.option.add((optionPreset[i][j] & FLAG_TRUE) != 0);
        }
      }
      presetSettings.add(s);
    }

    presetSettingsNames = new String[presetSettings.size()];
    for (int i = 0; i < presetSettings.size(); i++) {
      presetSettingsNames[i] = presetSettings.get(i).name;
    }
  }

  /**
   * Add our own custom listeners to the dialog. If we use dialogListerner in the GenericDialog then
   * it turns the macro recorder off before we read the fields.
   *
   * @param gd the dialog
   */
  private void addListeners(GenericDialog gd) {
    // Add a listener to all the dialog fields
    final TextListener tl = event -> dialogItemChanged(gd, event);
    final ItemListener il = event -> dialogItemChanged(gd, event);

    final Vector<TextField> fields = gd.getStringFields();
    // Optionally Ignore final text field (it is the result file field)
    final int stringFields = fields.size() - ((multiMode) ? 0 : 1);

    for (int i = 0; i < stringFields; i++) {
      fields.get(i).addTextListener(tl);
    }
    for (final Choice field : (Vector<Choice>) gd.getChoices()) {
      field.addItemListener(il);
    }
    for (final TextField field : (Vector<TextField>) gd.getNumericFields()) {
      field.addTextListener(tl);
    }
    for (final Checkbox field : (Vector<Checkbox>) gd.getCheckboxes()) {
      field.addItemListener(il);
    }
  }

  private boolean dialogItemChanged(GenericDialog gd, AWTEvent event) {
    if (event == null || event.getSource() == null || !lock.acquire()) {
      return true;
    }

    try {
      // Check if this is the settings checkbox
      if (event.getSource() == choice.get(0)) {
        final Choice thisChoice = (Choice) choice.get(0);

        // If the choice is currently custom save the current values so they can be restored
        if (custom) {
          saveCustomSettings(gd);
        }

        // Update the other fields with preset values
        final int index = thisChoice.getSelectedIndex();
        if (index != 0) {
          custom = false;
        }
        applySettings(gd, presetSettings.get(index));
      } else if (System.currentTimeMillis() - lastTime > 300) {
        // This is a change to another field. Note that the dialogItemChanged method is called
        // for each field modified in applySettings. This appears to happen after the applySettings
        // method has ended (as if the dialogItemChanged events are in a queue or are delayed until
        // the previous call to dialogItemChanged has ended).
        // To prevent processing these events ignore anything that happens within x milliseconds
        // of the call to applySettings.

        // A change to any other field makes this a custom setting
        // => Set the settings drop-down to custom
        final Choice thisChoice = (Choice) choice.get(0);
        if (thisChoice.getSelectedIndex() != 0) {
          custom = true;
          thisChoice.select(0);
        }

        // Ensure that checkboxes 1 & 2 are complementary
        if (event.getSource() instanceof Checkbox) {
          final Checkbox cb = (Checkbox) event.getSource();
          // If just checked then we must uncheck the complementing checkbox
          if (cb.getState()) {
            // Only checkbox 1 & 2 are complementary
            if (cb.equals(checkbox.get(0))) {
              ((Checkbox) checkbox.get(1)).setState(false);
            } else if (cb.equals(checkbox.get(1))) {
              ((Checkbox) checkbox.get(0)).setState(false);
            }
          }
        }
      }
    } finally {
      lock.release();
    }
    return true;
  }

  private void saveCustomSettings(GenericDialog gd) {
    final DialogSettings s = presetSettings.get(0);
    s.text.clear();
    s.option.clear();
    final Vector<TextField> fields = gd.getStringFields();
    // Optionally Ignore final text field (it is the result file field)
    final int stringFields = fields.size() - ((multiMode) ? 0 : 1);
    for (int i = 0; i < stringFields; i++) {
      s.text.add(fields.get(i).getText());
    }
    // The first choice is the settings name which we ignore
    final Vector<Choice> cfields = gd.getChoices();
    for (int i = 1; i < cfields.size(); i++) {
      s.text.add(cfields.get(i).getSelectedItem());
    }
    for (final TextField field : (Vector<TextField>) gd.getNumericFields()) {
      s.text.add(field.getText());
    }
    for (final Checkbox field : (Vector<Checkbox>) gd.getCheckboxes()) {
      s.option.add(field.getState());
    }
  }

  private void applySettings(GenericDialog gd, DialogSettings settings) {
    lastTime = System.currentTimeMillis();
    int index = 0;
    int index2 = 0;
    final Vector<TextField> fields = gd.getStringFields();
    // Optionally Ignore final text field (it is the result file field)
    final int stringFields = fields.size() - ((multiMode) ? 0 : 1);
    for (int i = 0; i < stringFields; i++) {
      fields.get(i).setText(settings.text.get(index++));
    }
    // The first choice is the settings name
    final Vector<Choice> cfields = gd.getChoices();
    cfields.get(0).select(settings.name);
    for (int i = 1; i < cfields.size(); i++) {
      cfields.get(i).select(settings.text.get(index++));
    }
    for (final TextField field : (Vector<TextField>) gd.getNumericFields()) {
      field.setText(settings.text.get(index++));
    }
    for (final Checkbox field : (Vector<Checkbox>) gd.getCheckboxes()) {
      field.setState(settings.option.get(index2++));
    }
  }

  // ---------------------------------------------------------------------------
  // End preset values
  // ---------------------------------------------------------------------------
}
