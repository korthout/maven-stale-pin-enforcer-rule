package io.github.korthout.enforcer.stalepin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleError;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.InputLocation;
import org.apache.maven.model.InputSource;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DependencyNode;

/**
 * Flags {@code dependencyManagement} entries (pins) no longer referenced by any dependency in the
 * build.
 *
 * <p>For the module the rule executes in, it considers every {@code dependencyManagement} entry
 * declared in that module's own POM file (entries inherited from a parent or flattened in from an
 * imported BOM are not this module's pins and are skipped). Each pin's {@code groupId:artifactId}
 * is then matched against the dependency graphs of <em>all</em> projects in the reactor — direct
 * and transitive dependencies alike — and the rule fails for every pin that no dependency anywhere
 * in the build resolves to.
 *
 * <p>The rule collects each reactor project's dependency graph itself (instead of reading the
 * project's resolved artifacts) because it typically runs in the {@code validate} phase, before
 * sibling modules have resolved their dependencies. The reactor-wide result is cached in the
 * resolver session, so the graphs are only collected once per build regardless of module count.
 */
@Named("stalePin")
public class StalePinRule extends AbstractEnforcerRule {

  private static final String USED_COORDINATES_CACHE_KEY =
      StalePinRule.class.getName() + ".usedCoordinates";

  private final MavenSession session;
  private final RepositorySystem repositorySystem;

  @Inject
  public StalePinRule(MavenSession session, RepositorySystem repositorySystem) {
    this.session = Objects.requireNonNull(session);
    this.repositorySystem = Objects.requireNonNull(repositorySystem);
  }

  @Override
  public void execute() throws EnforcerRuleException {
    MavenProject project = session.getCurrentProject();
    List<Dependency> pins = declaredPins(project);
    if (pins.isEmpty()) {
      getLog().debug("No dependencyManagement entries declared in this POM, nothing to check");
      return;
    }

    Set<String> used = usedCoordinates();
    List<Dependency> stale = pins.stream().filter(pin -> !used.contains(coordinate(pin))).toList();
    getLog()
        .debug(
            "Checked %d pin(s) against %d used coordinate(s), found %d stale"
                .formatted(pins.size(), used.size(), stale.size()));

    if (!stale.isEmpty()) {
      throw new EnforcerRuleException(staleMessage(project, stale));
    }
  }

  /**
   * Returns the {@code dependencyManagement} entries declared in the project's own POM file.
   *
   * <p>Works on the effective model (so properties in coordinates are interpolated), but uses
   * Maven's input-location tracking to keep only entries whose declaration lives in this POM:
   * entries inherited from a parent are checked when the rule runs in the declaring module, and
   * entries contributed by an imported BOM are not pins the user wrote at all.
   */
  private List<Dependency> declaredPins(MavenProject project) {
    DependencyManagement dependencyManagement = project.getModel().getDependencyManagement();
    if (dependencyManagement == null) {
      return List.of();
    }
    String modelId =
        project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion();
    return dependencyManagement.getDependencies().stream()
        .filter(dependency -> !"import".equals(dependency.getScope()))
        .filter(dependency -> declaredIn(dependency, modelId))
        .toList();
  }

  private boolean declaredIn(Dependency dependency, String modelId) {
    InputLocation location = dependency.getLocation("");
    InputSource source = location == null ? null : location.getSource();
    if (source == null) {
      // Without location tracking we cannot tell where the entry comes from. Treat it as
      // declared here rather than silently checking nothing; regular Maven builds always
      // track locations, so this is a defensive fallback only.
      return true;
    }
    return modelId.equals(source.getModelId());
  }

  /**
   * Returns the {@code groupId:artifactId} of every node in the dependency graphs of all reactor
   * projects. Computed once per build and cached in the resolver session.
   */
  @SuppressWarnings("unchecked")
  private Set<String> usedCoordinates() throws EnforcerRuleException {
    Object cached = session.getRepositorySession().getData().get(USED_COORDINATES_CACHE_KEY);
    if (cached != null) {
      return (Set<String>) cached;
    }
    Set<String> used = new HashSet<>();
    for (MavenProject project : session.getProjects()) {
      collectGraph(project, used);
    }
    session.getRepositorySession().getData().set(USED_COORDINATES_CACHE_KEY, used);
    return used;
  }

  private void collectGraph(MavenProject project, Set<String> used) throws EnforcerRuleException {
    ArtifactTypeRegistry typeRegistry = session.getRepositorySession().getArtifactTypeRegistry();

    CollectRequest request = new CollectRequest();
    Artifact projectArtifact = project.getArtifact();
    if (projectArtifact != null) {
      request.setRootArtifact(RepositoryUtils.toArtifact(projectArtifact));
    }
    request.setDependencies(
        project.getDependencies().stream()
            .map(dependency -> RepositoryUtils.toDependency(dependency, typeRegistry))
            .toList());
    if (project.getDependencyManagement() != null) {
      request.setManagedDependencies(
          project.getDependencyManagement().getDependencies().stream()
              .map(dependency -> RepositoryUtils.toDependency(dependency, typeRegistry))
              .toList());
    }
    request.setRepositories(project.getRemoteProjectRepositories());

    try {
      DependencyNode root =
          repositorySystem.collectDependencies(session.getRepositorySession(), request).getRoot();
      addCoordinates(root, used, true);
    } catch (DependencyCollectionException e) {
      throw new EnforcerRuleError(
          "Failed to collect the dependency graph of " + project.getId(), e);
    }
  }

  private void addCoordinates(DependencyNode node, Set<String> used, boolean isRoot) {
    if (!isRoot && node.getArtifact() != null) {
      used.add(node.getArtifact().getGroupId() + ":" + node.getArtifact().getArtifactId());
    }
    for (DependencyNode child : node.getChildren()) {
      addCoordinates(child, used, false);
    }
  }

  private static String coordinate(Dependency dependency) {
    return dependency.getGroupId() + ":" + dependency.getArtifactId();
  }

  private static String staleMessage(MavenProject project, List<Dependency> stale) {
    List<String> lines = new ArrayList<>();
    lines.add(
        "Found "
            + stale.size()
            + " stale dependencyManagement "
            + (stale.size() == 1 ? "entry" : "entries")
            + " in "
            + project.getId()
            + ":");
    lines.addAll(
        stale.stream()
            .map(pin -> "  - " + coordinate(pin) + " (pinned to " + pin.getVersion() + ")")
            .toList());
    lines.add(
        "No dependency in this build resolves to these coordinates (directly or transitively),");
    lines.add("so the pins have no effect. Remove them from dependencyManagement.");
    return lines.stream().collect(Collectors.joining(System.lineSeparator()));
  }
}
