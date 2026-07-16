package io.github.korthout.enforcer.stalepin;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.InputLocation;
import org.apache.maven.model.InputSource;
import org.apache.maven.project.MavenProject;

/** How the rules in this package look at {@code dependencyManagement} entries (pins). */
final class Pins {

  private Pins() {}

  /**
   * Returns the {@code dependencyManagement} entries declared in the project's own POM file.
   *
   * <p>Works on the effective model (so properties in coordinates are interpolated), but uses
   * Maven's input-location tracking to keep only entries whose declaration lives in this POM:
   * entries inherited from a parent are checked when the rule runs in the declaring module, and
   * entries contributed by an imported BOM are not pins the user wrote at all.
   */
  static List<Dependency> declaredIn(MavenProject project) {
    DependencyManagement dependencyManagement = project.getModel().getDependencyManagement();
    if (dependencyManagement == null) {
      return List.of();
    }
    String modelId =
        project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion();
    return dependencyManagement.getDependencies().stream()
        .filter(dependency -> !"import".equals(dependency.getScope()))
        .filter(dependency -> isDeclaredIn(dependency, modelId))
        .toList();
  }

  /** The {@code groupId:artifactId} key on which the rules match pins against dependencies. */
  static String coordinate(Dependency dependency) {
    return dependency.getGroupId() + ":" + dependency.getArtifactId();
  }

  /**
   * Builds the failure message shared by the rules: a headline naming the finding and the project,
   * one line per offending pin, and the rule's explanation of why the pins should go.
   */
  static String failureMessage(
      MavenProject project, List<Dependency> pins, String finding, List<String> explanation) {
    List<String> lines = new ArrayList<>();
    lines.add(
        "Found "
            + pins.size()
            + " "
            + finding
            + " dependencyManagement "
            + (pins.size() == 1 ? "entry" : "entries")
            + " in "
            + project.getId()
            + ":");
    lines.addAll(
        pins.stream()
            .map(pin -> "  - " + coordinate(pin) + " (pinned to " + pin.getVersion() + ")")
            .toList());
    lines.addAll(explanation);
    return lines.stream().collect(Collectors.joining(System.lineSeparator()));
  }

  private static boolean isDeclaredIn(Dependency dependency, String modelId) {
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
}
