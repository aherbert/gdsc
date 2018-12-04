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

package uk.ac.sussex.gdsc.ij.plugin.filter;

import ij.IJ;
import ij.ImagePlus;
import ij.Macro;
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

/**
 * This plug-in filter implements the Difference of Gaussians method for image enhancement. The
 * filter performs subtraction of one blurred version of an image from another, less blurred version
 * of the original. The result is an image containing only the information within the spatial
 * frequency between the two blurred images (i.e. a band-pass filter).
 *
 * The filter is implemented using two {@link GaussianBlur } passes. This takes advantage of the
 * downscaling/upscaling performed within the GaussianBlur class to increase speed for large radii.
 *
 * Preview is supported and the two Gaussian filtered images are cached to avoid recomputation if
 * unchanged.
 */
public class DifferenceOfGaussians extends GaussianBlur {

  /** the standard deviation of the Gaussian. */
  private static double sigma1 = Prefs.get("DoG.sigma1", 6.0);
  private static double sigma2 = Prefs.get("DoG.sigma2", 1.5);
  /** whether sigma is given in units corresponding to the pixel scale (not pixels). */
  private static boolean sigmaScaled = Prefs.getBoolean("DoG.sigmaScaled", false);
  private static boolean enhanceContrast = Prefs.getBoolean("DoG.enhanceContrast", false);
  private static boolean maintainRatio = Prefs.getBoolean("DoG.maintainRatio", false);
  /** The flags specifying the capabilities and needs. */
  private int flags = DOES_ALL | SUPPORTS_MASKING | KEEP_PREVIEW | FINAL_PROCESSING;
  private ImagePlus imp; // The ImagePlus of the setup call, needed to get the spatial calibration
  private boolean hasScale = false; // whether the image has an x&y scale
  private int nPasses = 1; // The number of passes (filter directions * color channels * stack
                           // slices)
  private int pass; // Current pass

  private TextField sigma1field;
  private TextField sigma2field;
  private Checkbox previewCheckbox;
  // Flag used by the preview to indicate that further changes to the parameters will occur.
  // Setting this to true will cause the next invocation of run() to do nothing.
  private boolean ignoreChange = false;
  private boolean preview = false;

  private boolean computeSigma1 = true;
  private boolean computeSigma2 = true;
  private ImageProcessor ip1 = null;
  private ImageProcessor ip2 = null;
  private PlugInFilterRunner pfr = null;
  private int currentSliceNumber = -1;
  private double percentInternal = 0;
  private long lastTime = 0;

  /**
   * Set to true to suppress progress reporting to the ImageJ window.
   */
  public boolean noProgress = false;

  /**
   * Method to return types supported.
   *
   * @param arg unused
   * @param imp The ImagePlus, used to get the spatial calibration
   * @return Code describing supported formats etc.
   */
  @Override
  public int setup(String arg, ImagePlus imp) {
    this.imp = imp;
    if (imp != null) {
      if (imp.getRoi() != null) {
        final Rectangle roiRect = imp.getRoi().getBounds();
        if (roiRect.y > 0 || roiRect.y + roiRect.height < imp.getDimensions()[1]) {
          flags |= SNAPSHOT; // snapshot for pixels above and/or below roi rectangle
        }
      }
      if (arg.equals("final")) {
        imp.resetDisplayRange();
        if (enhanceContrast) {
          final ContrastEnhancer ce = new ContrastEnhancer();
          ce.stretchHistogram(imp, 0.35);
        }
        imp.updateAndDraw();
      }
    }

    return flags;
  }

