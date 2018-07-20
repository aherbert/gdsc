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
package ij.plugin.filter;

import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.image.IndexColorModel;
import java.util.Properties;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.LookUpTable;
import ij.Macro;
import ij.Prefs;
import ij.Undo;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.ImageWindow;
import ij.gui.Overlay;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.macro.Interpreter;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.Colors;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.ByteStatistics;
import ij.process.ColorProcessor;
import ij.process.ColorStatistics;
import ij.process.FloatProcessor;
import ij.process.FloatStatistics;
import ij.process.FloodFiller;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.PolygonFiller;
import ij.process.ShortProcessor;
import ij.process.ShortStatistics;
import ij.text.TextPanel;
import ij.text.TextWindow;
import ij.util.Tools;

/**
 * A copy of ImageJ's Analyze Particles command. 
 * Copied from {@link ij.plugin.filter.ParticleAnalyzer}.
 *
 * <pre>
 * 	for each line do
 * 		for each pixel in this line do
 * 			if the pixel value is "inside" the threshold range then
 * 				trace the edge to mark the object
 * 				do the measurement
 * 				fill the object with a color outside the threshold range
 * 			else
 * 				continue the scan
 * </pre>
 */
@SuppressWarnings({ "unused", "javadoc" })
public class ParticleAnalyzerCopy implements PlugInFilter, Measurements
{
	/** Display results in the ImageJ console. */
	public static final int SHOW_RESULTS = 1;

	/** Obsolete, replaced by DISPLAY_SUMMARY */
	public static final int SHOW_SUMMARY = 2;

	/** Display image containing outlines of measured particles. */
	public static final int SHOW_OUTLINES = 4;

	/** Do not measure particles touching edge of image. */
	public static final int EXCLUDE_EDGE_PARTICLES = 8;

	/** Display image containing grayscales masks that identify measured particles. */
	public static final int SHOW_ROI_MASKS = 16;

	/** Display a progress bar. */
	public static final int SHOW_PROGRESS = 32;

	/** Clear ImageJ console before starting. */
	public static final int CLEAR_WORKSHEET = 64;

	/** Record starting coordinates so outline can be recreated later using doWand(x,y). */
	public static final int RECORD_STARTS = 128;

	/** Display a summary. */
	public static final int DISPLAY_SUMMARY = 256;

	/** Do not display particle outline image. */
	public static final int SHOW_NONE = 512;

	/** Flood fill to ignore interior holes. */
	public static final int INCLUDE_HOLES = 1024;

	/** Add particles to ROI Manager. */
	public static final int ADD_TO_MANAGER = 2048;

	/** Display image containing binary masks of measured particles. */
	public static final int SHOW_MASKS = 4096;

	/** Use 4-connected particle tracing. */
	public static final int FOUR_CONNECTED = 8192;

	/** Replace original image with masks. */
	public static final int IN_SITU_SHOW = 16384;

	/** Display particle outlines as an overlay. */
	public static final int SHOW_OVERLAY_OUTLINES = 32768;

	/** Display filled particle as an overlay. */
	public static final int SHOW_OVERLAY_MASKS = 65536;

	static final String OPTIONS = "ap.options";

	static final int BYTE = 0, SHORT = 1, FLOAT = 2, RGB = 3;
	static final double DEFAULT_MIN_SIZE = 0.0;
	static final double DEFAULT_MAX_SIZE = Double.POSITIVE_INFINITY;

	private static double staticMinSize = 0.0;
	private static double staticMaxSize = DEFAULT_MAX_SIZE;
	private static boolean pixelUnits;
	private static int staticOptions = Prefs.getInt(OPTIONS, CLEAR_WORKSHEET);
	private static String[] showStrings = { "Nothing", "Outlines", "Bare Outlines", "Ellipses", "Masks", "Count Masks",
			"Overlay Outlines", "Overlay Masks" };
	private static double staticMinCircularity = 0.0, staticMaxCircularity = 1.0;
	private static String prevHdr;

	/** Show nothing. */
	protected static final int NOTHING = 0;
	/** Show outlines */
	protected static final int OUTLINES = 1;
	/** Show bare outlines */
	protected static final int BARE_OUTLINES = 2;
	/** Show ellipses */
	protected static final int ELLIPSES = 3;
	/** Show masks */
	protected static final int MASKS = 4;
	/** Create ROI masks */
	protected static final int ROI_MASKS = 5;
	/** Overlay outlines */
	protected static final int OVERLAY_OUTLINES = 6;
	/** Overlay masks */
	protected static final int OVERLAY_MASKS = 7;

	/** The static show choice option. */
	protected static int staticShowChoice;

	/** The image. */
	protected ImagePlus imp;

	/** The results table. */
	protected ResultsTable rt;

	/** The analyzer. */
	protected Analyzer analyzer;

	/** The slice. */
	protected int slice;

	/** The process stack flag. */
	protected boolean processStack;

	/** The show results flag. */
	protected boolean showResults;

	/** The exclude edge particles flag. */
	protected boolean excludeEdgeParticles;

	/** The show size distribution flag. */
	protected boolean showSizeDistribution;

	/** The reset counter flag. */
	protected boolean resetCounter;

	/** The show progress flag. */
	protected boolean showProgress;

	/** The record starts flag. */
	protected boolean recordStarts;

	/** The display summary flag. */
	protected boolean displaySummary;

	/** The flood fill flag. */
	protected boolean floodFill;

	/** The add to manager flag. */
	protected boolean addToManager;

	/** The in situ show flag. */
	protected boolean inSituShow;

