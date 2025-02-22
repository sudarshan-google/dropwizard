package io.dropwizard.migrations;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.result.ResultIterable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
class DbMigrateCommandTest {

    private final DbMigrateCommand<TestMigrationConfiguration> migrateCommand = new DbMigrateCommand<>(
        TestMigrationConfiguration::getDataSource, TestMigrationConfiguration.class, "migrations.xml");
    private TestMigrationConfiguration conf;
    private String databaseUrl;

    @BeforeEach
    void setUp() {
        databaseUrl = MigrationTestSupport.getDatabaseUrl();
        conf = MigrationTestSupport.createConfiguration(databaseUrl);
    }

    @Test
    void testRun() throws Exception {
        migrateCommand.run(null, new Namespace(Collections.emptyMap()), conf);
        try (Handle handle = Jdbi.create(databaseUrl, "sa", "").open()) {
            final ResultIterable<Map<String, Object>> rows = handle.select("select * from persons").mapToMap();
            assertThat(rows).hasSize(1);
            assertThat(rows.first()).isEqualTo(
                    Map.of("id", 1,
                            "name", "Bill Smith",
                            "email", "bill@smith.me"));
        }
    }

    @Test
    void testRunFirstTwoMigration() throws Exception {
        migrateCommand.run(null, new Namespace(Collections.singletonMap("count", 2)), conf);
        try (Handle handle = Jdbi.create(databaseUrl, "sa", "").open()) {
            assertThat(handle.select("select * from persons").mapToMap()).isEmpty();
        }
    }

    @Test
    void testDryRun() throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        migrateCommand.setOutputStream(new PrintStream(baos));
        migrateCommand.run(null, new Namespace(Collections.singletonMap("dry-run", true)), conf);
        assertThat(baos.toString(UTF_8)).contains(String.format(
                "-- *********************************************************************%n" +
                "-- Update Database Script%n" +
                "-- *********************************************************************%n"));
    }

    @Test
    void testPrintHelp() throws Exception {
        final Subparser subparser = ArgumentParsers.newFor("db")
                .terminalWidthDetection(false)
                .build()
                .addSubparsers()
                .addParser(migrateCommand.getName())
                .description(migrateCommand.getDescription());
        migrateCommand.configure(subparser);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        subparser.printHelp(new PrintWriter(new OutputStreamWriter(baos, UTF_8), true));
        assertThat(baos.toString(UTF_8)).isEqualToNormalizingNewlines(
                        "usage: db migrate [-h] [--migrations MIGRATIONS-FILE] [--catalog CATALOG]\n" +
                            "          [--schema SCHEMA] [--analytics-enabled ANALYTICS-ENABLED] [-n]\n" +
                            "          [-c COUNT] [-i CONTEXTS] [file]\n" +
                            "\n" +
                            "Apply all pending change sets.\n" +
                            "\n" +
                            "positional arguments:\n" +
                            "  file                   application configuration file\n" +
                            "\n" +
                            "named arguments:\n" +
                            "  -h, --help             show this help message and exit\n" +
                            "  --migrations MIGRATIONS-FILE\n" +
                            "                         the file containing  the  Liquibase migrations for\n" +
                            "                         the application\n" +
                            "  --catalog CATALOG      Specify  the   database   catalog   (use  database\n" +
                            "                         default if omitted)\n" +
                            "  --schema SCHEMA        Specify the database schema  (use database default\n" +
                            "                         if omitted)\n" +
                            "  --analytics-enabled ANALYTICS-ENABLED\n" +
                            "                         This turns on analytics  gathering for that single\n" +
                            "                         occurrence of a command.\n" +
                            "  -n, --dry-run          output the DDL to stdout, don't run it\n" +
                            "  -c COUNT, --count COUNT\n" +
                            "                         only apply the next N change sets\n" +
                            "  -i CONTEXTS, --include CONTEXTS\n" +
                            "                         include change sets from the given context\n");
    }
}
