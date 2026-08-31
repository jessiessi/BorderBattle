package net.woistjojo.borderBattle.services;

import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
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
import java.time.Duration;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BorderBattleService {
    private static final Pattern OP_ENTRY_PATTERN = Pattern.compile("\\{[^}]*\"uuid\"\\s*:\\s*\"([^\"]+)\"[^}]*\"level\"\\s*:\\s*(\\d+)[^}]*}");

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
            if (getOperatorLevel(player) < 2) {
                player.kick(getEliminationMessage(player, data.getTotalPlayers(), data.getPlacement()));
                return;
            }

            clearEliminatedPlayer(player, data);
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
        PlayerData data = getOrCreatePlayerData(player);
        if (runningPhase == RunningPhase.RUNNING && !isEliminationBypassed(player, data) && !data.isEliminated()) {
            int totalPlayers = getActiveTotalPlayerCount();
            int placement = getAlivePlayerCount();
            eliminatePlayer(player, totalPlayers, placement);
        }

        playerCountBar.removePlayer(player);
        updatePlayerCountBar();
    }

    public void handleDeath(Player player) {
        PlayerData data = getOrCreatePlayerData(player);
        if (isEliminationBypassed(player, data) || data.isEliminated()) {
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
        showBorderWarning(blocks);
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

    private void clearEliminatedPlayer(Player player, PlayerData data) {
        data.setEliminated(false);
        data.setTotalPlayers(0);
        data.setPlacement(0);

        eliminatedPlayersConfig.set(player.getUniqueId().toString(), null);
        saveEliminatedPlayers();
    }

    private boolean isEliminationBypassed(Player player, PlayerData data) {
        return data.isModerator() || getOperatorLevel(player) >= 2;
    }

    private Component getEliminationMessage(Player player, int totalPlayers, int placement) {
        return Component.text("Du bist leider gestorben", NamedTextColor.RED)
                .append(Component.newline())
                .append(Component.text("Vielen Dank fuers mitmachen <3", NamedTextColor.BLUE))
                .append(Component.newline())
                .append(Component.text("Du " + player.getName() + " bist von " + totalPlayers + " auf Platz " + placement + ".", NamedTextColor.GRAY));
    }

    private void showBorderWarning(double blocks) {
        Title title = Title.title(
                Component.text("ACHTUNG!", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                Component.text("Die Border schrumpft auf " + formatBlocks(blocks) + " Blöcke", NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofMillis(500))
        );

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
        }
    }

    private String formatBlocks(double blocks) {
        if (blocks == Math.rint(blocks)) {
            return String.valueOf((long) blocks);
        }

        return String.valueOf(blocks);
    }

    private int getOperatorLevel(Player player) {
        File opsFile = new File(plugin.getServer().getWorldContainer(), "ops.json");
        if (!opsFile.isFile()) {
            return player.isOp() ? 4 : 0;
        }

        try {
            String content = Files.readString(opsFile.toPath());
            Matcher matcher = OP_ENTRY_PATTERN.matcher(content);
            String playerUuid = player.getUniqueId().toString();

            while (matcher.find()) {
                if (matcher.group(1).equalsIgnoreCase(playerUuid)) {
                    return Integer.parseInt(matcher.group(2));
                }
            }
        } catch (IOException | NumberFormatException exception) {
            plugin.getLogger().warning("ops.json konnte nicht gelesen werden: " + exception.getMessage());
        }

        return 0;
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
