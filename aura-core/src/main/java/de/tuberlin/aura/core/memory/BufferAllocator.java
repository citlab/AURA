package de.tuberlin.aura.core.memory;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tuberlin.aura.core.memory.spi.IAllocator;
import de.tuberlin.aura.core.memory.spi.IBufferCallback;


public final class BufferAllocator implements IAllocator {

    // ---------------------------------------------------
    // Constants.
    // ---------------------------------------------------

    private final static Logger LOG = LoggerFactory.getLogger(BufferAllocator.class);

    enum BufferSize {

        _8K(1024 * 8),

        _16K(1024 * 16),

        _32K(1024 * 32),

        _64K(1024 * 64);

        BufferSize(final int bytes) { this.bytes = bytes; }

        public final int bytes;
    }

    // ---------------------------------------------------
    // Fields.
    // ---------------------------------------------------

    private final int bufferSize;

    public final int bufferCount;

    private final byte[] memoryArena;

    public final BlockingQueue<MemoryView> freeList;

    //public final Map<MemoryView,StackTraceElement[]> usedMap;

    private final LinkedList<IBufferCallback> callbackList;

    private final Object callbackLock = new Object();

    // ---------------------------------------------------
    // Constructor.
    // ---------------------------------------------------

    public BufferAllocator(final int bufferSize, final int bufferCount) {

        // sanity check.
        if ((bufferSize & (bufferSize - 1)) != 0 && bufferSize < BufferSize._8K.bytes && bufferSize > BufferSize._64K.bytes)
            throw new IllegalArgumentException("illegal buffer size");
        if (bufferCount <= 0)
            throw new IllegalArgumentException("bufferCount <= 0");
        if ((bufferCount * bufferSize) % BufferSize._64K.bytes != 0)
            throw new IllegalArgumentException("allocated memory must be a multiple of 64K");

        this.bufferSize = bufferSize;

        this.bufferCount = bufferCount;

        this.memoryArena = new byte[bufferSize * bufferCount];

        this.freeList = new LinkedBlockingQueue<>();

        //this.usedMap = new HashMap<>();

        this.callbackList = new LinkedList<>();

        for (int i = 0; i < bufferCount; ++i) {
            this.freeList.add(new MemoryView(this, memoryArena, i * bufferSize, bufferSize));
        }
    }

    // ---------------------------------------------------
    // Public Methods: IAllocator Interface.
    // ---------------------------------------------------

    @Override
    public MemoryView alloc() {
        final MemoryView buffer = freeList.poll();
        if (buffer != null) {
            buffer.retain();
            //usedMap.put(buffer, Thread.currentThread().getStackTrace());
        }
        return buffer;
    }

    @Override
    public MemoryView alloc(final IBufferCallback callback) {
        // sanity check.
        if (callback == null)
            throw new IllegalArgumentException("callback == null");

        synchronized (callbackLock) {
            final MemoryView buffer = freeList.poll();
            if (buffer == null) {
                callbackList.add(callback);
            } else {
                buffer.retain();
                //usedMap.put(buffer, Thread.currentThread().getStackTrace());
            }
            return buffer;
        }
    }

    @Override
    public MemoryView allocBlocking() throws InterruptedException {
        MemoryView buffer = freeList.poll(10, TimeUnit.SECONDS);
        if (buffer == null) {
            logStatus();
            buffer = freeList.take();
            //usedMap.put(buffer, Thread.currentThread().getStackTrace());
        }
        if (buffer != null) {
            buffer.retain();
            //usedMap.put(buffer, Thread.currentThread().getStackTrace());
        }

        return buffer;
    }

    @Override
    public void free(final MemoryView buffer) {
        // sanity check.
        if (buffer == null)
            throw new IllegalArgumentException("buffer == null");

        synchronized (callbackLock) {
            if (!callbackList.isEmpty()) {
                final IBufferCallback bufferCallback = callbackList.poll();
                buffer.retain();
                //usedMap.put(buffer, Thread.currentThread().getStackTrace());
                bufferCallback.bufferReader(buffer);
            } else {
                freeList.add(buffer);
                //usedMap.remove(buffer);
            }
        }
    }

    @Override
    public boolean hasFree() {
        return !freeList.isEmpty();
    }

    @Override
    public int getBufferSize() {
        return bufferSize;
    }

    @Override
    public boolean isNotUsed() {
        return false;
    }

    @Override
    public void checkForMemoryLeaks() {
        if (freeList.size() != bufferCount) {
            throw new IllegalStateException( (bufferCount - freeList.size()) + " buffers are not freed. callbackList.size = " + callbackList.size() /*+ " usedMap: " + usedMap*/);
        }

        for (final MemoryView buffer : freeList) {
            if (buffer.getRefCount() != 0 ) {
                throw new IllegalStateException("Reference count of buffer is not zero.");
            }
        }
    }

    @Override
    public int getBufferCount() {
        return this.bufferCount;
    }

    // ---------------------------------------------------
    // Private Methods.
    // ---------------------------------------------------

    private void logStatus() {
        LOG.info("buffer count :" + bufferCount);
        LOG.info("freelist size: " + freeList.size());
        LOG.info("callbacks    : " + callbackList.size());
    }
}
