package com.adonax.audiocue;

/**
 * Represents an event in the life cycle of an {@code AudioCue} 
 * instance and is passed as an argument to objects that implement the 
 * {@code AudioCueListener} interface and are registered to listen.
 * <p>
 * {@code AudioCue} supports concurrent media writes by managing
 * a pool of instances. The lifecycle of an instance is as
 * follows:
 * <ul>
 * <li>OBTAIN_INSTANCE: an instance is obtained from the pool
 * of available instances if the limit of concurrent instances 
 * is not exceeded</li> 
 * <li>START_INSTANCE: an instance starts to play</li> 
 * <li>LOOP: a instance that finishes playing restarts from the
 * beginning</li>
 * <li>STOP_INSTANCE: a playing instance is stopped (but can
 * be restarted)</li>
 * <li>RELEASE_INSTANCE: an instance is released back into the 
 * pool of available instances.</li>
 * </ul>
 * <p>
 * An {@code AudioCueInstanceEvent} holds following immutable fields:
 * <ul>
 * <li><strong>type</strong> - an {@code enum}, 
 * {@code AudioCueInstanceEvent.Type} designating the event</li>
 * <li><strong>time</strong> - a {@code long} containing the time 
 * of occurrence of the event, to the nearest millisecond</li>
 * <li><strong>source</strong> - the parent {@code AudioCue}</li>
 * <li><strong>frame</strong> - a {@code double} containing the frame 
 * (may be a fractional value) that was current at the time of the event</li>
 * <li><strong>instanceID</strong> - an {@code int} used to identify the 
 * {@code AudioCue} instance.</li></ul>
 * 
 * @author Philip Freihofner
 * @version AudioCue 2.0.0
 */
public class AudioCueInstanceEvent {

	/**
	 * An enumeration of events that occur during the lifetime of an 
	 * {@code AudioCue} instance. 
	 */
	static public enum Type {
		/**
		 * Indicates that an instance has been obtained from 
		 * the pool of available instances.
		 */
		OBTAIN_INSTANCE, 
		/**
		 * Indicates that an instance has been released and 
		 * returned to the pool of available instances.
		 */
		RELEASE_INSTANCE,
		/**
		 * Indicates that an instance has started playing.
		 */
		START_INSTANCE, 
		/**
		 * Indicates that an instance has stopped playing.
		 */
		STOP_INSTANCE, 
		/**
		 * Indicates that an instance has finished playing
		 * and is starting to play again from the beginning
		 * of the media.
		 */
		LOOP};
	
	/**
	 * the triggering event
	 */
	public final Type type;
	
	/**
	 * the time in milliseconds when the event occurred 
	 */
	public final long time;
	
	/**
	 * the {@code AudioCue} from which the event originated 
	 */
	public final AudioCue source;
	
	/**
	 * the identifier for the parent AudioCue
	 */
	public final int instanceID;
	
	/**
	 * the sample frame number (may be fractional) current 
	 * at the time of the event
	 */
	public final double frame;
	
	/**
	 * Constructor for {@code AudioCueInstanceEvent}, creating an 
	 * instance of a data-holding class to be passed as a parameter
	 * for the {@code AudioCuelistener} method {@code instanceEventOccurred} 
	 *  
	 * @param type       - an {@code enum} that designates type of event
	 * @param source     - the parent {@code AudioCue}
	 * @param instanceID - an {@code int} identifier for the parent 
	 * 					   {@code AudioCue} instance
	 * @param frame      - a {@code double} that holds the sample frame 
	 *                     current at the time of the event
	 */
	public AudioCueInstanceEvent(Type type, AudioCue source, 
			int instanceID, double frame)
	{
		this.type = type;
		time = System.currentTimeMillis();
		this.source = source;
		this.instanceID = instanceID;
		this.frame = frame;
	}
}

