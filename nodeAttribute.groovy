import groovy.json.JsonSlurper
import groovy.json.JsonParserType
import org.apache.nifi.processor.io.InputStreamCallback
import java.nio.charset.StandardCharsets
import java.io.IOException
import java.io.InputStream

def flowFile = session.get()
if (!flowFile) {
    return
}

try {
    def nodeDetails
    def attributes = [:]

    // 1. Read content to get nodeDetails
    session.read(flowFile, new InputStreamCallback() {
        @Override
        void process(InputStream inputStream) throws IOException {
            nodeDetails = new JsonSlurper().setType(JsonParserType.INDEX_OVERLAY).parse(inputStream)
        }
    })
    
    def type = nodeDetails?.type
    def userInput = flowFile.getAttribute('userInput')
    def language = flowFile.getAttribute('language') ?: 'en'

    attributes['userSession.type'] = type

    switch (type) {
        case 'END':
            attributes['userSession.prompts'] = nodeDetails?.prompts?."${language}"
            break

        case 'MENU':
            if (userInput) {
                attributes['userSession.nextNode'] = nodeDetails?.transitions?."${userInput}"
                attributes['userSession.nextNodeType'] = nodeDetails?.nextNodesMetadata?."${userInput}"?.nextNodeType
                attributes['userSession.prompts'] = nodeDetails?.nextNodesMetadata?."${userInput}"?.nextNodePrompts?."${language}"
            }
            attributes['userSession.storeAttribute'] = nodeDetails?.storeAttribute
            break

        case 'INPUT':
            attributes['userSession.nextNode'] = nodeDetails?.transitions?.values()?.first()
            attributes['userSession.nextNodeType'] = nodeDetails?.nextNodeType
            attributes['userSession.prompts'] = nodeDetails?.nextNodePrompts?."${language}"
            attributes['userSession.storeAttribute'] = nodeDetails?.storeAttribute
            break

        case 'START':
            if (userInput) {
                attributes['userSession.nextNode'] = nodeDetails?.transitions?."${userInput}"
            }
            attributes['userSession.nextNodeType'] = nodeDetails?.nextNodeType
            attributes['userSession.prompts'] = nodeDetails?.nextNodePrompts?."${language}"
            attributes['userSession.storeAttribute'] = nodeDetails?.storeAttribute
            break

        case 'DYNAMIC-MENU':
            attributes['userSession.nextNode'] = nodeDetails?.transitions?.'*'
            attributes['userSession.nextNodeType'] = nodeDetails?.nextNodesMetadata?.'*'?.nextNodeType
            attributes['userSession.prompts'] = nodeDetails?.nextNodesMetadata?.'*'?.nextNodePrompts?."${language}"
            attributes['userSession.storeAttribute'] = nodeDetails?.nextNodesMetadata?.'*'?.nextNodeStoreAttribute
            break

        case 'ACTION':
            attributes['userSession.templateId'] = nodeDetails?.templateId
            attributes['userSession.isNextMenuDynamic'] = nodeDetails?.isNextMenuDynamic
            attributes['userSession.menuJolt'] = nodeDetails?.menuJolt
            attributes['userSession.menuName'] = nodeDetails?.menuName
            attributes['userSession.sessionSpec'] = nodeDetails?.sessionSpec
            attributes['userSession.isResponseParsingFor200'] = nodeDetails?.nextNodesMetadata?.'200'?.isResponseParsing ?: 'NOT DEFINED'
            attributes['userSession.isResponseParsingFor400'] = nodeDetails?.nextNodesMetadata?.'400'?.isResponseParsing ?: 'NOT DEFINED'
            attributes['userSession.queryRecordFor200'] = nodeDetails?.nextNodesMetadata?.'200'?.queryRecord ?: 'NOT DEFINED'
            attributes['userSession.queryRecordFor400'] = nodeDetails?.nextNodesMetadata?.'400'?.queryRecord ?: 'NOT DEFINED'
            break

        default:
            // Optional: handle unknown type
            log.warn("Unknown node type: ${type}")
            break
    }

    // Update attributes after content operation
    def finalAttributes = attributes.findAll { it.value != null }
    if (finalAttributes) {
        flowFile = session.putAllAttributes(flowFile, finalAttributes)
    }
    
    session.transfer(flowFile, REL_SUCCESS)

} catch (Exception e) {
    log.error("Error processing JSON in Groovy script", e)
    flowFile = session.penalize(flowFile)
    session.transfer(flowFile, REL_FAILURE)
}