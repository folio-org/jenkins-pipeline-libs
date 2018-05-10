#!/usr/bin/env groovy


/*
 * Send notifications to SNS for FOLIO Dashboard (FOLIO-1235)
 */

def call(String folioHost, String buildStatus, String regressionStatus) {

  def foliociLib = new org.folio.foliociCommands() 
  def currentTimeStamp = foliociLib.currentDateTime()

  def message = "${currentTimeStamp}, FOLIO Instance: ${folioHost}, Build Status: ${buildStatus}, Regression Tests Status: ${regressionStatus}" 

  // Send Slack notification
  withCredentials([[$class: 'AmazonWebServicesCredentialsBinding',
                             accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                             credentialsId: 'ebsco-sns',
                             secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {

    snsPublish(topicArn:'arn:aws:sns:us-east-1:579891902283:Folio-Environment', 
               subject:'FOLIO Build Status', 
               message: "$message", 
               messageAttributes: ['k1': 'v1', 'k2': 'v2']))
  }
}
