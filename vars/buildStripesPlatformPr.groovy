#!/usr/bin/env groovy

/*
 * Build stripes platform. 
 */


def call(String okapiUrl, String tenant) {

  // remove node_modules from previous 'yarn install'
  sh "rm -rf ${env.WORKSPACE}/project/node_modules"

  sh "yarn add ${env.WORKSPACE}/project"
  sh "yarn upgrade $env.npmName"

  sh 'yarn list --pattern @folio'

  // generate platform mod descriptors
  sh 'mkdir -p artifacts/md'
  foliociLib.genStripesModDescriptors("artifacts/md")

  // build webpack with stripes-cli. See STCLI-66 re: PREFIX env
  sh "yarn build --okapi $okapiUrl --tenant $tenant ./bundle" 

  // publish generated yarn.lock for possible debugging
  sh 'mkdir -p artifacts/yarn/'
  sh 'cp yarn.lock artifacts/yarn/yarnLock.html'

/*
 * publishHTML([allowMissing: false, alwaysLinkToLastBuild: false,
 *              keepAll: true, reportDir: 'artifacts/yarn',
 *              reportFiles: 'yarnLock.html',
 *              reportName: "YarnLock",
 *              reportTitles: "YarnLock"])
 *
 * // publish stripes bundle for debugging
 * archiveWebpack('./bundle')
 */
} 
