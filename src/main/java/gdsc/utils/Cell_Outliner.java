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
package gdsc.utils;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.IndexColorModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.apache.commons.math3.analysis.MultivariateMatrixFunction;
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

import gdsc.UsageTracker;
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

/**
 * Outlines a circular cell using the optimal path through a membrane scoring map.
 * <p>
 * The centre of the cell must be chosen and an approximate cell radius. Only pixels within a range of the cell radius
 * are processed. The score map is created using 2D convolutions of a curve through a NxN square. The curve is
 * constructed as a difference-of-Gaussians approximation to the Laplacian allowing dark/light edge detection. The curve
 * arc uses the cell radius and is rotated to produce membrane edge projections around the centre point.
 * <p>
 * The maximum intensity membrane projection is weighted using the current elliptical fit of the cell (initialised as a
 * circle using the cell radius). A polygon outline is constructed from the projection using the highest value in each
 * 10 degree segment around the selected centre. An elliptical shape consisting of two ellipses back-to-back (allowing
 * egg-like shapes) is then fitted to the polygon outline and the process iterated.
 */
public class Cell_Outliner implements ExtendedPlugInFilter, DialogListener
{
	private final int flags = DOES_8G + DOES_16 + DOES_32 + NO_CHANGES;

	private static final String TITLE = "Cell Outliner";
	private static int cellRadius = 25;
	private static double tolerance = 0.8;
	private static boolean darkEdge = true;
	private static int kernelWidth = 13;
	private static double kernelSmoothing = 1;
	private static int polygonSmoothing = 1;
	private static double weightingGamma = 3;
	private static int iterations = 3;
	private static boolean ellipticalFit = false;
	private static int dilate = 0;

	private boolean moreOptions = false;
	private boolean buildMaskOutput = false;
	private boolean processAllFrames = false;
	private boolean processAllSlices = false;
	private boolean debug = false;

	private ImagePlus imp;
	private Rectangle bounds;
	private PointRoi roi;
	private int[] xpoints, ypoints;
	private final ArrayList<Integer> rotationAngles = new ArrayList<>();

	HashMap<Integer, float[]> kernels = null;
	HashMap<Integer, FloatProcessor> convolved = null;
	private int halfWidth;
	private double maxDistance2;

