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
	 * The {@code enum VolumeType} is a repository of functions 
	 * used to convert an input in the linear range 0..1 to an 
	 * attenuation factor, where the input 0 indicating silence 
	 * and 1 indicating full volume, and the returned factor 
	 * intended to be multiplied to the PCM values on a per-element 
	 * basis.
	 * <p>
	 * The perception of amplitudes is not linear, but exponential, 
	 * and commonly measured using the deciBel unit. The formula 
	 * x^4 is widely used as a "good enough" approximation of the
	 * of the more costly dB calculation: exp(x * 6.908)/1000 that
	 * spans a range of 60 dB.
	 * <p>
	 * A straight use of linear values will tend to result in 
	 * hard-to-perceive changes in the upper range and more extreme 
	 * sensitivity with the lower values. But with a 60dB dynamic range,
	 * the x^4 approximation may have the opposite problem, with 
	 * values below 0.5 quickly becoming inaudible. For this reason, 
	 * a selection of intermediate exponential curves are offered.
	 * 
	 * @version 2.1.0
	 * @since 2.1.0
	 * @author Philip Freihofner
	 * 
	 * @see AudioCue#setVolType(VolType)
	 */	
	public static enum VolType 
	{	
		/**
		 * Represents an amplitude function that directly uses linear
		 * linear values ranging from 0 (silent) to 1 (maximum amplitude). 
		 * This volume control tends to result in most of the
		 * amplification occurring in the lower end of the numerical range.
		 * @see VolType
		 */	
		LINEAR( x -> x),
		/**
		 * Represents an amplitude function that tends to result in most
		 * of the perceived amplification taking place in the lower numerical
		 * range, but less so than the LINEAR function. Input values, ranging
		 * from 0 (silent) to 1 (full volume) are mapped to volume factors
		 * with the function <strong>f(x) = x * x</strong>.
		 * @see VolType 
		 */
		EXP_X2 ( x -> (float)x * x),
		/**
		 * Represents an 'intermediate' amplitude function that is somewhat 
		 * louder in the lower numeric range than EXP_X4, but quieter in
		 * the lower numeric range than EXP_X2. Input values, ranging
		 * from <strong>0</strong> (<em>silent</em>) to <strong>1</strong> 
		 * (<em>full volume</em>) are mapped to volume factors with the 
		 * function <strong>f(x) = x * x * x</strong>.
		 * @see VolType
		 */
		EXP_X3 ( x -> (float)x * x * x),
		/**
		 * Represents an amplitude function that is commonly used to map 
		 * the linear values ranging from <strong>0</strong> (<em>silent</em>) 
		 * to <strong>1</strong> (<em>full volume</em>) to volume factors 
		 * that approximate the geometric curve of human hearing. With a 
		 * dynamic range of 60 dB, values below 0.5 can quickly become
		 * inaudible. For this reason, functions with louder lower ends
		 * (EXP_X3, EXP_X2) are also offered. The mapping function is 
		 * calculated as follows: <strong>f(x) = x * x * x * x</strong>
		 * @see VolType
		 */
		EXP_X4 ( x -> (float)x * x * x * x),
		/**
		 * Represents an amplitude function with enhanced sensitivity to 
		 * volume changes occurring in the upper numerical area between
		 * <strong>0</strong> (<em>silent</em>) and <strong>1</strong> 
		 * (<em>full volume</em>) The mapping function is 
		 * calculated as follows: <strong>f(x) = x * x * x * x * x</strong>
		 * @see VolType
		 */
		EXP_X5 ( x -> (float)x * x * x * x * x),
		/**
		 * Represents an amplitude function that best maps to the geometric
		 * curve of human hearing, but at a higher computational cost than
		 * EXP_X4. The mapping function is calculated as follows:<br>
		 * <strong>f(x) = Math.exp(x * 6.908) / 1000.0</strong><br>
		 * The input value of 0 is directly mapped to zero instead of using
		 * the above function.
		 * @see VolType
		 * 
		 */
		EXP_60dB (x -> x == 0 ? 0 : (float)(Math.exp(x * 6.908) / 1000.0));
		
		final Function<Float, Float> vol;
	
		VolType(Function<Float, Float> vol) 
		{
			this.vol = vol;
		}
	}
	
	/**
	 * The {@code enum PanType} is a repository of functions 
	 * for volume-based panning for stereo media.Each function 
	 * takes a linear pan setting as an input, ranging 
	 * from -1 (100% left) to 1 (100% right) with 0 being the 
	 * center pan setting.
	 * 
	 * @version 2.1.0
	 * @since 2.0.0
	 * @author Philip Freihofner
	 * 
	 * @see PanType#FULL_LINEAR
	 * @see PanType#LEFT_RIGHT_CUT_LINEAR
	 * @see PanType#SQUARE_LAW
	 * @see PanType#SINE_LAW
	 * @see AudioCue#setPanType(PanType)
	 */
	public static enum PanType 
	{
		/**
		 * Represents a panning function that uses linear 
		 * gradients that taper from edge to edge, and the 
		 * combined volume is stronger at the edges than 
		 * at the center. For pan values -1 to 1 the left 
		 * channel factor is tapered with a linear function 
		 * from 1 to 0, and the right channel factor is 
		 * tapered via a linear function from 0 to 1.
		 * @see PanType
		 */	
		FULL_LINEAR(
				x -> 1 - ((1 + x) / 2),
				x -> (1 + x) / 2
			),
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
		 * @see PanType
		 */
		LEFT_RIGHT_CUT_LINEAR(  
				x -> Math.max(0, Math.min(1, 1 - x)),
				x -> Math.max(0, Math.min(1, 1 + x))
			), 
		/**
		 * Represents a panning function that uses square
		 * roots to taper the amplitude from edge to edge, 
		 * while maintaining the same total power of the 
		 * combined tracks across the panning range.
		 * <p>
		 * For inputs -1 (full left) to 1 (full right):<br>
		 * Left vol factor = Math.sqrt(1 - (1 + x) / 2.0) <br>
		 * Right vol factor = Math.sqrt((1 + x) / 2.0)
		 * <p>
		 * Settings will tend to sound a little more central 
		 * than with the use of SINE_LAW panning.
		 * @see PanType
		 * @see PanType#SINE_LAW
		 */
		SQUARE_LAW(
				x -> (float)(Math.sqrt(1 - (1 + x) / 2.0)),
				x -> (float)(Math.sqrt((1 + x) / 2.0))
			),

		/**
		 * Represents a panning function that uses sines
		 * to taper the amplitude from edge to edge while
		 * maintaining the same total power of the combined 
		 * tracks across the panning range.
		 * <p>
		 * For inputs -1 (full left) to 1 (full right):<br>
		 * Left vol factor = Math.sin((Math.PI / 2 ) * (1 - (1 + x) / 2.0)) <br>
		 * Right vol factor = Math.sin((Math.PI / 2 ) * ((1 + x) / 2.0))
		 * <p>
		 * Settings will tend to sound a little more spread 
		 * than with the use of SQUARE_LAW panning.
		 *  
 		 * @see PanType
 		 * @see PanType#SQUARE_LAW
		 */
		SINE_LAW(
				x -> (float)(Math.sin((Math.PI / 2) * (1 - (1 + x) / 2.0))),
				x -> (float)(Math.sin((Math.PI / 2 ) * ((1 + x) / 2.0)))
			);
	
		final Function<Float, Float> left;
		final Function<Float, Float> right;
	
		PanType(Function<Float, Float> left, 
				Function<Float, Float> right) {
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
