package gdsc.colocalisation.cda;

/*----------------------------------------------------------------------------- 
 * GDSC Plugins for ImageJ
 * 
 * Copyright (C) 2011 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 *
 * This class is based on the original CDA_Plugin developed by 
 * Maria Osorio-Reich:
 * http://imagejdocu.tudor.lu/doku.php?id=plugin:analysis:confined_displacement_algorithm_determines_true_and_random_colocalization_:start
 *---------------------------------------------------------------------------*/

import gdsc.colocalisation.cda.engine.CDAEngine;
import gdsc.colocalisation.cda.engine.CalculationResult;
import gdsc.utils.ImageJHelper;
import gdsc.utils.Random;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GUI;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.gui.Roi;
import ij.macro.MacroRunner;
import ij.plugin.frame.PlugInFrame;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.FloatStatistics;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.text.TextWindow;
import ij.util.Tools;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.swing.JPanel;

/**
 * Test for significant colocalisation within images using the Confined Displacement Algorithm (CDA).
 * 
 * Colocalisation of two channels is measured using Mander's coefficients (M1 & M2) and Pearson's correlation
 * coefficient (R). Significance is tested by shifting one image to all positions within a circular radius and computing
 * a probability distribution for M1, M2 and R. Significance is measured using a specified p-value.
 * 
 * Supports stacks. Only a specific channel and time frame can be used and all input images must have the same number
 * of z-sections for the chosen channel/time frame.
 */
public class CDA_Plugin extends PlugInFrame implements ActionListener, ItemListener, KeyListener, Runnable
{
	private static final long serialVersionUID = 1L;
	private static String FRAME_TITLE = "CDA Plugin";
	private static String OPTION_NONE = "(None)";
	private static String OPTION_MASK = "Use as mask";
	private static String OPTION_MIN_VALUE = "Min Display Value";
	private static String OPTION_USE_ROI = "Use ROI";
	private static String[] ROI_OPTIONS = { OPTION_NONE, OPTION_MIN_VALUE, OPTION_USE_ROI, OPTION_MASK };
	private static String CHANNEL_IMAGE = "(Channel image)";

	// Image titles
	private static String channel1RGBTitle = "CDA Channel 1";
	private static String channel2RGBTitle = "CDA Channel 2";
	private static String segmented1RGBTitle = "CDA ROI 1";
	private static String segmented2RGBTitle = "CDA ROI 2";
	private static String mergedChannelTitle = "CDA Merged channel";
	private static String mergedSegmentedTitle = "CDA Merged ROI";
	private static String lastShiftedChannelTitle = "CDA Merged channel maximum displacement";
	private static String lastShiftedSegmentedTitle = "CDA Merged ROI maximum displacement";

	private static String m1HistogramTitle = "CDA M1 PDF";
	private static String m2HistogramTitle = "CDA M2 PDF";
	private static String rHistogramTitle = "CDA R PDF";
	private static String plotM1Title = "CDA M1 samples";
	private static String plotM2Title = "CDA M2 samples";
	private static String plotRTitle = "CDA R samples";

	// Configuration options
	private static String choiceChannel1 = "Channel 1";
	private static String choiceChannel2 = "Channel 2";
	private static String choiceSegmentedChannel1 = "ROI for channel 1";
	private static String choiceSegmentedChannel2 = "ROI for channel 2";
	private static String choiceConfined = "Confined compartment";
	private static String choiceExpandConfined = "  Include ROIs";
	private static String choiceMaximumRadius = "Maximum radial displacement";
	private static String choiceRandomRadius = "Random radial displacement";
	private static String choiceSubRandomSamples = "Compute sub-random samples";
	private static String numberOfSamplesLabel = "  Approx. number of samples: ";
	private static String choiceBinsNumber = "Bins for histogram";
	private static String choiceCloseWindowsOnExit = "Close windows on exit";
	private static String choiceSetOptions = "Set results options";
	private static String okButtonLabel = "Apply";
	private static String helpButtonLabel = "Help";

	// PDFs
	private static String plotXLabel = "Radial displacement d";
	private static String plotM1YLabel = "M1";
	private static String plotM2YLabel = "M2";
	private static String plotRYLabel = "R";
	private static String plotPDFYLabel = "PDF";
	private static String pixelsUnitString = "[pixels]";

	// Status messages
	private static String mandersCalculationStatus = "Calculating manders coefficients...";
	private static String gettingConfigStatus = "Processing parameters...";
	private static String doneStatus = "Done.";
	private static String calculatingFirstMandersStatus = "Calculating M1 and M2";
	private static String preparingPlotsStatus = "Creating plots and images...";
	private static String processingMandersStatus = "Processing manders coefficients...";

	private static Frame instance;
	private Label pixelsLabel;
	private Choice channel1List;
	private Choice channel2List;
	private Choice segmented1List;
	private Choice segmented2List;
	private Choice confinedList;

	private Choice segmented1Option;
	private Choice segmented2Option;
	private Choice confinedOption;

	private Checkbox expandConfinedCheckbox;
	private TextField maximumRadiusText;
	private TextField randomRadiusText;
	private Checkbox subRandomSamplesCheckbox;
	private Label numberOfSamplesField;
	private TextField binsText;
	private Checkbox closeWindowsOnExitCheckbox;
	private Checkbox setResultsOptionsCheckbox;
	private Button okButton;
	private Button helpButton;

	private static int fontWidth = 12;
	private static Font monoFont = new Font("Monospaced", 0, fontWidth);

	// Windows that are opened by the CDA Plug-in.
	// These should be closed on exit.
	private static TextWindow tw;
	private ImagePlus channel1RGB;
	private ImagePlus channel2RGB;
	private ImagePlus segmented1RGB;
	private ImagePlus segmented2RGB;
	private ImagePlus mergedChannelRGB;
	private ImagePlus mergedSegmentedRGB;
	private ImagePlus mergedChannelDisplacementRGB;
	private ImagePlus mergedSegmentedDisplacementRGB;
	private PlotWindow m1PlotWindow;
	private PlotWindow m2PlotWindow;
	private PlotWindow rPlotWindow;
	private DisplayStatistics m1Statistics;
	private DisplayStatistics m2Statistics;
	private DisplayStatistics rStatistics;

	// Options
	private final String OPT_LOCATION = "CDA.location";
	private final String OPT_LOCATION_PLOT_M1 = "CDA.locationPlotM1";
	private final String OPT_LOCATION_PLOT_M2 = "CDA.locationPlotM2";
	private final String OPT_LOCATION_PLOT_R = "CDA.locationPlotR";
	private final String OPT_LOCATION_STATS_M1 = "CDA.locationStatisticsM1";
	private final String OPT_LOCATION_STATS_M2 = "CDA.locationStatisticsM2";
	private final String OPT_LOCATION_STATS_R = "CDA.locationStatisticsR";

	private final String OPT_CHANNEL1_INDEX = "CDA.channel1Index";
	private final String OPT_CHANNEL2_INDEX = "CDA.channel2Index";
	private final String OPT_SEGMENTED1_INDEX = "CDA.segmented1Index";
	private final String OPT_SEGMENTED2_INDEX = "CDA.segmented2Index";
	private final String OPT_CONFINED_INDEX = "CDA.confinedIndex";
	private final String OPT_SEGMENTED1_OPTION_INDEX = "CDA.segmented1OptionIndex";
	private final String OPT_SEGMENTED2_OPTION_INDEX = "CDA.segmented2OptionIndex";
	private final String OPT_CONFINED_OPTION_INDEX = "CDA.confinedOptionIndex";
	private final String OPT_EXPAND_CONFINED = "CDA.expandConfined";
	private final String OPT_MAXIMUM_RADIUS = "CDA.maximumRadius";
	private final String OPT_RANDOM_RADIUS = "CDA.randomRadius";
	private final String OPT_SUB_RANDOM_SAMPLES = "CDA.subRandomSamples";
	private final String OPT_HISTOGRAM_BINS = "CDA.histogramBins";
	private final String OPT_CLOSE_WINDOWS_ON_EXIT = "CDA.closeWindowsOnExit";
	private final String OPT_SET_OPTIONS = "CDA.setOptions";

	private final String OPT_SHOW_CHANNEL1_RGB = "CDA.showChannel1";
	private final String OPT_SHOW_CHANNEL2_RGB = "CDA.showChannel2";
	private final String OPT_SHOW_SEGMENTED1_RGB = "CDA.showSegmented1";
	private final String OPT_SHOW_SEGMENTED2_RGB = "CDA.showSegmented2";
	private final String OPT_SHOW_MERGED_CHANNEL_RGB = "CDA.showMergedChannel";
	private final String OPT_SHOW_MERGED_SEGMENTED_RGB = "CDA.showMergedSegmented";
	private final String OPT_SHOW_MERGED_CHANNEL_DISPLACEMENT_RGB = "CDA.showMergedChannelDisplacement";
	private final String OPT_SHOW_MERGED_SEGMENTED_DISPLACEMENT_RGB = "CDA.showMergedSegmentedDisplacement";
	private final String OPT_SHOW_M1_PLOT_WINDOW = "CDA.showM1PlotWindow";
	private final String OPT_SHOW_M2_PLOT_WINDOW = "CDA.showM2PlotWindow";
	private final String OPT_SHOW_R_PLOT_WINDOW = "CDA.showRPlotWindow";
	private final String OPT_SHOW_M1_STATISTICS = "CDA.showM1Statistics";
	private final String OPT_SHOW_M2_STATISTICS = "CDA.showM2Statistics";
	private final String OPT_SHOW_R_STATISTICS = "CDA.showRStatistics";
	private final String OPT_SAVE_RESULTS = "CDA.saveResults";
	private final String OPT_RESULTS_DIRECTORY = "CDA.resultsDirectory";
	private final String OPT_P_VALUE = "CDA.pValue";
	private final String OPT_PERMUTATIONS = "CDA.permutations";

	private final int DEFAULT_MAXIMUM_RADIUS = 12;
	private final int DEFAULT_RANDOM_RADIUS = 7;
	private final int DEFAULT_HISTOGRAM_BINS = 16;

