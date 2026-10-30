.. index:: ! Utility Plugins

Utility Plugins
===============

The GDSC plugins provide various utility tools for image analysis. The tools
include:

- Difference of Gaussians and Laplacian of Gaussian filters for contrast enhancement
- Image alignment
- Mask conversion and measurement tools

The plugins are described in the following sections using the order presented on the
``Plugins > GDSC > Utils`` menu.

.. index:: ! Contrast Enhancer

Contrast Enhancer
-----------------

Runs the ``ImageJ`` contrast enhancer (see ``Process > Enhance Contrast``) on all the open images using 0.35% saturated pixels. This allows a one-click adjustment of all the open images for optimal viewing.

.. index:: ! Difference of Gaussians

Difference of Gaussians
-----------------------

Performs a difference of Gaussians filter for local contrast enhancement. The filter performs subtraction of one blurred version of an image from another, less blurred version of the original. The result is an image containing only the information contained within the spatial frequency between the two blurred images.

The image is blurred using a Gaussian filter with a large standard deviation (``Sigma1``) and a small standard deviation (``Sigma2``). The large blur is subtracted from the small blur. The large blur removes the local background and the small blur removes the noise, leaving features with a size between the two scales.

The filter supports all image types. A colour image is filtered in each colour channel. The filtered image is written to the input image type; for an 8-bit or 16-bit image any negative values are set to zero. Convert the image to 32-bit to keep negative values.

.. index:: Difference of Gaussians Plugin Interface

Plugin Interface
~~~~~~~~~~~~~~~~

The ``Difference of Gaussians`` plugin uses a standard ``ImageJ`` dialog. The following parameters can be configured:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description

   * - Sigma1 (Radius)
     - The standard deviation of the large Gaussian blur. This defines the scale of the local contrast to remove. This must be larger than ``Sigma2``.

   * - Sigma2 (Radius)
     - The standard deviation of the small Gaussian blur. This defines the scale of the noise to remove.

   * - Maintain Ratio
     - If selected then changing one sigma updates the other sigma to keep the same ratio between them.

   * - Scaled Units
     - If selected then the sigma values are in the calibrated units of the image; otherwise they are in pixels. This option is only shown if the image is calibrated.

   * - Enhance Contrast
     - Set the display range of the filtered image using 0.35% saturated pixels.

   * - Preview
     - Show the filtered image. The preview is updated when the parameters are changed.

.. index:: Difference of Gaussians Output

Output
~~~~~~

The filtered image replaces the pixels of the input image. The change can be reverted using ``Edit > Undo``. If the image has an area ROI then only the pixels within the ROI are filtered. For a stack image ``ImageJ`` asks if all the image planes should be processed.

.. index:: ! Laplacian of Gaussian

Laplacian of Gaussian
---------------------

Performs a Laplacian of Gaussian filter for local contrast enhancement.

The Laplacian is the sum of the second derivatives of the image in the X and Y directions. The derivatives are computed after smoothing the image with a Gaussian filter. The filter has a strong response for round features (blobs) with a radius of approximately 1.41 times the Gaussian standard deviation. A bright blob has a negative response; a dark blob has a positive response.

Requires the ``Image Science`` library (see :ref:`installation:Installation` instructions).

The filter supports 8-bit, 16-bit and 32-bit images. The filtered image is written to the input image type; for an 8-bit or 16-bit image any negative values are set to zero. Convert the image to 32-bit to keep negative values, or use the ``Negate`` option to show bright blobs.

.. index:: Laplacian of Gaussian Plugin Interface

Plugin Interface
~~~~~~~~~~~~~~~~

The ``Laplacian of Gaussian`` plugin uses a standard ``ImageJ`` dialog. The following parameters can be configured:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description

   * - Sigma (Radius)
     - The standard deviation of the Gaussian filter. Use a value of the expected feature radius divided by 1.41.

   * - Scaled Units
     - If selected then the sigma is in the calibrated units of the image; otherwise it is in pixels. This option is only shown if the image is calibrated.

   * - Scale normalised
     - Multiply the result by the square of the sigma. This allows the response of filters with different sigma to be compared.

   * - Negate
     - Negate the result. Bright blobs have a positive response.

   * - Enhance Contrast
     - Set the display range of the filtered image using 0.35% saturated pixels.

   * - Preview
     - Show the filtered image. The preview is updated when the parameters are changed.

