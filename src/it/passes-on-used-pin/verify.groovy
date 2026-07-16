def log = new File(basedir, 'build.log').text

// the rule must actually have executed and passed, in both modules
assert log.count('StalePinRule(stalePin) passed') == 2
assert log.contains('BUILD SUCCESS')
