package io.github.korthout.enforcer.stalepin;

import java.util.function.Supplier;
import org.apache.maven.enforcer.rule.api.EnforcerLogger;

/** Discards all log output; the tests assert on exceptions, not on logging. */
final class NoopEnforcerLogger implements EnforcerLogger {

  @Override
  public void warnOrError(CharSequence message) {}

  @Override
  public void warnOrError(Supplier<CharSequence> message) {}

  @Override
  public boolean isDebugEnabled() {
    return false;
  }

  @Override
  public void debug(CharSequence message) {}

  @Override
  public void debug(Supplier<CharSequence> message) {}

  @Override
  public boolean isInfoEnabled() {
    return false;
  }

  @Override
  public void info(CharSequence message) {}

  @Override
  public void info(Supplier<CharSequence> message) {}

  @Override
  public boolean isWarnEnabled() {
    return false;
  }

  @Override
  public void warn(CharSequence message) {}

  @Override
  public void warn(Supplier<CharSequence> message) {}

  @Override
  public boolean isErrorEnabled() {
    return false;
  }

  @Override
  public void error(CharSequence message) {}

  @Override
  public void error(Supplier<CharSequence> message) {}
}
