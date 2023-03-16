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

package org.springframework.data.jdbc.core.convert.sqlgeneration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.data.relational.core.sql.*;

class StructureToSelect {

	private final AliasFactory aliasFactory;

	StructureToSelect() {
		this(new AliasFactory());
	}

	StructureToSelect(AliasFactory aliasFactory) {
		this.aliasFactory = aliasFactory;
	}

	SelectBuilder.BuildSelect createSelect(
			AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.Select queryStructure) {

		if (queryStructure instanceof AnalyticStructureBuilder.TableDefinition tableDefinition) {
			System.out.println("table " + tableDefinition.getTable());
			return createSimpleSelect(tableDefinition);
		}

		if (queryStructure instanceof AnalyticStructureBuilder.AnalyticJoin analyticJoin) {
			System.out.println("join");
			SelectBuilder.SelectFromAndJoinCondition join = createJoin(analyticJoin);
			return join;
		}

		if (queryStructure instanceof AnalyticStructureBuilder.AnalyticView analyticView) {
			System.out.println("view");
			return createView(analyticView);
		}

		throw new UnsupportedOperationException("Can't convert " + queryStructure);
	}

	private SelectBuilder.BuildSelect createView(AnalyticStructureBuilder.AnalyticView analyticView) {
		return createSelect(
				(AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.Select) analyticView
						.getFroms().get(0));
	}

	private SelectBuilder.SelectFromAndJoinCondition createJoin(
			AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.AnalyticJoin analyticJoin) {

		AnalyticStructureBuilder.Select child = analyticJoin.getChild();
		SelectBuilder.BuildSelect childSelect = createSelect(child);
		InlineQuery childQuery = InlineQuery.create(childSelect.build(), getAliasFor(child));

		Collection<Expression> columns = getSelectList(child, childQuery, false);

		AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.Select parent = analyticJoin
				.getParent();
		TableLike parentTable = tableFor(parent);
		columns.addAll(getSelectList(parent, parentTable, false));

		SelectBuilder.SelectFromAndJoin selectAndParent = StatementBuilder.select(columns).from(parentTable);

		Condition condition = createJoinCondition(analyticJoin);
		return selectAndParent.fullOuterJoin(childQuery).on(condition);
	}

	private String getAliasFor(Object object) {
		return aliasFactory.getAliasFor(object);
	}

	private Collection<Expression> getSelectList(
			AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.Select parent,
			TableLike parentTable, boolean declareAlias) {

		Collection<Expression> tableColumns = new ArrayList<>();

		for (AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.AnalyticColumn analyticColumn : parent
				.getColumns()) {

			if (analyticColumn instanceof AnalyticStructureBuilder.ForeignKey fk) {
				System.out.println("for now skipping foreign key" + fk);
				continue;
			}
			Expression column;

			RelationalPersistentProperty property = analyticColumn.getColumn();
			if (property == null) {
				// TODO: handle all the special join management columns.
				System.out.println("wip: " + analyticColumn);
				if (analyticColumn instanceof AnalyticStructureBuilder.RowNumber rn) {

					Column[] partitionBys = ((Stream<Column>) rn.getPartitionBy().stream().map(ac -> parentTable.column(aliasFactory.getAliasFor(ac)))).toArray(Column[]::new);

					column = AnalyticFunction.create("ROW_NUMBER").partitionBy(partitionBys).as(aliasFactory.getAliasFor(rn));
				} else {
					throw new UnsupportedOperationException("Can't handle " + analyticColumn);
				}
			} else {
				column = createColumn(parentTable, property, declareAlias);
			}

			tableColumns.add(column);
			System.out.println("column " + column);

		}
		return tableColumns;
	}

	private Column createColumn(TableLike parentTable, RelationalPersistentProperty property, boolean declareAlias) {

		String alias = getAliasFor(property);

		if (declareAlias) {

			return parentTable //
					.column(property.getColumnName()) //
					.as(alias);
		} else {
			return parentTable.column(alias);
		}
	}

	private TableLike tableFor(
			AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.Select parent) {

		if (parent instanceof AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.TableDefinition tableDefinition) {
			return Table.create(tableDefinition.getTable().getTableName()).as(getAliasFor(parent));
		}

		throw new UnsupportedOperationException("can only create table names for TableDefinitions right now");
	}

	private Condition createJoinCondition(
			AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.AnalyticJoin analyticJoin) {

		for (AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.JoinCondition joinCondition : analyticJoin
				.getConditions()) {
			AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.AnalyticColumn left = joinCondition
					.getLeft();
			joinCondition.getRight();

			return Conditions.isEqual(Expressions.just("1"), Expressions.just("2"));
		}
		return null;
	}

	private SelectBuilder.SelectFromAndJoin createSimpleSelect(
			AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.TableDefinition tableDefinition) {

		List<? extends AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.AnalyticColumn> analyticColumns = tableDefinition
				.getColumns();

		Collection<Expression> tableColumns = new ArrayList<>();
		TableLike table = tableFor(tableDefinition);
		for (AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.AnalyticColumn analyticColumn : analyticColumns) {

			if (analyticColumn instanceof AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.ForeignKey foreignKey) {

				SqlIdentifier columnName = createFkColumnName(foreignKey);
				String alias = aliasFactory.getAliasFor(foreignKey);
				tableColumns.add(table.column(columnName).as(alias));
				continue;
			}
			RelationalPersistentProperty property = analyticColumn.getColumn();
			if (property == null) {
				// TODO: handle all the special join management columns.
				continue;
			}
			SqlIdentifier columnName = property.getColumnName();
			Column column = table.column(columnName).as(getAliasFor(property));
			tableColumns.add(column);
			System.out.println("column " + column);

		}

		SelectBuilder.SelectFromAndJoin from = StatementBuilder.select(tableColumns).from(table);
		return from;
	}

	private static SqlIdentifier createFkColumnName(AnalyticStructureBuilder<RelationalPersistentEntity, RelationalPersistentProperty>.ForeignKey foreignKey) {
		// TODO: this is currently quite wrong
		return foreignKey.getForeignKeyColumn().getColumn().getColumnName();
	}
}
