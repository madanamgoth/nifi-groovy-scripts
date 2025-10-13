# USSD Menu Manager - Pseudocode and System Diagrams

## System Flow Pseudocode

### 1. Main USSD Processing Flow

```pseudocode
FUNCTION processUSSDRequest(ussdInput, sessionId):
    // Parse USSD input to determine type
    ussdType = parseUSSDType(ussdInput)
    
    SWITCH ussdType:
        CASE "BASIC_DIAL":
            result = processBasicDial(ussdInput, sessionId)
        CASE "LONG_CODE":
            result = processLongCode(ussdInput, sessionId)
        CASE "COMPOSITE_CODE":
            result = processCompositeCode(ussdInput, sessionId)
        DEFAULT:
            result = generateErrorResponse("Invalid USSD format")
    
    RETURN result
END FUNCTION

FUNCTION parseUSSDType(ussdInput):
    IF ussdInput MATCHES "*123#":
        RETURN "BASIC_DIAL"
    ELSE IF ussdInput MATCHES "*123*[params]*#":
        IF containsCompositeCode(ussdInput):
            RETURN "COMPOSITE_CODE"
        ELSE:
            RETURN "LONG_CODE"
    ELSE:
        RETURN "INVALID"
END FUNCTION
```

### 2. Basic Dial Processing (*123#)

```pseudocode
FUNCTION processBasicDial(ussdInput, sessionId):
    // Create new session
    session = createSession(sessionId)
    
    // Load START node from cache
    startNode = cacheManager.get("nodes", "START_NODE_ID")
    
    IF startNode IS NULL:
        RETURN generateErrorResponse("Service temporarily unavailable")
    
    // Set session state
    session.currentNodeId = startNode.id
    session.navigationPath = [startNode.id]
    
    // Generate menu response
    menuResponse = generateMenuResponse(startNode, session.language)
    
    // Store session
    cacheManager.put("sessions", sessionId, session)
    
    RETURN menuResponse
END FUNCTION

FUNCTION generateMenuResponse(node, language):
    prompts = node.nextNodePrompts[language]
    options = []
    
    FOR EACH choice IN node.transitions:
        options.ADD(choice + ". " + getChoiceDescription(choice, node, language))
    
    response = formatUSSDResponse(prompts, options)
    RETURN response
END FUNCTION
```

### 3. Long Code Processing (*123*1*9089*2332#)

```pseudocode
FUNCTION processLongCode(ussdInput, sessionId):
    // Parse parameters from USSD string
    parameters = extractParameters(ussdInput)
    
    // Create session with pre-filled values
    session = createSession(sessionId)
    session.userInputs = mapParametersToAttributes(parameters)
    
    // Execute flow automatically
    result = executeAutomaticFlow(session, parameters)
    
    RETURN result
END FUNCTION

FUNCTION executeAutomaticFlow(session, parameters):
    currentNode = getStartNode()
    paramIndex = 0
    
    WHILE currentNode.type != "END" AND paramIndex < parameters.length:
        SWITCH currentNode.type:
            CASE "START":
                // Navigate to first menu choice
                choice = parameters[paramIndex++]
                currentNode = getNextNode(currentNode, choice)
                
            CASE "MENU":
                // Use parameter as menu choice
                choice = parameters[paramIndex++]
                currentNode = getNextNode(currentNode, choice)
                
            CASE "INPUT":
                // Store parameter value
                value = parameters[paramIndex++]
                session.userInputs[currentNode.storeAttribute] = value
                currentNode = getNextNode(currentNode, "*")
                
            CASE "ACTION":
                // Execute action with collected data
                actionResult = executeAction(currentNode, session.userInputs)
                currentNode = getNextNode(currentNode, actionResult.status)
        
        // Update session navigation path
        session.navigationPath.ADD(currentNode.id)
    
    // Generate final response
    IF currentNode.type == "END":
        RETURN generateEndResponse(currentNode, session)
    ELSE:
        RETURN generateErrorResponse("Incomplete flow execution")
END FUNCTION
```

### 4. Composite Code Processing (*123*4444#)

