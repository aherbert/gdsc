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
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.HyperStackReducer;
import ij.plugin.PlugIn;
import ij.plugin.ZProjector;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.awt.Rectangle;
import java.util.ArrayList;
import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.ij.AlignImagesFft;
import uk.ac.sussex.gdsc.core.ij.AlignImagesFft.SubPixelMethod;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold.Method;
import uk.ac.sussex.gdsc.core.utils.ImageWindow.WindowMethod;

/**
 * Aligns open image stacks to a reference stack using XY translation to maximise the correlation.
 *
 * <p>Takes in: The reference image; Z-stack projection (maximum/average); Optional Max/Min values
 * for the X and Y translation; and Optional sub-pixel accuracy.
 *
 * <p>Accepts multi-dimensional stacks. For each timepoint a maximum/average intensity projection is
 * performed per channel. The channels are tiled to a composite image. The composite is then aligned
 * using the maximum correlation between the images. The translation is applied to the entire stack
 * for that timepoint.
 */
public class AlignStacks_PlugIn implements PlugIn {
  private static final String TITLE = "Align Stacks";
  private static final String SELF_ALIGN = "selfAlign";

  private static String reference = "";
  private static boolean selfAlign;
  private static int projectionMethod = ZProjector.MAX_METHOD;
  private static int myWindowFunction = 3; // Tukey
  private static boolean restrictTranslation;
  private static int minXShift = -20;
  private static int maxXShift = 20;
  private static int minYShift = -20;
  private static int maxYShift = 20;
  private static final String[] subPixelMethods = AlignImagesFft_PlugIn.getSubPixelMethods();
  private static int subPixelMethod = 2;
  private static final String[] interpolationMethods =
      AlignImagesFft_PlugIn.getInterpolationMethods();
  private static int interpolationMethod = ImageProcessor.NONE;
  private static boolean clipOutput;
  private static final String[] windowFunctions = AlignImagesFft_PlugIn.getWindowFunctions();

  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    final String[] imageList = getImagesList();

    if (imageList.length < 1) {
      IJ.showMessage("Error", "Only 8-bit and 16-bit images are supported (2 images required)");
      return;
    }

    final boolean selfAlignMode = (arg != null && arg.equals(SELF_ALIGN));

    if (!showDialog(imageList, selfAlignMode)) {
      return;
    }

    final ImagePlus refImp = WindowManager.getImage(reference);
    if (refImp == null) {
      IJ.log("Failed to find: " + reference);
      return;
    }

    Rectangle bounds = null;
    if (restrictTranslation) {
      bounds = createBounds(minXShift, maxXShift, minYShift, maxYShift);
    }

    final WindowMethod wm = WindowMethod.values()[myWindowFunction];
    final SubPixelMethod spm = SubPixelMethod.values()[subPixelMethod];
    if (selfAlign) {
      exec(refImp, refImp, projectionMethod, wm, bounds, spm, interpolationMethod, clipOutput);
    } else {
      for (final ImagePlus targetImp : getTargetImages(refImp)) {
        exec(refImp, targetImp, projectionMethod, wm, bounds, spm, interpolationMethod, clipOutput);
      }
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

  private static boolean showDialog(String[] imageList, boolean selfAlignMode) {
    final GenericDialog gd = new GenericDialog(TITLE);

    if (selfAlignMode) {
      if (WindowManager.getCurrentImage() != null) {
        reference = WindowManager.getCurrentImage().getTitle();
      }
      selfAlign = true;
      interpolationMethod = ImageProcessor.NONE;
      clipOutput = false;
    }

    String msg = (selfAlignMode) ? "Align all frames to the current frame."
        : "Align all open stacks with the same XYC dimensions as the reference.";
    msg += "\nZ stacks per time frame are projected and multiple channels\n"
        + "are tiled to a single image used to define the offset for\nthe frame.\n";
    if (!selfAlignMode) {
      msg += "Optionally align a single stack to the currect frame";
    }
    gd.addMessage(msg);

    gd.addChoice("Reference_image", imageList, reference);
    if (!selfAlignMode) {
      gd.addCheckbox("Self-align", selfAlign);
    }
    gd.addChoice("Projection", ZProjector.METHODS, ZProjector.METHODS[projectionMethod]);
    gd.addChoice("Window_function", windowFunctions, windowFunctions[myWindowFunction]);
    gd.addCheckbox("Restrict_translation", restrictTranslation);
    gd.addNumericField("Min_X_translation", minXShift, 0);
    gd.addNumericField("Max_X_translation", maxXShift, 0);
    gd.addNumericField("Min_Y_translation", minYShift, 0);
    gd.addNumericField("Max_Y_translation", maxYShift, 0);
    gd.addChoice("Sub-pixel_method", subPixelMethods, subPixelMethods[subPixelMethod]);
    gd.addChoice("Interpolation", interpolationMethods, interpolationMethods[interpolationMethod]);
    gd.addCheckbox("Clip_output", clipOutput);
    gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.UTILITY);

    gd.showDialog();

    if (!gd.wasOKed()) {
      return false;
    }

    reference = gd.getNextChoice();
    if (!selfAlignMode) {
      selfAlign = gd.getNextBoolean();
    }
    projectionMethod = gd.getNextChoiceIndex();
    myWindowFunction = gd.getNextChoiceIndex();
    restrictTranslation = gd.getNextBoolean();
    minXShift = (int) gd.getNextNumber();
    maxXShift = (int) gd.getNextNumber();
    minYShift = (int) gd.getNextNumber();
    maxYShift = (int) gd.getNextNumber();
    subPixelMethod = gd.getNextChoiceIndex();
    interpolationMethod = gd.getNextChoiceIndex();
    clipOutput = gd.getNextBoolean();

    return true;
  }

