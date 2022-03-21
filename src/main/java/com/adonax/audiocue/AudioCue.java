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

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Function;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line.Info;

import com.adonax.audiocue.AudioCueInstanceEvent.Type;

import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * The {@code AudioCue} represents a segment of audio media 
 * that is loaded into memory prior to playback and which 
 * offers minimal latency, concurrent playback of instances,
 * and real time controls for volume, panning and playback 
 * speed of each instance. The {@code AudioCue} combines 
 * and builds upon many features of the 
 * {@code javax.sound.sampled.Clip} and 
 * {@code javafx.scene.media.AudioClip}. Data is loaded 
 * either from a "CD Quality" wav file (44100 fps, 16-bit, 
 * little-endian) or a float array that conforms to this 
 * format, and stored in an internal float array as 
 * normalized values with the range [-1.0, 1.0].
 * <p>  
 * Unlike a {@code Clip} or {@code AudioClip} the {@code 
 * play} method returns an {@code int} <em>hook</em> to
 * the playable instance. You can ignore the hook and 
 * use the {@code play} method in a fire-and-forget 
 * manner, in which case the instance hook is automatically 
 * returned to the pool of available instances when play 
 * completes. Alternatively, you can use the hook as a 
 * parameter to specify an instance to {@code start} or 
 * {@code stop} (in effect, <em>pause</em>)
 * or to position the "play head" to any sample frame,
 * or to set other properties of the instance such as the 
 * number of repetitions. The hook can also be used to 
 * designate the target instance for setting or modulating
 * the volume, pan, or play speed while it is playing. This 
 * provides a reliable alternative to the use of a
 * {@code javax.sound.sampled.Control} for smooth 
 * volume fade-ins and fade-outs, as well as a basis for 
 * pitch-based effects such as Doppler shifting.
 * <p>
 * Internally, a {@code javax.sound.sampled.SourceDataLine} 
 * is used for output. When you {@code open} the {@code AudioCue},
 * the line is configured with a buffer size of 1024
 * frames (4192 bytes) and a thread priority of {@code 
 * HIGHEST}. Alternative values can be specified as parameters
 * to the {@code open} method. 
 * 
 * @author Philip Freihofner
 * @version AudioCue 1.1
 * @see http://adonax.com/AudioCue
 */
