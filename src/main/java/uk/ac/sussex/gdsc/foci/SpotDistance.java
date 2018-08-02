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

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.MacroInstaller;
import ij.plugin.PlugIn;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.text.TextPanel;
import ij.text.TextWindow;
import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.ij.Utils;
import uk.ac.sussex.gdsc.core.match.MatchCalculator;
import uk.ac.sussex.gdsc.core.match.PointPair;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold;
import uk.ac.sussex.gdsc.ij.plugin.filter.DifferenceOfGaussians;

/**
 * Output the distances between spots within a mask region.
 * <P>
 * For each mask region a Difference of Gaussians is performed to enhance spot features. The spots are then identified
 * and the distances between spots are calculated.
 * <p>
 * A mask can be defined using a freehand ROI or provided as an image. All pixels with the same non-zero value are used
 * to define a mask region. The image can thus be used to define multiple regions using pixel values 1,2,3, etc.
 */
public class SpotDistance implements PlugIn
{
    /**
     * Used to store results and cache XYZ output formatting
     */
    private class DistanceResult extends BasePoint implements Comparable<DistanceResult>
    {
        int id;
        double circularity;
        String pixelXYZ;
        String calXYZ;

        public DistanceResult(int x, int y, int z, double circularity)
        {
            super(x, y, z);
            this.circularity = circularity;
        }

        String getPixelXYZ()
        {
            if (pixelXYZ == null)
                pixelXYZ = String.format("%d\t%d\t%d", x, y, z);
            return pixelXYZ;
        }

        String getCalXYZ()
        {
            if (calXYZ == null)
                calXYZ = String.format("%s\t%s\t%s", Utils.rounded(x * cal.pixelWidth),
                        Utils.rounded(y * cal.pixelHeight), Utils.rounded(z * cal.pixelDepth));
            return calXYZ;
        }

        /*
         * (non-Javadoc)
         *
         * @see java.lang.Comparable#compareTo(java.lang.Object)
         */
        @Override
        public int compareTo(DistanceResult paramT)
        {
            // Sort by ID ascending
            return id - paramT.id;
        }
    }

    /** The plugin title. */
    private static final String TITLE = "Spot Distance";
    private static TextWindow resultsWindow = null;
    private static TextWindow summaryWindow = null;
    private static TextWindow distancesWindow = null;

    private static int lastResultLineCount = 0;
    private static int lastSummaryLineCount = 0;
    private static int lastDistancesLineCount = 0;
    private static boolean allowUndo = false;

    private static String maskImage = "";
    private static double smoothingSize = 1.5;
    private static double featureSize = 4.5;

    private static boolean autoThreshold = true;
    private static double stdDevAboveBackground = 3;
    private static int minPeakSize = 15;
    private static double minPeakHeight = 5;
    private static int maxPeaks = 50;

    private static double circularityLimit = 0.7;
    private static int showOverlay = 2;
    private static String[] OVERLAY = new String[] { "None", "Slice position", "Entire stack" };
    private static double maxDistance = 0;
    private static boolean processFrames = false;
    private static boolean showProjection = false;
    private static boolean showDistances = false;
    private static boolean pixelDistances = true;
    private static boolean calibratedDistances = true;
    private static boolean trackObjects = false;
    private static int regionCounter = 1;
    private static boolean debug = false;

    private static boolean dualChannel = false;
    private static int c1 = 1, c2 = 2;

    private static ImagePlus debugSpotImp = null;
    private static ImagePlus debugSpotImp2 = null;
    private static ImagePlus debugMaskImp = null;

    private ImagePlus imp;
    private ImagePlus maskImp;
    private ImagePlus projectionImp;
    private String resultEntry = null;
    private Calibration cal;
    private double maxDistance2;
    private boolean doDualChannel;

    // Used to cache the mask region
    private int[] regions = null;
    private ImagePlus regionImp = null;
    private Rectangle bounds = new Rectangle();
    private Rectangle cachedBlurBounds = null;

    // Store the last frame results to allow primitive tracking
    private ArrayList<DistanceResult> prevResultsArray = null, prevResultsArray2 = null;

