#!/usr/bin/env groovy


/*
 * Publish NPM Package to either FOLIO 'snapshot' repository (npm-folioci)  
 * or FOLIO 'release' repository (npm-folio)
 * 
 * Examples:
 *
 * publishNPM('npm-folio')
 * publishNPM('npm-folioci')
 * }
 * 
 */

def call(String repository) {

  def npmConfig 

  if (repository == 'npm-folio') { 
    npmConfig = 'jenkins-npm-folio'
  }
  else if (repository == 'npm-folioci') {
    npmConfig = 'jenkins-npm-folioci' 
  }
  else {
    error ("Unsupported parameter")
  }
  
  withCredentials([string(credentialsId: 'jenkins-npm-folioci',variable: 'NPM_TOKEN')]) {
    withNPM(npmrcConfig: npmConfig) {
      // clean up commonly generated artifacts before publishing
      sh 'rm -rf ci artifacts output' 
      sh 'npm publish'
    }
  }
}
