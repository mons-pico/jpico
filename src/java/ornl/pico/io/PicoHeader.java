/*------------------------------------------------------------------------------
 *        _        
 *   _ __(_)__ ___ 
 *  | '_ \ / _/ _ \
 *  | .__/_\__\___/
 *  |_|            Pico
 * 
 * Copyright (c) 2012 by UT-Battelle, LLC.
 * All rights reserved.
 *----------------------------------------------------------------------------*/

package ornl.pico.io;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Encapsulate the content of a Pico wrapper file's header.
 * 
 * @author Stacy Prowell (prowellsj@ornl.gov)
 */
class PicoHeader implements PicoFileOffsets {
    /** The hash of the unencrypted data. */
    public byte[] hash = null;
    /** The key used to encrypt the data.  The key size is inferred. */
    public byte[] key = null;
    /** Specify the file offset to the data. */
    public long offset = 0L;
    
    /* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    public PicoHeader clone() {
    	// Make a new header instance and then populate it.
    	PicoHeader newheader = new PicoHeader();
    	newheader.hash = hash.clone();
    	newheader.key = key.clone();
    	newheader.offset = offset;
    	return newheader;
    }

	/**
	 * Populate the header from the provided data chunk.  This does not
	 * populate the key, since the key length is contained in the header
	 * block, but the key array is created with the correct length.
	 * <p>
	 * The length of the data must be at least
	 * {@link PicoFileOffsets#FIXED_HEADER_LENGTH}, or the header is invalid.
	 * 
	 * @param data				The data.
	 * @throws PicoException	The header format is invalid.
	 */
	static PicoHeader readHeader(byte[] data) throws PicoException {
		if (data == null) {
			throw new NullPointerException("The data is null.");
		}
		
		// Check the length of the data.
		if (data.length < PicoFileOffsets.FIXED_HEADER_LENGTH) {
			throw new PicoException("Header too short.");
		}
		
		// Check the magic string.
		if (!Arrays.equals(PicoFileOffsets.MAGIC, Arrays.copyOfRange(data,
				(int) PicoFileOffsets.MAGIC_OFFSET,
				(int) PicoFileOffsets.MAGIC_LENGTH +
				(int) PicoFileOffsets.MAGIC_OFFSET))) {
			// The magic string does not match.  Reject this file.
			throw new PicoException("Incorrect magic string found; not a " +
					"Pico file?");
		}
		
		// The magic string checks out.  Next we need to get the version.
		// The version consists of a specific number of bytes, stored in
		// network byte order.  This is a little over-built, but it allows
		// the maximum flexibility to re-order fields in crazy, perhaps 
		// insane, ways.
		short major = ByteBuffer.wrap(data,
				(int) PicoFileOffsets.MAJOR_OFFSET,
				(int) PicoFileOffsets.MAJOR_LENGTH)
				.order(PicoFileOffsets.ORDER)
				.getShort();
		short minor = ByteBuffer.wrap(data,
				(int) PicoFileOffsets.MINOR_OFFSET,
				(int) PicoFileOffsets.MINOR_LENGTH)
				.order(PicoFileOffsets.ORDER)
				.getShort();
		// Verify the version.  Pico is intended to be forward compatible, so
		// new versions can read old versions... but not necessarily the
		// reverse.  So we should be able to read any prior version.
		if (major > PicoFileOffsets.MAJOR &&
				(major == PicoFileOffsets.MAJOR && minor > PicoFileOffsets.MINOR)) {
			// The version of the file is past the version of this library.
			// Don't read it.
			throw new PicoException("File version ("+major+"."+minor+
					") cannot be read by this software ("+
					PicoFileOffsets.MAJOR+"."+PicoFileOffsets.MINOR+").");
		}
		// We don't store the version; the version written would be the version
		// of this library, not the one read.
		
		// Make a new header to hold the data.
		PicoHeader head = new PicoHeader();
		
		// Now that the magic string and the version have checked out, get the
		// offset.
		head.offset = ByteBuffer.wrap(data,
				(int) PicoFileOffsets.OFFSET_OFFSET,
				(int) PicoFileOffsets.OFFSET_LENGTH)
				.order(PicoFileOffsets.ORDER)
				.getInt();
		
		// Read the hash.
		head.hash = new byte[(int) PicoFileOffsets.HASH_LENGTH];
		System.arraycopy(data, (int) PicoFileOffsets.HASH_OFFSET,
				head.hash, 0, (int) PicoFileOffsets.HASH_LENGTH);
		
		// Get the key size.
		short keysize = ByteBuffer.wrap(data,
				(int) PicoFileOffsets.KEYSIZE_OFFSET,
				(int) PicoFileOffsets.KEYSIZE_LENGTH)
				.order(PicoFileOffsets.ORDER)
				.getShort();
		head.key = new byte[keysize];
		
		// Ka-presto!  Everything has been processed except the key.  The caller
		// must now get the key.
		return head;
	}
	
