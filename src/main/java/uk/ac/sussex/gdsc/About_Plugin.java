/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2018 Alex Herbert
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package uk.ac.sussex.gdsc;

import uk.ac.sussex.gdsc.core.utils.TextUtils;

import ij.IJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * Contains help dialogs for the GDSC ImageJ plugins.
 */
public class About_Plugin implements PlugIn {
  /** The title of the plugin. */
  private static final String TITLE = "GDSC ImageJ Plugins";
  private static final String HELP_URL =
      "http://www.sussex.ac.uk/gdsc/intranet/microscopy/imagej/plugins";
  private static final String YEAR = "2018";

  @Override
  public void run(String arg) {
    if (arg == null || arg.length() == 0) {
      arg = "about";
    }

    UsageTracker.recordPlugin(this.getClass(), arg);
    if (arg.equals("about")) {
      showAbout();
      return;
    }

    if (arg.equals("uninstall")) {
      showUninstallDialog();
      return;
    }

    // if (arg.equals("options1"))
    // Do something ...

    showAbout();
  }

  /**
   * Show uninstall dialog.
   */
  public void showUninstallDialog() {
    IJ.showMessage(TITLE, "To uninstall this plugin, move gdsc_.jar out\n"
        + "of the plugins folder and restart ImageJ.");
  }

  /**
   * Show about dialog.
   */
  public static void showAbout() {
    if (IJ.altKeyDown() || IJ.shiftKeyDown()
        || Boolean.parseBoolean(System.getProperty("about-install", "false"))) {
      if (installPlugins()) {
        return;
      }
    }

    // Locate the README.txt file and load that into the dialog. Include SVN revision
    final Class<About_Plugin> resourceClass = About_Plugin.class;
    final InputStream readmeStream =
        resourceClass.getResourceAsStream("/uk/ac/sussex/gdsc/README.txt");

    StringBuilder msg = new StringBuilder();
    String helpURL = HELP_URL;

    // Read the contents of the README file
    try (final BufferedReader input = new BufferedReader(new InputStreamReader(readmeStream))) {
      String line;
      while ((line = input.readLine()) != null) {
        if (line.contains("http:")) {
          helpURL = line;
        } else {
          if (line.equals("")) {
            line = " "; // Required to insert a line in the GenericDialog
          }
          msg.append(line).append("\n");
        }
      }
    } catch (final IOException ex) {
      // Default message
      msg.append("GDSC Plugins for ImageJ\n");
      msg.append(" \n");
      msg.append("Copyright (C) ").append(YEAR).append(" Alex Herbert\n");
      msg.append("MRC Genome Damage and Stability Centre\n");
      msg.append("University of Sussex, UK\n");
    }

    // Build final message
    msg = new StringBuilder(msg.toString().trim());
    addVersion(msg, "GDSC", Version.getVersion(), Version.getBuildDate(), Version.getBuildNumber());
    addVersion(msg, "GDSC-Core", uk.ac.sussex.gdsc.core.VersionUtils.getVersion(),
        uk.ac.sussex.gdsc.core.VersionUtils.getBuildDate(),
        uk.ac.sussex.gdsc.core.VersionUtils.getBuildNumber());

    if (helpURL != null) {
      msg.append("\n \n(Click help for more information)");
    }

    final GenericDialog gd = new GenericDialog(TITLE);
    gd.addMessage(msg.toString());
    gd.addHelp(helpURL);
    gd.hideCancelButton();
    gd.showDialog();
  }

  private static void addVersion(StringBuilder msg, String name, String version, String buildDate,
      String buildNumber) {
    final boolean hasVersion = !TextUtils.isNullOrEmpty(version);
    final boolean hasBuildDate = !TextUtils.isNullOrEmpty(buildDate);
    final boolean hasBuildNumber = !TextUtils.isNullOrEmpty(buildNumber);
    if (hasVersion || hasBuildDate || hasBuildNumber) {
      msg.append("\n \n").append(name).append("\n");
    }
    if (hasVersion) {
      msg.append("Version : ").append(version).append("\n");
    }
    if (hasBuildDate) {
      msg.append("Build Date : ").append(buildDate).append("\n");
    }
    if (hasBuildNumber) {
      msg.append("Build Number : ").append(buildNumber).append("\n");
    }
  }

  private static boolean addSpacer = false;
  private static boolean installed = false;

  private static boolean installPlugins() {
    if (installed) {
      return false;
    }
    installed = true;

    // Locate all the GDSC plugins using the plugins.config:
    final InputStream pluginsStream = getPluginsConfig();

    ij.Menus.installPlugin("", ij.Menus.PLUGINS_MENU, "-", "", IJ.getInstance());

    // Read into memory
    final ArrayList<String[]> plugins = new ArrayList<>();
    int gaps = 0;
    try (BufferedReader input = new BufferedReader(new InputStreamReader(pluginsStream))) {
      String line;
      while ((line = input.readLine()) != null) {
        if (line.startsWith("#")) {
          continue;
        }
        final String[] tokens = line.split(",");
        if (tokens.length == 3) {
          // Only copy the entries from the Plugins menu
          if (tokens[0].startsWith("Plugins")) {
            if (!plugins.isEmpty()) {
              // Multiple gaps indicates a new column
              if (gaps > 1) {
                // plugins.add(new String[] { "next", "" });
              }
            }
            gaps = 0;
            plugins.add(new String[] {tokens[1].trim(), tokens[2].trim()});
          }
        } else {
          gaps++;
        }

        // Put a spacer between plugins if specified
        if ((tokens.length == 2 && tokens[0].startsWith("Plugins")
            && tokens[1].trim().equals("\"-\"")) || line.length() == 0) {
          plugins.add(new String[] {"spacer", ""});
        }
      }
    } catch (final IOException ex) {
      // Ignore
    }

    if (plugins.isEmpty()) {
      return false;
    }

    addSpacer = false;
    for (final String[] plugin : plugins) {
      if (plugin[0].equals("spacer")) {
        addSpacer = true;
      } else {
        addPlugin(plugin[0], plugin[1]);
      }
    }

    return true;
  }

  /**
   * Gets the plugins.config from the jar resources.
   *
   * @return the plugins config
   */
  public static InputStream getPluginsConfig() {
    // Get the embedded config in the jar file
    final Class<About_Plugin> resourceClass = About_Plugin.class;
    final InputStream readmeStream =
        resourceClass.getResourceAsStream("/uk/ac/sussex/gdsc/plugins.config");
    return readmeStream;
  }

  private static void addPlugin(String commandName, final String command) {
    // Disect the ImageJ plugins.config string, e.g.:
    // Plugins>GDSC, "FindFoci", uk.ac.sussex.gdsc.foci.FindFoci

    commandName = commandName.replaceAll("\"", "");

    // Add to Plugins menu so that the macros/toolset will work
    if (!ij.Menus.commandInUse(commandName)) {
      if (addSpacer) {
        try {
          ij.Menus.getImageJMenu("Plugins").addSeparator();
        } catch (final NoSuchMethodError ex) {
          // Ignore. This ImageJ method is from IJ 1.48+
        }
      }
      ij.Menus.installPlugin(command, ij.Menus.PLUGINS_MENU, commandName, "", IJ.getInstance());
    }

    if (addSpacer) {
      addSpacer = false;
    }
  }
}
