package io.github.korthout.enforcer.stalepin;

import java.util.ArrayList;
import java.util.Arrays;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.InputLocation;
import org.apache.maven.model.InputSource;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.DependencyNode;

/** Builders for the Maven model and dependency graph objects the rule tests exercise. */
final class Fixtures {

  private Fixtures() {}

  static MavenProject project(String modelId, Dependency... pins) {
    String[] coordinates = modelId.split(":");
    Model model = new Model();
    model.setGroupId(coordinates[0]);
    model.setArtifactId(coordinates[1]);
    model.setVersion(coordinates[2]);
    if (pins.length > 0) {
      DependencyManagement dependencyManagement = new DependencyManagement();
      for (Dependency pin : pins) {
        dependencyManagement.addDependency(pin);
      }
      model.setDependencyManagement(dependencyManagement);
    }
    MavenProject project = new MavenProject(model);
    project.setOriginalModel(new Model());
    return project;
  }

  /** A dependencyManagement entry whose declaration location points at the given model. */
  static Dependency pin(
      String groupId, String artifactId, String version, String declaringModelId) {
    Dependency dependency = directDependency(groupId, artifactId, version);
    InputSource source = new InputSource();
    source.setModelId(declaringModelId);
    dependency.setLocation("", new InputLocation(1, 1, source));
    return dependency;
  }

  static Dependency directDependency(String groupId, String artifactId, String version) {
    Dependency dependency = new Dependency();
    dependency.setGroupId(groupId);
    dependency.setArtifactId(artifactId);
    dependency.setVersion(version);
    return dependency;
  }

  static DependencyNode node(
      String groupId, String artifactId, String version, DependencyNode... children) {
    DefaultDependencyNode node =
        new DefaultDependencyNode(
            new org.eclipse.aether.graph.Dependency(
                new DefaultArtifact(groupId + ":" + artifactId + ":" + version), "compile"));
    node.setChildren(new ArrayList<>(Arrays.asList(children)));
    return node;
  }
}
