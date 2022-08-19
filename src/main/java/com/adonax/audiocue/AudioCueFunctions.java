package com.adonax.audiocue;

import java.util.function.Function;

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
	 * The {@code enum Type} is a repository of functions 
	 * used to perform volume-based panning for stereo media. 
	 * Each function takes a pan setting as an input, ranging 
	 * from -1 (100% left) to 1 (100% right) with 0 being the 
	 * center pan setting. 
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
				x -> 1 - ((1 + x) / 2),
				x -> (1 + x) / 2
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
				x -> (float)(Math.cos(Math.PI * ((1 + x) / 4))),
				x -> (float)(Math.sin(Math.PI * ((1 + x) / 4)))
				);
	
		final Function<Float, Float> left;
		final Function<Float, Float> right;
	
		PanType(Function<Float, Float> left, 
				Function<Float, Float> right)
		{
			this.left = left;
			this.right = right;
		}
	}

	
	
	
	
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
