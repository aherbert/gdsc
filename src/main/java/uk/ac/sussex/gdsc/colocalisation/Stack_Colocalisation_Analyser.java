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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.filter.PlugInFilter;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.text.TextWindow;
import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.colocalisation.cda.CDA_Plugin;
import uk.ac.sussex.gdsc.colocalisation.cda.TwinStackShifter;
import uk.ac.sussex.gdsc.core.ij.gui.ExtendedGenericDialog;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold;
import uk.ac.sussex.gdsc.core.utils.Correlator;

/**
 * Processes a stack image with multiple channels. Requires three channels. Each frame is processed separately. Extracts
 * all the channels (collating z-stacks) and performs:
 * (1). Thresholding to create a mask for each channel
 * (2). CDA analysis of channel 1 vs channel 2 within the region defined by channel 3.
 */
public class Stack_Colocalisation_Analyser implements PlugInFilter
{
    // Store a reference to the current working image
    private ImagePlus imp;

    private static TextWindow tw;
    private static String TITLE = "Stack Colocalisation Analyser";
    private static String COMBINE = "Combine 1+2";
    private static String NONE = "None";
    private boolean firstResult;

    // ImageJ indexes for the dimensions array
    // private final int X = 0;
    // private final int Y = 1;
    private final int C = 2;
    private final int Z = 3;
    private final int T = 4;

    private static String methodOption = AutoThreshold.Method.OTSU.name;
    private static int channel1 = 1;
    private static int channel2 = 2;
    private static int channel3 = 0;

    // Options flags
    private static boolean logThresholds = false;
    private static boolean logResults = false;
    private static boolean showMask = false;
    private static boolean subtractThreshold = false;

    private static int permutations = 100;
    private static int minimumRadius = 9;
    private static int maximumRadius = 16;
    private static double pCut = 0.05;

    private Correlator c;
    private int[] ii1, ii2;

    /** {@inheritDoc} */
    @Override
    public int setup(String arg, ImagePlus imp)
    {
        UsageTracker.recordPlugin(this.getClass(), arg);

        if (imp == null)
            return DONE;

        final int[] dimensions = imp.getDimensions();

        if (dimensions[2] < 2 || imp.getStackSize() < 2 || (imp.getBitDepth() != 8 && imp.getBitDepth() != 16))
        {
            if (IJ.isMacro())
                IJ.log("Multi-channel stack required (8-bit or 16-bit): " + imp.getTitle());
            else
                IJ.showMessage("Multi-channel stack required (8-bit or 16-bit)");
            return DONE;
        }

        this.imp = imp;

        return DOES_16 + DOES_8G + NO_CHANGES;
    }

