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

package org.apache.karaf.tooling.semantic;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.DefaultModelReader;
import org.apache.maven.model.io.ModelReader;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.internal.DefaultArtifactDescriptorReader;
import org.apache.maven.repository.internal.DefaultVersionRangeResolver;
import org.apache.maven.repository.internal.DefaultVersionResolver;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.impl.VersionResolver;
import org.eclipse.aether.internal.impl.DefaultTransporterProvider;
import org.eclipse.aether.internal.transport.wagon.PlexusWagonConfigurator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.spi.connector.transport.TransporterProvider;
import org.eclipse.aether.transport.wagon.WagonConfigurator;
import org.eclipse.aether.transport.wagon.WagonProvider;
import org.eclipse.aether.transport.wagon.WagonTransporterFactory;
import org.eclipse.aether.util.filter.ScopeDependencyFilter;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;

public class UnitHelp {

	private static volatile Logger logger;

	public static final String URL_CENTRAL = "http://repo1.maven.org/maven2/";

	public static final String URL_SONATYPE = "http://oss.sonatype.org/content/groups/public/";

	public static Logger logger() {
		if (logger == null) {
			logger = new ConsoleLogger();
		}
		return logger;
	}

	/**
	 * Verify operation manually.
	 */
	public static void main(final String[] args) throws Exception {

		final Logger log = logger();

		final RepositorySystem system = newSystem();

		final RepositorySystemSession session = newSession(system);

		// String uri = "jmock:jmock:pom:1.1.0";
		final String uri = "org.apache.maven:maven-profile:2.2.1";

		final Artifact artifact = new DefaultArtifact(uri);

		final Dependency dependency = new Dependency(artifact, "compile");

		final RemoteRepository central = newRepoRemote();

		final CollectRequest collectRequest = new CollectRequest();
		collectRequest.setRoot(dependency);
		collectRequest.addRepository(central);

		final DependencyNode collectNode = system.collectDependencies(session,
				collectRequest).getRoot();

		final List<String> include = new ArrayList<String>();
		final List<String> exclude = new ArrayList<String>();

		final DependencyFilter filter = new ScopeDependencyFilter(include,
				exclude);

		final DependencyRequest dependencyRequest = new DependencyRequest(
				collectNode, filter);

		final DependencyResult result = system.resolveDependencies(session,
				dependencyRequest);

		final DependencyNode resolveNode = result.getRoot();

		final PreorderNodeListGenerator generator = new PreorderNodeListGenerator();

		resolveNode.accept(generator);

		final String[] pathArray = generator.getClassPath().split(
				File.pathSeparator);

		for (final String path : pathArray) {
			log.info("path = " + path);
		}

		//

		final MavenProject project = newProject("org.apache.maven:maven-model:pom:3.0");

		log.info("project = " + project);

	}

	/**
	 * Resolve maven URI into maven artifact.
	 */
	public static Artifact newArtifact(final String mavenURI) throws Exception {

		final RepositorySystem system = newSystem();
		final RepositorySystemSession session = newSession(system);
		final RemoteRepository central = newRepoRemote();

		final ArtifactRequest request = new ArtifactRequest();
		request.setArtifact(new DefaultArtifact(mavenURI));
		request.addRepository(central);

		final ArtifactResult result = system.resolveArtifact(session, request);

		final Artifact artifact = result.getArtifact();

		return artifact;

	}

	/**
	 * Resolve maven URI into maven project.
	 * <p>
	 * Provides only model and artifact.
	 */
	public static MavenProject newProject(final String mavenURI) throws Exception {

		final Artifact artifact = newArtifact(mavenURI);

		final File input = artifact.getFile();

		final ModelReader reader = new DefaultModelReader();

		final Model model = reader.read(input, null);

		final MavenProject project = new MavenProject(model);

		project.setArtifact(RepositoryUtils.toArtifact(artifact));

		return project;

	}

	/**
	 * Local user repository.
	 */
	public static File newRepoFolder() throws Exception {
		final File home = new File(System.getProperty("user.home"));
		final File repo = new File(home, ".m2/repository");
		return repo;
	}

	/**
	 * Local user repository.
	 */
	public static LocalRepository newRepoLocal() throws Exception {
		return new LocalRepository(newWorkFolder());
	}

	/**
	 * Remote central repository.
	 */
	public static RemoteRepository newRepoRemote() throws Exception {
		final RemoteRepository central = new RemoteRepository.Builder("central",
				"default", URL_CENTRAL).build();
		return central;
	}

	/**
	 * Remote central repository as list.
	 */
	public static List<RemoteRepository> newRepoRemoteList() throws Exception {
		return Collections.singletonList(newRepoRemote());
	}

	/**
	 * Default repository session.
	 */
	public static RepositorySystemSession newSession() throws Exception {
		return newSession(newSystem());
	}

	/**
	 * Default repository session.
	 */
	public static DefaultRepositorySystemSession newSession(
			final RepositorySystem system) throws Exception {

		final LocalRepository localRepo = newRepoLocal();

		final DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();

		session.setLocalRepositoryManager(system
				.newLocalRepositoryManager(session, localRepo));

		return session;

	}

	/**
	 * Default repository system.
	 */
	public static RepositorySystem newSystem() throws Exception {

		final DefaultServiceLocator locator = new DefaultServiceLocator();

		locator.addService(VersionResolver.class, DefaultVersionResolver.class);
		locator.addService(VersionRangeResolver.class, DefaultVersionRangeResolver.class);
		locator.addService(ArtifactDescriptorReader.class, DefaultArtifactDescriptorReader.class);
		locator.addService(WagonProvider.class, SimpleWagonProvider.class);
		locator.addService(WagonConfigurator.class, PlexusWagonConfigurator.class);
		locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
		locator.addService(TransporterProvider.class, DefaultTransporterProvider.class);
		locator.addService(TransporterFactory.class, WagonTransporterFactory.class);

		return locator.getService(RepositorySystem.class);

	}

	/**
	 * Local repository.
	 */
	public static File newWorkFolder() throws Exception {
		final File work = new File(System.getProperty("user.dir"));
		final File target = new File(work, "target");
		// final File repo = new File(target, "repo-" +
		// System.currentTimeMillis());
		final File repo = new File(target, "repo-local");
		repo.mkdirs();
		repo.deleteOnExit();
		return repo;
	}

}
