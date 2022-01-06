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

package uk.ac.sussex.gdsc.foci.controller;

import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.process.ImageProcessor;
import java.util.ArrayList;
import java.util.List;
import uk.ac.sussex.gdsc.core.ij.gui.ExtendedGenericDialog;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions;
import uk.ac.sussex.gdsc.foci.FindFociResult;
import uk.ac.sussex.gdsc.foci.FindFociResults;
import uk.ac.sussex.gdsc.foci.FindFoci_PlugIn;
import uk.ac.sussex.gdsc.foci.model.FindFociModel;

/**
 * Allows ImageJ to run the {@link uk.ac.sussex.gdsc.foci.FindFoci_PlugIn } algorithm and return the
 * results.
 */
public class FindMaximaController extends ImageJController {
  private static final String[] SINGLE_SERIES = new String[] {"1"};

  private List<FindFociResult> resultsArray = new ArrayList<>();
  private ImageStack activeImageStack;
  private int activeChannel = 1;
  private int activeFrame = 1;

  /**
   * Instantiates a new find maxima controller.
   *
   * @param model the model
   */
  public FindMaximaController(FindFociModel model) {
    super(model);
  }

  /*
   * Allow N-Dimensional images. The controller can detect this and prompt the user for the desired
   * channel and frame for analysis.
   *
   * @see uk.ac.sussex.gdsc.foci.controller.ImageJController#updateImageList()
   */
  @Override
  public void updateImageList() {
    final int noOfImages = WindowManager.getImageCount();
    final List<String> imageList = new ArrayList<>(noOfImages);
    for (final int id : uk.ac.sussex.gdsc.core.ij.ImageJUtils.getIdList()) {
      final ImagePlus imp = WindowManager.getImage(id);

      // Image must be 8-bit/16-bit/32-bit
      if (imp != null && (imp.getType() == ImagePlus.GRAY8 || imp.getType() == ImagePlus.GRAY16
          || imp.getType() == ImagePlus.GRAY32)) {
        // Check it is not one the result images
        final String imageTitle = imp.getTitle();
        if (!imageTitle.endsWith(FindFoci_PlugIn.TITLE)) {
          imageList.add(imageTitle);
        }
      }
    }

    model.setImageList(imageList);

    // Get the selected image
    final String title = model.getSelectedImage();
    final ImagePlus imp = WindowManager.getImage(title);

    model.setMaskImageList(FindFoci_PlugIn.buildMaskList(imp));
  }

  /*
   * Allow N-dimensional images. Prompt for the channel and frame.
   *
   * @see uk.ac.sussex.gdsc.foci.controller.FindFociController#run()
   */
  @Override
  public void run() {
    resultsArray = new ArrayList<>();

    // Get the selected image
    final String title = model.getSelectedImage();
    ImagePlus imp = WindowManager.getImage(title);
    if (null == imp) {
      return;
    }

    if (!FindFoci_PlugIn.isSupported(imp.getBitDepth())) {
      return;
    }

    // Allow N-dimensional images. Prompt for the channel and frame.
    if (imp.getNChannels() != 1 || imp.getNFrames() != 1) {
      final ExtendedGenericDialog gd = new ExtendedGenericDialog("Select Channel/Frame");
      gd.addMessage("Stack detected.\nPlease select the channel/frame.");
      activeChannel = imp.getChannel();
      activeFrame = imp.getFrame();
      final String[] channels = getChannels(imp);
      final String[] frames = getFrames(imp);

      if (channels.length > 1) {
        gd.addChoice("Channel", channels, activeChannel - 1);
      }
      if (frames.length > 1) {
        gd.addChoice("Frame", frames, activeFrame - 1);
      }

      gd.showDialog();

      if (gd.wasCanceled()) {
        return;
      }

      // Extract the channel/frame
      activeChannel = (channels.length > 1) ? gd.getNextChoiceIndex() + 1 : 1;
      activeFrame = (frames.length > 1) ? gd.getNextChoiceIndex() + 1 : 1;

      activeImageStack = extractImage(imp, activeChannel, activeFrame);
      imp = new ImagePlus("", activeImageStack);
    } else {
      activeChannel = 1;
      activeFrame = 1;
      activeImageStack = imp.getStack();
    }

    // Set-up the FindFoci variables
    final String maskImage = model.getMaskImage();
    final FindFociProcessorOptions processorOptions = model.getProcessorOptions();

    model.setUnchanged();

    final ImagePlus mask = WindowManager.getImage(maskImage);

    final FindFoci_PlugIn ff = new FindFoci_PlugIn();
    final FindFociResults results =
        ff.createFindFociProcessor(imp).findMaxima(imp, mask, processorOptions);

    if (results != null) {
      final List<FindFociResult> newResultsArray = results.getResults();
      if (newResultsArray != null) {
        resultsArray = newResultsArray;
      }
    }
  }

  private static String[] getChannels(ImagePlus imp) {
    return getSeries(imp.getNChannels());
  }

  private static String[] getFrames(ImagePlus imp) {
    return getSeries(imp.getNFrames());
  }

  private static String[] getSeries(int size) {
    if (size == 1) {
      return SINGLE_SERIES;
    }
    final String[] result = new String[size];
    for (int i = 0; i < size; i++) {
      result[i] = Integer.toString(i + 1);
    }
    return result;
  }

  private static ImageStack extractImage(ImagePlus imp, int channel, int frame) {
    final int slices = imp.getNSlices();

    final ImageStack stack = new ImageStack(imp.getWidth(), imp.getHeight());
    final ImageStack inputStack = imp.getImageStack();

    for (int slice = 1; slice <= slices; slice++) {
      // Convert to a short processor
      final ImageProcessor ip = inputStack.getProcessor(imp.getStackIndex(channel, slice, frame));
      stack.addSlice(null, ip);
    }
    return stack;
  }

  /** {@inheritDoc} */
  @Override
  public void preview() {
    // Do nothing
  }

  /** {@inheritDoc} */
  @Override
  public void endPreview() {
    // Do nothing
  }

  /**
   * Gets the results array.
   *
   * @return the results array
   */
  public List<FindFociResult> getResultsArray() {
    return resultsArray;
  }

  /**
   * Gets the active image stack used within the find foci algorithm. This will be the sub-image
   * stack if an N-dimensional image was processed.
   *
   * @return The active image stack
   */
  public ImageStack getActiveImageStack() {
    return activeImageStack;
  }

  /**
   * Gets the active channel.
   *
   * @return the active channel
   */
  public int getActiveChannel() {
    return activeChannel;
  }

  /**
   * Gets the active frame.
   *
   * @return the active frame
   */
  public int getActiveFrame() {
    return activeFrame;
  }
}
