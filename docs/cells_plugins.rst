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

For each point ROI the plugin uses a curved edge filter to find edges within a range of an expected radius. The search region around each point extends to the radius multiplied by (1 + tolerance). Edge detection is performed using an approximation to the Laplacian-of-Gaussian (LoG) and the kernel size and smoothing can be controlled.

The edge detection is weighted using the distance to the current outline of the cell (initially a circle of the specified radius around the point). A polygon outline is constructed using the highest edge value in each 10 degree segment around the centre. This is fitted using an oval shape of two ellipses back-to-back and the process iterated using the new cell outline. The final outline can be output as the polygon shape or the elliptical fit. This can be dilated to expand the outline to encompass the entire cell.

Supports a preview allowing the parameters to be interactively set. The final call produces a mask image and options are provided to process each plane/frame of a hyperstack.

.. index:: ! Nuclei Outline

Nuclei Outline
--------------

Outline nuclei across multiple frames. Performs the following:

- Filter the image (Gaussian blur, or a difference of Gaussians if a second blur is configured)
- Mask all the nuclei using thresholding
- Fill holes and filter outliers
- Remove small nuclei based on size
- Divide large nuclei based on size using watershed
- Provides output options: ``Results overlay`` (ROIs); and ``Results table``
