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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import ornl.pico.PicoException;

/**
 * Encapsulate the content of a Pico wrapper file's header.
 * <p>
 * The header consists of two parts.  The "fixed" header is the fixed length
 * portion of the header that runs up to the key, and includes the key length.
 * The entire header includes the variable length key.  This distinction is
 * occasionally important.
 * <p>
 * See {@link PicoStructure} for the structure of the header.
 * 
 * @author Stacy Prowell (prowellsj@ornl.gov)
 */
public class PicoHeader implements PicoStructure {
    /** The hash of the unencrypted data. */
    public byte[] hash = null;
    /** Specify the file offset to the data. */
    public long offset = 0L;
    /** The key used to encrypt the data.  The key size is inferred. */
    private byte[] _key = null;
    
    /* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    public PicoHeader clone() {
    	// Make a new header instance and then populate it.
    	PicoHeader newheader = new PicoHeader();
    	newheader.hash = hash.clone();
    	newheader._key = _key.clone();
    	newheader.offset = offset;
    	return newheader;
    }
    
    /**
     * Get the key stored in this header.  <b>Caution</b>: This may be
     * {@code null} if no key has been stored.
     * <p>
     * <b>Caution</b>: The key itself is returned, and not a copy.  This is
     * done for efficiency.
     * 
     * @return	The key.
     */
    public byte[] getKey() {
    	return _key;
    }
    
    /**
     * Set the key stored in this header.  This method makes sure that zero
     * length keys are not allowed, and that the offset is corrected based on
     * the key length.
     * <p>
     * The input array is cloned.
     * 
     * @param key	The key.
     * @return		This header, for chaining.
     */
    public PicoHeader setKey(byte[] key) {
    	if (key == null) {
    		throw new NullPointerException("The key is null.");
    	}
    	if (key.length == 0) {
    		throw new IllegalArgumentException("The key is empty.");
    	}
    	this._key = key.clone();
    	this.offset = KEY_OFFSET + key.length;
    	return this;
    }
    
    /**
     * Perform the encryption or decryption of the specified byte given the
     * (zero-based) position within the data section of the file.
     * <p>
     * The Pico format uses symmetric encryption (xor), so the same operation
     * both encrypts and decrypts.
     * 
     * @param datum		The byte to encrypt or decrypt.
     * @param position	The position.
     * @return			The encrypted or decrypted byte.
     */
    public final byte crypt(byte datum, long position) {
    	return (byte)(datum ^ _key[(int)(position % _key.length)]);
    }

	/**
	 * Populate the fixed header from the provided data chunk.  This does not
	 * populate the key, since the key length is contained in the header
	 * block, but the key array is created with the correct length.
	 * <p>
	 * The length of the data must be at least
	 * {@link PicoStructure#FIXED_HEADER_LENGTH}, or the header is invalid.
	 * <p>
	 * <b>Note</b>: After calling this method the key array is created with
	 * the correct length, but not populated.  Thus enables passing the result
	 * of {@link #getKey()} directly to read methods to populate the key.
	 * 
	 * @param data				The data.
	 * @throws PicoException	The header format is invalid.
	 */
	static PicoHeader getHeader(byte[] data) throws PicoException {
		if (data == null) {
			throw new NullPointerException("The data is null.");
		}
		
		// Check the length of the data.
		if (data.length < FIXED_HEADER_LENGTH) {
			throw new PicoException("Header too short.");
		}
		
		// Check the magic string.
		if (!Arrays.equals(MAGIC, Arrays.copyOfRange(data,
				(int) MAGIC_OFFSET,
				(int) MAGIC_LENGTH +
				(int) MAGIC_OFFSET))) {
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
				(int) MAJOR_OFFSET,
				(int) MAJOR_LENGTH)
				.order(ORDER)
				.getShort();
		short minor = ByteBuffer.wrap(data,
				(int) MINOR_OFFSET,
				(int) MINOR_LENGTH)
				.order(ORDER)
				.getShort();
		// Verify the version.  Pico is intended to be forward compatible, so
		// new versions can read old versions... but not necessarily the
		// reverse.  So we should be able to read any prior version.
		if (major > MAJOR &&
				(major == MAJOR && minor > MINOR)) {
			// The version of the file is past the version of this library.
			// Don't read it.
			throw new PicoException("File version ("+major+"."+minor+
					") cannot be read by this software ("+
					MAJOR+"."+MINOR+").");
		}
		// We don't store the version; the version written would be the version
		// of this library, not the one read.
		
		// Make a new header to hold the data.
		PicoHeader head = new PicoHeader();
		
		// Now that the magic string and the version have checked out, get the
		// offset.
		head.offset = ByteBuffer.wrap(data,
				(int) OFFSET_OFFSET,
				(int) OFFSET_LENGTH)
				.order(ORDER)
				.getInt();
		
		// Read the hash.
		head.hash = new byte[(int) HASH_LENGTH];
		System.arraycopy(data, (int) HASH_OFFSET,
				head.hash, 0, (int) HASH_LENGTH);
		
		// Get the key size.
		short keysize = ByteBuffer.wrap(data,
				(int) KEYSIZE_OFFSET,
				(int) KEYSIZE_LENGTH)
				.order(ORDER)
				.getShort();
		head._key = new byte[keysize];
		
		// Ka-presto!  Everything has been processed except the key.  The caller
		// must now get the key.
		return head;
	}
	
