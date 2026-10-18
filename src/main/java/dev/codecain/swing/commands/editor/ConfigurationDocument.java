/*
 * Copyright (c) 2026 Jon Kent
 * https://codecain.dev
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.codecain.swing.commands.editor;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.KeyStroke;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;

import dev.codecain.swing.commands.config.ConfigurationManager;

/**
 * Editor-owned configuration document. It never loads or executes application command classes.
 */
final class ConfigurationDocument {

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    ObjectNode root;
    private String saved;
    private Path path;
    private boolean repairedVersion;

    ConfigurationDocument() {

        root = JSON.createObjectNode();
        root.put("version", 1);
        root.put("name", "My Application");
        root.putObject("commands");
        root.putArray("menus");
        root.putArray("toolbars");

        saved = root.toString();
    }

    static ConfigurationDocument open(Path file) throws IOException {

        ConfigurationDocument document = new ConfigurationDocument();
        JsonNode input = JSON.readTree(file.toFile());

        if (!(input instanceof ObjectNode object)) {
            throw new IOException("Expected an application configuration object");
        }

        document.root = object;
        document.repairedVersion = !object.has("version");

        if (document.repairedVersion) {
            object.put("version", 1);
        }

        document.validate();
        document.path = file.toAbsolutePath();
        document.saved = object.toString();

        return document;
    }

    Path path() {
        return path;
    }

    boolean dirty() {
        return repairedVersion || !root.toString().equals(saved);
    }

    boolean repairedVersion() {
        return repairedVersion;
    }

    ObjectNode commands() {
        return (ObjectNode) root.get("commands");
    }

    ArrayNode section(String name) {

        if (!root.has(name)) {
            root.putArray(name);
        }

        return (ArrayNode) root.get(name);
    }

    String label(JsonNode node) {

        if (node.has("command")) {
            String commandId = node.path("command").asText();

            return commands()
                    .path(commandId)
                    .path("label")
                    .asText(commandId);
        }

        if (node.has("separator")) {
            return "────────";
        }

        for (String key : List.of("label", "toggleGroup", "dropdown")) {
            if (node.has(key)) {
                return node.path(key).asText();
            }
        }

        return "Layout";
    }

    void putCommand(String oldId, String id, ObjectNode definition) throws IOException {

        if (!id.matches("[A-Za-z][A-Za-z0-9_.-]*")) {
            throw new IOException(
                    "Command ID must start with a letter; use letters, digits, '.', '_' or '-'.");
        }

        if (!id.equals(oldId) && commands().has(id)) {
            throw new IOException("That command ID already exists.");
        }

        ObjectNode before = root.deepCopy();

        if (oldId != null && !oldId.equals(id)) {
            commands().remove(oldId);
            replaceReferences(root.path("menus"), oldId, id);
            replaceReferences(root.path("toolbars"), oldId, id);
        }

        commands().set(id, definition);

        try {
            validate();
        } catch (IOException exception) {
            root = before;
            throw exception;
        }
    }

    private static void replaceReferences(JsonNode node, String oldId, String newId) {

        if (node instanceof ObjectNode object) {
            for (String field : List.of("command", "defaultCommand")) {
                if (object.path(field).asText().equals(oldId)) {
                    object.put(field, newId);
                }
            }
        }

        node.forEach(child -> replaceReferences(child, oldId, newId));
    }

    boolean referenced(String id) {
        return referenced(root.path("menus"), id)
                || referenced(root.path("toolbars"), id);
    }

    private static boolean referenced(JsonNode node, String id) {

        if (node.path("command").asText().equals(id)
                || node.path("defaultCommand").asText().equals(id)) {
            return true;
        }

        for (JsonNode child : node) {
            if (referenced(child, id)) {
                return true;
            }
        }

        return false;
    }

    void removeCommand(String id) throws IOException {

        if (referenced(id)) {
            throw new IOException(
                    "Remove this command's menu/toolbar placements before deleting its registration.");
        }

        commands().remove(id);
    }

