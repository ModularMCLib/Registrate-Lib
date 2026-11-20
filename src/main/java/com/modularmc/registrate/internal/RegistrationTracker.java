package com.modularmc.registrate.internal;

import com.modularmc.registrate.internal.util.DebugMarkers;
import com.modularmc.registrate.internal.util.RegistrateLogger;
import com.modularmc.registrate.util.entry.EntryMetadata;
import com.modularmc.registrate.util.entry.RegistryEntry;
import com.modularmc.registrate.util.nullness.NonNullConsumer;
import com.modularmc.registrate.util.nullness.NonNullFunction;
import com.modularmc.registrate.util.nullness.NonNullSupplier;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class RegistrationTracker {

    private static final Logger LOGGER = RegistrateLogger.registration();

    private static final class Registration<R, T extends R> {

        private final ResourceLocation identifier;
        private final ResourceKey<? extends Registry<R>> registryKey;
        private final NonNullSupplier<? extends T> creator;
        private final RegistryEntry<R, T> delegate;
        private final EntryMetadata metadata;
        private final List<NonNullConsumer<? super T>> callbacks = new ArrayList<>();

        private Registration(
                             ResourceLocation identifier,
                             ResourceKey<? extends Registry<R>> registryKey,
                             NonNullSupplier<? extends T> creator,
                             NonNullFunction<DeferredHolder<R, T>, ? extends RegistryEntry<R, T>> entryFactory,
                             EntryMetadata metadata) {
            this.identifier = identifier;
            this.registryKey = registryKey;
            this.creator = creator.lazy();
            this.delegate = entryFactory.apply(DeferredHolder.create(registryKey, identifier));
            this.metadata = metadata;
        }

        private ResourceLocation getIdentifier() {
            return identifier;
        }

        private RegistryEntry<R, T> getDelegate() {
            return delegate;
        }

        private void register(RegisterEvent event) {
            String stage = "creation";
            try {
                T entry = creator.get();
                stage = "registry insertion";
                event.register(registryKey, helper -> helper.register(identifier, entry));
                stage = "entry callback";
                callbacks.forEach(callback -> callback.accept(entry));
                callbacks.clear();
            } catch (RuntimeException exception) {
                throw new IllegalStateException(
                        "[%s] Registration of %s in %s failed during %s"
                                .formatted(identifier.getNamespace(), identifier, registryKey.location(), stage),
                        exception);
            }
        }

        private void addRegisterCallback(NonNullConsumer<? super T> callback) {
            Preconditions.checkNotNull(callback, "Callback must not be null");
            callbacks.add(callback);
        }
    }

    private final String modId;
    private final Map<ResourceKey<? extends Registry<?>>, Object2ObjectLinkedOpenHashMap<String, Registration<?, ?>>> registrations = new Object2ObjectOpenHashMap<>();
    private final Multimap<Pair<String, ResourceKey<? extends Registry<?>>>, NonNullConsumer<?>> pendingEntryCallbacks = HashMultimap.create();
    private final Multimap<ResourceKey<? extends Registry<?>>, Runnable> afterRegisterCallbacks = HashMultimap.create();
    private final Set<ResourceKey<? extends Registry<?>>> completedRegistrations = new ObjectOpenHashSet<>();

    public RegistrationTracker(String modId) {
        this.modId = modId;
    }

    public void onRegister(RegisterEvent event, boolean skipErrors, boolean devEnvironment) {
        ResourceKey<? extends Registry<?>> registryKey = event.getRegistryKey();
        if (registryKey == null) {
            LOGGER.debug(
                    DebugMarkers.REGISTER,
                    RegistrateLogger.modMessage("Skipping registry event without a registry key"),
                    modId);
            return;
        }

        if (!pendingEntryCallbacks.isEmpty()) {
            pendingEntryCallbacks.asMap().forEach((entryKey, callbacks) -> LOGGER.warn(
                    RegistrateLogger.modMessage("Found {} unused register callback(s) for entry {} [{}]. Was the entry ever registered?"),
                    modId,
                    callbacks.size(),
                    entryKey.getLeft(),
                    entryKey.getRight().location()));
            pendingEntryCallbacks.clear();
            if (devEnvironment) {
                throw new IllegalStateException("Found unused register callbacks, see logs");
            }
        }

        var registrationsForType = registrations.get(registryKey);
        if (registrationsForType == null) {
            return;
        }

        LOGGER.trace(
                DebugMarkers.REGISTER,
                RegistrateLogger.modMessage("Registering {} captured object(s) in {}"),
                modId,
                registrationsForType.size(),
                registryKey.location());
        for (var registration : registrationsForType.values()) {
            try {
                registration.register(event);
                LOGGER.trace(
                        DebugMarkers.REGISTER,
                        RegistrateLogger.modMessage("Registered {} in {}"),
                        modId,
                        registration.getIdentifier(),
                        registryKey.location());
            } catch (IllegalStateException exception) {
                if (!skipErrors) {
                    throw exception;
                }
                LOGGER.error(DebugMarkers.REGISTER, exception.getMessage(), exception);
            }
        }
    }

    public void onRegisterLate(RegisterEvent event) {
        ResourceKey<? extends Registry<?>> registryKey = event.getRegistryKey();
        if (registryKey == null) {
            return;
        }

        completedRegistrations.add(registryKey);
        for (var callback : afterRegisterCallbacks.removeAll(registryKey)) {
            callback.run();
        }
    }

    public <R, T extends R> RegistryEntry<R, T> track(
                                                      String name,
                                                      ResourceKey<? extends Registry<R>> registryKey,
                                                      NonNullSupplier<? extends T> creator,
                                                      NonNullFunction<DeferredHolder<R, T>, ? extends RegistryEntry<R, T>> entryFactory,
                                                      EntryMetadata metadata) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(modId, name);
        var row = registrations.get(registryKey);
        if (row != null && row.containsKey(name)) {
            throw new IllegalArgumentException("[%s] Duplicate registration %s in %s".formatted(modId, id, registryKey.location()));
        }
        Registration<R, T> registration = new Registration<>(id, registryKey, creator, entryFactory, metadata);
        LOGGER.trace(
                DebugMarkers.REGISTER,
                RegistrateLogger.modMessage("Captured registration {} for registry {}"),
                modId,
                registration.getIdentifier(),
                registryKey.location());
        pendingEntryCallbacks.removeAll(Pair.of(name, registryKey)).forEach(callback -> {
            @SuppressWarnings("unchecked")
            NonNullConsumer<? super T> unsafeCallback = (NonNullConsumer<? super T>) callback;
            registration.addRegisterCallback(unsafeCallback);
        });
        registrations.computeIfAbsent(registryKey, key -> new Object2ObjectLinkedOpenHashMap<>()).put(name, registration);
        return registration.getDelegate();
    }

    public EntryMetadata getMetadata(ResourceKey<?> key) {
        return getRegistration(key.location().getPath(), key.registryKey()).metadata;
    }

    public <R, T extends R> void addRegisterCallback(
                                                     String name,
                                                     ResourceKey<? extends Registry<R>> registryKey,
                                                     NonNullConsumer<? super T> callback) {
        Registration<R, T> registration = getRegistrationUnchecked(name, registryKey);
        if (registration == null) {
            pendingEntryCallbacks.put(Pair.of(name, registryKey), callback);
            return;
        }
        registration.addRegisterCallback(callback);
    }

    public <R> void addAfterRegisterCallback(ResourceKey<? extends Registry<R>> registryKey, Runnable callback) {
        afterRegisterCallbacks.put((ResourceKey<? extends Registry<?>>) registryKey, callback);
    }

    public <R> boolean isRegistered(ResourceKey<? extends Registry<R>> registryKey) {
        return completedRegistrations.contains(registryKey);
    }

    @SuppressWarnings("unchecked")
    public <R, T extends R> RegistryEntry<R, T> get(String name, ResourceKey<? extends Registry<R>> registryKey) {
        return (RegistryEntry<R, T>) getRegistration(name, registryKey).getDelegate();
    }

    public <R, T extends R> Optional<RegistryEntry<R, T>> getOptional(
                                                                      String name,
                                                                      ResourceKey<? extends Registry<R>> registryKey) {
        Registration<R, T> registration = getRegistrationUnchecked(name, registryKey);
        return registration == null ? Optional.empty() : Optional.of(registration.getDelegate());
    }

    @SuppressWarnings("unchecked")
    public <R, T extends R> Collection<RegistryEntry<R, T>> getAll(ResourceKey<? extends Registry<R>> registryKey) {
        var row = registrations.get(registryKey);
        if (row == null) {
            return List.of();
        }
        return row
                .values()
                .stream()
                .map(registration -> (RegistryEntry<R, T>) registration.getDelegate())
                .collect(Collectors.toList());
    }

    private <R, T extends R> Registration<R, T> getRegistration(String name, ResourceKey<? extends Registry<R>> registryKey) {
        Registration<R, T> registration = getRegistrationUnchecked(name, registryKey);
        if (registration != null) {
            return registration;
        }
        throw new IllegalArgumentException("Unknown registration " + name + " for type " + registryKey.location());
    }

    @SuppressWarnings("unchecked")
    private <R, T extends R> Registration<R, T> getRegistrationUnchecked(
                                                                         String name,
                                                                         ResourceKey<? extends Registry<R>> registryKey) {
        var row = registrations.get(registryKey);
        return row == null ? null : (Registration<R, T>) row.get(name);
    }
}
