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
import uk.ac.sussex.gdsc.core.annotation.Nullable;
import uk.ac.sussex.gdsc.core.ij.ImageJTrackProgress;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
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
import uk.ac.sussex.gdsc.foci.gui.OptimiserView;

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
import ij.plugin.PlugIn;
import ij.plugin.frame.Recorder;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

import org.apache.commons.lang3.ArrayUtils;

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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.swing.WindowConstants;

/**
 * Runs the FindFoci plugin with various settings and compares the results to the reference image
 * point ROI.
 */
public class FindFociOptimiser_PlugIn implements PlugIn {

  private static Logger logger = Logger.getLogger(FindFociOptimiser_PlugIn.class.getName());
  private static final String TITLE = "FindFoci Optimiser";
  private static final int RESULT_PRECISION = 4;

  private static final String INPUT_DIRECTORY = "findfoci.optimiser.inputDirectory";
  private static final String MASK_DIRECTORY = "findfoci.optimiser.maskDirectory";
  private static final String OUTPUT_DIRECTORY = "findfoci.optimiser.outputDirectory";
  private static final String SCORING_MODE = "findfoci.optimiser.scoringMode";
  private static final String REUSE_RESULTS = "findfoci.optimiser.reuseResults";

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

  /** The search methods used for matching. */
  private static final String[] matchSearchMethods = {"Relative", "Absolute"};

  // Copy for convenience
  private static final String[] backgroundMethods = FindFoci_PlugIn.getBackgroundMethods();
  private static final String[] searchMethods = FindFoci_PlugIn.getSearchMethods();
  private static final String[] peakMethods = FindFoci_PlugIn.getPeakMethods();
  private static final String[] maskOptions = FindFoci_PlugIn.getMaskOptions();
  private static final String[] sortIndexMethods = FindFoci_PlugIn.getSortIndexMethods();
  private static final String[] centreMethods = FindFoci_PlugIn.getCentreMethods();
  private static final String[] autoThresholdMethods = FindFoci_PlugIn.getAutoThresholdMethods();
  private static final String[] statisticsModes = FindFoci_PlugIn.getStatisticsModes();

  private static final Pattern TAB_PATTERN = Pattern.compile("\t");
  private static final Pattern pointsPattern = Pattern.compile("[, \t]+");
  /** The maximum errors when reading a file. */
  private static final int MAX_ERROR = 5;

  private static TextWindow resultsWindow;
  private static int stepLimit = 10000;

  private static String myMaskImage = "";
  private static boolean myBackgroundStdDevAboveMean = true;
  private static boolean myBackgroundAuto = true;
  private static boolean myBackgroundAbsolute;
  private static String myBackgroundParameter = "2.5, 3.5, 0.5";
  private static String myThresholdMethod = AutoThreshold.Method.OTSU.toString();
  private static String myStatisticsMode = "Both";
  private static boolean mySearchAboveBackground = true;
  private static boolean mySearchFractionOfPeak = true;
  private static String mySearchParameter = "0, 0.6, 0.2";
  private static String myMinSizeParameter = "1, 9, 2";
  private static String[] saddleOptions = {"Yes", "Yes - Connected", "No", "All"};
  private static int myMinimumAboveSaddle;
  private static int myPeakMethod = FindFociProcessor.PEAK_RELATIVE_ABOVE_BACKGROUND;
  private static String myPeakParameter = "0, 0.6, 0.2";
  private static String mySortMethod = "" + FindFociProcessor.SORT_INTENSITY;
  private static int myMaxPeaks = 500;
  private static String myGaussianBlur = "0, 0.5, 1";
  private static String myCentreMethod = "" + FindFoci_PlugIn.CENTRE_MAX_VALUE_SEARCH;
  private static String myCentreParameter = "2";

  private static final String[] SCORING_MODES =
      new String[] {"Raw score metric", "Relative (% drop from top)", "Z-score", "Rank"};
  private static final int SCORE_RAW = 0;
  private static final int SCORE_RELATIVE = 1;
  private static final int SCORE_Z = 2;
  private static final int SCORE_RANK = 3;

  private static String inputDirectory = Prefs.get(INPUT_DIRECTORY, "");
  private static String maskDirectory = Prefs.get(MASK_DIRECTORY, "");
  private static String outputDirectory = Prefs.get(OUTPUT_DIRECTORY, "");
  private static int scoringMode = Prefs.getInt(SCORING_MODE, SCORE_RAW);
  private static boolean reuseResults = Prefs.getBoolean(REUSE_RESULTS, true);

  private static int myMatchSearchMethod;
  private static double myMatchSearchDistance = 0.05;
  private static int myResultsSortMethod = SORT_JACCARD;
  private static double myBeta = 4.0;
  private static int myMaxResults = 100;
  private static boolean myShowScoreImages;
  private static String myResultFile = "";

  // Stored to allow the display of any of the latest results from the result table
  private static ImagePlus lastImp;
  private static ImagePlus lastMask;
  private static ArrayList<Result> lastResults;
  private static String optimiserCommandOptions;

  private static OptimiserView instance;

  private int[] optionsArray = {};
  private String[] thresholdMethods;
  private String[] statsModes;
  private int[] backgroundMethodArray = {};
  private double myBackgroundParameterMin;
  private double myBackgroundParameterMax;
  private double myBackgroundParameterInterval;
  private double[] myBackgroundParameterMinArray;
  private int[] searchMethodArray = {};
  private double mySearchParameterMin;
  private double mySearchParameterMax;
  private double mySearchParameterInterval;
  private double[] mySearchParameterMinArray;
  private int myMinSizeMin;
  private int myMinSizeMax;
  private int myMinSizeInterval;
  private double myPeakParameterMin;
  private double myPeakParameterMax;
  private double myPeakParameterInterval;
  private int[] sortMethodArray = {};
  private double[] blurArray = {};
  private int[] centreMethodArray = {};
  private int myCentreParameterMin;
  private int myCentreParameterMax;
  private int myCentreParameterInterval;
  private int[] myCentreParameterMinArray;
  private int[] myCentreParameterMaxArray;
  private int[] myCentreParameterIntervalArray;

  // For the multi-image mode
  private boolean multiMode;

  @SuppressWarnings("rawtypes")
  private Vector checkbox;
  @SuppressWarnings("rawtypes")
  private Vector choice;

  // The number of combinations
  private int combinations;

  private final TObjectIntHashMap<String> idMap = new TObjectIntHashMap<>();
  private final TIntObjectHashMap<Options> optionsMap = new TIntObjectHashMap<>();

  private final SoftLock lock = new SoftLock();

  /** {@inheritDoc} */
  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    if (arg.equals("frame")) {
      showOptimiserWindow();
    } else {
      final ImagePlus imp = (arg.equals("multi")) ? null : WindowManager.getCurrentImage();
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
      // Get the list of images
      final String[] imageList = FindFoci_PlugIn.getBatchImages(inputDirectory);
      if (imageList == null || imageList.length == 0) {
        IJ.error(TITLE, "No input images in folder: " + inputDirectory);
        return;
      }

      if (reuseResults && resultsExist(imageList)) {
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
      final Ticker ticker =
          Ticker.createStarted(new ImageJTrackProgress(), (long) combinations * size, true);
      for (final String image : imageList) {
        final OptimisationWorker w = new OptimisationWorker(image, ticker);
        workers.add(w);
        futures.add(threadPool.submit(w));
      }

      // Collect all the results
      ConcurrencyUtils.waitForCompletionUnchecked(futures);
      IJ.showProgress(1);
      IJ.showStatus("");

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
      getScore(results);
      size = allResults.size();
      for (int i = 1; i < size; i++) {
        final ArrayList<Result> results2 = allResults.get(i);
        getScore(results2);
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
        if (myResultsSortMethod == SORT_RMSD) {
          metrics[Result.SCORE] = metrics[Result.RMSD];
        }
      }

      // Now sort the results using the combined scores. Check is the scored metric is lowest first
      final boolean lowestFirst = myResultsSortMethod == SORT_RMSD;
      sortResultsByScore(results, lowestFirst);

      // Output the combined results
      saveResults(null, null, results, null, outputDirectory + "all");

      // Show in a table
      showResults(null, null, results);

      IJ.showStatus("");
    } else {
      final ImagePlus mask = WindowManager.getImage(myMaskImage);

      final OptimiserResult result = runOptimiser(imp, mask,
          Ticker.createStarted(new ImageJTrackProgress(), combinations, false));
      IJ.showProgress(1);

      if (ImageJUtils.isInterrupted()) {
        return;
      }

      if (result == null) {
        IJ.error(TITLE, "No ROI points fall inside the mask image");
        return;
      }

      // For a single image we use the raw score (since no results are combined)
      final ArrayList<Result> results = result.results;
      getScore(results, SCORE_RAW);
      showResults(imp, mask, results);

      // Re-run Find_Peaks and output the best result
      if (!results.isEmpty()) {
        IJ.log(
            "Top result = " + IJ.d2s(results.get(0).metrics[getSortIndex(myResultsSortMethod)], 4));

        final Options bestOptions = results.get(0).options;

        final AssignedPoint[] predictedPoints = showResult(imp, mask, bestOptions);

        saveResults(imp, mask, results, predictedPoints, myResultFile);

        checkOptimisationSpace(result, imp);
      }
    }
  }