    /** {@inheritDoc} */
    @Override
    public void run(ImageProcessor inputProcessor)
    {
        final int[] dimensions = imp.getDimensions();
        final int currentSlice = imp.getCurrentSlice();

        final String[] methods = getMethods();
        // channel3 is set within getMethods()
        final int nChannels = (channel3 != 1) ? 3 : 2;

        final int size = dimensions[0] * dimensions[1];
        c = new Correlator(size);
        ii1 = new int[size];
        ii2 = new int[size];

        for (final String method : methods)
        {
            if (logThresholds || logResults)
                IJ.log("Stack colocalisation (" + method + ") : " + imp.getTitle());

            ImageStack maskStack = null;
            ImagePlus maskImage = null;
            if (showMask)
            {
                // The stack will only have 3 channels
                maskStack = new ImageStack(imp.getWidth(), imp.getHeight(), nChannels * dimensions[Z] * dimensions[T]);

                // Ensure empty layers are filled to avoid ImageJ error creating ImagePlus
                final byte[] empty = new byte[maskStack.getWidth() * maskStack.getHeight()];
                Arrays.fill(empty, (byte) 255);
                maskStack.setPixels(empty, 1);

                maskImage = new ImagePlus(imp.getTitle() + ":" + method, maskStack);
                maskImage.setDimensions(nChannels, dimensions[Z], dimensions[T]);
            }

            for (int t = 1; t <= dimensions[T]; t++)
            {
                final ArrayList<SliceCollection> sliceCollections = new ArrayList<>();

                // Extract the channels
                for (int c = 1; c <= dimensions[C]; c++)
                {
                    // Process all slices together
                    final SliceCollection sliceCollection = new SliceCollection(c);
                    for (int z = 1; z <= dimensions[Z]; z++)
                        sliceCollection.add(imp.getStackIndex(c, z, t));
                    sliceCollections.add(sliceCollection);
                }

                // Get the channels:
                final SliceCollection s1 = sliceCollections.get(channel1 - 1);
                final SliceCollection s2 = sliceCollections.get(channel2 - 1);

                // Create masks
                extractImageAndCreateOutputMask(method, maskImage, 1, t, s1);
                extractImageAndCreateOutputMask(method, maskImage, 2, t, s2);

                // Note that channel 3 is offset by 1 because it contains the [none] option
                SliceCollection s3;
                if (channel3 > 1)
                {
                    s3 = sliceCollections.get(channel3 - 2);
                    extractImageAndCreateOutputMask(method, maskImage, 3, t, s3);
                }
                else
                {
                    s3 = new SliceCollection(0);
                    if (channel3 == 0)
                        // Combine the two masks
                        combineMasksAndCreateOutputMask(method, maskImage, 3, t, s1, s2, s3);
                }

                final double[] results = correlate(s1, s2, s3);

                reportResult(method, t, s1.getSliceName(), s2.getSliceName(), s3.getSliceName(), results);
            }

            if (showMask)
            {
                maskImage.show();
                IJ.run("Stack to Hyperstack...", "order=xyczt(default) channels=" + nChannels + " slices=" +
                        dimensions[Z] + " frames=" + dimensions[T] + " display=Color");
            }
        }
        imp.setSlice(currentSlice);
    }

    private void extractImageAndCreateOutputMask(String method, ImagePlus maskImage, int c, int t,
            SliceCollection sliceCollection)
    {
        sliceCollection.createStack(imp);
        sliceCollection.createMask(method);
        if (logThresholds)
            IJ.log("t" + t + sliceCollection.getSliceName() + " threshold = " + sliceCollection.threshold);

        if (showMask)
        {
            final ImageStack maskStack = maskImage.getImageStack();
            for (int s = 1; s <= sliceCollection.maskStack.getSize(); s++)
            {
                final int originalSliceNumber = sliceCollection.slices.get(s - 1);
                final int newSliceNumber = maskImage.getStackIndex(c, s, t);
                maskStack.setSliceLabel(method + ":" + imp.getStack().getSliceLabel(originalSliceNumber),
                        newSliceNumber);
                maskStack.setPixels(sliceCollection.maskStack.getPixels(s), newSliceNumber);
            }
        }
    }

    private static void combineMasksAndCreateOutputMask(String method, ImagePlus maskImage, int c, int t,
            SliceCollection s1, SliceCollection s2, SliceCollection sliceCollection)
    {
        sliceCollection.createMask(s1.maskStack, s2.maskStack);
        if (showMask)
        {
            final ImageStack maskStack = maskImage.getImageStack();
            for (int s = 1; s <= sliceCollection.maskStack.getSize(); s++)
            {
                final int newSliceNumber = maskImage.getStackIndex(c, s, t);
                maskStack.setSliceLabel(method + ":" + COMBINE, newSliceNumber);
                maskStack.setPixels(sliceCollection.maskStack.getPixels(s), newSliceNumber);
            }
        }
    }

    private String[] getMethods()
    {
        final ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);
        gd.addMessage(TITLE);
        firstResult = true;

        String[] indices = new String[imp.getNChannels()];
        for (int i = 1; i <= indices.length; i++)
            indices[i - 1] = "" + i;

        gd.addChoice("Channel_1", indices, channel1 - 1);
        gd.addChoice("Channel_2", indices, channel2 - 1);
        indices = addNoneOption(indices);
        gd.addChoice("Channel_3", indices, channel3);

