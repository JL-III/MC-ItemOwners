package com.bfrisco.itemowners.database;

import com.bfrisco.itemowners.ItemOwners;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcPooledConnectionSource;
import com.j256.ormlite.stmt.UpdateBuilder;
import com.j256.ormlite.table.TableUtils;

import java.io.File;
import java.sql.SQLException;
import java.util.List;

public final class ItemRecoveryRepository {
    private static Dao<ItemRecovery, Long> repository;

    private ItemRecoveryRepository() {
    }

    public static void init(File databaseFile) throws SQLException {
        JdbcPooledConnectionSource source = new JdbcPooledConnectionSource("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        TableUtils.createTableIfNotExists(source, ItemRecovery.class);
        repository = DaoManager.createDao(source, ItemRecovery.class);
    }

    public static synchronized boolean recordLoss(
            String itemId,
            String ownerId,
            String sourceEntityId,
            String itemData,
            long lostAt,
            String world,
            int x,
            int y,
            int z
    ) throws SQLException {
        if (findBySourceEntityId(sourceEntityId) != null) {
            return false;
        }

        ItemRecovery openRecovery = findOpenByItemId(itemId);
        if (openRecovery != null) {
            ItemRecoveryStatus status = openRecovery.getStatus();
            if (canConflict(status) && !markOpenItemConflicted(itemId)) {
                throw new SQLException("Could not lock conflicting recovery for item " + itemId);
            }
            if (ItemOwners.getBukkitLogger() != null) {
                if (canConflict(status)) {
                    ItemOwners.getBukkitLogger().warning(
                            "A second despawn was observed for open recovery item " + itemId + ". Recovery has been disabled for that item."
                    );
                } else {
                    ItemOwners.getBukkitLogger().warning(
                            "A second despawn was observed for committed recovery item " + itemId
                                    + " while it was " + status + ". The duplicate loss was rejected."
                    );
                }
            }
            return false;
        }

        ItemRecovery recovery = new ItemRecovery();
        recovery.setItemId(itemId);
        recovery.setOwnerId(ownerId);
        recovery.setSourceEntityId(sourceEntityId);
        recovery.setItemData(itemData);
        recovery.setLostAt(lostAt);
        recovery.setWorld(world);
        recovery.setX(x);
        recovery.setY(y);
        recovery.setZ(z);
        recovery.setStatus(ItemRecoveryStatus.AVAILABLE);
        recovery.setOpenItemId(itemId);
        recovery.setUpdatedAt(System.currentTimeMillis());
        repository.create(recovery);
        return true;
    }

    public static ItemRecovery findLatestByItemId(String itemId) throws SQLException {
        List<ItemRecovery> recoveries = repository.queryBuilder()
                .orderBy("id", false)
                .limit(1L)
                .where().eq("itemId", itemId)
                .query();
        return recoveries.isEmpty() ? null : recoveries.get(0);
    }

    public static synchronized boolean reserve(long recoveryId, String playerId, double fee) throws SQLException {
        UpdateBuilder<ItemRecovery, Long> update = repository.updateBuilder();
        update.updateColumnValue("status", ItemRecoveryStatus.RESERVED.name());
        update.updateColumnValue("claimedBy", playerId);
        update.updateColumnValue("quotedFee", fee);
        update.updateColumnValue("updatedAt", System.currentTimeMillis());
        update.where()
                .eq("id", recoveryId)
                .and().eq("ownerId", playerId)
                .and().eq("status", ItemRecoveryStatus.AVAILABLE.name());
        return update.update() == 1;
    }

    public static synchronized boolean markCharged(long recoveryId, double chargedAmount) throws SQLException {
        UpdateBuilder<ItemRecovery, Long> update = repository.updateBuilder();
        update.updateColumnValue("status", ItemRecoveryStatus.CHARGED.name());
        update.updateColumnValue("quotedFee", chargedAmount);
        update.updateColumnValue("updatedAt", System.currentTimeMillis());
        update.where()
                .eq("id", recoveryId)
                .and().eq("status", ItemRecoveryStatus.WITHDRAWING.name());
        return update.update() == 1;
    }

    public static synchronized boolean markChargeIntent(long recoveryId) throws SQLException {
        return transition(recoveryId, ItemRecoveryStatus.RESERVED, ItemRecoveryStatus.CHARGE_INTENT, false);
    }

    public static synchronized boolean beginWithdrawal(long recoveryId) throws SQLException {
        return transition(recoveryId, ItemRecoveryStatus.CHARGE_INTENT, ItemRecoveryStatus.WITHDRAWING, false);
    }

    public static synchronized boolean markIssued(long recoveryId) throws SQLException {
        return transition(recoveryId, ItemRecoveryStatus.CHARGED, ItemRecoveryStatus.ISSUED, false);
    }

    public static synchronized boolean beginDelivery(long recoveryId) throws SQLException {
        return transition(recoveryId, ItemRecoveryStatus.ISSUED, ItemRecoveryStatus.DELIVERING, false);
    }

    public static synchronized boolean beginRefundBeforeDelivery(long recoveryId) throws SQLException {
        UpdateBuilder<ItemRecovery, Long> update = repository.updateBuilder();
        update.updateColumnValue("status", ItemRecoveryStatus.REFUND_INTENT.name());
        update.updateColumnValue("updatedAt", System.currentTimeMillis());
        update.where()
                .eq("id", recoveryId)
                .and().in(
                        "status",
                        ItemRecoveryStatus.WITHDRAWING.name(),
                        ItemRecoveryStatus.CHARGED.name(),
                        ItemRecoveryStatus.ISSUED.name()
                );
        return update.update() == 1;
    }

    public static synchronized boolean beginDeliveryRefund(long recoveryId) throws SQLException {
        return transition(recoveryId, ItemRecoveryStatus.DELIVERING, ItemRecoveryStatus.REFUND_INTENT, false);
    }

    public static synchronized boolean finishRefund(long recoveryId) throws SQLException {
        return reopen(recoveryId, ItemRecoveryStatus.REFUND_INTENT);
    }

    public static synchronized boolean complete(long recoveryId) throws SQLException {
        return transition(recoveryId, ItemRecoveryStatus.DELIVERING, ItemRecoveryStatus.COMPLETED, true);
    }

    public static synchronized boolean release(long recoveryId) throws SQLException {
        return reopen(recoveryId, ItemRecoveryStatus.RESERVED);
    }

    public static synchronized boolean cancelBeforeIssue(long recoveryId) throws SQLException {
        UpdateBuilder<ItemRecovery, Long> update = repository.updateBuilder();
        update.updateColumnValue("status", ItemRecoveryStatus.AVAILABLE.name());
        update.updateColumnValue("claimedBy", null);
        update.updateColumnValue("quotedFee", 0D);
        update.updateColumnValue("updatedAt", System.currentTimeMillis());
        update.where()
                .eq("id", recoveryId)
                .and().in(
                        "status",
                        ItemRecoveryStatus.RESERVED.name(),
                        ItemRecoveryStatus.CHARGE_INTENT.name(),
                        ItemRecoveryStatus.WITHDRAWING.name(),
                        ItemRecoveryStatus.CHARGED.name()
                );
        return update.update() == 1;
    }

    public static synchronized boolean invalidateAvailable(long recoveryId) throws SQLException {
        return transition(recoveryId, ItemRecoveryStatus.AVAILABLE, ItemRecoveryStatus.INVALIDATED, true);
    }

    public static synchronized boolean invalidateOpenBeforePayment(String itemId) throws SQLException {
        UpdateBuilder<ItemRecovery, Long> update = repository.updateBuilder();
        update.updateColumnValue("status", ItemRecoveryStatus.INVALIDATED.name());
        update.updateColumnValue("openItemId", null);
        update.updateColumnValue("updatedAt", System.currentTimeMillis());
        update.where()
                .eq("openItemId", itemId)
                .and().in(
                        "status",
                        ItemRecoveryStatus.AVAILABLE.name(),
                        ItemRecoveryStatus.RESERVED.name(),
                        ItemRecoveryStatus.CHARGE_INTENT.name()
                );
        return update.update() == 1;
    }

    private static boolean reopen(long recoveryId, ItemRecoveryStatus from) throws SQLException {
        UpdateBuilder<ItemRecovery, Long> update = repository.updateBuilder();
        update.updateColumnValue("status", ItemRecoveryStatus.AVAILABLE.name());
        update.updateColumnValue("claimedBy", null);
        update.updateColumnValue("quotedFee", 0D);
        update.updateColumnValue("updatedAt", System.currentTimeMillis());
        update.where()
                .eq("id", recoveryId)
                .and().eq("status", from.name());
        return update.update() == 1;
    }

    public static long countAmbiguous() throws SQLException {
        return repository.queryBuilder().where().in(
                "status",
                ItemRecoveryStatus.RESERVED.name(),
                ItemRecoveryStatus.CHARGE_INTENT.name(),
                ItemRecoveryStatus.WITHDRAWING.name(),
                ItemRecoveryStatus.CHARGED.name(),
                ItemRecoveryStatus.ISSUED.name(),
                ItemRecoveryStatus.DELIVERING.name(),
                ItemRecoveryStatus.REFUND_INTENT.name(),
                ItemRecoveryStatus.CONFLICTED.name()
        ).countOf();
    }

    public static synchronized List<ItemRecovery> findAmbiguous() throws SQLException {
        return repository.queryBuilder()
                .orderBy("updatedAt", true)
                .where().in(
                        "status",
                        ItemRecoveryStatus.RESERVED.name(),
                        ItemRecoveryStatus.CHARGE_INTENT.name(),
                        ItemRecoveryStatus.WITHDRAWING.name(),
                        ItemRecoveryStatus.CHARGED.name(),
                        ItemRecoveryStatus.ISSUED.name(),
                        ItemRecoveryStatus.DELIVERING.name(),
                        ItemRecoveryStatus.REFUND_INTENT.name(),
                        ItemRecoveryStatus.CONFLICTED.name()
                ).query();
    }

    private static ItemRecovery findBySourceEntityId(String sourceEntityId) throws SQLException {
        List<ItemRecovery> recoveries = repository.queryBuilder()
                .limit(1L)
                .where().eq("sourceEntityId", sourceEntityId)
                .query();
        return recoveries.isEmpty() ? null : recoveries.get(0);
    }

    private static ItemRecovery findOpenByItemId(String itemId) throws SQLException {
        List<ItemRecovery> recoveries = repository.queryBuilder()
                .limit(1L)
                .where().eq("openItemId", itemId)
                .query();
        return recoveries.isEmpty() ? null : recoveries.get(0);
    }

    private static boolean markOpenItemConflicted(String itemId) throws SQLException {
        UpdateBuilder<ItemRecovery, Long> update = repository.updateBuilder();
        update.updateColumnValue("status", ItemRecoveryStatus.CONFLICTED.name());
        update.updateColumnValue("updatedAt", System.currentTimeMillis());
        update.where()
                .eq("openItemId", itemId)
                .and().in(
                        "status",
                        ItemRecoveryStatus.AVAILABLE.name(),
                        ItemRecoveryStatus.RESERVED.name(),
                        ItemRecoveryStatus.CHARGE_INTENT.name()
                );
        return update.update() == 1;
    }

    private static boolean canConflict(ItemRecoveryStatus status) {
        return status == ItemRecoveryStatus.AVAILABLE
                || status == ItemRecoveryStatus.RESERVED
                || status == ItemRecoveryStatus.CHARGE_INTENT;
    }

    private static boolean transition(
            long recoveryId,
            ItemRecoveryStatus from,
            ItemRecoveryStatus to,
            boolean close
    ) throws SQLException {
        UpdateBuilder<ItemRecovery, Long> update = repository.updateBuilder();
        update.updateColumnValue("status", to.name());
        update.updateColumnValue("updatedAt", System.currentTimeMillis());
        if (close) {
            update.updateColumnValue("openItemId", null);
        }
        update.where()
                .eq("id", recoveryId)
                .and().eq("status", from.name());
        return update.update() == 1;
    }
}
