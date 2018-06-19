#!/usr/bin/env groovy

/*
 * Build stripes bundle.  Use cases:  'platform' or 'app' only.  PR or non-PR.
 */


def call(String okapiUrl, String tenant, String stripesPlatform = null) {

  stage('Build Stripes') {

    if (stripesPlatform) {
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
          def isYarnLock = sh (script: 'grep "yarn lockfile" yarn.lock > /dev/null', 
                               returnStatus: true)

          if (isYarnLock != 0) { 
            error('unable to fetch yarn.lock for folio-snapshot-stable')
          }
        
          // substitute PR commit for package
          sh "yarn add folio-org/${env.projectName}#${env.CHANGE_ID}/head"
          sh "yarn upgrade $env.npmName"

          // we need to update the version of node_modules/PACKAGE module in package.json
          // since we are getting it from git before we generate a mod descriptor.
          // Format:  VERSION-pr.$env{CHANGE_ID}
          dir("node_modules/${env.npmName}") {
            def gitVersion = sh(returnStdout: true, 
                                script: "jq -r \".version\" package.json").trim()

            sh "npm version ${gitVersion}-pr.${env.CHANGE_ID}"
          }
        }
        else {
          // substitute git commit sha1 for package
          sh 'yarn install'
        }

        // generate mod descriptors with dependencies
        sh 'yarn postinstall --strict'

        // build webpack with stripes-cli. See STCLI-66 re: PREFIX env
        sh "PREFIX=/usr/local/share/.config/yarn " +
           "stripes build --okapi $okapiUrl --tenant $tenant stripes.config.js bundle" 

        // publish generated yarn.lock for possible debugging
        sh 'mkdir -p ci'
        sh 'cp yarn.lock ci/yarnLock.html'
        publishHTML([allowMissing: false, alwaysLinkToLastBuild: false,
                    keepAll: true, reportDir: 'ci',
                    reportFiles: 'yarnLock.html',
                    reportName: "YarnLock",
                    reportTitles: "YarnLock"])
      }
    }
    else { 
      // build in stripes-cli 'app' mode
      sh 'PREFIX=/usr/local/share/.config/yarn stripes build --output=./bundle'
      sh "stripes mod descriptor --full --strict > ${env.projectName}.json"
    }

    // start simple webserver to serve webpack
    withEnv(['JENKINS_NODE_COOKIE=dontkill']) {
      sh 'http-server -p 3000 ./bundle &'
    }

  } // end stage
} 
