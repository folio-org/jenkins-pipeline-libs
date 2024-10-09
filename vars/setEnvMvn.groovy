#!/usr/bin/env groovy


/*
 *  Set global CI environment vars for Maven projects
 */

def call() {

  def foliociLib = new org.folio.foliociCommands()

  // static vars
  env.okapiUrl = 'https://folio-snapshot-stable-okapi.dev.folio.org'
  env.folioRegistry = 'https://folio-registry.dev.folio.org'

  echo "Okapi URL: ${env.okapiUrl}"
  echo "FOLIO Registry: ${env.folioRegistry}"

  def mvn_artifact = readMavenPom().getArtifactId()
  def mvn_version =  readMavenPom().getVersion()
  env.name = mvn_artifact
  env.bareVersion = mvn_version

  // if release
  if ( foliociLib.isRelease() ) {
    // make sure git tag and maven version match
    if ( foliociLib.tagMatch(mvn_version) ) {
      env.version = mvn_version
      env.isRelease = true
      env.dockerRepo = 'folioorg'
    }
    else {
      error('Git release tag and Maven version mismatch')
    }
  }
  // else snapshot
  else {
    env.version = "${mvn_version}.${env.BUILD_NUMBER}"
    env.snapshot = true
    env.dockerRepo = 'folioci'
  }

  // project name is the GitHub repo name
  env.projectName = foliociLib.getProjName()

  //git commit sha1
  env.gitCommit = foliociLib.gitCommit()
  env.projUrl = foliociLib.getProjUrl()

  //set java version
  scriptStatus17 = sh(returnStatus:true, script: 'test -d "/usr/lib/jvm/java-17-openjdk-amd64"')
  scriptStatus21 = sh(returnStatus:true, script: 'test -d "/usr/lib/jvm/java-21-openjdk-amd64"')
  scriptStatus11 = sh(returnStatus:true, script: 'test -d "/usr/lib/jvm/java-11-openjdk-amd64"')

  if (scriptStatus17 == 0) {
    env.javaInstall = 'openjdk-17-jenkins-slave-all'
  }  
  else if (scriptStatus21 == 0) {
    env.javaInstall = 'openjdk-21-jenkins-slave-all'
  }
  else if (scriptStatus11 == 0) {
    env.javaInstall = 'openjdk-11-jenkins-slave-all'
  }
  else {
    error('Unable to determine Java version')
  }

  echo "JDK: $env.javaInstall"
  echo "Maven Project Name: $env.name"
  echo "Maven Project Version: $env.version"
  echo "Git Project Name: $env.projectName"
  echo "Git Project Url: $env.projUrl"
  echo "Git Commit SHA1: $env.gitCommit"
}
