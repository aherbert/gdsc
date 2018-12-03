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
package uk.ac.sussex.gdsc.ij.plugin;

import java.util.Arrays;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.ChannelSplitter;
import ij.plugin.PlugIn;
import ij.plugin.RGBStackMerge;
import ij.plugin.ZProjector;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

/**
 * This plugin performs a z-projection of the input stack. Type of
 * output image is same as type of input image.
 * <p>
 * Copied from {@link ij.plugin.ZProjector}.
 *
 * @author Patrick Kelly &lt;phkelly@ucsd.edu&gt;
 */
public class ZProjectorCopy implements PlugIn
{
    /** Use Average projection. */
    public static final int AVG_METHOD = 0;
    /** Use Max projection. */
    public static final int MAX_METHOD = 1;
    /** Use Min projection. */
    public static final int MIN_METHOD = 2;
    /** Use Sum projection. */
    public static final int SUM_METHOD = 3;
    /** Use Standard Deviation projection. */
    public static final int SD_METHOD = 4;
    /** Use Median projection. */
    public static final int MEDIAN_METHOD = 5;
    /** The available projection methods. */
    public static final String[] METHODS = { "Average Intensity", "Max Intensity", "Min Intensity", "Sum Slices",
            "Standard Deviation", "Median" };
    private static final String METHOD_KEY = "zproject.method";
    /** The method. */
    protected int method = (int) Prefs.get(METHOD_KEY, AVG_METHOD);

    /** Constant for byte images. */
    protected static final int BYTE_TYPE = 0;
    /** Constant for short images. */
    protected static final int SHORT_TYPE = 1;
    /** Constant for float images. */
    protected static final int FLOAT_TYPE = 2;

    private static final String lutMessage = "Stacks with inverter LUTs may not project correctly.\n" +
            "To create a standard LUT, invert the stack (Edit/Invert)\n" +
            "and invert the LUT (Image/Lookup Tables/Invert LUT).";

    /** Image to hold z-projection. */
    protected ImagePlus projImage = null;

    /** Image stack to project. */
    protected ImagePlus imp = null;

    /** Projection starts from this slice. */
    protected int startSlice = 1;
    /** Projection ends at this slice. */
    protected int stopSlice = 1;
    /** Project all time points? */
    protected boolean allTimeFrames = true;

    private String color = "";

    /** The is hyperstack flag. */
    protected boolean isHyperstack;

    /** The increment. */
    protected int increment = 1;

    /** The slice count. */
    protected int sliceCount;

    /**
     * Construction of ZProjector.
     */
    public ZProjectorCopy()
    {
    }

    /**
     * Construction of ZProjector with image to be projected.
     *
     * @param imp
     *            the image
     */
    public ZProjectorCopy(ImagePlus imp)
    {
        setImage(imp);
    }

    /**
     * Explicitly set image to be projected. This is useful if
     * ZProjection_ object is to be used not as a plugin but as a
     * stand alone processing object.
     *
     * @param imp
     *            the new image
     */
    public void setImage(ImagePlus imp)
    {
        this.imp = imp;
        startSlice = 1;
        stopSlice = imp.getStackSize();
    }

    /**
     * Sets the start slice.
     *
     * @param slice
     *            the new start slice
     */
    public void setStartSlice(int slice)
    {
        if (imp == null || slice < 1 || slice > imp.getStackSize())
            return;
        startSlice = slice;
    }

    /**
     * Sets the stop slice.
     *
     * @param slice
     *            the new stop slice
     */
    public void setStopSlice(int slice)
    {
        if (imp == null || slice < 1 || slice > imp.getStackSize())
            return;
        stopSlice = slice;
    }

    /**
     * Sets the method.
     *
     * @param projMethod
     *            the new method
     */
    public void setMethod(int projMethod)
    {
        method = projMethod;
    }

    /**
     * Retrieve results of most recent projection operation.
     *
     * @return the projection
     */
    public ImagePlus getProjection()
    {
        return projImage;
    }

