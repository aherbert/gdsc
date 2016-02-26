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

import java.lang.reflect.Method;

import gdsc.analytics.ClientData;
import gdsc.analytics.ClientDataManager;
import gdsc.analytics.ConsoleLogger;
import gdsc.analytics.JGoogleAnalyticsTracker;
import gdsc.analytics.RequestData;
import gdsc.analytics.SessionData;
import gdsc.analytics.JGoogleAnalyticsTracker.DispatchMode;
import gdsc.analytics.JGoogleAnalyticsTracker.GoogleAnalyticsVersion;
import ij.IJ;
import ij.ImageJ;

/**
 * Provide methods to track code usage within ImageJ
 */
public class ImageJTracker
{
	private static JGoogleAnalyticsTracker tracker = null;
	private static String baseUrl = null;
	private static RequestData requestData = null;

	/**
	 * Record the use of the ImageJ plugin
	 * 
	 * @param name
	 *            The plugin name
	 * @param argument
	 *            The plugin argument
	 */
	public static void recordPlugin(String name, String argument)
	{
		String url = name;
		if (argument != null && argument.length() > 0)
			url += '/' + argument;

		track(url, name);
	}

	/**
	 * Record the use of a Java class
	 * 
	 * @param clazz
	 *            The class
	 */
	public static void recordClass(@SuppressWarnings("rawtypes") Class clazz)
	{
		recordClass(clazz, null);
	}

	/**
	 * Record the use of a Java class and method.
	 * <p>
	 * Only the method name is recorded for tracking simplicity. If multiple methods have the same name with different
	 * parameters and access then this information will be lost.
	 * 
	 * @param clazz
	 *            The class
	 * @param method
	 *            Optional method
	 */
	public static void recordClass(@SuppressWarnings("rawtypes") Class clazz, Method method)
	{
		final String title = clazz.getName();
		String url = title.replace('.', '/');
		if (method != null)
			url += '/' + method.getName();
		track(url, title);
	}

	private static synchronized void track(String pageUrl, String pageTitle)
	{
		// This method is synchronized due to the use of the same RequestData object

		initialise();

		String url = baseUrl + pageUrl;
		requestData.setPageURL(url);
		requestData.setPageTitle(pageTitle);
		tracker.makeCustomRequest(requestData);
	}

	private static boolean initialise()
	{
		if (tracker == null)
		{
			// Set up a base url using the package version.
			// Note that the GA tracking code is specific to this codebase and so we do not
			// explicitly identify it (i.e. the Java package/program name) here.
			baseUrl = '/' + Version.getMajorMinorRelease() + '/';

			// Create the tracker
			final SessionData sessionData = SessionData.newSessionData(new ImageJSessionStore());
			final ClientData data = ClientDataManager.newClientData("UA-74107394-1", sessionData);

			tracker = new JGoogleAnalyticsTracker(data, GoogleAnalyticsVersion.V_5_6_7, DispatchMode.SINGLE_THREAD);

			// DEBUG: Enable logging
			JGoogleAnalyticsTracker.setLogger(new ConsoleLogger());

			// Record the ImageJ information.
			ImageJ ij = IJ.getInstance();
			if (ij == null)
			{
				// Run embedded without showing
				ij = new ImageJ(ImageJ.NO_SHOW);
			}
			// Note: Session variables have a 128 character key-value pair limit.
			// For better use in Google Analytics reporting add values indvidually.
			requestData = new RequestData();

			// ImageJ version
			// This call should return a string like:
			//   ImageJ 1.48a; Java 1.7.0_11 [64-bit]; Windows 7 6.1; 29MB of 5376MB (<1%)
			// (This should also be different if we are running within Fiji)
			String info = ij.getInfo();
			if (info.indexOf(';') != -1)
				info = info.substring(0, info.indexOf(';'));
			requestData.addCustomVariable("ImageJ", info, RequestData.LEVEL_SESSION);
			
			// Java version
			requestData.addCustomVariable("java.version", System.getProperty("java.version"), RequestData.LEVEL_SESSION);
			// OS
			requestData.addCustomVariable("os.name", System.getProperty("os.name"), RequestData.LEVEL_SESSION);
			requestData.addCustomVariable("os.version", System.getProperty("os.version"), RequestData.LEVEL_SESSION);
			requestData.addCustomVariable("os.arch", System.getProperty("os.arch"), RequestData.LEVEL_SESSION);
			
			return true;
		}
		return false;
	}
}