```pseudocode
FUNCTION processCompositeCode(ussdInput, sessionId):
    // Extract composite code
    compositeCode = extractCompositeCode(ussdInput)
    
    // Lookup composite code mapping
    compositeKey = buildCompositeKey(ussdInput, compositeCode)
    longCodePattern = cacheManager.get("composite_long_codes", compositeKey)
    
    IF longCodePattern IS NULL:
        RETURN generateErrorResponse("Invalid composite code")
    
    // Find target node
    targetNode = findCompositeTargetNode(compositeCode)
    
    IF targetNode IS NULL:
        RETURN generateErrorResponse("Composite code target not found")
    
    // Create session and navigate directly to target
    session = createSession(sessionId)
    session.currentNodeId = targetNode.id
    session.navigationPath = [targetNode.id]
    
    // Generate response for target node
    response = generateMenuResponse(targetNode, session.language)
    
    // Store session
    cacheManager.put("sessions", sessionId, session)
    
    RETURN response
END FUNCTION

FUNCTION findCompositeTargetNode(compositeCode):
    allNodes = cacheManager.getAll("nodes")
    
    FOR EACH node IN allNodes:
        IF node.compositCode == compositeCode:
            RETURN node
    
    RETURN NULL
END FUNCTION
```

### 5. Graph Processing and Composite Code Generation

```pseudocode
FUNCTION processGraphForCompositeCode(graphData):
    redisCommands = []
    nodesMap = buildNodesMap(graphData)
    parentChildMap = buildParentChildMap(nodesMap)
    
    FOR EACH node IN nodesMap.values():
        IF node.compositCode IS NOT NULL:
            longCodePath = generateLongCodePath(node, nodesMap, parentChildMap)
            
            IF longCodePath IS NOT NULL:
                redisCommand = createRedisCommand(node.compositCode, longCodePath)
                redisCommands.ADD(redisCommand)
    
    RETURN redisCommands
END FUNCTION

FUNCTION generateLongCodePath(targetNode, nodesMap, parentChildMap):
    choicePathParts = []
    currentNodeId = targetNode.id
    visitedNodes = SET()
    maxIterations = 30
    
    WHILE currentNodeId IS NOT NULL AND maxIterations > 0:
        maxIterations--
        
        // Check for circular reference
        IF currentNodeId IN visitedNodes:
            LOG("Circular reference detected at " + currentNodeId)
            BREAK
        
        visitedNodes.ADD(currentNodeId)
        currentNode = nodesMap[currentNodeId]
        
        // Stop at START node
        IF currentNode.type == "START":
            BREAK
        
        // Get parent relationship
        parentEdge = parentChildMap[currentNodeId]
        IF parentEdge IS NULL:
            LOG("Orphaned node detected: " + currentNodeId)
            BREAK
        
        parentNode = nodesMap[parentEdge.parentId]
        
        // Add path component based on parent type
        SWITCH parentNode.type:
            CASE "MENU":
                choicePathParts.ADD_TO_FRONT(parentEdge.choice)
            CASE "INPUT":
                attribute = parentNode.storeAttribute
                choicePathParts.ADD_TO_FRONT("${" + attribute + "}")
            CASE "DYNAMIC-MENU":
                choicePathParts.ADD_TO_FRONT(parentEdge.choice)
                BREAK  // Stop at dynamic menu
        
        currentNodeId = parentEdge.parentId
    
    // Build final path
    choicePath = JOIN(choicePathParts, "*")
    RETURN "*111*" + choicePath + "#"
END FUNCTION
```

### 6. Session Management

```pseudocode
FUNCTION createSession(sessionId):
    session = {
        sessionId: sessionId,
        currentNodeId: NULL,
        userInputs: {},
        navigationPath: [],
        language: "en",
        createdAt: getCurrentTimestamp(),
        lastActivity: getCurrentTimestamp()
    }
    RETURN session
END FUNCTION

FUNCTION updateSession(sessionId, nodeId, userInput):
    session = cacheManager.get("sessions", sessionId)
    
    IF session IS NULL:
        THROW SessionNotFoundException(sessionId)
    
    session.currentNodeId = nodeId
    session.lastActivity = getCurrentTimestamp()
    
    IF userInput IS NOT NULL:
        currentNode = getCurrentNode(session)
        IF currentNode.storeAttribute IS NOT NULL:
            session.userInputs[currentNode.storeAttribute] = userInput
    
    session.navigationPath.ADD(nodeId)
    cacheManager.put("sessions", sessionId, session)
    
    RETURN session
END FUNCTION

FUNCTION cleanupExpiredSessions():
    currentTime = getCurrentTimestamp()
    sessionTimeout = getSessionTimeoutMinutes()
    
    allSessions = cacheManager.getAll("sessions")
    
    FOR EACH session IN allSessions:
        IF (currentTime - session.lastActivity) > sessionTimeout:
            cacheManager.delete("sessions", session.sessionId)
            LOG("Cleaned up expired session: " + session.sessionId)
END FUNCTION
```

