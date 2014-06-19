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

/**
 * Controls the FindFoci algorithm 
 */
public abstract class FindFociController implements Runnable
{
	/**
	 * Used to pass messages about the processing state 
	 */
	protected MessageListener listener = null;
	
	/**
	 * Contains the model defining the parameters for the FindFoci algorithm
	 */
	protected FindFociModel model;
	
	/**
	 * Constructor
	 * @param model The model for the FindFoci algorithm
	 */
	public FindFociController(FindFociModel model)
	{
		this.model = model;
	}
	
	/**
	 * Returns the number of images 
	 */
	public abstract int getImageCount();
	
	/**
	 * Updates the list of images 
	 */
	public abstract void updateImageList();
	
	/**
	 * Run the FindFoci algorithm
	 */
	public abstract void run();
	
	/**
	 * Preview the results of the FindFoci algorithm
	 */
	public abstract void preview();

	/**
	 * Ends preview the results of the FindFoci algorithm
	 */
	public abstract void endPreview();

	/**
	 * Returns the min and max of the current image 
	 * @param limits Optional input array
	 * @return array containing min and max of image (or 0,255 if no valid image)
	 */
	public abstract int[] getImageLimits(int[] limits);

	/**
	 * Adds a listener. Allows objects to be notified of processing details.
	 * @param listener
	 */
	public void addMessageListener(MessageListener listener)
	{
		this.listener = listener;
	}
}
