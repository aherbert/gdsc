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

package uk.ac.sussex.gdsc.ij.foci.controller;

import com.google.common.util.concurrent.Futures;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.frame.Recorder;
import ij.process.ImageStatistics;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import uk.ac.sussex.gdsc.ij.UsageTracker;
import uk.ac.sussex.gdsc.ij.foci.FindFociOptions;
import uk.ac.sussex.gdsc.ij.foci.FindFociOptions.OutputOption;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.AlgorithmOption;
import uk.ac.sussex.gdsc.ij.foci.FindFoci_PlugIn;
import uk.ac.sussex.gdsc.ij.foci.model.FindFociModel;

/**
 * Allows ImageJ to run the {@link uk.ac.sussex.gdsc.ij.foci.FindFoci_PlugIn } algorithm.
 */
public class ImageJController extends FindFociController {
  /** The executor service for the preview. */
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  /** The runner for the preview. Modifications to this should be synchronized. */
  private FindFociRunner runner;
  /**
   * The future containing the runner. This should never be null and should be done when the preview
   * is not running. Modifications to this should be synchronized.
   */
  private Future<?> future = Futures.immediateFuture(null);
  /** The lock used for synchronisation. */
  private final Object lock = new Object();

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
    final FindFociOptions options = model.getOptions();
    if (options.getOptions().isEmpty()) {
      IJ.error("Error", "No results options chosen");
      return;
    }

    final String maskImage = model.getMaskImage();
    final FindFociProcessorOptions processorOptions = model.getProcessorOptions();
    final boolean showLogMessages = model.isShowLogMessages();
    if (!model.isSaveResults()) {
      options.setResultsDirectory(null);
    }

