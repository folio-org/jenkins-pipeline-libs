#!/usr/bin/env groovy

/*
 * Build stripes platform. 
 */


def call(String okapiUrl, String tenant) {

  def foliociLib = new org.folio.foliociCommands()

  // remove yarn.lock if it exists 
  sh 'rm -f yarn.lock'

  // Disable use of yarn.lock for now
  /* 
   * // grab yarn lock from folio-snapshot-stable
   * sh 'wget -O yarn.lock http://folio-snapshot-stable.aws.indexdata.com/yarn.lock'
   * 
   * // check to see we actually have a real yarn.lock
   * def isYarnLock = sh (script: 'grep "yarn lockfile" yarn.lock > /dev/null', 
   *                     returnStatus: true)
   * 
   * if (isYarnLock != 0) { 
   *   error('unable to fetch yarn.lock for folio-snapshot-stable')
   * }
   */

  sh 'yarn install'

  // generate platform mod descriptors
  foliociLib.genStripesModDescriptors('artifacts/md')

  // build webpack with stripes-cli. See STCLI-66 re: PREFIX env
  sh "yarn build --okapi $okapiUrl --tenant $tenant ./bundle" 

  // start simple webserver to serve webpack
  withEnv(['JENKINS_NODE_COOKIE=dontkill']) {
    sh 'yarn stripes serve --existing-build ./bundle &'
  }
  
  // publish generated yarn.lock for possible debugging
  sh 'mkdir -p ci'
  sh 'cp yarn.lock ci/yarnLock.html'
  publishHTML([allowMissing: false, alwaysLinkToLastBuild: false,
               keepAll: true, reportDir: 'ci',
               reportFiles: 'yarnLock.html',
               reportName: "YarnLock",
               reportTitles: "YarnLock"])

  // publish stripes bundle for debugging
  archiveWebpack('./bundle')
  // end stage
} 