  private static ArrayList<ImagePlus> getTargetImages(ImagePlus referenceImp) {
    final ArrayList<ImagePlus> imageList = new ArrayList<>();

    final int[] referenceDimensions = referenceImp.getDimensions();

    for (final int id : ImageJUtils.getIdList()) {
      final ImagePlus imp = WindowManager.getImage(id);

      // Image must be the same type and dimensions
      if (imp != null && imp.getType() == referenceImp.getType()
          && imp.getID() != referenceImp.getID()
          && isMatchingDimensions(referenceDimensions, imp.getDimensions())) {
        imageList.add(imp);
      }
    }

    return imageList;
  }

  private static boolean isMatchingDimensions(int[] referenceDimensions, int[] dimensions) {
    // Allow aligning a stack to an image of the same dimensions XYC if the reference has only one
    // frame
    if (referenceDimensions[4] == 1) {
      for (int i = 0; i < 3; i++) {
        if (dimensions[i] != referenceDimensions[i]) {
          return false;
        }
      }
      return true;
    }

    // Check for complete match
    for (int i = 0; i < referenceDimensions.length; i++) {
      if (dimensions[i] != referenceDimensions[i]) {
        return false;
      }
    }
    return true;
  }

  private static void exec(ImagePlus refImp, ImagePlus targetImp, int projectionMethod,
      WindowMethod windowFunction, Rectangle bounds, SubPixelMethod subPixelMethod,
      int interpolationMethod, boolean clipOutput) {
    // Check same size
    if (!isValid(refImp, targetImp)) {
      return;
    }

    // Store initial positions
    final int c1 = refImp.getChannel();
    final int z1 = refImp.getSlice();
    final int t1 = refImp.getFrame();
    final int c2 = targetImp.getChannel();
    final int z2 = targetImp.getSlice();
    final int t2 = targetImp.getFrame();

    if (refImp.getNFrames() == 1 || selfAlign) {
      final ImageProcessor ip1 = createComposite(refImp, t1, projectionMethod, windowFunction);
      final ImagePlus imp1 = new ImagePlus("ip1", ip1);
      final AlignImagesFft align = new AlignImagesFft();
      align.setDoTranslation(false);
      align.initialiseReference(imp1, windowFunction, true);

      // For each time point
      for (int frame = 1; frame <= targetImp.getNFrames(); frame++) {
        if (selfAlign && frame == t1) {
          continue;
        }

        IJ.showProgress(frame, targetImp.getNFrames());

        // Build composite image for the timepoint
        final ImageProcessor ip2 =
            createComposite(targetImp, frame, projectionMethod, windowFunction);

        // Align the image
        final ImagePlus imp2 = new ImagePlus("ip2", ip2);
        align.align(imp2, WindowMethod.NONE, bounds, subPixelMethod, interpolationMethod,
            clipOutput);

        // Transform original stack
        final ImageStack stack = targetImp.getImageStack();
        for (int channel = 1; channel <= targetImp.getNChannels(); channel++) {
          for (int slice = 1; slice <= targetImp.getNSlices(); slice++) {
            final int index = targetImp.getStackIndex(channel, slice, frame);
            final ImageProcessor ip = stack.getProcessor(index);
            AlignImages_PlugIn.translateProcessor(interpolationMethod, ip, align.getLastXOffset(),
                align.getLastYOffset(), clipOutput);
          }
        }

        if (ImageJUtils.isInterrupted()) {
          return;
        }
      }
    } else {
      // For each time point
      for (int frame = 1; frame <= targetImp.getNFrames(); frame++) {
        IJ.showProgress(frame, targetImp.getNFrames());

        // Build composite image for the timepoint
        final ImageProcessor ip1 = createComposite(refImp, frame, projectionMethod, windowFunction);
        final ImageProcessor ip2 =
            createComposite(targetImp, frame, projectionMethod, windowFunction);

        // Align the image
        final ImagePlus imp1 = new ImagePlus("ip1", ip1);
        final ImagePlus imp2 = new ImagePlus("ip2", ip2);
        final AlignImagesFft align = new AlignImagesFft();
        align.align(imp1, imp2, WindowMethod.NONE, bounds, subPixelMethod, interpolationMethod,
            true, null, null, null, clipOutput);

        // Transform original stack
        final ImageStack stack = targetImp.getImageStack();
        for (int channel = 1; channel <= targetImp.getNChannels(); channel++) {
          for (int slice = 1; slice <= targetImp.getNSlices(); slice++) {
            final int index = targetImp.getStackIndex(channel, slice, frame);
            final ImageProcessor ip = stack.getProcessor(index);
            AlignImages_PlugIn.translateProcessor(interpolationMethod, ip, align.getLastXOffset(),
                align.getLastYOffset(), clipOutput);
          }
        }

        if (ImageJUtils.isInterrupted()) {
          return;
        }
      }
    }

    // Reset input images
    refImp.setPosition(c1, z1, t1);
    targetImp.setPosition(c2, z2, t2);
    targetImp.updateAndDraw();
  }