	/** The show results window flag. */
	protected boolean showResultsWindow = true;
	private String summaryHdr = "Slice\tCount\tTotal Area\tAverage Size\t%Area";
	private double level1, level2;
	private double minSize, maxSize;
	private double minCircularity, maxCircularity;
	private int showChoice;
	private int options;
	private int measurements;
	private Calibration calibration;
	private String arg;
	private double fillColor;
	private boolean thresholdingLUT;
	private ImageProcessor drawIP;
	private int width, height;
	private boolean canceled;
	private ImageStack outlines;
	private IndexColorModel customLut;
	private int particleCount;
	private int maxParticleCount = 0;
	private int totalCount;
	private TextWindow tw;
	private Wand wand;
	private int imageType, imageType2;
	private boolean roiNeedsImage;
	private int minX, maxX, minY, maxY;
	private ImagePlus redirectImp;
	private ImageProcessor redirectIP;
	private PolygonFiller pf;
	private Roi saveRoi;
	private int beginningCount;
	private Rectangle r;
	private ImageProcessor mask;
	private double totalArea;
	private FloodFiller ff;
	private Polygon polygon;
	/** The roi manager. */
	protected RoiManager roiManager;
	private static RoiManager staticRoiManager;
	private static ResultsTable staticResultsTable;
	private ImagePlus outputImage;
	private boolean hideOutputImage;
	private int roiType;
	private int wandMode = Wand.LEGACY_MODE;
	private Overlay overlay;
	boolean blackBackground;
	private static int defaultFontSize = 9;
	private static int nextFontSize = defaultFontSize;
	private static Color defaultFontColor = Color.red;
	private static Color nextFontColor = defaultFontColor;
	private static int nextLineWidth = 1;
	private final int fontSize = nextFontSize;
	private final Color fontColor = nextFontColor;
	/** The line width. */
	protected int lineWidth = nextLineWidth;

	/**
	 * Constructs a ParticleAnalyzerCopy.
	 *
	 * @param options
	 *            a flag word created by SHOW_RESULTS, EXCLUDE_EDGE_PARTICLES, etc.
	 * @param measurements
	 *            a flag word created by constants defined in the Measurements interface
	 * @param rt
	 *            a ResultsTable where the measurements will be stored
	 * @param minSize
	 *            the smallest particle size in pixels
	 * @param maxSize
	 *            the largest particle size in pixels
	 * @param minCirc
	 *            minimum circularity
	 * @param maxCirc
	 *            maximum circularity
	 */
	public ParticleAnalyzerCopy(int options, int measurements, ResultsTable rt, double minSize, double maxSize,
			double minCirc, double maxCirc)
	{
		this.options = options;
		this.measurements = measurements;
		this.rt = rt;
		if (this.rt == null)
			this.rt = new ResultsTable();
		this.minSize = minSize;
		this.maxSize = maxSize;
		this.minCircularity = minCirc;
		this.maxCircularity = maxCirc;
		slice = 1;
		if ((options & SHOW_ROI_MASKS) != 0)
			showChoice = ROI_MASKS;
		if ((options & SHOW_OVERLAY_OUTLINES) != 0)
			showChoice = OVERLAY_OUTLINES;
		if ((options & SHOW_OVERLAY_MASKS) != 0)
			showChoice = OVERLAY_MASKS;
		if ((options & SHOW_OUTLINES) != 0)
			showChoice = OUTLINES;
		if ((options & SHOW_MASKS) != 0)
			showChoice = MASKS;
		if ((options & SHOW_NONE) != 0)
			showChoice = NOTHING;
		if ((options & FOUR_CONNECTED) != 0)
		{
			wandMode = Wand.FOUR_CONNECTED;
			options |= INCLUDE_HOLES;
		}
		nextFontSize = defaultFontSize;
		nextFontColor = defaultFontColor;
		nextLineWidth = 1;
	}

	/**
	 * Constructs a ParticleAnalyzerCopy using the default min and max circularity values (0 and 1).
	 *
	 * @param options
	 *            the options
	 * @param measurements
	 *            the measurements
	 * @param rt
	 *            the rt
	 * @param minSize
	 *            the min size
	 * @param maxSize
	 *            the max size
	 */
	public ParticleAnalyzerCopy(int options, int measurements, ResultsTable rt, double minSize, double maxSize)
	{
		this(options, measurements, rt, minSize, maxSize, 0.0, 1.0);
	}

	/** Default constructor */
	public ParticleAnalyzerCopy()
	{
		slice = 1;
	}

	@Override
	public int setup(String arg, ImagePlus imp)
	{
		this.arg = arg;
		this.imp = imp;
		IJ.register(ParticleAnalyzerCopy.class);
		if (imp == null)
		{
			IJ.noImage();
			return DONE;
		}
		if (imp.getBitDepth() == 24 && !isThresholdedRGB(imp))
		{
			IJ.error("Particle Analyzer", "RGB images must be thresholded using\n" + "Image>Adjust>Color Threshold.");
			return DONE;
		}
		if (!showDialog())
			return DONE;
		final int baseFlags = DOES_ALL + NO_CHANGES + NO_UNDO;
		final int flags = IJ.setupDialog(imp, baseFlags);
		processStack = (flags & DOES_STACKS) != 0;
		slice = 0;
		saveRoi = imp.getRoi();
		if (saveRoi != null && saveRoi.getType() != Roi.RECTANGLE && saveRoi.isArea())
			polygon = saveRoi.getPolygon();
		imp.startTiming();
		nextFontSize = defaultFontSize;
		nextFontColor = defaultFontColor;
		nextLineWidth = 1;
		return flags;
	}

	@Override
	public void run(ImageProcessor ip)
	{
		if (canceled)
			return;
		slice++;
		if (imp.getStackSize() > 1 && processStack)
			imp.setSlice(slice);
		if (imp.getType() == ImagePlus.COLOR_RGB)
		{
			ip = (ImageProcessor) imp.getProperty("Mask");
			ip.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
		}
		if (!analyze(imp, ip))
			canceled = true;
		if (slice == imp.getStackSize())
		{
			imp.updateAndDraw();
			if (saveRoi != null)
				imp.setRoi(saveRoi);
		}
	}