.. index:: Laplacian of Gaussian Output

Output
~~~~~~

The filtered image replaces the pixels of the input image. The change can be reverted using ``Edit > Undo``. For a stack image ``ImageJ`` asks if all the image planes should be processed.

.. index:: ! Align Images

Align Images
------------

Aligns an image stack to a reference image using XY translation to maximise the normalised correlation. Outputs a new stack with the best alignment with optional sub-pixel accuracy (performed using a Gaussian fit or cubic spline fit).

Contains options to:

- Restrict the translation space
- Provide a mask image to specify which pixels to include in the analysis
- Clip the bicubic interpolated output to fit within the min-max of the original image

Each image in the target stack is compared to the reference image at every translation within the configured range. The score is the Pearson correlation of the overlapping pixels. Pixels with a value of zero, or a saturated value (the maximum of an 8-bit, 10-bit, 12-bit or 16-bit image), are ignored. The translation with the highest score is used to align the target image.

This plugin computes the correlation directly at each translation. It is slow for a large translation range; in this case use the :ref:`utility_plugins:Align Images FFT` plugin.

.. index:: Align Images Plugin Interface

Plugin Interface
~~~~~~~~~~~~~~~~

The ``Align Images`` plugin uses a standard ``ImageJ`` dialog. The images must be greyscale (8-bit, 16-bit or 32-bit). The following parameters can be configured:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description

   * - Reference image
     - The reference image. The current image plane is used.

   * - Reference mask
     - An optional mask image. Only pixels in the reference image where the mask is non-zero are used to compute the correlation. The mask must have the same width and height as the reference image.

   * - Target image
     - The image stack to align. Each image plane is aligned to the reference. Use ``[None]`` to align the reference image stack.

   * - Min X translation
     - The minimum translation in the X direction (in pixels).

   * - Max X translation
     - The maximum translation in the X direction (in pixels).

   * - Min Y translation
     - The minimum translation in the Y direction (in pixels).

   * - Max Y translation
     - The maximum translation in the Y direction (in pixels).

   * - Sub-pixel method
     - The method used to refine the translation with sub-pixel accuracy:

       * ``None``: Use the integer translation with the highest score.
       * ``Cubic``: Search a cubic spline interpolation of the scores around the best translation.
       * ``Gaussian``: Fit a Gaussian to the scores around the best translation.

   * - Interpolation
     - The interpolation method used to translate the image by a sub-pixel offset: ``None``; ``Bilinear``; or ``Bicubic``. Integer offsets do not use interpolation.

   * - Clip output
     - Bicubic interpolation can create values above the maximum of the original image. If selected then these values are set to the maximum of the original image.

   * - Show correlation image
     - Show a stack of the scores for each translation. The image has a pixel for each translation in the range, starting from the minimum translation.

.. index:: Align Images Output

Output
~~~~~~

The aligned stack is shown as a new image named using the target image title with the suffix ``Aligned``. The optional correlation image uses the suffix ``Correlation``. If an image with the same name is open then it is replaced.

The plugin records the best translation for each image plane to the ``ImageJ`` log:

.. code-block:: text

    Best Slice 1  x 2.08  y -1.13 = 0.948 (interpolated score 0.951)

The ``x`` and ``y`` values are the translation; the score is the correlation at the best integer translation. If a sub-pixel method is used then the score is also estimated at the sub-pixel translation. The text ``Best Slice***`` marks a translation at the limit of the range; in this case the range should be increased.

If the inputs are invalid, for example the mask is a different size to the reference, then an error is shown and no output is created.

.. index:: ! Align Images FFT

Align Images FFT
----------------

Aligns an image stack to a reference image using XY translation to maximise the normalised correlation. The correlation is computed in the frequency domain using a fast Fourier transform (FFT). Edge artifacts of the frequency domain are reduced by using a window function on the image (gradually reducing the edge pixels to zero).

Outputs a new stack with the best alignment with optional sub-pixel accuracy (performed using a cubic spline fit).

