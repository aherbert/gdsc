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
package uk.ac.sussex.gdsc.colocalisation.cda;

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

import org.apache.commons.math3.random.Well19937c;

import gnu.trove.list.array.TIntArrayList;
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
import ij.process.LUT;
import ij.process.ShortProcessor;
import ij.text.TextWindow;
import ij.util.Tools;
import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.colocalisation.cda.engine.CDAEngine;
import uk.ac.sussex.gdsc.colocalisation.cda.engine.CalculationResult;
import uk.ac.sussex.gdsc.core.ij.Utils;
import uk.ac.sussex.gdsc.core.ij.process.LUTHelper;
import uk.ac.sussex.gdsc.core.ij.process.LUTHelper.LutColour;
import uk.ac.sussex.gdsc.core.utils.Random;
import uk.ac.sussex.gdsc.core.utils.StoredData;

/**
 * Test for significant colocalisation within images using the Confined Displacement Algorithm (CDA).
 *
 * Colocalisation of two channels is measured using Mander's coefficients (M1 &amp; M2) and Pearson's correlation
 * coefficient (R). Significance is tested by shifting one image to all positions within a circular radius and computing
 * a probability distribution for M1, M2 and R. Significance is measured using a specified p-value.
 *
 * Supports stacks. Only a specific channel and time frame can be used and all input images must have the same number
 * of z-sections for the chosen channel/time frame.
 * <p>
 * This class is based on the original CDA_Plugin developed by Maria Osorio-Reich:
 * <a href=
 * "http://imagejdocu.tudor.lu/doku.php?id=plugin:analysis:confined_displacement_algorithm_determines_true_and_random_colocalization_:start">Confined
 * Displacement Algorithm Determines True and Random Colocalization</a>
 * 
 * @see <a href="https://doi.org/10.1111/j.1365-2818.2010.03369.x">Confined displacement algorithm determines true and
 *      random colocalization in fluorescence microscopy</a>
 */
public class CDA_Plugin extends PlugInFrame implements ActionListener, ItemListener, KeyListener, Runnable
{
    private static final long serialVersionUID = 1L;
    private static String TITLE = "CDA Plugin";
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
    // Store the display range min for each ROI
    private final int[] displayMin = new int[3];

    // Stores the list of images last used in the selection options
    private ArrayList<String> imageList = new ArrayList<>();

    /**
     * Instantiates a new CD A plugin.
     */
    public CDA_Plugin()
    {
        super(TITLE);
    }

    /*
     * (non-Javadoc)
     *
     * @see ij.plugin.frame.PlugInFrame#run(java.lang.String)
     */
    @Override
    public void run(String arg)
    {
        UsageTracker.recordPlugin(this.getClass(), arg);

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
            if (!(instance.getTitle().equals(getTitle())))
            {
                final CDA_Plugin cda = (CDA_Plugin) instance;
                Prefs.saveLocation(OPT_LOCATION, cda.getLocation());
                cda.close();
            }
            else
            {
                instance.toFront();
                return;
            }

        instance = this;
        IJ.register(CDA_Plugin.class);
        WindowManager.addWindow(this);

        createFrame();
        setup();

        addKeyListener(IJ.getInstance());
        pack();
        final Point loc = Prefs.getLocation(OPT_LOCATION);
        if (loc != null)
            setLocation(loc);
        else
            GUI.center(this);
        if (IJ.isMacOSX())
            setResizable(false);
        setVisible(true);
    }

    private void setup()
    {
        final ImagePlus imp = WindowManager.getCurrentImage();
        if (imp == null)
            return;
        fillImagesList();
    }

    @SuppressWarnings("unused")
    @Override
    public synchronized void actionPerformed(ActionEvent e)
    {
        final Object actioner = e.getSource();

        if (actioner == null)
            return;

        if (((Button) actioner == okButton) && (parametersReady()))
        {
            final Thread thread = new Thread(this, "CDA_Plugin");
            thread.start();
        }
        if ((Button) actioner == helpButton)
        {
            final String macro = "run('URL...', 'url=" + uk.ac.sussex.gdsc.help.URL.COLOCALISATION + "');";
            new MacroRunner(macro);
        }

        super.notify();
    }

    @Override
    public void itemStateChanged(ItemEvent e)
    {
        final Object actioner = e.getSource();

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

    private void setResultsOptions()
    {
        final GenericDialog gd = new GenericDialog("Set Results Options");

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

    @Override
    public void keyTyped(KeyEvent e)
    {
        // Do nothing
    }

    @Override
    public void keyPressed(KeyEvent e)
    {
        // Do nothing
    }

    @Override
    public void keyReleased(KeyEvent e)
    {
        final Object actioner = e.getSource();

        if (actioner == null)
            return;

        // Update the number of combinations
        updateNumberOfSamples();
    }

    @Override
    public void windowClosing(WindowEvent e)
    {
        closeWindowsOnExit = closeWindowsOnExitCheckbox.getState();
        Prefs.set(OPT_CLOSE_WINDOWS_ON_EXIT, closeWindowsOnExit);

        Prefs.saveLocation(OPT_LOCATION, getLocation());

        close();
    }

    @Override
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
                tw.close();
        }

        instance = null;
        super.close();
    }

    private static void closeImagePlus(ImagePlus w)
    {
        if (w != null && w.isVisible())
            w.close();
    }

    private static void closePlotWindow(PlotWindow w, String locationKey)
    {
        if (w != null && !w.isClosed())
        {
            Prefs.saveLocation(locationKey, w.getLocation());
            w.close();
        }
    }

    private static void closeDisplayStatistics(DisplayStatistics w, String locationKey)
    {
        if (w != null && w.getPlotWindow() != null)
            if (w.getPlotWindow().isShowing())
            {
                Prefs.saveLocation(locationKey, w.getPlotWindow().getLocation());
                w.close();
            }
    }

