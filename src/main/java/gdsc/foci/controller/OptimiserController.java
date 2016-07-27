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
import gdsc.foci.FindFociOptimiser;
import gdsc.foci.model.FindFociModel;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.plugin.frame.Recorder;

import java.util.ArrayList;
import java.util.List;

/**
 * Allows ImageJ to run the {@link gdsc.foci.FindFoci } algorithm
 */
public class OptimiserController extends FindFociController implements Runnable
{
	private FindFociOptimiser optimiser = new FindFociOptimiser();

	public OptimiserController(FindFociModel model)
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
		return 0;
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
		for (int id : gdsc.core.ij.Utils.getIDList())
		{
			ImagePlus imp = WindowManager.getImage(id);

			// Image must be 8-bit/16-bit/32-bit && only contains XYZ dimensions
			if (imp != null && (imp.getType() == ImagePlus.GRAY8 || imp.getType() == ImagePlus.GRAY16 ||
					imp.getType() == ImagePlus.GRAY32) && (imp.getNChannels() == 1 && imp.getNFrames() == 1))
			{
				Roi roi = imp.getRoi();
				if (roi == null || roi.getType() != Roi.POINT)
					continue;

				// Check it is not one the result images
				String imageTitle = imp.getTitle();
				if (!imageTitle.endsWith(FindFoci.TITLE) && !imageTitle.endsWith("clone") &&
						!imageTitle.endsWith(" TP") && !imageTitle.endsWith(" FP") && !imageTitle.endsWith(" FN"))
				{
					imageList.add(imageTitle);
				}
			}
		}

		model.setImageList(imageList);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.controller.FindFociController#run()
	 */
	@Override
	public void run()
	{
		ImagePlus imp = WindowManager.getImage(model.getSelectedImage());

		// Ensure the optimiser is recorded by the Macro recorder
		Recorder.setCommand("FindFoci Optimiser");
		optimiser.run(imp);
		Recorder.saveCommand();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.controller.FindFociController#run()
	 */
	@Override
	public void preview()
	{
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.controller.FindFociController#endPreview()
	 */
	public void endPreview()
	{
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.foci.controller.FindFociController#getImageLimits(int[])
	 */
	@Override
	public int[] getImageLimits(int[] limits)
	{
		return null;
	}
}
