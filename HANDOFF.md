# Swing Commands handoff — 2026-10-11

## Current status

Source: C:\dev\swing-commands. Working Java 17/Swing library for configurable commands, menus, and toolbars, extracted from ASPEN concepts. No JIDE/ASPEN runtime dependencies. WASP is integrated against the local Maven artifact and has approved the behavior and editor/icon preview in conversation. Keep public integration simple; hide parser/validator internals. The library is now named SwingCommands. Public API package: dev.codecain.swing.commands.command; support packages remain under dev.codecain.swing.commands. Gradle coordinates: dev.codecain:swing-commands:0.2.0-SNAPSHOT. The groupId is provisional until codecain.dev is registered and its namespace is verified in Central. Gradle local-Maven publishing is configured; Maven Central publishing is not.

WASP handoff: C:\dev\waveformlab\HANDOFF.md. Original reference is C:\dev\aspen and earlier extraction C:\dev\system-actions. Do not replace either or undertake unrelated cleanup without a request.

## Build and deployment

- gradlew.bat build: ordinary JAR, sources, shaded -all JAR, distributions and checks.
- gradlew.bat shadowJar: build/libs/swing-commands-0.2.0-SNAPSHOT-all.jar.
- gradlew.bat publishToMavenLocal: installs dev.codecain:swing-commands:0.2.0-SNAPSHOT into the user's local Maven repository.
- WASP has been updated to use dev.codecain:swing-commands:0.2.0-SNAPSHOT from Maven Local; the user reports that it builds and runs. For manual single-file deployment, use build/libs/swing-commands-0.2.0-SNAPSHOT-all.jar.
- Shadow relocates Jackson, NetworkNT, SLF4J and other dependencies under dev.codecain.swing.commands.internal.shaded, preserving public API packages and merging services. Normal JAR retained for Maven publication with dependency metadata.
- Java source encoding explicitly UTF-8; a Windows rendering test caught and fixed garbled UI punctuation.

## SwingCommands migration for WASP

Earlier WASP integration was verified against the locally published Maven dependency. The default configuration now uses the JSON keys commands, command, and defaultCommand; the updated source resource was added and the latest API snapshot was republished to Maven Local. The user is rebuilding and testing WASP against this update. Keep WASP class registration names pointing to WASP-owned command implementations. The application schema is swing-command-schema.json; the preferences schema is command-preferences-schema.json.

## Configuration semantics

- ConfigurationManager.install reads schema-backed JSON into ResourceModels (DefaultTreeModel); directly usable with JTree. Application, preferences, and shared layout schemas are bundled under src/main/resources; the two entry-point schemas reference command-layout-schema.json for their shared menu and toolbar rules. Runtime resolves these references locally and does not fetch document $schema URLs.
- Defaults require version 1 and the JSON commands object. The optional $schema field is omitted by WASP. Stable command IDs map to class registrations, optional category/presentation. Class names of nested classes use $, ordinary classes use package.ClassName.
- Menus use label/items; nested menu objects are submenus. Optional container IDs unique per layout. Toolbars support separators, toggleGroup, dropdown/defaultCommand. Repeated exclusive groups must have identical membership; conflicts rejected.
- selected is a JSON boolean for BooleanCommand (including BooleanCallbackCommand). enabled is a JSON boolean for every command type. Omission inherits captured Java initialization defaults. Defaults applied AFTER initialize(), BEFORE presenters. No automatic selectionPerformed call. User accepts this order and only wanted configuration rather than hardcoded setters.
- Performers and enabled state are independent: registration/removal never changes enabled. Default callbacks enabled like ordinary Swing actions unless Java/config says otherwise. Enabled callback without performer safely has no callback behavior; boolean callback can still toggle. Disabled Swing action events do not execute.
- selected/enabled are runtime state, excluded from preference save/reset. Preference overrides store changed presentation fields and whole edited menu/toolbar sections; untouched sections inherit future defaults. Unknown IDs/bad preferences reject entire file, preserve it, use defaults and expose loadWarning.
- Single active configuration per shared command family; Command instances remain per-class singletons. Close configurations to release listeners. Host owns callback context/background execution.

## Editors and icons

