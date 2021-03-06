#!/usr/bin/env groovy


/*
 *  Set global CI environment vars for NPM projects
 */

def call() {

  def foliociLib = new org.folio.foliociCommands()

  // static vars
  env.okapiUrl = 'https://folio-snapshot-stable-okapi.dev.folio.org'
  env.folioRegistry = 'https://folio-registry.dev.folio.org'

  echo "Okapi URL: ${env.okapiUrl}"
  echo "FOLIO Registry: ${env.folioRegistry}"
  
  // if release
  if ( foliociLib.isRelease() ) {
    env.dockerRepo = 'folioorg'
    env.npmConfig = 'jenkins-npm-folio'
    env.isRelease = true
  }
  // else snapshot
  else {
    env.snapshot = true
    env.dockerRepo = 'folioci'
    env.npmConfig = 'jenkins-npm-folioci'
  }
            
  // convert version to a "snapshot" version for npm-folio-ci
  if ((env.snapshot) && (!env.CHANGE_ID))  {
    foliociLib.npmSnapshotVersion()
  }
 
  // convert version to a special PR version for PR builds
  if ((env.CHANGE_ID) && (env.snapshot)) {
    foliociLib.npmPrVersion()
  } 
          
  // the actual NPM package name as defined in package.json
  env.npmName = foliociLib.npmName('package.json')

  // folioName is similar to npmName except make name okapi compliant
  def Map folioNameVer = foliociLib.npmFolioNameVersion('package.json')          
  folioNameVer.each { key, value ->
                      env.folioName = key
                      env.version = value }

  // "short" (unscoped) name e.g. 'folio_users' -> 'users'
  env.npmShortName = foliociLib.npmShortName(env.folioName)

  // project name is the GitHub repo name
  env.projectName = foliociLib.getProjName()
  env.name = env.projectName

  //git commit sha1
  env.gitCommit = foliociLib.gitCommit()

  // Git Author/Committer
  env.gitCommitter = foliociLib.gitAuthor(env.gitCommit)

  env.projUrl = foliociLib.getProjUrl()

  echo "NPM Package Name: $env.npmName"
  echo "NPM FOLIO Name: $env.folioName"
  echo "NPM Package Unscoped Name: $env.npmShortName"
  echo "NPM Package Version: $env.version"
  echo "Git Project Name: $env.projectName"
  echo "Git Project Url: $env.projUrl"
  echo "Git Commit SHA1: $env.gitCommit"
  echo "Git Committer: $env.gitCommitter"

  // Check to ensure git tag and NPM version match if release
  if (env.isRelease) {
    if ( !foliociLib.tagMatch(env.version) ) {
      error('Git release tag and NPM version mismatch')
    }
  }

}
