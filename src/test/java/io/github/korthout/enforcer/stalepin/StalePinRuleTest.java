package io.github.korthout.enforcer.stalepin;

import static io.github.korthout.enforcer.stalepin.Fixtures.directDependency;
import static io.github.korthout.enforcer.stalepin.Fixtures.node;
import static io.github.korthout.enforcer.stalepin.Fixtures.pin;
import static io.github.korthout.enforcer.stalepin.Fixtures.project;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DependencyNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StalePinRuleTest {

  private static final String PROJECT_MODEL_ID = "com.acme:app:1.0.0";

  private final MavenSession session = mock(MavenSession.class);
  private final RepositorySystem repositorySystem = mock(RepositorySystem.class);
  private final DefaultRepositorySystemSession repositorySession =
      new DefaultRepositorySystemSession();

  private StalePinRule rule;

  @BeforeEach
  void setUp() {
    repositorySession.setArtifactTypeRegistry(typeId -> null);
    when(session.getRepositorySession()).thenReturn(repositorySession);
    rule = new StalePinRule(session, repositorySystem);
    rule.setLog(mock(EnforcerLogger.class));
  }

  @Test
  void failsOnPinThatNoDependencyResolvesTo() throws Exception {
    MavenProject project =
        project(PROJECT_MODEL_ID, pin("org.ow2.asm", "asm", "9.7.1", PROJECT_MODEL_ID));
    currentProjects(project);
    graphForAnyProject(node("com.acme", "lib", "1.0.0", node("org.other", "thing", "2.0.0")));

    EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);

    assertTrue(exception.getMessage().contains("org.ow2.asm:asm"), exception.getMessage());
    assertTrue(exception.getMessage().contains("9.7.1"), exception.getMessage());
  }

  @Test
  void messageIncludesPinPositionInPomFile() throws Exception {
    // the parser records the position just past the <dependency> start tag: for an element
    // starting at column 7 that is column 19, and the message must point back at column 7
    Dependency pin = pin("org.ow2.asm", "asm", "9.7.1", PROJECT_MODEL_ID, 42, 19);
    pin.getLocation("").getSource().setLocation("/workspace/app/pom.xml");
    MavenProject project = project(PROJECT_MODEL_ID, pin);
    currentProjects(project);
    graphForAnyProject(node("org.other", "thing", "2.0.0"));

    EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);

    assertTrue(
        exception.getMessage().contains("org.ow2.asm:asm (pinned to 9.7.1) at pom.xml:42:7"),
        exception.getMessage());
  }

  @Test
  void messageOmitsColumnWhenNotTracked() throws Exception {
    Dependency pin = pin("org.ow2.asm", "asm", "9.7.1", PROJECT_MODEL_ID, 42, -1);
    MavenProject project = project(PROJECT_MODEL_ID, pin);
    currentProjects(project);
    graphForAnyProject(node("org.other", "thing", "2.0.0"));

    EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);

    // the source carries no file path either, so the name falls back to pom.xml
    assertTrue(
        exception.getMessage().contains("org.ow2.asm:asm (pinned to 9.7.1) at pom.xml:42"),
        exception.getMessage());
    assertFalse(exception.getMessage().contains("pom.xml:42:"), exception.getMessage());
  }

  @Test
  void messageOmitsColumnThatCannotFollowADependencyStartTag() throws Exception {
    // a recorded column within the width of the <dependency> tag cannot be the position past
    // its start tag, so its meaning is unknown and it must not be reported shifted
    Dependency pin = pin("org.ow2.asm", "asm", "9.7.1", PROJECT_MODEL_ID, 42, 5);
    MavenProject project = project(PROJECT_MODEL_ID, pin);
    currentProjects(project);
    graphForAnyProject(node("org.other", "thing", "2.0.0"));

    EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);

    assertTrue(
        exception.getMessage().contains("org.ow2.asm:asm (pinned to 9.7.1) at pom.xml:42"),
        exception.getMessage());
    assertFalse(exception.getMessage().contains("pom.xml:42:"), exception.getMessage());
  }

  @Test
  void messageOmitsPositionWhenLocationIsNotTracked() throws Exception {
    // without location tracking the pin has no InputLocation at all; declaredIn defensively
    // still checks it, and the message simply reports it without a position
    MavenProject project =
        project(PROJECT_MODEL_ID, directDependency("org.ow2.asm", "asm", "9.7.1"));
    currentProjects(project);
    graphForAnyProject(node("org.other", "thing", "2.0.0"));

    EnforcerRuleException exception = assertThrows(EnforcerRuleException.class, rule::execute);

    assertTrue(
        exception.getMessage().contains("org.ow2.asm:asm (pinned to 9.7.1)"),
        exception.getMessage());
    assertFalse(exception.getMessage().contains(" at "), exception.getMessage());
  }

  @Test
  void passesWhenPinMatchesTransitiveDependency() throws Exception {
    MavenProject project =
        project(PROJECT_MODEL_ID, pin("org.ow2.asm", "asm", "9.7.1", PROJECT_MODEL_ID));
    currentProjects(project);
    graphForAnyProject(node("com.acme", "lib", "1.0.0", node("org.ow2.asm", "asm", "9.7.1")));

    assertDoesNotThrow(rule::execute);
  }

  @Test
  void passesWhenPinIsUsedOnlyBySiblingModule() throws Exception {
    MavenProject current =
        project(PROJECT_MODEL_ID, pin("org.ow2.asm", "asm", "9.7.1", PROJECT_MODEL_ID));
    MavenProject sibling = project("com.acme:sibling:1.0.0");
    sibling.getModel().addDependency(directDependency("com.acme", "sibling-dep", "1.0.0"));
    currentProjects(current, sibling);

    // only the sibling declares dependencies; its graph (transitively) contains the pinned
    // coordinates, while the current project's own graph is empty
    when(repositorySystem.collectDependencies(any(), any(CollectRequest.class)))
        .thenAnswer(
            invocation -> {
              CollectRequest request = invocation.getArgument(1);
              DependencyNode root =
                  request.getDependencies().isEmpty()
                      ? node("com.acme", "graph-root", "1.0.0")
                      : node(
                          "com.acme",
                          "graph-root",
                          "1.0.0",
                          node(
                              "com.acme",
                              "sibling-dep",
                              "1.0.0",
                              node("org.ow2.asm", "asm", "9.7.1")));
              return new CollectResult(request).setRoot(root);
            });

    assertDoesNotThrow(rule::execute);
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
  void collectsUsedCoordinatesOnlyOncePerBuild() throws Exception {
    MavenProject project =
        project(PROJECT_MODEL_ID, pin("org.ow2.asm", "asm", "9.7.1", PROJECT_MODEL_ID));
    currentProjects(project);
    graphForAnyProject(node("org.ow2.asm", "asm", "9.7.1"));

    assertDoesNotThrow(rule::execute);
    assertDoesNotThrow(rule::execute);

    // the reactor-wide coordinate set is cached in the resolver session, so the single
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