    @Override
    public void run(String arg)
    {
        UsageTracker.recordPlugin(this.getClass(), arg);

        boolean extraOptions = IJ.shiftKeyDown();

        if (arg != null && arg.equals("undo"))
        {
            undoLastResult();
            return;
        }

        if (arg != null && arg.equals("extra"))
            extraOptions = true;

        imp = WindowManager.getCurrentImage();

        if (null == imp)
        {
            IJ.noImage();
            return;
        }

        if (!FindFoci.isSupported(imp.getBitDepth()))
        {
            IJ.showMessage("Error", "Only " + FindFoci.getSupported() + " images are supported");
            return;
        }

        final boolean installOption = false; // TODO - Determine if the tool is already installed

        // Build a list of the open images
        final String[] maskImageList = buildMaskList(imp);

        final Roi roi = imp.getRoi();
        if (maskImageList.length == 1 && (roi == null || !roi.isArea()))
        {
            IJ.showMessage("Error", "No mask images and no area ROI");
            return;
        }

        if (arg == null || !arg.equals("redo")) // Allow repeat with same parameters
        {
            final GenericDialog gd = new GenericDialog(TITLE);

            gd.addMessage(
                    "Detects spots within a mask/ROI region\nand computes all-vs-all distances.\n(Hold shift for extra options)");

            gd.addChoice("Mask", maskImageList, maskImage);
            gd.addNumericField("Smoothing", smoothingSize, 2, 6, "pixels");
            gd.addNumericField("Feature_Size", featureSize, 2, 6, "pixels");
            if (extraOptions)
            {
                gd.addCheckbox("Auto_threshold", autoThreshold);
                gd.addNumericField("Background (SD > Av)", stdDevAboveBackground, 1);
                gd.addNumericField("Min_peak_size", minPeakSize, 0);
                gd.addNumericField("Min_peak_height", minPeakHeight, 2);
                gd.addNumericField("Max_peaks", maxPeaks, 2);
            }
            gd.addNumericField("Ciruclarity_limit", circularityLimit, 2);
            gd.addNumericField("Max_distance", maxDistance, 2, 6, "pixels");
            if (imp.getNFrames() != 1)
                gd.addCheckbox("Process_frames", processFrames);
            if (imp.getNChannels() != 1)
                gd.addCheckbox("Dual_channel", dualChannel);
            gd.addCheckbox("Show_projection", showProjection);
            gd.addCheckbox("Show_distances", showDistances);
            gd.addChoice("Show_overlay", OVERLAY, OVERLAY[showOverlay]);
            gd.addNumericField("Region_counter", regionCounter, 0);
            if (extraOptions)
                gd.addCheckbox("Show_spot_image (debugging)", debug);
            if (installOption)
                gd.addCheckbox("Install_tool", false);
            gd.addCheckbox("Pixel_distances", pixelDistances);
            gd.addCheckbox("Calibrated_distances", calibratedDistances);
            gd.addCheckbox("Track_objects", trackObjects);
            gd.addHelp(uk.ac.sussex.gdsc.help.URL.FIND_FOCI);

            gd.showDialog();
            if (!gd.wasOKed())
                return;

            maskImage = gd.getNextChoice();
            smoothingSize = gd.getNextNumber();
            featureSize = gd.getNextNumber();
            if (extraOptions)
            {
                autoThreshold = gd.getNextBoolean();
                stdDevAboveBackground = gd.getNextNumber();
                minPeakSize = (int) gd.getNextNumber();
                minPeakHeight = (int) gd.getNextNumber();
                maxPeaks = (int) gd.getNextNumber();
            }
            circularityLimit = gd.getNextNumber();
            maxDistance = gd.getNextNumber();
            if (imp.getNFrames() != 1)
                processFrames = gd.getNextBoolean();
            doDualChannel = false;
            if (imp.getNChannels() != 1)
                doDualChannel = dualChannel = gd.getNextBoolean();
            showProjection = gd.getNextBoolean();
            showDistances = gd.getNextBoolean();
            showOverlay = gd.getNextChoiceIndex();
            regionCounter = (int) gd.getNextNumber();
            if (extraOptions)
                debug = gd.getNextBoolean();
            else
                debug = false;
            pixelDistances = gd.getNextBoolean();
            calibratedDistances = gd.getNextBoolean();
            trackObjects = gd.getNextBoolean();
            if (installOption)
                if (gd.getNextBoolean())
                    installTool();
            if (!(pixelDistances || calibratedDistances))
                calibratedDistances = true;
        }

        maskImp = checkMask(imp, maskImage);

        if (maskImp == null)
            return;

        if (doDualChannel)
        {
            final GenericDialog gd = new GenericDialog(TITLE);
            gd.addMessage("Select the channels for dual channel analysis");

            final String[] list = new String[imp.getNChannels()];
            for (int i = 0; i < list.length; i++)
                list[i] = "Channel " + (i + 1);
            final int myC1 = (c1 > list.length) ? 0 : c1 - 1;
            int myC2 = (c2 > list.length) ? 0 : c2 - 1;
            if (myC1 == myC2)
                myC2 = (myC1 + 1) % list.length;
            gd.addChoice("Channel_1", list, list[myC1]);
            gd.addChoice("Channel_2", list, list[myC2]);
            gd.showDialog();
            if (!gd.wasOKed())
                return;

            c1 = gd.getNextChoiceIndex() + 1;
            c2 = gd.getNextChoiceIndex() + 1;

            if (c1 == c2)
                doDualChannel = false;
        }

        initialise();

        final int channel = (doDualChannel) ? c1 : imp.getChannel();
        final ImageStack imageStack = imp.getImageStack();
        final ImageStack maskStack = maskImp.getImageStack();

        // Cache the mask image. Only recreate the mask if using multiple frames
        ImageStack s2 = null;

        final int[] frames = buildFrameList();

        createProjection(frames, doDualChannel);

        for (int i = 0; i < frames.length; i++)
        {
            final int frame = frames[i];

            // Build an image and mask for the frame
            final ImageStack s1 = new ImageStack(imp.getWidth(), imp.getHeight(), imp.getNSlices());
            final ImageStack s1b = (doDualChannel) ? new ImageStack(imp.getWidth(), imp.getHeight(), imp.getNSlices())
                    : null;

            for (int slice = 1; slice <= imp.getNSlices(); slice++)
            {
                int stackIndex = imp.getStackIndex(channel, slice, frame);
                ImageProcessor ip = imageStack.getProcessor(stackIndex);
                s1.setPixels(ip.getPixels(), slice);
                if (doDualChannel)
                {
                    stackIndex = imp.getStackIndex(c2, slice, frame);
                    ip = imageStack.getProcessor(stackIndex);
                    s1b.setPixels(ip.getPixels(), slice);
                }
            }

            if (s2 == null)
            {
                s2 = new ImageStack(imp.getWidth(), imp.getHeight(), maskImp.getNSlices());

                // Only create a mask with 1 slice if necessary
                for (int slice = 1; slice <= maskImp.getNSlices(); slice++)
                {
                    final int maskChannel = (maskImp.getNChannels() > 1) ? channel : 1;
                    final int maskFrame = (maskImp.getNFrames() > 1) ? frame : 1;
                    final int maskSlice = (maskImp.getNSlices() > 1) ? slice : 1;

                    final int maskStackIndex = maskImp.getStackIndex(maskChannel, maskSlice, maskFrame);
                    final ImageProcessor maskIp = maskStack.getProcessor(maskStackIndex);

                    s2.setPixels(maskIp.getPixels(), slice);
                }
            }

            if (Utils.isInterrupted())
                return;

            exec(frame, channel, s1, s1b, s2);
            IJ.showProgress(i + 1, frames.length);

            if (Utils.isInterrupted())
                return;

            // Recreate the mask if using multiple frames
            if (maskImp.getNFrames() > 1)
                s2 = null;
        }
        resultsWindow.append("");

        if (showProjection)
            projectionImp.setSlice(1);
    }

    private static void undoLastResult()
    {
        if (allowUndo)
        {
            allowUndo = false;
            removeAfterLine(resultsWindow, lastResultLineCount);
            removeAfterLine(summaryWindow, lastSummaryLineCount);
            removeAfterLine(distancesWindow, lastDistancesLineCount);
        }
    }

    private static void removeAfterLine(TextWindow window, int line)
    {
        if (window != null && line > 0)
        {
            final TextPanel tp = window.getTextPanel();
            final int nLines = tp.getLineCount();
            if (line < nLines)
            {
                tp.setSelection(line, nLines);
                tp.clearSelection();
            }
        }
    }

    private void initialise()
    {
        regions = null;
        regionImp = null;
        bounds = new Rectangle();
        allowUndo = false;
        maxDistance2 = maxDistance * maxDistance;
        if (showOverlay > 0)
            imp.setOverlay(null);
        createResultsWindow();
    }

    private int[] buildFrameList()
    {
        int[] frames;
        if (processFrames)
        {
            frames = new int[imp.getNFrames()];
            for (int i = 0; i < frames.length; i++)
                frames[i] = i + 1;
        }
        else
            frames = new int[] { imp.getFrame() };
        return frames;
    }

