package com.aircargo.common.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guard de regresión para el bug de colisión de tipos de caché (AGENTS.md "Sep 2, 2026 (5)").
 *
 * <p>Simula lo que ocurría en {@code WarehouseReceiptServiceImpl.getAll} (List) y
 * {@code WarehouseServiceImpl.getPieces} (otra List) compartiendo la caché {@code warehouse-receipts}
 * con la misma clave por defecto, y cómo {@code getById} leía de esa caché esperando un DTO único.
 * Con {@link TypeSafeCacheManager} un valor de tipo inconsistente se descarta (miss) en vez de
 * propagarse y romper el controller.
 */
class TypeSafeCacheTest {

    private CacheManager buildCaffeineCacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager("warehouse-receipts", "receipt-pieces");
        mgr.setCaffeine(Caffeine.newBuilder());
        return mgr;
    }

    @Test
    void typeMismatchIsTreatedAsCacheMissInsteadOfClassCastException() {
        CacheManager raw = buildCaffeineCacheManager();
        CacheManager safe = new TypeSafeCacheManager(raw);
        Cache cache = safe.getCache("warehouse-receipts");

        // Método A (lista de DTOs de recibo) puebla la caché con la clave por defecto.
        cache.put("shared-key", List.of("dto-a", "dto-b"));

        // Método B esperaba un único String: el valor almacenado es una List, no un String.
        // Antes → ClassCastException. Ahora → null (miss) + el valor basura se desaloja.
        String value = cache.get("shared-key", String.class);
        assertNull(value, "debe tratarse como cache-miss");

        // El valor inconsistente se desaloja para no envenenar lectores futuros.
        assertNull(raw.getCache("warehouse-receipts").get("shared-key"));
    }

    @Test
    void matchingTypeIsReturnedNormally() {
        CacheManager safe = new TypeSafeCacheManager(buildCaffeineCacheManager());
        Cache cache = safe.getCache("warehouse-receipts");

        cache.put("k", "hola");
        assertEquals("hola", cache.get("k", String.class));

        List<String> list = List.of("a", "b");
        cache.put("kl", list);
        assertInstanceOf(List.class, cache.get("kl", List.class));
        assertEquals(list, cache.get("kl", List.class));
    }

    @Test
    void typeSafeManagerWrapsAndDelegates() {
        CacheManager safe = new TypeSafeCacheManager(buildCaffeineCacheManager());
        assertInstanceOf(TypeSafeCache.class, safe.getCache("warehouse-receipts"));
        assertEquals(2, safe.getCacheNames().size());
        assertTrue(safe.getCacheNames().contains("warehouse-receipts"));
        assertTrue(safe.getCacheNames().contains("receipt-pieces"));
    }
}