/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2025 Alex Herbert
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

package uk.ac.sussex.gdsc.ij.utils;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;
import java.awt.AWTEvent;
import java.util.concurrent.atomic.AtomicReference;
import uk.ac.sussex.gdsc.ij.UsageTracker;
import uk.ac.sussex.gdsc.ij.foci.ObjectEroder;
import uk.ac.sussex.gdsc.ij.foci.ObjectExpander;

/**
 * Creates a border around each object in a mask image.
 */
public class MaskToBorder_PlugIn implements ExtendedPlugInFilter, DialogListener {
  private static final String TITLE = "Mask to Border";
  private static final int FLAGS = DOES_8G | DOES_16;

  /** The current settings for the plugin instance. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    int outer = 0;
    int inner = 1;
    boolean extend;

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
      outer = source.outer;
      inner = source.inner;
      extend = source.extend;
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
      IJ.noImage();
      return DONE;
    }

    settings = Settings.load();
    settings.save();

    return FLAGS;
  }

  /** {@inheritDoc} */
  @Override
  public void run(ImageProcessor ip) {
    createBorder(ip);
  }

  /** {@inheritDoc} */
  @Override
  public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
    final ImageProcessor ip = imp.getProcessor();
    ip.snapshot();

    final GenericDialog gd = new GenericDialog(TITLE);
    gd.addMessage("Create a border around mask objects");

    gd.addSlider("inner", 0, 10, settings.inner);
    gd.addSlider("outer", 0, 10, settings.outer);
    gd.addCheckbox("extend_outside", settings.extend);

    gd.addPreviewCheckbox(pfr);
    gd.addDialogListener(this);
    gd.addHelp(uk.ac.sussex.gdsc.ij.help.Urls.UTILITY);
    gd.showDialog();

    final boolean cancelled = gd.wasCanceled() || !dialogItemChanged(gd, null);
    if (cancelled) {
      return DONE;
    }

    return IJ.setupDialog(imp, FLAGS);
  }

  /** Listener to modifications of the input fields of the dialog. */
  @Override
  public boolean dialogItemChanged(GenericDialog gd, AWTEvent event) {
    settings.inner = (int) gd.getNextNumber();
    if (gd.invalidNumber()) {
      return false;
    }
    settings.outer = (int) gd.getNextNumber();
    if (gd.invalidNumber()) {
      return false;
    }
    settings.extend = gd.getNextBoolean();
    return settings.inner > 0 || settings.outer > 0;
  }

  /** {@inheritDoc} */
  @Override
  public void setNPasses(int passes) {
    // Ignore
  }

  private void createBorder(ImageProcessor ip) {
    // Test creating a ring around each object
    final ImageProcessor ip1 = ip.duplicate();
    final ImageProcessor ip2 = ip.duplicate();
    new ObjectExpander(ip1).expand(settings.outer);
    new ObjectEroder(ip2, settings.extend).erode(settings.inner);
    for (int i = ip.getPixelCount(); i-- > 0;) {
      if (ip1.get(i) > ip2.get(i)) {
        ip.set(i, ip1.get(i));
      } else {
        ip.set(i, 0);
      }
    }
  }
}
