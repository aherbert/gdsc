/*----------------------------------------------------------------------------- 
 * GDSC Plugins for ImageJ
 * 
 * Copyright (C) 2016 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/
package gdsc;

import gdsc.analytics.SessionStore;
import ij.Prefs;

/**
 * Allow the session to be saved and loaded from the ImageJ preferences
 * 
 * @author a.herbert@sussex.ac.uk
 */
public class ImageJSessionStore implements SessionStore
{
	private static final String SESSION_PROPERTY = "gdsc.session";

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.analytics.SessionStore#load()
	 */
	public long[] load()
	{
		final String sessionString = Prefs.get(SESSION_PROPERTY, "");
		if (sessionString != null && sessionString.length() > 0)
		{
			// Session is stored as . delimited string
			String[] data = sessionString.split("\\.");
			if (data.length == 7)
			{
				final long[] state = new long[7];
				try
				{
					for (int i = 0; i < 7; i++)
						state[i] = Long.parseLong(data[i]);

					// In Google Analytics sessions can persist when the browser closes and is re-opened,
					// so do not update anything. This will restore the session to the state of the last 
					// call to storeSession(). This is true if Google Chrome processes run in the background.
					// If all the background processes are stopped then ...

					return state;
				}
				catch (NumberFormatException e)
				{
					// ignore this and create a new session
				}
			}
		}

		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.analytics.SessionStore#save(long[])
	 */
	public void save(long[] state)
	{
		// Assume the input state is not null or zero length
		//if (state == null || state.length == 0)
		//	return;

		final StringBuilder sb = new StringBuilder();
		sb.append(state[0]);
		for (int i = 1; i < state.length; i++)
			sb.append('.').append(state[i]);

		// This will be stored if ImageJ shuts down cleanly
		Prefs.set(SESSION_PROPERTY, sb.toString());

		// This is an expensive call so leave it to ImageJ to do this upon exit
		//Prefs.savePreferences();
	}
}