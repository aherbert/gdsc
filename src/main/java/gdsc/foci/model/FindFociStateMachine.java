package gdsc.foci.model;

/*----------------------------------------------------------------------------- 
 * GDSC Plugins for ImageJ
 * 
 * Copyright (C) 2011 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Provides a state-machine for the processing steps required to recompute the FindFoci result
 * following a change to the model parameters
 */
public class FindFociStateMachine extends AbstractModelObject implements PropertyChangeListener
{
	private FindFociState state = FindFociState.INITIAL;

	private Map<String, FindFociState> stateMap;

	public FindFociStateMachine()
	{
		init();
	}

	/**
	 * @param state
	 */
	public FindFociStateMachine(FindFociState state)
	{
		this.state = state;
		init();
	}

	private void init()
	{
		stateMap = new HashMap<String, FindFociState>();
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.beans.PropertyChangeListener#propertyChange(java.beans.PropertyChangeEvent)
	 */
	public void propertyChange(PropertyChangeEvent evt)
	{
		String propertyName = evt.getPropertyName();

		if ("state".equals(propertyName))
		{
			// Ignore this
		}
		else
		{
			FindFociState state = stateMap.get(propertyName);
			if (state != null)
			{
				//System.out.println("Changed : "+ propertyName);
				reduceState(state);
			}
			else
			{
				// Default: Reset
				setState(FindFociState.INITIAL);
			}
		}
	}

	/**
	 * Updates the state to an earlier level
	 * 
	 * @param state
	 *            The level
	 */
	private void reduceState(FindFociState state)
	{
		if (state.ordinal() < this.state.ordinal())
		{
			//System.out.printf("%s [%d] => %s [%d]\n", this.state, currentLevel, state, newLevel);
			setState(state);
		}
	}

	/**
	 * Updates the state to an earlier level
	 * 
	 * @param state
	 *            The level
	 */
	@SuppressWarnings("unused")
	private void increaseState(FindFociState state)
	{
		if (state.ordinal() > this.state.ordinal())
		{
			//System.out.printf("%s [%d] => %s [%d]\n", this.state, currentLevel, state, newLevel);
			setState(state);
		}
	}

	/**
	 * @param state
	 *            the state to set
	 */
	public void setState(FindFociState state)
	{
		FindFociState oldValue = this.state;
		//System.out.println(state);
		this.state = state;
		firePropertyChange("state", oldValue, state);
	}

	/**
	 * @return the state
	 */
	public FindFociState getState()
	{
		return state;
	}

	/**
	 * @return The set of properties for the FindFoci algorithm
	 *         that the state machine observes
	 */
	public Set<String> getObservedProperties()
	{
		return stateMap.keySet();
	}
}
