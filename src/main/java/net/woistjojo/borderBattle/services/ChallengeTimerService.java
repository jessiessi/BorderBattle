package net.woistjojo.borderBattle.services;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;

public final class ChallengeTimerService {
    private static final TextColor START_COLOR = TextColor.color(0, 120, 255);
    private static final TextColor END_COLOR = TextColor.color(255, 230, 0);

    private final JavaPlugin plugin;
    private BukkitTask updateTask;
    private Instant startedAt;

    public ChallengeTimerService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        startedAt = Instant.now();
        updateActionBars();

        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateActionBars, 20L, 20L);
    }

    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }

        startedAt = null;
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendActionBar(Component.empty());
        }
    }

    public void showTo(Player player) {
        if (startedAt == null) {
            player.sendActionBar(Component.empty());
            return;
        }

        long elapsedSeconds = Duration.between(startedAt, Instant.now()).getSeconds();
        player.sendActionBar(createGradientText(formatTime(elapsedSeconds)));
    }

    private void updateActionBars() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            showTo(player);
        }
    }

    private String formatTime(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private Component createGradientText(String text) {
        Component result = Component.empty();
        int lastCharacterIndex = Math.max(1, text.length() - 1);

        for (int index = 0; index < text.length(); index++) {
            double progress = (double) index / lastCharacterIndex;
            result = result.append(Component.text(text.charAt(index), interpolateColor(progress)));
        }

        return result;
    }

    private TextColor interpolateColor(double progress) {
        int red = interpolateChannel(START_COLOR.red(), END_COLOR.red(), progress);
        int green = interpolateChannel(START_COLOR.green(), END_COLOR.green(), progress);
        int blue = interpolateChannel(START_COLOR.blue(), END_COLOR.blue(), progress);
        return TextColor.color(red, green, blue);
    }

    private int interpolateChannel(int start, int end, double progress) {
        return start + (int) Math.round((end - start) * progress);
    }
}
