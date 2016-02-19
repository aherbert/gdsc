/*----------------------------------------------------------------------------- 
 * GDSC Plugins for ImageJ
 * 
 * Copyright (C) 2016 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 *
 * The code within the gdsc.analytics package is based upon the
 * JGoogleAnalyticsTracker by Daniel Murphy.
 *   
 * @see https://code.google.com/archive/p/jgoogleanalyticstracker/
 * 
 * The code has been modified to add specific tracking information required
 * about the use of the GDSC plugins within ImageJ.
 * 
 * JGoogleAnalyticsTracker is distributed under the MIT software licence. 
 * Any code within this package is also distributed under this licence:
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
 *---------------------------------------------------------------------------*/
package gdsc.analytics;

/**
 * Provides a default implementation for logging that does nothing
 */
public class Logger
{
	/**
	 * Write an error message
	 * 
	 * @param format
	 * @param args
	 */
	public void error(String format, Object... args)
	{
	}

	/**
	 * Write an debug message
	 * 
	 * @param format
	 * @param args
	 */
	public void debug(String format, Object... args)
	{
	}
}
