/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2019 Alex Herbert
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
import uk.ac.sussex.gdsc.help.UrlUtils;

import ij.IJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Contains help dialogs for the GDSC ImageJ plugins.
 */
public class About_PlugIn implements PlugIn {
  /** The title of the plugin. */
  private static final String TITLE = "GDSC ImageJ Plugins";
  private static final String HELP_URL = UrlUtils.BASE_URL;
  private static final String YEAR = "2019";
  private static final String PLUGINS = "Plugins";

  private static boolean addSpacer;
  private static boolean installed;

  @Override
  public void run(String arg) {
    if (TextUtils.isNullOrEmpty(arg)) {
      arg = "about";
    }

    UsageTracker.recordPlugin(this.getClass(), arg);
    if ("about".equals(arg)) {
      showAbout();
      return;
    }

    if ("uninstall".equals(arg)) {
      showUninstallDialog();
      return;
    }

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
    if ((IJ.altKeyDown() || IJ.shiftKeyDown()
        || Boolean.parseBoolean(System.getProperty("about-install", "false")))
        // Try and install the plugins
        && installPlugins()) {
      return;
    }

    // Locate the README.txt file and load that into the dialog. Include SVN revision
    final Class<About_PlugIn> resourceClass = About_PlugIn.class;
    final InputStream readmeStream =
        resourceClass.getResourceAsStream("/uk/ac/sussex/gdsc/README.txt");

    StringBuilder msg = new StringBuilder();
    String helpUrl = HELP_URL;

    // Read the contents of the README file
    try (final BufferedReader input =
        new BufferedReader(new InputStreamReader(readmeStream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = input.readLine()) != null) {
        if (line.contains("http:")) {
          helpUrl = line;
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

    msg.append("\n \n(Click help for more information)");

    final GenericDialog gd = new GenericDialog(TITLE);
    gd.addMessage(msg.toString());
    gd.addHelp(helpUrl);
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

  private static boolean installPlugins() {
    if (installed) {
      return false;
    }
    installed = true;

    // Locate all the GDSC plugins using the plugins.config:
    final ArrayList<String[]> plugins = readPluginsConfig();

    if (plugins.isEmpty()) {
      return false;
    }

    ij.Menus.installPlugin("", ij.Menus.PLUGINS_MENU, "-", "", IJ.getInstance());

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

  private static ArrayList<String[]> readPluginsConfig() {
    final ArrayList<String[]> plugins = new ArrayList<>();

    final InputStream pluginsStream = getPluginsConfig();
    try (BufferedReader input =
        new BufferedReader(new InputStreamReader(pluginsStream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = input.readLine()) != null) {
        if (line.length() > 0 && line.charAt(0) == '#') {
          continue;
        }
        final String[] tokens = line.split(",");
        if (tokens.length == 3) {
          // Only copy the entries from the Plugins menu
          if (tokens[0].startsWith(PLUGINS)) {
            plugins.add(new String[] {tokens[1].trim(), tokens[2].trim()});
          }

          // Put a spacer between plugins if specified
        } else if ((tokens.length == 2 && tokens[0].startsWith(PLUGINS)
            && tokens[1].trim().equals("\"-\"")) || line.length() == 0) {
          plugins.add(new String[] {"spacer", ""});
        }
      }
    } catch (final IOException ex) {
      // Ignore
    }
    return plugins;
  }

  /**
   * Gets the plugins.config from the jar resources.
   *
   * @return the plugins config
   */
  public static InputStream getPluginsConfig() {
    // Get the embedded config in the jar file
    final Class<About_PlugIn> resourceClass = About_PlugIn.class;
    return resourceClass.getResourceAsStream("/uk/ac/sussex/gdsc/plugins.config");
  }

  private static void addPlugin(String commandName, final String command) {
    // Disect the ImageJ plugins.config string, e.g.:
    // Plugins>GDSC, "FindFoci", uk.ac.sussex.gdsc.foci.FindFoci

    commandName = commandName.replaceAll("\"", "");

    // Add to Plugins menu so that the macros/toolset will work
    if (!ij.Menus.commandInUse(commandName)) {
      if (addSpacer) {
        try {
          ij.Menus.getImageJMenu(PLUGINS).addSeparator();
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
