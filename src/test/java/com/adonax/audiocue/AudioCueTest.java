package com.adonax.audiocue;

import java.util.function.Function;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.adonax.audiocue.AudioCue.PanType;

class AudioCueTest {

	/*
	 * TODO still
	 * check output when pan is changing dynamically (for all pans?)
	 * 
	 * check output for static speed
	 * check output when speed is changing dynamically
	 */

	// To cover float discrepancies. Why 6 digits? IDK.
	// What is a good amount to use as a delta for today's computers?
	float zeroDelta = (float)Math.pow(10, -6);
	
	
	@Test
	void testBasicProperties() {
		float[] pcmData = new float[44100];
		AudioCue testCue = AudioCue.makeStereoCue(pcmData, "testCue", 2);
		
		Assertions.assertEquals("testCue", testCue.getName());
		// stereo pcm array created, so assume 2 pcm values per frame
		Assertions.assertEquals(44100/2, testCue.getFrameLength());
		Assertions.assertEquals(500_000, testCue.getMicrosecondLength());
	}
	
	@Test
	void testPolyphony() {
		float[] pcmData = new float[44100];
		AudioCue testCue = AudioCue.makeStereoCue(pcmData, "testCue", 2);
		
		int hook0 = testCue.obtainInstance();
		int hook1 = testCue.obtainInstance();
		int hook2 = testCue.obtainInstance();
		
		Assertions.assertTrue(hook0 >= 0 && hook0 < 2);
		Assertions.assertTrue(hook1 >= 0 && hook1 < 2);
		Assertions.assertEquals(-1, hook2);		
	}
	
	@Test
	void testCursorPositioning() {
		// data will be one second in duration, 44100 total frames
		float[] pcmData = new float[88200]; 
		AudioCue testCue = AudioCue.makeStereoCue(pcmData, "testCue", 2);
		
		int hook0 = testCue.obtainInstance();
		
		Assertions.assertEquals(44100, testCue.getFrameLength());

		testCue.setFractionalPosition(hook0, 0);
		Assertions.assertEquals(0, testCue.getFramePosition(hook0));
		testCue.setFractionalPosition(hook0, 0.5);
		Assertions.assertEquals(22050, testCue.getFramePosition(hook0));
		testCue.setFractionalPosition(hook0, 1);
		Assertions.assertEquals(44100, testCue.getFramePosition(hook0));
		
		testCue.setMicrosecondPosition(hook0, 0);
		Assertions.assertEquals(0, testCue.getFramePosition(hook0));
		testCue.setMicrosecondPosition(hook0, 500_000);
		Assertions.assertEquals(22050, testCue.getFramePosition(hook0));
		testCue.setMicrosecondPosition(hook0, 1_000_000);
		Assertions.assertEquals(44100, testCue.getFramePosition(hook0));
		
		// Testing exceptions
		// When instance is released, isActive will be false
		// which should throw exception.
		testCue.releaseInstance(hook0);
		IllegalStateException thrown = Assertions.assertThrows(
				IllegalStateException.class, () -> {
					testCue.setFractionalPosition(hook0, 0);
				});		
		Assertions.assertEquals("Illegal state, testCue, instance:" 
				+ hook0, thrown.getMessage());
		
		thrown = Assertions.assertThrows(
				IllegalStateException.class, () -> {
					testCue.setMicrosecondPosition(hook0, 0);
				});		
		Assertions.assertEquals("Illegal state, testCue, instance:" 
				+ hook0, thrown.getMessage());

		// When instance is started, isPlaying == true
		// which should throw exception.
		int hook1 = testCue.obtainInstance();
		testCue.start(hook1);
		Assertions.assertTrue(testCue.getIsPlaying(hook1));
		thrown = Assertions.assertThrows(
				IllegalStateException.class, () -> {
					testCue.setFractionalPosition(hook1, 0);
				});		
		Assertions.assertEquals("Illegal state, testCue, instance:" 
				+ hook1, thrown.getMessage());
		thrown = Assertions.assertThrows(
				IllegalStateException.class, () -> {
					testCue.setMicrosecondPosition(hook1, 0);
				});		
		Assertions.assertEquals("Illegal state, testCue, instance:" 
				+ hook1, thrown.getMessage());
	}
	
