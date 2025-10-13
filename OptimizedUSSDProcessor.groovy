# NiFi Flow Optimization for High TPS USSD Processing

## Current Architecture Analysis

Based on your directed graph structure, I've identified several optimization opportunities to improve performance for high TPS scenarios.

## Key Performance Bottlenecks Identified

### 1. **Multiple Sequential API Calls**
Your current flow has these potential bottlenecks:
- PIN Validation → Menu Display → Action Execution
- Each step requires separate API calls and processing
- Synchronous processing limiting throughput

### 2. **Complex Conditional Logic**
```json
"queryRecord": "SELECT fetchquery,\n  TRIM(\n    CASE\n      WHEN userStatus = 'Y' AND email ='madan@gmail.com' THEN 'condition1'\n      ELSE 'NoMatch'\n    END\n  ) AS matchedPath\nFROM FLOWFILE"
```

### 3. **Dynamic Menu Processing Overhead**
- JOLT transformations for dynamic menus
- Multiple data transformation steps

## Optimization Strategies

### 1. **Processor Consolidation**

#### Current Flow (Multiple Processors):
```
ExecuteScript[Parse] → ExecuteScript[Validate] → ExecuteScript[Transform] → ExecuteScript[Route]
```

#### Optimized Flow (Single Processor):
```
ExecuteScript[AllInOne] → RouteOnAttribute[Status]
```

### 2. **Cache Pre-loading Strategy**

#### Pre-load Common Data:
- User validation data
- Menu structures
- Template configurations
- API endpoints

### 3. **Batch Processing Implementation**

#### Instead of:
- Process each USSD request individually

#### Implement:
- Batch multiple requests
- Process in parallel
- Return responses asynchronously

## Specific NiFi Optimizations

### 1. **Connection Pool Optimization**

```groovy
// In your Redis connection manager
def poolConfig = new JedisPoolConfig()
poolConfig.setMaxTotal(200)          // Increased from 50
poolConfig.setMaxIdle(50)            // Increased from 10
poolConfig.setMinIdle(20)            // Increased from 5
poolConfig.setMaxWaitMillis(2000)    // Reduced from 5000
poolConfig.setTestOnBorrow(false)    // Disable for speed
poolConfig.setTestOnReturn(false)    // Disable for speed
poolConfig.setTestWhileIdle(true)    // Enable background testing
```

### 2. **Processor Threading Configuration**

#### Recommended Settings:
```
ExecuteScript Processors:
- Concurrent Tasks: 10-20 (based on CPU cores)
- Run Schedule: 0 sec (continuous)
- Yield Duration: 100 ms

RouteOnAttribute:
- Concurrent Tasks: 20-30
- Run Schedule: 0 sec

UpdateAttribute:
- Concurrent Tasks: 15-25
- Run Schedule: 0 sec
```

### 3. **FlowFile Repository Optimization**

#### NiFi Configuration (nifi.properties):
```properties
# Increase repository sizes
nifi.flowfile.repository.directory=./flowfile_repository
nifi.content.repository.directory.default=./content_repository
nifi.provenance.repository.directory.default=./provenance_repository

# Optimize repository settings
nifi.flowfile.repository.checkpoint.interval=20 secs
nifi.content.repository.archive.max.retention.period=1 hours
nifi.provenance.repository.max.storage.time=2 hours

# Increase JVM heap
nifi.jvm.heap.init=2g
nifi.jvm.heap.max=8g
```

## Consolidated Processor Design

Let me create an optimized single processor that handles your entire flow:

<function_calls>
<invoke name="create_file">
<parameter name="content">import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.json.JsonBuilder
import org.apache.nifi.processor.io.InputStreamCallback
import org.apache.nifi.processor.io.OutputStreamCallback
import java.nio.charset.StandardCharsets
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

// Import your Redis connection manager
import com.example.utils.RedisConnectionManager

final Logger log = LoggerFactory.getLogger('OptimizedUSSDProcessor')

// Performance metrics
def performanceMetrics = [
    processedRequests: new AtomicLong(0),
    cacheHits: new AtomicLong(0),
    cacheMisses: new AtomicLong(0),
    processingTimes: []
]

// Local cache for frequently accessed data
def localCache = new ConcurrentHashMap<String, Object>()
def CACHE_TTL = 300000 // 5 minutes in milliseconds

class OptimizedUSSDProcessor {
    
    def redisManager
    def graphNodes
    def apiTemplates
    
    def initialize() {
        // Initialize Redis connection manager
        redisManager = RedisConnectionManager.getInstance()
        
        // Pre-load graph nodes and templates
        preloadCriticalData()
    }
    
