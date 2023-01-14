package com.adonax.audiocue;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Function;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line.Info;

import com.adonax.audiocue.AudioCueFunctions.PanType;
import com.adonax.audiocue.AudioCueFunctions.VolType;
import com.adonax.audiocue.AudioCueInstanceEvent.Type;

import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * The {@code AudioCue} class functions as a data line, where the
 * audio data played directly from memory. {@code AudioCue} is modeled 
 * upon {@code javax.sound.sampled.Clip} but with additional capabilities.
 * It can play multiple instances concurrently, and offers individual
 * dynamic controls for volume, panning and speed for each concurrently 
 * playing instance.
 * <p>
 * An {@code AudioCue} is created using the static factory method
 * {@code makeAudioCue}. When doing so, the media data is loaded 
 * either from a "CD Quality" wav file (44100 fps, 16-bit, stereo, 
 * little-endian) or a float array of stereo PCM normalized to the 
 * range -1 to 1, at 44100 fps. Once loaded, the media data is 
 * immutable.
 * <p>
 * {@code AudioCue} achieves concurrent playback by treating state at
 * two levels. A distinction is made between the {@code AudioCue} 
 * which holds the audio data, and a playback <em>instance</em> which
 * controls a cursor that traverses over that data and corresponds to
 * a single sounding playback. Methods that dynamically affect
 * playback (volume, pan, speed) operate at the instance level. When 
 * using the factory method to create an {@code AudioCue}, the
 * maximum number of simultaneously playing instances to be supported
 * is set, along with the data source.
 * <p>
 * An {@code AudioCue} is either open or closed. Upon opening, an
 * output line is obtained from the system. That line will either be
 * held directly by the {@code AudioCue} or provided indirectly by an
 * {@code AudioMixer}, in which case 'opening' refers to the act
 * of registering as an {@code AudioMixerTrack} with an 
 * {@code AudioMixer}. Upon closing, the {@code AudioCue} releases the
 * system audio line, or if registered with an {@code AudioMixer}, 
 * unregisters. Opening and closing events are broadcast to registered
 * listeners implementing the methods
 * {@code AudioCueListener.audioCueOpened} and
 * {@code AudioCueListener.audioCueClosed}.
 * <p>
 * The line used for output is a 
 * {@code javax.sound.sampled.SourceDataLine}. The format specified
 * for the line is 44100 fps, 16-bit, stereo, little-endian, a format
 * also known as "CD Quality". This format is one of the most widely
 * supported and is the only format available for output via 
 * {@code AudioCue}. If a {@code javax.sound.sampled.Mixer} is not
 * specified, the default behavior is to obtain the line from the
 * system's default {@code Mixer}, with a buffer size of 1024 frames
 * (4192 bytes). The line will be run from within a dedicated thread,
 * with a thread priority of {@code HIGHEST}. Given that the 
 * processing of audio data usually progresses much faster than time
 * it takes to play the media, this thread can be expected to spend
 * most of its time in a blocked state, which allows maximizing the
 * thread priority without impacting overall performance. An 
 * alternative {@code Mixer}, buffer length or thread priority can be
 * specified as parameters to the {@code open} method. 
 * <p>
 * An <em>instance</em> can either be <em>active</em> or not active,
 * and, if active, can either be <em>running</em> or not running. 
 * Instances are initially held in a pool as 'available instances'.
 * An instance becomes active when it is removed from that pool. 
 * Methods which remove an instance from the pool either return an
 * {@code int} identifier or -1 if there are no available instances.
 * An active instance can receive commands pertaining to the cursor's
 * location in the audio data, to starting or stopping, as well as
 * volume, pan, and speed changes, and other commands. An inactive
 * instance can only receive a command that withdraws it from the 
 * 'available instances' pool. An instance that is <em>running</em>
 * is one that is currently being played.
 * <p>
 * Each instance holds its own state variables for volume, pan and
 * speed, cursor location, and looping. Methods are provided for
 * changing these values. If the methods are called while the 
 * instance is not running, the new value is stored immediately and 
 * will take affect when the instance is restarted. If the instance
 * is running, and the change is for volume, pan, or speed, the new 
 * value will be arrived at incrementally, with the increments 
 * occurring behind the scenes on a per frame basis. This is done to
 * prevent discontinuities in the signal that might result in audible
 * clicks. If a running instance's property is updated before the 
 * incremental changes have completed, the new target value takes 
 * precedence, and a new increment is created based on the current,
 * in-progress value.
 * <p>
 * An instance may be played on a <em>fire-and-forget</em> basis, in 
 * which case it is automatically returned to the pool of available
 * instances (active == false). Alternatively, the instance can be
 * allowed to stay active upon completion. The behavior upon play 
 * completion is controlled by the public method 
 * {@code setRecycleWhenDone(int, boolean)}, where {@code int} is the
 * instance identifier. When a {@code play} method is called, the 
 * {@code recycledWhenDone} value is set to {@code true} by default.
 * When {@code obtainInstance} is used, the {@code recycledWhenDone}
 * value is set to {@code false}.
 * <p>
 * <strong>Examples</strong>: (assumes the audioCue is open) 
 * <pre><code>    // (1) Fire-and-Forget, with default values.
 *    audioCue.play(); // (vol = 1, pan = 0, speed = 1, loop = 0)
 *    // instance will be recycled upon completion.
 *    
 *    // (2) Fire-and-forget, with explicit values
 *    int play0 = audioCue.play(0.5, -0.75, 2, 1); 
 *    // will play at "half" volume, "half way" to the left pan,
 *    // double the speed, and will loop back and play again one time.
 *    // Instance will be recycled upon completion, however, the
 *    // play0 variable allows one to make further changes to the
 *    // instance if executed prior to the completion of the 
 *    // playback.
 *    
 *    // (3) Using obtainInstance()
 *    int footstep = footstepCue.obtainInstance();
 *    for (int i = 0; i &#60; 10; i++) {
 *        // play the successive footsteps more quietly
 *        footstepCue.setVolume(footstep, 1 - (i * 0.1));
 *        // successive footsteps will travel from left to right
 *        footstepCue.setPan(footstep, -1 + (i * 0.2));
 *        // cursor must be manually placed at the beginning
 *        footstepCue.setFramePosition(footstep, 0);
 *        footstepCue.start(footstep);
 *        Thread.sleep(1000); // Allow time between each footstep start.
 *        // This assumes that the cue is shorter than 1 second in 
 *        // in length and each start will stop on its own.
 *    }
 *    // Another instance might be associated with a slightly 
 *    // different speed, for example 1.1, implying a different 
 *    // individual with a slightly lighter foot fall.
 *    </code></pre>
 * <p>   
 * More extensive examples can be found in the companion github project
 * <em>audiocue-demo</em>.   
 * 
 * @since 2.0.0
 * @version 2.1.0
 * @author Philip Freihofner
 */
