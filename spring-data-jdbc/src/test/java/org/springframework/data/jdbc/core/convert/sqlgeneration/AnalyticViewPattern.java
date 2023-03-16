package org.springframework.data.jdbc.core.convert.sqlgeneration;
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

import java.util.List;

public record AnalyticViewPattern(StructurePattern content) implements StructurePattern {

	static AnalyticViewPattern av(StructurePattern content) {
		return new AnalyticViewPattern(content);
	}

	@Override
	public boolean matches(AnalyticStructureBuilder<?, ?>.Select select) {

		if (select instanceof AnalyticStructureBuilder.AnalyticView view) {
			final List froms = view.getFroms();
			return froms.size() == 1 && content.matches((AnalyticStructureBuilder<?, ?>.Select) froms.get(0));
		}
		return false;
	}

	@Override
	public String toString() {
		return "AV{" + content + '}';
	}
}
