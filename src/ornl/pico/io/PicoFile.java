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

/**
 * Provide for random access reading a Pico-encoded file.
 * 
 * @author Stack Prowell (prowellsj@ornl.gov)
 */
public class PicoFile implements ReadableByteChannel, SeekableByteChannel {
	
	/** The physical file for this logical Pico file. */
	private final RandomAccessFile _backing;
	
	/** Is the underlying file open? */
	private boolean _open = false;
	
	/** The Pico header read from the file. */
	private PicoHeader _head;
	
	/**
	 * Create a new Pico file instance from the given filename.
	 * @param filename			The filename.
	 * @throws PicoException	The file format is incorrect.
	 * @throws IOException		The file cannot be read.
	 */
	public PicoFile(String filename) throws PicoException, IOException {
		this(new RandomAccessFile(filename, "r"));
	}
	
	/**
	 * Create a new Pico file instance from the given file.
	 * @param file				The file.
	 * @throws PicoException	The file format is incorrect.
	 * @throws IOException		The file cannot be read.
	 */
	public PicoFile(File file) throws PicoException, IOException {
		this(new RandomAccessFile(file, "r"));
	}

	/**
	 * Create a new Pico file instance from the given random access file.
	 * @param backing			The random access file.
	 * @throws PicoException	The file format is incorrect.
	 * @throws IOException		The file cannot be read.
	 */
	PicoFile(RandomAccessFile backing) throws PicoException, IOException {
		if (backing == null) {
			throw new NullPointerException("The backing file is null.");
		}
		_backing = backing;
		readHeader();
		_open = true;
	}
	
	private void readHeader() throws PicoException, IOException {
		// Read the header from the file.  We read the fixed length portion
		// here, up through the start of the key.
		byte[] _fixedhdr = new byte[(int) PicoFileOffsets.FIXED_HEADER_LENGTH];
		int length = _backing.read(_fixedhdr);
		
		// Make sure we have enough bytes for the magic string.
		if (length <
				PicoFileOffsets.MAGIC_LENGTH - PicoFileOffsets.MAGIC_OFFSET) {
			// The magic string was not present.  This cannot be a Pico wrapper
			// file.
			throw new PicoException("File too short; missing magic string.");
		}
		
		// Process the head.
		_head = PicoHeader.readHeader(_fixedhdr);
		
		// Go and read the key, now that we know its length.
		length = _backing.read(_head.key);
		
		// Make sure we have the complete key.
		if (length != _head.key.length) {
			throw new PicoException("File too short; incomplete key.");
		}
		
		// Seek to the first byte of encrypted data.
		position(0L);
		
		// Ka-presto!  The header has been read.  Life is good.
	}

	/* (non-Javadoc)
	 * @see java.nio.channels.Channel#isOpen()
	 */
	@Override
	public boolean isOpen() {
		return _open;
	}

	/* (non-Javadoc)
	 * @see java.nio.channels.Channel#close()
	 */
	@Override
	public void close() throws IOException {
		_open = false;
		_backing.close();
	}

	/* (non-Javadoc)
	 * @see java.nio.channels.SeekableByteChannel#write(java.nio.ByteBuffer)
	 */
	@Override
	public int write(ByteBuffer src) throws IOException {
		throw new IOException("Write is not supported.");
	}

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
	public PicoFile position(long newPosition) throws IOException {
		_backing.seek(newPosition + _head.offset);
		return this;
	}

	/* (non-Javadoc)
	 * @see java.nio.channels.SeekableByteChannel#size()
	 */
	@Override
	public long size() throws IOException {
		return _backing.length();
	}

	/* (non-Javadoc)
	 * @see java.nio.channels.SeekableByteChannel#truncate(long)
	 */
	@Override
	public SeekableByteChannel truncate(long size) throws IOException {
		throw new IOException("Write (truncate) is not supported.");
	}

	/* (non-Javadoc)
	 * @see java.nio.channels.ReadableByteChannel#read(java.nio.ByteBuffer)
	 */
	@Override
	public int read(ByteBuffer dst) throws IOException {
		// Make an attempt to read up to r bytes from the channel, where
		// r is the number of bytes remaining in the buffer, that is,
		// dst.remaining(), at the moment this method is invoked.
		long _here = _backing.getFilePointer() - _head.offset;
		int remain = dst.remaining();
		byte[] data = new byte[remain];
		int length = _backing.read(data, 0, remain);
		if (length > 0) {
			for (int index = 0; index < length; index++) {
				data[index] ^= _head.key[(int) (index+_here) % _head.key.length];
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
		long _here = _backing.getFilePointer() - _head.offset;
		int val = _backing.read();
		if (val >= 0) {
			val ^= _head.key[(int)_here % _head.key.length];
		}
		return val;
	}
}