    // Record the macro command
    if (Recorder.record) {
      // These options should match the parameter names assigned within the FindFoci GenericDialog.
      Recorder.setCommand(FindFoci_PlugIn.TITLE);
      Recorder.recordOption(FindFoci_PlugIn.OPTION_MASK, maskImage);
      Recorder.recordOption(FindFoci_PlugIn.OPTION_BACKGROUND_METHOD,
          processorOptions.getBackgroundMethod().getDescription());
      Recorder.recordOption(FindFoci_PlugIn.OPTION_BACKGROUND_PARAMETER,
          Double.toString(processorOptions.getBackgroundParameter()));
      Recorder.recordOption(FindFoci_PlugIn.OPTION_AUTO_THRESHOLD,
          processorOptions.getThresholdMethod().getDescription());
      Recorder.recordOption(FindFoci_PlugIn.OPTION_STASTISTICS_MODE,
          processorOptions.getStatisticsMethod().getDescription());
      Recorder.recordOption(FindFoci_PlugIn.OPTION_SEARCH_METHOD,
          processorOptions.getSearchMethod().getDescription());
      Recorder.recordOption(FindFoci_PlugIn.OPTION_SEARCH_PARAMETER,
          Double.toString(processorOptions.getSearchParameter()));
      Recorder.recordOption(FindFoci_PlugIn.OPTION_MINIMUM_SIZE,
          Integer.toString(processorOptions.getMinSize()));
      recordOption(FindFoci_PlugIn.OPTION_MINIMUM_SIZE_ABOVE_SADDLE,
          processorOptions.isOption(AlgorithmOption.MINIMUM_ABOVE_SADDLE));
      recordOption(FindFoci_PlugIn.OPTION_CONNECTED_ABOVE_SADDLE,
          processorOptions.isOption(AlgorithmOption.CONTIGUOUS_ABOVE_SADDLE));
      Recorder.recordOption(FindFoci_PlugIn.OPTION_MINIMUM_PEAK_HEIGHT,
          processorOptions.getPeakMethod().getDescription());
      Recorder.recordOption(FindFoci_PlugIn.OPTION_PEAK_PARAMETER,
          Double.toString(processorOptions.getPeakParameter()));
      Recorder.recordOption(FindFoci_PlugIn.OPTION_SORT_METHOD,
          processorOptions.getSortMethod().getDescription());
      Recorder.recordOption(FindFoci_PlugIn.OPTION_MAXIMUM_PEAKS,
          Integer.toString(processorOptions.getMaxPeaks()));
      Recorder.recordOption(FindFoci_PlugIn.OPTION_SHOW_MASK,
          processorOptions.getMaskMethod().getDescription());
      recordOption(FindFoci_PlugIn.OPTION_OVERLAY_MASK,
          options.isOption(OutputOption.OVERLAY_MASK));
      Recorder.recordOption(FindFoci_PlugIn.OPTION_FRACTION_PARAMETER,
          Double.toString(processorOptions.getFractionParameter()));
      recordOption(FindFoci_PlugIn.OPTION_SHOW_TABLE, options.isOption(OutputOption.RESULTS_TABLE));
      recordOption(FindFoci_PlugIn.OPTION_CLEAR_TABLE,
          options.isOption(OutputOption.CLEAR_RESULTS_TABLE));
      recordOption(FindFoci_PlugIn.OPTION_MARK_MAXIMA,
          options.isOption(OutputOption.ROI_SELECTION));
      recordOption(FindFoci_PlugIn.OPTION_MARK_PEAK_MAXIMA,
          options.isOption(OutputOption.MASK_ROI_SELECTION));
      recordOption(FindFoci_PlugIn.OPTION_MARK_USING_OVERLAY,
          options.isOption(OutputOption.ROI_USING_OVERLAY));
      recordOption(FindFoci_PlugIn.OPTION_HIDE_LABELS, options.isOption(OutputOption.HIDE_LABELS));
      recordOption(FindFoci_PlugIn.OPTION_SHOW_PEAK_MAXIMA_AS_DOTS,
          processorOptions.isOption(AlgorithmOption.OUTPUT_MASK_PEAK_DOTS));
      recordOption(FindFoci_PlugIn.OPTION_SHOW_LOG_MESSAGES, showLogMessages);
      recordOption(FindFoci_PlugIn.OPTION_REMOVE_EDGE_MAXIMA,
          processorOptions.isOption(AlgorithmOption.REMOVE_EDGE_MAXIMA));
      Recorder.recordOption(FindFoci_PlugIn.OPTION_MAXIMUM_SIZE,
          Integer.toString(processorOptions.getMaxSize()));
      if (options.getResultsDirectory() != null) {
        Recorder.recordOption(FindFoci_PlugIn.OPTION_RESULTS_DIRECTORY,
            options.getResultsDirectory());
      }
      if (options.isOption(OutputOption.OBJECT_ANALYSIS)) {
        Recorder.recordOption(FindFoci_PlugIn.OPTION_OBJECT_ANALYSIS);
        recordOption(FindFoci_PlugIn.OPTION_SHOW_OBJECT_MASK,
            options.isOption(OutputOption.SHOW_OBJECT_MASK));
      }
      recordOption(FindFoci_PlugIn.OPTION_SAVE_TO_MEMORY,
          options.isOption(OutputOption.SAVE_TO_MEMORY));
      Recorder.recordOption(FindFoci_PlugIn.OPTION_GAUSSIAN_BLUR,
          Double.toString(processorOptions.getGaussianBlur()));
      Recorder.recordOption(FindFoci_PlugIn.OPTION_CENTRE_METHOD,
          processorOptions.getCentreMethod().getDescription());
      Recorder.recordOption(FindFoci_PlugIn.OPTION_CENTRE_PARAMETER,
          Double.toString(processorOptions.getCentreParameter()));

      Recorder.saveCommand();
    }

    model.setUnchanged();

    final ImagePlus mask = WindowManager.getImage(maskImage);

    // Run the plugin
    UsageTracker.recordPlugin(FindFoci_PlugIn.class, "");
    final FindFoci_PlugIn ff = new FindFoci_PlugIn();
    ff.exec(imp, mask, processorOptions, options, showLogMessages);
  }

  private static void recordOption(String key, boolean option) {
    if (option) {
      Recorder.recordOption(key);
    }
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
    synchronized (lock) {
      if (future.isDone()) {
        runner = new FindFociRunner(this.listener);
        future = executor.submit(runner);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void endPreview() {
    synchronized (lock) {
      if (runner != null) {
        runner.finish();
        future.cancel(true);
      }
    }
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
