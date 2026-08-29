package com.bfrisco.itemowners.database;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "recoverable_losses")
public class ItemRecovery {
    @DatabaseField(generatedId = true)
    private long id;

    @DatabaseField(canBeNull = false, index = true)
    private String itemId;

    @DatabaseField(canBeNull = false, index = true)
    private String ownerId;

    @DatabaseField(canBeNull = false, unique = true)
    private String sourceEntityId;

    @DatabaseField(canBeNull = false, dataType = DataType.LONG_STRING)
    private String itemData;

    @DatabaseField(canBeNull = false)
    private long lostAt;

    @DatabaseField
    private String world;

    @DatabaseField
    private int x;

    @DatabaseField
    private int y;

    @DatabaseField
    private int z;

    @DatabaseField(canBeNull = false, index = true)
    private String status;

    @DatabaseField(unique = true)
    private String openItemId;

    @DatabaseField
    private String claimedBy;

    @DatabaseField
    private double quotedFee;

    @DatabaseField
    private long updatedAt;

    public long getId() {
        return id;
    }

    public String getItemId() {
        return itemId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getSourceEntityId() {
        return sourceEntityId;
    }

    public String getItemData() {
        return itemData;
    }

    public long getLostAt() {
        return lostAt;
    }

    public String getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public ItemRecoveryStatus getStatus() {
        return ItemRecoveryStatus.valueOf(status);
    }

    public String getOpenItemId() {
        return openItemId;
    }

    public String getClaimedBy() {
        return claimedBy;
    }

    public double getQuotedFee() {
        return quotedFee;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public void setSourceEntityId(String sourceEntityId) {
        this.sourceEntityId = sourceEntityId;
    }

    public void setItemData(String itemData) {
        this.itemData = itemData;
    }

    public void setLostAt(long lostAt) {
        this.lostAt = lostAt;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    public void setZ(int z) {
        this.z = z;
    }

    public void setStatus(ItemRecoveryStatus status) {
        this.status = status.name();
    }

    public void setOpenItemId(String openItemId) {
        this.openItemId = openItemId;
    }

    public void setClaimedBy(String claimedBy) {
        this.claimedBy = claimedBy;
    }

    public void setQuotedFee(double quotedFee) {
        this.quotedFee = quotedFee;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
