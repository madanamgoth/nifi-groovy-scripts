# USSD Menu Manager - Architecture Documentation

## System Architecture Overview

The USSD Menu Manager is built on Apache NiFi with a microservices-oriented architecture that processes USSD interactions through directed graph navigation. The system employs distributed caching, event-driven processing, and modular Groovy scripts to deliver high-performance USSD services.

```
┌─────────────────────────────────────────────────────────────────┐
│                    USSD GATEWAY LAYER                          │
├─────────────────────────────────────────────────────────────────┤
│  USSD Requests (*123#, *123*1*9089#, *123*4444#)              │
└─────────────────┬───────────────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────────────┐
│                   APACHE NIFI CLUSTER                          │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐            │
│  │ NiFi Node 1 │  │ NiFi Node 2 │  │ NiFi Node N │            │
│  └─────────────┘  └─────────────┘  └─────────────┘            │
└─────────────────┬───────────────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────────────┐
│                  PROCESSING LAYER                               │
├─────────────────────────────────────────────────────────────────┤
│  ┌──────────────────┐ ┌──────────────────┐ ┌─────────────────┐ │
│  │   Graph Parser   │ │ Session Manager  │ │ Cache Manager   │ │
│  │   & Processor    │ │                  │ │                 │ │
│  └──────────────────┘ └──────────────────┘ └─────────────────┘ │
└─────────────────┬───────────────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────────────┐
│                    CACHE LAYER                                  │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐              ┌─────────────────┐          │
│  │   HAZELCAST     │      OR      │      REDIS      │          │
│  │   (Embedded)    │              │   (External)    │          │
│  └─────────────────┘              └─────────────────┘          │
└─────────────────┬───────────────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────────────┐
│                EXTERNAL INTEGRATIONS                           │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │  Mobiquity  │  │ 3rd Party   │  │   Backend Databases     │  │
│  │  Platform   │  │   APIs      │  │                         │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Core Architecture Components

### 1. NiFi Flow Architecture

#### Main Processing Flows

**Flow 1: Graph Loading and Node Distribution**
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   LoadGraph     │───▶│  GraphProcessor │───▶│   CacheLoader   │
│   Controller    │    │    (Groovy)     │    │   (Hazelcast/   │
└─────────────────┘    └─────────────────┘    │    Redis)       │
                                               └─────────────────┘
```

**Flow 2: API Template Management**
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  LoadTemplates  │───▶│ TemplateParser  │───▶│   CacheLoader   │
│   Controller    │    │    (Groovy)     │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

**Flow 3: Composite Code Processing**
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   ParseGraph    │───▶│   Generate      │───▶│    Store        │
│   for Composite │    │  Composite      │    │   Mappings      │
│     Codes       │    │   Long Codes    │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

**Flow 4: USSD Session Processing**
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   USSD Input    │───▶│  Session        │───▶│   Response      │
│   Processor     │    │  Navigator      │    │   Generator     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### 2. Cache Architecture

#### Dual Cache Strategy
```
┌─────────────────────────────────────────────────────────────────┐
│                      CACHE ABSTRACTION LAYER                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────┐              ┌─────────────────┐          │
│  │   HAZELCAST     │              │      REDIS      │          │
│  │   OPTION        │              │     OPTION      │          │
│  ├─────────────────┤              ├─────────────────┤          │
│  │ • Embedded      │              │ • External      │          │
│  │ • In-Memory     │              │ • Persistent    │          │
│  │ • Cluster-aware │              │ • High Perf     │          │
│  │ • Auto-scaling  │              │ • Shared Access │          │
│  └─────────────────┘              └─────────────────┘          │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│                         CACHED DATA TYPES                      │
├─────────────────────────────────────────────────────────────────┤
│  • Menu Nodes           • API Templates                        │
│  • Session Data         • Composite Mappings                   │
│  • User Preferences     • Navigation Paths                     │
└─────────────────────────────────────────────────────────────────┘
```

### 3. Data Models

