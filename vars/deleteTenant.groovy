#!/usr/bin/env groovy


/*
 * purge and delete FOLIO tenant in k8s
 */


def call(String targetOkapi, String targetTenant, Boolean secured = true) {

  if (secured) {
    writeFile file: 'getOkapiToken.sh', text: libraryResource('org/folio/getOkapiToken.sh')
    sh 'chmod +x getOkapiToken.sh' 
    env.okapiToken = sh(returnStdout: true, script: "./getOkapiToken.sh -t supertenant -o $targetOkapi -u $user -p $pass").trim()
  } else {
    env.okapiToken = ''
  }

  def getResult = httpRequest acceptType: 'APPLICATION_JSON_UTF8',
              contentType: 'APPLICATION_JSON_UTF8',
              consoleLogResponseBody: false,
              httpMode: 'GET',
              validResponseCodes: '200',
              outputFile:  "mods-enabled.json",
              customHeaders: [[maskValue: true,name: 'X-Okapi-Token',value: env.okapiToken],
                             [maskValue: false,name: 'X-Okapi-Tenant',value: 'supertenant']],
              url: "${targetOkapi}/_/proxy/tenants/${targetTenant}/modules"

  sh "cat mods-enabled.json | jq '[.[] + {\"action\" : \"disable\"}]' > disable.json"
  def disableJSON = readJSON file: 'disable.json'
  echo "${disableJSON}"

  def purgeResult = httpRequest acceptType: 'APPLICATION_JSON_UTF8',
              contentType: 'APPLICATION_JSON_UTF8',
              consoleLogResponseBody: false,
              customHeaders: [[maskValue: true,name: 'X-Okapi-Token',value: env.okapiToken], 
                              [maskValue: false,name: 'X-Okapi-Tenant',value: 'supertenant']],
              httpMode: 'POST',
              validResponseCodes: '200',
              requestBody: disableJSON
              url: "${targetOkapi}/_/proxy/tenants/${targetTenant}/install?simulate=true"

  return(true)

}