	@Test
	void testVolumeBasics() {

		float[] pcmData = new float[100]; 
		AudioCue testCue = AudioCue.makeStereoCue(pcmData, "testCue", 5);
		
		// default volume
		int hook0 = testCue.play();
		Assertions.assertEquals(1, testCue.getVolume(hook0));

		// Volume changes incrementally while running, so
		// since we aren't actually playing it, the volume
		// change should not have taken effect.
		testCue.setVolume(hook0, 0.25);
		Assertions.assertNotEquals(0.25, testCue.getVolume(hook0));
		
		// Play, with volume specified
		int hook1 = testCue.play(0.5);
		Assertions.assertEquals(0.5, testCue.getVolume(hook1));
		int hook2 = testCue.play(0.75, 0, 1, 0);
		Assertions.assertEquals(0.75, testCue.getVolume(hook2));
		
		// test clamps
		int hook3 = testCue.play(1.5);
		Assertions.assertEquals(1, testCue.getVolume(hook3));
		int hook4 = testCue.play(-1);
		Assertions.assertEquals(0, testCue.getVolume(hook4));
	}
	
	

	@Test
	void testPanBasics() {

		float[] pcmData = new float[100]; 
		AudioCue testCue = AudioCue.makeStereoCue(pcmData, "testCue", 4);
		
		// default pan
		int hook0 = testCue.play();
		Assertions.assertEquals(0, testCue.getPan(hook0));

		// Pan changes incrementally while running, so
		// since we aren't actually playing it, the pan
		// change should not have taken effect.
		testCue.setPan(hook0, 0.25);
		Assertions.assertNotEquals(0.25, testCue.getPan(hook0));
		
		// Play, with pan specified
		int hook1 = testCue.play(1, -0.5, 1, 0);
		Assertions.assertEquals(-0.5, testCue.getPan(hook1));

		// test clamps
		int hook2 = testCue.play(0.25, 1.5, 1, 0);
		Assertions.assertEquals(1, testCue.getPan(hook2));
		int hook3 = testCue.play(0.25, -1.5, 1, 0);
		Assertions.assertEquals(-1, testCue.getPan(hook3));
	}

	@Test
	void testSpeedBasics() {

		float[] pcmData = new float[100]; 
		AudioCue testCue = AudioCue.makeStereoCue(pcmData, "testCue", 4);
		
		// default speed
		int hook0 = testCue.play();
		Assertions.assertEquals(1, testCue.getSpeed(hook0));

		// Speed changes incrementally while running, so
		// since we aren't actually playing it, the speed
		// change should not have taken effect.
		testCue.setSpeed(hook0, 2.5);
		Assertions.assertNotEquals(2.5, testCue.getSpeed(hook0));
		
		// Play, with speed specified
		int hook1 = testCue.play(0.5, -0.5, 3, 0);
		Assertions.assertEquals(3, testCue.getSpeed(hook1));

		// test clamps
		int hook2 = testCue.play(0.25, 0.25, 9, 0);
		Assertions.assertEquals(8, testCue.getSpeed(hook2));
		int hook3 = testCue.play(0.25, -1.5, 0.1, 0);
		Assertions.assertEquals(0.125, testCue.getSpeed(hook3));	
	}
	
	@Test
	void testReadBasics() {
		/*
		 * Do we need to mock the data in cue[]? This isn't a 
		 * case where we are interacting with a different class.
		 * Establish some credibility by loading the AudioCue
		 * with a preset array, and then seeing if the buffer
		 * returned by read matches.
		 */
		
		float[] cueData = new float[AudioCue.DEFAULT_BUFFER_FRAMES * 2];
		float lastFrame = AudioCue.DEFAULT_BUFFER_FRAMES - 1;
		// values range from 0 to 1 over the buffer.
		for(int i = 0, n = AudioCue.DEFAULT_BUFFER_FRAMES; i < n; i++) {
			cueData[i * 2] = i / lastFrame;
			cueData[(i * 2) + 1] = cueData[i * 2];
		}

		AudioCue testCue = AudioCue.makeStereoCue(cueData, "testCue", 1);
		// With CENTER_LINEAR, pan 0 leaves both L & R values as is.
		testCue.setPanType(PanType.CENTER_LINEAR);
		testCue.play();
		
		// This makes use of the default readBuffer instantiated in the
		// AudioCue constructor.
		float[] testBuffer = testCue.readTrack();
		
//		for (int i = 0, n = testBuffer.length; i < n; i++) {
//			System.out.println("i:" + i + "\tval:" + testBuffer[i] + "\tcue:" + cueData[i]);
//		}
		
		Assertions.assertArrayEquals(cueData, testBuffer);
	}
	
