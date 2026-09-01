package net.woistjojo.borderBattle.commands;

import net.woistjojo.borderBattle.services.BorderBattleService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class GlowCommand implements CommandExecutor {
    private final BorderBattleService borderBattleService;

    public GlowCommand(BorderBattleService borderBattleService) {
        this.borderBattleService = borderBattleService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission("borderbattle.glow")) {
            sender.sendMessage("Dazu hast du keine Rechte.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("Nutze: /glow <sekunden>");
            return true;
        }

        Integer seconds = parsePositiveSeconds(args[0]);
        if (seconds == null) {
            sender.sendMessage("Nutze: /glow <sekunden>");
            return true;
        }

        int affectedPlayers = borderBattleService.glowOnlinePlayers(seconds);
        sender.sendMessage("Glow wurde für " + affectedPlayers + " Spieler für " + seconds + " Sekunden aktiviert.");
        return true;
    }

    private Integer parsePositiveSeconds(String input) {
        try {
            int seconds = Integer.parseInt(input);
            return seconds > 0 ? seconds : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
