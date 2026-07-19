def log = new File(basedir, 'build.log').text

// the build must have failed because of the stale pin, not for some other reason
assert log.contains('stale dependencyManagement')
assert log.contains('org.apache.commons:commons-lang3')
// the message points at the pin's <dependency> element in the pom (line 19, column 7)
assert log.contains('at pom.xml:19:7')
assert log.contains('BUILD FAILURE')