	@Test
	void testDynamicVolume() {
		
		float[] cueData = new float[AudioCue.DEFAULT_BUFFER_FRAMES * 2];
		int lastFrame = cueData.length - 2;
		// Goal is to show the output volume goes from the initial
		// val to the target volume over the course of VOLUME_STEPS
		// Test data is given a stable value so the output buffer
		// should just reflect the volume factor changing from 
		// 1 to 0.5.
		for(int i = 0, n = cueData.length; i < n; i++) {
			cueData[i] = 0.8f;
		}

		AudioCue testCue = AudioCue.makeStereoCue(cueData, "testCue", 1);
		// With CENTER_LINEAR, pan 0 leaves both L & R values as is.
		testCue.setPanType(PanType.CENTER_LINEAR);
				
		// Default play() method sets volume to initial value of 1f.
		int hook = testCue.play();
		// When playing, volume changes are spread out over AudioCue.VOLUME_STEPS.
		testCue.setVolume(hook, 0.5);
		
		float[] testBuffer = testCue.readTrack();
		
		for (int i = 0, n = testBuffer.length - 2; i < n; i+=2) {
//			System.out.println("i:" + i + "\tval:" + testBuffer[i] + "\tcue:" + cueData[i]);
			// The PCM out values should progressively diminish as the progressively lowers. 
			Assertions.assertTrue(cueData[i] - testBuffer[i] < cueData[i + 2] - testBuffer[i + 2]);
		}
//		System.out.println("i:" + lastFrame + "\tval:" + testBuffer[lastFrame] + "\tcue:" + cueData[lastFrame]);				
		Assertions.assertEquals(cueData[lastFrame], testBuffer[lastFrame] * 2);
	}

