package dev.mainardes.cache;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.ParameterizedType;

public interface EntryCreator<K, T> {
	
	public T create(K key, OutputStream output);
	
	public T get(InputStream input);
	
	public String name(K key);
	
	public String getExtension();
	
	public default Class<T> getContentType(){
		
		@SuppressWarnings("unchecked")
		Class<T> type = (Class<T>)((ParameterizedType)getClass().getGenericInterfaces()[0]).getActualTypeArguments()[1];
		
		return type;
	}
	
	public default long getLifetime() {
		return -1;
	}
}