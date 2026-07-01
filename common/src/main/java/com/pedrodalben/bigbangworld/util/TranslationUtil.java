package com.pedrodalben.bigbangworld.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class TranslationUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(TranslationUtil.class);
    private static final Map<String, Map<String, String>> LANG_MAPS = new HashMap<>();
    private static String currentLanguage = "pt_br";

    static {
        loadLanguage("pt_br");
        loadLanguage("en_us");
    }

    public static void loadLanguage(String lang) {
        String path = "/data/lang/" + lang + ".json";
        try (InputStream is = TranslationUtil.class.getResourceAsStream(path)) {
            if (is != null) {
                Map<String, String> map = new Gson().fromJson(
                        new InputStreamReader(is, StandardCharsets.UTF_8),
                        new TypeToken<Map<String, String>>() {}.getType()
                );
                LANG_MAPS.put(lang, map);
                LOGGER.info("[BigBangWorld] Loaded language file: {}", path);
            } else {
                LOGGER.warn("[BigBangWorld] Language file not found in jar: {}", path);
            }
        } catch (Exception e) {
            LOGGER.error("[BigBangWorld] Failed to load language file: {}", path, e);
        }
    }

    public static String get(String key, Object... args) {
        Map<String, String> map = LANG_MAPS.get(currentLanguage);
        if (map == null || !map.containsKey(key)) {
            map = LANG_MAPS.get("pt_br"); // Fallback
        }
        if (map == null || !map.containsKey(key)) {
            map = LANG_MAPS.get("en_us"); // Secondary fallback
        }

        if (map == null || !map.containsKey(key)) {
            return key;
        }

        String format = map.get(key);
        try {
            return java.text.MessageFormat.format(format, args);
        } catch (Exception e) {
            return format;
        }
    }

    public static Component getComponent(String key, Object... args) {
        return Component.literal(get(key, args));
    }

    public static void setLanguage(String lang) {
        currentLanguage = lang;
    }
}
