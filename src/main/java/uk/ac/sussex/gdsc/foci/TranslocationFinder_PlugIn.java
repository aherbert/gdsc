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

package uk.ac.sussex.gdsc.foci;

import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.annotation.Nullable;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.match.MatchCalculator;
import uk.ac.sussex.gdsc.core.match.PointPair;
import uk.ac.sussex.gdsc.core.utils.MathUtils;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.ImageCanvas;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.TextRoi;
import ij.gui.Toolbar;
import ij.plugin.PlugIn;
import ij.plugin.tool.PlugInTool;
import ij.process.ByteProcessor;
import ij.text.TextPanel;
import ij.text.TextWindow;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Find translocations using markers for colocalisation.
 *
 * <p>Run a pairwise analysis of 3 channels. Find triplets where the two markers from channel 2
 * &amp; 3 matching a foci in channel 1 are also a matching pair. Draw a bounding box round the
 * triplet and output the distances between the centres. Output a guess for a translocation where
 * channel 13 distance &lt;&lt; 12|23, no transolcation where 12 &lt;&lt; 13|23.
 */
public class TranslocationFinder_PlugIn implements PlugIn {
  private static final String TITLE = "Translocation Finder";
  private static final AtomicReference<TextWindow> resultsWindow = new AtomicReference<>();
  private static final int UNKNOWN = 0;
  private static final int NO_TRANSLOCATION = 1;
  private static final int TRANSLOCATION = 2;
  private static final String[] CLASSIFICATION = {"Unknown", "No translocation", "Translocation"};

  /** The current settings for the plugin instance. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    String resultsName1 = "";
    String resultsName2 = "";
    String resultsName3 = "";
    String image = "";
    double distance = 50;
    double minDistance = 8;
    double factor = 2;
    boolean showMatches;

    // To following are not state shown in the dialog but state set when the plugin is run
    // that can be used by the plugin tool.

    AssignedPoint[] foci1;
    AssignedPoint[] foci2;
    AssignedPoint[] foci3;
    /** Image to draw overlay. */
    ImagePlus imp;
    /** Current set of triplets. */
    ArrayList<int[]> triplets;
    /** Image to draw overlay for the manual triplets. */
    ImagePlus manualImp;
    /** Current set of manual triplets. */
    ArrayList<AssignedPoint[]> manualTriplets;

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
      resultsName1 = source.resultsName1;
      resultsName2 = source.resultsName2;
      resultsName3 = source.resultsName3;
      image = source.image;
      distance = source.distance;
      minDistance = source.minDistance;
      factor = source.factor;
      showMatches = source.showMatches;
      foci1 = source.foci1;
      foci2 = source.foci2;
      foci3 = source.foci3;
      imp = source.imp;
      triplets = source.triplets;
      manualImp = source.manualImp;
      manualTriplets = source.manualTriplets;
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

  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    if ("tool".equals(arg)) {
      addPluginTool();
      return;
    }

    // List the foci results
    final String[] names = FindFoci_PlugIn.getResultsNames();
    if (names == null || names.length < 3) {
      IJ.error(TITLE,
          "3 sets of Foci must be stored in memory using the " + FindFoci_PlugIn.TITLE + " plugin");
      return;
    }

    // Build a list of the open images to add an overlay
    final String[] imageList =
        ImageJUtils.getImageList(ImageJUtils.GREY_8_16 | ImageJUtils.NO_IMAGE, null);

    settings = Settings.load();
    final GenericDialog gd = new GenericDialog(TITLE);

    gd.addMessage(
        "Analyses spots within a mask/ROI region\nand computes density and closest distances.");

