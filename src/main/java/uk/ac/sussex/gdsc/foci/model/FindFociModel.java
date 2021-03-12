/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2020 Alex Herbert
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

package uk.ac.sussex.gdsc.foci.model;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import uk.ac.sussex.gdsc.foci.FindFociOptions;
import uk.ac.sussex.gdsc.foci.FindFociOptions.OutputOption;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.AlgorithmOption;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.BackgroundMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.CentreMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.MaskMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.PeakMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.SearchMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.SortMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.StatisticsMethod;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.ThresholdMethod;

/**
 * Provides a bean property model for the {@link uk.ac.sussex.gdsc.foci.FindFoci_PlugIn} algorithm.
 *
 * <p>Wraps the {@link FindFociProcessorOptions} and {@link FindFociOptions} and allows Enum
 * properties to be set using indices.
 */
public class FindFociModel extends AbstractModelObject {
  /**
   * The property name for the {@link PropertyChangeEvent} raised in
   * {@link #setBackgroundMethod(int)}.
   */
  public static final String PROPERTY_BACKGROUND_METHOD = "backgroundMethod";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in
   * {@link #setBackgroundParameter(double)}.
   */
  public static final String PROPERTY_BACKGROUND_PARAMETER = "backgroundParameter";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in
   * {@link #setThresholdMethod(int)}.
   */
  public static final String PROPERTY_THRESHOLD_METHOD = "thresholdMethod";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in
   * {@link #setStatisticsMode(int)}.
   */
  public static final String PROPERTY_STATISTICS_MODE = "statisticsMode";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in {@link #setSearchMethod(int)}.
   */
  public static final String PROPERTY_SEARCH_METHOD = "searchMethod";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in
   * {@link #setSearchParameter(double)}.
   */
  public static final String PROPERTY_SEARCH_PARAMETER = "searchParameter";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in {@link #setMinSize(int)}.
   */
  public static final String PROPERTY_MIN_SIZE = "minSize";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in {@link #setMaxSize(int)}.
   */
  public static final String PROPERTY_MAX_SIZE = "maxSize";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in
   * {@link #setMinimumAboveSaddle(boolean)}.
   */
  public static final String PROPERTY_MINIMUM_ABOVE_SADDLE = "minimumAboveSaddle";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in
   * {@link #setConnectedAboveSaddle(boolean)}.
   */
  public static final String PROPERTY_CONNECTED_ABOVE_SADDLE = "connectedAboveSaddle";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in {@link #setPeakMethod(int)}.
   */
  public static final String PROPERTY_PEAK_METHOD = "peakMethod";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in
   * {@link #setPeakParameter(double)}.
   */
  public static final String PROPERTY_PEAK_PARAMETER = "peakParameter";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in {@link #setSortMethod(int)}.
   */
  public static final String PROPERTY_SORT_METHOD = "sortMethod";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in {@link #setMaxPeaks(int)}.
   */
  public static final String PROPERTY_MAX_PEAKS = "maxPeaks";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in {@link #setShowMask(int)}.
   */
  public static final String PROPERTY_SHOW_MASK = "showMask";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in
   * {@link #setOverlayMask(boolean)}.
   */
  public static final String PROPERTY_OVERLAY_MASK = "overlayMask";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in {@link #setShowTable(boolean)}.
   */
  public static final String PROPERTY_SHOW_TABLE = "showTable";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in
   * {@link #setClearTable(boolean)}.
   */
  public static final String PROPERTY_CLEAR_TABLE = "clearTable";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in
   * {@link #setMarkMaxima(boolean)}.
   */
  public static final String PROPERTY_MARK_MAXIMA = "markMaxima";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in
   * {@link #setMarkRoiMaxima(boolean)}.
   */
  public static final String PROPERTY_MARK_ROI_MAXIMA = "markRoiMaxima";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in
   * {@link #setMarkUsingOverlay(boolean)}.
   */
  public static final String PROPERTY_MARK_USING_OVERLAY = "markUsingOverlay";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in
   * {@link #setHideLabels(boolean)}.
   */
  public static final String PROPERTY_HIDE_LABELS = "hideLabels";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in
   * {@link #setShowMaskMaximaAsDots(boolean)}.
   */
  public static final String PROPERTY_SHOW_MASK_MAXIMA_AS_DOTS = "showMaskMaximaAsDots";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in
   * {@link #setShowLogMessages(boolean)}.
   */
  public static final String PROPERTY_SHOW_LOG_MESSAGES = "showLogMessages";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in
   * {@link #setRemoveEdgeMaxima(boolean)}.
   */
  public static final String PROPERTY_REMOVE_EDGE_MAXIMA = "removeEdgeMaxima";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in
   * {@link #setSaveResults(boolean)}.
   */
  public static final String PROPERTY_SAVE_RESULTS = "saveResults";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in
   * {@link #setResultsDirectory(String)}.
   */
  public static final String PROPERTY_RESULTS_DIRECTORY = "resultsDirectory";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in
   * {@link #setGaussianBlur(double)}.
   */
  public static final String PROPERTY_GAUSSIAN_BLUR = "gaussianBlur";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in {@link #setCentreMethod(int)}.
   */
  public static final String PROPERTY_CENTRE_METHOD = "centreMethod";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in
   * {@link #setCentreParameter(double)}.
   */
  public static final String PROPERTY_CENTRE_PARAMETER = "centreParameter";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in
   * {@link #setFractionParameter(double)}.
   */
  public static final String PROPERTY_FRACTION_PARAMETER = "fractionParameter";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in {@link #setImageList(List)}.
   */
  public static final String PROPERTY_IMAGE_LIST = "imageList";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in
   * {@link #setSelectedImage(String)}.
   */
  public static final String PROPERTY_SELECTED_IMAGE = "selectedImage";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in
   * {@link #setMaskImageList(List)}.
   */
  public static final String PROPERTY_MASK_IMAGE_LIST = "maskImageList";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in {@link #setMaskImage(String)}.
   */
  public static final String PROPERTY_MASK_IMAGE = "maskImage";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in
   * {@link #setObjectAnalysis(boolean)}.
   */
  public static final String PROPERTY_OBJECT_ANALYSIS = "objectAnalysis";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in
   * {@link #setShowObjectMask(boolean)}.
   */
  public static final String PROPERTY_SHOW_OBJECT_MASK = "showObjectMask";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in
   * {@link #setSaveToMemory(boolean)}.
   */
  public static final String PROPERTY_SAVE_TO_MEMORY = "saveToMemory";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in {@link #setUnchanged()}.
   */
  public static final String PROPERTY_CHANGED = "changed";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in {@link #invalidate()}.
   */
  public static final String PROPERTY_VALID = "valid";

  private final FindFociProcessorOptions processorOptions;
  private final FindFociOptions options;
  private boolean saveResults;
  private boolean showLogMessages;
  private List<String> imageList;
  private String selectedImage;
  private List<String> maskImageList;
  private String maskImage;
  private boolean changed;

  /**
   * Used to swap between the background parameter for absolute values and others.
   */
  private double backgroundParameterMemory;
  /**
   * Used to swap between the peak parameter for absolute values and others.
   */
  private double peakParameterMemory;

  /**
   * Default constructor.
   */
  public FindFociModel() {
    // Set defaults
    processorOptions = new FindFociProcessorOptions();
    options = new FindFociOptions();
    showLogMessages = true;
    imageList = new ArrayList<>();
    selectedImage = "";
    maskImageList = new ArrayList<>();
    maskImage = "";

    // Notify if any properties change
    this.addPropertyChangeListener(evt -> {
      if (!changed && !(PROPERTY_CHANGED.equals(evt.getPropertyName()))) {
        setChanged(true);
      }
    });
  }

  /**
   * Copy constructor.
   *
   * @param source the source
   */
  public FindFociModel(FindFociModel source) {
    // Copy properties
    processorOptions = source.processorOptions.copy();
    options = source.options.copy();
    saveResults = source.saveResults;
    showLogMessages = source.showLogMessages;
    imageList = new ArrayList<>(source.imageList);
    selectedImage = source.selectedImage;
    maskImageList = new ArrayList<>(source.maskImageList);
    maskImage = source.maskImage;
    changed = source.changed;
    backgroundParameterMemory = source.backgroundParameterMemory;
    peakParameterMemory = source.peakParameterMemory;
  }

  /**
   * Performs a deep copy.
   *
   * @return A copy of this object
   */
  public FindFociModel deepCopy() {
    return new FindFociModel(this);
  }

  /**
   * Sets the background method.
   *
   * @param backgroundMethod the new background method
   */
  public void setBackgroundMethod(int backgroundMethod) {
    final int oldValue = getBackgroundMethod();
    processorOptions.setBackgroundMethod(BackgroundMethod.fromOrdinal(backgroundMethod));
    firePropertyChange(PROPERTY_BACKGROUND_METHOD, oldValue, getBackgroundMethod());

    // Check if this is a switch to/from absolute background
    if (oldValue != backgroundMethod && (oldValue == BackgroundMethod.ABSOLUTE.ordinal()
        || backgroundMethod == BackgroundMethod.ABSOLUTE.ordinal())) {
      final double current = getBackgroundParameter();
      setBackgroundParameter(backgroundParameterMemory);
      backgroundParameterMemory = current;
    }
  }

  /**
   * Gets the background method.
   *
   * @return the backgroundMethod.
   */
  public int getBackgroundMethod() {
    return processorOptions.getBackgroundMethod().ordinal();
  }

  /**
   * Sets the background parameter.
   *
   * @param backgroundParameter the new background parameter
   */
  public void setBackgroundParameter(double backgroundParameter) {
    final double oldValue = getBackgroundParameter();
    processorOptions.setBackgroundParameter(backgroundParameter);
    firePropertyChange(PROPERTY_BACKGROUND_PARAMETER, oldValue, backgroundParameter);
  }

  /**
   * Gets the background parameter.
   *
   * @return the backgroundParameter.
   */
  public double getBackgroundParameter() {
    return processorOptions.getBackgroundParameter();
  }

  /**
   * Sets the threshold method.
   *
   * @param thresholdMethod the new threshold method
   */
  public void setThresholdMethod(int thresholdMethod) {
    final int oldValue = getThresholdMethod();
    processorOptions.setThresholdMethod(ThresholdMethod.fromOrdinal(thresholdMethod));
    firePropertyChange(PROPERTY_THRESHOLD_METHOD, oldValue, thresholdMethod);
  }

  /**
   * Gets the threshold method.
   *
   * @return the thresholdMethod.
   */
  public int getThresholdMethod() {
    return processorOptions.getThresholdMethod().ordinal();
  }

  /**
   * Sets the statistics mode.
   *
   * @param statisticsMode the new statistics mode
   */
  public void setStatisticsMode(int statisticsMode) {
    final int oldValue = getStatisticsMode();
    processorOptions.setStatisticsMethod(StatisticsMethod.fromOrdinal(statisticsMode));
    firePropertyChange(PROPERTY_STATISTICS_MODE, oldValue, statisticsMode);
  }

  /**
   * Gets the statistics mode.
   *
   * @return the statisticsMode.
   */
  public int getStatisticsMode() {
    return processorOptions.getStatisticsMethod().ordinal();
  }

  /**
   * Sets the search method.
   *
   * @param searchMethod the new search method
   */
  public void setSearchMethod(int searchMethod) {
    final int oldValue = getSearchMethod();
    processorOptions.setSearchMethod(SearchMethod.fromOrdinal(searchMethod));
    firePropertyChange(PROPERTY_SEARCH_METHOD, oldValue, searchMethod);
  }

  /**
   * Gets the search method.
   *
   * @return the searchMethod.
   */
  public int getSearchMethod() {
    return processorOptions.getSearchMethod().ordinal();
  }

  /**
   * Sets the search parameter.
   *
   * @param searchParameter the new search parameter
   */
  public void setSearchParameter(double searchParameter) {
    final double oldValue = getSearchParameter();
    processorOptions.setSearchParameter(searchParameter);
    firePropertyChange(PROPERTY_SEARCH_PARAMETER, oldValue, searchParameter);
  }

  /**
   * Gets the search parameter.
   *
   * @return the searchParameter.
   */
  public double getSearchParameter() {
    return processorOptions.getSearchParameter();
  }

  /**
   * Sets the min size.
   *
   * @param minSize the new min size
   */
  public void setMinSize(int minSize) {
    final int oldValue = getMinSize();
    processorOptions.setMinSize(minSize);
    firePropertyChange(PROPERTY_MIN_SIZE, oldValue, minSize);
  }

  /**
   * Gets the min size.
   *
   * @return the minSize.
   */
  public int getMinSize() {
    return processorOptions.getMinSize();
  }

  /**
   * Sets the max size.
   *
   * @param maxSize the new max size
   */
  public void setMaxSize(int maxSize) {
    final int oldValue = getMaxSize();
    processorOptions.setMaxSize(maxSize);
    firePropertyChange(PROPERTY_MAX_SIZE, oldValue, maxSize);
  }

  /**
   * Gets the max size.
   *
   * @return the maxSize.
   */
  public int getMaxSize() {
    return processorOptions.getMaxSize();
  }

  /**
   * Sets the minimum above saddle.
   *
   * @param minimumAboveSaddle the new minimum above saddle
   */
  public void setMinimumAboveSaddle(boolean minimumAboveSaddle) {
    if (setOption(AlgorithmOption.MINIMUM_ABOVE_SADDLE, minimumAboveSaddle)) {
      firePropertyChange(PROPERTY_MINIMUM_ABOVE_SADDLE, !minimumAboveSaddle, minimumAboveSaddle);
    }
  }

  /**
   * Checks if is minimum above saddle.
   *
   * @return true, if is minimum above saddle
   */
  public boolean isMinimumAboveSaddle() {
    return isOption(AlgorithmOption.MINIMUM_ABOVE_SADDLE);
  }

  /**
   * Sets the connected above saddle.
   *
   * @param connectedAboveSaddle the new connected above saddle
   */
  public void setConnectedAboveSaddle(boolean connectedAboveSaddle) {
    if (setOption(AlgorithmOption.CONTIGUOUS_ABOVE_SADDLE, connectedAboveSaddle)) {
      firePropertyChange(PROPERTY_CONNECTED_ABOVE_SADDLE, !connectedAboveSaddle,
          connectedAboveSaddle);
    }
  }

  /**
   * Checks if is connected above saddle.
   *
   * @return true, if is connected above saddle
   */
  public boolean isConnectedAboveSaddle() {
    return isOption(AlgorithmOption.CONTIGUOUS_ABOVE_SADDLE);
  }

  /**
   * Sets the peak method.
   *
   * @param peakMethod the new peak method
   */
  public void setPeakMethod(int peakMethod) {
    final int oldValue = getPeakMethod();
    processorOptions.setPeakMethod(PeakMethod.fromOrdinal(peakMethod));
    firePropertyChange(PROPERTY_PEAK_METHOD, oldValue, peakMethod);

    // Check if this is a switch to/from absolute background
    if (oldValue != peakMethod && (oldValue == PeakMethod.ABSOLUTE.ordinal()
        || peakMethod == PeakMethod.ABSOLUTE.ordinal())) {
      final double current = getPeakParameter();
      setPeakParameter(peakParameterMemory);
      peakParameterMemory = current;
    }
  }

  /**
   * Gets the peak method.
   *
   * @return the peakMethod.
   */
  public int getPeakMethod() {
    return processorOptions.getPeakMethod().ordinal();
  }

  /**
   * Sets the peak parameter.
   *
   * @param peakParameter the new peak parameter
   */
  public void setPeakParameter(double peakParameter) {
    final double oldValue = getPeakParameter();
    processorOptions.setPeakParameter(peakParameter);
    firePropertyChange(PROPERTY_PEAK_PARAMETER, oldValue, peakParameter);
  }

  /**
   * Gets the peak parameter.
   *
   * @return the peakParameter.
   */
  public double getPeakParameter() {
    return processorOptions.getPeakParameter();
  }

  /**
   * Sets the sort method.
   *
   * @param sortMethod the new sort method
   */
  public void setSortMethod(int sortMethod) {
    final double oldValue = getSortMethod();
    processorOptions.setSortMethod(SortMethod.fromOrdinal(sortMethod));
    firePropertyChange(PROPERTY_SORT_METHOD, oldValue, sortMethod);
  }

  /**
   * Gets the sort method.
   *
   * @return the sortMethod.
   */
  public int getSortMethod() {
    return processorOptions.getSortMethod().ordinal();
  }

  /**
   * Sets the max peaks.
   *
   * @param maxPeaks the new max peaks
   */
  public void setMaxPeaks(int maxPeaks) {
    final double oldValue = getMaxPeaks();
    processorOptions.setMaxPeaks(maxPeaks);
    firePropertyChange(PROPERTY_MAX_PEAKS, oldValue, maxPeaks);
  }

  /**
   * Gets the max peaks.
   *
   * @return the maxPeaks.
   */
  public int getMaxPeaks() {
    return processorOptions.getMaxPeaks();
  }

  /**
   * Sets the show mask.
   *
   * @param showMask the new show mask
   */
  public void setShowMask(int showMask) {
    final int oldValue = getShowMask();
    processorOptions.setMaskMethod(MaskMethod.fromOrdinal(showMask));
    firePropertyChange(PROPERTY_SHOW_MASK, oldValue, showMask);
  }

  /**
   * Gets the show mask.
   *
   * @return the showMask.
   */
  public int getShowMask() {
    return processorOptions.getMaskMethod().ordinal();
  }

  /**
   * Sets the overlay mask.
   *
   * @param overlayMask the new overlay mask
   */
  public void setOverlayMask(boolean overlayMask) {
    if (setOption(OutputOption.OVERLAY_MASK, overlayMask)) {
      firePropertyChange(PROPERTY_OVERLAY_MASK, !overlayMask, overlayMask);
    }
  }

  /**
   * Checks if is overlay mask.
   *
   * @return true, if is overlay mask
   */
  public boolean isOverlayMask() {
    return isOption(OutputOption.OVERLAY_MASK);
  }

  /**
   * Sets the show table.
   *
   * @param showTable the new show table
   */
  public void setShowTable(boolean showTable) {
    if (setOption(OutputOption.RESULTS_TABLE, showTable)) {
      firePropertyChange(PROPERTY_SHOW_TABLE, !showTable, showTable);
    }
  }

  /**
   * Checks if is show table.
   *
   * @return true, if is show table
   */
  public boolean isShowTable() {
    return isOption(OutputOption.RESULTS_TABLE);
  }

  /**
   * Sets the clear table.
   *
   * @param clearTable the new clear table
   */
  public void setClearTable(boolean clearTable) {
    if (setOption(OutputOption.CLEAR_RESULTS_TABLE, clearTable)) {
      firePropertyChange(PROPERTY_CLEAR_TABLE, !clearTable, clearTable);
    }
  }

  /**
   * Checks if is clear table.
   *
   * @return true, if is clear table
   */
  public boolean isClearTable() {
    return isOption(OutputOption.CLEAR_RESULTS_TABLE);
  }

  /**
   * Sets the mark maxima.
   *
   * @param markMaxima the new mark maxima
   */
  public void setMarkMaxima(boolean markMaxima) {
    if (setOption(OutputOption.ROI_SELECTION, markMaxima)) {
      firePropertyChange(PROPERTY_MARK_MAXIMA, !markMaxima, markMaxima);
    }
  }

  /**
   * Checks if is mark maxima.
   *
   * @return true, if is mark maxima
   */
  public boolean isMarkMaxima() {
    return isOption(OutputOption.ROI_SELECTION);
  }

  /**
   * Sets the mark ROI maxima.
   *
   * @param markRoiMaxima the new mark roi maxima
   */
  public void setMarkRoiMaxima(boolean markRoiMaxima) {
    if (setOption(OutputOption.MASK_ROI_SELECTION, markRoiMaxima)) {
      firePropertyChange(PROPERTY_MARK_ROI_MAXIMA, !markRoiMaxima, markRoiMaxima);
    }
  }

  /**
   * Checks if is mark ROI maxima.
   *
   * @return true, if is mark roi maxima
   */
  public boolean isMarkRoiMaxima() {
    return isOption(OutputOption.MASK_ROI_SELECTION);
  }

  /**
   * Sets the mark using overlay.
   *
   * @param markUsingOverlay the new mark using overlay
   */
  public void setMarkUsingOverlay(boolean markUsingOverlay) {
    if (setOption(OutputOption.ROI_USING_OVERLAY, markUsingOverlay)) {
      firePropertyChange(PROPERTY_MARK_USING_OVERLAY, !markUsingOverlay, markUsingOverlay);
    }
  }

  /**
   * Checks if is mark using overlay.
   *
   * @return the markUsingOverlay.
   */
  public boolean isMarkUsingOverlay() {
    return isOption(OutputOption.ROI_USING_OVERLAY);
  }

  /**
   * Sets the hide labels.
   *
   * @param hideLabels the new hide labels
   */
  public void setHideLabels(boolean hideLabels) {
    if (setOption(OutputOption.HIDE_LABELS, hideLabels)) {
      firePropertyChange(PROPERTY_HIDE_LABELS, !hideLabels, hideLabels);
    }
  }

  /**
   * Checks if is hide labels.
   *
   * @return the hideLabels.
   */
  public boolean isHideLabels() {
    return isOption(OutputOption.HIDE_LABELS);
  }

  /**
   * Sets the show mask maxima as dots.
   *
   * @param showMaskMaximaAsDots the new show mask maxima as dots
   */
  public void setShowMaskMaximaAsDots(boolean showMaskMaximaAsDots) {
    if (setOption(AlgorithmOption.OUTPUT_MASK_PEAK_DOTS, showMaskMaximaAsDots)) {
      firePropertyChange(PROPERTY_SHOW_MASK_MAXIMA_AS_DOTS, !showMaskMaximaAsDots,
          showMaskMaximaAsDots);
    }
  }

  /**
   * Checks if is show mask maxima as dots.
   *
   * @return the showMaskMaximaAsDots.
   */
  public boolean isShowMaskMaximaAsDots() {
    return isOption(AlgorithmOption.OUTPUT_MASK_PEAK_DOTS);
  }

  /**
   * Sets the show log messages.
   *
   * @param showLogMessages the new show log messages
   */
  public void setShowLogMessages(boolean showLogMessages) {
    final boolean oldValue = this.showLogMessages;
    this.showLogMessages = showLogMessages;
    firePropertyChange(PROPERTY_SHOW_LOG_MESSAGES, oldValue, showLogMessages);
  }

  /**
   * Checks if is show log messages.
   *
   * @return the showLogMessages.
   */
  public boolean isShowLogMessages() {
    return showLogMessages;
  }

  /**
   * Sets the removes the edge maxima.
   *
   * @param removeEdgeMaxima the new removes the edge maxima
   */
  public void setRemoveEdgeMaxima(boolean removeEdgeMaxima) {
    if (setOption(AlgorithmOption.REMOVE_EDGE_MAXIMA, removeEdgeMaxima)) {
      firePropertyChange(PROPERTY_REMOVE_EDGE_MAXIMA, !removeEdgeMaxima, removeEdgeMaxima);
    }
  }

  /**
   * Checks if is removes the edge maxima.
   *
   * @return the removeEdgeMaxima.
   */
  public boolean isRemoveEdgeMaxima() {
    return isOption(AlgorithmOption.REMOVE_EDGE_MAXIMA);
  }

  /**
   * Sets the save results.
   *
   * @param saveResults the new save results
   */
  public void setSaveResults(boolean saveResults) {
    final boolean oldValue = this.saveResults;
    this.saveResults = saveResults;
    firePropertyChange(PROPERTY_SAVE_RESULTS, oldValue, saveResults);
  }

  /**
   * Checks if is save results.
   *
   * @return the saveResults.
   */
  public boolean isSaveResults() {
    return saveResults;
  }

  /**
   * Sets the results directory.
   *
   * @param resultsDirectory the new results directory
   */
  public void setResultsDirectory(String resultsDirectory) {
    final String oldValue = getResultsDirectory();
    options.setResultsDirectory(resultsDirectory);
    firePropertyChange(PROPERTY_RESULTS_DIRECTORY, oldValue, resultsDirectory);
  }

  /**
   * Gets the results directory.
   *
   * @return the resultsDirectory.
   */
  public String getResultsDirectory() {
    return options.getResultsDirectory();
  }

  /**
   * Sets the gaussian blur.
   *
   * @param gaussianBlur the new gaussian blur
   */
  public void setGaussianBlur(double gaussianBlur) {
    final double oldValue = getGaussianBlur();
    processorOptions.setGaussianBlur(gaussianBlur);
    firePropertyChange(PROPERTY_GAUSSIAN_BLUR, oldValue, gaussianBlur);
  }

  /**
   * Gets the gaussian blur.
   *
   * @return the gaussianBlur.
   */
  public double getGaussianBlur() {
    return processorOptions.getGaussianBlur();
  }

  /**
   * Sets the centre method.
   *
   * @param centreMethod the new centre method
   */
  public void setCentreMethod(int centreMethod) {
    final double oldValue = getCentreMethod();
    processorOptions.setCentreMethod(CentreMethod.fromOrdinal(centreMethod));
    firePropertyChange(PROPERTY_CENTRE_METHOD, oldValue, centreMethod);
  }

  /**
   * Gets the centre method.
   *
   * @return the centreMethod.
   */
  public int getCentreMethod() {
    return processorOptions.getCentreMethod().ordinal();
  }

  /**
   * Sets the centre parameter.
   *
   * @param centreParameter the new centre parameter
   */
  public void setCentreParameter(double centreParameter) {
    final double oldValue = getCentreParameter();
    processorOptions.setCentreParameter(centreParameter);
    firePropertyChange(PROPERTY_CENTRE_PARAMETER, oldValue, centreParameter);
  }

  /**
   * Gets the centre parameter.
   *
   * @return the centreParameter.
   */
  public double getCentreParameter() {
    return processorOptions.getCentreParameter();
  }

  /**
   * Sets the fraction parameter.
   *
   * @param fractionParameter the new fraction parameter
   */
  public void setFractionParameter(double fractionParameter) {
    final double oldValue = getFractionParameter();
    processorOptions.setFractionParameter(fractionParameter);
    firePropertyChange(PROPERTY_FRACTION_PARAMETER, oldValue, fractionParameter);
  }

  /**
   * Gets the fraction parameter.
   *
   * @return the fractionParameter.
   */
  public double getFractionParameter() {
    return processorOptions.getFractionParameter();
  }

  /**
   * Sets the image list.
   *
   * @param imageList the new image list
   */
  public void setImageList(List<String> imageList) {
    final List<String> oldValue = this.imageList;
    this.imageList = imageList;

    if (!imageList.equals(oldValue)) {
      final String oldSelectedImage = this.selectedImage;

      // The image list has changed - Notify bound properties
      firePropertyChange(PROPERTY_IMAGE_LIST, oldValue, imageList);

      // Check if the selected image still exists
      if (imageList.contains(oldSelectedImage)) {
        setSelectedImage(oldSelectedImage);
      } else {
        setSelectedImage(imageList.isEmpty() ? "" : imageList.get(0));
      }
    }
  }

  /**
   * Gets the image list.
   *
   * @return the imageList.
   */
  public List<String> getImageList() {
    return imageList;
  }

  /**
   * Sets the selected image.
   *
   * @param selectedImage the new selected image
   */
  public void setSelectedImage(String selectedImage) {
    final String oldValue = this.selectedImage;
    this.selectedImage = selectedImage;
    firePropertyChange(PROPERTY_SELECTED_IMAGE, oldValue, selectedImage);
  }

  /**
   * Gets the selected image.
   *
   * @return the selectedImage.
   */
  public String getSelectedImage() {
    return selectedImage;
  }

  /**
   * Sets the mask image list.
   *
   * @param maskImageList the new mask image list
   */
  public void setMaskImageList(List<String> maskImageList) {
    final List<String> oldValue = this.maskImageList;
    this.maskImageList = maskImageList;

    if (!maskImageList.equals(oldValue)) {
      final String oldMaskImage = this.maskImage;

      // The image list has changed - Notify bound properties
      firePropertyChange(PROPERTY_MASK_IMAGE_LIST, oldValue, maskImageList);

      // Check if the selected image still exists
      if (maskImageList.contains(oldMaskImage)) {
        setMaskImage(oldMaskImage);
      } else {
        setMaskImage(maskImageList.isEmpty() ? "" : maskImageList.get(0));
      }
    }
  }

  /**
   * Gets the mask image list.
   *
   * @return the imageList.
   */
  public List<String> getMaskImageList() {
    return maskImageList;
  }

  /**
   * Sets the mask image.
   *
   * @param maskImage the new mask image
   */
  public void setMaskImage(String maskImage) {
    final String oldValue = this.maskImage;
    this.maskImage = maskImage;
    firePropertyChange(PROPERTY_MASK_IMAGE, oldValue, maskImage);
  }

  /**
   * Gets the mask image.
   *
   * @return the maskImage.
   */
  public String getMaskImage() {
    return maskImage;
  }

  /**
   * Sets the object analysis.
   *
   * @param objectAnalysis the new object analysis
   */
  public void setObjectAnalysis(boolean objectAnalysis) {
    if (setOption(OutputOption.OBJECT_ANALYSIS, objectAnalysis)) {
      firePropertyChange(PROPERTY_OBJECT_ANALYSIS, !objectAnalysis, objectAnalysis);
    }
  }

  /**
   * Checks if is object analysis.
   *
   * @return true, if is object analysis
   */
  public boolean isObjectAnalysis() {
    return isOption(OutputOption.OBJECT_ANALYSIS);
  }

  /**
   * Sets the show object mask.
   *
   * @param showObjectMask the new show object mask
   */
  public void setShowObjectMask(boolean showObjectMask) {
    if (setOption(OutputOption.SHOW_OBJECT_MASK, showObjectMask)) {
      firePropertyChange(PROPERTY_SHOW_OBJECT_MASK, !showObjectMask, showObjectMask);
    }
  }

  /**
   * Checks if is show object mask.
   *
   * @return true, if is show object mask
   */
  public boolean isShowObjectMask() {
    return isOption(OutputOption.SHOW_OBJECT_MASK);
  }

  /**
   * Sets the save to memory.
   *
   * @param saveToMemory the new save to memory
   */
  public void setSaveToMemory(boolean saveToMemory) {
    if (setOption(OutputOption.SAVE_TO_MEMORY, saveToMemory)) {
      firePropertyChange(PROPERTY_SAVE_TO_MEMORY, !saveToMemory, saveToMemory);
    }
  }

  /**
   * Checks if is save to memory.
   *
   * @return true, if is save to memory
   */
  public boolean isSaveToMemory() {
    return isOption(OutputOption.SAVE_TO_MEMORY);
  }

  /**
   * Sets the current state of the FindFoci model to unchanged.
   */
  public void setUnchanged() {
    setChanged(false);
  }

  /**
   * Sets the changed.
   *
   * @param changed the new changed
   */
  private void setChanged(boolean changed) {
    final boolean oldValue = this.changed;
    this.changed = changed;
    firePropertyChange(PROPERTY_CHANGED, oldValue, changed);
  }

  /**
   * Checks if is changed.
   *
   * @return true, if is changed
   */
  public boolean isChanged() {
    return changed;
  }

  /**
   * Cause a property changed event to be created with the property name set to PROPERTY_VALID. This
   * does not alter any values in the model but serves as a mechanism to raise an event that signals
   * that any calculations based on the model should be refreshed. For example it can be used to
   * signal that a new image has been set without changing the selectedImage property.
   */
  public void invalidate() {
    firePropertyChange(PROPERTY_VALID, true, false);
  }

  /**
   * Set the algorithm option.
   *
   * @param option the option
   * @param enabled True if enabled
   * @return true, if the option was changed
   */
  private boolean setOption(AlgorithmOption option, boolean enabled) {
    return processorOptions.setOption(option, enabled);
  }

  /**
   * Set the output option.
   *
   * @param option the option
   * @param enabled True if enabled
   * @return true, if the option was changed
   */
  private boolean setOption(OutputOption option, boolean enabled) {
    return options.setOption(option, enabled);
  }

  /**
   * Checks if the algorithm option is enabled.
   *
   * @param option the option
   * @return True if enabled
   */
  private boolean isOption(AlgorithmOption option) {
    return processorOptions.isOption(option);
  }

  /**
   * Checks if the output option is enabled.
   *
   * @param option the option
   * @return True if enabled
   */
  private boolean isOption(OutputOption option) {
    return options.isOption(option);
  }

  /**
   * Gets a copy of the processor options.
   *
   * @return the processor options
   */
  public FindFociProcessorOptions getProcessorOptions() {
    return processorOptions.copy();
  }

  /**
   * Gets a copy of the options.
   *
   * @return the options
   */
  public FindFociOptions getOptions() {
    return options.copy();
  }
}
