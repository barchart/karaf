/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.karaf.tooling.features;

import static java.lang.String.format;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLStreamException;

import org.apache.felix.utils.properties.Properties;
import org.apache.karaf.features.BundleInfo;
import org.apache.karaf.features.Dependency;
import org.apache.karaf.features.FeaturesService;
import org.apache.karaf.features.Repository;
import org.apache.karaf.features.internal.model.Bundle;
import org.apache.karaf.features.internal.model.Feature;
import org.apache.karaf.features.internal.model.Features;
import org.apache.karaf.features.internal.model.JaxbUtil;
import org.apache.karaf.kar.internal.Kar;
import org.apache.karaf.tooling.utils.MojoSupport;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * Installs kar dependencies into a server-under-construction in target/assembly
 *
 * @goal install-kars
 * @phase process-resources
 * @requiresDependencyResolution runtime
 * @inheritByDefault true
 * @description Install kar dependencies
 */
public class InstallKarsMojo extends MojoSupport {

    /**
     * Directory that resources are copied to during the build.
     *
     * @parameter expression="${project.build.directory}/assembly"
     * @required
     */
    protected String workDirectory;

    /**
     * features config file.
     *
     * @parameter expression="${project.build.directory}/assembly/etc/org.apache.karaf.features.cfg"
     * @required
     */
    protected File featuresCfgFile;

    /**
     * startup.properties file.
     *
     * @parameter expression="${project.build.directory}/assembly/etc/startup.properties"
     * @required
     */
    protected File startupPropertiesFile;

    /**
     * default start level for bundles in features that dont' specify it
     *
     * @parameter
     */
    protected int defaultStartLevel = 30;

    /**
     * if false, unpack to system and add bundles to startup.properties
     * if true, unpack to system and add feature to features config
     */
    protected boolean dontAddToStartup;

    /**
     * Directory that resources are copied to during the build.
     *
     * @parameter expression="${project.build.directory}/assembly/system"
     * @required
     */
    protected File systemDirectory;

    /**
     * List of features from runtime-scope features xml and kars to be installed into system and listed in startup.properties.
     *
     * @parameter
     */
    private List<String> startupFeatures;

    /**
     * List of features from runtime-scope features xml and kars to be installed into system repo and listed in features service boot features.
     *
     * @parameter
     */
    private List<String> bootFeatures;

    /**
     * List of features from runtime-scope features xml and kars to be installed into system repo and not mentioned elsewhere.
     *
     * @parameter
     */
    private List<String> installedFeatures;

    // Aether support
    /**
     * The entry point to Aether, i.e. the component doing all the work.
     *
     * @component
     */
    private RepositorySystem repoSystem;

    /**
     * The current repository/network configuration of Maven.
     *
     * @parameter default-value="${repositorySystemSession}"
     * @readonly
     */
    private RepositorySystemSession repoSession;

    /**
     * The project's remote repositories to use for the resolution of plugins and their dependencies.
     *
     * @parameter default-value="${project.remoteProjectRepositories}"
     * @readonly
     */
    private List<RemoteRepository> remoteRepos;

    /**
     * When a feature depends on another feature, try to find it in another referenced feature-file and install that one
     * too.
     *
     * @parameter
     */
    private boolean addTransitiveFeatures = true;

    private URI system;
    private CommentProperties startupProperties = new CommentProperties();
    private Set<Feature> featureSet = new HashSet<Feature>();
    private List<Dependency> missingDependencies = new ArrayList<Dependency>();

    /**
     * list of features to  install into local repo.
     */
    private List<Feature> localRepoFeatures = new ArrayList<Feature>();

    @SuppressWarnings("deprecation")
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        systemDirectory.mkdirs();
        system = systemDirectory.toURI();
        if (startupPropertiesFile.exists()) {
            try {
                final InputStream in = new FileInputStream(startupPropertiesFile);
                try {
                    startupProperties.load(in);
                } finally {
                    in.close();
                }
            } catch (final IOException e) {
                throw new MojoFailureException("Could not open existing startup.properties file at " + startupPropertiesFile, e);
            }
        } else {
            startupProperties.setHeader(Collections.singletonList("#Bundles to be started on startup, with startlevel"));
            if (!startupPropertiesFile.getParentFile().exists()) {
                startupPropertiesFile.getParentFile().mkdirs();
            }
        }

        final FeaturesService featuresService = new OfflineFeaturesService();

