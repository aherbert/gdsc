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
package uk.ac.sussex.gdsc.colocalisation;

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
import java.awt.event.WindowEvent;
import java.text.DecimalFormat;
import java.util.ArrayList;

import javax.swing.JPanel;

import ij.IJ;
import ij.ImageJ;
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
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.text.TextWindow;
import uk.ac.sussex.gdsc.UsageTracker;

/**
 * Compares two images for correlated pixel intensities. If the two images are correlated a search is performed for the
 * threshold below which the two images are not correlated.
 *
 * Supports stacks. Only a specific channel and time frame can be used and all input images must have the same number
 * of z-sections for the chosen channel/time frame.
 */
public class ColocalisationThreshold_Plugin extends PlugInFrame implements ActionListener, ItemListener, Runnable
{
    private static final long serialVersionUID = 1L;

    // Used to show the results
    private static TextWindow tw;
    private static String TITLE = "CT Plugin";

    // Image titles
    private static String channel1Title = "CT Channel 1";
    private static String channel2Title = "CT Channel 2";
    private static String threshold1Title = "CT Pixels 1";
    private static String threshold2Title = "CT Pixels 2";
    private static String colocalisedPixelsTitle = "CT Colocalised Pixels";
    private static String correlationPlotTitle = "CT correlation: ";
    private static String correlationValuesTitle = "R-values";
    private static String[] resultsTitles = new String[] { channel1Title, channel2Title, threshold1Title,
            threshold2Title, correlationPlotTitle, correlationValuesTitle };

    // Options titles
    private static String choiceNoROI = "[None]";
    private static String choiceChannel1 = "Channel 1";
    private static String choiceChannel2 = "Channel 2";
    private static String choiceUseRoi = "Use ROI";
    private static String choiceRThreshold = "Correlation limit";
    private static String choiceSearchTolerance = "Search tolerance";
    private static String choiceShowColocalised = "Show colocalised pixels";
    private static String choiceUseConstantIntensity = "Use constant intensity for colocalised pixels";
    private static String choiceShowScatterPlot = "Show Scatter plot";
    private static String choiceIncludeZeroZeroPixels = "Include zero-zero pixels in threshold calculation";
    private static String choiceCloseWindowsOnExit = "Close windows on exit";
    private static String choiceSetOptions = "Set results options";
    private static String okButtonLabel = "Apply";
    private static String helpButtonLabel = "Help";

    private static Frame instance;

    private Choice channel1List;
    private Choice channel2List;
    private Choice roiList;
    private TextField rThresholdTextField;
    private TextField searchToleranceTextField;
    private Checkbox showColocalisedCheckbox;
    private Checkbox useConstantIntensityCheckbox;
    private Checkbox showScatterPlotCheckbox;
    private Checkbox includeZeroZeroPixelsCheckbox;
    private Checkbox closeWindowsOnExitCheckbox;
    private Checkbox setResultsOptionsCheckbox;
    private Button okButton;
    private Button helpButton;

    private final int fontWidth = 12;
    private final Font monoFont = new Font("Monospaced", 0, fontWidth);

    private final String OPT_LOCATION = "CT.location";

    private final String OPT_CHANNEL1_INDEX = "CT.channel1SelectedIndex";
    private final String OPT_CHANNEL2_INDEX = "CT.channel2SelectedIndex";
    private final String OPT_ROI_INDEX = "CT.roiIndex";
    private final String OPT_R_THRESHOLD = "CT.rThreshold";
    private final String OPT_SEARCH_TOLERANCE = "CT.searchTolerance";
    private final String OPT_SHOW_COLOCALISED = "CT.showColocalised";
    private final String OPT_USE_CONSTANT_INTENSITY = "CT.useConstantIntensity";
    private final String OPT_SHOW_SCATTER_PLOT = "CT.showScatterPlot";
    private final String OPT_INCLUDE_ZERO_ZERO_PIXELS = "CT.includeZeroZeroPixels";
    private final String OPT_CLOSE_WINDOWS_ON_EXIT = "CT.closeWindowsOnExit";

    private final String OPT_SHOW_THRESHOLDS = "CT.showThresholds";
    private final String OPT_SHOW_LINEAR_REGRESSION = "CT.showLinearRegression";
    private final String OPT_SHOW_R_TOTAL = "CT.showRTotal";
    private final String OPT_SHOW_R_GT_T = "CT.showRForGtT";
    private final String OPT_SHOW_R_LT_T = "CT.showRForLtT";
    private final String OPT_SHOW_MANDERS = "CT.showManders";
    private final String OPT_SHOW_MANDERS_GT_T = "CT.showMandersGtT";
    private final String OPT_SHOW_N_COLOC = "CT.showNColoc";
    private final String OPT_SHOW_VOLUME_COLOC = "CT.showVolumeColoc";
    private final String OPT_SHOW_VOLUME_GT_T_COLOC = "CT.showVolumeGtTColoc";
    private final String OPT_SHOW_INTENSITY_COLOC = "CT.showIntensityColoc";
    private final String OPT_SHOW_INTENSITY_GT_T_COLOC = "CT.showIntensityGtTColoc";
    private final String OPT_SHOW_ROIS_AND_MASKS = "CT.showRoisAndMasks";
    private final String OPT_EXHAUSTIVE_SEARCH = "CT.exhaustiveSearch";
    private final String OPT_PLOT_R_VALUES = "CT.plotRValues";
    private final String OPT_MAX_ITERATIONS = "CT.maxIterations";

