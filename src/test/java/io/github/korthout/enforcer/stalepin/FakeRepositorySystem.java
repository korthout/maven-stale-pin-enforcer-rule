package io.github.korthout.enforcer.stalepin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SyncContext;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeployResult;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallResult;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.resolution.MetadataRequest;
import org.eclipse.aether.resolution.MetadataResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.resolution.VersionRequest;
import org.eclipse.aether.resolution.VersionResult;

/**
 * Test double for the injected {@link RepositorySystem}. The rules only ever collect dependency
 * graphs, so only {@link #collectDependencies} is functional: it records every call and answers
 * through a configurable handler. All other operations are unsupported.
 */
final class FakeRepositorySystem implements RepositorySystem {

  /** Answers a {@link #collectDependencies} call after it has been recorded. */
  @FunctionalInterface
  interface CollectHandler {
    CollectResult collect(RepositorySystemSession session, CollectRequest request)
        throws DependencyCollectionException;
  }

  private final List<RepositorySystemSession> sessions = new ArrayList<>();
  private final List<CollectRequest> requests = new ArrayList<>();

  private CollectHandler handler =
      (session, request) -> {
        throw new AssertionError("unexpected dependency collection");
      };

  void onCollectDependencies(CollectHandler handler) {
    this.handler = handler;
  }

  List<RepositorySystemSession> collectedSessions() {
    return sessions;
  }

  List<CollectRequest> collectedRequests() {
    return requests;
  }

  @Override
  public CollectResult collectDependencies(RepositorySystemSession session, CollectRequest request)
      throws DependencyCollectionException {
    sessions.add(session);
    requests.add(request);
    return handler.collect(session, request);
  }

  @Override
  public VersionRangeResult resolveVersionRange(
      RepositorySystemSession session, VersionRangeRequest request) {
    throw unsupported();
  }

  @Override
  public VersionResult resolveVersion(RepositorySystemSession session, VersionRequest request) {
    throw unsupported();
  }

  @Override
  public ArtifactDescriptorResult readArtifactDescriptor(
      RepositorySystemSession session, ArtifactDescriptorRequest request) {
    throw unsupported();
  }

  @Override
  public DependencyResult resolveDependencies(
      RepositorySystemSession session, DependencyRequest request) {
    throw unsupported();
  }

  @Override
  public ArtifactResult resolveArtifact(RepositorySystemSession session, ArtifactRequest request) {
    throw unsupported();
  }

  @Override
  public List<ArtifactResult> resolveArtifacts(
      RepositorySystemSession session, Collection<? extends ArtifactRequest> requests) {
    throw unsupported();
  }

  @Override
  public List<MetadataResult> resolveMetadata(
      RepositorySystemSession session, Collection<? extends MetadataRequest> requests) {
    throw unsupported();
  }

  @Override
  public InstallResult install(RepositorySystemSession session, InstallRequest request) {
    throw unsupported();
  }

  @Override
  public DeployResult deploy(RepositorySystemSession session, DeployRequest request) {
    throw unsupported();
  }

  @Override
  public LocalRepositoryManager newLocalRepositoryManager(
      RepositorySystemSession session, LocalRepository localRepository) {
    throw unsupported();
  }

  @Override
  public SyncContext newSyncContext(RepositorySystemSession session, boolean shared) {
    throw unsupported();
  }

  @Override
  public List<RemoteRepository> newResolutionRepositories(
      RepositorySystemSession session, List<RemoteRepository> repositories) {
    throw unsupported();
  }

  @Override
  public RemoteRepository newDeploymentRepository(
      RepositorySystemSession session, RemoteRepository repository) {
    throw unsupported();
  }

  @Override
  public void addOnSystemEndedHandler(Runnable handler) {
    throw unsupported();
  }

  @Override
  public void shutdown() {
    throw unsupported();
  }

  private static UnsupportedOperationException unsupported() {
    return new UnsupportedOperationException("not used by the rules under test");
  }
}
