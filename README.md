GDSC ImageJ Plugins
===================

The Genome Damage and Stability Centre (GDSC) plugins are a collection of
analysis programs for microscopy images including colocalisation analysis and
peak finding.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Build Status](https://github.com/aherbert/gdsc/actions/workflows/build.yml/badge.svg)](https://github.com/aherbert/gdsc/actions/workflows/build.yml)
[![Coverage Status](https://codecov.io/gh/aherbert/gdsc/branch/master/graph/badge.svg)](https://app.codecov.io/gh/aherbert/gdsc)


Find Foci
---------

The Find Foci plugins allow the identification of peak intensity regions within
2D and 3D images. The tools provide: the automated and semi-automated labelling
of peaks; comparison of marked points between images; and alignment of manually
marked points to peak maxima.

See the [FindFoci User Manual](FindFoci.odt) for full details.

Colocalisation Analysis
-----------------------

The GDSC Colocalisation plugins provide various tools to perform colocalisation
analysis. The tools provide: thresholding for N-dimensional images for signal
identification; correlation and overlap coefficient analysis; and
colocalisation significance testing.

See the [Colocalisation User Manual](Colocalisation.odt) for full details.

Utility Plugins
---------------

The GDSC plugins provide various utility tools for image analysis. The tools
include: thresholding and mask generation; difference of Gaussians for
contrast enhancement; stack synchronisation for simultaneous image viewing;
and image alignment.


Install
-------

The GDSC plugins are distributed using an ImageJ2/Fiji update site.

To install the plugins using Fiji (an ImageJ distribution) just follow the
instructions [How_to_follow_a_3rd_party_update_site](http://fiji.sc/How_to_follow_a_3rd_party_update_site)
and add the GDSC update site. All the plugins will appear under the
'Plugins > GDSC' menu.

Some plugins require on the following 3rd party updates sites:
ImageScience; TrackMate


Installation from source
------------------------

The source code is accessed using git and built using Maven.

The code depends on the gdsc-test, gdsc-ij-parent and gdsc-core artifacts so
you will have to install these to your local Maven repository before building:

1. Clone the required repositories

        git clone https://github.com/aherbert/gdsc-test.git
        git clone https://github.com/aherbert/gdsc-ij-parent.git
        git clone https://github.com/aherbert/gdsc-core.git
        git clone https://github.com/aherbert/gdsc.git

1. Build the code and install using Maven

        cd ../gdsc-test
        mvn install
        cd ../gdsc-ij-parent
        mvn install
        cd ../gdsc-core
        mvn install
        cd ../gdsc
        mvn package

   This will produce a gdsc_-[VERSION].jar file in the target directory. Dependencies
   can be copied into the target/dependencies directory using:

        mvn dependency:copy-dependencies

1. Installation into a Fiji/ImageJ2 install can be performed using the scijava
maven goal to populate the application:

        cd gdsc-smlm-ij
        mvn scijava:populate-app -Dscijava.app.directory=/usr/local/fiji
        cd ..

   where `/usr/local/fiji` is the root directory of the ImageJ install.

1. Manual installation must copy the gdsc-smlm-ij_* jar into the plugins
directory of ImageJ.

   Copy the dependencies into the plugins directory (or onto the Java
   classpath). Note that the Maven package routine puts all dependencies into
   the target/dependencies directory even if they are not required by the SMLM code
   (it does not check what functions are actually used by the code). The libraries
   you will need are:

        gdsc-core
        gdsc-core-ij
        beansbinding
        commons-math3
        commons-lang3
        commons-rng-client-api
        commons-rng-core
        commons-rng-simple
        commons-rng-sampling
        imagescience
        Image_5D
        fastutil-core

   This excludes the 3D_Viewer and TrackMate functionality. View the dependencies of
   these using:

        mvn dependency:tree

5. The plugins will now appear under the 'Plugins > GDSC' menu in ImageJ.


Running from source
-------------------

Maven can be used to run ImageJ using a profile defined in the gdsc-ij-parent POM:

        mvn -P run-imagej

This profile compiles all classes and then executes ImageJ with the appropriate Java classpath. A
default plugin can then be run from the ImageJ Plugins menu to load the plugins defined in the
project's ImageJ plugins.config file. 

Note: This file is normally detected by ImageJ when loading plugin jar files to identify the
available plugins. The default plugin has been written to duplicate this functionality by reading
the configuration and populating the ImageJ menu.


Modifying the source
--------------------

The gdsc-smlm code was developed using the [Eclipse IDE](https://eclipse.org/).

Details of how to open the source code with Eclipse can be found in the eclipse
folder.


Legal
-----

See [LICENSE](LICENSE.txt)


# About #

###### Owner(s) ######
Alex Herbert

###### Institution ######
[Genome Damage and Stability Centre, University of Sussex](http://www.sussex.ac.uk/gdsc/)

###### URL ######
[GDSC ImageJ plugins](http://www.sussex.ac.uk/gdsc/intranet/microscopy/UserSupport/AnalysisProtocol/imagej/gdsc_plugins/)