    def preloadCriticalData() {
        try {
            // Load all graph nodes into local cache
            def nodeKeys = redisManager.keys("nodes:*")
            nodeKeys.each { key ->
                def nodeData = redisManager.get(key)
                if (nodeData) {
                    localCache.put(key, [data: nodeData, timestamp: System.currentTimeMillis()])
                }
            }
            
            // Load API templates
            def templateKeys = redisManager.keys("templates:*")
            templateKeys.each { key ->
                def templateData = redisManager.get(key)
                if (templateData) {
                    localCache.put(key, [data: templateData, timestamp: System.currentTimeMillis()])
                }
            }
            
            log.info("Pre-loaded ${nodeKeys.size()} nodes and ${templateKeys.size()} templates into local cache")
        } catch (Exception e) {
            log.error("Failed to preload critical data", e)
        }
    }
    
    def getCachedData(String key) {
        def cachedItem = localCache.get(key)
        if (cachedItem && (System.currentTimeMillis() - cachedItem.timestamp) < CACHE_TTL) {
            performanceMetrics.cacheHits.incrementAndGet()
            return cachedItem.data
        }
        
        // Cache miss - fetch from Redis
        performanceMetrics.cacheMisses.incrementAndGet()
        try {
            def data = redisManager.get(key)
            if (data) {
                localCache.put(key, [data: data, timestamp: System.currentTimeMillis()])
                return data
            }
        } catch (Exception e) {
            log.error("Failed to fetch data from Redis for key: ${key}", e)
        }
        return null
    }
    
    def processUSSDRequest(Map request) {
        def startTime = System.currentTimeMillis()
        
        try {
            // Extract request data
            def sessionId = request.sessionId
            def userInput = request.userInput
            def currentNodeId = request.currentNodeId ?: "123" // START node
            
            // Get current node
            def currentNode = getCurrentNode(currentNodeId)
            if (!currentNode) {
                return createErrorResponse("Invalid node: ${currentNodeId}")
            }
            
            // Process based on node type
            def response = processNodeByType(currentNode, userInput, sessionId)
            
            // Update performance metrics
            def processingTime = System.currentTimeMillis() - startTime
            performanceMetrics.processingTimes.add(processingTime)
            performanceMetrics.processedRequests.incrementAndGet()
            
            log.debug("Processed request in ${processingTime}ms for session ${sessionId}")
            
            return response
            
        } catch (Exception e) {
            log.error("Error processing USSD request", e)
            return createErrorResponse("Internal processing error")
        }
    }
    
    def getCurrentNode(String nodeId) {
        def nodeKey = "nodes:${nodeId}"
        def nodeData = getCachedData(nodeKey)
        
        if (nodeData) {
            return new JsonSlurper().parseText(nodeData)
        }
        
        // Fallback to predefined nodes if not in cache
        return getStaticNode(nodeId)
    }
    
    def getStaticNode(String nodeId) {
        // Your static node definitions for fallback
        def staticNodes = [
            "123": [
                id: "123",
                type: "START",
                transitions: ["123": "input_1758807120912_174"],
                nextNodeType: "INPUT",
                nextNodePrompts: [
                    en: "Please enter your pin:",
                    es: "Por favor ingrese su información:",
                    fr: "Veuillez saisir votre entrée:",
                    ar: "يرجى إدخال بياناتك:"
                ],
                nextNodeStoreAttribute: "USERPIN"
            ]
            // Add other critical nodes here
        ]
        
        return staticNodes[nodeId]
    }
    
    def processNodeByType(Map node, String userInput, String sessionId) {
        switch (node.type) {
            case "START":
                return processStartNode(node, sessionId)
            case "INPUT":
                return processInputNode(node, userInput, sessionId)
            case "MENU":
                return processMenuNode(node, userInput, sessionId)
            case "ACTION":
                return processActionNode(node, userInput, sessionId)
            case "DYNAMIC-MENU":
                return processDynamicMenuNode(node, userInput, sessionId)
            case "END":
                return processEndNode(node, sessionId)
            default:
                return createErrorResponse("Unknown node type: ${node.type}")
        }
    }
    
    def processStartNode(Map node, String sessionId) {
        // Create session and present first prompt
        def session = createSession(sessionId)
        session.currentNodeId = node.id
        
        // Store session
        storeSession(sessionId, session)
        
        return [
            type: "INPUT",
            prompt: node.nextNodePrompts.en,
            sessionId: sessionId,
            nextNode: node.transitions["123"]
        ]
    }
    
