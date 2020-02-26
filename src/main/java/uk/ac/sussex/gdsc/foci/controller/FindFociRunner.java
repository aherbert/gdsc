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

package uk.ac.sussex.gdsc.foci.controller;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.exception.ExceptionUtils;
import uk.ac.sussex.gdsc.foci.FindFociBaseProcessor;
import uk.ac.sussex.gdsc.foci.FindFociInitResults;
import uk.ac.sussex.gdsc.foci.FindFociMergeResults;
import uk.ac.sussex.gdsc.foci.FindFociMergeTempResults;
import uk.ac.sussex.gdsc.foci.FindFociOptions;
import uk.ac.sussex.gdsc.foci.FindFociPrelimResults;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions;
import uk.ac.sussex.gdsc.foci.FindFociResult;
import uk.ac.sussex.gdsc.foci.FindFociResults;
import uk.ac.sussex.gdsc.foci.FindFociSearchResults;
import uk.ac.sussex.gdsc.foci.FindFoci_PlugIn;
import uk.ac.sussex.gdsc.foci.controller.MessageListener.MessageType;
import uk.ac.sussex.gdsc.foci.model.FindFociModel;
import uk.ac.sussex.gdsc.foci.model.FindFociState;

/**
 * Runs the {@link uk.ac.sussex.gdsc.foci.FindFoci_PlugIn } algorithm using input from a
 * synchronised queueing method.
 */
public class FindFociRunner implements Runnable {
  /** The next model to be processed. Only updated when holding the lock. */
  private FindFociModel nextModel;
  /** The model that was previously processed. Set at the end of processing a model. */
  private FindFociModel previousModel;
  /** The lock used when updating the next model. */
  private final Object lock = new Object();
  /** The listener for messages. */
  private final MessageListener listener;

  /**
   * Flag to indicate the thread is allowed to run.
   *
   * <p>Volatile to allow concurrent access.
   */
  private volatile boolean running = true;

  /** The FindFoci instance used to create the processor. */
  private final FindFoci_PlugIn ff = new FindFoci_PlugIn();
  /**
   * The processor must be initialised for each new image so the dimensions and lookup tables are
   * computed.
   */
  private FindFociBaseProcessor processor;
  // Staged processing results
  private ImagePlus imp2;
  private FindFociInitResults initResults;
  private FindFociInitResults searchInitResults;
  private FindFociSearchResults searchArray;
  private FindFociInitResults mergeInitResults;
  private FindFociInitResults resultsInitResults;
  private FindFociInitResults maskInitResults;
  private FindFociMergeTempResults mergePeakResults;
  private FindFociMergeTempResults mergeSizeResults;
  private FindFociMergeResults mergeResults;
  private FindFociPrelimResults prelimResults;
  private FindFociResults results;

  /**
   * Instantiates a new find foci runner.
   *
   * @param listener the listener
   */
  public FindFociRunner(MessageListener listener) {
    this.listener = listener;
    notifyListener(MessageType.READY);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    try {
      while (running) {
        // Check if there is a model to run. Synchronized to avoid conflict with the queue() method.
        FindFociModel modelToRun = null;
        synchronized (lock) {
          if (nextModel != null) {
            modelToRun = nextModel.deepCopy();
            // Mark this as processed
            nextModel = null;
          } else {
            // Wait for a new model to be queued
            lock.wait();
          }
        }

        // Check for a model
        if (modelToRun != null) {
          // Note:
          // This system currently has to wait for the last calculation to finish before
          // looking for the next model to run. This means for long running calculations the
          // user may have to wait a long time for the last one to finish, then wait again for
          // the next.
          // Ideally we would run the calculation on a different thread. We can then do the model
          // comparison here. If the next model to run resets to an earlier state than the
          // current model then we should cancel the current calculation and start again, part way
          // through.
          // Basically this needs better interrupt handling for long running jobs.
          runFindFoci(modelToRun);
        }
      }
    } catch (final InterruptedException ex) {
      if (running) {
        IJ.log("FindPeakRunner interupted: " + ex.getLocalizedMessage());
      }
      Thread.currentThread().interrupt();
    } catch (final Exception thrown) {
      // Log this to ImageJ. Do not bubble up exceptions
      if (thrown.getMessage() != null) {
        IJ.log("An error occurred during processing: " + thrown.getMessage());
      } else {
        IJ.log("An error occurred during processing");
      }
      if (IJ.debugMode) {
        IJ.log(ExceptionUtils.getStackTrace(thrown));
      }

      notifyListener(MessageType.ERROR, thrown);
    } finally {
      notifyListener(MessageType.BACKGROUND_LEVEL, 0.0f);
      notifyListener(MessageType.SORT_INDEX_OK, 0.0f);
      notifyListener(MessageType.FINISHED);
    }
  }

