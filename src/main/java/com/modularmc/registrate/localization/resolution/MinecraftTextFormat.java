package com.modularmc.registrate.localization.resolution;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntMaps;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class MinecraftTextFormat {

    private static final Pattern TOKEN = Pattern.compile("%(?:(\\d+)\\$)?s|%%");

    private static final String NORMAL = "abcdefghijklmn\u00F1opqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789()[]{}<>\u25C1\u25B7_,;.?!/\\'";
    private static final String INVERTED = "\u0250q\u0254p\u01DD\u025Fb\u0265\u0131\u0638\u029E\u05DF\u026Fuuodb\u0279s\u0287n\u028C\u028Dx\u028Ez" +
            "\u2C6F\u15FA\u0186\u15E1\u018E\u2132\u2141HI\u017F\u029E\uA780WNO\u0500\u1F49\u1D1AS\u27D8\u2229\u039BMX\u028EZ" +
            "0\u0196\u1105\u0190\u3123\u03DB9\u312586)(][}{><\u25B7\u25C1\u203E'\u061B\u02D9\u00BF\u00A1\\/,";

    private MinecraftTextFormat() {}

    record Token(int start, int end, int argument) {}

    record Parsed(List<Token> tokens, Int2IntMap signature) {}

    private static final Parsed PLAIN = new Parsed(List.of(), Int2IntMaps.EMPTY_MAP);

    static Parsed parse(String text, String location) {
        int percent = text.indexOf('%');
        if (percent < 0) {
            return PLAIN;
        }

        var tokens = new ArrayList<Token>();
        var arguments = new Int2IntOpenHashMap();
        var matcher = TOKEN.matcher(text);
        int nextArgument = 1;
        while (percent >= 0) {
            if (!matcher.region(percent, text.length()).lookingAt()) {
                throw new IllegalArgumentException("Invalid Minecraft placeholder at index %d in %s".formatted(percent, location));
            }

            int argument;
            if (matcher.end() - percent == 2 && text.charAt(percent + 1) == '%') {
                argument = 0;
            } else if (matcher.group(1) == null) {
                argument = nextArgument++;
            } else {
                try {
                    argument = Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException error) {
                    throw new IllegalArgumentException("Invalid Minecraft argument index in " + location, error);
                }
                if (argument < 1) {
                    throw new IllegalArgumentException("Minecraft argument indices start at 1 in " + location);
                }
            }
            arguments.addTo(argument, 1);
            tokens.add(new Token(percent, matcher.end(), argument));
            percent = text.indexOf('%', matcher.end());
        }
        return new Parsed(tokens, arguments);
    }

    static String upsideDown(String text, Parsed format) {
        var result = new StringBuilder(text.length());
        int cursor = text.length();
        for (var token : format.tokens().reversed()) {
            appendUpsideDown(text, token.end(), cursor, result);
            if (token.argument() == 0) {
                result.append("%%");
            } else {
                result.append('%').append(token.argument()).append("$s");
            }
            cursor = token.start();
        }
        appendUpsideDown(text, 0, cursor, result);
        return result.toString();
    }

    private static void appendUpsideDown(String text, int start, int end, StringBuilder result) {
        for (int cursor = end; cursor > start;) {
            int ch = text.codePointBefore(cursor);
            cursor -= Character.charCount(ch);
            int index = NORMAL.indexOf(ch);
            result.appendCodePoint(index < 0 ? ch : INVERTED.charAt(index));
        }
    }
}
