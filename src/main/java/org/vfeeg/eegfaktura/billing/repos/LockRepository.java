package org.vfeeg.eegfaktura.billing.repos;

public interface LockRepository<K> {
    Object getLock(K key);
    void releaseLock(K key);
}