Note: This plugin is faster than the :ref:`utility_plugins:Align Images` plugin for large images and/or a large translation space. Note that the results will differ slightly due to the use of different correlation methods.

The images are padded to a square with a power of 2 size for the FFT. By default the translation is restricted so that at least half of the smaller image overlaps the larger image.

.. index:: Align Images FFT Plugin Interface

Plugin Interface
~~~~~~~~~~~~~~~~

The ``Align Images FFT`` plugin uses a standard ``ImageJ`` dialog. The images must be greyscale (8-bit, 16-bit or 32-bit). The following parameters can be configured:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description

   * - Reference image
     - The reference image. The current image plane is used.

   * - Target image
     - The image stack to align. Each image plane is aligned to the reference. Use ``[Reference stack]`` to align the reference image stack.

   * - Window function
     - The window function applied to the images before the correlation is computed: ``None``; ``Hanning``; ``Cosine``; or ``Tukey``. The window function gradually reduces the edge pixels to zero to reduce edge artifacts in the frequency domain.

   * - Restrict translation
     - Restrict the translation to the configured range. If not selected then the translation is restricted so that at least half of the smaller image overlaps the larger image.

   * - Min X translation
     - The minimum translation in the X direction (in pixels).

   * - Max X translation
     - The maximum translation in the X direction (in pixels).

   * - Min Y translation
     - The minimum translation in the Y direction (in pixels).

   * - Max Y translation
     - The maximum translation in the Y direction (in pixels).

   * - Sub-pixel method
     - The method used to refine the translation with sub-pixel accuracy:

       * ``None``: Use the integer translation with the highest score.
       * ``Cubic``: Search a cubic spline interpolation of the scores around the best translation.

   * - Interpolation
     - The interpolation method used to translate the image by a sub-pixel offset: ``None``; ``Bilinear``; or ``Bicubic``. Integer offsets do not use interpolation.

   * - Clip output
     - Bicubic interpolation can create values above the maximum of the original image. If selected then these values are set to the maximum of the original image.

   * - Normalised
     - Normalise the correlation using the intensity of the reference image under the target image at each translation. The score is in the range -1 to 1. If not selected then the correlation is not normalised and bright regions of the reference image can dominate the alignment.

   * - Show correlation image
     - Show a stack of the correlation scores.

   * - Show normalised image
     - Show the reference and target images after the window function and padding are applied.

.. index:: Align Images FFT Output

Output
~~~~~~

The aligned stack is shown as a new image named using the target image title with the suffix ``Aligned``. The optional images use the suffixes ``Correlation``, ``Normalised Ref`` and ``Normalised Target``. If an image with the same name is open then it is replaced.

The plugin records the best translation for each image plane to the ``ImageJ`` log:

.. code-block:: text

    Best Slice 1  x 2.08  y -1.13 = 0.948 (interpolated score 0.951)

.. index:: ! Align Stacks

Align Stacks
------------

Aligns open image stacks to a reference image using XY translation to maximise the normalised correlation. Accepts multi-dimensional stacks. The self-align mode aligns all timepoints within a stack to the current frame.

For each timepoint a z-projection (e.g. maximum or average intensity) is performed per channel. The projection is thresholded using the Otsu method to extract the signal, normalised to 0-255 and the resulting channel images are tiled to a composite image. The composite is then aligned using the maximum correlation between the images computed using a fast Fourier transform (FFT) with an optional window function. Using a composite ensures each channel contributes equally to the alignment. The translation is applied to the entire stack for that timepoint.

The plugin has two modes:

* Align other stacks: All open images with the same type and dimensions as the reference image are aligned. If the reference has a single frame then each frame of the other images is aligned to the reference; only the width, height and channels must match. If the reference has multiple frames then each frame of the other images is aligned to the same frame of the reference; all the dimensions must match.
* Self-align: Each frame of the reference image is aligned to its current frame.

The aligned images are modified in place. This cannot be undone. The reference image is not modified, except in the self-align mode.

.. index:: Align Stacks Plugin Interface

Plugin Interface
~~~~~~~~~~~~~~~~

