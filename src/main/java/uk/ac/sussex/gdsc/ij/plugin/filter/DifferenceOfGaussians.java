/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2019 Alex Herbert
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

package uk.ac.sussex.gdsc.ij.plugin.filter;

import uk.ac.sussex.gdsc.core.ij.ImageJUtils;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.plugin.ContrastEnhancer;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.awt.Rectangle;
import java.awt.TextField;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This plug-in filter implements the Difference of Gaussians method for image enhancement. The
 * filter performs subtraction of one blurred version of an image from another, less blurred version
 * of the original. The result is an image containing only the information within the spatial
 * frequency between the two blurred images (i.e. a band-pass filter).
 *
 * <p>The filter is implemented using two {@link GaussianBlur } passes. This takes advantage of the
 * downscaling/upscaling performed within the GaussianBlur class to increase speed for large radii.
 *
 * <p>Preview is supported and the two Gaussian filtered images are cached to avoid recomputation if
 * unchanged.
 */
public class DifferenceOfGaussians extends GaussianBlur {

  /** The flags specifying the capabilities and needs. */
  private static final int FLAGS = DOES_ALL | SUPPORTS_MASKING | KEEP_PREVIEW | FINAL_PROCESSING;
  /** The ImagePlus of the setup call, needed to get the spatial calibration. */
  private ImagePlus imp;
  /** Whether the image has an x&y scale. */
  private boolean hasScale;
  /** The number of passes (filter directions * color channels * stack slices). */
  private int passes = 1;
  private int pass; // Current pass

  private TextField sigma1field;
  private TextField sigma2field;
  private Checkbox previewCheckbox;
  private boolean preview;

  private boolean computeSigma1 = true;
  private boolean computeSigma2 = true;
  private ImageProcessor ip1;
  private ImageProcessor ip2;
  private PlugInFilterRunner pfr;
  private int currentSliceNumber = -1;

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

