package de.tuberlin.aura.core.memory;

/**
 *
 */
public interface IAllocator {

    public abstract MemoryView alloc();

    public abstract MemoryView allocBlocking() throws InterruptedException;

    public abstract MemoryView alloc(final BufferCallback bufferCallback);

    public abstract void free(final MemoryView memory);

    public abstract boolean hasFree();

    public abstract int getBufferSize();

    public abstract boolean isNotUsed();
}
