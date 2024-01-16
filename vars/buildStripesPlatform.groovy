#!/usr/bin/env groovy

/*
 * Build stripes platform.
 */


def call(String okapiUrl, String tenant, String branch='') {

  def foliociLib = new org.folio.foliociCommands()

  if (!(branch =~ /^[rR]\d-\d{4}(-([rR][cC]|hotfix-\d))?$/) && !(branch =~ /\bmaster\b/)) {
    sh 'rm -f yarn.lock'
  }

  sh 'yarn install --frozen-lockfile'

  // publish generated yarn.lock for possible debugging
  sh 'mkdir -p ci'
  sh 'cp yarn.lock ci/yarn.lock'
  sh 'bzip2 ci/yarn.lock'

  publishHTML([allowMissing: false, alwaysLinkToLastBuild: false,
               keepAll: true, reportDir: 'ci',
               reportFiles: 'yarn.lock.bz2',
               reportName: "Yarn Lock",
               reportTitles: "Yarn Lock"])

  // list yarn FOLIO deps
  sh 'yarn list --pattern @folio'

  // generate platform mod descriptors
  // foliociLib.genStripesModDescriptors("${env.WORKSPACE}/artifacts/md")
  sh 'yarn build-module-descriptors --strict'

  // build webpack with stripes-cli. See STCLI-66 re: PREFIX env
  sh "yarn build --okapi $okapiUrl --tenant $tenant ./output"

  // generate tenant stripes module list
  writeFile file: 'md2install.sh', text: libraryResource('org/folio/md2install.sh')
  sh 'chmod +x md2install.sh'
  sh './md2install.sh --outputfile stripes-install.json ./ModuleDescriptors'
  sh 'rm -f md2install.sh'

  // publish stripes bundle for debugging
  // archiveWebpack('./output')
  // end stage
}
