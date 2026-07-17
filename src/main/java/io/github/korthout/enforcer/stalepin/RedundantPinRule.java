package io.github.korthout.enforcer.stalepin;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleError;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DependencyNode;

/**
 * Flags {@code dependencyManagement} entries (pins) that no longer settle a version conflict.
 *
 * <p>Pins are typically added to settle a transitive version conflict: one dependency pulls in an
 * artifact at one version, another pulls it in at a different version, and the pin decides. Once
 * the dependencies evolve so that only a single version is requested anywhere in the reactor — and
 * that version is exactly the pinned one — the pin no longer changes anything, and this rule fails
 * the build for it.
 *
 * <p>Two situations intentionally pass:
 *
 * <ul>
 *   <li>The single requested version differs from the pinned version: the pin still forces a
 *       different version, which may be deliberate (e.g. avoiding a vulnerable release).
 *   <li>The pin supplies the version of a direct dependency declared without one: that is version
 *       management in active use, not a leftover conflict settlement.
 * </ul>
 *
 * <p>Pins that nothing in the build depends on at all are not this rule's finding; the {@code
 * stalePin} rule reports those.
 *
 * <p>To see the versions dependencies naturally request, the rule collects each reactor project's
 * dependency graph without applying the project's {@code dependencyManagement} and without conflict
 * resolution (so no version wins and hides the others). Like {@code stalePin} it collects the
 * graphs itself, because it typically runs at {@code validate}, and caches the reactor-wide result
 * in the resolver session so the graphs are only collected once per build.
 */
@Named("redundantPin")
public class RedundantPinRule extends AbstractEnforcerRule {

  private static final String REACTOR_REQUESTS_CACHE_KEY =
      RedundantPinRule.class.getName() + ".reactorRequests";

  private final MavenSession session;
  private final RepositorySystem repositorySystem;

  @Inject
  public RedundantPinRule(MavenSession session, RepositorySystem repositorySystem) {
    this.session = Objects.requireNonNull(session);
    this.repositorySystem = Objects.requireNonNull(repositorySystem);
  }

  @Override
  public void execute() throws EnforcerRuleException {
    MavenProject project = session.getCurrentProject();
    List<Dependency> pins = Pins.declaredIn(project);
    if (pins.isEmpty()) {
      getLog().debug("No dependencyManagement entries declared in this POM, nothing to check");
      return;
    }

    ReactorRequests requests = reactorRequests();
    List<Dependency> redundant = pins.stream().filter(pin -> isRedundant(pin, requests)).toList();
    getLog()
        .debug(
            "Checked %d pin(s) against the requested versions of %d coordinate(s), found %d redundant"
                .formatted(pins.size(), requests.requestedVersions().size(), redundant.size()));

    if (!redundant.isEmpty()) {
      throw new EnforcerRuleException(
          Pins.failureMessage(
              project,
              redundant,
              "redundant",
              List.of(
                  "Every dependency on these coordinates already requests the pinned version,",
                  "so there is no version conflict left to settle. Remove them from"
                      + " dependencyManagement.")));
    }
  }

  private boolean isRedundant(Dependency pin, ReactorRequests requests) {
    String coordinate = Pins.coordinate(pin);
    if (requests.versionlessCoordinates().contains(coordinate)) {
      // the pin supplies the version of a versionless direct dependency: in active use
      return false;
    }
    Set<String> requested =
        requests.requestedVersions().getOrDefault(coordinate, Collections.emptySet());
    if (requested.isEmpty()) {
      // nothing depends on the pinned coordinates at all: that is the stalePin rule's finding
      return false;
    }
    if (requested.size() > 1) {
      // the pin still settles a conflict between these versions
      return false;
    }
    String onlyRequested = requested.iterator().next();
    if (!onlyRequested.equals(pin.getVersion())) {
      getLog()
          .debug(
              "Pin %s:%s overrides the only requested version %s, keeping it (may be deliberate)"
                  .formatted(coordinate, pin.getVersion(), onlyRequested));
      return false;
    }
    return true;
  }

