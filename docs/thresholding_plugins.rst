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

The plugin processes an 8-bit, 16-bit or 32-bit image. The dialog shows the ``Otsu`` threshold of the image as a guide for the background level. If the image has an area ROI then the mask is cleared outside the ROI.

.. index:: Edge Mask Plugin Interface

Plugin Interface
~~~~~~~~~~~~~~~~

The ``Edge Mask`` plugin uses a standard ``ImageJ`` dialog. The following parameters can be configured:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description

   * - Method
     - The method used to create the mask:

       * ``Above background``: Pixels at or above the background level.
       * ``Laplacian edges``: The zero-crossings of the Laplacian of the image.
       * ``Gradient edges``: Pixels with a high gradient magnitude.
       * ``Maximum gradient edges``: Pixels with a high gradient magnitude that are the maximum across the edge (non-maximum suppression). This creates thin edges.

   * - Background
     - The background level. Pixels in the smoothed image below the background are excluded from the mask.

   * - Smooth
     - The standard deviation of the Gaussian blur applied to the image before comparison to the background. This is also the scale used to compute the Laplacian and gradient; a value of zero uses a scale of 1.

   * - Lower percentile
     - The lower percentile of the gradient magnitude used to select gradient edges.

   * - Upper percentile
     - The upper percentile of the gradient magnitude used to select gradient edges.

       If both percentiles are above zero then edges are selected using hysteresis thresholding: pixels above the upper limit, and connected pixels above the lower limit. If only one percentile is above zero then pixels above that limit are selected.

   * - Prune
     - For edge methods, remove lines that are not part of a closed loop, and isolated points.

   * - Fill
     - For ``Laplacian edges``, fill closed loops on the inside of the edge (the negative side of the Laplacian) to create a mask.

   * - Replace image
     - Replace the input image with the mask. If not selected then a new image is created.

   * - Preview
     - Show the mask on the image. The preview is updated when the parameters are changed.

.. index:: Edge Mask Output

Output
~~~~~~

The mask is an 8-bit image with mask pixels set to 255. If ``Replace image`` is not selected then the mask is shown as a new image named using the image title with the suffix ``Edge Mask``. For a stack image ``ImageJ`` asks if all the image planes should be processed.

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

The plugin requires a binary 8-bit image (pixels of 0 and 255). The foreground is defined using the ``ImageJ`` ``Black background`` binary option. The skeleton is created using the ``ImageJ`` skeletonise method with extra corner pixels removed so the lines are a single pixel wide.

Each skeleton pixel is classified as a node or edge using the number of lines leaving the pixel:

* Terminus: the end of a line (one line) or an isolated pixel.
* Edge: a pixel within a line (two lines).
* Junction: a pixel where lines meet (three or more lines).

The skeleton is divided into lines between nodes. Closed loops are also extracted as lines. The lines are sorted by length, longest first.

.. index:: Skeleton Analyser Plugin Interface

Plugin Interface
~~~~~~~~~~~~~~~~

The ``Skeleton Analyser`` plugin uses a standard ``ImageJ`` dialog. The following parameters can be configured:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description

   * - Prune junctions
     - Remove the shortest line from each junction that ends in a terminus, until no junctions remain. This removes short branches from the skeleton.

   * - Min length
     - The minimum length (in pixels) of a line to report in the table and overlay.

   * - Show node map
     - Show a colour image of the skeleton: terminus pixels are blue; edge pixels are red; and junction pixels are green.

   * - Show overlay
     - Label the start of each line on the node map. Only used with ``Show node map``.

   * - Show table
     - Show a table of the lines.

.. index:: Skeleton Analyser Output

Output
~~~~~~

The input image is replaced with the skeleton. The change can be reverted using ``Edit > Undo``. For a stack image ``ImageJ`` asks if all the image planes should be processed.

The node map is named using the image title with the suffix ``SkeletonNodeMap``.

The ``Skeleton Analyser Results`` table contains a row for each line with the following information:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Result
     - Description

   * - ID
     - The line ID. The lines are sorted by length, longest first.

   * - StartX, StartY
     - The start of the line.

   * - EndX, EndY
     - The end of the line.

   * - Length (px)
     - The length of the line in pixels. Diagonal steps have a length of :math:`\sqrt{2}`.

   * - Length
     - The length of the line in calibrated units.

   * - Unit
     - The calibrated unit.

