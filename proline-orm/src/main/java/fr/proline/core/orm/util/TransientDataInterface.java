package fr.proline.core.orm.util;

/**
 * Transient memory blocks which can be freed.
 */
public interface TransientDataInterface {
    void clearMemory();
    String getMemoryName(String additionalName);
}
