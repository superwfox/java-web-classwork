package com.example.javaweb.message;

import com.example.javaweb.database.Database;
import com.example.javaweb.geo.IpRegionRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.*;
import java.util.stream.Collectors;

@WebServlet(urlPatterns = "/api/messages", loadOnStartup = 1)
public final class MessageServlet extends HttpServlet {
    private MessageService service;
    @Override public void init() throws ServletException {
        try {
            Database database = new Database();
            IpRegionRepository regions = new IpRegionRepository(database);
            regions.importData();
            service = new MessageService(new MessageRepository(database), regions);
        } catch (Exception error) { throw new ServletException("留言数据库初始化失败", error); }
    }
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        prepare(response);
        try {
            long after = Long.parseLong(Optional.ofNullable(request.getParameter("after")).orElse("0"));
            List<Message> messages = service.list(after);
            response.getWriter().write("{\"items\":[" + messages.stream().map(MessageServlet::json).collect(Collectors.joining(","))
                    + "],\"next\":" + (messages.size() == 200 ? messages.get(messages.size()-1).id() : "null") + "}");
        } catch (IllegalArgumentException error) { error(response, 400, "无效分页参数"); }
        catch (Exception error) { log("读取留言失败", error); error(response, 500, "读取失败，请稍后重试"); }
    }
    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        prepare(response);
        // A custom header plus no CORS permission prevents cross-origin form submissions.
        if (!"JavaWeb".equals(request.getHeader("X-Requested-With")) || "cross-site".equals(request.getHeader("Sec-Fetch-Site"))) {
            error(response, 403, "请求来源不受支持"); return;
        }
        if (request.getContentType() == null || !request.getContentType().toLowerCase(Locale.ROOT).startsWith("application/x-www-form-urlencoded")) {
            error(response, 415, "不支持的请求格式"); return;
        }
        try {
            byte[] body = request.getInputStream().readNBytes(16385);
            if (body.length > 16384) { error(response, 413, "留言请求过大"); return; }
            Map<String,String> fields = new HashMap<>();
            for (String pair : new String(body, StandardCharsets.UTF_8).split("&")) {
                String[] parts = pair.split("=", 2);
                if (parts.length != 2) throw new IllegalArgumentException();
                String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                if (fields.put(key, URLDecoder.decode(parts[1], StandardCharsets.UTF_8)) != null) throw new IllegalArgumentException();
            }
            switch (required(fields, "action")) {
                case "create" -> {
                    Message message = service.create(required(fields, "requestId"), required(fields, "text"),
                            Double.parseDouble(required(fields, "latitude")), Double.parseDouble(required(fields, "longitude")), request.getRemoteAddr());
                    response.setStatus(201); response.getWriter().write(json(message));
                }
                case "update" -> response.getWriter().write(json(service.update(Long.parseLong(required(fields, "id")),
                        Long.parseLong(required(fields, "version")), required(fields, "text"))));
                case "delete" -> {
                    service.delete(Long.parseLong(required(fields, "id")), Long.parseLong(required(fields, "version")));
                    response.setStatus(204);
                }
                default -> error(response, 400, "未知操作");
            }
        } catch (MessageService.Conflict error) { error(response, 409, "留言已被他人修改或删除，请重新载入"); }
        catch (IllegalArgumentException error) { error(response, 400, "内容无效，请输入 1–1000 字留言及有效坐标"); }
        catch (Exception error) { log("保存留言失败", error); error(response, 500, "保存失败，请重试"); }
    }
    private static String required(Map<String,String> fields, String key) {
        String value = fields.get(key); if (value == null) throw new IllegalArgumentException(); return value;
    }
    private static void prepare(HttpServletResponse response) {
        response.setCharacterEncoding("UTF-8"); response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Content-Type-Options", "nosniff");
    }
    private static void error(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status); response.getWriter().write("{\"error\":" + quote(message) + "}");
    }
    private static String json(Message message) {
        return "{\"id\":" + message.id() + ",\"text\":" + quote(message.text()) + ",\"latitude\":" + message.latitude()
                + ",\"longitude\":" + message.longitude() + ",\"country\":" + quote(message.country())
                + ",\"createdAt\":" + quote(message.createdAt()) + ",\"updatedAt\":" + quote(message.updatedAt())
                + ",\"version\":" + message.version() + "}";
    }
    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> result.append("\\\""); case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n"); case '\r' -> result.append("\\r"); case '\t' -> result.append("\\t");
                default -> { if (c < 32 || Character.isSurrogate(c)) result.append(String.format("\\u%04x", (int)c)); else result.append(c); }
            }
        }
        return result.append('"').toString();
    }
}