If there are more than 1000 lines then the plugin asks to confirm the table should be shown.

.. index:: ! Thread Analyser

Thread Analyser
---------------

Analyses an image using a given mask. Skeletonises the mask image and extracts a set of lines connecting node points on the skeleton. Output statistics about each of the lines using the original image and the Euclidean Distance Map (EDM) of the mask.

The plugin processes 8-bit or 16-bit images. The mask image is thresholded unless it is already a mask (all non-zero pixels have the same value). The mask is skeletonised and divided into lines (threads) using the same method as the :ref:`thresholding_plugins:Skeleton Analyser`. For each thread the plugin extracts the profile of the image intensity and the EDM along the line and counts the maxima of the image intensity. An optional object image can be used to count objects along each thread.

.. index:: Thread Analyser Plugin Interface

Plugin Interface
~~~~~~~~~~~~~~~~

The ``Thread Analyser`` plugin uses a standard ``ImageJ`` dialog. The following parameters can be configured:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description

   * - Image
     - The image used to measure the intensity along the threads.

   * - Mask
     - The image defining the threads.

   * - Objects
     - An optional image defining objects on the threads. The image is thresholded unless it is already a mask.

   * - Threshold method
     - The threshold method used to create the mask and object images.

   * - Result directory
     - The directory used to save the thread profiles. Leave empty to not save the profiles.

   * - Min length
     - The minimum length (in pixels) of a thread.

   * - Show skeleton
     - Show the skeleton node map (see :ref:`thresholding_plugins:Skeleton Analyser`).

   * - Show skeleton map
     - Show an image of the threads. Each thread has the value of its ID.

   * - Show skeleton image
     - Show the image intensity on the skeleton pixels. All other pixels are zero.

   * - Show skeleton EDM
     - Show the Euclidean Distance Map of the mask. The value of each pixel is the distance to the background.

   * - Show object image
     - Show the object mask.

   * - Label threads
     - Label the threads on the output images with their ID.

If an image has multiple channels then a second dialog is shown to select the channel of each image.

.. index:: Thread Analyser Results Table

Results Table
~~~~~~~~~~~~~

The ``Thread Analyser Results`` table contains a row for each thread with the following information:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Result
     - Description

   * - ID
     - The thread ID. The threads are sorted by length, shortest first.

   * - StartX, StartY
     - The start of the thread.

   * - EndX, EndY
     - The end of the thread.

   * - Length
     - The length of the thread in pixels.

   * - Maxima
     - The number of maxima of the image intensity along the thread.

   * - InnerMaxima
     - The number of maxima that are not at the ends of the thread.

   * - Objects
     - The number of objects along the thread.

   * - InnerObjects
     - The number of objects that are not at the ends of the thread.

   * - Img Min, Img Max, Img Av, Img SD
     - The minimum, maximum, mean and standard deviation of the image intensity along the thread.

   * - EDM Min, EDM Max, EDM Av, EDM SD
     - The minimum, maximum, mean and standard deviation of the EDM along the thread. The EDM is a measure of the thread width.

If there are more than 1000 threads then the plugin asks to confirm the table should be shown. The output images and the result file are created even if the table is not shown.

**Result file**

If a ``Result directory`` is specified then the profile of each thread is saved to a comma-separated file named using the image and mask titles, e.g. ``image.tif_mask.tif.threads.csv``. For each thread the file contains the thread line (ID, start, end and length), then the profiles: ``Distance`` along the thread, ``Image Intensity``, ``EDM Intensity``, ``Objects``, and the distances of the ``Image Maxima`` and ``Object Maxima``.

**Output images**

The output images are named using the mask title with the suffix ``EDM``, ``SkeletonNodeMap`` or ``SkeletonMap``, the image title with the suffix ``Threads``, and the object image title with the suffix ``Objects``.

.. index:: ! Foreground Analyser

Foreground Analyser
-------------------

Threshold pixels inside an ROI and analyse foreground pixels (count; sum; mean; and standard deviation). Optionally the analysis can use the entire stack and the foreground pixels can be shown as a mask.

The plugin processes an 8-bit, 16-bit or 32-bit image. The threshold is computed from the histogram of the pixels inside the ROI (or the entire image). Pixels above the threshold are foreground. The image is not changed.

.. index:: Foreground Analyser Plugin Interface

