.. index:: ! Cells Plugins

Cells Plugins
=============

The GDSC plugins provide various tools for analysis of cells from microscopy images.

The plugins are described in the following sections using the order presented on the
``Plugins > GDSC > Cells`` menu.

.. index:: ! Cell Outliner

Cell Outliner
-------------

Find cell edges using marked seed points. Best suited to identify round cells with edge pixels either lighter or darker than the cell.

The plugin requires a greyscale image (8-bit, 16-bit or 32-bit) with a point ROI marking the centre of each cell. A multi-point ROI can be used to outline many cells in a single run. The output is a mask image containing a filled outline of each cell.

.. index:: Cell Outliner Method

Method
~~~~~~

The plugin performs the following steps for each point:

#. Search for edges around the point at the expected cell radius. The search looks for edges that curve around the point, like the edge of a round cell.
#. Start with a circle of the expected radius as the outline of the cell.
#. Find the strongest edge in each direction around the point, favouring edges close to the current outline. Join these edges to make a new outline (a polygon).
#. Fit an oval shape (an elliptical fit) to the new outline. The oval can be egg-shaped. Use this as the current outline and repeat from step 3 to refine the outline.
#. Output the final outline, or the fitted oval shape. The outline can be expanded to cover the entire cell.

If an oval shape cannot be fitted then a message is written to the ``ImageJ`` log and the polygon outline is used.

.. index:: Cell Outliner Plugin Interface

Plugin Interface
~~~~~~~~~~~~~~~~

The ``Cell Outliner`` plugin uses a standard ``ImageJ`` dialog. The following parameters can be configured:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description

   * - Cell radius
     - The expected radius of the cells (in pixels). This sets the curvature of the edge detection kernels and the initial circular outline of each cell.

   * - Tolerance
     - The tolerance for the cell radius. The search for edges extends to the cell radius multiplied by (1 + tolerance).

   * - Kernel width
     - The width of the edge detection kernels (in pixels). An even width is reduced by 1 to an odd width. The minimum is 3.

   * - Dark edge
     - Set to **true** if the edge of the cell is darker than the cell and the background, for example a cell membrane in a transmitted light image. Set to **false** if the edge is lighter.

   * - Kernel smoothing
     - The scale of the Difference-of-Gaussians edge profile. Higher values detect broader edges and reduce the effect of noise.

   * - Polygon smoothing
     - The number of points on either side of each polygon point used to smooth the polygon coordinates with a moving average. Use zero to disable smoothing. The window is limited to a quarter of the polygon points.

   * - Weighting gamma
     - The gamma applied to the weights computed from the distance to the current cell outline. The normalised weight :math:`w` is transformed to :math:`w^{1/\gamma}`. A gamma above 1 increases the weight of edges away from the current outline; a gamma below 1 restricts the outline to edges close to the current outline.

   * - Iterations
     - The number of iterations to refine the cell outline. The minimum is 1.

   * - Show elliptical fit
     - Set to **true** to output the elliptical fit of the cell outline. The default outputs the polygon outline.

   * - Dilate
     - The number of 1 pixel dilations to apply to the final outline. Use this to expand an outline drawn on the cell edge to encompass the entire cell.

   * - Preview
     - Show the cell outlines on the image as an overlay. The preview is updated when the parameters are changed.

**Debug**

Hold the ``Shift`` key when running the plugin to show the ``Debug`` option. This displays images of the intermediate processing steps, for example the edge projection and the weight map, during the preview.

**Stacks**

If the image has multiple z-slices or frames then a second dialog is shown after the parameters are set. Select ``Yes`` to process the selected dimensions: the ``All frames`` and ``All slices`` options select to process all the frames and/or z-slices of the current channel; otherwise only the current frame and z-slice are processed. Select ``No`` to process only the current frame and z-slice. The same point ROI is used for every image plane.

.. index:: Cell Outliner Output

Output
~~~~~~

The plugin creates a mask image named using the title of the input image with the suffix ``Cell Outline``. If an image with this name is open then it is replaced.

Each cell outline is filled with the number of the point in the ROI, starting from 1. The background is zero. The mask is an 8-bit image, or a 16-bit image if there are more than 255 points. If cell outlines overlap then the higher numbered cell is drawn over the lower numbered cell.

If multiple z-slices or frames are processed the mask is a hyperstack with a single channel. Each image plane is labelled using the frame and z-slice (``t<frame>z<slice>``).

The mask image is displayed using a spectrum LUT with the background shown as black. The point ROI is copied to the mask image.

.. index:: ! Nuclei Outline

Nuclei Outline
--------------

Outline nuclei across multiple frames. The plugin identifies nuclei that are brighter than the background using filtering and thresholding. Large objects are divided using a watershed and the outline of each nucleus is output as an overlay and/or a table of results.

