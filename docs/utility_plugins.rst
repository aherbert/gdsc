.. index:: ! Utility Plugins

Utility Plugins
===============

The GDSC plugins provide various utility tools for image analysis. The tools
include: difference of Gaussians for
contrast enhancement; stack synchronisation for simultaneous image viewing;
and image alignment.

The plugins are described in the following sections using the order presented on the
``Plugins > GDSC > Utility`` menu.

.. index:: ! Contrast Enhancer

Contrast Enhancer
-----------------

Runs the ``ImageJ`` contrast enhancer (see Image > Adjust > Brightness/Contrast) on all the open images. This allows a one-click adjustment of all the open images for optimal viewing

.. index:: ! Difference of Gaussians

Difference of Gaussians
-----------------------

Performs a difference of Gaussians filter for local contrast enhancement. The filter performs subtraction of one blurred version of an image from another, less blurred version of the original. The result is an image containing only the information contained within the spatial frequency between the two blurred images.

.. index:: ! Laplacian of Gaussian

Laplacian of Gaussians
----------------------

Performs a Laplacian of Gaussians filter for local contrast enhancement.

Requires the ``Image Science`` library (see :ref:`installation:Installation` instructions).

.. index:: ! Align Images

Align Images
------------

Aligns an image stack to a reference image using XY translation to maximise the normalised correlation. Outputs a new stack with the best alignment with optional sub-pixel accuracy (performed using a Gaussian fit or Cubic spline fit).

Contains options to: restrict the translation space; provide a mask image to specify which pixels to include in the analysis; and clip the bicubic interpolated output to fit within the min-max of the original image.

.. index:: ! Align Images FFT

Align Images FFT
----------------

Aligns an image stack to a reference image using XY translation to maximise the normalised correlation. The correlation is computed in the Frequency domain using Fast Fourier Transforms. Edge artifacts of the Frequency domain are reduced by using a window function on the image (gradually reducing the edge pixels to zero).

Outputs a new stack with the best alignment with optional sub-pixel accuracy (performed using a Gaussian fit or Cubic spline fit).

Contains options to restrict the translation space.

Note: This plugin is faster than the 'Align Images' plugin for large images and/or a large translation space. Note that the results will differ slightly due to the use of different correlation methods.

.. index:: ! Align Stacks

Align Stacks
------------

Aligns open image stacks to a reference image using XY translation to maximise the normalised correlation. Accepts multi-dimensional stacks. The self-align mode aligns all timepoints within a stack to the current frame.

For each timepoint a maximum/average intensity projection is performed per channel. The projection is thresholded to extract the signal, normalised to 0-255 and the resulting channel images are tiled to a composite image. The composite is then aligned using the maximum correlation between the images. Using a composite ensures each channel contributes equally to the aligment. The translation is applied to the entire stack for that timepoint.

.. index:: ! Image Scaler

Image Scaler
------------

Scales all images in a stack so that the maximum of the image is set to the specified value. For example this can be used to scale a floating point image so that the maximum is 255 for correct display.

Can accept a list file of images allowing a set of images to be scaled together. The maximum of the entire set of images is used to calculate the scale factor applied to each image.

.. index:: ! Cell Outliner

Cell Outliner
-------------

Find cell edges using marked seed points. Best suited to identify round cells with edge pixels either lighter or darker than the cell.

For each point ROI the plugin uses a curved edge filter to find edges within a range of an expected radius. The minimum/maximum size is set using the radius +/- tolerance. Edge detection is performed using an approximation to the Laplacian-of-Gaussian (LoG) and the kernel size and smoothing can be controlled.

The edge detection is weighted using the distance to the current outline of the cell (initially a circle of the specified radius around the point). A polygon outline is constructed using the highest edge value in each 10 degree segment around the centre. This is fitted using an oval shape of two ellipses back-to-back and the process iterated using the new cell outline. The final outline can be output as the polygon shape or the elliptical fit. This can be dilated to expand the outline to encompass the entire cell.

Supports a preview allowing the parameters to be interactively set. The final call produces a mask image and options are provided to process each plane/frame of a hyperstack.

.. index:: ! Spot Analyser

Spot Analyser
-------------

Identifies particles (e.g. cells) using thresholding and excludes small particles based on pixel count. The average intensity of each channel inside and outside the spot are then compared for the top N spots to identify increases in signal within spot regions.

.. index:: ! HSB Picker

HSB Picker
----------

Installs a tool into the menu that allows Hue-Saturation-Brightness (HSB) values to be sampled from an RGB image by mouse click. Pixels are sampled using a radius around the clicked position. The average HSB values and the standard deviations from all sampled pixels are shown in a frame.

Provides the ability to call the HSB Filter. The filter values are set using the averages and the widths using the scaled standard deviations (e.g. av +/- scale * std.dev.).

.. index:: ! HSB Filter

HSB Filter
----------

Provides the ability to filter pixels in an RGB image using Hue-Saturation-Brightness (HSB) values. All pixels not within the specified width of the given values are set to zero.

Note that Hue is a continuous property where values are wrapped around from 1 to 0.

.. index:: ! Spot Separation

Spot Separation
---------------

Finds spots in an image. Locates the closest neighbour spot within a radius and produces a line profile through the spots. If no neighbour is present produces a line profile through the principle axis. Outputs the separation between the spot maxima to a table.

Allows analysis of regions using the ROI selection tools and respects manual thresholding to identify spots.

.. index:: ! Z-Project extra

Z-Project extra...
------------------

Extends the ``ImageJ`` Z-Project command to add the a ``Mode`` projection option. The mode can be calculated with or without ignoring zero values.

This plugin is useful for projecting 3D masks, for example those created by the
:ref:`findfoci_plugins:Find Foci Plugins`.
Intensity Analysis  Analyses the mean value within an ROI across a stack of exposures. Exposures must be set within the slice labels. Saturated pixels within an ROI remove that slice from analysis. A linear fit is made of the mean verses exposure using a rolling window and the gradient reported for the best fit. The gradient is a measure of the pixel intensity within the ROI.

This plugin assumes images are taken of the same object at different exposure times on a camera with a linear response curve. Thus doubling the exposure should double the pixel value. The plugin allows analysis of images with a large dynamic range by capturing each object with a suitable exposure. Drift should be minimal for the best results.

.. index:: ! Intensity Analysis

Intensity Analysis
------------------

Calculate the normalised intensity within an ROI using a stack of exposures (different imaging times). Exposures must be set within the slice labels. Images should have a linear response with respect to exposure.

.. index:: ! Measure 3D

Measure 3D
----------

Installs a tool into the menu that allows distances to be measured in 3D. Distance is measured between two successive mouse clicks on the same channel and time frame of an image. Results are recorded to a table.

.. index:: ! Scale Space

Scale Space
-----------

Create a scale-space representation of an image using a 2D Gaussian filter at different scales.

Requires the ``Image Science`` library (see :ref:`installation:Installation` instructions).

.. index:: ! FRAP Analysis

FRAP Analysis
-------------

Analyses an image stack to detect pixel regions that have been photobleached. A plot of the intensity of the region is created over time. This can be saved to file for fitting with Fluorescence Recovery After Photobleaching (FRAP) models.

.. index:: ! Mask to ROI

Mask to ROI
-----------

Traces each object in a mask image and adds them to the ROI Manager.

.. index:: ! Mask to Border

Mask to Border
--------------

Creates a border around each object in a mask image using variable width inside and outside the mask. Used to create a ring ROI of each mask object.

.. index:: ! Mask Overlap

Mask Overlap
------------

Relabels mask image using overlap between z and/or t dimensions.
