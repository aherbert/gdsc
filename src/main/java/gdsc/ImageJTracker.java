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

import gdsc.analytics.ClientParametersManager;
import gdsc.analytics.ClientParameters;
import gdsc.analytics.ConsoleLogger;
import gdsc.analytics.JGoogleAnalyticsTracker;
import gdsc.analytics.JGoogleAnalyticsTracker.DispatchMode;
import gdsc.analytics.JGoogleAnalyticsTracker.MeasurementProtocolVersion;
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
	private static HashMap<String, String[]> map = new HashMap<String, String[]>();

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

	private static void track(String pageUrl, String pageTitle)
	{
		initialise();
		tracker.pageview(pageUrl, pageTitle);
	}

	private static void initialise()
	{
		if (tracker == null)
		{
			synchronized (map)
			{
				// Check again since this may be a second thread that was waiting  
				if (tracker != null)
					return;

				buildPluginMap();

				// Create the tracker ...

				// Get the client parameters
				final String clientId = Prefs.get(GDSC_CLIENT_ID_PROPERTY, null);
				final ClientParameters clientParameters = new ClientParameters("UA-74107394-1", clientId,
						"GDSC ImageJ Plugins");

				ClientParametersManager.populate(clientParameters);

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
				tracker = new JGoogleAnalyticsTracker(clientParameters, MeasurementProtocolVersion.V_1,
						DispatchMode.SINGLE_THREAD);

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
	 * @param string The field contents
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
}
