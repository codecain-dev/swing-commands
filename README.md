# SwingCommands v2

A Java 17/Swing command API with schema-backed JSON configuration. Define application commands, describe the default layout, connect callbacks, and install the UI. Parsing, validation, persistence and presenter synchronization are internal. No ASPEN or JIDE dependencies.

## Quick start

```powershell
.\gradlew.bat test
.\gradlew.bat run
```

The demo shows shared callbacks, toggles, exclusive groups, dropdowns, menus, toolbars, and the actual ResourceModels in JTrees. Choose **Commands > Customize**. Changes are live; Save writes `demo.user.json` in the working directory, with a `.bak` of the previous file. Close retains session edits. Reset restores current application defaults and must be saved to persist.

`gradlew.bat installDist` builds a runnable distribution under `build/install/swing-commands`. Tests run headlessly on the Swing event thread; they do not automate the visible customizer.

## Application integration

1. Define Command subclasses for shared, configurable application commands. Use ordinary Swing Actions for local controls.
2. Put a default JSON configuration and its schema in your application's resources.
3. Load it on the EDT, connect callbacks, and install menus/toolbars/shortcuts.

```java
public class OpenCommand extends CallbackCommand {
    @Override protected void initialize() {
        setName("Open...");
        setToolTip("Open a document");
        setKeyBinding(KeyStroke.getKeyStroke("ctrl pressed O"));
    }
}
```

```json
{
  "$schema": "./swing-command-schema.json",
  "version": 1,
  "name": "My Application",
  "commands": {
    "file.open": {
      "class": "myapp.commands.OpenCommand",
      "category": "File"
    }
  },
  "menus": [
    {
      "id": "file",
      "label": "File",
      "mnemonic": "F",
      "items": [{ "command": "file.open" }]
    }
  ],
  "toolbars": [
    {
      "id": "main",
      "label": "Main",
      "items": [{ "command": "file.open" }]
    }
  ]
}
```

```java
Configuration config = new ConfigurationManager().install(
    MyApplication.class, "/default-configuration.json", userFile);
config.getCommand(OpenCommand.class).setCommandPerformer((command, event) -> openDocument());
frame.setJMenuBar(config.getMenuBar());
frame.add(config.getToolbarPanel(), BorderLayout.NORTH);
config.installKeyBindings(frame.getRootPane());
// Your Customize command:
new ConfigurationCustomizerDialog(frame, config).setVisible(true);
// On shutdown:
config.close();
```

`getLoadWarning()` reports rejected preferences so your application can show an appropriate message. Invalid application defaults throw IOException at installation.

Java defines behavior and fallback presentation. Optional JSON `label`, `tooltip`, `shortcut`, and `icon` fields override those defaults. Use `CallableCommand` for direct behavior, `CallbackCommand` for host-supplied behavior, and the boolean variants for selected state. Callback registration does not control enabled state. Like other Swing Action implementations, callbacks start enabled unless Java initialization or configuration says otherwise. Update commands on the EDT; presentations synchronize automatically.

## JSON contract

- `src/main/resources/swing-command-schema.json`: application defaults, JSON Schema Draft 2020-12.
- `src/main/resources/command-preferences-schema.json`: saved user overrides.
- `src/main/resources/command-layout-schema.json`: shared menu and toolbar definitions referenced by both schemas.
- `src/main/resources/demo.json`: complete runnable example.

Keep all three schema files beside your defaults to enable validation and completion in editors supporting JSON Schema. The runtime resolves the shared layout definitions from its bundled resources and never fetches the document's `$schema` URL. Public API signatures expose no parser or validator types.

Command IDs must start with a letter and contain letters, digits, periods, underscores or hyphens. Keep them stable across releases, even if Java classes move. Exactly one ID registers each command class. `category` groups commands in the customization tree. The same ID can appear in many menu and toolbar placements. Container IDs are optional and must be unique within each layout.

Menu items can be command references, `{ "separator": true }`, nested menus (`label` and `items`), or exclusive groups (`toggleGroup` and command-only `items`). Toolbar items can be command references, separators, exclusive groups or dropdowns (`dropdown`, command-only `items`, optional `defaultCommand`). Toolbar options are `showLabels` and `floatable`. See the demo for examples. Empty layouts and empty groups are valid while customizing.