#### Graph Node Structure
```json
{
  "id": "menu_12345",
  "type": "MENU|INPUT|END|START|ACTION|DYNAMIC-MENU",
  "compositCode": "4444",
  "transitions": {
    "1": "target_node_id",
    "2": "another_node_id"
  },
  "nextNodesMetadata": {
    "1": {
      "nextNodeType": "INPUT",
      "nextNodePrompts": {
        "en": "Enter amount:",
        "es": "Ingrese monto:",
        "fr": "Saisissez le montant:",
        "ar": "أدخل المبلغ:"
      },
      "nextNodeStoreAttribute": "AMOUNT"
    }
  },
  "storeAttribute": "USER_CHOICE"
}
```

#### Session Data Structure
```json
{
  "sessionId": "uuid-session-id",
  "currentNodeId": "menu_12345",
  "userInputs": {
    "AMOUNT": "1000",
    "MSISDN": "1234567890"
  },
  "navigationPath": ["111", "menu_12345"],
  "language": "en",
  "createdAt": "2025-09-30T10:00:00Z",
  "lastActivity": "2025-09-30T10:05:00Z"
}
```

#### Composite Code Mapping
```json
{
  "composite_long_codes": {
    "*111*4444#": "*111*1*${SENDMONEYAMOUNT}*${SENDMONEYMSISDN}#",
    "*111*7634#": "*111*1*${SENDMONEYAMOUNT}*${SENDMONEYMSISDN}*1*${IMTPIN}*${IMTAMOUNT}*${IMTMSISDN}#"
  }
}
```

## Technical Stack

### Core Technologies
```
┌─────────────────────────────────────────────────────────────────┐
│                      TECHNOLOGY STACK                          │
├─────────────────────────────────────────────────────────────────┤
│  Framework:        Apache NiFi 2.5.0                          │
│  Language:         Groovy (Dynamic Scripts)                    │
│  Cache (Option 1): Hazelcast 2.5.0 (Embedded)                │
│  Cache (Option 2): Redis (External)                           │
│  Data Format:      JSON                                        │
│  Protocol:         USSD over HTTP/REST                         │
└─────────────────────────────────────────────────────────────────┘
```

### NiFi Processors Used
```
┌─────────────────────────────────────────────────────────────────┐
│                        NIFI PROCESSORS                         │
├─────────────────────────────────────────────────────────────────┤
│  • ExecuteScript           → Groovy script execution           │
│  • GetFile                 → Graph JSON input                  │
│  • PutFile                 → Output generation                 │
│  • RouteOnContent          → Flow routing                      │
│  • AttributeToJSON         → Data transformation               │
│  • UpdateAttribute         → Session management                │
│  • MonitorActivity         → Health monitoring                 │
└─────────────────────────────────────────────────────────────────┘
```

### Cache Services Configuration
```
┌─────────────────────────────────────────────────────────────────┐
│                      CACHE SERVICES                            │
├─────────────────────────────────────────────────────────────────┤
│  HAZELCAST CONFIGURATION:                                       │
│  • EmbeddedHazelcastCacheManager                               │
│  • HazelcastMapCacheClient                                     │
│  • Cluster-aware distributed caching                          │
│                                                                 │
│  REDIS CONFIGURATION:                                           │
│  • RedisConnectionManager (Custom Pool)                        │
│  • Jedis 6.2.0 Client Library                                 │
│  • Connection pooling optimization                             │
└─────────────────────────────────────────────────────────────────┘
```

## Groovy Script Architecture

### Script Modules
```
┌─────────────────────────────────────────────────────────────────┐
│                       GROOVY SCRIPTS                           │
├─────────────────────────────────────────────────────────────────┤
│  Core Processing:                                               │
│  • GraphProcessor.groovy     → Graph navigation logic          │
│  • redisJsonProcessor.groovy → Data processing engine          │
│                                                                 │
│  Cache Operations:                                              │
│  • getRedisJSON.groovy       → Cache retrieval                 │
│  • putRedisJSON.groovy       → Cache storage                   │
│  • deleteRedisJson.groovy    → Cache cleanup                   │
│  • mergeRedisJson.groovy     → Data merging                    │
│                                                                 │
│  Navigation & Flow:                                             │
│  • actionTransition.groovy   → State transitions               │
│  • createDynamicMenu.groovy  → Dynamic content                 │
│  • nodeAttribute.groovy      → Node processing                 │
│                                                                 │
│  Utilities:                                                     │
│  • redisConnectionManager.groovy → Connection management       │
│  • RedisConnectionManager.groovy → Pool management             │
└─────────────────────────────────────────────────────────────────┘
```

