def log = new File(basedir, 'build.log').text

// the build must have failed because of the redundant pin, not for some other reason
assert log.contains('redundant dependencyManagement')
assert log.contains('org.slf4j:slf4j-api')
// the message points at the pin's <dependency> element in the pom (line 21); Maven's
// location tracking also records a column, which follows the line as ':<column>'
assert log.contains('at pom.xml:21:')
assert log.contains('BUILD FAILURE')
