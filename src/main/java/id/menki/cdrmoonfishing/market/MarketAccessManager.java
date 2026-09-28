package id.menki.cdrmoonfishing.market;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class MarketAccessManager {
    private final JavaPlugin plugin;
    private final FishMarketManager market;
    private final File file;
    private YamlConfiguration yaml;
    private Location warp;
    private final Set<Integer> boundNpcIds = new LinkedHashSet<>();
    private final Set<UUID> pendingBind = new LinkedHashSet<>();
    private final Set<UUID> pendingUnbind = new LinkedHashSet<>();

    public MarketAccessManager(JavaPlugin plugin, FishMarketManager market) {
        this.plugin = plugin;
        this.market = market;
        this.file = new File(plugin.getDataFolder(), "market-access.yml");
        reload();
    }

    public void reload() {
        this.yaml = YamlConfiguration.loadConfiguration(file);
        this.boundNpcIds.clear();
        for (int id : yaml.getIntegerList("npc.ids")) {
            if (id >= 0) boundNpcIds.add(id);
        }
        this.warp = readWarp();
        pendingBind.clear();
        pendingUnbind.clear();
    }

    public boolean hasWarp() {
        return warp != null && warp.getWorld() != null;
    }

    public Location warp() {
        return warp == null ? null : warp.clone();
    }

    public void setWarp(Player player) {
        this.warp = player.getLocation().clone();
        writeWarp(warp);
        save();
        player.sendMessage(Component.text("Lokasi Pasar Ikan berhasil diatur di posisi kamu.", NamedTextColor.GREEN));
    }

    public void clearWarp(Player player) {
        this.warp = null;
        yaml.set("warp", null);
        save();
        player.sendMessage(Component.text("Lokasi Pasar Ikan berhasil dihapus.", NamedTextColor.YELLOW));
    }

    public boolean teleportToMarket(Player player) {
        if (!hasWarp()) {
            player.sendMessage(Component.text("Lokasi Pasar Ikan belum diatur.", NamedTextColor.RED));
            if (player.hasPermission("cdrmoonfishing.admin")) {
                player.sendMessage(Component.text("Berdiri dekat pedagang lalu gunakan /fishmarket setspawn.", NamedTextColor.GRAY));
            }
            return false;
        }

        player.closeInventory();
        Location target = warp.clone();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            player.teleport(target);
            player.sendTitle("§b§lPASAR IKAN", "§7Cari Pedagang Ikan lalu klik kanan untuk menjual.", 5, 35, 10);
            player.sendMessage(Component.text("Kamu tiba di Pasar Ikan. Klik kanan NPC pedagang untuk menjual tangkapan.", NamedTextColor.AQUA));
        });
        return true;
    }

    public void beginBind(Player player) {
        if (!citizensAvailable()) {
            player.sendMessage(Component.text("Citizens belum terpasang atau tidak aktif.", NamedTextColor.RED));
            return;
        }
        pendingUnbind.remove(player.getUniqueId());
        pendingBind.add(player.getUniqueId());
        player.sendMessage(Component.text("Mode bind aktif. Klik kanan NPC Citizens yang akan menjadi Pedagang Ikan.", NamedTextColor.AQUA));
    }

    public void beginUnbind(Player player) {
        if (!citizensAvailable()) {
            player.sendMessage(Component.text("Citizens belum terpasang atau tidak aktif.", NamedTextColor.RED));
            return;
        }
        pendingBind.remove(player.getUniqueId());
        pendingUnbind.add(player.getUniqueId());
        player.sendMessage(Component.text("Mode unbind aktif. Klik kanan NPC Pasar Ikan yang sudah terikat.", NamedTextColor.YELLOW));
    }

    public boolean handleEntityInteract(Player player, Entity entity) {
        Integer npcId = citizensNpcId(entity);
        UUID uuid = player.getUniqueId();

        if (pendingBind.remove(uuid)) {
            if (npcId == null) {
                player.sendMessage(Component.text("Entity itu bukan NPC Citizens.", NamedTextColor.RED));
                return true;
            }
            boundNpcIds.add(npcId);
            saveNpcIds();
            player.sendMessage(Component.text("NPC Citizens #" + npcId + " berhasil dijadikan Pedagang Ikan.", NamedTextColor.GREEN));
            return true;
        }

        if (pendingUnbind.remove(uuid)) {
            if (npcId == null || !boundNpcIds.remove(npcId)) {
                player.sendMessage(Component.text("NPC itu tidak terikat sebagai Pedagang Ikan.", NamedTextColor.RED));
                return true;
            }
            saveNpcIds();
            player.sendMessage(Component.text("NPC Citizens #" + npcId + " sudah dilepas dari Pasar Ikan.", NamedTextColor.YELLOW));
            return true;
        }

        if (npcId != null && boundNpcIds.contains(npcId)) {
            market.open(player);
            return true;
        }
        return false;
    }

    public List<Integer> boundNpcIds() {
        return List.copyOf(boundNpcIds);
    }

    public boolean citizensAvailable() {
        return plugin.getServer().getPluginManager().isPluginEnabled("Citizens");
    }

    private Integer citizensNpcId(Entity entity) {
        if (entity == null || !citizensAvailable()) return null;
        try {
            Class<?> api = Class.forName("net.citizensnpcs.api.CitizensAPI");
            Object registry = api.getMethod("getNPCRegistry").invoke(null);
            Method getNpc = registry.getClass().getMethod("getNPC", Entity.class);
            Object npc = getNpc.invoke(registry, entity);
            if (npc == null) return null;
            Object id = npc.getClass().getMethod("getId").invoke(npc);
            return id instanceof Number number ? number.intValue() : null;
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().warning("Could not resolve Citizens NPC identity: " + ex.getMessage());
            return null;
        }
    }

    private Location readWarp() {
        String worldName = yaml.getString("warp.world");
        if (worldName == null || worldName.isBlank()) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Fish Market warp world '" + worldName + "' is not loaded.");
            return null;
        }
        return new Location(
                world,
                yaml.getDouble("warp.x"),
                yaml.getDouble("warp.y"),
                yaml.getDouble("warp.z"),
                (float) yaml.getDouble("warp.yaw"),
                (float) yaml.getDouble("warp.pitch")
        );
    }

    private void writeWarp(Location location) {
        if (location == null || location.getWorld() == null) return;
        yaml.set("warp.world", location.getWorld().getName());
        yaml.set("warp.x", location.getX());
        yaml.set("warp.y", location.getY());
        yaml.set("warp.z", location.getZ());
        yaml.set("warp.yaw", location.getYaw());
        yaml.set("warp.pitch", location.getPitch());
    }

    private void saveNpcIds() {
        yaml.set("npc.ids", new ArrayList<>(boundNpcIds));
        save();
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save market-access.yml: " + ex.getMessage());
        }
    }
}
