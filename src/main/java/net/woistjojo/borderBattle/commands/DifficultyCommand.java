package net.woistjojo.borderBattle.commands;

import net.woistjojo.borderBattle.services.BorderBattleService;
import org.bukkit.Difficulty;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class DifficultyCommand implements CommandExecutor {
    private final BorderBattleService borderBattleService;
    private final Difficulty difficulty;
    private final String successMessage;

    public DifficultyCommand(BorderBattleService borderBattleService, Difficulty difficulty, String successMessage) {
        this.borderBattleService = borderBattleService;
        this.difficulty = difficulty;
        this.successMessage = successMessage;
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

        borderBattleService.setDifficulty(difficulty);
        sender.sendMessage(successMessage);
        return true;
    }
}