    private void createProjection(int[] frames, boolean dualChannel)
    {
        if (!showProjection)
            return;

        final ImageStack stack = imp.createEmptyStack();
        final ImageProcessor ip = imp.getProcessor();
        for (final int f : frames)
            stack.addSlice("Frame " + f, ip.createProcessor(ip.getWidth(), ip.getHeight()));
        if (dualChannel)
            for (final int f : frames)
                stack.addSlice("Frame " + f, ip.createProcessor(ip.getWidth(), ip.getHeight()));

        final String title = imp.getTitle() + " Spots Projection";
        projectionImp = WindowManager.getImage(title);
        final int nChannels = (dualChannel) ? 2 : 1;
        if (projectionImp == null)
            projectionImp = new ImagePlus(title, stack);
        else
            projectionImp.setStack(stack);
        projectionImp.setSlice(1);
        projectionImp.setOverlay(null);
        projectionImp.setDimensions(nChannels, 1, frames.length);
        projectionImp.setOpenAsHyperStack(dualChannel);
        projectionImp.show();
    }

    private static String[] buildMaskList(ImagePlus imp)
    {
        final ArrayList<String> newImageList = new ArrayList<>();
        newImageList.add("[None]");

        for (final int id : uk.ac.sussex.gdsc.core.ij.Utils.getIDList())
        {
            final ImagePlus maskImp = WindowManager.getImage(id);
            // Mask image must:
            // - Not be the same image
            // - Match XY dimensions of the input image
            // - Math Z dimensions of input or else be a single image
            if (maskImp != null && maskImp.getID() != imp.getID() && maskImp.getWidth() == imp.getWidth() &&
                    maskImp.getHeight() == imp.getHeight() &&
                    (maskImp.getNSlices() == imp.getNSlices() || maskImp.getNSlices() == 1) &&
                    (maskImp.getNChannels() == imp.getNChannels() || maskImp.getNChannels() == 1) &&
                    (maskImp.getNFrames() == imp.getNFrames() || maskImp.getNFrames() == 1))
                newImageList.add(maskImp.getTitle());
        }

        return newImageList.toArray(new String[0]);
    }

    private ImagePlus checkMask(ImagePlus imp, String maskImage)
    {
        ImagePlus maskImp = WindowManager.getImage(maskImage);

        if (maskImp == null)
        {
            // Build a mask image using the input image ROI
            final Roi roi = imp.getRoi();
            if (roi == null || !roi.isArea())
            {
                IJ.showMessage("Error", "No region defined (use an area ROI or an input mask)");
                return null;
            }
            final ShortProcessor ip = new ShortProcessor(imp.getWidth(), imp.getHeight());
            ip.setValue(1);
            ip.setRoi(roi);
            ip.fill(roi);

            // Label each separate region with a different number
            labelRegions(ip);

            maskImp = new ImagePlus("Mask", ip);
        }

        if (imp.getNSlices() > 1 && maskImp.getNSlices() != 1 && maskImp.getNSlices() != imp.getNSlices())
        {
            IJ.showMessage("Error", "Mask region has incorrect slice dimension");
            return null;
        }
        if (imp.getNChannels() > 1 && maskImp.getNChannels() != 1 && maskImp.getNChannels() != imp.getNChannels())
        {
            IJ.showMessage("Error", "Mask region has incorrect channel dimension");
            return null;
        }
        if (imp.getNFrames() > 1 && processFrames && maskImp.getNFrames() != 1 &&
                maskImp.getNFrames() != imp.getNFrames())
        {
            IJ.showMessage("Error", "Mask region has incorrect frame dimension");
            return null;
        }

        return maskImp;
    }

    /**
     * Create the result window (if it is not available)
     */
    private void createResultsWindow()
    {
        final String resultsHeader = createResultsHeader();
        if (resultsWindow == null || !resultsWindow.isShowing() || !sameHeader(resultsWindow, resultsHeader))
            resultsWindow = new TextWindow(TITLE + " Positions", createResultsHeader(), "", 700, 300);
        final String summaryHeader = createSummaryHeader();
        if (summaryWindow == null || !summaryWindow.isShowing() || !sameHeader(summaryWindow, summaryHeader))
        {
            summaryWindow = new TextWindow(TITLE + " Summary", createSummaryHeader(), "", 700, 300);
            final Point p = resultsWindow.getLocation();
            p.y += resultsWindow.getHeight();
            summaryWindow.setLocation(p);
        }
        final String distancesHeader = createDistancesHeader();
        if (showDistances && (distancesWindow == null || !distancesWindow.isShowing() ||
                !sameHeader(distancesWindow, distancesHeader)))
        {
            distancesWindow = new TextWindow(TITLE + " Distances", createDistancesHeader(), "", 700, 300);
            final Point p = summaryWindow.getLocation();
            p.y += summaryWindow.getHeight();
            distancesWindow.setLocation(p);
        }

        lastResultLineCount = resultsWindow.getTextPanel().getLineCount();
        lastSummaryLineCount = summaryWindow.getTextPanel().getLineCount();
        lastDistancesLineCount = (showDistances) ? distancesWindow.getTextPanel().getLineCount() : 0;

        // Create the image result prefix
        final StringBuilder sb = new StringBuilder();
        sb.append(imp.getTitle()).append('\t');
        if (calibratedDistances)
        {
            cal = imp.getCalibration();
            sb.append(cal.getXUnit());
            if (!(cal.getYUnit().equalsIgnoreCase(cal.getXUnit()) && cal.getZUnit().equalsIgnoreCase(cal.getXUnit())))
                sb.append(" ").append(cal.getYUnit()).append(" ").append(cal.getZUnit());
        }
        else
            sb.append("pixels");
        sb.append('\t');
        resultEntry = sb.toString();
    }

    private static boolean sameHeader(TextWindow results, String header)
    {
        return results.getTextPanel().getColumnHeadings().equals(header);
    }

