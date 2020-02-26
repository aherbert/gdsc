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

package uk.ac.sussex.gdsc.utils;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.plugin.LutLoader;
import ij.plugin.ZProjector;
import ij.plugin.filter.EDM;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.FloatBlitter;
import ij.process.FloatPolygon;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.IndexColorModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.math3.analysis.MultivariateVectorFunction;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer.Optimum;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresProblem;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.linear.DiagonalMatrix;
import org.apache.commons.math3.optim.ConvergenceChecker;
import org.apache.commons.math3.optim.PointVectorValuePair;
import org.apache.commons.math3.optim.SimplePointChecker;
import org.apache.commons.math3.util.Precision;
import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.annotation.Nullable;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.utils.MathUtils;

/**
 * Outlines a circular cell using the optimal path through a membrane scoring map.
 *
 * <p>The centre of the cell must be chosen and an approximate cell radius. Only pixels within a
 * range of the cell radius are processed. The score map is created using 2D convolutions of a curve
 * through a NxN square. The curve is constructed as a difference-of-Gaussians approximation to the
 * Laplacian allowing dark/light edge detection. The curve arc uses the cell radius and is rotated
 * to produce membrane edge projections around the centre point.
 *
 * <p>The maximum intensity membrane projection is weighted using the current elliptical fit of the
 * cell (initialised as a circle using the cell radius). A polygon outline is constructed from the
 * projection using the highest value in each 10 degree segment around the selected centre. An
 * elliptical shape consisting of two ellipses back-to-back (allowing egg-like shapes) is then
 * fitted to the polygon outline and the process iterated.
 */
public class CellOutliner_PlugIn implements ExtendedPlugInFilter, DialogListener {
  private static final int FLAGS = DOES_8G + DOES_16 + DOES_32 + NO_CHANGES;

  private static final int[] DIR_X_OFFSET = {0, 1, 1, 1, 0, -1, -1, -1};
  private static final int[] DIR_Y_OFFSET = {-1, -1, 0, 1, 1, 1, 0, -1};

  private static final String TITLE = "Cell Outliner";

  private boolean moreOptions;
  private boolean buildMaskOutput;
  private boolean processAllFrames;
  private boolean processAllSlices;
  private boolean debug;

  private ImagePlus imp;
  private PointRoi roi;
  private int[] xpoints;
  private int[] ypoints;
  private final TIntArrayList rotationAngles = new TIntArrayList();

  private TIntObjectHashMap<float[]> kernels;
  private TIntObjectHashMap<FloatProcessor> convolved;
  private int halfWidth;
  private double maxDistance2;

  private int maxx;
  private int xlimit;
  private int ylimit;
  private int[] offset;

  /** The current settings for the plugin instance. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    int cellRadius = 25;
    double tolerance = 0.8;
    boolean darkEdge = true;
    int kernelWidth = 13;
    double kernelSmoothing = 1;
    int polygonSmoothing = 1;
    double weightingGamma = 3;
    int iterations = 3;
    boolean ellipticalFit;
    int dilate;

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
      cellRadius = source.cellRadius;
      tolerance = source.tolerance;
      darkEdge = source.darkEdge;
      kernelWidth = source.kernelWidth;
      kernelSmoothing = source.kernelSmoothing;
      polygonSmoothing = source.polygonSmoothing;
      weightingGamma = source.weightingGamma;
      iterations = source.iterations;
      ellipticalFit = source.ellipticalFit;
      dilate = source.dilate;
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
    UsageTracker.recordPlugin(this.getClass(), arg);

    if (imp == null) {
      return DONE;
    }
    if (imp.getRoi() == null || imp.getRoi().getType() != Roi.POINT) {
      IJ.error("Please select a centre point using the ROI tool");
      return DONE;
    }
    this.imp = imp;
    kernels = null;
    roi = (PointRoi) imp.getRoi();
    xpoints = roi.getPolygon().xpoints;
    ypoints = roi.getPolygon().ypoints;

    return FLAGS;
  }

  /** {@inheritDoc} */
  @Override
  public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
    final GenericDialog gd = new GenericDialog(TITLE);
    moreOptions = IJ.shiftKeyDown();

    settings = Settings.load();
    gd.addSlider("Cell_radius", 10, 30, settings.cellRadius);
    gd.addSlider("Tolerance", 0.5, 1.5, settings.tolerance);
    gd.addSlider("Kernel_width", 7, 19, settings.kernelWidth);
    gd.addCheckbox("Dark_edge", settings.darkEdge);
    gd.addSlider("Kernel_smoothing", 1.0, 5.5, settings.kernelSmoothing);
    gd.addSlider("Polygon_smoothing", 0, 5, settings.polygonSmoothing);
    gd.addSlider("Weighting_gamma", 0.05, 5, settings.weightingGamma);
    gd.addSlider("Iterations", 1, 10, settings.iterations);
    gd.addCheckbox("Show_elliptical_fit", settings.ellipticalFit);
    gd.addSlider("Dilate", 0, 5, settings.dilate);

    if (moreOptions) {
      gd.addCheckbox("Debug", debug);
    } else {
      debug = false;
    }

    gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.UTILITY);

    gd.addPreviewCheckbox(pfr);
    gd.addDialogListener(this);
    gd.showDialog();

    // Final run: stop debug mode
    debug = false;

    final boolean cancelled = gd.wasCanceled() || !dialogItemChanged(gd, null);
    settings.save();
    if (cancelled) {
      imp.setOverlay(null); // Clear preview overlay
      return DONE;
    }

    if (!getStackOptions(imp)) {
      return DONE;
    }

    // Switch from the image overlay to mask output
    buildMaskOutput = true;

    return FLAGS;
  }

  /** {@inheritDoc} */
  @Override
  public boolean dialogItemChanged(GenericDialog gd, AWTEvent event) {
    final int oldCellRadius = settings.cellRadius;
    final double oldTolerance = settings.tolerance;
    final boolean oldDarkEdge = settings.darkEdge;
    final double oldKernelSmoothing = settings.kernelSmoothing;
    final int oldKernelWidth = settings.kernelWidth;

    settings.cellRadius = (int) gd.getNextNumber();
    settings.tolerance = gd.getNextNumber();
    settings.kernelWidth = (int) gd.getNextNumber();
    settings.darkEdge = gd.getNextBoolean();
    settings.kernelSmoothing = gd.getNextNumber();
    settings.polygonSmoothing = (int) gd.getNextNumber();
    settings.weightingGamma = gd.getNextNumber();
    settings.iterations = (int) gd.getNextNumber();
    settings.ellipticalFit = gd.getNextBoolean();
    settings.dilate = (int) gd.getNextNumber();
    if (moreOptions) {
      debug = gd.getNextBoolean();
    }

    // Round down to nearest odd number and check minimum size
    if (settings.kernelWidth % 2 == 0) {
      settings.kernelWidth--;
    }
    if (settings.kernelWidth < 3) {
      settings.kernelWidth = 3;
    }
    if (settings.cellRadius < 1) {
      settings.cellRadius = 1;
    }
    if (settings.iterations < 1) {
      settings.iterations = 1;
    }

    // Check if the convolutions need recalculating
    if (oldCellRadius != settings.cellRadius || oldTolerance != settings.tolerance
        || oldKernelWidth != settings.kernelWidth || oldDarkEdge != settings.darkEdge
        || oldKernelSmoothing != settings.kernelSmoothing) {
      kernels = null;
    }

    return true;
  }

  private boolean getStackOptions(ImagePlus imp) {
    if (imp.getNFrames() > 1 || imp.getNSlices() > 1) {
      final GenericDialog gd = new GenericDialog("Process Stack?");
      gd.addMessage("Process multiple slices (" + (imp.getNFrames() * imp.getNSlices()) + ")");
      if (imp.getNFrames() > 1) {
        gd.addCheckbox("All_frames (" + imp.getNFrames() + ")", false);
      }
      if (imp.getNSlices() > 1) {
        gd.addCheckbox("All_slices (" + imp.getNSlices() + ")", false);
      }
      gd.enableYesNoCancel("Yes", "No");
      gd.showDialog();
      if (gd.wasCanceled()) {
        return false;
      }
      if (imp.getNFrames() > 1) {
        processAllFrames = gd.getNextBoolean();
      }
      if (imp.getNSlices() > 1) {
        processAllSlices = gd.getNextBoolean();
      }
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
  public void run(ImageProcessor inputProcessor) {
    // This can be called by the preview or as the final run

    // Final: Create a new mask image
    if (buildMaskOutput) {
      final int w = inputProcessor.getWidth();
      final int h = inputProcessor.getHeight();
      final boolean useShort = (xpoints.length > 255);

      // Build a list of the frames to process
      final int channel = imp.getChannel();
      final int[] frames =
          (processAllFrames) ? sequence(imp.getNFrames()) : new int[] {imp.getFrame()};
      final int[] slices =
          (processAllSlices) ? sequence(imp.getNSlices()) : new int[] {imp.getSlice()};

      final int nFrames = frames.length;
      final int nSlices = slices.length;
      final int size = nFrames * nSlices;
      final boolean resetConvolved = (size > 1);

      final ImageStack stack = new ImageStack(w, h, size);
      final ImageStack inputStack = this.imp.getImageStack();

      int totalSlices = 1;
      for (final int frame : frames) {
        for (final int slice : slices) {
          final String label = "t" + frame + "z" + slice;
          IJ.showStatus("Processing " + label);
          IJ.showProgress(totalSlices - 1, size);

          final ImageProcessor maskIp =
              (useShort) ? new ShortProcessor(w, h) : new ByteProcessor(w, h);

          final int index = imp.getStackIndex(channel, slice, frame);
          final ImageProcessor ip = inputStack.getProcessor(index);

          if (resetConvolved) {
            convolved = null;
          }
          final PolygonRoi[] cells = findCells(ip);
          if (cells == null) {
            return;
          }

          for (int i = 0; i < cells.length; i++) {
            final PolygonRoi cell = cells[i];
            final Rectangle b = cell.getBounds();
            cell.setLocation(0, 0); // Remove the cell offset to allow it to be created
            final ByteProcessor cellIp = createFilledCell(b.width, b.height, cell, i + 1);
            maskIp.copyBits(cellIp, b.x, b.y, Blitter.COPY_ZERO_TRANSPARENT);
          }

          stack.setPixels(maskIp.getPixels(), totalSlices);
          stack.setSliceLabel(label, totalSlices);
          totalSlices++;
        }
      }

      // Create the output image
      final String title = imp.getTitle() + " Cell Outline";
      ImagePlus maskImp = WindowManager.getImage(title);
      if (maskImp == null) {
        maskImp = new ImagePlus(title, stack);
        maskImp.setOpenAsHyperStack(true);
        maskImp.setDimensions(1, nSlices, nFrames);
        maskImp.show();
      } else {
        maskImp.setStack(stack, 1, nSlices, nFrames);
      }
      maskImp.setRoi(roi);
      applyColorModel(maskImp);
      maskImp.setDisplayRange(0, xpoints.length);
      maskImp.updateAndDraw();
    } else {
      // Preview: Draw the polygon outline as an overlay
      final Overlay overlay = new Overlay();
      overlay.setStrokeColor(Color.green);
      overlay.setFillColor(null);

      final PolygonRoi[] cells = findCells(inputProcessor);
      ImageJUtils.finished();

      if (cells == null) {
        return;
      }

      for (final PolygonRoi cell : cells) {
        overlay.add(cell);
      }
      imp.setOverlay(overlay);
    }
  }

  private static int[] sequence(int max) {
    final int[] s = new int[max];
    for (int i = 0; i < max; i++) {
      s[i] = i + 1;
    }
    return s;
  }

  @Nullable
  private PolygonRoi[] findCells(ImageProcessor inputProcessor) {
    // Limit processing to where it is needed
    final Rectangle bounds = createBounds(inputProcessor, xpoints, ypoints, getCellRange());
    ImageProcessor ip = inputProcessor.duplicate();
    ip.setRoi(bounds);
    ip = ip.crop();

    if (kernels == null) {
      kernels = createKernels();
      convolved = null;
    }
    if (convolved == null) {
      convolved = convolveImage(ip, kernels);
      // showConvolvedImages(convolved)
    }

    if (ImageJUtils.isInterrupted()) {
      return null;
    }

    FloatProcessor combinedIp = null;
    Blitter blitter = null;
    ImagePlus combinedImp = null;

    if (debug) {
      combinedIp = new FloatProcessor(ip.getWidth(), ip.getHeight());
      blitter = new FloatBlitter(combinedIp);
      combinedImp = displayImage(combinedIp, "Combined edge projection");
    }

    final PolygonRoi[] cells = new PolygonRoi[xpoints.length];

    if (!this.buildMaskOutput) {
      IJ.showStatus("Finding cells ...");
    }

    // Process each point
    for (int n = 0; n < xpoints.length; n++) {
      if (!this.buildMaskOutput) {
        IJ.showProgress(n, xpoints.length);
      }

      final int cx = xpoints[n] - bounds.x;
      final int cy = ypoints[n] - bounds.y;

      // Restrict bounds using the cell radius and tolerance
      final Rectangle pointBounds = createBounds(ip, cx, cy, cx, cy, getCellRange());

      // Calculate the angle
      final FloatProcessor angle = createAngleProcessor(cx, cy, pointBounds);

      if (ImageJUtils.isInterrupted()) {
        return null;
      }
      final FloatProcessor edgeProjection = computeEdgeProjection(convolved, pointBounds, angle);

      // Initialise the edge as a circle.
      PolygonRoi cell = null;
      double[] params = {cx - pointBounds.x, cy - pointBounds.y, settings.cellRadius,
          settings.cellRadius, settings.cellRadius, 0};
      final double range = settings.cellRadius * 0.9;

      // Iterate to find the best cell outline
      boolean returnEllipticalFit = settings.ellipticalFit;
      for (int iter = 0; iter < settings.iterations; iter++) {
        // Use the current elliptical edge to define the weights for the edge projection
        final FloatProcessor weights = createWeightMap(pointBounds, params, range);
        if (ImageJUtils.isInterrupted() || weights == null) {
          return null;
        }

        if (debug) {
          displayImage(weights, "Weight map");
        }

        final FloatProcessor weightedEdgeProjection = applyWeights(edgeProjection, weights);

        if (debug) {
          blitter.copyBits(weightedEdgeProjection, pointBounds.x, pointBounds.y, Blitter.ADD);
          combinedIp.resetMinAndMax();
          combinedImp.updateAndDraw();
          displayImage(weightedEdgeProjection, "Weighted edge projection");
        }

        cell = findPolygonalCell((int) Math.round(params[0]), (int) Math.round(params[1]),
            weightedEdgeProjection, angle);

        final FloatProcessor weightMap = weightedEdgeProjection; // weights
        final double[] newParams = fitPolygonalCell(cell, params, weightMap);

        if (newParams == null) {
          returnEllipticalFit = false;
          break;
        }

        // Set the parameters for the weight map
        params = newParams;
      }

      assert cell != null : "No cell";

      // Return either the fitted elliptical cell or the last polygon outline
      if (returnEllipticalFit) {
        final EllipticalCell e = new EllipticalCell();
        final FloatPolygon ellipse = e.drawEllipse(params);
        cell = new PolygonRoi(ellipse.xpoints, ellipse.ypoints, ellipse.npoints, Roi.POLYGON);
      }

      PolygonRoi finalCell = cell;
      if (settings.dilate > 0) {
        // Dilate the cell and then trace the new outline
        final ByteProcessor bp = new ByteProcessor(pointBounds.width, pointBounds.height);
        bp.setColor(CELL & 0xff);
        bp.draw(cell);
        for (int i = 0; i < settings.dilate; i++) {
          dilate(bp);
        }
        cell = traceOutline(bp);
        if (cell != null) {
          finalCell = cell;
        }
      }

      final Rectangle pos = finalCell.getBounds();

      // Does not work in IJ 1.46+
      // finalCell.setLocation(pos.x + bounds.x + pointBounds.x, pos.y + bounds.y + pointBounds.y)

      // Create a new Polygon with the correct coordinates. This is required since IJ 1.46
      // since setting the location is not passed through when drawing an overlay
      final int[] xCoords = finalCell.getXCoordinates();
      final int[] yCoords = finalCell.getYCoordinates();
      final int npoints = finalCell.getNCoordinates();
      for (int i = 0; i < npoints; i++) {
        xCoords[i] += pos.x + bounds.x + pointBounds.x;
        yCoords[i] += pos.y + bounds.y + pointBounds.y;
      }

      finalCell = new PolygonRoi(xCoords, yCoords, npoints, Roi.POLYGON);

      cells[n] = finalCell;
    }

    return cells;
  }

  /**
   * Gets the cell range around the centre using the cell radius, the tolerance and half the kernel
   * width.
   *
   * @return the cell range
   */
  private int getCellRange() {
    return (int) Math.ceil(
        settings.cellRadius + settings.cellRadius * settings.tolerance + settings.kernelWidth / 2);
  }

  private static void applyColorModel(ImagePlus imp) {
    // Load the spectrum LUT
    WindowManager.setTempCurrentImage(imp);
    final LutLoader lut = new LutLoader();
    lut.run("spectrum");
    WindowManager.setTempCurrentImage(null);

    // Set zero to black
    final ImageProcessor ip = imp.getProcessor();
    IndexColorModel cm = (IndexColorModel) ip.getColorModel();
    final byte[] r = new byte[256];
    final byte[] b = new byte[256];
    final byte[] g = new byte[256];
    cm.getReds(r);
    cm.getBlues(b);
    cm.getGreens(g);
    r[0] = 0;
    b[0] = 0;
    g[0] = 0;
    cm = new IndexColorModel(8, 256, r, g, b);
    ip.setColorModel(cm);
  }

  /**
   * Draw the current elliptical cell. Then create an Euclidian Distance Map to use as the weights
   * within the provided range. All other points a zeros.
   *
   * @param pointBounds the point bounds
   * @param params the params
   * @param range the range
   * @return the float processor
   */
  private FloatProcessor createWeightMap(Rectangle pointBounds, double[] params, double range) {
    final EllipticalCell cell = new EllipticalCell();
    final FloatPolygon ellipse = cell.drawEllipse(params);

    final ByteProcessor mask = new ByteProcessor(pointBounds.width, pointBounds.height);
    mask.setValue(255);
    mask.draw(new PolygonRoi(ellipse.xpoints, ellipse.ypoints, ellipse.npoints, Roi.POLYGON));

    final EDM edm = new EDM();
    final FloatProcessor map = edm.makeFloatEDM(mask, (byte) 255, false);
    if (map == null) {
      // Preview thread has been interrupted
      return null;
    }
    map.invert();
    final double max = map.getMax();
    final double min = Math.max(max - range, 0);
    final float[] data = (float[]) map.getPixels();
    for (int i = 0; i < data.length; i++) {
      data[i] = (float) ((data[i] > min) ? (data[i] - min) / range : 0f);
    }

    // Apply a gamma function for a smoother roll-off
    double gamma = settings.weightingGamma;
    if (gamma > 0) {
      gamma = 1.0 / gamma;
      for (int i = 0; i < data.length; i++) {
        data[i] = (float) Math.pow(data[i], gamma);
      }
    }

    if (debug) {
      map.resetMinAndMax();
      final ImagePlus mapImp = displayImage(map, "Current weight map");
      mapImp.updateAndDraw();
    }

    return map;
  }

  private static FloatProcessor applyWeights(FloatProcessor edgeProjection,
      FloatProcessor weights) {
    final FloatProcessor ip =
        new FloatProcessor(edgeProjection.getWidth(), edgeProjection.getHeight());
    final float[] e = (float[]) edgeProjection.getPixels();
    final float[] w = (float[]) weights.getPixels();
    final float[] p = (float[]) ip.getPixels();
    for (int i = 0; i < p.length; i++) {
      p[i] = e[i] * w[i];
    }
    return ip;
  }

  private static ImagePlus displayImage(ImageProcessor maskIp, String title) {
    ImagePlus maskImp = WindowManager.getImage(title);
    if (maskImp == null) {
      maskImp = new ImagePlus(title, maskIp);
      maskImp.show();
    } else {
      maskImp.setProcessor(maskIp);
    }
    return maskImp;
  }

  /**
   * Find the bounding rectangle that contains all the pixels that will be required for processing.
   *
   * @param ip the ip
   * @param xpoints the xpoints
   * @param ypoints the ypoints
   * @param extra the extra tolerance around the cell
   * @return the rectangle
   */
  private static Rectangle createBounds(ImageProcessor ip, int[] xpoints, int[] ypoints,
      int extra) {
    // Get the range of clicked points
    int minx = Integer.MAX_VALUE;
    int maxx = 0;
    for (final int x : xpoints) {
      if (minx > x) {
        minx = x;
      }
      if (maxx < x) {
        maxx = x;
      }
    }
    int miny = Integer.MAX_VALUE;
    int maxy = 0;
    for (final int y : ypoints) {
      if (miny > y) {
        miny = y;
      }
      if (maxy < y) {
        maxy = y;
      }
    }

    // Add the cell width, tolerance and kernel size to get the total required limits
    return createBounds(ip, minx, miny, maxx, maxy, extra);
  }

  private static Rectangle createBounds(ImageProcessor ip, int minx, int miny, int maxx, int maxy,
      int extra) {
    minx = Math.max(0, minx - extra);
    miny = Math.max(0, miny - extra);
    maxx = Math.min(ip.getWidth() - 1, maxx + extra);
    maxy = Math.min(ip.getHeight() - 1, maxy + extra);
    return new Rectangle(minx, miny, maxx - minx + 1, maxy - miny + 1);
  }

  /**
   * Build the convolution kernels.
   *
   * @return the convolution kernels
   */
  private TIntObjectHashMap<float[]> createKernels() {
    final TIntObjectHashMap<float[]> newKernels = new TIntObjectHashMap<>();
    rotationAngles.clear();

    // Used to weight the kernel from the distance to the centre
    halfWidth = settings.kernelWidth / 2;
    maxDistance2 = halfWidth * halfWidth * 2.0;

    // Build a set convolution kernels at 6 degree intervals
    final int cx = halfWidth;
    final int cy = halfWidth;
    for (int rotation = 0; rotation < 180; rotation += 12) {
      rotationAngles.add(rotation);
      final FloatProcessor fp = new FloatProcessor(settings.kernelWidth, settings.kernelWidth);

      // createAliasedLines(halfWidth, cx, cy, degreesToRadians, rotation, fp)

      // Build curves using the cell size.
      final double radians = Math.toRadians(rotation);
      final double centreX = cx - Math.cos(radians) * settings.cellRadius;
      final double centreY = cy - Math.sin(radians) * settings.cellRadius;

      createDoGKernel(fp, centreX, centreY, settings.cellRadius, 1.0);

      newKernels.put(rotation, (float[]) fp.getPixels());
    }

    // Copy the kernels for the second half of the circle
    for (final int rotation : rotationAngles.toArray()) {
      rotationAngles.add(rotation + 180);
      FloatProcessor fp = new FloatProcessor(settings.kernelWidth, settings.kernelWidth,
          newKernels.get(rotation), null);
      fp = (FloatProcessor) fp.duplicate();
      fp.flipHorizontal();
      fp.flipVertical();
      newKernels.put(rotation + 180, (float[]) fp.getPixels());
    }

    return newKernels;
  }

  /**
   * Create a 1D Difference-of-Gaussians kernel using the distance from the centre minus the cell
   * radius to determine the x distance.
   *
   * @param fp the fp
   * @param centreX the centre X
   * @param centreY the centre Y
   * @param cellRadius the cell radius
   * @param width the width
   */
  private void createDoGKernel(FloatProcessor fp, double centreX, double centreY, int cellRadius,
      double width) {
    // 1.6:1 is an approximation of the Laplacian of Gaussians
    double sigma1 = 1.6 * width;
    double sigma2 = width;

    if (settings.kernelSmoothing > 0) {
      sigma1 *= settings.kernelSmoothing;
      sigma2 *= settings.kernelSmoothing;
    }

    final float[] data = (float[]) fp.getPixels();
    for (int y = 0, i = 0; y < fp.getHeight(); y++) {
      for (int x = 0; x < fp.getWidth(); x++, i++) {
        final double dx = centreX - x;
        final double dy = centreY - y;
        final double x0 = cellRadius - Math.sqrt(dx * dx + dy * dy);

        float value = (float) (gaussian(x0, sigma1) - gaussian(x0, sigma2));

        if (!settings.darkEdge) {
          value = -value;
        }

        // Weight the amount using the distance from the centre
        final double d = (x - halfWidth) * (x - halfWidth) + (y - halfWidth) * (y - halfWidth);
        value *= 1 - (d / maxDistance2);

        data[i] = value;
      }
    }
  }

  private static double gaussian(double x0, double sigma) {
    return 1.0 / (2 * Math.PI * sigma * sigma) * Math.exp(-0.5 * x0 * x0 / (sigma * sigma));
  }

  /**
   * Old method used to create straight line kernels.
   *
   * @param halfWidth the half width
   * @param cx the cx
   * @param cy the cy
   * @param degreesToRadians the degrees to radians
   * @param rotation the rotation
   * @param fp the fp
   */
  @SuppressWarnings("unused")
  private void createAliasedLines(int halfWidth, int cx, int cy, double degreesToRadians,
      int rotation, FloatProcessor fp) {
    // Calculate the direction of the line
    double dx = Math.sin(rotation * degreesToRadians);
    double dy = Math.cos(rotation * degreesToRadians);

    // Normalise so that each increment moves one pixel in either X or Y
    final double norm = Math.max(Math.abs(dx), Math.abs(dy));
    dx /= norm;
    dy /= norm;

    // Create aliased lines
    for (int i = 0; i <= halfWidth; i++) {
      add(fp, cx + i * dx, cy + i * dy, 1);
    }
    for (int i = 1; i <= halfWidth; i++) {
      add(fp, cx - i * dx, cy - i * dy, 1);
    }
  }

  /**
   * Spread a value of 1 using bilinear weighting around the point.
   *
   * @param fp the fp
   * @param x the x
   * @param y the y
   * @param value the value
   */
  private void add(FloatProcessor fp, double x, double y, float value) {
    final int x1 = (int) Math.floor(x);
    final int y1 = (int) Math.floor(y);

    // Ignore out-of-bounds
    if (x1 < -1 || x1 > fp.getWidth() || y1 < -1 || y1 > fp.getHeight()) {
      return;
    }

    // Weight the amount using the distance from the centre
    final double d = (x - halfWidth) * (x - halfWidth) + (y - halfWidth) * (y - halfWidth);
    value *= 1 - (d / maxDistance2);

    final double dx = x - x1;
    final double dy = y - y1;
    add(fp, x1 + 1, y1 + 1, value * dx * dy);
    add(fp, x1 + 1, y1, value * dx * (1 - dy));
    add(fp, x1, y1 + 1, value * (1 - dx) * dy);
    add(fp, x1, y1, value * (1 - dx) * (1 - dy));
  }

  private static void add(FloatProcessor fp, int x, int y, double weight) {
    final float value = fp.getPixelValue(x, y);
    fp.putPixelValue(x, y, value + weight);
  }

  private TIntObjectHashMap<FloatProcessor> convolveImage(ImageProcessor ip,
      TIntObjectHashMap<float[]> kernels) {
    final TIntObjectHashMap<FloatProcessor> newConvolved = new TIntObjectHashMap<>();

    // Convolve image with each
    final boolean ok = rotationAngles.forEach(rotation -> {
      if (!this.buildMaskOutput) {
        IJ.showStatus("Convolving " + rotation);
      }
      final float[] kernel = kernels.get(rotation);
      final FloatProcessor fp =
          (ip instanceof FloatProcessor) ? (FloatProcessor) ip.duplicate() : ip.toFloat(0, null);
      fp.convolve(kernel, settings.kernelWidth, settings.kernelWidth);
      newConvolved.put(rotation, fp);
      return !ImageJUtils.isInterrupted();
    });
    if (!this.buildMaskOutput) {
      // Convolver modifies the progress tracker
      ImageJUtils.finished();
    }
    return (ok) ? newConvolved : null;
  }

  /**
   * Debugging method to show the results of convolution.
   *
   * @param convolved the convolved
   */
  @SuppressWarnings("unused")
  private void showConvolvedImages(TIntObjectHashMap<FloatProcessor> convolved) {
    final ImageProcessor ip = convolved.get(0);
    final ImageStack stack = new ImageStack(ip.getWidth(), ip.getHeight());
    rotationAngles.forEach(rotation -> {
      stack.addSlice("Rotation " + rotation, convolved.get(rotation));
      return true;
    });

    final ImagePlus filterImp = new ImagePlus("Membrane filter", stack);
    filterImp.show();

    // Output different projections of the results
    final ZProjector projector = new ZProjector(filterImp);
    for (int projectionMethod = 0; projectionMethod < ZProjector.METHODS.length;
        projectionMethod++) {
      IJ.showStatus("Projecting " + ZProjector.METHODS[projectionMethod]);
      projector.setMethod(projectionMethod);
      projector.doProjection();
      final ImagePlus projImp = projector.getProjection();
      projImp.show();
    }
  }

  /**
   * For the given point, compute the angle between pixels and the centre.
   *
   * @param cx the cx
   * @param cy the cy
   * @param pointBounds the point bounds
   * @return the float processor
   */
  private static FloatProcessor createAngleProcessor(int cx, int cy, Rectangle pointBounds) {
    // Find the bounds to process
    final int minx = pointBounds.x;
    final int miny = pointBounds.y;
    final int maxx = minx + pointBounds.width;
    final int maxy = miny + pointBounds.height;

    final FloatProcessor angle = new FloatProcessor(pointBounds.width, pointBounds.height);
    for (int y = miny, index = 0; y < maxy; y++) {
      for (int x = minx; x < maxx; x++, index++) {
        final int dx = cx - x;
        final int dy = cy - y;
        final float a = (float) Math.toDegrees(Math.atan2(dy, dx));
        angle.setf(index, a + 180f); // Convert to 0-360 domain
      }
    }
    return angle;
  }

  private FloatProcessor computeEdgeProjection(TIntObjectHashMap<FloatProcessor> convolved,
      Rectangle pointBounds, FloatProcessor angle) {
    final float[] a = (float[]) angle.getPixels();

    // Do a projection of all membrane filters
    final float[][] stack = new float[rotationAngles.size()][];
    for (int i = 0; i < rotationAngles.size(); i++) {
      final int rotation = rotationAngles.get(i);
      FloatProcessor fp = (FloatProcessor) convolved.get(rotation).duplicate();
      fp.setRoi(pointBounds);
      fp = (FloatProcessor) fp.crop();
      if (settings.darkEdge) {
        fp.invert();
      }
      final float[] p = (float[]) fp.getPixels();

      // Do a projection of membrane filters convolved with a filter roughly perpendicular to the
      // edge
      for (int j = 0; j < p.length; j++) {
        // Check if angle is close enough
        float diff = (a[j] > rotation) ? a[j] - rotation : rotation - a[j];
        if (diff >= 180) {
          diff -= 360;
        }
        if (Math.abs(diff) > 30) {
          p[j] = 0;
        }
      }

      stack[i] = p;
    }

    // Perform Z-projection
    final FloatProcessor ip2 = new FloatProcessor(pointBounds.width, pointBounds.height);
    final float[] pixels = (float[]) ip2.getPixels();
    for (final float[] tmp : stack) {
      // Max intensity
      for (int i = 0; i < tmp.length; i++) {
        if (pixels[i] < tmp[i]) {
          pixels[i] = tmp[i];
        }
      }
    }

    return ip2;
  }

  private static final byte CELL = (byte) 255;

  private static FloatProcessor cropToValues(FloatProcessor fp, Rectangle cropBounds) {
    // Find the bounds
    int minx = fp.getWidth();
    int miny = fp.getHeight();
    int maxx = 0;
    int maxy = 0;
    final float[] data = (float[]) fp.getPixels();
    for (int i = 0; i < data.length; i++) {
      if (data[i] != 0) {
        final int x = i % fp.getWidth();
        final int y = i / fp.getWidth();
        if (minx > x) {
          minx = x;
        }
        if (maxx < x) {
          maxx = x;
        }
        if (miny > y) {
          miny = y;
        }
        if (maxy < y) {
          maxy = y;
        }
      }
    }

    // Crop to the bounds
    cropBounds.x = minx;
    cropBounds.y = miny;
    cropBounds.width = maxx - minx + 1;
    cropBounds.height = maxy - miny + 1;
    fp.setRoi(cropBounds);
    final FloatProcessor fp2 = (FloatProcessor) fp.crop();
    fp.setRoi((Rectangle) null);

    return fp2;
  }

  /**
   * Provides methods for drawing an elliptical path composed of two ellipses placed back-to-back.
   *
   * <p>The ellipse is constructed using two values for the major axis. This allows the function to
   * draw two ellipses back-to-back. The input parameters can be converted into the actual first and
   * second major axis values using the helper functions.
   */
  class EllipticalCell {
    /**
     * Convert the input function parameters for the major axis into the first actual major axis.
     *
     * @param axis1 the axis 1
     * @param axis2 the axis 2
     * @return the major 1
     */
    double getMajor1(double axis1, double axis2) {
      return (2 * axis1 + axis2) / 3;
    }

    /**
     * Convert the input function parameters for the major axis into the second actual major axis.
     *
     * @param axis1 the axis 1
     * @param axis2 the axis 2
     * @return the major 2
     */
    double getMajor2(double axis1, double axis2) {
      return (2 * axis2 + axis1) / 3;
    }

    /**
     * Draws the elliptical cell.
     *
     * @param params the params
     * @return the float polygon
     */
    FloatPolygon drawEllipse(final double[] params) {
      final double centreX = params[0];
      final double centreY = params[1];
      final double axis1 = params[2];
      final double axis2 = params[3];
      final double minor = params[4];
      final double phi = params[5];

      return drawEllipse(centreX, centreY, axis1, axis2, minor, phi);
    }

    /**
     * Draw an elliptical path of points. The ellipse is constructed as two half ellipses allowing a
     * two different major axes lengths, one for each side (i.e. an egg shape).
     *
     * @param centreX the centre X
     * @param centreY the centre Y
     * @param axis1 the axis 1
     * @param axis2 the axis 2
     * @param minor the minor
     * @param phi The angle from X-axis and the major axis of the ellipse
     * @return the float polygon
     */
    FloatPolygon drawEllipse(final double centreX, final double centreY, final double axis1,
        final double axis2, final double minor, final double phi) {
      final int npoints = 90;
      final double arcAngle = 2 * Math.PI / npoints;
      final float[] xp = new float[npoints];
      final float[] yp = new float[npoints];

      int count = 0;

      final double major1 = getMajor1(axis1, axis2);
      final double major2 = getMajor2(axis1, axis2);

      final double angleLimit = Math.PI / 2;
      double a1 = major1 * Math.cos(phi);
      double a2 = major1 * Math.sin(phi);
      final double b1 = minor * Math.sin(phi);
      final double b2 = minor * Math.cos(phi);

      // Create points around the start of the first half
      for (double angle = -Math.PI; angle < -angleLimit; angle += arcAngle, count++) {
        xp[count] = (float) (centreX + a1 * Math.cos(angle) - b1 * Math.sin(angle));
        yp[count] = (float) (centreY + a2 * Math.cos(angle) + b2 * Math.sin(angle));
      }

      // Create points around the second half
      a1 = major2 * Math.cos(phi);
      a2 = major2 * Math.sin(phi);
      for (double angle = -angleLimit; angle < angleLimit; angle += arcAngle, count++) {
        xp[count] = (float) (centreX + a1 * Math.cos(angle) - b1 * Math.sin(angle));
        yp[count] = (float) (centreY + a2 * Math.cos(angle) + b2 * Math.sin(angle));
      }

      // Create points around the rest of the first half
      a1 = major1 * Math.cos(phi);
      a2 = major1 * Math.sin(phi);
      for (double angle = angleLimit; angle < Math.PI && count < npoints;
          angle += arcAngle, count++) {
        xp[count] = (float) (centreX + a1 * Math.cos(angle) - b1 * Math.sin(angle));
        yp[count] = (float) (centreY + a2 * Math.cos(angle) + b2 * Math.sin(angle));
      }

      return new FloatPolygon(xp, yp, npoints);
    }
  }

  /**
   * Try and find a polygon that traces a path around the detected edges.
   *
   * @param cx the cx
   * @param cy the cy
   * @param fp the fp
   * @param angle the angle
   * @return the polygon roi
   */
  private PolygonRoi findPolygonalCell(int cx, int cy, FloatProcessor fp, FloatProcessor angle) {
    final Rectangle cropBounds = new Rectangle();
    final FloatProcessor cropFp = cropToValues(fp, cropBounds);
    angle.setRoi(cropBounds);
    final FloatProcessor cropAngles = (FloatProcessor) angle.crop();

    if (debug) {
      final ImagePlus fpImp = displayImage(cropFp, "Current edge outline");
      fpImp.updateAndDraw();
    }

    // Divide the region around the centre into segments and
    // find the maxima in each segment
    final float[] a = (float[]) cropAngles.getPixels();

    final int segmentArcWidth = 5;
    final float[] max = new float[360 / segmentArcWidth + 1];
    final int[] index = new int[max.length];

    for (int i = 0; i < a.length; i++) {
      final int segment = (int) (a[i] / segmentArcWidth);

      if (max[segment] < cropFp.getf(i)) {
        max[segment] = cropFp.getf(i);
        index[segment] = i;
      }
    }

    // Count the number of points.
    // Skip consecutive segments to ensure the sampled points are spread out.
    int npoints = 0;
    for (int i = 0; i < 360 / segmentArcWidth; i += 2) {
      if (max[i] > 0) {
        npoints++;
      }
    }

    // Return the polygon
    float[] xp = new float[npoints];
    float[] yp = new float[npoints];

    npoints = 0;
    for (int i = 0; i < 360 / segmentArcWidth; i += 2) {
      if (max[i] > 0) {
        xp[npoints] = index[i] % cropBounds.width + cropBounds.x;
        yp[npoints] = index[i] / cropBounds.width + cropBounds.y;
        npoints++;
      }
    }

    // Perform coordinate smoothing
    int window = settings.polygonSmoothing;
    if (window > 0) {
      final float[] newXPoints = new float[npoints];
      final float[] newYPoints = new float[npoints];
      window = Math.min(npoints / 4, window);
      for (int i = 0; i < npoints; i++) {
        double sumx = 0;
        double sumy = 0;
        double count = 0;
        for (int j = -window; j <= window; j++) {
          final double w = 1; // Alternative = (window - Math.abs(j) + 1.0) / (window + 1)
          final int ii = (i + j + npoints) % npoints;
          sumx += xp[ii] * w;
          sumy += yp[ii] * w;
          count += w;
        }
        newXPoints[i] = (float) MathUtils.div0(sumx, count);
        newYPoints[i] = (float) MathUtils.div0(sumy, count);
      }
      xp = newXPoints;
      yp = newYPoints;
    }

    return new PolygonRoi(xp, yp, npoints, Roi.POLYGON);
  }

  /**
   * Create a byte processor of the specified dimensions and fill the ROI.
   *
   * @param width the width
   * @param height the height
   * @param roi the roi
   * @return the byte processor
   */
  private static ByteProcessor createFilledCell(int width, int height, PolygonRoi roi) {
    return createFilledCell(width, height, roi, CELL & 0xff);
  }

  /**
   * Create a byte processor of the specified dimensions and fill the ROI.
   *
   * @param width the width
   * @param height the height
   * @param roi the roi
   * @param value the value
   * @return the byte processor
   */
  private static ByteProcessor createFilledCell(int width, int height, PolygonRoi roi, int value) {
    final ByteProcessor bp = new ByteProcessor(width, height);
    bp.setColor(value);
    bp.fill(roi);
    return bp;
  }

  /**
   * Find an ellipse that optimises the fit to the polygon detected edges.
   *
   * @param roi the roi
   * @param params the params
   * @param weightMap the weight map
   * @return the ellipse parameters
   */
  @Nullable
  private double[] fitPolygonalCell(PolygonRoi roi, double[] params, FloatProcessor weightMap) {
    // Get an estimate of the starting parameters using the current polygon
    final double[] startPoint =
        estimateStartPoint(roi, weightMap.getWidth(), weightMap.getHeight());

    final int maxEval = 2000;
    final DifferentiableEllipticalFitFunction func =
        new DifferentiableEllipticalFitFunction(roi, weightMap);

    final double relativeThreshold = 100 * Precision.EPSILON;
    final double absoluteThreshold = 100 * Precision.SAFE_MIN;
    final ConvergenceChecker<PointVectorValuePair> checker =
        new SimplePointChecker<>(relativeThreshold, absoluteThreshold);
    final double initialStepBoundFactor = 10;
    final double costRelativeTolerance = 1e-10;
    final double parRelativeTolerance = 1e-10;
    final double orthoTolerance = 1e-10;
    final double threshold = Precision.SAFE_MIN;

    final LevenbergMarquardtOptimizer optimiser =
        new LevenbergMarquardtOptimizer(initialStepBoundFactor, costRelativeTolerance,
            parRelativeTolerance, orthoTolerance, threshold);

    try {
      //@formatter:off
      final LeastSquaresProblem problem = new LeastSquaresBuilder()
          .maxEvaluations(Integer.MAX_VALUE)
          .maxIterations(maxEval)
          .start(startPoint)
          .target(func.calculateTarget())
          .weight(new DiagonalMatrix(func.calculateWeights()))
          .model(func, func::jacobian)
          .checkerPair(checker)
          .build();
      //@formatter:on

      final Optimum solution = optimiser.optimize(problem);

      if (debug) {
        IJ.log(String.format("Eval = %d (Iter = %d), RMS = %f", solution.getEvaluations(),
            solution.getIterations(), solution.getRMS()));
      }
      return solution.getPoint().toArray();
    } catch (final Exception ex) {
      IJ.log("Failed to find an elliptical solution, defaulting to polygon: " + ex.getMessage());
    }

    return null;
  }

  /**
   * Estimate the starting ellipse using the eccentricity around the central moments.
   *
   * <p>See: Burger & Burge, Digital Image Processing, An Algorithmic Introduction using Java (1st
   * Edition), pp231.
   *
   * @param roi the roi
   * @param width the width
   * @param height the height
   * @return the double[]
   */
  private double[] estimateStartPoint(PolygonRoi roi, int width, int height) {
    ByteProcessor ip = createFilledCell(width, height, roi);
    final byte[] data = (byte[]) ip.getPixels();

    // Speed up processing of the moments
    double u00 = 0;
    for (final byte b : data) {
      if (b != 0) {
        u00++;
      }
    }

    double cx = 0;
    double cy = 0;
    for (int v = 0, i = 0; v < ip.getHeight(); v++) {
      for (int u = 0; u < ip.getWidth(); u++, i++) {
        if (data[i] != 0) {
          cx += u;
          cy += v;
        }
      }
    }
    cx /= u00;
    cy /= u00;

    double u11 = 0;
    double u20 = 0;
    double u02 = 0;
    for (int v = 0, i = 0; v < ip.getHeight(); v++) {
      for (int u = 0; u < ip.getWidth(); u++, i++) {
        if (data[i] != 0) {
          final double dx = u - cx;
          final double dy = v - cy;
          u11 += dx * dy;
          u20 += dx * dx;
          u02 += dy * dy;
        }
      }
    }

    // Calculate the ellipsoid
    final double A = 2 * u11;
    final double B = u20 - u02;

    final double angle = 0.5 * Math.atan2(A, B);

    final double a1 = u20 + u02 + Math.sqrt((u20 - u02) * (u20 - u02) + 4 * u11 * u11);
    final double a2 = u20 + u02 - Math.sqrt((u20 - u02) * (u20 - u02) + 4 * u11 * u11);

    final double ra = Math.sqrt(2 * a1 / u00);
    final double rb = Math.sqrt(2 * a2 / u00);

    final double[] params = new double[] {cx, cy, ra, ra, rb, angle};

    if (debug) {
      final EllipticalCell cell = new EllipticalCell();
      final FloatPolygon ellipse = cell.drawEllipse(params);
      ip = createFilledCell(width, height,
          new PolygonRoi(ellipse.xpoints, ellipse.ypoints, ellipse.npoints, Roi.POLYGON));
      ip.setMinAndMax(0, CELL);
      displayImage(ip, "Start estimate");
    }

    return params;
  }

  /**
   * Provides a function to score the ellipse for use in gradient based optimisation methods.
   *
   * @see <a href="http://commons.apache.org/math/userguide/optimization.html">Commons Math
   *      optimisation</a>
   */
  private class DifferentiableEllipticalFitFunction extends EllipticalCell
      implements MultivariateVectorFunction {
    FloatProcessor weightMap;
    int npoints;
    int[] xpoints;
    int[] ypoints;

    /**
     * Instantiates a new differentiable elliptical fit function.
     *
     * @param roi The polygon to fit
     * @param weightMap the weight map
     */
    DifferentiableEllipticalFitFunction(PolygonRoi roi, FloatProcessor weightMap) {
      // These methods try to minimise the difference between a target value and your model value.
      // The target value is the polygon outline. The model is currently an elliptical path.
      this.weightMap = weightMap;
      npoints = roi.getNCoordinates();
      xpoints = Arrays.copyOf(roi.getXCoordinates(), npoints);
      ypoints = Arrays.copyOf(roi.getYCoordinates(), npoints);
      final Rectangle bounds = roi.getBounds();
      for (int i = 0; i < npoints; i++) {
        xpoints[i] += bounds.x;
        ypoints[i] += bounds.y;
      }
    }

    /**
     * Calculate target.
     *
     * @return Each point to be fitted
     */
    double[] calculateTarget() {
      final ByteProcessor bp = new ByteProcessor(weightMap.getWidth(), weightMap.getHeight());
      for (int i = 0; i < npoints; i++) {
        bp.putPixel(xpoints[i], ypoints[i], npoints);
      }

      if (debug) {
        bp.setMinAndMax(1, npoints);
        displayImage(bp, "Ellipse target");
      }

      // We want to minimise the distance to the polygon points.
      // Our values will be the distances so the target can be zeros.
      return new double[npoints];
    }

    /**
     * Calculate weights.
     *
     * @return The weights for each point to be fitted
     */
    double[] calculateWeights() {
      final double[] weights = new double[npoints];
      for (int i = 0; i < weights.length; i++) {
        weights[i] = weightMap.getPixelValue(ypoints[i], ypoints[i]);
      }
      return weights;
    }

    /** {@inheritDoc} */
    @Override
    public double[] value(double[] point) {
      if (debug) {
        ImageJUtils.log("%f,%f %f,%f,%f %f", point[0], point[1], point[2], point[3], point[4],
            Math.toDegrees(point[5]));
      }
      return getValue(point);
    }

    private double[] getValue(double[] point) {
      final double[] values = new double[npoints];

      final double cx = point[0];
      final double cy = point[1];
      final double axis1 = point[2];
      final double axis2 = point[3];
      final double minor = point[4];
      final double phi = point[5];

      // Link the axes parameters so that each one will have a gradient for all points.
      final double major1 = getMajor1(axis1, axis2);
      final double major2 = getMajor2(axis1, axis2);

      final ByteProcessor bp = new ByteProcessor(weightMap.getWidth(), weightMap.getHeight());

      // Calculate the distance of the ellipse to each point
      for (int i = 0; i < values.length; i++) {
        // Get the angle between the point and the centre
        final double absAngle = Math.atan2(ypoints[i] - cy, xpoints[i] - cx);

        // Adjust for the ellipse rotation
        double angle = absAngle - phi;
        // Check domain
        if (angle < -Math.PI) {
          angle += 2 * Math.PI;
        }
        if (angle > Math.PI) {
          angle -= 2 * Math.PI;
        }

        double a1;
        double a2;
        final double b1 = minor * Math.sin(phi);
        final double b2 = minor * Math.cos(phi);

        // Create ellipse point. The shape is two ellipse halves so use the correct part.
        if (angle < -Math.PI / 2 || angle > Math.PI / 2) {
          a1 = major1 * Math.cos(phi);
          a2 = major1 * Math.sin(phi);
        } else {
          a1 = major2 * Math.cos(phi);
          a2 = major2 * Math.sin(phi);
        }

        final double x = cx + a1 * Math.cos(angle) - b1 * Math.sin(angle);
        final double y = cy + a2 * Math.cos(angle) + b2 * Math.sin(angle);

        bp.putPixel((int) x, (int) y, npoints);

        // Get the distance
        double dx = x - xpoints[i];
        double dy = y - ypoints[i];
        values[i] = Math.sqrt(dx * dx + dy * dy);

        // Check if it is inside or outside the ellipse
        dx = cx - xpoints[i];
        dy = cy - ypoints[i];
        final double pointToCentre = dx * dx + dy * dy;

        dx = cx - x;
        dy = cy - y;
        final double ellipseToCentre = dx * dx + dy * dy;

        if (pointToCentre < ellipseToCentre) {
          values[i] = -values[i];
        }
      }

      if (debug) {
        bp.setMinAndMax(1, npoints);
        displayImage(bp, "Ellipse points");
      }

      return values;
    }

    private double[][] jacobian(double[] variables) {
      final double[][] jacobian = new double[npoints][variables.length];

      // Compute numerical differentiation
      // Param order:
      // centreX, centreY, major1, major2, minor, phi
      final double[] delta = {1, 1, 2, 2, 2, 5 * Math.PI / 180.0};

      for (int i = 0; i < variables.length - 1; i++) {
        final double[] upper = getValue(updateVariables(variables, i, delta, 1));
        final double[] lower = getValue(updateVariables(variables, i, delta, -1));

        for (int j = 0; j < jacobian.length; ++j) {
          jacobian[j][i] = (upper[j] - lower[j]) / (2 * delta[i]);
        }
      }
      // Only compute the angle gradient if the ellipse is not a circle
      if (variables[2] != variables[3] || variables[2] != variables[4]) {
        final int i = variables.length - 1;
        final double[] upper = getValue(updateVariables(variables, i, delta, 1));
        final double[] lower = getValue(updateVariables(variables, i, delta, -1));

        for (int j = 0; j < jacobian.length; ++j) {
          jacobian[j][i] = (upper[j] - lower[j]) / (2 * delta[i]);
        }
      }

      return jacobian;
    }

    private double[] updateVariables(double[] variables, int index, double[] delta, int sign) {
      final double[] newVariables = Arrays.copyOf(variables, variables.length);
      newVariables[index] += delta[index] * sign;
      return newVariables;
    }
  }

  private void dilate(ByteProcessor bp) {
    final byte[] data = (byte[]) bp.getPixels();
    final byte[] newData = new byte[data.length];
    initialise(bp);

    for (int index = 0; index < data.length; index++) {
      if (data[index] != 0) {
        newData[index] = CELL;
        final int x = index % maxx;
        final int y = index / maxx;
        final boolean isInnerXy = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);

        // Use 4-connected cells
        if (isInnerXy) {
          for (int d = 0; d < 8; d += 2) {
            newData[index + offset[d]] = CELL;
          }
        } else {
          for (int d = 0; d < 8; d += 2) {
            if (isWithinXy(x, y, d)) {
              newData[index + offset[d]] = CELL;
            }
          }
        }
      }
    }

    bp.setPixels(newData);
  }

  private PolygonRoi traceOutline(ByteProcessor bp) {
    final byte[] data = (byte[]) bp.getPixels();

    initialise(bp);

    // Find first pixel
    int startIndex = 0;
    while (data[startIndex] == 0 && startIndex < data.length) {
      startIndex++;
    }
    if (startIndex == data.length) {
      return null;
    }

    final ArrayList<Point> coords = new ArrayList<>(100);
    addPoint(coords, startIndex);

    // Set start direction for search
    int searchDirection = 7;
    int index = startIndex;
    // Safety limit - The outline shouldn't be greater than the image perimeter
    int limit = (bp.getWidth() + bp.getHeight()) * 2 - 2;
    while (limit-- > 0) {
      final int nextDirection = findNext(data, index, searchDirection);
      if (nextDirection >= 0) {
        index += offset[nextDirection];
        if (index == startIndex) {
          break; // End of the outline
        }
        addPoint(coords, index);
        searchDirection = (nextDirection + 6) % 8;
      } else {
        break; // Single point with no move direction
      }
    }
    if (limit <= 0) {
      return null;
    }

    // Return the outline
    int npoints = 0;
    final int[] xp = new int[coords.size()];
    final int[] yp = new int[coords.size()];
    for (final Point p : coords) {
      xp[npoints] = p.x;
      yp[npoints] = p.y;
      npoints++;
    }

    return new PolygonRoi(xp, yp, npoints, Roi.POLYGON);
  }

  private void addPoint(ArrayList<Point> coords, int index) {
    final Point p = new Point(index % maxx, index / maxx);
    coords.add(p);
  }

  private int findNext(byte[] data, int index, int direction) {
    final int x = index % maxx;
    final int y = index / maxx;
    final boolean isInnerXy = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);

    // Process the neighbours
    for (int d = 0; d < 7; d++) {
      if (isInnerXy || isWithinXy(x, y, direction)) {
        final int index2 = index + offset[direction];

        // Check if foreground
        if (data[index2] != 0) {
          return direction;
        }
      }
      direction = (direction + 1) % 8;
    }

    return -1;
  }

  /**
   * Initialises the global width and height variables. Creates the direction offset tables.
   */
  private void initialise(ImageProcessor ip) {
    maxx = ip.getWidth();
    final int maxy = ip.getHeight();

    xlimit = maxx - 1;
    ylimit = maxy - 1;

    // Create the offset table (for single array 3D neighbour comparisons)
    offset = new int[DIR_X_OFFSET.length];
    for (int d = offset.length; d-- > 0;) {
      offset[d] = DIR_X_OFFSET[d] + maxx * DIR_Y_OFFSET[d];
    }
  }

  /**
   * Returns whether the neighbour in a given direction is within the image. NOTE: it is assumed
   * that the pixel x,y itself is within the image! Uses class variables xlimit, ylimit: (dimensions
   * of the image)-1.
   *
   * @param x x-coordinate of the pixel that has a neighbour in the given direction
   * @param y y-coordinate of the pixel that has a neighbour in the given direction
   * @param direction the direction from the pixel towards the neighbour
   * @return true if the neighbour is within the image (provided that x, y is within)
   */
  private boolean isWithinXy(int x, int y, int direction) {
    switch (direction) {
      case 0:
        return (y > 0);
      case 1:
        return (y > 0 && x < xlimit);
      case 2:
        return (x < xlimit);
      case 3:
        return (y < ylimit && x < xlimit);
      case 4:
        return (y < ylimit);
      case 5:
        return (y < ylimit && x > 0);
      case 6:
        return (x > 0);
      case 7:
        return (y > 0 && x > 0);
      default:
        return false;
    }
  }
}
