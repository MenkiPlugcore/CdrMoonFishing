package id.menki.cdrmoonfishing.supply;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

public final class SupplyAccessManager {
    private final JavaPlugin plugin;
    private final SupplyShopManager shop;
    private final File file;
    private YamlConfiguration yaml;
    private final Set<Integer> boundNpcIds = new LinkedHashSet<>();
    private final Set<UUID> pendingBind = new LinkedHashSet<>();
    private final Set<UUID> pendingUnbind = new LinkedHashSet<>();

    public SupplyAccessManager(JavaPlugin plugin, SupplyShopManager shop) {
        this.plugin = plugin;
        this.shop = shop;
        this.file = new File(plugin.getDataFolder(), "supply-access.yml");
        reload();
    }

    public void reload() {
        this.yaml = YamlConfiguration.loadConfiguration(file);
        this.boundNpcIds.clear();
        for (int id : yaml.getIntegerList("npc.ids")) if (id >= 0) boundNpcIds.add(id);
        pendingBind.clear();
        pendingUnbind.clear();
    }

    public void beginBind(Player player) {
        if (!citizensAvailable()) {
            player.sendMessage(Component.text("Citizens belum terpasang atau tidak aktif.", NamedTextColor.RED));
            return;
        }
        pendingUnbind.remove(player.getUniqueId());
        pendingBind.add(player.getUniqueId());
        player.sendMessage(Component.text("Mode bind Fishing Supply aktif. Klik kanan NPC Citizens yang akan dipakai.", NamedTextColor.AQUA));
    }

    public void beginUnbind(Player player) {
        if (!citizensAvailable()) {
            player.sendMessage(Component.text("Citizens belum terpasang atau tidak aktif.", NamedTextColor.RED));
            return;
        }
        pendingBind.remove(player.getUniqueId());
        pendingUnbind.add(player.getUniqueId());
        player.sendMessage(Component.text("Mode unbind Fishing Supply aktif. Klik kanan NPC yang ingin dilepas.", NamedTextColor.YELLOW));
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
            player.sendMessage(Component.text("NPC Citizens #" + npcId + " berhasil dijadikan Fishing Supply.", NamedTextColor.GREEN));
            return true;
        }

        if (pendingUnbind.remove(uuid)) {
            if (npcId == null || !boundNpcIds.remove(npcId)) {
                player.sendMessage(Component.text("NPC itu tidak terikat sebagai Fishing Supply.", NamedTextColor.RED));
                return true;
            }
            saveNpcIds();
            player.sendMessage(Component.text("NPC Citizens #" + npcId + " sudah dilepas dari Fishing Supply.", NamedTextColor.YELLOW));
            return true;
        }

        if (npcId != null && boundNpcIds.contains(npcId)) {
            shop.open(player);
            return true;
        }
        return false;
    }

    public boolean citizensAvailable() {
        return plugin.getServer().getPluginManager().isPluginEnabled("Citizens");
    }

    public List<Integer> boundNpcIds() {
        return List.copyOf(boundNpcIds);
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
            plugin.getLogger().warning("Could not resolve Citizens NPC identity for Fishing Supply: " + ex.getMessage());
            return null;
        }
    }

    private void saveNpcIds() {
        yaml.set("npc.ids", new ArrayList<>(boundNpcIds));
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save supply-access.yml: " + ex.getMessage());
        }
    }
}
