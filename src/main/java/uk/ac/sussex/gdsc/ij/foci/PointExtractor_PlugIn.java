/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2025 Alex Herbert
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

package uk.ac.sussex.gdsc.ij.foci;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.ij.gui.ExtendedGenericDialog;
import uk.ac.sussex.gdsc.core.utils.TextUtils;
import uk.ac.sussex.gdsc.ij.UsageTracker;

/**
 * Extracts the ROI points from an image to file.
 */
public class PointExtractor_PlugIn implements PlugInFilter {
  private static final String TITLE = "Point Extracter";
  private static final String SETTING_FILENAME = "gdsc.foci.pointextractor.filename";

  private static String mask = "";
  private static String filename = Prefs.getString(SETTING_FILENAME, "");
  private static boolean xyz = true;

  private PointRoi[] pointRois;

  private static boolean useManager = true;
  private static boolean useCurrentImage = true;
  private static boolean reset;

  private ImagePlus imp;

  /**
   * Class used to filter points to a unique set by providing a hash code on the XYZ values.
   */
  private static class Xyz {
    private final int hash;
    private final AssignedPoint point;

    /**
     * Create an instance.
     *
     * @param point the point
     */
    Xyz(AssignedPoint point) {
      // Do not use the AssignedPoint hash code as we do not match the frame and ID
      hash = (41 * (41 * (41 + point.x) + point.y) + point.z);
      this.point = point;
    }

    @Override
    public int hashCode() {
      return hash;
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }
      if (!(object instanceof Xyz)) {
        return false;
      }

      // Compare XYZ coordinates
      final AssignedPoint that = ((Xyz) object).point;

      return point.x == that.x && point.y == that.y && point.z == that.z;
    }

