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

package uk.ac.sussex.gdsc.foci;

import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.plugin.filter.EDM;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.text.TextPanel;
import ij.text.TextWindow;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Output the density around spots within a mask region. Spots are defined using FindFoci results
 * loaded from memory.
 *
 * <p>A mask must be defined using a freehand ROI or provided as an image. This is used to create a
 * 2D distance map of foci from the edge of the mask. Any foci within the maximum analysis distance
 * from the edge are excluded from analysis.
 */
public class SpotDensity_PlugIn implements PlugIn {
  private static final String TITLE = "Spot Density";
  private static TextWindow resultsWindow;

  private static String resultsName1 = "";
  private static String resultsName2 = "";
  private static String maskImage = "";
  private static double distance = 15;
  private static double interval = 1.5;

  private static ArrayList<PairCorrelation> results = new ArrayList<>();

  private static class PairCorrelation {
    /** The number of points. */
    final int numberOfPoints;
    /** The area. */
    final int area;
    /** The radii for the concentric rings around the point. */
    final double[] radii;
    /** The pair correlation at each radius. */
    final double[] pc;

    PairCorrelation(int numberOfPoints, int area, double[] radii, double[] pc) {
      this.numberOfPoints = numberOfPoints;
      this.area = area;
      this.radii = radii;
      this.pc = pc;
    }
  }

  private static class Foci {
    final int id;
    final int x;
    final int y;

    Foci(int id, int x, int y) {
      this.id = id;
      this.x = x;
      this.y = y;
    }

    double distance2(Foci that) {
      final double dx = this.x - that.x;
      final double dy = this.y - that.y;
      return dx * dx + dy * dy;
    }
  }

  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    // List the foci results
    final String[] names = FindFoci_PlugIn.getResultsNames();
    if (names == null || names.length == 0) {
      IJ.error(TITLE,
          "Spots must be stored in memory using the " + FindFoci_PlugIn.TITLE + " plugin");
      return;
    }

    final ImagePlus imp = WindowManager.getCurrentImage();
    Roi roi = null;
    if (imp != null) {
      roi = imp.getRoi();
    }

    // Build a list of the open images for use as a mask
    final String[] maskImageList = buildMaskList(roi);

    if (maskImageList.length == 0) {
      IJ.error(TITLE, "No mask images and no area ROI on current image");
      return;
    }

    if (!showDialog(names, maskImageList)) {
      return;
    }

    final FloatProcessor fp = createDistanceMap(imp, maskImage);
    if (fp == null) {
      return;
    }

    // Get the foci
    final Foci[] foci1 = getFoci(resultsName1);
    final Foci[] foci2 = getFoci(resultsName2);

    if (foci1 == null || foci2 == null) {
      return;
    }

