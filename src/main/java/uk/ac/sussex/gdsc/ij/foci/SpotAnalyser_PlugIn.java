/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2022 Alex Herbert
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

package uk.ac.sussex.gdsc.ij.foci;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.text.TextWindow;
import java.awt.AWTEvent;
import java.awt.Label;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.commons.math3.exception.NumberIsTooSmallException;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.inference.TestUtils;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold;
import uk.ac.sussex.gdsc.ij.UsageTracker;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.BackgroundMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.CentreMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.MaskMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.PeakMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.SearchMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.SortMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.StatisticsMethod;

/**
 * Analyses the intensity of each channel within the brightest spot of the selected channel.
 *
 * <p>Identifies cells using thresholding. For each cell the region of the brightest spot is
 * identified. The intensity of each channel inside and outside the spot are then compared.
 */
public class SpotAnalyser_PlugIn implements ExtendedPlugInFilter, DialogListener {
  private static final String TITLE = "Spot Analyser";
  private static String[] maskOptions = new String[] {"Threshold", "Use ROI"};

  private static final int INSIDE = 0;
  private static final int OUTSIDE = 1;

  private static final int FLAGS = DOES_16 + DOES_8G + SNAPSHOT + FINAL_PROCESSING;
  private ImagePlus imp;
  private ImageProcessor maskIp;
  private Label label;
  private double min;
  private double max;
  private int spotChannel;
  private int thresholdChannel;
  private int noOfParticles;
  private boolean containsRoiMask;

  private static AtomicReference<TextWindow> results = new AtomicReference<>();
  private static AtomicBoolean writeHeader = new AtomicBoolean(true);

  /** The current settings for the plugin instance. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    int maskOption;
    double blur = 3;
    String thresholdMethod = AutoThreshold.Method.OTSU.toString();
    double minSize = 50;
    boolean showParticles;
    int maxPeaks = 1;
    double fraction = 0.9;
    boolean showSpots;

    /**
     * Default constructor.
     */
    Settings() {
      // Do nothing
    }

    /**
     * Copy constructor.
     *
     * @param source the source
     */
    private Settings(Settings source) {
      maskOption = source.maskOption;
      blur = source.blur;
      thresholdMethod = source.thresholdMethod;
      minSize = source.minSize;
      showParticles = source.showParticles;
      maxPeaks = source.maxPeaks;
      fraction = source.fraction;
      showSpots = source.showSpots;
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
  public int setup(String arg, ImagePlus imp) {
    if ("final".equals(arg)) {
      analyseImage();
      return DONE;
    }

    UsageTracker.recordPlugin(this.getClass(), arg);

    if (imp == null) {
      IJ.noImage();
      return DONE;
    }
    this.imp = imp;
    final ImageProcessor ip = imp.getProcessor();
    min = ip.getMin();
    max = ip.getMax();
    return FLAGS;
  }

  private void resetImage() {
    final ImageProcessor ip = imp.getProcessor();
    ip.reset();
    ip.setMinAndMax(min, max);
    imp.updateAndDraw();
  }

  /** {@inheritDoc} */
  @Override
  public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
    settings = Settings.load();

    final GenericDialog gd = new GenericDialog(TITLE);

    spotChannel = imp.getChannel();
    thresholdChannel = spotChannel;
    final Roi roi = imp.getRoi();
    if (roi != null && roi.isArea()) {
      containsRoiMask = true;
      gd.addChoice("Threshold_Channel", maskOptions, maskOptions[settings.maskOption]);

      // Set up the mask using the ROI
      maskIp = new ByteProcessor(imp.getWidth(), imp.getHeight());
      maskIp.setColor(255);
      maskIp.fill(roi);
      maskIp.setThreshold(0, 254, ImageProcessor.NO_LUT_UPDATE);
    }

    final String[] channels = new String[imp.getNChannels()];
    for (int i = 0; i < channels.length; i++) {
      channels[i] = Integer.toString(i + 1);
    }

    gd.addChoice("Threshold_Channel", channels, channels[thresholdChannel - 1]);
    gd.addSlider("Blur", 0.01, 5, settings.blur);
    gd.addChoice("Threshold_method", AutoThreshold.getMethods(), settings.thresholdMethod);
    gd.addChoice("Spot_Channel", channels, channels[spotChannel - 1]);
    gd.addSlider("Min_size", 50, 10000, settings.minSize);
    gd.addCheckbox("Show_particles", settings.showParticles);
    gd.addSlider("Max_peaks", 1, 10, settings.maxPeaks);
    gd.addSlider("Fraction", 0.01, 1, settings.fraction);
    gd.addCheckbox("Show_spots", settings.showSpots);
    gd.addMessage("");
    label = (Label) gd.getMessage();

    gd.addHelp(uk.ac.sussex.gdsc.ij.help.Urls.UTILITY);
    gd.addPreviewCheckbox(pfr);
    gd.addDialogListener(this);
    gd.showDialog();

    final boolean cancelled = gd.wasCanceled() || !dialogItemChanged(gd, null);
    settings.save();
    if (cancelled) {
      return DONE;
    }

    return FLAGS;
  }

