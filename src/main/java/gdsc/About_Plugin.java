package gdsc;

/*----------------------------------------------------------------------------- 
 * GDSC Plugins for ImageJ
 * 
 * Copyright (C) 2011 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

import ij.IJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * Contains help dialogs for the GDSC ImageJ plugins
 */
public class About_Plugin implements PlugIn
{
	private static String TITLE = "GDSC ImageJ Plugins";
	private static String HELP_URL = "http://www.sussex.ac.uk/gdsc/intranet/microscopy/imagej/plugins";
	private static String YEAR = "2015";

	public void run(String arg)
	{
		if (arg == null || arg.length() == 0)
			arg = "about";

		ImageJTracker.recordPlugin(this.getClass(), arg);
		if (arg.equals("about"))
		{
			showAbout();
			return;
		}

		if (arg.equals("uninstall"))
		{
			showUnintallDialog();
			return;
		}

		//if (arg.equals("options1"))
		// Do something ...

		showAbout();
	}

	public void showUnintallDialog()
	{
		IJ.showMessage(TITLE,
				"To uninstall this plugin, move gdsc_.jar out\n" + "of the plugins folder and restart ImageJ.");
	}

	public static void showAbout()
	{
		if (IJ.altKeyDown() || IJ.shiftKeyDown() || Boolean.parseBoolean(System.getProperty("about-install", "false")))
		{
			if (installPlugins())
				return;
		}

		// Locate the README.txt file and load that into the dialog. Include SVN revision
		Class<About_Plugin> resourceClass = About_Plugin.class;
		InputStream readmeStream = resourceClass.getResourceAsStream("/gdsc/README.txt");

		StringBuilder msg = new StringBuilder();
		String helpURL = HELP_URL;
		String version = Version.getVersion();
		String buildDate = Version.getBuildDate();

		try
		{
			// Read the contents of the README file
			BufferedReader input = new BufferedReader(new InputStreamReader(readmeStream));
			String line;
			while ((line = input.readLine()) != null)
			{
				if (line.contains("http:"))
				{
					helpURL = line;
				}
				else
				{
					if (line.equals(""))
						line = " "; // Required to insert a line in the GenericDialog
					msg.append(line).append("\n");
				}
			}
		}
		catch (IOException e)
		{
			// Default message
			msg.append("GDSC Plugins for ImageJ\n");
			msg.append(" \n");
			msg.append("Copyright (C) ").append(YEAR).append(" Alex Herbert\n");
			msg.append("MRC Genome Damage and Stability Centre\n");
			msg.append("University of Sussex, UK\n");
		}

		// Build final message
		msg = new StringBuilder(msg.toString().trim());
		if (version != Version.UNKNOWN || buildDate != Version.UNKNOWN)
			msg.append("\n \n");
		if (version != Version.UNKNOWN)
			msg.append("Version : ").append(version).append("\n");
		if (buildDate != Version.UNKNOWN)
			msg.append("Build Date : ").append(buildDate).append("\n");
		if (helpURL != null)
			msg.append("\n \n(Click help for more information)");

		GenericDialog gd = new GenericDialog(TITLE);
		gd.addMessage(msg.toString());
		gd.addHelp(helpURL);
		gd.hideCancelButton();
		gd.showDialog();
	}

	private static boolean addSpacer = false;
	private static boolean installed = false;

	private static boolean installPlugins()
	{
		if (installed)
			return false;
		installed = true;

		// Locate all the GDSC plugins using the plugins.config:
		InputStream pluginsStream = getPluginsConfig();

		ij.Menus.installPlugin("", ij.Menus.PLUGINS_MENU, "-", "", IJ.getInstance());

		// Read into memory
		ArrayList<String[]> plugins = new ArrayList<String[]>();
		int gaps = 0;
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
					// Only copy the entries from the Plugins menu
					if (tokens[0].startsWith("Plugins"))
					{
						if (!plugins.isEmpty())
						{
							// Multiple gaps indicates a new column
							if (gaps > 1)
							{
								//plugins.add(new String[] { "next", "" });
							}
						}
						gaps = 0;
						plugins.add(new String[] { tokens[1].trim(), tokens[2].trim() });
					}
				}
				else
					gaps++;

				// Put a spacer between plugins if specified
				if ((tokens.length == 2 && tokens[0].startsWith("Plugins") && tokens[1].trim().equals("\"-\"")) ||
						line.length() == 0)
				{
					plugins.add(new String[] { "spacer", "" });
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

		if (plugins.isEmpty())
			return false;

		addSpacer = false;
		for (String[] plugin : plugins)
		{
			if (plugin[0].equals("spacer"))
				addSpacer = true;
			else
				addPlugin(plugin[0], plugin[1]);
		}

		return true;
	}

	public static InputStream getPluginsConfig()
	{
		// Get the embedded config in the jar file
		Class<About_Plugin> resourceClass = About_Plugin.class;
		InputStream readmeStream = resourceClass.getResourceAsStream("/gdsc/plugins.config");
		return readmeStream;
	}

	private static void addPlugin(String commandName, final String command)
	{
		// Disect the ImageJ plugins.config string, e.g.:
		// Plugins>GDSC, "FindFoci", gdsc.foci.FindFoci

		commandName = commandName.replaceAll("\"", "");

		// Add to Plugins menu so that the macros/toolset will work
		if (!ij.Menus.commandInUse(commandName))
		{
			if (addSpacer)
			{
				try
				{
					ij.Menus.getImageJMenu("Plugins").addSeparator();
				}
				catch (NoSuchMethodError e)
				{
					// Ignore. This ImageJ method is from IJ 1.48+
				}
			}
			ij.Menus.installPlugin(command, ij.Menus.PLUGINS_MENU, commandName, "", IJ.getInstance());
		}

		if (addSpacer)
		{
			addSpacer = false;
		}
	}
}
