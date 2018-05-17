#!/usr/bin/env groovy


/*
 * Send notifications based on build status string
 */

def call(String testStatus, String instance, String okapiUrl = 'unknown') {

  // Default values
  def mailrcpt = 'folio-jenkins@indexdata.com'
  def slackChannel = '#coreteam'
  def color = 'RED'
  def colorCode = '#FF0000'
  def subject = "${testStatus}: 'UI Regression Tests failed for ${env.JOB_NAME} ${env.BUILD_DISPLAY_NAME}'"
  def details = "Check output at ${env.BUILD_URL}UI_20Regression_20Test_20Report/ to view the results."

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
  def summary = "${subject} (<${env.BUILD_URL}UI_20Regression_20Test_20Report/|Open>)"

  slackSend (channel: slackChannel, color: colorCode, message: summary)

  // Send SNS notification to EBSCO SNS for folio-1235
  withCredentials([[$class: 'AmazonWebServicesCredentialsBinding',
                             accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                             credentialsId: 'ebsco-sns',
                             secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {

    snsPublish(topicArn:'arn:aws:sns:us-east-1:579891902283:Folio-Environment',
               subject:'FOLIO Regression Test Status',
               message: "{ \"name\": \"${instance}\", \"message\": \"$summary\", \"okapiUrl\": \"$okapiUrl\" }",
               messageAttributes: ['k1': 'v1', 'k2': 'v2'])
  }

}