	/**
	 * Displays a modal options dialog.
	 *
	 * @return true, if successful
	 */
	public boolean showDialog()
	{
		final Calibration cal = imp != null ? imp.getCalibration() : (new Calibration());
		double unitSquared = cal.pixelWidth * cal.pixelHeight;
		if (pixelUnits)
			unitSquared = 1.0;
		if (Macro.getOptions() != null)
		{
			final boolean oldMacro = updateMacroOptions();
			if (oldMacro)
				unitSquared = 1.0;
			staticMinSize = 0.0;
			staticMaxSize = DEFAULT_MAX_SIZE;
			staticMinCircularity = 0.0;
			staticMaxCircularity = 1.0;
			staticShowChoice = NOTHING;
		}
		final GenericDialog gd = new GenericDialog("Analyze Particles");
		minSize = staticMinSize;
		maxSize = staticMaxSize;
		minCircularity = staticMinCircularity;
		maxCircularity = staticMaxCircularity;
		showChoice = staticShowChoice;
		if (maxSize == 999999)
			maxSize = DEFAULT_MAX_SIZE;
		options = staticOptions;
		final String unit = cal.getUnit();
		final boolean scaled = cal.scaled();
		final String units = unit + "^2";
		int places = 0;
		final double cmin = minSize * unitSquared;
		if ((int) cmin != cmin)
			places = 2;
		final double cmax = maxSize * unitSquared;
		if ((int) cmax != cmax && cmax != DEFAULT_MAX_SIZE)
			places = 2;
		String minStr = ResultsTable.d2s(cmin, places);
		if (minStr.indexOf("-") != -1)
			for (int i = places; i <= 6; i++)
			{
				minStr = ResultsTable.d2s(cmin, i);
				if (minStr.indexOf("-") == -1)
					break;
			}
		String maxStr = ResultsTable.d2s(cmax, places);
		if (maxStr.indexOf("-") != -1)
			for (int i = places; i <= 6; i++)
			{
				maxStr = ResultsTable.d2s(cmax, i);
				if (maxStr.indexOf("-") == -1)
					break;
			}
		if (scaled)
			gd.setInsets(5, 0, 0);
		gd.addStringField("Size (" + units + "):", minStr + "-" + maxStr, 12);
		if (scaled)
		{
			gd.setInsets(0, 40, 5);
			gd.addCheckbox("Pixel units", pixelUnits);
		}
		gd.addStringField("Circularity:", IJ.d2s(minCircularity) + "-" + IJ.d2s(maxCircularity), 12);
		gd.addChoice("Show:", showStrings, showStrings[showChoice]);
		final String[] labels = new String[8];
		final boolean[] states = new boolean[8];
		labels[0] = "Display results";
		states[0] = (options & SHOW_RESULTS) != 0;
		labels[1] = "Exclude on edges";
		states[1] = (options & EXCLUDE_EDGE_PARTICLES) != 0;
		labels[2] = "Clear results";
		states[2] = (options & CLEAR_WORKSHEET) != 0;
		labels[3] = "Include holes";
		states[3] = (options & INCLUDE_HOLES) != 0;
		labels[4] = "Summarize";
		states[4] = (options & DISPLAY_SUMMARY) != 0;
		labels[5] = "Record starts";
		states[5] = false;
		labels[6] = "Add to Manager";
		states[6] = (options & ADD_TO_MANAGER) != 0;
		labels[7] = "In_situ Show";
		states[7] = (options & IN_SITU_SHOW) != 0;
		gd.addCheckboxGroup(4, 2, labels, states);
		gd.addHelp(IJ.URL + "/docs/menus/analyze.html#ap");
		gd.showDialog();
		if (gd.wasCanceled())
			return false;

		final String size = gd.getNextString(); // min-max size
		if (scaled)
			pixelUnits = gd.getNextBoolean();
		if (pixelUnits)
			unitSquared = 1.0;
		else
			unitSquared = cal.pixelWidth * cal.pixelHeight;
		String[] minAndMax = Tools.split(size, " -");
		final double mins = gd.parseDouble(minAndMax[0]);
		final double maxs = minAndMax.length == 2 ? gd.parseDouble(minAndMax[1]) : Double.NaN;
		minSize = Double.isNaN(mins) ? DEFAULT_MIN_SIZE : mins / unitSquared;
		maxSize = Double.isNaN(maxs) ? DEFAULT_MAX_SIZE : maxs / unitSquared;
		if (minSize < DEFAULT_MIN_SIZE)
			minSize = DEFAULT_MIN_SIZE;
		if (maxSize < minSize)
			maxSize = DEFAULT_MAX_SIZE;
		staticMinSize = minSize;
		staticMaxSize = maxSize;

		minAndMax = Tools.split(gd.getNextString(), " -"); // min-max circularity
		final double minc = gd.parseDouble(minAndMax[0]);
		final double maxc = minAndMax.length == 2 ? gd.parseDouble(minAndMax[1]) : Double.NaN;
		minCircularity = Double.isNaN(minc) ? 0.0 : minc;
		maxCircularity = Double.isNaN(maxc) ? 1.0 : maxc;
		if (minCircularity < 0.0 || minCircularity > 1.0)
			minCircularity = 0.0;
		if (maxCircularity < minCircularity || maxCircularity > 1.0)
			maxCircularity = 1.0;
		if (minCircularity == 1.0 && maxCircularity == 1.0)
			minCircularity = 0.0;
		staticMinCircularity = minCircularity;
		staticMaxCircularity = maxCircularity;

		if (gd.invalidNumber())
		{
			IJ.error("Bins invalid.");
			canceled = true;
			return false;
		}
		showChoice = gd.getNextChoiceIndex();
		staticShowChoice = showChoice;
		if (gd.getNextBoolean())
			options |= SHOW_RESULTS;
		else
			options &= ~SHOW_RESULTS;
		if (gd.getNextBoolean())
			options |= EXCLUDE_EDGE_PARTICLES;
		else
			options &= ~EXCLUDE_EDGE_PARTICLES;
		if (gd.getNextBoolean())
			options |= CLEAR_WORKSHEET;
		else
			options &= ~CLEAR_WORKSHEET;
		if (gd.getNextBoolean())
			options |= INCLUDE_HOLES;
		else
			options &= ~INCLUDE_HOLES;
		if (gd.getNextBoolean())
			options |= DISPLAY_SUMMARY;
		else
			options &= ~DISPLAY_SUMMARY;
		if (gd.getNextBoolean())
			options |= RECORD_STARTS;
		else
			options &= ~RECORD_STARTS;
		if (gd.getNextBoolean())
			options |= ADD_TO_MANAGER;
		else
			options &= ~ADD_TO_MANAGER;
		if (gd.getNextBoolean())
			options |= IN_SITU_SHOW;
		else
			options &= ~IN_SITU_SHOW;
		staticOptions = options;
		options |= SHOW_PROGRESS;
		if ((options & DISPLAY_SUMMARY) != 0)
			Analyzer.setMeasurements(Analyzer.getMeasurements() | AREA);
		return true;
	}