    private static String createResultsHeader()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("Image\t");
        sb.append("Units\t");
        sb.append("Frame\t");
        sb.append("Channel\t");
        sb.append("Region\t");
        if (pixelDistances)
        {
            sb.append("x (px)\t");
            sb.append("y (px)\t");
            sb.append("z (px)\t");
        }
        if (calibratedDistances)
        {
            sb.append("X\t");
            sb.append("Y\t");
            sb.append("Z\t");
        }
        sb.append("Circularity");
        return sb.toString();
    }

    private static String createSummaryHeader()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("Image\t");
        sb.append("Units\t");
        sb.append("Frame\t");
        sb.append("Channel\t");
        sb.append("Region\t");
        sb.append("Spots");
        if (pixelDistances)
        {
            sb.append('\t');
            sb.append("Min (px)\t");
            sb.append("Max (px)\t");
            sb.append("Av (px)");
        }
        if (calibratedDistances)
        {
            sb.append('\t');
            sb.append("Min\t");
            sb.append("Max\t");
            sb.append("Av");
        }
        return sb.toString();
    }

    private static String createDistancesHeader()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("Image\t");
        sb.append("Units\t");
        sb.append("Frame\t");
        sb.append("Channel\t");
        sb.append("Region");
        if (pixelDistances)
        {
            sb.append('\t');
            sb.append("X1 (px)\tY1 (px)\tZ1 (px)\t");
            sb.append("X2 (px)\tY2 (px)\tZ2 (px)\t");
            sb.append("Distance (px)");
        }
        if (calibratedDistances)
        {
            sb.append('\t');
            sb.append("X1\tY1\tZ1\t");
            sb.append("X2\tY2\tZ2\t");
            sb.append("Distance");
        }
        return sb.toString();
    }

    private void exec(int frame, int channel, ImageStack s1, ImageStack s1b, ImageStack s2)
    {
        //new ImagePlus("image " + frame, s1).show();
        //new ImagePlus("mask " + frame, s2).show();

        // Check the mask we are using.
        // We can use a ROI bounds for the Gaussian blur to increase speed.
        Rectangle blurBounds = cachedBlurBounds;
        if (regions == null)
            regions = findRegions(s2);
        if (regions.length == 0)
            return; // Q. Should anything be reported?

        if (regions.length == 1)
        {
            if (regionImp == null)
            {
                regionImp = extractRegion(s2, regions[0], bounds);
                regionImp = crop(regionImp, bounds);
            }
            blurBounds = bounds;
        }
        else if (blurBounds == null)
            // Create the bounds using the min and max of all the regions
            blurBounds = findBounds(s2);

        cachedBlurBounds = blurBounds;

        if (showProjection)
        {
            // Do Z-projection into the current image processor of the stack
            runZProjection(frame, 1, s1);
            if (s1b != null)
                runZProjection(frame, 2, s1b);
        }

        // Perform Difference of Gaussians to enhance the spot features if two radii are provided
        if (featureSize > 0)
        {
            s1 = runDifferenceOfGaussians(s1, blurBounds);
            if (s1b != null)
                s1b = runDifferenceOfGaussians(s1b, blurBounds);
        }
        // Just perform image smoothing
        else if (smoothingSize > 0)
        {
            s1 = runGaussianBlur(s1, blurBounds);
            if (s1b != null)
                s1b = runGaussianBlur(s1b, blurBounds);
        }

        final ImagePlus spotImp = new ImagePlus(null, s1);
        final ImagePlus spotImp2 = (s1b != null) ? new ImagePlus(null, s1b) : null;

        // Process each region with FindFoci

        // TODO - Optimise these settings
        // - Maybe need a global thresholding on the whole DoG image
        // - Try Float IP for the DoG image
        // - Bigger feature size for DoG?

        final FindFoci ff = new FindFoci();
        final int backgroundMethod = (autoThreshold) ? FindFociProcessor.BACKGROUND_AUTO_THRESHOLD
                : FindFociProcessor.BACKGROUND_STD_DEV_ABOVE_MEAN;
        final double backgroundParameter = stdDevAboveBackground;
        final String autoThresholdMethod = AutoThreshold.Method.OTSU.name;
        final int searchMethod = FindFociProcessor.SEARCH_ABOVE_BACKGROUND;
        final double searchParameter = 0;
        final int minSize = minPeakSize;
        final int peakMethod = FindFociProcessor.PEAK_ABSOLUTE;
        final int outputType = FindFociProcessor.OUTPUT_RESULTS_TABLE | FindFociProcessor.OUTPUT_MASK_PEAKS |
                FindFociProcessor.OUTPUT_MASK_ABOVE_SADDLE | FindFociProcessor.OUTPUT_MASK_NO_PEAK_DOTS;
        final int sortIndex = FindFociProcessor.SORT_MAX_VALUE;
        final int options = FindFociProcessor.OPTION_MINIMUM_ABOVE_SADDLE; // | FindFoci.OPTION_STATS_INSIDE;
        final double blur = 0;
        final int centreMethod = FindFoci.CENTRE_OF_MASS_ORIGINAL;
        final double centreParameter = 2;

        Overlay overlay = null, overlay2 = null;
        if (showOverlay > 0)
        {
            overlay = new Overlay();
            if (s1b != null)
                overlay2 = new Overlay();
        }

        for (final int region : regions)
        {
            // Cache the cropped mask if it has only one region
            if (regions.length != 1)
                regionImp = null;

            if (regionImp == null)
            {
                regionImp = extractRegion(s2, region, bounds);
                regionImp = crop(regionImp, bounds);
            }

            final ImagePlus croppedImp = crop(spotImp, bounds);
            ImagePlus croppedImp2 = null;
            final ImageStack stack = croppedImp.getImageStack();
            final float scale = scaleImage(stack);
            croppedImp.setStack(stack); // Updates the image bit depth
            final double peakParameter = Math.round(minPeakHeight * scale);
            double peakParameter2 = 0;

            if (s1b != null)
            {
                croppedImp2 = crop(spotImp2, bounds);
                final ImageStack stack2 = croppedImp2.getImageStack();
                final float scale2 = scaleImage(stack2);
                croppedImp2.setStack(stack2); // Updates the image bit depth
                peakParameter2 = Math.round(minPeakHeight * scale2);
            }

            if (debug)
                showSpotImage(croppedImp, croppedImp2);

            final FindFociResults ffResult = ff.findMaxima(croppedImp, regionImp, backgroundMethod, backgroundParameter,
                    autoThresholdMethod, searchMethod, searchParameter, maxPeaks, minSize, peakMethod, peakParameter,
                    outputType, sortIndex, options, blur, centreMethod, centreParameter, 1);
            FindFociResults results2 = null;

            if (Utils.isInterrupted())
                return;

            final ArrayList<DistanceResult> resultsArray = analyseResults(prevResultsArray, ff, croppedImp, ffResult,
                    frame, channel, overlay);
            for (final DistanceResult result : resultsArray)
                addResult(frame, channel, region, result);

            ArrayList<DistanceResult> resultsArray2 = null;
            if (s1b != null)
            {
                // Analyse the second channel
                results2 = ff.findMaxima(croppedImp2, regionImp, backgroundMethod, backgroundParameter,
                        autoThresholdMethod, searchMethod, searchParameter, maxPeaks, minSize, peakMethod,
                        peakParameter2, outputType, sortIndex, options, blur, centreMethod, centreParameter, 1);
                if (Utils.isInterrupted())
                    return;

                resultsArray2 = analyseResults(prevResultsArray2, ff, croppedImp2, results2, frame, c2, overlay2);
                for (final DistanceResult result : resultsArray2)
                    addResult(frame, c2, region, result);
            }

            if (trackObjects)
            {
                prevResultsArray = resultsArray;
                prevResultsArray2 = resultsArray2;
            }

            if (s1b == null)
            {
                // Single channel analysis
                final String ch = Integer.toString(channel);

                if (resultsArray.size() < 2)
                {
                    // No comparisons possible
                    addSummaryResult(frame, ch, region, resultsArray.size());
                    continue;
                }

                double minD = Double.POSITIVE_INFINITY, minD2 = Double.POSITIVE_INFINITY;
                double maxD = 0, maxD2 = 0;
                double sumD = 0, sumD2 = 0;
                int count = 0;
                for (int i = 0; i < resultsArray.size(); i++)
                {
                    final DistanceResult r1 = resultsArray.get(i);
                    for (int j = i + 1; j < resultsArray.size(); j++)
                    {
                        final DistanceResult r2 = resultsArray.get(j);
                        final double[] diff = new double[] { (r1.x - r2.x), (r1.y - r2.y), (r1.z - r2.z) };

                        // Ignore distances above the maximum
                        double diff2 = -1;
                        if (maxDistance2 != 0)
                        {
                            diff2 = diff[0] * diff[0] + diff[1] * diff[1] + diff[2] * diff[2];
                            if (diff2 > maxDistance2)
                                continue;
                        }

                        count++;

                        double d = 0, d2 = 0;
                        if (pixelDistances)
                        {
                            if (diff2 == -1)
                                diff2 = diff[0] * diff[0] + diff[1] * diff[1] + diff[2] * diff[2];
                            d = Math.sqrt(diff2);
                            if (d < minD)
                                minD = d;
                            if (d > maxD)
                                maxD = d;
                            sumD += d;
                        }

                        if (calibratedDistances)
                        {
                            diff[0] *= cal.pixelWidth;
                            diff[1] *= cal.pixelHeight;
                            diff[2] *= cal.pixelDepth;
                            // TODO - This will not be valid if the units are not the same for all dimensions
                            d2 = Math.sqrt(diff[0] * diff[0] + diff[1] * diff[1] + diff[2] * diff[2]);
                            if (d2 < minD2)
                                minD2 = d2;
                            if (d2 > maxD2)
                                maxD2 = d2;
                            sumD2 += d2;
                        }

                        if (showDistances)
                            addDistanceResult(frame, ch, region, r1, r2, d, d2);
                    }
                }

                addSummaryResult(frame, ch, region, resultsArray.size(), minD, maxD, sumD / count, minD2, maxD2,
                        sumD2 / count);
            }
            else
            {
                // Dual channel analysis
                final String ch = channel + " + " + c2;

                if (resultsArray.isEmpty() || resultsArray2.isEmpty())
                {
                    // No comparisons possible
                    addSummaryResult(frame, ch, region, resultsArray.size() + resultsArray2.size());
                    continue;
                }

                double minD = Double.POSITIVE_INFINITY, minD2 = Double.POSITIVE_INFINITY;
                double maxD = 0, maxD2 = 0;
                double sumD = 0, sumD2 = 0;
                int count = 0;
                for (final DistanceResult r1 : resultsArray)
                    for (final DistanceResult r2 : resultsArray2)
                    {
                        final double[] diff = new double[] { (r1.x - r2.x), (r1.y - r2.y), (r1.z - r2.z) };

                        // Ignore distances above the maximum
                        double diff2 = -1;
                        if (maxDistance2 != 0)
                        {
                            diff2 = diff[0] * diff[0] + diff[1] * diff[1] + diff[2] * diff[2];
                            if (diff2 > maxDistance2)
                                continue;
                        }

                        count++;

                        double d = 0, d2 = 0;
                        if (pixelDistances)
                        {
                            if (diff2 == -1)
                                diff2 = diff[0] * diff[0] + diff[1] * diff[1] + diff[2] * diff[2];
                            d = Math.sqrt(diff2);
                            if (d < minD)
                                minD = d;
                            if (d > maxD)
                                maxD = d;
                            sumD += d;
                        }

                        if (calibratedDistances)
                        {
                            diff[0] *= cal.pixelWidth;
                            diff[1] *= cal.pixelHeight;
                            diff[2] *= cal.pixelDepth;
                            // TODO - This will not be valid if the units are not the same for all dimensions
                            d2 = Math.sqrt(diff[0] * diff[0] + diff[1] * diff[1] + diff[2] * diff[2]);
                            if (d2 < minD2)
                                minD2 = d2;
                            if (d2 > maxD2)
                                maxD2 = d2;
                            sumD2 += d2;
                        }

                        if (showDistances)
                            addDistanceResult(frame, ch, region, r1, r2, d, d2);
                    }

                addSummaryResult(frame, ch, region, resultsArray.size() + resultsArray2.size(), minD, maxD,
                        sumD / count, minD2, maxD2, sumD2 / count);
            }
        }

        // Append to image overlay
        if (showOverlay > 0)
        {
            Overlay impOverlay = imp.getOverlay();
            if (impOverlay == null)
                impOverlay = new Overlay();
            addToOverlay(impOverlay, overlay, Color.magenta);
            if (overlay2 != null)
                addToOverlay(impOverlay, overlay2, Color.yellow);
            imp.setOverlay(impOverlay);
            imp.updateAndDraw();
        }

        if (showProjection)
        {
            // Add all the ROIs of the overlay to the slice
            Overlay projOverlay = projectionImp.getOverlay();
            if (projOverlay == null)
                projOverlay = new Overlay();

            addToProjectionOverlay(projOverlay, overlay, frame, 1);
            if (overlay2 != null)
                addToProjectionOverlay(projOverlay, overlay2, frame, 2);

            final int index = projectionImp.getStackIndex(1, 1, frame);
            projectionImp.setSlice(index);
            projectionImp.setOverlay(projOverlay);
            projectionImp.updateAndDraw();
        }

        // Cache the mask only if it has one frame
        if (maskImp.getNFrames() > 1)
        {
            regions = null;
            regionImp = null;
            cachedBlurBounds = null;
        }
    }

    private void runZProjection(int frame, int channel, ImageStack s1)
    {
        final int index = projectionImp.getStackIndex(channel, 1, frame);
        projectionImp.setSliceWithoutUpdate(index);
        final ImageProcessor max = projectionImp.getProcessor();
        max.insert(s1.getProcessor(1), 0, 0);
        for (int n = 2; n <= s1.getSize(); n++)
        {
            final ImageProcessor ip = s1.getProcessor(n);
            for (int i = 0; i < ip.getPixelCount(); i++)
                if (max.get(i) < ip.get(i))
                    max.set(i, ip.get(i));
        }
    }

    private static ImageStack runDifferenceOfGaussians(ImageStack s1, Rectangle blurBounds)
    {
        final ImageStack newS1 = new ImageStack(s1.getWidth(), s1.getHeight(), s1.getSize());
        for (int slice = 1; slice <= s1.getSize(); slice++)
        {
            final ImageProcessor ip = s1.getProcessor(slice).toFloat(0, null);
            if (blurBounds != null)
                ip.setRoi(blurBounds);
            DifferenceOfGaussians.run(ip, featureSize, smoothingSize);
            newS1.setPixels(ip.getPixels(), slice);
        }
        s1 = newS1;
        return s1;
    }

    private static ImageStack runGaussianBlur(ImageStack s1, Rectangle blurBounds)
    {
        final DifferenceOfGaussians filter = new DifferenceOfGaussians();
        filter.noProgress = true;
        final ImageStack newS1 = new ImageStack(s1.getWidth(), s1.getHeight(), s1.getSize());
        for (int slice = 1; slice <= s1.getSize(); slice++)
        {
            final ImageProcessor ip = s1.getProcessor(slice).toFloat(0, null);
            if (blurBounds != null)
            {
                ip.setRoi(blurBounds);
                ip.snapshot(); // Needed to reset region outside ROI
            }
            filter.blurGaussian(ip, smoothingSize);
            newS1.setPixels(ip.getPixels(), slice);
        }
        s1 = newS1;
        return s1;
    }

    private static void addToOverlay(Overlay mainOverlay, Overlay overlay, Color color)
    {
        overlay.setFillColor(color);
        overlay.setStrokeColor(color);
        for (final Roi roi : overlay.toArray())
            mainOverlay.add(roi);
    }

    private void addToProjectionOverlay(Overlay mainOverlay, Overlay overlay, int frame, int channel)
    {
        if (projectionImp.getNFrames() == 1)
            frame = 1;
        overlay = overlay.duplicate();
        final Roi[] rois = overlay.toArray();
        for (final Roi roi : rois)
        {
            if (dualChannel)
                roi.setPosition(channel, 1, frame);
            else
                roi.setPosition(frame);
            mainOverlay.add(roi);
        }
    }

    /**
     * Scale the image to maximise the information available (since the FindFoci routine
     * uses integer values).
     * <p>
     * Only scale the image if the input image stack is a FloatProcessor. The stack will be converted to a
     * ShortProcessor.
     *
     * @param s1
     *            The image
     * @return The scale
     */
    private static float scaleImage(ImageStack s1)
    {
        if (!(s1.getPixels(1) instanceof float[]))
            return 1;

        // Find the range
        float min = Float.MAX_VALUE;
        float max = 0;
        for (int n = 1; n <= s1.getSize(); n++)
        {
            final float[] pixels = (float[]) s1.getPixels(n);
            for (final float f : pixels)
            {
                if (min > f)
                    min = f;
                if (max < f)
                    max = f;
            }
        }

        // Convert pixels to short[]
        final float range = max - min;
        final float scale = (range > 0) ? 65334 / range : 1;
        if (debug)
            IJ.log(String.format("Scaling transformed image by %f (range: %f - %f)", scale, min, max));
        for (int n = 1; n <= s1.getSize(); n++)
        {
            final float[] pixels = (float[]) s1.getPixels(n);
            final short[] newPixels = new short[pixels.length];
            for (int i = 0; i < pixels.length; i++)
                newPixels[i] = (short) Math.round((pixels[i] - min) * scale);
            s1.setPixels(newPixels, n);
        }
        return scale;
    }

    private void showSpotImage(ImagePlus croppedImp, ImagePlus croppedImp2)
    {
        debugSpotImp = createImage(croppedImp, debugSpotImp, TITLE + " Pixels", 0);
        if (croppedImp2 != null)
            debugSpotImp2 = createImage(croppedImp2, debugSpotImp2, TITLE + " Pixels B", 0);
        debugMaskImp = createImage(regionImp, debugMaskImp, TITLE + " Region", 1);
    }

    private ImagePlus createImage(ImagePlus sourceImp, ImagePlus debugImp, String title, int region)
    {
        if (debugImp == null || !debugImp.isVisible())
        {
            debugImp = new ImagePlus();
            debugImp.setTitle(title);
        }
        updateImage(debugImp, sourceImp, region);
        return debugImp;
    }

    private void updateImage(ImagePlus debugImp, ImagePlus sourceImp, int region)
    {
        debugImp.setStack(sourceImp.getImageStack(), 1, sourceImp.getStackSize(), 1);
        if (region > 0)
            debugImp.setDisplayRange(region - 1, region);
        else
            debugImp.resetDisplayRange();
        debugImp.updateAndDraw();
        debugImp.setCalibration(cal);
        debugImp.show();
    }

    private int[] findRegions(ImageStack s2)
    {
        // Build a histogram. Any non-zero value defines a region.
        final int[] histogram = s2.getProcessor(1).getHistogram();
        for (int slice = 2; slice <= maskImp.getNSlices(); slice++)
        {
            final int[] tmp = s2.getProcessor(slice).getHistogram();
            for (int i = 1; i < tmp.length; i++)
                histogram[i] += tmp[i];
        }

        int count = 0;
        for (int i = 1; i < histogram.length; i++)
            if (histogram[i] != 0)
                count++;

        final int[] regions = new int[count];
        count = 0;
        for (int i = 1; i < histogram.length; i++)
            if (histogram[i] != 0)
                regions[count++] = i;
        return regions;
    }

    private static ImagePlus extractRegion(ImageStack inputStack, int region, Rectangle bounds)
    {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = 0;
        int maxY = 0;
        final ImageStack outputStack = new ImageStack(inputStack.getWidth(), inputStack.getHeight(),
                inputStack.getSize());
        for (int slice = 1; slice <= inputStack.getSize(); slice++)
        {
            final ImageProcessor ip = inputStack.getProcessor(slice);
            final byte[] mask = new byte[inputStack.getWidth() * inputStack.getHeight()];
            for (int i = 0; i < ip.getPixelCount(); i++)
                if (ip.get(i) == region)
                {
                    mask[i] = 1;
                    // Calculate bounding rectangle
                    final int x = i % inputStack.getWidth();
                    final int y = i / inputStack.getWidth();
                    if (minX > x)
                        minX = x;
                    if (minY > y)
                        minY = y;
                    if (maxX < x)
                        maxX = x;
                    if (maxY < y)
                        maxY = y;
                }
            outputStack.setPixels(mask, slice);
        }

        bounds.x = minX;
        bounds.y = minY;
        bounds.width = maxX - minX + 1;
        bounds.height = maxY - minY + 1;
        return new ImagePlus(null, outputStack);
    }

    private static Rectangle findBounds(ImageStack inputStack)
    {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = 0;
        int maxY = 0;
        for (int slice = 1; slice <= inputStack.getSize(); slice++)
        {
            final ImageProcessor ip = inputStack.getProcessor(slice);
            for (int i = 0; i < ip.getPixelCount(); i++)
                if (ip.get(i) != 0)
                {
                    // Calculate bounding rectangle
                    final int x = i % inputStack.getWidth();
                    final int y = i / inputStack.getWidth();
                    if (minX > x)
                        minX = x;
                    if (minY > y)
                        minY = y;
                    if (maxX < x)
                        maxX = x;
                    if (maxY < y)
                        maxY = y;
                }
        }

        final Rectangle bounds = new Rectangle();
        bounds.x = minX;
        bounds.y = minY;
        bounds.width = maxX - minX + 1;
        bounds.height = maxY - minY + 1;
        return bounds;
    }

    /**
     * Crop by duplication within the bounds.
     *
     * @param imp
     *            the image
     * @param bounds
     *            the bounds
     * @return the image plus
     */
    private static ImagePlus crop(ImagePlus imp, Rectangle bounds)
    {
        imp.setRoi(bounds);
        final ImagePlus croppedImp = imp.duplicate();
        return croppedImp;
    }

    /**
     * Check the peak circularity. Add an overlay of the spots if requested.
     *
     * @param prev
     *            the prev
     * @param ff
     *            the ff
     * @param croppedImp
     *            the cropped imp
     * @param ffResult
     *            the ff result
     * @param frame
     *            the frame
     * @param channel
     *            the channel
     * @param overlay
     *            the overlay
     * @return the results
     */
    private ArrayList<DistanceResult> analyseResults(ArrayList<DistanceResult> prev, FindFoci ff, ImagePlus croppedImp,
            FindFociResults ffResult, int frame, int channel, Overlay overlay)
    {
        if (ffResult == null)
            return new ArrayList<>(0);

        final ImagePlus peaksImp = ffResult.mask;
        final ArrayList<FindFociResult> resultsArray = ffResult.results;

        final ArrayList<DistanceResult> newResultsArray = new ArrayList<>(resultsArray.size());

        // Process in XYZ order
        final FindFociBaseProcessor ffp = new FindFociIntProcessor();
        ffp.sortAscResults(resultsArray, FindFociProcessor.SORT_XYZ, null);

        final ImageStack maskStack = peaksImp.getImageStack();

        for (final FindFociResult result : resultsArray)
        {
            final int x = bounds.x + result.x;
            final int y = bounds.y + result.y;
            final int z = result.z;

            // Filter peaks on circularity (spots should be circles)
            // C = 4*pi*A / P^2
            // where A = Area, P = Perimeter
            // Q. Not sure if this will be valid for small spots.

            // Extract the peak.
            // This could extract by a % volume. At current just use the mask region.

            final ImageProcessor maskIp = maskStack.getProcessor(z + 1);

            //new ImagePlus("peak", fp.duplicate()).show();
            //new ImagePlus("mask", maskIp).show();

            final int peakId = result.id;
            final int maskId = resultsArray.size() - peakId + 1;
            final Roi roi = extractSelection(maskIp, maskId, channel, z + 1, frame);
            final double perimeter = roi.getLength();
            final double area = getArea(maskIp, maskId);

            final double circularity = perimeter == 0.0 ? 0.0 : 4.0 * Math.PI * (area / (perimeter * perimeter));

            if (circularity < circularityLimit)
            {
                IJ.log(String.format("Excluded non-circular peak @ x%d,y%d,z%d : %g (4pi * %g / %g^2)", x, y, z,
                        circularity, area, perimeter));
                continue;
            }

            if (overlay != null)
                //System.out.printf("%d, %d, %d\n", roi.getCPosition(), roi.getZPosition(), roi.getTPosition());
                overlay.add(roi);

            newResultsArray.add(new DistanceResult(x, y, z, circularity));
        }

        if (prev != null && !prev.isEmpty() && !newResultsArray.isEmpty())
        {
            // If we have results from a previous frame then try and assign an ID to each
            // result using closest distance tracking.
            final double d = Math.sqrt(
                    croppedImp.getWidth() * croppedImp.getWidth() + croppedImp.getHeight() * croppedImp.getHeight() +
                            croppedImp.getNSlices() * croppedImp.getNSlices());
            final List<PointPair> matches = new ArrayList<>();
            MatchCalculator.analyseResults3D(prev.toArray(new DistanceResult[prev.size()]),
                    newResultsArray.toArray(new DistanceResult[newResultsArray.size()]), d, null, null, null, matches);

            for (final PointPair match : matches)
                ((DistanceResult) match.getPoint2()).id = ((DistanceResult) match.getPoint1()).id;

            // Find max ID
            int id = 0;
            for (final DistanceResult r : newResultsArray)
                if (id < r.id)
                    id = r.id;
            // Assign those that could not be tracked
            for (final DistanceResult r : newResultsArray)
                if (r.id == 0)
                    r.id = ++id;
            // Sort
            Collections.sort(newResultsArray);
        }

        return newResultsArray;
    }

    private Roi extractSelection(ImageProcessor maskIp, int maskId, int channel, int slice, int frame)
    {
        maskIp.setThreshold(maskId, maskId, ImageProcessor.NO_LUT_UPDATE);
        final ThresholdToSelection ts = new ThresholdToSelection();
        final Roi roi = ts.convert(maskIp);
        if (imp.getStackSize() == 1)
            roi.setPosition(0);
        else if (imp.isDisplayedHyperStack())
        {
            if (showOverlay != 1)
                slice = 0; // Display across entire slice stack
            roi.setPosition(channel, slice, frame);
        }
        else
        {
            // We cannot support display across the entire stack if this is not a hyperstack
            final int index = imp.getStackIndex(channel, slice, frame);
            roi.setPosition(index);
        }
        final Rectangle roiBounds = roi.getBounds();
        roi.setLocation(bounds.x + roiBounds.x, bounds.y + roiBounds.y);
        return roi;
    }

    @SuppressWarnings("unused")
    private double getPerimeter(ImageProcessor maskIp, int maskId, Overlay overlay, int z)
    {
        maskIp.setThreshold(maskId, maskId, ImageProcessor.NO_LUT_UPDATE);
        final ThresholdToSelection ts = new ThresholdToSelection();
        final Roi roi = ts.convert(maskIp);
        if (overlay != null)
        {
            roi.setPosition(z);
            roi.setLocation(bounds.x, bounds.y);
            overlay.add(roi);
        }
        final double perimeter = roi.getLength();
        return perimeter;
    }

    private static double getArea(ImageProcessor maskIp, int maskId)
    {
        int area = 0;
        for (int i = 0; i < maskIp.getPixelCount(); i++)
            if (maskIp.get(i) == maskId)
                area++;
        return area;
    }

    private void addResult(int frame, int channel, int region, DistanceResult result)
    {
        final StringBuilder sb = new StringBuilder();
        sb.append(resultEntry);
        sb.append(frame).append('\t');
        sb.append(channel).append('\t');
        sb.append(region).append('\t');
        if (pixelDistances)
            sb.append(result.getPixelXYZ()).append('\t');
        if (calibratedDistances)
            sb.append(result.getCalXYZ()).append('\t');
        sb.append(Utils.rounded(result.circularity));
        resultsWindow.append(sb.toString());
        allowUndo = true;
    }

    private void addSummaryResult(int frame, String channel, int region, int size)
    {
        final StringBuilder sb = new StringBuilder();
        sb.append(resultEntry);
        sb.append(frame).append('\t');
        sb.append(channel).append('\t');
        sb.append(region).append('\t').append(size);
        if (pixelDistances)
            sb.append("\t0\t0\t0");
        if (calibratedDistances)
            sb.append("\t0\t0\t0");
        summaryWindow.append(sb.toString());
        allowUndo = true;
    }

    private void addSummaryResult(int frame, String channel, int region, int size, double minD, double maxD, double d,
            double minD2, double maxD2, double d2)
    {
        final StringBuilder sb = new StringBuilder();
        sb.append(resultEntry);
        sb.append(frame).append('\t');
        sb.append(channel).append('\t');
        sb.append(region).append('\t').append(size);
        if (pixelDistances)
        {
            sb.append('\t').append(Utils.rounded(minD));
            sb.append('\t').append(Utils.rounded(maxD));
            sb.append('\t').append(Utils.rounded(d));
        }
        if (calibratedDistances)
        {
            sb.append('\t').append(Utils.rounded(minD2));
            sb.append('\t').append(Utils.rounded(maxD2));
            sb.append('\t').append(Utils.rounded(d2));
        }
        summaryWindow.append(sb.toString());
        allowUndo = true;
    }

    private void addDistanceResult(int frame, String channel, int region, DistanceResult r1, DistanceResult r2,
            double d, double d2)
    {
        final StringBuilder sb = new StringBuilder();
        sb.append(resultEntry);
        sb.append(frame).append('\t');
        sb.append(channel).append('\t');
        sb.append(region);
        if (pixelDistances)
        {
            sb.append('\t').append(r1.getPixelXYZ());
            sb.append('\t').append(r2.getPixelXYZ());
            sb.append('\t').append(Utils.rounded(d));
        }
        if (calibratedDistances)
        {
            sb.append('\t').append(r1.getCalXYZ());
            sb.append('\t').append(r2.getCalXYZ());
            sb.append('\t').append(Utils.rounded(d2));
        }
        distancesWindow.append(sb.toString());
        allowUndo = true;
    }

    private void installTool()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("macro 'Spot Distance Action Tool - C00fo4233o6922oa644Cf00O00ff' {\n");
        sb.append("   call('").append(this.getClass().getName()).append(".run');\n");
        sb.append("};\n");
        sb.append("macro 'Spot Distance Action Tool Options' {\n");
        sb.append("   call('").append(this.getClass().getName()).append(".setOptions');\n");
        sb.append("};\n");
        new MacroInstaller().install(sb.toString());
    }

    /**
     * Run the SpotDistance plugin.
     */
    public static void run()
    {
        new SpotDistance().run(null);
    }

    /**
     * Run the SpotDistance plugin with the argument "redo".
     */
    public static void redo()
    {
        new SpotDistance().run("redo");
    }

    /**
     * Run the SpotDistance plugin with the argument "undo".
     */
    public static void undo()
    {
        new SpotDistance().run("undo");
    }

    /**
     * Run the SpotDistance plugin with the argument "extra".
     */
    public static void extra()
    {
        new SpotDistance().run("extra");
    }

    // Used for a particle search
    private static final int[] DIR_X_OFFSET = new int[] { 0, 1, 1, 1, 0, -1, -1, -1 };
    private static final int[] DIR_Y_OFFSET = new int[] { -1, -1, 0, 1, 1, 1, 0, -1 };
    private int maxx = 0, maxy;
    private int xlimit, ylimit;
    private int[] offset = null;

    /**
     * Convert the binary mask to labelled regions, each with a unique number.
     */
    private void labelRegions(ShortProcessor ip)
    {
        maxx = ip.getWidth();
        maxy = ip.getHeight();

        xlimit = maxx - 1;
        ylimit = maxy - 1;

        // Create the offset table (for single array 2D neighbour comparisons)
        offset = new int[DIR_X_OFFSET.length];
        for (int d = offset.length; d-- > 0;)
            offset[d] = maxx * DIR_Y_OFFSET[d] + DIR_X_OFFSET[d];

        final short[] mask = (short[]) ip.getPixels();
        final int[] pList = new int[mask.length];

        // Store all the non-zero positions
        final boolean[] binaryMask = new boolean[mask.length];
        for (int i = 0; i < binaryMask.length; i++)
            binaryMask[i] = (mask[i] != 0);

        // Find particles
        for (int i = 0; i < binaryMask.length; i++)
            if (binaryMask[i])
            {
                expandParticle(binaryMask, mask, pList, i, (short) regionCounter);
                regionCounter++;
            }

        // Free memory
        offset = null;
    }

    /**
     * Searches from the specified point to find all connected points and assigns them to given particle.
     */
    private void expandParticle(boolean[] binaryMask, short[] mask, int[] pList, int index0, final short particle)
    {
        binaryMask[index0] = false; // mark as processed
        int listI = 0; // index of current search element in the list
        int listLen = 1; // number of elements in the list

        // we create a list of connected points and start the list at the particle start position
        pList[listI] = index0;

        do
        {
            final int index1 = pList[listI];
            // Mark this position as part of the particle
            mask[index1] = particle;

            // Search the 8-connected neighbours
            final int x1 = index1 % maxx;
            final int y1 = index1 / maxx;

            final boolean isInnerXY = (x1 != 0 && x1 != xlimit) && (y1 != 0 && y1 != ylimit);

            if (isInnerXY)
                for (int d = 8; d-- > 0;)
                {
                    final int index2 = index1 + offset[d];
                    if (binaryMask[index2])
                    {
                        binaryMask[index2] = false; // mark as processed
                        // Add this to the search
                        pList[listLen++] = index2;
                    }
                }
            else
                for (int d = 8; d-- > 0;)
                    if (isInside(x1, y1, d))
                    {
                        final int index2 = index1 + offset[d];
                        if (binaryMask[index2])
                        {
                            binaryMask[index2] = false; // mark as processed
                            // Add this to the search
                            pList[listLen++] = index2;
                        }
                    }

            listI++;

        } while (listI < listLen);
    }

    /**
     * Returns whether the neighbour in a given direction is within the image. NOTE: it is assumed that the pixel x,y
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
    private boolean isInside(int x, int y, int direction)
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
        }
        return false;
    }
}
