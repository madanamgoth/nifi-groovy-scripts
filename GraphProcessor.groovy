import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import org.apache.nifi.processor.io.InputStreamCallback
import org.apache.nifi.processor.io.OutputStreamCallback
import java.nio.charset.StandardCharsets
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException

final Logger log = LoggerFactory.getLogger('GraphProcessor')

def processGraph(final InputStream inputStream) {
    def text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
    def slurper = new JsonSlurper()
    def graph = slurper.parseText(text)

    def nodesMap = [:]
    for (int i = 0; i < graph.size(); i++) {
        def node = graph[i]
        nodesMap[node.id] = node
    }
    
    def childToParentMap = [:]
    def startNode = null
    for (int i = 0; i < graph.size(); i++) {
        def node = graph[i]
        if (node.type == 'START') {
            startNode = node
            break
        }
    }
    def startNodeChoice = (startNode && startNode.transitions) ? startNode.transitions.keySet().first() : null

    for (def parentId in nodesMap.keySet()) {
        def parentNode = nodesMap[parentId]
        if (parentNode.transitions) {
            for (def choice in parentNode.transitions.keySet()) {
                def target = parentNode.transitions[choice]
                if (target instanceof String) {
                    childToParentMap[target] = [parentId: parentId, choice: choice]
                } else if (target instanceof Map) {
                    for (def nestedChoice in target.keySet()) {
                        def nestedTarget = target[nestedChoice]
                        childToParentMap[nestedTarget] = [parentId: parentId, choice: choice]
                    }
                }
            }
        }
    }

    def redisCommands = []

    for (def nodeId in nodesMap.keySet()) {
        def node = nodesMap[nodeId]
        if (node.compositCode) {
            List<String> choicePathParts = []
            String currentNodeId = node.id

            while (currentNodeId != null) {
                def currentNode = nodesMap[currentNodeId]
                if (currentNode.type == 'START') {
                    break
                }
                
                def parentEdge = childToParentMap[currentNodeId]
                if (parentEdge == null) {
                    log.warn("Could not find parent for node ID: ${currentNodeId}. Path may be incomplete.")
                    break
                }

                def parentNode = nodesMap[parentEdge.parentId]
                
                if (parentNode.type == 'DYNAMIC-MENU') {
                    choicePathParts.add(0, parentEdge.choice)
                    break 
                }
                
                if (parentNode.type != 'ACTION' && parentNode.type != 'START') {
                    if (parentNode.type == 'INPUT') {
                        def attribute = parentNode.storeAttribute ?: parentNode.nextNodeStoreAttribute
                        choicePathParts.add(0, attribute ? "\${${attribute}}" : "*")
                    } else {
                        choicePathParts.add(0, parentEdge.choice)
                    }
                }
                
                currentNodeId = parentEdge.parentId
            }

            def choicePath = choicePathParts.join('*')
            
            if (startNodeChoice) {
                choicePath = "*${startNodeChoice}*${choicePath}"
            }

            // Clean up any double separators that might occur
            choicePath = choicePath.replaceAll("\\*\\*+", "*")

            choicePath += "#"

            def fieldKey = "*${startNodeChoice}*${node.compositCode}#"

            redisCommands.add([
                command: "HSET",
                key: "composite_long_codes",
                field: fieldKey,
                value: choicePath
            ])
        }
    }
    return redisCommands
}

// --- Main script execution block using the proven pattern ---
def flowFile = session.get()
if (!flowFile) {
    return
}

try {
    // 1. Read the input FlowFile and get the results into a variable.
    def redisCommandsHolder = new Object() { List value }
    session.read(flowFile, new InputStreamCallback() {
        @Override
        void process(InputStream in) throws IOException {
            redisCommandsHolder.value = processGraph(in)
        }
    })

    // 2. Check if the result is valid.
    if (redisCommandsHolder.value) {
        // 3. Write the result to the FlowFile, modifying it in place.
        flowFile = session.write(flowFile, new OutputStreamCallback() {
            @Override
            void process(OutputStream out) throws IOException {
                out.write(JsonOutput.toJson(redisCommandsHolder.value).getBytes(StandardCharsets.UTF_8))
            }
        })
        session.transfer(flowFile, REL_SUCCESS)
    } else {
        log.warn("Processing did not generate any Redis commands for FlowFile ${flowFile.id}")
        session.transfer(flowFile, REL_FAILURE)
    }
} catch (e) {
    log.error("Failed to process graph for FlowFile ${flowFile.id}", e)
    flowFile = session.penalize(flowFile)
    session.transfer(flowFile, REL_FAILURE)
}