    /** The standard deviation of the larger Gaussian. */
    double sigma1 = Prefs.get("DoG.sigma1", 6.0);
    /** The standard deviation of the smaller Gaussian. */
    double sigma2 = Prefs.get("DoG.sigma2", 1.5);
    /** whether sigma is given in units corresponding to the pixel scale (not pixels). */
    boolean sigmaScaled = Prefs.getBoolean("DoG.sigmaScaled", false);
    boolean enhanceContrast = Prefs.getBoolean("DoG.enhanceContrast", false);
    boolean maintainRatio = Prefs.getBoolean("DoG.maintainRatio", false);

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
      sigma1 = source.sigma1;
      sigma2 = source.sigma2;
      sigmaScaled = source.sigmaScaled;
      enhanceContrast = source.enhanceContrast;
      maintainRatio = source.maintainRatio;
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
      Prefs.set("DoG.sigma1", sigma1);
      Prefs.set("DoG.sigma2", sigma2);
      Prefs.set("DoG.maintainRatio", maintainRatio);
      Prefs.set("DoG.sigmaScaled", sigmaScaled);
      Prefs.set("DoG.enhanceContrast", enhanceContrast);
    }
  }

  /**
   * Method to return types supported.
   *
   * @param arg unused
   * @param imp The ImagePlus, used to get the spatial calibration
   * @return Code describing supported formats etc.
   */
  @Override
  public int setup(String arg, ImagePlus imp) {
    if ("final".equals(arg)) {
      imp.resetDisplayRange();
      if (settings.enhanceContrast) {
        final ContrastEnhancer ce = new ContrastEnhancer();
        ce.stretchHistogram(imp, 0.35);
      }
      imp.updateAndDraw();
      return DONE;
    }

    this.imp = imp;
    int flags = FLAGS;
    if (imp != null && imp.getRoi() != null) {
      final Rectangle roiRect = imp.getRoi().getBounds();
      if (roiRect.y > 0 || roiRect.y + roiRect.height < imp.getDimensions()[1]) {
        flags |= SNAPSHOT; // snapshot for pixels above and/or below roi rectangle
      }
    }

    return flags;
  }

  /**
   * Ask the user for the parameters.
   */
  @Override
  public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
    settings = Settings.load();
    final double min = imp.getDisplayRangeMin();
    final double max = imp.getDisplayRangeMax();
    this.pfr = pfr;
    final GenericDialog gd = new GenericDialog(command);
    gd.addMessage("Subtracts blurred image 2 from 1:\n- Sigma1 = local contrast\n"
        + "- Sigma2 = local noise\nUse Sigma1 > Sigma2");
    gd.addNumericField("Sigma1 (Radius)", settings.sigma1, 2);
    gd.addNumericField("Sigma2 (Radius)", settings.sigma2, 2);
    gd.addCheckbox("Maintain Ratio", settings.maintainRatio);
    if (imp.getCalibration() != null && !imp.getCalibration().getUnits().equals("pixels")) {
      hasScale = true;
      gd.addCheckbox("Scaled Units (" + imp.getCalibration().getUnits() + ")",
          settings.sigmaScaled);
    } else {
      settings.sigmaScaled = false;
    }
    gd.addCheckbox("Enhance Contrast", settings.enhanceContrast);
    gd.addPreviewCheckbox(pfr);
    gd.addDialogListener(this);
    @SuppressWarnings("rawtypes")
    final Vector fields = gd.getNumericFields();
    sigma1field = (TextField) fields.elementAt(0);
    sigma2field = (TextField) fields.elementAt(1);
    previewCheckbox = gd.getPreviewCheckbox();
    preview = previewCheckbox.getState();
    gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.UTILITY);
    gd.showDialog(); // input by the user (or macro) happens here
    final boolean cancelled = gd.wasCanceled();
    settings.save();
    if (cancelled) {
      imp.setDisplayRange(min, max);
      return DONE;
    }
    return IJ.setupDialog(imp, FLAGS); // ask whether to process all slices of stack (if a stack)
  }

  /** Listener to modifications of the input fields of the dialog. */
  @Override
  public boolean dialogItemChanged(GenericDialog gd, AWTEvent event) {
    settings.maintainRatio = gd.getNextBoolean();

    double newSigma = gd.getNextNumber();
    if (newSigma <= 0 || gd.invalidNumber()) {
      return false;
    }
    if (settings.sigma1 != newSigma) {
      computeSigma1 = true;
      double ratio = (settings.maintainRatio) ? settings.sigma1 / settings.sigma2 : 1;
      // Update sigma before setting the other text field
      settings.sigma1 = newSigma;
      if (settings.maintainRatio) {
        computeSigma2 = true;
        settings.sigma2 = Double.parseDouble(IJ.d2s(newSigma / ratio, 3));
        sigma2field.setText("" + settings.sigma2);
      }
    }

    newSigma = gd.getNextNumber();
    if (newSigma < 0 || gd.invalidNumber()) {
      return false;
    }
    if (settings.sigma2 != newSigma) {
      computeSigma2 = true;
      double ratio = (settings.maintainRatio) ? settings.sigma1 / settings.sigma2 : 1;
      // Update sigma before setting the other text field
      settings.sigma2 = newSigma;
      if (settings.maintainRatio) {
        computeSigma1 = true;
        settings.sigma1 = Double.parseDouble(IJ.d2s(newSigma * ratio, 3));
        sigma1field.setText("" + settings.sigma1);
      }
    }

    if (settings.sigma1 <= settings.sigma2) {
      return false;
    }

    if (hasScale) {
      final boolean newSigmaScaled = gd.getNextBoolean();
      if (settings.sigmaScaled != newSigmaScaled) {
        computeSigma1 = true;
        computeSigma2 = true;
      }
      settings.sigmaScaled = newSigmaScaled;
    }

    settings.enhanceContrast = gd.getNextBoolean();

    final boolean newPreview = previewCheckbox.getState();
    if (preview != newPreview
        // Check if the preview has been turned off then reset the display range
        && !newPreview && imp != null) {
      imp.getProcessor().resetMinAndMax();
    }
    preview = newPreview;

    return true;
  }

  /**
   * Set the number of passes of the run method.
   */
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

    // Check if this is the same slice within the preview or a new slice when processing a stack
    if (currentSliceNumber != pfr.getSliceNumber()) {
      computeSigma1 = true;
      computeSigma2 = true;
    }
    currentSliceNumber = pfr.getSliceNumber();

    final double sigmaX =
        settings.sigmaScaled ? settings.sigma1 / imp.getCalibration().pixelWidth : settings.sigma1;
    final double sigmaY =
        settings.sigmaScaled ? settings.sigma1 / imp.getCalibration().pixelHeight : settings.sigma1;
    final double sigma2X =
        settings.sigmaScaled ? settings.sigma2 / imp.getCalibration().pixelWidth : settings.sigma2;
    final double sigma2Y =
        settings.sigmaScaled ? settings.sigma2 / imp.getCalibration().pixelHeight : settings.sigma2;
    final double accuracy =
        (ip instanceof ByteProcessor || ip instanceof ColorProcessor) ? 0.002 : 0.0002;

    // Recompute only the parts necessary
    if (computeSigma1) {
      ip1 = duplicateProcessor(ip);
      blurGaussian(ip1, sigmaX, sigmaY, accuracy);
      if (Thread.currentThread().isInterrupted()) {
        return; // interruption for new parameters during preview?
      }
      computeSigma1 = false;
    }
    showProgressInternal(0.333);
    if (computeSigma2) {
      ip2 = duplicateProcessor(ip);
      blurGaussian(ip2, sigma2X, sigma2Y, accuracy);
      if (Thread.currentThread().isInterrupted()) {
        return; // interruption for new parameters during preview?
      }
      computeSigma2 = false;
    }
    showProgressInternal(0.667);

    differenceOfGaussians(ip, ip1, ip2);

    // Need to refresh on screen display
    if (imp != null) {
      // This is necessary when processing stacks after preview
      imp.getStack().setPixels(ip.getPixels(), currentSliceNumber);

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
   * Perform a Difference of Gaussians (filteredImage2 - filteredImage1) on the image. Sigma1 should
   * be greater than sigma2.
   *
   * @param ip the image
   * @param sigma1 the sigma 1
   * @param sigma2 the sigma 2
   */
  public static void run(ImageProcessor ip, double sigma1, double sigma2) {
    final ImageProcessor ip1 = (sigma1 > 0) ? duplicateProcessor(ip) : ip;
    final ImageProcessor ip2 = (sigma2 > 0) ? duplicateProcessor(ip) : ip;
    final DifferenceOfGaussians filter = new DifferenceOfGaussians();
    filter.noProgress = true;
    filter.showProgress(false);
    filter.blurGaussian(ip1, sigma1);
    filter.blurGaussian(ip2, sigma2);
    differenceOfGaussians(ip, ip1, ip2);
  }

  /**
   * Subtract one image from the other (ip2 - ip1) and store in the result processor.
   *
   * @param resultIp the result ip
   * @param ip1 the image 1
   * @param ip2 the image 2
   */
  private static void differenceOfGaussians(ImageProcessor resultIp, ImageProcessor ip1,
      ImageProcessor ip2) {
    // Reuse the processor space
    FloatProcessor fp1 = null;
    FloatProcessor fp2 = null;
    FloatProcessor fp3 = null;

    final Rectangle roi = resultIp.getRoi();
    final int yTo = roi.y + roi.height;

    for (int i = 0; i < resultIp.getNChannels(); i++) {
      fp1 = ip1.toFloat(i, fp1);
      fp2 = ip2.toFloat(i, fp2);
      final float[] ff1 = (float[]) fp1.getPixels();
      final float[] ff2 = (float[]) fp2.getPixels();

      // If an ROI is present start with the original image
      if (roi.height != resultIp.getHeight() || roi.width != resultIp.getWidth()) {
        fp3 = resultIp.toFloat(i, fp3);
        final float[] ff3 = (float[]) fp3.getPixels();
        // Copy within the ROI
        for (int y = roi.y; y < yTo; y++) {
          int index = y * resultIp.getWidth() + roi.x;
          for (int x = 0; x < roi.width; x++, index++) {
            ff3[index] = ff2[index] - ff1[index];
          }
        }
      } else {
        fp3 = new FloatProcessor(fp1.getWidth(), fp2.getHeight());
        final float[] ff3 = (float[]) fp3.getPixels();
        for (int j = ff1.length; j-- > 0;) {
          ff3[j] = ff2[j] - ff1[j];
        }
      }

      if (Thread.currentThread().isInterrupted()) {
        return; // interruption for new parameters during preview?
      }
      resultIp.setPixels(i, fp3);
    }
  }

  /**
   * Perform a Gaussian blur on the image processor.
   *
   * @param ip the image
   * @param sigma The Gaussian width
   */
  @Override
  public void blurGaussian(ImageProcessor ip, double sigma) {
    final double accuracy =
        (ip instanceof ByteProcessor || ip instanceof ColorProcessor) ? 0.002 : 0.0002;
    blurGaussian(ip, sigma, sigma, accuracy);
  }

  private static ImageProcessor duplicateProcessor(ImageProcessor ip) {
    final ImageProcessor duplicateIp = ip.duplicate();
    if (ip.getRoi().height != ip.getHeight() || ip.getRoi().width != ip.getWidth()) {
      duplicateIp.snapshot();
      duplicateIp.setRoi(ip.getRoi());
      duplicateIp.setMask(ip.getMask());
    }
    return duplicateIp;
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

    if (ImageJUtils
        .showStatus(() -> String.format("Difference of Gaussians: %.3g%%", progress * 100))) {
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