  /**
   * Ask the user for the parameters.
   */
  @Override
  public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
    final double min = imp.getDisplayRangeMin();
    final double max = imp.getDisplayRangeMax();
    this.pfr = pfr;
    final String options = Macro.getOptions();
    boolean oldMacro = false;
    if (options != null) {
      if (options.indexOf("radius=") >= 0) { // ensure compatibility with old macros
        oldMacro = true; // specifying "radius=", not "sigma=
        Macro.setOptions(options.replaceAll("radius=", "sigma="));
      }
    }
    final GenericDialog gd = new GenericDialog(command);
    gd.addMessage(
        "Subtracts blurred image 2 from 1:\n- Sigma1 = local contrast\n- Sigma2 = local noise\nUse Sigma1 > Sigma2");
    gd.addNumericField("Sigma1 (Radius)", sigma1, 2);
    gd.addNumericField("Sigma2 (Radius)", sigma2, 2);
    gd.addCheckbox("Maintain Ratio", maintainRatio);
    if (imp.getCalibration() != null && !imp.getCalibration().getUnits().equals("pixels")) {
      hasScale = true;
      gd.addCheckbox("Scaled Units (" + imp.getCalibration().getUnits() + ")", sigmaScaled);
    } else {
      sigmaScaled = false;
    }
    gd.addCheckbox("Enhance Contrast", enhanceContrast);
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
    if (gd.wasCanceled()) {
      imp.setDisplayRange(min, max);
      return DONE;
    }
    if (oldMacro) {
      sigma1 /= 2.5; // for old macros, "radius" was 2.5 sigma
      sigma2 /= 2.5;
    }
    IJ.register(this.getClass()); // protect static class variables (parameters) from garbage
                                  // collection
    return IJ.setupDialog(imp, flags); // ask whether to process all slices of stack (if a stack)
  }

  /** Listener to modifications of the input fields of the dialog. */
  @Override
  public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
    // Flag used to indicate the next call to run should be ignored.
    // This is set when the ratio between fields is maintained. Since the
    // text field will be set the dialogItemChanged event will be called
    // again. This means the calculation is not needed a second time.
    boolean disableRun = false;

    maintainRatio = gd.getNextBoolean();

    double newSigma = gd.getNextNumber();
    if (newSigma <= 0 || gd.invalidNumber()) {
      return false;
    }
    if (sigma1 != newSigma) {
      computeSigma1 = true;
      if (maintainRatio) {
        computeSigma2 = true;
        final double ratio = sigma1 / sigma2;
        sigma2 = Double.parseDouble(IJ.d2s(newSigma / ratio, 3));
        // System.out.printf("New Sigma2 = %g\n", sigma2);
        disableRun = true;
        sigma2field.setText("" + sigma2);
      }
    }
    sigma1 = newSigma;

    newSigma = gd.getNextNumber();
    if (newSigma < 0 || gd.invalidNumber()) {
      return false;
    }
    if (sigma2 != newSigma) {
      computeSigma2 = true;
      if (maintainRatio) {
        computeSigma1 = true;
        final double ratio = sigma1 / sigma2;
        sigma1 = Double.parseDouble(IJ.d2s(newSigma * ratio, 3));
        // System.out.printf("New Sigma1 = %s\n", sigma1);
        disableRun = true;
        sigma1field.setText("" + sigma1);
      }
    }
    sigma2 = newSigma;

    if (sigma1 <= sigma2) {
      return false;
    }

    if (hasScale) {
      final boolean newSigmaScaled = gd.getNextBoolean();
      if (sigmaScaled != newSigmaScaled) {
        computeSigma1 = true;
        computeSigma2 = true;
      }
      sigmaScaled = newSigmaScaled;
    }

    enhanceContrast = gd.getNextBoolean();

    // Save settings to preferences
    Prefs.set("DoG.sigma1", sigma1);
    Prefs.set("DoG.sigma2", sigma2);
    Prefs.set("DoG.maintainRatio", maintainRatio);
    Prefs.set("DoG.sigmaScaled", sigmaScaled);
    Prefs.set("DoG.enhanceContrast", enhanceContrast);

    final boolean newPreview = previewCheckbox.getState();
    if (preview != newPreview) {
      // Check if the preview has been turned off then reset the display range
      if (!newPreview && imp != null) {
        imp.resetDisplayRange();
      }
    }
    preview = newPreview;

    if (disableRun) {
      ignoreChange = true;
    }

    return true;
  }

  /**
   * Set the number of passes of the run method.
   */
  @Override
  public void setNPasses(int nPasses) {
    this.nPasses = nPasses;
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
    if (ignoreChange) {
      ignoreChange = false;
      return;
    }
    ignoreChange = false;

    pass++;
    if (pass > nPasses) {
      pass = 1;
    }

    // Check if this is the same slice within the preview or a new slice when processing a stack
    if (currentSliceNumber != pfr.getSliceNumber()) {
      computeSigma1 = true;
      computeSigma2 = true;
    }
    currentSliceNumber = pfr.getSliceNumber();

    final double sigmaX = sigmaScaled ? sigma1 / imp.getCalibration().pixelWidth : sigma1;
    final double sigmaY = sigmaScaled ? sigma1 / imp.getCalibration().pixelHeight : sigma1;
    final double sigma2X = sigmaScaled ? sigma2 / imp.getCalibration().pixelWidth : sigma2;
    final double sigma2Y = sigmaScaled ? sigma2 / imp.getCalibration().pixelHeight : sigma2;
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
    // ip.resetMinAndMax();
    if (imp != null) {
      // This is necessary when processing stacks after preview
      imp.getStack().setPixels(ip.getPixels(), currentSliceNumber);

      imp.resetDisplayRange();
      if (enhanceContrast) {
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
    FloatProcessor fp1 = null;
    FloatProcessor fp2 = null;

    final Rectangle roi = resultIp.getRoi();
    final int yTo = roi.y + roi.height;
    FloatProcessor fp3 = null;

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
  private void showProgress(double percent) {
    if (noProgress) {
      return;
    }

    // Ignore the input percent and use the internal one
    percent = (double) (pass - 1) / nPasses + this.percentInternal / nPasses;

    final long time = System.currentTimeMillis();
    if (time - lastTime < 100) {
      return;
    }
    lastTime = time;

    IJ.showProgress(percent);
    IJ.showStatus(String.format("Difference of Gaussians: %.3g%%", percent * 100));
  }

  /**
   * Show the progress on the ImageJ progress bar.
   *
   * @param percent the percent
   */
  private void showProgressInternal(double percent) {
    this.percentInternal = percent;
    showProgress(0);
  }
}