    def processInputNode(Map node, String userInput, String sessionId) {
        // Store user input and navigate to next node
        def session = getSession(sessionId)
        
        if (node.storeAttribute) {
            session.userInputs[node.storeAttribute] = userInput
        }
        
        def nextNodeId = node.transitions["*"]
        def nextNode = getCurrentNode(nextNodeId)
        
        // Update session
        session.currentNodeId = nextNodeId
        storeSession(sessionId, session)
        
        // If next node is ACTION, process it immediately
        if (nextNode.type == "ACTION") {
            return processActionNode(nextNode, userInput, sessionId)
        }
        
        return [
            type: nextNode.type,
            prompt: nextNode.nextNodePrompts?.en ?: "",
            sessionId: sessionId,
            nextNode: nextNodeId
        ]
    }
    
    def processMenuNode(Map node, String userInput, String sessionId) {
        def session = getSession(sessionId)
        
        // Get selected choice
        def choice = userInput
        def nextNodeId = node.transitions[choice]
        
        if (!nextNodeId) {
            return createErrorResponse("Invalid menu choice: ${choice}")
        }
        
        def nextNode = getCurrentNode(nextNodeId)
        def metadata = node.nextNodesMetadata[choice]
        
        // Update session
        session.currentNodeId = nextNodeId
        session.lastChoice = choice
        storeSession(sessionId, session)
        
        return [
            type: metadata.nextNodeType,
            prompt: metadata.nextNodePrompts?.en ?: "",
            sessionId: sessionId,
            nextNode: nextNodeId
        ]
    }
    
    def processActionNode(Map node, String userInput, String sessionId) {
        def session = getSession(sessionId)
        
        // Execute API call using template
        def templateId = node.templateId
        def apiResponse = executeAPICall(templateId, session.userInputs)
        
        // Process response and determine next node
        def statusCode = apiResponse.statusCode?.toString() ?: "500"
        def nextNodeSpec = node.transitions[statusCode]
        
        if (nextNodeSpec instanceof Map) {
            // Complex conditional routing
            def condition = evaluateCondition(node, apiResponse)
            def nextNodeId = nextNodeSpec[condition]
            def metadata = node.nextNodesMetadata[statusCode][condition]
            
            return [
                type: metadata.nextNodeType,
                prompt: processPromptTemplate(metadata.nextNodePrompts?.en, apiResponse),
                sessionId: sessionId,
                nextNode: nextNodeId,
                apiResponse: apiResponse
            ]
        } else {
            // Simple routing
            def nextNodeId = nextNodeSpec
            def nextNode = getCurrentNode(nextNodeId)
            
            return [
                type: nextNode.type,
                prompt: nextNode.nextNodePrompts?.en ?: "",
                sessionId: sessionId,
                nextNode: nextNodeId,
                apiResponse: apiResponse
            ]
        }
    }
    
    def processDynamicMenuNode(Map node, String userInput, String sessionId) {
        def session = getSession(sessionId)
        
        // Get dynamic menu data from previous API response
        def menuData = session.lastAPIResponse?.billerlist_menu
        
        if (!menuData) {
            return createErrorResponse("No menu data available")
        }
        
        // Store selected biller information
        def selectedBiller = menuData[userInput]
        session.userInputs.selectedBiller = selectedBiller
        
        def nextNodeId = node.transitions["*"]
        def nextNode = getCurrentNode(nextNodeId)
        def metadata = node.nextNodesMetadata["*"]
        
        // Update session
        session.currentNodeId = nextNodeId
        storeSession(sessionId, session)
        
        return [
            type: metadata.nextNodeType,
            prompt: metadata.nextNodePrompts?.en,
            sessionId: sessionId,
            nextNode: nextNodeId
        ]
    }
    
    def processEndNode(Map node, String sessionId) {
        // Clean up session
        deleteSession(sessionId)
        
        return [
            type: "END",
            prompt: "Thank you for using our service!",
            sessionId: sessionId,
            completed: true
        ]
    }
    
    def executeAPICall(String templateId, Map userInputs) {
        // Simplified API call execution
        // In production, this would make actual HTTP calls
        
        def mockResponses = [
            "USERPINVALIDATION": [
                statusCode: 200,
                userStatus: "Y",
                email: "madan@gmail.com"
            ],
            "SENDMONEYONUS": [
                statusCode: 200,
                sendMoneyStatus: "200",
                sendMoneytransactionId: "TXN${System.currentTimeMillis()}"
            ],
            "GETALLBILLERLIST": [
                statusCode: 200,
                billerStatus: "200",
                billerlist_menu: [
                    "1": "Electricity Bill",
                    "2": "Water Bill",
                    "3": "Gas Bill"
                ]
            ],
            "CUSTBILLPAY": [
                statusCode: 200,
                billPaytransactionId: "BILL${System.currentTimeMillis()}"
            ]
        ]
        
        return mockResponses[templateId] ?: [statusCode: 500, error: "Unknown template"]
    }
    