        final Collection<Artifact> dependencies = project.getDependencyArtifacts();
        final StringBuilder buf = new StringBuilder();
        for (Artifact artifact : dependencies) {
            dontAddToStartup = "runtime".equals(artifact.getScope());
            if ("kar".equals(artifact.getType()) && acceptScope(artifact)) {
                final File file = artifact.getFile();
                try {
                    final Kar kar = new Kar(file.toURI());
                    kar.extract(new File(system.getPath()), new File(workDirectory));
                    for (final URI repoUri : kar.getFeatureRepos()) {
                        featuresService.removeRepository(repoUri);
                        featuresService.addRepository(repoUri);
                    }
                } catch (final Exception e) {
                    throw new RuntimeException("Could not install kar: " + artifact.toString() + "\n", e);
                    //buf.append("Could not install kar: ").append(artifact.toString()).append("\n");
                    //buf.append(e.getMessage()).append("\n\n");
                }
            }
            if ("features".equals(artifact.getClassifier()) && acceptScope(artifact)) {
                final String uri = MavenUtil.artifactToMvn(artifact);

                final File source = artifact.getFile();
                final DefaultRepositoryLayout layout = new DefaultRepositoryLayout();

                //remove timestamp version
                artifact = factory.createArtifactWithClassifier(artifact.getGroupId(), artifact.getArtifactId(), artifact.getBaseVersion(), artifact.getType(), artifact.getClassifier());
                final File target = new File(system.resolve(layout.pathOf(artifact)));

                if (!target.exists()) {
                    target.getParentFile().mkdirs();
                    try {
                        copy(source, target);
                    } catch (final RuntimeException e) {
                        getLog().error("Could not copy features " + uri + " from source file " + source, e);
                    }

                    // for snapshot, generate the repository metadata in order to avoid override of snapshot from remote repositories
                    if (artifact.isSnapshot()) {
                        getLog().debug("Feature " + uri + " is a SNAPSHOT, generate the maven-metadata-local.xml file");
                        final File metadataTarget = new File(target.getParentFile(), "maven-metadata-local.xml");
                        try {
                            MavenUtil.generateMavenMetadata(artifact, metadataTarget);
                        } catch (final Exception e) {
                            getLog().warn("Could not create maven-metadata-local.xml", e);
                            getLog().warn("It means that this SNAPSHOT could be overwritten by an older one present on remote repositories");
                        }
                    }

                }
                try {
                    featuresService.addRepository(URI.create(uri));
                } catch (final Exception e) {
                    buf.append("Could not install feature: ").append(artifact.toString()).append("\n");
                    buf.append(e.getMessage()).append("\n\n");
                }
            }
        }

        // install bundles listed in startup properties that weren't in kars into the system dir
        final Set<?> keySet = startupProperties.keySet();
        for (final Object keyObject : keySet) {
            final String key = (String) keyObject;
            final String path = MavenUtil.pathFromMaven(key);
            final File target = new File(system.resolve(path));
            if (!target.exists()) {
                install(key, target);
            }
        }

        // install bundles listed in install features not in system
        for (final Feature feature : localRepoFeatures) {
            for (final Bundle bundle : feature.getBundle()) {
                if (!bundle.isDependency()) {
                    final String key = bundle.getLocation();
                    final String path = MavenUtil.pathFromMaven(key);
                    final File test = new File(system.resolve(path));
                    if (!test.exists()) {
                        final File target = new File(system.resolve(path));
                        if (!target.exists()) {
                            install(key, target);
                            final Artifact artifact = MavenUtil.mvnToArtifact(key);
                            if (artifact.isSnapshot()) {
                                // generate maven-metadata-local.xml for the artifact
                                final File metadataSource = new File(resolve(key).getParentFile(), "maven-metadata-local.xml");
                                final File metadataTarget = new File(target.getParentFile(), "maven-metadata-local.xml");
                                metadataTarget.getParentFile().mkdirs();
                                try {
                                    if (!metadataSource.exists()) {
                                        // the maven-metadata-local.xml doesn't exist in the local repo, generate one
                                        MavenUtil.generateMavenMetadata(artifact, metadataTarget);
                                    } else {
                                        // copy the metadata to the target
                                        copy(metadataSource, metadataTarget);
                                    }
                                } catch (final IOException ioException) {
                                    getLog().warn(ioException);
                                    getLog().warn("Unable to copy the maven-metadata-local.xml, it means that this SNAPSHOT will be overwritten by a remote one (if exist)");
                                }
                            }
                        }
                    }
                }
            }
        }

