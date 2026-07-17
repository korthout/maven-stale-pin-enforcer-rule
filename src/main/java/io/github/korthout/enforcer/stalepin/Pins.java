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
   * one line per offending pin (with its position in the POM file when tracked), and the rule's
   * explanation of why the pins should go.
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
            .map(
                pin ->
                    "  - "
                        + coordinate(pin)
                        + " (pinned to "
                        + pin.getVersion()
                        + ")"
                        + position(pin))
            .toList());
    lines.addAll(explanation);
    return lines.stream().collect(Collectors.joining(System.lineSeparator()));
  }

  /**
   * The pin's position in its POM file, as {@code " at pom.xml:line"} or {@code " at
   * pom.xml:line:column"}, so users don't have to search a large dependencyManagement block for the
   * flagged coordinates. Empty when the model was built without location tracking (the same
   * defensive fallback as {@link #declaredIn}). Maven's model reader records line and column
   * together, so the column is normally present, but it is checked separately in case a location
   * carries only a line.
   */
  private static String position(Dependency pin) {
    InputLocation location = pin.getLocation("");
    if (location == null || location.getLineNumber() < 1) {
      return "";
    }
    StringBuilder position =
        new StringBuilder(" at ")
            .append(fileName(location.getSource()))
            .append(':')
            .append(location.getLineNumber());
    if (location.getColumnNumber() > 0) {
      position.append(':').append(location.getColumnNumber());
    }
    return position.toString();
  }

  /**
   * The file name of the POM the location's source points at. {@link #declaredIn} guarantees the
   * pins in a failure message live in the current project's own POM file, so the file name alone
   * identifies the file; when the source carries no path, that file can only be the project's
   * {@code pom.xml}.
   */
  private static String fileName(InputSource source) {
    String path = source == null ? null : source.getLocation();
    if (path == null || path.isEmpty()) {
      return "pom.xml";
    }
    int separator = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
    return separator < 0 ? path : path.substring(separator + 1);
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
