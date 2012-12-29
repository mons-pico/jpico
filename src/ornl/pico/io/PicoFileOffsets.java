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

import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Define the structure of a Pico wrapper file's header.  This specifies the
 * fixed offsets into the file.  To use this you can either inherit it or you
 * can do a static import.
 * <p>
 * The basic structure is as follows.
 * <table>
 * <tr><th>Offset</th><th>Item</th><th>Length</th></tr>
 * <tr><td>{@code MAGIC_OFFSET}</td><td>Magic String</td><td>{@code MAGIC_LENGTH}</td></tr>
 * <tr><td>{@code MAJOR_OFFSET}</td><td>Major Version</td><td>{@code MAJOR_LENGTH}</td></tr>
 * <tr><td>{@code MINOR_OFFSET}</td><td>Minor Version</td><td>{@code MINOR_LENGTH}</td></tr>
 * <tr><td>{@code OFFSET_OFFSET}</td><td>Offset to Start of Data</td><td>{@code OFFSET_LENGTH}</td></tr>
 * <tr><td>{@code HASH_OFFSET}</td><td>Hash</td><td>{@code HASH_LENGTH}</td></tr>
 * <tr><td>{@code KEYSIZE_OFFSET}</td><td>Length of Key</td><td>{@code KEYSIZE_LENGTH}</td></tr>
 * <tr><td>{@code KEY_OFFSET}</td><td>Key</td><td>{@code KEY_LENGTH}</td></tr>
 * <tr><td>(depends on key size)</td><td>Optional Metadata</td><td>(up to start of data)</td></tr>
 * <tr><td>(specified by OFFSET)</td><td>Encrypted Data</td><td>(to end of file)</td></tr>
 * </table>
 * All numbers are stored in "network byte order" (big endian) format.  That
 * is, the most significant byte is first.  The number 258 is 0102 in
 * hexadecimal, and is stored in order 01 02, with 01 at the lower address,
 * and 02 at the higher address.  If you are still confused, see here:
 * http://en.wikipedia.org/wiki/Endianness
 * <p>
 * To avoid having to keep track of this, the byte order is stored in the
 * {@code ORDER} field.
 * 
 * @author Stacy Prowell (prowellsj@ornl.gov)
 * @version {@value MAJOR}.{@value MINOR}
 */
public interface PicoFileOffsets {
	
	/** The magic string. */
	static final byte[] MAGIC = { (byte) 0x91, (byte) 0xc0 };
	
	/** Position of the magic string.  Currently {@value}.*/
	static final long MAGIC_OFFSET = 0L;
	
	/** Length of the magic string. */
	static final long MAGIC_LENGTH = MAGIC.length;
	
	/** Major version of the Pico file format.  Currently {@value}. */
	static final short MAJOR = 0;
	
	/** Position of the major version. */
	static final long MAJOR_OFFSET = MAGIC_OFFSET + MAGIC_LENGTH;
	
	/** Length of the major version.  Currently {@value}. */
	static final long MAJOR_LENGTH = Short.SIZE / 8;

	/** Minor version of the Pico file format.  Currently {@value}.*/
	static final short MINOR = 0;
	
	/** Position of the minor version. */
	static final long MINOR_OFFSET = MAJOR_OFFSET + MAJOR_LENGTH;
	
	/** Length of the minor version.  Currently {@value}. */
	static final long MINOR_LENGTH = Short.SIZE / 8;
	
	/** Position of the data offset. */
	static final long OFFSET_OFFSET = MINOR_OFFSET + MINOR_LENGTH;
	
	/** Length of the data offset.  Currently {@value}. */
	static final long OFFSET_LENGTH = Integer.SIZE / 8;
	
	/**
	 * Kind of hash to use.  This must be known to {@link MessageDigest}, and
	 * is currently {@value}.
	 */
	static final String HASH = "md5";
	
	/** Position of the hash. */
	static final long HASH_OFFSET = OFFSET_OFFSET + OFFSET_LENGTH;
	
	/** Length of the hash. */
	static final long HASH_LENGTH = HashLength.getHashLength();
	
	/**
	 * Compute the hash length reported by {@code HASH_LENGTH}.
	 * 
	 * @author Stacy Prowell (prowellsj@ornl.gov)
	 */
	public static class HashLength {
		/**
		 * Compute the length of the hash specified by {@code HASH}.
		 * 
		 * @return	Length of the hash, in bytes, or zero if the specified hash
		 * 			algorithm is not known.
		 */
		public static int getHashLength() {
			try {
				return MessageDigest.getInstance(HASH).getDigestLength();
			} catch(NoSuchAlgorithmException nsae) {
				return 0;
			}
		}
	}
	
	/** Position of the key size. */
	static final long KEYSIZE_OFFSET = HASH_OFFSET + HASH_LENGTH;
	
	/** Length of the key size.  Currently {@value}. */
	static final long KEYSIZE_LENGTH = Short.SIZE / 8;
	
	/** Position of the key. */
	static final long KEY_OFFSET = KEYSIZE_OFFSET + KEYSIZE_LENGTH;
	
	/**
	 * The length of the "fixed" header.  This is required by all versions of
	 * Pico, now and forever.  Amen.
	 */
	static final long FIXED_HEADER_LENGTH = KEY_OFFSET;
	
	/** Specify the byte order for the numeric fields. */
	static final ByteOrder ORDER = ByteOrder.BIG_ENDIAN;
}