    @Override
    public void run(String arg)
    {
        imp = IJ.getImage();
        final int stackSize = imp.getStackSize();
        if (imp == null)
        {
            IJ.noImage();
            return;
        }

        //  Make sure input image is a stack.
        if (stackSize == 1)
        {
            IJ.error("Z Project", "Stack required");
            return;
        }

        //  Check for inverting LUT.
        if (imp.getProcessor().isInvertedLut())
            if (!IJ.showMessageWithCancel("ZProjection", lutMessage))
                return;

        // Set default bounds.
        final int channels = imp.getNChannels();
        final int frames = imp.getNFrames();
        final int slices = imp.getNSlices();
        isHyperstack = imp.isHyperStack() || (ij.macro.Interpreter.isBatchMode() &&
                ((frames > 1 && frames < stackSize) || (slices > 1 && slices < stackSize)));
        final boolean simpleComposite = channels == stackSize;
        if (simpleComposite)
            isHyperstack = false;
        startSlice = 1;
        if (isHyperstack)
        {
            final int nSlices = imp.getNSlices();
            if (nSlices > 1)
                stopSlice = nSlices;
            else
                stopSlice = imp.getNFrames();
        }
        else
            stopSlice = stackSize;

        // Build control dialog
        final GenericDialog gd = buildControlDialog(startSlice, stopSlice);
        gd.showDialog();
        if (gd.wasCanceled())
            return;

        if (!imp.lock())
            return; // exit if in use
        final long tstart = System.currentTimeMillis();
        setStartSlice((int) gd.getNextNumber());
        setStopSlice((int) gd.getNextNumber());
        method = gd.getNextChoiceIndex();
        Prefs.set(METHOD_KEY, method);
        if (isHyperstack)
        {
            allTimeFrames = imp.getNFrames() > 1 && imp.getNSlices() > 1 ? gd.getNextBoolean() : false;
            doHyperStackProjection(allTimeFrames);
        }
        else if (imp.getType() == ImagePlus.COLOR_RGB)
            doRGBProjection();
        else
            doProjection();

        if (arg.equals("") && projImage != null)
        {
            final long tstop = System.currentTimeMillis();
            projImage.setCalibration(imp.getCalibration());
            if (simpleComposite)
                IJ.run(projImage, "Grays", "");
            projImage.show("ZProjector: " + IJ.d2s((tstop - tstart) / 1000.0, 2) + " seconds");
        }

        imp.unlock();
        IJ.register(ZProjector.class);
        return;
    }

    /**
     * Do RGB projection.
     */
    public void doRGBProjection()
    {
        doRGBProjection(imp.getStack());
    }

    private void doRGBProjection(ImageStack stack)
    {
        final ImageStack[] channels = ChannelSplitter.splitRGB(stack, true);
        final ImagePlus red = new ImagePlus("Red", channels[0]);
        final ImagePlus green = new ImagePlus("Green", channels[1]);
        final ImagePlus blue = new ImagePlus("Blue", channels[2]);
        imp.unlock();
        final ImagePlus saveImp = imp;
        imp = red;
        color = "(red)";
        doProjection();
        final ImagePlus red2 = projImage;
        imp = green;
        color = "(green)";
        doProjection();
        final ImagePlus green2 = projImage;
        imp = blue;
        color = "(blue)";
        doProjection();
        final ImagePlus blue2 = projImage;
        final int w = red2.getWidth(), h = red2.getHeight(), d = red2.getStackSize();
        if (method == SD_METHOD)
        {
            final ImageProcessor r = red2.getProcessor();
            final ImageProcessor g = green2.getProcessor();
            final ImageProcessor b = blue2.getProcessor();
            double max = 0;
            final double rmax = r.getStatistics().max;
            if (rmax > max)
                max = rmax;
            final double gmax = g.getStatistics().max;
            if (gmax > max)
                max = gmax;
            final double bmax = b.getStatistics().max;
            if (bmax > max)
                max = bmax;
            final double scale = 255 / max;
            r.multiply(scale);
            g.multiply(scale);
            b.multiply(scale);
            red2.setProcessor(r.convertToByte(false));
            green2.setProcessor(g.convertToByte(false));
            blue2.setProcessor(b.convertToByte(false));
        }
        final RGBStackMerge merge = new RGBStackMerge();
        final ImageStack stack2 = merge.mergeStacks(w, h, d, red2.getStack(), green2.getStack(), blue2.getStack(),
                true);
        imp = saveImp;
        projImage = new ImagePlus(makeTitle(), stack2);
    }

