package com.adonax.audiocue;

import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

/**
 * An {@code AudioMixer} mixes the media content of all the members  
 * of a {@code AudioMixerTrack} collection into a single output
 * line. Classes implementing {@code AudioMixerTrack} can be added 
 * and removed from the mix asynchronously, with the operation 
 * occurring at the next iteration of the read buffer. Unlike a 
 * mixer used in sound studios, the {@code AudioMixer} does not 
 * provide functions such as panning or volume controls. 
 * <p>
 * An {@code SourceDataLine} can be in one of two states: (1) running, 
 * or (2) not running. When running, audio data is read from the 
 * constituent tracks, mixed and written as a single stream using a
 * {@code javax.sound.sampled.SourceDataLine}. The mixer imposes a 
 * simple floor/ceiling of -1, 1, to guard against volume overflows.
 * When not running, the {@code SourceDataLine} is allowed to drain 
 * and close. A new {@code SourceDataLine} is instantiated if/when 
 * this {@code AudioMixer} is reopened.
 * <p>
 * Values used to configure the media output are provided in the 
 * constructor, and are held as immutable. These include a 
 * {@code javax.sound.sampled.Mixer} used to provide the 
 * {@code SourceDataLine}, the size of the buffer used for iterative
 * reads of the PCM data, and the thread priority. Multiple constructors
 * are provided to facilitate the use of default values. These
 * configuration values override those associated with the constituent
 * tracks. 
 * 
 * @since 2.0.0
 * @version 2.1.0
 * @author Philip Freihofner
 * 
 * @see AudioMixerTrack
 */
public class AudioMixer implements AutoCloseable
{
	private AudioMixerTrack[] trackCache;  
 
	private CopyOnWriteArrayList<AudioMixerTrack> trackManager; 
	private volatile boolean trackCacheUpdated;
	private int trackCount;
	
	/**
	 * Returns the number of tracks being mixed.
	 * 
	 * @return integer number of tracks being mixed.
	 */
	public int getTracksCount() {return trackCount;}
	
	/**
	 * An immutable number of PCM frames held in array buffers, set 
	 * during instantiation.
	 */
	public final int bufferFrames;
	
	/**
	 * An immutable number describing the size of an internal array 
	 * buffer, set during instantiation. The {@code readBufferSize} has 
	 * two PCM values per each frame being handled, corresponding to the
	 * left and right stereo channels, and is calculated by multiplying
	 * {@code bufferFrames} by 2.
	 */
	public final int readBufferSize; 

	/**
	 * An immutable number describing the size of an internal array 
	 * buffer, set during instantiation. The {@code sdlBufferSize} has 
	 * four bytes per frame, as each of the two PCM values per frame is
	 * encoded into two constituent bytes. The {@code sdlByteBufferSize}
	 * is calculated by multiplying {@code bufferFrames} by 4.
	 */
	public final int sdlByteBufferSize;

	private Mixer mixer;
	
	/**
	 * A value that holds the priority level of the thread that that handles
	 * the media output, set upon instantiation of the class. The value is 
	 * clamped to the range {@code java.lang.Thread.MIN_PRIORITY} to
	 * {@code java.lang.Thread.MAX_PRIORITY}.   
	 */
	public final int threadPriority;
	
	private volatile boolean mixerRunning;

	/**
	 * Constructor for {@code AudioMixer}, using default
	 * settings: 
	 * Mixer           = system default
	 * Buffer size     = 8192 frames
	 * Thread priority = 10.
	 * 
	 * The buffer size pertains to the frames collected in
	 * a single {@code while} loop iteration. A buffer that 
	 * corresponds to the same number of frames converted
	 * to bytes is assigned to the {@code SourceDataLine}. 
	 */
	public AudioMixer()
	{
		this(null, 1024 * 8, Thread.MAX_PRIORITY); // default build
	}
	
	/**
	 * Constructor for {@code AudioMixer}. The buffer size 
	 * is the number of frames collected in a single {@code while} 
	 * loop iteration. A buffer with a corresponding frame
	 * count, calculated in bytes, is assigned to the 
	 * {@code SourceDataLine}. A thread priority of 10 is
	 * recommended, in order to help prevent sound drop outs.
	 * Note that a well designed and properly running sound thread 
	 * should spend the vast majority of its time in a blocked 
	 * state, and thus have a minimal impact in terms of usurping
	 * cpu cycles from other threads.
	 * 
	 * @param mixer javax.sound.sampled.Mixer to be used
	 * @param bufferFrames int specifying the number of frames to 
	 * process with each iteration
	 * @param threadPriority int ranging from 1 to 10 specifying 
	 * the priority of the sound thread
	 */
	public AudioMixer(Mixer mixer, int bufferFrames, int threadPriority) 
	{
		trackManager = new CopyOnWriteArrayList<AudioMixerTrack>();
		this.bufferFrames = bufferFrames;
		this.readBufferSize = bufferFrames * 2;
		this.sdlByteBufferSize = bufferFrames * 4;
		this.mixer = mixer;
		this.threadPriority = threadPriority;
	}
	
