package com.bfrisco.itemowners.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemEventRepositoryTest {
    @Test
    void purgeRetainsNewestEventForEveryItem(@TempDir Path tempDirectory) throws Exception {
        Path database = initialize(tempDirectory);
        insertEvent(database, 1, "old-only", "2024-01-01");
        insertEvent(database, 2, "old-only", "2024-01-02");
        insertEvent(database, 3, "single-old", "2024-01-01");
        insertEvent(database, 4, "has-current", "2024-01-01");
        insertEvent(database, 5, "has-current", "2026-01-01");

        int deleted = ItemEventRepository.deleteBefore(Date.valueOf("2025-01-01"));

        assertEquals(2, deleted);
        assertEquals(List.of(2L), eventIds(database, "old-only"));
        assertEquals(List.of(3L), eventIds(database, "single-old"));
        assertEquals(List.of(5L), eventIds(database, "has-current"));
    }

    @Test
    void sameDateUsesNewestIdAsThePermanentEvent(@TempDir Path tempDirectory) throws Exception {
        Path database = initialize(tempDirectory);
        insertEvent(database, 10, "same-day", "2024-01-01");
        insertEvent(database, 11, "same-day", "2024-01-01");
        insertEvent(database, 12, "same-day", "2024-01-01");

        int deleted = ItemEventRepository.deleteBefore(Date.valueOf("2025-01-01"));

        assertEquals(2, deleted);
        assertEquals(List.of(12L), eventIds(database, "same-day"));
    }

    @Test
    void historyUsesIdToOrderEventsWithTheSameDate(@TempDir Path tempDirectory) throws Exception {
        Path database = initialize(tempDirectory);
        insertEvent(database, 20, "ordered", "2026-01-01");
        insertEvent(database, 21, "ordered", "2026-01-01");
        insertEvent(database, 22, "ordered", "2026-01-01");

        ItemEventPage page = ItemEventRepository.findByItemId("ordered", 1);

        assertEquals(List.of(22L, 21L, 20L), page.getResult().stream().map(ItemEvent::getId).toList());
    }

    @Test
    void initializationCreatesCompositeLookupIndex(@TempDir Path tempDirectory) throws Exception {
        Path database = initialize(tempDirectory);

        try (Connection connection = open(database);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT sql FROM sqlite_master WHERE type = 'index' AND name = 'idx_events_item_date_id'");
             ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            assertEquals(
                    "CREATE INDEX idx_events_item_date_id ON events (itemId, date DESC, id DESC)",
                    result.getString("sql")
            );
        }
    }

    private static Path initialize(Path tempDirectory) throws Exception {
        Path database = tempDirectory.resolve("events.db");
        ItemEventRepository.init(database.toFile());
        return database;
    }

    private static void insertEvent(Path database, long id, String itemId, String date) throws Exception {
        try (Connection connection = open(database);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO events (id, itemId, date, itemEventType, x, y, z) VALUES (?, ?, ?, ?, 0, 0, 0)")) {
            statement.setLong(1, id);
            statement.setString(2, itemId);
            statement.setDate(3, Date.valueOf(LocalDate.parse(date)));
            statement.setString(4, ItemEventType.TO_INVENTORY.name());
            statement.executeUpdate();
        }
    }

    private static List<Long> eventIds(Path database, String itemId) throws Exception {
        try (Connection connection = open(database);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id FROM events WHERE itemId = ? ORDER BY id")) {
            statement.setString(1, itemId);
            try (ResultSet result = statement.executeQuery()) {
                java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
                while (result.next()) {
                    ids.add(result.getLong("id"));
                }
                return ids;
            }
        }
    }

    private static Connection open(Path database) throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
    }
}
