package org.deeplearning4j.optimize.solvers.accumulation;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.compression.ThresholdCompression;
import org.nd4j.linalg.exception.ND4JIllegalStateException;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.AtomicBoolean;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
public class IndexedTail {
    // here we store positions of individual consumers
    protected ConcurrentHashMap<Long, AtomicLong> positions = new ConcurrentHashMap<>();

    // here we store individual updates
    protected Map<Long, INDArray> updates = new ConcurrentHashMap<>();

    // simple counter for new updates
    protected AtomicLong updatesCounter = new AtomicLong(0);

    protected AtomicLong lastDeletedIndex = new AtomicLong(-1);

    protected final int expectedConsumers;

    protected AtomicBoolean dead = new AtomicBoolean(false);

    protected ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public IndexedTail(int expectedConsumers) {
        this.expectedConsumers = expectedConsumers;
    }

    /**
     * This mehtod adds update, with optional collapse
     * @param update
     */
    public void put(@NonNull INDArray update) {
        try {
            lock.writeLock().lock();

            updates.put(updatesCounter.getAndIncrement(), update);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean hasAynthing() {
        return hasAynthing(Thread.currentThread().getId());
    }

    /**
     *
     * @return
     */
    public boolean hasAynthing(long threadId) {
        var threadPosition = positions.get(threadId);

        // will be instantiated on first call from any given thread
        if (threadPosition == null) {
            threadPosition = new AtomicLong(0);
            positions.put(threadId, threadPosition);
        }


        return threadPosition.get() < updatesCounter.get();
    }

    public boolean drainTo(@NonNull INDArray array) {
        return drainTo(Thread.currentThread().getId(), array);
    }

    protected long getGlobalPosition() {
        try {
            lock.readLock().lock();

            return updatesCounter.get();
        } finally {
            lock.readLock().unlock();
        }
    }

    protected long getLocalPosition() {
        return getLocalPosition(Thread.currentThread().getId());
    }

    protected long getDelta() {
        return getDelta(Thread.currentThread().getId());
    }

    protected long getDelta(long threadId) {
        return getGlobalPosition() - getLocalPosition(threadId);
    }

    protected long getLocalPosition(long threadId) {
        var threadPosition = positions.get(threadId);

        // will be instantiated on first call from any given thread
        if (threadPosition == null) {
            threadPosition = new AtomicLong(0);
            positions.put(threadId, threadPosition);
        }

        return threadPosition.get();
    }

    public boolean drainTo(long threadId, @NonNull INDArray array) {
        var threadPosition = positions.get(threadId);

        // will be instantiated on first call from any given thread
        if (threadPosition == null) {
            threadPosition = new AtomicLong(0);
            positions.put(threadId, threadPosition);
        }

        long globalPos = 0;

        try {
            lock.readLock().lock();

            globalPos = updatesCounter.get();
        } finally {
            lock.readLock().unlock();
        }
        val localPos = threadPosition.get();

        // we're finding out, how many arrays we should provide
        val delta = getDelta(threadId);

        // now we decompress all arrays within delta into provided array
        for (long e = localPos; e < localPos + delta; e++) {
            val update = updates.get(e);

            if (update == null) {
                log.info("Global: [{}]; Local: [{}]", globalPos, localPos);
                throw new RuntimeException("Element [" + e + "] is absent");
            }

            smartDecompress(update.unsafeDuplication(true), array);
        }

        // and shifting stuff by one
        threadPosition.addAndGet(delta);

        // TODO: this call should be either synchronized, or called from outside
        maintenance();

        return delta > 0;
    }

    /**
     * This method does maintenance of updates within
     */
    protected synchronized void maintenance() {
        // first of all we're checking, if all consumers were already registered. if not - just no-op.
        if (positions.size() < expectedConsumers)
            return;

        // now we should get minimal id of consumed update
        long minIdx = Long.MAX_VALUE;
        for (val v:positions.values()) {
            if (v.get() < minIdx)
                minIdx = v.get();
        }

        // now we're checking, if there are undeleted updates between
        if (minIdx > lastDeletedIndex.get()) {
            // delete everything between them
            for (long e = lastDeletedIndex.get(); e < minIdx; e++) {
                updates.remove(e);
            }

            // now, making sure we won't try to delete stuff twice
            lastDeletedIndex.set(minIdx);
        }
    }

    /**
     * This method returns actual number of updates stored within tail
     * @return
     */
    protected int updatesSize() {
        return updates.size();
    }

    protected INDArray smartDecompress(INDArray encoded, @NonNull INDArray target) {
        INDArray result = target;

        if (encoded.isCompressed() || encoded.data().dataType() == DataBuffer.Type.INT) {
            int encoding = encoded.data().getInt(3);
            if (encoding == ThresholdCompression.FLEXIBLE_ENCODING) {
                Nd4j.getExecutioner().thresholdDecode(encoded, result);
            } else if (encoding == ThresholdCompression.BITMAP_ENCODING) {
                Nd4j.getExecutioner().bitmapDecode(encoded, result);
            } else
                throw new ND4JIllegalStateException("Unknown encoding mode: [" + encoding + "]");
        } else {
            result.addi(encoded);
        }

        return result;
    }

    protected boolean isDead() {
        return dead.get();
    }

    protected void notifyDead() {
        dead.set(true);
    }
}
