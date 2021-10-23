package dev.mainardes.cache.impl;

import dev.mainardes.cache.EntryCreator;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;

public interface SerializableCreator<K, T extends Serializable> extends EntryCreator<K, T> {

	public T create(K key);
	
	public default T create(K key, OutputStream output) {
		T object = create(key);
		
		if (object != null) {
			
			try {
				ObjectOutputStream out = new ObjectOutputStream(output);
				out.writeObject(object);
				
				out.close();
				return object;
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
			
		}
		
		return null;
	}

	public default T get(InputStream input) {
		
		try {
			ObjectInputStream in = new ObjectInputStream(input);
			Object obj = in.readObject();
			
			Class<T> type = getContentType();
			if (type.isAssignableFrom(obj.getClass())) return type.cast(obj);
			
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
			return null;
		}
		
		
		return null;
	}
	
	
}
