# Upstream, fork, and license notes

The current layer source is embedded under `mk8-vr/src-fork`. Its MIT license and third-party notices are present. This exported copy has no `.git` metadata, so its original commit history and remote URL cannot be verified from the files alone.

The source identifies itself as a Super Mario 3D World VR integration in some inherited project metadata and documentation. MK8-specific code and packaging in this repository are separate modifications. Do not use the inherited SM3DW install instructions as instructions for this MK8 project.

For a first public release, a single repository is simplest:

- keep the modified layer source in-tree so one checkout contains the matching layer and MK8 pack;
- retain the upstream MIT license and all third-party notices;
- identify the source project and any reused code in the README;
- publish the prebuilt Windows install ZIP as a GitHub Release asset.

A GitHub fork is optional. Use one only if you can identify and verify the exact upstream repository and want to contribute changes back. A git submodule is a poor fit for a user-facing release unless the upstream accepts and maintains the MK8 changes. Do not create a fork based only on an inherited folder name.
