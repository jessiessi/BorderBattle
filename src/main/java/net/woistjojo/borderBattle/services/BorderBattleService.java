package net.woistjojo.borderBattle.services;

import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.woistjojo.borderBattle.BorderBattle;
import net.woistjojo.borderBattle.models.PlayerData;
import net.woistjojo.borderBattle.models.RunningPhase;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BorderBattleService {
    private final BorderBattle plugin;
    private final BossBar playerCountBar;
    private final ChallengeTimerService challengeTimerService;
    private File eliminatedPlayersFile;
    private YamlConfiguration eliminatedPlayersConfig;

    @Getter
    @Setter
    private RunningPhase runningPhase;

    @Getter
    private final ConcurrentHashMap<UUID, PlayerData> playerData = new ConcurrentHashMap<>();

    public BorderBattleService(BorderBattle plugin) {
        this.plugin = plugin;
        this.playerCountBar = Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SOLID);
        this.playerCountBar.setVisible(true);
        this.challengeTimerService = new ChallengeTimerService(plugin);
        this.runningPhase = RunningPhase.NONE;
    }

    public void initialize() {
        loadEliminatedPlayers();
        runningPhase = RunningPhase.JOINPHASE;
        setBorder(plugin.getPluginConfig().getWaitingBorderSize(), 0);

        for (Player player : Bukkit.getOnlinePlayers()) {
            handleJoin(player);
        }
    }

    public void shutdown() {
        challengeTimerService.stop();
        playerCountBar.removeAll();
    }

    public void handleJoin(Player player) {
        PlayerData data = getOrCreatePlayerData(player);

        if (data.isEliminated()) {
            player.kick(getEliminationMessage(player, data.getTotalPlayers(), data.getPlacement()));
            return;
        }

        playerCountBar.addPlayer(player);

        if (data.isModerator()) {
            player.setGameMode(GameMode.SPECTATOR);
        } else {
            player.setGameMode(GameMode.SURVIVAL);
            if (runningPhase != RunningPhase.RUNNING) {
                teleportIntoBorder(player);
            }
        }

        updatePlayerCountBar();
        challengeTimerService.showTo(player);
    }

    public void handleQuit(Player player) {
        playerCountBar.removePlayer(player);
        updatePlayerCountBar();
    }

    public void handleDeath(Player player) {
        PlayerData data = getOrCreatePlayerData(player);
        if (data.isModerator() || data.isEliminated()) {
            return;
        }

        int totalPlayers = getActiveTotalPlayerCount();
        int placement = getAlivePlayerCount();

        eliminatePlayer(player, totalPlayers, placement);
        Bukkit.getScheduler().runTask(plugin, () -> player.kick(getEliminationMessage(player, totalPlayers, placement)));
    }

    public boolean toggleModerator(Player player) {
        PlayerData data = getOrCreatePlayerData(player);
        boolean nowModerator = !data.isModerator();

        data.setModerator(nowModerator);
        player.setGameMode(nowModerator ? GameMode.SPECTATOR : GameMode.SURVIVAL);

        updatePlayerCountBar();
        return nowModerator;
    }

    public void startChallenge() {
        runningPhase = RunningPhase.RUNNING;
        setBorder(plugin.getPluginConfig().getChallengeBorderSize(), 0);
        challengeTimerService.start();

        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = getOrCreatePlayerData(player);
            player.setGameMode(data.isModerator() ? GameMode.SPECTATOR : GameMode.SURVIVAL);
        }

        updatePlayerCountBar();
    }

    public void stopChallenge() {
        runningPhase = RunningPhase.JOINPHASE;
        challengeTimerService.stop();
        clearEliminatedPlayers();
        setBorder(plugin.getPluginConfig().getWaitingBorderSize(), 0);

        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = getOrCreatePlayerData(player);
            if (data.isModerator()) {
                player.setGameMode(GameMode.SPECTATOR);
            } else {
                player.setGameMode(GameMode.SURVIVAL);
                teleportIntoBorder(player);
            }
        }

        updatePlayerCountBar();
    }

    public void moveBorder(double blocks, long seconds) {
        setBorder(blocks, seconds);
    }

    public void stopBorder() {
        WorldBorder border = getMainBorder();
        setBorder(border.getSize(), 0);
    }

    public boolean isChallengeRunning() {
        return runningPhase == RunningPhase.RUNNING;
    }

    private PlayerData getOrCreatePlayerData(Player player) {
        return playerData.compute(player.getUniqueId(), (uuid, current) -> {
            if (current == null) {
                return new PlayerData(uuid, player.getName(), false, false, 0, 0);
            }

            current.setName(player.getName());
            return current;
        });
    }

    private int getAlivePlayerCount() {
        int count = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = getOrCreatePlayerData(player);
            if (!data.isModerator() && !data.isEliminated()) {
                count++;
            }
        }

        return count;
    }

    private int getEliminatedPlayerCount() {
        int count = 0;

        for (PlayerData data : playerData.values()) {
            if (data.isEliminated()) {
                count++;
            }
        }

        return count;
    }

    private int getActiveTotalPlayerCount() {
        return getAlivePlayerCount() + getEliminatedPlayerCount();
    }

    private void updatePlayerCountBar() {
        int eliminated = getEliminatedPlayerCount();
        int totalPlayers = getActiveTotalPlayerCount();
        int alivePlayers = getAlivePlayerCount();

        playerCountBar.setTitle("Ausgeschieden: " + eliminated + " / " + totalPlayers);
        playerCountBar.setProgress(totalPlayers == 0 ? 0.0 : (double) alivePlayers / totalPlayers);
    }

    private void eliminatePlayer(Player player, int totalPlayers, int placement) {
        PlayerData data = getOrCreatePlayerData(player);

        data.setEliminated(true);
        data.setTotalPlayers(totalPlayers);
        data.setPlacement(placement);

        String path = player.getUniqueId().toString();
        eliminatedPlayersConfig.set(path + ".name", player.getName());
        eliminatedPlayersConfig.set(path + ".totalPlayers", totalPlayers);
        eliminatedPlayersConfig.set(path + ".placement", placement);
        saveEliminatedPlayers();
        updatePlayerCountBar();
    }

    private Component getEliminationMessage(Player player, int totalPlayers, int placement) {
        return Component.text("Du bist leider gestorben", NamedTextColor.RED)
                .append(Component.newline())
                .append(Component.text("Vielen Dank fuers mitmachen <3", NamedTextColor.BLUE))
                .append(Component.newline())
                .append(Component.text("Du " + player.getName() + " bist von " + totalPlayers + " auf Platz " + placement + ".", NamedTextColor.GRAY));
    }

    private void loadEliminatedPlayers() {
        eliminatedPlayersFile = new File(plugin.getDataFolder(), "eliminated-players.yml");
        eliminatedPlayersConfig = YamlConfiguration.loadConfiguration(eliminatedPlayersFile);

        for (String uuidText : eliminatedPlayersConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidText);
                String name = eliminatedPlayersConfig.getString(uuidText + ".name", uuidText);
                int totalPlayers = eliminatedPlayersConfig.getInt(uuidText + ".totalPlayers", 0);
                int placement = eliminatedPlayersConfig.getInt(uuidText + ".placement", totalPlayers);

                playerData.put(uuid, new PlayerData(uuid, name, false, true, totalPlayers, placement));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ungueltige UUID in eliminated-players.yml: " + uuidText);
            }
        }
    }

    private void saveEliminatedPlayers() {
        try {
            File parent = eliminatedPlayersFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().severe("Plugin-Ordner konnte nicht erstellt werden: " + parent);
                return;
            }

            eliminatedPlayersConfig.save(eliminatedPlayersFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("eliminated-players.yml konnte nicht gespeichert werden: " + exception.getMessage());
        }
    }

    private void clearEliminatedPlayers() {
        for (PlayerData data : playerData.values()) {
            data.setEliminated(false);
            data.setTotalPlayers(0);
            data.setPlacement(0);
        }

        for (String uuid : new HashSet<>(eliminatedPlayersConfig.getKeys(false))) {
            eliminatedPlayersConfig.set(uuid, null);
        }

        saveEliminatedPlayers();
    }

    private void setBorder(double size, long seconds) {
        WorldBorder border = getMainBorder();
        Location spawn = border.getWorld().getSpawnLocation();

        border.setCenter(spawn);
        if (seconds == 0) {
            border.setSize(size);
        } else {
            border.changeSize(size, seconds);
        }
    }

    private WorldBorder getMainBorder() {
        World world = Bukkit.getWorlds().getFirst();
        return world.getWorldBorder();
    }

    private void teleportIntoBorder(Player player) {
        WorldBorder border = getMainBorder();
        Location spawn = border.getWorld().getSpawnLocation();

        if (!player.getWorld().equals(spawn.getWorld()) || !border.isInside(player.getLocation())) {
            player.teleport(spawn);
        }
    }
}
