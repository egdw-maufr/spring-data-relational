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

import static org.assertj.core.api.Assertions.*;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SelectItemVisitorAdapter;
import net.sf.jsqlparser.statement.select.SubSelect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.mapping.PersistentPropertyPath;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;

public class SqlAssert extends AbstractAssert<SqlAssert, Statement> {

	private AliasFactory aliasFactory;

	protected SqlAssert(Statement actual, Class<?> selfType) {
		super(actual, selfType);
	}

	static SqlAssert assertThatParsed(String actualSql) {

		try {
			Statement parsed = CCJSqlParserUtil.parse(actualSql);
			return new SqlAssert(parsed, SqlAssert.class);
		} catch (JSQLParserException e) {
			Assertions.fail("Couldn't parse '%s'".formatted(actualSql));
		}

		throw new IllegalStateException("This should be unreachable");
	}

	public SqlAssert hasExactColumns(ColumnsSpec columnsSpec) {

		List<ParsedColumn> parsedColumns = collectActualColumns();

		List<ColumnSpec> notFound = new ArrayList<>();

		// check normal property based columns
		columnsSpec.foreach(aliasFactory, (ColumnSpec columnSpec) -> {
			for (ParsedColumn currentColumn : parsedColumns) {
				if (columnSpec.matches(currentColumn)) {
					parsedColumns.remove(currentColumn);
					return;
				}
			}
			notFound.add(columnSpec);
		});

		if (parsedColumns.isEmpty() && notFound.isEmpty()) {
			return this;
		}

		String failureMessage = "Expected %s%n to contain columns representing%n %s %n but ";
		if (!notFound.isEmpty()) {
			failureMessage += "no columns for %s were found";
		}
		if (!notFound.isEmpty() && !parsedColumns.isEmpty()) {
			failureMessage += "%n and ";
		}
		if (!parsedColumns.isEmpty()) {
			failureMessage += "the columns %s where not expected.";
		}

		String notFoundString = notFound.stream().map(key -> {
			if (key instanceof PropertyBasedColumn pbc) {
				return columnsSpec.paths.get(pbc.property);
			} else {
				return key.toString();
			}
		}).collect(Collectors.joining(", "));

		if (!notFound.isEmpty() && !parsedColumns.isEmpty()) {
			throw failure(failureMessage, getSelect().toString(), columnsSpec, notFoundString, parsedColumns);
		}
		if (!notFound.isEmpty()) {
			throw failure(failureMessage, getSelect().toString(), columnsSpec, notFoundString);
		} else {
			throw failure(failureMessage, getSelect().toString(), columnsSpec, parsedColumns);
		}

	}

	public SqlAssert assignsAliasesExactlyOnce() {

		// TODO: this should eventually test that all inner columns are aliased exactly once when they are originally
		// selected.
		List<ParsedColumn> aliasedColumns = new ArrayList<>();

		List<ParsedColumn> parsedColumns = collectActualColumns();
		for (ParsedColumn column : parsedColumns) {
			// TODO: this should be a "is simple column" method in the ActualColumn class
			if (!column.alias.isEmpty() && column.column.contains("00")) {
				aliasedColumns.add(column);
			}
		}

		assertThat(aliasedColumns)
				.describedAs("The columns %s should not have aliases, but %s have.", parsedColumns, aliasedColumns).isEmpty();

		return this;
	}

	public SqlAssert selectsInternally(String pathToEntity, String columnName) {

		Optional<Column> optionalColumn = allInternalSelectItems(getSelect()).map(x -> {
			return x;
		}).flatMap(si -> {

			if (si instanceof SelectExpressionItem sei) {
				if (sei.getExpression()instanceof Column c)
					return Stream.of(c);
			}
			return Stream.empty();
		}).filter(c -> c.getColumnName().equals(columnName)).findFirst();

		assertThat(optionalColumn).describedAs("No column of name " + columnName + " found.").isPresent();

		return this;
	}

	private Stream<SelectItem> allInternalSelectItems(PlainSelect select) {

		return allSelects(select).flatMap(s -> s.getSelectItems().stream());
	}

	private Stream<PlainSelect> allSelects(PlainSelect select) {

		return Stream.concat(Stream.of(select), getSubSelects(select));
	}

	@NotNull
	private static Stream<PlainSelect> getSubSelects(PlainSelect select) {
		return Stream.of(select).flatMap(s -> {
			List<Join> joins = s.getJoins();
			if (joins == null) {
				return Stream.empty();
			}
			return joins.stream() //
					.map(j -> j.getRightItem()) //
					.filter(fi -> fi instanceof SubSelect) //
					.map(ss -> (PlainSelect) ((SubSelect) ss).getSelectBody());
		});
	}

