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

package uk.ac.sussex.gdsc.trackmate.detector;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.detection.DetectorKeys;
import fiji.plugin.trackmate.detection.SpotDetector;
import fiji.plugin.trackmate.detection.SpotDetectorFactory;
import fiji.plugin.trackmate.gui.ConfigurationPanel;
import fiji.plugin.trackmate.io.IOUtils;
import fiji.plugin.trackmate.util.TMUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.ImageIcon;
import net.imagej.ImgPlus;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import org.jdom2.Element;
import org.scijava.plugin.Plugin;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold;

/**
 * A factory for creating NucleusDetector objects.
 *
 * @param <T> the pixels type
 */
@Plugin(type = SpotDetectorFactory.class)
public class NucleusDetectorFactory<T extends RealType<T> & NativeType<T>>
    implements SpotDetectorFactory<T> {

  /** HTML information text. */
  // @formatter:off
  static final String INFO_TEXT =
    "<html><p>Detect nuclei.</p>"
    + "<ul>"
    + "<li>Filter the image (Gaussian blur).</li>"
    + "<li>Mask all the nuclei using thresholding.</li>"
    + "<li>Fill holes and filter outliers.</li>"
    + "<li>Remove small nuclei based on size.</li>"
    + "<li>Divide large nuclei based on size using watershed.</li>"
    + "</ul></html>";
  // @formatter:on

  /** The pretty name of the target detector. */
  static final String NAME = "Nucleus detector";

  /** A string key identifying this factory. */
  private static final String KEY = "NUCLEUS_DETECTOR";

  /** Setting key for analysis channel. */
  static final String SETTING_ANALYSIS_CHANNEL = "ANALYSIS_CHANNEL";
  /** Setting key for blur1. */
  static final String SETTING_BLUR1 = "BLUR1";
  /** Setting key for blur2. */
  static final String SETTING_BLUR2 = "BLUR2";
  /** Setting key for method. */
  static final String SETTING_METHOD = "METHOD";
  /** Setting key for outlier radius. */
  static final String SETTING_OUTLIER_RADIUS = "OUTLIER_RADIUS";
  /** Setting key for outlier threshold. */
  static final String SETTING_OUTLIER_THRESHOLD = "OUTLIER_THRESHOLD";
  /** Setting key for maximum nucleus size. */
  static final String SETTING_MAX_NUCLEUS_SIZE = "MAX_NUCLEUS_SIZE";
  /** Setting key for minimum nucleus size. */
  static final String SETTING_MIN_NUCLEUS_SIZE = "MIN_NUCLEUS_SIZE";
  /** Setting key for erosion to area inside the nucleus. */
  static final String SETTING_EROSION = "EROSION";
  /** Setting key for expansion to define the inner ring outside the nucleus. */
  static final String SETTING_EXPANSION_INNER = "EXPANSION_INNER";
  /** Setting key for expansion area outside the nucleus. */
  static final String SETTING_EXPANSION = "EXPANSION";

  /** The pixel sizes in the 3 dimensions. */
  private double[] calibration;

  /** The message from the last error. */
  private String errorMessage;

  /** The target channel to find the nuclei. */
  private int targetChannel;

  /** The analysis channel to perform measurements inside/outside the nuclei. */
  private int analysisChannel;

  /** The detector settings. */
  private uk.ac.sussex.gdsc.foci.NucleiOutline_PlugIn.Settings detectorSettings;

  /** The image to operate on. Multiple frames, single channel. */
  private ImgPlus<T> img;

  @Override
  public String getInfoText() {
    return INFO_TEXT;
  }

  @Override
  public ImageIcon getIcon() {
    return null;
  }

  @Override
  public String getKey() {
    return KEY;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public boolean setTarget(final ImgPlus<T> img, final Map<String, Object> settings) {
    this.img = img;
    calibration = TMUtils.getSpatialCalibration(img);
    if (checkSettings(settings)) {
      // Convert to zero-based index
      targetChannel = (Integer) settings.get(DetectorKeys.KEY_TARGET_CHANNEL) - 1;
      analysisChannel = (Integer) settings.getOrDefault(SETTING_ANALYSIS_CHANNEL, 0) - 1;
      // Convert settings from the generic map
      detectorSettings = new uk.ac.sussex.gdsc.foci.NucleiOutline_PlugIn.Settings();
      detectorSettings.setBlur1((Double) settings.get(SETTING_BLUR1));
      detectorSettings.setBlur2((Double) settings.get(SETTING_BLUR2));
      detectorSettings.setMethod((AutoThreshold.Method) settings.get(SETTING_METHOD));
      detectorSettings.setOutlierRadius((Double) settings.get(SETTING_OUTLIER_RADIUS));
      detectorSettings.setOutlierThreshold((Double) settings.get(SETTING_OUTLIER_THRESHOLD));
      detectorSettings.setMaxNucleusSize((Double) settings.get(SETTING_MAX_NUCLEUS_SIZE));
      detectorSettings.setMinNucleusSize((Double) settings.get(SETTING_MIN_NUCLEUS_SIZE));
      detectorSettings.setErosion((Integer) settings.get(SETTING_EROSION));
      detectorSettings.setExpansionInner((Integer) settings.get(SETTING_EXPANSION_INNER));
      detectorSettings.setExpansion((Integer) settings.get(SETTING_EXPANSION));
      return true;
    }
    return false;
  }

  @Override
  public Map<String, Object> getDefaultSettings() {
    final uk.ac.sussex.gdsc.foci.NucleiOutline_PlugIn.Settings settings =
        uk.ac.sussex.gdsc.foci.NucleiOutline_PlugIn.Settings.load();
    final HashMap<String, Object> map = new HashMap<>();
    map.put(DetectorKeys.KEY_TARGET_CHANNEL, DetectorKeys.DEFAULT_TARGET_CHANNEL);
    map.put(SETTING_ANALYSIS_CHANNEL, Integer.valueOf(0));
    // Convert settings to a map
    map.put(SETTING_BLUR1, settings.getBlur1());
    map.put(SETTING_BLUR2, settings.getBlur2());
    map.put(SETTING_METHOD, settings.getMethod());
    map.put(SETTING_OUTLIER_RADIUS, settings.getOutlierRadius());
    map.put(SETTING_OUTLIER_THRESHOLD, settings.getOutlierThreshold());
    map.put(SETTING_MAX_NUCLEUS_SIZE, settings.getMaxNucleusSize());
    map.put(SETTING_MIN_NUCLEUS_SIZE, settings.getMinNucleusSize());
    map.put(SETTING_EROSION, settings.getErosion());
    map.put(SETTING_EXPANSION_INNER, settings.getExpansionInner());
    map.put(SETTING_EXPANSION, settings.getExpansion());
    return map;
  }

  @Override
  public boolean checkSettings(final Map<String, Object> settings) {
    final StringBuilder errorHolder = new StringBuilder();
    TMUtils.checkParameter(settings, DetectorKeys.KEY_TARGET_CHANNEL, Integer.class, errorHolder);
    TMUtils.checkParameter(settings, SETTING_ANALYSIS_CHANNEL, Integer.class, errorHolder);
    TMUtils.checkParameter(settings, SETTING_BLUR1, Double.class, errorHolder);
    TMUtils.checkParameter(settings, SETTING_BLUR2, Double.class, errorHolder);
    TMUtils.checkParameter(settings, SETTING_METHOD, AutoThreshold.Method.class, errorHolder);
    TMUtils.checkParameter(settings, SETTING_OUTLIER_RADIUS, Double.class, errorHolder);
    TMUtils.checkParameter(settings, SETTING_OUTLIER_THRESHOLD, Double.class, errorHolder);
    TMUtils.checkParameter(settings, SETTING_MAX_NUCLEUS_SIZE, Double.class, errorHolder);
    TMUtils.checkParameter(settings, SETTING_MIN_NUCLEUS_SIZE, Double.class, errorHolder);
    TMUtils.checkParameter(settings, SETTING_EROSION, Integer.class, errorHolder);
    TMUtils.checkParameter(settings, SETTING_EXPANSION_INNER, Integer.class, errorHolder);
    TMUtils.checkParameter(settings, SETTING_EXPANSION, Integer.class, errorHolder);
    // Note: Analysis of the second channel is not mandatory
    final List<String> mandatoryKeys = new ArrayList<>();
    mandatoryKeys.add(DetectorKeys.KEY_TARGET_CHANNEL);
    mandatoryKeys.add(SETTING_ANALYSIS_CHANNEL);
    mandatoryKeys.add(SETTING_BLUR1);
    mandatoryKeys.add(SETTING_BLUR2);
    mandatoryKeys.add(SETTING_METHOD);
    mandatoryKeys.add(SETTING_OUTLIER_RADIUS);
    mandatoryKeys.add(SETTING_OUTLIER_THRESHOLD);
    mandatoryKeys.add(SETTING_MAX_NUCLEUS_SIZE);
    mandatoryKeys.add(SETTING_MIN_NUCLEUS_SIZE);
    mandatoryKeys.add(SETTING_EROSION);
    mandatoryKeys.add(SETTING_EXPANSION_INNER);
    mandatoryKeys.add(SETTING_EXPANSION);
    TMUtils.checkMapKeys(settings, mandatoryKeys, null, errorHolder);
    if (errorHolder.length() != 0) {
      errorMessage = errorHolder.toString();
      return false;
    }
    return true;
  }

  @Override
  public SpotDetector<T> getDetector(final Interval interval, final int frame) {
    final RandomAccessible<T> imFrame1 = prepareFrameImg(frame, targetChannel);
    final RandomAccessible<T> imFrame2 = prepareFrameImg(frame, analysisChannel);
    return new NucleusDetector<>(detectorSettings, imFrame1, imFrame2, interval, calibration);
  }

  /**
   * Prepare a single frame, single channel view of the image.
   *
   * @param frame the frame
   * @param channel the channel
   * @return the single frame (or null if the channel is negative)
   */
  private RandomAccessible<T> prepareFrameImg(final int frame, final int channel) {
    if (channel < 0) {
      return null;
    }
    RandomAccessible<T> imFrame;
    final int cDim = TMUtils.findCAxisIndex(img);
    if (cDim < 0) {
      imFrame = img;
    } else {
      // In ImgLib2, dimensions are 0-based.
      imFrame = Views.hyperSlice(img, cDim, channel);
    }

    int timeDim = TMUtils.findTAxisIndex(img);
    if (timeDim >= 0) {
      if (cDim >= 0 && timeDim > cDim) {
        timeDim--;
      }
      imFrame = Views.hyperSlice(imFrame, timeDim, frame);
    }

    return imFrame;
  }

  @Override
  public String getErrorMessage() {
    return errorMessage;
  }

  @Override
  public boolean marshall(final Map<String, Object> settings, final Element element) {
    // This may not be needed. It is not present in the TrackMate source files but is
    // mentioned on the tutorials.
    element.setAttribute(DetectorKeys.XML_ATTRIBUTE_DETECTOR_NAME, getKey());

    final StringBuilder errorHolder = new StringBuilder();
    IOUtils.writeAttribute(settings, element, DetectorKeys.KEY_TARGET_CHANNEL, Integer.class,
        errorHolder);
    IOUtils.writeAttribute(settings, element, SETTING_ANALYSIS_CHANNEL, Integer.class, errorHolder);
    IOUtils.writeAttribute(settings, element, SETTING_BLUR1, Double.class, errorHolder);
    IOUtils.writeAttribute(settings, element, SETTING_BLUR2, Double.class, errorHolder);
    IOUtils.writeAttribute(settings, element, SETTING_METHOD, AutoThreshold.Method.class,
        errorHolder);
    IOUtils.writeAttribute(settings, element, SETTING_OUTLIER_RADIUS, Double.class, errorHolder);
    IOUtils.writeAttribute(settings, element, SETTING_OUTLIER_THRESHOLD, Double.class, errorHolder);
    IOUtils.writeAttribute(settings, element, SETTING_MAX_NUCLEUS_SIZE, Double.class, errorHolder);
    IOUtils.writeAttribute(settings, element, SETTING_MIN_NUCLEUS_SIZE, Double.class, errorHolder);
    IOUtils.writeAttribute(settings, element, SETTING_EROSION, Integer.class, errorHolder);
    IOUtils.writeAttribute(settings, element, SETTING_EXPANSION_INNER, Integer.class, errorHolder);
    IOUtils.writeAttribute(settings, element, SETTING_EXPANSION, Integer.class, errorHolder);
    if (errorHolder.length() != 0) {
      errorMessage = errorHolder.toString();
      return false;
    }
    return true;
  }

  @Override
  public boolean unmarshall(final Element element, final Map<String, Object> settings) {
    settings.clear();
    final StringBuilder errorHolder = new StringBuilder();
    IOUtils.readIntegerAttribute(element, settings, DetectorKeys.KEY_TARGET_CHANNEL, errorHolder);
    IOUtils.readIntegerAttribute(element, settings, SETTING_ANALYSIS_CHANNEL, errorHolder);
    IOUtils.readDoubleAttribute(element, settings, SETTING_BLUR1, errorHolder);
    IOUtils.readDoubleAttribute(element, settings, SETTING_BLUR2, errorHolder);
    readMethodAttribute(element, settings, SETTING_METHOD, errorHolder);
    IOUtils.readDoubleAttribute(element, settings, SETTING_OUTLIER_RADIUS, errorHolder);
    IOUtils.readDoubleAttribute(element, settings, SETTING_OUTLIER_THRESHOLD, errorHolder);
    IOUtils.readDoubleAttribute(element, settings, SETTING_MAX_NUCLEUS_SIZE, errorHolder);
    IOUtils.readDoubleAttribute(element, settings, SETTING_MIN_NUCLEUS_SIZE, errorHolder);
    IOUtils.readIntegerAttribute(element, settings, SETTING_EROSION, errorHolder);
    IOUtils.readIntegerAttribute(element, settings, SETTING_EXPANSION_INNER, errorHolder);
    IOUtils.readIntegerAttribute(element, settings, SETTING_EXPANSION, errorHolder);
    if (errorHolder.length() != 0) {
      errorMessage = errorHolder.toString();
      return false;
    }
    return checkSettings(settings);
  }

  /**
   * Read the Method attribute.
   *
   * @param element the element
   * @param settings the settings
   * @param parameterKey the parameter key
   * @param errorHolder the error holder
   * @return true, if successful
   */
  private static final boolean readMethodAttribute(final Element element,
      final Map<String, Object> settings, final String parameterKey,
      final StringBuilder errorHolder) {
    final String str = element.getAttributeValue(parameterKey);
    if (null == str) {
      errorHolder.append("Attribute " + parameterKey + " could not be found in XML element.\n");
      return false;
    }
    try {
      final AutoThreshold.Method val = AutoThreshold.getMethod(str);
      settings.put(parameterKey, val);
    } catch (final NumberFormatException nfe) {
      errorHolder.append(
          "Could not read " + parameterKey + " attribute as a double value. Got " + str + ".\n");
      return false;
    }
    return true;
  }

  @Override
  public ConfigurationPanel getDetectorConfigurationPanel(final Settings settings,
      final Model model) {
    return new NucleusDetectorConfigurationPanel(settings, model);
  }
}
