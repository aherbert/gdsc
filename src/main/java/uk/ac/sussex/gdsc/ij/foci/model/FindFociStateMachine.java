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

package uk.ac.sussex.gdsc.ij.foci.model;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Provides a state-machine for the processing steps required to recompute the
 * {@link uk.ac.sussex.gdsc.ij.foci.FindFoci_PlugIn} result following a change to the model parameters.
 */
public class FindFociStateMachine extends AbstractModelObject implements PropertyChangeListener {
  private FindFociState state = FindFociState.INITIAL;

  private Map<String, FindFociState> stateMap;

  /**
   * Instantiates a new find foci state machine.
   */
  public FindFociStateMachine() {
    init();
  }

  /**
   * Instantiates a new find foci state machine.
   *
   * @param state the state
   */
  public FindFociStateMachine(FindFociState state) {
    this.state = state;
    init();
  }

  private void init() {
    stateMap = new HashMap<>();
    stateMap.put("gaussianBlur", FindFociState.INITIAL);
    stateMap.put("backgroundMethod", FindFociState.FIND_MAXIMA);
    stateMap.put("thresholdMethod", FindFociState.FIND_MAXIMA);
    stateMap.put("maskImage", FindFociState.FIND_MAXIMA);
    stateMap.put("backgroundParameter", FindFociState.SEARCH);
    stateMap.put("searchMethod", FindFociState.SEARCH);
    stateMap.put("searchParameter", FindFociState.SEARCH);
    stateMap.put("peakMethod", FindFociState.MERGE_HEIGHT);
    stateMap.put("peakParameter", FindFociState.MERGE_HEIGHT);
    stateMap.put("minSize", FindFociState.MERGE_SIZE);
    stateMap.put("minimumAboveSaddle", FindFociState.MERGE_SADDLE);
    stateMap.put("removeEdgeMaxima", FindFociState.MERGE_SADDLE);
    stateMap.put("maxSize", FindFociState.MERGE_SADDLE);
    stateMap.put("sortMethod", FindFociState.CALCULATE_RESULTS);
    stateMap.put("maxPeaks", FindFociState.CALCULATE_RESULTS);
    stateMap.put("centreMethod", FindFociState.CALCULATE_RESULTS);
    stateMap.put("centreParameter", FindFociState.CALCULATE_RESULTS);
    stateMap.put("showMask", FindFociState.CALCULATE_OUTPUT_MASK);
    stateMap.put("overlayMask", FindFociState.CALCULATE_OUTPUT_MASK);
    stateMap.put("showMaskMaximaAsDots", FindFociState.CALCULATE_OUTPUT_MASK);
    stateMap.put("fractionParameter", FindFociState.CALCULATE_OUTPUT_MASK);
    stateMap.put("showTable", FindFociState.SHOW_RESULTS);
    stateMap.put("markMaxima", FindFociState.SHOW_RESULTS);
    stateMap.put("markROIMaxima", FindFociState.SHOW_RESULTS);
    stateMap.put("saveResults", FindFociState.SHOW_RESULTS);
    stateMap.put("resultsDirectory", FindFociState.SHOW_RESULTS);
    stateMap.put("showLogMessages", FindFociState.INITIAL);
  }

  /** {@inheritDoc} */
  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    final String propertyName = evt.getPropertyName();

    if ("state".equals(propertyName)) {
      // Ignore this
    } else {
      final FindFociState localState = stateMap.get(propertyName);
      if (localState != null) {
        reduceState(localState);
      } else {
        // Default: Reset
        setState(FindFociState.INITIAL);
      }
    }
  }

  /**
   * Updates the state to an earlier level.
   *
   * @param state The level
   */
  private void reduceState(FindFociState state) {
    if (state.ordinal() < this.state.ordinal()) {
      setState(state);
    }
  }

  /**
   * Updates the state to an earlier level.
   *
   * @param state The level
   */
  @SuppressWarnings("unused")
  private void increaseState(FindFociState state) {
    if (state.ordinal() > this.state.ordinal()) {
      setState(state);
    }
  }

  /**
   * Sets the state.
   *
   * @param state the state to set
   */
  public void setState(FindFociState state) {
    final FindFociState oldValue = this.state;
    this.state = state;
    firePropertyChange("state", oldValue, state);
  }

  /**
   * Gets the state.
   *
   * @return the state.
   */
  public FindFociState getState() {
    return state;
  }

  /**
   * Gets the set of properties for the FindFoci algorithm that the state machine observes.
   *
   * @return The set of properties for the FindFoci algorithm that the state machine observes
   */
  public Set<String> getObservedProperties() {
    return stateMap.keySet();
  }
}