The ``Align Stacks`` plugin uses a standard ``ImageJ`` dialog. The images must be greyscale (8-bit, 16-bit or 32-bit). The following parameters can be configured:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description

   * - Reference image
     - The reference image.

   * - Self-align
     - Align all the frames of the reference image to its current frame.

   * - Projection
     - The z-projection method used to create a single image for each channel of a frame.

   * - Window function
     - The window function applied to the images before the correlation is computed: ``None``; ``Hanning``; ``Cosine``; or ``Tukey``. The window function gradually reduces the edge pixels to zero to reduce edge artifacts in the frequency domain.

   * - Restrict translation
     - Restrict the translation to the configured range. If not selected then the translation is restricted so that at least half of the image overlaps the reference.

   * - Min X translation
     - The minimum translation in the X direction (in pixels).

   * - Max X translation
     - The maximum translation in the X direction (in pixels).

   * - Min Y translation
     - The minimum translation in the Y direction (in pixels).

   * - Max Y translation
     - The maximum translation in the Y direction (in pixels).

   * - Sub-pixel method
     - The method used to refine the translation with sub-pixel accuracy:

       * ``None``: Use the integer translation with the highest score.
       * ``Cubic``: Search a cubic spline interpolation of the scores around the best translation.

   * - Interpolation
     - The interpolation method used to translate the image by a sub-pixel offset: ``None``; ``Bilinear``; or ``Bicubic``. Integer offsets do not use interpolation.

   * - Clip output
     - Bicubic interpolation can create values above the maximum of the original image. If selected then these values are set to the maximum of the original image.

The self-align mode can be run from a macro using:

.. code::

    call('uk.ac.sussex.gdsc.ij.utils.AlignStacks_PlugIn.selfAlign');

This shows the dialog with the self-align mode selected for the current image.

.. index:: ! Image Scaler

Image Scaler
------------

Scales all images in a stack by a single factor so that the maximum of the stack is set to the specified value. For example this can be used to scale a floating point image so that the maximum is 255 for correct display.

Can accept a list file of images allowing a set of images to be scaled together. The maximum of the entire set of images is used to calculate the scale factor applied to each image. Note that in this mode each image file is overwritten with the scaled image.

.. index:: ! Spot Analyser

Spot Analyser
-------------

Identifies particles (e.g. cells) using a Gaussian blur and thresholding, and excludes small particles based on pixel count and particles touching the image edge. The top N spots (``Max peaks``) are identified within each particle using the ``FindFoci`` algorithm; each spot region is defined using a fraction of the spot height. The average intensity of each channel inside and outside the spot regions is then compared using a t-test to identify increases in signal within spot regions.

.. index:: ! HSB Picker

HSB Picker
----------

Installs a tool into the ``ImageJ`` toolbar that allows Hue-Saturation-Brightness (HSB) values to be sampled from an RGB image by mouse click. Pixels are sampled using a radius around the clicked position. The average HSB values and the standard deviations from all sampled pixels are shown in a frame.

Provides the ability to call the ``HSB Filter``. The filter values are set using the averages and the widths using the scaled standard deviations (e.g. av +/- scale * std.dev.).

.. index:: ! HSB Filter

HSB Filter
----------

Provides the ability to filter pixels in an RGB image using Hue-Saturation-Brightness (HSB) values. All pixels not within the specified width of the given values are set to zero.

Note that hue is a continuous property where values are wrapped around from 1 to 0.

.. index:: ! Spot Separation

Spot Separation
---------------

Finds spots in an image. Locates the closest neighbour spot within a radius and produces a line profile through the spots. If no neighbour is present produces a line profile through the principle axis. Outputs the separation between the spot maxima to a table.

Allows analysis of regions using the ROI selection tools and respects manual thresholding to identify spots.

.. index:: ! Z Project extra

Z Project extra...
------------------

Extends the ``ImageJ`` ``Z Project`` command to add a ``Mode`` projection option. The mode can be calculated with or without ignoring zero values.

This plugin is useful for projecting 3D masks, for example those created by the
:ref:`findfoci_plugins:Find Foci Plugins`.

.. index:: ! Intensity Analysis

Intensity Analysis
------------------

Calculate the normalised intensity within an ROI using a stack of exposures (different imaging times). Exposures must be set within the slice labels. Saturated pixels within an ROI remove that slice from analysis. A linear fit is made of the mean versus exposure using a rolling window and the gradient reported for the best fit. The gradient is a measure of the pixel intensity within the ROI.