    analyse(foci1, foci2, resultsName1.equals(resultsName2), fp);
  }

  private static boolean showDialog(String[] names, String[] maskImageList) {
    final GenericDialog gd = new GenericDialog(TITLE);

    gd.addMessage(
        "Analyses spots within a mask/ROI region\nand computes density and closest distances.");

    gd.addChoice("Results_name_1", names, resultsName1);
    gd.addChoice("Results_name_2", names, resultsName2);
    gd.addChoice("Mask", maskImageList, maskImage);
    gd.addNumericField("Distance", distance, 2, 6, "pixels");
    gd.addNumericField("Interval", interval, 2, 6, "pixels");
    gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.FIND_FOCI);

    gd.showDialog();
    if (!gd.wasOKed()) {
      return false;
    }

    resultsName1 = gd.getNextChoice();
    resultsName2 = gd.getNextChoice();
    maskImage = gd.getNextChoice();
    distance = gd.getNextNumber();
    interval = gd.getNextNumber();
    return true;
  }

  private static String[] buildMaskList(Roi roi) {
    final ArrayList<String> newImageList = new ArrayList<>();
    if (roi != null && roi.isArea()) {
      newImageList.add("[ROI]");
    }
    newImageList.addAll(Arrays.asList(ImageJUtils.getImageList(ImageJUtils.GREY_8_16, null)));
    return newImageList.toArray(new String[newImageList.size()]);
  }

  private static FloatProcessor createDistanceMap(ImagePlus imp, String maskImage) {
    final ImagePlus maskImp = WindowManager.getImage(maskImage);

    ByteProcessor bp = null;
    if (maskImp == null) {
      // Build a mask image using the input image ROI
      final Roi roi = (imp == null) ? null : imp.getRoi();
      if (roi == null || !roi.isArea()) {
        IJ.error("No region defined (use an area ROI or an input mask)");
        return null;
      }
      bp = new ByteProcessor(imp.getWidth(), imp.getHeight());
      bp.setValue(255);
      bp.setRoi(roi);
      bp.fill(roi);
    } else {
      final ImageProcessor ip = maskImp.getProcessor();
      bp = new ByteProcessor(maskImp.getWidth(), maskImp.getHeight());
      for (int i = 0; i < bp.getPixelCount(); i++) {
        if (ip.get(i) != 0) {
          bp.set(i, 255);
        }
      }
    }

    // Create a distance map from the mask
    final EDM edm = new EDM();
    return edm.makeFloatEDM(bp, 0, true);
  }

  private static Foci[] getFoci(String resultsName) {
    final FindFociMemoryResults memoryResults = FindFoci_PlugIn.getResults(resultsName);
    if (memoryResults == null) {
      IJ.showMessage("Error", "No foci with the name " + resultsName);
      return null;
    }
    final List<FindFociResult> results = memoryResults.results;
    if (results.isEmpty()) {
      IJ.showMessage("Error", "Zero foci in the results with the name " + resultsName);
      return null;
    }
    final Foci[] foci = new Foci[results.size()];
    int id = 0;
    for (final FindFociResult result : results) {
      foci[id] = new Foci(id, result.x, result.y);
      id++;
    }
    return foci;
  }

  /**
   * For all foci in set 1, compare to set 2 and output a histogram of the average density around
   * each foci is concentric rings (pair correlation) and the minimum distance to another foci.
   *
   * <p>Foci too close to the edge of the analysis region are ignored from set 1.
   *
   * @param foci1 the foci 1
   * @param foci2 the foci 2
   * @param identical True if the two sets are the same foci (self comparisons will be ignored)
   * @param map the map containing the distance to the edge of the mask (analysis) region
   */
  private static void analyse(Foci[] foci1, Foci[] foci2, boolean identical, FloatProcessor map) {
    final int nbins = (int) (distance / interval) + 1;
    final double maxDistance2 = distance * distance;
    final int[] h1 = new int[nbins];
    final int[] h2 = new int[nbins];

    final double[] distances = new double[foci1.length];
    int count = 0;

    // Update the second set to foci inside the mask (analysis region)
    int n2 = 0;
    for (int j = 0; j < foci2.length; j++) {
      final Foci m2 = foci2[j];
      if (map.getPixelValue(m2.x, m2.y) != 0) {
        foci2[n2++] = m2;
      }
    }

    int n1 = 0;
    for (int i = foci1.length; i-- > 0;) {
      final Foci m = foci1[i];
      // Ignore molecules that are near the edge of the analysis region
      if (map.getPixelValue(m.x, m.y) < distance) {
        continue;
      }
      n1++;

      double min = Double.POSITIVE_INFINITY;
      for (int j = n2; j-- > 0;) {
        final Foci m2 = foci2[j];

        if (identical && m.id == m2.id) {
          continue;
        }

        final double d2 = m.distance2(m2);
        if (d2 < maxDistance2) {
          h1[(int) (Math.sqrt(d2) / interval)]++;
        }
        if (d2 < min) {
          min = d2;
        }
      }

      if (min != Double.POSITIVE_INFINITY) {
        min = Math.sqrt(min);
        if (min < distance) {
          h2[(int) (min / interval)]++;
        }
        distances[count++] = min;
      }
    }

    double[] radii = new double[nbins + 1];
    for (int i = 0; i <= nbins; i++) {
      radii[i] = i * interval;
    }
    double[] pc = new double[nbins];
    final double[] dMin = new double[nbins];
    if (n1 > 0) {
      final double n1pi = n1 * Math.PI;
      for (int i = 0; i < nbins; i++) {
        // Pair-correlation is the count at the given distance divided by N (the number of items
        // analysed) and the area at distance ri:
        // H[i] / (N x (pi x (r_i+1)^2 - pi x r_i^2))
        pc[i] = h1[i] / (n1pi * (radii[i + 1] * radii[i + 1] - radii[i] * radii[i]));
        // Convert to double for plotting
        dMin[i] = h2[i];
      }
    }

    // Truncate the unused r for the plot
    radii = Arrays.copyOf(radii, nbins);
    final Plot plot1 = new Plot(TITLE + " Min Distance", "Distance (px)", "Frequency", radii, dMin);
    final PlotWindow pw1 = ImageJUtils.display(TITLE + " Min Distance", plot1);

    // The final bin may be empty if the correlation interval was a factor of the correlation
    // distance
    if (pc[pc.length - 1] == 0) {
      radii = Arrays.copyOf(radii, nbins - 1);
      pc = Arrays.copyOf(pc, nbins - 1);
    }

    // Get the pixels in the entire mask
    int area = 0;
    final float[] distanceMap = (float[]) map.getPixels();
    for (int i = distanceMap.length; i-- > 0;) {
      if (distanceMap[i] != 0) {
        area++;
      }
    }

    final double avDensity = (double) n2 / area;

    // Normalisation of the density chart to produce the pair correlation.
    // Get the maximum response for the summary.
    double max = 0;
    double maxr = 0;
    for (int i = 0; i < pc.length; i++) {
      pc[i] /= avDensity;
      if (max < pc[i]) {
        max = pc[i];
        maxr = radii[i];
      }
    }

    // Store the result
    final PairCorrelation pairCorrelation = new PairCorrelation(n2, area, radii, pc);
    results.add(pairCorrelation);

    // Display
    final PlotWindow pw2 = showPairCorrelation(pairCorrelation);

    final Point p = pw1.getLocation();
    p.y += pw1.getHeight();
    pw2.setLocation(p);

    // Table of results
    createResultsWindow();
    final StringBuilder sb = new StringBuilder();
    sb.append(results.size());
    sb.append('\t').append(foci1.length);
    sb.append('\t').append(n1);
    sb.append('\t').append(foci2.length);
    sb.append('\t').append(n2);
    sb.append('\t').append(IJ.d2s(distance));
    sb.append('\t').append(IJ.d2s(interval));
    sb.append('\t').append(area);
    sb.append('\t').append(IJ.d2s(avDensity, -3));
    sb.append('\t').append(IJ.d2s(max, 3));
    sb.append('\t').append(IJ.d2s(maxr, 3));
    sb.append('\t').append(count);
    double sum = 0;
    for (int i = 0; i < count; i++) {
      sum += distances[i];
    }
    sb.append('\t').append(IJ.d2s(sum / count, 2));

    resultsWindow.append(sb.toString());
  }

  private static PlotWindow showPairCorrelation(PairCorrelation pc) {
    final double avDensity = (double) pc.numberOfPoints / pc.area;
    final String title = "Pair Correlation";
    final Plot plot2 = new Plot(TITLE + " " + title, "r (px)", "g(r)", pc.radii, pc.pc);
    plot2.setColor(Color.red);
    plot2.drawLine(pc.radii[0], 1, pc.radii[pc.radii.length - 1], 1);
    plot2.addLabel(0, 0, "Av.Density = " + IJ.d2s(avDensity, -3) + " px^-2");
    plot2.setColor(Color.blue);
    return ImageJUtils.display(TITLE + " " + title, plot2);
  }

  private static void createResultsWindow() {
    if (resultsWindow == null || !resultsWindow.isShowing()) {
      resultsWindow = new TextWindow(TITLE + " Summary", createResultsHeader(), "", 700, 300);

      // Allow clicking multiple results in the window to show a combined curve
      resultsWindow.getTextPanel().addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent event) {
          TextPanel tp = null;
          if (event.getSource() instanceof TextPanel) {
            tp = (TextPanel) event.getSource();
          } else if (event.getSource() instanceof Canvas
              && ((Canvas) event.getSource()).getParent() instanceof TextPanel) {
            tp = (TextPanel) ((Canvas) event.getSource()).getParent();
          } else {
            return;
          }

          final int[] ids = new int[results.size()];
          int count = 0;
          for (int i = tp.getSelectionStart(); i <= tp.getSelectionEnd(); i++) {
            final String line = tp.getLine(i);
            try {
              final String sid = line.substring(0, line.indexOf('\t'));
              final int id = Integer.parseInt(sid);
              if (id > 0 && id <= results.size()) {
                ids[count++] = id;
              }
            } catch (final Exception ex) {
              // Ignore for now
            }
          }

          if (count > 2) {
            // Ask the user which curves to combine since we may want to ignore some
            // between start and end
            final GenericDialog gd = new GenericDialog(TITLE);
            gd.addMessage("Select which curves to combine");

            int count2 = 0;
            final int rowLimit = 20;
            if (count <= rowLimit) {
              // Use checkboxes
              for (int i = 0; i < count; i++) {
                gd.addCheckbox("ID_" + ids[i], true);
              }
              gd.showDialog();
              if (gd.wasCanceled()) {
                return;
              }
              for (int i = 0; i < count; i++) {
                if (gd.getNextBoolean()) {
                  ids[count2++] = ids[i];
                }
              }
            } else {
              // Use a text area
              final StringBuilder sb = new StringBuilder(Integer.toString(ids[0]));
              for (int i = 1; i < count; i++) {
                sb.append("\n").append(ids[i]);
              }
              gd.addTextAreas(sb.toString(), null, rowLimit, 10);
              gd.showDialog();
              if (gd.wasCanceled()) {
                return;
              }
              for (final String token : gd.getNextText().split("[ \t\n\r]+")) {
                try {
                  final int id = Integer.parseInt(token);
                  if (id > 0 && id <= results.size()) {
                    ids[count2++] = id;
                  }
                } catch (final Exception ex) {
                  // Ignore for now
                }
              }
            }
            count = count2;
          }

          if (count == 0) {
            return;
          }

          // Check all curves are the same size and build an average
          PairCorrelation pairCorrelation = results.get(ids[0] - 1);
          final int length = pairCorrelation.radii.length;
          int size = pairCorrelation.numberOfPoints;
          int area = pairCorrelation.area;
          final double[] radii = pairCorrelation.radii;
          final double[] pcf = pairCorrelation.pc.clone();
          for (int i = 1; i < count; i++) {
            pairCorrelation = results.get(ids[i] - 1);
            if (length != pairCorrelation.radii.length) {
              return;
            }
            size += pairCorrelation.numberOfPoints;
            area += pairCorrelation.area;
            for (int j = 0; j < length; j++) {
              // Distance scale must be the same!
              if (radii[j] != pairCorrelation.radii[j]) {
                return;
              }
              pcf[j] += pairCorrelation.pc[j];
            }
          }
          for (int j = 0; j < length; j++) {
            pcf[j] /= count;
          }

          showPairCorrelation(new PairCorrelation(size, area, radii, pcf));
        }
      });
    }
  }

  private static String createResultsHeader() {
    final StringBuilder sb = new StringBuilder();
    sb.append("ID");
    sb.append("\tN1");
    sb.append("\tN1_internal");
    sb.append("\tN2");
    sb.append("\tN2_selection");
    sb.append("\tDistance");
    sb.append("\tInterval");
    sb.append("\tArea");
    sb.append("\tAv. Density");
    sb.append("\tMax g(r)");
    sb.append("\tr");
    sb.append("\tCount Distances");
    sb.append("\tAv. Distance");
    return sb.toString();
  }
}
