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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.ij.Utils;
import uk.ac.sussex.gdsc.core.utils.TextUtils;

/**
 * Extracts the ROI points from an image to file.
 */
public class PointExtractorPlugin implements PlugInFilter
{
    private static String TITLE = "Point Extracter";

    private static String mask = "";
    private static String filename = "";
    private static boolean xyz = true;

    private PointRoi[] pointRois = null;

    private static boolean useManager = true;
    private static boolean reset = false;

    private ImagePlus imp;

    /** {@inheritDoc} */
    @Override
    public int setup(String arg, ImagePlus imp)
    {
        UsageTracker.recordPlugin(this.getClass(), arg);

        checkManagerForRois();

        if (imp == null && !isManagerAvailable())
        {
            IJ.noImage();
            return DONE;
        }

        this.imp = imp;

        return DOES_ALL | NO_IMAGE_REQUIRED;
    }

    private void checkManagerForRois()
    {
        final RoiManager manager = RoiManager.getInstance2();
        if (manager == null)
            return;

        pointRois = new PointRoi[manager.getCount()];

        // Store the point ROIs
        int count = 0;
        for (final Roi roi : manager.getRoisAsArray())
            if (roi instanceof PointRoi)
                pointRois[count++] = (PointRoi) roi;

        pointRois = Arrays.copyOf(pointRois, count);
    }

    private boolean isManagerAvailable()
    {
        return nPointRois() != 0;
    }

    private int nPointRois()
    {
        return (pointRois == null) ? 0 : pointRois.length;
    }

    private int nPoints()
    {
        if (pointRois == null)
            return 0;
        int count = 0;
        for (final PointRoi roi : pointRois)
            count += roi.getNCoordinates();
        return count;
    }

    /** {@inheritDoc} */
    @Override
    public void run(ImageProcessor ip)
    {
        if (!showDialog())
            return;

        AssignedPoint points[] = getPoints();

        // Allow empty files to be generated to support macros
        if (points == null)
            points = new AssignedPoint[0];

        try
        {
            if (xyz || imp == null)
                PointManager.savePoints(points, filename);
            else
            {
                // Extract the values
                final TimeValuedPoint[] p = new TimeValuedPoint[points.length];
                final ImageStack stack = imp.getImageStack();
                final int channel = imp.getChannel();
                final int frame = imp.getFrame();
                for (int i = 0; i < points.length; i++)
                {
                    final int x = (int) points[i].getX();
                    final int y = (int) points[i].getY();
                    final int z = (int) points[i].getZ();
                    final int index = imp.getStackIndex(channel, z, frame);
                    final ImageProcessor ip2 = stack.getProcessor(index);
                    p[i] = new TimeValuedPoint(x, y, z, frame, ip2.getf(x, y));
                }

                TimeValuePointManager.savePoints(p, filename);
            }
        }
        catch (final IOException e)
        {
            IJ.error("Failed to save the ROI points:\n" + e.getMessage());
        }
    }

    private AssignedPoint[] getPoints()
    {
        AssignedPoint[] roiPoints;

        if (isManagerAvailable() && useManager)
        {
            final ArrayList<AssignedPoint> points = new ArrayList<>();
            for (final PointRoi roi : pointRois)
                points.addAll(Arrays.asList(PointManager.extractRoiPoints(roi)));
            roiPoints = points.toArray(new AssignedPoint[points.size()]);

            if (reset)
            {
                final RoiManager manager = RoiManager.getInstance2();
                if (manager != null)
                    manager.runCommand("reset");
            }
        }
        else
            roiPoints = PointManager.extractRoiPoints(imp.getRoi());

        final ImagePlus maskImp = WindowManager.getImage(mask);
        return FindFociOptimiser.restrictToMask(maskImp, roiPoints);
    }

    private boolean showDialog()
    {
        // To improve the flexibility, do not restrict the mask to those suitable for the image. Allow any image for the mask.
        //ArrayList<String> newImageList = FindFoci.buildMaskList(imp);
        //String[] list = newImageList.toArray(new String[0]);
        final String[] list = Utils.getImageList(Utils.NO_IMAGE, null);

        final GenericDialog gd = new GenericDialog(TITLE);

        gd.addMessage("Extracts the ROI points to file");
        gd.addChoice("Mask", list, mask);
        gd.addStringField("Filename", filename, 30);
        if (imp != null)
            gd.addCheckbox("xyz_only", xyz);

        if (isManagerAvailable())
        {
            gd.addMessage(String.format("%s (%s) present in the ROI manager", TextUtils.pleural(nPointRois(), "ROI"),
                    TextUtils.pleural(nPoints(), "point")));
            gd.addCheckbox("Use_manager_ROIs", useManager);
            gd.addCheckbox("Reset_manager", reset);
        }
        else
        {
            final Roi roi = imp.getRoi();
            if (roi == null || roi.getType() != Roi.POINT)
                gd.addMessage("Warning: The image does not contain Point ROI.\nAn empty result file will be produced");
        }

        gd.showDialog();

        if (gd.wasCanceled())
            return false;

        mask = gd.getNextChoice();
        filename = gd.getNextString();
        if (imp != null)
            xyz = gd.getNextBoolean();

        if (isManagerAvailable())
        {
            useManager = gd.getNextBoolean();
            reset = gd.getNextBoolean();
        }

        return true;
    }
}