	/**
	 * Convert this header to a byte array, ready for writing.
	 * @return	The byte array to write.
	 */
	byte[] writeHeader() {
		// Allocate the array.
		byte[] data = new byte[(int) PicoFileOffsets.FIXED_HEADER_LENGTH +
		                       key.length];
		
		// Store the magic string.
		System.arraycopy(PicoFileOffsets.MAGIC, 0, data,
				(int) PicoFileOffsets.MAGIC_OFFSET,
				(int) PicoFileOffsets.MAGIC_LENGTH);
		
		// Store the version.
		ByteBuffer buf = ByteBuffer.allocate((int) PicoFileOffsets.MAJOR_LENGTH)
				.order(PicoFileOffsets.ORDER);
		buf.asShortBuffer().put(PicoFileOffsets.MAJOR);
		System.arraycopy(buf.array(), 0, data,
				(int) PicoFileOffsets.MAJOR_OFFSET,
				(int) PicoFileOffsets.MAJOR_LENGTH);
		buf = ByteBuffer.allocate((int) PicoFileOffsets.MINOR_LENGTH)
				.order(PicoFileOffsets.ORDER);
		buf.asShortBuffer().put(PicoFileOffsets.MINOR);
		System.arraycopy(buf.array(), 0, data,
				(int) PicoFileOffsets.MINOR_OFFSET,
				(int) PicoFileOffsets.MINOR_LENGTH);
		
		// Store the offset.
		buf = ByteBuffer.allocate((int) PicoFileOffsets.KEYSIZE_LENGTH)
				.order(PicoFileOffsets.ORDER);
		buf.asShortBuffer().put((short) key.length);
		System.arraycopy(buf.array(), 0, data,
				(int) PicoFileOffsets.KEYSIZE_OFFSET,
				(int) PicoFileOffsets.KEYSIZE_LENGTH);
		
		// If the offset is still zero, fix it.  Assume no metadata for now.
		if (offset <= 0) offset = PicoFileOffsets.KEY_OFFSET + key.length;
		
		// Store the key size.
		buf = ByteBuffer.allocate((int) PicoFileOffsets.OFFSET_LENGTH)
				.order(PicoFileOffsets.ORDER);
		buf.asIntBuffer().put((int) offset);
		System.arraycopy(buf.array(), 0, data,
				(int) PicoFileOffsets.OFFSET_OFFSET,
				(int) PicoFileOffsets.OFFSET_LENGTH);
		
		// Store the hash.
		System.arraycopy(hash, 0, data,
				(int) PicoFileOffsets.HASH_OFFSET,
				(int) PicoFileOffsets.HASH_LENGTH);
		
		// Store the key.
		System.arraycopy(key, 0, data,
				(int) PicoFileOffsets.KEY_OFFSET, key.length);
		
		// Done.  Return the array.
		return data;
	}
}