	private static boolean isThresholdedRGB(ImagePlus imp)
	{
		final Object obj = imp.getProperty("Mask");
		if (obj == null || !(obj instanceof ImageProcessor))
			return false;
		final ImageProcessor mask = (ImageProcessor) obj;
		return mask.getWidth() == imp.getWidth() && mask.getHeight() == imp.getHeight();
	}

	boolean updateMacroOptions()
	{
		String options = Macro.getOptions();
		int index = options.indexOf("maximum=");
		if (index == -1)
			return false;
		index += 8;
		final int len = options.length();
		while (index < len - 1 && options.charAt(index) != ' ')
			index++;
		if (index == len - 1)
			return false;
		final int min = (int) Tools.parseDouble(Macro.getValue(options, "minimum", "1"));
		final int max = (int) Tools.parseDouble(Macro.getValue(options, "maximum", "999999"));
		options = "size=" + min + "-" + max + options.substring(index, len);
		Macro.setOptions(options);
		return true;
	}

	/**
	 * Performs particle analysis on the specified image. Returns
	 * false if there is an error.
	 *
	 * @param imp
	 *            the image
	 * @return true, if successful
	 */
	public boolean analyze(ImagePlus imp)
	{
		return analyze(imp, imp.getProcessor());
	}

	/**
	 * Performs particle analysis on the specified ImagePlus and
	 * ImageProcessor. Returns false if there is an error.
	 *
	 * @param imp
	 *            the image
	 * @param ip
	 *            the image
	 * @return true, if successful
	 */
	public boolean analyze(ImagePlus imp, ImageProcessor ip)
	{
		if (this.imp == null)
			this.imp = imp;
		showResults = (options & SHOW_RESULTS) != 0;
		excludeEdgeParticles = (options & EXCLUDE_EDGE_PARTICLES) != 0;
		resetCounter = (options & CLEAR_WORKSHEET) != 0;
		showProgress = (options & SHOW_PROGRESS) != 0;
		floodFill = (options & INCLUDE_HOLES) == 0;
		recordStarts = (options & RECORD_STARTS) != 0;
		addToManager = (options & ADD_TO_MANAGER) != 0;
		if (staticRoiManager != null)
		{
			addToManager = true;
			roiManager = staticRoiManager;
			staticRoiManager = null;
		}
		if (staticResultsTable != null)
		{
			rt = staticResultsTable;
			staticResultsTable = null;
			showResultsWindow = false;
		}
		displaySummary = (options & DISPLAY_SUMMARY) != 0 || (options & SHOW_SUMMARY) != 0;
		inSituShow = (options & IN_SITU_SHOW) != 0;
		outputImage = null;
		ip.snapshot();
		ip.setProgressBar(null);
		if (Analyzer.isRedirectImage())
		{
			redirectImp = Analyzer.getRedirectImage(imp);
			if (redirectImp == null)
				return false;
			final int depth = redirectImp.getStackSize();
			if (depth > 1 && depth == imp.getStackSize())
			{
				final ImageStack redirectStack = redirectImp.getStack();
				redirectIP = redirectStack.getProcessor(imp.getCurrentSlice());
			}
			else
				redirectIP = redirectImp.getProcessor();
		}
		else if (imp.getType() == ImagePlus.COLOR_RGB)
		{
			final ImagePlus original = (ImagePlus) imp.getProperty("OriginalImage");
			if (original != null && original.getWidth() == imp.getWidth() && original.getHeight() == imp.getHeight())
			{
				redirectImp = original;
				redirectIP = original.getProcessor();
			}
		}
		if (!setThresholdLevels(imp, ip))
			return false;
		width = ip.getWidth();
		height = ip.getHeight();
		if (!(showChoice == NOTHING || showChoice == OVERLAY_OUTLINES || showChoice == OVERLAY_MASKS))
		{
			blackBackground = Prefs.blackBackground && inSituShow;
			if (slice == 1)
				outlines = new ImageStack(width, height);
			if (showChoice == ROI_MASKS)
				drawIP = new ShortProcessor(width, height);
			else
				drawIP = new ByteProcessor(width, height);
			drawIP.setLineWidth(lineWidth);
			if (showChoice == ROI_MASKS)
			{
				// Place holder for now...
			}
			else if (showChoice == MASKS && !blackBackground)
				drawIP.invertLut();
			else if (showChoice == OUTLINES)
			{
				if (!inSituShow)
				{
					if (customLut == null)
						makeCustomLut();
					drawIP.setColorModel(customLut);
				}
				drawIP.setFont(new Font("SansSerif", Font.PLAIN, fontSize));
				if (fontSize > 12 && inSituShow)
					drawIP.setAntialiasedText(true);
			}
			outlines.addSlice(null, drawIP);

			if (showChoice == ROI_MASKS || blackBackground)
			{
				drawIP.setColor(Color.black);
				drawIP.fill();
				drawIP.setColor(Color.white);
			}
			else
			{
				drawIP.setColor(Color.white);
				drawIP.fill();
				drawIP.setColor(Color.black);
			}
		}
		calibration = redirectImp != null ? redirectImp.getCalibration() : imp.getCalibration();

		if (rt == null)
		{
			rt = Analyzer.getResultsTable();
			analyzer = new Analyzer(imp);
		}
		else
		{
			if (measurements == 0)
				measurements = Analyzer.getMeasurements();
			analyzer = new Analyzer(imp, measurements, rt);
		}
		if (resetCounter && slice == 1)
			if (!Analyzer.resetCounter())
				return false;
		beginningCount = Analyzer.getCounter();

		byte[] pixels = null;
		if (ip instanceof ByteProcessor)
			pixels = (byte[]) ip.getPixels();
		if (r == null)
		{
			r = ip.getRoi();
			mask = ip.getMask();
			if (displaySummary)
				if (mask != null)
					totalArea = ImageStatistics.getStatistics(ip, AREA, calibration).area;
				else
					totalArea = r.width * calibration.pixelWidth * r.height * calibration.pixelHeight;
		}
		minX = r.x;
		maxX = r.x + r.width;
		minY = r.y;
		maxY = r.y + r.height;
		if (r.width < width || r.height < height || mask != null)
			if (!eraseOutsideRoi(ip, r, mask))
				return false;
		int offset;
		double value;
		final int inc = Math.max(r.height / 25, 1);
		final int mi = 0;
		final ImageWindow win = imp.getWindow();
		if (win != null)
			win.running = true;
		if (measurements == 0)
			measurements = Analyzer.getMeasurements();
		if (showChoice == ELLIPSES)
			measurements |= ELLIPSE;
		measurements &= ~LIMIT; // ignore "Limit to Threshold"
		roiNeedsImage = (measurements & PERIMETER) != 0 || (measurements & SHAPE_DESCRIPTORS) != 0 ||
				(measurements & FERET) != 0;
		particleCount = 0;
		wand = new Wand(ip);
		pf = new PolygonFiller();
		if (floodFill)
		{
			final ImageProcessor ipf = ip.duplicate();
			ipf.setValue(fillColor);
			ff = new FloodFiller(ipf);
		}
		roiType = Wand.allPoints() ? Roi.FREEROI : Roi.TRACED_ROI;

		for (int y = r.y; y < (r.y + r.height); y++)
		{
			offset = y * width;
			for (int x = r.x; x < (r.x + r.width); x++)
			{
				if (pixels != null)
					value = pixels[offset + x] & 255;
				else if (imageType == SHORT)
					value = ip.getPixel(x, y);
				else
					value = ip.getPixelValue(x, y);
				if (value >= level1 && value <= level2)
					analyzeParticle(x, y, imp, ip);
			}
			if (showProgress && ((y % inc) == 0))
				IJ.showProgress((double) (y - r.y) / r.height);
			if (win != null)
				canceled = !win.running;
			if (canceled)
			{
				Macro.abort();
				break;
			}
		}
		if (showProgress)
			IJ.showProgress(1.0);
		if (showResults && showResultsWindow)
			rt.updateResults();
		imp.deleteRoi();
		ip.resetRoi();
		ip.reset();
		if (displaySummary && IJ.getInstance() != null)
			updateSliceSummary();
		if (addToManager && roiManager != null)
			roiManager.setEditMode(imp, true);
		maxParticleCount = (particleCount > maxParticleCount) ? particleCount : maxParticleCount;
		totalCount += particleCount;
		if (!canceled)
			showResults();
		return true;
	}