public class AudioCue implements AudioMixerTrack
{
	public static final AudioFormat audioFormat = 
			new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 
					44100, 16, 2, 4, 44100, false);
	public static final Info info =	
			new DataLine.Info(SourceDataLine.class, audioFormat);
	
	private final int VOLUME_STEPS = 1024;
	private final int SPEED_STEPS = 1024 * 4;
	private final int PAN_STEPS = 1024;
	private final int DEFAULT_BUFFER_FRAMES = 1024 ;
	
	private final LinkedBlockingDeque<AudioCueCursor> availables;
	private final float[] cue;
	private final int cueFrameLength;
	private final AudioCueCursor[] cursors;
	private final int polyphony;
	
	private volatile boolean playerRunning;
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
	 * @param name a {@code String} to associate with this
	 * {@code AudioCue}
	 */
	public void setName(String name) {this.name = name;}
	
	// only stored if AudioMixer is opened:
	private AudioMixer audioMixer; 
	
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
	 * @param listener a class implementing the 
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
	 * @param listener a class implementing the 
	 * {@code AudioCueListener} interface
	 */
	public void removeAudioCueListener(AudioCueListener listener)
	{
		listeners.remove(listener);
	}
	
	/**
	 * The {@code enum Type} is a repository of functions 
	 * used to perform volume-based panning for stereo media. 
	 * Each function takes a pan setting as an input, ranging 
	 * from -1 (100% left) to 1 (100% right) with 0 being the 
	 * center pan setting. 
	 * <p>
	 * In the future, if or when mono media is implemented, a 
	 * delay-based panning function option will be added.
	 */
	public static enum PanType 
	{
		/**
		 * Represents a panning function that uses linear 
		 * gradients that taper from the center to the edges
		 * on the weak side, and the combined volume is 
		 * stronger at the center than at the edges. 
		 * For the pan values -1 to 0, the 
		 * left channel factor is kept at full volume ( = 1) 
		 * and the right channel factor is tapered via a 
		 * linear function from 0 to 1. 
		 * For pan values from 0 to 1, the left channel factor 
		 * is tapered via a linear function from 0 to 1 and the 
		 * right channel is kept at full volume ( = 1).
		 */
		CENTER_LINEAR(  
				x -> Math.max(0, Math.min(1, 1 - x)),
				x -> Math.max(0, Math.min(1, 1 + x))
				), 
		/**
		 * Represents a panning function that uses linear 
		 * gradients that taper from edge to edge, and the 
		 * combined volume is stronger at the edges than 
		 * at the center. For pan values -1 to 1 the left 
		 * channel factor is tapered with a linear function 
		 * from 1 to 0, and the right channel factor is 
		 * tapered via a linear function from 0 to 1. 
		 */	
		FULL_LINEAR(
				x -> (1 + x) / 2,
				x -> 1 - ((1 + x) / 2)
				),
		/**
		 * Represents a panning function that uses
		 * circle-shaped gradients that taper from edge to
		 * edge, and the combined volume is close to uniform 
		 * for all pan settings. For the pan values -1 to 1, 
		 * the left channel factor is tapered via a {@code cos}
		 * function with values ranging from 1 to 0, and the 
		 * right channel factor is tapered via a {@code sin} 
		 * function with values ranging from 0 to 1.
		 */
		CIRCULAR(
				x -> (float)(Math.cos(Math.PI * (1 + x) / 4)),
				x -> (float)(Math.sin(Math.PI * (1 + x) / 4))
				);
	
		private final Function<Float, Float> left;
		private final Function<Float, Float> right;
	
		PanType(Function<Float, Float> left, 
				Function<Float, Float> right)
		{
			this.left = left;
			this.right = right;
		}
	}
		
	private Function<Float, Float> panL;
	private Function<Float, Float> panR;
	
	/**
	 * Assigns the type of panning to be used.
	 * 
	 * @param panType a member of the {@code enum 
	 * AudioCue.PanType}
	 * @see PanType
	 */
	public void setPanType(PanType panType)
	{
		panL = panType.left;
		panR = panType.right;
	}
			
	/**
	 * Creates and returns a new AudioCue. This method 
	 * allows the direct insertion of a {@code float} 
	 * array as an argument, where the data is presumed 
	 * to conform to the "CD Quality" format: 44100 frames
	 * per second, 16-bit encoding, stereo, little-endian.
	 * The maximum number of concurrent playing instances
	 * is given as the {@code polyphony} argument. 
	 * The {@code polyphony} value can not be changed. 
	 * A large value may require additional buffering and 
	 * result in noticeable lag in order to prevent 
	 * drop outs.
	 * 
	 * @param cue a {@code float} array of audio data
	 * in "CD Quality" format, scaled to the range 
	 * [-1, 1]
	 * @param name a {@code String} to be associated
	 * with the {@code AudioCue}
	 * @param polyphony an {@code int} specifying 
	 * the maximum number of concurrent instances
	 * @return AudioCue
	 */
	public static AudioCue makeStereoCue(float[] cue, 
			String name, int polyphony)
	{
		return new AudioCue(cue, name, polyphony);
	}
	
	/**
	 * Creates and returns a new AudioCue. A {@code URL} 
	 * for a WAV file to be loaded is provided. At this
	 * point, only one format, known as "CD Quality", is
	 * supported: 44100 frames per second, 16-bit encoding, 
	 * stereo, little-endian. The maximum number of 
	 * concurrent playing instances is given as
	 * the {@code polyphony} argument. The {@code polyphony}
	 * value can not be changed. A large value may require
	 * additional buffering and result in noticeable lag
	 * in order to prevent drop outs.
	 * <p>
	 * The file name provided by the URL is automatically
	 * used as the name for the {@code AudioCue}, but can 
	 * be changed via the method {@code setName}.
	 * 
	 * @param url a {@code URL} for the source file 
	 * @param polyphony an {@code int} specifying 
	 * the maximum number of concurrent instances
	 * @return AudioCue
	 * @throws UnsupportedAudioFileException if the media
	 * is not a WAV file of "CD Quality"
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
	
	/**
	 * Private constructor, used internally.
	 * 
	 * @param cue a {@code float} array of audio data
	 * in "CD Quality" format, scaled to the range [-1..1]
	 * @param name a {@code String} to be associated
	 * with the {@code AudioCue}
	 * @param polyphony an {@code int} specifying 
	 * the maximum number of concurrent instances
	 */
	private AudioCue(float[] cue, String name, int polyphony)
	{
		this.cue = cue;
		this.cueFrameLength = cue.length / 2;
		this.polyphony = polyphony;
		this.name = name;
		
		availables = 
				new LinkedBlockingDeque<AudioCueCursor>(polyphony);
		cursors = new AudioCueCursor[polyphony];
		
		for (int i = 0; i < polyphony; i++)
		{	
			cursors[i] = new AudioCueCursor(i);
			cursors[i].resetInstance();
			availables.add(cursors[i]);
		}
		
		// default pan calculation function
		setPanType(PanType.CENTER_LINEAR);
		
		listeners = new CopyOnWriteArrayList<AudioCueListener>();
	}
	
	// Currently assumes stereo format ("CD Quality") 
	private static float[] loadURL(URL url) throws 
		UnsupportedAudioFileException, IOException
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
		
		for (int i = 0; i < temp.length; i++)
		{
			temp[i] = temp[i] / 32767f;
		}
		
		return temp;
	}	
	
	/**
	 * Allocates resources for media play, using default
	 * {@code Mixer}, thread priority and buffer size values. The
	 * {@code AudioCueListener} will broadcast a notification
	 * using the method {@code audioCueOpened}. 
	 * 
	 * @throws IllegalStateException if the player is already
	 * open
	 * @throws LineUnavailableException if unable to obtain a
	 * {@code SourceDataLine} for the player
	 */
	public void open() throws IllegalStateException, LineUnavailableException
	{
		open(null, DEFAULT_BUFFER_FRAMES, Thread.MAX_PRIORITY);
	}
	
	/**
	 * Allocates resources for media play, using the 
	 * default {@code Mixer} and thread priority values, while 
	 * setting the internal a custom buffer size. The
	 * {@code AudioCueListener} will broadcast a notification
	 * using the method {@code audioCueOpened}. 
	 * 
	 * @param bufferFrames number of stereo frames
	 * @throws IllegalStateException if the player is already
	 * open
	 * @throws LineUnavailableException if unable to obtain a
	 * {@code SourceDataLine} for the player
	 */
	public void open(int bufferFrames) throws IllegalStateException, LineUnavailableException
	{
		open(null, bufferFrames, Thread.MAX_PRIORITY);
	}
	
	/**
	 * Allocates resources for media play, setting explicit values to
	 * over ride the defaults. The {@code AudioCueListener}  will 
	 * broadcast a notification using the method {@code audioCueOpened}. 
	 * 
	 * @param mixer a {@code javax.sound.sampled.Mixer}
	 * @param bufferFrames an {@code int} specifying the size of the 
	 * internal buffer
	 * @param threadPriority an {@code int} specifying the priority
	 * level of the thread, ranging [1, 10]
	 * @throws LineUnavailableException if unable to obtain a
	 * {@code SourceDataLine} for the player
	 * @throws IllegalStateException if the {@code AudioCue} is 
	 * already open
	 */
	public void open(Mixer mixer, int bufferFrames, int threadPriority) 
		throws LineUnavailableException, IllegalStateException
	{
		if (playerRunning) 
		{
			throw new IllegalStateException(
					"Already open.");
		}
		
		AudioCuePlayer player = new AudioCuePlayer(mixer, bufferFrames);
		Thread t = new Thread(player);

		t.setPriority(threadPriority);     
		playerRunning = true;
		t.start();
		
		broadcastOpenEvent(t.getPriority(), bufferFrames, name);
	}
	
	/**
	 * Assigns an {@code AudioMixer}, instead of an internal
	 * AudioCuePlay, for media playback. The buffer size
	 * and thread priority of the {@code AudioMixer} will be
	 * used for playback. The {@code AudioCueListener} will 
	 * broadcast a notification using the method 
	 * {@code audioCueOpened}. 
	 * 
	 * @param audioMixer
	 * @throws IllegalStateException
	 */
	public void open(AudioMixer audioMixer) throws IllegalStateException
	{
		if (playerRunning) 
		{
			throw new IllegalStateException(
					"Already open.");
		}
		playerRunning = true;
		this.audioMixer = audioMixer;
		
		// assigned size is frames * stereo
		readBuffer = new float[audioMixer.bufferSize * 2];
		
		audioMixer.addTrack(this);
		audioMixer.updateTracks();
		
		broadcastOpenEvent(audioMixer.threadPriority, 
				audioMixer.bufferSize, name);
	}
	
	/**
	 * Releases resources allocated for media play. The
	 * {@code AudioCueListener} will broadcast a notification
	 * using the method {@code audioCueClosed}. 
	 *  
	 * @throws IllegalStateException if player is already
	 * closed
	 */
	public void close() throws IllegalStateException
	{
		if (playerRunning == false)
		{
			throw new IllegalStateException(
				"Already closed.");
		}

		if (audioMixer != null)
		{
			audioMixer.removeTrack(this);
			audioMixer.updateTracks();
			audioMixer = null;
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
	 * Obtains an {@code int} hook from a pool of available 
	 * instances.
	 * The {@code AudioCueListener} method {@code obtainedInstance} 
	 * will be called. If no playable instances are available,
	 * the method returns -1. An instance obtained by this 
	 * method does <em>not</em> recycle back into the pool of
	 * available instances when it finishes playing. To put the 
	 * instance back in the pool of availables, the method
	 * {@code releaseInstance} must be called.
	 * 
	 * @return an {@code int} hook to the playing instance, or -1
	 * if no instances are available
	 */
	public int obtainInstance()
	{
		AudioCueCursor aci = availables.pollLast();
		
		if (aci == null) return -1;
		else 
		{
			aci.isActive = true;
			broadcastCreateInstanceEvent(aci);
			return aci.hook;
		}
	}
	
	/**
	 * Releases an {@code AudioCue} instance, making 
	 * it available for a new {@code play} method.
	 * The {@code AudioCueListener} method {@code releaseInstance} 
	 * will be called.
	 * 
	 * @param instanceHook {@code int} hook identifying the cue 
	 * instance to be released
	 */
	public void releaseInstance(int instanceHook)
	{
		cursors[instanceHook].resetInstance();
		availables.offerFirst(cursors[instanceHook]);
		broadcastReleaseEvent(cursors[instanceHook]);
	}
	
	/**
	 * Plays an available {@code AudioCue} instance 
	 * from the start of the audio data, at full volume, center
	 * pan, and at normal speed, or, returns -1 if no 
	 * {@code AudioCue} instance is available. If an 
	 * {@code AudioCue} instance is able to play, 
	 * the {@code AudioCueListener} methods 
	 * {@code createInstance} and {@code startInstance}
	 * will be called. When the instance finishes playing
	 * it will be automatically recycled into the pool of
	 * available instances.
	 * <p>
	 * For best response time (lowest latency), the {@code play}
	 * method should be called on an {@code AudioCue} that has 
	 * already been opened, as opening requires the creation
	 * of an audio thread and the acquisition of an output line. 
	 * 
	 * @return an {@code int} hook to the playing instance,
	 * or -1 if no instance is available
	 */
	public int play()
	{
		return play(1, 0, 1, 0);
	}	
	
	/**
	 * Plays an available {@code AudioCue} instance 
	 * from the start of the audio data at the given volume, at 
	 * center pan, and at normal speed, or, returns -1 if no 
	 * {@code AudioCue} instance is available.  If an 
	 * {@code AudioCue} instance is able to play, 
	 * the {@code AudioCueListener} methods 
	 * {@code createInstance} and {@code startInstance}
	 * will be called. When the instance finishes playing
	 * it will be automatically recycled into the pool of
	 * available instances.
	 * <p>
	 * For best response time (lowest latency), the {@code play}
	 * method should be called on an {@code AudioCue} that has 
	 * already been opened, as opening requires the creation
	 * of an audio thread and the acquisition of an output line. 
	 *  
	 * @param volume a {@code double} in the range [0, 1]
	 * @return an {@code int} hook to the playing instance,
	 * or -1 if no instance is available
	 */
	public int play(double volume)
	{
		return play(volume, 0, 1, 0);
	}	
	
	/**
	 * Plays an available {@code AudioCue} instance from 
	 * the start of the audio data, at center pan, and at the
	 * specified speed, or, returns -1 if no {@code AudioCue}
	 * instance is available. If an {@code AudioCue} instance 
	 * is able to play, the {@code AudioCueListener} 
	 * notifications {@code createInstance} and 
	 * {@code startInstance} will be sent to all registered
	 * listeners. When the instance finishes playing
	 * it will be automatically recycled into the pool of
	 * available instances.
	 * <p>
	 * For best response time (lowest latency), the {@code play}
	 * method should be called on an {@code AudioCue} that has 
	 * already been opened, as opening requires the creation
	 * of an audio thread and the acquisition of an output line. 
	 * 
	 * @param volume a {@code double} within the range [0, 1]
	 * @param pan a {@code double} within the range [-1, 1]
	 * @param speed a {@code double} that becomes the frame rate 
	 * @param loop an {@code int} that specifies a number of 
	 * additional plays (looping)
	 * @return an {@code int} hook to the playing instance, 
	 * or -1 if no instance is available
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
	 * Plays the specified {@code AudioCue} instance from its current 
	 * position within the sound cue, using current volume, pan,
	 * and speed settings. The {@code AudioCueListener} method 
	 * {@code startInstance} will be called.
	 * 
	 * @param instanceHook an {@code int} used to identify an 
	 * {@code AudioCue} instance
	 * @throws IllegalStateException if instance is not active
	 * or if instance is playing
	 */
	public void start(int instanceHook) throws IllegalStateException
	{
		if (!cursors[instanceHook].isActive || 
			cursors[instanceHook].isPlaying)
		{
			throw new IllegalStateException("Illegal state, "
					+ name + ", instance:" + instanceHook);
		}
		
		cursors[instanceHook].isPlaying = true;
		broadcastStartEvent(cursors[instanceHook]);
	};
	
	/**
	 * Sends message to indicate that the playing of the cue
	 * associated with the hook should be paused. The 
	 * {@code AudioCueListener} method {@code stopInstance} 
	 * will be called. Sending this message to an already
	 * stopped instance does nothing. Once an instance is 
	 * stopped, it will be left in an active state until
	 * it is explicitly released.
	 * 
	 * @param instanceHook an {@code int} used to identify an 
	 * {@code AudioCue} instance
	 * @throws IllegalStateException if instance is not active
	 */
	public void stop(int instanceHook) throws IllegalStateException
	{
		if (!cursors[instanceHook].isActive)
		{
			throw new IllegalStateException("Illegal state, "
					+ name + ", instance:" + instanceHook);
		}
		
		cursors[instanceHook].isPlaying = false;
		broadcastStopEvent(cursors[instanceHook]);
		cursors[instanceHook].recycleWhenDone = false;
	};
		
	/**
	 * Sets the play position ("play head") to a 
	 * specified sample frame. The frame count is zero-based.
	 * The new play position can include a fractional sample 
	 * amount. The new sample frame position will be clamped to
	 * a value that lies within the {@code AudioCue}. When 
	 * the instance is restarted, it will commence from this 
	 * sample frame.
	 * 
	 * @param instanceHook an {@code int} used to identify an 
	 * {@code AudioCue} instance
	 * @param frame the sample frame number from which play will 
	 * commence when the next {@code start} method is executed
	 * @throws IllegalStateException if instance is not active
	 * or if instance is playing
	 */
	public void setFramePosition(int instanceHook, double frame)
		throws IllegalStateException
	{
		if (!cursors[instanceHook].isActive || 
				cursors[instanceHook].isPlaying)
		{
			throw new IllegalStateException("Illegal state, "
					+ name + ", instance:" + instanceHook);
		}
		
		cursors[instanceHook].idx = Math.max(0, Math.min(
				getFrameLength() - 1, (float)frame));
	};
	
	/**
	 * Returns the current sample frame number. The frame count
	 * is zero-based. The position may lie in between two frames.
	 * 
	 * @param instanceHook an {@code int} used to identify an 
	 * {@code AudioCue} instance
	 * @return a {@code double} corresponding to the current 
	 * sample frame position
	 * @throws IllegalStateException if instance is not active
	 */
	public double getFramePosition(int instanceHook)
	{
		if (!cursors[instanceHook].isActive)
		{
			throw new IllegalStateException(name + " instance: "
					+ instanceHook + " is inactive");
		}
		
		return cursors[instanceHook].idx;
	}
	
	/**
	 * Repositions the play position ("play head") of the
	 * given {@code AudioCue} instance to the sample frame that 
	 * corresponds to the specified elapsed time in milliseconds. 
	 * The new play position can include a fractional sample 
	 * amount. The new sample frame position will be clamped to
	 * a value that lies within the {@code AudioCue}. When the 
	 * instance is restarted, it will commence from this 
	 * sample frame.
	 * 
	 * @param instanceHook an {@code int} used to identify an 
	 * {@code AudioCue} instance
	 * @param milliseconds an {@code int} in milliseconds that
	 * corresponds to the desired starting point for the 
	 * {@code AudioCue} instance
	 * @throws IllegalStateException if instance is not active
	 * or if instance is playing
	 */
	public void setMillisecondPosition(int instanceHook, 
			int milliseconds)
	{
		if (!cursors[instanceHook].isActive || 
				cursors[instanceHook].isPlaying)
		{
			throw new IllegalStateException("Illegal state, "
					+ name + ", instance:" + instanceHook);
		}

		float samples = (audioFormat.getFrameRate() * milliseconds) 
				/ 1000f;
		cursors[instanceHook].idx = 
				Math.max(0,	Math.min(cueFrameLength - 1, samples));
	};
	
	/**
	 * Repositions the play position ("play head") of the
	 * given {@code AudioCue} instance to the sample frame that 
	 * corresponds to the specified elapsed fractional 
	 * part the total audio cue length. The new play position can
	 * include a fractional sample amount. Arguments are clamped 
	 * to the range [0..1]. When restarted, the audio cue will 
	 * commence from the new sample frame position.
	 * 
	 * @param instanceHook an {@code int} used to identify the 
	 * {@code AudioCue} instance
	 * @param normal a {@code double} in the range [0..1] that 
	 * corresponds to the desired starting point for the 
	 * {@code AudioCue} instance
	 * @throws IllegalStateException if instance is not active
	 * or if instance is playing
	 */
	public void setFractionalPosition(int instanceHook, double normal)
	{
		if (!cursors[instanceHook].isActive || 
				cursors[instanceHook].isPlaying)
		{
			throw new IllegalStateException("Illegal state, "
					+ name + ", instance:" + instanceHook);
		}
		
		cursors[instanceHook].idx = (float)((cueFrameLength - 1) * 
				Math.max(0, Math.min(1, normal)));
	};

	/**
	 * Sets the volume of the instance. Volumes can be altered 
	 * while a cue is playing with a latency largely determined
	 * by the buffer setting specified in the {@code open} method
	 * in combination with a smoothing algorithm used to prevent
	 * signal discontinuities that result in audible clicks or 
	 * other forms of distortion. Volumes can be altered 
	 * concurrently, with the most recent call 
	 * taking precedence over and interrupting previous calls.
	 * Arguments are clamped to the range [0..1] and 
	 * thus can only diminish, not amplify, the volume.
	 * <p> 
	 * Internally, the volume argument is used as a factor that 
	 * is directly multiplied against the media's sample values, 
	 * and it should be noted that this linear scaling of 
	 * sample values is not proportional with the human sense of 
	 * loudness. 
	 * 
	 * @param  instanceHook  an {@code int} used to identify the 
	 * {@code AudioCue} instance
	 * @param  volume a {@code float} in the range [0, 1] to be 
	 * multiplied against the audio sample values
	 * @throws IllegalStateException if instance is not active
	 */
	public void setVolume(int instanceHook, double volume)
		throws IllegalStateException
	{	
		if (!cursors[instanceHook].isActive)
		{
			throw new IllegalStateException(name + " instance: "
					+ instanceHook + " is inactive");
		}

		cursors[instanceHook].targetVolume = 
				(float)Math.min(1, Math.max(0, volume));
		if (cursors[instanceHook].isPlaying)
		{
			cursors[instanceHook].targetVolumeIncr = 
					(cursors[instanceHook].targetVolume 
						- cursors[instanceHook].volume) 
							/ VOLUME_STEPS;
			cursors[instanceHook].targetVolumeSteps = VOLUME_STEPS;
		}
		else
		{
			cursors[instanceHook].volume = 
					cursors[instanceHook].targetVolume;
		}
	};

	/**
	 * Returns a value indicating the current volume setting
	 * of an {@code AudioCue} instance, ranging [0..1].
	 * 
	 * @param instanceHook an {@code int} used to identify the 
	 * {@code AudioCue} instance
	 * @return volume factor as a {@code double}
	 * @throws IllegalStateException if instance is not active
	 */
	public double getVolume(int instanceHook)
		throws IllegalStateException
	{
		if (!cursors[instanceHook].isActive)
		{
			throw new IllegalStateException(name + " instance: "
					+ instanceHook + " is inactive");
		}

		return cursors[instanceHook].volume;
	};

	/**
	 * Sets the pan of the instance, where 100% left corresponds 
	 * to -1, 100% right corresponds to 1, and center = 0.  
	 * The pan setting can be changed while a cue is playing 
	 * with a latency largely determined by the buffer setting 
	 * specified in the {@code open} method in combination with 
	 * a smoothing algorithm used to prevent signal 
	 * discontinuities that result in audible clicks or 
	 * other forms of distortion. Pans can be altered 
	 * concurrently, with the most recent call 
	 * taking precedence over and interrupting previous calls.
	 * Arguments are clamped to the range [-1, 1].
	 * <p>
	 * For stereo {@code AudioCue}s, a volume-based pan is 
	 * performed. The calculation is performed by method 
	 * specified by {@code AudioCue.Pan}.
	 * 
	 * @param instanceHook an {@code int} used to identify the 
	 * {@code AudioCue} instance
	 * @param pan a {@code double} ranging from -1 to 1
	 * @throws IllegalStateException if instance is not active
	 * @see AudioCue.PanType
	 */
	public void setPan(int instanceHook, double pan)
		throws IllegalStateException
	{
		if (!cursors[instanceHook].isActive)
		{
			throw new IllegalStateException(name + " instance: "
					+ instanceHook + " is inactive");
		}
		cursors[instanceHook].targetPan =
				(float)Math.min(1, Math.max(-1, pan));
		if (cursors[instanceHook].isPlaying)
		{
			cursors[instanceHook].targetPanIncr = 
					(cursors[instanceHook].targetPan 
						- cursors[instanceHook].pan) 
							/ PAN_STEPS;
			cursors[instanceHook].targetPanSteps = PAN_STEPS;
		}
		else
		{
			cursors[instanceHook].pan = 
					cursors[instanceHook].targetPan;
		}
	};

	/**
	 * Returns a double in the range [-1, 1] where -1 
	 * indicates 100% left and 1 indicates 100% right.
	 * 
	 * @param instanceHook an {@code int} used to identify the 
	 * {@code AudioCue} instance
	 * @return the current pan value, ranging [-1, 1]
	 * @throws IllegalStateException if instance is not active
	 */
	public double getPan(int instanceHook) throws 
		IllegalStateException
	{
		if (!cursors[instanceHook].isActive)
		{
			throw new IllegalStateException(name + " instance: "
					+ instanceHook + " is inactive");
		}
		
		return cursors[instanceHook].pan;
	};

	/**
	 * Sets the play speed of the {@code AudioCue} instance. A 
	 * faster speed results in both higher-pitched frequency 
	 * content and a shorter duration. Play speeds can be 
	 * altered in real time while a cue is playing with a latency
	 * largely determined by the buffer setting specified in the 
	 * {@code open} method in combination with a smoothing 
	 * algorithm used to prevent signal discontinuities.
	 * Speeds can be altered concurrently, with the most recent 
	 * call taking precedence over and interrupting previous calls.
	 * A speed of 1 will play the {@code AudioCue} instance at its 
	 * originally recorded speed. A value of 2 will double the 
	 * play speed, and a value of 0.5 will halve the play speed.
	 * Arguments are clamped to values ranging from 8 times slower
	 * to 8 times faster than unity, a range of [0.125, 8].
	 * <p>
	 * Note that the determination of what is an effective amount 
	 * of speed-up or slow-down depends upon the frequency content
	 * of the original sample, thus "safe bounds" for the speed
	 * argument are difficult to specify. Thus, it is possible 
	 * to enter values that result in signals that are either
	 * too low or too high in frequency to be audible.
	 * 
	 * @param instanceHook an {@code int} used to identify an 
	 * {@code AudioCue} instance
	 * @param speed a {@code double} factor ranging from 
	 * 0.125 to 8 (1/8th to 8 times the original speed)
	 * @throws IllegalStateException if instance is not active
	 */
	public void setSpeed(int instanceHook, double speed)
			throws IllegalStateException
	{
		if (!cursors[instanceHook].isActive)
		{
			throw new IllegalStateException(name + " instance: "
					+ instanceHook + " is inactive");
		}

		cursors[instanceHook].targetSpeed = 
				(float)Math.min(8, Math.max(0.125, speed));
		if (cursors[instanceHook].isPlaying)
		{
			cursors[instanceHook].targetSpeedIncr = 
				(cursors[instanceHook].targetSpeed 
						- cursors[instanceHook].speed) / SPEED_STEPS;
			cursors[instanceHook].targetSpeedSteps = SPEED_STEPS;
		}
		else
		{
			cursors[instanceHook].speed = 
					cursors[instanceHook].targetSpeed;
		}
	};
	
	/**
	 * Returns a factor indicating the current rate of play of  
	 * the {@code AudioCue} instance relative to normal play.
	 * 
	 * @param instanceHook an {@code int} used to identify an 
	 * {@code AudioCue} instance
	 * @return a {@code float} factor indicating the speed at 
	 * which the {@code AudioCue} instance is being played 
	 * in the range [0.125, 8]
	 * @throws IllegalStateException if instance is not active
	 */
	public double getSpeed(int instanceHook) throws 
			IllegalStateException
	{
		if (!cursors[instanceHook].isActive)
		{
			throw new IllegalStateException(name + " instance: "
					+ instanceHook + " is inactive");
		}

		return cursors[instanceHook].speed;
	};

	/**
	 * Sets the number of times the media will restart
	 * from the beginning, after completing, or specifies 
	 * infinite looping via the value -1. Note: an instance
	 * set to loop 2 times will play back a total of 3 times.
	 * 
	 * @param instanceHook an {@code int} used to identify an 
	 * {@code AudioCue} instance
	 * @param loops an {@code int} that specifies the number
	 * of times an instance will return to the beginning
	 * and play again
	 * @throws IllegalStateException if instance is not active
	 */
	public void setLooping(int instanceHook, int loops)
		throws IllegalStateException
	{
		if (!cursors[instanceHook].isActive)
		{
			throw new IllegalStateException(name + " instance: "
					+ instanceHook + " is inactive");
		}
		
		cursors[instanceHook].loop = loops;
	};
	
	/**
	 * Sets a flag which determines what happens when the instance
	 * finishes playing. If {@code true} the instance will be 
	 * added to the pool of available instances and will no longer 
	 * allow updates. If {@code false} then the instance will 
	 * remain available for updates. By default, an instance that
	 * is obtained via a {@code play} method recycles, and an 
	 * instance obtained via {@code getInstance} will not. But in 
	 * both cases the behavior can be changed by setting this
	 * flag.
	 * 
	 * @param instanceHook an {@code int} used to identify an 
	 * {@code AudioCue} instance
	 * @param recycleWhenDone a {@code boolean} that designates
	 * the behavior ("recycle" or not) that occurs when the 
	 * instance finishes playing
	 * @throws IllegalStateException if the instance is not active
	 */
	public void setRecycleWhenDone(int instanceHook, 
			boolean recycleWhenDone) throws IllegalStateException
	{
		if (!cursors[instanceHook].isActive)
		{
			throw new IllegalStateException(name + " instance: "
					+ instanceHook + " is inactive");
		}
		
		cursors[instanceHook].recycleWhenDone = recycleWhenDone;
	}	
	
	/**
	 * Returns {@code true} if instance is active, {@code false}
	 * if not.
	 * 
	 * @param instanceHook an {@code int} used to identify an 
	 * {@code AudioCue} instance
	 * @return {@code true} if instance is playing, {@code false}
	 * if not
	 */
	public boolean getIsActive(int instanceHook)
	{
		return cursors[instanceHook].isActive;
	}
	
	/**
	 * Returns {@code true} if instance is playing, {@code false}
	 * if not
	 * 
	 * @param instanceHook an {@code int} used to identify an 
	 * {@code AudioCue} instance
	 * @return {@code true} if instance is playing, {@code false}
	 * if not
	 */
	public boolean getIsPlaying(int instanceHook)
	{
		return cursors[instanceHook].isPlaying;
	}
	
	
	/*
	 * A private, data-only class that is created and
	 * maintained internally for managing each concurrent instance 
	 * of an {@code AudioCue}. The 'hook' variable is an identifier 
	 * that is created upon instantiation to correspond 
	 * to the position of the {@code AudioCueCursor} instance in 
	 * the {@code AudioCue.cursors} array.
	 * <p>
	 * An instance is either active ({code isActive = true})
	 * in which case it can be updated, or inactive, in which case
	 * it is in a pool of <em>available</em> instances. An 
	 * <em>active</em> instance is either playing or stopped 
	 * (paused). These flags are checked for appropriate state
	 * for various method calls. The {@code recycleWhenDone
	 * boolean} is used to determine whether the instance is placed
	 * back in the pool of available instances when a play 
	 * completes, or if it remains available to update. 
	 * <p>
	 * The <em>target</em> variables are used to ensure that 
	 * changes in real time to the corresponding settings change
	 * in small enough increments that discontinuities are not 
	 * created in the data.
	 */
	private class AudioCueCursor
	{
		volatile boolean isPlaying;
		volatile boolean isActive;
		final int hook;
		
		float idx;
		float speed;
		float volume;
		float pan;
		int loop;
		boolean recycleWhenDone;

		float targetSpeed;
		float targetSpeedIncr;
		int targetSpeedSteps;
		
		float targetVolume;
		float targetVolumeIncr;
		int targetVolumeSteps;
		
		float targetPan;
		float targetPanIncr;
		int targetPanSteps;
		
		AudioCueCursor(int hook)
		{
			this.hook = hook;			
		}
		
		/*
		 * Used to clear settings from previous plays
		 * and put in default settings.
		 */
		void resetInstance()
		{
			isActive = false;
			isPlaying = false;
			idx = 0;
			speed = 1;
			volume = 0;
			pan = 0;
			loop = 0;
			recycleWhenDone = false;
			
			targetSpeedSteps = 0;
			targetVolumeSteps = 0;
			targetPanSteps = 0;
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
		
		AudioCuePlayer(Mixer mixer, int bufferFrames) throws 
			LineUnavailableException
		{
			readBuffer = new float[bufferFrames * 2];
			sdlBufferSize = bufferFrames * 4;
			audioBytes = new byte[sdlBufferSize];
					
			sdl = getSourceDataLine(mixer, info);
			sdl.open(audioFormat, sdlBufferSize);
			sdl.start();
		}
		
		// Audio Thread Code
		public void run()
		{			
			while(playerRunning)
			{
				readBuffer = fillBuffer(readBuffer);
				audioBytes = fromBufferToAudioBytes(audioBytes, readBuffer);
				sdl.write(audioBytes, 0, sdlBufferSize);
			}
			sdl.drain();
			sdl.close();
			sdl = null;
		}
	}

	/*
	 * AudioThread code.
	 * Within while loop.
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
				 * Usually, pan won't change, so let's 
				 * store value and only recalculate when 
				 * it changes.
				 */
				float panFactorL = panL.apply(acc.pan);
				float panFactorR = panR.apply(acc.pan);
				
				for (int i = 0; i < bufferLength; i += 2)
				{
					// adjust volume if needed
					if (acc.targetVolumeSteps-- > 0)
					{
						acc.volume += acc.targetVolumeIncr;
					}
					
					// adjust pan if needed
					if (acc.targetPanSteps-- > 0)
					{
						acc.pan += acc.targetPanIncr;
						panFactorL = panL.apply(acc.pan);
						panFactorR = panR.apply(acc.pan);
					}
					
					// get audioVals (with LERP for fractional idx)
					float[] audioVals = new float[2];
					audioVals = readFractionalFrame(audioVals, acc.idx);
					
					readBuffer[i] += (audioVals[0] 
							* acc.volume * panFactorL);
					readBuffer[i + 1] += (audioVals[1] 
							* acc.volume * panFactorR);
					
					// SET UP FOR NEXT ITERATION
					// adjust pitch if needed
					if (acc.targetSpeedSteps-- > 0)
					{
						acc.speed += 
								acc.targetSpeedIncr;
					}
	
					// set NEXT read position
					acc.idx += acc.speed;
					
					// test for "eof" and "looping"
					if (acc.idx >= (cueFrameLength - 1))
					{
						// keep looping indefinitely
						if (acc.loop == -1)
						{
							acc.idx = 0;
							broadcastLoopEvent(acc);
						}
						// loop specific number of times
						else if (acc.loop > 0)
						{
							acc.loop--;
							acc.idx = 0;
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
							break;
						}
					}
				}
			}
		}
		return readBuffer;
	}
	
	// Audio thread code, gets single stereo Frame pairs.
	// Due to variable pitch, requires LERP between frames.
	private float[] readFractionalFrame(float[] audioVals, float idx)
	{
		final int intIndex = (int) idx;
		final int flatIndex = intIndex * 2;
		
		audioVals[0] = cue[flatIndex + 2] * (idx - intIndex) 
				+ cue[flatIndex] * ((intIndex + 1) - idx);
		
		audioVals[1] = cue[flatIndex + 3] * (idx - intIndex) 
				+ cue[flatIndex + 1] * ((intIndex + 1) - idx);

		return audioVals;
	}
	
	// Audio Thread Code, keep this a self-contained function!
	public static byte[] fromBufferToAudioBytes(byte[] audioBytes, float[] buffer)
	{
		for (int i = 0, n = buffer.length; i < n; i++)
		{
			buffer[i] *= 32767;
			
			audioBytes[i*2] = (byte) buffer[i];
			audioBytes[i*2 + 1] = (byte)((int)buffer[i] >> 8 );
		}
	
		return audioBytes;
	}

	// Function - keep it self contained.
	public static SourceDataLine getSourceDataLine(Mixer mixer, 
			Info info) throws LineUnavailableException
	{
		SourceDataLine sdl;
		
		if (mixer == null)
		{
			sdl = (SourceDataLine)AudioSystem.getLine(info);
		}
		else
		{
			sdl = (SourceDataLine)mixer.getLine(info);
		}
		
		return sdl;
	}
	
	@Override  // AudioMixerTrack interface
	public boolean isRunning() 
	{
		return playerRunning;
	}
	
	@Override  // AudioMixerTrack interface
	public void setRunning(boolean bool) 
	{
		this.playerRunning = bool;
	}
	
	@Override  // AudioMixerTrack interface
	public float[] readTrack() throws IOException 
	{
		return fillBuffer(readBuffer);
	}
	
	
	
	// The following are the methods that broadcast events to 
	// the registered listeners.
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
							this, acc.hook, 0
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
							this, acc.hook, acc.idx
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
							this, acc.hook, acc.idx
					));
		}
	}
	
	private void broadcastLoopEvent(AudioCueCursor acc)
	{
		for (AudioCueListener acl:listeners)
		{
			acl.instanceEventOccurred(
					new AudioCueInstanceEvent(
							Type.LOOP, this, acc.hook, 0
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
							this, acc.hook, acc.idx
					));
		}
	}
}
