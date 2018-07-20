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
package uk.ac.sussex.gdsc.foci.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import uk.ac.sussex.gdsc.foci.model.FindFociModel;

/**
 * Dummy controller that provides stub functionality to {@link uk.ac.sussex.gdsc.foci.gui.FindFociView}.
 */
public class NullController extends FindFociController
{
	private int lowerLimit = 15;
	private int upperLimit = 220;

	/**
	 * Instantiates a new null controller.
	 *
	 * @param model
	 *            the model
	 */
	public NullController(FindFociModel model)
	{
		super(model);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see uk.ac.sussex.gdsc.foci.controller.FindFociController#getImageCount()
	 */
	@Override
	public int getImageCount()
	{
		return 3;
	}

	private final int updateCounter = 0;

	/*
	 * (non-Javadoc)
	 *
	 * @see uk.ac.sussex.gdsc.foci.controller.FindFociController#updateImageList()
	 */
	@Override
	public void updateImageList()
	{
		//System.out.println("updateImageList");

		// Note: Increment the updateCounter to ensure the list is refreshed
		//updateCounter++;

		final List<String> imageList = new ArrayList<>();
		imageList.add(updateCounter + " : One");
		imageList.add(updateCounter + " : Two");
		imageList.add(updateCounter + " : Three");
		model.setImageList(imageList);

		// Make up some random limits
		final Random rand = new Random();
		final int base = 25;
		lowerLimit = rand.nextInt(base);
		upperLimit = rand.nextInt(255 - base) + base;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see uk.ac.sussex.gdsc.foci.controller.FindFociController#run()
	 */
	@Override
	public void run()
	{
		model.setUnchanged();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see uk.ac.sussex.gdsc.foci.controller.FindFociController#preview()
	 */
	@Override
	public void preview()
	{
		System.out.println("FindFoci Preview");
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see uk.ac.sussex.gdsc.foci.controller.FindFociController#endPreview()
	 */
	@Override
	public void endPreview()
	{
		System.out.println("FindFoci EndPreview");
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see uk.ac.sussex.gdsc.foci.controller.FindFociController#getImageLimits(int[])
	 */
	@Override
	public int[] getImageLimits(int[] limits)
	{
		//System.out.println("getImageLimits");
		if (limits == null || limits.length < 2)
			limits = new int[2];
		limits[0] = lowerLimit;
		limits[1] = upperLimit;
		return limits;
	}
}
