package com.adonax.audiocue;

import java.io.IOException;

/**
 * AudioMixerTrack is the interface for tracks that
 * are mixed in an AudioMixer.
 * 
 * @author Philip Freihofner
 * @version AudioCue 1.2
 * @see http://adonax.com/AudioCue 
 * */
abstract public interface AudioMixerTrack {

	/**
	 * This function is consulted by the <b>AudioMixer</b> to 
	 * determine whether or not to call the <code>read()</code> 
	 * method for the track.
	 */
	boolean isRunning();
	void setRunning(boolean bool);

	/**
	 * Reads one buffer of normalized audio data frames of 
	 * the track.
	 * @return - one buffer of normalized audio frames
	 * @throws IOException - if an I/O exception occurs
	 */
	float[] readTrack() throws IOException;	
}
