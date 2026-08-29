package com.bfrisco.itemowners.database;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcPooledConnectionSource;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.table.TableUtils;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.Date;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

public final class ItemEventRepository {
    private static final String ITEM_DATE_ID_INDEX = "idx_events_item_date_id";
    private static Dao<ItemEvent, Long> repository;
    private static final long PAGE_SIZE = 7;
    private static List<String> DELETE_EVENT_TYPES;

    public static void init(File databaseFile) throws SQLException {
        JdbcPooledConnectionSource source = new JdbcPooledConnectionSource("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        TableUtils.createTableIfNotExists(source, ItemEvent.class);

        repository = DaoManager.createDao(source, ItemEvent.class);
        repository.executeRaw("CREATE INDEX IF NOT EXISTS " + ITEM_DATE_ID_INDEX
                + " ON events (itemId, date DESC, id DESC)");
        DELETE_EVENT_TYPES = Arrays.asList(
                ItemEventType.CLEARED_INVENTORY.name(),
                ItemEventType.BROKE.name(),
                ItemEventType.DISPOSED.name(),
                ItemEventType.DAMAGED.name(),
                ItemEventType.DESPAWNED.name(),
                ItemEventType.SALVAGED.name());
    }

    public static void save(ItemEventType type, String itemId, Player player) {
        ItemEvent event = new ItemEvent();
        event.setItemId(itemId);
        event.setDate(new Date(System.currentTimeMillis()));
        event.setItemEventType(type.name());
        event.setWorld(player.getWorld().getName());
        event.setX(player.getLocation().getBlockX());
        event.setY(player.getLocation().getBlockY());
        event.setZ(player.getLocation().getBlockZ());
        event.setPlayerId(player.getUniqueId().toString());
        save(event);
    }

    public static void save(ItemEventType type, String itemId, String playerId, Location location) {
        ItemEvent event = new ItemEvent();
        event.setItemId(itemId);
        event.setDate(new Date(System.currentTimeMillis()));
        event.setItemEventType(type.name());
        if (location != null) {
            if (location.getWorld() != null) {
                event.setWorld(location.getWorld().getName());
            }
            event.setX(location.getBlockX());
            event.setY(location.getBlockY());
            event.setZ(location.getBlockZ());
        }
        event.setPlayerId(playerId);
        save(event);
    }

    public static void save(ItemEventType type, String itemId, Location location) {
        ItemEvent event = new ItemEvent();
        event.setItemId(itemId);
        event.setDate(new Date(System.currentTimeMillis()));
        event.setItemEventType(type.name());
        if (location != null) {
            if (location.getWorld() != null) {
                event.setWorld(location.getWorld().getName());
            }
            event.setX(location.getBlockX());
            event.setY(location.getBlockY());
            event.setZ(location.getBlockZ());
        }
        save(event);
    }

    public static void save(
            ItemEventType type,
            String itemId,
            String playerId,
            String world,
            int x,
            int y,
            int z
    ) {
        ItemEvent event = new ItemEvent();
        event.setItemId(itemId);
        event.setDate(new Date(System.currentTimeMillis()));
        event.setItemEventType(type.name());
        event.setWorld(world);
        event.setX(x);
        event.setY(y);
        event.setZ(z);
        event.setPlayerId(playerId);
        save(event);
    }

    public static void save(ItemEvent e) {
        try {
            repository.create(e);

            if (DELETE_EVENT_TYPES.contains(e.getItemEventType())) {
                ItemRepository.lastEventDestruction(e.getItemId(), Boolean.TRUE);
            } else {
                ItemRepository.lastEventDestruction(e.getItemId(), Boolean.FALSE);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static ItemEventPage findByItemId(String itemId, int page) throws SQLException {
        ItemEventPage result = new ItemEventPage();
        result.setCurrentPage(page);

        long count = repository.queryBuilder()
                .where().eq("itemId", itemId).countOf();

        result.setTotalPages((int) Math.ceil(count / (double) PAGE_SIZE));

        List<ItemEvent> events = repository.queryBuilder()
                .offset((page - 1) * PAGE_SIZE)
                .limit(PAGE_SIZE)
                .orderBy("date", false)
                .orderBy("id", false)
                .where().eq("itemId", itemId).query();

        result.setResult(events);

        return result;
    }

    public static int deleteBefore(Date date) throws SQLException {
        DeleteBuilder<ItemEvent, Long> deleteBuilder = repository.deleteBuilder();
        deleteBuilder.where()
                .lt("date", date)
                .and()
                .raw("EXISTS ("
                        + "SELECT 1 FROM events AS newer "
                        + "WHERE newer.itemId = events.itemId "
                        + "AND (newer.date > events.date "
                        + "OR (newer.date = events.date AND newer.id > events.id))"
                        + ")");
        return deleteBuilder.delete();
    }

    public static int delete(String itemId) throws SQLException {
        DeleteBuilder<ItemEvent, Long> deleteBuilder = repository.deleteBuilder();
        deleteBuilder.where().eq("itemId", itemId);
        return deleteBuilder.delete();
    }
}
