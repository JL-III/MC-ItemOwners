package com.bfrisco.itemowners.util;

import com.bfrisco.itemowners.database.Item;
import com.bfrisco.itemowners.database.ItemEvent;
import com.bfrisco.itemowners.database.ItemEventPage;
import com.bfrisco.itemowners.database.ItemPage;
import com.bfrisco.itemowners.exceptions.ChatMessageGeneratorException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ChatMessageGenerator {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("[M/d/yy hh:mm a]");
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    private static final String ITEM_ID_PREFIX = "Item ID: ";

    private ChatMessageGenerator() {
    }

    public static Component generateHeader(ItemEventPage page, String itemId, String itemData, String ownerName) throws IOException {
        return Component.text()
                .append(Component.text(" ---- ", NamedTextColor.YELLOW))
                .append(Component.text("ItemHistory", NamedTextColor.GOLD))
                .append(Component.text(" -- ", NamedTextColor.YELLOW))
                .append(Component.text("Page ", NamedTextColor.GOLD))
                .append(Component.text(page.getCurrentPage(), NamedTextColor.RED))
                .append(Component.text("/", NamedTextColor.GOLD))
                .append(Component.text(page.getTotalPages(), NamedTextColor.RED))
                .append(Component.text(" ----", NamedTextColor.YELLOW))
                .appendNewline()
                .append(Component.text("Item ID: ", NamedTextColor.GOLD))
                .append(generateItemIdLink(itemId, itemData, ownerName, false, false))
                .append(generatePagination(
                        "/itemhistory " + itemId,
                        page.getCurrentPage(),
                        page.getTotalPages()
                ))
                .appendNewline()
                .build();
    }

    public static Component generateHeader(ItemPage page, String playerName) {
        return Component.text()
                .append(Component.text(" ---- ", NamedTextColor.YELLOW))
                .append(Component.text("ItemsOwned", NamedTextColor.GOLD))
                .append(Component.text(" -- ", NamedTextColor.YELLOW))
                .append(Component.text("Page ", NamedTextColor.GOLD))
                .append(Component.text(page.getCurrentPage(), NamedTextColor.RED))
                .append(Component.text("/", NamedTextColor.GOLD))
                .append(Component.text(page.getTotalPages(), NamedTextColor.RED))
                .append(Component.text(" ----", NamedTextColor.YELLOW))
                .appendNewline()
                .append(Component.text("Player: ", NamedTextColor.GOLD))
                .append(Component.text(playerName, NamedTextColor.RED))
                .append(generatePagination(
                        "/itemsowned " + playerName,
                        page.getCurrentPage(),
                        page.getTotalPages()
                ))
                .appendNewline()
                .build();
    }

    public static Component generate(ItemPage page, String ownerName) throws ChatMessageGeneratorException, IOException {
        if (page.getTotalPages() == 0) {
            throw new ChatMessageGeneratorException("No items owned by that player.");
        }

        if (page.getCurrentPage() > page.getTotalPages()) {
            throw new ChatMessageGeneratorException("Unknown chapter.");
        }

        Component message = Component.empty();
        int count = 1;
        for (Item item : page.getResult()) {
            message = message
                    .append(Component.text(DATE_FORMAT.format(item.getDate()) + ": ", NamedTextColor.GRAY))
                    .append(generateItemIdLink(
                            item.getId(),
                            item.getData(),
                            ownerName,
                            item.getLastEventDestruction(),
                            true
                    ));

            if (count != page.getResult().size()) {
                message = message.appendNewline();
            }

            count++;
        }

        return message;
    }

    public static Component generate(ItemEventPage page) throws ChatMessageGeneratorException {
        if (page.getTotalPages() == 0) {
            throw new ChatMessageGeneratorException("No history found for that item ID.");
        }

        if (page.getCurrentPage() > page.getTotalPages()) {
            throw new ChatMessageGeneratorException("Unknown chapter.");
        }

        Component message = Component.empty();
        int count = 1;
        for (ItemEvent event : page.getResult()) {
            message = message
                    .append(Component.text(DATE_FORMAT.format(event.getDate()) + ": ", NamedTextColor.GRAY))
                    .append(Component.text(event.getItemEventType() + " ", NamedTextColor.WHITE));

            StringBuilder locationText = new StringBuilder();
            if (event.getWorld() != null) {
                locationText.append(event.getWorld()).append(", ");
            }

            locationText.append(event.getX()).append(", ").append(event.getY()).append(", ").append(event.getZ());

            message = message.append(Component.text("[loc]", NamedTextColor.GOLD)
                    .hoverEvent(Component.text(locationText.toString())));

            if (event.getPlayerId() != null) {
                String playerName = event.getPlayerId();
                try {
                    OfflinePlayer eventPlayer = Bukkit.getOfflinePlayer(UUID.fromString(event.getPlayerId()));
                    if (eventPlayer.getName() != null) {
                        playerName = eventPlayer.getName();
                    }
                } catch (IllegalArgumentException ignored) {
                    // Preserve the recorded value when a legacy player ID is not a UUID.
                }

                message = message.append(Component.text(" [pl]", NamedTextColor.GOLD)
                        .hoverEvent(Component.text(playerName)));
            }

            if (count != page.getResult().size()) {
                message = message.appendNewline();
            }

            count++;
        }

        return message;
    }

    public static Component generatePagination(String baseCommand, int currentPage, int totalPages) {
        if (totalPages <= 1 || currentPage < 1 || currentPage > totalPages) {
            return Component.empty();
        }

        return Component.text()
                .appendSpace()
                .append(generatePageLink("Previous", baseCommand, currentPage - 1, currentPage > 1))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(generatePageLink("Next", baseCommand, currentPage + 1, currentPage < totalPages))
                .build();
    }

    private static Component generatePageLink(String label, String baseCommand, int page, boolean enabled) {
        Component link = Component.text("[" + label + "]", enabled ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY);
        if (!enabled) {
            return link;
        }

        return link
                .hoverEvent(Component.text("Go to page " + page, NamedTextColor.GRAY))
                .clickEvent(ClickEvent.runCommand(baseCommand + " " + page));
    }

    private static Component generateItemIdLink(
            String itemId,
            String itemData,
            String ownerName,
            boolean destroyed,
            boolean openHistory
    ) throws IOException {
        ItemStack hoverItem = addMissingTrackingLore(ItemSerialization.fromBase64(itemData), itemId, ownerName);

        Component link;
        if (destroyed) {
            link = Component.text("[" + itemId + "]", NamedTextColor.GRAY, TextDecoration.STRIKETHROUGH);
        } else {
            link = Component.text()
                    .append(Component.text("[", NamedTextColor.WHITE))
                    .append(Component.text(itemId, NamedTextColor.YELLOW))
                    .append(Component.text("]", NamedTextColor.WHITE))
                    .build();
        }

        ClickEvent clickEvent = openHistory
                ? ClickEvent.runCommand("/itemhistory " + itemId)
                : ClickEvent.copyToClipboard(itemId);

        return link.hoverEvent(hoverItem.asHoverEvent()).clickEvent(clickEvent);
    }

    private static ItemStack addMissingTrackingLore(ItemStack storedItem, String itemId, String ownerName) {
        ItemMeta storedMeta = storedItem.getItemMeta();
        if (storedMeta == null) {
            return storedItem;
        }

        List<Component> storedLore = storedMeta.lore();
        if (storedLore != null && storedLore.stream()
                .map(PLAIN_TEXT::serialize)
                .anyMatch(line -> line.equals(ITEM_ID_PREFIX + itemId))) {
            return storedItem;
        }

        ItemStack hoverItem = storedItem.clone();
        ItemMeta hoverMeta = hoverItem.getItemMeta();
        if (hoverMeta == null) {
            return storedItem;
        }

        List<Component> hoverLore = hoverMeta.lore();
        List<Component> updatedLore = hoverLore == null ? new ArrayList<>() : new ArrayList<>(hoverLore);
        updatedLore.add(trackingLore("Owner: " + displayOwnerName(ownerName)));
        updatedLore.add(trackingLore(ITEM_ID_PREFIX + itemId));
        hoverMeta.lore(updatedLore);
        hoverItem.setItemMeta(hoverMeta);
        return hoverItem;
    }

    private static Component trackingLore(String text) {
        return Component.text(text, NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false);
    }

    private static String displayOwnerName(String ownerName) {
        return ownerName == null || ownerName.isBlank() ? "Unknown" : ownerName;
    }
}
