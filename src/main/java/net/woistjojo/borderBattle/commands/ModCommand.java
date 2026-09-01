package net.woistjojo.borderBattle.commands;

import net.woistjojo.borderBattle.services.BorderBattleService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ModCommand implements CommandExecutor {
    private final BorderBattleService borderBattleService;

    public ModCommand(BorderBattleService borderBattleService) {
        this.borderBattleService = borderBattleService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission("borderbattle.mod")) {
            sender.sendMessage("Dazu hast du keine Rechte.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Command kann nur von Moderatoren genutzt werden.");
            return true;
        }

        boolean moderator = borderBattleService.toggleModerator(player);

        if (moderator) {
            player.sendMessage("Du bist jetzt Moderator und wirst nicht mehr als Spieler gezählt.");
        } else {
            player.sendMessage("Du bist jetzt wieder Spieler.");
        }

        return true;
    }
}
