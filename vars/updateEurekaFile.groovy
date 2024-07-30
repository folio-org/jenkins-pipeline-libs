import groovy.json.JsonSlurperClassic

def Info(String moduleName, String version) {
  String gitHub = 'https://github.com/folio-org/platform-complete.git'
  sh("git clone -b snapshot --single-branch ${gitHub}")
  dir('platform-complete') {
    def txt = readFile file: 'eureka-platform.json'
    def data = new JsonSlurperClassic().parseText(txt as String)
    if (data['id'].find { it.contains(moduleName) }) {
      data.each { module ->
        if (module['id'] =~ /${moduleName}/) {
          module['id'] = "${moduleName}-${version}"
        }
      }
    }
    sh("rm -f eureka-platform.json")
    writeJSON(file: "eureka-platform.json", json: data, pretty: 2)
    sh("cat eureka-platform.json")
    withCredentials([steps.usernamePassword(credentialsId: 'id-jenkins-github-personal-token-with-username', passwordVariable: 'GIT_PASS', usernameVariable: 'GIT_USER')]) {
      sh("git commit -am '[EPC] updated: ${moduleName}-${version}'")
      sh("set +x && git pull && git push --set-upstream https://${env.GIT_USER}:${env.GIT_PASS}@github.com/folio-org/platform-complete.git snapshot")
    }
  }
}
