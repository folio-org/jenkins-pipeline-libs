#!/usr/bin/env groovy


/*
 * Send notifications based on build status string
 */

def call(String buildStatus = 'STARTED') {
  // build status of null means successful
  buildStatus =  buildStatus ?: 'SUCCESSFUL'

  // Default values
  def color = 'RED'
  def colorCode = '#FF0000'
  def subject = "${buildStatus}: '${env.JOB_NAME} ${env.BUILD_DISPLAY_NAME}'"
  def summary = "${subject} (<${env.BUILD_URL}|Open>)"
  def details = "Check console output at ${env.BUILD_URL}console to view the results."

  // Override default values based on build status
  if (buildStatus == 'STARTED') {
    // Grey
    colorCode = '#808080'
  }
  else if (buildStatus == 'SUCCESSFUL') {
    // Green
    colorCode = '#32CD32'
  }
  else if (buildStatus == 'UNSTABLE') {
    // Yellow
    colorCode = '#FFFF00'
    emailext (
      to: 'folio-jenkins@indexdata.com',
      subject: subject,
      body: details,
      recipientProviders: [[$class: 'DevelopersRecipientProvider'], [$class: 'UpstreamComitterRecipientProvider'], [$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']]
    )
  }
  else if (buildStatus == 'ABORTED') {
    // Black
    colorCode = '#000000'
  }
  else {
    // Build failed. Red
    colorCode = '#FF0000'
    emailext (
      to: 'folio-jenkins@indexdata.com',
      subject: subject,
      body: details,
      recipientProviders: [[$class: 'DevelopersRecipientProvider'], [$class: 'UpstreamComitterRecipientProvider'], [$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']]

    )
  }

  // Send Slack notification
  slackSend (color: colorCode, message: summary)
  // Send SNS notification to EBSCO for dashboard (folio-1235)
  withCredentials([[$class: 'AmazonWebServicesCredentialsBinding',
                             accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                             credentialsId: 'ebsco-sns',
                             secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {

    snsPublish(topicArn:'arn:aws:sns:us-east-1:579891902283:Folio-Environment',
               subject:'FOLIO Build Status',
               message: summary,
               messageAttributes: ['k1': 'v1', 'k2': 'v2'])
  }

}
