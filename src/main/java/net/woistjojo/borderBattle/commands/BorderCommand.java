package net.woistjojo.borderBattle.commands;

import net.woistjojo.borderBattle.services.BorderBattleService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class BorderCommand implements CommandExecutor {
    private final BorderBattleService borderBattleService;

    public BorderCommand(BorderBattleService borderBattleService) {
        this.borderBattleService = borderBattleService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission("borderbattle.border")) {
            sender.sendMessage("Dazu hast du keine Rechte.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("stop")) {
            borderBattleService.stopBorder();
            sender.sendMessage("Die Border-Bewegung wurde gestoppt.");
            return true;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("go")) {
            Double blocks = parsePositiveDouble(args[1]);
            Long seconds = parsePositiveLong(args[2]);

            if (blocks == null || seconds == null) {
                sender.sendMessage("Nutze: /border go <bloecke> <sekunden>");
                return true;
            }

            borderBattleService.moveBorder(blocks, seconds);
            sender.sendMessage("Die Border bewegt sich auf " + blocks + " Bloecke in " + seconds + " Sekunden.");
            return true;
        }

        sender.sendMessage("Nutze: /border go <bloecke> <sekunden> oder /border stop");
        return true;
    }

    private Double parsePositiveDouble(String input) {
        try {
            double value = Double.parseDouble(input);
            return value > 0 ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long parsePositiveLong(String input) {
        try {
            long value = Long.parseLong(input);
            return value >= 0 ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
