package gdsc.foci.controller;

import gdsc.UsageTracker;

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

import gdsc.foci.FindFoci;
import gdsc.foci.model.FindFociModel;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.frame.Recorder;
import ij.process.ImageStatistics;

import java.util.ArrayList;
import java.util.List;

/**
 * Allows ImageJ to run the {@link gdsc.foci.FindFoci } algorithm
 */
public class ImageJController extends FindFociController
{
	private FindFociRunner runner = null;

	public ImageJController(FindFociModel model)
	{
		super(model);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.controller.FindFociController#getImageCount()
	 */
	@Override
	public int getImageCount()
	{
		return WindowManager.getImageCount();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.controller.FindFociController#updateImageList()
	 */
	@Override
	public void updateImageList()
	{
		int noOfImages = WindowManager.getImageCount();
		List<String> imageList = new ArrayList<String>(noOfImages);
		for (int id : gdsc.utils.ImageJHelper.getIDList())
		{
			ImagePlus imp = WindowManager.getImage(id);

			// Image must be 8-bit/16-bit
			if (imp != null && (imp.getType() == ImagePlus.GRAY8 || imp.getType() == ImagePlus.GRAY16))
			{
				// Check it is not one the result images
				String imageTitle = imp.getTitle();
				if (!imageTitle.endsWith(FindFoci.TITLE))
				{
					imageList.add(imageTitle);
				}
			}
		}

		model.setImageList(imageList);

		// Get the selected image
		String title = model.getSelectedImage();
		ImagePlus imp = WindowManager.getImage(title);

		model.setMaskImageList(FindFoci.buildMaskList(imp));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.controller.FindFociController#run()
	 */
	@Override
	public void run()
	{
		// Get the selected image
		String title = model.getSelectedImage();
		ImagePlus imp = WindowManager.getImage(title);
		if (null == imp)
		{
			return;
		}

		if (imp.getBitDepth() != 8 && imp.getBitDepth() != 16)
		{
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
		boolean showLogMessages = model.isShowLogMessages();
		boolean removeEdgeMaxima = model.isRemoveEdgeMaxima();
		boolean saveResults = model.isSaveResults();
		String resultsDirectory = model.getResultsDirectory();
		boolean objectAnalysis = model.isObjectAnalysis();
		boolean saveToMemory = model.isSaveToMemory();
		boolean showObjectMask = model.isShowObjectMask();
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
		if (showLogMessages)
			outputType += FindFoci.OUTPUT_LOG_MESSAGES;

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
			IJ.showMessage("Error", "No results options chosen");
			return;
		}

		// Record the macro command
		if (Recorder.record)
		{
			// These options should match the parameter names assigned within the FindFoci GenericDialog.
			Recorder.setCommand("FindFoci");
			Recorder.recordOption("Mask", maskImage);
			Recorder.recordOption("Background_method", FindFoci.backgroundMethods[backgroundMethod]);
			Recorder.recordOption("Background_parameter", "" + backgroundParameter);
			Recorder.recordOption("Auto_threshold", thresholdMethod);
			Recorder.recordOption("Statistics_mode", statisticsMode);
			Recorder.recordOption("Search_method", FindFoci.searchMethods[searchMethod]);
			Recorder.recordOption("Search_parameter", "" + searchParameter);
			Recorder.recordOption("Minimum_size", "" + minSize);
			if (minimumAboveSaddle)
				Recorder.recordOption("Minimum_above_saddle");
			Recorder.recordOption("Minimum_peak_height", FindFoci.peakMethods[peakMethod]);
			Recorder.recordOption("Peak_parameter", "" + peakParameter);
			Recorder.recordOption("Sort_method", FindFoci.sortIndexMethods[sortMethod]);
			Recorder.recordOption("Maximum_peaks", "" + maxPeaks);
			Recorder.recordOption("Show_mask", FindFoci.maskOptions[showMask]);
			if (overlayMask)
				Recorder.recordOption("Overlay_mask");
			Recorder.recordOption("Fraction_parameter", "" + fractionParameter);
			if (showTable)
				Recorder.recordOption("Show_table");
			if (clearTable)
				Recorder.recordOption("Clear_table");
			if (markMaxima)
				Recorder.recordOption("Mark_maxima");
			if (markROIMaxima)
				Recorder.recordOption("Mark_peak_maxima");
			if (markUsingOverlay)
				Recorder.recordOption("Mark_using_overlay");
			if (hideLabels)
				Recorder.recordOption("Hide_labels");
			if (showMaskMaximaAsDots)
				Recorder.recordOption("Show_peak_maxima_as_dots");
			if (showLogMessages)
				Recorder.recordOption("Show_log_messages");
			if (removeEdgeMaxima)
				Recorder.recordOption("Remove_edge_maxima");
			if (saveResults)
				Recorder.recordOption("Results_directory", resultsDirectory);
			if (objectAnalysis)
			{
				Recorder.recordOption("Object_analysis");
				if (showObjectMask)
					Recorder.recordOption("Show_object_mask");
			}
			if (saveToMemory)
				Recorder.recordOption("Save_to_memory");
			Recorder.recordOption("Gaussian_blur", "" + gaussianBlur);
			Recorder.recordOption("Centre_method", FindFoci.getCentreMethods()[centreMethod]);
			Recorder.recordOption("Centre_parameter", "" + centreParameter);
			Recorder.saveCommand();
		}

		model.setUnchanged();

		ImagePlus mask = WindowManager.getImage(maskImage);

		// Run the plugin
		UsageTracker.recordPlugin(FindFoci.class, "");
		FindFoci ff = new FindFoci();
		if (saveResults)
			ff.setResultsDirectory(resultsDirectory);
		ff.exec(imp, mask, backgroundMethod, backgroundParameter, thresholdMethod, searchMethod, searchParameter,
				maxPeaks, minSize, peakMethod, peakParameter, outputType, sortMethod, options, gaussianBlur,
				centreMethod, centreParameter, fractionParameter);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.controller.FindFociController#run()
	 */
	@Override
	public void preview()
	{
		// Get the selected image
		String title = model.getSelectedImage();
		ImagePlus imp = WindowManager.getImage(title);
		if (null == imp)
		{
			return;
		}

		if (imp.getBitDepth() != 8 && imp.getBitDepth() != 16)
		{
			return;
		}

		startRunner();

		runner.queue(model);
	}

	private void startRunner()
	{
		if (runner == null || !runner.isAlive())
		{
			runner = new FindFociRunner(this.listener);
			runner.start();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.controller.FindFociController#endPreview()
	 */
	public void endPreview()
	{
		if (runner != null)
		{
			runner.finish();
			runner.interrupt();
			runner = null;
		}
		System.gc();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.controller.FindFociController#getImageLimits(int[])
	 */
	@Override
	public int[] getImageLimits(int[] limits)
	{
		if (limits == null || limits.length < 2)
		{
			limits = new int[2];
		}
		ImagePlus imp = WindowManager.getImage(model.getSelectedImage());
		if (imp != null)
		{
			ImageStatistics stats = imp.getStatistics(ij.measure.Measurements.MIN_MAX);
			limits[0] = (int) stats.min;
			limits[1] = (int) stats.max;
		}
		else
		{
			limits[0] = 0;
			limits[1] = 255;
		}
		return limits;
	}
}
