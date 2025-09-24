import groovy.json.JsonSlurper
import org.apache.nifi.processor.io.StreamCallback
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

def flowFile = session.get()
if (!flowFile) {
    return
}

try {
    flowFile = session.write(flowFile, new StreamCallback() {
        @Override
        void process(InputStream inputStream, OutputStream outputStream) throws IOException {
            def promptTemplate = flowFile.getAttribute('userSession.prompts')
            if (promptTemplate == null) {
                outputStream.write("Error: No prompt template found".getBytes(StandardCharsets.UTF_8))
                return
            }

            def finalPrompt = promptTemplate
            
            try {
                def promptsListAttr = flowFile.getAttribute('userSession.promptsList')
                
                if (promptsListAttr) {
                    def parsedList = new JsonSlurper().parseText(promptsListAttr)
                    if (parsedList instanceof List && !parsedList.isEmpty()) {
                        // Only read content if we need to perform replacements
                        def contentJson = new JsonSlurper().parse(inputStream)
                        
                        parsedList.each { key ->
                            if (contentJson.containsKey(key)) {
                                def value = contentJson[key]
                                finalPrompt = finalPrompt.replace(":" + key, value.toString())
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error during placeholder replacement, using original prompt", e)
                // finalPrompt remains as original promptTemplate
            }

            // Always write the prompt text, never the original JSON content
            outputStream.write(finalPrompt.getBytes(StandardCharsets.UTF_8))
        }
    })

    session.transfer(flowFile, REL_SUCCESS)
} catch (Exception e) {
    // If anything goes seriously wrong, write a clean error message
    try {
        flowFile = session.write(flowFile, new StreamCallback() {
            @Override
            void process(InputStream inputStream, OutputStream outputStream) throws IOException {
                outputStream.write("Error processing prompt template".getBytes(StandardCharsets.UTF_8))
            }
        })
    } catch (Exception writeError) {
        log.error("Failed to write error message", writeError)
    }
    
    log.error("Error replacing placeholders in prompt", e)
    flowFile = session.penalize(flowFile)
    session.transfer(flowFile, REL_FAILURE)
}