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

import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Color;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import javax.swing.WindowConstants;

import gnu.trove.set.hash.TDoubleHashSet;
import gnu.trove.set.hash.TIntHashSet;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.ExtendedGenericDialog;
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
import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.ij.Utils;
import uk.ac.sussex.gdsc.core.match.Coordinate;
import uk.ac.sussex.gdsc.core.match.MatchCalculator;
import uk.ac.sussex.gdsc.core.match.MatchResult;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold;
import uk.ac.sussex.gdsc.core.utils.StoredData;
import uk.ac.sussex.gdsc.core.utils.TextUtils;
import uk.ac.sussex.gdsc.core.utils.UnicodeReader;
import uk.ac.sussex.gdsc.foci.gui.OptimiserView;

/**
 * Runs the FindFoci plugin with various settings and compares the results to the 
 * reference image point ROI.
 */
public class FindFociOptimiser
		implements PlugIn, MouseListener, WindowListener, DialogListener, TextListener, ItemListener
{
	private static OptimiserView instance;

	private static String TITLE = "FindFoci Optimiser";
	private static TextWindow resultsWindow = null;
	private static int STEP_LIMIT = 10000;
	private static final int RESULT_PRECISION = 4;

	private static String myMaskImage = "";
	private static boolean myBackgroundStdDevAboveMean = true;
	private static boolean myBackgroundAuto = true;
	private static boolean myBackgroundAbsolute = false;
	private static String myBackgroundParameter = "2.5, 3.5, 0.5";
	private static String myThresholdMethod = AutoThreshold.Method.OTSU.name;
	private static String myStatisticsMode = "Both";
	private static boolean mySearchAboveBackground = true;
	private static boolean mySearchFractionOfPeak = true;
	private static String mySearchParameter = "0, 0.6, 0.2";
	private static String myMinSizeParameter = "1, 9, 2";
	private static String[] saddleOptions = { "Yes", "Yes - Connected", "No", "All" };
	private static int myMinimumAboveSaddle = 0;
	private static int myPeakMethod = FindFociProcessor.PEAK_RELATIVE_ABOVE_BACKGROUND;
	private static String myPeakParameter = "0, 0.6, 0.2";
	private static String mySortMethod = "" + FindFociProcessor.SORT_INTENSITY;
	private static int myMaxPeaks = 500;
	private static String myGaussianBlur = "0, 0.5, 1";
	private static String myCentreMethod = "" + FindFoci.CENTRE_MAX_VALUE_SEARCH;
	private static String myCentreParameter = "2";

	/** The list of recognised methods for sorting the results. */
	public final static String[] sortMethods = { "None", "Precision", "Recall", "F0.5", "F1", "F2", "F-beta", "Jaccard",
			"RMSD" };

	/** Do not sort the results. */
	public final static int SORT_NONE = 0;

	/** Sort the results using the Precision. */
	public final static int SORT_PRECISION = 1;

	/** Sort the results using the Recall. */
	public final static int SORT_RECALL = 2;
	/**
	 * Sort the results using the F0.5 score (weights precision over recall)
	 */
	public final static int SORT_F05 = 3;

	/** Sort the results using the F1 score (precision and recall equally weighted). */
	public final static int SORT_F1 = 4;

	/** Sort the results using the F2 score (weights recall over precision). */
	public final static int SORT_F2 = 5;

	/** Sort the results using the F-beta score. */
	public final static int SORT_FBETA = 6;

	/** Sort the results using the Jaccard. */
	public final static int SORT_JACCARD = 7;
	/**
	 * Sort the results using the RMSD. Note that the RMSD is only computed using the TP so is therefore only of use
	 * when the TP values are the same (i.e. for a tie breaker)
	 */
	public final static int SORT_RMSD = 8;

	/** The search methods used for matching. */
	public final static String[] matchSearchMethods = { "Relative", "Absolute" };
	private static int myMatchSearchMethod = 0;
	private static double myMatchSearchDistance = 0.05;
	private static int myResultsSortMethod = SORT_JACCARD;
	private static double myBeta = 4.0;
	private static int myMaxResults = 100;
	private static boolean myShowScoreImages = false;
	private static String myResultFile = "";

	private int[] optionsArray = {};
	private String[] thresholdMethods = null;
	private String[] statisticsModes = null;
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
	private boolean multiMode = false;
	private static String INPUT_DIRECTORY = "findfoci.optimiser.inputDirectory";
	private static String MASK_DIRECTORY = "findfoci.optimiser.maskDirectory";
	private static String OUTPUT_DIRECTORY = "findfoci.optimiser.outputDirectory";
	private static String SCORING_MODE = "findfoci.optimiser.scoringMode";
	private static String REUSE_RESULTS = "findfoci.optimiser.reuseResults";
	private static String inputDirectory = Prefs.get(INPUT_DIRECTORY, "");
	private static String maskDirectory = Prefs.get(MASK_DIRECTORY, "");
	private static String outputDirectory = Prefs.get(OUTPUT_DIRECTORY, "");
	private static String[] SCORING_MODES = new String[] { "Raw score metric", "Relative (% drop from top)", "Z-score",
			"Rank" };
	private static final int SCORE_RAW = 0;
	private static final int SCORE_RELATIVE = 1;
	private static final int SCORE_Z = 2;
	private static final int SCORE_RANK = 3;
	private static int scoringMode = Prefs.getInt(SCORING_MODE, SCORE_RAW);
	private static boolean reuseResults = Prefs.getBoolean(REUSE_RESULTS, true);

	@SuppressWarnings("rawtypes")
	private Vector checkbox, choice;
	private GenericDialog listenerGd;

	// Stored to allow the display of any of the latest results from the result table
	private static ImagePlus lastImp, lastMask;
	private static ArrayList<Result> lastResults;
	private static String optimiserCommandOptions;

	// The number of combinations
	private int combinations;

	/*
	 * (non-Javadoc)
	 *
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	@Override
	public void run(String arg)
	{
		UsageTracker.recordPlugin(this.getClass(), arg);

		try
		{
			if (arg.equals("frame"))
				showOptimiserWindow();
			else
			{
				final ImagePlus imp = (arg.equals("multi")) ? null : WindowManager.getCurrentImage();
				run(imp);
			}
		}
		catch (final NoClassDefFoundError e)
		{
			// Because we have no underscore '_' in the class name ImageJ will not print
			// the error so handle it here
			IJ.handleException(e);
		}
	}

	/**
	 * Run the optimiser on the given image. If the image is null then process in multi-image mode.
	 *
	 * @param imp
	 *            the image
	 */
	public void run(ImagePlus imp)
	{
		if (!showDialog(imp))
			return;

		IJ.log("---\n" + TITLE);
		IJ.log(combinations + " combinations");

		if (multiMode)
		{
			// Get the list of images
			final String[] imageList = FindFoci.getBatchImages(inputDirectory);
			if (imageList == null || imageList.length == 0)
			{
				IJ.error(TITLE, "No input images in folder: " + inputDirectory);
				return;
			}

			if (reuseResults && resultsExist(imageList))
				IJ.log("Output directory contains existing results that will be re-used if they have the correct number of combinations");

			IJ.showStatus("Running optimiser ...");

			// For each image start an optimiser run:
			// - Run the optimiser
			// - save the results to the output directory
			int size = imageList.length;
			final ExecutorService threadPool = Executors.newFixedThreadPool(Prefs.getThreads());
			final List<Future<?>> futures = new ArrayList<>(size);
			final ArrayList<OptimisationWorker> workers = new ArrayList<>(size);

			// Allow progress to be tracked across all threads
			final Counter counter = new SynchronizedCounter(combinations * size);
			for (final String image : imageList)
			{
				final OptimisationWorker w = new OptimisationWorker(image, counter);
				workers.add(w);
				futures.add(threadPool.submit(w));
			}

			// Collect all the results
			Utils.waitForCompletion(futures);
			threadPool.shutdown();
			IJ.showProgress(1);
			IJ.showStatus("");

			if (Utils.isInterrupted())
				return;

			// Check all results are the same size
			final ArrayList<ArrayList<Result>> allResults = new ArrayList<>(size);
			for (final OptimisationWorker w : workers)
			{
				if (w.result == null)
					continue;
				final ArrayList<Result> results = w.result.results;
				if (!allResults.isEmpty() && results.size() != allResults.get(0).size())
				{
					IJ.error(TITLE, "Some optimisation runs produced a different number of results");
					return;
				}
				allResults.add(results);
			}
			if (allResults.isEmpty())
			{
				IJ.error(TITLE, "No optimisation runs produced results");
				return;
			}

			IJ.showStatus("Calculating scores ...");

			// Combine using the chosen ranking score.
			// Use the first set of results to accumulate scores.
			final ArrayList<Result> results = allResults.get(0);
			getScore(results);
			size = allResults.size();
			for (int i = 1; i < size; i++)
			{
				final ArrayList<Result> results2 = allResults.get(i);
				getScore(results2);
				for (int j = 0; j < results.size(); j++)
				{
					// Combine all the metrics
					final Result r1 = results.get(j);
					final Result r2 = results2.get(j);
					r1.add(r2);
				}
			}
			// Average the scores
			final double factor = 1.0 / size;
			for (int j = 0; j < results.size(); j++)
			{
				final double[] metrics = results.get(j).metrics;
				// Do not average the RMSD
				for (int i = 0; i < metrics.length - 1; i++)
					metrics[i] *= factor;
				// We must reset the score with the original RMSD
				if (myResultsSortMethod == SORT_RMSD)
					metrics[Result.SCORE] = metrics[Result.RMSD];
			}

			// Now sort the results using the combined scores. Check is the scored metric is lowest first
			final boolean lowestFirst = myResultsSortMethod == SORT_RMSD;
			sortResultsByScore(results, lowestFirst);

			// Output the combined results
			saveResults(null, null, results, null, outputDirectory + "all");

			// Show in a table
			showResults(null, null, results);

			IJ.showStatus("");
		}
		else
		{
			final ImagePlus mask = WindowManager.getImage(myMaskImage);

			final OptimiserResult result = runOptimiser(imp, mask, new StandardCounter(combinations));
			IJ.showProgress(1);

			if (Utils.isInterrupted())
				return;

			if (result == null)
			{
				IJ.error(TITLE, "No ROI points fall inside the mask image");
				return;
			}

			// For a single image we use the raw score (since no results are combined)
			final ArrayList<Result> results = result.results;
			getScore(results, SCORE_RAW);
			showResults(imp, mask, results);

			// Re-run Find_Peaks and output the best result
			if (!results.isEmpty())
			{
				IJ.log("Top result = " + IJ.d2s(results.get(0).metrics[getSortIndex(myResultsSortMethod)], 4));

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
	 * @param imageList
	 *            the image list
	 * @return true if results exist
	 */
	private static boolean resultsExist(String[] imageList)
	{
		// List the results in the output directory
		final String[] results = new File(outputDirectory).list(new FilenameFilter()
		{
			@Override
			public boolean accept(File dir, String name)
			{
				return name.endsWith(".results.xls");
			}
		});

		if (results == null || results.length == 0)
			return false;
		for (String name : imageList)
		{
			name = getShortTitle(name);
			for (final String result : results)
				if (result.startsWith(name))
					return true;
		}
		return false;
	}

	/**
	 * Copied from ImagePlus.getShortTitle()
	 *
	 * @param title
	 *            the title
	 * @return the title with no spaces or extension
	 */
	private static String getShortTitle(String title)
	{
		int index = title.indexOf(' ');
		if (index > -1)
			title = title.substring(0, index);
		index = title.lastIndexOf('.');
		if (index > 0)
			title = title.substring(0, index);
		return title;
	}

	/**
	 * Check if the optimal results was obtained at the edge of the optimisation search space.
	 *
	 * @param result
	 *            the result
	 * @param imp
	 *            the imp
	 */
	private void checkOptimisationSpace(OptimiserResult result, ImagePlus imp)
	{
		final Options bestOptions = result.results.get(0).options;
		if (bestOptions == null)
			return;

		if (result.time != 0)
		{
			final double seconds = result.time / 1e9;
			IJ.log(String.format("%s Optimisation time = %.3f sec (%.3f ms / combination). Speed up = %.3fx",
					imp.getTitle(), seconds, result.time / 1e6 / combinations, result.total / (double) result.time));
		}

		// Check if a sub-optimal best result was obtained at the limit of the optimisation range
		if (result.results.get(0).metrics[Result.F1] < 1.0)
		{
			final StringBuilder sb = new StringBuilder();
			if (backgroundMethodHasParameter(bestOptions.backgroundMethod))
				if (bestOptions.backgroundParameter == myBackgroundParameterMin)
					append(sb, "- Background parameter @ lower limit (%g)\n", bestOptions.backgroundParameter);
				else if (bestOptions.backgroundParameter + myBackgroundParameterInterval > myBackgroundParameterMax)
					append(sb, "- Background parameter @ upper limit (%g)\n", bestOptions.backgroundParameter);
			if (searchMethodHasParameter(bestOptions.searchMethod))
				if (bestOptions.searchParameter == mySearchParameterMin && mySearchParameterMin > 0)
					append(sb, "- Search parameter @ lower limit (%g)\n", bestOptions.searchParameter);
				else if (bestOptions.searchParameter + mySearchParameterInterval > mySearchParameterMax &&
						mySearchParameterMax < 1)
					append(sb, "- Search parameter @ upper limit (%g)\n", bestOptions.searchParameter);
			if (bestOptions.minSize == myMinSizeMin && myMinSizeMin > 1)
				append(sb, "- Min Size @ lower limit (%d)\n", bestOptions.minSize);
			else if (bestOptions.minSize + myMinSizeInterval > myMinSizeMax)
				append(sb, "- Min Size @ upper limit (%d)\n", bestOptions.minSize);

			if (bestOptions.peakParameter == myPeakParameterMin && myPeakParameterMin > 0)
				append(sb, "- Peak parameter @ lower limit (%g)\n", bestOptions.peakParameter);
			else if (bestOptions.peakParameter + myPeakParameterInterval > myPeakParameterMax && myPeakParameterMax < 1)
				append(sb, "- Peak parameter @ upper limit (%g)\n", bestOptions.peakParameter);

			if (bestOptions.blur == blurArray[0] && blurArray[0] > 0)
				append(sb, "- Gaussian blur @ lower limit (%g)\n", bestOptions.blur);
			else if (bestOptions.blur == blurArray[blurArray.length - 1])
				append(sb, "- Gaussian blur @ upper limit (%g)\n", bestOptions.blur);

			if (bestOptions.maxPeaks == result.results.get(0).n)
				append(sb, "- Total peaks == Maximum Peaks (%d)\n", bestOptions.maxPeaks);

			if (sb.length() > 0)
			{
				sb.insert(0,
						"Optimal result (" +
								IJ.d2s(result.results.get(0).metrics[getSortIndex(myResultsSortMethod)], 4) + ") for " +
								imp.getShortTitle() + " obtained at the following limits:\n");
				sb.append("You may want to increase the optimisation space.");

				showIncreaseSpaceMessage(sb);
			}
		}
	}

	private synchronized void showIncreaseSpaceMessage(StringBuilder sb)
	{
		IJ.log("---");
		IJ.log(sb.toString());

		// Do not show messages when running in batch
		if (!(java.awt.GraphicsEnvironment.isHeadless() || multiMode))
			IJ.showMessage(sb.toString());
	}

	/**
	 * Gets the score.
	 *
	 * @param results
	 *            the results
	 */
	private static void getScore(ArrayList<Result> results)
	{
		getScore(results, scoringMode);
	}

	/**
	 * Gets the score for each item in the results set and sets it to the score field. The score is determined using the
	 * configured resultsSortMethod and scoringMode. It is assumed that all the scoring metrics start at zero and higher
	 * is better.
	 *
	 * @param results
	 *            the results
	 * @param scoringMode
	 *            the scoring mode
	 */
	private static void getScore(ArrayList<Result> results, int scoringMode)
	{
		// Extract the score from the results
		final int scoreIndex;
		switch (scoringMode)
		{
			case SCORE_RAW:
			case SCORE_Z:
			case SCORE_RELATIVE:
				scoreIndex = getSortIndex(myResultsSortMethod);
				break;

			// If scoring using the rank then note that the rank was assigned
			// using the chosen metric in myResultsSortMethod within sortResults(...)
			case SCORE_RANK:
			default:
				scoreIndex = Result.RANK;
		}

		// Only Raw/Rank are valid for RMSD
		if (scoreIndex == Result.RMSD && (scoringMode != SCORE_RAW || scoringMode != SCORE_RANK))
			scoringMode = SCORE_RAW;

		double[] score = new double[results.size()];
		for (int i = 0; i < score.length; i++)
			score[i] = results.get(i).metrics[scoreIndex];

		// Perform additional score adjustment
		if (scoringMode == SCORE_Z)
		{
			// Use the z-score
			final double[] stats = getStatistics(score);
			final double av = stats[0];
			final double sd = stats[1];
			if (sd > 0)
			{
				final double factor = 1.0 / sd;
				for (int i = 0; i < score.length; i++)
					score[i] = (score[i] - av) * factor;
			}
			else
				score = new double[score.length]; // all have z=0
		}
		else if (scoringMode == SCORE_RELATIVE)
		{
			// Use the relative (%) from the top score. Assumes the bottom score is zero.
			final double top = getTop(score);
			final double factor = 100 / top;
			for (int i = 0; i < score.length; i++)
				score[i] = factor * (score[i] - top);
		}

		// Set the score into the results
		for (int i = 0; i < score.length; i++)
			results.get(i).metrics[Result.SCORE] = score[i];
	}

	/**
	 * Get the statistics.
	 *
	 * @param score
	 *            the score
	 * @return The average and standard deviation
	 */
	private static double[] getStatistics(double[] score)
	{
		// Get the average
		double sum = 0.0;
		double sum2 = 0.0;
		final int n = score.length;
		for (final double value : score)
		{
			sum += value;
			sum2 += (value * value);
		}
		final double av = sum / n;

		// Get the Std.Dev
		double stdDev;
		if (n > 0)
		{
			final double d = n;
			stdDev = (d * sum2 - sum * sum) / d;
			if (stdDev > 0.0)
				stdDev = Math.sqrt(stdDev / (d - 1.0));
			else
				stdDev = 0.0;
		}
		else
			stdDev = 0.0;

		return new double[] { av, stdDev };
	}

	/**
	 * Get the highest score. Assumes the lowest is zero
	 *
	 * @param score
	 *            the score
	 * @return The top score
	 */
	private static double getTop(double[] score)
	{
		double top = score[0];
		for (int i = 1; i < score.length; i++)
			if (top < score[i])
				top = score[i];
		return top;
	}

	private class StopWatch
	{
		long base;
		long time;

		StopWatch()
		{
			this(0);
		}

		StopWatch create()
		{
			return new StopWatch(time());
		}

		private StopWatch(long base)
		{
			time = System.nanoTime();
			this.base = base;
		}

		long stop()
		{
			time = System.nanoTime() - time;
			return time();
		}

		long time()
		{
			return time + base;
		}
	}

	private class OptimiserResult
	{
		ArrayList<Result> results;
		long time;
		long total;

		OptimiserResult(ArrayList<Result> results, long time)
		{
			this.results = results;
			this.time = time;
			total = 0;
			if (results == null)
				return;
			for (int i = 0; i < results.size(); i++)
				total += results.get(i).time;
		}
	}

	/**
	 * Enumerate the parameters for FindFoci on the provided image
	 *
	 * @param imp
	 *            The image
	 * @param mask
	 *            The mask
	 * @return The results (or null if there are no ROI points inside the mask)
	 */
	private OptimiserResult runOptimiser(ImagePlus imp, ImagePlus mask, Counter counter)
	{
		if (invalidImage(imp))
			return null;
		final AssignedPoint[] roiPoints = extractRoiPoints(imp.getRoi(), imp, mask);

		if (roiPoints.length == 0)
		{
			IJ.showMessage("Error", "Image must have a point ROI or corresponding ROI file");
			return null;
		}

		final boolean is3D = is3D(roiPoints);

		final ArrayList<Result> results = new ArrayList<>(combinations);

		// Set the threshold for assigning points matches as a fraction of the image size
		final double dThreshold = getDistanceThreshold(imp);

		final StopWatch sw = new StopWatch();

		final FindFoci ff = new FindFoci();
		int id = 0;
		for (int blurCount = 0; blurCount < blurArray.length; blurCount++)
		{
			final double blur = blurArray[blurCount];
			final StopWatch sw0 = new StopWatch();
			final ImagePlus imp2 = ff.blur(imp, blur);
			sw0.stop();
			//System.out.printf("0");

			// Iterate over the options
			int thresholdMethodIndex = 0;
			for (int b = 0; b < backgroundMethodArray.length; b++)
			{
				final String thresholdMethod = (backgroundMethodArray[b] == FindFociProcessor.BACKGROUND_AUTO_THRESHOLD)
						? thresholdMethods[thresholdMethodIndex++]
						: "";

				final String[] myStatsModes = backgroundMethodHasStatisticsMode(backgroundMethodArray[b])
						? statisticsModes
						: new String[] { "Both" };
				for (final String statsMode : myStatsModes)
				{
					final int statisticsMode = convertStatisticsMode(statsMode);
					final StopWatch sw1 = sw0.create();
					final FindFociInitResults initResults = ff.findMaximaInit(imp, imp2, mask, backgroundMethodArray[b],
							thresholdMethod, statisticsMode);
					sw1.stop();
					//System.out.printf("1");
					if (initResults == null)
						return null;
					//Object[] clonedInitArray = null;
					FindFociInitResults searchInitArray = null;

					for (double backgroundParameter = myBackgroundParameterMinArray[b]; backgroundParameter <= myBackgroundParameterMax; backgroundParameter += myBackgroundParameterInterval)
					{
						boolean logBackground = (blurCount == 0) && !multiMode; // Log on first blur iteration
						// Use zero when there is no parameter
						final double thisBackgroundParameter = (backgroundMethodHasParameter(backgroundMethodArray[b]))
								? backgroundParameter
								: 0;

						for (int s = 0; s < searchMethodArray.length; s++)
							for (double searchParameter = mySearchParameterMinArray[s]; searchParameter <= mySearchParameterMax; searchParameter += mySearchParameterInterval)
							{
								// Use zero when there is no parameter
								final double thisSearchParameter = (searchMethodHasParameter(searchMethodArray[s]))
										? searchParameter
										: 0;

								searchInitArray = ff.clone(initResults, searchInitArray);
								final StopWatch sw2 = sw1.create();
								final FindFociSearchResults searchArray = ff.findMaximaSearch(searchInitArray,
										backgroundMethodArray[b], thisBackgroundParameter, searchMethodArray[s],
										thisSearchParameter);
								sw2.stop();
								//System.out.printf("2");
								if (searchArray == null)
									return null;
								FindFociInitResults mergeInitArray = null;

								if (logBackground)
								{
									final float backgroundLevel = searchInitArray.stats.background;
									logBackground = false;
									IJ.log(String.format("Background level - %s %s: %s = %g",
											FindFoci.backgroundMethods[backgroundMethodArray[b]],
											backgroundMethodHasStatisticsMode(backgroundMethodArray[b])
													? "(" + statsMode + ") "
													: "",
											((backgroundMethodHasParameter(backgroundMethodArray[b]))
													? IJ.d2s(backgroundParameter, 2)
													: thresholdMethod),
											backgroundLevel));
								}

								for (double peakParameter = myPeakParameterMin; peakParameter <= myPeakParameterMax; peakParameter += myPeakParameterInterval)
								{
									final StopWatch sw3 = sw2.create();
									final FindFociMergeTempResults mergePeakResults = ff.findMaximaMergePeak(
											searchInitArray, searchArray, myPeakMethod, peakParameter);
									sw3.stop();
									//System.out.printf("3");

									for (int minSize = myMinSizeMin; minSize <= myMinSizeMax; minSize += myMinSizeInterval)
									{
										final StopWatch sw4 = sw3.create();
										//System.out.printf(".");
										final FindFociMergeTempResults mergeSizeResults = ff
												.findMaximaMergeSize(searchInitArray, mergePeakResults, minSize);
										sw4.stop();
										//System.out.printf("4");

										for (int options : optionsArray)
										{
											mergeInitArray = ff.clone(searchInitArray, mergeInitArray);
											final StopWatch sw5 = sw4.create();
											final FindFociMergeResults mergeArray = ff.findMaximaMergeFinal(
													mergeInitArray, mergeSizeResults, minSize, options, blur);
											sw5.stop();
											//System.out.printf("5");
											if (mergeArray == null)
												return null;

											options += statisticsMode;
											for (final int sortMethod : sortMethodArray)
												for (int c = 0; c < centreMethodArray.length; c++)
													for (double centreParameter = myCentreParameterMinArray[c]; centreParameter <= myCentreParameterMaxArray[c]; centreParameter += myCentreParameterIntervalArray[c])
													{
														final StopWatch sw6 = sw5.create();
														final FindFociResults peakResults = ff.findMaximaResults(
																mergeInitArray, mergeArray, myMaxPeaks, sortMethod,
																centreMethodArray[c], centreParameter);
														final long time = sw6.stop();
														//System.out.printf("6");

														counter.increment();

														if (peakResults != null)
														{
															// Get the results
															final Options runOptions = new Options(blur,
																	backgroundMethodArray[b], thisBackgroundParameter,
																	thresholdMethod, searchMethodArray[s],
																	thisSearchParameter, myMaxPeaks, minSize,
																	myPeakMethod, peakParameter, sortMethod, options,
																	centreMethodArray[c], centreParameter);
															final Result result = analyseResults(id, roiPoints,
																	peakResults.results, dThreshold, runOptions, time,
																	myBeta, is3D);
															results.add(result);
															//System.out.printf("%s %d\n", result.getParameters(), time);
														}
														else
														{
															//System.out.printf("\n");
														}

														id++;
														if (IJ.escapePressed())
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

		sw.stop();

		// All possible results sort methods are highest first
		sortResults(results, myResultsSortMethod);

		return new OptimiserResult(results, sw.time());
	}

	private void showResults(ImagePlus imp, ImagePlus mask, ArrayList<Result> results)
	{
		createResultsWindow(imp, mask, results);

		// Limit the number of results
		int noOfResults = results.size();
		if (myMaxResults > 0 && noOfResults > myMaxResults)
			noOfResults = myMaxResults;

		for (int i = noOfResults; i-- > 0;)
		{
			final Result result = results.get(i);
			final StringBuilder sb = new StringBuilder();
			sb.append(IJ.d2s(result.metrics[Result.RANK], 0)).append('\t');
			sb.append(result.getParameters());
			sb.append(result.n).append('\t');
			sb.append(result.tp).append('\t');
			sb.append(result.fp).append('\t');
			sb.append(result.fn).append('\t');
			sb.append(IJ.d2s(result.metrics[Result.JACCARD], RESULT_PRECISION)).append('\t');
			sb.append(IJ.d2s(result.metrics[Result.PRECISION], RESULT_PRECISION)).append('\t');
			sb.append(IJ.d2s(result.metrics[Result.RECALL], RESULT_PRECISION)).append('\t');
			sb.append(IJ.d2s(result.metrics[Result.F05], RESULT_PRECISION)).append('\t');
			sb.append(IJ.d2s(result.metrics[Result.F1], RESULT_PRECISION)).append('\t');
			sb.append(IJ.d2s(result.metrics[Result.F2], RESULT_PRECISION)).append('\t');
			sb.append(IJ.d2s(result.metrics[Result.Fb], RESULT_PRECISION)).append('\t');
			sb.append(IJ.d2s(result.metrics[Result.SCORE], RESULT_PRECISION)).append('\t');
			sb.append(IJ.d2s(result.metrics[Result.RMSD], RESULT_PRECISION)).append('\t');
			sb.append(IJ.d2s(result.time / 1000000.0, RESULT_PRECISION)).append("\n");
			resultsWindow.append(sb.toString());
		}
		resultsWindow.append("\n");
	}

	/**
	 * Saves the optimiser results to the given file. Also saves the predicted points (from the best scoring options) if
	 * provided.
	 *
	 * @param imp
	 *            the image
	 * @param mask
	 *            the mask
	 * @param results
	 *            the results
	 * @param predictedPoints
	 *            can be null
	 * @param resultFile
	 *            the result file
	 */
	private static void saveResults(ImagePlus imp, ImagePlus mask, ArrayList<Result> results,
			AssignedPoint[] predictedPoints, String resultFile)
	{
		if (TextUtils.isNullOrEmpty(resultFile))
			return;

		final Options bestOptions = results.get(0).options;

		final double fractionParameter = 0;
		final int outputType = FindFociProcessor.OUTPUT_RESULTS_TABLE | FindFociProcessor.OUTPUT_ROI_SELECTION |
				FindFociProcessor.OUTPUT_MASK_ROI_SELECTION | FindFociProcessor.OUTPUT_LOG_MESSAGES;

		// TODO - Add support for saving the channel, slice & frame
		FindFoci.saveParameters(resultFile + ".params", null, null, null, null, bestOptions.backgroundMethod,
				bestOptions.backgroundParameter, bestOptions.autoThresholdMethod, bestOptions.searchMethod,
				bestOptions.searchParameter, bestOptions.maxPeaks, bestOptions.minSize, bestOptions.peakMethod,
				bestOptions.peakParameter, outputType, bestOptions.sortIndex, bestOptions.options, bestOptions.blur,
				bestOptions.centreMethod, bestOptions.centreParameter, fractionParameter, "");

		try (final OutputStreamWriter out = createResultsFile(bestOptions, imp, mask, resultFile))
		{
			if (out == null)
				return;

			out.write("#\n# Results\n# " + createResultsHeader(true, false));

			// Output all results in ascending rank order
			for (int i = 0; i < results.size(); i++)
			{
				final Result result = results.get(i);
				final StringBuilder sb = new StringBuilder();
				sb.append(IJ.d2s(result.metrics[Result.RANK], 0)).append('\t');
				sb.append(result.getParameters());
				sb.append(result.n).append('\t');
				sb.append(result.tp).append('\t');
				sb.append(result.fp).append('\t');
				sb.append(result.fn).append('\t');
				sb.append(IJ.d2s(result.metrics[Result.JACCARD], RESULT_PRECISION)).append('\t');
				sb.append(IJ.d2s(result.metrics[Result.PRECISION], RESULT_PRECISION)).append('\t');
				sb.append(IJ.d2s(result.metrics[Result.RECALL], RESULT_PRECISION)).append('\t');
				sb.append(IJ.d2s(result.metrics[Result.F05], RESULT_PRECISION)).append('\t');
				sb.append(IJ.d2s(result.metrics[Result.F1], RESULT_PRECISION)).append('\t');
				sb.append(IJ.d2s(result.metrics[Result.F2], RESULT_PRECISION)).append('\t');
				sb.append(IJ.d2s(result.metrics[Result.Fb], RESULT_PRECISION)).append('\t');
				sb.append(IJ.d2s(result.metrics[Result.SCORE], RESULT_PRECISION)).append('\t');
				sb.append(IJ.d2s(result.metrics[Result.RMSD], RESULT_PRECISION)).append('\t');
				sb.append(result.time).append("\n");
				out.write(sb.toString());
			}

			// Save the identified points
			if (predictedPoints != null)
				PointManager.savePoints(predictedPoints, resultFile + ".points.csv");
		}
		catch (final IOException e)
		{
			IJ.log("Failed to write to the output file '" + resultFile + ".points.csv': " + e.getMessage());
		}
	}

	private synchronized static void addFindFociCommand(OutputStreamWriter out, Options bestOptions, String maskTitle)
			throws IOException
	{
		if (bestOptions == null)
			return;

		// This is the only way to clear the recorder.
		// It will save the current optimiser command to the recorder and then clear it.
		Recorder.saveCommand();

		// Use the recorder to build the options for the FindFoci plugin
		Recorder.setCommand("FindFoci");
		Recorder.recordOption("mask", maskTitle);
		Recorder.recordOption("background_method", FindFoci.backgroundMethods[bestOptions.backgroundMethod]);
		Recorder.recordOption("Background_parameter", "" + bestOptions.backgroundParameter);
		Recorder.recordOption("Auto_threshold", bestOptions.autoThresholdMethod);
		if (backgroundMethodHasStatisticsMode(bestOptions.backgroundMethod))
			Recorder.recordOption("Statistics_mode", FindFoci.getStatisticsMode(bestOptions.options));
		Recorder.recordOption("Search_method", FindFoci.searchMethods[bestOptions.searchMethod]);
		Recorder.recordOption("Search_parameter", "" + bestOptions.searchParameter);
		Recorder.recordOption("Minimum_size", "" + bestOptions.minSize);
		if ((bestOptions.options & FindFociProcessor.OPTION_MINIMUM_ABOVE_SADDLE) != 0)
			Recorder.recordOption("Minimum_above_saddle");
		if ((bestOptions.options & FindFociProcessor.OPTION_CONTIGUOUS_ABOVE_SADDLE) != 0)
			Recorder.recordOption("Connected_above_saddle");
		Recorder.recordOption("Minimum_peak_height", FindFoci.peakMethods[bestOptions.peakMethod]);
		Recorder.recordOption("Peak_parameter", "" + bestOptions.peakParameter);
		Recorder.recordOption("Sort_method", FindFoci.sortIndexMethods[bestOptions.sortIndex]);
		Recorder.recordOption("Maximum_peaks", "" + bestOptions.maxPeaks);
		Recorder.recordOption("Show_mask", FindFoci.maskOptions[3]);
		Recorder.recordOption("Show_table");
		Recorder.recordOption("Mark_maxima");
		Recorder.recordOption("Mark_peak_maxima");
		Recorder.recordOption("Show_log_messages");
		if (bestOptions.blur > 0)
			Recorder.recordOption("Gaussian_blur", "" + bestOptions.blur);
		Recorder.recordOption("Centre_method", FindFoci.getCentreMethods()[bestOptions.centreMethod]);
		if (bestOptions.centreMethod == FindFoci.CENTRE_OF_MASS_SEARCH)
			Recorder.recordOption("Centre_parameter", "" + bestOptions.centreParameter);

		out.write(String.format("# run(\"FindFoci\", \"%s\")\n", Recorder.getCommandOptions()));

		// Ensure the new command we have just added does not get recorded
		Recorder.setCommand(null);
	}

	private static double getDistanceThreshold(ImagePlus imp)
	{
		if (myMatchSearchMethod == 1)
			return myMatchSearchDistance;
		final int length = (imp.getWidth() < imp.getHeight()) ? imp.getWidth() : imp.getHeight();
		return Math.ceil(myMatchSearchDistance * length);
	}

	private static void append(StringBuilder sb, String format, Object... args)
	{
		sb.append(String.format(format, args));
	}

	private static AssignedPoint[] showResult(ImagePlus imp, ImagePlus mask, Options options)
	{
		if (imp == null)
			return null;

		final int outputType = FindFociProcessor.OUTPUT_MASK_PEAKS | FindFociProcessor.OUTPUT_MASK_ABOVE_SADDLE |
				FindFociProcessor.OUTPUT_MASK_ROI_SELECTION | FindFociProcessor.OUTPUT_ROI_SELECTION |
				FindFociProcessor.OUTPUT_LOG_MESSAGES;
		// Clone the input image to allow display of the peaks on the original
		final ImagePlus clone = cloneImage(imp, imp.getTitle() + " clone");
		clone.show();

		final FindFoci ff = new FindFoci();
		ff.exec(clone, mask, options.backgroundMethod, options.backgroundParameter, options.autoThresholdMethod,
				options.searchMethod, options.searchParameter, options.maxPeaks, options.minSize, options.peakMethod,
				options.peakParameter, outputType, options.sortIndex, options.options, options.blur,
				options.centreMethod, options.centreParameter, 1);

		// Add 3D support here by getting the results from the results table not the clone image which only supports 2D
		final ArrayList<FindFociResult> results = FindFoci.getResults();
		//AssignedPoint[] predictedPoints = PointManager.extractRoiPoints(clone.getRoi());
		final AssignedPoint[] predictedPoints = new AssignedPoint[results.size()];
		for (int i = 0; i < predictedPoints.length; i++)
		{
			final FindFociResult result = results.get(i);
			predictedPoints[i] = new AssignedPoint(result.x, result.y, result.z + 1, i);
		}
		maskImage(clone, mask);

		if (myShowScoreImages)
		{
			final AssignedPoint[] actualPoints = extractRoiPoints(imp.getRoi(), imp, mask);

			final List<Coordinate> TP = new LinkedList<>();
			final List<Coordinate> FP = new LinkedList<>();
			final List<Coordinate> FN = new LinkedList<>();
			final boolean is3D = is3D(actualPoints);
			if (is3D)
				MatchCalculator.analyseResults3D(actualPoints, predictedPoints, getDistanceThreshold(imp), TP, FP, FN);
			else
				MatchCalculator.analyseResults2D(actualPoints, predictedPoints, getDistanceThreshold(imp), TP, FP, FN);

			// Show image with TP, FP and FN. Use an overlay to support 3D images
			final ImagePlus tpImp = cloneImage(imp, mask, imp.getTitle() + " TP");
			//tpImp.setRoi(createRoi(TP));
			tpImp.setOverlay(createOverlay(TP, imp));
			tpImp.show();

			final ImagePlus fpImp = cloneImage(imp, mask, imp.getTitle() + " FP");
			//fpImp.setRoi(createRoi(FP));
			fpImp.setOverlay(createOverlay(FP, imp));
			fpImp.show();

			final ImagePlus fnImp = cloneImage(imp, mask, imp.getTitle() + " FN");
			//fnImp.setRoi(createRoi(FN));
			fnImp.setOverlay(createOverlay(FN, imp));
			fnImp.show();
		}
		else
		{
			// Leaving old results would be confusing so close them
			closeImage(imp.getTitle() + " TP");
			closeImage(imp.getTitle() + " FP");
			closeImage(imp.getTitle() + " FN");
		}

		return predictedPoints;
	}

	private static void closeImage(String title)
	{
		final ImagePlus imp = WindowManager.getImage(title);
		if (imp != null)
			imp.close();
	}

	private static ImagePlus cloneImage(ImagePlus imp, String cloneTitle)
	{
		ImagePlus clone = WindowManager.getImage(cloneTitle);
		if (clone == null)
		{
			clone = imp.duplicate();
			clone.setTitle(cloneTitle);
		}
		else
		{
			final ImageStack s1 = imp.getImageStack();
			final ImageStack s2 = clone.getImageStack();
			for (int n = 1; n <= s1.getSize(); n++)
				s2.setPixels(s1.getProcessor(n).duplicate().getPixels(), n);
			clone.setStack(s2);
		}
		clone.setOverlay(null);
		return clone;
	}

	private static ImagePlus cloneImage(ImagePlus imp, ImagePlus mask, String cloneTitle)
	{
		ImagePlus clone = WindowManager.getImage(cloneTitle);
		final Integer maskId = (mask != null) ? new Integer(mask.getID()) : 0;
		if (clone == null || !clone.getProperty("MASK").equals(maskId))
		{
			if (clone != null)
				clone.close();
			clone = imp.duplicate();
			clone.setTitle(cloneTitle);
			clone.setProperty("MASK", maskId);
			clone.setOverlay(null);

			// Exclude outside the mask
			maskImage(clone, mask);
		}
		return clone;
	}

	private static void maskImage(ImagePlus clone, ImagePlus mask)
	{
		if (validMask(clone, mask))
		{
			final ImageStack cloneStack = clone.getImageStack();
			final ImageStack maskStack = mask.getImageStack();
			final boolean reloadMask = cloneStack.getSize() == maskStack.getSize();
			for (int slice = 1; slice <= cloneStack.getSize(); slice++)
			{
				final ImageProcessor ipClone = cloneStack.getProcessor(slice);
				final ImageProcessor ipMask = maskStack.getProcessor(reloadMask ? slice : 1);

				for (int i = ipClone.getPixelCount(); i-- > 0;)
					if (ipMask.get(i) == 0)
						ipClone.set(i, 0);
			}
			clone.updateAndDraw();
		}
	}

	private static int[] createOptionsArray()
	{
		if (myMinimumAboveSaddle == 0)
			return new int[] { FindFociProcessor.OPTION_MINIMUM_ABOVE_SADDLE };
		else if (myMinimumAboveSaddle == 1)
			return new int[] {
					FindFociProcessor.OPTION_MINIMUM_ABOVE_SADDLE | FindFociProcessor.OPTION_CONTIGUOUS_ABOVE_SADDLE };
		else if (myMinimumAboveSaddle == 2)
			return new int[] { 0 };
		else
			return new int[] { FindFociProcessor.OPTION_MINIMUM_ABOVE_SADDLE,
					FindFociProcessor.OPTION_MINIMUM_ABOVE_SADDLE | FindFociProcessor.OPTION_CONTIGUOUS_ABOVE_SADDLE,
					0 };
	}

	private static boolean invalidImage(ImagePlus imp)
	{
		if (null == imp)
		{
			IJ.noImage();
			return true;
		}

		if (!FindFoci.isSupported(imp.getBitDepth()))
		{
			IJ.showMessage("Error", "Only " + FindFoci.getSupported() + " images are supported");
			return true;
		}

		// This error is now handled later
		//Roi roi = imp.getRoi();
		//if (roi != null && roi.getType() != Roi.POINT)
		//{
		//	IJ.showMessage("Error", "Image must have a point ROI");
		//}
		return false;
	}

	private boolean showDialog(ImagePlus imp)
	{
		// Ensure the Dialog options are recorded. These are used later to write to file.
		final boolean recorderOn = Recorder.record;
		if (!recorderOn)
		{
			Recorder.saveCommand(); // Clear the old command options
			Recorder.record = true;
		}

		if (imp == null)
		{
			multiMode = true;
			if (!showMultiDialog())
			{
				Recorder.record = recorderOn; // Reset the recorder
				return false;
			}
		}

		// Get the optimisation search settings
		final ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);

		final ArrayList<String> newImageList = (multiMode) ? null : FindFoci.buildMaskList(imp);

		gd.addMessage("Runs the FindFoci algorithm using different parameters.\n" +
				"Results are compared to reference ROI points.\n\n" +
				"Input range fields accept 3 values: min,max,interval\n" +
				"Gaussian blur accepts comma-delimited values for the blur.");

		createSettings();
		gd.addChoice("Settings", SETTINGS, SETTINGS[0]);

		if (!multiMode)
			gd.addChoice("Mask", newImageList.toArray(new String[0]), myMaskImage);

		// Do not allow background above mean and background absolute to both be enabled.
		gd.addCheckbox("Background_SD_above_mean", myBackgroundStdDevAboveMean);
		gd.addCheckbox("Background_Absolute", (myBackgroundStdDevAboveMean) ? false : myBackgroundAbsolute);

		gd.addStringField("Background_parameter", myBackgroundParameter, 12);
		gd.addCheckbox("Background_Auto_Threshold", myBackgroundAuto);
		gd.addStringField("Auto_threshold", myThresholdMethod, 25);
		gd.addStringField("Statistics_mode", myStatisticsMode, 25);
		gd.addCheckbox("Search_above_background", mySearchAboveBackground);
		gd.addCheckbox("Search_fraction_of_peak", mySearchFractionOfPeak);
		gd.addStringField("Search_parameter", mySearchParameter, 12);
		gd.addChoice("Minimum_peak_height", FindFoci.peakMethods, FindFoci.peakMethods[myPeakMethod]);
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
		gd.addChoice("Match_search_method", matchSearchMethods, matchSearchMethods[myMatchSearchMethod]);
		gd.addNumericField("Match_search_distance", myMatchSearchDistance, 2);
		gd.addChoice("Result_sort_method", sortMethods, sortMethods[myResultsSortMethod]);
		gd.addNumericField("F-beta", myBeta, 2);
		gd.addNumericField("Maximum_results", myMaxResults, 0);
		gd.addNumericField("Step_limit", STEP_LIMIT, 0);
		if (!multiMode)
		{
			gd.addCheckbox("Show_score_images", myShowScoreImages);
			gd.addFilenameField("Result_file", myResultFile, 35);

			// Add a message about double clicking the result table to show the result
			gd.addMessage("Note: Double-click an entry in the optimiser results table\n" +
					"to view the FindFoci output. This only works for the most recent\n" +
					"set of results in the table.");
		}

		gd.addHelp(uk.ac.sussex.gdsc.help.URL.FIND_FOCI);

		if (!java.awt.GraphicsEnvironment.isHeadless())
		{
			checkbox = gd.getCheckboxes();
			choice = gd.getChoices();

			saveCustomSettings(gd);

			// Listen for changes
			addListeners(gd);
		}

		gd.showDialog();
		if (gd.wasCanceled())
		{
			Recorder.record = recorderOn; // Reset the recorder
			return false;
		}

		// Ignore the settings field
		gd.getNextChoiceIndex();

		if (!multiMode)
			myMaskImage = gd.getNextChoice();
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
		myBeta = gd.getNextNumber();
		myMaxResults = (int) gd.getNextNumber();
		STEP_LIMIT = (int) gd.getNextNumber();
		if (!multiMode)
		{
			myShowScoreImages = gd.getNextBoolean();
			myResultFile = gd.getNextString();
		}
		Recorder.record = recorderOn; // Reset the recorder

		// This only works if we do not attach as a dialogListener to the GenericDialog
		optimiserCommandOptions = Recorder.getCommandOptions();

		// Validate the chosen parameters
		if (myBackgroundStdDevAboveMean && myBackgroundAbsolute)
		{
			IJ.error("Cannot optimise background methods 'SD above mean' and 'Absolute' using the same parameters");
			return false;
		}
		if (!(myBackgroundStdDevAboveMean | myBackgroundAbsolute | myBackgroundAuto))
		{
			IJ.error("Require at least one background method");
			return false;
		}
		if (!(mySearchAboveBackground | mySearchFractionOfPeak))
		{
			IJ.error("Require at least one background search method");
			return false;
		}

		// Check which options to optimise
		optionsArray = createOptionsArray();

		parseThresholdMethods();
		if (myBackgroundAuto && thresholdMethods.length == 0)
		{
			IJ.error("No recognised methods for auto-threshold");
			return false;
		}

		parseStatisticsModes();

		backgroundMethodArray = createBackgroundArray();
		parseBackgroundLimits();
		if (myBackgroundParameterMax < myBackgroundParameterMin)
		{
			IJ.error("Background parameter max must be greater than min");
			return false;
		}
		myBackgroundParameterMinArray = createBackgroundLimits();

		searchMethodArray = createSearchArray();
		parseSearchLimits();
		if (mySearchParameterMax < mySearchParameterMin)
		{
			IJ.error("Search parameter max must be greater than min");
			return false;
		}
		mySearchParameterMinArray = createSearchLimits();

		parseMinSizeLimits();
		if (myMinSizeMax < myMinSizeMin)
		{
			IJ.error("Size max must be greater than min");
			return false;
		}

		parsePeakParameterLimits();
		if (myPeakParameterMax < myPeakParameterMin)
		{
			IJ.error("Peak parameter max must be greater than min");
			return false;
		}

		sortMethodArray = createSortArray();
		if (sortMethodArray.length == 0)
		{
			IJ.error("Require at least one sort method");
			return false;
		}

		blurArray = createBlurArray();

		centreMethodArray = createCentreArray();
		parseCentreLimits();
		if (myCentreParameterMax < myCentreParameterMin)
		{
			IJ.error("Centre parameter max must be greater than min");
			return false;
		}
		myCentreParameterMinArray = createCentreMinLimits();
		myCentreParameterMaxArray = createCentreMaxLimits();
		myCentreParameterIntervalArray = createCentreIntervals();

		if (!multiMode)
		{
			final ImagePlus mask = WindowManager.getImage(myMaskImage);
			if (!validMask(imp, mask))
				statisticsModes = new String[] { "Both" };
		}

		if (myMatchSearchMethod == 1 && myMatchSearchDistance < 1)
			IJ.log("WARNING: Absolute peak match distance is less than 1 pixel: " + myMatchSearchDistance);

		// Count the number of options
		combinations = countSteps();
		if (combinations >= STEP_LIMIT)
		{
			IJ.error("Maximum number of optimisation steps exceeded: " + combinations + " >> " + STEP_LIMIT);
			return false;
		}

		final YesNoCancelDialog d = new YesNoCancelDialog(IJ.getInstance(), TITLE,
				combinations + " combinations. Do you wish to proceed?");
		if (!d.yesPressed())
			return false;

		return true;
	}

	private boolean showMultiDialog()
	{
		final GenericDialog gd = new GenericDialog(TITLE);
		gd.addMessage("Run " + TITLE +
				" on a set of images.\n \nAll images in a directory will be processed.\n \nOptional mask images in the input directory should be named:\n[image_name].mask.[ext]\nor placed in the mask directory with the same name as the parent image.");
		gd.addStringField("Input_directory", inputDirectory);
		gd.addStringField("Mask_directory", maskDirectory);
		gd.addStringField("Output_directory", outputDirectory);
		gd.addMessage("[Note: Double-click a text field to open a selection dialog]");
		gd.addMessage(
				"The score metric for each parameter combination is computed per image.\nThe scores are converted then averaged across all images.");
		gd.addChoice("Score_conversion", SCORING_MODES, SCORING_MODES[scoringMode]);
		gd.addCheckbox("Re-use_results", reuseResults);
		final Vector<TextField> texts = gd.getStringFields();
		for (final TextField tf : texts)
		{
			tf.addMouseListener(this);
			tf.setColumns(50);
		}

		gd.addHelp(uk.ac.sussex.gdsc.help.URL.FIND_FOCI);
		gd.showDialog();
		if (gd.wasCanceled())
			return false;

		inputDirectory = gd.getNextString();
		if (!new File(inputDirectory).isDirectory())
		{
			IJ.error(TITLE, "Input directory is not a valid directory: " + inputDirectory);
			return false;
		}
		maskDirectory = gd.getNextString();
		if ((maskDirectory != null && maskDirectory.length() > 0) && !new File(maskDirectory).isDirectory())
		{
			IJ.error(TITLE, "Mask directory is not a valid directory: " + maskDirectory);
			return false;
		}
		outputDirectory = gd.getNextString();
		if (!new File(outputDirectory).isDirectory())
		{
			IJ.error(TITLE, "Output directory is not a valid directory: " + outputDirectory);
			return false;
		}

		inputDirectory = Utils.addFileSeparator(inputDirectory);
		maskDirectory = Utils.addFileSeparator(maskDirectory);
		outputDirectory = Utils.addFileSeparator(outputDirectory);

		scoringMode = gd.getNextChoiceIndex();
		reuseResults = gd.getNextBoolean();
		Prefs.set(INPUT_DIRECTORY, inputDirectory);
		Prefs.set(MASK_DIRECTORY, maskDirectory);
		Prefs.set(OUTPUT_DIRECTORY, outputDirectory);
		Prefs.set(SCORING_MODE, scoringMode);
		Prefs.set(REUSE_RESULTS, reuseResults);
		return true;
	}

	private static String createSortOptions()
	{
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

	private static StringBuilder addSortOption(StringBuilder sb, int method)
	{
		sb.append("[").append(method).append("] ").append(FindFoci.sortIndexMethods[method]);
		return sb;
	}

	private static String createCentreOptions()
	{
		final StringBuilder sb = new StringBuilder();
		sb.append("Centre options (comma-delimited):\n");
		for (int method = 0; method < 4; method++)
		{
			addCentreOption(sb, method).append("; ");
			if ((method + 1) % 2 == 0)
				sb.append("\n");
		}
		return sb.toString();
	}

	private static StringBuilder addCentreOption(StringBuilder sb, int method)
	{
		sb.append("[").append(method).append("] ").append(FindFoci.getCentreMethods()[method]);
		return sb;
	}

	private void parseThresholdMethods()
	{
		final String[] values = myThresholdMethod.split("\\s*;\\s*|\\s*,\\s*|\\s*:\\s*");
		final LinkedList<String> methods = new LinkedList<>();
		for (final String method : values)
			validThresholdMethod(method, methods);
		thresholdMethods = methods.toArray(new String[0]);
	}

	private static void validThresholdMethod(String method, LinkedList<String> methods)
	{
		for (final String m : FindFoci.autoThresholdMethods)
			if (m.equalsIgnoreCase(method))
			{
				methods.add(m);
				return;
			}
	}

	private void parseStatisticsModes()
	{
		final String[] values = myStatisticsMode.split("\\s*;\\s*|\\s*,\\s*|\\s*:\\s*");
		final LinkedList<String> modes = new LinkedList<>();
		for (final String mode : values)
			validStatisticsMode(mode, modes);
		if (modes.isEmpty())
			modes.add("both");
		statisticsModes = modes.toArray(new String[0]);
	}

	private static void validStatisticsMode(String mode, LinkedList<String> modes)
	{
		for (final String m : FindFoci.statisticsModes)
			if (m.equalsIgnoreCase(mode))
			{
				modes.add(m);
				return;
			}
	}

	private static int convertStatisticsMode(String mode)
	{
		if (mode.equalsIgnoreCase("inside"))
			return FindFociProcessor.OPTION_STATS_INSIDE;
		if (mode.equalsIgnoreCase("outside"))
			return FindFociProcessor.OPTION_STATS_OUTSIDE;
		return 0;
	}

	private int[] createBackgroundArray()
	{
		final int[] array = new int[countBackgroundFlags()];
		int i = 0;
		if (myBackgroundAbsolute)
		{
			array[i] = FindFociProcessor.BACKGROUND_ABSOLUTE;
			i++;
		}
		if (myBackgroundAuto)
			for (@SuppressWarnings("unused")
			final String method : thresholdMethods)
			{
				array[i] = FindFociProcessor.BACKGROUND_AUTO_THRESHOLD;
				i++;
			}
		if (myBackgroundStdDevAboveMean)
		{
			array[i] = FindFociProcessor.BACKGROUND_STD_DEV_ABOVE_MEAN;
			i++;
		}
		return array;
	}

	private int countBackgroundFlags()
	{
		int i = 0;
		if (myBackgroundAbsolute)
			i++;
		if (myBackgroundAuto)
			i += thresholdMethods.length;
		if (myBackgroundStdDevAboveMean)
			i++;
		return i;
	}

	private void parseBackgroundLimits()
	{
		double[] values = splitValues(myBackgroundParameter);
		values = checkValuesTriplet("Background parameter", values, 0, 1);
		myBackgroundParameterMin = values[0];
		myBackgroundParameterMax = values[1];
		myBackgroundParameterInterval = values[2];
	}

	private static double[] checkValuesTriplet(String name, double[] values, double defaultMin, double defaultInterval)
	{
		if (values.length == 0)
		{
			Utils.log("%s Warning : %s : No min:max:increment, setting to default minimum %s", TITLE, name,
					Utils.rounded(defaultMin));
			return new double[] { defaultMin, defaultMin, defaultInterval };
		}
		double min, max, interval;

		min = values[0];
		if (min < defaultMin)
		{
			Utils.log("%s Warning : %s : Minimum below default (%f < %s), setting to default", TITLE, name, min,
					Utils.rounded(defaultMin));
			min = defaultMin;
		}

		max = min;
		interval = defaultInterval;
		if (values.length > 1)
		{
			max = values[1];
			if (max < min)
			{
				Utils.log("%s Warning : %s : Maximum below minimum (%f < %f), setting to minimum", TITLE, name, max,
						min);
				max = min;
			}

			if (values.length > 2)
			{
				interval = values[2];
				if (interval <= 0)
				{
					Utils.log("%s Warning : %s : Interval is not strictly positive (%f), setting to default (%s)",
							TITLE, name, interval, Utils.rounded(defaultInterval));
					interval = defaultInterval;
				}
			}
		}

		return new double[] { min, max, interval };
	}

	private static double[] splitValues(String text)
	{
		final String[] tokens = text.split(";|,|:");
		final StoredData list = new StoredData(tokens.length);
		for (final String token : tokens)
			try
			{
				list.add(Double.parseDouble(token));
			}
			catch (final Exception e)
			{
				// Ignore
			}

		return list.getValues();
	}

	private double[] createBackgroundLimits()
	{
		final double[] limits = new double[backgroundMethodArray.length];
		for (int i = limits.length; i-- > 0;)
			limits[i] = getBackgroundLimit(backgroundMethodArray[i]);
		return limits;
	}

	private double getBackgroundLimit(int backgroundMethod)
	{
		return backgroundMethodHasParameter(backgroundMethod) ? myBackgroundParameterMin : myBackgroundParameterMax;
	}

	private static boolean backgroundMethodHasStatisticsMode(int backgroundMethod)
	{
		return !(backgroundMethod == FindFociProcessor.BACKGROUND_NONE ||
				backgroundMethod == FindFociProcessor.BACKGROUND_ABSOLUTE);
	}

	private static boolean backgroundMethodHasParameter(int backgroundMethod)
	{
		return !(backgroundMethod == FindFociProcessor.BACKGROUND_NONE ||
				backgroundMethod == FindFociProcessor.BACKGROUND_MEAN ||
				backgroundMethod == FindFociProcessor.BACKGROUND_AUTO_THRESHOLD);
	}

	private static int[] createSearchArray()
	{
		final int[] array = new int[countSearchFlags()];
		int i = 0;
		if (mySearchAboveBackground)
		{
			array[i] = FindFociProcessor.SEARCH_ABOVE_BACKGROUND;
			i++;
		}
		if (mySearchFractionOfPeak)
		{
			array[i] = FindFociProcessor.SEARCH_FRACTION_OF_PEAK_MINUS_BACKGROUND;
			i++;
		}
		return array;
	}

	private static int countSearchFlags()
	{
		int i = 0;
		if (mySearchAboveBackground)
			i++;
		if (mySearchFractionOfPeak)
			i++;
		return i;
	}

	private void parseSearchLimits()
	{
		double[] values = splitValues(mySearchParameter);
		values = checkValuesTriplet("Search parameter", values, 0, 1);
		mySearchParameterMin = values[0];
		mySearchParameterMax = values[1];
		mySearchParameterInterval = values[2];
	}

	private double[] createSearchLimits()
	{
		final double[] limits = new double[searchMethodArray.length];
		for (int i = limits.length; i-- > 0;)
			limits[i] = getSearchLimit(searchMethodArray[i]);
		return limits;
	}

	private double getSearchLimit(int searchMethod)
	{
		return searchMethodHasParameter(searchMethod) ? mySearchParameterMin : mySearchParameterMax;
	}

	private static boolean searchMethodHasParameter(int searchMethod)
	{
		return !(searchMethod == FindFociProcessor.SEARCH_ABOVE_BACKGROUND);
	}

	private void parseMinSizeLimits()
	{
		double[] values = splitValues(myMinSizeParameter);
		values = checkValuesTriplet("Min size parameter", values, 1, 1);
		myMinSizeMin = (int) values[0];
		myMinSizeMax = (int) values[1];
		myMinSizeInterval = (int) values[2];
	}

	private void parsePeakParameterLimits()
	{
		double[] values = splitValues(myPeakParameter);
		values = checkValuesTriplet("Peak parameter", values, 0, 1);
		myPeakParameterMin = values[0];
		myPeakParameterMax = values[1];
		myPeakParameterInterval = values[2];
	}

	private static int[] createSortArray()
	{
		final double[] values = splitValues(mySortMethod);
		final TIntHashSet set = new TIntHashSet(values.length);
		for (final double v : values)
		{
			final int method = (int) v;
			if (method >= 0 && method <= FindFociProcessor.SORT_AVERAGE_INTENSITY_MINUS_MIN)
				set.add(method);
		}
		if (set.isEmpty())
		{
			Utils.log("%s Warning : Sort method : No values, setting to default %d", TITLE,
					FindFociProcessor.SORT_INTENSITY);
			return new int[] { FindFociProcessor.SORT_INTENSITY }; // Default
		}
		final int[] array = set.toArray();
		Arrays.sort(array);
		return array;
	}

	private static double[] createBlurArray()
	{
		final double[] values = splitValues(myGaussianBlur);
		final TDoubleHashSet set = new TDoubleHashSet(values.length);
		for (final double v : values)
			if (v >= 0)
				set.add(v);
		if (set.isEmpty())
		{
			Utils.log("%s Warning : Gaussian blur : No values, setting to default 0", TITLE);
			return new double[] { 0 }; // Default
		}
		final double[] array = set.toArray();
		Arrays.sort(array);
		return array;
	}

	private static int[] createCentreArray()
	{
		final double[] values = splitValues(myCentreMethod);
		final TIntHashSet set = new TIntHashSet(values.length);
		for (final double v : values)
		{
			final int method = (int) v;
			if (method >= 0 && method <= FindFoci.CENTRE_GAUSSIAN_ORIGINAL)
				set.add(method);
		}
		if (set.isEmpty())
		{
			Utils.log("%s Warning : Centre method : No values, setting to default %d", TITLE,
					FindFoci.CENTRE_MAX_VALUE_SEARCH);
			return new int[] { FindFoci.CENTRE_MAX_VALUE_SEARCH }; // Default
		}
		final int[] array = set.toArray();
		Arrays.sort(array);
		return array;
	}

	private void parseCentreLimits()
	{
		double[] values = splitValues(myCentreParameter);
		values = checkValuesTriplet("Centre parameter", values, 0, 1);
		myCentreParameterMin = (int) values[0];
		myCentreParameterMax = (int) values[1];
		myCentreParameterInterval = (int) values[2];
	}

	private int[] createCentreMinLimits()
	{
		final int[] limits = new int[centreMethodArray.length];
		for (int i = limits.length; i-- > 0;)
			limits[i] = getCentreMinLimit(centreMethodArray[i]);
		return limits;
	}

	private int getCentreMinLimit(int centreMethod)
	{
		// If a range has been specified then run the optimiser for average and maximum projection, otherwise use average projection only.
		if (centreMethod == FindFoci.CENTRE_GAUSSIAN_SEARCH)
			return (myCentreParameterMin < myCentreParameterMax) ? 0 : 1;

		if (centreMethod == FindFoci.CENTRE_OF_MASS_SEARCH)
			return myCentreParameterMin;

		return 0; // Other methods have no parameters
	}

	private int[] createCentreMaxLimits()
	{
		final int[] limits = new int[centreMethodArray.length];
		for (int i = limits.length; i-- > 0;)
			limits[i] = getCentreMaxLimit(centreMethodArray[i]);
		return limits;
	}

	private int getCentreMaxLimit(int centreMethod)
	{
		if (centreMethod == FindFoci.CENTRE_GAUSSIAN_SEARCH)
			return 1; // Average projection

		if (centreMethod == FindFoci.CENTRE_OF_MASS_SEARCH)
			return myCentreParameterMax; // Limit can be any value above zero

		return 0; // Other methods have no parameters
	}

	private int[] createCentreIntervals()
	{
		final int[] limits = new int[centreMethodArray.length];
		for (int i = limits.length; i-- > 0;)
			limits[i] = getCentreInterval(centreMethodArray[i]);
		return limits;
	}

	private int getCentreInterval(int centreMethod)
	{
		if (centreMethod == FindFoci.CENTRE_GAUSSIAN_SEARCH)
			return 1;
		return myCentreParameterInterval;
	}

	private static boolean centreMethodHasParameter(int centreMethod)
	{
		return (centreMethod == FindFoci.CENTRE_OF_MASS_SEARCH || centreMethod == FindFoci.CENTRE_GAUSSIAN_SEARCH);
	}

	// Do not get warnings about unused variables
	@SuppressWarnings("unused")
	private int countSteps()
	{
		final int stepLimit = STEP_LIMIT;
		int steps = 0;
		for (final double blur : blurArray)
			for (int b = 0; b < backgroundMethodArray.length; b++)
			{
				final String[] myStatsModes = backgroundMethodHasStatisticsMode(backgroundMethodArray[b])
						? statisticsModes
						: new String[] { "Both" };
				for (final String statsMode : myStatsModes)
					for (double backgroundParameter = myBackgroundParameterMinArray[b]; backgroundParameter <= myBackgroundParameterMax; backgroundParameter += myBackgroundParameterInterval)
						for (int minSize = myMinSizeMin; minSize <= myMinSizeMax; minSize += myMinSizeInterval)
							for (int s = 0; s < searchMethodArray.length; s++)
								for (double searchParameter = mySearchParameterMinArray[s]; searchParameter <= mySearchParameterMax; searchParameter += mySearchParameterInterval)
									for (double peakParameter = myPeakParameterMin; peakParameter <= myPeakParameterMax; peakParameter += myPeakParameterInterval)
										for (final int options : optionsArray)
											for (final int sortMethod : sortMethodArray)
												for (int c = 0; c < centreMethodArray.length; c++)
													for (double centreParameter = myCentreParameterMinArray[c]; centreParameter <= myCentreParameterMaxArray[c]; centreParameter += myCentreParameterIntervalArray[c])
														// Simple check to ensure the user has not configured something incorrectly
														if (steps++ >= stepLimit)
															return steps;
			}
		return steps;
	}

	private void createResultsWindow(ImagePlus imp, ImagePlus mask, ArrayList<Result> results)
	{
		String heading = null;
		lastImp = imp;
		lastMask = mask;
		lastResults = results;
		if (resultsWindow == null || !resultsWindow.isShowing())
		{
			heading = createResultsHeader(true, true);
			resultsWindow = new TextWindow(TITLE + " Results", heading, "", 1000, 300);
			if (resultsWindow.getTextPanel() != null)
				resultsWindow.getTextPanel().addMouseListener(this);
		}
	}

	private static OutputStreamWriter createResultsFile(Options bestOptions, ImagePlus imp, ImagePlus mask,
			String resultFile)
	{
		OutputStreamWriter out = null;
		try
		{
			final String filename = resultFile + ".results.xls";

			final File file = new File(filename);
			if (!file.exists())
				if (file.getParent() != null)
					new File(file.getParent()).mkdirs();

			// Save results to file
			final FileOutputStream fos = new FileOutputStream(filename);
			out = new OutputStreamWriter(fos, "UTF-8");

			String maskTitle = "";
			if (imp != null)
			{
				out.write("# ImageJ Script to repeat the optimiser and then run the optimal parameters\n#\n");
				if (mask != null)
				{
					out.write("# open(\"" + getFilename(mask) + "\");\n");
					maskTitle = mask.getTitle();
				}
				out.write("# open(\"" + getFilename(imp) + "\");\n");

			}
			// Write the ImageJ macro command
			out.write(String.format("# run(\"FindFoci Optimiser\", \"%s\")\n", optimiserCommandOptions));

			addFindFociCommand(out, bestOptions, maskTitle);

			return out;
		}
		catch (final Exception e)
		{
			IJ.log("Failed to create results file '" + resultFile + ".results.xls': " + e.getMessage());
			if (out != null)
				try
				{
					out.close();
				}
				catch (final IOException ioe)
				{
					// Ignore
				}
		}
		return null;
	}

	private static String getFilename(ImagePlus imp)
	{
		final FileInfo info = imp.getOriginalFileInfo();
		if (info != null)
			return Utils.combinePath(info.directory, info.fileName);
		return imp.getTitle();
	}

	private static String createResultsHeader(boolean withScore, boolean milliSeconds)
	{
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
		if (withScore)
			sb.append("Score\t");
		sb.append("RMSD\t");
		if (milliSeconds)
			sb.append("mSec\n");
		else
			sb.append("nanoSec\n");
		return sb.toString();
	}

	private Result analyseResults(int id, AssignedPoint[] roiPoints, ArrayList<FindFociResult> resultsArray,
			double dThreshold, Options options, long time, double beta, boolean is3D)
	{
		// Extract results for analysis
		final AssignedPoint[] predictedPoints = extractedPredictedPoints(resultsArray);

		MatchResult matchResult;
		if (is3D)
			matchResult = MatchCalculator.analyseResults3D(roiPoints, predictedPoints, dThreshold);
		else
			matchResult = MatchCalculator.analyseResults2D(roiPoints, predictedPoints, dThreshold);

		return new Result(id, options, matchResult.getNumberPredicted(), matchResult.getTruePositives(),
				matchResult.getFalsePositives(), matchResult.getFalseNegatives(), time, beta, matchResult.getRMSD());
	}

	private static AssignedPoint[] extractedPredictedPoints(ArrayList<FindFociResult> resultsArray)
	{
		final AssignedPoint[] predictedPoints = new AssignedPoint[resultsArray.size()];
		for (int i = 0; i < resultsArray.size(); i++)
		{
			final FindFociResult result = resultsArray.get(i);
			predictedPoints[i] = new AssignedPoint(result.x, result.y, result.z, i);
		}
		return predictedPoints;
	}

	/**
	 * Convert the FindFoci parameters into a text representation.
	 *
	 * @param blur
	 *            the blur
	 * @param backgroundMethod
	 *            the background method
	 * @param thresholdMethod
	 *            the threshold method
	 * @param backgroundParameter
	 *            the background parameter
	 * @param maxPeaks
	 *            the max peaks
	 * @param minSize
	 *            the min size
	 * @param searchMethod
	 *            the search method
	 * @param searchParameter
	 *            the search parameter
	 * @param peakMethod
	 *            the peak method
	 * @param peakParameter
	 *            the peak parameter
	 * @param sortMethod
	 *            the sort method
	 * @param options
	 *            the options
	 * @param centreMethod
	 *            the centre method
	 * @param centreParameter
	 *            the centre parameter
	 * @return the string
	 */
	static String createParameters(double blur, int backgroundMethod, String thresholdMethod,
			double backgroundParameter, int maxPeaks, int minSize, int searchMethod, double searchParameter,
			int peakMethod, double peakParameter, int sortMethod, int options, int centreMethod, double centreParameter)
	{
		// Output results
		final String spacer = " : ";
		final StringBuilder sb = new StringBuilder();
		sb.append(blur).append('\t');
		sb.append(FindFoci.backgroundMethods[backgroundMethod]);
		if (backgroundMethodHasStatisticsMode(backgroundMethod))
			sb.append(" (").append(FindFoci.getStatisticsMode(options)).append(") ");
		sb.append(spacer);
		sb.append(backgroundMethodHasParameter(backgroundMethod) ? IJ.d2s(backgroundParameter, 2) : thresholdMethod)
				.append('\t');
		sb.append(maxPeaks).append('\t');
		sb.append(minSize);
		if ((options & FindFociProcessor.OPTION_MINIMUM_ABOVE_SADDLE) != 0)
		{
			sb.append(" >saddle");
			if ((options & FindFociProcessor.OPTION_CONTIGUOUS_ABOVE_SADDLE) != 0)
				sb.append(" conn");
		}
		sb.append('\t');
		sb.append(FindFoci.searchMethods[searchMethod]);
		if (searchMethodHasParameter(searchMethod))
			sb.append(spacer).append(IJ.d2s(searchParameter, 2));
		sb.append('\t');
		sb.append(FindFoci.peakMethods[peakMethod]).append(spacer);
		sb.append(IJ.d2s(peakParameter, 2)).append('\t');
		sb.append(FindFoci.sortIndexMethods[sortMethod]).append('\t');
		sb.append(FindFoci.getCentreMethods()[centreMethod]);
		if (centreMethodHasParameter(centreMethod))
			sb.append(spacer).append(IJ.d2s(centreParameter, 2));
		sb.append('\t');
		return sb.toString();
	}

	/**
	 * Convert the FindFoci text representation into Options.
	 *
	 * @param parameters
	 *            the parameters
	 * @return the options
	 */
	private Options createOptions(String parameters)
	{
		final String[] fields = p.split(parameters);
		try
		{
			final double blur = Double.parseDouble(fields[0]);
			int backgroundMethod = -1;
			for (int i = 0; i < FindFoci.backgroundMethods.length; i++)
				if (fields[1].startsWith(FindFoci.backgroundMethods[i]))
				{
					backgroundMethod = i;
					break;
				}
			if (backgroundMethod < 0)
				throw new Exception("No background method");
			int options = 0;
			if (backgroundMethodHasStatisticsMode(backgroundMethod))
			{
				final int first = fields[1].indexOf('(') + 1;
				final int last = fields[1].indexOf(')', first);
				final String mode = fields[1].substring(first, last);
				if (mode.equals("Inside"))
					options |= FindFociProcessor.OPTION_STATS_INSIDE;
				else if (mode.equals("Outside"))
					options |= FindFociProcessor.OPTION_STATS_OUTSIDE;
				else
					options |= FindFociProcessor.OPTION_STATS_INSIDE | FindFociProcessor.OPTION_STATS_OUTSIDE;
			}
			int index = fields[1].indexOf(" : ") + 3;
			String thresholdMethod = fields[1].substring(index);
			double backgroundParameter = 0;
			if (backgroundMethodHasParameter(backgroundMethod))
			{
				backgroundParameter = Double.parseDouble(thresholdMethod);
				thresholdMethod = "";
			}
			final int maxPeaks = Integer.parseInt(fields[2]);
			// XXX
			index = fields[3].indexOf(" ");
			if (index > 0)
			{
				options |= FindFociProcessor.OPTION_MINIMUM_ABOVE_SADDLE;
				if (fields[3].contains("conn"))
					options |= FindFociProcessor.OPTION_CONTIGUOUS_ABOVE_SADDLE;
				fields[3] = fields[3].substring(0, index);
			}
			final int minSize = Integer.parseInt(fields[3]);
			int searchMethod = -1;
			for (int i = 0; i < FindFoci.searchMethods.length; i++)
				if (fields[4].startsWith(FindFoci.searchMethods[i]))
				{
					searchMethod = i;
					break;
				}
			if (searchMethod < 0)
				throw new Exception("No search method");
			double searchParameter = 0;
			if (searchMethodHasParameter(searchMethod))
			{
				index = fields[4].indexOf(" : ") + 3;
				searchParameter = Double.parseDouble(fields[4].substring(index));
			}
			int peakMethod = -1;
			for (int i = 0; i < FindFoci.peakMethods.length; i++)
				if (fields[5].startsWith(FindFoci.peakMethods[i]))
				{
					peakMethod = i;
					break;
				}
			if (peakMethod < 0)
				throw new Exception("No peak method");
			index = fields[5].indexOf(" : ") + 3;
			final double peakParameter = Double.parseDouble(fields[5].substring(index));
			int sortMethod = -1;
			for (int i = 0; i < FindFoci.sortIndexMethods.length; i++)
				if (fields[6].startsWith(FindFoci.sortIndexMethods[i]))
				{
					sortMethod = i;
					break;
				}
			if (sortMethod < 0)
				throw new Exception("No sort method");
			int centreMethod = -1;
			for (int i = 0; i < FindFoci.getCentreMethods().length; i++)
				if (fields[7].startsWith(FindFoci.getCentreMethods()[i]))
				{
					centreMethod = i;
					break;
				}
			if (centreMethod < 0)
				throw new Exception("No centre method");
			double centreParameter = 0;
			if (centreMethodHasParameter(centreMethod))
			{
				index = fields[7].indexOf(" : ") + 3;
				centreParameter = Double.parseDouble(fields[7].substring(index));
			}

			Options o = new Options(blur, backgroundMethod, backgroundParameter, thresholdMethod, searchMethod,
					searchParameter, maxPeaks, minSize, peakMethod, peakParameter, sortMethod, options, centreMethod,
					centreParameter);

			// Debugging
			if (!o.createParameters().equals(parameters))
			{
				System.out.printf("Error converting parameters to FindFoci options:\n%s\n%s\n", parameters,
						o.createParameters());
				o = null;
			}

			return o;
		}
		catch (final Exception e)
		{
			System.out
					.println("Error converting parameters to FindFoci options: " + parameters + "\n" + e.getMessage());
			return null;
		}
	}

	private static double calculateFScore(double precision, double recall, double beta)
	{
		final double b2 = beta * beta;
		final double f = ((1.0 + b2) * precision * recall) / (b2 * precision + recall);
		return (Double.isNaN(f) ? 0 : f);
	}

	/**
	 * Extract the points for the given image. If a file exists in the same directory as the image with the suffix .csv,
	 * .xyz, or .txt then the program will attempt to load 3D coordinates from file. Otherwise the points are taken from
	 * the the ROI.
	 *
	 * The points are then filtered to include only those within the mask region (if the mask dimensions match those of
	 * the image).
	 *
	 * @param roi
	 *            the roi
	 * @param imp
	 *            the image
	 * @param mask
	 *            the mask
	 * @return the assigned points
	 */
	public static AssignedPoint[] extractRoiPoints(Roi roi, ImagePlus imp, ImagePlus mask)
	{
		AssignedPoint[] roiPoints = null;

		final boolean is3D = imp.getNSlices() > 1;

		roiPoints = loadPointsFromFile(imp);

		if (roiPoints == null)
			roiPoints = PointManager.extractRoiPoints(roi);

		if (!is3D)
			// Discard any potential z-information since the image is not 3D
			for (final AssignedPoint point : roiPoints)
				point.z = 0;

		// If the mask is not valid or we have no points then return
		if (!validMask(imp, mask) || roiPoints.length == 0)
			return roiPoints;

		return restrictToMask(mask, roiPoints);
	}

	/**
	 * Restrict the given points to the mask.
	 *
	 * @param mask
	 *            the mask
	 * @param roiPoints
	 *            the roi points
	 * @return the assigned points
	 */
	public static AssignedPoint[] restrictToMask(ImagePlus mask, AssignedPoint[] roiPoints)
	{
		if (mask == null)
			return roiPoints;

		// Check that the mask should be used in 3D
		final boolean is3D = is3D(roiPoints) && mask.getNSlices() > 1;

		// Look through the ROI points and exclude all outside the mask
		final ImageStack stack = mask.getStack();
		final int c = mask.getChannel();
		final int f = mask.getFrame();

		int id = 0;
		for (final AssignedPoint point : roiPoints)
			if (is3D)
			{
				// Check within the 3D mask
				if (point.z <= mask.getNSlices())
				{
					final int stackIndex = mask.getStackIndex(c, point.z, f);
					final ImageProcessor ipMask = stack.getProcessor(stackIndex);

					if (ipMask.get(point.getXint(), point.getYint()) > 0)
						roiPoints[id++] = point;
				}
			}
			else
				// Check all slices of the mask, i.e. a 2D projection
				for (int slice = 1; slice <= mask.getNSlices(); slice++)
				{
					final int stackIndex = mask.getStackIndex(c, slice, f);
					final ImageProcessor ipMask = stack.getProcessor(stackIndex);

					if (ipMask.get(point.getXint(), point.getYint()) > 0)
					{
						roiPoints[id++] = point;
						break;
					}
				}

		return Arrays.copyOf(roiPoints, id);
	}

	private static boolean is3D(AssignedPoint[] roiPoints)
	{
		if (roiPoints.length == 0)
			return false;

		// All points must have a z-coordinate above zero
		for (final AssignedPoint point : roiPoints)
			if (point.z < 1)
				return false;

		return true;
	}

	private static AssignedPoint[] loadPointsFromFile(ImagePlus imp)
	{
		final FileInfo fileInfo = imp.getOriginalFileInfo();
		if (fileInfo != null && fileInfo.directory != null)
		{
			String title = imp.getTitle();
			final int index = title.lastIndexOf('.');
			if (index != -1)
				title = title.substring(0, index);

			for (final String suffix : new String[] { ".csv", ".xyz", ".txt" })
			{
				final AssignedPoint[] roiPoints = loadPointsFromFile(fileInfo.directory + title + suffix);
				if (roiPoints != null)
					return roiPoints;
			}
		}
		return null;
	}

	private static Pattern pointsPattern = Pattern.compile("[, \t]+");

	private static AssignedPoint[] loadPointsFromFile(String filename)
	{
		if (filename == null)
			return null;
		final File file = new File(filename);
		if (!file.exists())
			return null;

		final int MAX_ERROR = 5;
		try (BufferedReader input = new BufferedReader(new UnicodeReader(new FileInputStream(filename), null)))
		{
			String line;
			int id = 0;
			int errors = 0;
			final ArrayList<AssignedPoint> points = new ArrayList<>();
			while ((line = input.readLine()) != null)
			{
				if (line.length() == 0)
					continue;
				if (line.charAt(0) == '#')
					continue;
				final String[] fields = pointsPattern.split(line);
				if (fields.length > 1)
					try
					{
						final int x = (int) Double.parseDouble(fields[0]);
						final int y = (int) Double.parseDouble(fields[1]);
						int z = 0;
						if (fields.length > 2)
							z = (int) Double.parseDouble(fields[2]);
						points.add(new AssignedPoint(x, y, z, ++id));
					}
					catch (final NumberFormatException e)
					{
						// Abort if too many errors
						if (++errors == MAX_ERROR)
							break;
					}
			}
			return (errors == MAX_ERROR) ? null : points.toArray(new AssignedPoint[points.size()]);
		}
		catch (final NumberFormatException e)
		{
			// ignore
		}
		catch (final IOException e)
		{
			// ignore
		}
		return null;
	}

	@SuppressWarnings("unused")
	private static Roi createRoi(List<Coordinate> points)
	{
		final int[] ox = new int[points.size()];
		final int[] oy = new int[points.size()];

		int i = 0;
		for (final Coordinate point : points)
		{
			ox[i] = point.getXint();
			oy[i] = point.getYint();
			i++;
		}
		return new PointRoi(ox, oy, ox.length);
	}

	private static Overlay createOverlay(List<Coordinate> points, ImagePlus imp)
	{
		final int c = imp.getChannel();
		final int f = imp.getFrame();
		final boolean isHyperStack = imp.isDisplayedHyperStack();

		final int[] ox = new int[points.size()];
		final int[] oy = new int[points.size()];
		final int[] oz = new int[points.size()];

		int i = 0;
		for (final Coordinate point : points)
		{
			ox[i] = point.getXint();
			oy[i] = point.getYint();
			oz[i] = point.getZint();
			i++;
		}

		final Overlay overlay = new Overlay();
		int remaining = ox.length;
		for (int ii = 0; ii < ox.length; ii++)
			// Find the next unprocessed slice
			if (oz[ii] != -1)
			{
				final int slice = oz[ii];
				// Extract all the points from this slice
				int[] x = new int[remaining];
				int[] y = new int[remaining];
				int count = 0;
				for (int j = ii; j < ox.length; j++)
					if (oz[j] == slice)
					{
						x[count] = ox[j];
						y[count] = oy[j];
						count++;
						oz[j] = -1; // Mark processed
					}
				x = Arrays.copyOf(x, count);
				y = Arrays.copyOf(y, count);
				final PointRoi roi = new PointRoi(x, y, count);
				if (isHyperStack)
					roi.setPosition(c, slice, f);
				else
					roi.setPosition(slice);
				roi.setShowLabels(false);
				overlay.add(roi);
				remaining -= count;
			}

		overlay.setStrokeColor(Color.cyan);

		return overlay;
	}

	private static boolean validMask(ImagePlus imp, ImagePlus mask)
	{
		return mask != null && mask.getWidth() == imp.getWidth() && mask.getHeight() == imp.getHeight() &&
				(mask.getNSlices() == imp.getNSlices() || mask.getStackSize() == 1);
	}

	private void sortResults(ArrayList<Result> results, int sortMethod)
	{
		if (sortMethod != SORT_NONE)
		{
			final int sortIndex = getSortIndex(sortMethod);
			final ResultComparator c = new ResultComparator(sortIndex);
			sortAndAssignRank(results, sortIndex, c);
		}
	}

	private static int getSortIndex(int sortMethod)
	{
		// Most of the sort methods correspond to the first items of the metrics array
		if (sortMethod <= SORT_JACCARD)
			return sortMethod - 1;

		// Process special cases
		switch (sortMethod)
		{
			case SORT_RMSD:
				return Result.RMSD;
		}

		// This is an error
		return -1;
	}

	private void sortResultsByScore(ArrayList<Result> results, boolean lowestFirst)
	{
		final int sortIndex = Result.SCORE;
		final ResultComparator c = new ResultComparator(sortIndex, lowestFirst);
		sortAndAssignRank(results, sortIndex, c);
	}

	/**
	 * Sort and assign rank.
	 *
	 * @param results
	 *            the results
	 * @param sortIndex
	 *            the sort index
	 * @param c
	 *            the c
	 */
	public void sortAndAssignRank(ArrayList<Result> results, int sortIndex, ResultComparator c)
	{
		Collections.sort(results, c);

		// Cannot assign a rank if we have not sorted
		int rank = 1;
		int count = 0;
		double score = results.get(0).metrics[sortIndex];
		for (final Result r : results)
		{
			if (score != r.metrics[sortIndex])
			{
				rank += count;
				count = 0;
				score = r.metrics[sortIndex];
			}
			r.metrics[Result.RANK] = rank;
			count++;
		}
	}

	private static void sortResultsById(ArrayList<Result> results)
	{
		Collections.sort(results);
	}

	private class Result implements Comparable<Result>
	{
		public static final int PRECISION = 0;
		public static final int RECALL = 1;
		public static final int F05 = 2;
		public static final int F1 = 3;
		public static final int F2 = 4;
		public static final int Fb = 5;
		public static final int JACCARD = 6;
		public static final int RANK = 7;
		public static final int SCORE = 8;
		public static final int RMSD = 9;

		public int id;
		public Options options;
		public String parameters;
		public int n, tp, fp, fn;
		public long time;
		public double[] metrics = new double[10];

		public Result(int id, Options options, int n, int tp, int fp, int fn, long time, double beta, double rmsd)
		{
			this.id = id;
			this.options = options;
			if (options != null)
				this.parameters = options.createParameters();
			this.n = n;
			this.tp = tp;
			this.fp = fp;
			this.fn = fn;
			this.time = time;

			if (tp + fp > 0)
				metrics[PRECISION] = (double) tp / (tp + fp);
			if (tp + fn > 0)
				metrics[RECALL] = (double) tp / (tp + fn);
			if (tp + fp + fn > 0)
				metrics[JACCARD] = (double) tp / (tp + fp + fn);
			metrics[F05] = calculateFScore(metrics[PRECISION], metrics[RECALL], 0.5);
			metrics[F1] = calculateFScore(metrics[PRECISION], metrics[RECALL], 1.0);
			metrics[F2] = calculateFScore(metrics[PRECISION], metrics[RECALL], 2.0);
			metrics[Fb] = calculateFScore(metrics[PRECISION], metrics[RECALL], beta);
			metrics[RMSD] = rmsd;
		}

		/**
		 * Add the values stored in the given result to the current values.
		 *
		 * @param result
		 *            the result
		 */
		public void add(Result result)
		{
			// Create a new RMSD
			// rmsd = Math.sqrt(sd / tp);
			final double sd1 = metrics[RMSD] * metrics[RMSD] * tp;
			final double sd2 = result.metrics[RMSD] * result.metrics[RMSD] * result.tp;
			metrics[RMSD] = Math.sqrt((sd1 + sd2) / (tp + result.tp));

			// Combine all other metrics
			n += result.n;
			tp += result.tp;
			fp += result.fp;
			fn += result.fn;
			time += result.time;
			for (int i = 0; i < metrics.length - 1; i++)
				metrics[i] += result.metrics[i];
		}

		public String getParameters()
		{
			return parameters;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		@Override
		public int compareTo(Result paramT)
		{
			return id - paramT.id;
		}
	}

	/**
	 * Provides the ability to sort the results arrays in ascending order
	 */
	private class ResultComparator implements Comparator<Result>
	{
		private final int sortIndex;
		private final int tieIndex;
		private final int sortRank;
		private final int tieRank;

		public ResultComparator(int sortIndex, boolean lowestFirst)
		{
			this.sortIndex = sortIndex;
			if (sortIndex == Result.RMSD)
				tieIndex = Result.JACCARD;
			else
				tieIndex = Result.RMSD;
			sortRank = (lowestFirst) ? 1 : -1;
			tieRank = getRankOfHighest(tieIndex);
		}

		public ResultComparator(int sortIndex)
		{
			this.sortIndex = sortIndex;
			if (sortIndex == Result.RMSD)
				tieIndex = Result.JACCARD;
			else
				tieIndex = Result.RMSD;
			sortRank = getRankOfHighest(sortIndex);
			tieRank = getRankOfHighest(tieIndex);
		}

		private int getRankOfHighest(int index)
		{
			switch (index)
			{
				case Result.RANK:
				case Result.RMSD:
					// Highest last
					return 1;
			}
			// Highest first
			return -1;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
		 */
		@Override
		public int compare(Result o1, Result o2)
		{
			// Require the highest is first
			if (o1.metrics[sortIndex] > o2.metrics[sortIndex])
				return sortRank;
			if (o1.metrics[sortIndex] < o2.metrics[sortIndex])
				return -sortRank;

			// Compare using the tie index
			if (o1.metrics[tieIndex] > o2.metrics[tieIndex])
				return tieRank;
			if (o1.metrics[tieIndex] < o2.metrics[tieIndex])
				return -tieRank;

			// Update this to not perform a comparison of the result parameter options
			if (o1.options != null && o2.options != null)
			{
				// Return method with most conservative settings
				final int[] result = new int[1];

				if (compare(o1.options.blur, o2.options.blur, result) != 0)
					return result[0];

				// Higher background methods are more general
				if (compare(o1.options.backgroundMethod, o2.options.backgroundMethod, result) != 0)
					return -result[0];
				if (compare(o1.options.backgroundParameter, o2.options.backgroundParameter, result) != 0)
					return result[0];

				// Smallest size is more general
				if (compare(o1.options.minSize, o2.options.minSize, result) != 0)
					return result[0];

				// Lower search methods are more general
				if (compare(o1.options.searchMethod, o2.options.searchMethod, result) != 0)
					return result[0];
				if (compare(o1.options.searchParameter, o2.options.searchParameter, result) != 0)
					return result[0];

				// Higher peak methods are more general
				if (compare(o1.options.peakMethod, o2.options.peakMethod, result) != 0)
					return -result[0];
				if (compare(o1.options.peakParameter, o2.options.peakParameter, result) != 0)
					return result[0];
			}

			// Return fastest method
			if (o1.time < o2.time)
				return -1;
			if (o1.time > o2.time)
				return 1;

			System.out.println("Sort error");
			return 0;
		}

		private int compare(double value1, double value2, int[] result)
		{
			if (value1 < value2)
				result[0] = -1;
			else if (value1 > value2)
				result[0] = 1;
			else
				result[0] = 0;
			return result[0];
		}

		private int compare(int value1, int value2, int[] result)
		{
			return result[0] = value1 - value2;
		}
	}

	private class Options
	{
		private String parameters = null;
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

		public Options(double blur, int backgroundMethod, double backgroundParameter, String autoThresholdMethod,
				int searchMethod, double searchParameter, int maxPeaks, int minSize, int peakMethod,
				double peakParameter, int sortIndex, int options, int centreMethod, double centreParameter)
		{
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
		 * @return the string representation of the parameters (the value is computed once and cached)
		 */
		public String createParameters()
		{
			if (parameters == null)
				parameters = FindFociOptimiser.createParameters(blur, backgroundMethod, autoThresholdMethod,
						backgroundParameter, maxPeaks, minSize, searchMethod, searchParameter, peakMethod,
						peakParameter, sortIndex, options, centreMethod, centreParameter);
			return parameters;
		}
	}

	private abstract class Counter
	{
		final int total;
		final int progressSize;
		int next = 0;

		public Counter(int total)
		{
			this.total = total;
			this.progressSize = Math.max(1, total / 400);
			next = progressSize;
			IJ.showProgress(0);
		}

		public void increment()
		{
			// Don't show progress all the time
			if (incrementAndGet() >= next)
			{
				IJ.showProgress(get(), total);
				next = get() + progressSize;
			}
		}

		public void increment(int delta)
		{
			// Don't show progress all the time
			if (addAndGet(delta) >= next)
			{
				IJ.showProgress(get(), total);
				next = get() + progressSize;
			}
		}

		protected abstract int incrementAndGet();

		protected abstract int addAndGet(int delta);

		protected abstract int get();
	}

	private class StandardCounter extends Counter
	{
		int step = 0;

		public StandardCounter(int total)
		{
			super(total);
		}

		@Override
		protected int incrementAndGet()
		{
			return ++step;
		}

		@Override
		protected int addAndGet(int delta)
		{
			return step += delta;
		}

		@Override
		protected int get()
		{
			return step;
		}
	}

	private class SynchronizedCounter extends Counter
	{
		AtomicInteger step = new AtomicInteger();

		public SynchronizedCounter(int total)
		{
			super(total);
		}

		@Override
		protected int incrementAndGet()
		{
			return step.incrementAndGet();
		}

		@Override
		protected int addAndGet(int delta)
		{
			return step.addAndGet(delta);
		}

		@Override
		protected int get()
		{
			return step.get();
		}
	}

	/**
	 * The Class OptimisationWorker.
	 */
	public class OptimisationWorker implements Runnable
	{

		/** The image. */
		String image;

		/** The counter. */
		Counter counter;

		/** The result. */
		OptimiserResult result = null;

		/**
		 * Instantiates a new optimisation worker.
		 *
		 * @param image
		 *            the image
		 * @param counter
		 *            the counter
		 */
		public OptimisationWorker(String image, Counter counter)
		{
			this.image = image;
			this.counter = counter;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see java.lang.Runnable#run()
		 */
		@Override
		public void run()
		{
			if (IJ.escapePressed())
				return;

			final ImagePlus imp = FindFoci.openImage(inputDirectory, image);
			// The input filename may not be an image
			if (imp == null)
			{
				IJ.log("Skipping file (it may not be an image): " + inputDirectory + image);
				// We can skip forward in the progress
				counter.increment(combinations);
				return;
			}
			final String[] maskPath = FindFoci.getMaskImage(inputDirectory, maskDirectory, image);
			final ImagePlus mask = FindFoci.openImage(maskPath[0], maskPath[1]);
			final String resultFile = outputDirectory + imp.getShortTitle();
			final String fullResultFile = resultFile + ".results.xls";
			boolean newResults = false;
			if (reuseResults && new File(fullResultFile).exists())
			{
				final ArrayList<Result> results = loadResults(fullResultFile);
				if (results != null && results.size() == combinations)
				{
					IJ.log("Re-using results: " + fullResultFile);
					// We can skip forward in the progress
					counter.increment(combinations);
					result = new OptimiserResult(results, 0);
				}
			}
			if (result == null)
			{
				IJ.log("Creating results: " + fullResultFile);
				newResults = true;
				result = runOptimiser(imp, mask, counter);
			}
			if (result != null)
			{
				if (newResults)
					saveResults(imp, mask, result.results, null, resultFile);

				checkOptimisationSpace(result, imp);

				// Reset to the order defined by the ID
				sortResultsById(result.results);
			}
		}
	}

	private static Pattern p = Pattern.compile("\t");

	/**
	 * Load the results from the specified file. We assign an arbitrary ID to each result using the unique combination
	 * of parameters.
	 *
	 * @param filename
	 *            the filename
	 * @return The results
	 */
	private ArrayList<Result> loadResults(String filename)
	{
		final ArrayList<Result> results = new ArrayList<>();

		if (countLines(filename) != combinations)
			return null;

		try (BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(filename))))
		{
			String line;
			boolean isRMSD = false;
			while ((line = input.readLine()) != null)
			{
				if (line.length() == 0)
					continue;
				if (line.charAt(0) == '#')
				{
					// Look for the RMSD field which was added later. This supports older results files
					if (line.contains("RMSD"))
						isRMSD = true;
					continue;
				}

				// Code using split and parse
				// # Rank	Blur	Background method	Max	Min	Search method	Peak method	Sort method	Centre method	N	TP	FP	FN	Jaccard	Precision	Recall	F0.5	F1	F2	F-beta	RMSD	mSec
				final int endIndex = getIndex(line, 8) + 1; // include the final tab
				final String parameters = line.substring(line.indexOf('\t') + 1, endIndex);
				final String metrics = line.substring(endIndex);
				final String[] fields = p.split(metrics);

				// Items we require
				final int id = getId(parameters);

				final int n = Integer.parseInt(fields[0]);
				final int tp = Integer.parseInt(fields[1]);
				final int fp = Integer.parseInt(fields[2]);
				final int fn = Integer.parseInt(fields[3]);
				double rmsd = 0;
				if (isRMSD)
					rmsd = Double.parseDouble(fields[fields.length - 2]);
				final long time = Long.parseLong(fields[fields.length - 1]);

				final Result r = new Result(id, null, n, tp, fp, fn, time, myBeta, rmsd);
				// Do not count on the Options being parsed from the parameters.
				r.parameters = parameters;
				r.options = optionsMap.get(id);
				results.add(r);
			}

			// If the results were loaded then we must sort them to get a rank
			sortResults(results, myResultsSortMethod);
		}
		catch (final ArrayIndexOutOfBoundsException e)
		{
			return null;
		}
		catch (final IOException e)
		{
			return null;
		}
		catch (final NumberFormatException e)
		{
			return null;
		}
		return results;
	}

	/**
	 * Count the number of valid lines in the file.
	 *
	 * @param filename
	 *            the filename
	 * @return The number of lines
	 */
	private static int countLines(String filename)
	{
		try (BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(filename))))
		{
			int count = 0;
			String line;
			while ((line = input.readLine()) != null)
			{
				if (line.length() == 0)
					continue;
				if (line.charAt(0) == '#')
					continue;
				count++;
			}
			return count;
		}
		catch (final IOException e)
		{
			return 0;
		}
	}

	/**
	 * Get the index of the nth occurrence of the tab character.
	 *
	 * @param line
	 *            the line
	 * @param n
	 *            the n
	 * @return the index
	 */
	private static int getIndex(String line, int n)
	{
		final char[] value = line.toCharArray();
		for (int i = 0; i < value.length; i++)
			if (value[i] == '\t')
				if (n-- <= 0)
					return i;
		return -1;
	}

	private final HashMap<String, Integer> idMap = new HashMap<>();
	private final HashMap<Integer, Options> optionsMap = new HashMap<>();
	private int nextId = 1;

	/**
	 * Get a unique ID for the parameters string.
	 *
	 * @param parameters
	 *            the parameters
	 * @return the ID
	 */
	private int getId(String parameters)
	{
		Integer i = idMap.get(parameters);
		if (i == null)
			i = createId(parameters);
		return i;
	}

	/**
	 * Create a unique ID for the parameters string.
	 *
	 * @param parameters
	 *            the parameters
	 * @return the ID
	 */
	private synchronized Integer createId(String parameters)
	{
		// Check again in case another thread just created it
		Integer i = idMap.get(parameters);
		if (i == null)
		{
			i = new Integer(nextId++);
			// Ensure we have options for every ID
			optionsMap.put(i, createOptions(parameters));
			idMap.put(parameters, i);
		}
		return i;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.awt.event.MouseListener#mouseClicked(java.awt.event.MouseEvent)
	 */
	@Override
	public void mouseClicked(MouseEvent e)
	{
		// Show the result that was double clicked in the result table
		if (e.getClickCount() > 1)
			// Double-click on the multi-mode dialog text fields
			if (e.getSource() instanceof TextField)
			{
				final TextField tf = (TextField) e.getSource();
				String path = tf.getText();
				final boolean recording = Recorder.record;
				Recorder.record = false;
				path = Utils.getDirectory("Choose_a_directory", path);
				Recorder.record = recording;
				if (path != null)
					tf.setText(path);
			}
			// Double-click on the result table
			else if (lastImp != null && lastImp.isVisible())
			{
				// An extra line is added at the end of the results so remove this
				final int rank = resultsWindow.getTextPanel().getLineCount() -
						resultsWindow.getTextPanel().getSelectionStart() - 1;

				// Show the result that was double clicked. Results are stored in reverse order.
				if (rank > 0 && rank <= lastResults.size())
					showResult(lastImp, lastMask, lastResults.get(rank - 1).options);
			}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.awt.event.MouseListener#mousePressed(java.awt.event.MouseEvent)
	 */
	@Override
	public void mousePressed(MouseEvent e)
	{
		// Ignore
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.awt.event.MouseListener#mouseReleased(java.awt.event.MouseEvent)
	 */
	@Override
	public void mouseReleased(MouseEvent e)
	{
		// Ignore
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.awt.event.MouseListener#mouseEntered(java.awt.event.MouseEvent)
	 */
	@Override
	public void mouseEntered(MouseEvent e)
	{
		// Ignore
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.awt.event.MouseListener#mouseExited(java.awt.event.MouseEvent)
	 */
	@Override
	public void mouseExited(MouseEvent e)
	{
		// Ignore
	}

	private void showOptimiserWindow()
	{
		if (instance != null)
		{
			showInstance();
			return;
		}

		IJ.showStatus("Initialising FindFoci Optimiser ...");

		String errorMessage = null;
		Throwable exception = null;

		try
		{
			Class.forName("org.jdesktop.beansbinding.Property", false, this.getClass().getClassLoader());

			// it exists on the classpath
			instance = new OptimiserView();
			instance.addWindowListener(this);
			instance.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

			IJ.register(OptimiserView.class);

			showInstance();
			IJ.showStatus("FindFoci Optimiser ready");
		}
		catch (final ExceptionInInitializerError e)
		{
			exception = e;
			errorMessage = "Failed to initialize class: " + e.getMessage();
		}
		catch (final LinkageError e)
		{
			exception = e;
			errorMessage = "Failed to link class: " + e.getMessage();
		}
		catch (final ClassNotFoundException ex)
		{
			exception = ex;
			errorMessage = "Failed to find class: " + ex.getMessage() +
					"\nCheck you have beansbinding-1.2.1.jar on your classpath\n";
		}
		catch (final Throwable ex)
		{
			exception = ex;
			errorMessage = ex.getMessage();
		}
		finally
		{
			if (exception != null)
			{
				final StringWriter sw = new StringWriter();
				final PrintWriter pw = new PrintWriter(sw);
				pw.write(errorMessage);
				pw.append('\n');
				exception.printStackTrace(pw);
				IJ.log(sw.toString());
			}
		}
	}

	private static void showInstance()
	{
		WindowManager.addWindow(instance);
		instance.setVisible(true);
		instance.toFront();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.awt.event.WindowListener#windowOpened(java.awt.event.WindowEvent)
	 */
	@Override
	public void windowOpened(WindowEvent e)
	{
		// Ignore
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.awt.event.WindowListener#windowClosing(java.awt.event.WindowEvent)
	 */
	@Override
	public void windowClosing(WindowEvent e)
	{
		WindowManager.removeWindow(instance);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.awt.event.WindowListener#windowClosed(java.awt.event.WindowEvent)
	 */
	@Override
	public void windowClosed(WindowEvent e)
	{
		// Ignore
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.awt.event.WindowListener#windowIconified(java.awt.event.WindowEvent)
	 */
	@Override
	public void windowIconified(WindowEvent e)
	{
		// Ignore
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.awt.event.WindowListener#windowDeiconified(java.awt.event.WindowEvent)
	 */
	@Override
	public void windowDeiconified(WindowEvent e)
	{
		// Ignore
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.awt.event.WindowListener#windowActivated(java.awt.event.WindowEvent)
	 */
	@Override
	public void windowActivated(WindowEvent e)
	{
		// Ignore
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.awt.event.WindowListener#windowDeactivated(java.awt.event.WindowEvent)
	 */
	@Override
	public void windowDeactivated(WindowEvent e)
	{
		// Ignore
	}

	// ---------------------------------------------------------------------------
	// Start preset values.
	// The following code is for setting the dialog fields with preset values
	// ---------------------------------------------------------------------------
	private class DialogSettings
	{
		String name;
		ArrayList<String> text = new ArrayList<>();
		ArrayList<Boolean> option = new ArrayList<>();

		public DialogSettings(String name)
		{
			this.name = name;
		}
	}

	private String[] SETTINGS;
	private ArrayList<DialogSettings> settings;

	private boolean updating = false;
	private long lastTime = 0;
	private boolean custom = true;

	// Store the preset values for the Text fields, Choices, Numeric field.
	// Preceed with a '-' character if the field is for single mode only.
	//@formatter:off
	private final String[][] textPreset = new String[][] { { "Testing", // preset name
			// Text fields
			"3", // Background_parameter
			AutoThreshold.Method.OTSU.name, // Auto_threshold
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
	}, { "Default", // preset name
			// Text fields
			"2.5, 3.5, 0.5", // Background_parameter
			AutoThreshold.Method.OTSU.name, // Auto_threshold
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
	}, { "Benchmark", // preset name
			// Text fields
			"0, 4.7, 0.667", // Background_parameter
			AutoThreshold.Method.OTSU.name + ", "+AutoThreshold.Method.RENYI_ENTROPY.name+
			", "+AutoThreshold.Method.TRIANGLE.name, // Auto_threshold
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
	private final int FLAG_FALSE = 0;
	private final int FLAG_TRUE = 1;
	private final int FLAG_SINGLE = 2;
	private final int[][] optionPreset = new int[][] { { FLAG_FALSE, // Background_SD_above_mean
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

	private void createSettings()
	{
		settings = new ArrayList<>();

		settings.add(new DialogSettings("Custom"));
		for (int i = 0; i < textPreset.length; i++)
		{
			// First field is the name
			final DialogSettings s = new DialogSettings(textPreset[i][0]);
			// We only need the rest of the settings if there is a dialog
			if (!java.awt.GraphicsEnvironment.isHeadless())
			{
				for (int j = 1; j < textPreset[i].length; j++)
				{
					if (textPreset[i][j].startsWith("-"))
					{
						if (multiMode)
							continue;
						textPreset[i][j] = textPreset[i][j].substring(1);
					}
					s.text.add(textPreset[i][j]);
				}
				for (int j = 0; j < optionPreset[i].length; j++)
				{
					if (multiMode && (optionPreset[i][j] & FLAG_SINGLE) != 0)
						continue;
					s.option.add((optionPreset[i][j] & FLAG_TRUE) != 0);
				}
			}
			settings.add(s);
		}

		SETTINGS = new String[settings.size()];
		for (int i = 0; i < settings.size(); i++)
			SETTINGS[i] = settings.get(i).name;
	}

	/**
	 * Add our own custom listeners to the dialog. If we use dialogListerner in the GenericDialog then it turns the
	 * macro recorder off before we read the fields.
	 *
	 * @param gd
	 *            the dialog
	 */
	private void addListeners(GenericDialog gd)
	{
		listenerGd = gd;
		final Vector<TextField> fields = gd.getStringFields();
		// Optionally Ignore final text field (it is the result file field)
		final int stringFields = fields.size() - ((multiMode) ? 0 : 1);
		for (int i = 0; i < stringFields; i++)
			fields.get(i).addTextListener(this);
		for (final Choice field : (Vector<Choice>) gd.getChoices())
			field.addItemListener(this);
		for (final TextField field : (Vector<TextField>) gd.getNumericFields())
			field.addTextListener(this);
		for (final Checkbox field : (Vector<Checkbox>) gd.getCheckboxes())
			field.addItemListener(this);
	}

	private void saveCustomSettings(GenericDialog gd)
	{
		final DialogSettings s = settings.get(0);
		s.text.clear();
		s.option.clear();
		final Vector<TextField> fields = gd.getStringFields();
		// Optionally Ignore final text field (it is the result file field)
		final int stringFields = fields.size() - ((multiMode) ? 0 : 1);
		for (int i = 0; i < stringFields; i++)
			s.text.add(fields.get(i).getText());
		// The first choice is the settings name which we ignore
		final Vector<Choice> cfields = gd.getChoices();
		for (int i = 1; i < cfields.size(); i++)
			s.text.add(cfields.get(i).getSelectedItem());
		for (final TextField field : (Vector<TextField>) gd.getNumericFields())
			s.text.add(field.getText());
		for (final Checkbox field : (Vector<Checkbox>) gd.getCheckboxes())
			s.option.add(field.getState());
	}

	private void applySettings(GenericDialog gd, DialogSettings s)
	{
		//System.out.println("Applying " + s.name + " " + updating);
		lastTime = System.currentTimeMillis();
		int index = 0, index2 = 0;
		final Vector<TextField> fields = gd.getStringFields();
		// Optionally Ignore final text field (it is the result file field)
		final int stringFields = fields.size() - ((multiMode) ? 0 : 1);
		for (int i = 0; i < stringFields; i++)
			fields.get(i).setText(s.text.get(index++));
		// The first choice is the settings name
		final Vector<Choice> cfields = gd.getChoices();
		cfields.get(0).select(s.name);
		for (int i = 1; i < cfields.size(); i++)
			cfields.get(i).select(s.text.get(index++));
		for (final TextField field : (Vector<TextField>) gd.getNumericFields())
			field.setText(s.text.get(index++));
		for (final Checkbox field : (Vector<Checkbox>) gd.getCheckboxes())
			field.setState(s.option.get(index2++));
		//System.out.println("Done Applying " + s.name + " " + updating);
	}

	/**
	 * Action performed.
	 *
	 * @param e
	 *            the e
	 */
	public void actionPerformed(ActionEvent e)
	{
		dialogItemChanged(listenerGd, e);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.awt.event.TextListener#textValueChanged(java.awt.event.TextEvent)
	 */
	@Override
	public void textValueChanged(TextEvent e)
	{
		dialogItemChanged(listenerGd, e);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.awt.event.ItemListener#itemStateChanged(java.awt.event.ItemEvent)
	 */
	@Override
	public void itemStateChanged(ItemEvent e)
	{
		dialogItemChanged(listenerGd, e);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see ij.gui.DialogListener#dialogItemChanged(ij.gui.GenericDialog, java.awt.AWTEvent)
	 */
	@Override
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e)
	{
		if (e == null || e.getSource() == null || !aquireLock())
			return true;

		//System.out.println("changed " + e.getSource());

		// Check if this is the settings checkbox
		if (e.getSource() == choice.get(0))
		{
			final Choice thisChoice = (Choice) choice.get(0);

			// If the choice is currently custom save the current values so they can be restored
			if (custom)
				saveCustomSettings(gd);

			// Update the other fields with preset values
			final int index = thisChoice.getSelectedIndex();
			if (index != 0)
				custom = false;
			applySettings(gd, settings.get(index));
		}
		else // This is a change to another field. Note that the dialogItemChanged method is called
		// for each field modified in applySettings. This appears to happen after the applySettings
		// method has ended (as if the dialogItemChanged events are in a queue or are delayed until
		// the previous call to dialogItemChanged has ended).
		// To prevent processing these events ignore anything that happens within x milliseconds
		// of the call to applySettings
		if (System.currentTimeMillis() - lastTime > 300)
		{
			// A change to any other field makes this a custom setting
			// => Set the settings drop-down to custom
			final Choice thisChoice = (Choice) choice.get(0);
			if (thisChoice.getSelectedIndex() != 0)
			{
				custom = true;
				thisChoice.select(0);
			}

			// Esnure that checkboxes 1 & 2 are complementary
			if (e.getSource() instanceof Checkbox)
			{
				final Checkbox cb = (Checkbox) e.getSource();
				// If just checked then we must uncheck the complementing checkbox
				if (cb.getState())
					// Only checkbox 1 & 2 are complementary
					if (cb.equals(checkbox.get(0)))
						((Checkbox) checkbox.get(1)).setState(false);
					else if (cb.equals(checkbox.get(1)))
						((Checkbox) checkbox.get(0)).setState(false);
			}
		}

		updating = false;
		return true;
	}

	private synchronized boolean aquireLock()
	{
		if (updating)
			return false;
		updating = true;
		return true;
	}
	// ---------------------------------------------------------------------------
	// End preset values
	// ---------------------------------------------------------------------------
}
