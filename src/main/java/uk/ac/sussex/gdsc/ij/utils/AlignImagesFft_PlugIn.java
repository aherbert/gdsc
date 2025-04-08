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
import ij.WindowManager;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.function.Consumer;
import uk.ac.sussex.gdsc.core.ij.AlignImagesFft;
import uk.ac.sussex.gdsc.core.ij.AlignImagesFft.SubPixelMethod;
import uk.ac.sussex.gdsc.core.ij.ImageJTrackProgress;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.ij.gui.ExtendedGenericDialog;
import uk.ac.sussex.gdsc.core.utils.ImageWindow.WindowMethod;
import uk.ac.sussex.gdsc.ij.UsageTracker;

/**
 * Aligns an image stack to a reference image using XY translation to maximise the correlation.
 * Takes in: <ul> <li>The reference image <li>The image/stack to align. <li>Optional Max/Min values
 * for the X and Y translation <li>Window function to reduce edge artifacts in frequency space </ul>
 *
 *
 * <p>The alignment is calculated using the maximum correlation between the images. The correlation
 * is computed using the frequency domain (note that conjugate multiplication in the frequency
 * domain is equivalent to correlation in the space domain).
 *
 * <p>Output new stack with the best alignment with optional sub-pixel accuracy.
 *
 * <p>By default restricts translation so that at least half of the smaller image width/height is
 * within the larger image (half-max translation). This can be altered by providing a translation
 * bounds. Note that when using normalised correlation all scores are set to zero outside the
 * half-max translation due to potential floating-point summation error during normalisation.
 */
// This code is based on the Fast Normalised Cross-Correlation algorithm by J.P.Lewis
// http://scribblethink.org/Work/nvisionInterface/nip.pdf
public class AlignImagesFft_PlugIn implements PlugIn {
  private static final String TITLE = "Align Images FFT";

  /** The available window function. */
  private static final String[] windowFunctions;
  private static int myWindowFunction = WindowMethod.TUKEY.ordinal();
  private static boolean restrictTranslation;
  private static int myMinXShift = -20;
  private static int myMaxXShift = 20;
  private static int myMinYShift = -20;
  private static int myMaxYShift = 20;
  /** The available sub-pixel registration methods. */
  private static final String[] subPixelMethods;
  private static int subPixelMethod = SubPixelMethod.CUBIC.ordinal();
  /** The available interpolation methods. */
  private static final String[] interpolationMethods = ImageProcessor.getInterpolationMethods();
  private static int interpolationMethod = ImageProcessor.NONE;

  private static final String NONE = "[Reference stack]";
  private static String reference = "";
  private static String target = "";
  private static boolean normalised = true;
  private static boolean showCorrelationImage;
  private static boolean showNormalisedImage;
  private static boolean clipOutput;

  static {
    final WindowMethod[] m = WindowMethod.values();
    windowFunctions = new String[m.length];
    for (int i = 0; i < m.length; i++) {
      windowFunctions[i] = m[i].toString();
    }

    final SubPixelMethod[] m2 = AlignImagesFft.SubPixelMethod.values();
    subPixelMethods = new String[m2.length];
    for (int i = 0; i < m2.length; i++) {
      subPixelMethods[i] = m2[i].toString();
    }
  }

  /**
   * Gets the available window functions.
   *
   * @return the window functions
   */
  public static String[] getWindowFunctions() {
    return windowFunctions.clone();
  }

  /**
   * Gets available interpolation methods.
   *
   * @return the interpolation methods
   */
  public static String[] getInterpolationMethods() {
    return interpolationMethods.clone();
  }

  /**
   * Gets the available sub-pixel registration methods.
   *
   * @return the sub pixel methods
   */
  public static String[] getSubPixelMethods() {
    return subPixelMethods.clone();
  }

  /** Ask for parameters and then execute. */
  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    final String[] imageList = getImagesList();

    if (imageList.length < 1) {
      IJ.showMessage("Error", "Only 8-bit and 16-bit images are supported");
      return;
    }

    if (!showDialog(imageList)) {
      return;
    }

    final ImagePlus refImp = WindowManager.getImage(reference);
    final ImagePlus targetImp = WindowManager.getImage(target);

    Rectangle bounds = null;
    if (restrictTranslation) {
      bounds = createBounds(myMinXShift, myMaxXShift, myMinYShift, myMaxYShift);
    }

    Consumer<ImagePlus> correlationImageAction = createAction(showCorrelationImage);
    Consumer<ImagePlus> normalisedImageAction = createAction(showNormalisedImage);

