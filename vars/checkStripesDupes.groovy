#!/usr/bin/env groovy

/*
 * check for duplicate stripes-* dependencies in yarn.lock
 * 
 * Example:
 *
 * checkYarnLockDupes('yarn.lock')
 * 
 */

def checkStripesDupes(String yarnLockFile) {
  stage('Check yarn.lock for stripes-* duplicates') {
    def yarnLock = new File(yarnLockFile)
    def lines = yarnLock.readLines()
    def stripesPkgs = []
  
    //parse yarn.lock for matches to @folio/stripes-*
    for (line in lines) {
      if (line =~ /^"(@folio\/stripes-.*@).*\d":$/) {
        def matcher = (line =~ /^"(@folio\/stripes-.*)@.*\d":$/)
        stripesPkgs.add(matcher[0][1])
      }
    }
    def duplicates = false //initialize default condition
    // check for duplicates
    stripesPkgs.sort()
    def count = 1 
    while (count < stripesPkgs.size()) {
      if (stripesPkgs[count] == stripesPkgs[count -1]) {
        println stripesPkgs[count] + " is a duplicate"
        sh "yarn why ${stripesPkgs[count]}"
        duplicates = true
      }
      count += 1
    }
  
    if (duplicates == true) {
      def message = "Duplicate stripes-* dependencies found. See ${env.BUILD_URL}"
      // PR
      if (env.CHANGE_ID) {
        // Requires https://github.com/jenkinsci/pipeline-github-plugin
        @NonCPS
        def comment = pullRequest.comment(message) 
      }
      error(message)
    } else {
      echo "No stripes-* duplicates found in yarn.lock"
    }
  } // end stage
}
