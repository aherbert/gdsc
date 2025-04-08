/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2025 Alex Herbert
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
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.ImageCanvas;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.plugin.tool.PlugInTool;
import ij.process.ImageProcessor;
import ij.text.TextPanel;
import ij.text.TextWindow;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.core.utils.TextUtils;
import uk.ac.sussex.gdsc.ij.UsageTracker;

/**
 * Output the distances between the pair of spots from two channels at a user selected position.
 */
public class SpotPairDistance_PlugIn implements PlugIn {
  private static final String TITLE = "Spot Pair Distance";

  private static final String KEY_CHANNEL_1 = "gdsc.foci.spotpairdistance.channel_1";
  private static final String KEY_CHANNEL_2 = "gdsc.foci.spotpairdistance.channel_2";
  private static final String KEY_SEARCH_RANGE = "gdsc.foci.spotpairdistance.search_range";
  private static final String KEY_COM_RANGE = "gdsc.foci.spotpairdistance.com_range";
  private static final String KEY_REVERSE_ORIENTATION_LINE =
      "gdsc.foci.spotpairdistance.reverse_orientation_line";
  private static final String KEY_SHOW_DISTANCES = "gdsc.foci.spotpairdistance.show_distances";
  private static final String KEY_SHOW_SEARCH_REGION =
      "gdsc.foci.spotpairdistance.show_search_region";
  private static final String KEY_SHOW_COM_REGION = "gdsc.foci.spotpairdistance.show_com_region";
  private static final String KEY_SHOW_LINE = "gdsc.foci.spotpairdistance.show_line";
  private static final String KEY_SHOW_ORIENTATION_LINE =
      "gdsc.foci.spotpairdistance.show_orientation_line";
  private static final String KEY_ADD_TO_OVERLAY = "gdsc.foci.spotpairdistance.add_to_overlay";

  /**
   * All the work for this plugin is done with the plugin tool. It handles mouse click events from
   * an image.
   */
  private static class SpotPairDistancePluginTool extends PlugInTool {
    private static final SpotPairDistancePluginTool toolInstance = new SpotPairDistancePluginTool();

    private static AtomicReference<TextWindow> distancesWindow = new AtomicReference<>();

    private int channel1 = (int) Prefs.get(KEY_CHANNEL_1, 1);
    private int channel2 = (int) Prefs.get(KEY_CHANNEL_2, 2);
    private int searchRange = (int) Prefs.get(KEY_SEARCH_RANGE, 5);
    private int comRange = (int) Prefs.get(KEY_COM_RANGE, 2);
    private boolean reverseOrientationLine = Prefs.get(KEY_REVERSE_ORIENTATION_LINE, false);
    private boolean showDistances = Prefs.get(KEY_SHOW_DISTANCES, true);
    private boolean showSearchRegion = Prefs.get(KEY_SHOW_SEARCH_REGION, true);
    private boolean showComRegion = Prefs.get(KEY_SHOW_COM_REGION, true);
    private boolean showLine = Prefs.get(KEY_SHOW_LINE, true);
    private boolean showOrientationLine = Prefs.get(KEY_SHOW_ORIENTATION_LINE, true);
    private boolean addToOverlay = Prefs.get(KEY_ADD_TO_OVERLAY, true);

    boolean active = true;

    /** Flag set in mouse pressed and released in mouse released. */
    AtomicInteger dragging = new AtomicInteger();

    // Created in the MousePressed event
    int origX;
    int origY;
    int slice;
    int frame;
    Rectangle bounds;
    Rectangle r1 = new Rectangle();
    Rectangle r2 = new Rectangle();
    double[] com1;
    double[] com2;

    @Override
    public String getToolName() {
      return TITLE + " Tool";
    }

    @Override
    public String getToolIcon() {
      // A magenta line between a red and blue spot
      return "Cf0fL32daCf00o1055C00foc855";
    }

