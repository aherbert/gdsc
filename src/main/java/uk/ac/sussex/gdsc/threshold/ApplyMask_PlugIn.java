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

package uk.ac.sussex.gdsc.threshold;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import java.util.ArrayList;
import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold;

/**
 * Create a mask from a source image and apply it to a target image. All pixels outside the mask
 * will be set to zero. The mask can be created using: an existing mask; thresholding or the minimum
 * display value. The source image for the mask can be a different image but the dimensions must
 * match.
 */
public class ApplyMask_PlugIn implements PlugInFilter {

  private static final String TITLE = "Apply Mask";

  private static String selectedImage = "";
  private static int selectedOption = MaskCreater_PlugIn.OPTION_MASK;
  private static String selectedThresholdMethod = AutoThreshold.Method.OTSU.toString();
  private static int selectedChannel;
  private static int selectedSlice;
  private static int selectedFrame;

  private ImagePlus imp;
  private ImagePlus maskImp;
  private int option;
  private String thresholdMethod;
  private int channel;
  private int slice;
  private int frame;

  /** {@inheritDoc} */
  @Override
  public int setup(String arg, ImagePlus imp) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    if (imp == null) {
      IJ.noImage();
      return DONE;
    }
    this.imp = imp;
    if (showDialog(this)) {
      applyMask();
    }
    return DONE;
  }

  /** {@inheritDoc} */
  @Override
  public void run(ImageProcessor ip) {
    // All process already done
  }

  /**
   * Show dialog.
   *
   * @return true, if successful
   */
  private static boolean showDialog(ApplyMask_PlugIn plugin) {
    final String sourceImage = "(Use target)";
    final ArrayList<String> imageList = new ArrayList<>();
    imageList.add(sourceImage);

    for (final int id : ImageJUtils.getIdList()) {
      final ImagePlus imp = WindowManager.getImage(id);
      if (imp != null) {
        imageList.add(imp.getTitle());
      }
    }

    final GenericDialog gd = new GenericDialog(TITLE);
    gd.addMessage("Create a mask from a source image and apply it.\n"
        + "Pixels outside the mask will be set to zero.");
    gd.addChoice("Mask_Image", imageList.toArray(new String[0]), selectedImage);
    final String[] options = MaskCreater_PlugIn.getOptions();
    gd.addChoice("Option", options, options[selectedOption]);
    gd.addChoice("Threshold_Method", AutoThreshold.getMethods(), selectedThresholdMethod);
    gd.addNumericField("Channel", selectedChannel, 0);
    gd.addNumericField("Slice", selectedSlice, 0);
    gd.addNumericField("Frame", selectedFrame, 0);
    gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.UTILITY);
    gd.showDialog();

    if (gd.wasCanceled()) {
      return false;
    }

    selectedImage = gd.getNextChoice();
    selectedOption = gd.getNextChoiceIndex();
    selectedThresholdMethod = gd.getNextChoice();
    selectedChannel = (int) gd.getNextNumber();
    selectedSlice = (int) gd.getNextNumber();
    selectedFrame = (int) gd.getNextNumber();

    if (!selectedImage.equals(sourceImage)) {
      plugin.setMaskImp(WindowManager.getImage(selectedImage));
    } else {
      plugin.setMaskImp(plugin.getImp());
    }
    plugin.setOption(selectedOption);
    plugin.setThresholdMethod(selectedThresholdMethod);
    plugin.setChannel(selectedChannel);
    plugin.setSlice(selectedSlice);
    plugin.setFrame(selectedFrame);

    return true;
  }

  /**
   * Instantiates a new apply mask.
   */
  public ApplyMask_PlugIn() {
    initialise(null, MaskCreater_PlugIn.OPTION_MASK);
  }

  /**
   * Instantiates a new apply mask.
   *
   * @param imp the image
   */
  public ApplyMask_PlugIn(ImagePlus imp) {
    initialise(imp, MaskCreater_PlugIn.OPTION_MASK);
  }

  /**
   * Instantiates a new apply mask.
   *
   * @param imp the image
   * @param option the option
   */
  public ApplyMask_PlugIn(ImagePlus imp, int option) {
    initialise(imp, option);
  }

  /**
   * Set the image and options for processing.
   *
   * @param imp the imp
   * @param option the option
   */
  private void initialise(ImagePlus imp, int option) {
    this.imp = imp;
    this.option = option;
  }

  /**
   * Create a mask from a source image and apply it to a target image. All pixels outside the mask
   * will be set to zero.
   */
  public void applyMask() {
    if (imp == null) {
      return;
    }

    final MaskCreater_PlugIn mc = new MaskCreater_PlugIn();
    mc.setImp(maskImp);
    mc.setOption(selectedOption);
    mc.setThresholdMethod(selectedThresholdMethod);
    mc.setChannel(selectedChannel);
    mc.setSlice(selectedSlice);
    mc.setFrame(selectedFrame);
    maskImp = mc.createMask();

    // Check the mask has the correct dimensions
    if (maskImp == null) {
      IJ.error(TITLE, "No mask calculated");
      return;
    }
    if (imp.getWidth() != maskImp.getWidth() || imp.getHeight() != maskImp.getHeight()) {
      IJ.error(TITLE, "Calculated mask does not match the target image dimensions");
      return;
    }

    // Check other dimensions && log dimension mismatch
    final int[] dimensions1 = imp.getDimensions();
    final int[] dimensions2 = maskImp.getDimensions();

    final String[] dimName = {"C", "Z", "T"};

    StringBuilder sb = null;
    for (int i = 0, j = 2; i < 3; i++, j++) {
      if (dimensions1[j] != dimensions2[j]) {
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

    // Apply the mask to the correct stack dimensions
    final int[] channels = createArray(dimensions1[2]);
    final int[] slices = createArray(dimensions1[3]);
    final int[] frames = createArray(dimensions1[4]);

    final ImageStack imageStack = imp.getStack();
    final ImageStack maskStack = maskImp.getStack();

    for (final int t : frames) {
      for (final int z : slices) {
        for (final int c : channels) {
          final ImageProcessor ip = imageStack.getProcessor(imp.getStackIndex(c, z, t));

          // getStackIndex will clip to the mask dimensions
          final ImageProcessor maskIp = maskStack.getProcessor(maskImp.getStackIndex(c, z, t));

          for (int i = maskIp.getPixelCount(); i-- > 0;) {
            if (maskIp.get(i) == 0) {
              ip.set(i, 0);
            }
          }
        }
      }
    }

    imp.updateAndDraw();
  }

  /**
   * Creates the array.
   *
   * @param total the total
   * @return the int[]
   */
  private static int[] createArray(int total) {
    final int[] array = new int[total];
    for (int i = 0; i < array.length; i++) {
      array[i] = i + 1;
    }
    return array;
  }

  /**
   * Set the target image for the masking.
   *
   * @param imp the new imp
   */
  public void setImp(ImagePlus imp) {
    this.imp = imp;
  }

  /**
   * Get the target image for the masking.
   *
   * @return the imp
   */
  public ImagePlus getImp() {
    return imp;
  }

  /**
   * Set imp the source image for the mask generation.
   *
   * @param imp the new mask imp
   */
  public void setMaskImp(ImagePlus imp) {
    this.maskImp = imp;
  }

  /**
   * Get the source image for the mask generation.
   *
   * @return the mask imp
   */
  public ImagePlus getMaskImp() {
    return maskImp;
  }

  /**
   * Set option the option for defining the mask.
   *
   * @param option the new option
   */
  public void setOption(int option) {
    this.option = option;
  }

  /**
   * Get the option for defining the mask.
   *
   * @return the option
   */
  public int getOption() {
    return option;
  }

  /**
   * Set thresholdMethod the thresholdMethod to set.
   *
   * @param thresholdMethod the new threshold method
   */
  public void setThresholdMethod(String thresholdMethod) {
    this.thresholdMethod = thresholdMethod;
  }

  /**
   * Get the thresholdMethod.
   *
   * @return the threshold method
   */
  public String getThresholdMethod() {
    return thresholdMethod;
  }

  /**
   * Set channel the channel to set.
   *
   * @param channel the new channel
   */
  public void setChannel(int channel) {
    this.channel = channel;
  }

  /**
   * Get the channel.
   *
   * @return the channel
   */
  public int getChannel() {
    return channel;
  }

  /**
   * Set frame the frame to set.
   *
   * @param frame the new frame
   */
  public void setFrame(int frame) {
    this.frame = frame;
  }

  /**
   * Get the frame.
   *
   * @return the frame
   */
  public int getFrame() {
    return frame;
  }

  /**
   * Set slice the slice to set.
   *
   * @param slice the new slice
   */
  public void setSlice(int slice) {
    this.slice = slice;
  }

  /**
   * Get the slice.
   *
   * @return the slice
   */
  public int getSlice() {
    return slice;
  }
}
