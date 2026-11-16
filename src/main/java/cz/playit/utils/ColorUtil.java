package cz.playit.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private ColorUtil() {
    }

    public static Component parse(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        // Pokud text obsahuje MiniMessage tagy (např. <gradient:...>, <#FF0000>, <bold>)
        if (text.contains("<") && text.contains(">")) {
            // Převede případné zbylé & kódy na MiniMessage kompatibilní text přes legacy serializer
            String translatedHex = translateHexCodes(text);
            if (!translatedHex.contains("&")) {
                return MINI_MESSAGE.deserialize(translatedHex);
            }
            // Kombinace & a MiniMessage: nejprve deserializujeme legacy a pak MiniMessage
            Component legacyComp = LEGACY_SERIALIZER.deserialize(translatedHex);
            String miniSerialized = MINI_MESSAGE.serialize(legacyComp)
                    .replace("\\<", "<")
                    .replace("\\>", ">");
            return MINI_MESSAGE.deserialize(miniSerialized);
        }

        return LEGACY_SERIALIZER.deserialize(translateHexCodes(text));
    }

    private static String translateHexCodes(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder buffer = new StringBuilder(message.length() + 32);
        while (matcher.find()) {
            String group = matcher.group(1);
            matcher.appendReplacement(buffer, "&x"
                    + "&" + group.charAt(0) + "&" + group.charAt(1)
                    + "&" + group.charAt(2) + "&" + group.charAt(3)
                    + "&" + group.charAt(4) + "&" + group.charAt(5));
        }
        return matcher.appendTail(buffer).toString();
    }
}
