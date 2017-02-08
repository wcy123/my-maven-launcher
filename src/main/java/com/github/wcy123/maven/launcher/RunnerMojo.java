package com.github.wcy123.maven.launcher;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.repository.LocalArtifactRepository;
import org.apache.maven.shared.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.artifact.resolve.ArtifactResolverException;
import org.apache.maven.shared.artifact.resolve.ArtifactResult;
import org.apache.maven.shared.dependencies.DefaultDependableCoordinate;
import org.apache.maven.shared.dependencies.resolve.DependencyResolver;
import org.apache.maven.shared.dependencies.resolve.DependencyResolverException;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.utils.StringUtils;

/**
 * Goal which launch a maven project
 *
 * goal run
 * 
 * @phase process-sources
 */
@Mojo(name = "run", requiresProject = false)
public class RunnerMojo
        extends AbstractMojo {
    private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile("(.+)::(.*)::(.+)");

    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    private MavenSession session;

    /**
     *
     */
    @Component
    private ArtifactResolver artifactResolver;

    /**
     *
     */
    @Component
    private DependencyResolver dependencyResolver;

    /**
     * Map that contains the layouts.
     */
    @Component(role = ArtifactRepositoryLayout.class)
    private Map<String, ArtifactRepositoryLayout> repositoryLayouts;

    /**
     * The dependency tree builder to use.
     */
    @Component(hint = "default")
    private DependencyGraphBuilder dependencyGraphBuilder;

    /**
     * The computed dependency tree root node of the Maven project.
     */
    private DependencyNode rootNode;
    @Component
    private ProjectBuilder projectBuilder;

    private DefaultDependableCoordinate coordinate = new DefaultDependableCoordinate();

    /**
     * The groupId of the artifact to download. Ignored if {@link #artifact} is used.
     */
    @Parameter(property = "groupId")
    private String groupId;

    /**
     * The artifactId of the artifact to download. Ignored if {@link #artifact} is used.
     */
    @Parameter(property = "artifactId")
    private String artifactId;

    /**
     * The version of the artifact to download. Ignored if {@link #artifact} is used.
     */
    @Parameter(property = "version")
    private String version;

    /**
     * The classifier of the artifact to download. Ignored if {@link #artifact} is used.
     *
     * @since 2.3
     */
    @Parameter(property = "classifier")
    private String classifier;

    /**
     * The packaging of the artifact to download. Ignored if {@link #artifact} is used.
     */
    @Parameter(property = "packaging", defaultValue = "jar")
    private String packaging = "jar";

    /**
     * Repositories in the format id::[layout]::url or just url, separated by comma. ie.
     * central::default::http://repo1.maven.apache.org/maven2,myrepo::::http://repo.acme.com,http://repo.acme2.com
     */
    @Parameter(property = "remoteRepositories")
    private String remoteRepositories;

    /**
     * A string of the form groupId:artifactId:version[:packaging[:classifier]].
     */
    @Parameter(property = "artifact")
    private String artifact;

    /**
     *
     */
    @Parameter(defaultValue = "${project.remoteArtifactRepositories}", readonly = true,
            required = true)
    private List<ArtifactRepository> pomRemoteRepositories;

    /**
     *
     */
    @Parameter
    private LocalArtifactRepository localArtifactRepository;

    /**
     * Skip plugin execution completely.
     *
     * @since 2.7
     */
    @Parameter(property = "mdep.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute()
            throws MojoExecutionException, MojoFailureException {
        if (isSkip()) {
            getLog().info("Skipping plugin execution");
            return;
        }

        if (coordinate.getArtifactId() == null && artifact == null) {
            throw new MojoFailureException("You must specify an artifact, "
                    + "e.g. -Dartifact=org.apache.maven.plugins:maven-downloader-plugin:1.0");
        }
        if (artifact != null) {
            String[] tokens = StringUtils.split(artifact, ":");
            if (tokens.length < 3 || tokens.length > 5) {
                throw new MojoFailureException(
                        "Invalid artifact, you must specify groupId:artifactId:version[:packaging[:classifier]] "
                                + artifact);
            }
            coordinate.setGroupId(tokens[0]);
            coordinate.setArtifactId(tokens[1]);
            coordinate.setVersion(tokens[2]);
            if (tokens.length >= 4) {
                coordinate.setType(tokens[3]);
            }
            if (tokens.length == 5) {
                coordinate.setClassifier(tokens[4]);
            }
        }

        ArtifactRepositoryPolicy always =
                new ArtifactRepositoryPolicy(true, ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS,
                        ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN);

        List<ArtifactRepository> repoList = new ArrayList<ArtifactRepository>();

        if (pomRemoteRepositories != null) {
            repoList.addAll(pomRemoteRepositories);
        }

        if (remoteRepositories != null) {
            // Use the same format as in the deploy plugin id::layout::url
            List<String> repos = Arrays.asList(StringUtils.split(remoteRepositories, ","));
            for (String repo : repos) {
                repoList.add(parseRepository(repo, always));
            }
        }

        try {
            ProjectBuildingRequest buildingRequest =
                    new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
            buildingRequest.setProject(session.getCurrentProject());
            buildingRequest.setRemoteRepositories(repoList);

            getLog().info("Resolving " + coordinate + " with transitive dependencies");

            // FIXME
            // artifactResolver.resolveArtifact( buildingRequest, coordinate );

            final Iterable<ArtifactResult> artifactResults =
                    dependencyResolver.resolveDependencies(buildingRequest, coordinate, null);

            // start from here
            /*
             * DefaultArtifactCoordinate artifactCoordinate = new DefaultArtifactCoordinate();
             * artifactCoordinate.setGroupId(coordinate.getGroupId());
             * artifactCoordinate.setArtifactId(coordinate.getArtifactId());
             * artifactCoordinate.setVersion(coordinate.getVersion());
             * artifactCoordinate.setExtension(coordinate.getVersion());
             * artifactCoordinate.setClassifier(coordinate.getClassifier());
             * //buildingRequest.setProject(null);
             */
            if (!artifactResults.iterator().hasNext()) {
                getLog().error("cannot find the first artifcat");
                return;
            }
            for (ArtifactResult artifactResult : artifactResults) {
                getLog().info("artifacts " + artifactResult.getArtifact());
                artifactResolver.resolveArtifact(buildingRequest, artifactResult.getArtifact());
            }
            final ArtifactResult artifactResult = artifactResults.iterator().next();
            // artifactResolver.resolveArtifact(buildingRequest, artifactCoordinate);

            buildingRequest.setProject(null);
            final ProjectBuildingResult projectBuildingResult =
                    projectBuilder.build(artifactResult.getArtifact(), buildingRequest);
            final MavenProject project = projectBuildingResult.getProject();
            buildingRequest.setProject(project);
            ArtifactFilter artifactFilter = null;
            rootNode = dependencyGraphBuilder.buildDependencyGraph(buildingRequest, artifactFilter);
            final MavenClassLoader classLoader =
                    MavenClassLoader.create(rootNode, artifactResolver, buildingRequest);
            final File file = classLoader.getFile(rootNode);
            final JarFile jarFile = new JarFile(file);
            final Manifest manifest = jarFile.getManifest();
            final String value = manifest.getMainAttributes().getValue("Main-Class");
            // final String charSequence = "cannot find Main-Class: " +
            // manifest.getMainAttributes().entrySet().stream().map(e -> e.getKey() + ":" +
            // e.getValue()).collect(Collectors.joining("\n"));
            if (value == null) {
                throw new MojoFailureException("cannot find Main-Class " + file);
            }
            final Class<?> aClass = classLoader.loadClass(value);
            final Method main = aClass.getMethod("main", String[].class);
            main.invoke(null, new Object[] {new String[] {"hello", "world"}});

        } catch (DependencyResolverException e) {
            throw new MojoExecutionException("Couldn't download artifact: " + e.getMessage(), e);
        } catch (ProjectBuildingException e) {
            getLog().error("cannot build in-memory project ", e);
            throw new MojoFailureException("cannot build in-memory project");
        } catch (DependencyGraphBuilderException e) {
            getLog().error("cannot resolve dependency", e);
            throw new MojoFailureException("cannot resolve dependency");
        } catch (IOException e) {
            getLog().error("cannot create class loader", e);
            throw new MojoFailureException("cannot create class loader");
        } catch (ArtifactResolverException e) {
            getLog().error("cannot resolve artifact", e);
            throw new MojoFailureException("cannot resolve artifact");
        } catch (ClassNotFoundException e) {
            getLog().error("cannot load Main-Class", e);
            throw new MojoFailureException("cannot load Main-Class");
        } catch (NoSuchMethodException e) {
            getLog().error("cannot get main method", e);
            throw new MojoFailureException("cannot get main method");
        } catch (IllegalAccessException | InvocationTargetException e) {
            getLog().error("cannot invoke main method", e);
            throw new MojoFailureException("cannot invoke main method");
        }
    }

    ArtifactRepository parseRepository(String repo, ArtifactRepositoryPolicy policy)
            throws MojoFailureException {
        // if it's a simple url
        String id = "temp";
        ArtifactRepositoryLayout layout = getLayout("default");
        String url = repo;

        // if it's an extended repo URL of the form id::layout::url
        if (repo.contains("::")) {
            Matcher matcher = ALT_REPO_SYNTAX_PATTERN.matcher(repo);
            if (!matcher.matches()) {
                throw new MojoFailureException(repo, "Invalid syntax for repository: " + repo,
                        "Invalid syntax for repository. Use \"id::layout::url\" or \"URL\".");
            }

            id = matcher.group(1).trim();
            if (!StringUtils.isEmpty(matcher.group(2))) {
                layout = getLayout(matcher.group(2).trim());
            }
            url = matcher.group(3).trim();
        }
        return new MavenArtifactRepository(id, url, layout, policy, policy);
    }

    private ArtifactRepositoryLayout getLayout(String id)
            throws MojoFailureException {
        ArtifactRepositoryLayout layout = repositoryLayouts.get(id);

        if (layout == null) {
            throw new MojoFailureException(id, "Invalid repository layout",
                    "Invalid repository layout: " + id);
        }

        return layout;
    }

    protected boolean isSkip() {
        return skip;
    }

    // @Parameter( alias = "groupId" )
    public void setGroupId(String groupId) {
        this.coordinate.setGroupId(groupId);
    }

    // @Parameter( alias = "artifactId" )
    public void setArtifactId(String artifactId) {
        this.coordinate.setArtifactId(artifactId);
    }

    // @Parameter( alias = "version" )
    public void setVersion(String version) {
        this.coordinate.setVersion(version);
    }

    // @Parameter( alias = "classifier" )
    public void setClassifier(String classifier) {
        this.coordinate.setClassifier(classifier);
    }

    // @Parameter( alias = "packaging" )
    public void setPackaging(String type) {
        this.coordinate.setType(type);
    }

}
