package org.vfeeg.eegfaktura.billing.repos;

public interface LockRepository<K> {
    public Object getLock(K key);
    public void releaseLock(K key);
}