### 7. Cache Management Operations

```pseudocode
FUNCTION loadGraphToCache(graphJsonData):
    graph = parseJSON(graphJsonData)
    
    FOR EACH node IN graph:
        // Store individual nodes
        cacheManager.put("nodes", node.id, node)
        
        // Index nodes by type
        cacheManager.addToSet("nodes_by_type:" + node.type, node.id)
        
        // Index composite codes
        IF node.compositCode IS NOT NULL:
            cacheManager.put("composite_nodes", node.compositCode, node.id)
    
    LOG("Loaded " + graph.size() + " nodes to cache")
END FUNCTION

FUNCTION loadTemplateToCache(templateJsonData):
    templates = parseJSON(templateJsonData)
    
    FOR EACH template IN templates:
        cacheManager.put("api_templates", template.id, template)
    
    LOG("Loaded " + templates.size() + " templates to cache")
END FUNCTION

FUNCTION getNodeFromCache(nodeId):
    node = cacheManager.get("nodes", nodeId)
    
    IF node IS NULL:
        LOG("Node not found in cache: " + nodeId)
        THROW NodeNotFoundException(nodeId)
    
    RETURN node
END FUNCTION
```

## System Interaction Diagrams

### 1. Basic USSD Flow Sequence Diagram

```
User           USSD Gateway    NiFi Cluster     Cache Layer      External APIs
 |                  |               |               |               |
 |-- *123# -------->|               |               |               |
 |                  |--Process----->|               |               |
 |                  |               |--Get START--->|               |
 |                  |               |<--Node Data---|               |
 |                  |               |--Store------->|               |
 |                  |               |  Session      |               |
 |                  |<--Menu--------|               |               |
 |<--Menu Display---|               |               |               |
 |                  |               |               |               |
 |-- Choice 1 ----->|               |               |               |
 |                  |--Process----->|               |               |
 |                  |               |--Get Node---->|               |
 |                  |               |<--Node Data---|               |
 |                  |               |--Update------>|               |
 |                  |               |  Session      |               |
 |                  |<--Next Menu---|               |               |
 |<--Menu Display---|               |               |               |
```

### 2. Long Code Processing Sequence Diagram

```
User           USSD Gateway    NiFi Cluster     Cache Layer      External APIs
 |                  |               |               |               |
 |--*123*1*100*---->|               |               |               |
 |  987654321#      |               |               |               |
 |                  |--Process----->|               |               |
 |                  |  Long Code    |               |               |
 |                  |               |--Parse Params->|               |
 |                  |               |--Create------->|               |
 |                  |               |  Session      |               |
 |                  |               |--Execute Flow->|               |
 |                  |               |--Call API------|-------------->|
 |                  |               |<--API Response|<---------------|
 |                  |               |--Get END----->|               |
 |                  |               |  Node         |               |
 |                  |<--Final-------|               |               |
 |                  |  Response     |               |               |
 |<--Confirmation---|               |               |               |
```

### 3. Composite Code Flow Sequence Diagram

```
User           USSD Gateway    NiFi Cluster     Cache Layer      External APIs
 |                  |               |               |               |
 |-- *123*4444# --->|               |               |               |
 |                  |--Process----->|               |               |
 |                  |  Composite    |               |               |
 |                  |               |--Lookup------>|               |
 |                  |               |  Composite    |               |
 |                  |               |<--Long Code---|               |
 |                  |               |--Find Target->|               |
 |                  |               |  Node         |               |
 |                  |               |<--Node Data---|               |
 |                  |               |--Create------>|               |
 |                  |               |  Session      |               |
 |                  |<--Target------|               |               |
 |                  |  Menu         |               |               |
 |<--Direct Access--|               |               |               |
```

