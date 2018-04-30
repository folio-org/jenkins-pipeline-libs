#!/usr/bin/env groovy

def call(String okapiUrl, String tenant) {

  def stripesPlatform = 'folio-testing-platform'
  
  stage('Build Stripes Platform') {

    dir("${env.WORKSPACE}") {
      sh "git clone https://github.com/folio-org/folio-org/${stripesPlatform}"
    }

    dir("${env.WORKSPACE}/${stripesPlatform}") {
      if (env.CHANGE_ID) { 
        sh "yarn link $env.npm_name"
        sh 'rm -f yarn.lock'
      }

      sh 'yarn install'
   
      // generate mod descriptors with '--strict' flag for dependencies
      sh 'yarn postinstall --strict'

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
