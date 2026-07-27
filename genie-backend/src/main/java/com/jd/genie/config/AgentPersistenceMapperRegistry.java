package com.jd.genie.config;

import org.mybatis.spring.SqlSessionTemplate;

/** Keeps the agent-only MyBatis session out of Spring's global type lookup. */
public final class AgentPersistenceMapperRegistry {
    private final SqlSessionTemplate sqlSessionTemplate;

    public AgentPersistenceMapperRegistry(SqlSessionTemplate sqlSessionTemplate) {
        this.sqlSessionTemplate = sqlSessionTemplate;
    }

    public <T> T mapper(Class<T> mapperType) {
        return sqlSessionTemplate.getMapper(mapperType);
    }
}
