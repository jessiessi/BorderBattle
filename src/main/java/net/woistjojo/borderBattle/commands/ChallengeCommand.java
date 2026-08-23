package net.woistjojo.borderBattle.commands;

import net.woistjojo.borderBattle.services.BorderBattleService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class ChallengeCommand implements CommandExecutor {
    private final BorderBattleService borderBattleService;

    public ChallengeCommand(BorderBattleService borderBattleService) {
        this.borderBattleService = borderBattleService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission("borderbattle.challenge")) {
            sender.sendMessage("Dazu hast du keine Rechte.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("Nutze: /challenge start oder /challenge stop");
            return true;
        }

        if (args[0].equalsIgnoreCase("start")) {
            borderBattleService.startChallenge();
            sender.sendMessage("Die Challenge wurde gestartet.");
            return true;
        }

        if (args[0].equalsIgnoreCase("stop")) {
            borderBattleService.stopChallenge();
            sender.sendMessage("Die Challenge wurde gestoppt.");
            return true;
        }

        sender.sendMessage("Nutze: /challenge start oder /challenge stop");
        return true;
    }
}