    gd.addChoice("Resultsettings.name_1", names, settings.resultsName1);
    gd.addChoice("Resultsettings.name_2", names, settings.resultsName2);
    gd.addChoice("Resultsettings.name_3", names, settings.resultsName3);
    gd.addChoice("Overlay_on_image", imageList, settings.image);
    gd.addNumericField("Distance", settings.distance, 2, 6, "pixels");
    gd.addNumericField("Min_distance", settings.minDistance, 2, 6, "pixels");
    gd.addNumericField("Factor", settings.factor, 2);
    gd.addCheckbox("Show_matches", settings.showMatches);
    gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.FIND_FOCI);

    gd.showDialog();
    if (!gd.wasOKed()) {
      return;
    }

    settings.resultsName1 = gd.getNextChoice();
    settings.resultsName2 = gd.getNextChoice();
    settings.resultsName3 = gd.getNextChoice();
    settings.image = gd.getNextChoice();
    settings.distance = gd.getNextNumber();
    settings.minDistance = gd.getNextNumber();
    settings.factor = gd.getNextNumber();
    settings.showMatches = gd.getNextBoolean();

    // Get the foci
    settings.foci1 = getFoci(settings.resultsName1);
    settings.foci2 = getFoci(settings.resultsName2);
    settings.foci3 = getFoci(settings.resultsName3);

    if (anyNull(settings.foci1, settings.foci2, settings.foci3)) {
      return;
    }

    analyse();

    settings.save();
  }

  @Nullable
  private static AssignedPoint[] getFoci(String resultsName) {
    final FindFociMemoryResults memoryResults = FindFoci_PlugIn.getResults(resultsName);
    if (memoryResults == null) {
      IJ.error(TITLE, "No foci with the name " + resultsName);
      return null;
    }
    final List<FindFociResult> results = memoryResults.getResults();
    if (results.isEmpty()) {
      IJ.error(TITLE, "Zero foci in the results with the name " + resultsName);
      return null;
    }
    final AssignedPoint[] foci = new AssignedPoint[results.size()];
    int index = 0;
    for (final FindFociResult result : results) {
      foci[index] = new AssignedPoint(result.x, result.y, result.z + 1, index);
      index++;
    }
    return foci;
  }

  /**
   * Check if any of the results are null.
   *
   * @param foci1 the foci in set 1
   * @param foci2 the foci in set 2
   * @param foci3 the foci in set 3
   * @return true if at least one result is null
   */
  private static boolean anyNull(AssignedPoint[] foci1, AssignedPoint[] foci2,
      AssignedPoint[] foci3) {
    return foci1 == null || foci2 == null || foci3 == null;
  }

  /**
   * For all foci in set 1, compare to set 2 and output a histogram of the average density around
   * each foci (pair correlation) and the minimum distance to another foci.
   *
   * @param foci1 the foci in set 1
   * @param foci2 the foci in set 2
   * @param foci3 the foci in set 3
   */
  private void analyse() {

    // Compute pairwise matches
    final boolean is3D = is3D(settings.foci1) || is3D(settings.foci2) || is3D(settings.foci3);
    final List<PointPair> matches12 =
        new ArrayList<>(Math.min(settings.foci1.length, settings.foci2.length));
    final List<PointPair> matches13 =
        new ArrayList<>(Math.min(settings.foci1.length, settings.foci3.length));
    final List<PointPair> matches23 =
        new ArrayList<>(Math.min(settings.foci2.length, settings.foci3.length));
    if (is3D) {
      MatchCalculator.analyseResults3D(settings.foci1, settings.foci2, settings.distance, null,
          null, null, matches12);
      MatchCalculator.analyseResults3D(settings.foci1, settings.foci3, settings.distance, null,
          null, null, matches13);
      MatchCalculator.analyseResults3D(settings.foci2, settings.foci3, settings.distance, null,
          null, null, matches23);
    } else {
      MatchCalculator.analyseResults2D(settings.foci1, settings.foci2, settings.distance, null,
          null, null, matches12);
      MatchCalculator.analyseResults2D(settings.foci1, settings.foci3, settings.distance, null,
          null, null, matches13);
      MatchCalculator.analyseResults2D(settings.foci2, settings.foci3, settings.distance, null,
          null, null, matches23);
    }

    // Use for an overlay
    settings.imp = WindowManager.getImage(settings.image);

    if (settings.imp != null && settings.showMatches) {
      // DEBUG : Show the matches
      final ImageStack stack = new ImageStack(settings.imp.getWidth(), settings.imp.getHeight());
      stack.addSlice("12", new ByteProcessor(settings.imp.getWidth(), settings.imp.getHeight()));
      stack.addSlice("13", new ByteProcessor(settings.imp.getWidth(), settings.imp.getHeight()));
      stack.addSlice("23", new ByteProcessor(settings.imp.getWidth(), settings.imp.getHeight()));
      final Overlay ov = new Overlay();
      add(ov, matches12, 1);
      add(ov, matches13, 2);
      add(ov, matches23, 3);
      ImageJUtils.display(TITLE, stack).setOverlay(ov);
    }

    // Find triplets with mutual closest neighbours
    settings.triplets = new ArrayList<>();
    for (final PointPair pair12 : matches12) {
      final int id1 = ((AssignedPoint) pair12.getPoint1()).id;
      final int id2 = ((AssignedPoint) pair12.getPoint2()).id;

      // Find match in channel 3
      int id3 = -1;
      for (final PointPair pair13 : matches13) {
        if (id1 == ((AssignedPoint) pair13.getPoint1()).id) {
          id3 = ((AssignedPoint) pair13.getPoint2()).id;
          break;
        }
      }

      if (id3 != -1) {
        // Find if the same pair match in channel 23
        for (final PointPair pair23 : matches23) {
          if (id2 == ((AssignedPoint) pair23.getPoint1()).id) {
            if (id3 == ((AssignedPoint) pair23.getPoint2()).id) {
              // Add an extra int to store the classification
              settings.triplets.add(new int[] {id1, id2, id3, UNKNOWN});
            }
            break;
          }
        }
      }
    }

    // Table of results
    final TextWindow tw = createResultsWindowAndName();

    int count = 0;
    for (final int[] triplet : settings.triplets) {
      count++;
      addResult(tw, count, triplet);
    }

    overlayTriplets(settings);
  }

  private static boolean is3D(AssignedPoint[] foci) {
    final int z = foci[0].z;
    for (int i = 1; i < foci.length; i++) {
      if (foci[i].z != z) {
        return true;
      }
    }
    return false;
  }

  private static void add(Overlay ov, List<PointPair> matches, int position) {
    for (final PointPair pair : matches) {
      final AssignedPoint p1 = (AssignedPoint) pair.getPoint1();
      final AssignedPoint p2 = (AssignedPoint) pair.getPoint2();
      final Line line = new Line(p1.x, p1.y, p2.x, p2.y);
      line.setPosition(position);
      ov.add(line);
    }
  }

  /** The results name to use in the results window. */
  private String resultsName = "";

  private TextWindow createResultsWindowAndName() {
    // Get the name.
    // We are expecting FindFoci to be run on 3 channels of the same image:
    // 1=ImageTitle (c1,t1)
    // 2=ImageTitle (c2,t1)
    // 3=ImageTitle (c3,t1)
    // Look for this and then output:
    // ImageTitle (c1,t1); (c2,t1); (c3,t1)

    final int len = MathUtils.min(settings.resultsName1.length(), settings.resultsName2.length(),
        settings.resultsName3.length());
    int index = 0;
    while (index < len) {
      // Find common prefix
      if (settings.resultsName1.charAt(index) != settings.resultsName2.charAt(index)
          || settings.resultsName1.charAt(index) != settings.resultsName3.charAt(index)
          || settings.resultsName1.charAt(index) == '(') {
        break;
      }
      index++;
    }
    // Common prefix plus the FindFoci suffix
    resultsName = settings.resultsName1 + "; " + settings.resultsName2.substring(index).trim()
        + "; " + settings.resultsName3.substring(index).trim();

    return createResultsWindow();
  }

  private static TextWindow createResultsWindow() {
    return ImageJUtils.refresh(resultsWindow, () -> {
      final TextWindow window =
          new TextWindow(TITLE + " Results", createResultsHeader(), "", 1000, 300);

      // Allow the results to be manually changed
      window.getTextPanel().addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent event) {
          if (event.getClickCount() < 2) {
            return;
          }

          TextPanel tp = null;
          if (event.getSource() instanceof TextPanel) {
            tp = (TextPanel) event.getSource();
          } else if (event.getSource() instanceof Canvas
              && ((Canvas) event.getSource()).getParent() instanceof TextPanel) {
            tp = (TextPanel) ((Canvas) event.getSource()).getParent();
          } else {
            return;
          }

          final String line = tp.getLine(tp.getSelectionStart());
          final String[] fields = line.split("\t");
          final GenericDialog gd = new GenericDialog(TITLE + " Results Update");

          // Get the current classification: label = count + classification char
          final String label = fields[fields.length - 1];
          int index = 0;
          final char c = label.charAt(label.length() - 1);
          while (index < CLASSIFICATION.length && CLASSIFICATION[index].charAt(0) != c) {
            index++;
          }
          if (index == CLASSIFICATION.length) {
            index = 0;
          }

          // Prompt the user to change it
          gd.addMessage("Update the classification for " + label);
          gd.addChoice("Class", CLASSIFICATION, CLASSIFICATION[index]);
          gd.showDialog();
          if (gd.wasCanceled()) {
            return;
          }
          final int newIndex = gd.getNextChoiceIndex();
          final boolean noChange = (newIndex == index);
          index = newIndex;

          // Update the table fields so we capture the manual edit
          final String sCount = label.substring(0, label.length() - 1);
          fields[fields.length - 3] = "Manual";
          fields[fields.length - 2] = CLASSIFICATION[index];
          fields[fields.length - 1] = sCount + CLASSIFICATION[index].charAt(0);
          final StringBuilder sb = new StringBuilder(fields[0]);
          for (int i = 1; i < fields.length; i++) {
            sb.append('\t').append(fields[i]);
          }
          tp.setLine(tp.getSelectionStart(), sb.toString());

          // Update the overlay if we can
          if (noChange) {
            return;
          }
          // Get the latest settings (results may have changed since the text
          // window was constructed)
          final Settings latestSettings = Settings.load();
          if (latestSettings.imp == null && latestSettings.manualImp == null) {
            return;
          }
          if (latestSettings.triplets.isEmpty() && latestSettings.manualTriplets.isEmpty()) {
            return;
          }

          // Get the triplet count from the label
          int count = 0;
          try {
            count = Integer.parseInt(sCount);
          } catch (final NumberFormatException ex) {
            return;
          }
          if (count == 0) {
            return;
          }

          if (count > 0) {
            // Triplet added by the plugin
            if (latestSettings.triplets.size() < count) {
              return;
            }

            // Find if the selection is from the current set of triplets
            final int[] triplet = latestSettings.triplets.get(count - 1);
            final AssignedPoint p1 = latestSettings.foci1[triplet[0]];
            final AssignedPoint p2 = latestSettings.foci2[triplet[1]];
            final AssignedPoint p3 = latestSettings.foci3[triplet[2]];
            if (p1.x != Integer.parseInt(fields[1]) || p1.y != Integer.parseInt(fields[2])
                || p1.z != Integer.parseInt(fields[3]) || p2.x != Integer.parseInt(fields[4])
                || p2.y != Integer.parseInt(fields[5]) || p2.z != Integer.parseInt(fields[6])
                || p3.x != Integer.parseInt(fields[7]) || p3.y != Integer.parseInt(fields[8])
                || p3.z != Integer.parseInt(fields[9])) {
              return;
            }
            triplet[3] = index;
          } else {
            // Manual triplet
            count = -count;
            if (latestSettings.manualTriplets.size() < count) {
              return;
            }

            // Find if the selection is from the current set of manual triplets
            final AssignedPoint[] triplet = latestSettings.manualTriplets.get(count - 1);
            final AssignedPoint p1 = triplet[0];
            final AssignedPoint p2 = triplet[1];
            final AssignedPoint p3 = triplet[2];
            if (p1.x != Integer.parseInt(fields[1]) || p1.y != Integer.parseInt(fields[2])
                || p1.z != Integer.parseInt(fields[3]) || p2.x != Integer.parseInt(fields[4])
                || p2.y != Integer.parseInt(fields[5]) || p2.z != Integer.parseInt(fields[6])
                || p3.x != Integer.parseInt(fields[7]) || p3.y != Integer.parseInt(fields[8])
                || p3.z != Integer.parseInt(fields[9])) {
              return;
            }
            triplet[0].id = index;
          }

          overlayTriplets(latestSettings);
        }
      });

      return window;
    });
  }

  private static String createResultsHeader() {
    final StringBuilder sb = new StringBuilder();
    sb.append("Name");
    sb.append("\t1x\t1y\t1z");
    sb.append("\t2x\t2y\t2z");
    sb.append("\t3x\t3y\t3z");
    sb.append("\tD12");
    sb.append("\tD13");
    sb.append("\tD23");
    sb.append("\tMode");
    sb.append("\tClass");
    sb.append("\tLabel");
    return sb.toString();
  }

  /**
   * Adds the result.
   *
   * @param resultsWindow the results window
   * @param count the count
   * @param triplet the triplet
   */
  private void addResult(TextWindow resultsWindow, int count, int[] triplet) {
    final AssignedPoint p1 = settings.foci1[triplet[0]];
    final AssignedPoint p2 = settings.foci2[triplet[1]];
    final AssignedPoint p3 = settings.foci3[triplet[2]];
    triplet[3] = addResult(resultsWindow, count, resultsName, p1, p2, p3, settings.minDistance,
        settings.factor);
  }

  /**
   * Adds the result.
   *
   * @param count the count
   * @param name the name
   * @param p1 the first point
   * @param p2 the second point
   * @param p3 the third point
   * @param minDistance the min distance
   * @param factor the factor
   * @return the classification
   */
  private static int addResult(TextWindow resultsWindow, int count, String name, AssignedPoint p1,
      AssignedPoint p2, AssignedPoint p3, double minDistance, double factor) {
    return addResult(resultsWindow, count, name, p1, p2, p3, -1, minDistance, factor);
  }

  /**
   * Adds the result.
   *
   * @param resultsWindow the results window
   * @param count the count
   * @param name the name
   * @param p1 the first point
   * @param p2 the second point
   * @param p3 the third point
   * @param classification the classification (set to -1 to auto compute)
   * @param minDistance the min distance
   * @param factor the factor
   * @return the classification
   */
  private static int addResult(TextWindow resultsWindow, int count, String name, AssignedPoint p1,
      AssignedPoint p2, AssignedPoint p3, int classification, double minDistance, double factor) {
    final StringBuilder sb = new StringBuilder();
    sb.append(name);
    addTriplet(sb, p1);
    addTriplet(sb, p2);
    addTriplet(sb, p3);
    final double d12 = p1.distanceXyz(p2);
    final double d13 = p1.distanceXyz(p3);
    final double d23 = p2.distanceXyz(p3);
    sb.append('\t').append(MathUtils.rounded(d12));
    sb.append('\t').append(MathUtils.rounded(d13));
    sb.append('\t').append(MathUtils.rounded(d23));

    // Compute classification
    if (classification >= CLASSIFICATION.length || classification < 0) {
      classification = 0;
      // Is foci 3 separated from foci 1 & 2?
      if (isSeparated(d12, d13, d23, factor)) {
        classification = NO_TRANSLOCATION;

        // Is foci 2 separated from foci 1 & 3?
        // Note here the parameter order is deliberately changed to check for a translocation.
      } else if (isSeparated(d13, d12, d23, minDistance, factor)) {
        classification = TRANSLOCATION;
      }
      sb.append("\tAuto");
    } else {
      sb.append("\tManual");
    }
    sb.append('\t').append(CLASSIFICATION[classification]);
    sb.append('\t').append(count).append(CLASSIFICATION[classification].charAt(0));
    resultsWindow.append(sb.toString());
    return classification;
  }

  private static void addTriplet(StringBuilder sb, AssignedPoint point) {
    sb.append('\t').append(point.x);
    sb.append('\t').append(point.y);
    sb.append('\t').append(point.z);
  }

  /**
   * Check if distance 12 is much smaller than distance 13 and 23. It must be a given factor smaller
   * than the other two distances. i.event. foci 3 is separated from foci 1 and 2.
   *
   * @param d12 the distance 12
   * @param d13 the distance 13
   * @param d23 the distance 23
   * @param factor the factor
   * @return true, if successful
   */
  private static boolean isSeparated(double d12, double d13, double d23, double factor) {
    return d13 / d12 > factor && d23 / d12 > factor;
  }

  /**
   * Check if distance 12 is much smaller than distance 13 and 23. It must be a given factor smaller
   * than the other two distances. The other two distances must also be above the min distance
   * threshold, i.e. foci 3 is separated from foci 1 and 2.
   *
   * @param d12 the distance 12
   * @param d13 the distance 13
   * @param d23 the distance 23
   * @param minDistance the min distance
   * @param factor the factor
   * @return true, if successful
   */
  private static boolean isSeparated(double d12, double d13, double d23, double minDistance,
      double factor) {
    return d13 > minDistance && d23 > minDistance && d13 / d12 > factor && d23 / d12 > factor;
  }

  /**
   * Overlay triplets on image.
   */
  private static void overlayTriplets(Settings settings) {
    Overlay overaly = null;
    if (settings.imp != null) {
      overaly = new Overlay();
      int count = 0;
      for (final int[] triplet : settings.triplets) {
        count++;
        final AssignedPoint p1 = settings.foci1[triplet[0]];
        final AssignedPoint p2 = settings.foci2[triplet[1]];
        final AssignedPoint p3 = settings.foci3[triplet[2]];
        addTripletToOverlay(count, overaly, p1, p2, p3, triplet[3]);
      }
      settings.imp.setOverlay(overaly);
    }
    if (settings.manualImp != null) {
      // New overlay if the two images are different
      if (overaly == null || (settings.imp.getID() != settings.manualImp.getID())) {
        overaly = new Overlay();
      }
      int count = 0;
      for (final AssignedPoint[] triplet : settings.manualTriplets) {
        count--;
        final AssignedPoint p1 = triplet[0];
        final AssignedPoint p2 = triplet[1];
        final AssignedPoint p3 = triplet[2];
        // We store the classification in the id of the first point
        addTripletToOverlay(count, overaly, p1, p2, p3, triplet[0].id);
      }
      settings.manualImp.setOverlay(overaly);
    }
  }

  private static void addTripletToOverlay(int count, Overlay overlay, AssignedPoint p1,
      AssignedPoint p2, AssignedPoint p3, int classification) {
    final float[] x = new float[3];
    final float[] y = new float[3];
    x[0] = p1.x;
    x[1] = p2.x;
    x[2] = p3.x;
    y[0] = p1.y;
    y[1] = p2.y;
    y[2] = p3.y;
    final PolygonRoi roi = new PolygonRoi(x, y, 3, Roi.POLYGON);
    Color color;
    switch (classification) {
      case TRANSLOCATION:
        color = Color.CYAN;
        break;
      case NO_TRANSLOCATION:
        color = Color.MAGENTA;
        break;
      case UNKNOWN:
      default:
        color = Color.YELLOW;
    }
    roi.setStrokeColor(color);
    overlay.add(roi);

    final TextRoi text = new TextRoi(MathUtils.max(x) + 1, MathUtils.min(y),
        Integer.toString(count) + CLASSIFICATION[classification].charAt(0));
    text.setStrokeColor(color);
    overlay.add(text);
  }

  /**
   * Provide a tool on the ImageJ toolbar that responds to a user clicking on the same image to
   * identify foci for potential translocations.
   */
  public static class TranslocationFinderPluginTool extends PlugInTool {
    private static final TranslocationFinderPluginTool toolInstance =
        new TranslocationFinderPluginTool();

    private final String[] items;
    private int imageId;
    private final int[] ox = new int[3];
    private final int[] oy = new int[3];
    private final int[] oz = new int[3];
    private int points;
    private boolean prompt = true;

    /**
     * Instantiates a new translocation finder plugin tool.
     */
    TranslocationFinderPluginTool() {
      items = Arrays.copyOf(CLASSIFICATION, CLASSIFICATION.length + 1);
      items[items.length - 1] = "Auto";
    }

    @Override
    public String getToolName() {
      return "Manual Translocation Finder Tool";
    }

    @Override
    public String getToolIcon() {
      return "Cf00o4233C0f0o6933C00foa644C000Ta508M";
    }

    @Override
    public void showOptionsDialog() {
      final Settings settings = Settings.load();
      final GenericDialog gd = new GenericDialog(TITLE + " Tool Options");
      gd.addNumericField("Min_distance", settings.minDistance, 2, 6, "pixels");
      gd.addNumericField("Factor", settings.factor, 2);
      gd.addCheckbox("Show_record_dialog", prompt);
      gd.showDialog();
      if (gd.wasCanceled()) {
        return;
      }
      settings.minDistance = gd.getNextNumber();
      settings.factor = gd.getNextNumber();
      settings.save();
      prompt = gd.getNextBoolean();
    }

    @Override
    public void mouseClicked(ImagePlus imp, MouseEvent event) {
      // Ensure rapid mouse click does not break things
      synchronized (this) {
        if (imageId != imp.getID()) {
          points = 0;
        }
        imageId = imp.getID();

        final ImageCanvas ic = imp.getCanvas();
        ox[points] = ic.offScreenX(event.getX());
        oy[points] = ic.offScreenY(event.getY());
        oz[points] = imp.getSlice();
        points++;

        // Draw points as an ROI.
        if (points < 3) {
          final PointRoi roi = new PointRoi(ox, oy, points);
          roi.setShowLabels(true);
          imp.setRoi(roi);
        } else {
          imp.setRoi((Roi) null);
          points = 0;

          // Default to Auto
          int classification = CLASSIFICATION.length;

          // Q. Ask user if they want to add the point?
          final Settings settings = Settings.load();
          if (prompt) {
            final GenericDialog gd = new GenericDialog(TITLE + " Tool");
            gd.addMessage("Record manual translocation");
            gd.addChoice("Class", items, items[classification]);
            gd.addNumericField("Min_distance", settings.minDistance, 2, 6, "pixels");
            gd.addNumericField("Factor", settings.factor, 2);
            gd.showDialog();
            if (gd.wasCanceled()) {
              return;
            }
            classification = gd.getNextChoiceIndex();
            settings.minDistance = gd.getNextNumber();
            settings.factor = gd.getNextNumber();
          }

          // If a new image for a triplet then reset the manual triplets for the overlay
          if (settings.manualImp == null || settings.manualImp.getID() != imp.getID()) {
            settings.manualTriplets = new ArrayList<>();
          }
          settings.manualImp = imp;
          settings.save();

          final TextWindow tw = createResultsWindow();
          final AssignedPoint p1 = new AssignedPoint(ox[0], oy[0], oz[0], 1);
          final AssignedPoint p2 = new AssignedPoint(ox[1], oy[1], oz[1], 2);
          final AssignedPoint p3 = new AssignedPoint(ox[2], oy[2], oz[2], 3);
          settings.manualTriplets.add(new AssignedPoint[] {p1, p2, p3});
          final int count = -settings.manualTriplets.size();
          classification = addResult(tw, count, imp.getTitle() + " (Manual)", p1, p2, p3,
              classification, settings.minDistance, settings.factor);
          p1.id = classification;
          overlayTriplets(settings);
        }
      }

      event.consume();
    }
  }

  /**
   * Initialise the manual translocation finder tool. This is to allow support for calling within
   * macro toolsets.
   */
  public static void addPluginTool() {
    // Add the tool
    Toolbar.addPlugInTool(TranslocationFinderPluginTool.toolInstance);
    IJ.showStatus("Added " + TITLE + " Tool");
  }
}
