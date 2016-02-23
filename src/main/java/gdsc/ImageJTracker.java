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

import gdsc.analytics.ClientData;
import gdsc.analytics.ClientDataManager;
import gdsc.analytics.ConsoleLogger;
import gdsc.analytics.JGoogleAnalyticsTracker;
import gdsc.analytics.JGoogleAnalyticsTracker.DispatchMode;
import gdsc.analytics.JGoogleAnalyticsTracker.GoogleAnalyticsVersion;
import ij.ImageJ;

/**
 * Provide methods to track code usage within ImageJ
 */
public class ImageJTracker
{
	private static JGoogleAnalyticsTracker tracker = null;
	private static String baseUrl = null;

	/**
	 * Record the use of the ImageJ plugin
	 * @param name The plugin name
	 * @param argument The plugin argument
	 */
	public static void recordPlugin(String name, String argument)
	{
		// Log a page view then, for the first call, log a custom variable  
		// Q. Is this the correct thing to do or can it be done all at once?
		final boolean firstCall = initialise();
		
		String url = baseUrl + name;
		if (argument!=null && argument.length()>0)
			url += '/' + argument;
		
		tracker.page(url, name);
		
		if (firstCall)
		{
			// Record the ImageJ information. This call should return a string like:
			// ImageJ 1.48a; Java 1.7.0_11 [64-bit]; Windows 7 6.1; 29MB of 5376MB (<1%)
			// (This should also be different if we are running within Fiji)
			String info = new ImageJ().getInfo();
			tracker.customVariable(url, name, info);
		}
	}

	private static boolean initialise()
	{
		if (tracker == null)
		{
			// Set up a base url using the package version.
			// Note that the GA tracking code is specific to this codebase and so we do not
			// explicitly identify it here.
			baseUrl = '/' + Version.getMajorMinorRelease() + '/';
			ClientData data = ClientDataManager.newClientData("UA-74107394-1");
			
			// Start a new session if plugins are not used for 10 minutes
			data.getSessionData().setTimeout(60 * 10);
			
			// Create the tracker
			tracker = new JGoogleAnalyticsTracker(data, GoogleAnalyticsVersion.V_4_7_2, DispatchMode.SINGLE_THREAD);
			
			// DEBUG: Enable logging
			JGoogleAnalyticsTracker.setLogger(new ConsoleLogger());
			
			return true;
		}
		return false;
	}
}
