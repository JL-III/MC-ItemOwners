package com.bfrisco.itemowners.database;

import com.bfrisco.itemowners.ItemOwners;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcPooledConnectionSource;
import com.j256.ormlite.table.TableUtils;

import java.io.File;
import java.sql.Date;
import java.sql.SQLException;
import java.util.List;

public final class ItemRepository {
    private static Dao<Item, String> repository;
    private final static long PAGE_SIZE = 7;

    public static void init(File databaseFile) throws SQLException {
        JdbcPooledConnectionSource source = new JdbcPooledConnectionSource("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        TableUtils.createTableIfNotExists(source, Item.class);

        repository = DaoManager.createDao(source, Item.class);
    }

    public static ItemPage findByPlayerId(String playerId, int page) throws SQLException {
        ItemPage result = new ItemPage();
        result.setCurrentPage(page);

        long count = repository.queryBuilder()
                .where().eq("ownerId", playerId).countOf();

        result.setTotalPages((int) Math.ceil(count / (double) PAGE_SIZE));

        List<Item> items = repository.queryBuilder()
                .offset((page - 1) * PAGE_SIZE)
                .limit(PAGE_SIZE)
                .orderBy("lastEventDestruction", true)
                .orderBy("date", false)
                .where().eq("ownerId", playerId).query();

        result.setResult(items);

        return result;
    }

    public static void save(String itemId, String ownerId, String data) throws SQLException {
        Item item = new Item();
        item.setId(itemId);
        item.setOwnerId(ownerId);
        item.setData(data);
        item.setDate(new Date(System.currentTimeMillis()));
        item.setLastEventDestruction(Boolean.FALSE);
        repository.create(item);
    }

    public static int delete(String itemId) throws SQLException {
        return repository.deleteById(itemId);
    }

    public static boolean exists(String itemId) throws SQLException {
        return repository.idExists(itemId);
    }

    public static Item findById(String itemId) throws SQLException {
        return repository.queryForId(itemId);
    }

    public static int updateData(String itemId, String data) throws SQLException {
        Item item = findById(itemId);
        if (item == null) {
            return 0;
        }

        item.setData(data);
        return repository.update(item);
    }

    public static void lastEventDestruction(String itemId, Boolean destructed) throws SQLException {
        Item item = findById(itemId);

        if (item == null) {
            ItemOwners.getBukkitLogger().warning("Event occurred with item " + itemId + ", but this item is not in the database.");
            return;
        }

        item.setLastEventDestruction(destructed);
        repository.update(item);
    }

}
