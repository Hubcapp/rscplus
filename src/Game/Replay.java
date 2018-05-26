/**
 *	rscplus
 *
 *	This file is part of rscplus.
 *
 *	rscplus is free software: you can redistribute it and/or modify
 *	it under the terms of the GNU General Public License as published by
 *	the Free Software Foundation, either version 3 of the License, or
 *	(at your option) any later version.
 *
 *	rscplus is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *	GNU General Public License for more details.
 *
 *	You should have received a copy of the GNU General Public License
 *	along with rscplus.  If not, see <http://www.gnu.org/licenses/>.
 *
 *	Authors: see <https://github.com/OrN/rscplus>
 */

package Game;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import Client.Logger;
import Client.Settings;
import Client.Util;

public class Replay {
	static DataOutputStream output = null;
	static DataOutputStream input = null;
	static DataOutputStream keys = null;
	
	static DataInputStream play_keys = null;
	
	public static boolean isPlaying = false;
	public static boolean isRecording = false;
	public static boolean paused = false;
	
	public static int fps = 50;
	public static float fpsPlayMultiplier = 1.0f;
	public static int frame_time_slice;
	
	public static ReplayServer replayServer = null;
	public static Thread replayThread = null;
	
	public static int timestamp;
	
	public static void incrementTimestamp() {
		timestamp++;
	}
	
	public static void initializeReplayPlayback(String replayDirectory) {
		if (Client.username_login.length() == 0)
			Client.username_login = "Replay";
		
		try {
			play_keys = new DataInputStream(new FileInputStream(new File(replayDirectory + "/keys.bin")));
			
			timestamp = 0;
		} catch (Exception e) {
			play_keys = null;
			Logger.Error("Failed to initialize replay playback");
			return;
		}
		Game.getInstance().getJConfig().changeWorld(6);
		replayServer = new ReplayServer(replayDirectory);
		replayThread = new Thread(replayServer);
		replayThread.start();
		isPlaying = true;
		Logger.Info("Replay playback started");
	}
	
	public static void closeReplayPlayback() {
		if (play_keys == null)
			return;
		
		try {
			play_keys.close();
			
			play_keys = null;
		} catch (Exception e) {
			play_keys = null;
		}
		
		Game.getInstance().getJConfig().changeWorld(Settings.WORLD);
		isPlaying = false;
		Logger.Info("Replay playback stopped");
	}
	
	public static void initializeReplayRecording() {
		// No username specified, exit
		if (Client.username_login.length() == 0)
			return;
		
		String timeStamp = new SimpleDateFormat("MM-dd-yyyy HH.mm.ss").format(new Date());
		
		String recordingDirectory = Settings.Dir.REPLAY + "/" + Client.username_login;
		Util.makeDirectory(recordingDirectory);
		recordingDirectory = recordingDirectory + "/" + timeStamp;
		Util.makeDirectory(recordingDirectory);
		
		try {
			output = new DataOutputStream(new FileOutputStream(new File(recordingDirectory + "/out.bin")));
			input = new DataOutputStream(new FileOutputStream(new File(recordingDirectory + "/in.bin")));
			keys = new DataOutputStream(new FileOutputStream(new File(recordingDirectory + "/keys.bin")));
			timestamp = 0;
			
			Logger.Info("Replay recording started");
		} catch (Exception e) {
			output = null;
			input = null;
			keys = null;
			Logger.Error("Unable to create replay files");
			return;
		}
		
		isRecording = true;
	}
	
	public static void closeReplayRecording() {
		if (input == null)
			return;
		
		try {
			output.close();
			input.close();
			keys.close();
			
			output = null;
			input = null;
			keys = null;
			
			Logger.Info("Replay recording stopped");
		} catch (Exception e) {
			// output = null;
			input = null;
			keys = null;
			Logger.Error("Unable to close replay files");
			return;
		}
		
		isRecording = false;
	}
	
	public static void togglePause() {
		paused = !paused;
	}
	
	public static boolean isValid(String path) {
		return (new File(path + "/in.bin").exists() && new File(path + "/keys.bin").exists());
	}
	
	// adjusts frame time slice
	public static int getFrameTimeSlice() {
		if (isPlaying) {
			frame_time_slice = 1000 / ((int)(fps * fpsPlayMultiplier));
			return frame_time_slice;
		}
		
		return 1000 / fps;
	}
		
	public static int getFPS() {
		
		if (isPlaying) {
			return (int)(fps * fpsPlayMultiplier);
		}
		
		return fps;
	}
	
	public static void dumpRawInputStream(byte[] b, int n, int n2, int n5, int bytesread) {
		if (input == null)
			return;
		
		int off = n2 + n5;
		
		try {
			input.writeInt(timestamp);
			input.writeInt(bytesread);
			input.write(b, off, bytesread);
		} catch (Exception e) {
		}
	}
	
	public static void dumpRawOutputStream(byte[] b, int off, int len) {
		if (output == null)
			return;
		
		try {
			boolean isLogin = false;
			int pos = -1;
			byte[] out_b = null;
			// for the first bytes if byte == (byte)Client.version, 4 bytes before indicate if its
			// login or reconnect and 5 its what determines if its login-related
			for (int i = off + 5; i < off + Math.min(15, len); i++) {
				if (b[i] == (byte)Client.version && b[i - 5] == 0) {
					isLogin = true;
					pos = i + 1;
					out_b = b.clone();
					break;
				}
			}
			if (isLogin && pos != -1) {
				for (int i = pos; i < off + len; i++) {
					out_b[i] = 0;
				}
				
				output.writeInt(timestamp);
				output.writeInt(len);
				output.write(out_b, off, len);
				return;
			}
			
			output.writeInt(timestamp);
			output.writeInt(len);
			output.write(b, off, len);
		} catch (Exception e) {
		}
	}
	
	public static int hookXTEAKey(int key) {
		if (play_keys != null) {
			try {
				return play_keys.readInt();
			} catch (Exception e) {
				return key;
			}
		}
		
		if (keys == null)
			return key;
		
		try {
			keys.writeInt(key); // data length
		} catch (Exception e) {
		}
		
		return key;
	}
}