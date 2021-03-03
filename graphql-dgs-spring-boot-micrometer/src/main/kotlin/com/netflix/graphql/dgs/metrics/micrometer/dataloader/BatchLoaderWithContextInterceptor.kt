package com.netflix.graphql.dgs.metrics.micrometer.dataloader


import com.netflix.graphql.dgs.metrics.DgsMetrics.GqlTag
import com.netflix.graphql.dgs.metrics.DgsMetrics.GqlMetric
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import net.bytebuddy.implementation.bind.annotation.Pipe
import org.dataloader.BatchLoaderWithContext
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletionStage

internal class BatchLoaderWithContextInterceptor(
        private val batchLoader: BatchLoaderWithContext<*, *>,
        private val name: String,
        private val registry: MeterRegistry
) {
    companion object {
        private val ID = GqlMetric.DATA_LOADER.key
        private val logger = LoggerFactory.getLogger(BatchLoaderWithContextInterceptor::class.java)
    }

    fun load(@Pipe pipe: Forwarder<CompletionStage<List<*>>, BatchLoaderWithContext<*, *>>): CompletionStage<List<*>> {
        logger.debug("Starting metered timer[{}] for {}.", ID, javaClass.simpleName)
        val timerSampler = Timer.start(registry)
        return try {
            pipe.to(batchLoader).whenComplete { result, _ ->
                logger.debug("Stopping timer[{}] for {}", ID, javaClass.simpleName)
                timerSampler.stop(registry,
                        Timer.builder(ID)
                                .tags(Tags.of(
                                        Tag.of(GqlTag.LOADER_NAME.key, name),
                                        Tag.of(GqlTag.LOADER_BATCH_SIZE.key, result.size.toString()))))
            }
        } catch (exception: Exception) {
            logger.warn("Error creating timer interceptor '{}' for {}", ID, javaClass.simpleName)
            pipe.to(batchLoader)
        }
    }
}