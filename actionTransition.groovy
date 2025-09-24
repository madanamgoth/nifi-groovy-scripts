import groovy.json.JsonSlurper
import groovy.json.JsonParserType
import org.apache.nifi.processor.io.InputStreamCallback
import java.io.IOException
import java.io.InputStream

def flowFile = session.get()
if (!flowFile) {
    return
}

try {
    def nodeDetails
    session.read(flowFile, new InputStreamCallback() {
        @Override
        void process(InputStream inputStream) throws IOException {
            nodeDetails = new JsonSlurper().setType(JsonParserType.INDEX_OVERLAY).parse(inputStream)
        }
    })

    def httpStatusCode = flowFile.getAttribute('userSession.httpStatusCode')
    def language = flowFile.getAttribute('language') ?: 'en'
    def isParsingFor200 = flowFile.getAttribute('userSession.isResponseParsingFor200')?.toBoolean()
    def isParsingFor400 = flowFile.getAttribute('userSession.isResponseParsingFor400')?.toBoolean()
    
    def attributes = [:]

    if (httpStatusCode) {
        def condition = flowFile.getAttribute('userSession.condition')
        def is2xx = httpStatusCode ==~ /2\d{2}/
        def is4xx = httpStatusCode ==~ /4\d{2}/
        def isConditional = (is2xx && isParsingFor200) || (is4xx && isParsingFor400)

        if (isConditional && condition) {
            // Conditional path (isResponseParsingFor... is true)
            def statusCodeKey = is2xx ? '200' : '400'
            attributes['userSession.nextNode'] = nodeDetails?.transitions?."${statusCodeKey}"?."${condition}"
            def metadata = nodeDetails?.nextNodesMetadata?."${statusCodeKey}"?."${condition}"
            attributes['userSession.nextNodeType'] = metadata?.nextNodeType
            attributes['userSession.prompts'] = metadata?.nextNodePrompts?."${language}"
            attributes['userSession.storeAttribute'] = metadata?.nextNodeStoreAttribute
        } else {
            // Direct path (isResponseParsingFor... is false or no condition)
            attributes['userSession.nextNode'] = nodeDetails?.transitions?."${httpStatusCode}"
            def metadata = nodeDetails?.nextNodesMetadata?."${httpStatusCode}"
            attributes['userSession.nextNodeType'] = metadata?.nextNodeType
            attributes['userSession.prompts'] = metadata?.nextNodePrompts?."${language}"
            attributes['userSession.storeAttribute'] = metadata?.nextNodeStoreAttribute
        }
    }

    def finalAttributes = attributes.findAll { it.value != null }
    flowFile = session.putAllAttributes(flowFile, finalAttributes)
    session.transfer(flowFile, REL_SUCCESS)

} catch (Exception e) {
    log.error("Error in actionTransition.groovy script", e)
    flowFile = session.penalize(flowFile)
    session.transfer(flowFile, REL_FAILURE)
}