  /**
   * Creates the bounds for the shift.
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

  private static boolean isValid(ImagePlus refImp, ImagePlus targetImp) {
    if (refImp == null || targetImp == null) {
      return false;
    }
    if (refImp.getType() != targetImp.getType()) {
      return false;
    }
    return isMatchingDimensions(refImp.getDimensions(), targetImp.getDimensions());
  }

  private static ImageProcessor createComposite(ImagePlus imp, int frame, int projectionMethod,
      WindowMethod windowFunction) {
    // Extract the channels using the specified projection method
    final FloatProcessor[] tiles = new FloatProcessor[imp.getNChannels()];
    for (int channel = 1; channel <= imp.getNChannels(); channel++) {
      tiles[channel - 1] = extractTile(imp, frame, channel, projectionMethod);
      tiles[channel - 1] = AlignImagesFft.applyWindowSeparable(tiles[channel - 1], windowFunction);

      // Normalise so each image contributes equally to the alignment
      normalise(tiles[channel - 1]);
    }

    // Build a composite image
    final int w = imp.getWidth();
    final int h = imp.getHeight();

    // Calculate total dimensions by tiling always along the smallest dimension
    // to produce the smallest possible output image.
    int w2 = w;
    int h2 = h;
    int horizontalTiles = 1;
    int verticalTiles = 1;
    do {
      while (w2 <= h2 && horizontalTiles * verticalTiles < imp.getNChannels()) {
        horizontalTiles++;
        w2 += w;
      }
      while (h2 <= w2 && horizontalTiles * verticalTiles < imp.getNChannels()) {
        verticalTiles++;
        h2 += h;
      }
    } while (horizontalTiles * verticalTiles < imp.getNChannels());

    // Create output composite
    final FloatProcessor ip = new FloatProcessor(w2, h2);

    for (int channel = 1; channel <= imp.getNChannels(); channel++) {
      final int x = (channel - 1) % horizontalTiles;
      final int y = (channel - 1) / horizontalTiles;

      ip.insert(tiles[channel - 1], x * w, y * h);
    }

    return ip;
  }

  private static FloatProcessor extractTile(ImagePlus imp, int frame, int channel,
      int projectionMethod) {
    return ImageJUtils.extractTile(imp, frame, channel, projectionMethod).toFloat(1, null);
  }

  /**
   * Normalises the image. Performs a thresholding on the image using the Otsu method. Then scales
   * the pixels above the threshold from 0 to 255.
   *
   * @param ip The input image
   */
  private static void normalise(FloatProcessor ip) {
    final float[] pixels = (float[]) ip.getPixels();

    final ShortProcessor sp = (ShortProcessor) ip.convertToShort(true);
    final int[] data = sp.getHistogram();
    final int threshold = AutoThreshold.getThreshold(Method.OTSU, data);
    final float minf = threshold;
    final float maxf = (float) sp.getMax();

    final float scaleFactor = 255.0f / (maxf - minf);

    for (int i = pixels.length; i-- > 0;) {
      pixels[i] = (Math.max(sp.get(i), minf) - minf) * scaleFactor;
    }
  }

  /**
   * Extracts the specified frame from the hyperstack.
   *
   * @param imp the image
   * @param frame the frame
   * @return the image plus
   */
  public static ImagePlus extractFrame(ImagePlus imp, int frame) {
    imp.setPositionWithoutUpdate(1, 1, frame);

    // Extract the timepoint into a new stack
    final HyperStackReducer reducer = new HyperStackReducer(imp);
    final int channels = imp.getNFrames();
    final int slices = imp.getNSlices();
    final ImagePlus imp1 = imp.createHyperStack("", channels, slices, 1, imp.getBitDepth());
    reducer.reduce(imp1);

    return imp1;
  }

  /**
   * Run the plugin with self-alignment parameters.
   */
  public static void selfAlign() {
    new AlignStacks_PlugIn().run(SELF_ALIGN);
  }
}
