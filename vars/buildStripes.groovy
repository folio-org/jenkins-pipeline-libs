#!/usr/bin/env groovy

/*
 * Build stripes platform e.g. 'folio-testing-platform'
 */


def call(String okapiUrl, String tenant) {

  def stripesPlatform = 'folio-testing-platform'
  
  stage('Build Stripes Platform') {

    dir("${env.WORKSPACE}") {
      sh "git clone https://github.com/folio-org/${stripesPlatform}"
    }

    dir("${env.WORKSPACE}/${stripesPlatform}") {
      if (env.CHANGE_ID) { 
        // remove yarn.lock if it exists 
        sh 'rm -f yarn.lock'

        // grab yarn lock from folio-snapshot-stable
        sh 'wget -O yarn.lock http://folio-snapshot-stable.aws.indexdata.com/yarn.lock'

        // check to see we actually have a real yarn.lock
        def isYarnLock = sh (script: 'grep "yarn lockfile" yarn.lock > /dev/null', returnStatus: true)
        if (isYarnLock != 0) { 
          error('unable to fetch yarn.lock for folio-snapshot-stable')
        }
        
        // substitute PR commit for package
        sh "yarn add folio-org/${env.projectName}/#${env.CHANGE_ID}/head"
        sh "yarn upgrade $env.npmName"

        // we need to update the version of node_modules/PACKAGE module in package.json
        // since we are getting it from git before we generate a mod descriptor.
        // Format:  VERSION-pr.$env{CHANGE_ID}
        dir("node_modules/${env.npmName}") {
          def gitVersion = sh(returnStdout: true, script: "jq -r \".version\" package.json").trim()
          sh "npm version ${gitVersion}-pr.${env.CHANGE_ID}"
        }
      }
      else {
        // substitute git commit sha1 for package
        sh "yarn add folio-org/$env.projName}/#${env.gitCommit}"
        sh "yarn upgrade $env.npmName"
      }

      // generate mod descriptors with '--strict' flag for dependencies
      // sh 'yarn postinstall --strict'
      sh 'stripes mod descriptor --configFile stripes.config.js --full --strict ' +
         '> StripesModDescriptors.json'
 

      // build webpack with stripes-cli 
      sh "stripes build --okapi $okapiUrl --tenant $tenant stripes.config.js bundle" 

      // start simple webserver to serve webpack
      withEnv(['JENKINS_NODE_COOKIE=dontkill']) {
        sh 'http-server -p 3000 ./bundle &'
      }

      // publish generated yarn.lock for possible debugging
      sh 'mkdir -p ci'
      sh 'cp yarn.lock ci/yarnLock.html'
      publishHTML([allowMissing: false, alwaysLinkToLastBuild: false,
                   keepAll: true, reportDir: 'ci',
                   reportFiles: 'yarnLock.html',
                   reportName: "${stripesPlatform}YarnLock",  
                   reportTitles: "${stripesPlatform}YarnLock"])
    }
  } // end stage
} 
