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

package uk.ac.sussex.gdsc.colocalisation;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.text.TextWindow;
import java.awt.Color;
import java.awt.Point;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.ij.BufferedTextWindow;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.foci.ObjectAnalyzer3D;

/**
 * For all particles in a mask (defined by their unique pixel value), sum the pixel intensity in two
 * different images and then perform a correlation analysis.
 */
public class ParticleCorrelation_PlugIn implements PlugIn {
  private static final String TITLE = "Particle Correlation";

  private static AtomicReference<TextWindow> twSummaryRef = new AtomicReference<>();
  private static AtomicReference<TextWindow> twDataTableRef = new AtomicReference<>();

  private TextWindow twSummary;
  private TextWindow twDataTable;

  private ImagePlus maskImp;
  private ImagePlus imageImp1;
  private ImagePlus imageImp2;
  private int c1;
  private int c2;
  /** The plugin settings. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    String maskTitle1 = "";
    String imageTitle1 = "";
    String imageTitle2 = "";
    int imageC1 = 1;
    int imageC2 = 2;
    boolean eightConnected;
    int minSize;
    boolean showDataTable = true;
    boolean showPlot;
    boolean showObjects;

    Settings() {
      maskTitle1 = "";
      imageTitle1 = "";
      imageTitle2 = "";
      imageC1 = 1;
      imageC2 = 2;
      showDataTable = true;
    }

    Settings(Settings source) {
      maskTitle1 = source.maskTitle1;
      imageTitle1 = source.imageTitle1;
      imageTitle2 = source.imageTitle2;
      imageC1 = source.imageC1;
      imageC2 = source.imageC2;
      eightConnected = source.eightConnected;
      minSize = source.minSize;
      showDataTable = source.showDataTable;
      showPlot = source.showPlot;
      showObjects = source.showObjects;
    }

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

  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    if (WindowManager.getImageCount() == 0) {
      IJ.showMessage(TITLE, "No images opened.");
      return;
    }

    if (!showDialog()) {
      return;
    }

    analyse();
  }

  private boolean showDialog() {
    GenericDialog gd = new GenericDialog(TITLE);

    gd.addMessage("For each particle in a mask (defined by unique pixel value)\n"
        + "sum the pixel intensity in two different images and correlate");

    final String[] imageList = ImageJUtils.getImageList(ImageJUtils.GREY_SCALE, null);
    final String[] maskList = ImageJUtils.getImageList(ImageJUtils.GREY_8_16, null);

    settings = Settings.load();
    gd.addChoice("Particle_mask", maskList, settings.maskTitle1);
    gd.addChoice("Particle_image1", imageList, settings.imageTitle1);
    gd.addChoice("Particle_image2", imageList, settings.imageTitle2);
    gd.addCheckbox("Eight_connected", settings.eightConnected);
    gd.addNumericField("Min_object_size", settings.minSize, 0);
    gd.addCheckbox("Show_data_table", settings.showDataTable);
    gd.addCheckbox("Show_plot", settings.showPlot);
    gd.addCheckbox("Show_objects", settings.showObjects);
    gd.showDialog();

    if (gd.wasCanceled()) {
      return false;
    }

    settings.maskTitle1 = gd.getNextChoice();
    settings.imageTitle1 = gd.getNextChoice();
    settings.imageTitle2 = gd.getNextChoice();
    settings.eightConnected = gd.getNextBoolean();
    settings.minSize = (int) gd.getNextNumber();
    settings.showDataTable = gd.getNextBoolean();
    settings.showPlot = gd.getNextBoolean();
    settings.showObjects = gd.getNextBoolean();
    settings.save();

    maskImp = WindowManager.getImage(settings.maskTitle1);
    imageImp1 = WindowManager.getImage(settings.imageTitle1);
    imageImp2 = WindowManager.getImage(settings.imageTitle2);

    if (maskImp == null) {
      IJ.error(TITLE, "No particle mask specified");
      return false;
    }
    if (!checkDimensions(maskImp, imageImp1)) {
      IJ.error(TITLE, "Particle image 1 must match the dimensions of the particle mask");
      return false;
    }
    if (!checkDimensions(maskImp, imageImp2)) {
      IJ.error(TITLE, "Particle image 2 must match the dimensions of the particle mask");
      return false;
    }

    // We will use the current channel unless the two input images are the same image.
    if (settings.imageTitle1.equals(settings.imageTitle2)) {
      gd = new GenericDialog(TITLE);

      gd.addMessage("Same image detected for the two input\nSelect the channels for analysis");
      final int n = imageImp1.getNChannels();
      final String[] list = new String[n];
      for (int i = 0; i < n; i++) {
        list[i] = Integer.toString(i + 1);
      }

      c1 = Math.min(n, settings.imageC1);
      c2 = Math.min(n, settings.imageC2);
      gd.addChoice("Channel_1", list, list[c1 - 1]);
      gd.addChoice("Channel_2", list, list[c2 - 1]);
      gd.showDialog();

      if (gd.wasCanceled()) {
        return false;
      }

      c1 = settings.imageC1 = gd.getNextChoiceIndex() + 1;
      c2 = settings.imageC2 = gd.getNextChoiceIndex() + 1;
    } else {
      c1 = imageImp1.getChannel();
      c2 = imageImp2.getChannel();
    }

    return true;
  }

  private static boolean checkDimensions(ImagePlus imp1, ImagePlus imp2) {
    if (imp2 == null) {
      return false;
    }
    final int[] d1 = imp1.getDimensions();
    final int[] d2 = imp2.getDimensions();
    // Just check width, height, depth
    for (final int i : new int[] {0, 1, 3}) {
      if (d1[i] != d2[i]) {
        return false;
      }
    }
    return true;
  }

  private void analyse() {
    // Dimensions are the same. Extract a stack for each image.
    final int[] mask = extractStack(maskImp, maskImp.getChannel());
    final float[] i1 = extractFloatStack(imageImp1, c1);
    final float[] i2 = extractFloatStack(imageImp2, c2);

    final int maxx = maskImp.getWidth();
    final int maxy = maskImp.getHeight();
    final int maxz = maskImp.getNSlices();
    final ObjectAnalyzer3D oa =
        new ObjectAnalyzer3D(mask, maxx, maxy, maxz, settings.eightConnected);
    oa.setMinObjectSize(settings.minSize);
    final int[] objectMask = oa.getObjectMask();

    final int[] count = new int[oa.getMaxObject()];
    final double[] sumx = new double[count.length];
    final double[] sumy = new double[count.length];
    final double[] sumz = new double[count.length];
    final double[] sum1 = new double[count.length];
    final double[] sum2 = new double[count.length];
    for (int z = 0, i = 0; z < maxz; z++) {
      for (int y = 0; y < maxy; y++) {
        for (int x = 0; x < maxx; x++, i++) {
          int value = objectMask[i];
          if (value != 0) {
            value--;
            sumx[value] += x;
            sumy[value] += y;
            sumz[value] += z;
            sum1[value] += i1[i];
            sum2[value] += i2[i];
            count[value]++;
          }
        }
      }
    }

    // Summarise results
    createResultsTable();

    final String title = createTitle();

    addSummary(title, sum1, sum2);

    if (settings.showDataTable) {
      try (final BufferedTextWindow output = new BufferedTextWindow(twDataTable)) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count.length; i++) {
          final double x = sumx[i] / count[i];
          final double y = sumy[i] / count[i];
          // Centre on the slice
          final double z = 1 + sumz[i] / count[i];
          addResult(output::append, sb, title, i + 1, getValue(mask, objectMask, i + 1), x, y, z,
              count[i], sum1[i], sum2[i]);
        }
      }
    }

    if (settings.showPlot) {
      final String plotTitle = TITLE + " Plot";
      final Plot plot = new Plot(plotTitle, getName(imageImp1, c1), getName(imageImp2, c2));
      final double[] limitsx = MathUtils.limits(sum1);
      final double[] limitsy = MathUtils.limits(sum2);
      final double dx = (limitsx[1] - limitsx[0]) * 0.05;
      final double dy = (limitsy[1] - limitsy[0]) * 0.05;
      plot.setLimits(limitsx[0] - dx, limitsx[1] + dx, limitsy[0] - dy, limitsy[1] + dy);
      plot.setColor(Color.red);
      plot.addPoints(sum1, sum2, Plot.CROSS);
      ImageJUtils.display(plotTitle, plot);
    }

    if (settings.showObjects) {
      final ImageStack stack = new ImageStack(maxx, maxy, maxz);
      for (int z = 0, i = 0; z < maxz; z++) {
        final ImageProcessor ip = (oa.getMaxObject() < 256) ? new ByteProcessor(maxx, maxy)
            : new ShortProcessor(maxx, maxy);
        for (int y = 0, index = 0; y < maxy; y++) {
          for (int x = 0; x < maxx; x++, i++, index++) {
            final int value = objectMask[i];
            if (value != 0) {
              ip.set(index, value);
            }
          }
        }
        stack.setProcessor(ip, z + 1);
      }
      final ImagePlus imp = ImageJUtils.display(TITLE + " Objects", stack);
      if (oa.getMaxObject() < 256) {
        imp.setDisplayRange(0, oa.getMaxObject());
        imp.updateAndDraw();
      }
    }
  }

  private static int[] extractStack(ImagePlus imp, int channel) {
    final ImageStack stack = imp.getImageStack();
    final int frame = imp.getFrame();
    final int size = imp.getNSlices();
    final int[] image = new int[imp.getWidth() * imp.getHeight() * size];
    for (int slice = 1, i = 0; slice <= size; slice++) {
      final int stackIndex = imp.getStackIndex(channel, slice, frame);
      final ImageProcessor ip = stack.getProcessor(stackIndex);
      for (int index = 0; index < ip.getPixelCount(); index++) {
        image[i++] = ip.get(index);
      }
    }
    return image;
  }

  private static float[] extractFloatStack(ImagePlus imp, int channel) {
    final ImageStack stack = imp.getImageStack();
    final int frame = imp.getFrame();
    final int size = imp.getNSlices();
    final float[] image = new float[imp.getWidth() * imp.getHeight() * size];
    for (int slice = 1, i = 0; slice <= size; slice++) {
      final int stackIndex = imp.getStackIndex(channel, slice, frame);
      final ImageProcessor ip = stack.getProcessor(stackIndex);
      for (int index = 0; index < ip.getPixelCount(); index++) {
        image[i++] = ip.getf(index);
      }
    }
    return image;
  }

  private void createResultsTable() {
    twSummary = ImageJUtils.refresh(twSummaryRef, () -> new TextWindow(TITLE + " Summary",
        "Mask\tImage1\tImage2\tnParticles\tSpearman\tpSpearman\tR\tpR", "", 700, 250));
    if (!settings.showDataTable) {
      return;
    }
    twDataTable = ImageJUtils.refresh(twDataTableRef, () -> {
      final TextWindow window = new TextWindow(TITLE + " Results",
          "Mask\tImage1\tImage2\tID\tValue\tx\ty\tz\tN\tSum1\tSum2", "", 700, 500);
      // Position relative to summary
      final Point p = twSummary.getLocation();
      window.setLocation(p.x, p.y + twSummary.getHeight());
      return window;
    });
  }

  private String createTitle() {
    final StringBuilder sb = new StringBuilder();
    sb.append(getName(maskImp, maskImp.getChannel())).append('\t');
    sb.append(getName(imageImp1, c1)).append('\t');
    sb.append(getName(imageImp2, c2)).append('\t');
    return sb.toString();
  }

  private static String getName(ImagePlus imp, int channel) {
    String name = imp.getTitle();
    int suffix = 0;
    final String[] prefix = {" [", ","};
    if (imp.getNChannels() > 1) {
      name += prefix[suffix++] + "C=" + channel;
    }
    if (imp.getNFrames() > 1) {
      name += prefix[suffix++] + "T=" + imp.getFrame();
    }
    if (suffix > 0) {
      name += "]";
    }
    return name;
  }

  private static int getValue(int[] mask, int[] objectMask, int value) {
    for (int i = 0; i < objectMask.length; i++) {
      if (objectMask[i] == value) {
        return mask[i];
      }
    }
    return 0;
  }

  private void addSummary(String title, double[] sum1, double[] sum2) {
    final BlockRealMatrix rm = new BlockRealMatrix(sum1.length, 2);
    rm.setColumn(0, sum1);
    rm.setColumn(1, sum2);

    final SpearmansCorrelation sr = new SpearmansCorrelation(rm);
    final PearsonsCorrelation p1 = sr.getRankCorrelation();
    final PearsonsCorrelation p2 = new PearsonsCorrelation(rm);

    final StringBuilder sb = new StringBuilder(title);
    sb.append(sum1.length).append('\t');
    sb.append(MathUtils.rounded(p1.getCorrelationMatrix().getEntry(0, 1))).append('\t');
    sb.append(MathUtils.rounded(p1.getCorrelationPValues().getEntry(0, 1))).append('\t');
    sb.append(MathUtils.rounded(p2.getCorrelationMatrix().getEntry(0, 1))).append('\t');
    sb.append(MathUtils.rounded(p2.getCorrelationPValues().getEntry(0, 1)));
    twSummary.append(sb.toString());
  }

  private static void addResult(Consumer<String> output, StringBuilder sb, String title, int id,
      int value, double x, double y, double z, int count, double s1, double s2) {
    sb.setLength(0);
    sb.append(title);
    sb.append(id).append('\t');
    sb.append(value).append('\t');
    sb.append(MathUtils.rounded(x)).append('\t');
    sb.append(MathUtils.rounded(y)).append('\t');
    sb.append(MathUtils.rounded(z)).append('\t');
    sb.append(count).append('\t');
    sb.append(s1).append('\t');
    sb.append(s2);
    output.accept(sb.toString());
  }
}
