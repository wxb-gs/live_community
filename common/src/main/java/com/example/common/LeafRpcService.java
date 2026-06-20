package com.example.common;

public interface LeafRpcService {

    /**
     * Generate a unique ID using segment mode (for business keys like "note", "comment").
     */
    IdResponse generateSegmentId(String bizKey);

    /**
     * Generate a unique ID using snowflake mode.
     */
    IdResponse generateSnowflakeId();
}
