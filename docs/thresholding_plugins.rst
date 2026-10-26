.. index:: ! Thresholding Plugins

Thresholding Plugins
====================

The GDSC plugins provide various tools for thresholding and mask generation.

The plugins are described in the following sections using the order presented on the
``Plugins > GDSC > Thresholding`` menu.


.. index:: ! Global Threshold

Global Threshold
----------------

Provides methods to threshold an image into foreground and background pixels. This is a modified version of the ``Auto_Threshold`` plugin developed by Gabriel Landini. An additional method has been added that sets the threshold as the image average plus a factor of the standard deviation. This is a fast method that can be used within the :ref:`findfoci_plugins:FindFoci` algorithm.

.. index:: ! Multi-Threshold

Multi-Threshold
---------------

Thresholds an image into levels using multiple thresholds. Based on the ``Multi Threshold`` plugin developed by Yasunari Tosa; the modifications allow processing 16-bit images and further output options.

.. index:: ! Mask Creator

Mask Creator
------------

Creates a mask from an image with the following options:

- Use as mask: any non-zero pixel
- Min Display Value: any pixel above the minimum displayed value (set using ``Image > Adjust > Brightness/Contrast``). This respects the display value for each channel
- Use ROI: any pixel inside the marked ROI
- Threshold: perform thresholding (z-stack planes from the same time point are thresholded collectively)

The plugin can operate on stacks and optionally can extract a specified frame, slice or channel to create the mask.

.. index:: ! Edge Mask

Edge Mask
---------

Create an edge mask from an image. Edges are defined where the gradient is steepest. Optionally allows: the edge lines to be pruned to leave only closed loops; and filling of loops to create a mask.

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

Threshold pixels inside an ROI and analyse foreground pixels (count; mean; and standard deviation).

.. index:: ! Threshold Outliner

Threshold Outliner
------------------

Outlines foreground pixel regions using a concave hull. Foreground pixel regions can be auto or manually thresholded.