	private int channel1Index = (int) Prefs.get(OPT_CHANNEL1_INDEX, 0);
	private int channel2Index = (int) Prefs.get(OPT_CHANNEL2_INDEX, 0);
	private int segmented1Index = (int) Prefs.get(OPT_SEGMENTED1_INDEX, 0);
	private int segmented2Index = (int) Prefs.get(OPT_SEGMENTED2_INDEX, 0);
	private int confinedIndex = (int) Prefs.get(OPT_CONFINED_INDEX, 0);
	private int segmented1OptionIndex = (int) Prefs.get(OPT_SEGMENTED1_OPTION_INDEX, 0);
	private int segmented2OptionIndex = (int) Prefs.get(OPT_SEGMENTED2_OPTION_INDEX, 0);
	private int confinedOptionIndex = (int) Prefs.get(OPT_CONFINED_OPTION_INDEX, 0);
	private boolean expandConfinedCompartment = Prefs.get(OPT_EXPAND_CONFINED, false);
	private int maximumRadius = (int) Prefs.get(OPT_MAXIMUM_RADIUS, DEFAULT_MAXIMUM_RADIUS);
	private int randomRadius = (int) Prefs.get(OPT_RANDOM_RADIUS, DEFAULT_RANDOM_RADIUS);
	private boolean subRandomSamples = Prefs.get(OPT_SUB_RANDOM_SAMPLES, true);
	private int histogramBins = (int) Prefs.get(OPT_HISTOGRAM_BINS, DEFAULT_HISTOGRAM_BINS);
	private boolean closeWindowsOnExit = Prefs.get(OPT_CLOSE_WINDOWS_ON_EXIT, false);
	private boolean setOptions = Prefs.get(OPT_SET_OPTIONS, false);

	private boolean showChannel1RGB = Prefs.get(OPT_SHOW_CHANNEL1_RGB, false);
	private boolean showChannel2RGB = Prefs.get(OPT_SHOW_CHANNEL2_RGB, false);
	private boolean showSegmented1RGB = Prefs.get(OPT_SHOW_SEGMENTED1_RGB, false);
	private boolean showSegmented2RGB = Prefs.get(OPT_SHOW_SEGMENTED2_RGB, false);
	private boolean showMergedChannelRGB = Prefs.get(OPT_SHOW_MERGED_CHANNEL_RGB, true);
	private boolean showMergedSegmentedRGB = Prefs.get(OPT_SHOW_MERGED_SEGMENTED_RGB, true);
	private boolean showMergedChannelDisplacementRGB = Prefs.get(OPT_SHOW_MERGED_CHANNEL_DISPLACEMENT_RGB, false);
	private boolean showMergedSegmentedDisplacementRGB = Prefs.get(OPT_SHOW_MERGED_SEGMENTED_DISPLACEMENT_RGB, false);
	private boolean showM1PlotWindow = Prefs.get(OPT_SHOW_M1_PLOT_WINDOW, true);
	private boolean showM2PlotWindow = Prefs.get(OPT_SHOW_M2_PLOT_WINDOW, true);
	private boolean showRPlotWindow = Prefs.get(OPT_SHOW_R_PLOT_WINDOW, true);
	private boolean showM1Statistics = Prefs.get(OPT_SHOW_M1_STATISTICS, true);
	private boolean showM2Statistics = Prefs.get(OPT_SHOW_M2_STATISTICS, true);
	private boolean showRStatistics = Prefs.get(OPT_SHOW_R_STATISTICS, true);
	private boolean saveResults = Prefs.get(OPT_SAVE_RESULTS, false);
	private String resultsDirectory = Prefs.get(OPT_RESULTS_DIRECTORY, System.getProperty("java.io.tmpdir"));
	private double pValue = Prefs.get(OPT_P_VALUE, 0.05);
	private int permutations = (int) Prefs.get(OPT_PERMUTATIONS, 200);

	// Store the channels and frames to use from image stacks
	private static int[] sliceOptions = new int[10];

	// Stores the list of images last used in the selection options
	private ArrayList<String> imageList = new ArrayList<String>();

	public CDA_Plugin()
	{
		super(FRAME_TITLE);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.frame.PlugInFrame#run(java.lang.String)
	 */
	public void run(String arg)
	{
		if (WindowManager.getImageCount() == 0)
		{
			IJ.showMessage("No images opened.");
			return;
		}

		if ("macro".equals(arg))
		{
			runAsPlugin();
			return;
		}

		if (instance != null)
		{
			if (!(instance.getTitle().equals(getTitle())))
			{
				CDA_Plugin cda = (CDA_Plugin) instance;
				Prefs.saveLocation(OPT_LOCATION, cda.getLocation());
				cda.close();
			}
			else
			{
				instance.toFront();
				return;
			}
		}

		instance = this;
		IJ.register(CDA_Plugin.class);
		WindowManager.addWindow(this);

		createFrame();
		setup();

		addKeyListener(IJ.getInstance());
		pack();
		Point loc = Prefs.getLocation(OPT_LOCATION);
		if (loc != null)
			setLocation(loc);
		else
		{
			GUI.center(this);
		}
		if (IJ.isMacOSX())
			setResizable(false);
		setVisible(true);
	}

	private void setup()
	{
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp == null)
			return;
		fillImagesList();
	}

	public synchronized void actionPerformed(ActionEvent e)
	{
		Object actioner = e.getSource();

		if (actioner == null)
			return;

		if (((Button) actioner == okButton) && (parametersReady()))
		{
			Thread thread = new Thread(this, "CDA_Plugin");
			thread.start();
		}
		if ((Button) actioner == helpButton)
		{
			String macro = "run('URL...', 'url=" + gdsc.help.URL.COLOCALISATION + "');";
			new MacroRunner(macro);
		}

		super.notify();
	}

	public void itemStateChanged(ItemEvent e)
	{
		Object actioner = e.getSource();

		if (actioner == null)
			return;

		if ((Checkbox) actioner == subRandomSamplesCheckbox)
		{
			updateNumberOfSamples();
			return;
		}

		if (setResultsOptionsCheckbox.getState())
		{
			setResultsOptionsCheckbox.setState(false);
			setResultsOptions();
			updateNumberOfSamples();
		}
	}

	public void setResultsOptions()
	{
		GenericDialog gd = new GenericDialog("Set Results Options");

		gd.addCheckbox("Show_channel_1", showChannel1RGB);
		gd.addCheckbox("Show_channel_2", showChannel2RGB);
		gd.addCheckbox("Show_ROI_1", showSegmented1RGB);
		gd.addCheckbox("Show_ROI_2", showSegmented2RGB);
		gd.addCheckbox("Show_merged_channel", showMergedChannelRGB);
		gd.addCheckbox("Show_merged_ROI", showMergedSegmentedRGB);
		gd.addCheckbox("Show_merged_channel_max_displacement", showMergedChannelDisplacementRGB);
		gd.addCheckbox("Show_merged_ROI_max_displacement", showMergedSegmentedDisplacementRGB);
		gd.addCheckbox("Show_M1_plot", showM1PlotWindow);
		gd.addCheckbox("Show_M2_plot", showM2PlotWindow);
		gd.addCheckbox("Show_R_plot", showRPlotWindow);
		gd.addCheckbox("Show_M1_statistics", showM1Statistics);
		gd.addCheckbox("Show_M2_statistics", showM2Statistics);
		gd.addCheckbox("Show_R_statistics", showRStatistics);
		gd.addCheckbox("Save_results", saveResults);
		gd.addStringField("Results_directory", resultsDirectory);
		gd.addNumericField("p-Value", pValue, 2);
		gd.addNumericField("Permutations", permutations, 0);

		gd.showDialog();

		if (gd.wasCanceled())
			return;

		showChannel1RGB = gd.getNextBoolean();
		showChannel2RGB = gd.getNextBoolean();
		showSegmented1RGB = gd.getNextBoolean();
		showSegmented2RGB = gd.getNextBoolean();
		showMergedChannelRGB = gd.getNextBoolean();
		showMergedSegmentedRGB = gd.getNextBoolean();
		showMergedChannelDisplacementRGB = gd.getNextBoolean();
		showMergedSegmentedDisplacementRGB = gd.getNextBoolean();
		showM1PlotWindow = gd.getNextBoolean();
		showM2PlotWindow = gd.getNextBoolean();
		showRPlotWindow = gd.getNextBoolean();
		showM1Statistics = gd.getNextBoolean();
		showM2Statistics = gd.getNextBoolean();
		showRStatistics = gd.getNextBoolean();
		saveResults = gd.getNextBoolean();
		resultsDirectory = gd.getNextString();
		pValue = gd.getNextNumber();
		permutations = (int) gd.getNextNumber();
		if (permutations < 0)
			permutations = 0;

		checkResultsDirectory();
	}

	private boolean checkResultsDirectory()
	{
		if (!new File(resultsDirectory).exists())
		{
			IJ.error("The results directory does not exist. Results will not be saved.");
			return false;
		}
		return true;
	}

	public void keyTyped(KeyEvent e)
	{
		// Do nothing
	}

	public void keyPressed(KeyEvent e)
	{
		// Do nothing
	}

	public void keyReleased(KeyEvent e)
	{
		Object actioner = e.getSource();

		if (actioner == null)
			return;

		// Update the number of combinations
		updateNumberOfSamples();
	}

	public void windowClosing(WindowEvent e)
	{
		closeWindowsOnExit = closeWindowsOnExitCheckbox.getState();
		Prefs.set(OPT_CLOSE_WINDOWS_ON_EXIT, closeWindowsOnExit);

		Prefs.saveLocation(OPT_LOCATION, getLocation());

		close();
	}

	public void close()
	{
		if (closeWindowsOnExit)
		{
			closeImagePlus(channel1RGB);
			closeImagePlus(channel2RGB);
			closeImagePlus(segmented1RGB);
			closeImagePlus(segmented2RGB);
			closeImagePlus(mergedChannelRGB);
			closeImagePlus(mergedSegmentedRGB);
			closeImagePlus(mergedSegmentedRGB);
			closeImagePlus(mergedSegmentedDisplacementRGB);
			closeImagePlus(mergedChannelDisplacementRGB);

			closePlotWindow(m1PlotWindow, OPT_LOCATION_PLOT_M1);
			closePlotWindow(m2PlotWindow, OPT_LOCATION_PLOT_M2);
			closePlotWindow(rPlotWindow, OPT_LOCATION_PLOT_R);

			closeDisplayStatistics(m1Statistics, OPT_LOCATION_STATS_M1);
			closeDisplayStatistics(m2Statistics, OPT_LOCATION_STATS_M2);
			closeDisplayStatistics(rStatistics, OPT_LOCATION_STATS_R);

			if (tw != null && tw.isShowing())
			{
				tw.close();
			}
		}

		instance = null;
		super.close();
	}

	private void closeImagePlus(ImagePlus w)
	{
		if (w != null && w.isVisible())
		{
			w.close();
		}
	}

	private void closePlotWindow(PlotWindow w, String locationKey)
	{
		if (w != null && !w.isClosed())
		{
			Prefs.saveLocation(locationKey, w.getLocation());
			w.close();
		}
	}

	private void closeDisplayStatistics(DisplayStatistics w, String locationKey)
	{
		if (w != null && w.getPlotWindow() != null)
		{
			if (w.getPlotWindow().isShowing())
			{
				Prefs.saveLocation(locationKey, w.getPlotWindow().getLocation());
				w.close();
			}
		}
	}

