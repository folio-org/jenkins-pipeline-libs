#!/usr/bin/env groovy


/*
 * Send notifications based on build status string
 * TODO: Move to src/org/folio
 */

def call(String tenant,String prModDesc,String installJson) {

 
  def folioRegistry = 'http://folio-registry.indexdata.internal:9130'
  def okapiPull = "{ \"urls\" : [ \"http://${folioRegistry}\" ]}"
  def tenantJson = "{\"id\":\"${tenant}\"}"

  docker.image('folioorg/okapi:latest').withRun('', 'dev') { container ->
    def okapiIp = sh returnStdout:true, script: "docker inspect --format='{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}} ${container.id}").trim()

    // pull all MDs
    httpRequest acceptType: 'APPLICATION_JSON', 
                contentType: 'APPLICATION_JSON', 
                consoleLogResponseBody: true
                httpMode: 'POST',
                requestBody: "${okapiPull}", 
                url: "${okapiIp}:9130/_/proxy/pull/modules"

    // POST our MD
    httpRequest acceptType: 'APPLICATION_JSON', 
                contentType: 'APPLICATION_JSON', 
                consoleLogResponseBody: true
                httpMode: 'POST',
                requestBody: prModDesc, 
                url: "${okapiIp}:9130/_/proxy/modules"

    // create tenant
    httpRequest acceptType: 'APPLICATION_JSON', 
                contentType: 'APPLICATION_JSON', 
                consoleLogResponseBody: true
                httpMode: 'POST',
                requestBody: tenantJson, 
                url: "${okapiIp}:9130/_/proxy/tenants"

    // Enable Stripes Modules 
    httpRequest acceptType: 'APPLICATION_JSON', 
                contentType: 'APPLICATION_JSON', 
                consoleLogResponseBody: true
                httpMode: 'POST',
                requestBody: installJson, 
                url: "${okapiIp}:9130/_/proxy/tenants/${tenant}/install?simulate=true"

  } // destroy okapi container
}
