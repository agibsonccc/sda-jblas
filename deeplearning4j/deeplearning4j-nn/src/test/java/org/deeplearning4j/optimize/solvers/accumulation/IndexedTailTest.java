package org.deeplearning4j.optimize.solvers.accumulation;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;
import org.nd4j.linalg.factory.Nd4j;

import java.util.ArrayList;

import static org.junit.Assert.*;

@Slf4j
public class IndexedTailTest {

    @Test
    public void testDeltas_1() throws Exception {
        val tail = new IndexedTail(2);

        assertFalse(tail.hasAynthing(11));
        assertFalse(tail.hasAynthing(22));

        // 3 updates in queue
        tail.put(Nd4j.create(5, 5));
        tail.put(Nd4j.create(5, 5));
        tail.put(Nd4j.create(5, 5));

        assertEquals(3, tail.getDelta(11));
        assertEquals(3, tail.getDelta(22));


        tail.drainTo(22, Nd4j.create(5, 5));

        assertEquals(3, tail.getDelta(11));
        assertEquals(0, tail.getDelta(22));

        tail.put(Nd4j.create(5, 5));

        assertEquals(4, tail.getDelta(11));
        assertEquals(1, tail.getDelta(22));

        tail.drainTo(22, Nd4j.create(5, 5));
        tail.drainTo(11, Nd4j.create(5, 5));

        assertEquals(0, tail.getDelta(11));
        assertEquals(0, tail.getDelta(22));


        tail.put(Nd4j.create(5, 5));
        tail.put(Nd4j.create(5, 5));

        assertEquals(2, tail.getDelta(11));
        assertEquals(2, tail.getDelta(22));

        tail.drainTo(22, Nd4j.create(5, 5));

        assertEquals(2, tail.getDelta(11));
        assertEquals(0, tail.getDelta(22));
    }


    @Test
    public void testMaxAppliedIndex_1() {
        val tail = new IndexedTail(3);

        // "registering" 3 consumers
        assertFalse(tail.hasAynthing(11));
        assertFalse(tail.hasAynthing(22));
        assertFalse(tail.hasAynthing(33));

        // putting 10 updates in
        for (int e = 0; e < 10; e++) {
            tail.put(Nd4j.create(5, 5));
        }

        assertEquals(10, tail.updatesSize());

        assertEquals(-1, tail.maxAppliedIndexEverywhere());

        // 2 consumers consumed 2 elements, and 1 consumer consumed 3 elements
        tail.positions.get(11L).set(2);
        tail.positions.get(22L).set(2);
        tail.positions.get(33L).set(3);

        // all elements including this index are safe to remove, because they were consumed everywhere
        assertEquals(2, tail.maxAppliedIndexEverywhere());

        // only updates starting from 4 are safe to collapse, because 3 was consumed by one consumer
        assertEquals(4, tail.firstNotAppliedIndexEverywhere());

        // truncating stuff
        tail.maintenance();

        assertEquals(8, tail.updatesSize());
    }

    @Test
    public void testFirstNotApplied_1() {
        val tail = new IndexedTail(1);
        tail.hasAynthing();

        assertEquals(-1, tail.firstNotAppliedIndexEverywhere());

        tail.put(Nd4j.createUninitialized(5,5));

        assertEquals(0, tail.firstNotAppliedIndexEverywhere());

        tail.put(Nd4j.createUninitialized(5,5));
        tail.put(Nd4j.createUninitialized(5,5));

        assertEquals(0, tail.firstNotAppliedIndexEverywhere());

        assertTrue(tail.drainTo(Nd4j.create(5, 5)));

        assertEquals(4, tail.firstNotAppliedIndexEverywhere());
    }


    @Test
    public void testSingleThreaded_1() throws Exception {
        val tail = new IndexedTail(1);

        for (int e = 0; e < 100; e++) {
            val orig = Nd4j.create(5, 5).assign(e);
            tail.put(orig);
            Nd4j.getExecutioner().commit();

            assertTrue(tail.hasAynthing());

            val temp = Nd4j.create(5, 5);
            val status = tail.drainTo(temp);

            assertTrue(status);
            assertArrayEquals(orig.shape(), temp.shape());
            assertEquals(orig, temp);
        }

        assertEquals(0, tail.updatesSize());
    }

