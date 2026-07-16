def log = new File(basedir, 'build.log').text

// both rules must actually have executed and passed, in both modules
assert log.count('StalePinRule(stalePin) passed') == 2
assert log.count('RedundantPinRule(redundantPin) passed') == 2
assert log.contains('BUILD SUCCESS')
