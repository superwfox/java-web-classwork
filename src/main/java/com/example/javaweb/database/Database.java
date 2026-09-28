package com.example.javaweb.database;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

public final class Database {
    private final String url;

    public Database() throws Exception {
        Class.forName("org.sqlite.JDBC");
        String configured = System.getenv("JAVA_WEB_DATA_DIR");
        if (configured == null || configured.isBlank()) configured = System.getProperty("javaweb.dataDir");
        Path directory = configured == null || configured.isBlank()
                ? Path.of(System.getProperty("catalina.base", System.getProperty("user.home")), "data", "java-web-classwork")
                : Path.of(configured);
        Files.createDirectories(directory);
        url = "jdbc:sqlite:" + directory.toAbsolutePath().resolve("messages.sqlite");
        try (Connection connection = open(); Statement statement = connection.createStatement();
             var stream = Database.class.getResourceAsStream("/db/schema.sql")) {
            if (stream == null) throw new IOException("Missing database schema");
            statement.execute("PRAGMA journal_mode=WAL");
            for (String sql : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                if (!sql.isBlank()) statement.execute(sql);
            }
        }
    }

    public Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("PRAGMA foreign_keys=ON");
        } catch (SQLException error) {
            connection.close();
            throw error;
        }
        return connection;
    }
}
