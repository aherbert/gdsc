package gdsc.utils;

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

import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

import java.awt.AWTEvent;
import java.awt.Color;

import gdsc.PluginTracker;

/**
 * Alows an RGB image to be filtered using HSB limits.
 */
public class HSB_Filter implements ExtendedPlugInFilter, DialogListener
{
	private int flags = DOES_RGB | SNAPSHOT;

	private static final String TITLE = "HSB Filter";

	private float[] h = null, s = null, b = null;

	// Allow to be set by others in the package
	static float hue = 0;
	static float hueWidth = 1f / 6f;
	static float saturation = 0.5f;
	static float saturationWidth = 0.5f;
	static float brightness = 0.5f;
	static float brightnessWidth = 0.5f;

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	public int setup(String arg, ImagePlus imp)
	{
		PluginTracker.recordPlugin(this.getClass(), arg);
		
		if (imp == null)
		{
			return DONE;
		}
		return flags;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.ExtendedPlugInFilter#showDialog(ij.ImagePlus, java.lang.String,
	 * ij.plugin.filter.PlugInFilterRunner)
	 */
	public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
	{
		GenericDialog gd = new GenericDialog(TITLE);

		// Set-up the HSB images
		ColorProcessor cp = (ColorProcessor) imp.getProcessor().duplicate();
		getHSB((int[]) cp.getPixels());

		gd.addSlider("Hue", 0.01, 1, hue);
		gd.addSlider("Hue_width", 0.01, 1, hueWidth);
		gd.addSlider("Saturation", 0.01, 1, saturation);
		gd.addSlider("Saturation_width", 0.01, 1, saturationWidth);
		gd.addSlider("Brightness", 0.01, 1, brightness);
		gd.addSlider("Brightness_width", 0.01, 1, brightnessWidth);

		gd.addHelp(gdsc.help.URL.UTILITY);

		gd.addPreviewCheckbox(pfr);
		gd.addDialogListener(this);
		gd.showDialog();

		if (gd.wasCanceled() || !dialogItemChanged(gd, null))
			return DONE;

		return flags;
	}

	private void getHSB(int[] pixels)
	{
		int c, rr, gg, bb;
		float[] hsb = new float[3];
		h = new float[pixels.length];
		s = new float[pixels.length];
		b = new float[pixels.length];
		for (int i = 0; i < pixels.length; i++)
		{
			c = pixels[i];
			rr = (c & 0xff0000) >> 16;
			gg = (c & 0xff00) >> 8;
			bb = c & 0xff;
			hsb = Color.RGBtoHSB(rr, gg, bb, hsb);
			h[i] = hsb[0];
			s[i] = hsb[1];
			b[i] = hsb[2];
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.gui.DialogListener#dialogItemChanged(ij.gui.GenericDialog, java.awt.AWTEvent)
	 */
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e)
	{
		hue = (float) gd.getNextNumber();
		hueWidth = (float) gd.getNextNumber();
		saturation = (float) gd.getNextNumber();
		saturationWidth = (float) gd.getNextNumber();
		brightness = (float) gd.getNextNumber();
		brightnessWidth = (float) gd.getNextNumber();
		return !gd.invalidNumber();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.ExtendedPlugInFilter#setNPasses(int)
	 */
	public void setNPasses(int nPasses)
	{
		// Do nothing		
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	public void run(ImageProcessor inputProcessor)
	{
		final float minH = (float) (hue - hueWidth);
		final float maxH = (float) (hue + hueWidth);
		final float minS = (float) (saturation - saturationWidth);
		final float maxS = (float) (saturation + saturationWidth);
		final float minB = (float) (brightness - brightnessWidth);
		final float maxB = (float) (brightness + brightnessWidth);
		for (int i = 0; i < s.length; i++)
		{
			float hh = h[i];
			final float ss = s[i];
			final float bb = b[i];

			if (hh < minH) // Hue wraps around 0-1 values
				hh += 1;
			if (hh > maxH)
			{
				inputProcessor.set(i, 0);
				continue;
			}
			if (ss < minS || ss > maxS)
			{
				inputProcessor.set(i, 0);
				continue;
			}
			if (bb < minB || bb > maxB)
			{
				inputProcessor.set(i, 0);
				continue;
			}
		}
	}
}
