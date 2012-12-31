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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Create an output stream to write Pico-encrypted data.  To use this provide
 * a key and the output stream to get the data.  If you have the hash for the
 * unencrypted data, provide that, too.  If not, the hash will be computed as
 * the file is written.  Because the hash needs to go in the header at the
 * start of the stream, the encrypted data is first written to a temporary
 * file, and then copied to the stream on close.
 * <p>
 * Be sure to use the {@code close()} method to ensure the file is completely
 * written!  The {@code flush()} method has no effect.
 * 
 * @author Stacy Prowell (prowellsj@ornl.gov)
 */
public class PicoOutputStream extends OutputStream {
	
	/** The physical output stream to get the data. */
	private final OutputStream _backing;

	/** Make a temporary file. */
	private File _tmpfile;
	
	/** Temporary stream to get the encrypted data. */
	private OutputStream _encrypted;
	
	/** The header information. */
	private PicoHeader _head;
	
	/** The message digest (hash). */
	private final MessageDigest _hash;
	
	/** Has this stream been closed. */
	private boolean _closed = false;
	
	/** Position within the encrypted data. */
	private long _position = 0L;
	
	/**
	 * Make a new Pico output stream, wrapping the provided stream.  Use the
	 * given key to encrypt the data.
	 * 
	 * @param key				The key to use to encrypt.
	 * @param os				The stream to get the output.
	 * @throws IOException		An error occurred creating the temporary file.
	 */
	public PicoOutputStream(byte[] key, OutputStream os) throws IOException {
		if (key == null) {
			throw new NullPointerException("The key is null.");
		}
		if (os == null) {
			throw new NullPointerException("The output stream is null.");
		}
		_backing = os;
		try {
			_hash = MessageDigest.getInstance(PicoStructure.HASH);
		} catch (NoSuchAlgorithmException nsae) {
			throw new RuntimeException("Failed to create hash.", nsae);
		}
		// Construct a temporary file to get the encrypted data.
		_tmpfile = File.createTempFile("pico", "pico");
		_tmpfile.deleteOnExit();
		_encrypted = new FileOutputStream(_tmpfile);
		// Build the header.
		_head = new PicoHeader();
		_head.setKey(key);
	}

	/* (non-Javadoc)
	 * @see java.io.OutputStream#write(int)
	 */
	@Override
	public void write(int datum) throws IOException {
		if (_closed) return;
		// Byteify this.
		datum &= 0xff;
		// Add to the message digest.
		_hash.update((byte) datum);
		// Encode.
		datum = _head.crypt((byte) datum, _position);
		// Write.
		_encrypted.write(datum);
		_position += 1;
	}
	
	/* (non-Javadoc)
	 * @see java.io.OutputStream#close()
	 */
	@Override
	public void close() throws IOException {
		finish();
		_backing.close();
	}
	
	/**
	 * Finish the stream, writing the header and the encrypted data to the
	 * stream.  The underlying stream is not closed.  If you want to cause
	 * the underlying stream to be closed, too, use {@code close()}, which
	 * invokes this method and then closes the stream.
	 * 
	 * @throws IOException	An error occurred writing the file.
	 */
	public void finish() throws IOException {
		if (_closed) return;
		// Finish the hash and store it in the header.
		_head.hash = _hash.digest();
		
		// Write the header to the backing store.
		_backing.write(_head.putHeader());
		
		// Write the encrypted data to the backing store.
		_encrypted.flush();
		_encrypted.close();
		InputStream fis = new FileInputStream(_tmpfile);
		byte[] buffer = new byte[1024];
		while (fis.available() > 0) {
			int length = fis.read(buffer);
			if (length >= 0) {
				_backing.write(buffer, 0, length);
			}
		} // Copy all encrypted data.
		fis.close();
		_backing.flush();
		_tmpfile.delete();
	}
}
