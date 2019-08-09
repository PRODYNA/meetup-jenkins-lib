package com.prodyna.meetup.jenkins

import org.jenkinsci.plugins.pipeline.utility.steps.shaded.org.yaml.snakeyaml.DumperOptions
import org.jenkinsci.plugins.pipeline.utility.steps.shaded.org.yaml.snakeyaml.Yaml

import groovy.json.JsonOutput

String getGitCommitId(){
    return sh(returnStdout: true, script: "git rev-parse --short=11 HEAD").trim()
}

Void tagImage(String sourceImage, String targetTag) {
    def sourceImageNoTag = getImageNameWithoutTag(sourceImage)
    sh(script: "skopeo copy docker://${sourceImage} docker://${sourceImageNoTag}:${targetTag}")
}

String getImageNameWithoutTag(String imageName){
    def imageNameParts = imageName.tokenize('/')
    def imageId = imageNameParts.last()
    def imageIdNoTag = imageId.tokenize(':').first()

    imageNameParts.remove(imageNameParts.size() - 1)
    imageNameParts.add(imageIdNoTag)

    return imageNameParts.join('/')
}

boolean isImageAvailable(String image) {
    def result = false

    try {
        sh("skopeo inspect docker://${image} &> /dev/null")
        echo "Image ${image} found"
        result = true
    } catch (err) {
        echo "Image ${image} not found"
    }

    return result
}

Void copyImage(String sourceImage, String targetImage) {
    sh(script: "skopeo copy  docker://${sourceImage} docker://${targetImage}")
}

Map getImageInfo(String image) {
    def imageJson = sh(returnStdout: true, script: "skopeo inspect docker://${image}")
    def imageInfo = readJSON text: imageJson
    return imageInfo
}

Set<String> getImagesFromK8sYaml(String k8sYaml) {
    def images = new HashSet<String>()
    k8sYaml.findAll(/(?m)image:(\s).*/) {
        def image = it[0] ?: ''
        image = image.replace('image: ','').trim()
        image = image.replaceAll('"', '')
        image = image.replaceAll("'", '')
        if(!image.isEmpty()) {
            images.add(image)
        }
    }
    return images
}


String replaceImageTagWithDigest(String yamlString) {
    def yamlDocs = yamlString.split('(?m)^---\\s*(#.*)?$')

    def resultDocs = []
    yamlDocs.each { yamlDoc ->
        Set<String> images = getImagesFromK8sYaml(yamlDoc)

        def imageMapping = [:]
        images.each { image ->
            Map imageInfo = getImageInfo(image)
            String imageWithHash = "${imageInfo['Name']}@${imageInfo['Digest']}"
            imageMapping.put(imageWithHash, image)
            yamlDoc = yamlDoc.replaceAll(image, imageWithHash)
        }

        def k8sObject = readYaml text: yamlDoc

        if(!imageMapping.isEmpty()) {
            def annos = k8sObject.metadata.annotations ?: [:]
            annos.put("deploy.prodyna.com/hash-replace-original-images", JsonOutput.toJson(imageMapping))
            k8sObject.metadata.annotations = annos
        }

        resultDocs.add(k8sObject)
    }

    DumperOptions options = new DumperOptions()
    options.setExplicitStart(true)
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
    Yaml yaml = new Yaml(options)
    return yaml.dumpAll(resultDocs.iterator())
}

void buildImageFromDockerfile(String path, String image) {
    container(name: 'kaniko', shell: '/busybox/sh') {
        withEnv(['PATH+EXTRA=/busybox:/kaniko']) {
        sh """#!/busybox/sh
        /kaniko/executor -f ${path}/Dockerfile -c ${path} --destination=${image}
        """
        }
    }
}

void buildImageWithJib(String image) {
    sh(script: "mvn compile jib:build -Djib.to.image=${image}")
}

void deployWithKustomize(String target, boolean imageDigestReplace) {
    def resultYaml = sh(returnStdout: true, script: "kustomize build ${target}")

    if(imageDigestReplace) {
        resultYaml = replaceImageTagWithDigest(resultYaml)
    }

    echo "${resultYaml}"
    writeFile file: "tmp-digest-replace.yaml", text: resultYaml
    sh "kubectl apply -f tmp-digest-replace.yaml"
}

// Return the constants of this script so it can be re-used in Jenkinsfile.
return this
