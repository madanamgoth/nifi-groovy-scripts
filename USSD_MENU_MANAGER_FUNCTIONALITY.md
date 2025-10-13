# USSD Menu Manager - Functional Documentation

## Overview
The USSD Menu Manager is a sophisticated NiFi-based system designed to handle interactive USSD (Unstructured Supplementary Service Data) sessions through directed graph navigation. It supports three primary USSD interaction patterns: basic dial codes, long codes with automatic flow completion, and composite codes for direct menu navigation shortcuts.

## System Capabilities

### 1. Basic USSD Dial Code Processing (`*123#`)
- **Function**: Initiates a new USSD session by parsing directed graph structure
- **Flow**: Creates session → Loads graph nodes → Presents initial menu
- **User Experience**: Traditional USSD interaction starting from root menu
- **Example**: User dials `*123#` → System presents main menu options

### 2. Long Code Automatic Flow Completion (`*123*1*9089*2332#`)
- **Function**: Processes pre-filled USSD codes to automatically navigate through menu flows
- **Flow**: Parses input parameters → Executes navigation path → Completes transaction without user interaction
- **User Experience**: Single input execution with pre-determined values
- **Example**: `*123*1*9089*2332#` → Automatically executes "Send Money" with amount 9089 to number 2332

### 3. Composite Code Direct Navigation (`*123*4444#`)
- **Function**: Enables direct jumping to specific menu nodes using composite codes
- **Flow**: Validates composite code → Maps to target node → Presents associated menu
- **User Experience**: Shortcut access to frequently used services
- **Example**: `*123*4444#` → Direct access to "Send Money" menu bypassing main navigation

## Core Features

### Session Management
- **Session Creation**: Automatic session initialization upon USSD dial
- **Session Persistence**: Maintains user state throughout interaction flow
- **Session Cleanup**: Automatic cleanup after completion or timeout

### Graph Navigation Engine
- **Directed Graph Processing**: Interprets complex menu structures with multiple paths
- **Loop Detection**: Prevents infinite loops with 30-iteration safety limit
- **Orphaned Node Detection**: Identifies and handles disconnected menu nodes
- **Path Optimization**: Generates optimal navigation routes through menu structure

### Cache Management
- **Node Caching**: Stores individual menu nodes for rapid access
- **Template Caching**: Caches API templates for external service calls
- **Composite Code Mapping**: Maintains mapping between composite codes and target nodes
- **Dual Cache Support**: Hazelcast or Redis caching options

### Dynamic Content Generation
- **Multi-language Support**: Supports English, Spanish, French, and Arabic prompts
- **Dynamic Prompts**: Runtime generation of context-aware menu text
- **Template Substitution**: Dynamic variable replacement in prompts
- **Conditional Menus**: Context-dependent menu options

## Technical Workflows

### 1. Graph Parsing and Node Storage
```
Input: Directed Graph JSON
↓
Parse graph structure
↓
Validate node relationships
↓
Split into individual nodes
↓
Store in cache (Hazelcast/Redis)
↓
Create navigation mappings
```

### 2. API Template Management
```
Input: API Templates JSON
↓
Parse template definitions
↓
Validate template structure
↓
Store in cache
↓
Create template lookup index
```

### 3. Composite Code Processing
```
Input: Directed Graph
↓
Identify nodes with composite codes
↓
Generate navigation paths
↓
Create long code mappings
↓
Store composite code → path associations
```

### 4. USSD Session Flow
```
USSD Dial (*123#)
↓
Create Session
↓
Load START node
↓
Present menu options
↓
Process user input
↓
Navigate to next node
↓
Continue until END node
↓
Close session
```

## Node Types and Behaviors

### START Node
- **Purpose**: Entry point for USSD sessions
- **Behavior**: Presents initial menu options
- **Next Action**: Navigate to first menu level

### MENU Node
- **Purpose**: Presents choice options to user
- **Behavior**: Display numbered options, wait for user input
- **Next Action**: Route to selected option (INPUT, MENU, or END)

