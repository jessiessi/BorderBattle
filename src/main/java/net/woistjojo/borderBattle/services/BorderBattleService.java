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
import org.bukkit.Color;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BorderBattleService {
    private static final double BORDER_WARNING_DISTANCE = 20.0;
    private static final double MIN_BORDER_WARNING_SIZE = 100.0;
    private static final Duration BORDER_WARNING_COOLDOWN = Duration.ofSeconds(10);

    private final BorderBattle plugin;
    private final BossBar playerCountBar;
    private final ChallengeTimerService challengeTimerService;
    private final ConcurrentHashMap<UUID, Instant> lastBorderWarnings = new ConcurrentHashMap<>();
    private BukkitTask borderWarningTask;
    private BukkitTask victoryFireworkTask;
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
        freezeWorld();
        startBorderWarningTask();

        for (Player player : Bukkit.getOnlinePlayers()) {
            handleJoin(player);
        }
    }

    public void shutdown() {
        if (borderWarningTask != null) {
            borderWarningTask.cancel();
            borderWarningTask = null;
        }

        cancelVictoryFireworks();
        challengeTimerService.stop();
        playerCountBar.removeAll();
    }

    public void handleJoin(Player player) {
        PlayerData data = getOrCreatePlayerData(player);

        playerCountBar.addPlayer(player);

        if (data.isModerator() || data.isEliminated()) {
            player.setGameMode(GameMode.SPECTATOR);
        } else {
            player.setGameMode(runningPhase == RunningPhase.RUNNING ? GameMode.SURVIVAL : GameMode.ADVENTURE);
            if (runningPhase != RunningPhase.RUNNING) {
                teleportIntoBorder(player);
            }
        }

        updatePlayerCountBar();
        challengeTimerService.showTo(player);

        if (data.isEliminated()) {
            showEliminationFeedback(player, data.getTotalPlayers(), data.getPlacement());
        }
    }

    public void handleQuit(Player player) {
        PlayerData data = getOrCreatePlayerData(player);
        if (runningPhase == RunningPhase.RUNNING && !data.isModerator() && !data.isEliminated()) {
            int totalPlayers = getActiveTotalPlayerCount();
            int placement = getAlivePlayerCount();
            eliminatePlayer(player, totalPlayers, placement);
        }

        playerCountBar.removePlayer(player);
        lastBorderWarnings.remove(player.getUniqueId());
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
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.setGameMode(runningPhase == RunningPhase.RUNNING ? GameMode.SPECTATOR : GameMode.ADVENTURE);
            showEliminationFeedback(player, totalPlayers, placement);
        });
    }

    public boolean toggleModerator(Player player) {
        PlayerData data = getOrCreatePlayerData(player);
        boolean nowModerator = !data.isModerator();

        data.setModerator(nowModerator);
        if (nowModerator) {
            player.setGameMode(GameMode.SPECTATOR);
        } else if (data.isEliminated()) {
            player.setGameMode(GameMode.SPECTATOR);
        } else {
            player.setGameMode(runningPhase == RunningPhase.RUNNING ? GameMode.SURVIVAL : GameMode.ADVENTURE);
        }

        updatePlayerCountBar();
        return nowModerator;
    }

    public void startChallenge() {
        cancelVictoryFireworks();
        runningPhase = RunningPhase.RUNNING;
        unfreezeWorld();
        setBorder(plugin.getPluginConfig().getChallengeBorderSize(), 0);
        challengeTimerService.start();

        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = getOrCreatePlayerData(player);
            player.setGameMode(data.isModerator() || data.isEliminated() ? GameMode.SPECTATOR : GameMode.SURVIVAL);
        }

        updatePlayerCountBar();
    }

    public void stopChallenge() {
        cancelVictoryFireworks();
        runningPhase = RunningPhase.JOINPHASE;
        challengeTimerService.stop();
        clearEliminatedPlayers();
        setBorder(plugin.getPluginConfig().getWaitingBorderSize(), 0);
        freezeWorld();

        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = getOrCreatePlayerData(player);
            if (data.isModerator()) {
                player.setGameMode(GameMode.SPECTATOR);
            } else {
                player.setGameMode(GameMode.ADVENTURE);
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

    public int glowOnlinePlayers(int seconds) {
        int affectedPlayers = 0;
        PotionEffect glowEffect = new PotionEffect(PotionEffectType.GLOWING, seconds * 20, 0, false, false, true);

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.addPotionEffect(glowEffect);
            affectedPlayers++;
        }

        return affectedPlayers;
    }

    public void setDifficulty(Difficulty difficulty) {
        for (World world : Bukkit.getWorlds()) {
            world.setDifficulty(difficulty);
        }
    }

    public Difficulty changeDifficulty(int steps) {
        Difficulty difficulty = getMainWorld().getDifficulty();
        Difficulty newDifficulty = getDifficultyByLevel(getDifficultyLevel(difficulty) + steps);
        setDifficulty(newDifficulty);
        return newDifficulty;
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
        checkForWinner();
    }

    private void checkForWinner() {
        if (runningPhase != RunningPhase.RUNNING) {
            return;
        }

        Player winner = null;
        int alivePlayers = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = getOrCreatePlayerData(player);
            if (!data.isModerator() && !data.isEliminated()) {
                winner = player;
                alivePlayers++;
            }
        }

        if (alivePlayers == 1 && winner != null) {
            finishChallengeWithWinner(winner);
        }
    }

    private void finishChallengeWithWinner(Player winner) {
        runningPhase = RunningPhase.FINISHED;
        challengeTimerService.stop();
        setBorder(plugin.getPluginConfig().getWaitingBorderSize(), 0);
        freezeWorld();

        Location spawn = getMainWorld().getSpawnLocation();
        Location stageLocation = createVictoryStage(spawn);

        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = getOrCreatePlayerData(player);
            if (data.isModerator()) {
                player.setGameMode(GameMode.SPECTATOR);
                player.teleport(spawn);
                continue;
            }

            player.setGameMode(GameMode.ADVENTURE);
            player.teleport(player.getUniqueId().equals(winner.getUniqueId()) ? stageLocation : spawn);
        }

        Bukkit.broadcast(Component.text(winner.getName() + " Herzlichen Glückwunsch zum Sieg", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        startVictoryFireworks(stageLocation);
        clearEliminatedPlayers();
        runningPhase = RunningPhase.JOINPHASE;
        updatePlayerCountBar();
    }

    private Location createVictoryStage(Location spawn) {
        World world = spawn.getWorld();
        if (world == null) {
            return spawn;
        }

        int centerX = spawn.getBlockX() + 8;
        int centerZ = spawn.getBlockZ();
        int groundY = world.getHighestBlockYAt(centerX, centerZ);
        int platformY = groundY + 2;

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Material material = Math.abs(x) == 2 || Math.abs(z) == 2 ? Material.SMOOTH_QUARTZ_SLAB : Material.SMOOTH_QUARTZ;
                world.getBlockAt(centerX + x, platformY - 1, centerZ + z).setType(material);
                world.getBlockAt(centerX + x, platformY, centerZ + z).setType(Material.AIR);
                world.getBlockAt(centerX + x, platformY + 1, centerZ + z).setType(Material.AIR);
            }
        }

        world.getBlockAt(centerX, platformY - 1, centerZ).setType(Material.GOLD_BLOCK);
        return new Location(world, centerX + 0.5, platformY, centerZ + 0.5, 90.0F, 0.0F);
    }

    private void startVictoryFireworks(Location stageLocation) {
        cancelVictoryFireworks();

        final int[] launches = {0};
        victoryFireworkTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (launches[0] >= 10) {
                cancelVictoryFireworks();
                return;
            }

            double sideOffset = launches[0] % 2 == 0 ? -3.5 : 3.5;
            Location fireworkLocation = stageLocation.clone().add(0.0, 0.2, sideOffset);
            spawnVictoryFirework(fireworkLocation);
            launches[0]++;
        }, 0L, 10L);
    }

    private void spawnVictoryFirework(Location location) {
        Firework firework = location.getWorld().spawn(location, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();

        meta.setPower(1);
        meta.addEffect(org.bukkit.FireworkEffect.builder()
                .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.YELLOW, Color.ORANGE)
                .withFade(Color.WHITE)
                .trail(true)
                .flicker(true)
                .build());

        firework.setFireworkMeta(meta);
    }

    private void cancelVictoryFireworks() {
        if (victoryFireworkTask != null) {
            victoryFireworkTask.cancel();
            victoryFireworkTask = null;
        }
    }

    private void clearEliminatedPlayer(Player player, PlayerData data) {
        data.setEliminated(false);
        data.setTotalPlayers(0);
        data.setPlacement(0);

        eliminatedPlayersConfig.set(player.getUniqueId().toString(), null);
        saveEliminatedPlayers();
    }

    private void freezeWorld() {
        for (World world : Bukkit.getWorlds()) {
            world.setTime(0);
            world.setStorm(false);
            world.setThundering(false);
            world.setGameRule(GameRules.ADVANCE_TIME, false);
            world.setGameRule(GameRules.ADVANCE_WEATHER, false);
            world.setGameRule(GameRules.SPAWN_MOBS, false);

            for (Mob mob : world.getEntitiesByClass(Mob.class)) {
                mob.remove();
            }
        }
    }

    private void unfreezeWorld() {
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRules.ADVANCE_TIME, true);
            world.setGameRule(GameRules.ADVANCE_WEATHER, true);
            world.setGameRule(GameRules.SPAWN_MOBS, true);
        }
    }

    private void showEliminationFeedback(Player player, int totalPlayers, int placement) {
        Component placementMessage = getPlacementMessage(player, totalPlayers, placement);
        Title title = Title.title(
                Component.text("AUSGESCHIEDEN!", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                placementMessage,
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofMillis(500))
        );

        player.showTitle(title);
        player.sendMessage(placementMessage);
    }

    private Component getPlacementMessage(Player player, int totalPlayers, int placement) {
        return Component.text(player.getName().toUpperCase(), NamedTextColor.AQUA).decorate(TextDecoration.BOLD)
                .append(Component.text(" DU BIST AUF ", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
                .append(Component.text("PLATZ " + placement, NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                .append(Component.text(" VON ", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
                .append(Component.text(totalPlayers + " MITSPIELERN", NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
                .append(Component.text(" GEWORDEN!", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
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

    private void startBorderWarningTask() {
        if (borderWarningTask != null) {
            borderWarningTask.cancel();
        }

        borderWarningTask = Bukkit.getScheduler().runTaskTimer(plugin, this::warnPlayersNearBorder, 20L, 20L);
    }

    private void warnPlayersNearBorder() {
        WorldBorder border = getMainBorder();
        if (border.getSize() < MIN_BORDER_WARNING_SIZE) {
            lastBorderWarnings.clear();
            return;
        }

        Instant now = Instant.now();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = getOrCreatePlayerData(player);
            if (data.isModerator() || data.isEliminated() || !player.getWorld().equals(border.getWorld())) {
                continue;
            }

            double distance = getDistanceToBorder(player.getLocation(), border);
            if (distance > BORDER_WARNING_DISTANCE) {
                lastBorderWarnings.remove(player.getUniqueId());
                continue;
            }

            Instant lastWarning = lastBorderWarnings.get(player.getUniqueId());
            if (lastWarning != null && Duration.between(lastWarning, now).compareTo(BORDER_WARNING_COOLDOWN) < 0) {
                continue;
            }

            showPlayerNearBorderWarning(player);
            lastBorderWarnings.put(player.getUniqueId(), now);
        }
    }

    private double getDistanceToBorder(Location location, WorldBorder border) {
        Location center = border.getCenter();
        double halfSize = border.getSize() / 2.0;
        double distanceX = halfSize - Math.abs(location.getX() - center.getX());
        double distanceZ = halfSize - Math.abs(location.getZ() - center.getZ());
        return Math.min(distanceX, distanceZ);
    }

    private void showPlayerNearBorderWarning(Player player) {
        Title title = Title.title(
                Component.text("ACHTUNG!!! " + "Border!", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                Component.text("Schrumpft sie, stirbst du.", NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(4), Duration.ofMillis(500))
        );

        player.showTitle(title);
    }

    private String formatBlocks(double blocks) {
        if (blocks == Math.rint(blocks)) {
            return String.valueOf((long) blocks);
        }

        return String.valueOf(blocks);
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
                plugin.getLogger().warning("Ungültige UUID in eliminated-players.yml: " + uuidText);
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
        return getMainWorld().getWorldBorder();
    }

    private World getMainWorld() {
        return Bukkit.getWorlds().getFirst();
    }

    private int getDifficultyLevel(Difficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> 0;
            case EASY -> 1;
            case NORMAL -> 2;
            case HARD -> 3;
        };
    }

    private Difficulty getDifficultyByLevel(int level) {
        return switch (Math.max(0, Math.min(3, level))) {
            case 0 -> Difficulty.PEACEFUL;
            case 1 -> Difficulty.EASY;
            case 2 -> Difficulty.NORMAL;
            default -> Difficulty.HARD;
        };
    }

    private void teleportIntoBorder(Player player) {
        WorldBorder border = getMainBorder();
        Location spawn = border.getWorld().getSpawnLocation();

        if (!player.getWorld().equals(spawn.getWorld()) || !border.isInside(player.getLocation())) {
            player.teleport(spawn);
        }
    }
}
