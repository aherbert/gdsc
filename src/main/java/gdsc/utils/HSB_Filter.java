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
package gdsc.utils;

import java.awt.AWTEvent;
import java.awt.Color;

import gdsc.UsageTracker;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

/**
 * Alows an RGB image to be filtered using HSB limits.
 */
public class HSB_Filter implements ExtendedPlugInFilter, DialogListener
{
	private final int flags = DOES_RGB | SNAPSHOT;

	private static final String TITLE = "HSB Filter";

	private float[] h = null, s = null, b = null;

	// Allow to be set by others in the package

	/** The hue. */
	static float hue = 0;

	/** The hue width. */
	static float hueWidth = 1f / 6f;

	/** The saturation. */
	static float saturation = 0.5f;

	/** The saturation width. */
	static float saturationWidth = 0.5f;

	/** The brightness. */
	static float brightness = 0.5f;

	/** The brightness width. */
	static float brightnessWidth = 0.5f;

	/*
	 * (non-Javadoc)
	 *
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	@Override
	public int setup(String arg, ImagePlus imp)
	{
		UsageTracker.recordPlugin(this.getClass(), arg);

		if (imp == null)
			return DONE;
		return flags;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see ij.plugin.filter.ExtendedPlugInFilter#showDialog(ij.ImagePlus, java.lang.String,
	 * ij.plugin.filter.PlugInFilterRunner)
	 */
	@Override
	public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
	{
		final GenericDialog gd = new GenericDialog(TITLE);

		// Set-up the HSB images
		final ColorProcessor cp = (ColorProcessor) imp.getProcessor().duplicate();
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
	@Override
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
	@Override
	public void setNPasses(int nPasses)
	{
		// Do nothing
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	@Override
	public void run(ImageProcessor inputProcessor)
	{
		final float minH = hue - hueWidth;
		final float maxH = hue + hueWidth;
		final float minS = saturation - saturationWidth;
		final float maxS = saturation + saturationWidth;
		final float minB = brightness - brightnessWidth;
		final float maxB = brightness + brightnessWidth;
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
