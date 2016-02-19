/*----------------------------------------------------------------------------- 
 * GDSC Plugins for ImageJ
 * 
 * Copyright (C) 2016 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 *
 * The code within the gdsc.analytics package is based upon:
 * 
 * JGoogleAnalyticsTracker by Daniel Murphy
 * @see https://code.google.com/archive/p/jgoogleanalyticstracker/
 * 
 * JGoogleAnalytics by Siddique Hameed 
 * @see https://github.com/siddii/jgoogleanalytics
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/
package gdsc.analytics;

import java.security.SecureRandom;

/**
 * Represent the Google Analytics session data.
 * <p>
 * @see http://www.cardinalpath.com/ga-basics-the-structure-of-cookie-values/
 */
public class SessionData
{
	private int visitorId;
	private long initial;
	private long previous;
	private long current;
	private int sessionNumber;

	private SessionData(int visitorId, int sessionNumber)
	{
		this.visitorId = visitorId;
		this.initial = 0;
		this.previous = 0;
		this.current = 0;
		this.sessionNumber = sessionNumber;
	}

	/**
	 * Initializes a new session data, with new visitor id
	 */
	public static SessionData newSession()
	{
		final int visitorId = (new SecureRandom().nextInt() & 0x7FFFFFFF);
		return new SessionData(visitorId, 1);
	}

	public int getVisitorId()
	{
		return visitorId;
	}

	public long getInitial()
	{
		return initial;
	}

	public long getPrevious()
	{
		return previous;
	}

	/**
	 * Get the current time.
	 * <p>
	 * This should be called before {@link #getInitial()} and {@link #getPrevious()} as they are updated.
	 * 
	 * @return The current time
	 */
	public long getCurrent()
	{
		final long now = System.currentTimeMillis() / 1000L;
		if (initial == 0)
		{
			// If this is the first time event then initialise the time
			initial = previous = now;
		}
		else
		{
			// Else update the previous time
			previous = current;
		}
		return current = now;
	}

	public int getSessionNumber()
	{
		return sessionNumber;
	}

	/**
	 * Increment the session number
	 */
	public void resetSession()
	{
		// I do not think we need to update the timestamps here. 
		// The next call to getCurrent() will do that anyway.
		this.sessionNumber++;
	}

}