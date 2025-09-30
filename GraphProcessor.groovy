import org.apache.nifi.processor.io.StreamCallback
import java.nio.charset.StandardCharsets
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

def flowFile = session.get()
if (!flowFile) return

try {
    // 1. Read the incoming graph from the flow file content
    def graphJson = flowFile.read().getText(StandardCharsets.UTF_8)
    def graph = new JsonSlurper().parseText(graphJson)

    // --- Start of the graph processing logic from before ---

    // 2. Create lookup maps for efficient access
    def nodesMap = graph.collectEntries { [(it.id): it] }
    def childToParentMap = [:]
    nodesMap.each { nodeId, node ->
        node.transitions.each { transitionKey, transitionValue ->
            def children = transitionValue instanceof Map ? transitionValue.values() : [transitionValue]
            children.each { childId ->
                if (childId instanceof String) {
                    childToParentMap[childId] = [parentId: nodeId, choice: transitionKey]
                }
            }
        }
    }

    def allPathsMetadata = [:]
    def compositeCodeMap = [:]

    // 3. Iterate through each node to generate its path metadata
    nodesMap.each { nodeId, node ->
        if (node.transitions.get("compositeCode")) {
            compositeCodeMap[node.transitions.compositeCode] = nodeId
        }

        def pathSequence = []
        def requiredInputs = []
        def fixedChoices = []
        boolean isDynamicPath = false
        def automationTargetNode = nodeId
        def currentNodeId = nodeId
        
        while (currentNodeId != null && nodesMap[currentNodeId]?.type != 'START') {
            def parentInfo = childToParentMap[currentNodeId]
            if (!parentInfo) break
            def parentNode = nodesMap[parentInfo.parentId]
            if (!parentNode) break

            if (isDynamicPath) {
                currentNodeId = parentInfo.parentId
                continue
            }
            
            if (parentNode.type == 'DYNAMIC-MENU') {
                isDynamicPath = true
                automationTargetNode = parentNode.id
                pathSequence.clear()
                requiredInputs.clear()
                fixedChoices.clear()
            } else if (parentNode.type == 'INPUT') {
                requiredInputs.add(0, [node_id: parentNode.id, attribute: parentNode.transitions.attribute, prompt: parentNode.transitions.prompt])
                pathSequence.add(0, [type: 'INPUT', attribute: parentNode.transitions.attribute])
            } else if (parentNode.type == 'MENU') {
                fixedChoices.add(0, [from_node: parentNode.id, value: parentInfo.choice])
                pathSequence.add(0, [type: 'MENU_CHOICE', value: parentInfo.choice, from_node: parentNode.id])
            }
            
            currentNodeId = parentInfo.parentId
        }

        allPathsMetadata[nodeId] = [
            automation_target_node: automationTargetNode,
            is_dynamic_path: isDynamicPath,
            path_sequence: pathSequence,
            required_inputs: requiredInputs,
            fixed_choices: fixedChoices
        ]
    }

    // --- End of the graph processing logic ---

    // 4. Generate the list of Redis command objects
    def redisCommands = []

    // Add commands for individual nodes
    nodesMap.each { nodeId, nodeData ->
        redisCommands.add([
            command: "JSON.SET",
            key: "node:${nodeId}",
            path: "\$",
            value: JsonOutput.toJson(nodeData) // Value as a JSON string
        ])
    }

    // Add commands for composite codes
    compositeCodeMap.each { code, nodeId ->
        redisCommands.add([
            command: "HSET",
            key: "composite_codes",
            field: code,
            value: nodeId
        ])
    }

    // Add commands for path metadata
    allPathsMetadata.each { nodeId, metadata ->
        redisCommands.add([
            command: "JSON.SET",
            key: "path_meta:${nodeId}",
            path: "\$",
            value: JsonOutput.toJson(metadata) // Value as a JSON string
        ])
    }

    // 5. Write the JSON array of commands to the output flow file
    flowFile = session.write(flowFile, { outputStream ->
        outputStream.write(JsonOutput.toJson(redisCommands).getBytes(StandardCharsets.UTF_8))
    } as StreamCallback)
    
    session.transfer(flowFile, REL_SUCCESS)
    log.info("Successfully generated ${redisCommands.size()} Redis commands.")

} catch (Exception e) {
    log.error("Error during Graph Processing script: " + e.getMessage(), e)
    flowFile = session.penalize(flowFile)
    session.transfer(flowFile, REL_FAILURE)
}
