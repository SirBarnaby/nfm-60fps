# Editing this repository from an agent on Windows

*25 Nov 2026, from building the extras (helmet cam, rebuilt engine sound, powerups, suspension handling) with Claude Code on the owner's PC, where the tools tripped in the same few ways again and again.*

## The lessons

Each of these cost a failed step at least once. They are about the tools, not the game.

- **Line endings are per file.** Some sources are CRLF (`RaceSnapshot`, `RaceState`, `RaceLog`, `RaceSession`, `Control`, `KeyBindings`, `SplitKeys`, `Gamepads`, `GameUi`, `RaceRandom`, `Sound3D`, `GameSparker`, `Plane`), others LF (`Madness`, `FeatureBridge`, most of `features\`). An exact-text replace written with LF fails to match a CRLF file, and a rewrite that changes a file's endings shows every line as changed. Edit by reading the file's endings, replacing in LF and writing the endings back (a small script taking `{path: [(old, new)]}` pairs does it). Check with `file <path>` first.
- **Quoted heredocs that contain Python may still fail in the Bash tool.** Apostrophes in the text (`game's`) can end the command early ("unexpected EOF while looking for matching `'`"). Write the script to a file in the scratchpad with the Write tool and run it.
- **In Python, a Windows path in a normal string is not the path.** `"docs\features"` holds a form feed (`\f`), and `"\e"` only warns. Write paths with `/`, or double the backslashes in text meant for the README (`test\\extras-check.ps1`).
- **`sed` writes a real newline for `\n` in its replacement.** Fixing a script's `"\n"` with `sed -i` breaks the script's string across two lines. Edit scripts with the Edit tool, or write `chr(10)` in Python.
- **PowerShell splits `-Dname.part=value`** passed to a native program at the dot. Quote it whole: `"-Dnfm.window=1720x720"`, or set `JAVA_TOOL_OPTIONS`.
- **`openspec` runs from PowerShell.** Run `openspec new change <name>` and `openspec archive <name> --yes` there, from the worktree's root. A change built for requirements already in the specs says so with `skip_specs: true` in its `.openspec.yaml`, so no spec files are written for it; the archive's "MODIFIED Delta should include requirements" warnings are then expected.
- **`git add` with one missing path stages nothing.** It stops at the first pathspec that does not match. List only paths that exist, or add folders.
- **A background command needs no `&`.** The Bash tool's background mode detaches it already; a `&` of its own only gets in the way of the tool following it.
- **Two hidden game runs at once collide.** They share the log and cache folders. Run hidden runs one after another.
- **A local that shadows a field is meant here, not a slip.** `Plane.d` declares `ox`, `oy`, `oz` over the fields so a bent corner set can replace them for one polygon without writing the model back. Copy the pattern for a new draw-time reshape (the suspension's body does) instead of writing to the fields.
- **Reading a dirty tree that belongs to someone else.** Use `git --no-optional-locks status` and `diff`, so that not even the index's stat cache is rewritten. Hash a dirty file (`git hash-object`) and search `git log --all --raw --no-abbrev` for it to learn whether its content exists in any commit.

## How to apply

Before editing a file, check its line endings. Before a multi-line script, write it to a file. Before quoting a path in Python, look for `\` followed by a letter.
