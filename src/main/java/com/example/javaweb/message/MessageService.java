package com.example.javaweb.message;

import com.example.javaweb.geo.IpRegionRepository;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public final class MessageService {
    public static final class Conflict extends RuntimeException {}
    private final MessageRepository messages;
    private final IpRegionRepository regions;
    public MessageService(MessageRepository messages, IpRegionRepository regions) { this.messages = messages; this.regions = regions; }
    private String validText(String text) {
        if (text == null || text.isBlank() || text.length() > 1000 || text.indexOf('\0') >= 0)
            throw new IllegalArgumentException("请输入 1–1000 字的留言");
        return text.strip();
    }
    public List<Message> list(long after) throws SQLException {
        if (after < 0) throw new IllegalArgumentException("无效分页参数");
        return messages.list(after);
    }
    public Message create(String key, String text, double lat, double lon, String ip) throws SQLException {
        String requestId = UUID.fromString(key).toString();
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180)
            throw new IllegalArgumentException("无效经纬度");
        return messages.create(requestId, validText(text), lat, lon, ip, regions.country(ip));
    }
    public Message update(long id, long version, String text) throws SQLException {
        positive(id, version); return messages.update(id, version, validText(text));
    }
    public void delete(long id, long version) throws SQLException { positive(id, version); messages.delete(id, version); }
    private void positive(long id, long version) {
        if (id < 1 || version < 1) throw new IllegalArgumentException("无效留言版本");
    }
}
