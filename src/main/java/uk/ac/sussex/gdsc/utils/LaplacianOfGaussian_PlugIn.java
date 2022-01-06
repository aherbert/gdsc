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

package uk.ac.sussex.gdsc.utils;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.plugin.ContrastEnhancer;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import imagescience.feature.Differentiator;
import imagescience.image.FloatImage;
import imagescience.image.Image;
import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.util.concurrent.atomic.AtomicReference;
import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;

/**
 * Run a 2D Laplacian of Gaussian filter.
 *
 * <p>Requires the ImageScience library. The imagescience.jar must be installed in the ImageJ
 * plugins folder.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Blob_detection#The_Laplacian_of_Gaussian">Laplacian
 *      of Gaussian</a>
 * @see <a href= "https://imagescience.org/meijering/software/imagescience/">ImageScience: A Java
 *      Library for Scientific Image Computing</a>
 */
public class LaplacianOfGaussian_PlugIn implements ExtendedPlugInFilter {
  private static final String TITLE = "Laplacian Of Gaussian";

  /** The flags specifying the capabilities and needs. */
  private static final int FLAGS =
      DOES_8G | DOES_16 | DOES_32 | KEEP_PREVIEW | FINAL_PROCESSING | PARALLELIZE_STACKS;
  /** The ImagePlus of the setup call, needed to get the spatial calibration. */
  private ImagePlus imp;
  /** Whether the image has an x&y scale. */
  private boolean hasScale;
  /** The number of passes (filter directions * color channels * stack slices). */
  private int passes = 1;
  private int pass; // Current pass

  // Used to control preview processing. This can avoid re-computation of the LoG
  // if the sigma is not different.
  private Checkbox previewCheckbox;
  private boolean preview;

  /**
   * Set to true to suppress progress reporting to the ImageJ window.
   */
  private boolean noProgress;