    // Options
    private final double DEFAULT_R_LIMIT = 0;
    private final double DEFAULT_SEARCH_TOLERANCE = 0.05;
    private int channel1SelectedIndex = (int) Prefs.get(OPT_CHANNEL1_INDEX, 0);
    private int channel2SelectedIndex = (int) Prefs.get(OPT_CHANNEL2_INDEX, 0);
    private int roiIndex = (int) Prefs.get(OPT_ROI_INDEX, 0);
    private double rThreshold = Prefs.get(OPT_R_THRESHOLD, DEFAULT_R_LIMIT);
    private double searchTolerance = Prefs.get(OPT_SEARCH_TOLERANCE, DEFAULT_SEARCH_TOLERANCE);
    private boolean showColocalised = Prefs.get(OPT_SHOW_COLOCALISED, false);
    private boolean useConstantIntensity = Prefs.get(OPT_USE_CONSTANT_INTENSITY, false);
    private boolean showScatterPlot = Prefs.get(OPT_SHOW_SCATTER_PLOT, false);
    private boolean includeZeroZeroPixels = Prefs.get(OPT_INCLUDE_ZERO_ZERO_PIXELS, true);
    private boolean closeWindowsOnExit = Prefs.get(OPT_CLOSE_WINDOWS_ON_EXIT, true);

    // Results options
    private boolean showThresholds = Prefs.get(OPT_SHOW_THRESHOLDS, true);
    private boolean showLinearRegression = Prefs.get(OPT_SHOW_LINEAR_REGRESSION, true);
    private boolean showRTotal = Prefs.get(OPT_SHOW_R_TOTAL, true);
    private boolean showRForGtT = Prefs.get(OPT_SHOW_R_GT_T, true);
    private boolean showRForLtT = Prefs.get(OPT_SHOW_R_LT_T, true);
    private boolean showManders = Prefs.get(OPT_SHOW_MANDERS, true);
    private boolean showMandersGtT = Prefs.get(OPT_SHOW_MANDERS_GT_T, true);
    private boolean showNColoc = Prefs.get(OPT_SHOW_N_COLOC, true);
    private boolean showVolumeColoc = Prefs.get(OPT_SHOW_VOLUME_COLOC, true);
    private boolean showVolumeGtTColoc = Prefs.get(OPT_SHOW_VOLUME_GT_T_COLOC, true);
    private boolean showIntensityColoc = Prefs.get(OPT_SHOW_INTENSITY_COLOC, true);
    private boolean showIntensityGtTColoc = Prefs.get(OPT_SHOW_INTENSITY_GT_T_COLOC, true);
    private boolean showRoisAndMasks = Prefs.get(OPT_SHOW_ROIS_AND_MASKS, true);
    private boolean exhaustiveSearch = Prefs.get(OPT_EXHAUSTIVE_SEARCH, false);
    private boolean plotRValues = Prefs.get(OPT_PLOT_R_VALUES, true);
    private int maxIterations = (int) Prefs.get(OPT_MAX_ITERATIONS, 50);

    // Windows that are opened by the plug-in.
    // These should be closed on exit.
    private final ImagePlus scatterPlot = new ImagePlus();
    private final ImagePlus channel1RGB = new ImagePlus();
    private final ImagePlus channel2RGB = new ImagePlus();
    private final ImagePlus segmented1RGB = new ImagePlus();
    private final ImagePlus segmented2RGB = new ImagePlus();
    private final ImagePlus mixChannel = new ImagePlus();

    private PlotWindow rPlot;

    private ImageJ ij;

    // Store the channels and frames to use from image stacks
    private final int[] sliceOptions = new int[4];

    // Stores the list of images last used in the selection options
    private ArrayList<String> imageList = new ArrayList<>();

    /**
     * Instantiates a new colocalisation threshold plugin.
     */
    public ColocalisationThreshold_Plugin()
    {
        super(TITLE);
    }

    @Override
    public void run(String arg)
    {
        UsageTracker.recordPlugin(this.getClass(), arg);

        if (WindowManager.getImageCount() == 0)
        {
            IJ.error(TITLE, "No images opened.");
            return;
        }

        if (instance != null)
            if (!(instance.getTitle().equals(getTitle())))
            {
                final ColocalisationThreshold_Plugin cda = (ColocalisationThreshold_Plugin) instance;
                Prefs.saveLocation(OPT_LOCATION, cda.getLocation());
                cda.close();
            }
            else
            {
                instance.toFront();
                return;
            }

        instance = this;
        IJ.register(ColocalisationThreshold_Plugin.class);
        WindowManager.addWindow(this);

        ij = IJ.getInstance();

        createFrame();
        setup();

        addKeyListener(ij);
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
            final Thread thread = new Thread(this, "ColicalisationThreshold_Plugin");
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
        if (setResultsOptionsCheckbox.getState())
        {
            setResultsOptionsCheckbox.setState(false);

            final GenericDialog gd = new GenericDialog("Set Results Options");

            gd.addCheckbox("Show linear regression solution", showLinearRegression);
            gd.addCheckbox("Show thresholds", showThresholds);
            gd.addCheckbox("Pearson's for whole image", showRTotal);
            gd.addCheckbox("Pearson's for image above thresholds", showRForGtT);
            gd.addCheckbox("Pearson's for image below thresholds (should be ~0)", showRForLtT);
            gd.addCheckbox("Mander's original coefficients (threshold = 0)", showManders);
            gd.addCheckbox("Mander's using thresholds", showMandersGtT);
            gd.addCheckbox("Number of colocalised voxels", showNColoc);
            gd.addCheckbox("% Volume colocalised", showVolumeColoc);
            gd.addCheckbox("% Volume above threshold colocalised", showVolumeGtTColoc);
            gd.addCheckbox("% Intensity colocalised", showIntensityColoc);
            gd.addCheckbox("% Intensity above threshold colocalised", showIntensityGtTColoc);
            gd.addCheckbox("Output ROIs/Masks", showRoisAndMasks);
            gd.addCheckbox("Exhaustive search", exhaustiveSearch);
            gd.addCheckbox("Plot R values", plotRValues);
            gd.addNumericField("Max. iterations", maxIterations, 0);

            gd.showDialog();

            if (gd.wasCanceled())
                return;

            showThresholds = gd.getNextBoolean();
            showLinearRegression = gd.getNextBoolean();
            showRTotal = gd.getNextBoolean();
            showRForGtT = gd.getNextBoolean();
            showRForLtT = gd.getNextBoolean();
            showManders = gd.getNextBoolean();
            showMandersGtT = gd.getNextBoolean();
            showNColoc = gd.getNextBoolean();
            showVolumeColoc = gd.getNextBoolean();
            showVolumeGtTColoc = gd.getNextBoolean();
            showIntensityColoc = gd.getNextBoolean();
            showIntensityGtTColoc = gd.getNextBoolean();
            showRoisAndMasks = gd.getNextBoolean();
            exhaustiveSearch = gd.getNextBoolean();
            plotRValues = gd.getNextBoolean();
            maxIterations = (int) gd.getNextNumber();
        }
    }