	void updateSliceSummary()
	{
		final int slices = imp.getStackSize();
		float[] areas = rt.getColumn(ResultsTable.AREA);
		if (areas == null)
			areas = new float[0];
		String label = imp.getTitle();
		if (slices > 1)
		{
			if (processStack)
				label = imp.getStack().getShortSliceLabel(slice);
			else
				label = imp.getStack().getShortSliceLabel(imp.getCurrentSlice());
			label = label != null && !label.equals("") ? label : "" + slice;
		}
		String aLine = null;
		double sum = 0.0;
		final int start = areas.length - particleCount;
		if (start < 0)
			return;
		for (int i = start; i < areas.length; i++)
			sum += areas[i];
		final int places = Analyzer.getPrecision();
		final Calibration cal = imp.getCalibration();
		final String total = "\t" + ResultsTable.d2s(sum, places);
		final String average = "\t" + ResultsTable.d2s(sum / particleCount, places);
		final String fraction = "\t" + ResultsTable.d2s(sum * 100.0 / totalArea, places);
		aLine = label + "\t" + particleCount + total + average + fraction;
		aLine = addMeans(aLine, areas.length > 0 ? start : -1);
		if (slices == 1)
		{
			final Frame frame = WindowManager.getFrame("Summary");
			if (frame != null && (frame instanceof TextWindow) && summaryHdr.equals(prevHdr))
				tw = (TextWindow) frame;
		}
		else
		{
			final Frame frame = WindowManager.getFrame("Summary of " + imp.getTitle());
			if (frame != null && (frame instanceof TextWindow) && summaryHdr.equals(prevHdr))
				tw = (TextWindow) frame;
		}
		if (tw == null)
		{
			final String title = slices == 1 ? "Summary" : "Summary of " + imp.getTitle();
			tw = new TextWindow(title, summaryHdr, aLine, 450, 300);
			prevHdr = summaryHdr;
		}
		else
			tw.append(aLine);
	}

	String addMeans(String line, int start)
	{
		if ((measurements & MEAN) != 0)
			line = addMean(ResultsTable.MEAN, line, start);
		if ((measurements & MODE) != 0)
			line = addMean(ResultsTable.MODE, line, start);
		if ((measurements & PERIMETER) != 0)
			line = addMean(ResultsTable.PERIMETER, line, start);
		if ((measurements & ELLIPSE) != 0)
		{
			line = addMean(ResultsTable.MAJOR, line, start);
			line = addMean(ResultsTable.MINOR, line, start);
			line = addMean(ResultsTable.ANGLE, line, start);
		}
		if ((measurements & SHAPE_DESCRIPTORS) != 0)
		{
			line = addMean(ResultsTable.CIRCULARITY, line, start);
			line = addMean(ResultsTable.SOLIDITY, line, start);
		}
		if ((measurements & FERET) != 0)
		{
			line = addMean(ResultsTable.FERET, line, start);
			line = addMean(ResultsTable.FERET_X, line, start);
			line = addMean(ResultsTable.FERET_Y, line, start);
			line = addMean(ResultsTable.FERET_ANGLE, line, start);
			line = addMean(ResultsTable.MIN_FERET, line, start);
		}
		if ((measurements & INTEGRATED_DENSITY) != 0)
			line = addMean(ResultsTable.INTEGRATED_DENSITY, line, start);
		if ((measurements & MEDIAN) != 0)
			line = addMean(ResultsTable.MEDIAN, line, start);
		if ((measurements & SKEWNESS) != 0)
			line = addMean(ResultsTable.SKEWNESS, line, start);
		if ((measurements & KURTOSIS) != 0)
			line = addMean(ResultsTable.KURTOSIS, line, start);
		return line;
	}

	private String addMean(int column, String line, int start)
	{
		if (start == -1)
		{
			line += "\tNaN";
			summaryHdr += "\t" + ResultsTable.getDefaultHeading(column);
		}
		else
		{
			final float[] c = column >= 0 ? rt.getColumn(column) : null;
			if (c != null)
			{
				ImageProcessor ip = new FloatProcessor(c.length, 1, c, null);
				ip.setRoi(start, 0, ip.getWidth() - start, 1);
				ip = ip.crop();
				final ImageStatistics stats = new FloatStatistics(ip);
				line += n(stats.mean);
			}
			else
				line += "\tNaN";
			summaryHdr += "\t" + rt.getColumnHeading(column);
		}
		return line;
	}

