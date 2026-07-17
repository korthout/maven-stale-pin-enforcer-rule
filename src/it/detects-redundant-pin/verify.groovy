def log = new File(basedir, 'build.log').text

// the build must have failed because of the redundant pin, not for some other reason
assert log.contains('redundant dependencyManagement')
assert log.contains('org.slf4j:slf4j-api')
assert log.contains('BUILD FAILURE')
