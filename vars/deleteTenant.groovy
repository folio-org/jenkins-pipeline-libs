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

  def checkResult = httpRequest acceptType: 'APPLICATION_JSON_UTF8',
              contentType: 'APPLICATION_JSON_UTF8',
              consoleLogResponseBody: false,
              httpMode: 'GET',
              validResponseCodes: '200,404',
              customHeaders: [[maskValue: true,name: 'X-Okapi-Token',value: env.okapiToken],
                             [maskValue: false,name: 'X-Okapi-Tenant',value: 'supertenant']],
              url: "${targetOkapi}/_/proxy/tenants/${targetTenant}"

  if (checkResult.status.equals(200)) {
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
    def disable = readFile file: 'disable.json'
    echo "${disable}"

    def purgeResult = httpRequest acceptType: 'APPLICATION_JSON_UTF8',
                contentType: 'APPLICATION_JSON_UTF8',
                consoleLogResponseBody: true,
                customHeaders: [[maskValue: true,name: 'X-Okapi-Token',value: env.okapiToken], 
                                [maskValue: false,name: 'X-Okapi-Tenant',value: 'supertenant']],
                httpMode: 'POST',
                validResponseCodes: '200',
                requestBody: disable,
                url: "${targetOkapi}/_/proxy/tenants/${targetTenant}/install?purge=true"

    def deleteResult = httpRequest acceptType: 'APPLICATION_JSON_UTF8',
                contentType: 'APPLICATION_JSON_UTF8',
                consoleLogResponseBody: true,
                customHeaders: [[maskValue: true,name: 'X-Okapi-Token',value: env.okapiToken], 
                                [maskValue: false,name: 'X-Okapi-Tenant',value: 'supertenant']],
                httpMode: 'DELETE',
                validResponseCodes: '204',
                url: "${targetOkapi}/_/proxy/tenants/${targetTenant}"

    echo "deleted tenant ${targetTenant}"
  } else {
    echo "tenant ${targetTenant} does not exist, skipping deletion..."
  }
}
