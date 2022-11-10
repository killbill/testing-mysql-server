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

import com.google.common.io.ByteStreams;
import io.airlift.command.Command;
import io.airlift.command.CommandFailedException;
import io.airlift.units.Duration;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.sql.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.StandardSystemProperty.OS_ARCH;
import static com.google.common.base.StandardSystemProperty.OS_NAME;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.io.MoreFiles.deleteRecursively;
import static com.google.common.io.RecursiveDeleteOption.ALLOW_INSECURE;
import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static java.io.File.createTempFile;
import static java.lang.String.format;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.createTempDirectory;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

final class EmbeddedMySql implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(EmbeddedMySql.class);

    private final ExecutorService executor = newCachedThreadPool(daemonThreadsNamed("testing-mysql-server-%s"));
    private final Path serverDirectory;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Process mysqld;

    private final int port;
    private final Duration startupWait;
    private final Duration shutdownWait;
    private final Duration commandTimeout;
    private final String jdbcUrl;

    public EmbeddedMySql(final MySqlServerOptions options) throws IOException {
        serverDirectory = createTempDirectory("testing-mysql-server");
        log.info("Starting MySQL server in {}", serverDirectory);

        port = options.getPort();
        startupWait = options.getStartupWait();
        shutdownWait = options.getShutdownWait();
        commandTimeout = options.getCommandTimeout();
        jdbcUrl = options.getRootJdbcUrl();

        try {
            unpackMySql(serverDirectory);
            initialize();
            mysqld = startMysqld();
        } catch (final Exception e) {
            close();
            throw e;
        }
    }

    public Connection getMySqlDatabase() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    public Path getServerDirectory() {
        return serverDirectory;
    }

    @Override
    public void close() {
        if (closed.getAndSet(true)) {
            return;
        }

        if (mysqld != null) {
            log.info("Shutting down mysqld. Waiting up to {} for shutdown to finish.", startupWait);
            mysqld.destroyForcibly();
            try {
                mysqld.waitFor(shutdownWait.toMillis(), MILLISECONDS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (mysqld.isAlive()) {
                log.error("mysqld is still running in {}", serverDirectory);
            }
        }

        try {
            deleteRecursively(serverDirectory, ALLOW_INSECURE);
        } catch (final IOException e) {
            log.warn("Failed to delete {}", serverDirectory);
        }

        executor.shutdownNow();
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("serverDirectory", serverDirectory)
                .add("port", port)
                .toString();
    }

    private void initialize() {
        system(mysqld(),
                "--no-defaults",
                "--initialize-insecure",
                "--innodb-flush-method=nosync",
                "--datadir", dataDir());
    }

    private Process startMysqld() throws IOException {
        final List<String> args = newArrayList(
                mysqld(),
                "--no-defaults",
                "--skip-ssl",
                "--skip-mysqlx",
                "--default-time-zone=+00:00",
                "--innodb-flush-method=nosync",
                "--innodb-flush-log-at-trx-commit=0",
                "--innodb-doublewrite=0",
                "--bind-address=localhost",
                "--lc_messages_dir", serverDirectory.resolve("share").toString(),
                "--socket", serverDirectory.resolve("mysql.sock").toString(),
                "--port", String.valueOf(port),
                "--datadir", dataDir());

        final Process process = new ProcessBuilder(args)
                .redirectErrorStream(true)
                .start();

        log.info("mysqld started on port {}. Waiting up to {} for startup to finish.", port, startupWait);

        startOutputProcessor(process.getInputStream());

        waitForServerStartup(process);

        return process;
    }

    private String mysqld() {
        return serverDirectory.resolve("bin").resolve("mysqld").toString();
    }

    private String dataDir() {
        return serverDirectory.resolve("data").toString();
    }

    private void waitForServerStartup(final Process process) throws IOException {
        Throwable lastCause = null;
        final long start = System.nanoTime();
        while (Duration.nanosSince(start).compareTo(startupWait) <= 0) {
            try {
                checkReady();
                log.info("mysqld startup finished");
                return;
            } catch (final SQLException e) {
                lastCause = e;
            }

            try {
                // check if process has exited
                final int value = process.exitValue();
                throw new IOException(format("mysqld exited with value %s, check stdout for more detail", value));
            } catch (final IllegalThreadStateException ignored) {
                // process is still running, loop and try again
            }

            try {
                Thread.sleep(10);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new IOException("mysqld failed to start after " + startupWait, lastCause);
    }

    private void checkReady() throws SQLException {
        try (final Connection connection = getMySqlDatabase();
             final Statement statement = connection.createStatement();
             final ResultSet resultSet = statement.executeQuery("SELECT 42")) {
            checkSql(resultSet.next(), "no rows in result set");
            checkSql(resultSet.getInt(1) == 42, "wrong result");
            checkSql(!resultSet.next(), "multiple rows in result set");
        }
    }

    private static void checkSql(final boolean expression, final String message) throws SQLException {
        if (!expression) {
            throw new SQLException(message);
        }
    }

    private void startOutputProcessor(final InputStream in) {
        executor.execute(() -> {
            try {
                ByteStreams.copy(in, System.out);
            }
            catch (final IOException ignored) {
            }
        });
    }

    private void system(final String... command) {
        try {
            new Command(command)
                    .setTimeLimit(commandTimeout)
                    .execute(executor);
        } catch (final CommandFailedException e) {
            throw new RuntimeException(e);
        }
    }

    private void unpackMySql(final Path target) throws IOException {
        final String archiveName = format("/mysql-%s.tar.gz", getPlatform());
        final URL url = EmbeddedMySql.class.getResource(archiveName);
        if (url == null) {
            throw new RuntimeException("archive not found: " + archiveName);
        }

        final File archive = createTempFile("mysql-", null);
        try {
            try (final InputStream in = url.openStream()) {
                copy(in, archive.toPath(), REPLACE_EXISTING);
            }
            system("tar", "-xzf", archive.getPath(), "-C", target.toString());
        } finally {
            if (!archive.delete()) {
                log.warn("Failed to delete file {}", archive);
            }
        }
    }

    private static String getPlatform() {
        return (OS_NAME.value() + "-" + OS_ARCH.value()).replace(' ', '_');
    }
}
