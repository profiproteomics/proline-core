package fr.proline.core.orm.util;

/**
 * Listener for memory allocation of blocks of TransientDataInterface
 */
public interface TransientDataAllocationListener {
    void memoryAllocated(TransientDataInterface transientDataAllocated);
}
