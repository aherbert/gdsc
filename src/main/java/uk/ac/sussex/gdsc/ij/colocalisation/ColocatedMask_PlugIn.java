/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2022 Alex Herbert
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

package uk.ac.sussex.gdsc.ij.colocalisation;

import ij.CompositeImage;
import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.plugin.frame.Recorder;
import ij.process.ImageProcessor;
import ij.process.LUT;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import java.awt.Checkbox;
import java.awt.Choice;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import uk.ac.sussex.gdsc.core.ij.ImageAdapter;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.ij.gui.NonBlockingExtendedGenericDialog;
import uk.ac.sussex.gdsc.core.utils.SettingsList;
import uk.ac.sussex.gdsc.ij.UsageTracker;

/**
 * Create a colocated mask from two source images using the threshold or minimum display value from
 * each image to define their image mask. The two image masks are combined using an AND or OR
 * operator.
 */
public class ColocatedMask_PlugIn implements PlugIn {
  private static final String TITLE = "Colocated Mask";

  private static final String[] MASK_MODE = {"Threshold", "Minimum display value"};
  private static final String[] COLOCATED_MODE = {"And", "Or"};

  private Checkbox preview;
  private int id1;
  private int id2;

  /**
   * A list of all the settings used for the last analysis. This is used in the analysis preview to
   * determine if any work should be done.
   */
  private SettingsList lastSettingsList;

  /** The current settings for the plugin instance. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    String selectedImage1 = "";
    String selectedImage2 = "";
    int maskMode;
    int colocatedMode;

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
      selectedImage1 = source.selectedImage1;
      selectedImage2 = source.selectedImage2;
      maskMode = source.maskMode;
      colocatedMode = source.colocatedMode;
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

  /**
   * Simple class to maintain a synchronized state of clean/dirty.
   */
  private static class Flag {
    boolean isDirty;

    /**
     * Dirty the flag.
     */
    synchronized void dirty() {
      isDirty = true;
      notifyAll();
    }

    /**
     * Clean the flag.
     */
    synchronized void clean() {
      isDirty = false;
      // notifyAll(); Nothing listens for a clean state
    }

    /**
     * Checks if is clean.
     *
     * @return true, if is clean
     */
    synchronized boolean isClean() {
      return !isDirty;
    }
  }

  private Flag flag;

  private class Worker implements Runnable {
    volatile boolean stop;
    final Flag flag;

    Worker(Flag flag) {
      this.flag = flag;
    }

    @Override
    public void run() {
      while (!stop) {
        try {
          synchronized (flag) {
            if (flag.isClean()) {
              // Check for shutdown signal
              if (stop) {
                break;
              }
              // Wait for changes
              flag.wait();
            }
            // Clean the flag since we are about to do the work
            flag.clean();
          }

          createMask(true);
        } catch (final InterruptedException ex) {
          Thread.currentThread().interrupt();
          stop = true;
        }
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);
    if (WindowManager.getImageCount() == 0) {
      IJ.noImage();
    } else {
      showDialog();
    }
  }

