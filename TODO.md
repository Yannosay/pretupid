# TODO

Long-term improvements that are intentional and deferred. Each item has a clear
reason for being deferred and a concrete plan for when it is taken up.

## History rewrite for repository size

The repository history contains `target/` and build artifact paths from before
`.gitignore` was tightened. Every clone pays for this. A history rewrite with
`git filter-repo` would remove them.

Deferred because:

- Rewriting published history requires coordination with all clones.
- The current remote is functional; only the size is a problem.
- Timing should be after the current feature work stabilizes.

Plan when taken up:

1. Confirm no other contributor has an unmerged branch.
2. Run `git filter-repo --path target --path node_modules --invert-paths`.
3. Force-push all branches.
4. Notify any known clones to re-clone.

## Windows tray panel positioning on multi-monitor

The panel clamps to the monitor the tray icon is on. On mixed-DPI multi-monitor
setups, the scale factor is read from the panel's current monitor at show time,
which may differ from the tray icon's monitor.

Deferred because:

- Single-monitor behavior is correct.
- Mixed-DPI multi-monitor is rare in the current user base.

Plan when taken up:

- Pass the tray icon's monitor to `toggle_panel` and use its scale factor.

## Skin 3D preview failure on network-less boot

The 3D preview falls back to MHF_Steve when no skin is available. If the network
is unavailable AND no cached skin exists, the preview shows the default skin
indefinitely.

Deferred because:

- The fallback is acceptable behavior.
- A better solution would require caching the default skin on first run.

Plan when taken up:

- Bundle MHF_Steve as a local asset and use it as the fallback.