### INPUT Node
- **Purpose**: Collect user data (amounts, phone numbers, PINs)
- **Behavior**: Present prompt, validate input, store in session
- **Next Action**: Proceed to next node with collected data

### END Node
- **Purpose**: Terminate USSD session
- **Behavior**: Display final message, close session
- **Next Action**: Session cleanup

### ACTION Node
- **Purpose**: Execute business logic or API calls
- **Behavior**: Process data, call external services, return results
- **Next Action**: Navigate based on action outcome

### DYNAMIC-MENU Node
- **Purpose**: Generate menu options dynamically
- **Behavior**: Create menu based on runtime data
- **Next Action**: Present dynamically generated options

## Error Handling and Safety

### Loop Prevention
- **Circular Reference Detection**: Identifies and prevents infinite loops
- **Maximum Iteration Limit**: 30-iteration safety threshold
- **Graceful Degradation**: Generates partial paths when loops detected

### Orphaned Node Management
- **Detection**: Identifies nodes not reachable from START
- **Reporting**: Logs orphaned nodes for graph correction
- **Handling**: Skips processing of unreachable nodes

### Input Validation
- **USSD Code Format**: Validates dial code structure
- **Composite Code Verification**: Ensures composite codes exist in mapping
- **Parameter Validation**: Checks input parameters for completeness

## Integration Points

### External Service Integration
- **Mobiquity Platform**: Core mobile platform integration
- **Third-party APIs**: External service provider connections
- **Database Systems**: Backend data storage and retrieval

### Cache Systems
- **Hazelcast**: Embedded distributed caching
- **Redis**: External cache server option
- **Dual Configuration**: Runtime selection between cache types

### Monitoring and Logging
- **Performance Metrics**: Response time and throughput monitoring
- **Error Tracking**: Comprehensive error logging and alerting
- **Session Analytics**: User interaction pattern analysis

## Business Benefits

### Operational Efficiency
- **Reduced Development Time**: Template-based menu creation
- **Simplified Maintenance**: Centralized graph management
- **Rapid Deployment**: Configuration-driven changes

### User Experience
- **Fast Response Times**: Optimized cache usage
- **Intuitive Navigation**: Logical menu flow design
- **Shortcut Access**: Composite codes for power users

### Scalability
- **High Concurrency**: Support for thousands of simultaneous sessions
- **Distributed Processing**: NiFi cluster deployment capability
- **Elastic Scaling**: Dynamic resource allocation

## Configuration Management

### Graph Configuration
- **JSON-based Definition**: Human-readable graph structure
- **Version Control**: Git-based configuration management
- **Hot Deployment**: Runtime configuration updates

### Cache Configuration
- **Connection Pooling**: Optimized database connections
- **Memory Management**: Efficient cache size management
- **Failover Support**: Automatic failover to backup systems

### Language Configuration
- **Multi-language Prompts**: Centralized translation management
- **Dynamic Language Selection**: User preference-based language switching
- **Template Localization**: Language-specific prompt templates

## Performance Characteristics

### Response Times
- **Menu Display**: < 500ms average response
- **Navigation**: < 200ms between menu levels
- **Composite Code Resolution**: < 100ms direct access

### Throughput
- **Concurrent Sessions**: 10,000+ simultaneous users
- **Transactions per Second**: 5,000+ TPS capability
- **Cache Operations**: 50,000+ operations/second

### Resource Utilization
- **Memory Efficiency**: Optimized node caching
- **CPU Optimization**: Minimal processing overhead
- **Network Efficiency**: Compressed data transmission

## Security Considerations

### Data Protection
- **Session Isolation**: Complete isolation between user sessions
- **Sensitive Data Handling**: Secure storage and transmission of PINs/passwords
- **Audit Logging**: Comprehensive transaction logging

### Access Control
- **Service Authorization**: Controlled access to external services
- **Rate Limiting**: Protection against abuse and DoS attacks
- **Input Sanitization**: Prevention of injection attacks

### Compliance
- **Data Retention**: Configurable data retention policies
- **Privacy Protection**: User data anonymization capabilities
- **Regulatory Compliance**: Adherence to financial service regulations