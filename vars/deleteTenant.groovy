#/usr/bin/env groovy


/*
 * purge and delete FOLIO tenant in k8s
 */


def call(String targetOkapi, String targetTenant) {

  env.okapiToken = ''

  httpRequest acceptType: 'APPLICATION_JSON_UTF8',
              contentType: 'APPLICATION_JSON_UTF8',
              consoleLogResponseBody: false,
              httpMode: 'GET',
              validResponseCodes: '200',
              outputFile:  "tenants.json",
              customHeaders: [[maskValue: true,name: 'X-Okapi-Token',value: env.okapiToken],
                             [maskValue: false,name: 'X-Okapi-Tenant',value: 'supertenant']],
              url: "${targetOkapi}/_/proxy/tenants/${targetTenant}"

  sh "cat tenatns.json"

  return(true)

}
