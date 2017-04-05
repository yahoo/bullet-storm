package com.yahoo.bullet.operations.aggregations;

import com.yahoo.bullet.BulletConfig;
import com.yahoo.bullet.Utilities;
import com.yahoo.bullet.operations.aggregations.sketches.ThetaSketch;
import com.yahoo.bullet.parsing.Aggregation;
import com.yahoo.bullet.parsing.Error;
import com.yahoo.bullet.parsing.Specification;
import com.yahoo.bullet.record.BulletRecord;
import com.yahoo.sketches.Family;
import com.yahoo.sketches.ResizeFactor;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;

public class CountDistinct extends KMVStrategy<ThetaSketch> {
    public static final String NEW_NAME_KEY = "newName";
    public static final String DEFAULT_NEW_NAME = "COUNT DISTINCT";

    // Theta Sketch defaults
    // Recommended for real-time systems
    public static final String DEFAULT_UPDATE_SKETCH_FAMILY = Family.ALPHA.getFamilyName();
    // This gives us (Alpha sketches fall back to QuickSelect RSEs after compaction or set operations) a 2.34% error
    // rate at 99.73% confidence (3 Standard Deviations).
    public static final int DEFAULT_NOMINAL_ENTRIES = 16384;

    /**
     * Constructor that requires an {@link Aggregation}.
     *
     * @param aggregation An {@link Aggregation} with valid fields and attributes for this aggregation type.
     */
    @SuppressWarnings("unchecked")
    public CountDistinct(Aggregation aggregation) {
        super(aggregation);
        Map<String, Object> attributes = aggregation.getAttributes();

        ResizeFactor resizeFactor = getResizeFactor(BulletConfig.COUNT_DISTINCT_AGGREGATION_SKETCH_RESIZE_FACTOR);
        float samplingProbability = ((Number) config.getOrDefault(BulletConfig.COUNT_DISTINCT_AGGREGATION_SKETCH_SAMPLING,
                                                                  DEFAULT_SAMPLING_PROBABILITY)).floatValue();
        Family family = getFamily(config.getOrDefault(BulletConfig.COUNT_DISTINCT_AGGREGATION_SKETCH_FAMILY,
                                                      DEFAULT_UPDATE_SKETCH_FAMILY).toString());
        int nominalEntries = ((Number) config.getOrDefault(BulletConfig.COUNT_DISTINCT_AGGREGATION_SKETCH_ENTRIES,
                                                           DEFAULT_NOMINAL_ENTRIES)).intValue();
        String newName = attributes == null ? DEFAULT_NEW_NAME :
                                              attributes.getOrDefault(NEW_NAME_KEY, DEFAULT_NEW_NAME).toString();

        sketch = new ThetaSketch(resizeFactor, family, samplingProbability, nominalEntries, newName);
    }

    @Override
    public List<Error> initialize() {
        return Utilities.isEmpty(fields) ? singletonList(REQUIRES_FIELD_ERROR) : null;
    }

    @Override
    public void consume(BulletRecord data) {
        String field = getFieldsAsString(fields, data, separator);
        sketch.update(field);
    }

    private static String getFieldsAsString(List<String> fields, BulletRecord record, String separator) {
        // This explicitly does not do a TypedObject checking. Nulls turn into a String that will be counted.
        return fields.stream().map(field -> Specification.extractField(field, record))
                              .map(Objects::toString)
                              .collect(Collectors.joining(separator));
    }

    /**
     * Convert a String family into a {@link Family}. This is used to recognize a user input family choice.
     *
     * @param family The string version of the {@link Family}. Currently, QuickSelect and Alpha are supported.
     * @return The Sketch family represented by the string or {@link #DEFAULT_UPDATE_SKETCH_FAMILY} otherwise.
     */
    static Family getFamily(String family) {
        return Family.QUICKSELECT.getFamilyName().equals(family) ? Family.QUICKSELECT : Family.ALPHA;
    }
}
