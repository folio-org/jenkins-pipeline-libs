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
  def details = "Check console outputfor details: ${env.BUILD_URL}console"

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
      to: 'malc@indexdata.com',
      subject: subject,
      body: details,
      recipientProviders: [[$class: 'DevelopersRecipientProvider']]
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
      to: 'john@malconian.net',
      subject: subject,
      body: details,
      recipientProviders: [[$class: 'DevelopersRecipientProvider']]
    )
  }

  // Send Slack notification
  slackSend (color: colorCode, message: summary)

}
