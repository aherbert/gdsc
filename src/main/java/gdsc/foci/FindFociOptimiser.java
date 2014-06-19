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
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.gui.YesNoCancelDialog;
import ij.io.FileInfo;
import ij.plugin.PlugIn;
import ij.plugin.frame.Recorder;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

import java.awt.Checkbox;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

import javax.swing.JFrame;

/**
 * Runs the FindFoci plugin with various settings and compares the results to the reference image point ROI.
 */
// TODO - Add support for running the optimiser on many images. 
// This will create a huge set of results with all the parameters settings for each image in turn. 
// You could then combine the TP, FP, FN, etc, scores for each parameter setting across all the image.
// The setting with the best metrics would be the one to use.
public class FindFociOptimiser implements PlugIn, MouseListener, ItemListener, WindowListener
{
	private static OptimiserView instance;

	private static String FRAME_TITLE = "FindFoci Optimiser";
	private static TextWindow resultsWindow = null;
	private static int STEP_LIMIT = 10000;

	private static String myMaskImage = "";
	private static boolean myBackgroundStdDevAboveMean = true;
	private static boolean myBackgroundAuto = true;
	private static boolean myBackgroundAbsolute = false;
	private static String myBackgroundParameter = "2.5 , 3.5 , 0.5";
	private static String myThresholdMethod = "Otsu";
	private static String myStatisticsMode = "Both";
	private static boolean mySearchAboveBackground = true;
	private static boolean mySearchFractionOfPeak = true;
	private static String mySearchParameter = "0, 0.6, 0.2";
	private static String myMinSizeParameter = "1 , 9 , 2";
	private static String[] saddleOptions = { "Yes", "No", "Both" };
	private static int myMinimumAboveSaddle = 0;
	private static int myPeakMethod = FindFoci.PEAK_RELATIVE_ABOVE_BACKGROUND;
	private static String myPeakParameter = "0, 0.6, 0.2";
	private static String mySortMethod = "" + FindFoci.SORT_INTENSITY;
	private static int myMaxPeaks = 50;
	private static String myGaussianBlur = "0,0.5,1";
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
	 * Sort the results using the Jaccard
	 */
	public final static int SORT_JACCARD = 6;

	private static double mySearchFraction = 0.05;
	private static int myResultsSortMethod = SORT_F1;
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

	@SuppressWarnings("rawtypes")
	private Vector checkboxes;

