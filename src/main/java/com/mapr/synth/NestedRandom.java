package com.mapr.synth;

import com.google.common.base.Charsets;
import org.apache.mahout.math.MurmurHash;

import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Defines a tree of random number generators such that complex randomized structures can be
 * built in parallel in a deterministic way. The based idea is that a NestedRandom can produce
 * off-shoot NestedRandom generators given either an index or a String. You can also get a
 * conventional Random from a NestedRandom at any time.  The idea is that a data structure
 * can have components which need stable randomness sources and those components will also have
 * components. At the leaves of such a JSON-style nested data structure, you have conventional
 * random number generators seeded consistently given the path down to that generator.
 *
 * Note that NestedRandom instances are immutable and do not share any storage. They are
 * fully thread safe.
 */
public class NestedRandom implements Iterable<NestedRandom> {
    private NestedRandom parent;
    private final byte[] content;

    /**
     * Returns a NestedRandom with a default seed.
     */
    public NestedRandom() {
        this(0);
    }

    /**
     * Returns a NestedRandom with a specified seed.
     * @param seed  The seed to use.
     */
    public NestedRandom(int seed) {
        this(null, seed);
    }

    /**
     * Get a NestedRandom corresponding to the component with the specified name.
     * @param component  Which component to return.
     * @return A component stream.
     */
    public NestedRandom get(String component) {
        return new NestedRandom(this, component);
    }

    /**
     * Get a NestedRandom corresponding to a specified position in the notional array
     * of random generators with index i.
     * @param i  Which component to get.
     * @return The requested component.
     */
    public NestedRandom get(int i) {
        return new NestedRandom(this, i);
    }

    /**
     * Returns an iterator through the same generators indexed using @get(int).
     * @return An iterator that returns all of the integer addressable components.
     */
    public Iterator<NestedRandom> iterator() {
        return new Iterator<NestedRandom>() {
            AtomicInteger i = new AtomicInteger(-1);

            public boolean hasNext() {
                return true;
            }

            public NestedRandom next() {
                return NestedRandom.this.get(i.incrementAndGet());
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("Default operation");
            }
        };
    }

    /**
     * Return the random number generator associated with the current NestedRandom component.
     */
    public Random random() {
        return new Random(hash(0));
    }

    private NestedRandom(NestedRandom parent, int value) {
        this.parent = parent;
        content = new byte[4];

        int i = 0;
        content[i++] = (byte) (value & 0xff);
        value = value >> 8;
        content[i++] = (byte) (value & 0xff);
        value = value >> 8;
        content[i++] = (byte) (value & 0xff);
        value = value >> 8;
        content[i] = (byte) (value & 0xff);
    }

    private NestedRandom(NestedRandom parent, String component) {
        this.parent = parent;
        content = component.getBytes(Charsets.UTF_8);
    }

    private long hash(long seed) {
        // first we hash in our content
        int seed1 = MurmurHash.hash(content, (int) seed);
        int seed2 = MurmurHash.hash(content, (int) (seed >> 32));

        seed = seed1 + (((long) seed2) << 32);
        if (parent != null) {
            // then our parent's content
            seed = parent.hash(seed);
        }

        // result is the seed
        return seed;
    }
}