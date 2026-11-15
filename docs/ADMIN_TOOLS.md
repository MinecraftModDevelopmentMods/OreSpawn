# Admin and distribution tools

Use these tools in a disposable Creative world while tuning a pack. Surveys
do not generate terrain or enable OreSpawn's vanilla-ore management. Clearing
is different: it changes saved blocks, including matching player-placed rocks.

## Measure what actually generated

```
/orespawn help
/orespawn status verbose
/orespawn chunk
/orespawn chunk -2 3
/orespawn survey 1
/orespawn survey 2 --y 0 48 --block minecraft:redstone_ore
/orespawn survey 0 --center -2 3 --block minecraft:diorite
/orespawn explain minecraft:redstone_ore 100 20 -40
/orespawn retrogen status
/orespawn jobs
/orespawn jobs <job-id>
/orespawn cancel <job-id>
```

New diagnostic commands require permission level 2. Ordinary `status` remains
available without operator permission. Existing `reload`, `retrogen [radius]`
and `dump-biomes` commands keep their behaviour. `retrogen status` never queues
work, enables retrogen or writes a generation marker. Its completed flag is
session bookkeeping, not a claim about a chunk's complete history.

Chunk coordinates are block coordinates divided by 16, rounding down, including
negative coordinates. Without coordinates, commands use the player's chunk;
the console uses Overworld spawn. Shared options are `--center <chunkX chunkZ>`,
`--dimension <loaded-numeric-or-namespaced-ID>`, `--y <min max>` and the survey
filter `--block <namespace:block[property=value]>`. Heights are inclusive. Radius
defaults to 1 and is limited to 8: a square of `(2 * radius + 1)` chunks per side.

Only chunks already loaded are visited. The tool records skipped chunks and
chunks that unload during a scan. A partial report is not a complete regional
sample. Scans happen over a recorded interval, not at one atomic world instant.
For a fair comparison, use the same seed, dimensions, coordinates, height range
and coverage, and avoid mining or construction while collecting the sample.

Reports are stored in the world's `orespawn/diagnostics/reports/<job-id>/`.
Long chat summaries end in `...`; the saved reports are not shortened.
Check `jobs <job-id>` for export completion or I/O failure. JSON records block
states and categories, exact Y levels, biome columns, per-chunk counts, coverage
and the active profile fingerprint. CSV files provide the same count tables.
Total sampled volume includes air; non-air volume is reported separately. A
block filter restricts both those denominators to the matching sample.

Known ores come from native identities, configured outputs and exact block-tag registrations. Blocks are counted once even if several providers
share them. All observed states are exported, including unclassified blocks.
Configured fluid outputs are listed separately from ores. Reports do not
identify which mod historically placed a block or declare that a distribution
is statistically correct from a small sample.

`explain` uses OreSpawn's actual baked placement budget, host tests and
chunk-centre biome checks. Frequency means expected attempts per chunk, not
guaranteed blocks. Weighted host tests, random geometry, exposure rolls and
private extension checks still affect results. A rock under the cursor today
is not necessarily the host that existed during original generation. Chunk
geology information is labelled as a current base-geology prediction, not
saved formation provenance.
`chunk` also records observed block states down the centre column, every 16
blocks and at the selected range's endpoints. These sparse samples are not a
whole-chunk count; use `survey 0` for that.

## Expose one chunk, with a way back

```
/orespawn clearchunk --center -2 3
/orespawn clearchunk confirm <preview-token>
/orespawn undo <backup-id>
/orespawn undo <backup-id> confirm <undo-preview-token>
/orespawn jobs backups
```

Clearing and undo require permission level 4. Player operators must be Creative
or Spectator; clearing is refused while a Survival/Adventure player occupies
the target chunk. Keep the surrounding lighting area loaded. The aliases
`/clearchunk` and `/clearchunks` are registered only if another mod has not
claimed them. Both mean one chunk, not a region.

The first command only scans and prints a preview; no blocks change yet.
Review the coordinates, height range and removal count, then open chat (T by
default) and click **Confirm clearing**. That click sends the confirmation
command. **Cancel** dismisses the preview without changing blocks; **Details**
opens the job report. Hover over a link to see its exact command. The commands
above also work when typed, and the server console prints a copyable command.

After clearing, the **Undo…** link starts a restoration preview, not an
immediate restore. Review it and click **Confirm undo** to restore the blocks.
Tokens expire after 120 seconds and belong to their operator and snapshot.
A changed profile or snapshot requires a new preview. There is no immediate, unconfirmed clear mode.

Clearing removes recognised native/provider host rocks and background fluids.
Known ore outputs, configured fluid deposits, bedrock, tile entities and
unrecognised blocks are protected. Protection wins if a state is both an ore
and a host. When a configured deposit uses ordinary water or lava, the same
fluid elsewhere is protected too: there is no reliable historical provenance.

Before the first write, the tool writes, flushes and verifies an immutable
checksummed journal under `orespawn/diagnostics/backups/<backup-id>/`.
If that fails, no clearing occurs. Backups are not automatically deleted.
They remain on disk after restarts; `jobs backups` gives their directory.
Journals record the Minecraft version and are not interchangeable with backups
from other Minecraft versions, whose height and block-state encodings differ.

Undo validates every recorded state and checks the current blocks before
restoring anything. Already-restored cells are harmless; other intervening
edits cause a conflict rather than being overwritten. Work can be cancelled
or interrupted, so always inspect its final job status. A partial edit remains
recoverable from its original journal and never resumes by itself after restart.
Cancellation stops block writes, then finishes bounded lighting cleanup. If
the chunk or its lighting area unloads, the report records the interruption
and the journal remains available for checked recovery instead of claiming completion.
Only unfinished height and lighting cleanup is retained with the chunk. It resumes
in bounded steps when its lighting area is loaded; block edits never resume by themselves.

Undo restores recorded block states, not entity movement, elapsed time, mining
or changes made elsewhere. Fluids from outside the selection can later flow
back in. It is not a substitute for a full world backup.

One server job runs at a time, yielding after 16,384 block visits or a 4 ms
cooperative budget. File output runs separately and touches no live world.
No command expands the loaded chunk set to finish a report or an edit.
Reports stop with an explicit error above 500,000 count-table rows; individual
sidecars are limited to 32 MiB. Reduce the area or height range if you hit a
limit. Existing backups are never removed to make room.
The file-worker backlog is capped at 32 tasks. A full queue reports failure;
it never runs file output on the server thread or starts an unbacked edit.
