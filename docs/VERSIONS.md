# Mod Versioning Policy

This document defines how versions are assigned to MMD mods. 
It separates the version of the mod from the Minecraft version that the mod supports.

## Version format

Mod versions use three numbers:

```text
Major.Minor.Bug
```

For example, OreSpawn `4.0.6` means major version 4, minor version 0, and bug revision 6.

The final artifact or release may also identify its Minecraft version, such as `OreSpawn-26.2-4.0.6`. The Minecraft version is a compatibility target; it is not part of the mod's `Major.Minor.Bug` progression.

When the Major or Minor component increases, the components to its right reset to zero. For example:

```text
4.0.6 -> 4.1.0
4.1.3 -> 5.0.0
```

## Major version

Increase the **Major** number for a large-scale change, paradigm shift, or breaking change that moves the mod forward in a fundamental way.

Examples include:
- Mineralogy 6 no longer containing its own world generation engine, unlike Mineralogy 5.
- OreSpawn 4 gaining a complete terrain generation engine, including strata, unlike OreSpawn 3.

Compatibility adaptations required to support another Minecraft or loader version do not by themselves require a major version increase when the mod's supported behaviour and public contracts remain equivalent.

## Minor version

Increase the **Minor** number for a new feature or a significant change to existing behaviour that does not justify a new major generation.

Examples include:
- adding a new player-usable block or other substantial feature;
- substantially overhauling a world-generation engine;
- making a significant fix or adjustment that materially changes how a major part of the mod behaves.

## Bug version

Increase the **Bug** number for a bug fix or a very small feature that does not materially change the mod's design.

Examples include:
- correcting a generation defect;
- fixing a user interface or compatibility problem;
- adding or correcting a language file translation;
- making a small documentation or configuration improvement that warrants a
  release.

This component is sometimes called the patch number in other semantic version systems. MMD uses the name **Bug** to make its intended purpose explicit.

## Ports to new Minecraft versions

Porting a mod to a new Minecraft version does not automatically change the mod version. If the new branch is functionally equivalent to the source branch, both releases use the same mod version.

For example:

```text
Minecraft 26.1.2 / OreSpawn 4.0.6
Minecraft 26.2   / OreSpawn 4.0.6
```

Target MC/Framework specific implementation details may differ internally where Minecraft or its mod loader requires them. Those adaptations do not require a different mod version when users and integrations receive the same supported behaviour.

If a port also introduces a feature or fix that changes the functional release, the version must be assessed using the Major, Minor, and Bug rules above.

## Branch-specific fixes and skipped numbers

Version numbers are allocated across the mod as a whole and must not be reused for unrelated functional change sets on different Minecraft branches. The same number may be shared by functionally equivalent ports, as described above.

If a released branch receives a bug fix that other branches do not require, only the affected branch is incremented. For example, that branch may move from `4.0.6` to `4.0.7` while unaffected branches remain on `4.0.6`.

If a different branch later receives a separate fix, it uses the next unused version, such as `4.0.8`, even if the `4.0.7` fix was not applicable to it. A branch may therefore legitimately skip version numbers.

This provides three useful guarantees:
1. A version number is not used to describe two different functional change sets.
2. A higher version identifies a later change in the mod's release history.
3. It is immediately visible that one branch may contain work not present in an older-numbered branch.

A higher version on another Minecraft branch does **not** necessarily mean it contains every lower numbered branch specific fix. Some fixes are relevant only to a particular Minecraft or loader implementation.

## Release and pull-request documentation

Because maintained branches can legitimately contain different fixes, the version number alone is not a substitute for release notes.

Every release and pull request should state:

- the Minecraft version and loader it targets;
- the mod version before and after the change;
- the features and fixes actually included;
- any fixes from nearby versions that are not applicable to that branch;
- whether the change is functionally equivalent to another maintained branch;
- any migration, compatibility, or configuration considerations for users.

## Decision summary

When assigning a version, ask the following questions in order:

1. Is this a fundamental or breaking new generation of the mod? Increase **Major**.
2. Is this a substantial feature or significant behavioural overhaul? Increase **Minor**.
3. Is this a bug fix or very small feature? Increase **Bug**, using the next unused number across the mod.
4. Is this only a functionally equivalent Minecraft or loader port? Keep the existing mod version.

The objective is to make versions useful to players, pack developers, mod integrators, and release automation while allowing each maintained Minecraft branch to receive only the changes it actually needs.
