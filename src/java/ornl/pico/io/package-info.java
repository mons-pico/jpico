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

/**
 * The core package implementing the Pico encoding.  Read more about the Pico
 * encoding at {@linkplain http://mons-pico.github.com/}.
 * <p>
 * The goal is to create an interface to the Pico file format that hides all
 * the inner details of the format and is <em>nearly</em> as fast as direct
 * native access to the raw bytes.
 * <p>
 * There are two major operations to support.
 * <ol>
 * <li>Writing data to a new file.</li>
 * <li>Reading data from an existing file.</li>
 * </ol>
 * 
 * For the first of these it makes sense to just open a channel, write the
 * data, and then close the channel.  For the second we need to provide random
 * access to the data.  To accomplish all this, we use the interfaces of the
 * {@code java.nio.channels} package.  We also provide implementations of
 * both {@code java.io.FileInputStream} and {@code java.io.FileOutputStream}
 * for simple sequential access wrapping some underlying stream.
 * <p>
 * For random access (read and write) use an instance of
 * {@link ornl.pico.io.PicoFile}.  To wrap an existing stream, or to provide
 * a stream to methods that expect one, use {@link ornl.pico.io.PicoInputStream}
 * and {@link ornl.pico.io.PicoOutputStream}.
 */
package ornl.pico.io;
