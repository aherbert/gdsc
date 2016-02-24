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

import java.util.Random;

/**
 * Details about the parameters used by Google Analytics can be found here:
 * http://code.google.com/apis/analytics/docs/tracking/gaTrackingTroubleshooting.html#gifParameters
 * 
 * This class has been forked from the JGoogleAnalyticsTracker project and modified by Alex Herbert 
 * to support session timeout and caching of the client data.
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
		sb.append("&utmt=").append(URIEncoder.encodeURI(requestData.getType()));

		if (requestData.getValue() != null)
		{
			sb.append("&utme=").append(URIEncoder.encodeURI(requestData.getValue()));
		}

		if (requestData.getPageTitle() != null)
		{
			sb.append("&utmdt=" + URIEncoder.encodeURI(requestData.getPageTitle())); // page title
		}

		if (requestData.getPageURL() != null)
		{
			sb.append("&utmp=" + URIEncoder.encodeURI(requestData.getPageURL())); // page url
		}

		// A random number used to link Analytics GIF requests with AdSense.
		sb.append("&utmhid=" + random.nextInt());

		// Cookie data

		// Note: URL encoding (http://www.degraeve.com/reference/urlencoding.php)
		// %2B    +
		// %3B    ;
		// %3D    =
		// %7C    |

		// References:
		// https://www.koozai.com/blog/analytics/google-analytics-and-cookies/
		// https://www.optimizesmart.com/google-analytics-cookies-ultimate-guide/
		// http://www.cardinalpath.com/ga-basics-the-structure-of-cookie-values/

		//_utma (unique visitor cookie)
		//_utmb (session cookie)
		//_utmc (session cookie)
		//_utmt (The _utmt cookie is used to throttle the request rate and it expires after 10 minutes)
		//_utmv (visitor segmentation cookie)
		//_utmx (Google Analytics Content Experiment cookie)
		//_utmz (Campaign cookie)

		// Session information:
		//  initial = time of initial visit
		//  previous = time of beginning the previous session
		//  current = time for current visit
		//  visits = the number of sessions (due to inactive session timeout)
		final int hostnameHash = client.getHostName().hashCode();
		final SessionData sessionData = client.getSessionData();
		final int visitorId = sessionData.getVisitorId();
		final long initial = sessionData.getInitial();
		final long previous = sessionData.getPrevious();
		final long current = sessionData.getCurrent();
		final int sessionNumber = sessionData.getSessionNumber();

		//_utma (unique visitor cookie)
		sb.append("&utmcc=__utma%3D").append(hostnameHash).append(".").append(visitorId).append(".").append(initial)
				.append(".").append(previous).append(".").append(current).append(".").append(sessionNumber).append("%3B");

		//_utmb (session cookie)
		sb.append("%2B__utmb%3D").append(hostnameHash).append("%3B");
		//_utmc (session cookie)
		// __utmc not used any more but include it anyway
		sb.append("%2B__utmc%3D").append(hostnameHash).append("%3B");

		//_utmv (visitor segmentation cookie)
		sb.append("%2B__utmv%3D").append(hostnameHash).append("%3B");

		//_utmx (Google Analytics Content Experiment cookie)
		// The _utmx cookie is Google Analytics Content Experiment cookie, 
		// which is used for A/B testing of different versions of a web page.

		//_utmz (Campaign cookie)
		// (currently we do not support campaign data)
		//utmcsr = >It represents campaign source and stores the value of utm_source variable.
		//utmccn = >It represents campaign name and stores the value of utm_campaign variable.
		//utmcmd = >It represents campaign medium and stores the value of utm_medium variable.
		//utmctr = >It represents campaign term (keyword) and stores the value of utm_term variable.
		//utmcct = >It represents campaign content and stores the value of utm_content variable.

		final int campaignNumber = 1;
		sb.append("%2B__utmz%3D").append(hostnameHash).append(".").append(now).append(".").append(sessionNumber)
				.append(".").append(campaignNumber).append(".");

		// => No campaign:
		// utmcsr%3D(direct)%7Cutmccn%D(direct)%7utmcmd%3D(none)

		sb.append("utmcsr%3D(direct)%7Cutmccn%D(direct)%7utmcmd%3D(none)%3B");

		sb.append("&gaq=1");

		return sb.toString();
	}

	private void buildClientURL()
	{
		StringBuilder sb = new StringBuilder();
		// Google Analytics tracking code
		sb.append("&utmac=" + client.getTrackingCode());
		if (client.getEncoding() != null)
		{
			sb.append("&utmcs=" + URIEncoder.encodeURI(client.getEncoding())); // encoding
		}
		else
		{
			sb.append("&utmcs=-");
		}
		if (client.getScreenResolution() != null)
		{
			sb.append("&utmsr=" + URIEncoder.encodeURI(client.getScreenResolution())); // screen resolution
		}
		if (client.getUserLanguage() != null)
		{
			sb.append("&utmul=" + URIEncoder.encodeURI(client.getUserLanguage())); // language
		}
		if (client.getHostName() != null)
		{
			sb.append("&utmhn=" + URIEncoder.encodeURI(client.getHostName())); // hostname
		}

		// Q. Is this required?
		// Analytics did not register when it was not present.
		final String referrerURL = "http://www.google.com/";
		sb.append("&utmr=" + URIEncoder.encodeURI(referrerURL)); //referrer URL

		sb.append("&utmsc=32-bit"); // colour depth
		sb.append("&utmje=1"); // java enabled		
		sb.append("&utmfl=-"); //flash
		sb.append("&utmcr=1"); //carriage return

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
}