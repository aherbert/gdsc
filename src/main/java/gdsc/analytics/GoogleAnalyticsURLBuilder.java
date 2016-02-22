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
/**
 * Created at Jul 20, 2010, 4:39:49 AM
 */
package gdsc.analytics;

import java.util.Random;

/**
 * Details about the parameters used by Google Analytics can be found here:
 * http://code.google.com/apis/analytics/docs/tracking/gaTrackingTroubleshooting.html#gifParameters
 * 
 * This class has been forked from the JGoogleAnalyticsTracker project and modified by Alex Herbert.
 * 
 * @author Daniel Murphy, Alex Herbert
 * @see http://code.google.com/apis/analytics/docs/tracking/gaTrackingTroubleshooting.html#gifParameters
 */
public class GoogleAnalyticsURLBuilder implements IGoogleAnalyticsURLBuilder
{
	public static final String URL_PREFIX = "http://www.google-analytics.com/__utm.gif";

	private ClientData client;
	private Random random = new Random((long) (Math.random() * Long.MAX_VALUE));

	public GoogleAnalyticsURLBuilder(ClientData client)
	{
		this.client = client;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.analytics.IGoogleAnalyticsURLBuilder#getVersion()
	 */
	public String getVersion()
	{
		return "4.7.2";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.analytics.IGoogleAnalyticsURLBuilder#buildURL(gdsc.analytics.RequestData)
	 */
	public String buildURL(RequestData requestData)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(URL_PREFIX);

		long now = System.currentTimeMillis();

		sb.append("?utmwv=" + getVersion()); // version
		sb.append("&utmn=" + random.nextInt()); // random int so no caching

		// Build the client data
		if (client.getUrl() == null)
			buildClientURL();
		sb.append(client.getUrl());
		
		// Build the request data
		sb.append("&utmt=").append(requestData.getType());

		if (requestData.getValue() != null)
		{
			sb.append("&utme=").append(getURIString(requestData.getValue()));
		}

		if (requestData.getPageTitle() != null)
		{
			sb.append("&utmdt=" + getURIString(requestData.getPageTitle())); // page title
		}

		if (requestData.getPageURL() != null)
		{
			sb.append("&utmp=" + getURIString(requestData.getPageURL())); // page url
		}
		
		// A random number used to link Analytics GIF requests with AdSense.
		sb.append("&utmhid=" + random.nextInt());

		// cookie data
		//	utmcsr
		//	Identifies a search engine, newsletter name, or other source specified in the
		//	utm_source query parameter See the �Marketing Campaign Tracking�
		//	section for more information about query parameters.
		//
		//	utmccn
		//	Stores the campaign name or value in the utm_campaign query parameter.
		//
		//	utmctr
		//	Identifies the keywords used in an organic search or the value in the utm_term query parameter.
		//
		//	utmcmd
		//	A campaign medium or value of utm_medium query parameter.
		//
		//	utmcct
		//	Campaign content or the content of a particular ad (used for A/B testing)
		//	The value from utm_content query parameter.

		// utmcsr%3D(direct)%7Cutmccn%D(direct)%7utmcmd%3D(none)

		//	private String utmcsr = "(direct)";
		//	private String utmccn = "(direct)";
		//	private String utmctr = null;
		//	private String utmcmd = "(none)";
		//	private String utmcct = null;
		// utmccn=(organic)|utmcsr=google|utmctr=snotwuh |utmcmd=organic
		
		String utmcsr = getURIString("(direct)");
		String utmccn = getURIString("(direct)");
		String utmctr = null;
		String utmcmd = getURIString("(none)");
		String utmcct = null;

		int hostnameHash = hostnameHash(client.getHostName());
		SessionData sessionData = client.getSessionData();
		int visitorId = sessionData.getVisitorId();
		long timestampCurrent = sessionData.getCurrent();
		long timestampFirst = sessionData.getInitial();
		long timestampPrevious = sessionData.getPrevious();
		int visits = sessionData.getSessionNumber();

		// Q. What are first, previous, current, visits?
		// Should they be:
		// first = time the session started
		// previous = time of the last action/session?
		// current = time now
		// visits = the number of sessions
		// In this case we should update the VisitorData object to reflect this.
		// http://www.cardinalpath.com/ga-basics-the-structure-of-cookie-values/

		sb.append("&utmcc=__utma%3D" + hostnameHash + "." + visitorId + "." + timestampFirst + "." + timestampPrevious +
				"." + timestampCurrent + "." + visits + "%3B%2B__utmz%3D" + hostnameHash + "." + now +
				".1.1.utmcsr%3D" + utmcsr + "%7Cutmccn%3D" + utmccn + "%7utmcmd%3D" + utmcmd +
				(utmctr != null ? "%7Cutmctr%3D" + utmctr : "") + (utmcct != null ? "%7Cutmcct%3D" + utmcct : "") +
				"%3B&gaq=1");
		return sb.toString();
	}

	private void buildClientURL()
	{
		StringBuilder sb = new StringBuilder();
		// Google Analytics tracking code
		sb.append("&utmac=" + client.getTrackingCode());
		if (client.getEncoding() != null)
		{
			sb.append("&utmcs=" + getURIString(client.getEncoding())); // encoding
		}
		else
		{
			sb.append("&utmcs=-");
		}
		if (client.getScreenResolution() != null)
		{
			sb.append("&utmsr=" + getURIString(client.getScreenResolution())); // screen resolution
		}
		sb.append("&utmsc=32-bit"); // color depth
		if (client.getUserLanguage() != null)
		{
			sb.append("&utmul=" + getURIString(client.getUserLanguage())); // language
		}
		if (client.getHostName() != null)
		{
			sb.append("&utmhn=" + getURIString(client.getHostName())); // hostname
		}
		sb.append("&utmje=1"); // java enabled			
		client.setUrl(sb.toString());
	}

	/*
	 * page view url:
	 * http://www.google-analytics.com/__utm.gif
	 * ?utmwv=4.7.2
	 * &utmn=631966530
	 * &utmhn=www.dmurph.com
	 * &utmcs=ISO-8859-1
	 * &utmsr=1280x800
	 * &utmsc=24-bit
	 * &utmul=en-us
	 * &utmje=1
	 * &utmfl=10.1%20r53
	 * &utmdt=Hello
	 * &utmhid=2043994175
	 * &utmr=0
	 * &utmp=%2Ftest%2Ftest.php
	 * &utmac=UA-17109202-5
	 * &utmcc=__utma%3D143101472.2118079581.1279863622.1279863622.1279863622.1%3B%2B__utmz%3D143101472.1279863622.1.1.
	 * utmcsr
	 * %3D(direct)%7Cutmccn%3D(direct)%7Cutmcmd%3D(none)%3B&gaq=1
	 */

	// tracking url:
	/*
	 * http://www.google-analytics.com/__utm.gif
	 * ?utmwv=4.7.2
	 * &utmn=480124034
	 * &utmhn=www.dmurph.com
	 * &utmt=event
	 * &utme=5(Videos*Play)
	 * &utmcs=ISO-8859-1
	 * &utmsr=1280x800
	 * &utmsc=24-bit
	 * &utmul=en-us
	 * &utmje=1
	 * &utmfl=10.1%20r53
	 * &utmdt=Hello
	 * &utmhid=166062212
	 * &utmr=0
	 * &utmp=%2Ftest%2Ftest.php
	 * &utmac=UA-17109202-5
	 * &utmcc=__utma%3D143101472.2118079581.1279863622.1279863622.1279863622.1%3B%2B__utmz%3D143101472.1279863622.1.1.
	 * utmcsr
	 * %3D(direct)%7Cutmccn%3D(direct)%7Cutmcmd%3D(none)%3B&gaq=1
	 */

	private String getURIString(String argString)
	{
		if (argString == null)
		{
			return null;
		}
		return URIEncoder.encodeURI(argString);
	}

	private int hostnameHash(String hostname)
	{
		return hostname.hashCode();
	}
}