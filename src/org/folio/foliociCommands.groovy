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
  sh 'rm -f folioci_npmver.sh'

}

// get the NPM package name and scope
def npmName(String npmPackageFile = 'package.json') {
  
  def json = readJSON(file: npmPackageFile)
  def name = json.name

  return name
}

// get the unscoped module name and version from package.json. 
// Return name:version map.
@NonCPS
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
def updateModDescriptorId(String modDescriptor) {

  sh "mv $modDescriptor ${modDescriptor}.orig"
  sh """
  jq '.id |= \"${env.name}-${env.version}\"' ${modDescriptor}.orig > $modDescriptor
  """
}

def updateModDescriptor(String modDescriptor) { 
  sh "mv $modDescriptor ${modDescriptor}.orig"
  sh """
  jq  '. | .id |= \"${env.name}-${env.version}\" | if has(\"launchDescriptor\") then 
      .launchDescriptor.dockerImage |= \"${env.dockerRepo}/${env.name}:${env.version}\" |
      .launchDescriptor.dockerPull |= \"true\" else . end' \
    ${modDescriptor}.orig > $modDescriptor
  """
}

// get version from module descriptor id
def getModuleDescriptorIdVer(String modDescriptor) {
  def id = sh(returnStdout: true,
      script: "jq -r '.id' $modDescriptor").trim()

  def proj_name = getProjName() 

  def version = sh(returnStdout: true, 
      script: "echo $id | sed -e s/$proj_name-//").trim()

  return version
}

// replace all instances of '-' in string with '_'
@NonCPS
def replaceHyphen(String string) {
  def convertedString  = string.replaceAll(~/-/, "_")
  return convertedString
}

// get NPM module short name
@NonCPS
def getNpmShortName(String string) {
  def NpmShortName = string.replaceAll(~/folio_/, "")
  return NpmShortName
}
