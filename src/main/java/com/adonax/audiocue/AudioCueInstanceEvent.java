package com.adonax.audiocue;

/**
 * The {@code AudioCueInstanceEvent} class represents an event  
 * in the life cycle of an {@code AudioCue} <em>instance</em>. 
 * An {@code AudioCueInstanceEvent} is passed as 
 * an argument to every class that implements the 
 * {@code AudioCueListener} interface and is registered to 
 * receive notifications about <em>instance</em> events.
 * <p>
 * An {@code AudioCue} supports concurrent plays of its media, where
 * the maximum number of concurrent plays is equal to the value
 * held in the final variable {@code AudioCue.polyphony}.  
 * Each concurrent play is referred to as an <em>instance</em>.
 * Instance <em>events</em> mark the life cycle of
 * a single playing or playable instance as follows:
 * <ul>
 * <li>OBTAIN_INSTANCE: an instance is obtained from the pool
 * of available instances if the limit of allocated concurrent
 * instances is not exceeded</li> 
 * <li>START_INSTANCE: an instance starts to play</li> 
 * <li>LOOP: a instance that finishes playing restarts from the
 * beginning</li>
 * <li>STOP_INSTANCE: a playing instance is stopped (but can
 * still be restarted)</li>
 * <li>RELEASE_INSTANCE: an instance is released back into the 
 * pool of available instances. </li>
 * </ul>
 * <p>
 * The information packaged in the {@code AudioCueInstanceEvent}
 * is contained in the following immutable fields:
 * <ul>
 * <li><strong>type</strong> - an {@code enum} designating the 
 * nature of the event</li>
 * <li><strong>time</strong> - a {@code long} containing the time 
 * of occurrence of the event, to the nearest millisecond</li>
 * <li><strong>source</strong> - the originating 
 * {@code AudioCue}</li>
 * <li><strong>frame</strong> - a {@code double} containing the 
 * sample frame (may be fractional) current at the time of the 
 * event</li>
 * <li><strong>instanceID</strong> - an {@code int} used to 
 * identify the {@code AudioCue} instance.</li></ul>
 * 
 * @author Philip Freihofner
 * @version AudioCue 1.2
 * @see http://adonax.com/AudioCue
 */
public class AudioCueInstanceEvent {

	/**
	 * The {@code enum Type} represents events that occur during
	 * the lifetime of an {@code AudioCue} <em>instance</em>. 
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
	 * the hook used to identify the instance
	 */
	public final int instanceID;
	
	/**
	 * the sample frame number (may be fractional) current 
	 * at the time of the event
	 */
	public final double frame;
	
	/**
	 * Constructor for an {@code AudioCueInstanceEvent}, a
	 * class that is passed as a parameter for 
	 * {@code AudioCuelistener}. 
	 *  
	 * @param type an {@code enum} that designates the 
	 * category of {@code AudioCue} instance event
	 * @param source the {@code AudioCue} that is the origin 
	 * of the event
	 * @param instanceID an {@code int} hook used to identify the 
	 * {@code AudioCue}	instance
	 * @param frame a {@code double} that holds the sample 
	 * frame current at the time of the event
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