  /**
   * Check if the output directory has any results already.
   *
   * @param imageList the image list
   * @return true if results exist
   */
  private static boolean resultsExist(String[] imageList) {
    // List the results in the output directory
    final String[] results =
        new File(outputDirectory).list((dir, name) -> name.endsWith(RESULTS_SUFFIX));

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
    final Options bestOptions = result.results.get(0).options;
    if (bestOptions == null) {
      return;
    }

    if (result.time != 0) {
      final double seconds = result.time / 1e9;
      IJ.log(
          String.format("%s Optimisation time = %.3f sec (%.3f ms / combination). Speed up = %.3fx",
              imp.getTitle(), seconds, result.time / 1e6 / combinations,
              result.total / (double) result.time));
    }

    // Check if a sub-optimal best result was obtained at the limit of the optimisation range
    if (result.results.get(0).metrics[Result.F1] < 1.0) {
      final StringBuilder sb = new StringBuilder();
      if (backgroundMethodHasParameter(bestOptions.backgroundMethod)) {
        if (bestOptions.backgroundParameter == myBackgroundParameterMin) {
          append(sb, "- Background parameter @ lower limit (%g)\n",
              bestOptions.backgroundParameter);
        } else if (bestOptions.backgroundParameter
            + myBackgroundParameterInterval > myBackgroundParameterMax) {
          append(sb, "- Background parameter @ upper limit (%g)\n",
              bestOptions.backgroundParameter);
        }
      }
      if (searchMethodHasParameter(bestOptions.searchMethod)) {
        if (bestOptions.searchParameter == mySearchParameterMin && mySearchParameterMin > 0) {
          append(sb, "- Search parameter @ lower limit (%g)\n", bestOptions.searchParameter);
        } else if (bestOptions.searchParameter + mySearchParameterInterval > mySearchParameterMax
            && mySearchParameterMax < 1) {
          append(sb, "- Search parameter @ upper limit (%g)\n", bestOptions.searchParameter);
        }
      }
      if (bestOptions.minSize == myMinSizeMin && myMinSizeMin > 1) {
        append(sb, "- Min Size @ lower limit (%d)\n", bestOptions.minSize);
      } else if (bestOptions.minSize + myMinSizeInterval > myMinSizeMax) {
        append(sb, "- Min Size @ upper limit (%d)\n", bestOptions.minSize);
      }

      if (bestOptions.peakParameter == myPeakParameterMin && myPeakParameterMin > 0) {
        append(sb, "- Peak parameter @ lower limit (%g)\n", bestOptions.peakParameter);
      } else if (bestOptions.peakParameter + myPeakParameterInterval > myPeakParameterMax
          && myPeakParameterMax < 1) {
        append(sb, "- Peak parameter @ upper limit (%g)\n", bestOptions.peakParameter);
      }

      if (bestOptions.blur == blurArray[0] && blurArray[0] > 0) {
        append(sb, "- Gaussian blur @ lower limit (%g)\n", bestOptions.blur);
      } else if (bestOptions.blur == blurArray[blurArray.length - 1]) {
        append(sb, "- Gaussian blur @ upper limit (%g)\n", bestOptions.blur);
      }

      if (bestOptions.maxPeaks == result.results.get(0).count) {
        append(sb, "- Total peaks == Maximum Peaks (%d)\n", bestOptions.maxPeaks);
      }

      if (sb.length() > 0) {
        sb.insert(0,
            "Optimal result ("
                + IJ.d2s(result.results.get(0).metrics[getSortIndex(myResultsSortMethod)], 4)
                + ") for " + imp.getShortTitle() + " obtained at the following limits:\n");
        sb.append("You may want to increase the optimisation space.");

        showIncreaseSpaceMessage(sb);
      }
    }
  }

  private synchronized void showIncreaseSpaceMessage(StringBuilder sb) {
    IJ.log("---");
    IJ.log(sb.toString());

    // Do not show messages when running in batch
    if (!(java.awt.GraphicsEnvironment.isHeadless() || multiMode)) {
      IJ.showMessage(sb.toString());
    }
  }

  /**
   * Gets the score.
   *
   * @param results the results
   */
  private static void getScore(ArrayList<Result> results) {
    getScore(results, scoringMode);
  }

