package com.adonax.audiocue;

import java.io.IOException;

/**
 * An interface for classes that make audio data available to an 
 * {@code AudioMixer} for media play via the {@code read} method.
 * <p>
 * Objects that implement {@code AudioMixerTrack} can either be (1) running, 
 * or (2) not running. There are no explicit restrictions related to the 
 * relationship between the state and the read method. Nor are there any 
 * restrictions or promises made as to the content of the returned float 
 * array. 
 * <p>
 * However, in this package, the following conditions have been implemented:
 * <ul><li>The {@code read} method will only be executed after the track is
 * first shown to be in a running state;</li>
 * <li>the read array is expected to consist of signed, normalized floats of 
 * stereo PCM encoded at 44100 frames
 * per second.</li></ul>
 * <p>
 * With these constraints in place, the media write of audio from the track
 * can in effect be muted by setting the state to not running, and the media 
 * writes can be set to resume by setting the state to running.
 * 
 * @author Philip Freihofner
 * @version AudioCue 2.0.0
 */
abstract public interface AudioMixerTrack {

	/**
	 * Indicates if the track is or is not being included in the 
	 * {@code AudioMixer} media out. If the method returns {@code true}, 
	 * this track is included in the mix. If {@code false}, the track 
	 * is ignored, as if a 'mute' button had been pressed.
	 * 
	 * @return {@code true} if the track is being included in the mix, 
	 * 			otherwise {@code false}
	 */
	boolean isTrackRunning();
	
	/**
	 * Used to set whether or not this {@code AudioMixerTrack} is to be
	 * included in the {@code AudioMixer} media out. When set to 
	 * {@code true}, this {@code AudioMixerTrack} will be included in
	 * the audio mix. When set to {@code false}, this track will be ignored,
	 * and not included in the audio mix, as if a 'mute' button had
	 * been pressed. 
	 * 
	 * @param bool - if {@code true}, this track will be included in the 
	 * 				audio mix, if {@code false} this track will not be
	 * 				included
	 */
	void setTrackRunning(boolean bool);

	/**
	 * Reads one buffer of normalized audio data frames of 
	 * the track.
	 * @return one buffer of normalized audio frames
	 * @throws IOException if an I/O exception occurs
	 */
	float[] readTrack() throws IOException;	
}
