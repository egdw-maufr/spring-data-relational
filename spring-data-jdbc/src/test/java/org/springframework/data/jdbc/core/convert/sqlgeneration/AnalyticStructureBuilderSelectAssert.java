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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.error.BasicErrorMessageFactory;
import org.assertj.core.internal.Failures;
import org.assertj.core.internal.StandardComparisonStrategy;
import org.jetbrains.annotations.NotNull;
import org.springframework.lang.Nullable;

import static org.assertj.core.api.Assertions.*;

public class AnalyticStructureBuilderSelectAssert<T, C>
		extends AbstractAssert<AnalyticStructureBuilderSelectAssert<T,C>, AnalyticStructureBuilder.Select> {
	protected AnalyticStructureBuilderSelectAssert(AnalyticStructureBuilder.Select select) {
		super(select, AnalyticStructureBuilderSelectAssert.class);
	}



	AnalyticStructureBuilderSelectAssert<T, C> hasExactColumns(Object... expected) {

		return containsPatternsExactly(objectsToPatterns(expected));
	}

	AnalyticStructureBuilderSelectAssert<T, C> hasAtLeastColumns(Object... expected) {

		return containsPatterns(objectsToPatterns(expected));
	}

	@NotNull
	private static Pattern[] objectsToPatterns(Object[] expected) {
		Pattern[] patterns = new Pattern[expected.length];
		for (int i = 0; i < expected.length; i++) {

			Object object = expected[i];
			patterns[i] = object instanceof Pattern ? (Pattern) object : new BasePattern(object);
		}
		return patterns;
	}

	AnalyticStructureBuilderSelectAssert<T, C> hasStructure(StructurePattern pattern) {

		assertThat(actual).matches(pattern::matches, "\n\t" + pattern);

		return this;
	}

	private AnalyticStructureBuilderSelectAssert<T, C> containsPatternsExactly(Pattern... patterns) {

		List<? extends AnalyticStructureBuilder<?, ?>.AnalyticColumn> availableColumns = actual.getColumns();

		List<Pattern> notFound = new ArrayList<>();
		for (Pattern pattern : patterns) {

			boolean found = false;
			for (AnalyticStructureBuilder<?, ?>.AnalyticColumn actualColumn : availableColumns) {

				if (pattern.matches(actual, actualColumn)) {
					found = true;
					availableColumns.remove(actualColumn);
					break;
				}
			}

			if (!found) {
				notFound.add(pattern);
			}
		}

		if (notFound.isEmpty() && availableColumns.isEmpty()) {
			return this;
		}

		throw Failures.instance().failure(info, ColumnsShouldContainExactly
				.columnsShouldContainExactly(actual.getColumns(), patterns, notFound, availableColumns));
	}

	public AnalyticStructureBuilderSelectAssert<T,C> containsPatterns(Pattern ... patterns) {

		List<? extends AnalyticStructureBuilder<?, ?>.AnalyticColumn> availableColumns = actual.getColumns();

		List<Pattern> notFound = new ArrayList<>();
		for (Pattern pattern : patterns) {

			boolean found = false;
			for (AnalyticStructureBuilder<?, ?>.AnalyticColumn actualColumn : availableColumns) {

				if (pattern.matches(actual, actualColumn)) {
					found = true;
					availableColumns.remove(actualColumn);
					break;
				}
			}

			if (!found) {
				notFound.add(pattern);
			}
		}

		if (notFound.isEmpty()) {
			return this;
		}

		throw Failures.instance().failure(info, ColumnsShouldContainExactly
				.columnsShouldContainExactly(actual.getColumns(), patterns, notFound, availableColumns));
	}


	private static class ColumnsShouldContainExactly extends BasicErrorMessageFactory {

		static ColumnsShouldContainExactly columnsShouldContainExactly(
				List<? extends AnalyticStructureBuilder<?, ?>.AnalyticColumn> actualColumns, Pattern[] expected,
				List<Pattern> notFound, List<? extends AnalyticStructureBuilder<?, ?>.AnalyticColumn> notExpected) {
			String actualColumnsDescription = actualColumns.stream().map(AnalyticStructureBuilderSelectAssert::toString)
					.collect(Collectors.joining(", "));

			String expectedDescription = Arrays.stream(expected).map(Pattern::render).collect(Collectors.joining(", "));

			if (notFound.isEmpty()) {
				return new ColumnsShouldContainExactly(actualColumnsDescription, expectedDescription, notExpected);
			}
			if (notExpected.isEmpty()) {
				return new ColumnsShouldContainExactly(actualColumnsDescription, expectedDescription, notFound);
			}
			return new ColumnsShouldContainExactly(actualColumnsDescription, expectedDescription, notFound, notExpected);
		}

		private ColumnsShouldContainExactly(String actualColumns, String expected, Object notFound,
				List<? extends AnalyticStructureBuilder.AnalyticColumn> notExpected) {
			super("""

					Expecting:
					  <%s>
					  to contain exactly <%s>.
					  But <%s> were not found,
					  and <%s> where not expected.""", actualColumns, expected, notFound, notExpected,
					StandardComparisonStrategy.instance());
		}

		private ColumnsShouldContainExactly(String actualColumns, String expected,
				List<? extends AnalyticStructureBuilder.AnalyticColumn> notExpected) {
			super("""

					Expecting:
					  <%s>
					  to contain exactly <%s>.
					  But <%s> where not expected.""", actualColumns, expected, notExpected,
					StandardComparisonStrategy.instance());
		}

		private ColumnsShouldContainExactly(String actualColumns, String expected, Object notFound) {
			super("""

					Expecting:
					  <%s>
					  to contain exactly <%s>.
					  But <%s> were not found.""", actualColumns, expected, notFound, StandardComparisonStrategy.instance());
		}

	}

	private static String toString(Object c) {

		if (c instanceof AnalyticStructureBuilder.BaseColumn bc) {
			return bc.getColumn().toString();
		}
		if (c instanceof AnalyticStructureBuilder.ForeignKey fk) {
			return fk.toString();
		}
		if (c instanceof AnalyticStructureBuilder.DerivedColumn dc) {
			return toString(dc.getBase());
		}

		return extractColumn(c).toString();
	}

	static Object extractColumn(Object c) {

		if (c instanceof AnalyticStructureBuilder.BaseColumn bc) {
			return bc.getColumn();
		} else if (c instanceof AnalyticStructureBuilder.DerivedColumn dc) {
			return extractColumn(dc.getBase());
		} else if (c instanceof AnalyticStructureBuilder.RowNumber rn) {
			return "RN";
		} else if (c instanceof AnalyticStructureBuilder.ForeignKey fk) {
			return fk.toString();
		} else if (c instanceof AnalyticStructureBuilder.Greatest greatest) {
			return "MAX(" + toString(greatest.getLeft()) + ", " + toString(greatest.getRight()) + ")";
		} else {
			return "unknown";
		}
	}
	@Nullable
	static <T extends AnalyticStructureBuilder<?, ?>.AnalyticColumn> T extract(Class<T> type,
																			   AnalyticStructureBuilder<?, ?>.AnalyticColumn column) {

		if (type.isInstance(column)) {
			return type.cast(column);
		}

		if (column instanceof AnalyticStructureBuilder.DerivedColumn derivedColumn) {
			return extract(type, derivedColumn.getBase());
		}
		return null;
	}

	AnalyticStructureBuilderSelectAssert<T, C> hasId(C name) {

		assertThat(actual).matches( //
				a -> new BasePattern(name) //
						.matches(((List<AnalyticStructureBuilder.AnalyticColumn>) a.getId()).get(0)), // TODO: this works
				// only for single
				// ids.
				"has Id " + name + ", but was " + actual.getId());
		return this;
	}

}