    /**
     * Builds dialog to query users for projection parameters.
     *
     * @param start
     *            starting slice to display
     * @param stop
     *            last slice
     * @return the generic dialog
     */
    protected GenericDialog buildControlDialog(int start, int stop)
    {
        final GenericDialog gd = new GenericDialog("ZProjection", IJ.getInstance());
        gd.addNumericField("Start slice:", startSlice, 0/* digits */);
        gd.addNumericField("Stop slice:", stopSlice, 0/* digits */);
        gd.addChoice("Projection type", METHODS, METHODS[method]);
        if (isHyperstack && imp.getNFrames() > 1 && imp.getNSlices() > 1)
            gd.addCheckbox("All time frames", allTimeFrames);
        return gd;
    }

    /** Performs actual projection using specified method. */
    public void doProjection()
    {
        if (imp == null)
            return;
        sliceCount = 0;
        if (method < AVG_METHOD || method > MEDIAN_METHOD)
            method = AVG_METHOD;
        for (int slice = startSlice; slice <= stopSlice; slice += increment)
            sliceCount++;
        if (method == MEDIAN_METHOD)
        {
            projImage = doMedianProjection();
            return;
        }

        // Create new float processor for projected pixels.
        final FloatProcessor fp = new FloatProcessor(imp.getWidth(), imp.getHeight());
        final ImageStack stack = imp.getStack();
        final RayFunction rayFunc = getRayFunction(method, fp);
        if (IJ.debugMode == true)
            IJ.log("\nProjecting stack from: " + startSlice + " to: " + stopSlice);

        // Determine type of input image. Explicit determination of
        // processor type is required for subsequent pixel
        // manipulation.  This approach is more efficient than the
        // more general use of ImageProcessor's getPixelValue and
        // putPixel methods.
        int ptype;
        if (stack.getProcessor(1) instanceof ByteProcessor)
            ptype = BYTE_TYPE;
        else if (stack.getProcessor(1) instanceof ShortProcessor)
            ptype = SHORT_TYPE;
        else if (stack.getProcessor(1) instanceof FloatProcessor)
            ptype = FLOAT_TYPE;
        else
        {
            IJ.error("Z Project", "Non-RGB stack required");
            return;
        }

        // Do the projection.
        for (int n = startSlice; n <= stopSlice; n += increment)
        {
            IJ.showStatus("ZProjection " + color + ": " + n + "/" + stopSlice);
            IJ.showProgress(n - startSlice, stopSlice - startSlice);
            projectSlice(stack.getPixels(n), rayFunc, ptype);
        }

        // Finish up projection.
        if (method == SUM_METHOD)
        {
            fp.resetMinAndMax();
            projImage = new ImagePlus(makeTitle(), fp);
        }
        else if (method == SD_METHOD)
        {
            rayFunc.postProcess();
            fp.resetMinAndMax();
            projImage = new ImagePlus(makeTitle(), fp);
        }
        else
        {
            rayFunc.postProcess();
            projImage = makeOutputImage(imp, fp, ptype);
        }

        if (projImage == null)
            IJ.error("Z Project", "Error computing projection.");
    }

