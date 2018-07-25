#!/usr/bin/env groovy


/*
 * Archive a generated webpack bundle and publish in Jenkins
 * 
 */

def call(String webpackDir) {
 
  sh "mkdir -p artifacts/webpack"
  sh "tar cf artifacts/webpack/stripes_webpack.tar $webpackDir"
  sh "bzip2 artifacts/webpack/stripes_webpack.tar"

  publishHTML([allowMissing: false, alwaysLinkToLastBuild: false,
               keepAll: true, reportDir: 'artifacts/webpack',
               reportFiles: 'stripes_webpack.tar.bz2',
               reportName: "Generated Webpack Bundle",
               reportTitles: "Generated Webpack Bundle"]) 
}
