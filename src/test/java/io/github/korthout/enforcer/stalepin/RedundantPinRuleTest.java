package io.github.korthout.enforcer.stalepin;

import static io.github.korthout.enforcer.stalepin.Fixtures.directDependency;
import static io.github.korthout.enforcer.stalepin.Fixtures.node;
import static io.github.korthout.enforcer.stalepin.Fixtures.pin;
import static io.github.korthout.enforcer.stalepin.Fixtures.project;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.maven.enforcer.rule.api.EnforcerRuleError;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DependencyNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RedundantPinRuleTest {

  private static final String PROJECT_MODEL_ID = "com.acme:app:1.0.0";

  private final DefaultRepositorySystemSession repositorySession =
      new DefaultRepositorySystemSession();
  private final MavenSession session = Fixtures.session(repositorySession);
  private final FakeRepositorySystem repositorySystem = new FakeRepositorySystem();

  private RedundantPinRule rule;

  @BeforeEach
  void setUp() {
    repositorySession.setArtifactTypeRegistry(typeId -> null);
    // the build's session resolves conflicts; the rule must collect without doing so
    repositorySession.setDependencyGraphTransformer((node, context) -> node);
    rule = new RedundantPinRule(session, repositorySystem);
    rule.setLog(new NoopEnforcerLogger());
  }

  @Test
  void failsWhenEveryDependencyRequestsThePinnedVersion() throws Exception {
    MavenProject project =
        project(PROJECT_MODEL_ID, pin("org.ow2.asm", "asm", "9.7.1", PROJECT_MODEL_ID));
    currentProjects(project);
    graphForAnyProject(
        node("com.acme", "lib-a", "1.0.0", node("org.ow2.asm", "asm", "9.7.1")),
        node("com.acme", "lib-b", "1.0.0", node("org.ow2.asm", "asm", "9.7.1")));

    EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);

    assertTrue(exception.getMessage().contains("redundant"), exception.getMessage());
    assertTrue(exception.getMessage().contains("org.ow2.asm:asm"), exception.getMessage());
    assertTrue(exception.getMessage().contains("9.7.1"), exception.getMessage());
  }

  @Test
  void passesWhenDependenciesStillRequestDifferentVersions() throws Exception {
    MavenProject project =
        project(PROJECT_MODEL_ID, pin("org.ow2.asm", "asm", "9.10.1", PROJECT_MODEL_ID));
    currentProjects(project);
    graphForAnyProject(
        node("com.acme", "lib-a", "1.0.0", node("org.ow2.asm", "asm", "9.7.1")),
        node("com.acme", "lib-b", "1.0.0", node("org.ow2.asm", "asm", "9.10.1")));

    assertDoesNotThrow(rule::execute);
  }

  @Test
  void passesWhenSiblingModulesRequestDifferentVersions() throws Exception {
    MavenProject current =
        project(PROJECT_MODEL_ID, pin("org.ow2.asm", "asm", "9.10.1", PROJECT_MODEL_ID));
    MavenProject sibling = project("com.acme:sibling:1.0.0");
    sibling.getModel().addDependency(directDependency("com.acme", "sibling-dep", "1.0.0"));
    currentProjects(current, sibling);

    // the current project's graph requests one version, the sibling's another: still a conflict
    repositorySystem.onCollectDependencies(
        (repoSession, request) -> {
          DependencyNode root =
              request.getDependencies().isEmpty()
                  ? node(
                      "com.acme",
                      "graph-root",
                      "1.0.0",
                      node("com.acme", "lib-a", "1.0.0", node("org.ow2.asm", "asm", "9.7.1")))
                  : node(
                      "com.acme",
                      "graph-root",
                      "1.0.0",
                      node(
                          "com.acme",
                          "sibling-dep",
                          "1.0.0",
                          node("org.ow2.asm", "asm", "9.10.1")));
          return new CollectResult(request).setRoot(root);
        });

    assertDoesNotThrow(rule::execute);
  }

  @Test
  void passesWhenPinOverridesTheOnlyRequestedVersion() throws Exception {
    MavenProject project =
        project(PROJECT_MODEL_ID, pin("org.ow2.asm", "asm", "9.10.1", PROJECT_MODEL_ID));
    currentProjects(project);
    graphForAnyProject(node("com.acme", "lib", "1.0.0", node("org.ow2.asm", "asm", "9.7.1")));

    // the pin forces a version nothing requests, which may be deliberate (e.g. a CVE fix)
    assertDoesNotThrow(rule::execute);
  }

  @Test
  void passesWhenNothingDependsOnThePinnedCoordinates() throws Exception {
    MavenProject project =
        project(PROJECT_MODEL_ID, pin("org.ow2.asm", "asm", "9.7.1", PROJECT_MODEL_ID));
    currentProjects(project);
    graphForAnyProject(node("com.acme", "lib", "1.0.0", node("org.other", "thing", "2.0.0")));

    // an entirely unused pin is the stalePin rule's finding, not this rule's
    assertDoesNotThrow(rule::execute);
  }

  @Test
  void passesWhenPinManagesVersionlessDirectDependency() throws Exception {
    MavenProject project =
        project(PROJECT_MODEL_ID, pin("org.ow2.asm", "asm", "9.7.1", PROJECT_MODEL_ID));
    Dependency versionless = directDependency("org.ow2.asm", "asm", null);
    project.getOriginalModel().addDependency(versionless);
    currentProjects(project);
    // the effective direct dependency carries the pinned version, so the graph requests it
    graphForAnyProject(node("org.ow2.asm", "asm", "9.7.1"));

    assertDoesNotThrow(rule::execute);
  }

  @Test
  void collectsWithoutPinsAndWithoutConflictResolution() throws Exception {
    MavenProject project =
        project(PROJECT_MODEL_ID, pin("org.ow2.asm", "asm", "9.7.1", PROJECT_MODEL_ID));
    project.getModel().addDependency(directDependency("com.acme", "lib", "1.0.0"));
    currentProjects(project);

    repositorySystem.onCollectDependencies(
        (repoSession, request) ->
            new CollectResult(request)
                .setRoot(node("com.acme", "lib", "1.0.0", node("org.ow2.asm", "asm", "9.7.1"))));

    assertThrows(EnforcerRuleException.class, rule::execute);

    assertTrue(repositorySystem.collectedRequests().get(0).getManagedDependencies().isEmpty());
    assertNull(repositorySystem.collectedSessions().get(0).getDependencyGraphTransformer());
  }

  @Test
  void ignoresPinsInheritedFromParent() {
    MavenProject project =
        project(PROJECT_MODEL_ID, pin("org.unused", "artifact", "1.0.0", "com.acme:parent:1.0.0"));
    currentProjects(project);

    assertDoesNotThrow(rule::execute);
    assertTrue(repositorySystem.collectedRequests().isEmpty());
  }

  @Test
  void ignoresBomImports() {
    Dependency bomImport = pin("com.acme", "bom", "1.0.0", PROJECT_MODEL_ID);
    bomImport.setType("pom");
    bomImport.setScope("import");
    MavenProject project = project(PROJECT_MODEL_ID, bomImport);
    currentProjects(project);

    assertDoesNotThrow(rule::execute);
    assertTrue(repositorySystem.collectedRequests().isEmpty());
  }

  @Test
  void passesWhenPomDeclaresNoDependencyManagement() {
    MavenProject project = project(PROJECT_MODEL_ID);
    currentProjects(project);

    assertDoesNotThrow(rule::execute);
    assertTrue(repositorySystem.collectedRequests().isEmpty());
  }

  @Test
  void collectsRequestedVersionsOnlyOncePerBuild() throws Exception {
    MavenProject project =
        project(PROJECT_MODEL_ID, pin("org.ow2.asm", "asm", "9.10.1", PROJECT_MODEL_ID));
    currentProjects(project);
    graphForAnyProject(node("org.ow2.asm", "asm", "9.7.1"));

    assertDoesNotThrow(rule::execute);
    assertDoesNotThrow(rule::execute);

    // the reactor-wide requested versions are cached in the resolver session, so the single
    // reactor project's graph is collected exactly once even across executions
    assertEquals(1, repositorySystem.collectedRequests().size());
  }

  @Test
  void failsWithErrorWhenDependencyCollectionFails() throws Exception {
    MavenProject project =
        project(PROJECT_MODEL_ID, pin("org.ow2.asm", "asm", "9.7.1", PROJECT_MODEL_ID));
    currentProjects(project);
    repositorySystem.onCollectDependencies(
        (repoSession, request) -> {
          throw new DependencyCollectionException(new CollectResult(new CollectRequest()));
        });

    EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);

    assertInstanceOf(EnforcerRuleError.class, exception);
  }

  private void currentProjects(MavenProject current, MavenProject... siblings) {
    List<MavenProject> projects = new ArrayList<>();
    projects.add(current);
    projects.addAll(Arrays.asList(siblings));
    session.setProjects(projects); // also makes the first project the current one
  }

  private void graphForAnyProject(DependencyNode... graph) {
    repositorySystem.onCollectDependencies(
        (repoSession, request) ->
            new CollectResult(request).setRoot(node("com.acme", "graph-root", "1.0.0", graph)));
  }
}
