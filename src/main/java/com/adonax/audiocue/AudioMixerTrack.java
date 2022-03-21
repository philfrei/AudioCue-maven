/*
 *  This file is part of PFAudio, 
 *  Copyright 2015 Philip Freihofner.
 *  
 *  PFAudio is free software: you can 
 *  redistribute it and/or modify it under the terms of the 
 *  GNU General Public License as  published by the Free 
 *  Software Foundation, either version 3 of the License, 
 *  or (at your option) any later version.
 *
 *  Per Frame Audio Mixer System is distributed in the hope 
 *  that it will be useful, but WITHOUT ANY WARRANTY; without 
 *  even the implied warranty of MERCHANTABILITY or FITNESS 
 *  FOR A PARTICULAR PURPOSE.  See the GNU General Public 
 *  License for more details.
 *
 *  You should have received a copy of the GNU General Public 
 *  License along with Per Frame Audio Mixer System.  If not, 
 *  see <http://www.gnu.org/licenses/>.
 */
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
