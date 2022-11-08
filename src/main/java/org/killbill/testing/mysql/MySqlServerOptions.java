/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.testing.mysql;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashSet;
import java.util.Set;

import io.airlift.units.Duration;
import static java.util.concurrent.TimeUnit.SECONDS;

public final class MySqlServerOptions {

    private static final String JDBC_URL = "jdbc:mysql://localhost:%s/" +
        "%s?" +
        "user=%s&" +
        "password=%s&" +
        "useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true&allowMultiQueries=true&permitMysqlScheme=true";

    private final String username;
    private final String password;
    private final int port;
    private final Set<String> databaseNames;

    private final Duration startupWait;
    private final Duration shutdownWait;
    private final Duration commandTimeout;

    private MySqlServerOptions(final Builder builder) {
        username = builder.username;
        password = builder.password;
        port = randomPort();
        databaseNames = builder.databaseNames;
        startupWait = builder.startupWait;
        shutdownWait = builder.shutdownWait;
        commandTimeout = builder.commandTimeout;
    }

    private static int randomPort() {
        try (final ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (final IOException e) {
            throw new RuntimeException("Cannot getLocalPort(): " + e);
        }
    }

    /**
     * Calling this and {@link Builder#build()} will set username=root, password="", startupWait=10s, shutdownWait=10s,
     * and commandTimeout=10s.
     */
    public static Builder builder(final String... databaseNames) {
        return new Builder(databaseNames);
    }

    /**
     * Construct valid MySql connection string.
     */
    public String getJdbcUrl(final String databaseName) {
        return String.format(JDBC_URL, port, databaseName, username, password);
    }

    /**
     * Calling this method will construct JDBC url without selecting database and user default username=root and
     * password=''. For example, {@code EmbeddedMySql#checkReady()} need this for checking server state.
     */
    String getRootJdbcUrl() {
        return String.format(JDBC_URL, port, "", "root", "");
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public int getPort() {
        return port;
    }

    public Set<String> getDatabaseNames() {
        return databaseNames;
    }

    public Duration getStartupWait() {
        return startupWait;
    }

    public Duration getShutdownWait() {
        return shutdownWait;
    }

    public Duration getCommandTimeout() {
        return commandTimeout;
    }

    public static final class Builder {

        private String username = "root";
        private String password = "";
        private final Set<String> databaseNames;
        private Duration startupWait = new Duration(10, SECONDS);
        private Duration shutdownWait = new Duration(10, SECONDS);
        private Duration commandTimeout = new Duration(30, SECONDS);

        private Builder(final String... databaseNames) {
            this.databaseNames = buildDatabaseNames(databaseNames);
        }

        // Make sure that if single null passed, it will make empty set and get validated in #build().
        private static Set<String> buildDatabaseNames(final String... databaseNames) {
            final Set<String> result = new HashSet<>();
            for (final String name : databaseNames) {
                result.add(name);
            }
            return result;
        }

        public Builder setUsername(final String username) {
            this.username = username;
            return this;
        }

        public Builder setPassword(final String password) {
            this.password = password;
            return this;
        }

        public Builder setStartupWait(final int startupWaitInSecs) {
            this.startupWait = new Duration(startupWaitInSecs, SECONDS);
            return this;
        }

        public Builder setShutdownWait(final int shutdownWaitInSecs) {
            this.shutdownWait = new Duration(shutdownWaitInSecs, SECONDS);
            return this;
        }

        public Builder setCommandTimeout(final int commandTimeoutInSecs) {
            this.commandTimeout = new Duration(commandTimeoutInSecs, SECONDS);
            return this;
        }

        public MySqlServerOptions build() {
            if (databaseNames.isEmpty()) {
                throw new RuntimeException("'databaseNames' in MySqlServerOptions#builder(databaseNames) is null or empty");
            }

            return new MySqlServerOptions(this);
        }
    }
}
