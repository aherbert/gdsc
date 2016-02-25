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
 * to update to the latest version. It now supports session timeout and caching of the client data.
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
		return "5.6.7"; // Q. d suffix is for the debug script, e.g. 5.6.7d?
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.analytics.IGoogleAnalyticsURLBuilder#buildURL(gdsc.analytics.RequestData)
	 */
	public String buildURL(RequestData requestData)
	{
		final SessionData sessionData = client.getSessionData();
		// Refresh the session then get the timestamps and session number
		sessionData.refresh();
		final int hostnameHash = client.getHostName().hashCode();
		final int visitorId = sessionData.getVisitorId();
		final long initial = sessionData.getInitial();
		final long previous = sessionData.getPrevious();
		final long current = sessionData.getCurrent();
		final int sessionNumber = sessionData.getSessionNumber();
		final int hitCount = sessionData.getCount();

		// -=-=-=-=-=-=
		// Alex Herbert 
		// -=-=-=-=-=-=
		// I have modified this using the following test of Google Analytics script ga.js 
		// using a localhost webserver. Use this with the Chrome browser and the Google Analytics 
		// Debugger extension and you can see a lot of information on the URL sent to Google.
		//
		//<html>
		//<head>
		//<script type="text/javascript">
		//  var _gaq = _gaq || [];
		//  _gaq.push(['_setAccount', 'UA-0000000-0']);
		//  _gaq.push(['_setDomainName', 'none']);
		//  _gaq.push(['_setAllowLinker', true]);
		//  _gaq.push(['_trackPageview']);
		//  _gaq.push(['_setCustomVar',
		//   	  1,                // This custom var is set to slot #1.  Required parameter.
		//   	  'First',          // The name of the custom variable.  Required parameter.
		//   	  'v1; extra',      // The value of the custom variable.  Required parameter.
		//   						//  (possible values might be Free, Bronze, Gold, and Platinum)
		//   	  1                 // Sets the scope to visitor-level.  Optional parameter.
		//     ]); 
		//  _gaq.push(['_setCustomVar', 2, 'Another', 'Value', 2]); // Scoped to session level
		//
		//  (function() {
		//    var ga = document.createElement('script'); ga.type = 'text/javascript'; ga.async = true;
		//    ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';
		//    var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(ga, s);
		//  })();
		//</script>
		//<title>GA Test Page</title>
		//</head>
		//<body>
		//This is a test of Google Analytics!
		//</body>
		//</html>
		// -=-=-=-=-=-=

		StringBuilder sb = new StringBuilder();
		sb.append(URL_PREFIX);

		final long now = SessionData.timestamp();

		sb.append("?utmwv=").append(getVersion()); // version
		sb.append("&utms=").append(hitCount); // hit number for the session? 
		sb.append("&utmn=").append(random.nextInt()); // random int so no caching
		sb.append("&utmht=").append(now); // timestamp now

		// Build the client data
		if (client.getUrl() == null)
			buildClientURL();
		sb.append(client.getUrl());

		// Build the request data

		// Check for custom variables and allow caching this part of the URL
		if (requestData.getVariableCount() > 0)
		{
			if (requestData.getCustomVariablesURL() == null)
				buildCustomVariablesURL(requestData);
			sb.append(requestData.getCustomVariablesURL());
		}

		if (requestData.getPageTitle() != null)
		{
			sb.append("&utmdt=").append(URIEncoder.encodeURI(requestData.getPageTitle())); // page title
		}

		if (requestData.getPageURL() != null)
		{
			sb.append("&utmp=").append(URIEncoder.encodeURI(requestData.getPageURL())); // page url
		}

		// Hit ID. A random number used to link Analytics GIF requests with AdSense.
		sb.append("&utmhid=" + random.nextInt());

		// Cookie data

		// Note: URL encoding characters (http://www.degraeve.com/reference/urlencoding.php)
		// %2B    +
		// %3B    ;
		// %3D    =
		// %7C    |

		// References:
		// Many appear to old and out of date. This may be because Google Analytics now has a new 
		// script (analytics.js) to replace ga.js.
		// https://www.koozai.com/blog/analytics/google-analytics-and-cookies/
		// https://www.optimizesmart.com/google-analytics-cookies-ultimate-guide/
		// http://www.cardinalpath.com/ga-basics-the-structure-of-cookie-values/
		// Some of these are no longer used. I have build the cookie data based on debugging gs.js 
		// version 5.6.7

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
		//  current = time of beginning the current visit
		//  visits = the number of sessions (due to inactive session timeout)

		//_utma (unique visitor cookie)
		// Note: The hostnameHash is 1 in the v5.6.7 _utma cookie. Perhaps this is no longer used.

		sb.append("&utmcc=__utma%3D").append(hostnameHash).append(".").append(visitorId).append(".").append(initial)
				.append(".").append(previous).append(".").append(current).append(".").append(sessionNumber)
				.append("%3B");

		//_utmz (Campaign cookie)
		// (currently we do not support campaign data)
		//utmcsr = >It represents campaign source and stores the value of utm_source variable.
		//utmccn = >It represents campaign name and stores the value of utm_campaign variable.
		//utmcmd = >It represents campaign medium and stores the value of utm_medium variable.
		//utmctr = >It represents campaign term (keyword) and stores the value of utm_term variable.
		//utmcct = >It represents campaign content and stores the value of utm_content variable.

		final long campaignTime = initial;
		final int campaignSession = 1;
		final int campaignNumber = 1;
		sb.append("%2B__utmz%3D").append(hostnameHash).append(".").append(campaignTime).append(".")
				.append(campaignSession).append(".").append(campaignNumber).append(".");

		// => No campaign:
		// utmcsr%3D(direct)%7Cutmccn%D(direct)%7utmcmd%3D(none)

		sb.append("utmcsr%3D(direct)%7Cutmccn%D(direct)%7utmcmd%3D(none)%3B");

		// Not sure what this is? Sometimes it has a new int per page refresh. Sometimes it is empty.		
		sb.append("&utmjid=");
		//.append(random.nextInt());

		// Not sure what this is? Sometimes it is present, sometimes not
		//sb.append("&utmredir=1");

		// This is a new parameter that contains some internal state that helps improve ga.js.
		sb.append("&utmu=qxAAAAAAAAAAAAAAAAAAAAAE~");

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

		sb.append("&utmr=-"); //referrer URL
		sb.append("&utmsc=24-bit"); // colour depth
		sb.append("&utmje=1"); // java enabled
		sb.append("&utmfl=-"); //flash

		client.setUrl(sb.toString());
	}

	private void buildCustomVariablesURL(RequestData requestData)
	{
		// See https://developers.google.com/analytics/devguides/collection/gajs/gaTrackingCustomVariables

		// Custom variables have a label and value:
		//_gaq.push(['_setCustomVar',
		//     	  1,                // This custom var is set to slot #1.  Required parameter.
		//     	  'ImageJ',         // The name of the custom variable.  Required parameter.
		//     	  '1.48a; extra',          // The value of the custom variable.  Required parameter.
		//     						//  (possible values might be Free, Bronze, Gold, and Platinum)
		//     	  1                 // Sets the scope to visitor-level.  Optional parameter.
		//       ]);
		//_gaq.push(['_setCustomVar', 2, 'ImageJ2', '1.49a; plus', 2]);
		//
		// &utme=8(ImageJ*ImageJ2)9(1.48a%3B%20extra*1.49%3B%20plus)11(1*2)

		StringBuilder sb = new StringBuilder();

		final int count = requestData.getVariableCount();
		final String[] values = requestData.getValues();
		final String[] labels = requestData.getLabels();
		final int[] levels = requestData.getLevels();

		sb.append("&utme=8(");
		for (int i = 0; i < count; i++)
		{
			if (i != 0)
				sb.append('*');
			sb.append(URIEncoder.encodeURI(labels[i]));
		}
		sb.append(")9(");
		for (int i = 0; i < count; i++)
		{
			if (i != 0)
				sb.append('*');
			sb.append(URIEncoder.encodeURI(values[i]));
		}
		sb.append(")11(");
		for (int i = 0; i < count; i++)
		{
			if (i != 0)
				sb.append('*');
			sb.append(levels[i]); // Scope
		}
		sb.append(")");

		requestData.setCustomVariablesURL(sb.toString());
	}
}