    @Override
    public void windowActivated(WindowEvent e)
    {
        super.windowActivated(e);

        fillImagesList();
        WindowManager.setWindow(this);
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Runnable#run()
     */
    @Override
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
            return;

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

        final ImagePlus imp1 = WindowManager.getImage(channel1List.getSelectedItem());
        final ImagePlus imp2 = WindowManager.getImage(channel2List.getSelectedItem());
        final ImagePlus roi1 = getRoiSource(imp1, segmented1List, segmented1Option);
        final ImagePlus roi2 = getRoiSource(imp2, segmented2List, segmented2Option);
        final ImagePlus roi = getRoiSource(null, confinedList, confinedOption);

        runCDA(imp1, imp2, roi1, roi2, roi);
    }

    private void runCDA(ImagePlus imp1, ImagePlus imp2, ImagePlus roi1, ImagePlus roi2, ImagePlus roi)
    {
        // Check for image stacks and get the channel and frame (if applicable)
        if (!getStackOptions(imp1, imp2, roi1, roi2, roi))
            return;

        // TODO - Should the select channel/frames be saved (i.e. the slice options array)?
        saveOptions();

        final long startTime = System.currentTimeMillis();

        // Extract images
        final ImageStack imageStack1 = getStack(imp1, 0);
        final ImageStack imageStack2 = getStack(imp2, 2);

        // Get the ROIs
        final ImageStack roiStack1 = createROI(imp1, roi1, 4, segmented1OptionIndex);
        final ImageStack roiStack2 = createROI(imp2, roi2, 6, segmented2OptionIndex);
        final ImageStack confinedStack = createROI(imp1, roi, 8, confinedOptionIndex);

        // The images and the masks should be the same size.
        if (!checkDimensions(imageStack1, imageStack2, roiStack1, roiStack2, confinedStack))
            return;

        // Check inputs
        if (roiStack1 == null || roiStack2 == null || confinedStack == null)
        {
            IJ.showMessage(TITLE, "ROIs must be at the same width and height of the input images");
            return;
        }

        if (isZero(roiStack1) || isZero(roiStack2) || isZero(confinedStack))
        {
            IJ.showMessage(TITLE, "Empty ROI(s)");
            return;
        }

        if (expandConfinedCompartment)
        {
            // Expand the confined compartment to include all of the ROIs
            unionMask(confinedStack, roiStack1);
            unionMask(confinedStack, roiStack2);
        }
        else
        {
            // Restrict the ROIs to the confined compartment
            intersectMask(roiStack1, confinedStack);
            intersectMask(roiStack2, confinedStack);

            // This could be changed to check for a certain pixel count (e.g. (overlap / 255) < [count])
            if (isZero(roiStack1) || isZero(roiStack2))
            {
                IJ.showMessage(TITLE, "Empty ROI(s)");
                return;
            }
        }
        intersectMask(imageStack1, confinedStack);
        intersectMask(imageStack2, confinedStack);

        // Debug - show all the input
        //Utils.display("CDA Stack1", imageStack1);
        //Utils.display("CDA Stack2", imageStack2);
        //Utils.display("CDA ROI1", roiStack1);
        //Utils.display("CDA ROI2", roiStack2);
        //Utils.display("CDA Confined", confinedStack);

        IJ.showStatus(calculatingFirstMandersStatus);

        // Pre-calculate constants
        final double denom1 = intersectSum(imageStack1, roiStack1);
        final double denom2 = intersectSum(imageStack2, roiStack2);

        //System.out.printf("CDA total = %s (%d)  %s (%d) : %d)\n", denom1, intersectSum(roiStack1, roiStack1), denom2,
        //		intersectSum(roiStack2, roiStack2), intersectSum(confinedStack, confinedStack));

        IJ.showStatus(mandersCalculationStatus);

        // Build list of shifts above and below the random radius
        final int[] shiftIndices = buildShiftIndices(subRandomSamples, randomRadius, maximumRadius);

        final List<CalculationResult> results = calculateResults(imageStack1, roiStack1, confinedStack, imageStack2,
                roiStack2, denom1, denom2, shiftIndices);

        if (Utils.isInterrupted())
            return;

        // Get unshifted result
        CalculationResult unshifted = null;
        for (final CalculationResult r : results)
            if (r.distance == 0)
            {
                unshifted = r;
                break;
            }

        // Display an image of the largest shift
        ImageStack lastChannelShiftedRawStack = null;
        ImageStack lastSegmentedShiftedRawStack = null;
        if (showMergedChannelDisplacementRGB || showMergedSegmentedDisplacementRGB)
        {
            final TwinStackShifter twinImageShifter = new TwinStackShifter(imageStack1, roiStack1, confinedStack);
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
        final int[] shiftIndices1 = getRandomShiftIndices(randomRadius, maximumRadius, permutations);
        final int[] shiftIndices2 = (subRandomSamples) ? getRandomShiftIndices(0, randomRadius, permutations)
                : new int[0];
        final int[] shiftIndices = merge(shiftIndices1, shiftIndices2);
        return shiftIndices;
    }

    /**
     * Generate random shift indices between the minimum and maximum radius.
     * <p>
     * The indices are packed as unsigned bytes in the first 2 bytes of the integer. The signs are packed in the next 2
     * bits:
     *
     * <pre>
     * {
     *     &#64;code
     *     int index = (Math.abs(i) & 0xff) << 8 | Math.abs(j) & 0xff;
     *     if (i < 0)
     *         index |= 0x00010000;
     *     if (j < 0)
     *         index |= 0x00020000;
     * }
     * </pre>
     *
     * <p>
     * This supports shifts up to +/-256 pixels.
     *
     * @param minimumRadius
     *            the minimum radius (range 0-256)
     * @param maximumRadius
     *            the maximum radius (range 0-256)
     * @param permutations
     *            the permutations
     * @return the random shift indices
     */
    public static int[] getRandomShiftIndices(int minimumRadius, int maximumRadius, int permutations)
    {
        if (minimumRadius < 0 || minimumRadius > 256)
            throw new IllegalArgumentException("Shifts of 0-256 are supported");
        if (maximumRadius < 0 || maximumRadius > 256)
            throw new IllegalArgumentException("Shifts of 0-256 are supported");

        // Count the number of permutations
        final int maximumRadius2 = maximumRadius * maximumRadius;
        final int minimumRadius2 = minimumRadius * minimumRadius;
        final TIntArrayList list = new TIntArrayList(512);
        for (int i = -maximumRadius; i <= maximumRadius; ++i)
            for (int j = -maximumRadius; j <= maximumRadius; ++j)
            {
                // Int is big enough to hold 256^2 * 2
                final int distance2 = i * i + j * j;
                if (distance2 > maximumRadius2 || distance2 <= minimumRadius2)
                    continue;
                // Pack the magnitude of the shift into the first 2 bytes and then pack the signs.
                int index = (Math.abs(i) & 0xff) << 8 | Math.abs(j) & 0xff;
                if (i < 0)
                    index |= 0x00010000;
                if (j < 0)
                    index |= 0x00020000;
                list.add(index);
            }

        // Randomise the permutations
        if (permutations < list.size() && permutations > 0)
        {
            final int[] sample = Random.sample(permutations, list.size(), new Well19937c(30051977));
            final int[] indices = new int[permutations];
            for (int i = 0; i < permutations; i++)
                indices[i] = list.getQuick(sample[i]);
            return indices;
        }

        // No sub-selection
        return list.toArray();
    }

    /**
     * Gets the x shift from the packed index.
     *
     * @param index
     *            the index
     * @return the x shift
     */
    public static int getXShift(int index)
    {
        int i = (index & 0xff00) >> 8;
        if ((index & 0x00010000) == 0x00010000)
            i = -i;
        return i;
    }

    /**
     * Gets the y shift from the packed index.
     *
     * @param index
     *            the index
     * @return the y shift
     */
    public static int getYShift(int index)
    {
        int j = index & 0xff;
        if ((index & 0x00020000) == 0x00020000)
            j = -j;
        return j;
    }

    private static int[] merge(int[] shiftIndices1, int[] shiftIndices2)
    {
        // Include an extra entry for zero shift
        final int[] indices = new int[shiftIndices1.length + shiftIndices2.length + 1];
        int j = 0;
        for (final int index : shiftIndices1)
            indices[j++] = index;
        for (final int index : shiftIndices2)
            indices[j++] = index;
        return indices;
    }

    private static List<CalculationResult> calculateResults(ImageStack imageStack1, ImageStack roiStack1,
            ImageStack confinedStack, ImageStack imageStack2, ImageStack roiStack2, double denom1, double denom2,
            int[] shiftIndices)
    {
        // Initialise the progress count
        final int totalSteps = shiftIndices.length;
        IJ.showStatus("Creating CDA Engine ...");

        final List<CalculationResult> results = new ArrayList<>(totalSteps);

        final int threads = Prefs.getThreads();
        final CDAEngine engine = new CDAEngine(imageStack1, roiStack1, confinedStack, imageStack2, roiStack2, denom1,
                denom2, results, totalSteps, threads);
        // Wait for initialisation
        engine.isInitialised();

        IJ.showProgress(0);
        IJ.showStatus("Computing shifts ...");

        for (int n = 0; n < shiftIndices.length; n++)
        {
            final int index = shiftIndices[n];
            final int x = CDA_Plugin.getXShift(index);
            final int y = CDA_Plugin.getYShift(index);

            engine.run(n, x, y);

            if (Utils.isInterrupted())
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

    private static boolean checkDimensions(ImageStack imageStack1, ImageStack imageStack2, ImageStack roiStack1,
            ImageStack roiStack2, ImageStack confinedStack)
    {
        final int w = imageStack1.getWidth();
        final int h = imageStack1.getHeight();
        final int s = imageStack1.getSize();

        if (!checkDimensions(w, h, s, imageStack2))
            return false;
        if (!checkDimensions(w, h, s, roiStack1))
            return false;
        if (!checkDimensions(w, h, s, roiStack2))
            return false;
        if (!checkDimensions(w, h, s, confinedStack))
            return false;
        return true;
    }

    private static boolean checkDimensions(int w, int h, int s, ImageStack stack)
    {
        if (stack != null && (w != stack.getWidth() || h != stack.getHeight() || s != stack.getSize()))
        {
            IJ.showMessage(TITLE, "Images must be the same width and height and depth");
            return false;
        }
        return true;
    }

    private static ImagePlus getRoiSource(ImagePlus imp, Choice imageList, Choice optionList)
    {
        // Get the ROI option
        if (optionList.getSelectedItem().equals(OPTION_NONE))
            // No ROI image
            return null;

        // Find the image in the image list
        if (imageList.getSelectedItem().equals(CHANNEL_IMAGE))
            // Channel image is the source for the ROI
            return imp;

        return WindowManager.getImage(imageList.getSelectedItem());
    }

    private static boolean getStackOptions(ImagePlus imp1, ImagePlus imp2, ImagePlus roi1, ImagePlus roi2,
            ImagePlus roi)
    {
        if (isStack(imp1) || isStack(imp1) || isStack(roi1) || isStack(roi2) || isStack(roi))
        {
            final GenericDialog gd = new GenericDialog("Slice options");
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

    private static boolean isStack(ImagePlus imp)
    {
        return (imp != null && imp.getStackSize() > 1);
    }

    private static boolean addOptions(GenericDialog gd, ImagePlus imp, String title, int offset)
    {
        boolean added = false;
        if (imp != null)
        {
            final String[] channels = getChannels(imp);
            final String[] frames = getFrames(imp);

            if (channels.length > 1 || frames.length > 1)
                added = true;
            //gd.addMessage(title);

            setOption(gd, channels, title + "_Channel", offset);
            setOption(gd, frames, title + "_Frame", offset + 1);
        }
        return added;
    }

    private static boolean setOption(GenericDialog gd, String[] choices, String title, int offset)
    {
        if (choices.length > 1)
        {
            // Restore previous selection
            final int c = (sliceOptions[offset] > 0 && sliceOptions[offset] <= choices.length)
                    ? sliceOptions[offset] - 1
                    : 0;
            gd.addChoice(title, choices, choices[c]);
            return true;
        }
        // Set to default
        sliceOptions[offset] = 1;
        return false;
    }

    private static String[] getChannels(ImagePlus imp)
    {
        final int c = imp.getNChannels();
        final String[] result = new String[c];
        for (int i = 0; i < c; i++)
            result[i] = Integer.toString(i + 1);
        return result;
    }

    private static String[] getFrames(ImagePlus imp)
    {
        final int c = imp.getNFrames();
        final String[] result = new String[c];
        for (int i = 0; i < c; i++)
            result[i] = Integer.toString(i + 1);
        return result;
    }

    private static void getOptions(GenericDialog gd, ImagePlus imp, int offset)
    {
        if (imp != null)
        {
            final String[] channels = getChannels(imp);
            final String[] frames = getFrames(imp);

            if (channels.length > 1)
                sliceOptions[offset] = gd.getNextChoiceIndex() + 1;
            if (frames.length > 1)
                sliceOptions[offset + 1] = gd.getNextChoiceIndex() + 1;
        }
    }

    private static int getIntValue(String text, int defaultValue)
    {
        try
        {
            return Integer.parseInt(text);
        }
        catch (final NumberFormatException e)
        {
            return defaultValue;
        }
    }

    private static ImageStack getStack(ImagePlus imp, int offset)
    {
        final int channel = sliceOptions[offset];
        final int frame = sliceOptions[offset + 1];
        final int slices = imp.getNSlices();

        final ImageStack stack = new ImageStack(imp.getWidth(), imp.getHeight());
        final ImageStack inputStack = imp.getImageStack();

        for (int slice = 1; slice <= slices; slice++)
        {
            // Convert to a short processor
            final ImageProcessor ip = inputStack.getProcessor(imp.getStackIndex(channel, slice, frame));
            stack.addSlice(null, convertToShortProcessor(imp, ip, channel));
        }
        return stack;
    }

    private static ShortProcessor convertToShortProcessor(ImagePlus imp, ImageProcessor ip, int channel)
    {
        ShortProcessor result;
        if (ip.getClass() == ShortProcessor.class)
            result = (ShortProcessor) ip.duplicate();
        else
            result = (ShortProcessor) (ip.convertToShort(false));
        result.setMinAndMax(getDisplayRangeMin(imp, channel), imp.getDisplayRangeMax());
        result.setRoi(imp.getRoi());
        return result;
    }

    private ImageStack createROI(ImagePlus channelImp, ImagePlus roiImp, int offset, int optionIndex)
    {
        ByteProcessor bp;

        // Get the ROI option
        final String option = ROI_OPTIONS[optionIndex];

        if (option.equals(OPTION_NONE) || roiImp == null || roiImp.getWidth() != channelImp.getWidth() ||
                roiImp.getHeight() != channelImp.getHeight())
            // No ROI - create a mask that covers the entire image
            return defaultMask(channelImp);

        final ImageStack inputStack = roiImp.getImageStack();
        final int channel = sliceOptions[offset];
        final int frame = sliceOptions[offset + 1];
        final int slices = roiImp.getNSlices();

        if (option.equals(OPTION_MIN_VALUE) || option.equals(OPTION_MASK))
        {
            // Use the ROI image to create a mask either using:
            // - non-zero pixels (i.e. a mask)
            // - all pixels above the minimum display value
            final ImageStack result = new ImageStack(roiImp.getWidth(), roiImp.getHeight());

            final int min = (option.equals(OPTION_MIN_VALUE)) ? getDisplayRangeMin(roiImp, channel) : 1;
            final int minIndex = (offset - 4) / 2;
            displayMin[minIndex] = min;

            for (int slice = 1; slice <= slices; slice++)
            {
                bp = new ByteProcessor(roiImp.getWidth(), roiImp.getHeight());
                final ImageProcessor roiIp = inputStack.getProcessor(roiImp.getStackIndex(channel, slice, frame));
                for (int i = roiIp.getPixelCount(); i-- > 0;)
                    if (roiIp.get(i) >= min)
                        bp.set(i, 255);
                result.addSlice(null, bp);
            }

            return result;
        }

        if (option.equals(OPTION_USE_ROI))
        {
            // Use the ROI from the ROI image
            final Roi roi = roiImp.getRoi();

            if (roi != null)
            {
                final ImageStack result = new ImageStack(roiImp.getWidth(), roiImp.getHeight());

                bp = new ByteProcessor(roiImp.getWidth(), roiImp.getHeight());
                bp.setValue(255);
                bp.fill(roi);
                final byte[] pixels = (byte[]) bp.getPixels();
                for (int slice = 1; slice <= slices; slice++)
                    result.addSlice(null, pixels.clone());
                return result;

                //// Use a mask for an irregular ROI
                //ImageProcessor ipMask = roiImp.getMask();
                //
                //// Create a mask from the ROI rectangle
                //Rectangle bounds = roi.getBounds();
                //int xOffset = bounds.x;
                //int yOffset = bounds.y;
                //int rwidth = bounds.width;
                //int rheight = bounds.height;
                //
                //for (int slice = 1; slice <= slices; slice++)
                //{
                //	bp = new ByteProcessor(roiImp.getWidth(), roiImp.getHeight());
                //	for (int y = 0; y < rheight; y++)
                //	{
                //		for (int x = 0; x < rwidth; x++)
                //		{
                //			if (ipMask == null || ipMask.get(x, y) != 0)
                //			{
                //				bp.set(x + xOffset, y + yOffset, 255);
                //			}
                //		}
                //	}
                //	result.addSlice(null, bp);
                //}
                //return result;
            }
        }

        return defaultMask(channelImp);
    }

    private static int getDisplayRangeMin(ImagePlus imp, int channel)
    {
        // Composite images can have a display range for each color channel
        final LUT[] luts = imp.getLuts();
        if (luts != null && channel <= luts.length && channel > 0)
            return (int) luts[channel - 1].min;
        return (int) imp.getDisplayRangeMin();
    }

    private ImageProcessor getRoiIp(ImageProcessor channelIp, int index)
    {
        ImageProcessor roiIp = null;

        if (index < 0)
            roiIp = channelIp;
        else
        {
            final ImagePlus roiImage = WindowManager.getImage(imageList.get(index));
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
    private static ImageStack defaultMask(ImagePlus imp)
    {
        final ImageStack result = new ImageStack(imp.getWidth(), imp.getHeight());
        final ByteProcessor bp = new ByteProcessor(imp.getWidth(), imp.getHeight());
        bp.add(255);
        final byte[] pixels = (byte[]) bp.getPixels();
        for (int s = imp.getNSlices(); s-- > 0;)
            result.addSlice(null, pixels.clone());
        return result;
    }

    private void reportResults(ImagePlus imp1, ImagePlus imp2, ImageStack imageStack1, ImageStack imageStack2,
            ImageStack roiStack1, ImageStack roiStack2, ImageStack confinedStack, ImageStack lastChannelShiftedRawStack,
            ImageStack lastSegmentedShiftedRawStack, double M1, double M2, double R, List<CalculationResult> results)
    {
        IJ.showStatus(processingMandersStatus);

        // Extract the results into individual arrays
        final double[] distances = new double[results.size()];
        final double[] m1Values = new double[results.size()];
        final double[] m2Values = new double[results.size()];
        final double[] rValues = new double[results.size()];

        final int[] indexDistance = new int[m1Values.length];
        int size = 0;

        for (int i = 0; i < m1Values.length; ++i)
        {
            final CalculationResult result = results.get(i);

            distances[i] = result.distance;
            m1Values[i] = result.m1;
            m2Values[i] = result.m2;
            rValues[i] = result.r;

            // All results over the random distance threshold will be used for the significance calculation
            if (distances[i] > randomRadius)
                indexDistance[size++] = i;
        }

        // Extract the results above the random threshold
        final float[] m1ValuesForRandom = new float[size];
        final float[] m2ValuesForRandom = new float[size];
        final float[] rValuesForRandom = new float[size];

        for (int i = 0; i < m1ValuesForRandom.length; ++i)
        {
            m1ValuesForRandom[i] = (float) m1Values[indexDistance[i]];
            m2ValuesForRandom[i] = (float) m2Values[indexDistance[i]];
            rValuesForRandom[i] = (float) rValues[indexDistance[i]];
        }

        // Sort the 'random' result arrays
        Arrays.sort(m1ValuesForRandom);
        Arrays.sort(m2ValuesForRandom);
        Arrays.sort(rValuesForRandom);

        // Initialise the graph points
        final int deltaY = 10;

        final double[] spacedX = new double[maximumRadius];
        final double[] spacedY = new double[deltaY];
        final double[] ceroValuesX = new double[maximumRadius];
        final double[] ceroValuesY = new double[deltaY];

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
        final boolean isSaveResults = (saveResults && checkResultsDirectory());

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

        ImageStack mergedChannelIP = null, mergedSegmentedRGBIP = null, mergedChannelDisplacementIP = null,
                mergedSegmentedDisplacementIP = null;

        final int w = imageStack1.getWidth();
        final int h = imageStack1.getHeight();
        final int slices = imageStack1.getSize();
        if (showMergedChannelRGB)
            mergedChannelIP = new ImageStack(w, h, slices);
        if (showMergedSegmentedRGB)
            mergedSegmentedRGBIP = new ImageStack(w, h, slices);
        if (showMergedChannelDisplacementRGB)
            mergedChannelDisplacementIP = new ImageStack(w, h, slices);
        if (showMergedSegmentedDisplacementRGB)
            mergedSegmentedDisplacementIP = new ImageStack(w, h, slices);

        for (int n = 1; n <= imageStack1.getSize(); n++)
        {
            // Mix the channels
            if (showMergedChannelRGB)
            {
                final ColorProcessor cp = new ColorProcessor(w, h);
                cp.setChannel(1, (ByteProcessor) imageStack1.getProcessor(n).convertToByte(true));
                cp.setChannel(2, (ByteProcessor) imageStack2.getProcessor(n).convertToByte(true));
                mergedChannelIP.setPixels(cp.getPixels(), n);
            }

            // Mix the masks
            if (showMergedSegmentedRGB)
            {
                final ColorProcessor cp = new ColorProcessor(w, h);
                cp.setChannel(1, (ByteProcessor) roiStack1.getProcessor(n));
                cp.setChannel(2, (ByteProcessor) roiStack2.getProcessor(n));
                mergedSegmentedRGBIP.setPixels(cp.getPixels(), n);
            }

            // For reference output the maximum shift of channel 1 AND the original channel 2
            if (showMergedChannelDisplacementRGB)
            {
                final ColorProcessor cp = new ColorProcessor(w, h);
                cp.setChannel(1, (ByteProcessor) lastChannelShiftedRawStack.getProcessor(n).convertToByte(true));
                cp.setChannel(2, (ByteProcessor) imageStack2.getProcessor(n).convertToByte(true));
                mergedChannelDisplacementIP.setPixels(cp.getPixels(), n);
            }

            if (showMergedSegmentedDisplacementRGB)
            {
                final ColorProcessor cp = new ColorProcessor(w, h);
                cp.setChannel(1, (ByteProcessor) lastSegmentedShiftedRawStack.getProcessor(n));
                cp.setChannel(2, (ByteProcessor) roiStack2.getProcessor(n).convertToByte(true));
                mergedSegmentedDisplacementIP.setPixels(cp.getPixels(), n);
            }
        }

        // Update the images
        channel1RGB = updateImage(channel1RGB, channel1RGBTitle, imageStack1, showChannel1RGB, LutColour.RED);
        channel2RGB = updateImage(channel2RGB, channel2RGBTitle, imageStack2, showChannel2RGB, LutColour.GREEN);
        segmented1RGB = updateImage(segmented1RGB, segmented1RGBTitle, roiStack1, showSegmented1RGB, LutColour.RED);
        segmented2RGB = updateImage(segmented2RGB, segmented2RGBTitle, roiStack2, showSegmented2RGB, LutColour.GREEN);
        mergedChannelRGB = updateImage(mergedChannelRGB, mergedChannelTitle, mergedChannelIP, showMergedChannelRGB,
                null);
        mergedSegmentedRGB = updateImage(mergedSegmentedRGB, mergedSegmentedTitle, mergedSegmentedRGBIP,
                showMergedSegmentedRGB, null);
        mergedChannelDisplacementRGB = updateImage(mergedChannelDisplacementRGB, lastShiftedChannelTitle,
                mergedChannelDisplacementIP, showMergedChannelDisplacementRGB, null);
        mergedSegmentedDisplacementRGB = updateImage(mergedSegmentedDisplacementRGB, lastShiftedSegmentedTitle,
                mergedSegmentedDisplacementIP, showMergedSegmentedDisplacementRGB, null);

        // Create plots of the results
        m1PlotWindow = refreshPlotWindow(m1PlotWindow, showM1PlotWindow, plotM1, OPT_LOCATION_PLOT_M1);
        m2PlotWindow = refreshPlotWindow(m2PlotWindow, showM2PlotWindow, plotM2, OPT_LOCATION_PLOT_M2);
        rPlotWindow = refreshPlotWindow(rPlotWindow, showRPlotWindow, plotR, OPT_LOCATION_PLOT_R);

        // Create display statistics for the Mander's and correlation values
        final FloatProcessor m1ValuesFP = new FloatProcessor(m1ValuesForRandom.length, 1, m1ValuesForRandom, null);
        final FloatProcessor m2ValuesFP = new FloatProcessor(m2ValuesForRandom.length, 1, m2ValuesForRandom, null);
        final FloatProcessor rValuesFP = new FloatProcessor(rValuesForRandom.length, 1, rValuesForRandom, null);

        m1Statistics = refreshDisplayStatistics(m1Statistics, showM1Statistics, m1ValuesFP, m1ValuesForRandom,
                m1HistogramTitle, Color.red, "M1 ", M1, OPT_LOCATION_STATS_M1);
        m2Statistics = refreshDisplayStatistics(m2Statistics, showM2Statistics, m2ValuesFP, m2ValuesForRandom,
                m2HistogramTitle, Color.green, "M2 ", M2, OPT_LOCATION_STATS_M2);
        rStatistics = refreshDisplayStatistics(rStatistics, showRStatistics, rValuesFP, rValuesForRandom,
                rHistogramTitle, Color.blue, "R ", R, OPT_LOCATION_STATS_R);

        final String id = generateId();

        StringBuilder heading = null;

        // Output the results to a text window
        if (tw == null || !tw.isShowing())
        {
            heading = createHeading(heading);
            tw = new TextWindow(TITLE + " Results", heading.toString(), "", 1000, 300);
        }

        final StringBuilder resultsEntry = new StringBuilder();
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
            final String directory = Utils.combinePath(resultsDirectory, id);
            try
            {
                // Save results to file
                if (!new File(directory).mkdirs())
                    return;
            }
            catch (final Exception e)
            {
                return;
            }
            IJ.save(mergedSegmentedRGB, directory + File.separatorChar + "MergedROI.tif");
            IJ.save(mergedChannelRGB, directory + File.separatorChar + "MergedChannel.tif");

            try (final OutputStreamWriter out = new OutputStreamWriter(
                    new FileOutputStream(directory + File.separatorChar + "results.txt"), "UTF-8"))
            {
                final String newLine = System.getProperty("line.separator");
                heading = createHeading(heading);
                out.write(heading.toString());
                out.write(newLine);
                out.write(resultsEntry.toString());
                out.write(newLine);
            }
            catch (final Exception e)
            {
                return;
            }
        }
    }

    private static Object getImageTitle(ImagePlus imp, int offset)
    {
        if (imp != null)
        {
            final StringBuilder sb = new StringBuilder(imp.getTitle());
            if (imp.getStackSize() > 1 && (imp.getNChannels() > 1 || imp.getNFrames() > 1))
                sb.append(" (c").append(sliceOptions[offset]).append(",t").append(sliceOptions[offset + 1]).append(")");
            return sb.toString();
        }
        return "[Unknown]";
    }

    private static String generateId()
    {
        final DateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
        return "cda" + df.format(new Date());
    }

    private static ImagePlus updateImage(ImagePlus imp, String title, ImageStack stack, boolean show,
            LutColour lutColor)
    {
        if (!show)
        {
            if (imp != null)
                imp.hide();
            return null;
        }
        else if (stack != null)
        {
            if (imp == null || !imp.isVisible())
            {
                imp = new ImagePlus(title, stack);
                imp.show();
            }
            else
                imp.setStack(title, stack);
            if (lutColor != null)
                imp.setLut(LUTHelper.createLUT(lutColor, true));
        }
        return imp;
    }

    private static StringBuilder createHeading(StringBuilder heading)
    {
        if (heading == null)
        {
            heading = new StringBuilder();

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

    private static PlotWindow refreshPlotWindow(PlotWindow plotWindow, boolean showPlotWindow, Plot plot,
            String locationKey)
    {
        if (showPlotWindow && plot != null)
        {
            if (plotWindow != null && !plotWindow.isClosed())
                plotWindow.drawPlot(plot);
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
        final PlotResults statsPlot = new PlotResults(value, histogramBins, Tools.toDouble(valuesForRandom), colour);

        statsPlot.setXTitle(xTitle);
        statsPlot.setYTitle(plotPDFYLabel);
        statsPlot.setTitle("CDA " + xTitle + " PDF");

        // This is needed to calculate the probability limits for the results table
        // even if the plot is not shown
        statsPlot.calculate(pValue);

        // Generate the statistics needed for the plot
        final FloatStatistics floatStatistics = new FloatStatistics(m1ValuesFP);

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
            closeDisplayStatistics(displayStatistics, locationKey);

        return displayStatistics;
    }

    private static void restoreLocation(Frame frame, Point point)
    {
        if (point != null)
            frame.setLocation(point);
    }

    private Plot createPlot(double[] distances, double[] values, Color color, Color avColor, String title,
            String xLabel, String yLabel, double[] spacedX, double[] ceroValuesX, double[] ceroValuesY,
            double[] spacedY)
    {
        final float[] dummy = null;
        final Plot plot = new Plot(title, xLabel.concat(pixelsUnitString), yLabel, dummy, dummy,
                Plot.X_NUMBERS + Plot.Y_NUMBERS + Plot.X_TICKS + Plot.Y_TICKS);

        double min = 0;
        for (final double d : values)
            if (min > d)
                min = d;

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
        for (final double d : distances)
            if (maxDistance < d)
                maxDistance = d;

        final int n = (int) (maxDistance + 0.5) + 1;
        final double[] sum = new double[n];
        final int[] count = new int[n];
        for (int i = 0; i < distances.length; i++)
        {
            // Round up distance to nearest int
            final int d = (int) (distances[i] + 0.5);
            sum[d] += values[i];
            count[d]++;
        }
        final StoredData avDistances = new StoredData(n);
        final StoredData avValues = new StoredData(n);
        for (int i = (subRandomSamples) ? 0 : 1; i < n; i++)
            if (count[i] > 0)
            {
                avDistances.add(i);
                avValues.add(sum[i] / count[i]);
            }

        plot.setColor(color);
        plot.addPoints(avDistances.getValues(), avValues.getValues(), Plot.LINE);
    }

    private static StringBuilder addField(StringBuilder buffer, Object field)
    {
        if (buffer.length() > 0)
            buffer.append('\t');
        buffer.append(field);
        return buffer;
    }

    private void addRoiField(StringBuilder buffer, int roiIndex, int imageIndex, ImageProcessor roiIp, int offset)
    {
        final String roiOption = ROI_OPTIONS[roiIndex];
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
                    final int minIndex = (offset - 4) / 2;
                    buffer.append(" (>");
                    buffer.append(displayMin[minIndex]);
                    buffer.append(")");
                }
            }
            else if (roiOption.equals(OPTION_USE_ROI))
                if (roiIp != null)
                {
                    final Rectangle r = roiIp.getRoi();
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

    private static void addResults(StringBuilder sb, DisplayStatistics displayStatistics)
    {
        final double value = displayStatistics.getValue();
        final double av = displayStatistics.getAverage();
        final double sd = displayStatistics.getStdDev();
        final double lowerLimit = displayStatistics.getLowerLimit();
        final double upperLimit = displayStatistics.getUpperLimit();

        String result;
        if (value < lowerLimit)
            result = "Significant (non-colocated)";
        else if (value > upperLimit)
            result = "Significant (colocated)";
        else
            result = "Not significant";

        addField(sb, IJ.d2s(value, 4));
        addField(sb, IJ.d2s(av, 4));
        addField(sb, IJ.d2s(sd, 4));
        addField(sb, IJ.d2s(lowerLimit, 4)).append(" : ").append(IJ.d2s(upperLimit, 4));
        addField(sb, result);
    }

    private boolean parametersReady()
    {
        if (WindowManager.getImageCount() == 0)
            // IJ.showMessage(TITLE, "No images opened.");
            return false;

        if (channel1List.getItemCount() < 2)
        {
            // Check for an image stack with more than one channel/frame
            final ImagePlus imp = WindowManager.getImage(channel1List.getSelectedItem());
            if (imp == null || (imp.getNChannels() + imp.getNFrames() < 3))
            {
                IJ.showMessage(TITLE,
                        "Requires 2 images or a multi-channel/frame stack. Images must be 8-bit or 16-bit grayscale.");
                return false;
            }
        }

        maximumRadius = getIntValue(maximumRadiusText.getText(), 0);
        randomRadius = getIntValue(randomRadiusText.getText(), 0);

        if (randomRadius >= maximumRadius)
        {
            IJ.showMessage(TITLE, "Require '" + choiceRandomRadius + "' < '" + choiceMaximumRadius + "'");
            return false;
        }

        return ((channel1List.getSelectedIndex() != -1) && (channel2List.getSelectedIndex() != -1) &&
                (segmented1List.getSelectedIndex() != -1) && (segmented2List.getSelectedIndex() != -1) &&
                (confinedList.getSelectedIndex() != -1) && (channel1List.getItemCount() != 0) &&
                (channel2List.getItemCount() != 0) && (segmented1List.getItemCount() != 0) &&
                (segmented2List.getItemCount() != 0) && (confinedList.getItemCount() != 0));
    }

    private void fillImagesList()
    {
        // Find the currently open images
        final ArrayList<String> newImageList = createImageList();

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

        for (final String imageTitle : newImageList)
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

    private static ArrayList<String> createImageList()
    {
        final ArrayList<String> newImageList = new ArrayList<>();

        for (final int id : uk.ac.sussex.gdsc.core.ij.Utils.getIDList())
        {
            final ImagePlus imp = WindowManager.getImage(id);

            // Image must be 8-bit/16-bit
            if (imp != null && (imp.getType() == ImagePlus.GRAY8 || imp.getType() == ImagePlus.GRAY16))
            {
                // Check it is not one the result images
                final String imageTitle = imp.getTitle();
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
        final Panel mainPanel = new Panel();
        final GridLayout mainGrid = new GridLayout(0, 1);
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
        mainPanel.add(
                createRoiChoicePanel(segmented1List, segmented1Option, choiceSegmentedChannel1, segmented1OptionIndex));

        segmented2List = new Choice();
        segmented2Option = new Choice();
        mainPanel.add(
                createRoiChoicePanel(segmented2List, segmented2Option, choiceSegmentedChannel2, segmented2OptionIndex));

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

        final JPanel buttonPanel = new JPanel();
        final FlowLayout l = new FlowLayout();
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
            randomRadiusText.setBackground(Color.ORANGE);
        else
            randomRadiusText.setBackground(Color.WHITE);

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

    private static double approximateSamples(int radius)
    {
        return Math.PI * radius * radius;
    }

    private static Panel createChoicePanel(Choice list, String label)
    {
        final Panel panel = new Panel();
        panel.setLayout(new BorderLayout());
        final Label listLabel = new Label(label, 0);
        listLabel.setFont(monoFont);
        list.setSize(fontWidth * 3, fontWidth);
        panel.add(listLabel, BorderLayout.WEST);
        panel.add(list, BorderLayout.CENTER);
        return panel;
    }

    private static Panel createRoiChoicePanel(Choice imageList, Choice optionList, String label,
            int selectedOptionIndex)
    {
        final Panel panel = new Panel();
        panel.setLayout(new BorderLayout());
        final Label listLabel = new Label(label, 0);
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
            optionList.select(selectedOptionIndex);

        return panel;
    }

    private static Panel createTextPanel(TextField textField, String label, String value)
    {
        final Panel panel = new Panel();
        panel.setLayout(new BorderLayout());
        final Label listLabel = new Label(label, 0);
        listLabel.setFont(monoFont);
        textField.setSize(fontWidth * 3, fontWidth);
        textField.setText(value);
        panel.add(listLabel, BorderLayout.WEST);
        panel.add(textField, BorderLayout.CENTER);
        return panel;
    }

    private static Panel createCheckboxPanel(Checkbox checkbox, String label, boolean state)
    {
        final Panel panel = new Panel();
        panel.setLayout(new BorderLayout());
        final Label listLabel = new Label(label, 0);
        listLabel.setFont(monoFont);
        checkbox.setState(state);
        panel.add(listLabel, BorderLayout.WEST);
        panel.add(checkbox, BorderLayout.EAST);
        return panel;
    }

    private static Panel createLabelPanel(Label labelField, String label, String value)
    {
        final Panel panel = new Panel();
        panel.setLayout(new BorderLayout());
        final Label listLabel = new Label(label, 0);
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

    private static boolean isZero(ImageStack maskStack)
    {
        for (int s = 1; s <= maskStack.getSize(); s++)
            if (!isZero(maskStack.getProcessor(s)))
                return false;
        return true;
    }

    private static boolean isZero(ImageProcessor ip)
    {
        for (int i = ip.getPixelCount(); i-- > 0;)
            if (ip.get(i) != 0)
                return false;
        return true;
    }

    /**
     * Modify mask1 to include all non-zero pixels from mask 2.
     *
     * @param mask1
     *            the mask 1
     * @param mask2
     *            the mask 2
     */
    private static void unionMask(ImageStack mask1, ImageStack mask2)
    {
        for (int s = 1; s <= mask1.getSize(); s++)
            unionMask((ByteProcessor) mask1.getProcessor(s), (ByteProcessor) mask2.getProcessor(s));
    }

    /**
     * Modify mask1 to include all non-zero pixels from mask 2.
     *
     * @param mask1
     *            the mask 1
     * @param mask2
     *            the mask 2
     */
    private static void unionMask(ByteProcessor mask1, ByteProcessor mask2)
    {
        for (int i = mask1.getPixelCount(); i-- > 0;)
            if (mask2.get(i) != 0)
                mask1.set(i, 255);
    }

    /**
     * Modify the stack to zero all zero pixels from the mask.
     *
     * @param stack
     *            the stack
     * @param mask
     *            the mask
     */
    private static void intersectMask(ImageStack stack, ImageStack mask)
    {
        for (int s = 1; s <= stack.getSize(); s++)
            intersectMask(stack.getProcessor(s), (ByteProcessor) mask.getProcessor(s));
    }

    /**
     * Modify the processor to zero all zero pixels from the mask.
     *
     * @param ip
     *            the image
     * @param mask
     *            the mask
     */
    private static void intersectMask(ImageProcessor ip, ByteProcessor mask)
    {
        for (int i = ip.getPixelCount(); i-- > 0;)
            if (mask.get(i) == 0)
                ip.set(i, 0);
    }

    /**
     * Sum the stack within the mask.
     *
     * @param stack
     *            the stack
     * @param mask
     *            the mask
     * @return the long
     */
    private static long intersectSum(ImageStack stack, ImageStack mask)
    {
        long sum = 0;
        for (int s = 1; s <= stack.getSize(); s++)
            sum += intersectSum(stack.getProcessor(s), (ByteProcessor) mask.getProcessor(s));
        return sum;
    }

    /**
     * Sum the processor within the mask.
     *
     * @param ip
     *            the image
     * @param mask
     *            the mask
     * @return the long
     */
    private static long intersectSum(ImageProcessor ip, ByteProcessor mask)
    {
        long sum = 0;
        for (int i = mask.getPixelCount(); i-- > 0;)
            if (mask.get(i) != 0)
                sum += ip.get(i);
        return sum;
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
            final ImagePlus imp = (imageList.isEmpty()) ? null : WindowManager.getImage(imageList.get(0));
            if (imp == null || (imp.getNChannels() + imp.getNFrames() < 3))
                IJ.showMessage(TITLE,
                        "Requires 2 images or a multi-channel/frame stack. Images must be 8-bit or 16-bit grayscale.");
        }

        final String[] images = new String[imageList.size()];
        final String[] images2 = new String[imageList.size() + 1];
        images2[0] = CHANNEL_IMAGE;
        for (int i = 0; i < imageList.size(); i++)
            images[i] = images2[i + 1] = imageList.get(i);

        final GenericDialog gd = new GenericDialog(TITLE);

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
        setOptions = gd.getNextBoolean();

        if (setOptions)
            setResultsOptions();

        // Get the images
        final ImagePlus imp1 = WindowManager.getImage(images[channel1Index]);
        final ImagePlus imp2 = WindowManager.getImage(images[channel2Index]);
        final ImagePlus roi1 = WindowManager.getImage(images2[segmented1Index]);
        final ImagePlus roi2 = WindowManager.getImage(images2[segmented2Index]);
        final ImagePlus roi = WindowManager.getImage(images[confinedIndex]);

        runCDA(imp1, imp2, roi1, roi2, roi);
    }

    private static void addRoiChoice(GenericDialog gd, String title, String[] roiOptions, String[] images,
            int optionIndex, int index)
    {
        final String name = title.replace(" ", "_");
        gd.addChoice(name, roiOptions, getTitle(roiOptions, optionIndex));
        gd.addChoice(name + "_image", images, getTitle(images, index));
    }

    private static String getTitle(String[] titles, int index)
    {
        if (index < titles.length)
            return titles[index];
        return "";
    }
}