    /**
     * Compare using the XYZ of the two points.
     *
     * @param o1 the first object
     * @param o2 the second object
     * @return -1, 0, or 1
     */
    static int compare(Xyz o1, Xyz o2) {
      int result = Integer.compare(o1.point.z, o2.point.z);
      if (result != 0) {
        return result;
      }
      result = Integer.compare(o1.point.x, o2.point.x);
      if (result != 0) {
        return result;
      }
      return Integer.compare(o1.point.y, o2.point.y);
    }
  }

  /** {@inheritDoc} */
  @Override
  public int setup(String arg, ImagePlus imp) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    checkManagerForRois();

    if (imp == null && !isManagerAvailable()) {
      IJ.noImage();
      return DONE;
    }

    this.imp = imp;

    return DOES_ALL | NO_IMAGE_REQUIRED;
  }

  private void checkManagerForRois() {
    final RoiManager manager = RoiManager.getInstance2();
    if (manager == null) {
      return;
    }

    final Roi[] rois = manager.getRoisAsArray();
    pointRois = new PointRoi[rois.length];

    // Store the point ROIs
    int count = 0;
    for (final Roi roi : rois) {
      if (roi instanceof PointRoi) {
        pointRois[count++] = (PointRoi) roi;
      }
    }

    pointRois = Arrays.copyOf(pointRois, count);
  }

  private boolean isManagerAvailable() {
    return numberOfPointRois() != 0;
  }

  private int numberOfPointRois() {
    return (pointRois == null) ? 0 : pointRois.length;
  }

  private int numberOfPoints() {
    if (pointRois == null) {
      return 0;
    }
    int count = 0;
    for (final PointRoi roi : pointRois) {
      count += roi.getNCoordinates();
    }
    return count;
  }

  /** {@inheritDoc} */
  @Override
  public void run(ImageProcessor ip) {
    if (!showDialog(this)) {
      return;
    }

    AssignedPoint[] points = getPoints();

    // Allow empty files to be generated to support macros
    if (points == null) {
      points = new AssignedPoint[0];
    }

    try {
      if (xyz || imp == null) {
        AssignedPointUtils.savePoints(points, filename);
      } else {
        // Extract the values
        final TimeValuedPoint[] p = new TimeValuedPoint[points.length];
        final ImageStack stack = imp.getImageStack();
        final int channel = imp.getChannel();
        final int frame = imp.getFrame();
        for (int i = 0; i < points.length; i++) {
          final int x = (int) points[i].getX();
          final int y = (int) points[i].getY();
          final int z = (int) points[i].getZ();
          final int index = imp.getStackIndex(channel, z, frame);
          final ImageProcessor ip2 = stack.getProcessor(index);
          p[i] = new TimeValuedPoint(x, y, z, frame, ip2.getf(x, y));
        }

        TimeValuePointManager.savePoints(p, filename);
      }
    } catch (final IOException ex) {
      IJ.error("Failed to save the ROI points:\n" + ex.getMessage());
    }
  }

  private AssignedPoint[] getPoints() {
    AssignedPoint[] roiPoints;

    if (isManagerAvailable() && useManager) {
      final HashSet<Xyz> points = new HashSet<>();
      for (final PointRoi roi : pointRois) {
        // Note:
        // ROIs from the ROI manager may use an image which can be used to extract z positions.
        // When ROIs are added to the ROI manager it uses a clone which clears the image
        // associated with the ROI. The position array is not cleared.
        // Thus to extract the correct positions requires the current image.
        final ImagePlus imp = useCurrentImage ? this.imp : null;
        AssignedPoint[] assignedPoints;
        if (imp != null) {
          assignedPoints = AssignedPointUtils.extractRoiPoints(imp, roi);
        } else {
          assignedPoints = AssignedPointUtils.extractRoiPoints(roi);
        }
        Arrays.stream(assignedPoints).map(Xyz::new).forEach(points::add);
      }
      roiPoints =
          points.stream().sorted(Xyz::compare).map(x -> x.point).toArray(AssignedPoint[]::new);

      if (reset) {
        final RoiManager manager = RoiManager.getInstance2();
        if (manager != null) {
          manager.runCommand("reset");
        }
      }
    } else {
      roiPoints = AssignedPointUtils.extractRoiPoints(imp);
      Arrays.sort(roiPoints, (o1, o2) -> {
        int result = Integer.compare(o1.z, o2.z);
        if (result != 0) {
          return result;
        }
        result = Integer.compare(o1.x, o2.x);
        if (result != 0) {
          return result;
        }
        return Integer.compare(o1.y, o2.y);
      });
    }

    final ImagePlus maskImp = WindowManager.getImage(mask);
    return FindFociOptimiser_PlugIn.restrictToMask(maskImp, roiPoints);
  }

  private static boolean showDialog(PointExtractor_PlugIn plugin) {
    // To improve the flexibility, do not restrict the mask to those suitable for the image. Allow
    // any image for the mask.
    final String[] list = ImageJUtils.getImageList(ImageJUtils.NO_IMAGE, null);

    final ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);

    gd.addMessage("Extracts the ROI points to file");
    gd.addChoice("Mask", list, mask);
    gd.addFilenameField("Filename", filename, 30);
    if (plugin.imp != null) {
      gd.addCheckbox("xyz_only", xyz);
    }

    if (plugin.isManagerAvailable()) {
      gd.addMessage(String.format("%s (%s) present in the ROI manager",
          TextUtils.pleural(plugin.numberOfPointRois(), "ROI"),
          TextUtils.pleural(plugin.numberOfPoints(), "point")));
      gd.addCheckbox("Use_manager_ROIs", useManager);
      gd.addCheckbox("Use_current_image_for_z", useCurrentImage);
      gd.addCheckbox("Reset_manager", reset);
    } else {
      final Roi roi = plugin.imp.getRoi();
      if (roi == null || roi.getType() != Roi.POINT) {
        gd.addMessage("Warning: The image does not contain Point ROI.\n"
            + "An empty result file will be produced");
      }
    }

    gd.showDialog();

    if (gd.wasCanceled()) {
      return false;
    }

    mask = gd.getNextChoice();
    filename = gd.getNextString();
    if (plugin.imp != null) {
      xyz = gd.getNextBoolean();
    }

    if (plugin.isManagerAvailable()) {
      useManager = gd.getNextBoolean();
      useCurrentImage = gd.getNextBoolean();
      reset = gd.getNextBoolean();
    }

    Prefs.set(SETTING_FILENAME, filename);

    return true;
  }
}
