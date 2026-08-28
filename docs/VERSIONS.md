# Mod Versioning Policy

This document defines how versions are assigned to MMD mods and how an exact
Minecraft and loader target is encoded in a release version.

## Version format

Mod versions use four numeric components:

```text
Major.Minor.Bug.Target
```

The first three components describe the functional release. For example,
OreSpawn `4.0.6` means major version 4, minor version 0, and bug revision 6.

The fourth component identifies the Minecraft and loader target. A complete
release version such as `4.0.6.120061` therefore identifies both the OreSpawn
4.0.6 feature set and its Minecraft 1.20.6 Forge build.

This is an expanded, Maven-compatible versioning convention. It is not strict
Semantic Versioning 2.0, which defines exactly three numeric core components.

When the Major or Minor component increases, the functional components to its
right reset to zero. The Target component is then appended for the build being
released. For example:

```text
4.0.6.120061 -> 4.1.0.120061
4.1.3.120061 -> 5.0.0.120061
```

## Target component

The Target component is deterministic and is not another feature or bug
sequence number.

To calculate it:

1. Normalize the Minecraft version to `major.minor.patch`, using zero when the
   patch component is omitted.
2. Concatenate the Minecraft major number without padding, the minor number as
   two digits, the patch number as two digits, and the one-digit loader code.
3. Use loader code `1` for Forge and `2` for NeoForge.

The component can be decoded from right to left: one loader digit, two patch
digits, two minor digits, and all remaining digits for the Minecraft major
version.

Examples:

| Minecraft | Loader | Target | Example full OreSpawn version |
| --- | --- | ---: | --- |
| 1.13.2 | Forge | `113021` | `4.0.6.113021` |
| 1.14.4 | Forge | `114041` | `4.0.8.114041` |
| 1.15.2 | Forge | `115021` | `4.0.9.115021` |
| 1.16.5 | Forge | `116051` | `4.0.9.116051` |
| 1.17.1 | Forge | `117011` | `4.0.9.117011` |
| 1.18.2 | Forge | `118021` | `4.0.10.118021` |
| 1.19.4 | Forge | `119041` | `4.0.10.119041` |
| 1.20.1 | Forge | `120011` | `4.0.10.120011` |
| 1.20.6 | Forge | `120061` | `4.0.6.120061` |
| 1.21.11 | Forge | `121111` | `4.0.6.121111` |
| 26.1.2 | Forge | `2601021` | `4.0.6.2601021` |
| 26.2 | Forge | `2602001` | `4.0.6.2602001` |
| 26.2 | NeoForge | `2602002` | `4.0.6.2602002` |

Historical MMD releases may also have four numeric components but may have used
the fourth component differently. This policy applies prospectively; it does
not reinterpret an old release number.

## Major version

Increase the **Major** number for a large-scale change, paradigm shift, or
breaking change that moves the mod forward in a fundamental way.

Examples include:

- Mineralogy 6 no longer containing its own world generation engine, unlike
  Mineralogy 5.
- OreSpawn 4 gaining a complete terrain generation engine, including strata,
  unlike OreSpawn 3.

Compatibility adaptations required to support another Minecraft or loader
version do not by themselves require a major version increase when the mod's
supported behaviour and public contracts remain equivalent.

## Minor version

Increase the **Minor** number for a new feature or a significant change to
existing behaviour that does not justify a new major generation.

Examples include:

- adding a new player-usable block or other substantial feature;
- substantially overhauling a world-generation engine;
- making a significant fix or adjustment that materially changes how a major
  part of the mod behaves.

## Bug version

Increase the **Bug** number for a bug fix or a very small feature that does not
materially change the mod's design.

Examples include:

- correcting a generation defect;
- fixing a user interface or compatibility problem;
- adding or correcting a language file translation;
- making a small documentation or configuration improvement that warrants a
  release.

This component is sometimes called the patch number in other versioning
systems. MMD uses the name **Bug** to make its intended purpose explicit.

## Ports to new Minecraft versions

Porting a mod to a new Minecraft version does not automatically change the
functional `Major.Minor.Bug` version. Functionally equivalent ports share those
first three components, while their complete versions have different Target
components.

For example:

```text
Minecraft 26.1.2 / Forge    / OreSpawn 4.0.6.2601021
Minecraft 26.2   / Forge    / OreSpawn 4.0.6.2602001
Minecraft 26.2   / NeoForge / OreSpawn 4.0.6.2602002
```

Target-specific implementation details may differ internally where Minecraft
or its mod loader requires them. Those adaptations do not change the functional
version when users and integrations receive the same supported behaviour.

If a port also introduces a feature or fix that changes the functional release,
the first three components must be assessed using the Major, Minor, and Bug
rules above. The Target component always identifies the build's actual
Minecraft and loader target.

## Branch-specific fixes and skipped numbers

Functional version numbers are allocated across the mod as a whole and must
not be reused for unrelated change sets on different Minecraft branches. The
same `Major.Minor.Bug` may be shared by functionally equivalent ports.

If a released branch receives a bug fix that other branches do not require,
only the affected branch's Bug number is incremented. For example, Forge
1.12.2 moved to `4.0.7.112021` for its packaged access-transformer repair while
unaffected branches remained on their target-qualified 4.0.6 versions.

If a different branch later receives a shared fix, it uses the next unused
Bug number, such as Forge 1.14.4's `4.0.8.114041`, even though the 4.0.7 repair
was not applicable there. Forge 1.15.2 through 1.20.1 then advanced to their
target-qualified 4.0.9 releases for the provider terrain-host ordering repair.
Forge 1.18.2 through 1.20.1 then advanced to their target-qualified 4.0.10
releases for the distinct Stable Layers actual-height eligibility repair. A
branch may therefore legitimately skip functional version numbers.

This provides three useful guarantees:

1. A functional version is not used to describe two unrelated change sets.
2. A higher functional version identifies a later change in the mod's release
   history.
3. The Target component identifies the exact Minecraft and loader build without
   overloading the functional version.

A higher functional version on another Minecraft branch does **not**
necessarily mean it contains every lower-numbered branch-specific fix. Some
fixes are relevant only to a particular Minecraft or loader implementation.

## Dependency ranges

Dependencies should normally express the compatible functional release range.
For example, Maven-style range `[4.0.6,5.0.0)` deliberately accepts all
target-qualified OreSpawn 4.0.6 builds while excluding OreSpawn 5.

Consumers must still declare their supported Minecraft version and loader in
their own metadata. The Target component makes that compatibility visible; it
does not replace loader-level compatibility checks.

## Release and pull-request documentation

Because maintained branches can legitimately contain different fixes, the
version number alone is not a substitute for release notes.

Every release and pull request should state:

- the Minecraft version and loader it targets;
- the complete four-component version and its functional `Major.Minor.Bug`;
- the features and fixes actually included;
- any fixes from nearby versions that are not applicable to that branch;
- whether the change is functionally equivalent to another maintained branch;
- any migration, compatibility, or configuration considerations for users.

## Decision summary

When assigning a version, ask the following questions in order:

1. Is this a fundamental or breaking new generation of the mod? Increase
   **Major**.
2. Is this a substantial feature or significant behavioural overhaul? Increase
   **Minor**.
3. Is this a bug fix or very small feature? Increase **Bug**, using the next
   unused number across the mod.
4. Is this only a functionally equivalent Minecraft or loader port? Keep the
   existing `Major.Minor.Bug`.
5. Calculate and append the Target component for the exact Minecraft and loader
   build.

The objective is to make versions useful to players, pack developers, mod
integrators, release automation, and support teams while allowing each
maintained Minecraft branch to receive only the changes it actually needs.