### 4. Graph Processing Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    GRAPH PROCESSING FLOW                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐         │
│  │   Graph     │───▶│   Parse     │───▶│   Validate  │         │
│  │   JSON      │    │   Nodes     │    │   Structure │         │
│  │   Input     │    │             │    │             │         │
│  └─────────────┘    └─────────────┘    └─────────────┘         │
│         │                   │                   │               │
│         ▼                   ▼                   ▼               │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐         │
│  │   Split     │───▶│   Index     │───▶│   Cache     │         │
│  │   Nodes     │    │   By Type   │    │   Storage   │         │
│  │             │    │             │    │             │         │
│  └─────────────┘    └─────────────┘    └─────────────┘         │
│         │                   │                   │               │
│         ▼                   ▼                   ▼               │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐         │
│  │  Generate   │───▶│   Build     │───▶│   Store     │         │
│  │ Composite   │    │   Redis     │    │   Redis     │         │
│  │   Codes     │    │  Commands   │    │  Commands   │         │
│  └─────────────┘    └─────────────┘    └─────────────┘         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 5. Cache Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                      CACHE ARCHITECTURE                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                 APPLICATION LAYER                       │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐ │   │
│  │  │    NiFi     │  │   Session   │  │     Graph       │ │   │
│  │  │ Processors  │  │  Manager    │  │   Processor     │ │   │
│  │  └─────────────┘  └─────────────┘  └─────────────────┘ │   │
│  └─────────────────────┬───────────────────────────────────┘   │
│                        │                                       │
│  ┌─────────────────────▼───────────────────────────────────┐   │
│  │               CACHE ABSTRACTION                         │   │
│  │  ┌─────────────────────────────────────────────────────┐ │   │
│  │  │        RedisConnectionManager                       │ │   │
│  │  │        (Connection Pooling)                         │ │   │
│  │  └─────────────────────────────────────────────────────┘ │   │
│  └─────────────────────┬───────────────────────────────────┘   │
│                        │                                       │
│  ┌─────────────────────▼───────────────────────────────────┐   │
│  │                CACHE STORAGE                            │   │
│  │  ┌─────────────┐              ┌─────────────────────────┐ │   │
│  │  │ HAZELCAST   │      OR      │        REDIS            │ │   │
│  │  │ ┌─────────┐ │              │ ┌─────────────────────┐ │ │   │
│  │  │ │ Nodes   │ │              │ │      Nodes          │ │ │   │
│  │  │ ├─────────┤ │              │ ├─────────────────────┤ │ │   │
│  │  │ │Sessions │ │              │ │     Sessions        │ │ │   │
│  │  │ ├─────────┤ │              │ ├─────────────────────┤ │ │   │
│  │  │ │Templates│ │              │ │     Templates       │ │ │   │
│  │  │ ├─────────┤ │              │ ├─────────────────────┤ │ │   │
│  │  │ │Composite│ │              │ │ Composite Mappings  │ │ │   │
│  │  │ │Mappings │ │              │ │                     │ │ │   │
│  │  │ └─────────┘ │              │ └─────────────────────┘ │ │   │
│  │  └─────────────┘              └─────────────────────────┘ │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 6. Session State Machine Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    SESSION STATE MACHINE                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│        ┌─────────────┐                                          │
│        │    IDLE     │                                          │
│        │  (No Session)│                                         │
│        └──────┬──────┘                                          │
│               │ USSD Dial                                       │
│               ▼                                                 │
│        ┌─────────────┐                                          │
│        │  CREATED    │                                          │
│        │ (Session    │                                          │
│        │ Initialized)│                                          │
│        └──────┬──────┘                                          │
│               │ Load START Node                                 │
│               ▼                                                 │
│        ┌─────────────┐                                          │
│    ┌──▶│   ACTIVE    │◄─┐                                      │
│    │   │ (Navigating │  │                                      │
│    │   │    Menu)    │  │ User Input                           │
│    │   └──────┬──────┘  │                                      │
│    │          │         │                                      │
│    │          ▼         │                                      │
│    │   ┌─────────────┐  │                                      │
│    │   │ PROCESSING  │──┘                                      │
│    │   │ (Executing  │                                         │
│    │   │   Action)   │                                         │
│    │   └──────┬──────┘                                          │
│    │          │                                                 │
│    │          ▼                                                 │
│    │   ┌─────────────┐                                          │
│    └───│  COMPLETED  │                                          │
│        │   (END      │                                          │
│        │   Reached)  │                                          │
│        └──────┬──────┘                                          │
│               │ Session Cleanup                                 │
│               ▼                                                 │
│        ┌─────────────┐                                          │
│        │   EXPIRED   │                                          │
│        │  (Cleaned   │                                          │
│        │     Up)     │                                          │
│        └─────────────┘                                          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 7. Error Handling Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    ERROR HANDLING FLOW                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐         │
│  │   Error     │───▶│  Classify   │───▶│   Handle    │         │
│  │ Detection   │    │   Error     │    │   Based on  │         │
│  │             │    │    Type     │    │    Type     │         │
│  └─────────────┘    └─────────────┘    └─────────────┘         │
│         │                   │                   │               │
│         ▼                   ▼                   ▼               │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐         │
│  │   Graph     │    │   Cache     │    │  Session    │         │
│  │   Loops     │    │ Connection  │    │   Timeout   │         │
│  │             │    │   Errors    │    │             │         │
│  └─────────────┘    └─────────────┘    └─────────────┘         │
│         │                   │                   │               │
│         ▼                   ▼                   ▼               │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐         │
│  │  Generate   │    │   Retry     │    │   Clean     │         │
│  │  Partial    │    │   with      │    │   Session   │         │
│  │   Path      │    │  Backup     │    │             │         │
│  └─────────────┘    └─────────────┘    └─────────────┘         │
│         │                   │                   │               │
│         └─────────┬─────────┴─────────┬─────────┘               │
│                   ▼                   ▼                         │
│            ┌─────────────┐    ┌─────────────┐                   │
│            │    Log      │    │   Send      │                   │
│            │   Error     │    │   Error     │                   │
│            │             │    │  Response   │                   │
│            └─────────────┘    └─────────────┘                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Performance Optimization Pseudocode

