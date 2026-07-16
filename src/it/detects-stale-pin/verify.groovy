def log = new File(basedir, 'build.log').text

// the build must have failed because of the stale pin, not for some other reason
assert log.contains('stale dependencyManagement')
assert log.contains('org.apache.commons:commons-lang3')
assert log.contains('BUILD FAILURE')
