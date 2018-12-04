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

package uk.ac.sussex.gdsc.foci.controller;

import uk.ac.sussex.gdsc.foci.FindFociProcessor;
import uk.ac.sussex.gdsc.foci.FindFociResult;
import uk.ac.sussex.gdsc.foci.FindFociResults;
import uk.ac.sussex.gdsc.foci.FindFoci_PlugIn;
import uk.ac.sussex.gdsc.foci.model.FindFociModel;

import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.process.ImageProcessor;

import java.util.ArrayList;
import java.util.List;

/**
 * Allows ImageJ to run the {@link uk.ac.sussex.gdsc.foci.FindFoci_PlugIn } algorithm and return the
 * results.
 */
public class FindMaximaController extends ImageJController {
  private ArrayList<FindFociResult> resultsArray = new ArrayList<>();
  private ImageStack activeImageStack = null;
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
      final GenericDialog gd = new GenericDialog("Select Channel/Frame");
      gd.addMessage("Stack detected.\nPlease select the channel/frame.");
      final String[] channels = getChannels(imp);
      final String[] frames = getFrames(imp);

      if (channels.length > 1) {
        gd.addChoice("Channel", channels,
            channels[channels.length >= activeChannel ? activeChannel - 1 : 0]);
      }
      if (frames.length > 1) {
        gd.addChoice("Frame", frames, frames[frames.length >= activeFrame ? activeFrame - 1 : 0]);
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
      activeImageStack = imp.getStack();
    }

    // Set-up the FindFoci variables
    final String maskImage = model.getMaskImage();
    final int backgroundMethod = model.getBackgroundMethod();
    final double backgroundParameter = model.getBackgroundParameter();
    final String thresholdMethod = model.getThresholdMethod();
    final String statisticsMode = model.getStatisticsMode();
    final int searchMethod = model.getSearchMethod();
    final double searchParameter = model.getSearchParameter();
    final int minSize = model.getMinSize();
    final boolean minimumAboveSaddle = model.isMinimumAboveSaddle();
    final boolean connectedAboveSaddle = model.isConnectedAboveSaddle();
    final int peakMethod = model.getPeakMethod();
    final double peakParameter = model.getPeakParameter();
    final int sortMethod = model.getSortMethod();
    final int maxPeaks = model.getMaxPeaks();
    final int showMask = model.getShowMask();
    final boolean overlayMask = model.isOverlayMask();
    final boolean showTable = model.isShowTable();
    final boolean clearTable = model.isClearTable();
    final boolean markMaxima = model.isMarkMaxima();
    final boolean markROIMaxima = model.isMarkROIMaxima();
    final boolean markUsingOverlay = model.isMarkUsingOverlay();
    final boolean hideLabels = model.isHideLabels();
    final boolean showLogMessages = model.isShowLogMessages();
    final double gaussianBlur = model.getGaussianBlur();
    final int centreMethod = model.getCentreMethod();
    final double centreParameter = model.getCentreParameter();
    final double fractionParameter = model.getFractionParameter();

    int outputType = FindFoci_PlugIn.getOutputMaskFlags(showMask);

    if (overlayMask) {
      outputType += FindFociProcessor.OUTPUT_OVERLAY_MASK;
    }
    if (showTable) {
      outputType += FindFociProcessor.OUTPUT_RESULTS_TABLE;
    }
    if (clearTable) {
      outputType += FindFociProcessor.OUTPUT_CLEAR_RESULTS_TABLE;
    }
    if (markMaxima) {
      outputType += FindFociProcessor.OUTPUT_ROI_SELECTION;
    }
    if (markROIMaxima) {
      outputType += FindFociProcessor.OUTPUT_MASK_ROI_SELECTION;
    }
    if (markUsingOverlay) {
      outputType += FindFociProcessor.OUTPUT_ROI_USING_OVERLAY;
    }
    if (hideLabels) {
      outputType += FindFociProcessor.OUTPUT_HIDE_LABELS;
    }
    if (showLogMessages) {
      outputType += FindFociProcessor.OUTPUT_LOG_MESSAGES;
    }

    int options = 0;
    if (minimumAboveSaddle) {
      options |= FindFociProcessor.OPTION_MINIMUM_ABOVE_SADDLE;
    }
    if (connectedAboveSaddle) {
      options |= FindFociProcessor.OPTION_CONTIGUOUS_ABOVE_SADDLE;
    }
    if (statisticsMode.equalsIgnoreCase("inside")) {
      options |= FindFociProcessor.OPTION_STATS_INSIDE;
    } else if (statisticsMode.equalsIgnoreCase("outside")) {
      options |= FindFociProcessor.OPTION_STATS_OUTSIDE;
    }

    if (outputType == 0) {
      return;
    }

    model.setUnchanged();

    final ImagePlus mask = WindowManager.getImage(maskImage);

    final FindFoci_PlugIn ff = new FindFoci_PlugIn();
    final FindFociResults results =
        ff.findMaxima(imp, mask, backgroundMethod, backgroundParameter, thresholdMethod,
            searchMethod, searchParameter, maxPeaks, minSize, peakMethod, peakParameter, outputType,
            sortMethod, options, gaussianBlur, centreMethod, centreParameter, fractionParameter);

    if (results != null) {
      final ArrayList<FindFociResult> newResultsArray = results.results;
      if (newResultsArray != null) {
        resultsArray = newResultsArray;
      }
    }
  }

  private static String[] getChannels(ImagePlus imp) {
    final int c = imp.getNChannels();
    final String[] result = new String[c];
    for (int i = 0; i < c; i++) {
      result[i] = Integer.toString(i + 1);
    }
    return result;
  }

  private static String[] getFrames(ImagePlus imp) {
    final int c = imp.getNFrames();
    final String[] result = new String[c];
    for (int i = 0; i < c; i++) {
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
   * @return the resultsArray.
   */
  public ArrayList<FindFociResult> getResultsArray() {
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
   * @return the activeChannel.
   */
  public int getActiveChannel() {
    return activeChannel;
  }

  /**
   * @return the activeFrame.
   */
  public int getActiveFrame() {
    return activeFrame;
  }
}
