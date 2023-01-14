# AudioCue-maven
*AudioCue* is a Java audio-playback class, modeled on 
[javax.sound.sampled.Clip](https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/javax/sound/sampled/Clip.html), 
enhanced with concurrent playback capabilities and with dynamic handling of volume, 
pan and frequency. Included in the library is an audio mixing tool for merging the output
of multiple *AudioCues* into a single line out.

The project is now [published on *Maven Central*](https://search.maven.org/artifact/com.adonax/audiocue/2.0.0/jar).
Unit tests have been added, and more rigorous API documentation has been written. Over the course of making these changes, some errors 
and shortcomings were uncovered and have been corrected. The changes are extensive enough that this project 
will have a starting release version of 2.0.0. The [previous version](https://github.com/philfrei/AudioCue)
will no longer be maintained. 

The best way to make use of the **AudioCue** class is use the Maven build tool, and list *AudioCue* as a
dependency. To use *AudioCue* as a Maven dependency, add the following to your project's POM file.

```xml
    <dependencies>  
        <dependency>
            <groupId>com.adonax</groupId>
            <artifactId>audiocue</artifactId>
            <version>2.0.0</version>
        </dependency>
    </dependencies>
```

When used in this way, source code and Javadocs documentation will automatically be linked.

Another option is to fork this project and clone it to a development environment. From there, executing 
Maven's *install* command will put the library into the local Maven repository. Or, you can directly make 
use of the jar file that is created in the **/target** subdirectory via the *package* command by adding 
this jar file to your project's classpath. Also, since there are only a few files, you can consider 
simply copying these files directly into your project. Just be sure, if you do, to edit the *package* 
lines of the files to appropriately reflect their new file locations.

The library jar, as well as source and documentation jars are publicly available at
the Maven site for downloading if you do not wish to use Maven as the build tool.

### Basic playback (for "fire-and-forget" use)

Code fragments:

```java
    // Recommended: define as an instance variable.
    AudioCue myAudioCue; 

//////
    // Recommended: preload one time only.
    // This example assumes "myAudio.wav" is located in src/main/resources folder
    // of a Maven project.    
    URL url = this.getClass().getResource("/myAudio.wav");
    // The following allows up to 4 concurrent playbacks.
    AudioCue myAudioCue = AudioCue.makeStereoCue(url, 4); 
    myAudioCue.open();

//////
    // Recommended: the play() method should be called on a preloaded 
    // instance variable. Reloading or reopening prior for successive plays
    // will add processing latency to no practical purpose.
    myAudioCue.play();  
    // See API for parameters for vol, pan, speed and number of repetitions.
    // NOTE: The play method returns immediately. Playback occurs on a
    // daemon thread, and will not hold a program open. 

//////
    // To release resources when sound is no longer needed
    myAudioCue.close();
```

The following example plays an *AudioCue* via a Swing Button.

```java
	public class AudioCuePlayExample {
		public static void main(String[] args) {
			EventQueue.invokeLater(new Runnable(){
				public void run()
				{	
					DemoFrame frame = new DemoFrame();
					frame.pack;
					frame.setVisible(true);
				}
			});
		}
	}
    
	class DemoFrame extends JFrame {
		private AudioCue bell;
		public DemoFrame() {
			JPanel panel = new JPanel();
			JButton button = new JButton("Play sound");
			button.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					bell.play();
				}
			});
			panel.add(button);
			add(panel);
    
			// Set up the AudioCue
			URL url = this.getClass().getResource("/bell.wav");
			try {
				bell = AudioCue.makeStereoCue(url, 6);
				bell.open();
			} catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
				e.printStackTrace();
			}
		}
	}
```




### Usage: dynamic controls

To dynamically change the playback of a sound, we need to identify
the playback *instance*. Each playback instance has its own identifier,
an `int` ranging from 0 to the number of  concurrent instances minus one.
Identifiers are obtained from a pool of available instances. An instance is reserved
and its identifier is returned when the play method is executed:

    int instanceID = myAudioCue.play(); 

One can also reserve an *instance* and obtain its identifier from the pool of available 
instances as follows:

    int instanceID = myAudioCue.obtainInstance(); 

An *instance* can be started, stopped, or released by including the instance identifier 
as an argument:


```java
    myAudioCue.start(instanceID);
    myAudioCue.stop(instanceID);
    myAudioCue.releaseInstance(instanceID);
```

*Releasing* returns the instance identifier to the pool of available instances.

An important distinction exists between instances obtained from one of the 
`play` methods versus the `obtainInstance()` method.
The default value of the property `recycleWhenDone` for an instance 
obtained from `play()` is `true`. Thus, when playback completes, the instance
and its identifier will be returned to the pool of available instances.

An instance arising from `obtainInstance()` has the property `recycleWhenDone`
set to `false`. In this case, when playback completes, the instance identifier
is *not* returned to the pool of available instances, and the instance remains 
receptive to additional commands. In this case, after playing through to the
end, the frame position will be that of the end of the audio-data file, and,
like a `Clip` that has played through, will require repositioning and restarting
for further plays.

Properties that can be altered for an instance include the following:

```java
    //*volume*: 
    myAudioCue.setVolume(instanceID, value); // double ranging from 0 (silent)
                                             // to 1 (full volume)
    //*panning*: 
    myAudioCue.setPan(instanceID, value); // double ranging from -1 (full left)
                                          // to 1 (full right)
    //*speed of playback*: 
    myAudioCue.setSpeed(instanceID, value); // value is a factor that is  
                                  // multiplied against the normal playback rate, e.g.,
                                  // 2 will double playback speed, 0.5 will halve it 
    
    //*position* (cannot be changed while instance is playing):
    myAudioCue.setFramePosition(instanceID, value);  // position in frames
    myAudioCue.setMillisecondPosition(instanceID, value); // position in milliseconds
    myAudioCue.setsetFractionalPosition(instanceID, value); 
                                        // value is a decimal fraction of the cue length
                                        // where 0 = start, 1 = end
    //*other*:                                                
    myAudioCue.setLooping(handle, value); // number of additional times the cue will replay
                                          // -1 = infinite looping
    myAudioCue.setRecycleWhenDone(handle, value); // boolean that determines whether or 
                                        // not the instance will be returned to the
                                        // pool of available instances when the cue
                                        // plays to completion                                
```

### Usage: output configuration

Output configuration occurs within the *AudioCue*'s `open` methods. The 
default configuration employs *javax.sound.sampled.AudioSystem*'s default
`Mixer` and a `SourceDataLine` set with a 1024-frame buffer and the highest
thread priority. A high thread priority should not affect performance of 
the rest of an application, as audio threads normally spend the vast majority 
of their time in a blocked state. The buffer size can be set to 
optimize the balance between latency and dropouts. Incidents of dropouts 
are usually lessened by increasing the buffer size.

You can override the defaults by using an alternate form of the `open()` method. For 
example:

```java
    myAudioCue.open(mixer, bufferFrames, threadPriority);
```

Each `AudioCue` can have its own configuration, and will output on its own `SourceDataLine` line.  

### Usage: outputting via *AudioMixer*
Most operating systems will support multiple, concurrently playing `SourceDataLine` 
streams. But if desired, the streams can be merged into a single output line using 
`audiocue.AudioMixer`. This is accomplished by 
providing an `AudioMixer` instance as an argument to the `AudioCue` **open()** method: 

```java
    myAudioCue.open(myAudioMixer);
```

Internally, this method adds the `AudioCue` to the `AudioMixer` using the interface 
*AudioMixerTrack*. If an `AudioCue` is a track on an `AudioMixer`, the `AudioCue` 
method **close()** will automatically remove the track from the `AudioMixer`.

```java
    myAudioCue.close();
```

The `AudioMixer` is configured, upon instantiation, with a default 
`javax.sound.sampled.Mixer`, with a buffer size of 8192 frames, and 
with the highest thread priority. Alternate values can be provided 
at instantiation.

Any `AudioCue` routed through an `AudioMixer` will use the 
*AudioMixer*'s configuration properties. Requests to add or remove `AudioCues` 
from the `AudioMixer` are concurrency safe but with a latency equal to the time
that elapses until the buffer iterates.

In the following somewhat artificial example, we create and start an 
`AudioMixer`, add an `AudioCue` track, play the cue, then shut it all down.

```java
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
```

Reminder, this is an artificial example: best practice is to initialize and 
open an `AudioCue` only once, and to then reuse the `AudioCue` for multiple playbacks.
Also, this example uses `Thread.sleep()` to prevent the program from advancing and 
closing before the cue finishes playing. The reason for this is that `AudioCue` launches
a *daemon* thread internally for playback, so a playing instance will *not* prevent a 
program from closing once all the program instructions are completed. Unlike this example, 
an `AudioCue` is more typically called by a GUI that remains open, or from a long-running
program, and is able to complete playing without the program closing. In this more common 
use case, pausing the thread that has the *play()* method is quite unnecessary.  

### Usage: Additional examples and test files
Additional examples and test files can be found in the accompanying project 
[audiocue-demo](https://github.com/philfrei/audiocue-demo).

## Contribute to project
Please check the *Issues* area for entering or working on suggestions.

## Donate
If *AudioCue* has been helpful, it would be great to hear (by email or a 
"buy me a coffee/beer" donation).

<form action="https://www.paypal.com/donate" method="post" target="_top">
<input type="hidden" name="hosted_button_id" value="N7B6S36HEDLXG" />
<input type="image" src="https://www.paypalobjects.com/en_US/i/btn/btn_donateCC_LG.gif" border="0" name="submit" title="PayPal - The safer, easier way to pay online!" alt="Donate with PayPal button" />
<img alt="" border="0" src="https://www.paypal.com/en_US/i/scr/pixel.gif" width="1" height="1" />
</form>

## Contact Info

Programmer/Sound-Designer/Composer: Phil Freihofner

URL: http://adonax.com

Email: phil AT adonax.com

If using *StackOverflow* for a question, chances are highest
that I will see it if you include the tag *javasound*.

I'm happy to list links to applications that use *AudioCue*.
