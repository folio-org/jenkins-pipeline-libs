#!/usr/bin/env groovy


/*
 * Send notifications based on build status string
 * TODO: Move to src/org/folio
 */

def call(String modDescriptor) {
 
  def folioRegistry = 'http://folio-registry.aws.indexdata.com/_/proxy/modules'

  def request = readFile("$modDescriptor")
  echo "Module Descriptor:"
  echo "$request"

  httpRequest acceptType: 'APPLICATION_JSON', contentType: 'APPLICATION_JSON', httpMode: 'POST', 
              requestBody: "$request", url: "$folioRegistry"
      
}