	/**
	 * @return a list of select list elements that are queried for. Doesn't look at internal queries
	 */
	private List<ParsedColumn> collectActualColumns() {

		List<ParsedColumn> parsedColumns = new ArrayList<>();
		for (SelectItem selectItem : getSelect().getSelectItems()) {
			selectItem.accept(new SelectItemVisitorAdapter() {
				@Override
				public void visit(SelectExpressionItem item) {
					parsedColumns.add(new ParsedColumn(item.getExpression().toString(),
							item.getAlias() == null ? "" : item.getAlias().toString()));
				}
			});
		}
		return parsedColumns;
	}

	public SqlAssert selectsFrom(String tableName) {

		assertThat(getSelect().getFromItem().toString()) //
				.isEqualTo(tableName);

		return this;
	}

	private PlainSelect getSelect() {
		return (PlainSelect) ((Select) actual).getSelectBody();
	}

	static ColumnsSpec from(RelationalMappingContext context, RelationalPersistentEntity<?> entity) {
		return new ColumnsSpec(context, entity);
	}

	public SqlAssert withAliases(AliasFactory aliasFactory) {
		this.aliasFactory = aliasFactory;
		return this;
	}

	static class ColumnsSpec {
		private final RelationalMappingContext context;
		private final RelationalPersistentEntity<?> currentEntity;
		final Map<RelationalPersistentProperty, String> paths = new HashMap<>();

		final List<Function<AliasFactory, ColumnSpec>> specialColumns = new ArrayList<>();

		public ColumnsSpec(RelationalMappingContext context, RelationalPersistentEntity<?> entity) {

			this.context = context;
			this.currentEntity = entity;
		}

		public ColumnsSpec property(String pathString) {

			PersistentPropertyPath<RelationalPersistentProperty> path = context.getPersistentPropertyPath(pathString,
					currentEntity.getType());

			RelationalPersistentProperty leafProperty = path.getLeafProperty();
			paths.put(leafProperty, pathString);

			return this;
		}

		public ColumnsSpec rowNumber(String dummy) {

			specialColumns.add(f -> PrefixSpec.rowNumberSpec(f, dummy));
			return this;
		}

		public void foreach(AliasFactory aliasFactory, Consumer<? super ColumnSpec> specConsumer) {

			paths.keySet().forEach(p -> specConsumer.accept(new PropertyBasedColumn(aliasFactory, p)));
			specialColumns.forEach(o -> specConsumer.accept(o.apply(aliasFactory)));
		}

		@Override
		public String toString() {
			return paths.values().toString();
		}

		public ColumnsSpec alias(String alias) {
			specialColumns.add(f -> new AliasSpec(f, alias));
			return this;
		}

		public ColumnsSpec fk(String alias) {
			specialColumns.add(f -> new AliasSpec(f, alias));
			return this;
		}

		public ColumnsSpec greatest() {
			specialColumns.add(f -> PrefixSpec.greatestSpec(f, "greatest has unknown alias"));
			return this;
		}
	}

	private interface ColumnSpec {
		boolean matches(ParsedColumn parsedColumn);
	}

	private record PropertyBasedColumn(AliasFactory aliasFactory,
			RelationalPersistentProperty property) implements ColumnSpec {

		@Override
		public boolean matches(ParsedColumn parsedColumn) {

			String alias = aliasFactory.getAliasFor(property);
			if (parsedColumn.alias.equals(alias) || parsedColumn.column.equals(alias)) {
				return true;
			}
			return false;
		}

	}

	private record PrefixSpec(AliasFactory aliasFactory, Object someObject, String prefix) implements ColumnSpec {

		static PrefixSpec rowNumberSpec(AliasFactory aliasFactory, Object someObject) {
			return new PrefixSpec(aliasFactory, someObject, "ROW_NUMBER() OVER (PARTITION BY");
		}

		static PrefixSpec greatestSpec(AliasFactory aliasFactory, Object someObject) {
			return new PrefixSpec(aliasFactory, someObject, "GREATEST(");
		}

		@Override
		public boolean matches(ParsedColumn parsedColumn) {
			return parsedColumn.expression.startsWith(prefix);
		}

		@Override
		public String toString() {
			return someObject.toString();
		}
	}

	private record AliasSpec(AliasFactory aliasFactory, String alias) implements ColumnSpec {

		@Override
		public boolean matches(ParsedColumn parsedColumn) {
			return parsedColumn.expression.endsWith(alias);
		}

	}

	private record ParsedColumn(String expression, String table, String column, String alias) {
		ParsedColumn(String expression, String alias) {
			this(expression, table(expression), column(expression), alias.replace(" AS ", ""));
		}

		private static String table(String expression) {

			int index = expression.indexOf(".");
			if (index > 0) {
				return expression.substring(0, index);
			}
			return expression;
		}

		static String column(String expression) {

			int index = expression.indexOf(".");
			int spaceIndex = expression.indexOf(" ");
			if (index > 0 && (spaceIndex < 0 || index < spaceIndex)) {
				return expression.substring(index + 1);
			}
			return expression;
		}

		@Override
		public String toString() {
			return expression + (alias == null || alias.isBlank() ? "" : (" AS " + alias));
		}
	}

}