    void validate() throws IOException {

        try (InputStream input = ConfigurationDocument.class
                .getResourceAsStream("/swing-command-schema.json")) {
            if (input == null) {
                throw new IOException("Bundled schema is missing");
            }

            JsonSchema schema = JsonSchemaFactory
                    .getInstance(SpecVersion.VersionFlag.V202012)
                    .getSchema(
                            URI.create("classpath:/swing-command-schema.json"),
                            JSON.readTree(input));

            Set<?> errors = schema.validate(root);

            if (!errors.isEmpty()) {
                List<String> messages = new ArrayList<>();

                for (Object error : errors) {
                    messages.add(error.toString());
                }

                Collections.sort(messages);

                throw new IOException(String.join("\n", messages));
            }
        }

        Set<String> classes = new HashSet<>();
        Set<KeyStroke> shortcuts = new HashSet<>();
        var fields = commands().fields();

        while (fields.hasNext()) {
            var field = fields.next();
            JsonNode command = field.getValue();

            if (!classes.add(command.path("class").asText())) {
                throw new IOException(
                        "Command " + field.getKey() + ": class is already registered.");
            }

            try {
                KeyStroke key = ConfigurationManager
                        .parseKey(command.path("shortcut").asText(""));

                if (key != null && !shortcuts.add(key)) {
                    throw new IOException(
                            "Command " + field.getKey() + ": duplicate shortcut " + key);
                }
            } catch (IllegalArgumentException exception) {
                throw new IOException(
                        "Command " + field.getKey() + ": " + exception.getMessage(),
                        exception);
            }
        }

        List<Set<String>> groups = new ArrayList<>();

        validateNodes(root.path("menus"), "menus", new HashSet<>(), groups);
        validateNodes(root.path("toolbars"), "toolbars", new HashSet<>(), groups);
    }

    private void validateNodes(
            JsonNode node,
            String path,
            Set<String> ids,
            List<Set<String>> groups) throws IOException {

        if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                validateNodes(node.get(index), path + "[" + index + "]", ids, groups);
            }

            return;
        }

        if (!node.isObject()) {
            return;
        }

        if (node.has("command") && !commands().has(node.path("command").asText())) {
            throw new IOException(
                    path + ": unknown command ID " + node.path("command").asText());
        }

        if (node.has("id") && !ids.add(node.path("id").asText())) {
            throw new IOException(path + ": duplicate container ID");
        }

        if (node.has("defaultCommand")) {
            boolean found = false;

            for (JsonNode item : node.path("items")) {
                if (item.path("command").equals(node.get("defaultCommand"))) {
                    found = true;
                }
            }

            if (!found) {
                throw new IOException(
                        path + ": dropdown default must be one of its commands");
            }
        }

        if (node.has("toggleGroup")) {
            Set<String> members = new HashSet<>();

            for (JsonNode item : node.path("items")) {
                if (!members.add(item.path("command").asText())) {
                    throw new IOException(path + ": duplicate toggle command");
                }
            }

            for (Set<String> previous : groups) {
                if (!Collections.disjoint(previous, members)
                        && !previous.equals(members)) {
                    throw new IOException(
                            path + ": repeated exclusive groups must contain the same commands");
                }
            }

            groups.add(members);
        }

        validateNodes(node.path("items"), path + ".items", ids, groups);
    }

    void save(Path destination) throws IOException {

        // The editor owns the format version; the user does not edit it.
        root.put("version", 1);
        validate();

        Path target = destination.toAbsolutePath();
        Files.createDirectories(target.getParent());

        Path temporaryFile = Files.createTempFile(
                target.getParent(),
                "commands-",
                ".tmp");

        try {
            JSON.writerWithDefaultPrettyPrinter()
                    .writeValue(temporaryFile.toFile(), root);

            if (Files.exists(target)) {
                Path backup = target.resolveSibling(target.getFileName() + ".bak");

                Files.copy(
                        target,
                        backup,
                        StandardCopyOption.REPLACE_EXISTING);
            }

            try {
                Files.move(
                        temporaryFile,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(
                        temporaryFile,
                        target,
                        StandardCopyOption.REPLACE_EXISTING);
            }

            path = target;
            saved = root.toString();
            repairedVersion = false;
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }
}