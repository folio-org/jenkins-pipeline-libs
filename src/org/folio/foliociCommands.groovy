#!/usr/bin/env groovy

package org.folio

import jenkins.model.Jenkins
import com.cloudbees.groovy.cps.NonCPS
import java.text.SimpleDateFormat

// get git commit/sha1
def getCommitSha(){
    return sh(returnStdout: true, script: 'git rev-parse HEAD')
}

// Update npm package.json version to "snapshot" version for FOLIO CI
def npmSnapshotVersion() {

  def folioci_npmver = libraryResource('org/folio/folioci_npmver.sh')
  writeFile file: 'folioci_npmver.sh', text: folioci_npmver
  sh 'chmod +x folioci_npmver.sh'
  sh 'npm version `./folioci_npmver.sh`'
  sh 'rm -f folioci_npmver.sh'

}

def npmPrVersion() {
  def gitVersion = sh(returnStdout: true, script: "jq -r \".version\" package.json").trim()
  sh "npm version ${gitVersion}-pr.${env.CHANGE_ID}.${env.BUILD_NUMBER}"
}


// get the NPM package name and scope
def npmName(String npmPackageFile = 'package.json') {
  
  def json = readJSON(file: npmPackageFile)
  def name = json.name

  return name
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

// get NPM module short name
def getNpmShortName(String string) {
  def npmShortName = string.replaceAll(~/folio_/, "")
  return npmShortName
}

// get base repo/project name
def getProjName() {

  def projName = sh(returnStdout: true, 
      script: 'git config remote.origin.url | awk -F \'/\' \'{print $5}\' | sed -e \'s/\\.git//\'').trim()

  return projName
}

// get project URL
def getProjUrl() {
  def projUrl = sh(returnStdout: true, script: 'git config remote.origin.url').trim()

  return projUrl
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
def replaceHyphen(String string) {
  def convertedString  = string.replaceAll(~/-/, "_")
  return convertedString
}

// determine if this is a release or snapshot
def isRelease() {
  def gitTag = sh(returnStdout: true, script: 'git tag -l --points-at HEAD').trim()
  if ( gitTag ==~ /^v[[:digit:]]*/ ) { 
    echo "This is a release build: $gitTag"
    return true
  }
  else {
    echo "This is a snapshot build"
    return false
  }
}
    
// generate mod descriptors for Stripes
def genStripesModDescriptors(String outputDir = null) { 
  def script = libraryResource('org/folio/genStripesModDescriptors.sh')
  writeFile file: 'genStripesModDescriptors.sh', text: script
  sh 'chmod +x genStripesModDescriptors.sh'

  if (outputDir) { 
    sh "./genStripesModDescriptors.sh -o $outputDir"
  } 
  else { 
    sh './genStripesModDescriptors.sh'
  }

  sh 'rm -f genStripesModDescriptors.sh'
}
  
@NonCPS
def currentDateTime() {
  def dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm")
  def date = new Date()

  return dateFormat.format(date)
}


