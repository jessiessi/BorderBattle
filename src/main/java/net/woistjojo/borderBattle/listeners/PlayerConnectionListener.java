package net.woistjojo.borderBattle.listeners;

import net.woistjojo.borderBattle.services.BorderBattleService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {
    private final BorderBattleService borderBattleService;

    public PlayerConnectionListener(BorderBattleService borderBattleService) {
        this.borderBattleService = borderBattleService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        borderBattleService.handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        borderBattleService.handleQuit(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        borderBattleService.handleDeath(event.getPlayer());
    }
}
