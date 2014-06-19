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

import gdsc.foci.model.FindFociModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Dummy controller that provides stub functionality to {@link gdsc.foci.gui.FindFociView } 
 */
public class NullController extends FindFociController
{
	private int lowerLimit = 15;
	private int upperLimit = 220;
	
	public NullController(FindFociModel model)
	{
		super(model);
	}

	/* (non-Javadoc)
	 * @see gdsc.foci.controller.FindFociController#getImageCount()
	 */
	@Override
	public int getImageCount()
	{
		return 3;
	}
	
	private int updateCounter = 0;
	
	/* (non-Javadoc)
	 * @see gdsc.foci.controller.FindFociController#updateImageList()
	 */
	public void updateImageList()
	{
		//System.out.println("updateImageList");
		
		// Note: Increment the updateCounter to ensure the list is refreshed
		//updateCounter++;
		
		List<String> imageList = new ArrayList<String>();
		imageList.add(updateCounter + " : One");
		imageList.add(updateCounter + " : Two");
		imageList.add(updateCounter + " : Three");
		model.setImageList(imageList);

		// Make up some random limits
		Random rand = new Random();
		int base = 25;
		lowerLimit = rand.nextInt(base);
		upperLimit = rand.nextInt(255 - base) + base;
	}
	
	/* (non-Javadoc)
	 * @see gdsc.foci.controller.FindFociController#run()
	 */
	public void run()
	{
		model.setUnchanged();
	}
	
	/* (non-Javadoc)
	 * @see gdsc.foci.controller.FindFociController#preview()
	 */
	public void preview()
	{
		System.out.println("FindFoci Preview");
	}
	
	/* (non-Javadoc)
	 * @see gdsc.foci.controller.FindFociController#endPreview()
	 */
	public void endPreview()
	{
		System.out.println("FindFoci EndPreview");
	}

	/* (non-Javadoc)
	 * @see gdsc.foci.controller.FindFociController#getImageLimits(int[])
	 */
	@Override
	public int[] getImageLimits(int[] limits)
	{
		//System.out.println("getImageLimits");
		if (limits == null || limits.length < 2)
		{
			limits = new int[2];
		}
		limits[0] = lowerLimit;
		limits[1] = upperLimit;
		return limits;
	}
}
