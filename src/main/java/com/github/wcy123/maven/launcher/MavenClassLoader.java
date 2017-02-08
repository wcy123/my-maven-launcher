package com.github.wcy123.maven.launcher;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
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
    private final ClassLoader[] parents;
    private final String name;

    private MavenClassLoader(DependencyNode node, ArtifactResolver artifactResolver,
            ProjectBuildingRequest buildRequest) throws IOException, ArtifactResolverException {
        super(myGuessUrls(node, artifactResolver, buildRequest));
        final List<DependencyNode> nodes = node.getChildren();
        parents = new ClassLoader[nodes.size() + 1];
        parents[0] = getParent();
        for (int i = 1; i < nodes.size() + 1; ++i) {
            final DependencyNode n = nodes.get(i - 1);
            parents[i] = new MavenClassLoader(n, artifactResolver, buildRequest);
        }
        name = toKey(node);
    }

    public static MavenClassLoader create(DependencyNode node, ArtifactResolver artifactResolver,
            ProjectBuildingRequest buildRequest) throws IOException, ArtifactResolverException {
        String key = toKey(node);
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        final MavenClassLoader value = new MavenClassLoader(node, artifactResolver, buildRequest);
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

    public File getFile(DependencyNode node) {
        return cacheFile.get(toKey(node));
    }

    @Override
    public Class<?> loadClass(String name, boolean resolved) throws ClassNotFoundException {
        for (ClassLoader parent : parents) {
            try {
                final Class<?> aClass = parent.loadClass(name);
                if (parent instanceof MavenClassLoader) {
                    MavenClassLoader cl = (MavenClassLoader) parent;
                    System.out.println("loaded class " + name + " by " + cacheFile.get(cl.name)
                            + "@" + parent);
                    System.out.println("classLoader is " + aClass.getClassLoader());
                } else {
                    MavenClassLoader cl = (MavenClassLoader) parent;
                    System.out.println(
                            "loaded class " + name + " by other class loader " + "@" + parent);
                }
                return aClass;
            } catch (ClassNotFoundException ex) {
                if (parent instanceof MavenClassLoader) {
                    MavenClassLoader cl = (MavenClassLoader) parent;
                    System.out.println("cannot find class " + name + " by " + cacheFile.get(cl.name)
                            + "@" + cl);
                } else {
                    System.out.println(
                            "cannot find class " + name + " by other class loader " + "@" + parent);
                }
                // do not matter.
            }
        }
        try {
            final Class<?> aClass = super.loadClass(name, resolved);
            System.out.println(
                    "!loaded class " + name + " by " + cacheFile.get(this.name) + "@" + this);
            System.out.println("classLoader is " + aClass.getClassLoader());
            return aClass;
        } catch (ClassNotFoundException ex) {
            System.out.println("!cannot find class " + name + " by " + cacheFile.get(this.name)
                    + "@" + this);
            throw ex;
        }
    }
}
