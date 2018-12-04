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

import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.foci.FindFociProcessor;
import uk.ac.sussex.gdsc.foci.FindFoci_PlugIn;
import uk.ac.sussex.gdsc.foci.model.FindFociModel;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.frame.Recorder;
import ij.process.ImageStatistics;

import java.util.ArrayList;
import java.util.List;

/**
 * Allows ImageJ to run the {@link uk.ac.sussex.gdsc.foci.FindFoci_PlugIn } algorithm.
 */
public class ImageJController extends FindFociController {
  private FindFociRunner runner = null;

  /**
   * Instantiates a new image J controller.
   *
   * @param model the model
   */
  public ImageJController(FindFociModel model) {
    super(model);
  }

  /** {@inheritDoc} */
  @Override
  public int getImageCount() {
    return WindowManager.getImageCount();
  }

  /** {@inheritDoc} */
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

  /** {@inheritDoc} */
  @Override
  public void run() {
    // Get the selected image
    final String title = model.getSelectedImage();
    final ImagePlus imp = WindowManager.getImage(title);
    if (null == imp) {
      return;
    }

    if (!FindFoci_PlugIn.isSupported(imp.getBitDepth())) {
      return;
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
    final boolean showMaskMaximaAsDots = model.isShowMaskMaximaAsDots();
    final boolean showLogMessages = model.isShowLogMessages();
    final boolean removeEdgeMaxima = model.isRemoveEdgeMaxima();
    final boolean saveResults = model.isSaveResults();
    final String resultsDirectory = model.getResultsDirectory();
    final boolean objectAnalysis = model.isObjectAnalysis();
    final boolean saveToMemory = model.isSaveToMemory();
    final boolean showObjectMask = model.isShowObjectMask();
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
    if (!showMaskMaximaAsDots) {
      outputType += FindFociProcessor.OUTPUT_MASK_NO_PEAK_DOTS;
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
    if (removeEdgeMaxima) {
      options |= FindFociProcessor.OPTION_REMOVE_EDGE_MAXIMA;
    }
    if (objectAnalysis) {
      options |= FindFociProcessor.OPTION_OBJECT_ANALYSIS;
      if (showObjectMask) {
        options |= FindFociProcessor.OPTION_SHOW_OBJECT_MASK;
      }
    }
    if (saveToMemory) {
      options |= FindFociProcessor.OPTION_SAVE_TO_MEMORY;
    }

    if (outputType == 0) {
      IJ.showMessage("Error", "No results options chosen");
      return;
    }

    // Record the macro command
    if (Recorder.record) {
      // These options should match the parameter names assigned within the FindFoci GenericDialog.
      Recorder.setCommand("FindFoci");
      Recorder.recordOption("Mask", maskImage);
      Recorder.recordOption("Background_method", FindFoci_PlugIn.backgroundMethods[backgroundMethod]);
      Recorder.recordOption("Background_parameter", "" + backgroundParameter);
      Recorder.recordOption("Auto_threshold", thresholdMethod);
      Recorder.recordOption("Statistics_mode", statisticsMode);
      Recorder.recordOption("Search_method", FindFoci_PlugIn.searchMethods[searchMethod]);
      Recorder.recordOption("Search_parameter", "" + searchParameter);
      Recorder.recordOption("Minimum_size", "" + minSize);
      if (minimumAboveSaddle) {
        Recorder.recordOption("Minimum_above_saddle");
      }
      if (connectedAboveSaddle) {
        Recorder.recordOption("Connected_above_saddle");
      }
      Recorder.recordOption("Minimum_peak_height", FindFoci_PlugIn.peakMethods[peakMethod]);
      Recorder.recordOption("Peak_parameter", "" + peakParameter);
      Recorder.recordOption("Sort_method", FindFoci_PlugIn.sortIndexMethods[sortMethod]);
      Recorder.recordOption("Maximum_peaks", "" + maxPeaks);
      Recorder.recordOption("Show_mask", FindFoci_PlugIn.maskOptions[showMask]);
      if (overlayMask) {
        Recorder.recordOption("Overlay_mask");
      }
      Recorder.recordOption("Fraction_parameter", "" + fractionParameter);
      if (showTable) {
        Recorder.recordOption("Show_table");
      }
      if (clearTable) {
        Recorder.recordOption("Clear_table");
      }
      if (markMaxima) {
        Recorder.recordOption("Mark_maxima");
      }
      if (markROIMaxima) {
        Recorder.recordOption("Mark_peak_maxima");
      }
      if (markUsingOverlay) {
        Recorder.recordOption("Mark_using_overlay");
      }
      if (hideLabels) {
        Recorder.recordOption("Hide_labels");
      }
      if (showMaskMaximaAsDots) {
        Recorder.recordOption("Show_peak_maxima_as_dots");
      }
      if (showLogMessages) {
        Recorder.recordOption("Show_log_messages");
      }
      if (removeEdgeMaxima) {
        Recorder.recordOption("Remove_edge_maxima");
      }
      if (saveResults) {
        Recorder.recordOption("Results_directory", resultsDirectory);
      }
      if (objectAnalysis) {
        Recorder.recordOption("Object_analysis");
        if (showObjectMask) {
          Recorder.recordOption("Show_object_mask");
        }
      }
      if (saveToMemory) {
        Recorder.recordOption("Save_to_memory");
      }
      Recorder.recordOption("Gaussian_blur", "" + gaussianBlur);
      Recorder.recordOption("Centre_method", FindFoci_PlugIn.getCentreMethods()[centreMethod]);
      Recorder.recordOption("Centre_parameter", "" + centreParameter);
      Recorder.saveCommand();
    }

    model.setUnchanged();

    final ImagePlus mask = WindowManager.getImage(maskImage);

    // Run the plugin
    UsageTracker.recordPlugin(FindFoci_PlugIn.class, "");
    final FindFoci_PlugIn ff = new FindFoci_PlugIn();
    if (saveResults) {
      ff.setResultsDirectory(resultsDirectory);
    }
    ff.exec(imp, mask, backgroundMethod, backgroundParameter, thresholdMethod, searchMethod,
        searchParameter, maxPeaks, minSize, peakMethod, peakParameter, outputType, sortMethod,
        options, gaussianBlur, centreMethod, centreParameter, fractionParameter);
  }

  /** {@inheritDoc} */
  @Override
  public void preview() {
    // Get the selected image
    final String title = model.getSelectedImage();
    final ImagePlus imp = WindowManager.getImage(title);
    if (null == imp) {
      return;
    }

    if (!FindFoci_PlugIn.isSupported(imp.getBitDepth())) {
      return;
    }

    startRunner();

    runner.queue(model);
  }

  private void startRunner() {
    if (runner == null || !runner.isAlive()) {
      runner = new FindFociRunner(this.listener);
      runner.start();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void endPreview() {
    if (runner != null) {
      runner.finish();
      runner.interrupt();
      runner = null;
    }
    System.gc();
  }

  /** {@inheritDoc} */
  @Override
  public int[] getImageLimits(int[] limits) {
    if (limits == null || limits.length < 2) {
      limits = new int[2];
    }
    final ImagePlus imp = WindowManager.getImage(model.getSelectedImage());
    if (imp != null) {
      final ImageStatistics stats = imp.getStatistics(ij.measure.Measurements.MIN_MAX);
      limits[0] = (int) stats.min;
      limits[1] = (int) stats.max;
    } else {
      limits[0] = 0;
      limits[1] = 255;
    }
    return limits;
  }
}