    /**
     * Do hyper stack projection.
     *
     * @param allTimeFrames
     *            the all time frames flag
     */
    public void doHyperStackProjection(boolean allTimeFrames)
    {
        final int start = startSlice;
        final int stop = stopSlice;
        int firstFrame = 1;
        int lastFrame = imp.getNFrames();
        if (!allTimeFrames)
            firstFrame = lastFrame = imp.getFrame();
        final ImageStack stack = new ImageStack(imp.getWidth(), imp.getHeight());
        final int channels = imp.getNChannels();
        int slices = imp.getNSlices();
        if (slices == 1)
        {
            slices = imp.getNFrames();
            firstFrame = lastFrame = 1;
        }
        final int frames = lastFrame - firstFrame + 1;
        increment = channels;
        final boolean rgb = imp.getBitDepth() == 24;
        for (int frame = firstFrame; frame <= lastFrame; frame++)
            for (int channel = 1; channel <= channels; channel++)
            {
                startSlice = (frame - 1) * channels * slices + (start - 1) * channels + channel;
                stopSlice = (frame - 1) * channels * slices + (stop - 1) * channels + channel;
                if (rgb)
                    doHSRGBProjection(imp);
                else
                    doProjection();
                stack.addSlice(null, projImage.getProcessor());
            }
        projImage = new ImagePlus(makeTitle(), stack);
        projImage.setDimensions(channels, 1, frames);
        if (channels > 1)
        {
            projImage = new CompositeImage(projImage, 0);
            ((CompositeImage) projImage).copyLuts(imp);
            if (method == SUM_METHOD || method == SD_METHOD)
                ((CompositeImage) projImage).resetDisplayRanges();
        }
        if (frames > 1)
            projImage.setOpenAsHyperStack(true);
        IJ.showProgress(1, 1);
    }

    private void doHSRGBProjection(ImagePlus rgbImp)
    {
        final ImageStack stack = rgbImp.getStack();
        final ImageStack stack2 = new ImageStack(stack.getWidth(), stack.getHeight());
        for (int i = startSlice; i <= stopSlice; i++)
            stack2.addSlice(null, stack.getProcessor(i));
        startSlice = 1;
        stopSlice = stack2.getSize();
        doRGBProjection(stack2);
    }

    private RayFunction getRayFunction(int method, FloatProcessor fp)
    {
        switch (method)
        {
            case AVG_METHOD:
            case SUM_METHOD:
                return new AverageIntensity(fp, sliceCount);
            case MAX_METHOD:
                return new MaxIntensity(fp);
            case MIN_METHOD:
                return new MinIntensity(fp);
            case SD_METHOD:
                return new StandardDeviation(fp, sliceCount);
            default:
                IJ.error("Z Project", "Unknown method.");
                return null;
        }
    }

    /**
     * Generate output image whose type is same as input image.
     *
     * @param imp
     *            the image
     * @param fp
     *            the image
     * @param ptype
     *            the ptype
     * @return the image plus
     */
    protected ImagePlus makeOutputImage(ImagePlus imp, FloatProcessor fp, int ptype)
    {
        final int width = imp.getWidth();
        final int height = imp.getHeight();
        final float[] pixels = (float[]) fp.getPixels();
        ImageProcessor oip = null;

        // Create output image consistent w/ type of input image.
        final int size = pixels.length;
        switch (ptype)
        {
            case BYTE_TYPE:
                oip = imp.getProcessor().createProcessor(width, height);
                final byte[] pixels8 = (byte[]) oip.getPixels();
                for (int i = 0; i < size; i++)
                    pixels8[i] = (byte) pixels[i];
                break;
            case SHORT_TYPE:
                oip = imp.getProcessor().createProcessor(width, height);
                final short[] pixels16 = (short[]) oip.getPixels();
                for (int i = 0; i < size; i++)
                    pixels16[i] = (short) pixels[i];
                break;
            case FLOAT_TYPE:
                oip = new FloatProcessor(width, height, pixels, null);
                break;
        }

        // Adjust for display.
        // Calling this on non-ByteProcessors ensures image
        // processor is set up to correctly display image.
        oip.resetMinAndMax();

        // Create new image plus object. Don't use
        // ImagePlus.createImagePlus here because there may be
        // attributes of input image that are not appropriate for
        // projection.
        return new ImagePlus(makeTitle(), oip);
    }

    /**
     * Handles mechanics of projection by selecting appropriate pixel
     * array type. We do this rather than using more general
     * ImageProcessor getPixelValue() and putPixel() methods because
     * direct manipulation of pixel arrays is much more efficient.
     */
    private static void projectSlice(Object pixelArray, RayFunction rayFunc, int ptype)
    {
        switch (ptype)
        {
            case BYTE_TYPE:
                rayFunc.projectSlice((byte[]) pixelArray);
                break;
            case SHORT_TYPE:
                rayFunc.projectSlice((short[]) pixelArray);
                break;
            case FLOAT_TYPE:
                rayFunc.projectSlice((float[]) pixelArray);
                break;
        }
    }

