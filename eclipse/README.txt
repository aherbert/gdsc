The GDSC-SMLM code was developed using the Eclipse IDE:
https://eclipse.org/

The code can be built using Maven. See the README.md for details. However using 
Eclipse is preferred for code development to provide a debugging environment.

You will need the Maven and Git Eclipse plugins. The standard Eclipse IDE for
Java developers has these.

Set-up the project
------------------

Import the project into Eclipse (File>Import)
Select: Maven > Existing Maven projects

This will import the project but may not link it to the source Git repository.
Right-click on the project name and select 'Team > Share'. If you share it back to 
the same location it will attach to the source Git repository.

Code formatting
---------------

The Eclipse code format rules (in this directory) can be loaded using:

Eclipse Preferences : Java > Code Style > Formatter
Eclipse Preferences : Java > Code Style > Clean Up

Click 'Import...' to load the provided rules.

Running the code
----------------

Build the project.

Create a symbolic link on the filesystem to set-up the folders that are expected by ImageJ.

Windows:

    GDSC>mklink /D plugins target\classes
    symbolic link created for plugins <<===>> target\classes
    
    GDSC>mklink /D macros target\classes\gdsc\macros
    symbolic link created for macros <<===>> target\classes\gdsc\macros

Linux:

    [GDSC] % ln -s target/classes plugins
    [GDSC] % ln -s target/classes/gdsc/macros

Create a new Run configuration.

Select ij.ImageJ as the main class.
Add -Dabout-install=true to the VM arguments.

Run the code.

The same target can be used to debug the code.
