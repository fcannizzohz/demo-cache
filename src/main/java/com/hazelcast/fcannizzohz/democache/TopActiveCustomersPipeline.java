package com.hazelcast.fcannizzohz.democache;

import com.hazelcast.fcannizzohz.democache.model.ActivityEvent;
import com.hazelcast.function.ComparatorEx;
import com.hazelcast.function.FunctionEx;
import com.hazelcast.function.ToLongFunctionEx;
import com.hazelcast.jet.Traversers;
import com.hazelcast.jet.aggregate.AggregateOperations;
import com.hazelcast.jet.datamodel.Tuple2;
import com.hazelcast.jet.pipeline.JournalInitialPosition;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.Sources;
import com.hazelcast.jet.pipeline.StreamSource;
import com.hazelcast.jet.pipeline.WindowDefinition;

import java.time.Duration;
import java.util.Map;

import static com.hazelcast.jet.pipeline.WindowDefinition.sliding;

public class TopActiveCustomersPipeline {

    public static String DEFAULT_ACTIVITY_MAP_NAME = "activity-map";
    public static String DEFAULT_CUSTOMER_COUNT_MAP_NAME = "customer-counts-per-window";

    public static Pipeline buildPipeline() {
        // Define sliding window of 9 minutes, sliding every 1 minute
        long windowSize = Duration.ofMinutes(9).toMillis();
        long slideBy = Duration.ofMinutes(1).toMillis();
        return buildPipeline(10, windowSize, slideBy, 0);
    }

    public static Pipeline buildPipeline(int topN, long windowSize, long slideBy, long lag) {
        StreamSource<Map.Entry<Integer, ActivityEvent>> source = Sources.mapJournal(DEFAULT_ACTIVITY_MAP_NAME,
                JournalInitialPosition.START_FROM_CURRENT);

        WindowDefinition windowDef = sliding(windowSize, slideBy);
        ToLongFunctionEx<Map.Entry<Integer, ActivityEvent>> toLongInstant =
                activityEventEntry -> activityEventEntry.getValue().timestamp().toEpochMilli();

        Pipeline p = Pipeline.create();

        p.readFrom(source).withTimestamps(toLongInstant, lag)  // allow lag
         // group by customerId
         .groupingKey((FunctionEx<Map.Entry<Integer, ActivityEvent>, Integer>) e -> e.getValue().customerId())
         // define the window
         .window(windowDef)
         // aggregate by counting
         .aggregate(AggregateOperations.counting())
         // map to customerId->count
         .map(customerCount -> Tuple2.tuple2(customerCount.key(), customerCount.result()))
         // single group
         .groupingKey(t -> "top-N")
         // select top N
         .rollingAggregate(AggregateOperations.topN(topN, ComparatorEx.comparingLong(Tuple2<Integer, Long>::f1)))
         // map back to customerId->count
         .flatMap(result -> Traversers.traverseIterable(result.getValue()))
         // sink to map
         .writeTo(Sinks.map(DEFAULT_CUSTOMER_COUNT_MAP_NAME,
                 // customerId
                 Tuple2::f0,
                 // count
                 Tuple2::f1
         ));
        return p;
    }
}
