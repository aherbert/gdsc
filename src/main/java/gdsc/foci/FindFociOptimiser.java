package gdsc.foci;

/*----------------------------------------------------------------------------- 
 * GDSC Plugins for ImageJ
 * 
 * Copyright (C) 2011 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

import gdsc.foci.gui.OptimiserView;
import gdsc.utils.ImageJHelper;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.gui.YesNoCancelDialog;
import ij.io.FileInfo;
import ij.plugin.PlugIn;
import ij.plugin.frame.Recorder;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
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

import javax.swing.JFrame;

/**
 * Runs the FindFoci plugin with various settings and compares the results to the reference image point ROI.
 */
public class FindFociOptimiser implements PlugIn, MouseListener, WindowListener, DialogListener, TextListener,
		ItemListener
{
	private static OptimiserView instance;

	private static String FRAME_TITLE = "FindFoci Optimiser";
	private static TextWindow resultsWindow = null;
	private static int STEP_LIMIT = 10000;
	private static final int RESULT_PRECISION = 4;

	private static String myMaskImage = "";
	private static boolean myBackgroundStdDevAboveMean = true;
	private static boolean myBackgroundAuto = true;
	private static boolean myBackgroundAbsolute = false;
	private static String myBackgroundParameter = "2.5, 3.5, 0.5";
	private static String myThresholdMethod = "Otsu";
	private static String myStatisticsMode = "Both";
	private static boolean mySearchAboveBackground = true;
	private static boolean mySearchFractionOfPeak = true;
	private static String mySearchParameter = "0, 0.6, 0.2";
	private static String myMinSizeParameter = "1, 9, 2";
	private static String[] saddleOptions = { "Yes", "No", "Both" };
	private static int myMinimumAboveSaddle = 0;
	private static int myPeakMethod = FindFoci.PEAK_RELATIVE_ABOVE_BACKGROUND;
	private static String myPeakParameter = "0, 0.6, 0.2";
	private static String mySortMethod = "" + FindFoci.SORT_INTENSITY;
	private static int myMaxPeaks = 500;
	private static String myGaussianBlur = "0, 0.5, 1";
	private static String myCentreMethod = "" + FindFoci.CENTRE_MAX_VALUE_SEARCH;
	private static String myCentreParameter = "2";

	/**
	 * The list of recognised methods for sorting the results
	 */
	public final static String[] sortMethods = { "None", "Precision", "Recall", "F0.5", "F1", "F2", "F-beta", "Jaccard" };

	/**
	 * Do not sort the results
	 */
	public final static int SORT_NONE = 0;
	/**
	 * Sort the results using the Precision
	 */
	public final static int SORT_PRECISION = 1;
	/**
	 * Sort the results using the Recall
	 */
	public final static int SORT_RECALL = 2;
	/**
	 * Sort the results using the F0.5 score (weights precision over recall)
	 */
	public final static int SORT_F05 = 3;
	/**
	 * Sort the results using the F1 score (precision and recall equally weighted)
	 */
	public final static int SORT_F1 = 4;
	/**
	 * Sort the results using the F2 score (weights recall over precision)
	 */
	public final static int SORT_F2 = 5;
	/**
	 * Sort the results using the F-beta score
	 */
	public final static int SORT_FBETA = 6;
	/**
	 * Sort the results using the Jaccard
	 */
	public final static int SORT_JACCARD = 7;
	/**
	 * Sort the results using the rank
	 */
	@SuppressWarnings("unused")
	private final static int SORT_RANK = 8;
	/**
	 * Sort the results using the score. This field is used by the multiple image optimser.
	 */
	private final static int SORT_SCORE = 9;

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
	private static String OUTPUT_DIRECTORY = "findfoci.optimiser.outputDirectory";
	private static String SCORING_MODE = "findfoci.optimiser.scoringMode";
	private static String REUSE_RESULTS = "findfoci.optimiser.reuseResults";
	private static String inputDirectory = Prefs.get(INPUT_DIRECTORY, "");
	private static String outputDirectory = Prefs.get(OUTPUT_DIRECTORY, "");
	private static String[] SCORING_MODES = new String[] { "Raw score metric", "Relative (% drop from top)", "Z-score",
			"Rank" };
	private static final int SCORE_RAW = 0;
	private static final int SCORE_RELATIVE = 1;
	private static final int SCORE_Z = 2;
	private static final int SCORE_RANK = 3;
	private static int scoringMode = Prefs.getInt(SCORING_MODE, SCORE_RANK);
	private static boolean reuseResults = Prefs.getBoolean(REUSE_RESULTS, true);

	@SuppressWarnings("rawtypes")
	private Vector checkbox, choice;
	private GenericDialog listenerGd;

	// Stored to allow the display of any of the latest results from the result table
	private ImagePlus lastImp, lastMask;
	private ArrayList<Result> lastResults;
	private String optimiserCommandOptions;

	// The number of combinations
	private int combinations;

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	public void run(String arg)
	{
		if (arg.equals("frame"))
		{
			showOptimiserWindow();
		}
		else
		{
			ImagePlus imp = (arg.equals("multi")) ? null : WindowManager.getCurrentImage();
			run(imp);
		}
	}

	/**
	 * Run the optimiser on the given image. If the image is null then process in multi-image mode.
	 * 
	 * @param imp
	 */
	public void run(ImagePlus imp)
	{
		// TODO - Add 'true' support for 3D images. Currently it just uses XY distance and ignores Z. 
		// (not sure how to do this since the ROI seems to be associated with one slice. 
		// It may necessitate using all the ROIs in the ROI manager, one for each slice.)  

		if (!showDialog(imp))
			return;

		IJ.log("---\n" + FRAME_TITLE);
		IJ.log(combinations + " combinations");

		if (multiMode)
		{
			// Get the list of images
			String[] imageList = FindFoci.getBatchImages(inputDirectory);
			if (imageList == null || imageList.length == 0)
			{
				IJ.error(FRAME_TITLE, "No input images in folder: " + inputDirectory);
				return;
			}

			if (reuseResults && resultsExist(imageList))
				IJ.log("Output directory contains existing results that will be re-used if they have the correct number of combinations");

			IJ.showStatus("Running optimiser ...");

			// For each image start an optimiser run:
			// - Run the optimiser
			// - save the results to the output directory
			int size = imageList.length;
			ExecutorService threadPool = Executors.newFixedThreadPool(Prefs.getThreads());
			List<Future<?>> futures = new ArrayList<Future<?>>(size);
			ArrayList<OptimisationWorker> workers = new ArrayList<FindFociOptimiser.OptimisationWorker>(size);

			// Allow progress to be tracked across all threads
			Counter counter = new SynchronizedCounter(combinations * size);
			for (String image : imageList)
			{
				OptimisationWorker w = new OptimisationWorker(image, counter);
				workers.add(w);
				futures.add(threadPool.submit(w));
			}

			// Collect all the results
			ImageJHelper.waitForCompletion(futures);
			threadPool.shutdown();
			IJ.showProgress(1);
			IJ.showStatus("");

			// Check all results are the same size
			ArrayList<ArrayList<Result>> allResults = new ArrayList<ArrayList<Result>>(size);
			for (OptimisationWorker w : workers)
			{
				if (w.results == null)
					continue;
				if (!allResults.isEmpty() && w.results.size() != allResults.get(0).size())
				{
					IJ.error(FRAME_TITLE, "Some optimisation runs produced a different number of results");
					return;
				}
				allResults.add(w.results);
			}
			if (allResults.isEmpty())
			{
				IJ.error(FRAME_TITLE, "No optimisation runs produced results");
				return;
			}

			IJ.showStatus("Calculating scores ...");

			// Combine using the chosen ranking score.
			// Use the first set of results to accumulate scores.
			ArrayList<Result> results = allResults.get(0);
			getScore(results);
			size = allResults.size();
			for (int i = 1; i < size; i++)
			{
				ArrayList<Result> results2 = allResults.get(i);
				getScore(results2);
				for (int j = 0; j < results.size(); j++)
				{
					// Combine all the metrics
					Result r1 = results.get(j);
					Result r2 = results2.get(j);
					r1.add(r2);
				}
			}
			// Average the scores
			final double factor = 1.0 / size;
			for (int j = 0; j < results.size(); j++)
			{
				double[] metrics = results.get(j).metrics;
				for (int i = 0; i < metrics.length; i++)
					metrics[i] *= factor;
			}
			// Only when we score using rank do we want to have the lowest first
			sortResults(results, SORT_SCORE, (scoringMode != SCORE_RANK));

			// Output the combined results
			saveResults(null, null, results, null, outputDirectory + File.separator + "all");

			// Show in a table
			showResults(null, null, results);

			IJ.showStatus("");
		}
		else
		{
			ImagePlus mask = WindowManager.getImage(myMaskImage);

			long start = System.currentTimeMillis();

			ArrayList<Result> results = runOptimiser(imp, mask, new StandardCounter(combinations));

			if (results == null)
			{
				IJ.error(FRAME_TITLE, "No ROI points fall inside the mask image");
				return;
			}

			long runTime = System.currentTimeMillis() - start;
			double seconds = runTime / 1000.0;
			IJ.log(String.format("Optimisation time = %.3f sec (%.3f ms / combination)", seconds, (double) runTime /
					combinations));

			showResults(imp, mask, results);

			// Re-run Find_Peaks and output the best result
			if (!results.isEmpty())
			{
				IJ.log("Top result = " + IJ.d2s(results.get(0).metrics[myResultsSortMethod - 1], 4));

				Options bestOptions = results.get(0).options;

				AssignedPoint[] predictedPoints = showResult(imp, mask, bestOptions);

				saveResults(imp, mask, results, predictedPoints, myResultFile);

				checkOptimisationSpace(results, imp);
			}
			IJ.showTime(imp, start, "Done ");
		}
	}

	/**
	 * Check if the output directory has any results already
	 * 
	 * @param imageList
	 * @return true if results exist
	 */
	private boolean resultsExist(String[] imageList)
	{
		// List the results in the output directory
		String[] results = new File(outputDirectory).list(new FilenameFilter()
		{
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
			for (String result : results)
				if (result.startsWith(name))
					return true;
		}
		return false;
	}

	/**
	 * Copied from ImagePlus.getShortTitle()
	 * 
	 * @param title
	 * @return the title with no spaces or extension
	 */
	private String getShortTitle(String title)
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
	 * Check if the optimal results was obtained at the edge of the optimisation search space
	 * 
	 * @param results
	 * @param imp
	 */
	private void checkOptimisationSpace(ArrayList<Result> results, ImagePlus imp)
	{
		Options bestOptions = results.get(0).options;
		if (bestOptions == null)
			return;

		// Check if a sub-optimal best result was obtained at the limit of the optimisation range
		if (results.get(0).metrics[Result.F1] < 1.0)
		{
			StringBuilder sb = new StringBuilder();
			if (backgroundMethodHasParameter(bestOptions.backgroundMethod))
			{
				if (bestOptions.backgroundParameter == myBackgroundParameterMin)
					append(sb, "- Background parameter @ lower limit (%g)\n", bestOptions.backgroundParameter);
				else if (bestOptions.backgroundParameter + myBackgroundParameterInterval > myBackgroundParameterMax)
					append(sb, "- Background parameter @ upper limit (%g)\n", bestOptions.backgroundParameter);
			}
			if (searchMethodHasParameter(bestOptions.searchMethod))
			{
				if (bestOptions.searchParameter == mySearchParameterMin && mySearchParameterMin > 0)
					append(sb, "- Search parameter @ lower limit (%g)\n", bestOptions.searchParameter);
				else if (bestOptions.searchParameter + mySearchParameterInterval > mySearchParameterMax &&
						mySearchParameterMax < 1)
					append(sb, "- Search parameter @ upper limit (%g)\n", bestOptions.searchParameter);
			}
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

			if (bestOptions.maxPeaks == results.get(0).n)
				append(sb, "- Total peaks == Maximum Peaks (%d)\n", bestOptions.maxPeaks);

			if (sb.length() > 0)
			{
				sb.insert(0, "Optimal result (" + IJ.d2s(results.get(0).metrics[myResultsSortMethod - 1], 4) +
						") for " + imp.getShortTitle() + " obtained at the following limits:\n");
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
	 * Gets the score for each item in the results set and sets it to the score field. The score is determined using the
	 * configured resultsSortMethod and scoringMode. It is assumed that all the scoring metrics start at zero and higher
	 * is better.
	 * 
	 * @param results
	 */
	private void getScore(ArrayList<Result> results)
	{
		// Extract the score from the results
		final int scoreIndex;
		switch (scoringMode)
		{
			case SCORE_RAW:
			case SCORE_Z:
			case SCORE_RELATIVE:
				scoreIndex = myResultsSortMethod - 1;
				break;

			case SCORE_RANK:
			default:
				scoreIndex = Result.RANK;
		}

		double[] score = new double[results.size()];
		for (int i = 0; i < score.length; i++)
			score[i] = results.get(i).metrics[scoreIndex];

		// Perform additional score adjustment
		if (scoringMode == SCORE_Z)
		{
			// Use the z-score
			double[] stats = getStatistics(score);
			final double av = stats[0];
			final double sd = stats[1];
			if (sd > 0)
			{
				final double factor = 1.0 / sd;
				for (int i = 0; i < score.length; i++)
					score[i] = (score[i] - av) * factor;
			}
			else
			{
				score = new double[score.length]; // all have z=0
			}
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
	 * Get the statistics
	 * 
	 * @param score
	 * @return The average and standard deviation
	 */
	private double[] getStatistics(double[] score)
	{
		// Get the average
		double sum = 0.0;
		double sum2 = 0.0;
		int n = score.length;
		for (double value : score)
		{
			sum += value;
			sum2 += (value * value);
		}
		double av = sum / n;

		// Get the Std.Dev
		double stdDev;
		if (n > 0)
		{
			double d = n;
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
	 * @return The top score
	 */
	private double getTop(double[] score)
	{
		double top = score[0];
		for (int i = 1; i < score.length; i++)
			if (top < score[i])
				top = score[i];
		return top;
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
	private ArrayList<Result> runOptimiser(ImagePlus imp, ImagePlus mask, Counter counter)
	{
		Roi roi = checkImage(imp);
		if (roi == null)
			return null;
		AssignedPoint[] roiPoints = extractRoiPoints(roi, imp, mask);

		if (roiPoints.length == 0)
		{
			// TODO - Check if the same directory has a file named [image_title].csv and load points from that
			return null;
		}

		ArrayList<Result> results = new ArrayList<Result>(combinations);

		// Set the threshold for assigning points matches as a fraction of the image size
		double dThreshold = getDistanceThreshold(imp);

		FindFoci fp = new FindFoci();
		int id = 0;
		for (int blurCount = 0; blurCount < blurArray.length; blurCount++)
		{
			double blur = blurArray[blurCount];
			ImagePlus imp2 = FindFoci.applyBlur(imp, blur);

			// Iterate over the options
			int thresholdMethodIndex = 0;
			for (int b = 0; b < backgroundMethodArray.length; b++)
			{
				String thresholdMethod = (backgroundMethodArray[b] == FindFoci.BACKGROUND_AUTO_THRESHOLD) ? thresholdMethods[thresholdMethodIndex++]
						: "";

				String[] myStatsModes = backgroundMethodHasStatisticsMode(backgroundMethodArray[b]) ? statisticsModes
						: new String[] { "Both" };
				for (String statsMode : myStatsModes)
				{
					long methodStart = System.nanoTime();
					int statisticsMode = convertStatisticsMode(statsMode);
					Object[] initArray = fp.findMaximaInit(imp, imp2, mask, backgroundMethodArray[b], thresholdMethod,
							statisticsMode);
					Object[] clonedInitArray = null;
					long findMaximaInitTime = System.nanoTime() - methodStart;

					for (double backgroundParameter = myBackgroundParameterMinArray[b]; backgroundParameter <= myBackgroundParameterMax; backgroundParameter += myBackgroundParameterInterval)
					{
						boolean logBackground = (blurCount == 0) && !multiMode; // Log on first blur iteration
						// Use zero when there is no parameter
						final double thisBackgroundParameter = (backgroundMethodHasParameter(backgroundMethodArray[b])) ? backgroundParameter
								: 0;

						for (int minSize = myMinSizeMin; minSize <= myMinSizeMax; minSize += myMinSizeInterval)
							for (int s = 0; s < searchMethodArray.length; s++)
								for (double searchParameter = mySearchParameterMinArray[s]; searchParameter <= mySearchParameterMax; searchParameter += mySearchParameterInterval)
								{
									// Use zero when there is no parameter
									double thisSearchParameter = (searchMethodHasParameter(searchMethodArray[s])) ? searchParameter
											: 0;
									for (double peakParameter = myPeakParameterMin; peakParameter <= myPeakParameterMax; peakParameter += myPeakParameterInterval)
										for (int options : optionsArray)
										{
											options += statisticsMode;
											for (int sortMethod : sortMethodArray)
											{
												clonedInitArray = FindFoci.cloneInitArray(initArray, clonedInitArray);
												// System.currentTimeMillis() or System.nanoTime()
												methodStart = System.nanoTime();
												Object[] runArray = fp.findMaximaRun(clonedInitArray,
														backgroundMethodArray[b], backgroundParameter,
														searchMethodArray[s], searchParameter, minSize, myPeakMethod,
														peakParameter, sortMethod, options, blur);

												if (logBackground)
												{
													final double backgroundLevel = ((double[]) clonedInitArray[4])[FindFoci.STATS_BACKGROUND];
													logBackground = false;
													IJ.log(String
															.format("Background level - %s %s: %s = %g",
																	FindFoci.backgroundMethods[backgroundMethodArray[b]],
																	backgroundMethodHasStatisticsMode(backgroundMethodArray[b]) ? "(" +
																			statsMode + ") "
																			: "",
																	((backgroundMethodHasParameter(backgroundMethodArray[b])) ? IJ
																			.d2s(backgroundParameter, 2)
																			: thresholdMethod), backgroundLevel));
												}

												long findMaximaRunTime = findMaximaInitTime + System.nanoTime() -
														methodStart;

												for (int c = 0; c < centreMethodArray.length; c++)
												{
													for (double centreParameter = myCentreParameterMinArray[c]; centreParameter <= myCentreParameterMaxArray[c]; centreParameter += myCentreParameterIntervalArray[c])
													{
														methodStart = System.nanoTime();
														Object[] peakResults = fp.findMaximaResults(clonedInitArray,
																runArray, myMaxPeaks, sortMethod, centreMethodArray[c],
																centreParameter);

														long time = findMaximaRunTime + System.nanoTime() - methodStart;

														counter.increment();

														if (peakResults != null)
														{
															// Get the results
															@SuppressWarnings("unchecked")
															ArrayList<int[]> resultsArray = (ArrayList<int[]>) peakResults[1];

															Options runOptions = new Options(blur,
																	backgroundMethodArray[b], thisBackgroundParameter,
																	thresholdMethod, searchMethodArray[s],
																	thisSearchParameter, myMaxPeaks, minSize,
																	myPeakMethod, peakParameter, sortMethod, options,
																	centreMethodArray[c], centreParameter);
															Result result = analyseResults(id, roiPoints, resultsArray,
																	dThreshold, runOptions, time, myBeta);
															results.add(result);
														}
														id++;
													}
												}
											}
										}
								}
					}
				}
			}
		}
		// All possible results sort methods are highest first
		sortResults(results, myResultsSortMethod, true);
		return results;
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
			Result result = results.get(i);
			StringBuilder sb = new StringBuilder();
			sb.append(IJ.d2s(result.metrics[Result.RANK], 0)).append("\t");
			sb.append(result.getParameters());
			sb.append(result.n).append("\t");
			sb.append(result.tp).append("\t");
			sb.append(result.fp).append("\t");
			sb.append(result.fn).append("\t");
			sb.append(IJ.d2s(result.metrics[Result.JACCARD], RESULT_PRECISION)).append("\t");
			sb.append(IJ.d2s(result.metrics[Result.PRECISION], RESULT_PRECISION)).append("\t");
			sb.append(IJ.d2s(result.metrics[Result.RECALL], RESULT_PRECISION)).append("\t");
			sb.append(IJ.d2s(result.metrics[Result.F05], RESULT_PRECISION)).append("\t");
			sb.append(IJ.d2s(result.metrics[Result.F1], RESULT_PRECISION)).append("\t");
			sb.append(IJ.d2s(result.metrics[Result.F2], RESULT_PRECISION)).append("\t");
			sb.append(IJ.d2s(result.metrics[Result.Fb], RESULT_PRECISION)).append("\t");
			sb.append(IJ.d2s(result.metrics[Result.SCORE], RESULT_PRECISION)).append("\t");
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
	 * @param mask
	 * @param results
	 * @param predictedPoints
	 *            can be null
	 * @param resultFile
	 */
	private void saveResults(ImagePlus imp, ImagePlus mask, ArrayList<Result> results, AssignedPoint[] predictedPoints,
			String resultFile)
	{
		if (resultFile == null)
			return;

		Options bestOptions = results.get(0).options;

		OutputStreamWriter out = createResultsFile(bestOptions, imp, mask, resultFile);
		if (out == null)
			return;

		try
		{
			out.write("#\n# Results\n# " + createResultsHeader(true, false));

			// Output all results in ascending rank order
			for (int i = 0; i < results.size(); i++)
			{
				Result result = results.get(i);
				StringBuilder sb = new StringBuilder();
				sb.append(IJ.d2s(result.metrics[Result.RANK], 0)).append("\t");
				sb.append(result.getParameters());
				sb.append(result.n).append("\t");
				sb.append(result.tp).append("\t");
				sb.append(result.fp).append("\t");
				sb.append(result.fn).append("\t");
				sb.append(IJ.d2s(result.metrics[Result.JACCARD], RESULT_PRECISION)).append("\t");
				sb.append(IJ.d2s(result.metrics[Result.PRECISION], RESULT_PRECISION)).append("\t");
				sb.append(IJ.d2s(result.metrics[Result.RECALL], RESULT_PRECISION)).append("\t");
				sb.append(IJ.d2s(result.metrics[Result.F05], RESULT_PRECISION)).append("\t");
				sb.append(IJ.d2s(result.metrics[Result.F1], RESULT_PRECISION)).append("\t");
				sb.append(IJ.d2s(result.metrics[Result.F2], RESULT_PRECISION)).append("\t");
				sb.append(IJ.d2s(result.metrics[Result.Fb], RESULT_PRECISION)).append("\t");
				sb.append(IJ.d2s(result.metrics[Result.SCORE], RESULT_PRECISION)).append("\t");
				sb.append(result.time).append("\n");
				out.write(sb.toString());
			}

			// Save the identified points
			if (predictedPoints != null)
				PointManager.savePoints(predictedPoints, resultFile + ".points.csv");
		}
		catch (IOException e)
		{
			IJ.log("Failed to write to the output file '" + resultFile + ".points.csv': " + e.getMessage());
		}
		finally
		{
			try
			{
				out.close();
			}
			catch (IOException e)
			{
			}
		}

	}

	private synchronized void addFindFociCommand(OutputStreamWriter out, Options bestOptions, String maskTitle)
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
		if ((bestOptions.options & FindFoci.OPTION_MINIMUM_ABOVE_SADDLE) != 0)
			Recorder.recordOption("Minimum_above_saddle");
		Recorder.recordOption("Minimum_peak_height", FindFoci.peakMethods[bestOptions.peakMethod]);
		Recorder.recordOption("Peak_parameter", "" + bestOptions.peakParameter);
		Recorder.recordOption("Sort_method", FindFoci.sortIndexMethods[bestOptions.sortIndex]);
		Recorder.recordOption("Maximum_peaks", "" + bestOptions.maxPeaks);
		Recorder.recordOption("Show_mask");
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

	private double getDistanceThreshold(ImagePlus imp)
	{
		if (myMatchSearchMethod == 1)
			return myMatchSearchDistance;
		final int length = (imp.getWidth() < imp.getHeight()) ? imp.getWidth() : imp.getHeight();
		return Math.ceil(myMatchSearchDistance * length);
	}

	private void append(StringBuilder sb, String format, Object... args)
	{
		sb.append(String.format(format, args));
	}

	private AssignedPoint[] showResult(ImagePlus imp, ImagePlus mask, Options options)
	{
		if (imp == null)
			return null;

		int outputType = FindFoci.OUTPUT_MASK_PEAKS | FindFoci.OUTPUT_MASK_ABOVE_SADDLE |
				FindFoci.OUTPUT_MASK_ROI_SELECTION | FindFoci.OUTPUT_ROI_SELECTION | FindFoci.OUTPUT_LOG_MESSAGES;
		// Clone the input image to allow display of the peaks on the original
		ImagePlus clone = cloneImage(imp, imp.getTitle() + " clone");
		clone.show();

		FindFoci fp = new FindFoci();
		fp.exec(clone, mask, options.backgroundMethod, options.backgroundParameter, options.autoThresholdMethod,
				options.searchMethod, options.searchParameter, options.maxPeaks, options.minSize, options.peakMethod,
				options.peakParameter, outputType, options.sortIndex, options.options, options.blur,
				options.centreMethod, options.centreParameter, 1);
		AssignedPoint[] predictedPoints = PointManager.extractRoiPoints(clone.getRoi());
		maskImage(clone, mask);

		if (myShowScoreImages)
		{
			AssignedPoint[] actualPoints = PointManager.extractRoiPoints(imp.getRoi());

			List<Coordinate> TP = new LinkedList<Coordinate>();
			List<Coordinate> FP = new LinkedList<Coordinate>();
			List<Coordinate> FN = new LinkedList<Coordinate>();
			MatchCalculator.analyseResults2D(actualPoints, predictedPoints, getDistanceThreshold(imp), TP, FP, FN);

			// Show image with TP, FP and FN
			ImagePlus tpImp = cloneImage(imp, mask, imp.getTitle() + " TP");
			tpImp.setRoi(createRoi(TP));
			tpImp.show();

			ImagePlus fpImp = cloneImage(imp, mask, imp.getTitle() + " FP");
			fpImp.setRoi(createRoi(FP));
			fpImp.show();

			ImagePlus fnImp = cloneImage(imp, mask, imp.getTitle() + " FN");
			fnImp.setRoi(createRoi(FN));
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

	private void closeImage(String title)
	{
		ImagePlus imp = WindowManager.getImage(title);
		if (imp != null)
		{
			imp.close();
		}
	}

	private ImagePlus cloneImage(ImagePlus imp, String cloneTitle)
	{
		ImagePlus clone = WindowManager.getImage(cloneTitle);
		if (clone == null)
		{
			clone = imp.duplicate();
			clone.setTitle(cloneTitle);
		}
		else
		{
			ImageStack s1 = imp.getImageStack();
			ImageStack s2 = clone.getImageStack();
			for (int n = 1; n <= s1.getSize(); n++)
			{
				s2.setPixels(s1.getProcessor(n).duplicate().getPixels(), n);
			}
			clone.setStack(s2);
		}
		clone.setOverlay(null);
		return clone;
	}

	private ImagePlus cloneImage(ImagePlus imp, ImagePlus mask, String cloneTitle)
	{
		ImagePlus clone = WindowManager.getImage(cloneTitle);
		Integer maskId = (mask != null) ? new Integer(mask.getID()) : 0;
		if (clone == null || !clone.getProperty("MASK").equals(maskId))
		{
			if (clone != null)
			{
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

	private void maskImage(ImagePlus clone, ImagePlus mask)
	{
		if (validMask(clone, mask))
		{
			ImageStack cloneStack = clone.getImageStack();
			ImageStack maskStack = mask.getImageStack();
			boolean reloadMask = cloneStack.getSize() == maskStack.getSize();
			for (int slice = 1; slice <= cloneStack.getSize(); slice++)
			{
				ImageProcessor ipClone = cloneStack.getProcessor(slice);
				ImageProcessor ipMask = maskStack.getProcessor(reloadMask ? slice : 1);

				for (int i = ipClone.getPixelCount(); i-- > 0;)
				{
					if (ipMask.get(i) == 0)
					{
						ipClone.set(i, 0);
					}
				}
			}
			clone.updateAndDraw();
		}
	}

	private int[] createOptionsArray()
	{
		if (myMinimumAboveSaddle == 0)
			return new int[] { FindFoci.OPTION_MINIMUM_ABOVE_SADDLE };
		else if (myMinimumAboveSaddle == 1)
			return new int[] { 0 };
		else
			return new int[] { FindFoci.OPTION_MINIMUM_ABOVE_SADDLE, 0 };
	}

	private Roi checkImage(ImagePlus imp)
	{
		if (null == imp)
		{
			IJ.noImage();
			return null;
		}

		if (imp.getBitDepth() != 8 && imp.getBitDepth() != 16)
		{
			IJ.showMessage("Error", "Only 8-bit and 16-bit images are supported");
			return null;
		}

		if (imp.getNChannels() != 1 || imp.getNFrames() != 1)
		{
			IJ.showMessage("Error", "Only single channel, single frame images are supported");
			return null;
		}

		Roi roi = imp.getRoi();
		if (roi == null || roi.getType() != Roi.POINT)
		{
			IJ.showMessage("Error", "Image must have a point ROI");
			return null;
		}
		return roi;
	}

	private boolean showDialog(ImagePlus imp)
	{
		// Ensure the Dialog options are recorded. These are used later to write to file.
		boolean recorderOn = Recorder.record;
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
		GenericDialog gd = new GenericDialog(FRAME_TITLE);

		ArrayList<String> newImageList = FindFoci.buildMaskList(imp);

		// Column 1
		gd.addMessage("Runs the FindFoci algorithm using different parameters.\n"
				+ "Results are compared to reference ROI points.\n\n"
				+ "Input range fields accept 3 values: min,max,interval\n"
				+ "Gaussian blur accepts comma-delimited values for the blur.");

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
		gd.addStringField("Minimum_size", myMinSizeParameter, 12);
		gd.addChoice("Minimum_above_saddle", saddleOptions, saddleOptions[myMinimumAboveSaddle]);
		gd.addChoice("Minimum_peak_height", FindFoci.peakMethods, FindFoci.peakMethods[myPeakMethod]);
		gd.addStringField("Peak_parameter", myPeakParameter, 12);

		// Column 2
		gd.addMessage(createSortOptions());
		Component sortOptionsLabel = gd.getMessage(); // Note the component that will start column 2

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
			gd.addStringField("Result_file", myResultFile, 35);

			// Add a message about double clicking the result table to show the result
			gd.addMessage("Note: Double-click an entry in the optimiser results table\n"
					+ "to view the FindFoci output. This only works for the most recent\n"
					+ "set of results in the table.");
		}

		gd.addHelp(gdsc.help.URL.FIND_FOCI);

		if (!java.awt.GraphicsEnvironment.isHeadless())
		{
			checkbox = gd.getCheckboxes();
			choice = gd.getChoices();

			saveCustomSettings(gd);

			// Listen for changes
			addListeners(gd);
		}

		// Re-arrange the standard layout which has a GridBagLayout with 2 columns (label,field)
		// to 4 columns: (label,field) x 2

		if (gd.getLayout() != null)
		{
			GridBagLayout grid = (GridBagLayout) gd.getLayout();

			int xOffset = 0, yOffset = 0;
			int lastY = -1, rowCount = 0;
			for (Component comp : gd.getComponents())
			{
				// Check if this should be the second major column
				if (comp == sortOptionsLabel)
				{
					xOffset += 2;
					yOffset -= rowCount;
				}
				// Reposition the field
				GridBagConstraints c = grid.getConstraints(comp);
				if (lastY != c.gridy)
					rowCount++;
				lastY = c.gridy;
				c.gridx = c.gridx + xOffset;
				c.gridy = c.gridy + yOffset;
				c.insets.left = c.insets.left + 10 * xOffset;
				c.insets.top = 0;
				c.insets.bottom = 0;
				grid.setConstraints(comp, c);
			}

			if (IJ.isLinux())
				gd.setBackground(new Color(238, 238, 238));
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
		myMinSizeParameter = gd.getNextString();
		myMinimumAboveSaddle = gd.getNextChoiceIndex();
		myPeakMethod = gd.getNextChoiceIndex();
		myPeakParameter = gd.getNextString();
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

		ImagePlus mask = WindowManager.getImage(myMaskImage);

		if (!validMask(imp, mask))
		{
			statisticsModes = new String[] { "Both" };
		}

		if (myMatchSearchMethod == 1 && myMatchSearchDistance < 1)
		{
			IJ.log("WARNING: Absolute peak match distance is less than 1 pixel: " + myMatchSearchDistance);
		}

		// Count the number of options
		combinations = countSteps();
		if (combinations >= STEP_LIMIT)
		{
			IJ.error("Maximum number of optimisation steps exceeded: " + combinations + " >> " + STEP_LIMIT);
			return false;
		}

		YesNoCancelDialog d = new YesNoCancelDialog(IJ.getInstance(), FRAME_TITLE, combinations +
				" combinations. Do you wish to proceed?");
		if (!d.yesPressed())
			return false;

		return true;
	}

	private boolean showMultiDialog()
	{
		GenericDialog gd = new GenericDialog(FRAME_TITLE);
		gd.addMessage("Run " +
				FRAME_TITLE +
				" on a set of images.\n \nAll images in a directory will be processed.\n \nOptional mask images should be named:\n[image_name].mask.[ext]");
		gd.addStringField("Input_directory", inputDirectory);
		gd.addStringField("Output_directory", outputDirectory);
		gd.addMessage("[Note: Double-click a text field to open a selection dialog]");
		gd.addMessage("The score metric for each parameter combination is computed per image.\nThe scores are converted then averaged across all images.");
		gd.addChoice("Score_conversion", SCORING_MODES, SCORING_MODES[scoringMode]);
		gd.addCheckbox("Re-use_results", reuseResults);
		@SuppressWarnings("unchecked")
		Vector<TextField> texts = (Vector<TextField>) gd.getStringFields();
		for (TextField tf : texts)
		{
			tf.addMouseListener(this);
			tf.setColumns(50);
		}

		gd.addHelp(gdsc.help.URL.FIND_FOCI);
		gd.showDialog();
		if (gd.wasCanceled())
			return false;

		inputDirectory = gd.getNextString();
		if (!new File(inputDirectory).isDirectory())
		{
			IJ.error(FRAME_TITLE, "Input directory is not a valid directory: " + inputDirectory);
			return false;
		}
		outputDirectory = gd.getNextString();
		if (!new File(outputDirectory).isDirectory())
		{
			IJ.error(FRAME_TITLE, "Output directory is not a valid directory: " + outputDirectory);
			return false;
		}
		scoringMode = gd.getNextChoiceIndex();
		reuseResults = gd.getNextBoolean();
		Prefs.set(INPUT_DIRECTORY, inputDirectory);
		Prefs.set(OUTPUT_DIRECTORY, outputDirectory);
		Prefs.set(SCORING_MODE, scoringMode);
		Prefs.set(REUSE_RESULTS, reuseResults);
		return true;
	}

	private String createSortOptions()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Sort options (comma-delimited). Use if total peaks > max peaks:\n");
		addSortOption(sb, FindFoci.SORT_COUNT).append("; ");
		addSortOption(sb, FindFoci.SORT_INTENSITY).append("; ");
		addSortOption(sb, FindFoci.SORT_MAX_VALUE).append("; ");
		addSortOption(sb, FindFoci.SORT_AVERAGE_INTENSITY).append(";\n");
		addSortOption(sb, FindFoci.SORT_INTENSITY_MINUS_BACKGROUND).append("; ");
		addSortOption(sb, FindFoci.SORT_AVERAGE_INTENSITY_MINUS_BACKGROUND).append(";\n");
		addSortOption(sb, FindFoci.SORT_SADDLE_HEIGHT).append("; ");
		addSortOption(sb, FindFoci.SORT_COUNT_ABOVE_SADDLE).append("; ");
		addSortOption(sb, FindFoci.SORT_INTENSITY_ABOVE_SADDLE).append(";\n");
		addSortOption(sb, FindFoci.SORT_ABSOLUTE_HEIGHT);
		return sb.toString();
	}

	private StringBuilder addSortOption(StringBuilder sb, int method)
	{
		sb.append("[").append(method).append("] ").append(FindFoci.sortIndexMethods[method]);
		return sb;
	}

	private String createCentreOptions()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Centre options (comma-delimited):\n");
		for (int method = 0; method < 4; method++)
		{
			addCentreOption(sb, method).append("; ");
			if ((method + 1) % 2 == 0)
				sb.append("\n");
		}
		return sb.toString();
	}

	private StringBuilder addCentreOption(StringBuilder sb, int method)
	{
		sb.append("[").append(method).append("] ").append(FindFoci.getCentreMethods()[method]);
		return sb;
	}

	private void parseThresholdMethods()
	{
		String[] values = myThresholdMethod.split("\\s*;\\s*|\\s*,\\s*|\\s*:\\s*");
		LinkedList<String> methods = new LinkedList<String>();
		for (String method : values)
			validThresholdMethod(method, methods);
		thresholdMethods = methods.toArray(new String[0]);
	}

	private void validThresholdMethod(String method, LinkedList<String> methods)
	{
		for (String m : FindFoci.autoThresholdMethods)
			if (m.equalsIgnoreCase(method))
			{
				methods.add(m);
				return;
			}
	}

	private void parseStatisticsModes()
	{
		String[] values = myStatisticsMode.split("\\s*;\\s*|\\s*,\\s*|\\s*:\\s*");
		LinkedList<String> modes = new LinkedList<String>();
		for (String mode : values)
			validStatisticsMode(mode, modes);
		if (modes.isEmpty())
			modes.add("both");
		statisticsModes = modes.toArray(new String[0]);
	}

	private void validStatisticsMode(String mode, LinkedList<String> modes)
	{
		for (String m : FindFoci.statisticsModes)
			if (m.equalsIgnoreCase(mode))
			{
				modes.add(m);
				return;
			}
	}

	private int convertStatisticsMode(String mode)
	{
		if (mode.equalsIgnoreCase("inside"))
			return FindFoci.OPTION_STATS_INSIDE;
		if (mode.equalsIgnoreCase("outside"))
			return FindFoci.OPTION_STATS_OUTSIDE;
		return 0;
	}

	private int[] createBackgroundArray()
	{
		int[] array = new int[countBackgroundFlags()];
		int i = 0;
		if (myBackgroundAbsolute)
		{
			array[i] = FindFoci.BACKGROUND_ABSOLUTE;
			i++;
		}
		if (myBackgroundAuto)
		{
			for (@SuppressWarnings("unused")
			String method : thresholdMethods)
			{
				array[i] = FindFoci.BACKGROUND_AUTO_THRESHOLD;
				i++;
			}
		}
		if (myBackgroundStdDevAboveMean)
		{
			array[i] = FindFoci.BACKGROUND_STD_DEV_ABOVE_MEAN;
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

		myBackgroundParameterMin = values[0];
		if (values.length == 3)
		{
			myBackgroundParameterMax = values[1];
			myBackgroundParameterInterval = values[2];
		}
		else
		{
			myBackgroundParameterMax = values[0];
			myBackgroundParameterInterval = 1;
		}

		if (myBackgroundParameterInterval < 0)
			myBackgroundParameterInterval = -myBackgroundParameterInterval;

		while (myBackgroundParameterMin < 0)
			myBackgroundParameterMin += myBackgroundParameterInterval;
	}

	private double[] splitValues(String text)
	{
		String[] tokens = text.split(";|,|:");
		ArrayList<Double> list = new ArrayList<Double>(tokens.length);
		for (String token : tokens)
		{
			try
			{
				list.add(Double.parseDouble(token));
			}
			catch (Exception e)
			{
			}
		}

		if (list.size() == 0)
			return new double[] { 0 };

		double[] array = new double[list.size()];
		for (int i = 0; i < array.length; i++)
			array[i] = list.get(i);
		return array;
	}

	private double[] createBackgroundLimits()
	{
		double[] limits = new double[backgroundMethodArray.length];
		for (int i = limits.length; i-- > 0;)
		{
			limits[i] = getBackgroundLimit(backgroundMethodArray[i]);
		}
		return limits;
	}

	private double getBackgroundLimit(int backgroundMethod)
	{
		return backgroundMethodHasParameter(backgroundMethod) ? myBackgroundParameterMin : myBackgroundParameterMax;
	}

	private static boolean backgroundMethodHasStatisticsMode(int backgroundMethod)
	{
		return !(backgroundMethod == FindFoci.BACKGROUND_NONE || backgroundMethod == FindFoci.BACKGROUND_ABSOLUTE);
	}

	private static boolean backgroundMethodHasParameter(int backgroundMethod)
	{
		return !(backgroundMethod == FindFoci.BACKGROUND_NONE || backgroundMethod == FindFoci.BACKGROUND_MEAN || backgroundMethod == FindFoci.BACKGROUND_AUTO_THRESHOLD);
	}

	private int[] createSearchArray()
	{
		int[] array = new int[countSearchFlags()];
		int i = 0;
		if (mySearchAboveBackground)
		{
			array[i] = FindFoci.SEARCH_ABOVE_BACKGROUND;
			i++;
		}
		if (mySearchFractionOfPeak)
		{
			array[i] = FindFoci.SEARCH_FRACTION_OF_PEAK_MINUS_BACKGROUND;
			i++;
		}
		return array;
	}

	private int countSearchFlags()
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

		mySearchParameterMin = values[0];
		if (values.length == 3)
		{
			mySearchParameterMax = values[1];
			mySearchParameterInterval = values[2];
		}
		else
		{
			mySearchParameterMax = values[0];
			mySearchParameterInterval = 1;
		}

		if (mySearchParameterInterval < 0)
			mySearchParameterInterval = -mySearchParameterInterval;

		while (mySearchParameterMin < 0)
			mySearchParameterMin += mySearchParameterInterval;
	}

	private double[] createSearchLimits()
	{
		double[] limits = new double[searchMethodArray.length];
		for (int i = limits.length; i-- > 0;)
		{
			limits[i] = getSearchLimit(searchMethodArray[i]);
		}
		return limits;
	}

	private double getSearchLimit(int searchMethod)
	{
		return searchMethodHasParameter(searchMethod) ? mySearchParameterMin : mySearchParameterMax;
	}

	private static boolean searchMethodHasParameter(int searchMethod)
	{
		return !(searchMethod == FindFoci.SEARCH_ABOVE_BACKGROUND);
	}

	private void parseMinSizeLimits()
	{
		double[] values = splitValues(myMinSizeParameter);

		myMinSizeMin = (int) values[0];
		if (values.length == 3)
		{
			myMinSizeMax = (int) values[1];
			myMinSizeInterval = (int) values[2];
		}
		else
		{
			myMinSizeMax = (int) values[0];
			myMinSizeInterval = 1;
		}

		if (myMinSizeInterval < 1)
			myMinSizeInterval = -myMinSizeInterval;

		// The minimum size should not be zero (this would not be a peak)
		while (myMinSizeMin <= 0)
			myMinSizeMin += myMinSizeInterval;
	}

	private void parsePeakParameterLimits()
	{
		double[] values = splitValues(myPeakParameter);

		myPeakParameterMin = values[0];
		if (values.length == 3)
		{
			myPeakParameterMax = values[1];
			myPeakParameterInterval = values[2];
		}
		else
		{
			myPeakParameterMax = values[0];
			myPeakParameterInterval = 1;
		}

		if (myPeakParameterInterval < 0)
			myPeakParameterInterval = -myPeakParameterInterval;

		while (myPeakParameterMin < 0)
			myPeakParameterMin += myPeakParameterInterval;
	}

	private int[] createSortArray()
	{
		double[] values = splitValues(mySortMethod);
		int[] array = new int[values.length];
		for (int i = 0; i < array.length; i++)
			array[i] = (int) values[i];
		return array;
	}

	private double[] createBlurArray()
	{
		double[] array = splitValues(myGaussianBlur);
		Arrays.sort(array);
		return array;
	}

	private int[] createCentreArray()
	{
		double[] values = splitValues(myCentreMethod);
		if (values.length == 0)
			return new int[] { FindFoci.CENTRE_MAX_VALUE_SEARCH }; // Default 
		int[] array = new int[values.length];
		for (int i = 0; i < array.length; i++)
			array[i] = (int) values[i];
		return array;
	}

	private void parseCentreLimits()
	{
		double[] values = splitValues(myCentreParameter);

		myCentreParameterMin = (int) values[0];
		if (values.length == 3)
		{
			myCentreParameterMax = (int) values[1];
			myCentreParameterInterval = (int) values[2];
		}
		else
		{
			myCentreParameterMax = (int) values[0];
			myCentreParameterInterval = 1;
		}

		if (myCentreParameterInterval < 1)
			myCentreParameterInterval = -myCentreParameterInterval;

		while (myCentreParameterMin < 0)
			myCentreParameterMin += myCentreParameterInterval;
	}

	private int[] createCentreMinLimits()
	{
		int[] limits = new int[centreMethodArray.length];
		for (int i = limits.length; i-- > 0;)
		{
			limits[i] = getCentreMinLimit(centreMethodArray[i]);
		}
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
		int[] limits = new int[centreMethodArray.length];
		for (int i = limits.length; i-- > 0;)
		{
			limits[i] = getCentreMaxLimit(centreMethodArray[i]);
		}
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
		int[] limits = new int[centreMethodArray.length];
		for (int i = limits.length; i-- > 0;)
		{
			limits[i] = getCentreInterval(centreMethodArray[i]);
		}
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
		int stepLimit = STEP_LIMIT;
		int steps = 0;
		for (double blur : blurArray)
			for (int b = 0; b < backgroundMethodArray.length; b++)
			{
				String[] myStatsModes = backgroundMethodHasStatisticsMode(backgroundMethodArray[b]) ? statisticsModes
						: new String[] { "Both" };
				for (String statsMode : myStatsModes)
					for (double backgroundParameter = myBackgroundParameterMinArray[b]; backgroundParameter <= myBackgroundParameterMax; backgroundParameter += myBackgroundParameterInterval)
						for (int minSize = myMinSizeMin; minSize <= myMinSizeMax; minSize += myMinSizeInterval)
							for (int s = 0; s < searchMethodArray.length; s++)
								for (double searchParameter = mySearchParameterMinArray[s]; searchParameter <= mySearchParameterMax; searchParameter += mySearchParameterInterval)
									for (double peakParameter = myPeakParameterMin; peakParameter <= myPeakParameterMax; peakParameter += myPeakParameterInterval)
										for (int options : optionsArray)
											for (int sortMethod : sortMethodArray)
												for (int c = 0; c < centreMethodArray.length; c++)
													for (double centreParameter = myCentreParameterMinArray[c]; centreParameter <= myCentreParameterMaxArray[c]; centreParameter += myCentreParameterIntervalArray[c])
													{
														// Simple check to ensure the user has not configured something incorrectly
														if (steps++ >= stepLimit)
														{
															return steps;
														}
													}
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
			resultsWindow = new TextWindow(FRAME_TITLE + " Results", heading, "", 1000, 300);
			if (resultsWindow.getTextPanel() != null)
			{
				resultsWindow.getTextPanel().addMouseListener(this);
			}
		}
	}

	private OutputStreamWriter createResultsFile(Options bestOptions, ImagePlus imp, ImagePlus mask, String resultFile)
	{
		OutputStreamWriter out = null;
		try
		{
			String filename = resultFile + ".results.xls";

			File file = new File(filename);
			if (!file.exists())
			{
				if (file.getParent() != null)
					new File(file.getParent()).mkdirs();
			}

			// Save results to file
			FileOutputStream fos = new FileOutputStream(filename);
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
		catch (Exception e)
		{
			IJ.log("Failed to create results file '" + resultFile + ".results.xls': " + e.getMessage());
			if (out != null)
			{
				try
				{
					out.close();
				}
				catch (IOException ioe)
				{
				}
			}
		}
		return null;
	}

	private String getFilename(ImagePlus imp)
	{
		FileInfo info = imp.getOriginalFileInfo();
		if (info != null)
		{
			return ImageJHelper.combinePath(info.directory, info.fileName);
		}
		else
		{
			return imp.getTitle();
		}
	}

	private String createResultsHeader(boolean withScore, boolean milliSeconds)
	{
		StringBuilder sb = new StringBuilder();
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
		{
			sb.append("Score\t");
		}
		if (milliSeconds)
			sb.append("mSec\n");
		else
			sb.append("nanoSec\n");
		return sb.toString();
	}

	private Result analyseResults(int id, AssignedPoint[] roiPoints, ArrayList<int[]> resultsArray, double dThreshold,
			Options options, long time, double beta)
	{
		// Extract results for analysis
		AssignedPoint[] predictedPoints = extractedPredictedPoints(resultsArray);

		MatchResult matchResult = MatchCalculator.analyseResults2D(roiPoints, predictedPoints, dThreshold);

		return new Result(id, options, matchResult.getNumberPredicted(), matchResult.getTruePositives(),
				matchResult.getFalsePositives(), matchResult.getFalseNegatives(), time, beta);
	}

	private AssignedPoint[] extractedPredictedPoints(ArrayList<int[]> resultsArray)
	{
		AssignedPoint[] predictedPoints = new AssignedPoint[resultsArray.size()];
		for (int i = 0; i < resultsArray.size(); i++)
			predictedPoints[i] = new AssignedPoint(resultsArray.get(i)[FindFoci.RESULT_X],
					resultsArray.get(i)[FindFoci.RESULT_Y], i);
		return predictedPoints;
	}

	/**
	 * Convert the FindFoci parameters into a text representation
	 * 
	 * @param blur
	 * @param backgroundMethod
	 * @param thresholdMethod
	 * @param backgroundParameter
	 * @param maxPeaks
	 * @param minSize
	 * @param searchMethod
	 * @param searchParameter
	 * @param peakMethod
	 * @param peakParameter
	 * @param sortMethod
	 * @param options
	 * @param centreMethod
	 * @param centreParameter
	 * @return
	 */
	static String createParameters(double blur, int backgroundMethod, String thresholdMethod,
			double backgroundParameter, int maxPeaks, int minSize, int searchMethod, double searchParameter,
			int peakMethod, double peakParameter, int sortMethod, int options, int centreMethod, double centreParameter)
	{
		// Output results
		String spacer = " : ";
		StringBuilder sb = new StringBuilder();
		sb.append(blur).append("\t");
		sb.append(FindFoci.backgroundMethods[backgroundMethod]);
		if (backgroundMethodHasStatisticsMode(backgroundMethod))
		{
			sb.append(" (").append(FindFoci.getStatisticsMode(options)).append(") ");
		}
		sb.append(spacer);
		sb.append(backgroundMethodHasParameter(backgroundMethod) ? IJ.d2s(backgroundParameter, 2) : thresholdMethod)
				.append("\t");
		sb.append(maxPeaks).append("\t");
		sb.append(minSize);
		if ((options & FindFoci.OPTION_MINIMUM_ABOVE_SADDLE) != 0)
			sb.append(" >saddle");
		sb.append("\t");
		sb.append(FindFoci.searchMethods[searchMethod]);
		if (searchMethodHasParameter(searchMethod))
			sb.append(spacer).append(IJ.d2s(searchParameter, 2));
		sb.append("\t");
		sb.append(FindFoci.peakMethods[peakMethod]).append(spacer);
		sb.append(IJ.d2s(peakParameter, 2)).append("\t");
		sb.append(FindFoci.sortIndexMethods[sortMethod]).append("\t");
		sb.append(FindFoci.getCentreMethods()[centreMethod]);
		if (centreMethodHasParameter(centreMethod))
			sb.append(spacer).append(IJ.d2s(centreParameter, 2));
		sb.append("\t");
		return sb.toString();
	}

	/**
	 * Convert the FindFoci text representation into Options
	 * 
	 * @param parameters
	 * @return the options
	 */
	private Options createOptions(String parameters)
	{
		String[] fields = p.split(parameters);
		try
		{
			double blur = Double.parseDouble(fields[0]);
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
				int first = fields[1].indexOf('(') + 1;
				int last = fields[1].indexOf(')', first);
				String mode = fields[1].substring(first, last);
				if (mode.equals("Inside"))
					options |= FindFoci.OPTION_STATS_INSIDE;
				else if (mode.equals("Outside"))
					options |= FindFoci.OPTION_STATS_OUTSIDE;
				else
					options |= FindFoci.OPTION_STATS_INSIDE | FindFoci.OPTION_STATS_OUTSIDE;
			}
			int index = fields[1].indexOf(" : ") + 3;
			String thresholdMethod = fields[1].substring(index);
			double backgroundParameter = 0;
			if (backgroundMethodHasParameter(backgroundMethod))
			{
				backgroundParameter = Double.parseDouble(thresholdMethod);
				thresholdMethod = "";
			}
			int maxPeaks = Integer.parseInt(fields[2]);
			index = fields[3].indexOf(" ");
			if (index > 0)
			{
				fields[3] = fields[3].substring(0, index);
				options |= FindFoci.OPTION_MINIMUM_ABOVE_SADDLE;
			}
			int minSize = Integer.parseInt(fields[3]);
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
			double peakParameter = Double.parseDouble(fields[5].substring(index));
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
		catch (Exception e)
		{
			System.out
					.println("Error converting parameters to FindFoci options: " + parameters + "\n" + e.getMessage());
			return null;
		}
	}

	private static double calculateFScore(double precision, double recall, double beta)
	{
		double b2 = beta * beta;
		double f = ((1.0 + b2) * precision * recall) / (b2 * precision + recall);
		return (Double.isNaN(f) ? 0 : f);
	}

	public static AssignedPoint[] extractRoiPoints(Roi roi, ImagePlus imp, ImagePlus mask)
	{
		AssignedPoint[] roiPoints = PointManager.extractRoiPoints(roi);

		// Check if the mask is the correct dimensions to be used by the FindFoci algorithm
		if (!validMask(imp, mask))
		{
			return roiPoints;
		}

		// Look through the ROI points and exclude all outside the mask
		ImageStack stack = mask.getStack();

		int id = 0;
		for (int i = 0; i < roiPoints.length; i++)
		{
			AssignedPoint point = roiPoints[i];
			for (int slice = 1; slice <= stack.getSize(); slice++)
			{
				ImageProcessor ipMask = stack.getProcessor(slice);

				if (ipMask.get(point.getX(), point.getY()) > 0)
				{
					roiPoints[id++] = point;
					break;
				}
			}
		}

		return Arrays.copyOf(roiPoints, id);
	}

	private Roi createRoi(List<Coordinate> points)
	{
		int[] ox = new int[points.size()];
		int[] oy = new int[points.size()];

		int i = 0;
		for (Coordinate point : points)
		{
			ox[i] = point.getX();
			oy[i] = point.getY();
			i++;
		}
		return new PointRoi(ox, oy, ox.length);
	}

	private static boolean validMask(ImagePlus imp, ImagePlus mask)
	{
		return mask != null && mask.getWidth() == imp.getWidth() && mask.getHeight() == imp.getHeight() &&
				(mask.getStackSize() == imp.getStackSize() || mask.getStackSize() == 1);
	}

	private void sortResults(ArrayList<Result> results, int sortMethod, boolean highestFirst)
	{
		int sortIndex = sortMethod - 1;
		if (sortMethod != SORT_NONE)
		{
			ResultComparator c = new ResultComparator(sortIndex, highestFirst);
			//c.tieMethod = 0;
			Collections.sort(results, c);

			// Cannot assign a rank if we have not sorted
			int rank = 1;
			int count = 0;
			double score = results.get(0).metrics[sortIndex];
			for (Result r : results)
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
	}

	private void sortResults(ArrayList<Result> results)
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

		public int id;
		public Options options;
		public String parameters;
		public int n, tp, fp, fn;
		public long time;
		public double[] metrics = new double[9];

		public Result(int id, Options options, int n, int tp, int fp, int fn, long time, double beta)
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
			{
				metrics[PRECISION] = (double) tp / (tp + fp);
			}
			if (tp + fn > 0)
			{
				metrics[RECALL] = (double) tp / (tp + fn);
			}
			if (tp + fp + fn > 0)
			{
				metrics[JACCARD] = (double) tp / (tp + fp + fn);
			}
			metrics[F05] = calculateFScore(metrics[PRECISION], metrics[RECALL], 0.5);
			metrics[F1] = calculateFScore(metrics[PRECISION], metrics[RECALL], 1.0);
			metrics[F2] = calculateFScore(metrics[PRECISION], metrics[RECALL], 2.0);
			metrics[Fb] = calculateFScore(metrics[PRECISION], metrics[RECALL], beta);
		}

		/**
		 * Add the values stored in the given result to the current values
		 * 
		 * @param result
		 */
		public void add(Result result)
		{
			n += result.n;
			tp += result.tp;
			fp += result.fp;
			fn += result.fn;
			time += result.time;
			for (int i = 0; i < metrics.length; i++)
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
		private int sortIndex = 0;
		private int tieMethod = 1;
		private final int highest;

		public ResultComparator(int sortIndex, boolean highestFirst)
		{
			this.sortIndex = sortIndex;
			highest = (highestFirst) ? -1 : 1;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
		 */
		public int compare(Result o1, Result o2)
		{
			// Require the highest is first
			if (o1.metrics[sortIndex] > o2.metrics[sortIndex])
				return highest;
			if (o1.metrics[sortIndex] < o2.metrics[sortIndex])
				return -highest;

			if (tieMethod == 1 && o1.options != null && o2.options != null)
			{
				// Return method with most conservative settings
				int[] result = new int[1];

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
			{
				parameters = FindFociOptimiser.createParameters(blur, backgroundMethod, autoThresholdMethod,
						backgroundParameter, maxPeaks, minSize, searchMethod, searchParameter, peakMethod,
						peakParameter, sortIndex, options, centreMethod, centreParameter);
			}
			return parameters;
		}
	}

	private abstract class Counter
	{
		final int total;
		final int progressSize;
		int last = 0;

		public Counter(int total)
		{
			this.total = total;
			this.progressSize = Math.max(1, total / 400);
		}

		public void increment()
		{
			if (incrementAndGet() > last)
			{
				// Don't show progress all the time
				last = get() + progressSize;
				IJ.showProgress(get(), total);
			}
		}

		protected abstract int incrementAndGet();

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
		protected int get()
		{
			return step.get();
		}
	}

	public class OptimisationWorker implements Runnable
	{
		String image;
		Counter counter;
		ArrayList<Result> results = null;

		public OptimisationWorker(String image, Counter counter)
		{
			this.image = image;
			this.counter = counter;
		}

		public void run()
		{
			final ImagePlus imp = FindFoci.openImage(inputDirectory, image);
			final ImagePlus mask = FindFoci.openImage(inputDirectory, FindFoci.getMaskImage(inputDirectory, image));
			final String resultFile = outputDirectory + File.separator + imp.getShortTitle();
			final String fullResultFile = resultFile + ".results.xls";
			boolean newResults = false;
			if (reuseResults && new File(fullResultFile).exists())
			{
				results = loadResults(fullResultFile);
				if (results.size() == combinations)
					IJ.log("Re-using results: " + fullResultFile);
				else
					results = null;
			}
			if (results == null)
			{
				newResults = true;
				results = runOptimiser(imp, mask, counter);
			}
			if (results != null)
			{
				if (newResults)
				{
					saveResults(imp, mask, results, null, resultFile);
				}

				checkOptimisationSpace(results, imp);

				// Reset to the order defined by the ID
				sortResults(results);
			}
		}
	}

	private static Pattern p = Pattern.compile("\t");

	/**
	 * Load the results from the specified file. We assign an arbitrary ID to each result using the unique combination
	 * of parameters.
	 * 
	 * @param filename
	 * @return The results
	 */
	private ArrayList<Result> loadResults(String filename)
	{
		ArrayList<Result> results = new ArrayList<FindFociOptimiser.Result>();

		BufferedReader input = null;
		try
		{
			if (countLines(filename) != combinations)
				return null;

			FileInputStream fis = new FileInputStream(filename);
			input = new BufferedReader(new InputStreamReader(fis));

			String line;
			while ((line = input.readLine()) != null)
			{
				if (line.length() == 0)
					continue;
				if (line.charAt(0) == '#')
					continue;

				// Code using split and parse
				// # Rank	Blur	Background method	Max	Min	Search method	Peak method	Sort method	Centre method	N	TP	FP	FN	Jaccard	Precision	Recall	F0.5	F1	F2	F-beta	mSec
				int endIndex = getIndex(line, 8) + 1; // include the final tab
				String parameters = line.substring(line.indexOf('\t') + 1, endIndex);
				String metrics = line.substring(endIndex);
				String[] fields = p.split(metrics);

				// Items we require
				int id = getId(parameters);

				int n = Integer.parseInt(fields[0]);
				int tp = Integer.parseInt(fields[1]);
				int fp = Integer.parseInt(fields[2]);
				int fn = Integer.parseInt(fields[3]);
				long time = Long.parseLong(fields[fields.length - 1]);

				Result r = new Result(id, null, n, tp, fp, fn, time, myBeta);
				// Do not count on the Options being parsed from the parameters.
				r.parameters = parameters;
				r.options = optionsMap.get(id);
				results.add(r);
			}

			// If the results were loaded then we must sort them to get a rank
			sortResults(results, myResultsSortMethod, true);
		}
		catch (ArrayIndexOutOfBoundsException e)
		{
			return null;
		}
		catch (IOException e)
		{
			return null;
		}
		catch (NumberFormatException e)
		{
			return null;
		}
		finally
		{
			try
			{
				if (input != null)
					input.close();
			}
			catch (IOException e)
			{
				// Ignore
			}
		}

		return results;
	}

	/**
	 * Count the number of valid lines in the file
	 * 
	 * @param filename
	 * @return The number of lines
	 */
	private int countLines(String filename)
	{
		BufferedReader input = null;
		try
		{
			int count = 0;

			FileInputStream fis = new FileInputStream(filename);
			input = new BufferedReader(new InputStreamReader(fis));

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
		catch (IOException e)
		{
			return 0;
		}
		finally
		{
			try
			{
				if (input != null)
					input.close();
			}
			catch (IOException e)
			{
				// Ignore
			}
		}
	}

	/**
	 * Get the index of the nth occurrence of the tab character
	 * 
	 * @param line
	 * @param n
	 * @return
	 */
	private int getIndex(String line, int n)
	{
		char[] value = line.toCharArray();
		for (int i = 0; i < value.length; i++)
		{
			if (value[i] == '\t')
			{
				if (n-- <= 0)
					return i;
			}
		}
		return -1;
	}

	private HashMap<String, Integer> idMap = new HashMap<String, Integer>();
	private HashMap<Integer, Options> optionsMap = new HashMap<Integer, Options>();
	private int nextId = 1;

	/**
	 * Get a unique ID for the parameters string
	 * 
	 * @param parameters
	 * @return the ID
	 */
	private int getId(String parameters)
	{
		Integer i = idMap.get(parameters);
		if (i == null)
		{
			i = createId(parameters);
		}
		return i;
	}

	/**
	 * Create a unique ID for the parameters string
	 * 
	 * @param parameters
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
	public void mouseClicked(MouseEvent e)
	{
		// Show the result that was double clicked in the result table
		if (e.getClickCount() > 1)
		{
			// Double-click on the multi-mode dialog text fields
			if (e.getSource() instanceof TextField)
			{
				TextField tf = (TextField) e.getSource();
				String path = tf.getText();
				final boolean recording = Recorder.record;
				Recorder.record = false;
				path = ImageJHelper.getDirectory("Choose_a_directory", path);
				Recorder.record = recording;
				if (path != null)
					tf.setText(path);
			}
			// Double-click on the result table
			else if (lastImp != null)
			{
				// An extra line is added at the end of the results so remove this 
				int rank = resultsWindow.getTextPanel().getLineCount() -
						resultsWindow.getTextPanel().getSelectionStart() - 1;

				// Show the result that was double clicked. Results are stored in reverse order.
				if (rank > 0 && rank <= lastResults.size())
				{
					showResult(lastImp, lastMask, lastResults.get(rank - 1).options);
				}
			}
		}
	}

	public void mousePressed(MouseEvent e)
	{
	}

	public void mouseReleased(MouseEvent e)
	{
	}

	public void mouseEntered(MouseEvent e)
	{
	}

	public void mouseExited(MouseEvent e)
	{
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
			instance.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

			IJ.register(OptimiserView.class);

			showInstance();
			IJ.showStatus("FindFoci Optimiser ready");
		}
		catch (ExceptionInInitializerError e)
		{
			exception = e;
			errorMessage = "Failed to initialize class: " + e.getMessage();
		}
		catch (LinkageError e)
		{
			exception = e;
			errorMessage = "Failed to link class: " + e.getMessage();
		}
		catch (ClassNotFoundException ex)
		{
			exception = ex;
			errorMessage = "Failed to find class: " + ex.getMessage() +
					"\nCheck you have beansbinding-1.2.1.jar on your classpath\n";
		}
		catch (Throwable ex)
		{
			exception = ex;
			errorMessage = ex.getMessage();
		}
		finally
		{
			if (exception != null)
			{
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				pw.write(errorMessage);
				pw.append('\n');
				exception.printStackTrace(pw);
				IJ.log(sw.toString());
			}
		}
	}

	private void showInstance()
	{
		WindowManager.addWindow(instance);
		instance.setVisible(true);
		instance.toFront();
	}

	public void windowOpened(WindowEvent e)
	{
	}

	public void windowClosing(WindowEvent e)
	{
		WindowManager.removeWindow(instance);
	}

	public void windowClosed(WindowEvent e)
	{
	}

	public void windowIconified(WindowEvent e)
	{
	}

	public void windowDeiconified(WindowEvent e)
	{
	}

	public void windowActivated(WindowEvent e)
	{
	}

	public void windowDeactivated(WindowEvent e)
	{
	}

	// ---------------------------------------------------------------------------
	// Start preset values.
	// The following code is for setting the dialog fields with preset values
	// ---------------------------------------------------------------------------
	private class DialogSettings
	{
		String name;
		ArrayList<String> text = new ArrayList<String>();
		ArrayList<Boolean> option = new ArrayList<Boolean>();

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
	private String[][] textPreset = new String[][] { { "Testing", // preset name 
			// Text fields
			"3", // Background_parameter
			"Otsu", // Auto_threshold
			"Both", // Statistics_mode
			"0, 0.6, 0.2", // Search_parameter
			"3, 9, 2", // Minimum_size
			"0, 0.6, 0.2", // Peak_parameter
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
			"Otsu", // Auto_threshold
			"Both", // Statistics_mode
			"0, 0.6, 0.2", // Search_parameter
			"1, 19, 2", // Minimum_size
			"0, 0.6, 0.2", // Peak_parameter
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
			"Otsu, RenyiEntropy, Triangle", // Auto_threshold
			"Both", // Statistics_mode
			"0, 0.8, 0.1", // Search_parameter
			"1, 9, 2", // Minimum_size
			"0, 0.8, 0.1", // Peak_parameter
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
	private int[][] optionPreset = new int[][] { { FLAG_FALSE, // Background_SD_above_mean
			FLAG_FALSE, // Background_Absolute
			FLAG_TRUE, // Background_Auto_Threshold
			FLAG_TRUE, // Search_above_background
			FLAG_FALSE, // Search_fraction_of_peak
			FLAG_FALSE + FLAG_SINGLE // Show_score_images
	}, { FLAG_TRUE, // Background_SD_above_mean
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
	} };

	private void createSettings()
	{
		settings = new ArrayList<FindFociOptimiser.DialogSettings>();

		settings.add(new DialogSettings("Custom"));
		for (int i = 0; i < textPreset.length; i++)
		{
			// First field is the name
			DialogSettings s = new DialogSettings(textPreset[i][0]);
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
					{
						continue;
					}
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
	 */
	@SuppressWarnings("unchecked")
	private void addListeners(GenericDialog gd)
	{
		listenerGd = gd;
		Vector<TextField> fields = (Vector<TextField>) gd.getStringFields();
		// Optionally Ignore final text field (it is the result file field)
		int stringFields = fields.size() - ((multiMode) ? 0 : 1);
		for (int i = 0; i < stringFields; i++)
			fields.get(i).addTextListener(this);
		for (Choice field : (Vector<Choice>) gd.getChoices())
			field.addItemListener(this);
		for (TextField field : (Vector<TextField>) gd.getNumericFields())
			field.addTextListener(this);
		for (Checkbox field : (Vector<Checkbox>) gd.getCheckboxes())
			field.addItemListener(this);
	}

	@SuppressWarnings("unchecked")
	private void saveCustomSettings(GenericDialog gd)
	{
		DialogSettings s = settings.get(0);
		s.text.clear();
		s.option.clear();
		Vector<TextField> fields = (Vector<TextField>) gd.getStringFields();
		// Optionally Ignore final text field (it is the result file field)
		int stringFields = fields.size() - ((multiMode) ? 0 : 1);
		for (int i = 0; i < stringFields; i++)
			s.text.add(fields.get(i).getText());
		// The first choice is the settings name which we ignore
		Vector<Choice> cfields = (Vector<Choice>) gd.getChoices();
		for (int i = 1; i < cfields.size(); i++)
			s.text.add(cfields.get(i).getSelectedItem());
		for (TextField field : (Vector<TextField>) gd.getNumericFields())
			s.text.add(field.getText());
		for (Checkbox field : (Vector<Checkbox>) gd.getCheckboxes())
			s.option.add(field.getState());
	}

	@SuppressWarnings("unchecked")
	private void applySettings(GenericDialog gd, DialogSettings s)
	{
		//System.out.println("Applying " + s.name + " " + updating);
		lastTime = System.currentTimeMillis();
		int index = 0, index2 = 0;
		Vector<TextField> fields = (Vector<TextField>) gd.getStringFields();
		// Optionally Ignore final text field (it is the result file field)
		int stringFields = fields.size() - ((multiMode) ? 0 : 1);
		for (int i = 0; i < stringFields; i++)
			fields.get(i).setText(s.text.get(index++));
		// The first choice is the settings name
		Vector<Choice> cfields = (Vector<Choice>) gd.getChoices();
		cfields.get(0).select(s.name);
		for (int i = 1; i < cfields.size(); i++)
			cfields.get(i).select(s.text.get(index++));
		for (TextField field : (Vector<TextField>) gd.getNumericFields())
			field.setText(s.text.get(index++));
		for (Checkbox field : (Vector<Checkbox>) gd.getCheckboxes())
			field.setState(s.option.get(index2++));
		//System.out.println("Done Applying " + s.name + " " + updating);
	}

	public void actionPerformed(ActionEvent e)
	{
		dialogItemChanged(listenerGd, e);
	}

	public void textValueChanged(TextEvent e)
	{
		dialogItemChanged(listenerGd, e);
	}

	public void itemStateChanged(ItemEvent e)
	{
		dialogItemChanged(listenerGd, e);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.gui.DialogListener#dialogItemChanged(ij.gui.GenericDialog, java.awt.AWTEvent)
	 */
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e)
	{
		if (e == null || e.getSource() == null || !aquireLock())
			return true;

		//System.out.println("changed " + e.getSource());

		// Check if this is the settings checkbox
		if (e.getSource() == choice.get(0))
		{
			Choice thisChoice = (Choice) choice.get(0);

			// If the choice is currently custom save the current values so they can be restored
			if (custom)
				saveCustomSettings(gd);

			// Update the other fields with preset values
			int index = thisChoice.getSelectedIndex();
			if (index != 0)
				custom = false;
			applySettings(gd, settings.get(index));
		}
		else
		{
			// This is a change to another field. Note that the dialogItemChanged method is called
			// for each field modified in applySettings. This appears to happen after the applySettings
			// method has ended (as if the dialogItemChanged events are in a queue or are delayed until
			// the previous call to dialogItemChanged has ended).
			// To prevent processing these events ignore anything that happens within x milliseconds
			// of the call to applySettings
			if (System.currentTimeMillis() - lastTime > 300)
			{
				// A change to any other field makes this a custom setting			
				// => Set the settings drop-down to custom
				Choice thisChoice = (Choice) choice.get(0);
				if (thisChoice.getSelectedIndex() != 0)
				{
					custom = true;
					thisChoice.select(0);
				}

				// Esnure that checkboxes 1 & 2 are complementary
				if (e.getSource() instanceof Checkbox)
				{
					Checkbox cb = (Checkbox) e.getSource();
					// If just checked then we must uncheck the complementing checkbox
					if (cb.getState())
					{
						// Only checkbox 1 & 2 are complementary
						if (cb.equals(checkbox.get(0)))
						{
							((Checkbox) checkbox.get(1)).setState(false);
						}
						else if (cb.equals(checkbox.get(1)))
						{
							((Checkbox) checkbox.get(0)).setState(false);
						}
					}
				}
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