    def evaluateCondition(Map node, Map apiResponse) {
        // Simplified condition evaluation
        // In production, this would execute the actual SQL query
        
        def templateId = node.templateId
        
        switch (templateId) {
            case "USERPINVALIDATION":
                return (apiResponse.userStatus == "Y" && apiResponse.email == "madan@gmail.com") ? "condition1" : "NoMatch"
            case "SENDMONEYONUS":
                return (apiResponse.sendMoneyStatus == "200") ? "condition1" : "NoMatch"
            case "GETALLBILLERLIST":
                return (apiResponse.billerStatus == "200") ? "condition1" : "NoMatch"
            default:
                return "NoMatch"
        }
    }
    
    def processPromptTemplate(String template, Map apiResponse) {
        if (!template) return ""
        
        // Replace placeholders in prompt with actual data
        def result = template
        apiResponse.each { key, value ->
            result = result.replace(":${key}", value.toString())
        }
        return result
    }
    
    def createSession(String sessionId) {
        return [
            sessionId: sessionId,
            currentNodeId: null,
            userInputs: [:],
            lastChoice: null,
            lastAPIResponse: null,
            createdAt: System.currentTimeMillis()
        ]
    }
    
    def getSession(String sessionId) {
        def sessionKey = "session:${sessionId}"
        def sessionData = getCachedData(sessionKey)
        
        if (sessionData) {
            return new JsonSlurper().parseText(sessionData)
        }
        
        // Create new session if not found
        return createSession(sessionId)
    }
    
    def storeSession(String sessionId, Map session) {
        def sessionKey = "session:${sessionId}"
        def sessionJson = JsonOutput.toJson(session)
        
        try {
            redisManager.setex(sessionKey, 1800, sessionJson) // 30 minute expiry
            localCache.put(sessionKey, [data: sessionJson, timestamp: System.currentTimeMillis()])
        } catch (Exception e) {
            log.error("Failed to store session ${sessionId}", e)
        }
    }
    
    def deleteSession(String sessionId) {
        def sessionKey = "session:${sessionId}"
        try {
            redisManager.del(sessionKey)
            localCache.remove(sessionKey)
        } catch (Exception e) {
            log.error("Failed to delete session ${sessionId}", e)
        }
    }
    
    def createErrorResponse(String message) {
        return [
            type: "ERROR",
            prompt: message,
            error: true
        ]
    }
    
    def getPerformanceMetrics() {
        def avgProcessingTime = performanceMetrics.processingTimes.size() > 0 ? 
            performanceMetrics.processingTimes.sum() / performanceMetrics.processingTimes.size() : 0
            
        return [
            processedRequests: performanceMetrics.processedRequests.get(),
            cacheHitRatio: performanceMetrics.cacheHits.get() / 
                (performanceMetrics.cacheHits.get() + performanceMetrics.cacheMisses.get()) * 100,
            averageProcessingTime: avgProcessingTime,
            localCacheSize: localCache.size()
        ]
    }
}

// Main processing logic
def processor = new OptimizedUSSDProcessor()
processor.initialize()

def flowFile = session.get()
if (!flowFile) {
    return
}

try {
    def requestHolder = new Object() { Map value }
    
    session.read(flowFile, new InputStreamCallback() {
        @Override
        void process(InputStream inputStream) throws IOException {
            def text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
            requestHolder.value = new JsonSlurper().parseText(text)
        }
    })
    
    // Process the USSD request
    def response = processor.processUSSDRequest(requestHolder.value)
    
    // Write response
    flowFile = session.write(flowFile, new OutputStreamCallback() {
        @Override
        void process(OutputStream outputStream) throws IOException {
            outputStream.write(JsonOutput.toJson(response).getBytes(StandardCharsets.UTF_8))
        }
    })
    
    // Add performance attributes
    def metrics = processor.getPerformanceMetrics()
    flowFile = session.putAttribute(flowFile, "performance.cacheHitRatio", metrics.cacheHitRatio.toString())
    flowFile = session.putAttribute(flowFile, "performance.avgProcessingTime", metrics.averageProcessingTime.toString())
    
    session.transfer(flowFile, REL_SUCCESS)
    
    log.info("Processed USSD request successfully. Cache hit ratio: ${metrics.cacheHitRatio}%")
    
} catch (Exception e) {
    log.error("Failed to process USSD request", e)
    flowFile = session.penalize(flowFile)
    session.transfer(flowFile, REL_FAILURE)
}