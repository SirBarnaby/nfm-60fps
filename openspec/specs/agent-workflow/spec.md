# Agent workflow

## Purpose
How AI agents work on the game: hands-off from one prompt to finished results, while the owner's requirements can never be changed or quietly dropped without the owner.

## Requirements

### Requirement: Hands-off from prompt to result
Agents SHALL work hands-off: the owner gives a prompt, and the work runs to the end without stopping for questions, so results are ready in the morning. (Owner, 2026-11-21, R2 B18.)

#### Scenario: Check: an overnight run
- **WHEN** the owner gives a prompt and leaves
- **THEN** the work is finished, checked and committed when the owner returns, with no question left waiting

### Requirement: Only the owner changes the specs
Agents SHALL NOT change or remove a requirement in these specs, and SHALL NOT add features the owner did not ask for, without asking the owner first. Fixes and work the specs already cover go ahead on their own. (Owner, 2026-11-21, R2 B17.)

#### Scenario: Check: a change touches a requirement
- **WHEN** a change would alter or remove a requirement
- **THEN** the change proposal says so and waits for the owner, while the rest of the work goes on

### Requirement: An unmet requirement is reported, not hidden
When a requirement truly cannot be met (for example, a heavy physics rework that cannot reach the frame rate), the agent SHALL keep working and finish, but SHALL leave the requirement unchanged, leave its check failing, and put the gap at the top of its report with the numbers. It SHALL NOT call the gap a limit and move on, as happened with split screen drawn on the processor. (Owner, 2026-11-21, R2 B18.)

#### Scenario: Check: report with a gap
- **WHEN** a change finishes with a requirement unmet
- **THEN** the report opens with that requirement, its failing check and the measured numbers, and the spec text is unchanged

### Requirement: Agreed decisions go into the specs
Every new decision the owner makes about the game SHALL be written into these specs in the same work that acts on it, with the date, so it is never lost between threads and tools. Agents SHALL read the specs that cover a system before changing it. (Owner, 2026-11-21, R1 Q37, R2 B21.)

#### Scenario: Check: a decision in chat
- **WHEN** the owner decides something about the game in a conversation
- **THEN** the next commit about it adds or changes the matching requirement with "(Owner, date.)"

### Requirement: The specs live in the repo
The master copy of the specs SHALL be `openspec\specs` in the game's repo, which Claude Code and Codex read. A copy SHALL be kept in the project files for reading in the app. (Owner, 2026-11-21, R2 B21.)

#### Scenario: Check: copies agree
- **WHEN** a thread changes a spec in the repo
- **THEN** it refreshes the copy in the project files in the same session

### Requirement: Work goes to the staging branch
Commits SHALL go to the `staging` branch, one change per commit, with a message saying what changed and how it was checked. (Owner, 2026-10-15; kept 2026-11-21, R2 B19.)

#### Scenario: Check: branch
- **WHEN** an agent commits
- **THEN** the commit is on `staging`

### Requirement: Research before building
Before building something new, agents SHALL search for existing libraries and tools that already do it, and use or extend one when it fits. (Owner's project instructions; kept 2026-11-21, R2 B20.)

#### Scenario: Check: a new subsystem
- **WHEN** a change adds a new subsystem
- **THEN** its proposal lists what existing tools were found and why one was or was not used