The plugin processes all the frames of the current channel and z-slice of the image. Each frame is analysed independently; nuclei are not tracked between frames.

.. index:: Nuclei Outline Method

Method
~~~~~~

Each frame is processed to:

#. Filter the image using a Gaussian blur. If a second, larger blur is configured then a difference-of-Gaussians filter is used: the image with the larger blur (the local background) is subtracted from the image with the smaller blur.
#. Mask all the nuclei using an automatic threshold method.
#. Fill holes in the mask.
#. Remove outliers from the mask using a rank filter. A nucleus pixel is removed if the median of the surrounding pixels is background. This removes small foreground regions, for example noise, and smooths the outline of the nuclei.
#. Identify each nucleus as a region of 4-connected pixels. Remove nuclei below the minimum size.
#. Divide nuclei above the maximum size using a watershed. Any new nuclei below the minimum size are removed.

The nucleus sizes use the calibrated units of the image. If the image is not calibrated then the units are pixels.

.. index:: Nuclei Outline Plugin Interface

Plugin Interface
~~~~~~~~~~~~~~~~

The ``Nuclei Outline`` plugin uses a non-blocking dialog. This allows the current frame of the image to be changed while the dialog is open. The following parameters can be configured:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description

   * - Blur
     - The standard deviation of the Gaussian blur (in pixels). Use zero to disable filtering; in this case the ``Blur2`` parameter is ignored.

   * - Blur2
     - The standard deviation of the second Gaussian blur (in pixels). If this is larger than ``Blur`` then a difference-of-Gaussians filter is used.

   * - Threshold method
     - The automatic threshold method used to create the mask of the nuclei.

   * - Outlier radius
     - The radius (in pixels) of the rank filter used to remove outliers from the mask. Use zero to disable outlier removal.

   * - Outlier threshold
     - The threshold of the rank filter used to remove outliers from the mask. A pixel is replaced by the median of the surrounding pixels if it is brighter than the median by more than the threshold. The mask uses values of 0 for the background and 255 for the nuclei; any threshold below 255 removes nucleus pixels where the median is background.

   * - Max nucleus size
     - The maximum size of a nucleus (in calibrated units squared). Larger nuclei are divided using a watershed.

   * - Min nucleus size
     - The minimum size of a nucleus (in calibrated units squared). Smaller nuclei are removed.

   * - Preview
     - Show the outline of the nuclei on the current frame of the image as an ROI. The preview is updated when the parameters or the current frame are changed.

   * - Preview In Out
     - Show the inside and outside regions of the nuclei as an overlay during the preview. The inside region is shown in white and the outside region in yellow.

   * - Erosion
     - The number of pixels to erode each nucleus to define the inside region.

   * - Expansion inner
     - The number of pixels to expand each nucleus to define the inner edge of the outside region. Pixels within this distance of the nucleus are excluded from the outside region.

   * - Expansion
     - The width (in pixels) of the outside region. The outside region is a ring around the nucleus starting at the ``Expansion inner`` distance.

The inside and outside regions are not used by this plugin. The settings are saved and used as the default settings for the ``Nucleus detector`` that the GDSC plugins provide for `TrackMate <https://imagej.net/plugins/trackmate/>`_. The TrackMate detector outlines nuclei using the same method and computes the mean intensity of the inside and outside regions in an analysis channel. The ``Preview In Out`` option allows the regions to be checked.

The settings are saved between ``ImageJ`` sessions.

**Output Options**

When the dialog is closed a second dialog is shown to select the output options:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description

   * - Results overlay
     - Show the outline of each nucleus as an overlay on the image.

   * - Results table
     - Show a table of the nuclei.

If no output option is selected then the plugin exits without analysing the image.

.. index:: Nuclei Outline Results Overlay

Results Overlay
~~~~~~~~~~~~~~~

The outline of each nucleus is added to an overlay on the image. Each outline is shown only on the frame where the nucleus was identified (and the analysed channel for a hyperstack). Any existing overlay is replaced.

.. index:: Nuclei Outline Results Table

Results Table
~~~~~~~~~~~~~

The ``Nuclei Outline Summary`` table contains a row for each nucleus with the following information:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Result
     - Description

   * - Image
     - The image title.

   * - Channel
     - The analysed channel.

   * - Frame
     - The frame.

   * - Centre X
     - The X coordinate of the centre of the nucleus (in pixels).

   * - Centre Y
     - The Y coordinate of the centre of the nucleus (in pixels).

   * - Size
     - The size of the nucleus (in pixels).

   * - Radius
     - The radius of the nucleus (in pixels) estimated by assuming the nucleus is a circle of the same size.