### 1. Connection Pool Management

```pseudocode
CLASS RedisConnectionPool:
    PRIVATE pool
    PRIVATE maxConnections = 50
    PRIVATE minConnections = 10
    PRIVATE timeout = 5000ms
    
    FUNCTION initialize():
        poolConfig = createPoolConfig()
        poolConfig.maxTotal = maxConnections
        poolConfig.minIdle = minConnections
        poolConfig.maxWaitMillis = timeout
        
        pool = createJedisPool(poolConfig, host, port)
        
        // Health check
        testConnection = pool.getResource()
        testConnection.ping()
        testConnection.close()
    
    FUNCTION getConnection():
        TRY:
            connection = pool.getResource()
            RETURN connection
        CATCH PoolExhaustedException:
            LOG("Connection pool exhausted")
            THROW ServiceUnavailableException()
    
    FUNCTION returnConnection(connection):
        connection.close()  // Returns to pool
    
    FUNCTION getPoolStats():
        RETURN {
            active: pool.getNumActive(),
            idle: pool.getNumIdle(),
            waiters: pool.getNumWaiters()
        }
END CLASS
```

### 2. Cache Optimization

```pseudocode
FUNCTION optimizedCacheGet(key, fallbackFunction):
    // L1 Cache (In-memory)
    value = localCache.get(key)
    IF value IS NOT NULL:
        RETURN value
    
    // L2 Cache (Redis/Hazelcast)
    value = distributedCache.get(key)
    IF value IS NOT NULL:
        localCache.put(key, value, TTL=300)  // 5 minute local cache
        RETURN value
    
    // Fallback to data source
    value = fallbackFunction()
    IF value IS NOT NULL:
        distributedCache.put(key, value, TTL=3600)  // 1 hour distributed cache
        localCache.put(key, value, TTL=300)
    
    RETURN value
END FUNCTION

FUNCTION batchCacheLoad(keys):
    results = {}
    missingKeys = []
    
    // Check local cache first
    FOR EACH key IN keys:
        value = localCache.get(key)
        IF value IS NOT NULL:
            results[key] = value
        ELSE:
            missingKeys.ADD(key)
    
    // Batch get from distributed cache
    IF missingKeys.size() > 0:
        distributedValues = distributedCache.mget(missingKeys)
        
        FOR i = 0 TO missingKeys.size():
            key = missingKeys[i]
            value = distributedValues[i]
            IF value IS NOT NULL:
                results[key] = value
                localCache.put(key, value, TTL=300)
    
    RETURN results
END FUNCTION
```

This comprehensive pseudocode and diagram documentation provides a complete technical reference for the USSD Menu Manager system, suitable for presentation to technical teams and product owners. The pseudocode demonstrates the core algorithms and logic flows, while the diagrams illustrate the system architecture and data flows visually.