        try {
            final OutputStream out = new FileOutputStream(startupPropertiesFile);
            try {
                startupProperties.save(out);
            } finally {
                out.close();
            }
        } catch (final IOException e) {
            throw new MojoFailureException("Could not write startup.properties file at " + startupPropertiesFile, e);
        }
        if (buf.length() > 0) {
            throw new MojoExecutionException("Could not unpack all dependencies:\n" + buf.toString());
        }
    }

    private void install(final String key, final File target) throws MojoFailureException {
        final File source = resolve(key);
        target.getParentFile().mkdirs();
        copy(source, target);
    }

    private boolean acceptScope(final Artifact artifact) {
        return "compile".equals(artifact.getScope()) || "runtime".equals(artifact.getScope());
    }

    public File resolve(String id) throws MojoFailureException {
        id = MavenUtil.mvnToAether(id);
        final ArtifactRequest request = new ArtifactRequest();
        request.setArtifact(new DefaultArtifact(id));
        request.setRepositories(remoteRepos);

        getLog().debug("Resolving artifact " + id +
                " from " + remoteRepos);

        ArtifactResult result;
        try {
            result = repoSystem.resolveArtifact(repoSession, request);
        } catch (final ArtifactResolutionException e) {
            getLog().warn("could not resolve " + id, e);
            throw new MojoFailureException(format("Couldn't resolve artifact %s", id), e);
        }

        getLog().debug("Resolved artifact " + id + " to " +
                result.getArtifact().getFile() + " from "
                + result.getRepository());
        return result.getArtifact().getFile();
    }

    private class OfflineFeaturesService implements FeaturesService {
        private static final String FEATURES_REPOSITORIES = "featuresRepositories";
        private static final String FEATURES_BOOT = "featuresBoot";

        @Override
        public void validateRepository(final URI uri) throws Exception {
        }

        @Override
        public void addRepository(final URI uri) throws Exception {
            if (dontAddToStartup) {
                getLog().info("Adding feature repository to system: " + uri);
                if (featuresCfgFile.exists()) {
                    final Properties properties = new Properties();
                    final InputStream in = new FileInputStream(featuresCfgFile);
                    try {
                        properties.load(in);
                    } finally {
                        in.close();
                    }
                    String existingFeatureRepos = retrieveProperty(properties, FEATURES_REPOSITORIES);
                    if (!existingFeatureRepos.contains(uri.toString())) {
                        existingFeatureRepos = existingFeatureRepos + uri.toString();
                        properties.put(FEATURES_REPOSITORIES, existingFeatureRepos);
                    }
                    final Features repo = readFeatures(uri);
                    for (final Feature feature : repo.getFeature()) {
                        featureSet.add(feature);
                        if (startupFeatures != null && startupFeatures.contains(feature.getName())) {
                            installFeature(feature, null);
                        } else if (bootFeatures != null && bootFeatures.contains(feature.getName())) {
                            localRepoFeatures.add(feature);
                            missingDependencies.addAll(feature.getDependencies());
                            String existingBootFeatures = retrieveProperty(properties, FEATURES_BOOT);
                            if (!existingBootFeatures.contains(feature.getName())) {
                                existingBootFeatures = existingBootFeatures + feature.getName();
                                properties.put(FEATURES_BOOT, existingBootFeatures);
                            }
                        } else if (installedFeatures != null && installedFeatures.contains(feature.getName())) {
                            localRepoFeatures.add(feature);
                            missingDependencies.addAll(feature.getDependencies());
                        }
                    }
                    if (addTransitiveFeatures) {
                        addMissingDependenciesToRepo();
                    }
                    final FileOutputStream out = new FileOutputStream(featuresCfgFile);
                    try {
                        properties.save(out);
                    } finally {
                        out.close();
                    }
                }
            } else {
                getLog().info("Installing feature " + uri + " to system and startup.properties");
                final Features features = readFeatures(uri);
                for (final Feature feature : features.getFeature()) {
                    installFeature(feature, null);
                }
            }
        }

        private void addMissingDependenciesToRepo() {
            for (final ListIterator<Dependency> iterator = missingDependencies.listIterator(); iterator.hasNext(); ) {
                final Dependency dependency = iterator.next();
                final Feature depFeature = lookupFeature(dependency);
                if (depFeature == null) {
                    continue;
                }
                localRepoFeatures.add(depFeature);
                iterator.remove();
                addAllMissingDependencies(iterator, depFeature);
            }
        }

        private void addAllMissingDependencies(final ListIterator<Dependency> iterator, final Feature depFeature) {
            for (final Dependency dependency : depFeature.getDependencies()) {
                if (!missingDependencies.contains(dependency)) {
                    iterator.add(dependency);
                }
            }
        }

        @Override
        public void addRepository(final URI uri, final boolean install) throws Exception {
        }

        private String retrieveProperty(final Properties properties, final String key) {
            return properties.containsKey(key) && properties.get(key) != null ?  properties.get(key) + "," : "";
        }

        private Features readFeatures(final URI uri) throws XMLStreamException, JAXBException, IOException {
            File repoFile;
            if (uri.toString().startsWith("mvn:")) {
                final URI featuresPath = system.resolve(MavenUtil.pathFromMaven(uri.toString()));
                repoFile = new File(featuresPath);
            } else {
                repoFile = new File(uri);
            }
            final InputStream in = new FileInputStream(repoFile);
            Features features;
            try {
                features = JaxbUtil.unmarshal(in, false);
            } finally {
                in.close();
            }
            return features;
        }

        @Override
        public void removeRepository(final URI uri) {
        }

        @Override
        public void removeRepository(final URI uri, final boolean install) {
        }

        @Override
        public void restoreRepository(final URI uri) throws Exception {
        }

        @Override
        public Repository[] listRepositories() {
            return new Repository[0];
        }

        @Override
        public void installFeature(final String name) throws Exception {
        }

        @Override
        public void installFeature(final String name, final EnumSet<Option> options) throws Exception {
        }

        @Override
        public void installFeature(final String name, final String version) throws Exception {
        }

        @Override
        public void installFeature(final String name, final String version, final EnumSet<Option> options) throws Exception {
        }

        @Override
        public void installFeature(final org.apache.karaf.features.Feature feature, final EnumSet<Option> options) throws Exception {
            List<String> comment = Arrays.asList(new String[]{"", "# feature: " + feature.getName() + " version: " + feature.getVersion()});
            for (final BundleInfo bundle : feature.getBundles()) {
                final String location = bundle.getLocation();
                final String startLevel = Integer.toString(bundle.getStartLevel() == 0 ? defaultStartLevel : bundle.getStartLevel());
                if (startupProperties.containsKey(location)) {
					final int oldStartLevel = Integer.decode(startupProperties.get(location).toString());
                    if (oldStartLevel > bundle.getStartLevel()) {
                        startupProperties.put(location, startLevel);
                    }
                } else {
                    if (comment == null) {
                        startupProperties.put(location, startLevel);
                    } else {
                        startupProperties.put(location, comment, startLevel);
                        comment = null;
                    }
                }
            }
        }

        private Feature lookupFeature(final Dependency dependency) {
            for (final Feature feature : featureSet) {
                if (featureSatisfiesDependency(feature, dependency)) {
                    return feature;
                }
            }
            return null;
        }

        private boolean featureSatisfiesDependency(final Feature feature, final Dependency dependency) {
            if (!feature.getName().equals(dependency.getName())) {
                return false;
            }
            return true;
        }

        @Override
        public void installFeatures(final Set<org.apache.karaf.features.Feature> features, final EnumSet<Option> options)
            throws Exception {
        }

        @Override
        public void uninstallFeature(final String name) throws Exception {
        }

        @Override
        public void uninstallFeature(final String name, final String version) throws Exception {
        }

        @Override
        public Feature[] listFeatures() throws Exception {
            return new Feature[0];
        }

        @Override
        public Feature[] listInstalledFeatures() {
            return new Feature[0];
        }

        @Override
        public boolean isInstalled(final org.apache.karaf.features.Feature f) {
            return false;
        }

        @Override
        public org.apache.karaf.features.Feature getFeature(final String name, final String version) throws Exception {
            return null;
        }

        @Override
        public org.apache.karaf.features.Feature getFeature(final String name) throws Exception {
            return null;
        }

        @Override
        public Repository getRepository(final String repoName) {
            // TODO Auto-generated method stub
            return null;
        }

		@Override
		public void refreshRepository(final URI uri) throws Exception {
			// TODO Auto-generated method stub

		}
    }

    // when FELIX-2887 is ready we can use plain Properties again
    private static class CommentProperties extends Properties {

        private Map<String, Layout> layout;
        private Map<String, String> storage;

        @SuppressWarnings("unchecked")
        public CommentProperties() {
            layout = (Map<String, Layout>) getField("layout");
            storage = (Map<String, String>) getField("storage");
        }

        private Object getField(final String fieldName) {
            try {
                final Field l = Properties.class.getDeclaredField(fieldName);
                final boolean old = l.isAccessible();
                l.setAccessible(true);
                final Object layout = l.get(this);
                l.setAccessible(old);
                return layout;
            } catch (final Exception e) {
                throw new RuntimeException("Could not access field " + fieldName, e);
            }
        }

        @Override
		public String put(final String key, final String comment, final String value) {
            return put(key, Collections.singletonList(comment), value);
        }

        @Override
		public List<String> getRaw(final String key) {
            if (layout.containsKey(key)) {
                if (layout.get(key).getValueLines() != null) {
                    return new ArrayList<String>(layout.get(key).getValueLines());
                }
            }
            final List<String> result = new ArrayList<String>();
            if (storage.containsKey(key)) {
                result.add(storage.get(key));
            }
            return result;
        }

    }

}
