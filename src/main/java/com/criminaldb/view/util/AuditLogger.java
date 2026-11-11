package com.criminaldb.util;

import com.criminaldb.config.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class AuditLogger {

    public static void log(String username, String action) {
        new Thread(() -> {
            String sql = "INSERT INTO activity_logs (user, action_text, timestamp) VALUES (?, ?, NOW())";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, (username != null && !username.trim().isEmpty()) ? username : "SYSTEM");
                ps.setString(2, action != null ? action : "System Event");
                ps.executeUpdate();
            } catch (Exception ex) {
                // Secondary fallback in case action column is used directly
                try (Connection conn2 = DBConnection.getConnection();
                     PreparedStatement ps2 = conn2.prepareStatement("INSERT INTO activity_logs (user, action, timestamp) VALUES (?, ?, NOW())")) {
                    ps2.setString(1, (username != null && !username.trim().isEmpty()) ? username : "SYSTEM");
                    ps2.setString(2, action != null ? action : "System Event");
                    ps2.executeUpdate();
                } catch (Exception ignored) {
                    System.err.println("Audit log silent catch: " + ex.getMessage());
                }
            }
        }).start();
    }
}