	String n(double n)
	{
		String s;
		if (Math.round(n) == n)
			s = ResultsTable.d2s(n, 0);
		else
			s = ResultsTable.d2s(n, Analyzer.getPrecision());
		return "\t" + s;
	}

	boolean eraseOutsideRoi(ImageProcessor ip, Rectangle r, ImageProcessor mask)
	{
		final int width = ip.getWidth();
		final int height = ip.getHeight();
		ip.setRoi(r);
		if (excludeEdgeParticles && polygon != null)
		{
			final ImageStatistics stats = ImageStatistics.getStatistics(ip, MIN_MAX, null);
			if (fillColor >= stats.min && fillColor <= stats.max)
			{
				double replaceColor = level1 - 1.0;
				if (replaceColor < 0.0 || replaceColor == fillColor)
				{
					replaceColor = level2 + 1.0;
					final int maxColor = imageType == BYTE ? 255 : 65535;
					if (replaceColor > maxColor || replaceColor == fillColor)
					{
						IJ.error("Particle Analyzer", "Unable to remove edge particles");
						return false;
					}
				}
				for (int y = minY; y < maxY; y++)
					for (int x = minX; x < maxX; x++)
					{
						final int v = ip.getPixel(x, y);
						if (v == fillColor)
							ip.putPixel(x, y, (int) replaceColor);
					}
			}
		}
		ip.setValue(fillColor);
		if (mask != null)
		{
			mask = mask.duplicate();
			mask.invert();
			ip.fill(mask);
		}
		ip.setRoi(0, 0, r.x, height);
		ip.fill();
		ip.setRoi(r.x, 0, r.width, r.y);
		ip.fill();
		ip.setRoi(r.x, r.y + r.height, r.width, height - (r.y + r.height));
		ip.fill();
		ip.setRoi(r.x + r.width, 0, width - (r.x + r.width), height);
		ip.fill();
		ip.resetRoi();
		//IJ.log("erase: "+fillColor+"	"+level1+"	"+level2+"	"+excludeEdgeParticles);
		//(new ImagePlus("ip2", ip.duplicate())).show();
		return true;
	}

	boolean setThresholdLevels(ImagePlus imp, ImageProcessor ip)
	{
		final double t1 = ip.getMinThreshold();
		final double t2 = ip.getMaxThreshold();
		final boolean invertedLut = imp.isInvertedLut();
		final boolean byteImage = ip instanceof ByteProcessor;
		if (ip instanceof ShortProcessor)
			imageType = SHORT;
		else if (ip instanceof FloatProcessor)
			imageType = FLOAT;
		else
			imageType = BYTE;
		if (t1 == ImageProcessor.NO_THRESHOLD)
		{
			final ImageStatistics stats = imp.getStatistics();
			if (imageType != BYTE || (stats.histogram[0] + stats.histogram[255] != stats.pixelCount))
			{
				IJ.error("Particle Analyzer", "A thresholded image or 8-bit binary image is\n" +
						"required. Threshold levels can be set using\n" + "the Image->Adjust->Threshold tool.");
				canceled = true;
				return false;
			}
			boolean threshold255 = invertedLut;
			if (Prefs.blackBackground)
				threshold255 = !threshold255;
			if (threshold255)
			{
				level1 = 255;
				level2 = 255;
				fillColor = 64;
			}
			else
			{
				level1 = 0;
				level2 = 0;
				fillColor = 192;
			}
		}
		else
		{
			level1 = t1;
			level2 = t2;
			if (imageType == BYTE)
			{
				if (level1 > 0)
					fillColor = 0;
				else if (level2 < 255)
					fillColor = 255;
			}
			else if (imageType == SHORT)
			{
				if (level1 > 0)
					fillColor = 0;
				else if (level2 < 65535)
					fillColor = 65535;
			}
			else if (imageType == FLOAT)
				fillColor = -Float.MAX_VALUE;
			else
				return false;
		}
		imageType2 = imageType;
		if (redirectIP != null)
			if (redirectIP instanceof ShortProcessor)
				imageType2 = SHORT;
			else if (redirectIP instanceof FloatProcessor)
				imageType2 = FLOAT;
			else if (redirectIP instanceof ColorProcessor)
				imageType2 = RGB;
			else
				imageType2 = BYTE;
		return true;
	}

	int counter = 0;

