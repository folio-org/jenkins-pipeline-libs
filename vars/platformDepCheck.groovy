#!/usr/bin/env groovy


/*
 * Send notifications based on build status string
 * TODO: Move to src/org/folio
 */

def call(String tenant,String installJson,String okapiVersion = 'latest') {

 
  def okapiPull = "{ \"urls\" : [ \"${env.folioRegistry}\" ]}"
  def tenantJson = "{\"id\":\"${tenant}\"}"

  docker.withRegistry('https://docker.io/v2/', 'dockerhub-ci-pull-account') {
    docker.image("folioorg/okapi:${okapiVersion}").withRun('', 'dev') { container ->
      def okapiIp = sh(returnStdout:true, script: "docker inspect --format='{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' ${container.id}").trim()

      if (env.releaseOnly == 'true') {
        installUrl = "http://${okapiIp}:9130/_/proxy/tenants/${tenant}/install?simulate=true&preRelease=false"
      }
      else {
        installUrl = "http://${okapiIp}:9130/_/proxy/tenants/${tenant}/install?simulate=true"
      }

      // make sure okapi is fully started
      sleep 5

      // pull all MDs
      httpRequest acceptType: 'APPLICATION_JSON_UTF8', 
                  contentType: 'APPLICATION_JSON_UTF8', 
                  consoleLogResponseBody: false,
                  httpMode: 'POST',
                  requestBody: okapiPull, 
                  url: "http://${okapiIp}:9130/_/proxy/pull/modules"

      // create tenant
      httpRequest acceptType: 'APPLICATION_JSON_UTF8', 
                  contentType: 'APPLICATION_JSON_UTF8', 
                  consoleLogResponseBody: true,
                  httpMode: 'POST',
                  requestBody: tenantJson, 
                  url: "http://${okapiIp}:9130/_/proxy/tenants"

      // Enable Stripes Modules 
      httpRequest acceptType: 'APPLICATION_JSON_UTF8', 
                  contentType: 'APPLICATION_JSON_UTF8', 
                  consoleLogResponseBody: true,
                  httpMode: 'POST',
                  requestBody: installJson, 
                  outputFile: 'install.json',
                  url: installUrl

    } // destroy okapi container
  }
}