        // Commented out the methods that take a long time on 16-bit images.
        String[] methods = { "Try all", AutoThreshold.Method.DEFAULT.name,
                // "Huang",
                // "Intermodes",
                // "IsoData",
                AutoThreshold.Method.LI.name, AutoThreshold.Method.MAX_ENTROPY.name, AutoThreshold.Method.MEAN.name,
                AutoThreshold.Method.MIN_ERROR_I.name,
                // "Minimum",
                AutoThreshold.Method.MOMENTS.name, AutoThreshold.Method.OTSU.name, AutoThreshold.Method.PERCENTILE.name,
                AutoThreshold.Method.RENYI_ENTROPY.name,
                // "Shanbhag",
                AutoThreshold.Method.TRIANGLE.name, AutoThreshold.Method.YEN.name, AutoThreshold.Method.NONE.name };

        gd.addChoice("Method", methods, methodOption);
        gd.addCheckbox("Log_thresholds", logThresholds);
        gd.addCheckbox("Log_results", logResults);
        gd.addCheckbox("Show_mask", showMask);
        gd.addCheckbox("Subtract threshold", subtractThreshold);
        gd.addNumericField("Permutations", permutations, 0);
        gd.addNumericField("Minimum_shift", minimumRadius, 0);
        gd.addNumericField("Maximum_shift", maximumRadius, 0);
        gd.addNumericField("Significance", pCut, 3);
        gd.addHelp(uk.ac.sussex.gdsc.help.URL.COLOCALISATION);

        gd.showDialog();
        if (gd.wasCanceled())
            return new String[0];

        channel1 = gd.getNextChoiceIndex() + 1;
        channel2 = gd.getNextChoiceIndex() + 1;
        channel3 = gd.getNextChoiceIndex();
        methodOption = gd.getNextChoice();
        logThresholds = gd.getNextBoolean();
        logResults = gd.getNextBoolean();
        showMask = gd.getNextBoolean();
        subtractThreshold = gd.getNextBoolean();
        permutations = (int) gd.getNextNumber();
        minimumRadius = (int) gd.getNextNumber();
        maximumRadius = (int) gd.getNextNumber();
        pCut = gd.getNextNumber();

        // Check parameters
        if (minimumRadius > maximumRadius)
            return failed("Minimum radius cannot be above maximum radius");
        if (maximumRadius > 255)
            return failed("Maximum radius cannot be above 255");
        if (pCut < 0 || pCut > 1)
            return failed("Significance must be between 0-1");

        if (!methodOption.equals("Try all"))
            // Ensure that the string contains known methods (to avoid passing bad macro arguments)
            methods = extractMethods(methodOption.split(" "), methods);
        else
        {
            // Shift the array to remove the try all option
            final String[] newMethods = new String[methods.length - 1];
            for (int i = 0; i < newMethods.length; i++)
                newMethods[i] = methods[i + 1];
            methods = newMethods;
        }

        if (methods.length == 0)
            return failed("No valid thresholding method(s) specified");

