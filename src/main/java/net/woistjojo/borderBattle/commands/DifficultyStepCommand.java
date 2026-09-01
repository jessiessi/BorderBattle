package net.woistjojo.borderBattle.commands;

import net.woistjojo.borderBattle.services.BorderBattleService;
import org.bukkit.Difficulty;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class DifficultyStepCommand implements CommandExecutor {
    private final BorderBattleService borderBattleService;
    private final int steps;

    public DifficultyStepCommand(BorderBattleService borderBattleService, int steps) {
        this.borderBattleService = borderBattleService;
        this.steps = steps;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission("borderbattle.difficulty")) {
            sender.sendMessage("Dazu hast du keine Rechte.");
            return true;
        }

        if (args.length != 0) {
            sender.sendMessage("Nutze: /" + label);
            return true;
        }

        Difficulty difficulty = borderBattleService.changeDifficulty(steps);
        sender.sendMessage("Die Schwierigkeit ist jetzt " + formatDifficulty(difficulty) + ".");
        return true;
    }

    private String formatDifficulty(Difficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> "friedlich";
            case EASY -> "einfach";
            case NORMAL -> "normal";
            case HARD -> "schwer";
        };
    }
}
