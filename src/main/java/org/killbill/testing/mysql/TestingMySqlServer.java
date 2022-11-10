/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.killbill.testing.mysql;

import java.io.Closeable;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TestingMySqlServer implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(TestingMySqlServer.class);

    private final String version;
    private final EmbeddedMySql server;

    public TestingMySqlServer(final MySqlServerOptions options) throws Exception {
        log.info("Will start MySQL server for testing with database: {} at: {}",
                 options.getDatabaseNames().toString(),
                 options.getJdbcUrl("<see-previous-db-list>"));

        server = new EmbeddedMySql(options);

        try (final Connection connection = server.getMySqlDatabase()) {
            version = connection.getMetaData().getDatabaseProductVersion();
            try (final Statement statement = connection.createStatement()) {
                execute(statement, String.format("CREATE USER '%s'@'%%' IDENTIFIED WITH mysql_native_password BY '%s'", options.getUsername(), options.getPassword()));
                execute(statement, String.format("GRANT ALL ON *.* to '%s'@'%%' WITH GRANT OPTION", options.getUsername()));
                for (final String database : options.getDatabaseNames()) {
                    execute(statement, String.format("CREATE DATABASE %s", database));
                }
            }
        } catch (final SQLException e) {
            close();
            throw e;
        }

        log.info("MySQL server ready");
    }

    private static void execute(final Statement statement, final String sql) throws SQLException {
        log.debug("Executing: {}", sql);
        statement.execute(sql);
    }

    @Override
    public void close() {
        server.close();
    }

    /**
     * Get MySQL version of started server.
     */
    public String getMySqlVersion() {
        return version;
    }

    /**
     * Get MySQL server directory.
     */
    public String getServerDirectory() {
        return server.getServerDirectory().toString();
    }
}
