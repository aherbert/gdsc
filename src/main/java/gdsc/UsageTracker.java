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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.HashMap;

import gdsc.analytics.ClientParameters;
import gdsc.analytics.ClientParametersManager;
import gdsc.analytics.ConsoleLogger;
import gdsc.analytics.JGoogleAnalyticsTracker;
import gdsc.analytics.JGoogleAnalyticsTracker.DispatchMode;
import gdsc.analytics.JGoogleAnalyticsTracker.MeasurementProtocolVersion;
import gdsc.help.URL;
import ij.IJ;
import ij.ImageJ;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

/**
 * Provide methods to track code usage within ImageJ
 */
public class UsageTracker implements PlugIn
{
	private static final String TITLE = "Usage Tracker";
	private static final String PROPERTY_GA_CLIENT_ID = "gdsc.ga.clientId";
	private static final String PROPERTY_GA_STATE = "gdsc.ga.state";
	private static final String PROPERTY_GA_LAST_VERSION = "gdsc.ga.lastVersion";
	private static final String PROPERTY_GA_ANONYMIZE = "gdsc.ga.anonymize";
	private static final int DISABLED = -1;
	private static final int UNKNOWN = 0;
	private static final int ENABLED = 1;

	private static JGoogleAnalyticsTracker tracker = null;
	private static HashMap<String, String[]> map = new HashMap<String, String[]>();

	/**
	 * Flag indicating that the user has opted out of analytics
	 */
	private static int state = (int) Prefs.get(PROPERTY_GA_STATE, UNKNOWN);
	/**
	 * Flag indicating that the IP address of the sender will be anonymized
	 */
	private static int anonymized = (int) Prefs.get(PROPERTY_GA_ANONYMIZE, UNKNOWN);

	/**
	 * Record the use of the ImageJ plugin using the raw class and argument passed by ImageJ. The plugins config
	 * file will be used to identify the correct ImageJ plugin path and title.
	 * 
	 * @param clazz
	 *            The class
	 * @param argument
	 *            The plugin argument
	 */
	public static void recordPlugin(@SuppressWarnings("rawtypes") Class clazz, String argument)
	{
		initialise();
		if (isDisabled())
			return;

		final String[] pair = map.get(getKey(clazz.getName(), argument));
		if (pair == null)
		{
			recordPlugin(clazz.getName().replace('.', '/'), argument);
		}
		else
		{
			tracker.pageview(pair[0], pair[1]);
		}
	}

	private static String getKey(String name, String argument)
	{
		return (argument != null && argument.length() > 0) ? name + '.' + argument : name;
	}

	/**
	 * Record the use of the ImageJ plugin
	 * 
	 * @param name
	 *            The plugin name
	 * @param argument
	 *            The plugin argument
	 */
	private static void recordPlugin(String name, String argument)
	{
		initialise();
		if (isDisabled())
			return;

		String url = '/' + name;
		if (argument != null && argument.length() > 0)
			url += "?arg=" + argument;

		trackPageView(url, name);
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
		initialise();
		if (isDisabled())
			return;

		final String title = clazz.getName();
		String url = '/' + title.replace('.', '/');
		if (method != null)
			url += "?method=" + method.getName();
		trackPageView(url, title);
	}

	private static void trackPageView(String pageUrl, String pageTitle)
	{
		tracker.pageview(pageUrl, pageTitle);
	}

	/**
	 * Create the tracker and then verify the opt in/out status of the user if it unknown or a new major.minor version.
	 */
	private static void initialise()
	{
		verifyStatus();
		
		// We only need to create the tracker if we are monitoring
		if (!isDisabled())
			initialiseTracker();
	}

	/**
	 * Create the tracker
	 */
	private static void initialiseTracker()
	{
		if (tracker == null)
		{
			synchronized (map)
			{
				// Check again since this may be a second thread that was waiting  
				if (tracker != null)
					return;

				buildPluginMap();

				createTracker();

				// XXX - Disable in production code 
				// DEBUG: Enable logging
				JGoogleAnalyticsTracker.setLogger(new ConsoleLogger());
			}
		}
	}

	private static void buildPluginMap()
	{
		InputStream pluginsStream = About_Plugin.getPluginsConfig();
		BufferedReader input = null;
		try
		{
			input = new BufferedReader(new InputStreamReader(pluginsStream));
			String line;
			while ((line = input.readLine()) != null)
			{
				if (line.startsWith("#"))
					continue;
				String[] tokens = line.split(",");
				if (tokens.length == 3)
				{
					// Plugins have [Menu path, Name, class(argument)], e.g.
					// Plugins>GDSC>Colocalisation, "CDA (macro)", gdsc.colocalisation.cda.CDA_Plugin("macro")

					String documentTitle = tokens[1].replaceAll("[\"']", "").trim();
					String documentPath = getDocumentPath(tokens[0], documentTitle);
					String key = getKey(tokens[2]);
					map.put(key, new String[] { documentPath, documentTitle });
				}
			}
		}
		catch (IOException e)
		{
			// Ignore 
		}
		finally
		{
			if (input != null)
			{
				try
				{
					input.close();
				}
				catch (IOException e)
				{
					// Ignore
				}
			}
		}

	}

	/**
	 * Split the menu path string and create a document path
	 * 
	 * @param menuPath
	 *            The ImageJ menu path string
	 * @param documentTitle
	 * @return The document path
	 */
	private static String getDocumentPath(String menuPath, String documentTitle)
	{
		StringBuilder sb = new StringBuilder();
		for (String field : menuPath.split(">"))
		{
			sb.append('/').append(field.trim());
		}
		sb.append('/').append(documentTitle);
		return sb.toString();
	}

