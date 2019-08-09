import com.prodyna.meetup.jenkins.*

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def label = "slave-${UUID.randomUUID().toString()}"
    def buildSlave = config.buildSlave ?: { nodeLabel, nodebody ->
        new PodTemplates().maven(nodeLabel, nodebody)
    }
    def utils = new Utils()

    buildSlave(label) {
        try {
            node(label) {

                stage("Checkout") {
                    checkout scm
                }
                container('build') {
                    stage("Deploy") {
                        def envCfg = readYaml file: "environment.yaml"

                        def services = envCfg.services ?: []

                        services.each { service ->
                            def path = service.path
                            assert path != null : 'ERROR: Path may not be null'

                            def imageDigestReplace = service.imageDigestReplace ?: false
                            def type = service.type ?: 'kustomize'

                            if('kustomize'.equalsIgnoreCase(type)) {
                                utils.deployWithKustomize(path, imageDigestReplace)
                            }
                        }
                    }
                }
            }
        } catch (err) {
            echo "in catch block"
            echo "Message: ${err.getMessage()}"
            currentBuild.result = 'FAILURE'
            throw err
        }
    }

}