	/**
	 * Analyze particle.
	 *
	 * @param x
	 *            the x
	 * @param y
	 *            the y
	 * @param imp
	 *            the image
	 * @param ip
	 *            the image
	 */
	protected void analyzeParticle(int x, int y, ImagePlus imp, ImageProcessor ip)
	{
		//Wand wand = new Wand(ip);
		final ImageProcessor ip2 = redirectIP != null ? redirectIP : ip;
		wand.autoOutline(x, y, level1, level2, wandMode);
		if (wand.npoints == 0)
		{
			IJ.log("wand error: " + x + " " + y);
			return;
		}
		final Roi roi = new PolygonRoi(wand.xpoints, wand.ypoints, wand.npoints, roiType);
		final Rectangle r = roi.getBounds();
		if (r.width > 1 && r.height > 1)
		{
			final PolygonRoi proi = (PolygonRoi) roi;
			pf.setPolygon(proi.getXCoordinates(), proi.getYCoordinates(), proi.getNCoordinates());
			ip2.setMask(pf.getMask(r.width, r.height));
			if (floodFill)
			{
				// For the MaskParticleAnalyzer flood filling does not work using the original image.
				// This is because it is filling between level1 & level2. If two objects touch so that
				// the bounding box of 1 enters that of the other then the analysis will be incorrect.
				// This can be fixed by duplicating the IP again so only the current object is present
				// or flood filling at the current IP value (faster)

				// Duplicate so only the current object is present
				//ImageProcessor ipf = ip.duplicate();
				//ipf.setValue(fillColor);
				//ff = new FloodFiller(ipf);
				//ff.particleAnalyzerFill(x, y, level1, level2, ip2.getMask(), r);

				// Flood filling can use only the current pixel value
				final double value = (ip instanceof FloatProcessor) ? ip.getPixelValue(x, y) : ip.getPixel(x, y);
				ff.particleAnalyzerFill(x, y, value, value, ip2.getMask(), r);
			}
		}
		ip2.setRoi(r);
		ip.setValue(fillColor);
		final ImageStatistics stats = getStatistics(ip2, measurements, calibration);
		boolean include = true;
		if (excludeEdgeParticles)
		{
			if (r.x == minX || r.y == minY || r.x + r.width == maxX || r.y + r.height == maxY)
				include = false;
			if (polygon != null)
			{
				final Rectangle bounds = roi.getBounds();
				int x1 = bounds.x + wand.xpoints[wand.npoints - 1];
				int y1 = bounds.y + wand.ypoints[wand.npoints - 1];
				int x2, y2;
				for (int i = 0; i < wand.npoints; i++)
				{
					x2 = bounds.x + wand.xpoints[i];
					y2 = bounds.y + wand.ypoints[i];
					if (!polygon.contains(x2, y2))
					{
						include = false;
						break;
					}
					if ((x1 == x2 && ip.getPixel(x1, y1 - 1) == fillColor) ||
							(y1 == y2 && ip.getPixel(x1 - 1, y1) == fillColor))
					{
						include = false;
						break;
					}
					x1 = x2;
					y1 = y2;
				}
			}
		}
		final ImageProcessor mask = ip2.getMask();
		if (minCircularity > 0.0 || maxCircularity < 1.0)
		{
			final double perimeter = roi.getLength();
			double circularity = perimeter == 0.0 ? 0.0 : 4.0 * Math.PI * (stats.pixelCount / (perimeter * perimeter));
			if (circularity > 1.0)
				circularity = 1.0;
			//IJ.log(circularity+"	"+perimeter+"  "+stats.area);
			if (circularity < minCircularity || circularity > maxCircularity)
				include = false;
		}
		if (stats.pixelCount >= minSize && stats.pixelCount <= maxSize && include)
		{
			particleCount++;
			if (roiNeedsImage)
				roi.setImage(imp);
			stats.xstart = x;
			stats.ystart = y;
			saveResults(stats, roi);
			if (showChoice != NOTHING)
				drawParticle(drawIP, roi, stats, mask);
		}
		if (redirectIP != null)
			ip.setRoi(r);
		ip.fill(mask);
	}

	ImageStatistics getStatistics(ImageProcessor ip, int mOptions, Calibration cal)
	{
		switch (imageType2)
		{
			case BYTE:
				return new ByteStatistics(ip, mOptions, cal);
			case SHORT:
				return new ShortStatistics(ip, mOptions, cal);
			case FLOAT:
				return new FloatStatistics(ip, mOptions, cal);
			case RGB:
				return new ColorStatistics(ip, mOptions, cal);
			default:
				return null;
		}
	}

	/**
	 * Saves statistics for one particle in a results table. This is
	 * a method subclasses may want to override.
	 *
	 * @param stats
	 *            the stats
	 * @param roi
	 *            the roi
	 */
	protected void saveResults(ImageStatistics stats, Roi roi)
	{
		analyzer.saveResults(stats, roi);
		if (recordStarts)
		{
			rt.addValue("XStart", stats.xstart);
			rt.addValue("YStart", stats.ystart);
		}
		if (addToManager)
		{
			if (roiManager == null)
			{
				if (Macro.getOptions() != null && Interpreter.isBatchMode())
					roiManager = Interpreter.getBatchModeRoiManager();
				if (roiManager == null)
				{
					Frame frame = WindowManager.getFrame("ROI Manager");
					if (frame == null)
						IJ.run("ROI Manager...");
					frame = WindowManager.getFrame("ROI Manager");
					if (frame == null || !(frame instanceof RoiManager))
					{
						addToManager = false;
						return;
					}
					roiManager = (RoiManager) frame;
				}
				if (resetCounter)
					roiManager.runCommand("reset");
			}
			if (imp.getStackSize() > 1)
				roi.setPosition(imp.getCurrentSlice());
			if (lineWidth != 1)
				roi.setStrokeWidth(lineWidth);
			roiManager.add(imp, roi, rt.getCounter());
		}
		if (showResultsWindow && showResults)
			rt.addResults();
	}

	/**
	 * Draws a selected particle in a separate image. This is
	 * another method subclasses may want to override.
	 *
	 * @param drawIP
	 *            the separate image
	 * @param roi
	 *            the roi
	 * @param stats
	 *            the stats
	 * @param mask
	 *            the mask
	 */
	protected void drawParticle(ImageProcessor drawIP, Roi roi, ImageStatistics stats, ImageProcessor mask)
	{
		switch (showChoice)
		{
			case MASKS:
				drawFilledParticle(drawIP, roi, mask);
				break;
			case OUTLINES:
			case BARE_OUTLINES:
			case OVERLAY_OUTLINES:
			case OVERLAY_MASKS:
				drawOutline(drawIP, roi, rt.getCounter());
				break;
			case ELLIPSES:
				drawEllipse(drawIP, stats, rt.getCounter());
				break;
			case ROI_MASKS:
				drawRoiFilledParticle(drawIP, roi, mask, rt.getCounter());
				break;
			default:
		}
	}

	void drawFilledParticle(ImageProcessor ip, Roi roi, ImageProcessor mask)
	{
		//IJ.write(roi.getBounds()+" "+mask.length);
		ip.setRoi(roi.getBounds());
		ip.fill(mask);
	}