  /** {@inheritDoc} */
  @Override
  public boolean dialogItemChanged(GenericDialog gd, AWTEvent event) {
    if (containsRoiMask) {
      settings.maskOption = gd.getNextChoiceIndex();
    }
    thresholdChannel = gd.getNextChoiceIndex() + 1;
    settings.blur = gd.getNextNumber();
    if (gd.invalidNumber()) {
      return false;
    }
    settings.thresholdMethod = gd.getNextChoice();
    spotChannel = gd.getNextChoiceIndex() + 1;
    settings.minSize = gd.getNextNumber();
    if (gd.invalidNumber()) {
      settings.minSize = 50;
    }
    settings.showParticles = gd.getNextBoolean();
    settings.maxPeaks = (int) gd.getNextNumber();
    if (gd.invalidNumber() || settings.maxPeaks < 1) {
      settings.maxPeaks = 1;
    }
    settings.fraction = gd.getNextNumber();
    if (gd.invalidNumber()) {
      settings.fraction = 0.9;
    }
    settings.showSpots = gd.getNextBoolean();

    // Preview checkbox will be null if running headless
    if (gd.getPreviewCheckbox() != null && !gd.getPreviewCheckbox().getState()) {
      // Reset preview
      label.setText("");
      resetImage();
    }

    return true;
  }

  /** {@inheritDoc} */
  @Override
  public void setNPasses(int passes) {
    // Do nothing
  }

  /** {@inheritDoc} */
  @Override
  public void run(ImageProcessor inputIp) {
    // Just run the particle analyser
    noOfParticles = 0;

    // Avoid destructively modifying the image. Work on the selected channel for thresholding
    final int index = imp.getStackIndex(thresholdChannel, imp.getSlice(), imp.getFrame());
    ImageProcessor ip = imp.getImageStack().getProcessor(index).duplicate();

    if (!checkData(ip)) {
      IJ.error(TITLE, "Channel has no data range");
      resetImage();
      return;
    }

    if (containsRoiMask && settings.maskOption == 1) {
      ip = maskIp.duplicate();
      ip.setThreshold(254, 255, ImageProcessor.NO_LUT_UPDATE);
    } else {
      // Blur the image
      if (settings.blur > 0) {
        final GaussianBlur gb = new GaussianBlur();
        gb.blurGaussian(ip, settings.blur, settings.blur, 0.002);
      }

      // Threshold
      final int t = AutoThreshold.getThreshold(settings.thresholdMethod, ip.getHistogram());
      ip.setThreshold(t, 65536, ImageProcessor.NO_LUT_UPDATE);
    }

    // Analyse particles
    final ImagePlus particlesImp = new ImagePlus(imp.getTitle() + " Particles", ip);
    final int analyserOptions = ParticleAnalyzer.SHOW_ROI_MASKS + ParticleAnalyzer.IN_SITU_SHOW
        + ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES;
    final int measurements = 0;
    final ResultsTable rt = new ResultsTable();

    final double maxSize = Double.POSITIVE_INFINITY;
    final ParticleAnalyzer pa =
        new ParticleAnalyzer(analyserOptions, measurements, rt, settings.minSize, maxSize);
    pa.analyze(particlesImp, ip);

    final ImageProcessor particlesIp = particlesImp.getProcessor();
    noOfParticles = rt.getCounter();
    if (label != null) {
      label.setText(String.format("%d particle%s", noOfParticles, (noOfParticles == 1) ? "" : "s"));
    }

    // Show the particles
    inputIp.copyBits(particlesIp, 0, 0, Blitter.COPY);
    inputIp.setMinAndMax(0, noOfParticles);
    imp.updateAndDraw();
  }

  private static boolean checkData(ImageProcessor ip) {
    final int firstValue = ip.get(0);
    for (int i = 1; i < ip.getPixelCount(); i++) {
      if (ip.get(i) != firstValue) {
        return true;
      }
    }
    return false;
  }

