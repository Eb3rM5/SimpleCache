package dev.mainardes.cache;

import dev.mainardes.cache.impl.SerializableCreator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
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
	
	private static final DefaultObjectCreator DEFAULT_CREATOR = new DefaultObjectCreator();
	
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
	
	public synchronized <T extends Serializable> void store(String key, T object) throws IOException{
		if (key != null && object!= null) {
			Path path = getEntryPath(key, DEFAULT_CREATOR);
			Files.deleteIfExists(path);
			
			DEFAULT_CREATOR.object = object;
			
			put(path, key, DEFAULT_CREATOR);
		}
	}
	
	public synchronized <T extends Serializable> T get(String key) throws IOException {
		try {
			Serializable object = get(key, DEFAULT_CREATOR);
			
			@SuppressWarnings("unchecked")
			Class<T> type = (Class<T>)object.getClass();
			
			return type.isAssignableFrom(object.getClass()) ? type.cast(object) : null;
		} catch (ClassCastException e) {
			return null;
		}
	}
	
	public <K, T> T get(K key, EntryCreator<K, T> creator) throws IOException{
		
		Path path = getEntryPath(key, creator);
		
		if (Files.exists(path)) {
			
			FileTime time = Files.getLastModifiedTime(path);
			
			if (isLiving(path, time.toMillis(), creator.getLifetime())) {
				InputStream input = Files.newInputStream(path);
				T object = creator.get(input);
				input.close();
				
				return object;
			}
						
		}
		
		return put(path, key, creator);

	}
	
	private static <T, K> T put(Path path, K key, EntryCreator<K, T> creator) throws IOException {
		
		OutputStream output = Files.newOutputStream(path);
		T object = creator.create(key, output);
		
		if (object != null) Files.setLastModifiedTime(path, FileTime.from(Instant.now()));
		
		return object;
		
	}
	
	private <K> Path getEntryPath(K key, EntryCreator<K, ?> creator) {
		String filename = UUID.nameUUIDFromBytes(creator.name(key).getBytes()) + "." + creator.getExtension();
		return zip.getPath("/", filename);
	}
	
	private boolean isLiving(Path path, final long timestamp, final long maxLifetime) throws IOException {
		if ((System.currentTimeMillis() - timestamp) < (maxLifetime < 0 ? MAX_LIFETIME : maxLifetime)) return true;
		Files.delete(path);
		return false;
	}
	
	private static final class DefaultObjectCreator implements SerializableCreator<String, Serializable> {

		private Serializable object;
		
		@Override
		public String name(String key) {
			return key;
		}

		@Override
		public String getExtension() {
			return "object";
		}

		@Override
		public Serializable create(String key) {
			return object;
		}
		
	}
	
	static {
		ZIP_OPTIONS.put("create", "true");
		ZIP_OPTIONS.put("useTempFile", "true");
	}
	
}
