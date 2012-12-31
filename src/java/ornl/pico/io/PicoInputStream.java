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
import java.io.InputStream;

/**
 * Implement a simple class for directly reading the unencrypted bytes from
 * a Pico wrapper file.
 * <p>
 * To use this pass a source input stream.  The source stream must be at the
 * first byte of the Pico file.  It is also the case that you should not
 * manipulate the underlying stream after passing it to the constructor.
 * 
 * @author Stacy Prowell (prowellsj@ornl.gov)
 */
public class PicoInputStream extends InputStream {
	
	/** The physical stream for this logical Pico file. */
	private final InputStream _backing;
	
	/** The Pico header read from the file. */
	private PicoHeader _head;
	
	/** Position in the encrypted data. */
	private long _position = 0L;
	
	/**
	 * Create a new Pico file instance from the given input stream.
	 * @param is				The input stream.
	 * @throws PicoException	The file format is incorrect.
	 * @throws IOException		The file cannot be read.
	 */
	public PicoInputStream(InputStream is) throws PicoException, IOException {
		if (is == null) {
			throw new NullPointerException("The input stream is null.");
		}
		_backing = is;
		readHeader();
	}

	private void readHeader() throws PicoException, IOException {
		// Read the header from the file.  We read the fixed length portion
		// here, up through the start of the key.
		byte[] _fixedhdr = new byte[(int) PicoStructure.FIXED_HEADER_LENGTH];
		int length = _backing.read(_fixedhdr);
		
		// Make sure we have enough bytes for the magic string.
		if (length <
				PicoStructure.MAGIC_LENGTH - PicoStructure.MAGIC_OFFSET) {
			// The magic string was not present.  This cannot be a Pico wrapper
			// file.
			throw new PicoException("File too short; missing magic string.");
		}
		
		// Process the fixed part of the head.
		_head = PicoHeader.getHeader(_fixedhdr);
		
		// Read the key.
		length = _backing.read(_head.getKey());
		
		// Make sure we have the complete key.  Check the length, since the key
		// it outside the fixed header.
		if (length < _head.getKey().length) {
			throw new PicoException("File too short; incomplete key.");
		}
		
		// Ka-presto!  The header has been read.  Life is good.
	}
	
	/**
	 * Get the header for this Pico file.  The returned header is a copy of
	 * the actual header, so no damage can be done.
	 * 
	 * @return	The header of this file.
	 */
	public PicoHeader getHeader() {
		return _head.clone();
	}

	/**
	 * @see java.io.InputStream#read()
	 */
	@Override
	public int read() throws IOException {
		// Read the next byte from the underlying input stream.
		int result = _backing.read();
		if (result >= 0) {
			// Okay, got a byte.  Now decode the byte.
			result = _head.crypt((byte) result, _position) & 0xff;
			_position += 1;
		}
		return result;
	}
}
