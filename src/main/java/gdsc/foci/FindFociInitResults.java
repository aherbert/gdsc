package gdsc.foci;

import gdsc.core.threshold.Histogram;
import ij.ImagePlus;

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

/**
 * Contains the results of the FindFoci algorithm after the initialisation stage.
 */
public class FindFociInitResults
{
	public Object image;
	public byte[] types;
	public int[] maxima;
	public Histogram histogram;
	public FindFociStatistics stats;
	public Object originalImage;
	public ImagePlus originalImp;

	/**
	 * Instantiates a new find foci init results.
	 *
	 * @param image the image
	 * @param types the types
	 * @param maxima the maxima
	 * @param histgram the histgram
	 * @param stats the stats
	 * @param originalImage the original image
	 * @param originalImp the original imp
	 */
	public FindFociInitResults(Object image, byte[] types, int[] maxima, Histogram histgram, FindFociStatistics stats,
			Object originalImage, ImagePlus originalImp)
	{
		this.image = image;
		this.types = types;
		this.maxima = maxima;
		this.histogram = histgram;
		this.stats = stats;
		this.originalImage = originalImage;
		this.originalImp = originalImp;
	}
}