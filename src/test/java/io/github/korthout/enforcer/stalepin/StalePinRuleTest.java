package io.github.korthout.enforcer.stalepin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.apache.maven.enforcer.rule.api.AbstractEnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerLogger;
import org.junit.jupiter.api.Test;

class StalePinRuleTest {

  @Test
  void executeCompletesAndLogsOnce() {
    AbstractEnforcerRule rule = new StalePinRule();
    EnforcerLogger mockLog = mock(EnforcerLogger.class);
    rule.setLog(mockLog);

    assertDoesNotThrow(rule::execute);

    verify(mockLog, times(1)).info("not yet implemented");
  }
}