  private boolean showDialog() {
    settings = Settings.load();
    final String[] list = ImageJUtils.getImageList(0, new String[] {TITLE});

    final NonBlockingExtendedGenericDialog gd = new NonBlockingExtendedGenericDialog(TITLE);
    gd.addMessage("Create a mask from 2 images.\nImages must match XY dimensions.");
    final Choice c1 = gd.addAndGetChoice("Image_1", list, settings.selectedImage1);
    final Choice c2 = gd.addAndGetChoice("Image_2", list, settings.selectedImage2);
    final boolean dynamic = ImageJUtils.isShowGenericDialog();
    Worker worker = null;
    Thread thread = null;
    if (dynamic) {
      // Make sure the two images are different
      if (c1.getSelectedIndex() == c2.getSelectedIndex() && list.length > 1) {
        c2.select((c1.getSelectedIndex() + 1) % list.length);
      }

      settings.selectedImage1 = c1.getSelectedItem();
      settings.selectedImage2 = c2.getSelectedItem();
      id1 = getId(settings.selectedImage1);
      id2 = getId(settings.selectedImage2);
    }
    gd.addChoice("Mask_mode", MASK_MODE, MASK_MODE[settings.maskMode]);
    gd.addChoice("Colocated_mode", COLOCATED_MODE, COLOCATED_MODE[settings.colocatedMode]);
    gd.addHelp(uk.ac.sussex.gdsc.ij.help.Urls.UTILITY);

    ImageListener imageListener = null;
    if (dynamic) {
      // Set up a worker for the preview
      flag = new Flag();
      worker = new Worker(flag);
      thread = new Thread(worker);
      thread.setDaemon(true);
      thread.start();

      preview = gd.addAndGetCheckbox("Preview", false);
      gd.addDialogListener((dialog, event) -> {
        // The event will be null when the NonBlockingDialog is first shown
        if (event != null && preview.getState()) {
          readDialog(dialog, true);
        }
        return true;
      });
      imageListener = new ImageAdapter() {
        @Override
        public void imageUpdated(ImagePlus imp) {
          if ((imp.getID() == id1 || imp.getID() == id2) && preview.getState()) {
            // We are monitoring these images so action this
            createMaskWork(true);
          }
        }
      };
      ImagePlus.addImageListener(imageListener);
    }
    gd.showDialog();

    boolean upToDate = false;
    if (dynamic) {
      upToDate = preview.getState();
      preview = null;
      ImagePlus.removeImageListener(imageListener);
    }

    final boolean cancelled = gd.wasCanceled();
    if (dynamic) {
      // Stop the worker thread
      if (cancelled) {
        try {
          thread.interrupt();
        } catch (final SecurityException ex) {
          // We should have permission to interrupt this thread.
          Logger.getLogger(getClass().getName()).log(Level.WARNING, "Thread shutdown failed", ex);
        }
      } else {
        worker.stop = true;
        flag.dirty();

        // Leave to finish the work
        try {
          thread.join(0);
        } catch (final InterruptedException ex) {
          Logger.getLogger(getClass().getName()).log(Level.WARNING, "Unexpected interruption", ex);
          Thread.currentThread().interrupt();
        }
      }
    }
    if (cancelled) {
      settings.save();
      return false;
    }

    // Only do this if the preview was out-of-date
    if (!upToDate) {
      readDialog(gd, false);
    }
    settings.save();

    // Record options
    Recorder.recordOption("Image_1", settings.selectedImage1);
    Recorder.recordOption("Image_2", settings.selectedImage2);
    Recorder.recordOption("Mask_mode", MASK_MODE[settings.maskMode]);
    Recorder.recordOption("Colocated_mode", COLOCATED_MODE[settings.colocatedMode]);

    return true;
  }

  private static int getId(String title) {
    final ImagePlus imp = WindowManager.getImage(title);
    return (imp == null) ? 0 : imp.getID();
  }

  private void readDialog(GenericDialog gd, boolean preview) {
    settings.selectedImage1 = gd.getNextChoice();
    settings.selectedImage2 = gd.getNextChoice();
    settings.maskMode = gd.getNextChoiceIndex();
    settings.colocatedMode = gd.getNextChoiceIndex();

    createMaskWork(preview);
  }

  private void createMaskWork(boolean preview) {
    if (preview) {
      // Add work for the background thread
      flag.dirty();
    } else {
      createMask(false);
    }
  }