    @Override
    public synchronized void showOptionsDialog() {
      final GenericDialog gd = new GenericDialog(TITLE + " Tool Options");
      //@formatter:off
      gd.addMessage(
          TextUtils.wrap(
          "Click on a multi-channel image and the distance between the " +
          "center-of-mass of spots in two channels will be measured. " +
          "Drag from the clicked point to create an orientation for " +
          "relative XY distance.", 80));
      //@formatter:on

      if (!hasMultiChannelImage()) {
        gd.addMessage("Warning: Currently no multi-channel images are open.");
      }
      gd.addNumericField("Channel_1", channel1, 0);
      gd.addNumericField("Channel_2", channel2, 0);
      gd.addSlider("Search_range", 1, 10, searchRange);
      gd.addSlider("Centre_of_mass_range", 1, 10, comRange);
      gd.addCheckbox("Reverse_orientation_line", reverseOrientationLine);
      gd.addCheckbox("Show_distances", showDistances);
      gd.addCheckbox("Show_search_region", showSearchRegion);
      gd.addCheckbox("Show_com_region", showComRegion);
      gd.addCheckbox("Show_line", showLine);
      gd.addCheckbox("Show_orientation_line", showOrientationLine);
      gd.addCheckbox("Add_to_overlay", addToOverlay);
      gd.showDialog();
      if (gd.wasCanceled()) {
        return;
      }
      channel1 = (int) gd.getNextNumber();
      channel2 = (int) gd.getNextNumber();
      searchRange = (int) gd.getNextNumber();
      comRange = (int) gd.getNextNumber();
      comRange = MathUtils.clip(0, searchRange, comRange);
      reverseOrientationLine = gd.getNextBoolean();
      showDistances = gd.getNextBoolean();
      showSearchRegion = gd.getNextBoolean();
      showComRegion = gd.getNextBoolean();
      showLine = gd.getNextBoolean();
      showOrientationLine = gd.getNextBoolean();
      addToOverlay = gd.getNextBoolean();
      active = (channel1 != channel2 && searchRange > 0
          && (showDistances || showSearchRegion || showComRegion || showLine));
      Prefs.set(KEY_CHANNEL_1, channel1);
      Prefs.set(KEY_CHANNEL_2, channel2);
      Prefs.set(KEY_SEARCH_RANGE, searchRange);
      Prefs.set(KEY_COM_RANGE, comRange);
      Prefs.set(KEY_REVERSE_ORIENTATION_LINE, reverseOrientationLine);
      Prefs.set(KEY_SHOW_DISTANCES, showDistances);
      Prefs.set(KEY_SHOW_SEARCH_REGION, showSearchRegion);
      Prefs.set(KEY_SHOW_COM_REGION, showComRegion);
      Prefs.set(KEY_SHOW_LINE, showLine);
      Prefs.set(KEY_SHOW_ORIENTATION_LINE, showOrientationLine);
      Prefs.set(KEY_ADD_TO_OVERLAY, addToOverlay);
    }

    private static boolean hasMultiChannelImage() {
      for (final int id : ImageJUtils.getIdList()) {
        final ImagePlus imp = WindowManager.getImage(id);
        if (imp != null && imp.getNChannels() > 1) {
          return true;
        }
      }
      return false;
    }

    // --------------
    // Actions
    // --------------

    // Mouse press:
    // - Find the CoM in both channels
    // - Store press location

    // Mouse drag
    // - Draw a line ROI from the start location

    // Mouse released
    // - Compute relative orientation
    // - Output Euclidian distance and relative XY distance

    // Mouse clicked with modifier key
    // - Find Overlay components within the region and remove from Overlay and table

    // --------------

