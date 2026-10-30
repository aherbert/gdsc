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

The plugin processes the current image which must be an 8-bit or 16-bit image. A 32-bit image can be converted to 16-bit when the plugin is run; the conversion changes the input image.

The threshold is computed from the image histogram. Pixels above the threshold are foreground; all other pixels are background.

.. index:: Global Threshold Plugin Interface

Plugin Interface
~~~~~~~~~~~~~~~~

The ``Global Threshold`` plugin uses a standard ``ImageJ`` dialog. The following parameters can be configured:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description

   * - Method
     - The threshold method. ``Try all`` shows the result of every method. The ``MeanPlusStdDev`` method sets the threshold as the mean plus a multiple of the standard deviation.

   * - StdDev multiplier
     - The multiple of the standard deviation used by the ``MeanPlusStdDev`` method.

   * - Ignore black
     - Exclude pixels with a value of zero from the histogram.

   * - Ignore white
     - Exclude pixels with the maximum value of the image type from the histogram.

   * - White objects on black background
     - The foreground is set to white and the background to black. If not selected then the foreground is black and the background is white.

   * - SetThreshold instead of Threshold
     - Set the ``ImageJ`` threshold of the image (shown in red) instead of changing the pixel values. This applies when thresholding a single image or a single slice of a stack; it is ignored for ``Try all`` and when processing the whole stack.

   * - Show threshold values in log window
     - Write the threshold to the ``ImageJ`` log.

   * - Stack
     - Threshold every slice of the stack. Each slice uses its own histogram. Only shown for a stack.

   * - Use stack histogram
     - Threshold every slice of the stack using a single threshold computed from the histogram of all the slices. Only shown for a stack.

.. index:: Global Threshold Output

Output
~~~~~~

The input image is converted to a binary image: the foreground is white and the background is black, or the reverse if ``White objects on black background`` is not selected. The result for an 8-bit or 16-bit image is an 8-bit image (white is 255). A single slice of a 16-bit stack remains 16-bit (white is 65535). A single image can be restored using ``Edit > Undo``. If the threshold method fails then the image is unchanged.

If ``SetThreshold instead of Threshold`` is used then the pixel values and the image type are unchanged.

The ``Try all`` method does not change the input image. It creates a montage named using the image title with the suffix ``Thresholds`` showing the result of each method labelled with the threshold. The results for a 16-bit image are 16-bit (white is 65535). For a stack a montage is created for each slice; for more than 25 slices the plugin asks to confirm the display, or to write the thresholds to the log window.

.. index:: ! Multi-Threshold

Multi-Threshold
---------------

Thresholds an image into 2 to 5 levels using multi-level Otsu thresholding. Based on the ``Multi Otsu Threshold`` plugin developed by Yasunari Tosa; the modifications allow processing 16-bit images and stacks, and add further output options.

The plugin processes an 8-bit or 16-bit image. The thresholds are computed from the histogram of the entire stack (or the current image plane). If the image has an ROI then only the pixels inside the ROI are used to compute the thresholds. The image is not changed.

The thresholds divide the pixel values into levels. Level 0 contains the lowest pixel values and the highest level contains the highest pixel values.

.. index:: Multi-Threshold Plugin Interface

Plugin Interface
~~~~~~~~~~~~~~~~

The ``Multi-Threshold`` plugin uses a standard ``ImageJ`` dialog. The following parameters can be configured:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description

   * - Levels
     - The number of levels (2 to 5).

   * - Do stack
     - Compute the thresholds using all the planes of the stack. If not selected then only the current image plane is processed. Only shown for a stack.

   * - Ignore zero
     - Exclude pixels with a value of zero from the histogram. Zero pixels are assigned to level 0.

   * - Show histogram
     - Show a plot of the histogram with the thresholds marked as red lines.

   * - Show regions
     - Show an image for each level containing the pixels of the level. All other pixels are zero.

   * - Show masks
     - Show a mask image for each level. Pixels of the level have the value 255; all other pixels are zero.

   * - Log thresholds
     - Write the thresholds to the ``ImageJ`` log.

At least one output option must be selected.

.. index:: Multi-Threshold Output

Output
~~~~~~

The images are named using the image title with the suffix ``Region n`` or ``Mask n`` for level ``n``. The histogram plot uses the suffix ``Histogram``. If only the current image plane is processed then the title includes the channel, z-slice and frame, e.g. ``image.tif (c1,z1,t1)``.

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

.. index:: Mask Creator Plugin Interface

