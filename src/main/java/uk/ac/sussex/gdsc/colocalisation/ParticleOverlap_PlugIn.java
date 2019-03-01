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

import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.utils.FileUtils;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.core.utils.TextUtils;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * For all particles in a mask (defined by their unique pixel value), count the overlap with a
 * second mask image. An intensity image is used to calculate the Manders coefficient of each
 * particle.
 */
public class ParticleOverlap_PlugIn implements PlugIn {
  private static final String TITLE = "Particle Overlap";
  private static final String HEADER = "Mask1\tImage1\tMask2\tID\tN\tNo\t%\tI\tIo\tManders";

  private static AtomicReference<TextWindow> textWindowRef = new AtomicReference<>();

  private ImagePlus mask1Imp;
  private ImagePlus imageImp;
  private ImagePlus mask2Imp;
  private BufferedWriter out;

  /** The plugin settings. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    String maskTitle1;
    String imageTitle;
    String maskTitle2;
    boolean showTotal;
    boolean showTable;
    String filename;

    Settings() {
      maskTitle1 = "";
      imageTitle = "";
      maskTitle2 = "";
      showTable = true;
      filename = "";
    }

    Settings(Settings source) {
      maskTitle1 = source.maskTitle1;
      imageTitle = source.imageTitle;
      maskTitle2 = source.maskTitle2;
      showTotal = source.showTotal;
      showTable = source.showTable;
      filename = source.filename;
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
    final GenericDialog gd = new GenericDialog(TITLE);

    gd.addMessage("For each particle in a mask (defined by unique pixel value)\n"
        + "count the overlap and Manders coefficient with a second mask image");

    final String[] imageList = ImageJUtils.getImageList(ImageJUtils.GREY_SCALE, null);
    final String[] maskList = ImageJUtils.getImageList(ImageJUtils.GREY_8_16, null);

    settings = Settings.load();
    gd.addChoice("Particle_mask", maskList, settings.maskTitle1);
    gd.addChoice("Particle_image", imageList, settings.imageTitle);
    gd.addChoice("Overlap_mask", maskList, settings.maskTitle2);
    gd.addCheckbox("Show_total", settings.showTotal);
    gd.addCheckbox("Show_table", settings.showTable);
    gd.addStringField("File", settings.filename, 30);
    gd.showDialog();

    if (gd.wasCanceled()) {
      return false;
    }

    settings.maskTitle1 = gd.getNextChoice();
    settings.imageTitle = gd.getNextChoice();
    settings.maskTitle2 = gd.getNextChoice();
    settings.showTotal = gd.getNextBoolean();
    settings.showTable = gd.getNextBoolean();
    settings.filename = gd.getNextString();
    settings.save();

    mask1Imp = WindowManager.getImage(settings.maskTitle1);
    imageImp = WindowManager.getImage(settings.imageTitle);
    mask2Imp = WindowManager.getImage(settings.maskTitle2);

    if (mask1Imp == null) {
      IJ.error(TITLE, "No particle mask specified");
      return false;
    }
    if (!checkDimensions(mask1Imp, imageImp)) {
      IJ.error(TITLE, "Particle image must match the dimensions of the particle mask");
      return false;
    }
    if (!checkDimensions(mask1Imp, mask2Imp)) {
      IJ.error(TITLE, "Overlap mask must match the dimensions of the particle mask");
      return false;
    }
    if (!settings.showTable && emptyFilename()) {
      IJ.error(TITLE, "No output specified");
      return false;
    }

    return true;
  }

  private boolean emptyFilename() {
    return TextUtils.isNullOrEmpty(settings.filename);
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
    final ImageStack m1 = extractStack(mask1Imp);
    final ImageStack m2 = extractStack(mask2Imp);
    final ImageStack i1 = extractStack(imageImp);

    // Count the pixels in the particle mask
    final int[] n1 = new int[(mask1Imp.getBitDepth() == 8) ? 256 : 65536];
    // Count the pixels in the particle mask that overlap
    final int[] no1 = new int[n1.length];
    // Sum the pixels in the particle image
    final double[] s1 = new double[n1.length];
    // Sum the pixels in the particle image that overlap
    final double[] so1 = new double[n1.length];

    final int size = m1.getWidth() * m1.getHeight();
    for (int n = 1; n <= m1.getSize(); n++) {
      final ImageProcessor mask1 = m1.getProcessor(n);
      final ImageProcessor mask2 = m2.getProcessor(n);
      final ImageProcessor image1 = i1.getProcessor(n);

      for (int i = 0; i < size; i++) {
        final int p1 = mask1.get(i);
        if (p1 != 0) {
          final int p2 = mask2.get(i);
          final float v1 = image1.getf(i);
          n1[p1]++;
          s1[p1] += v1;
          if (p2 != 0) {
            no1[p1]++;
            so1[p1] += v1;
          }
        }
      }
    }

    // Summarise results
    final TextWindow textWindow = createResultsTable();
    createResultsFile();

    final String title = createTitle();
    long sn1 = 0;
    long sno1 = 0;
    double ss1 = 0;
    double sso1 = 0;
    for (int id = 1; id < n1.length; id++) {
      if (n1[id] == 0) {
        continue;
      }
      sn1 += n1[id];
      sno1 += no1[id];
      ss1 += s1[id];
      sso1 += so1[id];
      addResult(textWindow, title, id, n1[id], no1[id], s1[id], so1[id]);
    }
    if (settings.showTotal) {
      addResult(textWindow, title, 0, sn1, sno1, ss1, sso1);
    }

    closeResultsFile();
  }

  private static ImageStack extractStack(ImagePlus imp) {
    final ImageStack oldStack = imp.getImageStack();
    final int channel = imp.getChannel();
    final int frame = imp.getFrame();
    final int size = imp.getNSlices();
    final ImageStack stack = new ImageStack(imp.getWidth(), imp.getHeight(), size);
    for (int slice = 1; slice <= size; slice++) {
      final int index = imp.getStackIndex(channel, slice, frame);
      stack.setPixels(oldStack.getPixels(index), slice);
    }
    return stack;
  }

  private TextWindow createResultsTable() {
    if (!settings.showTable) {
      return null;
    }
    return ImageJUtils.refresh(textWindowRef,
        () -> new TextWindow(TITLE + " Results", HEADER, "", 800, 500));
  }

  private void createResultsFile() {
    if (emptyFilename()) {
      return;
    }

    try {
      out = Files
          .newBufferedWriter(Paths.get(FileUtils.addExtensionIfAbsent(settings.filename, ".xls")));
    } catch (final IOException ex) {
      logErrorAndCloseResultsFile(ex);
      return;
    }

    try {
      out.write(HEADER);
      out.newLine();
    } catch (final IOException ex) {
      logErrorAndCloseResultsFile(ex);
    }
  }

  private String createTitle() {
    final StringBuilder sb = new StringBuilder();
    sb.append(getName(mask1Imp)).append('\t');
    sb.append(getName(imageImp)).append('\t');
    sb.append(getName(mask2Imp)).append('\t');
    return sb.toString();
  }

  private static String getName(ImagePlus imp) {
    String name = imp.getTitle();
    int suffix = 0;
    final String[] prefix = {" [", ","};
    if (imp.getNChannels() > 1) {
      name += prefix[suffix++] + "C=" + imp.getChannel();
    }
    if (imp.getNFrames() > 1) {
      name += prefix[suffix++] + "T=" + imp.getFrame();
    }
    if (suffix > 0) {
      name += "]";
    }
    return name;
  }

  private void addResult(TextWindow textWindow, String title, int id, long n1, long no1, double s1,
      double so1) {
    final StringBuilder sb = new StringBuilder(title);
    sb.append(id).append('\t');
    sb.append(n1).append('\t');
    sb.append(no1).append('\t');
    sb.append(MathUtils.rounded(MathUtils.div0(100.0 * no1, n1))).append('\t');
    sb.append(s1).append('\t');
    sb.append(so1).append('\t');
    sb.append(MathUtils.rounded(MathUtils.div0(so1, s1), 5)).append('\t');

    recordResult(textWindow, sb.toString());
  }

  private void recordResult(TextWindow textWindow, String string) {
    if (textWindow != null) {
      textWindow.append(string);
    }
    if (out != null) {
      try {
        out.write(string);
        out.newLine();
      } catch (final IOException ex) {
        logErrorAndCloseResultsFile(ex);
      }
    }
  }

  private void logErrorAndCloseResultsFile(Exception ex) {
    if (ex != null) {
      Logger.getLogger(getClass().getName()).log(Level.WARNING, "Error writing to the result file",
          ex);
    }
    closeResultsFile();
  }

  private void closeResultsFile() {
    if (out != null) {
      try {
        out.close();
      } catch (final IOException innerEx) {
        // Ignore
      } finally {
        out = null;
      }
    }
  }
}
