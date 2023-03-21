/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.data.jdbc.core.convert;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.context.TestExecutionListeners.MergeMode.*;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.data.jdbc.core.convert.sqlgeneration.AggregateToStructure;
import org.springframework.data.jdbc.core.convert.sqlgeneration.AliasFactory;
import org.springframework.data.jdbc.core.convert.sqlgeneration.AnalyticSqlGenerator;
import org.springframework.data.jdbc.core.convert.sqlgeneration.StructureToSelect;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.jdbc.testing.AssumeFeatureTestExecutionListener;
import org.springframework.data.jdbc.testing.EnabledOnFeature;
import org.springframework.data.jdbc.testing.TestConfiguration;
import org.springframework.data.jdbc.testing.TestDatabaseFeatures;
import org.springframework.data.mapping.PersistentPropertyPath;
import org.springframework.data.relational.core.dialect.Dialect;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

@ContextConfiguration
@Transactional
@TestExecutionListeners(value = AssumeFeatureTestExecutionListener.class, mergeMode = MERGE_WITH_DEFAULTS)
@ExtendWith(SpringExtension.class)
public class SingleSelectIntegrationTests {

	@Autowired JdbcMappingContext jdbcMappingContext;
	@Autowired JdbcConverter converter;

	@Autowired Dialect dialect;

	@Autowired JdbcAggregateTemplate aggregateTemplate;

	@Autowired NamedParameterJdbcTemplate jdbcTemplate;

	@Test
	@EnabledOnFeature(TestDatabaseFeatures.Feature.SUPPORTS_SINGLE_SELECT_QUERY)
	void simpleEntity() {
		RelationalPersistentEntity<DummyEntity> entity = (RelationalPersistentEntity<DummyEntity>) jdbcMappingContext
				.getRequiredPersistentEntity(DummyEntity.class);

		AggregateReader<DummyEntity> reader = new AggregateReader<>(jdbcMappingContext, dialect, entity, converter, jdbcTemplate);

		DummyEntity saved = aggregateTemplate.save(new DummyEntity(null, "Jens"));

		assertThat(reader.findAll()).containsExactly(saved);
	}

	private  PathToColumnMapping createPathToColumnMapping(AliasFactory aliasFactory) {
		return new PathToColumnMapping() {
			@Override
			public String column(PersistentPropertyPath<RelationalPersistentProperty> propertyPath) {
				RelationalPersistentProperty leafProperty = propertyPath.getRequiredLeafProperty();
				if (leafProperty.isEntity()) {
					return aliasFactory.getOrCreateAlias(jdbcMappingContext.getRequiredPersistentEntity(leafProperty.getType()));
				}
				return aliasFactory.getOrCreateAlias(propertyPath);
			}

			@Override
			public String keyColumn(PersistentPropertyPath<RelationalPersistentProperty> propertyPath) {
				throw new UnsupportedOperationException("not supported yet");
			}
		};
	}


	@Test
	@EnabledOnFeature(TestDatabaseFeatures.Feature.SUPPORTS_SINGLE_SELECT_QUERY)
	void singleCollection() {

		RelationalPersistentEntity<SingleReference> entity = (RelationalPersistentEntity<SingleReference>) jdbcMappingContext
				.getRequiredPersistentEntity(SingleReference.class);
		AggregateReader<SingleReference> reader = new AggregateReader<>(jdbcMappingContext, dialect, entity, converter, jdbcTemplate);

		SingleReference singleReference = new SingleReference(null, new DummyEntity(null, "Jens"));
		SingleReference saved = aggregateTemplate.save(singleReference);

		assertThat(reader.findAll()).containsExactly(saved);
	}



	@Configuration
	@Import(TestConfiguration.class)
	static class Config {

		@Bean
		Class<?> testClass() {
			return SingleSelectIntegrationTests.class;
		}

		@Bean
		JdbcAggregateOperations operations(ApplicationEventPublisher publisher, RelationalMappingContext context,
				DataAccessStrategy dataAccessStrategy, JdbcConverter converter) {
			return new JdbcAggregateTemplate(publisher, context, converter, dataAccessStrategy);
		}
	}

	record DummyEntity(@Id Integer id, String name) {
	}


	record SingleReference (
		@Id Integer id,
		DummyEntity dummy){
	}
}
