package gdsc.foci.controller;

/*----------------------------------------------------------------------------- 
 * GDSC Plugins for ImageJ
 * 
 * Copyright (C) 2016 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

import java.util.ArrayList;

import gdsc.foci.FindFoci;
import gdsc.foci.FindFociBaseProcessor;
import gdsc.foci.FindFociInitResults;
import gdsc.foci.FindFociMergeResults;
import gdsc.foci.FindFociPrelimResults;
import gdsc.foci.FindFociResult;
import gdsc.foci.FindFociResults;
import gdsc.foci.FindFociSearchResults;
import gdsc.foci.controller.MessageListener.MessageType;
import gdsc.foci.model.FindFociModel;
import gdsc.foci.model.FindFociState;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;

/**
 * Runs the {@link gdsc.foci.FindFoci } algorithm using input from a
 * synchronised queueing method.
 */
public class FindFociRunner extends Thread
{
	private FindFociModel model = null;
	private FindFociModel previousModel = null;
	private final Object lock = new Object();
	private final MessageListener listener;

	private boolean running = true;

	// Used for the staged FindFoci results
	FindFoci ff = new FindFoci();
	ImagePlus imp2;
	FindFociInitResults initResults;
	FindFociInitResults searchInitResults;
	FindFociSearchResults searchArray;
	FindFociInitResults mergeInitResults;
	FindFociInitResults resultsInitResults;
	FindFociInitResults maskInitResults;
	FindFociMergeResults mergeResults;
	FindFociPrelimResults prelimResults;
	FindFociResults results;

