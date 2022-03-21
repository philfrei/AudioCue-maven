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

import java.util.concurrent.CopyOnWriteArrayList;

import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

/**
 * {@code AudioMixer} combines the output of members of an  
 * {@code AudioMixerTrack} collection into a single 
 * {@code SourceDataLine} output line. Classes implementing
 * {@code AudioMixerTrack} can be added and removed from the
 * mix asynchronously, with the operation occurring at the  
 * next buffer iteration. Source tracks must provide for the 
 * return of an array of sound data frames whose length is 
 * specified by the {@code AudioMixer}. Unlike an analog 
 * mixer used in sound studios, the AudioMixer does <i>not</i> 
 * provide functions such as panning or volume
 * controls. The only standard function is an equivalent
 * of a <em>mute</em> control, accessible via the {@code running}
 * variable. The mixer imposes a simple floor/ceiling of -1, 1, 
 * to guard against volume overflows.
 * 
 * @author Philip Freihofner
 * @version AudioCue 1.2
 * @see http://adonax.com/AudioCue
 */
public class AudioMixer 
{
	private AudioMixerTrack[] trackCache, mixerTracks;  
	private CopyOnWriteArrayList<AudioMixerTrack> trackManager; 
	private volatile boolean trackCacheUpdated;
	private int trackCount;
	
	/**
	 * Returns the number of tracks being mixed.
	 * 
	 * @return integer number of tracks being mixed.
	 */
	public int getTrackLength() {return trackCount;}
	
	public final int bufferSize, sdlByteBufferSize, 
			readBufferSize;
	private float[] audioData;
	private Mixer mixer;
	public final int threadPriority;
	
	private volatile boolean running;

	/**
	 * Constructor for {@code AudioMixer}, using default
	 * settings: 
	 * Mixer           = system default
	 * Buffer size     = 8196 frames
	 * Thread priority = 10.
	 * 
	 * The buffer size pertains to the frames collected in
	 * a single {@code while} loop iteration. A buffer that 
	 * corresponds to the same number of frames converted
	 * to bytes is assigned to the {@code SourceDataLine}. 
	 */
	public AudioMixer()
	{
		this(null, 1024 * 8, 10); // default build
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
	 * @param bufferSize int specifying the number of frames to 
	 * process with each iteration
	 * @param threadPriority int ranging from 1 to 10 specifying 
	 * the priority of the sound thread
	 */
	public AudioMixer(Mixer mixer, int bufferSize, int threadPriority) 
	{
		trackManager = new CopyOnWriteArrayList<AudioMixerTrack>();
		this.bufferSize = bufferSize;
		this.readBufferSize = bufferSize * 2;
		this.sdlByteBufferSize = bufferSize * 4;
		this.mixer = mixer;
		this.threadPriority = threadPriority;
	}
	
	// reminder: this does NOT update the trackCache!!
	/**
	 * Designates an {@code AudioMixerTrack} to be staged
	 * for loading to the array of tracks being mixed. If 
	 * the mixer is not running, the track will become 
	 * part of the mix when the (@code AudioMixer} is started. 
	 * If the {@code AudioMixer} is running, the method 
	 * {@code updateTracks} must be executed in order for
	 * the new track to be added to the mix.
	 * 
	 * @param track {@code AudioMixerTrack} to be added
	 */
	public void addTrack(AudioMixerTrack track)
	{
		trackManager.add(track);
	}

	// reminder: this does NOT update the trackCache!!
	/**
	 * Designates an {@code AudioMixerTrack} to be staged
	 * for removal from the array of tracks being mixed. If 
	 * the mixer is not running, the track, if previously
	 * added, will <em>not</em> be included in the mix
	 * when the (@code AudioMixer} is started. 
	 * If the {@code AudioMixer} is running, the method 
	 * {@code updateTracks} must be executed in order for
	 * the new track to be removed from the mix.
	 * 
	 * @param track
	 * @throws IllegalThreadStateException
	 */
	public void removeTrack(AudioMixerTrack track) throws IllegalThreadStateException
	{
		trackManager.remove(track);
	}

	/**
	 * This method signals the {@code AudioMixer}'s playback 
	 * loop to update the collection of tracks being mixed
	 * at the next opportunity (depends on the size of the
	 * buffer). Tracks to be added or removed are staged 
	 * for update by the methods {@code addTrack} and
	 * {@code removeTrack}.
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
		System.out.println("CoreMixer.updateTracks, new size:" + size);
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
	public void start() throws IllegalStateException, 
		LineUnavailableException
	{
		if (running) throw new IllegalStateException(
				"AudioMixer is already running!");
		
		updateTracks();
		
		AudioMixerPlayer player = new AudioMixerPlayer(
				mixer, bufferSize); 
		Thread t = new Thread(player);
		t.setPriority(threadPriority);
		t.start();
		
		running = true;
	}
	
	/**
	 * Stops the iteration of the {@code AudioMixer} after the 
	 * soonest data write operation. 
	 * 
	 * @throws IllegalStateException if the {@code AudioMixer}
	 * is already in a stopped state.
	 */
	public void stop() throws IllegalStateException
	{
		if (!running) throw new IllegalStateException("PFCoreMixer already stopped!");
		
		running = false;
	}
	
    private float[] fillBufferFromTracks(float[] normalizedOut)
	{	
    	// loop through all tracks, summing	
		for (int n = 0; n < trackCount; n++)	
		{
			if (mixerTracks[n].isRunning())
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
    
	private class AudioMixerPlayer implements Runnable
	{
		private SourceDataLine sdl;
		private float[] readBuffer;
		private byte[] audioBytes;
		
		AudioMixerPlayer(Mixer mixer, int bufferFrames) throws 
		LineUnavailableException
		{
			audioBytes = new byte[sdlByteBufferSize];
			
			sdl = AudioCue.getSourceDataLine(mixer, AudioCue.info);
			sdl.open(AudioCue.audioFormat, sdlByteBufferSize);
			sdl.start();
		}
		
		// Sound Thread
		public void run()
		{
			while(running)
			{				
		    	if (trackCacheUpdated)
		    	{
		    		/*
		    		 * Concurrency plan: Better to allow a late  
		    		 * or redundant update than to skip an update.
		    		 * Example: flag = true, next line resets, but 
		    		 * updater sets true again prior to mixerTracks
		    		 * assignment. We might load the same trackCache
		    		 * twice. That is OK. 
		    		 */
		    		trackCacheUpdated = false; 
		    		mixerTracks = trackCache;
		    		trackCount = mixerTracks.length;
		    	}
				readBuffer = new float[readBufferSize];
				readBuffer = fillBufferFromTracks(readBuffer);
				audioBytes = AudioCue.fromBufferToAudioBytes(
								audioBytes, readBuffer);
				sdl.write(audioBytes, 0, sdlByteBufferSize);
			}

			sdl.drain();
			sdl.close();
			sdl = null;
		}
	}
}

