import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.nio.charset.StandardCharsets

// Simple logger simulation for testing
class TestLogger {
    static void info(String msg) { println "INFO: $msg" }
    static void warn(String msg) { println "WARN: $msg" }
    static void error(String msg) { println "ERROR: $msg" }
}

def processGraph(final InputStream inputStream) {
    def text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
    def slurper = new JsonSlurper()
    def graph = slurper.parseText(text)

    // First pass: Check if any nodes have composite codes
    TestLogger.info("Starting first pass: checking for composite codes in graph")
    def hasCompositeCode = false
    for (int i = 0; i < graph.size(); i++) {
        def node = graph[i]
        if (node.compositCode) {
            hasCompositeCode = true
            TestLogger.info("Found composite code '${node.compositCode}' in node '${node.id}'")
            break
        }
    }
    
    if (!hasCompositeCode) {
        TestLogger.info("No composite codes found in graph - returning empty array")
        return []
    }
    
    TestLogger.info("Composite codes detected - proceeding with Redis command generation")

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
                    TestLogger.warn("Could not find parent for node ID: ${currentNodeId}. Path may be incomplete.")
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

// Test the function
def file = new File("c:\\Users\\amgoth.naik1\\Downloads\\nifi-2.5.0-bin\\nifi-2.5.0\\script\\test_graph_with_composite.json")
def inputStream = new FileInputStream(file)
def result = processGraph(inputStream)
inputStream.close()

println "Result:"
println JsonOutput.prettyPrint(JsonOutput.toJson(result))