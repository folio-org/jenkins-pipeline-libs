#!/usr/bin/env groovy

package org.folio

import com.cloudbees.groovy.cps.NonCPS
import jenkins.model.Jenkins


def setMavenSnapshotVer() {
  def pomVersion = readMavenPom().getVersion()
  def snapshotVersion = "${pomVersion}.${env_BUILD_NUMBER}"

  return snapshotVersion
}

def setBuildDisplayName() {
  currentBuild.displayName = "#${env.BUILD_NUMBER}-${env.JOB_BASE_NAME}"
}





