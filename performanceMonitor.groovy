import groovy.json.JsonOutput
import org.apache.nifi.processor.io.OutputStreamCallback
import java.nio.charset.StandardCharsets
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong
import java.lang.management.ManagementFactory
import java.lang.management.MemoryMXBean
import java.lang.management.ThreadMXBean

final Logger log = LoggerFactory.getLogger('PerformanceMonitor')

class PerformanceMonitor {
    
    static def performanceStats = [
        totalRequests: new AtomicLong(0),
        successfulRequests: new AtomicLong(0),
        failedRequests: new AtomicLong(0),
        totalProcessingTime: new AtomicLong(0),
        maxProcessingTime: new AtomicLong(0),
        minProcessingTime: new AtomicLong(Long.MAX_VALUE),
        cacheHits: new AtomicLong(0),
        cacheMisses: new AtomicLong(0),
        apiCalls: new AtomicLong(0),
        errors: new AtomicLong(0)
    ]
    
    static def startTime = System.currentTimeMillis()
    
    def generatePerformanceReport() {
        def currentTime = System.currentTimeMillis()
        def uptime = currentTime - startTime
        
        def totalReqs = performanceStats.totalRequests.get()
        def successReqs = performanceStats.successfulRequests.get()
        def failedReqs = performanceStats.failedRequests.get()
        def totalProcTime = performanceStats.totalProcessingTime.get()
        def maxProcTime = performanceStats.maxProcessingTime.get()
        def minProcTime = performanceStats.minProcessingTime.get()
        def cacheHits = performanceStats.cacheHits.get()
        def cacheMisses = performanceStats.cacheMisses.get()
        def apiCalls = performanceStats.apiCalls.get()
        def errors = performanceStats.errors.get()
        
        // Calculate derived metrics
        def avgProcessingTime = totalReqs > 0 ? totalProcTime / totalReqs : 0
        def successRate = totalReqs > 0 ? (successReqs / totalReqs) * 100 : 0
        def cacheHitRate = (cacheHits + cacheMisses) > 0 ? (cacheHits / (cacheHits + cacheMisses)) * 100 : 0
        def requestsPerSecond = uptime > 0 ? (totalReqs / (uptime / 1000.0)) : 0
        def errorRate = totalReqs > 0 ? (errors / totalReqs) * 100 : 0
        
        // Get JVM metrics
        def memoryBean = ManagementFactory.getMemoryMXBean()
        def threadBean = ManagementFactory.getThreadMXBean()
        
        def heapMemory = memoryBean.getHeapMemoryUsage()
        def nonHeapMemory = memoryBean.getNonHeapMemoryUsage()
        
        def report = [
            timestamp: new Date().toString(),
            uptime: [
                milliseconds: uptime,
                seconds: Math.round(uptime / 1000),
                minutes: Math.round(uptime / 60000),
                hours: Math.round(uptime / 3600000)
            ],
            requests: [
                total: totalReqs,
                successful: successReqs,
                failed: failedReqs,
                perSecond: Math.round(requestsPerSecond * 100) / 100,
                successRate: Math.round(successRate * 100) / 100,
                errorRate: Math.round(errorRate * 100) / 100
            ],
            performance: [
                averageProcessingTime: Math.round(avgProcessingTime * 100) / 100,
                maxProcessingTime: maxProcTime,
                minProcessingTime: minProcTime == Long.MAX_VALUE ? 0 : minProcTime,
                totalProcessingTime: totalProcTime
            ],
            cache: [
                hits: cacheHits,
                misses: cacheMisses,
                hitRate: Math.round(cacheHitRate * 100) / 100
            ],
            api: [
                totalCalls: apiCalls,
                callsPerSecond: uptime > 0 ? Math.round((apiCalls / (uptime / 1000.0)) * 100) / 100 : 0
            ],
            jvm: [
                heapMemory: [
                    used: heapMemory.getUsed(),
                    max: heapMemory.getMax(),
                    committed: heapMemory.getCommitted(),
                    usagePercentage: heapMemory.getMax() > 0 ? Math.round((heapMemory.getUsed() / heapMemory.getMax()) * 10000) / 100 : 0
                ],
                nonHeapMemory: [
                    used: nonHeapMemory.getUsed(),
                    max: nonHeapMemory.getMax(),
                    committed: nonHeapMemory.getCommitted()
                ],
                threads: [
                    active: threadBean.getThreadCount(),
                    peak: threadBean.getPeakThreadCount(),
                    daemon: threadBean.getDaemonThreadCount()
                ]
            ],
            thresholds: [
                performance: [
                    avgProcessingTimeWarning: avgProcessingTime > 500,
                    maxProcessingTimeWarning: maxProcTime > 2000,
                    requestsPerSecondLow: requestsPerSecond < 100
                ],
                cache: [
                    hitRateLow: cacheHitRate < 90
                ],
                jvm: [
                    heapUsageHigh: (heapMemory.getUsed() / heapMemory.getMax()) > 0.8,
                    threadCountHigh: threadBean.getThreadCount() > 200
                ],
                errors: [
                    errorRateHigh: errorRate > 5
                ]
            ]
        ]
        
        return report
    }
    
