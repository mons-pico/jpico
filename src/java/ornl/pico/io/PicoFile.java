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
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Provide for random access writing of a Pico-encoded file.
 * <p>
 * To use this make an instance wrapping the specified file.  The file can be
 * written via the {@link #write()} and {@link #write(ByteBuffer)} methods, and
 * the writing position can be modified via {@link #position(long)}.  To get
 * the current position within the data, use {@link #position()}.
 * <p>
 * These methods read and write the packaged data, so position zero is the
 * first byte of the packaged data.
 * <p>
 * <b>Note</b>: Because the data can be written arbitrarily, but the hash must
 * be computed sequentially, the hash is not available.  Also, if a block of
 * data is left, it is written as nulls, but these are decrypted when read, so
 * be aware of that.  The result will be portions (or repeats) of the encryption
 * key.
 * <p>
 * This class allows reading of the data via the {@link #read()} and
 * {@link #read(ByteBuffer)} methods, but only data that has been written can
 * be read.
 * <p>
 * <b>Caution</b>: You must invoke {@link #finish()} or {@link #close()} when
 * done to be sure the header is written, since the hash must be computed and
 * written last.
 * 
 * @author Stacy Prowell (prowellsj@ornl.gov)
 */
public class PicoFile
implements WritableByteChannel, ReadableByteChannel, SeekableByteChannel {
	
	//======================================================================
	// Static methods.
	//======================================================================
	
	/**
	 * Create or replace a Pico file.  If the file exists it will be replaced.
	 * If it does not exist it is created.
	 * 
	 * @param filename			The filename.
	 * @param key				The key to use to encrypt the file.
	 * @return					The Pico file instance.
	 * @throws IOException		The file cannot be created.
	 */
	public static PicoFile create(String filename, byte[] key)
			throws IOException {
		if (filename == null) {
			throw new NullPointerException("The filename is null.");
		}
		if (key == null) {
			throw new NullPointerException("The key is null.");
		}
		if (key.length == 0) {
			throw new IllegalArgumentException("Encryption key is empty.");
		}
		return new PicoFile(new RandomAccessFile(filename, "rw"), key);
	}
	
	/**
	 * Create or replace a Pico file.  If the file exists it will be replaced.
	 * If it does not exist it is created.
	 * 
	 * @param filename			The filename.
	 * @param key				The key to use to encrypt the file.
	 * @return					The Pico file instance.
	 * @throws IOException		The file cannot be created.
	 */
	public static PicoFile create(File file, byte[] key)
			throws IOException {
		if (file == null) {
			throw new NullPointerException("The file is null.");
		}
		if (key == null) {
			throw new NullPointerException("The key is null.");
		}
		if (key.length == 0) {
			throw new IllegalArgumentException("Encryption key is empty.");
		}
		return new PicoFile(new RandomAccessFile(file, "rw"), key);
	}
	
	/**
	 * Open an existing Pico file for reading and writing.  The file must
	 * already exist.
	 * 
	 * @param filename			The filename.
	 * @return					The Pico file instance.
	 * @throws PicoException	The file format is incorrect.
	 * @throws IOException		The file cannot be created.
	 */
	public static PicoFile open(String filename)
			throws PicoException, IOException {
		if (filename == null) {
			throw new NullPointerException("The filename is null.");
		}
		return new PicoFile(new RandomAccessFile(filename, "rw"));
	}
	
	/**
	 * Open an existing Pico file for reading and writing.  The file must
	 * already exist.
	 * 
	 * @param file				The file.
	 * @return					The Pico file instance.
	 * @throws PicoException	The file format is incorrect.
	 * @throws IOException		The file cannot be created.
	 */
	public static PicoFile open(File file)
			throws PicoException, IOException {
		if (file == null) {
			throw new NullPointerException("The file is null.");
		}
		return new PicoFile(new RandomAccessFile(file, "rw"));
	}
	
	//======================================================================
	// Instance data.
	//======================================================================

	/** The physical file for this logical Pico file. */
	private final RandomAccessFile _backing;
	
	/** Whether the file is open. */
	private boolean _open = false;
	
	/** The Pico header that will be written to the file. */
	private PicoHeader _head;
	
	/** If true then the hash stored in the header is valid. */
	private boolean _hashvalid = false;
	
	/** The digest is valid up to, but not including, this position. */
	private long _digestvalidto = 0L;
	
	/** The message digest to use to compute the hash. */
	private MessageDigest _digest;
	
	//======================================================================
	// Constructors.
	// The constructors are protected since the static methods should be used
	// to create instances.
	//======================================================================

	/**
	 * Open an existing Pico encoded file.
	 *  
	 * @param backing			The random access file.
	 * @throws PicoException	The file format is incorrect.
	 * @throws IOException		The file cannot be read.
	 */
	protected PicoFile(RandomAccessFile backing)
			throws PicoException, IOException {
		assert backing != null : "Backing is null.";
		_backing = backing;
		_open = true;
		_resetDigest();
		_readHeader();
		position(0L);
	}
	
	/**
	 * Create a new Pico file instance from the given random access file.  If
	 * the file exists and it not empty the it is truncated.  If it does not
	 * exist then it is created.
	 * 
	 * @param backing			The random access file.
	 * @param key				The key to use to encrypt the file.
	 * @throws IOException		The file cannot be read.
	 */
	protected PicoFile(RandomAccessFile backing, byte[] key)
			throws IOException {
		assert backing != null : "Backing is null.";
		assert key != null : "Key is null.";
		assert key.length > 0 : "Key is missing.";
		_backing = backing;
		_open = true;
		_resetDigest();
		// We are creating a new file, so truncate any existing file and
		// generate a new header.
		_backing.setLength(0L);
		_head = new PicoHeader();
		_head.setKey(key);
		position(0L);
	}
	
	//======================================================================
	// Internal methods.
	//======================================================================

	/**
	 * Reset the digest.  After calling this all bytes must be re-processed
	 * to obtain the hash.
	 */
	private void _resetDigest() {
		try {
			_digest = MessageDigest.getInstance(PicoStructure.HASH);
			_digestvalidto = 0L;
		} catch (NoSuchAlgorithmException nsae) {
			throw new RuntimeException("Failed to create hash.", nsae);
		}
	}
	
	/**
	 * Update the digest so it is valid up to the current file position.
	 * After invoking this the digest is valid up to the current file
	 * position given by {@link #position()}.
	 */
	private void _updateDigest() throws IOException {
		long pos = position();
		if (_digestvalidto == pos)
			return;
		if (_digestvalidto > pos)
			_resetDigest();
		int blocksize = 16384;
		ByteBuffer buf = ByteBuffer.allocate(blocksize);
		position(_digestvalidto);
		while (_digestvalidto < pos) {
			_digestvalidto += read(buf);
			_digest.update(buf);
			buf.clear();
		} // Compute the digest through the rest of the file.
	}
	
	/**
	 * Read the header.  This reads the header from an existing file.  After
	 * this method completes the header reflects what it stored in the file,
	 * including the hash and key.
	 * 
	 * @throws PicoException	The header structure is incorrect.
	 * @throws IOException		The header cannot be read.
	 */
	private void _readHeader() throws PicoException, IOException {
		if (! _open) return;
		
		// Save the current position and move to the start of the header.
		long pos = _backing.getFilePointer();
		_backing.seek(PicoStructure.HEAD_START);
		
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
		
		// Process the fixed portion of the header.  After calling this the
		// key is allocated, but not populated.
		_head = PicoHeader.getHeader(_fixedhdr);
		
		// The hash is valid because we just read it from the file and nothing
		// has yet been written.  All write methods must invalidate the hash.
		_hashvalid = true;
		
		// Go and read the key, now that we know its length.  Note that we read
		// it directly into the array returned by getKey.
		length = _backing.read(_head.getKey());
		
		// Make sure we have the complete key.  The only bad case is that the
		// file ends before the key is complete.
		if (length != _head.getKey().length) {
			throw new PicoException("File too short; incomplete key.");
		}
		
		// Move to the original position in the file.
		_backing.seek(pos);
		
		// Ka-presto!  The header has been read.  Life is good.
	}
	
	//======================================================================
	// General access methods.
	//======================================================================

	/**
	 * Get the header for this Pico file.  The returned header is a copy of
	 * the actual header.
	 * <p>
	 * Note that the returned hash may be {@code null} if it has not been
	 * computed.  If the file has been opened, but not written, then the hash
	 * will be available via this method.
	 * 
	 * @return	The header of this file.
	 * @throws	IOException		The file length cannot be read.
	 */
	public PicoHeader getHeader() throws IOException {
		PicoHeader head = _head.clone();
		if (! _hashvalid) {
			head.hash = null;
		}
		return head;
	}

	/* (non-Javadoc)
	 * @see java.nio.channels.Channel#isOpen()
	 */
	@Override
	public boolean isOpen() {
		return _open;
	}
	
	//======================================================================
	// Finish and close the file.
	//======================================================================

	/**
	 * Flush the file, computing the hash if necessary.  After this method
	 * completes the hash obtained in {@link #getHeader()} will be valid, but
	 * the file will not be closed.  If you want the file to be closed, use
	 * {@link #close()} instead, which invokes this method.
	 * <p>
	 * <b>Warning</b>: Because of the hash computation this method can be
	 * costly!  After calling this the internal hash computation must be
	 * completely reset, so a subsequent write will cause the hash to be
	 * updated from scratch.  Use caution with this method.  In fact, this
	 * is the reason this method is not named {@code flush}.
	 * 
	 * @throws IOException	An error occurred writing the file.
	 */
	public void finish() throws IOException {
		if (! _open) return;
		
		// Save the current position so we can restore it later.
		long pos = _backing.getFilePointer();
		
		// If the hash is not valid, compute it now.
		if (! _hashvalid) {
			// The hash is not valid.  Complete the computation now and store
			// the resulting hash.
			_backing.seek(_backing.length());
			_updateDigest();
			_head.hash = _digest.digest();
			_hashvalid = true;
			
			// Reset the digest now to force it to be re-computed next time.
			// This must be done since we just "used up" the existing digest
			// instance.
			_resetDigest();
		}
		
		// Write the header to the backing store.
		_backing.seek(PicoStructure.HEAD_START);
		_backing.write(_head.putHeader());

		// Restore the file position.
		_backing.seek(pos);
	}

	/* (non-Javadoc)
	 * @see java.nio.channels.Channel#close()
	 */
	@Override
	public void close() throws IOException {
		if (! _open) return;
		finish();
		_open = false;
		_backing.close();
	}

	//======================================================================
	// Position in channel and length of channel.
	// These methods operate on the packaged data, excluding the header.
	//======================================================================

	/* (non-Javadoc)
	 * @see java.nio.channels.SeekableByteChannel#position()
	 */
	@Override
	public long position() throws IOException {
		return _backing.getFilePointer() - _head.offset;
	}

	/* (non-Javadoc)
	 * @see java.nio.channels.SeekableByteChannel#position(long)
	 */
	@Override
	public SeekableByteChannel position(long newPosition) throws IOException {
		_backing.seek(newPosition + _head.offset);
		return this;
	}

	/* (non-Javadoc)
	 * @see java.nio.channels.SeekableByteChannel#size()
	 */
	@Override
	public long size() throws IOException {
		return _backing.length() - _head.offset;
	}

	//======================================================================
	// Read methods.
	//======================================================================

	/* (non-Javadoc)
	 * @see java.nio.channels.SeekableByteChannel#read(java.nio.ByteBuffer)
	 */
	@Override
	public int read(ByteBuffer dst) throws IOException {
		if (dst == null) {
			throw new NullPointerException("The destination buffer is null.");
		}
		if (dst.limit() == 0) {
			throw new IllegalArgumentException(
					"The destination buffer has zero length.");
		}
		if (! _open) return -1;
		
		// Make an attempt to read up to r bytes from the channel, where
		// r is the number of bytes remaining in the buffer, that is,
		// dst.remaining(), at the moment this method is invoked.
		long _here = position();
		int remain = dst.remaining();
		byte[] data = new byte[remain];
		int length = _backing.read(data, 0, remain);
		if (length > 0) {
			for (int index = 0; index < length; index++) {
				data[index] = _head.crypt(data[index], index+_here);
			} // Decrypt all bytes.
			dst.put(data, 0, length);
		}
		return length;
	}
	
	/**
	 * Read the next byte at the current position, and return it.  Return
	 * -1 if the end of the file is read.
	 * 
	 * @return	The next byte.
	 * @throws IOException	The next byte cannot be read.
	 */
	public int read() throws IOException {
		if (! _open) return -1;
		long _here = position();
		int val = _backing.read();
		if (val >= 0) {
			val = _head.crypt((byte) val, _here);
		}
		return val;
	}

	//======================================================================
	// Write methods.
	//======================================================================

	/* (non-Javadoc)
	 * @see java.nio.channels.SeekableByteChannel#truncate(long)
	 */
	@Override
	public PicoFile truncate(long size) throws IOException {
		if (_open) _backing.setLength(size + _head.offset);
		_hashvalid = false;
		_resetDigest();
		return this;
	}

	/* (non-Javadoc)
	 * @see java.nio.channels.WritableByteChannel#write(java.nio.ByteBuffer)
	 */
	@Override
	public int write(ByteBuffer src) throws IOException {
		if (src == null) {
			throw new NullPointerException("The source buffer is null.");
		}
		if (src.limit() == 0) {
			throw new IllegalArgumentException(
					"The source buffer has zero length.");
		}
		if (! _open) return -1;
		
		// Make an attempt to write up to r bytes to the channel, where
		// r is the number of bytes remaining in the buffer, that is,
		// src.remaining(), at the moment this method is invoked.
		// Note that we must be careful not to overwrite the array passed
		// in.
		long _here = position();
		byte[] data = src.array();
		byte[] encr = new byte[data.length];
		int length = data.length;
		
		// Update the digest to the start of the write.
		_updateDigest();
		
		// Encrypt the data and update the digest.
		if (length > 0) {
			for (int index = 0; index < length; index++) {
				_digest.update(data[index]);
				_digestvalidto++;
				encr[index] = _head.crypt(data[index], index+_here);
			} // Encrypt all bytes.
			_backing.write(encr);
		}
		return length;
	}
	
	/**
	 * Write a byte at the current position.
	 * 
	 * @param datum	The byte.
	 * @throws IOException	The byte cannot be written.
	 */
	public void write(int datum) throws IOException {
		if (! _open) return;
		datum &= 0xff;
		long _here = position();
		if (_here == _digestvalidto) {
			_digest.update((byte) datum);
		}
		datum = _head.crypt((byte) datum, _here);
		_backing.write(datum);
	}
}
