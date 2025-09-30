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

    // First pass: Check if any nodes have composite codes
    log.info("Starting first pass: checking for composite codes in graph")
    def hasCompositeCode = false
    for (int i = 0; i < graph.size(); i++) {
        def node = graph[i]
        if (node.compositCode) {
            hasCompositeCode = true
            log.info("Found composite code '${node.compositCode}' in node '${node.id}'")
            break
        }
    }
    
    if (!hasCompositeCode) {
        log.info("No composite codes found in graph - returning empty array")
        return []
    }
    
    log.info("Composite codes detected - proceeding with Redis command generation")

    // Pre-validation: Check for potential graph issues
    log.info("Performing graph validation...")
    def orphanedNodes = []
    def potentialLoops = []
    
    for (int i = 0; i < graph.size(); i++) {
        def node = graph[i]
        if (node.type != 'START') {
            def hasParent = false
            for (int j = 0; j < graph.size(); j++) {
                def parentCandidate = graph[j]
                if (parentCandidate.transitions) {
                    for (def choice in parentCandidate.transitions.keySet()) {
                        def target = parentCandidate.transitions[choice]
                        if (target == node.id || (target instanceof Map && target.values().contains(node.id))) {
                            hasParent = true
                            break
                        }
                    }
                }
                if (hasParent) break
            }
            if (!hasParent) {
                orphanedNodes.add(node.id)
            }
        }
    }
    
    if (orphanedNodes.size() > 0) {
        log.warn("Found ${orphanedNodes.size()} orphaned nodes: ${orphanedNodes.join(', ')}")
    }

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
                    // Direct transition: "200": "target_node_id"
                    childToParentMap[target] = [parentId: parentId, choice: choice]
                    log.debug("Direct transition: ${parentId} --${choice}--> ${target}")
                } else if (target instanceof Map) {
                    // Conditional transition: "200": {"condition1": "target_node_id"}
                    for (def nestedChoice in target.keySet()) {
                        def nestedTarget = target[nestedChoice]
                        childToParentMap[nestedTarget] = [parentId: parentId, choice: choice]
                        log.debug("Conditional transition: ${parentId} --${choice}/${nestedChoice}--> ${nestedTarget}")
                    }
                } else {
                    log.warn("Unexpected transition type in node ${parentId}, choice ${choice}: ${target?.getClass()}")
                }
            }
        }
    }

    def redisCommands = []

    for (def nodeId in nodesMap.keySet()) {
        def node = nodesMap[nodeId]
        if (node.compositCode) {
            log.info("Processing node ${node.id} with composite code '${node.compositCode}'")
            List<String> choicePathParts = []
            String currentNodeId = node.id
            Set<String> visitedNodes = new HashSet<>()
            int maxIterations = 30  // Safety limit

            while (currentNodeId != null && maxIterations > 0) {
                maxIterations--
                
                // Check for circular reference
                if (visitedNodes.contains(currentNodeId)) {
                    log.error("CIRCULAR REFERENCE detected at node ${currentNodeId}!")
                    log.error("Visited path so far: ${visitedNodes.join(' -> ')} -> ${currentNodeId}")
                    
                    // Strategy: Generate partial path up to loop point
                    log.info("Generating partial path up to loop detection point")
                    choicePathParts.add(0, "LOOP_AT_${currentNodeId}")
                    
                    // Try to continue from a safe point (find the START node manually)
                    def loopStartNode = null
                    for (def nodeEntry in nodesMap.entrySet()) {
                        if (nodeEntry.value.type == 'START') {
                            loopStartNode = nodeEntry.value
                            break
                        }
                    }
                    
                    if (loopStartNode != null) {
                        log.info("Found START node ${loopStartNode.id}, using for partial path generation")
                        // We'll let the normal path construction continue with what we have
                    }
                    break
                }
                visitedNodes.add(currentNodeId)
                log.debug("Visiting node: ${currentNodeId} (iteration ${101 - maxIterations})")
                
                def currentNode = nodesMap[currentNodeId]
                if (currentNode == null) {
                    log.error("Node ${currentNodeId} not found in nodesMap! Breaking traversal.")
                    choicePathParts.add(0, "MISSING_NODE")
                    break
                }
                
                if (currentNode.type == 'START') {
                    log.info("Reached START node ${currentNode.id}, stopping traversal")
                    break
                }
                
                def parentEdge = childToParentMap[currentNodeId]
                if (parentEdge == null) {
                    log.warn("Could not find parent for node ID: ${currentNodeId}. This node may be orphaned or unreachable from START. Stopping traversal.")
                    choicePathParts.add(0, "ORPHANED")
                    break
                }

                def parentNode = nodesMap[parentEdge.parentId]
                log.info("Processing parent ${parentNode.id} (type: ${parentNode.type}) with choice '${parentEdge.choice}'")
                
                if (parentNode.type == 'DYNAMIC-MENU') {
                    choicePathParts.add(0, parentEdge.choice)
                    log.info("DYNAMIC-MENU found, stopping traversal")
                    break 
                }
                
                if (parentNode.type != 'ACTION' && parentNode.type != 'START') {
                    if (parentNode.type == 'INPUT') {
                        def attribute = parentNode.storeAttribute ?: parentNode.nextNodeStoreAttribute
                        def pathPart = attribute ? "\${${attribute}}" : "*"
                        choicePathParts.add(0, pathPart)
                        log.info("Added INPUT path part: ${pathPart}")
                    } else {
                        choicePathParts.add(0, parentEdge.choice)
                        log.info("Added MENU choice path part: ${parentEdge.choice}")
                    }
                }
                
                currentNodeId = parentEdge.parentId
            }
            
            if (maxIterations <= 0) {
                log.error("TIMEOUT: Maximum iterations (30) reached for node ${node.id}!")
                log.error("Last visited nodes: ${visitedNodes.join(' -> ')}")
                log.error("This indicates a possible infinite loop or very deep graph structure.")
                
                // Strategy: Generate partial path with timeout marker
                choicePathParts.add(0, "TIMEOUT_AFTER_30_ITERATIONS")
                log.info("Generating partial path with timeout marker")
            }

            // Determine long code generation strategy
            def hasLoopMarker = choicePathParts.any { it.startsWith("LOOP_AT_") }
            def hasTimeoutMarker = choicePathParts.contains("TIMEOUT_AFTER_100_ITERATIONS")
            def hasOrphanedMarker = choicePathParts.contains("ORPHANED")
            def hasMissingNodeMarker = choicePathParts.contains("MISSING_NODE")

            // Build the path 
            def choicePath = choicePathParts.join('*')
            log.info("Raw choice path for ${node.id}: '${choicePath}'")
            
            // Different strategies based on error type
            if (hasLoopMarker) {
                log.warn("LOOP DETECTED: Generating clean partial long code for node ${node.id}")
                // Remove loop marker and generate clean partial path
                choicePathParts = choicePathParts.findAll { !it.startsWith("LOOP_AT_") }
                choicePath = choicePathParts.join('*')
                log.info("Generated clean path (loop handled): '${choicePath}'")
                
            } else if (hasTimeoutMarker) {
                log.warn("TIMEOUT DETECTED: Generating clean truncated long code for node ${node.id}")
                // Remove timeout marker and generate clean path
                choicePathParts = choicePathParts.findAll { it != "TIMEOUT_AFTER_100_ITERATIONS" }
                choicePath = choicePathParts.join('*')
                log.info("Generated clean path (timeout handled): '${choicePath}'")
                
            } else if (hasOrphanedMarker || hasMissingNodeMarker) {
                log.warn("STRUCTURAL ERROR: Skipping Redis command generation for node ${node.id}")
                continue
                
            } else {
                log.info("NORMAL PATH: Generated clean path for node ${node.id}")
            }
            log.info("Initial choice path: '${choicePath}'")
            
            if (startNodeChoice) {
                choicePath = "*${startNodeChoice}*${choicePath}"
            }

            // Clean up any double separators that might occur
            choicePath = choicePath.replaceAll("\\*\\*+", "*")

            choicePath += "#"

            def fieldKey = "*${startNodeChoice}*${node.compositCode}#"

            log.info("Final path for composite code '${node.compositCode}': field='${fieldKey}', value='${choicePath}'")

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

    // 2. Check if the result is valid (null means error, empty array is valid)
    if (redisCommandsHolder.value != null) {
        // 3. Write the result to the FlowFile, modifying it in place.
        flowFile = session.write(flowFile, new OutputStreamCallback() {
            @Override
            void process(OutputStream out) throws IOException {
                out.write(JsonOutput.toJson(redisCommandsHolder.value).getBytes(StandardCharsets.UTF_8))
            }
        })
        session.transfer(flowFile, REL_SUCCESS)
        if (redisCommandsHolder.value.isEmpty()) {
            log.info("No composite codes found in graph - returned empty array for FlowFile ${flowFile.id}")
        } else {
            log.info("Generated ${redisCommandsHolder.value.size()} Redis commands for FlowFile ${flowFile.id}")
        }
    } else {
        log.warn("Processing failed to generate valid result for FlowFile ${flowFile.id}")
        session.transfer(flowFile, REL_FAILURE)
    }
} catch (e) {
    log.error("Failed to process graph for FlowFile ${flowFile.id}", e)
    flowFile = session.penalize(flowFile)
    session.transfer(flowFile, REL_FAILURE)
}