### Connection Management Architecture
```
┌─────────────────────────────────────────────────────────────────┐
│                   CONNECTION POOLING                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              RedisConnectionManager                     │   │
│  │  ┌─────────────────────────────────────────────────────┐ │   │
│  │  │              Connection Pool                        │ │   │
│  │  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │ │   │
│  │  │  │ Conn 1  │ │ Conn 2  │ │ Conn 3  │ │ Conn N  │   │ │   │
│  │  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘   │ │   │
│  │  └─────────────────────────────────────────────────────┘ │   │
│  │  • Thread-safe operations                              │   │
│  │  • Automatic failover                                  │   │
│  │  • Health monitoring                                   │   │
│  │  • Resource cleanup                                    │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  Used by: All Redis operation scripts                          │
│  JAR: redis-utils-1.0.0.jar                                   │
└─────────────────────────────────────────────────────────────────┘
```

## Data Flow Patterns

### 1. Session Initialization Flow
```
USSD Dial (*123#)
      │
      ▼
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Create    │───▶│    Load     │───▶│   Present   │
│  Session    │    │ START Node  │    │ Initial     │
│     ID      │    │from Cache   │    │   Menu      │
└─────────────┘    └─────────────┘    └─────────────┘
```

### 2. Long Code Processing Flow
```
Long Code (*123*1*9089*2332#)
      │
      ▼
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│    Parse    │───▶│   Create    │───▶│  Execute    │───▶│   Return    │
│ Parameters  │    │  Session    │    │   Flow      │    │   Result    │
│             │    │with Values  │    │Automatically│    │             │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
```

### 3. Composite Code Resolution Flow
```
Composite Code (*123*4444#)
      │
      ▼
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Lookup    │───▶│   Resolve   │───▶│  Navigate   │───▶│   Present   │
│Composite Key│    │Target Node  │    │   Direct    │    │Target Menu  │
│  in Cache   │    │             │    │             │    │             │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
```

## Security Architecture

### Data Security Layers
```
┌─────────────────────────────────────────────────────────────────┐
│                      SECURITY LAYERS                           │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                 APPLICATION LAYER                       │   │
│  │  • Input validation and sanitization                   │   │
│  │  • Session isolation and management                    │   │
│  │  • Rate limiting and DoS protection                    │   │
│  └─────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    DATA LAYER                           │   │
│  │  • Sensitive data encryption                           │   │
│  │  • Secure cache storage                                │   │
│  │  • Audit trail logging                                 │   │
│  └─────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                 TRANSPORT LAYER                         │   │
│  │  • TLS/SSL encryption                                  │   │
│  │  • API authentication                                  │   │
│  │  • Certificate management                              │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

## Performance Architecture

### Scalability Design
```
┌─────────────────────────────────────────────────────────────────┐
│                    PERFORMANCE LAYERS                          │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                PROCESSING TIER                          │   │
│  │  • NiFi cluster horizontal scaling                     │   │
│  │  • Load balancing across nodes                         │   │
│  │  • Parallel processing capabilities                    │   │
│  └─────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                  CACHE TIER                             │   │
│  │  • Distributed caching with Hazelcast                  │   │
│  │  • Redis cluster for external cache                    │   │
│  │  • Connection pooling optimization                     │   │
│  └─────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                 MONITORING TIER                         │   │
│  │  • Real-time performance metrics                       │   │
│  │  • Automatic alerting and scaling                      │   │
│  │  • Resource utilization tracking                       │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

## Error Handling Architecture

