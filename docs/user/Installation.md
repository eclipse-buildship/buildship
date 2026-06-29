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

### Update site with the latest release

There are separate update sites containing the latest Buildship versions:

- latest relase: https://download.eclipse.org/buildship/updates/latest/
- latest snapshot: https://download.eclipse.org/buildship/updates/latest-snapshot/

This update site is built against the Eclipse version that was current at the time of the release.

### Update sites for Buildship 3.x

Eclipse Version | Type      | Update Site
--------------- | ----------| ------------
2025-06         | snapshot  | `https://download.eclipse.org/buildship/updates/e436/snapshots/3.x`
2025-06         | release   | `https://download.eclipse.org/buildship/updates/e436/releases/3.x`
2025-03         | snapshot  | `https://download.eclipse.org/buildship/updates/e435/snapshots/3.x`
2025-03         | release   | `https://download.eclipse.org/buildship/updates/e435/releases/3.x`
2024-12         | snapshot  | `https://download.eclipse.org/buildship/updates/e434/snapshots/3.x`
2024-12         | release   | `https://download.eclipse.org/buildship/updates/e434/releases/3.x`
2024-09         | snapshot  | `https://download.eclipse.org/buildship/updates/e433/snapshots/3.x`
2024-09         | release   | `https://download.eclipse.org/buildship/updates/e433/releases/3.x`
2024-06         | snapshot  | `https://download.eclipse.org/buildship/updates/e432/snapshots/3.x`
2024-06         | release   | `https://download.eclipse.org/buildship/updates/e432/releases/3.x`
2024-03         | snapshot  | `https://download.eclipse.org/buildship/updates/e431/snapshots/3.x`
2024-03         | release   | `https://download.eclipse.org/buildship/updates/e431/releases/3.x`
2023-12         | snapshot  | `https://download.eclipse.org/buildship/updates/e430/snapshots/3.x`
2023-12         | release   | `https://download.eclipse.org/buildship/updates/e430/releases/3.x`
2023-09         | snapshot  | `https://download.eclipse.org/buildship/updates/e429/snapshots/3.x`
2023-09         | release   | `https://download.eclipse.org/buildship/updates/e429/releases/3.x`
2023-06         | snapshot  | `https://download.eclipse.org/buildship/updates/e428/snapshots/3.x`
2023-06         | release   | `https://download.eclipse.org/buildship/updates/e428/releases/3.x`
2023-03         | snapshot  | `https://download.eclipse.org/buildship/updates/e427/snapshots/3.x`
2023-03         | release   | `https://download.eclipse.org/buildship/updates/e427/releases/3.x`
2022-12         | snapshot  | `https://download.eclipse.org/buildship/updates/e426/snapshots/3.x`
2022-12         | release   | `https://download.eclipse.org/buildship/updates/e426/releases/3.x`
2022-09         | snapshot  | `https://download.eclipse.org/buildship/updates/e425/snapshots/3.x`
2022-09         | release   | `https://download.eclipse.org/buildship/updates/e425/releases/3.x`
2022-06         | snapshot  | `https://download.eclipse.org/buildship/updates/e424/snapshots/3.x`
2022-06         | release   | `https://download.eclipse.org/buildship/updates/e424/releases/3.x`
2022-03         | snapshot  | `https://download.eclipse.org/buildship/updates/e423/snapshots/3.x`
2022-03         | release   | `https://download.eclipse.org/buildship/updates/e423/releases/3.x`
2021-12         | snapshot  | `https://download.eclipse.org/buildship/updates/e422/snapshots/3.x`
2021-12         | release   | `https://download.eclipse.org/buildship/updates/e422/releases/3.x`
2021-09         | snapshot  | `https://download.eclipse.org/buildship/updates/e421/snapshots/3.x`
2021-09         | release   | `https://download.eclipse.org/buildship/updates/e421/releases/3.x`
2021-06         | snapshot  | `https://download.eclipse.org/buildship/updates/e420/snapshots/3.x`
2021-06         | release   | `https://download.eclipse.org/buildship/updates/e420/releases/3.x`
2021-03         | snapshot  | `https://download.eclipse.org/buildship/updates/e419/snapshots/3.x`
2021-03         | release   | `https://download.eclipse.org/buildship/updates/e419/releases/3.x`
2020-12         | snapshot  | `https://download.eclipse.org/buildship/updates/e418/snapshots/3.x`
2020-12         | release   | `https://download.eclipse.org/buildship/updates/e418/releases/3.x`
2020-09         | snapshot  | `https://download.eclipse.org/buildship/updates/e417/snapshots/3.x`
2020-09         | release   | `https://download.eclipse.org/buildship/updates/e417/releases/3.x`
2020-06         | snapshot  | `https://download.eclipse.org/buildship/updates/e416/snapshots/3.x`
2020-06         | release   | `https://download.eclipse.org/buildship/updates/e416/releases/3.x`
2020-03         | snapshot  | `https://download.eclipse.org/buildship/updates/e415/snapshots/3.x`
2020-03         | milestone | `https://download.eclipse.org/buildship/updates/e415/milestones/3.x`
2020-03         | release   | `https://download.eclipse.org/buildship/updates/e415/releases/3.x`
2019-12         | snapshot  | `https://download.eclipse.org/buildship/updates/e414/snapshots/3.x`
2019-12         | milestone | `https://download.eclipse.org/buildship/updates/e414/milestones/3.x`
2019-12         | release   | `https://download.eclipse.org/buildship/updates/e414/releases/3.x`
2019-09         | snapshot  | `https://download.eclipse.org/buildship/updates/e413/snapshots/3.x`
2019-09         | milestone | `https://download.eclipse.org/buildship/updates/e413/milestones/3.x`
2019-09         | release   | `https://download.eclipse.org/buildship/updates/e413/releases/3.x`
2019-06         | snapshot  | `https://download.eclipse.org/buildship/updates/e412/snapshots/3.x`
2019-06         | milestone | `https://download.eclipse.org/buildship/updates/e412/milestones/3.x`
2019-06         | release   | `https://download.eclipse.org/buildship/updates/e412/releases/3.x`
2019-03         | snapshot  | `https://download.eclipse.org/buildship/updates/e411/snapshots/3.x`
2019-03         | milestone | `https://download.eclipse.org/buildship/updates/e411/milestones/3.x`
2019-03         | release   | `https://download.eclipse.org/buildship/updates/e411/releases/3.x`
2018-12         | snapshot  | `https://download.eclipse.org/buildship/updates/e410/snapshots/3.x`
2018-12         | milestone | `https://download.eclipse.org/buildship/updates/e410/milestones/3.x`
2018-12         | release   | `https://download.eclipse.org/buildship/updates/e410/releases/3.x`
2018-09         | snapshot  | `https://download.eclipse.org/buildship/updates/e49/snapshots/3.x`
2018-09         | milestone | `https://download.eclipse.org/buildship/updates/e49/milestones/3.x`
2018-09         | release   | `https://download.eclipse.org/buildship/updates/e49/releases/3.x`
Photon (4.8)    | snapshot  | `https://download.eclipse.org/buildship/updates/e48/snapshots/3.x`
Photon (4.8)    | milestone | `https://download.eclipse.org/buildship/updates/e48/milestones/3.x`
Photon (4.8)    | release   | `https://download.eclipse.org/buildship/updates/e48/releases/3.x`

