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

import java.util.Random;

/**
 * Provide some simple operations for working with Pico keys.
 * 
 * @author Stacy Prowell (prowellsj@ornl.gov)
 */
public class KeyUtils {
	
	/** The default size of a randomly-generated key. */
	public static final int KEYSIZE = 31;
	
	/** The source used for random keys.  It shouldn't have to be awesome. */
	public static final Random RAND = new Random();

	/**
	 * Make a random key.
	 * 
	 * @param keysize	The key size, which must be greater than zero.
	 * @return	The random key.
	 */
	public static byte[] makeKey(int keysize) {
		if (keysize < 1) {
			throw new IllegalArgumentException("Key size must be at least 1.");
		}
		byte[] key = new byte[keysize];
		for (int index = 0; index < keysize; index++) {
			key[index] = (byte) RAND.nextInt(256);
		} // Randomize the bytes.
		return key;
	}
	
	/**
	 * Make a random key.
	 * 
	 * @return	The random key.
	 */
	public static byte[] makeKey() {
		return makeKey(KEYSIZE);
	}
}
