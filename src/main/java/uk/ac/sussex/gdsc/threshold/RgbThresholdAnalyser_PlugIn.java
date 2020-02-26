/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2020 Alex Herbert
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

package uk.ac.sussex.gdsc.threshold;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.text.TextWindow;
import java.io.File;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold;
import uk.ac.sussex.gdsc.core.utils.MathUtils;

/**
 * For all the RGB images in one directory, look for a matching image in a second directory that has
 * 3 channels. Compute the minimum value (i.e. the threshold) for pixels within the red and green
 * channel of the RGB image. Then run thresholding methods on the pixels within the mask region
 * defined by channel 3 and output a table of results.
 */
public class RgbThresholdAnalyser_PlugIn implements PlugIn {
  private static final String TITLE = "RGB Threshold Analyser";
  private static AtomicReference<TextWindow> resultsWindowRef = new AtomicReference<>();

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    String dir1;
    String dir2;

    /**
     * Default constructor.
     */
    Settings() {
      dir1 = "";
      dir2 = "";
    }

    /**
     * Copy constructor.
     *
     * @param source the source
     */
    private Settings(Settings source) {
      dir1 = source.dir1;
      dir2 = source.dir2;
    }

    /**
     * Copy the settings.
     *
     * @return the settings
     */
    Settings copy() {
      return new Settings(this);
    }

    /**
     * Load a copy of the settings.
     *
     * @return the settings
     */
    static Settings load() {
      return lastSettings.get().copy();
    }

    /**
     * Save the settings.
     */
    void save() {
      lastSettings.set(this);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    final Settings settings = Settings.load();
    settings.dir1 = ImageJUtils.getDirectory("RGB_Directory", settings.dir1);
    if (settings.dir1 == null) {
      return;
    }
    settings.dir2 = ImageJUtils.getDirectory("Image_Directory", settings.dir2);
    if (settings.dir2 == null) {
      return;
    }
    settings.save();

    final File[] fileList = (new File(settings.dir1)).listFiles((dir, name) -> {
      name = name.toLowerCase(Locale.US);
      return name.endsWith(".tif") || name.endsWith(".tiff");
    });
    if (fileList == null) {
      return;
    }

    int count = 0;
    for (final File file : fileList) {
      count++;

      final ImagePlus imp = IJ.openImage(file.getAbsolutePath());
      if (imp == null) {
        IJ.log("Cannot open " + file.getAbsolutePath());
        continue;
      }
      if (imp.getBitDepth() != 24) {
        IJ.log("File is not an RGB image " + file.getAbsolutePath());
        continue;
      }

      // Find the matching image
      final String name = file.getName();
      final String path = settings.dir2 + name;
      final ImagePlus imp2 = IJ.openImage(path);
      if (imp2 == null) {
        IJ.log("Cannot open " + path);
        continue;
      }
      if (imp2.getNChannels() != 3) {
        IJ.log("File does not have 3 channels " + path);
        continue;
      }
      if (imp.getWidth() != imp2.getWidth() || imp.getHeight() != imp2.getHeight()) {
        IJ.log("Matching image is not the same dimensions " + path);
        continue;
      }
      if (!(imp2.getBitDepth() == 8 || imp2.getBitDepth() == 16)) {
        IJ.log("File is not an 8/16 bit image " + file.getAbsolutePath());
        continue;
      }

      IJ.showStatus(String.format("Processing %d / %d", count, fileList.length));

      final TextWindow resultsWindow = createResultsWindow();

      // Extract the 3 channels
      final ColorProcessor cp = (ColorProcessor) imp.getProcessor();
      final ImageProcessor ip1 = getProcessor(imp2, 1);
      final ImageProcessor ip2 = getProcessor(imp2, 2);
      final ImageProcessor ip3 = getProcessor(imp2, 3);

      analyse(resultsWindow, name, cp, 1, ip1, ip3);
      analyse(resultsWindow, name, cp, 2, ip2, ip3);

      if (ImageJUtils.isInterrupted()) {
        return;
      }
    }

    IJ.showStatus(TITLE + " Finished");
  }

