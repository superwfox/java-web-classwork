package com.example.javaweb.geo;

import com.example.javaweb.database.Database;
import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

public final class IpRegionRepository {
    private final Database database;
    public IpRegionRepository(Database database) { this.database = database; }

    public void importData() throws Exception {
        String external = System.getenv("JAVA_WEB_GEO_FILE");
        String version = external == null || external.isBlank() ? "dbip-country-lite-2026-09"
                : Path.of(external).toAbsolutePath() + ":" + Files.getLastModifiedTime(Path.of(external)).toMillis();
        try (Connection connection = database.open()) {
            try (PreparedStatement query = connection.prepareStatement("SELECT value FROM metadata WHERE key='geo_version'");
                 ResultSet result = query.executeQuery()) {
                if (result.next() && version.equals(result.getString(1))) return;
            }
            InputStream raw = external == null || external.isBlank()
                    ? getClass().getResourceAsStream("/geo/dbip-country-lite.csv.gz") : Files.newInputStream(Path.of(external));
            if (raw == null) throw new IOException("Missing IP country data");
            try (var reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8))) {
                connection.setAutoCommit(false);
                try (Statement clear = connection.createStatement();
                     PreparedStatement insert = connection.prepareStatement("INSERT INTO ip_regions VALUES(?,?,?,?)")) {
                    clear.executeUpdate("DELETE FROM ip_regions");
                    String line;
                    int count = 0;
                    while ((line = reader.readLine()) != null) {
                        String[] columns = line.replace("\"", "").split(",", -1);
                        if (columns.length != 3 || !columns[2].matches("[A-Z]{2}")) throw new IOException("Invalid IP country row");
                        byte[] start = packed(columns[0]);
                        byte[] end = packed(columns[1]);
                        if (start.length != end.length) throw new IOException("Mixed IP families");
                        insert.setInt(1, start.length);
                        insert.setString(2, HexFormat.of().formatHex(start));
                        insert.setString(3, HexFormat.of().formatHex(end));
                        insert.setString(4, columns[2]);
                        insert.addBatch();
                        if (++count % 1000 == 0) insert.executeBatch();
                    }
                    insert.executeBatch();
                    if (count == 0) throw new IOException("Empty IP country data");
                    try (PreparedStatement metadata = connection.prepareStatement("INSERT OR REPLACE INTO metadata VALUES('geo_version',?)")) {
                        metadata.setString(1, version);
                        metadata.executeUpdate();
                    }
                    connection.commit();
                } catch (Exception error) {
                    connection.rollback();
                    throw error;
                }
            }
        }
    }

    private static byte[] packed(String ip) throws IOException {
        byte[] bytes = literal(ip).getAddress();
        if (ip.contains(":") && bytes.length == 4) {
            byte[] mapped = new byte[16];
            mapped[10] = (byte) 255; mapped[11] = (byte) 255;
            System.arraycopy(bytes, 0, mapped, 12, 4);
            return mapped;
        }
        return bytes;
    }

    private static InetAddress literal(String ip) throws IOException {
        // Accept numeric literals only; never resolve arbitrary hostnames.
        if (ip == null || !ip.matches("[0-9a-fA-F:.]+")) throw new IOException("Invalid IP literal");
        return InetAddress.getByName(ip);
    }

    public String country(String ip) throws SQLException {
        InetAddress address;
        try { address = literal(ip.split("%", 2)[0]); } catch (IOException | RuntimeException ignored) { return ""; }
        byte[] bytes = address.getAddress();
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()
                || (bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc)
                || (bytes.length == 4 && (bytes[0] & 255) == 100 && (bytes[1] & 192) == 64)) return "";
        String value = HexFormat.of().formatHex(bytes);
        try (Connection connection = database.open(); PreparedStatement query = connection.prepareStatement(
                "SELECT ip_end,country FROM ip_regions WHERE family=? AND ip_start<=? ORDER BY ip_start DESC LIMIT 1")) {
            query.setInt(1, bytes.length);
            query.setString(2, value);
            try (ResultSet result = query.executeQuery()) {
                if (result.next() && result.getString(1).compareTo(value) >= 0) {
                    String country = result.getString(2);
                    return country.equals("ZZ") ? "" : country;
                }
            }
        }
        return "";
    }
}
