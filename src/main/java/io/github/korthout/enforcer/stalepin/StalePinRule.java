package io.github.korthout.enforcer.stalepin;

import jakarta.inject.Named;
import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;

/**
 * Flags {@code dependencyManagement} entries (pins) no longer referenced by any dependency in the
 * build.
 */
@Named("stalePin")
public class StalePinRule extends AbstractEnforcerRule {

  // TODO: implement stale-pin detection (see issue #11): collect the pins declared in
  // dependencyManagement, collect the dependencies actually declared (directly and transitively,
  // where relevant) by the project, and report any pin whose groupId/artifactId is no longer
  // referenced by a declared dependency.

  // NOTE: detecting stale pins across a multi-module reactor build may require visibility into
  // the reactor-wide MavenSession (e.g. to see dependencies declared in sibling modules). Whether
  // and how to obtain that session is a design question deferred to the detection-algorithm work
  // in issue #11; this stub does not wire up a session.

  @Override
  public void execute() throws EnforcerRuleException {
    getLog().info("not yet implemented");
  }
}