  private void analyseImage() {

    if (noOfParticles == 0) {
      resetImage(); // Restore the original image
      return;
    }

    // The particles have already been identified in the run() method.
    // Copy the pixels.
    final ImageProcessor particlesIp = imp.getProcessor().duplicate();

    resetImage(); // Restore the original image

    // Get the channel for spot analysis
    final int stackIndex = imp.getStackIndex(spotChannel, imp.getSlice(), imp.getFrame());
    final ImageProcessor ip = imp.getImageStack().getProcessor(stackIndex);
    final ImagePlus tmpImp = new ImagePlus("Dummy", ip);

    if (settings.showParticles) {
      final ImageProcessor tmpIp = particlesIp.duplicate();
      tmpIp.setMinAndMax(0, noOfParticles);
      showImage(this.imp.getTitle() + " Particles", tmpIp);
    }

    final ImageProcessor spotsIp =
        particlesIp.createProcessor(particlesIp.getWidth(), particlesIp.getHeight());

    // For each particle:
    // Run FindFoci to find the single highest spot
    final FindFoci_PlugIn ff = new FindFoci_PlugIn();
    final FindFociProcessorOptions processorOptions = new FindFociProcessorOptions();
    processorOptions.setBackgroundMethod(BackgroundMethod.MEAN);
    processorOptions.setBackgroundParameter(0);
    processorOptions.setSearchMethod(SearchMethod.ABOVE_BACKGROUND);
    processorOptions.setMaxPeaks(33000);
    processorOptions.setMinSize(5);
    processorOptions.setPeakMethod(PeakMethod.RELATIVE_ABOVE_BACKGROUND);
    processorOptions.setPeakParameter(0.5);
    processorOptions.getOptions().clear();
    processorOptions.setMaskMethod(MaskMethod.FRACTION_OF_HEIGHT);
    processorOptions.setSortMethod(SortMethod.MAX_VALUE);
    processorOptions.setStatisticsMethod(StatisticsMethod.INSIDE);
    processorOptions.setGaussianBlur(0);
    processorOptions.setCentreMethod(CentreMethod.MAX_VALUE_ORIGINAL);
    processorOptions.setFractionParameter(settings.fraction);

    for (int particle = 1; particle <= noOfParticles; particle++) {
      final ImagePlus mask = createMask(particlesIp, particle);
      final FindFociResults result =
          ff.createFindFociProcessor(tmpImp).findMaxima(tmpImp, mask, processorOptions);
      if (result == null) {
        continue;
      }

      final ImagePlus maximaImp = result.mask;
      final ImageProcessor spotIp = maximaImp.getProcessor();

      // Renumber to the correct particle value
      for (int j = 0; j < spotIp.getPixelCount(); j++) {
        if (spotIp.get(j) != 0) {
          spotIp.set(j, particle);
        }
      }
      spotsIp.copyBits(spotIp, 0, 0, Blitter.ADD);
    }

    if (settings.showSpots) {
      spotsIp.setMinAndMax(0, noOfParticles);
      showImage(this.imp.getTitle() + " Spots", spotsIp);
    }

    // Now we have:
    // particlesIp => Particles
    // spotsIp => The largest spot within each particle

    // Create a mask of the particles
    final byte[] mask = new byte[particlesIp.getPixelCount()];
    for (int i = 0; i < particlesIp.getPixelCount(); i++) {
      if (particlesIp.get(i) != 0) {
        mask[i] = 1;
      }
    }

    // Subtract the spots from the particles
    particlesIp.copyBits(spotsIp, 0, 0, Blitter.SUBTRACT);

    // Create a statistical summary for [channel][inside/outside][particle]
    final DescriptiveStatistics[][][] stats =
        new DescriptiveStatistics[imp.getNChannels() + 1][2][noOfParticles + 1];

    final ImageStack stack = imp.getImageStack();
    for (int channel = 1; channel <= imp.getNChannels(); channel++) {
      final int index = imp.getStackIndex(channel, imp.getSlice(), imp.getFrame());
      final ImageProcessor channelIp = stack.getProcessor(index);

      for (int particle = 0; particle <= noOfParticles; particle++) {
        stats[channel][INSIDE][particle] = new DescriptiveStatistics();
        stats[channel][OUTSIDE][particle] = new DescriptiveStatistics();
      }

      for (int i = 0; i < mask.length; i++) {
        if (mask[i] != 0) {
          final int v = channelIp.get(i);
          stats[channel][INSIDE][spotsIp.get(i)].addValue(v);
          stats[channel][OUTSIDE][particlesIp.get(i)].addValue(v);
        }
      }
    }

    final Consumer<String> output = createResultsWindow();

    // Add the counts inside and outside
    for (int particle = 1; particle <= noOfParticles; particle++) {
      // Just choose the first channel (all are the same)
      addResult(output, particle, stats[1][INSIDE][particle].getN(),
          stats[1][OUTSIDE][particle].getN());
    }

    // Add the statistics inside and outside for each channel
    for (int channel = 1; channel <= imp.getNChannels(); channel++) {
      for (int particle = 1; particle <= noOfParticles; particle++) {
        addResult(output, channel, particle, stats);
      }
    }
  }