        return methods;
    }

    private static String[] addNoneOption(String[] indices)
    {
        final String[] newIndices = new String[indices.length + 2];
        newIndices[0] = COMBINE;
        newIndices[1] = NONE;
        for (int i = 0; i < indices.length; i++)
            newIndices[i + 2] = indices[i];
        return newIndices;
    }

    private static String[] failed(String message)
    {
        IJ.error(TITLE, message);
        return new String[0];
    }

    /**
     * Filtered the set of options using the allowed methods array.
     *
     * @param options
     *            the options
     * @param allowedMethods
     *            the allowed methods
     * @return filtered options
     */
    private static String[] extractMethods(String[] options, String[] allowedMethods)
    {
        final ArrayList<String> methods = new ArrayList<>();
        for (final String option : options)
            for (final String allowedMethod : allowedMethods)
                if (option.equals(allowedMethod))
                {
                    methods.add(option);
                    break;
                }
        return methods.toArray(new String[0]);
    }

    /**
     * Create the combination of the two stacks.
     *
     * @param stack1
     *            the stack 1
     * @param stack2
     *            the stack 2
     * @param operation
     *            the operation
     * @return the new stack
     */
    private static ImageStack combineBits(ImageStack stack1, ImageStack stack2, int operation)
    {
        final ImageStack newStack = new ImageStack(stack1.getWidth(), stack1.getHeight());
        for (int s = 1; s <= stack1.getSize(); s++)
            newStack.addSlice(null, combineBits(stack1.getProcessor(s), stack2.getProcessor(s), operation));
        return newStack;
    }

    /**
     * Create the combination of the two processors.
     *
     * @param ip1
     *            the image 1
     * @param ip2
     *            the image 2
     * @param operation
     *            the blitter operation
     * @return the new processor
     */
    private static ImageProcessor combineBits(ImageProcessor ip1, ImageProcessor ip2, int operation)
    {
        final ImageProcessor bp = ip1.duplicate();
        bp.copyBits(ip2, 0, 0, operation);
        return bp;
    }

    /**
     * Calculate the Mander's coefficients and Pearson correlation coefficient (R) between the two input channels within
     * the intersect of their masks.
     *
     * @param s1
     *            the s 1
     * @param s2
     *            the s 2
     * @param s3
     *            the s 3
     * @return an array containing: M1, M2, R, the number of overlapping pixels; the % total area for the overlap;
     */
    private double[] correlate(SliceCollection s1, SliceCollection s2, SliceCollection s3)
    {
        double m1Significant = 0, m2Significant = 0, rSignificant = 0;

        // Debug - show all the input
        //Utils.display("Stack Analyser Stack1", s1.imageStack);
        //Utils.display("Stack Analyser Stack2", s2.imageStack);
        //Utils.display("Stack Analyser ROI1", s1.maskStack);
        //Utils.display("Stack Analyser ROI2", s2.maskStack);
        //Utils.display("Stack Analyser Confined", s3.maskStack);

        // Calculate the total intensity within the channels, only counting regions in the channel mask and the ROI
        final double totalIntensity1 = getTotalIntensity(s1.imageStack, s1.maskStack, s3.maskStack);
        final double totalIntensity2 = getTotalIntensity(s2.imageStack, s2.maskStack, s3.maskStack);

        //System.out.printf("Stack Analyser total = %s (%s)  %s (%s) : %s)\n",
        //		totalIntensity1, getTotalIntensity(s1.maskStack, s1.maskStack, null),
        //		totalIntensity2, getTotalIntensity(s2.maskStack, s2.maskStack, null),
        //		getTotalIntensity(s3.maskStack, s3.maskStack, null));

        // Get the standard result
        final CalculationResult result = correlate(s1.imageStack, s1.maskStack, s2.imageStack, s2.maskStack,
                s3.maskStack, 0, totalIntensity1, totalIntensity2);

        if (permutations > 0)
        {
            // Circularly permute the s2 stack and compute the M1,M2,R stats.
            // Perform permutations within given distance limits, random n trials from all combinations.

            final int[] indices = CDA_Plugin.getRandomShiftIndices(minimumRadius, maximumRadius, permutations);

            final TwinStackShifter stackShifter = new TwinStackShifter(s2.imageStack, s2.maskStack, s3.maskStack);

            // Process only the specified number of permutations
            final ArrayList<CalculationResult> results = new ArrayList<>(indices.length);

            for (int n = indices.length; n-- > 0;)
            {
                final int index = indices[n];
                final int x = CDA_Plugin.getXShift(index);
                final int y = CDA_Plugin.getYShift(index);

                final double distance = Math.sqrt(x * x + y * y);

                stackShifter.setShift(x, y);
                stackShifter.run();

                results.add(correlate(s1.imageStack, s1.maskStack, stackShifter.getResultImage().getStack(),
                        stackShifter.getResultImage2().getStack(), s3.maskStack, distance, totalIntensity1,
                        totalIntensity2));
            }

            // Output if significant at given confidence level. Avoid bounds errors.
            final int upperIndex = (int) Math.min(results.size() - 1, Math.ceil(results.size() * (1 - pCut)));
            final int lowerIndex = (int) Math.floor(results.size() * pCut);

            Collections.sort(results, new M1Comparator());
            m1Significant = sig(results.get(lowerIndex).m1, result.m1, results.get(upperIndex).m1);
            Collections.sort(results, new M2Comparator());
            m2Significant = sig(results.get(lowerIndex).m2, result.m2, results.get(upperIndex).m2);
            Collections.sort(results, new RComparator());
            rSignificant = sig(results.get(lowerIndex).r, result.r, results.get(upperIndex).r);
        }

        return new double[] { result.m1, result.m2, result.r, result.n, result.area, m1Significant, m2Significant,
                rSignificant };
    }

    private static double sig(double lower, double value, double upper)
    {
        //System.out.printf("%g < %g < %g\n", lower, value, upper);
        if (value < lower)
            return -1;
        if (value > upper)
            return 1;
        return 0;
    }

    private static double getTotalIntensity(ImageStack imageStack, ImageStack maskStack, ImageStack maskStack2)
    {
        double total = 0;
        for (int s = 1; s <= imageStack.getSize(); s++)
        {
            final ImageProcessor ip1 = imageStack.getProcessor(s);
            final ImageProcessor ip2 = maskStack.getProcessor(s);
            final byte[] mask = (byte[]) ip2.getPixels();

            if (maskStack2 != null)
            {
                final ImageProcessor ip3 = maskStack2.getProcessor(s);
                final byte[] mask2 = (byte[]) ip3.getPixels();

                for (int i = mask.length; i-- > 0;)
                    if (mask[i] != 0 && mask2[i] != 0)
                        total += ip1.get(i);
            }
            else
                for (int i = mask.length; i-- > 0;)
                    if (mask[i] != 0)
                        total += ip1.get(i);
        }
        return total;
    }

    /**
     * Calculate the Mander's coefficients and Pearson correlation coefficient (R) between the two input channels within
     * the intersect of their masks. Only use the pixels within the roi mask.
     */
    private CalculationResult correlate(ImageStack image1, ImageStack mask1, ImageStack image2, ImageStack mask2,
            ImageStack roi, double distance, double totalIntensity1, double totalIntensity2)
    {
        final ImageStack overlapStack = combineBits(mask1, mask2, Blitter.AND);

        int nTotal = 0;

        c.clear();

        for (int s = 1; s <= overlapStack.getSize(); s++)
        {
            final ImageProcessor ip1 = image1.getProcessor(s);
            final ImageProcessor ip2 = image2.getProcessor(s);

            final ByteProcessor overlap = (ByteProcessor) overlapStack.getProcessor(s);
            final byte[] b = (byte[]) overlap.getPixels();

            int n = 0;
            if (roi != null)
            {
                // Calculate correlation within a specified region
                final ImageProcessor ip3 = roi.getProcessor(s);
                final byte[] mask = (byte[]) ip3.getPixels();

                for (int i = mask.length; i-- > 0;)
                    if (mask[i] != 0)
                    {
                        nTotal++;
                        if (b[i] != 0)
                        {
                            ii1[n] = ip1.get(i);
                            ii2[n] = ip2.get(i);
                            n++;
                        }
                    }
            }
            else
            {
                // Calculate correlation for entire image
                for (int i = ip1.getPixelCount(); i-- > 0;)
                    if (b[i] != 0)
                    {
                        ii1[n] = ip1.get(i);
                        ii2[n] = ip2.get(i);
                        n++;
                    }
                nTotal += ip1.getPixelCount();
            }
            c.add(ii1, ii2, n);
        }

        final double m1 = c.getSumX() / totalIntensity1;
        final double m2 = c.getSumY() / totalIntensity2;
        final double r = c.getCorrelation();

        return new CalculationResult(distance, m1, m2, r, c.getN(), (100.0 * c.getN() / nTotal));
    }

    /**
     * Reports the results for the correlation to the IJ log window
     *
     * @param t
     *            The timeframe
     * @param c1
     *            Channel 1 title
     * @param c2
     *            Channel 2 title
     * @param c3
     *            Channel 3 title
     * @param results
     *            The correlation results
     */
    private void reportResult(String method, int t, String c1, String c2, String c3, double[] results)
    {
        createResultsWindow();

        final double m1 = results[0];
        final double m2 = results[1];
        final double r = results[2];
        final int n = (int) results[3];
        final double area = results[4];
        final String m1Significant = getResult(results[5]);
        final String m2Significant = getResult(results[6]);
        final String rSignificant = getResult(results[7]);

        final char spacer = (logResults) ? ',' : '\t';

        final StringBuilder sb = new StringBuilder();
        sb.append(imp.getTitle()).append(spacer);
        sb.append(IJ.d2s(pCut, 4)).append(spacer);
        sb.append(method).append(spacer);
        sb.append(t).append(spacer);
        sb.append(c1).append(spacer);
        sb.append(c2).append(spacer);
        sb.append(c3).append(spacer);
        sb.append(n).append(spacer);
        sb.append(IJ.d2s(area, 2)).append("%").append(spacer);
        sb.append(IJ.d2s(m1, 4)).append(spacer);
        sb.append(m1Significant).append(spacer);
        sb.append(IJ.d2s(m2, 4)).append(spacer);
        sb.append(m2Significant).append(spacer);
        sb.append(IJ.d2s(r, 4)).append(spacer);
        sb.append(rSignificant);

        if (logResults)
            IJ.log(sb.toString());
        else
            tw.append(sb.toString());
    }

    private static String getResult(double d)
    {
        if (d < 0)
            return "Non-colocated";
        if (d > 0)
            return "Colocated";
        return "-";
    }

    private void createResultsWindow()
    {
        if (logResults)
        {
            if (firstResult)
            {
                firstResult = false;
                IJ.log("Image,p,Method,Frame,Ch1,Ch2,Ch3,n,Area,M1,Sig,M2,Sig,R,Sig");
            }
        }
        else if (tw == null || !tw.isShowing())
            tw = new TextWindow(TITLE + " Results",
                    "Image\tp\tMethod\tFrame\tCh1\tCh2\tCh3\tn\tArea\tM1\tSig\tM2\tSig\tR\tSig", "", 700, 300);
    }

    /**
     * Provides functionality to process a collection of slices from an Image
     */
    private class SliceCollection
    {
        int c;
        int z;
        ArrayList<Integer> slices;

        private String sliceName = null;

        ImageStack imageStack = null;
        ImageStack maskStack = null;
        int threshold = 0;

        /**
         * @param c
         *            The channel
         */
        SliceCollection(int c)
        {
            this.c = c;
            this.z = 0;
            slices = new ArrayList<>();
        }

        /**
         * Utility method.
         *
         * @param i
         *            the i
         */
        void add(Integer i)
        {
            slices.add(i);
        }

        /**
         * Gets the slice name.
         *
         * @return the slice name
         */
        String getSliceName()
        {
            if (sliceName == null || sliceName == NONE)
                if (slices.isEmpty())
                    sliceName = NONE;
                else
                {
                    final StringBuilder sb = new StringBuilder();
                    sb.append("c").append(c);
                    if (z != 0)
                        sb.append("z").append(z);
                    sliceName = sb.toString();
                }
            return sliceName;
        }

        /**
         * Extracts the configured slices from the image into a stack.
         *
         * @param imp
         *            the image
         */
        void createStack(ImagePlus imp)
        {
            if (slices.isEmpty())
                return;

            imageStack = new ImageStack(imp.getWidth(), imp.getHeight());
            for (final int slice : slices)
            {
                imp.setSliceWithoutUpdate(slice);
                imageStack.addSlice(Integer.toString(slice), imp.getProcessor().duplicate());
            }
        }

        /**
         * Creates a mask using the specified thresholding method.
         *
         * @param method
         *            the method
         */
        private void createMask(String method)
        {
            if (slices.isEmpty())
                return;

            final boolean mySubtractThreshold;
            if (method != NONE)
            {
                // Create an aggregate histogram
                final int[] data = imageStack.getProcessor(1).getHistogram();
                int[] temp = new int[data.length];
                for (int s = 2; s <= imageStack.getSize(); s++)
                {
                    temp = imageStack.getProcessor(s).getHistogram();
                    for (int i = 0; i < data.length; i++)
                        data[i] += temp[i];
                }

                threshold = AutoThreshold.getThreshold(method, data);
                mySubtractThreshold = subtractThreshold && (threshold > 0);
            }
            else
                mySubtractThreshold = false;

            // Create a mask for each image in the stack
            maskStack = new ImageStack(imageStack.getWidth(), imageStack.getHeight());
            for (int s = 1; s <= imageStack.getSize(); s++)
            {
                final ByteProcessor bp = new ByteProcessor(imageStack.getWidth(), imageStack.getHeight());
                final ImageProcessor ip = imageStack.getProcessor(s);
                for (int i = bp.getPixelCount(); i-- > 0;)
                {
                    final int value = ip.get(i);
                    if (value > threshold)
                        bp.set(i, 255);
                    if (mySubtractThreshold)
                        ip.set(i, value - threshold);
                }
                maskStack.addSlice(null, bp);
            }
        }

        /**
         * Creates a mask by combining the two masks.
         *
         * @param stack1
         *            the stack 1
         * @param stack2
         *            the stack 2
         */
        private void createMask(ImageStack stack1, ImageStack stack2)
        {
            sliceName = COMBINE;
            maskStack = combineBits(stack1, stack2, Blitter.OR);
        }
    }

    /**
     * Used to store the calculation results of the intersection of two images.
     */
    public class CalculationResult
    {
        /** Shift distance */
        public double distance;
        /** Mander's 1 */
        public double m1;
        /** Mander's 2 */
        public double m2;
        /** Correlation */
        public double r;
        /** the number of overlapping pixels */
        public int n;
        /** the % total area for the overlap */
        public double area;

        /**
         * Instantiates a new calculation result.
         *
         * @param distance
         *            the shift distance
         * @param m1
         *            the Mander's 1
         * @param m2
         *            the Mander's 2
         * @param r
         *            the correlation
         * @param n
         *            the number of overlapping pixels
         * @param area
         *            the % total area for the overlap
         */
        public CalculationResult(double distance, double m1, double m2, double r, int n, double area)
        {
            this.distance = distance;
            this.m1 = m1;
            this.m2 = m2;
            this.r = r;
            this.n = n;
            this.area = area;
        }
    }

    /**
     * Compare the results using the Mander's 1 coefficient
     */
    private class M1Comparator implements Comparator<CalculationResult>
    {
        @Override
        public int compare(CalculationResult o1, CalculationResult o2)
        {
            if (o1.m1 < o2.m1)
                return -1;
            if (o1.m1 > o2.m1)
                return 1;
            return 0;
        }
    }

    /**
     * Compare the results using the Mander's 2 coefficient
     */
    private class M2Comparator implements Comparator<CalculationResult>
    {
        @Override
        public int compare(CalculationResult o1, CalculationResult o2)
        {
            if (o1.m2 < o2.m2)
                return -1;
            if (o1.m2 > o2.m2)
                return 1;
            return 0;
        }
    }

    /**
     * Compare the results using the correlation coefficient
     */
    private class RComparator implements Comparator<CalculationResult>
    {
        @Override
        public int compare(CalculationResult o1, CalculationResult o2)
        {
            if (o1.r < o2.r)
                return -1;
            if (o1.r > o2.r)
                return 1;
            return 0;
        }
    }
}
