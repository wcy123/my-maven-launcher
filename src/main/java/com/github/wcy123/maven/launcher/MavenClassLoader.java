package com.github.wcy123.maven.launcher;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.artifact.resolve.ArtifactResolverException;
import org.apache.maven.shared.dependency.graph.DependencyNode;

public class MavenClassLoader extends URLClassLoader {
    private final static Map<String, MavenClassLoader> cache = new ConcurrentHashMap<>();
    private final static Map<String, File> cacheFile = new ConcurrentHashMap<>();
    private final MavenClassLoader[] collections;
    private final String key;

    private MavenClassLoader(DependencyNode node,
            ArtifactResolver artifactResolver,
            ProjectBuildingRequest buildRequest,
            MavenClassLoader[] collections) throws IOException, ArtifactResolverException {
        super(myGuessUrls(node, artifactResolver, buildRequest));
        this.collections = collections;
        key = toKey(node);
    }

    public static MavenClassLoader create(DependencyNode node, ArtifactResolver artifactResolver,
            ProjectBuildingRequest buildRequest, MavenClassLoader[] collections)
            throws IOException, ArtifactResolverException {
        String key = toKey(node);
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        final MavenClassLoader value =
                new MavenClassLoader(node, artifactResolver, buildRequest, collections);
        cache.put(key, value);
        return value;
    }

    private static String toKey(DependencyNode node) {
        return toKey(node.getArtifact());
    }

    private static String toKey(Artifact artifact) {
        return String.join(":", artifact.getGroupId(), artifact.getArtifactId(),
                artifact.getVersion());
    }

    private static URL[] myGuessUrls(DependencyNode node, ArtifactResolver artifactResolver,
            ProjectBuildingRequest buildRequest) throws IOException, ArtifactResolverException {
        Artifact mavenNode = node.getArtifact();
        if (!mavenNode.isResolved()) {
            mavenNode = artifactResolver.resolveArtifact(buildRequest, mavenNode).getArtifact();
        }
        final File file = mavenNode.getFile();
        cacheFile.put(toKey(node), file);
        return new URL[] {file.toURI().toURL()};
    }

    public void print(PrintStream out, int indentLevel) {
        out.println(indent(indentLevel) + id());
        for (MavenClassLoader mavenNode : collections) {
            mavenNode.print(out, indentLevel + 1);
        }
    }

    public String id() {
        return getFileName() + "@" + this;
    }

    private String indent(int indent) {
        final char[] chars = new char[indent * 4];
        for (int i = 0; i < chars.length; ++i) {
            chars[i] = ' ';
        }
        return new String(chars);
    }

    public File getFile() {
        return cacheFile.get(key);
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        for (MavenClassLoader cl : collections) {
            return cl.superFindClass(name);
        }
        throw new ClassNotFoundException(name);
    }

    private Class<?> superFindClass(String name) throws ClassNotFoundException {
        try {
            final Class<?> aClass = super.findClass(name);
            System.out.println("loaded class " + name + " by " + getFileName()
                    + "@" + this);
            System.out.println("classLoader is " + aClass.getClassLoader());
            return aClass;
        } catch (ClassNotFoundException ex) {
            System.out.println("cannot find class " + name + " by " + getFileName()
                    + "@" + this);
            // do not matter.
            throw ex;
        }
    }

    private File getFileName() {
        return cacheFile.get(this.key);
    }
}
