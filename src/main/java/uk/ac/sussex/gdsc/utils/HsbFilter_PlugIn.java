/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2020 Alex Herbert
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

import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import java.awt.AWTEvent;
import java.awt.Color;
import java.util.concurrent.atomic.AtomicReference;
import uk.ac.sussex.gdsc.UsageTracker;

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

  /** The current settings for the plugin instance. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    /** The hue. */
    float hue = 0;

    /** The hue width. */
    float hueWidth = 1f / 6f;

    /** The saturation. */
    float saturation = 0.5f;

    /** The saturation width. */
    float saturationWidth = 0.5f;

    /** The brightness. */
    float brightness = 0.5f;

    /** The brightness width. */
    float brightnessWidth = 0.5f;

    /**
     * Default constructor.
     */
    Settings() {
      // Do nothing
    }

    /**
     * Copy constructor.
     *
     * @param source the source
     */
    private Settings(Settings source) {
      hue = source.hue;
      hueWidth = source.hueWidth;
      saturation = source.saturation;
      saturationWidth = source.saturationWidth;
      brightness = source.brightness;
      brightnessWidth = source.brightnessWidth;
    }

    /**
     * Copy the settings.
     *
     * @return the settings
     */
    Settings copy() {
      return new Settings(this);
    }

    /**
     * Load a copy of the settings.
     *
     * @return the settings
     */
    static Settings load() {
      return lastSettings.get().copy();
    }

    /**
     * Save the settings.
     */
    void save() {
      lastSettings.set(this);
    }
  }

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
    settings = Settings.load();
    final GenericDialog gd = new GenericDialog(TITLE);

    // Set-up the HSB images
    final ColorProcessor cp = (ColorProcessor) imp.getProcessor().duplicate();
    getHsb((int[]) cp.getPixels());

    gd.addSlider("Hue", 0.01, 1, settings.hue);
    gd.addSlider("Hue_width", 0.01, 1, settings.hueWidth);
    gd.addSlider("Saturation", 0.01, 1, settings.saturation);
    gd.addSlider("Saturation_width", 0.01, 1, settings.saturationWidth);
    gd.addSlider("Brightness", 0.01, 1, settings.brightness);
    gd.addSlider("Brightness_width", 0.01, 1, settings.brightnessWidth);

    gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.UTILITY);

    gd.addPreviewCheckbox(pfr);
    gd.addDialogListener(this);
    gd.showDialog();

    final boolean cancelled = gd.wasCanceled() || !dialogItemChanged(gd, null);
    settings.save();
    return (cancelled) ? DONE : FLAGS;
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
    settings.hue = (float) gd.getNextNumber();
    settings.hueWidth = (float) gd.getNextNumber();
    settings.saturation = (float) gd.getNextNumber();
    settings.saturationWidth = (float) gd.getNextNumber();
    settings.brightness = (float) gd.getNextNumber();
    settings.brightnessWidth = (float) gd.getNextNumber();
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
    final float minH = settings.hue - settings.hueWidth;
    final float maxH = settings.hue + settings.hueWidth;
    final float minS = settings.saturation - settings.saturationWidth;
    final float maxS = settings.saturation + settings.saturationWidth;
    final float minB = settings.brightness - settings.brightnessWidth;
    final float maxB = settings.brightness + settings.brightnessWidth;
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

  /**
   * Save the settings.
   *
   * @param hue the hue
   * @param hueWidth the hue width
   * @param saturation the saturation
   * @param saturationWidth the saturation width
   * @param brightness the brightness
   * @param brightnessWidth the brightness width
   */
  static void saveSettings(float hue, float hueWidth, float saturation, float saturationWidth,
      float brightness, float brightnessWidth) {
    final Settings newSettings = new Settings();
    newSettings.hue = hue;
    newSettings.hueWidth = hueWidth;
    newSettings.saturation = saturation;
    newSettings.saturationWidth = saturationWidth;
    newSettings.brightness = brightness;
    newSettings.brightnessWidth = brightnessWidth;
    newSettings.save();
  }
}
