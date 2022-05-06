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
