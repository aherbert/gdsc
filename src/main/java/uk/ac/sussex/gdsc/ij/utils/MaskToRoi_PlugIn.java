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

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import java.util.BitSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import uk.ac.sussex.gdsc.core.ij.SimpleImageJTrackProgress;
import uk.ac.sussex.gdsc.ij.UsageTracker;

/**
 * Traces each object in a mask image.
 */
public class MaskToRoi_PlugIn implements PlugInFilter {
  private static final String TITLE = "Mask to Roi";
  private ImagePlus imp;
  /** The current settings for the plugin instance. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    boolean eightConnected = true;
    boolean stack = true;

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
      eightConnected = source.eightConnected;
      stack = source.stack;
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

    this.imp = imp;
    if (!showDialog()) {
      return DONE;
    }
    return DOES_8G | DOES_16;
  }

  private boolean showDialog() {
    settings = Settings.load();

    final GenericDialog gd = new GenericDialog(TITLE);
    gd.addMessage("Trace all mask objects and add to the ROI Manager");
    final boolean isStack = imp.getNDimensions() > 2;
    gd.addCheckbox("8-connected", settings.eightConnected);
    if (isStack) {
      gd.addCheckbox("Stack", settings.stack);
    }
    gd.showDialog();
    if (gd.wasCanceled()) {
      return false;
    }

    settings.eightConnected = gd.getNextBoolean();
    if (isStack) {
      settings.stack = gd.getNextBoolean();
    }
    settings.save();

    return true;
  }

  /** {@inheritDoc} */
  @Override
  public void run(ImageProcessor ip) {
    if (imp.getNDimensions() > 2 && settings.stack) {
      final int nc = imp.getNChannels();
      final int nz = imp.getNSlices();
      final int nt = imp.getNFrames();
      final ImageStack stack = imp.getImageStack();
      final SimpleImageJTrackProgress progress = SimpleImageJTrackProgress.getInstance();
      final int total = stack.getSize();
      int count = 0;
      for (int c = 1; c <= nc; c++) {
        for (int z = 1; z <= nz; z++) {
          for (int t = 1; t <= nt; t++) {
            progress.progress(count++, total);
            // 1-based stack index
            // See ImagePlus.getStackIndex(c, z, t);
            final int i = (t - 1) * nc * nz + (z - 1) * nc + c;
            traceObjects(stack.getProcessor(i), createAction(c, z, t, i));
          }
        }
      }
      progress.progress(1);
    } else {
      traceObjects(ip, createAction(imp.getC(), imp.getZ(), imp.getT(), imp.getCurrentSlice()));
    }
  }

  private Consumer<Roi> createAction(int c, int z, int t, int index) {
    Consumer<Roi> action;

    // Set ROI position
    if (imp.isHyperStack()) {
      action = r -> r.setPosition(c, z, t);
    } else if (imp.getNDimensions() > 2) {
      action = r -> r.setPosition(index);
    } else {
      action = r -> {
      };
    }

    // Add to ROI manager
    new RoiManager();
    return action.andThen(RoiManager.getInstance()::addRoi);
  }

  private void traceObjects(ImageProcessor ip, Consumer<Roi> action) {
    final BitSet processed = new BitSet();

    final Wand wand = new Wand(ip);
    final int mode = settings.eightConnected ? Wand.EIGHT_CONNECTED : Wand.FOUR_CONNECTED;

    int index = 0;
    final int size = ip.getPixelCount();
    while (index < size) {
      // Scan for next object
      while (index < size && ip.get(index) == 0) {
        index++;
      }
      if (index == size) {
        break;
      }
      final int value = ip.get(index);
      if (!processed.get(value)) {
        processed.set(value);
        wand.autoOutline(index % ip.getWidth(), index / ip.getWidth(), 0.0, mode);
        action.accept(new PolygonRoi(wand.xpoints, wand.ypoints, wand.npoints, Roi.TRACED_ROI));
      }
      // Skip this processed object
      index++;
      while (index < size && ip.get(index) == value) {
        index++;
      }
    }
  }
}
