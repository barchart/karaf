package org.apache.karaf.tooling.semantic.selector;

/*******************************************************************************
 * Copyright (c) 2010-2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

import java.util.Set;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;

/**
 */
public class ExtensionDependencySelector implements DependencySelector {

	private final Set<String> typeIncluded;

	/**
     */
	public ExtensionDependencySelector(final Set<String> typeIncluded) {
		this.typeIncluded = typeIncluded;
	}

	@Override
	public boolean selectDependency(final Dependency dependency) {
		final Artifact artifact = dependency.getArtifact();
		final String extension = artifact.getExtension();
		return typeIncluded.contains(extension);
	}

	@Override
	public DependencySelector deriveChildSelector(
			final DependencyCollectionContext context) {
		return this;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		} else if (null == obj || !getClass().equals(obj.getClass())) {
			return false;
		}

		final ExtensionDependencySelector that = (ExtensionDependencySelector) obj;
		return this.typeIncluded.equals(that.typeIncluded);
	}

	@Override
	public int hashCode() {
		return typeIncluded.hashCode();
	}

}
