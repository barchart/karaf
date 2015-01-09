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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Snapshot;
import org.apache.maven.artifact.repository.metadata.SnapshotVersion;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Writer;
import org.eclipse.aether.artifact.DefaultArtifact;

/**
 * Util method for Maven manipulation (URL convert, metadata generation, etc).
 */
public class MavenUtil {

    private static final DefaultRepositoryLayout layout = new DefaultRepositoryLayout();
    private static final Pattern aetherPattern = Pattern.compile("([^: ]+):([^: ]+)(:([^: ]*)(:([^: ]+))?)?:([^: ]+)");
    private static final Pattern mvnPattern = Pattern.compile("mvn:([^/ ]+)/([^/ ]+)/([^/ ]*)(/([^/ ]+)(/([^/ ]+))?)?");

    /**
     * Convert PAX URL mvn format to aether coordinate format.
     * N.B. we do not handle repository-url in mvn urls.
     * N.B. version is required in mvn urls.
     *
     * @param name PAX URL mvn format: mvn-uri := 'mvn:' [ repository-url '!' ] group-id '/' artifact-id [ '/' [version] [ '/' [type] [ '/' classifier ] ] ] ]
     * @return aether coordinate format: <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>
     */
    static String mvnToAether(final String name) {
        final Matcher m = mvnPattern.matcher(name);
        if (!m.matches()) {
            return name;
        }
        final StringBuilder b = new StringBuilder();
        b.append(m.group(1)).append(":");//groupId
        b.append(m.group(2)).append(":");//artifactId
        final String extension = m.group(5);
        final String classifier = m.group(7);
        if (present(classifier)) {
            if (present(extension)) {
                b.append(extension).append(":");
            } else {
                b.append("jar:");
            }
            b.append(classifier).append(":");
        } else {
            if (present(extension) && !"jar".equals(extension)) {
                b.append(extension).append(":");
            }
        }
        b.append(m.group(3));
        return b.toString();
    }

    private static boolean present(final String part) {
        return part != null && !part.isEmpty();
    }

    /**
     * Convert Aether coordinate format to PAX mvn format.
     * N.B. we do not handle repository-url in mvn urls.
     * N.B. version is required in mvn urls.
     *
     * @param name aether coordinate format: <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>
     * @return PAX URL mvn format: mvn-uri := 'mvn:' [ repository-url '!' ] group-id '/' artifact-id [ '/' [version] [ '/' [type] [ '/' classifier ] ] ] ]
     */
    static String aetherToMvn(final String name) {
        final Matcher m = aetherPattern.matcher(name);
        if (!m.matches()) {
            return name;
        }
        final StringBuilder b = new StringBuilder("mvn:");
        b.append(m.group(1)).append("/");//groupId
        b.append(m.group(2)).append("/");//artifactId
        b.append(m.group(7));//version
        final String extension = m.group(4);
        final String classifier = m.group(6);
        if (present(classifier)) {
            if (present(extension)) {
                b.append("/").append(extension);
            } else {
                b.append("/jar");
            }
            b.append("/").append(classifier);
        } else if (present(extension)) {
            b.append("/").append(extension);
        }

        return b.toString();
    }

    /**
     * Convert a PAX URL mvn format into a filesystem path.
     *
     * @param name PAX URL mvn format (mvn:<groupId>/<artifactId>/<version>/<type>/<classifier>)
     * @return a filesystem path
     */
    static String pathFromMaven(String name) {
        if (name.indexOf(':') == -1) {
            return name;
        }
        name = mvnToAether(name);
        return pathFromAether(name);
    }

    /**
     * Convert a Aether coordinate format into a filesystem path.
     *
     * @param name the Aether coordinate format (<groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>)
     * @return the filesystem path
     */
    static String pathFromAether(final String name) {
        final DefaultArtifact artifact = new DefaultArtifact(name);
        final Artifact mavenArtifact = RepositoryUtils.toArtifact(artifact);
        return layout.pathOf(mavenArtifact);
    }

