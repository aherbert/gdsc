package gdsc.analytics;

/*
 * <ul>
 * <li>Copyright (c) 2010 Daniel Murphy
 * <li>Copyright (c) 2016 Alex Herbert
 * </ul>
 * 
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * @see https://code.google.com/archive/p/jgoogleanalyticstracker/
 */

import java.security.SecureRandom;

/**
 * Represent the Google Analytics session data for a visitor.
 * 
 * @see http://www.cardinalpath.com/ga-basics-the-structure-of-cookie-values/
 * @author Alex Herbert
 */
public class SessionData
{
	/**
	 * The unique vistor ID
	 */
	private int visitorId;
	/**
	 * Timestamp of the first visit
	 */
	private long initial;
	/**
	 * Timestamp of the previous visit
	 */
	private long previous;
	/**
	 * Timestamp of the current visit
	 */
	private long current;
	/**
	 * Session number
	 */
	private int sessionNumber;
	/**
	 * Google sessions timeout after 30 minutes of inactivity
	 */
	private final int TIMEOUT = 30 * 60;

	/**
	 * Create a new session
	 * 
	 * @param visitorId
	 * @param sessionNumber
	 */
	private SessionData(int visitorId, int sessionNumber)
	{
		this.visitorId = visitorId;
		initial = previous = current = timestamp();
		this.sessionNumber = sessionNumber;
	}

	/**
	 * Get the number of seconds since the epoch (midnight, January 1, 1970 UTC)
	 * 
	 * @return The timestamp in seconds
	 */
	public static long timestamp()
	{
		return System.currentTimeMillis() / 1000L;
	}

	/**
	 * Initializes a new session data, with new random visitor id
	 */
	public static SessionData newSessionData()
	{
		final int visitorId = (new SecureRandom().nextInt() & 0x7FFFFFFF);
		return new SessionData(visitorId, 1);
	}

	public int getVisitorId()
	{
		return visitorId;
	}

	/**
	 * @return Timestamp of the first visit
	 */
	public long getInitial()
	{
		return initial;
	}

	/**
	 * @return Timestamp of the previous visit
	 */
	public long getPrevious()
	{
		return previous;
	}

	/**
	 * @return Timestamp of the current visit
	 */
	public long getCurrent()
	{
		final long now = timestamp();
		// Check the timeout and start a new session if necessary
		if (now > current + TIMEOUT)
			newSession(now);
		return current;
	}

	/**
	 * @return The session number
	 */
	public int getSessionNumber()
	{
		return sessionNumber;
	}

	/**
	 * Increment the session number to start a new session
	 */
	public void newSession()
	{
		newSession(timestamp());
	}

	/**
	 * Increment the session number to start a new session
	 * 
	 * @param now
	 *            The current timpstamp for the new session
	 */
	private void newSession(long now)
	{
		// Previous stores the start time of the last session 
		previous = current;
		current = now;
		this.sessionNumber++;
	}
}