    /**
     * Make title.
     *
     * @return the string
     */
    protected String makeTitle()
    {
        String prefix = "AVG_";
        switch (method)
        {
            case SUM_METHOD:
                prefix = "SUM_";
                break;
            case MAX_METHOD:
                prefix = "MAX_";
                break;
            case MIN_METHOD:
                prefix = "MIN_";
                break;
            case SD_METHOD:
                prefix = "STD_";
                break;
            case MEDIAN_METHOD:
                prefix = "MED_";
                break;
        }
        return WindowManager.makeUniqueName(prefix + imp.getTitle());
    }

    /**
     * Do median projection.
     *
     * @return the image plus
     */
    protected ImagePlus doMedianProjection()
    {
        IJ.showStatus("Calculating median...");
        final ImageStack stack = imp.getStack();
        final ImageProcessor[] slices = new ImageProcessor[sliceCount];
        int index = 0;
        for (int slice = startSlice; slice <= stopSlice; slice += increment)
            slices[index++] = stack.getProcessor(slice);
        ImageProcessor ip2 = slices[0].duplicate();
        ip2 = ip2.convertToFloat();
        final float[] values = new float[sliceCount];
        final int width = ip2.getWidth();
        final int height = ip2.getHeight();
        final int inc = Math.max(height / 30, 1);
        for (int y = 0; y < height; y++)
        {
            if (y % inc == 0)
                IJ.showProgress(y, height - 1);
            for (int x = 0; x < width; x++)
            {
                for (int i = 0; i < sliceCount; i++)
                    values[i] = slices[i].getPixelValue(x, y);
                ip2.putPixelValue(x, y, median(values));
            }
        }
        if (imp.getBitDepth() == 8)
            ip2 = ip2.convertToByte(false);
        IJ.showProgress(1, 1);
        return new ImagePlus(makeTitle(), ip2);
    }

    /**
     * Compute the median.
     *
     * @param a
     *            the data
     * @return the float
     */
    protected float median(float[] a)
    {
        Arrays.sort(a);
        final int middle = a.length / 2;
        if ((a.length & 1) == 0) //even
            return (a[middle - 1] + a[middle]) / 2f;
        return a[middle];
    }

    /**
     * Abstract class that specifies structure of ray
     * function. Preprocessing should be done in derived class
     * constructors.
     */
    private abstract class RayFunction
    {
        abstract void projectSlice(byte[] pixels);

        abstract void projectSlice(short[] pixels);

        abstract void projectSlice(float[] pixels);

        /**
         * Perform any necessary post processing operations, e.g.
         * averaging values.
         */
        void postProcess()
        {
            // Do nothing
        }

    } // end RayFunction

    /** Compute average intensity projection. */
    private class AverageIntensity extends RayFunction
    {
        private final float[] fpixels;
        private final int num, len;

        /**
         * Constructor requires number of slices to be
         * projected. This is used to determine average at each
         * pixel.
         *
         * @param fp
         *            the image
         * @param num
         *            the number of slices
         */
        AverageIntensity(FloatProcessor fp, int num)
        {
            fpixels = (float[]) fp.getPixels();
            len = fpixels.length;
            this.num = num;
        }

        @Override
        void projectSlice(byte[] pixels)
        {
            for (int i = 0; i < len; i++)
                fpixels[i] += (pixels[i] & 0xff);
        }

        @Override
        void projectSlice(short[] pixels)
        {
            for (int i = 0; i < len; i++)
                fpixels[i] += pixels[i] & 0xffff;
        }

        @Override
        void projectSlice(float[] pixels)
        {
            for (int i = 0; i < len; i++)
                fpixels[i] += pixels[i];
        }

