package com.aircargo.common.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CacheManager decorador que envuelve cada caché en un {@link TypeSafeCache}.
 *
 * <p>Mitiga de raíz la clase de bug documentada en AGENTS.md (colisión de tipos en una misma
 * caché Caffeine: dos métodos con tipos de retorno incompatibles guardando bajo la misma clave).
 * Caffeine es type-erased (clave → Object), así que un valor del tipo equivocado se devolvía
 * "tal cual" y el proxy de {@code @Cacheable} lo casteaba al tipo declarado del método → {@link
 * ClassCastException} en el controller (p. ej. {@code GET /api/receipts/{id}}).
 *
 * <p>Con este wrapper, al leer una caché se comprueba que el valor almacenado sea asignable al
 * tipo esperado; si no lo es, se desaloja ese valor (el tipo es inconsistente y no servirá nunca)
 * y se devuelve {@code null} (cache-miss) para que el método vuelva a calcular. Con esto, un
 * nombre de caché compartido por error deja de lanzar errores y simplemente se comporta como
 * caché deshabilitada para la entrada en conflicto.
 */
public class TypeSafeCacheManager implements CacheManager {

    private final CacheManager delegate;
    private final Map<String, Cache> caches = new ConcurrentHashMap<>();

    public TypeSafeCacheManager(CacheManager delegate) {
        this.delegate = delegate;
    }

    @Override
    public Cache getCache(String name) {
        return caches.computeIfAbsent(name, n -> {
            Cache underlying = delegate.getCache(n);
            return underlying != null ? new TypeSafeCache(underlying) : null;
        });
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }
}