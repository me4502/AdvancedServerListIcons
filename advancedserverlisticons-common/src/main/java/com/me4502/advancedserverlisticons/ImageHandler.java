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

import com.google.common.base.Charsets;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

public class ImageHandler {

    private final Set<ImageDetails> imageDetails = new TreeSet<>();

    private final LoadingCache<ImageProfile, BufferedImage> iconCache = CacheBuilder.newBuilder().maximumSize(100).expireAfterAccess(5, TimeUnit.MINUTES).build(new CacheLoader<ImageProfile, BufferedImage>() {
        @Override
        public BufferedImage load(ImageProfile imageProfile) throws Exception {
            ImageDetails imageDetail = getImageDetails(imageProfile.uuid);
            BufferedImage headImage = getUserHeadImage(imageProfile.uuid, imageProfile.name);
            if (imageDetail != null) {
                File imagesDirectory = new File(AdvancedServerListIcons.inst().getDataFolder(), "images");
                imagesDirectory.mkdirs();

                switch (imageDetail.getType()) {
                    case UNDERLAY: {
                        BufferedImage overlayImage = ImageIO.read(new File(imagesDirectory, imageDetail.getImages().get(0)));
                        BufferedImage buffer = new BufferedImage(overlayImage.getWidth(null), overlayImage.getHeight(null), 2);

                        Graphics2D g = buffer.createGraphics();
                        g.drawImage(overlayImage, null, null);
                        g.drawImage(headImage, 16, 16, null);

                        return buffer;
                    }
                    case OVERLAY: {
                        BufferedImage overlayImage = ImageIO.read(new File(imagesDirectory, imageDetail.getImages().get(0)));
                        BufferedImage buffer = new BufferedImage(overlayImage.getWidth(null), overlayImage.getHeight(null), 2);

                        Graphics2D g = buffer.createGraphics();
                        g.drawImage(headImage, 16, 16, null);
                        g.drawImage(overlayImage, null, null);

                        return buffer;
                    }
                }
            }

            return headImage;
        }
    });

    public ImageHandler() {
        File imagesDirectory = new File(AdvancedServerListIcons.inst().getDataFolder(), "images");
        File playerHeadDirectory = new File(AdvancedServerListIcons.inst().getDataFolder(), "heads");
        imagesDirectory.mkdirs();
        playerHeadDirectory.mkdirs();
    }

    public BufferedImage getImageForUser(UUID uuid, String name) throws IOException {
        return iconCache.getUnchecked(new ImageProfile(name, uuid));
    }

    private final Gson gson = new GsonBuilder().create();

    private Optional<URL> getTextureUrl(UUID uuid) throws IOException {
        URL sessionUrl = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", ""));
        SessionProfileData data = gson.fromJson(
            String.join("\n", Resources.readLines(sessionUrl, Charsets.UTF_8)),
            SessionProfileData.class
        );
        for (TextureProperty property : data.properties) {
            if (property.name.equals("textures")) {
                String decodedTextureData = new String(Base64.getDecoder().decode(property.value));
                TextureData textureData = gson.fromJson(decodedTextureData, TextureData.class);
                return Optional.of(new URL(textureData.textures.get("SKIN").url));
            }
        }

        return Optional.empty();
    }

    public BufferedImage getUserHeadImage(UUID uuid, String name) throws IOException {
        File playerHeadDirectory = new File(AdvancedServerListIcons.inst().getDataFolder(), "heads");
        playerHeadDirectory.mkdirs();
        File file = new File(playerHeadDirectory, uuid.toString() + ".png");
        if (!file.exists() || System.currentTimeMillis() - file.lastModified() > 1000*60*60*24) {
            Optional<URL> textureOpt = getTextureUrl(uuid);
            if (textureOpt.isPresent()) {
                URL asset = textureOpt.get();
                BufferedImage img = toBufferedImage(ImageIO.read(asset).getSubimage(8, 8, 8, 8).getScaledInstance(32, 32, 1));
                ImageIO.write(img, "png", file);
                return img;
            } else {
                throw new FileNotFoundException();
            }
        } else {
            return ImageIO.read(file);
        }
    }

    private ImageDetails getImageDetails(UUID uuid) {
        for (ImageDetails details : imageDetails) {
            if (details.canUse(uuid)) {
                return details;
            }
        }

        return null;
    }

    private BufferedImage toBufferedImage(Image image) {
        if (image instanceof BufferedImage) {
            return (BufferedImage) image;
        }
        BufferedImage buffer = new BufferedImage(image.getWidth(null), image.getHeight(null), 2);
        Graphics2D g = buffer.createGraphics();
        g.drawImage(image, null, null);
        image.flush();
        return buffer;
    }

    public void addImage(ImageDetails imageDetail) {
        this.imageDetails.add(imageDetail);
    }

    private static class ImageProfile {
        private String name;
        private UUID uuid;

        ImageProfile(String name, UUID uuid) {
            this.name = name;
            this.uuid = uuid;
        }
    }

    private class TextureReference {
        String url;
    }

    private static class TextureData {
        long timestamp;
        String profileId;
        Map<String, TextureReference> textures;
    }

    private static class TextureProperty {
        String name;
        String value;
    }

    private static class SessionProfileData {
        String id;
        String name;
        TextureProperty[] properties;
    }
}