    @Override
    public void windowClosing(WindowEvent e)
    {
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
            closeImagePlus(mixChannel);
            closeImagePlus(scatterPlot);

            if (tw != null && tw.isShowing())
                tw.close();
        }

        instance = null;
        super.close();
    }

    private static void closeImagePlus(ImagePlus w)
    {
        if (w != null)
            w.close();
    }

    @Override
    public void windowActivated(WindowEvent e)
    {
        fillImagesList();

        super.windowActivated(e);
        WindowManager.setWindow(this);
    }

    /** {@inheritDoc} */
    @Override
    public void run()
    {
        findThreshold();

        synchronized (this)
        {
            super.notify();
        }
    }

    private void findThreshold()
    {
        if (!parametersReady())
            return;

        // Read settings
        channel1SelectedIndex = channel1List.getSelectedIndex();
        channel2SelectedIndex = channel2List.getSelectedIndex();
        roiIndex = roiList.getSelectedIndex();
        rThreshold = getDouble(rThresholdTextField.getText(), DEFAULT_R_LIMIT);
        searchTolerance = getDouble(searchToleranceTextField.getText(), DEFAULT_SEARCH_TOLERANCE);
        showColocalised = showColocalisedCheckbox.getState();
        useConstantIntensity = useConstantIntensityCheckbox.getState();
        showScatterPlot = showScatterPlotCheckbox.getState();
        includeZeroZeroPixels = includeZeroZeroPixelsCheckbox.getState();
        closeWindowsOnExit = closeWindowsOnExitCheckbox.getState();

        ImagePlus imp1 = WindowManager.getImage(channel1List.getSelectedItem());
        ImagePlus imp2 = WindowManager.getImage(channel2List.getSelectedItem());

        final int width1 = imp1.getWidth();
        final int width2 = imp2.getWidth();
        final int height1 = imp1.getHeight();
        final int height2 = imp2.getHeight();

        if ((width1 != width2) || (height1 != height2))
        {
            IJ.showMessage(TITLE, "Both images (stacks) must be at the same height and width");
            return;
        }

        // Check for image stacks and get the channel and frame (if applicable)
        if (!getStackOptions(imp1, imp2))
            return;

        // This should not be a problem but leave it in for now
        if ((imp1.getType() != ImagePlus.GRAY8 && imp1.getType() != ImagePlus.GRAY16) ||
                (imp2.getType() != ImagePlus.GRAY8 && imp2.getType() != ImagePlus.GRAY16))
        {
            IJ.showMessage("Image Correlator", "Images must be 8-bit or 16-bit grayscale.");
            return;
        }

        // Extract images
        imp1 = createImagePlus(imp1, 0);
        imp2 = createImagePlus(imp2, 2);

        if (imp1.getNSlices() != imp2.getNSlices())
        {
            IJ.showMessage(TITLE, "Both images (stacks) must be at the same depth");
            return;
        }

        correlate(imp1, imp2);
    }

    private static double getDouble(String value, double defaultValue)
    {
        try
        {
            return Double.parseDouble(value);
        }
        catch (final Exception ex)
        {
            return defaultValue;
        }
    }

    private boolean getStackOptions(ImagePlus imp1, ImagePlus imp2)
    {
        if (isStack(imp1) || isStack(imp1))
        {
            final GenericDialog gd = new GenericDialog("Slice options");
            gd.addMessage("Stacks detected. Please select the slices.");

            boolean added = false;
            added |= addOptions(gd, imp1, "Image 1", 0);
            added |= addOptions(gd, imp2, "Image 2", 2);

            if (added)
            {
                gd.showDialog();

                if (gd.wasCanceled())
                    return false;

                // Populate the channels and frames into an options array
                getOptions(gd, imp1, 0);
                getOptions(gd, imp2, 2);
            }
        }
        return true;
    }

    private static boolean isStack(ImagePlus imp)
    {
        return (imp != null && imp.getStackSize() > 1);
    }

    private boolean addOptions(GenericDialog gd, ImagePlus imp, String title, int offset)
    {
        boolean added = false;
        if (imp != null)
        {
            final String[] channels = getChannels(imp);
            final String[] frames = getFrames(imp);

            if (channels.length > 1 || frames.length > 1)
            {
                added = true;
                gd.addMessage(title);
            }

            setOption(gd, channels, "Channel", offset);
            setOption(gd, frames, "Frame", offset + 1);
        }
        return added;
    }

    private boolean setOption(GenericDialog gd, String[] choices, String title, int offset)
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

    private void getOptions(GenericDialog gd, ImagePlus imp, int offset)
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

    private ImagePlus createImagePlus(ImagePlus originalImp, int offset)
    {
        final int channel = sliceOptions[offset];
        final int frame = sliceOptions[offset + 1];
        final int slices = originalImp.getNSlices();

        final ImageStack stack = new ImageStack(originalImp.getWidth(), originalImp.getHeight());
        final ImageStack inputStack = originalImp.getImageStack();

        for (int slice = 1; slice <= slices; slice++)
        {
            // Convert to a short processor
            final ImageProcessor ip = inputStack.getProcessor(originalImp.getStackIndex(channel, slice, frame));
            stack.addSlice(null, ip);
        }

        final StringBuilder sb = new StringBuilder(originalImp.getTitle());
        if (slices > 1 && (originalImp.getNChannels() > 1 || originalImp.getNFrames() > 1))
            sb.append(" (c").append(channel).append(",t").append(frame).append(")");

        final ImagePlus imp = new ImagePlus(sb.toString(), stack);
        final Roi roi = originalImp.getRoi();
        if (roi != null)
            imp.setRoi(roi);
        return imp;
    }

    /**
     * Perform correlation analysis on two images.
     *
     * @param imp1
     *            the image 1
     * @param imp2
     *            the image 2
     */
    public void correlate(ImagePlus imp1, ImagePlus imp2)
    {
        final ImageStack img1 = imp1.getStack();
        final ImageStack img2 = imp2.getStack();

        // Start regression
        IJ.showStatus("Performing regression. Press 'Esc' to abort");

        ColocalisationThreshold ct;
        try
        {
            ct = new ColocalisationThreshold(imp1, imp2, roiIndex);
            ct.setIncludeNullPixels(includeZeroZeroPixels);
            ct.setMaxIterations(maxIterations);
            ct.setExhaustiveSearch(exhaustiveSearch);
            ct.setRThreshold(rThreshold);
            ct.setSearchTolerance(searchTolerance);
            if (!ct.correlate())
            {
                IJ.showMessage(TITLE, "No correlation found above tolerance. Ending");
                IJ.showStatus("Done");
                return;
            }
        }
        catch (final Exception ex)
        {
            IJ.error(TITLE, "Error: " + ex.getMessage());
            IJ.showStatus("Done");
            return;
        }

        IJ.showStatus("Generating results");

        // Set up the ROI
        ImageProcessor ipMask = null;
        Rectangle roiRect = null;

        if (roiIndex != 0)
        {
            final ImagePlus roiImage = (roiIndex == 1) ? imp1 : imp2;
            final Roi roi = roiImage.getRoi();

            if (roi != null)
            {
                roiRect = roi.getBounds();

                // Use a mask for an irregular ROI
                if (roi.getType() != Roi.RECTANGLE)
                    ipMask = roiImage.getMask();
            }
            else
                // Reset the choice for next time
                roiIndex = 0;
        }

        saveOptions();

        final int nslices = imp1.getStackSize();
        final int width = imp1.getWidth();
        final int height = imp1.getHeight();
        int xOffset, yOffset, rwidth, rheight;

        if (roiRect == null)
        {
            xOffset = 0;
            yOffset = 0;
            rwidth = width;
            rheight = height;
        }
        else
        {
            xOffset = roiRect.x;
            yOffset = roiRect.y;
            rwidth = roiRect.width;
            rheight = roiRect.height;
        }

        int mask = 0;

        final ImageProcessor plot16 = new ShortProcessor(256, 256);
        int scaledC1ThresholdValue = 0;
        int scaledC2ThresholdValue = 0;
        imp1.getCurrentSlice();

        final int ch1threshmax = ct.getThreshold1();
        final int ch2threshmax = ct.getThreshold2();
        int colocInt = 255;

        long n = 0;
        long nCh1gtT = 0;
        long nCh2gtT = 0;
        long nZero = 0;
        long nColoc = 0;

        long sumCh1 = 0;
        long sumCh2 = 0;
        long sumCh1_ch2gt0 = 0;
        long sumCh2_ch1gt0 = 0;
        long sumCh1_ch2gtT = 0;
        long sumCh2_ch1gtT = 0;
        long sumCh1gtT = 0;
        long sumCh2gtT = 0;
        long sumCh1_coloc = 0;
        long sumCh2_coloc = 0;

        final int ch1Max = ct.getCh1Max();
        final int ch2Max = ct.getCh2Max();
        double ch1Scaling = (double) 255 / (double) ch1Max;
        double ch2Scaling = (double) 255 / (double) ch2Max;
        if (ch1Scaling > 1)
            ch1Scaling = 1;
        if (ch2Scaling > 1)
            ch2Scaling = 1;

        final ImageStack stackColoc = new ImageStack(rwidth, rheight);

        // These will be used to store the output image ROIs
        ImageStack outputStack1 = null;
        ImageStack outputStack2 = null;
        // These will be used to store the output masks
        final ImageStack outputMask1 = new ImageStack(rwidth, rheight);
        final ImageStack outputMask2 = new ImageStack(rwidth, rheight);

        // If an ROI was used then an image should be extracted
        if (roiRect != null)
        {
            outputStack1 = new ImageStack(rwidth, rheight);
            outputStack2 = new ImageStack(rwidth, rheight);
        }

        final int[] color = new int[3];
        for (int s = 1; s <= nslices; s++)
        {
            final ImageProcessor ip1 = img1.getProcessor(s);
            final ImageProcessor ip2 = img2.getProcessor(s);

            final ColorProcessor ipColoc = new ColorProcessor(rwidth, rheight);
            ImageProcessor out1 = null;
            ImageProcessor out2 = null;
            final ByteProcessor mask1 = new ByteProcessor(rwidth, rheight);
            final ByteProcessor mask2 = new ByteProcessor(rwidth, rheight);

            if (outputStack1 != null)
            {
                // Create output processors of the same type
                out1 = createProcessor(imp1, rwidth, rheight);
                out2 = createProcessor(imp2, rwidth, rheight);
            }

            for (int y = 0; y < rheight; y++)
                for (int x = 0; x < rwidth; x++)
                {
                    mask = (ipMask != null) ? ipMask.get(x, y) : 1;

                    if (mask != 0)
                    {
                        final int ch1 = ip1.getPixel(x + xOffset, y + yOffset);
                        final int ch2 = ip2.getPixel(x + xOffset, y + yOffset);

                        if (out1 != null)
                        {
                            out1.set(x, y, ch1);
                            out2.set(x, y, ch2);
                        }

                        final int scaledCh1 = (int) (ch1 * ch1Scaling);
                        final int scaledCh2 = (int) (ch2 * ch2Scaling);

                        color[0] = scaledCh1;
                        color[1] = scaledCh2;
                        color[2] = 0;

                        ipColoc.putPixel(x, y, color);

                        sumCh1 += ch1;
                        sumCh2 += ch2;
                        n++;

                        scaledC1ThresholdValue = scaledCh1;
                        scaledC2ThresholdValue = 255 - scaledCh2;
                        int count = plot16.getPixel(scaledC1ThresholdValue, scaledC2ThresholdValue);
                        count++;
                        plot16.putPixel(scaledC1ThresholdValue, scaledC2ThresholdValue, count);

                        if (ch1 + ch2 == 0)
                            nZero++;

                        if (ch1 > 0)
                            sumCh2_ch1gt0 += ch2;
                        if (ch2 > 0)
                            sumCh1_ch2gt0 += ch1;

                        if (ch1 >= ch1threshmax)
                        {
                            nCh1gtT++;
                            sumCh1gtT += ch1;
                            sumCh2_ch1gtT += ch2;
                            mask1.set(x, y, 255);
                        }
                        if (ch2 >= ch2threshmax)
                        {
                            nCh2gtT++;
                            sumCh2gtT += ch2;
                            sumCh1_ch2gtT += ch1;
                            mask2.set(x, y, 255);

                            if (ch1 >= ch1threshmax)
                            {
                                sumCh1_coloc += ch1;
                                sumCh2_coloc += ch2;
                                nColoc++;

                                if (!useConstantIntensity)
                                    colocInt = (int) Math.sqrt(scaledCh1 * scaledCh2);
                                color[2] = colocInt;

                                ipColoc.putPixel(x, y, color);
                            }
                        }
                    }
                }

            stackColoc.addSlice(colocalisedPixelsTitle + "." + s, ipColoc);

            if (outputStack1 != null)
            {
                outputStack1.addSlice(channel1Title + "." + s, out1);
                outputStack2.addSlice(channel2Title + "." + s, out2);
            }
            outputMask1.addSlice(threshold1Title + "." + s, mask1);
            outputMask2.addSlice(threshold2Title + "." + s, mask2);
        }

        final long totalPixels = n;
        if (!includeZeroZeroPixels)
            n -= nZero;

        // Pearsons for colocalised volume -
        // Should get this directly from the Colocalisation object
        final double rColoc = ct.getRAboveThreshold();

        // Mander's original
        // [i.e. E(ch1 if ch2>0) / E(ch1total)]
        // (How much of channel 1 intensity occurs where channel 2 has signal)

        final double m1 = (double) sumCh1_ch2gt0 / sumCh1;
        final double m2 = (double) sumCh2_ch1gt0 / sumCh2;

        // Manders using threshold
        // [i.e. E(ch1 if ch2>ch2threshold) / E(ch1total)]
        // This matches other plug-ins, i.e. how much of channel 1 intensity occurs where channel 2 is correlated
        final double m1threshold = (double) sumCh1_ch2gtT / sumCh1;
        final double m2threshold = (double) sumCh2_ch1gtT / sumCh2;

        // as in Coste's paper
        // [i.e. E(ch1 > ch1threshold) / E(ch1total)]
        // This appears to be wrong when compared to other plug-ins
        // m1threshold = (double) sumCh1gtT / sumCh1;
        // m2threshold = (double) sumCh2gtT / sumCh2;

        // Imaris percentage volume
        final double percVolCh1 = (double) nColoc / (double) nCh1gtT;
        final double percVolCh2 = (double) nColoc / (double) nCh2gtT;

        final double percTotCh1 = (double) sumCh1_coloc / (double) sumCh1;
        final double percTotCh2 = (double) sumCh2_coloc / (double) sumCh2;

        // Imaris percentage material
        final double percGtTCh1 = (double) sumCh1_coloc / (double) sumCh1gtT;
        final double percGtTCh2 = (double) sumCh2_coloc / (double) sumCh2gtT;

        // Create results window
        final String resultsTitle = TITLE + " Results";
        openResultsWindow(resultsTitle);
        final String imageTitle = createImageTitle(imp1, imp2);

        showResults(imageTitle, ch1threshmax, ch2threshmax, n, nZero, nCh1gtT, nCh2gtT, ct, nColoc, rColoc, m1, m2,
                m1threshold, m2threshold, percVolCh1, percVolCh2, percTotCh1, percTotCh2, percGtTCh1, percGtTCh2,
                totalPixels);

        if (showColocalised)
            refreshImage(mixChannel, colocalisedPixelsTitle, stackColoc);

        if (showRoisAndMasks)
        {
            if (outputStack1 != null)
            {
                refreshImage(channel1RGB, channel1Title, outputStack1);
                refreshImage(channel2RGB, channel2Title, outputStack2);
            }

            refreshImage(segmented1RGB, threshold1Title, outputMask1);
            refreshImage(segmented2RGB, threshold2Title, outputMask2);
        }

        if (showScatterPlot)
            showScatterPlot(ct, ch1threshmax, ch2threshmax, plot16, ch1Max, ch1Scaling, ch2Scaling, imageTitle);

        if (plotRValues)
            plotResults(ct.getResults());

        IJ.selectWindow(resultsTitle);
        IJ.showStatus("Done");
    }

    private static String createImageTitle(ImagePlus imp1, ImagePlus imp2)
    {
        return imp1.getTitle() + " & " + imp2.getTitle();
    }

    private static void refreshImage(ImagePlus imp, String title, ImageStack img)
    {
        imp.setStack(title, img);
        imp.show();
        imp.updateAndDraw();
    }

    private static ImageProcessor createProcessor(ImagePlus imp, int rwidth, int rheight)
    {
        if (imp.getType() == ImagePlus.GRAY8)
            return new ByteProcessor(rwidth, rheight);
        return new ShortProcessor(rwidth, rheight);
    }

    private void showScatterPlot(ColocalisationThreshold ct, int ch1threshmax, int ch2threshmax, ImageProcessor plot16,
            int ch1Max, double ch1Scaling, double ch2Scaling, String fileName)
    {
        int scaledC1ThresholdValue;
        int scaledC2ThresholdValue;
        double plotY = 0;
        plot16.resetMinAndMax();
        final int plotmax2 = (int) (plot16.getMax());
        final int plotmax = plotmax2 / 2;

        scaledC1ThresholdValue = (int) (ch1threshmax * ch1Scaling);
        scaledC2ThresholdValue = 255 - (int) (ch2threshmax * ch2Scaling);

        final double m = ct.getM();
        final double b = ct.getB();

        // Draw regression line
        for (int c = (ch1Max < 256) ? 256 : ch1Max; c-- > 0;)
        {
            plotY = (c * m) + b;

            final int scaledXValue = (int) (c * ch1Scaling);
            final int scaledYValue = 255 - (int) (plotY * ch2Scaling);

            plot16.putPixel(scaledXValue, scaledYValue, plotmax);

            // Draw threshold lines
            plot16.putPixel(scaledXValue, scaledC2ThresholdValue, plotmax);
            plot16.putPixel(scaledC1ThresholdValue, scaledXValue, plotmax);
        }

        scatterPlot.setProcessor(correlationPlotTitle + fileName, plot16);
        scatterPlot.updateAndDraw();
        scatterPlot.show();
        IJ.selectWindow(scatterPlot.getTitle());
        IJ.run("Enhance Contrast", "saturated=50 equalize");
        IJ.run("Fire");
    }

    private void showResults(String fileName, int ch1threshmax, int ch2threshmax, long n, long nZero, long nCh1gtT,
            long nCh2gtT, ColocalisationThreshold ct, long nColoc, double rColoc, double m1, double m2,
            double m1threshold, double m2threshold, double percVolCh1, double percVolCh2, double percTotCh1,
            double percTotCh2, double percGtTCh1, double percGtTCh2, double totalPixels)
    {
        final StringBuilder str = new StringBuilder();
        str.append(fileName).append('\t');
        switch (roiIndex)
        {
            case 0:
                str.append(choiceNoROI);
                break;
            case 1:
                str.append(choiceChannel1);
                break;
            default:
                str.append(choiceChannel2);
                break;
        }

        final DecimalFormat df4 = new DecimalFormat("##0.0000");
        final DecimalFormat df3 = new DecimalFormat("##0.000");
        final DecimalFormat df2 = new DecimalFormat("##0.00");
        final DecimalFormat df1 = new DecimalFormat("##0.0");
        final DecimalFormat df0 = new DecimalFormat("##0");

        str.append((includeZeroZeroPixels) ? "\tincl.\t" : "\texcl.\t");

        if (showRTotal)
            appendFormat(str, ct.getRTotal(), df3);
        if (showLinearRegression)
        {
            final double m = ct.getM();
            final double b = ct.getB();
            appendFormat(str, m, df3);
            appendFormat(str, b, df1);
        }
        if (showThresholds)
        {
            appendFormat(str, ch1threshmax, df0);
            appendFormat(str, ch2threshmax, df0);
        }
        if (showRForGtT)
            appendFormat(str, rColoc, df4);
        if (showRForLtT)
            appendFormat(str, ct.getRBelowThreshold(), df3);
        if (showManders)
        {
            appendFormat(str, m1, df4);
            appendFormat(str, m2, df4);
        }
        if (showMandersGtT)
        {
            appendFormat(str, m1threshold, df4);
            appendFormat(str, m2threshold, df4);
        }
        if (showNColoc)
        {
            appendFormat(str, n, df0);
            appendFormat(str, nZero, df0);
            appendFormat(str, nCh1gtT, df0);
            appendFormat(str, nCh2gtT, df0);
            appendFormat(str, nColoc, df0);
        }
        if (showVolumeColoc)
            appendFormat(str, (nColoc * 100.0) / totalPixels, df2, "%");
        if (showVolumeGtTColoc)
        {
            appendFormat(str, percVolCh1 * 100.0, df2, "%");
            appendFormat(str, percVolCh2 * 100.0, df2, "%");
        }
        if (showIntensityColoc)
        {
            appendFormat(str, percTotCh1 * 100.0, df2, "%");
            appendFormat(str, percTotCh2 * 100.0, df2, "%");
        }
        if (showIntensityGtTColoc)
        {
            appendFormat(str, percGtTCh1 * 100.0, df2, "%");
            appendFormat(str, percGtTCh2 * 100.0, df2, "%");
        }

        tw.append(str.toString());
    }

    private void openResultsWindow(String resultsTitle)
    {
        if (tw == null || !tw.isShowing())
        {
            final StringBuilder heading = new StringBuilder("Images\tROI\tZeroZero\t");

            if (showRTotal)
                heading.append("Rtotal\t");
            if (showLinearRegression)
                heading.append("m\tb\t");
            if (showThresholds)
                heading.append("Ch1 thresh\tCh2 thresh\t");
            if (showRForGtT)
                heading.append("Rcoloc\t");
            if (showRForLtT)
                heading.append("R<threshold\t");
            if (showManders)
                heading.append("M1\tM2\t");
            if (showMandersGtT)
                heading.append("tM1\ttM2\t");
            if (showNColoc)
            {
                heading.append("N\t");
                heading.append("nZero\t");
                heading.append("nCh1gtT\t");
                heading.append("nCh2gtT\t");
                heading.append("nColoc\t");
            }
            if (showVolumeColoc)
                heading.append("%Vol Coloc\t");
            if (showVolumeGtTColoc)
            {
                heading.append("%Ch1gtT Vol Coloc\t");
                heading.append("%Ch2gtT Vol Coloc\t");
            }
            if (showIntensityColoc)
            {
                heading.append("%Ch1 Int Coloc\t");
                heading.append("%Ch2 Int Coloc\t");
            }
            if (showIntensityGtTColoc)
            {
                heading.append("%Ch1gtT Int Coloc\t");
                heading.append("%Ch2gtT Int Coloc\t");
            }
            heading.append("\n");

            tw = new TextWindow(resultsTitle, heading.toString(), "", 1000, 300);
        }
    }

    private static void appendFormat(StringBuilder str, double value, DecimalFormat format)
    {
        if (Double.isNaN(value))
            str.append("NaN");
        else
            str.append(format.format(value));
        str.append('\t');
    }

    private static void appendFormat(StringBuilder str, double value, DecimalFormat format, String units)
    {
        if (Double.isNaN(value))
            str.append("NaN");
        else
            str.append(format.format(value)).append(units);
        str.append('\t');
    }

    private void saveOptions()
    {
        Prefs.set(OPT_CHANNEL1_INDEX, channel1SelectedIndex);
        Prefs.set(OPT_CHANNEL2_INDEX, channel2SelectedIndex);
        Prefs.set(OPT_ROI_INDEX, roiIndex);
        Prefs.set(OPT_R_THRESHOLD, rThreshold);
        Prefs.set(OPT_SEARCH_TOLERANCE, searchTolerance);
        Prefs.set(OPT_SHOW_COLOCALISED, showColocalised);
        Prefs.set(OPT_USE_CONSTANT_INTENSITY, useConstantIntensity);
        Prefs.set(OPT_SHOW_SCATTER_PLOT, showScatterPlot);
        Prefs.set(OPT_INCLUDE_ZERO_ZERO_PIXELS, includeZeroZeroPixels);
        Prefs.set(OPT_CLOSE_WINDOWS_ON_EXIT, closeWindowsOnExit);

        Prefs.set(OPT_SHOW_THRESHOLDS, showThresholds);
        Prefs.set(OPT_SHOW_LINEAR_REGRESSION, showLinearRegression);
        Prefs.set(OPT_SHOW_R_TOTAL, showRTotal);
        Prefs.set(OPT_SHOW_R_GT_T, showRForGtT);
        Prefs.set(OPT_SHOW_R_LT_T, showRForLtT);
        Prefs.set(OPT_SHOW_MANDERS, showManders);
        Prefs.set(OPT_SHOW_MANDERS_GT_T, showMandersGtT);
        Prefs.set(OPT_SHOW_N_COLOC, showNColoc);
        Prefs.set(OPT_SHOW_VOLUME_COLOC, showVolumeColoc);
        Prefs.set(OPT_SHOW_VOLUME_GT_T_COLOC, showVolumeGtTColoc);
        Prefs.set(OPT_SHOW_INTENSITY_COLOC, showIntensityColoc);
        Prefs.set(OPT_SHOW_INTENSITY_GT_T_COLOC, showIntensityGtTColoc);
        Prefs.set(OPT_SHOW_ROIS_AND_MASKS, showRoisAndMasks);
        Prefs.set(OPT_EXHAUSTIVE_SEARCH, exhaustiveSearch);
        Prefs.set(OPT_PLOT_R_VALUES, plotRValues);
        Prefs.set(OPT_MAX_ITERATIONS, maxIterations);
    }

    private boolean parametersReady()
    {
        if (channel1List.getItemCount() == 0)
        {
            IJ.showMessage(TITLE, "No available images. Images must be 8-bit or 16-bit grayscale.");
            return false;
        }

        return ((channel1List.getSelectedIndex() != -1) && (channel2List.getSelectedIndex() != -1) &&
                (channel1List.getItemCount() != 0) && (channel2List.getItemCount() != 0));
    }

    /**
     * Fill the image list with currently open images.
     */
    public void fillImagesList()
    {
        // Find the currently open images
        final ArrayList<String> newImageList = new ArrayList<>();

        for (final int id : uk.ac.sussex.gdsc.core.ij.ImageJUtils.getIdList())
        {
            final ImagePlus imp = WindowManager.getImage(id);

            // Image must be 8-bit/16-bit
            if (imp != null && (imp.getType() == ImagePlus.GRAY8 || imp.getType() == ImagePlus.GRAY16))
            {
                // Exclude previous results
                if (previousResult(imp.getTitle()))
                    continue;

                newImageList.add(imp.getTitle());
            }
        }

        // Check if the image list has changed
        if (imageList.equals(newImageList))
            return;

        imageList = newImageList;

        // Re-populate the image lists
        channel1List.removeAll();
        channel2List.removeAll();

        for (final String imageTitle : newImageList)
        {
            channel1List.add(imageTitle);
            channel2List.add(imageTitle);
        }

        // Ensure the drop-downs are resized
        pack();

        // Restore previous selection
        if ((channel1SelectedIndex < channel1List.getItemCount()) && (channel1SelectedIndex >= 0))
            channel1List.select(channel1SelectedIndex);
        if ((channel2SelectedIndex < channel2List.getItemCount()) && (channel2SelectedIndex >= 0))
            channel2List.select(channel2SelectedIndex);

        roiList.select(roiIndex);
    }

    private static boolean previousResult(String title)
    {
        for (final String resultTitle : resultsTitles)
            if (title.startsWith(resultTitle))
                return true;
        return false;
    }

    private void createFrame()
    {
        final Panel mainPanel = new Panel();
        final GridLayout mainGrid = new GridLayout(0, 1);
        mainGrid.setHgap(10);
        mainGrid.setVgap(10);
        mainPanel.setLayout(mainGrid);
        add(mainPanel);

        channel1List = new Choice();
        mainPanel.add(createChoicePanel(channel1List, choiceChannel1));

        channel2List = new Choice();
        mainPanel.add(createChoicePanel(channel2List, choiceChannel2));

        roiList = new Choice();
        mainPanel.add(createChoicePanel(roiList, choiceUseRoi));
        roiList.add(choiceNoROI);
        roiList.add(choiceChannel1);
        roiList.add(choiceChannel2);

        searchToleranceTextField = new TextField();
        mainPanel.add(createTextPanel(searchToleranceTextField, choiceSearchTolerance, "" + searchTolerance));

        rThresholdTextField = new TextField();
        mainPanel.add(createTextPanel(rThresholdTextField, choiceRThreshold, "" + rThreshold));

        showColocalisedCheckbox = new Checkbox();
        mainPanel.add(createCheckboxPanel(showColocalisedCheckbox, choiceShowColocalised, showColocalised));

        useConstantIntensityCheckbox = new Checkbox();
        mainPanel.add(
                createCheckboxPanel(useConstantIntensityCheckbox, choiceUseConstantIntensity, useConstantIntensity));

        showScatterPlotCheckbox = new Checkbox();
        mainPanel.add(createCheckboxPanel(showScatterPlotCheckbox, choiceShowScatterPlot, showScatterPlot));

        includeZeroZeroPixelsCheckbox = new Checkbox();
        mainPanel.add(
                createCheckboxPanel(includeZeroZeroPixelsCheckbox, choiceIncludeZeroZeroPixels, includeZeroZeroPixels));

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

    private Panel createChoicePanel(Choice list, String label)
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

    private Panel createTextPanel(TextField textField, String label, String value)
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

    private Panel createCheckboxPanel(Checkbox checkbox, String label, boolean state)
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

    private void plotResults(ArrayList<ColocalisationThreshold.ThresholdResult> results)
    {
        if (results == null)
            return;

        final double[] threshold = new double[results.size()];
        final double[] R = new double[results.size()];
        final double[] R2 = new double[results.size()];

        double yMin = 1, yMax = -1;

        // Plot the zero threshold result at the minimum threshold value
        int minThreshold = Integer.MAX_VALUE;
        int zeroThresholdIndex = 0;

        for (int i = 0, j = results.size() - 1; i < results.size(); i++, j--)
        {
            final ColocalisationThreshold.ThresholdResult result = results.get(i);
            threshold[j] = result.threshold1;
            R[j] = result.r1;
            R2[j] = result.r2;
            if (yMin > R[j])
                yMin = R[j];
            if (yMin > R2[j])
                yMin = R2[j];
            if (yMax < R[j])
                yMax = R[j];
            if (yMax < R2[j])
                yMax = R2[j];
            if (result.threshold1 == 0)
                zeroThresholdIndex = j;
            else if (minThreshold > result.threshold1)
                minThreshold = result.threshold1;
        }
        threshold[zeroThresholdIndex] = (minThreshold > 0) ? minThreshold - 1 : 0;

        if (rPlot != null && rPlot.isVisible())
            rPlot.close();
        final Plot plot = new Plot(correlationValuesTitle, "Threshold", "R", threshold, R);
        plot.setLimits(threshold[0], threshold[threshold.length - 1], yMin, yMax);
        plot.setColor(Color.BLUE);
        plot.draw();
        plot.setColor(Color.RED);
        plot.addPoints(threshold, R2, Plot.CROSS);
        plot.setColor(Color.BLACK);
        plot.addLabel(0, 0, "Blue=C1+C2 above threshold; Red=Ch1/Ch2 below threshold");
        rPlot = plot.show();
    }
}
