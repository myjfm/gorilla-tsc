package fi.iki.yak.ts.compression.gorilla;

import java.math.BigInteger;
import java.nio.ByteBuffer;

/**
 * An implementation of BitOutput interface that uses off-heap storage.
 *
 * @author Michael Burman
 */
public class LongOutputProto implements BitOutput2 {
    public static final int DEFAULT_ALLOCATION =  4096*32;

    private long[] longArray;
    private long lB;
    private int position = 0;
    private int bitsLeft = Long.SIZE;

    private static long[] MASK_ARRAY;

    // Java does not allow creating 64 bit masks with (1L << 64) - 1; (end result is 0)
    // TODO Mention in the thesis (problems in the Java land)
    static {
        MASK_ARRAY = new long[64];
        long mask = 1;
        long value = 0;
        for (int i = 0; i < MASK_ARRAY.length; i++) {
            value = value | mask;
            mask = mask << 1;

            MASK_ARRAY[i] = value;
        }
    }


    /**
     * Creates a new ByteBufferBitOutput with a default allocated size of 4096 bytes.
     */
    public LongOutputProto() {
        this(DEFAULT_ALLOCATION);
    }

    /**
     * Give an initialSize different than DEFAULT_ALLOCATIONS. Recommended to use values which are dividable by 4096.
     *
     * @param initialSize New initialsize to use
     */
    public LongOutputProto(int initialSize) {
        longArray = new long[initialSize];
        lB = longArray[position];
    }

    private void expandAllocation() {
        long[] largerArray = new long[longArray.length*2];
        System.arraycopy(longArray, 0, largerArray, 0, longArray.length);
        longArray = largerArray;
    }

    private void checkAndFlipByte() {
        // Wish I could avoid this check in most cases...
        if(bitsLeft == 0) {
            System.out.printf("Check is flipping to next long\n");
            flipByte();
        }
    }

    private void flipByte() {
        System.out.printf("FlipByte is flipping to next long\n");
        longArray[position] = lB;
        String format = String.format("%064d", new BigInteger(Long.toBinaryString(longArray[position])));
        System.out.printf("longArray[%d]->%s\n", position, format);
        ++position;
        if(position >= (longArray.length - 2)) { // We want to have always at least 2 longs available
            expandAllocation();
        }
        lB = 0;
        bitsLeft = Long.SIZE;
    }

    private void flipByteWithoutExpandCheck() {
        System.out.printf("Noexpand is flipping to next long\n");
        longArray[position] = lB;
        String format = String.format("%064d", new BigInteger(Long.toBinaryString(longArray[position])));
        System.out.printf("longArray[%d]->%s\n", position, format);
        ++position;
        lB = 0; // Do I need even this?
        bitsLeft = Long.SIZE;
    }

    /**
     * Sets the next bit (or not) and moves the bit pointer.
     */
    public void writeBit() {
        System.out.printf("writeBit, bitsLeft->%d, mask->%s\n", bitsLeft, Long.toBinaryString((1L << (bitsLeft - 1))));
        lB |= (1L << (bitsLeft - 1)); // A table lookup for mask is faster.. I think? Test.
        bitsLeft--;
        checkAndFlipByte();
    }

    public void skipBit() {
        System.out.printf("skipBit, bitsLeft->%d\n", bitsLeft);
        bitsLeft--;
        checkAndFlipByte();
    }

    /**
     * Writes the given long to the stream using bits amount of meaningful bits. This command does not
     * check input values, so if they're larger than what can fit the bits (you should check this before writing),
     * expect some weird results.
     *
     * @param value Value to be written to the stream
     * @param bits How many bits are stored to the stream
     */
    public void writeBits(long value, int bits) {
        // TODO Could turn this to a branchless switch also .. worth it? Compare bits & bitsLeft first and then
        // a switch clause for -1 and 0

        // At least predictable speed..

        System.out.printf("writeBits value->%d, bits->%d, bitsLeft->%d, position->%d", value, bits, bitsLeft, position);

        if(bits <= bitsLeft) {
            int lastBitPosition = bitsLeft - bits;
            System.out.printf(", selected first part of the loop, lastBitPosition->%d\n", lastBitPosition);
            lB |= (value << lastBitPosition) & MASK_ARRAY[bitsLeft - 1];
            bitsLeft -= bits;
            checkAndFlipByte(); // We could be at 0 bits left because of the <= condition .. would it be faster with
                                // the other one?
        } else {
            value &= MASK_ARRAY[bits - 1]; // TODO turn to unsigned first - but we don't want that in the future
            int firstBitPosition = bits - bitsLeft;
            System.out.printf(", selected second part of the loop, mask->%s, value->%s, firstBitPosition->%d", Long
                    .toBinaryString(MASK_ARRAY[bits - 1]), Long.toBinaryString(value), firstBitPosition);
            lB |= value >>> firstBitPosition;
            System.out.printf(", firstWrite->%s\n", Long.toBinaryString(value >>> firstBitPosition));
            bits -= bitsLeft;
            flipByteWithoutExpandCheck();
            lB |= value << (64 - bits);
            System.out.printf("secondWrite->%s, bits->%d\n", Long.toBinaryString(value << (64 - bits)), bits);
            bitsLeft -= bits;
        }
    }

    /**
     * Causes the currently handled byte to be written to the stream
     */
    @Override
    public void flush() {
        flipByte(); // Causes write to the ByteBuffer
    }

    public void reset() {
        position = 0;
        bitsLeft = Long.SIZE;
        lB = 0;
    }

    /**
     * Returns the underlying DirectByteBuffer
     *
     * @return ByteBuffer of type DirectByteBuffer
     */
    public ByteBuffer getByteBuffer() {
//        LongBuffer wrap = LongBuffer.wrap(longArray, 0, position);
        ByteBuffer bb = ByteBuffer.allocate(position * 8);
        bb.asLongBuffer().put(longArray, 0, position);
        bb.position(position * 8);
        return bb;
    }

    public long[] getLongArray() {
        long[] copy = new long[position+1];
        System.arraycopy(longArray, 0, copy, 0, position);
        return copy;
    }
//    public ByteBuffer getByteBuffer() {
//        return this.bb;
//    }
}
