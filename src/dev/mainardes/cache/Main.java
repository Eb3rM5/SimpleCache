package dev.mainardes.cache;

import java.nio.file.Paths;
import java.util.ArrayList;

public class Main {

	public static void main(String[] args) throws Exception {
		Cache cache = new Cache(Paths.get("D:\\Cache\\"));
		
		cache.store("teste", new ArrayList<>());
		
		String teste = cache.get("teste");
		System.out.println(teste);
	}

}