        @Override
        void postProcess()
        {
            final float fnum = num;
            for (int i = 0; i < len; i++)
                fpixels[i] /= fnum;
        }

    } // end AverageIntensity

    /** Compute max intensity projection. */
    private class MaxIntensity extends RayFunction
    {
        private final float[] fpixels;
        private final int len;

        /**
         * Simple constructor since no preprocessing is necessary.
         *
         * @param fp
         *            the image
         */
        MaxIntensity(FloatProcessor fp)
        {
            fpixels = (float[]) fp.getPixels();
            len = fpixels.length;
            for (int i = 0; i < len; i++)
                fpixels[i] = -Float.MAX_VALUE;
        }

        @Override
        void projectSlice(byte[] pixels)
        {
            for (int i = 0; i < len; i++)
                if ((pixels[i] & 0xff) > fpixels[i])
                    fpixels[i] = (pixels[i] & 0xff);
        }

        @Override
        void projectSlice(short[] pixels)
        {
            for (int i = 0; i < len; i++)
                if ((pixels[i] & 0xffff) > fpixels[i])
                    fpixels[i] = pixels[i] & 0xffff;
        }

        @Override
        void projectSlice(float[] pixels)
        {
            for (int i = 0; i < len; i++)
                if (pixels[i] > fpixels[i])
                    fpixels[i] = pixels[i];
        }

    } // end MaxIntensity

    /** Compute min intensity projection. */
    private class MinIntensity extends RayFunction
    {
        private final float[] fpixels;
        private final int len;

        /**
         * Simple constructor since no preprocessing is necessary.
         *
         * @param fp
         *            the image
         */
        MinIntensity(FloatProcessor fp)
        {
            fpixels = (float[]) fp.getPixels();
            len = fpixels.length;
            for (int i = 0; i < len; i++)
                fpixels[i] = Float.MAX_VALUE;
        }

        @Override
        void projectSlice(byte[] pixels)
        {
            for (int i = 0; i < len; i++)
                if ((pixels[i] & 0xff) < fpixels[i])
                    fpixels[i] = (pixels[i] & 0xff);
        }

        @Override
        void projectSlice(short[] pixels)
        {
            for (int i = 0; i < len; i++)
                if ((pixels[i] & 0xffff) < fpixels[i])
                    fpixels[i] = pixels[i] & 0xffff;
        }

        @Override
        void projectSlice(float[] pixels)
        {
            for (int i = 0; i < len; i++)
                if (pixels[i] < fpixels[i])
                    fpixels[i] = pixels[i];
        }

    } // end MaxIntensity

    /** Compute standard deviation projection. */
    private class StandardDeviation extends RayFunction
    {
        private final float[] result;
        private final double[] sum, sum2;
        private final int num, len;

        public StandardDeviation(FloatProcessor fp, int num)
        {
            result = (float[]) fp.getPixels();
            len = result.length;
            this.num = num;
            sum = new double[len];
            sum2 = new double[len];
        }

        @Override
        public void projectSlice(byte[] pixels)
        {
            int v;
            for (int i = 0; i < len; i++)
            {
                v = pixels[i] & 0xff;
                sum[i] += v;
                sum2[i] += v * v;
            }
        }

        @Override
        public void projectSlice(short[] pixels)
        {
            double v;
            for (int i = 0; i < len; i++)
            {
                v = pixels[i] & 0xffff;
                sum[i] += v;
                sum2[i] += v * v;
            }
        }

        @Override
        public void projectSlice(float[] pixels)
        {
            double v;
            for (int i = 0; i < len; i++)
            {
                v = pixels[i];
                sum[i] += v;
                sum2[i] += v * v;
            }
        }

        @Override
        public void postProcess()
        {
            double stdDev;
            final double n = num;
            for (int i = 0; i < len; i++)
                if (num > 1)
                {
                    stdDev = (n * sum2[i] - sum[i] * sum[i]) / n;
                    if (stdDev > 0.0)
                        result[i] = (float) Math.sqrt(stdDev / (n - 1.0));
                    else
                        result[i] = 0f;
                }
                else
                    result[i] = 0f;
        }

    } // end StandardDeviation

} // end ZProjection