    final AlignImagesFft align = new AlignImagesFft();
    align.setProgress(new ImageJTrackProgress());
    final ImagePlus alignedImp =
        align.align(refImp, targetImp, WindowMethod.values()[myWindowFunction], bounds,
            SubPixelMethod.values()[subPixelMethod], interpolationMethod, normalised,
            correlationImageAction, normalisedImageAction, normalisedImageAction, clipOutput);

    if (alignedImp != null) {
      // Do the same action
      createAction(true).accept(alignedImp);
    }
  }

  private static String[] getImagesList() {
    // Find the currently open images
    final ArrayList<String> newImageList = new ArrayList<>();

    for (final int id : uk.ac.sussex.gdsc.core.ij.ImageJUtils.getIdList()) {
      final ImagePlus imp = WindowManager.getImage(id);

      // Image must be 8-bit/16-bit
      if (imp != null && (imp.getType() == ImagePlus.GRAY8 || imp.getType() == ImagePlus.GRAY16
          || imp.getType() == ImagePlus.GRAY32)) {
        // Check it is not one the result images
        final String imageTitle = imp.getTitle();
        newImageList.add(imageTitle);
      }
    }

    return newImageList.toArray(new String[0]);
  }

  private static Consumer<ImagePlus> createAction(boolean showImage) {
    if (showImage) {
      // This will create a new image
      // return ImagePlus::show;
      // This will reuse the same image window
      return imp -> ImageJUtils.display(imp.getTitle(), imp.getImageStack());
    }
    return null;
  }

  private static boolean showDialog(String[] imageList) {
    final ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);

    if (!contains(imageList, reference)) {
      reference = imageList[0];
    }

    // Add option to have no target
    final String[] targetList = new String[imageList.length + 1];
    targetList[0] = NONE;
    for (int i = 0; i < imageList.length; i++) {
      targetList[i + 1] = imageList[i];
    }
    if (!contains(targetList, target)) {
      target = targetList[0];
    }

    gd.addMessage(
        "Align target image stack to a reference using\ncorrelation in the frequency domain. "
            + "Edge artifacts\n"
            + "can be reduced using a window function or by\nrestricting the translation.");

    gd.addChoice("Reference_image", imageList, reference);
    gd.addChoice("Target_image", targetList, target);
    gd.addChoice("Window_function", windowFunctions, myWindowFunction);
    gd.addCheckbox("Restrict_translation", restrictTranslation);
    gd.addNumericField("Min_X_translation", myMinXShift, 0);
    gd.addNumericField("Max_X_translation", myMaxXShift, 0);
    gd.addNumericField("Min_Y_translation", myMinYShift, 0);
    gd.addNumericField("Max_Y_translation", myMaxYShift, 0);
    gd.addChoice("Sub-pixel_method", subPixelMethods, subPixelMethod);
    gd.addChoice("Interpolation", interpolationMethods, interpolationMethod);
    gd.addCheckbox("Normalised", normalised);
    gd.addCheckbox("Show_correlation_image", showCorrelationImage);
    gd.addCheckbox("Show_normalised_image", showNormalisedImage);
    gd.addCheckbox("Clip_output", clipOutput);
    gd.addHelp(uk.ac.sussex.gdsc.ij.help.Urls.UTILITY);

    gd.showDialog();

    if (gd.wasCanceled()) {
      return false;
    }

    reference = gd.getNextChoice();
    target = gd.getNextChoice();
    myWindowFunction = gd.getNextChoiceIndex();
    restrictTranslation = gd.getNextBoolean();
    myMinXShift = (int) gd.getNextNumber();
    myMaxXShift = (int) gd.getNextNumber();
    myMinYShift = (int) gd.getNextNumber();
    myMaxYShift = (int) gd.getNextNumber();
    subPixelMethod = gd.getNextChoiceIndex();
    interpolationMethod = gd.getNextChoiceIndex();
    normalised = gd.getNextBoolean();
    showCorrelationImage = gd.getNextBoolean();
    showNormalisedImage = gd.getNextBoolean();
    clipOutput = gd.getNextBoolean();

    return true;
  }

  private static boolean contains(String[] imageList, String title) {
    for (final String t : imageList) {
      if (t.equals(title)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Creates the bounds for alignment.
   *
   * @param minXShift the min X shift
   * @param maxXShift the max X shift
   * @param minYShift the min Y shift
   * @param maxYShift the max Y shift
   * @return the rectangle
   */
  public static Rectangle createBounds(int minXShift, int maxXShift, int minYShift, int maxYShift) {
    final int w = maxXShift - minXShift;
    final int h = maxYShift - minYShift;
    return new Rectangle(minXShift, minYShift, w, h);
  }
}
