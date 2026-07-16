package io.github.korthout.enforcer.stalepin;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.collection.CollectRequest;

/** Builds the collect requests through which the rules see a reactor project's dependencies. */
final class CollectRequests {

  private CollectRequests() {}

  /** The project's dependency graph as Maven builds it: {@code dependencyManagement} applied. */
  static CollectRequest managed(MavenProject project, ArtifactTypeRegistry typeRegistry) {
    CollectRequest request = base(project, typeRegistry);
    if (project.getDependencyManagement() != null) {
      request.setManagedDependencies(
          project.getDependencyManagement().getDependencies().stream()
              .map(dependency -> RepositoryUtils.toDependency(dependency, typeRegistry))
              .toList());
    }
    return request;
  }

  /**
   * The project's dependency graph as it would look without the project's {@code
   * dependencyManagement}, so every transitive dependency carries the version its dependents
   * naturally request instead of the managed one.
   */
  static CollectRequest unmanaged(MavenProject project, ArtifactTypeRegistry typeRegistry) {
    return base(project, typeRegistry);
  }

  private static CollectRequest base(MavenProject project, ArtifactTypeRegistry typeRegistry) {
    CollectRequest request = new CollectRequest();
    Artifact projectArtifact = project.getArtifact();
    if (projectArtifact != null) {
      request.setRootArtifact(RepositoryUtils.toArtifact(projectArtifact));
    }
    request.setDependencies(
        project.getDependencies().stream()
            .map(dependency -> RepositoryUtils.toDependency(dependency, typeRegistry))
            .toList());
    request.setRepositories(project.getRemoteProjectRepositories());
    return request;
  }
}
