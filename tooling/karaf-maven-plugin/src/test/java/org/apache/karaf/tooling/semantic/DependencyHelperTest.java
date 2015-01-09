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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.karaf.tooling.features.DependencyHelper;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.selector.OptionalDependencySelector;
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;
import org.junit.Test;

public class DependencyHelperTest {

	DependencyHelper newHelper() throws Exception {

		final List<RemoteRepository> remoteList = new ArrayList<RemoteRepository>();
		remoteList.add(UnitHelp.newRepoRemote());

		final List<RemoteRepository> pluginRepos = remoteList;
		final List<RemoteRepository> projectRepos = remoteList;
		final RepositorySystem system = UnitHelp.newSystem();
		final RepositorySystemSession session = UnitHelp.newSession(system);

		final DependencyHelper helper = new DependencyHelper(projectRepos, session, system);

		return helper;

	}

	@Test
	public void dependency() throws Exception {

		final String uri = "com.carrotgarden.osgi:carrot-osgi-anno-scr-make:pom:1.1.3";

		final DependencyHelper helper = newHelper();

		final MavenProject project = UnitHelp.newProject(uri);

		final Collection<String> included = null;
		final Collection<String> excluded = null;

		helper.getDependencies(project, true);

		final String report = helper.getTreeListing();

		System.out.println("\n" + report);

	}

	public static void main(final String[] args) throws Exception {

		final String uri = "com.carrotgarden.osgi:carrot-osgi-anno-scr-make:jar:1.1.3";

		final Artifact artifact = new DefaultArtifact(uri);

		final Dependency dependency = new Dependency(artifact, "compile");

		final CollectRequest collectRequest = new CollectRequest(dependency, null);

		final RepositorySystem system = UnitHelp.newSystem();

		final DefaultRepositorySystemSession session = UnitHelp.newSession(system);

		session.setOffline(true);

		final Collection<String> scopeIncluded = new ArrayList<String>();
		final Collection<String> scopeExcluded = new ArrayList<String>();

		scopeIncluded.add("provided");

		scopeExcluded.add("test");

		session.setDependencySelector( //
		new AndDependencySelector(//
				new OptionalDependencySelector(), //
				new ScopeDependencySelector(scopeIncluded, scopeExcluded), //
				new ExclusionDependencySelector()) //
		);

		final CollectResult collectResult = system.collectDependencies(session,
				collectRequest);

		final DependencyNode collectNode = collectResult.getRoot();

		final DependencyRequest dependencyRequest = new DependencyRequest(
				collectNode, null);

		final DependencyResult result = system.resolveDependencies(session,
				dependencyRequest);

		final DependencyNode resolveNode = result.getRoot();

		final PreorderNodeListGenerator generator = new PreorderNodeListGenerator();

		resolveNode.accept(generator);

		final List<Artifact> list = generator.getArtifacts(true);

		for (final Artifact item : list) {
			System.out.println("item = " + item );
		}

	}

}