	void drawOutline(ImageProcessor ip, Roi roi, int count)
	{
		if (showChoice == OVERLAY_OUTLINES || showChoice == OVERLAY_MASKS)
		{
			if (overlay == null)
			{
				overlay = new Overlay();
				overlay.drawLabels(true);
				overlay.setLabelFont(new Font("SansSerif", Font.PLAIN, fontSize));
			}
			final Roi roi2 = (Roi) roi.clone();
			roi2.setStrokeColor(Color.cyan);
			if (lineWidth != 1)
				roi2.setStrokeWidth(lineWidth);
			if (showChoice == OVERLAY_MASKS)
				roi2.setFillColor(Color.cyan);
			overlay.add(roi2);
		}
		else
		{
			final Rectangle r = roi.getBounds();
			final int nPoints = ((PolygonRoi) roi).getNCoordinates();
			final int[] xp = ((PolygonRoi) roi).getXCoordinates();
			final int[] yp = ((PolygonRoi) roi).getYCoordinates();
			final int x = r.x, y = r.y;
			if (!inSituShow)
				ip.setValue(0.0);
			ip.moveTo(x + xp[0], y + yp[0]);
			for (int i = 1; i < nPoints; i++)
				ip.lineTo(x + xp[i], y + yp[i]);
			ip.lineTo(x + xp[0], y + yp[0]);
			if (showChoice != BARE_OUTLINES)
			{
				final String s = ResultsTable.d2s(count, 0);
				ip.moveTo(r.x + r.width / 2 - ip.getStringWidth(s) / 2, r.y + r.height / 2 + fontSize / 2);
				if (!inSituShow)
					ip.setValue(1.0);
				ip.drawString(s);
			}
		}
	}

	void drawEllipse(ImageProcessor ip, ImageStatistics stats, int count)
	{
		stats.drawEllipse(ip);
	}

	void drawRoiFilledParticle(ImageProcessor ip, Roi roi, ImageProcessor mask, int count)
	{
		final int grayLevel = (count < 65535) ? count : 65535;
		ip.setValue(grayLevel);
		ip.setRoi(roi.getBounds());
		ip.fill(mask);
	}

	void showResults()
	{
		final int count = rt.getCounter();
		// if (count==0) return;
		final boolean lastSlice = !processStack || slice == imp.getStackSize();
		if ((showChoice == OVERLAY_OUTLINES || showChoice == OVERLAY_MASKS) && slice == 1 && count > 0)
			imp.setOverlay(overlay);
		else if (outlines != null && lastSlice)
		{
			final String title = imp != null ? imp.getTitle() : "Outlines";
			String prefix;
			if (showChoice == MASKS)
				prefix = "Mask of ";
			else if (showChoice == ROI_MASKS)
				prefix = "Count Masks of ";
			else
				prefix = "Drawing of ";
			outlines.update(drawIP);
			outputImage = new ImagePlus(prefix + title, outlines);
			outputImage.setCalibration(imp.getCalibration());
			if (inSituShow)
			{
				if (imp.getStackSize() == 1)
					Undo.setup(Undo.TRANSFORM, imp);
				imp.setStack(null, outputImage.getStack());
			}
			else if (!hideOutputImage)
				outputImage.show();
		}
		if (showResults && !processStack)
		{
			if (showResultsWindow)
			{
				final TextPanel tp = IJ.getTextPanel();
				if (beginningCount > 0 && tp != null && tp.getLineCount() != count)
					rt.show("Results");
			}
			// XXX Set these using reflection
			//Analyzer.firstParticle = beginningCount;
			//Analyzer.lastParticle = Analyzer.getCounter()-1;
			MaskParticleAnalyzer.setAnalyzerFirstParticle(beginningCount);
			MaskParticleAnalyzer.setAnalyzerLastParticle(Analyzer.getCounter() - 1);
		}
		else
		{
			//Analyzer.firstParticle = Analyzer.lastParticle = 0;
			MaskParticleAnalyzer.setAnalyzerFirstParticle(0);
			MaskParticleAnalyzer.setAnalyzerLastParticle(0);
		}
	}

	/**
	 * Returns the "Outlines", "Masks", "Elipses" or "Count Masks" image,
	 * or null if "Nothing" is selected in the "Show:" menu.
	 *
	 * @return the output image
	 */
	public ImagePlus getOutputImage()
	{
		return outputImage;
	}

	/**
	 * Set 'hideOutputImage' true to not display the "Show:" image.
	 *
	 * @param hideOutputImage
	 *            the new hide output image
	 */
	public void setHideOutputImage(boolean hideOutputImage)
	{
		this.hideOutputImage = hideOutputImage;
	}

	/**
	 * Sets the size of the font used to label outlines in the next particle analyzer instance.
	 *
	 * @param size
	 *            the new font size
	 */
	public static void setFontSize(int size)
	{
		nextFontSize = size;
	}

	/**
	 * Sets the color ("blue", "black", etc.) of the font used to label outlines in the next particle analyzer instance.
	 *
	 * @param color
	 *            the new font color
	 */
	public static void setFontColor(String color)
	{
		nextFontColor = Colors.decode(color, defaultFontColor);
	}

	/**
	 * Sets the outline line width for the next ParticleAnalyzerCopy instance.
	 *
	 * @param width
	 *            the new line width
	 */
	public static void setLineWidth(int width)
	{
		nextLineWidth = width;
	}

	/**
	 * Sets the RoiManager to be used by the next ParticleAnalyzerCopy
	 * instance. There is a JavaScript example at
	 * http://imagej.nih.gov/ij/macros/js/HiddenRoiManager.js
	 *
	 * @param manager
	 *            the new roi manager
	 */
	public static void setRoiManager(RoiManager manager)
	{
		staticRoiManager = manager;
	}

	/**
	 * Sets the ResultsTable to be used by the next
	 * ParticleAnalyzerCopy instance.
	 *
	 * @param rt
	 *            the new results table
	 */
	public static void setResultsTable(ResultsTable rt)
	{
		staticResultsTable = rt;
	}

	int getColumnID(String name)
	{
		int id = rt.getFreeColumn(name);
		if (id == ResultsTable.COLUMN_IN_USE)
			id = rt.getColumnIndex(name);
		return id;
	}

	void makeCustomLut()
	{
		final IndexColorModel cm = (IndexColorModel) LookUpTable.createGrayscaleColorModel(false);
		final byte[] reds = new byte[256];
		final byte[] greens = new byte[256];
		final byte[] blues = new byte[256];
		cm.getReds(reds);
		cm.getGreens(greens);
		cm.getBlues(blues);
		reds[1] = (byte) fontColor.getRed();
		greens[1] = (byte) fontColor.getGreen();
		blues[1] = (byte) fontColor.getBlue();
		customLut = new IndexColorModel(8, 256, reds, greens, blues);
	}

	/**
	 * Called once when ImageJ quits.
	 *
	 * @param prefs
	 *            the prefs
	 */
	public static void savePreferences(Properties prefs)
	{
		prefs.put(OPTIONS, Integer.toString(staticOptions));
	}
}