  /**
   * Notify the listener with a message.
   *
   * @param messageType the message type
   * @param params the message parameters
   */
  private void notifyListener(MessageType messageType, Object... params) {
    if (listener != null) {
      listener.notify(messageType, params);
    }
  }

  /**
   * Add a FindFociModel for processing.
   *
   * @param newModel the new model
   */
  public void queue(FindFociModel newModel) {
    if (newModel != null) {
      synchronized (lock) {
        nextModel = newModel;
        lock.notifyAll();
      }
    }
  }

  private void runFindFoci(FindFociModel model) {
    // Get the selected image
    final String title = model.getSelectedImage();
    final ImagePlus imp = WindowManager.getImage(title);
    if (null == imp) {
      notifyListener(MessageType.ERROR);
      return;
    }

    if (!FindFoci_PlugIn.isSupported(imp.getBitDepth())) {
      notifyListener(MessageType.ERROR);
      return;
    }

    // Set-up the FindFoci variables
    final FindFociOptions options = model.getOptions();
    if (options.getOptions().isEmpty()) {
      notifyListener(MessageType.ERROR);
      return;
    }

    final String maskImage = model.getMaskImage();
    final FindFociProcessorOptions processorOptions = model.getProcessorOptions();
    // Ignore these settings
    // Ignore: model.isShowLogMessages()
    // Ignore: model.isSaveResults()
    // Ignore: model.getResultsDirectory()
    options.setResultsDirectory(null);

    final ImagePlus mask = WindowManager.getImage(maskImage);

    IJ.showStatus(FindFoci_PlugIn.TITLE + " calculating ...");
    notifyListener(MessageType.RUNNING);

    // Compare this model with the previously computed results and
    // only update the parts that are necessary.
    final FindFociState state = compareModels(model, previousModel);
    previousModel = null;

    if (state.ordinal() <= FindFociState.INITIAL.ordinal()) {
      processor = ff.createFindFociProcessor(imp);
      imp2 = processor.blur(imp, processorOptions.getGaussianBlur());
      if (imp2 == null) {
        notifyFailed();
        return;
      }
    }
    if (state.ordinal() <= FindFociState.FIND_MAXIMA.ordinal()) {
      initResults = processor.findMaximaInit(imp, imp2, mask, processorOptions);
      if (initResults == null) {
        notifyFailed();
        return;
      }

      notifyListener(MessageType.BACKGROUND_LEVEL, initResults.stats.getBackground());
    }
    if (state.ordinal() <= FindFociState.SEARCH.ordinal()) {
      searchInitResults = processor.copyForStagedProcessing(initResults, searchInitResults);
      searchArray = processor.findMaximaSearch(searchInitResults, processorOptions);
      if (searchArray == null) {
        notifyFailed();
        return;
      }

      notifyListener(MessageType.BACKGROUND_LEVEL, searchInitResults.stats.getBackground());
    }
    if (state.ordinal() <= FindFociState.MERGE_HEIGHT.ordinal()) {
      // No clone as the maxima and types are not changed
      mergePeakResults =
          processor.findMaximaMergePeak(searchInitResults, searchArray, processorOptions);
      if (mergePeakResults == null) {
        notifyFailed();
        return;
      }
    }
    if (state.ordinal() <= FindFociState.MERGE_SIZE.ordinal()) {
      // No clone as the maxima and types are not changed
      mergeSizeResults =
          processor.findMaximaMergeSize(searchInitResults, mergePeakResults, processorOptions);
      if (mergeSizeResults == null) {
        notifyFailed();
        return;
      }
    }
    if (state.ordinal() <= FindFociState.MERGE_SADDLE.ordinal()) {
      mergeInitResults = processor.copyForStagedProcessing(searchInitResults, mergeInitResults);
      mergeResults =
          processor.findMaximaMergeFinal(mergeInitResults, mergeSizeResults, processorOptions);
      if (mergeResults == null) {
        notifyFailed();
        return;
      }
    }
    if (state.ordinal() <= FindFociState.CALCULATE_RESULTS.ordinal()) {
      if (initResults.stats.getImageMinimum() < 0 && FindFociBaseProcessor
          .isSortMethodSensitiveToNegativeValues(processorOptions.getSortMethod())) {
        notifyListener(MessageType.SORT_INDEX_SENSITIVE_TO_NEGATIVE_VALUES,
            initResults.stats.getImageMinimum());
      } else {
        notifyListener(MessageType.SORT_INDEX_OK, initResults.stats.getImageMinimum());
      }

      resultsInitResults = processor.copyForStagedProcessing(mergeInitResults, resultsInitResults);
      prelimResults =
          processor.findMaximaPrelimResults(resultsInitResults, mergeResults, processorOptions);
      if (prelimResults == null) {
        notifyFailed();
        return;
      }
    }
    if (state.ordinal() <= FindFociState.CALCULATE_OUTPUT_MASK.ordinal()) {
      maskInitResults = processor.copyForStagedProcessing(resultsInitResults, maskInitResults);
      results = processor.findMaximaMaskResults(maskInitResults, mergeResults, prelimResults,
          processorOptions);
      if (results == null) {
        notifyFailed();
        return;
      }
    }
    if (state.ordinal() <= FindFociState.SHOW_RESULTS.ordinal()) {
      FindFoci_PlugIn.showResults(imp, mask, processorOptions, options, processor, results, true);
    }

    IJ.showStatus(FindFoci_PlugIn.TITLE + " finished");
    notifyListener(MessageType.DONE);

    previousModel = model;
  }