  private static ImagePlus showImage(String title, ImageProcessor ip) {
    ImagePlus imp = WindowManager.getImage(title);
    if (imp == null) {
      imp = new ImagePlus(title, ip);
      imp.show();
    } else {
      imp.setProcessor(ip);
      imp.updateAndDraw();
    }
    return imp;
  }

  /**
   * Zero all pixels that are not the given value.
   *
   * @param ip the image
   * @param value the value
   * @return the new image
   */
  private static ImagePlus createMask(ImageProcessor ip, int value) {
    ip = ip.duplicate();
    for (int i = 0; i < ip.getPixelCount(); i++) {
      if (ip.get(i) != value) {
        ip.set(i, 0);
      }
    }
    return new ImagePlus("Mask", ip);
  }

  private static Consumer<String> createResultsWindow() {
    if (java.awt.GraphicsEnvironment.isHeadless()) {
      if (writeHeader.compareAndSet(true, false)) {
        IJ.log("Image\tChannel\tParticle\tInside Sum\tAv\tSD\tR\t"
            + "Outside Sum\tAv\tSD\tR\tIncrease\tp-value");
      }
      return IJ::log;
    }
    return ImageJUtils.refresh(results,
        () -> new TextWindow(TITLE + " Results", "Image\tChannel\tParticle\tInside Sum\tAv\tSD\tR\t"
            + "Outside Sum\tAv\tSD\tR\tIncrease\tp-value", "", 800, 600))::append;
  }

  private void addResult(Consumer<String> output, int particle, long countInside,
      long countOutside) {
    final StringBuilder sb = new StringBuilder();
    sb.append(imp.getTitle()).append("\t\t");
    sb.append(particle).append('\t');
    sb.append(countInside).append("\t\t\t\t");
    sb.append(countOutside).append("\t\t\t\t");
    output.accept(sb.toString());
  }

  private void addResult(Consumer<String> output, int channel, int particle,
      DescriptiveStatistics[][][] stats) {
    final StringBuilder sb = new StringBuilder();
    sb.append(imp.getTitle()).append('\t');
    sb.append(channel).append('\t');
    sb.append(particle).append('\t');

    final double sx = stats[channel][INSIDE][particle].getSum();
    final double sd = stats[channel][INSIDE][particle].getStandardDeviation();
    final long n = stats[channel][INSIDE][particle].getN();
    final double av = sx / n;
    final double sx2 = stats[channel][OUTSIDE][particle].getSum();
    final double sd2 = stats[channel][OUTSIDE][particle].getStandardDeviation();
    final long n2 = stats[channel][OUTSIDE][particle].getN();
    final double av2 = sx2 / n2;

    double pvalue = 0;
    try {
      pvalue = TestUtils.tTest(stats[channel][INSIDE][particle], stats[channel][OUTSIDE][particle]);
    } catch (final NumberIsTooSmallException ex) {
      // Ignore
    }

    // Correlate inside & outside spot with the principle channel
    double correlation1 = 1;
    double correlation2 = 1;
    if (channel != spotChannel) {
      // Principle channel => No test required
      correlation1 =
          new PearsonsCorrelation().correlation(stats[channel][INSIDE][particle].getValues(),
              stats[spotChannel][INSIDE][particle].getValues());
      correlation2 =
          new PearsonsCorrelation().correlation(stats[channel][OUTSIDE][particle].getValues(),
              stats[spotChannel][OUTSIDE][particle].getValues());
    }

    sb.append(IJ.d2s(sx, 0)).append('\t').append(IJ.d2s(av, 2)).append('\t').append(IJ.d2s(sd, 2))
        .append('\t').append(IJ.d2s(correlation1, 3)).append('\t');
    sb.append(IJ.d2s(sx2, 0)).append('\t').append(IJ.d2s(av2, 2)).append('\t')
        .append(IJ.d2s(sd2, 2)).append('\t').append(IJ.d2s(correlation2, 3)).append('\t');
    sb.append(IJ.d2s(av / av2, 2)).append('\t');
    sb.append(uk.ac.sussex.gdsc.core.utils.MathUtils.rounded(pvalue, 3));

    output.accept(sb.toString());
  }
}