### Fault Tolerance Design
```
┌─────────────────────────────────────────────────────────────────┐
│                    ERROR HANDLING STRATEGY                     │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              DETECTION LAYER                            │   │
│  │  • Graph loop detection (30-iteration limit)           │   │
│  │  • Orphaned node identification                        │   │
│  │  • Cache connectivity monitoring                       │   │
│  │  • Session timeout detection                           │   │
│  └─────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │               RECOVERY LAYER                            │   │
│  │  • Graceful degradation strategies                     │   │
│  │  • Partial path generation                             │   │
│  │  • Alternative routing options                         │   │
│  │  • Session state recovery                              │   │
│  └─────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              NOTIFICATION LAYER                         │   │
│  │  • Comprehensive error logging                         │   │
│  │  • Real-time alerting                                  │   │
│  │  • Performance impact analysis                         │   │
│  │  • Root cause identification                           │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

## Deployment Architecture

### Environment Configuration
```
┌─────────────────────────────────────────────────────────────────┐
│                    DEPLOYMENT ENVIRONMENTS                     │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                DEVELOPMENT                              │   │
│  │  • Single NiFi node                                    │   │
│  │  • Embedded Hazelcast                                  │   │
│  │  • Local testing and development                       │   │
│  └─────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                   STAGING                               │   │
│  │  • Multi-node NiFi cluster                             │   │
│  │  • External Redis cluster                              │   │
│  │  • Performance and load testing                        │   │
│  └─────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                 PRODUCTION                              │   │
│  │  • High-availability NiFi cluster                      │   │
│  │  • Redis cluster with failover                         │   │
│  │  • Monitoring and alerting                             │   │
│  │  • Disaster recovery capabilities                      │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

## Integration Architecture

### External System Connections
```
┌─────────────────────────────────────────────────────────────────┐
│                  INTEGRATION PATTERNS                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────┐    ┌─────────────────┐                    │
│  │    Mobiquity    │◄──►│  USSD Gateway   │                    │
│  │    Platform     │    │                 │                    │
│  └─────────────────┘    └─────────────────┘                    │
│           ▲                       ▲                             │
│           │                       │                             │
│  ┌─────────────────┐    ┌─────────────────┐                    │
│  │   3rd Party     │◄──►│   NiFi Cluster  │                    │
│  │   API Services  │    │                 │                    │
│  └─────────────────┘    └─────────────────┘                    │
│           ▲                       ▲                             │
│           │                       │                             │
│  ┌─────────────────┐    ┌─────────────────┐                    │
│  │   Backend       │◄──►│  Cache Layer    │                    │
│  │   Databases     │    │ (Redis/Hazel.)  │                    │
│  └─────────────────┘    └─────────────────┘                    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Monitoring and Observability

### Monitoring Stack
```
┌─────────────────────────────────────────────────────────────────┐
│                      MONITORING ARCHITECTURE                   │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                  METRICS COLLECTION                     │   │
│  │  • NiFi processor statistics                           │   │
│  │  • Cache hit/miss ratios                               │   │
│  │  • Session creation/completion rates                   │   │
│  │  • Error rates and patterns                            │   │
│  └─────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                  LOG AGGREGATION                        │   │
│  │  • Centralized logging system                          │   │
│  │  • Structured log format                               │   │
│  │  • Log correlation and analysis                        │   │
│  │  • Audit trail maintenance                             │   │
│  └─────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    ALERTING                             │   │
│  │  • Real-time threshold monitoring                      │   │
│  │  • Escalation procedures                               │   │
│  │  • Performance degradation alerts                      │   │
│  │  • System health notifications                         │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

## Future Architecture Considerations

### Scalability Roadmap
- **Microservices Migration**: Decompose monolithic flows into independent services
- **API Gateway Integration**: Centralized API management and routing
- **Event Streaming**: Apache Kafka integration for real-time event processing
- **Container Orchestration**: Kubernetes deployment for enhanced scalability

### Technology Evolution
- **Cloud-Native Deployment**: Migration to cloud-native NiFi distributions
- **Machine Learning Integration**: Predictive analytics for user behavior
- **Edge Computing**: Distributed processing closer to USSD gateways
- **Blockchain Integration**: Immutable audit trails for financial transactions

This architecture provides a robust, scalable foundation for high-volume USSD processing while maintaining flexibility for future enhancements and integrations.