public class AudioCue implements AudioMixerTrack, AutoCloseable
{
	/**
	 * A {@code javax.sound.sampled.AudioFormat}, set to the only
	 * format used by {@code AudioCue}, also known as 'CD quality.'
	 * The type is signed PCM, with a rate of 44100 frames per second, 
	 * with 16 bit encoding for each PCM value, stereo, and with the 
	 * constituent bytes of each PCM value given in little-endian order.
	 */
	public static final AudioFormat audioFormat = 
			new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 
					44100, 16, 2, 4, 44100, false);
	/**
	 * An immutable {@code javax.sound.sampled.Line.Info} that is used when obtaining a
	 * {@code SourceDataLine} for media output.
	 */
	public static final Info info =	
			new DataLine.Info(SourceDataLine.class, audioFormat);
	/**
	 * A value indicating the default number of PCM frames in a buffer used in {@code AudioCuePlayer}
	 * for processing media output.
	 */
	public static final int DEFAULT_BUFFER_FRAMES = 1024 ;
	/**
	 * A value indicating the number of frames over which the volume changes incrementally
	 * when a new volume is given.  
	 */
	public static final int VOLUME_STEPS = 1024;
	/**
	 * A value indicating the number of frames over which the speed setting changes incrementally
	 * when a new speed value is given.  
	 */
	public static final int SPEED_STEPS = 4096;
	/**
	 * A value indicating the number of frames over which the pan setting changes incrementally
	 * when a new pan value is given.  
	 */
	public static final int PAN_STEPS = 1024;
	
	private final LinkedBlockingDeque<AudioCueCursor> availables;
	private final float[] cue;
	private final int cueFrameLength;
	private final AudioCueCursor[] cursors;
	private final int polyphony;
	
	private AudioCuePlayer player;
	private AudioMixer audioMixer; 
	
	private boolean playerRunning;
	private volatile boolean trackRunning;
	private float[] readBuffer;
	
	
	private String name;
	/**
	 * Returns the name associated with the {@code AudioCue}.
	 * @return the name as a {@code String}  
	 */
	public String getName() { return name; }
	/**
	 * Sets the name of the {@code AudioCue}.
	 * 
	 * @param name - a {@code String} to associate with this
	 * {@code AudioCue}
	 */
	public void setName(String name) {this.name = name;}
	
	private CopyOnWriteArrayList<AudioCueListener> listeners;
	
	/**
	 * Registers an {@code AudioCueListener} to receive 
	 * notifications of events pertaining to the {@code AudioCue}
	 * and its playing or playable instances.
	 * <p> 
	 * Notifications occur on the thread which processes the
	 * audio signal, and includes events such as the starting, 
	 * stopping, and looping of instances. Implementations of 
	 * the methods that receive these notifications should be 
	 * coded for brevity in order to minimize extraneous 
	 * processing on the audio thread.
	 * 
	 * @param listener - an object implementing the 
	 * {@code AudioCueListener} interface
	 */
	public void addAudioCueListener(AudioCueListener listener)
	{
		listeners.add(listener);
	}
	
	/**
	 * Removes an {@code AudioCueListener} from receiving 
	 * notifications of events pertaining to the {@code AudioCue}
	 * and its playing instances.
	 * 
	 * @param listener - an object implementing the 
	 * {@code AudioCueListener} interface
	 */
	public void removeAudioCueListener(AudioCueListener listener)
	{
		listeners.remove(listener);
	}
			
	private Function<Float, Float> vol;

	/**
	 * Assigns a function to map linear volume settings to volume
	 * factors used to control the sound cue's amplitude.
	 * 
	 * @param volType - a member of the {@code enum AudioCue.VolType}
	 * @see VolType
	 */
	public void setVolType(VolType volType)
	{
		vol = volType.vol;
	}
	
	private Function<Float, Float> panL;
	private Function<Float, Float> panR;
	
	/**
	 * Assigns the type of panning to be used.
	 * 
	 * @param panType - a member of the {@code enum AudioCue.PanType}
	 * @see PanType
	 */
	public void setPanType(PanType panType)
	{
		panL = panType.left;
		panR = panType.right;
	}

	/**
	 * Private constructor, used internally.
	 * 
	 * @param cue       - a {@code float} array of stereo, signed, normalized PCM
	 * @param name      - a {@code String} to be associated with the 
	 * 				      {@code AudioCue} 
	 * @param polyphony - an {@code int} specifying the maximum number of 
	 *                    concurrent instances
	 */
	private AudioCue(float[] cue, String name, int polyphony)
	{
		this.cue = cue;
		this.cueFrameLength = cue.length / 2;
		this.polyphony = polyphony;
		this.name = name;
		
		availables = new LinkedBlockingDeque<AudioCueCursor>(polyphony);
		cursors = new AudioCueCursor[polyphony];
		
		for (int i = 0; i < polyphony; i++)
		{	
			cursors[i] = new AudioCueCursor(i);
			cursors[i].resetInstance();
			availables.add(cursors[i]);
		}
		
		// default readBuffer
		readBuffer = new float[DEFAULT_BUFFER_FRAMES * 2];
		
		// default volume function
		setVolType(VolType.EXP_X4);
		
		// default pan calculation function
		setPanType(PanType.SINE_LAW);
		
		listeners = new CopyOnWriteArrayList<AudioCueListener>();
	}
	
	/**
	 * Creates and returns a new {@code AudioCue}. This method 
	 * allows the direct insertion of a {@code float} 
	 * PCM array as an argument, where the data is presumed 
	 * to be stereo signed, normalized floats with a sample
	 * rate of 44100 frames per second. The name for this
	 * cue is set by the {@code name} argument.
	 * The maximum number of concurrent playing instances
	 * is set with the {@code polyphony} argument. 
	 * 
	 * @param cue - a {@code float} array of audio data
	 * in "CD Quality" format, scaled to the range 
	 * [-1, 1]
	 * @param name - a {@code String} to be associated
	 * with the {@code AudioCue}
	 * @param polyphony - an {@code int} specifying 
	 * the maximum number of concurrent instances
	 * @return AudioCue
	 */
	public static AudioCue makeStereoCue(float[] cue, 
			String name, int polyphony)
	{
		return new AudioCue(cue, name, polyphony);
	}
	
	/**
	 * Creates and returns a new {@code AudioCue}. The file
	 * designated by the {@code URL} argument is loaded and
	 * held in memory. Only one format, known as "CD Quality",
	 * is supported: 44100 frames per second, 16-bit encoding, 
	 * stereo, little-endian. The maximum number of concurrent
	 * playing instances is given as the {@code polyphony} argument.
	 * The file name is derived from the {@code URL} argument, but 
	 * can be changed via the method {@code setName}.
	 * 
	 * @param url       - a {@code URL} for the source file 
	 * @param polyphony - an {@code int} specifying the maximum 
	 *                    number of concurrent instances
	 * @return AudioCue
	 * @throws UnsupportedAudioFileException if the media being loaded
	 * 					is not 44100 fps, 16-bit, stereo, little-endian
	 * @throws IOException if unable to load the file
	 */
	public static AudioCue makeStereoCue(URL url, int polyphony) 
			throws UnsupportedAudioFileException, IOException
	{
		String urlName = url.getPath();
		int urlLen = urlName.length();
		String name = urlName.substring(urlName.lastIndexOf("/") + 1, urlLen);
		float[] cue = AudioCue.loadURL(url);
		
		return new AudioCue(cue, name, polyphony);
	}

	// Currently assumes stereo format ("CD Quality") 
	private static float[] loadURL(URL url) throws UnsupportedAudioFileException, 
			IOException
	{
		AudioInputStream ais = AudioSystem.getAudioInputStream(url);

		int framesCount = 0;
		if (ais.getFrameLength() > Integer.MAX_VALUE >> 1)
		{
			System.out.println(
					"WARNING: Clip is too large to entirely fit!");
			framesCount = Integer.MAX_VALUE >> 1;
		}
		else
		{
			framesCount = (int)ais.getFrameLength();
		}
		
		// stereo output, so two entries per frame
		float[] temp = new float[framesCount * 2];
		long tempCountdown = temp.length;

		int bytesRead = 0;
		int bufferIdx;
		int clipIdx = 0;
		byte[] buffer = new byte[1024];
		while((bytesRead = ais.read(buffer, 0, 1024)) != -1)
		{
			bufferIdx = 0;
			for (int i = 0, n = (bytesRead >> 1); i < n; i ++)
			{
				if ( tempCountdown-- >= 0)
				{
					temp[clipIdx++] = 
							( buffer[bufferIdx++] & 0xff )
							| ( buffer[bufferIdx++] << 8 ) ;
				}
			}
		}
		// Done with AudioInputStream
		ais.close();
		
		for (int i = 0; i < temp.length; i++)
		{
			temp[i] = temp[i] / 32767f;
		}

		return temp;
	}
	
	/**
	 * Returns a copy of the signed, normalized float PCM array for this 
	 * {@code AudioCue}.
	 * 
	 * @return a {@code float[]} new copy of the internal array of the
	 * PCM for the {@code AudioCue}
	 */
	public float[] getPcmCopy() {
		return Arrays.copyOf(cue, cue.length);
	}
	
	/**
	 * Readies this {@code AudioCue} for media play by instantiating, registering,
	 * and running the inner class {@code AudioCuePlayer} with default settings.
	 * Internally, the registered {@code AudioCuePlayer} instance obtains and 
	 * configures a {@code javax.sound.sampled.SourceDataLine} to write to the 
	 * default system {@code javax.sound.sampled.Mixer}, and will make use of the 
	 * default internal buffer size of 1024 PCM frames, and will run with the 
	 * default thread priority setting of 10.
	 * <p>
	 * Once completed, this {@code AudioCue} is marked open and the 
	 * {@code AudioCueListener.audioCueOpened} method is called on every registered 
	 * {@code AudioCueListener}.
	 * <p>
	 * NOTE: data <em>can</em> be read from an {@code AudioCue} even if it is not open. The 
	 * data that is read from an unopened {@code AudioCue} will not be written to
	 * a {@code SourceDataLine} and will not be heard.
	 * 
	 * @throws IllegalStateException if this {@code AudioCue} is already open
	 * @throws LineUnavailableException if unable to obtain a {@code SourceDataLine} 
	 *                         for the player, which could occur if the Mixer does
	 *                         not support the {@code AudioFormat} implemented by 
	 *                         {@code AudioCue}
	 * @see #audioFormat
	 * @see #open(Mixer, int, int)
	 * @see AudioCueListener#audioCueOpened(long, int, int, AudioCue)
	 */
	public void open() throws LineUnavailableException
	{
		open(null, DEFAULT_BUFFER_FRAMES, Thread.MAX_PRIORITY);
	}
	
	/**
	 * Readies this {@code AudioCue} for media play by instantiating, registering,
	 * and running the inner class {@code AudioCuePlayer} with a custom buffer 
	 * size. As the performance of the AudioCuePlayer is subject to tradeoffs 
	 * based upon the size of an internal buffer, a number of frames other than 
	 * the default of 1024 can be specified with this method. A lower value responds
	 * more quickly to dynamic requests, but is more prone to underflow which can
	 * result in audible drop outs. The registered {@code AudioCuePlayer} instance 
	 * obtains and configures a {@code javax.sound.sampled.SourceDataLine} to write 
	 * to the default system {@code javax.sound.sampled.Mixer}, and will run with 
	 * the default thread priority setting of 10.
	 * <p>
	 * Once completed, this {@code AudioCue} is marked open and the 
	 * {@code AudioCueListener.audioCueOpened} method is called on every registered 
	 * {@code AudioCueListener}.
	 * <p>
	 * NOTE: data <em>can</em> be read from an {@code AudioCue} even if it is not open. The 
	 * data that is read from an unopened {@code AudioCue} will not be written to
	 * a {@code SourceDataLine} and will not be heard.
	 *  
	 * @param bufferFrames - an {@code int} specifying the size of the internal
	 * 						 buffer in PCM frames
	 * @throws IllegalStateException if this {@code AudioCue} is already open
	 * @throws LineUnavailableException if unable to obtain a {@code SourceDataLine} 
	 *                         for the player, which could occur if the Mixer does
	 *                         not support the {@code AudioFormat} implemented by 
	 *                         {@code AudioCue}
	 * @see #audioFormat
	 * @see #open(Mixer, int, int)
	 * @see AudioCueListener#audioCueOpened(long, int, int, AudioCue)
	 */
	public void open(int bufferFrames) throws LineUnavailableException
	{
		open(null, bufferFrames, Thread.MAX_PRIORITY);
	}
	
	/**
	 * Readies this {@code AudioCue} for media play by instantiating, registering,
	 * and running the inner class {@code AudioCuePlayer} with explicit settings
	 * for the {@code javax.sound.sampled.Mixer}, the number of PCM frames used 
	 * in an internal buffer, and the priority level for the thread created to 
	 * handle the media play. Internally, the registered {@code AudioCuePlayer} 
	 * instance obtains and configures a {@code javax.sound.sampled.SourceDataLine}
	 * to write to the provided {@code javax.sound.sampled.Mixer} rather than the 
	 * default system Mixer. As the performance of the AudioCuePlayer is subject
	 * to tradeoffs based upon the size of an internal buffer, a number of frames
	 * other than the default of 1024 can be specified. A lower value responds
	 * more quickly to dynamic requests, but is more prone to underflow which can
	 * result in audible drop outs. While the default thread priority setting of 
	 * 10 is generally safe given that to audio processing spending a majority of
	 * its time in a blocked state, this method allows specification of a lower 
	 * priority setting.
	 * <p>
	 * Once completed, this {@code AudioCue} is marked open and the 
	 * {@code audioCueOpened} method is called on every registered 
	 * {@code AudioCueListener}.
	 * <p>
	 * NOTE: data <em>can</em> be read from an {@code AudioCue} even if it is not open. The 
	 * data that is read from an unopened {@code AudioCue} will not be written to
	 * a {@code SourceDataLine} and will not be heard. 
	 * 
	 * @param mixer          - a {@code javax.sound.sampled.Mixer}. If {@code null}, 
	 *  					   the system default mixer is used.
	 * @param bufferFrames   - an {@code int} specifying the size of the internal
	 * 						   buffer in PCM frames
	 * @param threadPriority - an {@code int} specifying the priority level of the
	 *                         thread, clamped to the range 1 to 10 inclusive
	 * @throws IllegalArgumentException if the thread priority is not in the range 
	 * 						   MIN_PRIORITY to MAX_PRIORITY.                        
	 * @throws IllegalStateException if this {@code AudioCue} is already open
	 * @throws LineUnavailableException if unable to obtain a {@code SourceDataLine} 
	 *                         for the player, which could occur if the Mixer does
	 *                         not support the {@code AudioFormat} implemented by 
	 *                         {@code AudioCue}
	 * @see #audioFormat
	 * @see AudioCueListener
	 * @see javax.sound.sampled.Mixer
	 * @see AudioCueListener#audioCueOpened(long, int, int, AudioCue)
	 */
	public void open(Mixer mixer, int bufferFrames, int threadPriority) 
		throws LineUnavailableException
	{
		if (playerRunning) 
		{
			throw new IllegalStateException(
					"Already open.");
		}
		
		if (threadPriority < Thread.MIN_PRIORITY 
				|| threadPriority > Thread.MAX_PRIORITY) {
			throw new IllegalArgumentException("Thread priority out of range.");
		}
		
		player = new AudioCuePlayer(mixer, bufferFrames);
		Thread t = new Thread(player);

		t.setPriority(threadPriority);
		playerRunning = true;
		t.start();
		
		broadcastOpenEvent(t.getPriority(), bufferFrames, name);
	}
	
	/**
	 * Registers an {@code AudioMixer}, instead of an inner class
	 * {@code AudioCuePlayer}, to handle the media play. This 
	 * {@code AudioCue} will be added as an {@code AudioMixerTrack}
	 * to the registered {@code AudioMixer}, and the buffer size and
	 * the thread priority of the {@code AudioMixer} will take effect
	 * for media play. 
	 * <p>
	 * Once completed, the {@code AudioCue} is marked open and the 
	 * {@code AudioCueListener.audioCueOpened} method is called on every 
	 * registered {@code AudioCueListener}.
	 * <p>
	 * NOTE: data <em>can</em> be read from an {@code AudioCue} even if it is not open. The 
	 * data that is read from an unopened {@code AudioCue} will not be written to
	 * a {@code SourceDataLine} and will not be heard.
	 * 
	 * @param audioMixer - the {@code AudioMixer} that will handle media output
	 * 					   for this {@code AudioCue}
	 * @throws IllegalStateException if the {@code AudioCue} is already open
	 * @see AudioMixer
	 * @see AudioCueListener#audioCueOpened(long, int, int, AudioCue)
	 */
	public void open(AudioMixer audioMixer)
	{
		if (playerRunning) 
		{
			throw new IllegalStateException("Already open.");
		}
		playerRunning = true;
		// default: AudioCueTrack is open
		trackRunning = true;
		this.audioMixer = audioMixer;
		
		// assigned size is frames * stereo
		readBuffer = new float[audioMixer.bufferFrames * 2];
		
		audioMixer.addTrack(this);
		audioMixer.updateTracks();
		
		broadcastOpenEvent(audioMixer.threadPriority, 
				audioMixer.bufferFrames, name);
	}
	
	/**
	 * Releases resources allocated for media play. If the {@code AudioCue}
	 * was opened as a stand alone cue, its inner class {@code AudioPlayer}
	 * runnable will be allowed to end, and allocated resources will be 
	 * cleaned up. If the {@code AudioCue} was opened as a track on an 
	 * {@code AudioMixer}, the track will be removed from the {@code AudioMixer}.
	 * <p>
	 * Once completed, the {@code AudioCue} is marked closed and the
     * {@code AudioCueListener.audioCueClosed} method is called on every 
     * registered {@code AudioCueListener}.
	 *  
	 * @throws IllegalStateException if player is already closed
	 * @see AudioCueListener#audioCueClosed(long, AudioCue)
	 */
	public void close()
	{
		if (playerRunning == false)
		{
			throw new IllegalStateException("Already closed.");
		}

		if (audioMixer != null)
		{
			audioMixer.removeTrack(this);
			audioMixer.updateTracks();
			audioMixer = null;
		} else {
			// allows player thread to end
			player.stopRunning();
		}
		
		playerRunning = false;
		
		broadcastCloseEvent(name);
	}
	
	/**
	 * Gets the media length in sample frames.
	 * 
	 * @return length in sample frames
	 */
	public long getFrameLength()
	{
		return cueFrameLength;
	}

	/**
	 * Gets the media length in microseconds.
	 * 
	 * @return length in microseconds
	 */
	public long getMicrosecondLength()
	{
		return (long)((cueFrameLength * 1_000_000.0) 
				/ audioFormat.getFrameRate());
	}

	
	/**
	 * Obtains an {@code int} instance identifier from a pool of 
	 * available instances and marks this instance as 'active'. If 
	 * no playable instances are available, the method returns -1.
	 * <p>
	 * The instance designated by this identifier is 
	 * <em>not</em> automatically recycled back into 
	 * the pool of available instances when it finishes playing. 
	 * To put the instance back in the pool of available instances, 
	 * the method {@code releaseInstance} must be called. To 
	 * change the behavior so that the instance <em>is</em> 
	 * returned to the pool when it plays completely to the end,
	 * use {@code setRecycleWhenDone(int, boolean)}.
	 * <p>
	 * When executed, the {@code AudioCueListener.instanceEventOccurred} 
	 * method will be called with an argument of 
	 * {@code AudioCueInstanceEvent.Type.OBTAIN_INSTANCE}.
	 * 
	 * @return an {@code int} instance ID for an active instance, 
	 *         or -1 if no instances are available
	 * @see #releaseInstance(int)
	 * @see #setRecycleWhenDone(int, boolean)
	 * @see AudioCueListener#instanceEventOccurred(AudioCueInstanceEvent)
	 * @see AudioCueInstanceEvent.Type#OBTAIN_INSTANCE
	 */
	public int obtainInstance()
	{
		AudioCueCursor acc = availables.pollLast();
		
		if (acc == null) return -1;
		else 
		{
			acc.isActive = true;
			broadcastCreateInstanceEvent(acc);
			return acc.id;
		}
	}
	
	/**
	 * Releases an {@code AudioCue} instance, making  it available
	 * as a new concurrently playing instance. Once released, and
	 * back in the pool of available instances, an instance cannot 
	 * receive updates. 
	 * <p>
	 * When executed, the {@code AudioCueListener.instanceEventOccurred} 
	 * method will be called with an argument of 
	 * {@code AudioCueInstanceEvent.Type.RELEASE_EVENT}.
	 * 
	 * @param instanceID - an {@code int} identifying the instance 
	 *                       to be released
	 * @see #obtainInstance()
	 * @see AudioCueListener#instanceEventOccurred(AudioCueInstanceEvent)
	 * @see AudioCueInstanceEvent.Type#RELEASE_INSTANCE
	 * 
	 */
	public void releaseInstance(int instanceID)
	{
		cursors[instanceID].resetInstance();
		availables.offerFirst(cursors[instanceID]);
		broadcastReleaseEvent(cursors[instanceID]);
	}
	
	/**
	 * Obtains an {@code AudioCue} instance and, if the {@code AudioCue} has 
	 * been <em>opened</em>, starts the cue playing in its own thread, from 
	 * the beginning, with default values: full volume, center pan and at 
	 * normal speed, and immediately returns an {@code int} identifying the 
	 * instance, or, returns -1 if no {@code AudioCue} instance is available.
	 * <p>
	 * If an {@code AudioCue} instance is available to play, the 
	 * {@code AudioCueListener.instanceEventOccurred} method
	 * will be called twice, first with the argument
     * {@code AudioCueInstanceEvent.Type.OBTAIN_INSTANCE} and
	 * then with {@code AudioCueInstanceEvent.Type.START_INSTANCE}.
	 * This instance will be set to automatically recycle back into 
	 * the pool of available instances when playing completes.
	 * <p>
	 * NOTE: the {@code play} method <em>can</em> be called on an 
	 * unopened {@code AudioCue}. If unopened, calling the 
	 * {@code AudioCuePlayer.readTrack} method will advance the 
	 * {@code AudioCueCursor} and return a buffer of PCM data, but
	 * will not write the data to the sound system.
	 * 
	 * @return an {@code int} identifying the playing instance,
	 *         or -1 if no instance is available
	 * @see AudioCueListener#instanceEventOccurred(AudioCueInstanceEvent)
	 * @see AudioCueInstanceEvent.Type#OBTAIN_INSTANCE
	 * @see AudioCueInstanceEvent.Type#START_INSTANCE
	 */
	public int play()
	{
		return play(1, 0, 1, 0);
	}	
	
	/**
	 * Obtains an {@code AudioCue} instance and, if the {@code AudioCue} has 
	 * been <em>opened</em>, starts the cue playing in its own thread, from 
	 * the beginning, at the given volume, at the default center pan, and at 
	 * the default normal speed, and immediately returns an {@code int} 
	 * identifying the instance, or, returns -1 if no {@code AudioCue} instance
	 * is available. 
	 * <p>
	 * If an {@code AudioCue} instance is available to play, the 
	 * {@code AudioCueListener.instanceEventOccurred} method 
	 * will be called twice, first with the argument
     * {@code AudioCueInstanceEvent.Type.OBTAIN_INSTANCE} and then
	 * with {@code AudioCueInstanceEvent.Type.START_INSTANCE}. This
	 * instance will be set to automatically recycle back into the 
	 * pool of available instances when playing completes.
	 * <p>
	 * NOTE: the {@code play} method <em>can</em> be called on an 
	 * unopened {@code AudioCue}. If unopened, the 
	 * {@code AudioCuePlayer.readTrack} method will advance the 
	 * {@code AudioCueCursor} and return a buffer of PCM data when 
	 * called, but will not write the data to the sound system.
	 *  
	 * @param volume - a {@code double} in the range [0, 1]
	 * @return an {@code int} identifying the playing instance,
	 *         or -1 if no instance is available
	 * @see AudioCueListener#instanceEventOccurred(AudioCueInstanceEvent)
	 * @see AudioCueInstanceEvent.Type#OBTAIN_INSTANCE
	 * @see AudioCueInstanceEvent.Type#START_INSTANCE
	 */
	public int play(double volume)
	{
		return play(volume, 0, 1, 0);
	}	
	
	/**
	 * Obtains an {@code AudioCue} instance and, if the {@code AudioCue} has 
	 * been <em>opened</em>, starts the cue playing in its own thread, from 
	 * the beginning, at the given volume, pan, speed and number of repetitions, 
	 * and returns an {@code int} identifying the instance, or, returns -1 if 
	 * no {@code AudioCue} instance is available.
	 * <p>
	 * If an {@code AudioCue} instance is available for play back, 
	 * the {@code AudioCueListener.instanceEventOccurred} method 
	 * will be called twice, first with the argument
     * {@code AudioCueInstanceEvent.Type.OBTAIN_INSTANCE} and then
	 * with {@code AudioCueInstanceEvent.Type.START_INSTANCE}. This
	 * instance will be set to automatically recycle back into the 
	 * pool of available instances when playing completes.
	 * <p>
	 * NOTE: the {@code play} method <em>can</em> be called on an 
	 * unopened {@code AudioCue}. If unopened, the 
	 * {@code AudioCuePlayer.readTrack} method will advance the 
	 * {@code AudioCueCursor} and return a buffer of PCM data when 
	 * called, but will not write the data to the sound system. 
	 * 
	 * @param volume - a {@code double} within the range [0, 1]
	 * @param pan    - a {@code double} within the range [-1, 1]
	 * @param speed  - a {@code double} factor that is multiplied 
	 *                 to the frame rate 
	 * @param loop   - an {@code int} that specifies a number of 
	 * 				   <em>additional</em> plays (looping)
	 * @return an {@code int} identifying the playing instance, 
	 *         or -1 if no instance is available
	 * @see AudioCueListener#instanceEventOccurred(AudioCueInstanceEvent)
	 * @see AudioCueInstanceEvent.Type#OBTAIN_INSTANCE
	 * @see AudioCueInstanceEvent.Type#START_INSTANCE
	 */
	public int play(double volume, double pan, double speed, int loop)
	{
		int idx = obtainInstance();
		if (idx < 0) 
		{
			System.out.println("All available notes are playing.");
			return idx;
		}
		
		setVolume(idx, volume);
		setPan(idx, pan);
		setSpeed(idx, speed);
		setLooping(idx, loop);
		setRecycleWhenDone(idx, true);
		
		start(idx);

		return idx;
	}
	
	/**
	 * Launches the playback of the specified {@code AudioCue} instance
	 * from its current position in the data, using existing volume, pan, 
	 * speed and number of repetitions. Returns immediately.
	 * <p>
	 * If an {@code AudioCue} instance is able to start, the 
	 * {@code AudioCueListener.instanceEventOccurred} method 
	 * will be ca	lled with the argument
	 * {@code AudioCueInstanceEvent.Type.START_INSTANCE}. When the
	 * playback ends, the {@code AudioCue} instance is <em>not</em>
	 * recycled, but instead stops running and the current position
	 * remains at the end point of the playback. This method does
	 * <em>not</em> automatically reposition the cursor to the 
	 * beginning of the cue, so, if the goal is to restart from the 
	 * beginning, a repositioning method (such as 
	 * {@code setFramePosition} must first be used to move the cursor.
	 * <p>
	 * If the {@code AudioCue} has not been <em>opened</em>, calls
	 * to {@code AudioMixerTrack.readTrack()} can be used to advance 
	 * the cursors and produce a buffer-length float[] array of the
	 * mix of all the playing instances without sending the data on
	 * to a {@code SourceDataLine} to be heard. 
	 * 
	 * @param instanceID - an {@code int} used to identify an 
	 *                     {@code AudioCue} instance
	 * @throws IllegalStateException if instance is not active
	 *         or if instance is already playing
	 *         
	 * @see AudioCueListener#instanceEventOccurred(AudioCueInstanceEvent)
	 * @see AudioCueInstanceEvent.Type#START_INSTANCE
	 * @see AudioMixerTrack#readTrack()
	 */
	public void start(int instanceID)
	{
		if (!cursors[instanceID].isActive || 
			cursors[instanceID].isPlaying)
		{
			throw new IllegalStateException(name + " instance: "
					+ instanceID + " is inactive");
		}
		cursors[instanceID].instantaneousUpdate();
		cursors[instanceID].isPlaying = true;
		broadcastStartEvent(cursors[instanceID]);
	};
	
	/**
	 * Sends message to indicate that the playing of the instance
	 * associated with the {@code int} identifier should be halted.
	 * Calling this method on an already stopped instance does 
	 * nothing. The instance is left in an open state and is 
	 * <em>not</em> recycled back into the pool of available 
	 * instances.
	 * <p>
	 * The {@code AudioCueListener.instanceEventOccurred} method
	 * will be called with the argument
	 * {@code AudioCueInstanceEvent.Type.STOP_INSTANCE}. When the
	 * {@code start} method is called on an instance that has been
	 * stopped, playback begins from the stopped cursor location.
	 * 
	 * @param instanceID - an {@code int} used to identify an 
	 *                     {@code AudioCue} instance
	 * @throws IllegalStateException if instance is not active
	 * @see AudioCueListener#instanceEventOccurred(AudioCueInstanceEvent)
	 * @see AudioCueInstanceEvent.Type#STOP_INSTANCE
	 */
	public void stop(int instanceID)
	{
		if (!cursors[instanceID].isActive)
		{
			throw new IllegalStateException(name + " instance: "
					+ instanceID + " is inactive");
		}
		
		cursors[instanceID].isPlaying = false;
		broadcastStopEvent(cursors[instanceID]);
	};
		
	/**
	 * Returns the current sample frame number. The frame count
	 * is zero-based. The position may lie in between two frames.
	 * <p>
	 * An instance cannot have its position read if it is
	 * currently playing. An attempt to do so will throw an
	 * {@code IllegalStateException}.
	 *  
	 * @param instanceID - an {@code int} used to identify an 
	 *                     {@code AudioCue} instance
	 * @return a {@code double} corresponding to the current 
	 *         sample frame position
	 * @throws IllegalStateException if instance is not active
	 * @see #setFramePosition(int, double)
	 * @see #setMicrosecondPosition(int, int)
	 * @see #setFractionalPosition(int, double)
	 */
	public double getFramePosition(int instanceID)
	{
		if (!cursors[instanceID].isActive)
		{
			throw new IllegalStateException(name + " instance: "
					+ instanceID + " is inactive");
		}
		
		return cursors[instanceID].cursor;
	}
	
	/**
	 * Sets the play position ("play head") to a specified 
	 * sample frame location. The frame count is zero-based.
	 * The play position can be a fractional amount, lying 
	 * between two frames.
	 * <p>
	 * The input frame position will be clamped to a value that 
	 * lies at or within the start and end of the media data. When 
	 * the instance is next started, it will commence from this 
	 * position.
	 * <p>
	 * An instance cannot have its position changed if it is
	 * currently playing. An attempt to do so will throw an
	 * {@code IllegalStateException}.
	 * 
	 * @param instanceID - an {@code int} used to identify an 
	 *                     {@code AudioCue} instance
	 * @param frame      - a {@code double} giving the frame position 
	 * 					   from which play will commence if the 
	 *                     {@code start} method is executed
	 * @throws IllegalStateException if instance is not active
	 *         or if the instance is playing
	 * @see #getFramePosition(int)
	 */
	public void setFramePosition(int instanceID, double frame)
	{
		if (!cursors[instanceID].isActive || 
				cursors[instanceID].isPlaying)
		{
			throw new IllegalStateException(name + " instance: "
					+ instanceID + " is inactive");
		}
		
		cursors[instanceID].cursor = Math.max(0, Math.min(
				getFrameLength() - 1, (float)frame));
	};
	
	/**
	 * Repositions the play position ("play head") of the
	 * designated {@code AudioCue} instance to the frame that 
	 * corresponds to the specified elapsed time in microseconds. 
	 * The new play position can be a fractional amount, lying 
	 * between two frames.
	 * <p>
	 * The input microsecond position will be clamped to a
	 * frame that lies within or is located at the start or end 
	 * of the media data. When the instance is next started, it 
	 * will commence from this position.
	 * <p>
	 * An instance cannot have its position changed if it is
	 * currently playing. An attempt to do so will throw an
	 * {@code IllegalStateException}.
	 *
	 * @param instanceID   - an {@code int} used to identify an 
	 *                       {@code AudioCue} instance
	 * @param microseconds - an {@code int} in microseconds that
	 *                       corresponds to a position in the  
	 *                       audio media
	 * @throws IllegalStateException if instance is not active
	 * 		   or if instance is playing
	 * @see #getFramePosition(int)
	 */
	public void setMicrosecondPosition(int instanceID, 
			int microseconds)
	{
		if (!cursors[instanceID].isActive || 
				cursors[instanceID].isPlaying)
		{
			throw new IllegalStateException(name + " instance: "
					+ instanceID + " is inactive");
		}

		float frames = (audioFormat.getFrameRate() * microseconds) 
				/ 1000_000f;
		cursors[instanceID].cursor = 
				Math.max(0,	Math.min(cueFrameLength, frames));
	};
	
	/**
	 * Repositions the play position ("play head") of the
	 * designated {@code AudioCue} instance to the frame that 
	 * corresponds to the specified elapsed fractional 
	 * part the total audio cue length. The new play position can
	 * be a fractional amount, lying between two frames. 
	 * <p>
	 * The fractional position argument is clamped to the range 
	 * [0..1], where 1 corresponds to 100% of the media.  When 
	 * restarted, the instance will commence from the new 
	 * sample frame position.
	 * <p>
	 * An instance cannot have its position changed if it is
	 * currently playing. An attempt to do so will throw an
	 * {@code IllegalStateException}.
	 * 
	 * @param instanceID - an {@code int} used to identify the 
	 * 		               {@code AudioCue} instance
	 * @param normal     - a {@code double} in the range [0..1]  
	 *                     that corresponds to a position in 
	 *                     the media
	 * @throws IllegalStateException if instance is not active
	 *         or if instance is playing
	 * @see #getFramePosition(int)
	 */
	public void setFractionalPosition(int instanceID, double normal)
	{
		if (!cursors[instanceID].isActive || 
				cursors[instanceID].isPlaying)
		{
			throw new IllegalStateException(name + " instance: "
					+ instanceID + " is inactive");
		}
		
		cursors[instanceID].cursor = (float)((cueFrameLength) * 
				Math.max(0, Math.min(1, normal)));
	};

	/**
	 * Returns a value indicating the current volume setting
	 * of an {@code AudioCue} instance, ranging [0..1].
	 * 
	 * @param instanceID - an {@code int} used to identify the 
	 *                     {@code AudioCue} instance
	 * @return volume factor as a {@code double}
	 * @throws IllegalStateException if instance is not active
	 * @see #setVolume(int, double)
	 */
	public double getVolume(int instanceID)
	{
		if (!cursors[instanceID].isActive)
		{
			throw new IllegalStateException(name + " instance: "
					+ instanceID + " is inactive");
		}
		return cursors[instanceID].isPlaying ? 
				cursors[instanceID].volume : cursors[instanceID].newTargetVolume; 				
	};

	/**
	 * Sets the volume of the instance. Volumes can be altered 
	 * while an instance is either playing or stopped. When a 
	 * volume change is presented while the instance is playing, a 
	 * smoothing algorithm used to prevent signal discontinuities 
	 * that could result in audible clicks. If a second volume 
	 * change arrives before the first change is completed, the 
	 * most recent volume change takes precedence.
	 * <p>
	 * Arguments are clamped to the range [0..1], with 0 denoting
	 * silence and 1 denoting the natural volume of the sample. In 
	 * other words, the volume control can only diminish the volume
	 * of the media, not amplify it. Internally, the volume argument 
	 * is used as a factor that is directly multiplied against the 
	 * media's PCM values.
	 * 
	 * @param  instanceID - an {@code int} used to identify the 
	 * 						{@code AudioCue} instance
	 * @param  volume     - a {@code float} in the range [0, 1]
	 *                      multiplied against the audio values
	 * @throws IllegalStateException if instance is not active
	 * @see #getVolume(int)
	 */
	public void setVolume(int instanceID, double volume)
	{	
		if (!cursors[instanceID].isActive)
		{
			throw new IllegalStateException(name + " instance: "
					+ instanceID + " is inactive");
		}

		cursors[instanceID].newTargetVolume = 
				(float)Math.min(1, Math.max(0, volume));
	};

	/**
	 * Returns a double in the range [-1, 1] where -1 corresponds
	 * to 100% left, 1 corresponds to 100% right, and 0 
	 * corresponds to center.
	 * <p>
	 * The calculations used to apply the pan are determined 
	 * by the {@code PanType}.
	 * 
	 * @param instanceID - an {@code int} used to identify the 
	 *                     {@code AudioCue} instance
	 * @return the current pan value, ranging [-1, 1]
	 * @throws IllegalStateException if instance is not active
	 * @see #setPan(int, double)
	 * @see PanType
	 * @see #setPanType(PanType)
	 */
	public double getPan(int instanceID)
	{
		if (!cursors[instanceID].isActive)
		{
			throw new IllegalStateException(name + " instance: "
					+ instanceID + " is inactive");
		}
		
		return cursors[instanceID].isPlaying ? 
				cursors[instanceID].pan : cursors[instanceID].newTargetPan; 
	};

	/**
	 * Sets the pan of the instance, where 100% left 
	 * corresponds to -1, 100% right corresponds to 1, and 
	 * center = 0. The pan setting can either be changed 
	 * while a cue is either playing or stopped. If the instance
	 * is playing, a smoothing algorithm used to prevent signal 
	 * discontinuities that result in audible clicks. If a second
	 * pan change arrives before the first change is completed, the 
	 * most recent pan change takes precedence.
	 * <p>
	 * Arguments are clamped to the range [-1, 1]. The calculations 
	 * used to apply the pan are determined by the {@code PanType}.
	 * 
	 * @param instanceID - an {@code int} used to identify the 
	 *                     {@code AudioCue} instance
	 * @param pan        - a {@code double} ranging from -1 to 1
	 * @throws IllegalStateException if instance is not active
	 * @see #setPan(int, double)
	 * @see AudioCueFunctions.PanType
	 * @see #setPanType(PanType)
	 */
	public void setPan(int instanceID, double pan)
	{
		if (!cursors[instanceID].isActive)
		{
			throw new IllegalStateException(name + " instance: "
					+ instanceID + " is inactive");
		}
		cursors[instanceID].newTargetPan =
				(float)Math.min(1, Math.max(-1, pan));
	};
	
	/**
	 * Returns a factor indicating the current rate of play of  
	 * the {@code AudioCue} instance relative to normal play.
	 * 
	 * @param instanceID - an {@code int} used to identify an 
	 * {@code AudioCue} instance
	 * @return a {@code float} factor indicating the speed at 
	 *         which the {@code AudioCue} instance is being played 
	 *         in the range [0.125, 8]
	 * @throws IllegalStateException if instance is not active
	 */
	public double getSpeed(int instanceID)
	{
		if (!cursors[instanceID].isActive)
		{
			throw new IllegalStateException(name + " instance: "
					+ instanceID + " is inactive");
		}

		return cursors[instanceID].isPlaying ? 
				cursors[instanceID].speed : cursors[instanceID].newTargetSpeed; 
	};

	/**
	 * Sets the play speed of the {@code AudioCue} instance. A 
	 * faster speed results in both higher-pitched frequency 
	 * content and a shorter duration. Play speeds can be 
	 * altered in while a cue is either playing or stopped. If
	 * the instance is playing, a smoothing algorithm used to 
	 * prevent signal discontinuities. If a second speed change
	 * arrives before the first change is completed, the most
	 * recent speed change takes precedence.
	 * <p>
	 * A speed of 1 will play the {@code AudioCue} instance at its 
	 * originally recorded speed. A value of 2 will double the 
	 * play speed, and a value of 0.5 will halve the play speed.
	 * Arguments are clamped to values ranging from 8 times slower
	 * to 8 times faster than unity, a range of [0.125, 8].
	 * 
	 * @param instanceID - an {@code int} used to identify an 
	 *                     {@code AudioCue} instance
	 * @param speed      -a {@code double} factor ranging from 
	 *                    0.125 to 8
	 * @throws IllegalStateException if instance is not active
	 */
	public void setSpeed(int instanceID, double speed)
	{
		if (!cursors[instanceID].isActive)
		{
			throw new IllegalStateException(name + " instance: "
					+ instanceID + " is inactive");
		}

		cursors[instanceID].newTargetSpeed = 
				Math.min(8, Math.max(0.125, speed));
	};

	/**
	 * Sets the number of times the media will restart
	 * from the beginning, after completing, or specifies 
	 * infinite looping via the value -1. Note: an instance
	 * set to loop 2 times will play back a total of 3 times.
	 * 
	 * @param instanceID  - an {@code int} used to identify an 
	 * 						{@code AudioCue} instance
	 * @param loops       - an {@code int} that specifies the 
	 *                      number of times an instance will
	 * 						return to the beginning and play
	 *                      the instance anew
	 * @throws IllegalStateException if instance is not active
	 */
	public void setLooping(int instanceID, int loops)
	{
		if (!cursors[instanceID].isActive)
		{
			throw new IllegalStateException(name + " instance: "
					+ instanceID + " is inactive");
		}
		
		cursors[instanceID].loop = loops;
	};
	
	/**
	 * Sets an internal flag which determines what happens when the
	 * designated instance finishes playing. If {@code true} the 
	 * instance will be added to the pool of available instances and 
	 * will not accept updates. If {@code false} then the instance 
	 * will remain available for updates.
	 * <p> 
	 * By default, an instance that is obtained and started via the 
	 * {@code play} method automatically recycles, and an instance 
	 * obtained via {@code getInstance} does not. In both cases the 
	 * behavior can be changed by setting this flag.
	 * 
	 * @param instanceID      - an {@code int} used to identify an 
	 *                          {@code AudioCue} instance
	 * @param recycleWhenDone - a {@code boolean} that designates
	 *                          whether to recycle the instance or 
	 *                          not when the instance plays through 
	 *                          to completion
	 * @throws IllegalStateException if the instance is not active
	 */
	public void setRecycleWhenDone(int instanceID, boolean recycleWhenDone)
	{
		if (!cursors[instanceID].isActive)
		{
			throw new IllegalStateException(name + " instance: "
					+ instanceID + " is inactive");
		}
		
		cursors[instanceID].recycleWhenDone = recycleWhenDone;
	}	
	
	/**
	 * Returns {@code true} if the designated instance is active, 
	 * {@code false} if not. An active instance is one which is 
	 * not in the pool of available instances, but is open to 
	 * receiving commands. It may or may not be playing at any given 
	 * moment.
	 * 
	 * @param instanceID - an {@code int} used to identify an 
	 *                     {@code AudioCue} instance
	 * @return {@code true} if the instance is active, {@code false}
	 *                      if not
	 * @see #getIsPlaying(int)
	 */
	public boolean getIsActive(int instanceID)
	{
		return cursors[instanceID].isActive;
	}
	
	/**
	 * Returns {@code true} if instance is playing, {@code false}
	 * if not
	 * 
	 * @param instanceID - an {@code int} used to identify an 
	 *                     {@code AudioCue} instance
	 * @return {@code true} if instance is playing, {@code false}
	 *         if not
	 */
	public boolean getIsPlaying(int instanceID)
	{
		return cursors[instanceID].isPlaying;
	}
	
	
	/*
	 * A private, data-only class that is created and maintained
	 * internally for managing a single instance of an AudioCue. 
	 * The immutable 'id' variable set during instantiation, with
	 * the value corresponding to the position of the AudioCueCursor
	 * instance in the AudioCue.cursors array.
	 * 
	 * An instance is either active (isActive == true) in which case
	 * it can be updated, or inactive, in which case it is in a pool
	 * of <em>available</em> instances. An active instance can either
	 * be playing (isPlaying == true) or stopped (isPlaying == false). 
	 * 
	 * The 'recycleWhenDone' boolean is used to determine whether 
	 * the instance is returned to the pool of available instances 
	 * when a play completes, or if it is allowed to remain active 
	 * and open to further commands. 
	 * 
	 * The target variables are used by operations that spread out
	 * changes over a preset number of steps (see VOLUME_STEPS,
	 * SPEED_STEPS, PAN_STEPS) to prevent discontinuities that 
	 * could otherwise cause audible clicks.
	 */
	private class AudioCueCursor
	{
		volatile boolean isPlaying;
		volatile boolean isActive;
		final int id;
		
		double cursor;
		double speed;
		float volume;
		float pan;
		int loop;
		boolean recycleWhenDone;

		double newTargetSpeed;
		double targetSpeed;
		double targetSpeedIncr;
		int targetSpeedSteps;
		
		float newTargetVolume;
		float targetVolume;
		float targetVolumeIncr;
		int targetVolumeSteps;
		
		float newTargetPan;
		float targetPan;
		float targetPanIncr;
		int targetPanSteps;
		
		AudioCueCursor(int instanceId)
		{
			this.id = instanceId;			
		}
		
		/*
		 * Used to clear settings from previous plays
		 * and put in default settings.
		 */
		private void resetInstance() {
			isActive = false;
			isPlaying = false;
			cursor = 0;
			
			volume = 0;
			newTargetVolume = 0;
			targetVolume = 0;
			targetVolumeSteps = 0;
			
			pan = 0;
			newTargetPan = 0;
			targetPan = 0;
			targetPanSteps = 0;
			
			speed = 1;
			newTargetSpeed = 1;
			targetSpeed = 1;
			targetSpeedSteps = 0;
			
			loop = 0;
			recycleWhenDone = false;
		}
		
		private void instantaneousUpdate() {
			if (!isActive) {
				throw new IllegalStateException(name + " instance: "
						+ id + " is inactive");
			}
			if (isPlaying) {
				throw new IllegalStateException(name + " instance: "
						+ id + " is playing");
			}
			
			// OK to execute instantaneous changes
			volume = newTargetVolume;
			targetVolume = newTargetVolume;
			targetVolumeSteps = 0;
			
			pan = newTargetPan;
			targetPan = newTargetPan;
			targetPanSteps = 0;
			
			speed = newTargetSpeed;
			targetSpeed = newTargetSpeed;
			targetSpeedSteps = 0;
		}
	}

	/*
	 * "Opening" line sets the SourceDataLine waiting for data.
	 * "Run" will start loop that will either send out silence 
	 * (zero-filled arrays) or sound data.
	 */
	private class AudioCuePlayer implements Runnable
	{
		private SourceDataLine sdl;
		private final int sdlBufferSize;
		private byte[] audioBytes;
//		private boolean playerRunning;
		
		public void stopRunning() {
			playerRunning = false;
		}
		
		AudioCuePlayer(Mixer mixer, int bufferFrames) throws 
			LineUnavailableException
		{
			// twice the frames length, because stereo
			// NOTE: there is also a default instantiation 
			// in the AudioCue constructor (to help with testing)
			readBuffer = new float[bufferFrames * 2];
			// SourceDataLine must be 4 * number of frames, to 
			// account for 16-bit encoding and stereo.
			sdlBufferSize = bufferFrames * 4;
			audioBytes = new byte[sdlBufferSize];
					
			sdl = AudioCueFunctions.getSourceDataLine(mixer, info);
			sdl.open(audioFormat, sdlBufferSize);
			sdl.start();
		}
		
		// Audio Thread Code
		public void run()
		{
			while(playerRunning)
			{
				readBuffer = fillBuffer(readBuffer);
				audioBytes = AudioCueFunctions.fromPcmToAudioBytes(audioBytes, readBuffer);
				sdl.write(audioBytes, 0, sdlBufferSize);
			}
			sdl.drain();
			sdl.close();
			sdl = null;
		}
	}

	/*
	 * AudioThread code, executing within the while loop of the run() method.
	 */
	private float[] fillBuffer(float[] readBuffer)
	{
		// Start with 0-filled buffer, send out silence
		// if nothing playing.
		int bufferLength = readBuffer.length;
		for (int i = 0; i < bufferLength; i++) 
		{
			readBuffer[i] = 0;
		}
		
		for (int ci = 0; ci < polyphony; ci++)
		{
			if (cursors[ci].isPlaying) 
			{
				AudioCueCursor acc = cursors[ci];
				/*
				 * Usually, these won't change, so initialize 
				 * and store value and only recalculate upon change.
				 */
				float panFactorL = panL.apply(acc.pan);
				float panFactorR = panR.apply(acc.pan);
				float volFactor = vol.apply(acc.volume);
				
				for (int i = 0; i < bufferLength; i += 2)
				{
					// has volume setting changed? if so recalc
					if (acc.newTargetVolume != acc.targetVolume) {
						acc.targetVolume = acc.newTargetVolume;
						acc.targetVolumeIncr = 
								(acc.targetVolume - acc.volume)	/ VOLUME_STEPS;
						acc.targetVolumeSteps = VOLUME_STEPS;
					}
					// adjust volume if needed
					if (acc.targetVolumeSteps-- > 0)
					{
						acc.volume += acc.targetVolumeIncr;
						if (acc.targetVolumeSteps == 0) {
							acc.volume = acc.targetVolume;
						}
						volFactor = vol.apply(acc.volume);
					}
					
					// has pan setting changed? if so, recalc
					if (acc.newTargetPan != acc.targetPan) {
						acc.targetPan = acc.newTargetPan;
						acc.targetPanIncr = (acc.targetPan - acc.pan) / PAN_STEPS;
						acc.targetPanSteps = PAN_STEPS;
					}
					// adjust pan if needed
					if (acc.targetPanSteps-- > 0)
					{
						if (acc.targetPanSteps != 0) {
							acc.pan += acc.targetPanIncr;
						} else { 
							acc.pan = acc.targetPan;
						}
						panFactorL = panL.apply(acc.pan);
						panFactorR = panR.apply(acc.pan);
					}
					
					// get audioVals, with LERP for fractional cursor position
					float[] audioVals = new float[2];
					if (acc.cursor == (int)acc.cursor) {
						audioVals[0] = cue[(int)acc.cursor * 2];
						audioVals[1] = cue[((int)acc.cursor * 2) + 1];
					} else {
						audioVals = readFractionalFrame(audioVals, acc.cursor);
					}
					
					readBuffer[i] += (audioVals[0] 
							* volFactor * panFactorL);
					readBuffer[i + 1] += (audioVals[1] 
							* volFactor * panFactorR);
					
					// SET UP FOR NEXT ITERATION
					// has speed setting changed? if so, recalc
					if (acc.newTargetSpeed != acc.targetSpeed) {
						acc.targetSpeed = acc.newTargetSpeed;
						acc.targetSpeedIncr = 
								(acc.targetSpeed - acc.speed) / SPEED_STEPS;
						acc.targetSpeedSteps = SPEED_STEPS;
					}
					// adjust speed if needed
					if (acc.targetSpeedSteps-- > 0)
					{
						acc.speed += acc.targetSpeedIncr;
					}
	
					// set NEXT read position
					acc.cursor += acc.speed;
					
					// test for "eof" and "looping"
					if (acc.cursor > (cueFrameLength - 1))
					{
						// keep looping indefinitely
						if (acc.loop == -1)
						{
							acc.cursor = 0;
							broadcastLoopEvent(acc);
						}
						// loop specific number of times
						else if (acc.loop > 0)
						{
							acc.loop--;
							acc.cursor = 0;
							broadcastLoopEvent(acc);
						}
						else // no more loops to do
						{
							acc.isPlaying = false;
							broadcastStopEvent(acc);
							if (acc.recycleWhenDone)
							{
								acc.resetInstance();
								availables.offerFirst(acc);
								broadcastReleaseEvent(acc);
							}
							// cursor is at end of cue before
							// readBuffer filled, no need to
							// process further (default 0's)
							break;
						}
					}
				}
			}
		}
		return readBuffer;
	}
	
	/*
	 *  Audio thread code, returns a single stereo PCM pair using a
	 *  LERP (linear interpolation) function. The difference between 
	 *  `idx` (floating point value) and `intIndex` determines the 
	 *  weighting amount for the LERP algorithm. As the PCM array of
	 *  audio data is stereo, we use `stereoIndex` (twice the amount
	 *  of `intIndex`) to locate the audio values to be weighted. 
	 */
	private float[] readFractionalFrame(float[] audioVals, double idx)
	{
		final int intIndex = (int) idx;
		final int stereoIndex = intIndex * 2;
		
		audioVals[0] = (float)(cue[stereoIndex + 2] * (idx - intIndex) 
				+ cue[stereoIndex] * ((intIndex + 1) - idx));
		
		audioVals[1] = (float)(cue[stereoIndex + 3] * (idx - intIndex) 
				+ cue[stereoIndex + 1] * ((intIndex + 1) - idx));

		return audioVals;
	}
	
	@Override  // AudioMixerTrack interface
	public boolean isTrackRunning() 
	{
		return trackRunning;
	}
	
	@Override  // AudioMixerTrack interface
	public void setTrackRunning(boolean trackRunning) 
	{
		this.trackRunning = trackRunning;
	}
	
	@Override  // AudioMixerTrack interface
	public float[] readTrack()  
	{
		return fillBuffer(readBuffer);
	}
	
	
	// Following are methods that broadcast events to registered listeners.
	
	private void broadcastOpenEvent(int threadPriority, 
			int bufferSize,	String name)
	{
		for (AudioCueListener acl : listeners)
		{
			acl.audioCueOpened(System.currentTimeMillis(), 
					threadPriority,	bufferSize, this);
		}
	}
	
	private void broadcastCloseEvent(String name)
	{
		for (AudioCueListener acl : listeners)
		{
			acl.audioCueClosed(System.currentTimeMillis(), this);
		}
	}
	
	
	private void broadcastCreateInstanceEvent(AudioCueCursor acc)
	{
		for (AudioCueListener acl:listeners)
		{
			acl.instanceEventOccurred(
					new AudioCueInstanceEvent(
							Type.OBTAIN_INSTANCE,
							this, acc.id, 0
					));
		}
	}
	
	private void broadcastReleaseEvent(AudioCueCursor acc)
	{
		for (AudioCueListener acl:listeners)
		{
			acl.instanceEventOccurred(
					new AudioCueInstanceEvent(
							Type.RELEASE_INSTANCE,
							this, acc.id, acc.cursor
					));
		}
	}

	private void broadcastStartEvent(AudioCueCursor acc)
	{
		for (AudioCueListener acl:listeners)
		{
			acl.instanceEventOccurred(
					new AudioCueInstanceEvent(
							Type.START_INSTANCE,
							this, acc.id, acc.cursor
					));
		}
	}
	
	private void broadcastLoopEvent(AudioCueCursor acc)
	{
		for (AudioCueListener acl:listeners)
		{
			acl.instanceEventOccurred(
					new AudioCueInstanceEvent(
							Type.LOOP, this, acc.id, 0
					));
		}
	}
	
	private void broadcastStopEvent(AudioCueCursor acc)
	{
		for (AudioCueListener acl:listeners)
		{
			acl.instanceEventOccurred(
					new AudioCueInstanceEvent(
							Type.STOP_INSTANCE,
							this, acc.id, acc.cursor
					));
		}
	}
}
