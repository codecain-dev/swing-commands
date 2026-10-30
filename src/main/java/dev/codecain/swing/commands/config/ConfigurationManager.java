/*
 * Copyright (c) 2026 Code Cain
 * https://codecain.dev
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.codecain.swing.commands.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.networknt.schema.*;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;

import dev.codecain.swing.commands.command.*;
import dev.codecain.swing.commands.resource.*;
import dev.codecain.swing.commands.resource.ResourceNode.Kind;

/**
 * Coordinates loading, validation, and installation of a runtime configuration.
 *
 * <p>An installation copies the factory configuration to the host application's
 * settings directory on first run, then loads that application copy. The factory
 * configuration remains the reset baseline.</p>
 */
public final class ConfigurationManager {

    /** Stable name of the configuration file stored in the host application's settings directory. */
    public static final String CONFIGURATION_FILE_NAME = "swing-commands.json";

    private static final ConfigurationManager DEFAULT = new ConfigurationManager();

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    // Baselines are cached by command identity so repeated installs start from Java defaults.
    private static final Map<Command, Presentation> JAVA_DEFAULTS = new IdentityHashMap<>();

    private Configuration current;

    /**
     * Captures a command's Java-defined presentation before configuration overrides.
     */
    private record Presentation(
            String label,
            String tooltip,
            String shortcut,
            String icon,
            Icon image,
            Boolean selected,
            boolean enabled) {

        static Presentation of(Command command) {

            String tooltip = Objects.toString(command.getToolTip(), "");
            String shortcut = Objects.toString(command.getKeyBinding(), "");
            Boolean selected = command instanceof BooleanCommand booleanCommand
                    ? booleanCommand.isSelected()
                    : null;

            return new Presentation(
                    command.getName(),
                    tooltip,
                    shortcut,
                    command.getIconResource(),
                    command.getIcon(),
                    selected,
                    command.isEnabled());
        }
    }

    /**
     * Returns the shared manager used by the standard runtime setup.
     *
     * @return shared configuration manager
     */
    public static ConfigurationManager getDefault() {
        return DEFAULT;
    }

    /**
     * Returns the configuration most recently installed by this manager.
     *
     * @return current live configuration
     * @throws IllegalStateException if this manager has not installed a configuration
     */
    public Configuration getCurrent() {

        if (current == null) {
            throw new IllegalStateException("No configuration installed");
        }

        return current;
    }

    /**
     * Copies the factory configuration to the application settings directory when
     * needed, then loads the application configuration from that directory.
     *
     * @param anchor class used to locate the factory configuration and its command classes
     * @param applicationSettingsDirectory directory for the application's saved configuration
     * @return the installed live configuration
     * @throws IOException if the resource is missing, malformed, or invalid
     */
    public Configuration install(Class<?> anchor, Path applicationSettingsDirectory)
            throws IOException {

        try (InputStream input = anchor.getResourceAsStream(
                "/" + CONFIGURATION_FILE_NAME)) {
            if (input == null) {
                throw new IOException(
                        "Missing factory configuration resource: /"
                                + CONFIGURATION_FILE_NAME);
            }

            return install(input, anchor.getClassLoader(), applicationSettingsDirectory);
        }
    }