    def generateOptimizationRecommendations(Map report) {
        def recommendations = []
        
        // Performance recommendations
        if (report.performance.averageProcessingTime > 500) {
            recommendations << [
                type: "PERFORMANCE",
                priority: "HIGH",
                issue: "High average processing time",
                recommendation: "Consider enabling local caching or optimizing database queries",
                currentValue: report.performance.averageProcessingTime,
                targetValue: "< 500ms"
            ]
        }
        
        if (report.requests.perSecond < 100) {
            recommendations << [
                type: "THROUGHPUT",
                priority: "MEDIUM",
                issue: "Low request throughput",
                recommendation: "Increase processor concurrent tasks or optimize bottlenecks",
                currentValue: report.requests.perSecond,
                targetValue: "> 100 req/s"
            ]
        }
        
        // Cache recommendations
        if (report.cache.hitRate < 90) {
            recommendations << [
                type: "CACHE",
                priority: "HIGH",
                issue: "Low cache hit rate",
                recommendation: "Review cache TTL settings, increase cache size, or pre-load critical data",
                currentValue: "${report.cache.hitRate}%",
                targetValue: "> 90%"
            ]
        }
        
        // JVM recommendations
        if (report.jvm.heapMemory.usagePercentage > 80) {
            recommendations << [
                type: "MEMORY",
                priority: "HIGH",
                issue: "High heap memory usage",
                recommendation: "Increase JVM heap size or optimize memory usage",
                currentValue: "${report.jvm.heapMemory.usagePercentage}%",
                targetValue: "< 80%"
            ]
        }
        
        if (report.jvm.threads.active > 200) {
            recommendations << [
                type: "THREADS",
                priority: "MEDIUM",
                issue: "High thread count",
                recommendation: "Review processor threading configuration",
                currentValue: report.jvm.threads.active,
                targetValue: "< 200"
            ]
        }
        
        // Error rate recommendations
        if (report.thresholds.errors.errorRateHigh) {
            recommendations << [
                type: "RELIABILITY",
                priority: "CRITICAL",
                issue: "High error rate",
                recommendation: "Investigate error patterns and implement better error handling",
                currentValue: "${report.requests.errorRate}%",
                targetValue: "< 5%"
            ]
        }
        
        return recommendations
    }
    