    @Override
    public void mousePressed(ImagePlus imp, MouseEvent event) {
      if (isRemoveEvent(event)) {
        return;
      }

      final int c = imp.getNChannels();
      if (!active || c == 1) {
        return;
      }

      // Mark this event as handled
      event.consume();

      // Ensure rapid mouse click / new options does not break things
      synchronized (this) {
        if (c < channel1 || c < channel2) {
          IJ.log(TITLE + " - ERROR: Image has fewer channels than those selected for analysis");
          return;
        }

        final ImageCanvas ic = imp.getCanvas();
        final int x = ic.offScreenX(event.getX());
        final int y = ic.offScreenY(event.getY());

        // Get the region bounds to search for maxima
        bounds = new Rectangle(x - searchRange, y - searchRange, 2 * searchRange + 1,
            2 * searchRange + 1).intersection(new Rectangle(imp.getWidth(), imp.getHeight()));
        if (bounds.width == 0 || bounds.height == 0) {
          return;
        }

        slice = imp.getZ();
        frame = imp.getFrame();

        final int i1 = imp.getStackIndex(channel1, slice, frame);
        final int i2 = imp.getStackIndex(channel2, slice, frame);

        final ImageStack stack = imp.getImageStack();
        final ImageProcessor ip1 = stack.getProcessor(i1);
        final ImageProcessor ip2 = stack.getProcessor(i2);

        // Get the maxima
        final int maxx = imp.getWidth();
        int m1 = bounds.y * maxx + bounds.x;
        int m2 = m1;
        for (int ys = 0; ys < bounds.height; ys++) {
          for (int xs = 0, i = (ys + bounds.y) * maxx + bounds.x; xs < bounds.width; xs++, i++) {
            if (ip1.getf(i) > ip1.getf(m1)) {
              m1 = i;
            }
            if (ip2.getf(i) > ip2.getf(m2)) {
              m2 = i;
            }
          }
        }

        // Find centre-of-mass around each maxima
        com1 = com(ip1, m1, r1);
        com2 = com(ip2, m2, r2);

        // Store the actual pixel position so it is clear when the mouse has
        // been dragged to a new position.
        origX = event.getX();
        origY = event.getY();

        // Add the overlay visuals
        if (showSearchRegion || showComRegion || showLine) {
          Overlay overlay = null;
          if (addToOverlay) {
            overlay = imp.getOverlay();
          }
          if (overlay == null) {
            overlay = new Overlay();
          }

          if (showSearchRegion) {
            overlay.add(createRoi(bounds, Color.magenta));
          }
          if (showComRegion) {
            overlay.add(createRoi(r1, Color.red));
            overlay.add(createRoi(r2, Color.blue));
          }
          if (showLine) {
            overlay.add(createLine(com1[0], com1[1], com2[0], com2[1], Color.magenta));
          }
          imp.setOverlay(overlay);
        }

        // Initiate dragging
        dragging.set(1);
      }
    }

    private static boolean isRemoveEvent(MouseEvent event) {
      return event.isAltDown() || event.isShiftDown() || event.isControlDown();
    }

    private double[] com(ImageProcessor ip, int index, Rectangle rectangle) {
      final int x = index % ip.getWidth();
      final int y = index / ip.getWidth();

      // Make range +/- equal
      int rx = comRange;
      if (x + comRange >= ip.getWidth()) {
        rx = ip.getWidth() - x - 1;
      } else if (x - comRange < 0) {
        rx = x;
      }
      int ry = comRange;
      if (y + comRange >= ip.getHeight()) {
        ry = ip.getHeight() - y - 1;
      } else if (y - comRange < 0) {
        ry = y;
      }
      final int mx = x - rx;
      rx = 2 * rx + 1;
      final int my = y - ry;
      ry = 2 * ry + 1;

      rectangle.x = mx;
      rectangle.width = rx;
      rectangle.y = my;
      rectangle.height = ry;

      double cx = 0;
      double cy = 0;
      double sum = 0;
      for (int ys = 0; ys < ry; ys++) {
        double sumX = 0;
        for (int xs = 0, i = (ys + my) * ip.getWidth() + mx; xs < rx; xs++, i++) {
          final float f = ip.getf(i);
          sumX += f;
          cx += f * xs;
        }
        sum += sumX;
        cy += sumX * ys;
      }

      if (sum == 0) {
        return new double[] {x + 0.5, y + 0.5};
      }

      // Find centre with 0.5 as the centre of the pixel
      cx = 0.5 + cx / sum;
      cy = 0.5 + cy / sum;
      return new double[] {mx + cx, my + cy};
    }

    private static Roi createRoi(Rectangle bounds, Color color) {
      final Roi roi = new Roi(bounds);
      roi.setStrokeColor(color);
      return roi;
    }

    private static Roi createLine(double x1, double y1, double x2, double y2, Color color) {
      final Line roi = new Line(x1, y1, x2, y2);
      roi.setStrokeColor(color);
      return roi;
    }