Plugin Interface
~~~~~~~~~~~~~~~~

The ``Mask Creator`` plugin uses a standard ``ImageJ`` dialog. The following parameters can be configured:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description

   * - Image
     - The image used to create the mask.

   * - Option
     - The method used to create the mask: ``Use as mask``; ``Min Display Value``; ``Use ROI``; or ``Threshold``.

   * - Threshold Method
     - The threshold method for the ``Threshold`` option. The ``Otsu_3_level`` and ``Otsu_4_level`` methods use multi-level Otsu thresholding (see :ref:`thresholding_plugins:Multi-Threshold`) and use the lowest threshold; zero pixels are ignored. A 32-bit image is thresholded by mapping each channel and frame to the 16-bit range.

   * - Clear outside ROI
     - Clear pixels of the mask outside the ROI of the image. Not used by the ``Use ROI`` option.

   * - Channel
     - The channel to use. Use zero for all channels.

   * - Slice
     - The z-slice to use. Use zero for all z-slices.

   * - Frame
     - The frame to use. Use zero for all frames.

   * - Remove edge particles
     - Remove objects touching the edge of the image in X or Y.

   * - Min particle size
     - The minimum size of an object (in pixels). Smaller objects are removed.

   * - Stack histogram
     - For the ``Threshold`` option, compute one threshold for all the z-slices of each channel and frame. If not selected then each image plane is thresholded separately.

   * - Assign objects
     - Assign each object a unique value. Objects are numbered separately for each channel and frame. A maximum of 65535 objects can be assigned; any further objects are removed and a warning is written to the ``ImageJ`` log.

   * - Eight connected
     - Define objects using 8-connected pixels (pixels touching diagonally are connected). If not selected then objects use 4-connected pixels. Objects are connected in 3D through the z-slices.

The object options (``Remove edge particles``, ``Min particle size`` and ``Assign objects``) identify objects in 3D using all the z-slices of each channel and frame.

.. index:: Mask Creator Output

Output
~~~~~~

The plugin creates a new image named using the image title with the suffix ``Mask``. Mask pixels have the value 255 (or the object number if using ``Assign objects``); all other pixels are zero. The mask is an 8-bit image, or 16-bit if there are more than 255 objects.

The mask has the selected channels, z-slices and frames of the image. The ``Use ROI`` option creates the same mask for every image plane. A rectangle ROI is added to the mask image that bounds all the mask pixels.

.. index:: ! Edge Mask

Edge Mask
---------

Create an edge mask from an image. The mask can be created using pixels above a background level, or using edges. Edges are defined using the zero-crossings of the Laplacian (where the gradient is steepest) or the magnitude of the gradient (optionally using only the maximum gradient). Optionally allows: the edge lines to be pruned to leave only closed loops; and, for Laplacian edges, filling of loops to create a mask.

Requires the ``Image Science`` library (see :ref:`installation:Installation` instructions).

.. index:: ! Apply Mask

Apply Mask
----------

Create a mask from a source image using the ``Mask Creator`` and apply it to a target image. All pixels outside the mask in the target image will be set to zero.

The target is the current image. The mask is created from a source image using the :ref:`thresholding_plugins:Mask Creator` options.

.. index:: Apply Mask Plugin Interface

Plugin Interface
~~~~~~~~~~~~~~~~

The ``Apply Mask`` plugin uses a standard ``ImageJ`` dialog. The following parameters can be configured:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description

   * - Mask Image
     - The source image used to create the mask. Use ``(Use target)`` to create the mask from the target image.

   * - Option
     - The method used to create the mask: ``Use as mask``; ``Min Display Value``; ``Use ROI``; or ``Threshold``.

   * - Threshold Method
     - The threshold method for the ``Threshold`` option. The methods are the same as the :ref:`thresholding_plugins:Mask Creator`.

   * - Channel
     - The channel of the source image to use. Use zero for all channels.

   * - Slice
     - The z-slice of the source image to use. Use zero for all z-slices.

   * - Frame
     - The frame of the source image to use. Use zero for all frames.

.. index:: Apply Mask Output

Output
~~~~~~

Pixels in the target image outside the mask are set to zero. A single image can be restored using ``Edit > Undo``; a stack cannot be restored.

The mask must have the same width and height as the target image. If the mask has fewer channels, z-slices or frames than the target image then the last mask plane in that dimension is used; for example a single mask plane is applied to every plane of the target image. Any difference in the dimensions is recorded in the ``ImageJ`` log.

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
