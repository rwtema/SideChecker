SideChecker
======

A coremod designed to check classes as they are loading and ensure that any code that calls a client-side code (i.e. code that is marked with @SideOnly(Side.CLIENT and therefore not present in a server enviroment) is also marked with @SideOnly(Side.CLIENT).


Note: This is meant to only be used in an deobfuscated enviroment.

Installation
==============
Download the latest compiled jar (<a href="http://www.mediafire.com/download/2yeqy4fsvualbpa/SideChecker-1.0.jar">here</a>) and add it to your run directory /mods/ folder.


Options
==============
Add the following VM options to configure the coreMods behaviour

-DSideChecker.filter=%package name%<br>
By default only classes in the 'production' directory are checked (i.e. classes than can be edited by the IDE) . If this filter is present then any class that starts with the package name will be checked.

-DSideChecker.clientSafeAnnotation=%classname%<br>
If you wish to not use @SideOnly on a class, then you can use the 'clientSafeAnnotation' to mark a method as 'safe'.

-DSideChecker.crashOnError=true<br>
When present, this will stop the execution whenever a serious error occurs.

-DSideChecker.crashOnWarning=true<br>
The same as above, except it will also stop execution when a warning is detected.
