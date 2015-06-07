/**
 * References: 
 * 		FPL Structure: https://github.com/tfriedel/foobar2000-export
 * 
 * TODO Add M3U(8), FPL, CUE loading support
 * TODO CLI Interface
 * TODO Copy only the proper files to directory (actual sync)
 *
 */

package doylee.plsyncer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PlaylistSyncer {
	public static void main(String[] args) throws IOException {
		if (args.length == 2) {
			System.out.println("PlaylistSyncer CLI");
			System.out.println("https://github.com/Doy-lee/PlaylistSyncer\n");
			BufferedReader br = null;
	        try {
	        	//Need to explicitly specify char-encoding for non-default system encoded files
	        	//Wrapped in InputStreamReader to supply encoding, wrapped in buffered for read line func.
	        	br = new BufferedReader(new InputStreamReader(new FileInputStream(args[0]), "UTF-8"));
				String line;

				//Check first line for header
				if ((line = br.readLine()) != null) {
					line.trim();
					if (line.equals("#EXTM3U"))	
						System.out.println("Extended M3U detected");
					else
						System.out.println("Normal M3U detected");
				}

				Path to = Paths.get(args[1]);
				System.out.println("Target Dir: " + to.toAbsolutePath());

				//Read contents out
				while ((line = br.readLine()) != null) {
					line.trim();
					Path from = Paths.get(line);
					if (from.toFile().exists()) {
						if (!from.toFile().canRead()) {
							System.err.println
								("Insufficient read permission: " + from.toAbsolutePath());
						} else {
							System.out.println(from.toAbsolutePath());
							Files.createDirectories(to);
							Files.copy(from, to.resolve(from.getFileName()), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);  
						}
					} else {
						System.err.println("File not found error: " + line);
					}
				}

			} finally {
				if (br != null) {
					br.close();
				}
			}
			System.out.print("\n");
		} else {
			System.out.println("Usage: java doylee.plsyncer.PlaylistSyncer <m3u file> <target dir>");
		}
	}
}
