package com.modularmc.registrate.localization.processor;

import com.modularmc.registrate.localization.MinecraftLocale;
import com.modularmc.registrate.localization.annotation.Trans;
import com.modularmc.registrate.localization.annotation.TransVariants;
import com.modularmc.registrate.localization.annotation.Translation;
import com.modularmc.registrate.localization.index.LocalizationIndex;
import com.modularmc.registrate.localization.index.LocalizationIndex.Entry;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.StandardLocation;

/** Collects the annotated declarations from all rounds of a downstream Java compilation. */
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class TranslationProcessor extends AbstractProcessor {

    private final Map<String, ArrayList<Declaration>> declarations = new LinkedHashMap<>();

    private record Declaration(Element element, Entry entry) {}

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(Trans.class.getName(), TransVariants.class.getName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        for (var element : round.getElementsAnnotatedWith(Trans.class)) {
            var trans = element.getAnnotation(Trans.class);
            add(trans.modId(), trans.namespace(), trans.key(), trans.source(), trans.translations(), element, "@Trans " + location(element));
        }
        for (var element : round.getElementsAnnotatedWith(TransVariants.class)) {
            var variants = element.getAnnotation(TransVariants.class);
            for (var variant : variants.value()) {
                String key = variants.keyPrefix() + "." + variant.id();
                add(variants.modId(), variants.namespace(), key, variant.source(), variant.translations(), element,
                        "@TransVariants " + location(element) + " [" + variant.id() + "]");
            }
        }
        if (round.processingOver() && !round.errorRaised()) {
            declarations.forEach(this::write);
        }
        return true;
    }

    private void add(String modId, String namespace, String key, String source, Translation[] translations, Element element, String location) {
        var texts = new EnumMap<MinecraftLocale, String>(MinecraftLocale.class);
        for (var translation : translations) {
            texts.put(translation.locale(), translation.text());
        }
        var entry = new Entry(namespace.isEmpty() ? modId : namespace, key, source, location, texts);
        declarations.computeIfAbsent(modId, ignored -> new ArrayList<>()).add(new Declaration(element, entry));
    }

    private void write(String modId, ArrayList<Declaration> group) {
        var entries = group.stream().map(Declaration::entry)
                .sorted(Comparator.comparing(Entry::namespace).thenComparing(Entry::key).thenComparing(Entry::location)).toList();
        var origins = group.stream().map(Declaration::element).distinct().toArray(Element[]::new);
        String path = LocalizationIndex.path(modId);
        try {
            var resource = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", path, origins);
            try (var output = new DataOutputStream(new BufferedOutputStream(resource.openOutputStream()))) {
                LocalizationIndex.write(output, entries);
            }
        } catch (IOException error) {
            processingEnv.getMessager().printError("Cannot write localization index " + path + ": " + error.getMessage());
        }
    }

    private static String location(Element element) {
        if (element instanceof TypeElement type) {
            return type.getQualifiedName().toString();
        }
        var type = (TypeElement) element.getEnclosingElement();
        return type.getQualifiedName() + "#" + element.getSimpleName();
    }
}
