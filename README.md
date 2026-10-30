# Swing Commands

Swing Commands is a Java 17 library for defining application commands once and reusing them across Swing menus, toolbars, and keyboard shortcuts. It keeps command behavior in the host application while managing shared presentation state and configurable layouts.

A command can appear in several parts of the interface and still represent the same operation. Change its name, icon, enabled state, or shortcut once; each presenter reflects that state. Describe the application's default command catalog and layout in JSON, then let users customize menus, toolbars, and shortcuts without changing application code.

## Features

- Define commands as Swing `Action` subclasses.
- Reuse each command in menus, popup menus, toolbars, buttons, and panels.
- Assign shortcuts and keep them synchronized with command state.
- Configure command catalogs and default layouts with validated JSON.
- Save a complete per-user command configuration separately from the factory configuration.
- Support callback commands, commands with built-in behavior, and shared boolean selection state.
- Include a standalone editor for application defaults and a runtime customizer for end users.

## Requirements

- Java 17 or newer
- Swing

## Installation

Add Swing Commands to your application from Maven Central.

### Gradle

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'dev.codecain:swing-commands:[version]'
}
```

### Maven

```xml
<dependency>
    <groupId>dev.codecain</groupId>
    <artifactId>swing-commands</artifactId>
    <version>[version]</version>
</dependency>
```

The regular library JAR is intended for dependency management. A sources JAR is also published. For a single-file deployment, see [Fat JAR](#fat-jar).

## Quick start

Create a command class. `CallbackCommand` lets the host application provide the behavior; `CallableCommand` is available when behavior belongs in the command class.

```java
import javax.swing.KeyStroke;

import dev.codecain.swing.commands.command.CallbackCommand;

public final class SaveCommand extends CallbackCommand {

    @Override
    protected void initialize() {
        setName("Save");
        setToolTip("Save the current document");
        setKeyBinding(KeyStroke.getKeyStroke("ctrl pressed S"));
    }
}
```

Register it in an application configuration file placed in your resources:

```json
{
  "$schema": "./swing-command-schema.json",
  "version": 1,
  "name": "My Application",
  "commands": {
    "file.save": {
      "class": "myapp.commands.SaveCommand",
      "category": "File"
    }
  },
  "menus": [
    {
      "label": "File",
      "items": [
        { "command": "file.save" }
      ]
    }
  ],
  "toolbars": [
    {
      "label": "Main",
      "items": [
        { "command": "file.save" }
      ]
    }
  ]
}
```

Inside your application window setup, load the configuration, connect the host behavior, and install its Swing components and shortcuts. The frame and `saveDocument()` method are supplied by your application.

```java
import java.awt.BorderLayout;
import java.nio.file.Path;
import javax.swing.JFrame;

import dev.codecain.swing.commands.config.Configuration;
import dev.codecain.swing.commands.config.ConfigurationManager;

Configuration configuration = ConfigurationManager.getDefault().install(
        MyApplication.class,
        Path.of(System.getProperty("user.home"), ".my-application"));

configuration.getCommand(SaveCommand.class)
        .setCommandPerformer((command, event) -> saveDocument());

frame.setJMenuBar(configuration.getMenuBar());
frame.add(configuration.getToolbarPanel(), BorderLayout.NORTH);
configuration.installKeyBindings(frame.getRootPane());
```

Close the configuration when its window is disposed:

```java
configuration.close();
```

Run configuration loading, UI updates, and command callbacks on Swing's event dispatch thread. The configuration loader creates command classes using no-argument constructors. Keep command IDs stable because layouts and saved configurations refer to them by ID.

## How it works

A command is the shared application operation and presentation state. Its presenters are ordinary Swing components backed by that command. The host application owns the behavior:

- `CallableCommand` subclasses implement `performCommand`.
- `CallbackCommand` subclasses receive host behavior through a `CommandPerformer`.
- `BooleanCommand` and `BooleanCallbackCommand` add selected state for toggle controls.

Place the factory configuration at `src/main/resources/swing-commands.json`. `ConfigurationManager` uses the anchor class to find that standard classpath resource, copies it to the application settings directory on first run, and loads the copy as the active configuration. The host application chooses the settings directory (for example, `$HOME/.wasp`); the persisted filename is always `swing-commands.json`. The factory configuration remains the reset baseline. The configuration builds menu and toolbar components and installs shortcuts on a window's `JRootPane`. Menu and toolbar layouts are available as `ResourceModel` instances for Swing tree views and supported model edits.

## Configuration files

The application defaults describe registered commands and their initial menus and toolbars. Optional command properties include `label`, `tooltip`, `shortcut`, `icon`, `enabled`, and `selected` for boolean commands. Keyboard shortcuts use Swing `KeyStroke` syntax, for example `ctrl pressed S`.

The library validates JSON structure and configuration semantics, including command IDs, command classes, shortcut conflicts, exclusive toggle groups, and dropdown defaults. The schema resources are:

- `swing-command-schema.json` for both factory and saved user configurations
- `command-layout-schema.json` for shared menu and toolbar definitions

For JSON Schema support in an IDE, keep these resources beside the configuration file referenced by `$schema`. Runtime validation uses the schemas bundled with the library.

The application file is a complete configuration, including command registrations, presentation properties, menus, and toolbars. It is loaded as the active configuration rather than merged with factory values. Customizations are saved to this file. Invalid application configurations are preserved and ignored as a whole; the factory configuration remains active for that run, and `Configuration.getLoadWarning()` reports why the saved file was rejected.

Use `Configuration.setKeyBinding` when changing shortcuts programmatically so conflicts can be checked. Close a configuration before installing another one that uses the same command classes; command instances are shared by class.

## Customization tools

The standalone configuration editor edits application defaults without loading or executing the host application's command classes. Run it from the project checkout with:

```powershell
.\gradlew.bat runEditor
```

To open a configuration file directly:

```powershell
.\gradlew.bat runEditor -PconfigFile=C:/path/to/commands.json
```

At runtime, `ConfigurationCustomizerDialog` lets users change registered command presentation, menus, toolbars, and shortcuts. Save writes the complete configuration to the application's user settings folder; it does not add Java commands or change their behavior.

## Fat JAR

For deployments that need a single JAR containing the runtime dependencies, build the shaded artifact from the project checkout:

```powershell
.\gradlew.bat shadowJar
```

The output is `build/libs/swing-commands-<version>-all.jar`. Use this JAR instead of the Maven dependency, not alongside it. Dependencies inside the fat JAR are relocated to avoid conflicts with the host application's Jackson and other libraries.

## Build from source

```powershell
.\gradlew.bat build
```

The build runs the runtime, configuration-editor, icon-preview, and shortcut-recorder checks. To generate the API documentation locally:

```powershell
.\gradlew.bat javadoc
```
