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

import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.filter.Getter;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.game.state.GameStoppingServerEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.event.server.ClientPingServerEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.service.user.UserStorageService;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Plugin(
        id = "advancedserverlisticons",
        name = "AdvancedServerListIcons",
        description = "Advanced Server List Icons for Sponge",
        authors = {
                "Me4502"
        }
)
public class AdvancedServerListIconsSponge implements AdvancedServerListIcons {

    @Inject private Logger logger;

    @Inject
    @DefaultConfig(sharedRoot = false)
    private Path defaultConfig;

    @Inject
    @DefaultConfig(sharedRoot = false)
    private ConfigurationLoader<CommentedConfigurationNode> configManager;

    private DatabaseManager databaseManager;
    private ImageHandler imageHandler;

    private UserStorageService userStorageService;

    @Listener
    public void onServerStart(GameStartedServerEvent event) {
        AdvancedServerListIcons.setInstance(this);
        imageHandler = new ImageHandler();

        loadConfig();

        userStorageService = Sponge.getServiceManager().provide(UserStorageService.class).get();

    }

    @Listener
    public void onServerReload(GameReloadEvent event) {
        databaseManager.disconnect();
        loadConfig();
    }

    private void loadConfig() {
        try {
            if (!Files.exists(defaultConfig, LinkOption.NOFOLLOW_LINKS)) {
                URL jarConfigFile = this.getClass().getResource("default.conf");
                ConfigurationLoader<CommentedConfigurationNode> loader = HoconConfigurationLoader.builder().setURL(jarConfigFile).build();
                configManager.save(loader.load());
            }

            ConfigurationNode node = configManager.load();
            node.getOptions().setShouldCopyDefaults(true);

            String user = node.getNode("jdbc-username").getString("root");
            String password = node.getNode("jdbc-password").getString("password");
            String jdbcUrl = node.getNode("jdbc-url").getString("jdbc:mysql://localhost:3306/minecraft");

            databaseManager = new DatabaseManager(jdbcUrl, user, password);
            databaseManager.connect();

            for (Map.Entry<Object, ? extends ConfigurationNode> definitionKey : node.getNode("definitions").getChildrenMap().entrySet()) {
                int priority = definitionKey.getValue().getNode("priority").getInt(1);
                ImageType type = ImageType.valueOf(definitionKey.getValue().getNode("type").getString(ImageType.OVERLAY.name()));
                String permission = definitionKey.getValue().getNode("permission").getString();
                List<String> images = definitionKey.getValue().getNode("images").getList(new TypeToken<String>() {});

                SpongeImageDetails imageDetails = new SpongeImageDetails(priority, type, permission, images);
                imageHandler.addImage(imageDetails);
            }

            configManager.save(node);
        } catch (IOException | ObjectMappingException e) {
            e.printStackTrace();
        }
    }

    @Listener
    public void onServerStopping(GameStoppingServerEvent event) {
        databaseManager.disconnect();
    }

    @Listener
    public void onPlayerJoin(ClientConnectionEvent.Join event, @Getter("getTargetEntity") Player player) {
        getDatabaseManager().addPlayerAddress(player.getUniqueId(), player.getConnection().getAddress().getAddress().getHostAddress());
    }

    @Listener
    public void onServerListPing(ClientPingServerEvent event) {
        UUID uuid = getDatabaseManager().getPlayerUUID(event.getClient().getAddress().getAddress().getHostAddress());
        if (uuid != null) {
            User user = userStorageService.get(uuid).get();
            try {
                event.getResponse().setFavicon(Sponge.getRegistry().loadFavicon(getImageHandler().getImageForUser(user.getUniqueId(), user.getName())));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public UserStorageService getUserStorageService() {
        return this.userStorageService;
    }

    @Override
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    @Override
    public ImageHandler getImageHandler() {
        return imageHandler;
    }

    @Override
    public File getDataFolder() {
        return defaultConfig.toFile().getParentFile();
    }
}
