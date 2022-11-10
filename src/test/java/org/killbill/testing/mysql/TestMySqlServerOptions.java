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

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.testng.Assert;
import org.testng.annotations.Test;

import io.airlift.units.Duration;

public class TestMySqlServerOptions {

    private static final Duration TEN_SECONDS = new Duration(10, TimeUnit.SECONDS);

    @Test
    public void testBuilder() {
        final MySqlServerOptions options = MySqlServerOptions.builder("any").build();
        Assert.assertEquals(options.getDatabaseNames(), Set.of("any"));
        Assert.assertEquals(options.getUsername(), "root");
        Assert.assertEquals(options.getPassword(), "");
        Assert.assertNotEquals(options.getPort(), 0);
        Assert.assertEquals(options.getStartupWait(), TEN_SECONDS);
        Assert.assertEquals(options.getShutdownWait(), TEN_SECONDS);
        Assert.assertEquals(options.getCommandTimeout(), new Duration(30, TimeUnit.SECONDS));

        // Call #builder() without any arguments will throw RuntimeException
        try {
            MySqlServerOptions.builder().build();
            Assert.fail("RuntimeException should be thrown");
        } catch (final RuntimeException e) {
            Assert.assertEquals(e.getMessage(), "'databaseNames' in MySqlServerOptions#builder(databaseNames) is null or empty");
        }

        // Call #builder() with null arguments will throw RuntimeException
        final String aNull = null;
        try {
            MySqlServerOptions.builder(aNull).build();
        } catch (final Exception e) {
            Assert.assertEquals(e.getMessage(), "'databaseNames' in MySqlServerOptions#builder(databaseNames) is null or empty");
        }
    }


    @Test
    public void testOptionalAttributes() {
        final MySqlServerOptions options = MySqlServerOptions
                .builder("any")
                .setUsername("any")
                .setPassword("any")
                .setStartupWait(30)
                .setShutdownWait(30)
                .setCommandTimeout(60)
                .build();

        Assert.assertEquals(options.getUsername(), "any");
        Assert.assertEquals(options.getPassword(), "any");
        Assert.assertEquals(options.getStartupWait(), new Duration(30, TimeUnit.SECONDS));
        Assert.assertEquals(options.getShutdownWait(), new Duration(30, TimeUnit.SECONDS));
        Assert.assertEquals(options.getCommandTimeout(), new Duration(60, TimeUnit.SECONDS));

        Assert.assertEquals(options.getJdbcUrl("any"),
                            "jdbc:mysql://localhost:"+ options.getPort() +"/any?user=any&password=any&useSSL=false&" +
                            "allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true&allowMultiQueries=true&permitMysqlScheme=true");
    }
}
