package dev.mainardes.cache;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.sun.nio.zipfs.ZipFileSystem;

public final class Cache implements Runnable {

	private static final long MAX_LIFETIME = 43_200_000;
	
	private static final Map<String, String> ZIP_OPTIONS = new HashMap<>();
	
	
	private final Path location;
	private ZipFileSystem zip;
	
	public Cache(Path location) throws IOException {
		this.location = location.resolve("cache.zip");
		
		zip = (ZipFileSystem)FileSystems.newFileSystem(URI.create("jar:" + this.location.toUri()), ZIP_OPTIONS, null);
		Runtime.getRuntime().addShutdownHook(new Thread(this, "Cache@" + hashCode()));
		
		System.out.println("Creating cache at \"" + this.location + "\"...");
		
	}
	
	@Override
	public void run() {
		
		System.out.println("Closing cache...");
		try {
			zip.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public Path getLocation() {
		return location;
	}
	
	public <K, T> T get(K key, EntryCreator<K, T> creator) throws IOException{
		
		String extension = creator.getExtension(),
			   filename = UUID.nameUUIDFromBytes(creator.name(key).getBytes()) + "." + extension;
		
		Path path = getInternalPath(filename);
		
		if (has(filename)) {
			
			FileTime time = Files.getLastModifiedTime(path);
			
			if (isLiving(path, time.toMillis(), creator.getLifetime())) {
				InputStream input = Files.newInputStream(path);
				T object = creator.get(input);
				input.close();
				
				return object;
			}
						
		}
		
		OutputStream output = Files.newOutputStream(path);
		T object = creator.create(key, output);
		
		if (object != null) Files.setLastModifiedTime(path, FileTime.from(Instant.now()));
		
		return object;

	}
	
	private Path getInternalPath(String filename) {
		return zip.getPath("/", filename);
	}
	
	private boolean has(String filename) {
		Path path = zip.getPath("/", filename);
		return Files.exists(path);
	}
	
	private boolean isLiving(Path path, final long timestamp, final long maxLifetime) throws IOException {
		if ((System.currentTimeMillis() - timestamp) < (maxLifetime < 0 ? MAX_LIFETIME : maxLifetime)) return true;
		Files.delete(path);
		return false;
	}
	
	static {
		ZIP_OPTIONS.put("create", "true");
		ZIP_OPTIONS.put("useTempFile", "true");
	}
	
}