	public void windowActivated(WindowEvent e)
	{
		super.windowActivated(e);

		fillImagesList();
		WindowManager.setWindow(this);
	}

	public static void update()
	{
		if (instance != null)
		{
			CDA_Plugin cda = (CDA_Plugin) instance;

			cda.fillImagesList();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	public void run()
	{
		doCDA();

		synchronized (this)
		{
			super.notify();
		}
	}

	private void doCDA()
	{
		if (!(parametersReady()))
		{
			return;
		}

		IJ.showStatus(gettingConfigStatus);

		// Stores these so that they can be reset later
		channel1Index = channel1List.getSelectedIndex();
		channel2Index = channel2List.getSelectedIndex();
		segmented1Index = segmented1List.getSelectedIndex();
		segmented2Index = segmented2List.getSelectedIndex();
		confinedIndex = confinedList.getSelectedIndex();
		segmented1OptionIndex = segmented1Option.getSelectedIndex();
		segmented2OptionIndex = segmented2Option.getSelectedIndex();
		confinedOptionIndex = confinedOption.getSelectedIndex();
		expandConfinedCompartment = expandConfinedCheckbox.getState();
		subRandomSamples = subRandomSamplesCheckbox.getState();

		maximumRadius = getIntValue(maximumRadiusText.getText(), DEFAULT_MAXIMUM_RADIUS);
		randomRadius = getIntValue(randomRadiusText.getText(), DEFAULT_RANDOM_RADIUS);
		histogramBins = getIntValue(binsText.getText(), DEFAULT_HISTOGRAM_BINS);

		closeWindowsOnExit = closeWindowsOnExitCheckbox.getState();

		ImagePlus imp1 = WindowManager.getImage(channel1List.getSelectedItem());
		ImagePlus imp2 = WindowManager.getImage(channel2List.getSelectedItem());
		ImagePlus roi1 = getRoiSource(imp1, segmented1List, segmented1Option);
		ImagePlus roi2 = getRoiSource(imp2, segmented2List, segmented2Option);
		ImagePlus roi = getRoiSource(null, confinedList, confinedOption);

		runCDA(imp1, imp2, roi1, roi2, roi);
	}

	private void runCDA(ImagePlus imp1, ImagePlus imp2, ImagePlus roi1, ImagePlus roi2, ImagePlus roi)
	{
		// Check for image stacks and get the channel and frame (if applicable)
		if (!getStackOptions(imp1, imp2, roi1, roi2, roi))
			return;

		// TODO - Should the select channel/frames be saved (i.e. the slice options array)? 
		saveOptions();

		long startTime = System.currentTimeMillis();

		// Extract images
		ImageStack imageStack1 = getStack(imp1, 0);
		ImageStack imageStack2 = getStack(imp2, 2);

		// Get the ROIs
		ImageStack roiStack1 = createROI(imp1, roi1, 4, segmented1OptionIndex);
		ImageStack roiStack2 = createROI(imp2, roi2, 6, segmented2OptionIndex);
		ImageStack confinedStack = createROI(imp1, roi, 8, confinedOptionIndex);

		// The images and the masks should be the same size.
		if (!checkDimensions(imageStack1, imageStack2, roiStack1, roiStack2, confinedStack))
			return;

		// Check inputs
		if (roiStack1 == null || roiStack2 == null || confinedStack == null)
		{
			IJ.showMessage(FRAME_TITLE, "ROIs must be at the same width and height of the input images");
			return;
		}

		long[] sum = new long[1];
		if (expandConfinedCompartment)
		{
			// Expand the confined compartment to include all of the ROIs
			unionMask(confinedStack, roiStack1);
			unionMask(confinedStack, roiStack2);
		}
		else
		{
			// Restrict the ROIs to the confined compartment
			roiStack1 = intersectMask(roiStack1, confinedStack, sum, false);
			roiStack2 = intersectMask(roiStack2, confinedStack, sum, false);
		}
		imageStack1 = intersectMask(imageStack1, confinedStack, sum, false);
		imageStack2 = intersectMask(imageStack2, confinedStack, sum, false);

		// This could be changed to check for a certain pixel count (e.g. (overlap / 255) < [count])
		if (sum[0] == 0)
		{
			IJ.error("The two ROIs do not overlap ... quitting");
			return;
		}

		IJ.showStatus(calculatingFirstMandersStatus);

		// Pre-calculate constants
		intersectMask(imageStack1, roiStack1, sum, true);
		double denom1 = (double) sum[0];
		intersectMask(imageStack2, roiStack2, sum, true);
		double denom2 = (double) sum[0];

		IJ.showStatus(mandersCalculationStatus);

		// Build list of shifts above and below the random radius
		int[] shiftIndices = buildShiftIndices(subRandomSamples, randomRadius, maximumRadius);

		List<CalculationResult> results = calculateResults(imageStack1, roiStack1, confinedStack, imageStack2,
				roiStack2, denom1, denom2, shiftIndices);

		if (ImageJHelper.isInterrupted())
			return;

		// Get unshifted result
		CalculationResult unshifted = null;
		for (CalculationResult r : results)
		{
			if (r.distance == 0)
			{
				unshifted = r;
				break;
			}
		}

		// Display an image of the largest shift
		ImageStack lastChannelShiftedRawStack = null;
		ImageStack lastSegmentedShiftedRawStack = null;
		if (showMergedChannelDisplacementRGB || showMergedSegmentedDisplacementRGB)
		{
			TwinStackShifter twinImageShifter = new TwinStackShifter(imageStack1, roiStack1, confinedStack);
			twinImageShifter.run(maximumRadius, 0);
			lastChannelShiftedRawStack = twinImageShifter.getResultStack();
			lastSegmentedShiftedRawStack = twinImageShifter.getResultStack2();
		}

		reportResults(imp1, imp2, imageStack1, imageStack2, roiStack1, roiStack2, confinedStack,
				lastChannelShiftedRawStack, lastSegmentedShiftedRawStack, unshifted.m1, unshifted.m2, unshifted.r,
				results);

		IJ.showStatus(doneStatus + " Time taken = " + (System.currentTimeMillis() - startTime) / 1000.0);
	}

	private int[] buildShiftIndices(boolean subRandomSamples, int randomRadius, int maximumRadius)
	{
		int[] shiftIndices1 = randomIndices(randomRadius, maximumRadius);
		int[] shiftIndices2 = randomIndices(0, (subRandomSamples) ? randomRadius : 0);
		int[] shiftIndices = merge(shiftIndices1, shiftIndices2);
		return shiftIndices;
	}

	private int[] randomIndices(int minimumRadius, int maximumRadius)
	{
		// Count the number of permutations
		double maximumRadius2 = maximumRadius * maximumRadius;
		double minimumRadius2 = minimumRadius * minimumRadius;
		int totalPermutations = 0;
		for (int i = -maximumRadius; i <= maximumRadius; ++i)
		{
			for (int j = -maximumRadius; j <= maximumRadius; ++j)
			{
				double distance2 = i * i + j * j;
				if (distance2 > maximumRadius2 || distance2 <= minimumRadius2)
					continue;
				totalPermutations++;
			}
		}

		if (totalPermutations == 0)
			return new int[0];

		// Randomise the permutations
		int[] indices = new int[totalPermutations];
		totalPermutations = 0;
		for (int i = -maximumRadius; i <= maximumRadius; ++i)
		{
			for (int j = -maximumRadius; j <= maximumRadius; ++j)
			{
				double distance2 = i * i + j * j;
				if (distance2 > maximumRadius2 || distance2 <= minimumRadius2)
					continue;
				// Pack the magnitude of the shift into the first 4 bytes and then pack the signs
				int index = (Math.abs(i) & 0xff) << 8 | Math.abs(j) & 0xff;
				if (i < 0)
					index |= 0x00010000;
				if (j < 0)
					index |= 0x00020000;

				indices[totalPermutations++] = index;
			}
		}

		if (permutations < totalPermutations && permutations > 0)
		{
			// Fisher-Yates shuffle to randomly select
			Random rand = new Random();
			rand.shuffle(indices);
			return Arrays.copyOf(indices, permutations);
		}
		return indices;
	}

	private int[] merge(int[] shiftIndices1, int[] shiftIndices2)
	{
		// Include an extra entry for zero shift
		int[] indices = new int[shiftIndices1.length + shiftIndices2.length + 1];
		int j = 0;
		for (int index : shiftIndices1)
			indices[j++] = index;
		for (int index : shiftIndices2)
			indices[j++] = index;
		return indices;
	}

	private List<CalculationResult> calculateResults(ImageStack imageStack1, ImageStack roiStack1,
			ImageStack confinedStack, ImageStack imageStack2, ImageStack roiStack2, double denom1, double denom2,
			int[] shiftIndices)
	{
		// Initialise the progress count
		int totalSteps = shiftIndices.length;
		IJ.showStatus("Creating CDA Engine ...");

		List<CalculationResult> results = new ArrayList<CalculationResult>(totalSteps);

		int threads = Prefs.getThreads();
		CDAEngine engine = new CDAEngine(imageStack1, roiStack1, confinedStack, imageStack2, roiStack2, denom1, denom2,
				results, totalSteps, threads);
		// Wait for initialisation
		engine.isInitialised();

		IJ.showProgress(0);
		IJ.showStatus("Computing shifts ...");
		
		for (int n = 0; n < shiftIndices.length; n++)
		{
			int index = shiftIndices[n];
			int y = index & 0xff;
			int x = (index & 0xff00) >> 8;
			if ((index & 0x00010000) == 0x00010000)
				x = -x;
			if ((index & 0x00020000) == 0x00020000)
				y = -y;

			engine.run(n, x, y);

			if (ImageJHelper.isInterrupted())
			{
				engine.end(true);
				break;
			}
		}

		engine.end(false);
		//IJ.log("# of results = " + results.size() + " / " + totalSteps);

		IJ.showProgress(1.0);
		IJ.showStatus("Computing shifts ...");

		return results;
	}

	private boolean checkDimensions(ImageStack imageStack1, ImageStack imageStack2, ImageStack roiStack1,
			ImageStack roiStack2, ImageStack confinedStack)
	{
		int w = imageStack1.getWidth();
		int h = imageStack1.getHeight();
		int s = imageStack1.getSize();

		if (!checkDimensions(w, h, s, imageStack2))
			return false;
		if (!checkDimensions(w, h, s, roiStack1))
			return false;
		if (!checkDimensions(w, h, s, roiStack1))
			return false;
		if (!checkDimensions(w, h, s, confinedStack))
			return false;
		return true;
	}

	private boolean checkDimensions(int w, int h, int s, ImageStack stack)
	{
		if (stack != null && (w != stack.getWidth() || h != stack.getHeight() || s != stack.getSize()))
		{
			IJ.showMessage(FRAME_TITLE, "Images must be the same width and height and depth");
			return false;
		}
		return true;
	}

	private ImagePlus getRoiSource(ImagePlus imp, Choice imageList, Choice optionList)
	{
		// Get the ROI option
		if (optionList.getSelectedItem().equals(OPTION_NONE))
		{
			// No ROI image
			return null;
		}

		// Find the image in the image list
		if (imageList.getSelectedItem().equals(CHANNEL_IMAGE))
		{
			// Channel image is the source for the ROI
			return imp;
		}

		return WindowManager.getImage(imageList.getSelectedItem());
	}

	private boolean getStackOptions(ImagePlus imp1, ImagePlus imp2, ImagePlus roi1, ImagePlus roi2, ImagePlus roi)
	{
		if (isStack(imp1) || isStack(imp1) || isStack(roi1) || isStack(roi2) || isStack(roi))
		{
			GenericDialog gd = new GenericDialog("Slice options");
			gd.addMessage("Stacks detected. Please select the slices.");

			boolean added = false;
			added |= addOptions(gd, imp1, "Image_1", 0);
			added |= addOptions(gd, imp2, "Image_2", 2);
			added |= addOptions(gd, roi1, "ROI_1", 4);
			added |= addOptions(gd, roi2, "ROI_2", 6);
			added |= addOptions(gd, roi, "Confined", 8);

			if (added)
			{
				gd.showDialog();

				if (gd.wasCanceled())
					return false;

				// Populate the channels and frames into an options array
				getOptions(gd, imp1, 0);
				getOptions(gd, imp2, 2);
				getOptions(gd, roi1, 4);
				getOptions(gd, roi2, 6);
				getOptions(gd, roi, 8);
			}
		}
		return true;
	}

	private boolean isStack(ImagePlus imp)
	{
		return (imp != null && imp.getStackSize() > 1);
	}

	private boolean addOptions(GenericDialog gd, ImagePlus imp, String title, int offset)
	{
		boolean added = false;
		if (imp != null)
		{
			String[] channels = getChannels(imp);
			String[] frames = getFrames(imp);

			if (channels.length > 1 || frames.length > 1)
			{
				added = true;
				//gd.addMessage(title);
			}

			setOption(gd, channels, title + "_Channel", offset);
			setOption(gd, frames, title + "_Frame", offset + 1);
		}
		return added;
	}

	private boolean setOption(GenericDialog gd, String[] choices, String title, int offset)
	{
		if (choices.length > 1)
		{
			// Restore previous selection
			int c = (sliceOptions[offset] > 0 && sliceOptions[offset] <= choices.length) ? sliceOptions[offset] - 1 : 0;
			gd.addChoice(title, choices, choices[c]);
			return true;
		}
		else
		{
			// Set to default
			sliceOptions[offset] = 1;
			return false;
		}
	}

	private String[] getChannels(ImagePlus imp)
	{
		int c = imp.getNChannels();
		String[] result = new String[c];
		for (int i = 0; i < c; i++)
			result[i] = Integer.toString(i + 1);
		return result;
	}

	private String[] getFrames(ImagePlus imp)
	{
		int c = imp.getNFrames();
		String[] result = new String[c];
		for (int i = 0; i < c; i++)
			result[i] = Integer.toString(i + 1);
		return result;
	}

	private void getOptions(GenericDialog gd, ImagePlus imp, int offset)
	{
		if (imp != null)
		{
			String[] channels = getChannels(imp);
			String[] frames = getFrames(imp);

			if (channels.length > 1)
				sliceOptions[offset] = gd.getNextChoiceIndex() + 1;
			if (frames.length > 1)
				sliceOptions[offset + 1] = gd.getNextChoiceIndex() + 1;
		}
	}

	private int getIntValue(String text, int defaultValue)
	{
		try
		{
			return Integer.parseInt(text);
		}
		catch (NumberFormatException e)
		{
			return defaultValue;
		}
	}

	private ImageStack getStack(ImagePlus imp, int offset)
	{
		int channel = sliceOptions[offset];
		int frame = sliceOptions[offset + 1];
		int slices = imp.getNSlices();

		ImageStack stack = new ImageStack(imp.getWidth(), imp.getHeight());
		ImageStack inputStack = imp.getImageStack();

		for (int slice = 1; slice <= slices; slice++)
		{
			// Convert to a short processor
			ImageProcessor ip = inputStack.getProcessor(imp.getStackIndex(channel, slice, frame));
			stack.addSlice(null, convertToShortProcessor(imp, ip));
		}
		return stack;
	}

	private ShortProcessor convertToShortProcessor(ImagePlus channel, ImageProcessor ip)
	{
		ShortProcessor result;
		if (ip.getClass() == ShortProcessor.class)
			result = (ShortProcessor) ip.duplicate();
		else
			result = (ShortProcessor) (ip.convertToShort(false));
		result.setMinAndMax(channel.getDisplayRangeMin(), channel.getDisplayRangeMax());
		result.setRoi(channel.getRoi());
		return result;
	}

	private ImageStack createROI(ImagePlus channelImp, ImagePlus roiImp, int offset, int optionIndex)
	{
		ByteProcessor bp;

		// Get the ROI option
		String option = ROI_OPTIONS[optionIndex];

		if (option.equals(OPTION_NONE) || roiImp == null || roiImp.getWidth() != channelImp.getWidth() ||
				roiImp.getHeight() != channelImp.getHeight())
		{
			// No ROI - create a mask that covers the entire image
			return defaultMask(channelImp);
		}

		ImageStack inputStack = roiImp.getImageStack();
		int channel = sliceOptions[offset];
		int frame = sliceOptions[offset + 1];
		int slices = roiImp.getNSlices();

		if (option.equals(OPTION_MIN_VALUE) || option.equals(OPTION_MASK))
		{
			// Use the ROI image to create a mask either using:
			// - non-zero pixels (i.e. a mask)
			// - all pixels above the minimum display value
			ImageStack result = new ImageStack(roiImp.getWidth(), roiImp.getHeight());

			double min = (option.equals(OPTION_MIN_VALUE)) ? roiImp.getDisplayRangeMin() : 1;

			for (int slice = 1; slice <= slices; slice++)
			{
				bp = new ByteProcessor(roiImp.getWidth(), roiImp.getHeight());
				ImageProcessor roiIp = inputStack.getProcessor(roiImp.getStackIndex(channel, slice, frame));
				for (int i = roiIp.getPixelCount(); i-- > 0;)
				{
					if (roiIp.get(i) >= min)
					{
						bp.set(i, 255);
					}
				}
				result.addSlice(null, bp);
			}

			return result;
		}

		if (option.equals(OPTION_USE_ROI))
		{
			// Use the ROI from the ROI image
			Roi roi = roiImp.getRoi();

			if (roi != null)
			{
				// Use a mask for an irregular ROI
				ImageProcessor ipMask = roiImp.getMask();

				// Create a mask from the ROI rectangle
				Rectangle bounds = roi.getBounds();
				int xOffset = bounds.x;
				int yOffset = bounds.y;
				int rwidth = bounds.width;
				int rheight = bounds.height;

				ImageStack result = new ImageStack(roiImp.getWidth(), roiImp.getHeight());

				for (int slice = 1; slice <= slices; slice++)
				{
					bp = new ByteProcessor(roiImp.getWidth(), roiImp.getHeight());
					for (int y = 0; y < rheight; y++)
					{
						for (int x = 0; x < rwidth; x++)
						{
							if (ipMask == null || ipMask.get(x, y) != 0)
							{
								bp.set(x + xOffset, y + yOffset, 255);
							}
						}
					}
					result.addSlice(null, bp);
				}
				return result;
			}
		}

		return defaultMask(channelImp);
	}

	private ImageProcessor getRoiIp(ImageProcessor channelIp, int index)
	{
		ImageProcessor roiIp = null;

		if (index < 0)
		{
			roiIp = channelIp;
		}
		else
		{
			ImagePlus roiImage = WindowManager.getImage(imageList.get(index));
			if (roiImage != null)
			{
				roiIp = roiImage.getProcessor();
				roiIp.setRoi(roiImage.getRoi());
			}
		}
		return roiIp;
	}

	/**
	 * Build a stack that covers all z-slices in the image
	 */
	private ImageStack defaultMask(ImagePlus imp)
	{
		ImageStack result = new ImageStack(imp.getWidth(), imp.getHeight());
		for (int s = imp.getNSlices(); s-- > 0;)
		{
			ByteProcessor bp = new ByteProcessor(imp.getWidth(), imp.getHeight());
			bp.add(255);
			result.addSlice(null, bp);
		}
		return result;
	}

	private void reportResults(ImagePlus imp1, ImagePlus imp2, ImageStack imageStack1, ImageStack imageStack2,
			ImageStack roiStack1, ImageStack roiStack2, ImageStack confinedStack,
			ImageStack lastChannelShiftedRawStack, ImageStack lastSegmentedShiftedRawStack, double M1, double M2,
			double R, List<CalculationResult> results)
	{
		IJ.showStatus(processingMandersStatus);

		// Extract the results into individual arrays
		double[] distances = new double[results.size()];
		double[] m1Values = new double[results.size()];
		double[] m2Values = new double[results.size()];
		double[] rValues = new double[results.size()];

		ArrayList<Integer> indexDistance = new ArrayList<Integer>();

		for (int i = 0; i < m1Values.length; ++i)
		{
			CalculationResult result = results.get(i);

			distances[i] = result.distance;
			m1Values[i] = result.m1;
			m2Values[i] = result.m2;
			rValues[i] = result.r;

			// All results over the random distance threshold will be used for the significance calculation
			if (distances[i] > randomRadius)
			{
				indexDistance.add(i);
			}
		}

		// Extract the results above the random threshold
		float[] m1ValuesForRandom = new float[indexDistance.size()];
		float[] m2ValuesForRandom = new float[indexDistance.size()];
		float[] rValuesForRandom = new float[indexDistance.size()];

		for (int i = 0; i < m1ValuesForRandom.length; ++i)
		{
			m1ValuesForRandom[i] = (float) m1Values[indexDistance.get(i)];
			m2ValuesForRandom[i] = (float) m2Values[indexDistance.get(i)];
			rValuesForRandom[i] = (float) rValues[indexDistance.get(i)];
		}

		// Sort the 'random' result arrays
		Arrays.sort(m1ValuesForRandom);
		Arrays.sort(m2ValuesForRandom);
		Arrays.sort(rValuesForRandom);

		// Initialise the graph points
		int deltaY = 10;

		double[] spacedX = new double[maximumRadius];
		double[] spacedY = new double[deltaY];
		double[] ceroValuesX = new double[maximumRadius];
		double[] ceroValuesY = new double[deltaY];

		for (int i = 0; i < maximumRadius; ++i)
		{
			spacedX[i] = i;
			ceroValuesX[i] = 0.0D;
		}

		for (int i = 0; i < deltaY; ++i)
		{
			spacedY[i] = (1.0D / deltaY * i);
			ceroValuesY[i] = 0.0D;
		}

		// TODO - Only compute the images that will be displayed or saved as results
		boolean isSaveResults = (saveResults && checkResultsDirectory());

		IJ.showStatus(preparingPlotsStatus);
		Plot plotM1 = null, plotM2 = null, plotR = null;

		if (showM1PlotWindow)
			plotM1 = createPlot(distances, m1Values, Color.red, Color.blue, plotM1Title, plotXLabel, plotM1YLabel,
					spacedX, ceroValuesX, ceroValuesY, spacedY);
		if (showM2PlotWindow)
			plotM2 = createPlot(distances, m2Values, Color.green, Color.blue, plotM2Title, plotXLabel, plotM2YLabel,
					spacedX, ceroValuesX, ceroValuesY, spacedY);
		if (showRPlotWindow)
			plotR = createPlot(distances, rValues, Color.blue, Color.green, plotRTitle, plotXLabel, plotRYLabel,
					spacedX, ceroValuesX, ceroValuesY, spacedY);

		// Prepare output images
		// Output the channel 1 as red
		// Output the channel 2 as green

		ImageStack channel1RGBIP = null, channel2RGBIP = null, segmented1RGBIP = null, segmented2RGBIP = null;
		ImageStack mergedChannelIP = null, mergedSegmentedRGBIP = null, mergedChannelDisplacementIP = null, mergedSegmentedDisplacementIP = null;

		if (showChannel1RGB || showMergedChannelRGB)
			channel1RGBIP = createColorOutput(imageStack1, confinedStack, 0);
		if (showChannel2RGB || showMergedChannelRGB)
			channel2RGBIP = createColorOutput(imageStack2, confinedStack, 1);
		if (showSegmented1RGB || showMergedSegmentedRGB)
			segmented1RGBIP = createColorOutput(roiStack1, confinedStack, 0);
		if (showSegmented2RGB || showMergedSegmentedRGB)
			segmented2RGBIP = createColorOutput(roiStack2, confinedStack, 1);
		int w = imageStack1.getWidth();
		int h = imageStack1.getHeight();
		int slices = imageStack1.getSize();
		if (showMergedChannelRGB)
			mergedChannelIP = new ImageStack(w, h, slices);
		if (showMergedSegmentedRGB)
			mergedSegmentedRGBIP = new ImageStack(w, h, slices);
		if (showMergedChannelDisplacementRGB)
			mergedChannelDisplacementIP = createColorOutput(lastChannelShiftedRawStack, confinedStack, 0);
		if (showMergedSegmentedDisplacementRGB)
			mergedSegmentedDisplacementIP = createColorOutput(lastSegmentedShiftedRawStack, confinedStack, 0);

		for (int n = 1; n <= imageStack1.getSize(); n++)
		{
			// Mix the channels
			if (showMergedChannelRGB)
			{
				ColorProcessor cp = new ColorProcessor(w, h);
				cp.setPixels(0, channel1RGBIP.getProcessor(n).toFloat(0, null));
				cp.setPixels(1, channel2RGBIP.getProcessor(n).toFloat(1, null));
				mergedChannelIP.setPixels(cp.getPixels(), n);
			}

			// Mix the masks
			if (showMergedSegmentedRGB)
			{
				ColorProcessor cp = new ColorProcessor(w, h);
				cp.setPixels(0, segmented1RGBIP.getProcessor(n).toFloat(0, null));
				cp.setPixels(1, segmented2RGBIP.getProcessor(n).toFloat(1, null));
				mergedSegmentedRGBIP.setPixels(cp.getPixels(), n);
			}

			// For reference output the maximum shift of channel 1 AND the original channel 2
			if (showMergedChannelDisplacementRGB)
			{
				ColorProcessor cp = new ColorProcessor(w, h);
				cp.setPixels(mergedChannelDisplacementIP.getPixels(n));
				cp.setPixels(1, channel2RGBIP.getProcessor(n).toFloat(1, null));
			}

			if (showMergedSegmentedDisplacementRGB)
			{
				ColorProcessor cp = new ColorProcessor(w, h);
				cp.setPixels(mergedSegmentedDisplacementIP.getPixels(n));
				cp.setPixels(1, segmented2RGBIP.getProcessor(n).toFloat(1, null));
			}
		}

		// Set the area outside the ROI to white.
		//		invert(channel1RGBIP, confinedStack);
		//		invert(channel2RGBIP, confinedStack);
		//		invert(segmented1RGBIP, confinedStack);
		//		invert(segmented2RGBIP, confinedStack);
		//		invert(mergedChannelIP, confinedStack);
		//		invert(mergedSegmentedRGBIP, confinedStack);
		//		invert(mergedChannelDisplacementIP, confinedStack);
		//		invert(mergedSegmentedDisplacementIP, confinedStack);

		createDisplayImages(channel1RGBIP, channel2RGBIP, segmented1RGBIP, segmented2RGBIP, mergedChannelIP,
				mergedSegmentedRGBIP, mergedChannelDisplacementIP, mergedSegmentedDisplacementIP);

		// Update the images
		updateImage(channel1RGB, channel1RGBTitle, channel1RGBIP, showChannel1RGB);
		updateImage(channel2RGB, channel2RGBTitle, channel2RGBIP, showChannel2RGB);
		updateImage(segmented1RGB, segmented1RGBTitle, segmented1RGBIP, showSegmented1RGB);
		updateImage(segmented2RGB, segmented2RGBTitle, segmented2RGBIP, showSegmented2RGB);
		updateImage(mergedChannelRGB, mergedChannelTitle, mergedChannelIP, showMergedChannelRGB);
		updateImage(mergedSegmentedRGB, mergedSegmentedTitle, mergedSegmentedRGBIP, showMergedSegmentedRGB);
		updateImage(mergedChannelDisplacementRGB, lastShiftedChannelTitle, mergedChannelDisplacementIP,
				showMergedChannelDisplacementRGB);
		updateImage(mergedSegmentedDisplacementRGB, lastShiftedSegmentedTitle, mergedSegmentedDisplacementIP,
				showMergedSegmentedDisplacementRGB);

		// Create plots of the results
		m1PlotWindow = refreshPlotWindow(m1PlotWindow, showM1PlotWindow, plotM1, OPT_LOCATION_PLOT_M1);
		m2PlotWindow = refreshPlotWindow(m2PlotWindow, showM2PlotWindow, plotM2, OPT_LOCATION_PLOT_M2);
		rPlotWindow = refreshPlotWindow(rPlotWindow, showRPlotWindow, plotR, OPT_LOCATION_PLOT_R);

		// Create display statistics for the Mander's and correlation values
		FloatProcessor m1ValuesFP = new FloatProcessor(m1ValuesForRandom.length, 1, m1ValuesForRandom, null);
		FloatProcessor m2ValuesFP = new FloatProcessor(m2ValuesForRandom.length, 1, m2ValuesForRandom, null);
		FloatProcessor rValuesFP = new FloatProcessor(rValuesForRandom.length, 1, rValuesForRandom, null);

		m1Statistics = refreshDisplayStatistics(m1Statistics, showM1Statistics, m1ValuesFP, m1ValuesForRandom,
				m1HistogramTitle, Color.red, "M1 ", M1, OPT_LOCATION_STATS_M1);
		m2Statistics = refreshDisplayStatistics(m2Statistics, showM2Statistics, m2ValuesFP, m2ValuesForRandom,
				m2HistogramTitle, Color.green, "M2 ", M2, OPT_LOCATION_STATS_M2);
		rStatistics = refreshDisplayStatistics(rStatistics, showRStatistics, rValuesFP, rValuesForRandom,
				rHistogramTitle, Color.blue, "R ", R, OPT_LOCATION_STATS_R);

		String id = generateId();

		StringBuffer heading = null;

		// Output the results to a text window
		if (tw == null || !tw.isShowing())
		{
			heading = createHeading(heading);
			tw = new TextWindow(FRAME_TITLE + " Results", heading.toString(), "", 1000, 300);
		}

		StringBuffer resultsEntry = new StringBuffer();
		addField(resultsEntry, id);
		addField(resultsEntry, getImageTitle(imp1, 0));
		addField(resultsEntry, getImageTitle(imp2, 2));

		// Note that the segmented indices must be offset by one to account for the extra option
		// in the list of segmented images 
		addRoiField(resultsEntry, segmented1OptionIndex, segmented1Index - 1,
				getRoiIp(imageStack1.getProcessor(1), segmented1Index - 1), 4);
		addRoiField(resultsEntry, segmented2OptionIndex, segmented2Index - 1,
				getRoiIp(imageStack2.getProcessor(1), segmented2Index - 1), 6);
		addRoiField(resultsEntry, confinedOptionIndex, confinedIndex,
				getRoiIp(confinedStack.getProcessor(1), confinedIndex), 8);
		addField(resultsEntry, expandConfinedCompartment);
		addField(resultsEntry, maximumRadius);
		addField(resultsEntry, randomRadius);
		addField(resultsEntry, results.size());
		addField(resultsEntry, histogramBins);
		addField(resultsEntry, pValue);

		addResults(resultsEntry, m1Statistics);
		addResults(resultsEntry, m2Statistics);
		addResults(resultsEntry, rStatistics);

		tw.append(resultsEntry.toString());

		if (isSaveResults)
		{
			try
			{
				// Save results to file
				String directory = ImageJHelper.combinePath(resultsDirectory, id);
				if (!new File(directory).mkdirs())
					return;
				IJ.save(mergedSegmentedRGB, directory + File.separatorChar + "MergedROI.tif");
				IJ.save(mergedChannelRGB, directory + File.separatorChar + "MergedChannel.tif");

				FileOutputStream fos = new FileOutputStream(directory + File.separatorChar + "results.txt");
				OutputStreamWriter out = new OutputStreamWriter(fos, "UTF-8");
				String newLine = System.getProperty("line.separator");
				heading = createHeading(heading);
				out.write(heading.toString());
				out.write(newLine);
				out.write(resultsEntry.toString());
				out.write(newLine);
				out.close();
			}
			catch (Exception e)
			{
				return;
			}
		}
	}

	private Object getImageTitle(ImagePlus imp, int offset)
	{
		if (imp != null)
		{
			StringBuilder sb = new StringBuilder(imp.getTitle());
			if (imp.getStackSize() > 1 && (imp.getNChannels() > 1 || imp.getNFrames() > 1))
			{
				sb.append(" (c").append(sliceOptions[offset]).append(",t").append(sliceOptions[offset + 1]).append(")");
			}
			return sb.toString();
		}
		else
			return "[Unknown]";
	}

	private String generateId()
	{
		DateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		return "cda" + df.format(new Date());
	}

	private void createDisplayImages(ImageStack channel1RGBIP, ImageStack channel2RGBIP, ImageStack segmented1RGBIP,
			ImageStack segmented2RGBIP, ImageStack mergedChannelIP, ImageStack mergedSegmentedRGBIP,
			ImageStack mergedChannelDisplacementIP, ImageStack mergedSegmentedDisplacementIP)
	{
		// Only create the images if they have not yet been made.
		if (channel1RGB == null)
			channel1RGB = newImagePlus(channel1RGBTitle, channel1RGBIP);
		if (channel2RGB == null)
			channel2RGB = newImagePlus(channel2RGBTitle, channel2RGBIP);
		if (segmented1RGB == null)
			segmented1RGB = newImagePlus(segmented1RGBTitle, segmented1RGBIP);
		if (segmented2RGB == null)
			segmented2RGB = newImagePlus(segmented2RGBTitle, segmented2RGBIP);
		if (mergedChannelRGB == null)
			mergedChannelRGB = newImagePlus(mergedChannelTitle, mergedChannelIP);
		if (mergedSegmentedRGB == null)
			mergedSegmentedRGB = newImagePlus(mergedSegmentedTitle, mergedSegmentedRGBIP);
		if (mergedChannelDisplacementRGB == null)
			mergedChannelDisplacementRGB = newImagePlus(lastShiftedChannelTitle, mergedChannelDisplacementIP);
		if (mergedSegmentedDisplacementRGB == null)
			mergedSegmentedDisplacementRGB = newImagePlus(lastShiftedSegmentedTitle, mergedSegmentedDisplacementIP);
	}

	private ImagePlus newImagePlus(String title, ImageStack stack)
	{
		if (stack != null)
			return new ImagePlus(title, stack);
		return null;
	}

	private void updateImage(ImagePlus imp, String title, ImageStack stack, boolean show)
	{
		if (stack != null)
			imp.setStack(title, stack);
		//imp.getMask(); // Re-initialise the image? 

		if (imp != null)
		{
			if (show)
				imp.show();
			else
				imp.hide();
		}
	}

	private StringBuffer createHeading(StringBuffer heading)
	{
		if (heading == null)
		{
			heading = new StringBuffer();

			addField(heading, "Exp. Id");
			addField(heading, choiceChannel1);
			addField(heading, choiceChannel2);
			addField(heading, "ROI 1");
			addField(heading, "ROI 2");
			addField(heading, "Confined");
			addField(heading, choiceExpandConfined.trim());
			addField(heading, "D-max");
			addField(heading, "D-random");
			addField(heading, "Samples");
			addField(heading, "Bins");
			addField(heading, "p-Value");

			addField(heading, "M1");
			addField(heading, "M1 av");
			addField(heading, "M1 sd");
			addField(heading, "M1 limits");
			addField(heading, "M1 result");
			addField(heading, "M2");
			addField(heading, "M2 av");
			addField(heading, "M2 sd");
			addField(heading, "M2 limits");
			addField(heading, "M2 result");
			addField(heading, "R");
			addField(heading, "R av");
			addField(heading, "R sd");
			addField(heading, "R limits");
			addField(heading, "R result");
		}
		return heading;
	}

	private PlotWindow refreshPlotWindow(PlotWindow plotWindow, boolean showPlotWindow, Plot plot, String locationKey)
	{
		if (showPlotWindow && plot != null)
		{
			if (plotWindow != null && !plotWindow.isClosed())
			{
				plotWindow.drawPlot(plot);
			}
			else
			{
				plotWindow = plot.show();
				// Restore location from preferences
				restoreLocation(plotWindow, Prefs.getLocation(locationKey));
			}
		}
		else
		{
			closePlotWindow(plotWindow, locationKey);
			plotWindow = null;
		}

		return plotWindow;
	}

	private DisplayStatistics refreshDisplayStatistics(DisplayStatistics displayStatistics, boolean showStatistics,
			FloatProcessor m1ValuesFP, float[] valuesForRandom, String histogramTitle, Color colour, String xTitle,
			double value, String locationKey)
	{
		// Draw a plot of the values. This will be used within the DisplayStatistics chart
		PlotResults statsPlot = new PlotResults(value, histogramBins, Tools.toDouble(valuesForRandom), colour);

		statsPlot.setXTitle(xTitle);
		statsPlot.setYTitle(plotPDFYLabel);
		statsPlot.setTitle("CDA " + xTitle + " PDF");

		// This is needed to calculate the probability limits for the results table 
		// even if the plot is not shown
		statsPlot.calculate(pValue);

		// Generate the statistics needed for the plot
		FloatStatistics floatStatistics = new FloatStatistics(m1ValuesFP);

		Point point = null;
		if (displayStatistics == null)
		{
			displayStatistics = new DisplayStatistics(histogramTitle, xTitle);
			point = Prefs.getLocation(locationKey);
		}
		displayStatistics.setData(statsPlot, floatStatistics, value);

		// Show the new plot
		if (showStatistics)
		{
			displayStatistics.draw();
			// Restore the position if necessary
			restoreLocation(displayStatistics.getPlotWindow(), point);
		}
		else
		{
			closeDisplayStatistics(displayStatistics, locationKey);
		}

		return displayStatistics;
	}

	private void restoreLocation(Frame frame, Point point)
	{
		if (point != null)
		{
			frame.setLocation(point);
		}
	}

	@SuppressWarnings("unused")
	private void invert(ImageStack imageStack, ImageStack confinedStack)
	{
		for (int n = 1; n <= imageStack.getSize(); n++)
		{
			ImageProcessor ip = imageStack.getProcessor(n);
			ImageProcessor confinedIp = confinedStack.getProcessor(n);

			for (int i = confinedIp.getPixelCount(); i-- > 0;)
			{
				if (confinedIp.get(i) == 0)
				{
					ip.set(i, 0xFFFFFFFF);
				}
			}
		}
	}

	private ImageStack createColorOutput(ImageStack image, ImageStack mask, int channel)
	{
		ImageStack result = new ImageStack(image.getWidth(), image.getHeight());
		for (int n = 1; n <= image.getSize(); n++)
		{
			ByteProcessor byteIP = (ByteProcessor) (image.getProcessor(n).duplicate().convertToByte(true));
			intersectMask(byteIP, (ByteProcessor) mask.getProcessor(n), byteIP);

			ColorProcessor cp = new ColorProcessor(byteIP.getWidth(), byteIP.getHeight());
			cp.setPixels(channel, byteIP.toFloat(0, null));

			result.addSlice(null, cp);
		}
		return result;
	}

	private Plot createPlot(double[] distances, double[] values, Color color, Color avColor, String title,
			String xLabel, String yLabel, double[] spacedX, double[] ceroValuesX, double[] ceroValuesY, double[] spacedY)
	{
		float[] dummy = null;
		Plot plot = new Plot(title, xLabel.concat(pixelsUnitString), yLabel, dummy, dummy, Plot.X_NUMBERS +
				Plot.Y_NUMBERS + Plot.X_TICKS + Plot.Y_TICKS);

		double min = 0;
		for (double d : values)
		{
			if (min > d)
				min = d;
		}

		plot.setLimits(0.0D, maximumRadius, min, 1.0D);
		plot.setColor(color);
		plot.addPoints(distances, values, Plot.X);
		addAverage(plot, distances, values, avColor);
		plot.setColor(Color.black);
		plot.addPoints(spacedX, ceroValuesX, 5);
		plot.addPoints(ceroValuesY, spacedY, 5);
		return plot;
	}

	private void addAverage(Plot plot, double[] distances, double[] values, Color color)
	{
		double maxDistance = 0;
		for (double d : distances)
		{
			if (maxDistance < d)
				maxDistance = d;
		}

		int n = (int) (maxDistance + 0.5) + 1;
		double[] sum = new double[n];
		int[] count = new int[n];
		for (int i = 0; i < distances.length; i++)
		{
			// Round up distance to nearest int
			int d = (int) (distances[i] + 0.5);
			sum[d] += values[i];
			count[d]++;
		}
		ArrayList<Double> avDistances = new ArrayList<Double>(n);
		ArrayList<Double> avValues = new ArrayList<Double>(n);
		for (int i = (subRandomSamples) ? 0 : 1; i < n; i++)
		{
			if (count[i] > 0)
			{
				avDistances.add((double) i);
				avValues.add(sum[i] / count[i]);
			}
		}

		plot.setColor(color);
		plot.addPoints(toArray(avDistances), toArray(avValues), Plot.LINE);
	}

	private double[] toArray(ArrayList<Double> values)
	{
		double[] array = new double[values.size()];
		for (int i = 0; i < array.length; i++)
		{
			array[i] = values.get(i);
		}
		return array;
	}

	private StringBuffer addField(StringBuffer buffer, Object field)
	{
		if (buffer.length() > 0)
		{
			buffer.append("\t");
		}
		buffer.append(field);
		return buffer;
	}

	private void addRoiField(StringBuffer buffer, int roiIndex, int imageIndex, ImageProcessor roiIp, int offset)
	{
		String roiOption = ROI_OPTIONS[roiIndex];
		addField(buffer, roiOption);
		if (!roiOption.equals(OPTION_NONE))
		{
			buffer.append(" : ");
			if (imageIndex < 0)
				buffer.append(CHANNEL_IMAGE);
			else
				buffer.append(imageList.get(imageIndex)).append(" (c").append(sliceOptions[offset]).append(",t")
						.append(sliceOptions[offset + 1]).append(")");
			if (roiOption.equals(OPTION_MIN_VALUE))
			{
				if (roiIp != null)
				{
					buffer.append(" (>");
					buffer.append(roiIp.getMin());
					buffer.append(")");
				}
			}
			else if (roiOption.equals(OPTION_USE_ROI))
			{
				if (roiIp != null)
				{
					Rectangle r = roiIp.getRoi();
					if (r != null)
					{
						buffer.append(" (");
						buffer.append(r.x).append(",").append(r.y).append(":").append(r.x + r.width).append(",")
								.append(r.y + r.height);
						buffer.append(")");
					}
				}
			}
		}
	}

	private void addResults(StringBuffer sb, DisplayStatistics displayStatistics)
	{
		double value = displayStatistics.getValue();
		double av = displayStatistics.getAverage();
		double sd = displayStatistics.getStdDev();
		double lowerLimit = displayStatistics.getLowerLimit();
		double upperLimit = displayStatistics.getUpperLimit();

		String result;
		if (value < lowerLimit)
		{
			result = "Significant (non-colocated)";
		}
		else if (value > upperLimit)
		{
			result = "Significant (colocated)";
		}
		else
		{
			result = "Not significant";
		}

		addField(sb, IJ.d2s(value, 4));
		addField(sb, IJ.d2s(av, 4));
		addField(sb, IJ.d2s(sd, 4));
		addField(sb, IJ.d2s(lowerLimit, 4)).append(" : ").append(IJ.d2s(upperLimit, 4));
		addField(sb, result);
	}

	@SuppressWarnings("unused")
	private void showImage(String title, ImageProcessor ip)
	{
		ImagePlus img = new ImagePlus(title, ip);
		img.show();
		IJ.showMessage(title);
		try
		{
			Thread.sleep(100);
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		img.close();
	}

	private boolean parametersReady()
	{
		if (WindowManager.getImageCount() == 0)
		{
			// IJ.showMessage(FRAME_TITLE, "No images opened.");
			return false;
		}

		if (channel1List.getItemCount() < 2)
		{
			// Check for an image stack with more than one channel/frame
			ImagePlus imp = WindowManager.getImage(channel1List.getSelectedItem());
			if (imp == null || (imp.getNChannels() + imp.getNFrames() < 3))
			{
				IJ.showMessage(FRAME_TITLE,
						"Requires 2 images or a multi-channel/frame stack. Images must be 8-bit or 16-bit grayscale.");
				return false;
			}
		}

		maximumRadius = getIntValue(maximumRadiusText.getText(), 0);
		randomRadius = getIntValue(randomRadiusText.getText(), 0);

		if (randomRadius >= maximumRadius)
		{
			IJ.showMessage(FRAME_TITLE, "Require '" + choiceRandomRadius + "' < '" + choiceMaximumRadius + "'");
			return false;
		}

		return ((channel1List.getSelectedIndex() != -1) && (channel2List.getSelectedIndex() != -1) &&
				(segmented1List.getSelectedIndex() != -1) && (segmented2List.getSelectedIndex() != -1) &&
				(confinedList.getSelectedIndex() != -1) && (channel1List.getItemCount() != 0) &&
				(channel2List.getItemCount() != 0) && (segmented1List.getItemCount() != 0) &&
				(segmented2List.getItemCount() != 0) && (confinedList.getItemCount() != 0));
	}

	public void fillImagesList()
	{
		// Find the currently open images
		ArrayList<String> newImageList = createImageList();

		// Check if the image list has changed
		if (imageList.equals(newImageList))
			return;

		imageList = newImageList;

		// Repopulate the image lists
		channel1List.removeAll();
		channel2List.removeAll();
		segmented1List.removeAll();
		segmented2List.removeAll();
		confinedList.removeAll();

		segmented1List.add(CHANNEL_IMAGE);
		segmented2List.add(CHANNEL_IMAGE);

		for (String imageTitle : newImageList)
		{
			segmented1List.add(imageTitle);
			segmented2List.add(imageTitle);
			confinedList.add(imageTitle);
			channel1List.add(imageTitle);
			channel2List.add(imageTitle);
		}

		// Ensure the drop-downs are resized
		pack();

		// Restore previous selection
		if ((channel1Index < channel1List.getItemCount()) && (channel1Index >= 0))
			channel1List.select(channel1Index);
		if ((channel2Index < channel2List.getItemCount()) && (channel2Index >= 0))
			channel2List.select(channel2Index);
		if ((segmented1Index < segmented1List.getItemCount()) && (segmented1Index >= 0))
			segmented1List.select(segmented1Index);
		if ((segmented2Index < segmented2List.getItemCount()) && (segmented2Index >= 0))
			segmented2List.select(segmented2Index);
		if ((confinedIndex < confinedList.getItemCount()) && (confinedIndex >= 0))
			confinedList.select(confinedIndex);
	}

	public ArrayList<String> createImageList()
	{
		ArrayList<String> newImageList = new ArrayList<String>();

		for (int id : gdsc.utils.ImageJHelper.getIDList())
		{
			ImagePlus imp = WindowManager.getImage(id);

			// Image must be 8-bit/16-bit
			if (imp != null && (imp.getType() == ImagePlus.GRAY8 || imp.getType() == ImagePlus.GRAY16))
			{
				// Check it is not one the result images
				String imageTitle = imp.getTitle();
				if ((imageTitle.equals(channel1RGBTitle)) || (imageTitle.equals(channel2RGBTitle)) ||
						(imageTitle.equals(segmented1RGBTitle)) || (imageTitle.equals(segmented2RGBTitle)) ||
						(imageTitle.equals(plotM1Title)) || (imageTitle.equals(plotM2Title)) ||
						(imageTitle.equals(m1HistogramTitle)) || (imageTitle.equals(m2HistogramTitle)) ||
						(imageTitle.equals(mergedChannelTitle)) || (imageTitle.equals(mergedSegmentedTitle)) ||
						(imageTitle.equals(lastShiftedSegmentedTitle)) || (imageTitle.equals(lastShiftedChannelTitle)))
					continue;

				newImageList.add(imageTitle);
			}
		}
		return newImageList;
	}

	private void createFrame()
	{
		Panel mainPanel = new Panel();
		GridLayout mainGrid = new GridLayout(0, 1);
		mainGrid.setHgap(10);
		mainGrid.setVgap(10);
		mainPanel.setLayout(mainGrid);
		add(mainPanel);

		pixelsLabel = new Label(pixelsUnitString, 0);
		pixelsLabel.setFont(monoFont);

		mainPanel.add(createLabelPanel(null, "Channel/frame options for stacks pop-up at run-time", null));

		channel1List = new Choice();
		mainPanel.add(createChoicePanel(channel1List, choiceChannel1));

		channel2List = new Choice();
		mainPanel.add(createChoicePanel(channel2List, choiceChannel2));

		segmented1List = new Choice();
		segmented1Option = new Choice();
		mainPanel.add(createRoiChoicePanel(segmented1List, segmented1Option, choiceSegmentedChannel1,
				segmented1OptionIndex));

		segmented2List = new Choice();
		segmented2Option = new Choice();
		mainPanel.add(createRoiChoicePanel(segmented2List, segmented2Option, choiceSegmentedChannel2,
				segmented2OptionIndex));

		confinedList = new Choice();
		confinedOption = new Choice();
		mainPanel.add(createRoiChoicePanel(confinedList, confinedOption, choiceConfined, confinedOptionIndex));

		expandConfinedCheckbox = new Checkbox();
		mainPanel.add(createCheckboxPanel(expandConfinedCheckbox, choiceExpandConfined, expandConfinedCompartment));

		maximumRadiusText = new TextField();
		mainPanel.add(createTextPanel(maximumRadiusText, choiceMaximumRadius, "" + maximumRadius));
		maximumRadiusText.addKeyListener(this);

		randomRadiusText = new TextField();
		mainPanel.add(createTextPanel(randomRadiusText, choiceRandomRadius, "" + randomRadius));
		randomRadiusText.addKeyListener(this);

		subRandomSamplesCheckbox = new Checkbox();
		mainPanel.add(createCheckboxPanel(subRandomSamplesCheckbox, choiceSubRandomSamples, subRandomSamples));
		subRandomSamplesCheckbox.addItemListener(this);

		numberOfSamplesField = new Label();
		mainPanel.add(createLabelPanel(numberOfSamplesField, numberOfSamplesLabel, ""));
		updateNumberOfSamples();

		binsText = new TextField();
		mainPanel.add(createTextPanel(binsText, choiceBinsNumber, "" + histogramBins));

		closeWindowsOnExitCheckbox = new Checkbox();
		mainPanel.add(createCheckboxPanel(closeWindowsOnExitCheckbox, choiceCloseWindowsOnExit, closeWindowsOnExit));

		setResultsOptionsCheckbox = new Checkbox();
		mainPanel.add(createCheckboxPanel(setResultsOptionsCheckbox, choiceSetOptions, false));
		setResultsOptionsCheckbox.addItemListener(this);

		okButton = new Button(okButtonLabel);
		okButton.addActionListener(this);
		helpButton = new Button(helpButtonLabel);
		helpButton.addActionListener(this);

		JPanel buttonPanel = new JPanel();
		FlowLayout l = new FlowLayout();
		l.setVgap(0);
		buttonPanel.setLayout(l);
		buttonPanel.add(okButton, BorderLayout.CENTER);
		buttonPanel.add(helpButton, BorderLayout.CENTER);

		mainPanel.add(buttonPanel);
	}

	private void updateNumberOfSamples()
	{
		maximumRadius = getIntValue(maximumRadiusText.getText(), 0);
		randomRadius = getIntValue(randomRadiusText.getText(), 0);
		subRandomSamples = subRandomSamplesCheckbox.getState();

		if (randomRadius >= maximumRadius)
		{
			randomRadiusText.setBackground(Color.ORANGE);
		}
		else
		{
			randomRadiusText.setBackground(Color.WHITE);
		}

		double subNumber = approximateSamples(randomRadius);
		double number = approximateSamples(maximumRadius) - subNumber;

		if (permutations > 0)
		{
			number = Math.min(permutations, number);
			subNumber = Math.min(permutations, subNumber);
		}

		double total = ((subRandomSamples) ? subNumber : 0) + number;
		if (total < 0)
			total = 0;
		numberOfSamplesField.setText("" + (long) total);
	}

	private double approximateSamples(int radius)
	{
		return Math.PI * radius * radius;
	}

	private Panel createChoicePanel(Choice list, String label)
	{
		Panel panel = new Panel();
		panel.setLayout(new BorderLayout());
		Label listLabel = new Label(label, 0);
		listLabel.setFont(monoFont);
		list.setSize(fontWidth * 3, fontWidth);
		panel.add(listLabel, BorderLayout.WEST);
		panel.add(list, BorderLayout.CENTER);
		return panel;
	}

	private Panel createRoiChoicePanel(Choice imageList, Choice optionList, String label, int selectedOptionIndex)
	{
		Panel panel = new Panel();
		panel.setLayout(new BorderLayout());
		Label listLabel = new Label(label, 0);
		listLabel.setFont(monoFont);
		// imageList.setSize(fontWidth * 3, fontWidth);
		panel.add(listLabel, BorderLayout.WEST);
		panel.add(optionList, BorderLayout.CENTER);
		panel.add(imageList, BorderLayout.EAST);
		optionList.add(OPTION_NONE);
		optionList.add(OPTION_MIN_VALUE);
		optionList.add(OPTION_USE_ROI);
		optionList.add(OPTION_MASK);
		imageList.add(CHANNEL_IMAGE);

		if (selectedOptionIndex < 4 && selectedOptionIndex >= 0)
		{
			optionList.select(selectedOptionIndex);
		}

		return panel;
	}

	private Panel createTextPanel(TextField textField, String label, String value)
	{
		Panel panel = new Panel();
		panel.setLayout(new BorderLayout());
		Label listLabel = new Label(label, 0);
		listLabel.setFont(monoFont);
		textField.setSize(fontWidth * 3, fontWidth);
		textField.setText(value);
		panel.add(listLabel, BorderLayout.WEST);
		panel.add(textField, BorderLayout.CENTER);
		return panel;
	}

	private Panel createCheckboxPanel(Checkbox checkbox, String label, boolean state)
	{
		Panel panel = new Panel();
		panel.setLayout(new BorderLayout());
		Label listLabel = new Label(label, 0);
		listLabel.setFont(monoFont);
		checkbox.setState(state);
		panel.add(listLabel, BorderLayout.WEST);
		panel.add(checkbox, BorderLayout.EAST);
		return panel;
	}

	private Panel createLabelPanel(Label labelField, String label, String value)
	{
		Panel panel = new Panel();
		panel.setLayout(new BorderLayout());
		Label listLabel = new Label(label, 0);
		listLabel.setFont(monoFont);
		panel.add(listLabel, BorderLayout.WEST);
		if (labelField != null)
		{
			labelField.setSize(fontWidth * 3, fontWidth);
			labelField.setText(value);
			panel.add(labelField, BorderLayout.CENTER);
		}
		return panel;
	}

	/**
	 * Create the intersect of the two masks. Results are written back to the image processor in the target stack unless
	 * the duplicate option is true. The sum of the source image intensity within the intersect is accumulated.
	 */
	private ImageStack intersectMask(ImageStack targetStack, ImageStack sourceStack, long[] sum, boolean duplicate)
	{
		ImageStack newStack = new ImageStack(targetStack.getWidth(), targetStack.getHeight());
		sum[0] = 0;
		for (int s = 1; s <= targetStack.getSize(); s++)
		{
			ImageProcessor ip = targetStack.getProcessor(s);
			if (duplicate)
				ip = ip.duplicate();
			sum[0] += intersectMask(ip, (ByteProcessor) sourceStack.getProcessor(s), ip);
			newStack.addSlice(null, ip);
		}
		return newStack;
	}

	/**
	 * Create the intersect of the two masks. Results are written back to the output image processor if specified.
	 * 
	 * @return The sum of the source image intensity within the intersect
	 */
	private long intersectMask(ImageProcessor sourceImage, ByteProcessor maskImage, ImageProcessor outputImage)
	{
		long sum = 0;

		// We need to do extra work if there is an output image so put this in a different loop
		if (outputImage != null)
		{
			int sourceIntensity;
			for (int i = maskImage.getPixelCount(); i-- > 0;)
			{
				if (maskImage.get(i) != 0)
				{
					sourceIntensity = sourceImage.get(i);
					outputImage.set(i, sourceIntensity);
					sum += sourceIntensity;
				}
				else
				{
					outputImage.set(i, 0);
				}
			}
		}
		else
		{
			for (int i = maskImage.getPixelCount(); i-- > 0;)
			{
				if (maskImage.get(i) != 0)
				{
					sum += sourceImage.get(i);
				}
			}
		}

		return sum;
	}

	/**
	 * Modify mask1 to include all non-zero pixels from mask 2
	 * 
	 * @param mask1
	 * @param mask2
	 */
	private void unionMask(ImageStack mask1, ImageStack mask2)
	{
		for (int s = 1; s <= mask1.getSize(); s++)
		{
			unionMask((ByteProcessor) mask1.getProcessor(s), (ByteProcessor) mask2.getProcessor(s));
		}
	}

	/**
	 * Modify mask1 to include all non-zero pixels from mask 2
	 * 
	 * @param mask1
	 * @param mask2
	 */
	private void unionMask(ByteProcessor mask1, ByteProcessor mask2)
	{
		for (int i = mask1.getPixelCount(); i-- > 0;)
		{
			if (mask2.get(i) != 0)
			{
				mask1.set(i, 255);
			}
		}
	}

	private void saveOptions()
	{
		Prefs.set(OPT_CHANNEL1_INDEX, channel1Index);
		Prefs.set(OPT_CHANNEL2_INDEX, channel2Index);
		Prefs.set(OPT_SEGMENTED1_INDEX, segmented1Index);
		Prefs.set(OPT_SEGMENTED2_INDEX, segmented2Index);
		Prefs.set(OPT_CONFINED_INDEX, confinedIndex);
		Prefs.set(OPT_SEGMENTED1_OPTION_INDEX, segmented1OptionIndex);
		Prefs.set(OPT_SEGMENTED2_OPTION_INDEX, segmented2OptionIndex);
		Prefs.set(OPT_CONFINED_OPTION_INDEX, confinedOptionIndex);
		Prefs.set(OPT_EXPAND_CONFINED, expandConfinedCompartment);
		Prefs.set(OPT_MAXIMUM_RADIUS, maximumRadius);
		Prefs.set(OPT_RANDOM_RADIUS, randomRadius);
		Prefs.set(OPT_SUB_RANDOM_SAMPLES, subRandomSamples);
		Prefs.set(OPT_HISTOGRAM_BINS, histogramBins);
		Prefs.set(OPT_CLOSE_WINDOWS_ON_EXIT, closeWindowsOnExit);
		Prefs.set(OPT_SET_OPTIONS, setOptions);

		Prefs.set(OPT_SHOW_CHANNEL1_RGB, showChannel1RGB);
		Prefs.set(OPT_SHOW_CHANNEL2_RGB, showChannel2RGB);
		Prefs.set(OPT_SHOW_SEGMENTED1_RGB, showSegmented1RGB);
		Prefs.set(OPT_SHOW_SEGMENTED2_RGB, showSegmented2RGB);
		Prefs.set(OPT_SHOW_MERGED_CHANNEL_RGB, showMergedChannelRGB);
		Prefs.set(OPT_SHOW_MERGED_SEGMENTED_RGB, showMergedSegmentedRGB);
		Prefs.set(OPT_SHOW_MERGED_CHANNEL_DISPLACEMENT_RGB, showMergedChannelDisplacementRGB);
		Prefs.set(OPT_SHOW_MERGED_SEGMENTED_DISPLACEMENT_RGB, showMergedSegmentedDisplacementRGB);
		Prefs.set(OPT_SHOW_M1_PLOT_WINDOW, showM1PlotWindow);
		Prefs.set(OPT_SHOW_M2_PLOT_WINDOW, showM2PlotWindow);
		Prefs.set(OPT_SHOW_M1_STATISTICS, showM1Statistics);
		Prefs.set(OPT_SHOW_M2_STATISTICS, showM2Statistics);
		Prefs.set(OPT_SHOW_R_STATISTICS, showRStatistics);
		Prefs.set(OPT_SAVE_RESULTS, saveResults);
		Prefs.set(OPT_RESULTS_DIRECTORY, resultsDirectory);
		Prefs.set(OPT_P_VALUE, pValue);
		Prefs.set(OPT_PERMUTATIONS, permutations);
	}

	/**
	 * Run using an ImageJ generic dialog to allow recording in macros
	 */
	private void runAsPlugin()
	{
		imageList = createImageList();
		if (imageList.size() < 2)
		{
			// Check for an image stack with more than one channel/frame
			ImagePlus imp = (imageList.isEmpty()) ? null : WindowManager.getImage(imageList.get(0));
			if (imp == null || (imp.getNChannels() + imp.getNFrames() < 3))
			{
				IJ.showMessage(FRAME_TITLE,
						"Requires 2 images or a multi-channel/frame stack. Images must be 8-bit or 16-bit grayscale.");
			}
		}

		String[] images = new String[imageList.size()];
		String[] images2 = new String[imageList.size() + 1];
		images2[0] = CHANNEL_IMAGE;
		for (int i = 0; i < imageList.size(); i++)
		{
			images[i] = images2[i + 1] = imageList.get(i);
		}

		GenericDialog gd = new GenericDialog(FRAME_TITLE);

		gd.addChoice(choiceChannel1.replace(" ", "_"), images, getTitle(images, channel1Index));
		gd.addChoice(choiceChannel2.replace(" ", "_"), images, getTitle(images, channel2Index));
		// ROIs require two choice boxes
		addRoiChoice(gd, choiceSegmentedChannel1, ROI_OPTIONS, images2, segmented1OptionIndex, segmented1Index);
		addRoiChoice(gd, choiceSegmentedChannel2, ROI_OPTIONS, images2, segmented2OptionIndex, segmented2Index);
		addRoiChoice(gd, choiceConfined, ROI_OPTIONS, images, confinedOptionIndex, confinedIndex);

		gd.addCheckbox(choiceExpandConfined.trim().replace(" ", "_"), expandConfinedCompartment);
		gd.addNumericField(choiceMaximumRadius, maximumRadius, 0);
		gd.addNumericField(choiceRandomRadius, randomRadius, 0);
		gd.addCheckbox(choiceSubRandomSamples.trim().replace(" ", "_"), subRandomSamples);
		gd.addNumericField(choiceBinsNumber, histogramBins, 0);
		gd.addCheckbox(choiceSetOptions.replace(" ", "_"), setOptions);

		gd.showDialog();

		if (gd.wasCanceled())
			return;

		channel1Index = gd.getNextChoiceIndex();
		channel2Index = gd.getNextChoiceIndex();
		segmented1OptionIndex = gd.getNextChoiceIndex();
		segmented1Index = gd.getNextChoiceIndex();
		segmented2OptionIndex = gd.getNextChoiceIndex();
		segmented2Index = gd.getNextChoiceIndex();
		confinedOptionIndex = gd.getNextChoiceIndex();
		confinedIndex = gd.getNextChoiceIndex();
		expandConfinedCompartment = gd.getNextBoolean();
		maximumRadius = (int) gd.getNextNumber();
		randomRadius = (int) gd.getNextNumber();
		subRandomSamples = gd.getNextBoolean();
		histogramBins = (int) gd.getNextNumber();

		if ((setOptions = gd.getNextBoolean()))
			setResultsOptions();

		// Get the images
		ImagePlus imp1 = WindowManager.getImage(images[channel1Index]);
		ImagePlus imp2 = WindowManager.getImage(images[channel2Index]);
		ImagePlus roi1 = WindowManager.getImage(images2[segmented1Index]);
		ImagePlus roi2 = WindowManager.getImage(images2[segmented2Index]);
		ImagePlus roi = WindowManager.getImage(images[confinedIndex]);

		runCDA(imp1, imp2, roi1, roi2, roi);
	}

	private void addRoiChoice(GenericDialog gd, String title, String[] roiOptions, String[] images, int optionIndex,
			int index)
	{
		String name = title.replace(" ", "_");
		gd.addChoice(name, roiOptions, getTitle(roiOptions, optionIndex));
		gd.addChoice(name + "_image", images, getTitle(images, index));
	}

	private String getTitle(String[] titles, int index)
	{
		if (index < titles.length)
			return titles[index];
		return "";
	}
}
