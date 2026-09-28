package com.example.javaweb.message;

import com.example.javaweb.database.Database;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class MessageRepository {
    private final Database database;
    public MessageRepository(Database database) { this.database = database; }
    private static Message read(ResultSet row) throws SQLException {
        return new Message(row.getLong("id"), row.getString("text"), row.getDouble("latitude"), row.getDouble("longitude"),
                row.getString("country"), row.getString("created_at"), row.getString("updated_at"), row.getLong("version"));
    }
    public List<Message> list(long after) throws SQLException {
        try (Connection connection = database.open(); PreparedStatement query = connection.prepareStatement(
                "SELECT * FROM messages WHERE id>? ORDER BY id LIMIT 200")) {
            query.setLong(1, after);
            try (ResultSet rows = query.executeQuery()) {
                List<Message> messages = new ArrayList<>();
                while (rows.next()) messages.add(read(rows));
                return messages;
            }
        }
    }
    public Message create(String requestId, String text, double latitude, double longitude, String ip, String country) throws SQLException {
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO messages(request_id,text,latitude,longitude,source_ip,country,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(request_id) DO NOTHING")) {
                    String now = Instant.now().toString();
                    insert.setString(1, requestId); insert.setString(2, text);
                    insert.setDouble(3, latitude); insert.setDouble(4, longitude);
                    insert.setString(5, ip); insert.setString(6, country);
                    insert.setString(7, now); insert.setString(8, now); insert.executeUpdate();
                }
                try (PreparedStatement query = connection.prepareStatement("SELECT * FROM messages WHERE request_id=?")) {
                    query.setString(1, requestId);
                    try (ResultSet row = query.executeQuery()) {
                        if (!row.next()) throw new SQLException("Insert returned no message");
                        Message message = read(row);
                        connection.commit();
                        return message;
                    }
                }
            } catch (SQLException error) { connection.rollback(); throw error; }
        }
    }
    public Message update(long id, long version, String text) throws SQLException {
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE messages SET text=?, updated_at=?, version=version+1 WHERE id=? AND version=?")) {
                    update.setString(1, text); update.setString(2, Instant.now().toString());
                    update.setLong(3, id); update.setLong(4, version);
                    if (update.executeUpdate() == 0) throw new MessageService.Conflict();
                }
                try (PreparedStatement query = connection.prepareStatement("SELECT * FROM messages WHERE id=?")) {
                    query.setLong(1, id);
                    try (ResultSet row = query.executeQuery()) {
                        if (!row.next()) throw new MessageService.Conflict();
                        Message message = read(row); connection.commit(); return message;
                    }
                }
            } catch (SQLException | RuntimeException error) { connection.rollback(); throw error; }
        }
    }
    public void delete(long id, long version) throws SQLException {
        try (Connection connection = database.open(); PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM messages WHERE id=? AND version=?")) {
            delete.setLong(1, id); delete.setLong(2, version);
            if (delete.executeUpdate() == 0) throw new MessageService.Conflict();
        }
    }
}