	// reminder: this does NOT update the trackCache!!
	/**
	 * Designates an {@code AudioMixerTrack} to be staged for addition
	 * into the collection of tracks actively being mixed. If the  
	 * {@code AudioMixer} is running, actual addition occurs when the 
	 * {@code updateTracks} method is executed. If the 
	 * {@code AudioMixer} is not running, the addition will occur
	 * automatically when the {@code start} method is called.
	 * 
	 * @param track - an {@code AudioMixerTrack} to be added to the mix
	 * @see #removeTrack(AudioMixerTrack)
	 * @see #updateTracks()
	 */
	public void addTrack(AudioMixerTrack track)
	{
		trackManager.add(track);
	}

	// reminder: this does NOT update the trackCache!!
	/**
	 * Designates an {@code AudioMixerTrack} to be staged for removal
	 * from the collection of tracks actively being mixed.  If the
	 * {@code AudioMixer} is running, actual removal occurs when the 
	 * {@code updateTracks} method is executed.  If the
	 * {@code AudioMixer} is not running, the removal will occur
	 * automatically when the {@code start} method is called.
	 *  
	 * @param track - an {@code AudioMixerTrack} to be removed from the mix
	 * @see #addTrack(AudioMixerTrack)
	 * @see #updateTracks()
	 */
	public void removeTrack(AudioMixerTrack track)
	{
		trackManager.remove(track);
	}

	/**
	 * Signals the internal media writer to load an updated  
	 * {@code AudiomixerTrack} collection at the next opportunity. 
	 * Tracks to be added or removed are first staged using the 
	 * methods {@code addTrack} and {@code removeTrack}.
	 * @see #addTrack(AudioMixerTrack)
	 * @see #removeTrack(AudioMixerTrack)
	 */
	public void updateTracks()
	{
		int size = trackManager.size();
		AudioMixerTrack[] workCopyTracks = new AudioMixerTrack[size]; 
		for (int i = 0; i < size; i++)
		{
			workCopyTracks[i] = trackManager.get(i);
		}
		
		trackCache = workCopyTracks;
		trackCacheUpdated = true;
	}
	
	/**
	 * Starts the operation of the {@code AudioMixer}. A running
	 * {@code AudioMixer} iteratively sums a buffer's worth of 
	 * frames of sound data from a collection of 
	 * {@code AudioMixerTrack}s, and writes the resulting 
	 * array to a {@code SourceDataLine}. 
	 * 
	 * @throws IllegalStateException is thrown if the 
	 * {@code AudioMixer} is already running.
	 * @throws LineUnavailableException is thrown if there 
	 * is a problem securing a {@code SourceDataLine}
	 */
	public void start() throws LineUnavailableException
	{
		if (mixerRunning) throw new IllegalStateException(
				"AudioMixer is already running!");
		
		updateTracks();
		
		AudioMixerPlayer player = new AudioMixerPlayer(mixer); 
		Thread t = new Thread(player);
		t.setPriority(threadPriority);
		t.start();
		
		mixerRunning = true;
	}
	
	/**
	 * Sets a flag that will signal the {@code AudioMixer} to stop
	 * media writes and release resources.
	 * 
	 * @throws IllegalStateException if the {@code AudioMixer}
	 * is already in a stopped state.
	 */
	public void stop()
	{
		if (!mixerRunning) throw new IllegalStateException("PFCoreMixer already stopped!");
		
		mixerRunning = false;
	}

	@Override
	public void close() throws Exception {
		stop();
	}
	
	private class AudioMixerPlayer implements Runnable
	{
		private SourceDataLine sdl;
		private float[] readBuffer;
		private byte[] audioBytes;
		private AudioMixerTrack[] mixerTracks;
		private float[] audioData;
		
		AudioMixerPlayer(Mixer mixer) throws LineUnavailableException
		{
			audioBytes = new byte[sdlByteBufferSize];
			readBuffer = new float[readBufferSize];
			
			sdl = AudioCueFunctions.getSourceDataLine(mixer, AudioCue.info);
			sdl.open(AudioCue.audioFormat, sdlByteBufferSize);
			sdl.start();
		}
		
		// Sound Thread
		public void run()
		{
			while(mixerRunning)
			{				
		    	if (trackCacheUpdated)
		    	{
		    		/*
		    		 * Concurrency plan: Better to allow a late  
		    		 * or redundant update than to skip an update.
		    		 */
		    		trackCacheUpdated = false; 
		    		mixerTracks = trackCache;
		    		trackCount = mixerTracks.length;
		    	}
		    	Arrays.fill(readBuffer, 0);
				readBuffer = fillBufferFromTracks(readBuffer);
				audioBytes = AudioCueFunctions.fromPcmToAudioBytes(audioBytes, readBuffer);
				sdl.write(audioBytes, 0, sdlByteBufferSize);
			}

			sdl.drain();
			sdl.close();
			sdl = null;
		}
		
	   private float[] fillBufferFromTracks(float[] normalizedOut)
		{	
	    	// loop through all tracks, summing	
			for (int n = 0; n < trackCount; n++)	
			{
				if (mixerTracks[n].isTrackRunning())
				{
					try 
					{
						audioData = mixerTracks[n].readTrack();
						for (int i = 0; i < readBufferSize; i++)
						{
							normalizedOut[i] += audioData[i];
						}
					} 
					catch (Exception e) 
					{
						e.printStackTrace();
					}							
				}
				for (int i = 0; i < readBufferSize; i++)
				{
					if (normalizedOut[i] > 1)
					{
						normalizedOut[i] = 1;
					}
					else if (normalizedOut[i] < -1)
					{
						normalizedOut[i] = -1;
					}
				}
			}
			return normalizedOut;
		}
	}
}