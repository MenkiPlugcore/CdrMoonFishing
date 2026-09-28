package id.menki.cdrmoonfishing.listener;

import id.menki.cdrmoonfishing.fishing.FishingManager;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class FishingListener implements Listener {
    private final FishingManager fishingManager;

    public FishingListener(FishingManager fishingManager) {
        this.fishingManager = fishingManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (fishingManager.isActive(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }

        switch (event.getState()) {
            case BITE -> fishingManager.prepareEncounter(event.getPlayer(), event.getHook().getLocation());
            case CAUGHT_FISH -> {
                event.setCancelled(true);
                event.setExpToDrop(0);
                if (event.getCaught() != null) {
                    event.getCaught().remove();
                }
                event.getHook().remove();
                fishingManager.startPrepared(event.getPlayer(), event.getHook().getLocation());
            }
            case FAILED_ATTEMPT, REEL_IN, IN_GROUND -> fishingManager.clearPrepared(event.getPlayer());
            default -> {
                // Keep vanilla behavior for casting, lured state, and caught entities.
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onReelInput(PlayerInteractEvent event) {
        if (!fishingManager.isActive(event.getPlayer())) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getPlayer().getInventory().getItemInMainHand().getType() != Material.FISHING_ROD) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        event.setCancelled(true);
        fishingManager.reelPulse(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        fishingManager.cancel(event.getPlayer(), false);
    }
}
