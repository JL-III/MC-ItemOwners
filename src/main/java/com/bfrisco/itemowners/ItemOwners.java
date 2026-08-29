package com.bfrisco.itemowners;

import com.bfrisco.itemowners.commands.Own;
import com.bfrisco.itemowners.commands.Disown;
import com.bfrisco.itemowners.commands.ItemHistory;
import com.bfrisco.itemowners.commands.ItemsOwned;
import com.bfrisco.itemowners.commands.RecoverItem;
import com.bfrisco.itemowners.constants.DependencyNames;
import com.bfrisco.itemowners.database.DataRetentionPurger;
import com.bfrisco.itemowners.database.ItemEventRepository;
import com.bfrisco.itemowners.database.ItemRecovery;
import com.bfrisco.itemowners.database.ItemRecoveryRepository;
import com.bfrisco.itemowners.database.ItemRepository;
import com.bfrisco.itemowners.listeners.OwnedItemListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ItemOwners extends JavaPlugin {
    private static ItemOwners plugin;
    private static Logger logger;
    private static FileConfiguration config;
    private static File dataFolder;
    private static final List<Material> VALID_ITEMS = new ArrayList<>();
    private Economy economy;

    public ItemOwners() throws SQLException {
        plugin = this;
        logger = getLogger();
        config = getConfig();
        dataFolder = getDataFolder();

        for (String item : getConfig().getStringList("items")) {
            try {
                VALID_ITEMS.add(Material.valueOf(item));
            } catch (Exception e) {
                ItemOwners.getBukkitLogger().log(Level.WARNING, String.format("Ignoring item '%s' - not a valid material name!", item));
            }
        }

        ItemRepository.init(loadFile("items.db"));
        ItemEventRepository.init(loadFile("events.db"));
        ItemRecoveryRepository.init(loadFile("recoveries.db"));
    }


    @Override
    public void onEnable() {
        Objects.requireNonNull(getCommand("own")).setExecutor(new Own(this));
        Objects.requireNonNull(getCommand("disown")).setExecutor(new Disown(this));
        Objects.requireNonNull(getCommand("itemhistory")).setExecutor(new ItemHistory(this));
        Objects.requireNonNull(getCommand("itemsowned")).setExecutor(new ItemsOwned(this));
        Objects.requireNonNull(getCommand("recoveritem")).setExecutor(new RecoverItem(this));

        saveDefaultConfig();
        boolean configMigrated = addConfigValueIfMissing("recovery.enabled", true);
        configMigrated |= addConfigValueIfMissing("recovery.fee", 1000D);
        configMigrated |= addConfigValueIfMissing("recovery.max-age-days", 14);
        this.getConfig().options().copyDefaults(true);
        if (configMigrated) {
            saveConfig();
            getLogger().info("Added the item recovery settings to config.yml.");
        }

        registerEvent(new OwnedItemListener(this));

        setupEconomy();
        if (getConfig().getBoolean("recovery.enabled") && getConfig().getDouble("recovery.fee") > 0D && economy == null) {
            getLogger().warning("Paid item recovery is unavailable until Vault and an economy provider are installed.");
        }

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                List<ItemRecovery> ambiguousRecoveries = ItemRecoveryRepository.findAmbiguous();
                if (!ambiguousRecoveries.isEmpty()) {
                    getLogger().severe(ambiguousRecoveries.size() + " item recovery record(s) require staff review. Do not retry them automatically.");
                    for (ItemRecovery recovery : ambiguousRecoveries) {
                        getLogger().severe(String.format(
                                "Recovery review: recoveryId=%d, itemId=%s, status=%s, claimant=%s, amount=%.2f, updated=%s",
                                recovery.getId(),
                                recovery.getItemId(),
                                recovery.getStatus(),
                                recovery.getClaimedBy() == null ? "none" : recovery.getClaimedBy(),
                                recovery.getQuotedFee(),
                                Instant.ofEpochMilli(recovery.getUpdatedAt())
                        ));
                    }
                }
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "Could not inspect interrupted item recoveries.", e);
            }
        });

        DataRetentionPurger.schedule(this);
    }

    public static void registerEvent(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }

    public static boolean isNotValid(ItemStack item) {
        if (item == null) return true;
        if (item.getAmount() != 1) return true;
        if (item.getMaxStackSize() != 1) return true;
        if (item.getItemMeta() == null) return true;
        return !VALID_ITEMS.contains(item.getType());
    }

    public static String getItemId(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;

        List<String> lore = item.getItemMeta().getLore();
        if (lore == null || lore.isEmpty()) return null;

        for (String line : lore) {
            if (line != null && line.startsWith(ChatColor.RED + "Item ID: ")) return line.split(": ")[1];
        }

        return null;
    }

    public static String getItemId(ItemStack item1, ItemStack item2) {
        String itemId = getItemId(item1);
        if (itemId != null) return itemId;
        return getItemId(item2);
    }

    public static boolean isOwned(ItemStack item) {
        return getItemId(item) != null;
    }

    private static File loadFile(String string) {
        File file = new File(dataFolder, string);
        return loadFile(file);
    }

    private static File loadFile(File file) {
        if (!file.exists()) {
            try {
                if (file.getParent() != null) {
                    file.getParentFile().mkdirs();
                }

                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return file;
    }

    public static Logger getBukkitLogger() {
        return logger;
    }

    public static FileConfiguration getBukkitConfig() {
        return config;
    }

    public Economy getEconomy() {
        if (economy == null) {
            setupEconomy();
        }
        return economy;
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin(DependencyNames.VAULT) == null) {
            return;
        }

        RegisteredServiceProvider<Economy> registration = getServer().getServicesManager().getRegistration(Economy.class);
        if (registration != null) {
            economy = registration.getProvider();
        }
    }

    private boolean addConfigValueIfMissing(String path, Object value) {
        if (getConfig().contains(path, true)) {
            return false;
        }
        getConfig().set(path, value);
        return true;
    }
}
