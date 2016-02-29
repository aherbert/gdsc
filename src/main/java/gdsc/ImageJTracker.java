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

import gdsc.analytics.ClientDataManager;
import gdsc.analytics.ClientParameters;
import gdsc.analytics.ConsoleLogger;
import gdsc.analytics.JGoogleAnalyticsTracker;
import gdsc.analytics.JGoogleAnalyticsTracker.DispatchMode;
import gdsc.analytics.JGoogleAnalyticsTracker.GoogleAnalyticsVersion;
import ij.IJ;
import ij.ImageJ;
import ij.Prefs;

/**
 * Provide methods to track code usage within ImageJ
 */
public class ImageJTracker
{
	private static final String GDSC_CLIENT_ID_PROPERTY = "gdsc.ga.clientId";

	private static JGoogleAnalyticsTracker tracker = null;

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
		String url = '/' + name;
		if (argument != null && argument.length() > 0)
			url += "?arg=" + argument;

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
		String url = '/' + title.replace('.', '/');
		if (method != null)
			url += "?method=" + method.getName();
		track(url, title);
	}

	private static synchronized void track(String pageUrl, String pageTitle)
	{
		// This method is synchronized due to the use of the same RequestData object

		initialise();

		tracker.pageview(pageUrl, pageTitle);
	}

	private static boolean initialise()
	{
		if (tracker == null)
		{
			// Create the tracker ...

			// Get the client parameters
			final String clientId = Prefs.get(GDSC_CLIENT_ID_PROPERTY, null);
			final ClientParameters clientParameters = new ClientParameters("UA-74107394-1", clientId,
					"GDSC ImageJ Plugins");
			
			ClientDataManager.populate(clientParameters);

			// Record for next time
			Prefs.set(GDSC_CLIENT_ID_PROPERTY, clientParameters.getClientId());

			clientParameters.setApplicationVersion(Version.getMajorMinorRelease());

			// Record the ImageJ information.
			ImageJ ij = IJ.getInstance();
			if (ij == null)
			{
				// Run embedded without showing
				ij = new ImageJ(ImageJ.NO_SHOW);
			}

			// ImageJ version
			// This call should return a string like:
			//   ImageJ 1.48a; Java 1.7.0_11 [64-bit]; Windows 7 6.1; 29MB of 5376MB (<1%)
			// (This should also be different if we are running within Fiji)
			String info = ij.getInfo();
			if (info.indexOf(';') != -1)
				info = info.substring(0, info.indexOf(';'));

			clientParameters.addCustomDimension(info);

			// Java version
			clientParameters.addCustomDimension(System.getProperty("java.version"));
			
			// OS
			clientParameters.addCustomDimension(System.getProperty("os.name"));
			clientParameters.addCustomDimension(System.getProperty("os.version"));
			clientParameters.addCustomDimension(System.getProperty("os.arch"));

			// Create the tracker
			tracker = new JGoogleAnalyticsTracker(clientParameters, GoogleAnalyticsVersion.V_1,
					DispatchMode.SINGLE_THREAD);

			// DEBUG: Enable logging
			JGoogleAnalyticsTracker.setLogger(new ConsoleLogger());

			return true;
		}
		return false;
	}
}
