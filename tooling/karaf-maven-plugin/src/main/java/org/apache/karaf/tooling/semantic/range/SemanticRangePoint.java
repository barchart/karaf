package org.apache.karaf.tooling.semantic.range;

import org.eclipse.aether.version.Version;

public class SemanticRangePoint extends SemanticRangeBase {

	private final Version version;

	public SemanticRangePoint(final Version version) {
		this.version = version;
	}

	@Override
	public boolean containsVersion(final Version version) {
		return this.version.equals(version);
	}

	@Override
	public Bound getLowerBound() {
		return new Bound(version, true);
	}

	@Override
	public Bound getUpperBound() {
		return new Bound(version, true);
	}

	@Override
	public String toString() {
		return "[" + version + "," + version + "]";
	}

}