  /** The current settings for the plugin instance. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    /** The standard deviation of the Gaussian. */
    double sigma = Prefs.get("LoG.sigma", 1.0);
    /** Whether sigma is given in units corresponding to the pixel scale (not pixels). */
    boolean sigmaScaled = Prefs.getBoolean("LoG.sigmaScaled", false);
    boolean scaledNormalised = Prefs.getBoolean("LoG.scaledNormalised", true);
    boolean negate = Prefs.getBoolean("LoG.negate", false);
    boolean enhanceContrast = Prefs.getBoolean("LoG.enhanceContrast", false);

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
      sigma = source.sigma;
      sigmaScaled = source.sigmaScaled;
      scaledNormalised = source.scaledNormalised;
      negate = source.negate;
      enhanceContrast = source.enhanceContrast;
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
      // Save settings to preferences for state between ImageJ sessions
      Prefs.set("LoG.sigma", sigma);
      Prefs.set("LoG.sigmaScaled", sigmaScaled);
      Prefs.set("LoG.scaledNormalised", scaledNormalised);
      Prefs.set("LoG.negate", negate);
      Prefs.set("LoG.enhanceContrast", enhanceContrast);
    }
  }

  @Override
  public int setup(String arg, ImagePlus imp) {
    if ("final".equals(arg)) {
      imp.getProcessor().resetMinAndMax();
      if (settings.enhanceContrast) {
        final ContrastEnhancer ce = new ContrastEnhancer();
        ce.stretchHistogram(imp, 0.35);
      }
      imp.updateAndDraw();
      return DONE;
    }

    if (!ImageScienceUtils.hasImageScience()) {
      ImageScienceUtils.showError();
      return DONE;
    }

    UsageTracker.recordPlugin(this.getClass(), arg);

    this.imp = imp;

    return FLAGS;
  }

  @Override
  public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
    settings = Settings.load();
    final double min = imp.getDisplayRangeMin();
    final double max = imp.getDisplayRangeMax();
    final GenericDialog gd = new GenericDialog(command);
    gd.addMessage(
        "Computes the " + TITLE + ".\nStrong responses for blobs of radius 1.41 * sigma.");
    gd.addSlider("Sigma (Radius)", 0.5, 20, settings.sigma);
    if (imp.getCalibration() != null && !imp.getCalibration().getUnits().equals("pixels")) {
      hasScale = true;
      gd.addCheckbox("Scaled Units (" + imp.getCalibration().getUnits() + ")",
          settings.sigmaScaled);
    } else {
      settings.sigmaScaled = false;
    }
    gd.addCheckbox("Scale_normalised", settings.scaledNormalised);
    gd.addCheckbox("Negate", settings.negate);
    gd.addCheckbox("Enhance Contrast", settings.enhanceContrast);
    gd.addPreviewCheckbox(pfr);
    gd.addDialogListener(this::dialogItemChanged);
    previewCheckbox = gd.getPreviewCheckbox();
    preview = previewCheckbox.getState();
    gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.UTILITY);
    gd.showDialog();
    final boolean cancelled = gd.wasCanceled();
    settings.save();
    if (cancelled) {
      imp.setDisplayRange(min, max);
      return DONE;
    }

    // Stop preview
    this.imp = null;

    // Ask whether to process all slices of stack (if a stack)
    return IJ.setupDialog(imp, FLAGS);
  }

  private boolean dialogItemChanged(GenericDialog gd, @SuppressWarnings("unused") AWTEvent event) {

    double newSigma = gd.getNextNumber();
    if (newSigma <= 0 || gd.invalidNumber()) {
      return false;
    }

    settings.sigma = newSigma;

    if (hasScale) {
      settings.sigmaScaled = gd.getNextBoolean();
    }

    settings.scaledNormalised = gd.getNextBoolean();
    settings.negate = gd.getNextBoolean();
    settings.enhanceContrast = gd.getNextBoolean();

    final boolean newPreview = previewCheckbox.getState();
    if (preview != newPreview
        // Check if the preview has been turned off then reset the display range
        && !newPreview && imp != null) {
      imp.resetDisplayRange();
    }
    preview = newPreview;

    return true;
  }

  @Override
  public void setNPasses(int passes) {
    this.passes = passes;
    pass = 0;
  }

  /**
   * This method is invoked for each slice during execution.
   *
   * @param ip The image subject to filtering. It must have a valid snapshot if the height of the
   *        roi is less than the full image height.
   */
  @Override
  public void run(ImageProcessor ip) {
    pass++;
    if (pass > passes) {
      pass = 1;
    }

    final double sigmaX =
        settings.sigmaScaled ? settings.sigma / imp.getCalibration().pixelWidth : settings.sigma;
    final double sigmaY =
        settings.sigmaScaled ? settings.sigma / imp.getCalibration().pixelHeight : settings.sigma;

    // Recompute only if necessary
    // Compute the LoG image using the ImageScience library.
    // The input should be a FloatProcessor.
    FloatProcessor fp =
        (ip instanceof FloatProcessor) ? (FloatProcessor) ip.duplicate() : ip.toFloat(0, null);
    final FloatImage img = (FloatImage) Image.wrap(new ImagePlus(null, fp));
    final Differentiator differentiator = new Differentiator();
    final Image Ixx = differentiator.run(img.duplicate(), sigmaX, 2, 0, 0);
    if (Thread.currentThread().isInterrupted()) {
      // possible new preview?
      return;
    }
    showProgressInternal(0.45);
    final Image Iyy = differentiator.run(img, sigmaY, 0, 2, 0);
    if (Thread.currentThread().isInterrupted()) {
      // possible new preview?
      return;
    }
    showProgressInternal(0.95);
    Iyy.add(Ixx);
    fp = (FloatProcessor) Iyy.imageplus().getProcessor();

    // Post-processing
    double norm = (settings.negate) ? -1 : 1;
    if (settings.scaledNormalised) {
      norm *= sigmaX * sigmaY;
    }
    if (norm != 1) {
      final float[] data = (float[]) fp.getPixels();
      for (int i = 0; i < data.length; i++) {
        data[i] *= norm;
      }
    }

    // Using copyBits will do scaling for ByteProcessor but not for ShortProcessor.
    // This is inconsistent. setPixels will do clipped rounding for both.
    ip.setPixels(0, fp);

    // Need to refresh on screen display during preview
    if (imp != null) {
      ip.resetMinAndMax();
      if (settings.enhanceContrast) {
        final ContrastEnhancer ce = new ContrastEnhancer();
        ce.stretchHistogram(imp, 0.35);
      }
      imp.updateAndDraw();
    }

    showProgressInternal(1.0);
  }

  /**
   * Show the progress on the ImageJ progress bar.
   *
   * @param percent the percent
   */
  private void showProgressInternal(double percent) {
    if (noProgress) {
      return;
    }

    // Ignore the input percent and use the internal one
    final double progress = (double) (pass - 1) / passes + percent / passes;

    if (ImageJUtils.showStatus(() -> String.format("LoG : %.3g%%", progress * 100))) {
      IJ.showProgress(progress);
    }
  }

  /**
   * Set to true if suppressing progress reporting to the ImageJ window.
   *
   * @return true, if is no progress
   */
  public boolean isNoProgress() {
    return noProgress;
  }

  /**
   * Set to true to suppress progress reporting to the ImageJ window.
   *
   * @param noProgress the new no progress
   */
  public void setNoProgress(boolean noProgress) {
    this.noProgress = noProgress;
  }
}