This plugin assumes images are taken of the same object at different exposure times on a camera with a linear response curve. Thus doubling the exposure should double the pixel value. The plugin allows analysis of images with a large dynamic range by capturing each object with a suitable exposure. Drift should be minimal for the best results.

.. index:: ! Measure 3D

Measure 3D
----------

Installs a tool into the ``ImageJ`` toolbar that allows distances to be measured in 3D. Distance is measured between two successive mouse clicks on the same channel and time frame of an image. Results are recorded to a table.

.. index:: ! Scale Space

Scale Space
-----------

Create a scale-space representation of an image using a 2D Gaussian filter at different scales. The output can be the Gaussian, Difference of Gaussians or Laplacian of Gaussian.

The ``Laplacian of Gaussian`` output requires the ``Image Science`` library (see :ref:`installation:Installation` instructions).

A scale-space represents an image at a series of increasing amounts of blurring. The scale (:math:`t`) is the variance of the Gaussian filter, i.e. the square of the standard deviation: :math:`t = \sigma^2`. The scale is doubled at each level (an octave); this increases the standard deviation by a factor of :math:`\sqrt{2}`. Additional levels can be added within each octave.

The plugin processes the current image plane.

.. index:: Scale Space Plugin Interface

Plugin Interface
~~~~~~~~~~~~~~~~

The ``Scale Space`` plugin uses a standard ``ImageJ`` dialog. The following parameters can be configured:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description

   * - Min scale
     - The minimum scale (the Gaussian variance). The minimum allowed scale is 0.25.

   * - Max scale
     - The maximum scale (the Gaussian variance). Use zero for the maximum allowed scale. The maximum allowed scale is limited so that the Gaussian standard deviation is a quarter of the smallest image dimension in X or Y.

   * - Output
     - The output image:

       * ``Gaussian``: The image blurred at each scale.
       * ``Difference of Gaussians``: The difference between the Gaussian images at consecutive scales. Features with a size between the two scales have a strong response. At least 2 scales are required.
       * ``Laplacian of Gaussian``: The scale-normalised Laplacian of Gaussian at each scale. The result is negated so that bright blobs have a positive response.

   * - Sub interval steps
     - The number of additional scales within each octave. The scales are spaced so that the ratio between consecutive scales is constant. For example with 1 step the scales are :math:`t`, :math:`\sqrt{2}t`, :math:`2t`, ... Use zero for only the octave scales.

.. index:: Scale Space Output

Output
~~~~~~

The plugin creates a new stack named using the input image title and the output, for example ``Image.tif Laplacian of Gaussian``. The ``Gaussian`` output is named using the suffix ``Scale Space``.

Each slice is labelled using its scale (``t=<scale>``). The ``Gaussian`` output starts with the input image at a scale of zero and has the type of the input image. The ``Difference of Gaussians`` slices are labelled using the two scales of the difference and are 32-bit images. The ``Laplacian of Gaussian`` output is a 32-bit image.

.. index:: ! FRAP Analysis

FRAP Analysis
-------------

Analyses an image stack to detect pixel regions that have been photobleached. The stack is first aligned to correct for drift. A plot of the intensity of each region is created over time and Fluorescence Recovery After Photobleaching (FRAP) models (exponential and diffusion-limited recovery) are fitted to each region. The results can optionally be saved to a directory.

.. index:: ! Mask to ROI

Mask to ROI
-----------

Traces each object in a mask image and adds them to the ROI Manager.

The mask must be an 8-bit or 16-bit image. An object is a connected region of pixels with the same non-zero value. This supports a binary mask and a labelled mask where each object has a different value, for example the mask image created by the :ref:`findfoci_plugins:FindFoci` plugin. The outline of each object is traced; any holes inside an object are included in the outline.

.. index:: Mask to ROI Plugin Interface

Plugin Interface
~~~~~~~~~~~~~~~~

The ``Mask to ROI`` plugin uses a standard ``ImageJ`` dialog. The following parameters can be configured:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description

   * - 8-connected
     - Trace the outline of objects using 8-connected pixels, i.e. pixels touching diagonally are part of the same object. If not selected then objects use 4-connected pixels.

   * - Stack
     - Trace the objects in every image plane of the stack. If not selected then only the current image plane is traced. This option is only shown for a stack image.