    def generateDetailedAnalysis(Map report) {
        def analysis = [
            summary: "System performance analysis for USSD processing",
            overallHealth: calculateOverallHealth(report),
            keyFindings: [],
            detailedMetrics: report,
            recommendations: generateOptimizationRecommendations(report)
        ]
        
        // Key findings
        if (report.requests.perSecond > 500) {
            analysis.keyFindings << "✅ Excellent throughput: ${report.requests.perSecond} req/s"
        } else if (report.requests.perSecond > 100) {
            analysis.keyFindings << "⚠️ Good throughput: ${report.requests.perSecond} req/s"
        } else {
            analysis.keyFindings << "❌ Low throughput: ${report.requests.perSecond} req/s"
        }
        
        if (report.cache.hitRate > 95) {
            analysis.keyFindings << "✅ Excellent cache performance: ${report.cache.hitRate}%"
        } else if (report.cache.hitRate > 90) {
            analysis.keyFindings << "⚠️ Good cache performance: ${report.cache.hitRate}%"
        } else {
            analysis.keyFindings << "❌ Poor cache performance: ${report.cache.hitRate}%"
        }
        
        if (report.requests.successRate > 99) {
            analysis.keyFindings << "✅ Excellent reliability: ${report.requests.successRate}%"
        } else if (report.requests.successRate > 95) {
            analysis.keyFindings << "⚠️ Good reliability: ${report.requests.successRate}%"
        } else {
            analysis.keyFindings << "❌ Poor reliability: ${report.requests.successRate}%"
        }
        
        return analysis
    }
    
    def calculateOverallHealth(Map report) {
        def score = 100
        
        // Deduct points for issues
        if (report.performance.averageProcessingTime > 500) score -= 20
        if (report.cache.hitRate < 90) score -= 15
        if (report.jvm.heapMemory.usagePercentage > 80) score -= 15
        if (report.requests.errorRate > 5) score -= 25
        if (report.requests.perSecond < 100) score -= 10
        if (report.jvm.threads.active > 200) score -= 10
        
        if (score >= 90) return "EXCELLENT"
        else if (score >= 75) return "GOOD"
        else if (score >= 60) return "FAIR"
        else if (score >= 40) return "POOR"
        else return "CRITICAL"
    }
}

// Main execution
def monitor = new PerformanceMonitor()

def flowFile = session.get()
if (!flowFile) {
    return
}

try {
    // Generate performance report
    def report = monitor.generatePerformanceReport()
    def analysis = monitor.generateDetailedAnalysis(report)
    
    // Create comprehensive output
    def output = [
        reportType: "USSD_PERFORMANCE_ANALYSIS",
        generatedAt: new Date().toString(),
        analysis: analysis
    ]
    
    // Write to flowfile
    flowFile = session.write(flowFile, new OutputStreamCallback() {
        @Override
        void process(OutputStream out) throws IOException {
            out.write(JsonOutput.prettyPrint(JsonOutput.toJson(output)).getBytes(StandardCharsets.UTF_8))
        }
    })
    
    // Add summary attributes
    flowFile = session.putAttribute(flowFile, "performance.overallHealth", analysis.overallHealth)
    flowFile = session.putAttribute(flowFile, "performance.throughput", report.requests.perSecond.toString())
    flowFile = session.putAttribute(flowFile, "performance.cacheHitRate", report.cache.hitRate.toString())
    flowFile = session.putAttribute(flowFile, "performance.successRate", report.requests.successRate.toString())
    flowFile = session.putAttribute(flowFile, "performance.recommendationCount", analysis.recommendations.size().toString())
    
    // Log key metrics
    log.info("Performance Report Generated:")
    log.info("- Overall Health: ${analysis.overallHealth}")
    log.info("- Throughput: ${report.requests.perSecond} req/s")
    log.info("- Cache Hit Rate: ${report.cache.hitRate}%")
    log.info("- Success Rate: ${report.requests.successRate}%")
    log.info("- Active Recommendations: ${analysis.recommendations.size()}")
    
    session.transfer(flowFile, REL_SUCCESS)
    
} catch (Exception e) {
    log.error("Failed to generate performance report", e)
    flowFile = session.penalize(flowFile)
    session.transfer(flowFile, REL_FAILURE)
}