	/**
	 * Get the raw class name and string argument from the ImageJ 'org.package.Class("argument")' field
	 * 
	 * @param string
	 *            The field contents
	 * @return The hash key
	 */
	private static String getKey(String string)
	{
		String name = string.trim(), argument = null;
		final int index = name.indexOf('(');
		if (index != -1)
		{
			// Get the remaining text and remove the quotes " and brackets ()
			argument = name.substring(index).replaceAll("[\"'()]", "").trim();
			// Get the class name
			name = name.substring(0, index);
		}
		return getKey(name, argument);
	}

	private static void createTracker()
	{
		// Get the client parameters
		final String clientId = Prefs.get(PROPERTY_GA_CLIENT_ID, null);
		final ClientParameters clientParameters = new ClientParameters("UA-74107394-1", clientId, About_Plugin.TITLE);

		ClientParametersManager.populate(clientParameters);

		// Record for next time
		Prefs.set(PROPERTY_GA_CLIENT_ID, clientParameters.getClientId());

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
		tracker = new JGoogleAnalyticsTracker(clientParameters, MeasurementProtocolVersion.V_1,
				DispatchMode.SINGLE_THREAD);

		tracker.setAnonymised(isAnonymized());
	}

	/**
	 * @return True if analytics is disabled
	 */
	public static boolean isDisabled()
	{
		// XXX - Tracking is disabled to allow a release of the code while this feature is not mature
		return true; // (state == DISABLED);
	}

	/**
	 * Set the state of the analytics tracker
	 * 
	 * @param disabled
	 *            True to disable analytics
	 */
	public static void setDisabled(boolean disabled)
	{
		final int oldState = UsageTracker.state; 
		UsageTracker.state = (disabled) ? DISABLED : ENABLED;

		Prefs.set(PROPERTY_GA_LAST_VERSION, getVersion());
		
		if (oldState != state)
		{
			Prefs.set(PROPERTY_GA_STATE, state);

			// Record this opt in/out status change
			
			// If the state was previously unknown and they opt out then do nothing.
			// This is a user who never wants to be tracked.
			if (oldState == UNKNOWN && disabled)
				return;

			// Otherwise record the in/out status change, either:
			// - The user was previously opt in but now opts out; or
			// - The user was previously opt out but now opts in

			final boolean enabled = !disabled;
			
			initialiseTracker();
			// Reset the session if tracking has been enabled.
			if (enabled)
				tracker.resetSession();
			// Track the opt status change with an event
			tracker.event("Tracking", Boolean.toString(enabled), getVersion(), null);			
		}
	}

	/**
	 * @return True if the IP address of the sender will be anonymized
	 */
	public static boolean isAnonymized()
	{
		return (anonymized == ENABLED);
	}

	/**
	 * Set the state of IP anonymization
	 * 
	 * @param anonymize
	 *            True if the IP address of the sender will be anonymized
	 */
	public static void setAnonymized(boolean anonymize)
	{
		final int oldAnonymized = anonymized;
		UsageTracker.anonymized = (anonymize) ? ENABLED : DISABLED;

		Prefs.set(PROPERTY_GA_LAST_VERSION, getVersion());
		
		if (oldAnonymized != anonymized)
		{
			Prefs.set(PROPERTY_GA_ANONYMIZE, anonymized);

			// Make sure the tracker is informed
			if (tracker != null)
				tracker.setAnonymised(isAnonymized());
		}
	}

	/**
	 * @return The version of the code
	 */
	private static String getVersion()
	{
		return Version.getMajorMinor();
	}

	/**
	 * Check the opt-in/out status. If it is not known then present a dialog to ask the user to set preferences.
	 */
	private static void verifyStatus()
	{
		// XXX - Tracking is disabled to allow a release of the code while this feature is not mature
		if (true)
			return;
		
		String lastVersion = Prefs.get(PROPERTY_GA_LAST_VERSION, "");
		String thisVersion = getVersion();
		if (state == UNKNOWN || anonymized == UNKNOWN || !lastVersion.equals(thisVersion))
			showDialog(true);
	}

	/* (non-Javadoc)
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	public void run(String arg)
	{
		recordPlugin(this.getClass(), arg);
		showDialog(false);
	}

	private static void showDialog(boolean autoMessage)
	{
		GenericDialog gd = new GenericDialog(TITLE);
		// @formatter:off
		gd.addMessage(About_Plugin.TITLE + "\n \n" +
				"The use of these plugins is free and unconstrained.\n" +
				"The code uses Google Analytics to help us understand\n" +
				"how users are using the plugins.\n \n" +
				"Privacy Policy\n \n" +
				"No personal information or data within ImageJ is recorded.\n \n" +
				"We record the plugin name and the software running ImageJ.\n" +
				"This happens in the background when nothing else is active so will\n" +
				"not slow down ImageJ. IP anonymization will truncate your IP\n" +
				"address to a region, usually a country. For more details click\n" +
				"the Help button.\n \n" + 
				"Click here to opt-out from Google Analytics");
		gd.addHelp(URL.TRACKING);
		gd.addCheckbox("Disabled", isDisabled());
		gd.addCheckbox("Anonymise IP", isAnonymized());
		if (autoMessage)
		{
		gd.addMessage(
				"Note: This dialog is automatically shown if your preferences\n" +
				"are not known, or after a release that changes the tracking data.");
		}
		// @formatter:on
		gd.hideCancelButton();
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		final boolean disabled = gd.getNextBoolean();
		final boolean anonymize = gd.getNextBoolean();
		// XXX - Tracking is disabled to allow a release of the code while this feature is not mature
		if (true)
			return;
		// Anonymize first to respect the user choice if they still have tracking on
		setAnonymized(anonymize);
		setDisabled(disabled);
	}
}
