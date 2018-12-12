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

package uk.ac.sussex.gdsc.utils;

import uk.ac.sussex.gdsc.UsageTracker;

import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

import java.awt.AWTEvent;
import java.awt.Color;

/**
 * Allows an RGB image to be filtered using HSB limits.
 */
public class HsbFilter_PlugIn implements ExtendedPlugInFilter, DialogListener {
  private static final int FLAGS = DOES_RGB | SNAPSHOT;

  /** The plugin title. */
  static final String TITLE = "HSB Filter";

  private float[] hues;
  private float[] saturations;
  private float[] brightnesses;

  // Allow to be set by others in the package

  /** The hue. */
  static float hue;

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

  /** {@inheritDoc} */
  @Override
  public int setup(String arg, ImagePlus imp) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    if (imp == null) {
      return DONE;
    }
    return FLAGS;
  }

  /** {@inheritDoc} */
  @Override
  public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
    final GenericDialog gd = new GenericDialog(TITLE);

    // Set-up the HSB images
    final ColorProcessor cp = (ColorProcessor) imp.getProcessor().duplicate();
    getHsb((int[]) cp.getPixels());

    gd.addSlider("Hue", 0.01, 1, hue);
    gd.addSlider("Hue_width", 0.01, 1, hueWidth);
    gd.addSlider("Saturation", 0.01, 1, saturation);
    gd.addSlider("Saturation_width", 0.01, 1, saturationWidth);
    gd.addSlider("Brightness", 0.01, 1, brightness);
    gd.addSlider("Brightness_width", 0.01, 1, brightnessWidth);

    gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.UTILITY);

    gd.addPreviewCheckbox(pfr);
    gd.addDialogListener(this);
    gd.showDialog();

    if (gd.wasCanceled() || !dialogItemChanged(gd, null)) {
      return DONE;
    }

    return FLAGS;
  }

  private void getHsb(int[] pixels) {
    int rr;
    int gg;
    int bb;
    float[] hsb = new float[3];
    hues = new float[pixels.length];
    saturations = new float[pixels.length];
    brightnesses = new float[pixels.length];
    for (int i = 0; i < pixels.length; i++) {
      final int value = pixels[i];
      rr = (value & 0xff0000) >> 16;
      gg = (value & 0xff00) >> 8;
      bb = value & 0xff;
      hsb = Color.RGBtoHSB(rr, gg, bb, hsb);
      hues[i] = hsb[0];
      saturations[i] = hsb[1];
      brightnesses[i] = hsb[2];
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean dialogItemChanged(GenericDialog gd, AWTEvent event) {
    hue = (float) gd.getNextNumber();
    hueWidth = (float) gd.getNextNumber();
    saturation = (float) gd.getNextNumber();
    saturationWidth = (float) gd.getNextNumber();
    brightness = (float) gd.getNextNumber();
    brightnessWidth = (float) gd.getNextNumber();
    return !gd.invalidNumber();
  }

  /** {@inheritDoc} */
  @Override
  public void setNPasses(int passes) {
    // Do nothing
  }

  /** {@inheritDoc} */
  @Override
  public void run(ImageProcessor inputProcessor) {
    final float minH = hue - hueWidth;
    final float maxH = hue + hueWidth;
    final float minS = saturation - saturationWidth;
    final float maxS = saturation + saturationWidth;
    final float minB = brightness - brightnessWidth;
    final float maxB = brightness + brightnessWidth;
    for (int i = 0; i < saturations.length; i++) {
      float hh = hues[i];
      final float ss = saturations[i];
      final float bb = brightnesses[i];

      // Hue wraps around from 0 to 1 so if below the limit check it is above the limit
      // after wrapping
      if (hh < minH) {
        hh += 1;
      }
      if ((hh > maxH) || (ss < minS || ss > maxS) || (bb < minB || bb > maxB)) {
        inputProcessor.set(i, 0);
      }
    }
  }
}
