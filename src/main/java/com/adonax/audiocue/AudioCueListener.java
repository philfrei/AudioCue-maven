/*
 * This file is part of AudioCue, 
 * Copyright 2017 Philip Freihofner.
 *  
 * Redistribution and use in source and binary forms, with or 
 * without modification, are permitted provided that the 
 * following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above 
 * copyright notice, this list of conditions and the following 
 * disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above 
 * copyright notice, this list of conditions and the following 
 * disclaimer in the documentation and/or other materials 
 * provided with the distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of 
 * its contributors may be used to endorse or promote products 
 * derived from this software without specific prior written 
 * permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND 
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR 
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT 
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR 
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, 
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.adonax.audiocue;

/**
 * A listener interface for receiving notifications of events
 * pertaining to an {@code AudioCue} and its playable or playing 
 * instances. For a class to receive and act on these 
 * notifications, it implements this interface, and a copy of
 * the class is registered with the {@code AudioCue} via the
 * following method:
 * <pre>    myAudioCue.addAudioCueListener(myAudioCueListener);</pre>
 * <p>
 * The execution of the implementing methods will occur on the 
 * same thread that processes the audio data, and thus should 
 * be coded for brevity in order to minimize extraneous processing
 * that might contribute to dropouts during playback. 
 * 
 * @author Philip Freihofner
 * @version AudioCue 1.2
 * @see http://adonax.com/AudioCue
 */
public interface AudioCueListener 
{
	/**
	 * Method called when an {@code AudioCue} executes its 
	 * {@code open} method.
	 * 
	 * @param now {@code long} holding millisecond value 
	 * @param threadPriority {@code int} specifying thread
	 * priority
	 * @param bufferSize {@code int} specifying buffer size
	 * in sample frames
	 * @param source {@code AudioCue} that originated the
	 * notification
	 */
	void audioCueOpened(long now, int threadPriority, int bufferSize, 
			AudioCue source);
	/**
	 * Method called when an {@code AudioCue} executes its 
	 * {@code close} method.
	 * 
	 * @param now {@code long} holding millisecond value
	 * @param source {@code AudioCue} that originated the
	 * notification
	 */
	void audioCueClosed(long now, AudioCue source);
	
	/**
	 * Method called when an {@code AudioCue} instance event 
	 * occurs.
	 * 
	 * @param event {@code AudioCueInstanceEvent} 
	 */
	void instanceEventOccurred(AudioCueInstanceEvent event);
}
