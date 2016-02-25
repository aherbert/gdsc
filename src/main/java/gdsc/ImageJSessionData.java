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

import java.security.SecureRandom;

import gdsc.analytics.SessionData;
import ij.Prefs;

/**
 * Extend the SessionData object to track the information using an ImageJ property.
 * This allows a visitor to be tracked over multiple ImageJ sessions.
 * <p>
 * Note that if a user runs multiple instances of ImageJ then the session data will be broken.
 * <p>
 * Note that if the IJ_Prefs.txt file is on a network device the session may be created
 * for the same user but on a different host.
 */
public class ImageJSessionData extends SessionData
{
	private static final String SESSION_PROPERTY = "gdsc.session";

	protected ImageJSessionData(int visitorId, int sessionNumber)
	{
		this(visitorId, 0, 0, 0, 0, sessionNumber, 0);
	}

	protected ImageJSessionData(int visitorId, long initial, long previous, long current, long latest,
			int sessionNumber, int count)
	{
		super(visitorId, sessionNumber);

		// Use the session data if provided.
		if (initial != 0)
		{
			this.initial = initial;
			this.previous = previous;
			this.current = current;
			this.latest = latest;
			this.count = count;
		}

		storeSession();
	}

	/**
	 * Store the current session. This should be called whenever the session number is incremented.
	 */
	private void storeSession()
	{
		// Session is stored as visitorId.initial.previous.current.latest.sessionNumber.count
		StringBuilder sb = new StringBuilder();
		sb.append(visitorId).append('.');
		sb.append(initial).append('.');
		sb.append(previous).append('.');
		sb.append(current).append('.');
		sb.append(latest).append('.');
		sb.append(sessionNumber).append('.');
		sb.append(count);

		// This will be stored if ImageJ shuts down cleanly
		Prefs.set(SESSION_PROPERTY, sb.toString());
		// This is an expensive call so leave it for ImageJ to do this upon exit
		//Prefs.savePreferences();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.analytics.SessionData#refresh()
	 */
	@Override
	public void refresh()
	{
		super.refresh();
		storeSession();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.analytics.SessionData#newSession(long)
	 */
	@Override
	protected void newSession(long now)
	{
		super.newSession(now);
		storeSession();
	}

	/**
	 * Initialises a new session data, with the previous visitor id and session information if available, otherwise
	 * a new random visitor id
	 */
	public static ImageJSessionData newImageJSessionData()
	{
		// Get the session information from the ImageJ properties 
		final String sessionString = Prefs.get(SESSION_PROPERTY, "");
		if (sessionString != null && sessionString.length() > 0)
		{
			// Session is stored as visitorId.initial.previous.current.latest.sessionNumber.count
			String[] data = sessionString.split("\\.");
			if (data.length == 7)
			{
				try
				{
					int visitorId = Integer.parseInt(data[0]);
					long initial = Long.parseLong(data[1]);
					long previous = Long.parseLong(data[2]);
					long current = Long.parseLong(data[3]);
					long latest = Long.parseLong(data[4]);
					int sessionNumber = Integer.parseInt(data[5]);
					int count = Integer.parseInt(data[6]);

					// In Google Analytics sessions can persist when the browser closes and is re-opened,
					// so do not update anything. This will restore the session to the state of the last 
					// call to storeSession().

					return new ImageJSessionData(visitorId, initial, previous, current, latest, sessionNumber, count);
				}
				catch (NumberFormatException e)
				{
					// ignore this and create a new session
				}
			}
		}

		// Default to a new session
		return new ImageJSessionData(new SecureRandom().nextInt() & 0x7FFFFFFF, 1);
	}
}