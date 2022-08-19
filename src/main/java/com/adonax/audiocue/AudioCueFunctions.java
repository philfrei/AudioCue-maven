package com.adonax.audiocue;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.Line.Info;

/**
 * A class containing static functions and public
 * enums used in the AudioCue package 
 * 
 * @since 2.1.0
 * @version 2.1.0
 * @author Philip Freihofner
 */
public class AudioCueFunctions {

	/**
	 * Obtains a {@code SourceDataLine} that is available for use from the 
	 * specified {@code javax.sound.sampled.Mixer} and that matches the
	 * description in the specified {@code Line.Info}.   
	 * 
	 * @param mixer - an {@code javax.sound.sampled.Mixer}
	 * @param info - describes the desired line 
	 * @return a a line that is available for use from the specified
	 * 				{@code javax.sound.sampled.Mixer} and that matches the 
	 * 				description	in the specified {@code Line.Info} object
	 * @throws LineUnavailableException if a matching line is not available
	 */
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
	
	/**
	 * Converts an array of signed, normalized float PCM values to a 
	 * corresponding byte array using 16-bit, little-endian encoding. 
	 * This is the sole audio format supported by this application, 
	 * and is expected by the {@code SourceDataLine} configured for
	 * media play. Because each float value is converted into two  
	 * bytes, the receiving array, {@code audioBytes}, must be twice
	 * the length of the array of data to be converted, {@code sourcePcm}.
	 * Failure to comply will throw an {@code IllegalArgumentException}.
	 * 
	 * @param audioBytes - an byte array ready to receive the converted 
	 * 					audio data.	Should be twice the length of 
	 * 					{@code buffer}.
	 * @param sourcePcm - a float array with signed, normalized PCM data to
	 * 					be converted 
	 * @return the byte array {@code audioBytes} after is has been populated
	 * 					with the converted data
	 * @throws IllegalArgumentException if destination array is not exactly
	 * 					twice the length of the source array
	 */
	public static byte[] fromPcmToAudioBytes(byte[] audioBytes, float[] sourcePcm)
	{
		if (sourcePcm.length * 2 != audioBytes.length) {
			throw new IllegalArgumentException(
					"Destination array must be exactly twice the length of the source array");
		}
		
		for (int i = 0, n = sourcePcm.length; i < n; i++)
		{
			sourcePcm[i] *= 32767;
			
			audioBytes[i*2] = (byte) sourcePcm[i];
			audioBytes[i*2 + 1] = (byte)((int)sourcePcm[i] >> 8 );
		}
	
		return audioBytes;
	}
	
}
