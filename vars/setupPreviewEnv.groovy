#!/usr/bin/env groovy

def call(Map previewOpts = [:]) {

  def defaultK8Domain = previewOpts.defaultK8Domain ?: 'folio-default.svc.cluster.local'
  def defaultOkapiUrl = previewOpts.defaultOkapiUrl ?: 'https://okapi-default.ci.folio.org'
  def previewOkapiUrl = previewOpts.previewOkapiUrl ?: 'https://okapi-preview.ci.folio.org'

  // okapi tokens
  withCredentials([usernamePassword(credentialsId: 'okapi-preview-superuser', passwordVariable: 'pass', usernameVariable: 'user')]) {
    writeFile file: 'getOkapiToken.sh', text: libraryResource('org/folio/getOkapiToken.sh')
    sh 'chmod +x getOkapiToken.sh' 
    env.okapiPreviewToken = sh(returnStdout: true, script: "./getOkapiToken.sh -t supertenant -o $previewOkapiUrl -u $user -p $pass").trim()
  }
  
  withCredentials([usernamePassword(credentialsId: 'okapi-default-superuser', passwordVariable: 'pass', usernameVariable: 'user')]) {
    env.okapiDefaultToken = sh(returnStdout: true, script: "./getOkapiToken.sh -t supertenant -o $defaultOkapiUrl -u $user -p $pass").trim()
  }

  // sync MDs from okapi-default
  httpRequest acceptType: 'APPLICATION_JSON_UTF8',
              contentType: 'APPLICATION_JSON_UTF8',
              consoleLogResponseBody: false,
              customHeaders: [[maskValue: true,name: 'X-Okapi-Token',value: env.okapiPreviewToken], 
                              [maskValue: false,name: 'X-Okapi-Tenant',value: 'supertenant']],
              httpMode: 'POST',
              validResponseCodes: '200',
              requestBody: "{ \"urls\" : [ \"${defaultOkapiUrl}\" ]}",
              url: "${previewOkapiUrl}/_/proxy/pull/modules"
       

  // Get stripes MDs
  def stripesMds = findFiles(glob: 'ModuleDescriptors/*.json') 

  stripesMds.each {
    def stripesMdFile = it.name
    echo "$stripesMdFile"
    def stripesMd = readFile "ModuleDescriptors/${stripesMdFile}"
   
    // post module's MD to preview Okapi 
    httpRequest acceptType: 'APPLICATION_JSON_UTF8',
                contentType: 'APPLICATION_JSON_UTF8',
                consoleLogResponseBody: true,
                customHeaders: [[maskValue: true,name: 'X-Okapi-Token',value: env.okapiPreviewToken],
                               [maskValue: false,name: 'X-Okapi-Tenant',value: 'supertenant']],
                httpMode: 'POST',
                validResponseCodes: '201,400',
                requestBody: stripesMd,
                url: "${previewOkapiUrl}/_/proxy/modules"
  }

  def okapiInstall = readJSON file: 'okapi-install.json'

  okapiInstall.each {
    def modId = it.id
    echo "Mod: ${modId}"
    if (modId ==~ /mod-.*-\d+\.\d+\.\d+-SNAPSHOT\.\d+\.\d+/) {
      echo "$modId is a preview module"
    }
    else {

      httpRequest acceptType: 'APPLICATION_JSON_UTF8',
                  contentType: 'APPLICATION_JSON_UTF8',
                  consoleLogResponseBody: false,
                  httpMode: 'GET',
                  validResponseCodes: '200',
                  outputFile:  "${modId}-disc.json",
                  customHeaders: [[maskValue: true,name: 'X-Okapi-Token',value: env.okapiDefaultToken],
                                 [maskValue: false,name: 'X-Okapi-Tenant',value: 'supertenant']],
                  url: "${defaultOkapiUrl}/_/discovery/modules/${modId}"


      def previewModUrl = sh (returnStdout: true, 
                              script: "jq -r '.[0].url' ${modId}-disc.json | " +
                         "sed -r 's|^(http:\\/\\/)(mod-.*)(:[0-9]+)|\\1\\2.${defaultK8Domain}\\3|'").trim()

      sh "jq '.[0].url |= \"${previewModUrl}\"' ${modId}-disc.json > ${modId}-preview-tmp.json"
      sh "jq '.[0]' ${modId}-preview-tmp.json > ${modId}-preview.json"
      sh "cat ${modId}-preview.json"

      def modPreviewExists = httpRequest contentType: 'APPLICATION_JSON_UTF8',
                                         httpMode: 'GET',
                                         validResponseCodes: '200,404',
                                         customHeaders: [[maskValue: true,name: 'X-Okapi-Token',value: env.okapiPreviewToken],
                                                        [maskValue: false,name: 'X-Okapi-Tenant',value: 'supertenant']],
                                         url: "${previewOkapiUrl}/_/discovery/modules/${modId}"
      echo "$modPreviewExists.status"
      // fix this.  
      if (modPreviewExists.status != '200') { 
        // post module's DD to preview Okapi 
        def modDd = readFile file: "${modId}-preview.json"
        httpRequest acceptType: 'APPLICATION_JSON_UTF8',
                    contentType: 'APPLICATION_JSON_UTF8',
                    consoleLogResponseBody: true,
                    customHeaders: [[maskValue: true,name: 'X-Okapi-Token',value: env.okapiPreviewToken], 
                                   [maskValue: false,name: 'X-Okapi-Tenant',value: 'supertenant']],
                    httpMode: 'POST',
                    validResponseCodes: '201,400',
                    requestBody: modDd, 
                    url: "${previewOkapiUrl}/_/discovery/modules"
      }
    }
  }
}