Schema validation rejects unknown properties, wrong types, unsupported versions and incorrect item structures. Semantic validation checks command IDs, command classes, shortcut conflicts, exclusive groups and dropdown defaults. Errors include setting paths where available. Duplicate JSON keys, trailing documents and comments are rejected.

Shortcuts use Swing KeyStroke syntax, such as `ctrl pressed S`; null or an empty string clears one. Use Configuration.setKeyBinding for edits with conflict checking. Icons are classpath image resources or local image paths; null removes an icon. Programmatically generated icons remain host-owned and cannot be serialized as preference values.

## Preferences and application updates

Preferences contain only presentation differences and changed layouts, for example:

```json
{
  "$schema": "command-preferences-schema.json",
  "version": 1,
  "commands": {
    "file.open": { "label": "Open document...", "shortcut": null }
  }
}
```

Omitted properties inherit the current application defaults. Unchanged menus/toolbars are omitted and therefore receive future default updates. Editing a menu layout saves the complete menu array; editing a toolbar layout saves the complete toolbar array. Those customized sections remain fixed until reset or edited again. Newly registered commands still appear in the command catalog, where users can add them to their layouts. There is no automatic merge into customized layouts.

Unknown or removed command IDs, invalid settings, and shortcut conflicts reject the whole preferences document: defaults remain active, the original file is preserved, and getLoadWarning explains why. Stable IDs are therefore important; renaming/removing IDs requires an application migration or user reset. Version 1 is the only supported version. No XML/previous-JSON conversion is provided.

Preferences never register Java classes or change callbacks, enabled state or selected state. Saving uses a temporary file, backup and atomic replacement where supported. Runtime state is not reset as a preference. A reset followed by Save writes empty overrides and restores inheritance.

## Advanced use and boundaries

ResourceModel extends DefaultTreeModel: `new JTree(config.getMenuModel())` directly displays the model used by the menus. No adapter or parallel configuration DTO hierarchy is needed. Use model operations for edits instead of changing nodes directly. Ordinary hosts do not need to manipulate models.

Use one active configuration per shared command family. Close before replacing a configuration. Commands are shared per class for the JVM lifetime; callbacks and background execution belong to the host. Root-pane shortcuts preserve focused text-component key precedence. Exclusive groups permit no selection; repeated groups must contain identical command sets. Conflicting edits are rejected and rolled back.

The customizer uses explicit add/move/remove controls rather than drag-and-drop. ASPEN context services, docking, automatic background workers and undo infrastructure are outside this extraction. WASP consumes the locally published Maven artifact; its source remains in a separate project.

Implementation dependencies are Jackson (JSON parsing) and NetworkNT JSON Schema Validator, supplied transitively by Gradle and hidden from host code. Validator API reference: https://github.com/networknt/json-schema-validator/tree/1.5.6 . The original extraction and ASPEN source were not modified.

## Single-file deployment (fat JAR)

```powershell
.\gradlew.bat shadowJar
```

Output: `build/libs/swing-commands-0.2.0-SNAPSHOT-all.jar`.
Copy this file into your application's `lib` directory and use:

```groovy
dependencies {
    implementation files('lib/swing-commands-0.2.0-SNAPSHOT-all.jar')
}
```

Use this instead of the normal command API JAR or composite-build dependency, not alongside them. The fat JAR includes the schemas and runtime dependencies. Dependency packages are relocated under `dev.codecain.swing.commands.internal.shaded` so the host can use its own Jackson version. Public `dev.codecain.swing.commands.command`, `config`, `resource` and `customize` packages are unchanged. Java 17 is still required.

The normal JAR and sources JAR remain available for future repository publication. `gradlew.bat build` produces both normal and fat JARs and runs the standard unit and editor checks. You can also run the demo with `java -jar build/libs/swing-commands-0.2.0-SNAPSHOT-all.jar`.