Plugin Interface
~~~~~~~~~~~~~~~~

The ``Foreground Analyser`` plugin uses a standard ``ImageJ`` dialog. The following parameters can be configured:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description

   * - Threshold method
     - The threshold method.

   * - Show mask
     - Show a mask of the foreground pixels.

   * - Do stack
     - Analyse all the z-slices of the current channel and frame. If not selected then only the current image plane is analysed. Only shown for an image with multiple z-slices.

   * - Histogram bins
     - The number of histogram bins used to threshold a 32-bit image. Only shown for a 32-bit image.

.. index:: Foreground Analyser Results Table

Results Table
~~~~~~~~~~~~~

The ``Foreground Analyser Results`` table contains a row for each analysis with the following information:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Result
     - Description

   * - Image
     - The image title.

   * - C, Z, T
     - The channel, z-slice and frame. For the entire stack the z-slice is the range, e.g. ``1-10``.

   * - Roi
     - The bounds of the ROI: ``x,y widthxheight``.

   * - Pixels
     - The number of pixels analysed.

   * - Threshold
     - The threshold.

   * - Sum
     - The sum of the foreground pixels.

   * - N
     - The number of foreground pixels.

   * - Mean
     - The mean of the foreground pixels.

   * - SD
     - The standard deviation of the foreground pixels.

The mask image is named ``Foreground Analyser``. Foreground pixels have the value 255.

.. index:: ! Threshold Outliner

Threshold Outliner
------------------

Outlines objects defined in a mask image. The pixels of each object are thresholded using an auto-threshold method or a manual percentile. Optionally outliers are removed using local outlier probability (LoOP). The remaining pixels are outlined using a convex or concave hull. Creates an ROI overlay of the outlines and a new mask image.

The plugin processes the current channel and frame of an 8-bit or 16-bit image. The objects are defined in a mask image: each object is a connected region of non-zero pixels with the same value. For each object the plugin:

#. Thresholds the image pixels inside the object using the histogram of the object pixels. Pixels above the threshold are kept.
#. Optionally removes outlier pixels using local outlier probability (LoOP). The LoOP score of a pixel is the probability that it is an outlier from its nearest neighbour pixels.
#. Creates a polygon outline around the kept pixels using a hull.

.. index:: Threshold Outliner Plugin Interface

Plugin Interface
~~~~~~~~~~~~~~~~

The ``Threshold Outliner`` plugin uses a non-blocking dialog. This allows the current z-slice of the image to be changed during the preview. The following parameters can be configured:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description

   * - Mask image
     - The image defining the objects. Only 8-bit or 16-bit images with the same width and height as the image, and a single z-slice or the same number of z-slices, are listed.

   * - Threshold method
     - The threshold method applied to the pixels of each object. ``Manual percentile`` uses the ``Percentile`` parameter.

   * - Percentile
     - The fraction (0 to 1) of the object pixels below the threshold for the ``Manual percentile`` method. For example 0.9 keeps the brightest 10% of the pixels.

   * - Outlier neighbours
     - The number of nearest neighbours used to compute the local outlier probability.

   * - Outlier threshold
     - The local outlier probability above which a pixel is removed. Use 1 to disable outlier removal.

   * - Hull method
     - The method used to outline the pixels:

       * ``Convex``: The convex hull.
       * ``Digging concave``: A concave hull created by digging into the convex hull. A smaller ``Digging threshold`` creates a more concave outline.
       * ``Knn concave``: A concave hull created using the ``K neighbours`` nearest neighbours. A smaller number creates a more concave outline.

   * - Digging threshold
     - The threshold for the ``Digging concave`` hull.

   * - K neighbours
     - The number of nearest neighbours for the ``Knn concave`` hull.

   * - Colour
     - The colour of the outlines and the preview pixels.

   * - Opacity
     - The opacity of the coloured pixels shown in the preview.

   * - Preview
     - Show the pixels above the threshold (coloured) and the outlines on the image. The preview is updated when the parameters or the z-slice are changed.

.. index:: Threshold Outliner Output

Output
~~~~~~

The outlines are added to the image as an overlay. Any existing overlay is replaced. For a stack ``ImageJ`` asks if all the z-slices of the current channel and frame should be processed.

A mask image is created named using the image title with the suffix ``Mask``. Each outline is filled with the value 255. The mask has a slice for each processed z-slice.
