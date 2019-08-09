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
                    stage("Build") {
                        if(config.buildCommand != null) {
                            echo '---> Building application from source.'
                            config.buildCommand.call(utils)
                        } else {
                            echo '---> No commands for source build defined.'
                        }
                    }

                    stage("Deploy") {
                        def deployTargetsByBranch = config.deployTargets ?: [:]
                        def deployTargets = deployTargetsByBranch.get(env.BRANCH_NAME)
                        for (String deployTarget : deployTargets) {
                            utils.deployWithKustomize(deployTarget, true)
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
