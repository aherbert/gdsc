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

import gdsc.analytics.JGoogleAnalyticsTracker.DispatchMode;
import gdsc.analytics.JGoogleAnalyticsTracker.GoogleAnalyticsVersion;
import gdsc.analytics.system.AWTSystemPopulator;
import ij.IJ;
import ij.ImageJ;

/**
 * Provide methods to track code usage within ImageJ
 */
public class ImageJTracker
{
	private static JGoogleAnalyticsTracker tracker = null;

	static
	{
		// -=-=-=-=-=-=--=-=-=-=-=-=-=-=-=-=-=-=-
		// Add the Google Analytics tracking code
		// -=-=-=-=-=-=--=-=-=-=-=-=-=-=-=-=-=-=-
		AnalyticsConfigData data = new AnalyticsConfigData("");

		AWTSystemPopulator.populateConfigData(data);
		
		data.setEncoding(System.getProperty("file.encoding"));

        String region = System.getProperty("user.region");
        if (region == null) {
            region = System.getProperty("user.country");
        }
        data.setUserLanguage(System.getProperty("user.language") + "-" + region);
		

		// ImageJ information
		String ijVersion = IJ.getVersion();
		String osname = System.getProperty("os.name");
		String osversion = System.getProperty("os.version");
		String osarch = System.getProperty("os.arch");
		String javaVersion = System.getProperty("java.version"); //.substring(0,3);

		// Should return a string like:
		// ImageJ 1.48a; Java 1.7.0_11 [64-bit]; Windows 7 6.1; 29MB of 5376MB (<1%)
		// (This should also be different if we are running within Fiji)
		String info = new ImageJ().getInfo();

		tracker = new JGoogleAnalyticsTracker(data, GoogleAnalyticsVersion.V_4_7_2, DispatchMode.SINGLE_THREAD);
	}

	public static void recordPlugin(String name, String argument)
	{
		// JGoogleAnalyticsTracker 
		// .trackEvent(String argCategory, String argAction, String argLabel)
		// &utme=5(category*action[*label])[(value)]
		
		// .trackPageView(String argPageURL, String argPageTitle, String argHostName)
		// &utmdt=" + getURIString(argData.getPageTitle()))
		// &utmp=" + getURIString(argData.getPageURL())
		// &utmhn=" + getURIString(argData.getHostName())

		// Both delegate to tracker.makeCustomRequest(argData);
		// So all the above URL data could be encoded if necessary

		// JGoogleAnalytics (contains parent information)
		// .trackAsynchronously(FocusPoint focusPoint)
		// &utmdt=" + focusPoint.getContentTitle()); e.g. AppName-AppVersion-Name
		// &utmp=" + focusPoint.getContentURI());  e.g. /AppName/AppVersion/Name

		// Q. What data is easiest to analyse out of Google Analytics?
		// Comprehensive list of all &utm... params and valid values:
		//   http://code.google.com/apis/analytics/docs/tracking/gaTrackingTroubleshooting.html#gifParameters

		
		// utme is a custom field for any data. The ImageJ details could be entered here
		// and utmt set to 'custom variable'. The page URL could then be used to track the 
		// plugin details
		
		// We do not really need the color bit depth so we can use the ImageJ getScreenSize()
		// method to get the screen dimensions and remove the need for the AWTSystemPopulator.
		
		// Need to understand how this data is presented back in Google Analytics.

		AnalyticsRequestData data = new AnalyticsRequestData();
		data.setEventCategory("Plugin");
		data.setEventAction(name);
		data.setEventLabel(argument);
		//data.setEventValue(0);
		//data.setHostName("");
		//data.setPageTitle("");
		//data.setPageURL("");
		tracker.makeCustomRequest(data);

		//tracker.trackEvent("Plugin", name, argument);
	}
}
