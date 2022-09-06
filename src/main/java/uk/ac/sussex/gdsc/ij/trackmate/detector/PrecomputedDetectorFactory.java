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

package uk.ac.sussex.gdsc.ij.trackmate.detector;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.detection.DetectorKeys;
import fiji.plugin.trackmate.detection.SpotDetector;
import fiji.plugin.trackmate.detection.SpotDetectorFactory;
import fiji.plugin.trackmate.detection.SpotDetectorFactoryBase;
import fiji.plugin.trackmate.gui.components.ConfigurationPanel;
import fiji.plugin.trackmate.io.IOUtils;
import fiji.plugin.trackmate.util.TMUtils;
import ij.Prefs;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import javax.swing.ImageIcon;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imglib2.Interval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import org.jdom2.Element;
import org.scijava.plugin.Plugin;
import uk.ac.sussex.gdsc.core.utils.LocalList;
import uk.ac.sussex.gdsc.core.utils.TextUtils;
import uk.ac.sussex.gdsc.core.utils.UnicodeReader;

/**
 * A factory for creating {@link PrecomputedDetector} objects.
 *
 * @param <T> the pixels type
 */
@Plugin(type = SpotDetectorFactory.class)
public class PrecomputedDetectorFactory<T extends RealType<T> & NativeType<T>>
    implements SpotDetectorFactory<T> {

  /** HTML information text. */
  // @formatter:off
  static final String INFO_TEXT =
    "<html><p>Load precomputed spots.</p>"
    + "<ul>"
    + "<li>Define an input file.</li>"
    + "<li>Define file header.</li>"
    + "<li>Define delimiter.</li>"
    + "<li>Define optional ID column.</li>"
    + "<li>Define Frame column.</li>"
    + "<li>Define XYZ columns.</li>"
    + "<li>Define Radius column.</li>"
    + "<li>Define optional Category column.</li>"
    + "</ul><p>The category file has one category per line. Numbers are assigned to each unique "
    + "category in encountered order.</p></html>";
  // @formatter:on

  /** The pretty name of the target detector. */
  static final String NAME = "Precomputed detector";

  /** A string key identifying this factory. */
  private static final String KEY = "PRECOMPUTED_DETECTOR";

  /** Zero as an Integer. */
  private static final Integer ZERO = 0;

  /** Setting key for the input file. */
  static final String SETTING_INPUT_FILE = "INPUT_FILE";
  /** Setting key for the number of header lines. */
  static final String SETTING_HEADER_LINES = "HEADER_LINES";
  /** Setting key for the comment character. */
  static final String SETTING_COMMENT_CHAR = "COMMENT_CHAR";
  /** Setting key for the column delimiter. */
  static final String SETTING_DELIMITER = "DELIMITER";
  /** Setting key for the spot id column. */
  static final String SETTING_COLUMN_ID = "COLUMN_ID";
  /** Setting key for the spot frame column. */
  static final String SETTING_COLUMN_FRAME = "COLUMN_FRAME";
  /** Setting key for the spot x position column. */
  static final String SETTING_COLUMN_X = "COLUMN_X";
  /** Setting key for the spot y position column. */
  static final String SETTING_COLUMN_Y = "COLUMN_Y";
  /** Setting key for the spot z position column. */
  static final String SETTING_COLUMN_Z = "COLUMN_Z";
  /** Setting key for the spot radius column. */
  static final String SETTING_COLUMN_RADIUS = "COLUMN_RADIUS";
  /** Setting key for the spot category column. */
  static final String SETTING_COLUMN_CATEGORY = "COLUMN_CATEGORY";
  /** Setting key for the category file. */
  static final String SETTING_CATEGORY_FILE = "CATEGORY_FILE";

  private static final String KEY_INPUT_FILE = "gdsc.tm.detector.precomputed.input_file";
  private static final String KEY_HEADER_LINES = "gdsc.tm.detector.precomputed.header_lines";
  private static final String KEY_COMMENT_CHAR = "gdsc.tm.detector.precomputed.comment_char";
  private static final String KEY_DELIMITER = "gdsc.tm.detector.precomputed.delimiter";
  private static final String KEY_COLUMN_ID = "gdsc.tm.detector.precomputed.column_id";
  private static final String KEY_COLUMN_FRAME = "gdsc.tm.detector.precomputed.column_frame";
  private static final String KEY_COLUMN_X = "gdsc.tm.detector.precomputed.column_x";
  private static final String KEY_COLUMN_Y = "gdsc.tm.detector.precomputed.column_y";
  private static final String KEY_COLUMN_Z = "gdsc.tm.detector.precomputed.column_z";
  private static final String KEY_COLUMN_RADIUS = "gdsc.tm.detector.precomputed.column_radius";
  private static final String KEY_COLUMN_CATEGORY = "gdsc.tm.detector.precomputed.column_category";
  private static final String KEY_CATEGORY_FILE = "gdsc.tm.detector.precomputed.category_file";

  /** The pixel sizes in the 3 dimensions. */
  private double[] calibration;

  /** The message from the last error. */
  private String errorMessage;

  /** The data. */
  private Map<Integer, List<RawSpot>> data;

  /**
   * Simple class to implement the SpotDetector interface when there is an error. It returns the
   * error message and empty/not valid results for all other methods.
   */
  private class FailedSpotDetector implements SpotDetector<T> {
    @Override
    public List<Spot> getResult() {
      return Collections.emptyList();
    }

    @Override
    public boolean checkInput() {
      return false;
    }

    @Override
    public boolean process() {
      return false;
    }

    @Override
    public String getErrorMessage() {
      return errorMessage;
    }

    @Override
    public long getProcessingTime() {
      return 0;
    }
  }

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
    // Reset
    calibration = null;
    errorMessage = null;
    data = null;

    if ((img.dimensionIndex(Axes.X) | img.dimensionIndex(Axes.Y)) < 0) {
      errorMessage = "Image must have XY axes";
      return false;
    }
    if (!checkSettings(settings)) {
      return false;
    }

    saveSettings(settings);

    // Load the pre-computed data
    final StringBuilder errorHolder = new StringBuilder();
    final Map<Integer, List<RawSpot>> data = new HashMap<>();
    readInput(settings, img.dimensionIndex(Axes.Z) < 0, data, errorHolder);
    if (errorHolder.length() != 0) {
      errorMessage = errorHolder.toString();
      return false;
    }
    calibration = TMUtils.getSpatialCalibration(img);
    this.data = data;
    return true;
  }

  @Override
  public Map<String, Object> getDefaultSettings() {
    // Load previous settings
    final HashMap<String, Object> map = new HashMap<>();
    map.put(SETTING_INPUT_FILE, Prefs.get(KEY_INPUT_FILE, ""));
    map.put(SETTING_HEADER_LINES, (int) Prefs.get(KEY_HEADER_LINES, 1));
    map.put(SETTING_COMMENT_CHAR, Prefs.get(KEY_COMMENT_CHAR, "#"));
    map.put(SETTING_DELIMITER, Prefs.get(KEY_DELIMITER, ","));
    map.put(SETTING_COLUMN_ID, (int) Prefs.get(KEY_COLUMN_ID, -1));
    map.put(SETTING_COLUMN_FRAME, (int) Prefs.get(KEY_COLUMN_FRAME, 0));
    map.put(SETTING_COLUMN_X, (int) Prefs.get(KEY_COLUMN_X, 1));
    map.put(SETTING_COLUMN_Y, (int) Prefs.get(KEY_COLUMN_Y, 2));
    map.put(SETTING_COLUMN_Z, (int) Prefs.get(KEY_COLUMN_Z, 3));
    map.put(SETTING_COLUMN_RADIUS, (int) Prefs.get(KEY_COLUMN_RADIUS, 4));
    map.put(SETTING_COLUMN_CATEGORY, (int) Prefs.get(KEY_COLUMN_CATEGORY, -1));
    map.put(SETTING_CATEGORY_FILE, Prefs.get(KEY_CATEGORY_FILE, ""));
    return map;
  }

  /**
   * Save settings to the ImageJ preferences.
   *
   * @param map the settings map
   */
  private static void saveSettings(Map<String, Object> map) {
    Prefs.set(KEY_INPUT_FILE, (String) map.get(SETTING_INPUT_FILE));
    Prefs.set(KEY_HEADER_LINES, (int) map.get(SETTING_HEADER_LINES));
    Prefs.set(KEY_COMMENT_CHAR, (String) map.get(SETTING_COMMENT_CHAR));
    Prefs.set(KEY_DELIMITER, (String) map.get(SETTING_DELIMITER));
    Prefs.set(KEY_COLUMN_ID, (int) map.get(SETTING_COLUMN_ID));
    Prefs.set(KEY_COLUMN_FRAME, (int) map.get(SETTING_COLUMN_FRAME));
    Prefs.set(KEY_COLUMN_X, (int) map.get(SETTING_COLUMN_X));
    Prefs.set(KEY_COLUMN_Y, (int) map.get(SETTING_COLUMN_Y));
    Prefs.set(KEY_COLUMN_Z, (int) map.get(SETTING_COLUMN_Z));
    Prefs.set(KEY_COLUMN_RADIUS, (int) map.get(SETTING_COLUMN_RADIUS));
    Prefs.set(KEY_COLUMN_CATEGORY, (int) map.get(SETTING_COLUMN_CATEGORY));
    Prefs.set(KEY_CATEGORY_FILE, (String) map.get(SETTING_CATEGORY_FILE));
  }

  @Override
  public boolean checkSettings(final Map<String, Object> settings) {
    final StringBuilder errorHolder = new StringBuilder();
    TMUtils.checkParameter(settings, SETTING_INPUT_FILE, String.class, errorHolder);
    TMUtils.checkParameter(settings, SETTING_HEADER_LINES, Integer.class, errorHolder);
    TMUtils.checkParameter(settings, SETTING_COMMENT_CHAR, String.class, errorHolder);
    TMUtils.checkParameter(settings, SETTING_DELIMITER, String.class, errorHolder);
    TMUtils.checkParameter(settings, SETTING_COLUMN_ID, Integer.class, errorHolder);
    TMUtils.checkParameter(settings, SETTING_COLUMN_FRAME, Integer.class, errorHolder);
    TMUtils.checkParameter(settings, SETTING_COLUMN_X, Integer.class, errorHolder);
    TMUtils.checkParameter(settings, SETTING_COLUMN_Y, Integer.class, errorHolder);
    TMUtils.checkParameter(settings, SETTING_COLUMN_Z, Integer.class, errorHolder);
    TMUtils.checkParameter(settings, SETTING_COLUMN_RADIUS, Integer.class, errorHolder);
    TMUtils.checkParameter(settings, SETTING_COLUMN_CATEGORY, Integer.class, errorHolder);
    TMUtils.checkParameter(settings, SETTING_CATEGORY_FILE, String.class, errorHolder);
    // This checks for extra keys that should not be present
    final List<String> mandatoryKeys = Arrays.asList(SETTING_INPUT_FILE, SETTING_HEADER_LINES,
        SETTING_COMMENT_CHAR, SETTING_DELIMITER, SETTING_COLUMN_ID, SETTING_COLUMN_FRAME,
        SETTING_COLUMN_X, SETTING_COLUMN_Y, SETTING_COLUMN_Z, SETTING_COLUMN_RADIUS,
        SETTING_COLUMN_CATEGORY, SETTING_CATEGORY_FILE);
    TMUtils.checkMapKeys(settings, mandatoryKeys, null, errorHolder);
    if (errorHolder.length() != 0) {
      errorMessage = errorHolder.toString();
      return false;
    }
    return true;
  }

  /**
   * Read the input into the provided data object.
   *
   * @param settings the settings
   * @param ignoreZ true if the Z column can be ignored
   * @param data the data (to be populated)
   * @param errorHolder the error holder
   */
  private static void readInput(Map<String, Object> settings, boolean ignoreZ,
      Map<Integer, List<RawSpot>> data, StringBuilder errorHolder) {
    // This could fail if frame or XYZ are outside the bounds of the target image.
    // For now this fact is ignored and the spots are simply loaded. The SpotDetector
    // will be used to filter the spots based on the input bounds.

    final String inputFile = (String) settings.get(SETTING_INPUT_FILE);
    int headerLines = (int) settings.get(SETTING_HEADER_LINES);
    final String commentChar = (String) settings.get(SETTING_COMMENT_CHAR);
    final String delimiter = (String) settings.get(SETTING_DELIMITER);
    final int columnId = (int) settings.get(SETTING_COLUMN_ID);
    final int columnFrame = (int) settings.get(SETTING_COLUMN_FRAME);
    final int columnX = (int) settings.get(SETTING_COLUMN_X);
    final int columnY = (int) settings.get(SETTING_COLUMN_Y);
    final int columnZ = (int) settings.get(SETTING_COLUMN_Z);
    final int columnRadius = (int) settings.get(SETTING_COLUMN_RADIUS);
    final int columnCategory = (int) settings.get(SETTING_COLUMN_CATEGORY);
    final String categoryFile = (String) settings.get(SETTING_CATEGORY_FILE);

    // ID field is optional
    Function<String[], String> idFunction;
    if (columnId < 0) {
      final int[] count = {0};
      idFunction = fields -> Integer.toString(++count[0]);
    } else {
      idFunction = fields -> fields[columnId];
    }

    // Category field is optional
    ToIntFunction<String[]> catgeoryFunction;
    if (columnCategory < 0) {
      catgeoryFunction = fields -> 0;
    } else {
      // Read the category map
      final Map<String, Integer> map = new HashMap<>();
      map.put("", ZERO);
      try (BufferedReader input =
          new BufferedReader(new UnicodeReader(new FileInputStream(categoryFile), "UTF-8"))) {
        String line;
        while ((line = input.readLine()) != null) {
          // First occurrences are added using the next category ID
          map.computeIfAbsent(line.trim(), key -> map.size());
        }
      } catch (final IOException ex) {
        errorHolder.append("IO error in category map: ").append(ex.getMessage()).append('\n');
        return;
      }
      final Set<String> unknown = new HashSet<>();
      catgeoryFunction = fields -> {
        final String key = fields[columnCategory];
        final Integer value = map.get(key);
        if (value == null) {
          // Log the error only once
          if (unknown.add(key)) {
            errorHolder.append("Unknown category: ").append(key).append('\n');
          }
          return 0;
        }
        return value.intValue();
      };
    }

    try (BufferedReader input =
        new BufferedReader(new UnicodeReader(new FileInputStream(inputFile), "UTF-8"))) {
      final Pattern p = Pattern.compile(delimiter);
      final boolean hasComment = !TextUtils.isNullOrEmpty(commentChar);

      int count = 0;
      String line;
      while ((line = input.readLine()) != null) {
        // Skip header
        // Skip empty lines
        // Skip comments
        if ((headerLines-- > 0) || (line.length() == 0)
            || (hasComment && line.startsWith(commentChar))) {
          continue;
        }

        final String[] fields = p.split(line);
        count++;

        try {
          final String id = idFunction.apply(fields);
          final int frame = Integer.parseInt(fields[columnFrame]);
          final double x = Double.parseDouble(fields[columnX]);
          final double y = Double.parseDouble(fields[columnY]);
          final double z = ignoreZ ? 0 : Double.parseDouble(fields[columnZ]);
          final double radius = Double.parseDouble(fields[columnRadius]);
          final int category = catgeoryFunction.applyAsInt(fields);

          data.computeIfAbsent(frame, t -> new LocalList<>())
              .add(new RawSpot(id, x, y, z, radius, category));
        } catch (final NumberFormatException | IndexOutOfBoundsException ex) {
          errorHolder.append("Error on record number ").append(count).append(":\n");
          if (line.length() < 40) {
            errorHolder.append(line);
          } else {
            errorHolder.append(line, 0, 37).append("...");
          }
          errorHolder.append("\n\n").append(ex.getClass().getSimpleName()).append(": ")
              .append(ex.getMessage()).append('\n');
          break;
        }
      }
    } catch (final IOException ex) {
      errorHolder.append("IO error: ").append(ex.getMessage()).append('\n');
    }
  }

  @Override
  public SpotDetector<T> getDetector(final Interval interval, final int frame) {
    // This should only be called after setTarget.
    // If we have no data then return a dummy detector that will return the error message.
    if (data == null) {
      return new FailedSpotDetector();
    }
    return new PrecomputedDetector<>(data.getOrDefault(frame, Collections.emptyList()), interval,
        calibration);
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
    IOUtils.writeAttribute(settings, element, SETTING_INPUT_FILE, String.class, errorHolder);
    IOUtils.writeAttribute(settings, element, SETTING_HEADER_LINES, Integer.class, errorHolder);
    IOUtils.writeAttribute(settings, element, SETTING_COMMENT_CHAR, String.class, errorHolder);
    IOUtils.writeAttribute(settings, element, SETTING_DELIMITER, String.class, errorHolder);
    IOUtils.writeAttribute(settings, element, SETTING_COLUMN_ID, Integer.class, errorHolder);
    IOUtils.writeAttribute(settings, element, SETTING_COLUMN_FRAME, Integer.class, errorHolder);
    IOUtils.writeAttribute(settings, element, SETTING_COLUMN_X, Integer.class, errorHolder);
    IOUtils.writeAttribute(settings, element, SETTING_COLUMN_Y, Integer.class, errorHolder);
    IOUtils.writeAttribute(settings, element, SETTING_COLUMN_Z, Integer.class, errorHolder);
    IOUtils.writeAttribute(settings, element, SETTING_COLUMN_RADIUS, Integer.class, errorHolder);
    IOUtils.writeAttribute(settings, element, SETTING_COLUMN_CATEGORY, Integer.class, errorHolder);
    IOUtils.writeAttribute(settings, element, SETTING_CATEGORY_FILE, String.class, errorHolder);
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
    readStringAttribute(element, settings, SETTING_INPUT_FILE, errorHolder);
    IOUtils.readIntegerAttribute(element, settings, SETTING_HEADER_LINES, errorHolder);
    readStringAttribute(element, settings, SETTING_COMMENT_CHAR, errorHolder);
    readStringAttribute(element, settings, SETTING_DELIMITER, errorHolder);
    IOUtils.readIntegerAttribute(element, settings, SETTING_COLUMN_ID, errorHolder);
    IOUtils.readIntegerAttribute(element, settings, SETTING_COLUMN_FRAME, errorHolder);
    IOUtils.readIntegerAttribute(element, settings, SETTING_COLUMN_X, errorHolder);
    IOUtils.readIntegerAttribute(element, settings, SETTING_COLUMN_Y, errorHolder);
    IOUtils.readIntegerAttribute(element, settings, SETTING_COLUMN_Z, errorHolder);
    IOUtils.readIntegerAttribute(element, settings, SETTING_COLUMN_RADIUS, errorHolder);
    IOUtils.readIntegerAttribute(element, settings, SETTING_COLUMN_CATEGORY, errorHolder);
    readStringAttribute(element, settings, SETTING_CATEGORY_FILE, errorHolder);
    if (errorHolder.length() != 0) {
      errorMessage = errorHolder.toString();
      return false;
    }
    return checkSettings(settings);
  }

  /**
   * Read the String attribute.
   *
   * @param element the element
   * @param settings the settings
   * @param parameterKey the parameter key
   * @param errorHolder the error holder
   * @return true, if successful
   */
  private static final boolean readStringAttribute(final Element element,
      final Map<String, Object> settings, final String parameterKey,
      final StringBuilder errorHolder) {
    final String str = element.getAttributeValue(parameterKey);
    if (null == str) {
      errorHolder.append("Attribute " + parameterKey + " could not be found in XML element.\n");
      return false;
    }
    settings.put(parameterKey, str);
    return true;
  }

  @Override
  public ConfigurationPanel getDetectorConfigurationPanel(final Settings settings,
      final Model model) {
    return new PrecomputedDetectorConfigurationPanel(settings, model);
  }

  @Override
  public SpotDetectorFactoryBase<T> copy() {
    // Method added in TM v7.2.1
    // It is not clear if the copy requires a fresh instance, shallow copy or deep copy.
    // The method is used in fiji.plugin.trackmate.Settings.copyOn(ImagePlus) where it is
    // documented to copy the settings but configured to run on a new image. In this case
    // any cache is not relevant and we return a fresh instance.
    return new PrecomputedDetectorFactory<>();
  }
}