	/*
	 * (non-Javadoc)
	 *
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	@Override
	public int setup(String arg, ImagePlus imp)
	{
		UsageTracker.recordPlugin(this.getClass(), arg);

		if (imp == null)
			return DONE;
		if (imp.getRoi() == null || imp.getRoi().getType() != Roi.POINT)
		{
			IJ.error("Please select a centre point using the ROI tool");
			return DONE;
		}
		this.imp = imp;
		kernels = null;
		roi = (PointRoi) imp.getRoi();
		xpoints = roi.getPolygon().xpoints;
		ypoints = roi.getPolygon().ypoints;

		return flags;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see ij.plugin.filter.ExtendedPlugInFilter#showDialog(ij.ImagePlus, java.lang.String,
	 * ij.plugin.filter.PlugInFilterRunner)
	 */
	@Override
	public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr)
	{
		GenericDialog gd = new GenericDialog(TITLE);
		moreOptions = IJ.shiftKeyDown();

		gd.addSlider("Cell_radius", 10, 30, cellRadius);
		gd.addSlider("Tolerance", 0.5, 1.5, tolerance);
		gd.addSlider("Kernel_width", 7, 19, kernelWidth);
		gd.addCheckbox("Dark_edge", darkEdge);
		gd.addSlider("Kernel_smoothing", 1.0, 5.5, kernelSmoothing);
		gd.addSlider("Polygon_smoothing", 0, 5, polygonSmoothing);
		gd.addSlider("Weighting_gamma", 0.05, 5, weightingGamma);
		gd.addSlider("Iterations", 1, 10, iterations);
		gd.addCheckbox("Show_elliptical_fit", ellipticalFit);
		gd.addSlider("Dilate", 0, 5, dilate);

		if (moreOptions)
			gd.addCheckbox("Debug", debug);
		else
			debug = false;

		gd.addHelp(gdsc.help.URL.UTILITY);

		gd.addPreviewCheckbox(pfr);
		gd.addDialogListener(this);
		gd.showDialog();

		// Final run: stop debug mode
		debug = false;

		if (gd.wasCanceled() || !dialogItemChanged(gd, null))
		{
			imp.setOverlay(null); // Clear preview overlay
			return DONE;
		}

		if (imp.getNFrames() > 1 || imp.getNSlices() > 1)
		{
			gd = new GenericDialog("Process Stack?");
			gd.addMessage("Process multiple slices (" + (imp.getNFrames() * imp.getNSlices()) + ")");
			if (imp.getNFrames() > 1)
				gd.addCheckbox("All_frames (" + imp.getNFrames() + ")", false);
			if (imp.getNSlices() > 1)
				gd.addCheckbox("All_slices (" + imp.getNSlices() + ")", false);
			gd.enableYesNoCancel("Yes", "No");
			gd.showDialog();
			if (gd.wasCanceled())
				return DONE;
			if (imp.getNFrames() > 1)
				processAllFrames = gd.getNextBoolean();
			if (imp.getNSlices() > 1)
				processAllSlices = gd.getNextBoolean();
		}

		// Switch from the image overlay to mask output
		buildMaskOutput = true;

		return flags; //IJ.setupDialog(imp, flags);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see ij.gui.DialogListener#dialogItemChanged(ij.gui.GenericDialog, java.awt.AWTEvent)
	 */
	@Override
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e)
	{
		final int oldCellRadius = cellRadius;
		final double oldTolerance = tolerance;
		final boolean oldDarkEdge = darkEdge;
		final double oldKernelSmoothing = kernelSmoothing;
		final int oldKernelWidth = kernelWidth;

		cellRadius = (int) gd.getNextNumber();
		tolerance = gd.getNextNumber();
		kernelWidth = (int) gd.getNextNumber();
		darkEdge = gd.getNextBoolean();
		kernelSmoothing = gd.getNextNumber();
		polygonSmoothing = (int) gd.getNextNumber();
		weightingGamma = gd.getNextNumber();
		iterations = (int) gd.getNextNumber();
		ellipticalFit = gd.getNextBoolean();
		dilate = (int) gd.getNextNumber();
		if (moreOptions)
			debug = gd.getNextBoolean();

		// Round down to nearest odd number and check minimum size
		if (kernelWidth % 2 == 0)
			kernelWidth--;
		if (kernelWidth < 3)
			kernelWidth = 3;
		if (cellRadius < 1)
			cellRadius = 1;
		if (iterations < 1)
			iterations = 1;

		// Check if the convolutions need recalculating
		if (oldCellRadius != cellRadius || oldTolerance != tolerance || oldKernelWidth != kernelWidth ||
				oldDarkEdge != darkEdge || oldKernelSmoothing != kernelSmoothing)
			kernels = null;

		return true;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see ij.plugin.filter.ExtendedPlugInFilter#setNPasses(int)
	 */
	@Override
	public void setNPasses(int nPasses)
	{
		// Do nothing
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	@Override
	public void run(ImageProcessor inputProcessor)
	{
		// This can be called by the preview or as the final run

		// Final: Create a new mask image
		if (buildMaskOutput)
		{
			final int w = inputProcessor.getWidth();
			final int h = inputProcessor.getHeight();
			final boolean useShort = (xpoints.length > 255);

			// Build a list of the frames to process
			final int channel = imp.getChannel();
			final int[] frames = (processAllFrames) ? sequence(imp.getNFrames()) : new int[] { imp.getFrame() };
			final int[] slices = (processAllSlices) ? sequence(imp.getNSlices()) : new int[] { imp.getSlice() };

			final int nFrames = frames.length;
			final int nSlices = slices.length;
			final int size = nFrames * nSlices;
			final boolean resetConvolved = (size > 1);

			final ImageStack stack = new ImageStack(w, h, size);
			final ImageStack inputStack = this.imp.getImageStack();

			int n = 1;
			for (final int frame : frames)
				for (final int slice : slices)
				{
					final String label = "t" + frame + "z" + slice;
					IJ.showStatus("Processing " + label);
					IJ.showProgress(n - 1, size);

					final ImageProcessor maskIp = (useShort) ? new ShortProcessor(w, h) : new ByteProcessor(w, h);

					final int index = imp.getStackIndex(channel, slice, frame);
					final ImageProcessor ip = inputStack.getProcessor(index);

					if (resetConvolved)
						convolved = null;
					final PolygonRoi[] cells = findCells(ip);

					for (int i = 0; i < cells.length; i++)
					{
						final PolygonRoi cell = cells[i];
						final Rectangle b = cell.getBounds();
						cell.setLocation(0, 0); // Remove the cell offset to allow it to be created
						final ByteProcessor cellIp = createFilledCell(b.width, b.height, cell, i + 1);
						maskIp.copyBits(cellIp, b.x, b.y, Blitter.COPY_ZERO_TRANSPARENT);
					}

					stack.setPixels(maskIp.getPixels(), n);
					stack.setSliceLabel(label, n);
					n++;
				}

			// Create the output image
			final String title = imp.getTitle() + " Cell Outline";
			ImagePlus maskImp = WindowManager.getImage(title);
			if (maskImp == null)
			{
				maskImp = new ImagePlus(title, stack);
				maskImp.setOpenAsHyperStack(true);
				maskImp.setDimensions(1, nSlices, nFrames);
				maskImp.show();
			}
			else
				maskImp.setStack(stack, 1, nSlices, nFrames);
			maskImp.setRoi(roi);
			applyColorModel(maskImp);
			maskImp.setDisplayRange(0, xpoints.length);
			maskImp.updateAndDraw();
		}
		// Preview: Draw the polygon outline as an overlay
		else
		{
			final Overlay overlay = new Overlay();
			overlay.setStrokeColor(Color.green);
			overlay.setFillColor(null);

			final PolygonRoi[] cells = findCells(inputProcessor);
			IJ.showStatus("");
			IJ.showProgress(1);

			if (cells == null)
				return;

			for (final PolygonRoi cell : cells)
				overlay.add(cell);
			imp.setOverlay(overlay);
		}
	}

	private static int[] sequence(int n)
	{
		final int[] s = new int[n];
		for (int i = 0; i < n; i++)
			s[i] = i + 1;
		return s;
	}

	private PolygonRoi[] findCells(ImageProcessor inputProcessor)
	{
		// Limit processing to where it is needed
		bounds = createBounds(inputProcessor);
		ImageProcessor ip = inputProcessor.duplicate();
		ip.setRoi(bounds);
		ip = ip.crop();

		if (kernels == null)
		{
			kernels = createKernels();
			convolved = null;
		}
		if (convolved == null)
			convolved = convolveImage(ip, kernels);
		//showConvolvedImages(convolved);

		if (Thread.currentThread().isInterrupted())
			return null;

		FloatProcessor combinedIp = null;
		Blitter b = null;
		ImagePlus combinedImp = null;

		if (debug)
		{
			combinedIp = new FloatProcessor(ip.getWidth(), ip.getHeight());
			b = new FloatBlitter(combinedIp);
			combinedImp = displayImage(combinedIp, "Combined edge projection");
		}

		final PolygonRoi[] cells = new PolygonRoi[xpoints.length];

		if (!this.buildMaskOutput)
			IJ.showStatus("Finding cells ...");

		// Process each point
		for (int n = 0; n < xpoints.length; n++)
		{
			if (!this.buildMaskOutput)
				IJ.showProgress(n, xpoints.length);

			final int cx = xpoints[n] - bounds.x;
			final int cy = ypoints[n] - bounds.y;

			// Restrict bounds using the cell radius and tolerance
			final int extra = (int) Math.ceil(cellRadius + cellRadius * tolerance + kernelWidth / 2);
			final int minx = Math.max(0, cx - extra);
			final int miny = Math.max(0, cy - extra);
			final int maxx = Math.min(ip.getWidth() - 1, cx + extra);
			final int maxy = Math.min(ip.getHeight() - 1, cy + extra);
			final Rectangle pointBounds = new Rectangle(minx, miny, maxx - minx + 1, maxy - miny + 1);

			// Calculate the angle
			final FloatProcessor angle = createAngleProcessor(ip, cx, cy, pointBounds);
			//if (debug) displayImage(angle, "Angle");

			if (Thread.currentThread().isInterrupted())
				return null;
			final FloatProcessor edgeProjection = computeEdgeProjection(convolved, pointBounds, angle);

			// Initialise the edge as a circle.
			PolygonRoi cell = null;
			double[] params = { cx - minx, cy - miny, cellRadius, cellRadius, cellRadius, 0 };
			final double range = cellRadius * 0.9;

			// Iterate to find the best cell outline
			boolean returnEllipticalFit = ellipticalFit;
			for (int iter = 0; iter < iterations; iter++)
			{
				// Use the current elliptical edge to define the weights for the edge projection
				final FloatProcessor weights = createWeightMap(pointBounds, params, range);
				if (Thread.currentThread().isInterrupted())
					return null;

				if (debug)
					displayImage(weights, "Weight map");

				final FloatProcessor weightedEdgeProjection = applyWeights(edgeProjection, weights);

				if (debug)
				{
					b.copyBits(weightedEdgeProjection, pointBounds.x, pointBounds.y, Blitter.ADD);
					combinedIp.resetMinAndMax();
					combinedImp.updateAndDraw();
					displayImage(weightedEdgeProjection, "Weighted edge projection");
				}

				cell = findPolygonalCell((int) Math.round(params[0]), (int) Math.round(params[1]),
						weightedEdgeProjection, angle);

				final FloatProcessor weightMap = weightedEdgeProjection; // weights
				final double[] newParams = fitPolygonalCell(cell, params, weightMap, angle);

				if (newParams == null)
				{
					returnEllipticalFit = false;
					break;
				}

				// Set the parameters for the weight map
				params = newParams;
				// Update the range to become smaller for a tighter fit
				//range = Math.max(3, range * 0.9);
			}

			// Return either the fitted elliptical cell or the last polygon outline
			if (returnEllipticalFit)
			{
				final EllipticalCell e = new EllipticalCell();
				final FloatPolygon ellipse = e.drawEllipse(params);
				cell = new PolygonRoi(ellipse.xpoints, ellipse.ypoints, ellipse.npoints, Roi.POLYGON);
			}

			PolygonRoi finalCell = cell;
			if (dilate > 0)
			{
				// Dilate the cell and then trace the new outline
				final ByteProcessor bp = new ByteProcessor(pointBounds.width, pointBounds.height);
				bp.setColor(CELL & 0xff);
				bp.draw(cell);
				for (int i = 0; i < dilate; i++)
					dilate(bp);
				cell = traceOutline(bp);
				if (cell != null)
					finalCell = cell;
			}

			final Rectangle pos = finalCell.getBounds();

			// Does not work in IJ 1.46+
			//finalCell.setLocation(pos.x + bounds.x + pointBounds.x, pos.y + bounds.y + pointBounds.y);

			// Create a new Polygon with the correct coordinates. This is required since IJ 1.46
			// since setting the location is not passed through when drawing an overlay
			final int[] xCoords = finalCell.getXCoordinates();
			final int[] yCoords = finalCell.getYCoordinates();
			final int nPoints = finalCell.getNCoordinates();
			for (int i = 0; i < nPoints; i++)
			{
				xCoords[i] += pos.x + bounds.x + pointBounds.x;
				yCoords[i] += pos.y + bounds.y + pointBounds.y;
			}

			finalCell = new PolygonRoi(xCoords, yCoords, nPoints, Roi.POLYGON);

			cells[n] = finalCell;
		}

		return cells;
	}

	private static void applyColorModel(ImagePlus imp)
	{
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
	 * @param pointBounds
	 * @param params
	 * @param range
	 * @return
	 */
	private FloatProcessor createWeightMap(Rectangle pointBounds, double[] params, double range)
	{
		final EllipticalCell cell = new EllipticalCell();
		final FloatPolygon ellipse = cell.drawEllipse(params);

		final ByteProcessor mask = new ByteProcessor(pointBounds.width, pointBounds.height);
		mask.setValue(255);
		mask.draw(new PolygonRoi(ellipse.xpoints, ellipse.ypoints, ellipse.npoints, Roi.POLYGON));

		final EDM edm = new EDM();
		final FloatProcessor map = edm.makeFloatEDM(mask, (byte) 255, false);
		if (map == null)
			// Preview thread has been interrupted
			return null;
		map.invert();
		final double max = map.getMax();
		final double min = Math.max(max - range, 0);
		final float[] data = (float[]) map.getPixels();
		for (int i = 0; i < data.length; i++)
			data[i] = (float) ((data[i] > min) ? (data[i] - min) / range : 0f);

		// Apply a gamma function for a smoother roll-off
		double g = weightingGamma;
		if (g > 0)
		{
			g = 1.0 / g;
			for (int i = 0; i < data.length; i++)
				data[i] = (float) Math.pow(data[i], g);
		}

		if (debug)
		{
			map.resetMinAndMax();
			final ImagePlus mapImp = displayImage(map, "Current weight map");
			mapImp.updateAndDraw();
		}

		return map;
	}

	private static FloatProcessor applyWeights(FloatProcessor edgeProjection, FloatProcessor weights)
	{
		final FloatProcessor ip = new FloatProcessor(edgeProjection.getWidth(), edgeProjection.getHeight());
		final float[] e = (float[]) edgeProjection.getPixels();
		final float[] w = (float[]) weights.getPixels();
		final float[] p = (float[]) ip.getPixels();
		for (int i = 0; i < p.length; i++)
			p[i] = e[i] * w[i];
		return ip;
	}

	private static ImagePlus displayImage(ImageProcessor maskIp, String title)
	{
		ImagePlus maskImp = WindowManager.getImage(title);
		if (maskImp == null)
		{
			maskImp = new ImagePlus(title, maskIp);
			maskImp.show();
		}
		else
			maskImp.setProcessor(maskIp);
		//maskImp.updateAndDraw();
		return maskImp;
	}

	/**
	 * Find the bounding rectangle that contains all the pixels that will be required for processing
	 *
	 * @param ip
	 * @return
	 */
	private Rectangle createBounds(ImageProcessor ip)
	{
		// Get the range of clicked points
		int minx = Integer.MAX_VALUE;
		int maxx = 0;
		for (final int x : xpoints)
		{
			if (minx > x)
				minx = x;
			if (maxx < x)
				maxx = x;
		}
		int miny = Integer.MAX_VALUE;
		int maxy = 0;
		for (final int y : ypoints)
		{
			if (miny > y)
				miny = y;
			if (maxy < y)
				maxy = y;
		}

		// Add the cell width, tolerance and kernel size to get the total required limits
		final int extra = (int) Math.ceil(cellRadius + cellRadius * tolerance + kernelWidth / 2);
		minx -= extra;
		miny -= extra;
		maxx += extra;
		maxy += extra;
		minx = Math.max(0, minx);
		miny = Math.max(0, miny);
		maxx = Math.min(ip.getWidth() - 1, maxx);
		maxy = Math.min(ip.getHeight() - 1, maxy);

		return new Rectangle(minx, miny, maxx - minx + 1, maxy - miny + 1);
	}

	/**
	 * Build the convolution kernels
	 *
	 * @return
	 */
	private HashMap<Integer, float[]> createKernels()
	{
		final HashMap<Integer, float[]> kernels = new HashMap<>();
		rotationAngles.clear();

		// Used to weight the kernel from the distance to the centre
		halfWidth = kernelWidth / 2;
		maxDistance2 = halfWidth * halfWidth * 2;

		// Build a set convolution kernels at 6 degree intervals
		final int cx = halfWidth, cy = halfWidth;
		final double degreesToRadians = Math.PI / 180.0;
		for (int rotation = 0; rotation < 180; rotation += 12)
		{
			rotationAngles.add(rotation);
			final FloatProcessor fp = new FloatProcessor(kernelWidth, kernelWidth);

			//createAliasedLines(halfWidth, cx, cy, degreesToRadians, rotation, fp);

			// Build curves using the cell size.
			final double centreX = cx - Math.cos(rotation * degreesToRadians) * cellRadius;
			final double centreY = cy - Math.sin(rotation * degreesToRadians) * cellRadius;

			createDoGKernel(fp, centreX, centreY, cellRadius, 1.0);

			// Draw circles to use as a directional edge filter
			//drawCircle(fp, centreX, centreY, cellRadius + 1, -1 * value);
			//drawCircle(fp, centreX, centreY, cellRadius, 2 * value);
			//drawCircle(fp, centreX, centreY, cellRadius - 1, -1 * value);

			kernels.put(rotation, (float[]) fp.getPixels());
		}

		// Copy the kernels for the second half of the circle
		for (final int rotation : rotationAngles.toArray(new Integer[0]))
		{
			rotationAngles.add(rotation + 180);
			FloatProcessor fp = new FloatProcessor(kernelWidth, kernelWidth, kernels.get(rotation), null);
			fp = (FloatProcessor) fp.duplicate();
			fp.flipHorizontal();
			fp.flipVertical();
			kernels.put(rotation + 180, (float[]) fp.getPixels());
		}

		// Show for debugging
		//for (int rotation : rotationAngles)
		//{
		//	FloatProcessor fp = new FloatProcessor(kernelWidth, kernelWidth, kernels.get(rotation), null);
		//	new ImagePlus("Rotation " + rotation, fp).show();
		//}

		return kernels;
	}

	/**
	 * Create a 1D Difference-of-Gaussians kernel using the distance from the centre minus the cell radius to determine
	 * the x distance.
	 *
	 * @param fp
	 * @param centreX
	 * @param centreY
	 * @param cellRadius
	 * @param width
	 */
	private void createDoGKernel(FloatProcessor fp, double centreX, double centreY, int cellRadius, double width)
	{
		// 1.6:1 is an approximation of the Laplacian of Gaussians
		double sigma1 = 1.6 * width;
		double sigma2 = width;

		if (kernelSmoothing > 0)
		{
			sigma1 *= kernelSmoothing;
			sigma2 *= kernelSmoothing;
		}

		final float[] data = (float[]) fp.getPixels();
		for (int y = 0, i = 0; y < fp.getHeight(); y++)
			for (int x = 0; x < fp.getWidth(); x++, i++)
			{
				final double dx = centreX - x;
				final double dy = centreY - y;
				final double x0 = cellRadius - Math.sqrt(dx * dx + dy * dy);

				float value = (float) (gaussian(x0, sigma1) - gaussian(x0, sigma2));

				if (!darkEdge)
					value = -value;

				// Weight the amount using the distance from the centre
				final double d = (x - halfWidth) * (x - halfWidth) + (y - halfWidth) * (y - halfWidth);
				value *= 1 - (d / maxDistance2);

				data[i] = value;
			}
	}

	private static double gaussian(double x0, double sigma)
	{
		return 1.0 / (2 * Math.PI * sigma * sigma) * Math.exp(-0.5 * x0 * x0 / (sigma * sigma));
	}

	/**
	 * Old method used to create straight line kernels
	 *
	 * @param halfWidth
	 * @param cx
	 * @param cy
	 * @param degreesToRadians
	 * @param rotation
	 * @param fp
	 */
	@SuppressWarnings("unused")
	private void createAliasedLines(int halfWidth, int cx, int cy, double degreesToRadians, int rotation,
			FloatProcessor fp)
	{
		// Calculate the direction of the line
		double dx = Math.sin(rotation * degreesToRadians);
		double dy = Math.cos(rotation * degreesToRadians);

		// Normalise so that each increment moves one pixel in either X or Y
		final double norm = Math.max(Math.abs(dx), Math.abs(dy));
		dx /= norm;
		dy /= norm;

		// Create aliased lines
		for (int i = 0; i <= halfWidth; i++)
			add(fp, cx + i * dx, cy + i * dy, 1);
		for (int i = 1; i <= halfWidth; i++)
			add(fp, cx - i * dx, cy - i * dy, 1);
	}

	/**
	 * Spread a value of 1 using bilinear weighting around the point
	 *
	 * @param fp
	 * @param x
	 * @param y
	 * @param value
	 */
	private void add(FloatProcessor fp, double x, double y, float value)
	{
		final int x1 = (int) Math.floor(x);
		final int y1 = (int) Math.floor(y);

		// Ignore out-of-bounds
		if (x1 < -1 || x1 > fp.getWidth() || y1 < -1 || y1 > fp.getHeight())
			return;

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

	private static void add(FloatProcessor fp, int x, int y, double weight)
	{
		final float value = fp.getPixelValue(x, y);
		fp.putPixelValue(x, y, value + weight);
	}

	private HashMap<Integer, FloatProcessor> convolveImage(ImageProcessor ip, HashMap<Integer, float[]> kernels)
	{
		HashMap<Integer, FloatProcessor> convolved = new HashMap<>();

		// Convolve image with each
		for (final int rotation : rotationAngles)
		{
			if (!this.buildMaskOutput)
				IJ.showStatus("Convolving " + rotation);
			final float[] kernel = kernels.get(rotation);
			final FloatProcessor fp = (ip instanceof FloatProcessor) ? (FloatProcessor) ip.duplicate()
					: ip.toFloat(0, null);
			fp.convolve(kernel, kernelWidth, kernelWidth);
			convolved.put(rotation, fp);
			if (Thread.currentThread().isInterrupted())
			{
				convolved = null;
				break;
			}
		}
		if (!this.buildMaskOutput)
		{
			IJ.showProgress(1); // Convolver modifies the progress tracker
			IJ.showStatus("");
		}
		return convolved;
	}

	/**
	 * Debugging method to show the results of convolution
	 *
	 * @param convolved
	 */
	@SuppressWarnings("unused")
	private void showConvolvedImages(HashMap<Integer, FloatProcessor> convolved)
	{
		final ImageProcessor ip = convolved.get(0);
		final ImageStack stack = new ImageStack(ip.getWidth(), ip.getHeight());
		for (final int rotation : rotationAngles)
			stack.addSlice("Rotation " + rotation, convolved.get(rotation));

		final ImagePlus imp = new ImagePlus("Membrane filter", stack);
		imp.show();

		// Output different projections of the results
		final ZProjector projector = new ZProjector(imp);
		for (int projectionMethod = 0; projectionMethod < ZProjector.METHODS.length; projectionMethod++)
		{
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
	 * @param ip
*            the image
	 * @param cx
	 *            the cx
	 * @param cy
	 *            the cy
	 * @param pointBounds
	 *            the point bounds
	 * @return the float processor
	 */
	private static FloatProcessor createAngleProcessor(ImageProcessor ip, int cx, int cy, Rectangle pointBounds)
	{
		// Find the bounds to process
		final int minx = pointBounds.x;
		final int miny = pointBounds.y;
		final int maxx = minx + pointBounds.width;
		final int maxy = miny + pointBounds.height;

		final FloatProcessor angle = new FloatProcessor(pointBounds.width, pointBounds.height);
		for (int y = miny, index = 0; y < maxy; y++)
			for (int x = minx; x < maxx; x++, index++)
			{
				final int dx = cx - x;
				final int dy = cy - y;
				final float a = (float) (Math.atan2(dy, dx) * 180.0 / Math.PI);
				angle.setf(index, a + 180f); // Convert to 0-360 domain
			}
		return angle;
	}

	private FloatProcessor computeEdgeProjection(HashMap<Integer, FloatProcessor> convolved, Rectangle pointBounds,
			FloatProcessor angle)
	{
		final float[] a = (float[]) angle.getPixels();

		// Do a projection of all membrane filters
		final float[][] stack = new float[rotationAngles.size()][];
		for (int i = 0; i < rotationAngles.size(); i++)
		{
			final int rotation = rotationAngles.get(i);
			FloatProcessor fp = (FloatProcessor) convolved.get(rotation).duplicate();
			fp.setRoi(pointBounds);
			fp = (FloatProcessor) fp.crop();
			if (darkEdge)
				fp.invert();
			final float[] p = (float[]) fp.getPixels();

			// Do a projection of membrane filters convolved with a filter roughly perpendicular to the edge
			for (int j = 0; j < p.length; j++)
			{
				// Check if angle is close enough
				float diff = (a[j] > rotation) ? a[j] - rotation : rotation - a[j];
				if (diff >= 180)
					diff -= 360;
				if (Math.abs(diff) > 30)
					p[j] = 0;
			}

			stack[i] = p;
		}

		// Perform Z-projection
		final FloatProcessor ip2 = new FloatProcessor(pointBounds.width, pointBounds.height);
		final float[] pixels = (float[]) ip2.getPixels();
		for (final float[] tmp : stack)
			// Max intensity
			for (int i = 0; i < tmp.length; i++)
				if (pixels[i] < tmp[i])
					pixels[i] = tmp[i];

		return ip2;
	}

	private static final byte CELL = (byte) 255;

	private static FloatProcessor cropToValues(FloatProcessor fp, Rectangle cropBounds)
	{
		// Find the bounds
		int minx = fp.getWidth();
		int miny = fp.getHeight();
		int maxx = 0;
		int maxy = 0;
		final float[] data = (float[]) fp.getPixels();
		for (int i = 0; i < data.length; i++)
			if (data[i] != 0)
			{
				final int x = i % fp.getWidth();
				final int y = i / fp.getWidth();
				if (minx > x)
					minx = x;
				if (maxx < x)
					maxx = x;
				if (miny > y)
					miny = y;
				if (maxy < y)
					maxy = y;
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
	 * <p>
	 * The ellipse is constructed using two values for the major axis. This allows the function to draw two ellipses
	 * back-to-back. The input parameters can be converted into the actual first and second major axis values using the
	 * helper functions.
	 */
	class EllipticalCell
	{
		/**
		 * Convert the input function parameters for the major axis into the first actual major axis.
		 *
		 * @param axis1
		 *            the axis 1
		 * @param axis2
		 *            the axis 2
		 * @return the major 1
		 */
		double getMajor1(double axis1, double axis2)
		{
			return (2 * axis1 + axis2) / 3;
		}

		/**
		 * Convert the input function parameters for the major axis into the second actual major axis.
		 *
		 * @param axis1
		 *            the axis 1
		 * @param axis2
		 *            the axis 2
		 * @return the major 2
		 */
		double getMajor2(double axis1, double axis2)
		{
			return (2 * axis2 + axis1) / 3;
		}

		/**
		 * Draws the elliptical cell.
		 *
		 * @param params
		 *            the params
		 * @return the float polygon
		 */
		FloatPolygon drawEllipse(final double[] params)
		{
			final double centreX = params[0];
			final double centreY = params[1];
			final double axis1 = params[2];
			final double axis2 = params[3];
			final double minor = params[4];
			final double phi = params[5];

			return drawEllipse(centreX, centreY, axis1, axis2, minor, phi);
		}

		/**
		 * Draw an elliptical path of points. The ellipse is constructed as two half ellipses allowing a two different
		 * major axes lengths, one for each side (i.e. an egg shape).
		 *
		 * @param centreX
		 *            the centre X
		 * @param centreY
		 *            the centre Y
		 * @param axis1
		 *            the axis 1
		 * @param axis2
		 *            the axis 2
		 * @param minor
		 *            the minor
		 * @param phi
		 *            The angle from X-axis and the major axis of the ellipse
		 * @return the float polygon
		 */
		FloatPolygon drawEllipse(final double centreX, final double centreY, final double axis1, final double axis2,
				final double minor, final double phi)
		{
			final int nPoints = 90;
			final double arcAngle = 2 * Math.PI / nPoints;
			final float[] xPoints = new float[nPoints];
			final float[] yPoints = new float[nPoints];

			int n = 0;

			final double major1 = getMajor1(axis1, axis2);
			final double major2 = getMajor2(axis1, axis2);

			final double angleLimit = Math.PI / 2;
			double a1 = major1 * Math.cos(phi);
			double a2 = major1 * Math.sin(phi);
			final double b1 = minor * Math.sin(phi);
			final double b2 = minor * Math.cos(phi);

			// Create points around the start of the first half
			for (double angle = -Math.PI; angle < -angleLimit; angle += arcAngle)
			{
				xPoints[n] = (float) (centreX + a1 * Math.cos(angle) - b1 * Math.sin(angle));
				yPoints[n] = (float) (centreY + a2 * Math.cos(angle) + b2 * Math.sin(angle));
				n++;
			}

			// Create points around the second half
			a1 = major2 * Math.cos(phi);
			a2 = major2 * Math.sin(phi);
			for (double angle = -angleLimit; angle < angleLimit; angle += arcAngle)
			{
				xPoints[n] = (float) (centreX + a1 * Math.cos(angle) - b1 * Math.sin(angle));
				yPoints[n] = (float) (centreY + a2 * Math.cos(angle) + b2 * Math.sin(angle));
				n++;
			}

			// Create points around the rest of the first half
			a1 = major1 * Math.cos(phi);
			a2 = major1 * Math.sin(phi);
			for (double angle = angleLimit; angle < Math.PI && n < nPoints; angle += arcAngle)
			{
				xPoints[n] = (float) (centreX + a1 * Math.cos(angle) - b1 * Math.sin(angle));
				yPoints[n] = (float) (centreY + a2 * Math.cos(angle) + b2 * Math.sin(angle));
				n++;
			}

			return new FloatPolygon(xPoints, yPoints, nPoints);
		}
	}

	/**
	 * Try and find a polygon that traces a path around the detected edges.
	 *
	 * @param cx
	 *            the cx
	 * @param cy
	 *            the cy
	 * @param fp
	 *            the fp
	 * @param angle
	 *            the angle
	 * @return the polygon roi
	 */
	private PolygonRoi findPolygonalCell(int cx, int cy, FloatProcessor fp, FloatProcessor angle)
	{
		final Rectangle cropBounds = new Rectangle();
		final FloatProcessor cropFp = cropToValues(fp, cropBounds);
		angle.setRoi(cropBounds);
		final FloatProcessor cropAngles = (FloatProcessor) angle.crop();

		if (debug)
		{
			final ImagePlus fpImp = displayImage(cropFp, "Current edge outline");
			fpImp.updateAndDraw();
		}

		//ImagePlus mapImp = displayImage(cropAngles, "Current angles");
		//mapImp.updateAndDraw();

		// Divide the region around the centre into segments and
		// find the maxima in each segment
		final float[] a = (float[]) cropAngles.getPixels();

		final int segmentArcWidth = 5;
		final float[] max = new float[360 / segmentArcWidth + 1];
		final int[] index = new int[max.length];

		for (int i = 0; i < a.length; i++)
		{
			final int segment = (int) (a[i] / segmentArcWidth);

			if (max[segment] < cropFp.getf(i))
			{
				max[segment] = cropFp.getf(i);
				index[segment] = i;
			}
		}

		// Count the number of points.
		// Skip consecutive segments to ensure the sampled points are spread out.
		int nPoints = 0;
		for (int i = 0; i < 360 / segmentArcWidth; i += 2)
			if (max[i] > 0)
				nPoints++;

		// Return the polygon
		float[] xPoints = new float[nPoints];
		float[] yPoints = new float[nPoints];

		nPoints = 0;
		for (int i = 0; i < 360 / segmentArcWidth; i += 2)
			if (max[i] > 0)
			{
				xPoints[nPoints] = index[i] % cropBounds.width + cropBounds.x;
				yPoints[nPoints] = index[i] / cropBounds.width + cropBounds.y;
				nPoints++;
			}

		// Perform coordinate smoothing
		int window = polygonSmoothing;
		if (window > 0)
		{
			final float[] newXPoints = new float[nPoints];
			final float[] newYPoints = new float[nPoints];
			window = Math.min(nPoints / 4, window);
			for (int i = 0; i < nPoints; i++)
			{
				double sumx = 0, sumy = 0, n = 0;
				for (int j = -window; j <= window; j++)
				{
					final double w = 1; //(window - Math.abs(j) + 1.0) / (window + 1);
					final int ii = (i + j + nPoints) % nPoints;
					sumx += xPoints[ii] * w;
					sumy += yPoints[ii] * w;
					n += w;
				}
				newXPoints[i] = (float) (sumx / n);
				newYPoints[i] = (float) (sumy / n);
			}
			xPoints = newXPoints;
			yPoints = newYPoints;
		}

		return new PolygonRoi(xPoints, yPoints, nPoints, Roi.POLYGON);
	}

	/**
	 * Create a byte processor of the specified dimensions and fill the ROI.
	 *
	 * @param width
	 *            the width
	 * @param height
	 *            the height
	 * @param roi
	 *            the roi
	 * @return the byte processor
	 */
	private static ByteProcessor createFilledCell(int width, int height, PolygonRoi roi)
	{
		return createFilledCell(width, height, roi, CELL & 0xff);
	}

	/**
	 * Create a byte processor of the specified dimensions and fill the ROI.
	 *
	 * @param width
	 *            the width
	 * @param height
	 *            the height
	 * @param roi
	 *            the roi
	 * @param value
	 *            the value
	 * @return the byte processor
	 */
	private static ByteProcessor createFilledCell(int width, int height, PolygonRoi roi, int value)
	{
		final ByteProcessor bp = new ByteProcessor(width, height);
		bp.setColor(value);
		bp.fill(roi);
		return bp;
	}

	/**
	 * Find an ellipse that optimises the fit to the polygon detected edges.
	 *
	 * @param roi
	 * @param params
	 * @param weightMap
	 * @param angle
	 * @return
	 */
	private double[] fitPolygonalCell(PolygonRoi roi, double[] params, FloatProcessor weightMap, FloatProcessor angle)
	{
		// Get an estimate of the starting parameters using the current polygon
		double[] startPoint = params;
		startPoint = estimateStartPoint(roi, weightMap.getWidth(), weightMap.getHeight());

		final int maxEval = 2000;
		final DifferentiableEllipticalFitFunction func = new DifferentiableEllipticalFitFunction(roi, weightMap);

		final double relativeThreshold = 100 * Precision.EPSILON;
		final double absoluteThreshold = 100 * Precision.SAFE_MIN;
		final ConvergenceChecker<PointVectorValuePair> checker = new SimplePointChecker<>(relativeThreshold,
				absoluteThreshold);
		final double initialStepBoundFactor = 10;
		final double costRelativeTolerance = 1e-10;
		final double parRelativeTolerance = 1e-10;
		final double orthoTolerance = 1e-10;
		final double threshold = Precision.SAFE_MIN;

		final LevenbergMarquardtOptimizer optimiser = new LevenbergMarquardtOptimizer(initialStepBoundFactor,
				costRelativeTolerance, parRelativeTolerance, orthoTolerance, threshold);

		try
		{
			//@formatter:off
			final LeastSquaresProblem problem = new LeastSquaresBuilder()
					.maxEvaluations(Integer.MAX_VALUE)
					.maxIterations(maxEval)
					.start(startPoint)
					.target(func.calculateTarget())
					.weight(new DiagonalMatrix(func.calculateWeights()))
					.model(func, new MultivariateMatrixFunction() {
						@Override
						public double[][] value(double[] point) throws IllegalArgumentException
						{
							return func.jacobian(point);
						}} )
					.checkerPair(checker)
					.build();
			//@formatter:on

			final Optimum solution = optimiser.optimize(problem);

			if (debug)
				IJ.log(String.format("Eval = %d (Iter = %d), RMS = %f", solution.getEvaluations(),
						solution.getIterations(), solution.getRMS()));
			return solution.getPoint().toArray();
		}
		catch (final Exception e)
		{
			IJ.log("Failed to find an elliptical solution, defaulting to polygon");
			e.printStackTrace();
		}

		return null;
	}

	/**
	 * Estimate the starting ellipse using the eccentricity around the central moments
	 * <p>
	 *
	 * @see Burger & Burge, Digital Image Processing, An Algorithmic Introduction using Java (1st Edition), pp231
	 *
	 * @param roi
	 * @param height
	 * @param width
	 * @return
	 */
	private double[] estimateStartPoint(PolygonRoi roi, int width, int height)
	{
		ByteProcessor ip = createFilledCell(width, height, roi);
		final byte[] data = (byte[]) ip.getPixels();

		//// Default processing
		//double m00 = moment(ip, 0, 0); // region area
		//double xCtr = moment(ip, 1, 0) / m00;
		//double yCtr = moment(ip, 0, 1) / m00;
		//
		//double u00 = m00; //centralMoment(ip, 0, 0);
		//double u11 = centralMoment(ip, 1, 1);
		//double u20 = centralMoment(ip, 2, 0);
		//double u02 = centralMoment(ip, 0, 2);

		// Speed up processing of the moments
		double u00 = 0;
		for (final byte b : data)
			if (b != 0)
				u00++;

		double xCtr = 0, yCtr = 0;
		for (int v = 0, i = 0; v < ip.getHeight(); v++)
			for (int u = 0; u < ip.getWidth(); u++, i++)
				if (data[i] != 0)
				{
					xCtr += u;
					yCtr += v;
				}
		xCtr /= u00;
		yCtr /= u00;

		double u11 = 0;
		double u20 = 0;
		double u02 = 0;
		for (int v = 0, i = 0; v < ip.getHeight(); v++)
			for (int u = 0; u < ip.getWidth(); u++, i++)
				if (data[i] != 0)
				{
					final double dx = u - xCtr;
					final double dy = v - yCtr;
					u11 += dx * dy;
					u20 += dx * dx;
					u02 += dy * dy;
				}

		// Calculate the ellipsoid
		final double A = 2 * u11;
		final double B = u20 - u02;

		final double angle = 0.5 * Math.atan2(A, B);

		final double a1 = u20 + u02 + Math.sqrt((u20 - u02) * (u20 - u02) + 4 * u11 * u11);
		final double a2 = u20 + u02 - Math.sqrt((u20 - u02) * (u20 - u02) + 4 * u11 * u11);

		final double ra = Math.sqrt(2 * a1 / u00);
		final double rb = Math.sqrt(2 * a2 / u00);

		final double[] params = new double[] { xCtr, yCtr, ra, ra, rb, angle };

		if (debug)
		{
			final EllipticalCell cell = new EllipticalCell();
			final FloatPolygon ellipse = cell.drawEllipse(params);
			ip = createFilledCell(width, height,
					new PolygonRoi(ellipse.xpoints, ellipse.ypoints, ellipse.npoints, Roi.POLYGON));
			ip.setMinAndMax(0, CELL);
			displayImage(ip, "Start estimate");
		}

		return params;
	}

	static final int BACKGROUND = 0;

	/**
	 * Compute the moment of an image.
	 *
	 * @param ip
*            the image
	 * @param p
	 *            the p
	 * @param q
	 *            the q
	 * @return the double
	 */
	public static double moment(ImageProcessor ip, int p, int q)
	{
		double Mpq = 0.0;
		for (int v = 0, i = 0; v < ip.getHeight(); v++)
			for (int u = 0; u < ip.getWidth(); u++, i++)
				if (ip.get(i) != BACKGROUND)
					Mpq += Math.pow(u, p) * Math.pow(v, q);
		return Mpq;
	}

	/**
	 * Compute the central moment of an image
	 *
	 * @param ip
*            the image
	 * @param p
	 *            the p
	 * @param q
	 *            the q
	 * @return the double
	 */
	public static double centralMoment(ImageProcessor ip, int p, int q)
	{
		final double m00 = moment(ip, 0, 0); // region area
		final double xCtr = moment(ip, 1, 0) / m00;
		final double yCtr = moment(ip, 0, 1) / m00;
		double cMpq = 0.0;
		for (int v = 0, i = 0; v < ip.getHeight(); v++)
			for (int u = 0; u < ip.getWidth(); u++, i++)
				if (ip.get(i) != BACKGROUND)
					cMpq += Math.pow(u - xCtr, p) * Math.pow(v - yCtr, q);
		return cMpq;
	}

	/**
	 * Provides a function to score the ellipse for use in gradient based optimisation methods.
	 *
	 * @see "http://commons.apache.org/math/userguide/optimization.html"
	 */
	class DifferentiableEllipticalFitFunction extends EllipticalCell implements MultivariateVectorFunction
	{
		FloatProcessor weightMap;
		int nPoints;
		int[] xPoints;
		int[] yPoints;

		// Debugging variables
		int iter = 0;

		/**
		 * Instantiates a new differentiable elliptical fit function.
		 *
		 * @param roi
		 *            The polygon to fit
		 * @param weightMap
		 *            the weight map
		 */
		DifferentiableEllipticalFitFunction(PolygonRoi roi, FloatProcessor weightMap)
		{
			// These methods try to minimise the difference between a target value and your model value.
			// The target value is the polygon outline. The model is currently an elliptical path.
			this.weightMap = weightMap;
			nPoints = roi.getNCoordinates();
			xPoints = Arrays.copyOf(roi.getXCoordinates(), nPoints);
			yPoints = Arrays.copyOf(roi.getYCoordinates(), nPoints);
			final Rectangle bounds = roi.getBounds();
			for (int i = 0; i < nPoints; i++)
			{
				xPoints[i] += bounds.x;
				yPoints[i] += bounds.y;
			}
		}

		/**
		 * Calculate target.
		 *
		 * @return Each point to be fitted
		 */
		double[] calculateTarget()
		{
			final ByteProcessor bp = new ByteProcessor(weightMap.getWidth(), weightMap.getHeight());
			for (int i = 0; i < nPoints; i++)
				bp.putPixel(xPoints[i], yPoints[i], nPoints);

			if (debug)
			{
				bp.setMinAndMax(1, nPoints);
				displayImage(bp, "Ellipse target");
			}

			// We want to minimise the distance to the polygon points.
			// Our values will be the distances so the target can be zeros.
			final double[] target = new double[nPoints];
			return target;
		}

		/**
		 * Calculate weights.
		 *
		 * @return The weights for each point to be fitted
		 */
		double[] calculateWeights()
		{
			final double[] weights = new double[nPoints];
			for (int i = 0; i < weights.length; i++)
				weights[i] = weightMap.getPixelValue(yPoints[i], yPoints[i]);
			//weights[i] = 1;
			return weights;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see org.apache.commons.math3.analysis.MultivariateVectorFunction#value(double[])
		 */
		@Override
		public double[] value(double[] point) throws IllegalArgumentException
		{
			if (debug)
				System.out.printf("%f,%f %f,%f,%f %f\n", point[0], point[1], point[2], point[3], point[4],
						point[5] * 180.0 / Math.PI);
			return getValue(point);
		}

		private double[] getValue(double[] point) throws IllegalArgumentException
		{
			final double[] values = new double[nPoints];

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
			for (int i = 0; i < values.length; i++)
			{
				// Get the angle between the point and the centre
				final double absAngle = Math.atan2(yPoints[i] - cy, xPoints[i] - cx);

				// Adjust for the ellipse rotation
				double angle = absAngle - phi;
				// Check domain
				if (angle < -Math.PI)
					angle += 2 * Math.PI;
				if (angle > Math.PI)
					angle -= 2 * Math.PI;

				double a1, a2;
				final double b1 = minor * Math.sin(phi);
				final double b2 = minor * Math.cos(phi);

				// Create ellipse point. The shape is two ellipse halves so use the correct part.
				if (angle < -Math.PI / 2 || angle > Math.PI / 2)
				{
					a1 = major1 * Math.cos(phi);
					a2 = major1 * Math.sin(phi);
				}
				else
				{
					a1 = major2 * Math.cos(phi);
					a2 = major2 * Math.sin(phi);
				}

				final double x = cx + a1 * Math.cos(angle) - b1 * Math.sin(angle);
				final double y = cy + a2 * Math.cos(angle) + b2 * Math.sin(angle);

				bp.putPixel((int) x, (int) y, nPoints);

				// Get the distance
				double dx = x - xPoints[i];
				double dy = y - yPoints[i];
				values[i] = Math.sqrt(dx * dx + dy * dy);

				// Check if it is inside or outside the ellipse
				dx = cx - xPoints[i];
				dy = cy - yPoints[i];
				final double pointToCentre = dx * dx + dy * dy;

				dx = cx - x;
				dy = cy - y;
				final double ellipseToCentre = dx * dx + dy * dy;

				if (pointToCentre < ellipseToCentre)
					values[i] = -values[i];
			}

			if (debug)
			{
				bp.setMinAndMax(1, nPoints);
				displayImage(bp, "Ellipse points " + iter);
			}

			return values;
		}

		private double[][] jacobian(double[] variables)
		{
			final double[][] jacobian = new double[nPoints][variables.length];

			// Compute numerical differentiation
			// Param order:
			// centreX, centreY, major1, major2, minor, phi
			final double[] delta = { 1, 1, 2, 2, 2, 5 * Math.PI / 180.0 };

			for (int i = 0; i < variables.length - 1; i++)
			{
				final double[] upper = getValue(updateVariables(variables, i, delta, 1));
				final double[] lower = getValue(updateVariables(variables, i, delta, -1));

				for (int j = 0; j < jacobian.length; ++j)
					jacobian[j][i] = (upper[j] - lower[j]) / (2 * delta[i]);
			}
			// Only compute the angle gradient if the ellipse is not a circle
			if (variables[2] != variables[3] || variables[2] != variables[4])
			{
				final int i = variables.length - 1;
				final double[] upper = getValue(updateVariables(variables, i, delta, 1));
				final double[] lower = getValue(updateVariables(variables, i, delta, -1));

				for (int j = 0; j < jacobian.length; ++j)
					jacobian[j][i] = (upper[j] - lower[j]) / (2 * delta[i]);
			}

			return jacobian;
		}

		private double[] updateVariables(double[] variables, int i, double[] delta, int sign)
		{
			final double[] newVariables = Arrays.copyOf(variables, variables.length);
			newVariables[i] += delta[i] * sign;
			return newVariables;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see org.apache.commons.math3.analysis.DifferentiableMultivariateVectorFunction#jacobian()
		 */
		public MultivariateMatrixFunction jacobian()
		{
			return new MultivariateMatrixFunction()
			{
				@Override
				public double[][] value(double[] point)
				{
					return jacobian(point);
				}
			};
		}
	}

	private void dilate(ByteProcessor bp)
	{
		final byte[] data = (byte[]) bp.getPixels();
		final byte[] newData = new byte[data.length];
		initialise(bp);

		for (int index = 0; index < data.length; index++)
			if (data[index] != 0)
			{
				newData[index] = CELL;
				final int x = index % maxx;
				final int y = index / maxx;
				final boolean isInnerXY = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);

				// Use 4-connected cells
				if (isInnerXY)
					for (int d = 0; d < 8; d += 2)
						newData[index + offset[d]] = CELL;
				else
					for (int d = 0; d < 8; d += 2)
						if (isWithinXY(x, y, d))
							newData[index + offset[d]] = CELL;
			}

		bp.setPixels(newData);
	}

	private PolygonRoi traceOutline(ByteProcessor bp)
	{
		final byte[] data = (byte[]) bp.getPixels();

		initialise(bp);

		// Find first pixel
		int startIndex = 0;
		while (data[startIndex] == 0 && startIndex < data.length)
			startIndex++;
		if (startIndex == data.length)
			return null;

		final ArrayList<Point> coords = new ArrayList<>(100);
		addPoint(coords, startIndex);

		// Set start direction for search
		int searchDirection = 7;
		int index = startIndex;
		// Safety limit - The outline shouldn't be greater than the image perimeter
		int limit = (bp.getWidth() + bp.getHeight()) * 2 - 2;
		while (limit-- > 0)
		{
			final int nextDirection = findNext(data, index, searchDirection);
			if (nextDirection >= 0)
			{
				index += offset[nextDirection];
				if (index == startIndex)
					break; // End of the outline
				addPoint(coords, index);
				searchDirection = (nextDirection + 6) % 8;
			}
			else
				break; // Single point with no move direction
		}
		if (limit <= 0)
			return null;

		// Return the outline
		int nPoints = 0;
		final int[] xPoints = new int[coords.size()];
		final int[] yPoints = new int[coords.size()];
		for (final Point p : coords)
		{
			xPoints[nPoints] = p.x;
			yPoints[nPoints] = p.y;
			nPoints++;
		}

		return new PolygonRoi(xPoints, yPoints, nPoints, Roi.POLYGON);
	}

	private void addPoint(ArrayList<Point> coords, int index)
	{
		final Point p = new Point(index % maxx, index / maxx);
		//System.out.printf("%d, %d\n", p.x, p.y);
		coords.add(p);
	}

	private int findNext(byte[] data, int index, int direction)
	{
		final int x = index % maxx;
		final int y = index / maxx;
		final boolean isInnerXY = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);

		// Process the neighbours
		for (int d = 0; d < 7; d++)
		{
			if (isInnerXY || isWithinXY(x, y, direction))
			{
				final int index2 = index + offset[direction];

				// Check if foreground
				if (data[index2] != 0)
					return direction;
			}
			direction = (direction + 1) % 8;
		}

		return -1;
	}

	private int maxx, maxy, xlimit, ylimit;
	private int[] offset;
	private final int[] DIR_X_OFFSET = new int[] { 0, 1, 1, 1, 0, -1, -1, -1 };
	private final int[] DIR_Y_OFFSET = new int[] { -1, -1, 0, 1, 1, 1, 0, -1 };

	/**
	 * Initialises the global width and height variables. Creates the direction offset tables.
	 */
	private void initialise(ImageProcessor ip)
	{
		maxx = ip.getWidth();
		maxy = ip.getHeight();

		xlimit = maxx - 1;
		ylimit = maxy - 1;

		// Create the offset table (for single array 3D neighbour comparisons)
		offset = new int[DIR_X_OFFSET.length];
		for (int d = offset.length; d-- > 0;)
			offset[d] = DIR_X_OFFSET[d] + maxx * DIR_Y_OFFSET[d];
	}

	/**
	 * returns whether the neighbour in a given direction is within the image. NOTE: it is assumed that the pixel x,y
	 * itself is within the image! Uses class variables xlimit, ylimit: (dimensions of the image)-1
	 *
	 * @param x
	 *            x-coordinate of the pixel that has a neighbour in the given direction
	 * @param y
	 *            y-coordinate of the pixel that has a neighbour in the given direction
	 * @param direction
	 *            the direction from the pixel towards the neighbour
	 * @return true if the neighbour is within the image (provided that x, y is within)
	 */
	private boolean isWithinXY(int x, int y, int direction)
	{
		switch (direction)
		{
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
			case 8:
				return true;
		}
		return false;
	}
}
