#!/usr/bin/env groovy


/*
 * Archive a generated webpack bundle and publish in Jenkins
 * 
 */

def call(String webpackDir) {

  def archiveVer = "${env.projectName}.${env.BRANCH_NAME}.${env.BUILD_NUMBER}"
 
  sh "mkdir -p artifacts/webpack"
  sh "tar cf artifacts/webpack/stripes-${archiveVer}.tar $webpackDir"
  sh "bzip2 artifacts/webpack/stripes-${archiveVer}.tar"

  publishHTML([allowMissing: false, alwaysLinkToLastBuild: false,
               keepAll: true, reportDir: 'artifacts/webpack',
               reportFiles: "stripes-${archiveVer}.tar.bz2",
               reportName: "Generated Webpack Bundle",
               reportTitles: "Generated Webpack Bundle"]) 
}
