package com.modularmc.registrate.localization.index;

import com.modularmc.registrate.localization.MinecraftLocale;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/** Index shared by the annotation processor and the DataGen reader on this Minecraft version line. */
public final class LocalizationIndex {

    private LocalizationIndex() {}

    public static String path(String modId) {
        return "META-INF/registratelib/localization/" + modId + ".bin";
    }

    public record Entry(String namespace, String key, String source, String location, Map<MinecraftLocale, String> translations) {}

    public static void write(DataOutputStream output, Collection<Entry> entries) throws IOException {
        output.writeInt(entries.size());
        for (var entry : entries) {
            output.writeUTF(entry.namespace());
            output.writeUTF(entry.key());
            output.writeUTF(entry.source());
            output.writeUTF(entry.location());
            output.writeInt(entry.translations().size());
            for (var translation : entry.translations().entrySet()) {
                output.writeUTF(translation.getKey().name());
                output.writeUTF(translation.getValue());
            }
        }
    }

    public static void read(DataInputStream input, Consumer<Entry> consumer) throws IOException {
        int count = input.readInt();
        for (int i = 0; i < count; i++) {
            String namespace = input.readUTF();
            String key = input.readUTF();
            String source = input.readUTF();
            String location = input.readUTF();
            int targets = input.readInt();

            var translations = new EnumMap<MinecraftLocale, String>(MinecraftLocale.class);
            for (int target = 0; target < targets; target++) {
                translations.put(MinecraftLocale.valueOf(input.readUTF()), input.readUTF());
            }
            consumer.accept(new Entry(namespace, key, source, location, translations));
        }
    }
}
