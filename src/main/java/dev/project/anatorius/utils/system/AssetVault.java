package dev.project.anatorius.utils.system;

import java.io.InputStream;

public final class AssetVault {
    private AssetVault() {
    }

    public static InputStream open(String path) {
        String key = path.startsWith("/") ? path.substring(1) : path;
        return AssetVault.class.getResourceAsStream("/" + key);
    }
}