	/**
	 * Convert this header to a byte array, ready for writing.
	 * 
	 * @return	The byte array to write.
	 */
	byte[] putHeader() {
		// Allocate the array.
		byte[] data = new byte[(int) FIXED_HEADER_LENGTH +
		                       _key.length];
		
		// Store the magic string.
		System.arraycopy(MAGIC, 0, data,
				(int) MAGIC_OFFSET,
				(int) MAGIC_LENGTH);
		
		// Store the version.
		ByteBuffer buf = ByteBuffer.allocate((int) MAJOR_LENGTH)
				.order(ORDER);
		buf.asShortBuffer().put(MAJOR);
		System.arraycopy(buf.array(), 0, data,
				(int) MAJOR_OFFSET,
				(int) MAJOR_LENGTH);
		buf = ByteBuffer.allocate((int) MINOR_LENGTH)
				.order(ORDER);
		buf.asShortBuffer().put(MINOR);
		System.arraycopy(buf.array(), 0, data,
				(int) MINOR_OFFSET,
				(int) MINOR_LENGTH);
		
		// Store the key length.
		buf = ByteBuffer.allocate((int) KEYSIZE_LENGTH)
				.order(ORDER);
		buf.asShortBuffer().put((short) _key.length);
		System.arraycopy(buf.array(), 0, data,
				(int) KEYSIZE_OFFSET,
				(int) KEYSIZE_LENGTH);
		
		// If the offset is still zero, fix it.  Assume no metadata for now.
		if (offset <= 0) offset = KEY_OFFSET + _key.length;
		
		// Store the offset.
		buf = ByteBuffer.allocate((int) OFFSET_LENGTH)
				.order(ORDER);
		buf.asIntBuffer().put((int) offset);
		System.arraycopy(buf.array(), 0, data,
				(int) OFFSET_OFFSET,
				(int) OFFSET_LENGTH);
		
		// Store the hash.
		System.arraycopy(hash, 0, data,
				(int) HASH_OFFSET,
				(int) HASH_LENGTH);
		
		// Store the key.
		System.arraycopy(_key, 0, data,
				(int) KEY_OFFSET, _key.length);
		
		// Done.  Return the array.
		return data;
	}
	
	@Override
	public String toString() {
		try {
			return toString(new StringBuffer()).toString();
		} catch(IOException ioe) {
			throw new RuntimeException(ioe);
		}
	}
	
	/**
	 * Represent this header in extended JSON, allowing hex literals and
	 * trailing commas.
	 * 
	 * @param app	An appendable to get the output.
	 * @return		The provided appendable, for chaining.
	 * @throws IOException	The appendable throws an exception.
	 */
	public Appendable toString(Appendable app) throws IOException {
		if (app == null) {
			throw new NullPointerException("The appendable is null.");
		}
		app.append("{\n");
		app.append("  magic-string: [ ");
		for (byte byt : MAGIC) {
			app.append(String.format("0x%02x, ", byt));
		} // Add the magic string.
		app.append("],\n");
		app.append("       version: ");
		app.append(String.format("\"%d.%d\",\n", MAJOR, MINOR));
		app.append("  fixed-header: ");
		app.append(String.format("%d,\n", FIXED_HEADER_LENGTH));
		app.append("        offset: ");
		app.append(String.format("%d,\n", offset));
		if (hash != null) {
			app.append("          hash: 0x");
			for (byte byt : hash) {
				app.append(String.format("%02x", byt));
			} // Add the hash code.
			app.append(",\n");
		}
		if (_key != null) {
			app.append("      key-size: ");
			app.append(String.format("%d,\n", _key.length));
			app.append("           key: [ ");
			for (byte byt : _key) {
				app.append(String.format("0x%02x, ", byt));
			} // Add the key.
			app.append("]\n");
		}
		app.append("}");
		return app;
	}
}
