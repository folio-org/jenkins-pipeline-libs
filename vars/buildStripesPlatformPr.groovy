#!/usr/bin/env groovy

/*
 * Build stripes platform for PR
 */


def call(String okapiUrl, String tenant) {
  def foliociLib = new org.folio.foliociCommands()

  // remove node_modules from previous 'yarn install'
  sh "rm -rf ../project/node_modules"

  sh "yarn add ../project"
  sh "yarn upgrade $env.npmName"

  sh 'yarn list --pattern @folio'

  // generate platform mod descriptors
  sh 'yarn build-module-descriptors --strict'

  // build webpack with stripes-cli. See STCLI-66 re: PREFIX env
  sh "yarn build --okapi $okapiUrl --tenant $tenant ./bundle" 

  // generate tenant stripes module list
  writeFile file: 'md2install.sh', text: libraryResource('org/folio/md2install.sh')
  sh 'chmod +x md2install.sh'
  sh "./md2install.sh --outputfile stripes-install-${env.CHANGE_ID}.json ./ModuleDescriptors"
  sh 'rm -f md2install.sh'

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