    @Override
    public void mouseDragged(ImagePlus imp, MouseEvent event) {
      if (dragging.get() == 0) {
        return;
      }
      event.consume();

      // Ensure fields written in a synchronized block are read in a synchronized block
      synchronized (this) {
        // Only a drag if the mouse has moved position
        if (origX != event.getX() || origY != event.getY()) {
          final ImageCanvas ic = imp.getCanvas();
          final double x = ic.offScreenXD(event.getX());
          final double y = ic.offScreenYD(event.getY());

          // - Draw a line ROI from the start location (it may be reversed)
          final double[] line = createOrientationLine(com1[0], com1[1], x, y);
          imp.setRoi(createLine(line[0], line[1], line[2], line[3], Color.yellow));
          dragging.incrementAndGet();
        }
      }
    }

    @Override
    public void mouseReleased(ImagePlus imp, MouseEvent event) {
      if (dragging.get() == 0) {
        return;
      }

      event.consume();

      // Note: ImageJ bug
      // ImageCanvas.activateOverlayRoi() may set the image ROI (if currently null)
      // using the first ROI that contains a mouseReleased event if >250 milliseconds
      // have elapsed from the mousePressed event. Nothing can currently be done to
      // stop this since it ignores the consumed flag.

      synchronized (this) {
        // Halt dragging (set to zero) but check if a drag did occur
        // (incremented in the mouseDragged method).
        final boolean hasDragged = dragging.getAndSet(0) > 1;

        double[] line = null;
        if (hasDragged) {
          // Remove drag line
          imp.killRoi();

          final ImageCanvas ic = imp.getCanvas();
          final double x = ic.offScreenXD(event.getX());
          final double y = ic.offScreenYD(event.getY());
          line = createOrientationLine(com1[0], com1[1], x, y);

          if (showOrientationLine) {
            Overlay overlay = null;
            if (addToOverlay) {
              overlay = imp.getOverlay();
            }
            if (overlay == null) {
              overlay = new Overlay();
            }
            overlay.add(createLine(line[0], line[1], line[2], line[3], Color.yellow));
            imp.setOverlay(overlay);
          }
        }

        // - Output Euclidian distance and relative XY distance
        if (showDistances) {
          // For relative orientation
          double angle = 0;
          double relX = 0;
          double relY = 0;
          if (line != null) {
            // - Compute relative orientation
            // Both vectors are are centred on com1. The drag end point
            // specifies the vector direction origin not the end.
            final double[] v1 = createVector(line[2], line[3], line[0], line[1]);
            final double[] v2 = createVector(com2[0], com2[1], com1[0], com1[1]);

            // Requires the vectors to have a length.
            final double l1 = normalise(v1);
            final double l2 = normalise(v2);
            if (l1 != 0 && l2 != 0) {
              final double dot = v1[0] * v2[0] + v1[1] * v2[1];
              angle = Math.toDegrees(Math.acos(dot));
              // Scalar projection of v2 in direction of v1.
              // This is the relative X component
              relX = dot * l2;
              // The relative Y component is determined using Pythagoras' rule
              relY = Math.sqrt(l2 * l2 - relX * relX);
              // Compute sign to get an orientation
              if (angle != 0) {
                final double sign = Math.signum(v1[0] * v2[1] - v1[1] * v2[0]);
                angle *= sign;
                relY *= -sign; // Positive rotation makes y below the x-axis direction
              }
            }
          }

          addDistanceResult(imp, slice, frame, bounds, com1, com2, angle, relX, relY);
        }
      }
    }

    private static double[] createVector(double x1, double y1, double x2, double y2) {
      final double x = x1 - x2;
      final double y = y1 - y2;
      return new double[] {x, y};
    }

    /**
     * Creates the orientation line.
     *
     * @param x1 the CoM x
     * @param y1 the CoM y
     * @param x2 the mouse position x
     * @param y2 the mouse position y
     * @return [startX,startY,endX,endY]
     */
    private double[] createOrientationLine(double x1, double y1, double x2, double y2) {
      if (reverseOrientationLine) {
        x2 = 2 * x1 - x2; // == x1 -(x2-x1)
        y2 = 2 * y1 - y2; // == y1 -(y2-y1)
        return new double[] {x1, y1, x2, y2};
      }
      return new double[] {x1, y1, x2, y2};
    }

