package org.apache.karaf.tooling.semantic.range;

import org.apache.karaf.tooling.semantic.ReflectUtil;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionRange;

public class SemanticRangeSpan extends SemanticRangeBase {

	private final VersionRange range;

	public SemanticRangeSpan(final VersionRange range) {
		this.range = range;
	}

	@Override
	public boolean containsVersion(final Version version) {
		return range.containsVersion(version);
	}

	@Override
	public Bound getLowerBound() {
		final Version version = ReflectUtil.readField(range, "lowerBound");
		final boolean inclusive = ReflectUtil.readField(range, "lowerBoundInclusive");
		return new Bound(version, inclusive);
	}

	@Override
	public Bound getUpperBound() {
		final Version version = ReflectUtil.readField(range, "upperBound");
		final boolean inclusive = ReflectUtil.readField(range, "upperBoundInclusive");
		return new Bound(version, inclusive);
	}

	@Override
	public String toString() {
		return range.toString();
	}

}
