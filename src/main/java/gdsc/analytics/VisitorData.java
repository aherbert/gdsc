/**
 * Copyright (c) 2010 Daniel Murphy
 * 
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package gdsc.analytics;

import java.security.SecureRandom;

public class VisitorData
{
	private int visitorId;
	private long timestampFirst;
	private long timestampPrevious;
	private long timestampCurrent;
	private int visits;

	VisitorData(int visitorId, long timestampFirst, long timestampPrevious, long timestampCurrent, int visits)
	{
		this.visitorId = visitorId;
		this.timestampFirst = timestampFirst;
		this.timestampPrevious = timestampPrevious;
		this.timestampCurrent = timestampCurrent;
		this.visits = visits;
	}

	public long newRequest()
	{
		this.timestampCurrent = now();
		return this.timestampCurrent;
	}

	public void resetSession()
	{
		long now = now();
		this.timestampPrevious = this.timestampCurrent;
		this.timestampCurrent = now;
		this.visits++;
	}

	private static long now()
	{
		long now = System.currentTimeMillis() / 1000L;
		return now;
	}

	public int getVisitorId()
	{
		return visitorId;
	}

	public long getTimestampFirst()
	{
		return timestampFirst;
	}

	public long getTimestampPrevious()
	{
		return timestampPrevious;
	}

	public long getTimestampCurrent()
	{
		return timestampCurrent;
	}

	public int getVisits()
	{
		return visits;
	}

	/**
	 * initializes a new visitor data, with new visitorid
	 */
	public static VisitorData newVisitor()
	{
		int visitorId = (new SecureRandom().nextInt() & 0x7FFFFFFF);
		long now = now();
		return new VisitorData(visitorId, now, now, now, 1);
	}

	public static VisitorData newSession(int visitorId, long timestampfirst, long timestamplast, int visits)
	{
		long now = now();
		return new VisitorData(visitorId, timestampfirst, timestamplast, now, visits + 1);
	}
}