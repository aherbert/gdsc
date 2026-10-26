.. index:: ! Installation

Installation
============

The plugin is designed to run within ``ImageJ``. This can be the original ``ImageJ`` (version 1) or any modified version of ``ImageJ`` such as ``ImageJ2`` or ``Fiji``.


.. index:: ! Install using ImageJ2/Fiji

Install using ImageJ2/Fiji
--------------------------

The GDSC plugins are distributed using an ``ImageJ2``/``Fiji`` update site. This allows the plugins to be easily installed and kept up-to-date. To install the plugins using ``Fiji`` just follow the instructions here:

* https://imagej.net/update-sites/following

Add the ``GDSC`` update site. ``Fiji`` will automatically check for a new version during start-up and install it if desired.

All the plugins will appear under the ``Plugins > GDSC`` menu.

The ``Edge Mask`` and ``Laplacian of Gaussian`` plugins, and the ``Laplacian of Gaussian`` output of the ``Scale Space`` plugin, depend on the ``Image Science`` library. If you wish to use these then you must also enable the ``ImageScience`` update site. This was previously installed by default within Fiji but changed to an optional component in August 2016.


.. index:: ! Install using ImageJ version 1

Install using ImageJ version 1
------------------------------

The plugin is designed to run within ``ImageJ``. You can obtain the latest version of ``ImageJ`` from:

* https://imagej.net/ij/download.html

To get the plugins you can download the latest ``jar`` files from the update site and put them in your ``ImageJ`` plugins folder. The jars can be found here:

* https://sites.imagej.net/GDSC/

You will also need to install the additional libraries listed in the table below. These are already included in ``Fiji`` so are not on the update site. A few plugins require the Image Science 3 library. The libraries can be obtained from Maven Central; for example:

* `Apache Commons Math 3 <https://repo.maven.apache.org/maven2/org/apache/commons/commons-math3/3.6.1/>`_
* `Image Science 3 <https://imagescience.org/meijering/software/imagescience/>`_
* `Maven Central <https://repo.maven.apache.org/maven2/>`_

Place all of the following ``jar`` files into the ``ImageJ`` plugins directory:

.. list-table::
   :widths: 30 70
   :header-rows: 1

   * - Jar File
     - Description

   * - beansbinding
     - Contains routines to bind Java bean properties (used by the ``FindFoci`` GUI).

   * - commons-lang3
     - Contains Java language utility routines.

   * - commons-math3
     - Contains math routines.

   * - commons-numbers-*
     - Contains number routines.

   * - commons-rng-*
     - Contains random number routines.

   * - commons-statistics-*
     - Contains statistics routines.

   * - gdsc-core
     - Contains the GDSC core library.

   * - gdsc-core-ij
     - Contains the GDSC core ``ImageJ`` library.

   * - fastutil-core
     - Library for type-specific Java collections.

   * - guava, failureaccess
     - Contains Google core libraries for Java.

   * - imagescience
     - Contains image analysis routines.

   * - quickhull3d
     - A java implementation of the QuickHull algorithm.

The plugins will be visible under the ``Plugins > GDSC`` menu.


.. index:: ! Toolsets

Toolsets
--------

The plugins jar contains ``ImageJ`` macro toolsets that add buttons to the ``ImageJ`` toolbar to run some of the plugins. The toolset files are not installed automatically. To use a toolset, extract the file from the plugins jar (using an archiving utility; the jar is a zip file) and place it in the ``ImageJ/macros/toolsets`` directory. The toolset can then be selected using the ``More Tools`` menu button (``>>``, the last button on the ``ImageJ`` toolbar).

The toolset files are located in the jar in the directory ``uk/ac/sussex/gdsc/ij/macros/toolsets``:

.. list-table::
   :widths: 30 70
   :header-rows: 1

   * - Toolset
     - Description

   * - Spot Distance Tools.txt
     - Buttons to run the :ref:`findfoci_plugins:Spot Distance` plugin; run the plugin with extra options; redo the analysis using the same parameters; and undo the last analysis (delete the last result).

   * - Translocation Finder Tools.txt
     - Buttons to run the :ref:`findfoci_plugins:Translocation Finder` plugin; and to add the :ref:`findfoci_plugins:Translocation Finder (Tool)` to the toolbar.
