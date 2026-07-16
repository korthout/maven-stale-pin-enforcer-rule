package io.github.korthout.enforcer.stalepin;

import static io.github.korthout.enforcer.stalepin.Fixtures.directDependency;
import static io.github.korthout.enforcer.stalepin.Fixtures.node;
import static io.github.korthout.enforcer.stalepin.Fixtures.pin;
import static io.github.korthout.enforcer.stalepin.Fixtures.project;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.maven.enforcer.rule.api.EnforcerLogger;
import org.apache.maven.enforcer.rule.api.EnforcerRuleError;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DependencyNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RedundantPinRuleTest {

  private static final String PROJECT_MODEL_ID = "com.acme:app:1.0.0";

  private final MavenSession session = mock(MavenSession.class);
  private final RepositorySystem repositorySystem = mock(RepositorySystem.class);
  private final DefaultRepositorySystemSession repositorySession =
      new DefaultRepositorySystemSession();

  private RedundantPinRule rule;

  @BeforeEach
  void setUp() {
    repositorySession.setArtifactTypeRegistry(typeId -> null);
    // the build's session resolves conflicts; the rule must collect without doing so
    repositorySession.setDependencyGraphTransformer((node, context) -> node);
    when(session.getRepositorySession()).thenReturn(repositorySession);
    rule = new RedundantPinRule(session, repositorySystem);
    rule.setLog(mock(EnforcerLogger.class));
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
    when(repositorySystem.collectDependencies(any(), any(CollectRequest.class)))
        .thenAnswer(
            invocation -> {
              CollectRequest request = invocation.getArgument(1);
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

    List<CollectRequest> requests = new ArrayList<>();
    List<RepositorySystemSession> sessions = new ArrayList<>();
    when(repositorySystem.collectDependencies(any(), any(CollectRequest.class)))
        .thenAnswer(
            invocation -> {
              sessions.add(invocation.getArgument(0));
              CollectRequest request = invocation.getArgument(1);
              requests.add(request);
              return new CollectResult(request)
                  .setRoot(node("com.acme", "lib", "1.0.0", node("org.ow2.asm", "asm", "9.7.1")));
            });

    assertThrows(EnforcerRuleException.class, rule::execute);

    assertTrue(requests.get(0).getManagedDependencies().isEmpty());
    assertNull(sessions.get(0).getDependencyGraphTransformer());
  }

  @Test
  void ignoresPinsInheritedFromParent() {
    MavenProject project =
        project(PROJECT_MODEL_ID, pin("org.unused", "artifact", "1.0.0", "com.acme:parent:1.0.0"));
    currentProjects(project);

    assertDoesNotThrow(rule::execute);
    verifyNoInteractions(repositorySystem);
  }

  @Test
  void ignoresBomImports() {
    Dependency bomImport = pin("com.acme", "bom", "1.0.0", PROJECT_MODEL_ID);
    bomImport.setType("pom");
    bomImport.setScope("import");
    MavenProject project = project(PROJECT_MODEL_ID, bomImport);
    currentProjects(project);

    assertDoesNotThrow(rule::execute);
    verifyNoInteractions(repositorySystem);
  }

  @Test
  void passesWhenPomDeclaresNoDependencyManagement() {
    MavenProject project = project(PROJECT_MODEL_ID);
    currentProjects(project);

    assertDoesNotThrow(rule::execute);
    verifyNoInteractions(repositorySystem);
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
    verify(repositorySystem, times(1)).collectDependencies(any(), any(CollectRequest.class));
  }

  @Test
  void failsWithErrorWhenDependencyCollectionFails() throws Exception {
    MavenProject project =
        project(PROJECT_MODEL_ID, pin("org.ow2.asm", "asm", "9.7.1", PROJECT_MODEL_ID));
    currentProjects(project);
    when(repositorySystem.collectDependencies(any(), any(CollectRequest.class)))
        .thenThrow(new DependencyCollectionException(new CollectResult(new CollectRequest())));

    EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);

    assertInstanceOf(EnforcerRuleError.class, exception);
  }

  private void currentProjects(MavenProject current, MavenProject... siblings) {
    when(session.getCurrentProject()).thenReturn(current);
    List<MavenProject> projects = new ArrayList<>();
    projects.add(current);
    projects.addAll(Arrays.asList(siblings));
    when(session.getProjects()).thenReturn(projects);
  }

  private void graphForAnyProject(DependencyNode... graph) throws DependencyCollectionException {
    when(repositorySystem.collectDependencies(any(), any(CollectRequest.class)))
        .thenAnswer(
            invocation -> {
              CollectRequest request = invocation.getArgument(1);
              DependencyNode root = node("com.acme", "graph-root", "1.0.0", graph);
              return new CollectResult(request).setRoot(root);
            });
  }
}
