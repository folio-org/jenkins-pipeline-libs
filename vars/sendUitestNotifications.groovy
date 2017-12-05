#!/usr/bin/env groovy


/*
 * Send notifications based on build status string
 */

def call(String testStatus) {

  // Default values
  def mailrcpt = 'malc@indexdata.com'
  def color = 'RED'
  def colorCode = '#FF0000'
  def subject = "${testStatus}: 'UI Regression Tests failed for ${env.JOB_NAME} ${env.BUILD_DISPLAY_NAME}'"
  def summary = "${subject} (<${env.BUILD_URL}|Open>)"
  def details = "Check output at ${env.BUILD_URL} to view the results."

  // Override default values based on build status
  if (testStatus == 'FAILED')  {
    // Build failed. Red
    colorCode = '#FF0000'
    emailext (
      to: 'folio-jenkins@indexdata.com',
      subject: subject,
      body: details,
    )
  }
  else if (testStatus == 'SUCCESS') {
    // Green
    colorCode = '#32CD32'
  }
  else { 
    // Grey
    colorCode = '#808080'
  }

  // Send Slack notification
  slackSend (color: colorCode, message: summary)

}