  private void createMask(boolean preview) {
    final ImagePlus imp1 = WindowManager.getImage(settings.selectedImage1);
    final ImagePlus imp2 = WindowManager.getImage(settings.selectedImage2);

    if (imp1 == null) {
      IJ.error(TITLE, "Image " + settings.selectedImage1
          + " has been closed.\nPlease restart the plugin to refresh the image list.");
      return;
    }
    if (imp2 == null) {
      IJ.error(TITLE, "Image " + settings.selectedImage2
          + " has been closed.\nPlease restart the plugin to refresh the image list.");
      return;
    }

    // Change the images we are monitoring
    id1 = imp1.getID();
    id2 = imp2.getID();

    // Check the dimensions
    final int width = imp1.getWidth();
    final int height = imp1.getHeight();
    if (width != imp2.getWidth() || width != imp2.getHeight()) {
      if (preview) {
        IJ.log(TITLE + ": Images must have same XY dimensions");
      } else {
        IJ.error(TITLE, "Images must have same XY dimensions");
      }
      return;
    }

    // Create a mask using the correct stack dimensions
    final int[] dimensions1 = imp1.getDimensions();
    final int[] dimensions2 = imp2.getDimensions();

    // Produce the biggest hyperstack possible
    final String[] dimName = {"C", "Z", "T"};

    final int[] index = new int[3];

    StringBuilder sb = null;
    for (int i = 0, j = 2; i < 3; i++, j++) {
      if (dimensions1[j] == dimensions2[j]) {
        index[i] = dimensions1[j];
      } else {
        // If the CZT dimensions do not match then one must be 1
        if (dimensions1[j] != 1 && dimensions2[j] != 1) {
          IJ.error(TITLE,
              "Images must have same " + dimName[i] + " dimension or one must be singular");
          return;
        }
        // Use the max dimension.
        // The other image is only singular so getStackIndex will clip it appropriately.
        index[i] = Math.max(dimensions1[j], dimensions2[j]);
        // Log dimension mismatch
        if (sb == null) {
          sb = new StringBuilder(TITLE).append(" Warning - Dimension mismatch:");
        } else {
          sb.append(",");
        }
        sb.append(" ").append(dimName[i]).append(" ").append(dimensions1[j]).append("!=")
            .append(dimensions2[j]);
      }
    }
    if (sb != null) {
      IJ.log(sb.toString());
    }

    // Get the thresholds
    final DoubleArrayList list = new DoubleArrayList(index[0] * 2);
    for (int channel = 1; channel <= index[0]; channel++) {
      list.add(getMin(imp1, channel));
      list.add(getMin(imp2, channel));
    }

    // Check if it is worth doing any work.
    final SettingsList settingsList =
        new SettingsList(id1, id2, width, height, dimensions1[2], dimensions1[3], dimensions1[4],
            dimensions2[2], dimensions2[3], dimensions2[4], settings.colocatedMode, list);
    if (settingsList.equals(lastSettingsList)) {
      return;
    }
    lastSettingsList = settingsList;

    final ImageStack imageStack1 = imp1.getStack();
    final ImageStack imageStack2 = imp2.getStack();
    final ImagePlus imp =
        IJ.createHyperStack(TITLE, width, height, index[0], index[1], index[2], 8);
    final ImageStack outputStack = imp.getStack();

    final double[] e = list.elements();
    for (int channel = 1, next = 0; channel <= index[0]; channel++) {
      final double min1 = e[next++];
      final double min2 = e[next++];

      for (int slice = 1; slice <= index[1]; slice++) {
        for (int frame = 1; frame <= index[2]; frame++) {
          final ImageProcessor ip1 =
              imageStack1.getProcessor(imp1.getStackIndex(frame, slice, frame));
          final ImageProcessor ip2 =
              imageStack2.getProcessor(imp2.getStackIndex(frame, slice, frame));
          final byte[] b = (byte[]) outputStack.getPixels(imp1.getStackIndex(frame, slice, frame));

          if (settings.colocatedMode == 0) {
            // AND
            for (int i = ip2.getPixelCount(); i-- > 0;) {
              if (ip1.getf(i) >= min1 && ip2.getf(i) >= min2) {
                b[i] = (byte) 255;
              }
            }
          } else {
            // OR
            for (int i = ip2.getPixelCount(); i-- > 0;) {
              if (ip1.getf(i) >= min1 || ip2.getf(i) >= min2) {
                b[i] = (byte) 255;
              }
            }
          }
        }
      }
    }

    final ImagePlus oldImp = WindowManager.getImage(TITLE);
    if (oldImp == null) {
      imp.show();
    } else {
      oldImp.setStack(outputStack, index[0], index[1], index[2]);
    }
  }

  private double getMin(ImagePlus imp, int channel) {
    // Clip channel
    channel = Math.min(channel, imp.getNChannels());
    return (settings.maskMode == 0) ? getThreshold(imp, channel) : getDisplayRangeMin(imp, channel);
  }

  private static double getThreshold(ImagePlus imp, int channel) {
    // Composite image have different processors
    final ImageProcessor ip =
        (imp.isComposite()) ? ((CompositeImage) imp).getProcessor(channel) : imp.getProcessor();

    final double t = ip.getMinThreshold();
    return (t != ImageProcessor.NO_THRESHOLD) ? t : Double.NEGATIVE_INFINITY;
  }

  private static double getDisplayRangeMin(ImagePlus imp, int channel) {
    // Composite images can have a display range for each color channel
    final LUT[] luts = imp.getLuts();
    if (luts != null && channel <= luts.length) {
      return luts[channel - 1].min;
    }

    return imp.getDisplayRangeMin();
  }
}
