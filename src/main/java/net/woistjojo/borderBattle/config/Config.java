package net.woistjojo.borderBattle.config;

import de.exlll.configlib.ConfigLib;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.DeserializationCoercionType;
import de.exlll.configlib.Ignore;
import de.exlll.configlib.YamlConfigurationProperties;
import de.exlll.configlib.YamlConfigurations;
import lombok.Getter;
import lombok.Setter;
import net.woistjojo.borderBattle.BorderBattle;

import java.io.File;
import java.nio.file.Path;

@Getter
@Setter
@Configuration
public class Config {
    public static final String FILE_NAME = "config.yml";

    @Ignore
    private BorderBattle plugin;

    private double waitingBorderSize = 20.0;
    private double challengeBorderSize = 500.0;

    public static YamlConfigurationProperties getProperties(BorderBattle plugin) {
        return ConfigLib.BUKKIT_DEFAULT_PROPERTIES.toBuilder()
                .header("""
                        BorderBattle config
                        """)
                .setDeserializationCoercionTypes(DeserializationCoercionType.NUMBER_TO_STRING)
                .build();
    }

    public void save() {
        if (!this.plugin.getDataFolder().exists() && !this.plugin.getDataFolder().mkdirs()) {
            this.plugin.getLogger().severe("Plugin-Ordner konnte nicht erstellt werden.");
            return;
        }

        Path path = this.plugin.getDataPath().resolve(FILE_NAME);
        YamlConfigurations.save(path, Config.class, this, getProperties(this.plugin));
    }

    public static void load(BorderBattle plugin) {
        File file = plugin.getDataFolder().toPath().resolve(FILE_NAME).toFile();
        plugin.setPluginConfig(YamlConfigurations.update(file.toPath(), Config.class, getProperties(plugin)));
        plugin.getPluginConfig().setPlugin(plugin);
    }
}
