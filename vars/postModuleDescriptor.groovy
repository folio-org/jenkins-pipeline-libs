#!/usr/bin/env groovy


/*
 * Send notifications based on build status string
 */

def call(String modDescriptor,String version) {
 
  //def folioRegistry = 'http://folio-registry.aws.indexdata.com:9130/_/proxy/modules'
  def folioRegistry = 'http://folio-registry-test.aws.indexdata.com:9130/_/proxy/modules'

  // Add build number to version if snapshot
  if (version ==~ /.*-SNAPSHOT$/) {
    sh "mv $modDescriptor ${modDescriptor}.tmp"
    sh """
      jq '.id |= \"${version}.${env.BUILD_NUMBER}\" ${modDescriptor}.tmp > $modDescriptor
    """
  }

  def request = readFile("$modDescriptor")
  httpRequest acceptType: 'APPLICATION_JSON', contentType: 'APPLICATION_JSON', httpMode: 'POST', 
              requestBody: "$request", url: "$folioRegistry"
      
}
