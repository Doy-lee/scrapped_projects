/**
 * References: 
 * 		FPL Structure: https://github.com/tfriedel/foobar2000-export
 * 
 * TODO Add M3U(8), FPL, CUE loading support
 * TODO CLI Interface
 *
 */

package doylee.plsyncer;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;


public class PlaylistSyncer {
	public static void main(String[] args) throws IOException {
		if (args.length == 1) {
			System.out.println("test");
	        try {
	            BufferedReader br = new BufferedReader(new FileReader(args[0]));

	            int c;
	            while ((c = inputStream.read()) != -1) {
	                System.out.println((char) c);
	            }
	        } finally {
	            if (inputStream != null) {
	                inputStream.close();
	            }
			}
		} else {
			System.out.println("Usage: java doylee.plsyncer.PlaylistSyncer <m3u file>");
		}
	}
}
