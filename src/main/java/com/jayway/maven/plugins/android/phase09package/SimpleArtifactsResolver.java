/*
 * Copyright (C) 2011 Jayway AB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jayway.maven.plugins.android.phase09package;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.logging.Log;
import org.sonatype.aether.repository.RemoteRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Convenience class for using an {@link ArtifactResolver} on more than one artifact at a time.
 *
 * @author hugo@josefson.org
 */
public class SimpleArtifactsResolver {
    private final ArtifactResolver artifactResolver;
    private final ArtifactRepository localRepository;
    private final List<ArtifactRepository> remoteRepositories;

    public SimpleArtifactsResolver(final ArtifactResolver artifactResolver, final ArtifactRepository localRepository, final List<ArtifactRepository> remoteRepositories, final boolean b) {
        this.artifactResolver = artifactResolver;
        this.localRepository = localRepository;
        this.remoteRepositories = remoteRepositories;
    }

    /**
     * Convenience method for accessing
     * {@link org.apache.maven.artifact.resolver.ArtifactResolver#resolve(org.apache.maven.artifact.Artifact, java.util.List, org.apache.maven.artifact.repository.ArtifactRepository)}
     * with more than one artifact at a time.
     *
     * @param artifacts the artifacts to resolve
     * @throws ArtifactResolutionException
     * @throws ArtifactNotFoundException
     */
    public void resolve(final Set<Artifact> artifacts) throws ArtifactResolutionException, ArtifactNotFoundException {
        for (Artifact artifact : artifacts) {
            artifactResolver.resolve(artifact, remoteRepositories, localRepository);
        }
    }
}
