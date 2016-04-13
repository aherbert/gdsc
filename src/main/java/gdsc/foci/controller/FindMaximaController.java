package gdsc.foci.controller;

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
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.process.ImageProcessor;

import java.util.ArrayList;
import java.util.List;

/**
 * Allows ImageJ to run the {@link gdsc.foci.FindFoci } algorithm and return the results.
 */
public class FindMaximaController extends ImageJController
{
	private ArrayList<int[]> resultsArray = new ArrayList<int[]>();
	private ImageStack activeImageStack = null;
	private int activeChannel = 1;
	private int activeFrame = 1;

	public FindMaximaController(FindFociModel model)
	{
		super(model);
	}

	/*
	 * Allow N-Dimensional images. The controller can detect this and prompt the user for the desired channel and frame
	 * for analysis.
	 * 
	 * @see gdsc.foci.controller.ImageJController#updateImageList()
	 */
	@Override
	public void updateImageList()
	{
		int noOfImages = WindowManager.getImageCount();
		List<String> imageList = new ArrayList<String>(noOfImages);
		for (int id : gdsc.core.ij.Utils.getIDList())
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
	 * Allow N-dimensional images. Prompt for the channel and frame.
	 * 
	 * @see gdsc.foci.controller.FindFociController#run()
	 */
	@Override
	public void run()
	{
		resultsArray = new ArrayList<int[]>();

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

		// Allow N-dimensional images. Prompt for the channel and frame.
		if (imp.getNChannels() != 1 || imp.getNFrames() != 1)
		{
			GenericDialog gd = new GenericDialog("Select Channel/Frame");
			gd.addMessage("Stack detected.\nPlease select the channel/frame.");
			String[] channels = getChannels(imp);
			String[] frames = getFrames(imp);

			if (channels.length > 1)
				gd.addChoice("Channel", channels, channels[channels.length >= activeChannel ? activeChannel - 1 : 0]);
			if (frames.length > 1)
				gd.addChoice("Frame", frames, frames[frames.length >= activeFrame ? activeFrame - 1 : 0]);

			gd.showDialog();

			if (gd.wasCanceled())
				return;

			// Extract the channel/frame
			activeChannel = (channels.length > 1) ? gd.getNextChoiceIndex() + 1 : 1;
			activeFrame = (frames.length > 1) ? gd.getNextChoiceIndex() + 1 : 1;

			activeImageStack = extractImage(imp, activeChannel, activeFrame);
			imp = new ImagePlus("", activeImageStack);
		}
		else
		{
			activeImageStack = imp.getStack();
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
		boolean showLogMessages = model.isShowLogMessages();
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
		if (showLogMessages)
			outputType += FindFoci.OUTPUT_LOG_MESSAGES;

		int options = 0;
		if (minimumAboveSaddle)
			options |= FindFoci.OPTION_MINIMUM_ABOVE_SADDLE;
		if (statisticsMode.equalsIgnoreCase("inside"))
			options |= FindFoci.OPTION_STATS_INSIDE;
		else if (statisticsMode.equalsIgnoreCase("outside"))
			options |= FindFoci.OPTION_STATS_OUTSIDE;

		if (outputType == 0)
		{
			return;
		}

		model.setUnchanged();

		ImagePlus mask = WindowManager.getImage(maskImage);

		FindFoci fp = new FindFoci();
		Object[] results = fp.findMaxima(imp, mask, backgroundMethod, backgroundParameter, thresholdMethod,
				searchMethod, searchParameter, maxPeaks, minSize, peakMethod, peakParameter, outputType, sortMethod,
				options, gaussianBlur, centreMethod, centreParameter, fractionParameter);

		if (results != null)
		{
			@SuppressWarnings("unchecked")
			ArrayList<int[]> newResultsArray = (ArrayList<int[]>) results[1];

			if (newResultsArray != null)
				resultsArray = newResultsArray;
		}
	}

	private String[] getChannels(ImagePlus imp)
	{
		int c = imp.getNChannels();
		String[] result = new String[c];
		for (int i = 0; i < c; i++)
			result[i] = Integer.toString(i + 1);
		return result;
	}

	private String[] getFrames(ImagePlus imp)
	{
		int c = imp.getNFrames();
		String[] result = new String[c];
		for (int i = 0; i < c; i++)
			result[i] = Integer.toString(i + 1);
		return result;
	}

	private ImageStack extractImage(ImagePlus imp, int channel, int frame)
	{
		int slices = imp.getNSlices();

		ImageStack stack = new ImageStack(imp.getWidth(), imp.getHeight());
		ImageStack inputStack = imp.getImageStack();

		for (int slice = 1; slice <= slices; slice++)
		{
			// Convert to a short processor
			ImageProcessor ip = inputStack.getProcessor(imp.getStackIndex(channel, slice, frame));
			stack.addSlice(null, ip);
		}
		return stack;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.controller.FindFociController#run()
	 */
	@Override
	public void preview()
	{
		// Do nothing
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.controller.FindFociController#endPreview()
	 */
	public void endPreview()
	{
		// Do nothing
	}

	/**
	 * @return the resultsArray
	 */
	public ArrayList<int[]> getResultsArray()
	{
		return resultsArray;
	}

	/**
	 * Gets the active image stack used within the find foci algorithm. This will be the sub-image stack if an
	 * N-dimensional image was processed.
	 * 
	 * @return The active image stack
	 */
	public ImageStack getActiveImageStack()
	{
		return activeImageStack;
	}

	/**
	 * @return the activeChannel
	 */
	public int getActiveChannel()
	{
		return activeChannel;
	}

	/**
	 * @return the activeFrame
	 */
	public int getActiveFrame()
	{
		return activeFrame;
	}
}