.. index:: Mask to ROI Output

Output
~~~~~~

Each traced object is added to the ROI Manager. For a stack image the ROI position is set to the image plane (the channel, z-slice and frame for a hyperstack) containing the object.

.. index:: ! Mask to Border

Mask to Border
--------------

Creates a border around each object in a mask image using variable width inside and outside the mask. Used to create a ring ROI of each mask object.

The mask must be an 8-bit or 16-bit image. Objects are defined by their pixel value. The border of each object has the same value as the object; all other pixels are set to zero. The border is created by:

#. Expanding the objects outwards by the ``outer`` width. Each expansion adds a 1 pixel layer to the outside of each object. Where expanding objects meet, a pixel is assigned to the object with the most contact.
#. Eroding the objects inwards by the ``inner`` width. Each erosion removes a 1 pixel layer from the edge of each object.
#. The border is the region of the expanded object that is not in the eroded object.

.. index:: Mask to Border Plugin Interface

Plugin Interface
~~~~~~~~~~~~~~~~

The ``Mask to Border`` plugin uses a standard ``ImageJ`` dialog. The following parameters can be configured:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description

   * - inner
     - The width of the border inside the object (in pixels). Use zero for a border only outside the object.

   * - outer
     - The width of the border outside the object (in pixels). Use zero for a border only inside the object.

       At least one of ``inner`` or ``outer`` must be above zero.

   * - extend outside
     - Treat the area outside the image as a continuation of the edge pixels. Objects touching the image edge are not eroded at the edge and the border does not follow the image edge. If not selected then the image edge is treated as the edge of the object.

   * - Preview
     - Show the border on the image. The preview is updated when the parameters are changed.

.. index:: Mask to Border Output

Output
~~~~~~

The border replaces the pixels of the input image. The change can be reverted using ``Edit > Undo``. For a stack image ``ImageJ`` asks if all the image planes should be processed.

.. index:: ! Mask Overlap

Mask Overlap
------------

Relabels a mask image using overlap between z and/or t dimensions. Allows mapping the same object through z and/or t dimensions with the same mask value.

The mask must be a 16-bit image with multiple z-slices or frames. Each object in an image plane must have a unique non-zero value. The values do not have to match between image planes. The plugin changes the values so that the same object has the same value in every image plane.

The plugin compares each image plane with the previous plane:

#. Compute the overlap of each object with the objects in the previous plane. The overlap is measured using the Intersection over Union (IoU): the number of pixels in both objects divided by the number of pixels in either object.
#. Match objects to objects in the previous plane with an IoU at or above a threshold. A matched object is assigned the value of the object in the previous plane.
#. Assign any unmatched object a new value that is not used by any other object.

For z overlap each z-slice is compared with the previous z-slice. For t overlap each frame is compared with the previous frame using all the z-slices of the frame. If both options are selected then the z overlap is performed first.

.. index:: Mask Overlap Plugin Interface

Plugin Interface
~~~~~~~~~~~~~~~~

The ``Mask Overlap`` plugin uses a standard ``ImageJ`` dialog. The following parameters can be configured:

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description

   * - Mask
     - The mask image. Only 16-bit images with multiple z-slices or frames are listed.

   * - All channels
     - Process all the channels independently. If not selected then only the current channel is processed.

   * - Z overlap
     - Relabel the objects using the overlap between z-slices.

   * - T overlap
     - Relabel the objects using the overlap between frames.

   * - IoU threshold
     - The minimum Intersection over Union (IoU) for objects to be matched. Objects must overlap to be matched; a threshold of zero matches objects with any overlap.

   * - Method
     - The method used to match the objects:

       * ``Nearest Neighbour``: Match the pairs of objects with the highest overlap first.
       * ``Minimum Distance``: Find the matching that maximises the total overlap of all matched objects.

.. index:: Mask Overlap Output

Output
~~~~~~

The object values in the mask image are changed. This cannot be undone. The maximum number of objects is 65535; an error is raised if more unique values are required.

The display range of the mask is set from zero to the maximum object value in the stack.
