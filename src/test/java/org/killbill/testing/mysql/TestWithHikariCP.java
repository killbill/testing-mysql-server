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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.testng.Assert;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class TestWithHikariCP {

    private MySqlServerOptions options;
    private TestingMySqlServer mySqlServer;

    @BeforeSuite
    public void beforeSuite() throws Exception {
        options = MySqlServerOptions.builder("test1", "test2").build();
        mySqlServer = new TestingMySqlServer(options);
    }

    @AfterSuite
    public void afterSuite() {
        if (mySqlServer != null) {
            mySqlServer.close();
        }
    }

    private DataSource createHikariDataSource(final String dbName) {
        final HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setUsername(options.getUsername());
        hikariConfig.setPassword(options.getPassword());
        hikariConfig.setJdbcUrl(options.getJdbcUrl(dbName));

        return new HikariDataSource(hikariConfig);
    }

    @Test
    public void testCreateDataSource() {
        DataSource dataSource = createHikariDataSource("test1");
        try (final Connection connection = dataSource.getConnection()) {
            final Statement statement = connection.createStatement();
            statement.execute("CREATE TABLE person (id bigint PRIMARY KEY, name varchar(20));");
            statement.execute("INSERT INTO person (id, name) VALUES (1, 'p1')");
            final ResultSet rs = statement.executeQuery("select * from person");
            Assert.assertTrue(rs.next());
        } catch (final SQLException e) {
            throw new RuntimeException("Error when testCreateDatasource: " + e);
        }

        dataSource = createHikariDataSource("test2");
        try (final Connection connection = dataSource.getConnection()) {
            final Statement statement = connection.createStatement();
            statement.executeQuery("select * from person");
        } catch (final SQLException e) {
            // Table person only exist in database named 'test2'
            Assert.assertEquals(e.getMessage(), "Table 'test2.person' doesn't exist");
        }
    }
}