    /**
     * Reads factory configuration JSON from a stream, copies it to the application
     * settings directory when needed, and installs the application copy.
     *
     * <p>The stream remains open after this method returns. The factory file is copied
     * only when the stable application configuration file does not already exist.
     * Invalid application configurations are preserved, left unapplied, and recorded
     * as a warning.</p>
     *
     * @param input stream containing the default configuration JSON
     * @param loader class loader used to resolve command classes
     * @param applicationSettingsDirectory directory for the application's saved configuration
     * @return the installed live configuration
     * @throws IOException if the defaults cannot be read, validated, or applied
     * @throws IllegalStateException if another configuration is still open
     */
    Configuration install(
            InputStream input,
            ClassLoader loader,
            Path applicationSettingsDirectory)
            throws IOException {

        if (current != null && !current.isClosed()) {
            throw new IllegalStateException(
                    "Close the current configuration before installing another");
        }

        if (applicationSettingsDirectory == null) {
            throw new IllegalArgumentException(
                    "Application settings directory must be supplied");
        }

        byte[] factoryBytes = input.readAllBytes();
        Parsed defaults = parse(read(new ByteArrayInputStream(factoryBytes)), loader);
        Path applicationConfiguration = prepareApplicationConfiguration(
                factoryBytes,
                applicationSettingsDirectory);
        SavedConfiguration saved = readSavedConfiguration(loader, applicationConfiguration);
        apply(defaults);

        Configuration config = new Configuration(
                defaults.name,
                defaults.commands,
                defaults.menus,
                defaults.toolbars,
                defaults.commandsById,
                applicationConfiguration);

        if (saved.parsed() != null) {
            try {
                config.applyUserConfiguration(saved.parsed());
            } catch (IllegalArgumentException exception) {
                config.resetToDefaults();
                config.setLoadWarning(
                        "Application configuration was preserved but not applied; "
                                + "the factory configuration was loaded: "
                                + exception.getMessage());
            }
        }

        if (saved.warning() != null) {
            config.setLoadWarning(saved.warning());
        }

        current = config;

        return config;
    }

    /**
     * Creates the application directory and initial user configuration when needed.
     */
    private static Path prepareApplicationConfiguration(
            byte[] factoryBytes,
            Path applicationSettingsDirectory) throws IOException {

        Files.createDirectories(applicationSettingsDirectory);
        Path applicationConfiguration = applicationSettingsDirectory
                .resolve(CONFIGURATION_FILE_NAME);

        if (!Files.exists(applicationConfiguration)) {
            try {
                Files.write(
                        applicationConfiguration,
                        factoryBytes,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
            } catch (FileAlreadyExistsException ignored) {
                // Another startup created the initial copy first.
            }
        }

        return applicationConfiguration;
    }

    private SavedConfiguration readSavedConfiguration(
            ClassLoader loader,
            Path applicationConfiguration) {

        try (InputStream applicationInput = Files.newInputStream(applicationConfiguration)) {
            JsonNode document = read(applicationInput);
            Parsed savedConfiguration = parse(document, loader);

            return new SavedConfiguration(savedConfiguration, null);
        } catch (IOException | IllegalArgumentException exception) {
            String warning = "Application configuration was preserved but not applied; "
                    + "the factory configuration was loaded: "
                    + exception.getMessage();

            return new SavedConfiguration(null, warning);
        }
    }

    private static JsonNode read(InputStream input) throws IOException {

        JsonNode document = JSON.readTree(input);
        validate(document);

        return document;
    }

    /**
     * Validates a complete application configuration against its bundled schema.
     */
    private static void validate(JsonNode document) throws IOException {

        String resource = "/swing-command-schema.json";

        try (InputStream input = ConfigurationManager.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing bundled schema " + resource);
            }

            JsonSchema schema = JsonSchemaFactory
                    .getInstance(SpecVersion.VersionFlag.V202012)
                    .getSchema(
                            URI.create("classpath:" + resource),
                            JSON.readTree(input));

            JsonNode value = document == null ? NullNode.instance : document;
            Set<ValidationMessage> errors = schema.validate(value);

            if (!errors.isEmpty()) {
                List<String> messages = new ArrayList<>();

                for (ValidationMessage error : errors) {
                    messages.add(error.toString());
                }

                Collections.sort(messages);

                throw new IOException(
                        "Configuration schema errors: " + String.join("; ", messages));
            }
        }
    }

    /**
     * Temporary result of parsing JSON before its values are applied to live commands.
     */
    static final class Parsed {

        String name;
        ResourceModel commands;
        ResourceModel menus;
        ResourceModel toolbars;

        final Map<String, Command> commandsById = new LinkedHashMap<>();
        final Map<Command, Map<String, String>> properties = new LinkedHashMap<>();
        final Map<Command, Icon> factoryIcons = new IdentityHashMap<>();
    }

    private record SavedConfiguration(Parsed parsed, String warning) {
    }

