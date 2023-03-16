/*
 * Copyright 2022 the original author or authors.
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

import org.springframework.lang.Nullable;

record GreatestPattern<C> (Pattern left, Pattern right) implements Pattern {

	public static <C> GreatestPattern<C> greatest(C left, Pattern right) {
		return new GreatestPattern(left instanceof Pattern leftPattern ? leftPattern : new BasePattern(left), right);
	}

	@Override
	public boolean matches(AnalyticStructureBuilder<?, ?>.Select select,
			AnalyticStructureBuilder<?, ?>.AnalyticColumn actualColumn) {

		AnalyticStructureBuilder<?, ?>.Greatest greatest = extractGreatest(actualColumn);
		return greatest != null && left.matches(select, greatest.left) && right.matches(select, greatest.right);
	}

	@Override
	public String render() {
		return "GREATEST(%s, %s)".formatted(left.render(), right.render());
	}

	@Nullable
	private AnalyticStructureBuilder.Greatest extractGreatest(AnalyticStructureBuilder<?, ?>.AnalyticColumn other) {
		return AnalyticStructureBuilderSelectAssert.extract(AnalyticStructureBuilder.Greatest.class, other);
	}

	@Override
	public String toString() {
		return "Greatest(" + left + ", " + right + ')';
	}
}