  /**
   * What the reactor's dependencies ask for when the pins are ignored: every version requested per
   * {@code groupId:artifactId}, plus the coordinates of direct dependencies declared without a
   * version (whose version therefore comes from a pin). Computed once per build and cached in the
   * resolver session.
   */
  private ReactorRequests reactorRequests() throws EnforcerRuleException {
    Object cached = session.getRepositorySession().getData().get(REACTOR_REQUESTS_CACHE_KEY);
    if (cached != null) {
      return (ReactorRequests) cached;
    }
    Map<String, Set<String>> requestedVersions = new HashMap<>();
    Set<String> versionlessCoordinates = new HashSet<>();
    RepositorySystemSession unmanagedSession = unmanagedSession();
    for (MavenProject project : session.getProjects()) {
      collectRequestedVersions(project, unmanagedSession, requestedVersions);
      addVersionlessCoordinates(project, versionlessCoordinates);
    }
    ReactorRequests requests = new ReactorRequests(requestedVersions, versionlessCoordinates);
    session.getRepositorySession().getData().set(REACTOR_REQUESTS_CACHE_KEY, requests);
    return requests;
  }

  /**
   * A copy of the build's resolver session without a dependency graph transformer: no conflict
   * resolution runs, so a graph collected with it keeps one node per requested version instead of a
   * single winner.
   */
  private RepositorySystemSession unmanagedSession() {
    DefaultRepositorySystemSession copy =
        new DefaultRepositorySystemSession(session.getRepositorySession());
    copy.setDependencyGraphTransformer(null);
    return copy;
  }

  private void collectRequestedVersions(
      MavenProject project,
      RepositorySystemSession resolverSession,
      Map<String, Set<String>> requestedVersions)
      throws EnforcerRuleException {
    CollectRequest request =
        CollectRequests.unmanaged(project, resolverSession.getArtifactTypeRegistry());
    try {
      DependencyNode root =
          repositorySystem.collectDependencies(resolverSession, request).getRoot();
      addRequestedVersions(
          root, requestedVersions, Collections.newSetFromMap(new IdentityHashMap<>()), true);
    } catch (DependencyCollectionException e) {
      throw new EnforcerRuleError(
          "Failed to collect the dependency graph of " + project.getId(), e);
    }
  }

  private void addRequestedVersions(
      DependencyNode node,
      Map<String, Set<String>> requestedVersions,
      Set<DependencyNode> visited,
      boolean isRoot) {
    // without conflict resolution the same node can appear under multiple parents (and the
    // graph may contain cycles), so track visited nodes by identity
    if (!visited.add(node)) {
      return;
    }
    if (!isRoot && node.getArtifact() != null) {
      requestedVersions
          .computeIfAbsent(
              node.getArtifact().getGroupId() + ":" + node.getArtifact().getArtifactId(),
              key -> new HashSet<>())
          .add(node.getArtifact().getVersion());
    }
    for (DependencyNode child : node.getChildren()) {
      addRequestedVersions(child, requestedVersions, visited, false);
    }
  }

  /**
   * Direct dependencies declared without a version (in the raw POM, before inheritance and
   * interpolation) get their version from {@code dependencyManagement}, so a pin backing such a
   * declaration is in active use.
   */
  private static void addVersionlessCoordinates(
      MavenProject project, Set<String> versionlessCoordinates) {
    Model original = project.getOriginalModel();
    if (original == null) {
      return;
    }
    original.getDependencies().stream()
        .filter(dependency -> dependency.getVersion() == null)
        .map(Pins::coordinate)
        .forEach(versionlessCoordinates::add);
  }

  private record ReactorRequests(
      Map<String, Set<String>> requestedVersions, Set<String> versionlessCoordinates) {}
}
