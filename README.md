# AudioCue-maven
*AudioCue* is a Java audio-playback class, modeled on [javax.sound.sampled.Clip](https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/javax/sound/sampled/Clip.html), enhanced with concurrent playback and dynamic handling of volume, pan and frequency. Included is a mixer for merging multiple playing instances into a single audio out.

This project revises the build tool of [an earlier version of AudioCue](https://github.com/philfrei/AudioCue) from Gradle to Maven, with the goal of publishing via [Sonatype/Maven Central](https://search.maven.org/). *TODO: some steps remain prior to publication.*

Until *AudioCue* is available at Maven Central, the best way to make use of the **AudioCue** class is to fork this project, then clone to a development environment. From there, you can execute Maven's *install* command, which will add the **.jar** to the local Maven repository. Or, you can directly make use of the **.jar** created in the **/target** subdirectory via the *package* command by, for example, adding it to your project's classpath.

To use *AudioCue* in a Maven project, add the following dependency to your project's POM file.

    <dependency>
      <groupId>com.adonax</groupId>
      <artifactId>audiocue</artifactId>
      <version>1.0.0</version>
    </dependency>
    
The API can be generated via the Javadoc tool. At this time, the API for this project and the earlier [Gradle-based AudioCue](https://github.com/philfrei/AudioCue) are identical. This API can be viewed [here](http://adonax.com/AudioCue/api).

## Usage
### Basic playback (for "fire-and-forget" use)

    // Recommended: define as an instance variable.
    AudioCue myAudioCue; 

    // Recommended: preload one time only
    // -- this example assumes "myAudio.wav" is located in src/main/resources folder    
    URL url = this.getClass().getResource("/myAudio.wav");
    // -- example allows up to 4 concurrent playbacks
    AudioCue myAudioCue = AudioCue.makeStereoCue(url, 4); 
    myAudioCue.open();
    // end of preloading steps

    myAudioCue.play();  // see API for parameters to override default vol, pan, pitch 
    
    // release resources when sound is no longer needed
    myAudioCue.close();

### Usage: dynamic controls

To dynamically change the playback of a sound, we need to identify
the playback instance. Each concurrent playback has its own identifier,
an **int** ranging from 0 to the number of configured concurrent instances less one. 
An ID can be obtained at the moment of playback as follows:

    int handle = myAudioCue.play(); 

One can also obtain an ID from the pool of available instances as follows:

    int handle = myAudioCue.obtainInstance(); 

A specific instance can be started, stopped, and released 
(*releasing* returns the instance to the pool of available 
instances) by including the int ID as an argument:

    myAudioCue.start(handle);
    myAudioCue.stop(handle);
    myAudioCue.release(handle);

An important distinction exists between instances returned from a 
`play()` method versus the `obtainInstance()` method.
The default value of an editable boolean field `recycleWhenDone`
for an instance obtained from `play()` is `true`, and so, 
will automatically be released and recycled after a playback completes.
An instance arising from `obtainInstance()` has this value set to `false`. 
In this case, when playback completes, the instance remains receptive to 
additional commands. Note that similar to a `Clip`, the instance will be 
positioned at the end of the audio file after playback, and will require 
repositioning and restarting.

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
    //*position*:
    myAudioCue.setFramePosition(handle, frameNumber);
    myAudioCue.setMillisecondPosition(handle, int); // position in millis
    myAudioCue.setsetFractionalPosition(int, double); // position as a fraction of whole cue,
                                                    // where 0 = first frame, 1 = last frame

### Usage: output configuration

Output configuration occurs with the *AudioCue*'s `open()` method. The 
default configuration employs *javax.sound.sampled.AudioSystem*'s default
`Mixer` and a `SourceDataLine` set with a 1024-frame buffer and the highest
thread priority. (A high thread priority should not affect performance of 
the rest of an application, as audio threads normally spend the vast majority 
of their time in a blocked state.) The buffer size can be set to 
optimize the balance between latency and dropouts. (Incidents of dropouts 
are usually lessened by increasing the buffer size.)

You can override the output line defaults by using an alternate form
of the `open()` method. For example:

    myAudioCue.open(mixer, bufferFrames, threadPriority);

Each _AudioCue_ can have its own configuration, and will 
output on its own `SourceDataLine` line, similar to the way that each 
Java `Clip` consumes an output line.  

### Usage: Outputting via _AudioMixer_
Most operating systems will support numerous concurrently playing `SourceDataLine` 
streams. But if desired, the streams can be merged into a single output line using 
_AudioMixer_, which is part of this package. This is accomplished by providing an 
_AudioMixer_ instance to the _open()_ method: 

    myAudioCue.open(myAudioMixer); 

The _AudioMixer_ is configured, upon instantiation, with a default 
`javax.sound.sampled.Mixer`, a buffer size of 8192 frames, and 
the highest thread priority. Alternate values can be provided 
at instantiation.

Any _AudioCue_ routed through an _AudioMixer_ will use the 
*AudioMixer*'s configuration properties. Pending _AudioMixer_
additions and removals are handled automatically at 
the start of each buffer iteration.

In the following example, we create and start an _AudioMixer_, add
an _AudioCue_ track, play the cue, then shut it all down.

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
    Thread.sleep(2000); // For purposes of the demo, wait for the cue to finish
                        // (assumes cue is shorter than 2 seconds)
    myAudioCue.close(); // will remove AudioCue from the mix                    
    audioMixer.stop();  // AudioMixer will stop outputting and will
                        // close the runnable in an 'orderly' manner.

Reminder, this is an artificial example: best practice is to initialize and 
open an _AudioCue_ only once, and to reuse the _AudioCue_ instance.

### Usage: Additional examples and test files
Additional examples and test files can be found in the [audiocue-demo](https://github.com/philfrei/audiocue-demo) project.
