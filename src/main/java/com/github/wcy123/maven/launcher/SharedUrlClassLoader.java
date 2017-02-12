package com.github.wcy123.maven.launcher;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SharedUrlClassLoader extends URLClassLoader {
    private final static Map<String, SharedUrlClassLoader> cache = new ConcurrentHashMap<>();
    private final static Map<String, URL> cacheFile = new ConcurrentHashMap<>();
    private final static Map<String, CL> cacheClassLoader = new ConcurrentHashMap<>();
    private final Set<SharedUrlClassLoader> collections;
    private final String key;

    private SharedUrlClassLoader(Set<SharedUrlClassLoader> collections, String key, URL url)
            throws MalformedURLException {
        super(myGuessUrls(key, url));
        this.collections = collections;
        this.key = key;
    }

    public static SharedUrlClassLoader create(URL urls[]) throws IOException {
        if (urls.length == 0) {
            throw new IllegalArgumentException("URL length is zero");
        }
        Set<SharedUrlClassLoader> collections = new HashSet<>();
        SharedUrlClassLoader ret = null;
        for (int i = 0; i < urls.length; ++i) {
            final URL url = urls[i];
            final String key = url.toString();
            if (!cache.containsKey(key)) {
                cache.put(key, new SharedUrlClassLoader(collections, key, url));
            }
            final SharedUrlClassLoader value = cache.get(key);
            collections.add(value);
            if (ret == null) {
                ret = value;
            }
        }
        return ret;
    }

    public static URL getURL(String key) {
        return cacheFile.get(key);
    }

    private static URL[] myGuessUrls(String key, URL url) throws MalformedURLException {
        cacheFile.put(key, url);
        return new URL[] {url};
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        for (SharedUrlClassLoader cl : collections) {
            try {
                return cl.superFindClass1(name);
            } catch (ClassNotFoundException ex) {
                // it is OK
            }
        }
        throw new ClassNotFoundException(name);
    }

    @Override
    public URL findResource(String name) {
        for (SharedUrlClassLoader cl : collections) {
            final URL url = cl.superFindResource(name);
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    @Override
    public Enumeration<URL> findResources(String name) throws IOException {
        Enumeration<URL> ret = null;
        for (SharedUrlClassLoader cl : collections) {
            if (ret == null) {
                ret = cl.superFindResources(name);
            } else {
                ret = Utils.concat(ret, cl.superFindResources(name));
            }
        }
        return ret;
    }

    public Enumeration<URL> superFindResources(String name) throws IOException {
        return super.findResources(name);
    }

    private URL superFindResource(String name) {
        return super.findResource(name);
    }

    private Class<?> superFindClass1(String name) throws ClassNotFoundException {
        try {
            if (cacheClassLoader.containsKey(name)) {
                return cacheClassLoader.get(name).getClazz();
            }
            final Class<?> aClass = super.findClass(name);
            // System.out.println("loaded class " + name + " by " + id());
            // System.out.println("classLoader is " + aClass.getClassLoader());
            cacheClassLoader.put(name, new CL(this, aClass));
            return aClass;
        } catch (ClassNotFoundException ex) {
            // System.out.println("cannot find class " + name + " by " + id());
            // do not matter.
            throw ex;
        }
    }

    private String id() {
        return getFileName() + "@" + this;
    }

    public URL getFileName() {
        return getURL(this.key);
    }

    private static class CL {
        private final SharedUrlClassLoader loader;
        private final Class<?> clazz;

        private CL(SharedUrlClassLoader loader, Class<?> clazz) {
            this.loader = loader;
            this.clazz = clazz;
        }

        public SharedUrlClassLoader getLoader() {
            return loader;
        }

        public Class<?> getClazz() {
            return clazz;
        }
    }
}
