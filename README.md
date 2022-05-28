# AudioCue-maven
*AudioCue* is a Java audio-playback class, modeled on [javax.sound.sampled.Clip](https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/javax/sound/sampled/Clip.html), 
enhanced with concurrent playback and with dynamic handling of volume, pan and frequency. 
Included is a mixer that can be optionally used to merge the output of multiple *AudioCues* into a single line out.

This project changes the build tool of [an earlier version of AudioCue](https://github.com/philfrei/AudioCue) from *Gradle* to *Maven*. 
A goal: publication via *Sonatype/Maven Central*. 
In support of that goal, unit tests have been added, and some errors and shortcomings uncovered by those tests have been corrected. 
With the two projects starting to diverge, I recommend preferring these more recent builds.

Until *AudioCue* is available at *Maven Central*, the best way to make use of the **AudioCue** class is to fork this project, then clone it to a development environment. 
From there, you can execute Maven's *install* command, which will put the library into your local Maven repository. 
Or, you can directly make use of the jar file that is created in the **/target** subdirectory via the *package* command by adding this jar file to your project's classpath. 
Also, since there are only five files, you can consider copying these files directly into your project. 
Just be sure, if you do, to edit the *package* lines of the files to appropriately reflect the new file locations.

To use *AudioCue* in a Maven project, add the following dependency to your project's POM file.

    <dependency>
      <groupId>com.adonax</groupId>
      <artifactId>audiocue</artifactId>
      <version>1.1.0</version>
    </dependency>
    
The API can be generated via the Javadoc tool. At this time, the API for this project and the earlier [Gradle-based AudioCue](https://github.com/philfrei/AudioCue) are close to identical,
with the only divergence being that the *AudioCueMixer* **.getTrackLength()** method has been renamed 
**.getTracksCount()**. 
This API can be viewed [here](http://adonax.com/AudioCue/api). 
*TODO: generate a new Javadoc API.*

## Usage
### Basic playback (for "fire-and-forget" use)

    // Recommended: define as an instance variable.
    AudioCue myAudioCue; 

    // Recommended: preload one time only
    // This example assumes "myAudio.wav" is located in src/main/resources folder
    // of a Maven project    
    URL url = this.getClass().getResource("/myAudio.wav");
    // This example allows up to 4 concurrent playbacks
    AudioCue myAudioCue = AudioCue.makeStereoCue(url, 4); 
    myAudioCue.open();
    // end of preloading steps

    // Recommended: the play() method should be called on a preloaded 
    // instance variable separately from preloading. Reloading or reopening 
    // prior to each play() will likely add latency for no practical purpose.
    myAudioCue.play();  
    // see API for parameters to override default vol, pan, speed 

    // release resources when sound is no longer needed
    myAudioCue.close();

### Usage: dynamic controls

To dynamically change the playback of a sound, we need to identify
the playback *instance*. Each playback instance has its own identifier,
an `int` ranging from 0 to the number of configured concurrent instances minus one.
These identifiers are initially held in a pool of available instances. 
If the pool has not been depleted, an identifier is obtained from the pool 
at the moment of playback as follows:

    int handle = myAudioCue.play(); 

One can also obtain an identifier from the pool of available instances as follows:

    int handle = myAudioCue.obtainInstance(); 

A specific instance can be started, stopped, or released by including the 
instance identifier as an argument:

    myAudioCue.start(handle);
    myAudioCue.stop(handle);
    myAudioCue.releaseInstance(handle);

*Releasing* returns the instance identifier to the pool of available instances.
  
An important distinction exists between instances obtained from a 
`play()` method versus the `obtainInstance()` method.
The default value of the property `recycleWhenDone` for an instance 
obtained from `play()` is `true`. Thus, when playback completes, the
identifier will be returned to the pool of available instances.
An instance arising from `obtainInstance()` has the property `recycleWhenDone`
set to `false`. In this case, when playback completes, the instance identifier
is *not* returned to the pool of available instances, and the instance remains 
receptive to additional commands. In this case, after playing through to the
end, the frame position will be set to the end of the audio-data file, and,
like a `Clip` that has played through, will require repositioning and restarting
for further plays.

Properties that can be altered for an instance include the following:

    //*volume*: 
    myAudioCue.setVolume(handle, value); // double ranging from 0 (silent)
                                         // to 1 (full volume)
    //*panning*: 
    myAudioCue.setPan(handle, value); // double ranging from -1 (full left)
                                      // to 1 (full right)
    //*speed of playback*: 
    myAudioCue.setSpeed(handle, value); // value is a factor that is  
                                        // multiplied against the normal playback rate, e.g.,
                                        // 2 will double playback speed, 0.5 will halve it 
    //*position* (cannot be changed while instance is playing):
    myAudioCue.setFramePosition(handle, value);  // position in frames
    myAudioCue.setMillisecondPosition(handle, value); // position in milliseconds
    myAudioCue.setsetFractionalPosition(handle, value); 
                                        // value is a decimal fraction of the cue length
                                        // where 0 = start, 1 = end
    //*other*:                                                
    myAudioCue.setRecyleWhenDone(handle, value); // boolean
    myAudioCue.setLooping(handle, value); // number of additional times the cue will replay
                                          // -1 = infinite looping

### Usage: output configuration

Output configuration occurs within the *AudioCue*'s `open()` method. The 
default configuration employs *javax.sound.sampled.AudioSystem*'s default
`Mixer` and a `SourceDataLine` set with a 1024-frame buffer and the highest
thread priority. A high thread priority should not affect performance of 
the rest of an application, as audio threads normally spend the vast majority 
of their time in a blocked state. The buffer size can be set to 
optimize the balance between latency and dropouts. Incidents of dropouts 
are usually lessened by increasing the buffer size.

You can override the defaults by using an alternate form of the `open()` method. For example:

    myAudioCue.open(mixer, bufferFrames, threadPriority);

Each *AudioCue* can have its own configuration, and will output on its own `SourceDataLine` line.  

### Usage: Outputting via *AudioMixer*
Most operating systems will support numerous concurrently playing `SourceDataLine` 
streams. But if desired, the streams can be merged into a single output line using 
*AudioMixer*, which is part of this package. This is accomplished by providing an 
*AudioMixer* instance as an argument to the *AudioCue* **open()** method: 

    myAudioCue.open(myAudioMixer);
    
Internally, this method adds the `AudioCue` to the `AudioMixer` using the interface 
*AudioMixerTrack*. If an `AudioCue` is a track on an `AudioMixer`, the *AudioCue* 
method **close()** will automatically remove the track from the `AudioMixer`.

    myAudioCue.close();

The *AudioMixer* is configured, upon instantiation, with a default 
`javax.sound.sampled.Mixer`, with a buffer size of 8192 frames, and 
with the highest thread priority. Alternate values can be provided 
at instantiation.

Any `AudioCue` routed through an `AudioMixer` will use the 
*AudioMixer*'s configuration properties. Pending `AudioMixer`
additions and removals are handled automatically at the start of 
each buffer iteration.

In the following somewhat artificial example, we create and start an 
`AudioMixer`, add an `AudioCue` track, play the cue, then shut it all down.

    AudioMixer audioMixer = new AudioMixer();
    audioMixer.start();
    // At this point, AudioMixer will create and start a runnable and will
    // actively output 'silence' (zero values) on its SourceDataLine. 
    
    URL url = this.getClass().getResource("myAudio.wav");
    AudioCue myAudioCue = AudioCue.makeStereoCue(url, 1); 
    myAudioCue.open(mixer); 
    // The open method automatically adds the AudioCue to
    // the AudioMixer.
    
    myAudioCue.play();
    Thread.sleep(2000); // For purposes of the demo only, to hold the program open to give the AudioCue time
                        // to play to completion (assumes cue is shorter than 2 seconds).
    myAudioCue.close(); // will remove AudioCue from the mix                    
    audioMixer.stop();  // AudioMixer will stop outputting and will
                        // close the runnable in an 'orderly' manner.

Reminder, this is an artificial example: best practice is to initialize and 
open an `AudioCue` only once, and to then reuse the `AudioCue` for multiple playbacks.
Also, this example uses `Thread.sleep()` to prevent the program from advancing and 
closing before the cue finishes playing. The reason for this is that `AudioCue` launches
a *daemon* thread internally for playback, so a playing instance will *not* prevent a 
program from closing once all the program instructions are completed. Unlike this example, 
an `AudioCue` is more typically called by a GUI that remains open, or from a long-running
program, and is well able to complete playing without the program closing. In that more 
usual case, pausing the thread that has the *play()* method is quite unnecessary.  

### Usage: Additional examples and test files
Additional examples and test files can be found in the accompanying project 
[audiocue-demo](https://github.com/philfrei/audiocue-demo).