    /**
     * Builds resource models and property maps from a complete configuration document.
     */
    private static Parsed parse(JsonNode document, ClassLoader loader)
            throws IOException {

        try {
            Parsed parsed = new Parsed();

            parsed.name = document.path("name").asText("Application");

            parsed.commands = new ResourceModel(
                    ResourceModel.Type.COMMAND,
                    new ResourceNode(Kind.ROOT, "Commands"));

            readCommands(document, loader, parsed);

            parsed.menus = readModel(
                    document,
                    "menus",
                    ResourceModel.Type.MENU,
                    parsed,
                    null);
            parsed.toolbars = readModel(
                    document,
                    "toolbars",
                    ResourceModel.Type.TOOLBAR,
                    parsed,
                    null);

            validateShortcutsAndIcons(parsed);
            Configuration.validateGroups(parsed.menus, parsed.toolbars);

            return parsed;
        } catch (Exception exception) {
            throw new IOException(
                    "Invalid configuration: " + exception.getMessage(),
                    exception);
        }
    }

    private static void readCommands(
            JsonNode document,
            ClassLoader loader,
            Parsed parsed) throws ClassNotFoundException {

        Iterator<Map.Entry<String, JsonNode>> entries =
                document.path("commands").fields();

        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            String id = entry.getKey();
            JsonNode definition = entry.getValue();
            Command command = resolveCommand(id, definition, loader, parsed);

            Map<String, String> properties = readCommandProperties(
                    id,
                    definition,
                    command,
                    parsed);

            parsed.properties.put(command, properties);

            addCommandNode(parsed.commands, definition, command);
        }
    }

    private static Command resolveCommand(
            String id,
            JsonNode definition,
            ClassLoader loader,
            Parsed parsed) throws ClassNotFoundException {

        Class<? extends Command> type = Class
                .forName(definition.get("class").asText(), false, loader)
                .asSubclass(Command.class);
        Command command = Command.get(type);

        if (parsed.commandsById.containsValue(command)) {
            throw new IllegalArgumentException(
                    "$.commands." + id
                            + ": class already registered under another ID");
        }

        parsed.commandsById.put(id, command);

        return command;
    }
    private static Map<String, String> readCommandProperties(
            String id,
            JsonNode definition,
            Command command,
            Parsed parsed) {

        Map<String, String> properties = new LinkedHashMap<>();

        addFactoryProperties(command, properties, parsed);

        List<String> fields = List.of("label", "tooltip", "shortcut", "icon", "enabled");

        for (String field : fields) {
            if (definition.has(field)) {
                JsonNode value = definition.get(field);
                properties.put(field, value.isNull() ? "" : value.asText());
            }
        }

        if (definition.has("selected")) {
            if (!(command instanceof BooleanCommand)) {
                throw new IllegalArgumentException(
                        "$.commands." + id + ".selected: requires a BooleanCommand");
            }

            properties.put("selected", definition.get("selected").asText());
        }

        return properties;
    }

    private static void addFactoryProperties(
            Command command,
            Map<String, String> properties,
            Parsed parsed) {

        Presentation baseline = JAVA_DEFAULTS.computeIfAbsent(
                command,
                Presentation::of);

        properties.put("label", baseline.label);
        properties.put("tooltip", baseline.tooltip);
        properties.put("shortcut", baseline.shortcut);
        properties.put("enabled", Boolean.toString(baseline.enabled));

        if (baseline.selected != null) {
            properties.put("selected", baseline.selected.toString());
        }

        parsed.factoryIcons.put(command, baseline.image);

        if (baseline.icon != null) {
            properties.put("icon", baseline.icon);
        }
    }

    private static void addCommandNode(
            ResourceModel model,
            JsonNode definition,
            Command command) {

        ResourceNode parent = model.getRoot();
        String category = definition.path("category").asText("");

        if (!category.isBlank()) {
            ResourceNode group = findCategory(parent, category);

            if (group == null) {
                group = ResourceNode.group(category);
                model.add(parent, group);
            }

            parent = group;
        }

        model.add(parent, ResourceNode.command(command));
    }

    private static ResourceNode findCategory(ResourceNode parent, String category) {

        for (int i = 0; i < parent.getChildCount(); i++) {
            ResourceNode child = parent.getChildAt(i);

            if (child.getName().equals(category)) {
                return child;
            }
        }

        return null;
    }

    private static void validateShortcutsAndIcons(Parsed parsed) {

        Map<KeyStroke, String> shortcuts = new HashMap<>();

        for (Map.Entry<String, Command> entry : parsed.commandsById.entrySet()) {
            String id = entry.getKey();
            Command command = entry.getValue();
            Map<String, String> properties = parsed.properties.getOrDefault(
                    command,
                    Map.of());

            KeyStroke key = properties.containsKey("shortcut")
                    ? parseKey(properties.get("shortcut"))
                    : command.getKeyBinding();

            if (key != null && shortcuts.put(key, id) != null) {
                throw new IllegalArgumentException(
                        "$.commands." + id + ".shortcut: duplicate shortcut " + key);
            }

            if (properties.containsKey("icon")) {
                Command.loadIcon(command.getClass(), properties.get("icon"));
            }
        }
    }

    private static ResourceModel readModel(
            JsonNode document,
            String field,
            ResourceModel.Type type,
            Parsed parsed,
            ResourceModel fallback) {

        String rootName = type == ResourceModel.Type.MENU ? "Menus" : "Toolbars";
        ResourceModel model = new ResourceModel(
                type,
                new ResourceNode(Kind.ROOT, rootName));

        if (!document.has(field) && fallback != null) {
            model.replaceWith(fallback.getRoot().copy());

            return model;
        }

        Set<String> ids = new HashSet<>();
        int index = 0;

        for (JsonNode node : document.path(field)) {
            String path = "$." + field + "[" + index++ + "]";
            ResourceNode group = readNode(node, parsed, path, ids);
            model.add(model.getRoot(), group);
        }

        return model;
    }

    private static ResourceNode readNode(
            JsonNode node,
            Parsed parsed,
            String path,
            Set<String> ids) {

        if (node.has("command")) {
            String id = node.get("command").asText();
            Command command = parsed.commandsById.get(id);

            if (command == null) {
                throw new IllegalArgumentException(
                        path + ".command: unknown command ID " + id);
            }

            return ResourceNode.command(command);
        }

        if (node.has("separator")) {
            return new ResourceNode(Kind.SEPARATOR, "Separator");
        }

        Kind kind = node.has("toggleGroup")
                ? Kind.TOGGLE_GROUP
                : node.has("dropdown")
                        ? Kind.DROPDOWN
                        : Kind.GROUP;
        String labelField = kind == Kind.TOGGLE_GROUP
                ? "toggleGroup"
                : kind == Kind.DROPDOWN
                        ? "dropdown"
                        : "label";
        String label = node.path(labelField).asText();
        ResourceNode result = new ResourceNode(kind, label);

        if (node.has("id") && !ids.add(node.get("id").asText())) {
            throw new IllegalArgumentException(path + ".id: duplicate container ID");
        }

        List<String> attributes = List.of(
                "id",
                "mnemonic",
                "showLabels",
                "floatable");

        for (String field : attributes) {
            if (node.has(field)) {
                result.getAttributes().put(field, node.get(field).asText());
            }
        }

        int index = 0;

        for (JsonNode child : node.path("items")) {
            String childPath = path + ".items[" + index++ + "]";
            result.add(readNode(child, parsed, childPath, ids));
        }

        if (node.has("defaultCommand")) {
            applyDefaultCommand(node, result, parsed, path);
        }

        return result;
    }

    private static void applyDefaultCommand(
            JsonNode node,
            ResourceNode dropdown,
            Parsed parsed,
            String path) {

        String selectedId = node.get("defaultCommand").asText();
        Command selectedCommand = parsed.commandsById.get(selectedId);
        boolean found = false;

        // A dropdown default must refer to one of this dropdown's own entries.
        for (int i = 0; i < dropdown.getChildCount(); i++) {
            ResourceNode child = dropdown.getChildAt(i);

            if (child.getCommand() == selectedCommand) {
                child.getAttributes().put("default", "true");
                found = true;
            }
        }

        if (!found) {
            throw new IllegalArgumentException(
                    path + ".defaultCommand must reference an item in this dropdown");
        }
    }

    /**
     * Parses and validates a Swing keystroke used as a command shortcut.
     *
     * @param value Swing keystroke text, or {@code null} or blank for no shortcut
     * @return the parsed pressed keystroke, or {@code null} when none is supplied
     * @throws IllegalArgumentException if the value is not an allowed shortcut
     */
    public static KeyStroke parseKey(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        KeyStroke key = KeyStroke.getKeyStroke(value);

        if (key == null
                || key.getKeyEventType() != java.awt.event.KeyEvent.KEY_PRESSED) {
            throw new IllegalArgumentException(
                    "Use a pressed shortcut such as 'ctrl pressed S'");
        }

        boolean functionKey = key.getKeyCode() >= java.awt.event.KeyEvent.VK_F1
                && key.getKeyCode() <= java.awt.event.KeyEvent.VK_F12;

        if (key.getModifiers() == 0 && !functionKey) {
            throw new IllegalArgumentException(
                    "Shortcuts need a modifier or an F1-F12 key");
        }

        return key;
    }

    /**
     * Applies parsed command properties after resolving all icons successfully.
     */
    static void apply(Parsed parsed) {

        // Load every icon before mutating any shared command.
        Map<Command, Icon> icons = new HashMap<>();

        for (Map.Entry<Command, Map<String, String>> entry
                : parsed.properties.entrySet()) {
            Command command = entry.getKey();
            Map<String, String> properties = entry.getValue();

            if (properties.containsKey("icon")) {
                Icon icon = Command.loadIcon(
                        command.getClass(),
                        properties.get("icon"));
                icons.put(command, icon);
            }
        }

        for (Map.Entry<Command, Icon> entry : parsed.factoryIcons.entrySet()) {
            entry.getKey().setIcon(entry.getValue());
        }

        for (Map.Entry<Command, Map<String, String>> entry
                : parsed.properties.entrySet()) {
            Command command = entry.getKey();

            for (Map.Entry<String, String> property : entry.getValue().entrySet()) {
                applyProperty(command, property.getKey(), property.getValue(), icons);
            }
        }
    }

    private static void applyProperty(
            Command command,
            String key,
            String value,
            Map<Command, Icon> icons) {

        switch (key) {
            case "label" -> command.setName(value);
            case "tooltip" -> command.setToolTip(value);
            case "shortcut" -> command.setKeyBinding(parseKey(value));
            case "selected" -> ((BooleanCommand) command)
                    .setSelected(Boolean.parseBoolean(value));
            case "enabled" -> command.setEnabled(Boolean.parseBoolean(value));
            case "icon" -> {
                command.setIcon(icons.get(command));
                command.putValue(
                        Command.ICON_RESOURCE,
                        value.isBlank() ? null : value);
            }
        }
    }

    /**
     * Serializes the complete live configuration and safely replaces the application configuration file.
     *
     * <p>An existing file is copied to a {@code .bak} sibling before replacement.
     * The temporary file is moved atomically when the file system supports it.</p>
     *
     * @param config live configuration to save
     * @param path destination for the complete configuration JSON
     * @throws IOException if validation or file operations fail
     */
    static void save(Configuration config, Path path) throws IOException {

        ObjectNode document = createConfigurationDocument(config);
        validate(document);
        validateConfigurationSemantics(document, config);

        Path target = path.toAbsolutePath();
        Path parent = target.getParent();

        Files.createDirectories(parent);

        Path temp = Files.createTempFile(parent, "commands-", ".tmp");

        try {
            writeConfiguration(document, target, temp);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static ObjectNode createConfigurationDocument(Configuration config) {

        ObjectNode document = JSON.createObjectNode();
        document.put("$schema", "swing-command-schema.json");
        document.put("version", 1);
        document.put("name", config.getName());

        ObjectNode commands = document.putObject("commands");
        Map<Command, String> categories = commandCategories(config.getCommandModel().getRoot());

        for (Command command : config.getCommands()) {
            ObjectNode properties = commands.putObject(config.commandId(command));
            properties.put("class", command.getClass().getName());

            String category = categories.get(command);
            if (category != null) {
                properties.put("category", category);
            }

            String label = Objects.toString(command.getName(), command.getClass().getSimpleName());
            properties.put("label", label);
            properties.put("tooltip", Objects.toString(command.getToolTip(), ""));
            properties.put("enabled", command.isEnabled());

            if (command instanceof BooleanCommand booleanCommand) {
                properties.put("selected", booleanCommand.isSelected());
            }

            if (command.getKeyBinding() == null) {
                properties.putNull("shortcut");
            } else {
                properties.put("shortcut", command.getKeyBinding().toString());
            }

            if (command.getIconResource() != null) {
                properties.put("icon", command.getIconResource());
            } else if (config.presentationOverrides(command).containsKey("icon")) {
                properties.putNull("icon");
            }
        }

        ArrayNode menus = writeChildren(config.getMenuModel().getRoot(), config);
        ArrayNode toolbars = writeChildren(config.getToolbarModel().getRoot(), config);
        document.set("menus", menus);
        document.set("toolbars", toolbars);

        return document;
    }

    private static Map<Command, String> commandCategories(ResourceNode root) {

        Map<Command, String> categories = new IdentityHashMap<>();
        collectCommandCategories(root, null, categories);

        return categories;
    }

    private static void collectCommandCategories(
            ResourceNode parent,
            String category,
            Map<Command, String> categories) {

        String childCategory = parent.getKind() == Kind.GROUP
                ? parent.getName()
                : category;

        for (int i = 0; i < parent.getChildCount(); i++) {
            ResourceNode child = parent.getChildAt(i);

            if (child.getKind() == Kind.COMMAND) {
                if (childCategory != null) {
                    categories.put(child.getCommand(), childCategory);
                }
            } else {
                collectCommandCategories(child, childCategory, categories);
            }
        }
    }

    private static void validateConfigurationSemantics(
            ObjectNode document,
            Configuration config) throws IOException {

        ClassLoader loader = ConfigurationManager.class.getClassLoader();

        for (Command command : config.getCommands()) {
            loader = command.getClass().getClassLoader();
            break;
        }

        parse(document, loader);
    }

    /**
     * Writes a temporary configuration file and safely replaces the destination.
     */
    private static void writeConfiguration(
            ObjectNode document,
            Path target,
            Path temp) throws IOException {

        JSON.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), document);

        if (Files.exists(target)) {
            Path backup = target.resolveSibling(target.getFileName() + ".bak");
            Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
        }

        try {
            Files.move(
                    temp,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static ArrayNode writeChildren(ResourceNode parent, Configuration config) {

        ArrayNode children = JSON.createArrayNode();

        for (int i = 0; i < parent.getChildCount(); i++) {
            ResourceNode node = parent.getChildAt(i);
            ObjectNode item = children.addObject();

            switch (node.getKind()) {
                case COMMAND -> item.put("command", config.commandId(node.getCommand()));
                case SEPARATOR -> item.put("separator", true);
                default -> writeContainer(item, node, config);
            }
        }

        return children;
    }

    private static void writeContainer(
            ObjectNode item,
            ResourceNode node,
            Configuration config) {

        String labelField = node.getKind() == Kind.TOGGLE_GROUP
                ? "toggleGroup"
                : node.getKind() == Kind.DROPDOWN
                        ? "dropdown"
                        : "label";
        item.put(labelField, node.getName());

        writeAttributes(item, node);
        item.set("items", writeChildren(node, config));

        if (node.getKind() == Kind.DROPDOWN) {
            writeDefaultCommand(item, node, config);
        }
    }

    private static void writeAttributes(ObjectNode item, ResourceNode node) {

        List<String> textAttributes = List.of("id", "mnemonic");

        for (String field : textAttributes) {
            if (node.getAttributes().containsKey(field)) {
                item.put(field, node.attribute(field, ""));
            }
        }

        List<String> booleanAttributes = List.of("showLabels", "floatable");

        for (String field : booleanAttributes) {
            if (node.getAttributes().containsKey(field)) {
                boolean value = Boolean.parseBoolean(node.attribute(field, "false"));
                item.put(field, value);
            }
        }
    }

    private static void writeDefaultCommand(
            ObjectNode item,
            ResourceNode dropdown,
            Configuration config) {

        for (int i = 0; i < dropdown.getChildCount(); i++) {
            ResourceNode child = dropdown.getChildAt(i);

            if (Boolean.parseBoolean(child.attribute("default", "false"))) {
                item.put("defaultCommand", config.commandId(child.getCommand()));
            }
        }
    }
}
