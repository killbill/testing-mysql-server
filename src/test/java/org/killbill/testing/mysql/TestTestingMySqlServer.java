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

import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.testng.Assert.*;

public class TestTestingMySqlServer {

    private static final String EXPECTED_MYSQL_VERSION = "8.0.31";

    @Test
    public void testDatabase() throws Exception {
        final MySqlServerOptions options = MySqlServerOptions
                .builder("db1", "db2")
                .setUsername("testuser")
                .setPassword("testpass")
                .build();
        try (final TestingMySqlServer server = new TestingMySqlServer(options)) {
            assertEquals(server.getMySqlVersion(), EXPECTED_MYSQL_VERSION);

            try (final Connection connection = DriverManager.getConnection(options.getJdbcUrl("db1"))) {
                assertEquals(connection.getMetaData().getDatabaseProductName(), "MySQL");
            }

            for (final String database : options.getDatabaseNames()) {
                try (final Connection connection = DriverManager.getConnection(options.getJdbcUrl(database))) {
                    try (final Statement statement = connection.createStatement()) {
                        statement.execute("CREATE TABLE test_table (c1 bigint PRIMARY KEY)");
                        statement.execute("INSERT INTO test_table (c1) VALUES (1)");
                        try (final ResultSet resultSet = statement.executeQuery("SELECT count(*) FROM test_table")) {
                            assertTrue(resultSet.next());
                            assertEquals(resultSet.getLong(1), 1L);
                            assertFalse(resultSet.next());
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testGlobal() throws Exception {
        final MySqlServerOptions options = MySqlServerOptions.builder("any").build();
        try (final TestingMySqlServer ignored = new TestingMySqlServer(options)) {
            final Connection connection = DriverManager.getConnection(options.getRootJdbcUrl());
            assertEquals(connection.getMetaData().getDatabaseProductName(), "MySQL");

            try (final Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE testdb");
                statement.execute("CREATE TABLE testdb.test_table (id bigint PRIMARY KEY)");
            }
        }
    }
}