### Update sites for Buildship 2.x

Eclipse Version | Type      | Update Site
--------------- | ----------| ------------
Photon (4.8)    | release   | `https://download.eclipse.org/buildship/updates/e48/releases/2.x`
Photon (4.8)    | milestone | `https://download.eclipse.org/buildship/updates/e48/milestones/2.x`
Photon (4.8)    | snapshot  | `https://download.eclipse.org/buildship/updates/e48/snapshots/2.x`
Oxygen (4.7)    | release   | `https://download.eclipse.org/buildship/updates/e47/releases/2.x`
Oxygen (4.7)    | milestone | `https://download.eclipse.org/buildship/updates/e47/milestones/2.x`
Oxygen (4.7)    | snapshot  | `https://download.eclipse.org/buildship/updates/e47/snapshots/2.x`
Neon (4.6)      | release   | `https://download.eclipse.org/buildship/updates/e46/releases/2.x`
Neon (4.6)      | milestone | `https://download.eclipse.org/buildship/updates/e46/milestones/2.x`
Neon (4.6)      | snapshot  | `https://download.eclipse.org/buildship/updates/e46/snapshots/2.x`
Mars (4.5)      | release   | `https://download.eclipse.org/buildship/updates/e45/releases/2.x`
Mars (4.5)      | milestone | `https://download.eclipse.org/buildship/updates/e45/milestones/2.x`
Mars (4.5)      | snapshot  | `https://download.eclipse.org/buildship/updates/e45/snapshots/2.x`
Luna (4.4)      | release   | `https://download.eclipse.org/buildship/updates/e44/releases/2.x`
Luna (4.4)      | milestone | `https://download.eclipse.org/buildship/updates/e44/milestones/2.x`
Luna (4.4)      | snapshot  | `https://download.eclipse.org/buildship/updates/e44/snapshots/2.x`
Kepler (4.3)    | release   | `https://download.eclipse.org/buildship/updates/e43/releases/2.x`
Kepler (4.3)    | milestone | `https://download.eclipse.org/buildship/updates/e43/milestones/2.x`
Kepler (4.3)    | snapshot  | `https://download.eclipse.org/buildship/updates/e43/snapshots/2.x`
Juno (4.2)      | release   | `https://download.eclipse.org/buildship/updates/e42/releases/2.x`
Juno (4.2)      | milestone | `https://download.eclipse.org/buildship/updates/e42/milestones/2.x`
Juno (4.2)      | snapshot  | `https://download.eclipse.org/buildship/updates/e42/snapshots/2.x`
                
#### Update sites for Buildship 1.x

Eclipse Version | Update Site
--------------  |------------
Neon (4.6)      | `https://download.eclipse.org/buildship/updates/e46/releases/1.0`
Mars (4.5)      | `https://download.eclipse.org/buildship/updates/e45/releases/1.0`
Luna (4.4)      | `https://download.eclipse.org/buildship/updates/e44/releases/1.0`
Kepler (4.3)    | `https://download.eclipse.org/buildship/updates/e43/releases/1.0`
Juno (4.2)      | `https://download.eclipse.org/buildship/updates/e42/releases/1.0`
Indigo (3.7)    | `https://download.eclipse.org/buildship/updates/e37/releases/1.0`
Helios (3.6)    | `https://download.eclipse.org/buildship/updates/e36/releases/1.0`


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

## Updating from update site

If you have already installed Buildship, you can update to the most recent version by opening the menu item _Help >> Check for Updates_. Note, that the update works only if Buildship was installed from the updates sites from download.eclipse.org or from builds.gradle.org, as listed above. If Buildship comes preinstalled in your Eclipse (for instance if you use the standard [Eclipse for Java developers](https://www.eclipse.org/downloads/packages/eclipse-ide-java-developers/neon) package) then you have to do the update manually. To do that just follow the steps from the previous section.
