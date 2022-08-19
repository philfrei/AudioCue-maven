package com.adonax.audiocue;

import java.util.function.Function;

import javax.sound.sampled.LineUnavailableException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.adonax.audiocue.AudioCueFunctions.PanType;

class AudioCueTest {
	
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
		
		int instance0 = testCue.obtainInstance();
		int instance1 = testCue.obtainInstance();
		int instance2 = testCue.obtainInstance();
		
		// Are instance IDs in the expected range?
		Assertions.assertTrue(instance0 >= 0 && instance0 < 2);
		Assertions.assertTrue(instance1 >= 0 && instance1 < 2);
		// At this point there should not be any instances available.
		Assertions.assertEquals(-1, instance2);
	}
	
	// @TODO test state logic (active, running)
	
	@Test
	void testCursorPositioning() {
		// data will be one second in duration, 44100 total frames
		float[] pcmData = new float[88200]; 
		AudioCue testCue = AudioCue.makeStereoCue(pcmData, "TestCue", 2);
		
		int instance0 = testCue.obtainInstance();
		
		Assertions.assertEquals(44100, testCue.getFrameLength());

		testCue.setFractionalPosition(instance0, 0);
		Assertions.assertEquals(0, testCue.getFramePosition(instance0));
		testCue.setFractionalPosition(instance0, 0.5);
		Assertions.assertEquals(22050, testCue.getFramePosition(instance0));
		testCue.setFractionalPosition(instance0, 1);
		Assertions.assertEquals(44100, testCue.getFramePosition(instance0));
		
		testCue.setMicrosecondPosition(instance0, 0);
		Assertions.assertEquals(0, testCue.getFramePosition(instance0));
		testCue.setMicrosecondPosition(instance0, 500_000);
		Assertions.assertEquals(22050, testCue.getFramePosition(instance0));
		testCue.setMicrosecondPosition(instance0, 1_000_000);
		Assertions.assertEquals(44100, testCue.getFramePosition(instance0));
		
		// Testing exceptions
		// When instance0 is released, isActive will be set to false.
		// Attempts to setFractionalPosition should now throw exception.
		testCue.releaseInstance(instance0);
		Exception thrown = Assertions.assertThrows(
				IllegalStateException.class, () -> {
					testCue.setFractionalPosition(instance0, 0);
				});		
		Assertions.assertEquals(
				"TestCue instance: " + instance0 + " is inactive", 
				thrown.getMessage());
		
		thrown = Assertions.assertThrows(IllegalStateException.class, () -> {
					testCue.setMicrosecondPosition(instance0, 0);
				});		
		Assertions.assertEquals(
				"TestCue instance: " + instance0 + " is inactive", 
				thrown.getMessage());

		// When an instance is started, isPlaying == true
		// which should throw exception.
		int instance1 = testCue.obtainInstance();
		testCue.start(instance1);
		Assertions.assertTrue(testCue.getIsPlaying(instance1));
		thrown = Assertions.assertThrows(
				IllegalStateException.class, () -> {
					testCue.setFractionalPosition(instance1, 0);
				});		
		Assertions.assertEquals(
				"TestCue instance: " + instance1 + " is inactive", 
				thrown.getMessage());
		
		thrown = Assertions.assertThrows(
				IllegalStateException.class, () -> {
					testCue.setMicrosecondPosition(instance1, 0);
				});		
		Assertions.assertEquals( 
				"TestCue instance: " + instance1 + " is inactive", 
				thrown.getMessage());
	}
	
	@Test
	void testGetPcmCopy() {
		int cueLen = 128;
		float[] testCueData = new float[cueLen];
		// fill with random signed, normalized floats
		for (int i = 0; i < cueLen; i++) {
			testCueData[i] = (float)Math.random() * 2 - 1;
		}
		AudioCue testCue = AudioCue.makeStereoCue(testCueData, "testCue", 1);
		float[] pcmCopy = testCue.getPcmCopy();
		Assertions.assertEquals(cueLen, pcmCopy.length);
		
		for (int i = 0; i < cueLen; i++) {
			Assertions.assertEquals(pcmCopy[i], testCueData[i]);
		}
	}
	
	@Test
	void testVolumeBasics() {

		float[] pcmData = new float[100]; 
		AudioCue testCue = AudioCue.makeStereoCue(pcmData, "testCue", 5);
		
		// default volume
		int instance0 = testCue.play();
		Assertions.assertEquals(1, testCue.getVolume(instance0));

		// Volume changes incrementally while playing the cue.
		// Since we aren't actually running the "playing" 
		// methods, the change should not have taken effect.		
		testCue.setVolume(instance0, 0.25);
		Assertions.assertNotEquals(0.25, testCue.getVolume(instance0));
		
		testCue.stop(instance0);
		testCue.setVolume(instance0, 0.75);
		// isPlaying == false, so setVolume() should take immediate effect
		Assertions.assertEquals(0.75, testCue.getVolume(instance0));
		
		// Play, with volume specified
		int instance1 = testCue.play(0.5);
		Assertions.assertEquals(0.5, testCue.getVolume(instance1));
		int instance2 = testCue.play(0.75, 0, 1, 0);
		Assertions.assertEquals(0.75, testCue.getVolume(instance2));
		
		// test clamps
		int instance3 = testCue.play(1.5);
		Assertions.assertEquals(1, testCue.getVolume(instance3));
		int instance4 = testCue.play(-1);
		Assertions.assertEquals(0, testCue.getVolume(instance4));
	}

	@Test
	void testPanBasics() {

		float[] pcmData = new float[100]; 
		AudioCue testCue = AudioCue.makeStereoCue(pcmData, "testCue", 4);
		
		// default pan
		int instance0 = testCue.play();
		Assertions.assertEquals(0, testCue.getPan(instance0));

		// Pan changes incrementally while playing the cue.
		// Since we aren't actually running the "playing" 
		// methods, the change should not have taken effect.
		testCue.setPan(instance0, 0.25);
		Assertions.assertNotEquals(0.25, testCue.getPan(instance0));
		
		testCue.stop(instance0);
		testCue.setPan(instance0, 0.75);
		// isPlaying == false, so setPan() should take immediate effect
		Assertions.assertEquals(0.75, testCue.getPan(instance0));
		
		// Play, with pan specified
		int instance1 = testCue.play(1, -0.5, 1, 0);
		Assertions.assertEquals(-0.5, testCue.getPan(instance1));

		// test clamps
		int instance2 = testCue.play(0.25, 1.5, 1, 0);
		Assertions.assertEquals(1, testCue.getPan(instance2));
		int instance3 = testCue.play(0.25, -1.5, 1, 0);
		Assertions.assertEquals(-1, testCue.getPan(instance3));
	}

	@Test
	void testSpeedBasics() {

		float[] pcmData = new float[100]; 
		AudioCue testCue = AudioCue.makeStereoCue(pcmData, "testCue", 4);
		
		// default speed
		int instance0 = testCue.play();
		Assertions.assertEquals(1, testCue.getSpeed(instance0));

		// Speed changes incrementally while playing the cue.
		// Since we aren't actually running the "playing" 
		// methods, the change should not have taken effect.
		testCue.setSpeed(instance0, 2.5);
		Assertions.assertNotEquals(2.5, testCue.getSpeed(instance0));
		
		testCue.stop(instance0);
		testCue.setSpeed(instance0, 0.75);
		// isPlaying == false, so setSpeed() should take immediate effect
		Assertions.assertEquals(0.75, testCue.getSpeed(instance0));
		
		// play(), with speed specified
		int instance1 = testCue.play(0.5, -0.5, 3, 0);
		Assertions.assertEquals(3, testCue.getSpeed(instance1));

		// test clamps
		int instance2 = testCue.play(0.25, 0.25, 9, 0);
		Assertions.assertEquals(8, testCue.getSpeed(instance2));
		int instance3 = testCue.play(0.25, -1.5, 0.1, 0);
		Assertions.assertEquals(0.125, testCue.getSpeed(instance3));	
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
		testCue.setPanType(PanType.LR_CUT_LINEAR);
		
		// This sets variable needed for reading track (cursor.isPlaying) but 
		// does not output to SDL because we haven't opened the AudioCue.
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
		
		float[] cueData = new float[AudioCue.VOLUME_STEPS * 2];
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
		testCue.setPanType(PanType.LR_CUT_LINEAR);
				
		// Default play() method sets volume to initial value of 1f.
		int instance0 = testCue.play();
		// When playing, volume changes are spread out over AudioCue.VOLUME_STEPS.
		double targetVolume = 0.5;
		testCue.setVolume(instance0, targetVolume);
		// Set to loop so that instance0 does not recycle, so that we 
		// can execute .getVolume() on instance0 at end of test.
		testCue.setLooping(instance0, -1);
		
		float[] testBuffer = testCue.readTrack();
		
		for (int i = 0, n = testBuffer.length - 2; i < n; i+=2) {
			// The PCM out values should progressively diminish as the progressively lowers. 
			Assertions.assertTrue(cueData[i] - testBuffer[i] < cueData[i + 2] - testBuffer[i + 2]);
		}
		
		Assertions.assertEquals(targetVolume, testCue.getVolume(instance0));		
		Assertions.assertEquals(cueData[lastFrame] * targetVolume, testBuffer[lastFrame]);
	}

	@Test
	void testPanFunctions() {

		PanType panType = PanType.FULL_LINEAR;
		Function<Float, Float> panL = panType.left;
		Function<Float, Float> panR = panType.right;
		
		// Test to within 6 decimals (covers float discrepancies) 
		// Why 6? IDK. What is a good amount to use for today's computers?
		float zeroDelta = (float)Math.pow(10, -6);
		
		// TODO: test some intermediate values, not just the
		// end points, e.g., -0.5, 0.5, where formulas have to 
		// perform calculations that might introduce error.
		float panVal = -1f;
		float expectedLeft = 1;
		float expectedRight = 0;
		float volL = panL.apply(panVal);
		float volR = panR.apply(panVal);

		Assertions.assertEquals(expectedLeft, volL, zeroDelta);
		Assertions.assertEquals(expectedRight, volR, zeroDelta);
		
		panVal = 0;
		expectedLeft = 0.5f;
		expectedRight = 0.5f;
		volL = panL.apply(panVal);
		volR = panR.apply(panVal);
		Assertions.assertEquals(expectedLeft, volL, zeroDelta);
		Assertions.assertEquals(expectedRight, volR, zeroDelta);
		
		panVal = 1f;
		expectedLeft = 0;
		expectedRight = 1;
		volL = panL.apply(panVal);
		volR = panR.apply(panVal);
		Assertions.assertEquals(expectedLeft, volL, zeroDelta);
		Assertions.assertEquals(expectedRight, volR, zeroDelta);
		
		panType = PanType.LR_CUT_LINEAR;
		panL = panType.left;
		panR = panType.right;
		
		panVal = -1f;
		expectedLeft = 1;
		expectedRight = 0;
		volL = panL.apply(panVal);
		volR = panR.apply(panVal);		
		Assertions.assertEquals(expectedLeft, volL, zeroDelta);
		Assertions.assertEquals(expectedRight, volR, zeroDelta);
		
		panVal = 0;
		expectedLeft = 1;
		expectedRight = 1;
		volL = panL.apply(panVal);
		volR = panR.apply(panVal);
		Assertions.assertEquals(expectedLeft, volL, zeroDelta);
		Assertions.assertEquals(expectedRight, volR, zeroDelta);
		
		panVal = 1f;
		expectedLeft = 0;
		expectedRight = 1;
		volL = panL.apply(panVal);
		volR = panR.apply(panVal);
		Assertions.assertEquals(expectedLeft, volL, zeroDelta);
		Assertions.assertEquals(expectedRight, volR, zeroDelta);

		panType = PanType.SINE_LAW;
		panL = panType.left;
		panR = panType.right;
		
		panVal = -1f;
		expectedLeft = 1;
		expectedRight = 0;
		volL = panL.apply(panVal);
		volR = panR.apply(panVal);		
		Assertions.assertEquals(expectedLeft, volL, zeroDelta);
		Assertions.assertEquals(expectedRight, volR, zeroDelta);
		
		panVal = 0;
		expectedLeft = (float)Math.cos(Math.PI * 0.25);
		expectedRight = (float)Math.sin(Math.PI * 0.25);
		volL = panL.apply(panVal);
		volR = panR.apply(panVal);
		Assertions.assertEquals(expectedLeft, volL, zeroDelta);
		Assertions.assertEquals(expectedRight, volR, zeroDelta);
		
		panVal = 1f;
		expectedLeft = 0;
		expectedRight = 1;
		volL = panL.apply(panVal);
		volR = panR.apply(panVal);
		Assertions.assertEquals(expectedLeft, volL, zeroDelta);
		Assertions.assertEquals(expectedRight, volR, zeroDelta);
	}
	
	@Test
	void testPanOutput() {
		// PCM data for test cue.
		int cueLength = 10;
		float[] cueData = new float[cueLength];
		// PCM data ranges from -1 to 0 (left), 1 to 0 (right)
		for(int i = 0; i < cueLength; i += 2) {
			cueData[i] = 1 - (i / (float)(cueData.length - 2));
			cueData[i + 1] = -1 + i/(float)(cueData.length - 2);
		}

		// Test to within 6 decimals (covers float discrepancies) 
		// Why 6? IDK. What is a good amount to use for today's computers?
		float zeroDelta = (float)Math.pow(10, -6);		
		
		// Set up AudioCue and instance
		AudioCue testCue = AudioCue.makeStereoCue(cueData, "testCue", 1);
		int instance0 = testCue.obtainInstance();
		testCue.setVolume(instance0, 1);

		// Tests for FULL_LINEAR type pan.
		testCue.setPanType(PanType.FULL_LINEAR);

		// Pan = -1 (full left)
		float panVal = -1;
		testCue.setPan(instance0, panVal);
		testCue.start(instance0);

		float[] testBuffer = testCue.readTrack();
		for (int i = 0; i < cueLength; i+=2) {
			Assertions.assertEquals(cueData[i], testBuffer[i], zeroDelta);
			Assertions.assertEquals(0, testBuffer[i + 1], zeroDelta);
		}

		// Pan = 0 (center)
		testCue.stop(instance0);
		testCue.setFramePosition(instance0, 0);
		panVal = 0;
		testCue.setPan(instance0, panVal);
		testCue.start(instance0);

		testBuffer = testCue.readTrack();
		for (int i = 0; i < cueLength; i+=2) {
			Assertions.assertEquals(cueData[i] / 2, testBuffer[i], zeroDelta);
			Assertions.assertEquals(cueData[i + 1] / 2, testBuffer[i + 1], zeroDelta);
		}

		// Pan = 1 (full right)
		testCue.stop(instance0);
		testCue.setFramePosition(instance0, 0);
		panVal = 1;
		testCue.setPan(instance0, panVal);
		testCue.start(instance0);

		testBuffer = testCue.readTrack();
		for (int i = 0; i < cueLength; i+=2) {
			Assertions.assertEquals(0, testBuffer[i], zeroDelta);
			Assertions.assertEquals(cueData[i + 1], testBuffer[i + 1], zeroDelta);
		}

		/////////////////////////////////////////////////////////////////////////
		testCue.setPanType(PanType.LR_CUT_LINEAR);

		// Pan = -1 (full left)
		testCue.stop(instance0);
		testCue.setFramePosition(instance0, 0);
		panVal = -1;
		testCue.setPan(instance0, panVal);
		testCue.start(instance0);
		
		testBuffer = testCue.readTrack();
		for (int i = 0; i < cueLength; i+=2) {
			Assertions.assertEquals(cueData[i], testBuffer[i], zeroDelta);
			Assertions.assertEquals(0, testBuffer[i + 1], zeroDelta);
		}
		
		// Pan = 0 (center)
		testCue.stop(instance0);
		testCue.setFramePosition(instance0, 0);
		panVal = 0;
		testCue.setPan(instance0, panVal);
		testCue.start(instance0);
		
		testBuffer = testCue.readTrack();
		for (int i = 0; i < cueLength; i+=2) {
			Assertions.assertEquals(cueData[i], testBuffer[i], zeroDelta);
			Assertions.assertEquals(cueData[i + 1], testBuffer[i + 1], zeroDelta);
		}

		// Pan = 1 (full right)
		testCue.stop(instance0);
		testCue.setFramePosition(instance0, 0);
		panVal = 1;
		testCue.setPan(instance0, panVal);
		testCue.start(instance0);
		
		testBuffer = testCue.readTrack();
		for (int i = 0; i < cueLength; i+=2) {
			Assertions.assertEquals(0, testBuffer[i], zeroDelta);
			Assertions.assertEquals(cueData[i + 1], testBuffer[i + 1], zeroDelta);
		}
		
		/////////////////////////////////////////////////////////////////////////
		testCue.setPanType(PanType.SINE_LAW);

		// Pan = -1 (full left)
		testCue.stop(instance0);
		testCue.setFramePosition(instance0, 0);
		panVal = -1;
		testCue.setPan(instance0, panVal);
		testCue.start(instance0);
		
		testBuffer = testCue.readTrack();
		for (int i = 0; i < cueLength; i+=2) {
			Assertions.assertEquals(cueData[i], testBuffer[i], zeroDelta);
			Assertions.assertEquals(0, testBuffer[i + 1], zeroDelta);
		}
		
		// Pan = 0 (center)
		testCue.stop(instance0);
		testCue.setFramePosition(instance0, 0);
		panVal = 0;
		testCue.setPan(instance0, panVal);
		testCue.start(instance0);
		
		testBuffer = testCue.readTrack();
		for (int i = 0; i < cueLength; i+=2) {
			Assertions.assertEquals(cueData[i] * Math.cos(Math.PI * 0.25), testBuffer[i], zeroDelta);
			Assertions.assertEquals(cueData[i + 1] * Math.sin(Math.PI * 0.25), testBuffer[i + 1], zeroDelta);
		}

		// Pan = 1 (full right)
		testCue.stop(instance0);
		testCue.setFramePosition(instance0, 0);
		panVal = 1;
		testCue.setPan(instance0, panVal);
		testCue.start(instance0);
		
		testBuffer = testCue.readTrack();
		for (int i = 0; i < cueLength; i+=2) {
			Assertions.assertEquals(0, testBuffer[i], zeroDelta);
			Assertions.assertEquals(cueData[i + 1], testBuffer[i + 1], zeroDelta);
		}		
	}
	
	@Test
	void testDynamicPan() {
		float[] cueData = new float[AudioCue.PAN_STEPS * 2];
		// PCM test data is given an unchanging value so the output
		// should just show changes due to the changing pan.
		for(int i = 0; i < AudioCue.PAN_STEPS; i++) {
			int frame = i * 2;
			cueData[frame] = 1f;  		// left channel
			cueData[frame + 1] = -1f;  	// right channel
		}
		
		AudioCue testCue = AudioCue.makeStereoCue(cueData, "testCue", 1);
		testCue.setPanType(PanType.FULL_LINEAR);
		int instance0 = testCue.obtainInstance();
		testCue.setVolume(instance0, 1);
		testCue.setPan(instance0, -1);
		testCue.start(instance0);
		// In playing state, the change from -1 to 1 
		// should be incrementally spread across AudioCue.PAN_STEPS
		testCue.setPan(instance0, 1);		
		
		float[] testBuffer = testCue.readTrack();
		int secondToLastFrame = AudioCue.PAN_STEPS - 1;
		for (int i = 0; i < secondToLastFrame; i++) {
			int cueIdx = i * 2;
			// left track values should go from 1 to 0 as pan transitions from -1 to 1
			Assertions.assertTrue(
					cueData[cueIdx] - testBuffer[cueIdx] < cueData[cueIdx + 2] - testBuffer[cueIdx + 2]);
			// right track values should go from 0 to -1 as pan transitions from -1 to 1
			Assertions.assertTrue(
					cueData[cueIdx + 1] - testBuffer[cueIdx + 1] > cueData[cueIdx + 3] - testBuffer[cueIdx]);
		}
		int lastCueFrameIdx =  (AudioCue.PAN_STEPS - 1) * 2;
		Assertions.assertEquals(1, testCue.getPan(instance0));
		Assertions.assertEquals(0, testBuffer[lastCueFrameIdx]);
		Assertions.assertEquals(-1, testBuffer[lastCueFrameIdx + 1]);	
	}
		
	@Test 
	void testSpeedOutput() {
		/*
		 * For the test data, using PCM that goes up from 0
		 * by increments of 0.0001. This will make it easier
		 * to see if the LERP is working correctly on non-integer
		 * frame positions.
		 */
		float[] cueData = new float[AudioCue.DEFAULT_BUFFER_FRAMES * 2];
		for(int i = 0; i < AudioCue.DEFAULT_BUFFER_FRAMES; i++) {
			cueData[i * 2] = i * 0.0001f;
			cueData[i * 2 + 1] = cueData[i * 2];
		}

		AudioCue testCue = AudioCue.makeStereoCue(cueData, "testCue", 1);
		// With CENTER_LINEAR, default pan 0 leaves both L & R values as is.
		testCue.setPanType(PanType.LR_CUT_LINEAR);
		
		int instance0 = testCue.obtainInstance();
		testCue.setVolume(instance0, 1);
		testCue.setPan(instance0, 0);

		// testing 3/4 speed
		float testSpeed = 0.75f;
		testCue.setSpeed(instance0, testSpeed);
		testCue.start(instance0);
		float[] testBuffer = testCue.readTrack();
		
		// Check the current cursor position is at expected
		Assertions.assertEquals(testSpeed * AudioCue.DEFAULT_BUFFER_FRAMES, 
				testCue.getFramePosition(instance0));
		
		// Calculate the expected value after 5 steps taken (i.e., after 5th frame output).
		int testSteps = 5;

		// Test to within 6 decimals (covers float discrepancies) 
		// Why 6? IDK. What is a good amount to use for today's computers?
		float zeroDelta = (float)Math.pow(10, -6);	
		float testCursor = testSpeed * testSteps;
		// LERP formula calculation
		int byteIdx = (int)testCursor * 2;
		float expectedVal = cueData[byteIdx + 2] * (testCursor - (int)testCursor) 
				+ cueData[byteIdx] * (((int)testCursor + 1) - testCursor);
		
		Assertions.assertEquals(expectedVal, testBuffer[testSteps * 2], zeroDelta);
	}
	
	@Test
	void testDynamicSpeed() {

		float[] cueData = new float[AudioCue.SPEED_STEPS * 2];
		for(int i = 0; i < AudioCue.SPEED_STEPS; i++) {
			cueData[i * 2] = i;
			cueData[i * 2 + 1] = cueData[i * 2];
		}
		
		AudioCue testCue = AudioCue.makeStereoCue(cueData, "testCue", 1);
		// With CENTER_LINEAR, default pan 0 leaves both L & R values as is.
		testCue.setPanType(PanType.LR_CUT_LINEAR);
		
		int instance0 = testCue.obtainInstance();
		testCue.setVolume(instance0, 1);
		testCue.setPan(instance0, 0);
		testCue.setLooping(instance0, -1);
		
		// The following maneuver is used to set a smaller buffer size, giving us more 
		// opportunities to check the contents of the AudioCueCursor.
		try {
			testCue.open(AudioCue.DEFAULT_BUFFER_FRAMES / 16);
			testCue.close();
		} catch (IllegalStateException | LineUnavailableException e) {
			e.printStackTrace();
		}
		
		testCue.start(instance0);
		
		// Default speed is 1.
		// By setting a new speed after cue has started
		// the speed change will be handled incrementally.
		testCue.setSpeed(instance0, 0.5);
	
		float[] testBuffer;
		
		double startSpeed = 1;
		double targetSpeed = 0.5;
		double oneIncrement = (targetSpeed - startSpeed) / AudioCue.SPEED_STEPS;
		
		int elapsedFrames = 0;
		do {
			testBuffer = testCue.readTrack();
			elapsedFrames += testBuffer.length / 2;
			if (elapsedFrames > AudioCue.SPEED_STEPS) break;
			
			// Calculate the expected position of the AudioCueCursor.
			double currFrame = elapsedFrames * startSpeed  + 
					(((elapsedFrames + 1) * elapsedFrames * oneIncrement) / 2.0) ;
			float zeroDelta = (float)Math.pow(10, -6);	
			Assertions.assertEquals(currFrame, testCue.getFramePosition(instance0), zeroDelta);
			
		} while (true);
		Assertions.assertEquals(targetSpeed, testCue.getSpeed(instance0));
	}	
	
}
