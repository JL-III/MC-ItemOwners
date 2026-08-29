package com.bfrisco.itemowners.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

public final class ItemSerialization {
    private static final String PAPER_NBT_PREFIX = "paper-v1:";

    private ItemSerialization() {
    }

    public static String toBase64(ItemStack item) throws IllegalStateException {
        try {
            return PAPER_NBT_PREFIX + Base64.getEncoder().encodeToString(item.serializeAsBytes());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to save item stacks.", e);
        }
    }

    public static ItemStack fromBase64(String data) throws IOException {
        if (data.startsWith(PAPER_NBT_PREFIX)) {
            try {
                byte[] bytes = Base64.getDecoder().decode(data.substring(PAPER_NBT_PREFIX.length()));
                return ItemStack.deserializeBytes(bytes);
            } catch (IllegalArgumentException e) {
                throw new IOException("Unable to decode Paper item data.", e);
            }
        }

        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (ClassNotFoundException | IOException e) {
            throw new IOException("Unable to decode class type.", e);
        }
    }
}