    /**
     * Convert a Maven <code>Artifact</code> into a PAX URL mvn format.
     *
     * @param artifact the Maven <code>Artifact</code>.
     * @return the corresponding PAX URL mvn format (mvn:<groupId>/<artifactId>/<version>/<type>/<classifier>)
     */
    static String artifactToMvn(final Artifact artifact) {
        return artifactToMvn(RepositoryUtils.toArtifact(artifact));
    }

    /**
	 * Convert an Aether <code>org.eclipse.aether.artifact.Artifact</code> into a PAX URL mvn format.
	 *
	 * @param artifact the Aether <code>org.eclipse.aether.artifact.Artifact</code>.
	 * @return the corresponding PAX URL mvn format (mvn:<groupId>/<artifactId>/<version>/<type>/<classifier>)
	 */
	static String artifactToMvn(final org.eclipse.aether.artifact.Artifact artifact) {
        String bundleName;
        if (artifact.getExtension().equals("jar") && isEmpty(artifact.getClassifier())) {
            bundleName = String.format("mvn:%s/%s/%s", artifact.getGroupId(), artifact.getArtifactId(), artifact.getBaseVersion());
        } else {
            if (isEmpty(artifact.getClassifier())) {
                bundleName = String.format("mvn:%s/%s/%s/%s", artifact.getGroupId(), artifact.getArtifactId(), artifact.getBaseVersion(), artifact.getExtension());
            } else {
                bundleName = String.format("mvn:%s/%s/%s/%s/%s", artifact.getGroupId(), artifact.getArtifactId(), artifact.getBaseVersion(), artifact.getExtension(), artifact.getClassifier());
            }
        }
        return bundleName;
    }

    private static boolean isEmpty(final String classifier) {
        return classifier == null || classifier.length() == 0;
    }

    static Artifact mvnToArtifact(String name) {
        name = mvnToAether(name);
        final DefaultArtifact artifact = new DefaultArtifact(name);
        final Artifact mavenArtifact = RepositoryUtils.toArtifact(artifact);
        return mavenArtifact;
    }

    /**
     * Generate the maven-metadata-local.xml for the given Maven <code>Artifact</code>.
     *
     * @param artifact the Maven <code>Artifact</code>.
     * @param target   the target maven-metadata-local.xml file to generate.
     * @throws IOException if the maven-metadata-local.xml can't be generated.
     */
    static void generateMavenMetadata(final Artifact artifact, final File target) throws IOException {
        target.getParentFile().mkdirs();
        final Metadata metadata = new Metadata();
        metadata.setGroupId(artifact.getGroupId());
        metadata.setArtifactId(artifact.getArtifactId());
        metadata.setVersion(artifact.getVersion());
        metadata.setModelVersion("1.1.0");

        final Versioning versioning = new Versioning();
        versioning.setLastUpdatedTimestamp(new Date(System.currentTimeMillis()));
        final Snapshot snapshot = new Snapshot();
        snapshot.setLocalCopy(true);
        versioning.setSnapshot(snapshot);
        final SnapshotVersion snapshotVersion = new SnapshotVersion();
        snapshotVersion.setClassifier(artifact.getClassifier());
        snapshotVersion.setVersion(artifact.getVersion());
        snapshotVersion.setExtension(artifact.getType());
        snapshotVersion.setUpdated(versioning.getLastUpdated());
        versioning.addSnapshotVersion(snapshotVersion);

        metadata.setVersioning(versioning);

        final MetadataXpp3Writer metadataWriter = new MetadataXpp3Writer();
        final Writer writer = new FileWriter(target);
        metadataWriter.write(writer, metadata);
    }

    static String getFileName(final Artifact artifact) {
        final String name = artifact.getArtifactId() + "-" + artifact.getBaseVersion()
            + (artifact.getClassifier() != null ? "-" + artifact.getClassifier() : "") + "." + artifact.getType();
        return name;
    }

    static String getDir(final Artifact artifact) {
        return artifact.getGroupId().replace('.', '/') + "/" + artifact.getArtifactId() + "/" + artifact.getBaseVersion() + "/";
    }

}
