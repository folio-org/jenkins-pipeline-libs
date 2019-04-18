#!/usr/bin/env groovy

/*
 * check for duplicate stripes-* dependencies in yarn.lock
 * 
 * Example:
 *
 * checkStripesDupes('yarn.lock')
 * 
 */

def call(String yarnLockFile) {
  stage('checkDupes') {

    // set default condition to failed
    def status = 1
    sh "mkdir -p dupes" 

    sh "grep -oP '^\"\K@folio\/stripes-[^@]*' yarn.lock > dupes/stripes_deps.txt"
    sh "cat stripes_deps | sort | uniq -d > dupes/stripes_duplicates.txt"
    
    status = sh(script:'''
    if [ -s dupes/stripes_duplicates.txt ]
    then
      echo "duplicates found"
      while read dep; do
        yarn why $dep
      done < dupes/stripes_duplicates.txt
      exit 1
    else
      echo "No duplicates found"
      exit 0
    fi
    ''', returnStatus: true)
    
    sh "rm -rf dupes"
  
    if (status != 0) {
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
