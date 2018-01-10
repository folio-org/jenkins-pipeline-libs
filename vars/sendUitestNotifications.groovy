#!/usr/bin/env groovy


/*
 * Send notifications based on build status string
 */

def call(String testStatus) {

  // Default values
  def mailrcpt = 'folio-jenkins@indexdata.com'
  def slackChannel = '#coreteam'
  def color = 'RED'
  def colorCode = '#FF0000'
  def subject = "${testStatus}: 'UI Regression Tests failed for ${env.JOB_NAME} ${env.BUILD_DISPLAY_NAME}'"
  def details = "Check output at ${env.BUILD_URL}UI_Regression_Test_Report/ to view the results."

  // Override default values based on build status
  if (testStatus == 'FAILED')  {
    // Build failed. Red
    colorCode = '#FF0000'
    emailext (
      to: mailrcpt,
      subject: subject,
      body: details,
    )
  }
  else if (testStatus == 'SUCCESS') {
    // Green
    colorCode = '#32CD32'
    subject = "${testStatus}: 'UI Regression Tests passed for ${env.JOB_NAME} ${env.BUILD_DISPLAY_NAME}. W00t!'"
  }
  else { 
    // Grey
    colorCode = '#808080'
    subject = "${testStatus}: 'UI Regression Tests status for ${env.JOB_NAME} ${env.BUILD_DISPLAY_NAME} undetermined.'"
  }

  // Send Slack notification
  def summary = "${subject} (<${env.BUILD_URL}UI_Regression_Test_Report/|Open>)"
  slackSend (channel: slackChannel, color: colorCode, message: summary)

}