  /**
   * Gets the score for each item in the results set and sets it to the score field. The score is
   * determined using the configured resultsSortMethod and scoringMode. It is assumed that all the
   * scoring metrics start at zero and higher is better.
   *
   * @param results the results
   * @param scoringMode the scoring mode
   */
  private static void getScore(ArrayList<Result> results, int scoringMode) {
    // Extract the score from the results
    final int scoreIndex = getScoreIndex(scoringMode);

    // Only Raw/Rank are valid for RMSD
    if (scoreIndex == Result.RMSD && (scoringMode != SCORE_RAW || scoringMode != SCORE_RANK)) {
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

  private static int getScoreIndex(int scoringMode) {
    switch (scoringMode) {
      case SCORE_RAW:
      case SCORE_Z:
      case SCORE_RELATIVE:
        return getSortIndex(myResultsSortMethod);

      // If scoring using the rank then note that the rank was assigned
      // using the chosen metric in myResultsSortMethod within sortResults(...)
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

  private class OptimiserResult {
    ArrayList<Result> results;
    long time;
    long total;

    OptimiserResult(ArrayList<Result> results, long time) {
      this.results = results;
      this.time = time;
      total = 0;
      if (results == null) {
        return;
      }
      for (int i = 0; i < results.size(); i++) {
        total += results.get(i).time;
      }
    }
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
    final AssignedPoint[] roiPoints = extractRoiPoints(imp.getRoi(), imp, mask);

    if (roiPoints.length == 0) {
      IJ.showMessage("Error", "Image must have a point ROI or corresponding ROI file");
      return null;
    }

    final boolean is3D = is3D(roiPoints);

    final ArrayList<Result> results = new ArrayList<>(combinations);

    // Set the threshold for assigning points matches as a fraction of the image size
    final double distanceThreshold = getDistanceThreshold(imp);

    final StopWatch sw = new StopWatch();

    final FindFoci_PlugIn ff = new FindFoci_PlugIn();
    int id = 0;
    for (int blurCount = 0; blurCount < blurArray.length; blurCount++) {
      final double blur = blurArray[blurCount];
      final StopWatch sw0 = new StopWatch();
      final ImagePlus imp2 = ff.blur(imp, blur);
      sw0.stop();

      // Iterate over the options
      int thresholdMethodIndex = 0;
      for (int b = 0; b < backgroundMethodArray.length; b++) {
        final String thresholdMethod =
            (backgroundMethodArray[b] == FindFociProcessor.BACKGROUND_AUTO_THRESHOLD)
                ? thresholdMethods[thresholdMethodIndex++]
                : "";

        final String[] myStatsModes =
            backgroundMethodHasStatisticsMode(backgroundMethodArray[b]) ? statsModes
                : new String[] {"Both"};
        for (final String statsMode : myStatsModes) {
          final int statisticsMode = convertStatisticsMode(statsMode);
          final StopWatch sw1 = sw0.create();
          final FindFociInitResults initResults = ff.findMaximaInit(imp, imp2, mask,
              backgroundMethodArray[b], thresholdMethod, statisticsMode);
          sw1.stop();
          if (initResults == null) {
            return null;
          }
          FindFociInitResults searchInitArray = null;

          for (double backgroundParameter = myBackgroundParameterMinArray[b];
              backgroundParameter <= myBackgroundParameterMax;
              backgroundParameter += myBackgroundParameterInterval) {
            boolean logBackground = (blurCount == 0) && !multiMode; // Log on first blur iteration
            // Use zero when there is no parameter
            final double thisBackgroundParameter =
                (backgroundMethodHasParameter(backgroundMethodArray[b])) ? backgroundParameter : 0;

            for (int s = 0; s < searchMethodArray.length; s++) {
              for (double searchParameter = mySearchParameterMinArray[s];
                  searchParameter <= mySearchParameterMax;
                  searchParameter += mySearchParameterInterval) {
                // Use zero when there is no parameter
                final double thisSearchParameter =
                    (searchMethodHasParameter(searchMethodArray[s])) ? searchParameter : 0;

                searchInitArray = ff.copyForStagedProcessing(initResults, searchInitArray);
                final StopWatch sw2 = sw1.create();
                final FindFociSearchResults searchArray =
                    ff.findMaximaSearch(searchInitArray, backgroundMethodArray[b],
                        thisBackgroundParameter, searchMethodArray[s], thisSearchParameter);
                sw2.stop();
                if (searchArray == null) {
                  return null;
                }
                FindFociInitResults mergeInitArray = null;

                if (logBackground) {
                  final float backgroundLevel = searchInitArray.stats.background;
                  logBackground = false;
                  IJ.log(String.format("Background level - %s %s: %s = %g",
                      backgroundMethods[backgroundMethodArray[b]],
                      backgroundMethodHasStatisticsMode(backgroundMethodArray[b])
                          ? "(" + statsMode + ") "
                          : "",
                      ((backgroundMethodHasParameter(backgroundMethodArray[b]))
                          ? IJ.d2s(backgroundParameter, 2)
                          : thresholdMethod),
                      backgroundLevel));
                }

                for (double peakParameter = myPeakParameterMin; peakParameter <= myPeakParameterMax;
                    peakParameter += myPeakParameterInterval) {
                  final StopWatch sw3 = sw2.create();
                  final FindFociMergeTempResults mergePeakResults = ff.findMaximaMergePeak(
                      searchInitArray, searchArray, myPeakMethod, peakParameter);
                  sw3.stop();

                  for (int minSize = myMinSizeMin; minSize <= myMinSizeMax;
                      minSize += myMinSizeInterval) {
                    final StopWatch sw4 = sw3.create();
                    final FindFociMergeTempResults mergeSizeResults =
                        ff.findMaximaMergeSize(searchInitArray, mergePeakResults, minSize);
                    sw4.stop();

                    for (int options : optionsArray) {
                      mergeInitArray = ff.copyForStagedProcessing(searchInitArray, mergeInitArray);
                      final StopWatch sw5 = sw4.create();
                      final FindFociMergeResults mergeArray = ff.findMaximaMergeFinal(
                          mergeInitArray, mergeSizeResults, minSize, options, blur);
                      sw5.stop();
                      if (mergeArray == null) {
                        return null;
                      }

                      options += statisticsMode;
                      for (final int sortMethod : sortMethodArray) {
                        for (int c = 0; c < centreMethodArray.length; c++) {
                          for (double centreParameter = myCentreParameterMinArray[c];
                              centreParameter <= myCentreParameterMaxArray[c];
                              centreParameter += myCentreParameterIntervalArray[c]) {
                            final StopWatch sw6 = sw5.create();
                            final FindFociResults peakResults =
                                ff.findMaximaResults(mergeInitArray, mergeArray, myMaxPeaks,
                                    sortMethod, centreMethodArray[c], centreParameter);
                            final long time = sw6.stop();

                            ticker.tick();

                            if (peakResults != null) {
                              // Get the results
                              final Options runOptions = new Options(blur, backgroundMethodArray[b],
                                  thisBackgroundParameter, thresholdMethod, searchMethodArray[s],
                                  thisSearchParameter, myMaxPeaks, minSize, myPeakMethod,
                                  peakParameter, sortMethod, options, centreMethodArray[c],
                                  centreParameter);
                              final Result result =
                                  analyseResults(id, roiPoints, peakResults.results,
                                      distanceThreshold, runOptions, time, myBeta, is3D);
                              results.add(result);
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
    sortResults(results, myResultsSortMethod);

    return new OptimiserResult(results, sw.getTime());
  }

  private static void showResults(ImagePlus imp, ImagePlus mask, ArrayList<Result> results) {
    createResultsWindow(imp, mask, results);

    // Limit the number of results
    int noOfResults = results.size();
    if (myMaxResults > 0 && noOfResults > myMaxResults) {
      noOfResults = myMaxResults;
    }

    for (int i = noOfResults; i-- > 0;) {
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
      sb.append(IJ.d2s(result.time / 1000000.0, RESULT_PRECISION)).append("\n");
      resultsWindow.append(sb.toString());
    }
    resultsWindow.append("\n");
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
  private static void saveResults(ImagePlus imp, ImagePlus mask, ArrayList<Result> results,
      AssignedPoint[] predictedPoints, String resultFile) {
    if (TextUtils.isNullOrEmpty(resultFile)) {
      return;
    }

    final Options bestOptions = results.get(0).options;

    final double fractionParameter = 0;
    final int outputType =
        FindFociProcessor.OUTPUT_RESULTS_TABLE | FindFociProcessor.OUTPUT_ROI_SELECTION
            | FindFociProcessor.OUTPUT_MASK_ROI_SELECTION | FindFociProcessor.OUTPUT_LOG_MESSAGES;

    // TODO - Add support for saving the channel, slice & frame
    FindFoci_PlugIn.saveParameters(resultFile + ".params", null, null, null, null,
        bestOptions.backgroundMethod, bestOptions.backgroundParameter,
        bestOptions.autoThresholdMethod, bestOptions.searchMethod, bestOptions.searchParameter,
        bestOptions.maxPeaks, bestOptions.minSize, bestOptions.peakMethod,
        bestOptions.peakParameter, outputType, bestOptions.sortIndex, bestOptions.options,
        bestOptions.blur, bestOptions.centreMethod, bestOptions.centreParameter, fractionParameter,
        "");

    try (final BufferedWriter out = createResultsFile(bestOptions, imp, mask, resultFile)) {
      if (out == null) {
        return;
      }

      out.write("#");
      out.newLine();
      out.write("# Results");
      out.newLine();
      out.write("# ");
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

  private static synchronized void addFindFociCommand(BufferedWriter out, Options bestOptions,
      String maskTitle) throws IOException {
    if (bestOptions == null) {
      return;
    }

    // This is the only way to clear the recorder.
    // It will save the current optimiser command to the recorder and then clear it.
    Recorder.saveCommand();

    // Use the recorder to build the options for the FindFoci plugin
    Recorder.setCommand("FindFoci");
    Recorder.recordOption("mask", maskTitle);
    Recorder.recordOption("background_method", backgroundMethods[bestOptions.backgroundMethod]);
    Recorder.recordOption("Background_parameter", "" + bestOptions.backgroundParameter);
    Recorder.recordOption("Auto_threshold", bestOptions.autoThresholdMethod);
    if (backgroundMethodHasStatisticsMode(bestOptions.backgroundMethod)) {
      Recorder.recordOption("Statistics_mode",
          FindFoci_PlugIn.getStatisticsModeFromOptions(bestOptions.options));
    }
    Recorder.recordOption("Search_method", searchMethods[bestOptions.searchMethod]);
    Recorder.recordOption("Search_parameter", "" + bestOptions.searchParameter);
    Recorder.recordOption("Minimum_size", "" + bestOptions.minSize);
    if ((bestOptions.options & FindFociProcessor.OPTION_MINIMUM_ABOVE_SADDLE) != 0) {
      Recorder.recordOption("Minimum_above_saddle");
    }
    if ((bestOptions.options & FindFociProcessor.OPTION_CONTIGUOUS_ABOVE_SADDLE) != 0) {
      Recorder.recordOption("Connected_above_saddle");
    }
    Recorder.recordOption("Minimum_peak_height", peakMethods[bestOptions.peakMethod]);
    Recorder.recordOption("Peak_parameter", "" + bestOptions.peakParameter);
    Recorder.recordOption("Sort_method", sortIndexMethods[bestOptions.sortIndex]);
    Recorder.recordOption("Maximum_peaks", "" + bestOptions.maxPeaks);
    Recorder.recordOption("Show_mask", maskOptions[3]);
    Recorder.recordOption("Show_table");
    Recorder.recordOption("Mark_maxima");
    Recorder.recordOption("Mark_peak_maxima");
    Recorder.recordOption("Show_log_messages");
    if (bestOptions.blur > 0) {
      Recorder.recordOption("Gaussian_blur", "" + bestOptions.blur);
    }
    Recorder.recordOption("Centre_method", centreMethods[bestOptions.centreMethod]);
    if (bestOptions.centreMethod == FindFoci_PlugIn.CENTRE_OF_MASS_SEARCH) {
      Recorder.recordOption("Centre_parameter", "" + bestOptions.centreParameter);
    }

    out.write(String.format("# run(\"FindFoci\", \"%s\")%n", Recorder.getCommandOptions()));

    // Ensure the new command we have just added does not get recorded
    Recorder.setCommand(null);
  }

  private static double getDistanceThreshold(ImagePlus imp) {
    if (myMatchSearchMethod == 1) {
      return myMatchSearchDistance;
    }
    final int length = (imp.getWidth() < imp.getHeight()) ? imp.getWidth() : imp.getHeight();
    return Math.ceil(myMatchSearchDistance * length);
  }

  private static void append(StringBuilder sb, String format, Object... args) {
    sb.append(String.format(format, args));
  }

  private static AssignedPoint[] showResult(ImagePlus imp, ImagePlus mask, Options options) {
    if (imp == null) {
      return null;
    }

    final int outputType = FindFociProcessor.OUTPUT_MASK_PEAKS
        | FindFociProcessor.OUTPUT_MASK_ABOVE_SADDLE | FindFociProcessor.OUTPUT_MASK_ROI_SELECTION
        | FindFociProcessor.OUTPUT_ROI_SELECTION | FindFociProcessor.OUTPUT_LOG_MESSAGES;
    // Clone the input image to allow display of the peaks on the original
    final ImagePlus clone = cloneImage(imp, imp.getTitle() + " clone");
    clone.show();

    final FindFoci_PlugIn ff = new FindFoci_PlugIn();
    ff.exec(clone, mask, options.backgroundMethod, options.backgroundParameter,
        options.autoThresholdMethod, options.searchMethod, options.searchParameter,
        options.maxPeaks, options.minSize, options.peakMethod, options.peakParameter, outputType,
        options.sortIndex, options.options, options.blur, options.centreMethod,
        options.centreParameter, 1);

    // Add 3D support here by getting the results from the results table not the clone image which
    // only supports 2D
    final List<FindFociResult> results = FindFoci_PlugIn.getLastResults();
    final AssignedPoint[] predictedPoints = new AssignedPoint[results.size()];
    for (int i = 0; i < predictedPoints.length; i++) {
      final FindFociResult result = results.get(i);
      predictedPoints[i] = new AssignedPoint(result.x, result.y, result.z + 1, i);
    }
    maskImage(clone, mask);

    if (myShowScoreImages) {
      final AssignedPoint[] actualPoints = extractRoiPoints(imp.getRoi(), imp, mask);

      final List<Coordinate> truePositives = new LinkedList<>();
      final List<Coordinate> falsePositives = new LinkedList<>();
      final List<Coordinate> falseNegatives = new LinkedList<>();
      final boolean is3D = is3D(actualPoints);
      if (is3D) {
        MatchCalculator.analyseResults3D(actualPoints, predictedPoints, getDistanceThreshold(imp),
            truePositives, falsePositives, falseNegatives);
      } else {
        MatchCalculator.analyseResults2D(actualPoints, predictedPoints, getDistanceThreshold(imp),
            truePositives, falsePositives, falseNegatives);
      }

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
      clone = imp.duplicate();
      clone.setTitle(cloneTitle);
    } else {
      clone.setStack(imp.getImageStack().duplicate());
    }
    clone.setOverlay(null);
    return clone;
  }

  private static ImagePlus cloneImage(ImagePlus imp, ImagePlus mask, String cloneTitle) {
    ImagePlus clone = WindowManager.getImage(cloneTitle);
    final Integer maskId = (mask != null) ? Integer.valueOf(mask.getID()) : 0;
    if (clone == null || !clone.getProperty("MASK").equals(maskId)) {
      if (clone != null) {
        clone.close();
      }
      clone = imp.duplicate();
      clone.setTitle(cloneTitle);
      clone.setProperty("MASK", maskId);
      clone.setOverlay(null);

      // Exclude outside the mask
      maskImage(clone, mask);
    }
    return clone;
  }

  private static void maskImage(ImagePlus clone, ImagePlus mask) {
    if (validMask(clone, mask)) {
      final ImageStack cloneStack = clone.getImageStack();
      final ImageStack maskStack = mask.getImageStack();
      final boolean reloadMask = cloneStack.getSize() == maskStack.getSize();
      for (int slice = 1; slice <= cloneStack.getSize(); slice++) {
        final ImageProcessor ipClone = cloneStack.getProcessor(slice);
        final ImageProcessor ipMask = maskStack.getProcessor(reloadMask ? slice : 1);

        for (int i = ipClone.getPixelCount(); i-- > 0;) {
          if (ipMask.get(i) == 0) {
            ipClone.set(i, 0);
          }
        }
      }
      clone.updateAndDraw();
    }
  }

  private static int[] createOptionsArray() {
    if (myMinimumAboveSaddle == 0) {
      return new int[] {FindFociProcessor.OPTION_MINIMUM_ABOVE_SADDLE};
    } else if (myMinimumAboveSaddle == 1) {
      return new int[] {FindFociProcessor.OPTION_MINIMUM_ABOVE_SADDLE
          | FindFociProcessor.OPTION_CONTIGUOUS_ABOVE_SADDLE};
    } else if (myMinimumAboveSaddle == 2) {
      return new int[] {0};
    } else {
      return new int[] {FindFociProcessor.OPTION_MINIMUM_ABOVE_SADDLE,
          FindFociProcessor.OPTION_MINIMUM_ABOVE_SADDLE
              | FindFociProcessor.OPTION_CONTIGUOUS_ABOVE_SADDLE,
          0};
    }
  }

  private static boolean invalidImage(ImagePlus imp) {
    if (null == imp) {
      IJ.noImage();
      return true;
    }
    if (!FindFoci_PlugIn.isSupported(imp.getBitDepth())) {
      IJ.showMessage("Error", "Only " + FindFoci_PlugIn.getSupported() + " images are supported");
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
    final ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);

    final List<String> newImageList = (multiMode) ? null : FindFoci_PlugIn.buildMaskList(imp);

    gd.addMessage("Runs the FindFoci algorithm using different parameters.\n"
        + "Results are compared to reference ROI points.\n\n"
        + "Input range fields accept 3 values: min,max,interval\n"
        + "Gaussian blur accepts comma-delimited values for the blur.");

    createSettings();
    gd.addChoice("Settings", settingsNames, settingsNames[0]);

    if (newImageList != null) {
      gd.addChoice("Mask", newImageList.toArray(new String[newImageList.size()]), myMaskImage);
    }

    // Do not allow background above mean and background absolute to both be enabled.
    gd.addCheckbox("Background_SD_above_mean", myBackgroundStdDevAboveMean);
    gd.addCheckbox("Background_Absolute", !myBackgroundStdDevAboveMean && myBackgroundAbsolute);

    gd.addStringField("Background_parameter", myBackgroundParameter, 12);
    gd.addCheckbox("Background_Auto_Threshold", myBackgroundAuto);
    gd.addStringField("Auto_threshold", myThresholdMethod, 25);
    gd.addStringField("Statistics_mode", myStatisticsMode, 25);
    gd.addCheckbox("Search_above_background", mySearchAboveBackground);
    gd.addCheckbox("Search_fraction_of_peak", mySearchFractionOfPeak);
    gd.addStringField("Search_parameter", mySearchParameter, 12);
    gd.addChoice("Minimum_peak_height", peakMethods, peakMethods[myPeakMethod]);
    gd.addStringField("Peak_parameter", myPeakParameter, 12);
    gd.addStringField("Minimum_size", myMinSizeParameter, 12);
    gd.addChoice("Minimum_above_saddle", saddleOptions, saddleOptions[myMinimumAboveSaddle]);

    gd.addMessage(createSortOptions());

    gd.addStringField("Sort_method", mySortMethod);
    gd.addNumericField("Maximum_peaks", myMaxPeaks, 0);
    gd.addStringField("Gaussian_blur", myGaussianBlur);
    gd.addMessage(createCentreOptions());
    gd.addStringField("Centre_method", myCentreMethod);
    gd.addStringField("Centre_parameter", myCentreParameter);

    gd.addMessage("Optimisation options:");
    gd.addChoice("Match_search_method", matchSearchMethods,
        matchSearchMethods[myMatchSearchMethod]);
    gd.addNumericField("Match_search_distance", myMatchSearchDistance, 2);
    gd.addChoice("Result_sort_method", sortMethods, sortMethods[myResultsSortMethod]);
    gd.addNumericField("F-beta", myBeta, 2);
    gd.addNumericField("Maximum_results", myMaxResults, 0);
    gd.addNumericField("Step_limit", stepLimit, 0);
    if (!multiMode) {
      gd.addCheckbox("Show_score_images", myShowScoreImages);
      gd.addFilenameField("Result_file", myResultFile, 35);

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
    if (myBackgroundStdDevAboveMean && myBackgroundAbsolute) {
      IJ.error("Cannot optimise background methods 'SD above mean' and 'Absolute' "
          + "using the same parameters");
      return false;
    }
    if (!(myBackgroundStdDevAboveMean || myBackgroundAbsolute || myBackgroundAuto)) {
      IJ.error("Require at least one background method");
      return false;
    }
    if (!(mySearchAboveBackground || mySearchFractionOfPeak)) {
      IJ.error("Require at least one background search method");
      return false;
    }

    // Check which options to optimise
    optionsArray = createOptionsArray();

    parseThresholdMethods();
    if (myBackgroundAuto && thresholdMethods.length == 0) {
      IJ.error("No recognised methods for auto-threshold");
      return false;
    }

    parseStatisticsModes();

    backgroundMethodArray = createBackgroundArray();
    parseBackgroundLimits();
    if (myBackgroundParameterMax < myBackgroundParameterMin) {
      IJ.error("Background parameter max must be greater than min");
      return false;
    }
    myBackgroundParameterMinArray = createBackgroundLimits();

    searchMethodArray = createSearchArray();
    parseSearchLimits();
    if (mySearchParameterMax < mySearchParameterMin) {
      IJ.error("Search parameter max must be greater than min");
      return false;
    }
    mySearchParameterMinArray = createSearchLimits();

    parseMinSizeLimits();
    if (myMinSizeMax < myMinSizeMin) {
      IJ.error("Size max must be greater than min");
      return false;
    }

    parsePeakParameterLimits();
    if (myPeakParameterMax < myPeakParameterMin) {
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
    if (myCentreParameterMax < myCentreParameterMin) {
      IJ.error("Centre parameter max must be greater than min");
      return false;
    }
    myCentreParameterMinArray = createCentreMinLimits();
    myCentreParameterMaxArray = createCentreMaxLimits();
    myCentreParameterIntervalArray = createCentreIntervals();

    if (!multiMode) {
      final ImagePlus mask = WindowManager.getImage(myMaskImage);
      if (!validMask(imp, mask)) {
        statsModes = new String[] {"Both"};
      }
    }

    if (myMatchSearchMethod == 1 && myMatchSearchDistance < 1) {
      IJ.log(
          "WARNING: Absolute peak match distance is less than 1 pixel: " + myMatchSearchDistance);
    }

    // Count the number of options
    combinations = countSteps();
    if (combinations >= stepLimit) {
      IJ.error(
          "Maximum number of optimisation steps exceeded: " + combinations + " >> " + stepLimit);
      return false;
    }

    final YesNoCancelDialog d = new YesNoCancelDialog(IJ.getInstance(), TITLE,
        combinations + " combinations. Do you wish to proceed?");
    return d.yesPressed();
  }

  private static boolean showMultiDialog() {
    final ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);
    gd.addMessage("Run " + TITLE
        + " on a set of images.\n \nAll images in a directory will be processed.\n \n"
        + "Optional mask images in the input directory should be named:\n"
        + "[image_name].mask.[ext]\n"
        + "or placed in the mask directory with the same name as the parent image.");
    gd.addDirectoryField("Input_directory", inputDirectory);
    gd.addDirectoryField("Mask_directory", maskDirectory);
    gd.addDirectoryField("Output_directory", outputDirectory);
    gd.addMessage("The score metric for each parameter combination is computed per image.\n"
        + "The scores are converted then averaged across all images.");
    gd.addChoice("Score_conversion", SCORING_MODES, SCORING_MODES[scoringMode]);
    gd.addCheckbox("Re-use_results", reuseResults);

    gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.FIND_FOCI);
    gd.showDialog();
    if (gd.wasCanceled()) {
      return false;
    }

    inputDirectory = gd.getNextString();
    if (!new File(inputDirectory).isDirectory()) {
      IJ.error(TITLE, "Input directory is not a valid directory: " + inputDirectory);
      return false;
    }
    maskDirectory = gd.getNextString();
    if ((maskDirectory != null && maskDirectory.length() > 0)
        && !new File(maskDirectory).isDirectory()) {
      IJ.error(TITLE, "Mask directory is not a valid directory: " + maskDirectory);
      return false;
    }
    outputDirectory = gd.getNextString();
    if (!new File(outputDirectory).isDirectory()) {
      IJ.error(TITLE, "Output directory is not a valid directory: " + outputDirectory);
      return false;
    }

    inputDirectory = FileUtils.addFileSeparator(inputDirectory);
    maskDirectory = FileUtils.addFileSeparator(maskDirectory);
    outputDirectory = FileUtils.addFileSeparator(outputDirectory);

    scoringMode = gd.getNextChoiceIndex();
    reuseResults = gd.getNextBoolean();
    Prefs.set(INPUT_DIRECTORY, inputDirectory);
    Prefs.set(MASK_DIRECTORY, maskDirectory);
    Prefs.set(OUTPUT_DIRECTORY, outputDirectory);
    Prefs.set(SCORING_MODE, scoringMode);
    Prefs.set(REUSE_RESULTS, reuseResults);
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
  private static void readDialog(ExtendedGenericDialog gd, boolean multiMode, boolean recorderOn) {
    // Ignore the settings field
    gd.getNextChoiceIndex();

    if (!multiMode) {
      myMaskImage = gd.getNextChoice();
    }
    myBackgroundStdDevAboveMean = gd.getNextBoolean();
    myBackgroundAbsolute = gd.getNextBoolean();
    myBackgroundParameter = gd.getNextString();
    myBackgroundAuto = gd.getNextBoolean();
    myThresholdMethod = gd.getNextString();
    myStatisticsMode = gd.getNextString();
    mySearchAboveBackground = gd.getNextBoolean();
    mySearchFractionOfPeak = gd.getNextBoolean();
    mySearchParameter = gd.getNextString();
    myPeakMethod = gd.getNextChoiceIndex();
    myPeakParameter = gd.getNextString();
    myMinSizeParameter = gd.getNextString();
    myMinimumAboveSaddle = gd.getNextChoiceIndex();
    mySortMethod = gd.getNextString();
    myMaxPeaks = (int) gd.getNextNumber();
    myGaussianBlur = gd.getNextString();
    myCentreMethod = gd.getNextString();
    myCentreParameter = gd.getNextString();

    myMatchSearchMethod = gd.getNextChoiceIndex();
    myMatchSearchDistance = gd.getNextNumber();
    myResultsSortMethod = gd.getNextChoiceIndex();

    myMaxResults = (int) gd.getNextNumber();
    stepLimit = (int) gd.getNextNumber();
    if (!multiMode) {
      myShowScoreImages = gd.getNextBoolean();
      myResultFile = gd.getNextString();
    }
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
    addSortOption(sb, FindFociProcessor.SORT_COUNT).append("; ");
    addSortOption(sb, FindFociProcessor.SORT_INTENSITY).append("; ");
    addSortOption(sb, FindFociProcessor.SORT_MAX_VALUE).append("; ");
    addSortOption(sb, FindFociProcessor.SORT_AVERAGE_INTENSITY).append(";\n");
    addSortOption(sb, FindFociProcessor.SORT_INTENSITY_MINUS_BACKGROUND).append("; ");
    addSortOption(sb, FindFociProcessor.SORT_AVERAGE_INTENSITY_MINUS_BACKGROUND).append(";\n");
    addSortOption(sb, FindFociProcessor.SORT_SADDLE_HEIGHT).append("; ");
    addSortOption(sb, FindFociProcessor.SORT_COUNT_ABOVE_SADDLE).append("; ");
    addSortOption(sb, FindFociProcessor.SORT_INTENSITY_ABOVE_SADDLE).append(";\n");
    addSortOption(sb, FindFociProcessor.SORT_ABSOLUTE_HEIGHT);
    return sb.toString();
  }

  private static StringBuilder addSortOption(StringBuilder sb, int method) {
    sb.append("[").append(method).append("] ").append(sortIndexMethods[method]);
    return sb;
  }

  private static String createCentreOptions() {
    final StringBuilder sb = new StringBuilder();
    sb.append("Centre options (comma-delimited):\n");
    for (int method = 0; method < 4; method++) {
      addCentreOption(sb, method).append("; ");
      if ((method + 1) % 2 == 0) {
        sb.append("\n");
      }
    }
    return sb.toString();
  }

  private static StringBuilder addCentreOption(StringBuilder sb, int method) {
    sb.append("[").append(method).append("] ").append(centreMethods[method]);
    return sb;
  }

  private void parseThresholdMethods() {
    final String[] values = myThresholdMethod.split("\\s*;\\s*|\\s*,\\s*|\\s*:\\s*");
    final LinkedList<String> methods = new LinkedList<>();
    for (final String method : values) {
      validThresholdMethod(method, methods);
    }
    thresholdMethods = methods.toArray(new String[0]);
  }

  private static void validThresholdMethod(String method, LinkedList<String> methods) {
    for (final String m : autoThresholdMethods) {
      if (m.equalsIgnoreCase(method)) {
        methods.add(m);
        return;
      }
    }
  }

  private void parseStatisticsModes() {
    final String[] values = myStatisticsMode.split("\\s*;\\s*|\\s*,\\s*|\\s*:\\s*");
    final LinkedList<String> modes = new LinkedList<>();
    for (final String mode : values) {
      validStatisticsMode(mode, modes);
    }
    if (modes.isEmpty()) {
      modes.add("both");
    }
    statsModes = modes.toArray(new String[0]);
  }

  private static void validStatisticsMode(String mode, LinkedList<String> modes) {
    for (final String m : statisticsModes) {
      if (m.equalsIgnoreCase(mode)) {
        modes.add(m);
        return;
      }
    }
  }

  private static int convertStatisticsMode(String mode) {
    if (mode.equalsIgnoreCase("inside")) {
      return FindFociProcessor.OPTION_STATS_INSIDE;
    }
    if (mode.equalsIgnoreCase("outside")) {
      return FindFociProcessor.OPTION_STATS_OUTSIDE;
    }
    return 0;
  }

  private int[] createBackgroundArray() {
    final int[] array = new int[countBackgroundFlags()];
    int index = 0;
    if (myBackgroundAbsolute) {
      array[index] = FindFociProcessor.BACKGROUND_ABSOLUTE;
      index++;
    }
    if (myBackgroundAuto) {
      for (int i = 0; i < thresholdMethods.length; i++) {
        array[index] = FindFociProcessor.BACKGROUND_AUTO_THRESHOLD;
        index++;
      }
    }
    if (myBackgroundStdDevAboveMean) {
      array[index] = FindFociProcessor.BACKGROUND_STD_DEV_ABOVE_MEAN;
    }
    return array;
  }

  private int countBackgroundFlags() {
    int count = 0;
    if (myBackgroundAbsolute) {
      count++;
    }
    if (myBackgroundAuto) {
      count += thresholdMethods.length;
    }
    if (myBackgroundStdDevAboveMean) {
      count++;
    }
    return count;
  }

  private void parseBackgroundLimits() {
    double[] values = splitValues(myBackgroundParameter);
    values = checkValuesTriplet("Background parameter", values, 0, 1);
    myBackgroundParameterMin = values[0];
    myBackgroundParameterMax = values[1];
    myBackgroundParameterInterval = values[2];
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
      } catch (final Exception ex) {
        // Ignore
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

  private double getBackgroundLimit(int backgroundMethod) {
    return backgroundMethodHasParameter(backgroundMethod) ? myBackgroundParameterMin
        : myBackgroundParameterMax;
  }

  private static boolean backgroundMethodHasStatisticsMode(int backgroundMethod) {
    return !(backgroundMethod == FindFociProcessor.BACKGROUND_NONE
        || backgroundMethod == FindFociProcessor.BACKGROUND_ABSOLUTE);
  }

  private static boolean backgroundMethodHasParameter(int backgroundMethod) {
    return !(backgroundMethod == FindFociProcessor.BACKGROUND_NONE
        || backgroundMethod == FindFociProcessor.BACKGROUND_MEAN
        || backgroundMethod == FindFociProcessor.BACKGROUND_AUTO_THRESHOLD);
  }

  private static int[] createSearchArray() {
    final int[] array = new int[countSearchFlags()];
    int count = 0;
    if (mySearchAboveBackground) {
      array[count] = FindFociProcessor.SEARCH_ABOVE_BACKGROUND;
      count++;
    }
    if (mySearchFractionOfPeak) {
      array[count] = FindFociProcessor.SEARCH_FRACTION_OF_PEAK_MINUS_BACKGROUND;
    }
    return array;
  }

  private static int countSearchFlags() {
    int count = 0;
    if (mySearchAboveBackground) {
      count++;
    }
    if (mySearchFractionOfPeak) {
      count++;
    }
    return count;
  }

  private void parseSearchLimits() {
    double[] values = splitValues(mySearchParameter);
    values = checkValuesTriplet("Search parameter", values, 0, 1);
    mySearchParameterMin = values[0];
    mySearchParameterMax = values[1];
    mySearchParameterInterval = values[2];
  }

  private double[] createSearchLimits() {
    final double[] limits = new double[searchMethodArray.length];
    for (int i = limits.length; i-- > 0;) {
      limits[i] = getSearchLimit(searchMethodArray[i]);
    }
    return limits;
  }

  private double getSearchLimit(int searchMethod) {
    return searchMethodHasParameter(searchMethod) ? mySearchParameterMin : mySearchParameterMax;
  }

  private static boolean searchMethodHasParameter(int searchMethod) {
    return searchMethod != FindFociProcessor.SEARCH_ABOVE_BACKGROUND;
  }

  private void parseMinSizeLimits() {
    double[] values = splitValues(myMinSizeParameter);
    values = checkValuesTriplet("Min size parameter", values, 1, 1);
    myMinSizeMin = (int) values[0];
    myMinSizeMax = (int) values[1];
    myMinSizeInterval = (int) values[2];
  }

  private void parsePeakParameterLimits() {
    double[] values = splitValues(myPeakParameter);
    values = checkValuesTriplet("Peak parameter", values, 0, 1);
    myPeakParameterMin = values[0];
    myPeakParameterMax = values[1];
    myPeakParameterInterval = values[2];
  }

  private static int[] createSortArray() {
    final double[] values = splitValues(mySortMethod);
    final TIntHashSet set = new TIntHashSet(values.length);
    for (final double v : values) {
      final int method = (int) v;
      if (method >= 0 && method <= FindFociProcessor.SORT_AVERAGE_INTENSITY_MINUS_MIN) {
        set.add(method);
      }
    }
    if (set.isEmpty()) {
      ImageJUtils.log("%s Warning : Sort method : No values, setting to default %d", TITLE,
          FindFociProcessor.SORT_INTENSITY);
      return new int[] {FindFociProcessor.SORT_INTENSITY}; // Default
    }
    final int[] array = set.toArray();
    Arrays.sort(array);
    return array;
  }

  private static double[] createBlurArray() {
    final double[] values = splitValues(myGaussianBlur);
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

  private static int[] createCentreArray() {
    final double[] values = splitValues(myCentreMethod);
    final TIntHashSet set = new TIntHashSet(values.length);
    for (final double v : values) {
      final int method = (int) v;
      if (method >= 0 && method <= FindFoci_PlugIn.CENTRE_GAUSSIAN_ORIGINAL) {
        set.add(method);
      }
    }
    if (set.isEmpty()) {
      ImageJUtils.log("%s Warning : Centre method : No values, setting to default %d", TITLE,
          FindFoci_PlugIn.CENTRE_MAX_VALUE_SEARCH);
      return new int[] {FindFoci_PlugIn.CENTRE_MAX_VALUE_SEARCH}; // Default
    }
    final int[] array = set.toArray();
    Arrays.sort(array);
    return array;
  }

  private void parseCentreLimits() {
    double[] values = splitValues(myCentreParameter);
    values = checkValuesTriplet("Centre parameter", values, 0, 1);
    myCentreParameterMin = (int) values[0];
    myCentreParameterMax = (int) values[1];
    myCentreParameterInterval = (int) values[2];
  }

  private int[] createCentreMinLimits() {
    final int[] limits = new int[centreMethodArray.length];
    for (int i = limits.length; i-- > 0;) {
      limits[i] = getCentreMinLimit(centreMethodArray[i]);
    }
    return limits;
  }

  private int getCentreMinLimit(int centreMethod) {
    // If a range has been specified then run the optimiser for average and maximum projection,
    // otherwise use average projection only.
    if (centreMethod == FindFoci_PlugIn.CENTRE_GAUSSIAN_SEARCH) {
      return (myCentreParameterMin < myCentreParameterMax) ? 0 : 1;
    }

    if (centreMethod == FindFoci_PlugIn.CENTRE_OF_MASS_SEARCH) {
      return myCentreParameterMin;
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

  private int getCentreMaxLimit(int centreMethod) {
    if (centreMethod == FindFoci_PlugIn.CENTRE_GAUSSIAN_SEARCH) {
      return 1; // Average projection
    }

    if (centreMethod == FindFoci_PlugIn.CENTRE_OF_MASS_SEARCH) {
      return myCentreParameterMax; // Limit can be any value above zero
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

  private int getCentreInterval(int centreMethod) {
    if (centreMethod == FindFoci_PlugIn.CENTRE_GAUSSIAN_SEARCH) {
      return 1;
    }
    return myCentreParameterInterval;
  }

  private static boolean centreMethodHasParameter(int centreMethod) {
    return (centreMethod == FindFoci_PlugIn.CENTRE_OF_MASS_SEARCH
        || centreMethod == FindFoci_PlugIn.CENTRE_GAUSSIAN_SEARCH);
  }

  // Do not get warnings about unused variables
  @SuppressWarnings("unused")
  private int countSteps() {
    final int maxSteps = stepLimit;
    int steps = 0;
    for (final double blur : blurArray) {
      for (int b = 0; b < backgroundMethodArray.length; b++) {
        final String[] myStatsModes =
            backgroundMethodHasStatisticsMode(backgroundMethodArray[b]) ? statsModes
                : new String[] {"Both"};
        for (final String statsMode : myStatsModes) {
          for (double backgroundParameter = myBackgroundParameterMinArray[b];
              backgroundParameter <= myBackgroundParameterMax;
              backgroundParameter += myBackgroundParameterInterval) {
            for (int minSize = myMinSizeMin; minSize <= myMinSizeMax;
                minSize += myMinSizeInterval) {
              for (int s = 0; s < searchMethodArray.length; s++) {
                for (double searchParameter = mySearchParameterMinArray[s];
                    searchParameter <= mySearchParameterMax;
                    searchParameter += mySearchParameterInterval) {
                  for (double peakParameter = myPeakParameterMin;
                      peakParameter <= myPeakParameterMax;
                      peakParameter += myPeakParameterInterval) {
                    for (final int options : optionsArray) {
                      for (final int sortMethod : sortMethodArray) {
                        for (int c = 0; c < centreMethodArray.length; c++) {
                          for (double centreParameter = myCentreParameterMinArray[c];
                              centreParameter <= myCentreParameterMaxArray[c];
                              centreParameter += myCentreParameterIntervalArray[c]) {
                            // Simple check to ensure the user has not configured something
                            // incorrectly
                            if (steps++ >= maxSteps) {
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

  private static void createResultsWindow(ImagePlus imp, ImagePlus mask,
      ArrayList<Result> results) {
    String heading = null;
    lastImp = imp;
    lastMask = mask;
    lastResults = results;
    if (resultsWindow == null || !resultsWindow.isShowing()) {
      heading = createResultsHeader(true, true);
      resultsWindow = new TextWindow(TITLE + " Results", heading, "", 1000, 300);
      if (resultsWindow.getTextPanel() != null) {
        resultsWindow.getTextPanel().addMouseListener(new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent event) {
            // Show the result that was double clicked in the result table
            if (event.getClickCount() > 1 && lastImp != null && lastImp.isVisible()) {
              // An extra line is added at the end of the results so remove this
              final int rank = resultsWindow.getTextPanel().getLineCount()
                  - resultsWindow.getTextPanel().getSelectionStart() - 1;

              // Show the result that was double clicked. Results are stored in reverse order.
              if (rank > 0 && rank <= lastResults.size()) {
                showResult(lastImp, lastMask, lastResults.get(rank - 1).options);
              }
            }
          }
        });
      }
    }
  }

  private static BufferedWriter createResultsFile(Options bestOptions, ImagePlus imp,
      ImagePlus mask, String resultFile) {
    BufferedWriter out = null;
    try {
      final String filename = resultFile + RESULTS_SUFFIX;

      final File file = new File(filename);
      if (!file.exists() && file.getParent() != null) {
        new File(file.getParent()).mkdirs();
      }

      // Save results to file
      out = Files.newBufferedWriter(Paths.get(filename));

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
      List<FindFociResult> resultsArray, double distanceThreshold, Options options, long time,
      double beta, boolean is3D) {
    // Extract results for analysis
    final AssignedPoint[] predictedPoints = extractedPredictedPoints(resultsArray);

    MatchResult matchResult;
    if (is3D) {
      matchResult = MatchCalculator.analyseResults3D(roiPoints, predictedPoints, distanceThreshold);
    } else {
      matchResult = MatchCalculator.analyseResults2D(roiPoints, predictedPoints, distanceThreshold);
    }

    return new Result(id, options, matchResult.getNumberPredicted(), matchResult.getTruePositives(),
        matchResult.getFalsePositives(), matchResult.getFalseNegatives(), time, beta,
        matchResult.getRmsd());
  }

  private static AssignedPoint[] extractedPredictedPoints(List<FindFociResult> resultsArray) {
    final AssignedPoint[] predictedPoints = new AssignedPoint[resultsArray.size()];
    for (int i = 0; i < resultsArray.size(); i++) {
      final FindFociResult result = resultsArray.get(i);
      predictedPoints[i] = new AssignedPoint(result.x, result.y, result.z, i);
    }
    return predictedPoints;
  }

  /**
   * Convert the FindFoci parameters into a text representation.
   *
   * @param blur the blur
   * @param backgroundMethod the background method
   * @param thresholdMethod the threshold method
   * @param backgroundParameter the background parameter
   * @param maxPeaks the max peaks
   * @param minSize the min size
   * @param searchMethod the search method
   * @param searchParameter the search parameter
   * @param peakMethod the peak method
   * @param peakParameter the peak parameter
   * @param sortMethod the sort method
   * @param options the options
   * @param centreMethod the centre method
   * @param centreParameter the centre parameter
   * @return the string
   */
  static String createParameters(double blur, int backgroundMethod, String thresholdMethod,
      double backgroundParameter, int maxPeaks, int minSize, int searchMethod,
      double searchParameter, int peakMethod, double peakParameter, int sortMethod, int options,
      int centreMethod, double centreParameter) {
    // Output results
    final String spacer = " : ";
    final StringBuilder sb = new StringBuilder();
    sb.append(blur).append('\t');
    sb.append(backgroundMethods[backgroundMethod]);
    if (backgroundMethodHasStatisticsMode(backgroundMethod)) {
      sb.append(" (").append(FindFoci_PlugIn.getStatisticsModeFromOptions(options)).append(") ");
    }
    sb.append(spacer);
    sb.append(backgroundMethodHasParameter(backgroundMethod) ? IJ.d2s(backgroundParameter, 2)
        : thresholdMethod).append('\t');
    sb.append(maxPeaks).append('\t');
    sb.append(minSize);
    if ((options & FindFociProcessor.OPTION_MINIMUM_ABOVE_SADDLE) != 0) {
      sb.append(" >saddle");
      if ((options & FindFociProcessor.OPTION_CONTIGUOUS_ABOVE_SADDLE) != 0) {
        sb.append(" conn");
      }
    }
    sb.append('\t');
    sb.append(searchMethods[searchMethod]);
    if (searchMethodHasParameter(searchMethod)) {
      sb.append(spacer).append(IJ.d2s(searchParameter, 2));
    }
    sb.append('\t');
    sb.append(peakMethods[peakMethod]).append(spacer);
    sb.append(IJ.d2s(peakParameter, 2)).append('\t');
    sb.append(sortIndexMethods[sortMethod]).append('\t');
    sb.append(centreMethods[centreMethod]);
    if (centreMethodHasParameter(centreMethod)) {
      sb.append(spacer).append(IJ.d2s(centreParameter, 2));
    }
    sb.append('\t');
    return sb.toString();
  }

  /**
   * Convert the FindFoci text representation into Options.
   *
   * @param parameters the parameters
   * @return the options
   */
  private static Options createOptions(String parameters) {
    final String[] fields = TAB_PATTERN.split(parameters);
    try {
      final double blur = Double.parseDouble(fields[0]);
      int backgroundMethod = -1;
      for (int i = 0; i < backgroundMethods.length; i++) {
        if (fields[1].startsWith(backgroundMethods[i])) {
          backgroundMethod = i;
          break;
        }
      }
      if (backgroundMethod < 0) {
        throw new IllegalArgumentException("No background method");
      }
      int options = 0;
      if (backgroundMethodHasStatisticsMode(backgroundMethod)) {
        final int first = fields[1].indexOf('(') + 1;
        final int last = fields[1].indexOf(')', first);
        final String mode = fields[1].substring(first, last);
        if (mode.equals("Inside")) {
          options |= FindFociProcessor.OPTION_STATS_INSIDE;
        } else if (mode.equals("Outside")) {
          options |= FindFociProcessor.OPTION_STATS_OUTSIDE;
        } else {
          options |= FindFociProcessor.OPTION_STATS_INSIDE | FindFociProcessor.OPTION_STATS_OUTSIDE;
        }
      }
      int index = fields[1].indexOf(" : ") + 3;
      String thresholdMethod = fields[1].substring(index);
      double backgroundParameter = 0;
      if (backgroundMethodHasParameter(backgroundMethod)) {
        backgroundParameter = Double.parseDouble(thresholdMethod);
        thresholdMethod = "";
      }
      final int maxPeaks = Integer.parseInt(fields[2]);
      // XXX
      index = fields[3].indexOf(' ');
      if (index > 0) {
        options |= FindFociProcessor.OPTION_MINIMUM_ABOVE_SADDLE;
        if (fields[3].contains("conn")) {
          options |= FindFociProcessor.OPTION_CONTIGUOUS_ABOVE_SADDLE;
        }
        fields[3] = fields[3].substring(0, index);
      }
      final int minSize = Integer.parseInt(fields[3]);
      int searchMethod = -1;
      for (int i = 0; i < searchMethods.length; i++) {
        if (fields[4].startsWith(searchMethods[i])) {
          searchMethod = i;
          break;
        }
      }
      if (searchMethod < 0) {
        throw new IllegalArgumentException("No search method");
      }
      double searchParameter = 0;
      if (searchMethodHasParameter(searchMethod)) {
        index = fields[4].indexOf(" : ") + 3;
        searchParameter = Double.parseDouble(fields[4].substring(index));
      }
      int peakMethod = -1;
      for (int i = 0; i < peakMethods.length; i++) {
        if (fields[5].startsWith(peakMethods[i])) {
          peakMethod = i;
          break;
        }
      }
      if (peakMethod < 0) {
        throw new IllegalArgumentException("No peak method");
      }
      index = fields[5].indexOf(" : ") + 3;
      final double peakParameter = Double.parseDouble(fields[5].substring(index));
      int sortMethod = -1;
      for (int i = 0; i < sortIndexMethods.length; i++) {
        if (fields[6].startsWith(sortIndexMethods[i])) {
          sortMethod = i;
          break;
        }
      }
      if (sortMethod < 0) {
        throw new IllegalArgumentException("No sort method");
      }
      int centreMethod = -1;
      for (int i = 0; i < centreMethods.length; i++) {
        if (fields[7].startsWith(centreMethods[i])) {
          centreMethod = i;
          break;
        }
      }
      if (centreMethod < 0) {
        throw new IllegalArgumentException("No centre method");
      }
      double centreParameter = 0;
      if (centreMethodHasParameter(centreMethod)) {
        index = fields[7].indexOf(" : ") + 3;
        centreParameter = Double.parseDouble(fields[7].substring(index));
      }

      final Options ffOptions = new Options(blur, backgroundMethod, backgroundParameter,
          thresholdMethod, searchMethod, searchParameter, maxPeaks, minSize, peakMethod,
          peakParameter, sortMethod, options, centreMethod, centreParameter);

      // Debugging
      if (!ffOptions.createParameters().equals(parameters)) {
        logger.log(Level.SEVERE,
            () -> String.format("Error converting parameters to FindFoci options:%n%s%n%s",
                parameters, ffOptions.createParameters()));
        return null;
      }

      return ffOptions;
    } catch (final Exception ex) {
      logger.log(Level.SEVERE, ex,
          () -> "Error converting parameters to FindFoci options: " + parameters);
      return null;
    }
  }

  /**
   * Extract the points for the given image. If a file exists in the same directory as the image
   * with the suffix .csv, .xyz, or .txt then the program will attempt to load 3D coordinates from
   * file. Otherwise the points are taken from the the ROI.
   *
   * <p>The points are then filtered to include only those within the mask region (if the mask
   * dimensions match those of the image).
   *
   * @param roi the roi
   * @param imp the image
   * @param mask the mask
   * @return the assigned points
   */
  public static AssignedPoint[] extractRoiPoints(Roi roi, ImagePlus imp, ImagePlus mask) {
    AssignedPoint[] roiPoints = null;

    final boolean is3D = imp.getNSlices() > 1;

    roiPoints = loadPointsFromFile(imp);

    if (roiPoints == null) {
      roiPoints = AssignedPointUtils.extractRoiPoints(roi);
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

  private static AssignedPoint[] loadPointsFromFile(ImagePlus imp) {
    final FileInfo fileInfo = imp.getOriginalFileInfo();
    if (fileInfo != null && fileInfo.directory != null) {
      String title = imp.getTitle();
      final int index = title.lastIndexOf('.');
      if (index != -1) {
        title = title.substring(0, index);
      }

      for (final String suffix : new String[] {".csv", ".xyz", ".txt"}) {
        final AssignedPoint[] roiPoints = loadPointsFromFile(fileInfo.directory + title + suffix);
        if (roiPoints != null) {
          return roiPoints;
        }
      }
    }
    return null;
  }

  private static AssignedPoint[] loadPointsFromFile(String filename) {
    if (filename == null) {
      return null;
    }
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
        final String[] fields = pointsPattern.split(line);
        if (fields.length > 1) {
          try {
            final int x = (int) Double.parseDouble(fields[0]);
            final int y = (int) Double.parseDouble(fields[1]);
            int zposition = 0;
            if (fields.length > 2) {
              zposition = (int) Double.parseDouble(fields[2]);
            }
            points.add(new AssignedPoint(x, y, zposition, ++id));
          } catch (final NumberFormatException ex) {
            // Abort if too many errors
            if (++errors == MAX_ERROR) {
              return null;
            }
          }
        }
      }
      return points.toArray(new AssignedPoint[points.size()]);
    } catch (final IOException ex) {
      // ignore
    }
    return null;
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
    Options options;
    String parameters;
    int count;
    int tp;
    int fp;
    int fn;
    long time;
    double[] metrics = new double[10];

    Result(int id, Options options, int count, int tp, int fp, int fn, long time, double beta,
        double rmsd) {
      this.id = id;
      this.options = options;
      if (options != null) {
        this.parameters = options.createParameters();
      }
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
      return parameters;
    }
  }

  /**
   * Provides the ability to sort the results arrays in ascending order.
   */
  private static class ResultComparator implements Comparator<Result> {
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

        if (compare(o1.options.blur, o2.options.blur, result) != 0) {
          return result[0];
        }

        // Higher background methods are more general
        if (compare(o1.options.backgroundMethod, o2.options.backgroundMethod, result) != 0) {
          return -result[0];
        }
        if (compare(o1.options.backgroundParameter, o2.options.backgroundParameter, result) != 0) {
          return result[0];
        }

        // Smallest size is more general
        if (compare(o1.options.minSize, o2.options.minSize, result) != 0) {
          return result[0];
        }

        // Lower search methods are more general
        if (compare(o1.options.searchMethod, o2.options.searchMethod, result) != 0) {
          return result[0];
        }
        if (compare(o1.options.searchParameter, o2.options.searchParameter, result) != 0) {
          return result[0];
        }

        // Higher peak methods are more general
        if (compare(o1.options.peakMethod, o2.options.peakMethod, result) != 0) {
          return -result[0];
        }
        if (compare(o1.options.peakParameter, o2.options.peakParameter, result) != 0) {
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
      result[0] = value1 - value2;
      return result[0];
    }
  }

  private static class Options {
    private String parameters;
    public double blur;
    public int backgroundMethod;
    public double backgroundParameter;
    public String autoThresholdMethod;
    public int searchMethod;
    public double searchParameter;
    public int maxPeaks;
    public int minSize;
    public int peakMethod;
    public double peakParameter;
    public int sortIndex;
    public int options;
    public int centreMethod;
    public double centreParameter;

    Options(double blur, int backgroundMethod, double backgroundParameter,
        String autoThresholdMethod, int searchMethod, double searchParameter, int maxPeaks,
        int minSize, int peakMethod, double peakParameter, int sortIndex, int options,
        int centreMethod, double centreParameter) {
      this.blur = blur;
      this.backgroundMethod = backgroundMethod;
      this.backgroundParameter = backgroundParameter;
      this.autoThresholdMethod = autoThresholdMethod;
      this.searchMethod = searchMethod;
      this.searchParameter = searchParameter;
      this.maxPeaks = maxPeaks;
      this.minSize = minSize;
      this.peakMethod = peakMethod;
      this.peakParameter = peakParameter;
      this.sortIndex = sortIndex;
      this.options = options;
      this.centreMethod = centreMethod;
      this.centreParameter = centreParameter;
    }

    /**
     * Creates the string representation of the parameters (the value is computed once and cached).
     *
     * @return the string representation of the parameters.
     */
    String createParameters() {
      if (parameters == null) {
        parameters =
            FindFociOptimiser_PlugIn.createParameters(blur, backgroundMethod, autoThresholdMethod,
                backgroundParameter, maxPeaks, minSize, searchMethod, searchParameter, peakMethod,
                peakParameter, sortIndex, options, centreMethod, centreParameter);
      }
      return parameters;
    }
  }

  /**
   * The Class OptimisationWorker.
   */
  class OptimisationWorker implements Runnable {

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
    public OptimisationWorker(String image, Ticker ticker) {
      this.image = image;
      this.ticker = ticker;
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
      if (IJ.escapePressed()) {
        return;
      }

      final ImagePlus imp = FindFoci_PlugIn.openImage(inputDirectory, image);
      // The input filename may not be an image
      if (imp == null) {
        IJ.log("Skipping file (it may not be an image): " + inputDirectory + image);
        // We can skip forward in the progress
        ticker.tick(combinations);
        return;
      }
      final String[] maskPath = FindFoci_PlugIn.getMaskImage(inputDirectory, maskDirectory, image);
      final ImagePlus mask = FindFoci_PlugIn.openImage(maskPath[0], maskPath[1]);
      final String resultFile = outputDirectory + imp.getShortTitle();
      final String fullResultFile = resultFile + RESULTS_SUFFIX;
      boolean newResults = false;
      if (reuseResults && new File(fullResultFile).exists()) {
        final ArrayList<Result> results = loadResults(fullResultFile);
        if (results != null && results.size() == combinations) {
          IJ.log("Re-using results: " + fullResultFile);
          // We can skip forward in the progress
          ticker.tick(combinations);
          result = new OptimiserResult(results, 0);
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
  }

  /**
   * Load the results from the specified file. We assign an arbitrary ID to each result using the
   * unique combination of parameters.
   *
   * @param filename the filename
   * @return The results
   */
  private ArrayList<Result> loadResults(String filename) {

    if (countLines(filename) != combinations) {
      return null;
    }

    try (BufferedReader input = Files.newBufferedReader(Paths.get(filename))) {
      final ArrayList<Result> results = new ArrayList<>();
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
        // # Rank Blur Background method Max Min Search method Peak method Sort method Centre method
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

        final Result r = new Result(id, null, n, tp, fp, fn, time, myBeta, rmsd);
        // Do not count on the Options being parsed from the parameters.
        r.parameters = parameters;
        r.options = optionsMap.get(id);
        results.add(r);
      }

      // If the results were loaded then we must sort them to get a rank
      sortResults(results, myResultsSortMethod);
      return results;
    } catch (final ArrayIndexOutOfBoundsException | IOException | NumberFormatException ex) {
      return null;
    }
  }

  /**
   * Count the number of valid lines in the file.
   *
   * @param filename the filename
   * @return The number of lines
   */
  private static int countLines(String filename) {
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
  private static int getIndex(String line, int nth) {
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
      id = createId(parameters);
    }
    return id;
  }

  /**
   * Create a unique ID for the parameters string.
   *
   * @param parameters the parameters
   * @return the ID
   */
  private synchronized Integer createId(String parameters) {
    // Check again in case another thread just created it
    int id = idMap.get(parameters);
    if (id == 0) {
      id = idMap.size() + 1;
      // Ensure we have options for every ID
      optionsMap.put(id, createOptions(parameters));
      idMap.put(parameters, id);
    }
    return id;
  }

  private static void showOptimiserWindow() {
    if (instance != null) {
      showInstance();
      return;
    }

    IJ.showStatus("Initialising FindFoci Optimiser ...");

    String errorMessage = null;
    Throwable exception = null;

    try {
      Class.forName("org.jdesktop.beansbinding.Property", false,
          FindFociOptimiser_PlugIn.class.getClassLoader());

      // it exists on the classpath
      instance = new OptimiserView();
      instance.addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent event) {
          WindowManager.removeWindow(instance);
        }
      });
      instance.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

      IJ.register(OptimiserView.class);

      showInstance();
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

  private static void showInstance() {
    WindowManager.addWindow(instance);
    instance.setVisible(true);
    instance.toFront();
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

  private String[] settingsNames;
  private ArrayList<DialogSettings> settings;

  private long lastTime;
  private boolean custom = true;

  // Store the preset values for the Text fields, Choices, Numeric field.
  // Preceed with a '-' character if the field is for single mode only.
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
  private static final int[][] optionPreset = new int[][] { { FLAG_FALSE, // Background_SD_above_mean
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

  private void createSettings() {
    settings = new ArrayList<>();

    settings.add(new DialogSettings("Custom"));
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
      settings.add(s);
    }

    settingsNames = new String[settings.size()];
    for (int i = 0; i < settings.size(); i++) {
      settingsNames[i] = settings.get(i).name;
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
        applySettings(gd, settings.get(index));
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
    final DialogSettings s = settings.get(0);
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