	public FindFociRunner(MessageListener listener)
	{
		this.listener = listener;
		notify(MessageType.READY);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Thread#run()
	 */
	public void run()
	{
		try
		{
			while (running)
			{
				// Check if there is a model to run. Synchronized to avoid conflict with the queue() method.
				FindFociModel modelToRun = null;
				synchronized (lock)
				{
					if (model != null)
					{
						modelToRun = model.deepCopy();
						model = null;
					}
				}

				if (modelToRun != null)
				{
					runFindFoci(modelToRun);
				}
				else
				{
					// Wait for a new model to be queued
					pause();
				}
			}
		}
		catch (InterruptedException e)
		{
			if (running)
			{
				IJ.log("FindPeakRunner interupted: " + e.getLocalizedMessage());
			}
		}
		catch (Throwable t)
		{
			// Log this to ImageJ. Do not bubble up exceptions
			if (t.getMessage() != null)
				IJ.log("A error occurred during processing: " + t.getMessage());
			else
				IJ.log("A error occurred during processing");
			t.printStackTrace();

			notify(MessageType.ERROR, t);
		}
		finally
		{
			ff = null;
			imp2 = null;
			initResults = null;
			searchInitResults = null;
			searchArray = null;
			mergeInitResults = null;
			resultsInitResults = null;
			maskInitResults = null;
			mergeResults = null;
			prelimResults = null;
			results = null;
			finish();
		}
	}

	private void notify(MessageType messageType, Object... params)
	{
		if (listener != null)
			listener.notify(messageType, params);
	}

	/**
	 * Invoke the Thread.wait() method
	 * 
	 * @throws InterruptedException
	 */
	private synchronized void pause() throws InterruptedException
	{
		wait();
	}

	/**
	 * Add a FindFociModel for processing
	 * 
	 * @param newModel
	 */
	public synchronized void queue(FindFociModel newModel)
	{
		if (newModel != null)
		{
			synchronized (lock)
			{
				model = newModel;
			}
			notifyAll();
		}
	}

	private void runFindFoci(FindFociModel model)
	{
		// Get the selected image
		String title = model.getSelectedImage();
		ImagePlus imp = WindowManager.getImage(title);
		if (null == imp)
		{
			notify(MessageType.ERROR);
			return;
		}

		if (!FindFoci.isSupported(imp.getBitDepth()))
		{
			notify(MessageType.ERROR);
			return;
		}

		// Set-up the FindFoci variables
		String maskImage = model.getMaskImage();
		int backgroundMethod = model.getBackgroundMethod();
		double backgroundParameter = model.getBackgroundParameter();
		String thresholdMethod = model.getThresholdMethod();
		String statisticsMode = model.getStatisticsMode();
		int searchMethod = model.getSearchMethod();
		double searchParameter = model.getSearchParameter();
		int minSize = model.getMinSize();
		boolean minimumAboveSaddle = model.isMinimumAboveSaddle();
		int peakMethod = model.getPeakMethod();
		double peakParameter = model.getPeakParameter();
		int sortMethod = model.getSortMethod();
		int maxPeaks = model.getMaxPeaks();
		int showMask = model.getShowMask();
		boolean overlayMask = model.isOverlayMask();
		boolean showTable = model.isShowTable();
		boolean clearTable = model.isClearTable();
		boolean markMaxima = model.isMarkMaxima();
		boolean markROIMaxima = model.isMarkROIMaxima();
		boolean markUsingOverlay = model.isMarkUsingOverlay();
		boolean hideLabels = model.isHideLabels();
		boolean showMaskMaximaAsDots = model.isShowMaskMaximaAsDots();
		// Ignore: model.isShowLogMessages()
		boolean removeEdgeMaxima = model.isRemoveEdgeMaxima();
		// Ignore: model.isSaveResults();
		// Ignore: model.getResultsDirectory();
		boolean objectAnalysis = model.isObjectAnalysis();
		boolean showObjectMask = model.isShowObjectMask();
		boolean saveToMemory = model.isSaveToMemory();
		double gaussianBlur = model.getGaussianBlur();
		int centreMethod = model.getCentreMethod();
		double centreParameter = model.getCentreParameter();
		double fractionParameter = model.getFractionParameter();

		int outputType = FindFoci.getOutputMaskFlags(showMask);

		if (overlayMask)
			outputType += FindFoci.OUTPUT_OVERLAY_MASK;
		if (showTable)
			outputType += FindFoci.OUTPUT_RESULTS_TABLE;
		if (clearTable)
			outputType += FindFoci.OUTPUT_CLEAR_RESULTS_TABLE;
		if (markMaxima)
			outputType += FindFoci.OUTPUT_ROI_SELECTION;
		if (markROIMaxima)
			outputType += FindFoci.OUTPUT_MASK_ROI_SELECTION;
		if (markUsingOverlay)
			outputType += FindFoci.OUTPUT_ROI_USING_OVERLAY;
		if (hideLabels)
			outputType += FindFoci.OUTPUT_HIDE_LABELS;
		if (!showMaskMaximaAsDots)
			outputType += FindFoci.OUTPUT_MASK_NO_PEAK_DOTS;

		int options = 0;
		if (minimumAboveSaddle)
			options |= FindFoci.OPTION_MINIMUM_ABOVE_SADDLE;
		if (statisticsMode.equalsIgnoreCase("inside"))
			options |= FindFoci.OPTION_STATS_INSIDE;
		else if (statisticsMode.equalsIgnoreCase("outside"))
			options |= FindFoci.OPTION_STATS_OUTSIDE;
		if (removeEdgeMaxima)
			options |= FindFoci.OPTION_REMOVE_EDGE_MAXIMA;
		if (objectAnalysis)
		{
			options |= FindFoci.OPTION_OBJECT_ANALYSIS;
			if (showObjectMask)
				options |= FindFoci.OPTION_SHOW_OBJECT_MASK;
		}
		if (saveToMemory)
			options |= FindFoci.OPTION_SAVE_TO_MEMORY;

		if (outputType == 0)
		{
			notify(MessageType.ERROR);
			return;
		}

		ImagePlus mask = WindowManager.getImage(maskImage);

		IJ.showStatus(FindFoci.TITLE + " calculating ...");
		notify(MessageType.RUNNING);

		// Compare this model with the previously computed results and
		// only update the parts that are necessary. 
		FindFociState state = compareModels(model, previousModel);
		previousModel = null;

		//System.out.println("Updating from " + state);

		if (state.ordinal() <= FindFociState.INITIAL.ordinal())
		{
			imp2 = ff.blur(imp, gaussianBlur);
			if (imp2 == null)
			{
				IJ.showStatus(FindFoci.TITLE + " failed");
				notify(MessageType.FAILED);
				return;
			}
		}
		if (state.ordinal() <= FindFociState.FIND_MAXIMA.ordinal())
		{
			initResults = ff.findMaximaInit(imp, imp2, mask, backgroundMethod, thresholdMethod, options);
			if (initResults == null)
			{
				IJ.showStatus(FindFoci.TITLE + " failed");
				notify(MessageType.FAILED);
				return;
			}

			notify(MessageType.BACKGROUND_LEVEL, initResults.stats.background);
		}
		if (state.ordinal() <= FindFociState.SEARCH.ordinal())
		{
			searchInitResults = ff.cloneForSearch(initResults, searchInitResults);
			searchArray = ff.findMaximaSearch(searchInitResults, backgroundMethod, backgroundParameter, searchMethod,
					searchParameter);
			if (searchArray == null)
			{
				IJ.showStatus(FindFoci.TITLE + " failed");
				notify(MessageType.FAILED);
				return;
			}

			notify(MessageType.BACKGROUND_LEVEL, searchInitResults.stats.background);
		}
		if (state.ordinal() <= FindFociState.MERGE.ordinal())
		{
			mergeInitResults = ff.clone(searchInitResults, mergeInitResults);
			mergeResults = ff.findMaximaMerge(mergeInitResults, searchArray, minSize, peakMethod, peakParameter, options,
					gaussianBlur);
			if (mergeResults == null)
			{
				IJ.showStatus(FindFoci.TITLE + " failed");
				notify(MessageType.FAILED);
				return;
			}
		}
		if (state.ordinal() <= FindFociState.CALCULATE_RESULTS.ordinal())
		{
			if (initResults.stats.imageMinimum < 0 && FindFociBaseProcessor.isSortIndexSenstiveToNegativeValues(sortMethod))
				notify(MessageType.SORT_INDEX_SENSITIVE_TO_NEGATIVE_VALUES, initResults.stats.imageMinimum);
			else
				notify(MessageType.SORT_INDEX_OK, initResults.stats.imageMinimum);
			
			resultsInitResults = ff.clone(mergeInitResults, resultsInitResults);
			prelimResults = ff.findMaximaPrelimResults(resultsInitResults, mergeResults, maxPeaks, sortMethod, centreMethod,
					centreParameter);
			if (prelimResults == null)
			{
				IJ.showStatus(FindFoci.TITLE + " failed");
				notify(MessageType.FAILED);
				return;
			}
		}
		if (state.ordinal() <= FindFociState.CALCULATE_OUTPUT_MASK.ordinal())
		{
			maskInitResults = ff.clone(resultsInitResults, maskInitResults);
			results = ff.findMaximaMaskResults(maskInitResults, mergeResults, prelimResults, outputType, thresholdMethod,
					imp.getTitle(), fractionParameter);
			if (results == null)
			{
				IJ.showStatus(FindFoci.TITLE + " failed");
				notify(MessageType.FAILED);
				return;
			}
		}
		if (state.ordinal() <= FindFociState.SHOW_RESULTS.ordinal())
		{
			ff.showResults(imp, mask, backgroundMethod, backgroundParameter, thresholdMethod, searchParameter, maxPeaks,
					minSize, peakMethod, peakParameter, outputType, sortMethod, options, results);
		}

		IJ.showStatus(FindFoci.TITLE + " finished");
		notify(MessageType.DONE);

		previousModel = model;
	}

	/**
	 * Compare two models and identify the state of the calculation
	 * 
	 * @param model
	 * @param previousModel
	 * @return The state
	 */
	private FindFociState compareModels(FindFociModel model, FindFociModel previousModel)
	{
		boolean ignoreChange = false;

		if (previousModel == null || notEqual(model.getSelectedImage(), previousModel.getSelectedImage()) ||
				notEqual(model.getGaussianBlur(), previousModel.getGaussianBlur()))
		{
			return FindFociState.INITIAL;
		}

		if (notEqual(model.getBackgroundMethod(), previousModel.getBackgroundMethod()) ||
				notEqual(model.getMaskImage(), previousModel.getMaskImage()) ||
				notEqual(model.getBackgroundParameter(), previousModel.getBackgroundParameter()) ||
				notEqual(model.getThresholdMethod(), previousModel.getThresholdMethod())
		//|| notEqual(model.isShowLogMessages(), previousModel.isShowLogMessages())
		)
		{
			return FindFociState.FIND_MAXIMA;
		}

		if (notEqual(model.getSearchMethod(), previousModel.getSearchMethod()) ||
				notEqual(model.getSearchParameter(), previousModel.getSearchParameter()))
		{
			return FindFociState.SEARCH;
		}

		if (notEqual(model.getMinSize(), previousModel.getMinSize()) ||
				notEqual(model.isMinimumAboveSaddle(), previousModel.isMinimumAboveSaddle()) ||
				notEqual(model.getPeakMethod(), previousModel.getPeakMethod()) ||
				notEqual(model.getPeakParameter(), previousModel.getPeakParameter()))
		{
			return FindFociState.MERGE;
		}

		if (notEqual(model.getSortMethod(), previousModel.getSortMethod()) ||
				notEqual(model.getCentreMethod(), previousModel.getCentreMethod()) ||
				notEqual(model.getCentreParameter(), previousModel.getCentreParameter()))
		{
			return FindFociState.CALCULATE_RESULTS;
		}

		// Special case where the change is only relevant if previous model was at the limit
		if (notEqual(model.getMaxPeaks(), previousModel.getMaxPeaks()))
		{
			ArrayList<FindFociResult> resultsArrayList = results.results;
			int change = model.getMaxPeaks() - previousModel.getMaxPeaks();
			if ((change > 0 && resultsArrayList.size() >= previousModel.getMaxPeaks()) ||
					(change < 0 && resultsArrayList.size() > model.getMaxPeaks()))
			{
				//System.out.println("Updating change to maxpeaks: " + previousModel.getMaxPeaks() + " => " + model.getMaxPeaks());
				return FindFociState.CALCULATE_RESULTS;
			}
			ignoreChange = true;
			//System.out.println("Ignoring change to maxpeaks: " + previousModel.getMaxPeaks() + " => " + model.getMaxPeaks());
		}

		if (notEqual(model.getShowMask(), previousModel.getShowMask()) ||
				notEqual(model.isOverlayMask(), previousModel.isOverlayMask()))
		{
			return FindFociState.CALCULATE_OUTPUT_MASK;
		}
		if (notEqual(model.getFractionParameter(), previousModel.getFractionParameter()))
		{
			if (model.getShowMask() >= 5) // Only do this if using the fraction parameter
				return FindFociState.CALCULATE_OUTPUT_MASK;
			ignoreChange = true;
		}

		if (notEqual(model.isShowTable(), previousModel.isShowTable()) ||
				notEqual(model.isClearTable(), previousModel.isClearTable()) ||
				notEqual(model.isMarkMaxima(), previousModel.isMarkMaxima()) ||
				notEqual(model.isMarkUsingOverlay(), previousModel.isMarkUsingOverlay()) ||
				notEqual(model.isHideLabels(), previousModel.isHideLabels()) ||
				notEqual(model.isMarkROIMaxima(), previousModel.isMarkROIMaxima()))
		{
			return FindFociState.SHOW_RESULTS;
		}

		if (notEqual(model.isObjectAnalysis(), previousModel.isObjectAnalysis()))
		{
			// Only repeat if switched on. We can ignore it if the flag is switched off since the results
			// will be the same.
			if (model.isObjectAnalysis())
				return FindFociState.SHOW_RESULTS;
			ignoreChange = true;
		}

		if (notEqual(model.isShowObjectMask(), previousModel.isShowObjectMask()))
		{
			// Only repeat if the mask is to be shown
			if (model.isObjectAnalysis() && model.isShowObjectMask())
				return FindFociState.SHOW_RESULTS;
			ignoreChange = true;
		}

		if (notEqual(model.isSaveToMemory(), previousModel.isSaveToMemory()))
		{
			// Only repeat if switched on. We can ignore it if the flag is switched off since the results
			// will be the same.
			if (model.isSaveToMemory())
				return FindFociState.SHOW_RESULTS;
			ignoreChange = true;
		}

		// Options to ignore
		if (notEqual(model.isShowLogMessages(), previousModel.isShowLogMessages()) ||
				notEqual(model.getResultsDirectory(), previousModel.getResultsDirectory()) ||
				notEqual(model.isSaveResults(), previousModel.isSaveResults()))
		{
			ignoreChange = true;
		}

		if (ignoreChange)
		{
			return FindFociState.COMPLETE;
		}

		// Default to do the entire calculation
		return FindFociState.INITIAL;
	}

	private boolean notEqual(double newValue, double oldValue)
	{
		return newValue != oldValue;
	}

	private boolean notEqual(int newValue, int oldValue)
	{
		return newValue != oldValue;
	}

	private boolean notEqual(boolean newValue, boolean oldValue)
	{
		return newValue != oldValue;
	}

	private boolean notEqual(String newValue, String oldValue)
	{
		return !(oldValue != null && newValue != null && oldValue.equals(newValue));
	}

	public void finish()
	{
		notify(MessageType.BACKGROUND_LEVEL, 0.0f);
		notify(MessageType.SORT_INDEX_OK, 0.0f);
		notify(MessageType.FINISHED);
		running = false;
	}
}
