package com.aircargo.authservice.service;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveSessionTrackerTest {

    @Test
    void heartbeat_mantieneLaSesionViva() {
        ActiveSessionTracker tracker = new ActiveSessionTracker(120, 300);
        UUID uid = UUID.randomUUID();
        tracker.recordHeartbeat(uid, "a@b.c", "A", "ADMIN", OffsetDateTime.now());
        assertTrue(tracker.isAlive(uid));
    }

    @Test
    void sinHeartbeat_laSesionNoEstaViva() {
        ActiveSessionTracker tracker = new ActiveSessionTracker(120, 300);
        assertFalse(tracker.isAlive(UUID.randomUUID()));
    }

    @Test
    void heartbeatViejo_laSesionExpira() throws InterruptedException {
        ActiveSessionTracker tracker = new ActiveSessionTracker(1, 300);
        UUID uid = UUID.randomUUID();
        tracker.recordHeartbeat(uid, "a@b.c", "A", "ADMIN", OffsetDateTime.now());
        Thread.sleep(1_200);
        assertFalse(tracker.isAlive(uid));
    }

    @Test
    void heartbeatFresco_reapareceEnLaListaDeConectados() {
        ActiveSessionTracker tracker = new ActiveSessionTracker(120, 300);
        UUID uid = UUID.randomUUID();
        tracker.recordHeartbeat(uid, "a@b.c", "A", "ADMIN", OffsetDateTime.now());
        assertTrue(tracker.getConnectedUsers().stream().anyMatch(c -> c.getUserId().equals(uid)));
    }

    @Test
    void navegadorCerrado_laSesionCaeEnStaleYPurgeLaElimina() throws InterruptedException {
        ActiveSessionTracker tracker = new ActiveSessionTracker(1, 1);
        UUID uid = UUID.randomUUID();
        tracker.recordHeartbeat(uid, "a@b.c", "A", "ADMIN", OffsetDateTime.now());
        Thread.sleep(1_600);

        List<UUID> stale = tracker.staleUserIds();
        assertEquals(List.of(uid), stale);

        List<UUID> removed = tracker.purgeStaleSessions();
        assertEquals(List.of(uid), removed);
        assertFalse(tracker.isAlive(uid));
        assertTrue(tracker.getConnectedUsers().isEmpty());
        assertTrue(tracker.staleUserIds().isEmpty());
    }
}