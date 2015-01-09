package org.apache.karaf.tooling.semantic.range;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;
import org.eclipse.aether.version.VersionRange;

public class SemanticRangeFactory {

	public static SemanticRange from(final DependencyNode node) {

		final VersionConstraint constraint = node.getVersionConstraint();

		final VersionType versionType = VersionType.form(constraint);

		switch (versionType) {

		case VALUE:
			final Version version = constraint.getVersion();
			return new SemanticRangePoint(version);

		case RANGE:
				final VersionRange range = constraint.getRange();
				return new SemanticRangeSpan(range);

		default:
			throw new IllegalStateException("Wrong version type = "
					+ versionType);

		}

	}

}