  /**
   * Compare two models and identify the state of the calculation.
   *
   * @param model the model
   * @param previousModel the previous model
   * @return The state
   */
  private FindFociState compareModels(FindFociModel model, FindFociModel previousModel) {
    boolean ignoreChange = false;

    if (previousModel == null
        || notEqual(model.getSelectedImage(), previousModel.getSelectedImage())
        || notEqual(model.getGaussianBlur(), previousModel.getGaussianBlur())) {
      return FindFociState.INITIAL;
    }

    if (notEqual(model.getBackgroundMethod(), previousModel.getBackgroundMethod())
        || notEqual(model.getMaskImage(), previousModel.getMaskImage())
        || notEqual(model.getBackgroundParameter(), previousModel.getBackgroundParameter())
        || notEqual(model.getThresholdMethod(), previousModel.getThresholdMethod())
    // || notEqual(model.isShowLogMessages(), previousModel.isShowLogMessages())
    ) {
      return FindFociState.FIND_MAXIMA;
    }

    if (notEqual(model.getSearchMethod(), previousModel.getSearchMethod())
        || notEqual(model.getSearchParameter(), previousModel.getSearchParameter())) {
      return FindFociState.SEARCH;
    }

    if (notEqual(model.getPeakMethod(), previousModel.getPeakMethod())
        || notEqual(model.getPeakParameter(), previousModel.getPeakParameter())) {
      return FindFociState.MERGE_HEIGHT;
    }

    if (notEqual(model.getMinSize(), previousModel.getMinSize())) {
      return FindFociState.MERGE_SIZE;
    }

    if (notEqual(model.isMinimumAboveSaddle(), previousModel.isMinimumAboveSaddle())
        || notEqual(model.isRemoveEdgeMaxima(), previousModel.isRemoveEdgeMaxima())) {
      return FindFociState.MERGE_SADDLE;
    }
    if (notEqual(model.isConnectedAboveSaddle(), previousModel.isConnectedAboveSaddle())) {
      if (model.isMinimumAboveSaddle()) {
        return FindFociState.MERGE_SADDLE;
      }
      ignoreChange = true;
    }

    if (notEqual(model.getSortMethod(), previousModel.getSortMethod())
        || notEqual(model.getCentreMethod(), previousModel.getCentreMethod())
        || notEqual(model.getCentreParameter(), previousModel.getCentreParameter())) {
      return FindFociState.CALCULATE_RESULTS;
    }

    // Special case where the change is only relevant if previous model was at the limit
    if (notEqual(model.getMaxPeaks(), previousModel.getMaxPeaks())) {
      final List<FindFociResult> resultsArrayList = results.getResults();
      final int change = model.getMaxPeaks() - previousModel.getMaxPeaks();
      if ((change > 0 && resultsArrayList.size() >= previousModel.getMaxPeaks())
          || (change < 0 && resultsArrayList.size() > model.getMaxPeaks())) {
        return FindFociState.CALCULATE_RESULTS;
      }
      ignoreChange = true;
    }

    if (notEqual(model.getShowMask(), previousModel.getShowMask())
        || notEqual(model.isOverlayMask(), previousModel.isOverlayMask())) {
      return FindFociState.CALCULATE_OUTPUT_MASK;
    }
    if (notEqual(model.getFractionParameter(), previousModel.getFractionParameter())) {
      if (model.getShowMask() >= 5) {
        return FindFociState.CALCULATE_OUTPUT_MASK;
      }
      ignoreChange = true;
    }

    if (notEqual(model.isShowTable(), previousModel.isShowTable())
        || notEqual(model.isClearTable(), previousModel.isClearTable())
        || notEqual(model.isMarkMaxima(), previousModel.isMarkMaxima())
        || notEqual(model.isMarkUsingOverlay(), previousModel.isMarkUsingOverlay())
        || notEqual(model.isHideLabels(), previousModel.isHideLabels())
        || notEqual(model.isMarkRoiMaxima(), previousModel.isMarkRoiMaxima())) {
      return FindFociState.SHOW_RESULTS;
    }

    if (notEqual(model.isObjectAnalysis(), previousModel.isObjectAnalysis())) {
      // Only repeat if switched on. We can ignore it if the flag is switched off since the results
      // will be the same.
      if (model.isObjectAnalysis()) {
        return FindFociState.SHOW_RESULTS;
      }
      ignoreChange = true;
    }

    if (notEqual(model.isShowObjectMask(), previousModel.isShowObjectMask())) {
      // Only repeat if the mask is to be shown
      if (model.isObjectAnalysis() && model.isShowObjectMask()) {
        return FindFociState.SHOW_RESULTS;
      }
      ignoreChange = true;
    }

    if (notEqual(model.isSaveToMemory(), previousModel.isSaveToMemory())) {
      // Only repeat if switched on. We can ignore it if the flag is switched off since the results
      // will be the same.
      if (model.isSaveToMemory()) {
        return FindFociState.SHOW_RESULTS;
      }
      ignoreChange = true;
    }

    // Options to ignore. They will still trigger a model comparison.
    if (notEqual(model.isShowLogMessages(), previousModel.isShowLogMessages())
        || notEqual(model.getResultsDirectory(), previousModel.getResultsDirectory())
        || notEqual(model.isSaveResults(), previousModel.isSaveResults())) {
      ignoreChange = true;
    }

    if (ignoreChange) {
      return FindFociState.COMPLETE;
    }

    // Unknown model change.
    // Default to do the entire calculation.
    return FindFociState.INITIAL;
  }

  private static boolean notEqual(double newValue, double oldValue) {
    return newValue != oldValue;
  }

  private static boolean notEqual(int newValue, int oldValue) {
    return newValue != oldValue;
  }

  private static boolean notEqual(boolean newValue, boolean oldValue) {
    return newValue != oldValue;
  }

  private static boolean notEqual(String newValue, String oldValue) {
    return !Objects.equals(oldValue, newValue);
  }

  private void notifyFailed() {
    IJ.showStatus(FindFoci_PlugIn.TITLE + " failed");
    notifyListener(MessageType.FAILED);
  }

  /**
   * Finish. Call this to shut down the thread.
   *
   * @see java.lang.Thread#run()
   */
  public void finish() {
    if (running) {
      running = false;
      // Notify if waiting
      synchronized (lock) {
        lock.notifyAll();
      }
    }
  }
}
