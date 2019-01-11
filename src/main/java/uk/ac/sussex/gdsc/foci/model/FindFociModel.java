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

package uk.ac.sussex.gdsc.foci.model;

import uk.ac.sussex.gdsc.core.threshold.AutoThreshold;
import uk.ac.sussex.gdsc.foci.FindFociProcessor;
import uk.ac.sussex.gdsc.foci.FindFoci_PlugIn;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides a bean property model for the {@link uk.ac.sussex.gdsc.foci.FindFoci_PlugIn} algorithm.
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
   * {@link #setThresholdMethod(String)}.
   */
  public static final String PROPERTY_THRESHOLD_METHOD = "thresholdMethod";
  /**
   * The property name for the {@link PropertyChangeEvent} raised in
   * {@link #setStatisticsMode(String)}.
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

  private int backgroundMethod = FindFociProcessor.BACKGROUND_AUTO_THRESHOLD;
  private double backgroundParameter = 3;
  private String thresholdMethod = AutoThreshold.Method.OTSU.toString();
  private String statisticsMode = "Both";
  private int searchMethod = FindFociProcessor.SEARCH_ABOVE_BACKGROUND;
  private double searchParameter = 0.3;
  private int minSize = 5;
  private boolean minimumAboveSaddle = true;
  private boolean connectedAboveSaddle;
  private int peakMethod = FindFociProcessor.PEAK_RELATIVE_ABOVE_BACKGROUND;
  private double peakParameter = 0.5;
  private int sortMethod = FindFociProcessor.SORT_INTENSITY;
  private int maxPeaks = 50;
  private int showMask = 3;
  private boolean overlayMask = true;
  private boolean showTable = true;
  private boolean clearTable = true;
  private boolean markMaxima = true;
  private boolean markRoiMaxima;
  private boolean markUsingOverlay;
  private boolean hideLabels;
  private boolean showMaskMaximaAsDots;
  private boolean showLogMessages = true;
  private boolean removeEdgeMaxima;
  private boolean saveResults;
  private String resultsDirectory;
  private double gaussianBlur;
  private int centreMethod = FindFoci_PlugIn.CENTRE_MAX_VALUE_SEARCH;
  private double centreParameter = 2;
  private double fractionParameter = 0.5;
  private boolean objectAnalysis;
  private boolean showObjectMask;
  private boolean saveToMemory;
  private List<String> imageList = new ArrayList<>();
  private String selectedImage = "";
  private List<String> maskImageList = new ArrayList<>();
  private String maskImage = "";
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
    backgroundMethod = source.backgroundMethod;
    backgroundParameter = source.backgroundParameter;
    thresholdMethod = source.thresholdMethod;
    statisticsMode = source.statisticsMode;
    searchMethod = source.searchMethod;
    searchParameter = source.searchParameter;
    minSize = source.minSize;
    minimumAboveSaddle = source.minimumAboveSaddle;
    connectedAboveSaddle = source.connectedAboveSaddle;
    peakMethod = source.peakMethod;
    peakParameter = source.peakParameter;
    sortMethod = source.sortMethod;
    maxPeaks = source.maxPeaks;
    showMask = source.showMask;
    overlayMask = source.overlayMask;
    showTable = source.showTable;
    clearTable = source.clearTable;
    markMaxima = source.markMaxima;
    markRoiMaxima = source.markRoiMaxima;
    markUsingOverlay = source.markUsingOverlay;
    hideLabels = source.hideLabels;
    showMaskMaximaAsDots = source.showMaskMaximaAsDots;
    showLogMessages = source.showLogMessages;
    removeEdgeMaxima = source.removeEdgeMaxima;
    saveResults = source.saveResults;
    resultsDirectory = source.resultsDirectory;
    gaussianBlur = source.gaussianBlur;
    centreMethod = source.centreMethod;
    centreParameter = source.centreParameter;
    fractionParameter = source.fractionParameter;
    objectAnalysis = source.objectAnalysis;
    showObjectMask = source.showObjectMask;
    saveToMemory = source.saveToMemory;
    selectedImage = source.selectedImage;
    maskImage = source.maskImage;
    changed = source.changed;
    backgroundParameterMemory = source.backgroundParameterMemory;
    peakParameterMemory = source.peakParameterMemory;

    // Copy lists
    imageList = new ArrayList<>(source.imageList);
    maskImageList = new ArrayList<>(source.maskImageList);
  }

  /**
   * Sets the background method.
   *
   * @param backgroundMethod the new background method
   */
  public void setBackgroundMethod(int backgroundMethod) {
    final int oldValue = this.backgroundMethod;
    this.backgroundMethod = backgroundMethod;
    firePropertyChange(PROPERTY_BACKGROUND_METHOD, oldValue, this.backgroundMethod);

    // Check if this is a switch to/from absolute background
    if (oldValue != backgroundMethod && (oldValue == FindFociProcessor.BACKGROUND_ABSOLUTE
        || backgroundMethod == FindFociProcessor.BACKGROUND_ABSOLUTE)) {
      final double current = backgroundParameter;
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
    return backgroundMethod;
  }

  /**
   * Sets the background parameter.
   *
   * @param backgroundParameter the new background parameter
   */
  public void setBackgroundParameter(double backgroundParameter) {
    final double oldValue = this.backgroundParameter;
    this.backgroundParameter = backgroundParameter;
    firePropertyChange(PROPERTY_BACKGROUND_PARAMETER, oldValue, this.backgroundParameter);
  }

  /**
   * Gets the background parameter.
   *
   * @return the backgroundParameter.
   */
  public double getBackgroundParameter() {
    return backgroundParameter;
  }

  /**
   * Sets the threshold method.
   *
   * @param thresholdMethod the new threshold method
   */
  public void setThresholdMethod(String thresholdMethod) {
    final String oldValue = this.thresholdMethod;
    this.thresholdMethod = thresholdMethod;
    firePropertyChange(PROPERTY_THRESHOLD_METHOD, oldValue, this.thresholdMethod);
  }

  /**
   * Gets the threshold method.
   *
   * @return the thresholdMethod.
   */
  public String getThresholdMethod() {
    return thresholdMethod;
  }

  /**
   * Sets the statistics mode.
   *
   * @param statisticsMode the new statistics mode
   */
  public void setStatisticsMode(String statisticsMode) {
    final String oldValue = this.statisticsMode;
    this.statisticsMode = statisticsMode;
    firePropertyChange(PROPERTY_STATISTICS_MODE, oldValue, this.statisticsMode);
  }

  /**
   * Gets the statistics mode.
   *
   * @return the statisticsMode.
   */
  public String getStatisticsMode() {
    return statisticsMode;
  }

  /**
   * Sets the search method.
   *
   * @param searchMethod the new search method
   */
  public void setSearchMethod(int searchMethod) {
    final int oldValue = this.searchMethod;
    this.searchMethod = searchMethod;
    firePropertyChange(PROPERTY_SEARCH_METHOD, oldValue, this.searchMethod);
  }

  /**
   * Gets the search method.
   *
   * @return the searchMethod.
   */
  public int getSearchMethod() {
    return searchMethod;
  }

  /**
   * Sets the search parameter.
   *
   * @param searchParameter the new search parameter
   */
  public void setSearchParameter(double searchParameter) {
    final double oldValue = this.searchParameter;
    this.searchParameter = searchParameter;
    firePropertyChange(PROPERTY_SEARCH_PARAMETER, oldValue, this.searchParameter);
  }

  /**
   * Gets the search parameter.
   *
   * @return the searchParameter.
   */
  public double getSearchParameter() {
    return searchParameter;
  }

  /**
   * Sets the min size.
   *
   * @param minSize the new min size
   */
  public void setMinSize(int minSize) {
    final int oldValue = this.minSize;
    this.minSize = minSize;
    firePropertyChange(PROPERTY_MIN_SIZE, oldValue, this.minSize);
  }

  /**
   * Gets the min size.
   *
   * @return the minSize.
   */
  public int getMinSize() {
    return minSize;
  }

  /**
   * Sets the minimum above saddle.
   *
   * @param minimumAboveSaddle the new minimum above saddle
   */
  public void setMinimumAboveSaddle(boolean minimumAboveSaddle) {
    final boolean oldValue = this.minimumAboveSaddle;
    this.minimumAboveSaddle = minimumAboveSaddle;
    firePropertyChange(PROPERTY_MINIMUM_ABOVE_SADDLE, oldValue, this.minimumAboveSaddle);
  }

  /**
   * Checks if is minimum above saddle.
   *
   * @return true, if is minimum above saddle
   */
  public boolean isMinimumAboveSaddle() {
    return minimumAboveSaddle;
  }

  /**
   * Sets the connected above saddle.
   *
   * @param connectedAboveSaddle the new connected above saddle
   */
  public void setConnectedAboveSaddle(boolean connectedAboveSaddle) {
    final boolean oldValue = this.connectedAboveSaddle;
    this.connectedAboveSaddle = connectedAboveSaddle;
    firePropertyChange(PROPERTY_CONNECTED_ABOVE_SADDLE, oldValue, this.connectedAboveSaddle);
  }

  /**
   * Checks if is connected above saddle.
   *
   * @return true, if is connected above saddle
   */
  public boolean isConnectedAboveSaddle() {
    return connectedAboveSaddle;
  }

  /**
   * Sets the peak method.
   *
   * @param peakMethod the new peak method
   */
  public void setPeakMethod(int peakMethod) {
    final int oldValue = this.peakMethod;
    this.peakMethod = peakMethod;
    firePropertyChange(PROPERTY_PEAK_METHOD, oldValue, this.peakMethod);

    // Check if this is a switch to/from absolute background
    if (oldValue != peakMethod && (oldValue == FindFociProcessor.PEAK_ABSOLUTE
        || peakMethod == FindFociProcessor.PEAK_ABSOLUTE)) {
      final double current = peakParameter;
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
    return peakMethod;
  }

  /**
   * Sets the peak parameter.
   *
   * @param peakParameter the new peak parameter
   */
  public void setPeakParameter(double peakParameter) {
    final double oldValue = this.peakParameter;
    this.peakParameter = peakParameter;
    firePropertyChange(PROPERTY_PEAK_PARAMETER, oldValue, this.peakParameter);
  }

  /**
   * Gets the peak parameter.
   *
   * @return the peakParameter.
   */
  public double getPeakParameter() {
    return peakParameter;
  }

  /**
   * Sets the sort method.
   *
   * @param sortMethod the new sort method
   */
  public void setSortMethod(int sortMethod) {
    final double oldValue = this.sortMethod;
    this.sortMethod = sortMethod;
    firePropertyChange(PROPERTY_SORT_METHOD, oldValue, this.sortMethod);
  }

  /**
   * Gets the sort method.
   *
   * @return the sortMethod.
   */
  public int getSortMethod() {
    return sortMethod;
  }

  /**
   * Sets the max peaks.
   *
   * @param maxPeaks the new max peaks
   */
  public void setMaxPeaks(int maxPeaks) {
    final double oldValue = this.maxPeaks;
    this.maxPeaks = maxPeaks;
    firePropertyChange(PROPERTY_MAX_PEAKS, oldValue, this.maxPeaks);
  }

  /**
   * Gets the max peaks.
   *
   * @return the maxPeaks.
   */
  public int getMaxPeaks() {
    return maxPeaks;
  }

  /**
   * Sets the show mask.
   *
   * @param showMask the new show mask
   */
  public void setShowMask(int showMask) {
    final int oldValue = this.showMask;
    this.showMask = showMask;
    firePropertyChange(PROPERTY_SHOW_MASK, oldValue, this.showMask);
  }

  /**
   * Gets the show mask.
   *
   * @return the showMask.
   */
  public int getShowMask() {
    return showMask;
  }

  /**
   * Sets the overlay mask.
   *
   * @param overlayMask the new overlay mask
   */
  public void setOverlayMask(boolean overlayMask) {
    final boolean oldValue = this.overlayMask;
    this.overlayMask = overlayMask;
    firePropertyChange(PROPERTY_OVERLAY_MASK, oldValue, this.overlayMask);
  }

  /**
   * Checks if is overlay mask.
   *
   * @return true, if is overlay mask
   */
  public boolean isOverlayMask() {
    return overlayMask;
  }

  /**
   * Sets the show table.
   *
   * @param showTable the new show table
   */
  public void setShowTable(boolean showTable) {
    final boolean oldValue = this.showTable;
    this.showTable = showTable;
    firePropertyChange(PROPERTY_SHOW_TABLE, oldValue, this.showTable);
  }

  /**
   * Checks if is show table.
   *
   * @return true, if is show table
   */
  public boolean isShowTable() {
    return showTable;
  }

  /**
   * Sets the clear table.
   *
   * @param clearTable the new clear table
   */
  public void setClearTable(boolean clearTable) {
    final boolean oldValue = this.clearTable;
    this.clearTable = clearTable;
    firePropertyChange(PROPERTY_CLEAR_TABLE, oldValue, this.clearTable);
  }

  /**
   * Checks if is clear table.
   *
   * @return true, if is clear table
   */
  public boolean isClearTable() {
    return clearTable;
  }

  /**
   * Sets the mark maxima.
   *
   * @param markMaxima the new mark maxima
   */
  public void setMarkMaxima(boolean markMaxima) {
    final boolean oldValue = this.markMaxima;
    this.markMaxima = markMaxima;
    firePropertyChange(PROPERTY_MARK_MAXIMA, oldValue, this.markMaxima);
  }

  /**
   * Checks if is mark maxima.
   *
   * @return true, if is mark maxima
   */
  public boolean isMarkMaxima() {
    return markMaxima;
  }

  /**
   * Sets the mark ROI maxima.
   *
   * @param markRoiMaxima the new mark roi maxima
   */
  public void setMarkRoiMaxima(boolean markRoiMaxima) {
    final boolean oldValue = this.markRoiMaxima;
    this.markRoiMaxima = markRoiMaxima;
    firePropertyChange(PROPERTY_MARK_ROI_MAXIMA, oldValue, this.markRoiMaxima);
  }

  /**
   * Checks if is mark ROI maxima.
   *
   * @return true, if is mark roi maxima
   */
  public boolean isMarkRoiMaxima() {
    return markRoiMaxima;
  }

  /**
   * Sets the mark using overlay.
   *
   * @param markUsingOverlay the new mark using overlay
   */
  public void setMarkUsingOverlay(boolean markUsingOverlay) {
    final boolean oldValue = this.markUsingOverlay;
    this.markUsingOverlay = markUsingOverlay;
    firePropertyChange(PROPERTY_MARK_USING_OVERLAY, oldValue, this.markUsingOverlay);
  }

  /**
   * Checks if is mark using overlay.
   *
   * @return the markUsingOverlay.
   */
  public boolean isMarkUsingOverlay() {
    return markUsingOverlay;
  }

  /**
   * Sets the hide labels.
   *
   * @param hideLabels the new hide labels
   */
  public void setHideLabels(boolean hideLabels) {
    final boolean oldValue = this.hideLabels;
    this.hideLabels = hideLabels;
    firePropertyChange(PROPERTY_HIDE_LABELS, oldValue, this.hideLabels);
  }

  /**
   * Checks if is hide labels.
   *
   * @return the hideLabels.
   */
  public boolean isHideLabels() {
    return hideLabels;
  }

  /**
   * Checks if is show mask maxima as dots.
   *
   * @return the showMaskMaximaAsDots.
   */
  public boolean isShowMaskMaximaAsDots() {
    return showMaskMaximaAsDots;
  }

  /**
   * Sets the show mask maxima as dots.
   *
   * @param showMaskMaximaAsDots the new show mask maxima as dots
   */
  public void setShowMaskMaximaAsDots(boolean showMaskMaximaAsDots) {
    final boolean oldValue = this.showMaskMaximaAsDots;
    this.showMaskMaximaAsDots = showMaskMaximaAsDots;
    firePropertyChange(PROPERTY_SHOW_MASK_MAXIMA_AS_DOTS, oldValue, this.showMaskMaximaAsDots);
  }

  /**
   * Sets the show log messages.
   *
   * @param showLogMessages the new show log messages
   */
  public void setShowLogMessages(boolean showLogMessages) {
    final boolean oldValue = this.showLogMessages;
    this.showLogMessages = showLogMessages;
    firePropertyChange(PROPERTY_SHOW_LOG_MESSAGES, oldValue, this.showLogMessages);
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
    final boolean oldValue = this.removeEdgeMaxima;
    this.removeEdgeMaxima = removeEdgeMaxima;
    firePropertyChange(PROPERTY_REMOVE_EDGE_MAXIMA, oldValue, this.removeEdgeMaxima);
  }

  /**
   * Checks if is removes the edge maxima.
   *
   * @return the removeEdgeMaxima.
   */
  public boolean isRemoveEdgeMaxima() {
    return removeEdgeMaxima;
  }

  /**
   * Sets the save results.
   *
   * @param saveResults the new save results
   */
  public void setSaveResults(boolean saveResults) {
    final boolean oldValue = this.saveResults;
    this.saveResults = saveResults;
    firePropertyChange(PROPERTY_SAVE_RESULTS, oldValue, this.saveResults);
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
    final String oldValue = this.resultsDirectory;
    this.resultsDirectory = resultsDirectory;
    firePropertyChange(PROPERTY_RESULTS_DIRECTORY, oldValue, this.resultsDirectory);
  }

  /**
   * Gets the results directory.
   *
   * @return the resultsDirectory.
   */
  public String getResultsDirectory() {
    return resultsDirectory;
  }

  /**
   * Sets the gaussian blur.
   *
   * @param gaussianBlur the new gaussian blur
   */
  public void setGaussianBlur(double gaussianBlur) {
    final double oldValue = this.gaussianBlur;
    this.gaussianBlur = gaussianBlur;
    firePropertyChange(PROPERTY_GAUSSIAN_BLUR, oldValue, this.gaussianBlur);
  }

  /**
   * Gets the gaussian blur.
   *
   * @return the gaussianBlur.
   */
  public double getGaussianBlur() {
    return gaussianBlur;
  }

  /**
   * Sets the centre method.
   *
   * @param centreMethod the new centre method
   */
  public void setCentreMethod(int centreMethod) {
    final double oldValue = this.centreMethod;
    this.centreMethod = centreMethod;
    firePropertyChange(PROPERTY_CENTRE_METHOD, oldValue, this.centreMethod);
  }

  /**
   * Gets the centre method.
   *
   * @return the centreMethod.
   */
  public int getCentreMethod() {
    return centreMethod;
  }

  /**
   * Sets the centre parameter.
   *
   * @param centreParameter the new centre parameter
   */
  public void setCentreParameter(double centreParameter) {
    final double oldValue = this.centreParameter;
    this.centreParameter = centreParameter;
    firePropertyChange(PROPERTY_CENTRE_PARAMETER, oldValue, this.centreParameter);
  }

  /**
   * Gets the centre parameter.
   *
   * @return the centreParameter.
   */
  public double getCentreParameter() {
    return centreParameter;
  }

  /**
   * Sets the fraction parameter.
   *
   * @param fractionParameter the new fraction parameter
   */
  public void setFractionParameter(double fractionParameter) {
    final double oldValue = this.fractionParameter;
    this.fractionParameter = fractionParameter;
    firePropertyChange(PROPERTY_FRACTION_PARAMETER, oldValue, this.fractionParameter);
  }

  /**
   * Gets the fraction parameter.
   *
   * @return the fractionParameter.
   */
  public double getFractionParameter() {
    return fractionParameter;
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
    firePropertyChange(PROPERTY_SELECTED_IMAGE, oldValue, this.selectedImage);
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
    firePropertyChange(PROPERTY_MASK_IMAGE, oldValue, this.maskImage);
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
    final boolean oldValue = this.objectAnalysis;
    this.objectAnalysis = objectAnalysis;
    firePropertyChange(PROPERTY_OBJECT_ANALYSIS, oldValue, this.objectAnalysis);
  }

  /**
   * Checks if is object analysis.
   *
   * @return true, if is object analysis
   */
  public boolean isObjectAnalysis() {
    return objectAnalysis;
  }

  /**
   * Sets the show object mask.
   *
   * @param showObjectMask the new show object mask
   */
  public void setShowObjectMask(boolean showObjectMask) {
    final boolean oldValue = this.showObjectMask;
    this.showObjectMask = showObjectMask;
    firePropertyChange(PROPERTY_SHOW_OBJECT_MASK, oldValue, this.showObjectMask);
  }

  /**
   * Checks if is show object mask.
   *
   * @return true, if is show object mask
   */
  public boolean isShowObjectMask() {
    return showObjectMask;
  }

  /**
   * Sets the save to memory.
   *
   * @param saveToMemory the new save to memory
   */
  public void setSaveToMemory(boolean saveToMemory) {
    final boolean oldValue = this.saveToMemory;
    this.saveToMemory = saveToMemory;
    firePropertyChange(PROPERTY_SAVE_TO_MEMORY, oldValue, this.saveToMemory);
  }

  /**
   * Checks if is save to memory.
   *
   * @return true, if is save to memory
   */
  public boolean isSaveToMemory() {
    return saveToMemory;
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
   * Performs a deep copy.
   *
   * @return A copy of this object
   */
  public FindFociModel deepCopy() {
    return new FindFociModel(this);
  }
}
