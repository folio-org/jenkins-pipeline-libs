#!/usr/bin/env groovy

package org.folio

import jenkins.model.Jenkins
import com.cloudbees.groovy.cps.NonCPS
import java.text.SimpleDateFormat


// Update npm package.json version to "snapshot" version for FOLIO CI
def npmSnapshotVersion() {
  def folioci_npmver = libraryResource('org/folio/folioci_npmver.sh')
  writeFile file: 'folioci_npmver.sh', text: folioci_npmver
  sh 'chmod +x folioci_npmver.sh'
  sh 'npm version `./folioci_npmver.sh`'
  sh 'rm -f folioci_npmver.sh'
}

// set an unique npm package version for PR testing
def npmPrVersion() {
  def version = sh(returnStdout: true, script: "jq -r \".version\" package.json").trim()
  sh "npm version ${version}-pr.${env.CHANGE_ID}.${env.BUILD_NUMBER}"
}

// get the NPM package name and scope
def npmName(String npmPackageFile = 'package.json') {
  def json = readJSON(file: npmPackageFile)
  def name = json.name
  return name
}

// get the module name and version from package.json and
// convert scoped name format to "FOLIO" name.  e.g folio_NAME
def npmFolioNameVersion(String npmPackageFile = 'package.json') {
  def map = [:]
  def json = readJSON(file: npmPackageFile)
  def n = json.name.replaceAll(~/\//, "_")  

  name = n.replaceAll(~/@/, "")  
  version = json.version
  
  map = [(name):version]
  return map
}

// get NPM module short name (unscoped name)
def npmShortName(String string) {
  def n = string.replaceAll(~/folio_/, "")
  return n
}

// get git commit/sha1
def gitCommit(){
    return sh(returnStdout: true, script: 'git rev-parse HEAD')
}

// get base repo/project name
def getProjName() {
  def n = sh(returnStdout: true, script: 'git config remote.origin.url | awk -F \'/\' \'{print $5}\' | sed -e \'s/\\.git//\'').trim()
  return n
}

// get project URL
def getProjUrl() {
  def url = sh(returnStdout: true, script: 'git config remote.origin.url').trim()
  return url
}

// determine if this is a release or snapshot
def boolean isRelease() {
  def gitTag = sh(returnStdout: true, script: 'git tag -l --points-at HEAD').trim()
  if ( gitTag ==~ /^v\d+.*/ ) {
    return true
  }
  else {
    return false
  }
}

// get git tag
def gitTag() {
  def tag = sh(returnStdout: true, script: 'git tag -l --points-at HEAD').trim()
  return tag
}

// compare git tag with env.version
def boolean tagMatch(String version) {
  def tag = sh(returnStdout: true, script: 'git tag -l --points-at HEAD | tr -d v').trim()
  if (tag == version) {
    return true
  }
  else {
    return false
  }
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

  def name = getProjName() 

  def version = sh(returnStdout: true, 
      script: "echo $id | sed -e s/$name-//").trim()

  return version
}

// replace all instances of '-' in string with '_'
def replaceHyphen(String string) {
  def s  = string.replaceAll(~/-/, "_")
  return s
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

def gradleProperty(String property) {
  def value = sh(returnStdout: true, 
                 script: "grep \'^${property}=\' gradle.properties | awk -F \'=\' \'{ print \$2 }\'").trim()
  return value
}

// check for snapshot deps in Maven release
def checkMvnReleaseDeps() {
  def deps = sh(returnStdOut: true,
                  script: 'mvn dependency:list | { grep -i snapshot || true; }')
  return deps
}

  
@NonCPS
def currentDateTime() {
  def dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm")
  def date = new Date()

  return dateFormat.format(date)
}


