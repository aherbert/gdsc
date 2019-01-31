/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2019 Alex Herbert
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

package uk.ac.sussex.gdsc.threshold;

import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.ij.ThresholdUtils;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold;
import uk.ac.sussex.gdsc.utils.SliceCollection;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

import java.util.ArrayList;

/**
 * Processes an image stack and applies thresholding to create a mask for each channel+frame
 * combination.
 */
public class StackThreshold_PlugIn implements PlugInFilter {
  // Store a reference to the current working image
  private ImagePlus imp;

  private static final String PLUGIN_TITLE = "Stack Threshold";

  // ImageJ indexes for the dimensions array
  private static final int C = 2;
  private static final int Z = 3;
  private static final int T = 4;

  private static String methodOption = AutoThreshold.Method.OTSU.toString();

  // Options flags
  private static boolean logThresholds;
  private static boolean compositeColour;
  private static boolean newImage = true;

  /** {@inheritDoc} */
  @Override
  public int setup(String arg, ImagePlus imp) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    if (imp == null) {
      IJ.noImage();
      return DONE;
    }

    this.imp = imp;

    return DOES_16 + DOES_8G + NO_CHANGES;
  }

  /** {@inheritDoc} */
  @Override
  public void run(ImageProcessor inputProcessor) {
    final int[] dimensions = imp.getDimensions();
    final int currentSlice = imp.getCurrentSlice();
    for (final String method : getMethods()) {
      final ImageStack maskStack =
          new ImageStack(imp.getWidth(), imp.getHeight(), imp.getStackSize());

      // Process each frame
      for (int t = 1; t <= dimensions[T]; t++) {
        final ArrayList<AnalysisSliceCollection> sliceCollections = new ArrayList<>();

        // Extract the channels
        for (int c = 1; c <= dimensions[C]; c++) {
          // Process all slices together
          final AnalysisSliceCollection sliceCollection = new AnalysisSliceCollection(c);
          for (int z = 1; z <= dimensions[Z]; z++) {
            sliceCollection.add(imp.getStackIndex(c, z, t));
          }
          sliceCollections.add(sliceCollection);
        }

        // Create masks
        for (final AnalysisSliceCollection s : sliceCollections) {
          createMask(method, maskStack, t, s);
        }
      }

      if (newImage) {
        final ImagePlus newImg = new ImagePlus(imp.getTitle() + ":" + method, maskStack);
        newImg.setDimensions(dimensions[C], dimensions[Z], dimensions[T]);
        if (imp.getNDimensions() > 3) {
          newImg.setOpenAsHyperStack(true);
        }
        newImg.show();
        if (compositeColour) {
          IJ.run("Make Composite", "display=Color");
        }
      } else {
        imp.setStack(maskStack, imp.getNChannels(), imp.getNSlices(), imp.getNFrames());

        for (int slice = 1; slice <= imp.getStackSize(); slice++) {
          imp.setSliceWithoutUpdate(slice);
          imp.resetDisplayRange();
        }

        imp.updateAndRepaintWindow();
      }
    }
    imp.setSlice(currentSlice);
  }

  private void createMask(String method, ImageStack maskStack, int frame,
      AnalysisSliceCollection sliceCollection) {
    sliceCollection.createStack(imp);
    sliceCollection.createMask(method);
    if (logThresholds) {
      IJ.log("t" + frame + sliceCollection.getSliceName() + " threshold = "
          + sliceCollection.threshold);
    }

    for (int s = 1; s <= sliceCollection.maskStack.getSize(); s++) {
      final int originalSliceNumber = sliceCollection.get(s - 1);
      maskStack.setSliceLabel(method + ":" + imp.getStack().getSliceLabel(originalSliceNumber),
          originalSliceNumber);
      maskStack.setPixels(sliceCollection.maskStack.getPixels(s), originalSliceNumber);
    }
  }

  private String[] getMethods() {
    final GenericDialog gd = new GenericDialog(PLUGIN_TITLE);
    gd.addMessage(PLUGIN_TITLE);

    // Commented out the methods that take a long time on 16-bit images.
    String[] methods = {"Try all", AutoThreshold.Method.DEFAULT.toString(),
        // "Huang",
        // "Intermodes",
        // "IsoData",
        AutoThreshold.Method.LI.toString(), AutoThreshold.Method.MAX_ENTROPY.toString(),
        AutoThreshold.Method.MEAN.toString(), AutoThreshold.Method.MIN_ERROR_I.toString(),
        // "Minimum",
        AutoThreshold.Method.MOMENTS.toString(), AutoThreshold.Method.OTSU.toString(),
        AutoThreshold.Method.PERCENTILE.toString(), AutoThreshold.Method.RENYI_ENTROPY.toString(),
        // "Shanbhag",
        AutoThreshold.Method.TRIANGLE.toString(), AutoThreshold.Method.YEN.toString()};

    gd.addChoice("Method", methods, methodOption);
    gd.addCheckbox("Log_thresholds", logThresholds);
    if (imp.getDimensions()[C] > 1) {
      gd.addCheckbox("Composite_colour", compositeColour);
    }
    gd.addCheckbox("New_image", newImage);
    gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.FIND_FOCI);

    gd.showDialog();
    if (gd.wasCanceled()) {
      return new String[0];
    }

    methodOption = gd.getNextChoice();
    logThresholds = gd.getNextBoolean();
    if (imp.getDimensions()[C] > 1) {
      compositeColour = gd.getNextBoolean();
    }
    newImage = gd.getNextBoolean();

    if (!methodOption.equals("Try all")) {
      // Ensure that the string contains known methods (to avoid passing bad macro arguments)
      methods = extractMethods(methodOption.split(" "), methods);
    } else {
      // Shift the array to remove the try all option
      final String[] newMethods = new String[methods.length - 1];
      for (int i = 0; i < newMethods.length; i++) {
        newMethods[i] = methods[i + 1];
      }
      methods = newMethods;
    }

    if (methods.length == 0) {
      return failed("No valid thresholding method(s) specified");
    }

    // Cannot update original with more than one method
    if (methods.length > 1) {
      newImage = true;
    }

    return methods;
  }

  private static String[] failed(String message) {
    IJ.error(PLUGIN_TITLE, message);
    return new String[0];
  }

  /**
   * Filtered the set of options using the allowed methods array.
   *
   * @param options the options
   * @param allowedMethods the allowed methods
   * @return filtered options
   */
  private static String[] extractMethods(String[] options, String[] allowedMethods) {
    final ArrayList<String> methods = new ArrayList<>();
    for (final String option : options) {
      for (final String allowedMethod : allowedMethods) {
        if (option.equals(allowedMethod)) {
          methods.add(option);
          break;
        }
      }
    }
    return methods.toArray(new String[0]);
  }

  /**
   * Provides functionality to process a collection of slices from an Image.
   */
  private static class AnalysisSliceCollection extends SliceCollection {
    ImageStack imageStack;
    ImageStack maskStack;
    int threshold;

    /**
     * Instantiates a new analysis slice collection.
     *
     * @param indexC The channel index
     */
    AnalysisSliceCollection(int indexC) {
      super(indexC, 0, 0);
    }

    /**
     * Extracts the configured slices from the image into a stack.
     *
     * @param imp the image
     */
    void createStack(ImagePlus imp) {
      imageStack = createStack(imp.getImageStack());
    }

    /**
     * Creates a mask using the specified thresholding method.
     *
     * @param method the method
     */
    private void createMask(String method) {
      // Create an aggregate histogram
      final int[] data = ThresholdUtils.getHistogram(imageStack);

      threshold = AutoThreshold.getThreshold(method, data);

      // Create a mask for each image in the stack
      maskStack = new ImageStack(imageStack.getWidth(), imageStack.getHeight());
      final int size = imageStack.getWidth() * imageStack.getHeight();
      for (int s = 1; s <= imageStack.getSize(); s++) {
        final byte[] bp = new byte[size];
        final ImageProcessor ip = imageStack.getProcessor(s);
        for (int i = size; i-- > 0;) {
          if (ip.get(i) > threshold) {
            bp[i] = (byte) 255;
          }
        }
        maskStack.addSlice(null, bp);
      }
    }
  }
}
