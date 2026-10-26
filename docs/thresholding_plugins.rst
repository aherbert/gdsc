.. index:: ! Thresholding Plugins

Thresholding Plugins
====================

The GDSC plugins provide various tools for thresholding and mask generation.

The plugins are described in the following sections using the order presented on the
``Plugins > GDSC > Thresholding`` menu.


.. index:: ! Global Threshold

Global Threshold
----------------

Provides methods to threshold an image into foreground and background pixels. This is a modified version of the ``Auto_Threshold`` plugin developed by Gabriel Landini. An additional method has been added that sets the threshold as the image average plus a factor of the standard deviation. This is a fast method that can be used within the :ref:`findfoci_plugins:FindFoci` algorithm using the ``Std.Dev above mean`` ``Background method``, with the ``Background parameter`` setting the multiplier.

.. index:: ! Multi-Threshold

Multi-Threshold
---------------

Thresholds an image into 2 to 5 levels using multi-level Otsu thresholding. Based on the ``Multi Otsu Threshold`` plugin developed by Yasunari Tosa; the modifications allow processing 16-bit images and stacks, and add further output options.

.. index:: ! Mask Creator

Mask Creator
------------

Creates a mask from an image with the following options:

- Use as mask: any non-zero pixel
- Min Display Value: any pixel at or above the minimum displayed value (set using ``Image > Adjust > Brightness/Contrast``). This respects the display value for each channel
- Use ROI: any pixel inside the marked ROI
- Threshold: perform thresholding using an auto-threshold method (optionally the z-stack planes from the same channel and time point are thresholded collectively using the ``Stack histogram`` option)

The plugin can operate on stacks and optionally can extract a specified frame, slice or channel to create the mask (use zero for all).

The mask can be post-processed to clear pixels outside the ROI, remove particles touching the image edge or below a minimum size, and assign a unique value to each object (using 4 or 8-connected pixels in 3D).

.. index:: ! Edge Mask

Edge Mask
---------

Create an edge mask from an image. The mask can be created using pixels above a background level, or using edges. Edges are defined using the zero-crossings of the Laplacian (where the gradient is steepest) or the magnitude of the gradient (optionally using only the maximum gradient). Optionally allows: the edge lines to be pruned to leave only closed loops; and, for Laplacian edges, filling of loops to create a mask.

Requires the ``Image Science`` library (see :ref:`installation:Installation` instructions).

.. index:: ! Apply Mask

Apply Mask
----------

Create a mask from a source image using the ``Mask Creator`` and apply it to a target image. All pixels outside the mask in the target image will be set to zero.

.. index:: ! Skeleton Analyser

Skeleton Analyser
-----------------

Skeletonise (reduce to single pixel lines) a mask image. Then produce a set of lines connecting node points on the skeleton.

.. index:: ! Thread Analyser

Thread Analyser
---------------

Analyses an image using a given mask. Skeletonises the mask image and extracts a set of lines connecting node points on the skeleton. Output statistics about each of the lines using the original image and the Euclidean Distance Map (EDM) of the mask.

.. index:: ! Foreground Analyser

Foreground Analyser
-------------------

Threshold pixels inside an ROI and analyse foreground pixels (count; sum; mean; and standard deviation). Optionally the analysis can use the entire stack and the foreground pixels can be shown as a mask.

.. index:: ! Threshold Outliner

Threshold Outliner
------------------

Outlines objects defined in a mask image. The pixels of each object are thresholded using an auto-threshold method or a manual percentile. Optionally outliers are removed using local outlier probability (LoOP). The remaining pixels are outlined using a convex or concave hull. Creates an ROI overlay of the outlines and a new mask image.
