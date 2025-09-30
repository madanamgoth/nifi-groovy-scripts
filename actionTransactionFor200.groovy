import groovy.json.JsonSlurper
import groovy.json.JsonParserType
import org.apache.nifi.processor.io.StreamCallback
import groovy.json.JsonBuilder
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

def flowFile = session.get()
if (!flowFile) {
    return
}

try {
    def attributes = [:]
    def nextNodeValue = null
    
    // Read content, determine attributes, and write new content in one operation
    flowFile = session.write(flowFile, new StreamCallback() {
        @Override
        void process(InputStream inputStream, OutputStream outputStream) throws IOException {
            def nodeDetails = new JsonSlurper().setType(JsonParserType.INDEX_OVERLAY).parse(inputStream)
            
            def language = flowFile.getAttribute('language') ?: 'en'
            def isParsingFor200 = flowFile.getAttribute('userSession.isResponseParsingFor200') == 'Y'
            def condition = flowFile.getAttribute('userSession.matchedPath')
            
            if (isParsingFor200) {
                // Conditional path for 2xx
                def finalCondition = condition ?: 'NoMatch'
                attributes['userSession.nextNode'] = nodeDetails?.transitions?.'200'?."${finalCondition}"
                def metadata = nodeDetails?.nextNodesMetadata?.'200'?."${finalCondition}"
                attributes['userSession.nextNodeType'] = metadata?.nextNodeType
                attributes['userSession.prompts'] = metadata?.nextNodePrompts?."${language}"
                attributes['userSession.storeAttribute'] = metadata?.nextNodeStoreAttribute
                attributes['userSession.promptsList'] = metadata?.promptsList ? groovy.json.JsonOutput.toJson(metadata.promptsList) : '["NODATA"]'
            } else {
                // Direct path for 2xx (isParsingFor200 is 'N' or not present)
                attributes['userSession.nextNode'] = nodeDetails?.transitions?.'200'
                def metadata = nodeDetails?.nextNodesMetadata?.'200'
                attributes['userSession.nextNodeType'] = metadata?.nextNodeType
                attributes['userSession.prompts'] = metadata?.nextNodePrompts?."${language}"
                attributes['userSession.storeAttribute'] = metadata?.nextNodeStoreAttribute
                attributes['userSession.promptsList'] = metadata?.promptsList ? groovy.json.JsonOutput.toJson(metadata.promptsList) : '["NODATA"]'
            }
            
            nextNodeValue = attributes['userSession.nextNode']
            
            // Write new content
            if (nextNodeValue) {
                def jsonContent = new JsonBuilder([currentNode: nextNodeValue]).toPrettyString()
                outputStream.write(jsonContent.getBytes(StandardCharsets.UTF_8))
            }
        }
    })
    
    // Update attributes after content modification
    def finalAttributes = attributes.findAll { it.value != null }
    if (finalAttributes) {
        flowFile = session.putAllAttributes(flowFile, finalAttributes)
    }
    
    session.transfer(flowFile, REL_SUCCESS)

} catch (Exception e) {
    log.error("Error in actionTransactionFor200.groovy script", e)
    flowFile = session.penalize(flowFile)
    session.transfer(flowFile, REL_FAILURE)
}