	@Test
	void testPanFunctions() {

		AudioCue.PanType panType = AudioCue.PanType.FULL_LINEAR;
		Function<Float, Float> panL = panType.left;
		Function<Float, Float> panR = panType.right;
		
//		// Test to within 6 decimals (covers float discrepancies) Why 6? IDK
//		// What is a good amount to use as a delta for today's computers?
//		float zeroDelta = (float)Math.pow(10, -6);
		
		float panVal = -1f;
		float expectedLeft = 1;
		float expectedRight = 0;
		float volL = panL.apply(panVal);
		float volR = panR.apply(panVal);		
//		System.out.println("PanType:" + panType + "\tPanVal:" + panVal 
//				+ "\tVolFactors [" + volL + ", " + volR + "]");
		Assertions.assertEquals(expectedLeft, volL, zeroDelta);
		Assertions.assertEquals(expectedRight, volR, zeroDelta);
		
		panVal = 0;
		expectedLeft = 0.5f;
		expectedRight = 0.5f;
		volL = panL.apply(panVal);
		volR = panR.apply(panVal);
//		System.out.println("PanType:" + panType + "\tPanVal:" + panVal 
//				+ "\tVolFactors [" + volL + ", " + volR + "]");
		Assertions.assertEquals(expectedLeft, volL, zeroDelta);
		Assertions.assertEquals(expectedRight, volR, zeroDelta);
		
		panVal = 1f;
		expectedLeft = 0;
		expectedRight = 1;
		volL = panL.apply(panVal);
		volR = panR.apply(panVal);
//		System.out.println("PanType:" + panType + "\tPanVal:" + panVal 
//				+ "\tVolFactors [" + volL + ", " + volR + "]");
		Assertions.assertEquals(expectedLeft, volL, zeroDelta);
		Assertions.assertEquals(expectedRight, volR, zeroDelta);
		
		panType = AudioCue.PanType.CENTER_LINEAR;
		panL = panType.left;
		panR = panType.right;
		
		panVal = -1f;
		expectedLeft = 1;
		expectedRight = 0;
		volL = panL.apply(panVal);
		volR = panR.apply(panVal);		
//		System.out.println("\nPanType:" + panType + "\tPanVal:" + panVal 
//				+ "\tVolFactors [" + volL + ", " + volR + "]");
		Assertions.assertEquals(expectedLeft, volL, zeroDelta);
		Assertions.assertEquals(expectedRight, volR, zeroDelta);
		
		panVal = 0;
		expectedLeft = 1;
		expectedRight = 1;
		volL = panL.apply(panVal);
		volR = panR.apply(panVal);
//		System.out.println("PanType:" + panType + "\tPanVal:" + panVal 
//				+ "\tVolFactors [" + volL + ", " + volR + "]");
		Assertions.assertEquals(expectedLeft, volL, zeroDelta);
		Assertions.assertEquals(expectedRight, volR, zeroDelta);
		
		panVal = 1f;
		expectedLeft = 0;
		expectedRight = 1;
		volL = panL.apply(panVal);
		volR = panR.apply(panVal);
//		System.out.println("PanType:" + panType + "\tPanVal:" + panVal 
//				+ "\tVolFactors [" + volL + ", " + volR + "]");
		Assertions.assertEquals(expectedLeft, volL, zeroDelta);
		Assertions.assertEquals(expectedRight, volR, zeroDelta);

		panType = AudioCue.PanType.CIRCULAR;
		panL = panType.left;
		panR = panType.right;
		
		panVal = -1f;
		expectedLeft = 1;
		expectedRight = 0;
		volL = panL.apply(panVal);
		volR = panR.apply(panVal);		
//		System.out.println("\nPanType:" + panType + "\tPanVal:" + panVal 
//				+ "\tVolFactors [" + volL + ", " + volR + "]");
		Assertions.assertEquals(expectedLeft, volL, zeroDelta);
		Assertions.assertEquals(expectedRight, volR, zeroDelta);
		
		panVal = 0;
		expectedLeft = (float)Math.cos(Math.PI * 0.25);
		expectedRight = (float)Math.sin(Math.PI * 0.25);
		volL = panL.apply(panVal);
		volR = panR.apply(panVal);
//		System.out.println("PanType:" + panType + "\tPanVal:" + panVal 
//				+ "\tVolFactors [" + volL + ", " + volR + "]");
		Assertions.assertEquals(expectedLeft, volL, zeroDelta);
		Assertions.assertEquals(expectedRight, volR, zeroDelta);
		
		panVal = 1f;
		expectedLeft = 0;
		expectedRight = 1;
		volL = panL.apply(panVal);
		volR = panR.apply(panVal);
//		System.out.println("PanType:" + panType + "\tPanVal:" + panVal 
//				+ "\tVolFactors [" + volL + ", " + volR + "]");
		Assertions.assertEquals(expectedLeft, volL, zeroDelta);
		Assertions.assertEquals(expectedRight, volR, zeroDelta);
	}

