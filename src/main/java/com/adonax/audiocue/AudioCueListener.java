package com.adonax.audiocue;

/**
 * A listener interface for receiving notifications of events
 * pertaining to an {@code AudioCue} and to its individual play
 * back instances.
 * <p>
 * The execution of the implementing method {@code instanceEventOccurred}
 * may be on the same thread that processes the audio data (for example, 
 * the {@code AudioCueInstanceEvent} type {@code LOOP}), and thus should
 * be coded for brevity in order to minimize non-audio processing that 
 * could potentially contribute to dropouts during media play. 
 * 
 * @author Philip Freihofner
 * @version AudioCue 2.0.0
 */
public interface AudioCueListener 
{
	/**
	 * Method called when an {@code AudioCue} executes its 
	 * {@code open} method.
	 * 
	 * @param now            - a {@code long} holding millisecond value 
	 * @param threadPriority - an {@code int} specifying thread
	 *                         priority
	 * @param bufferSize     - and {@code int} specifying buffer size
	 *                         in frames
	 * @param source         - the parent {@code AudioCue} that originated 
	 * 						   the notification
	 */
	void audioCueOpened(long now, int threadPriority, int bufferSize, 
			AudioCue source);
	/**
	 * Method called when an {@code AudioCue} executes its 
	 * {@code close} method.
	 * S
	 * @param now    - a {@code long} holding a millisecond value
	 * @param source - the parent {@code AudioCue} that originated the
	 *                 notification
	 */
	void audioCueClosed(long now, AudioCue source);
	
	/**
	 * Method called when an {@code AudioCue} instance event 
	 * occurs.
	 * 
	 * @param event -an {@code AudioCueInstanceEvent} 
	 */
	void instanceEventOccurred(AudioCueInstanceEvent event);
}
