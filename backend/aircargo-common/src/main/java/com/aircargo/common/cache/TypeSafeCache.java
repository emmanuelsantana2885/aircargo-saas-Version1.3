package com.aircargo.common.cache;

import org.springframework.cache.Cache;
import org.springframework.lang.Nullable;

import java.util.concurrent.Callable;

/**
 * Wrapper de {@link Cache} que valida el tipo del valor al leerlo y descarta valores
 * de un tipo no esperado (tratados como cache-miss) en lugar de permitir un
 * {@link ClassCastException} posterior en el controller. Ver {@link TypeSafeCacheManager}.
 */
public class TypeSafeCache implements Cache {

    private final Cache delegate;

    public TypeSafeCache(Cache delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Object getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    @Nullable
    public ValueWrapper get(Object key) {
        ValueWrapper wrapper = delegate.get(key);
        return wrapper; // sin tipo esperado no podemos validar; se valida en la sobrecarga con tipo
    }

    @Override
    @Nullable
    public <T> T get(Object key, @Nullable Class<T> type) {
        if (type == null || type == Object.class) {
            // No hay tipo útil que validar; Spring delega igual en el cache subyacente.
            return delegate.get(key, type);
        }
        ValueWrapper wrapper = delegate.get(key);
        if (wrapper == null) {
            return null;
        }
        Object value = wrapper.get();
        if (value == null) {
            // Valor "nulo cachead" (Spring permite cachear null) — no hay tipo que validar.
            return null;
        }
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        // Tipo inconsistente en esta caché: no es el que espera el método @Cacheable.
        // Se desaloja (el valor es basura para cualquier lector) y se trata como miss.
        delegate.evict(key);
        return null;
    }

    @Override
    @Nullable
    public <T> T get(Object key, Callable<T> valueLoader) {
        return delegate.get(key, valueLoader);
    }

    @Override
    public void put(Object key, @Nullable Object value) {
        delegate.put(key, value);
    }

    @Override
    @Nullable
    public ValueWrapper putIfAbsent(Object key, @Nullable Object value) {
        return delegate.putIfAbsent(key, value);
    }

    @Override
    public void evict(Object key) {
        delegate.evict(key);
    }

    @Override
    public void clear() {
        delegate.clear();
    }
}