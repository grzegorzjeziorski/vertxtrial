package com.gjeziorski.vertxtrial.common;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class ObjectMapperProvider {

    private static ObjectMapper instance = null;

    private ObjectMapperProvider() {
    }

    public static ObjectMapper getObjectMapper() {
        if (instance == null) {
            synchronized (ObjectMapperProvider.class) {
                if (instance == null) {
                    instance = new ObjectMapper().registerModule(new JavaTimeModule())
                        .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
                        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
                }
            }
        }
        return instance;
    }

}
