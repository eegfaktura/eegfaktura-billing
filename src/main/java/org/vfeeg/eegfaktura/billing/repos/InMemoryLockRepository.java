package org.vfeeg.eegfaktura.billing.repos;

import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Eine generische Klasse zur Verwaltung von In-Memory-Locks, die mit einem Zeitstempel
 * arbeiten.
 *
 * @TODO Sollte der BillingService horizontal skaliert werden. Dann muss diese
 * Lösung natürlich in einer verteilte Lösung umgebaut werden (zB Speichern der
 * Locks in der DB oder Verwendung anderer Lösungen ZooKeeper oder Redis o.ä.
 */
@Repository
public class InMemoryLockRepository implements LockRepository<String> {

    private final Map<String, LockWithTimestamp> locks = new ConcurrentHashMap<>();
    private final long EXPIRATION_MINUTES = 15;

    /**
     * Holt ein Lock für den gegebenen Schlüssel. Wenn der Lock nicht existiert oder abgelaufen ist,
     * wird ein neuer Lock erstellt.
     */
    public Object getLock(String key) {
        locks.compute(key, (k, currentLock) -> {
            if (currentLock == null || currentLock.isExpired(EXPIRATION_MINUTES)) {
                return new LockWithTimestamp();
            }
            return currentLock;
        });
        return locks.get(key).getLock();
    }

    /**
     * Entfernt den Lock für den gegebenen Schlüssel.
     */
    public void releaseLock(String key) {
        locks.remove(key);
    }

    /**
     * Startet einen Hintergrund-Thread, der automatisch abgelaufene Locks regelmäßig entfernt.
     */
    private void startCleanupTask() {
        Thread cleanupThread = new Thread(() -> {
            while (true) {
                try {
                    // Alle 5 Minuten abgelaufene Locks entfernen
                    Thread.sleep(300000); // 5 Minuten
                    cleanupExpiredLocks();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    /**
     * Entfernt alle abgelaufenen Locks aus der Map.
     */
    private void cleanupExpiredLocks() {
        Iterator<Map.Entry<String, LockWithTimestamp>> iterator = locks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, LockWithTimestamp> entry = iterator.next();
            if (entry.getValue().isExpired(EXPIRATION_MINUTES)) {
                iterator.remove();
            }
        }
    }

    /**
     * Innere Klasse, die ein Lock-Objekt mit einem Zeitstempel kombiniert.
     */
    private static class LockWithTimestamp {
        private final Object lock = new Object();
        private final LocalDateTime timestamp;

        public LockWithTimestamp() {
            this.timestamp = LocalDateTime.now();
        }

        public Object getLock() {
            return lock;
        }

        public boolean isExpired(long expirationMinutes) {
            return timestamp.plusMinutes(expirationMinutes).isBefore(LocalDateTime.now());
        }
    }
}