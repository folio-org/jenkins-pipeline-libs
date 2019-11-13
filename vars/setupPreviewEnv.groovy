#!/usr/bin/env groovy

def call(Map previewOpts = [:]) {

  def defaultK8Domain = previewOpts.defaultK8Domain ?: 'folio-default.svc.cluster.local'
  def defaultOkapiUrl = previewOpts.defaultOkapiUrl ?: 'https://okapi-default.ci.folio.org'
  def previewOkapiUrl = previewOpts.previewOkapiUrl ?: 'https://okapi-preview.ci.folio.org'


  // sync MDs from okapi-default
  httpRequest acceptType: 'APPLICATION_JSON_UTF8',
              contentType: 'APPLICATION_JSON_UTF8',
              consoleLogResponseBody: false,
              httpMode: 'POST',
              validResponseCodes: '200',
              requestBody: "{ \"urls\" : [ \"${defaultOkapiUrl}\" ]}",
              url: "${previewOkapiUrl}/_/proxy/pull/modules"
       

  def okapiInstall = readJSON file: 'okapi-install.json'

  // okapi tokens? 

  okapiInstall.each {
    def modId = it.id
    echo "Mod: ${modId}"
    if (modId ==~ /mod-.*-\d+\.\d+\.\d+-SNAPSHOT\.\d+\.\d+/) {
      echo "$modId is a preview module"
    }
    else {
      // post new DD to preview env.

      httpRequest acceptType: 'APPLICATION_JSON_UTF8',
                  contentType: 'APPLICATION_JSON_UTF8',
                  consoleLogResponseBody: false,
                  httpMode: 'GET',
                  validResponseCodes: '200',
                  outputFile:  "${modId}-disc.json",
                  url: "${defaultOkapiUrl}/_/discovery/modules/${modId}"


      def previewModUrl = sh (returnStdout: true, 
                              script: "jq -r '.[0].url' ${modId}-disc.json | " +
                         "sed -r 's|^(http:\\/\\/)(mod-.*)(:[0-9]+)|\\1\\2.${defaultK8Domain}\\3|'")

      sh "jq '.[0].url |= \"${previewModUrl}\"' ${modId}-disc.json > ${modId}-preview-tmp.json"
      sh "jq '.[0]' ${modId}-preview-tmp.json > ${modId}-preview.json"
      sh "cat ${modId}-preview.json"

      def modPreviewExists = httpRequest contentType: 'APPLICATION_JSON_UTF8',
                                         httpMode: 'GET',
                                         validResponseCodes: '200,404',
                                         url: "${previewOkapiUrl}/_/discovery/modules/${modId}"

      if (modPreviewExists.status != '200') { 
        // post module's DD to preview Okapi 
        httpRequest acceptType: 'APPLICATION_JSON_UTF8',
                    contentType: 'APPLICATION_JSON_UTF8',
                    consoleLogResponseBody: true,
                    httpMode: 'POST',
                    validResponseCodes: '200',
                    requestBody: "${modId}-preview.json", 
                    url: "${previewOkapiUrl}/_/discovery/modules"
      }
    }
  }
}