- Standalone entry point dev.codecain.swing.commands.editor.ConfigurationEditor, Gradle runEditor, optional -PconfigFile=absolute/path.json. Fat JAR launch: java -cp path/to/all.jar dev.codecain.swing.commands.editor.ConfigurationEditor [configuration.json].
- Developer editor manages registration/class names, IDs, category and defaults, layouts, selection/enabled defaults, properties, move/remove, non-executing layout preview, validation and Save/Save As. Unsaved-change prompts and backups. Automatically writes version:1; repairs missing version as an unsaved change. Host classes never loaded, so runtime must validate class/type/inherited shortcut/icon compatibility.
- End-user ConfigurationCustomizerDialog uses actual registered commands, saves preferences, and applies edits live. Close retains unsaved session changes. Non-executing preview; layout controls fit in rows.
- Latest feature: dev.codecain.swing.commands.editor.IconPreviewer shared by both editors. Live thumbnail beside text field, 200ms debounce, SwingWorker loading, revision/cancellation to avoid stale results, <=28px display fitting, dimensions/error tooltips, cleanup on removal.
- EditorIconLoader resolves available classpath resources, local files and config-resource-directory/package-relative paths without instantiating host classes. In-app uses Command.loadIcon anchored to selected class. Relative-file preview fallback beside config is convenience only; runtime relative local paths remain relative to working directory.
- Runtime icons support classpath resources or local files; null removes configured default. Static images only. Theme-adaptive icons were discussed but not implemented. Standalone layout preview remains approximate; live icon-field preview is implemented and user-approved.

## Verification

Latest build passed 149 existing checks (71 JSON, 25 enabled, 29 integration, 24 editor), and IconPreviewTest's resource/local lookup, live updates, invalid-path and clear behavior. Headless Swing screenshots under build/reports/editor-*.png were inspected during editor work. User reports editor and live icon preview look good. New tests live in src/test/java/dev/codecain/swing/commands and editor subpackage.

WASP startup was previously reproduced failing on missing NetworkNT from a thin JAR; fixed, successfully run with OpenGL initialization and 3 passing WASP tests, then user switched to the preferred fat-JAR workflow. Latest built JAR was offered for manual copying; do not assume WASP always has the latest binary.

## Potential next work (not automatically authorized)

Maven Central publishing and release versioning; theme-aware icons; further editor UX refinement; release/migration policy; callback context/undo requirements. Do not implement these merely because listed. Check current files before edits: user actively changes and reformats code between turns. Latest request was to preserve handoff notes, not start new functionality.

## Maven Central publishing plan — staged first release

The user wants the first publication staged in the Central Portal and tested before any public release. Keep publishing in `USER_MANAGED` mode: upload the release bundle, wait for validation, test the validated deployment from a local Gradle build through Central's authenticated manual-testing endpoint, then either publish manually or drop the deployment. A validated staging deployment is for private/manual testing, not a general private Maven repository. Published Central releases are immutable; corrections require a new version.

Before the first public release, complete these steps:

- Verify ownership of the `dev.codecain` namespace in Central after registering the domain.
- Choose the first non-`SNAPSHOT` release version.
- Decide the distribution license and include its license file in the project.
- Set the project URL and source repository URL, then add the required POM metadata: name, description, license, developer, and SCM details.
- Keep the existing sources JAR and add a Javadoc JAR; verify Javadoc generation succeeds.
- Configure GPG/PGP signing and ensure the publishing route supplies required checksums.
- Configure Central Portal publishing and keep tokens and signing credentials outside source control.
- Upload in `USER_MANAGED` mode, validate the deployment, and test resolving it from a local Gradle build. Publish manually only when ready; otherwise drop the deployment.

Current coordinates are `dev.codecain:swing-commands:0.2.0-SNAPSHOT`. Central publishing is not configured. Publish the normal library JAR with dependency metadata; keep the shaded `-all.jar` for manual single-file deployment.

Central currently has no official Gradle publishing plugin. Choose a community Gradle route such as JReleaser, or assemble/upload a Central bundle through the Portal API. Keep Portal tokens and signing credentials out of source control. Do not trigger public publication until the user is ready; the final Portal Publish action is the irreversible public release step.

References:
- Namespace setup: https://central.sonatype.org/register/namespace/
- Publishing requirements: https://central.sonatype.org/publish/requirements/
- Gradle publishing options: https://central.sonatype.org/publish/publish-portal-gradle/
- Staged deployment, manual testing, publish/drop API: https://central.sonatype.org/publish/publish-portal-api/
