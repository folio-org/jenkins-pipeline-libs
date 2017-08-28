#!/usr/bin/groovy

import org.folio.foliociCommands

def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  currentBuild.displayName = "#${env.BUILD_NUMBER}-${env.JOB_BASE_NAME}"
  sendNotifications 'STARTED'

}
 
  

  