    @Test
    public void testSingleThreaded_2() throws Exception {
        val tail = new IndexedTail(1);

        for (int e = 0; e < 100; e++) {
            int numUpdates = RandomUtils.nextInt(1, 10);
            int sum = 0;

            for (int f = 1; f <= numUpdates; f++) {
                sum += f;
                val orig = Nd4j.create(5, 5).assign(f);
                tail.put(orig);
            }
            Nd4j.getExecutioner().commit();

            assertTrue(tail.hasAynthing());

            val temp = Nd4j.create(5, 5);
            val status = tail.drainTo(temp);

            assertTrue(status);
            assertEquals(sum, temp.meanNumber().intValue());
        }

        assertEquals(0, tail.updatesSize());
    }

    @Test
    public void testSingleThreaded_3() throws Exception {
        val tail = new IndexedTail(2, true, new long[]{5, 5});
        assertFalse(tail.hasAynthing());
        assertFalse(tail.hasAynthing(11));

        int sum = 0;
        for (int e = 0; e < 64; e++) {
            sum += (e+1);
            tail.put(Nd4j.createUninitialized(5,5).assign(e+1));
        }

        assertTrue(tail.collapsedMode.get());
        assertEquals(1, tail.updatesSize());
    }


    @Test
    public void testPseudoMultiThreaded_1() throws Exception {
        val tail = new IndexedTail(2);

        for (int e = 0; e < 100; e++) {
            // putting in one thread
            val orig = Nd4j.create(5, 5).assign(e);
            tail.put(orig);
            Nd4j.getExecutioner().commit();

            for (int t = 0; t < 2; t++) {
                assertTrue(tail.hasAynthing(t));

                val temp = Nd4j.create(5, 5);
                val status = tail.drainTo(t, temp);

                assertTrue(status);
                assertArrayEquals(orig.shape(), temp.shape());
                assertEquals(orig, temp);
            }
        }

        assertEquals(0, tail.updatesSize());
    }



    @Test
    public void testMultiThreaded_1() throws Exception {
        val numReaders = 4;
        final val tail = new IndexedTail(numReaders);

        val sums = new long[numReaders];
        val readers = new ArrayList<Thread>();
        for (int e = 0; e < numReaders; e++) {
            val f = e;
            val t = new Thread(new Runnable() {
                @Override
                public void run() {
                    sums[f] = 0;
                    while (!tail.isDead()) {
                        while (tail.hasAynthing()) {
                            val updates = Nd4j.create(5, 5);
                            tail.drainTo(updates);
                            val mean = (int) updates.getDouble(0);
                            sums[f] += mean;
                        }
                    }
                }
            });

            t.setName("reader thread " + f);
            t.start();
            readers.add(t);
        }


        int sum = 0;
        for (int e = 0; e < 10000; e++) {
            val array = Nd4j.create(5, 5).assign(e+1);
            Nd4j.getExecutioner().commit();

            sum += (e+1);
            tail.put(array);
        }
        // just wait till everything consumed
        Thread.sleep(2000);
        tail.notifyDead();


        for (val t:readers)
            t.join();


        for (int e = 0; e < numReaders; e++)
            assertEquals("Failed for reader [" + e + "]",sum, sums[e]);


        assertEquals(0, tail.updatesSize());
    }

    @Test
    public void testMultiThreaded_2() throws Exception {
        val numReaders = 4;
        val numWriters = 4;
        final val tail = new IndexedTail(numReaders);

        val sums = new long[numReaders];
        val readers = new ArrayList<Thread>();
        for (int e = 0; e < numReaders; e++) {
            val f = e;
            val t = new Thread(new Runnable() {
                @Override
                public void run() {
                    sums[f] = 0;
                    while (!tail.isDead()) {
                        while (tail.hasAynthing()) {
                            val updates = Nd4j.create(5, 5);
                            tail.drainTo(updates);
                            val mean = (int) updates.getDouble(0);
                            sums[f] += mean;
                        }
                    }
                }
            });

            t.setName("reader thread " + f);
            t.start();
            readers.add(t);
        }

        val writers = new ArrayList<Thread>();
        for (int e = 0; e < numWriters; e++) {
            val f = e;
            val t = new Thread(new Runnable() {
                @Override
                public void run() {
                    int sum = 0;
                    for (int e = 0; e < 1000; e++) {
                        val array = Nd4j.create(5, 5).assign(e+1);
                        Nd4j.getExecutioner().commit();

                        sum += (e+1);
                        tail.put(array);
                    }
                }
            });

            t.setName("writer thread " + f);
            t.start();
            writers.add(t);
        }



        for (val t:writers)
            t.join();

        // just wait till everything consumed
        Thread.sleep(2000);
        tail.notifyDead();



        for (val t:readers)
            t.join();


        for (int e = 0; e < numReaders; e++)
            assertEquals("Failed for reader [" + e + "]",500500 * numWriters, sums[e]);


        assertEquals(0, tail.updatesSize());
    }
}