	@Test
	void testPanOutput() {
		// PCM data for test cue.
		int cueLength = 10;
		float[] cueData = new float[cueLength];	
		for(int i = 0; i < cueLength; i += 2) {
			cueData[i] = 1 - (i / (float)(cueData.length - 2));
			cueData[i + 1] = -1 + i/(float)(cueData.length - 2);
		}
//		for (int i = 0; i < cueLength; i+=2) {
//			System.out.println("cue:[" + cueData[i] + ", " + cueData[i+1] + "]");
//		}

		// Set up AudioCue and instance
		AudioCue testCue = AudioCue.makeStereoCue(cueData, "testCue", 1);
		int hook = testCue.obtainInstance();
		testCue.setVolume(hook, 1);
		
		// Tests for FULL_LINEAR type pan.
		testCue.setPanType(AudioCue.PanType.FULL_LINEAR);
//		System.out.println("\nPan:" + AudioCue.PanType.FULL_LINEAR);

		// Pan = -1 (full left)
		float panVal = -1;
		testCue.setPan(hook, panVal);
		testCue.start(hook);
		
		float[] testBuffer = testCue.readTrack();
		for (int i = 0; i < cueLength; i+=2) {
//			System.out.println("pan = " + panVal + "\ti:" + i + "\t  cue:[" + cueData[i] + ", " + cueData[i+1] + "]"
//					+ "\tresult:[" + testBuffer[i] + ", " + testBuffer[i+1] + "]");
			Assertions.assertEquals(cueData[i], testBuffer[i], zeroDelta);
			Assertions.assertEquals(0, testBuffer[i + 1], zeroDelta);
		}
		
		// Pan = 0 (center)
		testCue.stop(hook);
		testCue.setFramePosition(hook, 0);
		panVal = 0;
		testCue.setPan(hook, panVal);
		testCue.start(hook);
		
		testBuffer = testCue.readTrack();
//		System.out.println();
		for (int i = 0; i < cueLength; i+=2) {
//			System.out.println("pan = " + panVal + "\ti:" + i + "\t  cue:[" + cueData[i] + ", " + cueData[i+1] + "]"
//					+ "\tresult:[" + testBuffer[i] + ", " + testBuffer[i+1] + "]");
			Assertions.assertEquals(cueData[i] / 2, testBuffer[i], zeroDelta);
			Assertions.assertEquals(cueData[i + 1] / 2, testBuffer[i + 1], zeroDelta);
		}

		// Pan = 1 (full right)
		testCue.stop(hook);
		testCue.setFramePosition(hook, 0);
		panVal = 1;
		testCue.setPan(hook, panVal);
		testCue.start(hook);
		
		testBuffer = testCue.readTrack();
//		System.out.println();
		for (int i = 0; i < cueLength; i+=2) {
//			System.out.println("pan = " + panVal + "\ti:" + i + "\t  cue:[" + cueData[i] + ", " + cueData[i+1] + "]"
//					+ "\tresult:[" + testBuffer[i] + ", " + testBuffer[i+1] + "]");
			Assertions.assertEquals(0, testBuffer[i], zeroDelta);
			Assertions.assertEquals(cueData[i + 1], testBuffer[i + 1], zeroDelta);
		}

		/////////////////////////////////////////////////////////////////////////
		testCue.setPanType(AudioCue.PanType.CENTER_LINEAR);
//		System.out.println("\nPan:" + AudioCue.PanType.CENTER_LINEAR);

		// Pan = -1 (full left)
		testCue.stop(hook);
		testCue.setFramePosition(hook, 0);
		panVal = -1;
		testCue.setPan(hook, panVal);
		testCue.start(hook);
		
		testBuffer = testCue.readTrack();
		for (int i = 0; i < cueLength; i+=2) {
//			System.out.println("pan = " + panVal + "\ti:" + i + "\t  cue:[" + cueData[i] + ", " + cueData[i+1] + "]"
//					+ "\tresult:[" + testBuffer[i] + ", " + testBuffer[i+1] + "]");
			Assertions.assertEquals(cueData[i], testBuffer[i], zeroDelta);
			Assertions.assertEquals(0, testBuffer[i + 1], zeroDelta);
		}
		
		// Pan = 0 (center)
		testCue.stop(hook);
		testCue.setFramePosition(hook, 0);
		panVal = 0;
		testCue.setPan(hook, panVal);
		testCue.start(hook);
		
		testBuffer = testCue.readTrack();
//		System.out.println();
		for (int i = 0; i < cueLength; i+=2) {
//			System.out.println("pan = " + panVal + "\ti:" + i + "\t  cue:[" + cueData[i] + ", " + cueData[i+1] + "]"
//					+ "\tresult:[" + testBuffer[i] + ", " + testBuffer[i+1] + "]");
			Assertions.assertEquals(cueData[i], testBuffer[i], zeroDelta);
			Assertions.assertEquals(cueData[i + 1], testBuffer[i + 1], zeroDelta);
		}

		// Pan = 1 (full right)
		testCue.stop(hook);
		testCue.setFramePosition(hook, 0);
		panVal = 1;
		testCue.setPan(hook, panVal);
		testCue.start(hook);
		
		testBuffer = testCue.readTrack();
//		System.out.println();
		for (int i = 0; i < cueLength; i+=2) {
//			System.out.println("pan = " + panVal + "\ti:" + i + "\t  cue:[" + cueData[i] + ", " + cueData[i+1] + "]"
//					+ "\tresult:[" + testBuffer[i] + ", " + testBuffer[i+1] + "]");
			Assertions.assertEquals(0, testBuffer[i], zeroDelta);
			Assertions.assertEquals(cueData[i + 1], testBuffer[i + 1], zeroDelta);
		}
		
		/////////////////////////////////////////////////////////////////////////
		testCue.setPanType(AudioCue.PanType.CIRCULAR);
//		System.out.println("\nPan:" + AudioCue.PanType.CIRCULAR);

		// Pan = -1 (full left)
		testCue.stop(hook);
		testCue.setFramePosition(hook, 0);
		panVal = -1;
		testCue.setPan(hook, panVal);
		testCue.start(hook);
		
		testBuffer = testCue.readTrack();
		for (int i = 0; i < cueLength; i+=2) {
//			System.out.println("pan = " + panVal + "\ti:" + i + "\t  cue:[" + cueData[i] + ", " + cueData[i+1] + "]"
//					+ "\tresult:[" + testBuffer[i] + ", " + testBuffer[i+1] + "]");
			Assertions.assertEquals(cueData[i], testBuffer[i], zeroDelta);
			Assertions.assertEquals(0, testBuffer[i + 1], zeroDelta);
		}
		
		// Pan = 0 (center)
		testCue.stop(hook);
		testCue.setFramePosition(hook, 0);
		panVal = 0;
		testCue.setPan(hook, panVal);
		testCue.start(hook);
		
		testBuffer = testCue.readTrack();
//		System.out.println();
		for (int i = 0; i < cueLength; i+=2) {
//			System.out.println("pan = " + panVal + "\ti:" + i + "\t  cue:[" + cueData[i] + ", " + cueData[i+1] + "]"
//					+ "\tresult:[" + testBuffer[i] + ", " + testBuffer[i+1] + "]");
			Assertions.assertEquals(cueData[i] * Math.cos(Math.PI * 0.25), testBuffer[i], zeroDelta);
			Assertions.assertEquals(cueData[i + 1] * Math.sin(Math.PI * 0.25), testBuffer[i + 1], zeroDelta);
		}

		// Pan = 1 (full right)
		testCue.stop(hook);
		testCue.setFramePosition(hook, 0);
		panVal = 1;
		testCue.setPan(hook, panVal);
		testCue.start(hook);
		
		testBuffer = testCue.readTrack();
//		System.out.println();
		for (int i = 0; i < cueLength; i+=2) {
//			System.out.println("pan = " + panVal + "\ti:" + i + "\t  cue:[" + cueData[i] + ", " + cueData[i+1] + "]"
//					+ "\tresult:[" + testBuffer[i] + ", " + testBuffer[i+1] + "]");
			Assertions.assertEquals(0, testBuffer[i], zeroDelta);
			Assertions.assertEquals(cueData[i + 1], testBuffer[i + 1], zeroDelta);
		}		
	}
	
	
	@Test
	void testDynamicPan() {
		float[] cueData = new float[AudioCue.DEFAULT_BUFFER_FRAMES * 2];
		// PCM test data is given an unchanging value so the output
		// should just reflect the change in the pan.
		for(int i = 0; i < AudioCue.DEFAULT_BUFFER_FRAMES; i++) {
			int frame = i * 2;
			cueData[frame] = 1f;  		// left channel
			cueData[frame + 1] = -1f;  	// right channel
		}
		
		AudioCue testCue = AudioCue.makeStereoCue(cueData, "testCue", 1);
		testCue.setPanType(AudioCue.PanType.FULL_LINEAR);
		int hook = testCue.obtainInstance();
		testCue.setVolume(hook, 1);
		testCue.setPan(hook, -1);
		testCue.start(hook);
		// Because we are in a playing state, the change from -1 to 1 
		// should be spread out over AudioCue.PAN_STEPS
		testCue.setPan(hook, 1);		
		
		float[] testBuffer = testCue.readTrack();
		int secondToLastFrame = AudioCue.DEFAULT_BUFFER_FRAMES - 1;
		for (int i = 0; i < secondToLastFrame; i++) {
			int cueIdx = i * 2;
//			System.out.println(" frame:" + i + "\tcue:[" + cueData[cueIdx] + ", " + cueData[cueIdx + 1] + "]"
//					+ "\tresult:[" + testBuffer[cueIdx] + ", " + testBuffer[cueIdx + 1] + "]");
			// left track values should go from 1 to 0 as pan transitions from -1 to 1
			Assertions.assertTrue(
					cueData[cueIdx] - testBuffer[cueIdx] < cueData[cueIdx + 2] - testBuffer[cueIdx + 2]);
			// right track values should go from 0 to -1 as pan transitions from -1 to 1
			Assertions.assertTrue(
					cueData[cueIdx + 1] - testBuffer[cueIdx + 1] > cueData[cueIdx + 3] - testBuffer[cueIdx]);
		}
		int lastCueFrameIdx =  (AudioCue.DEFAULT_BUFFER_FRAMES - 1) * 2;
//		System.out.println(" frame:" + secondToLastFrame 
//				+ "\tcue:[" + cueData[lastCueFrameIdx] + ", " 
//				+ cueData[lastCueFrameIdx + 1] + "]"
//				+ "\tresult:[" + testBuffer[lastCueFrameIdx] + ", " 
//				+ testBuffer[lastCueFrameIdx + 1] + "]");
		Assertions.assertEquals(0, testBuffer[lastCueFrameIdx]);
		Assertions.assertEquals(-1, testBuffer[lastCueFrameIdx + 1]);	
	}
		
