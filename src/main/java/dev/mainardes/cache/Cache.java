package dev.mainardes.cache;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.mainardes.cache.impl.SerializableCreator;
import javafx.application.Platform;
import javafx.beans.property.Property;

public final class Cache implements Runnable {

	private static final long MAX_LIFETIME = 43_200_000;
	
	private static final Map<String, String> ZIP_OPTIONS = new HashMap<>();
	
	private static final DefaultObjectCreator DEFAULT_CREATOR = new DefaultObjectCreator();
	
	private static final ExecutorService POOL = Executors.newFixedThreadPool(4);
	
	private final Path location;
	private FileSystem zip;
	
	private final WeakHashMap<String, Object> objects = new WeakHashMap<>();
	
	public Cache(Path location) throws IOException {
		this.location = location.resolve("cache.zip");
		zip = FileSystems.newFileSystem(URI.create("jar:" + this.location.toUri()), ZIP_OPTIONS, null);
		Runtime.getRuntime().addShutdownHook(new Thread(this, "Cache@" + hashCode()));
		System.out.println("Creating cache at \"" + this.location + "\"...");
	}

	public <T, K> void set(K key, EntryCreator<K, T> creator, Property<T> property) {
		set(key, creator, property, true);
	}
	
	public <T, K> void set(K key, EntryCreator<K, T> creator, Property<T> property, boolean onFxThread) {
		POOL.submit(new CacheTask<>(key, onFxThread, creator, property));
	}
	
	public synchronized <T extends Serializable> void store(String key, T object) throws IOException{
		if (key != null && object!= null) {
			String filename = getFilename(key, DEFAULT_CREATOR);
			Path path = getEntryPath(key, DEFAULT_CREATOR, filename);
			Files.deleteIfExists(path);
			
			DEFAULT_CREATOR.object = object;
			
			put(path, key, DEFAULT_CREATOR, filename);
		}
	}
	
	public synchronized <T extends Serializable> T get(String key) throws IOException, ClassCastException {
		Serializable object = get(key, DEFAULT_CREATOR);
		
		@SuppressWarnings("unchecked")
		Class<T> type = (Class<T>)object.getClass(); //This is NOT typesafe, it'll cast no matter what. The result class may not be the expected one.
		
		return type.cast(object);
	}
	
	public <K, T> T get(K key, EntryCreator<K, T> creator) throws IOException{
		String filename = getFilename(key, creator);
		
		Object storedObject = objects.get(filename);
		if (storedObject != null) {
			Class<T> type = creator.getContentType();
			if (type.isAssignableFrom(storedObject.getClass())) return type.cast(storedObject);
		}
		
		Path path = getEntryPath(key, creator, filename);
		
		T object;
		
		if (Files.exists(path)) {
			
			FileTime time = Files.getLastModifiedTime(path);
			
			if (isLiving(path, time.toMillis(), creator.getLifetime())) {
				InputStream input = Files.newInputStream(path);
				object = creator.get(input);
				input.close();
				
				return object;
			}
						
		}
		
		object = put(path, key, creator, filename);
		
		return object;
	}
	
	private <T, K> T put(Path path, K key, EntryCreator<K, T> creator, String filename) throws IOException {
		OutputStream output = Files.newOutputStream(path);
		T object = creator.create(key, output);
		
		output.close();
		
		if (object != null) {
			Files.setLastModifiedTime(path, FileTime.from(Instant.now()));
			objects.put(filename, object);
		} else objects.remove(filename);
		
		return object;
		
	}
	
	@Override
	public void run() {
		
		try {
			zip.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public Path getLocation() {
		return location;
	}
	
	private <K> String getFilename(K key, EntryCreator<K, ?> creator) {
		return UUID.nameUUIDFromBytes(creator.name(key).getBytes()) + "." + creator.getExtension();
	}
	
	private <K> Path getEntryPath(K key, EntryCreator<K, ?> creator, String filename) {
		return zip.getPath(filename);
	}
	
	private boolean isLiving(Path path, final long timestamp, final long maxLifetime) throws IOException {
		if ((System.currentTimeMillis() - timestamp) < (maxLifetime < 0 ? MAX_LIFETIME : maxLifetime)) return true;
		Files.delete(path);
		return false;
	}
	
	private class CacheTask<K, T> implements Runnable {
		
		private K key;
		private boolean onFxThread;
		private EntryCreator<K, T> creator;	
		
		private Property<T> property;
		
		private T object;
		
		public CacheTask(K key, boolean onFxThread, EntryCreator<K, T> creator, Property<T> property) {
			this.key = key;
			this.onFxThread = onFxThread;
			this.creator = creator;
			this.property = property;
		}

		@Override
		public void run() {
			
			if (Platform.isFxApplicationThread()) {
				property.setValue(object);
				return;
			}
			
			try {
				object = get(key, creator);
				
				if (onFxThread) {
					Platform.runLater(this);
					return;
				} else {
					property.setValue(object);
					object = null;
				}
				
			} catch (IOException e) {
				e.printStackTrace();
			}
			
		}
		
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