    private static double normalise(double[] vector) {
      final double x = vector[0];
      final double y = vector[1];
      // Normalise
      final double length = Math.sqrt(x * x + y * y);
      if (length != 0) {
        vector[0] /= length;
        vector[1] /= length;
      }
      return length;
    }

    private static String createDistancesHeader() {
      final StringBuilder sb = new StringBuilder();
      sb.append("Image\t");
      sb.append("Ch 1\t");
      sb.append("Ch 2\t");
      sb.append("Z\t");
      sb.append("T\t");
      sb.append("Region\t");
      sb.append("X1 (px)\tY1 (px)\t");
      sb.append("X2 (px)\tY2 (px)\t");
      sb.append("Distance (px)\t");
      sb.append("Relative Angle\t");
      sb.append("Rel X (px)\tRel Y (px)\t");
      sb.append("X1\tY1\t");
      sb.append("X2\tY2\t");
      sb.append("Distance\t");
      sb.append("Rel X\tRel Y\t");
      sb.append("Units");
      return sb.toString();
    }

    private synchronized void addDistanceResult(ImagePlus imp, int slice, int frame,
        Rectangle bounds, double[] com1, double[] com2, double angle, double relX, double relY) {
      // Create the result window (if it is not available).
      final TextWindow window = ImageJUtils.refresh(distancesWindow,
          () -> new TextWindow(TITLE + " Distances", createDistancesHeader(), "", 700, 300));

      final StringBuilder sb = new StringBuilder();
      sb.append(imp.getTitle()).append('\t');
      sb.append(channel1).append('\t');
      sb.append(channel2).append('\t');
      sb.append(slice).append('\t');
      sb.append(frame).append('\t');
      sb.append(bounds.x).append(',');
      sb.append(bounds.y).append(' ');
      sb.append(bounds.width).append('x');
      sb.append(bounds.height);

      sb.append('\t').append(MathUtils.rounded(com1[0]));
      sb.append('\t').append(MathUtils.rounded(com1[1]));
      sb.append('\t').append(MathUtils.rounded(com2[0]));
      sb.append('\t').append(MathUtils.rounded(com2[1]));
      double dx = com1[0] - com2[0];
      double dy = com1[1] - com2[1];
      double distance = Math.sqrt(dx * dx + dy * dy);
      sb.append('\t').append(MathUtils.rounded(distance));
      sb.append('\t').append(MathUtils.rounded(angle));
      sb.append('\t').append(MathUtils.rounded(relX));
      sb.append('\t').append(MathUtils.rounded(relY));

      final Calibration cal = imp.getCalibration();
      final String unit = cal.getUnit();
      // Only if matching units and pixel scaling
      if (cal.getYUnit().equals(unit) && (cal.pixelWidth != 1.0 || cal.pixelHeight != 1.0)) {
        sb.append('\t').append(MathUtils.rounded(com1[0] * cal.pixelWidth));
        sb.append('\t').append(MathUtils.rounded(com1[1] * cal.pixelHeight));
        sb.append('\t').append(MathUtils.rounded(com2[0] * cal.pixelWidth));
        sb.append('\t').append(MathUtils.rounded(com2[1] * cal.pixelHeight));
        dx *= cal.pixelWidth;
        dy *= cal.pixelHeight;
        distance = Math.sqrt(dx * dx + dy * dy);
        sb.append('\t').append(MathUtils.rounded(distance));
        // Units must be the same for calibrated relative distance
        if (cal.pixelWidth == cal.pixelHeight) {
          sb.append('\t').append(MathUtils.rounded(relX * cal.pixelWidth));
          sb.append('\t').append(MathUtils.rounded(relY * cal.pixelWidth));
        } else {
          sb.append("'\t-\t-");
        }
        sb.append('\t').append(unit);
      }
      window.append(sb.toString());
    }

