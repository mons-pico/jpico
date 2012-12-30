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

/**
 * Report a serious error in the Pico file format, typically a mis-formatted
 * Pico header.
 * 
 * @author Stacy Prowell (prowellsj@ornl.gov)
 */
public class PicoException extends Exception {
	
	/**
	 * The serial version ID.
	 */
	private static final long serialVersionUID = 7687070621801512832L;

	/**
	 * Create a new instance with the given human-readable message.
	 * @param msg	The human-readable message.
	 */
	public PicoException(String msg) {
		super(msg);
	}
	
	/**
	 * Create a new instance with the given human-readable message and the
	 * given prior exception.
	 * @param msg	The human-readable message.
	 * @param prior	A prior exception.
	 */
	public PicoException(String msg, Throwable prior) {
		super(msg, prior);
	}
}
