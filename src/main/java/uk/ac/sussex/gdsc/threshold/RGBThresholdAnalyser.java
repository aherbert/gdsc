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
package uk.ac.sussex.gdsc.threshold;

import java.io.File;
import java.io.FilenameFilter;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.text.TextWindow;
import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold;

/**
 * For all the RGB images in one directory, look for a matching image in a second directory that has
 * 3 channels. Compute the minimum value (i.e. the threshold) for pixels within the red and green
 * channel of the RGB image. Then run thresholding methods on the pixels within the mask region
 * defined by channel 3 and output a table of results.
 */
public class RGBThresholdAnalyser implements PlugIn {
  private static String TITLE = "RGB Threshold Analyser";
  private static TextWindow resultsWindow = null;

  private static String dir1 = "", dir2 = "";

  /** {@inheritDoc} */
  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    dir1 = ImageJUtils.getDirectory("RGB_Directory", dir1);
    if (dir1 == null) {
      return;
    }
    dir2 = ImageJUtils.getDirectory("Image_Directory", dir2);
    if (dir2 == null) {
      return;
    }

    final File[] fileList = (new File(dir1)).listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        name = name.toLowerCase();
        return name.endsWith(".tif") || name.endsWith(".tiff");
      }
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
      final String path = dir2 + name;
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

      createResultsWindow();

      // Extract the 3 channels
      final ColorProcessor cp = (ColorProcessor) imp.getProcessor();
      final ImageProcessor ip1 = getProcessor(imp2, 1);
      final ImageProcessor ip2 = getProcessor(imp2, 2);
      final ImageProcessor ip3 = getProcessor(imp2, 3);

      analyse(name, cp, 1, ip1, ip3);
      analyse(name, cp, 2, ip2, ip3);

      if (ImageJUtils.isInterrupted()) {
        return;
      }
    }

    IJ.showStatus(TITLE + " Finished");
  }

  private static void analyse(String name, ColorProcessor cp, int channel, ImageProcessor ip,
      ImageProcessor maskIp) {
    final byte[] mask = cp.getChannel(channel);

    // Utils.display("RGB Channel " + channel, new ByteProcessor(ip.getWidth(), ip.getHeight(),
    // mask));
    // Utils.display("Channel " + channel, ip);

    // Get the histogram for the channel
    final int[] h = new int[(ip instanceof ByteProcessor) ? 256 : 65336];

    // Get the manual threshold within the color channel mask
    int manual = Integer.MAX_VALUE;
    for (int i = 0; i < mask.length; i++) {
      if (maskIp.get(i) != 0) {
        h[ip.get(i)]++;

        if (mask[i] != 0) {
          if (manual > ip.get(i)) {
            manual = ip.get(i);
          }
        }
      }
    }

    // Check the threshold is valid
    // ImageProcessor ep = ip.createProcessor(ip.getWidth(), ip.getHeight());
    int error = 0;
    long sum = 0;
    for (int i = 0; i < mask.length; i++) {
      if (maskIp.get(i) != 0) {
        if (mask[i] == 0 && ip.get(i) >= manual) {
          error++;
          sum += ip.get(i) - manual;
          // ep.set(i, ip.get(i));
        }
      }
    }
    if (error != 0) {
      System.out.printf("%s [%d] %d error pixels (sum = %d)\n", name, channel, error, sum);
      // Utils.display("Error ch "+channel, ep);
    }

    final double[] stats = getStatistics(h);

    final String[] methods = AutoThreshold.getMethods(true);
    final int[] thresholds = new int[methods.length];
    for (int i = 0; i < thresholds.length; i++) {
      thresholds[i] = AutoThreshold.getThreshold(methods[i], h);
    }

    addResult(name, channel, stats, manual, thresholds, h);
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
    long n = 0;
    for (int i = min; i <= max; i++) {
      if (hist[i] > 0) {
        count = hist[i];
        n += count;
        value = i;
        sum += value * count;
        sum2 += (value * value) * count;
      }
    }
    final double av = sum / n;

    // Get the Std.Dev
    double stdDev;
    if (n > 0) {
      final double d = n;
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

  private static void createResultsWindow() {
    if (resultsWindow == null || !resultsWindow.isShowing()) {
      resultsWindow = new TextWindow(TITLE + " Results", createResultsHeader(), "", 900, 500);
    }
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

  private static void addResult(String name, int channel, double[] stats, int manual,
      int[] thresholds, int[] h) {
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
    double count = 0, sum = 0;
    for (int i = min; i < area.length; i++) {
      area[i] = count;
      intensity[i] = sum;
      count += h[i];
      sum += h[i] * i;
    }
    // Normalise so that the numbers represent the fraction at the threshold or above
    for (int i = min; i < area.length; i++) {
      area[i] = (count - area[i]) / count;
      intensity[i] = (sum - intensity[i]) / sum;
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

  private static void addResult(StringBuilder sb, int t, double[] area, double[] intensity) {
    sb.append('\t').append(t);
    sb.append('\t').append(IJ.d2s(area[t], 5));
    sb.append('\t').append(IJ.d2s(intensity[t], 5));
  }

}
