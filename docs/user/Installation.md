# Installation instructions

## Requirements

- Buildship 3.x
  - Minimum Java version: 1.8
  - Eclipse version: 4.3, or newer
- Buildship 2.x
  - Minimum Java version: 1.7
  - Eclipse version: 4.2, or newer
- Buildship 1.x
  - Minimum Java version: 1.6
  - Eclipse version: 4.2 - 4.6
 
Different Eclipse versions might be compatible but they aren't explicitly tested. 
Depending on the Gradle version that Buildship uses for a project import, certain features may not be available.

## Installing from eclipse.org update site

We propose you install Buildship from the [Eclipse Marketplace](http://marketplace.eclipse.org/content/buildship-gradle-integration).

Buildship is also available through one of the provided composite update sites listed on [eclipse.org](https://projects.eclipse.org/projects/tools.buildship/downloads).

For manual installation use one of the update sites below.


### Update sites for Buildship 3.x

Eclipse Version | Type      | Update Site
--------------- | ----------| ------------
2018-09         | snapshot  | `http://download.eclipse.org/buildship/updates/e49/snapshots/3.x`
Photon (4.8)    | snapshot  | `http://download.eclipse.org/buildship/updates/e48/snapshots/3.x`
Oxygen (4.7)    | snapshot  | `http://download.eclipse.org/buildship/updates/e47/snapshots/3.x`
Neon (4.6)      | snapshot  | `http://download.eclipse.org/buildship/updates/e46/snapshots/3.x`
Mars (4.5)      | snapshot  | `http://download.eclipse.org/buildship/updates/e45/snapshots/3.x`
Luna (4.4)      | snapshot  | `http://download.eclipse.org/buildship/updates/e44/snapshots/3.x`
Kepler (4.3)    | snapshot  | `http://download.eclipse.org/buildship/updates/e43/snapshots/3.x`
Juno (4.2)      | snapshot  | `http://download.eclipse.org/buildship/updates/e42/snapshots/3.x`

### Update sites for Buildship 2.x

Eclipse Version | Type      | Update Site
--------------- | ----------| ------------
Photon (4.8)    | release   | `http://download.eclipse.org/buildship/updates/e48/releases/2.x`
Photon (4.8)    | milestone | `http://download.eclipse.org/buildship/updates/e48/milestones/2.x`
Photon (4.8)    | snapshot  | `http://download.eclipse.org/buildship/updates/e48/snapshots/2.x`
Oxygen (4.7)    | release   | `http://download.eclipse.org/buildship/updates/e47/releases/2.x`
Oxygen (4.7)    | milestone | `http://download.eclipse.org/buildship/updates/e47/milestones/2.x`
Oxygen (4.7)    | snapshot  | `http://download.eclipse.org/buildship/updates/e47/snapshots/2.x`
Neon (4.6)      | release   | `http://download.eclipse.org/buildship/updates/e46/releases/2.x`
Neon (4.6)      | milestone | `http://download.eclipse.org/buildship/updates/e46/milestones/2.x`
Neon (4.6)      | snapshot  | `http://download.eclipse.org/buildship/updates/e46/snapshots/2.x`
Mars (4.5)      | release   | `http://download.eclipse.org/buildship/updates/e45/releases/2.x`
Mars (4.5)      | milestone | `http://download.eclipse.org/buildship/updates/e45/milestones/2.x`
Mars (4.5)      | snapshot  | `http://download.eclipse.org/buildship/updates/e45/snapshots/2.x`
Luna (4.4)      | release   | `http://download.eclipse.org/buildship/updates/e44/releases/2.x`
Luna (4.4)      | milestone | `http://download.eclipse.org/buildship/updates/e44/milestones/2.x` 
Luna (4.4)      | snapshot  | `http://download.eclipse.org/buildship/updates/e44/snapshots/2.x`
Kepler (4.3)    | release   | `http://download.eclipse.org/buildship/updates/e43/releases/2.x`
Kepler (4.3)    | milestone | `http://download.eclipse.org/buildship/updates/e43/milestones/2.x`
Kepler (4.3)    | snapshot  | `http://download.eclipse.org/buildship/updates/e43/snapshots/2.x`
Juno (4.2)      | release   | `http://download.eclipse.org/buildship/updates/e42/releases/2.x`
Juno (4.2)      | milestone | `http://download.eclipse.org/buildship/updates/e42/milestones/2.x`
Juno (4.2)      | snapshot  | `http://download.eclipse.org/buildship/updates/e42/snapshots/2.x`
                
#### Update sites for Buildship 1.x

Eclipse Version | Update Site
--------------  |------------
Neon (4.6)      | `http://download.eclipse.org/buildship/updates/e46/releases/1.0`
Mars (4.5)      | `http://download.eclipse.org/buildship/updates/e45/releases/1.0`
Luna (4.4)      | `http://download.eclipse.org/buildship/updates/e44/releases/1.0`
Kepler (4.3)    | `http://download.eclipse.org/buildship/updates/e43/releases/1.0`
Juno (4.2)      | `http://download.eclipse.org/buildship/updates/e42/releases/1.0`
Indigo (3.7)    | `http://download.eclipse.org/buildship/updates/e37/releases/1.0`
Helios (3.6)    | `http://download.eclipse.org/buildship/updates/e36/releases/1.0`


The continuous integration server generates nightly snapshot releases each day 23:00 CET which instantly become
available at the snapshot update sites above. In regular intervals, the Buildship team also creates new
milestone releases and makes them available at the milestone update sites.

Apply the following instructions to install the latest snapshot or milestone of Buildship into Eclipse.

 1. In Eclipse, open the menu item _Help >> Install New Software_.
 1. Paste the appropriate update site link into the _Work with_ text box.
 1. Click the _Add_ button at the top of the screen, give the update site a name, and press _OK_.
 1. Ensure that the option _Group Items by Category_ is enabled.
 1. Select the top-level node _Buildship: Eclipse Plug-ins for Gradle_ once it appears.
 1. Click _Next_. This may take a while.
 1. Review the list of software that will be installed. Click _Next_ again.
 1. Review and accept the licence agreement and click _Finish_.


## Installing from builds.gradle.org update site

We propose you install Buildship from eclipse.org. If, for any reason, you still want to install
from [builds.gradle.org](https://builds.gradle.org/project.html?projectId=Tooling_Buildship&tab=projectOverview), the following snapshot update sites
are available for all the supported Eclipse versions:
  
  * `https://builds.gradle.org/repository/download/Tooling_Buildship_Full_Test_Coverage_Linux_Eclipse48_Java8/.lastSuccessful/update-site` (latest 4.8 development snapshot)
  * `https://builds.gradle.org/repository/download/Tooling_Buildship_Full_Test_Coverage_Linux_Eclipse47_Java8/.lastSuccessful/update-site` (latest 4.7 development snapshot)
  * `https://builds.gradle.org/repository/download/Tooling_Buildship_Full_Test_Coverage_Linux_Eclipse46_Java8/.lastSuccessful/update-site` (latest 4.6 development snapshot)
  * `https://builds.gradle.org/repository/download/Tooling_Buildship_Full_Test_Coverage_Linux_Eclipse45_Java7/.lastSuccessful/update-site` (latest 4.5 development snapshot)
  * `https://builds.gradle.org/repository/download/Tooling_Buildship_Full_Test_Coverage_Linux_Eclipse44_Java7/.lastSuccessful/update-site` (latest 4.4 development snapshot)
  * `https://builds.gradle.org/repository/download/Tooling_Buildship_Full_Test_Coverage_Linux_Eclipse43_Java7/.lastSuccessful/update-site` (latest 4.3 development snapshot)
  * `https://builds.gradle.org/repository/download/Tooling_Buildship_Full_Test_Coverage_Linux_Eclipse42_Java7/.lastSuccessful/update-site` (latest 4.2 development snapshot)

Apply the following instructions to install the latest snapshot of Buildship into your version of Eclipse.

 1. In Eclipse, open the menu item _Help >> Install New Software_.
 1. Paste the update site link that matches your Eclipse version into the _Work with_ text box.
 1. Click the _Add_ button at the top of the screen, give the update site a name, and press _OK_.
 1. If prompted, set the following credentials: username=guest, password=guest.
 1. Ensure that the option _Group Items by Category_ is enabled.
 1. Select the top-level node _Buildship: Eclipse Plug-ins for Gradle_ once it appears.
 1. Click _Next_. This may take a while.
 1. Review the list of software that will be installed. Click _Next_ again.
 1. Review and accept the licence agreement and click _Finish_.


## Updating from update site

If you have already installed Buildship, you can update to the most recent version by opening the menu item _Help >> Check for Updates_. Note, that the update works only if Buildship was installed from the updates sites from download.eclipse.org or from builds.gradle.org, as listed above. If Buildship comes preinstalled in your Eclipse (for instance if you use the standard [Eclipse for Java developers](https://www.eclipse.org/downloads/packages/eclipse-ide-java-developers/neon) package) then you have to do the update manually. To do that just follow the steps from the previous section.