  private static void analyse(TextWindow resultsWindow, String name, ColorProcessor cp, int channel,
      ImageProcessor ip, ImageProcessor maskIp) {
    final byte[] mask = cp.getChannel(channel);

    // Get the histogram for the channel
    final int[] h = new int[(ip instanceof ByteProcessor) ? 256 : 65336];

    // Get the manual threshold within the color channel mask
    int manual = Integer.MAX_VALUE;
    for (int i = 0; i < mask.length; i++) {
      if (maskIp.get(i) != 0) {
        h[ip.get(i)]++;

        if (mask[i] != 0 && manual > ip.get(i)) {
          manual = ip.get(i);
        }
      }
    }

    // Check the threshold is valid
    int error = 0;
    long sum = 0;
    for (int i = 0; i < mask.length; i++) {
      if (maskIp.get(i) != 0 && mask[i] == 0 && ip.get(i) >= manual) {
        error++;
        sum += ip.get(i) - manual;
      }
    }
    if (error != 0) {
      ImageJUtils.log("%s [%d] %d error pixels (sum = %d)", name, channel, error, sum);
    }

    final double[] stats = getStatistics(h);

    final String[] methods = AutoThreshold.getMethods(true);
    final int[] thresholds = new int[methods.length];
    for (int i = 0; i < thresholds.length; i++) {
      thresholds[i] = AutoThreshold.getThreshold(methods[i], h);
    }

    addResult(resultsWindow, name, channel, stats, manual, thresholds, h);
  }

  private static ImageProcessor getProcessor(ImagePlus imp, int channel) {
    imp.setC(channel);
    return imp.getProcessor().duplicate();
  }

  /**
   * Return the image statistics.
   *
   * @param hist The image histogram
   * @return Array containing: min, max, av, stdDev
   */
  private static double[] getStatistics(int[] hist) {
    // Get the limits
    int min = 0;
    int max = hist.length - 1;
    while ((hist[min] == 0) && (min < max)) {
      min++;
    }
    while ((hist[max] == 0) && (max > min)) {
      max--;
    }

    // Get the average
    int count;
    double value;
    double sum = 0.0;
    double sum2 = 0.0;
    long total = 0;
    for (int i = min; i <= max; i++) {
      if (hist[i] > 0) {
        count = hist[i];
        total += count;
        value = i;
        sum += value * count;
        sum2 += (value * value) * count;
      }
    }
    final double av = MathUtils.div0(sum, total);

    // Get the Std.Dev
    double stdDev;
    if (total > 0) {
      final double d = total;
      stdDev = (d * sum2 - sum * sum) / d;
      if (stdDev > 0.0) {
        stdDev = Math.sqrt(stdDev / (d - 1.0));
      } else {
        stdDev = 0.0;
      }
    } else {
      stdDev = 0.0;
    }

    return new double[] {min, max, av, stdDev};
  }

  private static TextWindow createResultsWindow() {
    return ImageJUtils.refresh(resultsWindowRef,
        () -> new TextWindow(TITLE + " Results", createResultsHeader(), "", 900, 500));
  }

  private static String createResultsHeader() {
    final StringBuilder sb = new StringBuilder();
    sb.append("Image\t");
    sb.append("Channel\t");
    sb.append("Min\tMax\tAv\tSD");

    addMethod(sb, "Manual");
    final String[] methods = AutoThreshold.getMethods(true);
    for (final String method : methods) {
      addMethod(sb, method);
    }
    return sb.toString();
  }

  private static void addMethod(StringBuilder sb, String method) {
    sb.append('\t').append(method).append("\tCount\tSum");
  }

  private static void addResult(TextWindow resultsWindow, String name, int channel, double[] stats,
      int manual, int[] thresholds, int[] histogram) {
    // The threshold levels is expressed as:
    // Absolute
    // Fraction of area
    // Fraction of intensity

    // Build a cumulative histogram for the area and intensity
    final int min = (int) stats[0];
    final int max = (int) stats[1];
    final double[] area = new double[max + 1];
    final double[] intensity = new double[area.length];

    // Build the cumulative to represent the total below that value
    double count = 0;
    double sum = 0;
    for (int i = min; i < area.length; i++) {
      area[i] = count;
      intensity[i] = sum;
      count += histogram[i];
      sum += histogram[i] * i;
    }
    // Normalise so that the numbers represent the fraction at the threshold or above
    if (count != 0) {
      for (int i = min; i < area.length; i++) {
        area[i] = (count - area[i]) / count;
        intensity[i] = (sum - intensity[i]) / sum;
      }
    }

    final StringBuilder sb = new StringBuilder();
    sb.append(name).append('\t').append(channel);
    for (int i = 0; i < stats.length; i++) {
      sb.append('\t').append(IJ.d2s(stats[i]));
    }
    addResult(sb, manual, area, intensity);
    for (final int t : thresholds) {
      addResult(sb, t, area, intensity);
    }
    resultsWindow.append(sb.toString());
  }

  private static void addResult(StringBuilder sb, int threshold, double[] area,
      double[] intensity) {
    sb.append('\t').append(threshold);
    sb.append('\t').append(IJ.d2s(area[threshold], 5));
    sb.append('\t').append(IJ.d2s(intensity[threshold], 5));
  }
}