Packaging uses the [Shadow Gradle plugin](https://gradleup.com/shadow/) for dependency bundling, service merging and relocation.

## Visual configuration editor

Run the standalone developer editor:

```powershell
.\gradlew.bat runEditor
# Open WASP's existing defaults (nothing is written until you save):
.\gradlew.bat runEditor -PconfigFile=C:/dev/waveformlab/src/main/resources/wasp_default_config.json
```

Or launch it from the fat JAR:

```powershell
java -cp build/libs/swing-commands-0.2.0-SNAPSHOT-all.jar dev.codecain.swing.commands.editor.ConfigurationEditor
```

Use **Registered Commands** to add/edit stable IDs, Java classes and optional presentation overrides. Blank overrides inherit the Java command defaults; explicit removal checkboxes clear a default shortcut or icon. **Menus** and **Toolbars** support adding commands, separators, groups and dropdowns; moving, renaming and removing items; and editing container properties. **Preview** shows a non-executing approximation of the layout. Commands without a label override display their ID because the standalone editor deliberately does not instantiate host classes or load icons.

Save/Save As writes an application-default JSON file, inserts `version: 1`, validates it, and preserves a `.bak` before replacement. No version/schema form fields or raw JSON editing are needed. Opening a file with a missing version offers the current format by marking the repair as unsaved; unsupported explicit versions are rejected. Save validation catches invalid references, structure, duplicate IDs/classes, explicit shortcut conflicts, invalid dropdown defaults and conflicting exclusive groups. Java class availability/type, inherited shortcut conflicts, and icon availability are checked later by the host runtime. The standalone editor can therefore work without WASP or its classes on the classpath.

Renaming a command ID updates its layout references. Deleting a registered command requires removing its placements first. Unsaved changes prompt on New, Open and Close; Save As confirms replacement. Use Save As to experiment with an existing application configuration.

The in-application `ConfigurationCustomizerDialog` remains the end-user editor: it only exposes registered commands and saves **user preferences**, not Java registrations or application defaults. It now includes a non-executing Preview and controls laid out in rows. Its Apply operations remain live; Save persists them and Close retains unsaved session changes.

`gradlew.bat build` runs editor document regression checks, headless Swing rendering and the existing runtime/fat-JAR checks. Headless renderings appear in `build/reports/editor-*.png`; interactive desktop review is still useful before adopting the editor's UX.

### Boolean command startup defaults

Application command declarations may include a JSON boolean (not a quoted string):

```json
"view.grid": {
  "class": "myapp.commands.ShowGridCommand",
  "selected": true
}
```

`selected` supports BooleanCommand and BooleanCallbackCommand. Omit it to inherit the command's Java initialization default. It is applied when installing application defaults, before menus/toolbars are created, without invoking the command's callback. The developer editor offers Inherit / Selected / Unselected in the command dialog. Standalone editing cannot check Java inheritance; the runtime rejects selected on non-boolean commands. Selection remains runtime state: preference saves and preference resets do not persist/reset it. For exclusive groups, configure at most one selected command; existing group behavior retains the first selected member if several are true.


### Enabled startup defaults

Every application command declaration can specify `"enabled": true` or `"enabled": false` (JSON booleans). Omission inherits the Java initialization default, normally true. The developer editor provides Inherit / Enabled / Disabled. Runtime loading applies this before presenters are created; later `setEnabled(...)` updates all bound Swing presenters.

Registering, replacing, clearing or removing a CommandPerformer never changes enabled state. An enabled callback without a performer has no callback behavior; an enabled boolean callback still toggles its selection normally. Disabled commands ignore Swing action events. As with selected, enabled is startup/runtime state, not a saved user preference or a preference-reset property.

### Live icon-field preview

Both command editors show a thumbnail beside the icon path as you type. Small icons retain their size; larger images fit within 28 pixels without changing the asset. Hover for image dimensions or a load-error explanation. Clearing/removing an icon clears the preview. Loading is debounced and runs off the Swing event thread.

The in-application editor resolves resources against the selected command class, as the runtime does. The standalone editor uses available classpath resources, local files, and the opened configuration's resources directory (or its containing directory when no resources ancestor exists). This allows `/icons/exit.png` to preview when WASP's config is opened from `src/main/resources`. Class-relative paths use the entered Java package without loading its class. Relative local paths can also be previewed beside the config; runtime local paths still resolve against the application's working directory. Unavailable host resources show an explanation rather than preventing editing. The layout Preview tab remains an approximation.