	// Stored to allow the display of any of the latest results from the result table
	private ArrayList<Result> results = new ArrayList<Result>();
	private int myImage = 0;

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
			ImagePlus imp = WindowManager.getCurrentImage();
			run(imp);
		}
	}

	/**
	 * Run the optimiser on the given image
	 */
	public void run(ImagePlus imp)
	{
		Roi roi = checkImage(imp);
		if (roi == null)
			return;
		myImage = imp.getID();

		// TODO - Add 'true' support for 3D images. Currently it just uses XY distance and ignores Z. 
		// (not sure how to do this since the ROI seems to be associated with one slice. 
		// It may necessitate using all the ROIs in the ROI manager, one for each slice.)  

		if (!showDialog(imp))
			return;

		if (myBackgroundStdDevAboveMean && myBackgroundAbsolute)
		{
			IJ.error("Cannot optimise background methods 'SD above mean' and 'Absolute' using the same parameters");
			return;
		}
		if (!(myBackgroundStdDevAboveMean | myBackgroundAbsolute | myBackgroundAuto))
		{
			IJ.error("Require at least one background method");
			return;
		}
		if (!(mySearchAboveBackground | mySearchFractionOfPeak))
		{
			IJ.error("Require at least one background search method");
			return;
		}

		// Check which options to optimise
		optionsArray = createOptionsArray();

		parseThresholdMethods();
		if (myBackgroundAuto && thresholdMethods.length == 0)
		{
			IJ.error("No recognised methods for auto-threshold");
			return;
		}

		parseStatisticsModes();

		backgroundMethodArray = createBackgroundArray();
		parseBackgroundLimits();
		if (myBackgroundParameterMax < myBackgroundParameterMin)
		{
			IJ.error("Background parameter max must be greater than min");
			return;
		}
		myBackgroundParameterMinArray = createBackgroundLimits();

		searchMethodArray = createSearchArray();
		parseSearchLimits();
		if (mySearchParameterMax < mySearchParameterMin)
		{
			IJ.error("Search parameter max must be greater than min");
			return;
		}
		mySearchParameterMinArray = createSearchLimits();

		parseMinSizeLimits();
		if (myMinSizeMax < myMinSizeMin)
		{
			IJ.error("Size max must be greater than min");
			return;
		}

		parsePeakParameterLimits();
		if (myPeakParameterMax < myPeakParameterMin)
		{
			IJ.error("Peak parameter max must be greater than min");
			return;
		}

		sortMethodArray = createSortArray();
		if (sortMethodArray.length == 0)
		{
			IJ.error("Require at least one sort method");
			return;
		}

		blurArray = createBlurArray();

		centreMethodArray = createCentreArray();
		parseCentreLimits();
		if (myCentreParameterMax < myCentreParameterMin)
		{
			IJ.error("Centre parameter max must be greater than min");
			return;
		}
		myCentreParameterMinArray = createCentreMinLimits();
		myCentreParameterMaxArray = createCentreMaxLimits();
		myCentreParameterIntervalArray = createCentreIntervals();

		ImagePlus mask = WindowManager.getImage(myMaskImage);

		if (!validMask(imp, mask))
		{
			statisticsModes = new String[] { "Both" };
		}

		// Count the number of options
		int totalSteps = countSteps();
		if (totalSteps >= STEP_LIMIT)
		{
			IJ.error("Maximum number of optimisation steps exceeded: " + totalSteps + " >> " + STEP_LIMIT);
			return;
		}

		YesNoCancelDialog d = new YesNoCancelDialog(IJ.getInstance(), FRAME_TITLE, totalSteps +
				" combinations. Do you wish to proceed?");
		if (!d.yesPressed())
			return;

		int step = 0;
		FindFoci fp = new FindFoci();
		long start = System.currentTimeMillis();

		// Set the threshold for assigning points matches as a fraction of the image size
		double dThreshold = getDistanceThreshold(imp);

		results = new ArrayList<Result>(totalSteps);

		AssignedPoint[] roiPoints = extractRoiPoints(roi, imp, mask);

		if (roiPoints.length == 0)
		{
			IJ.error(FRAME_TITLE, "No ROI points fall inside the mask image");
			return;
		}

		IJ.log("---\n" + FRAME_TITLE);
		IJ.log(totalSteps + " combinations");

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
						boolean logBackground = (blurCount == 0); // Log on first blur iteration

						for (int minSize = myMinSizeMin; minSize <= myMinSizeMax; minSize += myMinSizeInterval)
							for (int s = 0; s < searchMethodArray.length; s++)
								for (double searchParameter = mySearchParameterMinArray[s]; searchParameter <= mySearchParameterMax; searchParameter += mySearchParameterInterval)
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

												double backgroundLevel = ((double[]) clonedInitArray[4])[FindFoci.STATS_BACKGROUND];
												if (logBackground)
												{
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

														IJ.showProgress(++step, totalSteps);

														if (peakResults == null)
															continue;

														// Get the results
														@SuppressWarnings("unchecked")
														ArrayList<int[]> resultsArray = (ArrayList<int[]>) peakResults[1];

														Options runOptions = new Options(blur,
																backgroundMethodArray[b], backgroundParameter,
																thresholdMethod, searchMethodArray[s], searchParameter,
																myMaxPeaks, minSize, myPeakMethod, peakParameter,
																sortMethod, options, centreMethodArray[c],
																centreParameter, backgroundLevel);
														Result result = analyseResults(roiPoints, resultsArray,
																dThreshold, runOptions, time, myBeta);
														results.add(result);
													}
												}
											}
										}
					}
				}
			}
		}

		long runTime = System.currentTimeMillis() - start;
		double seconds = runTime / 1000.0;
		IJ.log(String.format("Optimisation time = %.3f sec (%.3f ms / combination)", seconds, (double) runTime /
				totalSteps));

		// Sort results (ascending to place best result at bottom of scrolling results window)
		sortResults(results, myResultsSortMethod);

		createResultsWindow();

		// Limit the number of results
		int noOfResults = results.size();
		if (myMaxResults > 0 && noOfResults > myMaxResults)
			noOfResults = myMaxResults;

		for (int i = noOfResults; i-- > 0;)
		{
			Result result = results.get(i);
			StringBuilder sb = new StringBuilder();
			sb.append(i + 1).append("\t");
			sb.append(result.getParameters());
			sb.append(result.n).append("\t");
			sb.append(result.tp).append("\t");
			sb.append(result.fp).append("\t");
			sb.append(result.fn).append("\t");
			sb.append(IJ.d2s(result.metrics[Result.JACCARD], 4)).append("\t");
			sb.append(IJ.d2s(result.metrics[Result.PRECISION], 4)).append("\t");
			sb.append(IJ.d2s(result.metrics[Result.RECALL], 4)).append("\t");
			sb.append(IJ.d2s(result.metrics[Result.F05], 4)).append("\t");
			sb.append(IJ.d2s(result.metrics[Result.F1], 4)).append("\t");
			sb.append(IJ.d2s(result.metrics[Result.F2], 4)).append("\t");
			sb.append(IJ.d2s(result.metrics[Result.Fb], 4)).append("\t");
			sb.append(IJ.d2s(result.time / 1000000.0, 4)).append("\n");
			resultsWindow.append(sb.toString());
		}
		resultsWindow.append("\n");

		// Re-run Find_Peaks and output the best result
		if (!results.isEmpty())
		{
			IJ.log("Top result = " + IJ.d2s(results.get(0).metrics[myResultsSortMethod - 1], 4));

			Options bestOptions = results.get(0).options;

			AssignedPoint[] predictedPoints = showResult(bestOptions);

			saveResults(results, bestOptions, predictedPoints);

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
				else if (bestOptions.peakParameter + myPeakParameterInterval > myPeakParameterMax &&
						myPeakParameterMax < 1)
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
							") obtained at the following limits:\n");
					sb.append("You may want to increase the optimisation space.");

					IJ.log("---");
					IJ.log(sb.toString());

					// Do not show duplicate messages when running in batch
					if (!java.awt.GraphicsEnvironment.isHeadless())
						IJ.showMessage(sb.toString());
				}
			}
		}

		IJ.showTime(imp, start, "Done ");
	}

	private void saveResults(ArrayList<Result> results, Options bestOptions, AssignedPoint[] predictedPoints)
	{
		if (myResultFile == null)
			return;

		OutputStreamWriter out = createResultsFile(bestOptions);
		if (out == null)
			return;

		try
		{
			out.write("#\n# Results\n# " + createResultsHeader());

			// Output all results in ascending rank order
			for (int i = 0; i < results.size(); i++)
			{
				Result result = results.get(i);
				StringBuilder sb = new StringBuilder();
				sb.append(i + 1).append("\t");
				sb.append(result.getParameters());
				sb.append(result.n).append("\t");
				sb.append(result.tp).append("\t");
				sb.append(result.fp).append("\t");
				sb.append(result.fn).append("\t");
				sb.append(IJ.d2s(result.metrics[Result.JACCARD], 4)).append("\t");
				sb.append(IJ.d2s(result.metrics[Result.PRECISION], 4)).append("\t");
				sb.append(IJ.d2s(result.metrics[Result.RECALL], 4)).append("\t");
				sb.append(IJ.d2s(result.metrics[Result.F05], 4)).append("\t");
				sb.append(IJ.d2s(result.metrics[Result.F1], 4)).append("\t");
				sb.append(IJ.d2s(result.metrics[Result.F2], 4)).append("\t");
				sb.append(IJ.d2s(result.metrics[Result.Fb], 4)).append("\t");
				sb.append(IJ.d2s(result.time / 1000000.0, 4)).append("\n");
				out.write(sb.toString());
			}

			// Save the identified points
			PointManager.savePoints(predictedPoints, myResultFile + ".points.csv");
		}
		catch (IOException e)
		{
			IJ.log("Failed to write to the output file '" + myResultFile + ".points.csv': " + e.getMessage());
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

	private void addFindFociCommand(OutputStreamWriter out, Options bestOptions) throws IOException
	{
		// This is the only way to clear the recorder. 
		// It will save the current optimiser command to the recorder and then clear it.
		Recorder.saveCommand();

		// Use the recorder to build the options for the FindFoci plugin
		Recorder.setCommand("FindFoci");
		Recorder.recordOption("mask", myMaskImage);
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
		int length = (imp.getWidth() < imp.getHeight()) ? imp.getWidth() : imp.getHeight();
		double dThreshold = Math.ceil(mySearchFraction * length);
		return dThreshold;
	}

	private void append(StringBuilder sb, String format, Object... args)
	{
		sb.append(String.format(format, args));
	}

	private AssignedPoint[] showResult(Options options)
	{
		ImagePlus imp = WindowManager.getImage(myImage);
		if (imp == null)
			return null;
		ImagePlus mask = WindowManager.getImage(myMaskImage);

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
		ImagePlus clone = WindowManager.getImage(title);
		if (clone != null)
		{
			clone.close();
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
		Recorder.record = true;

		// Get the optimisation search settings
		GenericDialog gd = new GenericDialog(FRAME_TITLE);

		ArrayList<String> newImageList = FindFoci.buildMaskList(imp);

		// Column 1
		gd.addMessage("Runs the FindFoci algorithm using different parameters.\n"
				+ "Results are compared to reference ROI points.\n\n"
				+ "Input range fields accept 3 values: min,max,interval\n"
				+ "Gaussian blur accepts comma-delimited values for the blur.");

		gd.addChoice("Mask", newImageList.toArray(new String[0]), myMaskImage);

		// Do not allow background above mean and background absolute to both be enabled.
		gd.addCheckbox("Background_SD_above_mean", myBackgroundStdDevAboveMean);
		gd.addCheckbox("Background_Absolute", (myBackgroundStdDevAboveMean) ? false : myBackgroundAbsolute);

		if (!java.awt.GraphicsEnvironment.isHeadless())
		{
			checkboxes = gd.getCheckboxes();
			((Checkbox) checkboxes.get(0)).addItemListener(this);
			((Checkbox) checkboxes.get(1)).addItemListener(this);
		}

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
		gd.addNumericField("Peak_match_search_fraction", mySearchFraction, 2);
		gd.addChoice("Result_sort_method", sortMethods, sortMethods[myResultsSortMethod]);
		gd.addNumericField("F-beta", myBeta, 2);
		gd.addNumericField("Maximum_results", myMaxResults, 0);
		gd.addNumericField("Step_limit", STEP_LIMIT, 0);
		gd.addCheckbox("Show_score_images", myShowScoreImages);
		gd.addStringField("Result_file", myResultFile, 35);

		// Add a message about double clicking the result table to show the result
		gd.addMessage("Note: Double-click an entry in the optimiser results table\n"
				+ "to view the FindFoci output. This only works for the most recent\n"
				+ "set of results in the table.");

		gd.addHelp(gdsc.help.URL.FIND_FOCI);

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

		mySearchFraction = gd.getNextNumber();
		myResultsSortMethod = gd.getNextChoiceIndex();
		myBeta = gd.getNextNumber();
		myMaxResults = (int) gd.getNextNumber();
		STEP_LIMIT = (int) gd.getNextNumber();
		myShowScoreImages = gd.getNextBoolean();
		myResultFile = gd.getNextString();

		Recorder.record = recorderOn; // Reset the recorder

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

		return myCentreParameterMax; // Other methods have no parameters
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

		return myCentreParameterMax; // Other methods have no parameters
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

	private void createResultsWindow()
	{
		String heading = null;
		if (resultsWindow == null || !resultsWindow.isShowing())
		{
			heading = createResultsHeader();
			resultsWindow = new TextWindow(FRAME_TITLE + " Results", heading, "", 1000, 300);
			if (resultsWindow.getTextPanel() != null)
			{
				resultsWindow.getTextPanel().addMouseListener(this);
			}
		}
	}

	private OutputStreamWriter createResultsFile(Options bestOptions)
	{
		OutputStreamWriter out = null;
		try
		{
			String filename = myResultFile + ".results.xls";

			File file = new File(filename);
			if (!file.exists())
			{
				if (file.getParent() != null)
					new File(file.getParent()).mkdirs();
			}

			// Save results to file
			FileOutputStream fos = new FileOutputStream(filename);
			out = new OutputStreamWriter(fos, "UTF-8");

			// Record the images used
			ImagePlus imp = WindowManager.getImage(myImage);
			ImagePlus mask = WindowManager.getImage(myMaskImage);

			out.write("# ImageJ Script to repeat the optimiser and then run the optimal parameters\n#\n");
			if (mask != null)
				out.write("# open(\"" + getFilename(mask) + "\");\n");
			out.write("# open(\"" + getFilename(imp) + "\");\n");

			// Write the ImageJ macro command
			out.write(String.format("# run(\"FindFoci Optimiser\", \"%s\")\n", Recorder.getCommandOptions()));

			addFindFociCommand(out, bestOptions);

			return out;
		}
		catch (Exception e)
		{
			IJ.log("Failed to create results file '" + myResultFile + ".results.xls': " + e.getMessage());
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

	private String createResultsHeader()
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
		sb.append("mSec\n");
		return sb.toString();
	}

	private Result analyseResults(AssignedPoint[] roiPoints, ArrayList<int[]> resultsArray, double dThreshold,
			Options options, long time, double beta)
	{
		// Extract results for analysis
		AssignedPoint[] predictedPoints = extractedPredictedPoints(resultsArray);

		MatchResult matchResult = MatchCalculator.analyseResults2D(roiPoints, predictedPoints, dThreshold);

		return new Result(options, matchResult.getNumberPredicted(), matchResult.getTruePositives(),
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
		List<AssignedPoint> newPoints = new LinkedList<AssignedPoint>();
		ImageStack stack = mask.getStack();

		int id = 0;
		for (AssignedPoint point : roiPoints)
		{
			for (int slice = 1; slice <= stack.getSize(); slice++)
			{
				ImageProcessor ipMask = stack.getProcessor(slice);

				if (ipMask.get(point.getX(), point.getY()) > 0)
				{
					newPoints.add(new AssignedPoint(point.getX(), point.getY(), point.getZ(), id++));
					break;
				}
			}
		}

		return newPoints.toArray(new AssignedPoint[0]);
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

	private void sortResults(ArrayList<Result> results, int sortMethod)
	{
		if (sortMethod == SORT_NONE)
			return;
		Collections.sort(results, new ResultComparator(sortMethod - 1));
	}

	private class Result
	{
		public static final int PRECISION = 0;
		public static final int RECALL = 1;
		public static final int F05 = 2;
		public static final int F1 = 3;
		public static final int F2 = 4;
		public static final int Fb = 5;
		public static final int JACCARD = 6;

		public Options options;
		public int n, tp, fp, fn;
		public long time;
		public double[] metrics = new double[8];

		public Result(Options options, int n, int tp, int fp, int fn, long time, double beta)
		{
			this.options = options;
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

		public String getParameters()
		{
			return options.createParameters();
		}
	}

	/**
	 * Provides the ability to sort the results arrays in ascending order
	 */
	private class ResultComparator implements Comparator<Result>
	{
		private int sortIndex = 0;
		private int tieMethod = 1;

		public ResultComparator(int sortIndex)
		{
			this.sortIndex = sortIndex;
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
				return -1;
			if (o1.metrics[sortIndex] < o2.metrics[sortIndex])
				return 1;

			if (tieMethod == 1)
			{
				// Return method with most conservative settings
				int[] result = new int[1];

				if (compare(o1.options.blur, o2.options.blur, result) != 0)
					return result[0];

				// Lowest background level is more general
				if (compare(o1.options.backgroundLevel, o2.options.backgroundLevel, result) != 0)
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
			if (value1 < value2)
				result[0] = -1;
			else if (value1 > value2)
				result[0] = 1;
			else
				result[0] = 0;
			return result[0];
		}

		/**
		 * Set the method to use when the results have the same metric: 1 - Most conservative settings; 0 - Fastest
		 * 
		 * @param tieMethod
		 *            the tieMethod to set
		 */
		@SuppressWarnings("unused")
		public void setTieMethod(int tieMethod)
		{
			this.tieMethod = tieMethod;
		}

		/**
		 * @return the tieMethod
		 */
		@SuppressWarnings("unused")
		public int getTieMethod()
		{
			return tieMethod;
		}
	}

	private class Options
	{
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
		public double backgroundLevel;

		public Options(double blur, int backgroundMethod, double backgroundParameter, String autoThresholdMethod,
				int searchMethod, double searchParameter, int maxPeaks, int minSize, int peakMethod,
				double peakParameter, int sortIndex, int options, int centreMethod, double centreParameter,
				double backgroundLevel)
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
			this.backgroundLevel = backgroundLevel;
		}

		public String createParameters()
		{
			return FindFociOptimiser.createParameters(blur, backgroundMethod, autoThresholdMethod, backgroundParameter,
					maxPeaks, minSize, searchMethod, searchParameter, peakMethod, peakParameter, sortIndex, options,
					centreMethod, centreParameter);
		}
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
			// An extra line is added at the end of the results so remove this 
			int rank = resultsWindow.getTextPanel().getLineCount() - resultsWindow.getTextPanel().getSelectionStart() -
					1;

			// Show the result that was double clicked. Results are stored in reverse order.
			if (rank > 0 && rank <= results.size())
			{
				showResult(results.get(rank - 1).options);
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

	public void itemStateChanged(ItemEvent e)
	{
		if (e != null)
		{
			if (e.getSource() instanceof Checkbox && checkboxes != null && checkboxes.size() > 1)
			{
				Checkbox cb = (Checkbox) e.getSource();
				// If just checked then we must uncheck the complementing checkbox
				if (cb.getState())
				{
					// Only checkbox 1 & 2 are complementary
					if (cb.equals(checkboxes.get(0)))
					{
						((Checkbox) checkboxes.get(1)).setState(false);
					}
					else if (cb.equals(checkboxes.get(1)))
					{
						((Checkbox) checkboxes.get(0)).setState(false);
					}
				}
			}
		}
	}
}