	@Test 
	void testSpeedOutput() {
		/*
		 * For the test data, let's create PCM that goes from 0 to the 
		 * readBuffer length by increments of 0.0001. This will make it easier
		 * to see if the interpolation is working correctly.
		 */
		float[] cueData = new float[AudioCue.DEFAULT_BUFFER_FRAMES * 2];
		for(int i = 0; i < AudioCue.DEFAULT_BUFFER_FRAMES; i++) {
			cueData[i * 2] = i * 0.0001f;
			cueData[i * 2 + 1] = cueData[i * 2];
		}

		AudioCue testCue = AudioCue.makeStereoCue(cueData, "testCue", 1);
		// With CENTER_LINEAR, default pan 0 leaves both L & R values as is.
		testCue.setPanType(PanType.CENTER_LINEAR);
		
		int hook = testCue.obtainInstance();
		testCue.setVolume(hook, 1);
		testCue.setPan(hook, 0);
		testCue.start(hook);
		
		float[] testBuffer = testCue.readTrack();
		
		// This merely shows that the output data from readTrack() matches the PCM.
		for (int i = 0; i < AudioCue.DEFAULT_BUFFER_FRAMES; i++) {
			int idx = i * 2;
			System.out.println(
					"frame:" + i + "\tcue:[" + cueData[idx] + ",      \t" + cueData[idx + 1] + "]   "
					+ "\tresult:[" + testBuffer[idx] + ",   \t" + testBuffer[idx + 1] + "]");
		}
		
		testCue.setFramePosition(hook, 0);
		float testSpeed = 0.75f;
		testCue.setSpeed(hook, testSpeed);
		testCue.start(hook);
		// testing 3/4 speed
		testBuffer = testCue.readTrack();
		
		// Check the current position is at expected
		Assertions.assertEquals(testSpeed * AudioCue.DEFAULT_BUFFER_FRAMES, testCue.getFramePosition(hook));
		
		// Calculate the expected value after 5 steps taken.
		float cursor5 = testSpeed * 5;
		int cueIndex = (int)cursor5 * 2;
		// LERP formula calculation
		float expectedVal = cueData[cueIndex + 2] * (cursor5 - (int)cursor5) 
				+ cueData[cueIndex] * (((int)cursor5 + 1) - cursor5);
		
		Assertions.assertEquals(expectedVal, testBuffer[5 * 2], zeroDelta);
		
//		for (int i = 0; i < AudioCue.DEFAULT_BUFFER_FRAMES; i++) {
//			int idx = i * 2;
//			System.out.println(
//					"frame:" + i + "\tcue:[" + cueData[idx] + ",      \t" + cueData[idx + 1] + "]   "
//					+ "\tresult:[" + testBuffer[idx] + ",   \t" + testBuffer[idx + 1] + "]");
//		}		
//		System.out.println("cursor Location:" + testCue.getFramePosition(hook));
	}
	
	
}