    @Override
    public void mouseClicked(ImagePlus imp, MouseEvent event) {
      final int c = imp.getNChannels();
      if (!active || c == 1) {
        return;
      }

      if (!isRemoveEvent(event)) {
        return;
      }

      // Mark this event as handled
      event.consume();

      // Ensure rapid mouse click / new options does not break things
      synchronized (this) {
        // Option to remove the result
        final ImageCanvas ic = imp.getCanvas();
        final int x = ic.offScreenX(event.getX());
        final int y = ic.offScreenY(event.getY());

        // Get the region bounds to search for maxima
        final Rectangle searchBounds =
            new Rectangle(x - searchRange, y - searchRange, 2 * searchRange + 1,
                2 * searchRange + 1).intersection(new Rectangle(imp.getWidth(), imp.getHeight()));
        if (searchBounds.width == 0 || searchBounds.height == 0) {
          return;
        }

        // Remove all the overlay components
        Overlay overlay = imp.getOverlay();
        if (overlay != null) {
          final Roi[] rois = overlay.toArray();
          overlay = new Overlay();
          for (int i = 0; i < rois.length; i++) {
            final Roi roi = rois[i];
            if (roi.isArea()) {
              final Rectangle r = roi.getBounds();
              if (r.intersects(searchBounds)) {
                continue;
              }
            } else if (roi instanceof Line) {
              final Line line = (Line) roi;
              if (searchBounds.contains(line.x1d, line.y1d)
                  || searchBounds.contains(line.x2d, line.y2d)) {
                continue;
              }
            }
            overlay.add(roi);
          }
          if (overlay.size() == 0) {
            imp.setOverlay(null);
          } else {
            imp.setOverlay(overlay);
          }

          // Note:
          // ImageCanvas.activateOverlayRoi() may set the image ROI using the first ROI
          // that contains a mousePressed/mouseReleased event. Check if it should be removed.
          final Roi impRoi = imp.getRoi();
          if (impRoi != null && searchBounds.intersects(impRoi.getBounds())) {
            imp.setRoi((Roi) null);
          }
        }

        // Remove any distances from this image
        final TextWindow window = distancesWindow.get();
        if (window != null && window.isShowing()) {
          final TextPanel tp = window.getTextPanel();
          final String title = imp.getTitle();
          for (int i = 0; i < tp.getLineCount(); i++) {
            final String line = tp.getLine(i);
            // Check the image name
            int startIndex = line.indexOf('\t');
            if (startIndex == -1 || !title.equals(line.substring(0, startIndex))) {
              continue;
            }

            // Find the start of the region column
            for (int j = 4; j-- > 0;) {
              startIndex = line.indexOf('\t', startIndex + 1);
              if (startIndex == -1) {
                break;
              }
            }
            if (startIndex == -1) {
              continue;
            }
            final int endIndex = line.indexOf('\t', startIndex + 1);
            if (endIndex == -1) {
              continue;
            }
            final String text = line.substring(startIndex + 1, endIndex);

            // Region is formatted as 'x,y wxh'
            final int commaIndex = text.indexOf(',');
            final int spaceIndex = text.indexOf(' ');
            final int xIndex = text.indexOf('x');
            if (commaIndex == -1 || spaceIndex < commaIndex || xIndex < spaceIndex) {
              continue;
            }
            try {
              final int xx = Integer.parseInt(text.substring(0, commaIndex));
              final int yy = Integer.parseInt(text.substring(commaIndex + 1, spaceIndex));
              final int w = Integer.parseInt(text.substring(spaceIndex + 1, xIndex));
              final int h = Integer.parseInt(text.substring(xIndex + 1));
              final Rectangle r = new Rectangle(xx, yy, w, h);
              if (r.intersects(searchBounds)) {
                tp.setSelection(i, i);
                tp.clearSelection();
                // Since i will be incremented for the next line,
                // decrement to check the current line again.
                i--;
              }
            } catch (final NumberFormatException ex) {
              // Ignore
            }
          }
        }
      }
    }
  }

  /**
   * Initialise the spot pair distance tool. This is to allow support for calling within macro
   * toolsets.
   */
  public static void addPluginTool() {
    // Add the tool
    Toolbar.addPlugInTool(SpotPairDistancePluginTool.toolInstance);
    IJ.showStatus("Added " + TITLE + " Tool");
  }

  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    addPluginTool();

    // Fiji restores the toolbar from the last session.
    // Do not show the options if this is happening.
    final ImageJ ij = IJ.getInstance();
    if (ij == null || !ij.isVisible()) {
      return;
    }

    SpotPairDistancePluginTool.toolInstance.showOptionsDialog();
  }
}
