/*
 * Copyright (c) 2026 Code Cain
 * https://codecain.dev
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.codecain.swing.commands.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Test access to the package-private configuration loading entry point. */
public final class ConfigurationManagerTestSupport {

    private ConfigurationManagerTestSupport() {
    }

    public static Configuration install(
            InputStream factoryConfiguration,
            ClassLoader loader,
            Path applicationSettingsDirectory) throws IOException {

        return new ConfigurationManager().install(
                factoryConfiguration,
                loader,
                applicationSettingsDirectory);
    }

    public static Configuration install(
            Path factoryConfiguration,
            Path applicationSettingsDirectory) throws IOException {

        try (InputStream input = Files.newInputStream(factoryConfiguration)) {
            return install(
                    input,
                    Thread.currentThread().getContextClassLoader(),
                    applicationSettingsDirectory);
        }
    }
}
