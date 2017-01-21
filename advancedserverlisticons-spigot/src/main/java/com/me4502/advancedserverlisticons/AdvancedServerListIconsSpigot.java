/*
 * Copyright (c) 2017 Me4502 (Madeline Miller)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.me4502.advancedserverlisticons;

import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

public final class AdvancedServerListIconsSpigot extends JavaPlugin implements Listener, AdvancedServerListIcons {

    public static Permission perms = null;

    private DatabaseManager databaseManager;
    private ImageHandler imageHandler;

    @Override
    public void onEnable() {
        AdvancedServerListIcons.setInstance(this);
        setupPermissions();

        saveDefaultConfig();

        Bukkit.getPluginManager().registerEvents(this, this);

        imageHandler = new ImageHandler();

        loadConfig();
    }

    private void setupPermissions() {
        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        perms = rsp.getProvider();
    }

    private void loadConfig() {
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(new File(getDataFolder(), "config.yml"));
            config.options().copyDefaults(true);

            String user = config.getString("jdbc-username", "root");
            String password = config.getString("jdbc-password", "password");
            String jdbcUrl = config.getString("jdbc-url", "jdbc:mysql://localhost:3306/minecraft");

            databaseManager = new DatabaseManager(jdbcUrl, user, password);
            databaseManager.connect();

            ConfigurationSection definitions = config.getConfigurationSection("definitions");
            for (String definitionKey : definitions.getKeys(false)) {
                ConfigurationSection definition = definitions.getConfigurationSection(definitionKey);

                int priority = definition.getInt("priority", 1);
                ImageType type = ImageType.valueOf(definition.getString("type", ImageType.OVERLAY.name()));
                String permission = definition.getString("permission");
                List<String> images = definition.getStringList("images");

                SpigotImageDetails imageDetails = new SpigotImageDetails(priority, type, permission, images);
                imageHandler.addImage(imageDetails);
            }

            config.save(new File(getDataFolder(), "config.yml"));
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        getDatabaseManager().disconnect();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        getDatabaseManager().addPlayerAddress(event.getPlayer().getUniqueId(), event.getPlayer().getAddress().getAddress().getHostAddress());
    }

    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        UUID uuid = getDatabaseManager().getPlayerUUID(event.getAddress().getHostAddress());
        if (uuid != null) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            try {
                event.setServerIcon(Bukkit.loadServerIcon(getImageHandler().getImageForUser(player.getUniqueId(), player.getName())));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    @Override
    public ImageHandler getImageHandler() {
        return imageHandler;
    }
}
