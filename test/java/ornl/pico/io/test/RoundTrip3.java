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

package ornl.pico.io.test;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import ornl.pico.PicoException;
import ornl.pico.io.PicoFile;
import ornl.pico.io.PicoInputStream;

/**
 * @author Stacy Prowell (prowellsj@ornl.gov)
 */
public class RoundTrip3 {
	
	/** The test data file to write and read back. */
	static byte[] testdata = {
			(byte) 'T', (byte) 'h', (byte) 'i', (byte) 's',
			(byte) ' ', (byte) 'i', (byte) 's', (byte) ' ',
			(byte) 'a', (byte) ' ', (byte) 'T', (byte) 'E',
			(byte) 'S', (byte) 'T', (byte) '.', (byte) 0x0a,
			(byte) 0x00, (byte) 0xff, (byte) 0x55, (byte) 0xaa,
			(byte) 'A', (byte) ' ', (byte) 't', (byte) 'e',
			(byte) 's', (byte) 't', (byte) ' ', (byte) 't',
			(byte) 'h', (byte) 'i', (byte) 's', (byte) ' ',
			(byte) 'i', (byte) 's', (byte) '.', (byte) 0x37,
	};
	
	/* The above data, when correctly in a Pico wrapper file, looks like this.
	 * 
	 * 0000000 91 c0 --> magic string
	 * 0000002 00 00 --> major version number (0)
	 * 0000004 00 00 --> minor version number (0)
	 * 0000006 00 00 00 29 --> offset to data
	 * 000000a 9f 20 7f 81 09 be ef 4d 7f c9 d4 04 d6 df ca 20 --> hash (md5)
	 * 000001a ?? ?? --> key length
	 * 000001c ?? ?? .. ?? --> key
	 * 0000029 ?? ?? .. ?? --> encrypted data
	 */
	
	private File tmpfile;
	
	@Before
	public void setup() throws Exception {
		// Make a temporary file and Pico-encode the data.
		tmpfile = File.createTempFile("test", "pico");
		tmpfile.deleteOnExit();
		PicoFile pf = PicoFile.create(tmpfile);
		pf.write(ByteBuffer.wrap(testdata));
		pf.close();
	}

	@Test
	public void fileTest() throws IOException, PicoException {
		// Now the data is encoded.  Open the file and read it back.
		ByteBuffer bb = ByteBuffer.allocate(8);
		PicoFile pf = PicoFile.open(tmpfile);
		int length;
		int index = 0;
		do {
			// Try to fill the buffer.
			bb.clear();
			length = pf.read(bb);
			if (length > 0) {
				// Check.
				for (int here = 0; here < length; here++) {
					assertEquals("Incorrect byte on read at index "+
							(index+here)+":",
							bb.get(here), testdata[index+here]);
				}
				index += length;
			}
		} while (length > 0);
		assertEquals("Not all data was read back:", testdata.length, index);
		pf.close();
	}

	@Test
	public void streamTest() throws IOException, PicoException {
		// Now the data is encoded.  Open the file and read it back.
		byte[] data = new byte[8];
		PicoInputStream pf = new PicoInputStream(new FileInputStream(tmpfile));
		int length;
		int index = 0;
		do {
			// Try to fill the buffer.
			length = pf.read(data);
			if (length > 0) {
				// Check.
				for (int here = 0; here < length; here++) {
					assertEquals("Incorrect byte on read at index "+
							(here+index)+":",
							data[here], testdata[index+here]);
				}
				index += length;
			}
		} while (length > 0);
		assertEquals("Not all data was read back:", testdata.length, index);
		pf.close();
	}
	
	@After
	public void shutdown() throws Exception {
		// Discard the temp file.
		tmpfile.delete();
	}
}
