#!/usr/bin/env groovy

package org.folio

import jenkins.model.Jenkins
import com.cloudbees.groovy.cps.NonCPS

// Update npm package.json version to "snapshot" version for FOLIO CI
def npmSnapshotVersion() {

  def folioci_npmver = libraryResource('org/folio/folioci_npmver.sh')
  writeFile file: 'folioci_npmver.sh', text: folioci_npmver
  sh 'chmod +x folioci_npmver.sh'
  sh 'npm version `./folioci_npmver.sh`'

}

// get the unscoped module name and version from package.json. 
// Return name:version map.
def npmSimpleNameVersion(String npmPackageFile = 'package.json') {
  
  def simpleNameVersion = [:]
  def json = readJSON(file: npmPackageFile)
  def n = json.name.replaceAll(~/\//, "_")  

  name = n.replaceAll(~/@/, "")  
  version = json.version
  
  simpleNameVersion = [(name):version]
  
  return simpleNameVersion
}

// get base repo/project name
def getProjName() {

  def proj_name = sh(returnStdout: true, 
      script: 'git config remote.origin.url | awk -F \'/\' \'{print $5}\' | sed -e \'s/\\.git//\'').trim()

  return proj_name
}

// update the 'Id' field (for snapshot versions, etc)
def updateModDescrioptorId(String modDescriptor) {

  echo "Update Module Descriptor version to snapshot version"
  sh "mv $modDescriptor ${modDescriptor}.tmp"
  sh """
  jq '.id |= \"${env.name}-${env.version}\"' ${modDescriptor}.tmp > $modDescriptor
  """
}


