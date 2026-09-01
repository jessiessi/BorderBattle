package net.woistjojo.borderBattle;

import lombok.Getter;
import lombok.Setter;
import net.woistjojo.borderBattle.commands.BorderCommand;
import net.woistjojo.borderBattle.commands.ChallengeCommand;
import net.woistjojo.borderBattle.commands.DifficultyCommand;
import net.woistjojo.borderBattle.commands.DifficultyStepCommand;
import net.woistjojo.borderBattle.commands.GlowCommand;
import net.woistjojo.borderBattle.commands.ModCommand;
import net.woistjojo.borderBattle.config.Config;
import net.woistjojo.borderBattle.listeners.PlayerConnectionListener;
import net.woistjojo.borderBattle.services.BorderBattleService;
import org.bukkit.Difficulty;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
@Setter
public final class BorderBattle extends JavaPlugin {
    private Config pluginConfig;
    private BorderBattleService borderBattleService;

    @Override
    public void onEnable() {
        Config.load(this);

        this.borderBattleService = new BorderBattleService(this);
        this.borderBattleService.initialize();

        registerCommand("mod", new ModCommand(borderBattleService));
        registerCommand("challenge", new ChallengeCommand(borderBattleService));
        registerCommand("border", new BorderCommand(borderBattleService));
        registerCommand("glow", new GlowCommand(borderBattleService));
        registerCommand("schwer", new DifficultyCommand(borderBattleService, Difficulty.HARD, "Die Challenge ist jetzt schwerer."));
        registerCommand("low", new DifficultyCommand(borderBattleService, Difficulty.EASY, "Die Challenge ist jetzt leichter."));
        registerCommand("schwerer", new DifficultyStepCommand(borderBattleService, 1));
        registerCommand("lower", new DifficultyStepCommand(borderBattleService, -1));

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new PlayerConnectionListener(borderBattleService), this);
    }

    @Override
    public void onDisable() {
        if (borderBattleService != null) {
            borderBattleService.shutdown();
        }
    }

    private void registerCommand(String name, CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command '" + name + "' fehlt in der plugin.yml.");
            return;
        }

        command.setExecutor(executor);
    }
}
